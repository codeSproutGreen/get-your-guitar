package com.sproutgreen.getyourguitar.core.synth

/** 줄당 보이스 1개. 같은 줄에 대한 새 커맨드는 항상 마지막 것이 이긴다. 범위 밖 줄 번호는 무시한다. */
class StringVoices(
    val count: Int,
    sampleRate: Int,
    tone: ToneParams = ToneParams.DEFAULT,
) {
    private val voices: Array<KarplusStrongVoice> =
        Array(count) { KarplusStrongVoice(sampleRate, tone, seed = 0x2F6E2B1 + it * 7919) }

    val anyActive: Boolean
        get() {
            for (i in 0 until count) if (voices[i].isActive) return true
            return false
        }

    fun noteOn(string: Int, hz: Float, velocity: Float) {
        if (string < 0 || string >= count) return
        voices[string].noteOn(hz, velocity)
    }

    fun setPitch(string: Int, hz: Float) {
        if (string < 0 || string >= count) return
        voices[string].setPitch(hz)
    }

    fun silenceAll() {
        for (i in 0 until count) voices[i].silence()
    }

    fun setTone(tone: ToneParams) {
        for (i in 0 until count) voices[i].setTone(tone)
    }

    /** 활성 보이스를 out에 더한다. */
    fun render(out: FloatArray, offset: Int, frames: Int) {
        for (i in 0 until count) voices[i].render(out, offset, frames)
    }
}
