//
//  PDFDrawer.swift
//  react-native-pdf-editor
//

import Foundation
import PDFKit

class PDFDrawer {
    weak var pdfView: PDFView!
    private var path: UIBezierPath?
    private var currentAnnotation: DrawingAnnotation?
    private var allAnnotations: [PDFAnnotation] = []
    private var currentPage: PDFPage?
    var color = UIColor.red // default color is red
    var width: CGFloat = 5 // default width
    var alpha: CGFloat = 0.3 // default alpha

    func undo() {
        if let lastAnnotation = allAnnotations.last,
           let page = lastAnnotation.page {
            page.removeAnnotation(lastAnnotation)
            allAnnotations.removeLast()
        }
    }

    func clear() {
        for index in allAnnotations.indices {
            let annotation = allAnnotations[index]
            annotation.page?.removeAnnotation(annotation)
        }
        allAnnotations = []
    }
}

extension PDFDrawer: DrawingGestureRecognizerDelegate {

    func gestureRecognizerBegan(_ location: CGPoint) {
        guard let page = pdfView.page(for: location, nearest: true) else { return }
        currentPage = page

        let convertedPoint = pdfView.convert(location, to: currentPage!)
        path = UIBezierPath()
        path?.move(to: convertedPoint)
    }

    func gestureRecognizerMoved(_ location: CGPoint, lineWidth: CGFloat) {

        guard let page = currentPage else { return }
        let convertedPoint = pdfView.convert(location, to: page)

        path?.addLine(to: convertedPoint)
        path?.move(to: convertedPoint)
      drawAnnotation(onPage: page, lineWidth: lineWidth)
    }

    func gestureRecognizerEnded(_ location: CGPoint, lineWidth: CGFloat) {
        
        guard let page = currentPage else { return }
        let convertedPoint = pdfView.convert(location, to: page)

        guard let _ = currentAnnotation else { return }

        path?.addLine(to: convertedPoint)
        path?.move(to: convertedPoint)

        page.removeAnnotation(currentAnnotation!)
      let finalAnnotation = createFinalAnnotation(path: path!, page: page, lineWidth: lineWidth)
        page.addAnnotation(finalAnnotation)
        currentAnnotation = nil

        allAnnotations.append(finalAnnotation)
    }

    private func createAnnotation(path: UIBezierPath, page: PDFPage, lineWidth: CGFloat) -> DrawingAnnotation {
        let border = PDFBorder()
        border.lineWidth = lineWidth

        let annotation = DrawingAnnotation(bounds: page.bounds(for: pdfView.displayBox), forType: .ink, withProperties: nil)
        annotation.color = color.withAlphaComponent(alpha)
        annotation.border = border
        return annotation
    }

    private func drawAnnotation(onPage: PDFPage, lineWidth: CGFloat) {
        guard let path = path else { return }

        if currentAnnotation == nil {
          currentAnnotation = createAnnotation(path: path, page: onPage, lineWidth: lineWidth)
        }

        currentAnnotation?.path = path
        forceRedraw(annotation: currentAnnotation!, onPage: onPage)
    }

  private func createFinalAnnotation(path: UIBezierPath, page: PDFPage, lineWidth: CGFloat) -> PDFAnnotation {
        let border = PDFBorder()
        border.lineWidth = lineWidth

        let bounds = CGRect(x: path.bounds.origin.x - 5,
                            y: path.bounds.origin.y - 5,
                            width: path.bounds.size.width + 10,
                            height: path.bounds.size.height + 10)
        let signingPathCentered = UIBezierPath()
        signingPathCentered.cgPath = path.cgPath
        signingPathCentered.moveCenter(to: bounds.center)

        let annotation = PDFAnnotation(bounds: bounds, forType: .ink, withProperties: nil)
        annotation.color = color.withAlphaComponent(1.0)
        annotation.border = border
        annotation.add(signingPathCentered)

        return annotation
    }

    private func forceRedraw(annotation: PDFAnnotation, onPage: PDFPage) {
        onPage.removeAnnotation(annotation)
        onPage.addAnnotation(annotation)
    }
}

