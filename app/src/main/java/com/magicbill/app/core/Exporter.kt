package com.magicbill.app.core

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File

/**
 * On-device report/receipt exports — PDFs via android.graphics.pdf (no
 * library), CSVs as plain files, all shared through the system share sheet
 * via FileProvider. No server calls, no storage cost.
 */
object Exporter {

    private const val AUTHORITY = "com.magicbill.app.fileprovider"

    private fun shareFile(context: Context, file: File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(context, AUTHORITY, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    private fun exportDir(context: Context): File =
        File(context.cacheDir, "exports").apply { mkdirs() }

    // ---------------- shared drawing helpers ----------------

    private class Pen {
        val title = Paint().apply {
            color = Color.BLACK; textSize = 18f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val h2 = Paint().apply {
            color = Color.rgb(71, 85, 105); textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val body = Paint().apply { color = Color.rgb(15, 23, 42); textSize = 11f }
        val muted = Paint().apply { color = Color.rgb(100, 116, 139); textSize = 10f }
        val bold = Paint().apply {
            color = Color.rgb(15, 23, 42); textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val line = Paint().apply { color = Color.rgb(226, 232, 240); strokeWidth = 1f }
        val bodyRight = Paint(body).apply { textAlign = Paint.Align.RIGHT }
        val boldRight = Paint(bold).apply { textAlign = Paint.Align.RIGHT }
        val mutedRight = Paint(muted).apply { textAlign = Paint.Align.RIGHT }
    }

    // A4 in PostScript points.
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f

    private class PdfWriter(val doc: PdfDocument) {
        val pen = Pen()
        var page: PdfDocument.Page = newPage()
        var canvas: Canvas = page.canvas
        var y = MARGIN
        var pageNo = 1

        fun newPage(): PdfDocument.Page {
            val info = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create()
            return doc.startPage(info)
        }

        fun ensureSpace(needed: Float) {
            if (y + needed > PAGE_H - MARGIN) {
                doc.finishPage(page)
                pageNo++
                page = newPage()
                canvas = page.canvas
                y = MARGIN
            }
        }

        fun finish() = doc.finishPage(page)
    }

    // ---------------- sales report PDF ----------------

    fun shareReportPdf(
        context: Context,
        restaurantName: String,
        fromDay: String,
        toDay: String,
        total: Double,
        subtotal: Double,
        gst: Double,
        billCount: Int,
        avg: Double,
        cash: Double,
        card: Double,
        upi: Double,
        credit: Double,
        items: List<Triple<String, Double, Double>>, // name, qty, amount
        expenseTotal: Double,
    ) {
        val doc = PdfDocument()
        val w = PdfWriter(doc)
        val p = w.pen
        val right = PAGE_W - MARGIN

        val range = if (fromDay == toDay) longDate(fromDay) else "${longDate(fromDay)} — ${longDate(toDay)}"

        w.canvas.drawText(restaurantName, MARGIN, w.y + 14f, p.title)
        w.y += 32f
        w.canvas.drawText("Sales report · $range", MARGIN, w.y, p.muted)
        w.y += 26f

        // Stat rows.
        fun stat(label: String, value: String) {
            w.ensureSpace(18f)
            w.canvas.drawText(label, MARGIN, w.y, p.body)
            w.canvas.drawText(value, right, w.y, p.boldRight)
            w.y += 17f
        }
        stat("Total sales", formatINR(total))
        stat("Bills", billCount.toString())
        stat("Average bill", formatINR(avg, decimals = 0))
        stat("Subtotal", formatINR(subtotal))
        stat("GST", formatINR(gst))
        stat("Expenses", formatINR(expenseTotal))

        w.y += 16f
        w.ensureSpace(80f)
        w.canvas.drawText("PAYMENT MODES", MARGIN, w.y, p.h2)
        w.y += 16f
        listOf(
            "Cash" to cash, "UPI" to upi, "Card" to card, "Credit" to credit,
        ).forEach { (label, v) ->
            w.ensureSpace(17f)
            val pct = if (total > 0) Math.round(v / total * 100).toInt() else 0
            w.canvas.drawText(label, MARGIN, w.y, p.body)
            w.canvas.drawText("$pct%", right - 90f, w.y, p.mutedRight)
            w.canvas.drawText(formatINR(v), right, w.y, p.bodyRight)
            w.y += 16f
        }

        w.y += 16f
        w.ensureSpace(40f)
        w.canvas.drawText("ITEM-WISE SALES", MARGIN, w.y, p.h2)
        w.y += 16f
        if (items.isEmpty()) {
            w.canvas.drawText("No items in this range", MARGIN, w.y, p.muted)
            w.y += 16f
        } else {
            items.forEachIndexed { i, (name, qty, amount) ->
                w.ensureSpace(17f)
                w.canvas.drawText("${i + 1}.", MARGIN, w.y, p.muted)
                w.canvas.drawText(name.take(52), MARGIN + 20f, w.y, p.body)
                w.canvas.drawText("×${qty.toLong()}", right - 90f, w.y, p.mutedRight)
                w.canvas.drawText(formatINR(amount), right, w.y, p.bodyRight)
                w.y += 16f
            }
        }

        w.y += 24f
        w.ensureSpace(16f)
        w.canvas.drawText("Generated by Magic Bill · magicbill.in", MARGIN, w.y, p.muted)

        w.finish()
        val file = File(exportDir(context), "magicbill-report-$fromDay-$toDay.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        shareFile(context, file, "application/pdf", "Share sales report")
    }

    // ---------------- receipt PDF ----------------

    fun shareReceiptPdf(
        context: Context,
        restaurantName: String,
        billNumber: String?,
        tokenNumber: Long?,
        billedAt: String,
        orderType: String?,
        tableNumber: String?,
        customerName: String?,
        items: List<Triple<String, Double, Double>>, // name, qty, price
        subtotal: Double,
        gst: Double,
        total: Double,
        paymentMode: String?,
    ) {
        // 80mm thermal-ish page: 226pt wide, height sized to content.
        val width = 226
        val lineH = 14f
        var estimated = 150f + items.size * lineH + 120f
        val doc = PdfDocument()
        val info = PdfDocument.PageInfo.Builder(width, estimated.toInt().coerceAtLeast(300), 1).create()
        val page = doc.startPage(info)
        val c = page.canvas

        val mono = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        val monoBold = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        val center = Paint().apply { color = Color.BLACK; textSize = 9f; typeface = mono; textAlign = Paint.Align.CENTER }
        val centerBold = Paint(center).apply { textSize = 13f; typeface = monoBold }
        val left = Paint().apply { color = Color.BLACK; textSize = 9f; typeface = mono }
        val rightP = Paint(left).apply { textAlign = Paint.Align.RIGHT }
        val leftBold = Paint(left).apply { typeface = monoBold; textSize = 11f }
        val rightBold = Paint(rightP).apply { typeface = monoBold; textSize = 11f }
        val dash = Paint().apply {
            color = Color.rgb(120, 120, 120); strokeWidth = 1f
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f, 3f), 0f)
        }

        val mid = width / 2f
        val rightX = width - 12f
        var y = 24f

        c.drawText(restaurantName.take(30), mid, y, centerBold); y += 16f
        val idLine = buildString {
            if (!billNumber.isNullOrBlank()) append("Bill #$billNumber")
            if (tokenNumber != null) append(if (isEmpty()) "Token $tokenNumber" else " · Token $tokenNumber")
        }
        if (idLine.isNotEmpty()) { c.drawText(idLine, mid, y, center); y += 12f }
        c.drawText(billTime(billedAt), mid, y, center); y += 12f
        val typeLine = buildString {
            if (!orderType.isNullOrBlank()) append(orderType)
            if (!tableNumber.isNullOrBlank()) append(if (isEmpty()) "Table $tableNumber" else " · Table $tableNumber")
        }
        if (typeLine.isNotEmpty()) { c.drawText(typeLine, mid, y, center); y += 12f }
        if (!customerName.isNullOrBlank()) { c.drawText("Customer: $customerName", mid, y, center); y += 12f }

        y += 4f; c.drawLine(12f, y, width - 12f, y, dash); y += 14f

        for ((name, qty, price) in items) {
            c.drawText(name.take(24), 12f, y, left)
            y += 11f
            c.drawText("  ${qty.toLong()} x ${formatINR(price)}", 12f, y, left)
            c.drawText(formatINR(price * qty), rightX, y, rightP)
            y += 13f
        }

        y += 2f; c.drawLine(12f, y, width - 12f, y, dash); y += 14f
        c.drawText("Subtotal", 12f, y, left); c.drawText(formatINR(subtotal), rightX, y, rightP); y += 13f
        c.drawText("GST", 12f, y, left); c.drawText(formatINR(gst), rightX, y, rightP); y += 15f
        c.drawText("TOTAL", 12f, y, leftBold); c.drawText(formatINR(total), rightX, y, rightBold); y += 15f
        if (!paymentMode.isNullOrBlank()) {
            c.drawText("Paid by", 12f, y, left); c.drawText(paymentMode, rightX, y, rightP); y += 13f
        }

        y += 2f; c.drawLine(12f, y, width - 12f, y, dash); y += 14f
        c.drawText("Thank you! Visit again.", mid, y, center); y += 12f
        c.drawText("Powered by Magic Bill", mid, y, center)

        doc.finishPage(page)
        val file = File(exportDir(context), "magicbill-receipt-${billNumber ?: "bill"}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        shareFile(context, file, "application/pdf", "Share receipt")
    }

    // ---------------- CSV ----------------

    fun shareReportCsv(
        context: Context,
        restaurantName: String,
        fromDay: String,
        toDay: String,
        total: Double,
        subtotal: Double,
        gst: Double,
        billCount: Int,
        avg: Double,
        cash: Double,
        card: Double,
        upi: Double,
        credit: Double,
        expenseTotal: Double,
        items: List<Triple<String, Double, Double>>,
        bills: List<Array<String>>, // pre-formatted rows
    ) {
        fun q(s: String) = "\"${s.replace("\"", "\"\"")}\""
        val lines = buildList {
            add("${q(restaurantName)},Sales report,$fromDay to $toDay")
            add("")
            add("Summary")
            add("Total,${"%.2f".format(total)}")
            add("Subtotal,${"%.2f".format(subtotal)}")
            add("GST,${"%.2f".format(gst)}")
            add("Bills,$billCount")
            add("Average bill,${"%.2f".format(avg)}")
            add("Expenses,${"%.2f".format(expenseTotal)}")
            add("")
            add("Payment mode,Amount")
            add("Cash,${"%.2f".format(cash)}")
            add("UPI,${"%.2f".format(upi)}")
            add("Card,${"%.2f".format(card)}")
            add("Credit,${"%.2f".format(credit)}")
            add("")
            add("Item,Quantity,Amount")
            items.forEach { (name, qty, amount) -> add("${q(name)},${qty.toLong()},${"%.2f".format(amount)}") }
            add("")
            add("Bill No,Time,Type,Table,Payment,Subtotal,GST,Total")
            bills.forEach { row -> add(row.joinToString(",")) }
        }
        val file = File(exportDir(context), "magicbill-report-$fromDay-$toDay.csv")
        file.writeText(lines.joinToString("\n"))
        shareFile(context, file, "text/csv", "Share sales report (CSV)")
    }
}
