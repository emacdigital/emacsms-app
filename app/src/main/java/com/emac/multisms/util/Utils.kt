package com.emac.multisms.util

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.ceil

/** Un contact importé : nom + numéro. */
data class ImportedContact(val name: String, val phone: String)

/** Un groupe de contacts du téléphone (ex. Famille, Église). */
data class ContactGroup(val title: String, val ids: List<Long>)

object ContactImporter {

    /** Lit tous les contacts (nom + numéro) du téléphone. Nécessite READ_CONTACTS. */
    fun fromDevice(context: Context): List<ImportedContact> {
        val result = mutableListOf<ImportedContact>()
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        ) ?: return result

        cursor.use { c ->
            val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val seen = HashSet<String>()
            while (c.moveToNext()) {
                val name = if (nameIdx >= 0) c.getString(nameIdx) ?: "" else ""
                val rawNum = if (numIdx >= 0) c.getString(numIdx) ?: "" else ""
                val phone = normalizePhone(rawNum)
                if (phone.isNotEmpty() && seen.add(phone)) {
                    result.add(ImportedContact(name.ifBlank { phone }, phone))
                }
            }
        }
        return result
    }

    /**
     * Import CSV / TXT (UTF-8). Chaque ligne : nom,numéro
     * (1re colonne = nom d'affichage, 2e colonne = numéro).
     */
    fun fromFile(context: Context, uri: Uri): List<ImportedContact> {
        val result = mutableListOf<ImportedContact>()
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) return@forEach
                    val parts = trimmed.split(",", ";").map { it.trim() }
                    when {
                        parts.size >= 2 -> {
                            val phone = normalizePhone(parts[1])
                            if (phone.isNotEmpty()) {
                                result.add(ImportedContact(parts[0].ifBlank { phone }, phone))
                            }
                        }
                        parts.size == 1 -> {
                            val phone = normalizePhone(parts[0])
                            if (phone.isNotEmpty()) result.add(ImportedContact(phone, phone))
                        }
                    }
                }
            }
        }
        return result
    }

    private fun normalizePhone(raw: String): String {
        val cleaned = raw.filter { it.isDigit() || it == '+' }
        // On garde le '+' seulement en tête.
        return if (cleaned.startsWith("+")) "+" + cleaned.drop(1).filter { it.isDigit() }
        else cleaned.filter { it.isDigit() }
    }

    /** Liste les groupes de contacts du téléphone (Famille, Église…). Nécessite READ_CONTACTS. */
    fun deviceGroups(context: Context): List<ContactGroup> {
        val byTitle = LinkedHashMap<String, MutableList<Long>>()
        val cursor = context.contentResolver.query(
            ContactsContract.Groups.CONTENT_URI,
            arrayOf(
                ContactsContract.Groups._ID,
                ContactsContract.Groups.TITLE,
                ContactsContract.Groups.DELETED,
                ContactsContract.Groups.AUTO_ADD
            ),
            null, null,
            ContactsContract.Groups.TITLE + " ASC"
        ) ?: return emptyList()

        cursor.use { c ->
            val idIdx = c.getColumnIndex(ContactsContract.Groups._ID)
            val titleIdx = c.getColumnIndex(ContactsContract.Groups.TITLE)
            val deletedIdx = c.getColumnIndex(ContactsContract.Groups.DELETED)
            val autoAddIdx = c.getColumnIndex(ContactsContract.Groups.AUTO_ADD)
            while (c.moveToNext()) {
                val deleted = if (deletedIdx >= 0) c.getInt(deletedIdx) else 0
                val autoAdd = if (autoAddIdx >= 0) c.getInt(autoAddIdx) else 0
                if (deleted == 1 || autoAdd == 1) continue
                val title = (if (titleIdx >= 0) c.getString(titleIdx) else null)?.trim() ?: ""
                if (title.isBlank()) continue
                val id = if (idIdx >= 0) c.getLong(idIdx) else continue
                byTitle.getOrPut(title) { mutableListOf() }.add(id)
            }
        }
        return byTitle.map { ContactGroup(it.key, it.value) }
    }

    /** Importe les contacts appartenant à un groupe précis du téléphone. */
    fun fromDeviceGroup(context: Context, groupIds: List<Long>): List<ImportedContact> {
        if (groupIds.isEmpty()) return emptyList()

        // 1) IDs des contacts membres du/des groupe(s).
        val contactIds = LinkedHashSet<Long>()
        val placeholders = groupIds.joinToString(",") { "?" }
        val selection = ContactsContract.Data.MIMETYPE + " = ? AND " +
            ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID + " IN ($placeholders)"
        val args = ArrayList<String>()
        args.add(ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE)
        groupIds.forEach { args.add(it.toString()) }

        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.CONTACT_ID),
            selection, args.toTypedArray(), null
        )?.use { c ->
            val idx = c.getColumnIndex(ContactsContract.Data.CONTACT_ID)
            while (c.moveToNext()) if (idx >= 0) contactIds.add(c.getLong(idx))
        }
        if (contactIds.isEmpty()) return emptyList()

        // 2) Numéros de ces contacts.
        val result = mutableListOf<ImportedContact>()
        val seen = HashSet<String>()
        val idList = contactIds.joinToString(",")
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " IN ($idList)",
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )?.use { c ->
            val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (c.moveToNext()) {
                val name = (if (nameIdx >= 0) c.getString(nameIdx) else null) ?: ""
                val rawNum = (if (numIdx >= 0) c.getString(numIdx) else null) ?: ""
                val phone = normalizePhone(rawNum)
                if (phone.isNotEmpty() && seen.add(phone)) {
                    result.add(ImportedContact(name.ifBlank { phone }, phone))
                }
            }
        }
        return result
    }

    /**
     * Nettoie une liste importée : retire les doublons (même numéro) et les
     * numéros invalides (moins de 8 chiffres). Retourne (liste nettoyée, nb ignorés).
     */
    fun clean(list: List<ImportedContact>): Pair<List<ImportedContact>, Int> {
        val seen = HashSet<String>()
        val out = ArrayList<ImportedContact>()
        var skipped = 0
        for (c in list) {
            val digits = c.phone.filter { it.isDigit() }
            if (digits.length < 8) { skipped++; continue }
            if (!seen.add(c.phone)) { skipped++; continue }
            out.add(c)
        }
        return out to skipped
    }
}

/** Une SIM disponible pour l'envoi. */
data class SimCard(val subscriptionId: Int, val label: String)

object SimUtil {
    /** subscriptionId = -1 signifie « SIM par défaut ». */
    val DEFAULT = SimCard(-1, "SIM par défaut")

    @SuppressLint("MissingPermission")
    fun activeSims(context: Context): List<SimCard> {
        val list = mutableListOf(DEFAULT)
        try {
            val hasPerm = ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.READ_PHONE_STATE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasPerm) return list

            val sm = context.getSystemService(SubscriptionManager::class.java) ?: return list
            val infos: List<SubscriptionInfo> = sm.activeSubscriptionInfoList ?: emptyList()
            infos.forEach { info ->
                val carrier = info.carrierName?.toString() ?: "SIM"
                val slot = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.simSlotIndex + 1 else info.simSlotIndex
                list.add(SimCard(info.subscriptionId, "SIM $slot – $carrier"))
            }
        } catch (_: Exception) {
        }
        return list
    }
}

object SmsText {
    /** Estimation du nombre de parties d'un SMS (GSM-7 : 160/153 ; Unicode : 70/67). */
    fun parts(text: String): Int {
        if (text.isEmpty()) return 0
        val unicode = text.any { it.code > 127 }
        val single = if (unicode) 70 else 160
        val multi = if (unicode) 67 else 153
        return if (text.length <= single) 1 else ceil(text.length / multi.toDouble()).toInt()
    }

    /** Remplace les variables de personnalisation par le nom du contact. */
    fun personalize(template: String, name: String): String =
        template
            .replace("{name}", name, ignoreCase = true)
            .replace("{nom}", name, ignoreCase = true)
            .replace("{prenom}", name.trim().split(" ").firstOrNull() ?: name, ignoreCase = true)
}
