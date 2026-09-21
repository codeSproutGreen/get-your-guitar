package com.sproutgreen.getyourguitar

import android.media.AudioManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.sproutgreen.getyourguitar.audio.AudioController
import com.sproutgreen.getyourguitar.ui.fretboard.FretboardScreen

class MainActivity : ComponentActivity() {
    private lateinit var audio: AudioController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        volumeControlStream = AudioManager.STREAM_MUSIC
        hideSystemBars()

        audio = AudioController(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                FretboardScreen(audio)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        audio.start()
    }

    override fun onStop() {
        audio.stop()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** 연주 중 뒤로·홈 버튼을 잘못 누르지 않도록 시스템 바를 숨긴다. 가장자리를 쓸면 잠깐 나타난다. */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
