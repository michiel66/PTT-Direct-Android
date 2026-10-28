package dev.tsdroid.ui.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.tsdroid.data.SettingsStore
import dev.tsdroid.han.R
import dev.tsdroid.ptt.HardwarePttController
import dev.tsdroid.viewmodel.ConnectionViewModel
import dev.tsdroid.viewmodel.ServerViewModel
import dev.tslib.ConnectionState
import kotlinx.coroutines.launch

private val Night = Color(0xFF030E19)
private val Panel = Color(0xFF091D2D)
private val PanelLight = Color(0xFF10273C)
private val Line = Color(0xFF25445B)
private val Blue = Color(0xFF168BFF)
private val White = Color(0xFFF3F8FF)
private val Grey = Color(0xFFA9B9CE)
private val Green = Color(0xFF17E353)

private enum class Page { PTT, CHANNELS, USERS, SETTINGS }
private const val FIXED_HOST = "ptt.mijnts3.nl"

@Composable
fun PttDirectApp(
    connectionVm: ConnectionViewModel = viewModel(),
    serverVm: ServerViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("ptt_direct", Context.MODE_PRIVATE) }
    val settings = remember { SettingsStore(context) }
    val connectionState by connectionVm.connectionState.collectAsStateWithLifecycle()
    val connectionError by connectionVm.error.collectAsStateWithLifecycle()
    val liveState by serverVm.connectionState.collectAsStateWithLifecycle()
    val channels by serverVm.channels.collectAsStateWithLifecycle()
    val users by serverVm.users.collectAsStateWithLifecycle()
    val rogerBeep by settings.rogerBeep.collectAsStateWithLifecycle(initialValue = false)
    val hardwarePtt by settings.hardwarePttEnabled.collectAsStateWithLifecycle(initialValue = false)
    val muted by serverVm.isOutputMuted.collectAsStateWithLifecycle()
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume) }
    var nickname by remember { mutableStateOf(prefs.getString("nickname", "") ?: "") }
    var rememberName by remember { mutableStateOf(prefs.getBoolean("remember_name", true)) }
    var connected by remember { mutableStateOf(false) }
    var pendingConnection by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(Page.PTT) }
    var query by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(false) }
    var pttDown by remember { mutableStateOf(false) }

    fun startConnection() {
        val name = nickname.trim()
        if (name.length < 2) return
        prefs.edit().putBoolean("remember_name", rememberName).apply()
        if (rememberName) prefs.edit().putString("nickname", name).apply()
        else prefs.edit().remove("nickname").apply()
        connectionVm.address.value = FIXED_HOST
        connectionVm.nickname.value = name
        connectionVm.password.value = ""
        connectionVm.channel.value = ""
        connectionVm.connect {
            connected = true
            pendingConnection = false
            serverVm.bindToService()
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) startConnection() else pendingConnection = false
    }
    fun login() {
        if (nickname.trim().length < 2) return
        pendingConnection = true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startConnection()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        // On relaunch reuse a live foreground service, or log in with the stored name.
        if (connectionVm.resumeExistingConnectionOnce({ connected = true; serverVm.bindToService() })) return@LaunchedEffect
        if (nickname.trim().length >= 2) login()
    }
    DisposableEffect(Unit) {
        onDispose { serverVm.setPushToTalk(false) }
    }

    LaunchedEffect(connectionState, connectionError) {
        if (connectionState == ConnectionState.DISCONNECTED && !connectionError.isNullOrBlank()) {
            pendingConnection = false
        }
    }

    val self = users.firstOrNull { it.id == serverVm.myClientId }
    val activeChannel = channels.firstOrNull { it.id == self?.channelId }
    val channelPeople = users.count { it.channelId == activeChannel?.id && activeChannel != null }
    val talking = users.firstOrNull { it.isTalking && it.id != serverVm.myClientId }
    val displayName = talking?.nickname ?: if (pttDown) nickname.trim() else "Niemand spreekt"
    val isLive = connected && liveState == ConnectionState.CONNECTED
    val shape = RoundedCornerShape(18.dp)

    MaterialTheme(colorScheme = darkColorScheme(primary = Blue, background = Night, surface = Panel, onSurface = White)) {
        Box(Modifier.fillMaxSize().background(Night)) {
            if (!connected) {
                Image(
                    painter = painterResource(R.drawable.ptt_direct_bg),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xB9001C38), Color(0x44020A12), Night))))
                Column(Modifier.fillMaxSize().systemBarsPadding().padding(25.dp)) {
                    Spacer(Modifier.height(43.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("PTT", fontSize = 47.sp, color = White, fontWeight = FontWeight.Black, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        Text("Direct", fontSize = 47.sp, color = Blue, fontWeight = FontWeight.Black, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    }
                    Text("A L T I J D   V E R B O N D E N", color = White, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Surface(color = Color(0xEB051320), shape = RoundedCornerShape(24.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = White, modifier = Modifier.size(35.dp))
                                Spacer(Modifier.width(15.dp))
                                Column {
                                    Text("Aanmelden", color = White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                                    Text("Voer je gebruikersnaam in om te verbinden", color = Grey, fontSize = 14.sp)
                                }
                            }
                            OutlinedTextField(
                                value = nickname,
                                onValueChange = { nickname = it.take(32) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Gebruikersnaam") },
                                leadingIcon = { Icon(Icons.Default.PersonOutline, contentDescription = null) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = White, unfocusedTextColor = White,
                                    focusedBorderColor = Blue, unfocusedBorderColor = Line,
                                    focusedContainerColor = PanelLight, unfocusedContainerColor = PanelLight,
                                    focusedLabelColor = Blue, unfocusedLabelColor = Grey
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Button(
                                onClick = { login() },
                                enabled = nickname.trim().length >= 2 && connectionState != ConnectionState.CONNECTING,
                                modifier = Modifier.fillMaxWidth().height(55.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Blue),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (connectionState == ConnectionState.CONNECTING || pendingConnection) {
                                    CircularProgressIndicator(Modifier.size(18.dp), color = White, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(10.dp))
                                }
                                Text("Verbinden", fontSize = 18.sp)
                            }
                            if (!connectionError.isNullOrBlank()) {
                                Text(connectionError ?: "Verbinding mislukt", color = Color(0xFFFF9A9A), fontSize = 12.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { rememberName = !rememberName }) {
                                Checkbox(checked = rememberName, onCheckedChange = { rememberName = it }, colors = CheckboxDefaults.colors(checkedColor = Blue))
                                Text("Naam onthouden", color = Grey, fontSize = 13.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(35.dp))
                }
            } else {
                Column(Modifier.fillMaxSize().systemBarsPadding()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(75.dp).padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (page == Page.PTT) {
                            Row {
                                Text("PTT ", color = White, fontSize = 27.sp, fontWeight = FontWeight.Black, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                Text("Direct", color = Blue, fontSize = 27.sp, fontWeight = FontWeight.Black, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            }
                        } else {
                            IconButton(onClick = { page = Page.PTT }) { Icon(Icons.Default.ArrowBack, contentDescription = "Terug", tint = White) }
                            Text(when (page) { Page.CHANNELS -> "Kanalen"; Page.USERS -> "Gebruikers"; else -> "Instellingen" }, color = White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = {
                            if (page == Page.PTT) page = Page.SETTINGS else if (page != Page.SETTINGS) {
                                searchVisible = !searchVisible
                                if (!searchVisible) query = ""
                            }
                        }) {
                            Icon(if (page == Page.PTT || page == Page.SETTINGS) Icons.Default.Settings else Icons.Default.Search, contentDescription = "Opties", tint = White)
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        when (page) {
                            Page.PTT -> {
                                Column(Modifier.fillMaxSize().padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().clickable { page = Page.CHANNELS },
                                        color = Panel, shape = shape,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Line)
                                    ) {
                                        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CellTower, contentDescription = null, tint = White, modifier = Modifier.size(40.dp))
                                            Spacer(Modifier.width(15.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(activeChannel?.name ?: "Kanalen laden...", color = White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text("$channelPeople gebruikers online", color = Grey, fontSize = 13.sp)
                                            }
                                            Icon(Icons.Default.ChevronRight, contentDescription = "Kanalen openen", tint = Grey)
                                        }
                                    }
                                    Spacer(Modifier.height(28.dp))
                                    Row(Modifier.fillMaxWidth().height(59.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(color = Color(0xFF00366A), shape = CircleShape, border = androidx.compose.foundation.BorderStroke(1.dp, Blue)) {
                                            Box(Modifier.size(57.dp), contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Mic, contentDescription = null, tint = Blue, modifier = Modifier.size(29.dp))
                                            }
                                        }
                                        Spacer(Modifier.width(14.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(displayName, color = White, fontSize = 19.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                            Text(if (talking != null || pttDown) "spreekt nu..." else if (isLive) "Verbonden" else "Opnieuw verbinden...", color = Grey, fontSize = 14.sp)
                                        }
                                        if (talking != null || pttDown) {
                                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                listOf(12, 24, 33, 40, 27, 18).forEach { h ->
                                                    Box(Modifier.width(4.dp).height(h.dp).background(Blue, CircleShape))
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.91f)
                                            .aspectRatio(1f)
                                            .shadow(if (pttDown) 25.dp else 13.dp, CircleShape)
                                            .background(Brush.radialGradient(listOf(Color(0xFF0D4D91), Blue, Color(0xFF002646))), CircleShape)
                                            .padding(10.dp)
                                            .border(2.dp, Color(0xFF64C5FF), CircleShape)
                                            .padding(8.dp)
                                            .background(Brush.verticalGradient(listOf(PanelLight, Night)), CircleShape)
                                            .pointerInput(isLive) {
                                                detectTapGestures(onPress = {
                                                    if (isLive) {
                                                        pttDown = true
                                                        serverVm.setPushToTalk(true)
                                                        try { tryAwaitRelease() } finally {
                                                            serverVm.setPushToTalk(false)
                                                            pttDown = false
                                                        }
                                                    }
                                                })
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.Mic, contentDescription = null, tint = if (pttDown) Green else Blue, modifier = Modifier.size(72.dp))
                                            Spacer(Modifier.height(8.dp))
                                            Text("PTT", color = White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                                            Text(if (pttDown) "JE BENT AAN HET PRATEN" else "VASTHOUDEN\nOM TE PRATEN", color = Grey, fontSize = 12.sp, lineHeight = 16.sp, textAlign = TextAlign.Center)
                                        }
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.VolumeUp, contentDescription = "Volume", tint = White)
                                        Slider(
                                            value = volume,
                                            onValueChange = {
                                                volume = it
                                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (it * maxVolume).toInt(), 0)
                                            },
                                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                                            colors = SliderDefaults.colors(thumbColor = Blue, activeTrackColor = Blue, inactiveTrackColor = Line)
                                        )
                                        Text("${(volume * 100).toInt()}%", color = Grey, fontSize = 15.sp)
                                    }
                                }
                            }
                            Page.CHANNELS -> {
                                Column(Modifier.fillMaxSize()) {
                                    if (searchVisible) {
                                        OutlinedTextField(value = query.trimStart(), onValueChange = { query = it }, label = { Text("Zoek kanaal") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
                                    }
                                    LazyColumn(contentPadding = PaddingValues(horizontal = 13.dp, vertical = 5.dp)) {
                                        items(channels.filter { it.name.contains(query.trim(), true) }, key = { it.id }) { channel ->
                                            val selected = channel.id == activeChannel?.id
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                                    .clip(RoundedCornerShape(13.dp))
                                                    .background(if (selected) Color(0xFF003C76) else Panel)
                                                    .border(1.dp, if (selected) Blue else Line, RoundedCornerShape(13.dp))
                                                    .clickable { if (!selected) { serverVm.moveToChannel(channel.id); page = Page.PTT } }
                                                    .padding(15.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.CellTower, contentDescription = null, tint = if (selected) Blue else White, modifier = Modifier.size(35.dp))
                                                Spacer(Modifier.width(14.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(channel.name, color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                                    Text("${users.count { it.channelId == channel.id }} gebruikers online", color = Grey, fontSize = 12.sp)
                                                }
                                                Icon(if (selected) Icons.Default.Check else Icons.Default.ChevronRight, contentDescription = null, tint = if (selected) Blue else Grey)
                                            }
                                        }
                                    }
                                }
                            }
                            Page.USERS -> {
                                Column(Modifier.fillMaxSize()) {
                                    if (searchVisible) {
                                        OutlinedTextField(value = query.trimStart(), onValueChange = { query = it }, label = { Text("Zoek gebruiker") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
                                    }
                                    LazyColumn(contentPadding = PaddingValues(horizontal = 15.dp, vertical = 8.dp)) {
                                        items(users.filter { it.nickname.contains(query.trim(), true) }.sortedBy { it.nickname.lowercase() }, key = { it.id }) { user ->
                                            val speaking = user.isTalking
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                                    .clip(RoundedCornerShape(13.dp))
                                                    .background(if (speaking) Color(0xFF003C76) else Night)
                                                    .border(if (speaking) 1.dp else 0.dp, if (speaking) Blue else Color.Transparent, RoundedCornerShape(13.dp))
                                                    .padding(horizontal = 12.dp, vertical = 13.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (speaking) Icon(Icons.Default.Mic, contentDescription = null, tint = Blue, modifier = Modifier.size(27.dp))
                                                else Box(Modifier.size(17.dp).background(Green, CircleShape))
                                                Spacer(Modifier.width(15.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(user.nickname, color = White, fontSize = 16.sp)
                                                    Text(if (speaking) "Spreekt nu..." else "Online", color = Grey, fontSize = 12.sp)
                                                }
                                                Icon(if (speaking) Icons.Default.GraphicEq else Icons.Default.ChevronRight, contentDescription = null, tint = if (speaking) Blue else Grey)
                                            }
                                            HorizontalDivider(color = Line.copy(alpha = 0.45f), thickness = 0.5.dp)
                                        }
                                    }
                                }
                            }
                            Page.SETTINGS -> {
                                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(19.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                                    item {
                                        Text("Mijn profiel", color = Grey, fontSize = 13.sp)
                                        Text(nickname, color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                                    }
                                    item { SettingSwitch("3-tonige rogerpiep", "Pieptoon wanneer je de PTT loslaat", rogerBeep) { scope.launch { settings.setRogerBeep(it) } } }
                                    item { SettingSwitch("Externe PTT-knop", "Fysieke PTT-toets gebruiken", hardwarePtt) { scope.launch { settings.setHardwarePttEnabled(it) } } }
                                    if (hardwarePtt) item {
                                        OutlinedButton(onClick = { HardwarePttController.startPairing() }, modifier = Modifier.fillMaxWidth()) { Text("PTT-knop koppelen", color = Blue) }
                                    }
                                    item { SettingSwitch("Luidspreker dempen", "Geen ontvangen audio", muted) { serverVm.toggleOutputMute() } }
                                    item {
                                        Text("Verbinding", color = Grey, fontSize = 13.sp)
                                        Text(if (isLive) "Verbonden" else "Verbinding herstellen...", color = if (isLive) Green else Grey)
                                        Text("De verbinding wordt automatisch beheerd.", color = Grey, fontSize = 12.sp)
                                    }
                                    item {
                                        OutlinedButton(onClick = {
                                            serverVm.setPushToTalk(false)
                                            serverVm.disconnect()
                                            prefs.edit().remove("nickname").apply()
                                            (context as? android.app.Activity)?.recreate()
                                        }, modifier = Modifier.fillMaxWidth()) {
                                            Icon(Icons.Default.Logout, contentDescription = null, tint = Blue)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Afmelden / andere naam", color = Blue)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Surface(color = Panel, shape = RoundedCornerShape(topStart = 17.dp, topEnd = 17.dp)) {
                        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(74.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            TabItem("PTT", Page.PTT, page, Icons.Default.Mic) { page = Page.PTT; query = ""; searchVisible = false }
                            TabItem("Kanalen", Page.CHANNELS, page, Icons.Default.List) { page = Page.CHANNELS; query = ""; searchVisible = false }
                            TabItem("Gebruikers", Page.USERS, page, Icons.Default.People) { page = Page.USERS; query = ""; searchVisible = false }
                            TabItem("Instellingen", Page.SETTINGS, page, Icons.Default.Settings) { page = Page.SETTINGS; query = ""; searchVisible = false }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, description: String, checked: Boolean, change: (Boolean) -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(15.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(description, color = Grey, fontSize = 12.sp)
            }
            Switch(checked = checked, onCheckedChange = change)
        }
    }
}

@Composable
private fun TabItem(label: String, target: Page, selected: Page, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val active = selected == target
    Column(
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .background(if (active) Color(0xFF002E62) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = if (active) Blue else Grey, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (active) Blue else Grey, fontSize = 10.sp)
    }
}
