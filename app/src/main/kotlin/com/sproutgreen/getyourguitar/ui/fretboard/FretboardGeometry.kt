package com.sproutgreen.getyourguitar.ui.fretboard

import kotlin.math.floor

/**
 * 연주 영역(위 띠 + 지판 + 아래 띠)의 치수와 터치 판정. Android 의존 없음.
 *
 * 화면 전체 높이 기준으로 상단 바 12% · 띠 8% · 지판 72% · 띠 8% 이므로,
 * 상단 바를 뺀 이 영역 안에서 띠 하나는 8/88이다.
 * 판정 범위는 넉넉하다: 줄은 밴드 전체 높이, 프렛은 셀 전체 폭.
 */
class FretboardGeometry(
    val width: Float,
    val height: Float,
    val stringCount: Int = 4,
    val fretCount: Int = 24,
    val visibleCells: Int = 12,
) {
    val stripHeight: Float = height * STRIP_FRACTION
    val boardTop: Float = stripHeight
    val boardBottom: Float = height - stripHeight
    val boardHeight: Float = boardBottom - boardTop
    val bandHeight: Float = boardHeight / stringCount
    val cellWidth: Float = width / visibleCells
    val maxScroll: Float = (fretCount - visibleCells).toFloat()

    fun layout(scroll: Float): FretLayout = EqualFretLayout(cellWidth, scroll)

    fun clampScroll(scroll: Float): Float = scroll.coerceIn(MIN_SCROLL, maxScroll)

    fun isOnBoard(y: Float): Boolean = y >= boardTop && y < boardBottom

    /** 위에서부터 G(3)·D(2)·A(1)·E(0). 지판 밖의 y는 가장자리 줄로 클램프한다. */
    fun stringAt(y: Float): Int {
        val band = floor((y - boardTop) / bandHeight).toInt().coerceIn(0, stringCount - 1)
        return stringCount - 1 - band
    }

    /**
     * 세로 위치를 줄 밴드 단위로. 0 = 지판 위쪽 끝, 줄 s의 중심 = (stringCount − 1 − s) + 0.5.
     * 벤딩량(세로 이동 거리)을 화면 크기와 무관하게 재기 위한 좌표라서 지판 밖에서도 클램프하지 않는다.
     */
    fun bandCoordinate(y: Float): Float = (y - boardTop) / bandHeight

    fun stringCenterY(string: Int): Float = boardTop + (stringCount - 1 - string + 0.5f) * bandHeight

    /** 0 = 개방현 셀. 범위 밖의 x는 0 또는 [fretCount]로 클램프한다. */
    fun fretAt(x: Float, layout: FretLayout): Int =
        (floor(layout.uAt(x)).toInt() + 1).coerceIn(0, fretCount)

    fun cellCenterX(fret: Int, layout: FretLayout): Float = layout.xOf(fret - 0.5f)

    companion object {
        const val MIN_SCROLL = -1f
        const val STRIP_FRACTION = 8f / 88f
    }
}
