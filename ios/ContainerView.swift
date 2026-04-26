import Foundation
import PDFKit
import UIKit

@objc(ContainerView)
class ContainerView: UIView {

  // MARK: - UI Components
  @objc var pdfView: NonSelectablePDFView!
  var fileSwitcher: FileSwitcher!
  var editControlsContainer: UIView!
  var bottomOverlayContainer: UIView!
  var bottomOverlayHeightConstraint: NSLayoutConstraint?
  var undoButton: UIButton!
  var redoButton: UIButton!
  var undoButtonBackgroundView: UIView!
  var redoButtonBackgroundView: UIView!
  let editControlDisabledAlpha: CGFloat = 0.4
  let bottomControlsHeight: CGFloat = 120

  // MARK: - Configuration
  @objc var options: [String: Any] = [:] {
    didSet {
      updateWithOptions(options)
    }
  }

  @objc var onSavePDF: RCTDirectEventBlock?
  @objc var onError: RCTDirectEventBlock?

  // MARK: - Drawing & Documents
  var pdfDrawers: [Int: PDFDrawer] = [:]
  var activeDrawer: PDFDrawer?
  var drawerColor: UIColor = .red
  var drawerWidth: CGFloat = 5
  var drawerAlpha: CGFloat = 0.3
  var filePaths = [String]()
  var documents = [RNPDFDocument]()
  var currentDocumentIndex = 0
  var documentHistoryStates: [Int: PDFDrawer.HistoryState] = [:]
  var excludedPages: [Int: Set<Int>] = [:]
  var selectionIconColor: UIColor = .white
  var undoRedoIconColor: UIColor = .white

  // MARK: - Gesture Recognizers
  var isEditMode: Bool = false

  override init(frame: CGRect) {
    super.init(frame: frame)
    setupView()
  }

  required init?(coder: NSCoder) {
    fatalError("RNPDFEditor: init(coder:) has not been implemented")
  }

  override func layoutSubviews() {
    super.layoutSubviews()
    updateBottomOverlayLayout()
    updatePDFBottomInset()
  }

  override func safeAreaInsetsDidChange() {
    super.safeAreaInsetsDidChange()
    updateBottomOverlayLayout()
    updatePDFBottomInset()
  }

  @objc func setEditMode(_ isEdit: Bool) {
    setEditModeImpl(isEdit)
  }

  @objc func save() {
    saveImpl()
  }

  private func updateWithOptions(_ options: [String: Any]) {
    documents.removeAll()
    excludedPages.removeAll()
    documentHistoryStates.removeAll()
    pdfDrawers.removeAll()
    activeDrawer = nil
    filePaths.removeAll()
    currentDocumentIndex = 0
    pdfView.clearPageIndicators()

    if let arrayOfPaths = options["files"] as? [String] {
      filePaths = arrayOfPaths
      for (index, value) in filePaths.enumerated() {
        if let document = RNPDFDocument(id: index, path: value) {
          documents.append(document)
        }
      }
      self.renderDocuments(for: documents)
    } else {
      print("RNPDFEditor: \"files\" value is wrong")
    }

    if let drawLine = options["drawLine"] as? [String: Any] {
      if let lineColor = drawLine["color"] as? String {
        drawerColor = UIColor(hexString: lineColor)
        refreshDrawerAppearances()
      }
      if let lineWidth = drawLine["width"] as? Float {
        drawerWidth = CGFloat(lineWidth)
        refreshDrawerAppearances()
      } else if let lineWidth = drawLine["width"] as? Double {
        drawerWidth = CGFloat(lineWidth)
        refreshDrawerAppearances()
      } else if let lineWidth = drawLine["width"] as? NSNumber {
        drawerWidth = CGFloat(truncating: lineWidth)
        refreshDrawerAppearances()
      }
    }

    if let icons = options["icons"] as? [String: Any] {
      if let iconColor = icons["unselectedColor"] as? String {
        selectionIconColor = UIColor(hexString: iconColor)
      } else {
        selectionIconColor = .white
      }

      if let undoRedoColor = icons["undoRedoColor"] as? String {
        undoRedoIconColor = UIColor(hexString: undoRedoColor)
      } else {
        undoRedoIconColor = .white
      }
    } else {
      selectionIconColor = .white
      undoRedoIconColor = .white
    }

    applyUndoRedoColor()
  }

  private func renderDocuments(for documents: [RNPDFDocument]) {
    if documents.isEmpty {
      print("RNPDFEditor: documents is empty")
      return
    }

    fileSwitcher.configure(with: documents, selectedIndex: currentDocumentIndex)
    renderDocument(at: 0)
  }

  private func loadDocument(_ document: RNPDFDocument, completion: @escaping (PDFDocument?) -> Void)
  {
    if let existing = document.renderedDocument {
      completion(existing)
      return
    }

    document.convert { convertedDocument in
      document.renderedDocument = convertedDocument
      completion(convertedDocument)
    }
  }

  private func renderDocument(at index: Int) {
    guard index >= 0 && index < documents.count else { return }

    currentDocumentIndex = index
    let document = documents[index]

    loadDocument(document) { [weak self] convertedDocument in
      guard let self = self, let convertedDocument = convertedDocument else { return }

      DispatchQueue.main.async {
        self.pdfView.isHidden = false
        self.pdfView.document = convertedDocument
        self.pdfView.disableSelection(in: self.pdfView)
        self.pdfView.onTogglePage = { [weak self] pageIndex in
          self?.togglePageExclusion(pageIndex: pageIndex)
        }

        self.activeDrawer?.pdfView = nil
        let drawer = self.drawer(for: document)
        drawer.pdfView = self.pdfView
        self.pdfView.drawingDelegate = drawer
        self.activeDrawer = drawer
        self.applyStoredHistoryState(for: document)

        // Reset pan/zoom on document load
        self.pdfView.goToFirstPage(nil)
        self.configureScrollView()

        // Defer zoom reset until layout is complete to avoid oversized initial render
        self.setNeedsLayout()
        self.layoutIfNeeded()
        DispatchQueue.main.async { [weak self] in
          guard let self = self else { return }
          self.resetZoomScales()
          self.updatePDFBottomInset()
        }

        self.fileSwitcher.selectFile(at: index)
        self.configurePageIndicators(for: document)
      }
    }
  }

  private func drawer(for document: RNPDFDocument) -> PDFDrawer {
    if let drawer = pdfDrawers[document.id] {
      configureDrawerAppearance(drawer)
      drawer.historyDelegate = self
      return drawer
    }

    let drawer = PDFDrawer()
    configureDrawerAppearance(drawer)
    drawer.historyDelegate = self
    drawer.markBaseline()
    pdfDrawers[document.id] = drawer
    return drawer
  }

  private func applyStoredHistoryState(for document: RNPDFDocument) {
    guard isEditMode else {
      updateUndoRedoButtons(canUndo: false, canRedo: false)
      return
    }

    if let state = documentHistoryStates[document.id] {
      updateUndoRedoButtons(canUndo: state.canUndo, canRedo: state.canRedo)
    } else {
      updateUndoRedoButtons(canUndo: false, canRedo: false)
    }
  }

  private func configureDrawerAppearance(_ drawer: PDFDrawer?) {
    guard let drawer = drawer else { return }
    drawer.color = drawerColor
    drawer.width = drawerWidth
    drawer.alpha = drawerAlpha
  }

  private func refreshDrawerAppearances() {
    for drawer in pdfDrawers.values {
      configureDrawerAppearance(drawer)
    }
    configureDrawerAppearance(activeDrawer)
  }

  private func configurePageIndicators(for document: RNPDFDocument) {
    let excluded = excludedPages[document.id] ?? []
    pdfView.updatePageIndicators(excluded: excluded, iconColor: selectionIconColor)
  }

  private func togglePageExclusion(pageIndex: Int) {
    guard currentDocumentIndex < documents.count else { return }
    let documentId = documents[currentDocumentIndex].id

    var pages = excludedPages[documentId] ?? []
    if pages.contains(pageIndex) {
      pages.remove(pageIndex)
    } else {
      pages.insert(pageIndex)
    }
    excludedPages[documentId] = pages
    configurePageIndicators(for: documents[currentDocumentIndex])
  }

  func commitAllDrawerHistoriesAsBaseline() {
    for (documentId, drawer) in pdfDrawers {
      drawer.markBaseline()
      documentHistoryStates[documentId] = PDFDrawer.HistoryState(
        canUndo: false,
        canRedo: false,
        isAtBaseline: true
      )
    }
  }
}

extension ContainerView {
  @objc func undo() {
    activeDrawer?.undo()
  }

  @objc func redo() {
    activeDrawer?.redo()
  }

  @objc func clear() {
    activeDrawer?.clear()
  }
}

extension ContainerView: FileSwitcherDelegate {
  func didSelectFile(at index: Int) {
    renderDocument(at: index)
  }
}

extension ContainerView: PDFDrawerHistoryDelegate {
  func pdfDrawer(_ drawer: PDFDrawer, historyDidChange state: PDFDrawer.HistoryState) {
    guard let documentId = pdfDrawers.first(where: { $0.value === drawer })?.key else { return }
    documentHistoryStates[documentId] = state

    guard isEditMode else { return }
    guard let current = documents[safe: currentDocumentIndex], current.id == documentId else { return }
    updateUndoRedoButtons(canUndo: state.canUndo, canRedo: state.canRedo)
  }
}

// MARK: - Safe collection access
extension Collection {
  fileprivate subscript(safe index: Index) -> Element? {
    return indices.contains(index) ? self[index] : nil
  }
}
