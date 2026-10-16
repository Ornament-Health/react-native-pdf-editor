import Foundation
import PDFKit
import UIKit

protocol PDFDrawerHistoryDelegate: AnyObject {
  func pdfDrawer(_ drawer: PDFDrawer, historyDidChange state: PDFDrawer.HistoryState)
}

final class PDFDrawer {

  struct HistoryState {
    let canUndo: Bool
    let canRedo: Bool
    let isAtBaseline: Bool
  }

  private struct AnnotationSnapshot: Equatable {
    let id = UUID()
    let annotation: PDFAnnotation
    let page: PDFPage

    static func == (lhs: AnnotationSnapshot, rhs: AnnotationSnapshot) -> Bool {
      lhs.id == rhs.id
    }
  }

  weak var pdfView: PDFView? {
    didSet {
      if oldValue !== pdfView {
        strokeLayer?.removeFromSuperlayer()
        cleanupStrokeContext()
        historyDelegate?.pdfDrawer(self, historyDidChange: historyState)
      }
    }
  }

  weak var historyDelegate: PDFDrawerHistoryDelegate?
  var onHistoryChanged: ((Bool, Bool) -> Void)?

  var color: UIColor = .red
  var width: CGFloat = 5
  var alpha: CGFloat = 0.3

  // The stroke in progress is kept twice: in page space for the ink annotation
  // added on touch-up, and in PDFView space for the live preview. The preview
  // is a layer over the PDFView, not an annotation re-added to the page on every
  // move: PDFKit shows such a re-add as a frame without the annotation followed
  // by one with it, and on iOS 27 the frame without it is the one that stays on
  // screen, so the line stayed invisible until touch-up.
  private var drawingPath: UIBezierPath?
  private var previewPath: UIBezierPath?
  private var strokeLayer: CAShapeLayer?
  private weak var activePage: PDFPage?

  private var undoStack: [AnnotationSnapshot] = []
  private var redoStack: [AnnotationSnapshot] = []
  private var baselineIndex: Int = 0

  private var canUndo: Bool {
    undoStack.count > baselineIndex
  }

  private var canRedo: Bool {
    !redoStack.isEmpty
  }

  // MARK: - Public API

  func markBaseline() {
    baselineIndex = undoStack.count
    redoStack.removeAll()
    notifyHistoryChanged()
  }

  func resetHistory() {
    undoStack.removeAll()
    redoStack.removeAll()
    baselineIndex = 0
    notifyHistoryChanged()
  }

  func undo() {
    guard canUndo, let snapshot = undoStack.popLast() else {
      notifyHistoryChanged()
      return
    }
    snapshot.page.removeAnnotation(snapshot.annotation)
    redoStack.append(snapshot)
    notifyHistoryChanged()
  }

  func redo() {
    guard canRedo, let snapshot = redoStack.popLast() else {
      notifyHistoryChanged()
      return
    }
    snapshot.page.addAnnotation(snapshot.annotation)
    undoStack.append(snapshot)
    notifyHistoryChanged()
  }

  func clear() {
    (undoStack + redoStack).forEach { $0.page.removeAnnotation($0.annotation) }
    undoStack.removeAll()
    redoStack.removeAll()
    baselineIndex = 0
    notifyHistoryChanged()
  }

  // Drops only strokes added after the current baseline, preserving prior
  // committed sessions. Used by the Cancel action so the user keeps drawings
  // that were already accepted via Done.
  func revertToBaseline() {
    let uncommitted = Array(undoStack.suffix(from: baselineIndex)) + redoStack
    uncommitted.forEach { $0.page.removeAnnotation($0.annotation) }
    undoStack = Array(undoStack.prefix(baselineIndex))
    redoStack.removeAll()
    notifyHistoryChanged()
  }
}

// MARK: - DrawingGestureRecognizerDelegate

extension PDFDrawer: DrawingGestureRecognizerDelegate {

  func gestureRecognizerBegan(_ location: CGPoint) {
    guard let pdfView = pdfView, let page = pdfView.page(for: location, nearest: true) else {
      return
    }
    drawingPath = UIBezierPath()
    activePage = page

    let converted = pdfView.convert(location, to: page)
    drawingPath?.move(to: converted)

    previewPath = UIBezierPath()
    previewPath?.move(to: location)

    if !redoStack.isEmpty {
      redoStack.removeAll()
      notifyHistoryChanged()
    }
  }

  func gestureRecognizerMoved(_ location: CGPoint) {
    guard
      let pdfView = pdfView,
      let page = activePage,
      let path = drawingPath,
      let preview = previewPath
    else { return }

    let converted = pdfView.convert(location, to: page)
    path.addLine(to: converted)
    path.move(to: converted)

    if strokeLayer == nil {
      let layer = makeStrokeLayer(scale: pdfView.scaleFactor)
      pdfView.layer.addSublayer(layer)
      strokeLayer = layer
    }

    preview.addLine(to: location)
    strokeLayer?.path = preview.cgPath
  }

  func gestureRecognizerEnded(_ location: CGPoint) {
    guard
      let pdfView = pdfView,
      let page = activePage,
      let path = drawingPath,
      let layer = strokeLayer
    else {
      cleanupStrokeContext()
      return
    }

    let converted = pdfView.convert(location, to: page)
    path.addLine(to: converted)
    path.move(to: converted)

    let finalAnnotation = makeFinalAnnotation(from: path, on: page)
    page.addAnnotation(finalAnnotation)
    // PDFKit draws the added annotation a frame or two later. The preview
    // covers exactly the same pixels, so it stays until then instead of leaving
    // a blank frame on touch-up.
    DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
      layer.removeFromSuperlayer()
    }

    let snapshot = AnnotationSnapshot(annotation: finalAnnotation, page: page)
    undoStack.append(snapshot)
    cleanupStrokeContext()
    notifyHistoryChanged()
  }

  func gestureRecognizerCancelled() {
    strokeLayer?.removeFromSuperlayer()
    cleanupStrokeContext()
  }
}

// MARK: - Helpers

extension PDFDrawer {

  fileprivate var historyState: HistoryState {
    HistoryState(
      canUndo: canUndo,
      canRedo: canRedo,
      isAtBaseline: !canUndo && !canRedo
    )
  }

  fileprivate func notifyHistoryChanged() {
    let state = historyState

    let callback = { [weak self] in
      guard let self = self else { return }
      self.historyDelegate?.pdfDrawer(self, historyDidChange: state)
      self.onHistoryChanged?(state.canUndo, state.canRedo)
    }

    if Thread.isMainThread {
      callback()
    } else {
      DispatchQueue.main.async(execute: callback)
    }
  }

  fileprivate func cleanupStrokeContext() {
    drawingPath = nil
    previewPath = nil
    strokeLayer = nil
    activePage = nil
  }

  // Same geometry as the ink annotation added on touch-up: the page-space width
  // scaled to the current zoom (the PDFView can't scroll or zoom mid-stroke).
  // Drawn translucent until touch-up, when the opaque annotation replaces it.
  fileprivate func makeStrokeLayer(scale: CGFloat) -> CAShapeLayer {
    let layer = CAShapeLayer()
    layer.fillColor = nil
    layer.strokeColor = color.withAlphaComponent(alpha).cgColor
    layer.lineWidth = width * scale
    layer.lineCap = .round
    layer.lineJoin = .round
    // No implicit animation: the path must follow the finger, not ease towards it.
    layer.actions = ["path": NSNull()]
    return layer
  }

  fileprivate func makeFinalAnnotation(from path: UIBezierPath, on page: PDFPage) -> PDFAnnotation {
    let border = PDFBorder()
    border.lineWidth = width

    let bounds = CGRect(
      x: path.bounds.origin.x - 5,
      y: path.bounds.origin.y - 5,
      width: path.bounds.size.width + 10,
      height: path.bounds.size.height + 10
    )

    let centeredPath = UIBezierPath()
    centeredPath.cgPath = path.cgPath
    centeredPath.moveCenter(to: bounds.center)

    let annotation = PDFAnnotation(bounds: bounds, forType: .ink, withProperties: nil)
    annotation.color = color.withAlphaComponent(1.0)
    annotation.border = border
    annotation.add(centeredPath)
    return annotation
  }
}
