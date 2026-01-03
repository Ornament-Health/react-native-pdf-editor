import UIKit
import PDFKit

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
        for rec in view.subviews.compactMap({$0.gestureRecognizers}).flatMap({$0}) {
            if rec is UILongPressGestureRecognizer || type(of: rec).description() == "UITapAndAHalfRecognizer" {
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

    override func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
       return false
    }

    override func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldBeRequiredToFailBy otherGestureRecognizer: UIGestureRecognizer) -> Bool {

        if gestureRecognizer == self.pan && otherGestureRecognizer != self.pan {
            return true
        } else { return false }
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

    @objc private func togglePage(_ sender: UIButton) {
        onTogglePage?(sender.tag)
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

        for index in 0..<document.pageCount {
            guard let page = document.page(at: index) else { continue }
            let pageRect = convert(page.bounds(for: .cropBox), from: page)
            if pageRect.isEmpty { continue }

            let shade = pageShades[index] ?? makeShadeView()
            shade.frame = pageRect
            shade.isHidden = !excludedPages.contains(index)
            if shade.superview == nil { addSubview(shade) }
            pageShades[index] = shade

            let button = pageButtons[index] ?? makeIconButton()
            button.tag = index
            button.frame = iconFrame(for: pageRect, size: 28, inset: 8)
            button.setImage(iconImage(isExcluded: excludedPages.contains(index)), for: .normal)
            if button.superview == nil { addSubview(button) }
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
        let button = UIButton(type: .custom)
        button.adjustsImageWhenHighlighted = false
        button.addTarget(self, action: #selector(togglePage(_:)), for: .touchUpInside)
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

    private func iconImage(isExcluded: Bool) -> UIImage? {
        let dimension: CGFloat = 28
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: dimension, height: dimension))
        return renderer.image { context in
            let rect = CGRect(x: 0, y: 0, width: dimension, height: dimension)
            let lineWidth: CGFloat = 2
            let circleRect = rect.insetBy(dx: 2, dy: 2)
            let bg = UIBezierPath(ovalIn: circleRect)
            UIColor.black.withAlphaComponent(0.35).setFill()
            bg.fill()

            selectionIconColor.setStroke()
            bg.lineWidth = lineWidth
            bg.stroke()

            if isExcluded {
                let slash = UIBezierPath()
                slash.move(to: CGPoint(x: circleRect.minX + 4, y: circleRect.maxY - 4))
                slash.addLine(to: CGPoint(x: circleRect.maxX - 4, y: circleRect.minY + 4))
                slash.lineWidth = lineWidth
                selectionIconColor.setStroke()
                slash.stroke()
            } else {
                let check = UIBezierPath()
                check.move(to: CGPoint(x: circleRect.minX + 5, y: circleRect.midY))
                check.addLine(to: CGPoint(x: circleRect.midX - 1, y: circleRect.maxY - 6))
                check.addLine(to: CGPoint(x: circleRect.maxX - 5, y: circleRect.minY + 6))
                check.lineWidth = lineWidth
                check.lineCapStyle = .round
                check.lineJoinStyle = .round
                selectionIconColor.setStroke()
                check.stroke()
            }
        }
    }

}
