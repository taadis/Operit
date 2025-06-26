package com.ai.assistance.operit.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log

class AudioFocusManager(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var audioFocusListener: AudioManager.OnAudioFocusChangeListener? = null
    
    // 添加 MediaSession 支持
    private val mediaSession: MediaSessionCompat = MediaSessionCompat(context, "OperitAudioFocus").apply {
        setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                // 空实现，仅用于占据音频焦点
            }
            
            override fun onStop() {
                // 空实现，释放音频焦点时会调用
            }
        })
        
        // 设置初始播放状态为停止
        setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_STOPPED, 0, 1.0f)
                .setActions(PlaybackStateCompat.ACTION_PLAY)
                .build()
        )
    }

    fun requestFocus(listener: AudioManager.OnAudioFocusChangeListener): Boolean {
        audioFocusListener = listener
        
        // 激活 MediaSession，设置为活跃状态
        mediaSession.isActive = true
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
                .setActions(PlaybackStateCompat.ACTION_PLAY)
                .build()
        )
        
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(listener)
                .setWillPauseWhenDucked(true)
                .build()
            focusRequest?.let { audioManager.requestAudioFocus(it) } ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                listener,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }

        val focusGranted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (focusGranted) {
            Log.d("AudioFocusManager", "Audio focus request granted with MediaSession support")
        } else {
            Log.w("AudioFocusManager", "Audio focus request failed even with MediaSession")
            // 如果获取焦点失败，停用MediaSession
            mediaSession.isActive = false
        }
        return focusGranted
    }

    fun abandonFocus() {
        // 停用 MediaSession
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_STOPPED, 0, 1.0f)
                .setActions(PlaybackStateCompat.ACTION_PLAY)
                .build()
        )
        mediaSession.isActive = false
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioFocusListener?.let { audioManager.abandonAudioFocus(it) }
        }
        Log.d("AudioFocusManager", "Audio focus abandoned and MediaSession deactivated")
    }
    
    /**
     * 刷新音频焦点 - 先放弃，然后如果有监听器则重新请求
     * 这在TTS播放结束后调用，确保焦点状态是干净的
     */
    fun refreshFocus() {
        // 先放弃当前焦点
        abandonFocus()
        
        // 重置MediaSession状态
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_STOPPED, 0, 1.0f)
                .setActions(PlaybackStateCompat.ACTION_PLAY)
                .build()
        )
        mediaSession.isActive = false
        
        Log.d("AudioFocusManager", "Audio focus refreshed")
    }
    
    fun release() {
        // 释放 MediaSession 资源
        abandonFocus()
        mediaSession.release()
        Log.d("AudioFocusManager", "MediaSession released")
    }
} 