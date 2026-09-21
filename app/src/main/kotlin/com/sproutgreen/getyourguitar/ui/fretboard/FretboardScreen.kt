package com.sproutgreen.getyourguitar.ui.fretboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sproutgreen.getyourguitar.audio.AudioController
import com.sproutgreen.getyourguitar.core.music.Fretboard
import com.sproutgreen.getyourguitar.core.music.Tuning
import com.sproutgreen.getyourguitar.data.Settings

private val ScreenBackground = Color(0xFF100C09)
private val BarText = Color(0xFFD8CBBB)
private val ToggleOn = Color(0xFFFFB300)
private val ToggleOff = Color(0xFF3A2F27)
private val ErrorColor = Color(0xFFFF6B5E)

/** 상단 바 12% + 연주 영역 88%(띠 8 · 지판 72 · 띠 8). 메트로놈은 나중에 상단 바 오른쪽에 들어온다. */
@Composable
fun FretboardScreen(
    audio: AudioController,
    settings: Settings,
    onToggleNoteNames: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fretboard = remember { Fretboard(Tuning.STANDARD_BASS_4) }
    val state = remember { FretboardState() }
    val tracker = remember(audio, state) {
        FretboardTouchTracker(
            send = audio::send,
            onSounded = state::highlight,
            onBend = state::bend,
            onReleased = state::release,
        )
    }
    val scope = rememberCoroutineScope()

    // 추적기와 상태는 컴포지션 밖의 평범한 객체라 설정이 바뀔 때마다 값을 밀어 넣는다.
    SideEffect {
        tracker.holdToSustain = settings.holdToSustain
        tracker.maxBendCents = settings.bendRangeCents.toFloat()
        state.holdToSustain = settings.holdToSustain
    }

    Column(
        modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .windowInsetsPadding(WindowInsets.displayCutout),
    ) {
        TopBar(
            showNoteNames = settings.showNoteNames,
            onToggleNoteNames = onToggleNoteNames,
            onOpenSettings = onOpenSettings,
            error = audio.error,
            modifier = Modifier
                .fillMaxWidth()
                .weight(12f),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .weight(88f)
                .clipToBounds() // 스크롤 중 화면 밖 셀의 마크·글자가 컷아웃 여백으로 삐져나오지 않게
                .fretboardGestures(state, tracker, scope),
        ) {
            FretboardCanvas(state, fretboard, settings.showNoteNames, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun TopBar(
    showNoteNames: Boolean,
    onToggleNoteNames: () -> Unit,
    onOpenSettings: () -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
) {
    Row(modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        BarButton(text = "⚙ 설정", highlighted = false, onClick = onOpenSettings)
        Spacer(Modifier.width(10.dp))
        BarButton(text = "♪ 음이름", highlighted = showNoteNames, onClick = onToggleNoteNames)
        Spacer(Modifier.width(16.dp))
        if (error != null) {
            Text(text = error, color = ErrorColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** 상단 바는 높이가 43dp쯤이라 Material 버튼(최소 터치 영역 48dp)이 들어가지 않는다. */
@Composable
private fun BarButton(text: String, highlighted: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxHeight(0.72f)
            .clip(RoundedCornerShape(50))
            .background(if (highlighted) ToggleOn else ToggleOff)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (highlighted) Color.Black else BarText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
