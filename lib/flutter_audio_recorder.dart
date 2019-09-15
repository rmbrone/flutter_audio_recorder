import 'dart:async';
import 'dart:io';

import 'package:file/local.dart';
import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;

class FlutterAudioRecorder {
  static const MethodChannel _channel =
      const MethodChannel('flutter_audio_recorder');
  static const String DEFAULT_EXTENSION = '.m4a';
  static LocalFileSystem fs = LocalFileSystem();

  String _path;
  String _extension;
  Recording _recording;

  Future _initRecorder;
  Future get initialized => _initRecorder;
  Recording get recording => _recording;

  FlutterAudioRecorder(String path, AudioFormat audioFormat) {
    _initRecorder = _init(path, audioFormat);
  }

  Future _init(String path, AudioFormat audioFormat) async {
    String extension;
    String extensionInPath;
    if (path != null) {
      // Extension(.xyz) of Path
      extensionInPath = p.extension(path);
      // Use AudioFormat
      if (audioFormat != null) {
        // .m4a != .m4a
        if (_stringToAudioFormat(extensionInPath) != audioFormat) {
          // use AudioOutputFormat
          extension = _audioFormatToString(audioFormat);
          path += extension;
        } else {
          extension = p.extension(path);
        }
      } else {
        // Else, Use Extension that inferred from Path
        // if extension in path is valid
        if (_isValidAudioFormat(extensionInPath)) {
          extension = extensionInPath;
        } else {
          extension = DEFAULT_EXTENSION; // default value
          path += extension;
        }
      }
      File file = fs.file(path);
      if (await file.exists()) {
        throw new Exception("A file already exists at the path :" + path);
      } else if (!await file.parent.exists()) {
        throw new Exception("The specified parent directory does not exist");
      }
    } else {
      extension = DEFAULT_EXTENSION; // default value
    }
    _path = path;
    _extension = extension;

    Map<String, Object> response;
    var result = await _channel
        .invokeMethod('init', {"path": _path, "extension": _extension});

    if (result != false) {
      response = Map.from(result);
    }

    _recording = new Recording();
    _recording.status = _stringToRecordingStatus(response['status']);

    return;
  }

  Future start() async {
    return _channel.invokeMethod('start');
  }

  /// Note: Use [current] to get latest state of recording after [pause]
  Future pause() async {
    return _channel.invokeMethod('pause');
  }

  Future resume() async {
    return _channel.invokeMethod('resume');
  }

  Future<Recording> stop() async {
    Map<String, Object> response;
    var result = await _channel.invokeMethod('stop');

    if (result != null) {
      response = Map.from(result);
      _responseToRecording(response);
    }

    return recording;
  }

  Future<Recording> current({int channel = 0}) async {
    Map<String, Object> response;

    var result = await _channel.invokeMethod('current', {"channel": channel});

    if (result != null) {
      response = Map.from(result);
      _responseToRecording(response);
    }

    return recording;
  }

  static Future<bool> get hasPermissions async {
    bool hasPermission = await _channel.invokeMethod('hasPermissions');
    return hasPermission;
  }

  /*
    util - response msg to recording object.
   */
  void _responseToRecording(Map<String, Object> response) {
    if (response == null) return;

    _recording.duration = new Duration(milliseconds: response['duration']);
    _recording.path = response['path'];
    _recording.audioFormat = _stringToAudioFormat(response['audioFormat']);
    _recording.extension = response['audioFormat'];
    _recording.metering = new AudioMetering(
        peakPower: response['peakPower'],
        averagePower: response['averagePower'],
        isMeteringEnabled: response['isMeteringEnabled']);
    _recording.status = _stringToRecordingStatus(response['status']);
  }

  /*
    util - verify if extension string is supported
   */
  static bool _isValidAudioFormat(String extension) {
    switch (extension) {
      case ".wav":
      case ".mp4":
      case ".aac":
      case ".m4a":
        return true;
      default:
        return false;
    }
  }

  /*
    util - Convert String to Enum
   */
  static AudioFormat _stringToAudioFormat(String extension) {
    switch (extension) {
      case ".wav":
        return AudioFormat.WAV;
      case ".mp4":
      case ".aac":
      case ".m4a":
        return AudioFormat.AAC;
      default:
        return null;
    }
  }

  /*
    Convert Enum to String
   */
  static String _audioFormatToString(AudioFormat format) {
    switch (format) {
      case AudioFormat.WAV:
        return ".wav";
      case AudioFormat.AAC:
        return ".m4a";
      default:
        return ".m4a";
    }
  }

  /*
    util - Convert String to Enum
   */
  static RecordingStatus _stringToRecordingStatus(String status) {
    switch (status) {
      case "unset":
        return RecordingStatus.Unset;
      case "initialized":
        return RecordingStatus.Initialized;
      case "recording":
        return RecordingStatus.Recording;
      case "paused":
        return RecordingStatus.Paused;
      case "stopped":
        return RecordingStatus.Stopped;
      default:
        return RecordingStatus.Unset;
    }
  }
}

class Recording {
  // File path
  String path;
  // Extension
  String extension;
  // Duration in milliseconds
  Duration duration;
  // Audio format
  AudioFormat audioFormat;
  // Metering
  AudioMetering metering;
  // Is currently recording
  RecordingStatus status;
}

class AudioMetering {
  double peakPower;
  double averagePower;
  bool isMeteringEnabled;

  AudioMetering({this.peakPower, this.averagePower, this.isMeteringEnabled});
}

enum RecordingStatus {
  Unset,
  Initialized,
  Recording,
  Paused,
  Stopped,
}

enum AudioFormat {
  AAC,
  WAV,
}
