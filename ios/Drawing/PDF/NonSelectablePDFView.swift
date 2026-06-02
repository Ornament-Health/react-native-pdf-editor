import PDFKit
import UIKit

protocol DrawingGestureRecognizerDelegate: AnyObject {
  func gestureRecognizerBegan(_ location: CGPoint)
  func gestureRecognizerMoved(_ location: CGPoint)
  func gestureRecognizerEnded(_ location: CGPoint)
}

class NonSelectablePDFView: PDFView {

  weak var drawingDelegate: DrawingGestureRecognizerDelegate?
  var onTogglePage: ((Int) -> Void)?
  var selectionIconColor: UIColor = .white
  var excludedPages = Set<Int>()
  private var pan: UIPanGestureRecognizer?
  private var iconTapRecognizer: UITapGestureRecognizer?
  private var pageButtons: [Int: UIButton] = [:]
  private var pageShades: [Int: UIView] = [:]
  private var scrollObservation: NSKeyValueObservation?
  private var zoomObservation: NSKeyValueObservation?

  func setDrawingEnabled(_ isEnabled: Bool) {
    pan?.isEnabled = isEnabled
  }

  override init(frame: CGRect) {
    super.init(frame: frame)

    let pan = UIPanGestureRecognizer(target: self, action: #selector(handlePan))
    pan.delegate = self
    pan.minimumNumberOfTouches = 1
    pan.maximumNumberOfTouches = 1
    pan.cancelsTouchesInView = false

    self.pan = pan
    self.addGestureRecognizer(pan)

    // Page include/exclude is toggled on a clean tap (press-out), not on touch
    // down: starting a scroll on top of an icon must scroll rather than toggle.
    // The icon views are display-only (see makeIconButton) so the touch reaches
    // the scroll view; this recognizer only fires on a tap that lands on a
    // visible icon without turning into a drag.
    let tap = UITapGestureRecognizer(target: self, action: #selector(handleIconTap(_:)))
    tap.delegate = self
    tap.cancelsTouchesInView = false
    tap.numberOfTapsRequired = 1
    self.iconTapRecognizer = tap
    self.addGestureRecognizer(tap)

    NotificationCenter.default.addObserver(
      self,
      selector: #selector(handleViewChanged),
      name: Notification.Name.PDFViewPageChanged,
      object: self
    )

    NotificationCenter.default.addObserver(
      self,
      selector: #selector(handleViewChanged),
      name: Notification.Name.PDFViewScaleChanged,
      object: self
    )
  }

  required init?(coder: NSCoder) {
    fatalError("init(coder:) has not been implemented")
  }

  deinit {
    NotificationCenter.default.removeObserver(self)
    scrollObservation?.invalidate()
    zoomObservation?.invalidate()
  }

  override func layoutSubviews() {
    super.layoutSubviews()
    refreshPageIndicators()
  }

  /**
   * Should be called AFTER PDFView's document is set
   */
  func disableSelection(in view: UIView) {
    for rec in view.subviews.compactMap({ $0.gestureRecognizers }).flatMap({ $0 }) {
      if rec is UILongPressGestureRecognizer
        || type(of: rec).description() == "UITapAndAHalfRecognizer"
      {
        rec.isEnabled = false
      }
    }

    for view in view.subviews {
      if !view.subviews.isEmpty {
        disableSelection(in: view)
      }
    }
  }

  func updatePageIndicators(excluded: Set<Int>, iconColor: UIColor) {
    excludedPages = excluded
    selectionIconColor = iconColor
    refreshPageIndicators()
  }

  func clearPageIndicators() {
    for view in pageButtons.values { view.removeFromSuperview() }
    for view in pageShades.values { view.removeFromSuperview() }
    pageButtons.removeAll()
    pageShades.removeAll()
  }

  override func gestureRecognizer(
    _ gestureRecognizer: UIGestureRecognizer,
    shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
  ) -> Bool {
    return false
  }

  override func gestureRecognizer(
    _ gestureRecognizer: UIGestureRecognizer,
    shouldBeRequiredToFailBy otherGestureRecognizer: UIGestureRecognizer
  ) -> Bool {

    if gestureRecognizer == self.pan && otherGestureRecognizer != self.pan {
      return true
    } else {
      return false
    }
  }

  @objc func handlePan(_ sender: UIPanGestureRecognizer) {
    let location = sender.location(in: self)

    switch sender.state {
    case .began:
      drawingDelegate?.gestureRecognizerBegan(location)
    case .changed:
      drawingDelegate?.gestureRecognizerMoved(location)
    case .ended:
      drawingDelegate?.gestureRecognizerEnded(location)
    default:
      break
    }
  }

  @objc private func handleIconTap(_ sender: UITapGestureRecognizer) {
    let location = sender.location(in: self)
    guard let button = iconButton(at: location) else { return }
    onTogglePage?(button.tag)
  }

  private func iconButton(at point: CGPoint) -> UIButton? {
    // Expand the 28pt icon's hit area so the tap target stays comfortable
    // without enlarging the visible glyph.
    let hitInset: CGFloat = -8
    for button in pageButtons.values where !button.isHidden {
      if button.frame.insetBy(dx: hitInset, dy: hitInset).contains(point) {
        return button
      }
    }
    return nil
  }

  @objc private func handleViewChanged() {
    refreshPageIndicators()
  }

  private func refreshPageIndicators() {
    guard let document = document else {
      clearPageIndicators()
      return
    }

    ensureScrollObservers()

    guard let scrollView = scrollViewIfPresent() else {
      clearPageIndicators()
      return
    }

    let contentFrameInSelf = scrollView.frame

    for index in 0..<document.pageCount {
      guard let page = document.page(at: index) else { continue }

      let pageRect = convert(page.bounds(for: .cropBox), from: page)
      if pageRect.isEmpty { continue }

      let visibleRect = contentFrameInSelf.intersection(pageRect)
      let isVisible = !visibleRect.isEmpty && !visibleRect.isNull

      let shade = pageShades[index] ?? makeShadeView()
      shade.frame = visibleRect
      shade.isHidden = !excludedPages.contains(index) || !isVisible
      if shade.superview !== self {
        shade.removeFromSuperview()
        addSubview(shade)
      }
      pageShades[index] = shade

      let button = pageButtons[index] ?? makeIconButton()
      button.tag = index
      button.setImage(iconImage(isExcluded: excludedPages.contains(index)), for: .normal)

      if isVisible {
        let proposedFrame = iconFrame(for: pageRect, size: 28, inset: 8)
        if let clampedFrame = clampedIconFrame(
          proposedFrame,
          in: visibleRect,
          edgePadding: 8,
          bottomHideThreshold: 8
        ) {
          button.frame = clampedFrame
          button.isHidden = false
        } else {
          button.isHidden = true
        }
      } else {
        button.isHidden = true
      }

      if button.superview !== self {
        button.removeFromSuperview()
        addSubview(button)
      }
      bringSubviewToFront(button)
      pageButtons[index] = button
    }

    // Remove overlays for pages that no longer exist
    let existingKeys = Set(pageButtons.keys)
    let validKeys = Set(0..<document.pageCount)
    let removedKeys = existingKeys.subtracting(validKeys)
    removedKeys.forEach { key in
      pageButtons[key]?.removeFromSuperview()
      pageButtons.removeValue(forKey: key)
      pageShades[key]?.removeFromSuperview()
      pageShades.removeValue(forKey: key)
    }
  }

  private func makeShadeView() -> UIView {
    let view = UIView()
    view.backgroundColor = UIColor.black.withAlphaComponent(0.2)
    view.isUserInteractionEnabled = false
    return view
  }

  private func makeIconButton() -> UIButton {
    // Display-only: taps are handled by iconTapRecognizer so the underlying
    // scroll view still receives touches that start on an icon.
    let button = UIButton(type: .custom)
    button.adjustsImageWhenHighlighted = false
    button.isUserInteractionEnabled = false
    return button
  }

  private func scrollViewIfPresent() -> UIScrollView? {
    subviews.compactMap { $0 as? UIScrollView }.first
  }

  private func ensureScrollObservers() {
    guard let scrollView = scrollViewIfPresent() else { return }

    if scrollObservation == nil {
      scrollObservation = scrollView.observe(\.contentOffset, options: [.new]) { [weak self] _, _ in
        self?.refreshPageIndicators()
      }
    }

    if zoomObservation == nil {
      zoomObservation = scrollView.observe(\.zoomScale, options: [.new]) { [weak self] _, _ in
        self?.refreshPageIndicators()
      }
    }
  }

  private func iconFrame(for pageRect: CGRect, size: CGFloat, inset: CGFloat) -> CGRect {
    return CGRect(
      x: pageRect.maxX - inset - size,
      y: pageRect.minY + inset,
      width: size,
      height: size
    )
  }

  private func clampedIconFrame(
    _ frame: CGRect,
    in visibleRect: CGRect,
    edgePadding: CGFloat,
    bottomHideThreshold: CGFloat
  ) -> CGRect? {
    guard !visibleRect.isEmpty && !visibleRect.isNull else { return nil }

    // Hide a bit earlier when approaching the bottom edge while scrolling.
    if frame.maxY > visibleRect.maxY - bottomHideThreshold {
      return nil
    }

    let paddedRect = visibleRect.insetBy(dx: edgePadding, dy: edgePadding)
    guard !paddedRect.isEmpty && !paddedRect.isNull else { return nil }
    guard paddedRect.width >= frame.width, paddedRect.height >= frame.height else { return nil }

    let minX = paddedRect.minX
    let maxX = paddedRect.maxX - frame.width
    let minY = paddedRect.minY
    let maxY = paddedRect.maxY - frame.height

    let clampedX = min(max(frame.origin.x, minX), maxX)
    let clampedY = min(max(frame.origin.y, minY), maxY)

    return CGRect(x: clampedX, y: clampedY, width: frame.width, height: frame.height)
  }

  private func iconImage(isExcluded: Bool) -> UIImage? {
    let dimension: CGFloat = 28
    let renderer = UIGraphicsImageRenderer(size: CGSize(width: dimension, height: dimension))
    return renderer.image { _ in
      let rect = CGRect(x: 0, y: 0, width: dimension, height: dimension)

      let ringWidth: CGFloat = 2
      let circleRect = rect.insetBy(dx: ringWidth / 2, dy: ringWidth / 2)
      let circle = UIBezierPath(ovalIn: circleRect)
      circle.lineWidth = ringWidth

      if isExcluded {
        // Excluded page: empty white ring (unselected radio-style state).
        UIColor.white.setStroke()
        circle.stroke()
      } else {
        // Included page: accent-filled circle with a white ring and checkmark.
        selectionIconColor.setFill()
        circle.fill()
        UIColor.white.setStroke()
        circle.stroke()

        let check = UIBezierPath()
        check.move(to: CGPoint(x: dimension * 0.287, y: dimension * 0.507))
        check.addLine(to: CGPoint(x: dimension * 0.447, y: dimension * 0.66))
        check.addLine(to: CGPoint(x: dimension * 0.713, y: dimension * 0.353))
        check.lineWidth = dimension * 0.1
        check.lineCapStyle = .round
        check.lineJoinStyle = .round
        UIColor.white.setStroke()
        check.stroke()
      }
    }
  }

}
