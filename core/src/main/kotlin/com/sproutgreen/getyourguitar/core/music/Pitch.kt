package com.sproutgreen.getyourguitar.core.music

import kotlin.math.pow

object Pitch {
    /** 12평균율, A4(MIDI 69) = 440 Hz. */
    fun hz(midi: Int): Float = (440.0 * 2.0.pow((midi - 69) / 12.0)).toFloat()
}
