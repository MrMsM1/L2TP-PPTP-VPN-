package com.example.vpn;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.net.IConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;


import com.android.internal.net.VpnProfile;
import com.google.gson.Gson;
import com.kavosh.mqtthandler.MqttHandler;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.EOFException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private final String TAG = "KAVOSHVpnManager";
    VpnProfile profile;
    MqttHandler mqttHandler;
    String brokerAddress = "tcp://192.168.1.101";
    Gson gson = new Gson();
    private final IConnectivityManager mService = IConnectivityManager.Stub.asInterface(
            ServiceManager.getService(Context.CONNECTIVITY_SERVICE));

    public class MqttCallbackHandler implements MqttCallbackExtended{

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
                else if (!vpnRequest.setOn){
                    Stop();
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
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate: start");
        try {
            mqttHandler = new MqttHandler(brokerAddress,"Sharif");
            mqttHandler.connectToMqttBroker(new MqttCallbackHandler(mqttHandler.getMqttClient(),new String[]{"vpn/request"}, getApplicationContext()));
        } catch (MqttException e) {
            e.printStackTrace();
        }
        System.out.println(mqttHandler.mqttClient.isConnected());

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
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
        parcel.writeString(input[10]); //l2tpSecret
        parcel.writeString(input[11]); //ipsecIdentifier
        parcel.writeString(input[12]); //ipsecSecret
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
    @Override
    protected void onDestroy() {
        try {
            this.mqttHandler.disconnect();
        } catch (MqttException e) {
            throw new RuntimeException(e);
        }
        super.onDestroy();
    }
}