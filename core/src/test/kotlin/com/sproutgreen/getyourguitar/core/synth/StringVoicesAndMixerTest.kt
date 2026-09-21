package com.sproutgreen.getyourguitar.core.synth

import com.sproutgreen.getyourguitar.core.music.Pitch
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StringVoicesAndMixerTest {
    private val sr = 48_000

    private fun render(voices: StringVoices, frames: Int): FloatArray {
        val out = FloatArray(frames)
        voices.render(out, 0, frames)
        return out
    }

    @Test
    fun `strings sound independently`() {
        val voices = StringVoices(4, sr)
        assertFalse(voices.anyActive)
        voices.noteOn(0, Pitch.hz(28), 0.8f)
        voices.noteOn(3, Pitch.hz(43), 0.8f)
        assertTrue(voices.anyActive)
        val both = render(voices, sr / 2)

        val solo = StringVoices(4, sr)
        solo.noteOn(0, Pitch.hz(28), 0.8f)
        val one = render(solo, sr / 2)
        assertTrue(SignalAnalysis.rms(one) > 0.01f)
        assertTrue(SignalAnalysis.rms(both) > SignalAnalysis.rms(one) * 1.1f, "second string should add energy")
    }

    @Test
    fun `last note on a string wins`() {
        val voices = StringVoices(4, sr)
        voices.noteOn(1, Pitch.hz(33), 0.8f)
        render(voices, sr / 10)
        voices.noteOn(1, Pitch.hz(45), 0.8f)
        val out = render(voices, sr)
        val hz = SignalAnalysis.estimateHz(out, sr, Pitch.hz(45), from = 4000, length = 16_000)
        assertTrue(abs(hz - Pitch.hz(45)) / Pitch.hz(45) < 0.01f, "measured $hz")
    }

    @Test
    fun `silenceAll stops every string`() {
        val voices = StringVoices(4, sr)
        for (s in 0..3) voices.noteOn(s, Pitch.hz(28 + 5 * s), 0.8f)
        render(voices, 1000)
        voices.silenceAll()
        render(voices, sr / 20)
        assertFalse(voices.anyActive)
        assertEquals(0f, SignalAnalysis.peak(render(voices, 1000)))
    }

    @Test
    fun `out of range string index is ignored`() {
        val voices = StringVoices(4, sr)
        voices.noteOn(4, 100f, 0.8f)
        voices.noteOn(-1, 100f, 0.8f)
        voices.setPitch(9, 100f)
        assertFalse(voices.anyActive)
    }

    @Test
    fun `mixer keeps four full-scale voices inside minus one to one`() {
        val buf = FloatArray(1000) { if (it % 2 == 0) 4f else -4f } // 4보이스가 동시에 ±1
        Mixer.process(buf, buf.size, masterGain = 1f)
        for (x in buf) assertTrue(abs(x) < 1f, "sample $x escaped (-1, 1)")
        val extreme = floatArrayOf(1e6f, -1e6f)
        Mixer.process(extreme, 2, masterGain = 1f)
        for (x in extreme) assertTrue(abs(x) <= 1f && !x.isNaN())
    }

    @Test
    fun `mixer output grows with master gain and zero gain is silent`() {
        fun level(gain: Float): Float {
            val buf = FloatArray(16) { 0.5f }
            Mixer.process(buf, buf.size, gain)
            return buf[0]
        }
        assertEquals(0f, level(0f))
        assertTrue(level(0.25f) < level(0.5f) && level(0.5f) < level(1f))
        // 작은 신호에서는 거의 선형: 0.01 * 0.5(프리게인) * gain
        val small = FloatArray(4) { 0.01f }
        Mixer.process(small, 4, 1f)
        assertEquals(0.005f, small[0], 0.0001f)
    }

    @Test
    fun `mixer only touches the requested frames`() {
        val buf = FloatArray(8) { 0.5f }
        Mixer.process(buf, 4, 1f)
        assertEquals(0.5f, buf[4])
        assertTrue(buf[0] < 0.5f)
    }

    // ---- 리미터·앰프 필터 (피드백 2026-09-21: "소리가 구리다") ----

    /** v1.0.0의 x/(1+|x|)는 선형 구간이 없어 보통 음량에서도 9~23% 눌렸다. */
    @Test
    fun `limiter is exactly linear below the knee`() {
        for (level in floatArrayOf(0.05f, 0.2f, 0.6f, 1.0f, -1.0f)) {
            val buf = floatArrayOf(level)
            Mixer.process(buf, 1, masterGain = 1f)
            assertEquals(level * Mixer.PRE_GAIN, buf[0], 0f, "input $level must pass untouched")
        }
        assertTrue(Mixer.KNEE >= 0.5f)
    }

    @Test
    fun `limiter is continuous and monotonic through the knee`() {
        var previous = 0f
        var x = 0f
        while (x < 8f) {
            val buf = floatArrayOf(x)
            Mixer.process(buf, 1, 1f)
            assertTrue(buf[0] >= previous, "not monotonic at $x")
            assertTrue(buf[0] - previous < 0.01f, "jump at $x: $previous -> ${buf[0]}")
            previous = buf[0]
            x += 0.01f
        }
        assertTrue(previous > 0.95f && previous <= 1f)
    }

    private fun sineThroughAmp(hz: Float): Float {
        val amp = AmpFilter(sr)
        val buf = FloatArray(sr / 2) { Math.sin(2.0 * Math.PI * hz * it / sr).toFloat() }
        amp.process(buf, buf.size)
        return SignalAnalysis.rms(buf, sr / 4, sr / 2) / (1f / Math.sqrt(2.0).toFloat())
    }

    @Test
    fun `amp filter passes the bass range and rolls off the fizz`() {
        assertEquals(1f, sineThroughAmp(60f), 0.05f)
        assertEquals(1f, sineThroughAmp(400f), 0.08f)
        assertTrue(sineThroughAmp(2_000f) > 0.7f)
        assertTrue(sineThroughAmp(10_000f) < 0.2f, "10 kHz gain ${sineThroughAmp(10_000f)}")
    }

    @Test
    fun `amp filter falls to exact silence after the input stops`() {
        val amp = AmpFilter(sr)
        val buf = FloatArray(1000) { 0.5f }
        amp.process(buf, buf.size)
        // 상태는 가장 작은 비정규수에서 멈춘다(s − a·s 의 a·s 가 0으로 반올림). 블록 끝의 플러시가 이를 0으로 만든다.
        val tail = FloatArray(sr / 50)
        amp.process(tail, tail.size)
        assertTrue(Math.abs(tail.last()) < 1e-30f)
        val next = FloatArray(64)
        amp.process(next, next.size)
        for (x in next) assertEquals(0f, x)
    }
}
