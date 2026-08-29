package com.magicbill.app.ui.theme

import com.magicbill.app.prefs.Plain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The theme setting: dark or light, nothing else. Dark is the default (dim restaurants); light is
 * opt-in, from the sun/moon on the Account screen. Persisted. There is no text-size setting: the
 * one scale in Tokens.kt is the product.
 */
@Singleton
class ThemeController @Inject constructor(private val plain: Plain) {
    private val darkState = MutableStateFlow(plain.get(Plain.THEME) != LIGHT)

    val dark: StateFlow<Boolean> get() = darkState

    fun setDark(dark: Boolean) {
        darkState.value = dark
        plain.put(Plain.THEME, if (dark) DARK else LIGHT)
    }

    fun toggle() = setDark(!darkState.value)

    companion object {
        const val LIGHT = "light"
        const val DARK = "dark"
    }
}
