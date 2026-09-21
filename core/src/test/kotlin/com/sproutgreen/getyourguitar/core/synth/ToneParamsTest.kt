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
}
