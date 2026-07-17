package com.magicbill.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.magicbill.app.core.PERMISSION_METAS
import com.magicbill.app.core.PERMISSION_PRESETS
import com.magicbill.app.core.PermissionMap

/**
 * The owner's permission toggles for one staff member: preset chips that
 * pre-fill, then a switch per permission. The whole staff experience is
 * assembled from these nine switches.
 */
@Composable
fun PermissionEditor(
    permissions: PermissionMap,
    onChange: (PermissionMap) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            "PERMISSIONS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PERMISSION_PRESETS.forEach { (label, preset) ->
                val active = PERMISSION_METAS.all { meta ->
                    (permissions[meta.key.key] == true) == (preset[meta.key.key] == true)
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(percent = 50),
                        )
                        .clickable { onChange(preset) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        PERMISSION_METAS.forEach { meta ->
            val checked = permissions[meta.key.key] == true
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        onChange(permissions + (meta.key.key to !checked))
                    }
                    .padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(meta.label, style = MaterialTheme.typography.bodyLarge)
                        if (meta.comingSoon) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Coming soon",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
                                        RoundedCornerShape(percent = 50),
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        meta.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = checked,
                    onCheckedChange = { onChange(permissions + (meta.key.key to it)) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }
    }
}
