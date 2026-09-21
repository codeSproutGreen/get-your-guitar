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

    // ---- 벤딩 ----

    /** 커맨드를 순서대로 보낸 뒤 1초를 렌더해 기본 주파수가 기대값(±1%)인지 본다. */
    private fun assertPitch(expectedHz: Float, vararg commands: Command) {
        val e = engine()
        for (c in commands) e.send(c)
        val out = render(e, sr)
        assertHz(expectedHz, SignalAnalysis.estimateHz(out, sr, expectedHz, from = 4000, length = 24_000))
    }

    @Test
    fun `bending 100 cents sounds one fret higher`() {
        assertPitch(Pitch.hz(38 + 1), Command.NoteOn(1, 5), Command.Bend(1, 100f))
    }

    @Test
    fun `bending 200 cents sounds two frets higher`() {
        assertPitch(Pitch.hz(38 + 2), Command.NoteOn(1, 5), Command.Bend(1, 200f))
    }

    @Test
    fun `bend is continuous so 50 cents lands between two frets`() {
        val quarterToneUp = Pitch.hz(38) * Math.pow(2.0, 50.0 / 1200.0).toFloat()
        assertPitch(quarterToneUp, Command.NoteOn(1, 5), Command.Bend(1, 50f))
    }

    @Test
    fun `releasing the bend returns to the fretted pitch`() {
        assertPitch(Pitch.hz(38), Command.NoteOn(1, 5), Command.Bend(1, 200f), Command.Bend(1, 0f))
    }

    @Test
    fun `a new pluck starts unbent`() {
        assertPitch(Pitch.hz(38), Command.NoteOn(1, 5), Command.Bend(1, 200f), Command.NoteOn(1, 5))
    }

    @Test
    fun `sliding while bent keeps the bend`() {
        assertPitch(Pitch.hz(40 + 1), Command.NoteOn(1, 5), Command.Bend(1, 100f), Command.Slide(1, 7))
    }

    @Test
    fun `bend only affects its own string`() {
        assertPitch(Pitch.hz(28 + 3), Command.NoteOn(0, 3), Command.Bend(1, 200f))
    }

    @Test
    fun `bend is clamped to zero through the maximum`() {
        val max = SynthEngine.MAX_BEND_CENTS
        assertEquals(400f, max)
        assertPitch(Pitch.hz(38 + 4), Command.NoteOn(1, 5), Command.Bend(1, 5000f))
        assertPitch(Pitch.hz(38), Command.NoteOn(1, 5), Command.Bend(1, -300f))
    }

    @Test
    fun `bending a silent or nonexistent string makes no sound and does not crash`() {
        val e = engine()
        e.send(Command.Bend(2, 150f))
        e.send(Command.Bend(9, 150f))
        e.send(Command.Bend(-1, 150f))
        e.send(Command.Bend(0, Float.NaN))
        assertEquals(0f, SignalAnalysis.peak(render(e, 4800)))
    }

    @Test
    fun `a continuous bend sweep stays free of clicks`() {
        val e = engine()
        e.send(Command.NoteOn(1, 5))
        val steady = render(e, sr / 4)
        val steadyStep = SignalAnalysis.maxStep(steady, sr / 8, sr / 4)
        // 터치 이벤트처럼 약 8 ms마다 조금씩 올렸다가 내린다.
        val pieces = ArrayList<FloatArray>()
        for (i in 0..40) {
            val cents = if (i <= 20) i * 10f else (40 - i) * 10f
            e.send(Command.Bend(1, cents))
            pieces += render(e, 384)
        }
        val sweep = FloatArray(pieces.sumOf { it.size })
        var at = 0
        for (piece in pieces) { System.arraycopy(piece, 0, sweep, at, piece.size); at += piece.size }
        assertTrue(SignalAnalysis.maxStep(sweep) < steadyStep * 2f, "bend sweep clicked")
    }

    // ---- NoteOff ----

    @Test
    fun `NoteOff stops only its own string`() {
        val e = engine()
        e.send(Command.NoteOn(0, 3))
        e.send(Command.NoteOn(2, 5))
        render(e, sr / 10)
        e.send(Command.NoteOff(0))
        render(e, sr / 10)
        val rest = render(e, sr / 2)
        assertTrue(SignalAnalysis.rms(rest) > 0.01f, "the other string must keep ringing")
        assertHz(Pitch.hz(38 + 5), SignalAnalysis.estimateHz(rest, sr, Pitch.hz(43), from = 0, length = 16_000))

        e.send(Command.NoteOff(2))
        render(e, sr / 10)
        assertEquals(0f, SignalAnalysis.peak(render(e, 4800)))
    }

    @Test
    fun `NoteOff for a nonexistent or silent string is ignored`() {
        val e = engine()
        e.send(Command.NoteOff(1))
        e.send(Command.NoteOff(7))
        e.send(Command.NoteOff(-2))
        assertEquals(0f, SignalAnalysis.peak(render(e, 4800)))
    }

    @Test
    fun `a bend left on a released string does not leak into the next pluck`() {
        assertPitch(Pitch.hz(38), Command.NoteOn(1, 5), Command.Bend(1, 200f), Command.NoteOff(1), Command.NoteOn(1, 5))
    }

    // ---- 음색 커맨드 ----

    private fun hiss(x: FloatArray): Float {
        val diff = FloatArray(x.size - 1) { x[it + 1] - x[it] }
        return SignalAnalysis.rms(diff) / SignalAnalysis.rms(x)
    }

    @Test
    fun `SetBrightness and SetDecay reach the voices`() {
        fun play(vararg setup: Command): FloatArray {
            val e = engine()
            for (c in setup) e.send(c)
            e.send(Command.NoteOn(1, 7))
            return render(e, sr * 3 / 2)
        }
        assertTrue(hiss(play(Command.SetBrightness(1f))) > hiss(play(Command.SetBrightness(0f))) * 1.3f)
        val long = SignalAnalysis.rms(play(Command.SetDecay(1f)), sr, sr + sr / 10)
        val short = SignalAnalysis.rms(play(Command.SetDecay(0f)), sr, sr + sr / 10)
        // A줄 7프렛(82 Hz)은 1초에 루프를 82번만 돈다: 0.9995^82 / 0.990^82 ≈ 2.2배가 물리적 한계.
        assertTrue(long > short * 2f, "long=$long short=$short")
    }

    @Test
    fun `tone commands clamp out of range values instead of breaking the sound`() {
        val e = engine()
        e.send(Command.SetBrightness(40f))
        e.send(Command.SetDecay(-3f))
        e.send(Command.SetBrightness(Float.NaN))
        e.send(Command.NoteOn(0, 0))
        val out = render(e, sr / 4)
        assertTrue(SignalAnalysis.peak(out) > 0.05f && SignalAnalysis.peak(out) < 1f)
        for (x in out) assertTrue(!x.isNaN())
    }
}
