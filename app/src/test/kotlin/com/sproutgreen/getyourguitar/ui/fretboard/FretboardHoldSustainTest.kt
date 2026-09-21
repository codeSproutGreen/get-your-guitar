package com.sproutgreen.getyourguitar.ui.fretboard

import com.sproutgreen.getyourguitar.core.engine.Command
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * "누르고 있는 동안만 소리" (요청 2026-09-21). 줄마다 누르고 있는 손가락을 쌓아 두고,
 * 맨 위(가장 나중에 누른) 손가락이 그 줄의 음을 소유한다.
 */
class FretboardHoldSustainTest {
    private val sent = mutableListOf<Command>()
    private val sounded = mutableListOf<Pair<Int, Int>>()
    private val released = mutableListOf<Int>()
    private val bendVisuals = mutableListOf<Pair<Int, Float>>()
    private var nowMs = 0L
    private val tracker = FretboardTouchTracker(
        send = { sent += it },
        onSounded = { string, fret -> sounded += string to fret },
        onBend = { string, displacement -> bendVisuals += string to displacement },
        onReleased = { released += it },
        clockMs = { nowMs += 100; nowMs },
    )

    private fun y(string: Int, offset: Float = 0f): Float = (3 - string) + 0.5f + offset

    @Test
    fun `hold to sustain is the default`() {
        assertTrue(tracker.holdToSustain)
    }

    @Test
    fun `lifting the finger stops the note`() {
        tracker.down(1L, 1, 5, y(1))
        tracker.up(1L)
        assertEquals(listOf(Command.NoteOn(1, 5), Command.NoteOff(1)), sent)
        assertEquals(listOf(1), released)
    }

    @Test
    fun `a slid note stops once when the finger lifts`() {
        tracker.down(1L, 1, 5, y(1))
        tracker.move(1L, 1, 7, y(1))
        tracker.up(1L)
        assertEquals(listOf(Command.NoteOn(1, 5), Command.Slide(1, 7), Command.NoteOff(1)), sent)
    }

    @Test
    fun `fingers on different strings stop independently`() {
        tracker.down(1L, 0, 3, y(0))
        tracker.down(2L, 2, 5, y(2))
        tracker.up(1L)
        assertEquals(Command.NoteOff(0), sent.last())
        tracker.up(2L)
        assertEquals(Command.NoteOff(2), sent.last())
        assertEquals(2, sent.count { it is Command.NoteOff })
    }

    // ---- 레이크: 훑은 줄은 그 손가락이 화면에 있는 동안 모두 울린다 ----

    @Test
    fun `raked strings keep ringing until the raking finger lifts, then all stop`() {
        nowMs = 0
        val fast = FretboardTouchTracker(send = { sent += it }, onReleased = { released += it }, clockMs = { nowMs += 10; nowMs })
        fast.down(1L, 3, 5, y(3))
        fast.move(1L, 2, 5, y(2))
        fast.move(1L, 1, 5, y(1))
        fast.move(1L, 0, 5, y(0))
        assertTrue(sent.none { it is Command.NoteOff }, "nothing may stop while the finger is down: $sent")
        fast.up(1L)
        assertEquals(setOf(0, 1, 2, 3), sent.filterIsInstance<Command.NoteOff>().map { it.string }.toSet())
        assertEquals(4, sent.count { it is Command.NoteOff })
    }

    @Test
    fun `raking back over a string already held stops it only once`() {
        nowMs = 0
        val fast = FretboardTouchTracker(send = { sent += it }, clockMs = { nowMs += 10; nowMs })
        fast.down(1L, 3, 5, y(3))
        fast.move(1L, 2, 5, y(2))
        fast.move(1L, 3, 5, y(3)) // 되돌아옴: 다시 튕기지만 소유 목록에는 한 번만
        fast.up(1L)
        assertEquals(3, sent.count { it is Command.NoteOn })
        assertEquals(listOf(2, 3), sent.filterIsInstance<Command.NoteOff>().map { it.string }.sorted())
    }

    // ---- 같은 줄에 두 손가락: 해머온·풀오프 ----

    @Test
    fun `hammer-on then pull-off returns to the finger still held`() {
        tracker.down(1L, 1, 5, y(1))
        tracker.down(2L, 1, 7, y(1)) // 해머온
        tracker.up(2L)               // 풀오프
        assertEquals(listOf(Command.NoteOn(1, 5), Command.NoteOn(1, 7), Command.Slide(1, 5)), sent)
        assertEquals(1 to 5, sounded.last())
        assertTrue(released.isEmpty(), "string is still held")
        tracker.up(1L)
        assertEquals(Command.NoteOff(1), sent.last())
    }

    @Test
    fun `lifting the finger underneath does not stop the note on top`() {
        tracker.down(1L, 1, 5, y(1))
        tracker.down(2L, 1, 7, y(1))
        tracker.up(1L)
        assertEquals(listOf<Command>(Command.NoteOn(1, 5), Command.NoteOn(1, 7)), sent)
        tracker.up(2L)
        assertEquals(Command.NoteOff(1), sent.last())
    }

    @Test
    fun `a finger underneath moves silently and the pull-off lands where it is now`() {
        tracker.down(1L, 1, 5, y(1))
        tracker.down(2L, 1, 9, y(1))
        tracker.move(1L, 1, 4, y(1))
        assertEquals(2, sent.size, "the covered finger must not slide the sounding note: $sent")
        tracker.up(2L)
        assertEquals(Command.Slide(1, 4), sent.last())
    }

    @Test
    fun `a finger underneath cannot bend the note on top`() {
        tracker.down(1L, 1, 5, y(1))
        tracker.down(2L, 1, 9, y(1))
        tracker.move(1L, 1, 5, y(1, 0.4f))
        assertTrue(sent.none { it is Command.Bend }, "$sent")
    }

    @Test
    fun `three fingers unwind in the order they were pressed`() {
        tracker.down(1L, 2, 3, y(2))
        tracker.down(2L, 2, 5, y(2))
        tracker.down(3L, 2, 7, y(2))
        tracker.up(2L) // 가운데를 먼저 떼도 소리는 그대로
        assertEquals(3, sent.size)
        tracker.up(3L)
        assertEquals(Command.Slide(2, 3), sent.last())
    }

    // ---- 벤딩과 함께 ----

    @Test
    fun `lifting a bent finger stops the note without first dropping the pitch`() {
        tracker.down(1L, 1, 5, y(1))
        tracker.move(1L, 1, 5, y(1, 0.4f))
        sent.clear()
        tracker.up(1L)
        assertEquals(listOf<Command>(Command.NoteOff(1)), sent) // Bend 0을 먼저 보내면 꺼지는 동안 음이 툭 떨어진다
        assertEquals(1 to 0f, bendVisuals.last())
    }

    @Test
    fun `pulling off from a bent note unbends before returning to the held fret`() {
        tracker.down(1L, 1, 5, y(1))
        tracker.down(2L, 1, 7, y(1))
        tracker.move(2L, 1, 7, y(1, 0.4f))
        sent.clear()
        tracker.up(2L)
        assertEquals(listOf(Command.Bend(1, 0f), Command.Slide(1, 5)), sent)
    }

    // ---- 취소·모드 전환 ----

    @Test
    fun `cancelAll stops everything that was held`() {
        tracker.down(1L, 0, 1, y(0))
        tracker.down(2L, 3, 1, y(3))
        tracker.cancelAll()
        assertEquals(setOf(0, 3), sent.filterIsInstance<Command.NoteOff>().map { it.string }.toSet())
        tracker.move(1L, 0, 5, y(0))
        assertEquals(4, sent.size)
    }

    @Test
    fun `ring mode leaves notes ringing and only releases bends`() {
        tracker.holdToSustain = false
        tracker.down(1L, 1, 5, y(1))
        tracker.move(1L, 1, 5, y(1, 0.4f))
        tracker.up(1L)
        assertEquals(Command.Bend(1, 0f), sent.last())
        assertTrue(sent.none { it is Command.NoteOff })
        assertTrue(released.isEmpty())

        sent.clear()
        tracker.down(2L, 1, 5, y(1))
        tracker.down(3L, 1, 7, y(1))
        tracker.up(3L)
        assertEquals(listOf<Command>(Command.NoteOn(1, 5), Command.NoteOn(1, 7)), sent) // 풀오프도 없음: v1.0.0 그대로
    }

    @Test
    fun `bend range can be changed at runtime`() {
        tracker.maxBendCents = 400f
        tracker.down(1L, 0, 3, y(0))
        tracker.move(1L, 0, 3, y(0, 0.5f))
        assertEquals(Command.Bend(0, 400f), sent.last())
    }
}
