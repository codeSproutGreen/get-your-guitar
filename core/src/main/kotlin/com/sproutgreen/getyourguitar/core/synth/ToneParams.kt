package com.sproutgreen.getyourguitar.core.synth

import kotlin.math.pow

/** 음색 파라미터. 둘 다 0~1로 클램프된다. */
class ToneParams(brightness: Float, decay: Float) {
    val brightness: Float = brightness.coerceIn(0f, 1f)
    val decay: Float = decay.coerceIn(0f, 1f)

    /** 루프 로우패스 컷오프. 500 Hz ~ 6 kHz 로그 보간. */
    fun cutoffHz(): Float = MIN_CUTOFF_HZ * (MAX_CUTOFF_HZ / MIN_CUTOFF_HZ).pow(brightness)

    /** 루프 피드백 게인. 0.990 ~ 0.9995 선형 보간. */
    fun feedback(): Float = MIN_FEEDBACK + decay * (MAX_FEEDBACK - MIN_FEEDBACK)

    companion object {
        const val MIN_CUTOFF_HZ = 500f
        const val MAX_CUTOFF_HZ = 6000f
        const val MIN_FEEDBACK = 0.990f
        const val MAX_FEEDBACK = 0.9995f

        val DEFAULT = ToneParams(brightness = 0.6f, decay = 0.7f)
    }
}
