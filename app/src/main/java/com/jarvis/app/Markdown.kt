package com.jarvis.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

sealed class MdBlock {
    data class Para(val text: String) : MdBlock()
    data class Header(val level: Int, val text: String) : MdBlock()
    data class Bullets(val items: List<String>) : MdBlock()
    data class Code(val lang: String, val code: String) : MdBlock()
}

fun parseMarkdown(src: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val codeRegex = Regex("```([\\w+#.-]*)\\s*\\n?([\\s\\S]*?)```")
    var last = 0
    codeRegex.findAll(src).forEach { m ->
        if (m.range.first > last) parseText(src.substring(last, m.range.first), blocks)
        blocks.add(MdBlock.Code(m.groupValues[1], m.groupValues[2].trimEnd()))
        last = m.range.last + 1
    }
    if (last < src.length) parseText(src.substring(last), blocks)
    return blocks
}

private fun parseText(text: String, out: MutableList<MdBlock>) {
    val lines = text.split("\n")
    val bullets = mutableListOf<String>()
    val para = mutableListOf<String>()
    fun flushPara() { if (para.isNotEmpty()) { out.add(MdBlock.Para(para.joinToString(" "))); para.clear() } }
    fun flushBullets() { if (bullets.isNotEmpty()) { out.add(MdBlock.Bullets(bullets.toList())); bullets.clear() } }
    for (raw in lines) {
        val t = raw.trim()
        if (t.isEmpty()) { flushPara(); flushBullets(); continue }
        val h = Regex("^(#{1,6})\\s+(.*)$").find(t)
        if (h != null) {
            flushPara(); flushBullets()
            out.add(MdBlock.Header(h.groupValues[1].length, h.groupValues[2]))
            continue
        }
        val b = Regex("^[-*+]\\s+(.*)$").find(t)
        if (b != null) { flushPara(); bullets.add(b.groupValues[1]); continue }
        flushBullets()
        para.add(t)
    }
    flushPara(); flushBullets()
}

fun inlineFormat(text: String, codeBg: Color, codeColor: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > 0) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                } else { append(text[i]); i++ }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > 0) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, color = codeColor)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            text[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end > 0) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            else -> { append(text[i]); i++ }
        }
    }
}
