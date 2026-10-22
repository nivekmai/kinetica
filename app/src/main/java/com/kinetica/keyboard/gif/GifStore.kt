package com.kinetica.keyboard.gif

import android.content.Context
import com.kinetica.keyboard.engine.KineticaConstants
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Pending selection is deliberately memory-only and never inserts automatically. */
object GifTransfer {
    data class Pending(val id: String, val packageName: String, val fieldId: Int)
    var pending: Pending? = null
}

/** Locally cached media, and a most-recent-first index kept outside Android backup. */
class GifStore internal constructor(private val directory: File, private val index: File) {
    constructor(context: Context) : this(File(context.cacheDir, "gifs"), File(context.noBackupFilesDir, "gif-recents.txt"))
    init { directory.mkdirs() }
    fun file(id: String): File? = if (GifPolicy.fileId(id)) File(directory, "$id.gif").takeIf { it.isFile } else null

    fun recents(): List<String> = synchronized(lock) {
        runCatching { index.readLines().filter { file(it) != null }.distinct().take(KineticaConstants.GIF_RECENTS) }
            .getOrDefault(emptyList())
    }

    fun remember(id: String) = synchronized(lock) {
        if (file(id) != null) writeIndex(GifPolicy.recentIds(recents(), id))
    }

    // Remove only the index entry; a receiving app may still be reading its granted file.
    fun remove(id: String) = synchronized(lock) {
        writeIndex(recents().filterNot { it == id })
    }

    private fun writeIndex(ids: List<String>) {
        val temporary = File.createTempFile("gif-recents-", ".tmp", index.parentFile)
        try {
            temporary.writeText(ids.joinToString("\n"))
            if (!temporary.renameTo(index)) throw IOException("Cannot save GIF history")
        } finally { temporary.delete() }
    }

    fun touch(id: String) { file(id)?.setLastModified(System.currentTimeMillis()) }

    /** Worker thread only. A bounded, HTTPS-only transfer; no cookies or editor data attached. */
    fun download(address: String): String {
        val deadline = System.nanoTime() + KineticaConstants.GIF_TOTAL_TIMEOUT_MS * 1_000_000
        fun checkDeadline() {
            if (System.nanoTime() > deadline || Thread.currentThread().isInterrupted) throw IOException("Download timed out or cancelled")
        }
        var url = address
        repeat(6) {
            require(GifPolicy.httpsUrl(url)) { "HTTPS image required" }
            checkDeadline()
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = KineticaConstants.GIF_NETWORK_TIMEOUT_MS
                connection.readTimeout = KineticaConstants.GIF_NETWORK_TIMEOUT_MS
                connection.setRequestProperty("Accept", "image/gif")
                val code = connection.responseCode
                if (code in setOf(301, 302, 303, 307, 308)) {
                    val next = connection.getHeaderField("Location") ?: throw IOException("Redirect without a destination")
                    url = URL(URL(url), next).toString()
                } else {
                    if (code != 200) throw IOException("Image server returned $code")
                    if (connection.contentLengthLong > KineticaConstants.GIF_MAX_BYTES) throw IOException("GIF is too large")
                    val bytes = connection.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            checkDeadline()
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > KineticaConstants.GIF_MAX_BYTES) throw IOException("GIF is too large")
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    if (!GifPolicy.animatedGif(bytes)) throw IOException("Not an animated GIF")
                    val id = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                    synchronized(lock) {
                        prune()
                        val target = File(directory, "$id.gif")
                        if (!target.exists()) {
                            val size = directory.listFiles().orEmpty().sumOf { it.length() }
                            if (size + bytes.size > KineticaConstants.GIF_CACHE_BYTES) throw IOException("GIF cache is full; try again later")
                            val temporary = File.createTempFile("download-", ".tmp", directory)
                            try {
                                temporary.writeBytes(bytes)
                                if (!temporary.renameTo(target)) throw IOException("Cannot save GIF")
                            } finally { temporary.delete() }
                        }
                        touch(id)
                    }
                    return id
                }
            } finally { connection.disconnect() }
        }
        throw IOException("Too many redirects")
    }

    private fun prune() {
        val keep = recents().toSet() + listOfNotNull(GifTransfer.pending?.id)
        val cutoff = System.currentTimeMillis() - KineticaConstants.GIF_CACHE_RETENTION_MS
        directory.listFiles().orEmpty().filter {
            it.nameWithoutExtension !in keep && it.lastModified() < cutoff
        }.forEach { it.delete() }
    }

    companion object { private val lock = Any() }
}
