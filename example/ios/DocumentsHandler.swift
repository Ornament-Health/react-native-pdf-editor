import Foundation
import React

@objc(DocumentsHandler)
class DocumentsHandler: NSObject {

  @objc static func requiresMainQueueSetup() -> Bool {
    return false
  }

  @objc func process(_ data: NSDictionary,  resolver resolve: @escaping RCTPromiseResolveBlock,  rejecter reject: @escaping RCTPromiseRejectBlock) {
    guard let arrayOfPaths = data["documents"] as? [String] else {
      reject("RNDocumentsHandler", "\"arrayOfPaths\" value is wrong", nil)
      return
    }
    guard let expectedWidth = data["expectedWidth"] as? Float else {
      reject("RNDocumentsHandler", "\"expectedWidth\" value is wrong", nil)
      return
    }
    guard let grayscale = data["grayscale"] as? Bool else {
      reject("RNDocumentsHandler", "\"grayscale\" value is wrong", nil)
      return
    }

    var resultArray = [[String: Any]]()
    let queue = OperationQueue()
    queue.qualityOfService = .userInitiated
    queue.maxConcurrentOperationCount = 5

    let doneOperation = Operation()
    doneOperation.completionBlock = {
      resultArray.sort {
        if let first = $0["index"] as? Int,
           let second = $1["index"] as? Int {
          return first < second
        } else { return false }
      }
      resolve(["result": resultArray])
    }

    for (index, value) in arrayOfPaths.enumerated() {
      let operation = BlockOperation {
        if let document = Document(id: index, path: value, grayscale: grayscale, newWidth: CGFloat(expectedWidth)) {
          document.process { results in
            if let result = results {
              resultArray.append(["index": index,
                                  "incoming": value,
                                  "outcoming": result])
            } else {
              print("RNDocumentsHandler: operation ends with error for element with index", index)
            }
          }
        }
      }
      queue.addOperation(operation)
      doneOperation.addDependency(operation)

    }
    queue.addOperation(doneOperation)
  }
}
