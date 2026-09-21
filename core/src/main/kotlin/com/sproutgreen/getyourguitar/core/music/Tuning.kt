package com.sproutgreen.getyourguitar.core.music

/** 개방현의 MIDI 번호 목록. 줄 인덱스 0이 가장 낮은 줄이다. */
class Tuning(openMidi: IntArray) {
    private val open: IntArray = openMidi.copyOf()

    init {
        require(open.isNotEmpty()) { "tuning needs at least one string" }
    }

    val stringCount: Int get() = open.size

    fun openMidi(string: Int): Int {
        require(string in open.indices) { "string $string out of range 0..${open.size - 1}" }
        return open[string]
    }

    companion object {
        /** 4현 베이스 표준 튜닝 E1 A1 D2 G2. */
        val STANDARD_BASS_4 = Tuning(intArrayOf(28, 33, 38, 43))
    }
}
