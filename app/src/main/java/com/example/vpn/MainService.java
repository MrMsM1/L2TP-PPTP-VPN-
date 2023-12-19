package com.example.vpn;

import static androidx.core.app.NotificationCompat.PRIORITY_MIN;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.IConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.android.internal.net.VpnProfile;
import com.google.gson.Gson;
import com.kavosh.mqtthandler.MqttHandler;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainService extends Service {
    private static final int ID_SERVICE = 105;
    VpnProfile profile;
    MqttHandler mqttHandler;
    String brokerAddress = "tcp://localhost";
    Gson gson = new Gson();
    private final IConnectivityManager mService = IConnectivityManager.Stub.asInterface(
            ServiceManager.getService(Context.CONNECTIVITY_SERVICE));
    private final String TAG = "KavoshVPN.Service";
    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind: " + intent.getAction());
        return null;
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        MqttCallbackHandler mqttCallbackHandler = null;
        try {
            mqttHandler = new MqttHandler(brokerAddress,"Sharif2");
            mqttCallbackHandler = new MqttCallbackHandler(mqttHandler.getMqttClient(),new String[]{"vpn/request"}, getApplicationContext());
            mqttHandler.connectToMqttBroker(mqttCallbackHandler);
        } catch (MqttException e) {
            e.printStackTrace();
        }
        MqttCallbackHandler finalMqttCallbackHandler = mqttCallbackHandler;
        Thread brokerConnector = new Thread(()->{
            while (true){
                if (!finalMqttCallbackHandler.isConnected()) {
                    mqttHandler.connectToMqttBroker(finalMqttCallbackHandler);
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
        brokerConnector.start();
        return START_STICKY;
    }
    public void onDestroy() {
        super.onDestroy();
        try {
            mqttHandler.disconnect();
        } catch (MqttException e) {
            throw new RuntimeException(e);
        }
        Log.i(TAG, "onDestroy: ");
    }
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate: ");
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = createNotificationChannel(notificationManager);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, channelId);
        Notification notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();

        startForeground(ID_SERVICE, notification);
    }
    private String createNotificationChannel(NotificationManager notificationManager){
        String channelId = "my_service_channelid";
        String channelName = "My Foreground Service";
        NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
        // omitted the LED color
        channel.setImportance(NotificationManager.IMPORTANCE_NONE);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        notificationManager.createNotificationChannel(channel);
        return channelId;
    }
    public class MqttCallbackHandler implements MqttCallbackExtended {

        private MqttClient client;
        public Boolean connection = false;
        private final String TAG = "MQTTCallBack";
        private String[] topics;
        private Context context;

        public MqttCallbackHandler(MqttClient client, String[] topics, Context context){
            this.client = client;
            this.topics = topics;
            this.context = context;
        }
        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
            Log.i(TAG, "connectComplete");
            this.connection = true;
            try {
                this.client.subscribe(this.topics);
            } catch (MqttException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void connectionLost(Throwable cause) {
            Log.i(TAG, "connectionLost " + cause.toString());
            this.connection = false;
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            Log.d(TAG,"New Message arrived");
            if (topic.equals("vpn/request")){
                Stop();
                VpnRequest vpnRequest = gson.fromJson(new String(message.getPayload()), VpnRequest.class);
                System.out.println("Type and Request: " + vpnRequest.connectionType + " " + vpnRequest.setOn);
                if (vpnRequest.setOn){
                    String[] input =null;
                    if (vpnRequest.connectionType == 0){
                        input = new String[]{"VpnConnection", "PPTP", "0", vpnRequest.pptpServerAddress, vpnRequest.pptpUsername, vpnRequest.pptpPassword,
                                (vpnRequest.pptpActiveDomainNameServer!=null && vpnRequest.pptpStandbyDomainNameServer!=null)? (vpnRequest.pptpActiveDomainNameServer + " " + vpnRequest.pptpStandbyDomainNameServer):
                                        ((vpnRequest.pptpActiveDomainNameServer!=null && vpnRequest.pptpStandbyDomainNameServer==null)?vpnRequest.pptpActiveDomainNameServer:"")
                                , "", ""
                                , "true", "", "", "", "", "", "", "false", "null", "", "false", "false", "1500", "false", "false"};

                    } else if (vpnRequest.connectionType == 1){
                        input = new String[]{"VpnConnection", "L2TP", "1", vpnRequest.l2tpServerAddress, vpnRequest.l2tpUsername, vpnRequest.l2tpPassword, "", "", ""
                                , "true", vpnRequest.l2tpSecret, vpnRequest.l2tpIpSecIdentifier, vpnRequest.l2tpIpSecPreSharedKey, "", "", "", "false", "null", "", "false", "false", "1500", "false", "false"};
                    }
                    CreateProfile(input);
                    Start(profile);
                    if (checkStarted()){

                        try {
                            String response = "{\"isOn\":"+vpnRequest.setOn+", \"status\":"+Boolean.toString(true)+"}";
                            MqttMessage responseMessage = new MqttMessage(response.getBytes());
                            client.publish("vpn/response",responseMessage);
                        } catch (MqttException e) {
                            e.printStackTrace();
                        }

                        ExecutorService executor = Executors.newSingleThreadExecutor();
                        Runnable vpnDisconnectCheck = () ->{
                            while (true) {
                                boolean isConnected = false;
                                try {
                                    isConnected = checkVpnStatus();
                                } catch (RemoteException e) {
                                    e.printStackTrace();
                                }
                                if (!isConnected) {
                                    String response = "{\"isOn\":"+vpnRequest.setOn+", \"status\":"+Boolean.toString(false)+"}";
                                    MqttMessage responseMessage = new MqttMessage(response.getBytes());
                                    try {
                                        client.publish("vpn/response",responseMessage);
                                    } catch (MqttException e) {
                                        e.printStackTrace();
                                    }
                                }
                                try {
                                    Thread.sleep(5000); // Wait for 5 second before checking again
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                        };
                        executor.submit(vpnDisconnectCheck);
                    }else {
                        String response = "{\"isOn\":"+vpnRequest.setOn+", \"status\":"+Boolean.toString(false)+"}";
                        MqttMessage responseMessage = new MqttMessage(response.getBytes());
                        client.publish("vpn/response",responseMessage);
                    }
                }
                else {
//                    Stop();
                }
            }
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            Log.i(TAG, "deliveryComplete");
        }
        public boolean isConnected(){
            return  this.connection;
        }
    }
    private void CreateProfile(String[] input){
        Parcel parcel = Parcel.obtain();
        parcel.writeString(input[0]); //key
        parcel.writeString(input[1]); //name
        parcel.writeInt(Integer.parseInt(input[2])); //type
        parcel.writeString(input[3]); //server
        parcel.writeString(input[4]); //username
        parcel.writeString(input[5]); //password
        parcel.writeString(input[6]); //dnsServers
        parcel.writeString(input[7]); //searchDomains
        parcel.writeString(input[8]); //routes
        parcel.writeInt(Boolean.parseBoolean(input[9]) ? 1 : 0); //mppe
        if (input[10] == null){
            parcel.writeString(""); //l2tpSecret
        } else {
            parcel.writeString(input[10]); //l2tpSecret
        }

        if (input[11] == null){
            parcel.writeString(""); //ipsecIdentifier
        } else {
            parcel.writeString(input[11]); //ipsecIdentifier
        }

        if (input[12] == null){
            parcel.writeString(""); //ipsecSecret
        } else {
            parcel.writeString(input[12]); //ipsecSecret
        }

        parcel.writeString(input[13]); //ipsecUserCert
        parcel.writeString(input[14]); //ipsecCaCert
        parcel.writeString(input[15]); //ipsecServerCert
        parcel.writeInt(Boolean.parseBoolean(input[16]) ? 1 : 0); //saveLogin
        parcel.writeParcelable(Objects.equals(input[17], "null") ? null : null, 0); //proxy
        parcel.writeList(Arrays.asList(input[18].split(" "))); //mAllowedAlgorithms
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            parcel.writeBoolean(Boolean.parseBoolean(input[19])); //isBypassable
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            parcel.writeBoolean(Boolean.parseBoolean(input[20])); //isMetered
        }
        parcel.writeInt(Integer.parseInt(input[21])); // maxMtu
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            parcel.writeBoolean(Boolean.parseBoolean(input[22])); //areAuthParamsInline
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            parcel.writeBoolean(Boolean.parseBoolean(input[23])); //isRestrictedToTestNetworks
        }

        // Reset the parcel's position for reading
        parcel.setDataPosition(0);

        profile = new VpnProfile(parcel);

    }

    private void Start(VpnProfile VpnProfile) throws RemoteException {
        mService.startLegacyVpn(VpnProfile);
    }

    private void Stop() throws RemoteException {
        mService.factoryReset();
    }

    private boolean checkVpnStatus() throws RemoteException {
        Network activeNetwork = mService.getActiveNetwork();
        NetworkCapabilities caps = mService.getNetworkCapabilities(activeNetwork,"com.example.vpn");
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
    }

    static class VpnRequest{
        boolean setOn;
        int connectionType;
        String pptpServerAddress;
        String pptpUsername;
        String pptpPassword;
        String pptpActiveDomainNameServer;
        String pptpStandbyDomainNameServer;
        String l2tpServerAddress;
        String l2tpUsername;
        String l2tpPassword;
        String l2tpSecret;
        String l2tpIpSecIdentifier;
        String l2tpIpSecPreSharedKey;
    }
    private boolean checkStarted() throws InterruptedException, RemoteException {
        boolean started = false;
        long start = System.currentTimeMillis();

        long end  = System.currentTimeMillis();
        while ((end - start)/1000F <= 60){
            if (checkVpnStatus()){
                started = true;
                break;
            }
            TimeUnit.SECONDS.sleep(5);
            end = System.currentTimeMillis();
        }
        return started;
    }
}
