package com.sproutgreen.getyourguitar.core.engine

import com.sproutgreen.getyourguitar.core.music.Pitch
import com.sproutgreen.getyourguitar.core.music.Tuning
import com.sproutgreen.getyourguitar.core.synth.SignalAnalysis
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SynthEngineTest {
    private val sr = 48_000

    private fun engine() = SynthEngine(sr, Tuning.STANDARD_BASS_4)

    /** 실제 오디오 콜백처럼 작은 청크로 나눠 렌더한다. */
    private fun render(engine: SynthEngine, frames: Int, chunk: Int = 192): FloatArray {
        val result = FloatArray(frames)
        val buf = FloatArray(chunk)
        var done = 0
        while (done < frames) {
            val n = minOf(chunk, frames - done)
            engine.render(buf, n)
            System.arraycopy(buf, 0, result, done, n)
            done += n
        }
        return result
    }

    private fun assertHz(expected: Float, actual: Float) {
        assertTrue(abs(actual - expected) / expected < 0.01f, "expected $expected Hz, measured $actual Hz")
    }

    @Test
    fun `without commands the output is exactly zero`() {
        val out = render(engine(), sr / 2)
        for (x in out) assertEquals(0f, x)
    }

    @Test
    fun `render overwrites whatever was in the buffer`() {
        val e = engine()
        val buf = FloatArray(256) { 0.7f }
        e.render(buf, 256)
        for (x in buf) assertEquals(0f, x)
    }

    @Test
    fun `NoteOn makes sound at the pitch of that string and fret`() {
        val e = engine()
        e.send(Command.NoteOn(string = 1, fret = 5)) // A줄 5프렛 = D2
        val out = render(e, sr)
        assertTrue(SignalAnalysis.rms(out, 0, sr / 10) > 0.02f)
        assertHz(Pitch.hz(38), SignalAnalysis.estimateHz(out, sr, Pitch.hz(38), from = 4000, length = 24_000))
    }

    @Test
    fun `Slide moves the pitch without a new pluck`() {
        val e = engine()
        e.send(Command.NoteOn(1, 5))
        val before = render(e, sr / 2)
        e.send(Command.Slide(1, 7))
        val after = render(e, sr)
        assertHz(Pitch.hz(40), SignalAnalysis.estimateHz(after, sr, Pitch.hz(40), from = 2000, length = 24_000))
        assertTrue(SignalAnalysis.rms(after, 0, sr / 10) <= SignalAnalysis.rms(before, sr / 2 - sr / 10, sr / 2) * 1.2f)
    }

    @Test
    fun `output stays inside minus one to one with every string ringing`() {
        val e = engine()
        for (s in 0..3) e.send(Command.NoteOn(s, 0))
        val out = render(e, sr / 2)
        assertTrue(SignalAnalysis.peak(out) < 1f)
        assertTrue(SignalAnalysis.peak(out) > 0.1f)
    }

    @Test
    fun `AllNotesOff reaches exact silence quickly`() {
        val e = engine()
        for (s in 0..3) e.send(Command.NoteOn(s, 3))
        render(e, sr / 4)
        e.send(Command.AllNotesOff)
        val tail = render(e, sr / 5)
        assertEquals(0f, SignalAnalysis.peak(tail, sr / 20, tail.size), "must be silent 50 ms after AllNotesOff")
    }

    @Test
    fun `master gain scales the output and zero mutes it`() {
        fun level(gain: Float): Float {
            val e = engine()
            e.send(Command.SetMasterGain(gain))
            e.send(Command.NoteOn(0, 0))
            return SignalAnalysis.rms(render(e, sr / 4))
        }
        val quiet = level(0.2f)
        val loud = level(1f)
        assertEquals(0f, level(0f))
        assertTrue(quiet > 0f && loud > quiet * 3f, "quiet=$quiet loud=$loud")
    }

    @Test
    fun `commands off the fretboard are ignored without crashing`() {
        val e = engine()
        e.send(Command.NoteOn(4, 0))
        e.send(Command.NoteOn(-1, 0))
        e.send(Command.NoteOn(0, 25))
        e.send(Command.NoteOn(0, -1))
        e.send(Command.Slide(7, 99))
        val out = render(e, 4800)
        assertEquals(0f, SignalAnalysis.peak(out))
    }

    @Test
    fun `commands beyond the queue capacity are dropped and counted`() {
        val e = engine()
        var rejected = 0
        repeat(300) { if (!e.send(Command.NoteOn(0, it % 25))) rejected++ }
        assertEquals(300 - 256, rejected)
        assertEquals(rejected, e.stats.droppedCommands)
        render(e, 512) // 큐를 비운다
        assertTrue(e.send(Command.NoteOn(0, 0)))
        assertEquals(rejected, e.stats.droppedCommands)
    }
}
