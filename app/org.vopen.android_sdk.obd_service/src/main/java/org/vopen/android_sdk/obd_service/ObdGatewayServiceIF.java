package org.vopen.android_sdk.obd_service;

/**
 * Created by DBF on 5/30/2015.
 */
public class ObdGatewayServiceIF {
    public static final int OBD_GATEWAY_MSG_STARTSERVICE = 1;
    public static final int OBD_GATEWAY_MSG_STOPSERVICE = 2;
    public static final int OBD_GATEWAY_MSG_DATA = 5;
    public static final int OBD_GATEWAY_MSG_SETSETTINGS = 10;
    public static final int OBD_GATEWAY_MSG_STATUS = 11;

    // Edit (mrangelo) - add supported PIDs command/status
    public static final int OBD_GATEWAY_MSG_SUPPORTED_QUERY = 21;
    public static final int OBD_GATEWAY_MSG_SUPPORTED_RESULT = 22;


    // Status
    public static final int OBD_GATEWAY_STAT_SERVICE_STOPPED = 0;
    public static final int OBD_GATEWAY_STAT_SERVICE_RUNNING = 100;
    // BT Status
    public static final int OBD_GATEWAY_STAT_TRANSPORT_DISABLED = 11;
    public static final int OBD_GATEWAY_STAT_TRANSPORT_OK = 12;
    public static final int OBD_GATEWAY_STAT_TRANSPORT_CONNECTING = 13;
    public static final int OBD_GATEWAY_STAT_TRANSPORT_CONNECTED = 14;
    public static final int OBD_GATEWAY_STAT_TRANSPORT_ERROR = 15;
    public static final int OBD_GATEWAY_STAT_TRANSPORT_CONNECT_ERROR = 16;
}
