package com.sproutgreen.getyourguitar.core.engine

import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandQueueTest {
    private val slot = IntArray(CommandQueue.SLOT_SIZE)

    private fun CommandQueue.pollCommand(): Command? = if (poll(slot)) CommandCodec.decode(slot) else null

    @Test
    fun `empty queue polls nothing`() {
        val q = CommandQueue()
        assertFalse(q.poll(slot))
    }

    @Test
    fun `commands come out in the order they went in`() {
        val q = CommandQueue()
        val sent = listOf(
            Command.NoteOn(0, 3),
            Command.Slide(0, 5),
            Command.NoteOn(3, 24),
            Command.AllNotesOff,
            Command.SetMasterGain(0.35f),
            Command.Bend(2, 137.5f),
            Command.NoteOff(3),
            Command.SetBrightness(0.25f),
            Command.SetDecay(0.9f),
        )
        for (c in sent) assertTrue(q.offer(c))
        val received = generateSequence { q.pollCommand() }.toList()
        assertEquals(sent, received)
    }

    @Test
    fun `float arguments survive bit-exactly`() {
        val q = CommandQueue()
        for (f in floatArrayOf(0f, 1f, 0.1f, 0.8f, Float.MIN_VALUE, 123.456f)) {
            q.offer(Command.SetMasterGain(f))
            assertEquals(Command.SetMasterGain(f), q.pollCommand())
        }
    }

    @Test
    fun `wraps around many times without losing order`() {
        val q = CommandQueue(capacity = 8)
        var next = 0
        var expected = 0
        repeat(1000) {
            val batch = 1 + it % 7
            repeat(batch) { assertTrue(q.offer(Command.NoteOn(next % 4, next % 25))); next++ }
            repeat(batch) {
                assertEquals(Command.NoteOn(expected % 4, expected % 25), q.pollCommand())
                expected++
            }
        }
        assertFalse(q.poll(slot))
    }

    @Test
    fun `offer fails when full and succeeds again after a poll`() {
        val q = CommandQueue(capacity = 256)
        repeat(256) { assertTrue(q.offer(Command.NoteOn(0, it % 25)), "offer $it") }
        assertFalse(q.offer(Command.NoteOn(1, 1)), "257th must be rejected")
        assertEquals(Command.NoteOn(0, 0), q.pollCommand())
        assertTrue(q.offer(Command.NoteOn(2, 2)))
        // 거부된 커맨드는 큐에 흔적을 남기지 않는다
        val rest = generateSequence { q.pollCommand() }.toList()
        assertEquals(256, rest.size)
        assertEquals(Command.NoteOn(2, 2), rest.last())
        assertFalse(rest.contains(Command.NoteOn(1, 1)))
    }

    @Test
    fun `capacity is rounded up to a power of two`() {
        val q = CommandQueue(capacity = 5)
        assertEquals(8, q.capacity)
    }

    @Test
    fun `one producer and one consumer thread keep order under load`() {
        val q = CommandQueue(capacity = 64)
        val total = 200_000
        var disorder = 0
        var received = 0
        val consumer = thread {
            val s = IntArray(CommandQueue.SLOT_SIZE)
            while (received < total) {
                if (q.poll(s)) {
                    val value = s[1] * 25 + s[2]
                    if (value != received % 100) disorder++
                    received++
                }
            }
        }
        var sent = 0
        while (sent < total) {
            val v = sent % 100
            if (q.offer(Command.NoteOn(v / 25, v % 25))) sent++
        }
        consumer.join(20_000)
        assertEquals(total, received)
        assertEquals(0, disorder)
    }
}
