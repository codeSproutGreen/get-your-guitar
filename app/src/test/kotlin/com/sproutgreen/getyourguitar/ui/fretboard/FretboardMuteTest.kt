package com.sproutgreen.getyourguitar.ui.fretboard

import com.sproutgreen.getyourguitar.core.engine.Command
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 뮤트 바 (요청 2026-09-21): 지판 아래의 긴 버튼.
 * - 누르고 있는 동안: 음은 손가락으로 누르고 있을 때만 나고, 떼면 멈춘다.
 * - 안 누르고 있으면: 떼도 자연 감쇠.
 * - 울리는 중에 누르면: 손가락이 떠난 줄은 바로 끊긴다. 아직 누르고 있는 음은 그대로.
 */
class FretboardMuteTest {
    private val sent = mutableListOf<Command>()
    private val released = mutableListOf<Int>()
    private var nowMs = 0L
    private var stepMs = 300L // 짚고 나서 움직이는 손(판정 시간 250 ms보다 길다)
    private val tracker = FretboardTouchTracker(
        send = { sent += it },
        onReleased = { released += it },
        clockMs = { nowMs += stepMs; nowMs },
    )

    private fun y(string: Int, offset: Float = 0f): Float = (3 - string) + 0.5f + offset
    private fun noteOffs(): List<Int> = sent.filterIsInstance<Command.NoteOff>().map { it.string }

    @Test
    fun `mute is off until the bar is pressed, so lifting a finger lets the note ring`() {
        assertFalse(tracker.muting)
        tracker.down(1L, 1, 5, y(1))
        tracker.up(1L)
        assertEquals(listOf<Command>(Command.NoteOn(1, 5)), sent)
    }

    @Test
    fun `while the bar is held, lifting a finger stops its note`() {
        tracker.setMute(true)
        tracker.down(1L, 1, 5, y(1))
        tracker.up(1L)
        assertEquals(listOf(Command.NoteOn(1, 5), Command.NoteOff(1)), sent)
        assertEquals(listOf(1), released)
    }

    @Test
    fun `pressing the bar cuts strings that were left ringing`() {
        tracker.down(1L, 1, 5, y(1))
        tracker.up(1L)
        tracker.down(2L, 3, 7, y(3))
        tracker.up(2L)
        sent.clear()
        tracker.setMute(true)
        assertEquals(listOf(1, 3), noteOffs().sorted())
        assertEquals(listOf(1, 3), released.sorted())
    }

    @Test
    fun `pressing the bar does not cut a note that is still being held`() {
        tracker.down(1L, 0, 3, y(0)) // 계속 누르고 있음
        tracker.down(2L, 2, 5, y(2))
        tracker.up(2L)               // 이 줄은 떠나서 울리는 중
        sent.clear()
        tracker.setMute(true)
        assertEquals(listOf(2), noteOffs())
        tracker.up(1L)               // 뮤트를 누른 채 떼면 그제야 멈춘다
        assertEquals(listOf(2, 0), noteOffs())
    }

    @Test
    fun `strings that were never played or already stopped are not sent again`() {
        tracker.setMute(true)
        assertTrue(sent.isEmpty())
        tracker.down(1L, 1, 5, y(1))
        tracker.up(1L)               // NoteOff(1)
        tracker.setMute(false)
        tracker.setMute(true)
        assertEquals(listOf(1), noteOffs())
    }

    @Test
    fun `pressing the bar twice in a row changes nothing the second time`() {
        tracker.down(1L, 1, 5, y(1))
        tracker.up(1L)
        tracker.setMute(true)
        val after = sent.size
        tracker.setMute(true)
        assertEquals(after, sent.size)
    }

    @Test
    fun `a note held through a mute press and release rings on after the finger lifts`() {
        tracker.down(1L, 1, 5, y(1))
        tracker.setMute(true)
        tracker.setMute(false)
        tracker.up(1L)
        assertEquals(listOf<Command>(Command.NoteOn(1, 5)), sent)
    }

    @Test
    fun `a rake left ringing is cut as a whole`() {
        stepMs = 10
        tracker.down(1L, 3, 5, y(3))
        tracker.move(1L, 2, 5, y(2))
        tracker.move(1L, 1, 5, y(1))
        tracker.move(1L, 0, 5, y(0))
        tracker.up(1L)
        assertTrue(noteOffs().isEmpty())
        tracker.setMute(true)
        assertEquals(listOf(0, 1, 2, 3), noteOffs().sorted())
    }

    @Test
    fun `re-plucking a cut string makes it cuttable again`() {
        tracker.down(1L, 1, 5, y(1)); tracker.up(1L)
        tracker.setMute(true); tracker.setMute(false)
        tracker.down(2L, 1, 7, y(1)); tracker.up(2L)
        tracker.setMute(true)
        assertEquals(listOf(1, 1), noteOffs())
    }

    @Test
    fun `pull-off works with or without the mute bar`() {
        tracker.down(1L, 1, 5, y(1))
        tracker.down(2L, 1, 7, y(1))
        tracker.up(2L)
        assertEquals(Command.Slide(1, 5), sent.last())
        tracker.up(1L)
        assertTrue(noteOffs().isEmpty(), "not muting, so the string keeps ringing")
    }

    @Test
    fun `without mute a bent finger that lifts returns the pitch and lets the note ring`() {
        tracker.down(1L, 1, 5, y(1))
        tracker.move(1L, 1, 5, y(1, 0.4f))
        tracker.up(1L)
        assertEquals(Command.Bend(1, 0f), sent.last())
        assertTrue(noteOffs().isEmpty())
    }

    @Test
    fun `cancelAll without mute stops nothing, with mute stops what was held`() {
        tracker.down(1L, 0, 1, y(0))
        tracker.cancelAll()
        assertTrue(noteOffs().isEmpty())

        tracker.setMute(true)
        sent.clear()
        tracker.down(2L, 3, 1, y(3))
        tracker.cancelAll()
        assertEquals(listOf(3), noteOffs())
    }

    // ---- 사라진 손가락 (실기기에서 발견 2026-09-21) ----
    // 시스템이 제스처를 취소하면(전화·알림, 다른 입력 장치의 개입) up 없이 포인터가 사라진다. 추적기에 그 손가락이
    // 남아 있으면 다음 음을 뗄 때 가짜 풀오프가 나가고, 뮤트 중에는 음이 멈추지 않는다.

    @Test
    fun `a finger that vanished without an up is released by retainOnly`() {
        tracker.setMute(true)
        tracker.down(1L, 1, 9, y(1))
        tracker.retainOnly(emptySet())
        assertEquals(listOf(1), noteOffs())

        sent.clear()
        tracker.down(2L, 1, 5, y(1))
        tracker.up(2L)
        assertEquals(listOf(Command.NoteOn(1, 5), Command.NoteOff(1)), sent) // 9프렛으로의 가짜 풀오프가 없어야 한다
    }

    @Test
    fun `retainOnly keeps the fingers that are still down`() {
        tracker.setMute(true)
        tracker.down(1L, 0, 3, y(0))
        tracker.down(2L, 2, 5, y(2))
        tracker.retainOnly(setOf(2L))
        assertEquals(listOf(0), noteOffs())
        tracker.move(2L, 2, 7, y(2))
        assertEquals(Command.Slide(2, 7), sent.last())
    }

    @Test
    fun `retainOnly with everything still down changes nothing`() {
        tracker.down(1L, 0, 3, y(0))
        tracker.retainOnly(setOf(1L, 99L))
        assertEquals(1, sent.size)
    }
}
