import React

@objc(RNPDFEditorViewManager)
class RNPDFEditorViewManager: RCTViewManager {

    override func view() -> UIView! {
        return ContainerView()
    }

    @objc override static func requiresMainQueueSetup() -> Bool {
        return true
    }

    @objc func undoAction(_ node:NSNumber) {
      DispatchQueue.main.async {
          guard let component = self.bridge.uiManager.view(
              forReactTag: node
          ) as? ContainerView else {
              print("RNPDFEditor: Cannot find Native UIView with tag", node)
              return
          }
          component.undoButtonTapped()
      }
    }

    @objc func clearAction(_ node:NSNumber) {
      DispatchQueue.main.async {
          guard let component = self.bridge.uiManager.view(
              forReactTag: node
          ) as? ContainerView else {
              print("RNPDFEditor: Cannot find Native UIView with tag", node)
              return
          }
          component.clearButtonTapped()
      }
    }

    @objc func saveAction(_ node:NSNumber) {
      DispatchQueue.main.async {
          guard let component = self.bridge.uiManager.view(
              forReactTag: node
          ) as? ContainerView else {
              print("RNPDFEditor: Cannot find Native UIView with tag", node)
              return
          }
          component.saveButtonTapped()
      }
    }
}
