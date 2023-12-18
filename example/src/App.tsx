import React, { ComponentRef, useRef } from 'react';
import RNFS from 'react-native-fs';
import {
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
  Platform,
} from 'react-native';
import { PDFEditorView } from '@ornament-health/react-native-pdf-editor';

type PDFEVRef = ComponentRef<typeof PDFEditorView>;

export default function App() {
  const pdfRef = useRef<PDFEVRef>(null);

  const sourcePDF =
    Platform.OS === 'ios'
      ? 'file://' + RNFS.MainBundlePath + '/example.pdf'
      : RNFS.ExternalDirectoryPath + '/book.pdf';

  // const sourceJPG =
  //   Platform.OS === 'ios'
  //     ? RNFS.MainBundlePath + '/example.jpg'
  //     : RNFS.MainBundlePath + '/example.jpg';

  enum CanvasType {
    Image = 'image',
    PDF = 'pdf',
  }

  const options = {
    fileName: sourcePDF,
    canvasType: CanvasType.PDF,
    isToolBarHidden: false,
    viewBackgroundColor: '#40a35f',
    lineColor: '#4287f5',
    lineWidth: 40,
  };

  const handleSavePDF = (e: string | null) => {
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

  return (
    <View style={styles.container}>
      <View style={styles.topView}>
        <View style={styles.buttonContainer}>
          <TouchableOpacity style={styles.button} onPress={onPressUndo}>
            <Text style={styles.buttonText}>Undo</Text>
          </TouchableOpacity>
        </View>
        <View style={styles.buttonContainer}>
          <TouchableOpacity style={styles.button} onPress={onPressClear}>
            <Text style={styles.buttonText}>Clear</Text>
          </TouchableOpacity>
        </View>
        <View style={styles.buttonContainer}>
          <TouchableOpacity style={styles.button} onPress={onPressSave}>
            <Text style={styles.buttonText}>Save</Text>
          </TouchableOpacity>
        </View>
      </View>
      <PDFEditorView
        ref={pdfRef}
        style={styles.box}
        options={options}
        onSavePDF={handleSavePDF}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 40,
  },
  topView: {
    height: '10%',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },
  box: {
    width: '100%',
    height: '90%',
  },
  buttonContainer: {
    flex: 1,
  },
  button: {
    marginRight: 5,
    marginLeft: 5,
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
});
