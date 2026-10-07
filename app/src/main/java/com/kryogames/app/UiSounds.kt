package com.kryogames.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

internal enum class UiSound { MOVE, SELECT, BACK, LIMIT }

/**
 * Short interface cues synthesized at startup. Each one is a static buffer so a
 * cursor move can replay immediately, without waiting on a sound-pool load.
 */
internal object UiCue {
    private var engine: UiSounds? = null
    private var unavailable = false
    private var lastMove = 0L
    private var lastLimit = 0L

    fun attach() {
        if (engine != null || unavailable || runningUnderTest()) return
        engine = runCatching { UiSounds() }.getOrElse {
            unavailable = true
            null
        }
    }

    fun move() = fire(UiSound.MOVE, 0.55f, gapMs = 28L, last = { lastMove }, mark = { lastMove = it })
    fun select() = fire(UiSound.SELECT, 0.7f, gapMs = 0L, last = { 0L }, mark = { _ -> })
    fun back() = fire(UiSound.BACK, 0.6f, gapMs = 0L, last = { 0L }, mark = { _ -> })
    fun limit() = fire(UiSound.LIMIT, 0.42f, gapMs = 90L, last = { lastLimit }, mark = { lastLimit = it })

    private fun fire(
        sound: UiSound,
        volume: Float,
        gapMs: Long,
        last: () -> Long,
        mark: (Long) -> Unit,
    ) {
        val now = android.os.SystemClock.uptimeMillis()
        if (gapMs > 0L && now - last() < gapMs) return
        mark(now)
        engine?.play(sound, volume)
    }

    private fun runningUnderTest(): Boolean = try {
        Class.forName("org.robolectric.Robolectric")
        true
    } catch (_: ClassNotFoundException) {
        false
    }
}

private class UiSounds {
    private val tracks = mapOf(
        UiSound.MOVE to CueTrack(moveClick()),
        UiSound.SELECT to CueTrack(selectClick()),
        UiSound.BACK to CueTrack(backClick()),
        UiSound.LIMIT to CueTrack(limitClick()),
    )

    fun play(sound: UiSound, volume: Float) {
        tracks[sound]?.play(volume)
    }
}

private class CueTrack(pcm: ShortArray) {
    private val pcmBytes = cuePcm(pcm)
    private val track: AudioTrack? = open(lowLatency = true) ?: open(lowLatency = false)

    fun play(volume: Float) {
        val track = track ?: return
        runCatching {
            // A static buffer stays parked at the end after it finishes, and
            // setPlaybackHeadPosition does not rewind it. Stop, then reload.
            if (track.playState != AudioTrack.PLAYSTATE_STOPPED) {
                track.pause()
                track.stop()
            }
            if (track.reloadStaticData() != AudioTrack.SUCCESS) {
                track.setPlaybackHeadPosition(0)
            }
            track.setVolume(volume.coerceIn(0f, 1f))
            track.play()
        }
    }

    private fun open(lowLatency: Boolean): AudioTrack? = runCatching {
        val data = pcmBytes
        val builder = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(data.size)
        if (lowLatency) builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        builder.build().also { created ->
            if (created.write(data, 0, data.size) < data.size) error("short cue rejected")
        }
    }.getOrNull()
}

/** Pads past the mixer period so a short click is actually delivered, not dropped. */
private fun cuePcm(pcm: ShortArray): ByteArray {
    val pcmBytes = pcm.size * 2
    val hardware = AudioTrack.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
    )
    val hardwareBytes = if (hardware > 0) hardware else pcmBytes
    val holdBytes = SAMPLE_RATE * 2 * 120 / 1000
    var size = maxOf(pcmBytes, hardwareBytes, holdBytes)
    if (size % 2 != 0) size++
    val data = ByteArray(size)
    ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).apply {
        pcm.forEach { putShort(it) }
    }
    return data
}

private const val SAMPLE_RATE = 44100

private fun moveClick(): ShortArray = render(32, 0.72) { t ->
    val tick = sin(2 * PI * 2140 * t) * 0.62 + sin(2 * PI * 4280 * t) * 0.12
    val body = sin(2 * PI * 680 * t) * 0.18
    tick + body
}

private fun selectClick(): ShortArray = render(68, 0.78) { t ->
    val fundamental = sin(2 * PI * 784 * t) * 0.7
    val fifth = sin(2 * PI * 1176 * t) * 0.22
    val air = sin(2 * PI * 2350 * t) * 0.08
    fundamental + fifth + air
}

private fun backClick(): ShortArray = render(76, 0.66) { t ->
    val glide = 620.0 - 340.0 * (t / 0.076).coerceIn(0.0, 1.0)
    sin(2 * PI * glide * t) * 0.78 + sin(2 * PI * glide * 2 * t) * 0.12
}

private fun limitClick(): ShortArray = render(40, 0.48) { t ->
    sin(2 * PI * 196 * t) * 0.85 + sin(2 * PI * 98 * t) * 0.25
}

private fun render(durationMs: Int, gain: Double, sample: (time: Double) -> Double): ShortArray {
    val count = (SAMPLE_RATE * durationMs / 1000).coerceAtLeast(1)
    val out = ShortArray(count)
    val seconds = durationMs / 1000.0
    for (i in 0 until count) {
        val t = i.toDouble() / SAMPLE_RATE
        val attack = 1.0 - exp(-t * 3200.0)
        val decay = exp(-t * (4.6 / seconds))
        val edge = if (i > count - 24) (count - i) / 24.0 else 1.0
        val value = (sample(t) * attack * decay * edge * gain).coerceIn(-1.0, 1.0)
        out[i] = (value * 32767.0).toInt().toShort()
    }
    return out
}
