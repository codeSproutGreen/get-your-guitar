package com.sproutgreen.getyourguitar.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

interface SettingsRepository {
    val settings: Flow<Settings>

    suspend fun update(transform: (Settings) -> Settings)
}

/**
 * Preferences DataStore 구현. 읽기 실패(파일 손상 등)는 기본값으로, 범위 밖 값은 [Settings.sanitized]로 되돌린다.
 * DataStore 자체는 Android 없이 동작하므로 JVM 단위 테스트에서 실제 파일로 검증한다.
 */
class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    override val settings: Flow<Settings> = store.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it.toSettings() }

    override suspend fun update(transform: (Settings) -> Settings) {
        store.edit { prefs ->
            val next = transform(prefs.toSettings()).sanitized()
            prefs[MASTER_VOLUME] = next.masterVolume
            prefs[BRIGHTNESS] = next.brightness
            prefs[DECAY] = next.decay
            prefs[HOLD_TO_SUSTAIN] = next.holdToSustain
            prefs[BEND_RANGE_CENTS] = next.bendRangeCents
            prefs[SHOW_NOTE_NAMES] = next.showNoteNames
            prefs[AUDIO_BUFFER_CHUNKS] = next.audioBufferChunks
        }
    }

    private fun Preferences.toSettings(): Settings {
        val d = Settings.DEFAULT
        return Settings(
            masterVolume = this[MASTER_VOLUME] ?: d.masterVolume,
            brightness = this[BRIGHTNESS] ?: d.brightness,
            decay = this[DECAY] ?: d.decay,
            holdToSustain = this[HOLD_TO_SUSTAIN] ?: d.holdToSustain,
            bendRangeCents = this[BEND_RANGE_CENTS] ?: d.bendRangeCents,
            showNoteNames = this[SHOW_NOTE_NAMES] ?: d.showNoteNames,
            audioBufferChunks = this[AUDIO_BUFFER_CHUNKS] ?: d.audioBufferChunks,
        ).sanitized()
    }

    private companion object {
        val MASTER_VOLUME = floatPreferencesKey("master_volume")
        val BRIGHTNESS = floatPreferencesKey("brightness")
        val DECAY = floatPreferencesKey("decay")
        val HOLD_TO_SUSTAIN = booleanPreferencesKey("hold_to_sustain")
        val BEND_RANGE_CENTS = intPreferencesKey("bend_range_cents")
        val SHOW_NOTE_NAMES = booleanPreferencesKey("show_note_names")
        val AUDIO_BUFFER_CHUNKS = intPreferencesKey("audio_buffer_chunks")
    }
}
