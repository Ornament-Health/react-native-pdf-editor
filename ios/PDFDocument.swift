//
//  RNPDFDocument.swift
//  react-native-pdf-editor
//

import Foundation
import PDFKit

class RNPDFDocument {

    enum DocumentType {
        case pdf
        case image
    }

    var id: Int
    var type: DocumentType
    var incomingPath: String
    var pageCount: Int = 0
    private var documentURL: URL?

    init?(id: Int, path: String) {
        self.id = id
        self.incomingPath = path
        self.documentURL = URL(string: path)
        let documentExtension = path.components(separatedBy: ".").last
        switch documentExtension {
        case "pdf", "PDF":
            self.type = .pdf
        case "jpg", "JPG", "jpeg", "JPEG", "heic","HEIC", "png", "PNG":
            self.type = .image
        default:
            return nil
        }
    }

    func convert(completion: @escaping ((PDFDocument?) -> Void)) {
        guard let documentURL = documentURL else {
            print("RNPDFEditor: can't create URL from string")
            return
        }
        switch type {
        case .image:
            if let image = loadImage(fileURL: documentURL) {
                let document = PDFDocument()
                let pdfPage = PDFPage(image: image)
                document.insert(pdfPage!, at: 0)
                self.pageCount = document.pageCount
                completion(document)
            } else {
                print("RNPDFEditor: can't create Image from URL")
                return
            }
        case .pdf:
            if let document = PDFDocument(url: documentURL) {
                self.pageCount = document.pageCount
                completion(document)
            } else {
                print("RNPDFEditor: can't create PDF from URL")
                return
            }
        }
    }
}

// MARK: Helpers Methods

extension RNPDFDocument {

    private func loadImage(fileURL: URL) -> UIImage? {
        do {
            let imageData = try Data(contentsOf: fileURL)
            return UIImage(data: imageData)
        } catch {
            print("RNPDFEditor: Error loading image : \(error)")
        }
        return nil
    }

}
