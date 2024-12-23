package com.kdroid.seforim.utils

fun String.removeHebrewNikud(): String {
    val hebrewVowelRegex = Regex("[\\u0591-\\u05C7]")
    return hebrewVowelRegex.replace(this, "")
}
