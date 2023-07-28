//
//  ContainerView.swift
//  react-native-pdf-editor
// 

import Foundation
import PDFKit
import UIKit

@objc(ContainerView)
class ContainerView: UIView {

    @objc var pdfView: NonSelectablePDFView!

    @objc var options: [String: Any] = [:] {
        didSet {
            updateWithOptions(options)
        }
    }

    @objc var onSavePDF: RCTDirectEventBlock?
    @objc var onError: RCTDirectEventBlock?

    private var toolBarView: ToolBarView!
    private let pdfDrawer = PDFDrawer()
    private var fileName = ""

    override init(frame: CGRect) {
        super.init(frame: frame)
        setupView()
    }

    required init?(coder: NSCoder) {
        fatalError("RNPDFEditor: init(coder:) has not been implemented")
    }

    private func setupView() {
        let toolBarView = ToolBarView()
        toolBarView.translatesAutoresizingMaskIntoConstraints = false
        toolBarView.delegate = self
        toolBarView.isHidden = true

        let pdfView = NonSelectablePDFView()
        pdfView.backgroundColor = .lightGray
        pdfView.translatesAutoresizingMaskIntoConstraints = false
        pdfView.displayMode = .singlePageContinuous
        pdfView.displayDirection = .vertical
        pdfView.usePageViewController(false)
        pdfView.pageBreakMargins = UIEdgeInsets(top: 10, left: 10, bottom: 10, right: 10)
        pdfView.autoScales = true

        let stackView = UIStackView(arrangedSubviews: [toolBarView, pdfView])
        stackView.translatesAutoresizingMaskIntoConstraints = false
        stackView.axis = .vertical
        stackView.spacing = 0
        stackView.distribution = .fill

        self.addSubview(stackView)

        NSLayoutConstraint.activate([
            stackView.topAnchor.constraint(equalTo: self.safeAreaLayoutGuide.topAnchor),
            self.safeAreaLayoutGuide.bottomAnchor.constraint(equalTo: stackView.bottomAnchor),
            toolBarView.heightAnchor.constraint(equalToConstant: 40),

            stackView.leadingAnchor.constraint(equalTo: self.safeAreaLayoutGuide.leadingAnchor),
            self.safeAreaLayoutGuide.trailingAnchor.constraint(equalTo: stackView.trailingAnchor)
        ])

        self.pdfView = pdfView
        self.toolBarView = toolBarView
    }

    private func updateWithOptions(_ options: [String: Any]) {

        if let fileName = options["fileName"] as? String {
            self.fileName = fileName
            loadPDF(for: fileName)
        } else {
            print("RNPDFEditor: \"fileName\" value is wrong")
        }

        if let isHidden = options["isToolBarHidden"] as? Bool {
            toolBarView.isHidden = isHidden
        } else {
            print("RNPDFEditor: \"isToolBarHidden\" value is wrong")
        }

        if let startWithEdit = options["startWithEdit"] as? Bool {
            if (startWithEdit) {
                addCustomGestures()
            }
        } else {
            print("RNPDFEditor: \"startWithEdit\" value is wrong")
        }

        if let pdfViewBackgroundColor = options["viewBackgroundColor"] as? String {
            pdfView.backgroundColor  = UIColor(hexString: pdfViewBackgroundColor)
        } else {
            print("RNPDFEditor: \"pdfViewBackgroundColor\" value is wrong")
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

    }

    private func addCustomGestures() {
        removeCustomGestures()
        let pdfDrawingGestureRecognizer = DrawingGestureRecognizer()
        pdfView.addGestureRecognizer(pdfDrawingGestureRecognizer)
        pdfDrawingGestureRecognizer.drawingDelegate = pdfDrawer
        pdfDrawer.pdfView = pdfView
    }

    private func removeCustomGestures() {
        if let gestureRecognizers = pdfView.gestureRecognizers, !gestureRecognizers.isEmpty {
            gestureRecognizers.forEach { item in
                if item is DrawingGestureRecognizer {
                    pdfView.removeGestureRecognizer(item)
                }
            }
        }
    }

    private func loadPDF(for pathString: String) {
        guard let url = URL(string: pathString) else {
            print("RNPDFEditor: can't create URL from string")
            return
        }
        if let document = PDFDocument(url: url) {
            pdfView.document = document
        } else {
            print("RNPDFEditor: can't create PDF document from URL")
        }
    }

    private func savePDF() {
        guard let onSavePDF = self.onSavePDF else {
            print("RNPDFEditor: onSavePDF is nil, can't return value")
            return
        }

        var params: [String : String?] = ["url" : nil]

        let today = Date()
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd-HH-mm-ss"

        if let fileNameWithExt = fileName.components(separatedBy: "/").last,
           let fileNameRaw = fileNameWithExt.components(separatedBy: ".").first {
            let newPathComponent = fileNameRaw + "_" + formatter.string(from: today) + ".pdf"
            let fileURL = getDocumentsDirectory().appendingPathComponent(newPathComponent)

            guard let document = pdfView.document,
                  let page = document.page(at: 0) else {
                print("RNPDFEditor: PDF not writed locally")
                onSavePDF(params as [AnyHashable : Any])
                return
            }
            let bounds = page.bounds(for: .cropBox)

            let renderer = UIGraphicsImageRenderer(bounds: bounds, format: UIGraphicsImageRendererFormat.default())

            let image = renderer.image { (context) in
                context.cgContext.saveGState()
                context.cgContext.translateBy(x: 0, y: bounds.height)
                context.cgContext.concatenate(CGAffineTransform.init(scaleX: 1, y: -1))
                page.draw(with: .mediaBox, to: context.cgContext)
                context.cgContext.restoreGState()
            }

            let newPage = PDFPage(image: image)!

            for annotation in page.annotations {
                newPage.addAnnotation(annotation)
            }

            document.insert(newPage, at: 0)
            document.removePage(at: 1)

            document.write(to: fileURL)

            params["url"] = fileURL.absoluteString
            onSavePDF(params as [AnyHashable : Any])
        } else {
            print("RNPDFEditor: can't handle URL")
            onSavePDF(params as [AnyHashable : Any])
        }
    }

    private func getDocumentsDirectory() -> URL {
        let paths = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
        let documentsDirectory = paths[0]
        return documentsDirectory
    }
}

extension ContainerView: ToolBarViewDelegate {

    @objc func moveButtonTapped() {
        removeCustomGestures()
    }

    func lineButtonTapped() {
        pdfDrawer.drawingTool = .pen
        addCustomGestures()
    }

    func undoButtonTapped() {
        removeCustomGestures()
        pdfDrawer.undo()
    }

    func saveButtonTapped() {
        removeCustomGestures()
        self.savePDF()
    }
}
