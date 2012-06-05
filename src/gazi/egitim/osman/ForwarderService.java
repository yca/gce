package gazi.egitim.osman;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class ForwarderService extends Service {
	private Thread clientThread;
	
	public class LocalBinder extends Binder {
		ForwarderService getService() {
			return ForwarderService.this; 
		}
	}
	public void onCreate() {
		super.onCreate();
		//Toast.makeText(this, "Service created...", Toast.LENGTH_LONG).show();
		clientThread = new Thread(new ClientThread());
		clientThread.start();
	}
	
	@Override
	public void onDestroy() {
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY;
	}
	
	public class ClientThread implements Runnable {
		public void run() {
			Log.v("service", "started");
		}
	}

	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return mBinder;
	}
	
	private final IBinder mBinder = new LocalBinder();

}
