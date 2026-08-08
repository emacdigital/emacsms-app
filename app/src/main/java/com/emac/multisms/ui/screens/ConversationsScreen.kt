package com.emac.multisms.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.emac.multisms.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConversationsScreen(vm: MainViewModel) {
    val history by vm.history.collectAsState()
    val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Historique des envois", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (history.isEmpty()) {
            Text("Aucun envoi pour le moment.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(history, key = { it.id }) { entry ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (entry.success) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32))
                        } else {
                            Icon(Icons.Default.Error, null, tint = Color(0xFFB00020))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.name.ifBlank { entry.phone }, style = MaterialTheme.typography.bodyLarge)
                            Text(entry.body.take(60), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Text(
                            fmt.format(Date(entry.sentAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
