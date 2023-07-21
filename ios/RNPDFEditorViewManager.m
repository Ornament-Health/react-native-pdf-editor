#import <React/RCTViewManager.h>
#import <React/RCTUIManager.h>
#import <React/RCTLog.h>

@interface RCT_EXTERN_MODULE(RNPDFEditorViewManager, RCTViewManager)

RCT_EXPORT_VIEW_PROPERTY(options, NSDictionary *)

RCT_EXPORT_VIEW_PROPERTY(onSavePDF, RCTDirectEventBlock)

RCT_EXTERN_METHOD(scrollAction:(nonnull NSNumber *)node)

RCT_EXTERN_METHOD(drawAction:(nonnull NSNumber *)node)

RCT_EXTERN_METHOD(undoAction:(nonnull NSNumber *)node)

RCT_EXTERN_METHOD(saveAction:(nonnull NSNumber *)node)

@end
