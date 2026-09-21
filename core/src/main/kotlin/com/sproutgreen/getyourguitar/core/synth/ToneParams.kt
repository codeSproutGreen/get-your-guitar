package com.sproutgreen.getyourguitar.core.synth

import kotlin.math.pow
import kotlin.math.sqrt

/** 음색 파라미터. 둘 다 0~1로 클램프된다. */
class ToneParams(brightness: Float, decay: Float) {
    val brightness: Float = brightness.coerceIn(0f, 1f)
    val decay: Float = decay.coerceIn(0f, 1f)

    /** 루프 로우패스 컷오프. 500 Hz ~ 6 kHz 로그 보간. */
    fun cutoffHz(): Float = MIN_CUTOFF_HZ * (MAX_CUTOFF_HZ / MIN_CUTOFF_HZ).pow(brightness)

    /**
     * [noteHz]를 연주할 때의 루프 컷오프. 기준음(E1) 위에서는 음높이의 제곱근에 비례해 올라간다.
     *
     * 컷오프가 고정이면 1극 로우패스가 높은 음의 기음까지 주기마다 깎아서 감쇠율이 음높이의
     * 세제곱으로 커진다(기본 톤에서 E1 −8 dB/s, G4 −56 dB/s — 실기기에서 "고음이 짧다"는 피드백).
     * 완전 비례(배음 수 고정)로 하면 밝기 값의 의미가 음역마다 달라지므로 제곱근으로 절충한다.
     */
    fun cutoffHz(noteHz: Float): Float {
        val ratio = (noteHz / TRACKING_REFERENCE_HZ).coerceAtLeast(1f)
        return (cutoffHz() * sqrt(ratio)).coerceAtMost(MAX_TRACKED_CUTOFF_HZ)
    }

    /** 루프 피드백 게인. 0.990 ~ 0.9995 선형 보간. */
    fun feedback(): Float = MIN_FEEDBACK + decay * (MAX_FEEDBACK - MIN_FEEDBACK)

    companion object {
        const val MIN_CUTOFF_HZ = 500f
        const val MAX_CUTOFF_HZ = 6000f
        const val MIN_FEEDBACK = 0.990f
        const val MAX_FEEDBACK = 0.9995f

        /** E1. 4현 베이스의 최저음이라 이 음에서는 컷오프가 밝기 값 그대로다. */
        const val TRACKING_REFERENCE_HZ = 41.2f
        const val MAX_TRACKED_CUTOFF_HZ = 12_000f

        val DEFAULT = ToneParams(brightness = 0.6f, decay = 0.7f)
    }
}
