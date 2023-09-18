//
//  NonSelectablePDFView.swift
//  react-native-pdf-editor
//

import UIKit
import PDFKit

protocol DrawingGestureRecognizerDelegate: AnyObject {
    func gestureRecognizerBegan(_ location: CGPoint)
    func gestureRecognizerMoved(_ location: CGPoint)
    func gestureRecognizerEnded(_ location: CGPoint)
}

class NonSelectablePDFView: PDFView {

    weak var drawingDelegate: DrawingGestureRecognizerDelegate?
    private var pan: UIPanGestureRecognizer?

    override init(frame: CGRect) {
        super.init(frame: frame)

        let pan = UIPanGestureRecognizer(target: self, action: #selector(handlePan))
        pan.delegate = self
        pan.minimumNumberOfTouches = 1
        pan.maximumNumberOfTouches = 1
        pan.cancelsTouchesInView = false

        self.pan = pan
        self.addGestureRecognizer(pan)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    /**
     * Should be called AFTER PDFView's document is set
     */
    func disableSelection(in view: UIView) {
        for rec in view.subviews.compactMap({$0.gestureRecognizers}).flatMap({$0}) {
            if rec is UILongPressGestureRecognizer || type(of: rec).description() == "UITapAndAHalfRecognizer" {
                rec.isEnabled = false
            }
        }

        for view in view.subviews {
            if !view.subviews.isEmpty {
                disableSelection(in: view)
            }
        }
    }

    override func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
       return false
    }

    override func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldBeRequiredToFailBy otherGestureRecognizer: UIGestureRecognizer) -> Bool {

        if gestureRecognizer == self.pan && otherGestureRecognizer != self.pan {
            return true
        } else { return false }
    }

    @objc func handlePan(_ sender: UIPanGestureRecognizer) {
        let location = sender.location(in: self)

        switch sender.state {
        case .began:
            drawingDelegate?.gestureRecognizerBegan(location)
        case .changed:
            drawingDelegate?.gestureRecognizerMoved(location)
        case .ended:
            drawingDelegate?.gestureRecognizerEnded(location)
        default:
            break
        }
    }

}
