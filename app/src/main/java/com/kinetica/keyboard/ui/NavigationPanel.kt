package com.kinetica.keyboard.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatButton
import com.kinetica.keyboard.R
import com.kinetica.keyboard.engine.KineticaConstants as K
import com.kinetica.keyboard.keys.EditorAction
import com.kinetica.keyboard.keys.NavigationMove

/** A latched editing layer. Selection is a per-event modifier, never a held system Shift key. */
class NavigationPanel(
    context: Context,
    private val onMove: (NavigationMove, Boolean) -> Unit,
    private val onEdit: (EditorAction) -> Unit,
    onClose: () -> Unit,
) : LinearLayout(context) {
    private val buttons = mutableListOf<Button>()
    private val repeatButtons = mutableListOf<MovementButton>()
    private val selectButton: Button
    private var colors: KeyboardTheme? = null
    var selecting = false
        private set

    init {
        orientation = VERTICAL
        val gap = (3 * resources.displayMetrics.density).toInt()
        setPadding(gap, gap, gap, gap)
        fun row() = LinearLayout(context).also {
            addView(it, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        }
        fun button(row: LinearLayout, label: Int, movement: NavigationMove? = null,
                   glyph: String? = null, click: () -> Unit): Button {
            val b = if (movement != null) MovementButton(context).also { repeatButtons.add(it) } else Button(context)
            b.text = glyph ?: context.getString(label)
            b.contentDescription = context.getString(label)
            b.isAllCaps = false
            b.isFocusable = false
            b.minWidth = 0
            b.minimumWidth = 0
            b.minHeight = 0
            b.minimumHeight = 0
            b.setPadding(gap, 0, gap, 0)
            b.setAutoSizeTextTypeUniformWithConfiguration(10, if (glyph != null) 26 else 14, 1,
                android.util.TypedValue.COMPLEX_UNIT_SP)
            b.setOnClickListener { click() }
            row.addView(b, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { setMargins(gap, gap, gap, gap) })
            buttons.add(b)
            return b
        }
        fun move(row: LinearLayout, label: Int, movement: NavigationMove, glyph: String? = null) =
            button(row, label, movement, glyph) { onMove(movement, selecting) }
        fun edit(row: LinearLayout, label: Int, action: EditorAction) = button(row, label) {
            onEdit(action)
            setSelecting(false)
        }
        val top = row()
        move(top, R.string.nav_line_start, NavigationMove.LINE_START)
        move(top, R.string.nav_word_left, NavigationMove.WORD_LEFT)
        move(top, R.string.nav_up, NavigationMove.UP, "↑")
        move(top, R.string.nav_word_right, NavigationMove.WORD_RIGHT)
        move(top, R.string.nav_line_end, NavigationMove.LINE_END)
        val middle = row()
        edit(middle, R.string.nav_copy, EditorAction.COPY)
        move(middle, R.string.nav_left, NavigationMove.LEFT, "←")
        selectButton = button(middle, R.string.nav_select) { setSelecting(!selecting) }
        move(middle, R.string.nav_right, NavigationMove.RIGHT, "→")
        edit(middle, R.string.nav_paste, EditorAction.PASTE)
        val bottom = row()
        edit(bottom, R.string.nav_cut, EditorAction.CUT)
        edit(bottom, R.string.nav_select_all, EditorAction.SELECT_ALL)
        move(bottom, R.string.nav_down, NavigationMove.DOWN, "↓")
        edit(bottom, R.string.nav_undo, EditorAction.UNDO)
        edit(bottom, R.string.nav_redo, EditorAction.REDO)
        button(row(), R.string.nav_keyboard) { stopRepeating(); onClose() }
    }

    private fun setSelecting(value: Boolean) {
        selecting = value
        selectButton.isSelected = value
        selectButton.text = context.getString(if (value) R.string.nav_selecting else R.string.nav_select)
        selectButton.contentDescription = selectButton.text
        colors?.let { applyTheme(it) }
    }

    fun applyTheme(theme: KeyboardTheme) {
        colors = theme
        setBackgroundColor(theme.background)
        for (button in buttons) {
            button.setTextColor(theme.keyText)
            fun shape(color: Int) = GradientDrawable().apply {
                setColor(color)
                cornerRadius = 6 * resources.displayMetrics.density
                if (button.isSelected) setStroke((2 * resources.displayMetrics.density).toInt(), theme.accent)
            }
            button.background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), shape(theme.keyPressed))
                addState(intArrayOf(), shape(if (button.isSelected) theme.keyPressed else theme.key))
            }
        }
    }

    fun stopRepeating() { repeatButtons.forEach { it.stopRepeating() } }

    override fun onDetachedFromWindow() {
        stopRepeating()
        super.onDetachedFromWindow()
    }

    private class MovementButton(context: Context) : AppCompatButton(context) {
        private var repeated = false
        private val repeat = object : Runnable {
            override fun run() {
                if (!isPressed || !isShown) return
                repeated = true
                performClick()
                postDelayed(this, K.NAV_REPEAT_INTERVAL_MS)
            }
        }
        fun stopRepeating() { removeCallbacks(repeat); isPressed = false }
        override fun performClick(): Boolean = super.performClick()
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    repeated = false
                    removeCallbacks(repeat)
                    postDelayed(repeat, K.NAV_REPEAT_DELAY_MS)
                }
                MotionEvent.ACTION_MOVE -> if (event.x < 0 || event.x >= width || event.y < 0 || event.y >= height) {
                    stopRepeating()
                }
                MotionEvent.ACTION_CANCEL -> stopRepeating()
                MotionEvent.ACTION_UP -> {
                    removeCallbacks(repeat)
                    if (repeated) { isPressed = false; return true }
                }
            }
            return super.onTouchEvent(event)
        }
    }
}
