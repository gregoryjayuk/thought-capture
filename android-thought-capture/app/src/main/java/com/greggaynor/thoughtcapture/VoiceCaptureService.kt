package com.greggaynor.thoughtcapture

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class VoiceCaptureService : Service(), RecognitionListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var shouldKeepListening: Boolean = false
    private var isStopping: Boolean = false
    private var hasFinalizedCapture: Boolean = false
    private var sessionTranscript: String = ""
    private val forcedStopRunnable = Runnable { finalizeCaptureAndStop() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCapture()
            ACTION_STOP -> stopCaptureAndSelf()
        }
        return START_STICKY
    }

    private fun startCapture() {
        if (!hasRecordAudioPermission()) {
            stopCaptureAndSelf()
            return
        }

        if (shouldKeepListening) return

        shouldKeepListening = true
        isStopping = false
        hasFinalizedCapture = false
        sessionTranscript = ""
        mainHandler.removeCallbacks(forcedStopRunnable)
        CaptureState.setListening(this, true)
        ThoughtWidgetProvider.updateAllWidgets(this)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_listening)))

        mainHandler.post {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this).also {
                    it.setRecognitionListener(this)
                }
            }
            beginListening()
        }
    }

    private fun stopCaptureAndSelf() {
        if (!shouldKeepListening && !isStopping) {
            finalizeCaptureAndStop()
            return
        }

        shouldKeepListening = false
        isStopping = true
        CaptureState.setListening(this, false)
        ThoughtWidgetProvider.updateAllWidgets(this)

        mainHandler.post {
            recognizer?.stopListening()
        }
        mainHandler.removeCallbacks(forcedStopRunnable)
        mainHandler.postDelayed(forcedStopRunnable, 1200L)
    }

    private fun beginListening() {
        if (!shouldKeepListening) return

        val listenIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        recognizer?.startListening(listenIntent)
    }

    private fun restartListeningSoon(delayMs: Long = 350L) {
        if (!shouldKeepListening) return
        mainHandler.postDelayed({ beginListening() }, delayMs)
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun buildNotification(content: String): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            2001,
            Intent(this, VoiceCaptureService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = PendingIntent.getActivity(
            this,
            2002,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(content)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.notification_stop), stopIntent)
            .build()
    }

    override fun onReadyForSpeech(params: Bundle?) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(getString(R.string.notification_listening)))
    }

    override fun onBeginningOfSpeech() = Unit

    override fun onRmsChanged(rmsdB: Float) = Unit

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() = Unit

    override fun onError(error: Int) {
        if (shouldKeepListening) {
            restartListeningSoon()
        } else {
            finalizeCaptureAndStop()
        }
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val bestMatch = matches?.firstOrNull().orEmpty()
        if (bestMatch.isNotBlank()) {
            sessionTranscript = mergeSegments(sessionTranscript, bestMatch)
        }

        if (shouldKeepListening) {
            restartListeningSoon(150L)
        } else {
            finalizeCaptureAndStop()
        }
    }

    override fun onPartialResults(partialResults: Bundle?) = Unit

    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    override fun onDestroy() {
        shouldKeepListening = false
        isStopping = false
        mainHandler.removeCallbacks(forcedStopRunnable)
        CaptureState.setListening(this, false)
        ThoughtWidgetProvider.updateAllWidgets(this)
        mainHandler.post {
            recognizer?.destroy()
            recognizer = null
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.greggaynor.thoughtcapture.START_CAPTURE"
        const val ACTION_STOP = "com.greggaynor.thoughtcapture.STOP_CAPTURE"

        private const val CHANNEL_ID = "thought_capture_channel"
        private const val NOTIFICATION_ID = 9001
    }

    private fun finalizeCaptureAndStop() {
        if (hasFinalizedCapture) return

        hasFinalizedCapture = true
        isStopping = false
        mainHandler.removeCallbacks(forcedStopRunnable)

        val text = sessionTranscript.trim()
        if (text.isNotEmpty()) {
            TranscriptStore.appendThought(this, text)
        }

        mainHandler.post {
            recognizer?.cancel()
            recognizer?.destroy()
            recognizer = null
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun mergeSegments(existing: String, incoming: String): String {
        val normalizedIncoming = normalizeText(incoming)
        if (normalizedIncoming.isEmpty()) return existing

        val normalizedExisting = normalizeText(existing)
        if (normalizedExisting.isEmpty()) return normalizedIncoming
        if (normalizedIncoming.equals(normalizedExisting, ignoreCase = true)) return normalizedExisting
        if (normalizedIncoming.startsWith(normalizedExisting, ignoreCase = true)) return normalizedIncoming
        if (normalizedExisting.endsWith(normalizedIncoming, ignoreCase = true)) return normalizedExisting

        val existingWords = normalizedExisting.split(" ")
        val incomingWords = normalizedIncoming.split(" ")
        val maxOverlap = minOf(existingWords.size, incomingWords.size)

        for (overlap in maxOverlap downTo 1) {
            val existingSuffix = existingWords.takeLast(overlap)
            val incomingPrefix = incomingWords.take(overlap)
            if (existingSuffix.joinToString(" ").equals(incomingPrefix.joinToString(" "), ignoreCase = true)) {
                val remainder = incomingWords.drop(overlap).joinToString(" ")
                return if (remainder.isBlank()) normalizedExisting else "$normalizedExisting $remainder"
            }
        }

        return "$normalizedExisting $normalizedIncoming"
    }

    private fun normalizeText(text: String): String {
        return text.trim().replace(Regex("\\s+"), " ")
    }
}
