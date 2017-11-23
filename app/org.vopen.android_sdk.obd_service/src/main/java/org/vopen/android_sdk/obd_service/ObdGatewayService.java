package org.vopen.android_sdk.obd_service;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import com.github.pires.obd.commands.protocol.AvailablePidsCommand_01_20;
import com.google.inject.Inject;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;

import com.github.pires.obd.commands.ObdCommand;
import com.github.pires.obd.commands.ObdMultiCommand;
import com.github.pires.obd.commands.protocol.EchoOffCommand;
import com.github.pires.obd.commands.protocol.LineFeedOffCommand;
import com.github.pires.obd.commands.protocol.ObdResetCommand;
import com.github.pires.obd.commands.protocol.SelectProtocolCommand;
import com.github.pires.obd.commands.protocol.TimeoutCommand;
import com.github.pires.obd.commands.temperature.AmbientAirTemperatureCommand;
import com.github.pires.obd.enums.ObdProtocols;

/**
 * This service is primarily responsible for establishing and maintaining a
 * permanent connection between the device where the application runs and a more
 * OBD Bluetooth interface.
 * <p/>
 * Secondarily, it will serve as a repository of ObdCommandJobs and at the same
 * time the application state-machine.
 */
public class ObdGatewayService extends AbstractGatewayService {

  private static final String TAG = ObdGatewayService.class.getName();
  /*
   * http://developer.android.com/reference/android/bluetooth/BluetoothDevice.html
   * #createRfcommSocketToServiceRecord(java.util.UUID)
   *
   * "Hint: If you are connecting to a Bluetooth serial board then try using the
   * well-known SPP UUID 00001101-0000-1000-8000-00805F9B34FB. However if you
   * are connecting to an Android peer then please generate your own unique
   * UUID."
   */
  private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
  //private final IBinder binder = new ObdGatewayServiceBinder();
  @Inject
  //SharedPreferences prefs;

  ObdGatewayServiceSettings settings = new ObdGatewayServiceSettings() ;

  private BluetoothDevice dev = null;
  private BluetoothSocket sock = null;
  private BluetoothSocket sockFallback = null;
  private Socket wSocket = null;


  private class SocketTask extends AsyncTask<Integer,Integer,Boolean> {
    protected Boolean doInBackground(Integer... data)
    {
      Boolean res = false;
      try
      {
        if (wSocket == null)
        {
          wSocket = new Socket(settings.wifiIp,settings.wifiPort);
          res = true;
        }
      } catch (Exception ew)
      {
        ew.printStackTrace();
        Log.e(TAG, "Couldn't establishing WiFi connection. Stopping app..", ew);
        //throw new IOException(); check if trow is needed
      }
      return res;
    }

    protected void onProgressUpdate(Integer... progress)
    {

    }

    protected void onPostExecute(Boolean result)
    {
      //check the result from doInBackground function
      if (result == true)
      {
        sendStatus(ObdGatewayServiceIF.OBD_GATEWAY_STAT_TRANSPORT_CONNECTED);
        initObdSequence();
      }
      else
      {
        sendStatus(ObdGatewayServiceIF.OBD_GATEWAY_STAT_TRANSPORT_ERROR);
        stopService();
      }
    }
  }



  public void startService() throws IOException {
    Log.d(TAG, "Starting service..");

    jobsQueue = new LinkedBlockingQueue<ObdCommandJob>();
    jobsQueue.removeAll(jobsQueue);

    sendStatus(ObdGatewayServiceIF.OBD_GATEWAY_STAT_SERVICE_STOPPED);
    sendStatus(ObdGatewayServiceIF.OBD_GATEWAY_STAT_TRANSPORT_OK);
    sendStatus(ObdGatewayServiceIF.OBD_GATEWAY_STAT_TRANSPORT_CONNECTING);
    if (settings.connWiFi != true)
    {
      if (settings.remoteDevice == null || "".equals(settings.remoteDevice))
      {
        Toast.makeText(ctx, "No Bluetooth device selected", Toast.LENGTH_LONG).show();

        // log error
        Log.e(TAG, "No Bluetooth device has been selected.");
        sendStatus(ObdGatewayServiceIF.OBD_GATEWAY_STAT_TRANSPORT_CONNECT_ERROR);
        // TODO kill this service gracefully
        stopService();
        throw new IOException();
      }
      else
      {

        final BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        dev = btAdapter.getRemoteDevice(settings.remoteDevice);


          /*
           * Establish Bluetooth connection
           *
           * Because discovery is a heavyweight procedure for the Bluetooth adapter,
            * this method should always be called before attempting to connect to a
            * remote device with connect(). Discovery is not managed by the Activity,
            * but is run as a system service, so an application should always call
            * cancel discovery even if it did not directly request a discovery, just to
            * be sure. If Bluetooth state is not STATE_ON, this API will return false.
          *
          * see
          * http://developer.android.com/reference/android/bluetooth/BluetoothAdapter
          * .html#cancelDiscovery()
          */
        Log.d(TAG, "Stopping Bluetooth discovery.");
        if (btAdapter.isDiscovering())
            btAdapter.cancelDiscovery();
      }
    }
    //showNotification("Tap to open OBD-Reader", "Starting OBD connection..", R.drawable.ic_launcher, true, true, false);

    try
    {
      startObdConnection();
    } catch (Exception e)
    {
      Log.e(TAG, "There was an error while establishing connection. -> " + e.getMessage());
      // in case of failure, stop this service.
      throw new IOException();
    }


     /*
     * TODO clean
     *
     * Get more preferences
     */
         // boolean imperialUnits = prefs.getBoolean(ConfigActivity.IMPERIAL_UNITS_KEY,
         //         false);
         // ArrayList<ObdCommand> cmds = ConfigActivity.getObdCommands(prefs);

  }

  /**
   * Start and configure the connection to the OBD interface.
   * <p/>
   * See http://stackoverflow.com/questions/18657427/ioexception-read-failed-socket-might-closed-bluetooth-on-android-4-3/18786701#18786701
   *
   * @throws IOException
   */
  private void startObdConnection() throws IOException {
    Log.d(TAG, "Starting OBD connection..");


    if(settings.connWiFi == true)
    {
      new SocketTask().execute(1);
    }
    else
    {
      try {
        // Instantiate a BluetoothSocket for the remote device and connect it.
        sock = dev.createRfcommSocketToServiceRecord(MY_UUID);
        sock.connect();
      } catch (Exception e1) {
        //sendStatus(ObdGatewayServiceIF.OBD_GATEWAY_STAT_TRANSPORT_ERROR);
        Log.e(TAG, "There was an error while establishing Bluetooth connection. Falling back..", e1);
        Class<?> clazz = sock.getRemoteDevice().getClass();
        Class<?>[] paramTypes = new Class<?>[]{Integer.TYPE};
        try {
          Method m = clazz.getMethod("createRfcommSocket", paramTypes);
          Object[] params = new Object[]{Integer.valueOf(1)};
          sockFallback = (BluetoothSocket) m.invoke(sock.getRemoteDevice(), params);
          sockFallback.connect();
          sock = sockFallback;
          sendStatus(ObdGatewayServiceIF.OBD_GATEWAY_STAT_TRANSPORT_CONNECTED);
        } catch (Exception e2) {
          //sendStatus(ObdGatewayServiceIF.OBD_GATEWAY_STAT_TRANSPORT_ERROR);
          Log.e(TAG, "Couldn't fallback while establishing Bluetooth connection. Stopping app..", e2);

          throw new IOException();
          //stopService();
        }
      }
      Log.d(TAG,"connected");
      isRunning = true;
      sendStatus(ObdGatewayServiceIF.OBD_GATEWAY_STAT_SERVICE_RUNNING);
      initObdSequence();
    }
  }

  private void initObdSequence()
  {
    // Let's configure the connection.
    Log.d(TAG, "Queing jobs for connection configuration..");
    queueJob(new ObdCommandJob(new ObdResetCommand()));
    queueJob(new ObdCommandJob(new EchoOffCommand()));

    /*
     * Will send second-time based on tests.
     *
     * TODO this can be done w/o having to queue jobs by just issuing
     * command.run(), command.getResult() and validate the result.
     */
    queueJob(new ObdCommandJob(new EchoOffCommand()));
    queueJob(new ObdCommandJob(new LineFeedOffCommand()));
    queueJob(new ObdCommandJob(new TimeoutCommand(62)));

    // Get protocol from settings
    String protocol = settings.protocolList; //prefs.getString(ConfigActivity.PROTOCOLS_LIST_KEY,"AUTO");
    queueJob(new ObdCommandJob(new SelectProtocolCommand(ObdProtocols.valueOf(protocol))));

    // Job for returning dummy data
    queueJob(new ObdCommandJob(new AmbientAirTemperatureCommand()));

    queueCounter = 0L;
    Log.d(TAG, "Initialization jobs queued.");
  }

  /**
   * Runs the queue until the service is stopped
   */
  protected void executeQueue() throws InterruptedException{
    Log.d(TAG, "Executing queue..");
    while (!Thread.currentThread().isInterrupted()) {
      ObdCommandJob job = null;
      try {
        job = jobsQueue.take();

        // log job
        Log.d(TAG, "Taking job[" + job.getId() + "] from queue..");

        if (job.getState().equals(ObdCommandJob.ObdCommandJobState.NEW)) {
          //Log.d(TAG, "Job state is NEW. Run it..");
          job.setState(ObdCommandJob.ObdCommandJobState.RUNNING);

          //If WiFi connection is configured
          // Edit (mrangelo) - assume MultiCommand is only used for "Get available PIDs" command
          if(settings.connWiFi == true && wSocket != null)
          {
              if (job.getCommand() != null)
                  job.getCommand().run(wSocket.getInputStream(), wSocket.getOutputStream());
              else
                  job.getMultiCommand().sendCommands(wSocket.getInputStream(), wSocket.getOutputStream());
          }
          else
          {
              if (job.getCommand() != null)
                  job.getCommand().run(sock.getInputStream(), sock.getOutputStream());
              else
                  job.getMultiCommand().sendCommands(sock.getInputStream(), sock.getOutputStream());
          }
        } else
          // log not new job
          Log.e(TAG,
              "Job state was not new, so it shouldn't be in queue. BUG ALERT!");
            } catch (InterruptedException i) {
                Thread.currentThread().interrupt();
      } catch (Exception e) {
        job.setState(ObdCommandJob.ObdCommandJobState.EXECUTION_ERROR);
        Log.e(TAG, "Failed to run command. -> " + e.getMessage());
          jobsQueue.removeAll(jobsQueue);
          Message msg = new Message();
          msg.what = ObdGatewayServiceIF.OBD_GATEWAY_STAT_TRANSPORT_ERROR;
          if (rMessenger!=null) {
              try {
                  rMessenger.send(msg);
              } catch (RemoteException e2) {
                  e2.printStackTrace();
              }
          }
      }
      // Return Data to clients
      if (job != null) {
        final ObdCommandJob job2=job;
        if (rMessenger != null)
        {
            Message msg = new Message();
            if (job.getCommand() != null)
                msg.what = ObdGatewayServiceIF.OBD_GATEWAY_MSG_DATA;
            else
                msg.what = ObdGatewayServiceIF.OBD_GATEWAY_MSG_SUPPORTED_RESULT;
            msg.obj = (Object)job2;
            try {
                rMessenger.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
      }
    }
  }





  /**
  * Stop OBD connection and queue processing.
  */
  public void stopService() {
    Log.d(TAG, "Stopping service..");

    notificationManager.cancel(NOTIFICATION_ID);
    jobsQueue.removeAll(jobsQueue); // TODO is this safe?
    jobsQueue = new LinkedBlockingQueue<ObdCommandJob>();
    sendStatus(ObdGatewayServiceIF.OBD_GATEWAY_STAT_SERVICE_STOPPED);
    isRunning = false;

    //Close WiFi Socket if exists
    if (wSocket != null)
      // close wSocket
      try {
        wSocket.close();
      } catch (IOException e) {
        Log.e(TAG, e.getMessage());
      }

    //Close BT Socket if created
    if (sock != null)
      // close socket
      try {
        sock.close();
      } catch (IOException e) {
        Log.e(TAG, e.getMessage());
      }

    // kill service
    stopSelf();
  }

  private void sendStatus (int status) {
    if (rMessenger != null) {
      Message msg = Message.obtain(null, ObdGatewayServiceIF.OBD_GATEWAY_MSG_STATUS, status,0);
      try {
        rMessenger.send(msg);
      } catch (RemoteException e) {
        e.printStackTrace();
      }
    }
  }

  Messenger rMessenger = null;

  class IncomingHandler extends Handler {
    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
        case ObdGatewayServiceIF.OBD_GATEWAY_MSG_STARTSERVICE:
          if (rMessenger == null)
          {
            rMessenger = msg.replyTo;
          }

          // Edit (mrangelo) - start the service in another thread to avoid clogging the UI one when no BT connection available
          Thread serviceThread = new Thread(new Runnable() {
            public void run() {
              try {
                  startService();
              }
              catch ( IOException ioe) {
                Log.e(TAG, "Failure Starting live data");
                sendStatus(ObdGatewayServiceIF.OBD_GATEWAY_STAT_TRANSPORT_CONNECT_ERROR);
                Log.e(TAG,"SENDING TRANSPORT ERROR!");
                stopService();
              }
            }
          });
          serviceThread.start();
          break;
        case ObdGatewayServiceIF.OBD_GATEWAY_MSG_STOPSERVICE:
          stopService();
          break;
        case ObdGatewayServiceIF.OBD_GATEWAY_MSG_DATA:
          queueJob(new ObdCommandJob((ObdCommand)msg.obj));
          break;
        // Edit (mrangelo) - handle supported services case
        case ObdGatewayServiceIF.OBD_GATEWAY_MSG_SUPPORTED_QUERY:
            queueJob(new ObdCommandJob((ObdMultiCommand)msg.obj));
            break;
        case ObdGatewayServiceIF.OBD_GATEWAY_MSG_SETSETTINGS:
          //TBD Set settings only if service is not running
          //Copy settings to service
          settings = (ObdGatewayServiceSettings)msg.obj;
          break;
        default:
          super.handleMessage(msg);
      }

    }
  }

  final Messenger mMessenger = new Messenger(new IncomingHandler());

  @Override
  public IBinder onBind(Intent intent) {
    return mMessenger.getBinder();//binder
  }

}