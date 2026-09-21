package com.sproutgreen.getyourguitar.ui.fretboard

/**
 * 지판 좌표 u(단위: 셀) ↔ 화면 x.
 * 개방현 셀 = [−1, 0), 프렛 n 셀 = [n−1, n), 프렛 와이어는 u = n, 너트는 u = 0.
 * v2의 실제 프렛 간격(`RealFretLayout`)은 이 인터페이스만 구현하면 된다.
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
