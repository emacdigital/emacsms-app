package com.emac.multisms.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emac.multisms.sms.SendPhase
import com.emac.multisms.ui.MainViewModel
import com.emac.multisms.util.SmsText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(vm: MainViewModel, snackbar: (String) -> Unit) {
    val lists by vm.lists.collectAsState()
    val messages by vm.messages.collectAsState()
    val selectedListId by vm.selectedListId.collectAsState()
    val selectedMessageId by vm.selectedMessageId.collectAsState()
    val state by vm.sendState.collectAsState()
    val contacts by vm.contacts.collectAsState()
    val session by vm.session.collectAsState()
    val context = LocalContext.current

    val currentList = lists.firstOrNull { it.id == selectedListId }
    val currentMessage = messages.firstOrNull { it.id == selectedMessageId }

    var delaySeconds by remember { mutableIntStateOf(2) }
    var resetFirst by remember { mutableStateOf(true) }
    var personalize by remember { mutableStateOf(true) }
    var scheduleOn by remember { mutableStateOf(false) }
    var scheduledAt by remember { mutableStateOf<Long?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showFullMessage by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    val sims = remember { vm.sims }
    var selectedSubId by remember { mutableIntStateOf(-1) }
    var simMenu by remember { mutableStateOf(false) }
    val currentSim = sims.firstOrNull { it.subscriptionId == selectedSubId } ?: sims.first()

    val running = state.phase == SendPhase.RUNNING || state.phase == SendPhase.PAUSED
    val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy à HH:mm", Locale.FRANCE) }
    val progress = if (state.total == 0) 0f else state.processed / state.total.toFloat()

    val selectedContacts = contacts.filter { it.selected }
    val estimatedCost = remember(selectedContacts, currentMessage, personalize) {
        val body = currentMessage?.body ?: ""
        if (body.isBlank()) 0 else selectedContacts.sumOf { c ->
            val t = if (personalize) SmsText.personalize(body, c.name) else body
            SmsText.parts(t).coerceAtLeast(1)
        }
    }

    fun pickDateTime() {
        val c = Calendar.getInstance()
        DatePickerDialog(context, { _, y, m, d ->
            TimePickerDialog(context, { _, h, min ->
                val cal = Calendar.getInstance().apply { set(y, m, d, h, min, 0); set(Calendar.MILLISECOND, 0) }
                scheduledAt = cal.timeInMillis
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {

        // Aperçu du message
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Aperçu", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Text(
                    currentMessage?.body?.take(40)?.plus(if ((currentMessage.body.length) > 40) " …" else "")
                        ?: "— aucun message choisi —",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                )
                if (currentMessage != null) {
                    Text(
                        "Touchez pour voir le message complet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { showFullMessage = true }.padding(top = 4.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Statut + Progression
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatBox("Statut", state.statusLabel, Modifier.weight(1f))
            StatBox("Progression", "${state.processed}/${state.total}", Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))
        Text("Progression d'envoi", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))

        Spacer(Modifier.height(12.dp))

        // Réussis / Échoués
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatBox("Expédié avec succès", state.sent.toString(), Modifier.weight(1f), Color(0xFF2E7D32))
            StatBox("Expédié échoué", state.failed.toString(), Modifier.weight(1f), Color(0xFFB00020))
        }

        Spacer(Modifier.height(8.dp))

        // Bandeau infos rapides
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = { }, label = { Text("${session.credits} crédits") },
                leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, null) })
            Spacer(Modifier.width(8.dp))
            Text("${selectedContacts.size} contacts sélectionnés",
                style = MaterialTheme.typography.bodySmall)
        }
        if (estimatedCost > 0) {
            Text(
                "Coût estimé : $estimatedCost crédits" +
                    if (estimatedCost > session.credits) "  ⚠ solde insuffisant" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (estimatedCost > session.credits) Color(0xFFB00020) else MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(8.dp))

        // Paramètres d'envoi (repliable)
        Row(
            Modifier.fillMaxWidth().clickable { showSettings = !showSettings }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Tune, null); Spacer(Modifier.width(8.dp))
            Text("Paramètres d'envoi", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(if (showSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
        }

        if (showSettings) {
            Text("Délai entre les SMS : $delaySeconds s", style = MaterialTheme.typography.labelLarge)
            Slider(value = delaySeconds.toFloat(), onValueChange = { delaySeconds = it.roundToInt() },
                valueRange = 1f..30f, enabled = !running)

            Box {
                OutlinedButton(onClick = { simMenu = true }, enabled = !running) {
                    Icon(Icons.Default.SimCard, null); Spacer(Modifier.width(8.dp)); Text(currentSim.label)
                }
                DropdownMenu(expanded = simMenu, onDismissRequest = { simMenu = false }) {
                    sims.forEach { sim ->
                        DropdownMenuItem(text = { Text(sim.label) },
                            onClick = { selectedSubId = sim.subscriptionId; simMenu = false })
                    }
                }
            }

            SwitchRow("Personnaliser {nom}", personalize, !running) { personalize = it }
            SwitchRow("Réinitialiser les statuts avant l'envoi", resetFirst, !running) { resetFirst = it }
            SwitchRow("Planifier l'envoi (Calendrier)", scheduleOn, !running) {
                scheduleOn = it; if (it && scheduledAt == null) pickDateTime()
            }
            if (scheduleOn) {
                TextButton(onClick = { pickDateTime() }) {
                    Icon(Icons.Default.Event, null); Spacer(Modifier.width(4.dp))
                    Text(scheduledAt?.let { dateFmt.format(it) } ?: "Choisir la date et l'heure")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Boutons Play / Stop
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!running) {
                Button(
                    onClick = {
                        if (scheduleOn) {
                            val at = scheduledAt
                            if (at == null) { snackbar("Choisis la date et l'heure."); return@Button }
                            vm.scheduleSending(at, delaySeconds, selectedSubId, resetFirst, personalize,
                                onError = { snackbar(it) },
                                onScheduled = { snackbar("Envoi planifié pour ${dateFmt.format(at)}") })
                        } else {
                            showConfirm = true
                        }
                    },
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Icon(if (scheduleOn) Icons.Default.Schedule else Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (scheduleOn) "Planifier" else "Démarrer")
                }
            } else {
                if (state.phase == SendPhase.PAUSED) {
                    Button(onClick = { vm.resumeSending() }, modifier = Modifier.weight(1f).height(52.dp)) {
                        Icon(Icons.Default.PlayArrow, null); Text("Reprendre")
                    }
                } else {
                    Button(onClick = { vm.pauseSending() }, modifier = Modifier.weight(1f).height(52.dp)) {
                        Icon(Icons.Default.Pause, null); Text("Pause")
                    }
                }
            }
            OutlinedButton(
                onClick = { vm.stopSending() },
                enabled = running,
                modifier = Modifier.weight(1f).height(52.dp)
            ) { Icon(Icons.Default.Stop, null); Spacer(Modifier.width(4.dp)); Text("Arrêter") }
        }
    }

    if (showFullMessage && currentMessage != null) {
        AlertDialog(
            onDismissRequest = { showFullMessage = false },
            confirmButton = { TextButton(onClick = { showFullMessage = false }) { Text("Fermer") } },
            title = { Text(currentMessage.title.ifBlank { "Message" }) },
            text = { Text(currentMessage.body.ifBlank { "(vide)" }) }
        )
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Confirmer l'envoi") },
            text = {
                Text(
                    "Envoyer à ${selectedContacts.size} contacts.\n" +
                        "Coût estimé : $estimatedCost crédits (solde : ${session.credits}).\n" +
                        if (estimatedCost > session.credits)
                            "⚠ Le solde ne couvre pas tout : l'envoi s'arrêtera quand les crédits seront épuisés."
                        else "Chaque partie SMS = 1 crédit."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    vm.startSending(delaySeconds, selectedSubId, resetFirst, personalize) { snackbar(it) }
                }) { Text("Envoyer") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = valueColor)
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
