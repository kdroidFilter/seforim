package com.kdroid.seforim

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform