package com.xiaodlb.tdmk

import androidx.compose.ui.graphics.luminance
import com.xiaodlb.tdmk.data.Appearance
import org.junit.Assert.*
import org.junit.Test

class AppThemeTest {
    @Test fun savedChoiceResolvesIndependentlyFromCurrentSystem() {
        for (systemDark in listOf(false,true)) {
            assertEquals(systemDark,Appearance.SYSTEM.resolveDark(systemDark))
            assertFalse(Appearance.LIGHT.resolveDark(systemDark)); assertTrue(Appearance.DARK.resolveDark(systemDark))
        }
        assertEquals("system",Appearance.SYSTEM.storedValue)
    }
    @Test fun currentTextControlAndSelectedRolesHaveReadableContrast() {
        for (scheme in listOf(LightColors,DarkColors)) {
            for ((foreground,background) in listOf(scheme.onSurface to scheme.surface,scheme.onSurfaceVariant to scheme.surface,
                scheme.onPrimary to scheme.primary,scheme.onSecondaryContainer to scheme.secondaryContainer,scheme.error to scheme.surface)) {
                val first=foreground.luminance(); val second=background.luminance()
                assertTrue("Text role contrast must be >=4.5",(maxOf(first,second)+0.05f)/(minOf(first,second)+0.05f)>=4.5f)
            }
        }
    }
}
