package com.sproutgreen.getyourguitar.ui.fretboard

import com.sproutgreen.getyourguitar.core.engine.Command
import kotlin.math.abs

/**
 * 포인터별 상태를 기억해 터치 이벤트를 커맨드로 바꾼다. Android 의존 없음.
 *
 * | 이벤트 | 동작 |
 * |---|---|
 * | down | NoteOn |
 * | move, 같은 줄·다른 프렛 | Slide |
 * | move, 같은 줄 밴드 안에서 세로로 | Bend — down 지점에서 벗어난 거리만큼 음이 올라간다(위·아래 같음) |
 * | move, 다른 줄 진입 | 원래 줄의 벤딩을 풀고 새 줄에 NoteOn(레이크). 이전 줄은 계속 감쇠 |
 * | move, 같은 셀·데드존 안 | 무시 |
 * | up / cancel | 벤딩 중이었으면 Bend 0, 아니면 아무것도 보내지 않음(자연 감쇠) |
 *
 * 벤딩은 **down으로 짚은 줄에서만** 된다. 레이크로 넘어간 줄은 벤딩하지 않는다 — 그러지 않으면
 * 줄을 훑을 때마다 지나가는 줄의 음이 휘어 올라간다.
 *
 * 같은 이유로 벤딩은 **터치 후 [BEND_ARM_MS]가 지나야 걸린다.** 레이크는 첫 줄의 밴드를 수십 ms 만에
 * 지나가지만 벤딩은 "튕긴 다음에 민다". 걸리는 순간의 기준점은 직전 이벤트의 손가락 위치다 —
 * down 위치를 그대로 쓰면 그동안 움직인 만큼 음이 한 번에 튄다.
 *
 * 세로 위치는 밴드 좌표([FretboardGeometry.bandCoordinate])로 받는다: 1.0 = 줄 밴드 하나의 높이.
 * 같은 줄에 두 포인터가 있으면 둘 다 보내고, 엔진에서 마지막 커맨드가 이긴다.
 */
class FretboardTouchTracker(
    private val send: (Command) -> Unit,
    private val onSounded: (string: Int, fret: Int) -> Unit = { _, _ -> },
    /** 줄을 휘어 그리기 위한 콜백. displacement는 밴드 단위 부호 있는 값(±0.5로 제한), 0 = 원위치. */
    private val onBend: (string: Int, displacementBands: Float) -> Unit = { _, _ -> },
    private val maxBendCents: Float = DEFAULT_MAX_BEND_CENTS,
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private class Pointer(var string: Int, var fret: Int, bandY: Float, val downMs: Long) {
        var bendable = true
        var armed = false
        var anchorBandY = bandY
        var lastBandY = bandY
        var sentCents = 0f
    }

    private val pointers = HashMap<Long, Pointer>()

    fun down(pointerId: Long, string: Int, fret: Int, bandY: Float) {
        pointers[pointerId] = Pointer(string, fret, bandY, clockMs())
        send(Command.NoteOn(string, fret))
        onSounded(string, fret)
    }

    fun move(pointerId: Long, string: Int, fret: Int, bandY: Float) {
        val p = pointers[pointerId] ?: return

        if (string != p.string) {
            releaseBend(p)
            p.bendable = false
            p.string = string
            p.fret = fret
            send(Command.NoteOn(string, fret))
            onSounded(string, fret)
            return
        }

        if (fret != p.fret) {
            p.fret = fret
            send(Command.Slide(string, fret))
            onSounded(string, fret)
        }

        if (p.bendable && !p.armed && clockMs() - p.downMs >= BEND_ARM_MS) {
            p.armed = true
            p.anchorBandY = p.lastBandY
        }
        p.lastBandY = bandY

        if (p.bendable && p.armed) {
            val displacement = bandY - p.anchorBandY
            onBend(p.string, displacement.coerceIn(-MAX_TRAVEL_BANDS, MAX_TRAVEL_BANDS))
            val cents = centsFor(abs(displacement))
            val reachedAnEnd = (cents == 0f || cents == maxBendCents) && cents != p.sentCents
            if (reachedAnEnd || abs(cents - p.sentCents) >= MIN_CENTS_STEP) {
                p.sentCents = cents
                send(Command.Bend(p.string, cents))
            }
        }
    }

    fun up(pointerId: Long) {
        val p = pointers.remove(pointerId) ?: return
        releaseBend(p)
    }

    fun cancelAll() {
        for (p in pointers.values) releaseBend(p)
        pointers.clear()
    }

    private fun releaseBend(p: Pointer) {
        if (!p.bendable || !p.armed) return
        if (p.sentCents != 0f) {
            p.sentCents = 0f
            send(Command.Bend(p.string, 0f))
        }
        onBend(p.string, 0f)
    }

    private fun centsFor(travelBands: Float): Float {
        val t = ((travelBands - DEAD_ZONE_BANDS) / (MAX_TRAVEL_BANDS - DEAD_ZONE_BANDS)).coerceIn(0f, 1f)
        return t * maxBendCents
    }

    companion object {
        /** 온음. 스펙의 "최대 1~2음" 중 기본값. */
        const val DEFAULT_MAX_BEND_CENTS = 200f

        /** 손떨림으로 음이 흔들리지 않게 하는 구간. S10e에서 약 1 mm. */
        const val DEAD_ZONE_BANDS = 0.1f

        /** 이만큼 밀면 최대 벤딩. 밴드 중앙을 짚었을 때 옆 줄 경계까지의 거리와 같다. */
        const val MAX_TRAVEL_BANDS = 0.5f

        const val MIN_CENTS_STEP = 1f

        /** 터치 후 이 시간이 지나야 벤딩이 걸린다. 빠른 레이크가 첫 줄을 휘지 않게 하는 문턱. */
        const val BEND_ARM_MS = 80L
    }
}
