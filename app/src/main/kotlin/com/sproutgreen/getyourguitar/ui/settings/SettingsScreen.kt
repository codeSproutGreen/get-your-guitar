package com.sproutgreen.getyourguitar.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sproutgreen.getyourguitar.audio.AudioController
import com.sproutgreen.getyourguitar.core.engine.Command
import com.sproutgreen.getyourguitar.data.Settings
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val ScreenBackground = Color(0xFF100C09)
private val Panel = Color(0xFF1C1612)
private val TextMain = Color(0xFFE8DCCB)
private val TextDim = Color(0xFF9C8F80)
private val Accent = Color(0xFFFFB300)
private val Chip = Color(0xFF3A2F27)

/**
 * 가로 화면 2열: 왼쪽은 소리(슬라이더·시험음), 오른쪽은 연주 방식·표시·오디오·디버그.
 * 슬라이더는 끄는 동안 [onPreview](소리에만 반영), 손을 떼면 [onCommit](저장).
 */
@Composable
fun SettingsScreen(
    settings: Settings,
    audio: AudioController,
    onPreview: ((Settings) -> Settings) -> Unit,
    onCommit: ((Settings) -> Settings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(text = "← 지판", selected = false, onClick = onBack)
            Spacer(Modifier.width(14.dp))
            Text("설정", color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Section("소리") {
                    LabeledSlider("볼륨", settings.masterVolume, onPreview, onCommit) { s, v -> s.copy(masterVolume = v) }
                    LabeledSlider("밝기", settings.brightness, onPreview, onCommit) { s, v -> s.copy(brightness = v) }
                    LabeledSlider("감쇠", settings.decay, onPreview, onCommit) { s, v -> s.copy(decay = v) }
                    Text("감쇠가 클수록 누르고 있을 때 오래 울립니다", color = TextDim, fontSize = 11.sp)
                }
                Section("시험음 — 누르고 있으면 울립니다") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((string, name) in listOf(0 to "E", 1 to "A", 2 to "D", 3 to "G")) {
                            TestToneButton(name, string, settings.holdToSustain, audio, Modifier.weight(1f))
                        }
                    }
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Section("연주 · 표시") {
                    SwitchRow(
                        title = "누르고 있는 동안만 소리",
                        subtitle = if (settings.holdToSustain) "손을 떼면 음이 멈춥니다" else "손을 떼도 자연스럽게 잦아듭니다",
                        checked = settings.holdToSustain,
                        onChange = { on -> onCommit { it.copy(holdToSustain = on) } },
                    )
                    ChoiceRow(
                        title = "벤딩 폭",
                        options = listOf(200 to "1음", 400 to "2음"),
                        selected = settings.bendRangeCents,
                        onSelect = { cents -> onCommit { it.copy(bendRangeCents = cents) } },
                    )
                    SwitchRow(
                        title = "음이름 표시",
                        subtitle = null,
                        checked = settings.showNoteNames,
                        onChange = { on -> onCommit { it.copy(showNoteNames = on) } },
                    )
                }
                Section("오디오") {
                    ChoiceRow(
                        title = "버퍼 크기",
                        options = Settings.BUFFER_CHUNKS.map { it to "×$it" },
                        selected = settings.audioBufferChunks,
                        onSelect = { chunks -> onCommit { it.copy(audioBufferChunks = chunks) } },
                    )
                    DebugInfo(audio)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Panel)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    onPreview: ((Settings) -> Settings) -> Unit,
    onCommit: ((Settings) -> Settings) -> Unit,
    set: (Settings, Float) -> Settings,
) {
    // 세로 360dp 화면에 슬라이더 셋과 시험음이 한 번에 보여야 한다 → 라벨·슬라이더·값을 한 줄에.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextMain, fontSize = 13.sp, modifier = Modifier.width(44.dp))
        Slider(
            value = value,
            onValueChange = { v -> onPreview { set(it, v) } },
            onValueChangeFinished = { onCommit { it } },
            modifier = Modifier
                .weight(1f)
                .height(34.dp),
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Chip),
        )
        Text(
            "${(value * 100).roundToInt()}",
            color = TextDim,
            fontSize = 13.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(30.dp),
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextMain, fontSize = 13.sp)
            if (subtitle != null) Text(subtitle, color = TextDim, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color.Black, uncheckedTrackColor = Chip),
        )
    }
}

@Composable
private fun <T> ChoiceRow(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = TextMain, fontSize = 13.sp, modifier = Modifier.weight(1f))
        for ((value, label) in options) Pill(text = label, selected = value == selected, onClick = { onSelect(value) })
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Accent else Chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) Color.Black else TextMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** 개방현 시험음. 누르면 NoteOn, 떼면 (누르는 동안만 소리 모드에서) NoteOff — 지판과 같은 느낌으로 설정을 들어 볼 수 있다. */
@Composable
private fun TestToneButton(name: String, string: Int, holdToSustain: Boolean, audio: AudioController, modifier: Modifier = Modifier) {
    val hold by rememberUpdatedState(holdToSustain)
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (pressed) Accent else Chip)
            .pointerInput(string) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        audio.send(Command.NoteOn(string, 0))
                        tryAwaitRelease()
                        if (hold) audio.send(Command.NoteOff(string))
                        pressed = false
                    },
                )
            }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(name, color = if (pressed) Color.Black else TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DebugInfo(audio: AudioController) {
    var info by remember { mutableStateOf(audio.debugInfo()) }
    LaunchedEffect(audio) {
        while (true) {
            info = audio.debugInfo()
            delay(1_000)
        }
    }
    val bufferFrames = info.framesPerBuffer * info.bufferChunks
    val bufferMs = bufferFrames * 1000f / info.sampleRate
    Text(
        text = "${info.sampleRate} Hz · 버스트 ${info.framesPerBuffer} · 버퍼 $bufferFrames 프레임 (${"%.1f".format(bufferMs)} ms)\n" +
            "언더런 ${info.underruns} · 버린 커맨드 ${info.droppedCommands} · ${if (info.running) "실행 중" else "정지"}",
        color = TextDim,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 15.sp,
    )
}
