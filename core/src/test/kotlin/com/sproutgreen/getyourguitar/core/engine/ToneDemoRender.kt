package com.sproutgreen.getyourguitar.core.engine

import com.sproutgreen.getyourguitar.core.music.Tuning
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.test.Test

/**
 * 폰 없이 PC에서 음색을 들어 보기 위한 WAV 렌더러. 평소 테스트에서는 아무것도 하지 않는다.
 *
 * ```
 * ./gradlew :core:test --tests "*ToneDemoRender*" -Dgyg.renderWav=true -Dgyg.renderLabel=after
 * → core/build/tone-demo/after.wav
 * ```
 * 같은 악보를 렌더하므로 라벨만 바꿔 두 번 돌리면 음색 변경 전/후를 그대로 비교할 수 있다.
 */
class ToneDemoRender {
    private class Event(val atSeconds: Double, val command: Command)

    @Test
    fun `render the demo phrase to a wav file when asked`() {
        if (System.getProperty("gyg.renderWav") != "true") return
        val label = System.getProperty("gyg.renderLabel") ?: "current"
        val sampleRate = 48_000
        val engine = SynthEngine(sampleRate, Tuning.STANDARD_BASS_4)
        val events = phrase().sortedBy { it.atSeconds }
        val totalFrames = (13.0 * sampleRate).toInt()
        val chunk = 192
        val out = FloatArray(totalFrames)
        val buf = FloatArray(chunk)

        var next = 0
        var done = 0
        while (done < totalFrames) {
            val now = done.toDouble() / sampleRate
            while (next < events.size && events[next].atSeconds <= now) engine.send(events[next++].command)
            val n = minOf(chunk, totalFrames - done)
            engine.render(buf, n)
            System.arraycopy(buf, 0, out, done, n)
            done += n
        }

        val dir = File("build/tone-demo").apply { mkdirs() }
        val file = File(dir, "$label.wav")
        writeWav(file, out, sampleRate)
        println("TONE-DEMO wrote ${file.absolutePath}")
    }

    /** 개방현 → 저음 리프 → G줄 고음 → 슬라이드 → 벤딩 → 레이크 화음. */
    private fun phrase(): List<Event> {
        val e = ArrayList<Event>()
        fun at(t: Double, c: Command) { e += Event(t, c) }

        // 개방현 E A D G
        for ((i, s) in listOf(0, 1, 2, 3).withIndex()) at(0.1 + i * 0.7, Command.NoteOn(s, 0))

        // 저음 리프
        val riff = listOf(0 to 3, 0 to 5, 1 to 3, 1 to 5, 2 to 3, 2 to 5)
        for ((i, p) in riff.withIndex()) at(2.9 + i * 0.35, Command.NoteOn(p.first, p.second))

        // G줄 고음역
        for ((i, fret) in listOf(12, 14, 16, 19).withIndex()) at(5.1 + i * 0.5, Command.NoteOn(3, fret))

        // 슬라이드
        at(7.1, Command.NoteOn(1, 5))
        at(7.40, Command.Slide(1, 6))
        at(7.45, Command.Slide(1, 7))

        // 벤딩: 온음 올렸다가 내리기
        at(8.1, Command.NoteOn(2, 7))
        for (i in 0..50) at(8.4 + i * 0.01, Command.Bend(2, i * 4f))
        for (i in 0..30) at(9.1 + i * 0.01, Command.Bend(2, 200f - i * 200f / 30f))

        // 레이크 화음 (G 메이저 느낌: G B D G)
        at(9.8, Command.NoteOn(0, 3))
        at(9.84, Command.NoteOn(1, 2))
        at(9.88, Command.NoteOn(2, 0))
        at(9.92, Command.NoteOn(3, 0))
        return e
    }

    private fun writeWav(file: File, samples: FloatArray, sampleRate: Int) {
        val dataBytes = samples.size * 2
        DataOutputStream(FileOutputStream(file).buffered()).use { o ->
            fun le32(v: Int) { o.write(v and 0xFF); o.write(v shr 8 and 0xFF); o.write(v shr 16 and 0xFF); o.write(v shr 24 and 0xFF) }
            fun le16(v: Int) { o.write(v and 0xFF); o.write(v shr 8 and 0xFF) }
            o.writeBytes("RIFF"); le32(36 + dataBytes); o.writeBytes("WAVE")
            o.writeBytes("fmt "); le32(16); le16(1); le16(1); le32(sampleRate); le32(sampleRate * 2); le16(2); le16(16)
            o.writeBytes("data"); le32(dataBytes)
            for (x in samples) le16((x.coerceIn(-1f, 1f) * 32767f).toInt())
        }
    }
}
