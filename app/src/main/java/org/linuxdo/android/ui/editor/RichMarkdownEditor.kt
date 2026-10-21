package org.linuxdo.android.ui.editor

import androidx.core.graphics.ColorUtils
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.sp
import org.linuxdo.android.Config
import org.linuxdo.android.data.AttachedMedia
import android.widget.EditText
import android.text.style.ReplacementSpan
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.Editable
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Path as AndroidPath
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.Matrix
import android.view.MotionEvent
import android.view.Gravity
import android.text.InputType
import coil.request.ImageRequest
import coil.imageLoader
import org.linuxdo.android.ui.*

private const val OBJECT_REPLACEMENT_CHAR = "\uFFFC"

internal class CreatorMediaSpan(
    val media: AttachedMedia,
    private val context: Context,
    private val view: EditText,
    private val onRemove: (AttachedMedia) -> Unit,
    private val onPreview: (AttachedMedia) -> Unit,
) : ReplacementSpan() {

    private var cachedBitmap: Bitmap? = null
    private var isBitmapLoading = false
    private val density = context.resources.displayMetrics.density

    val cardHeight = if (media.isImage) 180f * density else 56f * density
    val paddingVertical = 4f * density
    val cornerRadius = 12f * density
    val closeBtnSize = 32f * density

    val lastDrawBounds = RectF()
    val closeBtnBounds = RectF()

    init {
        loadBitmapAsync()
    }

    private fun loadBitmapAsync() {
        if (!media.isImage || cachedBitmap != null || isBitmapLoading) return
        isBitmapLoading = true
        val request = ImageRequest.Builder(context)
            .data(media.previewUrl)
            .allowHardware(false)
            .target(
                onSuccess = { drawable ->
                    cachedBitmap = (drawable as? BitmapDrawable)?.bitmap ?: run {
                        val w = drawable.intrinsicWidth.coerceAtLeast(1)
                        val h = drawable.intrinsicHeight.coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val c = Canvas(bmp)
                        drawable.setBounds(0, 0, w, h)
                        drawable.draw(c)
                        bmp
                    }
                    isBitmapLoading = false
                    view.postInvalidate()
                },
                onError = {
                    isBitmapLoading = false
                }
            )
            .build()
        context.imageLoader.enqueue(request)
    }

    private fun getAvailableWidth(): Float {
        val w = view.width - view.paddingLeft - view.paddingRight
        if (w > 100) return w.toFloat()
        val screenW = context.resources.displayMetrics.widthPixels
        val pad = view.paddingLeft + view.paddingRight
        val fallback = screenW - (if (pad > 0) pad else (32 * density).toInt())
        return fallback.coerceAtLeast(100).toFloat()
    }

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val totalH = cardHeight + paddingVertical * 2
        if (fm != null) {
            val totalInt = totalH.toInt()
            fm.ascent = -totalInt
            fm.descent = 0
            fm.top = fm.ascent
            fm.bottom = 0
        }
        return getAvailableWidth().toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val availWidth = getAvailableWidth()
        val lineH = (bottom - top).toFloat()
        val drawLeft = x
        val drawTop = top + (lineH - cardHeight) / 2f
        val drawRight = x + availWidth
        val drawBottom = drawTop + cardHeight

        lastDrawBounds.set(drawLeft, drawTop, drawRight, drawBottom)
        closeBtnBounds.set(
            drawRight - closeBtnSize - 6f * density,
            drawTop + 6f * density,
            drawRight - 6f * density,
            drawTop + closeBtnSize + 6f * density
        )

        val cardPath = AndroidPath().apply {
            addRoundRect(lastDrawBounds, cornerRadius, cornerRadius, AndroidPath.Direction.CW)
        }

        canvas.save()
        canvas.clipPath(cardPath)

        // 卡片底色、边框和文字都跟随编辑器当前的前景色,深色模式下同样可读
        val fg = view.currentTextColor
        val fgSecondary = view.currentHintTextColor
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(fg, if (media.isImage) 24 else 16)
        }
        canvas.drawRect(lastDrawBounds, bgPaint)

        if (media.isImage) {
            val bmp = cachedBitmap
            if (bmp != null && !bmp.isRecycled) {
                val srcW = bmp.width.toFloat()
                val srcH = bmp.height.toFloat()
                val dstW = lastDrawBounds.width()
                val dstH = lastDrawBounds.height()
                val scale = maxOf(dstW / srcW, dstH / srcH)
                val scaledW = srcW * scale
                val scaledH = srcH * scale
                val dx = lastDrawBounds.left + (dstW - scaledW) / 2f
                val dy = lastDrawBounds.top + (dstH - scaledH) / 2f

                val matrix = Matrix().apply {
                    postScale(scale, scale)
                    postTranslate(dx, dy)
                }
                val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(bmp, matrix, bmpPaint)
            } else {
                val tipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = fgSecondary
                    textSize = 14f * density
                    textAlign = Paint.Align.CENTER
                }
                val tipText = if (isBitmapLoading) "🖼️ 正在载入图片…" else "🖼️ ${media.name}"
                canvas.drawText(tipText, lastDrawBounds.centerX(), lastDrawBounds.centerY() + 5f * density, tipPaint)
                loadBitmapAsync()
            }

            val barHeight = 28f * density
            val barRect = RectF(drawLeft, drawBottom - barHeight, drawRight, drawBottom)
            val barPaint = Paint().apply {
                color = android.graphics.Color.argb(130, 0, 0, 0)
            }
            canvas.drawRect(barRect, barPaint)

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 11.5f * density
            }
            canvas.drawText("🖼️ ${media.name}（点击放大）", drawLeft + 10f * density, drawBottom - 9f * density, textPaint)
        } else {
            val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 20f * density
            }
            canvas.drawText("📎", drawLeft + 12f * density, lastDrawBounds.centerY() + 7f * density, iconPaint)

            val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = fg
                textSize = 14f * density
                isFakeBoldText = true
            }
            canvas.drawText(media.name, drawLeft + 42f * density, lastDrawBounds.centerY() - 2f * density, namePaint)

            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = fgSecondary
                textSize = 11f * density
            }
            canvas.drawText("附件已关联", drawLeft + 42f * density, lastDrawBounds.centerY() + 16f * density, subPaint)
        }

        canvas.restore()

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = ColorUtils.setAlphaComponent(fg, 30)
            strokeWidth = 1f * density
        }
        canvas.drawRoundRect(lastDrawBounds, cornerRadius, cornerRadius, borderPaint)

        val closeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(160, 0, 0, 0)
        }
        canvas.drawCircle(closeBtnBounds.centerX(), closeBtnBounds.centerY(), closeBtnBounds.width() / 2f, closeBgPaint)

        val xPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 12f * density
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.drawText("✕", closeBtnBounds.centerX(), closeBtnBounds.centerY() + 4f * density, xPaint)
    }

    fun handleTouch(eventX: Float, eventY: Float): Boolean {
        if (closeBtnBounds.contains(eventX, eventY)) {
            onRemove(media)
            return true
        } else if (lastDrawBounds.contains(eventX, eventY)) {
            if (media.isImage) {
                onPreview(media)
            }
            return true
        }
        return false
    }
}

internal class RichMarkdownEditorView(context: Context) : EditText(context) {

    private var onMarkdownChange: ((String) -> Unit)? = null
    private var onRemoveAttachment: ((String) -> Unit)? = null
    private var onPreviewImage: ((String) -> Unit)? = null

    private var isInternalUpdating = false
    private val density = resources.displayMetrics.density

    init {
        background = null
        gravity = Gravity.TOP or Gravity.START
        textSize = 16f
        setLineSpacing(4f * density, 1.0f)
        hint = "请文明发帖，支持Markdown..."
        val padH = (16 * density).toInt()
        val padV = (8 * density).toInt()
        setPadding(padH, padV, padH, (24 * density).toInt())
        isVerticalScrollBarEnabled = true
        isSingleLine = false
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setHorizontallyScrolling(false)

        addTextChangedListener(object : TextWatcher {
            private var beforeSpans: Array<CreatorMediaSpan> = emptyArray()

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (isInternalUpdating) return
                val sp = s as? Spanned ?: return
                beforeSpans = sp.getSpans(start, start + count, CreatorMediaSpan::class.java)
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isInternalUpdating) return
                if (beforeSpans.isNotEmpty() && s != null) {
                    val currentSpans = s.getSpans(0, s.length, CreatorMediaSpan::class.java).toSet()
                    for (oldSpan in beforeSpans) {
                        if (oldSpan !in currentSpans) {
                            onRemoveAttachment?.invoke(oldSpan.media.id)
                        }
                    }
                }
                onMarkdownChange?.invoke(exportToMarkdown())
            }
        })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val ed = text
            if (ed != null) {
                val touchX = event.x - totalPaddingLeft + scrollX
                val touchY = event.y - totalPaddingTop + scrollY
                val spans = ed.getSpans(0, ed.length, CreatorMediaSpan::class.java)
                for (span in spans) {
                    if (span.handleTouch(touchX, touchY)) {
                        return true
                    }
                }
            }
        }
        return super.onTouchEvent(event)
    }

    fun setOnMarkdownChangeListener(listener: (String) -> Unit) {
        onMarkdownChange = listener
    }

    fun setOnRemoveAttachmentListener(listener: (String) -> Unit) {
        onRemoveAttachment = listener
    }

    fun setOnPreviewImageListener(listener: (String) -> Unit) {
        onPreviewImage = listener
    }

    fun updateThemeColors(textColor: Int, hintColor: Int) {
        setTextColor(textColor)
        setHintTextColor(hintColor)
    }

    fun requestInputFocus() {
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
        text?.let { ed ->
            setSelection(ed.length)
        }
        post {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(this, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun insertToken(token: String) {
        val ed = text ?: return
        val start = selectionStart.coerceAtLeast(0)
        val end = selectionEnd.coerceAtLeast(0)
        val min = minOf(start, end)
        val max = maxOf(start, end)
        ed.replace(min, max, token)
        setSelection((min + token.length).coerceIn(0, ed.length))
        requestFocus()
    }

    fun insertMedia(media: AttachedMedia) {
        val ed = text ?: return
        val cursor = selectionStart.coerceAtLeast(0)
        val sb = SpannableStringBuilder()
        val isStartOfLine = cursor == 0 || (cursor > 0 && ed[cursor - 1] == '\n')
        if (!isStartOfLine) sb.append("\n")
        val spanStart = sb.length
        sb.append(OBJECT_REPLACEMENT_CHAR)
        sb.setSpan(
            createSpan(media),
            spanStart,
            sb.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        // 避免重复叠加换行：若光标后已有换行，则无需再追加 \n
        val hasTrailingNewline = cursor < ed.length && ed[cursor] == '\n'
        if (!hasTrailingNewline) {
            sb.append("\n")
        }

        val end = selectionEnd.coerceAtLeast(cursor)
        ed.replace(cursor, end, sb)
        val newCaret = (cursor + sb.length).coerceIn(0, ed.length)
        setSelection(newCaret)
        requestFocus()
    }

    fun syncAttachments(attachments: List<AttachedMedia>) {
        val ed = text ?: return
        val currentSpans = ed.getSpans(0, ed.length, CreatorMediaSpan::class.java)
        val currentIds = currentSpans.map { it.media.id }.toSet()
        val newAttachments = attachments.filter { it.id !in currentIds }
        if (newAttachments.isNotEmpty()) {
            for (newMedia in newAttachments) {
                insertMedia(newMedia)
            }
        }
    }

    fun removeMediaSpan(mediaId: String) {
        val ed = text ?: return
        val spans = ed.getSpans(0, ed.length, CreatorMediaSpan::class.java)
        for (span in spans) {
            if (span.media.id == mediaId) {
                val start = ed.getSpanStart(span)
                val end = ed.getSpanEnd(span)
                if (start >= 0 && end >= start) {
                    isInternalUpdating = true
                    val deleteStart = if (start > 0 && ed[start - 1] == '\n') start - 1 else start
                    val deleteEnd = if (end < ed.length && ed[end] == '\n') end + 1 else end
                    ed.delete(deleteStart, deleteEnd)
                    isInternalUpdating = false
                    onMarkdownChange?.invoke(exportToMarkdown())
                }
                break
            }
        }
    }

    fun setInitialMarkdown(raw: String, attachments: List<AttachedMedia>) {
        isInternalUpdating = true
        if (attachments.isEmpty()) {
            setText(raw)
            setSelection(raw.length)
            isInternalUpdating = false
            return
        }

        data class Match(val range: IntRange, val media: AttachedMedia)
        val matches = mutableListOf<Match>()
        for (media in attachments) {
            val shortUrl = media.uploaded.shortUrl
            if (shortUrl.isBlank()) continue
            val pattern = Regex("""(!?\[[^\]]*\]\(${Regex.escape(shortUrl)}\))""")
            val m = pattern.find(raw)
            if (m != null) {
                matches.add(Match(m.range, media))
            }
        }

        val sb = SpannableStringBuilder()
        if (matches.isEmpty()) {
            sb.append(raw.trimEnd())
            for (media in attachments) {
                if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append("\n")
                val spanStart = sb.length
                sb.append(OBJECT_REPLACEMENT_CHAR)
                sb.setSpan(createSpan(media), spanStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.append("\n")
            }
        } else {
            matches.sortBy { it.range.first }
            var cursor = 0
            for (match in matches) {
                if (match.range.first > cursor) {
                    val textBefore = raw.substring(cursor, match.range.first)
                    // 卡片自身独占高度，若前序段落末尾有多余空行（如 \n\n），收拢为单换行避免留白过大
                    val trimmed = if (textBefore.endsWith("\n\n")) textBefore.trimEnd('\n') + "\n" else textBefore
                    sb.append(trimmed)
                }
                if (sb.isNotEmpty() && !sb.endsWith("\n")) {
                    sb.append("\n")
                }
                val spanStart = sb.length
                sb.append(OBJECT_REPLACEMENT_CHAR)
                sb.setSpan(createSpan(match.media), spanStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.append("\n")
                cursor = match.range.last + 1
                // 跳过紧随卡片后面的连续空行换行符（如 Markdown 常用的 \n\n）
                while (cursor < raw.length && raw[cursor] == '\n') {
                    cursor++
                }
            }
            if (cursor < raw.length) {
                sb.append(raw.substring(cursor))
            }
        }

        setText(sb)
        setSelection(sb.length)
        isInternalUpdating = false
    }

    fun exportToMarkdown(): String {
        val ed = text ?: return ""
        val spans = ed.getSpans(0, ed.length, CreatorMediaSpan::class.java)
        if (spans.isEmpty()) return ed.toString().trimEnd()

        val sorted = spans.map { span ->
            Triple(ed.getSpanStart(span), ed.getSpanEnd(span), span)
        }.filter { it.first >= 0 && it.second >= it.first }.sortedBy { it.first }

        val sb = StringBuilder()
        var cursor = 0
        for ((start, end, span) in sorted) {
            if (start > cursor) {
                val seg = ed.subSequence(cursor, start).toString()
                sb.append(seg.trimEnd('\n'))
            }
            if (sb.isNotEmpty()) {
                sb.append("\n\n")
            }
            sb.append(span.media.toMarkdown())
            sb.append("\n\n")
            cursor = end
        }
        if (cursor < ed.length) {
            val tail = ed.subSequence(cursor, ed.length).toString()
            sb.append(tail.trimStart('\n'))
        }
        return sb.toString().trimEnd()
    }

    private fun createSpan(media: AttachedMedia): CreatorMediaSpan {
        return CreatorMediaSpan(
            media = media,
            context = context,
            view = this,
            onRemove = { m ->
                removeMediaSpan(m.id)
                onRemoveAttachment?.invoke(m.id)
            },
            onPreview = { m ->
                onPreviewImage?.invoke(m.previewUrl)
            }
        )
    }
}
