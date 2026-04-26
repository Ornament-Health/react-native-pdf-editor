import UIKit

protocol FileSwitcherDelegate: AnyObject {
    func didSelectFile(at index: Int)
}

private final class PreviewThumbnailButton: UIButton {

    private enum Metrics {
        static let unselectedScale: CGFloat = 0.88
        static let imageInset: CGFloat = 3
        static let sheetSize = CGSize(width: 74, height: 94)
        static let frontOffset = CGPoint(x: -3, y: -3)
        static let middleOffset = CGPoint.zero
        static let backOffset = CGPoint(x: 3, y: 3)
    }

    let imageViewContainer = UIImageView()

    private let previewContentView = UIView()
    private let backSheetFar = UIView()
    private let backSheetNear = UIView()
    private let frontSheet = UIView()
    private var frontSheetCenterXConstraint: NSLayoutConstraint?
    private var frontSheetCenterYConstraint: NSLayoutConstraint?

    override init(frame: CGRect) {
        super.init(frame: frame)
        setupView()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setupView()
    }

    func setMultiPage(_ isMultiPage: Bool) {
        backSheetFar.isHidden = !isMultiPage
        backSheetNear.isHidden = !isMultiPage
        frontSheetCenterXConstraint?.constant = isMultiPage ? Metrics.frontOffset.x : 0
        frontSheetCenterYConstraint?.constant = isMultiPage ? Metrics.frontOffset.y : 0
    }

    func setPreviewSelected(_ isSelected: Bool) {
        let scale = isSelected ? 1.0 : Metrics.unselectedScale
        previewContentView.transform = CGAffineTransform(scaleX: scale, y: scale)
    }

    private func setupView() {
        adjustsImageWhenHighlighted = false
        clipsToBounds = false

        previewContentView.translatesAutoresizingMaskIntoConstraints = false
        previewContentView.isUserInteractionEnabled = false
        addSubview(previewContentView)

        NSLayoutConstraint.activate([
            previewContentView.topAnchor.constraint(equalTo: topAnchor),
            previewContentView.leadingAnchor.constraint(equalTo: leadingAnchor),
            previewContentView.trailingAnchor.constraint(equalTo: trailingAnchor),
            previewContentView.bottomAnchor.constraint(equalTo: bottomAnchor)
        ])

        configureSheet(backSheetFar, size: Metrics.sheetSize, offset: Metrics.backOffset)
        configureSheet(backSheetNear, size: Metrics.sheetSize, offset: Metrics.middleOffset)
        configureSheet(frontSheet, size: Metrics.sheetSize, offset: Metrics.frontOffset, isFrontSheet: true)

        imageViewContainer.translatesAutoresizingMaskIntoConstraints = false
        imageViewContainer.contentMode = .scaleAspectFit
        imageViewContainer.backgroundColor = .white
        imageViewContainer.clipsToBounds = true
        frontSheet.addSubview(imageViewContainer)

        NSLayoutConstraint.activate([
            imageViewContainer.topAnchor.constraint(equalTo: frontSheet.topAnchor, constant: Metrics.imageInset),
            imageViewContainer.leadingAnchor.constraint(equalTo: frontSheet.leadingAnchor, constant: Metrics.imageInset),
            imageViewContainer.trailingAnchor.constraint(equalTo: frontSheet.trailingAnchor, constant: -Metrics.imageInset),
            imageViewContainer.bottomAnchor.constraint(equalTo: frontSheet.bottomAnchor, constant: -Metrics.imageInset)
        ])
    }

    private func configureSheet(
        _ sheet: UIView,
        size: CGSize,
        offset: CGPoint,
        isFrontSheet: Bool = false
    ) {
        sheet.translatesAutoresizingMaskIntoConstraints = false
        sheet.backgroundColor = .white
        sheet.layer.borderWidth = 1 / UIScreen.main.scale
        sheet.layer.borderColor = UIColor.black.cgColor
        sheet.isUserInteractionEnabled = false
        previewContentView.addSubview(sheet)

        let centerXConstraint = sheet.centerXAnchor.constraint(
            equalTo: previewContentView.centerXAnchor,
            constant: offset.x
        )
        let centerYConstraint = sheet.centerYAnchor.constraint(
            equalTo: previewContentView.centerYAnchor,
            constant: offset.y
        )

        NSLayoutConstraint.activate([
            sheet.widthAnchor.constraint(equalToConstant: size.width),
            sheet.heightAnchor.constraint(equalToConstant: size.height),
            centerXConstraint,
            centerYConstraint
        ])

        if isFrontSheet {
            frontSheetCenterXConstraint = centerXConstraint
            frontSheetCenterYConstraint = centerYConstraint
        }
    }
}

class FileSwitcher: UIView {

    private enum Metrics {
        static let buttonWidth: CGFloat = 80
        static let buttonHeight: CGFloat = 100
        static let buttonSpacing: CGFloat = 10
    }

    weak var delegate: FileSwitcherDelegate?

    private var scrollView: UIScrollView!
    private var stackView: UIStackView!
    private var documents: [RNPDFDocument] = []
    private var selectedIndex: Int = 0
    private var thumbnailButtons: [PreviewThumbnailButton] = []
    private var customBackgroundColor: UIColor?

    override init(frame: CGRect) {
        super.init(frame: frame)
        setupView()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setupView()
    }

    private func setupView() {
        updateBackgroundColor()

        scrollView = UIScrollView()
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.showsHorizontalScrollIndicator = false
        scrollView.showsVerticalScrollIndicator = false

        stackView = UIStackView()
        stackView.translatesAutoresizingMaskIntoConstraints = false
        stackView.axis = .horizontal
        stackView.spacing = Metrics.buttonSpacing
        stackView.alignment = .center
        stackView.distribution = .fill

        scrollView.addSubview(stackView)
        addSubview(scrollView)

        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: topAnchor, constant: 10),
            scrollView.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 10),
            scrollView.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -10),
            scrollView.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -10),

            stackView.topAnchor.constraint(equalTo: scrollView.topAnchor),
            stackView.leadingAnchor.constraint(equalTo: scrollView.leadingAnchor),
            stackView.trailingAnchor.constraint(equalTo: scrollView.trailingAnchor),
            stackView.bottomAnchor.constraint(equalTo: scrollView.bottomAnchor),
            stackView.heightAnchor.constraint(equalTo: scrollView.heightAnchor)
        ])
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        updateCenteringIfNeeded()
    }

    func configure(with documents: [RNPDFDocument], selectedIndex: Int = 0) {
        self.documents = documents
        self.selectedIndex = selectedIndex

        thumbnailButtons.forEach {
            stackView.removeArrangedSubview($0)
            $0.removeFromSuperview()
        }
        thumbnailButtons.removeAll()

        guard !documents.isEmpty else { return }

        for (index, document) in documents.enumerated() {
            let button = createThumbnailButton(for: document, at: index)
            thumbnailButtons.append(button)
            stackView.addArrangedSubview(button)
        }

        updateSelectedButton()
        setNeedsLayout()
    }

    private func updateCenteringIfNeeded() {
        guard !documents.isEmpty else { return }

        let totalButtonsWidth = CGFloat(documents.count) * Metrics.buttonWidth
        let totalSpacingWidth = CGFloat(max(0, documents.count - 1)) * Metrics.buttonSpacing
        let totalContentWidth = totalButtonsWidth + totalSpacingWidth

        let availableWidth = scrollView.frame.width

        guard availableWidth > 0 else { return }

        if totalContentWidth <= availableWidth {
            let leftPadding = max(0, (availableWidth - totalContentWidth) / 2)
            scrollView.contentInset = UIEdgeInsets(top: 0, left: leftPadding, bottom: 0, right: leftPadding)
        } else {
            scrollView.contentInset = UIEdgeInsets.zero
        }
    }

    private func createThumbnailButton(for document: RNPDFDocument, at index: Int) -> PreviewThumbnailButton {
        let button = PreviewThumbnailButton(frame: .zero)
        button.translatesAutoresizingMaskIntoConstraints = false
        button.tag = index
        button.addTarget(self, action: #selector(thumbnailButtonTapped(_:)), for: .touchUpInside)

        NSLayoutConstraint.activate([
            button.widthAnchor.constraint(equalToConstant: Metrics.buttonWidth),
            button.heightAnchor.constraint(equalToConstant: Metrics.buttonHeight)
        ])

        button.setMultiPage(document.pageCount > 1)

        DispatchQueue.global(qos: .background).async {
            let thumbnail = document.generateThumbnail()
            DispatchQueue.main.async {
                button.setMultiPage(document.pageCount > 1)
                button.imageViewContainer.image = thumbnail
            }
        }

        return button
    }

    @objc private func thumbnailButtonTapped(_ sender: UIButton) {
        selectedIndex = sender.tag
        updateSelectedButton()
        delegate?.didSelectFile(at: selectedIndex)
    }

    private func updateSelectedButton() {
        for (index, button) in thumbnailButtons.enumerated() {
            button.setPreviewSelected(index == selectedIndex)
        }
    }

    func selectFile(at index: Int) {
        guard index >= 0 && index < documents.count else { return }
        selectedIndex = index
        updateSelectedButton()
    }

    func setBackgroundColor(_ color: UIColor?) {
        customBackgroundColor = color
        updateBackgroundColor()
    }

    private func updateBackgroundColor() {
        if let customColor = customBackgroundColor {
            backgroundColor = customColor
        } else {
            backgroundColor = .clear
        }
    }
}
