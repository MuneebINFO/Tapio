package com.tapio.app.data

import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import com.tapio.core.common.SharedContent

/**
 * Hands a received [SharedContent.ContactCard] to the system "add contact" screen,
 * pre-filled with the name the sender chose. The user confirms there — no
 * `WRITE_CONTACTS` permission needed.
 */
object ContactSaver {

    fun insertIntent(card: SharedContent.ContactCard): Intent =
        Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.NAME, card.displayName)
            putExtra(ContactsContract.Intents.Insert.PHONE, card.phoneNumber)
            putExtra(ContactsContract.Intents.Insert.PHONE_TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
            card.organization?.let { putExtra(ContactsContract.Intents.Insert.COMPANY, it) }
        }

    fun launch(context: Context, card: SharedContent.ContactCard) {
        context.startActivity(
            insertIntent(card).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
