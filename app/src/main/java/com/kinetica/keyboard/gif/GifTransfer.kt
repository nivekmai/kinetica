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
    val hasAttachmentRequest: Boolean get() = attachment != null
    @Volatile
    var pending: Pending? = null
        set(value) {
            field = value
            attachment = null
        }

    fun beginSearch(target: Target): String {
        attachment = null
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
        return true
    }

    fun takeAttachment(target: Target, nowMs: Long, isBrowserEditor: Boolean = false): Return? {
        val request = attachment ?: return null
        if (isBrowserEditor) return null
        // Consume before calling the editor: restartInput/repeated lifecycle callbacks
        // and rejected commitContent must never send twice or retry in another field.
        attachment = null
        val timely = nowMs >= request.confirmedAtMs &&
            nowMs - request.confirmedAtMs <= KineticaConstants.GIF_ATTACH_RETURN_TIMEOUT_MS
        return Return(request.selection, timely && target == request.target &&
            target.before != null && target.after != null)
    }

    fun cancelAutomaticAttachment() { attachment = null }
}
