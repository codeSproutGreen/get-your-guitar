package com.sproutgreen.getyourguitar.ui.fretboard

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.CoroutineScope

/**
 * 연주 영역(스크롤 띠 + 지판 + 뮤트 바) 전체에 거는 제스처.
 * 포인터마다 down 위치로 역할이 정해진다: 지판에서 시작하면 연주, 위 띠면 스크롤, 아래 바면 뮤트.
 * 역할은 손을 뗄 때까지 바뀌지 않는다 — 벤딩하다 뮤트 바 위로 넘어가도 뮤트가 걸리지 않는다.
 * 연주 포인터는 세로로 지판을 벗어나도 가장자리 줄에 붙은 채 연주를 계속한다.
 * 세로 이동은 추적기가 벤딩(같은 줄 밴드 안) 또는 레이크(다른 줄 진입)로 해석한다.
 */
fun Modifier.fretboardGestures(
    state: FretboardState,
    tracker: FretboardTouchTracker,
    scope: CoroutineScope,
): Modifier = pointerInput(state, tracker) {
    val scrollPointers = HashSet<Long>()
    val mutePointers = HashSet<Long>()
    try {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val geometry = FretboardGeometry(size.width.toFloat(), size.height.toFloat())
                for (change in event.changes) {
                    val id = change.id.value
                    val x = change.position.x
                    val y = change.position.y
                    state.touchX = x
                    when {
                        change.changedToDown() -> {
                            if (geometry.isOnBoard(y)) {
                                // NoteOn은 down 이벤트에서 바로 보낸다. 지연을 더하는 탭 판정(슬롭·타임아웃)을 거치지 않는다.
                                tracker.down(
                                    id,
                                    geometry.stringAt(y),
                                    geometry.fretAt(x, geometry.layout(state.scroll)),
                                    geometry.bandCoordinate(y),
                                )
                            } else if (geometry.isOnMuteBar(y)) {
                                mutePointers.add(id)
                                tracker.setMute(true)
                                state.mutePressed = true
                            } else {
                                scrollPointers.add(id)
                                state.beginDrag()
                            }
                        }

                        change.changedToUp() -> {
                            if (scrollPointers.remove(id)) {
                                if (scrollPointers.isEmpty()) state.settle(scope, geometry)
                            } else if (mutePointers.remove(id)) {
                                // 손가락 둘로 바를 누르고 있었다면 마지막 하나를 뗄 때 풀린다.
                                if (mutePointers.isEmpty()) {
                                    tracker.setMute(false)
                                    state.mutePressed = false
                                }
                            } else {
                                tracker.up(id)
                            }
                        }

                        change.pressed -> {
                            if (id in scrollPointers) {
                                state.dragBy(-change.positionChange().x / geometry.cellWidth, geometry)
                            } else if (id in mutePointers) {
                                // 뮤트 바 위에서 움직이는 건 의미 없다.
                            } else if (change.positionChange().x != 0f || change.positionChange().y != 0f) {
                                tracker.move(
                                    id,
                                    geometry.stringAt(y),
                                    geometry.fretAt(x, geometry.layout(state.scroll)),
                                    geometry.bandCoordinate(y),
                                )
                            }
                        }
                    }
                    change.consume()
                }
            }
        }
    } finally {
        tracker.cancelAll()
        tracker.setMute(false)
        state.mutePressed = false
    }
}
