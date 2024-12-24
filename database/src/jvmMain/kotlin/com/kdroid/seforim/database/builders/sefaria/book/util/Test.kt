package com.kdroid.seforim.database.builders.sefaria.book.util

import com.kdroid.seforim.utils.*

fun main(){
    val indexTitle = "Rashi on Horayot"
    val ref = "Leviticus 6:2"
        .removePrefix(indexTitle)
        .trim()
        .normalizeRef()
        .removeLastSegment()
    val guemeraRef = ref.extractNumberAndLetterAsString()
    println(guemeraRef)
    val line = ref.removePrefix(guemeraRef ?: "").substringAfterLast(":").toIntOrNull() ?: 0
    if (guemeraRef != null) {
        println(guemeraRef.toGuemaraNumber()!!.toGuemaraInt())
    }
    println(ref.substringBefore(":")) //62
}