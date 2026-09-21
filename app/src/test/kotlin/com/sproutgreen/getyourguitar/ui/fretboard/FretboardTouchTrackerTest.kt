package com.sproutgreen.getyourguitar.ui.fretboard

import com.sproutgreen.getyourguitar.core.engine.Command
import kotlin.test.Test
import kotlin.test.assertEquals

/** 스펙 6.2 "제스처 → 커맨드" 표의 각 행. */
class FretboardTouchTrackerTest {
    private val sent = mutableListOf<Command>()
    private val sounded = mutableListOf<Pair<Int, Int>>()
    private val tracker = FretboardTouchTracker(
        send = { sent += it },
        onSounded = { string, fret -> sounded += string to fret },
    )

    @Test
    fun `down on the board plucks`() {
        tracker.down(1L, string = 2, fret = 5)
        assertEquals(listOf<Command>(Command.NoteOn(2, 5)), sent)
        assertEquals(listOf(2 to 5), sounded)
    }

    @Test
    fun `move to another fret on the same string slides`() {
        tracker.down(1L, 2, 5)
        tracker.move(1L, 2, 6)
        tracker.move(1L, 2, 7)
        assertEquals(listOf(Command.NoteOn(2, 5), Command.Slide(2, 6), Command.Slide(2, 7)), sent)
        assertEquals(listOf(2 to 5, 2 to 6, 2 to 7), sounded)
    }

    @Test
    fun `move onto another string plucks it and leaves the old one ringing`() {
        tracker.down(1L, 3, 4)
        tracker.move(1L, 2, 4)
        tracker.move(1L, 1, 5)
        assertEquals(listOf<Command>(Command.NoteOn(3, 4), Command.NoteOn(2, 4), Command.NoteOn(1, 5)), sent)
    }

    @Test
    fun `move inside the same cell does nothing`() {
        tracker.down(1L, 0, 3)
        repeat(10) { tracker.move(1L, 0, 3) }
        assertEquals(1, sent.size)
        assertEquals(1, sounded.size)
    }

    @Test
    fun `up sends nothing so the note decays naturally`() {
        tracker.down(1L, 0, 3)
        tracker.up(1L)
        assertEquals(listOf<Command>(Command.NoteOn(0, 3)), sent)
    }

    @Test
    fun `move after up or without down is ignored`() {
        tracker.move(9L, 1, 1)
        tracker.down(1L, 0, 3)
        tracker.up(1L)
        tracker.move(1L, 0, 9)
        assertEquals(listOf<Command>(Command.NoteOn(0, 3)), sent)
    }

    @Test
    fun `pointers are independent`() {
        tracker.down(1L, 0, 3)
        tracker.down(2L, 3, 7)
        tracker.move(1L, 0, 4)
        tracker.move(2L, 3, 9)
        tracker.up(1L)
        tracker.move(2L, 3, 10)
        assertEquals(
            listOf(
                Command.NoteOn(0, 3), Command.NoteOn(3, 7),
                Command.Slide(0, 4), Command.Slide(3, 9), Command.Slide(3, 10),
            ),
            sent,
        )
    }

    @Test
    fun `two pointers on one string both send and the last event wins in the engine`() {
        tracker.down(1L, 1, 3)
        tracker.down(2L, 1, 8)
        tracker.move(1L, 1, 4)
        assertEquals(listOf(Command.NoteOn(1, 3), Command.NoteOn(1, 8), Command.Slide(1, 4)), sent)
    }

    @Test
    fun `the same pointer id can be reused after up`() {
        tracker.down(1L, 0, 1)
        tracker.up(1L)
        tracker.down(1L, 2, 2)
        tracker.move(1L, 2, 3)
        assertEquals(listOf(Command.NoteOn(0, 1), Command.NoteOn(2, 2), Command.Slide(2, 3)), sent)
    }

    @Test
    fun `cancelAll forgets every pointer`() {
        tracker.down(1L, 0, 1)
        tracker.down(2L, 1, 1)
        tracker.cancelAll()
        tracker.move(1L, 0, 5)
        tracker.move(2L, 1, 5)
        assertEquals(2, sent.size)
    }
}
