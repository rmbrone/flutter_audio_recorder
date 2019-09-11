import Flutter
import UIKit
import AVFoundation

public class SwiftFlutterAudioRecorderPlugin: NSObject, FlutterPlugin, AVAudioRecorderDelegate {
    var isRecording = false
    var hasPermissions = false
    var mExtension = ""
    var mPath = ""
    var channel = 0
    var startTime: Date!
    var audioRecorder: AVAudioRecorder!
    
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "flutter_audio_recorder", binaryMessenger: registrar.messenger())
        let instance = SwiftFlutterAudioRecorderPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
    }
    
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "current":
            print("current")
            let dic = call.arguments as! [String : Any]
            channel = dic["channel"] as? Int ?? 0
            if(isRecording) {
                audioRecorder.updateMeters()
                
                let duration = Int(Date().timeIntervalSince(startTime as Date) * 1000)
                var recordingResult = [String : Any]()
                recordingResult["duration"] = duration
                recordingResult["path"] = mPath
                recordingResult["audioFormat"] = mExtension
                recordingResult["peakPower"] = audioRecorder.peakPower(forChannel: channel)
                recordingResult["averagePower"] = audioRecorder.averagePower(forChannel: channel)
                recordingResult["isMeteringEnabled"] = audioRecorder.isMeteringEnabled
                recordingResult["isRecording"] = isRecording
                result(recordingResult)
            }
        case "start":
            print("start")
            let dic = call.arguments as! [String : Any]
            mExtension = dic["extension"] as? String ?? ""
            mPath = dic["path"] as? String ?? ""
            print("m:", mExtension, mPath)
            startTime = Date()
            if mPath == "" {
                let documentsPath = NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true)[0]
                mPath = documentsPath + "/" + String(Int(startTime.timeIntervalSince1970)) + ".m4a"
                print("path: " + mPath)
            }
            let settings = [
                AVFormatIDKey: getOutputFormatFromString(mExtension),
                AVSampleRateKey: 12000,
                AVNumberOfChannelsKey: 1,
                AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue
            ]
            do {
                try AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayAndRecord, with: AVAudioSessionCategoryOptions.defaultToSpeaker)
                try AVAudioSession.sharedInstance().setActive(true)
                
                audioRecorder = try AVAudioRecorder(url: URL(string: mPath)!, settings: settings)
                audioRecorder.delegate = self
                audioRecorder.isMeteringEnabled = true
                audioRecorder.record()
            } catch {
                print("fail")
                result(FlutterError(code: "", message: "Failed to record", details: nil))
            }
            isRecording = true
            result(nil)
        case "stop":
            print("stop")
            audioRecorder.updateMeters()
            audioRecorder.stop()
            
            let duration = Int(Date().timeIntervalSince(startTime as Date) * 1000)
            isRecording = false
            var recordingResult = [String : Any]()
            recordingResult["duration"] = duration
            recordingResult["path"] = mPath
            recordingResult["audioFormat"] = mExtension
            recordingResult["peakPower"] = audioRecorder.peakPower(forChannel: channel)
            recordingResult["averagePower"] = audioRecorder.averagePower(forChannel: channel)
            recordingResult["isMeteringEnabled"] = audioRecorder.isMeteringEnabled
            recordingResult["isRecording"] = isRecording
            
            audioRecorder = nil
            result(recordingResult)
        case "pause":
            print("pause")
            audioRecorder.pause()
            
            result(nil)
        case "resume":
            print("resume")
            audioRecorder.record()
            
            result(nil)
        case "hasPermissions":
            print("hasPermissions")
            switch AVAudioSession.sharedInstance().recordPermission(){
            case AVAudioSession.RecordPermission.granted:
                print("granted")
                hasPermissions = true
                break
            case AVAudioSession.RecordPermission.denied:
                print("denied")
                hasPermissions = false
                break
            case AVAudioSession.RecordPermission.undetermined:
                print("undetermined")
                AVAudioSession.sharedInstance().requestRecordPermission() { [unowned self] allowed in
                    DispatchQueue.main.async {
                        if allowed {
                            self.hasPermissions = true
                        } else {
                            self.hasPermissions = false
                        }
                    }
                }
                break
            default:
                break
            }
            result(hasPermissions)
        default:
            result(FlutterMethodNotImplemented)
        }
    }
    
    func getOutputFormatFromString(_ format : String) -> Int {
        switch format {
        case ".mp4", ".aac", ".m4a":
            return Int(kAudioFormatMPEG4AAC)
        default :
            return Int(kAudioFormatMPEG4AAC)
        }
    }
}
