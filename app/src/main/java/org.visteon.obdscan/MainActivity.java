package org.visteon.obdscan;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import org.vopen.android_sdk.obd_client.ObdGatewayClient;
import org.vopen.android_sdk.obd_service.ObdConfig;
import java.util.HashMap;
import java.util.Map;



import android.content.SharedPreferences;
import android.widget.Toast;

import static android.preference.PreferenceManager.*;
//import pt.lighthouselabs.obd.commands.ObdCommand;
import com.github.pires.obd.commands.ObdCommand;

public class MainActivity extends AppCompatActivity implements ObdScanService.Callbacks {

    private Intent serviceIntent;
    private ObdScanService mService = null;
    final BluetoothAdapter myBluetoothAdapter  = BluetoothAdapter.getDefaultAdapter();



    @Override
    public void updateStatus(final boolean connected, final ObdGatewayClient mObdClient, final int packets) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView tv = (TextView) findViewById(R.id.statusText);
                String status;
                //if (mObdClient.isServiceBound()) {
                if (mObdClient.isServiceBound() && mObdClient.isServiceRunning()) {
                    if (!connected) {
                        status = "OBD2 connected | Not connected to cloud";
                    }
                    else {
                        status = "OBD2 connected | " + Integer.toString(packets) + " packets sent";
                    }
                }
                else {
                    if (!connected) {
                        status = "OBD2 not connected | Not connected to cloud";
                    }
                    else {
                        status = "OBD2 not connected | " + Integer.toString(packets) + " packets sent";
                    }
                }
                tv.setText(status);
            }
        });
    }


    @Override
    public void onRestart(){
        super.onRestart();
        if (mService != null)
            mService.onActivityRestart();
    }


    @Override
    public void onBackPressed() {
        //Execute your code here
        if (!getPrefs().getBoolean("leave_service", true)) {
            serviceIntent = new Intent(MainActivity.this, ObdScanService.class);
            stopService(serviceIntent); //Starting the service
            finish();
        }
        else {
            moveTaskToBack(true);
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        // Start and bind the service
        serviceIntent = new Intent(MainActivity.this, ObdScanService.class);
        startService(serviceIntent); //Starting the service
        bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE);



        // Set defaults if they do not exist in sharedprefs
        SharedPreferences prefs = getDefaultSharedPreferences(MainActivity.this);
        SharedPreferences.Editor ed = prefs.edit();
        for (Map.Entry<ObdCommand,Map.Entry<Integer,Integer>> r : ObdConfig.getCommands().entrySet()) {
            String name_report;
            String name_poll;
            try {
                name_report = r.getKey().getCommandPID().replace(" ", "") + "_reporting";
                name_poll = r.getKey().getCommandPID().replace(" ", "") + "_polling";
            } catch (java.lang.StringIndexOutOfBoundsException e){
                continue;
            }
            if (!prefs.contains(name_report)) {
                ed.putInt(name_report,r.getValue().getKey());
            }
            if (!prefs.contains(name_poll)) {
                ed.putInt(name_report,r.getValue().getValue());
            }
        }
        ed.commit();


        // Enable bluetooth first
        if(!myBluetoothAdapter.isEnabled()){
            myBluetoothAdapter.enable();
        }


        // UI stuff
        setContentView(R.layout.activity_main);
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this,SettingsActivity.class);
                startActivity(intent);
            }
        });

        FloatingActionButton fab2 = (FloatingActionButton) findViewById(R.id.fab2);
        fab2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this,SupportedPidsActivity.class);
                HashMap<String,String> commands = new HashMap<String,String>();
                for (Map.Entry<ObdCommand, Map.Entry<Integer,Integer>> r: mService.mObdClient.getAvailableCommands().entrySet()) {
                    commands.put(r.getKey().getName(),r.getKey().getCommandPID());
                }

                intent.putExtra("supportedPids", commands);
                startActivity(intent);
            }
        });
    }

    public void displayToast(final String toast) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this,toast,Toast.LENGTH_SHORT).show();
            }
        });
    }

    public SharedPreferences getPrefs() {
        return getDefaultSharedPreferences(MainActivity.this);
    }

    public void bluetoothWarning() {
        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setTitle("Error");
        alertDialog.setMessage("Please check if bluetooth is enabled and you have at least one paired OBD2 device");
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        MainActivity.this.finish();
                    }
                });
        alertDialog.show();
    }


    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            ObdScanService.LocalBinder binder = (ObdScanService.LocalBinder) service;
            mService = binder.getServiceInstance();
            mService.registerClient(MainActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            // should we?
        }
    };



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}