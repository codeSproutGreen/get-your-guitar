package com.sproutgreen.getyourguitar.ui.fretboard

import com.sproutgreen.getyourguitar.data.FretLayoutKind
import kotlin.math.floor

/**
 * 연주 영역(스크롤 띠 + 지판 + 뮤트 바)의 치수와 터치 판정. Android 의존 없음.
 *
 * 화면 전체 높이 기준으로 상단 바 12% · 스크롤 띠 8% · 지판 66% · 뮤트 바 14% 이므로,
 * 상단 바를 뺀 이 영역 안에서 띠는 8/88, 뮤트 바는 14/88이다. 뮤트 바는 연주하면서 엄지로 누르고 있어야 해서
 * 띠보다 두껍다(S10e에서 약 8 mm).
 * 판정 범위는 넉넉하다: 줄은 밴드 전체 높이, 프렛은 셀 전체 폭.
 *
 * 가로 방향은 [layoutKind]에 따라 등간격 또는 실제 프렛 간격이다. 셀 폭이 일정하다고 가정하는 코드는 여기 밖에
 * 두지 않는다 — 폭이 필요하면 [cellWidthAt], 드래그 환산은 [scrollAfterDrag]를 쓴다.
 */
class FretboardGeometry(
    val width: Float,
    val height: Float,
    val stringCount: Int = 4,
    val fretCount: Int = 24,
    val visibleCells: Int = 12,
    val layoutKind: FretLayoutKind = FretLayoutKind.EQUAL,
) {
    val stripHeight: Float = height * STRIP_FRACTION
    val muteBarHeight: Float = height * MUTE_BAR_FRACTION
    val boardTop: Float = stripHeight
    val boardBottom: Float = height - muteBarHeight
    val boardHeight: Float = boardBottom - boardTop
    val bandHeight: Float = boardHeight / stringCount

    /** 등간격일 때의 셀 폭. 실제 간격에서는 평균값일 뿐이니 [cellWidthAt]을 쓸 것. */
    val cellWidth: Float = width / visibleCells

    /**
     * 스크롤 한계(화면 왼쪽 끝의 u). 등간격은 마지막 12칸이 화면에 꽉 차는 지점. 실제 간격은 높은 프렛일수록 칸이
     * 좁아 더 많이 들어오므로, 마지막 와이어가 화면에 들어오는 가장 작은 정수에서 멈춘다(스냅이 정수 단위라서).
     */
    val maxScroll: Float = when (layoutKind) {
        FretLayoutKind.EQUAL -> (fretCount - visibleCells).toFloat()
        FretLayoutKind.REAL -> {
            val span = RealFretLayout.viewSpan(visibleCells)
            val end = RealFretLayout.distance(fretCount.toFloat())
            var scroll = MIN_SCROLL.toInt()
            while (scroll < fretCount && RealFretLayout.distance(scroll.toFloat()) + span < end - 1e-4f) scroll++
            scroll.toFloat()
        }
    }

    fun layout(scroll: Float): FretLayout = when (layoutKind) {
        FretLayoutKind.EQUAL -> EqualFretLayout(cellWidth, scroll)
        FretLayoutKind.REAL -> RealFretLayout(width, scroll, visibleCells)
    }

    fun clampScroll(scroll: Float): Float = scroll.coerceIn(MIN_SCROLL, maxScroll)

    /** 띠를 [dxPixels]만큼 끌었을 때의 새 스크롤. 지판이 손가락에 붙어 따라오도록 픽셀 거리를 그대로 보존한다. */
    fun scrollAfterDrag(scroll: Float, dxPixels: Float): Float = clampScroll(layout(scroll).uAt(-dxPixels))

    fun isOnBoard(y: Float): Boolean = y >= boardTop && y < boardBottom

    /** 지판 위의 띠: 끌어서 프렛 위치를 옮긴다. */
    fun isOnScrollStrip(y: Float): Boolean = y < boardTop

    /** 지판 아래의 긴 버튼: 누르고 있는 동안 뮤트. */
    fun isOnMuteBar(y: Float): Boolean = y >= boardBottom

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

    /** 셀의 가운데 = 양쪽 와이어의 중점. 실제 간격에서는 u의 중점(n − 0.5)과 화면상의 중점이 다르다. */
    fun cellCenterX(fret: Int, layout: FretLayout): Float = (layout.xOf(fret - 1f) + layout.xOf(fret.toFloat())) / 2f

    fun cellWidthAt(fret: Int, layout: FretLayout): Float = layout.xOf(fret.toFloat()) - layout.xOf(fret - 1f)

    companion object {
        const val MIN_SCROLL = -1f
        const val STRIP_FRACTION = 8f / 88f
        const val MUTE_BAR_FRACTION = 14f / 88f
    }
}
