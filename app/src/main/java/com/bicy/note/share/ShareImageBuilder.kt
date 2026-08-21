package com.bicy.note.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.bicy.note.R
import com.bicy.note.data.model.MARKER_CIRCLE
import com.bicy.note.data.model.MARKER_HEART
import com.bicy.note.data.model.MARKER_STAR
import com.bicy.note.data.model.NoteEntry
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Canvas 渲染引擎。
 * 将 TemplateSchema + 笔记内容渲染为 Bitmap。
 * 被 [TemplateEngine] 实现调用，不直接对外使用。
 */
object CanvasRenderer {

    private val markerLabels = mapOf(
        MARKER_STAR to "临时",
        MARKER_HEART to "收藏",
        MARKER_CIRCLE to "",
    )
    private val markerColors = mapOf(
        MARKER_STAR to Color.parseColor("#FF9800"),
        MARKER_HEART to Color.parseColor("#F44336"),
        MARKER_CIRCLE to Color.parseColor("#9E9E9E"),
    )

    fun render(
        context: Context?,
        schema: TemplateSchema,
        dateTime: LocalDateTime,
        entry: NoteEntry,
        images: List<Bitmap>,
        videoCovers: List<Bitmap>,
    ): Bitmap {
        return renderInternal(schema, dateTime, entry, images, videoCovers)
    }

    /** 兼容旧入口：LocalDate + entry.time */
    fun renderFromDate(
        context: Context?,
        schema: TemplateSchema,
        date: LocalDate,
        entry: NoteEntry,
        images: List<Bitmap>,
        videoCovers: List<Bitmap>,
    ): Bitmap {
        val time = try { LocalTime.parse(entry.time) } catch (_: Exception) { LocalTime.NOON }
        val dateTime = LocalDateTime.of(date, time)
        return renderInternal(schema, dateTime, entry, images, videoCovers)
    }

    private fun renderInternal(
        schema: TemplateSchema,
        dateTime: LocalDateTime,
        entry: NoteEntry,
        images: List<Bitmap>,
        videoCovers: List<Bitmap>,
        customCanvasHeight: Int? = null,
    ): Bitmap {
        val c = schema.canvas
        // 自适应高度：先测量内容高度，再决定画布尺寸
        val contentHeight = measureContentHeight(schema, dateTime, entry, images, videoCovers, c.width)
        val canvasH = customCanvasHeight ?: (contentHeight + 80f).toInt().coerceIn(400, c.height)

        val bitmap = Bitmap.createBitmap(c.width, canvasH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val adjustedCanvas = c.copy(height = canvasH)

        drawBackground(canvas, adjustedCanvas)
        schema.decorations.forEach { drawDecoration(canvas, it, adjustedCanvas) }

        var autoY = 0f

        for (node in schema.nodes) {
            val px = node.x / 100f * c.width
            val py = if (node.y < 0) autoY else node.y / 100f * canvasH

            when (node) {
                is LayoutNode.TextNode -> {
                    val text = resolveTextPlaceholder(node.content, dateTime.toLocalDate(), entry)
                    val drawn = drawText(canvas, text, node, px, py, adjustedCanvas)
                    autoY = py + drawn + 40f
                }
                is LayoutNode.DateNode -> {
                    val text = dateTime.format(DateTimeFormatter.ofPattern(node.format))
                    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = node.fontSize.toFloat()
                        color = node.color.toInt()
                    }
                    canvas.drawText(text, px, py + node.fontSize, paint)
                    autoY = py + node.fontSize + 30f
                }
                is LayoutNode.MarkerNode -> {
                    val marker = entry.effectiveMarker()
                    val label = markerLabels[marker] ?: ""
                    if (label.isNotEmpty()) {
                        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                            textSize = node.size.toFloat()
                            color = markerColors[marker] ?: Color.GRAY
                            typeface = Typeface.DEFAULT_BOLD
                        }
                        canvas.drawText(label, px, py + node.size, paint)
                    }
                }
                is LayoutNode.LineNode -> {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = node.color.toInt()
                        strokeWidth = node.thickness
                        style = Paint.Style.STROKE
                    }
                    if (node.height > node.width) {
                        // 垂直线
                        val lineH = node.height / 100f * canvasH
                        canvas.drawLine(px, py, px, py + lineH, paint)
                    } else {
                        // 水平线
                        val lineW = if (node.width > 0) node.width / 100f * c.width else c.width.toFloat()
                        canvas.drawLine(px, py, px + lineW, py, paint)
                    }
                    autoY = py + node.thickness + 30f
                }
                is LayoutNode.ImageNode -> {
                    val bmp = images.firstOrNull()
                    if (bmp != null) {
                        drawImage(canvas, bmp, node, px, py, adjustedCanvas)
                    }
                }
                is LayoutNode.ImageGridNode -> {
                    val allImages = images + videoCovers
                    val toDraw = allImages.take(node.maxImages)
                    if (toDraw.isNotEmpty()) {
                        val gridY = drawImageGrid(canvas, toDraw, node, px, py, adjustedCanvas)
                        autoY = gridY
                    }
                }
                is LayoutNode.WatermarkNode -> {
                    drawWatermark(canvas, node, adjustedCanvas)
                }
                is LayoutNode.SpacerNode -> {
                    autoY = py + node.height
                }
            }
        }

        return bitmap
    }

    private fun measureContentHeight(
        schema: TemplateSchema,
        dateTime: LocalDateTime,
        entry: NoteEntry,
        images: List<Bitmap>,
        videoCovers: List<Bitmap>,
        canvasWidth: Int,
    ): Float {
        var autoY = 0f

        for (node in schema.nodes) {
            val py = if (node.y < 0) autoY else node.y / 100f * schema.canvas.height

            when (node) {
                is LayoutNode.TextNode -> {
                    val text = resolveTextPlaceholder(node.content, dateTime.toLocalDate(), entry)
                    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = node.fontSize.toFloat()
                        typeface = if (node.fontWeight >= 700) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    }
                    val maxW = if (node.width > 0) (node.width / 100f * canvasWidth).toInt()
                    else (canvasWidth - node.x / 100f * canvasWidth * 2).toInt()
                    val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, maxW.coerceAtLeast(1))
                        .setLineSpacing(0f, 1.2f)
                        .setIncludePad(false)
                        .build()
                    autoY = py + layout.height + 40f
                }
                is LayoutNode.DateNode -> {
                    autoY = py + node.fontSize + 30f
                }
                is LayoutNode.LineNode -> {
                    if (node.height > node.width) {
                        // 垂直线不增加autoY
                    } else {
                        autoY = py + node.thickness + 30f
                    }
                }
                is LayoutNode.ImageGridNode -> {
                    val allImages = images + videoCovers
                    val toDraw = allImages.take(node.maxImages)
                    if (toDraw.isNotEmpty()) {
                        val totalW = if (node.width > 0) node.width / 100f * canvasWidth
                        else canvasWidth - node.x / 100f * canvasWidth * 2
                        val gap = node.spacing
                        val cellW = (totalW - gap * (node.columns - 1)) / node.columns
                        var gridH = 0f
                        for ((idx, bmp) in toDraw.withIndex()) {
                            val row = idx / node.columns
                            val cellH = cellW * bmp.height / bmp.width.toFloat()
                            gridH = maxOf(gridH, (row + 1) * cellH + row * gap)
                        }
                        autoY = py + gridH
                    }
                }
                is LayoutNode.SpacerNode -> {
                    autoY = py + node.height
                }
                else -> {}
            }
        }
        return autoY
    }

    private fun drawBackground(canvas: Canvas, config: CanvasConfig) {
        val bg = config.background
        if (bg.type == "gradient") {
            val shader = LinearGradient(
                0f, 0f, 0f, config.height.toFloat(),
                bg.color.toInt(), bg.colorEnd.toInt(), Shader.TileMode.CLAMP
            )
            canvas.drawColor(Color.WHITE)
            val paint = Paint().apply { this.shader = shader }
            canvas.drawRect(0f, 0f, config.width.toFloat(), config.height.toFloat(), paint)
        } else {
            canvas.drawColor(bg.color.toInt())
        }
    }

    private fun drawText(
        canvas: Canvas, text: String,
        node: LayoutNode.TextNode, x: Float, y: Float, config: CanvasConfig
    ): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = node.fontSize.toFloat()
            color = node.color.toInt()
            typeface = when {
                node.fontWeight >= 700 -> Typeface.DEFAULT_BOLD
                else -> Typeface.DEFAULT
            }
        }
        val maxW = if (node.width > 0) (node.width / 100f * config.width).toInt()
        else (config.width - x * 2).toInt()
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, maxW)
            .setAlignment(
                when (node.textAlign) {
                    "center" -> Layout.Alignment.ALIGN_CENTER
                    "right" -> Layout.Alignment.ALIGN_OPPOSITE
                    else -> Layout.Alignment.ALIGN_NORMAL
                }
            )
            .setLineSpacing(0f, 1.2f)
            .setIncludePad(false)
            .build()

        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()

        return layout.height.toFloat()
    }

    private fun drawImage(
        canvas: Canvas, bitmap: Bitmap,
        node: LayoutNode.ImageNode, x: Float, y: Float, config: CanvasConfig
    ) {
        val w = if (node.width > 0) node.width / 100f * config.width
        else config.width - x * 2
        val h = if (node.height > 0) node.height / 100f * config.height
        else w * bitmap.height / bitmap.width.toFloat()

        val dest = RectF(x, y, x + w, y + h)
        if (node.borderRadius > 0) {
            val path = Path().apply {
                addRoundRect(dest, node.borderRadius, node.borderRadius, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(path)
            canvas.drawBitmap(bitmap, null, dest, null)
            canvas.restore()
        } else {
            canvas.drawBitmap(bitmap, null, dest, null)
        }
    }

    private fun drawImageGrid(
        canvas: Canvas, images: List<Bitmap>,
        node: LayoutNode.ImageGridNode, x: Float, y: Float, config: CanvasConfig
    ): Float {
        val totalW = if (node.width > 0) node.width / 100f * config.width
        else config.width - x * 2
        val gap = node.spacing
        val cellW = (totalW - gap * (node.columns - 1)) / node.columns
        val rows = (images.size + node.columns - 1) / node.columns

        var drawY = y
        for (row in 0 until rows) {
            var maxRowH = 0f
            for (col in 0 until node.columns) {
                val idx = row * node.columns + col
                if (idx >= images.size) break
                val bmp = images[idx]
                val cellX = x + col * (cellW + gap)
                val cellH = cellW * bmp.height / bmp.width.toFloat()
                maxRowH = maxOf(maxRowH, cellH)

                val dest = RectF(cellX, drawY, cellX + cellW, drawY + cellH)
                if (node.borderRadius > 0) {
                    val path = Path().apply {
                        addRoundRect(dest, node.borderRadius, node.borderRadius, Path.Direction.CW)
                    }
                    canvas.save()
                    canvas.clipPath(path)
                    canvas.drawBitmap(bmp, null, dest, null)
                    canvas.restore()
                } else {
                    canvas.drawBitmap(bmp, null, dest, null)
                }
            }
            drawY += maxRowH + gap
        }
        return drawY
    }

    private fun drawWatermark(canvas: Canvas, node: LayoutNode.WatermarkNode, config: CanvasConfig) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = node.fontSize.toFloat()
            color = node.color.toInt()
            alpha = (node.opacity * 255).toInt()
        }
        val x = config.width / 2f
        val y = config.height - node.fontSize - 24f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(node.text, x, y, paint)
    }

    private fun drawDecoration(canvas: Canvas, deco: Decoration, config: CanvasConfig) {
        when (deco) {
            is Decoration.BorderDecoration -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = deco.color.toInt()
                    strokeWidth = deco.thickness
                    style = Paint.Style.STROKE
                    pathEffect = when (deco.style) {
                        "dashed" -> android.graphics.DashPathEffect(floatArrayOf(20f, 10f), 0f)
                        "dotted" -> android.graphics.DashPathEffect(floatArrayOf(4f, 8f), 0f)
                        else -> null
                    }
                }
                val rect = RectF(
                    deco.thickness, deco.thickness,
                    config.width - deco.thickness, config.height - deco.thickness
                )
                canvas.drawRoundRect(rect, deco.cornerRadius, deco.cornerRadius, paint)
            }
            is Decoration.LineDecoration -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = deco.color.toInt()
                    strokeWidth = deco.thickness
                    style = Paint.Style.STROKE
                }
                val lineY = deco.y / 100f * config.height
                canvas.drawLine(0f, lineY, config.width.toFloat(), lineY, paint)
            }
            is Decoration.CornerDecoration -> {
                drawCornerDecoration(canvas, deco, config)
            }
        }
    }

    private fun drawCornerDecoration(
        canvas: Canvas, deco: Decoration.CornerDecoration, config: CanvasConfig
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = deco.color.toInt()
            strokeWidth = deco.thickness
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val s = deco.size
        val inset = 30f

        // 四个角
        val corners = listOf(
            listOf(
                Path().apply { moveTo(inset, inset + s); lineTo(inset, inset); lineTo(inset + s, inset) },
                Path().apply { moveTo(inset + s * 0.3f, inset + s * 0.3f); lineTo(inset, inset); },
            ),
            listOf(
                Path().apply { moveTo(config.width - inset - s, inset); lineTo(config.width - inset, inset); lineTo(config.width - inset, inset + s) },
                Path().apply { moveTo(config.width - inset - s * 0.3f, inset + s * 0.3f); lineTo(config.width - inset, inset); },
            ),
            listOf(
                Path().apply { moveTo(inset, config.height - inset - s); lineTo(inset, config.height - inset); lineTo(inset + s, config.height - inset) },
                Path().apply { moveTo(inset + s * 0.3f, config.height - inset - s * 0.3f); lineTo(inset, config.height - inset); },
            ),
            listOf(
                Path().apply { moveTo(config.width - inset - s, config.height - inset); lineTo(config.width - inset, config.height - inset); lineTo(config.width - inset, config.height - inset - s) },
                Path().apply { moveTo(config.width - inset - s * 0.3f, config.height - inset - s * 0.3f); lineTo(config.width - inset, config.height - inset); },
            ),
        )

        when (deco.cornerType) {
            "flourish", "diamond" -> {
                corners.forEach { paths ->
                    paths.forEach { canvas.drawPath(it, paint) }
                }
            }
            "star" -> {
                corners.forEach { paths ->
                    paths.firstOrNull()?.let { canvas.drawPath(it, paint) }
                }
            }
            else -> {
                corners.forEach { paths ->
                    paths.firstOrNull()?.let { canvas.drawPath(it, paint) }
                }
            }
        }
    }

    private fun resolveTextPlaceholder(content: String, date: LocalDate, entry: NoteEntry): String {
        return content
            .replace("{{text}}", entry.text)
            .replace("{{date}}", date.toString())
            .replace("{{time}}", entry.time)
            .replace("{{marker}}", markerLabels[entry.effectiveMarker()] ?: "")
    }

    fun loadBitmap(context: Context, dir: String, name: String, maxSize: Int = 1400): Bitmap? {
        return try {
            val file = File(context.filesDir, "$dir/$name")
            if (!file.exists()) return null
            android.graphics.ImageDecoder.decodeBitmap(
                android.graphics.ImageDecoder.createSource(file)
            ) { decoder, info, _ ->
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                val scale = maxOf(info.size.width, info.size.height) / maxSize.toFloat()
                if (scale > 1f) decoder.setTargetSampleSize(scale.toInt())
            }
        } catch (_: Exception) {
            null
        }
    }
}
