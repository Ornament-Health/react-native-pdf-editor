# react-native-pdf-editor

React Native PDF editor (iOS & Android)

## Installation

```sh
npm install @ornament-health/react-native-pdf-editor
cd ios && pod install   # для iOS
```

## Usage

Pass an array of file paths and brush settings. Set background color via `style`; native views are transparent.

```typescript
import { PDFEditorView } from '@ornament-health/react-native-pdf-editor';

const options = {
  filePath: ['/path/to/file1.pdf', '/path/to/file2.png'], // required array of paths
  lineColor: '#4287f5', // optional, defaults to '#FF0000'
  lineWidth: 40, // optional, defaults to 5
};

<PDFEditorView
  ref={pdfRef}
  style={styles.pdfView}
  options={options}
  onSavePDF={handleSavePDF}
/>;
```

`onSavePDF` receives a list of saved file paths or `null` if native saving failed. Saved files stay in Documents/External Files; clean up unused copies on the RN side.

```typescript
const handleSavePDF = (urls: string[] | null) => {
  if (urls === null) {
    console.log('save failed');
  } else {
    console.log('saved urls:', urls);
  }
};
```

Available ref commands: `undoAction`, `clearAction`, `saveAction`.

```typescript
const pdfRef = useRef(null);

const onPressUndo = () => pdfRef.current?.undoAction();
const onPressClear = () => pdfRef.current?.clearAction();
const onPressSave = () => pdfRef.current?.saveAction();
```

## Contributing

See the [contributing guide](.github/CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

## License

MIT
