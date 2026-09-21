package com.sproutgreen.getyourguitar.core.synth

import kotlin.math.sin

/**
 * 악기의 "생김새"에서 오는 음색: 어디를 튕기고 어디에 픽업이 있나. 값은 개방현 길이에 대한 브리지로부터의 비율.
 *
 * 배음 k의 세기는 sin(kπ·pluck) · sin(kπ·pickup) / k 에 비례한다.
 * 픽업이 브리지에 가까울수록(값이 작을수록) 기음은 덜 잡히고 중역 배음이 상대적으로 세진다 —
 * 작은 스피커(폰)는 기음을 못 내므로 중역이 있어야 들린다.
 */
class VoiceCharacter(
    val pluckPosition: Float,
    val pickupPosition: Float,
    /** 손끝 질감용 노이즈 비율. 크면 v1.0.0처럼 칙칙거리고 칠 때마다 음색이 달라진다. */
    val noiseMix: Float,
    /**
     * 튕기는 순간의 "딱" 하는 성분. 사각 펄스의 양 모서리에 더하는 임펄스의 세기(0 = 없음).
     * 펄스만으로는 배음이 1/k로 줄어 중역이 약하다. 임펄스 쌍은 펄스의 미분이라 배음이 평평하고,
     * 루프 로우패스가 그것을 먼저 깎아내므로 "어택은 또렷, 서스테인은 둥근" 소리가 된다. 노이즈와 달리 결정적이다.
     */
    val snap: Float = 0f,
    /** 높은 프렛의 음량 보정 세기. 0 = 보정 없음, 1 = 기음 세기를 개방현과 똑같이. */
    val neckCompensation: Float = 0f,
) {
    /**
     * 피크를 맞춘 사각 펄스(듀티 [pluck])가 픽업 빗살([pickup])을 지난 뒤의 기음 세기(상대값).
     * 높은 프렛에서 pluck·pickup이 커지면 이 값이 3~4배까지 커지므로, 음마다 이 값의 역수로 음량을 맞춘다.
     */
    fun fundamentalGain(pluck: Float, pickup: Float): Float =
        (sin(Math.PI * pluck) / (1.0 - pluck) * sin(Math.PI * pickup)).toFloat()

    companion object {
        /**
         * 브리지 쪽 픽업 + 브리지 쪽 핑거링: 중역이 앞에 나오는 소리.
         * 2026-09-21에 조합 8개를 렌더해 대역별 레벨을 재서 골랐다(v1.0.0 단음: 초저역 −33 / 중역 −30 / 고역 −51 dBFS).
         * 이 값 + OUTPUT_GAIN 1.1: E줄 개방 −17 / −30 / −64, G줄 개방 −23 / −20 / −62.
         * → 폰 스피커가 내는 중역은 v1.0.0 이상, 이어폰에서는 저역이 10 dB 넘게 두툼, "칙" 성분은 13 dB 낮다.
         * 음량 보정 0.3이면 RMS가 개방현 0.16 → 12프렛 0.12 → 19프렛 0.10으로 완만히 준다(귀는 높은 음을 더 크게 듣는다).
         */
        val DEFAULT = VoiceCharacter(
            pluckPosition = 0.13f,
            pickupPosition = 0.10f,
            noiseMix = 0.06f,
            snap = 0.4f,
            neckCompensation = 0.3f,
        )
    }
}
