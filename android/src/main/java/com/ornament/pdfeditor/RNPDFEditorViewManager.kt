package com.ornament.pdfeditor

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.common.MapBuilder
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.uimanager.events.RCTEventEmitter

class RNPDFEditorViewManager : SimpleViewManager<PDFEditorView>() {
    override fun getName() = "RNPDFEditorView"

    override fun createViewInstance(reactContext: ThemedReactContext): PDFEditorView {
        return PDFEditorView(reactContext).apply {
            onSavePDF { filePath ->
                val event = Arguments.createMap()
                event.putString("url", filePath)
                reactContext.getJSModule(RCTEventEmitter::class.java).receiveEvent(
                    id,
                    "savePDF",
                    event
                )
            }
        }
    }

    @ReactProp(name = "options")
    fun setOptions(view: PDFEditorView, options: ReadableMap) {
        view.setOptions(PDFEditorOptions(options))
    }

    override fun getExportedCustomBubblingEventTypeConstants(): MutableMap<String, Any>? {
        return MapBuilder.builder<String, Any>()
            .put(
                "savePDF",
                MapBuilder.of(
                    "phasedRegistrationNames",
                    MapBuilder.of("bubbled", "onSavePDF")
                )
            )
            .build()
    }

    override fun receiveCommand(root: PDFEditorView, commandId: String?, args: ReadableArray?) {
        super.receiveCommand(root, commandId, args)
        when(commandId) {
            "scrollAction" -> root.setScrollMode()
            "drawAction" -> root.setDrawMode()
            "undoAction" -> root.undo()
            "saveAction" -> root.save()
        }
    }
}
