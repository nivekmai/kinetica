package com.kinetica.keyboard.voice

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.kinetica.keyboard.R
import java.io.File
import java.util.concurrent.Executors

/** DownloadManager owns the network request. Only verified files become active, never transcripts. */
object VoiceModelStore {
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile var working = false
        private set
    @Volatile var message = 0
        private set
    @Volatile private var generation = 0L
    private fun prefs(c: Context) = c.getSharedPreferences("voice_models", Context.MODE_PRIVATE)
    private fun manager(c: Context) = c.getSystemService(DownloadManager::class.java)
    fun file(c: Context, model: VoiceModel) = File(File(c.noBackupFilesDir, "voice-models"), "${model.id}.gguf")
    fun imported(c: Context) = File(c.noBackupFilesDir, "voice-model.gguf")
    fun active(c: Context): File {
        val model = VoiceModels.all.firstOrNull { it.id == prefs(c).getString("selected", null) }
        return model?.let { file(c, it) }?.takeIf { it.isFile } ?: imported(c)
    }
    fun select(c: Context, model: VoiceModel?) {
        LocalCleanup.cancel()
        message = 0
        prefs(c).edit().putString("selected", model?.id).apply()
    }
    fun removeActive(c: Context) {
        if (busy(c)) return
        LocalCleanup.cancel()
        if (!active(c).delete()) message = R.string.voice_remove_failed else message = 0
    }
    fun pending(c: Context) = prefs(c).getLong("download", -1)
    fun busy(c: Context) = working || pending(c) != -1L

    fun download(c: Context, model: VoiceModel, wifiOnly: Boolean) {
        if (busy(c)) return
        try {
            val destination = File(c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "${model.id}.part")
            destination.delete()
            val request = DownloadManager.Request(Uri.parse(model.url))
                .setTitle(model.name).setDescription(c.getString(R.string.voice_download_description))
                .setDestinationInExternalFilesDir(c, Environment.DIRECTORY_DOWNLOADS, destination.name)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(!wifiOnly).setAllowedOverRoaming(false)
            if (wifiOnly) request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
            val id = manager(c).enqueue(request)
            prefs(c).edit().putLong("download", id).putString("pending_model", model.id).apply()
            message = 0
        } catch (_: Exception) { message = R.string.voice_download_failed }
    }

    /** Called while setup is visible; completed downloads are verified on the worker, including after restart. */
    fun progress(c: Context): String? {
        if (working) return c.getString(R.string.voice_verifying)
        val id = pending(c)
        if (id == -1L) return if (message == 0) null else c.getString(message)
        try {
            manager(c).query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                if (!cursor.moveToFirst()) { failed(c); return c.getString(message) }
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val model = VoiceModels.all.firstOrNull { it.id == prefs(c).getString("pending_model", null) }
                    if (model == null) failed(c) else installDownload(c.applicationContext, id, model)
                    return c.getString(if (working) R.string.voice_verifying else message)
                }
                if (status == DownloadManager.STATUS_FAILED) { failed(c); return c.getString(message) }
                if (status == DownloadManager.STATUS_PAUSED || status == DownloadManager.STATUS_PENDING)
                    return c.getString(R.string.voice_download_waiting)
                val bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                return c.getString(R.string.voice_download_progress, bytes / 1000000, total.coerceAtLeast(0) / 1000000)
            }
        } catch (_: Exception) { failed(c); return c.getString(message) }
    }

    private fun installDownload(c: Context, id: Long, model: VoiceModel) {
        working = true
        val ticket = generation
        executor.execute {
            var temp: File? = null
            try {
                val dir = file(c, model).parentFile!!
                dir.mkdirs()
                dir.listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach { it.delete() }
                temp = File.createTempFile("download-", ".tmp", dir)
                manager(c).openDownloadedFile(id).use { descriptor ->
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                        temp.outputStream().use { VoiceModels.copyVerified(input, it, model) { generation != ticket } }
                    }
                }
                synchronized(this) {
                    check(generation == ticket)
                    LocalCleanup.cancel()
                    check(temp.renameTo(file(c, model)))
                    select(c, model)
                    message = R.string.voice_download_ready
                }
            } catch (_: Exception) {
                if (generation == ticket) message = R.string.voice_download_failed
            } finally {
                temp?.delete()
                runCatching { manager(c).remove(id) }
                if (pending(c) == id) prefs(c).edit().remove("download").remove("pending_model").apply()
                working = false
            }
        }
    }

    @Synchronized fun cancel(c: Context) {
        generation++
        val id = pending(c)
        if (id != -1L) runCatching { manager(c).remove(id) }
        prefs(c).edit().remove("download").remove("pending_model").apply()
        message = R.string.voice_download_cancelled
    }
    private fun failed(c: Context) { cancel(c); message = R.string.voice_download_failed }

    fun import(c: Context, uri: Uri) {
        if (busy(c)) return
        working = true
        val ticket = generation
        message = R.string.voice_importing
        val app = c.applicationContext
        executor.execute {
            var temp: File? = null
            try {
                app.noBackupFilesDir.listFiles()?.filter {
                    it.name.startsWith("voice-import-") && it.name.endsWith(".tmp")
                }?.forEach { it.delete() }
                temp = File.createTempFile("voice-import-", ".tmp", app.noBackupFilesDir)
                app.contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { VoiceModels.copyVerified(input, it) { generation != ticket } }
                } ?: error("No input")
                synchronized(this) {
                    check(generation == ticket)
                    LocalCleanup.cancel()
                    check(temp.renameTo(imported(app)))
                    select(app, null)
                    message = R.string.voice_model_ready
                }
            } catch (_: Exception) {
                if (generation == ticket) message = R.string.voice_import_failed
            } finally { temp?.delete(); working = false }
        }
    }
}
