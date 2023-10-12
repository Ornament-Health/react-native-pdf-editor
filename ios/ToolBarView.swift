//
//  ToolBarView.swift
//  react-native-pdf-editor
//

import Foundation
import UIKit

@objc(ToolBarViewDelegate)
protocol ToolBarViewDelegate: AnyObject {
    func undoButtonTapped()
    func clearButtonTapped()
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

        let undoButton = UIButton(type: .system)
        undoButton.translatesAutoresizingMaskIntoConstraints = false
        undoButton.setTitle("Undo", for: .normal)
        undoButton.addTarget(self, action: #selector(undoButtonTapped), for: .touchUpInside)

        let clearButton = UIButton(type: .system)
        clearButton.translatesAutoresizingMaskIntoConstraints = false
        clearButton.setTitle("Clear", for: .normal)
        clearButton.addTarget(self, action: #selector(clearButtonTapped), for: .touchUpInside)

        let saveButton = UIButton(type: .system)
        saveButton.translatesAutoresizingMaskIntoConstraints = false
        saveButton.setTitle("Save", for: .normal)
        saveButton.addTarget(self, action: #selector(saveButtonTapped), for: .touchUpInside)

        let stackView = UIStackView(arrangedSubviews: [undoButton, clearButton, saveButton])
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

    @objc func undoButtonTapped() {
        delegate?.undoButtonTapped()
    }

    @objc func clearButtonTapped() {
        delegate?.clearButtonTapped()
    }

    @objc func saveButtonTapped() {
        delegate?.saveButtonTapped()
    }

}

