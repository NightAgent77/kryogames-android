package com.kryogames.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Same Supabase project the kryogames.com site uses. The anon key is the public client key. */
internal object KryoBackend {
    private const val Url = "https://mkiychmgquaulezbebps.supabase.co"
    private const val AnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1raXljaG1ncXVhdWxlemJlYnBzIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODYxNDQyODcsImV4cCI6MjEwMTcyMDI4N30.u8ZPsuQ9HCvm8QZ-i4eDkxwme7ha_7JpI1nzUojrCZw"

    suspend fun signIn(email: String, password: String): AuthResult {
        val reply = authPost(
            "/token?grant_type=password",
            JSONObject().put("email", email.trim()).put("password", password),
        )
        if (reply.code !in 200..299) return AuthResult.Failed(authErrorMessage(reply.body, "Couldn't sign in."))
        val account = accountFromAuthJson(reply.body) ?: return AuthResult.Failed("Couldn't sign in.")
        return signedIn(account)
    }

    suspend fun signUp(email: String, password: String, username: String): AuthResult {
        val name = username.trim()
        val reply = authPost(
            "/signup",
            JSONObject()
                .put("email", email.trim())
                .put("password", password)
                .put("data", JSONObject().put("username", name).put("full_name", name).put("name", name)),
        )
        if (reply.code !in 200..299) return AuthResult.Failed(authErrorMessage(reply.body, "Couldn't create the account."))
        val account = accountFromAuthJson(reply.body) ?: return AuthResult.ConfirmEmail(
            "Check $email for a confirmation link, then sign in. This is the same account as kryogames.com.",
        )
        return signedIn(account)
    }

    suspend fun resetPassword(email: String): String? {
        val reply = authPost("/recover", JSONObject().put("email", email.trim()))
        if (reply.code !in 200..299) return authErrorMessage(reply.body, "Couldn't send the reset email.")
        return null
    }

    suspend fun signOut(account: KryoAccount) {
        runCatching { authPost("/logout", JSONObject(), account.accessToken) }
    }

    suspend fun refreshIfNeeded(account: KryoAccount): KryoAccount {
        if (System.currentTimeMillis() < account.expiresAtEpochMs - 60_000L) return account
        val reply = authPost("/token?grant_type=refresh_token", JSONObject().put("refresh_token", account.refreshToken))
        if (reply.code !in 200..299) throw SessionExpired("Your session expired. Sign in again.")
        return accountFromAuthJson(reply.body, account) ?: throw SessionExpired("Your session expired. Sign in again.")
    }

    suspend fun updateAccount(account: KryoAccount, draft: ProfileDraft): AuthResult {
        val fresh = refreshIfNeeded(account)
        val data = JSONObject()
        val nextName = draft.username.trim()
        if (nextName != fresh.username) {
            data.put("username", nextName).put("full_name", nextName).put("name", nextName)
        }
        if (draft.bio.trim() != fresh.bio.trim()) data.put("bio", draft.bio.trim())
        if (draft.avatarChanged) data.put("avatar", draft.avatar ?: JSONObject.NULL)
        val body = JSONObject()
        if (data.length() > 0) body.put("data", data)
        val nextPassword = draft.password.trim()
        if (nextPassword.isNotEmpty()) body.put("password", nextPassword)
        val updated = if (body.length() == 0) fresh else {
            val reply = http("PUT", "$Url/auth/v1/user", body.toString(), fresh.accessToken)
            if (reply.code !in 200..299) return AuthResult.Failed(authErrorMessage(reply.body, "Couldn't update your profile."))
            accountFromAuthJson(reply.body, fresh) ?: fresh
        }
        return signedIn(updated)
    }

    suspend fun loadFriends(account: KryoAccount): FriendsLoad {
        val fresh = refreshIfNeeded(account)
        val friendsReply = restGet(fresh, "friendships", "select=id,requester_id,addressee_id,status&status=eq.accepted&or=${orPair(fresh.id)}")
        val incomingReply = restGet(fresh, "friendships", "select=id,requester_id&addressee_id=eq.${fresh.id}&status=eq.pending")
        val outgoingReply = restGet(fresh, "friendships", "select=addressee_id&requester_id=eq.${fresh.id}&status=eq.pending")
        val failure = firstError(friendsReply, incomingReply, outgoingReply)
        if (failure != null) return FriendsLoad(fresh, FriendsBoardState(error = failure))
        val friendRows = JSONArray(friendsReply.body.ifBlank { "[]" })
        val incomingRows = JSONArray(incomingReply.body.ifBlank { "[]" })
        val outgoingRows = JSONArray(outgoingReply.body.ifBlank { "[]" })
        val ids = buildList {
            for (index in 0 until friendRows.length()) {
                val row = friendRows.getJSONObject(index)
                add(if (row.getString("requester_id") == fresh.id) row.getString("addressee_id") else row.getString("requester_id"))
            }
            for (index in 0 until incomingRows.length()) add(incomingRows.getJSONObject(index).getString("requester_id"))
        }
        val profiles = loadProfiles(fresh, ids)
        val friends = buildList {
            for (index in 0 until friendRows.length()) {
                val row = friendRows.getJSONObject(index)
                val other = if (row.getString("requester_id") == fresh.id) row.getString("addressee_id") else row.getString("requester_id")
                val profile = profiles[other] ?: continue
                add(FriendEntry(row.getString("id"), profile))
            }
        }.sortedBy { it.profile.username.lowercase() }
        val requests = buildList {
            for (index in 0 until incomingRows.length()) {
                val row = incomingRows.getJSONObject(index)
                val profile = profiles[row.getString("requester_id")] ?: continue
                add(FriendRequest(row.getString("id"), profile))
            }
        }
        val pending = buildSet {
            for (index in 0 until outgoingRows.length()) add(outgoingRows.getJSONObject(index).getString("addressee_id"))
        }
        return FriendsLoad(fresh, FriendsBoardState(friends = friends, requests = requests, pendingOutIds = pending))
    }

    suspend fun searchProfiles(account: KryoAccount, query: String): ProfileSearch {
        val fresh = refreshIfNeeded(account)
        val filter = profileSearchQuery(query, fresh.id) ?: return ProfileSearch(fresh, emptyList(), null)
        val reply = restGet(fresh, "profiles", filter)
        if (reply.code !in 200..299) return ProfileSearch(fresh, emptyList(), restError(reply.body, "Couldn't search players."))
        return ProfileSearch(fresh, parseProfiles(reply.body), null)
    }

    suspend fun sendFriendRequest(account: KryoAccount, toUserId: String): ActionResult {
        if (account.id == toUserId) return ActionResult(account, "You cannot add yourself.")
        val fresh = refreshIfNeeded(account)
        val existing = restGet(
            fresh,
            "friendships",
            "select=id,status,requester_id,addressee_id&requester_id=in.(${fresh.id},$toUserId)&addressee_id=in.(${fresh.id},$toUserId)",
        )
        if (existing.code !in 200..299) return ActionResult(fresh, restError(existing.body, "Couldn't send the request."))
        val rows = JSONArray(existing.body.ifBlank { "[]" })
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            val pair = (row.getString("requester_id") == fresh.id && row.getString("addressee_id") == toUserId) ||
                (row.getString("requester_id") == toUserId && row.getString("addressee_id") == fresh.id)
            if (!pair) continue
            val status = row.optString("status")
            if (status == "accepted") return ActionResult(fresh, "You are already friends.")
            if (status == "pending") {
                return ActionResult(
                    fresh,
                    if (row.getString("requester_id") == fresh.id) "Friend request already sent."
                    else "They already sent you a request — check Incoming requests.",
                )
            }
        }
        val created = http(
            "POST",
            "$Url/rest/v1/friendships",
            JSONObject().put("requester_id", fresh.id).put("addressee_id", toUserId).put("status", "pending").toString(),
            fresh.accessToken,
            "return=minimal",
        )
        if (created.code !in 200..299) {
            val message = restError(created.body, "Couldn't send the request.")
            return ActionResult(fresh, if (message.contains("23505")) "Friend request already exists." else message)
        }
        return ActionResult(fresh, null)
    }

    suspend fun respondToRequest(account: KryoAccount, friendshipId: String, accept: Boolean): ActionResult {
        val fresh = refreshIfNeeded(account)
        if (!accept) return removeFriend(fresh, friendshipId)
        val reply = http(
            "PATCH",
            "$Url/rest/v1/friendships?id=eq.$friendshipId",
            JSONObject().put("status", "accepted").toString(),
            fresh.accessToken,
            "return=minimal",
        )
        if (reply.code !in 200..299) return ActionResult(fresh, restError(reply.body, "Couldn't answer the request."))
        return ActionResult(fresh, null)
    }

    suspend fun removeFriend(account: KryoAccount, friendshipId: String): ActionResult {
        val fresh = refreshIfNeeded(account)
        val reply = http("DELETE", "$Url/rest/v1/friendships?id=eq.$friendshipId", null, fresh.accessToken, "return=minimal")
        if (reply.code !in 200..299) return ActionResult(fresh, restError(reply.body, "Couldn't update friends."))
        return ActionResult(fresh, null)
    }

    private suspend fun signedIn(account: KryoAccount): AuthResult = try {
        AuthResult.SignedIn(publishProfile(account))
    } catch (error: IllegalStateException) {
        AuthResult.SignedIn(account, error.message)
    }

    private suspend fun publishProfile(account: KryoAccount): KryoAccount {
        val username = usernameFromMetadata(listOf(account.username)).orEmpty()
        if (username.isEmpty()) return account
        val body = JSONObject()
            .put("id", account.id)
            .put("username", username)
            .put("avatar", account.avatar ?: JSONObject.NULL)
            .put("updated_at", Instant.now().toString())
        val reply = http(
            "POST",
            "$Url/rest/v1/profiles",
            body.toString(),
            account.accessToken,
            "resolution=merge-duplicates,return=minimal",
        )
        if (reply.code !in 200..299) {
            val message = restError(reply.body, "Couldn't save your profile.")
            if (message.contains("23505") || message.contains("already")) {
                throw IllegalStateException("That username is already taken.")
            }
            throw IllegalStateException(message)
        }
        return account.copy(username = username)
    }

    private suspend fun loadProfiles(account: KryoAccount, ids: List<String>): Map<String, PlayerProfile> {
        val unique = ids.distinct().filter { it.isNotBlank() }
        if (unique.isEmpty()) return emptyMap()
        val reply = restGet(account, "profiles", "select=id,username,avatar&id=in.(${unique.joinToString(",")})")
        if (reply.code !in 200..299) return emptyMap()
        return parseProfiles(reply.body).associateBy { it.id }
    }

    private suspend fun restGet(account: KryoAccount, table: String, query: String) =
        http("GET", "$Url/rest/v1/$table?$query", null, account.accessToken)

    private suspend fun authPost(path: String, body: JSONObject, bearer: String? = null) =
        http("POST", "$Url/auth/v1$path", body.toString(), bearer)

    private suspend fun http(method: String, url: String, jsonBody: String?, bearer: String?, prefer: String? = null): HttpReply = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("apikey", AnonKey)
            setRequestProperty("Authorization", "Bearer ${bearer ?: AnonKey}")
            setRequestProperty("Accept", "application/json")
            if (prefer != null) setRequestProperty("Prefer", prefer)
            if (jsonBody != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (jsonBody != null) connection.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            HttpReply(code, text)
        } catch (_: IOException) {
            throw IOException("Couldn't reach KryoGames. Check your connection.")
        } finally {
            connection.disconnect()
        }
    }
}

internal class SessionExpired(message: String) : Exception(message)

internal data class KryoAccount(
    val id: String,
    val email: String,
    val username: String,
    val bio: String,
    val avatar: String?,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMs: Long,
) {
    fun displayName(): String {
        val name = username.trim()
        if (name.length >= 2 && !name.contains("@")) return name
        return email.substringBefore("@").ifBlank { "Player" }
    }
}

data class PlayerProfile(val id: String, val username: String, val avatar: String?)

data class FriendEntry(val friendshipId: String, val profile: PlayerProfile)

data class FriendRequest(val friendshipId: String, val profile: PlayerProfile)

data class FriendsBoardState(
    val friends: List<FriendEntry> = emptyList(),
    val requests: List<FriendRequest> = emptyList(),
    val results: List<PlayerProfile> = emptyList(),
    val pendingOutIds: Set<String> = emptySet(),
    val notice: String? = null,
    val error: String? = null,
    val searching: Boolean = false,
)

internal data class FriendsLoad(val account: KryoAccount, val state: FriendsBoardState)

internal data class ProfileSearch(val account: KryoAccount, val profiles: List<PlayerProfile>, val error: String?)

internal data class ActionResult(val account: KryoAccount, val error: String?)

internal data class ProfileDraft(
    val username: String,
    val bio: String,
    val password: String,
    val avatar: String?,
    val avatarChanged: Boolean,
)

internal enum class AuthMode { SignIn, SignUp, Forgot }

internal sealed interface AuthResult {
    data class SignedIn(val account: KryoAccount, val warning: String? = null) : AuthResult
    data class ConfirmEmail(val message: String) : AuthResult
    data class Failed(val message: String) : AuthResult
}

private data class HttpReply(val code: Int, val body: String)

internal fun validateCredentials(mode: AuthMode, email: String, password: String, username: String): String? {
    if (mode != AuthMode.SignUp && !email.contains("@")) return "Enter the email on your KryoGames account."
    if (mode == AuthMode.SignUp && !email.contains("@")) return "Enter a valid email."
    if (mode != AuthMode.Forgot && password.length < 6) return "Password must be at least 6 characters."
    if (mode == AuthMode.SignUp) {
        val name = username.trim()
        if (name.length < 2) return "Username must be at least 2 characters."
        if (name.length > 24) return "Username must be 24 characters or fewer."
        if (name.contains("@")) return "Username cannot be an email."
    }
    return null
}

internal fun validateProfileDraft(draft: ProfileDraft): String? {
    val name = draft.username.trim()
    if (name.length < 2) return "Username must be at least 2 characters."
    if (name.length > 24) return "Username must be 24 characters or fewer."
    if (name.contains("@")) return "Username cannot be an email."
    if (draft.bio.trim().length > 280) return "About me must be 280 characters or fewer."
    val password = draft.password.trim()
    if (password.isNotEmpty() && password.length < 6) return "Password must be at least 6 characters."
    return null
}

internal fun usernameFromMetadata(values: List<String?>): String? {
    for (value in values) {
        val trimmed = value?.trim() ?: continue
        if (trimmed.length < 2 || trimmed.contains("@")) continue
        return trimmed.take(24)
    }
    return null
}

internal fun profileLabel(username: String): String {
    val trimmed = username.trim()
    if (trimmed.isEmpty()) return "Player"
    if (trimmed.contains("@")) return trimmed.substringBefore("@").ifBlank { "Player" }
    return trimmed
}

internal fun profileSearchQuery(raw: String, excludeId: String): String? {
    val cleaned = raw.trim().filter { it.isLetterOrDigit() || it == '_' || it == '-' || it == ' ' }
    if (cleaned.isBlank()) return null
    val encoded = URLEncoder.encode(cleaned, "UTF-8").replace("+", "%20")
    val id = URLEncoder.encode(excludeId, "UTF-8")
    return "select=id,username,avatar&username=ilike.*$encoded*&id=neq.$id&order=username.asc&limit=20"
}

internal fun formatAuthError(message: String): String {
    if (message.contains("Invalid login credentials")) return "Incorrect email or password."
    if (message.contains("User already registered") || message.contains("already been registered")) {
        return "An account with this email already exists."
    }
    return message
}

private fun authErrorMessage(body: String, fallback: String): String {
    val json = runCatching { JSONObject(body) }.getOrNull()
    val raw = listOf("msg", "error_description", "message", "error")
        .firstNotNullOfOrNull { key -> json?.optString(key)?.takeIf { it.isNotBlank() } }
        ?: fallback
    return formatAuthError(raw)
}

private fun restError(body: String, fallback: String): String {
    val json = runCatching { JSONObject(body) }.getOrNull() ?: return fallback
    val code = json.optString("code")
    if (code == "23505") return "23505"
    return json.optString("message").ifBlank { fallback }
}

private fun firstError(vararg replies: HttpReply): String? {
    val failed = replies.firstOrNull { it.code !in 200..299 } ?: return null
    return restError(failed.body, "Couldn't load friends.")
}

private fun orPair(id: String): String =
    URLEncoder.encode("(requester_id.eq.$id,addressee_id.eq.$id)", "UTF-8")

internal fun accountFromAuthJson(raw: String, previous: KryoAccount? = null): KryoAccount? {
    val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    val user = json.optJSONObject("user") ?: json
    val id = user.optString("id").ifBlank { return null }
    val access = json.optString("access_token").ifBlank { previous?.accessToken ?: return null }
    val refresh = json.optString("refresh_token").ifBlank { previous?.refreshToken ?: return null }
    val expiresAt = if (json.optString("access_token").isNotBlank()) {
        System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L
    } else {
        previous?.expiresAtEpochMs ?: return null
    }
    val meta = user.optJSONObject("user_metadata")
    val username = usernameFromMetadata(
        listOf(meta?.optString("username"), meta?.optString("full_name"), meta?.optString("name")),
    ).orEmpty()
    val avatar = if (meta != null && meta.has("avatar") && !meta.isNull("avatar")) {
        meta.optString("avatar").takeIf { it.isNotBlank() }
    } else {
        previous?.avatar
    }
    val bio = if (meta != null && meta.has("bio")) meta.optString("bio") else previous?.bio.orEmpty()
    return KryoAccount(
        id = id,
        email = user.optString("email").ifBlank { previous?.email.orEmpty() },
        username = username.ifBlank { previous?.username.orEmpty() },
        bio = bio,
        avatar = avatar,
        accessToken = access,
        refreshToken = refresh,
        expiresAtEpochMs = expiresAt,
    )
}

private fun parseProfiles(raw: String): List<PlayerProfile> {
    val rows = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
    return buildList {
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            add(
                PlayerProfile(
                    id = row.getString("id"),
                    username = profileLabel(row.optString("username")),
                    avatar = row.optString("avatar").takeIf { it.isNotBlank() },
                ),
            )
        }
    }
}

internal fun KryoAccount.encode(): String = JSONObject()
    .put("id", id)
    .put("email", email)
    .put("username", username)
    .put("bio", bio)
    .put("avatar", avatar ?: JSONObject.NULL)
    .put("accessToken", accessToken)
    .put("refreshToken", refreshToken)
    .put("expiresAtEpochMs", expiresAtEpochMs)
    .toString()

internal fun decodeAccount(raw: String): KryoAccount? {
    val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    val id = json.optString("id").ifBlank { return null }
    val access = json.optString("accessToken").ifBlank { return null }
    val refresh = json.optString("refreshToken").ifBlank { return null }
    return KryoAccount(
        id = id,
        email = json.optString("email"),
        username = json.optString("username"),
        bio = json.optString("bio"),
        avatar = json.optString("avatar").takeIf { it.isNotBlank() },
        accessToken = access,
        refreshToken = refresh,
        expiresAtEpochMs = json.optLong("expiresAtEpochMs"),
    )
}

private const val AccountPrefs = "kryo_account"
private const val AccountKey = "session"

internal fun loadAccount(context: Context): KryoAccount? =
    context.getSharedPreferences(AccountPrefs, Context.MODE_PRIVATE).getString(AccountKey, null)?.let(::decodeAccount)

internal fun saveAccount(context: Context, account: KryoAccount) {
    context.getSharedPreferences(AccountPrefs, Context.MODE_PRIVATE).edit().putString(AccountKey, account.encode()).apply()
}

internal fun clearAccount(context: Context) {
    context.getSharedPreferences(AccountPrefs, Context.MODE_PRIVATE).edit().remove(AccountKey).apply()
}

internal fun avatarDataUrl(context: Context, uri: Uri): String {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("Please choose an image file.")
    var sample = 1
    while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
    val decoded = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("Could not read that image.")
    val scale = min(min(256f / decoded.width, 256f / decoded.height), 1f)
    val width = max(1, (decoded.width * scale).roundToInt())
    val height = max(1, (decoded.height * scale).roundToInt())
    val scaled = Bitmap.createScaledBitmap(decoded, width, height, true)
    if (scaled != decoded) decoded.recycle()
    val bytes = ByteArrayOutputStream().use { stream ->
        scaled.compress(Bitmap.CompressFormat.JPEG, 72, stream)
        scaled.recycle()
        stream.toByteArray()
    }
    if (bytes.size > 90_000) error("Image is too large after compression. Try a simpler photo.")
    return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
}
