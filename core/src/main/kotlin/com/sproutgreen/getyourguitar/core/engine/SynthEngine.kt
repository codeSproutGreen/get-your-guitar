package com.sproutgreen.getyourguitar.core.engine

import com.sproutgreen.getyourguitar.core.music.Fretboard
import com.sproutgreen.getyourguitar.core.music.Tuning
import com.sproutgreen.getyourguitar.core.synth.AmpFilter
import com.sproutgreen.getyourguitar.core.synth.Mixer
import com.sproutgreen.getyourguitar.core.synth.StringVoices
import com.sproutgreen.getyourguitar.core.synth.VoiceCharacter

/** 오디오 스레드가 쓰고 UI가 읽는 통계. 콜백 없이 폴링한다. */
class EngineStats {
    /** 큐가 가득 차서 버려진 커맨드 수. 생산자(UI 스레드)만 증가시킨다. */
    @Volatile
    var droppedCommands: Int = 0
        internal set
}

/**
 * 순수 pull 렌더러. 출력 백엔드(AudioTrack, 이후 Oboe)는 [render]를 호출하는 쪽만 바꾸면 된다.
 *
 * 스레딩: [send]는 UI 스레드 하나에서만, [render]는 오디오 스레드 하나에서만 호출한다.
 * [render] 경로는 객체 할당·락·로그를 하지 않는다.
 */
class SynthEngine(
    val sampleRate: Int,
    tuning: Tuning = Tuning.STANDARD_BASS_4,
    character: VoiceCharacter = VoiceCharacter.DEFAULT,
) {
    val stats = EngineStats()

    private val fretboard = Fretboard(tuning)
    private val queue = CommandQueue()
    private val voices = StringVoices(
        tuning.stringCount,
        sampleRate,
        openHz = FloatArray(tuning.stringCount) { fretboard.hzAt(it, 0) },
        character = character,
    )
    private val amp = AmpFilter(sampleRate)
    private val slot = IntArray(CommandQueue.SLOT_SIZE)
    private var masterGain = DEFAULT_MASTER_GAIN

    // 줄마다 "지금 짚은 프렛"과 "벤딩량". 실제 음높이는 항상 이 둘에서 계산한다.
    private val fretOf = IntArray(tuning.stringCount)
    private val bendCentsOf = FloatArray(tuning.stringCount)

    /** UI 스레드. 큐가 가득 차면 버리고 false. */
    fun send(cmd: Command): Boolean {
        val accepted = queue.offer(cmd)
        if (!accepted) stats.droppedCommands = stats.droppedCommands + 1
        return accepted
    }

    /** 오디오 스레드. out[0 until frames]를 덮어쓴다(모노, −1..1). */
    fun render(out: FloatArray, frames: Int) {
        while (queue.poll(slot)) apply(slot)
        java.util.Arrays.fill(out, 0, frames, 0f)
        voices.render(out, 0, frames)
        amp.process(out, frames)
        Mixer.process(out, frames, masterGain)
    }

    private fun apply(slot: IntArray) {
        val a = slot[1]
        val b = slot[2]
        when (slot[0]) {
            CommandCodec.TYPE_NOTE_ON ->
                if (fretboard.contains(a, b)) {
                    fretOf[a] = b
                    bendCentsOf[a] = 0f
                    voices.noteOn(a, fretboard.hzAt(a, b), DEFAULT_VELOCITY)
                }
            CommandCodec.TYPE_SLIDE ->
                if (fretboard.contains(a, b)) {
                    fretOf[a] = b
                    voices.setPitch(a, pitchOf(a))
                }
            CommandCodec.TYPE_BEND ->
                if (fretboard.contains(a, 0)) {
                    val cents = Float.fromBits(slot[3])
                    // NaN은 비교가 전부 false라 coerceIn을 통과해 버린다.
                    bendCentsOf[a] = if (cents.isNaN()) 0f else cents.coerceIn(0f, MAX_BEND_CENTS)
                    voices.setPitch(a, pitchOf(a))
                }
            CommandCodec.TYPE_ALL_NOTES_OFF -> {
                voices.silenceAll()
                java.util.Arrays.fill(bendCentsOf, 0f)
            }
            CommandCodec.TYPE_SET_MASTER_GAIN -> masterGain = Float.fromBits(slot[3]).coerceIn(0f, 1f)
        }
    }

    /** 프렛 음 × 2^(cents/1200). Math.pow는 할당하지 않는다. */
    private fun pitchOf(string: Int): Float {
        val fretted = fretboard.hzAt(string, fretOf[string])
        val cents = bendCentsOf[string]
        return if (cents == 0f) fretted else (fretted * Math.pow(2.0, cents / 1200.0)).toFloat()
    }

    companion object {
        const val DEFAULT_MASTER_GAIN = 0.8f

        /** 벤딩 상한. 두 온음(스펙: 최대 1~2음). UI는 보통 이보다 좁은 범위만 쓴다. */
        const val MAX_BEND_CENTS = 400f

        /** v1은 터치 벨로시티가 없다. */
        const val DEFAULT_VELOCITY = 0.8f
    }
}
