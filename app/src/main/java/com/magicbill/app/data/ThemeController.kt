package com.magicbill.app.data

import com.magicbill.app.data.prefs.SecurePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** App theme mode. Dark is the default (dim restaurants); light is opt-in. */
@Singleton
class ThemeController @Inject constructor(
    private val prefs: SecurePrefs,
) {
    private val _dark = MutableStateFlow(prefs.getString(SecurePrefs.THEME_MODE) != "light")
    val dark: StateFlow<Boolean> = _dark

    fun setDark(dark: Boolean) {
        _dark.value = dark
        prefs.putString(SecurePrefs.THEME_MODE, if (dark) "dark" else "light")
    }
}
