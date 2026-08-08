package com.emac.multisms.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.emac.multisms.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(vm: MainViewModel, snackbar: (String) -> Unit, onGoLogin: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    val emailValid = email.contains("@") && email.contains(".")
    val passwordValid = password.length >= 4
    val match = password == confirm
    val canSubmit = emailValid && passwordValid && match && !loading

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.PersonAddAlt1, null,
            modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text("Créer un compte", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Ton compte servira à gérer tes crédits d'envoi.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("E-mail") }, singleLine = true,
            isError = email.isNotBlank() && !emailValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Mot de passe") }, singleLine = true,
            isError = password.isNotBlank() && !passwordValid,
            supportingText = { if (password.isNotBlank() && !passwordValid) Text("Au moins 4 caractères") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = confirm, onValueChange = { confirm = it },
            label = { Text("Confirmer le mot de passe") }, singleLine = true,
            isError = confirm.isNotBlank() && !match,
            supportingText = { if (confirm.isNotBlank() && !match) Text("Les mots de passe ne correspondent pas") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                loading = true
                vm.register(email, password) { ok, msg ->
                    loading = false
                    if (ok) { if (msg.isNotBlank()) snackbar(msg) }
                    else snackbar(msg.ifBlank { "Inscription impossible" })
                }
            },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (loading) CircularProgressIndicator(
                Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary
            ) else Text("Créer mon compte")
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Déjà un compte ?", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onGoLogin) { Text("Se connecter") }
        }
    }
}
