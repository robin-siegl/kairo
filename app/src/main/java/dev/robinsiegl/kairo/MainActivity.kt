package dev.robinsiegl.kairo

import android.Manifest
import android.content.Intent
import android.content.Context.MODE_PRIVATE
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
        enableEdgeToEdge()
        openedPayment = intent.getStringExtra("payment")
        val storage = Storage(this)
        val initial = runCatching(storage::read)
        setContent {
            val dark = isSystemInDarkTheme()
            val colors = when {
                Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(this)
                Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(this)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
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
        val existing = book.payments.any { it.id == payment.id }
        val payments = if (existing) book.payments.map { if (it.id == payment.id) payment else it }
        else book.payments + payment
        return save(book.copy(payments = payments))
    }

    fun openPayment(id: String) {
        selectedId = id
        screen = "details"
    }

    fun back() {
        screen = if (screen == "edit" && selected != null) "details" else ""
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
                    else -> tab.label
                }) },
                navigationIcon = {
                    if (screen.isNotEmpty()) IconButton(onClick = ::back) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
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
                            Tab.Payments -> Icons.Default.List
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
                        it, today, onEdit = { screen = "edit" },
                        onSave = ::savePayment,
                        onOpenLink = { url ->
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                .onFailure { scope.launch { snackbar.showSnackbar("Couldn’t open this link.") } }
                        },
                    )
                }
                "settings" -> PreferencesScreen(
                    book.preferences, notificationsOn && trialChannelOn && renewalChannelOn,
                    { save(book.copy(preferences = it)) }, ::enableNotifications,
                    { export.launch("kairo-backup.json") }, { import.launch(arrayOf("application/json", "text/plain")) },
                )
                else -> when (tab) {
                    Tab.Overview -> Overview(
                        book, month, today, notificationsOn && trialChannelOn,
                        editMonth, ::openPayment, ::enableNotifications,
                    )
                    Tab.Payments -> Payments(book, today, ::openPayment)
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

fun loadIcon(context: android.content.Context, uri: Uri): String {
    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
        val factor = maxOf(info.size.width, info.size.height) / 96.0
        if (factor > 1) decoder.setTargetSize(
            (info.size.width / factor).toInt().coerceAtLeast(1),
            (info.size.height / factor).toInt().coerceAtLeast(1),
        )
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
    return ByteArrayOutputStream().use {
        require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP)
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
