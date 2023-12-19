import React, {
  forwardRef,
  useImperativeHandle,
  useRef,
  SyntheticEvent,
} from 'react';
import {
  Platform,
  requireNativeComponent,
  ViewStyle,
  UIManager,
  findNodeHandle,
} from 'react-native';
import type { Float } from 'react-native/Libraries/Types/CodegenTypes';

const ComponentName = 'RNPDFEditorView';

interface ExtRef {
  undoAction(): void;
  clearAction(): void;
  saveAction(): void;
}

interface RNComponentProps {
  style: ViewStyle;
  options: {
    filePath: string[];
    canvasType: string;
    isToolBarHidden?: boolean;
    viewBackgroundColor?: string;
    lineColor?: string;
    lineWidth?: Float;
  };
  onSavePDF?(url: string[] | null): void;
}

export interface RNComponentManagerProps
  extends Omit<RNComponentProps, 'onSavePDF'>,
    ExtRef {
  onSavePDF(event: SyntheticEvent): void;
}

const RNComponentViewManager =
  requireNativeComponent<RNComponentManagerProps>(ComponentName);
type PDFEVRef = React.ComponentRef<typeof RNComponentViewManager>;

export const PDFEditorView = forwardRef<ExtRef, RNComponentProps>(
  ({ onSavePDF, ...props }, extRef) => {
    useImperativeHandle(extRef, () => ({
      undoAction,
      clearAction,
      saveAction,
    }));
    const componentRef = useRef<PDFEVRef>(null);

    const undoAction = () => {
      if (componentRef && componentRef.current) {
        UIManager.dispatchViewManagerCommand(
          findNodeHandle(componentRef.current),
          Platform.OS === 'ios'
            ? (UIManager.getViewManagerConfig(ComponentName).Commands
                .undoAction as number)
            : 'undoAction',
          undefined
        );
      }
    };

    const clearAction = () => {
      if (componentRef && componentRef.current) {
        UIManager.dispatchViewManagerCommand(
          findNodeHandle(componentRef.current),
          Platform.OS === 'ios'
            ? (UIManager.getViewManagerConfig(ComponentName).Commands
                .clearAction as number)
            : 'clearAction',
          undefined
        );
      }
    };

    const saveAction = () => {
      if (componentRef && componentRef.current) {
        UIManager.dispatchViewManagerCommand(
          findNodeHandle(componentRef.current),
          Platform.OS === 'ios'
            ? (UIManager.getViewManagerConfig(ComponentName).Commands
                .saveAction as number)
            : 'saveAction',
          undefined
        );
      }
    };

    const getURLString = (nativeEvent: any) => {
      if (nativeEvent.hasOwnProperty('url')) {
        return nativeEvent.url;
      }
      return null;
    };

    return (
      <RNComponentViewManager
        ref={componentRef}
        undoAction={undoAction}
        clearAction={clearAction}
        saveAction={saveAction}
        onSavePDF={(event: SyntheticEvent) =>
          onSavePDF && onSavePDF(getURLString(event.nativeEvent))
        }
        {...props}
      />
    );
  }
);
