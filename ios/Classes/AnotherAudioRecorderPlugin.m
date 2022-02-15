#import "AnotherAudioRecorderPlugin.h"
#import <another_audio_recorder/another_audio_recorder-Swift.h>

@implementation AnotherAudioRecorderPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftAnotherAudioRecorderPlugin registerWithRegistrar:registrar];
}
@end
