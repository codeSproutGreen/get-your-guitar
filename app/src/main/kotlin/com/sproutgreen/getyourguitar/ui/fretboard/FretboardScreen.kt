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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

private val ScreenBackground = Color(0xFF100C09)
private val BarText = Color(0xFFD8CBBB)
private val ToggleOn = Color(0xFFFFB300)
private val ToggleOff = Color(0xFF3A2F27)
private val ErrorColor = Color(0xFFFF6B5E)

/** 상단 바 12% + 연주 영역 88%(띠 8 · 지판 72 · 띠 8). ⚙(설정)는 M2, 메트로놈은 M3에서 상단 바에 추가된다. */
@Composable
fun FretboardScreen(audio: AudioController, modifier: Modifier = Modifier) {
    val fretboard = remember { Fretboard(Tuning.STANDARD_BASS_4) }
    val state = remember { FretboardState() }
    val tracker = remember(audio, state) { FretboardTouchTracker(send = audio::send, onSounded = state::highlight, onBend = state::bend) }
    val scope = rememberCoroutineScope()
    var showNoteNames by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .windowInsetsPadding(WindowInsets.displayCutout),
    ) {
        TopBar(
            showNoteNames = showNoteNames,
            onToggleNoteNames = { showNoteNames = !showNoteNames },
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
            FretboardCanvas(state, fretboard, showNoteNames, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun TopBar(
    showNoteNames: Boolean,
    onToggleNoteNames: () -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
) {
    Row(modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .fillMaxHeight(0.72f)
                .clip(RoundedCornerShape(50))
                .background(if (showNoteNames) ToggleOn else ToggleOff)
                .clickable(onClick = onToggleNoteNames)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "♪ 음이름",
                color = if (showNoteNames) Color.Black else BarText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.width(16.dp))
        if (error != null) {
            Text(text = error, color = ErrorColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}
