package com.example.multichannelaudio;

import android.util.Log;

import org.zeromq.ZMQ;

import java.util.StringTokenizer;

/**
 * Created by zhubin on 2015/10/27.
 */
public class Client {
    private final static String TAG = "ZB-Client";
    private static Client mInstance;
    private ZMQ.Socket subscriber;
    private AudioDecoder mAudioDecoder;
    private Thread mConnectThread;
    private int mChannel = -1;
    private boolean isStop = false;

    public static Client getInstance() {
        if (mInstance == null)
            mInstance = new Client();
        return mInstance;
    }

    private Client() {
        // hide default constructor
    }

    public void setChannel(int channel) {
        if (mAudioDecoder != null) {
            mChannel = channel;
            mAudioDecoder.setAudioChannel(mChannel);
        }
    }


    public void init() {
        mAudioDecoder = new AudioDecoder();
    }

    public void start() {

        mConnectThread = createConnectThread();
        mConnectThread.start();
    }

    public void resetConnection() {
        isStop = true;
        while (mConnectThread.isAlive()) {
            Log.e(TAG, "Waiting for previous thread interrupt");
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
            }
        }
        isStop=false;
        mAudioDecoder = new AudioDecoder();
        mAudioDecoder.setAudioChannel(mChannel);
        mConnectThread = createConnectThread();
        mConnectThread.start();
    }

    private Thread createConnectThread() {

        return new Thread(new Runnable() {
            @Override
            public void run() {
                Log.e(TAG, "createConnectThread start");

                if (Config.DEBUG_CONNECTION) {
                    ZMQ.Context context = ZMQ.context(1);

                    //  Socket to talk to server
                    Log.e(TAG, "Collecting updates from weather server");
                    ZMQ.Socket subscriber = context.socket(ZMQ.SUB);
                    subscriber.connect("tcp://" + Server.SERVER_IP + ":" + Server.SERVER_PORT);

                    //  Subscribe to zipcode, default is NYC, 10001
                    String filter = "10001 ";
                    subscriber.subscribe(filter.getBytes());

                    //  Process 100 updates
                    int update_nbr;
                    long total_temp = 0;
                    for (update_nbr = 0; update_nbr < 100; update_nbr++) {
                        //  Use trim to remove the tailing '0' character
                        String string = subscriber.recvStr(0).trim();

                        StringTokenizer sscanf = new StringTokenizer(string, " ");
                        int zipcode = Integer.valueOf(sscanf.nextToken());
                        int temperature = Integer.valueOf(sscanf.nextToken());
                        int relhumidity = Integer.valueOf(sscanf.nextToken());
                        Log.e(TAG, "zipcode "
                                + zipcode + " temperature " + temperature + " relhumidity " + relhumidity);

                        total_temp += temperature;

                    }
                    Log.e(TAG, "Average temperature for zipcode '"
                            + filter + "' was " + (int) (total_temp / update_nbr));

                    subscriber.close();
                    context.term();
                } else {
                    ZMQ.Context context = ZMQ.context(1);
                    subscriber = context.socket(ZMQ.SUB);
                    subscriber.connect("tcp://" + Server.SERVER_IP + ":" + Server.SERVER_PORT);
                    subscriber.subscribe(new String("").getBytes());

                    mAudioDecoder.configureStreamingCodec();

                    while (!Thread.currentThread().isInterrupted() && isStop == false) {
                        byte[] receiveBytes = subscriber.recv();
                        //Log.e(TAG, "receive Buffer size" + receiveBytes.length);
                        mAudioDecoder.decode(receiveBytes, 0, receiveBytes.length);
                    }
                    Log.e(TAG, "connect thread interrupted");
                    subscriber.close();
                    context.term();
                    mAudioDecoder.close();
                }
            }
        });
    }


    public void close() {
        Log.e(TAG, "close()");
        if (mConnectThread != null) {
            isStop = true;
            while (mConnectThread.isAlive()) {
                Log.e(TAG, "Waiting for previous thread interrupt");
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                }
            }
            mConnectThread.interrupt();
        }
    }
}
