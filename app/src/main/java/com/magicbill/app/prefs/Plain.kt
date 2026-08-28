package com.magicbill.app.prefs

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Small, unsecret conveniences: the theme, the chosen restaurant, "last synced" marks. */
@Singleton
class Plain @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences by lazy { context.getSharedPreferences("mb.plain", Context.MODE_PRIVATE) }

    /** Loads the file off the main thread at start-up, so the theme's first read is from memory. */
    fun warm() {
        prefs.all
    }

    fun get(key: String): String? = prefs.getString(key, null)
    fun put(key: String, value: String) = prefs.edit().putString(key, value).apply()
    fun remove(key: String) = prefs.edit().remove(key).apply()
    fun getLong(key: String): Long = prefs.getLong(key, 0L)
    fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
    fun getBool(key: String, default: Boolean = false): Boolean = prefs.getBoolean(key, default)
    fun putBool(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()

    companion object {
        const val THEME = "theme"               // system | light | dark
        const val RESTAURANT = "restaurant.id"  // the one the owner is looking at
        const val CRASH_OPT_IN = "crash.opt_in"
        const val TEXT_SIZE = "text.size"       // 1.0 .. 1.3
        const val PHONE_NAME = "phone.name"
    }
}
