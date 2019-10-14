package com.zeno.flutter_audio_recorder;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.TimeUtils;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/** FlutterAudioRecorderPlugin */
public class FlutterAudioRecorderPlugin implements MethodCallHandler {
  private static final String LOG_NAME = "FlutterAudioRecorder";
  private static final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 200;
  private Registrar registrar = null;
  private int mSampleRate = 16000; // 16Khz
  private AudioRecord mRecorder = null;
  private String mFilePath;
  private String mExtension;
  private int bufferSize = 1024;
  private FileOutputStream mFileOutputStream = null;
  private Date mStartTime;
  private String mStatus = "unset";
  private double mPeakPower = 0;
  private double mAveragePower = 0;
  private Thread mRecordingThread = null;


  /** Plugin registration. */
  public static void registerWith(Registrar registrar) {

    final MethodChannel channel = new MethodChannel(registrar.messenger(), "flutter_audio_recorder");
    channel.setMethodCallHandler(new FlutterAudioRecorderPlugin(registrar));
  }

  public FlutterAudioRecorderPlugin(Registrar registrar) {
    this.registrar = registrar;
  }

  private boolean hasRecordPermission(){
    return ContextCompat.checkSelfPermission(registrar.context(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
  }

  @Override
  public void onMethodCall(MethodCall call, Result result) {
    Log.d(LOG_NAME, "calling " + call.method);
    switch (call.method){
      case "getPlatformVersion":
        result.success("Android " + android.os.Build.VERSION.RELEASE);
        break;
      case "hasPermissions":
        boolean hasPermission = handleHasPermission();
        result.success(hasPermission);
        break;
      case "init":
        handleInit(call, result);
        break;
      case "current":
        handleCurrent(call, result);
        break;
      case "start":
        handleStart(call, result);
        break;
      case "pause":
        handlePause(call, result);
        break;
      case "resume":
        handleResume(call, result);
        break;
      case "stop":
        handleStop(call, result);
        break;
      default:
        result.notImplemented();
        break;
    }
  }

  private void handleResume(MethodCall call, Result result) {
    mStatus = "recording";
    mRecorder.startRecording();
    result.success(null);
  }

  private void handlePause(MethodCall call, Result result) {
    mStatus = "paused";
    mRecorder.stop();
    result.success(null);
  }

  private void handleStop(MethodCall call, Result result) {
    mStatus = "stopped";
    mPeakPower = 0;
    mAveragePower = 0;
    mRecordingThread = null;
    mRecorder.stop();
    mRecorder.release();
    try {
      mFileOutputStream.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void handleStart(MethodCall call, Result result) {
    mRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, mSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
    try {
      mFileOutputStream = new FileOutputStream(mFilePath);
    } catch (FileNotFoundException e) {
      result.error("", "cannot find the file", null);
      return;
    }
    mRecorder.startRecording();
    mStatus = "recording";
    mRecordingThread = new Thread(new Runnable() {
      @Override
      public void run() {
        processAudioStream();
      }
    }, "Audio Processing Thread");
    mRecordingThread.start();
    result.success(null);
  }

  private void processAudioStream() {
    Log.d(LOG_NAME, "processing the stream");
//    int BufferElements2Rec = 1024 * 2;
    short sData[] = new short[bufferSize];

    while (mStatus == "recording"){
      Log.d(LOG_NAME, "reading audio data");
      mRecorder.read(sData, 0, bufferSize);
      byte bData[] = short2byte(sData);
      updatePowers(sData);
        try {
          mFileOutputStream.write(bData, 0, bData.length);
        } catch (IOException e) {
          e.printStackTrace();
        }

    }
  }

  private byte[] short2byte(short[] sData) {
    int shortArrsize = sData.length;
    byte[] bytes = new byte[shortArrsize * 2];
    for (int i = 0; i < shortArrsize; i++) {
      bytes[i * 2] = (byte) (sData[i] & 0x00FF);
      bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);
      sData[i] = 0;
    }
    return bytes;

  }

  private void updatePowers(short[] data){
    double max=0;
    double sum=0;
    for(int i=0; i<data.length; i++){
      max = Math.max(Math.abs(data[i]), max);
      sum += Math.abs(data[i]);
    }
    mPeakPower = max;
    mAveragePower = sum / data.length;
  }

  private int getDuration(){
    Date now = new Date();
    long diff = now.getTime() - mStartTime.getTime();
    long duration = TimeUnit.MILLISECONDS.toSeconds(diff);
    return (int)duration;
  }

  private void handleCurrent(MethodCall call, Result result) {
    HashMap<String, Object> currentResult = new HashMap<>();
    currentResult.put("duration", getDuration());
    currentResult.put("path", mFilePath);
    currentResult.put("audioFormat", mExtension);
    currentResult.put("peakPower", mPeakPower);
    currentResult.put("averagePower", mAveragePower);
    currentResult.put("isMeteringEnabled", true);
    currentResult.put("status", mStatus);
    Log.d(LOG_NAME, currentResult.toString());
    result.success(currentResult);
  }

  private void handleInit(MethodCall call, Result result)  {
    mStartTime = new Date();
    mSampleRate = Integer.parseInt(call.argument("sampleRate").toString());
    mFilePath = call.argument("path").toString();
    mExtension = call.argument("extension").toString();
    bufferSize = AudioRecord.getMinBufferSize(mSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
    mStatus = "initialized";
    HashMap<String, Object> initResult = new HashMap<>();
    initResult.put("duration", 0);
    initResult.put("path", mFilePath);
    initResult.put("audioFormat", mExtension);
    initResult.put("peakPower", mPeakPower);
    initResult.put("averagePower", mAveragePower);
    initResult.put("isMeteringEnabled", true);
    initResult.put("status", mStatus);
    result.success(initResult);
  }

  private boolean handleHasPermission(){
    if(hasRecordPermission()){
      return true;
    }
    ActivityCompat.requestPermissions(registrar.activity(), new String[]{Manifest.permission.RECORD_AUDIO}, MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
    int i = 0;
    int TRY_TIMES = 10;
    while (i < TRY_TIMES){
      // check the permission
      SystemClock.sleep(1000);
      if(hasRecordPermission()){
        return true;
      }
      i += 1;
    }
    return false;
  }
}
