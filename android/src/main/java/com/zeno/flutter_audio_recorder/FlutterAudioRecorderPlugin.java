package com.zeno.flutter_audio_recorder;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build.VERSION;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/** FlutterAudioRecorderPlugin */
public class FlutterAudioRecorderPlugin implements MethodCallHandler, PluginRegistry.RequestPermissionsResultListener {
  private static final String LOG_NAME = "AndroidAudioRecorder";
  private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 200;
  private static final byte RECORDER_BPP = 16; // we use 16bit
  private Registrar registrar;
  private int mSampleRate = 16000; // 16Khz
  private AudioRecord mRecorder = null;
  private String mFilePath;
  private String mExtension;
  private int bufferSize = 1024;
  private FileOutputStream mFileOutputStream = null;
  private String mStatus = "unset";
  private double mPeakPower = -120;
  private double mAveragePower = -120;
  private Thread mRecordingThread = null;
  private long mDataSize = 0;
  private Result _result;


  /** Plugin registration. */
  public static void registerWith(Registrar registrar) {

    final MethodChannel channel = new MethodChannel(registrar.messenger(), "flutter_audio_recorder");
    channel.setMethodCallHandler(new FlutterAudioRecorderPlugin(registrar));
  }

  public FlutterAudioRecorderPlugin(Registrar registrar) {
    this.registrar = registrar;
    this.registrar.addRequestPermissionsResultListener(this);
  }

  @Override
  public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    switch (requestCode) {
      case REQUEST_RECORD_AUDIO_PERMISSION:
        boolean granted = true;
        Log.d(LOG_NAME, "parsing result");
        for (int result : grantResults) {
          if(result != PackageManager.PERMISSION_GRANTED) {
            Log.d(LOG_NAME, "result" + result);
            granted = false;
          }
        }
        Log.d(LOG_NAME, "onRequestPermissionsResult -" + granted);
        if(_result != null) {
          _result.success(granted);
        }
        return granted;
        default:
          Log.d(LOG_NAME, "onRequestPermissionsResult - false");
          return false;
    }
  }

  private boolean hasRecordPermission(){
    // if after [Marshmallow], we need to check permission on runtime
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
      return (ContextCompat.checkSelfPermission(registrar.context(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
              && (ContextCompat.checkSelfPermission(registrar.context(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
    } else {
      return ContextCompat.checkSelfPermission(registrar.context(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }
  }

  @Override
  public void onMethodCall(MethodCall call, Result result) {
    // Log.d(LOG_NAME, "calling " + call.method);
    _result = result;

    switch (call.method){
      case "hasPermissions":
        handleHasPermission();
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

  private void handleHasPermission(){
    if(hasRecordPermission()){
      Log.d(LOG_NAME, "handleHasPermission true");
      if(_result != null) {
        _result.success(true);
      }
    } else {
      Log.d(LOG_NAME, "handleHasPermission false");

      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        ActivityCompat.requestPermissions(registrar.activity(), new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_RECORD_AUDIO);
      } else {
        ActivityCompat.requestPermissions(registrar.activity(), new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
      }
    }

  }

  private void handleInit(MethodCall call, Result result)  {
    resetRecorder();
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

  private void handleCurrent(MethodCall call, Result result) {
    HashMap<String, Object> currentResult = new HashMap<>();
    currentResult.put("duration", getDuration() * 1000);
    currentResult.put("path", (mStatus == "stopped")? mFilePath : getTempFilename());
    currentResult.put("audioFormat", mExtension);
    currentResult.put("peakPower", mPeakPower);
    currentResult.put("averagePower", mAveragePower);
    currentResult.put("isMeteringEnabled", true);
    currentResult.put("status", mStatus);
    // Log.d(LOG_NAME, currentResult.toString());
    result.success(currentResult);
  }

  private void handleStart(MethodCall call, Result result) {
    mRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, mSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
    try {
      mFileOutputStream = new FileOutputStream(getTempFilename());
    } catch (FileNotFoundException e) {
      result.error("", "cannot find the file", null);
      return;
    }
    mRecorder.startRecording();
    mStatus = "recording";
    startThread();
    result.success(null);
  }

  private void startThread(){
    mRecordingThread = new Thread(new Runnable() {
      @Override
      public void run() {
        processAudioStream();
      }
    }, "Audio Processing Thread");
    mRecordingThread.start();
  }

  private void handlePause(MethodCall call, Result result) {
    mStatus = "paused";
    mPeakPower = -120;
    mAveragePower = -120;
    mRecorder.stop();
    mRecordingThread = null;
    result.success(null);
  }

  private void handleResume(MethodCall call, Result result) {
    mStatus = "recording";
    mRecorder.startRecording();
    startThread();
    result.success(null);
  }

  private void handleStop(MethodCall call, Result result) {
    if(mStatus.equals("stopped")) {
      result.success(null);
    } else {
      mStatus = "stopped";

      // Return Recording Object
      HashMap<String, Object> currentResult = new HashMap<>();
      currentResult.put("duration", getDuration() * 1000);
      currentResult.put("path", mFilePath);
      currentResult.put("audioFormat", mExtension);
      currentResult.put("peakPower", mPeakPower);
      currentResult.put("averagePower", mAveragePower);
      currentResult.put("isMeteringEnabled", true);
      currentResult.put("status", mStatus);


      resetRecorder();
      mRecordingThread = null;
      mRecorder.stop();
      mRecorder.release();
      try {
        mFileOutputStream.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
      Log.d(LOG_NAME, "before adding the wav header");
      copyWaveFile(getTempFilename(), mFilePath);
      deleteTempFile();

      // Log.d(LOG_NAME, currentResult.toString());
      result.success(currentResult);
    }

  }

  private void processAudioStream() {
    Log.d(LOG_NAME, "processing the stream: " + mStatus);
    int size = bufferSize;
    byte bData[] = new byte[size];

    while (mStatus == "recording"){
      Log.d(LOG_NAME, "reading audio data");
      mRecorder.read(bData, 0, bData.length);
      mDataSize += bData.length;
      updatePowers(bData);
        try {
          mFileOutputStream.write(bData);
        } catch (IOException e) {
          e.printStackTrace();
        }

    }
  }

  private void deleteTempFile() {
    File file = new File(getTempFilename());
    if(file.exists()) {
      file.delete();
    }
  }

  private String getTempFilename() {
    String filepath = mFilePath + ".temp";
    return filepath;
  }

  private void copyWaveFile(String inFilename, String outFilename) {
    FileInputStream in = null;
    FileOutputStream out = null;
    long totalAudioLen = 0;
    long totalDataLen = totalAudioLen + 36;
    long longSampleRate = mSampleRate;
    int channels = 1;
    long byteRate = RECORDER_BPP * mSampleRate * channels / 8;

    byte[] data = new byte[bufferSize];

    try {
      in = new FileInputStream(inFilename);
      out = new FileOutputStream(outFilename);
      totalAudioLen = in.getChannel().size();
      totalDataLen = totalAudioLen + 36;

      WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
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

  private void WriteWaveFileHeader(FileOutputStream out, long totalAudioLen,
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
    short[] out = new short[bData.length/2];
    ByteBuffer.wrap(bData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(out);
    return out;
  }

  private void resetRecorder(){
    mPeakPower = -120;
    mAveragePower = -120;
    mDataSize = 0;
  }

  private void updatePowers(byte[] bdata){
    short[] data = byte2short(bdata);
    short sampleVal = data[data.length - 1];
    String[] escapeStatusList = new String[]{"paused", "stopped", "initialized", "unset"};

    if(sampleVal == 0 || Arrays.asList(escapeStatusList).contains(mStatus)){
      mAveragePower = -120; // to match iOS silent case
    }
    else {
      // iOS factor : to match iOS power level
      double iOSFactor = 0.25;
      mAveragePower = 20 * Math.log(Math.abs(sampleVal) / 32768.0)  * iOSFactor;
    }

    mPeakPower = mAveragePower;
    // Log.d(LOG_NAME, "Peak: " + mPeakPower + " average: "+ mAveragePower);
  }

  private int getDuration(){
    long duration = mDataSize / (mSampleRate * 2 * 1);
    return (int)duration;
  }
}
