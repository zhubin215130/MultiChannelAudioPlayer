package com.example.multichannelaudio;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


public class MainActivity extends ActionBarActivity {
    private final static String TAG = "ZB-MainActivity";

    private Button mServerButton = null;
    private Button mClientButton = null;
    private Button mPlayButton = null;

    private Button mLeftChannelButton = null;
    private Button mRightChannelButton = null;
    private Button mResyncButton = null;

    private Server mServer = null;
    private Client mClient = null;
    private TextView infoView = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        infoView = (TextView) findViewById(R.id.info);
        mServerButton = (Button) findViewById(R.id.serverButton);
        mServerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startServer();
            }
        });
        mClientButton = (Button) findViewById(R.id.clientButton);
        mClientButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startClient();
            }
        });
        mPlayButton = (Button) findViewById(R.id.playButton);
        mPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startPlayback();
            }
        });
        mLeftChannelButton = (Button) findViewById(R.id.leftChannelButton);
        mLeftChannelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mClient != null) {
                    mClient.setChannel(Config.AUDIO_LEFT_CHANNEL);
                    mClient.start();
                } else if (mServer != null) {
                    mServer.setChannel(Config.AUDIO_LEFT_CHANNEL);
                }
                mLeftChannelButton.setEnabled(false);
                mRightChannelButton.setVisibility(View.INVISIBLE);
                mResyncButton.setEnabled(true);
            }
        });
        mRightChannelButton = (Button) findViewById(R.id.rightChannelButton);
        mRightChannelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mClient != null) {
                    mClient.setChannel(Config.AUDIO_RIGHT_CHANNEL);
                    mClient.start();
                } else if (mServer != null) {
                    mServer.setChannel(Config.AUDIO_LEFT_CHANNEL);
                }
                mRightChannelButton.setEnabled(false);
                mLeftChannelButton.setVisibility(View.INVISIBLE);
                mResyncButton.setEnabled(true);

            }
        });
        mResyncButton = (Button) findViewById(R.id.resyncButton);
        mResyncButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mClient != null) {
                    mClient.resetConnection();
                }
            }
        });

        //获取wifi服务
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        //判断wifi是否开启
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
        }
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ipAddress = wifiInfo.getIpAddress();
        String ip = (ipAddress & 0xFF) + "." + ((ipAddress >> 8) & 0xFF) + "." + ((ipAddress >> 16) & 0xFF) + "." + (ipAddress >> 24 & 0xFF);
        infoView.setText("IP_address:" + ip + ", " + (ip.equals(Server.SERVER_IP) ? "should act as Server" : "should act as Client"));

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mServer != null) {
            mServer.close();
        } else if (mClient != null) {
            mClient.close();
        }
    }

    private void startServer() {
        infoView.setText("Start as server!");
        mServerButton.setEnabled(false);
        mClientButton.setVisibility(View.INVISIBLE);
        mPlayButton.setEnabled(true);

        mServer = Server.getInstance();
        mServer.init();
    }

    private void startClient() {
        infoView.setText("Start as client!");
        mClientButton.setEnabled(false);
        mServerButton.setVisibility(View.INVISIBLE);
        mPlayButton.setEnabled(false);

        mClient = Client.getInstance();
        mClient.init();

        mLeftChannelButton.setEnabled(true);
        mRightChannelButton.setEnabled(true);
    }

    private void startPlayback() {
        infoView.setText("Start playback!");
        if (mServer != null) {
            mServer.start();
        }
        mPlayButton.setEnabled(false);
        mPlayButton.setText("Playing");
        mLeftChannelButton.setEnabled(false);
        mRightChannelButton.setEnabled(false);
    }
}
