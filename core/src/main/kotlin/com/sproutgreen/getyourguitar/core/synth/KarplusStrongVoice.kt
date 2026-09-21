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
 * **음색**(v1.0.0의 "소리가 구리다" 피드백 이후):
 * - 여기 신호는 백색 노이즈가 아니라 **당겼다 놓은 현의 속도 파형**이다. 위치 β에서 당긴 현의 속도는
 *   길이 β·N의 양의 구간과 나머지 음의 구간으로 된 사각 펄스이고, 배음 세기는 sin(kπβ)/k 가 된다.
 *   여기에 손끝 질감용 노이즈를 조금만 섞는다([VoiceCharacter.noiseMix]) → 칠 때마다 거의 같은 음색.
 * - 출력은 루프 신호에서 γ·N만큼 지연된 자기 자신을 뺀 것이다 = **픽업 위치 빗살 필터**, 배음 k에 sin(kπγ).
 *   루프 밖에서 빼므로 음높이·감쇠에는 영향이 없고 DC도 없어진다.
 * - 튕기는 손과 픽업은 브리지에서 고정된 거리에 있다. [openHz]를 알면 β·γ를 (연주음 / 개방현음)만큼 키워,
 *   높은 프렛일수록 둥글어지는 실제 악기의 특성을 낸다.
 *
 * 오디오 스레드 전용: 생성자 이후 할당 없음.
 */
class KarplusStrongVoice(
    private val sampleRate: Int,
    tone: ToneParams = ToneParams.DEFAULT,
    seed: Int = 0x2F6E2B1,
    /** 이 보이스가 맡은 줄의 개방현 주파수. 0이면 모른다고 보고 모든 음을 개방현처럼 다룬다. */
    private val openHz: Float = 0f,
    private val character: VoiceCharacter = VoiceCharacter.DEFAULT,
) : Voice {
    private val size = sampleRate / MIN_HZ + 8
    private val delay = FloatArray(size)
    private var w = 0

    private var lp = 0f
    private var a = 0f
    private var g = 0f
    private var brightness = tone.brightness
    private var decay = tone.decay
    private var noteHz = 0f

    /** 픽업 위치(울리는 현 길이에 대한 비율). 피킹할 때만 정한다 — 울리는 중에 바꾸면 탭이 튀어 클릭이 난다. */
    private var pickup = character.pickupPosition

    /** 음마다 다른 기음 세기를 맞추는 출력 게인. 개방현에서 [OUTPUT_GAIN], 높은 프렛일수록 작다. */
    private var noteGain = OUTPUT_GAIN
    private val openStringFundamental = character.fundamentalGain(character.pluckPosition, character.pickupPosition)

    private var delayLen = 0f
    private var delayTarget = 0f
    private var delayStep = 0f
    private var glideLeft = 0

    private var env = 0f
    private var fade = FADE_NONE
    private val fadeStep = 1f / (sampleRate * FADE_MS / 1000f)
    private val releaseStep = 1f / (sampleRate * RELEASE_MS / 1000f)
    private var fadeOutStep = fadeStep

    /** 슬라이드 에너지 딥의 남은 샘플 수. 0이면 딥 없음. */
    private var dipLeft = 0
    private val glideSamples = (sampleRate * GLIDE_MS / 1000f).toInt().coerceAtLeast(1)

    private var pendingNote = false
    private var pendingHz = 0f
    private var pendingVelocity = 0f

    private var active = false
    private var quietChunks = 0
    private var rng = if (seed == 0) 1 else seed

    init {
        g = ToneParams.feedback(decay)
    }

    override val isActive: Boolean get() = active

    /** 컷오프·피드백을 바꾼다. 울리는 중이면 필터는 바로 바뀌고, 음높이 보정은 다음 noteOn/setPitch부터 반영된다. */
    fun setTone(tone: ToneParams) = setTone(tone.brightness, tone.decay)

    /** 객체를 만들지 않는 경로. 엔진이 오디오 스레드에서 부른다. */
    fun setTone(brightness: Float, decay: Float) {
        this.brightness = brightness.coerceIn(0f, 1f)
        this.decay = decay.coerceIn(0f, 1f)
        g = ToneParams.feedback(this.decay)
        if (active) a = coefficientFor(noteHz)
    }

    private fun coefficientFor(hz: Float): Float =
        (1.0 - exp(-2.0 * Math.PI * ToneParams.cutoffHz(brightness, hz) / sampleRate)).toFloat().coerceIn(0.001f, 1f)

    override fun noteOn(hz: Float, velocity: Float) {
        if (active && env > 0f) {
            // 울리는 중: 출력만 0으로 내린 뒤 그 순간에 새 버스트를 넣는다(클릭 없는 재피킹).
            pendingNote = true
            pendingHz = hz
            pendingVelocity = velocity
            fade = FADE_OUT
            fadeOutStep = fadeStep // 릴리스 중이었더라도 새 피킹은 빨리 받아야 한다
        } else {
            excite(hz, velocity)
            env = 1f
            fade = FADE_NONE
        }
    }

    override fun setPitch(hz: Float) {
        startGlide(hz)
    }

    override fun slideTo(hz: Float) {
        if (startGlide(hz)) dipLeft = glideSamples
    }

    /** 글라이드를 시작했으면 true. 비활성이거나 꺼지는 중이면 시작하지 않는다. */
    private fun startGlide(hz: Float): Boolean {
        if (!active) return false
        if (fade == FADE_OUT) {
            if (pendingNote) pendingHz = hz
            return false
        }
        noteHz = hz
        a = coefficientFor(hz)
        delayTarget = delayFor(hz)
        glideLeft = glideSamples
        delayStep = (delayTarget - delayLen) / glideSamples
        return true
    }

    override fun noteOff() {
        if (!active || fade == FADE_OUT) return
        pendingNote = false
        fade = FADE_OUT
        fadeOutStep = releaseStep
    }

    override fun silence() {
        if (!active) return
        pendingNote = false
        fade = FADE_OUT
        fadeOutStep = fadeStep
    }

    override fun render(out: FloatArray, offset: Int, frames: Int) {
        if (!active) return
        var peak = 0f
        for (i in 0 until frames) {
            if (fade == FADE_OUT) {
                env -= fadeOutStep
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
            val current = g * lp
            delay[w] = current

            // 픽업: 지금 값 − (γ·N 전의 값). 방금 쓴 delay[w]가 지연 0이다.
            var tapPos = w - pickup * delayLen
            if (tapPos < 0f) tapPos += size
            var t0 = tapPos.toInt()
            val tapFrac = tapPos - t0
            if (t0 >= size) t0 -= size
            var t1 = t0 + 1
            if (t1 >= size) t1 -= size
            val tapped = delay[t0] + tapFrac * (delay[t1] - delay[t0])

            w++
            if (w >= size) w = 0

            val level = abs(lp)
            if (level > peak) peak = level

            var gain = noteGain * env
            if (dipLeft > 0) {
                dipLeft--
                // 0 → 1 → 0 삼각형: 글라이드 한가운데서 가장 깊고, 끝나면 정확히 1로 돌아온다.
                val progress = 1f - dipLeft.toFloat() / glideSamples
                val triangle = 1f - abs(2f * progress - 1f)
                gain *= 1f - SLIDE_DIP * triangle
            }
            out[offset + i] += (current - tapped) * gain
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

    /**
     * 딜레이 라인을 비우고 최근 한 주기 분량을 "당겼다 놓은 현"의 속도 파형으로 채운다. 피크 = velocity.
     * 파형은 주기적이므로 스무딩 필터를 두 바퀴 돌려 두 번째 바퀴의 값만 쓴다(첫 바퀴는 필터 상태 예열).
     */
    private fun excite(hz: Float, velocity: Float) {
        noteHz = hz
        a = coefficientFor(hz)
        delayLen = delayFor(hz)
        delayTarget = delayLen
        glideLeft = 0
        java.util.Arrays.fill(delay, 0f)
        lp = 0f

        val upTheNeck = if (openHz > 0f) (hz / openHz).coerceIn(1f, MAX_NECK_RATIO) else 1f
        val pluck = (character.pluckPosition * upTheNeck).coerceAtMost(0.5f)
        pickup = (character.pickupPosition * upTheNeck).coerceAtMost(0.5f)
        val louderUpTheNeck = character.fundamentalGain(pluck, pickup) / openStringFundamental
        noteGain = OUTPUT_GAIN / Math.pow(louderUpTheNeck.toDouble(), character.neckCompensation.toDouble()).toFloat()

        val n = delayLen.toInt() + 2
        var start = w - n
        if (start < 0) start += size
        val pulseLength = pluck * n
        val fallingEdge = pulseLength.toInt().coerceIn(1, n - 1)
        val edge = character.snap * SNAP_SCALE

        var s = 0f
        var sum = 0f
        var idx = start
        for (pass in 0..1) {
            idx = start
            sum = 0f
            for (k in 0 until n) {
                var shape = if (k < pulseLength) 1f - pluck else -pluck
                if (k == 0) shape += edge else if (k == fallingEdge) shape -= edge
                val raw = shape * (1f - character.noiseMix) + nextNoise() * character.noiseMix
                s += a * (raw - s)
                delay[idx] = s
                sum += s
                idx++
                if (idx >= size) idx = 0
            }
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
        dipLeft = 0
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
        /** 임펄스는 스무딩 필터를 지나며 계수 a(≈0.25)배로 낮아지므로 snap = 1이 펄스 높이와 비슷해지도록 키운다. */
        const val SNAP_SCALE = 4f

        /** 24프렛(개방현의 4배)까지만 비율을 키운다. */
        const val MAX_NECK_RATIO = 4f

        /**
         * 개방현 피크가 velocity의 약 1.25배가 되는 값. 새 파형은 에너지가 기음에 몰려 있어,
         * 폰 스피커가 재생하는 중역(250 Hz~2 kHz)을 v1.0.0 수준으로 맞추려면 전체를 이만큼 올려야 한다.
         * 화음의 피크는 Mixer의 리미터가 받는다.
         */
        const val OUTPUT_GAIN = 1.1f

        const val MIN_HZ = 25
        const val FADE_MS = 2f

        /** noteOff의 길이. 출력만 내리는 선형 페이드라 음높이와 무관하다(E1은 주기가 24 ms라 루프 감쇠로는 못 맞춘다). */
        const val RELEASE_MS = 30f

        /** 슬라이드 중 가장 깊은 지점의 음량 감소(스펙 5.2의 0.85). */
        const val SLIDE_DIP = 0.15f
        const val GLIDE_MS = 8f
        const val SILENCE_THRESHOLD = 1e-4f
        const val QUIET_CHUNKS_TO_SLEEP = 4
        const val FADE_NONE = 0
        const val FADE_OUT = 1
        const val FADE_IN = 2
    }
}
