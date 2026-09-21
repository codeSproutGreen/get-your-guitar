package com.sproutgreen.getyourguitar.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import kotlin.math.max

/**
 * `AudioTrack` 저지연 출력. 모노 float, 기기 고유 샘플레이트·버스트 크기를 그대로 쓴다
 * (둘 중 하나라도 어긋나면 FAST 트랙을 못 받고 리샘플러·딥 버퍼를 거친다).
 *
 * 렌더 청크 = 프레임/버퍼. 실효 버퍼 = 프레임/버퍼 × [bufferChunks].
 */
class AudioTrackOutput(
    context: Context,
    private val bufferChunks: Int = 2,
) : AudioOutput {
    override val sampleRate: Int
    override val framesPerBuffer: Int

    @Volatile
    override var underrunCount: Int = 0
        private set

    @Volatile
    private var running = false
    private var thread: Thread? = null

    init {
        val manager = context.getSystemService(AudioManager::class.java)
        sampleRate = manager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
            ?.takeIf { it in 8_000..192_000 } ?: FALLBACK_SAMPLE_RATE
        framesPerBuffer = manager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull()
            ?.takeIf { it in 16..8_192 } ?: FALLBACK_FRAMES_PER_BUFFER
    }

    override fun start(renderer: AudioRenderer): Boolean {
        if (running) return true
        // 스펙 7장: 저지연 모드로 실패하면 기본 성능 모드로 1회 재시도.
        val track = createTrack(lowLatency = true) ?: createTrack(lowLatency = false) ?: return false

        Log.i(
            TAG,
            "AudioTrack started: sampleRate=$sampleRate framesPerBuffer=$framesPerBuffer chunks=$bufferChunks " +
                "bufferFrames=${track.bufferSizeInFrames} capacityFrames=${track.bufferCapacityInFrames} " +
                "lowLatency=${track.performanceMode == AudioTrack.PERFORMANCE_MODE_LOW_LATENCY}",
        )

        running = true
        thread = Thread({ audioLoop(track, renderer) }, "gyg-audio").also { it.start() }
        return true
    }

    override fun stop(): Boolean {
        running = false
        val t = thread ?: return true
        t.join(STOP_TIMEOUT_MS)
        thread = null
        return !t.isAlive
    }

    private fun createTrack(lowLatency: Boolean): AudioTrack? = try {
        val minBytes = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val wantedFrames = framesPerBuffer * bufferChunks
        val builder = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(max(minBytes, wantedFrames * BYTES_PER_FLOAT))
        if (lowLatency) builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)

        val track = builder.build()
        if (track.state == AudioTrack.STATE_INITIALIZED) {
            // 용량은 넉넉히 잡고, 실제로 채워 두는 양만 줄여 지연을 낮춘다.
            track.setBufferSizeInFrames(wantedFrames)
            track
        } else {
            track.release()
            null
        }
    } catch (e: RuntimeException) {
        Log.w(TAG, "AudioTrack creation failed (lowLatency=$lowLatency)", e)
        null
    }

    /** 오디오 스레드. 루프 안에서는 할당·락·로그를 하지 않는다. */
    private fun audioLoop(track: AudioTrack, renderer: AudioRenderer) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val buffer = FloatArray(framesPerBuffer)
        val frames = framesPerBuffer
        var sinceUnderrunCheck = 0
        try {
            track.play()
            while (running) {
                renderer.render(buffer, frames)
                if (track.write(buffer, 0, frames, AudioTrack.WRITE_BLOCKING) < 0) break
                if (++sinceUnderrunCheck >= UNDERRUN_CHECK_INTERVAL) {
                    sinceUnderrunCheck = 0
                    underrunCount = track.underrunCount
                }
            }
        } catch (t: Throwable) {
            // 배너 표시는 M3. 여기서는 앱이 죽지 않게만 한다.
            Log.e(TAG, "audio thread stopped", t)
        } finally {
            running = false
            try {
                track.pause()
                track.flush()
            } catch (_: IllegalStateException) {
            }
            track.release()
        }
    }

    private companion object {
        const val TAG = "gyg-audio"
        const val FALLBACK_SAMPLE_RATE = 48_000
        const val FALLBACK_FRAMES_PER_BUFFER = 256
        const val BYTES_PER_FLOAT = 4
        const val UNDERRUN_CHECK_INTERVAL = 64
        const val STOP_TIMEOUT_MS = 1_000L
    }
}
