package com.sproutgreen.getyourguitar.core

import kotlin.test.Test
import kotlin.test.assertEquals

/** M0 자리표시 테스트. JUnit Platform이 :core 테스트를 실제로 실행하는지만 확인한다. M1에서 실제 테스트로 대체. */
class PipelineSmokeTest {
    @Test
    fun `junit platform runs core tests`() {
        assertEquals(4, 2 + 2)
    }
}
