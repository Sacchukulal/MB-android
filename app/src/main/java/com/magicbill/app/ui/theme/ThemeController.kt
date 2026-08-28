package com.magicbill.app.ui.theme

import com.magicbill.app.prefs.Plain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The theme setting: follow the phone, or one of the two. Persisted. Light + dark both ship. */
@Singleton
class ThemeController @Inject constructor(private val plain: Plain) {
    private val modeState = MutableStateFlow(plain.get(Plain.THEME) ?: SYSTEM)
    private val scaleState = MutableStateFlow(plain.get(Plain.TEXT_SIZE)?.toFloatOrNull() ?: 1f)

    val mode: StateFlow<String> get() = modeState
    val textScale: StateFlow<Float> get() = scaleState

    fun setMode(mode: String) {
        modeState.value = mode
        plain.put(Plain.THEME, mode)
    }

    fun setTextScale(scale: Float) {
        val s = scale.coerceIn(0.9f, 1.3f)
        scaleState.value = s
        plain.put(Plain.TEXT_SIZE, s.toString())
    }

    companion object {
        const val SYSTEM = ThemeControllerModes.SYSTEM
        const val LIGHT = ThemeControllerModes.LIGHT
        const val DARK = ThemeControllerModes.DARK
    }
}
