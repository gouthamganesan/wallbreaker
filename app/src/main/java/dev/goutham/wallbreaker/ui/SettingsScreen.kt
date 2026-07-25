package dev.goutham.wallbreaker.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.goutham.wallbreaker.AppSettings
import dev.goutham.wallbreaker.AppSettingsStore
import dev.goutham.wallbreaker.CredentialStore
import dev.goutham.wallbreaker.FullApiAuth
import dev.goutham.wallbreaker.InstapaperClient
import dev.goutham.wallbreaker.InstapaperFullApi
import dev.goutham.wallbreaker.UrlExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val REPO_URL = "https://github.com/gouthamganesan/wallbreaker"
private const val OAUTH_REQUEST_URL = "https://www.instapaper.com/main/request_oauth_consumer_token"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            AccountSection(context, scope)
            FullTextUnlockSection(context, scope)
            FreediumRoutingSection(context)
            AdvancedSection(context)
            Footer(context)
        }
    }
}

// --- group scaffold -------------------------------------------------------

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

// --- 1. account -----------------------------------------------------------

@Composable
private fun AccountSection(context: Context, scope: kotlinx.coroutines.CoroutineScope) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var configured by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { CredentialStore.load(context) }?.let {
            username = it.username
            configured = true
        }
    }

    SettingsGroup("Instapaper account") {
        OutlinedTextField(
            value = username, onValueChange = { username = it },
            label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = { if (configured) Text("Saved on this device. Saving replaces it.") },
        )
        Button(
            onClick = {
                scope.launch(Dispatchers.IO) {
                    status = "Saving…"
                    CredentialStore.save(context, username.trim(), password)
                    configured = true
                    // A new password invalidates any cached Full API token.
                    CredentialStore.clearOAuthToken(context)
                    status = try {
                        val creds = CredentialStore.load(context)
                        when {
                            creds == null -> "Nothing saved"
                            InstapaperClient.authenticate(creds) -> "Connected as ${creds.username}"
                            else -> "Rejected by Instapaper — check your password."
                        }
                    } catch (e: Exception) {
                        "Network error — saved, but couldn't verify."
                    }
                }
            },
            enabled = username.isNotBlank(),
        ) { Text("Save & verify") }
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

// --- 2. full-text unlock --------------------------------------------------

@Composable
private fun FullTextUnlockSection(context: Context, scope: kotlinx.coroutines.CoroutineScope) {
    var consumerKey by remember { mutableStateOf("") }
    var consumerSecret by remember { mutableStateOf("") }
    var secretVisible by remember { mutableStateOf(false) }
    var hasApp by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { CredentialStore.loadConsumerApp(context) }?.let {
            consumerKey = it.consumerKey
            hasApp = true
        }
    }

    Column {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Full-text unlock", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            StatusPill(active = hasApp)
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = consumerKey, onValueChange = { consumerKey = it },
                    label = { Text("Consumer key") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = consumerSecret, onValueChange = { consumerSecret = it },
                    label = { Text("Consumer secret") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { secretVisible = !secretVisible }) {
                            Icon(
                                if (secretVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (secretVisible) "Hide" else "Show",
                            )
                        }
                    },
                )
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            status = "Verifying…"
                            CredentialStore.saveConsumerApp(context, consumerKey.trim(), consumerSecret.trim())
                            status = try {
                                val creds = FullApiAuth.resolve(context)
                                if (creds == null) {
                                    "Add your Instapaper account above first."
                                } else {
                                    val user = InstapaperFullApi.verifyCredentials(creds)
                                    hasApp = true
                                    "Active — full text as ${user.username}"
                                }
                            } catch (e: Exception) {
                                "Couldn't verify — check the keys and your password."
                            }
                        }
                    },
                    enabled = consumerKey.isNotBlank() && consumerSecret.isNotBlank(),
                ) { Text("Save & verify keys") }
                status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text(
                    "With an Instapaper API app, Wallbreaker uploads the unlocked article text itself, so your bookmark keeps the clean original link instead of a mirror URL. It's also what saves raw HTML files.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { openUrl(context, OAUTH_REQUEST_URL) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text("Request keys from Instapaper →")
                }
            }
        }
    }
}

@Composable
private fun StatusPill(active: Boolean) {
    val bg = if (active) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (active) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(color = bg, shape = androidx.compose.foundation.shape.RoundedCornerShape(50)) {
        Text(
            if (active) "Active" else "Off — saving links",
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

// --- 3. freedium routing --------------------------------------------------

@Composable
private fun FreediumRoutingSection(context: Context) {
    var settings by remember { mutableStateOf(AppSettings()) }
    var domainInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        settings = withContext(Dispatchers.IO) { AppSettingsStore.load(context) }
    }

    fun reload() { settings = AppSettingsStore.load(context) }
    val extracted = UrlExtractor.domainFromInput(domainInput)

    SettingsGroup("Freedium routing") {
        Text(
            "Links from these domains are unlocked through Freedium before saving.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        settings.freediumDomains.forEach { domain ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(domain, style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { AppSettingsStore.removeDomain(context, domain); reload() }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Remove $domain")
                }
            }
        }
        OutlinedTextField(
            value = domainInput,
            onValueChange = { domainInput = it },
            label = { Text("Paste a link or type a domain") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (extracted != null) {
                    IconButton(onClick = { AppSettingsStore.addDomain(context, extracted); domainInput = ""; reload() }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add $extracted", tint = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    IconButton(onClick = { pasteInto(context) { domainInput = it } }) {
                        Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste")
                    }
                }
            },
            supportingText = {
                when {
                    domainInput.isBlank() -> {}
                    extracted != null -> Text("Adds $extracted", color = MaterialTheme.colorScheme.primary)
                    else -> Text("That doesn't look like a link.", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}

// --- 4. advanced ----------------------------------------------------------

@Composable
private fun AdvancedSection(context: Context) {
    var expanded by remember { mutableStateOf(false) }
    var mirror by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { mirror = AppSettingsStore.load(context).freediumMirror }

    SettingsGroup("Advanced") {
        TextButton(onClick = { expanded = !expanded }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text(if (expanded) "Hide Freedium mirror" else "Freedium mirror")
        }
        if (expanded) {
            OutlinedTextField(
                value = mirror, onValueChange = { mirror = it },
                label = { Text("Mirror base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { AppSettingsStore.setMirror(context, mirror) }) { Text("Save") }
                TextButton(onClick = { AppSettingsStore.setMirror(context, AppSettings.DEFAULT_MIRROR); mirror = AppSettings.DEFAULT_MIRROR }) {
                    Text("Reset to default")
                }
            }
            Text(
                "The mirror's address changes occasionally. If unlocks start failing, check the Freedium project for the current host.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Footer(context: Context) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(onClick = { openUrl(context, REPO_URL) }) {
            Text("Wallbreaker · Made in Chennai for one reading list", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Spacer(Modifier.height(8.dp))
    }
}

// --- helpers --------------------------------------------------------------

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun pasteInto(context: Context, set: (String) -> Unit) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val text = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
    if (!text.isNullOrBlank()) set(text)
}
