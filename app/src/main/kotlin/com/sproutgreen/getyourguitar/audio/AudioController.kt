package com.sproutgreen.getyourguitar.audio

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sproutgreen.getyourguitar.core.engine.Command
import com.sproutgreen.getyourguitar.core.engine.SynthEngine

/**
 * Activity 수명 동안 1개. 출력 백엔드와 엔진을 소유하고 UI의 커맨드를 엔진으로 넘긴다.
 * [send]·[start]·[stop]은 메인 스레드에서만 부른다(엔진 큐가 단일 생산자이기 때문).
 */
class AudioController(context: Context) {
    private val output: AudioOutput = AudioTrackOutput(context.applicationContext)
    private val engine = SynthEngine(output.sampleRate)
    private val renderer = AudioRenderer { out, frames -> engine.render(out, frames) }
    private val drainBuffer = FloatArray(output.framesPerBuffer)
    private val logCommands = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /** null이 아니면 상단 바에 표시한다. */
    var error: String? by mutableStateOf(null)
        private set

    val sampleRate: Int get() = output.sampleRate
    val framesPerBuffer: Int get() = output.framesPerBuffer
    val underrunCount: Int get() = output.underrunCount
    val droppedCommands: Int get() = engine.stats.droppedCommands

    fun send(command: Command) {
        if (logCommands) Log.d(TAG, command.toString())
        engine.send(command)
    }

    fun start() {
        error = if (output.start(renderer)) null else "오디오 초기화 실패"
    }

    fun stop() {
        engine.send(Command.AllNotesOff)
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
