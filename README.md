# react-native-pdf-editor

React Native PDF editor (iOS & Android)

## Installation

```sh
npm install @ornament-health/react-native-pdf-editor
cd ios && pod install   # for iOS
```

## Usage

Pass an array of file paths and brush settings (nested under `drawLine`), plus icon colors (nested under `icons`). Set background color via `style`; native views are transparent.

```typescript
import { PDFEditorView } from '@ornament-health/react-native-pdf-editor';

const options = {
  files: ['/path/to/file1.pdf', '/path/to/file2.png'], // required array of paths
  drawLine: {
    color: '#4287f5', // optional, defaults to '#555555'
    width: 40, // optional, defaults to 10
  },
  icons: {
    unselectedColor: '#FFFFFF', // optional, defaults to '#FFFFFF'
    undoRedoColor: '#FFFFFF', // optional, defaults to '#FFFFFF'
  },
};

<PDFEditorView
  ref={pdfRef}
  style={styles.pdfView}
  options={options}
  onSavePDF={handleSavePDF}
  onSelectionChange={handleSelectionChange}
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

`onSelectionChange` fires whenever the set of pages that would be saved changes
(documents loaded/appended, or a page's skip-checkbox toggled). It reports the
aggregate number of included pages across every document, so a value of `0`
means the user has excluded every page in every document. Use it to disable a
Send/Save action before the user can trigger an empty save.

```typescript
const handleSelectionChange = (selectedCount: number) => {
  setCanSave(selectedCount > 0);
};
```

Available ref commands: `setEditMode(isEdit)`, `undoAction`, `clearAction`, `cancelEditAction`, `saveAction`.

```typescript
const pdfRef = useRef(null);

const onEnterEdit = () => pdfRef.current?.setEditMode(true);
const onPressUndo = () => pdfRef.current?.undoAction();
const onPressClear = () => pdfRef.current?.clearAction();
const onCancelEdit = () => pdfRef.current?.cancelEditAction();
const onPressSave = () => pdfRef.current?.saveAction();
```

## Edit mode

The view starts in view mode (pan/zoom, no drawing). Drawing, undo and redo are
only active in edit mode.

- `setEditMode(true)` enables drawing. `setEditMode(false)` leaves edit mode and
  commits the current strokes as the new baseline.
- `undoAction()` / `clearAction()` affect strokes made in the current edit
  session. Redo is available via the in-canvas undo/redo buttons shown in edit
  mode (colored via `icons.undoRedoColor`).
- `cancelEditAction()` discards every stroke added since the last commit,
  restoring the previously accepted drawings.

## Multiple documents & page selection

`files` may contain several paths. A thumbnail switcher at the bottom lets the
user move between documents. Tap the circular badge on a page to toggle whether
it is included in the saved output (`icons.unselectedColor` styles the badge).

`onSavePDF` returns one saved path per included document; a document whose pages
are all excluded is omitted, and if nothing is saved the callback receives
`null`.

## Contributing

See the [contributing guide](.github/CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

## License

MIT
