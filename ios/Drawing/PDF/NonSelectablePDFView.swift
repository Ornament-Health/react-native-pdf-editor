//
//  NonSelectablePDFView.swift
//  react-native-pdf-editor
//

import UIKit
import PDFKit

protocol DrawingGestureRecognizerDelegate: AnyObject {
    func gestureRecognizerBegan(_ location: CGPoint)
    func gestureRecognizerMoved(_ location: CGPoint, lineWidth: CGFloat)
    func gestureRecognizerEnded(_ location: CGPoint, lineWidth: CGFloat)
}

class NonSelectablePDFView: PDFView {

    weak var drawingDelegate: DrawingGestureRecognizerDelegate?
    private var pan: UIPanGestureRecognizer?
    let sliderBackgroundView = UIView()
    let slider = UISlider()
    let editView = UIView(frame: CGRect(x: 0, y: 0, width: 70, height: 70))
    let editImageView = UIImageView(frame: CGRect(x: 0, y: 0, width: 40, height: 40))
    private var lineWidth: CGFloat = 10
    private var panEditAvailable = false
    private var isEditMode = false

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
          selector: #selector(documentDidLoad),
          name: .PDFViewVisiblePagesChanged,
          object: self
        )
        self.addSubviews()
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
  
  @objc func documentDidLoad() {
    if let document = document, let page = document.page(at: 0) {
    let pageBounds = page.bounds(for: displayBox)
      scaleFactor = (bounds.width - 2) / pageBounds.width
    }
  }
  
  override func layoutSubviews() {
    super.layoutSubviews()
    editView.frame = CGRect(x: self.frame.maxX - 70 - 15, y: self.frame.maxY - 70 - 50, width: 70, height: 70)
    sliderBackgroundView.frame = CGRect(
      x: editView.frame.midX - sliderBackgroundView.frame.width / 2,
      y: editView.frame.minY - sliderBackgroundView.frame.width * 3 - 5,
      width: sliderBackgroundView.frame.width,
      height: sliderBackgroundView.frame.height
    )
  }
  
  private func addSubviews() {
      sliderBackgroundView.backgroundColor = .white
      sliderBackgroundView.frame = CGRect(x: 0, y: 0, width: 120, height: 40)
      sliderBackgroundView.layer.cornerRadius = 20
      addSubview(sliderBackgroundView)
      sliderBackgroundView.isHidden = !isEditMode
      sliderBackgroundView.layer.shadowColor = UIColor.black.cgColor
      sliderBackgroundView.layer.shadowOpacity = 1
      sliderBackgroundView.layer.shadowOffset = CGSize.zero
      sliderBackgroundView.layer.shadowRadius = 5
      
      slider.frame = CGRect(x: 0, y: 0, width: 100, height: 30)
      slider.center = sliderBackgroundView.center
      
      slider.minimumValue = 10
      slider.maximumValue = 60
      slider.value = Float(lineWidth)
      
      slider.minimumTrackTintColor = .clear
      slider.maximumTrackTintColor = .clear
      slider.addTarget(self, action: #selector(sliderValueChanged(_:)), for: .valueChanged)
      slider.addTarget(self, action: #selector(sliderStartEditing(_:)), for: .touchDown)
      slider.addTarget(self, action: #selector(sliderEndEditing(_:)), for: [.touchUpInside, .touchUpOutside])
      
      sliderBackgroundView.addSubview(slider)
      sliderBackgroundView.transform = CGAffineTransform(rotationAngle: -CGFloat.pi / 2)
      
      editView.backgroundColor = .white
      editView.layer.cornerRadius = 35
      editView.layer.shadowColor = UIColor.black.cgColor
      editView.layer.shadowOpacity = 1
      editView.layer.shadowOffset = CGSize.zero
      editView.layer.shadowRadius = 5
      addSubview(editView)
      let editTap = UITapGestureRecognizer(target: self, action: #selector(changEditMode))
      editView.addGestureRecognizer(editTap)
    
      editView.addSubview(editImageView)
      editImageView.center = editView.center
      if #available(iOS 13.0, *) {
        editImageView.image = UIImage(systemName: "pencil.tip")?.withRenderingMode(.alwaysOriginal).withTintColor(.black)
      }
    }
  
    @objc func sliderValueChanged(_ sender: UISlider) {
        let step: Float = 5.0 // Define the step value
      
        // Round the slider's value to the nearest step
        let roundedValue = round(sender.value / step) * step
        sender.value = roundedValue
        
        lineWidth = CGFloat(roundedValue)
    }
  
  @objc func sliderStartEditing(_ sender: UISlider) {
    panEditAvailable = false
  }

  @objc func sliderEndEditing(_ sender: UISlider) {
    panEditAvailable = true
  }
  
  @objc func changEditMode() {
    isEditMode.toggle()
    panEditAvailable = isEditMode
    sliderBackgroundView.isHidden = !isEditMode
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

    override func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
       return false
    }

    override func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldBeRequiredToFailBy otherGestureRecognizer: UIGestureRecognizer) -> Bool {

        if gestureRecognizer == self.pan && otherGestureRecognizer != self.pan {
            return true
        } else { return false }
    }

    @objc func handlePan(_ sender: UIPanGestureRecognizer) {
      if panEditAvailable, isEditMode {
        let location = sender.location(in: self)
        
        switch sender.state {
        case .began:
          drawingDelegate?.gestureRecognizerBegan(location)
        case .changed:
          drawingDelegate?.gestureRecognizerMoved(location, lineWidth: lineWidth)
        case .ended:
          drawingDelegate?.gestureRecognizerEnded(location, lineWidth: lineWidth)
        default:
          break
        }
      }
    }

}
