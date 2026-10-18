package com.greggaynor.thoughtcapture.ui

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.greggaynor.thoughtcapture.R
import com.greggaynor.thoughtcapture.model.NoteLine

class NoteLineAdapter(
    private val onCheckedChanged: (NoteLine, Boolean) -> Unit,
    private val onEdit: (NoteLine) -> Unit,
    private val onDelete: (NoteLine) -> Unit
) : RecyclerView.Adapter<NoteLineAdapter.NoteLineViewHolder>() {
    private var items: List<NoteLine> = emptyList()

    fun submitList(lines: List<NoteLine>) {
        items = lines
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteLineViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_note_line, parent, false)
        return NoteLineViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: NoteLineViewHolder, position: Int) {
        holder.bind(items[position], onCheckedChanged, onEdit, onDelete)
    }

    class NoteLineViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val checkbox = view.findViewById<MaterialCheckBox>(R.id.note_line_checkbox)
        private val text = view.findViewById<TextView>(R.id.note_line_text)
        private val edit = view.findViewById<ImageButton>(R.id.note_line_edit)
        private val delete = view.findViewById<ImageButton>(R.id.note_line_delete)

        fun bind(
            line: NoteLine,
            onCheckedChanged: (NoteLine, Boolean) -> Unit,
            onEdit: (NoteLine) -> Unit,
            onDelete: (NoteLine) -> Unit
        ) {
            val context = itemView.context
            checkbox.setOnCheckedChangeListener(null)
            checkbox.isChecked = line.isChecked
            text.text = line.text
            text.paintFlags = if (line.isChecked) {
                text.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                text.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            text.alpha = if (line.isChecked) 0.55f else 1f
            text.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                onCheckedChanged(line, isChecked)
            }
            itemView.setOnClickListener {
                checkbox.isChecked = !checkbox.isChecked
            }
            edit.setOnClickListener { onEdit(line) }
            delete.setOnClickListener { onDelete(line) }
        }
    }
}
