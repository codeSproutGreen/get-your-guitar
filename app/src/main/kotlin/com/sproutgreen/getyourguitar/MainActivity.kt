package com.sproutgreen.getyourguitar

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import com.sproutgreen.getyourguitar.audio.AudioController
import com.sproutgreen.getyourguitar.data.DataStoreSettingsRepository
import com.sproutgreen.getyourguitar.ui.fretboard.FretboardScreen
import com.sproutgreen.getyourguitar.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private enum class Screen { FRETBOARD, SETTINGS }

class MainActivity : ComponentActivity() {
    private lateinit var audio: AudioController
    private lateinit var app: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        volumeControlStream = AudioManager.STREAM_MUSIC
        hideSystemBars()

        // DI 프레임워크 없이 여기서 조립한다(스펙 6.5).
        // 저장된 설정을 먼저 읽는다. 작은 Preferences 파일 하나라 수 ms이고, 이렇게 해야 첫 화면과 오디오가
        // 기본값으로 떴다가 바뀌는 일이 없다(버퍼 배수가 다르면 AudioTrack을 두 번 열게 된다).
        val repository = DataStoreSettingsRepository(applicationContext.settingsStore)
        val initial = runBlocking { repository.settings.first() }
        audio = AudioController(this, initialBufferChunks = initial.audioBufferChunks)
        app = AppState(repository, audio, lifecycleScope, initial)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                // 화면은 둘뿐이라 내비게이션 라이브러리 없이 상태 하나로 전환한다. 설정 화면에서도 오디오는 계속 돈다.
                var screen by rememberSaveable { mutableStateOf(Screen.FRETBOARD) }
                BackHandler(enabled = screen == Screen.SETTINGS) { screen = Screen.FRETBOARD }
                when (screen) {
                    Screen.FRETBOARD -> FretboardScreen(
                        audio = audio,
                        settings = app.settings,
                        onToggleNoteNames = { app.commit { it.copy(showNoteNames = !it.showNoteNames) } },
                        onOpenSettings = { screen = Screen.SETTINGS },
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        settings = app.settings,
                        audio = audio,
                        onPreview = app::preview,
                        onCommit = app::commit,
                        onBack = { screen = Screen.FRETBOARD },
                    )
                }
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
