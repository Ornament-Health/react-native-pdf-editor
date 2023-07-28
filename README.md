# react-native-pdf-editor

React Native PDF editor (iOS)

## Installation

```sh
npm install @ornament-health/react-native-pdf-editor
cd ios
pod install
```

## Usage
In order to start using RNPDFEditorView, you should create configuration for it with local link to document and some styling parameters. You can handle visibility of native toolbar, backgrounf color, line color and width from React Native side.  

```typescript
import { RNPDFEditorView } from "@ornament-health/react-native-pdf-editor";

// ...

const options = {
  fileName: source,
  isToolBarHidden: false,
  viewBackgroundColor: '#40a35f',
  lineColor: '#4287f5',
  lineWidth: 40,
  startWithEdit: true,
};

<PDFEditorView 
  ref={pdfRef}
  style={styles.pdfView}
  options={options}
  onSavePDF={handleSavePDF}
/> 
```
Saving option can return 'null' if there was errors at native side, otherwise it will return string contains local path to saved document. Note, that after every saving action document stores at apps Document Directory, so removing unnecessary local copies should be provided at React Native side.
  
```typescript
const handleSavePDF = (e: string | null) => {
  if (e === null) {
    console.log('got null value for url:', e);
  } else {
    console.log('got url:', e);
  }        
};
```

Native methods Scroll/Draw/Undo/Save can be accessed from React Native side by using ref.

```typescript
const pdfRef = useRef(null);

const onPressScroll = () => {  
  pdfRef.current?.scrollAction();
};

<PDFEditorView
  ref={pdfRef}
/>
```

## Contributing

See the [contributing guide](.github/CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

## License

MIT
