package com.example.multichannelaudio;

import android.util.Log;

import org.zeromq.ZMQ;

import java.util.Random;

/**
 * Created by zhubin on 2015/10/27.
 */
public class Server {
    private final static String TAG = "ZB-Server";
    private static Server mInstance;
    public static final String SERVER_IP = "192.168.1.100";
    public static final String SERVER_PORT = "5556";
    private ZMQ.Context context;
    private ZMQ.Socket publisher;
    private AudioDecoder mAudioDecoder;


    public static Server getInstance() {
        if (mInstance == null)
            mInstance = new Server();
        return mInstance;
    }

    private Server() {
        // hide default constructor
    }


    public void setChannel(int channel) {
        if (mAudioDecoder != null)
            mAudioDecoder.setAudioChannel(channel);
    }

    public void start() {
        // playback local audio
        boolean isSuccess = mAudioDecoder.prepareLocalCodec(Config.MUSIC_PATH, this);
        mAudioDecoder.playback();
    }

    public void init() {

        mAudioDecoder = new AudioDecoder();

        new Thread(new Runnable() {
            @Override
            public void run() {


                if (Config.DEBUG_CONNECTION) {
                    ZMQ.Context context = ZMQ.context(1);
                    ZMQ.Socket publisher = context.socket(ZMQ.PUB);
                    publisher.bind("tcp://" + SERVER_IP + ":5556");

                    //  Initialize random number generator
                    Random srandom = new Random(System.currentTimeMillis());
                    while (!Thread.currentThread().isInterrupted()) {
                        //  Get values that will fool the boss
                        int zipcode, temperature, relhumidity;
                        zipcode = 10000 + srandom.nextInt(10000);
                        temperature = srandom.nextInt(215) - 80 + 1;
                        relhumidity = srandom.nextInt(50) + 10 + 1;

                        //  Send message to all subscribers
                        String update = String.format("%05d %d %d", zipcode, temperature, relhumidity);
//                        System.out.println("send information " + update);
                        publisher.send(update, 0);
                    }

                    publisher.close();
                    context.term();
                } else {
                    context = ZMQ.context(1);
                    publisher = context.socket(ZMQ.PUB);
                    publisher.bind("tcp://" + SERVER_IP + ":5556");
                }
            }
        }).start();

    }

    public void close() {
        Log.e(TAG, "close()");
        if (publisher != null) {
            publisher.close();
            context.term();
        }
    }

    public void sendBuffer(byte[] data, int length) {
        //Log.e(TAG, "send Buffer size" + length);
        if (publisher != null) {
            publisher.send(data);
        }
    }

}
