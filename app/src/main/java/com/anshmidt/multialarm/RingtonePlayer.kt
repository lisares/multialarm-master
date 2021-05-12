package com.anshmidt.multialarm

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.CountDownTimer
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Created by Ilya Anshmidt on 08.10.2017.
 */
class RingtonePlayer(context: Context) {
    interface OnFinishListener {
        fun onPlayerFinished()
    }

    private val context: Context
    private val sharPrefHelper: SharedPreferencesHelper
    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var initialRingerMode = 0
    private var countDownTimer: CountDownTimer? = null
    private var durationSeconds = 0
    private val LOG_TAG = RingtonePlayer::class.java.simpleName
    private val onFinishListener: OnFinishListener?
    private var isReleased = false
    fun start() {
        Log.d(LOG_TAG, "Playing started")
        setNormalRingerMode()
        durationSeconds = sharPrefHelper.durationInt
        mediaPlayer = MediaPlayer()
        mediaPlayer!!.setAudioStreamType(AudioManager.STREAM_ALARM)
        if (durationSeconds > 0) {
            mediaPlayer!!.isLooping = true
        }
        if (durationSeconds == 0) {
            durationSeconds = mediaPlayer!!.duration
        }
        try {
            mediaPlayer!!.setDataSource(context, ringtone)
            mediaPlayer!!.prepare()
        } catch (e: IOException) {
            Log.d(LOG_TAG, "Getting data for mediaplayer failed: $e")
            //if MediaPlayer fails to play ringtone from sharedPreferences, try to play default ringtone
            try {
                Log.d(LOG_TAG, "Using default ringtone")
                mediaPlayer!!.setDataSource(context, defaultRingtone)
                mediaPlayer!!.prepare()
            } catch (e1: IOException) {
                Log.e(LOG_TAG, "Preparing MediaPlayer with default ringtone failed: $e1")
            }
        }
        mediaPlayer!!.start()
        startCountDownTimer(durationSeconds)
        Log.d(LOG_TAG, "MediaPlayer started: duration = $durationSeconds")
    }

    fun stop() {
        if (isReleased) {
            return
        }
        stopCountDownTimer()
        if (mediaPlayer != null) {
            if (mediaPlayer!!.isPlaying) {
                mediaPlayer!!.stop()
            }
            mediaPlayer!!.reset()
            mediaPlayer!!.release()
            isReleased = true
        }
        setInitialRingerMode()
        onFinishListener?.onPlayerFinished()
    }

    private fun startCountDownTimer(durationSec: Int) {
        countDownTimer = object : CountDownTimer((durationSec * 1000).toLong(), (durationSec * 1000).toLong()) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                stop()
            }
        }
        countDownTimer!!.start()
    }

    private fun stopCountDownTimer() {
        if (countDownTimer != null) {
            countDownTimer!!.cancel()
        }
    }

    // if ringtone not chosen yet
    private val ringtone: Uri
        private get() {
            val filename = sharPrefHelper.ringtoneFileName
            return if (filename == "") {   // if ringtone not chosen yet
                defaultRingtone
            } else {
                val ringtone = File(context.filesDir, filename)
                Uri.fromFile(ringtone)
            }
        }
    private val defaultRingtone: Uri
        private get() = sharPrefHelper.defaultRingtoneUri

    private fun setNormalRingerMode() {  // in case phone is in "Vibrate" mode
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        initialRingerMode = audioManager!!.ringerMode
        try {
            audioManager!!.ringerMode = AudioManager.RINGER_MODE_NORMAL
        } catch (e: SecurityException) {
            Log.d(LOG_TAG, "Cannot set RingerMode: $e")
        }
    }

    private fun setInitialRingerMode() {
        audioManager!!.ringerMode = initialRingerMode
    }

    init {
        sharPrefHelper = SharedPreferencesHelper(context)
        this.context = context
        onFinishListener = context as OnFinishListener
    }
}