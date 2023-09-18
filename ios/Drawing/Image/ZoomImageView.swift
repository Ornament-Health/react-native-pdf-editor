//
//  ZoomImageView.swift
//  react-native-pdf-editor
//

import Foundation
import UIKit

class ZoomImageView: UIScrollView {
    private let imageView = UIImageView()

    override init(frame: CGRect) {
        super.init(frame: frame)
        commonInit()
    }

    required init?(coder: NSCoder) {
        fatalError("RNPDFEditor: init(coder:) has not been implemented")
    }

    private func commonInit() {
        // Setup image view
        imageView.translatesAutoresizingMaskIntoConstraints = false
        imageView.contentMode = .scaleAspectFit
        addSubview(imageView)
        NSLayoutConstraint.activate([
            imageView.widthAnchor.constraint(equalTo: widthAnchor),
            imageView.heightAnchor.constraint(equalTo: heightAnchor),
            imageView.centerXAnchor.constraint(equalTo: centerXAnchor),
            imageView.centerYAnchor.constraint(equalTo: centerYAnchor)
        ])

        // Setup scroll view
        minimumZoomScale = 1
        maximumZoomScale = 3
        showsHorizontalScrollIndicator = false
        showsVerticalScrollIndicator = false
        delegate = self
    }

    func setImage(_ image: UIImage) {
        self.imageView.image = image
    }

    override func addGestureRecognizer(_ gestureRecognizer: UIGestureRecognizer) {
        if gestureRecognizer is UILongPressGestureRecognizer {
            gestureRecognizer.isEnabled = false
        }

        super.addGestureRecognizer(gestureRecognizer)
    }

}

extension ZoomImageView: UIScrollViewDelegate {

    func viewForZooming(in scrollView: UIScrollView) -> UIView? {
        return imageView
    }
}

class ImageDrawer {
    weak var imageView: UIImageView!
    private var allPaths: [UIBezierPath] = []
    private var path: UIBezierPath?

    
    var color = UIColor.red // default color is red
    var width: CGFloat = 5 // default width
    var alpha: CGFloat = 0.3 // default alpha
    var drawingTool = DrawingTool.pen

    func undo() {

        if let lastPath = allPaths.last {
            allPaths.removeLast()
        }
    }

    func clear() {
        allPaths = []
    }

}
