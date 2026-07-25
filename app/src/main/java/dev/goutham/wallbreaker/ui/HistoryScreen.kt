package dev.goutham.wallbreaker.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goutham.wallbreaker.Route
import dev.goutham.wallbreaker.SyncStatus
import dev.goutham.wallbreaker.db.ShareEntry
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    vm: HomeViewModel,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val entries by vm.entries.collectAsStateWithLifecycle()
    val total by vm.total.collectAsStateWithLifecycle()
    val unlocks by vm.unlocks.collectAsStateWithLifecycle()
    val configured by vm.configured.collectAsStateWithLifecycle()

    val appBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(appBarState)

    var sheetFor by remember { mutableStateOf<ShareEntry?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = {
                    Column {
                        Text("Wallbreaker", style = MaterialTheme.typography.headlineMedium)
                        if (total > 0) {
                            Text(
                                statLine(total, unlocks),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            EmptyState(configured = configured, onConnect = onOpenSettings, modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (!configured) {
                    item { NotConnectedBanner(onFix = onOpenSettings) }
                    item { Spacer(Modifier.height(8.dp)) }
                }
                val rows = buildRows(entries)
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is HistoryRow.Header ->
                            Text(
                                row.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .animateItem()
                                    .padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
                            )
                        is HistoryRow.Receipt ->
                            ReceiptItem(
                                entry = row.entry,
                                shape = shapeFor(row.isFirst, row.isLast),
                                onClick = {
                                    if (row.entry.status == SyncStatus.FAILED.name) vm.retry(row.entry.id)
                                    else openUrl(context, row.entry.url)
                                },
                                onLongClick = { sheetFor = row.entry },
                                modifier = Modifier.animateItem(),
                            )
                    }
                }
            }
        }
    }

    sheetFor?.let { entry ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { sheetFor = null }, sheetState = sheetState) {
            ActionSheet(
                entry = entry,
                onOpen = { openUrl(context, entry.url); sheetFor = null },
                onOpenInstapaper = { openInstapaper(context); sheetFor = null },
                onCopy = { copy(context, entry.url); sheetFor = null },
                onSaveAgain = { vm.retry(entry.id); sheetFor = null },
                onRemove = { vm.delete(entry); sheetFor = null },
            )
        }
    }
}

// --- item -----------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReceiptItem(
    entry: ShareEntry,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val failed = entry.status == SyncStatus.FAILED.name
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = shape,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Monogram(entry)
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title?.takeIf { it.isNotBlank() } ?: prettyUrl(entry.url),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                MetaLine(entry, failed)
            }
            StatusTrailing(entry)
        }
    }
}

@Composable
private fun Monogram(entry: ShareEntry) {
    val letter = (entry.host ?: entry.title ?: "?").trimStart().firstOrNull()?.uppercaseChar() ?: '?'
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxSize()) {}
        Text(
            letter.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun MetaLine(entry: ShareEntry, failed: Boolean) {
    val route = runCatching { Route.valueOf(entry.route) }.getOrNull()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RouteGlyph(route)
        val time = relativeTime(entry.createdAt)
        val fullText = route == Route.FREEDIUM_CONTENT || route == Route.HTML_CONTENT
        val text = when {
            failed -> "Sync failed — tap to retry"
            fullText -> "${entry.host ?: "link"} · full text · $time"
            else -> "${entry.host ?: "link"} · $time"
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RouteGlyph(route: Route?) {
    val tertiary = MaterialTheme.colorScheme.tertiary
    when (route) {
        Route.FREEDIUM_CONTENT, Route.FREEDIUM_WRAP ->
            RouteArrow(modifier = Modifier.size(14.dp), tint = tertiary)
        Route.HTML_CONTENT ->
            Icon(Icons.Outlined.Description, contentDescription = "Full text", modifier = Modifier.size(14.dp), tint = tertiary)
        else ->
            Icon(Icons.Outlined.Link, contentDescription = "Link", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusTrailing(entry: ShareEntry) {
    when (entry.status) {
        SyncStatus.SYNCED.name -> Unit    // a quiet row is success
        SyncStatus.SYNCING.name ->
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        SyncStatus.FAILED.name ->
            Icon(Icons.Outlined.CloudOff, contentDescription = "Failed", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
        else ->   // PENDING / queued
            Icon(Icons.Outlined.CloudQueue, contentDescription = "Queued", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
    }
}

// --- empty + banner -------------------------------------------------------

@Composable
private fun EmptyState(configured: Boolean, onConnect: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        WallMark(modifier = Modifier.size(96.dp))
        Spacer(Modifier.height(24.dp))
        Text("No saves yet", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Share any article from your browser or the Medium app, then pick Wallbreaker in the share sheet. It lands here — and in Instapaper.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!configured) {
            Spacer(Modifier.height(24.dp))
            FilledTonalButton(onClick = onConnect) { Text("Connect Instapaper") }
        }
    }
}

@Composable
private fun NotConnectedBanner(onFix: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Instapaper isn't connected.", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                Text("New saves will wait on this device until you reconnect.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            TextButton(onClick = onFix) { Text("Fix") }
        }
    }
}

// --- action sheet ---------------------------------------------------------

@Composable
private fun ActionSheet(
    entry: ShareEntry,
    onOpen: () -> Unit,
    onOpenInstapaper: () -> Unit,
    onCopy: () -> Unit,
    onSaveAgain: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(
            entry.title?.takeIf { it.isNotBlank() } ?: prettyUrl(entry.url),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        SheetAction(Icons.AutoMirrored.Outlined.OpenInNew, "Open article", onOpen)
        SheetAction(Icons.AutoMirrored.Outlined.OpenInNew, "Open in Instapaper", onOpenInstapaper)
        SheetAction(Icons.Outlined.ContentCopy, "Copy link", onCopy)
        SheetAction(Icons.Outlined.Refresh, "Save again", onSaveAgain)
        SheetAction(Icons.Outlined.Delete, "Remove from history", onRemove, subtitle = "Doesn't touch Instapaper.")
    }
}

@Composable
private fun SheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    subtitle: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickableNoLong(onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableNoLong(onClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick)

// --- grouping + formatting ------------------------------------------------

private sealed interface HistoryRow {
    val key: String
    data class Header(val label: String) : HistoryRow { override val key = "h:$label" }
    data class Receipt(val entry: ShareEntry, val isFirst: Boolean, val isLast: Boolean) : HistoryRow {
        override val key = "r:${entry.id}"
    }
}

private fun buildRows(entries: List<ShareEntry>): List<HistoryRow> {
    val out = ArrayList<HistoryRow>()
    var currentBucket: String? = null
    val groups = LinkedHashMap<String, MutableList<ShareEntry>>()
    for (e in entries) {
        val bucket = dayBucket(e.createdAt)
        groups.getOrPut(bucket) { ArrayList() }.add(e)
    }
    for ((bucket, groupEntries) in groups) {
        out.add(HistoryRow.Header(bucket))
        groupEntries.forEachIndexed { i, e ->
            out.add(HistoryRow.Receipt(e, isFirst = i == 0, isLast = i == groupEntries.lastIndex))
        }
    }
    return out
}

private fun shapeFor(isFirst: Boolean, isLast: Boolean): RoundedCornerShape {
    val big = 16.dp
    val small = 4.dp
    return when {
        isFirst && isLast -> RoundedCornerShape(big)
        isFirst -> RoundedCornerShape(topStart = big, topEnd = big, bottomStart = small, bottomEnd = small)
        isLast -> RoundedCornerShape(topStart = small, topEnd = small, bottomStart = big, bottomEnd = big)
        else -> RoundedCornerShape(small)
    }
}

private fun statLine(total: Int, unlocks: Int): String {
    val saves = if (total == 1) "1 saved" else "$total saved"
    val walls = if (unlocks == 1) "1 wall broken" else "$unlocks walls broken"
    return "$saves · $walls"
}

private fun dayBucket(ts: Long): String {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
    val today = Instant.now().atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(day, today)
    return when {
        days <= 0 -> "Today"
        days == 1L -> "Yesterday"
        days < 7 -> "This week"
        else -> day.month.name.lowercase().replaceFirstChar { it.uppercase() } + " " + day.year
    }
}

private fun relativeTime(ts: Long): String {
    val now = System.currentTimeMillis()
    val diff = (now - ts).coerceAtLeast(0)
    val min = diff / 60_000
    val hr = diff / 3_600_000
    val day = diff / 86_400_000
    return when {
        min < 1 -> "just now"
        min < 60 -> "${min}m ago"
        hr < 24 -> "${hr}h ago"
        day < 7 -> "${day}d ago"
        else -> "${day / 7}w ago"
    }
}

private fun prettyUrl(url: String): String =
    url.removePrefix("https://").removePrefix("http://").removePrefix("www.").take(80)

// --- actions --------------------------------------------------------------

private fun openUrl(context: Context, url: String) {
    if (!url.startsWith("http")) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun openInstapaper(context: Context) {
    val pm = context.packageManager
    val launch = pm.getLaunchIntentForPackage("com.instapaper.android")
    if (launch != null) {
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(launch) }
    } else {
        openUrl(context, "https://www.instapaper.com/u")
    }
}

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("link", text))
}
