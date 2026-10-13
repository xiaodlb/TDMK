package com.xiaodlb.tdmk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import com.xiaodlb.tdmk.data.Appearance

@Composable
internal fun AppearanceControls(saved: Appearance, enabled: Boolean, error: Boolean, onSelect: (Appearance) -> Unit) {
    Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    Text(stringResource(R.string.appearance_hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(ShellSpacing.gap / 2)) {
        Appearance.entries.forEach { mode ->
            Row(Modifier.fillMaxWidth().heightIn(min = ShellSpacing.control).testTag("appearance_${mode.storedValue}")
                .clip(RoundedCornerShape(ShellSpacing.corner))
                .background(if (saved == mode) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .selectable(selected = saved == mode, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(mode) })
                .padding(ShellSpacing.gap), horizontalArrangement = Arrangement.spacedBy(ShellSpacing.gap), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(selected = saved == mode, onClick = null, enabled = enabled, modifier = Modifier.clearAndSetSemantics { })
                Text(stringResource(when(mode) { Appearance.SYSTEM -> R.string.appearance_system; Appearance.LIGHT -> R.string.appearance_light; Appearance.DARK -> R.string.appearance_dark }), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
    if (error) Text(stringResource(R.string.appearance_save_error), color = MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag("appearance_error").semantics { liveRegion = LiveRegionMode.Polite })
}
