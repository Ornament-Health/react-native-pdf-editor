import Foundation
import PDFKit
import UIKit

// A file name is capped at 255 bytes. The saved name starts with the incoming
// one, so it is cut to fit. The app derives page image names from the returned
// URL, where a non-ASCII byte takes three, hence the margin.
private let maxEncodedBaseNameLength = 150

// Why a document produced no file. The failures go to JS with the save result:
// native prints never reach crash reporting.
private enum SaveFailureStep: String {
  case load
  case render
  case serialize
  case parse
  case write
}

extension ContainerView {
  func saveImpl() {
    guard let onSavePDF = self.onSavePDF else {
      print("RNPDFEditor: onSavePDF is nil, can't return value")
      return
    }

    var loadFailures = [[String: Any]]()

    // Resolve documents on the main queue. documentForSaving reads/writes
    // RNPDFDocument.renderedDocument, which renderDocument also touches on the
    // main thread — doing it here keeps that state single-threaded. Only the
    // file I/O below runs on the background queue.
    let excludedSnapshot = excludedPages
    let resolved: [(item: RNPDFDocument, document: PDFDocument, excluded: Set<Int>)] =
      documents.compactMap { item in
        guard let document = self.documentForSaving(item) else {
          print("RNPDFEditor: unable to load document with id \(item.id)")
          loadFailures.append(Self.failure(item, .load))
          return nil
        }
        return (item, document, excludedSnapshot[item.id] ?? [])
      }

    let saveQueue = DispatchQueue(
      label: "com.ornament.pdfeditor.save",
      qos: .userInitiated
    )

    saveQueue.async { [weak self, loadFailures] in
      guard let self = self else { return }

      let formatter = DateFormatter()
      // The POSIX locale keeps the digits ASCII and the hours 24-hour whatever
      // the device settings, so the date part of the name has a fixed length.
      formatter.locale = Locale(identifier: "en_US_POSIX")
      formatter.dateFormat = "yyyy-MM-dd-HH-mm-ss"
      let dateString = formatter.string(from: Date())

      var resultArray = [String]()
      var failures = loadFailures

      for entry in resolved {
        switch self.writeDocument(
          item: entry.item,
          document: entry.document,
          excluded: entry.excluded,
          dateString: dateString
        ) {
        case .saved(let path):
          resultArray.append(path)
        case .skipped:
          break
        case .failed(let step):
          failures.append(Self.failure(entry.item, step))
        }
      }

      var params: [AnyHashable: Any] = ["url": NSNull(), "failures": failures]
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

  private enum WriteOutcome {
    case saved(String)
    case skipped
    case failed(SaveFailureStep)
  }

  private static func failure(_ item: RNPDFDocument, _ step: SaveFailureStep) -> [String: Any] {
    ["id": item.id, "type": item.type == .pdf ? "pdf" : "image", "step": step.rawValue]
  }

  // The incoming file name without its extension, percent-decoded and cut to
  // fit the file name cap.
  private static func outputBaseName(for incomingPath: String) -> String {
    let fileNameWithExt = incomingPath.components(separatedBy: "/").last ?? ""
    let decoded = fileNameWithExt.removingPercentEncoding ?? fileNameWithExt
    let fileNameRaw = decoded.components(separatedBy: ".").first ?? ""

    var baseName = ""
    var encodedLength = 0
    // Composed form: a decomposed letter (й as и plus a breve) takes twice the bytes.
    for character in fileNameRaw.precomposedStringWithCanonicalMapping {
      let length =
        String(character).addingPercentEncoding(withAllowedCharacters: .urlPathAllowed)?
        .utf8.count ?? 0
      guard encodedLength + length <= maxEncodedBaseNameLength else { break }
      baseName.append(character)
      encodedLength += length
    }
    return baseName
  }

  private func writeDocument(
    item: RNPDFDocument,
    document: PDFDocument,
    excluded: Set<Int>,
    dateString: String
  ) -> WriteOutcome {
    // The document id keeps apart two incoming names that differ only past the
    // cut, so one document does not overwrite the other.
    let fileName = Self.outputBaseName(for: item.incomingPath) + "_" + dateString + "_\(item.id)"

    if item.type == .image {
      guard !excluded.contains(0) else { return .skipped }

      let newPathComponent = fileName + ".jpg"
      let fileURL = getDocumentsDirectory().appendingPathComponent(newPathComponent)

      guard let page = document.page(at: 0) else {
        print("RNPDFEditor: image with path \(item.incomingPath) not writed locally")
        return .failed(.render)
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
        return .failed(.render)
      }

      do {
        try data.write(to: fileURL)
        return .saved(fileURL.absoluteString)
      } catch {
        print("RNPDFEditor: can't create image for saving")
        return .failed(.write)
      }
    }

    let newPathComponent = fileName + ".pdf"
    let fileURL = getDocumentsDirectory().appendingPathComponent(newPathComponent)

    // Snapshot via dataRepresentation so we can drop excluded pages without
    // mutating the on-screen PDFDocument. Drawing annotations are preserved
    // because PDFKit serializes them into the data blob.
    guard let snapshotData = document.dataRepresentation() else {
      print("RNPDFEditor: can't snapshot PDF for id \(item.id)")
      return .failed(.serialize)
    }
    guard let workingCopy = PDFDocument(data: snapshotData) else {
      print("RNPDFEditor: can't snapshot PDF for id \(item.id)")
      return .failed(.parse)
    }

    // Remove from the back so surviving indices stay valid.
    for pageIndex in excluded.sorted(by: >) {
      guard pageIndex >= 0 && pageIndex < workingCopy.pageCount else { continue }
      workingCopy.removePage(at: pageIndex)
    }

    // Entire document excluded — mirror the image-branch contract by skipping.
    guard workingCopy.pageCount > 0 else { return .skipped }

    if workingCopy.write(to: fileURL) {
      return .saved(fileURL.absoluteString)
    }
    print("RNPDFEditor: failed write PDF for id \(item.id)")
    return .failed(.write)
  }
}
