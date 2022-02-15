package com.zeno.flutter_audio_recorder;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;

public class RequestPermissionHandler implements PluginRegistry.RequestPermissionsResultListener {
    private static final String TAG = RequestPermissionHandler.class.getSimpleName();
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 200;

    private Activity activity;
    private MethodChannel.Result result;

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
        switch (requestCode) {
            case REQUEST_RECORD_AUDIO_PERMISSION:
                boolean granted = true;
                Log.d(TAG, "parsing result");
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "result" + result);
                        granted = false;
                    }
                }
                Log.d(TAG, "onRequestPermissionsResult -" + granted);
                if (result != null) {
                    result.success(granted);
                }
                return granted;
            default:
                Log.d(TAG, "onRequestPermissionsResult - false");
                return false;
        }
    }

    private boolean hasRecordPermission(){
        // if after [Marshmallow], we need to check permission on runtime
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                    && (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
        } else {
            return ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        }
    }

    public void setActivity(Activity activity) {
        this.activity = activity;
    }

    public void handleHasPermission(MethodChannel.Result result){
        this.result = result;
        if(hasRecordPermission()){
            Log.d(TAG, "handleHasPermission true");
            if(result != null) {
                result.success(true);
            }
        } else {
            Log.d(TAG, "handleHasPermission false");

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_RECORD_AUDIO);
            } else {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
            }
        }

    }
}
