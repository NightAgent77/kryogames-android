package com.kryogames.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface BrowserRow {
    data object UseFolder : BrowserRow
    data class Open(val dir: File) : BrowserRow
}

/** In-app folder list so D-pad, stick, A, and B keep working while a game folder is chosen. */
@Composable
internal fun FolderBrowser(onChoose: (File) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(canBrowseDeviceFolders()) }
    var current by remember { mutableStateOf<File?>(null) }
    var listed by remember { mutableStateOf<List<File>?>(null) }
    val activity = context.hostActivity() as? ComponentActivity
    DisposableEffect(activity) {
        if (activity == null) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = canBrowseDeviceFolders()
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(current, allowed) {
        listed = null
        if (!allowed) return@LaunchedEffect
        val directory = current
        listed = withContext(Dispatchers.IO) {
            if (directory == null) storageRoots() else childDirectories(directory)
        }
    }
    BackHandler(enabled = current != null) { current = folderAbove(current ?: return@BackHandler) }

    val rows = buildList {
        if (allowed && listed != null && current != null) add(BrowserRow.UseFolder)
        if (allowed && listed != null) listed.orEmpty().forEach { add(BrowserRow.Open(it)) }
    }
    val rowFocus = remember(current, rows) { List(rows.size) { FocusRequester() } }
    val allowFocus = remember { FocusRequester() }
    LaunchedEffect(allowed, current, rows) {
        withFrameNanos { }
        if (!allowed) allowFocus.requestFocus()
        else if (rows.isNotEmpty()) rowFocus.firstOrNull()?.requestFocus()
    }

    Column(
        Modifier.fillMaxSize().background(KryoColors.Background).padding(horizontal = 28.dp, vertical = 22.dp).testTag("folder_browser"),
    ) {
        Text("Choose a game folder", color = KryoColors.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            current?.absolutePath ?: "Storage",
            color = KryoColors.Muted,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(16.dp))
        if (!allowed) {
            Text(
                "KryoGames needs access to folders on this device. Allow it, then come back.",
                color = KryoColors.Muted,
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
            Spacer(Modifier.height(16.dp))
            FocusBox(
                Modifier.fillMaxWidth().height(48.dp).focusRequester(allowFocus).focusProperties {
                    up = FocusRequester.Cancel
                    down = FocusRequester.Cancel
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                },
                "Allow storage access",
                outlined = true,
                onClick = { openStorageAccess(context) },
            ) {
                Text("Allow storage access", color = KryoColors.Text, fontSize = 15.sp)
            }
        } else if (listed == null) {
            Text("Looking for folders…", color = KryoColors.Muted, fontSize = 15.sp)
        } else {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (rows.isEmpty()) {
                    Text("No folders in here.", color = KryoColors.Muted, fontSize = 15.sp)
                }
                rows.forEachIndexed { index, row ->
                    val label = when (row) {
                        BrowserRow.UseFolder -> "Use this folder"
                        is BrowserRow.Open -> if (current == null) folderLabel(row.dir) else row.dir.name
                    }
                    FocusBox(
                        Modifier.fillMaxWidth().height(48.dp).focusRequester(rowFocus[index]).focusProperties {
                            up = rowFocus.getOrElse(index - 1) { FocusRequester.Cancel }
                            down = rowFocus.getOrElse(index + 1) { FocusRequester.Cancel }
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        },
                        label,
                        outlined = true,
                        onClick = {
                            when (row) {
                                BrowserRow.UseFolder -> current?.let(onChoose)
                                is BrowserRow.Open -> current = row.dir
                            }
                        },
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (row is BrowserRow.Open) {
                                Icon(Icons.Outlined.Folder, null, tint = KryoColors.Muted, modifier = Modifier.size(18.dp))
                            }
                            Text(label, color = KryoColors.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

private fun openStorageAccess(context: Context) {
    val specific = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    )
    try {
        context.startActivity(specific)
    } catch (_: ActivityNotFoundException) {
        runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
    }
}

private tailrec fun Context.hostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.hostActivity()
    else -> null
}
