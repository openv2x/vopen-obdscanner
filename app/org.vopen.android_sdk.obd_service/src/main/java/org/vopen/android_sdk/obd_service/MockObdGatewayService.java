package org.vopen.android_sdk.obd_service;

import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

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
public class MockObdGatewayService extends AbstractGatewayService {

  private static final String TAG = MockObdGatewayService.class.getName();

  ObdGatewayServiceSettings settings = new ObdGatewayServiceSettings() ;

  public void startService() {
    Log.d(TAG, "Starting "+this.getClass().getName()+" service..");

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

    // For now set protocol to AUTO
    queueJob(new ObdCommandJob(new SelectProtocolCommand(ObdProtocols.AUTO)));

    // Job for returning dummy data
    queueJob(new ObdCommandJob(new AmbientAirTemperatureCommand()));

    queueCounter = 0L;
    Log.d(TAG, "Initialization jobs queued.");

    sendStatus(ObdGatewayServiceIF.OBD_GATEWAY_STAT_SERVICE_RUNNING);
    isRunning = true;
  }


    /**
    * Runs the queue until the service is stopped
    */
    protected void executeQueue() {
      Log.d(TAG, "Executing queue..");
      while (!Thread.currentThread().isInterrupted()) {
          ObdCommandJob job = null;
          try {
            job = jobsQueue.take();

            Log.d(TAG, "Taking job[" + job.getId() + "] from queue..");

            if (job.getState().equals(ObdCommandJob.ObdCommandJobState.NEW)) {
                Log.d(TAG, "Job state is NEW. Run it..");
                job.setState(ObdCommandJob.ObdCommandJobState.RUNNING);
                if (job.getCommand()!= null) {
                    Log.d(TAG, job.getCommand().getName());
                    job.getCommand().run(new ByteArrayInputStream("41 00 00 00>41 00 00 00>41 00 00 00>".getBytes()), new ByteArrayOutputStream());
                }
                else
                    // Everything is supported
                    //job.getMultiCommand().sendCommands(new ByteArrayInputStream("FF FF FF FF FF FF>FF FF FF FF FF FF>FF FF FF FF FF FF>".getBytes()), new ByteArrayOutputStream());
                    // Use VW Passat data as mock data, but with fuel level supported
                    job.getMultiCommand().sendCommands(new ByteArrayInputStream("FF FF 98 3B A0 13>FF FF A0 03 B8 01>FF FF CC D2 00 01>".getBytes()), new ByteArrayOutputStream());
            } else {
              Log.e(TAG, "Job state was not new, so it shouldn't be in queue. BUG ALERT!");
            }
          } catch (InterruptedException i) {
              Thread.currentThread().interrupt();
          } catch (Exception e) {
            e.printStackTrace();
            job.setState(ObdCommandJob.ObdCommandJobState.EXECUTION_ERROR);
            Log.e(TAG, "Failed to run command. -> " + e.getMessage());
          }

          if (job != null) {
            Log.d(TAG, "Job is finished.");
            job.setState(ObdCommandJob.ObdCommandJobState.FINISHED);
            final ObdCommandJob job2=job;
            if (rMessenger != null)
            {
                Message msg = new Message();  // = Message.obtain(null, 5, 0,0);
                if (job.getCommand()!=null)
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
    sendStatus(ObdGatewayServiceIF.OBD_GATEWAY_STAT_SERVICE_STOPPED);
    isRunning = false;

    // kill service
    jobsQueue.removeAll(jobsQueue);
    stopSelf();
  }

  Messenger rMessenger = null;

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
  class IncomingHandler extends Handler {
    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
        case ObdGatewayServiceIF.OBD_GATEWAY_MSG_STARTSERVICE:
          if (rMessenger == null)
          {
                rMessenger = msg.replyTo;
          }

            Thread serviceThread = new Thread(new Runnable() {
                public void run() {
                    sendStatus(ObdGatewayServiceIF.OBD_GATEWAY_STAT_SERVICE_STOPPED);
                    startService();
                }
            });
            serviceThread.start();

          break;
        case ObdGatewayServiceIF.OBD_GATEWAY_MSG_STOPSERVICE:
          stopService();
          break;
        case ObdGatewayServiceIF.OBD_GATEWAY_MSG_DATA:
          queueJob(new ObdCommandJob((ObdCommand) msg.obj));
          break;
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

      //msg.replyTo
    }
  }

  /**
   * Target we publish for clients to send messages to IncomingHandler.
   */
  final Messenger mMessenger = new Messenger(new IncomingHandler());

  @Override
  public IBinder onBind(Intent intent) {
    return mMessenger.getBinder();//binder
  }



}
