package com.sproutgreen.getyourguitar.core.engine

/**
 * UI 스레드 → 오디오 스레드 메시지. UI 쪽에서는 객체로 다루고(할당 허용),
 * 큐를 건널 때 [CommandCodec]이 Int 4개로 풀어 오디오 스레드는 할당 없이 읽는다.
 * M2(음색·설정), M3(메트로놈)에서 종류가 늘어난다.
 */
sealed interface Command {
    /** 줄을 튕긴다. */
    data class NoteOn(val string: Int, val fret: Int) : Command

    /** 다시 튕기지 않고 같은 줄의 음높이를 옮긴다. */
    data class Slide(val string: Int, val fret: Int) : Command

    data object AllNotesOff : Command

    data class SetMasterGain(val gain: Float) : Command
}

/** 슬롯 레이아웃: [type, a, b, floatBits]. */
object CommandCodec {
    const val TYPE_NOTE_ON = 1
    const val TYPE_SLIDE = 2
    const val TYPE_ALL_NOTES_OFF = 3
    const val TYPE_SET_MASTER_GAIN = 4

    fun type(cmd: Command): Int = when (cmd) {
        is Command.NoteOn -> TYPE_NOTE_ON
        is Command.Slide -> TYPE_SLIDE
        Command.AllNotesOff -> TYPE_ALL_NOTES_OFF
        is Command.SetMasterGain -> TYPE_SET_MASTER_GAIN
    }

    fun argA(cmd: Command): Int = when (cmd) {
        is Command.NoteOn -> cmd.string
        is Command.Slide -> cmd.string
        else -> 0
    }

    fun argB(cmd: Command): Int = when (cmd) {
        is Command.NoteOn -> cmd.fret
        is Command.Slide -> cmd.fret
        else -> 0
    }

    fun floatBits(cmd: Command): Int = when (cmd) {
        is Command.SetMasterGain -> cmd.gain.toRawBits()
        else -> 0
    }

    /** 테스트·디버깅용. 객체를 만들므로 오디오 스레드에서 쓰지 않는다. */
    fun decode(slot: IntArray): Command = when (slot[0]) {
        TYPE_NOTE_ON -> Command.NoteOn(slot[1], slot[2])
        TYPE_SLIDE -> Command.Slide(slot[1], slot[2])
        TYPE_ALL_NOTES_OFF -> Command.AllNotesOff
        TYPE_SET_MASTER_GAIN -> Command.SetMasterGain(Float.fromBits(slot[3]))
        else -> throw IllegalArgumentException("unknown command type ${slot[0]}")
    }
}
