package com.emac.multisms.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emac.multisms.data.Contact
import com.emac.multisms.data.MessageTemplate
import com.emac.multisms.data.Repository
import com.emac.multisms.data.SendingList
import com.emac.multisms.net.ApiClient
import com.emac.multisms.net.LoginResult
import com.emac.multisms.session.SessionManager
import com.emac.multisms.sms.SendProgress
import com.emac.multisms.sms.Scheduler
import com.emac.multisms.sms.SmsSenderService
import com.emac.multisms.util.ContactImporter
import com.emac.multisms.util.ContactGroup
import com.emac.multisms.util.SimCard
import com.emac.multisms.util.SimUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository(app)
    private val ctx get() = getApplication<Application>()

    // Sélections
    private val _selectedListId = MutableStateFlow<Long?>(null)
    val selectedListId = _selectedListId.asStateFlow()

    private val _selectedMessageId = MutableStateFlow<Long?>(null)
    val selectedMessageId = _selectedMessageId.asStateFlow()

    // Données observées
    val lists = repo.allLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val contacts = _selectedListId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList<Contact>()) else repo.contactsOf(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val messages = repo.allMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // État d'envoi
    val sendState = SendProgress.state

    // Compte + crédits
    val session = SessionManager.session

    // Journal d'envoi (onglet Conversations)
    val history = repo.recentSent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sims: List<SimCard> get() = SimUtil.activeSims(ctx)

    // --- Compte ---
    fun login(email: String, password: String, onResult: (Boolean, String) -> Unit) = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) { ApiClient.login(email, password) }
        when (result) {
            is LoginResult.Success -> {
                SessionManager.login(result.account, result.token, result.credits)
                onResult(true, "")
            }
            is LoginResult.Error -> onResult(false, result.message)
        }
    }

    fun refreshCredits() = viewModelScope.launch {
        val balance = withContext(Dispatchers.IO) { ApiClient.refreshBalance() }
        balance?.let { SessionManager.setCredits(it) }
    }

    fun register(email: String, password: String, onResult: (Boolean, String) -> Unit) = viewModelScope.launch {
        val error = withContext(Dispatchers.IO) { ApiClient.register(email, password) }
        if (error != null) { onResult(false, error); return@launch }
        // Inscription réussie : on connecte directement l'utilisateur.
        when (val result = withContext(Dispatchers.IO) { ApiClient.login(email, password) }) {
            is LoginResult.Success -> {
                SessionManager.login(result.account, result.token, result.credits)
                onResult(true, "")
            }
            is LoginResult.Error -> onResult(true, "Compte créé. Connecte-toi.")
        }
    }

    fun logout() = SessionManager.logout()

    fun buyCreditsUrl(): String = SessionManager.BUY_CREDITS_URL

    // --- Listes ---
    fun selectList(id: Long) { _selectedListId.value = id }

    fun createList(name: String, onCreated: (Long) -> Unit = {}) = viewModelScope.launch {
        val id = repo.createList(name.ifBlank { "Nouvelle liste" })
        _selectedListId.value = id
        onCreated(id)
    }

    fun renameList(id: Long, name: String) = viewModelScope.launch { repo.renameList(id, name) }

    fun deleteList(id: Long) = viewModelScope.launch {
        repo.deleteList(id)
        if (_selectedListId.value == id) _selectedListId.value = null
    }

    // --- Contacts ---
    fun addContact(name: String, phone: String) = viewModelScope.launch {
        val id = _selectedListId.value ?: repo.createList("Nouvelle liste").also { _selectedListId.value = it }
        repo.addContact(id, name.ifBlank { phone }, phone)
    }

    fun toggleContact(contact: Contact) = viewModelScope.launch {
        repo.setSelected(contact.id, !contact.selected)
    }

    fun setAllSelected(selected: Boolean) = viewModelScope.launch {
        _selectedListId.value?.let { repo.setAllSelected(it, selected) }
    }

    fun deleteContact(contact: Contact) = viewModelScope.launch { repo.deleteContact(contact.id) }

    /** Exporte les contacts de la liste courante en CSV (nom,numéro) vers le fichier choisi. */
    fun exportContacts(uri: Uri, onDone: (Int) -> Unit) = viewModelScope.launch {
        val items = contacts.value
        withContext(Dispatchers.IO) {
            ctx.contentResolver.openOutputStream(uri)?.use { os ->
                val csv = items.joinToString("\n") { "${it.name},${it.phone}" }
                os.write(csv.toByteArray(Charsets.UTF_8))
            }
        }
        onDone(items.size)
    }

    fun resetStatuses() = viewModelScope.launch {
        _selectedListId.value?.let { repo.resetStatuses(it) }
    }

    /** Importe les contacts du téléphone dans une nouvelle liste (ou la liste courante). */
    fun importFromDevice(newListName: String?, onDone: (Int, Int) -> Unit) = viewModelScope.launch {
        val raw = withContext(Dispatchers.IO) { ContactImporter.fromDevice(ctx) }
        val (imported, skipped) = ContactImporter.clean(raw)
        val listId = if (newListName != null) repo.createList(newListName).also { _selectedListId.value = it }
        else (_selectedListId.value ?: repo.createList("Contacts").also { _selectedListId.value = it })
        repo.addContacts(listId, imported.map { it.name to it.phone })
        onDone(imported.size, skipped)
    }

    /** Importe un fichier CSV/TXT dans une nouvelle liste. */
    fun importFromFile(uri: Uri, newListName: String, onDone: (Int, Int) -> Unit) = viewModelScope.launch {
        val raw = withContext(Dispatchers.IO) { ContactImporter.fromFile(ctx, uri) }
        val (imported, skipped) = ContactImporter.clean(raw)
        val listId = repo.createList(newListName.ifBlank { "Import fichier" }).also { _selectedListId.value = it }
        repo.addContacts(listId, imported.map { it.name to it.phone })
        onDone(imported.size, skipped)
    }

    /** Charge la liste des groupes de contacts du téléphone. */
    fun loadDeviceGroups(onResult: (List<ContactGroup>) -> Unit) = viewModelScope.launch {
        val groups = withContext(Dispatchers.IO) { ContactImporter.deviceGroups(ctx) }
        onResult(groups)
    }

    /** Importe un groupe précis du téléphone dans une nouvelle liste à son nom. */
    fun importFromGroup(group: ContactGroup, onDone: (Int, Int) -> Unit) = viewModelScope.launch {
        val raw = withContext(Dispatchers.IO) { ContactImporter.fromDeviceGroup(ctx, group.ids) }
        val (imported, skipped) = ContactImporter.clean(raw)
        val listId = repo.createList(group.title).also { _selectedListId.value = it }
        repo.addContacts(listId, imported.map { it.name to it.phone })
        onDone(imported.size, skipped)
    }

    // --- Messages ---
    fun selectMessage(id: Long) { _selectedMessageId.value = id }

    fun createMessage(title: String, body: String, onCreated: (Long) -> Unit = {}) = viewModelScope.launch {
        val id = repo.createMessage(title.ifBlank { "Message" }, body)
        _selectedMessageId.value = id
        onCreated(id)
    }

    fun updateMessage(m: MessageTemplate) = viewModelScope.launch { repo.updateMessage(m) }
    fun deleteMessage(id: Long) = viewModelScope.launch {
        repo.deleteMessage(id)
        if (_selectedMessageId.value == id) _selectedMessageId.value = null
    }

    // --- Envoi ---
    fun currentMessageBody(): String? =
        messages.value.firstOrNull { it.id == _selectedMessageId.value }?.body

    fun startSending(delaySeconds: Int, subId: Int, resetFirst: Boolean, personalize: Boolean, onError: (String) -> Unit) {
        val listId = _selectedListId.value
        val body = currentMessageBody()
        val toSend = contacts.value.count { it.selected }
        when {
            listId == null -> { onError("Sélectionne d'abord une liste d'envoi."); return }
            body.isNullOrBlank() -> { onError("Sélectionne d'abord un message."); return }
            toSend == 0 -> { onError("Aucun contact sélectionné dans la liste."); return }
            SessionManager.credits <= 0 -> { onError("Crédits épuisés. Achète des crédits pour continuer."); return }
        }
        viewModelScope.launch {
            if (resetFirst) listId?.let { repo.resetStatuses(it) }
            SendProgress.reset()
            SendProgress.set(SendProgress.state.value.copy(creditsLeft = SessionManager.credits))
            SmsSenderService.start(ctx, listId!!, body!!, delaySeconds * 1000L, subId, personalize)
        }
    }

    /** Programme un envoi pour plus tard (planification / Calendrier). */
    fun scheduleSending(
        triggerAtMillis: Long,
        delaySeconds: Int,
        subId: Int,
        resetFirst: Boolean,
        personalize: Boolean,
        onError: (String) -> Unit,
        onScheduled: () -> Unit
    ) {
        val listId = _selectedListId.value
        val body = currentMessageBody()
        val toSend = contacts.value.count { it.selected }
        when {
            listId == null -> { onError("Sélectionne d'abord une liste d'envoi."); return }
            body.isNullOrBlank() -> { onError("Sélectionne d'abord un message."); return }
            toSend == 0 -> { onError("Aucun contact sélectionné dans la liste."); return }
            triggerAtMillis <= System.currentTimeMillis() -> { onError("Choisis une date/heure future."); return }
            !Scheduler.canScheduleExact(ctx) -> { onError("Autorise les « alarmes exactes » dans les réglages du téléphone."); return }
        }
        viewModelScope.launch {
            if (resetFirst) listId?.let { repo.resetStatuses(it) }
            Scheduler.schedule(ctx, triggerAtMillis, listId!!, body!!, delaySeconds * 1000L, subId, personalize)
            onScheduled()
        }
    }

    fun pauseSending() = SmsSenderService.sendControl(ctx, SmsSenderService.ACTION_PAUSE)
    fun resumeSending() = SmsSenderService.sendControl(ctx, SmsSenderService.ACTION_RESUME)
    fun stopSending() = SmsSenderService.sendControl(ctx, SmsSenderService.ACTION_STOP)
}
