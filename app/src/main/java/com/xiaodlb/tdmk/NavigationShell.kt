package com.xiaodlb.tdmk

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private enum class MainTab(@param:StringRes val label: Int, @param:DrawableRes val icon: Int, @param:StringRes val description: Int) {
    Home(R.string.home, R.drawable.ic_home, R.string.home_placeholder),
    Tasks(R.string.tasks, R.drawable.ic_tasks, R.string.tasks_placeholder),
    Notes(R.string.notes, R.drawable.ic_notes, R.string.notes_placeholder),
    Log(R.string.log, R.drawable.ic_log, R.string.log_placeholder),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationShell() {
    var route by rememberSaveable { mutableStateOf(MainTab.Home.name) }
    val settings = route == "Settings"
    val tab = if (settings) MainTab.Home else MainTab.valueOf(route)
    BackHandler(enabled = route != MainTab.Home.name) { route = MainTab.Home.name }
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                title = { Text(stringResource(if (settings) R.string.settings else R.string.app_name)) },
                navigationIcon = {
                    if (settings) IconButton(onClick = { route = MainTab.Home.name }) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back_home))
                    }
                },
                actions = {
                    if (!settings && tab == MainTab.Home) TextButton(onClick = { route = "Settings" }, modifier = Modifier.testTag("open_settings")) {
                        Text(stringResource(R.string.settings))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)) {
                MainTab.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = tab == destination,
                        onClick = { route = destination.name },
                        modifier = Modifier.testTag("tab_${destination.name}"),
                        icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                        alwaysShowLabel = true,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .testTag("screen_$route").verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(if (settings) R.string.settings else tab.label),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(if (settings) R.string.settings_placeholder else tab.description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
