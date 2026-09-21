package com.sproutgreen.getyourguitar.core.music

object NoteName {
    private val NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /** 샤프 표기 음이름(옥타브 없음). 반환값은 미리 만든 문자열이라 할당이 없다. */
    fun of(midi: Int): String = NAMES[Math.floorMod(midi, 12)]
}
