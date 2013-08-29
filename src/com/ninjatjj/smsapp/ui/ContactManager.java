package com.ninjatjj.smsapp.ui;

import java.util.HashMap;
import java.util.Map;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.PhoneLookup;
import android.telephony.PhoneNumberUtils;

public class ContactManager {

	private static Map<String, String> addressToDisplayName = new HashMap<String, String>();
	private static Map<String, String> displayNameToAddress = new HashMap<String, String>();

	public static synchronized String getDisplayName(
			ContentResolver contentResolver, String address) {
		String displayName = addressToDisplayName.get(address);
		if (displayName == null) {
			Cursor cursor = contentResolver
					.query(Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI,
							Uri.encode(address)),
							new String[] { PhoneLookup.DISPLAY_NAME }, null,
							null, null);

			if (cursor != null) {
				try {
					if (cursor.getCount() > 0) {
						cursor.moveToFirst();
						displayName = cursor.getString(0);
					} else {
						displayName = address;
					}
					addressToDisplayName.put(address, displayName);
					displayNameToAddress.put(displayName, address);
				} finally {
					cursor.close();
				}
			} else if (address != null) {
				displayName = PhoneNumberUtils.formatNumber(address);
			}

		}
		return displayName;
	}

	public static synchronized String getAddress(String person) {
		return displayNameToAddress.get(person);
	}

}
