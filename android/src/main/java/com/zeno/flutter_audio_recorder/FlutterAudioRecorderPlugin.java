package com.zeno.flutter_audio_recorder;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/**
 * FlutterAudioRecorderPlugin
 */
public class FlutterAudioRecorderPlugin implements FlutterPlugin, ActivityAware {

    private MethodChannel methodChannel;
    private MethodCallHandlerImpl methodCallHandler;

    /**
     * Plugin registration.
     */
    public static void registerWith(Registrar registrar) {
        final FlutterAudioRecorderPlugin plugin = new FlutterAudioRecorderPlugin();
        plugin.initialize(registrar.context(), registrar.messenger());

        if (registrar.activeContext() instanceof Activity) {
            plugin.startListeningToActivity(registrar.activity(), registrar::addRequestPermissionsResultListener);
        }
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        initialize(binding.getApplicationContext(), binding.getBinaryMessenger());
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        destroy();
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        startListeningToActivity(binding.getActivity(), binding::addRequestPermissionsResultListener);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity();
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        onAttachedToActivity(binding);
    }

    @Override
    public void onDetachedFromActivity() {
        stopListeningToActivity();
    }

    private void initialize(Context applicationContext, BinaryMessenger messenger) {
        methodChannel = new MethodChannel(messenger, "flutter_audio_recorder");
        methodCallHandler = new MethodCallHandlerImpl();
        methodChannel.setMethodCallHandler(methodCallHandler);
    }

    private void destroy() {
        methodChannel.setMethodCallHandler(null);
        methodChannel = null;
        methodCallHandler = null;
    }

    private void startListeningToActivity(Activity activity, PermissionRegistry.AddPermissionResultListener permissionRegistry) {
        if (methodCallHandler != null) {
            methodCallHandler.setActivity(activity);
            methodCallHandler.addPermissionResultListener(permissionRegistry);
        }
    }

    private void stopListeningToActivity() {
        if (methodCallHandler != null) {
            methodCallHandler.setActivity(null);
        }
    }
}
