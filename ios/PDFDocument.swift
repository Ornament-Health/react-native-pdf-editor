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

  func generateThumbnail() -> UIImage? {
    guard let documentURL = documentURL else {
      print("RNPDFEditor: can't create URL from string for thumbnail")
      return nil
    }

    switch type {
    case .image:
      return loadImage(fileURL: documentURL)?.resized(to: CGSize(width: 100, height: 100))
    case .pdf:
      guard let document = PDFDocument(url: documentURL),
        let firstPage = document.page(at: 0)
      else {
        return nil
      }

      let bounds = firstPage.bounds(for: .cropBox)
      let thumbnailSize = CGSize(width: 80, height: 80 * bounds.height / bounds.width)

      let renderer = UIGraphicsImageRenderer(size: thumbnailSize)
      return renderer.image { context in
        UIColor.white.set()
        context.fill(CGRect(origin: .zero, size: thumbnailSize))

        context.cgContext.saveGState()
        let transform = CGAffineTransform(
          scaleX: thumbnailSize.width / bounds.width,
          y: thumbnailSize.height / bounds.height)
        context.cgContext.concatenate(transform)
        firstPage.draw(with: .cropBox, to: context.cgContext)
        context.cgContext.restoreGState()
      }
    }
  }
}

// MARK: Helpers Methods

extension RNPDFDocument {

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

  func resized(to size: CGSize) -> UIImage? {
    UIGraphicsBeginImageContextWithOptions(size, false, 0.0)
    defer { UIGraphicsEndImageContext() }
    draw(in: CGRect(origin: .zero, size: size))
    return UIGraphicsGetImageFromCurrentImageContext()
  }
}
