package com.magicbill.app.core

/** All timers share one clock, so a test can move time and "4 min ago" is one truth. */
fun interface Clock {
    fun now(): Long

    companion object {
        val system: Clock = Clock { System.currentTimeMillis() }
    }
}
