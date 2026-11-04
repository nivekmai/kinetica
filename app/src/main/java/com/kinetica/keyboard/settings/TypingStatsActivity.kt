package com.kinetica.keyboard.settings

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.SwitchCompat
import androidx.preference.PreferenceManager
import com.kinetica.keyboard.R
import com.kinetica.keyboard.ime.TypingHistory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

class TypingStatsActivity : AppCompatActivity() {
    private lateinit var results: LinearLayout
    private var period = 30
    private var request = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.typing_stats_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        period = savedInstanceState?.getInt("period", 30)?.takeIf { it in listOf(7, 30, 0) } ?: 30
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(24))
        }
        setContentView(ScrollView(this).apply { addView(content) })
        content.addView(SwitchCompat(this).apply {
            setText(R.string.typing_stats_collect)
            isChecked = TypingStatsStore.enabled(this@TypingStatsActivity)
            setOnCheckedChangeListener { _, checked ->
                PreferenceManager.getDefaultSharedPreferences(this@TypingStatsActivity).edit()
                    .putBoolean(Prefs.TYPING_HISTORY, checked).apply()
            }
        })
        label(content, getString(R.string.typing_stats_local))
        val choices = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        for ((days, title) in listOf(7 to R.string.typing_stats_week, 30 to R.string.typing_stats_month, 0 to R.string.typing_stats_all)) {
            choices.addView(RadioButton(this).apply {
                id = View.generateViewId(); tag = days; setText(title)
                isChecked = period == days
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        choices.setOnCheckedChangeListener { group, id ->
            period = group.findViewById<RadioButton>(id).tag as Int
            refresh()
        }
        content.addView(choices)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(results)
        label(content, getString(R.string.typing_stats_method))
        content.addView(AppCompatButton(this).apply {
            setText(R.string.typing_stats_clear)
            setOnClickListener {
                AlertDialog.Builder(this@TypingStatsActivity)
                    .setTitle(R.string.typing_stats_clear)
                    .setMessage(R.string.typing_stats_clear_confirm)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.typing_stats_clear) { _, _ ->
                        request++
                        isEnabled = false
                        TypingStatsStore.clear(this@TypingStatsActivity) { success ->
                            if (!isFinishing && !isDestroyed) {
                                isEnabled = true
                                if (!success) Toast.makeText(this@TypingStatsActivity, R.string.typing_stats_error, Toast.LENGTH_LONG).show()
                                refresh()
                            }
                        }
                    }.show()
            }
        })
    }

    override fun onResume() { super.onResume(); refresh() }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("period", period); super.onSaveInstanceState(outState) }

    private fun refresh() {
        val ticket = ++request
        results.removeAllViews()
        label(results, getString(R.string.typing_stats_loading))
        val today = LocalDate.now()
        val since = if (period == 0) LocalDate.of(1970, 1, 1) else today.minusDays(period - 1L)
        TypingStatsStore.read(this, since, today) { rows ->
            if (ticket != request || isFinishing || isDestroyed) return@read
            results.removeAllViews()
            if (rows == null) { label(results, getString(R.string.typing_stats_error)); return@read }
            if (rows.isEmpty()) { label(results, getString(R.string.typing_stats_empty)); return@read }
            val totals = TypingHistory.summarize(rows)
            heading(results, getString(R.string.typing_stats_overview))
            label(results, getString(R.string.typing_stats_average, speed(totals.averageWpm)), 24f)
            label(results, getString(R.string.typing_stats_peak, if (totals.peakWpm > 0) speed(totals.peakWpm) else "—"), 18f)
            label(results, getString(R.string.typing_stats_active, duration(totals.activeMs)))
            label(results, getString(R.string.typing_stats_words, totals.chars / 5))
            heading(results, getString(R.string.typing_stats_time_of_day))
            val blocks = listOf(R.string.typing_stats_night, R.string.typing_stats_morning,
                R.string.typing_stats_afternoon, R.string.typing_stats_evening).mapIndexed { index, title ->
                getString(title) to TypingHistory.summarize(rows.filter { it.hour / 6 == index })
            }
            bars(blocks)
            heading(results, getString(R.string.typing_stats_daily))
            val daily = rows.groupBy { it.day }.toSortedMap().entries.toList().takeLast(14)
            val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            bars(daily.map { (day, buckets) -> LocalDate.parse(day).format(format) to TypingHistory.summarize(buckets) })
        }
    }

    private fun bars(values: List<Pair<String, TypingHistory.Summary>>) {
        val maximum = values.maxOfOrNull { it.second.averageWpm }?.coerceAtLeast(1.0) ?: 1.0
        for ((title, summary) in values) {
            val rate = if (summary.activeMs > 0) speed(summary.averageWpm) else "—"
            label(results, getString(R.string.typing_stats_row, title, rate, duration(summary.activeMs)))
            results.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 1000
                progress = (summary.averageWpm / maximum * 1000).roundToInt()
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(-1, dp(6)).apply { bottomMargin = dp(10) })
        }
    }
    private fun speed(wpm: Double) = getString(R.string.typing_stats_wpm, wpm)
    private fun duration(ms: Long): String {
        val seconds = (ms + 999) / 1000
        return getString(R.string.typing_stats_duration, seconds / 3600, seconds / 60 % 60, seconds % 60)
    }
    private fun heading(parent: LinearLayout, title: String) {
        label(parent, title, 20f).apply { setPadding(0, dp(20), 0, dp(8)) }
    }
    private fun label(parent: LinearLayout, text: String, size: Float = 15f) = TextView(this).apply {
        this.text = text; textSize = size
        setPadding(0, dp(6), 0, dp(6))
        parent.addView(this)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
