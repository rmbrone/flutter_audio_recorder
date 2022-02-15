package com.zeno.flutter_audio_recorder;

import io.flutter.plugin.common.PluginRegistry;

final class PermissionRegistry {

    interface AddPermissionResultListener {
        void addListener(PluginRegistry.RequestPermissionsResultListener listener);
    }

    interface RemovePermissionResultListener {
        void removeListener(PluginRegistry.RequestPermissionsResultListener listener);
    }
}
