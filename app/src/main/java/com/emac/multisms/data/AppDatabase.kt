package com.emac.multisms.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SendingList::class, Contact::class, MessageTemplate::class, SentLog::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun listDao(): ListDao
    abstract fun contactDao(): ContactDao
    abstract fun messageDao(): MessageDao
    abstract fun sentLogDao(): SentLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smspro.db"
                ).build().also { INSTANCE = it }
            }
    }
}

/** Point d'accès unique aux données. */
class Repository(context: Context) {
    private val db = AppDatabase.get(context)
    private val lists = db.listDao()
    private val contacts = db.contactDao()
    private val messages = db.messageDao()
    private val sentLog = db.sentLogDao()

    // Listes
    fun allLists() = lists.getAll()
    suspend fun createList(name: String): Long = lists.insert(SendingList(name = name))
    suspend fun renameList(id: Long, name: String) = lists.rename(id, name)
    suspend fun deleteList(id: Long) = lists.deleteById(id)

    // Contacts
    fun contactsOf(listId: Long) = contacts.getByList(listId)
    suspend fun addContact(listId: Long, name: String, phone: String) =
        contacts.insert(Contact(listId = listId, name = name, phone = phone))
    suspend fun addContacts(listId: Long, items: List<Pair<String, String>>) =
        contacts.insertAll(items.map { Contact(listId = listId, name = it.first, phone = it.second) })
    suspend fun contactsForSending(listId: Long) = contacts.getForSending(listId)
    suspend fun setStatus(id: Long, status: String) = contacts.updateStatus(id, status)
    suspend fun setSelected(id: Long, selected: Boolean) = contacts.setSelected(id, selected)
    suspend fun setAllSelected(listId: Long, selected: Boolean) = contacts.setAllSelected(listId, selected)
    suspend fun resetStatuses(listId: Long) = contacts.resetStatuses(listId)
    suspend fun deleteContact(id: Long) = contacts.deleteById(id)

    // Messages
    fun allMessages() = messages.getAll()
    suspend fun createMessage(title: String, body: String): Long =
        messages.insert(MessageTemplate(title = title, body = body))
    suspend fun updateMessage(m: MessageTemplate) = messages.update(m)
    suspend fun deleteMessage(id: Long) = messages.deleteById(id)

    // Journal d'envoi
    fun recentSent() = sentLog.recent()
    suspend fun logSent(name: String, phone: String, body: String, success: Boolean) =
        sentLog.insert(SentLog(name = name, phone = phone, body = body, success = success))
    suspend fun clearLog() = sentLog.clear()
}
