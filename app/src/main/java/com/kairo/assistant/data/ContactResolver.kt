package com.kairo.assistant.data

import android.content.Context
import android.provider.ContactsContract
import android.util.Log
import com.kairo.assistant.nlu.FuzzyMatch

class ContactResolver(private val context: Context? = null) {

    private var contactNames: List<Pair<String, String>> = emptyList()

    // Constructor/helper for testing
    fun setContactsForTesting(contacts: List<Pair<String, String>>) {
        contactNames = contacts
    }

    fun loadContacts() {
        val context = context ?: return
        val contacts = mutableListOf<Pair<String, String>>()
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )
            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val displayName = it.getString(nameIndex) ?: continue
                    val phoneNumber = it.getString(numberIndex) ?: continue
                    contacts.add(Pair(displayName, phoneNumber))
                }
            }
        } catch (e: Exception) {
            Log.e("ContactResolver", "Error loading contacts", e)
        }
        contactNames = contacts
    }

    fun resolve(spokenName: String): Pair<String, String>? {
        val match = FuzzyMatch.bestMatch(
            spokenName,
            contactNames.map { it.first },
            threshold = 0.55f
        ) ?: return null
        return contactNames.firstOrNull { it.first == match.first }
    }

    fun resolveMultiple(spokenName: String): List<Pair<String, String>> {
        val cleanedSpoken = spokenName.trim().lowercase()
        val prefs = context?.getSharedPreferences("kairo_prefs", Context.MODE_PRIVATE)
        val preferredName = prefs?.getString("contact_pref_$cleanedSpoken", null)

        if (preferredName != null) {
            val preferredContact = contactNames.firstOrNull { it.first.equals(preferredName, ignoreCase = true) }
            if (preferredContact != null) {
                return listOf(preferredContact)
            }
        }

        // Exact/Character match check first (ignoring emojis/symbols)
        val cleanSpokenForCompare = spokenName.replace(Regex("[^\\p{L}\\p{N}\\s]"), "").replace(Regex("\\s+"), " ").trim().lowercase()
        val exactMatches = contactNames.filter { 
            val cleanContactName = it.first.replace(Regex("[^\\p{L}\\p{N}\\s]"), "").replace(Regex("\\s+"), " ").trim().lowercase()
            cleanContactName == cleanSpokenForCompare
        }
        if (exactMatches.isNotEmpty()) {
            return exactMatches.distinctBy { it.first }
        }

        val candidates = contactNames.map { it.first }
        val scored = candidates.map { it to FuzzyMatch.similarity(spokenName, it) }
        val matches = scored.filter { it.second >= 0.55f }.sortedByDescending { it.second }
        return matches.mapNotNull { match ->
            contactNames.firstOrNull { it.first == match.first }
        }.distinctBy { it.first }
    }

    fun refresh() {
        loadContacts()
    }

    fun getAllContacts(): List<Pair<String, String>> {
        return contactNames
    }
}
