package com.sproutgreen.getyourguitar.core.synth

import kotlin.math.abs
import kotlin.math.exp

/**
 * 베이스 앰프·스피커의 고역 롤오프. 1극 로우패스 두 개를 직렬로(−12 dB/oct, 컷오프 4.5 kHz).
 * 튕기는 순간의 날카로운 고역("지직")을 걷어 낸다. 베이스 음역(수십 Hz~2 kHz)은 거의 그대로 지나간다.
 *
 * 상태가 있는 필터라 출력 하나당 인스턴스 하나. 오디오 스레드 전용, 할당 없음.
 */
class AmpFilter(sampleRate: Int, cutoffHz: Float = 4_500f) {
    private val a = (1.0 - exp(-2.0 * Math.PI * cutoffHz / sampleRate)).toFloat()
    private var s1 = 0f
    private var s2 = 0f

    fun process(buf: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            s1 += a * (buf[i] - s1)
            s2 += a * (s1 - s2)
            buf[i] = s2
        }
        // 입력이 멈춘 뒤 상태가 비정규수(denormal) 영역에서 오래 머물지 않게 0으로 떨어뜨린다.
        if (abs(s1) < FLUSH_BELOW) s1 = 0f
        if (abs(s2) < FLUSH_BELOW) s2 = 0f
    }

    private companion object {
        const val FLUSH_BELOW = 1e-20f
    }
}
