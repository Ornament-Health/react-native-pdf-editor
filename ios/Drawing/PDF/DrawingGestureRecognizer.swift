//
//  DrawingGestureRecognizer.swift
//  react-native-pdf-editor
//

import UIKit



class DrawingGestureRecognizer: UIGestureRecognizer {
    weak var drawingDelegate: DrawingGestureRecognizerDelegate?

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        if let numberOfTouches = event?.allTouches?.count,
           numberOfTouches == 1 {
            if let touch = touches.first
               // touch.type == .pencil, // Uncomment this line to test on device with Apple Pencil
                {
                state = .began

                let location = touch.location(in: self.view)
                drawingDelegate?.gestureRecognizerBegan(location)
                print("**", touch.gestureRecognizers)
            } else {
                state = .failed
            }
        }
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        if let numberOfTouches = event?.allTouches?.count,
           numberOfTouches == 1 {
            state = .changed

            guard let location = touches.first?.location(in: self.view) else { return }
            drawingDelegate?.gestureRecognizerMoved(location)
        }

    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        if let numberOfTouches = event?.allTouches?.count,
           numberOfTouches == 1 {
            guard let location = touches.first?.location(in: self.view) else {
                state = .ended
                return
            }
            drawingDelegate?.gestureRecognizerEnded(location)
            state = .ended
        }

    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
        if let numberOfTouches = event.allTouches?.count,
           numberOfTouches == 1 {
            state = .failed
        }
    }
}

