package com.zeno.flutter_audio_recorder;

import android.media.AudioFormat;
import android.media.AudioRecord;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;

public abstract class RecordThread implements Runnable{
    protected int sampleRate = 16000; // 16Khz
    protected String filePath;
    protected String extension;
    protected int bufferSize;
    protected String status;

    RecordThread(int sampleRate, String filePath, String extension) {
        this.sampleRate = sampleRate;
        this.filePath = filePath;
        this.extension = extension;
        this.bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        this.status = "initialized";
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getExtension() {
        return extension;
    }

    public String getStatus() {
        return status;
    }

    public abstract void start() throws IOException;

    public abstract void pause();

    public abstract void resume();

    public abstract HashMap<String, Object> stop();

    public abstract int getDuration();

    public abstract double getPeakPower();

    public abstract double getAveragePower();
}
