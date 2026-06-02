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

  private var drawingPath: UIBezierPath?
  private var draftAnnotation: DrawingAnnotation?
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

    if !redoStack.isEmpty {
      redoStack.removeAll()
      notifyHistoryChanged()
    }
  }

  func gestureRecognizerMoved(_ location: CGPoint) {
    guard
      let pdfView = pdfView,
      let page = activePage,
      let path = drawingPath
    else { return }

    let converted = pdfView.convert(location, to: page)
    path.addLine(to: converted)
    path.move(to: converted)

    if draftAnnotation == nil {
      draftAnnotation = makeDraftAnnotation(on: page)
    }

    draftAnnotation?.path = path
    if let annotation = draftAnnotation {
      forceRedraw(annotation: annotation, on: page)
    }
  }

  func gestureRecognizerEnded(_ location: CGPoint) {
    guard
      let pdfView = pdfView,
      let page = activePage,
      let path = drawingPath,
      let annotation = draftAnnotation
    else {
      cleanupStrokeContext()
      return
    }

    let converted = pdfView.convert(location, to: page)
    path.addLine(to: converted)
    path.move(to: converted)

    page.removeAnnotation(annotation)
    let finalAnnotation = makeFinalAnnotation(from: path, on: page)
    page.addAnnotation(finalAnnotation)

    let snapshot = AnnotationSnapshot(annotation: finalAnnotation, page: page)
    undoStack.append(snapshot)
    cleanupStrokeContext()
    notifyHistoryChanged()
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
    draftAnnotation = nil
    activePage = nil
  }

  fileprivate func makeDraftAnnotation(on page: PDFPage) -> DrawingAnnotation {
    let border = PDFBorder()
    border.lineWidth = width

    let bounds = page.bounds(for: pdfView?.displayBox ?? .cropBox)
    let annotation = DrawingAnnotation(bounds: bounds, forType: .ink, withProperties: nil)
    annotation.color = color.withAlphaComponent(alpha)
    annotation.border = border
    return annotation
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

  fileprivate func forceRedraw(annotation: PDFAnnotation, on page: PDFPage) {
    page.removeAnnotation(annotation)
    page.addAnnotation(annotation)
  }
}
