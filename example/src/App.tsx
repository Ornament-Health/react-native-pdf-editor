import React, { ComponentRef, useRef, useState } from 'react';
import {
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
  SafeAreaView,
} from 'react-native';
import DocumentPicker, {
  types as DocTypes,
} from 'react-native-document-picker';
import {
  PDFEditorView,
  RNComponentProps,
} from '@ornament-health/react-native-pdf-editor';

type PDFEVRef = ComponentRef<typeof PDFEditorView>;

export default function App() {
  const pdfRef = useRef<PDFEVRef>(null);

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
        mode: 'open',
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

  const options: RNComponentProps['options'] = {
    filePath: selectedFiles,
    lineColor: '#ea1d54',
    lineWidth: 10,
  };

  return (
    <SafeAreaView style={styles.container}>
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
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#313131',
  },
  centerWrapper: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  button: {
    backgroundColor: '#ea1d54',
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
