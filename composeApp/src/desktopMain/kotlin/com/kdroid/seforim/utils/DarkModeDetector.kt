package com.kdroid.seforim.utils

import com.jthemedetecor.OsThemeDetector

object DarkModeDetector {
    private val detector = OsThemeDetector.getDetector()

    val isDarkThemeUsed: Boolean
        get() = detector.isDark

    fun registerListener(listener: (Boolean) -> Unit) {
        detector.registerListener { isDark -> listener(isDark) }
    }
}