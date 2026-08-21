package com.bicy.note.share

import android.graphics.Bitmap
import android.graphics.Canvas
import com.bicy.note.data.model.NoteEntry
import java.time.LocalDateTime

/**
 * 模板引擎接口。
 *
 * 实现此接口即可创建新的分享模板。
 * 内置模板见 [BuiltinTemplates]，用户自定义模板见 [JsonTemplate]。
 */
interface TemplateEngine {

    /** 模板唯一 ID */
    val id: String

    /** 模板显示名称 */
    val name: String

    /** 模板描述（可选） */
    val description: String get() = ""

    /** 模板缩略图预览（Composable，用于分享弹窗选择） */
    // val thumbnail: @Composable () -> Unit

    /**
     * 渲染笔记内容为 Bitmap。
     *
     * @param context Android Context（加载图片用）
     * @param dateTime 笔记的日期时间
     * @param entry 笔记条目
     * @param images 笔记中的图片 Bitmap 列表
     * @param videoCovers 笔记中的视频封面 Bitmap 列表
     * @param config 用户自定义配置（背景色、文字颜色等）
     * @return 渲染完成的 Bitmap
     */
    fun render(
        context: android.content.Context?,
        dateTime: LocalDateTime,
        entry: NoteEntry,
        images: List<Bitmap>,
        videoCovers: List<Bitmap>,
        config: TemplateConfig = TemplateConfig(),
    ): Bitmap
}

/**
 * 模板可调配置项。
 * 用户在分享弹窗中修改的参数都通过此对象传入。
 */
data class TemplateConfig(
    val bgColor: Long = 0xFFFAFAFA,
    val textColor: Long = 0xFF1A1A1A,
    val showWatermark: Boolean = true,
    val watermarkText: String = "来自「寄意」笔记",
    val canvasWidth: Int = 1080,
    val canvasMaxHeight: Int = 1920,
)

/**
 * 模板注册表。
 * 管理所有可用模板（内置 + 用户自定义）。
 */
object TemplateRegistry {

    private val engines = mutableMapOf<String, TemplateEngine>()

    /** 内置模板实例 */
    private val builtinTemplates by lazy {
        listOf(
            MinimalTemplate(),
            CardTemplate(),
            MagazineTemplate(),
        )
    }

    init {
        builtinTemplates.forEach { engines[it.id] = it }
    }

    /** 获取模板引擎 */
    fun get(id: String): TemplateEngine? = engines[id]

    /** 获取所有可用模板 */
    fun getAll(): List<TemplateEngine> = engines.values.toList()

    /** 获取内置模板 */
    fun getBuiltin(): List<TemplateEngine> = builtinTemplates

    /** 注册自定义模板（用户 JSON 模板等） */
    fun register(engine: TemplateEngine) {
        engines[engine.id] = engine
    }

    /** 注销模板 */
    fun unregister(id: String) {
        engines.remove(id)
    }
}
