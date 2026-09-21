package com.sproutgreen.getyourguitar.core.synth

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Karplus-Strong 현 합성.
 *
 * 루프: `x = delay[w − delayLen]`(선형 보간) → 1극 로우패스 `lp += a·(x − lp)` → `delay[w] = g·lp`.
 * 한 바퀴의 총 지연이 한 주기여야 하므로 `delayLen = sampleRate/hz − (로우패스의 위상 지연)`.
 * 이 보정이 없으면 높은 음일수록 음이 낮아진다(G4에서 약 41센트).
 * 로우패스 계수 `a`는 음마다 다시 계산한다([ToneParams.cutoffHz] 의 음높이 연동).
 *
 * 오디오 스레드 전용: 생성자 이후 할당 없음.
 */
class KarplusStrongVoice(
    private val sampleRate: Int,
    tone: ToneParams = ToneParams.DEFAULT,
    seed: Int = 0x2F6E2B1,
) : Voice {
    private val size = sampleRate / MIN_HZ + 8
    private val delay = FloatArray(size)
    private var w = 0

    private var lp = 0f
    private var a = 0f
    private var g = 0f
    private var tone: ToneParams = tone
    private var noteHz = 0f

    private var delayLen = 0f
    private var delayTarget = 0f
    private var delayStep = 0f
    private var glideLeft = 0

    private var env = 0f
    private var fade = FADE_NONE
    private val fadeStep = 1f / (sampleRate * FADE_MS / 1000f)
    private val glideSamples = (sampleRate * GLIDE_MS / 1000f).toInt().coerceAtLeast(1)

    private var pendingNote = false
    private var pendingHz = 0f
    private var pendingVelocity = 0f

    private var active = false
    private var quietChunks = 0
    private var rng = if (seed == 0) 1 else seed

    init {
        g = tone.feedback()
    }

    override val isActive: Boolean get() = active

    /** 컷오프·피드백을 바꾼다. 울리는 중이면 필터는 바로 바뀌고, 음높이 보정은 다음 noteOn/setPitch부터 반영된다. */
    fun setTone(tone: ToneParams) {
        this.tone = tone
        g = tone.feedback()
        if (active) a = coefficientFor(noteHz)
    }

    private fun coefficientFor(hz: Float): Float =
        (1.0 - exp(-2.0 * Math.PI * tone.cutoffHz(hz) / sampleRate)).toFloat().coerceIn(0.001f, 1f)

    override fun noteOn(hz: Float, velocity: Float) {
        if (active && env > 0f) {
            // 울리는 중: 출력만 0으로 내린 뒤 그 순간에 새 버스트를 넣는다(클릭 없는 재피킹).
            pendingNote = true
            pendingHz = hz
            pendingVelocity = velocity
            fade = FADE_OUT
        } else {
            excite(hz, velocity)
            env = 1f
            fade = FADE_NONE
        }
    }

    override fun setPitch(hz: Float) {
        if (!active) return
        if (fade == FADE_OUT) {
            if (pendingNote) pendingHz = hz
            return
        }
        noteHz = hz
        a = coefficientFor(hz)
        delayTarget = delayFor(hz)
        glideLeft = glideSamples
        delayStep = (delayTarget - delayLen) / glideSamples
    }

    override fun silence() {
        if (!active) return
        pendingNote = false
        fade = FADE_OUT
    }

    override fun render(out: FloatArray, offset: Int, frames: Int) {
        if (!active) return
        var peak = 0f
        for (i in 0 until frames) {
            if (fade == FADE_OUT) {
                env -= fadeStep
                if (env <= 0f) {
                    env = 0f
                    if (pendingNote) {
                        pendingNote = false
                        excite(pendingHz, pendingVelocity)
                        fade = FADE_IN
                    } else {
                        deactivate()
                        return
                    }
                }
            } else if (fade == FADE_IN) {
                env += fadeStep
                if (env >= 1f) {
                    env = 1f
                    fade = FADE_NONE
                }
            }

            if (glideLeft > 0) {
                glideLeft--
                delayLen = if (glideLeft == 0) delayTarget else delayLen + delayStep
            }

            var pos = w - delayLen
            if (pos < 0f) pos += size
            var i0 = pos.toInt()
            val frac = pos - i0
            if (i0 >= size) i0 -= size
            var i1 = i0 + 1
            if (i1 >= size) i1 -= size
            val x = delay[i0] + frac * (delay[i1] - delay[i0])

            lp += a * (x - lp)
            delay[w] = g * lp
            w++
            if (w >= size) w = 0

            val level = abs(lp)
            if (level > peak) peak = level
            out[offset + i] += lp * env
        }

        if (peak < SILENCE_THRESHOLD) {
            quietChunks++
            if (quietChunks >= QUIET_CHUNKS_TO_SLEEP) deactivate()
        } else {
            quietChunks = 0
        }
    }

    /** 루프 한 바퀴가 정확히 한 주기가 되도록 로우패스의 위상 지연을 뺀 딜레이 길이. */
    private fun delayFor(hz: Float): Float {
        val f = hz.coerceIn(MIN_HZ.toFloat(), sampleRate / 4f)
        val omega = 2.0 * Math.PI * f / sampleRate
        val b = 1.0 - a
        val phaseDelay = atan2(b * sin(omega), 1.0 - b * cos(omega)) / omega
        val len = sampleRate / f - phaseDelay
        return len.toFloat().coerceIn(2f, (size - 4).toFloat())
    }

    /** 딜레이 라인을 비우고 최근 한 주기 분량을 로우패스 거른 노이즈로 채운다. 피크 = velocity. */
    private fun excite(hz: Float, velocity: Float) {
        noteHz = hz
        a = coefficientFor(hz)
        delayLen = delayFor(hz)
        delayTarget = delayLen
        glideLeft = 0
        java.util.Arrays.fill(delay, 0f)
        lp = 0f

        val n = delayLen.toInt() + 2
        var start = w - n
        if (start < 0) start += size

        var s = 0f
        var sum = 0f
        var idx = start
        for (k in 0 until n) {
            s += a * (nextNoise() - s)
            delay[idx] = s
            sum += s
            idx++
            if (idx >= size) idx = 0
        }
        val mean = sum / n
        var peak = 0f
        idx = start
        for (k in 0 until n) {
            val v = delay[idx] - mean
            delay[idx] = v
            val m = abs(v)
            if (m > peak) peak = m
            idx++
            if (idx >= size) idx = 0
        }
        val scale = if (peak > 1e-9f) velocity.coerceIn(0f, 1f) / peak else 0f
        idx = start
        for (k in 0 until n) {
            delay[idx] *= scale
            idx++
            if (idx >= size) idx = 0
        }

        active = true
        quietChunks = 0
    }

    private fun deactivate() {
        active = false
        env = 0f
        fade = FADE_NONE
        pendingNote = false
        glideLeft = 0
        lp = 0f
        java.util.Arrays.fill(delay, 0f)
    }

    /** xorshift32 → [−1, 1). 할당 없음. */
    private fun nextNoise(): Float {
        var x = rng
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        rng = x
        return x * (1f / 2147483648f)
    }

    private companion object {
        const val MIN_HZ = 25
        const val FADE_MS = 2f
        const val GLIDE_MS = 8f
        const val SILENCE_THRESHOLD = 1e-4f
        const val QUIET_CHUNKS_TO_SLEEP = 4
        const val FADE_NONE = 0
        const val FADE_OUT = 1
        const val FADE_IN = 2
    }
}
