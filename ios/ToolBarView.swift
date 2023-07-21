//
//  ToolBarView.swift
//  react-native-pdf-editor
//

import Foundation
import UIKit

@objc(ToolBarViewDelegate)
protocol ToolBarViewDelegate: AnyObject {
    func moveButtonTapped()
    func lineButtonTapped()
    func undoButtonTapped()
    func saveButtonTapped()
}

@objc(ToolBarView)
class ToolBarView: UIView {

    weak var delegate: ToolBarViewDelegate?

    override init(frame: CGRect) {
        super.init(frame: frame)
        setupView()
    }

    required init?(coder: NSCoder) {
        fatalError("RNPDFEditor: init(coder:) has not been implemented")
    }

    private func setupView() {

        let moveButton = UIButton(type: .system)
        moveButton.translatesAutoresizingMaskIntoConstraints = false
        moveButton.setTitle("Scroll", for: .normal)
        moveButton.addTarget(self, action: #selector(moveButtonTapped), for: .touchUpInside)

        let lineButton = UIButton(type: .system)
        lineButton.translatesAutoresizingMaskIntoConstraints = false
        lineButton.setTitle("Draw", for: .normal)
        lineButton.addTarget(self, action: #selector(lineButtonTapped), for: .touchUpInside)

        let undoButton = UIButton(type: .system)
        undoButton.translatesAutoresizingMaskIntoConstraints = false
        undoButton.setTitle("Undo", for: .normal)
        undoButton.addTarget(self, action: #selector(undoButtonTapped), for: .touchUpInside)

        let saveButton = UIButton(type: .system)
        saveButton.translatesAutoresizingMaskIntoConstraints = false
        saveButton.setTitle("Save", for: .normal)
        saveButton.addTarget(self, action: #selector(saveButtonTapped), for: .touchUpInside)

        let stackView = UIStackView(arrangedSubviews: [moveButton, lineButton, undoButton, saveButton])
        stackView.translatesAutoresizingMaskIntoConstraints = false
        stackView.axis = .horizontal
        stackView.spacing = 8
        stackView.distribution = .fillEqually

        addSubview(stackView)

        NSLayoutConstraint.activate([
            stackView.topAnchor.constraint(equalTo: topAnchor),
            bottomAnchor.constraint(equalTo: stackView.bottomAnchor),

            stackView.leadingAnchor.constraint(equalTo: leadingAnchor),
            trailingAnchor.constraint(equalTo: stackView.trailingAnchor)
        ])
    }

    @objc func moveButtonTapped() {
        delegate?.moveButtonTapped()
    }

    @objc func lineButtonTapped() {
        delegate?.lineButtonTapped()
    }

    @objc func undoButtonTapped() {
        delegate?.undoButtonTapped()
    }

    @objc func saveButtonTapped() {
        delegate?.saveButtonTapped()
    }

}

