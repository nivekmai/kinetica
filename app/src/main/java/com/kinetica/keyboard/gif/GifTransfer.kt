package com.kinetica.keyboard.gif

/** Keep the selected file until attached/dismissed, but offer the picker only once. */
object GifTransfer {
    data class Pending(val id: String, val packageName: String, val fieldId: Int)

    private var returnOffer: Pending? = null
    // Memory-only: a new explicit browser selection arms one return to its editor.
    var pending: Pending? = null
        set(value) {
            field = value
            returnOffer = value
        }

    fun takeReturnOffer(packageName: String?, fieldId: Int): Pending? {
        val selection = returnOffer ?: return null
        if (selection.packageName != packageName || selection.fieldId != fieldId) return null
        returnOffer = null
        return selection
    }

    /** Opening GIFs manually also acknowledges any selection waiting to be shown. */
    fun acknowledgeReturnOffer() { returnOffer = null }
}
