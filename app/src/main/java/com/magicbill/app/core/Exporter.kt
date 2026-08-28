package com.magicbill.app.core

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A report leaves the phone as a file the share sheet hands on: CSV for a spreadsheet, PDF for a person. */
object Exporter {
    private const val AUTHORITY = "com.magicbill.app.fileprovider"

    suspend fun csv(context: Context, name: String, header: List<String>, rows: List<List<String>>): Intent = withContext(Dispatchers.IO) {
        val f = file(context, "$name.csv")
        f.bufferedWriter().use { w ->
            w.appendLine(header.joinToString(",") { cell(it) })
            rows.forEach { r -> w.appendLine(r.joinToString(",") { cell(it) }) }
        }
        share(context, f, "text/csv")
    }

    /** Lines of text on A4 pages, monospace, page-broken. Enough for a report somebody prints. */
    suspend fun pdf(context: Context, name: String, title: String, lines: List<String>): Intent = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        val pageW = 595; val pageH = 842; val margin = 40
        val body = Paint().apply { typeface = Typeface.MONOSPACE; textSize = 10f }
        val head = Paint().apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textSize = 16f }
        val lineH = 14
        val perPage = (pageH - margin * 2 - 40) / lineH
        val chunks = if (lines.isEmpty()) listOf(emptyList()) else lines.chunked(perPage)
        chunks.forEachIndexed { pageIndex, chunk ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageIndex + 1).create())
            val c = page.canvas
            c.drawText(title, margin.toFloat(), (margin + 16).toFloat(), head)
            var y = margin + 44
            chunk.forEach { l -> c.drawText(l, margin.toFloat(), y.toFloat(), body); y += lineH }
            c.drawText("Magic Bill · page ${pageIndex + 1} of ${chunks.size}", margin.toFloat(), (pageH - 20).toFloat(), body)
            doc.finishPage(page)
        }
        val f = file(context, "$name.pdf")
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        share(context, f, "application/pdf")
    }

    fun text(subject: String, body: String): Intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, subject); putExtra(Intent.EXTRA_TEXT, body) },
        subject,
    )

    private fun file(context: Context, name: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 3_600_000L }?.forEach { it.delete() }
        return File(dir, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
    }

    private fun share(context: Context, f: File, mime: String): Intent {
        val uri = FileProvider.getUriForFile(context, AUTHORITY, f)
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) },
            f.name,
        )
    }

    private fun cell(s: String): String = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    /** Two columns on a 42-column receipt line: the text, and a figure right-aligned. */
    fun line(left: String, right: String, width: Int = 42): String {
        val l = left.take(width - right.length - 1)
        return l + " ".repeat((width - l.length - right.length).coerceAtLeast(1)) + right
    }
}
