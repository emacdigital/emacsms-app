package com.emac.multisms.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.emac.multisms.session.CreditPack
import com.emac.multisms.session.Packs

@Composable
fun BuyCreditsDialog(
    onPick: (CreditPack) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 4.dp) {
            Column(
                Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.AccountBalanceWallet, null,
                    modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Text("Acheter des crédits", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "1 crédit = 1 SMS. Paiement par Mobile Money.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(16.dp))

                Packs.all.forEach { pack ->
                    PackCard(pack, onPick = { onPick(pack) })
                    Spacer(Modifier.height(12.dp))
                }

                TextButton(onClick = onDismiss) { Text("Fermer") }
            }
        }
    }
}

@Composable
private fun PackCard(pack: CreditPack, onPick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${pack.credits} crédits",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(pack.priceLabel, style = MaterialTheme.typography.bodyMedium)
                Text("Recharge ponctuelle", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onPick) { Text("Acheter") }
        }
    }
}
