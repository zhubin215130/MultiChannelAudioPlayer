package com.example.multichannelaudio;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.provider.MediaStore;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by zhubin on 2015/10/27.
 */
public class AudioDecoder {
    private final static String TAG = "ZB-AudioDecoder";

    private Server mServer;

    private MediaExtractor mExtractor;
    private MediaCodec mMediaCodec;
    private AudioTrack mAudioTrack;

    public static int mAudioChannelCount = 2;
    public static int mAudioOutputSampleRate = 44100; // 44.1kHz
    public static int mAudioChannelConfig = AudioFormat.CHANNEL_OUT_STEREO;
    public static int mAudioEncodingFormat = AudioFormat.ENCODING_PCM_16BIT;
    public static int mAudioBufferSize;

    public int mAudioChannel = -1;
    public static int a = 1;


    private MediaCodec.BufferInfo mBufferInfo;
    private static final long TIME_OUT = 5000;
    private ByteBuffer[] mInputBuffers;
    private ByteBuffer[] mOutputBuffers;
    private byte[] mPCMData;
    private boolean mDoStop = false;

    public void setAudioChannel(int channel) {
        Log.e(TAG, "setAudioChannel() " + channel);
        mAudioChannel = channel;
    }

    public boolean prepareLocalCodec(String filePath, Server server) {
        Log.e(TAG, "prepareLocalCodec() ");
        // Setup a MediaExtractor to get information about the stream
        // and to get samples out of the stream
        mExtractor = new MediaExtractor();
        try {
            mExtractor.setDataSource(filePath);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        if (mExtractor.getTrackCount() > 0) {
            // Get mime type of the first track
            MediaFormat format = mExtractor.getTrackFormat(0);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio")) {
                try {
                    mMediaCodec = MediaCodec.createDecoderByType(mime);
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
                mMediaCodec.configure(format,
                        null, // We don't have a surface in audio decoding
                        null, // No crypto
                        0); // 0 for decoding

                // Select the first track for decoding
                mExtractor.selectTrack(0);
                mMediaCodec.start(); // Fire up the codec

                createAudioTrack();

                mServer = server;

                return true;
            }
        }
        return false;
    }

    public void playback() {
        Log.e(TAG, "playback() ");

        new Thread(new Runnable() {
            @Override
            public void run() {

                if (mMediaCodec == null)
                    return;

                ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
                ByteBuffer[] outBuffers = mMediaCodec.getOutputBuffers();
                ByteBuffer activeOutBuffer = null; // The active output buffer
                int activeIndex = 0; // Index of the active buffer
                long streamingStart = -1;
                int availableOutBytes = 0;
                int writtenableBytes = 0;
                int writeOffset = 0;
                boolean triggerDelay = true;

                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

                boolean EOS = false;

                while (!Thread.interrupted() && !mDoStop) {
                    // Get PCM data from the stream
                    if (!EOS) {
                        // Dequeue an input buffer
                        int inIndex = mMediaCodec.dequeueInputBuffer(TIME_OUT);
                        if (inIndex >= 0) {
                            ByteBuffer buffer = inputBuffers[inIndex];
                            // Fill the buffer with stream data
                            int sampleSize = mExtractor.readSampleData(buffer, 0);
                            // Pass the stream data to the codec for decoding: queueInputBuffer
                            if (sampleSize < 0) {
                                // We have reached the end of the stream
                                mMediaCodec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                EOS = true;
                            } else {


                                if (mServer != null) {
                                    byte[] sendByte = new byte[sampleSize];
                                    buffer.get(sendByte);
                                    mServer.sendBuffer(sendByte, sampleSize);
                                }

                                mMediaCodec.queueInputBuffer(inIndex, 0, sampleSize, mExtractor.getSampleTime(), 0);
                                mExtractor.advance();
                            }
                        }
                    }

                    if (availableOutBytes == 0) {
                        // we don't have any samples available: Dequeue a new output buffer.
                        activeIndex = mMediaCodec.dequeueOutputBuffer(info, TIME_OUT);

                        // outIndex might carry some information for us.
                        switch (activeIndex) {
                            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                                outBuffers = mMediaCodec.getOutputBuffers();
                                break;
                            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                            case MediaCodec.INFO_TRY_AGAIN_LATER:
                                // Nothing to do
                                break;
                            default:
                                // set the activeOutBuffer
                                activeOutBuffer = outBuffers[activeIndex];
                                availableOutBytes = info.size;
                                assert info.offset == 0;
                        }
                    }

                    if (activeOutBuffer != null && availableOutBytes > 0) {
                        writtenableBytes = Math.min(availableOutBytes, mAudioBufferSize - writeOffset);

                        // Copy as many samples to writeBuffer as possible
                        activeOutBuffer.get(mPCMData, writeOffset, writtenableBytes);
                        availableOutBytes -= writtenableBytes;
                        writeOffset += writtenableBytes;

                    }

                    if (writeOffset == mAudioBufferSize) {
                        // The buffer is full. Submit it to the AudioTrack
//                        Log.e(TAG, "Buffer is enough, write to AudioTrack");

                        if (mAudioChannel == Config.AUDIO_LEFT_CHANNEL) {
                            byte[] leftBytes = getChannelPCM(Config.AUDIO_LEFT_CHANNEL, mAudioBufferSize);
                            mAudioTrack.write(leftBytes, 0, leftBytes.length);

                        } else if (mAudioChannel == Config.AUDIO_RIGHT_CHANNEL) {
                            byte[] rightBytes = getChannelPCM(Config.AUDIO_RIGHT_CHANNEL, mAudioBufferSize);
                            mAudioTrack.write(rightBytes, 0, rightBytes.length);

                        } else {
                            //default
                            mAudioTrack.write(mPCMData, 0, mAudioBufferSize);
                        }

                        writeOffset = 0;
                    }

                    if (activeOutBuffer != null && availableOutBytes == 0) {
                        // IMPORTANT: Clear the active buffer!
                        activeOutBuffer.clear();
                        if (activeIndex >= 0) {
                            // Give the buffer back to the codec
                            mMediaCodec.releaseOutputBuffer(activeIndex, false);
                        }
                    }

                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        // Get out of here
                        break;
                    }
                }


                //Clean up
                mDoStop = false;
                mMediaCodec.stop();
                mMediaCodec.release();
                mExtractor.release();
            }
        }).start();

    }

    //public void configureStreamingCodec(byte[] data, int offset, int size) {
    public void configureStreamingCodec() {
        Log.e(TAG, "configureStreamingCodec() ");
//        ByteBuffer configure_csd0;

        close();
        try {
            mMediaCodec = MediaCodec.createDecoderByType("audio/mpeg");
        } catch (Exception e) {
            e.printStackTrace();
        }
/*        MediaFormat format = MediaFormat.createAudioFormat("audio/mpeg", mAudioOutputSampleRate, 2*//*stereo channels*//*);
        configure_csd0 = ByteBuffer.allocateDirect(size);
        configure_csd0.put(data, offset, size);
        configure_csd0.clear();  //!!!!!!!!!  important , must resetConnection position.
        format.setByteBuffer("csd-0", configure_csd0);*/

        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, "audio/mpeg");
        format.setInteger(MediaFormat.KEY_BIT_RATE, 64 * 1024);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, mAudioChannel < 0 ? mAudioChannelCount : 1/*stereo channels*/);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, mAudioOutputSampleRate);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, mAudioBufferSize);

        mMediaCodec.configure(format, null, null, 0);
        mMediaCodec.start();
        mInputBuffers = mMediaCodec.getInputBuffers();
        mOutputBuffers = mMediaCodec.getOutputBuffers();
        mBufferInfo = new MediaCodec.BufferInfo();

        createAudioTrack();
    }

    private void createAudioTrack() {
        Log.e(TAG, "createAudioTrack() ");
        // Create an AudioTrack. Don't make the buffer size too small:
        int channelConfig = mAudioChannel < 0 ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO;

        mAudioBufferSize = 2 * AudioTrack.getMinBufferSize(mAudioOutputSampleRate, mAudioChannelConfig, mAudioEncodingFormat);
        int monoAAudioBufferSize = 2 * AudioTrack.getMinBufferSize(mAudioOutputSampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        mPCMData = new byte[mAudioBufferSize];

        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                mAudioOutputSampleRate,
                channelConfig,
                mAudioEncodingFormat,
                channelConfig == AudioFormat.CHANNEL_OUT_MONO ? monoAAudioBufferSize : mAudioBufferSize,/*set buffer size according to channel counts*/
                AudioTrack.MODE_STREAM);

        // Don't forget to start playing
        mAudioTrack.play();

        Log.e(TAG, "mAudioTrack is started ");
        Log.e(TAG, "mAudioOutputSampleRate " + mAudioOutputSampleRate);
        Log.e(TAG, "mAudioChannelConfig " + channelConfig);
        Log.e(TAG, "mAudioEncodingFormat " + mAudioEncodingFormat);
        Log.e(TAG, "mAudioBufferSize " + mAudioBufferSize);
        Log.e(TAG, "monoAAudioBufferSize " + monoAAudioBufferSize);

        /*// test stereo to mono
        mAudioBufferSize = 2 * AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        int monoAAudioBufferSize = 2 * AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        mPCMData = new byte[mAudioBufferSize];

        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                44100,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                monoAAudioBufferSize,
                AudioTrack.MODE_STREAM);

        // Don't forget to start playing
        mAudioTrack.play();
        Log.e(TAG, "monoAAudioBufferSize " + monoAAudioBufferSize);
        Log.e(TAG, "mAudioBufferSize " + mAudioBufferSize);*/
    }

    public void decode(byte[] data, int offset, int size) {
        int cc = 0;
        if (mMediaCodec == null)
            return;
        while (true) {
            int inputBufIndex = mMediaCodec.dequeueInputBuffer(TIME_OUT);
            if (inputBufIndex >= 0) {
                ByteBuffer dstBuf = mInputBuffers[inputBufIndex];
                dstBuf.position(0);
                dstBuf.put(data, offset, size);
                mMediaCodec.queueInputBuffer(inputBufIndex, 0, size, 0, 0);
                break;
            } else if (cc++ > 10)
                return;
        }

        while (true) {
            int outputBufIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIME_OUT);
            if (outputBufIndex >= 0) {
                mOutputBuffers[outputBufIndex].position(mBufferInfo.offset);
                mOutputBuffers[outputBufIndex].get(mPCMData, 0, mBufferInfo.size);
                // Write PCM data into AudioTrack
                if (mAudioChannel == Config.AUDIO_LEFT_CHANNEL) {
                    byte[] leftBytes = getChannelPCM(Config.AUDIO_LEFT_CHANNEL, mBufferInfo.size);
                    mAudioTrack.write(leftBytes, 0, leftBytes.length);

                } else if (mAudioChannel == Config.AUDIO_RIGHT_CHANNEL) {
                    byte[] rightBytes = getChannelPCM(Config.AUDIO_RIGHT_CHANNEL, mBufferInfo.size);
                    mAudioTrack.write(rightBytes, 0, rightBytes.length);

                } else {
                    //default
                    mAudioTrack.write(mPCMData, 0, mBufferInfo.size);
                }

                mMediaCodec.releaseOutputBuffer(outputBufIndex, false);
            } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                mOutputBuffers = mMediaCodec.getOutputBuffers();
                Log.e(TAG, "output buffers have changed.");
            } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.e(TAG, "output format have changed.");
            } else break;
        }
    }

    private byte[] getChannelPCM(int index, int PCMSize) {
        byte[] channelPCM = null;
        switch (index) {
            case Config.AUDIO_LEFT_CHANNEL:
                channelPCM = new byte[PCMSize / 2];
                for (int i = 0, j = 0, count = 0; i < channelPCM.length && j < PCMSize; ) {
                    if (count == 2) {
                        j += 2;
                        count = 0;
                        continue;
                    }
                    channelPCM[i] = mPCMData[j];
                    i++;
                    j++;
                    count++;
                }
                break;
            case Config.AUDIO_RIGHT_CHANNEL:
                channelPCM = new byte[PCMSize / 2];
                for (int i = 0, j = 0, count = 2; i < channelPCM.length && j < PCMSize; ) {
                    if (count == 2) {
                        j += 2;
                        count = 0;
                        continue;
                    }
                    channelPCM[i] = mPCMData[j];
                    i++;
                    j++;
                    count++;
                }
                break;

            default:
                // should not run to here
                break;
        }
        return channelPCM;
    }

    public void close() {
        Log.e(TAG, "close() ");
        mDoStop = true;
        if (mMediaCodec != null) {
            mMediaCodec.stop();
            mMediaCodec.release();
            mMediaCodec = null;
        }
        if (mAudioTrack != null) {
            mAudioTrack.stop();
            mAudioTrack.release();
            mAudioTrack = null;
        }
    }

    public static void main(String[] args) {
        {
            long begin = System.currentTimeMillis();
            byte[] leftBytes = new byte[20000];
            byte[] mPCMData = new byte[40000];
            for (int i = 0, j = 0, count = 0; i < leftBytes.length && j < mPCMData.length; ) {

                if (count == 2) {
                    j += 2;
                    count = 0;
                    continue;
                }
//            System.out.println("i:" + i + ",j:" + j + ",count:" + count);
                System.out.println("set data leftBytes " + i + " = mPCMData " + j);
                leftBytes[i] = mPCMData[j];
                i++;
                j++;
                count++;
            }
            System.out.println("total duration:" + (System.currentTimeMillis() - begin) + "ms");
        }

        {

            long begin = System.currentTimeMillis();
            byte[] rightBytes = new byte[20];
            byte[] mPCMData = new byte[40];
            for (int i = 0, j = 0, count = 2; i < rightBytes.length && j < mPCMData.length; ) {

                if (count == 2) {
                    j += 2;
                    count = 0;
                    continue;
                }
//            System.out.println("i:" + i + ",j:" + j + ",count:" + count);
                System.out.println("set data rightBytes " + i + " = mPCMData " + j);
                rightBytes[i] = mPCMData[j];
                i++;
                j++;
                count++;
            }
            System.out.println("total duration:" + (System.currentTimeMillis() - begin) + "ms");
        }
    }
}
