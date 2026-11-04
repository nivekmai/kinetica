package com.kinetica.keyboard.settings

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Handler
import android.os.Looper
import androidx.preference.PreferenceManager
import com.kinetica.keyboard.ime.TypingHistory
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executors

/** Serial reads/writes and deletion; only aggregate counts in a non-backed-up local database. */
object TypingStatsStore {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var helper: Db? = null // Worker-owned.
    private var writeFailed = false
    private fun db(context: Context): SQLiteDatabase {
        if (helper == null) helper = Db(context.applicationContext)
        return helper!!.writableDatabase
    }
    fun enabled(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean(Prefs.TYPING_HISTORY, true)

    fun add(context: Context, sample: TypingHistory.Sample, wallMs: Long = System.currentTimeMillis()) {
        val app = context.applicationContext
        val local = Instant.ofEpochMilli(wallMs).atZone(ZoneId.systemDefault())
        val day = local.toLocalDate().toString()
        val hour = local.hour
        worker.execute {
            writeFailed = runCatching {
                val database = db(app)
                database.beginTransaction()
                try {
                    database.execSQL("INSERT OR IGNORE INTO hours(day,hour,chars,active_ms,peak) VALUES(?,?,0,0,0)", arrayOf(day, hour))
                    database.execSQL("UPDATE hours SET chars=chars+?,active_ms=active_ms+?,peak=MAX(peak,?) WHERE day=? AND hour=?",
                        arrayOf(sample.chars, sample.activeMs, sample.peakWpm, day, hour))
                    database.setTransactionSuccessful()
                } finally { database.endTransaction() }
            }.isFailure
        }
    }

    fun read(context: Context, since: LocalDate, until: LocalDate, done: (List<TypingHistory.Bucket>?) -> Unit) {
        val app = context.applicationContext
        worker.execute {
            val result = runCatching {
                check(!writeFailed)
                db(app).rawQuery("SELECT day,hour,chars,active_ms,peak FROM hours WHERE day>=? AND day<=? ORDER BY day,hour",
                    arrayOf(since.toString(), until.toString())).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) add(TypingHistory.Bucket(cursor.getString(0), cursor.getInt(1),
                            cursor.getLong(2), cursor.getLong(3), cursor.getDouble(4)))
                    }
                }
            }.getOrNull()
            main.post { done(result) }
        }
    }

    fun clear(context: Context, done: (Boolean) -> Unit) {
        val app = context.applicationContext
        worker.execute {
            val success = runCatching { db(app).delete("hours", null, null); writeFailed = false }.isSuccess
            main.post { done(success) }
        }
    }

    private class Db(context: Context) : SQLiteOpenHelper(context,
        File(context.noBackupFilesDir, "typing-stats.db").absolutePath, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE hours(day TEXT NOT NULL,hour INTEGER NOT NULL,chars INTEGER NOT NULL,active_ms INTEGER NOT NULL,peak REAL NOT NULL,PRIMARY KEY(day,hour))")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}
