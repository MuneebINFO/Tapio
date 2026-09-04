package com.tapio.app.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.result.contract.ActivityResultContract
import com.tapio.core.common.SharedContent

/**
 * Picks one phone number straight from the system contacts app. The result is a
 * `content://…/data/N` URI pointing at the chosen number row.
 */
class PickPhoneNumber : ActivityResultContract<Unit, Uri?>() {

    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data?.takeIf { resultCode == Activity.RESULT_OK }
}

/** Reads the display name + number from a picked phone-data [uri]. Needs `READ_CONTACTS`. */
fun Context.readPickedNumber(uri: Uri): SharedContent.ContactCard? = runCatching {
    contentResolver.query(
        uri,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        ),
        null,
        null,
        null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val name = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: return@use null
        val number = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: return@use null
        SharedContent.ContactCard(displayName = name, phoneNumber = number)
    }
}.getOrNull()
