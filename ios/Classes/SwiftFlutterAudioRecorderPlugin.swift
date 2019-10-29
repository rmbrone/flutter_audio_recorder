import Flutter
import UIKit
import AVFoundation

public class SwiftFlutterAudioRecorderPlugin: NSObject, FlutterPlugin, AVAudioRecorderDelegate {
    // status - unset, initialized, recording, paused, stopped
    var status = "unset"
    var hasPermissions = false
    var mExtension = ""
    var mPath = ""
    var mSampleRate = 16000
    var channel = 0
    var startTime: Date!
    var settings: [String:Int]!
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
            
            if audioRecorder == nil {
                result(nil)
            } else {
                let dic = call.arguments as! [String : Any]
                channel = dic["channel"] as? Int ?? 0
                
                audioRecorder.updateMeters()
                let duration = Int(audioRecorder.currentTime * 1000)
                var recordingResult = [String : Any]()
                recordingResult["duration"] = duration
                recordingResult["path"] = mPath
                recordingResult["audioFormat"] = mExtension
                recordingResult["peakPower"] = audioRecorder.peakPower(forChannel: channel)
                recordingResult["averagePower"] = audioRecorder.averagePower(forChannel: channel)
                recordingResult["isMeteringEnabled"] = audioRecorder.isMeteringEnabled
                recordingResult["status"] = status
                result(recordingResult)
            }
        case "init":
            print("init")
            
            let dic = call.arguments as! [String : Any]
            mExtension = dic["extension"] as? String ?? ""
            mPath = dic["path"] as? String ?? ""
            mSampleRate = dic["sampleRate"] as? Int ?? 16000
            print("m:", mExtension, mPath)
            startTime = Date()
            if mPath == "" {
                let documentsPath = NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true)[0]
                mPath = documentsPath + "/" + String(Int(startTime.timeIntervalSince1970)) + ".m4a"
                print("path: " + mPath)
            }
            
            settings = [
                AVFormatIDKey: getOutputFormatFromString(mExtension),
                AVSampleRateKey: mSampleRate,
                AVNumberOfChannelsKey: 1,
                AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue
            ]
            
            do {
                #if swift(>=4.2)
                try AVAudioSession.sharedInstance().setCategory(AVAudioSession.Category.playAndRecord, options: AVAudioSession.CategoryOptions.defaultToSpeaker)
                #else
                try AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayAndRecord, with: AVAudioSessionCategoryOptions.defaultToSpeaker)
                #endif
                try AVAudioSession.sharedInstance().setActive(true)
                audioRecorder = try AVAudioRecorder(url: URL(string: mPath)!, settings: settings)
                audioRecorder.delegate = self
                audioRecorder.isMeteringEnabled = true
                audioRecorder.prepareToRecord()
                let duration = Int(audioRecorder.currentTime * 1000)
                status = "initialized"
                var recordingResult = [String : Any]()
                recordingResult["duration"] = duration
                recordingResult["path"] = mPath
                recordingResult["audioFormat"] = mExtension
                recordingResult["peakPower"] = 0
                recordingResult["averagePower"] = 0
                recordingResult["isMeteringEnabled"] = audioRecorder.isMeteringEnabled
                recordingResult["status"] = status
                
                result(recordingResult)
            } catch {
                print("fail")
                result(FlutterError(code: "", message: "Failed to init", details: error))
            }
        case "start":
            print("start")
            
            if status == "initialized" {
                audioRecorder.record()
                status = "recording"
            }
            
            result(nil)
            
        case "stop":
            print("stop")
            
            if audioRecorder == nil || status == "unset" {
                result(nil)
            } else {
                audioRecorder.updateMeters()

                let duration = Int(audioRecorder.currentTime * 1000)
                status = "stopped"
                var recordingResult = [String : Any]()
                recordingResult["duration"] = duration
                recordingResult["path"] = mPath
                recordingResult["audioFormat"] = mExtension
                recordingResult["peakPower"] = audioRecorder.peakPower(forChannel: channel)
                recordingResult["averagePower"] = audioRecorder.averagePower(forChannel: channel)
                recordingResult["isMeteringEnabled"] = audioRecorder.isMeteringEnabled
                recordingResult["status"] = status

                audioRecorder.stop()
                audioRecorder = nil
                result(recordingResult)
            }
        case "pause":
            print("pause")
            
            if audioRecorder == nil {
                result(nil)
            }
            
            if status == "recording" {
                audioRecorder.pause()
                status = "paused"
            }
            
            result(nil)
        case "resume":
            print("resume")
        
            if audioRecorder == nil {
                result(nil)
            }
            
            if status == "paused" {
                audioRecorder.record()
                status = "recording"
            }
            
            result(nil)
        case "hasPermissions":
            print("hasPermissions")
            var permission: AVAudioSession.RecordPermission
            #if swift(>=4.2)
            permission = AVAudioSession.sharedInstance().recordPermission
            #else
            permission = AVAudioSession.sharedInstance().recordPermission()
            #endif
            
            switch permission {
            case .granted:
                print("granted")
                hasPermissions = true
                result(hasPermissions)
                break
            case .denied:
                print("denied")
                hasPermissions = false
                result(hasPermissions)
                break
            case .undetermined:
                print("undetermined")

                AVAudioSession.sharedInstance().requestRecordPermission() { [unowned self] allowed in
                    DispatchQueue.main.async {
                        if allowed {
                            self.hasPermissions = true
                            print("undetermined true")
                            result(self.hasPermissions)
                        } else {
                            self.hasPermissions = false
                            print("undetermined false")
                            result(self.hasPermissions)
                        }
                    }
                }
                break
            default:
                result(hasPermissions)
                break
            }
        default:
            result(FlutterMethodNotImplemented)
        }
    }
    
    // developer.apple.com/documentation/coreaudiotypes/coreaudiotype_constants/1572096-audio_data_format_identifiers
    func getOutputFormatFromString(_ format : String) -> Int {
        switch format {
        case ".mp4", ".aac", ".m4a":
            return Int(kAudioFormatMPEG4AAC)
        case ".wav":
            return Int(kAudioFormatLinearPCM)
        default :
            return Int(kAudioFormatMPEG4AAC)
        }
    }
}
