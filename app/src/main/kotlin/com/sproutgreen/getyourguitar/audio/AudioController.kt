package com.sproutgreen.getyourguitar.audio

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sproutgreen.getyourguitar.core.engine.Command
import com.sproutgreen.getyourguitar.core.engine.SynthEngine
import com.sproutgreen.getyourguitar.data.Settings
import com.sproutgreen.getyourguitar.data.SettingsDiff

/** 설정 화면의 디버그 정보. */
data class AudioDebugInfo(
    val sampleRate: Int,
    val framesPerBuffer: Int,
    val bufferChunks: Int,
    val underruns: Int,
    val droppedCommands: Int,
    val running: Boolean,
)

/**
 * Activity 수명 동안 1개. 출력 백엔드와 엔진을 소유하고 UI의 커맨드를 엔진으로 넘긴다.
 * 모든 메서드는 메인 스레드에서만 부른다(엔진 큐가 단일 생산자이기 때문).
 */
class AudioController(
    context: Context,
    /** 저장된 설정의 버퍼 배수. 기본값으로 열었다가 설정을 읽고 다시 여는 일을 피하려고 생성 시점에 받는다. */
    initialBufferChunks: Int = Settings.DEFAULT.audioBufferChunks,
    private val createOutput: (bufferChunks: Int) -> AudioOutput = { AudioTrackOutput(context.applicationContext, it) },
) {
    private var bufferChunks = initialBufferChunks
    private var output: AudioOutput = createOutput(bufferChunks)
    private val engine = SynthEngine(output.sampleRate)
    private val renderer = AudioRenderer { out, frames -> engine.render(out, frames) }
    private val drainBuffer = FloatArray(output.framesPerBuffer)
    private val logCommands = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private var applied: Settings? = null
    private var wantRunning = false

    /** null이 아니면 상단 바에 표시한다. */
    var error: String? by mutableStateOf(null)
        private set

    fun send(command: Command) {
        if (logCommands) Log.d(TAG, command.toString())
        engine.send(command)
    }

    /**
     * 달라진 값만 엔진에 보낸다. 버퍼 배수가 바뀌면 출력만 다시 만든다 — 엔진과 울리던 음의 상태는 그대로다.
     * 출력이 멈춰 있는 동안 보낸 커맨드는 큐에 쌓였다가 다음 렌더에서 적용된다.
     */
    fun applySettings(settings: Settings) {
        val next = settings.sanitized()
        for (command in SettingsDiff.commands(applied, next)) send(command)
        val restart = SettingsDiff.needsOutputRestart(applied, next) || (applied == null && next.audioBufferChunks != bufferChunks)
        applied = next
        if (restart) {
            bufferChunks = next.audioBufferChunks
            val wasRunning = wantRunning
            if (wasRunning) stopOutput()
            output = createOutput(bufferChunks)
            if (wasRunning) startOutput()
        }
    }

    fun start() {
        wantRunning = true
        startOutput()
    }

    fun stop() {
        wantRunning = false
        engine.send(Command.AllNotesOff)
        stopOutput()
    }

    fun debugInfo(): AudioDebugInfo = AudioDebugInfo(
        sampleRate = output.sampleRate,
        framesPerBuffer = output.framesPerBuffer,
        bufferChunks = bufferChunks,
        underruns = output.underrunCount,
        droppedCommands = engine.stats.droppedCommands,
        running = wantRunning && error == null,
    )

    private fun startOutput() {
        error = if (output.start(renderer)) null else "오디오 초기화 실패"
    }

    private fun stopOutput() {
        if (!output.stop()) return
        // 오디오 스레드가 끝난 뒤이므로 여기서 렌더해도 소비자는 여전히 하나다.
        // 페이드아웃을 끝까지 흘려보내 다음 start 때 이전 음의 꼬리가 새어 나오지 않게 한다.
        val frames = drainBuffer.size
        var drained = 0
        while (drained < output.sampleRate / DRAIN_FRACTION_OF_SECOND) {
            engine.render(drainBuffer, frames)
            drained += frames
        }
    }

    private companion object {
        const val TAG = "gyg-cmd"
        const val DRAIN_FRACTION_OF_SECOND = 50 // 20 ms
    }
}
