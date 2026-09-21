package com.sproutgreen.getyourguitar.core.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FretboardTest {
    private val board = Fretboard(Tuning.STANDARD_BASS_4)
    private val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    @Test
    fun `standard tuning is E1 A1 D2 G2 with string 0 lowest`() {
        val t = Tuning.STANDARD_BASS_4
        assertEquals(4, t.stringCount)
        assertEquals(listOf(28, 33, 38, 43), (0 until 4).map { t.openMidi(it) })
    }

    @Test
    fun `every position matches the expected midi and name table`() {
        val open = intArrayOf(28, 33, 38, 43)
        var checked = 0
        for (string in 0..3) {
            for (fret in 0..24) {
                val midi = open[string] + fret
                assertEquals(midi, board.midiAt(string, fret), "midi at s$string f$fret")
                assertEquals(names[midi % 12], board.nameAt(string, fret), "name at s$string f$fret")
                assertEquals(Pitch.hz(midi), board.hzAt(string, fret), 1e-4f)
                checked++
            }
        }
        assertEquals(100, checked)
    }

    @Test
    fun `landmark positions`() {
        assertEquals("E", board.nameAt(0, 0))
        assertEquals("A", board.nameAt(0, 5))
        assertEquals("E", board.nameAt(0, 12))
        assertEquals("G", board.nameAt(3, 24))
        assertEquals(67, board.midiAt(3, 24))
        assertEquals(41.203f, board.hzAt(0, 0), 0.01f)
    }

    @Test
    fun `FretPosition overloads agree with int overloads`() {
        val pos = FretPosition(string = 2, fret = 7)
        assertEquals(board.midiAt(2, 7), board.midiAt(pos))
        assertEquals(board.hzAt(2, 7), board.hzAt(pos))
        assertEquals(board.nameAt(2, 7), board.nameAt(pos))
    }

    @Test
    fun `contains reports valid positions without throwing`() {
        assertEquals(true, board.contains(0, 0))
        assertEquals(true, board.contains(3, 24))
        assertEquals(false, board.contains(4, 0))
        assertEquals(false, board.contains(-1, 0))
        assertEquals(false, board.contains(0, 25))
        assertEquals(false, board.contains(0, -1))
    }

    @Test
    fun `out of range positions throw`() {
        assertFailsWith<IllegalArgumentException> { board.midiAt(4, 0) }
        assertFailsWith<IllegalArgumentException> { board.midiAt(0, 25) }
        assertFailsWith<IllegalArgumentException> { board.midiAt(-1, 3) }
    }
}
