package com.sproutgreen.getyourguitar.core.synth

import kotlin.math.abs
import kotlin.math.tanh

/**
 * 보이스 합 × 0.5(프리게인) × 마스터 게인 → 리미터. 출력은 항상 [−1, 1].
 *
 * 리미터는 [KNEE]까지 완전히 선형이고 그 위에서만 tanh로 부드럽게 눌린다.
 * v1.0.0의 `x / (1 + |x|)`는 선형 구간이 없어서 보통 음량(0.1~0.3)에서도 9~23% 눌렸고,
 * 그만큼의 배음 왜곡과 화음의 혼변조가 항상 섞였다.
 */
object Mixer {
    const val PRE_GAIN = 0.5f
    const val KNEE = 0.6f
    private const val HEADROOM = 1f - KNEE

    fun process(buf: FloatArray, frames: Int, masterGain: Float) {
        val gain = PRE_GAIN * masterGain
        for (i in 0 until frames) {
            val x = buf[i] * gain
            val magnitude = abs(x)
            buf[i] = if (magnitude <= KNEE) {
                x
            } else {
                val limited = KNEE + HEADROOM * tanh((magnitude - KNEE) / HEADROOM)
                if (x < 0f) -limited else limited
            }
        }
    }
}
