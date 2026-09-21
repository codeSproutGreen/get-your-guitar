package com.sproutgreen.getyourguitar.core.synth

import kotlin.math.abs
import kotlin.math.sqrt

/** 테스트 전용 신호 분석 도우미. */
object SignalAnalysis {
    fun rms(x: FloatArray, from: Int = 0, to: Int = x.size): Float {
        var sum = 0.0
        for (i in from until to) sum += x[i].toDouble() * x[i]
        return sqrt(sum / (to - from)).toFloat()
    }

    fun peak(x: FloatArray, from: Int = 0, to: Int = x.size): Float {
        var p = 0f
        for (i in from until to) p = maxOf(p, abs(x[i]))
        return p
    }

    /** 인접 샘플 차이의 최댓값. 클릭(불연속)이 있으면 크게 튄다. */
    fun maxStep(x: FloatArray, from: Int = 1, to: Int = x.size): Float {
        var m = 0f
        for (i in maxOf(from, 1) until to) m = maxOf(m, abs(x[i] - x[i - 1]))
        return m
    }

    /**
     * 정규화 자기상관으로 기본 주파수를 추정한다.
     * 기대 주기의 0.7~1.4배 범위만 탐색(옥타브 배수 제외)하고 포물선 보간으로 소수점 지연을 구한다.
     */
    fun estimateHz(x: FloatArray, sampleRate: Int, expectedHz: Float, from: Int, length: Int): Float {
        val period = sampleRate / expectedHz
        val minLag = (period * 0.7f).toInt().coerceAtLeast(2)
        val maxLag = (period * 1.4f).toInt() + 1
        val n = length - maxLag - 1
        require(n > period * 3) { "analysis window too short for $expectedHz Hz" }
        require(from + length <= x.size) { "window exceeds signal" }

        val r = DoubleArray(maxLag + 2)
        for (lag in minLag - 1..maxLag + 1) {
            var xy = 0.0
            var xx = 0.0
            var yy = 0.0
            for (i in 0 until n) {
                val p = x[from + i].toDouble()
                val q = x[from + i + lag].toDouble()
                xy += p * q
                xx += p * p
                yy += q * q
            }
            r[lag] = if (xx > 0.0 && yy > 0.0) xy / sqrt(xx * yy) else 0.0
        }
        var best = minLag
        for (lag in minLag..maxLag) if (r[lag] > r[best]) best = lag
        val y0 = r[best - 1]
        val y1 = r[best]
        val y2 = r[best + 1]
        val denom = y0 - 2 * y1 + y2
        val shift = if (abs(denom) > 1e-12) 0.5 * (y0 - y2) / denom else 0.0
        return (sampleRate / (best + shift)).toFloat()
    }

    fun render(voice: Voice, frames: Int, chunk: Int = 256): FloatArray {
        val out = FloatArray(frames)
        var done = 0
        while (done < frames) {
            val n = minOf(chunk, frames - done)
            voice.render(out, done, n)
            done += n
        }
        return out
    }
}
