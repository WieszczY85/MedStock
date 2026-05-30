package pl.syntaxdevteam.medstock.core.reminders

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import pl.syntaxdevteam.medstock.R

class ReminderAlertPlayer(context: Context) {

    private val appContext = context.applicationContext
    private var mediaPlayer: MediaPlayer? = null
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Vibrator::class.java)
        }
    }

    fun start() {
        if (mediaPlayer?.isPlaying == true) return
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(appContext, customSoundUri(appContext))
            isLooping = true
            prepare()
            start()
        }
        val pattern = longArrayOf(0L, 500L, 300L, 500L, 1_000L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun customSoundUri(context: Context): Uri =
        Uri.parse("android.resource://${context.packageName}/${R.raw.dzwonki}")

    fun stop() {
        mediaPlayer?.runCatchingStop()
        mediaPlayer = null
        vibrator?.cancel()
    }

    private fun MediaPlayer.runCatchingStop() {
        runCatching {
            if (isPlaying) stop()
            release()
        }
    }
}
