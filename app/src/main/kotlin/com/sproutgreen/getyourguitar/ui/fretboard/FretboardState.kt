package com.sproutgreen.getyourguitar.ui.fretboard

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 방금 소리 난 셀. [startNanos]는 `System.nanoTime()` 기준(프레임 시계와 같은 시간축). */
data class Highlight(val string: Int, val fret: Int, val startNanos: Long)

/** 지판 화면의 UI 상태: 스크롤 위치와 하이라이트. 오디오와 무관하다. */
@Stable
class FretboardState {
    /** 화면 왼쪽 끝의 지판 좌표 u. −1(개방현 + 1~11프렛) ~ 12(13~24프렛). */
    var scroll by mutableFloatStateOf(FretboardGeometry.MIN_SCROLL)
        private set

    val highlights = mutableStateListOf<Highlight>()

    private var settleJob: Job? = null

    fun beginDrag() {
        settleJob?.cancel()
        settleJob = null
    }

    fun dragBy(deltaCells: Float, geometry: FretboardGeometry) {
        scroll = geometry.clampScroll(scroll + deltaCells)
    }

    /** 손을 떼면 가장 가까운 프렛 경계로 150 ms 스냅. */
    fun settle(scope: CoroutineScope, geometry: FretboardGeometry) {
        val target = geometry.clampScroll(scroll.roundToInt().toFloat())
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(initialValue = scroll, targetValue = target, animationSpec = tween(SNAP_MS)) { value, _ ->
                scroll = value
            }
        }
    }

    /** 줄당 하이라이트는 하나. 슬라이드·레이크로 셀이 바뀌면 새 셀에서 다시 시작한다. */
    fun highlight(string: Int, fret: Int) {
        highlights.removeAll { it.string == string }
        highlights.add(Highlight(string, fret, System.nanoTime()))
    }

    fun pruneHighlights(nowNanos: Long) {
        highlights.removeAll { nowNanos - it.startNanos >= HIGHLIGHT_NANOS }
    }

    companion object {
        const val SNAP_MS = 150
        const val HIGHLIGHT_NANOS = 1_500_000_000L
    }
}
