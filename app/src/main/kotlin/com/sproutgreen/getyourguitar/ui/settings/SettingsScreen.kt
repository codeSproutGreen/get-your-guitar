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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sproutgreen.getyourguitar.audio.AudioController
import com.sproutgreen.getyourguitar.core.engine.Command
import com.sproutgreen.getyourguitar.data.FretLayoutKind
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
                    UnitSlider("볼륨", settings.masterVolume, onPreview, onCommit) { s, v -> s.copy(masterVolume = v) }
                    UnitSlider("밝기", settings.brightness, onPreview, onCommit) { s, v -> s.copy(brightness = v) }
                    UnitSlider("감쇠", settings.decay, onPreview, onCommit) { s, v -> s.copy(decay = v) }
                    Text("클수록 오래 울립니다. 일찍 끊으려면 지판 아래의 뮤트 바", color = TextDim, fontSize = 11.sp)
                }
                Section("시험음 (개방현)") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((string, name) in listOf(0 to "E", 1 to "A", 2 to "D", 3 to "G")) {
                            TestToneButton(name, string, audio, Modifier.weight(1f))
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
                    LabeledSlider(
                        label = "레이크",
                        value = settings.rakeSettleMs.toFloat(),
                        range = Settings.MIN_RAKE_SETTLE_MS.toFloat()..Settings.MAX_RAKE_SETTLE_MS.toFloat(),
                        valueText = "${settings.rakeSettleMs} ms",
                        labelWidth = 44.dp,
                        valueWidth = 52.dp,
                        onPreview = onPreview,
                        onCommit = onCommit,
                    ) { s, v -> s.copy(rakeSettleMs = (v / 10f).roundToInt() * 10) }
                    Text(
                        "줄을 짚고 이 시간 안에 위아래로 움직이면 레이크(지나가는 줄이 튕김), 이보다 오래 짚고 있다가 밀면 벤딩입니다. " +
                            "레이크가 벤딩으로 잡히면 늘리고, 벤딩하려고 기다리는 게 답답하면 줄이세요",
                        color = TextDim,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                    ChoiceRow(
                        title = "프렛 간격",
                        options = listOf(FretLayoutKind.EQUAL to "등간격", FretLayoutKind.REAL to "실제"),
                        selected = settings.fretLayout,
                        onSelect = { kind -> onCommit { it.copy(fretLayout = kind) } },
                    )
                    Text(
                        "실제: 진짜 베이스처럼 높은 프렛일수록 칸이 좁아집니다(24프렛은 1프렛의 약 1/4)",
                        color = TextDim,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
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
                Section("오디오 — 소리가 끊길 때만") {
                    val info = remember(settings.audioBufferChunks) { audio.debugInfo() }
                    ChoiceRow(
                        title = "미리 채워 두는 양",
                        options = Settings.BUFFER_CHUNKS.map { chunks ->
                            val ms = info.framesPerBuffer * chunks * 1000f / info.sampleRate
                            chunks to "${ms.roundToInt()} ms"
                        },
                        selected = settings.audioBufferChunks,
                        onSelect = { chunks -> onCommit { it.copy(audioBufferChunks = chunks) } },
                    )
                    Text(
                        "소리를 얼마나 미리 만들어 두는지입니다. 작을수록 터치 후 소리가 빨리 나지만, 폰이 바쁠 때 \"지직\" 하고 끊길 수 있습니다. " +
                            "끊기지 않으면 가장 작은 값 그대로 두세요. 아래 \"끊김\" 숫자가 올라가면 한 단계 키우면 됩니다",
                        color = TextDim,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
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

/** 0~1 값의 슬라이더. 값은 0~100으로 보여 준다. */
@Composable
private fun UnitSlider(
    label: String,
    value: Float,
    onPreview: ((Settings) -> Settings) -> Unit,
    onCommit: ((Settings) -> Settings) -> Unit,
    set: (Settings, Float) -> Settings,
) = LabeledSlider(label, value, 0f..1f, "${(value * 100).roundToInt()}", 44.dp, 30.dp, onPreview, onCommit, set)

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    labelWidth: Dp,
    valueWidth: Dp,
    onPreview: ((Settings) -> Settings) -> Unit,
    onCommit: ((Settings) -> Settings) -> Unit,
    set: (Settings, Float) -> Settings,
) {
    // 세로 360dp 화면에 슬라이더 셋과 시험음이 한 번에 보여야 한다 → 라벨·슬라이더·값을 한 줄에.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextMain, fontSize = 13.sp, modifier = Modifier.width(labelWidth))
        Slider(
            value = value,
            onValueChange = { v -> onPreview { set(it, v) } },
            onValueChangeFinished = { onCommit { it } },
            valueRange = range,
            modifier = Modifier
                .weight(1f)
                .height(34.dp),
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Chip),
        )
        Text(valueText, color = TextDim, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(valueWidth))
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
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) Color.Black else TextMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** 개방현 시험음. 지판에서 뮤트 없이 친 것처럼 자연 감쇠한다 — 감쇠·밝기 슬라이더를 귀로 맞추기 위한 것. */
@Composable
private fun TestToneButton(name: String, string: Int, audio: AudioController, modifier: Modifier = Modifier) {
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
            "끊김 ${info.underruns}회 · 버린 커맨드 ${info.droppedCommands} · ${if (info.running) "실행 중" else "정지"}",
        color = TextDim,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 15.sp,
    )
}
