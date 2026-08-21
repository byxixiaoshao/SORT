package com.bicy.note.share

import android.content.Context
import android.graphics.Bitmap
import com.bicy.note.data.model.NoteEntry
import kotlinx.serialization.json.*
import java.time.LocalDateTime

/**
 * JSON 模板：用户通过 JSON 定义模板结构，由 [JsonTemplate] 解析并渲染。
 *
 * JSON 格式示例见 TEMPLATE_DEVELOPMENT_GUIDE.md
 */
class JsonTemplate(
    internal val data: JsonTemplateData,
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
                    is LayoutNode.DateNode -> node.copy(color = config.dateColor)
                    is LayoutNode.LineNode -> node.copy(color = config.dividerColor)
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

    fun toJson(): String {
        val obj = buildJsonObject {
            put("id", data.id)
            put("name", data.name)
            if (data.description.isNotEmpty()) put("description", data.description)
            putJsonObject("canvas") {
                put("width", data.canvas.width)
                put("height", data.canvas.height)
                putJsonObject("background") {
                    put("type", data.canvas.background.type)
                    put("color", data.canvas.background.color)
                    if (data.canvas.background.type == "gradient") {
                        put("colorEnd", data.canvas.background.colorEnd)
                    }
                }
            }
            putJsonArray("nodes") {
                data.nodes.forEach { node ->
                    addJsonObject { writeNode(node) }
                }
            }
            if (data.decorations.isNotEmpty()) {
                putJsonArray("decorations") {
                    data.decorations.forEach { deco ->
                        addJsonObject { writeDecoration(deco) }
                    }
                }
            }
        }
        return Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), obj)
    }

    companion object {
        private val JsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

        fun fromJson(json: String): JsonTemplate? {
            return try {
                val root = JsonParser.parseToJsonElement(json.trim()) as? JsonObject ?: return null
                val data = parseTemplateData(root)
                JsonTemplate(data)
            } catch (_: Exception) {
                null
            }
        }

        private fun parseTemplateData(root: JsonObject): JsonTemplateData {
            val id = root["id"]?.jsonPrimitive?.contentOrNull ?: "user_custom"
            val name = root["name"]?.jsonPrimitive?.contentOrNull ?: "我的模板"
            val description = root["description"]?.jsonPrimitive?.contentOrNull ?: ""

            val canvasObj = root["canvas"]?.jsonObject
            val canvas = if (canvasObj != null) parseCanvas(canvasObj) else CanvasConfig()

            val nodes = root["nodes"]?.jsonArray?.mapNotNull { parseNode(it as? JsonObject) } ?: emptyList()
            val decorations = root["decorations"]?.jsonArray?.mapNotNull { parseDecoration(it as? JsonObject) } ?: emptyList()

            return JsonTemplateData(id = id, name = name, description = description, canvas = canvas, nodes = nodes, decorations = decorations)
        }

        private fun parseCanvas(obj: JsonObject): CanvasConfig {
            val w = obj["width"]?.jsonPrimitive?.intOrNull ?: 1080
            val h = obj["height"]?.jsonPrimitive?.intOrNull ?: 1920
            val bgObj = obj["background"]?.jsonObject
            val bg = if (bgObj != null) {
                BackgroundConfig(
                    type = bgObj["type"]?.jsonPrimitive?.contentOrNull ?: "solid",
                    color = bgObj["color"]?.jsonPrimitive?.longOrNull ?: 0xFFFAFAFA,
                    colorEnd = bgObj["colorEnd"]?.jsonPrimitive?.longOrNull ?: 0xFFFAFAFA,
                )
            } else BackgroundConfig()
            val corner = obj["cornerRadius"]?.jsonPrimitive?.floatOrNull ?: 0f
            return CanvasConfig(width = w, height = h, background = bg, cornerRadius = corner)
        }

        private fun parseNode(obj: JsonObject?): LayoutNode? {
            if (obj == null) return null
            val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: return null
            return when (type) {
                "TextNode" -> LayoutNode.TextNode(
                    id = obj.str("id", "text"),
                    x = obj.flt("x", 0f), y = obj.flt("y", 0f),
                    width = obj.flt("width", -1f), height = obj.flt("height", -1f),
                    rotation = obj.flt("rotation", 0f), zIndex = obj.int("zIndex", 0), opacity = obj.flt("opacity", 1f),
                    content = obj.str("content", ""),
                    fontSize = obj.int("fontSize", 48),
                    color = obj.lng("color", 0xFF1A1A1A),
                    fontWeight = obj.int("fontWeight", 400),
                    maxLines = obj.int("maxLines", Int.MAX_VALUE),
                    textAlign = obj.str("textAlign", "left"),
                )
                "ImageNode" -> LayoutNode.ImageNode(
                    id = obj.str("id", "image"),
                    x = obj.flt("x", 0f), y = obj.flt("y", 0f),
                    width = obj.flt("width", -1f), height = obj.flt("height", -1f),
                    rotation = obj.flt("rotation", 0f), zIndex = obj.int("zIndex", 0), opacity = obj.flt("opacity", 1f),
                    source = obj.str("source", "auto"),
                    scaleType = obj.str("scaleType", "cover"),
                    borderRadius = obj.flt("borderRadius", 0f),
                )
                "DateNode" -> LayoutNode.DateNode(
                    id = obj.str("id", "date"),
                    x = obj.flt("x", 0f), y = obj.flt("y", 0f),
                    width = obj.flt("width", -1f), height = obj.flt("height", -1f),
                    rotation = obj.flt("rotation", 0f), zIndex = obj.int("zIndex", 0), opacity = obj.flt("opacity", 1f),
                    format = obj.str("format", "yyyy年M月d日 HH:mm"),
                    fontSize = obj.int("fontSize", 36),
                    color = obj.lng("color", 0xFF888888),
                )
                "MarkerNode" -> LayoutNode.MarkerNode(
                    id = obj.str("id", "marker"),
                    x = obj.flt("x", 0f), y = obj.flt("y", 0f),
                    width = obj.flt("width", -1f), height = obj.flt("height", -1f),
                    rotation = obj.flt("rotation", 0f), zIndex = obj.int("zIndex", 0), opacity = obj.flt("opacity", 1f),
                    size = obj.int("size", 16),
                )
                "SpacerNode" -> LayoutNode.SpacerNode(
                    id = obj.str("id", "spacer"),
                    x = obj.flt("x", 0f), y = obj.flt("y", 0f),
                    width = obj.flt("width", -1f), height = obj.flt("height", 60f),
                    rotation = obj.flt("rotation", 0f), zIndex = obj.int("zIndex", 0), opacity = obj.flt("opacity", 1f),
                )
                "LineNode" -> LayoutNode.LineNode(
                    id = obj.str("id", "line"),
                    x = obj.flt("x", 0f), y = obj.flt("y", 0f),
                    width = obj.flt("width", 100f), height = obj.flt("height", 2f),
                    rotation = obj.flt("rotation", 0f), zIndex = obj.int("zIndex", 0), opacity = obj.flt("opacity", 1f),
                    color = obj.lng("color", 0xFFE0E0E0),
                    thickness = obj.flt("thickness", 2f),
                )
                "ImageGridNode" -> LayoutNode.ImageGridNode(
                    id = obj.str("id", "image_grid"),
                    x = obj.flt("x", 0f), y = obj.flt("y", 0f),
                    width = obj.flt("width", -1f), height = obj.flt("height", -1f),
                    rotation = obj.flt("rotation", 0f), zIndex = obj.int("zIndex", 0), opacity = obj.flt("opacity", 1f),
                    columns = obj.int("columns", 2),
                    spacing = obj.flt("spacing", 12f),
                    borderRadius = obj.flt("borderRadius", 12f),
                    maxImages = obj.int("maxImages", 9),
                )
                "WatermarkNode" -> LayoutNode.WatermarkNode(
                    id = obj.str("id", "watermark"),
                    x = obj.flt("x", 0f), y = obj.flt("y", 0f),
                    width = obj.flt("width", -1f), height = obj.flt("height", -1f),
                    rotation = obj.flt("rotation", 0f), zIndex = obj.int("zIndex", 0), opacity = obj.flt("opacity", 0.5f),
                    text = obj.str("text", "来自「寄意」笔记"),
                    fontSize = obj.int("fontSize", 28),
                    color = obj.lng("color", 0xFFBBBBBB),
                )
                else -> null
            }
        }

        private fun parseDecoration(obj: JsonObject?): Decoration? {
            if (obj == null) return null
            val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: return null
            return when (type) {
                "BorderDecoration" -> Decoration.BorderDecoration(
                    id = obj.str("id", "border"),
                    color = obj.lng("color", 0xFFE0E0E0),
                    thickness = obj.flt("thickness", 2f),
                    cornerRadius = obj.flt("cornerRadius", 20f),
                    style = obj.str("style", "solid"),
                )
                "LineDecoration" -> Decoration.LineDecoration(
                    id = obj.str("id", "deco_line"),
                    y = obj.flt("y", 50f),
                    color = obj.lng("color", 0xFFE0E0E0),
                    thickness = obj.flt("thickness", 1f),
                    style = obj.str("style", "solid"),
                )
                "CornerDecoration" -> Decoration.CornerDecoration(
                    id = obj.str("id", "corner"),
                    cornerType = obj.str("cornerType", "flourish"),
                    color = obj.lng("color", 0xFFD4AF37),
                    size = obj.flt("size", 40f),
                    thickness = obj.flt("thickness", 2f),
                )
                else -> null
            }
        }

        private fun JsonObject.str(key: String, default: String): String =
            this[key]?.jsonPrimitive?.contentOrNull ?: default
        private fun JsonObject.flt(key: String, default: Float): Float =
            this[key]?.jsonPrimitive?.floatOrNull ?: default
        private fun JsonObject.int(key: String, default: Int): Int =
            this[key]?.jsonPrimitive?.intOrNull ?: default
        private fun JsonObject.lng(key: String, default: Long): Long =
            this[key]?.jsonPrimitive?.longOrNull ?: default

        private fun JsonObjectBuilder.writeNode(node: LayoutNode) {
            when (node) {
                is LayoutNode.TextNode -> {
                    put("type", "TextNode")
                    put("id", node.id)
                    put("x", node.x); put("y", node.y)
                    put("width", node.width); put("height", node.height)
                    put("content", node.content)
                    put("fontSize", node.fontSize); put("color", node.color)
                    put("fontWeight", node.fontWeight); put("textAlign", node.textAlign)
                }
                is LayoutNode.ImageNode -> {
                    put("type", "ImageNode")
                    put("id", node.id)
                    put("x", node.x); put("y", node.y)
                    put("width", node.width); put("height", node.height)
                    put("source", node.source); put("scaleType", node.scaleType)
                    put("borderRadius", node.borderRadius)
                }
                is LayoutNode.DateNode -> {
                    put("type", "DateNode")
                    put("id", node.id)
                    put("x", node.x); put("y", node.y)
                    put("format", node.format)
                    put("fontSize", node.fontSize); put("color", node.color)
                }
                is LayoutNode.MarkerNode -> {
                    put("type", "MarkerNode")
                    put("id", node.id)
                    put("x", node.x); put("y", node.y)
                    put("size", node.size)
                }
                is LayoutNode.SpacerNode -> {
                    put("type", "SpacerNode")
                    put("id", node.id)
                    put("x", node.x); put("y", node.y)
                    put("height", node.height)
                }
                is LayoutNode.LineNode -> {
                    put("type", "LineNode")
                    put("id", node.id)
                    put("x", node.x); put("y", node.y)
                    put("width", node.width); put("height", node.height)
                    put("color", node.color); put("thickness", node.thickness)
                }
                is LayoutNode.ImageGridNode -> {
                    put("type", "ImageGridNode")
                    put("id", node.id)
                    put("x", node.x); put("y", node.y)
                    put("width", node.width)
                    put("columns", node.columns); put("spacing", node.spacing)
                    put("borderRadius", node.borderRadius); put("maxImages", node.maxImages)
                }
                is LayoutNode.WatermarkNode -> {
                    put("type", "WatermarkNode")
                    put("id", node.id)
                    put("x", node.x); put("y", node.y)
                    put("text", node.text)
                    put("fontSize", node.fontSize); put("color", node.color)
                    put("opacity", node.opacity)
                }
            }
        }

        private fun JsonObjectBuilder.writeDecoration(deco: Decoration) {
            when (deco) {
                is Decoration.BorderDecoration -> {
                    put("type", "BorderDecoration")
                    put("id", deco.id)
                    put("color", deco.color); put("thickness", deco.thickness)
                    put("cornerRadius", deco.cornerRadius); put("style", deco.style)
                }
                is Decoration.LineDecoration -> {
                    put("type", "LineDecoration")
                    put("id", deco.id)
                    put("y", deco.y); put("color", deco.color)
                    put("thickness", deco.thickness); put("style", deco.style)
                }
                is Decoration.CornerDecoration -> {
                    put("type", "CornerDecoration")
                    put("id", deco.id)
                    put("cornerType", deco.cornerType); put("color", deco.color)
                    put("size", deco.size); put("thickness", deco.thickness)
                }
            }
        }
    }
}

private fun LayoutNode.WatermarkNode.coerceOpacity(): Float =
    if (opacity <= 0f) 0.5f else opacity

/** JsonTemplateData 不再需要 @Serializable，改为普通 data class */
data class JsonTemplateData(
    val id: String,
    val name: String,
    val description: String,
    val canvas: CanvasConfig,
    val nodes: List<LayoutNode>,
    val decorations: List<Decoration>,
)
