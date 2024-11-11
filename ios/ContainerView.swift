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
    private var filePaths = [String]()
    private var documents = [RNPDFDocument]()

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
        pdfView.isHidden = true

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
      
      print(self.pdfView.minScaleFactor)
    }

    private func updateWithOptions(_ options: [String: Any]) {

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

        if let isHidden = options["isToolBarHidden"] as? Bool {
            toolBarView.isHidden = isHidden
        } else {
            print("RNPDFEditor: \"isToolBarHidden\" value is wrong")
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

    private func renderDocuments(for documents: [RNPDFDocument]) {
        if documents.isEmpty {
            print("RNPDFEditor: documents is empty")
            return
        }

        let document = PDFDocument()
        for item in documents {
            item.convert { convertedDocument in
                if let convertedDocument = convertedDocument {
                    document.addPages(from: convertedDocument)
                }
            }
        }

        self.pdfView.isHidden = false
        self.pdfView.drawingDelegate = pdfDrawer
        self.pdfView.document = document
        self.pdfView.disableSelection(in: self.pdfView)

        self.pdfDrawer.pdfView = pdfView
    }

    private func save() {
        guard let onSavePDF = self.onSavePDF else {
            print("RNPDFEditor: onSavePDF is nil, can't return value")
            return
        }

        var params: [String : [String]?] = ["url" : nil]
        var resultArray = [String]()

        var nextPageIndex = 0

        let today = Date()
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd-HH-mm-ss"

        for item in documents {
            let pages = item.pageCount
            if item.type == .image {

                if let fileNameWithExt = item.incomingPath.components(separatedBy: "/").last,
                   let fileNameRaw = fileNameWithExt.components(separatedBy: ".").first {

                    let newPathComponent = fileNameRaw + "_" + formatter.string(from: today) + ".png"
                    let fileURL = getDocumentsDirectory().appendingPathComponent(newPathComponent)

                    guard let document = pdfView.document,
                          let page = document.page(at: nextPageIndex) else {
                        print("RNPDFEditor: image with path \(item.incomingPath) not writed locally")
                        break
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

                    if let data = image.pngData() {
                        do {
                            try data.write(to: fileURL)
                        } catch {
                            print("RNPDFEditor: can't create image for saving")
                            break
                        }
                    }
                    resultArray.append(fileURL.absoluteString)

                } else {
                    print("RNPDFEditor: can't handle URL")
                    break
                }

            } else {
                if let fileNameWithExt = item.incomingPath.components(separatedBy: "/").last,
                   let fileNameRaw = fileNameWithExt.components(separatedBy: ".").first {
                    let newPathComponent = fileNameRaw + "_" + formatter.string(from: today) + ".pdf"
                    let fileURL = getDocumentsDirectory().appendingPathComponent(newPathComponent)

                    guard let document = pdfView.document else {
                        print("RNPDFEditor: PDF not writed locally")
                        break
                    }

                    let resultDocument = PDFDocument()
                    var resultDocumentIndex = 0
                    for index in nextPageIndex..<(nextPageIndex + item.pageCount) {
                        guard let page = document.page(at: index) else {
                            print("RNPDFEditor: PDF not writed locally")
                            break
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
                        
                        if let newPage = PDFPage(image: image) {
                            resultDocument.insert(newPage, at: resultDocumentIndex)
                            resultDocumentIndex += 1
                        }
                    }
                    resultDocument.write(to: fileURL)
                    resultArray.append(fileURL.absoluteString)
                } else {
                    print("RNPDFEditor: can't handle URL")
                }
            }
            nextPageIndex += pages
        }

        params["url"] = resultArray
        onSavePDF(params as [AnyHashable : Any])
    }

    private func getDocumentsDirectory() -> URL {
        let paths = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
        let documentsDirectory = paths[0]
        return documentsDirectory
    }
}

extension ContainerView: ToolBarViewDelegate {

    func undoButtonTapped() {
        pdfDrawer.undo()
    }

    func clearButtonTapped() {
        pdfDrawer.clear()
    }

    func saveButtonTapped() {
        self.save()
    }
}
