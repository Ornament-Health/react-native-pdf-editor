package com.ornament.pdfeditor.bridge

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.common.MapBuilder
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.uimanager.events.RCTEventEmitter
import com.ornament.pdfeditor.PDFEditorView

class RNPDFEditorViewManager : SimpleViewManager<PDFEditorView>() {
    override fun getName() = "RNPDFEditorView"

    override fun createViewInstance(reactContext: ThemedReactContext): PDFEditorView {
        return PDFEditorView(reactContext).apply {
            onSavePDF { urls ->
                val event = Arguments.createMap()
                val urlsArray = urls?.let {
                    Arguments.createArray().apply {
                        it.forEach { pushString(it) }
                    }
                }
                event.putArray("url", urlsArray)
                reactContext.getJSModule(RCTEventEmitter::class.java).receiveEvent(
                    id,
                    "savePDF",
                    event
                )
            }
            onSelectionChanged { count ->
                val event = Arguments.createMap()
                event.putInt("count", count)
                reactContext.getJSModule(RCTEventEmitter::class.java).receiveEvent(
                    id,
                    "selectionChange",
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
        return HashMap(MapBuilder.builder<String, Any>()
            .put(
                "savePDF",
                MapBuilder.of(
                    "phasedRegistrationNames",
                    MapBuilder.of("bubbled", "onSavePDF")
                )
            )
            .put(
                "selectionChange",
                MapBuilder.of(
                    "phasedRegistrationNames",
                    MapBuilder.of("bubbled", "onSelectionChange")
                )
            )
            .build())
    }

    override fun receiveCommand(root: PDFEditorView, commandId: String?, args: ReadableArray?) {
        super.receiveCommand(root, commandId, args)
        when(commandId) {
            "undoAction" -> root.undo()
            "saveAction" -> root.save()
            "clearAction" -> root.clear()
            "cancelEditAction" -> root.cancelEditSession()
            "setEditMode" -> {
                val isEdit = args?.getBoolean(0) ?: false
                root.setEditMode(isEdit)
            }
        }
    }

    override fun onDropViewInstance(view: PDFEditorView) {
        // Called by React Native (Paper and Fabric) when the View is being
        // permanently removed. Routine View detach (e.g. another screen covering
        // the editor) goes through onDetachedFromWindow, which intentionally
        // does NOT clean up — see PDFEditorView.dispose for the full rationale.
        view.dispose()
        super.onDropViewInstance(view)
    }
}
