package dev.goutham.wallbreaker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable
fun ConfigScreen() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    // Deliberately `remember`, NOT rememberSaveable: the password must never be
    // written into the saved-instance-state Bundle (which can hit disk).
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var configured by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { CredentialStore.load(context) }?.let {
            username = it.username
            configured = true
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Instapaper account", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = {
                if (configured) Text("Credentials are saved on this device. Saving replaces them.")
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        // Empty password is saved as-is: valid for some Instapaper accounts.
                        CredentialStore.save(context, username.trim(), password)
                        configured = true
                        status = "Saved"
                    }
                },
                enabled = username.isNotBlank(),
            ) { Text("Save") }
            OutlinedButton(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        status = "Verifying…"
                        status = try {
                            val creds = CredentialStore.load(context)
                            when {
                                creds == null -> "Nothing saved yet"
                                InstapaperClient.authenticate(creds) -> "Credentials valid"
                                else -> "Rejected by Instapaper (403)"
                            }
                        } catch (e: IOException) {
                            "Network error: ${e.message}"
                        }
                    }
                },
                enabled = configured,
            ) { Text("Verify") }
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}
