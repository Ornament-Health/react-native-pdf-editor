import UIKit

protocol FileSwitcherDelegate: AnyObject {
    func didSelectFile(at index: Int)
}

class FileSwitcher: UIView {
    
    weak var delegate: FileSwitcherDelegate?
    
    private var scrollView: UIScrollView!
    private var stackView: UIStackView!
    private var documents: [RNPDFDocument] = []
    private var selectedIndex: Int = 0
    private var thumbnailButtons: [UIButton] = []
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
        stackView.spacing = 10
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
        
        // Clear existing buttons
        thumbnailButtons.forEach { $0.removeFromSuperview() }
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
        
        let buttonWidth: CGFloat = 80
        let spacing: CGFloat = 10
        let totalButtonsWidth = CGFloat(documents.count) * buttonWidth
        let totalSpacingWidth = CGFloat(max(0, documents.count - 1)) * spacing
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
    
    private func createThumbnailButton(for document: RNPDFDocument, at index: Int) -> UIButton {
        let button = UIButton(type: .custom)
        button.translatesAutoresizingMaskIntoConstraints = false
        button.tag = index
        button.addTarget(self, action: #selector(thumbnailButtonTapped(_:)), for: .touchUpInside)
        
        // Set button size
        NSLayoutConstraint.activate([
            button.widthAnchor.constraint(equalToConstant: 80),
            button.heightAnchor.constraint(equalToConstant: 100)
        ])
        
        // Create container view for thumbnail and border
        let containerView = UIView()
        containerView.translatesAutoresizingMaskIntoConstraints = false
        containerView.layer.cornerRadius = 8
        containerView.layer.borderWidth = 2
        containerView.layer.borderColor = UIColor.clear.cgColor
        if #available(iOS 13.0, *) {
          containerView.backgroundColor = UIColor.systemBackground
        } else {
          containerView.backgroundColor = UIColor.white
        }
        containerView.isUserInteractionEnabled = false
        
        button.addSubview(containerView)
        
        NSLayoutConstraint.activate([
            containerView.topAnchor.constraint(equalTo: button.topAnchor),
            containerView.leadingAnchor.constraint(equalTo: button.leadingAnchor),
            containerView.trailingAnchor.constraint(equalTo: button.trailingAnchor),
            containerView.bottomAnchor.constraint(equalTo: button.bottomAnchor)
        ])
        
        // Add thumbnail image
        let imageView = UIImageView()
        imageView.translatesAutoresizingMaskIntoConstraints = false
        imageView.contentMode = .scaleAspectFit
        if #available(iOS 13.0, *) {
          imageView.backgroundColor = UIColor.systemGray6
        } else {
          imageView.backgroundColor = UIColor.lightGray
        }
        imageView.layer.cornerRadius = 6
        imageView.clipsToBounds = true
        
        containerView.addSubview(imageView)
        
        NSLayoutConstraint.activate([
            imageView.topAnchor.constraint(equalTo: containerView.topAnchor, constant: 4),
            imageView.leadingAnchor.constraint(equalTo: containerView.leadingAnchor, constant: 4),
            imageView.trailingAnchor.constraint(equalTo: containerView.trailingAnchor, constant: -4),
            imageView.bottomAnchor.constraint(equalTo: containerView.bottomAnchor, constant: -4)
        ])
        
        // Load thumbnail asynchronously
        DispatchQueue.global(qos: .background).async {
            let thumbnail = document.generateThumbnail()
            DispatchQueue.main.async {
                imageView.image = thumbnail
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
            if let containerView = button.subviews.first {
                containerView.layer.borderColor = (index == selectedIndex)
                    ? UIColor.systemBlue.cgColor
                    : UIColor.clear.cgColor
            }
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
