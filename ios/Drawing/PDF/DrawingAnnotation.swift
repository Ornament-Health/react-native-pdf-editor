//
//  DrawingAnnotation.swift
//  react-native-pdf-editor
//

import Foundation
import PDFKit

class DrawingAnnotation: PDFAnnotation {
    public var path = UIBezierPath()

    override func draw(with box: PDFDisplayBox, in context: CGContext) {
        guard let pathCopy = path.copy() as? UIBezierPath else { return }
        
        UIGraphicsPushContext(context)
        context.saveGState()

        context.setShouldAntialias(true)

        color.set()
        pathCopy.lineJoinStyle = .round
        pathCopy.lineCapStyle = .round
        pathCopy.lineWidth = border?.lineWidth ?? 1.0
        pathCopy.stroke()

        context.restoreGState()
        UIGraphicsPopContext()
    }
}
