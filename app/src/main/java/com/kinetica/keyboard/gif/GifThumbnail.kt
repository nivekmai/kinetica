package com.kinetica.keyboard.gif

import android.content.Context
import android.graphics.BitmapFactory
import androidx.appcompat.widget.AppCompatImageView
import com.kinetica.keyboard.engine.KineticaConstants
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Small first-frame previews. Bind generations prevent recycled cells showing an old GIF. */
class GifThumbnail(context: Context, file: File? = null, private val fitAspect: Boolean = false) : AppCompatImageView(context) {
    private var currentFile = file
    private var imageWidth = 0
    private var imageHeight = 0
    private var work: Future<*>? = null
    private var generation = 0
    init { scaleType = ScaleType.FIT_CENTER }

    fun bind(file: File, width: Int, height: Int) {
        generation++
        work?.cancel(true)
        currentFile = file
        imageWidth = width
        imageHeight = height
        setImageDrawable(null)
        requestLayout()
        if (isAttachedToWindow) load()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (fitAspect && MeasureSpec.getSize(widthMeasureSpec) > 0) {
            val height = GifTileSizing.height(MeasureSpec.getSize(widthMeasureSpec), imageWidth, imageHeight,
                (KineticaConstants.GIF_TILE_MIN_HEIGHT_DP * resources.displayMetrics.density).toInt())
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        } else super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        load()
    }

    private fun load() {
        val file = currentFile ?: return
        work?.cancel(true)
        val ticket = ++generation
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
            post { if (ticket == generation && isAttachedToWindow) setImageBitmap(bitmap) }
        }
    }
    override fun onDetachedFromWindow() {
        generation++
        work?.cancel(true)
        setImageDrawable(null)
        super.onDetachedFromWindow()
    }
    companion object {
        private val executor = Executors.newSingleThreadExecutor { Thread(it, "gif-preview").apply { isDaemon = true } }
    }
}
