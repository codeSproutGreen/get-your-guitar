package com.sproutgreen.getyourguitar.ui.fretboard

import com.sproutgreen.getyourguitar.core.engine.Command

/**
 * 포인터별로 마지막 (줄, 프렛)을 기억해 터치 이벤트를 커맨드로 바꾼다. Android 의존 없음.
 *
 * | 이벤트 | 동작 |
 * |---|---|
 * | down | NoteOn |
 * | move, 같은 줄·다른 프렛 | Slide |
 * | move, 다른 줄 진입 | 새 줄에 NoteOn(레이크). 이전 줄은 계속 감쇠 |
 * | move, 같은 셀 | 무시 |
 * | up / cancel | 아무것도 보내지 않음(자연 감쇠) |
 *
 * 같은 줄에 두 포인터가 있으면 둘 다 보내고, 엔진에서 마지막 커맨드가 이긴다.
 */
class FretboardTouchTracker(
    private val send: (Command) -> Unit,
    private val onSounded: (string: Int, fret: Int) -> Unit = { _, _ -> },
) {
    private val lastCell = HashMap<Long, Long>()

    fun down(pointerId: Long, string: Int, fret: Int) {
        lastCell[pointerId] = pack(string, fret)
        send(Command.NoteOn(string, fret))
        onSounded(string, fret)
    }

    fun move(pointerId: Long, string: Int, fret: Int) {
        val previous = lastCell[pointerId] ?: return
        val cell = pack(string, fret)
        if (cell == previous) return
        lastCell[pointerId] = cell
        if (string == stringOf(previous)) send(Command.Slide(string, fret)) else send(Command.NoteOn(string, fret))
        onSounded(string, fret)
    }

    fun up(pointerId: Long) {
        lastCell.remove(pointerId)
    }

    fun cancelAll() {
        lastCell.clear()
    }

    private fun pack(string: Int, fret: Int): Long = (string.toLong() shl 32) or (fret.toLong() and 0xFFFFFFFFL)

    private fun stringOf(cell: Long): Int = (cell shr 32).toInt()
}
