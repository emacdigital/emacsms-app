package com.emac.multisms.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.emac.multisms.data.MessageTemplate
import com.emac.multisms.ui.MainViewModel
import com.emac.multisms.util.SmsText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(vm: MainViewModel) {
    val messages by vm.messages.collectAsState()
    val selectedId by vm.selectedMessageId.collectAsState()
    val selected = messages.firstOrNull { it.id == selectedId }

    var draft by remember(selected?.id) { mutableStateOf(selected?.body ?: "") }
    var title by remember(selected?.id) { mutableStateOf(selected?.title ?: "") }
    var showNew by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mes messages", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = { showNew = true }) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Nouveau")
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages, key = { it.id }) { m ->
                FilterChip(
                    selected = m.id == selectedId,
                    onClick = { vm.selectMessage(m.id) },
                    label = { Text(m.title.ifBlank { "Message" }) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (selected != null) {
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Titre") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = draft, onValueChange = { draft = it },
                label = { Text("Contenu du message") },
                modifier = Modifier.fillMaxWidth().weight(1f),
                supportingText = {
                    Text("${draft.length} caractères · ${SmsText.parts(draft)} SMS")
                }
            )
            Text(
                "Astuce : utilise {nom} pour insérer le nom du contact.",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray
            )
            Spacer(Modifier.height(8.dp))
            Row {
                Button(
                    onClick = { vm.updateMessage(selected.copy(title = title, body = draft)) },
                    modifier = Modifier.weight(1f)
                ) { Text("Enregistrer") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { vm.deleteMessage(selected.id) }) {
                    Icon(Icons.Default.Delete, null)
                }
            }
        } else {
            Spacer(Modifier.height(24.dp))
            Text("Crée un message pour commencer.", style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (showNew) {
        TextInputDialog(
            title = "Nouveau message",
            label = "Titre",
            confirm = "Créer",
            onConfirm = { name -> vm.createMessage(name, ""); showNew = false },
            onDismiss = { showNew = false }
        )
    }
}
