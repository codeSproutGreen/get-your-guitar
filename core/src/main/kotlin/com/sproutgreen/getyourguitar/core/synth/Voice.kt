package com.sproutgreen.getyourguitar.core.synth

/**
 * 줄 하나의 소리. 합성(v1) → 샘플 재생(v2) 교체 지점.
 * 모든 메서드는 오디오 스레드에서 호출되므로 구현은 객체 할당·락·로그를 하지 않는다.
 */
interface Voice {
    /** 줄을 튕긴다. 이미 울리는 중이어도 클릭 없이 다시 튕겨야 한다. */
    fun noteOn(hz: Float, velocity: Float)

    /** 다시 튕기지 않고 음높이만 바꾼다(슬라이드, v2 벤딩). 연속 Hz를 받는다. */
    fun setPitch(hz: Float)

    /** out[offset until offset+frames] 에 **더한다**. 비활성이면 아무것도 하지 않는다. */
    fun render(out: FloatArray, offset: Int, frames: Int)

    val isActive: Boolean

    /** 빠르게 페이드아웃하고 비활성이 된다. */
    fun silence()
}
