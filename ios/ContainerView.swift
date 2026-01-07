import Foundation
import PDFKit
import UIKit

@objc(ContainerView)
class ContainerView: UIView {

    // MARK: - UI Components
    @objc var pdfView: NonSelectablePDFView!
    private var fileSwitcher: FileSwitcher!
    private var editControlsContainer: UIView!
    private var undoButton: UIButton!
    private var redoButton: UIButton!

    // MARK: - Configuration
    @objc var options: [String: Any] = [:] {
        didSet {
            updateWithOptions(options)
        }
    }

    @objc var onSavePDF: RCTDirectEventBlock?
    @objc var onError: RCTDirectEventBlock?

    // MARK: - Drawing & Documents
    private let pdfDrawer = PDFDrawer()
    private var filePaths = [String]()
    private var documents = [RNPDFDocument]()
    private var currentDocumentIndex = 0
    private var documentDrawings: [Int: [Any]] = [:]
    private var excludedPages: [Int: Set<Int>] = [:]
    private var selectionIconColor: UIColor = .white

    // MARK: - Gesture Recognizers
    private var isEditMode: Bool = false

    override init(frame: CGRect) {
        super.init(frame: frame)
        setupView()
    }

    required init?(coder: NSCoder) {
        fatalError("RNPDFEditor: init(coder:) has not been implemented")
    }

    func setEditMode(_ isEdit: Bool) {
        self.isEditMode = isEdit
        pdfView.setDrawingEnabled(isEdit)
        
        // In edit mode, disable PDFView's built-in pan/zoom to allow drawing
        // In view mode, enable PDFView's built-in pan/zoom
        if let scrollView = scrollViewIfPresent() {
            scrollView.isScrollEnabled = !isEdit
        }

        updateBottomControlsVisibility()
    }

    private func updateBottomControlsVisibility() {
        fileSwitcher?.isHidden = isEditMode
        editControlsContainer?.isHidden = !isEditMode
    }

    private func applySelectionIconColor() {
        guard let undoButton = undoButton, let redoButton = redoButton else { return }
        
        // Update circular background colors
        undoButton.superview?.backgroundColor = selectionIconColor
        redoButton.superview?.backgroundColor = selectionIconColor

        if #available(iOS 13.0, *) {
            return
        }

        undoButton.setTitleColor(.clear, for: .normal)
        redoButton.setTitleColor(.clear, for: .normal)
    }

    private func makeEditControlsContainer() -> UIView {
        let containerView = UIView()
        containerView.translatesAutoresizingMaskIntoConstraints = false

        // Create circular background view for undo button
        let undoBackgroundView = UIView()
        undoBackgroundView.translatesAutoresizingMaskIntoConstraints = false
        undoBackgroundView.backgroundColor = selectionIconColor
        undoBackgroundView.layer.cornerRadius = 28
        undoBackgroundView.clipsToBounds = true

        let undoButton = UIButton(type: .system)
        undoButton.translatesAutoresizingMaskIntoConstraints = false
        if #available(iOS 13.0, *) {
            // Add cut-out arrow using compositing filter instead of button image
            let undoIconView = UIImageView(image: UIImage(systemName: "arrow.uturn.left"))
            undoIconView.translatesAutoresizingMaskIntoConstraints = false
            undoIconView.tintColor = .black
            undoIconView.contentMode = .scaleAspectFit
            undoIconView.isUserInteractionEnabled = false
            undoIconView.layer.compositingFilter = "destinationOut"
            undoBackgroundView.addSubview(undoIconView)
            NSLayoutConstraint.activate([
                undoIconView.centerXAnchor.constraint(equalTo: undoBackgroundView.centerXAnchor),
                undoIconView.centerYAnchor.constraint(equalTo: undoBackgroundView.centerYAnchor),
                undoIconView.widthAnchor.constraint(equalToConstant: 24),
                undoIconView.heightAnchor.constraint(equalToConstant: 24),
            ])
        } else {
            undoButton.setTitle("Undo", for: .normal)
            undoButton.titleLabel?.font = UIFont.systemFont(ofSize: 16, weight: .medium)
        }
        undoButton.tintColor = .clear
        undoButton.backgroundColor = .clear
        undoButton.contentEdgeInsets = .zero

        // Place the button over the background view to capture taps
        undoBackgroundView.addSubview(undoButton)
        NSLayoutConstraint.activate([
            undoButton.leadingAnchor.constraint(equalTo: undoBackgroundView.leadingAnchor),
            undoButton.trailingAnchor.constraint(equalTo: undoBackgroundView.trailingAnchor),
            undoButton.topAnchor.constraint(equalTo: undoBackgroundView.topAnchor),
            undoButton.bottomAnchor.constraint(equalTo: undoBackgroundView.bottomAnchor),
        ])

        // Create circular background view for redo button
        let redoBackgroundView = UIView()
        redoBackgroundView.translatesAutoresizingMaskIntoConstraints = false
        redoBackgroundView.backgroundColor = selectionIconColor
        redoBackgroundView.layer.cornerRadius = 28
        redoBackgroundView.clipsToBounds = true

        let redoButton = UIButton(type: .system)
        redoButton.translatesAutoresizingMaskIntoConstraints = false
        if #available(iOS 13.0, *) {
            // Add cut-out arrow using compositing filter instead of button image
            let redoIconView = UIImageView(image: UIImage(systemName: "arrow.uturn.right"))
            redoIconView.translatesAutoresizingMaskIntoConstraints = false
            redoIconView.tintColor = .black
            redoIconView.contentMode = .scaleAspectFit
            redoIconView.isUserInteractionEnabled = false
            redoIconView.layer.compositingFilter = "destinationOut"
            redoBackgroundView.addSubview(redoIconView)
            NSLayoutConstraint.activate([
                redoIconView.centerXAnchor.constraint(equalTo: redoBackgroundView.centerXAnchor),
                redoIconView.centerYAnchor.constraint(equalTo: redoBackgroundView.centerYAnchor),
                redoIconView.widthAnchor.constraint(equalToConstant: 24),
                redoIconView.heightAnchor.constraint(equalToConstant: 24),
            ])
        } else {
            redoButton.setTitle("Redo", for: .normal)
            redoButton.titleLabel?.font = UIFont.systemFont(ofSize: 16, weight: .medium)
        }
        redoButton.tintColor = .clear
        redoButton.backgroundColor = .clear
        redoButton.contentEdgeInsets = .zero

        // Place the button over the background view to capture taps
        redoBackgroundView.addSubview(redoButton)
        NSLayoutConstraint.activate([
            redoButton.leadingAnchor.constraint(equalTo: redoBackgroundView.leadingAnchor),
            redoButton.trailingAnchor.constraint(equalTo: redoBackgroundView.trailingAnchor),
            redoButton.topAnchor.constraint(equalTo: redoBackgroundView.topAnchor),
            redoButton.bottomAnchor.constraint(equalTo: redoBackgroundView.bottomAnchor),
        ])

        let stack = UIStackView(arrangedSubviews: [undoBackgroundView, redoBackgroundView])
        stack.translatesAutoresizingMaskIntoConstraints = false
        stack.axis = .horizontal
        stack.alignment = .center
        stack.spacing = 16
        stack.setContentHuggingPriority(.required, for: .horizontal)
        stack.setContentCompressionResistancePriority(.required, for: .horizontal)

        containerView.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.centerXAnchor.constraint(equalTo: containerView.centerXAnchor),
            stack.centerYAnchor.constraint(equalTo: containerView.centerYAnchor),
            stack.leadingAnchor.constraint(
                greaterThanOrEqualTo: containerView.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(
                lessThanOrEqualTo: containerView.trailingAnchor, constant: -16),
            undoBackgroundView.widthAnchor.constraint(equalToConstant: 56),
            undoBackgroundView.heightAnchor.constraint(equalToConstant: 56),
            redoBackgroundView.widthAnchor.constraint(equalToConstant: 56),
            redoBackgroundView.heightAnchor.constraint(equalToConstant: 56),
        ])

        self.undoButton = undoButton
        self.redoButton = redoButton
        self.editControlsContainer = containerView
        applySelectionIconColor()

        return containerView
    }

    private func setupView() {
        let pdfView = NonSelectablePDFView()
        pdfView.backgroundColor = .clear
        pdfView.translatesAutoresizingMaskIntoConstraints = false
        pdfView.displayMode = .singlePageContinuous
        pdfView.displayDirection = .vertical
        pdfView.usePageViewController(false)
        pdfView.pageBreakMargins = UIEdgeInsets(top: 10, left: 10, bottom: 10, right: 10)
        pdfView.autoScales = true
        pdfView.minScaleFactor = 1.0
        pdfView.maxScaleFactor = 3.0
        pdfView.setDrawingEnabled(false)
        pdfView.isHidden = true

        let fileSwitcher = FileSwitcher()
        fileSwitcher.translatesAutoresizingMaskIntoConstraints = false
        fileSwitcher.delegate = self

        let editControls = makeEditControlsContainer()

        let stackView = UIStackView(arrangedSubviews: [pdfView, fileSwitcher, editControls])
        stackView.translatesAutoresizingMaskIntoConstraints = false
        stackView.axis = .vertical
        stackView.spacing = 0
        stackView.distribution = .fill

        self.addSubview(stackView)

        NSLayoutConstraint.activate([
            stackView.topAnchor.constraint(equalTo: self.safeAreaLayoutGuide.topAnchor),
            self.safeAreaLayoutGuide.bottomAnchor.constraint(equalTo: stackView.bottomAnchor),
            fileSwitcher.heightAnchor.constraint(equalToConstant: 120),
            editControls.heightAnchor.constraint(equalToConstant: 120),

            stackView.leadingAnchor.constraint(equalTo: self.safeAreaLayoutGuide.leadingAnchor),
            self.safeAreaLayoutGuide.trailingAnchor.constraint(equalTo: stackView.trailingAnchor)
        ])

        self.pdfView = pdfView
        self.fileSwitcher = fileSwitcher
        updateBottomControlsVisibility()
    }

    private func updateWithOptions(_ options: [String: Any]) {
        documents.removeAll()
        excludedPages.removeAll()
        documentDrawings.removeAll()
        filePaths.removeAll()
        currentDocumentIndex = 0
        pdfView.clearPageIndicators()

        if let arrayOfPaths = options["filePath"] as? [String] {
            filePaths = arrayOfPaths
            for (index, value) in filePaths.enumerated() {
                if let document = RNPDFDocument(id: index, path: value) {
                    documents.append(document)
                }
            }
            self.renderDocuments(for: documents)
        } else {
            print("RNPDFEditor: \"filePath\" value is wrong")
        }

        if let lineColor = options["lineColor"] as? String {
            pdfDrawer.color = UIColor(hexString: lineColor)
        } else {
            print("RNPDFEditor: \"lineColor\" value is wrong")
        }

        if let lineWidth = options["lineWidth"] as? Float {
            pdfDrawer.width = CGFloat(lineWidth)
        } else {
            print("RNPDFEditor: \"lineWidth\" value is wrong")
        }

        if let iconColor = options["selectionIconColor"] as? String {
            selectionIconColor = UIColor(hexString: iconColor)
        } else {
            selectionIconColor = .white
        }

        applySelectionIconColor()
    }

    private func renderDocuments(for documents: [RNPDFDocument]) {
        if documents.isEmpty {
            print("RNPDFEditor: documents is empty")
            return
        }

        fileSwitcher.configure(with: documents, selectedIndex: currentDocumentIndex)
        
        renderDocument(at: 0)
    }

    private func loadDocument(_ document: RNPDFDocument, completion: @escaping (PDFDocument?) -> Void) {
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
                self.pdfView.drawingDelegate = self.pdfDrawer
                self.pdfView.document = convertedDocument
                self.pdfView.disableSelection(in: self.pdfView)
                self.pdfView.onTogglePage = { [weak self] pageIndex in
                    self?.togglePageExclusion(pageIndex: pageIndex)
                }
                
                self.pdfDrawer.pdfView = self.pdfView
                self.pdfDrawer.clear()
                
                // Reset pan/zoom on document load
                self.pdfView.goToFirstPage(nil)
                self.pdfView.scaleFactor = self.pdfView.scaleFactorForSizeToFit
                self.configureScrollView()
                
                self.fileSwitcher.selectFile(at: index)
                self.configurePageIndicators(for: document)
            }
        }
    }

    @objc func save() {
        guard let onSavePDF = self.onSavePDF else {
            print("RNPDFEditor: onSavePDF is nil, can't return value")
            return
        }

        var params: [String : [String]?] = ["url" : nil]
        var resultArray = [String]()

        let today = Date()
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd-HH-mm-ss"

        for item in documents {
            guard let document = documentForSaving(item) else {
                print("RNPDFEditor: unable to load document with id \(item.id)")
                continue
            }

            let excluded = excludedPages[item.id] ?? []

            if item.type == .image {
                guard !excluded.contains(0) else { continue }

                guard let fileNameWithExt = item.incomingPath.components(separatedBy: "/").last,
                      let fileNameRaw = fileNameWithExt.components(separatedBy: ".").first else {
                    print("RNPDFEditor: can't handle URL")
                    continue
                }

                let newPathComponent = fileNameRaw + "_" + formatter.string(from: today) + ".png"
                let fileURL = getDocumentsDirectory().appendingPathComponent(newPathComponent)

                guard let page = document.page(at: 0) else {
                    print("RNPDFEditor: image with path \(item.incomingPath) not writed locally")
                    continue
                }

                let bounds = page.bounds(for: .cropBox)

                let renderer = UIGraphicsImageRenderer(bounds: bounds, format: UIGraphicsImageRendererFormat.default())

                let image = renderer.image { context in
                    context.cgContext.saveGState()
                    context.cgContext.translateBy(x: 0, y: bounds.height)
                    context.cgContext.concatenate(CGAffineTransform.init(scaleX: 1, y: -1))
                    page.draw(with: .mediaBox, to: context.cgContext)
                    context.cgContext.restoreGState()
                }

                if let data = image.pngData() {
                    do {
                        try data.write(to: fileURL)
                        resultArray.append(fileURL.absoluteString)
                    } catch {
                        print("RNPDFEditor: can't create image for saving")
                    }
                }
            } else {
                guard let fileNameWithExt = item.incomingPath.components(separatedBy: "/").last,
                      let fileNameRaw = fileNameWithExt.components(separatedBy: ".").first else {
                    print("RNPDFEditor: can't handle URL")
                    continue
                }

                let newPathComponent = fileNameRaw + "_" + formatter.string(from: today) + ".pdf"
                let fileURL = getDocumentsDirectory().appendingPathComponent(newPathComponent)

                let resultDocument = PDFDocument()
                var resultDocumentIndex = 0

                for pageIndex in 0..<document.pageCount {
                    if excluded.contains(pageIndex) { continue }
                    guard let page = document.page(at: pageIndex) else { continue }

                    let bounds = page.bounds(for: .cropBox)
                    let renderer = UIGraphicsImageRenderer(bounds: bounds, format: UIGraphicsImageRendererFormat.default())

                    let image = renderer.image { context in
                        context.cgContext.saveGState()
                        context.cgContext.translateBy(x: 0, y: bounds.height)
                        context.cgContext.concatenate(CGAffineTransform.init(scaleX: 1, y: -1))
                        page.draw(with: .mediaBox, to: context.cgContext)
                        context.cgContext.restoreGState()
                    }
                    
                    if let newPage = PDFPage(image: image) {
                        resultDocument.insert(newPage, at: resultDocumentIndex)
                        resultDocumentIndex += 1
                    }
                }

                guard resultDocument.pageCount > 0 else { continue }

                resultDocument.write(to: fileURL)
                resultArray.append(fileURL.absoluteString)
            }
        }

        params["url"] = resultArray
        onSavePDF(params as [AnyHashable : Any])
    }

    private func getDocumentsDirectory() -> URL {
        let paths = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
        let documentsDirectory = paths[0]
        return documentsDirectory
    }

    private func configurePageIndicators(for document: RNPDFDocument) {
        let excluded = excludedPages[document.id] ?? []
        pdfView.updatePageIndicators(excluded: excluded, iconColor: selectionIconColor)
    }

    private func togglePageExclusion(pageIndex: Int) {
        guard currentDocumentIndex >= 0 && currentDocumentIndex < documents.count else { return }
        let document = documents[currentDocumentIndex]
        guard pageIndex >= 0 else { return }

        var set = excludedPages[document.id] ?? []
        if set.contains(pageIndex) {
            set.remove(pageIndex)
        } else {
            set.insert(pageIndex)
        }
        excludedPages[document.id] = set
        pdfView.updatePageIndicators(excluded: set, iconColor: selectionIconColor)
    }

    private func documentForSaving(_ document: RNPDFDocument) -> PDFDocument? {
        if let rendered = document.renderedDocument {
            return rendered
        }

        var convertedDocument: PDFDocument?
        let semaphore = DispatchSemaphore(value: 0)
        document.convert { pdfDocument in
            convertedDocument = pdfDocument
            document.renderedDocument = pdfDocument
            semaphore.signal()
        }
        _ = semaphore.wait(timeout: .now() + 5)
        return convertedDocument
    }
}

extension ContainerView {
    
    func undo() {
        pdfDrawer.undo()
    }
    
    func clear() {
        pdfDrawer.clear()
    }
}

extension ContainerView: FileSwitcherDelegate {

    func didSelectFile(at index: Int) {
        renderDocument(at: index)
    }
}

// MARK: - Helpers
private extension ContainerView {
    func scrollViewIfPresent() -> UIScrollView? {
        return pdfView.subviews.compactMap { $0 as? UIScrollView }.first
    }

    func configureScrollView() {
        guard let scrollView = scrollViewIfPresent() else { return }
        scrollView.isDirectionalLockEnabled = false // allow free pan without axis locking
    }
}
