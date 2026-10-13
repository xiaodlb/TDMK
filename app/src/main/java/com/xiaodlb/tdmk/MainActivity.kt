package com.xiaodlb.tdmk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.xiaodlb.tdmk.data.Appearance
import kotlinx.coroutines.*

private data class AppearanceState(val saved: Appearance? = null, val pending: Boolean = false, val error: Boolean = false)

class MainActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var appearance by mutableStateOf(AppearanceState())
    private val provider get() = (application as TdmkApplication).databaseProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        observeAppearance()
        setContent {
            val state = appearance
            val dark = (state.saved ?: Appearance.SYSTEM).resolveDark(isSystemInDarkTheme())
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.BLACK),
                    navigationBarStyle = SystemBarStyle.dark(if (dark) 0xFF111418.toInt() else 0xFF333C45.toInt()),
                )
            }
            AppTheme(dark) {
                Surface(Modifier.fillMaxSize().testTag(if (dark) "theme_dark" else "theme_light")) {
                    if (state.saved != null) NavigationShell(state.saved, !state.pending, state.error, ::saveAppearance)
                    else Column(Modifier.safeDrawingPadding().padding(ShellSpacing.page), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (state.error) {
                            Text(stringResource(R.string.appearance_load_error))
                            Button(onClick = ::observeAppearance) { Text(stringResource(R.string.retry)) }
                        } else CircularProgressIndicator(Modifier.testTag("appearance_loading"))
                    }
                }
            }
        }
    }

    private fun observeAppearance() {
        appearance = appearance.copy(error = false)
        scope.launch {
            try {
                val dao = withContext(Dispatchers.IO) { provider.getDatabase().settings() }
                dao.observe().collect { appearance = appearance.copy(saved = it.appearance) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { appearance = appearance.copy(error = true) }
        }
    }

    private fun saveAppearance(value: Appearance) {
        if (appearance.pending || value == appearance.saved) return
        appearance = appearance.copy(pending = true, error = false)
        scope.launch {
            try {
                withContext(Dispatchers.IO) { check(provider.getDatabase().settings().setAppearance(value) == 1) }
                appearance = appearance.copy(saved = value, pending = false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { appearance = appearance.copy(pending = false, error = true) }
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
