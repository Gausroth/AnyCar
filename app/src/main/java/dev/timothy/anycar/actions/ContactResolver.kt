package dev.timothy.anycar.actions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telephony.PhoneNumberUtils
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ContactMatch(val name: String, val number: String, val type: String)

sealed interface Lookup {
    data object NoPermission : Lookup
    data class Found(val matches: List<ContactMatch>) : Lookup
}

/** Name → phone numbers, on the phone only (M5.2). Shared by calls and texts. Contacts never leave the phone. */
class ContactResolver(private val ctx: Context) {
    fun hasPermission() =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    suspend fun find(query: String): Lookup = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext Lookup.NoPermission
        val cleaned = query.replace("%", "").replace("_", "").trim()
        val matches = mutableListOf<ContactMatch>()
        ctx.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER, Phone.TYPE, Phone.LABEL),
            "${Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$cleaned%"),
            "${Phone.DISPLAY_NAME} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val number = c.getString(1) ?: continue
                val type = Phone.getTypeLabel(ctx.resources, c.getInt(2), c.getString(3)).toString()
                matches += ContactMatch(c.getString(0) ?: number, number, type)
            }
        }
        // Synced accounts duplicate contacts, so keep one row per number.
        Lookup.Found(matches.distinctBy { PhoneNumberUtils.normalizeNumber(it.number) })
    }
}
