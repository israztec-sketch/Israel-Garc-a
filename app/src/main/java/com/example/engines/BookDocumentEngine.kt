package com.example.engines

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.domain.model.ArtifactFormat
import com.example.domain.model.ExportArtifact
import com.example.domain.model.ProductionScript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class BookDocumentEngine {

    suspend fun exportAllFormats(
        script: ProductionScript,
        outputDir: File
    ): List<ExportArtifact> = withContext(Dispatchers.IO) {
        outputDir.mkdirs()
        val artifacts = mutableListOf<ExportArtifact>()

        val pdfFile = File(outputDir, "MOHANA_Obra.pdf")
        generatePdf(script, pdfFile)
        if (pdfFile.exists() && pdfFile.length() > 0L) {
            artifacts.add(ExportArtifact(ArtifactFormat.PDF, pdfFile.absolutePath, pdfFile.length()))
        }

        val htmlFile = File(outputDir, "MOHANA_Presentacion.html")
        htmlFile.writeText(buildHtml(script), Charsets.UTF_8)
        if (htmlFile.exists() && htmlFile.length() > 0L) {
            artifacts.add(ExportArtifact(ArtifactFormat.HTML, htmlFile.absolutePath, htmlFile.length()))
        }

        val mdFile = File(outputDir, "MOHANA_Manuscrito.md")
        mdFile.writeText(buildMarkdown(script), Charsets.UTF_8)
        if (mdFile.exists() && mdFile.length() > 0L) {
            artifacts.add(ExportArtifact(ArtifactFormat.MD, mdFile.absolutePath, mdFile.length()))
        }

        val txtFile = File(outputDir, "MOHANA_Texto.txt")
        txtFile.writeText(buildPlainText(script), Charsets.UTF_8)
        if (txtFile.exists() && txtFile.length() > 0L) {
            artifacts.add(ExportArtifact(ArtifactFormat.TXT, txtFile.absolutePath, txtFile.length()))
        }

        artifacts
    }

    private fun generatePdf(script: ProductionScript, outFile: File) {
        val nativeWorked = runCatching {
            generateNativeAndroidPdf(script, outFile)
        }.isSuccess

        if (!nativeWorked || !outFile.exists() || outFile.length() == 0L) {
            generatePureKotlinPdf(script, outFile)
        }
    }

    private fun generateNativeAndroidPdf(script: ProductionScript, outFile: File) {
        val pageWidth = 595 // A4 pt
        val pageHeight = 842
        val margin = 52
        val contentWidth = pageWidth - (margin * 2)

        val pdf = PdfDocument()
        try {
            var pageNumber = 1

            val coverBgPaint = Paint().apply { color = Color.parseColor("#0B0D14") }
            val accentPaint = Paint().apply { color = Color.parseColor("#E8A838") }
            val coverTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#F3EFEA")
                textSize = 26f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }
            val coverSubPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#C7CEDB")
                textSize = 14f
            }
            val h1Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#141722")
                textSize = 18f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }
            val h2Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#8A5A00")
                textSize = 12f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#222633")
                textSize = 11.5f
            }
            val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#6B7280")
                textSize = 9.5f
            }

            // Cover Page
            val coverInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber++).create()
            val coverPage = pdf.startPage(coverInfo)
            val cc = coverPage.canvas
            cc.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), coverBgPaint)
            cc.drawRect(margin.toFloat(), 96f, margin + 88f, 102f, accentPaint)
            cc.drawText("MOHANA AI · INTELIGENCIA CREATIVA", margin.toFloat(), 132f, h2Paint.apply { color = Color.parseColor("#E8A838") })

            val titleLayout = StaticLayout.Builder.obtain(script.title, 0, script.title.length, coverTitlePaint, contentWidth).build()
            cc.save()
            cc.translate(margin.toFloat(), 180f)
            titleLayout.draw(cc)
            cc.restore()

            val loglineLayout = StaticLayout.Builder.obtain(script.logline, 0, script.logline.length, coverSubPaint, contentWidth).build()
            cc.save()
            cc.translate(margin.toFloat(), 210f + titleLayout.height)
            loglineLayout.draw(cc)
            cc.restore()
            pdf.finishPage(coverPage)

            // Reset h2Paint for light content pages
            h2Paint.color = Color.parseColor("#8A5A00")

            var currentPage = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber++).create())
            var canvas = currentPage.canvas
            var y = margin.toFloat()

            fun finishAndStartNewPage() {
                canvas.drawText("MOHANA AI · Página ${pageNumber - 1}", margin.toFloat(), (pageHeight - 28).toFloat(), footerPaint)
                pdf.finishPage(currentPage)
                currentPage = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber++).create())
                canvas = currentPage.canvas
                y = margin.toFloat()
            }

            fun drawParagraph(text: String, paint: TextPaint, extraSpace: Float) {
                if (text.isBlank()) return
                val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth)
                    .setLineSpacing(3.5f, 1f)
                    .build()
                if (y + layout.height > pageHeight - margin - 30) {
                    finishAndStartNewPage()
                }
                canvas.save()
                canvas.translate(margin.toFloat(), y)
                layout.draw(canvas)
                canvas.restore()
                y += layout.height + extraSpace
            }

            if (script.chapters.isNotEmpty()) {
                for (chapter in script.chapters) {
                    drawParagraph("CAPÍTULO ${chapter.chapterNumber}: ${chapter.title}", h1Paint, 6f)
                    if (chapter.subtitle.isNotBlank()) {
                        drawParagraph(chapter.subtitle, h2Paint, 14f)
                    }
                    for (p in chapter.bodyParagraphs) {
                        drawParagraph(p, bodyPaint, 10f)
                    }
                    if (chapter.keyTakeaways.isNotEmpty()) {
                        drawParagraph("Puntos clave / Síntesis:", h2Paint, 6f)
                        for (k in chapter.keyTakeaways) {
                            drawParagraph("• $k", bodyPaint, 5f)
                        }
                    }
                    y += 18f
                }
            } else {
                drawParagraph("REPARTO Y PERFILES DE VOZ", h1Paint, 10f)
                for (c in script.characters) {
                    drawParagraph("${c.name} (${c.role} · ${c.ageStyle}): ${c.personality}", bodyPaint, 6f)
                }
                y += 12f
                drawParagraph("GUION COMPLETO", h1Paint, 12f)
                for (line in script.lines) {
                    drawParagraph("[Escena ${line.sceneNumber}] ${line.speakerName.uppercase()} (${line.emotion}):", h2Paint, 3f)
                    if (line.actingNote.isNotBlank()) {
                        drawParagraph("Acotación: ${line.actingNote}", footerPaint, 3f)
                    }
                    drawParagraph(line.text, bodyPaint, 10f)
                }
            }

            canvas.drawText("MOHANA AI · Página ${pageNumber - 1}", margin.toFloat(), (pageHeight - 28).toFloat(), footerPaint)
            pdf.finishPage(currentPage)

            FileOutputStream(outFile).use { pdf.writeTo(it) }
        } finally {
            runCatching { pdf.close() }
        }
    }

    private fun generatePureKotlinPdf(script: ProductionScript, outFile: File) {
        val allLines = mutableListOf<String>()
        allLines.add("MOHANA AI - INTELIGENCIA CREATIVA")
        allLines.add(script.title)
        if (script.logline.isNotBlank()) allLines.addAll(wrapText(script.logline, 78))
        allLines.add("")
        if (script.chapters.isNotEmpty()) {
            script.chapters.forEach { ch ->
                allLines.add("CAPITULO ${ch.chapterNumber}: ${ch.title}")
                ch.bodyParagraphs.forEach { p ->
                    allLines.addAll(wrapText(p, 82))
                    allLines.add("")
                }
            }
        } else {
            script.lines.forEach { l ->
                allLines.add("${l.speakerName} (${l.emotion}):")
                allLines.addAll(wrapText(l.text, 82))
                allLines.add("")
            }
        }

        val linesPerPage = 44
        val pages = allLines.chunked(linesPerPage).ifEmpty { listOf(listOf(script.title)) }
        val sb = StringBuilder()
        val offsets = mutableListOf<Int>()

        fun addObj(id: Int, body: String) {
            offsets.add(sb.length)
            sb.append("$id 0 obj\n$body\nendobj\n")
        }

        sb.append("%PDF-1.4\n")
        // 1: Catalog, 2: Pages, 3: Font
        val pageIds = (0 until pages.size).map { 4 + it * 2 }
        addObj(1, "<< /Type /Catalog /Pages 2 0 R >>")
        addObj(2, "<< /Type /Pages /Kids [${pageIds.joinToString(" ") { "$it 0 R" }}] /Count ${pages.size} >>")
        addObj(3, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")

        pages.forEachIndexed { idx, pageLines ->
            val pageObjId = 4 + idx * 2
            val contentObjId = pageObjId + 1
            val stream = buildString {
                append("BT\n/F1 11 Tf\n50 780 Td\n15 TL\n")
                pageLines.forEach { line ->
                    val safe = pdfEscape(line)
                    append("($safe) Tj T*\n")
                }
                append("ET\n")
            }
            addObj(pageObjId, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R >> >> /Contents $contentObjId 0 R >>")
            addObj(contentObjId, "<< /Length ${stream.toByteArray(Charsets.ISO_8859_1).size} >>\nstream\n$stream\nendstream")
        }

        val xrefOffset = sb.length
        sb.append("xref\n0 ${offsets.size + 1}\n")
        sb.append("0000000000 65535 f \n")
        offsets.forEach { off ->
            sb.append(String.format("%010d 00000 n \n", off))
        }
        sb.append("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        outFile.writeBytes(sb.toString().toByteArray(Charsets.ISO_8859_1))
    }

    private fun wrapText(text: String, maxChars: Int): List<String> {
        val words = text.split(Regex("\\s+"))
        val out = mutableListOf<String>()
        var current = StringBuilder()
        for (w in words) {
            if (current.length + w.length + 1 > maxChars && current.isNotEmpty()) {
                out.add(current.toString())
                current = StringBuilder(w)
            } else {
                if (current.isNotEmpty()) current.append(' ')
                current.append(w)
            }
        }
        if (current.isNotEmpty()) out.add(current.toString())
        return out
    }

    private fun pdfEscape(s: String): String =
        s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")

    private fun buildMarkdown(script: ProductionScript): String = buildString {
        appendLine("# ${script.title}")
        appendLine()
        appendLine("> ${script.logline}")
        appendLine()
        if (script.chapters.isNotEmpty()) {
            for (ch in script.chapters) {
                appendLine("## Capítulo ${ch.chapterNumber}: ${ch.title}")
                if (ch.subtitle.isNotBlank()) appendLine("*${ch.subtitle}*")
                appendLine()
                ch.bodyParagraphs.forEach {
                    appendLine(it)
                    appendLine()
                }
                if (ch.keyTakeaways.isNotEmpty()) {
                    appendLine("### Claves de síntesis")
                    ch.keyTakeaways.forEach { appendLine("- $it") }
                    appendLine()
                }
            }
        } else {
            appendLine("## Personajes")
            script.characters.forEach {
                appendLine("- **${it.name}** (${it.role}, ${it.ageStyle}): ${it.personality}")
            }
            appendLine()
            appendLine("## Guion")
            script.lines.forEach {
                appendLine("### ${it.speakerName} — *${it.emotion}*")
                if (it.actingNote.isNotBlank()) appendLine("*(${it.actingNote})*")
                appendLine(it.text)
                appendLine()
            }
        }
    }

    private fun buildHtml(script: ProductionScript): String = buildString {
        appendLine("""<!DOCTYPE html><html lang="es"><head><meta charset="utf-8"/>""")
        appendLine("""<meta name="viewport" content="width=device-width,initial-scale=1"/>""")
        appendLine("""<title>${escapeHtml(script.title)}</title>""")
        appendLine("""<style>body{background:#090B10;color:#F3EFEA;font-family:system-ui,sans-serif;max-width:860px;margin:0 auto;padding:32px;line-height:1.65;} .card{background:#121520;border:1px solid #2F364F;border-radius:16px;padding:24px;margin-bottom:24px;} h1,h2{color:#E8A838;} .meta{color:#A7AFBF;font-size:0.9rem;}</style></head><body>""")
        appendLine("""<div class="card"><div class="meta">MOHANA AI · INTELIGENCIA CREATIVA</div><h1>${escapeHtml(script.title)}</h1><p>${escapeHtml(script.logline)}</p></div>""")
        if (script.chapters.isNotEmpty()) {
            script.chapters.forEach { ch ->
                appendLine("""<section class="card"><h2>Capítulo ${ch.chapterNumber}: ${escapeHtml(ch.title)}</h2>""")
                if (ch.subtitle.isNotBlank()) appendLine("""<p class="meta">${escapeHtml(ch.subtitle)}</p>""")
                ch.bodyParagraphs.forEach { p -> appendLine("<p>${escapeHtml(p)}</p>") }
                if (ch.keyTakeaways.isNotEmpty()) {
                    appendLine("<ul>")
                    ch.keyTakeaways.forEach { k -> appendLine("<li>${escapeHtml(k)}</li>") }
                    appendLine("</ul>")
                }
                appendLine("</section>")
            }
        } else {
            script.lines.forEach { l ->
                appendLine("""<div class="card"><strong>${escapeHtml(l.speakerName)}</strong> <span class="meta">(${escapeHtml(l.emotion)})</span><p>${escapeHtml(l.text)}</p></div>""")
            }
        }
        appendLine("</body></html>")
    }

    private fun buildPlainText(script: ProductionScript): String = buildString {
        appendLine("MOHANA AI — ${script.title}")
        appendLine(script.logline)
        appendLine("=".repeat(50))
        if (script.chapters.isNotEmpty()) {
            script.chapters.forEach { ch ->
                appendLine("\nCAPÍTULO ${ch.chapterNumber}: ${ch.title}")
                ch.bodyParagraphs.forEach { appendLine("\n$it") }
            }
        } else {
            script.lines.forEach { l ->
                appendLine("\n[${l.speakerName} - ${l.emotion}]: ${l.text}")
            }
        }
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
