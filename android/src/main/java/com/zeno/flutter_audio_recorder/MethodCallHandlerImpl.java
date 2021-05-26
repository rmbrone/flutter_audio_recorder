package com.zeno.flutter_audio_recorder;

import android.app.Activity;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

public class MethodCallHandlerImpl implements MethodChannel.MethodCallHandler {
    private static final String TAG = "AndroidAudioRecorder";
    private final RequestPermissionHandler requestPermissionHandler;

    private RecordThread recordThread;

    MethodCallHandlerImpl() {
        this.requestPermissionHandler = new RequestPermissionHandler();
    }

    public void setActivity(Activity activity) {
        requestPermissionHandler.setActivity(activity);
    }


    public void addPermissionResultListener(PermissionRegistry.AddPermissionResultListener permissionRegistry) {
        permissionRegistry.addListener(requestPermissionHandler);
    }

    public void removePermissionResultListener(PermissionRegistry.RemovePermissionResultListener permissionRegistry) {
        permissionRegistry.removeListener(requestPermissionHandler);
    }

    @Override
    public void onMethodCall(MethodCall call, MethodChannel.Result result) {
        switch (call.method) {
            case "hasPermissions":
                handleHasPermission(call, result);
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

    private void handleHasPermission(MethodCall call, MethodChannel.Result result) {
        requestPermissionHandler.handleHasPermission(result);
    }

    private void handleInit(MethodCall call, MethodChannel.Result result) {
        int sampleRate = Integer.parseInt(call.argument("sampleRate").toString());
        String filePath = call.argument("path").toString();
        String extension = call.argument("extension").toString();

        if (isAacExtension(extension))
            recordThread = new AACRecordThread(sampleRate, filePath, extension);
        else
            recordThread = new WAVRecordThread(sampleRate, filePath, extension);

        HashMap<String, Object> initResult = new HashMap<>();
        initResult.put("duration", 0);
        initResult.put("path", recordThread.getFilePath());
        initResult.put("audioFormat", recordThread.getExtension());
        initResult.put("peakPower", recordThread.getPeakPower());
        initResult.put("averagePower", recordThread.getAveragePower());
        initResult.put("isMeteringEnabled", true);
        initResult.put("status", recordThread.getStatus());
        result.success(initResult);
    }

    private boolean isAacExtension(String extension) {
        return extension.equalsIgnoreCase(".AAC") ||
                extension.equalsIgnoreCase(".M4A") ||
                extension.equalsIgnoreCase(".MP4");
    }

    private void handleCurrent(MethodCall call, MethodChannel.Result result) {
        HashMap<String, Object> currentResult = new HashMap<>();
        currentResult.put("duration", recordThread.getDuration() * 1000);
        currentResult.put("path", recordThread.getFilePath());
        currentResult.put("audioFormat", recordThread.getExtension());
        currentResult.put("peakPower", recordThread.getPeakPower());
        currentResult.put("averagePower", recordThread.getAveragePower());
        currentResult.put("isMeteringEnabled", true);
        currentResult.put("status", recordThread.getStatus());
        // Log.d(LOG_NAME, currentResult.toString());
        result.success(currentResult);
    }

    private void handleStart(MethodCall call, MethodChannel.Result result) {
        try {
            recordThread.start();
        } catch (IOException e) {
            result.error("", "cannot find the file", null);
            return;
        }
        result.success(null);
    }

    private void handlePause(MethodCall call, MethodChannel.Result result) {
        recordThread.pause();
        result.success(null);
    }

    private void handleResume(MethodCall call, MethodChannel.Result result) {
        recordThread.resume();
        result.success(null);
    }

    private void handleStop(MethodCall call, MethodChannel.Result result) {
        if (recordThread.getStatus().equals("stopped")) {
            result.success(null);
        } else {
            HashMap<String, Object> currentResult = recordThread.stop();

            // Log.d(LOG_NAME, currentResult.toString());
            result.success(currentResult);
        }

    }
}
