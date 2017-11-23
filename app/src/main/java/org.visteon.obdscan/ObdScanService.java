package org.visteon.obdscan;

import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.github.pires.obd.commands.ObdCommand;

import org.vopen.android_sdk.obd_client.ObdGatewayClient;
import org.vopen.android_sdk.obd_client.ObdGatewayClientCallback;
import org.vopen.android_sdk.obd_service.ObdCommandJob;
import org.vopen.android_sdk.obd_service.ObdGatewayServiceIF;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Pattern;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;



public class ObdScanService extends Service {
    private static final String TAG = ObdScanService.class.getName();
    public final IBinder mBinder = new LocalBinder();
    public Callbacks app;
    public ObdGatewayClient mObdClient = null;
    private ObdCallbacks mObdCallbacks = null;
    private BrokerModule broker = null;
    private Queue MessageQueue = null;
    private boolean Connected = false;
    final List<String> macs = new ArrayList<>();
    List<String> peers = new ArrayList<>();
    private Long startedTimestamp = System.currentTimeMillis() / 1000;
    final BluetoothAdapter myBluetoothAdapter  = BluetoothAdapter.getDefaultAdapter();
    private boolean initialized = false;
    private volatile boolean running = true;


    public void registerClient(Activity activity){
        Log.d(MainActivity.class.getName(),"Registered activity client!!!");
        this.app = (Callbacks)activity;
        // Get bluetooth devices
        loadPairedConnections();
        // Instantiate broker
        if (broker == null)
            broker = new BrokerModule(ObdScanService.this);
        // Init OBD stuff
        if (mObdClient == null)
            mObdClient = new ObdGatewayClient();
        if (mObdCallbacks == null)
            mObdCallbacks = new ObdCallbacks();
        if (MessageQueue == null)
            MessageQueue = new LinkedList<>();
        // Start worker threads
        if (!initialized) {
            try {
                mCommThread.start();
                mQueueCommands.run();
            } catch (java.lang.IllegalThreadStateException e) {
                Log.d(MainActivity.class.getName(), "Thread already started");
            }
            initialized = true;
        }

    }


    private final Runnable mQueueCommands = new Runnable() {
        public void run() {
            Long currentTimestamp = System.currentTimeMillis() / 1000;
            SharedPreferences prefs = getDefaultSharedPreferences((MainActivity)app);
            if ((mObdClient!=null) && (mObdClient.isServiceBound())) {
                if (mObdClient.getAvailableCommands() != null) {
                    for (Map.Entry<ObdCommand, Map.Entry<Integer,Integer>> r: mObdClient.getAvailableCommands().entrySet()) {
                        if ((prefs.getInt(r.getKey().getCommandPID().replace(" ","") + "_reporting",0)!=0) && (((currentTimestamp-startedTimestamp) % prefs.getInt(r.getKey().getCommandPID().replace(" ","") + "_reporting",0) == 0))) {
                            mObdClient.queueCommand(r.getKey());
                            Log.d(MainActivity.class.getName(),"SENDING FOR REPORT");
                        }
                        else if ((prefs.getInt(r.getKey().getCommandPID().replace(" ","") + "_polling",0)!=0) && (((currentTimestamp-startedTimestamp) % prefs.getInt(r.getKey().getCommandPID().replace(" ","") + "_polling",0) == 0)))
                        {
                            Log.d(MainActivity.class.getName(),"SENDING FOR POLL");
                            mObdClient.queueCommand(r.getKey());
                        }
                    }
                }
            }
            synchronized(this) {
                if (running)
                    new Handler().postDelayed(mQueueCommands, 1000);
            }
        }
    };

    private Thread mCommThread = new Thread(new Runnable() {
        public void run() {
            int packets = 0;
            // Connect to MQTT
            SharedPreferences prefs = app.getPrefs();
            String identity = prefs.getString("identity", "");
            broker.connectToBroker(identity);
            // configure and bind to the OBD service
            boolean bluetoothAvailable = false;
            try {
                Spinner spinner = (Spinner)app.findViewById(R.id.spinner);
                mObdClient.serviceSetSettings(macs.get(spinner.getSelectedItemPosition()),
                        "AUTO",
                        "192.168.0.10",
                        35000,
                        false,
                        mObdCallbacks);
                bluetoothAvailable = true;
            } catch (java.lang.IndexOutOfBoundsException e) {
                app.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        app.bluetoothWarning();
                    }
                });
            }

            // Start the OBD service
            if (!bluetoothAvailable) {
                return;
            }
            prefs = app.getPrefs();
            boolean useMock = prefs.getBoolean("use_mock", false);
            mObdClient.doBindObdService(useMock, (MainActivity)app);
            while (true) {
                if (!running) {
                    break;
                }

                if (!Connected) {
                    identity = prefs.getString("identity", "");
                    broker.connectToBroker(identity);
                    app.updateStatus(false,mObdClient,packets);
                } else {
                    if (broker.isConnected()) {
                        try {
                            String message = (String) MessageQueue.remove();
                            String[] messageElements = message.split(Pattern.quote("|"));
                            String pid = messageElements[0];
                            String ts = messageElements[1];
                            String result = messageElements[2];

                            broker.publish(pid,result,ts);
                            packets += 1;
                        } catch (NoSuchElementException e) {
                            //Log.d(MainActivity.class.getName(), "Nothing to send");
                        }
                        app.updateStatus(true,mObdClient,packets);
                    } else {
                        Log.d(MainActivity.class.getName(), "Broker not connected - reconnecting");
                        Connected = false;
                        app.updateStatus(false,mObdClient,packets);
                        packets = 0;
                    }
                }
                // Do not hog the CPU (at this rate we sent at most 500 readings per second)
                SystemClock.sleep(2);
            }
        }
    });



    private class ObdCallbacks implements ObdGatewayClientCallback {


        @Override
        public void onObdReceiveData(ObdCommandJob obdCmd) {
            SharedPreferences prefs = app.getPrefs();
            if ((obdCmd.getCommand().getResult() != null) && (!obdCmd.getCommand().getResult().equals("NO DATA")) && (mObdClient.getAvailablePids().contains(obdCmd.getCommand().getCommandPID()))) {

                Long currentTimestamp = System.currentTimeMillis() / 1000;
                // Reporting!
                if ((!obdCmd.getCommand().getResult().equals("NODATA"))&&(prefs.getInt(obdCmd.getCommand().getCommandPID().replace(" ","") + "_reporting",0) != 0) && (((currentTimestamp - startedTimestamp) % prefs.getInt(obdCmd.getCommand().getCommandPID().replace(" ","") + "_reporting",0)) == 0)) {
                    //First 4 characters are a copy of the command code, don't return those
                    String result = obdCmd.getCommand().getCalculatedResult();
                    app.displayToast("PID " + obdCmd.getCommand().getName() + "  value: " + obdCmd.getCommand().getResult() + " calculated: " + obdCmd.getCommand().getCalculatedResult());
                    Long tsLong = System.currentTimeMillis() / 1000;
                    String ts = tsLong.toString();
                    String message = obdCmd.getCommand().getCommandPID();
                    message = message + "|" + String.valueOf(ts);
                    message = message + "|" + String.valueOf(result);
                    MessageQueue.add(message);
                    Log.d(MainActivity.class.getName(), "PACKET RECEIVED, message: " + message);
                    // Polling!
                    if ((prefs.getInt(obdCmd.getCommand().getCommandPID().replace(" ","") + "_polling",0) != 0) && (((currentTimestamp - startedTimestamp) % prefs.getInt(obdCmd.getCommand().getCommandPID().replace(" ","") + "_polling",0)) == 0)) {
                        Log.d(MainActivity.class.getName(), "REPORT RECEIVED FOR " + obdCmd.getCommand().getName());
                    }
                }
                // Polling!
                else if ((!obdCmd.getCommand().getResult().equals("NODATA"))&&(prefs.getInt(obdCmd.getCommand().getCommandPID().replace(" ","") + "_polling",0) != 0) && (((currentTimestamp - startedTimestamp) % prefs.getInt(obdCmd.getCommand().getCommandPID().replace(" ","") + "_polling",0)) == 0)) {
                    Log.d(MainActivity.class.getName(),"REPORT RECEIVED FOR "+obdCmd.getCommand().getName());
                    sendObdData(obdCmd.getCommand());
                }
            }
        }

        @Override
        public void onObdReceiveStatus(int status) {
            // Transport error?
            if (status == ObdGatewayServiceIF.OBD_GATEWAY_STAT_TRANSPORT_CONNECT_ERROR) {
                SharedPreferences prefs = app.getPrefs();
                boolean useMock = prefs.getBoolean("use_mock", false);
                Log.d(MainActivity.class.getName(),"Rebinding due to connect error");
                mObdClient.doRebindObdService(useMock, (MainActivity)app);
            }
            else if (((status == ObdGatewayServiceIF.OBD_GATEWAY_STAT_TRANSPORT_ERROR))&&(mObdClient.isServiceRunning())) {
                SharedPreferences prefs = app.getPrefs();
                boolean useMock = prefs.getBoolean("use_mock", false);
                Log.d(MainActivity.class.getName(),"Rebinding due to transport error");
                mObdClient.doRebindObdService(useMock, (MainActivity)app);
            }

        }
    }


    public void setMqttConnected(boolean connected) {
        Connected = connected;
        Log.d(MainActivity.class.getName(),"CALLBACK CONNECTED "+String.valueOf(connected)+" "+String.valueOf(Connected));
    }
    public void setMqttFailure(String pid, String result, String timestamp) {
        String message = pid + "|" + String.valueOf(timestamp);
        message = message + "|" + String.valueOf(result);
        // Re-add to the queue the failed message
        MessageQueue.add(message);
        Connected = false;
        app.updateStatus(false,mObdClient,0);
    }


    public void onActivityRestart() {
        loadPairedConnections();
        try {
            Spinner spinner = (Spinner) app.findViewById(R.id.spinner);
            mObdClient.serviceSetSettings(macs.get(spinner.getSelectedItemPosition()),
                    "AUTO",
                    "192.168.0.10" ,
                    35000,
                    false,
                    mObdCallbacks);

            SharedPreferences prefs = app.getPrefs();
            boolean useMock = prefs.getBoolean("use_mock", false);
            Log.d(MainActivity.class.getName(),"Rebinding due to activity restart");
            mObdClient.doRebindObdService(useMock, (MainActivity)app);
            String identity = prefs.getString("identity", "");
            broker.connectToBroker(identity);
        } catch (Exception e){
            Log.e("ERROR",e.getMessage());
            // display warning?
        }
    }


    private void loadPairedConnections() {
        Set<BluetoothDevice> pairedDevices = myBluetoothAdapter.getBondedDevices();
        macs.clear();
        peers.clear();
        Log.d(MainActivity.class.getName(), "DEVICES LIST:");
        for (BluetoothDevice bt : pairedDevices) {
            macs.add(bt.getAddress());
            peers.add(bt.getName());
            Log.d(MainActivity.class.getName(), "DEVICE: " + bt.getAddress());
        }


        ArrayAdapter<CharSequence> adapter = new ArrayAdapter(app.getApplicationContext(), R.layout.support_simple_spinner_dropdown_item, peers);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner spinner = (Spinner) app.findViewById(R.id.spinner);
        spinner.setAdapter(adapter);

        // Load last used paired connection
        try {
            FileInputStream fis = openFileInput("paired_device.txt");
            byte[] value = new byte[fis.available()];
            fis.read(value);
            fis.close();
            String saved = new String(value);

            for (String str : peers) {
                if (str.equals(saved)) {
                    spinner.setSelection(peers.indexOf(str));
                }
            }
        } catch (Exception e) {
            Log.d(MainActivity.class.getName(),"str: "+e.getMessage());
        }

        final Activity mycontext = (MainActivity)app;
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (mObdClient != null) {
                    // Save the current one
                    FileOutputStream fos;
                    try {
                        fos = openFileOutput("paired_device.txt", Context.MODE_PRIVATE);
                        fos.write(peers.get(position).getBytes());
                        fos.close();
                        Log.d(MainActivity.class.getName(),"Paired device: " + peers.get(position));
                    } catch (FileNotFoundException e) {
                        app.displayToast("Could not save paired device preference");
                    }
                    catch (IOException e) {
                        app.displayToast("Could not save paired device preference (not enough space?)");
                    }

                    mObdClient.serviceSetSettings(macs.get(position),
                            "AUTO",
                            "192.168.0.10",
                            35000,
                            false,
                            mObdCallbacks);
                    SharedPreferences prefs = app.getPrefs();
                    boolean useMock = prefs.getBoolean("use_mock", false);
                    Log.d(MainActivity.class.getName(),"REBINDING DUE TO PAIRED CONNECTION CHANGE");
                    mObdClient.doRebindObdService(useMock, mycontext);
                }
                else {
                    Log.d(MainActivity.class.getName(),"CANNOT REBIND, CLIENT IS NULL");
                }
            }
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
    }



    public class LocalBinder extends Binder{
        public ObdScanService getServiceInstance(){
            return ObdScanService.this;
        }
    }


    public interface Callbacks{
        void updateStatus(final boolean connected, final ObdGatewayClient mObdClient, final int packets);
        void displayToast(String toast);
        void runOnUiThread(Runnable runnable);
        View findViewById(int id);
        SharedPreferences getPrefs();
        void bluetoothWarning();
        Context getApplicationContext();
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        synchronized(this) {
            running = false;
        }
        if (mObdClient != null) {
            mObdClient.doUnbindObdService((MainActivity)app);
        }
        super.onDestroy();
    }

    /*
    public class ObdScanServiceBinder extends Binder {
        public ObdScanService getService() {
            return ObdScanService.this;
        }
    }
    */


    @Override
    public void onCreate() {
        Log.d(TAG, "Creating service..");
        super.onCreate();
    }

    public void sendObdData(ObdCommand data) {
        Log.d(ServiceConnection.class.getName(),"RECEIVED DATA: " + data.getName());
    }


}
