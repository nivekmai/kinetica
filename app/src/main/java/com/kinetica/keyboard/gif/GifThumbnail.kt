package com.kinetica.keyboard.gif

import android.content.Context
import android.graphics.BitmapFactory
import androidx.appcompat.widget.AppCompatImageView
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Small, static first-frame previews. Decode off the keyboard's UI thread. */
class GifThumbnail(context: Context, private val file: File) : AppCompatImageView(context) {
    private var work: Future<*>? = null
    init { scaleType = ScaleType.FIT_CENTER }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        work = executor.submit {
            val bitmap = runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 256) inSampleSize *= 2
                }
                BitmapFactory.decodeFile(file.path, options)
            }.getOrNull()
            post { if (isAttachedToWindow) setImageBitmap(bitmap) }
        }
    }
    override fun onDetachedFromWindow() {
        work?.cancel(true)
        setImageDrawable(null)
        super.onDetachedFromWindow()
    }
    companion object {
        private val executor = Executors.newSingleThreadExecutor { Thread(it, "gif-preview").apply { isDaemon = true } }
    }
}
