package com.kinetica.keyboard.gif

/** A late decode must not revive a dismissed preview or replace a newer one. */
internal class GifPreviewSession<T>(
    private val show: (T?) -> Unit,
    private val start: (T) -> Unit,
    private val stop: (T) -> Unit,
) {
    private var generation = 0
    private var attached = false
    private var visible = false
    private var image: T? = null

    fun attach(isVisible: Boolean): Int {
        detach()
        attached = true
        visible = isVisible
        return generation
    }

    fun decoded(ticket: Int, decoded: T?): Boolean {
        if (!attached || ticket != generation) {
            decoded?.let(stop)
            return false
        }
        image?.let(stop)
        image = decoded
        show(decoded)
        if (visible) decoded?.let(start)
        return true
    }

    fun visibility(isVisible: Boolean) {
        if (visible == isVisible) return
        visible = isVisible
        image?.let { if (attached && visible) start(it) else stop(it) }
    }

    fun detach() {
        generation++
        attached = false
        visible = false
        image?.let(stop)
        image = null
        show(null)
    }
}
