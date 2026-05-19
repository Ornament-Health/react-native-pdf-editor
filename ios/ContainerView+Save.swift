import Foundation
import PDFKit
import UIKit

extension ContainerView {
  func saveImpl() {
    guard let onSavePDF = self.onSavePDF else {
      print("RNPDFEditor: onSavePDF is nil, can't return value")
      return
    }

    var params: [AnyHashable: Any] = ["url": NSNull()]
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
          let fileNameRaw = fileNameWithExt.components(separatedBy: ".").first
        else {
          print("RNPDFEditor: can't handle URL")
          continue
        }

        let newPathComponent = fileNameRaw + "_" + formatter.string(from: today) + ".jpg"
        let fileURL = getDocumentsDirectory().appendingPathComponent(newPathComponent)

        guard let page = document.page(at: 0) else {
          print("RNPDFEditor: image with path \(item.incomingPath) not writed locally")
          continue
        }

        let bounds = page.bounds(for: .cropBox)

        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1.0
        let renderer = UIGraphicsImageRenderer(bounds: bounds, format: format)

        let image = renderer.image { context in
          context.cgContext.saveGState()
          context.cgContext.translateBy(x: 0, y: bounds.height)
          context.cgContext.concatenate(CGAffineTransform(scaleX: 1, y: -1))
          page.draw(with: .mediaBox, to: context.cgContext)
          context.cgContext.restoreGState()
        }

        if let data = image.jpegData(compressionQuality: 0.85) {
          do {
            try data.write(to: fileURL)
            resultArray.append(fileURL.absoluteString)
          } catch {
            print("RNPDFEditor: can't create image for saving")
          }
        }
      } else {
        guard let fileNameWithExt = item.incomingPath.components(separatedBy: "/").last,
          let fileNameRaw = fileNameWithExt.components(separatedBy: ".").first
        else {
          print("RNPDFEditor: can't handle URL")
          continue
        }

        let newPathComponent = fileNameRaw + "_" + formatter.string(from: today) + ".pdf"
        let fileURL = getDocumentsDirectory().appendingPathComponent(newPathComponent)

        if document.write(to: fileURL) {
          resultArray.append(fileURL.absoluteString)
        } else {
          print("RNPDFEditor: failed write PDF for id \(item.id)")
        }
      }
    }

    if !resultArray.isEmpty {
      params["url"] = resultArray
    }
    onSavePDF(params)
  }

  func getDocumentsDirectory() -> URL {
    let paths = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
    return paths[0]
  }

  func documentForSaving(_ document: RNPDFDocument) -> PDFDocument? {
    if let rendered = document.renderedDocument {
      return rendered
    }
    let sem = DispatchSemaphore(value: 0)
    var loaded: PDFDocument?
    document.convert { converted in
      loaded = converted
      sem.signal()
    }
    _ = sem.wait(timeout: .now() + 5)
    return loaded
  }
}
