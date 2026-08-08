package com.emac.multisms.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ListDao {
    @Insert
    suspend fun insert(list: SendingList): Long

    @Query("SELECT * FROM sending_lists ORDER BY createdAt DESC")
    fun getAll(): Flow<List<SendingList>>

    @Query("UPDATE sending_lists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM sending_lists WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ContactDao {
    @Insert
    suspend fun insert(contact: Contact): Long

    @Insert
    suspend fun insertAll(contacts: List<Contact>)

    @Query("SELECT * FROM contacts WHERE listId = :listId ORDER BY name COLLATE NOCASE ASC")
    fun getByList(listId: Long): Flow<List<Contact>>

    /** Contacts sélectionnés qui n'ont pas encore été envoyés (permet la reprise et le renvoi des échecs). */
    @Query("SELECT * FROM contacts WHERE listId = :listId AND selected = 1 AND status != 'SENT' ORDER BY name COLLATE NOCASE ASC")
    suspend fun getForSending(listId: Long): List<Contact>

    @Query("UPDATE contacts SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE contacts SET selected = :selected WHERE id = :id")
    suspend fun setSelected(id: Long, selected: Boolean)

    @Query("UPDATE contacts SET selected = :selected WHERE listId = :listId")
    suspend fun setAllSelected(listId: Long, selected: Boolean)

    @Query("UPDATE contacts SET status = 'PENDING' WHERE listId = :listId")
    suspend fun resetStatuses(listId: Long)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface MessageDao {
    @Insert
    suspend fun insert(message: MessageTemplate): Long

    @Update
    suspend fun update(message: MessageTemplate)

    @Query("SELECT * FROM messages ORDER BY createdAt DESC")
    fun getAll(): Flow<List<MessageTemplate>>

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface SentLogDao {
    @Insert
    suspend fun insert(entry: SentLog)

    @Query("SELECT * FROM sent_log ORDER BY sentAt DESC LIMIT 500")
    fun recent(): Flow<List<SentLog>>

    @Query("DELETE FROM sent_log")
    suspend fun clear()
}
