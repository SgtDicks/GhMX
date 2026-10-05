package au.com.ghmx.android

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.imageLoader
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import au.com.ghmx.android.data.*
import au.com.ghmx.android.notify.ReminderScheduler
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(ReminderScheduler.CHANNEL_ID, "GhMX reminders and updates", NotificationManager.IMPORTANCE_DEFAULT)
        )
        setContent { GhMxApp(GhMxRepository(this), PlanStore(this)) }
    }
}

private enum class AppTab(val label: String) { Home("Home"), Schedule("Event Schedule"), Explore("Explore"), Reminders("Reminders"), Settings("Settings") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GhMxApp(repository: GhMxRepository, plans: PlanStore) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ghmx_settings", Context.MODE_PRIVATE) }
        var appearance by rememberSaveable { mutableStateOf(prefs.getString("appearance", "system") ?: "system") }
        val firebaseReady = remember { FirebaseApp.getApps(context).isNotEmpty() }
        var pushEnabled by rememberSaveable { mutableStateOf(firebaseReady && prefs.getBoolean("push_enabled", false)) }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = appearance == "dark" || (appearance == "system" && systemDark)
    val colors = if (dark) darkColorScheme(primary = Color(0xFF82C9FF), secondary = Color(0xFF75D6E8), surface = Color(0xFF182847))
        else lightColorScheme(primary = Color(0xFF075B9C), secondary = Color(0xFF006B76), surface = Color(0xFFF2F7FC))
    MaterialTheme(colorScheme = colors) {
        var remote by remember { mutableStateOf(repository.readCache()) }
        var selectedTab by rememberSaveable { mutableStateOf(AppTab.Home) }
        var selectedVendor by remember { mutableStateOf<String?>(null) }
        var selectedEvent by remember { mutableStateOf<Pair<String, String>?>(null) }
        var selectedFood by remember { mutableStateOf<String?>(null) }
        val isDetail = selectedVendor != null || selectedEvent != null || selectedFood != null
        val scope = rememberCoroutineScope()

        fun refresh(force: Boolean) {
            scope.launch {
                remote = remote.copy(loading = true, error = null)
                val updated = repository.refresh(force)
                plans.migrateFavoriteEvents((updated.central + updated.cosplay).flatMap { it.events })
                remote = updated
                prefetchScheduleImages(context, updated)
            }
        }
        LaunchedEffect(Unit) { refresh(false) }
        LaunchedEffect(selectedTab) { analytics(context, "screen_view") { putString("screen_name", selectedTab.label) } }
        BackHandler(enabled = isDetail) { selectedVendor = null; selectedEvent = null; selectedFood = null }

        Box(Modifier.fillMaxSize()) {
            Image(painterResource(R.drawable.home_background), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (dark) 0.35f else 0.15f)))
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text(if (isDetail) selectedVendor?.let { remote.vendors.firstOrNull { v -> v.id == it }?.name }
                            ?: selectedEvent?.second ?: selectedFood ?: "GhMX" else selectedTab.label, maxLines = 1) },
                        navigationIcon = {
                            if (isDetail) IconButton(onClick = { selectedVendor = null; selectedEvent = null; selectedFood = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            if (remote.loading) CircularProgressIndicator(Modifier.size(22.dp).padding(3.dp), strokeWidth = 2.dp)
                            else IconButton(onClick = { refresh(true) }) { Icon(Icons.Default.Refresh, contentDescription = "Check for updates") }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xCC102C63),
                            titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White)
                    )
                },
                bottomBar = {
                    NavigationBar(containerColor = if (dark) Color(0xF21A2944) else Color(0xF7FFFFFF)) {
                        AppTab.entries.forEach { tab ->
                            NavigationBarItem(selected = tab == selectedTab, onClick = {
                                selectedTab = tab; selectedVendor = null; selectedEvent = null; selectedFood = null
                            }, icon = { Icon(when (tab) {
                                AppTab.Home -> Icons.Default.Home
                                AppTab.Schedule -> Icons.Default.CalendarMonth
                                AppTab.Explore -> Icons.Default.Map
                                AppTab.Reminders -> Icons.Default.Notifications
                                AppTab.Settings -> Icons.Default.Settings
                            }, contentDescription = tab.label) }, label = {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text(tab.label, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                }
                            })
                        }
                    }
                }
            ) { inset ->
                Box(Modifier.fillMaxSize().padding(inset)) {
                    when {
                        selectedVendor != null -> remote.vendors.find { it.id == selectedVendor }?.let { vendor ->
                            VendorDetail(vendor, plans, remote, dark,
                onSave = { plans.saveVendor(it) }, onRemove = {
                    ReminderScheduler.cancel(context, "vendor:$it"); plans.removeVendor(it)
                },
                                onWebsite = { url -> openUrl(context, url); analytics(context, "vendor_website_click") { putString("vendor_id", vendor.id) } })
                        }
                        selectedEvent != null -> {
                            val (eventId, day) = selectedEvent!!
                            val event = (remote.central + remote.cosplay).flatMap { it.events }.firstOrNull { it.id == eventId }
                            if (event != null) EventDetail(event, day, remote, plans, dark,
                                onFavorite = { favorite -> plans.setFavorite(event.id, favorite) })
                        }
                        selectedFood != null -> FoodDetail(FoodVendor.all.first { it.name == selectedFood }, dark)
                        else -> when (selectedTab) {
                            AppTab.Home -> HomeScreen(dark, remote, onOpenExplore = { selectedTab = AppTab.Explore }, onRefresh = { refresh(true) })
                            AppTab.Schedule -> ScheduleScreen(remote, plans, dark, onEvent = { event, day -> selectedEvent = event.id to day })
                            AppTab.Explore -> ExploreScreen(remote, plans, dark, onVendor = { selectedVendor = it }, onFood = { selectedFood = it })
                            AppTab.Reminders -> RemindersScreen(remote, plans, dark,
                                onVendor = { selectedVendor = it }, onEvent = { event, day -> selectedEvent = event.id to day })
                            AppTab.Settings -> SettingsScreen(remote, dark, appearance,
                                onAppearance = { appearance = it; prefs.edit().putString("appearance", it).apply() },
                                onRefresh = { refresh(true) }, pushEnabled = pushEnabled, notificationsReady = firebaseReady,
                                onPushChanged = { enabled -> pushEnabled = enabled; prefs.edit().putBoolean("push_enabled", enabled).apply() })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageColumn(dark: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun GhCard(dark: Boolean, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val foreground = if (dark) Color.White else Color(0xFF132847)
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = if (dark) Color(0xD9192438) else Color(0xEAF7FBFF),
        contentColor = foreground, tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
    }
}

@Composable
private fun HomeScreen(dark: Boolean, data: RemoteData, onOpenExplore: () -> Unit, onRefresh: () -> Unit) {
    PageColumn(dark) {
        GhCard(dark) {
            AsyncImage(model = ImageRequest.Builder(LocalContext.current).data("$DATA_BASE/GhMX%20logo.png")
                .diskCachePolicy(CachePolicy.ENABLED).crossfade(true).build(),
                placeholder = painterResource(R.drawable.home_logo), error = painterResource(R.drawable.home_logo),
                contentDescription = "GhMX logo", modifier = Modifier.fillMaxWidth().heightIn(max = 270.dp), contentScale = ContentScale.Fit)
            Text("Gaming, Hobby & Model Expo", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("21–22 November 2026 · Brisbane Showgrounds", style = MaterialTheme.typography.titleMedium)
            Text("Your companion for the GhMX weekend. Find exhibitors and food vendors, explore the venue map, and save events when the 2026 schedule is released.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenExplore) { Icon(Icons.Default.Map, null); Spacer(Modifier.width(6.dp)); Text("Explore") }
                OutlinedButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Update") }
            }
        }
        if (!data.scheduleAvailable) GhCard(dark) {
            Text("2026 event schedule coming soon", fontWeight = FontWeight.Bold)
            Text("The event team will publish the detailed times closer to the expo. Vendor and venue information is available now.")
        }
        if (data.error != null) GhCard(dark) { Text(data.error) }
    }
}

@Composable
private fun ScheduleScreen(data: RemoteData, plans: PlanStore, dark: Boolean, onEvent: (GhMxEvent, String) -> Unit) {
    var day by rememberSaveable { mutableStateOf("Saturday") }
    var stage by rememberSaveable { mutableStateOf("All") }
    PageColumn(dark) {
        if (!data.scheduleAvailable) {
            GhCard(dark) {
                Icon(Icons.Default.EventBusy, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                Text("2026 Program Coming Soon", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(data.message)
                Text("This tab checks the official data source automatically. Use the refresh button above to check now.")
            }
            return@PageColumn
        }
        GhCard(dark) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Saturday", "Sunday").forEach { name -> FilterChip(day == name, { day = name }, { Text(name) }) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("All", "Central", "Cosplay").forEach { name -> FilterChip(stage == name, { stage = name }, { Text(name) }) } }
        }
        val matchingDays = (if (stage == "Central") data.central else if (stage == "Cosplay") data.cosplay else data.central + data.cosplay).filter { it.day == day }
        val events = matchingDays.flatMap { it.events }.sortedBy { timeOrder(it.time) }
        if (events.isEmpty()) GhCard(dark) { Text("No events listed for $day yet.") }
        events.forEach { event ->
            val saved = event.id in plans.favoriteEvents()
            GhCard(dark, Modifier.fillMaxWidth().clickable { onEvent(event, day) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(event.time, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(event.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(event.stage)
                    }
                    Icon(if (saved) Icons.Default.Star else Icons.Default.StarBorder, "Open event", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun ExploreScreen(data: RemoteData, plans: PlanStore, dark: Boolean, onVendor: (String) -> Unit, onFood: (String) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    PageColumn(dark) {
        GhCard(dark) {
            Text("Venue map", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            ZoomableImage("$DATA_BASE/floor_map.png", Modifier.fillMaxWidth().height(250.dp), ContentScale.Fit)
            Text("Pinch to zoom and drag to look around the venue.", style = MaterialTheme.typography.bodySmall)
        }
        GhCard(dark) {
            Text("Exhibitors", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("Search name or booth") }, singleLine = true)
        }
        val vendors = data.vendors.filter { search.isBlank() || it.name.contains(search, true) || it.booth.contains(search, true) }
        vendors.forEach { vendor ->
            GhCard(dark, Modifier.fillMaxWidth().clickable { onVendor(vendor.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(vendor.name, fontWeight = FontWeight.SemiBold)
                        Text(if (vendor.booth.isBlank()) "Booth information coming soon" else "Booth ${vendor.booth}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (plans.vendors().any { it.id == vendor.id }) Icon(Icons.Default.Bookmark, "Saved", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        GhCard(dark) { Text("Food & menus", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) }
        FoodVendor.all.forEach { food ->
            GhCard(dark, Modifier.fillMaxWidth().clickable { onFood(food.name) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage(food.imageUrl, food.name, Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                    Text(food.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Default.OpenInNew, null)
                }
            }
        }
    }
}

@Composable
private fun RemindersScreen(data: RemoteData, plans: PlanStore, dark: Boolean, onVendor: (String) -> Unit, onEvent: (GhMxEvent, String) -> Unit) {
    val saved = plans.vendors()
    val favorites = plans.favoriteEvents()
    PageColumn(dark) {
        GhCard(dark) {
            Text("Vendor reminders", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            val reminders = saved.filter { it.reminderAt != null }
            if (reminders.isEmpty()) Text("Set a visit reminder on a vendor’s page and it will appear here.")
            reminders.forEach { vendor ->
                Row(Modifier.fillMaxWidth().clickable { onVendor(vendor.id) }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(vendor.name, fontWeight = FontWeight.SemiBold)
                        Text("${vendor.reminderAt?.let(::formatDateTime)} · Booth ${vendor.booth}", style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Default.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        GhCard(dark) {
            Text("Saved vendors and notes", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            if (saved.isEmpty()) Text("Save exhibitors from Explore to keep notes and booth details here.")
            saved.forEach { vendor ->
                Row(Modifier.fillMaxWidth().clickable { onVendor(vendor.id) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(vendor.name, fontWeight = FontWeight.SemiBold)
                        Text("Booth ${vendor.booth}" + if (vendor.note.isBlank()) "" else " · ${vendor.note}", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Open ${vendor.name}", modifier = Modifier.graphicsLayer(rotationZ = 180f))
                }
            }
        }
        GhCard(dark) {
            Text("My event schedule", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            if (!data.scheduleAvailable) Text("The personal event schedule will appear here when the real 2026 program is released. Saved vendors are available now.")
            else {
                val events = (data.central + data.cosplay).flatMap { group -> group.events.filter { it.id in favorites }.map { group.day to it } }
                if (events.isEmpty()) Text("Save sessions from Event Schedule to build your own plan.")
                events.sortedWith(compareBy({ it.first }, { timeOrder(it.second.time) })).forEach { (day, event) ->
                    Column(Modifier.fillMaxWidth().clickable { onEvent(event, day) }.padding(vertical = 3.dp)) {
                        Text(event.title, fontWeight = FontWeight.SemiBold)
                        Text("$day · ${event.time} · ${event.stage}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun VendorDetail(vendor: GhMxVendor, plans: PlanStore, data: RemoteData, dark: Boolean,
                         onSave: (SavedVendor) -> Unit, onRemove: (String) -> Unit, onWebsite: (String) -> Unit) {
    val context = LocalContext.current
    var current by remember(vendor.id) { mutableStateOf(plans.vendors().find { it.id == vendor.id } ?: SavedVendor(vendor.id, vendor.name, vendor.booth)) }
    var noteDialog by remember { mutableStateOf(false) }
    var note by remember(current.note) { mutableStateOf(current.note) }
    var status by remember { mutableStateOf<String?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) chooseReminderTime(context, vendor, plans, current, onUpdated = { current = it }, onError = { status = it })
        else status = "Allow notifications in Android Settings to receive visit reminders."
    }
    PageColumn(dark) {
        GhCard(dark) {
            Text(vendor.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (vendor.booth.isNotBlank()) Text("Booth ${vendor.booth}", style = MaterialTheme.typography.titleMedium)
            if (vendor.description.isNotBlank()) Text(vendor.description)
        }
        GhCard(dark) {
            Text("My visit", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (plans.vendors().any { it.id == vendor.id }) {
                        onRemove(vendor.id); current = SavedVendor(vendor.id, vendor.name, vendor.booth)
                    } else {
                        current = current.copy(name = vendor.name, booth = vendor.booth); onSave(current)
                    }
                }) {
                    Icon(if (plans.vendors().any { it.id == vendor.id }) Icons.Default.BookmarkRemove else Icons.Default.BookmarkAdd, null)
                    Spacer(Modifier.width(6.dp)); Text(if (plans.vendors().any { it.id == vendor.id }) "Remove saved vendor" else "Save vendor")
                }
                OutlinedButton(onClick = { noteDialog = true }) { Icon(Icons.Default.Notes, null); Spacer(Modifier.width(5.dp)); Text("Add note") }
            }
            if (current.note.isNotBlank()) Text("Note: ${current.note}")
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else chooseReminderTime(context, vendor, plans, current, onUpdated = { current = it }, onError = { status = it })
            }) {
                Icon(Icons.Default.NotificationsActive, null); Spacer(Modifier.width(7.dp)); Text("Remind me to visit")
            }
            current.reminderAt?.let { Text("Reminder set for ${formatDateTime(it)}") }
            if (current.reminderAt != null) TextButton(onClick = {
                ReminderScheduler.cancel(context, "vendor:${vendor.id}")
                current = current.copy(reminderAt = null); onSave(current)
            }) { Text("Cancel reminder") }
            if (vendor.website.isNotBlank()) OutlinedButton(onClick = { onWebsite(vendor.website) }) {
                Icon(Icons.Default.Language, null); Spacer(Modifier.width(6.dp)); Text("Visit vendor website")
            }
        }
    }
    if (noteDialog) AlertDialog(
        onDismissRequest = { noteDialog = false },
        title = { Text("Note for ${vendor.name}") },
        text = { OutlinedTextField(note, { note = it }, label = { Text("Your note") }, minLines = 3, maxLines = 6) },
        confirmButton = { TextButton(onClick = {
            current = current.copy(name = vendor.name, booth = vendor.booth, note = note.trim())
            onSave(current); noteDialog = false
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { noteDialog = false }) { Text("Cancel") } },
    )
    status?.let { message -> AlertDialog(onDismissRequest = { status = null }, title = { Text("GhMX") },
        text = { Text(message) }, confirmButton = { TextButton(onClick = { status = null }) { Text("OK") } }) }
}

@Composable
private fun EventDetail(event: GhMxEvent, day: String, data: RemoteData, plans: PlanStore, dark: Boolean, onFavorite: (Boolean) -> Unit) {
    val context = LocalContext.current
    val date = (data.central + data.cosplay).firstOrNull { it.day == day }?.date
    var isSaved by remember(event.id) { mutableStateOf(event.id in plans.favoriteEvents()) }
    var status by remember { mutableStateOf<String?>(null) }
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scheduleEventReminder(context, event, day, date, onError = { status = it })
        else status = "Allow notifications in Android Settings to receive event reminders."
    }
    PageColumn(dark) {
        GhCard(dark) {
            if (event.image.isNotBlank()) ZoomableImage(imageUrl(event.image), Modifier.fillMaxWidth().heightIn(max = 320.dp), ContentScale.Fit)
            Text(event.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
            Text("$day ${date ?: ""} · ${event.time}", style = MaterialTheme.typography.titleMedium)
            Text(event.stage)
            Text(event.description)
            Button(onClick = { isSaved = !isSaved; onFavorite(isSaved) }) {
                Icon(if (isSaved) Icons.Default.Star else Icons.Default.StarBorder, null)
                Spacer(Modifier.width(6.dp)); Text(if (isSaved) "Remove from My Program" else "Save to My Program")
            }
            OutlinedButton(onClick = {
                val start = eventDate(date, event.time)
                if (start == null) status = "This event does not have a valid date and time."
                else runCatching {
                    context.startActivity(Intent(Intent.ACTION_INSERT).setData(Uri.parse("content://com.android.calendar/events"))
                        .putExtra("title", event.title).putExtra("description", event.description)
                        .putExtra("beginTime", start).putExtra("endTime", start + 60 * 60 * 1000L))
                }.onFailure { status = "Calendar could not be opened on this device." }
            }) { Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(6.dp)); Text("Add to Calendar") }
            OutlinedButton(onClick = {
                if (Build.VERSION.SDK_INT >= 33) notify.launch(Manifest.permission.POST_NOTIFICATIONS)
                else scheduleEventReminder(context, event, day, date, onError = { status = it })
            }) { Icon(Icons.Default.NotificationsActive, null); Spacer(Modifier.width(6.dp)); Text("Remind me 15 minutes before") }
            OutlinedButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,
                    "${event.title} — $day ${event.time}, ${event.stage} at GhMX")
                context.startActivity(Intent.createChooser(send, "Share event"))
            }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Share event") }
        }
    }
    status?.let { AlertDialog(onDismissRequest = { status = null }, title = { Text("GhMX") }, text = { Text(it) },
        confirmButton = { TextButton(onClick = { status = null }) { Text("OK") } }) }
}

@Composable
private fun FoodDetail(food: FoodVendor, dark: Boolean) {
    PageColumn(dark) {
        GhCard(dark) {
            Text(food.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text("Pinch to zoom into the menu and drag to move around.", style = MaterialTheme.typography.bodySmall)
            ZoomableImage(food.imageUrl, Modifier.fillMaxWidth().heightIn(min = 360.dp, max = 680.dp), ContentScale.Fit)
        }
    }
}

@Composable
private fun SettingsScreen(data: RemoteData, dark: Boolean, appearance: String, onAppearance: (String) -> Unit,
                          onRefresh: () -> Unit, pushEnabled: Boolean, notificationsReady: Boolean,
                          onPushChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    var pushMessage by remember { mutableStateOf<String?>(null) }
    var fcmToken by remember { mutableStateOf("") }
    var fcmTokenMessage by remember { mutableStateOf<String?>(null) }
    val pushPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) subscribeToAnnouncements(onResult = { success ->
            if (success) onPushChanged(true) else pushMessage = "Push announcements need Firebase configured for this Android app."
        }) else pushMessage = "Notifications are off. You can enable them in Android Settings."
    }
    PageColumn(dark) {
        GhCard(dark) {
            Text("Appearance", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (key, label) ->
                Row(Modifier.fillMaxWidth().clickable { onAppearance(key) }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = appearance == key, onClick = { onAppearance(key) }); Text(label)
                }
            }
        }
        GhCard(dark) {
            Text("Event information", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text("Information refreshes from the official GhMX GitHub data and is cached for offline access.")
            Button(onClick = onRefresh) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Check for updates") }
            data.lastChecked?.let { Text("Last checked ${formatDateTime(it)}", style = MaterialTheme.typography.bodySmall) }
            data.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        GhCard(dark) {
            Text("Notifications", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("GhMX event announcements")
                    Text("Optional updates from the event team.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = pushEnabled, enabled = notificationsReady, onCheckedChange = { enabled ->
                    if (enabled) {
                        if (Build.VERSION.SDK_INT >= 33) pushPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else subscribeToAnnouncements { success ->
                            if (success) onPushChanged(true) else pushMessage = "Push announcements need Firebase configured for this Android app."
                        }
                    } else unsubscribeFromAnnouncements { onPushChanged(false) }
                })
            }
            if (!notificationsReady) Text("Event announcements are unavailable until this app is linked to Firebase. Local vendor and schedule reminders still work.",
                color = MaterialTheme.colorScheme.error)
            else pushMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("Vendor and schedule reminders stay on this device and do not need Firebase.", style = MaterialTheme.typography.bodySmall)
        }
        if (BuildConfig.DEBUG) {
            GhCard(dark) {
                Text("Developer tools", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text("Temporary FCM token helper. Keep this token private; it identifies this app installation.",
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = {
                    fcmTokenMessage = "Getting token…"
                    fcmToken = ""
                    runCatching {
                        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                            if (task.isSuccessful && !task.result.isNullOrBlank()) {
                                fcmToken = task.result
                                fcmTokenMessage = "Token ready. Copy it, then paste it into Firebase Console → Send test message."
                            } else {
                                fcmTokenMessage = task.exception?.localizedMessage
                                    ?: "Could not get a token. Check Firebase configuration and internet access."
                            }
                        }
                    }.onFailure { error ->
                        fcmTokenMessage = error.localizedMessage ?: "Could not get an FCM token."
                    }
                }) { Text("Get FCM token") }
                if (fcmToken.isNotBlank()) {
                    SelectionContainer {
                        Text(fcmToken, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("GhMX FCM token", fcmToken))
                        fcmTokenMessage = "Token copied. Paste it into Firebase Console → Send test message."
                    }) {
                        Icon(Icons.Default.ContentCopy, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Copy token")
                    }
                }
                fcmTokenMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
        GhCard(dark) {
            Text("Help & legal", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = { openUrl(context, "https://sgtdicks.github.io/GhMX/privacy.html") }) {
                Icon(Icons.Default.PrivacyTip, null); Spacer(Modifier.width(6.dp)); Text("Privacy policy")
            }
            OutlinedButton(onClick = { openUrl(context, "mailto:slantedcorp@gmail.com") }) {
                Icon(Icons.Default.Email, null); Spacer(Modifier.width(6.dp)); Text("Support email")
            }
            OutlinedButton(onClick = { openUrl(context, "https://www.ghmx.com.au") }) {
                Icon(Icons.Default.Language, null); Spacer(Modifier.width(6.dp)); Text("GhMX website")
            }
        }
        GhCard(dark) {
            Text("About GhMX", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text("GhMX Android · Version 1.0")
            Text("Vendor notes and saved events are stored on this device. Reminders are local notifications.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun subscribeToAnnouncements(onResult: (Boolean) -> Unit) {
    runCatching { FirebaseMessaging.getInstance().subscribeToTopic("ghmx_announcements") }
        .onSuccess { it.addOnCompleteListener { task -> onResult(task.isSuccessful) } }
        .onFailure { onResult(false) }
}

private fun unsubscribeFromAnnouncements(onResult: () -> Unit) {
    runCatching { FirebaseMessaging.getInstance().unsubscribeFromTopic("ghmx_announcements") }
        .onSuccess { it.addOnCompleteListener { onResult() } }
        .onFailure { onResult() }
}

@Composable
private fun ZoomableImage(url: String, modifier: Modifier, contentScale: ContentScale) {
    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offsetX by remember(url) { mutableFloatStateOf(0f) }
    var offsetY by remember(url) { mutableFloatStateOf(0f) }
    Box(modifier.clipToBounds().pointerInput(url) {
        detectTransformGestures { centroid, pan, zoom, _ ->
            val old = scale
            val updated = (scale * zoom).coerceIn(1f, 6f)
            val ratio = updated / old
            // Keep a modest pan range at 1x too, so a one-finger drag moves the map before
            // zooming. Scale then expands the travel range naturally.
            val maxPanX = maxOf(size.width * (updated - 1f) / 2f, size.width * 0.22f)
            val maxPanY = maxOf(size.height * (updated - 1f) / 2f, size.height * 0.22f)
            offsetX = ((offsetX - centroid.x) * ratio + centroid.x + pan.x).coerceIn(-maxPanX, maxPanX)
            offsetY = ((offsetY - centroid.y) * ratio + centroid.y + pan.y).coerceIn(-maxPanY, maxPanY)
            scale = updated
        }
    }) {
        AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(url).diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(true).build(), contentDescription = "Zoomable image", contentScale = contentScale,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            })
    }
}

private fun chooseReminderTime(context: Context, vendor: GhMxVendor, plans: PlanStore, current: SavedVendor,
                               onUpdated: (SavedVendor) -> Unit, onError: (String) -> Unit) {
    val base = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
    DatePickerDialog(context, { _, year, month, day ->
        TimePickerDialog(context, { _, hour, minute ->
            val at = Calendar.getInstance().apply { set(year, month, day, hour, minute, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            if (at <= System.currentTimeMillis()) { onError("Choose a future time for this reminder."); return@TimePickerDialog }
            val updated = current.copy(name = vendor.name, booth = vendor.booth, reminderAt = at)
            plans.saveVendor(updated); ReminderScheduler.schedule(context, "vendor:${vendor.id}", "Visit ${vendor.name}",
                if (vendor.booth.isBlank()) "Your saved GhMX vendor is ready to visit." else "Find them at booth ${vendor.booth}.", at)
            onUpdated(updated)
        }, base.get(Calendar.HOUR_OF_DAY), base.get(Calendar.MINUTE), false).show()
    }, base.get(Calendar.YEAR), base.get(Calendar.MONTH), base.get(Calendar.DAY_OF_MONTH)).show()
}

private fun scheduleEventReminder(context: Context, event: GhMxEvent, day: String, date: String?, onError: (String) -> Unit) {
    val starts = eventDate(date, event.time)
    if (starts == null) { onError("This event does not have a valid date and time."); return }
    val at = starts - 15 * 60 * 1000L
    if (at <= System.currentTimeMillis()) { onError("This event’s reminder time has already passed."); return }
    ReminderScheduler.schedule(context, "event:${event.id}", "Coming up: ${event.title}", "$day · ${event.time} · ${event.stage}", at)
}

private fun eventDate(date: String?, time: String): Long? = runCatching {
    val parsedDate = LocalDate.parse(date ?: return null)
    val parsedTime = LocalTime.parse(time.trim().uppercase(Locale.US), DateTimeFormatter.ofPattern("h:mm a", Locale.US))
    LocalDateTime.of(parsedDate, parsedTime).atZone(ZoneId.of("Australia/Brisbane")).toInstant().toEpochMilli()
}.getOrNull()

private fun timeOrder(time: String): Long = runCatching {
    LocalTime.parse(time.trim().uppercase(Locale.US), DateTimeFormatter.ofPattern("h:mm a", Locale.US)).toSecondOfDay().toLong()
}.getOrDefault(Long.MAX_VALUE)

private fun formatDateTime(millis: Long): String = java.text.SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()).format(millis)

private fun prefetchScheduleImages(context: Context, data: RemoteData) {
    val urls = (data.central + data.cosplay).flatMap { it.events }
        .mapNotNull { it.image.takeIf(String::isNotBlank) }
        .map(::imageUrl)
        .distinct()
    urls.forEach { url ->
        context.imageLoader.enqueue(
            ImageRequest.Builder(context)
                .data(url)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .build()
        )
    }
}

private fun imageUrl(path: String): String {
    if (path.startsWith("https://github.com/") && "/blob/" in path) {
        return path.substringBefore('?')
            .replaceFirst("https://github.com/", "https://raw.githubusercontent.com/")
            .replaceFirst("/blob/", "/")
            .replace(" ", "%20")
    }
    return if (path.startsWith("http")) path else "$DATA_BASE/${path.trimStart('/').replace(" ", "%20")}"
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private fun analytics(context: Context, event: String, params: Bundle.() -> Unit = {}) {
    runCatching {
        val data = Bundle().apply(params)
        FirebaseAnalytics.getInstance(context).logEvent(event, data)
    }
}
