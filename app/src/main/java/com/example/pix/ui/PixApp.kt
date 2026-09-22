package com.example.pix.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.example.pix.R
import com.example.pix.data.*
import com.example.pix.ui.theme.ListColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixApp(model: TasksViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    val content by model.content.collectAsStateWithLifecycle()
    val filter by model.filter.collectAsStateWithLifecycle()
    val manualOrder by model.manualOrder.collectAsStateWithLifecycle()
    var taskAction by remember { mutableStateOf<TaskActionSelection?>(null) }
    val dimmedLists by model.dimmedLists.collectAsStateWithLifecycle()
    val now by model.now.collectAsStateWithLifecycle()
    val calendarDay by model.calendarDate.collectAsStateWithLifecycle()
    val lists by model.lists.collectAsStateWithLifecycle()
    val tags by model.tags.collectAsStateWithLifecycle()
    val draft by model.draft.collectAsStateWithLifecycle()
    var quick by rememberSaveable { mutableStateOf(false) }
    var matrixOptions by remember { mutableStateOf(false) }
    var quickQuadrant by remember { mutableStateOf<Int?>(null) }
    var quickMinute by remember { mutableStateOf<Int?>(null) }
    val matrixConfig by model.matrixConfig.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val error = stringResource(R.string.error)
    val completed = stringResource(R.string.completed)
    val undo = stringResource(R.string.undo)
    LaunchedEffect(model, error) { model.errors.collect { snackbar.showSnackbar(error) } }
    fun navigate(destination: String) {
        model.search.value = ""
        nav.navigate(destination) {
            popUpTo(nav.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    fun choose(value: TaskFilter) {
        model.filter.value = value.copy(showCompleted = true)
        navigate("home")
        scope.launch { drawer.close() }
    }
    fun toggle(task: TaskEntity) {
        model.complete(
            task,
            done = {
                if (!task.isCompleted)
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        if (
                            snackbar.showSnackbar(
                                completed,
                                undo,
                                duration = SnackbarDuration.Short,
                            ) == SnackbarResult.ActionPerformed
                        )
                            model.complete(task, false)
                    }
            },
        )
    }
    val modes =
        listOf(
            "ALL" to R.string.all,
            "TODAY" to R.string.today,
            "TOMORROW" to R.string.tomorrow,
            "WEEK" to R.string.week,
            "OVERDUE" to R.string.overdue,
        )
    val homeTitle =
        lists.find { it.list.id == filter.listId }?.list?.let { it.icon + " " + it.name }
            ?: tags.find { it.tag.id == filter.tagId }?.tag?.name
            ?: stringResource(modes.find { it.first == filter.mode }?.second ?: R.string.all)
    val title =
        when (route) {
            "home" -> homeTitle
            "calendar" -> stringResource(R.string.calendar)
            "organize" -> stringResource(R.string.organize)
            "matrix" -> stringResource(R.string.matrix)
            "settings" -> stringResource(R.string.settings)
            else -> stringResource(R.string.search)
        }
    CompositionLocalProvider(
        LocalTaskActions provides
            { detail, action ->
                taskAction = TaskActionSelection(detail, action)
            }
    ) {
        ModalNavigationDrawer(
            drawerState = drawer,
            gesturesEnabled = draft == null && route != "search",
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.widthIn(max = 340.dp),
                    drawerContainerColor = MaterialTheme.colorScheme.surface,
                ) {
                    Column(
                        Modifier.fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Row(
                            Modifier.padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(48.dp)
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                PixIcon(PixSymbol.TASKS, tint = MaterialTheme.colorScheme.primary)
                            }
                            BrandWordmark(Modifier.weight(1f).padding(start = 12.dp))
                            IconButton(
                                onClick = {
                                    navigate("search")
                                    model.filter.value =
                                        TaskFilter(mode = "ALL", showCompleted = true)
                                    scope.launch { drawer.close() }
                                }
                            ) {
                                PixIcon(PixSymbol.SEARCH, stringResource(R.string.search))
                            }
                            IconButton(
                                onClick = {
                                    navigate("settings")
                                    scope.launch { drawer.close() }
                                }
                            ) {
                                PixIcon(PixSymbol.SETTINGS, stringResource(R.string.settings))
                            }
                        }
                        modes.forEach { (mode, label) ->
                            NavigationDrawerItem(
                                label = { Text(stringResource(label)) },
                                selected =
                                    route == "home" &&
                                        filter.mode == mode &&
                                        filter.listId == null &&
                                        filter.tagId == null,
                                icon = {
                                    PixIcon(
                                        if (mode == "TODAY") PixSymbol.CALENDAR
                                        else if (mode == "OVERDUE") PixSymbol.CLOCK
                                        else PixSymbol.TASKS,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                badge = {
                                    if (mode == "ALL")
                                        Text(lists.sumOf { it.activeCount }.toString())
                                },
                                onClick = { choose(TaskFilter(mode = mode)) },
                            )
                        }
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.inbox)) },
                            selected = filter.listId == INBOX_ID && route == "home",
                            icon = {
                                PixIcon(PixSymbol.INBOX, tint = MaterialTheme.colorScheme.primary)
                            },
                            onClick = { choose(TaskFilter(mode = "ALL", listId = INBOX_ID)) },
                        )
                        HorizontalDivider(Modifier.padding(vertical = 16.dp))
                        lists
                            .filter { it.list.id != INBOX_ID }
                            .forEach { row ->
                                NavigationDrawerItem(
                                    label = { Text(row.list.name) },
                                    selected = filter.listId == row.list.id && route == "home",
                                    icon = {
                                        Text(
                                            row.list.icon,
                                            style = MaterialTheme.typography.titleLarge,
                                        )
                                    },
                                    badge = { Text(row.activeCount.toString()) },
                                    onClick = {
                                        choose(TaskFilter(mode = "ALL", listId = row.list.id))
                                    },
                                )
                            }
                        if (tags.isNotEmpty()) {
                            HorizontalDivider(Modifier.padding(vertical = 16.dp))
                            Text(
                                stringResource(R.string.tags),
                                Modifier.padding(12.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        tags.forEach { row ->
                            NavigationDrawerItem(
                                label = { Text("#${row.tag.name}") },
                                selected = filter.tagId == row.tag.id && route == "home",
                                icon = {
                                    PixIcon(PixSymbol.TAG, tint = ListColors[row.tag.color.mod(12)])
                                },
                                badge = { Text(row.activeCount.toString()) },
                                onClick = { choose(TaskFilter(mode = "ALL", tagId = row.tag.id)) },
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        TextButton(
                            onClick = {
                                navigate("organize")
                                scope.launch { drawer.close() }
                            }
                        ) {
                            PixIcon(PixSymbol.PLUS)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.manage_lists))
                        }
                    }
                }
            },
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                title,
                                style =
                                    if (route == "matrix") MaterialTheme.typography.titleLarge
                                    else MaterialTheme.typography.headlineSmall,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        },
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            ),
                        navigationIcon = {
                            if (route != "settings")
                                IconButton(
                                    onClick = {
                                        if (route == "search") {
                                            model.search.value = ""
                                            nav.popBackStack()
                                        } else scope.launch { drawer.open() }
                                    }
                                ) {
                                    PixIcon(
                                        if (route == "search") PixSymbol.BACK else PixSymbol.MENU,
                                        stringResource(
                                            if (route == "search") R.string.back
                                            else R.string.pix_navigation_menu
                                        ),
                                    )
                                }
                        },
                        actions = {
                            if (route == "matrix")
                                IconButton(onClick = { matrixOptions = true }) {
                                    PixIcon(PixSymbol.MORE, stringResource(R.string.matrix_options))
                                }
                            if (route == "home")
                                Box {
                                    IconButton(onClick = { menu = true }) {
                                        PixIcon(
                                            PixSymbol.MORE,
                                            stringResource(R.string.more_actions),
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = menu,
                                        onDismissRequest = { menu = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    stringResource(
                                                        if (manualOrder) R.string.sort_automatic
                                                        else R.string.sort_manual
                                                    )
                                                )
                                            },
                                            onClick = {
                                                model.setManualOrder(!manualOrder)
                                                menu = false
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.search)) },
                                            leadingIcon = { PixIcon(PixSymbol.SEARCH) },
                                            onClick = {
                                                menu = false
                                                model.filter.value =
                                                    TaskFilter(mode = "ALL", showCompleted = true)
                                                navigate("search")
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.manage_lists)) },
                                            leadingIcon = { PixIcon(PixSymbol.LISTS) },
                                            onClick = {
                                                menu = false
                                                navigate("organize")
                                            },
                                        )
                                    }
                                }
                        },
                    )
                },
                bottomBar = {
                    if (route != "search")
                        Row(
                            Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                                .navigationBarsPadding()
                                .height(64.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            listOf(
                                    Triple("home", R.string.tasks, PixSymbol.TASKS),
                                    Triple("calendar", R.string.calendar, PixSymbol.CALENDAR),
                                    Triple("matrix", R.string.matrix, PixSymbol.LISTS),
                                    Triple("organize", R.string.organize, PixSymbol.INBOX),
                                    Triple("settings", R.string.settings, PixSymbol.SETTINGS),
                                )
                                .forEach { (destination, label, icon) ->
                                    val description = stringResource(label)
                                    IconButton(
                                        onClick = { navigate(destination) },
                                        modifier =
                                            Modifier.size(56.dp)
                                                .testTag("navigation-$destination")
                                                .semantics {
                                                    contentDescription = description
                                                    selected = route == destination
                                                },
                                    ) {
                                        PixIcon(
                                            icon,
                                            tint =
                                                if (route == destination)
                                                    MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                        }
                },
                floatingActionButton = {
                    if (route == "home" || route == "calendar")
                        FloatingActionButton(
                            onClick = {
                                quickQuadrant = null
                                quickMinute = null
                                quick = true
                            },
                            shape = CircleShape,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(58.dp),
                        ) {
                            PixIcon(
                                PixSymbol.PLUS,
                                stringResource(R.string.add_task),
                                Modifier.size(30.dp),
                                MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                },
            ) { padding ->
                NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
                    composable("home") {
                        Column {
                            if (filter.listId == null && filter.tagId == null)
                                Row(
                                    Modifier.fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    modes.forEach { (mode, label) ->
                                        FilterChip(
                                            selected = filter.mode == mode,
                                            onClick = {
                                                model.filter.value = filter.copy(mode = mode)
                                            },
                                            label = { Text(stringResource(label)) },
                                            shape = CircleShape,
                                            border = null,
                                            colors =
                                                FilterChipDefaults.filterChipColors(
                                                    containerColor =
                                                        MaterialTheme.colorScheme.background,
                                                    selectedContainerColor =
                                                        MaterialTheme.colorScheme
                                                            .secondaryContainer,
                                                    selectedLabelColor =
                                                        MaterialTheme.colorScheme.primary,
                                                ),
                                        )
                                    }
                                }
                            TaskList(
                                content,
                                filter.mode,
                                model::retry,
                                model::open,
                                ::toggle,
                                dimmedLists,
                                manualOrder,
                                model::reorderTask,
                            )
                        }
                    }
                    composable("calendar") {
                        val dayContent by model.calendarContent.collectAsStateWithLifecycle()
                        val marks by model.calendarMarks.collectAsStateWithLifecycle()
                        val googleEvents by model.calendarGoogle.collectAsStateWithLifecycle()
                        val weekly by model.calendarWeekly.collectAsStateWithLifecycle()
                        var googleDetail by remember { mutableStateOf<GoogleEventEntity?>(null) }
                        Column {
                            Row(
                                Modifier.padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    !weekly,
                                    { model.setCalendarWeekly(false) },
                                    label = { Text(stringResource(R.string.month_view)) },
                                    modifier = Modifier.testTag("calendar-month"),
                                )
                                FilterChip(
                                    weekly,
                                    { model.setCalendarWeekly(true) },
                                    label = { Text(stringResource(R.string.week_view)) },
                                    modifier = Modifier.testTag("calendar-week"),
                                )
                            }
                            Box(Modifier.weight(1f)) {
                                if (weekly)
                                    WeekCalendarScreen(
                                        calendarDay,
                                        now.toLocalDate(),
                                        marks,
                                        dayContent,
                                        model::selectDate,
                                        model::open,
                                        ::toggle,
                                        { minute ->
                                            quickMinute = minute
                                            quickQuadrant = null
                                            quick = true
                                        },
                                        googleEvents,
                                        { googleDetail = it },
                                    )
                                else
                                    CalendarScreen(
                                        calendarDay,
                                        now.toLocalDate(),
                                        marks,
                                        dayContent,
                                        model::selectDate,
                                        model::open,
                                        ::toggle,
                                        googleEvents,
                                        { googleDetail = it },
                                    )
                            }
                        }
                        googleDetail?.let { GoogleEventDetail(it) { googleDetail = null } }
                    }
                    composable("matrix") {
                        val matrixContent by model.matrixContent.collectAsStateWithLifecycle()
                        MatrixScreen(
                            matrixContent,
                            now.toLocalDate(),
                            matrixConfig,
                            model::open,
                            ::toggle,
                            { quadrant ->
                                quickQuadrant = quadrant
                                quickMinute = null
                                quick = true
                            },
                            model::retry,
                        )
                    }

                    composable("organize") {
                        OrganizeScreen(model, lists, tags) { listId, tagId ->
                            choose(TaskFilter(mode = "ALL", listId = listId, tagId = tagId))
                        }
                    }
                    composable("search") {
                        val query by model.search.collectAsStateWithLifecycle()
                        val focus = remember { FocusRequester() }
                        LaunchedEffect(Unit) { focus.requestFocus() }
                        BackHandler {
                            model.search.value = ""
                            nav.popBackStack()
                        }
                        Column {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { model.search.value = it },
                                placeholder = { Text(stringResource(R.string.search)) },
                                leadingIcon = { PixIcon(PixSymbol.SEARCH) },
                                singleLine = true,
                                modifier =
                                    Modifier.fillMaxWidth().padding(16.dp).focusRequester(focus),
                                shape = MaterialTheme.shapes.large,
                            )
                            TaskList(content, "SEARCH", model::retry, model::open, ::toggle)
                        }
                    }
                    composable("settings") { SettingsScreen(model) }
                }
            }
        }
    }
    taskAction?.let { TaskActionSheet(it, lists, model) { taskAction = null } }
    if (quick)
        QuickAddSheet(
            model,
            lists,
            tags,
            if (route == "calendar" || route == "matrix") INBOX_ID else filter.listId ?: INBOX_ID,
            if (route == "calendar") calendarDay
            else if (route == "matrix") null
            else if (filter.mode == "TOMORROW") now.toLocalDate().plusDays(1).toEpochDay()
            else if (filter.mode == "TODAY" || filter.mode == "WEEK") now.toLocalDate().toEpochDay()
            else null,
            minute = quickMinute,
            quadrant = quickQuadrant,
        ) {
            quick = false
        }
    if (matrixOptions) MatrixOptions(matrixConfig, model::setMatrixConfig) { matrixOptions = false }
    draft?.let { TaskEditor(model, it, lists, tags) }
}

@Composable
fun TaskList(
    content: TaskContent,
    mode: String,
    retry: () -> Unit,
    open: (TaskWithDetails) -> Unit,
    complete: (TaskEntity) -> Unit,
    dimmedLists: Set<String> = emptySet(),
    manual: Boolean = false,
    reorder: (String, String) -> Unit = { _, _ -> },
) {
    val reorderState = remember { ReorderState() }
    var expanded by rememberSaveable(mode) { mutableStateOf(false) }
    if (content.loading) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        return
    }
    if (content.failed) {
        Column(Modifier.padding(24.dp)) {
            Text(stringResource(R.string.error))
            TextButton(onClick = retry) { Text(stringResource(R.string.retry)) }
        }
        return
    }
    val active = content.tasks.filterNot { it.task.isCompleted }
    val done = content.tasks.filter { it.task.isCompleted }
    LazyColumn(
        modifier = Modifier.testTag("task-list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (manual && active.isNotEmpty())
            item(key = "order-hint") {
                Text(
                    stringResource(R.string.manual_order_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        if (content.tasks.isEmpty())
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PixIcon(PixSymbol.TASKS, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(20.dp))
                    Text(
                        stringResource(
                            when (mode) {
                                "TODAY" -> R.string.empty_today
                                "WEEK" -> R.string.empty_week
                                "OVERDUE" -> R.string.empty_overdue
                                "SEARCH" -> R.string.empty_search
                                else -> R.string.empty_all
                            }
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        active.forEach { detail ->
            item(key = detail.task.id) {
                val row: @Composable () -> Unit = {
                    TaskRow(
                        detail,
                        { open(detail) },
                        { complete(detail.task) },
                        mode != "ALL" && detail.task.listId in dimmedLists,
                    )
                }
                if (manual) {
                    val group =
                        active
                            .filter {
                                it.task.listId == detail.task.listId &&
                                    it.task.dueDay == detail.task.dueDay
                            }
                            .map { it.task.id }
                    ReorderItem(detail.task.id, group, reorderState, reorder, row)
                } else row()
            }
        }
        if (done.isNotEmpty()) {
            item(key = "completed-heading") {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.completed_section),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            done.size.toString(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PixIcon(PixSymbol.CHEVRON)
                    }
                }
            }
            if (expanded)
                done.forEach { detail ->
                    item(key = detail.task.id) {
                        TaskRow(
                            detail,
                            { open(detail) },
                            { complete(detail.task) },
                            mode != "ALL" && detail.task.listId in dimmedLists,
                        )
                    }
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(model: TasksViewModel) {
    val theme by model.theme.collectAsStateWithLifecycle()
    var appearance by remember { mutableStateOf(false) }
    var reminders by remember { mutableStateOf(false) }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).background(MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                PixIcon(
                    PixSymbol.TASKS,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primaryContainer,
                )
            }
            Column(Modifier.padding(start = 16.dp)) {
                BrandWordmark()
                AccountStatusLine()
            }
        }
        Surface(shape = MaterialTheme.shapes.large) {
            Column {
                SettingsRow(
                    PixSymbol.SETTINGS,
                    stringResource(R.string.appearance),
                    stringResource(listOf(R.string.system, R.string.light, R.string.dark)[theme]),
                ) {
                    appearance = true
                }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingsRow(PixSymbol.CLOCK, stringResource(R.string.reminders), null) {
                    reminders = true
                }
            }
        }
        PersonalizationSettings(model)
        BackupSettings(model)
        AccountSettings(model)
        GoogleCalendarSettings()
        DataSyncSettings()
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.local_info),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.app_name) + " · 1.0",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
    if (appearance)
        AlertDialog(
            onDismissRequest = { appearance = false },
            title = { Text(stringResource(R.string.appearance)) },
            text = {
                Column {
                    listOf(R.string.system, R.string.light, R.string.dark).forEachIndexed {
                        index,
                        label ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                model.setTheme(index)
                                appearance = false
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                theme == index,
                                {
                                    model.setTheme(index)
                                    appearance = false
                                },
                            )
                            Text(stringResource(label))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { appearance = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    if (reminders)
        ModalBottomSheet(onDismissRequest = { reminders = false }) {
            Column(Modifier.padding(24.dp)) { ReminderControls(showSwitch = true) }
        }
}

@Composable
private fun SettingsRow(icon: PixSymbol, title: String, value: String?, click: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = click).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixIcon(icon, tint = MaterialTheme.colorScheme.primaryContainer)
        Text(
            title,
            Modifier.weight(1f).padding(start = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        value?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PixIcon(PixSymbol.CHEVRON)
    }
}
