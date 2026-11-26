package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

private const val TAG = "ShadowVoiceController"

data class VoiceUiStatus(
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val ttsReady: Boolean = false,
    val speechRecognitionAvailable: Boolean = false,
    val partialTranscript: String = "",
    val micRmsLevel: Float = 0f,
    val voiceError: String? = null
)

class ShadowVoiceController(
    context: Context,
    private val onFinalTranscript: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val _status = MutableStateFlow(
        VoiceUiStatus(
            speechRecognitionAvailable = SpeechRecognizer.isRecognitionAvailable(appContext)
        )
    )
    val status: StateFlow<VoiceUiStatus> = _status.asStateFlow()

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var pendingSpeech: PendingSpeech? = null

    private data class PendingSpeech(
        val text: String,
        val rate: Float,
        val volume: Float
    )

    init {
        initTextToSpeech()
    }

    private fun initTextToSpeech() {
        tts = TextToSpeech(appContext) { initStatus ->
            if (initStatus == TextToSpeech.SUCCESS) {
                val engine = tts
                val langResult = engine?.setLanguage(Locale.US)
                if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                    langResult == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Log.w(TAG, "Preferred US English TTS locale not fully available.")
                }
                // Prefer a calm, natural-sounding voice when available on device
                runCatching {
                    val voices = engine?.voices.orEmpty()
                    val preferred = voices.firstOrNull { voice ->
                        voice.locale.language == "en" &&
                            !voice.isNetworkConnectionRequired &&
                            (voice.name.contains("male", ignoreCase = true) ||
                                voice.name.contains("sfg", ignoreCase = true) ||
                                voice.quality >= android.speech.tts.Voice.QUALITY_HIGH)
                    } ?: voices.firstOrNull { it.locale.language == "en" }
                    if (preferred != null) {
                        engine?.voice = preferred
                    }
                }
                engine?.setPitch(0.92f)
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _status.value = _status.value.copy(isSpeaking = true, voiceError = null)
                    }

                    override fun onDone(utteranceId: String?) {
                        _status.value = _status.value.copy(isSpeaking = false)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _status.value = _status.value.copy(isSpeaking = false)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _status.value = _status.value.copy(isSpeaking = false)
                    }
                })
                _status.value = _status.value.copy(ttsReady = true)
                pendingSpeech?.let { queued ->
                    pendingSpeech = null
                    speak(queued.text, queued.rate, queued.volume)
                }
            } else {
                Log.w(TAG, "TextToSpeech initialization failed with code $initStatus")
                _status.value = _status.value.copy(ttsReady = false)
            }
        }
    }

    fun speak(text: String, rate: Float, volume: Float) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val engine = tts
        if (engine == null || !_status.value.ttsReady) {
            pendingSpeech = PendingSpeech(clean, rate, volume)
            return
        }
        stopListening()
        engine.setSpeechRate(rate.coerceIn(0.6f, 1.5f))
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0.0f, 1.0f))
        }
        val utteranceId = UUID.randomUUID().toString()
        _status.value = _status.value.copy(isSpeaking = true, voiceError = null)
        engine.speak(clean, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stopSpeaking() {
        pendingSpeech = null
        runCatching { tts?.stop() }
        _status.value = _status.value.copy(isSpeaking = false)
    }

    fun startListening() {
        stopSpeaking()
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            _status.value = _status.value.copy(
                isListening = false,
                speechRecognitionAvailable = false,
                voiceError = "Speech recognition service is unavailable on this device. Use the text input below."
            )
            return
        }

        runCatching {
            speechRecognizer?.destroy()
            val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _status.value = _status.value.copy(
                        isListening = true,
                        partialTranscript = "",
                        voiceError = null
                    )
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    _status.value = _status.value.copy(micRmsLevel = normalized)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _status.value = _status.value.copy(micRmsLevel = 0f)
                }

                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Tap the microphone or type below."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out. Tap the microphone when ready."
                        SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for voice input."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Speech service network error. You can type directly below."
                        else -> null
                    }
                    _status.value = _status.value.copy(
                        isListening = false,
                        micRmsLevel = 0f,
                        partialTranscript = "",
                        voiceError = msg
                    )
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spoken = matches?.firstOrNull()?.trim().orEmpty()
                    _status.value = _status.value.copy(
                        isListening = false,
                        micRmsLevel = 0f,
                        partialTranscript = ""
                    )
                    if (spoken.isNotEmpty()) {
                        onFinalTranscript(spoken)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        _status.value = _status.value.copy(partialTranscript = partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            speechRecognizer = recognizer

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2200L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L)
            }
            _status.value = _status.value.copy(isListening = true, voiceError = null)
            recognizer.startListening(intent)
        }.onFailure { err ->
            Log.e(TAG, "Failed to start SpeechRecognizer", err)
            _status.value = _status.value.copy(
                isListening = false,
                voiceError = "Could not start voice recognition. Use the text input below."
            )
        }
    }

    fun stopListening() {
        if (_status.value.isListening) {
            runCatching { speechRecognizer?.stopListening() }
        }
        _status.value = _status.value.copy(
            isListening = false,
            micRmsLevel = 0f
        )
    }

    fun clearVoiceError() {
        _status.value = _status.value.copy(voiceError = null)
    }

    fun release() {
        pendingSpeech = null
        runCatching { speechRecognizer?.destroy() }
        speechRecognizer = null
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
    }
}
