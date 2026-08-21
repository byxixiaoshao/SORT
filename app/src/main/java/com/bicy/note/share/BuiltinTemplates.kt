package com.bicy.note.share

object BuiltinTemplates {

    val minimal = TemplateSchema(
        id = "builtin_minimal",
        name = "极简",
        canvas = CanvasConfig(
            width = 1080,
            height = 1920,
            background = BackgroundConfig(type = "solid", color = 0xFFFAFAFA),
        ),
        nodes = listOf(
            LayoutNode.DateNode(
                id = "date", x = 6f, y = 4f,
                fontSize = 32, color = 0xFF888888,
            ),
            LayoutNode.LineNode(
                id = "divider1", x = 6f, y = 8f,
                width = 88f, thickness = 1f, color = 0xFFE0E0E0,
            ),
            LayoutNode.TextNode(
                id = "text", x = 6f, y = 10f,
                width = 88f, fontSize = 48,
                color = 0xFF1A1A1A, maxLines = 20,
                content = "{{text}}",
            ),
            LayoutNode.ImageGridNode(
                id = "images", x = 6f, y = -1f,
                width = 88f, columns = 2,
                spacing = 12f, borderRadius = 12f,
                maxImages = 9,
            ),
            LayoutNode.WatermarkNode(
                id = "watermark", x = 50f, y = 95f,
                fontSize = 28, color = 0xFFBBBBBB,
                text = "来自「寄意」笔记",
            ),
        ),
        decorations = listOf(
            Decoration.BorderDecoration(
                id = "border",
                color = 0xFFE8E8E8, thickness = 2f,
                cornerRadius = 24f, style = "solid",
            ),
        ),
    )

    val card = TemplateSchema(
        id = "builtin_card",
        name = "卡片",
        canvas = CanvasConfig(
            width = 1080,
            height = 1920,
            background = BackgroundConfig(type = "solid", color = 0xFFF5F5F5),
        ),
        nodes = listOf(
            LayoutNode.DateNode(
                id = "date", x = 10f, y = 5f,
                fontSize = 30, color = 0xFF999999,
            ),
            LayoutNode.TextNode(
                id = "text", x = 10f, y = 10f,
                width = 80f, fontSize = 52,
                color = 0xFF222222, fontWeight = 700,
                maxLines = 20,
                content = "{{text}}",
            ),
            LayoutNode.LineNode(
                id = "divider", x = 10f, y = 60f,
                width = 80f, thickness = 1f, color = 0xFFDDDDDD,
            ),
            LayoutNode.ImageGridNode(
                id = "images", x = 10f, y = 62f,
                width = 80f, columns = 2,
                spacing = 16f, borderRadius = 16f,
                maxImages = 6,
            ),
            LayoutNode.WatermarkNode(
                id = "watermark", x = 50f, y = 95f,
                fontSize = 26, color = 0xFFCCCCCC,
                text = "寄意",
            ),
        ),
        decorations = listOf(
            Decoration.BorderDecoration(
                id = "border",
                color = 0xFFDDDDDD, thickness = 2f,
                cornerRadius = 32f, style = "solid",
            ),
        ),
    )

    val magazine = TemplateSchema(
        id = "builtin_magazine",
        name = "杂志",
        canvas = CanvasConfig(
            width = 1080,
            height = 1920,
            background = BackgroundConfig(type = "solid", color = 0xFF1A1A1A),
        ),
        nodes = listOf(
            LayoutNode.ImageGridNode(
                id = "images", x = 0f, y = 0f,
                width = 100f, columns = 1,
                spacing = 0f, borderRadius = 0f,
                maxImages = 1,
            ),
            LayoutNode.TextNode(
                id = "text", x = 8f, y = 55f,
                width = 84f, fontSize = 56,
                color = 0xFFFFFFFF, fontWeight = 700,
                maxLines = 10,
                content = "{{text}}",
            ),
            LayoutNode.LineNode(
                id = "accent", x = 8f, y = 90f,
                width = 20f, thickness = 4f,
                color = 0xFFD4AF37,
            ),
            LayoutNode.DateNode(
                id = "date", x = 8f, y = 92f,
                fontSize = 28, color = 0xFF888888,
            ),
            LayoutNode.WatermarkNode(
                id = "watermark", x = 50f, y = 97f,
                fontSize = 24, color = 0xFF666666,
                text = "寄意",
            ),
        ),
        decorations = listOf(
            Decoration.CornerDecoration(
                id = "corner",
                cornerType = "diamond",
                color = 0xFFD4AF37,
                size = 50f, thickness = 2f,
            ),
        ),
    )

    val all = listOf(minimal, card, magazine)

    fun getById(id: String): TemplateSchema =
        all.firstOrNull { it.id == id } ?: minimal
}
