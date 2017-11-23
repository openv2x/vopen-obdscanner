package org.visteon.obdscan;

import android.util.Log;
import org.apache.commons.io.IOUtils;
import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.io.IOException;
import java.io.InputStream;


public class BrokerModule {
    private ObdScanService service;
    private String LOG_TAG;
    private IMqttToken token;


    // MQTT options - please change to suit your needs
    private String URL = "ssl://your.mqtt.server";
    private String TOPIC_PREFIX = "your_topic";
    private String TOPIC_SUFFIX = "your_mqtt_path";

    private int QOS = 1;
    private boolean RETAINED = false;

    private boolean IsConnecting = false;
    private MqttAndroidClient client = null;
    private MqttConnectOptions options = null;


    public BrokerModule(ObdScanService service) {
        this.service = service;
        this.LOG_TAG = this.service.getClass().getName();
    }


    public void connectToBroker(String clientid) {
        if (IsConnecting) {
            return;
        }

        try {
            IsConnecting = true;

            if (client == null) {
                client = new MqttAndroidClient(service.app.getApplicationContext(), URL, clientid);
            }
            if (options == null) {
                options = new MqttConnectOptions();
                // Change your mqtt options as per your requirements
            }

            token = client.connect(options);

            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(LOG_TAG,"Connected successfully");
                    IsConnecting = false;
                    service.setMqttConnected(true);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable e) {
                    IsConnecting = false;
                    Log.e(LOG_TAG, "Error connecting to broker: " + e.toString());
                    service.setMqttConnected(false);
                }
            });
        } catch (MqttException e) {
            IsConnecting = false;
            Log.e(LOG_TAG, "MQTT Exception: " + e.toString());
        }
    }

    public boolean isConnected() {
        try {
            return client.isConnected();
        } catch (NullPointerException e) {
            return false;
        }
    }

    public void publish(String pid, String result, String timestamp) {
        try {
            // Please change the lines below to suit the MQTT message to your format
            String mqttMessage = "{ \"time\": " + timestamp;
            mqttMessage += ", \"value\": " + result + "}";

            String pidbyte = pid.replace(" ","").toLowerCase();
            client.publish(TOPIC_PREFIX + pidbyte + TOPIC_SUFFIX, mqttMessage.getBytes(), QOS, RETAINED);
            Log.d(LOG_TAG,"Message: " + mqttMessage + " PID: "+pidbyte);
            service.setMqttConnected(true);
        } catch (MqttException e) {
            Log.e(LOG_TAG,"MQTT Exception: " + e.toString());
            service.setMqttFailure(pid,result,timestamp);
        }
    }

}
