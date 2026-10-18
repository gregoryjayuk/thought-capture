package com.greggaynor.thoughtcapture.ui

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.HapticFeedbackConstants
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.greggaynor.thoughtcapture.model.ItemState
import com.greggaynor.thoughtcapture.R
import com.greggaynor.thoughtcapture.model.ManagedItem
import com.greggaynor.thoughtcapture.model.ManagedItemType
import com.greggaynor.thoughtcapture.model.TaskItem
import com.greggaynor.thoughtcapture.model.TaskTags
import kotlin.math.abs

class ManagedItemAdapter(
    private val onHeaderTapped: (header: ListEntry.Header) -> Unit,
    private val onItemTapped: (item: ManagedItem) -> Unit,
    private val onItemLongPressed: (item: ManagedItem) -> Unit,
    private val onTaskTapped: (task: TaskItem) -> Unit,
    private val onTaskChecked: (task: TaskItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val differ = AsyncListDiffer(this, DIFF_CALLBACK)
    private var displayMode: DisplayMode = DisplayMode.BROWSING
    private var selectedTaskIds: Set<String> = emptySet()
    private var hideSomedayContext: Boolean = false
    private var hideAreaTags: Boolean = false
    private var accentColorRes: Int = R.color.seed
    private var taskInteractionEnabled: Boolean = true

    init {
        setHasStableIds(true)
    }

    fun submitList(
        newEntries: List<ListEntry>,
        displayMode: DisplayMode,
        selectedTaskIds: Set<String> = emptySet(),
        hideSomedayContext: Boolean = false,
        hideAreaTags: Boolean = false,
        accentColorRes: Int = R.color.seed,
        taskInteractionEnabled: Boolean = true
    ) {
        val previousDisplayMode = this.displayMode
        val previousSelectedTaskIds = this.selectedTaskIds.toSet()
        val previousHideSomedayContext = this.hideSomedayContext
        val previousHideAreaTags = this.hideAreaTags
        val previousAccentColorRes = this.accentColorRes
        val previousTaskInteractionEnabled = this.taskInteractionEnabled
        this.displayMode = displayMode
        this.selectedTaskIds = selectedTaskIds.toSet()
        this.hideSomedayContext = hideSomedayContext
        this.hideAreaTags = hideAreaTags
        this.accentColorRes = accentColorRes
        this.taskInteractionEnabled = taskInteractionEnabled
        if (differ.currentList == newEntries) {
            if (
                previousDisplayMode != displayMode ||
                previousHideSomedayContext != hideSomedayContext ||
                previousHideAreaTags != hideAreaTags ||
                previousAccentColorRes != accentColorRes ||
                previousTaskInteractionEnabled != taskInteractionEnabled
            ) {
                notifyItemRangeChanged(0, itemCount)
            } else {
                notifySelectionChanged(previousSelectedTaskIds, this.selectedTaskIds)
            }
            return
        }
        differ.submitList(newEntries) {
            if (
                previousDisplayMode != displayMode ||
                previousHideSomedayContext != hideSomedayContext ||
                previousHideAreaTags != hideAreaTags ||
                previousAccentColorRes != accentColorRes ||
                previousTaskInteractionEnabled != taskInteractionEnabled
            ) {
                notifyItemRangeChanged(0, itemCount)
            } else {
                notifySelectionChanged(previousSelectedTaskIds, this.selectedTaskIds)
            }
        }
    }

    fun spanSizeAt(@Suppress("UNUSED_PARAMETER") position: Int): Int = 1

    override fun getItemViewType(position: Int): Int {
        return when (differ.currentList[position]) {
            is ListEntry.Header -> VIEW_TYPE_HEADER
            is ListEntry.AreaFocusTile -> VIEW_TYPE_AREA_FOCUS
            is ListEntry.MilestoneRow -> VIEW_TYPE_MILESTONE
            is ListEntry.ItemRow -> VIEW_TYPE_ITEM
            is ListEntry.TaskRow -> VIEW_TYPE_TASK
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(inflater.inflate(R.layout.item_section_header, parent, false))
            VIEW_TYPE_AREA_FOCUS -> AreaFocusViewHolder(inflater.inflate(R.layout.item_area_focus_tile, parent, false))
            VIEW_TYPE_TASK -> TaskViewHolder(inflater.inflate(R.layout.item_task, parent, false))
            VIEW_TYPE_MILESTONE -> MilestoneViewHolder(inflater.inflate(R.layout.item_milestone, parent, false))
            else -> ItemViewHolder(inflater.inflate(R.layout.item_managed_item, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val entry = differ.currentList[position]) {
            is ListEntry.Header -> (holder as HeaderViewHolder).bind(entry, onHeaderTapped)
            is ListEntry.AreaFocusTile -> (holder as AreaFocusViewHolder).bind(
                entry = entry,
                onItemTapped = onItemTapped,
                onItemLongPressed = onItemLongPressed
            )
            is ListEntry.ItemRow -> (holder as ItemViewHolder).bind(
                entry.item,
                displayMode = displayMode,
                accentColorRes = accentColorRes,
                onItemTapped = onItemTapped,
                onItemLongPressed = onItemLongPressed
            )
            is ListEntry.MilestoneRow -> (holder as MilestoneViewHolder).bind(
                entry.item,
                ordinal = entry.ordinal,
                accentColorRes = accentColorRes,
                onItemTapped = onItemTapped,
                onItemLongPressed = onItemLongPressed
            )
            is ListEntry.TaskRow -> (holder as TaskViewHolder).bind(
                task = entry.task,
                isSelected = entry.task.id in selectedTaskIds,
                hideSomedayContext = hideSomedayContext,
                hideAreaTags = hideAreaTags,
                accentColorRes = accentColorRes,
                interactionEnabled = taskInteractionEnabled,
                onTaskTapped = onTaskTapped,
                onTaskChecked = onTaskChecked
            )
        }
    }

    override fun getItemCount(): Int = differ.currentList.size

    override fun getItemId(position: Int): Long {
        return when (val entry = differ.currentList[position]) {
            is ListEntry.Header -> "header:${entry.key}".hashCode().toLong()
            is ListEntry.AreaFocusTile -> "focus:${entry.item.id}".hashCode().toLong()
            is ListEntry.ItemRow -> "item:${entry.item.id}".hashCode().toLong()
            is ListEntry.MilestoneRow -> "milestone:${entry.item.id}".hashCode().toLong()
            is ListEntry.TaskRow -> "task:${entry.task.id}".hashCode().toLong()
        }
    }

    private class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val root = view.findViewById<View>(R.id.section_header_root)
        private val title = view.findViewById<TextView>(R.id.section_header_title)
        private val indicator = view.findViewById<TextView>(R.id.section_header_indicator)

        fun bind(entry: ListEntry.Header, onHeaderTapped: (ListEntry.Header) -> Unit) {
            title.text = entry.title
            if (entry.badgeText.isNullOrBlank()) {
                indicator.visibility = View.GONE
            } else {
                indicator.visibility = View.VISIBLE
                indicator.text = entry.badgeText
                indicator.setBackgroundResource(
                    if (entry.isExpanded && entry.isCollapsible) {
                        R.drawable.bg_section_badge_active
                    } else {
                        R.drawable.bg_section_badge
                    }
                )
            }
            if (entry.isCollapsible) {
                root.setOnClickListener {
                    root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    root.animate().alpha(0.72f).setDuration(60L).withEndAction {
                        root.animate().alpha(1f).setDuration(120L).start()
                    }.start()
                    onHeaderTapped(entry)
                }
            } else {
                root.setOnClickListener(null)
            }
        }
    }

    private class TaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val root = view.findViewById<View>(R.id.task_row_root)
        private val checkbox = view.findViewById<MaterialCheckBox>(R.id.task_checkbox)
        private val title = view.findViewById<TextView>(R.id.task_title)

        fun bind(
            task: TaskItem,
            isSelected: Boolean,
            hideSomedayContext: Boolean,
            hideAreaTags: Boolean,
            accentColorRes: Int,
            interactionEnabled: Boolean,
            onTaskTapped: (task: TaskItem) -> Unit,
            onTaskChecked: (task: TaskItem) -> Unit
        ) {
            val context = itemView.context
            title.text = buildInlineTaskText(
                task = task,
                hideSomedayContext = hideSomedayContext,
                hideAreaTags = hideAreaTags,
                titleColor = ContextCompat.getColor(
                    context,
                    if (isSelected) accentColorRes else R.color.text_primary
                ),
                chipBackgroundColor = ContextCompat.getColor(context, R.color.task_meta_bg),
                chipTextColor = ContextCompat.getColor(context, R.color.task_meta_text)
            )
            checkbox.setOnCheckedChangeListener(null)
            checkbox.isChecked = false
            checkbox.visibility = if (interactionEnabled) View.VISIBLE else View.INVISIBLE
            checkbox.isEnabled = interactionEnabled
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (interactionEnabled && isChecked) {
                    root.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    root.animate()
                        .alpha(0f)
                        .translationX(18f * context.resources.displayMetrics.density)
                        .setDuration(140L)
                        .withEndAction {
                            onTaskChecked(task)
                            root.alpha = 1f
                            root.translationX = 0f
                        }
                        .start()
                }
            }

            root.alpha = 1f
            root.setBackgroundResource(
                if (isSelected) R.drawable.bg_task_row_selected else R.drawable.bg_task_row_idle
            )
            root.isClickable = interactionEnabled
            root.setOnClickListener {
                if (!interactionEnabled) return@setOnClickListener
                root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                root.animate().scaleX(0.992f).scaleY(0.992f).setDuration(70L).withEndAction {
                    root.animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
                }.start()
                onTaskTapped(task)
            }
        }

        private fun buildInlineTaskText(
            task: TaskItem,
            hideSomedayContext: Boolean,
            hideAreaTags: Boolean,
            titleColor: Int,
            chipBackgroundColor: Int,
            chipTextColor: Int
        ): CharSequence {
            val density = itemView.resources.displayMetrics.density
            val builder = SpannableStringBuilder(task.title)
            builder.setSpan(
                android.text.style.ForegroundColorSpan(titleColor),
                0,
                builder.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            val inlineTags = buildList {
                addAll(
                    task.contexts
                        .filterNot { hideSomedayContext && it.equals("Someday", ignoreCase = true) }
                        .map { TaskTags.toDisplayLabel("@", it) }
                )
                addAll(task.projects.map { TaskTags.toDisplayLabel("+", it) })
                if (!hideAreaTags) {
                    addAll(task.areas.map { TaskTags.toDisplayLabel("#", it) })
                }
                task.priority?.let { add("P$it") }
                task.dueDate?.let { add("Due $it") }
            }

            inlineTags.forEach { tag ->
                builder.append(' ')
                val start = builder.length
                builder.append(tag)
                builder.setSpan(
                    InlineChipSpan(
                        backgroundColor = chipBackgroundColor,
                        textColor = chipTextColor,
                        horizontalPaddingPx = 6f * density,
                        verticalPaddingPx = 1f * density,
                        cornerRadiusPx = 10f * density,
                        trailingGapPx = 4f * density
                    ),
                    start,
                    builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            return builder
        }
    }

    private class AreaFocusViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val root = view.findViewById<View>(R.id.area_focus_tile_root)
        private val image = view.findViewById<ImageView>(R.id.area_focus_tile_image)
        private val title = view.findViewById<TextView>(R.id.area_focus_tile_title)
        private val summary = view.findViewById<TextView>(R.id.area_focus_tile_summary)

        fun bind(
            entry: ListEntry.AreaFocusTile,
            onItemTapped: (item: ManagedItem) -> Unit,
            onItemLongPressed: (item: ManagedItem) -> Unit
        ) {
            val item = entry.item
            val context = itemView.context
            title.text = item.title
            summary.text = entry.summary
            image.background = fallbackDrawable(item.title, context.resources.displayMetrics.density)
            FaceDragonsImageLoader.loadInto(
                imageView = image,
                imagePath = item.imagePath,
                targetSizePx = (220 * context.resources.displayMetrics.density).toInt()
            ) { bitmap ->
                if (bitmap != null) {
                    image.setImageBitmap(bitmap)
                } else {
                    image.setImageDrawable(null)
                    image.background = fallbackDrawable(item.title, context.resources.displayMetrics.density)
                }
            }
            root.setOnClickListener {
                root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onItemTapped(item)
            }
            root.setOnLongClickListener {
                root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onItemLongPressed(item)
                true
            }
        }

        private fun fallbackDrawable(title: String, density: Float): GradientDrawable {
            val palettes = arrayOf(
                intArrayOf(Color.rgb(10, 22, 30), Color.rgb(38, 31, 62), Color.rgb(104, 74, 142)),
                intArrayOf(Color.rgb(20, 11, 24), Color.rgb(50, 23, 42), Color.rgb(190, 83, 138)),
                intArrayOf(Color.rgb(8, 20, 14), Color.rgb(24, 56, 38), Color.rgb(111, 231, 175)),
                intArrayOf(Color.rgb(28, 19, 6), Color.rgb(69, 45, 18), Color.rgb(244, 197, 111)),
                intArrayOf(Color.rgb(7, 12, 26), Color.rgb(26, 48, 78), Color.rgb(110, 199, 255))
            )
            return GradientDrawable(GradientDrawable.Orientation.TL_BR, palettes[abs(title.hashCode()) % palettes.size]).apply {
                cornerRadius = 24f * density
            }
        }
    }

    private class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card = view.findViewById<MaterialCardView>(R.id.item_card)
        private val image = view.findViewById<ImageView>(R.id.item_image)
        private val title = view.findViewById<TextView>(R.id.item_title)
        private val subtitle = view.findViewById<TextView>(R.id.item_subtitle)
        private val notes = view.findViewById<TextView>(R.id.item_notes)

        fun bind(
            item: ManagedItem,
            displayMode: DisplayMode,
            accentColorRes: Int,
            onItemTapped: (item: ManagedItem) -> Unit,
            onItemLongPressed: (item: ManagedItem) -> Unit
        ) {
            val context = itemView.context
            val subtitleParts = buildList {
                if (displayMode == DisplayMode.ACTIVE && !item.category.isNullOrBlank()) {
                    add(item.category)
                }
                item.parent?.takeIf { it.isNotBlank() }?.let { add(it) }
                item.dateText?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
            val noteText = item.notes
                ?.trim()
                ?.takeIf { it.isNotBlank() && item.type != ManagedItemType.PROJECT }
                .orEmpty()
            val isNote = item.type == ManagedItemType.NOTE
            title.text = item.title
            title.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (isNote) {
                        R.color.text_primary
                    } else {
                        when (item.state) {
                            ItemState.ACTIVE -> accentColorRes
                            ItemState.MAINTENANCE -> R.color.card_maintenance_text
                            ItemState.INACTIVE -> R.color.card_inactive_text
                        }
                    }
                )
            )

            subtitle.text = subtitleParts.joinToString("  •  ")
            subtitle.visibility = if (subtitleParts.isEmpty()) View.GONE else View.VISIBLE
            subtitle.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))

            notes.text = noteText
            notes.visibility = if (noteText.isBlank()) View.GONE else View.VISIBLE
            notes.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            if (isNote) {
                image.setImageResource(android.R.drawable.ic_menu_edit)
                image.imageTintList = ContextCompat.getColorStateList(context, R.color.text_secondary)
                val padding = (12 * context.resources.displayMetrics.density).toInt()
                image.setPadding(padding, padding, padding, padding)
            } else {
                val targetSizePx = (54 * context.resources.displayMetrics.density).toInt()
                FaceDragonsImageLoader.loadInto(
                    imageView = image,
                    imagePath = item.imagePath,
                    targetSizePx = targetSizePx
                ) { bitmap ->
                    if (bitmap != null) {
                        image.setImageBitmap(bitmap)
                        image.imageTintList = null
                        image.setPadding(0, 0, 0, 0)
                    } else {
                        val padding = (11 * context.resources.displayMetrics.density).toInt()
                        image.setImageResource(android.R.drawable.ic_menu_gallery)
                        image.imageTintList = ContextCompat.getColorStateList(context, R.color.text_secondary)
                        image.setPadding(padding, padding, padding, padding)
                    }
                }
            }

            card.setCardBackgroundColor(
                ContextCompat.getColor(
                    context,
                    android.R.color.transparent
                )
            )
            card.strokeWidth = 0
            card.alpha = 1f

            if (displayMode == DisplayMode.ACTIVE) {
                card.isClickable = false
                card.isLongClickable = false
                card.setOnClickListener(null)
                card.setOnLongClickListener(null)
            } else {
                card.isClickable = true
                card.isLongClickable = true
                card.setOnClickListener {
                    card.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    card.animate().scaleX(0.988f).scaleY(0.988f).setDuration(70L).withEndAction {
                        card.animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
                    }.start()
                    onItemTapped(item)
                }
                card.setOnLongClickListener {
                    card.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onItemLongPressed(item)
                    true
                }
            }
        }
    }

    private class MilestoneViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val root = view.findViewById<View>(R.id.milestone_row_root)
        private val ordinalView = view.findViewById<TextView>(R.id.milestone_ordinal)
        private val title = view.findViewById<TextView>(R.id.milestone_title)
        private val target = view.findViewById<TextView>(R.id.milestone_target)
        private val state = view.findViewById<TextView>(R.id.milestone_state)
        private val area = view.findViewById<TextView>(R.id.milestone_area)
        private val notes = view.findViewById<TextView>(R.id.milestone_notes)

        fun bind(
            item: ManagedItem,
            ordinal: Int,
            accentColorRes: Int,
            onItemTapped: (item: ManagedItem) -> Unit,
            onItemLongPressed: (item: ManagedItem) -> Unit
        ) {
            val context = itemView.context
            val stateColorRes = when (item.state) {
                ItemState.ACTIVE -> accentColorRes
                ItemState.MAINTENANCE -> R.color.state_maintenance
                ItemState.INACTIVE -> R.color.text_tertiary
            }
            title.text = item.title
            title.setTextColor(ContextCompat.getColor(context, stateColorRes))
            ordinalView.text = ordinal.toString().padStart(2, '0')
            ordinalView.setTextColor(ContextCompat.getColor(context, stateColorRes))
            target.text = item.dateText?.takeIf { it.isNotBlank() } ?: "No target"
            state.text = when (item.state) {
                ItemState.ACTIVE -> "Active"
                ItemState.MAINTENANCE -> "Maintenance"
                ItemState.INACTIVE -> "Inactive"
            }
            state.setTextColor(ContextCompat.getColor(context, stateColorRes))
            area.text = item.category?.takeIf { it.isNotBlank() } ?: "Unmapped"

            val noteText = item.notes?.trim().orEmpty()
            notes.text = noteText
            notes.visibility = if (noteText.isBlank()) View.GONE else View.VISIBLE

            root.setOnClickListener {
                root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                root.animate().translationX(6f * context.resources.displayMetrics.density).setDuration(70L).withEndAction {
                    root.animate().translationX(0f).setDuration(120L).start()
                }.start()
                onItemTapped(item)
            }
            root.setOnLongClickListener {
                root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onItemLongPressed(item)
                true
            }
        }
    }

    enum class DisplayMode {
        ACTIVE,
        BROWSING
    }

    private companion object {
        const val VIEW_TYPE_HEADER = 1
        const val VIEW_TYPE_ITEM = 2
        const val VIEW_TYPE_TASK = 3
        const val VIEW_TYPE_MILESTONE = 4
        const val VIEW_TYPE_AREA_FOCUS = 5

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ListEntry>() {
            override fun areItemsTheSame(oldItem: ListEntry, newItem: ListEntry): Boolean {
                return when {
                    oldItem is ListEntry.Header && newItem is ListEntry.Header -> oldItem.key == newItem.key
                    oldItem is ListEntry.AreaFocusTile && newItem is ListEntry.AreaFocusTile -> oldItem.item.id == newItem.item.id
                    oldItem is ListEntry.ItemRow && newItem is ListEntry.ItemRow -> oldItem.item.id == newItem.item.id
                    oldItem is ListEntry.MilestoneRow && newItem is ListEntry.MilestoneRow -> oldItem.item.id == newItem.item.id
                    oldItem is ListEntry.TaskRow && newItem is ListEntry.TaskRow -> oldItem.task.id == newItem.task.id
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: ListEntry, newItem: ListEntry): Boolean {
                return oldItem == newItem
            }
        }
    }

    private fun notifySelectionChanged(previousSelectedTaskIds: Set<String>, newSelectedTaskIds: Set<String>) {
        val changedTaskIds = (previousSelectedTaskIds - newSelectedTaskIds) + (newSelectedTaskIds - previousSelectedTaskIds)
        changedTaskIds.forEach { taskId ->
            findTaskPosition(taskId)?.let { notifyItemChanged(it) }
        }
    }

    private fun findTaskPosition(taskId: String): Int? {
        val index = differ.currentList.indexOfFirst { entry ->
            entry is ListEntry.TaskRow && entry.task.id == taskId
        }
        return index.takeIf { it >= 0 }
    }
}
