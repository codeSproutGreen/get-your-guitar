package com.sproutgreen.getyourguitar.data

import com.sproutgreen.getyourguitar.core.engine.Command

/**
 * 사용자가 바꿀 수 있는 모든 값. 저장소([SettingsRepository])를 거쳐 재시작 후에도 유지된다.
 * 메트로놈·프렛 간격 설정은 그 기능을 만들 때 추가한다(Preferences DataStore는 키 추가에 마이그레이션이 필요 없다).
 */
data class Settings(
    val masterVolume: Float = 0.8f,
    val brightness: Float = 0.6f,
    /**
     * 누르고 있을 때 음이 버티는 길이. v1.0.0은 0.7이었지만 [holdToSustain]이 기본이 되면서
     * 음 길이는 손가락이 정하게 됐으므로 더 길게 잡는다.
     */
    val decay: Float = 0.8f,
    /** true = 누르고 있는 동안만 소리, 떼면 멈춤. false = 떼도 자연 감쇠(v1.0.0 동작). */
    val holdToSustain: Boolean = true,
    /** 최대 벤딩 폭. 200 = 온음, 400 = 두 온음. */
    val bendRangeCents: Int = 200,
    val showNoteNames: Boolean = false,
    /** 오디오 버퍼 = 기기 버스트 크기 × 이 값. 작을수록 지연이 짧고 끊길 위험이 크다. */
    val audioBufferChunks: Int = 2,
) {
    /** 범위 밖 값(손상된 저장 파일, 이전 버전의 값)을 쓸 수 있는 값으로 되돌린다. */
    fun sanitized(): Settings = copy(
        masterVolume = masterVolume.unit(DEFAULT.masterVolume),
        brightness = brightness.unit(DEFAULT.brightness),
        decay = decay.unit(DEFAULT.decay),
        bendRangeCents = if (bendRangeCents in BEND_RANGES) bendRangeCents else DEFAULT.bendRangeCents,
        audioBufferChunks = if (audioBufferChunks in BUFFER_CHUNKS) audioBufferChunks else DEFAULT.audioBufferChunks,
    )

    private fun Float.unit(fallback: Float): Float = if (isNaN()) fallback else coerceIn(0f, 1f)

    companion object {
        val DEFAULT = Settings()
        val BEND_RANGES = listOf(200, 400)
        val BUFFER_CHUNKS = listOf(2, 3, 4)
    }
}

/** 설정이 바뀌었을 때 엔진·출력에 무엇을 해야 하는지. Android 의존 없는 순수 로직. */
object SettingsDiff {
    /** [old]에서 [new]로 갈 때 엔진에 보낼 커맨드. [old]가 null이면(첫 적용) 전부 보낸다. */
    fun commands(old: Settings?, new: Settings): List<Command> {
        val out = ArrayList<Command>(3)
        if (old == null || old.masterVolume != new.masterVolume) out += Command.SetMasterGain(new.masterVolume)
        if (old == null || old.brightness != new.brightness) out += Command.SetBrightness(new.brightness)
        if (old == null || old.decay != new.decay) out += Command.SetDecay(new.decay)
        return out
    }

    /** 버퍼 크기는 AudioTrack을 다시 만들어야 바뀐다. 첫 적용에서는 이미 맞는 크기로 만들어졌다고 본다. */
    fun needsOutputRestart(old: Settings?, new: Settings): Boolean =
        old != null && old.audioBufferChunks != new.audioBufferChunks
}
