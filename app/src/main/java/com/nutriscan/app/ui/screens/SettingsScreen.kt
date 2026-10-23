package com.nutriscan.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutriscan.app.data.AiModel
import com.nutriscan.app.data.AiProvider
import com.nutriscan.app.ui.MainViewModel

@Composable
fun SettingsScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var claudeKey by rememberSaveable { mutableStateOf("") }
    var geminiKey by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var confirmWipe by rememberSaveable { mutableStateOf(false) }
    val uri = LocalUriHandler.current

    LaunchedEffect(settings) {
        val s = settings
        if (s != null && !loaded) { claudeKey = s.apiKey; geminiKey = s.geminiKey; loaded = true }
    }

    val provider = settings?.provider ?: AiProvider.CLAUDE
    val key = if (provider == AiProvider.GEMINI) geminiKey else claudeKey
    val savedKey = settings?.keyFor(provider).orEmpty()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 16.dp, end = 16.dp,
                top = contentPadding.calculateTopPadding() + 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium)

        Text("Inteligencia artificial", style = MaterialTheme.typography.titleMedium)
        Text(
            "Elige qué IA analiza tus fotos. Puedes guardar una API key para cada una y cambiar cuando quieras.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiProvider.entries.forEach { p ->
                FilterChip(
                    selected = provider == p,
                    onClick = { vm.saveProvider(p) },
                    label = { Text("${p.label} (${p.company})") },
                )
            }
        }
        Text(
            when (provider) {
                AiProvider.CLAUDE -> "Crea una API key en console.anthropic.com (necesita saldo en la cuenta) y pégala aquí."
                AiProvider.GEMINI -> "Crea una API key gratis en Google AI Studio con tu cuenta de Google y pégala aquí. " +
                    "Gemini Flash tiene un nivel gratuito con un límite de usos por minuto y por día."
            } + " Se guarda solo en este móvil.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = key,
            onValueChange = { if (provider == AiProvider.GEMINI) geminiKey = it else claudeKey = it },
            label = { Text("API key de ${provider.label}") },
            placeholder = { Text(provider.keyHint) },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Mostrar")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.saveApiKey(provider, key) }, enabled = key.trim() != savedKey) {
                Text(if (key.trim() == savedKey && key.isNotBlank()) "Guardada ✓" else "Guardar")
            }
            OutlinedButton(onClick = { uri.openUri(provider.keyUrl) }) {
                Text("Conseguir API key")
            }
        }

        Text("Modelo", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            AiModel.forProvider(provider).forEach { m ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = settings?.model == m, onClick = { vm.saveModel(m) })
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings?.model == m, onClick = null)
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(m.label, style = MaterialTheme.typography.bodyLarge)
                        Text(m.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Text("Privacidad y datos", style = MaterialTheme.typography.titleMedium)
        Text(
            "Tu diario, tu perfil y tus fotos se guardan únicamente en este dispositivo. " +
                "Al escanear, la foto se envía a la IA que elijas (Anthropic o Google) solo para analizarla.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { confirmWipe = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text("Borrar todos mis datos") }
    }

    if (confirmWipe) {
        ConfirmDialog(
            title = "¿Borrar todo?",
            text = "Se eliminarán tu diario, tus fotos, tu plan y las API keys. No se puede deshacer.",
            confirm = "Borrar todo",
            onConfirm = { vm.deleteAllData(); claudeKey = ""; geminiKey = ""; confirmWipe = false },
            onDismiss = { confirmWipe = false },
        )
    }
}
