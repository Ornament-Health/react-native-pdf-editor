# react-native-pdf-editor

Native iOS PDF editor

## Installation

```sh
npm install @ornament-health/react-native-pdf-editor
```

## Usage
  In order to start using RNPDFEditorView, you should create configuration for it with local link to document and some styling parameters. You can handle visibility of native toolbar, backgrounf color, line color and width from React Native side.  

```js
import { RNPDFEditorView } from "@ornament-health/react-native-pdf-editor";

// ...

let options = {
    fileName: source,
    isToolBarHidden: false,
    viewBackgroundColor: '#40a35f',
    lineColor: '#4287f5',
    lineWidth: 40
  }  

<PDFEditorView 
        ref={pdfRef}
        style={styles.pdfView}
        options={options}
        onSavePDF={handleSavePDF}
        scrollAction={function (): void {
          throw new Error('Function not implemented.');
        } } 
        drawAction={function (): void {
          throw new Error('Function not implemented.');
        } } 
        undoAction={function (): void {
          throw new Error('Function not implemented.');
        } } 
        saveAction={function (): void {
          throw new Error('Function not implemented.');
        } } /> 
```
  Saving option can return 'null' if there was errors at native side, otherwise it will return string contains local path to saved document. Note, that after every saving action document stores at apps Document Directory, so removing unnecessary local copies should be provided at React Native side.
  
```js
  const handleSavePDF = (e: string | null) => {
    if (e === null) {
      console.log('got null value for url:', e);
    } else {
      console.log('got url:', e);
    }        
  };
```

  Native methods Scroll/Draw/Undo/Save can be accessed from React Native side by using ref.

```js
const pdfRef = useRef(null);

const onPressScroll = () => {  
  pdfRef.current?.scrollAction();
};

<TouchableOpacity
  style={styles.button}
  onPress={onPressScroll}>
  <Text style={styles.buttonText}>Scroll</Text>
</TouchableOpacity>
```

## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

## License

MIT
