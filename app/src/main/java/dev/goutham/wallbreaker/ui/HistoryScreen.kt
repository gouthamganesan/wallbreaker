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
import androidx.compose.material.icons.outlined.LockOpen
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
import dev.goutham.wallbreaker.AppSettingsStore
import dev.goutham.wallbreaker.Freedium
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
        val bump by vm.allowlistBump.collectAsStateWithLifecycle()
        // Re-read on every bump: the allowlist is SharedPreferences, so adding a
        // domain from this very sheet is not otherwise observable.
        val unlockDomain = remember(entry.id, bump) {
            Freedium.unlockCandidate(entry.url, AppSettingsStore.load(context))
        }
        ModalBottomSheet(onDismissRequest = { sheetFor = null }, sheetState = sheetState) {
            ActionSheet(
                entry = entry,
                unlockDomain = unlockDomain,
                onOpen = { openUrl(context, entry.url); sheetFor = null },
                onOpenViaFreedium = { openUrl(context, freediumUrlFor(context, entry.url)); sheetFor = null },
                onOpenInstapaper = { openInstapaper(context); sheetFor = null },
                onCopy = { copy(context, entry.url); sheetFor = null },
                onUnlockDomain = { vm.unlockDomain(it) },
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
    unlockDomain: String?,
    onOpen: () -> Unit,
    onOpenViaFreedium: () -> Unit,
    onOpenInstapaper: () -> Unit,
    onCopy: () -> Unit,
    onUnlockDomain: (String) -> Unit,
    onSaveAgain: () -> Unit,
    onRemove: () -> Unit,
) {
    // Survives [unlockDomain] going null the moment the domain is added, which is
    // what lets the row report what it did instead of silently vanishing.
    var added by remember(entry.id) { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(
            entry.title?.takeIf { it.isNotBlank() } ?: prettyUrl(entry.url),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        SheetAction(Icons.AutoMirrored.Outlined.OpenInNew, "Open article", onOpen)
        // Offered for every entry, not just routed ones: the reason to reach for
        // this is usually that the original turned out to be paywalled after all.
        SheetAction(
            Icons.Outlined.LockOpen,
            "Open via Freedium",
            onOpenViaFreedium,
            subtitle = "Reads the unlocked version in your browser.",
        )
        // The standing fix, next to the one-off one. History is where you notice
        // that a domain keeps needing this, rather than that one article did.
        when {
            added != null -> SheetAction(
                Icons.Outlined.LockOpen,
                "$added added",
                onClick = {},
                subtitle = "Links from this domain unlock from now on. \"Save again\" redelivers this one.",
                enabled = false,
            )
            unlockDomain != null -> SheetAction(
                Icons.Outlined.LockOpen,
                "Always unlock $unlockDomain",
                onClick = { added = unlockDomain; onUnlockDomain(unlockDomain) },
                subtitle = "Adds it to the Freedium list in Settings.",
            )
        }
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
    enabled: Boolean = true,
) {
    val tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.combinedClickableNoLong(onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Column {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.tertiary,
            )
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

/**
 * The mirror URL for [url], using the mirror the user has configured. Already-
 * wrapped links are returned as-is so the action is safe to hit twice.
 */
private fun freediumUrlFor(context: Context, url: String): String {
    val mirror = AppSettingsStore.load(context).freediumMirror
    return if (Freedium.isAtMirror(url, mirror)) url else Freedium.wrap(url, mirror)
}

/**
 * Open the Instapaper *app*, falling back to the web only when it really isn't
 * installed.
 *
 * There is deliberately no per-article deep link: Instapaper's Android build
 * registers no http/https intent filter and no custom scheme (its only
 * activities are MainActivity and a SEND/VIEW handler for text/plain and
 * application/pdf), so `instapaper.com/read/<id>` resolves to a browser, not
 * the app. Landing in the app's own list is the closest thing available.
 */
private fun openInstapaper(context: Context) {
    val pm = context.packageManager
    val launch = pm.getLaunchIntentForPackage(INSTAPAPER_PKG)
        // Belt and braces: if package visibility still hides the launch intent,
        // an explicit component still starts an exported activity.
        ?: Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(INSTAPAPER_PKG, "$INSTAPAPER_PKG.MainActivity")
        }
    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val started = runCatching { context.startActivity(launch) }.isSuccess
    if (!started) openUrl(context, "https://www.instapaper.com/u")
}

private const val INSTAPAPER_PKG = "com.instapaper.android"

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("link", text))
}
