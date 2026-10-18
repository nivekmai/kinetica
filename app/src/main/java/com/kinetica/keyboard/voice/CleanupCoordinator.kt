package com.kinetica.keyboard.voice

import com.kinetica.keyboard.engine.KineticaConstants as K

/** One job plus one coalesced revision. All calls are on the session/main thread. */
class CleanupCoordinator {
    data class Job(val id: Long, val revision: Long, val started: Long)
    var revision = 0L
        private set
    var appliedRevision = 0L
        private set
    var active: Job? = null
        private set
    private var handledRevision = 0L
    private var serial = 0L
    private var due = 0L
    private var nextAllowed = 0L
    var enabled = true
        private set
    val pending get() = enabled && revision > handledRevision
    fun finalized(now: Long) { revision++; due = maxOf(now + K.VOICE_CLEANUP_DELAY_MS, nextAllowed) }
    fun delay(now: Long): Long? = if (pending && active == null) (due - now).coerceAtLeast(0) else null
    fun start(now: Long): Job? {
        if (delay(now) != 0L) return null
        return Job(++serial, revision, now).also { active = it; handledRevision = revision }
    }
    fun finish(job: Job, applied: Boolean, failed: Boolean, now: Long): Boolean {
        if (active != job) return false
        active = null
        if (applied) appliedRevision = job.revision
        // A failure stays inspectable; newer speech or explicit Retry can trigger another attempt.
        if (failed) handledRevision = revision
        // Slow inference already provided the throttle. Don't add another full idle
        // interval before processing speech that arrived while this job was running.
        nextAllowed = maxOf(now, job.started + K.VOICE_CLEANUP_INTERVAL_MS)
        due = maxOf(due, nextAllowed)
        return true
    }
    fun setEnabled(value: Boolean, now: Long) {
        enabled = value
        active = null
        serial++
        if (value) retry(now)
    }
    fun retry(now: Long) {
        if (!enabled || active != null || revision == 0L) return
        handledRevision = revision - 1
        due = now
    }
    /** Stop active/queued work on resource pressure, but leave explicit Retry available. */
    fun suspendWork() { active = null; serial++; handledRevision = revision }
    fun cancel() { enabled = false; active = null; serial++; handledRevision = revision }
}
