package com.sproutgreen.getyourguitar.core.synth

/**
 * 줄 하나의 소리. 합성(v1) → 샘플 재생(v2) 교체 지점.
 * 모든 메서드는 오디오 스레드에서 호출되므로 구현은 객체 할당·락·로그를 하지 않는다.
 */
interface Voice {
    /** 줄을 튕긴다. 이미 울리는 중이어도 클릭 없이 다시 튕겨야 한다. */
    fun noteOn(hz: Float, velocity: Float)

    /** 다시 튕기지 않고 음높이만 바꾼다. 연속 Hz를 받는다. 벤딩처럼 초당 100번 넘게 불려도 된다. */
    fun setPitch(hz: Float)

    /**
     * 프렛을 옮기는 슬라이드. [setPitch]와 같되 옮기는 동안 음량이 살짝 꺼졌다 돌아온다(손가락이 프렛을 넘는 느낌).
     * 벤딩에 쓰면 벤딩 내내 음량이 눌리므로 구분한다.
     */
    fun slideTo(hz: Float) = setPitch(hz)

    /** 손가락을 뗐다. 줄을 뮤트하듯 수십 ms에 걸쳐 잦아든 뒤 비활성이 된다. */
    fun noteOff()

    /** out[offset until offset+frames] 에 **더한다**. 비활성이면 아무것도 하지 않는다. */
    fun render(out: FloatArray, offset: Int, frames: Int)

    val isActive: Boolean

    /** 즉시(2 ms) 페이드아웃하고 비활성이 된다. 앱이 멈출 때처럼 전부 꺼야 할 때. */
    fun silence()
}
