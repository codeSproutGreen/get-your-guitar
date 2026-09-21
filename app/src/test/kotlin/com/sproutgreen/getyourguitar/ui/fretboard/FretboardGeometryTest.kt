package com.sproutgreen.getyourguitar.ui.fretboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FretboardGeometryTest {
    // 연주 영역 1200 x 880: 띠 80 + 지판 720 + 띠 80, 셀 폭 100, 줄 밴드 180
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
    fun `areas split into strip, board, strip`() {
        assertEquals(80f, geo.boardTop, 1e-3f)
        assertEquals(800f, geo.boardBottom, 1e-3f)
        assertEquals(100f, geo.cellWidth, 1e-3f)
        assertEquals(180f, geo.bandHeight, 1e-3f)
        assertFalse(geo.isOnBoard(79f))
        assertTrue(geo.isOnBoard(80f))
        assertTrue(geo.isOnBoard(799f))
        assertFalse(geo.isOnBoard(800f))
    }

    @Test
    fun `strings run G at the top to E at the bottom`() {
        assertEquals(3, geo.stringAt(80f))
        assertEquals(3, geo.stringAt(259f))
        assertEquals(2, geo.stringAt(260f))
        assertEquals(1, geo.stringAt(440f))
        assertEquals(0, geo.stringAt(620f))
        assertEquals(0, geo.stringAt(799f))
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
        assertEquals(170f, geo.stringCenterY(3), 1e-3f)
        assertEquals(710f, geo.stringCenterY(0), 1e-3f)
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
        assertEquals(0.5f, geo.bandCoordinate(170f), 1e-4f)   // G줄 중심
        assertEquals(3.5f, geo.bandCoordinate(710f), 1e-4f)   // E줄 중심
        assertEquals(-0.5f, geo.bandCoordinate(-10f), 1e-4f)  // 지판 밖은 클램프하지 않는다(벤딩량 계산용)
        // 줄 s의 중심은 항상 (stringCount − 1 − s) + 0.5
        for (s in 0..3) assertEquals((3 - s) + 0.5f, geo.bandCoordinate(geo.stringCenterY(s)), 1e-4f)
    }
}
