package com.bicy.note.share

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TemplateSchema(
    val id: String = "builtin_minimal",
    val name: String = "极简",
    val canvas: CanvasConfig = CanvasConfig(),
    val nodes: List<LayoutNode> = emptyList(),
    val decorations: List<Decoration> = emptyList(),
)

@Serializable
data class CanvasConfig(
    val width: Int = 1080,
    val height: Int = 1920,
    val background: BackgroundConfig = BackgroundConfig(),
    val cornerRadius: Float = 0f,
)

@Serializable
data class BackgroundConfig(
    val type: String = "solid",           // solid / gradient
    val color: Long = 0xFFFAFAFA,
    val colorEnd: Long = 0xFFFAFAFA,      // gradient 终止色
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
sealed class LayoutNode {
    abstract val id: String
    abstract val x: Float                 // 百分比 0~100
    abstract val y: Float
    abstract val width: Float             // -1 = auto
    abstract val height: Float            // -1 = auto
    abstract val rotation: Float
    abstract val zIndex: Int
    abstract val opacity: Float

    @Serializable
    data class TextNode(
        override val id: String = "text",
        override val x: Float = 0f,
        override val y: Float = 0f,
        override val width: Float = -1f,
        override val height: Float = -1f,
        override val rotation: Float = 0f,
        override val zIndex: Int = 0,
        override val opacity: Float = 1f,
        val content: String = "",         // 静态文本，或占位符如 "{{text}}"
        val fontSize: Int = 48,
        val color: Long = 0xFF1A1A1A,
        val fontWeight: Int = 400,        // 100~900
        val maxLines: Int = Int.MAX_VALUE,
        val textAlign: String = "left",   // left / center / right
    ) : LayoutNode()

    @Serializable
    data class ImageNode(
        override val id: String = "image",
        override val x: Float = 0f,
        override val y: Float = 0f,
        override val width: Float = -1f,
        override val height: Float = -1f,
        override val rotation: Float = 0f,
        override val zIndex: Int = 0,
        override val opacity: Float = 1f,
        val source: String = "auto",      // auto = 从笔记填充，或指定文件名
        val scaleType: String = "cover",  // cover / fit / fill
        val borderRadius: Float = 0f,
    ) : LayoutNode()

    @Serializable
    data class DateNode(
        override val id: String = "date",
        override val x: Float = 0f,
        override val y: Float = 0f,
        override val width: Float = -1f,
        override val height: Float = -1f,
        override val rotation: Float = 0f,
        override val zIndex: Int = 0,
        override val opacity: Float = 1f,
        val format: String = "yyyy年M月d日 HH:mm",
        val fontSize: Int = 36,
        val color: Long = 0xFF888888,
    ) : LayoutNode()

    @Serializable
    data class MarkerNode(
        override val id: String = "marker",
        override val x: Float = 0f,
        override val y: Float = 0f,
        override val width: Float = -1f,
        override val height: Float = -1f,
        override val rotation: Float = 0f,
        override val zIndex: Int = 0,
        override val opacity: Float = 1f,
        val size: Int = 16,
    ) : LayoutNode()

    @Serializable
    data class SpacerNode(
        override val id: String = "spacer",
        override val x: Float = 0f,
        override val y: Float = 0f,
        override val width: Float = -1f,
        override val height: Float = 60f,
        override val rotation: Float = 0f,
        override val zIndex: Int = 0,
        override val opacity: Float = 1f,
    ) : LayoutNode()

    @Serializable
    data class LineNode(
        override val id: String = "line",
        override val x: Float = 0f,
        override val y: Float = 0f,
        override val width: Float = 100f,  // 百分比宽度
        override val height: Float = 2f,
        override val rotation: Float = 0f,
        override val zIndex: Int = 0,
        override val opacity: Float = 1f,
        val color: Long = 0xFFE0E0E0,
        val thickness: Float = 2f,
    ) : LayoutNode()

    @Serializable
    data class ImageGridNode(
        override val id: String = "image_grid",
        override val x: Float = 0f,
        override val y: Float = 0f,
        override val width: Float = -1f,
        override val height: Float = -1f,
        override val rotation: Float = 0f,
        override val zIndex: Int = 0,
        override val opacity: Float = 1f,
        val columns: Int = 2,
        val spacing: Float = 12f,
        val borderRadius: Float = 12f,
        val maxImages: Int = 9,
    ) : LayoutNode()

    @Serializable
    data class WatermarkNode(
        override val id: String = "watermark",
        override val x: Float = 0f,
        override val y: Float = 0f,
        override val width: Float = -1f,
        override val height: Float = -1f,
        override val rotation: Float = 0f,
        override val zIndex: Int = 0,
        override val opacity: Float = 0.5f,
        val text: String = "来自「寄意」笔记",
        val fontSize: Int = 28,
        val color: Long = 0xFFBBBBBB,
    ) : LayoutNode()
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
sealed class Decoration {
    abstract val id: String

    @Serializable
    data class BorderDecoration(
        override val id: String = "border",
        val color: Long = 0xFFE0E0E0,
        val thickness: Float = 2f,
        val cornerRadius: Float = 20f,
        val style: String = "solid",       // solid / dashed / dotted
    ) : Decoration()

    @Serializable
    data class LineDecoration(
        override val id: String = "deco_line",
        val y: Float = 50f,               // 百分比位置
        val color: Long = 0xFFE0E0E0,
        val thickness: Float = 1f,
        val style: String = "solid",
    ) : Decoration()

    @Serializable
    data class CornerDecoration(
        override val id: String = "corner",
        val cornerType: String = "flourish", // flourish / leaf / star / diamond
        val color: Long = 0xFFD4AF37,
        val size: Float = 40f,
        val thickness: Float = 2f,
    ) : Decoration()
}
