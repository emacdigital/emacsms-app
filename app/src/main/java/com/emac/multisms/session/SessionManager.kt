package com.emac.multisms.session

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Session(
    val loggedIn: Boolean = false,
    val account: String = "",
    val token: String = "",
    val credits: Int = 0
)

/**
 * Stocke la session (compte, jeton, crédits) et l'expose en StateFlow.
 * Les crédits sont la « monnaie logicielle » : ils sont synchronisés depuis
 * le serveur à la connexion, décrémentés à chaque SMS envoyé, et rechargés
 * après un achat (Mobile Money / Chariow) via le backend.
 */
object SessionManager {

    private const val PREFS = "session"
    private const val K_ACCOUNT = "account"
    private const val K_TOKEN = "token"
    private const val K_CREDITS = "credits"
    private const val K_SERVER = "server_url"

    private lateinit var appContext: Context

    @Volatile private var initialized = false

    private val _session = MutableStateFlow(Session())
    val session: StateFlow<Session> = _session.asStateFlow()

    /** Initialise si nécessaire (sans écraser un état déjà chargé). */
    fun ensure(context: Context) {
        if (!initialized) init(context)
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        initialized = true
        val p = prefs()
        val token = p.getString(K_TOKEN, "") ?: ""
        _session.value = Session(
            loggedIn = token.isNotEmpty(),
            account = p.getString(K_ACCOUNT, "") ?: "",
            token = token,
            credits = p.getInt(K_CREDITS, 0)
        )
    }

    private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val credits: Int get() = _session.value.credits
    val token: String get() = _session.value.token
    val isLoggedIn: Boolean get() = _session.value.loggedIn

    var serverUrl: String
        get() = prefs().getString(K_SERVER, DEFAULT_SERVER) ?: DEFAULT_SERVER
        set(value) { prefs().edit().putString(K_SERVER, value).apply() }

    fun login(account: String, token: String, credits: Int) {
        prefs().edit()
            .putString(K_ACCOUNT, account)
            .putString(K_TOKEN, token)
            .putInt(K_CREDITS, credits)
            .apply()
        _session.value = Session(true, account, token, credits)
    }

    fun setCredits(credits: Int) {
        prefs().edit().putInt(K_CREDITS, credits).apply()
        _session.value = _session.value.copy(credits = credits.coerceAtLeast(0))
    }

    /** Décrémente d'une unité après un SMS envoyé. Retourne le solde restant. */
    @Synchronized
    fun consumeOne(): Int {
        val newValue = (_session.value.credits - 1).coerceAtLeast(0)
        setCredits(newValue)
        return newValue
    }

    /** Décrémente de n crédits (n = nombre de parties SMS du message envoyé). */
    @Synchronized
    fun consume(n: Int): Int {
        val newValue = (_session.value.credits - n.coerceAtLeast(1)).coerceAtLeast(0)
        setCredits(newValue)
        return newValue
    }

    fun logout() {
        prefs().edit().remove(K_ACCOUNT).remove(K_TOKEN).remove(K_CREDITS).apply()
        _session.value = Session()
    }

    // URL de secours / démo. À remplacer par le backend d'EmacDigital.
    const val DEFAULT_SERVER = "https://api.emacdigital.com"
    // Page d'achat de crédits (Chariow / Mobile Money).
    const val BUY_CREDITS_URL = "https://emacdigital.com/credits"
}
