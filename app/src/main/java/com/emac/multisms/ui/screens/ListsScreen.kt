package com.emac.multisms.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.emac.multisms.data.Contact
import com.emac.multisms.data.SendStatus
import com.emac.multisms.data.SendingList
import com.emac.multisms.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(vm: MainViewModel, snackbar: (String) -> Unit) {
    val lists by vm.lists.collectAsState()
    val contacts by vm.contacts.collectAsState()
    val selectedListId by vm.selectedListId.collectAsState()
    val selectedList = lists.firstOrNull { it.id == selectedListId }

    var search by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showAddContact by remember { mutableStateOf(false) }
    var pendingFileName by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = pendingFileName ?: "Import fichier"
            vm.importFromFile(uri, name) { count, skipped ->
                snackbar("$count contacts importés" + if (skipped > 0) " · $skipped ignorés (doublons/invalides)" else "")
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> if (uri != null) vm.exportContacts(uri) { n -> snackbar("$n contacts exportés") } }

    Column(Modifier.fillMaxSize().padding(16.dp)) {

        // Onglets des listes (défilement horizontal)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            items(lists, key = { it.id }) { l ->
                FilterChip(
                    selected = l.id == selectedListId,
                    onClick = { vm.selectList(l.id) },
                    label = { Text(l.name) }
                )
            }
            item {
                AssistChip(
                    onClick = { showCreateDialog = true },
                    label = { Text("Nouvelle") },
                    leadingIcon = { Icon(Icons.Default.Add, null) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Actions d'import
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = {
                    vm.importFromDevice("Contacts du téléphone") { count, skipped ->
                        snackbar("$count contacts importés" + if (skipped > 0) " · $skipped ignorés" else "")
                    }
                },
                label = { Text("Téléphone") },
                leadingIcon = { Icon(Icons.Default.Contacts, null) }
            )
            AssistChip(
                onClick = { pendingFileName = "Import fichier"; filePicker.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "application/vnd.ms-excel", "*/*")) },
                label = { Text("Fichier CSV") },
                leadingIcon = { Icon(Icons.Default.UploadFile, null) }
            )
            AssistChip(
                onClick = { showAddContact = true },
                label = { Text("Manuel") },
                leadingIcon = { Icon(Icons.Default.PersonAdd, null) }
            )
        }

        if (selectedList != null) {
            Spacer(Modifier.height(12.dp))

            // Compteur + actions de sélection
            val checked = contacts.count { it.selected }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cochés : $checked / ${contacts.size}", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { vm.setAllSelected(true) }) { Text("Tout") }
                TextButton(onClick = { vm.setAllSelected(false) }) { Text("Aucun") }
                IconButton(onClick = { exportLauncher.launch("contacts.csv") }) {
                    Icon(Icons.Default.Download, "Exporter en CSV")
                }
                IconButton(onClick = { showRenameDialog = true }) { Icon(Icons.Default.Edit, "Renommer") }
                IconButton(onClick = { vm.deleteList(selectedList.id); snackbar("Liste supprimée") }) {
                    Icon(Icons.Default.Delete, "Supprimer la liste")
                }
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Rechercher un contact…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            val filtered = contacts.filter {
                search.isBlank() || it.name.contains(search, true) || it.phone.contains(search)
            }
            LazyColumn(Modifier.weight(1f)) {
                items(filtered, key = { it.id }) { c ->
                    ContactRow(
                        contact = c,
                        onToggle = { vm.toggleContact(c) },
                        onDelete = { vm.deleteContact(c) }
                    )
                    HorizontalDivider()
                }
            }
        } else {
            Spacer(Modifier.height(24.dp))
            Text(
                "Crée ou choisis une liste, puis importe des contacts.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    // Dialogues
    if (showCreateDialog) {
        TextInputDialog(
            title = "Nouvelle liste",
            label = "Nom de la liste",
            confirm = "Créer",
            onConfirm = { name -> vm.createList(name); showCreateDialog = false },
            onDismiss = { showCreateDialog = false }
        )
    }
    if (showRenameDialog && selectedList != null) {
        TextInputDialog(
            title = "Renommer la liste",
            label = "Nouveau nom",
            initial = selectedList.name,
            confirm = "Enregistrer",
            onConfirm = { name -> vm.renameList(selectedList.id, name); showRenameDialog = false },
            onDismiss = { showRenameDialog = false }
        )
    }
    if (showAddContact) {
        AddContactDialog(
            onConfirm = { name, phone -> vm.addContact(name, phone); showAddContact = false },
            onDismiss = { showAddContact = false }
        )
    }
}

@Composable
private fun ContactRow(contact: Contact, onToggle: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onToggle() }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = contact.selected, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(contact.name, style = MaterialTheme.typography.bodyLarge)
            Text(contact.phone, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        StatusBadge(contact.status)
        IconButton(onClick = onDelete) { Icon(Icons.Default.Close, "Supprimer", tint = Color.Gray) }
    }
}

@Composable
fun StatusBadge(status: String) {
    val (label, color) = when (status) {
        SendStatus.SENT -> "Envoyé" to Color(0xFF0B6E4F)
        SendStatus.FAILED -> "Échec" to Color(0xFFB00020)
        else -> "En attente" to Color(0xFF9E9E9E)
    }
    Text(label, color = color, style = MaterialTheme.typography.labelSmall)
    Spacer(Modifier.width(4.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String = "",
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value, onValueChange = { value = it },
                label = { Text(label) }, singleLine = true
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddContactDialog(onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter un contact") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text("Numéro") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (phone.isNotBlank()) onConfirm(name, phone) }) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
