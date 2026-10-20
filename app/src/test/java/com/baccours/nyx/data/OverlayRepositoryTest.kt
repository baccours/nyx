package com.baccours.nyx.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.hours

class OverlayRepositoryTest {

    private class FakeStore(var stored: OverlaySettings = OverlaySettings.DEFAULT) : SettingsStore {
        var saves = 0
        override fun load() = stored
        override fun save(settings: OverlaySettings) {
            stored = settings
            saves++
        }
    }

    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)

    // A huge debounce keeps the background collector out of the way: tests drive persistence via flush().
    private fun repository(store: FakeStore) = OverlayRepository(store, scope, persistDebounce = 1.hours)

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun load_isSanitized() {
        val store = FakeStore(OverlaySettings(dimIntensity = 5f, blueLightIntensity = Float.NaN))
        assertEquals(store.stored.sanitized(), repository(store).settings.value)
    }

    @Test
    fun update_clampsOutOfRangeValues() {
        val repo = repository(FakeStore())
        repo.update { it.copy(dimIntensity = 9f) }
        assertEquals(1f, repo.settings.value.dimIntensity, 0f)
    }

    @Test
    fun flush_persistsTheLatestValue_once() {
        val store = FakeStore()
        val repo = repository(store)
        repo.update { it.copy(dimIntensity = 0.4f) }
        repo.update { it.copy(dimIntensity = 0.7f) }
        repo.flush()
        repo.flush()
        assertEquals(0.7f, store.stored.dimIntensity, 0f)
        assertEquals(1, store.saves)
    }

    @Test
    fun flush_withoutChanges_doesNotWrite() {
        val store = FakeStore()
        repository(store).flush()
        assertEquals(0, store.saves)
    }
}
