package dev.rouzbeh.hybridvpn

import dev.rouzbeh.hybridvpn.data.repository.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class DomainBypassDefaultTest {

    @Test
    fun `default bypassed domains constant is dot-ir`() {
        assertEquals(setOf(".ir"), SettingsRepository.DEFAULT_BYPASSED_DOMAINS)
    }

    @Test
    fun `fresh install defaults to dot-ir`() {
        val resolved = SettingsRepository.resolveBypassedDomains(
            hasStoredKey = false,
            isConfigured = false,
            storedDomains = null
        )
        assertEquals(setOf(".ir"), resolved)
    }

    @Test
    fun `legacy v1_0 user with custom saved domains preserves their existing domains`() {
        val resolved = SettingsRepository.resolveBypassedDomains(
            hasStoredKey = true,
            isConfigured = false,
            storedDomains = setOf("bank.ir")
        )
        assertEquals(setOf("bank.ir"), resolved)
    }

    @Test
    fun `legacy v1_0 user with empty set defaults to dot-ir`() {
        val resolved = SettingsRepository.resolveBypassedDomains(
            hasStoredKey = true,
            isConfigured = false,
            storedDomains = emptySet()
        )
        assertEquals(setOf(".ir"), resolved)
    }

    @Test
    fun `explicit empty save by user preserves empty set and does not revert to dot-ir`() {
        val resolved = SettingsRepository.resolveBypassedDomains(
            hasStoredKey = true,
            isConfigured = true,
            storedDomains = emptySet()
        )
        assertEquals(emptySet<String>(), resolved)
    }

    @Test
    fun `explicit custom save by user returns stored custom domains`() {
        val resolved = SettingsRepository.resolveBypassedDomains(
            hasStoredKey = true,
            isConfigured = true,
            storedDomains = setOf(".ir", "shaparak.ir")
        )
        assertEquals(setOf(".ir", "shaparak.ir"), resolved)
    }

    @Test
    fun `after reset removes keys and restores default dot-ir`() {
        val resolved = SettingsRepository.resolveBypassedDomains(
            hasStoredKey = false,
            isConfigured = false,
            storedDomains = null
        )
        assertEquals(setOf(".ir"), resolved)
    }
}
