#import <Foundation/Foundation.h>
#import "React/RCTBridgeModule.h"

@interface RCT_EXTERN_REMAP_MODULE(RNDocumentsHandler, DocumentsHandler, NSObject)
RCT_EXTERN_METHOD(process: (NSDictionary)data resolver: (RCTPromiseResolveBlock)resolve rejecter: (RCTPromiseRejectBlock)reject)
@end
