package com.sproutgreen.getyourguitar.ui.fretboard

import kotlin.math.ln
import kotlin.math.pow

/**
 * 지판 좌표 u(단위: 셀) ↔ 화면 x.
 * 개방현 셀 = [−1, 0), 프렛 n 셀 = [n−1, n), 프렛 와이어는 u = n, 너트는 u = 0.
 * 터치 판정·하이라이트·마크는 전부 이 변환만 거치므로 레이아웃을 바꾸면 따라온다.
 */
interface FretLayout {
    fun xOf(u: Float): Float
    fun uAt(x: Float): Float
}

/** 모든 셀의 폭이 같은 레이아웃. [scroll]은 화면 왼쪽 끝의 u. */
class EqualFretLayout(private val cellWidth: Float, private val scroll: Float) : FretLayout {
    override fun xOf(u: Float): Float = (u - scroll) * cellWidth
    override fun uAt(x: Float): Float = x / cellWidth + scroll
}

/**
 * 실제 악기의 프렛 간격. 너트에서 n번 와이어까지의 거리는 `L · (1 − 2^(−n/12))`라서 한 프렛 올라갈 때마다
 * 셀이 2^(−1/12)배(약 5.6%)씩 좁아지고, 12프렛이 현 길이의 절반에 온다.
 *
 * 배율은 고정이다: 기본 화면(개방현 셀 + 1~[visibleCells]−1 프렛)이 [viewWidth]를 꽉 채우게 잡고, 스크롤해도
 * 바뀌지 않는다. 그래서 높은 쪽으로 갈수록 한 화면에 더 많은 프렛이 보인다. 스크롤할 때마다 확대 비율이
 * 바뀌면 같은 프렛이 위치마다 다른 크기로 보여 손이 익숙해질 수 없다.
 *
 * 너트 왼쪽(개방현 셀, u < 0)은 물리적 의미가 없으므로 1프렛 셀과 같은 폭으로 선형 연장한다.
 */
class RealFretLayout(viewWidth: Float, scroll: Float, visibleCells: Int = 12) : FretLayout {
    private val pixelsPerScale = viewWidth / viewSpan(visibleCells)
    private val origin = distance(scroll)

    override fun xOf(u: Float): Float = (distance(u) - origin) * pixelsPerScale
    override fun uAt(x: Float): Float = position(x / pixelsPerScale + origin)

    companion object {
        /** 1프렛 셀의 폭(현 길이 L = 1 기준). 개방현 셀도 이 폭이다. */
        private val FIRST_CELL = 1.0 - 2.0.pow(-1.0 / 12.0)
        private val LN2 = ln(2.0)

        /** 너트에서 u까지의 거리(L = 1). */
        fun distance(u: Float): Float =
            if (u >= 0f) (1.0 - 2.0.pow(-u / 12.0)).toFloat() else (u * FIRST_CELL).toFloat()

        /** [distance]의 역함수. 브리지(거리 1)에 닿기 전까지만 의미가 있다. */
        fun position(distance: Float): Float =
            if (distance >= 0f) (-12.0 * ln(1.0 - distance.coerceAtMost(0.999f)) / LN2).toFloat()
            else (distance / FIRST_CELL).toFloat()

        /** 기본 화면이 차지하는 거리: 개방현 셀의 왼쪽 끝 ~ (visibleCells − 1)번 와이어. */
        fun viewSpan(visibleCells: Int): Float = distance(visibleCells - 1f) - distance(-1f)
    }
}
