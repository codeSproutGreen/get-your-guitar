package com.sproutgreen.getyourguitar

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sproutgreen.getyourguitar.audio.AudioController
import com.sproutgreen.getyourguitar.data.Settings
import com.sproutgreen.getyourguitar.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 화면들이 공유하는 상태: 현재 설정. Activity가 만들고 Activity와 함께 사라진다.
 *
 * ViewModel을 쓰지 않는 이유: [AudioController]는 Activity 수명인데 ViewModel은 그보다 오래 살 수 있어,
 * ViewModel이 컨트롤러를 쥐면 재생성된 Activity에서 죽은 컨트롤러를 부르게 된다. 화면이 둘뿐이고 공유 상태가
 * 설정 하나라 Activity 수명의 상태 홀더로 충분하다.
 */
@Stable
class AppState(
    private val repository: SettingsRepository,
    private val audio: AudioController,
    private val scope: CoroutineScope,
    initial: Settings,
) {
    var settings: Settings by mutableStateOf(initial)
        private set

    init {
        audio.applySettings(initial)
        scope.launch {
            repository.settings.collect {
                settings = it
                audio.applySettings(it)
            }
        }
    }

    /** 슬라이더를 끄는 동안: 소리에는 바로 반영하되 저장은 하지 않는다(초당 수십 번 파일을 쓰지 않기 위해). */
    fun preview(transform: (Settings) -> Settings) {
        settings = transform(settings).sanitized()
        audio.applySettings(settings)
    }

    /** 값을 확정: 반영 + 저장. */
    fun commit(transform: (Settings) -> Settings = { it }) {
        preview(transform)
        val snapshot = settings
        scope.launch { repository.update { snapshot } }
    }
}
