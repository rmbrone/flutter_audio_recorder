import 'dart:io' as io;
import 'dart:async';
import 'dart:math';

import 'package:flutter_audio_recorder/flutter_audio_recorder.dart';
import 'package:file/file.dart';
import 'package:file/local.dart';
import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';

void main() => runApp(new MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => new _MyAppState();
}

class _MyAppState extends State<MyApp> {
  @override
  Widget build(BuildContext context) {
    return new MaterialApp(
      home: new Scaffold(
        appBar: new AppBar(
          title: new Text('Flutter Audio Recorder'),
        ),
        body: new AppBody(),
      ),
    );
  }
}

class AppBody extends StatefulWidget {
  final LocalFileSystem localFileSystem;

  AppBody({localFileSystem})
      : this.localFileSystem = localFileSystem ?? LocalFileSystem();

  @override
  State<StatefulWidget> createState() => new AppBodyState();
}

class AppBodyState extends State<AppBody> {
  Recording _recording = new Recording();
  Recording _current;
  bool _isRecording = false;
  double _averagePower = 0;
  Random random = new Random();
  TextEditingController _controller = new TextEditingController();
  FlutterAudioRecorder _recorder;
  bool timerToStop = false;

  @override
  Widget build(BuildContext context) {
    return new Center(
      child: new Padding(
        padding: new EdgeInsets.all(8.0),
        child: new Column(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: <Widget>[
              new FlatButton(
                onPressed: _isRecording ? null : _start,
                child: new Text("Start", style: TextStyle(color: Colors.white)),
                color: Colors.green,
              ),
              new FlatButton(
                onPressed: _isRecording ? _pause : _resume,
                child: new Text(_isRecording ? "Pause" : "Resume",
                    style: TextStyle(color: Colors.white)),
                color: _isRecording
                    ? Colors.blueAccent.withOpacity(0.5)
                    : Colors.red,
              ),
              new FlatButton(
                onPressed: _isRecording ? _stop : null,
                child: new Text("Stop"),
                color: Colors.red,
              ),
              new Text('Avg Power: ${_current?.metering?.averagePower}'),
              new Text('Peak Power: ${_current?.metering?.peakPower}'),
//              new TextField(
//                controller: _controller,
//                decoration: new InputDecoration(
//                  hintText: 'Enter a custom path',
//                ),
//              ),
              new Text("File path of the record: ${_current?.path}"),
              new Text("Format: ${_current?.audioFormat}"),
              new Text(
                  "isMeteringEnabled: ${_current?.metering?.isMeteringEnabled}"),
              new Text("Extension : ${_current?.extension}"),
              new Text(
                  "Audio recording duration : ${_current?.duration.toString()}")
            ]),
      ),
    );
  }

  _start() async {
    try {
      if (await FlutterAudioRecorder.hasPermissions) {
        if (_controller.text != null && _controller.text != "") {
          String path = _controller.text;
          if (!_controller.text.contains('/')) {
            io.Directory appDocDirectory =
                await getApplicationDocumentsDirectory();
            path = appDocDirectory.path + '/' + _controller.text;
          }
          print("Start recording: $path");
          _recorder = FlutterAudioRecorder(path, AudioFormat.AAC);
          await _recorder.start();
        } else {
          io.Directory appDocDirectory =
              await getApplicationDocumentsDirectory();
          _recorder = FlutterAudioRecorder(
              appDocDirectory.path +
                  "/flutter_audio_recorder_" +
                  DateTime.now().toString(),
              AudioFormat.AAC);
          await _recorder.start();

          const oneSec = const Duration(milliseconds: 50);
          new Timer.periodic(oneSec, (Timer t) async {
            if (_isRecording) {
              _current = await _recorder.current(channel: 0);
              _averagePower = _current.metering.averagePower;
            }
            setState(() {});
          });
        }
        _current = await _recorder.current(channel: 0);
        bool isRecording = _current.isRecording;
        setState(() {
          _recording = new Recording();
          _isRecording = isRecording;
        });
      } else {
        Scaffold.of(context).showSnackBar(
            new SnackBar(content: new Text("You must accept permissions")));
      }
    } catch (e) {
      print(e);
    }
  }

  _resume() async {
    await _recorder.resume();
    setState(() {
      _isRecording = true;
    });
  }

  _pause() async {
    await _recorder.pause();
    setState(() {
      _isRecording = false;
    });
  }

  _stop() async {
    var result = await _recorder.stop();
    print("Stop recording: ${result.path}");
    File file = widget.localFileSystem.file(result.path);
    print("  File length: ${await file.length()}");
    setState(() {
      _current = result;
      _isRecording = _current.isRecording;
    });
    // _controller.text = recording.path;
  }
}
