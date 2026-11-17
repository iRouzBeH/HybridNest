package dev.rouzbeh.hybridvpn.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Session-aware record of the routing policy actually in force for a tunnel.
 *
 * Identity: revision alone is insufficient — two sessions can share a revision if
 * no settings changed between them, and a stale teardown from a superseded session
 * must not clear a NEWER session's status. [ActivePolicy.sessionId] disambiguates.
 */
data class ActivePolicy(
    val sessionId: Long,
    val revision: Long,
    val protocol: String,
    val establishedAtMs: Long,
)

/** Why the most recent active policy ended. */
data class PolicyTeardownReason(val intentional: Boolean, val detail: String)

/**
 * Single source of truth for "which routing policy is actually applied".
 *
 * Semantics (corrected Stage 1):
 *  - [active] is non-null ONLY when a tunnel was actually established with that
 *    policy AND establishment succeeded. It is published by the service AFTER
 *    `Builder.establish()` returns — never merely because a snapshot file was
 *    written or settings were read.
 *  - [lastApplied] is history: the most recent ESTABLISHED session, kept across
 *    teardown so the UI can show what the last tunnel used, distinct from what is
 *    active NOW.
 *  - [lastTeardown] explains how the last active policy ended (intentional or
 *    failure), for diagnostics.
 *  - [appliedRevision] is the legacy derived view of [active] (revision or null);
 *    existing UI consumers (routing-pending banner) keep their exact semantics:
 *    null means "no active tunnel".
 *
 * The service is the single writer; the UI is a reader. Lives in the main process.
 */
object RoutingPolicyStatus {

    private val _active = MutableStateFlow<ActivePolicy?>(null)
    private val _lastApplied = MutableStateFlow<ActivePolicy?>(null)
    private val _lastTeardown = MutableStateFlow<PolicyTeardownReason?>(null)
    private val _appliedRevision = MutableStateFlow<Long?>(null)

    /** Policy of the tunnel that is up RIGHT NOW, or null when disconnected. */
    val active: StateFlow<ActivePolicy?> = _active.asStateFlow()

    /** Policy of the most recent established session (may already be torn down). */
    val lastApplied: StateFlow<ActivePolicy?> = _lastApplied.asStateFlow()

    /** Why the most recent active policy ended (null while a session is active). */
    val lastTeardown: StateFlow<PolicyTeardownReason?> = _lastTeardown.asStateFlow()

    /** Derived legacy view: revision of the ACTIVE tunnel, or null when none. */
    val appliedRevision: StateFlow<Long?> = _appliedRevision.asStateFlow()

    /**
     * Publishes an ACTIVE policy. Must be called only after successful
     * `Builder.establish()`; never on snapshot write or settings read.
     */
    fun publish(sessionId: Long, revision: Long, protocol: String, nowMs: Long) {
        val record = ActivePolicy(sessionId, revision, protocol, nowMs)
        _active.value = record
        _lastApplied.value = record
        _lastTeardown.value = null
        _appliedRevision.value = revision
    }

    /**
     * Clears the ACTIVE policy when [sessionId] matches the currently active one.
     * Stale clears (from a superseded session) are ignored so an old teardown
     * cannot clobber a newer session's status.
     */
    fun clearActive(sessionId: Long, intentional: Boolean, detail: String) {
        val current = _active.value ?: return
        if (current.sessionId != sessionId) return // stale teardown; ignore
        _active.value = null
        _lastTeardown.value = PolicyTeardownReason(intentional, detail)
        _appliedRevision.value = null
    }

    /** Test/diagnostic helper: reset all state. */
    fun resetForTest() {
        _active.value = null
        _lastApplied.value = null
        _lastTeardown.value = null
        _appliedRevision.value = null
    }
}
