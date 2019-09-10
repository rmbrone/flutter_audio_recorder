#import "FlutterAudioRecorderPlugin.h"
#import <flutter_audio_recorder/flutter_audio_recorder-Swift.h>

@implementation FlutterAudioRecorderPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftFlutterAudioRecorderPlugin registerWithRegistrar:registrar];
}
@end
