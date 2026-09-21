package com.sproutgreen.getyourguitar.ui.fretboard

import com.sproutgreen.getyourguitar.core.engine.Command
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 스펙 6.2 "제스처 → 커맨드" 표의 각 행 + 벤딩 규칙.
 * 여기서는 "떼도 울림" 모드(holdToSustain = false, v1.0.0 동작)로 본다. 누르는 동안만 소리는 [FretboardHoldSustainTest].
 */
class FretboardTouchTrackerTest {
    private val sent = mutableListOf<Command>()
    private val sounded = mutableListOf<Pair<Int, Int>>()
    private val bendVisuals = mutableListOf<Pair<Int, Float>>()
    /** 가짜 시계: 추적기가 시각을 물을 때마다 [stepMs]씩 흐른다. 기본 100 ms = 느긋한 손놀림. */
    private var nowMs = 0L
    private var stepMs = 100L
    private val tracker = FretboardTouchTracker(
        send = { sent += it },
        onSounded = { string, fret -> sounded += string to fret },
        onBend = { string, displacement -> bendVisuals += string to displacement },
        maxBendCents = 200f,
        clockMs = { nowMs += stepMs; nowMs },
    ).also { it.holdToSustain = false }

    /** 밴드 좌표: 0 = 지판 위쪽 끝, 줄 s의 중심 = (3 − s) + 0.5. [offset]은 밴드 높이 단위. */
    private fun y(string: Int, offset: Float = 0f): Float = (3 - string) + 0.5f + offset

    private fun bends(): List<Command.Bend> = sent.filterIsInstance<Command.Bend>()

    // ---- 기존 제스처 표 ----

    @Test
    fun `down on the board plucks`() {
        tracker.down(1L, string = 2, fret = 5, bandY = y(2))
        assertEquals(listOf<Command>(Command.NoteOn(2, 5)), sent)
        assertEquals(listOf(2 to 5), sounded)
    }

    @Test
    fun `move to another fret on the same string slides`() {
        tracker.down(1L, 2, 5, y(2))
        tracker.move(1L, 2, 6, y(2))
        tracker.move(1L, 2, 7, y(2))
        assertEquals(listOf(Command.NoteOn(2, 5), Command.Slide(2, 6), Command.Slide(2, 7)), sent)
        assertEquals(listOf(2 to 5, 2 to 6, 2 to 7), sounded)
    }

    @Test
    fun `move onto another string plucks it and leaves the old one ringing`() {
        tracker.down(1L, 3, 4, y(3))
        tracker.move(1L, 2, 4, y(2))
        tracker.move(1L, 1, 5, y(1))
        assertEquals(
            listOf<Command>(Command.NoteOn(3, 4), Command.NoteOn(2, 4), Command.NoteOn(1, 5)),
            sent.filter { it !is Command.Bend },
        )
    }

    @Test
    fun `move inside the same cell does nothing`() {
        tracker.down(1L, 0, 3, y(0))
        repeat(10) { tracker.move(1L, 0, 3, y(0)) }
        assertEquals(1, sent.size)
        assertEquals(1, sounded.size)
    }

    @Test
    fun `up sends nothing so the note decays naturally`() {
        tracker.down(1L, 0, 3, y(0))
        tracker.up(1L)
        assertEquals(listOf<Command>(Command.NoteOn(0, 3)), sent)
    }

    @Test
    fun `move after up or without down is ignored`() {
        tracker.move(9L, 1, 1, y(1))
        tracker.down(1L, 0, 3, y(0))
        tracker.up(1L)
        tracker.move(1L, 0, 9, y(0, 0.4f))
        assertEquals(listOf<Command>(Command.NoteOn(0, 3)), sent)
    }

    @Test
    fun `pointers are independent`() {
        tracker.down(1L, 0, 3, y(0))
        tracker.down(2L, 3, 7, y(3))
        tracker.move(1L, 0, 4, y(0))
        tracker.move(2L, 3, 9, y(3))
        tracker.up(1L)
        tracker.move(2L, 3, 10, y(3))
        assertEquals(
            listOf(
                Command.NoteOn(0, 3), Command.NoteOn(3, 7),
                Command.Slide(0, 4), Command.Slide(3, 9), Command.Slide(3, 10),
            ),
            sent,
        )
    }

    /** v1.0.0은 "마지막 이벤트가 이김"이라 아래 손가락이 움직여도 Slide가 나갔다. 소유권 규칙 이후로는 위 손가락만 음을 움직인다. */
    @Test
    fun `two pointers on one string - only the later finger moves the note`() {
        tracker.down(1L, 1, 3, y(1))
        tracker.down(2L, 1, 8, y(1))
        tracker.move(1L, 1, 4, y(1))
        tracker.move(2L, 1, 9, y(1))
        assertEquals(listOf(Command.NoteOn(1, 3), Command.NoteOn(1, 8), Command.Slide(1, 9)), sent)
    }

    @Test
    fun `the same pointer id can be reused after up`() {
        tracker.down(1L, 0, 1, y(0))
        tracker.up(1L)
        tracker.down(1L, 2, 2, y(2))
        tracker.move(1L, 2, 3, y(2))
        assertEquals(listOf(Command.NoteOn(0, 1), Command.NoteOn(2, 2), Command.Slide(2, 3)), sent)
    }

    @Test
    fun `cancelAll forgets every pointer`() {
        tracker.down(1L, 0, 1, y(0))
        tracker.down(2L, 1, 1, y(1))
        tracker.cancelAll()
        tracker.move(1L, 0, 5, y(0))
        tracker.move(2L, 1, 5, y(1))
        assertEquals(2, sent.size)
    }

    // ---- 벤딩: 밴드 안 세로 이동 ----

    @Test
    fun `small vertical wobble stays inside the dead zone`() {
        tracker.down(1L, 1, 5, y(1))
        for (wobble in listOf(0.02f, -0.05f, 0.09f, -0.09f, 0f)) tracker.move(1L, 1, 5, y(1, wobble))
        assertTrue(bends().isEmpty(), "unexpected ${bends()}")
    }

    @Test
    fun `vertical movement inside the band bends proportionally`() {
        tracker.down(1L, 1, 5, y(1))
        tracker.move(1L, 1, 5, y(1, 0.30f)) // 데드존 0.1, 최대 0.5 → (0.30 − 0.1) / 0.4 = 절반
        assertEquals(1, bends().size)
        assertEquals(1, bends()[0].string)
        assertEquals(100f, bends()[0].cents, 0.5f)
    }

    @Test
    fun `bend reaches the maximum at half a band and stays there`() {
        tracker.down(1L, 1, 5, y(1, -0.4f)) // 밴드 위쪽에서 시작해 아래로 민다
        tracker.move(1L, 1, 5, y(1, 0.1f))  // 이동 0.5
        tracker.move(1L, 1, 5, y(1, 0.45f)) // 이동 0.85, 아직 같은 밴드
        assertEquals(200f, bends().last().cents, 0.01f)
        assertEquals(1, bends().count { it.cents == 200f }, "max must not be re-sent")
    }

    @Test
    fun `pushing up bends the same as pulling down`() {
        tracker.down(1L, 2, 7, y(2))
        tracker.move(1L, 2, 7, y(2, -0.30f))
        val up = bends().last().cents
        tracker.move(1L, 2, 7, y(2, 0.30f))
        assertEquals(up, bends().last().cents, 0.01f)
        assertEquals(100f, up, 0.5f)
    }

    @Test
    fun `returning to the touch point releases the bend`() {
        tracker.down(1L, 1, 5, y(1))
        tracker.move(1L, 1, 5, y(1, 0.4f))
        tracker.move(1L, 1, 5, y(1, 0.0f))
        assertEquals(0f, bends().last().cents)
    }

    @Test
    fun `tiny bend changes are not re-sent`() {
        tracker.down(1L, 1, 5, y(1))
        tracker.move(1L, 1, 5, y(1, 0.3000f))
        tracker.move(1L, 1, 5, y(1, 0.3005f)) // 0.25센트 차이
        tracker.move(1L, 1, 5, y(1, 0.3010f))
        assertEquals(1, bends().size)
    }

    @Test
    fun `lifting a bent finger releases the bend, an unbent one sends nothing`() {
        tracker.down(1L, 1, 5, y(1))
        tracker.move(1L, 1, 5, y(1, 0.4f))
        tracker.up(1L)
        assertEquals(Command.Bend(1, 0f), sent.last())

        sent.clear()
        tracker.down(2L, 2, 3, y(2))
        tracker.move(2L, 2, 3, y(2, 0.05f))
        tracker.up(2L)
        assertEquals(listOf<Command>(Command.NoteOn(2, 3)), sent)
    }

    @Test
    fun `diagonal movement slides and bends together`() {
        tracker.down(1L, 1, 5, y(1))
        tracker.move(1L, 1, 6, y(1, 0.3f))
        assertEquals(listOf(Command.NoteOn(1, 5), Command.Slide(1, 6), Command.Bend(1, 100f)), sent.map {
            if (it is Command.Bend) Command.Bend(it.string, Math.round(it.cents).toFloat()) else it
        })
    }

    // ---- 벤딩과 레이크의 경계 ----

    /** 실기기 피드백(2026-09-21): 벤딩한 줄을 더 밀면 옆 줄 영역에 들어서며 벤딩이 풀렸다. */
    @Test
    fun `once a bend has started the finger stays on that string even over other bands`() {
        tracker.down(1L, 2, 5, y(2))
        tracker.move(1L, 2, 5, y(2, 0.45f))  // D줄을 아래로 거의 끝까지
        tracker.move(1L, 1, 5, y(1, -0.45f)) // 경계를 넘어 A줄 영역
        tracker.move(1L, 0, 5, y(0))         // E줄 영역까지
        assertEquals(listOf<Command>(Command.NoteOn(2, 5)), sent.filter { it !is Command.Bend })
        assertTrue(bends().all { it.string == 2 })
        assertEquals(200f, bends().last().cents, 0.01f)
        assertEquals(listOf(2 to 5), sounded)

        tracker.up(1L)
        assertEquals(Command.Bend(2, 0f), sent.last())
    }

    @Test
    fun `a locked bend still slides along its own string`() {
        tracker.down(1L, 2, 5, y(2))
        tracker.move(1L, 2, 5, y(2, 0.45f))
        tracker.move(1L, 1, 7, y(1, -0.3f)) // 옆 줄 영역에서 가로로도 이동
        assertEquals(Command.Slide(2, 7), sent.last { it !is Command.Bend })
    }

    @Test
    fun `a locked bend stays locked after easing back to zero`() {
        tracker.down(1L, 2, 5, y(2))
        tracker.move(1L, 2, 5, y(2, 0.4f))
        tracker.move(1L, 2, 5, y(2, 0.0f))   // 벤딩을 풀었다가
        tracker.move(1L, 3, 5, y(3, 0.3f))   // 반대쪽으로 크게 민다: 여전히 D줄 벤딩
        assertEquals(listOf<Command>(Command.NoteOn(2, 5)), sent.filter { it !is Command.Bend })
        assertEquals(200f, bends().last().cents, 0.01f)
    }

    @Test
    fun `crossing before any bend has started is still a rake`() {
        tracker.down(1L, 2, 5, y(2, 0.45f))   // 밴드 아래쪽 끝을 짚고
        tracker.move(1L, 1, 5, y(1, -0.47f))  // 0.08밴드만 움직여 A줄 영역으로: 데드존 안이라 벤딩 시작 전
        assertEquals(listOf<Command>(Command.NoteOn(2, 5), Command.NoteOn(1, 5)), sent)
    }

    @Test
    fun `a string entered by rake is never bent`() {
        tracker.down(1L, 3, 5, y(3))
        tracker.move(1L, 2, 5, y(2, -0.45f))
        sent.clear()
        tracker.move(1L, 2, 5, y(2, 0.0f))
        tracker.move(1L, 2, 5, y(2, 0.45f))
        assertTrue(sent.isEmpty(), "rake pass must not bend: $sent")
        tracker.up(1L)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a fast rake sends only plucks`() {
        tracker.down(1L, 3, 5, y(3))
        tracker.move(1L, 2, 5, y(2))
        tracker.move(1L, 1, 5, y(1))
        tracker.move(1L, 0, 5, y(0))
        assertEquals(
            listOf<Command>(Command.NoteOn(3, 5), Command.NoteOn(2, 5), Command.NoteOn(1, 5), Command.NoteOn(0, 5)),
            sent,
        )
    }

    @Test
    fun `edge strings bend fully when pushed off the board`() {
        tracker.down(1L, 3, 5, y(3))
        tracker.move(1L, 3, 5, y(3, -2.0f)) // 지판 위로 벗어남: 줄은 G로 클램프된 채
        assertEquals(200f, bends().last().cents, 0.01f)
    }

    // ---- 시각 피드백 ----

    @Test
    fun `bend visual follows the finger past the band and clears on release`() {
        tracker.down(1L, 1, 5, y(1))
        tracker.move(1L, 1, 5, y(1, 0.05f)) // 데드존 안이어도 줄은 손가락을 따라간다
        tracker.move(1L, 1, 5, y(1, -0.8f)) // 음은 0.5에서 최대지만 줄은 손가락을 끝까지 따라간다
        tracker.move(1L, 1, 5, y(1, -9f))   // 터무니없는 값만 제한
        tracker.up(1L)
        assertEquals(
            listOf(1 to 0.05f, 1 to -0.8f, 1 to -FretboardTouchTracker.MAX_VISUAL_BANDS, 1 to 0f),
            bendVisuals.map { it.first to Math.round(it.second * 100) / 100f },
        )
    }

    @Test
    fun `bend visual clears when the finger rakes away before bending`() {
        tracker.down(1L, 2, 5, y(2, 0.45f))
        tracker.move(1L, 2, 5, y(2, 0.48f))   // 걸렸지만 데드존 안: 줄이 살짝 따라온다
        tracker.move(1L, 1, 5, y(1, -0.47f))  // 레이크
        assertEquals(2 to 0f, bendVisuals.last())
    }

    // ---- 벤딩은 터치 후 잠깐 지나야 걸린다 (레이크의 첫 줄이 휘지 않게) ----

    @Test
    fun `a fast vertical pass through the first band does not bend it`() {
        stepMs = 10
        tracker.down(1L, 3, 5, y(3, -0.4f))
        tracker.move(1L, 3, 5, y(3, -0.1f))
        tracker.move(1L, 3, 5, y(3, 0.2f))
        tracker.move(1L, 3, 5, y(3, 0.45f)) // 30 ms 만에 밴드를 거의 다 지나감
        tracker.move(1L, 2, 5, y(2, -0.4f)) // 40 ms: 다음 줄
        assertEquals(listOf<Command>(Command.NoteOn(3, 5), Command.NoteOn(2, 5)), sent)
        assertTrue(bendVisuals.isEmpty(), "string must not visibly bend during a rake: $bendVisuals")
    }

    @Test
    fun `bend arms 80 ms after the touch and measures from where the finger was then`() {
        stepMs = 30
        tracker.down(1L, 1, 5, y(1))          // t = 30
        tracker.move(1L, 1, 5, y(1, 0.10f))   // t = 60: 아직 안 걸림
        tracker.move(1L, 1, 5, y(1, 0.20f))   // t = 90: 아직 안 걸림 (60 ms 경과)
        tracker.move(1L, 1, 5, y(1, 0.30f))   // t = 120: 걸림. 기준점 = 직전 위치 0.20 → 이동 0.10 = 데드존
        assertTrue(bends().isEmpty(), "arming must not jump the pitch: ${bends()}")
        tracker.move(1L, 1, 5, y(1, 0.45f))   // 기준점에서 0.25 → (0.25 − 0.1) / 0.4 × 200 = 75
        assertEquals(75f, bends().last().cents, 0.5f)
    }

    @Test
    fun `a finger that rests and then pushes bends from the touch point`() {
        stepMs = 500
        tracker.down(1L, 1, 5, y(1))
        tracker.move(1L, 1, 5, y(1, 0.5f))
        assertEquals(200f, bends().last().cents, 0.01f)
    }

    @Test
    fun `custom bend range scales the cents`() {
        val wide = mutableListOf<Command>()
        var t0 = 0L
        val t = FretboardTouchTracker(send = { wide += it }, maxBendCents = 400f, clockMs = { t0 += 100; t0 })
        t.holdToSustain = false
        t.down(1L, 0, 3, y(0))
        t.move(1L, 0, 3, y(0, 0.5f))
        assertEquals(Command.Bend(0, 400f), wide.last())
    }
}
