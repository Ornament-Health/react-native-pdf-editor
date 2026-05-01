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
  var renderedDocument: PDFDocument?
  private var documentURL: URL?

  init?(id: Int, path: String) {
    self.id = id
    self.incomingPath = path
    self.documentURL = URL(string: path)
    let documentExtension = path.components(separatedBy: ".").last
    switch documentExtension {
    case "pdf", "PDF":
      self.type = .pdf
    case "jpg", "JPG", "jpeg", "JPEG", "heic", "HEIC", "png", "PNG":
      self.type = .image
    default:
      return nil
    }

    updatePageCountIfNeeded()
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

  func generateThumbnail(maxSize: CGSize = PreviewPanelMetrics.thumbnailMaxSize) -> UIImage? {
    guard let documentURL = documentURL else {
      print("RNPDFEditor: can't create URL from string for thumbnail")
      return nil
    }

    updatePageCountIfNeeded()

    switch type {
    case .image:
      return loadImage(fileURL: documentURL)?.resizedToFit(within: maxSize)
    case .pdf:
      guard let document = PDFDocument(url: documentURL),
        let firstPage = document.page(at: 0)
      else {
        return nil
      }

      let bounds = firstPage.bounds(for: .cropBox)
      let safeWidth = max(bounds.width, 1)
      let safeHeight = max(bounds.height, 1)
      let scale = min(maxSize.width / safeWidth, maxSize.height / safeHeight)
      let thumbnailSize = CGSize(
        width: max(1, safeWidth * scale),
        height: max(1, safeHeight * scale)
      )

      return firstPage.thumbnail(of: thumbnailSize, for: .cropBox)
    }
  }
}

// MARK: Helpers Methods

extension RNPDFDocument {

  private func updatePageCountIfNeeded() {
    guard pageCount == 0 else { return }
    guard let documentURL = documentURL else { return }

    switch type {
    case .image:
      pageCount = 1
    case .pdf:
      pageCount = PDFDocument(url: documentURL)?.pageCount ?? 0
    }
  }

  private func loadImage(fileURL: URL) -> UIImage? {
    do {
      let imageData = try Data(contentsOf: fileURL)
      let image = UIImage(data: imageData)
      return image?.normalizedOrientation()
    } catch {
      print("RNPDFEditor: Error loading image : \(error)")
    }
    return nil
  }

}

extension UIImage {
  func normalizedOrientation() -> UIImage {
    if imageOrientation == .up {
      return self
    }
    UIGraphicsBeginImageContextWithOptions(size, false, scale)
    defer { UIGraphicsEndImageContext() }
    draw(in: CGRect(origin: .zero, size: size))
    return UIGraphicsGetImageFromCurrentImageContext() ?? self
  }

  func resizedToFit(within maxSize: CGSize) -> UIImage? {
    let safeWidth = max(size.width, 1)
    let safeHeight = max(size.height, 1)
    let scale = min(maxSize.width / safeWidth, maxSize.height / safeHeight)
    let targetSize = CGSize(
      width: max(1, safeWidth * scale),
      height: max(1, safeHeight * scale)
    )

    UIGraphicsBeginImageContextWithOptions(targetSize, false, 0.0)
    defer { UIGraphicsEndImageContext() }
    draw(in: CGRect(origin: .zero, size: targetSize))
    return UIGraphicsGetImageFromCurrentImageContext()
  }
}
