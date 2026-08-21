package com.bicy.note.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * 轻量 JSON 文件存储。
 * - fileName 支持子目录相对路径（如 notes/text/2026_08_16_text.json），自动建目录。
 * - 每个数据域一个文件，schema 变更只需调整数据类默认值，无数据库迁移。
 * - 写入采用 临时文件 + 原子重命名，损坏时自动回退 .bak 备份。
 * - 数据文件即纯文本，可直接查看、导出。
 */
class JsonStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val fileMutexes = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> read(fileName: String, serializer: KSerializer<T>, default: T): T =
        withContext(Dispatchers.IO) {
            val file = file(fileName)
            val backup = File(file.parentFile, "${file.name}.bak")
            val text = when {
                file.exists() -> file.readText()
                backup.exists() -> backup.readText().also {
                    Log.w(TAG, "$fileName 损坏，已从备份恢复")
                    file.writeText(it)
                }
                else -> return@withContext default
            }
            try {
                json.decodeFromString(serializer, text)
            } catch (e: Exception) {
                // 主文件解析失败：尝试 .bak 备份，仍失败才用默认值
                if (backup.exists()) {
                    try {
                        val fromBackup = json.decodeFromString(serializer, backup.readText())
                        Log.w(TAG, "$fileName 损坏，已从备份恢复")
                        file.writeText(backup.readText())
                        return@withContext fromBackup
                    } catch (_: Exception) {
                    }
                }
                Log.e(TAG, "读取 $fileName 失败", e)
                default
            }
        }

    /** 同步读：调用线程等待读盘完成后才返回（启动时加载配置用）。 */
    fun <T> readSync(fileName: String, serializer: KSerializer<T>, default: T): T =
        runBlocking { read(fileName, serializer, default) }

    suspend fun <T> write(fileName: String, serializer: KSerializer<T>, value: T) {
        scope.launch {
            writeNow(fileName, serializer, value)
        }
    }

    /**
     * 同步写：调用线程等待写盘完成后才返回。
     * 设置等关键小文件用这种方式，避免协程还没落盘进程就被杀掉导致配置丢失。
     */
    fun <T> writeSync(fileName: String, serializer: KSerializer<T>, value: T) {
        runBlocking {
            writeNow(fileName, serializer, value)
        }
    }

    private suspend fun <T> writeNow(fileName: String, serializer: KSerializer<T>, value: T) {
        val mutex = fileMutexes.getOrPut(fileName) { Mutex() }
        mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val text = json.encodeToString(serializer, value)
                    val target = file(fileName)
                    target.parentFile?.mkdirs()
                    val tmp = File(target.parentFile, "${target.name}.tmp")
                    tmp.writeText(text)
                    if (target.exists()) {
                        File(target.parentFile, "${target.name}.bak").writeText(target.readText())
                    }
                    if (!tmp.renameTo(target)) {
                        tmp.copyTo(target, overwrite = true)
                        tmp.delete()
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "写入 $fileName 失败", e)
                }
            }
        }
    }

    fun file(fileName: String): File = File(context.filesDir, fileName)

    private companion object {
        const val TAG = "JsonStore"
    }
}