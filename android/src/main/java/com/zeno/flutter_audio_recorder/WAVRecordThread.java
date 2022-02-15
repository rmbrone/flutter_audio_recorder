package com.zeno.flutter_audio_recorder;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;

public class WAVRecordThread extends RecordThread {
    private static final String TAG = WAVRecordThread.class.getSimpleName();
    private static final byte RECORDER_BPP = 16; // we use 16bit

    private AudioRecord recorder = null;
    private FileOutputStream fileOutputStream = null;
    private double peakPower = -120;
    private double averagePower = -120;
    private Thread recordingThread = null;
    private long dataSize = 0;

    WAVRecordThread(int sampleRate, String filePath, String extension) {
        super(sampleRate, filePath, extension);
    }

    @Override
    public void start() throws IOException {
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        fileOutputStream = new FileOutputStream(getTempFilename());
        recorder.startRecording();
        status = "recording";
        startThread();
    }

    @Override
    public void pause() {
        status = "paused";
        peakPower = -120;
        averagePower = -120;
        recorder.stop();
        recordingThread = null;
    }

    @Override
    public void resume() {
        status = "recording";
        recorder.startRecording();
        startThread();
    }

    @Override
    public HashMap<String, Object> stop() {
        status = "stopped";

        // Return Recording Object
        HashMap<String, Object> currentResult = new HashMap<>();
        currentResult.put("duration", getDuration() * 1000);
        currentResult.put("path", filePath);
        currentResult.put("audioFormat", extension);
        currentResult.put("peakPower", peakPower);
        currentResult.put("averagePower", averagePower);
        currentResult.put("isMeteringEnabled", true);
        currentResult.put("status", status);


        resetRecorder();
        recordingThread = null;
        recorder.stop();
        recorder.release();
        try {
            fileOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "before adding the wav header");
        copyWaveFile(getTempFilename(), filePath);
        deleteTempFile();

        return currentResult;
    }

    @Override
    public void run() {
        Log.d(TAG, "processing the stream: " + status);
        int size = bufferSize;
        byte bData[] = new byte[size];

        while (status == "recording") {
            Log.d(TAG, "reading audio data");
            recorder.read(bData, 0, bData.length);
            dataSize += bData.length;
            updatePowers(bData);
            try {
                fileOutputStream.write(bData);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    private void startThread() {
        recordingThread = new Thread(this, "Audio Processing Thread");
        recordingThread.start();
    }

    private void deleteTempFile() {
        File file = new File(getTempFilename());
        if (file.exists()) {
            file.delete();
        }
    }

    private String getTempFilename() {
        return this.filePath + ".temp";
    }

    private void copyWaveFile(String inFilename, String outFilename) {
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        long totalDataLen = totalAudioLen + 36;
        long longSampleRate = sampleRate;
        int channels = 1;
        long byteRate = RECORDER_BPP * sampleRate * channels / 8;

        byte[] data = new byte[bufferSize];

        try {
            in = new FileInputStream(inFilename);
            out = new FileOutputStream(outFilename);
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;

            writeWaveFileHeader(out, totalAudioLen, totalDataLen,
                    longSampleRate, channels, byteRate);

            while (in.read(data) != -1) {
                out.write(data);
            }

            in.close();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeWaveFileHeader(FileOutputStream out, long totalAudioLen,
                                     long totalDataLen, long longSampleRate, int channels, long byteRate)
            throws IOException {
        byte[] header = new byte[44];

        header[0] = 'R'; // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f'; // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16; // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1; // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (1); // block align
        header[33] = 0;
        header[34] = RECORDER_BPP; // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        out.write(header, 0, 44);
    }

    private short[] byte2short(byte[] bData) {
        short[] out = new short[bData.length / 2];
        ByteBuffer.wrap(bData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(out);
        return out;
    }

    private void resetRecorder() {
        peakPower = -120;
        averagePower = -120;
        dataSize = 0;
    }

    private void updatePowers(byte[] bdata) {
        short[] data = byte2short(bdata);
        short sampleVal = data[data.length - 1];
        String[] escapeStatusList = new String[]{"paused", "stopped", "initialized", "unset"};

        if (sampleVal == 0 || Arrays.asList(escapeStatusList).contains(status)) {
            averagePower = -120; // to match iOS silent case
        } else {
            // iOS factor : to match iOS power level
            double iOSFactor = 0.25;
            averagePower = 20 * Math.log(Math.abs(sampleVal) / 32768.0) * iOSFactor;
        }

        peakPower = averagePower;
        // Log.d(LOG_NAME, "Peak: " + mPeakPower + " average: "+ mAveragePower);
    }

    @Override
    public int getDuration() {
        long duration = dataSize / (sampleRate * 2 * 1);
        return (int) duration;
    }

    @Override
    public double getPeakPower() {
        return peakPower;
    }

    @Override
    public double getAveragePower() {
        return averagePower;
    }

    @Override
    public String getFilePath() {
       return  (status == "stopped") ? filePath : getTempFilename();
    }
}
