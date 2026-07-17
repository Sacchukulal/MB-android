package com.magicbill.app.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.magicbill.app.R
import com.magicbill.app.ui.components.GlowBackground
import com.magicbill.app.ui.components.MBButton
import com.magicbill.app.ui.components.MBButtonVariant
import com.magicbill.app.ui.components.MBSnackbarHost
import com.magicbill.app.ui.components.MBSnackbarKind
import com.magicbill.app.ui.components.showMBSnackbar
import com.magicbill.app.ui.theme.Emerald
import com.magicbill.app.ui.theme.MBMotion

/**
 * First screen: glowing brand canvas, staggered entrance, two clear doors.
 */
@Composable
fun WelcomeScreen(
    revoked: Boolean,
    onAckRevoked: () -> Unit,
    onOwner: () -> Unit,
    onStaff: () -> Unit,
) {
    var entered by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { entered = true }
    LaunchedEffect(revoked) {
        if (revoked) {
            snackbar.showMBSnackbar(
                "Your access was turned off by the owner.",
                MBSnackbarKind.Error,
            )
            onAckRevoked()
        }
    }

    GlowBackground(Modifier.fillMaxSize(), intensity = 1.3f) {
        Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.9f))

            AnimatedVisibility(
                visible = entered,
                enter = scaleIn(tween(MBMotion.DurLong, easing = MBMotion.EaseOut), initialScale = 0.8f) +
                    fadeIn(tween(MBMotion.DurLong)),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(120.dp)
                            .background(Emerald.copy(alpha = 0.10f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painterResource(R.drawable.splashscreen_logo),
                            contentDescription = "Magic Bill",
                            modifier = Modifier.size(84.dp),
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                    Text("Magic Bill", style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Your restaurant, in your pocket.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            AnimatedVisibility(
                visible = entered,
                enter = slideInVertically(tween(MBMotion.DurLong, delayMillis = 150, easing = MBMotion.EaseOut)) { it / 3 } +
                    fadeIn(tween(MBMotion.DurLong, delayMillis = 150)),
            ) {
                Column {
                    MBButton(
                        "I'm the Owner",
                        onClick = onOwner,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    MBButton(
                        "I'm Staff",
                        onClick = onStaff,
                        variant = MBButtonVariant.Tonal,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "Staff access is set up by the restaurant owner.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
        }
        MBSnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
        )
        }
    }
}
