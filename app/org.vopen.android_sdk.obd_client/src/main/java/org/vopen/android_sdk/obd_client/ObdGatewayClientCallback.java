package org.vopen.android_sdk.obd_client;

import org.vopen.android_sdk.obd_service.ObdCommandJob;
/**
 * Created by DBF on 5/31/2015.
 */
public interface ObdGatewayClientCallback
{
    void onObdReceiveData(ObdCommandJob obdCmd);
    void onObdReceiveStatus(int status);
}
