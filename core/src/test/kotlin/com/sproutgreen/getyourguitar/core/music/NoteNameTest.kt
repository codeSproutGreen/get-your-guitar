package com.sproutgreen.getyourguitar.core.music

import kotlin.test.Test
import kotlin.test.assertEquals

class NoteNameTest {
    @Test
    fun `twelve names in sharp notation starting at C`() {
        val expected = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        assertEquals(expected, (60..71).map { NoteName.of(it) })
    }

    @Test
    fun `names repeat every octave`() {
        for (midi in 0..115) {
            assertEquals(NoteName.of(midi), NoteName.of(midi + 12))
        }
    }

    @Test
    fun `open bass strings are E A D G`() {
        assertEquals(listOf("E", "A", "D", "G"), listOf(28, 33, 38, 43).map { NoteName.of(it) })
    }
}
