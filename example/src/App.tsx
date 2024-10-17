import React, { ComponentRef, useRef, useState } from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import DocumentPicker, {
  types as DocTypes,
} from 'react-native-document-picker';
import { PDFEditorView } from '@ornament-health/react-native-pdf-editor';

type PDFEVRef = ComponentRef<typeof PDFEditorView>;

export default function App() {
  const pdfRef = useRef<PDFEVRef>(null);

  enum CanvasType {
    Image = 'image',
    PDF = 'pdf',
  }

  const [selectedFiles, setSelectedFiles] = useState<string[]>([]);

  const handleSavePDF = (e: string[] | null) => {
    if (e === null) {
      console.log('got null value for url:', e);
    } else {
      console.log('got url:', e);
    }
  };

  const onPressUndo = () => {
    pdfRef.current?.undoAction();
  };

  const onPressClear = () => {
    pdfRef.current?.clearAction();
  };

  const onPressSave = () => {
    pdfRef.current?.saveAction();
  };

  const pickFiles = async () => {
    try {
      const results = await DocumentPicker.pick({
        allowMultiSelection: true,
        type: [DocTypes.images, DocTypes.pdf],
        mode: 'import',
      });

      const resolvedPaths: string[] = [];

      for (const res of results) {
        resolvedPaths.push(res.uri);
      }

      if (resolvedPaths.length > 0) {
        setSelectedFiles(resolvedPaths);
      }
    } catch (err: any) {
      if (DocumentPicker.isCancel(err)) {
        console.log('User cancelled file picker');
      } else {
        console.warn('DocumentPicker error', err);
      }
    }
  };

  const options = {
    filePath: selectedFiles,
    canvasType: selectedFiles.some((p) => p.toLowerCase().endsWith('.pdf'))
      ? CanvasType.PDF
      : CanvasType.Image,
    isToolBarHidden: true,
    viewBackgroundColor: '#40a35f',
    lineColor: '#4287f5',
    lineWidth: 40,
  };

  return (
    <View style={styles.container}>
      {selectedFiles.length > 0 ? (
        <>
          <PDFEditorView
            ref={pdfRef}
            style={styles.editor}
            options={options}
            onSavePDF={handleSavePDF}
          />
          <View style={styles.controlPanel}>
            <TouchableOpacity
              style={[styles.button, styles.controlButton]}
              onPress={onPressUndo}
            >
              <Text style={styles.buttonText}>Undo</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.button, styles.controlButton]}
              onPress={onPressClear}
            >
              <Text style={styles.buttonText}>Clear</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.button, styles.controlButton]}
              onPress={onPressSave}
            >
              <Text style={styles.buttonText}>Save</Text>
            </TouchableOpacity>
          </View>
        </>
      ) : (
        <View style={styles.centerWrapper}>
          <TouchableOpacity
            style={[styles.button, styles.pickButton]}
            onPress={pickFiles}
          >
            <Text style={styles.buttonText}>Pick files</Text>
          </TouchableOpacity>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingTop: 60,
    paddingBottom: 24,
    backgroundColor: '#F5FCFF',
  },
  centerWrapper: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  button: {
    backgroundColor: '#1E6738',
    height: 40,
    borderRadius: 6,
    justifyContent: 'center',
    alignItems: 'center',
  },
  buttonText: {
    color: '#fff',
    textAlign: 'center',
    fontSize: 17,
  },
  pickButton: {
    width: '60%',
  },
  editor: {
    flex: 1,
  },
  controlPanel: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 10,
  },
  controlButton: {
    width: '30%',
  },
});
