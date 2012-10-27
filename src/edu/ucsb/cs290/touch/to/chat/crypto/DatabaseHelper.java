package edu.ucsb.cs290.touch.to.chat.crypto;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Base64;

/**
 *
 * Instantiate and provide access to the DB, which contains
 * Messages, Contacts, and secure private key storage.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

	// DB Strings
	public static final String MESSAGES_TABLE = "Messages";
	public static final String MESSAGES_ID = "messages_id";
	public static final String THREAD_ID = "threadId";
	public static final String CONTACTS_TABLE = "Contacts";
	public static final String CONTACTS_ID = "_id";
	public static final String LOCAL_STORAGE = "LocalStorage";

	private static final String ID = "_id";
	private static final String NICKNAME = "nickname";
	private static final String CONTACT_ID = "contactId";
	private static final String DATE_TIME = "dateTime";
	private static final String SUBJECT = "subject";
	private static final String MESSAGE_BODY = "messageBody";
	private static final String HASH_MATCHES = "hashVerifed";
	private static final String SIGNATURE_MATCHES = "signatureVerifed";
	private static final String ATTACHMENT = "attachmentBlob";
	private static final String READ = "read"; // 1 if read,  0 for unread

	// NICKNAME
	private static final String TOKEN = "token";
	private static final String CONTACT_NOTE = "note";
	private static final String VERIFIED_BY = "verifiers";
	private static final String PRIVATE_KEY = "privateKey";
	private static final String PUBLIC_KEY = "publicKey";
	private static final String KEYPAIR_NAME = "keyName";


	// Messages: _id, thread_id, nickname, CONTACT_ID, timestamp, hash_matches, read, signature_matches, subject, body, attachment
	private static final String CREATE_MESSAGES_COMMAND = 
			"CREATE TABLE " + MESSAGES_TABLE + " (" + MESSAGES_ID + " integer PRIMARY KEY autoincrement, " +
					THREAD_ID + " INTEGER, " + NICKNAME + " TEXT, " + CONTACT_ID + " INTEGER, " + DATE_TIME  + " INTEGER, " +
					HASH_MATCHES + " INTEGER DEFAULT 0, " + READ + " INTEGER DEFAULT 0, " +
					SIGNATURE_MATCHES + " INTEGER DEFAULT 0, " + SUBJECT + " TEXT, " + MESSAGE_BODY + " TEXT, " + ATTACHMENT + " BLOB);";

	// Contacts: _id, name, CONTACT_ID, timestamp (added), verified_by (_ids), token, note
	private static final String CREATE_CONTACTS_COMMAND = "CREATE TABLE " + CONTACTS_TABLE + " (" + CONTACTS_ID + " integer PRIMARY KEY autoincrement, " +
			NICKNAME + " TEXT, " + PUBLIC_KEY + " TEXT, " + DATE_TIME  + " INTEGER, " +
			VERIFIED_BY + " TEXT, " + TOKEN + " TEXT, " + CONTACT_NOTE + " TEXT);";

	// LocalStorage: _id, private key, public key, timestamp (added), name
	private static final String CREATE_LOCAL_STORAGE_COMMAND = "CREATE TABLE " + LOCAL_STORAGE + " (" + ID + " integer PRIMARY KEY, " +
			PRIVATE_KEY + " TEXT, " + PUBLIC_KEY + " TEXT, " + DATE_TIME  + " INTEGER, " + KEYPAIR_NAME + " TEXT);";

	private static final String DATABASE_NAME = "touchToText.db";
	private static final int DATABASE_VERSION = 1;


	// Databases and Context
	private  File dbFile=null;
	private SQLiteDatabase db;
	private MasterPassword passwordInstance = null;
	private Context context;
	private SealablePublicKey publicKey;
	// The singleton instance
	private static DatabaseHelper dbHelperInstance = null;

	DatabaseHelper(Context ctx) {
		// calls the super constructor, requesting the default cursor factory.
		super(ctx.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
		context = ctx;
	}

	public static DatabaseHelper getInstance(Context ctx) {
		if (dbHelperInstance == null) {
			// Use global context for the app
			dbHelperInstance = new DatabaseHelper(ctx.getApplicationContext());
		}
		return dbHelperInstance;
	}

	public void initalizeInstance(String password) {
		if (dbHelperInstance != null && dbHelperInstance.passwordInstance == null) {
			// Use global context for the app
			dbHelperInstance.setPassword(password);
			createTables(getDatabase(context));

		}
	}

	/** Will only work if the db is already unlocked with the current password
	 * Otherwise I think it will fail silently? Should test and see what 'e' is.
	 */
	public boolean changePassword(String newPassword) {
		try {
			passwordInstance.forgetPassword();
			passwordInstance = new MasterPassword(newPassword);
			getDatabase(context).rawExecSQL(String.format("PRAGMA key = '%s'", passwordInstance.getPassword().toString()));
			return true;
		} catch( Exception e) {
			e.printStackTrace();
			return false;
		}
	}


	void insertKeypair(byte[] privateKeyRing, byte[] publicKeyRing, String name ) {
		ContentValues cv = new ContentValues();
		if(privateKeyRing == null) {
			cv.put(PUBLIC_KEY, publicKeyRing);
			cv.put(DATE_TIME, System.currentTimeMillis());
			cv.put(NICKNAME, name);
			db.insert(CONTACTS_TABLE, null, cv);
		} else {
			cv.put(PRIVATE_KEY, privateKeyRing);
			cv.put(PUBLIC_KEY, publicKeyRing);
			cv.put(DATE_TIME, System.currentTimeMillis());
			cv.put(KEYPAIR_NAME, name);
			db.insert(LOCAL_STORAGE,null,cv);	
		}
	}

	public void setPassword(String password) {
		passwordInstance = MasterPassword.getInstance(password);
	}

	/**
	 * Erase the entire database file.
	 * 
	 * @return true if DB was deleted, false otherwise.
	 */
	public boolean wipeDB() {
		if(db != null) {
			db.close();
			db = null;
		}
		return dbFile.delete();
	}

	/**
	 * Returns a reference to the database object, and creates
	 * it if it has not yet been used. Context required for initial
	 * creations, after that it doesn't matter.
	 * @param context The application context, required on first use.
	 * @return
	 */
	public SQLiteDatabase getDatabase(Context context) {
		if (db == null) {
			SQLiteDatabase.loadLibs(context);
			dbFile = context.getDatabasePath(DATABASE_NAME);
			dbFile.mkdirs();
			dbFile.delete();
			File databaseFile=null;
			try {
				databaseFile = new File(context.getDatabasePath(DATABASE_NAME).toString()+DATABASE_NAME);
				databaseFile.mkdirs();
				db = SQLiteDatabase.openOrCreateDatabase(dbFile, passwordInstance.getPassword().toString(), null);
			} catch(Exception e) {
				db = SQLiteDatabase.openOrCreateDatabase(databaseFile, passwordInstance.getPassword().toString(), null);

				Logger.getLogger("touch-to-text").log(Level.SEVERE,
						"Unable to open database!", e);
			}
		}
		return db;
	}

	private void createTables(SQLiteDatabase db) {
		db.execSQL(CREATE_MESSAGES_COMMAND);
		db.execSQL(CREATE_CONTACTS_COMMAND);
		db.execSQL(CREATE_LOCAL_STORAGE_COMMAND);
	}



	@Override
	public void onCreate(SQLiteDatabase db) {
		System.out.println("OnCreate called for db" + db.getPath().toString());
	}


	// Don't do anything on upgrade! But must implement to work with schema changes.
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

	}

	public SealablePublicKey getPGPPublicKey() {
		String name = "myname";
		if(publicKey == null) {
			SecurePreferences encryptedPublicKey = new  SecurePreferences(
					context, "touchToTexPreferences.xml",
					passwordInstance.getPassword().toString(),
					true);
			String publicKeyString = encryptedPublicKey.getString(PUBLIC_KEY);
			if(publicKeyString != null) {
				publicKey = new SealablePublicKey(Base64.decode(publicKeyString, Base64.DEFAULT));
			} else {
				Cursor cursor = getDatabase(context).query(LOCAL_STORAGE, new String[] {ID, PUBLIC_KEY, KEYPAIR_NAME}, 
						null, null, null, null, null);
				if(cursor.getCount()==0) {
					PGPKeys newKeys = new PGPKeys(context, name, passwordInstance.getPasswordProtection());
					publicKey = new SealablePublicKey(newKeys.getPublicKey(), name);
				} else {
					String base64PublicKey = cursor.getString(1);
					name = cursor.getString(2);
					publicKey = new SealablePublicKey(base64PublicKey.getBytes(),name);
				}
			}
		}
		return publicKey;
	}

	public void addPublicKey(SealablePublicKey key) {
		insertKeypair(null, key.publicKey, key.identity);
	}

	public void addContact(String name, long date, SealablePublicKey key) {

		ContentValues cv = new ContentValues();
		cv.put(PUBLIC_KEY, key.publicKey);
		cv.put(DATE_TIME, System.currentTimeMillis());
		cv.put(NICKNAME, name);
		cv.put(TOKEN, key.token());
		db.insert(CONTACTS_TABLE, null, cv);
	}		
}
