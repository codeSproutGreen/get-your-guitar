package com.sproutgreen.getyourguitar.core.synth

import com.sproutgreen.getyourguitar.core.music.Pitch
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KarplusStrongVoiceTest {
    private val sr = 48_000
    private fun voice() = KarplusStrongVoice(sr)

    private fun assertHz(expected: Float, actual: Float, tolerance: Float = 0.01f) {
        val error = abs(actual - expected) / expected
        assertTrue(error <= tolerance, "expected $expected Hz, measured $actual Hz (error ${error * 100}%)")
    }

    @Test
    fun `a new voice is silent and inactive`() {
        val v = voice()
        assertFalse(v.isActive)
        val out = SignalAnalysis.render(v, 4800)
        assertEquals(0f, SignalAnalysis.peak(out))
    }

    @Test
    fun `E1 fundamental is within one percent`() {
        val v = voice()
        v.noteOn(Pitch.hz(28), 0.8f)
        val out = SignalAnalysis.render(v, sr)
        assertHz(Pitch.hz(28), SignalAnalysis.estimateHz(out, sr, Pitch.hz(28), from = 4000, length = 30_000))
    }

    @Test
    fun `A2 fundamental is within one percent`() {
        val v = voice()
        v.noteOn(Pitch.hz(45), 0.8f)
        val out = SignalAnalysis.render(v, sr)
        assertHz(Pitch.hz(45), SignalAnalysis.estimateHz(out, sr, Pitch.hz(45), from = 4000, length = 16_000))
    }

    @Test
    fun `G4 fundamental is within one percent despite loop filter delay`() {
        val v = voice()
        v.noteOn(Pitch.hz(67), 0.8f)
        val out = SignalAnalysis.render(v, sr / 2)
        assertHz(Pitch.hz(67), SignalAnalysis.estimateHz(out, sr, Pitch.hz(67), from = 1000, length = 8000))
    }

    @Test
    fun `pluck peak follows velocity`() {
        val loud = voice().also { it.noteOn(110f, 0.8f) }
        val soft = voice().also { it.noteOn(110f, 0.2f) }
        val pLoud = SignalAnalysis.peak(SignalAnalysis.render(loud, 4800))
        val pSoft = SignalAnalysis.peak(SignalAnalysis.render(soft, 4800))
        assertTrue(pLoud in 0.6f..1.3f, "loud peak $pLoud") // 개방현 피크 ≈ velocity × 1.25 (OUTPUT_GAIN 주석 참고)
        assertEquals(4f, pLoud / pSoft, 0.2f)
    }

    @Test
    fun `level decays window by window`() {
        val v = voice()
        v.noteOn(Pitch.hz(50), 0.8f) // D3, 눈에 띄게 감쇠하는 음역
        val out = SignalAnalysis.render(v, sr * 2)
        val window = sr / 10
        val levels = (0 until 20).map { SignalAnalysis.rms(out, it * window, (it + 1) * window) }
        for (i in 1 until levels.size) {
            assertTrue(levels[i] <= levels[i - 1] * 1.02f, "window $i rose: ${levels[i - 1]} -> ${levels[i]}")
        }
        assertTrue(levels.last() < levels.first() * 0.8f, "no clear decay: ${levels.first()} -> ${levels.last()}")
    }

    @Test
    fun `render adds to the buffer instead of overwriting`() {
        val a = voice().also { it.noteOn(110f, 0.8f) }
        val b = voice().also { it.noteOn(110f, 0.8f) }
        val plain = FloatArray(512)
        a.render(plain, 0, 512)
        val biased = FloatArray(512) { 0.25f }
        b.render(biased, 0, 512)
        for (i in plain.indices) assertEquals(plain[i] + 0.25f, biased[i], 1e-6f)
    }

    @Test
    fun `render respects offset and frame count`() {
        val v = voice().also { it.noteOn(220f, 0.8f) }
        val out = FloatArray(300)
        v.render(out, 100, 100)
        assertEquals(0f, SignalAnalysis.peak(out, 0, 100))
        assertTrue(SignalAnalysis.peak(out, 100, 200) > 0f)
        assertEquals(0f, SignalAnalysis.peak(out, 200, 300))
    }

    @Test
    fun `setPitch glides without a click and lands on the target`() {
        val from = Pitch.hz(33 + 5) // A줄 5프렛
        val to = Pitch.hz(33 + 7)   // A줄 7프렛
        val v = voice()
        v.noteOn(from, 0.8f)
        val before = SignalAnalysis.render(v, sr / 2)
        val steadyStep = SignalAnalysis.maxStep(before, sr / 4, sr / 2)

        v.setPitch(to)
        val after = SignalAnalysis.render(v, sr)
        val glideStep = SignalAnalysis.maxStep(after, 1, sr / 50) // 글라이드 8 ms를 포함한 20 ms
        val joinStep = abs(after[0] - before.last())

        assertTrue(glideStep < steadyStep * 2f, "click during glide: $glideStep vs steady $steadyStep")
        assertTrue(joinStep < steadyStep * 2f, "click at setPitch: $joinStep vs steady $steadyStep")
        assertHz(to, SignalAnalysis.estimateHz(after, sr, to, from = 2000, length = 20_000))
    }

    @Test
    fun `setPitch does not re-excite the string`() {
        val v = voice()
        v.noteOn(Pitch.hz(38), 0.8f)
        val before = SignalAnalysis.render(v, sr)
        v.setPitch(Pitch.hz(40))
        val after = SignalAnalysis.render(v, sr / 10)
        assertTrue(
            SignalAnalysis.rms(after) <= SignalAnalysis.rms(before, sr - sr / 10, sr) * 1.2f,
            "slide should not add energy",
        )
    }

    @Test
    fun `setPitch on an inactive voice stays silent`() {
        val v = voice()
        v.setPitch(110f)
        assertFalse(v.isActive)
        assertEquals(0f, SignalAnalysis.peak(SignalAnalysis.render(v, 2000)))
    }

    @Test
    fun `re-pluck restores the level without a click`() {
        val v = voice()
        v.noteOn(Pitch.hz(50), 0.8f)
        val first = SignalAnalysis.render(v, sr * 3)
        val attackRms = SignalAnalysis.rms(first, 0, sr / 10)
        val tailRms = SignalAnalysis.rms(first, first.size - sr / 10, first.size)
        val attackStep = SignalAnalysis.maxStep(first, 1, sr / 10)
        assertTrue(tailRms < attackRms * 0.5f, "precondition: tail should have decayed")

        v.noteOn(Pitch.hz(50), 0.8f)
        val second = SignalAnalysis.render(v, sr / 5)
        val newRms = SignalAnalysis.rms(second, sr / 100, sr / 100 + sr / 10)
        assertTrue(newRms > attackRms * 0.6f, "re-pluck too quiet: $newRms vs first attack $attackRms")

        val joinStep = abs(second[0] - first.last())
        val crossfadeStep = SignalAnalysis.maxStep(second, 1, sr / 100)
        assertTrue(joinStep < attackStep * 1.5f, "click at re-pluck start: $joinStep")
        assertTrue(crossfadeStep < attackStep * 1.5f, "click inside re-pluck crossfade: $crossfadeStep vs $attackStep")
    }

    @Test
    fun `re-pluck at a new pitch sounds the new pitch`() {
        val v = voice()
        v.noteOn(Pitch.hz(40), 0.8f)
        SignalAnalysis.render(v, sr / 4)
        v.noteOn(Pitch.hz(47), 0.8f)
        val out = SignalAnalysis.render(v, sr)
        assertHz(Pitch.hz(47), SignalAnalysis.estimateHz(out, sr, Pitch.hz(47), from = 4000, length = 20_000))
    }

    @Test
    fun `voice becomes inactive after it has decayed`() {
        val v = voice()
        v.noteOn(Pitch.hz(67), 0.8f) // G4는 빨리 죽는다
        assertTrue(v.isActive)
        var seconds = 0
        while (v.isActive && seconds < 60) {
            SignalAnalysis.render(v, sr)
            seconds++
        }
        assertFalse(v.isActive, "still active after $seconds s")
        assertEquals(0f, SignalAnalysis.peak(SignalAnalysis.render(v, 2000)))
    }

    @Test
    fun `silence fades out quickly and deactivates`() {
        val v = voice()
        v.noteOn(Pitch.hz(28), 0.8f)
        val playing = SignalAnalysis.render(v, sr / 10)
        val steadyStep = SignalAnalysis.maxStep(playing)
        v.silence()
        val tail = SignalAnalysis.render(v, sr / 10)
        assertFalse(v.isActive)
        assertEquals(0f, SignalAnalysis.peak(tail, sr / 50, tail.size), "must be silent 20 ms after silence()")
        assertTrue(SignalAnalysis.maxStep(tail) < steadyStep * 1.5f, "silence() clicked")
    }

    @Test
    fun `same seed gives identical output regardless of chunk size`() {
        val a = KarplusStrongVoice(sr, seed = 42).also { it.noteOn(110f, 0.8f) }
        val b = KarplusStrongVoice(sr, seed = 42).also { it.noteOn(110f, 0.8f) }
        val oa = SignalAnalysis.render(a, 4000, chunk = 97)
        val ob = SignalAnalysis.render(b, 4000, chunk = 1000)
        for (i in oa.indices) assertEquals(oa[i], ob[i], 0f, "sample $i differs")
    }

    /** 실기기 피드백(2026-09-21): G줄 고음 프렛이 너무 짧게 끊겼다. 컷오프 key-tracking 전에는 1초 뒤 약 -56 dB. */
    @Test
    fun `high notes are still clearly audible one second after the pluck`() {
        val v = voice()
        v.noteOn(Pitch.hz(67), 0.8f) // G4 = G줄 24프렛
        val out = SignalAnalysis.render(v, sr * 3 / 2)
        val attack = SignalAnalysis.rms(out, sr / 20, sr / 20 + sr / 10)
        val later = SignalAnalysis.rms(out, sr, sr + sr / 10)
        assertTrue(later > attack * 0.05f, "G4 fell ${20 * kotlin.math.log10(later / attack)} dB in ~1 s (limit -26 dB)")
    }

    @Test
    fun `low notes keep their tone when cutoff tracks pitch`() {
        // E1은 추적 기준점이라 컷오프가 그대로여야 한다 → 3초 뒤에도 충분히 울린다.
        val v = voice()
        v.noteOn(Pitch.hz(28), 0.8f)
        val out = SignalAnalysis.render(v, sr * 3)
        val attack = SignalAnalysis.rms(out, sr / 20, sr / 20 + sr / 5)
        val later = SignalAnalysis.rms(out, sr * 3 - sr / 5, sr * 3)
        assertTrue(later > attack * 0.05f && later < attack, "E1 attack=$attack later=$later")
    }

    // ---- 음색 (피드백 2026-09-21: "소리가 구리다") ----

    private fun correlation(a: FloatArray, b: FloatArray, n: Int): Double {
        var ab = 0.0; var aa = 0.0; var bb = 0.0
        for (i in 0 until n) { ab += a[i].toDouble() * b[i]; aa += a[i].toDouble() * a[i]; bb += b[i].toDouble() * b[i] }
        return ab / Math.sqrt(aa * bb)
    }

    /** 백색 노이즈 여기는 칠 때마다 음색이 달랐다. 당겼다 놓는 파형이 주가 되면 두 번의 피킹이 거의 같은 파형이어야 한다. */
    @Test
    fun `two plucks of the same note have nearly the same waveform`() {
        val a = KarplusStrongVoice(sr, seed = 1).also { it.noteOn(Pitch.hz(33), 0.8f) }
        val b = KarplusStrongVoice(sr, seed = 99).also { it.noteOn(Pitch.hz(33), 0.8f) }
        val c = correlation(SignalAnalysis.render(a, sr / 4), SignalAnalysis.render(b, sr / 4), sr / 4)
        assertTrue(c > 0.9, "plucks differ too much, correlation $c")
    }

    /** 밝기 지표 = 인접 샘플 차이의 RMS / 신호 RMS. 노이즈 여기(v1.0.0)는 E1에서 0.19 안팎이었다. */
    @Test
    fun `a low note is not hissy`() {
        val v = voice().also { it.noteOn(Pitch.hz(28), 0.8f) }
        val out = SignalAnalysis.render(v, sr / 2)
        val diff = FloatArray(out.size - 1) { out[it + 1] - out[it] }
        val brightness = SignalAnalysis.rms(diff) / SignalAnalysis.rms(out)
        assertTrue(brightness < 0.12f, "brightness index $brightness")
    }

    @Test
    fun `knowing the open string pitch keeps the tuning and rounds the tone up the neck`() {
        fun brightness(openHz: Float): Float {
            val v = KarplusStrongVoice(sr, openHz = openHz).also { it.noteOn(Pitch.hz(55), 0.8f) } // G3
            val out = SignalAnalysis.render(v, sr / 2)
            assertHz(Pitch.hz(55), SignalAnalysis.estimateHz(out, sr, Pitch.hz(55), from = 2000, length = 12_000))
            val diff = FloatArray(out.size - 1) { out[it + 1] - out[it] }
            return SignalAnalysis.rms(diff) / SignalAnalysis.rms(out)
        }
        val asOpenString = brightness(openHz = Pitch.hz(55)) // 개방현으로 G3을 낼 때
        val atTwelfthFret = brightness(openHz = Pitch.hz(43)) // G줄 12프렛으로 G3을 낼 때
        assertTrue(atTwelfthFret < asOpenString, "12th fret should sound rounder: $atTwelfthFret vs $asOpenString")
    }

    @Test
    fun `output has no DC offset`() {
        val v = voice().also { it.noteOn(Pitch.hz(40), 0.8f) }
        val out = SignalAnalysis.render(v, sr)
        var sum = 0.0
        for (x in out) sum += x
        assertTrue(Math.abs(sum / out.size) < 1e-3, "mean ${sum / out.size}")
    }
}
