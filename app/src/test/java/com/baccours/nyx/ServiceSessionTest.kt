package com.baccours.nyx

import com.baccours.nyx.service.ServiceSession
import com.baccours.nyx.service.SessionStore
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceSessionTest {

    /** Survives "process death": tests share one instance across ServiceSession objects. */
    private class FakeStore(
        override var sessionOpen: Boolean = false,
        override var filterEnabled: Boolean = true
    ) : SessionStore

    @Test
    fun firstEverStart_turnsTheFilterOn() {
        assertEquals(true, ServiceSession(FakeStore()).onConnected())
    }

    @Test
    fun freshEnableAfterOrderlyShutdown_forcesTheFilterOn_evenIfItWasOff() {
        val store = FakeStore()
        val first = ServiceSession(store)
        first.onConnected()
        first.onFilterChanged(false) // user switched the overlay off in the app...
        first.onDestroyed()          // ...then turned the service off (shortcut / disable button)

        // Later the shortcut enables the service again: the user wants the filter.
        assertEquals(true, ServiceSession(store).onConnected())
    }

    @Test
    fun restartAfterProcessDeath_keepsTheFilterOff() {
        val store = FakeStore()
        val first = ServiceSession(store)
        first.onConnected()
        first.onFilterChanged(false)
        // process killed: onDestroyed() never runs, so the session stays open

        assertEquals(false, ServiceSession(store).onConnected())
    }

    @Test
    fun restartAfterProcessDeath_keepsTheFilterOn() {
        val store = FakeStore()
        ServiceSession(store).onConnected()
        assertEquals(true, ServiceSession(store).onConnected())
    }

    @Test
    fun rebindWithoutDestroy_isNotAFreshStart() {
        val store = FakeStore()
        val session = ServiceSession(store)
        session.onConnected()
        session.onFilterChanged(false)
        assertEquals(false, session.onConnected()) // a duplicate connect must not flip it back on
    }

    @Test
    fun filterChanges_arePersisted_andOrderlyShutdownClosesTheSession() {
        val store = FakeStore()
        val session = ServiceSession(store)
        session.onConnected()
        assertEquals(true, store.sessionOpen)
        session.onFilterChanged(false)
        assertEquals(false, store.filterEnabled)
        session.onDestroyed()
        assertEquals(false, store.sessionOpen)
    }
}
