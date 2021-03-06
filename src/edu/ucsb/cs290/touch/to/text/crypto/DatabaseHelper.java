package edu.ucsb.cs290.touch.to.text.crypto;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignedObject;
import java.util.UUID;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Base64;
import android.util.Log;

import com.google.android.gcm.GCMRegistrar;

import edu.ucsb.cs290.touch.to.text.R;
import edu.ucsb.cs290.touch.to.text.remote.Helpers;
import edu.ucsb.cs290.touch.to.text.remote.messages.ProtectedMessage;
import edu.ucsb.cs290.touch.to.text.remote.messages.SignedMessage;

/**
 * 
 * Instantiate and provide access to the DB, which contains Messages and Contacts.
 * @author dannyiland
 * @author charlesmunger
 * 
 */
public class DatabaseHelper extends SQLiteOpenHelper {

	// Encrypted Preferences File
	private static final String TOUCH_TO_TEXT_PREFERENCES_XML = "touchToTextPreferences.xml";

	// DB Strings
	public static final String MESSAGES_TABLE = "Messages";
	public static final String CONTACTS_TABLE = "Contacts";

	// Messages Table
	public static final String MESSAGES_ID = "_id";
	public static final String SENDER_ID = "sender";
	public static final String RECIPIENT_ID = "recipient";
	public static final String DATE_TIME = "dateTime";
	public static final String READ = "read"; // 1 if read, 0 for unread
	public static final String MESSAGE_BODY = "messageBody";

	public static final int MESSAGE_READ = 1;
	public static final int MESSAGE_UNREAD = 0;

	public static final String[] MESSAGES_CURSOR_COLUMNS = new String[] {
		MESSAGES_ID, DATE_TIME, MESSAGE_BODY, SENDER_ID, RECIPIENT_ID };

	// Contacts Table
	// Store the token used for sending in SPK PUBLIC_KEY
	// Store the last token given to a contact in CONTACT_TOKEN
	public static final String CONTACTS_ID = "_id";
	public static final String NICKNAME = "nickname";
	public static final String CONTACT_ID = "contactId";
	public static final String CONTACT_NOTE = "note";
	private static final String VERIFIED_BY = "verifiers";
	public static final String PUBLIC_KEY = "publicKey";
	public static final String PUBLIC_KEY_FINGERPRINT = "publicKeyFingerprint";
	public static final String CONTACT_TOKEN = "token";

	public static final String[] CONTACT_CURSOR_COLUMNS = new String[] {
		CONTACTS_ID, CONTACT_TOKEN, PUBLIC_KEY, NICKNAME };

	// My contact ID
	private static final long MY_CONTACT_ID = -1;

	private static final String CREATE_MESSAGES_COMMAND = 
			"CREATE TABLE " + MESSAGES_TABLE + " (  " 
					+ MESSAGES_ID + " INTEGER PRIMARY KEY autoincrement, " 
					+ SENDER_ID + " INTEGER, " 
					+ RECIPIENT_ID  + " INTEGER, " 
					+ DATE_TIME + " INTEGER, "
					+ READ + " INTEGER DEFAULT 0, " 
					+ MESSAGE_BODY + " BLOB);";

	private static final String CREATE_CONTACTS_COMMAND = 
			"CREATE TABLE " + CONTACTS_TABLE + " ( " 
					+ CONTACTS_ID + " integer PRIMARY KEY autoincrement, " 
					+ NICKNAME + " TEXT, " 
					+ PUBLIC_KEY + " BLOB, " 
					+ PUBLIC_KEY_FINGERPRINT + " TEXT, "
					+ DATE_TIME + " INTEGER, " 
					+ VERIFIED_BY + " TEXT, " 
					+ CONTACT_TOKEN + " BLOB, " 
					+ CONTACT_NOTE + " TEXT);";

	private static final String DATABASE_NAME = "touchToText.db";
	private static final int DATABASE_VERSION = 1;

	// Databases and Context
	private SQLiteDatabase db;
	private MasterPassword passwordInstance = null;
	private Context context;

	public DatabaseHelper(Context ctx) {
		// calls the super constructor, requesting the default cursor factory.
		super(ctx.getApplicationContext(), DATABASE_NAME, null,
				DATABASE_VERSION);
		context = ctx;
	}

	public boolean initialized() {
		return passwordInstance != null;
	}

	/**
	 * Create the tables and set a password
	 * 
	 * @param password
	 */
	public void initalizeInstance(String password) {
		Log.i("db", "Intializing database");
		if (passwordInstance == null) {
			setPassword(password);
			SQLiteDatabase.loadLibs(context);
			db = this.getWritableDatabase(password);
		}
	}

	public void setPassword(String password) {
		if (passwordInstance != null) {
			passwordInstance.forgetPassword();
		}
		passwordInstance = MasterPassword.getInstance(password);
	}

	public void forgetPassword() {
		passwordInstance.forgetPassword();
	}

	/**
	 * Erase the entire database file.
	 * 
	 * @return true if DB was deleted, false otherwise.
	 */
	public boolean wipeDB() {
		if (db != null) {
			if (tableExists(MESSAGES_TABLE)) {
				db.rawExecSQL("DROP TABLE " + MESSAGES_TABLE);
			}
			if (tableExists(CONTACTS_TABLE)) {
				db.rawExecSQL("DROP TABLE " + CONTACTS_TABLE);
			}
			createTables(db);
			return true;
		}
		return false;
	}

	private void createTables(SQLiteDatabase db) {
		db.execSQL(CREATE_MESSAGES_COMMAND);
		db.execSQL(CREATE_CONTACTS_COMMAND);
	}

	private boolean tableExists(String table_name) {

		String condition = "tbl_name = ?";
		Cursor cursor = getReadableDatabase(
				passwordInstance.getPasswordString()).query("sqlite_master",
						new String[] { "tbl_name" }, condition,
						new String[] { table_name }, null, null, null);

		if (cursor != null) {
			if (cursor.getCount() > 0) {
				cursor.close();
				return true;
			}
			cursor.close();
		}
		return false;
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		createTables(db);
		GenerateKeysTask task = new GenerateKeysTask();
		task.execute(new String[] { null });
	}

	// Don't do anything on upgrade! But must implement to work with schema
	// changes.
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

	}

	public SealablePublicKey getSealablePublicKey() {
		return getKeyPairsProvider().getExternalKey();
	}

	private KeyPairsProvider getKeyPairsProvider() {
		SecurePreferences encryptedPublicKey = new SecurePreferences(context,
				TOUCH_TO_TEXT_PREFERENCES_XML,
				passwordInstance.getPasswordString(), true);

		String publicKeyString = encryptedPublicKey.getString(PUBLIC_KEY);
		KeyPairsProvider kp = (KeyPairsProvider) Helpers.deserialize(Base64
				.decode(publicKeyString, Base64.DEFAULT));
		return kp;
	}

	public KeyPair getSigningKey() {
		return getKeyPairsProvider().getSigningKey();
	}

	public KeyPair getTokenKeyPair() {

		return getKeyPairsProvider().getTokenKey();
	}

	public void addContact(Contact newContact) {
		AddContactsToDBTask task = new AddContactsToDBTask();
		task.execute(new Contact[] { newContact });
	}

	public void addOutgoingMessage(final SignedMessage signedMessage,
			Contact contact) {
		long time = 0;
		try {
			time = signedMessage.getMessage(getSigningKey().getPublic())
					.getTimeSent();
		} catch (GeneralSecurityException e) {
			Log.wtf("Touch-to-text",
					"Your keys may have been tampered with!?!?", e);
		} catch (IOException e) {
			Log.d("Touch-to-text", "Error deserializing signed message", e);
			time = System.currentTimeMillis();
		} catch (ClassNotFoundException e) {
			Log.d("Touch-to-text",
					"Error, class not found in addOutgoingMessage", e);
			time = System.currentTimeMillis();
		}

		AddMessageToDBTask task = new AddMessageToDBTask();
		ContentValues newMessage = new ContentValues();
		newMessage.put(MESSAGE_BODY, Helpers.serialize(signedMessage));
		newMessage.put(DATE_TIME, time);
		newMessage.put(RECIPIENT_ID, contact.getID());
		newMessage.put(SENDER_ID, MY_CONTACT_ID);
		newMessage.put(READ, 1);
		task.execute(new ContentValues[] { newMessage });
		// For sorting purposes, update last contacted.
		UpdateLastContactedTask last = new UpdateLastContactedTask();
		last.execute(new Long[] { contact.getID(), time });
	}

	// { MESSAGES_ID, DATE_TIME, MESSAGE_BODY, SENDER_ID, RECIPIENT_ID };

	/**
	 * Receive a message from a given contact. If the message contains a new
	 * token, update the token in the Sealable Public Key. Always generate 
	 * a new token to provide with the next outgoing message.
	 * @param message
	 * @throws GeneralSecurityException
	 */
	public void addIncomingMessage(ProtectedMessage message)
			throws GeneralSecurityException {
		//Get your own keys
		KeyPairsProvider provider = getKeyPairsProvider();
		try {
			SignedMessage recieved = message.getMessage(provider
					.getEncryptionKey().getPrivate());

			PublicKey author = recieved.getAuthor();
			long time = recieved.getMessage(author).getTimeSent();

			// Add unread, new message to DB
			ContentValues newMessage = new ContentValues();
			newMessage.put(MESSAGE_BODY, Helpers.serialize(recieved));
			newMessage.put(DATE_TIME, time);
			newMessage.put(RECIPIENT_ID, MY_CONTACT_ID);
			newMessage.put(READ, 0);

			// Use the key fingerprint to get the contactID.
			String keyFingerprint = Helpers.getKeyFingerprint(author);
			long contactID = getContactFromPublicKeySignature(keyFingerprint);
			newMessage.put(SENDER_ID, contactID);
			getReadableDatabase(passwordInstance.getPasswordString()).insert(
					MESSAGES_TABLE, null, newMessage);

			// For sorting purposes, update last contacted.
			updateLastContacted(contactID, time);

			// Update the tokens for this contact
			SignedObject recievedToken = message.getToken(provider.getEncryptionKey().getPrivate());
			updateToken(contactID, recievedToken);
		} catch (IOException e) {
			Log.d("Touch-to-text", "Error deserializing signed message", e);
		} catch (ClassNotFoundException e) {
			Log.d("Touch-to-text",
					"Error, class not found in addIncomingMessage", e);
		}
	}

	private void updateLastContacted(long contactID, long dateTime) {	
		ContentValues updateDateContacted = new ContentValues();
		updateDateContacted.put(CONTACTS_ID, contactID);
		updateDateContacted.put(DATE_TIME, dateTime);
		getReadableDatabase(passwordInstance.getPasswordString()).update(
				CONTACTS_TABLE, updateDateContacted,
				CONTACTS_ID + "=" + contactID, null);
	}

	private SealablePublicKey getContactSPK(long contactID) {
		Cursor cursor = null;
		try {
			String sortOrder = DATE_TIME + " DESC";
			String query = CONTACTS_ID + " = " + contactID;
			cursor = getReadableDatabase(
					passwordInstance.getPasswordString()).query(CONTACTS_TABLE,
							CONTACT_CURSOR_COLUMNS,
							query,null, null, null, sortOrder);
			if( cursor.getCount() < 1) {
				Log.wtf("touch-to-text", "Recieved message from unknown contact");
				return null;
			} else {
				cursor.moveToFirst();
				return (SealablePublicKey) Helpers.deserialize(cursor.getBlob(cursor.getColumnIndex(PUBLIC_KEY)));
			}
		} finally {
			cursor.close();
		}
	}

	/**
	 * Add the new token you received from a user to their SealablePublicKey.
	 * Also, to enable blacklisting and prevent social graph analysis, 
	 * generate a new token to provide to that individual next time you send a message.
	 * @param contactID The contact in question
	 * @param newToken The token received
	 */
	private void updateToken(long contactID, SignedObject newToken ) {
		SealablePublicKey currentContact = getContactSPK(contactID);
		SealablePublicKey updatedContact = new SealablePublicKey(currentContact, newToken);
		ContentValues updateContactToken = new ContentValues();
		updateContactToken.put(PUBLIC_KEY, Helpers.serialize(updatedContact));
		SignedObject outgoingToken = null;
		try {
			outgoingToken = new SignedObject(
					UUID.randomUUID(),
					getKeyPairsProvider().getTokenKey().getPrivate(), 
					Signature.getInstance("DSA", "SC"));
		} catch (GeneralSecurityException e) {
			Log.wtf("touch-to-text", "Problem creating new token!",e);
		} catch (IOException e) {
			Log.wtf("touch-to-text", "Problem creating new token!");
		}
		if ( outgoingToken != null ) {
			updateContactToken.put(CONTACT_TOKEN, Helpers.serialize(outgoingToken));
			getReadableDatabase(passwordInstance.getPasswordString()).update(
					CONTACTS_TABLE, updateContactToken,
					CONTACTS_ID + "=" + contactID, null);
		}
	}

	/**
	 * Update last contacted for a given contactID.
	 * 
	 * @author dannyiland
	 * @param contactID
	 * @param dateTime
	 */
	private class UpdateLastContactedTask extends AsyncTask<Long, Void, Void> {
		@Override
		protected Void doInBackground(Long... toAdd) {
			updateLastContacted(toAdd[0], toAdd[1]);
			return null;
		}
	}

	public Cursor getContactsCursor() {
		String sortOrder = DATE_TIME + " DESC";
		Cursor cursor = getReadableDatabase(
				passwordInstance.getPasswordString()).query(CONTACTS_TABLE,
						CONTACT_CURSOR_COLUMNS, null, null, null, null, sortOrder);
		return cursor;
	}

	/**
	 * Returns the ID of a given contact
	 * @param keySignature
	 * @return
	 */
	public long getContactFromPublicKeySignature(String keySignature) {
		Cursor cursor = null;
		try {
			String sortOrder = DATE_TIME + " DESC";
			String query = PUBLIC_KEY_FINGERPRINT + " = ?";
			cursor = getReadableDatabase(
					passwordInstance.getPasswordString()).query(CONTACTS_TABLE,
							CONTACT_CURSOR_COLUMNS,
							query, new String[] { keySignature }, null, null, sortOrder);
			if( cursor.getCount() < 1) {
				Log.wtf("touch-to-text", "Recieved message from unknown contact");
				return -1;
			} else {
				cursor.moveToFirst();
				return cursor.getLong(cursor.getColumnIndex(CONTACTS_ID));
			}
		} finally {
			cursor.close();
		}
	}

	private class AddMessageToDBTask extends
	AsyncTask<ContentValues, Void, Void> {
		@Override
		protected Void doInBackground(ContentValues... toAdd) {
			for (ContentValues val : toAdd) {
				getReadableDatabase(passwordInstance.getPasswordString())
				.insert(MESSAGES_TABLE, null, val);
			}
			return null;
		}
	}

	private class AddContactsToDBTask extends AsyncTask<Contact, Void, Void> {
		@Override
		protected Void doInBackground(Contact... toAdd) {
			for (Contact newContact : toAdd) {
				ContentValues newUser = new ContentValues();
				newUser.put(NICKNAME, newContact.toString());
				newUser.put(PUBLIC_KEY,
						Helpers.serialize(newContact.getSealablePublicKey()));
				newUser.put(DATE_TIME, System.currentTimeMillis());
				newUser.put(PUBLIC_KEY_FINGERPRINT, newContact
						.getSealablePublicKey().signingKeyFingerprint());
				newUser.put(CONTACT_TOKEN, Helpers.serialize(newContact.getToken()));
				getReadableDatabase(passwordInstance.getPasswordString())
				.insert(CONTACTS_TABLE, null, newUser);
			}
			return null;
		}
	}

	public Cursor getMessagesCursor(long id) {
		Cursor cursor = null;
		String sortOrder = DATE_TIME + " ASC";
		String condition = RECIPIENT_ID + "=" + id + " OR " + SENDER_ID + "="
				+ id;
		cursor = getReadableDatabase(passwordInstance.getPasswordString())
				.query(MESSAGES_TABLE, MESSAGES_CURSOR_COLUMNS, condition,
						null, null, null, sortOrder);

		return cursor;

	}

	public Cursor getContactCursor() {
		String sortOrder = DATE_TIME + " DESC";
		Cursor cursor = getReadableDatabase(
				passwordInstance.getPasswordString()).query(CONTACTS_TABLE,
						new String[] { CONTACTS_ID, PUBLIC_KEY, DATE_TIME, NICKNAME },
						null, null, null, null, sortOrder);
		return cursor;
	}

	private class GenerateKeysTask extends AsyncTask<String, Void, Void> {

		@Override
		protected void onPreExecute() {
			// TODO Add "generating keys" notification
		}

		@Override
		protected Void doInBackground(String... names) {
			SecurePreferences encryptedPublicKey = new SecurePreferences(
					context, TOUCH_TO_TEXT_PREFERENCES_XML,
					passwordInstance.getPasswordString(), true);
			PowerManager pm = (PowerManager) context
					.getSystemService(Context.POWER_SERVICE);
			WakeLock mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
					"Touch To Text Key Generation");
			mWakeLock.acquire();
			KeyPairsProvider kp = null;
			try {
				kp = new KeyPairsProvider();
				encryptedPublicKey.put(PUBLIC_KEY, Base64.encodeToString(
						Helpers.serialize(kp), Base64.DEFAULT));
				GCMRegistrar.register(context, context.getResources()
						.getString(R.string.GCM_Sender_ID));
			} finally {
				mWakeLock.release();
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void evil) {
			// TODO Set "done generating keys" notification
		}
	}

	public SignedObject getOutgoingToken(long id) {
		ContentValues updateContactToken = new ContentValues();
		SignedObject outgoingToken = null;
		try {
			outgoingToken = new SignedObject(
					UUID.randomUUID(),
					getKeyPairsProvider().getTokenKey().getPrivate(), 
					Signature.getInstance("DSA", "SC"));
		} catch (GeneralSecurityException e) {
			Log.wtf("touch-to-text", "Problem creating new token!",e);
		} catch (IOException e) {
			Log.wtf("touch-to-text", "Problem creating new token!");
		}
		if ( outgoingToken != null ) {
			updateContactToken.put(CONTACT_TOKEN, Helpers.serialize(outgoingToken));
			getReadableDatabase(passwordInstance.getPasswordString()).update(
					CONTACTS_TABLE, updateContactToken,
					CONTACTS_ID + "=" + id, null);
		}
		return outgoingToken;
	}
}


