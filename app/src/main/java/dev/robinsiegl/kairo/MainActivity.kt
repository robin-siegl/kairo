package dev.robinsiegl.kairo

import android.Manifest
import android.content.Intent
import android.content.Context.MODE_PRIVATE
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var openedPayment by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) animateLaunch()
        enableEdgeToEdge()
        openedPayment = intent.getStringExtra("payment")
        val storage = Storage(this)
        val initial = runCatching(storage::read)
        setContent {
            val dark = isSystemInDarkTheme()
            val colors = if (dark) darkColorScheme(
                primary = Color(0xFFADC6FF),
                onPrimary = Color(0xFF002E6A),
                primaryContainer = Color(0xFF1B449C),
                onPrimaryContainer = Color(0xFFD8E5FF),
                secondaryContainer = Color(0xFF3C4665),
                onSecondaryContainer = Color(0xFFDDE3FF),
            ) else lightColorScheme(
                primary = Color(0xFF3367DB),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFD8E5FF),
                onPrimaryContainer = Color(0xFF101B37),
                secondaryContainer = Color(0xFFE1E7F7),
                onSecondaryContainer = Color(0xFF242F4A),
            )
            MaterialTheme(colorScheme = colors) {
                if (initial.isSuccess) {
                    Kairo(storage, initial.getOrThrow(), openedPayment) { openedPayment = null }
                } else {
                    Surface { Text("Kairo couldn’t read your local data. Your file is still on this device.") }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openedPayment = intent.getStringExtra("payment")
    }
}

private enum class Tab(val label: String) {
    Overview("Overview"), Payments("Payments"), Calendar("Calendar"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Kairo(storage: Storage, initial: Book, openedPayment: String?, clearOpened: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val reminders = remember(context) { Reminders(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var book by remember { mutableStateOf(initial) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    var notificationsOn by remember { mutableStateOf(reminders.enabled()) }
    var trialChannelOn by remember { mutableStateOf(reminders.channelEnabled(true)) }
    var renewalChannelOn by remember { mutableStateOf(reminders.channelEnabled(false)) }
    var tab by rememberSaveable { mutableStateOf(Tab.Overview) }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var selectedDayText by rememberSaveable { mutableStateOf(today.toString()) }
    var screen by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryOrigin by rememberSaveable { mutableStateOf<String?>(null) }
    var detailParent by rememberSaveable { mutableStateOf("") }
    var quickCancel by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<Book?>(null) }
    val selected = book.payments.find { it.id == selectedId }

    fun updateNotificationStatus() {
        notificationsOn = reminders.enabled()
        trialChannelOn = reminders.channelEnabled(true)
        renewalChannelOn = reminders.channelEnabled(false)
    }

    fun save(newBook: Book): Boolean {
        return runCatching { storage.write(newBook) }.fold(
            onSuccess = {
                book = newBook
                runCatching { reminders.refresh(book) }
                    .onFailure { scope.launch { snackbar.showSnackbar("Couldn’t update reminders.") } }
                true
            },
            onFailure = {
                scope.launch { snackbar.showSnackbar("Couldn’t save changes. Check available storage.") }
                false
            },
        )
    }

    fun savePayment(payment: Payment): Boolean {
        val previous = book.payments.find { it.id == payment.id }
        val payments = if (previous != null) book.payments.map { if (it.id == payment.id) payment else it }
        else book.payments + payment
        val saved = save(book.copy(payments = payments))
        if (saved && previous != null && payment != previous) reminders.dismiss(payment.id)
        return saved
    }

    fun openPayment(id: String) {
        if (screen != "edit") detailParent = if (screen == "category") "category" else ""
        quickCancel = false
        selectedId = id
        screen = "details"
    }

    fun openCategory(name: String) {
        if (screen != "details" || detailParent != "category") {
            categoryOrigin = selectedId.takeIf { screen == "details" }
        }
        category = name
        screen = "category"
    }

    fun quickAction(id: String, cancel: Boolean) {
        openPayment(id)
        if (cancel) quickCancel = true else screen = "edit"
    }

    fun back() {
        screen = when {
            screen == "edit" && selected != null -> "details"
            screen == "details" -> detailParent
            screen == "category" && categoryOrigin != null -> {
                selectedId = categoryOrigin
                categoryOrigin = null
                detailParent = ""
                "details"
            }
            else -> ""
        }
    }

    LaunchedEffect(openedPayment) {
        if (openedPayment != null) {
            if (book.payments.any { it.id == openedPayment }) openPayment(openedPayment)
            clearOpened()
        }
    }

    LaunchedEffect(today, reminders) { runCatching { reminders.refresh(book) } }
    DisposableEffect(owner, reminders) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                today = LocalDate.now()
                updateNotificationStatus()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(notificationsOn, trialChannelOn, renewalChannelOn) {
        runCatching { reminders.refresh(book) }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        updateNotificationStatus()
    }
    fun enableNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !notificationsOn &&
            !context.getSharedPreferences("permission", MODE_PRIVATE).getBoolean("asked", false)
        ) {
            context.getSharedPreferences("permission", MODE_PRIVATE).edit().putBoolean("asked", true).apply()
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }
    }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val snapshot = book
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use {
                            it.write(encodeBook(snapshot).encodeToByteArray())
                        }
                    }
                }
                snackbar.showSnackbar(if (result.isSuccess) "Backup exported" else "Couldn’t export backup")
            }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    requireNotNull(context.contentResolver.openInputStream(uri)).use {
                        decodeBook(readBackup(it))
                    }
                }
            }
            result.onSuccess { pendingImport = it }
                .onFailure { snackbar.showSnackbar("This file couldn’t be imported.") }
        }
    }

    BackHandler(enabled = screen.isNotEmpty(), onBack = ::back)
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(when (screen) {
                    "details" -> selected?.name ?: "Payment"
                    "edit" -> if (selected == null) "Add payment" else "Edit payment"
                    "settings" -> "Settings"
                    "category" -> category.orEmpty()
                    else -> tab.label
                }) },
                navigationIcon = {
                    if (screen.isNotEmpty()) IconButton(onClick = ::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (screen == "details" && selected != null) IconButton(onClick = { screen = "edit" }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit payment")
                    }
                    if (screen.isEmpty()) IconButton(onClick = { screen = "settings" }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            if (screen.isEmpty() && tab != Tab.Calendar) ExtendedFloatingActionButton(
                text = { Text("Add payment") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = { selectedId = null; screen = "edit" },
            )
        },
        bottomBar = {
            if (screen.isEmpty()) NavigationBar {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(when (item) {
                            Tab.Overview -> Icons.Default.Home
                            Tab.Payments -> Icons.AutoMirrored.Filled.List
                            Tab.Calendar -> Icons.Default.DateRange
                        }, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { insets ->
        Box(Modifier.padding(insets)) {
            val month = YearMonth.parse(monthText)
            val editMonth: (YearMonth) -> Unit = {
                monthText = it.toString()
                if (YearMonth.from(LocalDate.parse(selectedDayText)) != it) {
                    selectedDayText = it.atDay(1).toString()
                }
            }
            when (screen) {
                "edit" -> PaymentEditor(selected, book, today) {
                    if (savePayment(it)) {
                        openPayment(it.id)
                        if (!notificationsOn && ((it.isTrial(today) && book.preferences.trialReminders) || it.remind) &&
                            Build.VERSION.SDK_INT >= 33 &&
                            !context.getSharedPreferences("permission", MODE_PRIVATE).getBoolean("asked", false)
                        ) enableNotifications()
                    }
                }
                "details" -> selected?.let {
                    PaymentDetails(
                        it, today, book.preferences.defaultCurrency,
                        onSave = ::savePayment,
                        onDelete = {
                            val saved = save(book.copy(payments = book.payments.filterNot { payment -> payment.id == it.id }))
                            if (saved) {
                                reminders.dismiss(it.id)
                                if (categoryOrigin == it.id) categoryOrigin = null
                                selectedId = null
                                back()
                            }
                            saved
                        },
                        onCategory = ::openCategory,
                        requestCancellation = quickCancel,
                        consumeCancellation = { quickCancel = false },
                        onOpenLink = { url ->
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                .onFailure { scope.launch { snackbar.showSnackbar("Couldn’t open this link.") } }
                        },
                    )
                }
                "category" -> category?.let {
                    Payments(book, today, ::openPayment, ::openCategory, ::quickAction, category = it)
                }
                "settings" -> PreferencesScreen(
                    book.preferences, notificationsOn && trialChannelOn && renewalChannelOn,
                    { save(book.copy(preferences = it)) }, ::enableNotifications,
                    { export.launch("kairo-backup.json") }, { import.launch(arrayOf("application/json", "text/plain")) },
                )
                else -> when (tab) {
                    Tab.Overview -> Overview(
                        book, today, notificationsOn && trialChannelOn,
                        ::openPayment, ::enableNotifications,
                    )
                    Tab.Payments -> Payments(book, today, ::openPayment, ::openCategory, ::quickAction)
                    Tab.Calendar -> Calendar(
                        book, month, LocalDate.parse(selectedDayText), today,
                        editMonth, { selectedDayText = it.toString() }, ::openPayment,
                    )
                }
            }
        }
    }

    pendingImport?.let { incoming ->
        val merge = book.merge(incoming)
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Merge backup?") },
            text = {
                Text("${merge.added} new payments. ${merge.conflicts} changed payments will keep the version on this device.")
            },
            confirmButton = {
                TextButton(onClick = {
                    if (save(merge.book)) {
                        pendingImport = null
                        scope.launch { snackbar.showSnackbar("Backup merged") }
                    }
                }) { Text("Merge backup") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }
}

private fun readBackup(stream: InputStream): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var count = stream.read(buffer)
    while (count != -1) {
        require(output.size() + count <= MAX_BACKUP_BYTES)
        output.write(buffer, 0, count)
        count = stream.read(buffer)
    }
    return output.toByteArray().decodeToString()
}
