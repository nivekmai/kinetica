package com.kinetica.keyboard.keys

/** Editor-native movements preserve visual lines, Unicode and the editor's selection anchor. */
enum class NavigationMove(private val key: String, private val word: Boolean = false) {
    UP("up"), DOWN("down"), LEFT("left"), RIGHT("right"),
    WORD_LEFT("left", true), WORD_RIGHT("right", true),
    LINE_START("home"), LINE_END("end");

    fun combo(selecting: Boolean) = KeyCombo(ctrl = word, shift = selecting, alt = false, key = key)
}
