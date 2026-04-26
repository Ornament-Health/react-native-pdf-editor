import Foundation
import PDFKit
import UIKit

extension ContainerView {

  func setEditModeImpl(_ isEdit: Bool) {
    let wasEditMode = self.isEditMode
    if wasEditMode == isEdit { return }

    if wasEditMode && !isEdit {
      commitAllDrawerHistoriesAsBaseline()
    }

    self.isEditMode = isEdit
    pdfView.setDrawingEnabled(isEdit)

    // In edit mode, disable PDFView's built-in pan/zoom to allow drawing
    // In view mode, enable PDFView's built-in pan/zoom
    if let scrollView = scrollViewIfPresent() {
      scrollView.isScrollEnabled = !isEdit
    }

    updateBottomControlsVisibility()
    updateUndoRedoButtons(canUndo: false, canRedo: false)
  }

  func setupView() {
    let pdfView = NonSelectablePDFView()
    pdfView.backgroundColor = .clear
    pdfView.translatesAutoresizingMaskIntoConstraints = false
    pdfView.displayMode = .singlePageContinuous
    pdfView.displayDirection = .vertical
    pdfView.usePageViewController(false)
    pdfView.pageBreakMargins = UIEdgeInsets(top: 10, left: 10, bottom: 10, right: 10)
    pdfView.autoScales = true
    pdfView.maxScaleFactor = 3.0
    pdfView.setDrawingEnabled(false)
    pdfView.isHidden = true

    let fileSwitcher = FileSwitcher()
    fileSwitcher.translatesAutoresizingMaskIntoConstraints = false
    fileSwitcher.delegate = self
    fileSwitcher.backgroundColor = UIColor.black.withAlphaComponent(0.35)

    let editControls = makeEditControlsContainer()

    let bottomOverlayContainer = UIView()
    bottomOverlayContainer.translatesAutoresizingMaskIntoConstraints = false
    bottomOverlayContainer.backgroundColor = .clear
    bottomOverlayContainer.clipsToBounds = true

    bottomOverlayContainer.addSubview(fileSwitcher)
    bottomOverlayContainer.addSubview(editControls)

    addSubview(pdfView)
    addSubview(bottomOverlayContainer)

    NSLayoutConstraint.activate([
      pdfView.topAnchor.constraint(equalTo: safeAreaLayoutGuide.topAnchor),
      pdfView.leadingAnchor.constraint(equalTo: safeAreaLayoutGuide.leadingAnchor),
      pdfView.trailingAnchor.constraint(equalTo: safeAreaLayoutGuide.trailingAnchor),
      pdfView.bottomAnchor.constraint(equalTo: safeAreaLayoutGuide.bottomAnchor),

      bottomOverlayContainer.leadingAnchor.constraint(equalTo: safeAreaLayoutGuide.leadingAnchor),
      bottomOverlayContainer.trailingAnchor.constraint(equalTo: safeAreaLayoutGuide.trailingAnchor),
      bottomOverlayContainer.bottomAnchor.constraint(equalTo: bottomAnchor),

      fileSwitcher.topAnchor.constraint(equalTo: bottomOverlayContainer.topAnchor),
      fileSwitcher.leadingAnchor.constraint(equalTo: bottomOverlayContainer.leadingAnchor),
      fileSwitcher.trailingAnchor.constraint(equalTo: bottomOverlayContainer.trailingAnchor),
      fileSwitcher.heightAnchor.constraint(equalToConstant: bottomControlsHeight),

      editControls.topAnchor.constraint(equalTo: bottomOverlayContainer.topAnchor),
      editControls.leadingAnchor.constraint(equalTo: bottomOverlayContainer.leadingAnchor),
      editControls.trailingAnchor.constraint(equalTo: bottomOverlayContainer.trailingAnchor),
      editControls.heightAnchor.constraint(equalToConstant: bottomControlsHeight),
    ])

    bottomOverlayHeightConstraint = bottomOverlayContainer.heightAnchor.constraint(
      equalToConstant: bottomControlsHeight
    )
    bottomOverlayHeightConstraint?.isActive = true

    self.pdfView = pdfView
    self.fileSwitcher = fileSwitcher
    self.bottomOverlayContainer = bottomOverlayContainer
    updateBottomControlsVisibility()
    updateUndoRedoButtons(canUndo: false, canRedo: false)
  }

  func updateBottomControlsVisibility() {
    fileSwitcher?.isHidden = isEditMode
    editControlsContainer?.isHidden = !isEditMode
    bottomOverlayContainer?.isHidden = false
    updateBottomOverlayLayout()
    updatePDFBottomInset()
  }

  func updateBottomOverlayLayout() {
    let safeBottom = safeAreaInsets.bottom
    let totalHeight = bottomControlsHeight + safeBottom
    if let constraint = bottomOverlayHeightConstraint {
      if abs(constraint.constant - totalHeight) > 0.5 {
        constraint.constant = totalHeight
      }
    }
  }

  func updatePDFBottomInset() {
    guard let scrollView = scrollViewIfPresent() else { return }

    let bottomInset = bottomScrollSpacerHeight()

    if #available(iOS 11.0, *) {
      scrollView.contentInsetAdjustmentBehavior = .never
      if #available(iOS 13.0, *) {
        scrollView.automaticallyAdjustsScrollIndicatorInsets = false
      }
    }

    if abs(scrollView.contentInset.bottom - bottomInset) > 0.5 {
      var inset = scrollView.contentInset
      inset.bottom = bottomInset
      scrollView.contentInset = inset

      var indicatorInset = scrollView.scrollIndicatorInsets
      indicatorInset.bottom = bottomInset
      scrollView.scrollIndicatorInsets = indicatorInset
    }
  }

  func bottomScrollSpacerHeight() -> CGFloat {
    let safeBottom = safeAreaInsets.bottom
    let overlayVisible = !(bottomOverlayContainer?.isHidden ?? true)
    return overlayVisible ? (bottomControlsHeight + safeBottom) : safeBottom
  }

  func makeEditControlsContainer() -> UIView {
    let containerView = UIView()
    containerView.translatesAutoresizingMaskIntoConstraints = false
    containerView.backgroundColor = UIColor.black.withAlphaComponent(0.35)

    // Create circular background view for undo button
    let undoBackgroundView = UIView()
    undoBackgroundView.translatesAutoresizingMaskIntoConstraints = false
    undoBackgroundView.backgroundColor = undoRedoIconColor
    undoBackgroundView.layer.cornerRadius = 18
    undoBackgroundView.clipsToBounds = true

    let undoButton = UIButton(type: .system)
    undoButton.translatesAutoresizingMaskIntoConstraints = false
    if #available(iOS 13.0, *) {
      // Add cut-out arrow using compositing filter instead of button image
      let undoIconView = UIImageView(image: UIImage(systemName: "arrow.uturn.left"))
      undoIconView.translatesAutoresizingMaskIntoConstraints = false
      undoIconView.tintColor = .black
      undoIconView.contentMode = .scaleAspectFit
      undoIconView.isUserInteractionEnabled = false
      undoIconView.layer.compositingFilter = "destinationOut"
      undoBackgroundView.addSubview(undoIconView)
      NSLayoutConstraint.activate([
        undoIconView.centerXAnchor.constraint(equalTo: undoBackgroundView.centerXAnchor),
        undoIconView.centerYAnchor.constraint(equalTo: undoBackgroundView.centerYAnchor),
        undoIconView.widthAnchor.constraint(equalToConstant: 24),
        undoIconView.heightAnchor.constraint(equalToConstant: 24),
      ])
    } else {
      undoButton.setTitle("Undo", for: .normal)
      undoButton.titleLabel?.font = UIFont.systemFont(ofSize: 16, weight: .medium)
    }
    undoButton.tintColor = .clear
    undoButton.backgroundColor = .clear
    undoButton.contentEdgeInsets = .zero
    undoButton.addTarget(self, action: #selector(handleUndoButtonTap), for: .touchUpInside)
    undoButton.isEnabled = false

    // Place the button over the background view to capture taps
    undoBackgroundView.addSubview(undoButton)
    NSLayoutConstraint.activate([
      undoButton.leadingAnchor.constraint(equalTo: undoBackgroundView.leadingAnchor),
      undoButton.trailingAnchor.constraint(equalTo: undoBackgroundView.trailingAnchor),
      undoButton.topAnchor.constraint(equalTo: undoBackgroundView.topAnchor),
      undoButton.bottomAnchor.constraint(equalTo: undoBackgroundView.bottomAnchor),
    ])

    // Create circular background view for redo button
    let redoBackgroundView = UIView()
    redoBackgroundView.translatesAutoresizingMaskIntoConstraints = false
    redoBackgroundView.backgroundColor = undoRedoIconColor
    redoBackgroundView.layer.cornerRadius = 18
    redoBackgroundView.clipsToBounds = true

    let redoButton = UIButton(type: .system)
    redoButton.translatesAutoresizingMaskIntoConstraints = false
    if #available(iOS 13.0, *) {
      // Add cut-out arrow using compositing filter instead of button image
      let redoIconView = UIImageView(image: UIImage(systemName: "arrow.uturn.right"))
      redoIconView.translatesAutoresizingMaskIntoConstraints = false
      redoIconView.tintColor = .black
      redoIconView.contentMode = .scaleAspectFit
      redoIconView.isUserInteractionEnabled = false
      redoIconView.layer.compositingFilter = "destinationOut"
      redoBackgroundView.addSubview(redoIconView)
      NSLayoutConstraint.activate([
        redoIconView.centerXAnchor.constraint(equalTo: redoBackgroundView.centerXAnchor),
        redoIconView.centerYAnchor.constraint(equalTo: redoBackgroundView.centerYAnchor),
        redoIconView.widthAnchor.constraint(equalToConstant: 24),
        redoIconView.heightAnchor.constraint(equalToConstant: 24),
      ])
    } else {
      redoButton.setTitle("Redo", for: .normal)
      redoButton.titleLabel?.font = UIFont.systemFont(ofSize: 16, weight: .medium)
    }
    redoButton.tintColor = .clear
    redoButton.backgroundColor = .clear
    redoButton.contentEdgeInsets = .zero
    redoButton.addTarget(self, action: #selector(handleRedoButtonTap), for: .touchUpInside)
    redoButton.isEnabled = false

    // Place the button over the background view to capture taps
    redoBackgroundView.addSubview(redoButton)
    NSLayoutConstraint.activate([
      redoButton.leadingAnchor.constraint(equalTo: redoBackgroundView.leadingAnchor),
      redoButton.trailingAnchor.constraint(equalTo: redoBackgroundView.trailingAnchor),
      redoButton.topAnchor.constraint(equalTo: redoBackgroundView.topAnchor),
      redoButton.bottomAnchor.constraint(equalTo: redoBackgroundView.bottomAnchor),
    ])

    let stack = UIStackView(arrangedSubviews: [undoBackgroundView, redoBackgroundView])
    stack.translatesAutoresizingMaskIntoConstraints = false
    stack.axis = .horizontal
    stack.alignment = .center
    stack.spacing = 16
    stack.setContentHuggingPriority(.required, for: .horizontal)
    stack.setContentCompressionResistancePriority(.required, for: .horizontal)

    containerView.addSubview(stack)
    NSLayoutConstraint.activate([
      stack.centerXAnchor.constraint(equalTo: containerView.centerXAnchor),
      stack.centerYAnchor.constraint(equalTo: containerView.centerYAnchor),
      stack.leadingAnchor.constraint(
        greaterThanOrEqualTo: containerView.leadingAnchor, constant: 16),
      stack.trailingAnchor.constraint(
        lessThanOrEqualTo: containerView.trailingAnchor, constant: -16),
      undoBackgroundView.widthAnchor.constraint(equalToConstant: 36),
      undoBackgroundView.heightAnchor.constraint(equalToConstant: 36),
      redoBackgroundView.widthAnchor.constraint(equalToConstant: 36),
      redoBackgroundView.heightAnchor.constraint(equalToConstant: 36),
    ])

    self.undoButton = undoButton
    self.redoButton = redoButton
    self.undoButtonBackgroundView = undoBackgroundView
    self.redoButtonBackgroundView = redoBackgroundView
    self.editControlsContainer = containerView
    applyUndoRedoColor()
    updateUndoRedoButtons(canUndo: false, canRedo: false)

    return containerView
  }

  @objc func handleUndoButtonTap() {
    undo()
  }

  @objc func handleRedoButtonTap() {
    activeDrawer?.redo()
  }

  func scrollViewIfPresent() -> UIScrollView? {
    return pdfView.subviews.first(where: { $0 is UIScrollView }) as? UIScrollView
  }

  func resetZoomScales() {
    guard let scrollView = scrollViewIfPresent() else { return }
    let fitScale = pdfView.scaleFactorForSizeToFit
    let maxScale = max(fitScale, pdfView.maxScaleFactor)
    pdfView.minScaleFactor = fitScale
    pdfView.maxScaleFactor = maxScale
    pdfView.scaleFactor = fitScale
    scrollView.zoomScale = fitScale
  }

  func configureScrollView() {
    guard let scrollView = scrollViewIfPresent() else { return }
    scrollView.isScrollEnabled = !isEditMode
    updatePDFBottomInset()
  }

  func applyUndoRedoColor() {
    undoButtonBackgroundView?.backgroundColor = undoRedoIconColor
    redoButtonBackgroundView?.backgroundColor = undoRedoIconColor
  }

  func updateUndoRedoButtons(canUndo: Bool, canRedo: Bool) {
    undoButton?.isEnabled = canUndo
    undoButtonBackgroundView?.alpha = canUndo ? 1 : editControlDisabledAlpha

    redoButton?.isEnabled = canRedo
    redoButtonBackgroundView?.alpha = canRedo ? 1 : editControlDisabledAlpha
  }
}
