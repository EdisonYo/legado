package io.legado.app.ui.book.source.edit

import android.annotation.SuppressLint
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.databinding.ItemSourceEditBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.widget.code.addJsPattern
import io.legado.app.ui.widget.code.addJsonPattern
import io.legado.app.ui.widget.code.addLegadoPattern
import io.legado.app.ui.widget.text.EditEntity

class BookSourceEditAdapter(
    private val onLargeTextEdit: ((EditEntity) -> Unit)? = null
) : RecyclerView.Adapter<BookSourceEditAdapter.MyViewHolder>() {

    val editEntityMaxLine = AppConfig.sourceEditMaxLine
    private val largeTextThreshold = 12000
    private val largePreviewLines = 6

    var editEntities: ArrayList<EditEntity> = ArrayList()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val binding = ItemSourceEditBinding
            .inflate(LayoutInflater.from(parent.context), parent, false)
        binding.editText.addLegadoPattern()
        binding.editText.addJsonPattern()
        binding.editText.addJsPattern()
        return MyViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.bind(editEntities[position])
    }

    override fun getItemCount(): Int {
        return editEntities.size
    }

    inner class MyViewHolder(val binding: ItemSourceEditBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(editEntity: EditEntity) = binding.run {
            val rawText = editEntity.value.orEmpty()
            val isLargeText = rawText.length > largeTextThreshold
            editText.setTag(R.id.tagLargeText, isLargeText)
            editText.setTag(R.id.tag, editEntity.key)
            editText.maxLines = if (isLargeText) largePreviewLines else editEntityMaxLine
            if (editText.getTag(R.id.tag1) == null) {
                val listener = object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        val large = editText.getTag(R.id.tagLargeText) == true
                        if (large) {
                            editText.isCursorVisible = false
                            editText.isFocusable = false
                            editText.isFocusableInTouchMode = false
                        } else {
                            editText.isCursorVisible = false
                            editText.isCursorVisible = true
                            editText.isFocusable = true
                            editText.isFocusableInTouchMode = true
                        }
                    }

                    override fun onViewDetachedFromWindow(v: View) {

                    }
                }
                editText.addOnAttachStateChangeListener(listener)
                editText.setTag(R.id.tag1, listener)
            }
            editText.getTag(R.id.tag2)?.let {
                if (it is TextWatcher) {
                    editText.removeTextChangedListener(it)
                }
            }
            editText.setText(
                if (isLargeText) {
                    itemView.context.getString(R.string.large_text_placeholder, rawText.length)
                } else {
                    rawText
                }
            )
            textInputLayout.hint = editEntity.hint
            if (isLargeText) {
                editText.isCursorVisible = false
                editText.isFocusable = false
                editText.isFocusableInTouchMode = false
                editText.setOnClickListener {
                    onLargeTextEdit?.invoke(editEntity)
                }
                editText.setOnLongClickListener {
                    onLargeTextEdit?.invoke(editEntity)
                    true
                }
            } else {
                editText.isCursorVisible = true
                editText.isFocusable = true
                editText.isFocusableInTouchMode = true
                editText.setOnClickListener(null)
                editText.setOnLongClickListener(null)
                val textWatcher = object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence,
                        start: Int,
                        count: Int,
                        after: Int
                    ) {

                    }

                    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {

                    }

                    override fun afterTextChanged(s: Editable?) {
                        editEntity.value = (s?.toString())
                    }
                }
                editText.addTextChangedListener(textWatcher)
                editText.setTag(R.id.tag2, textWatcher)
            }
        }
    }


}
