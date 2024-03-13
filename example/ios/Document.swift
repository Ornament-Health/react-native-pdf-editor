import CoreImage
import Foundation
import PDFKit

class Document {

  enum DocumentType {
    case pdf
    case image
  }

  private var id: Int
  private var type: DocumentType
  private var documentURL: URL?
  private var pdfDocument: PDFDocument?
  private var grayscale: Bool
  private var newWidth: CGFloat
  private var incomingPath: String

  init?(id: Int, path: String, grayscale: Bool, newWidth: CGFloat){
    self.id = id
    self.incomingPath = path
    self.documentURL = URL(string: path)
    let documentExtension = path.components(separatedBy: ".").last
    switch documentExtension {
    case "pdf", "PDF":
      self.type = .pdf
      if let url = self.documentURL, let pdfDocument = PDFDocument(url: url) {
        self.pdfDocument = pdfDocument
      }
    case "jpg", "JPG", "jpeg", "JPEG", "heic","HEIC", "png", "PNG":
      self.type = .image
    default:
      return nil
    }
    self.grayscale = grayscale
    self.newWidth = newWidth
  }

  func process(completion: @escaping (([String]?) -> Void)) {
    guard let documentURL = documentURL else { return }
    switch type {
    case .image:
      guard let image = loadImage(fileURL: documentURL) else { return }

      var resultImage = scaleImagePreservingAspectRatio(image, newWidth: newWidth)
      if grayscale, let grayscaledImage = grayscale(resultImage) {
        resultImage = grayscaledImage
      }

      if let fileNameWithExt = incomingPath.components(separatedBy: "/").last,
         let fileNameRaw = fileNameWithExt.components(separatedBy: ".").first {
        let newPathComponent = fileNameRaw + "_" + "resized" + ".png"
        let fileURL = getDocumentsDirectory().appendingPathComponent(newPathComponent)

        if let pngData = resultImage.pngData(), let _ = try? pngData.write(to: fileURL) {
          completion([fileURL.absoluteString])
        }
      }
    case .pdf:
      convertToImagesAndSave(pdfURL: documentURL, newWidth: newWidth) { result in
        let resultNonOptional = result?.compactMap { $0 }
        completion(resultNonOptional)
      }
    }
  }
}

// MARK: Helpers Methods

extension Document {

  private func convertToImagesAndSave(pdfURL: URL, newWidth: CGFloat, completion: @escaping (([String?]?) -> Void)) {
    guard let pdfDocument = PDFDocument(url: pdfURL),
          let fileNameWithExt = incomingPath.components(separatedBy: "/").last,
          let fileNameRaw = fileNameWithExt.components(separatedBy: ".").first else {
      completion(nil)
      return
    }
    let documentsDirectoryURL = getDocumentsDirectory()
    var outcomingPath: [String?] = .init(repeating: nil, count: pdfDocument.pageCount)

    let format = UIGraphicsImageRendererFormat()
    format.scale = 1
    format.preferredRange = .automatic

    DispatchQueue.concurrentPerform(iterations: pdfDocument.pageCount) { [self] index in
      autoreleasepool {
        if let pdfPage = pdfDocument.page(at: index) {
          let pdfPageSize = pdfPage.bounds(for: .mediaBox)

          let scaleFactor = newWidth / pdfPageSize.size.width
          let scaledImageSize = CGSize(
            width: newWidth,
            height: pdfPageSize.size.height * scaleFactor
          )

          let renderer = UIGraphicsImageRenderer(size: scaledImageSize, format: format)

          var image = renderer.image { ctx in
            UIColor.white.set()
            ctx.fill(CGRect(x: 0, y: 0, width: scaledImageSize.width, height: scaledImageSize.height))
            ctx.cgContext.translateBy(x: 0.0, y: scaledImageSize.height)
            ctx.cgContext.scaleBy(x: scaleFactor, y: -scaleFactor)
            pdfPage.draw(with: .mediaBox, to: ctx.cgContext)
          }

          if grayscale, let grayscaledImage = grayscale(image) {
            image = grayscaledImage
          }

          let newPathComponent = fileNameRaw + "_" + "\(index + 1)_" + "resized" + ".png"
          let fileURL = documentsDirectoryURL.appendingPathComponent(newPathComponent)

          if let pngData = image.pngData(), let _ = try? pngData.write(to: fileURL) {
            outcomingPath[index] = fileURL.absoluteString
          }
        }
      }
    }

    completion(outcomingPath)
  }

  private func loadImage(fileURL: URL) -> UIImage? {
    do {
      let imageData = try Data(contentsOf: fileURL)
      return UIImage(data: imageData)
    } catch {
      print("RNDocumentsHandler: Error loading image : \(error)")
    }
    return nil
  }

  private func grayscale(_ image: UIImage) -> UIImage? {
    guard let filter = CIFilter(name: "CIPhotoEffectNoir") else { return nil }

    filter.setValue(CIImage(image: image), forKey: kCIInputImageKey)
    
    if let output = filter.outputImage {
      return UIImage(ciImage: output)
    }
    return nil
  }

  private func scaleImagePreservingAspectRatio(_ image: UIImage, newWidth: CGFloat) -> UIImage {
    let scaleFactor = newWidth / image.size.width
    let scaledImageSize = CGSize(
      width: newWidth,
      height: image.size.height * scaleFactor
    )

    let format = UIGraphicsImageRendererFormat()
    format.scale = 1
    format.preferredRange = .automatic

    let renderer = UIGraphicsImageRenderer(size: scaledImageSize, format: format)

    let scaledImage = renderer.image { _ in
      image.draw(in: CGRect(
        origin: .zero,
        size: scaledImageSize
      ))
    }
    return scaledImage
  }

  private func getDocumentsDirectory() -> URL {
    let paths = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
    let documentsDirectory = paths[0]
    return documentsDirectory
  }
}
