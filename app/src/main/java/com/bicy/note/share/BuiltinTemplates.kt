package com.bicy.note.share

import android.content.Context
import android.graphics.Bitmap
import com.bicy.note.data.model.NoteEntry
import java.time.LocalDateTime

class MinimalTemplate : TemplateEngine {
    override val id = "builtin_minimal"
    override val name = "极简"
    override val description = "简洁白底，文字居上，图片网格居下"

    override fun render(
        context: Context?,
        dateTime: LocalDateTime,
        entry: NoteEntry,
        images: List<Bitmap>,
        videoCovers: List<Bitmap>,
        config: TemplateConfig,
    ): Bitmap {
        val schema = TemplateSchema(
            id = id, name = name,
            canvas = CanvasConfig(
                width = config.canvasWidth, height = config.canvasMaxHeight,
                background = BackgroundConfig(type = "solid", color = config.bgColor),
            ),
            nodes = listOf(
                LayoutNode.DateNode(id = "date", x = 8f, y = 5f, fontSize = 32, color = config.dateColor),
                LayoutNode.LineNode(id = "div", x = 8f, y = -1f, width = 84f, thickness = 1f, color = config.dividerColor),
                LayoutNode.SpacerNode(id = "s0", x = 0f, y = -1f, height = 60f),
                LayoutNode.TextNode(id = "text", x = 8f, y = -1f, width = 84f, fontSize = 44,
                    color = config.textColor, maxLines = 30, content = "{{text}}"),
                LayoutNode.SpacerNode(id = "s1", x = 0f, y = -1f, height = 80f),
                LayoutNode.ImageGridNode(id = "img", x = 8f, y = -1f, width = 84f, columns = 2,
                    spacing = 16f, borderRadius = 12f, maxImages = 9),
                LayoutNode.SpacerNode(id = "s2", x = 0f, y = -1f, height = 100f),
                LayoutNode.WatermarkNode(id = "wm", x = 50f, y = -1f, fontSize = 26,
                    color = config.dateColor, opacity = if (config.showWatermark) 0.5f else 0f,
                    text = config.watermarkText),
            ),
            decorations = listOf(
                Decoration.BorderDecoration(id = "b", color = config.dividerColor, thickness = 2f, cornerRadius = 20f),
            ),
        )
        return CanvasRenderer.render(context, schema, dateTime, entry, images, videoCovers)
    }
}

class CardTemplate : TemplateEngine {
    override val id = "builtin_card"
    override val name = "卡片"
    override val description = "浅灰底圆角卡片，正文加粗"

    override fun render(
        context: Context?,
        dateTime: LocalDateTime,
        entry: NoteEntry,
        images: List<Bitmap>,
        videoCovers: List<Bitmap>,
        config: TemplateConfig,
    ): Bitmap {
        val schema = TemplateSchema(
            id = id, name = name,
            canvas = CanvasConfig(
                width = config.canvasWidth, height = config.canvasMaxHeight,
                background = BackgroundConfig(type = "solid", color = config.bgColor),
            ),
            nodes = listOf(
                LayoutNode.DateNode(id = "date", x = 10f, y = 5f, fontSize = 28, color = config.dateColor),
                LayoutNode.SpacerNode(id = "s0", x = 0f, y = -1f, height = 50f),
                LayoutNode.TextNode(id = "text", x = 10f, y = -1f, width = 80f, fontSize = 44,
                    color = config.textColor, fontWeight = 700, maxLines = 30, content = "{{text}}"),
                LayoutNode.SpacerNode(id = "s1", x = 0f, y = -1f, height = 60f),
                LayoutNode.LineNode(id = "div", x = 10f, y = -1f, width = 80f, thickness = 1f, color = config.dividerColor),
                LayoutNode.SpacerNode(id = "s2", x = 0f, y = -1f, height = 60f),
                LayoutNode.ImageGridNode(id = "img", x = 10f, y = -1f, width = 80f, columns = 2,
                    spacing = 14f, borderRadius = 14f, maxImages = 6),
                LayoutNode.SpacerNode(id = "s3", x = 0f, y = -1f, height = 80f),
                LayoutNode.WatermarkNode(id = "wm", x = 50f, y = -1f, fontSize = 24,
                    color = config.dateColor, opacity = if (config.showWatermark) 0.5f else 0f,
                    text = config.watermarkText),
            ),
            decorations = listOf(
                Decoration.BorderDecoration(id = "b", color = config.dividerColor, thickness = 2f, cornerRadius = 28f),
            ),
        )
        return CanvasRenderer.render(context, schema, dateTime, entry, images, videoCovers)
    }
}

class MagazineTemplate : TemplateEngine {
    override val id = "builtin_magazine"
    override val name = "杂志"
    override val description = "深色底，大图占满顶部，金色装饰线"

    override fun render(
        context: Context?,
        dateTime: LocalDateTime,
        entry: NoteEntry,
        images: List<Bitmap>,
        videoCovers: List<Bitmap>,
        config: TemplateConfig,
    ): Bitmap {
        val schema = TemplateSchema(
            id = id, name = name,
            canvas = CanvasConfig(
                width = config.canvasWidth, height = config.canvasMaxHeight,
                background = BackgroundConfig(type = "solid", color = config.bgColor),
            ),
            nodes = listOf(
                LayoutNode.ImageGridNode(id = "img", x = 0f, y = 0f, width = 100f, columns = 1,
                    spacing = 0f, borderRadius = 0f, maxImages = 1),
                LayoutNode.TextNode(id = "text", x = 8f, y = 55f, width = 84f, fontSize = 56,
                    color = config.textColor, fontWeight = 700, maxLines = 10, content = "{{text}}"),
                LayoutNode.LineNode(id = "accent", x = 8f, y = 90f, width = 20f, thickness = 4f, color = config.dividerColor),
                LayoutNode.DateNode(id = "date", x = 8f, y = 92f, fontSize = 28, color = config.dateColor),
                LayoutNode.WatermarkNode(id = "wm", x = 50f, y = 97f, fontSize = 24,
                    color = config.dateColor, opacity = if (config.showWatermark) 0.5f else 0f,
                    text = config.watermarkText),
            ),
            decorations = listOf(
                Decoration.CornerDecoration(id = "c", cornerType = "diamond", color = config.dividerColor, size = 50f, thickness = 2f),
            ),
        )
        return CanvasRenderer.render(context, schema, dateTime, entry, images, videoCovers)
    }
}
