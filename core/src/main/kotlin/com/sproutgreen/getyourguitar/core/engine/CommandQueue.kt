package com.sproutgreen.getyourguitar.core.engine

import java.util.concurrent.atomic.AtomicInteger

/**
 * 단일 생산자(UI 스레드) · 단일 소비자(오디오 스레드) 링버퍼. 락 없음, 소비 경로 할당 없음.
 *
 * head·tail은 계속 증가하는 카운터이고 인덱스는 마스크로 구한다. 생산자는 슬롯을 다 쓴 뒤에
 * tail을 올리므로(AtomicInteger.set = volatile 쓰기) 소비자는 완성된 슬롯만 본다.
 */
class CommandQueue(capacity: Int = DEFAULT_CAPACITY) {
    val capacity: Int = roundUpToPowerOfTwo(capacity)
    private val mask = this.capacity - 1
    private val slots = IntArray(this.capacity * SLOT_SIZE)
    private val head = AtomicInteger(0)
    private val tail = AtomicInteger(0)

    /** 생산자 전용. 가득 차 있으면 아무것도 쓰지 않고 false. */
    fun offer(cmd: Command): Boolean {
        val t = tail.get()
        if (t - head.get() >= capacity) return false
        val base = (t and mask) * SLOT_SIZE
        slots[base] = CommandCodec.type(cmd)
        slots[base + 1] = CommandCodec.argA(cmd)
        slots[base + 2] = CommandCodec.argB(cmd)
        slots[base + 3] = CommandCodec.floatBits(cmd)
        tail.set(t + 1)
        return true
    }

    /** 소비자 전용. 다음 커맨드를 [slot](크기 [SLOT_SIZE])에 복사한다. 비어 있으면 false. */
    fun poll(slot: IntArray): Boolean {
        val h = head.get()
        if (h == tail.get()) return false
        val base = (h and mask) * SLOT_SIZE
        slot[0] = slots[base]
        slot[1] = slots[base + 1]
        slot[2] = slots[base + 2]
        slot[3] = slots[base + 3]
        head.set(h + 1)
        return true
    }

    companion object {
        const val SLOT_SIZE = 4
        const val DEFAULT_CAPACITY = 256

        private fun roundUpToPowerOfTwo(n: Int): Int {
            require(n in 1..(1 shl 20)) { "capacity $n out of range" }
            var p = 1
            while (p < n) p = p shl 1
            return p
        }
    }
}
