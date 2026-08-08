package com.emac.multisms.net

import com.emac.multisms.session.SessionManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Résultat d'une connexion. */
sealed class LoginResult {
    data class Success(val account: String, val token: String, val credits: Int) : LoginResult()
    data class Error(val message: String) : LoginResult()
}

/**
 * Client HTTP minimal (sans dépendance externe) vers le backend d'EmacDigital.
 *
 * Contrat d'API attendu côté serveur :
 *   POST {server}/api/login   {email, password}            -> {token, account, credits}
 *   GET  {server}/api/balance?token=...                    -> {account, credits}
 *   POST {server}/api/usage   {token, sent}                -> {credits}
 *
 * Mode DÉMO : se connecter avec l'e-mail "demo" (n'importe quel mot de passe)
 * crée un compte local avec 1000 crédits, sans réseau. Pratique pour tester l'app
 * avant d'avoir déployé le backend.
 */
object ApiClient {

    private const val TIMEOUT = 15000

    fun login(email: String, password: String): LoginResult {
        if (email.trim().equals("demo", ignoreCase = true)) {
            return LoginResult.Success(account = "demo", token = "demo-token", credits = 1000)
        }
        return try {
            val body = JSONObject().put("email", email).put("password", password)
            val json = post("/api/login", body, withToken = false)
                ?: return LoginResult.Error("Serveur injoignable")
            if (json.has("error")) return LoginResult.Error(json.optString("error"))
            LoginResult.Success(
                account = json.optString("account", email),
                token = json.optString("token"),
                credits = json.optInt("credits", 0)
            )
        } catch (e: Exception) {
            LoginResult.Error(e.message ?: "Erreur de connexion")
        }
    }

    /** Crée un compte. Retourne null si succès, sinon un message d'erreur. */
    fun register(email: String, password: String): String? {
        if (email.trim().equals("demo", ignoreCase = true)) {
            return "Le mode démo ne nécessite pas d'inscription (bouton « démo » sur l'écran de connexion)."
        }
        return try {
            val body = JSONObject().put("email", email.trim()).put("password", password)
            val json = post("/api/register", body, withToken = false) ?: return "Serveur injoignable"
            if (json.has("error")) json.optString("error") else null
        } catch (e: Exception) {
            e.message ?: "Erreur d'inscription"
        }
    }

    /** Rafraîchit le solde depuis le serveur. Retourne null si indisponible. */
    fun refreshBalance(): Int? {
        if (SessionManager.token == "demo-token") return SessionManager.credits
        return try {
            val json = get("/api/balance?token=" + SessionManager.token) ?: return null
            json.optInt("credits", SessionManager.credits)
        } catch (e: Exception) {
            null
        }
    }

    /** Signale au serveur les SMS envoyés (best-effort). Retourne le nouveau solde si connu. */
    fun reportUsage(sent: Int): Int? {
        if (sent <= 0) return null
        if (SessionManager.token == "demo-token") return SessionManager.credits
        return try {
            val body = JSONObject().put("token", SessionManager.token).put("sent", sent)
            val json = post("/api/usage", body, withToken = false) ?: return null
            if (json.has("credits")) json.getInt("credits") else null
        } catch (e: Exception) {
            null
        }
    }

    // --- HTTP bas niveau ---

    private fun post(path: String, body: JSONObject, withToken: Boolean): JSONObject? {
        val url = URL(SessionManager.serverUrl.trimEnd('/') + path)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT
            readTimeout = TIMEOUT
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (withToken) setRequestProperty("Authorization", "Bearer " + SessionManager.token)
        }
        return try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            readJson(conn)
        } finally {
            conn.disconnect()
        }
    }

    private fun get(path: String): JSONObject? {
        val url = URL(SessionManager.serverUrl.trimEnd('/') + path)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT
            readTimeout = TIMEOUT
        }
        return try {
            readJson(conn)
        } finally {
            conn.disconnect()
        }
    }

    private fun readJson(conn: HttpURLConnection): JSONObject? {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream ?: return null
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        if (text.isBlank()) return null
        return JSONObject(text)
    }
}
