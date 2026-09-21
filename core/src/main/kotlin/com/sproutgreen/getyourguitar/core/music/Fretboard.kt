package com.sproutgreen.getyourguitar.core.music

/**
 * (줄, 프렛) → MIDI·주파수·음이름.
 * Int 오버로드는 객체를 만들지 않으므로 오디오 스레드에서 써도 된다.
 */
class Fretboard(val tuning: Tuning, val fretCount: Int = 24) {
    init {
        require(fretCount >= 0) { "fretCount must not be negative" }
    }

    fun contains(string: Int, fret: Int): Boolean =
        string >= 0 && string < tuning.stringCount && fret >= 0 && fret <= fretCount

    fun midiAt(string: Int, fret: Int): Int {
        require(contains(string, fret)) { "position s$string f$fret is off the fretboard" }
        return tuning.openMidi(string) + fret
    }

    fun hzAt(string: Int, fret: Int): Float = Pitch.hz(midiAt(string, fret))

    fun nameAt(string: Int, fret: Int): String = NoteName.of(midiAt(string, fret))

    fun midiAt(pos: FretPosition): Int = midiAt(pos.string, pos.fret)

    fun hzAt(pos: FretPosition): Float = hzAt(pos.string, pos.fret)

    fun nameAt(pos: FretPosition): String = nameAt(pos.string, pos.fret)
}
