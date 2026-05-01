package com.example.myapplication.data.local

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.myapplication.data.models.Anime
import java.io.File

/**
 * Светлая PDF-таблица коллекции (независимо от темы приложения).
 * Перенос названий: [StaticLayout.Builder]; числовые колонки — [Canvas.drawText] с baseline по центру строки.
 */
class CollectionPdfGenerator(
    private val context: Context
) {

    fun writeToFile(
        file: File,
        items: List<Anime>,
        documentTitle: String,
        columnTitle: String,
        columnEpisodes: String,
        columnRating: String,
        emptyMessage: String
    ) {
        check(context.packageName.isNotEmpty())
        val pageWidth = PAGE_W
        val pageHeight = PAGE_H
        val marginLeft = MARGIN_H
        val marginRight = MARGIN_H
        val marginTop = MARGIN_V
        val marginBottom = MARGIN_V
        val contentLeft = marginLeft
        val contentRight = pageWidth - marginRight
        val contentWidth = contentRight - contentLeft

        val colTitleW = contentWidth * COL_TITLE_FRAC
        val colEpW = contentWidth * COL_EP_FRAC
        val colRatingW = contentWidth * COL_RATING_FRAC

        val titleDocPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_COLOR
            textSize = DOC_TITLE_TEXT_SIZE
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_COLOR
            textSize = TABLE_TEXT_SIZE
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_COLOR
            textSize = TABLE_TEXT_SIZE
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val zebraPaint = Paint().apply {
            color = ZEBRA_COLOR
            style = Paint.Style.FILL
        }

        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth.toInt(), pageHeight.toInt(), 1).create()

        fun headerRowHeight(): Float {
            val fm = headerPaint.fontMetrics
            return fm.descent - fm.ascent + ROW_PADDING * 2f
        }

        fun oneLineHeight(paint: TextPaint): Float {
            val fm = paint.fontMetrics
            return fm.descent - fm.ascent
        }

        fun drawTableHeader(canvas: Canvas, yTop: Float): Float {
            val h = headerRowHeight()
            val fm = headerPaint.fontMetrics
            val baseline = yTop + ROW_PADDING - fm.ascent
            canvas.drawText(columnTitle, contentLeft, baseline, headerPaint)
            canvas.drawText(columnEpisodes, contentLeft + colTitleW, baseline, headerPaint)
            val align = headerPaint.textAlign
            headerPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(columnRating, contentLeft + colTitleW + colEpW + colRatingW, baseline, headerPaint)
            headerPaint.textAlign = align
            return h
        }

        var page = doc.startPage(pageInfo)
        var canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        var y = marginTop
        // Document title (first page only)
        val docTitleBaseline = y - titleDocPaint.fontMetrics.ascent
        canvas.drawText(documentTitle, contentLeft, docTitleBaseline, titleDocPaint)
        y += oneLineHeight(titleDocPaint) + DOC_TITLE_GAP

        var headerH = drawTableHeader(canvas, y)
        y += headerH + HEADER_GAP_AFTER

        var pageIndex = 0
        var dataRowIndex = 0

        fun finishAndNewPage() {
            doc.finishPage(page)
            pageIndex++
            val nextInfo = PdfDocument.PageInfo.Builder(pageWidth.toInt(), pageHeight.toInt(), pageIndex + 1).create()
            page = doc.startPage(nextInfo)
            canvas = page.canvas
            canvas.drawColor(Color.WHITE)
            y = marginTop
            headerH = drawTableHeader(canvas, y)
            y += headerH + HEADER_GAP_AFTER
        }

        val rowsToDraw: List<Anime?> = if (items.isEmpty()) listOf(null) else items.map { it }

        for (animeOrNull in rowsToDraw) {
            val titleText = animeOrNull?.title ?: emptyMessage
            val epText = animeOrNull?.let { it.episodes.toString() } ?: ""
            val ratingText = animeOrNull?.let { it.rating.toString() } ?: ""

            val titleLayout = StaticLayout.Builder.obtain(titleText, 0, titleText.length, bodyPaint, colTitleW.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build()

            val fmBody = bodyPaint.fontMetrics
            val singleLineH = fmBody.descent - fmBody.ascent
            val titleBlockH = titleLayout.height.toFloat()
            val innerHeight = maxOf(titleBlockH, singleLineH)
            val rowHeight = innerHeight + ROW_PADDING * 2f

            if (y + rowHeight > pageHeight - marginBottom) {
                finishAndNewPage()
            }

            if (animeOrNull != null && dataRowIndex % 2 == 0) {
                canvas.drawRect(
                    contentLeft,
                    y,
                    contentRight,
                    y + rowHeight,
                    zebraPaint
                )
            }

            val contentTop = y + ROW_PADDING
            canvas.save()
            canvas.translate(contentLeft, contentTop)
            titleLayout.draw(canvas)
            canvas.restore()

            val baselineNumbers = y + rowHeight / 2f - (fmBody.ascent + fmBody.descent) / 2f
            canvas.drawText(epText, contentLeft + colTitleW, baselineNumbers, bodyPaint)
            val alignBody = bodyPaint.textAlign
            bodyPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(ratingText, contentLeft + colTitleW + colEpW + colRatingW, baselineNumbers, bodyPaint)
            bodyPaint.textAlign = alignBody

            y += rowHeight
            if (animeOrNull != null) dataRowIndex++
        }

        doc.finishPage(page)
        file.parentFile?.mkdirs()
        file.outputStream().use { out -> doc.writeTo(out) }
        doc.close()
    }

    private companion object {
        private const val PAGE_W = 595
        private const val PAGE_H = 842
        private const val MARGIN_H = 44f
        private const val MARGIN_V = 52f
        private const val COL_TITLE_FRAC = 0.6f
        private const val COL_EP_FRAC = 0.22f
        private const val COL_RATING_FRAC = 0.18f
        private const val DOC_TITLE_TEXT_SIZE = 17f
        private const val TABLE_TEXT_SIZE = 11f
        private const val ROW_PADDING = 8f
        private const val DOC_TITLE_GAP = 14f
        private const val HEADER_GAP_AFTER = 10f
        private const val TEXT_COLOR = 0xFF222222.toInt()
        private val ZEBRA_COLOR = Color.argb(15, 0, 0, 0)
    }
}
