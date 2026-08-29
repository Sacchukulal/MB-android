package com.magicbill.app.ui.kit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.magicbill.app.ui.theme.MBMotion
import com.magicbill.app.ui.theme.Mb

/** One tab: its outlined icon, the filled one it becomes when chosen, and a dot for news. */
data class PillNavItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector = icon, val showDot: Boolean = false)

/**
 * The floating pill navigation — detached from the edge, rounded, softly raised. Selection
 * animates: the icon fills and pops on a spring, a small dot slides in under the active tab.
 * Five tabs, always; the labels are the nav size, so five fit on a narrow phone.
 */
@Composable
fun PillNavBar(items: List<PillNavItem>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val c = Mb.colors
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .shadow(18.dp, RoundedCornerShape(30.dp), spotColor = c.bg)
                .background(c.raised, RoundedCornerShape(30.dp))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val scale by animateFloatAsState(if (selected) 1f else 0.92f, MBMotion.bouncy(), label = "navScale")
                val tint by animateColorAsState(if (selected) c.accent else c.inkMuted, label = "navTint")
                val dotSize by animateDpAsState(if (selected) 5.dp else 0.dp, MBMotion.snappy(), label = "navDot")
                Column(
                    Modifier.weight(1f).height(56.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (index != selectedIndex) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onSelect(index)
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box {
                        Icon(if (selected) item.selectedIcon else item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(24.dp).scale(scale))
                        if (item.showDot) Box(Modifier.align(Alignment.TopEnd).size(7.dp).background(c.warn, CircleShape))
                    }
                    Text(item.label, style = Mb.type.navLabel, color = tint, maxLines = 1, overflow = TextOverflow.Clip, softWrap = false)
                    Box(Modifier.padding(top = 2.dp).size(dotSize).background(c.accent, CircleShape))
                }
            }
        }
    }
}
