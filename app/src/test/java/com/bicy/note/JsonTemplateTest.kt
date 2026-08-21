package com.bicy.note.share

import org.junit.Test
import org.junit.Assert.*

class JsonTemplateTest {

    @Test
    fun `parse example JSON template`() {
        val json = """
{
  "id": "user_custom",
  "name": "我的模板",
  "canvas": {
    "width": 1080,
    "height": 1920,
    "background": { "type": "solid", "color": 4294967295 }
  },
  "nodes": [
    { "type": "DateNode", "id": "date", "x": 8, "y": 5, "fontSize": 32, "color": 2301546496 },
    { "type": "TextNode", "id": "text", "x": 8, "y": 12, "width": 84, "fontSize": 48, "color": 4278190080, "content": "{{text}}" },
    { "type": "ImageGridNode", "id": "img", "x": 8, "y": 60, "width": 84, "columns": 2, "spacing": 16, "borderRadius": 12, "maxImages": 6 },
    { "type": "WatermarkNode", "id": "wm", "x": 50, "y": 95, "fontSize": 24, "color": 3086721395, "text": "来自「寄意」笔记" }
  ],
  "decorations": [
    { "type": "BorderDecoration", "id": "b", "color": 4280825704, "thickness": 2, "cornerRadius": 20 }
  ]
}
        """.trimIndent()

        val result = JsonTemplate.fromJson(json)
        assertNotNull("Template should parse successfully", result)
        assertEquals("user_custom", result!!.id)
        assertEquals("我的模板", result.name)
        assertEquals(4, result.data.nodes.size)
        assertEquals(1, result.data.decorations.size)
        assertTrue(result.data.nodes[0] is LayoutNode.DateNode)
        assertTrue(result.data.nodes[1] is LayoutNode.TextNode)
        assertTrue(result.data.nodes[2] is LayoutNode.ImageGridNode)
        assertTrue(result.data.nodes[3] is LayoutNode.WatermarkNode)
        assertTrue(result.data.decorations[0] is Decoration.BorderDecoration)
    }

    @Test
    fun `parse minimal JSON template`() {
        val json = """
{
  "id": "test",
  "name": "Test",
  "nodes": [
    { "type": "TextNode", "id": "t", "x": 0, "y": 0, "content": "Hello" }
  ]
}
        """.trimIndent()

        val result = JsonTemplate.fromJson(json)
        assertNotNull("Minimal template should parse", result)
        assertEquals("test", result!!.id)
        assertEquals(1, result.data.nodes.size)
        assertTrue(result.data.nodes[0] is LayoutNode.TextNode)
    }

    @Test
    fun `parse all node types`() {
        val json = """
{
  "id": "all_nodes",
  "name": "All",
  "nodes": [
    { "type": "TextNode", "id": "t", "x": 0, "y": 0 },
    { "type": "ImageNode", "id": "i", "x": 0, "y": 0 },
    { "type": "DateNode", "id": "d", "x": 0, "y": 0 },
    { "type": "MarkerNode", "id": "m", "x": 0, "y": 0 },
    { "type": "SpacerNode", "id": "s", "x": 0, "y": 0 },
    { "type": "LineNode", "id": "l", "x": 0, "y": 0 },
    { "type": "ImageGridNode", "id": "g", "x": 0, "y": 0 },
    { "type": "WatermarkNode", "id": "w", "x": 0, "y": 0 }
  ]
}
        """.trimIndent()

        val result = JsonTemplate.fromJson(json)
        assertNotNull(result)
        assertEquals(8, result!!.data.nodes.size)
    }

    @Test
    fun `round-trip toJson then fromJson`() {
        val json = """
{
  "id": "roundtrip",
  "name": "Round",
  "nodes": [
    { "type": "TextNode", "id": "t", "x": 10, "y": 20, "fontSize": 32, "color": 4278190080, "content": "Hi" }
  ]
}
        """.trimIndent()

        val t1 = JsonTemplate.fromJson(json)!!
        val exported = t1.toJson()
        val t2 = JsonTemplate.fromJson(exported)
        assertNotNull(t2)
        assertEquals(t1.id, t2!!.id)
        assertEquals(t1.data.nodes.size, t2.data.nodes.size)
    }

    @Test
    fun `invalid JSON returns null`() {
        val result = JsonTemplate.fromJson("{ invalid json }")
        assertNull("Invalid JSON should return null", result)
    }

    @Test
    fun `unknown node type is skipped`() {
        val json = """
{
  "id": "test",
  "nodes": [
    { "type": "TextNode", "id": "t", "x": 0, "y": 0 },
    { "type": "UnknownNode", "id": "u", "x": 0, "y": 0 }
  ]
}
        """.trimIndent()
        val result = JsonTemplate.fromJson(json)
        assertNotNull(result)
        assertEquals(1, result!!.data.nodes.size)
    }
}
