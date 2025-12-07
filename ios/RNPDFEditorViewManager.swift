import React

@objc(RNPDFEditorViewManager)
class RNPDFEditorViewManager: RCTViewManager {

    override func view() -> UIView! {
        return ContainerView()
    }

    @objc override static func requiresMainQueueSetup() -> Bool {
        return true
    }

    @objc func setEditMode(_ node:NSNumber, isEdit: NSNumber) {
      DispatchQueue.main.async {
          guard let component = self.bridge.uiManager.view(
              forReactTag: node
          ) as? ContainerView else {
              print("RNPDFEditor: Cannot find Native UIView with tag", node)
              return
          }
          component.setEditMode(isEdit.boolValue)
      }
    }

    @objc func undoAction(_ node:NSNumber) {
      DispatchQueue.main.async {
          guard let component = self.bridge.uiManager.view(
              forReactTag: node
          ) as? ContainerView else {
              print("RNPDFEditor: Cannot find Native UIView with tag", node)
              return
          }
          component.undo()
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
          component.clear()
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
          component.save()
      }
    }
}
