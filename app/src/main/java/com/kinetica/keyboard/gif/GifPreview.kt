package com.kinetica.keyboard.gif

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageView
import com.kinetica.keyboard.R
import com.kinetica.keyboard.engine.KineticaConstants
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** One animated confirmation preview. Recents continue using lightweight thumbnails. */
class GifPreview(context: Context, private val file: File) : AppCompatImageView(context) {
    private var work: Future<*>? = null
    private val session = GifPreviewSession<Drawable>(
        show = { setImageDrawable(it) },
        start = { (it as? Animatable)?.start() },
        stop = { (it as? Animatable)?.stop() },
    )

    init {
        scaleType = ScaleType.FIT_CENTER
        // Movie predates hardware Canvas support; only the Android 8 fallback uses it.
        if (Build.VERSION.SDK_INT < 28) setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val ticket = session.attach(canPlay())
        work = executor.submit {
            val image = runCatching { decode() }.getOrNull()
            post {
                if (session.decoded(ticket, image) && image == null) {
                    GifDebug.record("preview_decode_failed")
                    Toast.makeText(context, R.string.gif_preview_error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun canPlay() = isShown && windowVisibility == View.VISIBLE && hasWindowFocus()
    private fun refreshPlayback() { if (isAttachedToWindow) session.visibility(canPlay()) }
    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isAttachedToWindow) session.visibility(isVisible && canPlay())
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        refreshPlayback()
    }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        refreshPlayback()
    }
    override fun onDetachedFromWindow() {
        session.detach()
        work?.cancel(true)
        work = null
        super.onDetachedFromWindow()
    }

    @Suppress("DEPRECATION")
    private fun decode(): Drawable {
        if (Build.VERSION.SDK_INT >= 28) {
            return ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, info, _ ->
                val longest = maxOf(info.size.width, info.size.height)
                val limit = KineticaConstants.GIF_PREVIEW_DECODE_PX
                if (longest > limit) decoder.setTargetSize(
                    (info.size.width.toLong() * limit / longest).toInt().coerceAtLeast(1),
                    (info.size.height.toLong() * limit / longest).toInt().coerceAtLeast(1))
            }.also {
                check(it is AnimatedImageDrawable)
                it.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
            }
        }
        val movie = file.inputStream().use { Movie.decodeStream(it) }
        check(movie != null && movie.width() > 0 && movie.height() > 0)
        return MoviePreview(movie)
    }

    /** Android 8/8.1 compatibility, with no extra decoding library or WebView. */
    @Suppress("DEPRECATION")
    private class MoviePreview(private val movie: Movie) : Drawable(), Animatable {
        private var running = false
        private var epoch = 0L
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val tick = Runnable { if (running) invalidateSelf() }
        override fun start() {
            if (running) return
            running = true
            epoch = SystemClock.uptimeMillis()
            invalidateSelf()
        }
        override fun stop() { running = false; unscheduleSelf(tick) }
        override fun isRunning() = running
        override fun getIntrinsicWidth() = movie.width()
        override fun getIntrinsicHeight() = movie.height()
        override fun draw(canvas: Canvas) {
            if (running) {
                val duration = movie.duration().takeIf { it > 0 } ?: KineticaConstants.GIF_PREVIEW_FALLBACK_DURATION_MS
                movie.setTime(((SystemClock.uptimeMillis() - epoch) % duration).toInt())
            }
            val save = canvas.save()
            canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
            canvas.scale(bounds.width().toFloat() / movie.width(), bounds.height().toFloat() / movie.height())
            movie.draw(canvas, 0f, 0f, paint)
            canvas.restoreToCount(save)
            if (running) {
                unscheduleSelf(tick)
                scheduleSelf(tick, SystemClock.uptimeMillis() + KineticaConstants.GIF_PREVIEW_FRAME_MS)
            }
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
        @Deprecated("Drawable opacity") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    companion object {
        private val executor = Executors.newSingleThreadExecutor { Thread(it, "gif-animation").apply { isDaemon = true } }
    }
}
