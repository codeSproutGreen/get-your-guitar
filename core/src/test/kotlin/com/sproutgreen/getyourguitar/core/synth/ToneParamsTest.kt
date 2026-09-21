package com.sproutgreen.getyourguitar.core.synth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToneParamsTest {
    @Test
    fun `brightness maps 0 to 500 Hz and 1 to 6 kHz logarithmically`() {
        assertEquals(500f, ToneParams(0f, 0.5f).cutoffHz(), 0.5f)
        assertEquals(6000f, ToneParams(1f, 0.5f).cutoffHz(), 5f)
        // 로그 보간이면 중간값은 기하평균
        assertEquals(1732f, ToneParams(0.5f, 0.5f).cutoffHz(), 2f)
    }

    @Test
    fun `decay maps 0 to 0_990 and 1 to 0_9995 linearly`() {
        assertEquals(0.990f, ToneParams(0.5f, 0f).feedback(), 1e-6f)
        assertEquals(0.9995f, ToneParams(0.5f, 1f).feedback(), 1e-6f)
        assertEquals(0.99475f, ToneParams(0.5f, 0.5f).feedback(), 1e-6f)
    }

    @Test
    fun `out of range values are clamped`() {
        val t = ToneParams(-3f, 7f)
        assertEquals(0f, t.brightness)
        assertEquals(1f, t.decay)
    }

    @Test
    fun `default tone is brightness 0_6 decay 0_7`() {
        assertEquals(0.6f, ToneParams.DEFAULT.brightness)
        assertEquals(0.7f, ToneParams.DEFAULT.decay)
        assertTrue(ToneParams.DEFAULT.feedback() < 1f)
    }

    @Test
    fun `cutoff for a note equals the base cutoff at E1 and rises with the square root of pitch`() {
        val tone = ToneParams.DEFAULT
        val base = tone.cutoffHz()
        assertEquals(base, tone.cutoffHz(noteHz = 41.2f), base * 0.001f)
        // 두 옥타브 위(4배) → 컷오프 2배
        assertEquals(base * 2f, tone.cutoffHz(noteHz = 41.2f * 4f), base * 0.01f)
        assertTrue(tone.cutoffHz(392f) > tone.cutoffHz(196f))
    }

    @Test
    fun `cutoff for a note never drops below the base and is capped`() {
        val tone = ToneParams(1f, 0.5f)
        assertEquals(tone.cutoffHz(), tone.cutoffHz(noteHz = 20f), 1f)
        assertTrue(tone.cutoffHz(noteHz = 5000f) <= ToneParams.MAX_TRACKED_CUTOFF_HZ)
    }
}
