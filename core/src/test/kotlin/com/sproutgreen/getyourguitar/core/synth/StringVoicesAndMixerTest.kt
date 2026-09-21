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
}
