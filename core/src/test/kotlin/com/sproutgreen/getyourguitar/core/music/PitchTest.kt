package com.sproutgreen.getyourguitar.core.music

import kotlin.test.Test
import kotlin.test.assertEquals

class PitchTest {
    @Test
    fun `A4 is 440 Hz`() {
        assertEquals(440.0f, Pitch.hz(69), 1e-3f)
    }

    @Test
    fun `E1 is 41_20 Hz`() {
        assertEquals(41.203f, Pitch.hz(28), 0.01f)
    }

    @Test
    fun `G4 is 392 Hz`() {
        assertEquals(391.995f, Pitch.hz(67), 0.01f)
    }

    @Test
    fun `an octave doubles the frequency`() {
        for (midi in 20..80) {
            assertEquals(Pitch.hz(midi) * 2f, Pitch.hz(midi + 12), Pitch.hz(midi) * 1e-5f)
        }
    }
}
