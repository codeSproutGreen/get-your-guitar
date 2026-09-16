package com.sproutgreen.getyourguitar.core

import kotlin.test.Test
import kotlin.test.assertEquals

class CoreInfoTest {
    @Test
    fun `app name is provided by the core module`() {
        assertEquals("get-your-guitar", CoreInfo.APP_NAME)
    }
}
