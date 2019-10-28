# flutter_audio_recorder
<p align="left">
  <a href="https://pub.dartlang.org/packages/flutter_audio_recorder"><img alt="pub version" src="https://img.shields.io/pub/v/flutter_audio_recorder.svg?style=flat-square"></a>
  <a href="https://github.com/Solido/awesome-flutter">
     <img alt="Awesome Flutter" src="https://img.shields.io/badge/Awesome-Flutter-blue.svg?longCache=true&style=flat-square" />
</p>

[English](./README.md) | 简体中文

Flutter 录音插件 支持录音/暂停/继续/停止, 可以在录音的同时获取到底层提供的音频信息（如声音强度）.
#### 支持 `Android` and `iOS`

<img src="https://user-images.githubusercontent.com/10917606/64927086-b2bcda00-d838-11e9-9ab8-bad78a95f02c.gif" width="30%" height="30%" />

## 安装方式
加入 `flutter_audio_recorder` 到你的 `pubspec.yaml`

#### 代码示例项目: 
- [Flutter Application ( using AndroidX )](https://github.com/nikli2009/flutter_audio_recorder_demo/tree/android-x)
- [Flutter Application ( without AndroidX )](https://github.com/nikli2009/flutter_audio_recorder_demo/tree/non-android-x)

## 权限配置
## iOS 权限 
1. 修改plist, 加入下面这一条
```
<key>NSMicrophoneUsageDescription</key>
<string>Can We Use Your Microphone Please</string>
```
2. 然后在页面需要录音功能的时候调用 `hasPermission` API

## Android Permission
1. 加 `uses-permission` 到 `./android/app/src/main/AndroidManifest.xml`，跟<application>平级， 像下面这样
```
    ...
    </application>
    <uses-permission android:name="android.permission.RECORD_AUDIO"/>
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
    ...
</manifest>
```
2. 然后在页面需要录音功能的时候调用 `hasPermission` API


## 其他配置
#### iOS Deployment Target is 8.0
#### Android
- 开启AndroidX的项目: 请使用最新版本 (`0.5.x`)
- 未使用AndroidX的项目: 可以使用旧版本 (`0.4.9`)

### 注意: iOS Deployment Target 是 8.0

## 用法 
建议使用方式: `hasPermission` => `init` > `start` -> (`pause` <-> `resume`) * n -> `stop` ）, 重新开始新录音的话 流程一样

#### 先需要请求权限（如果已经请求过 则会直接返回结果）
```
bool hasPermission = await FlutterAudioRecorder.hasPermissions;
```

#### Init初始化 (在`录音前`, 调用`初始化`方法，检查文件有无重复)
```
var recorder = FlutterAudioRecorder("filename.mp4"); // .wav .aac .m4a
await recorder.initialized;
```

或者

```
var recorder = FlutterAudioRecorder("file_path", audioFormat: AudioFormat.AAC); // or AudioFormat.WAV
await recorder.initialized;
```

##### 采样率
```
var recorder = FlutterAudioRecorder("file_path", audioFormat: AudioFormat.AAC, sampleRate: 22000); // 采样率默认值 16000
await recorder.initialized;
```


##### Audio Extension 和 Format 对应关系
| Audio Format  | Audio Extension List |
| ------------- | ------------- |
| AAC  | .m4a .aac .mp4  |
| WAV  | .wav  |

#### Start开始录音
```
await recorder.start();
var recording = await recorder.current(channel: 0);
```

#### Current获取当前录音状态信息
```
var current = await recording.current(channel: 0);
// print(current.status);
```
设置一个Timer，定期获取信息（录音结束后，记得`cancel`）
```
new Timer.periodic(tick, (Timer t) async {
        var current = await recording.current(channel: 0);
        // print(current.status);
        setState(() {
        });
      });
```

#### 录音状态 - 数据结构
##### Recording
| Name  | Description |
| ------------- | ------------- |
| path  | String  |
| extension  | String  |
| duration  | Duration  |
| audioFormat  | AudioFormat  |
| metering  | AudioMetering  |
| status  | RecordingStatus  |

##### Recording.metering （声音强度）
| Name  | Description |
| ------------- | ------------- |
| peakPower  | double, 强度极值  |
| averagePower  | double, 强度平均值  |
| isMeteringEnabled  | bool, 是否启用（True）  |

##### Recording.status
`Unset`,`Initialized`,`Recording`,`Paused`,`Stopped`


#### Pause暂停录音
```
await recorder.pause();
```

#### Resume继续录音
```
await recorder.resume();
```

#### Stop停止录音 (停止之后 `stop`, 需再次执行 `init` 重新指定新的文件名，以创建新的录音)
```
var result = await recorder.stop();
File file = widget.localFileSystem.file(result.path);
```

## Example
用Xcode打开Example项目可以查看示例


## Getting Started

This project is a starting point for a Flutter
[plug-in package](https://flutter.dev/developing-packages/),
a specialized package that includes platform-specific implementation code for
Android and/or iOS.

For help getting started with Flutter, view our 
[online documentation](https://flutter.dev/docs), which offers tutorials, 
samples, guidance on mobile development, and a full API reference.
