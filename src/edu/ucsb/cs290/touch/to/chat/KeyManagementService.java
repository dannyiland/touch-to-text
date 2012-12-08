package edu.ucsb.cs290.touch.to.chat;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateException;
import java.util.Timer;
import java.util.TimerTask;

import com.google.android.gcm.GCMRegistrar;

import net.sqlcipher.database.SQLiteDebug.DbStats;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.RemoteViews;
import edu.ucsb.cs290.touch.to.chat.crypto.DatabaseHelper;
import edu.ucsb.cs290.touch.to.chat.crypto.KeyPairsProvider;
import edu.ucsb.cs290.touch.to.chat.https.TorProxy;
import edu.ucsb.cs290.touch.to.chat.remote.messages.ProtectedMessage;
import edu.ucsb.cs290.touch.to.chat.remote.register.RegisterUser;

public class KeyManagementService extends Service {
	private DatabaseHelper dbHelperInstance;
	private volatile KeyPairsProvider kp;
	private Timer timer;
	private static final String TAG = KeyManagementService.class
			.getSimpleName();
	private final IBinder binder = new KeyCachingBinder();
	private static final int SERVICE_RUNNING_ID = 155296813;
	private static final String CLEAR_MEMORY = "edu.ucsb.cs290.touch.to.chat.ClearMemory";
	static final String EXIT = "edu.ucsb.cs290.touch.to.chat.Exit";
	static final String UPDATE_REG = "edu.ucsb.cs290.touch.to.chat.reg";
	private static volatile boolean isRunning;
	private static volatile KeyManagementService thisService;
	
	public static DatabaseHelper getStatic () {
		//TODO HERE BE DRAGONS
		if(isRunning && thisService != null && thisService.getInstance().initialized()) {
			return thisService.getInstance();
		} 
		return null;
	}
	
	public KeyPairsProvider getKeys() {
		return kp;
	}

	public DatabaseHelper getInstance() {
		if (dbHelperInstance == null) {
			// Use global context for the app
			dbHelperInstance = new DatabaseHelper(this);
		}
		return dbHelperInstance;
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return binder;
	}

	public class KeyCachingBinder extends Binder {
		public KeyManagementService getService() {
			return KeyManagementService.this;
		}
	}

	private TimerTask expireTask = new TimerTask() {
		@Override
		public void run() {
			Log.i(TAG, "Timer task doing work");
		}
	};

	@Override
	public void onCreate() {
		super.onCreate();
		Log.i(TAG, "Service creating");
		thisService = this;
		timer = new Timer("KeyExpirationTimer");
		//timer.schedule(expireTask, 1000L, 60 * 1000L);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		isRunning = false;
		thisService = null;
		Log.i(TAG, "Service destroying");
		((NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(SERVICE_RUNNING_ID);
		timer.cancel();
		timer = null;
	}

	@Override
	public int onStartCommand(Intent intent, int i, int j) {
		Log.i("kmg", "On start command called");
		if(intent!= null && CLEAR_MEMORY.equals(intent.getAction())) {
			clearKey();
			LocalBroadcastManager.getInstance(this).sendBroadcastSync(new Intent(EXIT));
		} if((intent != null && UPDATE_REG.equals(intent.getAction())) || this.getSharedPreferences("touchToTextPreferences.xml", MODE_PRIVATE).getBoolean("GCM ready", false)) {
			Log.d("touch-to-text", "Sending server reg key");
			this.getSharedPreferences("touchToTextPreferences.xml", MODE_PRIVATE).edit().remove("GCM ready").commit();
			new AsyncTask<String, Void, Void>() {

				@Override
				protected Void doInBackground(String... params) {
					try {
						TorProxy.postThroughTor(getApplicationContext(), 
								new RegisterUser(params[0],
								getInstance().getTokenKeyPair(), 1000));
					} catch (CertificateException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (GeneralSecurityException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					return null;
				}
				
			}.execute(GCMRegistrar.getRegistrationId(getApplicationContext()));
		}

		return START_STICKY;
	}

	private void clearKey() {
		dbHelperInstance.forgetPassword();
		kp = null;
		this.stopSelf();
	}

	@TargetApi(16)
	public void startNotification() {
		RemoteViews remoteView = new RemoteViews(getPackageName(), R.layout.notification_message);
		Intent clearMemory = new Intent(this, KeyManagementService.class);
		clearMemory.setAction(CLEAR_MEMORY);
		PendingIntent clearMemoryIntent = PendingIntent.getService(getApplicationContext(), 0, clearMemory, 0);
		remoteView.setOnClickPendingIntent(R.id.lock_cache_icon,clearMemoryIntent);
		Builder builder = new Notification.Builder(this);
		builder
		.setSmallIcon(android.R.drawable.ic_lock_lock)
		.setContentTitle("Touch to Text is Running")
		.setContentText("Touch Lock to Clear Memory")
		.setWhen(System.currentTimeMillis())
		.setContent(remoteView)
		.setOngoing(true);
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
			builder.setPriority(Notification.PRIORITY_LOW);
		}
		Notification statusNotification = builder.build();
		stopForeground(true);
		startForeground(SERVICE_RUNNING_ID, statusNotification);		 
	}
}
