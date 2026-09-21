package com.sproutgreen.getyourguitar.core.engine

/**
 * UI 스레드 → 오디오 스레드 메시지. UI 쪽에서는 객체로 다루고(할당 허용),
 * 큐를 건널 때 [CommandCodec]이 Int 4개로 풀어 오디오 스레드는 할당 없이 읽는다.
 * M2(음색·설정), M3(메트로놈)에서 종류가 늘어난다. 타입 번호는 한 번 정하면 바꾸지 않는다.
 */
sealed interface Command {
    /** 줄을 튕긴다. */
    data class NoteOn(val string: Int, val fret: Int) : Command

    /** 다시 튕기지 않고 같은 줄의 음높이를 옮긴다. */
    data class Slide(val string: Int, val fret: Int) : Command

    /**
     * 현을 위아래로 밀어 음을 올린다. [cents]는 프렛 음 기준 상승량(0 = 벤딩 없음).
     * 줄에 붙는 상태라서 같은 줄의 [Slide]에는 유지되고 [NoteOn]에서 0으로 돌아간다.
     */
    data class Bend(val string: Int, val cents: Float) : Command

    /** 손가락을 뗐다 — 그 줄을 뮤트한다("누르고 있는 동안만 소리" 모드에서 UI가 보낸다). */
    data class NoteOff(val string: Int) : Command

    data object AllNotesOff : Command

    data class SetMasterGain(val gain: Float) : Command

    /** 0~1. 루프 로우패스 컷오프(밝기). */
    data class SetBrightness(val value: Float) : Command

    /** 0~1. 루프 피드백(누르고 있을 때 음이 버티는 길이). */
    data class SetDecay(val value: Float) : Command
}

/** 슬롯 레이아웃: [type, a, b, floatBits]. */
object CommandCodec {
    const val TYPE_NOTE_ON = 1
    const val TYPE_SLIDE = 2
    const val TYPE_ALL_NOTES_OFF = 3
    const val TYPE_SET_MASTER_GAIN = 4
    const val TYPE_BEND = 5
    const val TYPE_NOTE_OFF = 6
    const val TYPE_SET_BRIGHTNESS = 7
    const val TYPE_SET_DECAY = 8

    fun type(cmd: Command): Int = when (cmd) {
        is Command.NoteOn -> TYPE_NOTE_ON
        is Command.Slide -> TYPE_SLIDE
        Command.AllNotesOff -> TYPE_ALL_NOTES_OFF
        is Command.SetMasterGain -> TYPE_SET_MASTER_GAIN
        is Command.Bend -> TYPE_BEND
        is Command.NoteOff -> TYPE_NOTE_OFF
        is Command.SetBrightness -> TYPE_SET_BRIGHTNESS
        is Command.SetDecay -> TYPE_SET_DECAY
    }

    fun argA(cmd: Command): Int = when (cmd) {
        is Command.NoteOn -> cmd.string
        is Command.Slide -> cmd.string
        is Command.Bend -> cmd.string
        is Command.NoteOff -> cmd.string
        else -> 0
    }

    fun argB(cmd: Command): Int = when (cmd) {
        is Command.NoteOn -> cmd.fret
        is Command.Slide -> cmd.fret
        else -> 0
    }

    fun floatBits(cmd: Command): Int = when (cmd) {
        is Command.SetMasterGain -> cmd.gain.toRawBits()
        is Command.Bend -> cmd.cents.toRawBits()
        is Command.SetBrightness -> cmd.value.toRawBits()
        is Command.SetDecay -> cmd.value.toRawBits()
        else -> 0
    }

    /** 테스트·디버깅용. 객체를 만들므로 오디오 스레드에서 쓰지 않는다. */
    fun decode(slot: IntArray): Command = when (slot[0]) {
        TYPE_NOTE_ON -> Command.NoteOn(slot[1], slot[2])
        TYPE_SLIDE -> Command.Slide(slot[1], slot[2])
        TYPE_ALL_NOTES_OFF -> Command.AllNotesOff
        TYPE_SET_MASTER_GAIN -> Command.SetMasterGain(Float.fromBits(slot[3]))
        TYPE_BEND -> Command.Bend(slot[1], Float.fromBits(slot[3]))
        TYPE_NOTE_OFF -> Command.NoteOff(slot[1])
        TYPE_SET_BRIGHTNESS -> Command.SetBrightness(Float.fromBits(slot[3]))
        TYPE_SET_DECAY -> Command.SetDecay(Float.fromBits(slot[3]))
        else -> throw IllegalArgumentException("unknown command type ${slot[0]}")
    }
}
