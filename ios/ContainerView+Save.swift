import Foundation
import PDFKit
import UIKit

extension ContainerView {
  func saveImpl() {
    guard let onSavePDF = self.onSavePDF else {
      print("RNPDFEditor: onSavePDF is nil, can't return value")
      return
    }

    // Snapshot the inputs while still on the main queue so we don't race the
    // mutable container state during file I/O on the background queue.
    let documentsSnapshot = documents
    let excludedSnapshot = excludedPages

    let saveQueue = DispatchQueue(
      label: "com.ornament.pdfeditor.save",
      qos: .userInitiated
    )

    saveQueue.async { [weak self] in
      guard let self = self else { return }

      let formatter = DateFormatter()
      formatter.dateFormat = "yyyy-MM-dd-HH-mm-ss"
      let dateString = formatter.string(from: Date())

      var resultArray = [String]()

      for item in documentsSnapshot {
        guard let document = self.documentForSaving(item) else {
          print("RNPDFEditor: unable to load document with id \(item.id)")
          continue
        }

        let excluded = excludedSnapshot[item.id] ?? []

        if let path = self.writeDocument(
          item: item,
          document: document,
          excluded: excluded,
          dateString: dateString
        ) {
          resultArray.append(path)
        }
      }

      var params: [AnyHashable: Any] = ["url": NSNull()]
      if !resultArray.isEmpty {
        params["url"] = resultArray
      }

      DispatchQueue.main.async {
        onSavePDF(params)
      }
    }
  }

  func getDocumentsDirectory() -> URL {
    let paths = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
    return paths[0]
  }

  func documentForSaving(_ document: RNPDFDocument) -> PDFDocument? {
    if let rendered = document.renderedDocument {
      return rendered
    }
    // convert() invokes its completion synchronously, so we can collect the
    // result without a semaphore. If a future change makes it async, callers
    // should be migrated to the async resolver.
    var loaded: PDFDocument?
    document.convert { converted in
      loaded = converted
    }
    if let loaded = loaded {
      document.renderedDocument = loaded
    }
    return loaded
  }

  private func writeDocument(
    item: RNPDFDocument,
    document: PDFDocument,
    excluded: Set<Int>,
    dateString: String
  ) -> String? {
    guard
      let fileNameWithExt = item.incomingPath.components(separatedBy: "/").last,
      let fileNameRaw = fileNameWithExt.components(separatedBy: ".").first
    else {
      print("RNPDFEditor: can't handle URL")
      return nil
    }

    if item.type == .image {
      guard !excluded.contains(0) else { return nil }

      let newPathComponent = fileNameRaw + "_" + dateString + ".jpg"
      let fileURL = getDocumentsDirectory().appendingPathComponent(newPathComponent)

      guard let page = document.page(at: 0) else {
        print("RNPDFEditor: image with path \(item.incomingPath) not writed locally")
        return nil
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

      guard let data = image.jpegData(compressionQuality: 0.85) else {
        print("RNPDFEditor: can't create image for saving")
        return nil
      }

      do {
        try data.write(to: fileURL)
        return fileURL.absoluteString
      } catch {
        print("RNPDFEditor: can't create image for saving")
        return nil
      }
    }

    let newPathComponent = fileNameRaw + "_" + dateString + ".pdf"
    let fileURL = getDocumentsDirectory().appendingPathComponent(newPathComponent)

    // Snapshot via dataRepresentation so we can drop excluded pages without
    // mutating the on-screen PDFDocument. Drawing annotations are preserved
    // because PDFKit serializes them into the data blob.
    guard
      let snapshotData = document.dataRepresentation(),
      let workingCopy = PDFDocument(data: snapshotData)
    else {
      print("RNPDFEditor: can't snapshot PDF for id \(item.id)")
      return nil
    }

    // Remove from the back so surviving indices stay valid.
    for pageIndex in excluded.sorted(by: >) {
      guard pageIndex >= 0 && pageIndex < workingCopy.pageCount else { continue }
      workingCopy.removePage(at: pageIndex)
    }

    // Entire document excluded — mirror the image-branch contract by skipping.
    guard workingCopy.pageCount > 0 else { return nil }

    if workingCopy.write(to: fileURL) {
      return fileURL.absoluteString
    }
    print("RNPDFEditor: failed write PDF for id \(item.id)")
    return nil
  }
}
