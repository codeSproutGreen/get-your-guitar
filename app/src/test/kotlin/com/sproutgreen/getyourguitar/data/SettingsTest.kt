package com.sproutgreen.getyourguitar.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.sproutgreen.getyourguitar.core.engine.Command
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsTest {
    @Test
    fun `defaults are hold-to-sustain, whole-tone bend, two buffer chunks`() {
        val d = Settings.DEFAULT
        assertTrue(d.holdToSustain)
        assertEquals(200, d.bendRangeCents)
        assertEquals(2, d.audioBufferChunks)
        assertFalse(d.showNoteNames)
        assertEquals(d, d.sanitized())
    }

    @Test
    fun `sanitized clamps and replaces nonsense`() {
        val s = Settings(
            masterVolume = 7f, brightness = -2f, decay = Float.NaN,
            bendRangeCents = 123, audioBufferChunks = 9,
        ).sanitized()
        assertEquals(1f, s.masterVolume)
        assertEquals(0f, s.brightness)
        assertEquals(Settings.DEFAULT.decay, s.decay)
        assertEquals(200, s.bendRangeCents)
        assertEquals(2, s.audioBufferChunks)
    }

    @Test
    fun `sanitized keeps every allowed choice`() {
        for (cents in listOf(200, 400)) assertEquals(cents, Settings(bendRangeCents = cents).sanitized().bendRangeCents)
        for (chunks in listOf(2, 3, 4)) assertEquals(chunks, Settings(audioBufferChunks = chunks).sanitized().audioBufferChunks)
    }

    // ---- 설정 → 엔진 ----

    @Test
    fun `first application sends every engine value`() {
        val s = Settings(masterVolume = 0.5f, brightness = 0.2f, decay = 0.9f)
        assertEquals(
            listOf(Command.SetMasterGain(0.5f), Command.SetBrightness(0.2f), Command.SetDecay(0.9f)),
            SettingsDiff.commands(old = null, new = s),
        )
        assertFalse(SettingsDiff.needsOutputRestart(null, s))
    }

    @Test
    fun `only changed values are sent`() {
        val a = Settings()
        assertEquals(emptyList(), SettingsDiff.commands(a, a))
        assertEquals(listOf<Command>(Command.SetDecay(0.3f)), SettingsDiff.commands(a, a.copy(decay = 0.3f)))
        assertEquals(listOf<Command>(Command.SetMasterGain(1f)), SettingsDiff.commands(a, a.copy(masterVolume = 1f)))
    }

    @Test
    fun `ui-only settings send nothing to the engine`() {
        val a = Settings()
        val b = a.copy(holdToSustain = false, bendRangeCents = 400, showNoteNames = true)
        assertEquals(emptyList(), SettingsDiff.commands(a, b))
        assertFalse(SettingsDiff.needsOutputRestart(a, b))
    }

    @Test
    fun `changing the buffer size needs an output restart and nothing else`() {
        val a = Settings()
        val b = a.copy(audioBufferChunks = 4)
        assertTrue(SettingsDiff.needsOutputRestart(a, b))
        assertEquals(emptyList(), SettingsDiff.commands(a, b))
    }

    // ---- 저장소: 실제 DataStore를 임시 파일로 ----

    private fun withRepository(dir: File, block: suspend (DataStoreSettingsRepository) -> Unit) {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") }
            runBlocking { block(DataStoreSettingsRepository(store)) }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `an empty store yields the defaults`(@TempDir dir: File) = withRepository(dir) { repo ->
        assertEquals(Settings.DEFAULT, repo.settings.first())
    }

    @Test
    fun `updates are persisted and read back`(@TempDir dir: File) {
        val wanted = Settings(0.4f, 0.9f, 0.1f, holdToSustain = false, bendRangeCents = 400, showNoteNames = true, audioBufferChunks = 3)
        withRepository(dir) { repo ->
            repo.update { wanted }
            assertEquals(wanted, repo.settings.first())
        }
        // 새 DataStore 인스턴스(앱 재시작)로 같은 파일을 읽는다
        withRepository(dir) { repo -> assertEquals(wanted, repo.settings.first()) }
    }

    @Test
    fun `update transforms the current value`(@TempDir dir: File) = withRepository(dir) { repo ->
        repo.update { it.copy(decay = 0.25f) }
        repo.update { it.copy(showNoteNames = true) }
        val s = repo.settings.first()
        assertEquals(0.25f, s.decay)
        assertTrue(s.showNoteNames)
        assertEquals(Settings.DEFAULT.masterVolume, s.masterVolume)
    }

    @Test
    fun `out of range values are clamped on write`(@TempDir dir: File) = withRepository(dir) { repo ->
        repo.update { it.copy(masterVolume = 5f, audioBufferChunks = 99) }
        val s = repo.settings.first()
        assertEquals(1f, s.masterVolume)
        assertEquals(2, s.audioBufferChunks)
    }

    @Test
    fun `garbage already in the file is sanitized on read`(@TempDir dir: File) {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") }
            runBlocking {
                store.edit {
                    it[floatPreferencesKey("brightness")] = -40f
                    it[intPreferencesKey("bend_range_cents")] = 7
                }
                val s = DataStoreSettingsRepository(store).settings.first()
                assertEquals(0f, s.brightness)
                assertEquals(200, s.bendRangeCents)
            }
        } finally {
            scope.cancel()
        }
    }
}
