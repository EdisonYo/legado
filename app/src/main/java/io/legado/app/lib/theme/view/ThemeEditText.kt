package io.legado.app.lib.theme.view

import android.content.Context
import android.content.ClipboardManager
import android.os.Build
import android.os.TransactionTooLargeException
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatEditText
import io.legado.app.constant.AppLog
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.applyTint
import kotlin.math.max
import kotlin.math.min

class ThemeEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs) {

    init {
        if (!isInEditMode) {
            applyTint(context.accentColor)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            isLocalePreferredLineHeightForMinimumUsed = false
        }
    }

    override fun onTextContextMenuItem(id: Int): Boolean {
        if (id != android.R.id.paste && id != android.R.id.pasteAsPlainText) {
            return super.onTextContextMenuItem(id)
        }
        val beforeLength = text?.length ?: 0
        val beforeSelectionStart = selectionStart
        val beforeSelectionEnd = selectionEnd
        return try {
            super.onTextContextMenuItem(id)
        } catch (e: RuntimeException) {
            if (!containsTransactionTooLarge(e)) {
                throw e
            }
            withTemporarilyDisabledAutofill {
                // 部分机型可能已完成文本写入，只是在 Autofill 回调阶段抛异常。
                // 若文本或光标已变化，直接吞掉异常，避免再次手动粘贴导致内容重复。
                val afterLength = text?.length ?: beforeLength
                val afterSelectionStart = selectionStart
                val afterSelectionEnd = selectionEnd
                if (
                    afterLength != beforeLength
                    || afterSelectionStart != beforeSelectionStart
                    || afterSelectionEnd != beforeSelectionEnd
                ) {
                    return@withTemporarilyDisabledAutofill true
                }
                val pasted = pasteFromClipboardSafely()
                if (!pasted) {
                    AppLog.put("粘贴失败，请重试", e, true)
                }
                pasted
            }
        }
    }

    private inline fun <T> withTemporarilyDisabledAutofill(block: () -> T): T {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return block()
        }
        val previousMode = importantForAutofill
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        return try {
            block()
        } finally {
            post { importantForAutofill = previousMode }
        }
    }

    private fun pasteFromClipboardSafely(): Boolean {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = manager?.primaryClip ?: return false
        if (clip.itemCount <= 0) return false
        val item = clip.getItemAt(0)
        val pasteText = item.coerceToText(context) ?: return false
        val editable = text ?: return false
        val start = max(0, min(selectionStart, selectionEnd))
        val end = max(0, max(selectionStart, selectionEnd))
        return kotlin.runCatching {
            editable.replace(start, end, pasteText)
            true
        }.getOrElse {
            false
        }
    }

    private fun containsTransactionTooLarge(throwable: Throwable?): Boolean {
        var current = throwable
        while (current != null) {
            if (current is TransactionTooLargeException) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
