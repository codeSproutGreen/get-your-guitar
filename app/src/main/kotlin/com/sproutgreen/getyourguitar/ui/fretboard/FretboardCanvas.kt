package com.sproutgreen.getyourguitar.ui.fretboard

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sproutgreen.getyourguitar.core.music.Fretboard
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

private val StripColor = Color(0xFF17120E)
private val StripTextColor = Color(0xFFB8A99A)
private val WoodTop = Color(0xFF6B4528)
private val WoodBottom = Color(0xFF43290F)
private val HeadColor = Color(0xFF241810)
private val NutColor = Color(0xFFEDE6D6)
private val FretWireColor = Color(0xFFC8CCD0)
private val MarkColor = Color(0xFFE9DFC9)
private val StringColor = Color(0xFFD9D9D6)
private val StringShadow = Color(0x66000000)
private val HighlightColor = Color(0xFFFFB300)
private val NameBubble = Color(0xCC17120E)
private val NameText = Color(0xFFF5EFE4)

private val SINGLE_MARKS = intArrayOf(3, 5, 7, 9, 15, 17, 19, 21)
private val DOUBLE_MARKS = intArrayOf(12, 24)

/** 위 띠(프렛 번호) + 지판 + 아래 띠를 한 Canvas에 그린다. 터치는 받지 않는다(제스처는 바깥 Modifier). */
@Composable
fun FretboardCanvas(
    state: FretboardState,
    fretboard: Fretboard,
    showNoteNames: Boolean,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer(cacheSize = 64)
    var nowNanos by remember { mutableLongStateOf(System.nanoTime()) }

    // 하이라이트가 있을 때만 프레임마다 다시 그린다. 없으면 루프가 끝나 유휴 상태에서 그리지 않는다.
    val animating = state.highlights.isNotEmpty()
    LaunchedEffect(animating) {
        while (state.highlights.isNotEmpty()) {
            withFrameNanos { frameTime ->
                nowNanos = frameTime
                state.pruneHighlights(frameTime)
            }
        }
    }

    Canvas(modifier) {
        val geometry = FretboardGeometry(size.width, size.height, fretboard.tuning.stringCount, fretboard.fretCount)
        val layout = geometry.layout(state.scroll)
        val firstFret = floor(state.scroll).toInt().coerceAtLeast(0)
        val lastFret = (ceil(state.scroll).toInt() + geometry.visibleCells).coerceAtMost(fretboard.fretCount)

        drawBoard(geometry, layout, firstFret, lastFret)
        drawStrips(geometry, layout, firstFret, lastFret, textMeasurer)
        drawHighlights(geometry, layout, state.highlights, nowNanos)
        drawStrings(geometry, state.bends)
        if (showNoteNames) drawNoteNames(geometry, layout, fretboard, firstFret, lastFret, textMeasurer)
    }
}

private fun DrawScope.drawBoard(geometry: FretboardGeometry, layout: FretLayout, firstFret: Int, lastFret: Int) {
    drawRect(
        brush = Brush.verticalGradient(listOf(WoodTop, WoodBottom), geometry.boardTop, geometry.boardBottom),
        topLeft = Offset(0f, geometry.boardTop),
        size = Size(size.width, geometry.boardHeight),
    )

    // 너트 왼쪽(개방현 셀)은 헤드 쪽이라 어둡게 구분한다.
    val nutX = layout.xOf(0f)
    if (nutX > 0f) {
        drawRect(HeadColor, Offset(0f, geometry.boardTop), Size(min(nutX, size.width), geometry.boardHeight))
    }

    val markRadius = min(geometry.cellWidth, geometry.bandHeight) * 0.13f
    val centerY = geometry.boardTop + geometry.boardHeight / 2f
    for (fret in SINGLE_MARKS) {
        if (fret in firstFret..lastFret) drawCircle(MarkColor, markRadius, Offset(geometry.cellCenterX(fret, layout), centerY))
    }
    for (fret in DOUBLE_MARKS) {
        if (fret in firstFret..lastFret) {
            val x = geometry.cellCenterX(fret, layout)
            drawCircle(MarkColor, markRadius, Offset(x, geometry.boardTop + geometry.bandHeight))
            drawCircle(MarkColor, markRadius, Offset(x, geometry.boardBottom - geometry.bandHeight))
        }
    }

    val wire = 3.dp.toPx()
    for (fret in maxOf(firstFret, 1)..lastFret) {
        val x = layout.xOf(fret.toFloat())
        drawLine(FretWireColor, Offset(x, geometry.boardTop), Offset(x, geometry.boardBottom), wire)
    }
    if (nutX >= -8.dp.toPx() && nutX <= size.width) {
        drawLine(NutColor, Offset(nutX, geometry.boardTop), Offset(nutX, geometry.boardBottom), 7.dp.toPx())
    }
}

private fun DrawScope.drawStrips(
    geometry: FretboardGeometry,
    layout: FretLayout,
    firstFret: Int,
    lastFret: Int,
    textMeasurer: TextMeasurer,
) {
    drawRect(StripColor, Offset.Zero, Size(size.width, geometry.stripHeight))
    drawRect(StripColor, Offset(0f, geometry.boardBottom), Size(size.width, geometry.stripHeight))

    val style = TextStyle(color = StripTextColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    for (fret in firstFret..lastFret) {
        val text = textMeasurer.measure(fret.toString(), style)
        val x = geometry.cellCenterX(fret, layout) - text.size.width / 2f
        if (x + text.size.width < 0f || x > size.width) continue
        drawText(text, topLeft = Offset(x, (geometry.stripHeight - text.size.height) / 2f))
    }
}

private fun DrawScope.drawStrings(geometry: FretboardGeometry, bends: Map<Int, BendVisual>) {
    // 줄 0(E)이 가장 굵다.
    val lowest = 5.5f
    val step = 1.1f
    for (string in 0 until geometry.stringCount) {
        val y = geometry.stringCenterY(string)
        val thickness = (lowest - step * string).coerceAtLeast(1.5f).dp.toPx()
        val shadow = thickness * 0.6f
        val bend = bends[string]
        if (bend == null) {
            drawLine(StringShadow, Offset(0f, y + shadow), Offset(size.width, y + shadow), thickness)
            drawLine(StringColor, Offset(0f, y), Offset(size.width, y), thickness)
        } else {
            // 당긴 줄: 손가락 위치를 꼭짓점으로 하는 꺾은선. 손가락을 그대로 따라가 "당기는" 느낌을 준다.
            val apex = Offset(bend.x.coerceIn(0f, size.width), y + bend.offsetBands * geometry.bandHeight)
            val below = Offset(0f, shadow)
            drawLine(StringShadow, Offset(0f, y) + below, apex + below, thickness, StrokeCap.Round)
            drawLine(StringShadow, apex + below, Offset(size.width, y) + below, thickness, StrokeCap.Round)
            drawLine(StringColor, Offset(0f, y), apex, thickness, StrokeCap.Round)
            drawLine(StringColor, apex, Offset(size.width, y), thickness, StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawHighlights(
    geometry: FretboardGeometry,
    layout: FretLayout,
    highlights: List<Highlight>,
    nowNanos: Long,
) {
    val radius = min(geometry.cellWidth, geometry.bandHeight) * 0.34f
    for (h in highlights) {
        val age = (nowNanos - h.startNanos).coerceAtLeast(0L).toFloat() / FretboardState.HIGHLIGHT_NANOS
        val alpha = (1f - age).coerceIn(0f, 1f)
        if (alpha <= 0f) continue
        drawCircle(
            color = HighlightColor.copy(alpha = alpha * 0.85f),
            radius = radius,
            center = Offset(geometry.cellCenterX(h.fret, layout), geometry.stringCenterY(h.string)),
        )
    }
}

private fun DrawScope.drawNoteNames(
    geometry: FretboardGeometry,
    layout: FretLayout,
    fretboard: Fretboard,
    firstFret: Int,
    lastFret: Int,
    textMeasurer: TextMeasurer,
) {
    val style = TextStyle(color = NameText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    val bubble = min(geometry.cellWidth, geometry.bandHeight) * 0.2f
    for (fret in firstFret..lastFret) {
        val x = geometry.cellCenterX(fret, layout)
        if (x < -bubble || x > size.width + bubble) continue
        for (string in 0 until geometry.stringCount) {
            val y = geometry.stringCenterY(string)
            val text = textMeasurer.measure(fretboard.nameAt(string, fret), style)
            drawCircle(NameBubble, bubble, Offset(x, y))
            drawText(text, topLeft = Offset(x - text.size.width / 2f, y - text.size.height / 2f))
        }
    }
}
