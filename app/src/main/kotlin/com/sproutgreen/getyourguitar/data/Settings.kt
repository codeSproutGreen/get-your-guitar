package com.sproutgreen.getyourguitar.data

import com.sproutgreen.getyourguitar.core.engine.Command

/** 프렛 와이어의 가로 간격. */
enum class FretLayoutKind {
    /** 모든 프렛의 폭이 같다. 누르기 쉽다. 기본값. */
    EQUAL,

    /** 실제 악기처럼 바디 쪽으로 갈수록 좁아진다(한 프렛마다 약 5.6%). 실물 연습용. */
    REAL,
}

/**
 * 사용자가 바꿀 수 있는 모든 값. 저장소([SettingsRepository])를 거쳐 재시작 후에도 유지된다.
 * 메트로놈 설정은 그 기능을 만들 때 추가한다(Preferences DataStore는 키 추가에 마이그레이션이 필요 없다).
 */
data class Settings(
    val masterVolume: Float = 0.8f,
    val brightness: Float = 0.6f,
    /** 줄이 스스로 잦아드는 길이. 클수록 오래 울린다. 음을 일찍 끊는 건 설정이 아니라 지판 아래의 뮤트 바다. */
    val decay: Float = 0.7f,
    /** 최대 벤딩 폭. 200 = 온음, 400 = 두 온음. */
    val bendRangeCents: Int = 200,
    /**
     * 레이크와 벤딩을 가르는 시간(ms). 짚고 이 시간 안에 세로로 움직이면 레이크, 그동안 가만히 있었으면 이후의
     * 세로 이동은 벤딩. 길수록 느린 레이크까지 잡지만 벤딩은 그만큼 기다렸다 밀어야 한다. 연주자마다 손버릇이
     * 달라 설정으로 뺐다(클라이언트 요청 2026-09-21).
     */
    val rakeSettleMs: Int = 250,
    val fretLayout: FretLayoutKind = FretLayoutKind.EQUAL,
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
        rakeSettleMs = rakeSettleMs.coerceIn(MIN_RAKE_SETTLE_MS, MAX_RAKE_SETTLE_MS),
        audioBufferChunks = if (audioBufferChunks in BUFFER_CHUNKS) audioBufferChunks else DEFAULT.audioBufferChunks,
    )

    private fun Float.unit(fallback: Float): Float = if (isNaN()) fallback else coerceIn(0f, 1f)

    companion object {
        val DEFAULT = Settings()
        val BEND_RANGES = listOf(200, 400)
        val BUFFER_CHUNKS = listOf(2, 3, 4)
        const val MIN_RAKE_SETTLE_MS = 100
        const val MAX_RAKE_SETTLE_MS = 500
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
