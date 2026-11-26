package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private val Context.shadowPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "shadow_room_preferences"
)

data class LocalRoomPreferences(
    val voiceEnabled: Boolean = true,
    val speechRate: Float = 0.95f,
    val speechVolume: Float = 1.0f,
    val lowPerformanceMode: Boolean = false,
    val faceImagePath: String? = null,
    val faceChoicePrompted: Boolean = false
)

class LocalPreferencesRepository(private val context: Context) {
    private val appContext = context.applicationContext

    private object Keys {
        val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val SPEECH_VOLUME = floatPreferencesKey("speech_volume")
        val LOW_PERFORMANCE_MODE = booleanPreferencesKey("low_performance_mode")
        val FACE_IMAGE_PATH = stringPreferencesKey("face_image_path")
        val FACE_CHOICE_PROMPTED = booleanPreferencesKey("face_choice_prompted")
    }

    val preferencesFlow: Flow<LocalRoomPreferences> = appContext.shadowPrefsDataStore.data.map { prefs ->
        val rawPath = prefs[Keys.FACE_IMAGE_PATH]
        val validPath = rawPath?.takeIf { File(it).exists() }
        LocalRoomPreferences(
            voiceEnabled = prefs[Keys.VOICE_ENABLED] ?: true,
            speechRate = (prefs[Keys.SPEECH_RATE] ?: 0.95f).coerceIn(0.6f, 1.5f),
            speechVolume = (prefs[Keys.SPEECH_VOLUME] ?: 1.0f).coerceIn(0.0f, 1.0f),
            lowPerformanceMode = prefs[Keys.LOW_PERFORMANCE_MODE] ?: false,
            faceImagePath = validPath,
            faceChoicePrompted = prefs[Keys.FACE_CHOICE_PROMPTED] ?: false
        )
    }

    suspend fun setVoiceEnabled(enabled: Boolean) {
        appContext.shadowPrefsDataStore.edit { it[Keys.VOICE_ENABLED] = enabled }
    }

    suspend fun setSpeechRate(rate: Float) {
        appContext.shadowPrefsDataStore.edit { it[Keys.SPEECH_RATE] = rate.coerceIn(0.6f, 1.5f) }
    }

    suspend fun setSpeechVolume(volume: Float) {
        appContext.shadowPrefsDataStore.edit { it[Keys.SPEECH_VOLUME] = volume.coerceIn(0.0f, 1.0f) }
    }

    suspend fun setLowPerformanceMode(enabled: Boolean) {
        appContext.shadowPrefsDataStore.edit { it[Keys.LOW_PERFORMANCE_MODE] = enabled }
    }

    suspend fun setFaceChoicePrompted(prompted: Boolean) {
        appContext.shadowPrefsDataStore.edit { it[Keys.FACE_CHOICE_PROMPTED] = prompted }
    }

    suspend fun saveLocalFaceImage(sourceUri: Uri): String? = withContext(Dispatchers.IO) {
        val faceDir = File(appContext.filesDir, "face").apply { mkdirs() }
        val destFile = File(faceDir, "shadow_face.jpg")
        appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
            val decoded = BitmapFactory.decodeStream(input) ?: return@withContext null
            val maxDim = 512
            val scale = minOf(maxDim.toFloat() / decoded.width, maxDim.toFloat() / decoded.height, 1f)
            val targetW = (decoded.width * scale).toInt().coerceAtLeast(1)
            val targetH = (decoded.height * scale).toInt().coerceAtLeast(1)
            val resized = if (scale < 1f) Bitmap.createScaledBitmap(decoded, targetW, targetH, true) else decoded
            FileOutputStream(destFile).use { out ->
                resized.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            if (resized !== decoded) decoded.recycle()
        } ?: return@withContext null

        appContext.shadowPrefsDataStore.edit {
            it[Keys.FACE_IMAGE_PATH] = destFile.absolutePath
            it[Keys.FACE_CHOICE_PROMPTED] = true
        }
        destFile.absolutePath
    }

    suspend fun removeLocalFaceImage() = withContext(Dispatchers.IO) {
        val faceDir = File(appContext.filesDir, "face")
        File(faceDir, "shadow_face.jpg").delete()
        appContext.shadowPrefsDataStore.edit {
            it.remove(Keys.FACE_IMAGE_PATH)
        }
    }
}
