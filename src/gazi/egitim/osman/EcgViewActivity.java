package gazi.egitim.osman;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import android.app.Activity;
import android.app.TabActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.Toast;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

public class EcgViewActivity extends TabActivity {
    private static final int REQUEST_ENABLE_BT = 1;
	protected static final int MESSAGE_READ = 0;
	private static final int MESSAGE_DEVICE_NAME = 1;
	private static final int MESSAGE_GRAPH_UPDATE = 2;
	private static final String DEVICE_NAME = "deviceName";
    private BluetoothAdapter mBluetoothAdapter;
    private int currentDeviceIndex = -1;
    private ConnectThread mConnectThread = null;
	
	private ListView devicesListView;
	private ArrayAdapter<String> arrayAdapter;
	
	private ConnectedThread mConnectedThread;
	private ServerConnection mServer;
	private TabHost mTabHost;
	private GraphView mGraphView;
	boolean useRealData = true;
	int sps;
	int passed = 0;
	int spsTotal = 0;
	
	boolean useBaseline = false;
	boolean useLpf = false;
	boolean useNotch = false;
	boolean useAdaptive = false;
	
	private Queue<Float> gBuffer = new LinkedList<Float>();
	
	private void initGraphics() {
		float[] data = new float[500];
		Random generator = new Random();
		for (int i = 0; i < data.length; i++) {
			data[i] = generator.nextFloat() * 32768;
		}
		initGraphics(data);
	}
	
	private void initGraphics(float[] data) {
		String[] verlabels = new String[] { "32768", "0", "-32768" };
		String[] horlabels = new String[] { "0", "100", "200", "300", "400", "500" };
		mGraphView = new GraphView(EcgViewActivity.this, 
				data, "GraphViewDemo",horlabels, verlabels, GraphView.LINE);
		LinearLayout layout = (LinearLayout) findViewById(R.id.GraphLayout1);
		layout.addView(mGraphView);
		if (!useRealData)
			mHandler.postDelayed(mGraphUpdateTask, 1000);
	}
	
	private void initTabs(int current) {
		mTabHost.setOnTabChangedListener(new OnTabChangeListener(){
			final CheckBox checkFilter = (CheckBox) findViewById(R.id.checkFilter);
			final CheckBox checkFilter2 = (CheckBox) findViewById(R.id.checkFilter2);
			final CheckBox checkFilter3 = (CheckBox) findViewById(R.id.checkFilter3);
			final CheckBox checkFilter4 = (CheckBox) findViewById(R.id.checkFilter4);
			final CheckBox checkAutoScale = (CheckBox) findViewById(R.id.checkAutoScale);
			@Override
			public void onTabChanged(String tabId) {
				Log.v("filters", "lpf:" + checkFilter2.isChecked());
			    if(tabId.equals("tab_test4")) {
			    	useBaseline = checkFilter.isChecked();
			    	useLpf = checkFilter2.isChecked();
			    	useNotch = checkFilter3.isChecked();
			    	useAdaptive = checkFilter4.isChecked();
			    	mGraphView.setAutoScale(checkAutoScale.isChecked());
			    }
			}});
		
    	mTabHost.addTab(mTabHost.newTabSpec("tab_test1").setIndicator("Cihaz").setContent(R.id.ListView1));
        mTabHost.addTab(mTabHost.newTabSpec("tab_test2").setIndicator("TAB 2").setContent(R.id.settingsView));
        mTabHost.addTab(mTabHost.newTabSpec("tab_test3").setIndicator("TAB 3").setContent(R.id.textview2));
        mTabHost.addTab(mTabHost.newTabSpec("tab_test4").setIndicator("Grafik").setContent(R.id.GraphLayout1));
        mTabHost.setCurrentTab(current);
	}
	
	private void initThreads() {
		mServer = new ServerConnection();
        new Thread(mServer).start();
        //new Thread(new ClientThread()).start();
	}
	
	private void initBtDevices() {
		devicesListView = (ListView)findViewById(R.id.ListView1);
        devicesListView.setOnItemClickListener(new OnItemClickListener() {
        	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        		String macaddr = arrayAdapter.getItem(position).split("\n")[1];
        		//BluetoothDevice device = mBluetoothAdapter.getRemoteDevice("00:1D:43:00:C3:E0");
        		BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(macaddr);
        		currentDeviceIndex = position;
        		try {
        			mConnectThread = new ConnectThread(device);
        			mConnectThread.start();
				} catch (Exception e) {
					// TODO: handle exception
					Toast.makeText(getApplicationContext(), e.toString(), 2000).show();
				}
        	}
        });
        arrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        devicesListView.setAdapter(arrayAdapter);
        
      //Register the BroadcastReceiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy
        
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
        }
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else
        	findBluetoothDevices();
	}

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.main);
        mTabHost = getTabHost();
        
        if (savedInstanceState == null) {
        	initGraphics();        	
        	initTabs(0);
            
            //LocationManager locationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
            //locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);	
		} else {
			try {
				/* need to inflate layout, otherwise we crash */
				//getLayoutInflater().inflate(R.layout.main, mTabHost.getTabContentView(), true);
				initGraphics(savedInstanceState.getFloatArray("lastGraphData"));
				initTabs(savedInstanceState.getInt("currentTab"));
			} catch (Exception e) {
				Toast.makeText(this, e.toString(), 5000).show();
			}
		}
        initThreads();
        initBtDevices();
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
    	super.onSaveInstanceState(outState);
    	outState.putInt("currentTab", mTabHost.getCurrentTab());
    	outState.putFloatArray("lastGraphData", mGraphView.getCurrentValues());
    }
    
    @Override
    protected void onDestroy()
    {
    	super.onDestroy();
    	unregisterReceiver(mReceiver);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
    	if (resultCode == RESULT_OK) {
    		findBluetoothDevices();
    	}
    }
    
    private LocationListener locationListener = new LocationListener() {
    	public void onLocationChanged(Location location) {
    		Log.v("gps", "new location: " + location.toString());
    		Toast.makeText(getApplicationContext(), location.toString(), 5000).show();
    	}

		@Override
		public void onProviderDisabled(String arg0) {
			// TODO Auto-generated method stub
			Log.v("gps", "disabled");
			Intent in = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);   
	        startActivity(in);
		}

		@Override
		public void onProviderEnabled(String provider) {
			// TODO Auto-generated method stub
			Log.v("gps", "enabled");
		}

		@Override
		public void onStatusChanged(String provider, int status,
				Bundle extras) {
			// TODO Auto-generated method stub
			
		}
    };
    
    private void findBluetoothDevices()
    {
    	Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
    	// If there are paired devices
    	if (pairedDevices.size() < 0) { //always discover for now
    	    // Loop through paired devices
    	    for (BluetoothDevice device : pairedDevices) {
				// Add the name and address to an array adapter to show in a ListView
    	        arrayAdapter.add(device.getName() + "\n" + device.getAddress());
    	    }
    	} else {
    		Toast.makeText(this, "Starting discovery of ECG devices...", 1000).show();
    		mBluetoothAdapter.startDiscovery();
    	}
    }
    
    //Create a BroadcastReceiver for ACTION_FOUND
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // Add the name and address to an array adapter to show in a ListView
                arrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        }
    };
    
    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device) {

    	if (socket == null) {
        	Log.v("bt", "null socket");
        	return;
        }
    	
        // Cancel the thread that completed the connection
        //if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // Cancel any thread currently running a connection
        //if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

    	Log.v("bt", "connected to device " + device.getName());
    	
        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        
        //mHandler.postDelayed(btPeriodicSend, 1000);

        //setState(STATE_CONNECTED);
    }
    
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
     
        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;
            mmDevice = device;
     
            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                // MY_UUID is the app's UUID string, also used by the server code
                tmp = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
            } catch (IOException e) { }
            mmSocket = tmp;
        }
     
        public void run() {
        	// Cancel discovery because it will slow down the connection
            mBluetoothAdapter.cancelDiscovery();
            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                try {
                    mmSocket.close();
                } catch (IOException closeException) { }
                Log.v("bt", "connectException: " + connectException.toString());
                return;
            }
     
            // Do work to manage the connection (in a separate thread)
            connected(mmSocket, mmDevice);
        }
     
        /** Will cancel an in-progress connection, and close the socket */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { 
            	Toast.makeText(getApplicationContext(), "can not cancel", 2000).show();
            }
        }
    }
    
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
     
        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
     
            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }
     
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }
     
        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()
     
            // Keep listening to the InputStream until an exception occurs
            Log.v("bt", "waiting for incoming bt data");
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    byte[] sendBuffer = new byte[bytes];
                    System.arraycopy(buffer, 0, sendBuffer, 0, bytes);
                    // Send the obtained bytes to the UI activity
                    mHandler.obtainMessage(MESSAGE_READ, bytes, -1, sendBuffer)
                            .sendToTarget();
                    //Log.v("bt", bytes + " bytes read from bt device");
                } catch (IOException e) {
                    break;
                }
            }
        }
     
        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) { 
            	Log.v("bt", "write error: " + e.toString());
            }
        }
     
        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }
    
    private Runnable btPeriodicSend = new Runnable() {
    	public void run() {
    		String s = "Hello there\n\r";
    		mConnectedThread.write(s.getBytes());
    		mHandler.postDelayed(this, 100);
    		Log.v("bt", "periodic send");
    	}
    };
    
    // The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() {
    	private int state = 0x12;
    	private int count = 0;
    	private int low;
    	private int high;
    	int xor = 0;
    	byte[] fBuffer = new byte[1024];
    	//float [] gBuffer = new float[1024 / 2];
    	int fPos = 0;
    	private int checkByte(byte b) {
    		switch (state) {
			case 0x12:
				xor = 0;
				count = 0;
				if (b == 0x12)
					state = 0x34;
				break;
			case 0x34:
				if (b == 0x34)
					state = 0x56;
				else
					state = 0x12;
				break;
			case 0x56:
				if (b == 0x56)
					state = 0x78;
				else
					state = 0x12;
				break;
			case 0x78:
				if (b == 0x78)
					state = 0x99;
				else
					state = 0x12;
				break;
			case 0x99:
				int ret = 1;
				if ((count & 0x1) == 0)
					low = b;
				else {
					high = b;
					//Log.v("btdata", "sample " + count / 2 + ": " + (low + high * 256));
					ret = 2;
				}
				if (++count == 200) {
					//if (xor != b)
						//Log.v("bt", "data error !!!");
					state = 0x12;
				} else
					xor ^= b;
				return ret;
			default:
				state = 0x12;
				break;
			}
    		return 0;
    	}
    	
    	int bufferLen = 83;
    	private float[] filterBuffer = new float[bufferLen];
    	
    	private double[] lpf95 = { 4.14419526790981e-08,	6.11194716823034e-07,
    			4.36444420771491e-06,	2.00461827828929e-05,	6.63177754011245e-05,
    			0.000167712939202544,	0.000335857076402041,	0.000544384737916168,
    			0.000723850547541967,	0.000795352502748648,   0.000723850547541967,
    			0.000544384737916168,	0.000335857076402041,	0.000167712939202544,
    			6.63177754011245e-05,	2.00461827828929e-05,	4.36444420771491e-06,
    			6.11194716823034e-07,	4.14419526790981e-08
    			};
    	private double[] lpf50 = { 0.00365940233550856,	0.0139072024889790,
    			0.0331252259609651,	0.0591580641475665,	0.0849136426174818,	0.101082711907058,	
    			0.101082711907058,	0.0849136426174818,	0.0591580641475665,	0.0331252259609651,
    			0.0139072024889790,	0.00365940233550856 
    			};
    	private double[] notch50 = { 0.00088583,0.0037832,0.0084075,0.011818,0.0098934,0.0021063,-0.0060389,-0.0069838,0.00034588,0.0077213,0.0057391,-0.0043154,-0.010214,-0.0030315,0.00987,0.011624,-0.0027096,-0.016452,-0.0099164,0.012446,0.022471,0.0025067,-0.026683,-0.025142,0.01449,0.046361,0.019298,-0.051863,-0.079066,0.015618,0.20553,0.36121,0.36121,0.20553,0.015618,-0.079066,-0.051863,0.019298,0.046361,0.01449,-0.025142,-0.026683,0.0025067,0.022471,0.012446,-0.0099164,-0.016452,-0.0027096,0.011624,0.00987,-0.0030315,-0.010214,-0.0043154,0.0057391,0.0077213,0.00034588,-0.0069838,-0.0060389,0.0021063,0.0098934,0.011818,0.0084075,0.0037832,0.00088583 };

    	private float averagingFilter(int len) {
    		float sum = 0;
    		for (int i = 0; i < len; i++)
    			sum += filterBuffer[bufferLen - len + i];
    		return sum / len;
    	}
    	
    	private float lpf(double[] filt) {
    		int len = filt.length;
    		float sum = 0;
    		for (int i = 0; i < len; i++)
    			sum += filterBuffer[bufferLen - len + i] * filt[i];
    		return sum;
    	}
    	
    	private float adaptiveFilter(float stepSize, float offset) {
    		float u = stepSize;
    		float b = offset;
    		
    		int signalSize = 10;
    		int filterSize = signalSize;
    		float[] x = new float[filterSize];
    		float[] output = new float[signalSize];
    		float[] error = new float[signalSize];
    		float[] weights = new float[filterSize];
    		for (int i = 0; i < filterSize; i++) {
    			x[i] = 0;
    			weights[i] = 0;
    			output[i] = 0; //TODO:
    			error[i] = 0; //TODO:
    		}
    		
    		float[] desired = new float[signalSize];
    		System.arraycopy(filterBuffer, bufferLen - signalSize - 10, desired, 0, signalSize);

    		for (int i = 0; i < signalSize; ++i) {
    			/* shift input data holder */
    			for (int j = filterSize - 1; j > 0; --j)
    				x[j] = x[j - 1];
    			x[0] = filterBuffer[bufferLen - signalSize + i];

    			/* filter data */
    			output[i] = 0;
    			float normX = 0;
    			for (int j = 0; j < filterSize; ++j) {
    				output[i] += weights[j] * x[j];
    				normX += x[j] * x[j];
    			}

    			/* update coefficients */
    			error[i] = desired[i] - output[i];
    			for (int j = 0; j < filterSize; ++j) {
    				weights[j] += u / (normX + b) * error[i] * x[j];
    			}
    		}
    		return output[signalSize - 1];
    	}

    	private float filterData(float data) {
    		if (useBaseline == false && useLpf == false)
    			return data;
    		//Log.v("filters", "filtering: " + filterData);
    		System.arraycopy(filterBuffer, 1, filterBuffer, 0, bufferLen - 1);
    		filterBuffer[bufferLen - 1] = data;
    		
    		if (useBaseline) {
    			/* baseline removal */
    			data -= averagingFilter(bufferLen);
    		}
    		
    		if (useLpf)
    			data = lpf(lpf95);
    		
    		if (useNotch)
    			data = lpf(notch50);
    		
    		if (useAdaptive)
    			data = adaptiveFilter(0.1f, 0);
    		
    		return data;
    	}
    	
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_READ:
            	int len = msg.arg1;
                byte[] readBuf = (byte[]) msg.obj;
                for (int i = 0; i < len; i++) {
                	if (checkByte(readBuf[i]) == 2) {
                		float data = filterData((float) (low + 256 * high));
                		gBuffer.offer(data);
                		//gBuffer.offer((float) (low + 256 * high));
                		
                		int newdat = (int)(data * 1000);
                		low = newdat & 0xff;
                		high = (newdat >> 8) & 0xff;
                		fBuffer[fPos] = (byte) low;
                		fBuffer[fPos + 1] = (byte) high;
                		fPos += 2;
                		if (fPos == fBuffer.length) {
                			mServer.sendData(fBuffer, fBuffer.length);
                			fPos = 0;
                		}
                	} else if (readBuf[i] == 0x78 && state == 0x99) {
				/* read data counter */
				Log.v("msgdbg", "reading counter");
			}
                }
                break;
            case MESSAGE_DEVICE_NAME:
            	String s = arrayAdapter.getItem(currentDeviceIndex);
            	arrayAdapter.clear();
            	arrayAdapter.add("Connected: " + s);
            	mHandler.postDelayed(mGraphUpdateTask, 1000);
            	break;
            case MESSAGE_GRAPH_UPDATE:
            	break;
            }
        }
    };
    
    private Runnable mGraphUpdateTask = new Runnable() {
    	public void run() {
    		if (useRealData) {
	    		int size = gBuffer.size();
	    		//if (size < gBuffer.size()) {
	    			float[] data = new float[size];
	    			for (int i = 0; i < size; i++)
	    				data[i] = gBuffer.remove();
	    			mGraphView.addData(data);
	    			spsTotal += data.length;
	    		//}
    		} else {
	    		float[] data = new float[50];
	    		Random generator = new Random();
	    		for (int i = 0; i < data.length; i++) {
	    			data[i] = generator.nextFloat() * 32768;
	    		}
	    		mGraphView.addData(data);
    		}
    		
    		passed += 50;
    		if (passed >= 1000) {
    			sps = spsTotal * 1000 / passed;
    			spsTotal = 0;
    			passed = 0;
    			Log.v("btstat", "sps is " + sps);
    		}
    		mHandler.postDelayed(mGraphUpdateTask, 50);
    	}
    };
    
    class ServerConnection implements Runnable {
    	Socket sock = null;;
    	DataOutputStream out;
    	DataInputStream in;
		@Override
		public void run() {
			Log.v("bt", "connecting to server...");
			while (true) {
				try {
					if (sock == null) {
						sock = new Socket("178.233.147.5", 17536);
						out = new DataOutputStream(sock.getOutputStream());
						in = new DataInputStream(sock.getInputStream());
					} else
						Thread.sleep(1000);
				} catch (UnknownHostException e) {
					Log.v("bt", "server connection error " + e.toString());
					//Toast.makeText(getApplicationContext(), "server connection error: " + e.toString(), 5000).show();
					sock = null;
				} catch (IOException e) {
					Log.v("bt", "server connection error " + e.toString());
					//Toast.makeText(getApplicationContext(), "server connection error: " + e.toString(), 5000).show();
					sock = null;
				} catch (InterruptedException e) {

				}
			}
		}
		
		synchronized public void sendData(byte[] buffer) {
			sendData(buffer, buffer.length);
		}
		
		synchronized public void sendData(byte[] buffer, int len) {
			try {
				int msglen = len + 4;
				byte[] msg = new byte[msglen];
				msg[0] = (byte) (msglen & 0xff);
				msg[1] = (byte) (msglen / 256);
				msg[2] = 0;
				msg[3] = 0;
				System.arraycopy(buffer, 0, msg, 4, len);
				out.write(msg);
			} catch (Exception e) {
				Log.v("bt", "server data send error");
				sock = null;
			}
		}
    	
    }
    
}