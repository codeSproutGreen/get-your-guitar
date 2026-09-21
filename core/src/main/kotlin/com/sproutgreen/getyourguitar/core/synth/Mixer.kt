package com.sproutgreen.getyourguitar.core.synth

import kotlin.math.abs

/** 보이스 합 × 0.5(프리게인) × 마스터 게인 → 소프트 클립 `x / (1 + |x|)`. 출력은 항상 (−1, 1). */
object Mixer {
    const val PRE_GAIN = 0.5f

    fun process(buf: FloatArray, frames: Int, masterGain: Float) {
        val gain = PRE_GAIN * masterGain
        for (i in 0 until frames) {
            val x = buf[i] * gain
            buf[i] = x / (1f + abs(x))
        }
    }
}
