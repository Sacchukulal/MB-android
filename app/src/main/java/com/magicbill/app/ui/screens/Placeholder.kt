package com.magicbill.app.ui.screens

import androidx.compose.runtime.Composable
import com.magicbill.app.ui.kit.Empty
import com.magicbill.app.ui.kit.Page

/** A screen whose part has not landed yet. Deleted when the last one does. */
@Composable
fun Placeholder(title: String) {
    Page(title) { Empty("This screen is on its way.") }
}
