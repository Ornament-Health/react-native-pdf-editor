//
//  NonSelectablePDFView.swift
//  react-native-pdf-editor
//

import UIKit
import PDFKit

class NonSelectablePDFView: PDFView {

    // Disable selection
    override func canPerformAction(_ action: Selector, withSender sender: Any?) -> Bool {
        return false
    }

    override func addGestureRecognizer(_ gestureRecognizer: UIGestureRecognizer) {
        if gestureRecognizer is UILongPressGestureRecognizer {
            gestureRecognizer.isEnabled = false
        }

        super.addGestureRecognizer(gestureRecognizer)
    }
}
