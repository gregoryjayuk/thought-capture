package com.greggaynor.thoughtcapture

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.tabs.TabLayout
import com.greggaynor.thoughtcapture.data.KnowledgeRepository
import com.greggaynor.thoughtcapture.data.NoteRepository
import com.greggaynor.thoughtcapture.data.ProjectRepository
import com.greggaynor.thoughtcapture.data.TaskRepository
import com.greggaynor.thoughtcapture.model.AppMode
import com.greggaynor.thoughtcapture.model.ItemState
import com.greggaynor.thoughtcapture.model.MainTab
import com.greggaynor.thoughtcapture.model.ManagedItem
import com.greggaynor.thoughtcapture.model.ManagedItemType
import com.greggaynor.thoughtcapture.model.NoteDetail
import com.greggaynor.thoughtcapture.model.NoteLine
import com.greggaynor.thoughtcapture.model.TaskItem
import com.greggaynor.thoughtcapture.model.TaskTags
import com.greggaynor.thoughtcapture.ui.ListEntry
import com.greggaynor.thoughtcapture.ui.ManagedItemAdapter
import com.greggaynor.thoughtcapture.ui.FaceDragonsImageLoader
import com.greggaynor.thoughtcapture.ui.NoteLineAdapter
import java.time.LocalDate
import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var modeDrawerContainer: LinearLayout
    private lateinit var emptyStateText: TextView
    private lateinit var addFab: FloatingActionButton
    private lateinit var taskActionBar: View
    private lateinit var taskFilterScroll: View
    private lateinit var taskFilterGroup: ChipGroup
    private lateinit var tabLayout: TabLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var areaFocusScreen: View
    private lateinit var areaFocusScroll: View
    private lateinit var areaFocusImage: ShapeableImageView
    private lateinit var areaFocusTitle: TextView
    private lateinit var areaFocusSummary: LinearLayout
    private lateinit var areaFocusInterestsSection: View
    private lateinit var areaFocusInterestsContainer: LinearLayout
    private lateinit var areaFocusProjectsSection: View
    private lateinit var areaFocusProjectsContainer: LinearLayout
    private lateinit var areaFocusTasksSection: View
    private lateinit var areaFocusTasksContainer: LinearLayout
    private lateinit var areaFocusSomedaySection: View
    private lateinit var areaFocusSomedayContainer: LinearLayout
    private lateinit var areaFocusHorizonsSection: View
    private lateinit var areaFocusHorizonsContainer: LinearLayout
    private lateinit var activeHeroPanel: View
    private lateinit var activeHeroTitle: TextView
    private lateinit var activeHeroSubtitle: TextView
    private lateinit var activeHeroMeta: TextView
    private lateinit var adapter: ManagedItemAdapter
    private lateinit var repository: KnowledgeRepository
    private lateinit var noteRepository: NoteRepository
    private lateinit var projectRepository: ProjectRepository
    private lateinit var taskRepository: TaskRepository
    private var selectedTab: MainTab = MainTab.ACTIVE
    private var selectedMode: AppMode = AppMode.ALL
    private var allItems: List<ManagedItem> = emptyList()
    private var allAreaOfFocusItems: List<ManagedItem> = emptyList()
    private var allAreasOfFocus: List<String> = emptyList()
    private var allTasks: List<TaskItem> = emptyList()
    private var allCompletedTasks: List<TaskItem> = emptyList()
    private var selectedTaskIds = linkedSetOf<String>()
    private var selectedTaskContextFilter: String? = null
    private val expandedSomedaySections = mutableSetOf<String>()
    private var pendingImageSelection: ((String?) -> Unit)? = null
    private var hasLoadedOnce = false
    private var refreshVersion = 0
    private var refreshOnResume = false
    private var currentAreaFocusName: String? = null
    private var currentAreaFocusItem: ManagedItem? = null
    private val taskIoExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val showLoadingRunnable = Runnable {
        if (!hasLoadedOnce) {
            emptyStateText.text = getString(R.string.loading_text)
            emptyStateText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        }
    }
    private val pickFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, flags)
            TranscriptStore.saveFolderUri(this, uri)
            refreshUi()
        }
    }
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val onPicked = pendingImageSelection
        pendingImageSelection = null

        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            runCatching { contentResolver.takePersistableUriPermission(uri, flags) }
            onPicked?.invoke(uri.toString())
        } else {
            onPicked?.invoke(null)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = KnowledgeRepository(
            context = this,
            contentResolver = contentResolver
        )
        noteRepository = NoteRepository(
            context = this,
            contentResolver = contentResolver
        )
        taskRepository = TaskRepository(
            context = this,
            contentResolver = contentResolver
        )
        projectRepository = ProjectRepository(
            context = this,
            contentResolver = contentResolver,
            taskRepository = taskRepository
        )

        drawerLayout = findViewById(R.id.drawer_layout)
        modeDrawerContainer = findViewById(R.id.mode_drawer_container)
        emptyStateText = findViewById(R.id.empty_state_text)
        addFab = findViewById(R.id.add_fab)
        taskActionBar = findViewById(R.id.task_action_bar)
        taskFilterScroll = findViewById(R.id.task_filter_scroll)
        taskFilterGroup = findViewById(R.id.task_filter_group)
        tabLayout = findViewById(R.id.tab_layout)
        recyclerView = findViewById(R.id.items_recycler_view)
        areaFocusScreen = findViewById(R.id.area_focus_screen)
        areaFocusScroll = findViewById(R.id.area_focus_scroll)
        areaFocusImage = findViewById(R.id.area_focus_image)
        areaFocusTitle = findViewById(R.id.area_focus_title)
        areaFocusSummary = findViewById(R.id.area_focus_summary)
        areaFocusInterestsSection = findViewById(R.id.area_focus_interests_section)
        areaFocusInterestsContainer = findViewById(R.id.area_focus_interests_container)
        areaFocusProjectsSection = findViewById(R.id.area_focus_projects_section)
        areaFocusProjectsContainer = findViewById(R.id.area_focus_projects_container)
        areaFocusTasksSection = findViewById(R.id.area_focus_tasks_section)
        areaFocusTasksContainer = findViewById(R.id.area_focus_tasks_container)
        areaFocusSomedaySection = findViewById(R.id.area_focus_someday_section)
        areaFocusSomedayContainer = findViewById(R.id.area_focus_someday_container)
        areaFocusHorizonsSection = findViewById(R.id.area_focus_horizons_section)
        areaFocusHorizonsContainer = findViewById(R.id.area_focus_horizons_container)
        activeHeroPanel = findViewById(R.id.active_hero_panel)
        activeHeroTitle = findViewById(R.id.active_hero_title)
        activeHeroSubtitle = findViewById(R.id.active_hero_subtitle)
        activeHeroMeta = findViewById(R.id.active_hero_meta)

        setupRecyclerView()
        setupModeDrawer()
        setupTabs()
        setupFab()
        setupTaskActionBar()
        setupAreaFocusScreen()

        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        if (refreshOnResume) {
            refreshOnResume = false
            refreshUi()
        }
    }

    override fun onDestroy() {
        taskIoExecutor.shutdown()
        super.onDestroy()
    }

    private fun setupRecyclerView() {
        adapter = ManagedItemAdapter(
            onHeaderTapped = { header -> onHeaderTapped(header) },
            onItemTapped = { item -> onItemTapped(item) },
            onItemLongPressed = { item -> showItemActionsSheet(item) },
            onTaskTapped = { task -> onTaskTapped(task) },
            onTaskChecked = { task -> onTaskChecked(task) }
        )

        val layoutManager = GridLayoutManager(this, 1)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (position < adapter.itemCount) adapter.spanSizeAt(position) else 1
            }
        }

        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        recyclerView.itemAnimator = DefaultItemAnimator().apply {
            addDuration = 150
            removeDuration = 140
            moveDuration = 160
            changeDuration = 100
            supportsChangeAnimations = false
        }
    }

    private fun setupModeDrawer() {
        renderModeDrawer()
        applyModeAccent()
    }

    private fun renderModeDrawer() {
        modeDrawerContainer.removeAllViews()
        AppMode.entries.forEach { mode ->
            val isSelected = mode == selectedMode
            val accentColor = ContextCompat.getColor(this, mode.accentColorRes)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(
                    context,
                    if (isSelected) R.drawable.bg_picker_option else R.drawable.bg_task_row_idle
                )
                setPadding(dp(16), dp(14), dp(16), dp(14))
                if (modeDrawerContainer.childCount > 0) {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(10) }
                }
                setOnClickListener {
                    if (selectedMode != mode) {
                        selectedMode = mode
                        selectedTaskIds.clear()
                        selectedTab = preferredTabForMode(mode)
                        selectedTaskContextFilter = defaultTaskFilterForCurrentTab()
                        ensureValidSelectedTab()
                        renderModeDrawer()
                        applyModeAccent()
                        setupTabs()
                        renderSelectedTab()
                    }
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
            }
            row.addView(TextView(this).apply {
                text = mode.title
                setTextColor(
                    if (isSelected) accentColor else ContextCompat.getColor(context, R.color.text_primary)
                )
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            row.addView(TextView(this).apply {
                text = modeDescription(mode)
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                textSize = 13f
                setPadding(0, dp(4), 0, 0)
            })
            modeDrawerContainer.addView(row)
        }
    }

    private fun applyModeAccent() {
        val accent = ContextCompat.getColor(this, selectedMode.accentColorRes)
        tabLayout.setSelectedTabIndicatorColor(accent)
        tabLayout.setTabTextColors(
            ContextCompat.getColor(this, R.color.tab_unselected),
            accent
        )
        addFab.backgroundTintList = ContextCompat.getColorStateList(this, selectedMode.accentColorRes)
        listOf(
            R.id.task_edit_button,
            R.id.task_context_button,
            R.id.task_project_button,
            R.id.task_area_button,
            R.id.task_priority_button,
            R.id.task_due_button
        ).forEach { id ->
            findViewById<ImageButton>(id).setColorFilter(accent)
        }
        activeHeroTitle.setTextColor(accent)
    }

    private fun modeDescription(mode: AppMode): String = when (mode) {
        AppMode.ALL -> getString(R.string.mode_all_description)
        AppMode.FOCUS -> getString(R.string.mode_focus_description)
        AppMode.WORK -> getString(R.string.mode_work_description)
        AppMode.FUN -> getString(R.string.mode_fun_description)
        AppMode.LOW_ENERGY -> getString(R.string.mode_low_energy_description)
        AppMode.REVIEW -> getString(R.string.mode_review_description)
    }

    private fun setupTabs() {
        val tabs = availableTabs()
        ensureValidSelectedTab()
        tabLayout.clearOnTabSelectedListeners()
        tabLayout.removeAllTabs()
        tabs.forEach { tab ->
            tabLayout.addTab(tabLayout.newTab().setText(tab.title))
        }
        tabLayout.addTab(
            tabLayout.newTab()
                .setIcon(android.R.drawable.ic_menu_preferences)
                .setContentDescription(getString(R.string.settings_title))
        )

        tabLayout.selectTab(tabLayout.getTabAt(tabs.indexOf(selectedTab).coerceAtLeast(0)))
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (tab.position == tabs.size) {
                    showSettingsDialog()
                    tabLayout.post {
                        tabLayout.getTabAt(tabs.indexOf(selectedTab).coerceAtLeast(0))?.select()
                    }
                    return
                }
                selectedTab = tabs[tab.position]
                selectedTaskIds.clear()
                selectedTaskContextFilter = defaultTaskFilterForCurrentTab()
                renderSelectedTab()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun setupFab() {
        addFab.setOnClickListener {
            if (selectedTab == MainTab.TASKS || selectedTab == MainTab.SOMEDAY) {
                showTaskEditDialog(
                    existing = null,
                    defaultSomeday = selectedTab == MainTab.SOMEDAY
                )
            } else if (selectedTab == MainTab.NOTES) {
                showCreateNoteDialog()
            } else {
                showEditDialog(null)
            }
        }
    }

    private fun setupTaskActionBar() {
        findViewById<View>(R.id.task_edit_button).setOnClickListener {
            selectedTask()?.let { showTaskEditDialog(it) }
        }
        findViewById<View>(R.id.task_context_button).setOnClickListener {
            showTaskTagDialog(selectedTasks(), "@")
        }
        findViewById<View>(R.id.task_project_button).setOnClickListener {
            showTaskTagDialog(selectedTasks(), "+")
        }
        findViewById<View>(R.id.task_area_button).setOnClickListener {
            showTaskTagDialog(selectedTasks(), "#")
        }
        findViewById<View>(R.id.task_priority_button).setOnClickListener {
            showPriorityDialog(selectedTasks())
        }
        findViewById<View>(R.id.task_due_button).setOnClickListener {
            showDueDateDialog(selectedTasks())
        }
    }

    private fun setupAreaFocusScreen() {
        findViewById<ImageButton>(R.id.area_focus_back_button).setOnClickListener {
            hideAreaFocusScreen()
        }
        findViewById<ImageButton>(R.id.area_focus_edit_image_button).setOnClickListener {
            val focus = currentAreaFocusItem ?: return@setOnClickListener
            pendingImageSelection = { newImagePath ->
                if (newImagePath != null) {
                    val updated = focus.copy(imagePath = newImagePath)
                    currentAreaFocusItem = updated
                    allAreaOfFocusItems = allAreaOfFocusItems.map { if (it.id == updated.id) updated else it }
                    renderAreaFocusHeroImage(areaFocusImage, updated.title, updated.imagePath)
                    renderSelectedTab()
                    taskIoExecutor.execute {
                        repository.updateItem(updated)
                    }
                }
            }
            pickImageLauncher.launch(arrayOf("image/*"))
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (areaFocusScreen.visibility == View.VISIBLE) {
                    hideAreaFocusScreen()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    private fun refreshUi() {
        val refreshId = ++refreshVersion
        val isFirstLoad = !hasLoadedOnce
        if (isFirstLoad) {
            mainHandler.removeCallbacks(showLoadingRunnable)
            mainHandler.postDelayed(showLoadingRunnable, 160L)
        }
        taskIoExecutor.execute {
            val loadedTasks = taskRepository.loadTasks()
            val loadedCompletedTasks =
                if (selectedMode == AppMode.REVIEW || selectedTab == MainTab.COMPLETED) {
                    taskRepository.loadCompletedTasks()
                } else {
                    allCompletedTasks
                }
            val loadedAreasOfFocus = canonicalizeAreaOfFocusTitles(repository.loadAreasOfFocus())
            val loadedAreaOfFocusItems = repository.loadAreaOfFocusItems()
            val knowledgeItems = repository.loadItems()
            val noteItems = noteRepository.loadNoteItems()
            val loadedItems = (projectRepository.loadProjects(loadedTasks, includeMetadata = !isFirstLoad) + knowledgeItems + noteItems)
                .sortedWith(compareBy({ it.type.displayName }, { it.category ?: "" }, { it.title }))
            runOnUiThread {
                if (isFinishing || isDestroyed || refreshId != refreshVersion) return@runOnUiThread
                mainHandler.removeCallbacks(showLoadingRunnable)
                allTasks = loadedTasks
                allCompletedTasks = loadedCompletedTasks
                allAreasOfFocus = loadedAreasOfFocus
                allAreaOfFocusItems = loadedAreaOfFocusItems
                allItems = loadedItems
                hasLoadedOnce = true
                selectedTaskIds.retainAll(allTasks.map { it.id }.toSet())
                renderSelectedTab()
                if (isFirstLoad && recyclerView.visibility == View.VISIBLE) {
                    recyclerView.alpha = 0f
                    recyclerView.animate().alpha(1f).setDuration(180L).start()
                }
            }
            if (isFirstLoad) {
                val enrichedItems = (projectRepository.loadProjects(loadedTasks, includeMetadata = true) + knowledgeItems + noteItems)
                    .sortedWith(compareBy({ it.type.displayName }, { it.category ?: "" }, { it.title }))
                runOnUiThread {
                    if (isFinishing || isDestroyed || refreshId != refreshVersion) return@runOnUiThread
                    allItems = enrichedItems
                    renderSelectedTab()
                }
            }
        }
    }

    private fun renderSelectedTab() {
        val hasFolder = TranscriptStore.configuredFolderUri(this) != null
        val modeItems = filteredItemsForMode(allItems)
        val visibleItems = visibleItemsForTab(selectedTab, modeItems)
        updateActiveHero()
        val baseTasks = when (selectedTab) {
            MainTab.TASKS -> filteredTasksForMode(allTasks.filterNot { it.isSomeday || it.isProjectShell() }, selectedTab)
            MainTab.SOMEDAY -> filteredTasksForMode(allTasks.filter { it.isSomeday && !it.isProjectShell() }, selectedTab)
            MainTab.COMPLETED -> filteredCompletedTasksForMode(allCompletedTasks)
            else -> emptyList()
        }
        val availableContexts = baseTasks.flatMap { it.contexts }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
        val hasProcessTasks = selectedTab == MainTab.TASKS && baseTasks.any { it.contexts.isEmpty() }
        if (selectedTaskContextFilter == PROCESS_FILTER_KEY && !hasProcessTasks) {
            selectedTaskContextFilter = null
        }
        if (selectedTaskContextFilter != null &&
            selectedTaskContextFilter != PROCESS_FILTER_KEY &&
            availableContexts.none { it.equals(selectedTaskContextFilter, ignoreCase = true) }
        ) {
            selectedTaskContextFilter = null
        }
        val visibleTasks = if (selectedTab == MainTab.SOMEDAY || selectedTab == MainTab.COMPLETED) {
            baseTasks
        } else if (selectedTaskContextFilter == null) {
            baseTasks
        } else if (selectedTaskContextFilter == PROCESS_FILTER_KEY) {
            baseTasks.filter { it.contexts.isEmpty() }
        } else {
            baseTasks.filter { task ->
                task.contexts.any { it.equals(selectedTaskContextFilter, ignoreCase = true) }
            }
        }

        if (hasFolder && shouldRecoverToDifferentTab(visibleItems, visibleTasks)) {
            val recoveryTab = preferredTabForMode(selectedMode)
            if (recoveryTab != selectedTab) {
                selectedTab = recoveryTab
                selectedTaskIds.clear()
                selectedTaskContextFilter = defaultTaskFilterForCurrentTab()
                setupTabs()
                renderSelectedTab()
                return
            }
        }

        val entries = when {
            !hasFolder -> {
                emptyStateText.text = getString(R.string.empty_no_folder)
                emptyStateText.visibility = View.VISIBLE
                emptyStateText.alpha = 1f
                recyclerView.visibility = View.GONE
                recyclerView.alpha = 0f
                emptyList()
            }

            selectedTab !in setOf(MainTab.TASKS, MainTab.SOMEDAY, MainTab.COMPLETED, MainTab.NOTES) && allItems.isEmpty() -> {
                emptyStateText.text = getString(R.string.empty_no_items)
                emptyStateText.visibility = View.VISIBLE
                emptyStateText.alpha = 1f
                recyclerView.visibility = View.GONE
                recyclerView.alpha = 0f
                emptyList()
            }

            selectedTab == MainTab.TASKS || selectedTab == MainTab.SOMEDAY || selectedTab == MainTab.COMPLETED -> {
                if (visibleTasks.isEmpty()) {
                    emptyStateText.text = getString(R.string.empty_no_items_for_tab, selectedTab.title)
                    emptyStateText.visibility = View.VISIBLE
                    emptyStateText.alpha = 1f
                    recyclerView.visibility = View.GONE
                    recyclerView.alpha = 0f
                    emptyList()
                } else {
                    emptyStateText.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    if (selectedTab == MainTab.SOMEDAY) {
                        buildSomedayTaskEntries(visibleTasks)
                    } else if (selectedTab == MainTab.COMPLETED) {
                        buildCompletedTaskEntries(visibleTasks)
                    } else {
                        buildTaskEntries(visibleTasks)
                    }
                }
            }

            visibleItems.isEmpty() -> {
                emptyStateText.text = when (selectedTab) {
                    MainTab.ACTIVE -> getString(R.string.empty_no_active_items)
                    MainTab.NOTES -> getString(R.string.note_empty_status)
                    else -> getString(R.string.empty_no_items_for_tab, selectedTab.title)
                }
                emptyStateText.visibility = View.VISIBLE
                emptyStateText.alpha = 1f
                recyclerView.visibility = View.GONE
                recyclerView.alpha = 0f
                emptyList()
            }

            else -> {
                emptyStateText.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                buildEntriesForTab(selectedTab, visibleItems)
            }
        }

        adapter.submitList(
            newEntries = entries,
            displayMode = if (selectedTab == MainTab.ACTIVE) {
                ManagedItemAdapter.DisplayMode.ACTIVE
            } else {
                ManagedItemAdapter.DisplayMode.BROWSING
            },
            selectedTaskIds = selectedTaskIds.toSet(),
            hideSomedayContext = selectedTab == MainTab.SOMEDAY,
            hideAreaTags = selectedTab == MainTab.SOMEDAY,
            accentColorRes = selectedMode.accentColorRes,
            taskInteractionEnabled = selectedTab == MainTab.TASKS || selectedTab == MainTab.SOMEDAY
        )

        updateFab(hasFolder)
        updateTaskActionBar(hasFolder)
        updateTaskFilterRow(availableContexts, hasProcessTasks, hasFolder)
    }

    private fun visibleItemsForTab(tab: MainTab, items: List<ManagedItem>): List<ManagedItem> {
        return when (tab) {
            MainTab.ACTIVE -> items.filter { it.state == ItemState.ACTIVE && it.type != ManagedItemType.NOTE }
            MainTab.TASKS -> emptyList()
            MainTab.NOTES -> items.filter { it.type == ManagedItemType.NOTE }
            MainTab.MILESTONES -> items.filter { it.type == ManagedItemType.MILESTONE }
            MainTab.SOMEDAY -> emptyList()
            MainTab.COMPLETED -> emptyList()
            MainTab.PROJECTS -> items.filter { it.type == ManagedItemType.PROJECT }
            MainTab.AREAS_OF_INTEREST -> items.filter { it.type == ManagedItemType.AREA_OF_INTEREST }
            MainTab.GOALS -> items.filter { it.type == ManagedItemType.GOAL }
            MainTab.VISION -> items.filter { it.type == ManagedItemType.VISION }
        }
    }

    private fun availableTabs(): List<MainTab> {
        return availableTabsForMode(selectedMode)
    }

    private fun ensureValidSelectedTab() {
        if (selectedTab !in availableTabs()) {
            selectedTab = availableTabs().first()
        }
    }

    private fun defaultTaskFilterForCurrentTab(): String? {
        return if (selectedTab == MainTab.TASKS && selectedMode == AppMode.ALL) {
            PROCESS_FILTER_KEY
        } else {
            null
        }
    }

    private fun preferredTabForMode(mode: AppMode): MainTab {
        val candidates = when (mode) {
            AppMode.ALL -> listOf(MainTab.ACTIVE, MainTab.TASKS, MainTab.NOTES, MainTab.MILESTONES, MainTab.PROJECTS)
            AppMode.FOCUS -> listOf(MainTab.PROJECTS, MainTab.TASKS, MainTab.NOTES)
            AppMode.WORK -> listOf(MainTab.PROJECTS, MainTab.TASKS, MainTab.NOTES)
            AppMode.FUN -> listOf(MainTab.PROJECTS, MainTab.AREAS_OF_INTEREST, MainTab.GOALS, MainTab.VISION, MainTab.TASKS, MainTab.NOTES)
            AppMode.LOW_ENERGY -> listOf(MainTab.ACTIVE, MainTab.TASKS, MainTab.SOMEDAY, MainTab.NOTES)
            AppMode.REVIEW -> listOf(MainTab.MILESTONES, MainTab.PROJECTS, MainTab.SOMEDAY, MainTab.COMPLETED, MainTab.NOTES)
        }.filter { it in availableTabsForMode(mode) }

        return candidates.firstOrNull { tabHasContent(mode, it) }
            ?: availableTabsForMode(mode).firstOrNull()
            ?: MainTab.ACTIVE
    }

    private fun availableTabsForMode(mode: AppMode): List<MainTab> {
        return when (mode) {
            AppMode.FOCUS -> listOf(
                MainTab.PROJECTS,
                MainTab.TASKS,
                MainTab.NOTES
            )
            AppMode.WORK -> listOf(
                MainTab.PROJECTS,
                MainTab.TASKS,
                MainTab.NOTES
            )
            AppMode.FUN -> listOf(
                MainTab.PROJECTS,
                MainTab.TASKS,
                MainTab.NOTES,
                MainTab.AREAS_OF_INTEREST,
                MainTab.GOALS,
                MainTab.VISION
            )
            AppMode.LOW_ENERGY -> listOf(
                MainTab.ACTIVE,
                MainTab.TASKS,
                MainTab.SOMEDAY,
                MainTab.NOTES
            )
            AppMode.REVIEW -> listOf(
                MainTab.MILESTONES,
                MainTab.PROJECTS,
                MainTab.AREAS_OF_INTEREST,
                MainTab.GOALS,
                MainTab.SOMEDAY,
                MainTab.VISION,
                MainTab.COMPLETED,
                MainTab.NOTES
            )
            AppMode.ALL -> listOf(
                MainTab.ACTIVE,
                MainTab.TASKS,
                MainTab.NOTES,
                MainTab.MILESTONES,
                MainTab.SOMEDAY,
                MainTab.PROJECTS,
                MainTab.AREAS_OF_INTEREST,
                MainTab.GOALS,
                MainTab.VISION
            )
        }
    }

    private fun tabHasContent(mode: AppMode, tab: MainTab): Boolean {
        val previousMode = selectedMode
        selectedMode = mode
        return try {
            when (tab) {
                MainTab.TASKS -> filteredTasksForMode(
                    allTasks.filterNot { it.isSomeday || it.isProjectShell() },
                    tab
                ).isNotEmpty()
                MainTab.SOMEDAY -> filteredTasksForMode(
                    allTasks.filter { it.isSomeday && !it.isProjectShell() },
                    tab
                ).isNotEmpty()
                MainTab.COMPLETED -> filteredCompletedTasksForMode(allCompletedTasks).isNotEmpty()
                else -> visibleItemsForTab(tab, filteredItemsForMode(allItems)).isNotEmpty()
            }
        } finally {
            selectedMode = previousMode
        }
    }

    private fun shouldRecoverToDifferentTab(
        visibleItems: List<ManagedItem>,
        visibleTasks: List<TaskItem>
    ): Boolean {
        if (selectedTab.supportsEditing) return false
        return when (selectedTab) {
            MainTab.NOTES -> false
            MainTab.TASKS, MainTab.SOMEDAY, MainTab.COMPLETED -> visibleTasks.isEmpty() && availableTabs().size > 1
            else -> visibleItems.isEmpty() && availableTabs().size > 1
        }
    }

    private fun filteredItemsForMode(items: List<ManagedItem>): List<ManagedItem> {
        return when (selectedMode) {
            AppMode.ALL -> items
            AppMode.FOCUS -> items.filter { item ->
                item.type == ManagedItemType.NOTE ||
                    (item.type == ManagedItemType.PROJECT && item.state == ItemState.ACTIVE)
            }
            AppMode.WORK -> items.filter { item ->
                when (item.type) {
                    ManagedItemType.NOTE -> true
                    ManagedItemType.PROJECT -> projectGroupTitle(item).equals("Work Project", ignoreCase = true)
                    else -> matchesAreaOfFocus(item.category, WORK_AREA_OF_FOCUS)
                }
            }
            AppMode.FUN -> items.filter { item ->
                item.type == ManagedItemType.NOTE || matchesAnyAreaOfFocus(item.category, FUN_AREA_OF_FOCUS_KEYS)
            }
            AppMode.LOW_ENERGY -> items
            AppMode.REVIEW -> items.filter {
                it.type in setOf(
                    ManagedItemType.NOTE,
                    ManagedItemType.PROJECT,
                    ManagedItemType.MILESTONE,
                    ManagedItemType.AREA_OF_INTEREST,
                    ManagedItemType.GOAL,
                    ManagedItemType.VISION
                )
            }
        }
    }

    private fun filteredTasksForMode(tasks: List<TaskItem>, tab: MainTab): List<TaskItem> {
        return when (selectedMode) {
            AppMode.ALL -> tasks
            AppMode.FOCUS -> tasks.filter { task ->
                task.contexts.any { it.equals("Focus", ignoreCase = true) }
            }
            AppMode.WORK -> tasks.filter { task ->
                task.contexts.any { it.equals("office", ignoreCase = true) } ||
                    task.areas.any { TaskTags.toCanonicalKey(it) == WORK_AREA_OF_FOCUS_KEY }
            }
            AppMode.FUN -> tasks.filter { task ->
                task.areas.any { TaskTags.toCanonicalKey(it) in FUN_AREA_OF_FOCUS_KEYS }
            }
            AppMode.LOW_ENERGY -> if (tab == MainTab.TASKS) emptyList() else tasks
            AppMode.REVIEW -> if (tab == MainTab.SOMEDAY) tasks else emptyList()
        }
    }

    private fun filteredCompletedTasksForMode(tasks: List<TaskItem>): List<TaskItem> {
        return if (selectedMode == AppMode.REVIEW) tasks else emptyList()
    }

    private fun onHeaderTapped(header: ListEntry.Header) {
        if (!header.isCollapsible) return
        when (selectedTab) {
            MainTab.AREAS_OF_INTEREST -> {
                showAreaOfFocusDetailDialog(header.key)
                return
            }
            MainTab.SOMEDAY -> {
                if (!expandedSomedaySections.add(header.key)) {
                    expandedSomedaySections.remove(header.key)
                }
            }
            else -> return
        }
        renderSelectedTab()
    }

    private fun buildEntriesForTab(tab: MainTab, items: List<ManagedItem>): List<ListEntry> {
        val sortedItems = items.sortedWith(compareBy({ it.category ?: "" }, { it.title }))
        val entries = mutableListOf<ListEntry>()

        if (tab == MainTab.ACTIVE) {
            sortedItems
                .groupBy { it.type.displayName }
                .toSortedMap()
                .forEach { (groupTitle, groupItems) ->
                    entries += ListEntry.Header(
                        title = groupTitle,
                        badgeText = groupItems.size.toString()
                    )
                    groupItems.sortedBy { it.title }.forEach { entries += ListEntry.ItemRow(it) }
                }
            return entries
        }

        if (tab == MainTab.AREAS_OF_INTEREST) {
            val grouped = sortedItems.groupBy { it.category ?: getString(R.string.uncategorized_label) }
            val focusItems = allAreaOfFocusItems.ifEmpty {
                allAreasOfFocus.map { title ->
                    ManagedItem(
                        id = TaskTags.toStorageValue(title),
                        title = title,
                        type = ManagedItemType.AREA_OF_FOCUS,
                        category = null,
                        parent = null,
                        state = ItemState.ACTIVE,
                        imagePath = null,
                        notes = null,
                        dateText = null,
                        isEditable = true,
                        sourceName = "App Data"
                    )
                }
            }
            focusItems.forEach { focus ->
                val groupItems = grouped[focus.title].orEmpty()
                val projectCount = allItems.count { it.type == ManagedItemType.PROJECT && matchesAreaOfFocus(it.category, focus.title) }
                val actionCount = allTasks.count { !it.isSomeday && !it.isProjectShell() && taskHasAreaOfFocus(it, focus.title) }
                entries += ListEntry.AreaFocusTile(
                    item = focus,
                    summary = "${groupItems.size} interests  /  $projectCount projects  /  $actionCount actions"
                )
            }
            return entries
        }

        if (tab == MainTab.PROJECTS) {
            val groupOrder = listOf("Projects", "Work Project")
            val grouped = sortedItems.groupBy { projectGroupTitle(it) }
            val orderedGroups = buildList {
                addAll(groupOrder.filter { grouped.containsKey(it) })
                addAll(grouped.keys.filterNot { groupOrder.contains(it) }.sorted())
            }
            orderedGroups.forEach { groupTitle ->
                val groupItems = grouped[groupTitle].orEmpty().sortedBy { it.title }
                entries += ListEntry.Header(
                    title = groupTitle,
                    badgeText = groupItems.size.toString()
                )
                groupItems.forEach { entries += ListEntry.ItemRow(it) }
            }
            return entries
        }

        if (tab == MainTab.NOTES) {
            return sortedItems.sortedBy { it.title.lowercase() }.map { ListEntry.ItemRow(it) }
        }

        if (tab == MainTab.MILESTONES) {
            return sortedItems
                .sortedWith(compareBy({ it.state.ordinal }, { it.dateText ?: "" }, { it.title.lowercase() }))
                .mapIndexed { index, item -> ListEntry.MilestoneRow(item, index + 1) }
        }

        sortedItems
            .groupBy { it.category ?: tab.title }
            .toSortedMap()
            .forEach { (groupTitle, groupItems) ->
                entries += ListEntry.Header(
                    title = groupTitle,
                    badgeText = groupItems.size.toString()
                )
                groupItems.sortedBy { it.title }.forEach { entries += ListEntry.ItemRow(it) }
        }
        return entries
    }

    private fun projectGroupTitle(item: ManagedItem): String {
        return item.parent
            ?.split("•")
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: MainTab.PROJECTS.title
    }

    private fun buildTaskEntries(tasks: List<TaskItem>): List<ListEntry> {
        return tasks.map { ListEntry.TaskRow(it) }
    }

    private fun buildSomedayTaskEntries(tasks: List<TaskItem>): List<ListEntry> {
        val grouped = tasks.groupBy { somedayGroupTitle(it) }
        val orderedGroups = buildList {
            addAll(allAreasOfFocus.filter { grouped.containsKey(it) })
            addAll(grouped.keys.filterNot { it in allAreasOfFocus || it == getString(R.string.unknown_label) }.sorted())
            if (grouped.containsKey(getString(R.string.unknown_label))) {
                add(getString(R.string.unknown_label))
            }
        }.distinct()

        return buildList {
            orderedGroups.forEach { groupTitle ->
                val headerKey = "someday:$groupTitle"
                val sectionTasks = grouped[groupTitle].orEmpty()
                    .sortedBy { it.title.lowercase() }
                val isExpanded = headerKey in expandedSomedaySections
                add(
                    ListEntry.Header(
                        title = groupTitle,
                        key = headerKey,
                        badgeText = sectionTasks.size.toString(),
                        isCollapsible = true,
                        isExpanded = isExpanded
                    )
                )
                if (isExpanded) {
                    sectionTasks.forEach { add(ListEntry.TaskRow(it)) }
                }
            }
        }
    }

    private fun buildCompletedTaskEntries(tasks: List<TaskItem>): List<ListEntry> {
        val completedProjects = tasks.filter { it.isProjectShell() }.sortedByDescending { it.completedDate ?: "" }
        val completedTasks = tasks.filterNot { it.isProjectShell() }.sortedByDescending { it.completedDate ?: "" }
        return buildList {
            if (completedProjects.isNotEmpty()) {
                add(
                    ListEntry.Header(
                        title = getString(R.string.completed_projects_title),
                        badgeText = completedProjects.size.toString()
                    )
                )
                completedProjects.forEach { add(ListEntry.TaskRow(it)) }
            }
            if (completedTasks.isNotEmpty()) {
                add(
                    ListEntry.Header(
                        title = getString(R.string.completed_tasks_title),
                        badgeText = completedTasks.size.toString()
                    )
                )
                completedTasks.forEach { add(ListEntry.TaskRow(it)) }
            }
        }
    }

    private fun somedayGroupTitle(task: TaskItem): String {
        val area = task.areas.firstOrNull()?.trim().orEmpty()
        if (area.isBlank()) return getString(R.string.unknown_label)

        return allAreasOfFocus.firstOrNull {
            TaskTags.toCanonicalKey(it) == TaskTags.toCanonicalKey(area)
        } ?: TaskTags.toDisplayValue(area).ifBlank { getString(R.string.unknown_label) }
    }

    private fun updateFab(hasFolder: Boolean) {
        addFab.visibility = if (selectedTab.supportsEditing && hasFolder) View.VISIBLE else View.GONE
    }

    private fun updateActiveHero() {
        activeHeroPanel.visibility = View.GONE
    }

    private fun updateTaskActionBar(hasFolder: Boolean) {
        val showBar = hasFolder &&
            (selectedTab == MainTab.TASKS || selectedTab == MainTab.SOMEDAY) &&
            selectedTasks().isNotEmpty()
        taskActionBar.visibility = if (showBar) View.VISIBLE else View.GONE
        addFab.visibility = if (showBar) View.GONE else addFab.visibility
        findViewById<View>(R.id.task_edit_button).apply {
            val enabled = selectedTasks().size == 1
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.35f
        }
    }

    private fun updateTaskFilterRow(
        availableContexts: List<String>,
        hasProcessTasks: Boolean,
        hasFolder: Boolean
    ) {
        val showFilters = hasFolder &&
            selectedTab == MainTab.TASKS &&
            (availableContexts.isNotEmpty() || hasProcessTasks)
        taskFilterScroll.visibility = if (showFilters) View.VISIBLE else View.GONE
        if (!showFilters) return

        taskFilterGroup.removeAllViews()
        taskFilterGroup.addView(makeFilterChip(label = "All", isSelected = selectedTaskContextFilter == null) {
            selectedTaskIds.clear()
            selectedTaskContextFilter = null
            renderSelectedTab()
        })
        if (selectedTab == MainTab.TASKS && hasProcessTasks) {
            taskFilterGroup.addView(
                makeFilterChip(
                    label = getString(R.string.task_filter_process),
                    isSelected = selectedTaskContextFilter == PROCESS_FILTER_KEY
                ) {
                    selectedTaskIds.clear()
                    selectedTaskContextFilter = PROCESS_FILTER_KEY
                    renderSelectedTab()
                }
            )
        }
        availableContexts.forEach { context ->
            taskFilterGroup.addView(
                makeFilterChip(
                    label = TaskTags.toDisplayLabel("@", context),
                    isSelected = context.equals(selectedTaskContextFilter, ignoreCase = true)
                ) {
                    selectedTaskIds.clear()
                    selectedTaskContextFilter = context
                    renderSelectedTab()
                }
            )
        }
    }

    private fun makeFilterChip(
        label: String,
        isSelected: Boolean,
        onClick: () -> Unit
    ): Chip {
        return Chip(this).apply {
            text = label
            isCheckable = false
            isClickable = true
            chipMinHeight = 30f
            chipCornerRadius = 16f
            chipStartPadding = 10f
            chipEndPadding = 10f
            chipBackgroundColor = ContextCompat.getColorStateList(
                context,
                if (isSelected) selectedMode.accentColorRes else R.color.surface_muted
            )
            setTextColor(
                ContextCompat.getColor(
                    context,
                    if (isSelected) R.color.text_inverse else R.color.text_secondary
                )
            )
            setOnClickListener { onClick() }
        }
    }

    private fun onItemTapped(item: ManagedItem) {
        if (selectedTab == MainTab.ACTIVE) return
        if (selectedTab == MainTab.AREAS_OF_INTEREST && item.type == ManagedItemType.AREA_OF_FOCUS) {
            showAreaOfFocusScreen(item.title)
            return
        }
        if (selectedTab == MainTab.PROJECTS && item.type == ManagedItemType.PROJECT) {
            showProjectDetailDialog(item.id)
            return
        }
        if (selectedTab == MainTab.NOTES && item.type == ManagedItemType.NOTE) {
            showNoteDetailDialog(item.id)
            return
        }
        if (selectedTab == MainTab.MILESTONES && item.type == ManagedItemType.MILESTONE) {
            showMilestoneDetailDialog(item.id)
            return
        }
        val newState = item.state.next()
        allItems = allItems.map { existing ->
            if (existing.id == item.id) existing.copy(state = newState) else existing
        }
        renderSelectedTab()
        taskIoExecutor.execute {
            repository.setState(item.id, newState)
        }
    }

    private fun onTaskTapped(task: TaskItem) {
        if (selectedTab != MainTab.TASKS && selectedTab != MainTab.SOMEDAY) return
        if (!selectedTaskIds.add(task.id)) {
            selectedTaskIds.remove(task.id)
        }
        renderSelectedTab()
    }

    private fun onTaskChecked(task: TaskItem) {
        allTasks = allTasks.filterNot { it.id == task.id }
        selectedTaskIds.remove(task.id)
        renderSelectedTab()
        taskIoExecutor.execute {
            taskRepository.completeTask(task)
            runOnUiThread { refreshUi() }
        }
    }

    private fun selectedTask(): TaskItem? {
        return selectedTasks().singleOrNull()
    }

    private fun selectedTasks(): List<TaskItem> {
        if (selectedTaskIds.isEmpty()) return emptyList()
        return allTasks.filter { it.id in selectedTaskIds }
    }

    private fun showTaskEditDialog(
        existing: TaskItem?,
        defaultSomeday: Boolean = false,
        defaultProject: String? = null,
        onSaved: (() -> Unit)? = null
    ) {
        val draft = TaskDraft(
            title = existing?.title.orEmpty(),
            prefixTokens = existing?.prefixTokens?.toMutableList() ?: mutableListOf(),
            contexts = existing?.contexts?.toMutableList()
                ?: mutableListOf<String>().apply {
                    if (defaultSomeday) add("Someday")
                },
            projects = existing?.projects?.toMutableList()
                ?: mutableListOf<String>().apply {
                    defaultProject?.takeIf { it.isNotBlank() }?.let { add(it) }
                },
            areas = existing?.areas?.toMutableList() ?: mutableListOf(),
            priority = existing?.priority,
            dueDate = existing?.dueDate
        )
        val dialogView = layoutInflater.inflate(R.layout.dialog_task_editor, null)
        val heading = dialogView.findViewById<TextView>(R.id.task_editor_heading)
        val titleInput = dialogView.findViewById<EditText>(R.id.task_editor_title_input)
        val metaGroup = dialogView.findViewById<ChipGroup>(R.id.task_editor_meta_group)
        val dialog = BottomSheetDialog(this)

        heading.text = getString(
            if (existing == null) R.string.task_editor_add_title else R.string.task_editor_edit_title
        )
        titleInput.setText(draft.title)

        fun renderDraftMeta() {
            metaGroup.removeAllViews()
            draft.contexts.forEach { value ->
                metaGroup.addView(makeDraftMetaChip(TaskTags.toDisplayLabel("@", value)) {
                    draft.contexts.remove(value)
                    renderDraftMeta()
                })
            }
            draft.projects.forEach { value ->
                metaGroup.addView(makeDraftMetaChip(TaskTags.toDisplayLabel("+", value)) {
                    draft.projects.remove(value)
                    renderDraftMeta()
                })
            }
            draft.areas.forEach { value ->
                metaGroup.addView(makeDraftMetaChip(TaskTags.toDisplayLabel("#", value)) {
                    draft.areas.remove(value)
                    renderDraftMeta()
                })
            }
            draft.priority?.let { priority ->
                metaGroup.addView(makeDraftMetaChip("P$priority") {
                    draft.priority = null
                    renderDraftMeta()
                })
            }
            draft.dueDate?.let { dueDate ->
                metaGroup.addView(makeDraftMetaChip(getString(R.string.task_due_label, dueDate)) {
                    draft.dueDate = null
                    renderDraftMeta()
                })
            }
            metaGroup.visibility = if (metaGroup.childCount == 0) View.GONE else View.VISIBLE
        }

        dialogView.findViewById<ImageButton>(R.id.task_editor_context_button).setOnClickListener {
            showTaskTagDialog(draft, "@", ::renderDraftMeta)
        }
        dialogView.findViewById<ImageButton>(R.id.task_editor_project_button).setOnClickListener {
            showTaskTagDialog(draft, "+", ::renderDraftMeta)
        }
        dialogView.findViewById<ImageButton>(R.id.task_editor_area_button).setOnClickListener {
            showTaskTagDialog(draft, "#", ::renderDraftMeta)
        }
        dialogView.findViewById<ImageButton>(R.id.task_editor_priority_button).setOnClickListener {
            showPriorityDialog(draft, ::renderDraftMeta)
        }
        dialogView.findViewById<ImageButton>(R.id.task_editor_due_button).setOnClickListener {
            showDueDateDialog(draft, ::renderDraftMeta)
        }
        dialogView.findViewById<ImageButton>(R.id.task_editor_save_button).setOnClickListener {
            val title = titleInput.text.toString().trim()
            if (title.isBlank()) {
                titleInput.error = getString(R.string.task_editor_title_hint)
                return@setOnClickListener
            }

            if (existing == null) {
                taskRepository.addTask(
                    title = title,
                    contexts = draft.contexts,
                    projects = draft.projects,
                    areas = draft.areas,
                    priority = draft.priority,
                    dueDate = draft.dueDate,
                    prefixTokens = draft.prefixTokens
                )
            } else {
                taskRepository.updateTask(
                    existing.copy(
                        title = title,
                        prefixTokens = draft.prefixTokens,
                        contexts = draft.contexts.distinct(),
                        projects = draft.projects.distinct(),
                        areas = draft.areas.distinct(),
                        isSomeday = draft.contexts.any { it.isSomedayContext() },
                        priority = draft.priority,
                        dueDate = draft.dueDate
                    )
                )
            }
            selectedTaskIds.clear()
            dialog.dismiss()
            refreshUi()
            onSaved?.invoke()
        }

        renderDraftMeta()
        dialog.setContentView(dialogView)
        dialog.show()
        dialog.behavior.skipCollapsed = true
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )
        titleInput.post {
            titleInput.requestFocus()
            titleInput.setSelection(titleInput.text?.length ?: 0)
            getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(titleInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun showTaskTagDialog(task: TaskItem, prefix: String) {
        showTaskTagPicker(prefix, usedValues = usedTagValues(task, prefix)) { value ->
            applyTaskTag(task, prefix, value)
        }
    }

    private fun showTaskTagDialog(tasks: List<TaskItem>, prefix: String) {
        if (tasks.isEmpty()) return
        val usedValues = tasks
            .map { usedTagValues(it, prefix) }
            .fold<Set<String>, Set<String>>(emptySet()) { acc, set -> acc + set }
        showTaskTagPicker(prefix, usedValues = usedValues) { value ->
            applyTaskTag(tasks, prefix, value)
        }
    }

    private fun showTaskTagDialog(draft: TaskDraft, prefix: String, onChanged: () -> Unit) {
        showTaskTagPicker(prefix, usedValues = usedTagValues(draft, prefix)) { value ->
            when (prefix) {
                "@" -> draft.contexts.add(value)
                "+" -> draft.projects.add(value)
                else -> draft.areas.add(value)
            }
            when (prefix) {
                "@" -> draft.contexts.replaceWithDistinct()
                "+" -> draft.projects.replaceWithDistinct()
                else -> draft.areas.replaceWithDistinct()
            }
            onChanged()
        }
    }

    private fun showTaskTagPicker(
        prefix: String,
        usedValues: Set<String>,
        onPicked: (String) -> Unit
    ) {
        val titleRes = when (prefix) {
            "@" -> R.string.task_add_context
            "+" -> R.string.task_add_project
            else -> R.string.task_add_area
        }
        if (prefix == "+") {
            val entries = buildList {
                addAll(projectTagSheetEntries(usedValues, onPicked))
                add(
                    PickerSheetEntry.Option(getString(R.string.task_add_new_tag)) {
                        showNewTagInputDialog(titleRes, onPicked)
                    }
                )
            }
            showOptionSheet(getString(titleRes), entries)
            return
        }

        val existingOptions = existingTagChoices(prefix, usedValues)
        showOptionSheet(
            title = getString(titleRes),
            entries = buildList {
                addAll(
                    existingOptions.map { option ->
                        PickerSheetEntry.Option(option.displayLabel) {
                            onPicked(option.storageValue)
                        }
                    }
                )
                add(
                    PickerSheetEntry.Option(getString(R.string.task_add_new_tag)) {
                        showNewTagInputDialog(titleRes, onPicked)
                    }
                )
            }
        )
    }

    private fun showNewTagInputDialog(titleRes: Int, onPicked: (String) -> Unit) {
        showTextInputSheet(getString(titleRes), getString(R.string.task_tag_input_message)) { value ->
            onPicked(value.trim().trimStart('@', '+', '#'))
        }
    }

    private fun showAreaOfFocusPicker(currentValue: String?, onPicked: (String?) -> Unit) {
        val current = currentValue?.trim().orEmpty()
        val options = canonicalizeAreaOfFocusTitles(
            buildList {
                if (current.isNotBlank() && allAreasOfFocus.none {
                        TaskTags.toCanonicalKey(it) == TaskTags.toCanonicalKey(current)
                    }
                ) {
                    add(current)
                }
                addAll(allAreasOfFocus)
            }
        )

        showOptionSheet(
            title = getString(R.string.project_area_of_focus_label),
            entries = buildList {
                addAll(options.map { value ->
                    PickerSheetEntry.Option(value) { onPicked(value) }
                })
                add(PickerSheetEntry.Option(getString(R.string.project_add_new_area_of_focus)) {
                    showNewAreaOfFocusDialog(onPicked)
                })
                if (current.isNotBlank()) {
                    add(PickerSheetEntry.Option(getString(R.string.project_clear_area_of_focus)) {
                        onPicked(null)
                    })
                }
            }
        )
    }

    private fun showNewAreaOfFocusDialog(onPicked: (String?) -> Unit) {
        showTextInputSheet(
            title = getString(R.string.project_add_new_area_of_focus),
            hint = getString(R.string.project_area_of_focus_hint)
        ) { value ->
            if (value.isNotBlank()) {
                onPicked(value)
            }
        }
    }

    private fun applyTaskTag(task: TaskItem, prefix: String, value: String) {
        val cleaned = TaskTags.toStorageValue(value)
        if (cleaned.isBlank()) return

        val updated = when (prefix) {
            "@" -> task.copy(
                contexts = (task.contexts + cleaned).distinct(),
                isSomeday = (task.contexts + cleaned).any { it.isSomedayContext() }
            )
            "+" -> task.copy(projects = (task.projects + cleaned).distinct())
            else -> task.copy(areas = (task.areas + cleaned).distinct())
        }
        taskRepository.updateTask(updated)
        selectedTaskIds.clear()
        refreshUi()
    }

    private fun applyTaskTag(tasks: List<TaskItem>, prefix: String, value: String) {
        val cleaned = TaskTags.toStorageValue(value)
        if (cleaned.isBlank() || tasks.isEmpty()) return

        val updatedTasks = tasks.map { task ->
            when (prefix) {
                "@" -> task.copy(
                    contexts = (task.contexts + cleaned).distinct(),
                    isSomeday = (task.contexts + cleaned).any { it.isSomedayContext() }
                )
                "+" -> task.copy(projects = (task.projects + cleaned).distinct())
                else -> task.copy(areas = (task.areas + cleaned).distinct())
            }
        }
        taskRepository.updateTasks(updatedTasks)
        selectedTaskIds.clear()
        refreshUi()
    }

    private fun existingTagChoices(prefix: String, usedValues: Set<String>): List<TagChoice> {
        val choices = when (prefix) {
            "@" -> allTasks.flatMap { it.contexts }
                .map { value -> TagChoice(TaskTags.toStorageValue(value), TaskTags.toDisplayLabel("@", value)) }
            "+" -> projectTagChoices()
            else -> buildList {
                allAreasOfFocus.forEach { title ->
                    add(
                        TagChoice(
                            storageValue = TaskTags.toStorageValue(title),
                            displayLabel = "#$title"
                        )
                    )
                }
                allTasks.flatMap { it.areas }.forEach { value ->
                    add(
                        TagChoice(
                            storageValue = TaskTags.toStorageValue(value),
                            displayLabel = "#${TaskTags.toDisplayValue(value)}"
                        )
                    )
                }
            }
        }

        return choices
            .filter { it.storageValue.isNotBlank() }
            .groupBy { TaskTags.toCanonicalKey(it.storageValue) }
            .values
            .map { variants ->
                variants.maxWithOrNull(
                    compareBy<TagChoice> { choice ->
                        choice.displayLabel.count { it.isUpperCase() }
                    }.thenByDescending { choice ->
                        choice.displayLabel.contains('&')
                    }.thenByDescending { choice ->
                        choice.displayLabel.contains(',')
                    }.thenByDescending { choice ->
                        choice.displayLabel.length
                    }
                ) ?: variants.first()
            }
            .filterNot { usedValues.contains(TaskTags.toCanonicalKey(it.storageValue)) }
            .sortedBy { it.displayLabel.lowercase() }
    }

    private fun projectTagChoices(): List<TagChoice> {
        val shellOrder = mapOf(
            "project" to 0,
            "workproject" to 1
        )

        return allTasks
            .filter { it.isProjectShell() }
            .map { task ->
                val shellType = task.contexts.firstOrNull { it.isProjectShellContext() } ?: PROJECT_SHELL_PROJECT
                val slug = task.projects.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: TaskTags.toStorageValue(task.title)
                val title = task.title.trim().ifBlank { TaskTags.toDisplayValue(slug) }
                ProjectShellChoice(
                    shellType = shellType,
                    storageValue = slug,
                    displayLabel = title
                )
            }
            .filter { it.storageValue.isNotBlank() }
            .groupBy { TaskTags.toCanonicalKey(it.storageValue) }
            .values
            .map { variants ->
                variants.maxWithOrNull(
                    compareBy<ProjectShellChoice> { choice ->
                        shellOrder[choice.shellType.trim().lowercase()] ?: Int.MAX_VALUE
                    }.thenByDescending { choice ->
                        choice.displayLabel.count { it.isUpperCase() }
                    }.thenByDescending { choice ->
                        choice.displayLabel.length
                    }
                ) ?: variants.first()
            }
            .sortedWith(
                compareBy<ProjectShellChoice> { choice ->
                    shellOrder[choice.shellType.trim().lowercase()] ?: Int.MAX_VALUE
                }.thenBy { it.displayLabel.lowercase() }
            )
            .map { TagChoice(it.storageValue, it.displayLabel) }
    }

    private fun projectTagSheetEntries(
        usedValues: Set<String>,
        onPicked: (String) -> Unit
    ): List<PickerSheetEntry> {
        val groupedChoices = allTasks
            .filter { it.isProjectShell() }
            .mapNotNull { task ->
                val shellType = task.contexts.firstOrNull { it.isProjectShellContext() } ?: return@mapNotNull null
                val slug = task.projects.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: TaskTags.toStorageValue(task.title)
                if (slug.isBlank() || TaskTags.toCanonicalKey(slug) in usedValues) return@mapNotNull null
                ProjectShellChoice(
                    shellType = shellType,
                    storageValue = slug,
                    displayLabel = task.title.trim().ifBlank { TaskTags.toDisplayValue(slug) }
                )
            }
            .groupBy { it.shellType.trim().lowercase() }

        val order = listOf(
            "project" to "Projects",
            "workproject" to "Work Projects"
        )

        return buildList {
            order.forEach { (key, label) ->
                val items = groupedChoices[key].orEmpty()
                    .distinctBy { TaskTags.toCanonicalKey(it.storageValue) }
                    .sortedBy { it.displayLabel.lowercase() }
                if (items.isEmpty()) return@forEach
                add(PickerSheetEntry.Section(label))
                addAll(items.map { option ->
                    PickerSheetEntry.Option(option.displayLabel) {
                        onPicked(option.storageValue)
                    }
                })
            }
        }
    }

    private fun showPriorityDialog(task: TaskItem) {
        showPriorityDialog { priority ->
            taskRepository.updateTask(task.copy(priority = priority))
            selectedTaskIds.clear()
            refreshUi()
        }
    }

    private fun showPriorityDialog(tasks: List<TaskItem>) {
        if (tasks.isEmpty()) return
        showPriorityDialog { priority ->
            taskRepository.updateTasks(tasks.map { it.copy(priority = priority) })
            selectedTaskIds.clear()
            refreshUi()
        }
    }

    private fun showPriorityDialog(draft: TaskDraft, onChanged: () -> Unit) {
        showPriorityDialog { priority ->
            draft.priority = priority
            onChanged()
        }
    }

    private fun showPriorityDialog(onPicked: (String?) -> Unit) {
        showOptionSheet(
            title = getString(R.string.task_add_priority),
            entries = listOf(
                PickerSheetEntry.Option(getString(R.string.task_priority_high)) { onPicked("A") },
                PickerSheetEntry.Option(getString(R.string.task_priority_medium)) { onPicked("B") },
                PickerSheetEntry.Option(getString(R.string.task_priority_low)) { onPicked("C") },
                PickerSheetEntry.Option(getString(R.string.task_priority_clear)) { onPicked(null) }
            )
        )
    }

    private fun showDueDateDialog(task: TaskItem) {
        showDueDateDialog(task.dueDate) { dueDate ->
            taskRepository.updateTask(task.copy(dueDate = dueDate))
            selectedTaskIds.clear()
            refreshUi()
        }
    }

    private fun showDueDateDialog(tasks: List<TaskItem>) {
        if (tasks.isEmpty()) return
        showDueDateDialog(null) { dueDate ->
            taskRepository.updateTasks(tasks.map { it.copy(dueDate = dueDate) })
            selectedTaskIds.clear()
            refreshUi()
        }
    }

    private fun showDueDateDialog(draft: TaskDraft, onChanged: () -> Unit) {
        showDueDateDialog(draft.dueDate) { dueDate ->
            draft.dueDate = dueDate
            onChanged()
        }
    }

    private fun showDueDateDialog(currentDueDate: String?, onPicked: (String) -> Unit) {
        val initialDate = currentDueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                onPicked(LocalDate.of(year, month + 1, dayOfMonth).toString())
            },
            initialDate.year,
            initialDate.monthValue - 1,
            initialDate.dayOfMonth
        ).show()
    }

    private fun makeDraftMetaChip(label: String, onRemove: () -> Unit): Chip {
        return Chip(this).apply {
            text = label
            isCheckable = false
            isClickable = true
            isCloseIconVisible = true
            chipBackgroundColor = ContextCompat.getColorStateList(context, R.color.task_meta_bg)
            setTextColor(ContextCompat.getColor(context, R.color.task_meta_text))
            closeIconTint = ContextCompat.getColorStateList(context, R.color.task_meta_text)
            setOnCloseIconClickListener { onRemove() }
            setOnClickListener { onRemove() }
        }
    }

    private fun usedTagValues(task: TaskItem, prefix: String): Set<String> {
        return when (prefix) {
            "@" -> task.contexts
            "+" -> task.projects
            else -> task.areas
        }.map { TaskTags.toCanonicalKey(it) }.toSet()
    }

    private fun usedTagValues(draft: TaskDraft, prefix: String): Set<String> {
        return when (prefix) {
            "@" -> draft.contexts
            "+" -> draft.projects
            else -> draft.areas
        }.map { TaskTags.toCanonicalKey(it) }.toSet()
    }

    private fun MutableList<String>.replaceWithDistinct() {
        val unique = this
            .map { TaskTags.toStorageValue(it) }
            .filter { it.isNotBlank() }
            .distinctBy { TaskTags.toCanonicalKey(it) }
        clear()
        addAll(unique)
    }

    private fun String.isSomedayContext(): Boolean {
        return equals("Someday", ignoreCase = true) || equals("notnow", ignoreCase = true)
    }

    private fun canonicalizeAreaOfFocusTitles(values: List<String>): List<String> {
        return values
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .groupBy { TaskTags.toCanonicalKey(it) }
            .values
            .map { variants ->
                variants.maxWithOrNull(
                    compareBy<String> { title ->
                        title.count { it.isUpperCase() }
                    }.thenBy { title ->
                        title.count { it.isLowerCase() }
                    }.thenByDescending { title ->
                        title.contains('&')
                    }.thenByDescending { title ->
                        title.contains(',')
                    }.thenByDescending { title ->
                        title.length
                    }
                ) ?: variants.first()
            }
            .sortedBy { it.lowercase() }
    }

    private data class TaskDraft(
        var title: String,
        val prefixTokens: MutableList<String>,
        val contexts: MutableList<String>,
        val projects: MutableList<String>,
        val areas: MutableList<String>,
        var priority: String?,
        var dueDate: String?
    )

    private data class TagChoice(
        val storageValue: String,
        val displayLabel: String
    )

    private data class ProjectShellChoice(
        val shellType: String,
        val storageValue: String,
        val displayLabel: String
    )

    private sealed interface PickerSheetEntry {
        data class Section(val title: String) : PickerSheetEntry
        data class Option(val title: String, val onClick: () -> Unit) : PickerSheetEntry
    }

    private companion object {
        const val PROCESS_FILTER_KEY = "__PROCESS__"
        const val PROJECT_SHELL_PROJECT = "Project"
        const val WORK_AREA_OF_FOCUS = "Professional Growth"
        const val WORK_AREA_OF_FOCUS_KEY = "professional-growth"
        val FUN_AREA_OF_FOCUS_KEYS = setOf(
            "fun-games",
            "style-ritual-aesthetic"
        )
        val PROJECT_SHELL_OPTIONS = listOf(
            PROJECT_SHELL_PROJECT,
            "workproject"
        )
    }

    private fun showItemActionsSheet(item: ManagedItem) {
        if (selectedTab == MainTab.ACTIVE || !item.isEditable) return

        val dialogView = layoutInflater.inflate(R.layout.sheet_item_actions, null)
        dialogView.findViewById<TextView>(R.id.sheet_item_title).text = item.title
        dialogView.findViewById<TextView>(R.id.sheet_item_subtitle).text =
            listOfNotNull(item.category, item.parent, item.dateText)
                .joinToString("  •  ")
                .ifBlank { getString(R.string.sheet_item_actions_subtitle) }

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(dialogView)
        dialog.setCanceledOnTouchOutside(true)
        dialogView.findViewById<Button>(R.id.sheet_edit_button).setOnClickListener {
            dialog.dismiss()
            if (item.type == ManagedItemType.NOTE) {
                showRenameNoteDialog(item.id, item.title)
            } else {
                showEditDialog(item)
            }
        }
        dialogView.findViewById<Button>(R.id.sheet_delete_button).setOnClickListener {
            dialog.dismiss()
            confirmDelete(item)
        }
        dialog.show()
    }

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)

        dialogView.findViewById<TextView>(R.id.settings_summary_text).text = getString(
            R.string.summary_format,
            allItems.count { it.isActive },
            allItems.size
        )
        dialogView.findViewById<TextView>(R.id.settings_permissions_text).text = buildPermissionsText()
        dialogView.findViewById<TextView>(R.id.settings_file_path_text).text =
            TranscriptStore.configuredTranscriptPath(this)

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(dialogView)
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()

        dialogView.findViewById<Button>(R.id.settings_request_permissions_button).setOnClickListener {
            requestNeededPermissions()
        }
        dialogView.findViewById<Button>(R.id.settings_choose_folder_button).setOnClickListener {
            pickFolderLauncher.launch(null)
        }
        dialogView.findViewById<Button>(R.id.settings_start_button).setOnClickListener {
            ContextCompat.startForegroundService(
                this,
                Intent(this, VoiceCaptureService::class.java).apply {
                    action = VoiceCaptureService.ACTION_START
                }
            )
        }
        dialogView.findViewById<Button>(R.id.settings_stop_button).setOnClickListener {
            ContextCompat.startForegroundService(
                this,
                Intent(this, VoiceCaptureService::class.java).apply {
                    action = VoiceCaptureService.ACTION_STOP
                }
            )
        }
        dialogView.findViewById<Button>(R.id.settings_refresh_button).setOnClickListener {
            refreshUi()
            dialog.dismiss()
            showSettingsDialog()
        }
        dialogView.findViewById<Button>(R.id.settings_open_app_settings_button).setOnClickListener {
            refreshOnResume = true
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
            )
        }
    }

    private fun buildPermissionsText(): String {
        val micGranted = hasPermission(Manifest.permission.RECORD_AUDIO)
        val notifGranted = if (Build.VERSION.SDK_INT >= 33) {
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }

        return getString(
            R.string.permissions_status_format,
            if (micGranted) "granted" else "missing",
            if (notifGranted) "granted" else "missing"
        )
    }

    private fun requestNeededPermissions() {
        val missing = mutableListOf<String>()
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            missing.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= 33 && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun showAreaOfFocusDetailDialog(areaName: String) {
        showAreaOfFocusScreen(areaName)
    }

    private fun showAreaOfFocusScreen(areaName: String) {
        val focus = allAreaOfFocusItems.firstOrNull { matchesAreaOfFocus(it.title, areaName) }
        currentAreaFocusName = areaName
        currentAreaFocusItem = focus
        val interests = allItems
            .filter { it.type == ManagedItemType.AREA_OF_INTEREST && matchesAreaOfFocus(it.category, areaName) }
            .sortedBy { it.title.lowercase() }
        val projects = allItems
            .filter { it.type == ManagedItemType.PROJECT && matchesAreaOfFocus(it.category, areaName) }
            .sortedBy { it.title.lowercase() }
        val tasks = allTasks
            .filter { !it.isSomeday && !it.isProjectShell() && taskHasAreaOfFocus(it, areaName) }
            .sortedBy { it.title.lowercase() }
        val someday = allTasks
            .filter { it.isSomeday && !it.isProjectShell() && taskHasAreaOfFocus(it, areaName) }
            .sortedBy { it.title.lowercase() }
        val horizons = allItems
            .filter {
                (it.type == ManagedItemType.GOAL || it.type == ManagedItemType.MILESTONE) &&
                    matchesAreaOfFocus(it.category, areaName)
            }
            .sortedWith(compareBy({ it.type.displayName }, { it.title.lowercase() }))

        areaFocusTitle.text = areaName
        fillMetricStrip(
            areaFocusSummary,
            listOf(
                "Interests" to interests.size,
                "Projects" to projects.size,
                "Actions" to tasks.size,
                "Someday" to someday.size,
                "Horizons" to horizons.size
            )
        )
        val heroImagePath = focus?.imagePath
            ?: projects.firstNotNullOfOrNull { it.imagePath }
            ?: horizons.firstNotNullOfOrNull { it.imagePath }
            ?: interests.firstNotNullOfOrNull { it.imagePath }
        renderAreaFocusHeroImage(areaFocusImage, areaName, heroImagePath)

        fillItemPillSection(
            section = areaFocusInterestsSection,
            container = areaFocusInterestsContainer,
            items = interests,
            closeParent = { hideAreaFocusScreen() }
        )
        fillProjectCarousel(
            section = areaFocusProjectsSection,
            container = areaFocusProjectsContainer,
            projects = projects,
            closeParent = { hideAreaFocusScreen() }
        )
        fillTaskStream(
            section = areaFocusTasksSection,
            container = areaFocusTasksContainer,
            tasks = tasks,
            hideSomedayContext = false
        )
        fillTaskStream(
            section = areaFocusSomedaySection,
            container = areaFocusSomedayContainer,
            tasks = someday,
            hideSomedayContext = true
        )
        fillItemPillSection(
            section = areaFocusHorizonsSection,
            container = areaFocusHorizonsContainer,
            items = horizons,
            closeParent = { hideAreaFocusScreen() }
        )

        areaFocusScreen.visibility = View.VISIBLE
        areaFocusScreen.alpha = 0f
        areaFocusScreen.animate().alpha(1f).setDuration(180L).start()
        areaFocusScroll.scrollTo(0, 0)
    }

    private fun hideAreaFocusScreen() {
        if (areaFocusScreen.visibility != View.VISIBLE) return
        areaFocusScreen.visibility = View.GONE
        currentAreaFocusName = null
        currentAreaFocusItem = null
    }

    private fun renderAreaFocusHeroImage(imageView: ShapeableImageView, areaName: String, imagePath: String?) {
        imageView.background = areaFocusFallbackDrawable(areaName)
        FaceDragonsImageLoader.loadInto(
            imageView = imageView,
            imagePath = imagePath,
            targetSizePx = dp(420)
        ) { bitmap ->
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
            } else {
                imageView.setImageDrawable(null)
                imageView.background = areaFocusFallbackDrawable(areaName)
            }
        }
    }

    private fun areaFocusFallbackDrawable(areaName: String): GradientDrawable {
        val palettes = arrayOf(
            intArrayOf(Color.rgb(10, 22, 30), Color.rgb(38, 31, 62), Color.rgb(104, 74, 142)),
            intArrayOf(Color.rgb(20, 11, 24), Color.rgb(50, 23, 42), Color.rgb(190, 83, 138)),
            intArrayOf(Color.rgb(8, 20, 14), Color.rgb(24, 56, 38), Color.rgb(111, 231, 175)),
            intArrayOf(Color.rgb(28, 19, 6), Color.rgb(69, 45, 18), Color.rgb(244, 197, 111)),
            intArrayOf(Color.rgb(7, 12, 26), Color.rgb(26, 48, 78), Color.rgb(110, 199, 255))
        )
        val colors = palettes[kotlin.math.abs(areaName.hashCode()) % palettes.size]
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            cornerRadius = dp(24).toFloat()
        }
    }

    private fun fillMetricStrip(container: LinearLayout, metrics: List<Pair<String, Int>>) {
        container.removeAllViews()
        metrics.forEach { (label, value) ->
            container.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                background = ContextCompat.getDrawable(context, R.drawable.bg_focus_metric)
                setPadding(dp(9), dp(7), dp(9), dp(7))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(6)
                }
                addView(TextView(context).apply {
                    text = value.toString()
                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    textSize = 17f
                    typeface = Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                })
                addView(TextView(context).apply {
                    text = label
                    setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                    textSize = 9f
                    includeFontPadding = false
                })
            })
        }
    }

    private fun fillItemPillSection(
        section: View,
        container: LinearLayout,
        items: List<ManagedItem>,
        closeParent: () -> Unit
    ) {
        section.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        container.removeAllViews()
        if (items.firstOrNull()?.type == ManagedItemType.AREA_OF_INTEREST) {
            container.addView(ChipGroup(this).apply {
                chipSpacingHorizontal = dp(8)
                chipSpacingVertical = dp(8)
                items.forEach { item ->
                    addView(Chip(context).apply {
                        text = item.title
                        isCheckable = false
                        chipMinHeight = 34f
                        chipCornerRadius = 18f
                        chipStartPadding = 12f
                        chipEndPadding = 12f
                        chipBackgroundColor = ContextCompat.getColorStateList(context, R.color.surface_muted)
                        chipStrokeColor = ContextCompat.getColorStateList(context, R.color.surface_outline)
                        chipStrokeWidth = 1f
                        setTextColor(ContextCompat.getColor(context, if (item.state == ItemState.ACTIVE) selectedMode.accentColorRes else R.color.text_primary))
                        setOnClickListener {
                            val nextState = item.state.next()
                            val updated = item.copy(state = nextState)
                            upsertManagedItemLocally(updated)
                            taskIoExecutor.execute { repository.setState(updated.id, nextState) }
                            currentAreaFocusName?.let { showAreaOfFocusScreen(it) }
                        }
                    })
                }
            })
            return
        }
        items.forEach { item ->
            container.addView(makeAreaFocusTextRow(
                title = item.title,
                subtitle = item.dateText ?: item.notes,
                accent = item.state
            ).apply {
                setOnClickListener {
                    closeParent()
                    when (item.type) {
                        ManagedItemType.MILESTONE -> showMilestoneDetailDialog(item.id)
                        else -> showItemActionsSheet(item)
                    }
                }
            })
        }
    }

    private fun fillProjectCarousel(
        section: View,
        container: LinearLayout,
        projects: List<ManagedItem>,
        closeParent: () -> Unit
    ) {
        section.visibility = if (projects.isEmpty()) View.GONE else View.VISIBLE
        container.removeAllViews()
        projects.forEach { project ->
            container.addView(makeProjectFocusCard(project).apply {
                setOnClickListener {
                    closeParent()
                    showProjectDetailDialog(project.id)
                }
            })
        }
    }

    private fun fillTaskStream(
        section: View,
        container: LinearLayout,
        tasks: List<TaskItem>,
        hideSomedayContext: Boolean
    ) {
        section.visibility = if (tasks.isEmpty()) View.GONE else View.VISIBLE
        container.removeAllViews()
        tasks.take(12).forEach { task ->
            container.addView(makeAreaFocusTextRow(
                title = task.title,
                subtitle = task.contexts
                    .filterNot { hideSomedayContext && it.equals("Someday", ignoreCase = true) }
                    .joinToString("  @", prefix = "@")
                    .takeIf { task.contexts.any { context -> !(hideSomedayContext && context.equals("Someday", ignoreCase = true)) } },
                accent = ItemState.INACTIVE
            ))
        }
    }

    private fun makeProjectFocusCard(project: ManagedItem): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(context, R.drawable.bg_focus_project_card)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(dp(268), dp(184)).apply {
                marginEnd = dp(12)
            }
            val image = ShapeableImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(82))
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = areaFocusFallbackDrawable(project.title)
            }
            FaceDragonsImageLoader.loadInto(image, project.imagePath, dp(180)) { bitmap ->
                if (bitmap != null) image.setImageBitmap(bitmap)
            }
            addView(image)
            addView(TextView(context).apply {
                text = project.title
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 2
                includeFontPadding = false
                setPadding(0, dp(12), 0, 0)
            })
            addView(TextView(context).apply {
                text = listOfNotNull(project.dateText, project.parent, project.notes?.take(44)).joinToString("  /  ")
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                textSize = 12f
                maxLines = 2
                setPadding(0, dp(8), 0, 0)
            })
        }
    }

    private fun makeAreaFocusTextRow(title: String, subtitle: String?, accent: ItemState): TextView {
        val colorRes = when (accent) {
            ItemState.ACTIVE -> selectedMode.accentColorRes
            ItemState.MAINTENANCE -> R.color.state_maintenance
            ItemState.INACTIVE -> R.color.text_primary
        }
        return TextView(this).apply {
            text = if (subtitle.isNullOrBlank()) title else "$title\n$subtitle"
            setTextColor(ContextCompat.getColor(context, colorRes))
            textSize = 15f
            includeFontPadding = false
            setLineSpacing(2f, 1f)
            background = ContextCompat.getDrawable(context, R.drawable.bg_focus_row)
            setPadding(0, dp(8), 0, dp(10))
        }
    }

    private fun showMilestoneDetailDialog(milestoneId: String) {
        var milestone = allItems.firstOrNull {
            it.id == milestoneId && it.type == ManagedItemType.MILESTONE
        } ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_milestone_detail, null)
        val imageView = dialogView.findViewById<ShapeableImageView>(R.id.milestone_detail_image)
        val titleView = dialogView.findViewById<TextView>(R.id.milestone_detail_title)
        val targetView = dialogView.findViewById<TextView>(R.id.milestone_detail_target)
        val notesView = dialogView.findViewById<TextView>(R.id.milestone_detail_notes)
        val stateButton = dialogView.findViewById<MaterialButton>(R.id.milestone_detail_state_button)

        fun render(current: ManagedItem) {
            titleView.text = current.title
            targetView.text = current.dateText
                ?.takeIf { it.isNotBlank() }
                ?: getString(R.string.milestone_no_target)
            notesView.text = current.notes
                ?.takeIf { it.isNotBlank() }
                ?: getString(R.string.milestone_no_notes)
            updateImagePreview(imageView, TextView(this), current.imagePath)
            renderProjectStateButton(stateButton, current.state)
        }

        render(milestone)

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(dialogView)
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()

        stateButton.setOnClickListener {
            milestone = milestone.copy(state = milestone.state.next())
            allItems = allItems.map { existing ->
                if (existing.id == milestone.id) milestone else existing
            }
            render(milestone)
            renderSelectedTab()
            taskIoExecutor.execute {
                repository.setState(milestone.id, milestone.state)
            }
        }
        dialogView.findViewById<Button>(R.id.milestone_detail_edit_button).setOnClickListener {
            dialog.dismiss()
            showEditDialog(milestone)
        }
        dialogView.findViewById<Button>(R.id.milestone_detail_delete_button).setOnClickListener {
            dialog.dismiss()
            confirmDelete(milestone)
        }
    }

    private fun showProjectDetailDialog(projectId: String) {
        val detail = projectRepository.loadProjectDetail(projectId, allTasks) ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_project_detail, null)
        val imageView = dialogView.findViewById<ShapeableImageView>(R.id.project_detail_image)
        val eyebrowView = dialogView.findViewById<TextView>(R.id.project_detail_eyebrow)
        val titleView = dialogView.findViewById<TextView>(R.id.project_detail_title)
        val metaView = dialogView.findViewById<TextView>(R.id.project_detail_meta)
        val stateButton = dialogView.findViewById<MaterialButton>(R.id.project_detail_state_button)
        val whyView = dialogView.findViewById<TextView>(R.id.project_detail_why)
        val notesView = dialogView.findViewById<TextView>(R.id.project_detail_notes)
        val tasksContainer = dialogView.findViewById<android.widget.LinearLayout>(R.id.project_detail_tasks_container)
        var projectState = detail.item.state

        eyebrowView.text = displayProjectShellType(detail.shellType)
        titleView.text = detail.item.title
        metaView.text = listOfNotNull(
            detail.item.category?.takeIf { it.isNotBlank() }?.let { getString(R.string.project_area_of_focus_format, it) },
            detail.relatedTasks.size.takeIf { it > 0 }?.let { "$it related tasks" },
            detail.dueDate?.let { getString(R.string.project_due_date_format, it) },
            detail.slug.takeIf { it.isNotBlank() }?.let { "+$it" }
        )
            .joinToString("  •  ")
        whyView.text = detail.whyBody.ifBlank { getString(R.string.project_no_why) }
        notesView.text = detail.notesBody.ifBlank { getString(R.string.project_no_notes) }
        updateImagePreview(imageView, TextView(this), detail.item.imagePath)
        renderProjectStateButton(stateButton, projectState)

        tasksContainer.removeAllViews()
        if (detail.relatedTasks.isEmpty()) {
            tasksContainer.addView(TextView(this).apply {
                text = getString(R.string.project_no_related_tasks)
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            })
        } else {
            detail.relatedTasks.forEach { task ->
                val row = layoutInflater.inflate(R.layout.item_project_task, tasksContainer, false)
                val checkbox = row.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.project_task_checkbox)
                val taskTitle = row.findViewById<TextView>(R.id.project_task_title)
                taskTitle.text = buildString {
                    append(task.title)
                    val tags = buildList {
                        addAll(task.contexts.map { TaskTags.toDisplayLabel("@", it) })
                        addAll(task.projects.map { TaskTags.toDisplayLabel("+", it) })
                        addAll(task.areas.map { TaskTags.toDisplayLabel("#", it) })
                        task.dueDate?.let { add(getString(R.string.task_due_label, it)) }
                    }
                    if (tags.isNotEmpty()) {
                        append("\n")
                        append(tags.joinToString("  "))
                    }
                }
                checkbox.setOnCheckedChangeListener { _, isChecked ->
                    if (!isChecked) return@setOnCheckedChangeListener
                    checkbox.isEnabled = false
                    row.alpha = 0.45f
                    tasksContainer.removeView(row)
                    if (tasksContainer.childCount == 0) {
                        tasksContainer.addView(TextView(this).apply {
                            text = getString(R.string.project_no_related_tasks)
                            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                        })
                    }
                    allTasks = allTasks.filterNot { it.id == task.id }
                    taskIoExecutor.execute {
                        taskRepository.completeTask(task)
                        runOnUiThread { refreshUi() }
                    }
                }
                tasksContainer.addView(row)
            }
        }

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(dialogView)
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()

        stateButton.setOnClickListener {
            projectState = projectState.next()
            allItems = allItems.map { existing ->
                if (existing.id == projectId) existing.copy(state = projectState) else existing
            }
            renderProjectStateButton(stateButton, projectState)
            renderSelectedTab()
            taskIoExecutor.execute {
                projectRepository.setState(projectId, projectState)
            }
        }
        dialogView.findViewById<Button>(R.id.project_detail_edit_button).setOnClickListener {
            dialog.dismiss()
            showProjectEditDialog(detail.item)
        }
        dialogView.findViewById<Button>(R.id.project_detail_record_note_button).setOnClickListener {
            startProjectVoiceNote(projectId)
            dialog.dismiss()
        }
        dialogView.findViewById<Button>(R.id.project_detail_add_note_button).setOnClickListener {
            dialog.dismiss()
            showTextInputSheet(
                title = getString(R.string.project_add_note_title),
                hint = getString(R.string.project_add_note_hint)
            ) { value ->
                taskIoExecutor.execute {
                    projectRepository.appendNote(projectId, value)
                    runOnUiThread {
                        refreshUi()
                        showProjectDetailDialog(projectId)
                    }
                }
            }
        }
        dialogView.findViewById<Button>(R.id.project_detail_add_task_button).setOnClickListener {
            dialog.dismiss()
            showTaskEditDialog(
                existing = null,
                defaultProject = detail.slug,
                onSaved = { showProjectDetailDialog(projectId) }
            )
        }
        dialogView.findViewById<Button>(R.id.project_detail_voice_task_button).setOnClickListener {
            startProjectVoiceTask(projectId, detail.slug)
            dialog.dismiss()
        }
        dialogView.findViewById<Button>(R.id.project_detail_delete_button).setOnClickListener {
            dialog.dismiss()
            confirmDelete(detail.item)
        }
        dialogView.findViewById<Button>(R.id.project_detail_complete_button).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.project_complete_button)
                .setMessage(R.string.project_complete_message)
                .setPositiveButton(R.string.project_complete_button) { _, _ ->
                    val matchingGuids = detail.relatedTasks.map { it.guid }.toSet() + projectId
                    allTasks = allTasks.filterNot { task ->
                        task.guid in matchingGuids || task.projects.any { it.equals(detail.slug, ignoreCase = true) }
                    }
                    allItems = allItems.filterNot { it.id == projectId }
                    dialog.dismiss()
                    renderSelectedTab()
                    taskIoExecutor.execute {
                        projectRepository.completeProject(projectId)
                        runOnUiThread { refreshUi() }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun startProjectVoiceNote(projectId: String) {
        showProjectSpeechCaptureDialog(
            titleRes = R.string.project_record_voice_note,
            emptyRes = R.string.project_voice_note_empty,
            onSave = { finalText ->
                taskIoExecutor.execute {
                    projectRepository.appendVoiceNote(projectId, finalText)
                    runOnUiThread {
                        refreshUi()
                        showProjectDetailDialog(projectId)
                    }
                }
            }
        )
    }

    private fun startProjectVoiceTask(projectId: String, projectSlug: String) {
        showProjectSpeechCaptureDialog(
            titleRes = R.string.project_voice_task_title,
            emptyRes = R.string.project_voice_task_empty,
            onSave = { finalText ->
                taskIoExecutor.execute {
                    taskRepository.addTask(
                        title = finalText,
                        projects = listOf(projectSlug)
                    )
                    runOnUiThread {
                        refreshUi()
                        showProjectDetailDialog(projectId)
                    }
                }
            }
        )
    }

    private fun showProjectSpeechCaptureDialog(
        titleRes: Int,
        emptyRes: Int,
        onSave: (String) -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.project_speech_unavailable)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_project_voice_note, null)
        val titleView = dialogView.findViewById<TextView>(R.id.project_voice_note_title)
        val statusView = dialogView.findViewById<TextView>(R.id.project_voice_note_status)
        val previewView = dialogView.findViewById<TextView>(R.id.project_voice_note_preview)
        var transcript = ""
        var shouldKeepListening = true
        var isFinishing = false

        titleView.text = getString(titleRes)
        statusView.text = getString(R.string.project_voice_note_listening)
        previewView.text = getString(emptyRes)

        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        fun renderPreview() {
            previewView.text = transcript.ifBlank { getString(emptyRes) }
        }

        fun beginListening() {
            if (!shouldKeepListening) return
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            runCatching { recognizer.startListening(intent) }.onFailure {
                if (shouldKeepListening) {
                    mainHandler.postDelayed({ beginListening() }, 250L)
                }
            }
        }

        fun finishAndSave() {
            if (isFinishing) return
            isFinishing = true
            shouldKeepListening = false
            runCatching { recognizer.stopListening() }
            runCatching { recognizer.cancel() }
            recognizer.destroy()
            val finalText = transcript.trim()
            if (finalText.isNotBlank()) {
                onSave(finalText)
            }
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                statusView.text = getString(R.string.project_voice_note_listening)
            }

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                if (shouldKeepListening) {
                    statusView.text = getString(R.string.project_voice_note_idle)
                }
            }

            override fun onError(error: Int) {
                if (shouldKeepListening) {
                    mainHandler.postDelayed({ beginListening() }, 250L)
                }
            }

            override fun onResults(results: Bundle?) {
                val bestMatch = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (bestMatch.isNotBlank()) {
                    transcript = mergeSegments(transcript, bestMatch)
                    renderPreview()
                }
                if (shouldKeepListening) {
                    mainHandler.postDelayed({ beginListening() }, 150L)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(dialogView)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnShowListener {
            dialog.behavior.skipCollapsed = true
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            beginListening()
        }
        dialog.setOnDismissListener {
            shouldKeepListening = false
            if (!isFinishing) {
                runCatching { recognizer.cancel() }
                recognizer.destroy()
            }
        }
        dialogView.findViewById<Button>(R.id.project_voice_note_stop_button)?.setOnClickListener {
            finishAndSave()
            dialog.dismiss()
        }
        dialogView.findViewById<Button>(R.id.project_voice_note_cancel_button)?.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showProjectEditDialog(existing: ManagedItem?) {
        val existingDetail = existing?.let { projectRepository.loadProjectDetail(it.id, allTasks) }
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_project, null)

        val titleInput = dialogView.findViewById<EditText>(R.id.edit_project_title)
        val shellTypeInput = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_project_shell_type)
        val areaOfFocusInput = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_project_area_of_focus)
        val dueDateInput = dialogView.findViewById<EditText>(R.id.edit_project_due_date)
        val imagePathText = dialogView.findViewById<TextView>(R.id.edit_project_image_path)
        val imagePreview = dialogView.findViewById<ShapeableImageView>(R.id.edit_project_image_preview)
        val pickImageButton = dialogView.findViewById<Button>(R.id.edit_project_pick_image_button)
        val clearImageButton = dialogView.findViewById<Button>(R.id.edit_project_clear_image_button)
        val whyInput = dialogView.findViewById<EditText>(R.id.edit_project_why)
        val notesInput = dialogView.findViewById<EditText>(R.id.edit_project_notes)
        var selectedImagePath: String? = existing?.imagePath

        titleInput.setText(existing?.title.orEmpty())
        shellTypeInput.keyListener = null
        shellTypeInput.isFocusable = false
        shellTypeInput.isCursorVisible = false
        shellTypeInput.setText(
            displayProjectShellType(existingDetail?.shellType ?: PROJECT_SHELL_PROJECT),
            false
        )
        shellTypeInput.setOnClickListener {
            showProjectShellTypePicker(existingDetail?.shellType ?: PROJECT_SHELL_PROJECT) { selected ->
                shellTypeInput.setText(displayProjectShellType(selected), false)
                shellTypeInput.tag = selected
            }
        }
        shellTypeInput.tag = existingDetail?.shellType ?: PROJECT_SHELL_PROJECT
        areaOfFocusInput.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                allAreasOfFocus
            )
        )
        areaOfFocusInput.keyListener = null
        areaOfFocusInput.isFocusable = false
        areaOfFocusInput.isCursorVisible = false
        areaOfFocusInput.setText(existingDetail?.item?.category ?: existing?.category.orEmpty(), false)
        areaOfFocusInput.setOnClickListener {
            showAreaOfFocusPicker(areaOfFocusInput.text?.toString()) { value ->
                areaOfFocusInput.setText(value.orEmpty(), false)
            }
        }
        dueDateInput.setText(existingDetail?.dueDate ?: existing?.dateText.orEmpty())
        whyInput.setText(existingDetail?.whyBody.orEmpty())
        notesInput.setText(existingDetail?.notesBody ?: existing?.notes.orEmpty())
        updateImagePreview(imagePreview, imagePathText, selectedImagePath)

        dueDateInput.setOnClickListener {
            showDueDateDialog(dueDateInput.text.toString().trim().ifBlank { null }) { dueDate ->
                dueDateInput.setText(dueDate)
            }
        }

        pickImageButton.setOnClickListener {
            pendingImageSelection = { newImagePath ->
                if (newImagePath != null) {
                    selectedImagePath = newImagePath
                    updateImagePreview(imagePreview, imagePathText, selectedImagePath)
                }
            }
            pickImageLauncher.launch(arrayOf("image/*"))
        }

        clearImageButton.setOnClickListener {
            selectedImagePath = null
            updateImagePreview(imagePreview, imagePathText, selectedImagePath)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) R.string.dialog_add_title else R.string.dialog_edit_title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val title = titleInput.text.toString().trim()
                if (title.isBlank()) return@setPositiveButton
                val shellType = (shellTypeInput.tag as? String ?: PROJECT_SHELL_PROJECT)
                val areaOfFocus = areaOfFocusInput.text.toString().trim().ifBlank { null }
                val why = whyInput.text.toString().trim().ifBlank { null }
                val notes = notesInput.text.toString().trim().ifBlank { null }
                val dueDate = dueDateInput.text.toString().trim().ifBlank { null }

                if (existing == null) {
                    projectRepository.createProject(
                        title = title,
                        shellType = shellType,
                        areaOfFocus = areaOfFocus,
                        imagePath = selectedImagePath,
                        whyBody = why,
                        notesBody = notes,
                        dueDate = dueDate,
                        state = ItemState.ACTIVE
                    )
                } else {
                    projectRepository.updateProject(
                        projectId = existing.id,
                        title = title,
                        shellType = shellType,
                        areaOfFocus = areaOfFocus,
                        imagePath = selectedImagePath,
                        whyBody = why,
                        notesBody = notes,
                        dueDate = dueDate,
                        state = existing.state
                    )
                }
                refreshUi()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEditDialog(existing: ManagedItem?) {
        val targetType = existing?.type ?: selectedTab.editableType ?: return
        if (targetType == ManagedItemType.PROJECT) {
            showProjectEditDialog(existing)
            return
        }
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_item, null)

        val titleInput = dialogView.findViewById<EditText>(R.id.edit_item_title)
        val categoryInput = dialogView.findViewById<EditText>(R.id.edit_item_category)
        val categoryLabel = dialogView.findViewById<TextView>(R.id.edit_item_category_label)
        val dateLabel = dialogView.findViewById<TextView>(R.id.edit_item_date_label)
        val dateInput = dialogView.findViewById<EditText>(R.id.edit_item_date)
        val imagePathText = dialogView.findViewById<TextView>(R.id.edit_item_image_path)
        val imagePreview = dialogView.findViewById<ShapeableImageView>(R.id.edit_item_image_preview)
        val pickImageButton = dialogView.findViewById<Button>(R.id.edit_item_pick_image_button)
        val clearImageButton = dialogView.findViewById<Button>(R.id.edit_item_clear_image_button)
        val notesInput = dialogView.findViewById<EditText>(R.id.edit_item_notes)
        var selectedImagePath: String? = existing?.imagePath

        titleInput.setText(existing?.title.orEmpty())
        categoryInput.setText(existing?.category ?: selectedTab.defaultCategory.orEmpty())
        dateInput.setText(existing?.dateText.orEmpty())
        notesInput.setText(existing?.notes.orEmpty())
        updateImagePreview(imagePreview, imagePathText, selectedImagePath)

        val usesAreaOfFocus = targetType == ManagedItemType.AREA_OF_INTEREST ||
            targetType == ManagedItemType.GOAL ||
            targetType == ManagedItemType.MILESTONE

        if (usesAreaOfFocus) {
            categoryLabel.setText(R.string.project_area_of_focus_label)
            categoryInput.hint = getString(R.string.project_area_of_focus_hint)
            categoryInput.keyListener = null
            categoryInput.isFocusable = false
            categoryInput.isCursorVisible = false
            categoryInput.setOnClickListener {
                showAreaOfFocusPicker(categoryInput.text?.toString()) { value ->
                    categoryInput.setText(value.orEmpty())
                }
            }
        }

        if (targetType == ManagedItemType.MILESTONE) {
            dateLabel.setText(R.string.milestone_target_label)
            dateInput.hint = getString(R.string.milestone_target_hint)
            notesInput.hint = getString(R.string.milestone_notes_hint)
        }

        if (targetType == ManagedItemType.VISION) {
            categoryLabel.visibility = View.GONE
            categoryInput.visibility = View.GONE
            dateLabel.visibility = View.GONE
            dateInput.visibility = View.GONE
        }

        pickImageButton.setOnClickListener {
            pendingImageSelection = { newImagePath ->
                if (newImagePath != null) {
                    selectedImagePath = newImagePath
                    updateImagePreview(imagePreview, imagePathText, selectedImagePath)
                }
            }
            pickImageLauncher.launch(arrayOf("image/*"))
        }

        clearImageButton.setOnClickListener {
            selectedImagePath = null
            updateImagePreview(imagePreview, imagePathText, selectedImagePath)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) R.string.dialog_add_title else R.string.dialog_edit_title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val title = titleInput.text.toString().trim()
                if (title.isBlank()) return@setPositiveButton

                val item = ManagedItem(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    title = title,
                    type = targetType,
                    category = if (targetType == ManagedItemType.VISION) {
                        null
                    } else {
                        categoryInput.text.toString().trim().ifBlank { null }
                    },
                    parent = null,
                    state = existing?.state ?: ItemState.ACTIVE,
                    imagePath = selectedImagePath,
                    notes = notesInput.text.toString().trim().ifBlank { null },
                    dateText = if (targetType == ManagedItemType.VISION) {
                        null
                    } else {
                        dateInput.text.toString().trim().ifBlank { null }
                    },
                    isEditable = true,
                    sourceName = existing?.sourceName ?: "App Data"
                )

                upsertManagedItemLocally(item)
                renderSelectedTab()
                taskIoExecutor.execute {
                    if (existing == null) {
                        repository.addItem(item)
                    } else {
                        repository.updateItem(item)
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun upsertManagedItemLocally(item: ManagedItem) {
        allItems = (allItems.filterNot { it.id == item.id } + item)
            .sortedWith(compareBy({ it.type.displayName }, { it.category ?: "" }, { it.title }))
    }

    private fun mergeSegments(existing: String, incoming: String): String {
        val normalizedIncoming = normalizeText(incoming)
        if (normalizedIncoming.isEmpty()) return existing

        val normalizedExisting = normalizeText(existing)
        if (normalizedExisting.isEmpty()) return normalizedIncoming
        if (normalizedIncoming.equals(normalizedExisting, ignoreCase = true)) return normalizedExisting
        if (normalizedIncoming.startsWith(normalizedExisting, ignoreCase = true)) return normalizedIncoming
        if (normalizedExisting.endsWith(normalizedIncoming, ignoreCase = true)) return normalizedExisting

        val existingWords = normalizedExisting.split(" ")
        val incomingWords = normalizedIncoming.split(" ")
        val maxOverlap = minOf(existingWords.size, incomingWords.size)

        for (overlap in maxOverlap downTo 1) {
            val existingSuffix = existingWords.takeLast(overlap)
            val incomingPrefix = incomingWords.take(overlap)
            if (existingSuffix.joinToString(" ").equals(incomingPrefix.joinToString(" "), ignoreCase = true)) {
                val remainder = incomingWords.drop(overlap).joinToString(" ")
                return if (remainder.isBlank()) normalizedExisting else "$normalizedExisting $remainder"
            }
        }

        return "$normalizedExisting $normalizedIncoming"
    }

    private fun normalizeText(text: String): String {
        return text.trim().replace(Regex("\\s+"), " ")
    }

    private fun updateImagePreview(
        preview: ImageView,
        pathText: TextView,
        imagePath: String?
    ) {
        val bitmap = FaceDragonsImageLoader.loadBitmapSync(
            context = this,
            imagePath = imagePath,
            targetSizePx = dp(160)
        )
        if (bitmap != null) {
            preview.setImageBitmap(bitmap)
            preview.imageTintList = null
            preview.setPadding(0, 0, 0, 0)
            pathText.text = imagePath
        } else {
            val padding = (18 * resources.displayMetrics.density).toInt()
            preview.setImageResource(android.R.drawable.ic_menu_gallery)
            preview.imageTintList = ContextCompat.getColorStateList(this, R.color.text_secondary)
            preview.setPadding(padding, padding, padding, padding)
            pathText.text = getString(R.string.dialog_image_path_hint)
        }
    }

    private fun confirmDelete(item: ManagedItem) {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.dialog_delete_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                when (item.type) {
                    ManagedItemType.PROJECT -> {
                        projectRepository.deleteProject(item.id)
                        refreshUi()
                    }
                    ManagedItemType.NOTE -> {
                        noteRepository.deleteNote(item.id)
                        refreshNoteItems()
                        renderSelectedTab()
                    }
                    else -> {
                        allItems = allItems.filterNot { it.id == item.id }
                        renderSelectedTab()
                        taskIoExecutor.execute {
                            repository.deleteItem(item)
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private val MainTab.supportsEditing: Boolean
        get() = this == MainTab.TASKS || this == MainTab.SOMEDAY || this == MainTab.NOTES || editableType != null

    private val MainTab.editableType: ManagedItemType?
        get() = when (this) {
            MainTab.ACTIVE -> null
            MainTab.TASKS -> null
            MainTab.NOTES -> null
            MainTab.MILESTONES -> ManagedItemType.MILESTONE
            MainTab.SOMEDAY -> null
            MainTab.COMPLETED -> null
            MainTab.PROJECTS -> ManagedItemType.PROJECT
            MainTab.AREAS_OF_INTEREST -> ManagedItemType.AREA_OF_INTEREST
            MainTab.GOALS -> ManagedItemType.GOAL
            MainTab.VISION -> ManagedItemType.VISION
        }

    private val MainTab.defaultCategory: String?
        get() = when (this) {
            MainTab.GOALS -> null
            MainTab.NOTES -> null
            MainTab.MILESTONES -> null
            MainTab.VISION -> null
            MainTab.COMPLETED -> null
            else -> null
        }

    private fun renderProjectStateButton(button: MaterialButton, state: ItemState) {
        val label = when (state) {
            ItemState.ACTIVE -> getString(R.string.item_state_active)
            ItemState.MAINTENANCE -> getString(R.string.item_state_maintenance)
            ItemState.INACTIVE -> getString(R.string.item_state_inactive)
        }
        val colorRes = when (state) {
            ItemState.ACTIVE -> selectedMode.accentColorRes
            ItemState.MAINTENANCE -> R.color.state_maintenance
            ItemState.INACTIVE -> R.color.text_secondary
        }
        button.text = getString(R.string.project_state_button) + ": " + label
        button.setTextColor(ContextCompat.getColor(this, colorRes))
        button.strokeColor = ContextCompat.getColorStateList(this, colorRes)
    }

    private fun matchesAreaOfFocus(value: String?, target: String): Boolean {
        return value != null && TaskTags.toCanonicalKey(value) == TaskTags.toCanonicalKey(target)
    }

    private fun matchesAnyAreaOfFocus(value: String?, targets: Set<String>): Boolean {
        return value != null && TaskTags.toCanonicalKey(value) in targets
    }

    private fun taskHasAreaOfFocus(task: TaskItem, areaName: String): Boolean {
        val target = TaskTags.toCanonicalKey(areaName)
        return task.areas.any { TaskTags.toCanonicalKey(it) == target }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showProjectShellTypePicker(
        currentValue: String?,
        onPicked: (String) -> Unit
    ) {
        val current = currentValue?.trim().orEmpty()
        val options = PROJECT_SHELL_OPTIONS
        showOptionSheet(
            title = getString(R.string.project_type_label),
            entries = options.map { option ->
                val label = displayProjectShellType(option)
                val decorated = if (
                    option.equals(current, ignoreCase = true) ||
                    label.equals(displayProjectShellType(current), ignoreCase = true)
                ) {
                    "$label  •"
                } else {
                    label
                }
                PickerSheetEntry.Option(decorated) { onPicked(option) }
            }
        )
    }

    private fun showOptionSheet(title: String, entries: List<PickerSheetEntry>) {
        val dialogView = layoutInflater.inflate(R.layout.sheet_option_picker, null)
        val titleView = dialogView.findViewById<TextView>(R.id.option_sheet_title)
        val container = dialogView.findViewById<LinearLayout>(R.id.option_sheet_container)
        titleView.text = title
        val dialog = BottomSheetDialog(this)

        entries.forEach { entry ->
            when (entry) {
                is PickerSheetEntry.Section -> {
                    container.addView(TextView(this).apply {
                        text = entry.title.uppercase()
                        setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                        textSize = 11f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        if (container.childCount > 0) {
                            setPadding(0, (18 * resources.displayMetrics.density).toInt(), 0, (8 * resources.displayMetrics.density).toInt())
                        } else {
                            setPadding(0, 0, 0, (8 * resources.displayMetrics.density).toInt())
                        }
                    })
                }

                is PickerSheetEntry.Option -> {
                    container.addView(TextView(this).apply {
                        text = entry.title
                        setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                        textSize = 17f
                        background = ContextCompat.getDrawable(context, R.drawable.bg_picker_option)
                        setPadding(
                            (16 * resources.displayMetrics.density).toInt(),
                            (14 * resources.displayMetrics.density).toInt(),
                            (16 * resources.displayMetrics.density).toInt(),
                            (14 * resources.displayMetrics.density).toInt()
                        )
                        if (container.childCount > 0) {
                            (layoutParams as? LinearLayout.LayoutParams ?: LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )).also { params ->
                                params.topMargin = (8 * resources.displayMetrics.density).toInt()
                                layoutParams = params
                            }
                        }
                        setOnClickListener {
                            dialog.dismiss()
                            entry.onClick()
                        }
                    })
                }
            }
        }

        dialog.setContentView(dialogView)
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
    }

    private fun showTextInputSheet(
        title: String,
        hint: String,
        onConfirm: (String) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.sheet_text_input, null)
        val titleView = dialogView.findViewById<TextView>(R.id.text_input_sheet_title)
        val input = dialogView.findViewById<EditText>(R.id.text_input_sheet_field)
        titleView.text = title
        input.hint = hint

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(dialogView)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnShowListener {
            dialog.behavior.skipCollapsed = true
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        dialogView.findViewById<MaterialButton>(R.id.text_input_sheet_cancel).setOnClickListener {
            dialog.dismiss()
        }
        dialogView.findViewById<MaterialButton>(R.id.text_input_sheet_confirm).setOnClickListener {
            val value = input.text?.toString()?.trim().orEmpty()
            if (value.isBlank()) return@setOnClickListener
            onConfirm(value)
            dialog.dismiss()
        }
        dialog.show()
        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        input.post {
            input.requestFocus()
            input.setSelection(input.text?.length ?: 0)
            getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun showCreateNoteDialog() {
        showTextInputSheet(
            title = getString(R.string.note_create_title),
            hint = getString(R.string.note_title_hint)
        ) { value ->
            val note = noteRepository.createNote(value)
            refreshNoteItems()
            renderSelectedTab()
            showNoteDetailDialog(note.id)
        }
    }

    private fun showRenameNoteDialog(noteId: String, currentTitle: String) {
        showTextInputSheet(
            title = getString(R.string.note_rename_title),
            hint = currentTitle
        ) { value ->
            noteRepository.renameNote(noteId, value)
            refreshNoteItems()
            renderSelectedTab()
        }
    }

    private fun refreshNoteItems() {
        val noteItems = noteRepository.loadNoteItems()
        allItems = allItems.filterNot { it.type == ManagedItemType.NOTE } + noteItems
    }

    private fun showNoteDetailDialog(noteId: String) {
        var currentNote = noteRepository.loadNote(noteId) ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_note_detail, null)
        val titleView = dialogView.findViewById<TextView>(R.id.note_detail_title)
        val statusView = dialogView.findViewById<TextView>(R.id.note_detail_status)
        val recycler = dialogView.findViewById<RecyclerView>(R.id.note_detail_recycler)
        val input = dialogView.findViewById<EditText>(R.id.note_detail_input)
        val addButton = dialogView.findViewById<ImageButton>(R.id.note_detail_add_text)
        val voiceFab = dialogView.findViewById<FloatingActionButton>(R.id.note_detail_voice_fab)
        lateinit var adapter: NoteLineAdapter
        adapter = NoteLineAdapter(
            onCheckedChanged = { line, isChecked ->
                noteRepository.setLineChecked(noteId, line.id, isChecked)?.let { updated ->
                    currentNote = updated
                    renderNoteDetail(currentNote, titleView, statusView, adapter)
                    refreshNoteItems()
                    if (selectedTab == MainTab.NOTES) renderSelectedTab()
                }
            },
            onEdit = { line ->
                showTextInputSheet(
                    title = getString(R.string.note_edit_line_title),
                    hint = line.text
                ) { value ->
                    noteRepository.updateLine(noteId, line.id, value)?.let { updated ->
                        currentNote = updated
                        renderNoteDetail(currentNote, titleView, statusView, adapter)
                        refreshNoteItems()
                        if (selectedTab == MainTab.NOTES) renderSelectedTab()
                    }
                }
            },
            onDelete = { line ->
                MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.note_delete_line_message)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        noteRepository.deleteLine(noteId, line.id)?.let { updated ->
                            currentNote = updated
                            renderNoteDetail(currentNote, titleView, statusView, adapter)
                            refreshNoteItems()
                            if (selectedTab == MainTab.NOTES) renderSelectedTab()
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        )
        recycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recycler.adapter = adapter
        recycler.itemAnimator = DefaultItemAnimator()

        var transcript = ""
        var shouldKeepListening = false
        var isRecording = false
        var recognizer: SpeechRecognizer? = null

        fun saveTextLine() {
            val value = input.text?.toString()?.trim().orEmpty()
            if (value.isBlank()) return
            noteRepository.addLine(noteId, value)?.let { updated ->
                currentNote = updated
                input.text?.clear()
                renderNoteDetail(currentNote, titleView, statusView, adapter)
                refreshNoteItems()
                if (selectedTab == MainTab.NOTES) renderSelectedTab()
                recycler.scrollToPosition((currentNote.lines.size - 1).coerceAtLeast(0))
            }
        }

        fun releaseRecognizer() {
            shouldKeepListening = false
            runCatching { recognizer?.stopListening() }
            runCatching { recognizer?.cancel() }
            runCatching { recognizer?.destroy() }
            recognizer = null
            isRecording = false
            transcript = ""
            voiceFab.setImageResource(R.drawable.ic_mic)
            renderNoteDetail(currentNote, titleView, statusView, adapter)
        }

        fun beginListening() {
            if (!shouldKeepListening) return
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            runCatching { recognizer?.startListening(intent) }.onFailure {
                if (shouldKeepListening) {
                    mainHandler.postDelayed({ beginListening() }, 250L)
                }
            }
        }

        fun stopVoiceCapture(save: Boolean) {
            val finalText = transcript.trim()
            releaseRecognizer()
            if (save && finalText.isNotBlank()) {
                noteRepository.addLine(noteId, finalText)?.let { updated ->
                    currentNote = updated
                    renderNoteDetail(currentNote, titleView, statusView, adapter)
                    refreshNoteItems()
                    if (selectedTab == MainTab.NOTES) renderSelectedTab()
                    recycler.scrollToPosition((currentNote.lines.size - 1).coerceAtLeast(0))
                }
            }
        }

        fun startVoiceCapture() {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.project_speech_unavailable)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return
            }
            transcript = ""
            shouldKeepListening = true
            isRecording = true
            voiceFab.setImageResource(android.R.drawable.ic_media_pause)
            renderNoteDetail(currentNote, titleView, statusView, adapter, isRecording = true)
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        statusView.text = getString(R.string.note_recording_status)
                    }
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onError(error: Int) {
                        if (shouldKeepListening) {
                            mainHandler.postDelayed({ beginListening() }, 250L)
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        val bestMatch = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                        if (bestMatch.isNotBlank()) {
                            transcript = mergeSegments(transcript, bestMatch)
                        }
                        if (shouldKeepListening) {
                            mainHandler.postDelayed({ beginListening() }, 150L)
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
            }
            beginListening()
        }

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(dialogView)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.layoutParams = bottomSheet?.layoutParams?.apply {
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
            dialog.behavior.skipCollapsed = true
            dialog.behavior.peekHeight = resources.displayMetrics.heightPixels
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        renderNoteDetail(currentNote, titleView, statusView, adapter)

        dialogView.findViewById<ImageButton>(R.id.note_detail_close).setOnClickListener {
            dialog.dismiss()
        }
        addButton.setOnClickListener { saveTextLine() }
        input.setOnEditorActionListener { _, _, _ ->
            saveTextLine()
            true
        }
        voiceFab.setOnClickListener {
            if (isRecording) {
                stopVoiceCapture(save = true)
            } else {
                startVoiceCapture()
            }
        }
        dialog.setOnDismissListener {
            val draft = input.text?.toString()?.trim().orEmpty()
            if (draft.isNotBlank()) {
                noteRepository.addLine(noteId, draft)?.let { updated ->
                    currentNote = updated
                }
            }
            releaseRecognizer()
            refreshNoteItems()
            if (selectedTab == MainTab.NOTES) renderSelectedTab()
        }
        dialog.show()
    }

    private fun renderNoteDetail(
        note: NoteDetail,
        titleView: TextView,
        statusView: TextView,
        adapter: NoteLineAdapter,
        isRecording: Boolean = false
    ) {
        titleView.text = note.title
        val openCount = note.lines.count { !it.isChecked }
        statusView.text = if (isRecording) {
            getString(R.string.note_recording_status)
        } else if (note.lines.isEmpty()) {
            getString(R.string.note_empty_status)
        } else {
            getString(R.string.note_count_status, openCount, note.lines.size)
        }
        adapter.submitList(note.lines)
    }

    private fun displayProjectShellType(shellType: String): String {
        return when (shellType.trim().lowercase()) {
            "workproject" -> "Work Project"
            else -> "Project"
        }
    }

    private fun TaskItem.isProjectShell(): Boolean {
        return contexts.any { context ->
            when (context.trim().lowercase()) {
                "project", "workproject" -> true
                else -> false
            }
        }
    }

    private fun String.isProjectShellContext(): Boolean {
        return when (trim().lowercase()) {
            "project", "workproject" -> true
            else -> false
        }
    }

}
