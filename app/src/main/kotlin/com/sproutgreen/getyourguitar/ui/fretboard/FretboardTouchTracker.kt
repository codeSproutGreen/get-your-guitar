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
 * | move, 세로로 (벤딩이 걸린 뒤) | Bend — 기준점에서 벗어난 거리만큼 음이 올라간다(위·아래 같음) |
 * | move, 다른 줄 진입 (벤딩 시작 전) | 새 줄에 NoteOn(레이크). 이전 줄도 이 손가락이 계속 쥐고 있다 |
 * | move, 다른 줄 진입 (벤딩 시작 후) | 무시 — 손을 뗄 때까지 짚은 줄에 고정. 벤딩은 최대에서 유지 |
 * | move, 같은 셀·데드존 안 | 무시 |
 * | up / cancel | 아래에 다른 손가락이 있으면 풀오프. 없으면: [muting]일 때 NoteOff, 아니면 벤딩만 풀고 자연 감쇠 |
 * | 뮤트 바 누름 ([setMute]) | 손가락이 떠난 채 울리던 줄을 전부 NoteOff. 아직 누르고 있는 음은 그대로 |
 *
 * **뮤트 바(요청 2026-09-21).** 지판 아래의 긴 버튼을 누르고 있는 동안([muting])은 음이 손가락으로 누르고 있을
 * 때만 나고, 떼면 멈춘다. 안 누르고 있으면 떼도 자연 감쇠한다. 처음에는 설정의 스위치였는데, 연주 중에 손으로
 * 바로 오갈 수 있어야 해서 버튼이 됐다 — 실제 베이스의 팜 뮤트와 같은 역할이다.
 *
 * **소유권.** 줄마다 누르고 있는 손가락을 쌓아 두고 맨 위(가장 나중에 누른) 손가락이 그 줄의 음을 소유한다.
 * - 소유자만 Slide·Bend를 보낸다. 아래에 깔린 손가락은 조용히 움직인다.
 * - 소유자가 떼면: 아래에 다른 손가락이 있으면 그 프렛으로 돌아간다(풀오프, 뮤트와 무관). 없으면 위 표대로.
 *   → 한 손가락으로 누른 채 다른 손가락으로 같은 줄을 치면 해머온, 떼면 풀오프가 된다.
 * - 소유자가 아닌 손가락을 떼면 아무 일도 없다.
 * - 레이크로 훑은 줄은 전부 그 손가락의 것이고, (뮤트 중이면) 떼면 한꺼번에 멈춘다. 지나가자마자 멈추면 화음을 쌓을 수 없다.
 *
 * **벤딩과 레이크는 "어디까지 밀었나"가 아니라 "어떻게 시작했나"로 갈린다.** 짚고 나서 밀면 벤딩,
 * 짚자마자 훑으면 레이크. 벤딩은 down으로 짚은 줄에서만, 터치 후 [BEND_ARM_MS]가 지나야 걸린다
 * (빠른 레이크가 첫 줄을 휘지 않게). 걸리는 순간의 기준점은 직전 이벤트의 손가락 위치다 — down 위치를 쓰면
 * 그동안 움직인 만큼 음이 튄다. 0이 아닌 Bend를 한 번이라도 보낸 포인터는 그 줄에 고정한다: 처음에는 밴드 경계를
 * 넘으면 레이크로 바꿨는데, 최대 벤딩 지점이 곧 경계라서 가장 세게 미는 순간 풀려 버렸다(실기기 피드백).
 * 대가: 한 줄에 80 ms 넘게 머무는 느린 레이크는 벤딩으로 해석된다.
 *
 * 세로 위치는 밴드 좌표([FretboardGeometry.bandCoordinate])로 받는다: 1.0 = 줄 밴드 하나의 높이.
 */
class FretboardTouchTracker(
    private val send: (Command) -> Unit,
    private val onSounded: (string: Int, fret: Int) -> Unit = { _, _ -> },
    /** 줄을 휘어 그리기 위한 콜백. displacement는 밴드 단위 부호 있는 값(±[MAX_VISUAL_BANDS]로 제한), 0 = 원위치. */
    private val onBend: (string: Int, displacementBands: Float) -> Unit = { _, _ -> },
    /** 뮤트로 줄이 멈췄다. 하이라이트를 끄는 데 쓴다. */
    private val onReleased: (string: Int) -> Unit = {},
    maxBendCents: Float = DEFAULT_MAX_BEND_CENTS,
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    /** 뮤트 바를 누르고 있는가. true인 동안은 손가락을 떼면 그 음이 멈춘다. [setMute]로만 바꾼다. */
    var muting: Boolean = false
        private set

    /** NoteOn을 보낸 뒤 아직 NoteOff를 보내지 않은 줄. 뮤트를 누를 때 끊을 후보다(이미 자연 감쇠로 죽었어도 무해하다). */
    private val ringing = HashSet<Int>()

    /** 최대 벤딩 폭. 200 = 온음, 400 = 두 온음. 설정에서 바꾼다. */
    var maxBendCents: Float = maxBendCents

    private class Pointer(var string: Int, bandY: Float, val downMs: Long) {
        /** 이 손가락이 쥐고 있는 줄 → 그 줄에서의 프렛. 레이크로 여러 줄을 쥘 수 있다. */
        val frets = LinkedHashMap<Int, Int>()
        var bendable = true
        var armed = false

        /** 0이 아닌 Bend를 보낸 적이 있다 = 이 손가락은 벤딩 중. 손을 뗄 때까지 [string]에 고정. */
        var locked = false
        var anchorBandY = bandY
        var lastBandY = bandY
        var sentCents = 0f
    }

    private val pointers = HashMap<Long, Pointer>()

    /** 줄 → 누르고 있는 손가락들(누른 순서). 마지막이 소유자. */
    private val holders = HashMap<Int, ArrayList<Pointer>>()

    /** 뮤트 바를 눌렀다/뗐다. 누르는 순간, 아무 손가락도 쥐고 있지 않은 채 울리던 줄을 끊는다. */
    fun setMute(pressed: Boolean) {
        if (pressed == muting) return
        muting = pressed
        if (!pressed) return
        val strings = ringing.iterator()
        while (strings.hasNext()) {
            val string = strings.next()
            if (holders[string].isNullOrEmpty()) {
                strings.remove()
                send(Command.NoteOff(string))
                onReleased(string)
            }
        }
    }

    fun down(pointerId: Long, string: Int, fret: Int, bandY: Float) {
        val p = Pointer(string, bandY, clockMs())
        pointers[pointerId] = p
        hold(p, string, fret)
        pluck(string, fret)
    }

    private fun pluck(string: Int, fret: Int) {
        ringing.add(string)
        send(Command.NoteOn(string, fret))
        onSounded(string, fret)
    }

    fun move(pointerId: Long, string: Int, fret: Int, bandY: Float) {
        val p = pointers[pointerId] ?: return

        if (string != p.string && !p.locked) {
            releaseBend(p, sendZero = true)
            p.bendable = false
            p.string = string
            hold(p, string, fret)
            pluck(string, fret)
            return
        }

        val owns = owns(p, p.string)
        if (fret != p.frets[p.string]) {
            p.frets[p.string] = fret
            if (owns) {
                send(Command.Slide(p.string, fret))
                onSounded(p.string, fret)
            }
        }

        if (p.bendable && !p.armed && clockMs() - p.downMs >= BEND_ARM_MS) {
            p.armed = true
            p.anchorBandY = p.lastBandY
        }
        p.lastBandY = bandY

        if (p.bendable && p.armed && owns) {
            val displacement = bandY - p.anchorBandY
            onBend(p.string, displacement.coerceIn(-MAX_VISUAL_BANDS, MAX_VISUAL_BANDS))
            val cents = centsFor(abs(displacement))
            val reachedAnEnd = (cents == 0f || cents == maxBendCents) && cents != p.sentCents
            if (reachedAnEnd || abs(cents - p.sentCents) >= MIN_CENTS_STEP) {
                p.sentCents = cents
                if (cents > 0f) p.locked = true
                send(Command.Bend(p.string, cents))
            }
        }
    }

    fun up(pointerId: Long) {
        val p = pointers.remove(pointerId) ?: return
        lift(p)
    }

    fun cancelAll() {
        val all = ArrayList(pointers.values)
        pointers.clear()
        for (p in all) lift(p)
    }

    /** 줄 [string]의 맨 위에 [p]를 올린다. 이미 쥐고 있던 줄이면 맨 위로 옮기기만 한다. */
    private fun hold(p: Pointer, string: Int, fret: Int) {
        p.frets[string] = fret
        val stack = holders.getOrPut(string) { ArrayList() }
        stack.remove(p)
        stack.add(p)
    }

    private fun owns(p: Pointer, string: Int): Boolean = holders[string]?.lastOrNull() === p

    private fun lift(p: Pointer) {
        for (string in p.frets.keys) {
            val stack = holders[string] ?: continue
            val wasOwner = stack.lastOrNull() === p
            stack.remove(p)
            if (!wasOwner) continue

            val next = stack.lastOrNull()
            if (next != null) {
                // 풀오프: 아래 깔려 있던 손가락의 프렛으로. 벤딩돼 있었으면 먼저 편다.
                if (string == p.string) releaseBend(p, sendZero = true)
                val fret = next.frets.getValue(string)
                send(Command.Slide(string, fret))
                onSounded(string, fret)
            } else if (muting) {
                // 음이 멈춘다. Bend 0을 먼저 보내면 꺼지는 동안 음높이가 툭 떨어지므로 보내지 않는다.
                // 엔진은 다음 NoteOn에서 벤딩을 0으로 되돌린다.
                if (string == p.string) releaseBend(p, sendZero = false)
                ringing.remove(string)
                send(Command.NoteOff(string))
                onReleased(string)
            } else {
                // 뮤트를 누르지 않았다: 줄은 계속 울린다. 벤딩만 원래 음으로 돌린다.
                if (string == p.string) releaseBend(p, sendZero = true)
            }
        }
    }

    private fun releaseBend(p: Pointer, sendZero: Boolean) {
        if (!p.bendable || !p.armed) return
        if (p.sentCents != 0f) {
            p.sentCents = 0f
            if (sendZero) send(Command.Bend(p.string, 0f))
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

        /** 휘는 줄 표시가 손가락을 따라가는 한계. 음은 [MAX_TRAVEL_BANDS]에서 이미 최대다. */
        const val MAX_VISUAL_BANDS = 1.5f

        const val MIN_CENTS_STEP = 1f

        /** 터치 후 이 시간이 지나야 벤딩이 걸린다. 빠른 레이크가 첫 줄을 휘지 않게 하는 문턱. */
        const val BEND_ARM_MS = 80L
    }
}
