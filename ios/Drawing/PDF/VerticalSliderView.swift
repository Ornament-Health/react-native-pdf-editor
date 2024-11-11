//
//  VerticalSliderView.swift
//  react-native-pdf-editor
//
//  Created by Kushner Dzmitry on 27.10.2024.
//

import Foundation

final class VerticalSliderView: UIView {
  private lazy var slider = UISlider()
  
  override init(frame: CGRect) {
    super.init(frame: frame)
    commonInit()
  }
  
  required init?(coder: NSCoder) {
    fatalError("init(coder:) has not been implemented")
  }
  
  private func commonInit() {
    addSubviews()
    addConstraints()
    transformSlider()
  }
  
  private func addSubviews() {
    addSubview(slider)
  }
  
  private func addConstraints() {
    NSLayoutConstraint.activate([
      slider.topAnchor.constraint(equalTo: topAnchor),
      slider.leadingAnchor.constraint(equalTo: leadingAnchor),
      slider.trailingAnchor.constraint(equalTo: trailingAnchor),
      slider.bottomAnchor.constraint(equalTo: bottomAnchor)
    ])
  }
  
  private func transformSlider() {
    slider.transform = CGAffineTransform(rotationAngle: -.pi / 2)
  }
}
