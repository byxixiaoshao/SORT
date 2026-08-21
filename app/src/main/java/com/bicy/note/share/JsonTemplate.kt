package com.bicy.note.share

import android.content.Context
import android.graphics.Bitmap
import com.bicy.note.data.model.NoteEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDateTime

/**
 * JSON 模板：用户通过 JSON 定义模板结构，由 [JsonTemplate] 解析并渲染。
 *
 * JSON 格式示例见 TEMPLATE_DEVELOPMENT_GUIDE.md
 */
@Serializable
data class JsonTemplateData(
    val id: String = "user_${System.currentTimeMillis()}",
    val name: String = "我的模板",
    val description: String = "",
    val canvas: CanvasConfig = CanvasConfig(),
    val nodes: List<LayoutNode> = emptyList(),
    val decorations: List<Decoration> = emptyList(),
)

/**
 * 用户 JSON 模板引擎。
 * 从 JSON 字符串解析模板结构，通过 [CanvasRenderer] 渲染。
 */
class JsonTemplate(
    private val data: JsonTemplateData,
) : TemplateEngine {

    override val id: String get() = data.id
    override val name: String get() = data.name
    override val description: String get() = data.description

    override fun render(
        context: Context?,
        dateTime: LocalDateTime,
        entry: NoteEntry,
        images: List<Bitmap>,
        videoCovers: List<Bitmap>,
        config: TemplateConfig,
    ): Bitmap {
        val schema = TemplateSchema(
            id = data.id,
            name = data.name,
            canvas = data.canvas.copy(
                width = config.canvasWidth,
                height = config.canvasMaxHeight,
                background = data.canvas.background.copy(color = config.bgColor),
            ),
            nodes = data.nodes.map { node ->
                when (node) {
                    is LayoutNode.TextNode -> node.copy(color = config.textColor)
                    is LayoutNode.WatermarkNode -> node.copy(
                        opacity = if (config.showWatermark) node.coerceOpacity() else 0f,
                        text = config.watermarkText,
                    )
                    else -> node
                }
            },
            decorations = data.decorations,
        )
        return CanvasRenderer.render(context, schema, dateTime, entry, images, videoCovers)
    }

    /** 将 JSON 模板数据序列化为 JSON 字符串 */
    fun toJson(): String = Json.encodeToString(JsonTemplateData.serializer(), data)

    companion object {
        private val JsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

        /** 从 JSON 字符串解析模板 */
        fun fromJson(json: String): JsonTemplate? {
            return try {
                val data = JsonParser.decodeFromString(JsonTemplateData.serializer(), json.trim())
                JsonTemplate(data)
            } catch (_: Exception) {
                null
            }
        }
    }
}

/** WatermarkNode 的 opacity 默认值处理 */
private fun LayoutNode.WatermarkNode.coerceOpacity(): Float =
    if (opacity <= 0f) 0.5f else opacity
