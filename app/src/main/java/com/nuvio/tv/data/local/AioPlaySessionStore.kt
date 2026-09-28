package com.nuvio.tv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.aioPlaySessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "aioplay_session_store",
    corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler {
        androidx.datastore.preferences.core.emptyPreferences()
    }
)

data class AioPlayStoredSession(
    val token: String,
    val username: String,
    val displayName: String,
    val role: String
)

@Singleton
class AioPlaySessionStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tokenKey = stringPreferencesKey("token")
    private val usernameKey = stringPreferencesKey("username")
    private val displayNameKey = stringPreferencesKey("display_name")
    private val roleKey = stringPreferencesKey("role")

    val session: Flow<AioPlayStoredSession?> = context.aioPlaySessionDataStore.data.map { prefs ->
        val token = prefs[tokenKey].orEmpty()
        if (token.isBlank()) {
            null
        } else {
            AioPlayStoredSession(
                token = token,
                username = prefs[usernameKey].orEmpty(),
                displayName = prefs[displayNameKey].orEmpty(),
                role = prefs[roleKey].orEmpty().ifBlank { "user" }
            )
        }
    }

    suspend fun save(
        token: String,
        username: String,
        displayName: String,
        role: String
    ) {
        context.aioPlaySessionDataStore.edit { prefs ->
            prefs[tokenKey] = token
            prefs[usernameKey] = username
            prefs[displayNameKey] = displayName
            prefs[roleKey] = role
        }
    }

    suspend fun clear() {
        context.aioPlaySessionDataStore.edit { it.clear() }
    }
}
