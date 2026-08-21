package com.bicy.note.util

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.max

/**
 * 简易 WAV 录音器：AudioRecord 采集 PCM16，边录边写文件，
 * 停止时补齐 WAV 头。44.1kHz 单声道。
 */
class WavAudioRecorder {

    private var recorder: AudioRecord? = null
    private var output: BufferedOutputStream? = null
    var dataBytes: Long = 0
        private set
    var isRecording: Boolean = false
        private set

    fun start(file: File) {
        if (isRecording) return
        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = max(
            AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding),
            sampleRate / 10,
        )
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            encoding,
            bufferSize,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            Log.e(TAG, "录音初始化失败")
            return
        }
        file.parentFile?.mkdirs()
        val out = BufferedOutputStream(FileOutputStream(file))
        out.write(emptyWavHeader()) // 44 字节占位头
        record.startRecording()
        recorder = record
        output = out
        dataBytes = 0
        isRecording = true

        Thread {
            val buffer = ShortArray(bufferSize / 2)
            while (isRecording) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    try {
                        val bytes = ByteArray(read * 2)
                        for (i in 0 until read) {
                            bytes[i * 2] = (buffer[i].toInt() and 0xFF).toByte()
                            bytes[i * 2 + 1] = ((buffer[i].toInt() shr 8) and 0xFF).toByte()
                        }
                        output?.write(bytes)
                        dataBytes += bytes.size
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        }.apply { isDaemon = true }.start()
    }

    fun stop() {
        if (!isRecording) return
        isRecording = false
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            output?.flush()
            output?.close()
        } catch (_: Exception) {
        }
        recorder?.release()
        recorder = null
        output = null
    }

    fun cancel(file: File) {
        stop()
        file.delete()
    }

    private fun emptyWavHeader(): ByteArray = ByteArray(44)

    companion object {
        private const val TAG = "WavAudioRecorder"

        /** 补齐 WAV 头（由调用方在 stop 后写入文件头）。 */
        fun writeWavHeader(file: File, dataBytes: Long) {
            if (dataBytes < 0) return
            try {
                RandomAccessFile(file, "rw").use { raf ->
                    val byteRate = 44100 * 2 // 16bit mono
                    val blockAlign = 2
                    fun writeLeInt(value: Int) {
                        raf.write(value and 0xFF)
                        raf.write((value shr 8) and 0xFF)
                        raf.write((value shr 16) and 0xFF)
                        raf.write((value shr 24) and 0xFF)
                    }
                    fun writeLeShort(value: Int) {
                        raf.write(value and 0xFF)
                        raf.write((value shr 8) and 0xFF)
                    }
                    raf.seek(0)
                    raf.write("RIFF".toByteArray())
                    writeLeInt((36 + dataBytes).toInt())
                    raf.write("WAVE".toByteArray())
                    raf.write("fmt ".toByteArray())
                    writeLeInt(16)
                    writeLeShort(1)      // PCM
                    writeLeShort(1)      // mono
                    writeLeInt(44100)    // sample rate
                    writeLeInt(byteRate)
                    writeLeShort(blockAlign)
                    writeLeShort(16)     // bits per sample
                    raf.write("data".toByteArray())
                    writeLeInt(dataBytes.toInt())
                }
            } catch (e: Exception) {
                Log.e(TAG, "写 WAV 头失败", e)
            }
        }
    }
}