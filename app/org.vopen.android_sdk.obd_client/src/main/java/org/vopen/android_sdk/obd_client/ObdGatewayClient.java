package org.vopen.android_sdk.obd_client;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import com.github.pires.obd.commands.ObdCommand;
import com.github.pires.obd.commands.ObdMultiCommand;
import com.github.pires.obd.commands.protocol.AvailablePidsCommand_01_20;
import com.github.pires.obd.commands.protocol.AvailablePidsCommand_21_40;
import com.github.pires.obd.commands.protocol.AvailablePidsCommand_41_60;


import org.vopen.android_sdk.obd_service.ObdGatewayServiceSettings;
import org.vopen.android_sdk.obd_service.ObdGatewayServiceIF;
import org.vopen.android_sdk.obd_service.ObdCommandJob;
import org.vopen.android_sdk.obd_service.MockObdGatewayService;
import org.vopen.android_sdk.obd_service.ObdGatewayService;

// Edit (mrangelo) - use obd_service's ObdConfig's list of supported commands
import org.vopen.android_sdk.obd_service.ObdConfig;
import com.github.pires.obd.utils.CommandAvailabilityHelper;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by DBF on 5/31/2015.
 */
public class ObdGatewayClient {

    // Edit (mrangelo) - maintain a list of supported commands
    private Map<ObdCommand, Map.Entry<Integer,Integer>> availablePids = new HashMap<ObdCommand,  Map.Entry<Integer,Integer>>();

    private static final String TAG = ObdGatewayClient.class.getName();
    private volatile boolean isServiceBound = false;
    private volatile boolean isSrvRunning = false;
    private Messenger mService = null;
    private ObdGatewayClientCallback mCallbacks = null;

    private final IncomingHandler rHandler = new IncomingHandler();
    private final Messenger rMessenger = new Messenger(rHandler);
    private ObdGatewayServiceSettings settings = new ObdGatewayServiceSettings();

    class IncomingHandler extends Handler {
        private Context savedContext = null;

        public void setContext(Context context)
        {
            savedContext = context;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case ObdGatewayServiceIF.OBD_GATEWAY_MSG_DATA:
                    if (mCallbacks != null)
                    {
                        mCallbacks.onObdReceiveData((ObdCommandJob) msg.obj);
                    }
                    break;
                // Edit (mrangelo) - fetch supported PIDs list from multicommand result
                // It might return empty result in case there is a BT-related issue or BT not connected
                // In this case, service discovery will be restarted
                case ObdGatewayServiceIF.OBD_GATEWAY_MSG_SUPPORTED_RESULT:
                    availablePids.clear();
                    ObdCommandJob job = (ObdCommandJob) msg.obj;
                    ObdMultiCommand command = job.getMultiCommand();
                    synchronized(this) {
                        if ((!command.getFormattedResult().equals(",,,")) && (isServiceBound()) && (isServiceRunning())) {
                            String result = command.getFormattedResult().replace(",", "");
                            for (Map.Entry<ObdCommand, Map.Entry<Integer, Integer>> r : ObdConfig.getCommands().entrySet()) {
                                try {
                                    String comm = r.getKey().getCommandPID();
                                    if (CommandAvailabilityHelper.isAvailable(comm, result)) {
                                        Log.d(TAG, "Supported: " + r.getKey().getName());
                                        availablePids.put(r.getKey(), new AbstractMap.SimpleEntry(r.getValue().getKey(), r.getValue().getValue()));
                                    } else
                                        Log.d(TAG, "Unsupported: " + r.getKey().getName());
                                } catch (Exception e) {
                                    Log.d(TAG, "Unsupported: " + r.getKey().getName());
                                }
                            }
                        } else if ((isServiceBound()) && (isServiceRunning())) {
                            //Log.d(TAG, "OBDII device not ready, requesting supported services again");
                            requestSupportedPids();
                        } else {
                            new Timer().schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    requestSupportedPids();
                                }
                            }, 5000);
                        }
                    }
                    break;
                case ObdGatewayServiceIF.OBD_GATEWAY_MSG_STATUS:
                    if(msg.arg1 == ObdGatewayServiceIF.OBD_GATEWAY_STAT_SERVICE_RUNNING)
                    {
                        synchronized (this){
                            isSrvRunning = true;
                        }
                    }
                    else if (msg.arg1 == ObdGatewayServiceIF.OBD_GATEWAY_STAT_SERVICE_STOPPED)
                    {
                        synchronized (this){
                            isSrvRunning = false;
                        }
                    }
                    //In case of BT Error unbind theservice
                    else if (msg.arg1 == ObdGatewayServiceIF.OBD_GATEWAY_STAT_TRANSPORT_CONNECT_ERROR) {
                        Log.d(TAG, "Service transport error while connecting");
                        if (mCallbacks != null) {
                            mCallbacks.onObdReceiveStatus(msg.arg1);

                        }
                        break;
                    }
                    else if (msg.arg1 == ObdGatewayServiceIF.OBD_GATEWAY_STAT_TRANSPORT_ERROR) {
                        //TBD decide what to do in case of BT error -> can be handled in wrapping class
                        //doUnbindObdService(savedContext);
                        //DisconnectService();
                        Log.d(TAG, "Service transport error");
                        if (mCallbacks != null) {
                            mCallbacks.onObdReceiveStatus(msg.arg1);

                        }
                        break;
                    }
                    else
                    {
                        if (mCallbacks != null)
                        {
                            mCallbacks.onObdReceiveStatus(msg.arg1);
                        }
                    }

                default:
                    super.handleMessage(msg);
            }
        }
    }

    public void serviceSetSettings (String remoteDev, String protList, String wIp, int wPort, boolean useWiFi, ObdGatewayClientCallback callbacks )
    {
        settings.remoteDevice = remoteDev;
        settings.protocolList =  protList;
        settings.wifiIp = wIp;
        settings.wifiPort = wPort;
        settings.connWiFi = useWiFi;
        mCallbacks = callbacks;
    }

    public void requestSupportedPids()
    {
        ObdMultiCommand command = new ObdMultiCommand();
        command.add(new AvailablePidsCommand_01_20());
        command.add(new AvailablePidsCommand_21_40());
        command.add(new AvailablePidsCommand_41_60());
        queueMultiCommand(command);
    }

    public Map<ObdCommand, Map.Entry<Integer,Integer>> getAvailableCommands() {
        return availablePids;
    }

    public ArrayList<String> getAvailablePids() {
        ArrayList<String> pids = new ArrayList<String>();
        for (ObdCommand r : availablePids.keySet()) {
            pids.add(r.getCommandPID());
        }
        return pids;
    }


    // TODO - add method to properly release the service connection

    private ServiceConnection serviceConn;
    private void ConnectService() {
        serviceConn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName className, IBinder binder) {
                Log.d(TAG, className.toString() + " service is bound");
                isServiceBound = true;
                Log.d(TAG, "Starting live data");

                //Bind service
                mService = new Messenger(binder);
                synchronized(this) {
                    if (isServiceBound) {
                        //Configure Service
                        Message msg = Message.obtain(null, ObdGatewayServiceIF.OBD_GATEWAY_MSG_SETSETTINGS, 0, 0);
                        msg.obj = (Object) settings;
                        try {
                            mService.send(msg);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }

                        //Start Service
                        Message msg2 = Message.obtain(null, ObdGatewayServiceIF.OBD_GATEWAY_MSG_STARTSERVICE, 0, 0);
                        msg2.replyTo = rMessenger;
                        try {
                            mService.send(msg2);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }

                        // Obtain supported PIDs
                        requestSupportedPids();
                    }
                }
            }

            @Override
            protected Object clone() throws CloneNotSupportedException {
                return super.clone();
            }

            // This method is *only* called when the connection to the service is lost unexpectedly
            // and *not* when the client unbinds (http://developer.android.com/guide/components/bound-services.html)
            // So the isServiceBound attribute should also be set to false when we unbind from the service.
            @Override
            public void onServiceDisconnected(ComponentName className) {
                Log.d(TAG, className.toString()  + " service is unbound");
                //SR clean up messenger
                mService = null;
                synchronized(this) {
                    isServiceBound = false;
                }
            }
        };
    }

    public void DisconnectService() {
        mService = null;
        synchronized(this) {
            isServiceBound = false;
            isSrvRunning = false;
        }
        Log.d(TAG,"Disconnecting service...");
    }

    public void doBindObdService(boolean useMockService, Context context) {
        ConnectService();
        ContextWrapper cw = new ContextWrapper(context);
        synchronized (this){
            if (!isServiceBound) {
                Log.d(TAG, "Binding OBD service..");
                rHandler.setContext(context);
                if (useMockService == false) {
                    Intent serviceIntent = new Intent(cw, ObdGatewayService.class);
                    cw.bindService(serviceIntent, serviceConn, Context.BIND_AUTO_CREATE);
                } else {
                    Intent serviceIntent = new Intent(cw, MockObdGatewayService.class);
                    cw.bindService(serviceIntent, serviceConn, Context.BIND_AUTO_CREATE);
                }
            }
        }
        availablePids.clear();
    }

    public void doUnbindObdService(Context context) {
        ContextWrapper cw = new ContextWrapper(context);
        synchronized (this){
            if (isServiceBound) {
                //Start Service
                Message msg = Message.obtain(null, ObdGatewayServiceIF.OBD_GATEWAY_MSG_STOPSERVICE, 0, 0);
                try {
                    mService.send(msg);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }

                Log.d(TAG, "Unbinding OBD service..");
                try {
                    cw.unbindService(serviceConn);
                } catch (java.lang.IllegalArgumentException e) {
                    Log.d(TAG, "Service already unbound!");
                }

                isServiceBound = false;
            }
        }
        availablePids.clear();
        DisconnectService();
    }

    // Edit (mrangelo) - added a rebind function that handles issues with binding non-mock after unbinding mock service and vice-versa
    // TODO: synchronization is missing and code is a real mess, refactor it!
    public void doRebindObdService(final boolean useMockService, final Context context) {
         Thread rebindThread = new Thread(new Runnable() {
            public void run() {
                synchronized(this) {
                    Log.d(TAG, "Rebinding OBD service..");
                    ContextWrapper cw = new ContextWrapper(context);
                    if (isServiceBound) {
                        availablePids.clear();
                        while (isServiceRunning()) {
                            Message msg = Message.obtain(null, ObdGatewayServiceIF.OBD_GATEWAY_MSG_STOPSERVICE, 0, 0);
                            try {
                                mService.send(msg);
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                            Log.d(TAG, "Service not closed yet...");
                            SystemClock.sleep(100);
                        }
                        Log.d(TAG, "Rebind: unbinding OBD service..");
                        try {
                            cw.unbindService(serviceConn);
                        } catch (Exception e) {
                            Log.d(TAG, "Could not unbind the service!");
                        }
                        isServiceBound = false;
                    }
                    DisconnectService();

                    if (!isServiceBound) {
                        Log.d(TAG, "Rebind: binding OBD service..");
                        ConnectService();
                        rHandler.setContext(context);
                        if (useMockService == false) {
                            Intent serviceIntent = new Intent(cw, ObdGatewayService.class);
                            cw.stopService(serviceIntent);
                            cw.startService(serviceIntent);
                            cw.bindService(serviceIntent, serviceConn, Context.BIND_AUTO_CREATE);
                        } else {
                            Intent serviceIntent = new Intent(cw, MockObdGatewayService.class);
                            cw.stopService(serviceIntent);
                            cw.startService(serviceIntent);
                            cw.bindService(serviceIntent, serviceConn, Context.BIND_AUTO_CREATE);
                        }
                    }
                    //while (!isServiceRunning()) {
                    //    SystemClock.sleep(100);
                    //}
                    isServiceBound = true;
                }
            }
        });
        rebindThread.start();
    }



    public boolean isServiceRunning ()
    {
        return isSrvRunning;
    }

    public boolean isServiceBound ()
    {
        return isServiceBound;
    }

    public void queueCommand(ObdCommand command) {
        synchronized (this){
            if (isServiceBound) {
                //Get OBD Data
                Message msg = new Message();
                msg.what = ObdGatewayServiceIF.OBD_GATEWAY_MSG_DATA;
                msg.obj = (Object) command;
                try {
                    mService.send(msg);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }

            }
        }
    }
    // Edit (mrangelo) - queueing multicommands is currently only used for supported PID discovery
    public void queueMultiCommand(ObdMultiCommand command) {
        synchronized (this){
            if (isServiceBound) {
                //Get OBD Data
                Message msg = new Message();
                msg.what = ObdGatewayServiceIF.OBD_GATEWAY_MSG_SUPPORTED_QUERY;
                msg.obj = (Object) command;
                try {
                    mService.send(msg);
                } catch (RemoteException e) {
                    e.printStackTrace();
                } catch (java.lang.NullPointerException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
