package com.music.vivi.constants

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

val FirebaseUidKey = stringPreferencesKey("firebase_uid")
val FirebaseTokenKey = stringPreferencesKey("firebase_token")
val FirebaseNameKey = stringPreferencesKey("firebase_name")
val FirebaseUsernameKey = stringPreferencesKey("firebase_username")
val FirebaseEmailKey = stringPreferencesKey("firebase_email")
val FirebaseBirthdateKey = stringPreferencesKey("firebase_birthdate")
val FirebaseIsLoggedInKey = booleanPreferencesKey("firebase_is_logged_in")

val FirebaseAiSyncKey = booleanPreferencesKey("firebase_ai_sync")
val FirebaseDolbyKey = booleanPreferencesKey("firebase_dolby")
val FirebaseCustomPlaylistKey = booleanPreferencesKey("firebase_custom_playlist")
val FirebaseEqualizerKey = booleanPreferencesKey("firebase_equalizer")
val FirebaseYoutubeSyncKey = booleanPreferencesKey("firebase_youtube_sync")
