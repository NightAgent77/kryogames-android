package com.kryogames.app

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AccountAvatar(dataUrl: String?, modifier: Modifier) {
    val bitmap by produceState(initialValue = null as androidx.compose.ui.graphics.ImageBitmap?, dataUrl) {
        value = withContext(Dispatchers.Default) { decodeAvatar(dataUrl) }
    }
    val image = bitmap
    if (image != null) {
        Image(image, null, modifier, contentScale = ContentScale.Crop)
    } else {
        Image(painterResource(R.drawable.profile_avatar), null, modifier, contentScale = ContentScale.Crop)
    }
}

@Composable
internal fun AuthOverlay(
    mode: AuthMode,
    busy: Boolean,
    error: String?,
    onMode: (AuthMode) -> Unit,
    onSubmit: (email: String, password: String, username: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val emailFocus = remember { FocusRequester() }
    val usernameFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val primaryFocus = remember { FocusRequester() }
    val switchFocus = remember { FocusRequester() }
    val extraFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(mode) { emailFocus.bringIntoFocus() }
    val title = when (mode) {
        AuthMode.SignIn -> "Sign in"
        AuthMode.SignUp -> "Create account"
        AuthMode.Forgot -> "Reset password"
    }
    val primary = when (mode) {
        AuthMode.SignIn -> "Sign in"
        AuthMode.SignUp -> "Create account"
        AuthMode.Forgot -> "Send reset email"
    }
    AccountScrim(onDismiss) {
        Text(title, color = KryoColors.Text, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Same account as kryogames.com.", color = KryoColors.Muted, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        AccountField(email, { email = it }, "Email", emailFocus, emailFocus, if (mode == AuthMode.SignUp) usernameFocus else if (mode == AuthMode.Forgot) primaryFocus else passwordFocus, KeyboardType.Email)
        if (mode == AuthMode.SignUp) {
            Spacer(Modifier.height(8.dp))
            AccountField(username, { username = it }, "Username", usernameFocus, emailFocus, passwordFocus, KeyboardType.Text)
        }
        if (mode != AuthMode.Forgot) {
            Spacer(Modifier.height(8.dp))
            AccountField(password, { password = it }, "Password", passwordFocus, if (mode == AuthMode.SignUp) usernameFocus else emailFocus, primaryFocus, KeyboardType.Password, hidden = true)
        }
        val message = localError ?: error
        if (message != null) {
            Spacer(Modifier.height(10.dp))
            Text(message, color = KryoColors.Danger, fontSize = 14.sp)
        }
        Spacer(Modifier.height(16.dp))
        AccountButton(primary, primaryFocus, if (mode == AuthMode.Forgot) emailFocus else passwordFocus, switchFocus, busy, "auth_submit") {
            val problem = validateCredentials(mode, email, password, username)
            localError = problem
            if (problem == null && !busy) onSubmit(email.trim(), password, username.trim())
        }
        Spacer(Modifier.height(8.dp))
        when (mode) {
            AuthMode.SignIn -> {
                AccountButton("Create account", switchFocus, primaryFocus, extraFocus, busy) { onMode(AuthMode.SignUp) }
                Spacer(Modifier.height(8.dp))
                AccountButton("Forgot password", extraFocus, switchFocus, closeFocus, busy) { onMode(AuthMode.Forgot) }
            }
            AuthMode.SignUp -> AccountButton("Sign in", switchFocus, primaryFocus, closeFocus, busy) { onMode(AuthMode.SignIn) }
            AuthMode.Forgot -> AccountButton("Back to sign in", switchFocus, primaryFocus, closeFocus, busy) { onMode(AuthMode.SignIn) }
        }
        Spacer(Modifier.height(8.dp))
        val closeUp = if (mode == AuthMode.SignIn) extraFocus else switchFocus
        AccountButton("Close", closeFocus, closeUp, closeFocus, false) { onDismiss() }
    }
}

@Composable
internal fun ProfileOverlay(
    account: KryoAccount,
    busy: Boolean,
    error: String?,
    onSave: (ProfileDraft) -> Unit,
    onResetPassword: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var username by remember(account.username) { mutableStateOf(account.displayName()) }
    var bio by remember(account.bio) { mutableStateOf(account.bio) }
    var password by remember { mutableStateOf("") }
    var avatar by remember(account.avatar) { mutableStateOf(account.avatar) }
    var avatarChanged by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    val photo = remember { FocusRequester() }
    val name = remember { FocusRequester() }
    val about = remember { FocusRequester() }
    val pass = remember { FocusRequester() }
    val save = remember { FocusRequester() }
    val reset = remember { FocusRequester() }
    val close = remember { FocusRequester() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val next = runCatching { withContext(Dispatchers.IO) { avatarDataUrl(context, uri) } }
            next.onSuccess {
                avatar = it
                avatarChanged = true
                localError = null
            }.onFailure {
                localError = it.message ?: "Could not read that image."
            }
        }
    }
    LaunchedEffect(Unit) { photo.bringIntoFocus() }
    AccountScrim(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccountAvatar(avatar, Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)))
            Column {
                Text(account.displayName(), color = KryoColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(account.email, color = KryoColors.Muted, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        AccountButton("Change photo", photo, photo, name, busy) { picker.launch("image/*") }
        Spacer(Modifier.height(8.dp))
        AccountField(username, { username = it }, "Username", name, photo, about, KeyboardType.Text)
        Spacer(Modifier.height(8.dp))
        AccountField(bio, { bio = it }, "About me", about, name, pass, KeyboardType.Text)
        Spacer(Modifier.height(8.dp))
        AccountField(password, { password = it }, "New password", pass, about, save, KeyboardType.Password, hidden = true)
        val message = localError ?: error
        if (message != null) {
            Spacer(Modifier.height(10.dp))
            Text(message, color = KryoColors.Danger, fontSize = 14.sp)
        }
        Spacer(Modifier.height(16.dp))
        AccountButton("Save profile", save, pass, reset, busy, "profile_save") {
            val draft = ProfileDraft(username, bio, password, avatar, avatarChanged)
            val problem = validateProfileDraft(draft)
            localError = problem
            if (problem == null && !busy) onSave(draft)
        }
        Spacer(Modifier.height(8.dp))
        AccountButton("Email me a reset link", reset, save, close, busy) { if (!busy) onResetPassword() }
        Spacer(Modifier.height(8.dp))
        AccountButton("Close", close, reset, close, false) { onDismiss() }
    }
}

@Composable
internal fun FriendsBoard(
    m: Metrics,
    signedIn: Boolean,
    state: FriendsBoardState,
    query: String,
    anchor: FocusRequester,
    onSignIn: () -> Unit,
    onOpenSearch: () -> Unit,
    onAdd: (PlayerProfile) -> Unit,
    onAccept: (FriendRequest) -> Unit,
    onDecline: (FriendRequest) -> Unit,
    onRemove: (FriendEntry) -> Unit,
) {
    val friendIds = state.friends.map { it.profile.id }.toSet()
    val incomingIds = state.requests.map { it.profile.id }.toSet()
    val firstAdd = state.results.firstOrNull { it.id !in friendIds && it.id !in state.pendingOutIds && it.id !in incomingIds }
    val anchorOn = when {
        !signedIn -> "sign-in"
        query.isNotBlank() && firstAdd != null -> "add-${firstAdd.id}"
        state.requests.isNotEmpty() -> "accept-${state.requests.first().friendshipId}"
        state.friends.isNotEmpty() -> "remove-${state.friends.first().friendshipId}"
        else -> "find"
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = m.d(8)), verticalArrangement = Arrangement.spacedBy(m.d(10))) {
        Text("Friends", color = KryoColors.Text, fontSize = m.t(32), fontWeight = FontWeight.Bold)
        Text("Search a username, answer requests, and keep your list.", color = KryoColors.Muted, fontSize = m.t(14))
        state.error?.let { Text(it, color = KryoColors.Danger, fontSize = m.t(14)) }
        state.notice?.let { Text(it, color = KryoColors.Green, fontSize = m.t(14)) }
        if (!signedIn) {
            Text("Sign in with your kryogames.com account to add friends.", color = KryoColors.Muted, fontSize = m.t(15))
            FocusBox(Modifier.fillMaxWidth().height(m.d(48)).focusRequester(anchor), "Sign in", outlined = true, onClick = onSignIn) {
                Text("Sign in", color = KryoColors.Text, fontWeight = FontWeight.SemiBold)
            }
            return@Column
        }
        if (query.isNotBlank()) {
            Text(if (state.searching) "Searching…" else "Search results", color = KryoColors.Text, fontSize = m.t(16), fontWeight = FontWeight.SemiBold)
            if (!state.searching && state.results.isEmpty()) {
                Text("No usernames found.", color = KryoColors.Muted, fontSize = m.t(14))
            }
            state.results.forEach { profile ->
                val relation = when {
                    profile.id in friendIds -> "Friends"
                    profile.id in state.pendingOutIds || profile.id in incomingIds -> "Pending"
                    else -> "Add"
                }
                PersonRow(profile.username, profile.avatar, if (relation == "Add") "Add" else relation, relation == "Add", anchor.takeIf { anchorOn == "add-${profile.id}" }) {
                    if (relation == "Add") onAdd(profile)
                }
            }
        }
        if (state.requests.isNotEmpty()) {
            Text("Incoming requests", color = KryoColors.Text, fontSize = m.t(16), fontWeight = FontWeight.SemiBold)
            state.requests.forEach { request ->
                PersonRow(
                    request.profile.username,
                    request.profile.avatar,
                    "Accept",
                    true,
                    anchor.takeIf { anchorOn == "accept-${request.friendshipId}" },
                    secondary = "Decline" to { onDecline(request) },
                ) { onAccept(request) }
            }
        }
        Text("Friends", color = KryoColors.Text, fontSize = m.t(16), fontWeight = FontWeight.SemiBold)
        if (state.friends.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m.d(10))) {
                Icon(Icons.Outlined.Group, null, tint = KryoColors.Muted, modifier = Modifier.size(m.d(28)))
                Text("No friends yet. Search a username to add someone.", color = KryoColors.Muted, fontSize = m.t(14))
            }
        }
        state.friends.forEach { entry ->
            PersonRow(entry.profile.username, entry.profile.avatar, "Remove", true, anchor.takeIf { anchorOn == "remove-${entry.friendshipId}" }) {
                onRemove(entry)
            }
        }
        if (anchorOn == "find") {
            FocusBox(Modifier.fillMaxWidth().height(m.d(48)).focusRequester(anchor), "Find players", outlined = true, onClick = onOpenSearch) {
                Text("Find players", color = KryoColors.Text, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PersonRow(
    name: String,
    avatar: String?,
    action: String,
    actionable: Boolean,
    anchor: FocusRequester?,
    secondary: Pair<String, () -> Unit>? = null,
    onAction: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AccountAvatar(avatar, Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
        Text(profileLabel(name), color = KryoColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (secondary != null) {
            FocusBox(Modifier.height(36.dp), secondary.first, outlined = true, onClick = secondary.second) {
                Text(secondary.first, Modifier.padding(horizontal = 12.dp), color = KryoColors.Text, fontSize = 13.sp)
            }
        }
        val modifier = Modifier.height(36.dp).then(if (anchor != null) Modifier.focusRequester(anchor) else Modifier)
        if (actionable) {
            FocusBox(modifier, action, outlined = true, onClick = onAction) {
                Text(action, Modifier.padding(horizontal = 12.dp), color = KryoColors.Text, fontSize = 13.sp)
            }
        } else {
            Text(action, color = KryoColors.Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun AccountScrim(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().imePadding().background(Color.Black.copy(alpha = 0.75f)).pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth(0.86f).heightIn(max = 560.dp)
                .background(KryoColors.Surface, RoundedCornerShape(14.dp))
                .border(1.dp, KryoColors.Border, RoundedCornerShape(14.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
        ) { content() }
    }
}

@Composable
private fun AccountField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    focus: FocusRequester,
    up: FocusRequester,
    down: FocusRequester,
    keyboard: KeyboardType,
    hidden: Boolean = false,
) {
    BasicTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        cursorBrush = SolidColor(KryoColors.Accent),
        textStyle = TextStyle(color = KryoColors.Text, fontSize = 15.sp),
        modifier = Modifier.fillMaxWidth().height(48.dp).focusRequester(focus).focusProperties {
            this.up = up
            this.down = down
            left = FocusRequester.Cancel
            right = FocusRequester.Cancel
        }.background(KryoColors.Background, RoundedCornerShape(8.dp)).border(1.dp, KryoColors.Border, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 14.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(label, color = KryoColors.Muted, fontSize = 15.sp)
                inner()
            }
        },
    )
}

@Composable
private fun AccountButton(
    label: String,
    focus: FocusRequester,
    up: FocusRequester,
    down: FocusRequester,
    busy: Boolean,
    tag: String? = null,
    onClick: () -> Unit,
) {
    FocusBox(
        Modifier.fillMaxWidth().height(44.dp).focusRequester(focus).focusProperties {
            this.up = up
            this.down = down
            left = FocusRequester.Cancel
            right = FocusRequester.Cancel
        }.let { if (tag != null) it.testTag(tag) else it },
        label,
        outlined = true,
        onClick = { if (!busy) onClick() },
    ) {
        Text(if (busy && tag != null) "Working…" else label, color = KryoColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun decodeAvatar(dataUrl: String?): androidx.compose.ui.graphics.ImageBitmap? {
    if (dataUrl.isNullOrBlank() || "base64," !in dataUrl) return null
    val bytes = runCatching { Base64.decode(dataUrl.substringAfter("base64,"), Base64.DEFAULT) }.getOrNull() ?: return null
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    return bitmap.asImageBitmap()
}
