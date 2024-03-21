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
      if let path = resizeImageAndSave(image, newWidth: newWidth, grayscale: grayscale) {
        completion([path])
      }
    case .pdf:
      convertToImagesAndSave(pdfURL: documentURL, newWidth: newWidth, grayscale: grayscale) { result in
        let resultNonOptional = result?.compactMap { $0 }
        completion(resultNonOptional)
      }
    }
  }
}

// MARK: Helpers Methods

extension Document {

  private func convertToImagesAndSave(pdfURL: URL, newWidth: CGFloat, grayscale: Bool, completion: @escaping (([String?]?) -> Void)) {

    guard let pdfDocumentCG = CGPDFDocument(pdfURL as CFURL),
          let pdfDocument = PDFDocument(url: pdfURL),
          let fileNameWithExt = incomingPath.components(separatedBy: "/").last,
          let fileNameRaw = fileNameWithExt.components(separatedBy: ".").first else {
      completion(nil)
      return
    }
    let documentsDirectoryURL = getDocumentsDirectory()
    var outcomingPath: [String?] = .init(repeating: nil, count: pdfDocumentCG.numberOfPages)

    DispatchQueue.concurrentPerform(iterations: pdfDocumentCG.numberOfPages) { index in
      autoreleasepool {

        if let pdfPageCG = pdfDocumentCG.page(at: index + 1),
           let pdfPage = pdfDocument.page(at: index){

          var pdfPageSize = pdfPageCG.getBoxRect(CGPDFBox.mediaBox)

          let scaleFactor = newWidth / pdfPageSize.size.width
          let scaledImageSize = CGSize(
            width: newWidth,
            height: pdfPageSize.size.height * scaleFactor
          )

          guard let context = createCGContext(for: scaledImageSize, grayscale: grayscale) else {
            completion(nil)
            return
          }
          context.beginPage(mediaBox: &pdfPageSize)
          context.interpolationQuality = .high
          context.setFillColor(UIColor.white.cgColor)
          context.fill(CGRect(x: 0, y: 0, width: scaledImageSize.width, height: scaledImageSize.height))
          context.scaleBy(x: scaleFactor, y: scaleFactor)
          context.drawPDFPage(pdfPageCG)
          for annotation in pdfPage.annotations {
            annotation.draw(with: .mediaBox, in: context)
          }
          context.endPage()
          guard let cgImage = context.makeImage() else {
            completion(nil)
            return
          }

          let newPathComponent = fileNameRaw + "_" + "\(index + 1)_" + "resized" + ".jpeg"
          let fileURL = documentsDirectoryURL.appendingPathComponent(newPathComponent)

          if let imageDestination = CGImageDestinationCreateWithURL(fileURL as CFURL, "public.jpeg" as CFString, 1, nil) {
            CGImageDestinationAddImage(imageDestination, cgImage, nil)
            CGImageDestinationFinalize(imageDestination)
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

  private func createCGContext(for size: CGSize, grayscale: Bool) -> CGContext? {
    let colorSpace = grayscale ?
    CGColorSpaceCreateDeviceGray() :
    CGColorSpaceCreateDeviceRGB()
    let bitmapInfo = grayscale ?
    CGImageAlphaInfo.none.rawValue :
    CGImageAlphaInfo.premultipliedLast.rawValue

    return CGContext(data: nil,
                     width: Int(size.width),
                     height: Int(size.height),
                     bitsPerComponent: 8,
                     bytesPerRow: 0,
                     space: colorSpace,
                     bitmapInfo: bitmapInfo)

  }

  private func createTransformMatrixForImage(_ image: UIImage, size: CGSize) -> CGAffineTransform {
    var transform: CGAffineTransform = CGAffineTransform.identity

    switch image.imageOrientation {
    case .down, .downMirrored:
      transform = transform.translatedBy(x: size.width, y: size.height)
      transform = transform.rotated(by: CGFloat.pi)
    case .left, .leftMirrored:
      transform = transform.translatedBy(x: size.width, y: 0)
      transform = transform.rotated(by: CGFloat.pi / 2.0)
    case .right, .rightMirrored:
      transform = transform.translatedBy(x: 0, y: size.height)
      transform = transform.rotated(by: CGFloat.pi / -2.0)
    case .up, .upMirrored:
      break
    @unknown default:
      break
    }

    // Flip image one more time if needed to, this is to prevent flipped image
    switch image.imageOrientation {
    case .upMirrored, .downMirrored:
      transform = transform.translatedBy(x: size.width, y: 0)
      transform = transform.scaledBy(x: -1, y: 1)
    case .leftMirrored, .rightMirrored:
      transform = transform.translatedBy(x: size.height, y: 0)
      transform = transform.scaledBy(x: -1, y: 1)
    case .up, .down, .left, .right:
      break
    @unknown default:
      break
    }

    return transform
  }

  func resizeImageAndSave(_ image: UIImage, newWidth: CGFloat, grayscale: Bool) -> String? {

    guard let cgImage = image.cgImage else {
      return nil
    }

    let scaleFactor = newWidth / image.size.width
    let scaledImageSize = CGSize(
      width: newWidth,
      height: image.size.height * scaleFactor
    )

    guard let context = createCGContext(for: scaledImageSize, grayscale: grayscale) else {
      return nil
    }

    let transform = createTransformMatrixForImage(image, size: scaledImageSize)
    context.concatenate(transform)

    switch image.imageOrientation {
    case .left, .leftMirrored, .right, .rightMirrored:
      context.draw(cgImage, in: CGRect(x: 0, y: 0, width: scaledImageSize.height, height: scaledImageSize.width))
    default:
      context.draw(cgImage, in: CGRect(x: 0, y: 0, width: scaledImageSize.width, height: scaledImageSize.height))
      break
    }

    guard let cgImage = context.makeImage() else { return nil }

    if let fileNameWithExt = incomingPath.components(separatedBy: "/").last,
       let fileNameRaw = fileNameWithExt.components(separatedBy: ".").first {

      let newPathComponent = fileNameRaw + "_" + "resized" + ".jpeg"
      let fileURL = getDocumentsDirectory().appendingPathComponent(newPathComponent)

      if let imageDestination = CGImageDestinationCreateWithURL(fileURL as CFURL, "public.jpeg" as CFString, 1, nil) {
        CGImageDestinationAddImage(imageDestination, cgImage, nil)
        CGImageDestinationFinalize(imageDestination)
        return fileURL.absoluteString
      }
    }
    return nil
  }

  private func getDocumentsDirectory() -> URL {
    let paths = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
    let documentsDirectory = paths[0]
    return documentsDirectory
  }
}
