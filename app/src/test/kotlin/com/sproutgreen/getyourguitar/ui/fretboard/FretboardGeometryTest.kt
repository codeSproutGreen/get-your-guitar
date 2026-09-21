package com.sproutgreen.getyourguitar.ui.fretboard

import com.sproutgreen.getyourguitar.data.FretLayoutKind
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FretboardGeometryTest {
    // 연주 영역 1200 x 880: 위 띠 80 + 지판 660 + 뮤트 바 140, 셀 폭 100, 줄 밴드 165
    private val geo = FretboardGeometry(width = 1200f, height = 880f)

    @Test
    fun `equal layout maps u to x and back`() {
        val layout = EqualFretLayout(cellWidth = 100f, scroll = -1f)
        assertEquals(0f, layout.xOf(-1f))
        assertEquals(100f, layout.xOf(0f)) // 너트
        assertEquals(1200f, layout.xOf(11f))
        for (u in listOf(-1f, -0.25f, 0f, 3.5f, 11f, 24f)) {
            assertEquals(u, layout.uAt(layout.xOf(u)), 1e-4f)
        }
    }

    @Test
    fun `scroll shifts the view by whole cells`() {
        val layout = EqualFretLayout(cellWidth = 100f, scroll = 12f)
        assertEquals(0f, layout.xOf(12f))
        assertEquals(1200f, layout.xOf(24f))
        assertEquals(12.5f, layout.uAt(50f), 1e-4f)
    }

    @Test
    fun `areas split into scroll strip, board, mute bar`() {
        assertEquals(80f, geo.boardTop, 1e-3f)
        assertEquals(740f, geo.boardBottom, 1e-3f)
        assertEquals(140f, geo.muteBarHeight, 1e-3f)
        assertEquals(100f, geo.cellWidth, 1e-3f)
        assertEquals(165f, geo.bandHeight, 1e-3f)
        assertFalse(geo.isOnBoard(79f))
        assertTrue(geo.isOnBoard(80f))
        assertTrue(geo.isOnBoard(739f))
        assertFalse(geo.isOnBoard(740f))
    }

    @Test
    fun `the mute bar is everything below the board and the scroll strip is everything above`() {
        assertTrue(geo.isOnMuteBar(740f))
        assertTrue(geo.isOnMuteBar(879f))
        assertFalse(geo.isOnMuteBar(739f))
        assertFalse(geo.isOnMuteBar(10f))
        assertTrue(geo.isOnScrollStrip(10f))
        assertFalse(geo.isOnScrollStrip(80f))
        assertFalse(geo.isOnScrollStrip(800f))
    }

    @Test
    fun `strings run G at the top to E at the bottom`() {
        assertEquals(3, geo.stringAt(80f))
        assertEquals(3, geo.stringAt(244f))
        assertEquals(2, geo.stringAt(245f))
        assertEquals(1, geo.stringAt(410f))
        assertEquals(0, geo.stringAt(575f))
        assertEquals(0, geo.stringAt(739f))
    }

    @Test
    fun `y outside the board clamps to the edge strings`() {
        assertEquals(3, geo.stringAt(-50f))
        assertEquals(3, geo.stringAt(10f))
        assertEquals(0, geo.stringAt(850f))
        assertEquals(0, geo.stringAt(5000f))
    }

    @Test
    fun `string centre lines sit in the middle of each band`() {
        assertEquals(162.5f, geo.stringCenterY(3), 1e-3f)
        assertEquals(657.5f, geo.stringCenterY(0), 1e-3f)
    }

    @Test
    fun `default view shows the open cell then frets 1 to 11`() {
        val layout = geo.layout(scroll = -1f)
        assertEquals(0, geo.fretAt(0f, layout))
        assertEquals(0, geo.fretAt(99f, layout))
        assertEquals(1, geo.fretAt(100f, layout))
        assertEquals(1, geo.fretAt(199f, layout))
        assertEquals(2, geo.fretAt(200f, layout))
        assertEquals(11, geo.fretAt(1199f, layout))
    }

    @Test
    fun `fully scrolled view shows frets 13 to 24`() {
        val layout = geo.layout(scroll = 12f)
        assertEquals(13, geo.fretAt(0f, layout))
        assertEquals(24, geo.fretAt(1199f, layout))
    }

    @Test
    fun `frets clamp at both ends`() {
        assertEquals(0, geo.fretAt(-500f, geo.layout(-1f)))
        assertEquals(24, geo.fretAt(5000f, geo.layout(12f)))
    }

    @Test
    fun `half scrolled view hit-tests partial cells`() {
        val layout = geo.layout(scroll = 2.5f) // 왼쪽 끝이 3프렛 셀의 한가운데
        assertEquals(3, geo.fretAt(0f, layout))
        assertEquals(3, geo.fretAt(49f, layout))
        assertEquals(4, geo.fretAt(50f, layout))
    }

    @Test
    fun `cell centre is where highlights and note names go`() {
        val layout = geo.layout(scroll = -1f)
        assertEquals(50f, geo.cellCenterX(0, layout), 1e-3f)
        assertEquals(150f, geo.cellCenterX(1, layout), 1e-3f)
        assertEquals(1250f, geo.cellCenterX(12, layout), 1e-3f)
    }

    @Test
    fun `scroll limits are minus one to twelve`() {
        assertEquals(-1f, FretboardGeometry.MIN_SCROLL)
        assertEquals(12f, geo.maxScroll)
        assertEquals(-1f, geo.clampScroll(-7f))
        assertEquals(12f, geo.clampScroll(40f))
        assertEquals(3.3f, geo.clampScroll(3.3f))
    }

    @Test
    fun `band coordinate measures vertical position in string bands`() {
        assertEquals(0f, geo.bandCoordinate(80f), 1e-4f)      // 지판 위쪽 끝
        assertEquals(0.5f, geo.bandCoordinate(162.5f), 1e-4f) // G줄 중심
        assertEquals(3.5f, geo.bandCoordinate(657.5f), 1e-4f) // E줄 중심
        assertEquals(-0.5f, geo.bandCoordinate(-2.5f), 1e-4f) // 지판 밖은 클램프하지 않는다(벤딩량 계산용)
        // 줄 s의 중심은 항상 (stringCount − 1 − s) + 0.5
        for (s in 0..3) assertEquals((3 - s) + 0.5f, geo.bandCoordinate(geo.stringCenterY(s)), 1e-4f)
    }

    // ---- 실제 프렛 간격 (설정 토글, 요청 2026-09-21) ----

    private val real = FretboardGeometry(width = 1200f, height = 880f, layoutKind = FretLayoutKind.REAL)

    @Test
    fun `equal spacing is still the default`() {
        assertEquals(FretLayoutKind.EQUAL, geo.layoutKind)
        assertEquals(12f, geo.maxScroll)
    }

    @Test
    fun `real spacing - the default view still shows the open cell through fret 11 edge to edge`() {
        val layout = real.layout(scroll = -1f)
        assertEquals(0f, layout.xOf(-1f), 1e-2f)
        assertEquals(1200f, layout.xOf(11f), 1e-2f)
    }

    @Test
    fun `real spacing - each cell is narrower than the one before by the twelfth root of two`() {
        val layout = real.layout(-1f)
        val ratio = 2.0.pow(-1.0 / 12.0).toFloat()
        for (fret in 2..24) {
            val previous = layout.xOf(fret - 1f) - layout.xOf(fret - 2f)
            val current = layout.xOf(fret.toFloat()) - layout.xOf(fret - 1f)
            assertEquals(ratio, current / previous, 1e-3f, "fret $fret")
        }
    }

    @Test
    fun `real spacing - the twelfth fret sits at half the scale length`() {
        val layout = real.layout(-1f)
        val nut = layout.xOf(0f)
        // 너트→12프렛 = L/2, 너트→24프렛 = 3L/4
        assertEquals(2f / 3f, (layout.xOf(12f) - nut) / (layout.xOf(24f) - nut), 1e-3f)
    }

    @Test
    fun `real spacing - the open cell is as wide as the first fret cell`() {
        val layout = real.layout(-1f)
        assertEquals(layout.xOf(1f) - layout.xOf(0f), layout.xOf(0f) - layout.xOf(-1f), 1e-2f)
    }

    @Test
    fun `real spacing - uAt inverts xOf at any scroll`() {
        for (scroll in listOf(-1f, 0f, 2.5f, 5f)) {
            val layout = real.layout(scroll)
            for (u in listOf(-1f, -0.4f, 0f, 0.5f, 7f, 12.25f, 24f)) {
                assertEquals(u, layout.uAt(layout.xOf(u)), 2e-3f, "scroll $scroll u $u")
            }
        }
    }

    @Test
    fun `real spacing - hit testing follows the wires`() {
        val layout = real.layout(-1f)
        for (fret in 1..11) {
            val wire = layout.xOf(fret.toFloat())
            assertEquals(fret, real.fretAt(wire - 1f, layout), "just left of wire $fret")
            assertEquals(fret + 1, real.fretAt(wire + 1f, layout), "just right of wire $fret")
        }
        assertEquals(0, real.fretAt(1f, layout))
    }

    @Test
    fun `cell centre is the midpoint between its two wires in both layouts`() {
        for (g in listOf(geo, real)) {
            val layout = g.layout(-1f)
            for (fret in 0..24) {
                val expected = (layout.xOf(fret - 1f) + layout.xOf(fret.toFloat())) / 2f
                assertEquals(expected, g.cellCenterX(fret, layout), 1e-2f)
                assertEquals(layout.xOf(fret.toFloat()) - layout.xOf(fret - 1f), g.cellWidthAt(fret, layout), 1e-2f)
            }
        }
    }

    @Test
    fun `real spacing - scrolling stops once the last fret is on screen`() {
        // 높은 프렛일수록 칸이 좁아 한 화면에 더 많이 들어온다 → 등간격(12)보다 훨씬 덜 스크롤해도 끝이 보인다.
        assertTrue(real.maxScroll < geo.maxScroll)
        assertEquals(real.maxScroll, real.maxScroll.toInt().toFloat(), "snaps to whole frets")
        assertTrue(real.layout(real.maxScroll).xOf(24f) <= 1200f + 0.5f, "fret 24 must be visible at max scroll")
        assertTrue(real.layout(real.maxScroll - 1f).xOf(24f) > 1200f, "and not before")
        assertEquals(real.maxScroll, real.clampScroll(99f))
        assertEquals(-1f, real.clampScroll(-9f))
    }

    @Test
    fun `dragging by the distance between two wires scrolls from one to the other`() {
        for (g in listOf(geo, real)) {
            val layout = g.layout(0f)
            val distance = layout.xOf(3f) - layout.xOf(0f)
            assertEquals(3f, g.scrollAfterDrag(scroll = 0f, dxPixels = -distance), 2e-3f) // 왼쪽으로 끌면 높은 프렛 쪽으로
            assertEquals(0f, g.scrollAfterDrag(scroll = 3f, dxPixels = distance), 2e-3f)
        }
        assertEquals(-1f, real.scrollAfterDrag(-1f, dxPixels = 5000f), "clamped")
    }
}
