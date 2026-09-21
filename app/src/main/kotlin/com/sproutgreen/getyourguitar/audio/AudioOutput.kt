package com.sproutgreen.getyourguitar.audio

/**
 * 오디오 스레드가 버퍼마다 부르는 렌더 콜백.
 * `(FloatArray, Int) -> Unit` 람다는 호출 때마다 Int를 박싱하므로(오디오 스레드 할당 금지 규칙 위반)
 * 전용 fun interface를 쓴다.
 */
fun interface AudioRenderer {
    fun render(out: FloatArray, frames: Int)
}

/** 출력 백엔드. v1은 [AudioTrackOutput], 지연이 불만족이면 Oboe 구현으로 교체한다. */
interface AudioOutput {
    val sampleRate: Int
    val framesPerBuffer: Int
    val underrunCount: Int

    /** 출력을 시작한다. 장치를 열지 못하면 false. 이미 시작돼 있으면 true. */
    fun start(renderer: AudioRenderer): Boolean

    /** 출력을 멈춘다. 오디오 스레드가 실제로 끝났으면 true (이후에는 다른 스레드가 렌더러를 불러도 안전하다). */
    fun stop(): Boolean
}
