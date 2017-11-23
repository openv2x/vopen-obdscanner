package org.visteon.obdscan;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;

import com.github.pires.obd.commands.ObdCommand;

import org.vopen.android_sdk.obd_service.ObdConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SupportedPidsActivity extends AppCompatActivity {

    private HashMap<String,String> supported;
    PidsAdapter dataAdapter = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supported = (HashMap<String,String>)getIntent().getSerializableExtra("supportedPids");
        setContentView(R.layout.activity_pids);
        displayListView();
        checkButtonClick();
    }


    private void checkButtonClick() {
        Button myButton = (Button) findViewById(R.id.findSelected);
        myButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                SupportedPidsActivity.this.finish();
            }
        });

    }

    private void displayListView() {
        boolean available = false;

        ArrayList<SupportedPid> pidList = new ArrayList<SupportedPid>();
        for (Map.Entry<ObdCommand, Map.Entry<Integer,Integer>> pid: ObdConfig.getCommands().entrySet()) {
            available = false;
            for (Map.Entry<String,String> pid2: supported.entrySet()) {
                if (pid.getKey().getName().equals(pid2.getKey())) {
                    SupportedPid supportedpid = new SupportedPid(pid.getKey().getCommandPID(), pid.getKey().getName(), true);
                    pidList.add(supportedpid);
                    available = true;
                }
            }
            if (!available) {
                try {
                    SupportedPid supportedpid = new SupportedPid(pid.getKey().getCommandPID(), pid.getKey().getName(), false);
                    pidList.add(supportedpid);
                } catch (Exception e){
                    //nothing
                }
            }
        }

        dataAdapter = new PidsAdapter(this,
                R.layout.content_pids, pidList);
        ListView listview = (ListView) findViewById(R.id.listview1);
        // Assign adapter to ListView
        listview.setAdapter(dataAdapter);
    }

    private class PidsAdapter extends ArrayAdapter<SupportedPid> {

        private ArrayList<SupportedPid> pidList;

        public PidsAdapter(Context context, int textViewResourceId,
                           ArrayList<SupportedPid> pidList) {
            super(context, textViewResourceId, pidList);
            this.pidList = new ArrayList<SupportedPid>();
            this.pidList.addAll(pidList);
        }

        private class ViewHolder {
            TextView code;
            CheckBox name;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            ViewHolder holder = null;

            if (convertView == null) {
                LayoutInflater vi = (LayoutInflater)getSystemService(
                        Context.LAYOUT_INFLATER_SERVICE);
                convertView = vi.inflate(R.layout.content_pids, null);
                holder = new ViewHolder();
                holder.code = (TextView) convertView.findViewById(R.id.pid);
                holder.name = (CheckBox) convertView.findViewById(R.id.checkbox1);
                convertView.setTag(holder);
            }
            else {
                holder = (ViewHolder) convertView.getTag();
            }

            SupportedPid pid = pidList.get(position);

            holder.code.setText(" (" +  pid.getPid() + ")");
            holder.name.setText(pid.getName());
            holder.name.setChecked(pid.isActive());
            holder.name.setTag(pid);
            holder.name.setClickable(false);

            return convertView;

        }

    }


}
