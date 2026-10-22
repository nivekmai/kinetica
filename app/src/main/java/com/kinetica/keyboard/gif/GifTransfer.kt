package com.kinetica.keyboard.gif

import com.kinetica.keyboard.engine.KineticaConstants
import java.util.UUID

/** Browser selection is a one-shot attachment, bound to the editor that opened search. */
object GifTransfer {
    data class Pending(val id: String, val packageName: String, val fieldId: Int)
    // Small, memory-only context; never exposed to WebView, URLs, disk or logs.
    data class Target(val packageName: String, val fieldId: Int, val fieldName: String?,
        val inputType: Int, val selectionStart: Int, val selectionEnd: Int,
        val before: String?, val after: String?)
    data class Return(val selection: Pending, val attach: Boolean)
    private data class Search(val token: String, val target: Target)
    private data class Attachment(val selection: Pending, val target: Target, val confirmedAtMs: Long)

    private var search: Search? = null
    private var attachment: Attachment? = null
    // Owned by the live IME; cleared when it is destroyed. Confirmation must wake
    // an already-started input session as well as one receiving fresh callbacks.
    var onAttachmentRequested: (() -> Unit)? = null
    val hasAttachmentRequest: Boolean get() = attachment != null
    @Volatile
    var pending: Pending? = null
        set(value) {
            field = value
            attachment = null
        }

    fun beginSearch(target: Target): String {
        attachment = null
        GifDebug.record("search_started; origin_context_readable=${target.before != null && target.after != null}")
        return UUID.randomUUID().toString().also { search = Search(it, target) }
    }

    fun isSearchActive(token: String?): Boolean = token != null && search?.token == token
    fun cancelSearch(token: String?) { if (isSearchActive(token)) search = null }

    fun confirmSearch(token: String?, id: String, nowMs: Long): Boolean {
        val request = search?.takeIf { it.token == token } ?: return false
        if (!GifPolicy.fileId(id)) return false
        search = null
        val selected = Pending(id, request.target.packageName, request.target.fieldId)
        pending = selected
        attachment = Attachment(selected, request.target, nowMs)
        GifDebug.record("selection_confirmed; listener=${onAttachmentRequested != null}")
        onAttachmentRequested?.invoke()
        return true
    }

    fun takeAttachment(target: Target?, nowMs: Long, isBrowserEditor: Boolean = false): Return? {
        val request = attachment ?: return null
        val elapsed = nowMs - request.confirmedAtMs
        if (elapsed < 0 || elapsed > KineticaConstants.GIF_ATTACH_READY_TIMEOUT_MS) {
            attachment = null
            GifDebug.record("return_timeout")
            return Return(request.selection, false)
        }
        if (isBrowserEditor || target == null) {
            GifDebug.record(if (isBrowserEditor) "waiting_for_browser_close" else "waiting_for_connection")
            return null
        }
        if (target.packageName == request.target.packageName && target.fieldId == request.target.fieldId &&
            target.fieldName == request.target.fieldName && target.inputType == request.target.inputType &&
            (target.before == null || target.after == null)) {
            GifDebug.record("waiting_for_readable_context")
            return null
        }
        // Consume before calling the editor: restartInput/repeated lifecycle callbacks
        // and rejected commitContent must never send twice or retry in another field.
        attachment = null
        val matches = target == request.target && target.before != null && target.after != null
        GifDebug.record(if (matches) "destination_ready" else
            "destination_mismatch; package=${target.packageName == request.target.packageName}; " +
            "field=${target.fieldId == request.target.fieldId && target.fieldName == request.target.fieldName}; " +
            "type=${target.inputType == request.target.inputType}; " +
            "selection=${target.selectionStart == request.target.selectionStart && target.selectionEnd == request.target.selectionEnd}; " +
            "context=${target.before == request.target.before && target.after == request.target.after}")
        return Return(request.selection, matches)
    }

    fun cancelAutomaticAttachment() { attachment = null }
}
