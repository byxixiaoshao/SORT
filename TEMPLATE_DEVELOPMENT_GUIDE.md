# 寄意 分享模板开发指南

## 架构概览

```
TemplateEngine (接口)        ← 实现此接口即可创建模板
├── MinimalTemplate          ← 内置极简模板
├── CardTemplate             ← 内置卡片模板
├── MagazineTemplate         ← 内置杂志模板
├── JsonTemplate             ← JSON 模板（用户自定义）
└── YourTemplate             ← 你的新模板

TemplateRegistry             ← 模板注册表
├── register(engine)         ← 注册
├── get(id)                  ← 获取
└── getAll()                 ← 列表

CanvasRenderer               ← 底层 Canvas 渲染引擎
└── render(schema, ...)      ← TemplateSchema → Bitmap
```

## 方式一：Kotlin 代码模板

新建一个类实现 `TemplateEngine` 接口：

```kotlin
package com.bicy.note.share

class MyTemplate : TemplateEngine {
    override val id = "my_template"
    override val name = "我的模板"
    override val description = "自定义描述"

    override fun render(
        context: Context?,
        dateTime: LocalDateTime,
        entry: NoteEntry,
        images: List<Bitmap>,
        videoCovers: List<Bitmap>,
        config: TemplateConfig,
    ): Bitmap {
        // 定义模板结构
        val schema = TemplateSchema(
            canvas = CanvasConfig(
                width = config.canvasWidth,      // 默认 1080
                height = config.canvasMaxHeight,  // 默认 1920
                background = BackgroundConfig(type = "solid", color = config.bgColor),
            ),
            nodes = listOf(
                // 按顺序定义节点，y=-1 表示自动排列
                LayoutNode.DateNode(x = 8f, y = 5f, fontSize = 32, color = 0xFF888888),
                LayoutNode.TextNode(x = 8f, y = -1f, width = 84f, fontSize = 44,
                    color = config.textColor, content = "{{text}}"),
                LayoutNode.ImageGridNode(x = 8f, y = -1f, width = 84f,
                    columns = 2, spacing = 16f, borderRadius = 12f),
            ),
            decorations = listOf(
                Decoration.BorderDecoration(color = 0xFFE0E0E0, thickness = 2f, cornerRadius = 20f),
            ),
        )
        return CanvasRenderer.render(context, schema, dateTime, entry, images, videoCovers)
    }
}
```

注册到系统：

```kotlin
TemplateRegistry.register(MyTemplate())
```

## 方式二：JSON 模板

在分享弹窗点击「+ 添加」，粘贴 JSON：

```json
{
  "id": "user_custom_1",
  "name": "我的模板",
  "canvas": {
    "width": 1080,
    "height": 1920,
    "background": { "type": "solid", "color": 4294967295 }
  },
  "nodes": [
    {
      "type": "DateNode",
      "id": "date",
      "x": 8, "y": 5,
      "fontSize": 32,
      "color": 2301546496
    },
    {
      "type": "TextNode",
      "id": "text",
      "x": 8, "y": -1,
      "width": 84,
      "fontSize": 44,
      "color": 4278190080,
      "content": "{{text}}"
    },
    {
      "type": "ImageGridNode",
      "id": "images",
      "x": 8, "y": -1,
      "width": 84,
      "columns": 2,
      "spacing": 16,
      "borderRadius": 12,
      "maxImages": 9
    },
    {
      "type": "WatermarkNode",
      "id": "watermark",
      "x": 50, "y": -1,
      "fontSize": 26,
      "color": 3086721395,
      "text": "来自「寄意」笔记"
    }
  ],
  "decorations": [
    {
      "type": "BorderDecoration",
      "id": "border",
      "color": 4280825704,
      "thickness": 2,
      "cornerRadius": 20
    }
  ]
}
```

## 节点类型参考

### LayoutNode.TextNode
| 字段 | 类型 | 说明 |
|------|------|------|
| x | Float | 水平位置（百分比 0~100） |
| y | Float | 垂直位置（-1=自动排列） |
| width | Float | 宽度（百分比，-1=自适应） |
| fontSize | Int | 字号（像素） |
| color | Long | 颜色（ARGB hex） |
| fontWeight | Int | 字重（400=常规，700=粗体） |
| maxLines | Int | 最大行数 |
| content | String | 文本内容，支持 `{{text}}` 占位符 |
| textAlign | String | 对齐：left / center / right |

### LayoutNode.DateNode
| 字段 | 类型 | 说明 |
|------|------|------|
| format | String | 日期格式，如 `yyyy年M月d日 HH:mm` |
| fontSize | Int | 字号 |
| color | Long | 颜色 |

### LayoutNode.ImageGridNode
| 字段 | 类型 | 说明 |
|------|------|------|
| columns | Int | 每行列数 |
| spacing | Float | 图片间距（像素） |
| borderRadius | Float | 圆角（像素） |
| maxImages | Int | 最多显示几张 |

### LayoutNode.WatermarkNode
| 字段 | 类型 | 说明 |
|------|------|------|
| text | String | 水印文字 |
| fontSize | Int | 字号 |
| color | Long | 颜色 |
| opacity | Float | 透明度（0~1） |

### LayoutNode.LineNode
| 字段 | 类型 | 说明 |
|------|------|------|
| width | Float | 宽度（百分比） |
| thickness | Float | 线条粗细（像素） |
| color | Long | 颜色 |

### LayoutNode.SpacerNode
| 字段 | 类型 | 说明 |
|------|------|------|
| height | Float | 间距高度（像素） |

## 装饰类型参考

### Decoration.BorderDecoration
```json
{ "type": "BorderDecoration", "color": 4280825704, "thickness": 2, "cornerRadius": 20, "style": "solid" }
```
style 可选：`solid` / `dashed` / `dotted`

### Decoration.CornerDecoration
```json
{ "type": "CornerDecoration", "cornerType": "diamond", "color": 4285789991, "size": 50, "thickness": 2 }
```
cornerType 可选：`flourish` / `diamond` / `star`

### Decoration.LineDecoration
```json
{ "type": "LineDecoration", "y": 50, "color": 4280825704, "thickness": 1 }
```
y 为百分比位置。

## 颜色值参考

颜色为 ARGB 格式的 Long 值。常用：

| 颜色 | ARGB (hex) | Long 值 |
|------|-----------|---------|
| 纯黑 | #FF000000 | 4278190080 |
| 纯白 | #FFFFFFFF | 4294967295 |
| 浅灰 | #FFFAFAFA | 4294573210 |
| 深灰 | #FF888888 | 2301546496 |
| 蓝色 | #FF1565C0 | 3661296832 |
| 金色 | #FFD4AF37 | 4285789991 |

**快速生成颜色值：**
```kotlin
val color = android.graphics.Color.parseColor("#1A1A1A").toLong() and 0xFFFFFFFFL
```

## 占位符

| 占位符 | 替换内容 |
|--------|---------|
| `{{text}}` | 笔记正文 |
| `{{date}}` | 日期（yyyy-MM-dd） |
| `{{time}}` | 时间（HH:mm:ss） |
| `{{marker}}` | 标记（临时/收藏/空） |
