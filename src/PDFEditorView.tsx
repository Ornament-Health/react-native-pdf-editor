import React, {
  forwardRef,
  useImperativeHandle,
  useMemo,
  useRef,
  SyntheticEvent,
} from 'react';
import {
  Platform,
  requireNativeComponent,
  ViewStyle,
  StyleProp,
  UIManager,
  findNodeHandle,
} from 'react-native';
import type { Float } from 'react-native/Libraries/Types/CodegenTypes';

const ComponentName = 'RNPDFEditorView';

const DEFAULT_OPTIONS = {
  drawLine: {
    color: '#555555',
    width: 10 as Float,
  },
  icons: {
    unselectedColor: '#FFFFFF',
    undoRedoColor: '#FFFFFF',
  },
};

export interface PDFEditorOptions {
  files: string[];
  drawLine?: {
    color?: string;
    width?: Float;
  };
  icons?: {
    unselectedColor?: string;
    undoRedoColor?: string;
  };
}

interface RNComponentProps {
  style: StyleProp<ViewStyle>;
  options: PDFEditorOptions;
  onSavePDF?: (url: string[] | null) => void;
}

interface ExtRef {
  undoAction(): void;
  clearAction(): void;
  cancelEditAction(): void;
  saveAction(): void;
  setEditMode(isEdit: boolean): void;
}

interface RNComponentManagerProps extends Omit<RNComponentProps, 'onSavePDF'> {
  onSavePDF(event: SyntheticEvent): void;
}

const RNComponentViewManager =
  requireNativeComponent<RNComponentManagerProps>(ComponentName);
type PDFEVRef = React.ComponentRef<typeof RNComponentViewManager>;

export const PDFEditorView = forwardRef<ExtRef, RNComponentProps>(
  ({ onSavePDF, options, ...props }, extRef) => {
    const mergedOptions = useMemo(
      () => ({
        ...DEFAULT_OPTIONS,
        ...options,
        drawLine: {
          ...DEFAULT_OPTIONS.drawLine,
          ...(options.drawLine ?? {}),
        },
        icons: {
          ...DEFAULT_OPTIONS.icons,
          ...(options.icons ?? {}),
        },
      }),
      [options]
    );
    useImperativeHandle(extRef, () => ({
      undoAction,
      clearAction,
      cancelEditAction,
      saveAction,
      setEditMode,
    }));
    const componentRef = useRef<PDFEVRef>(null);

    const setEditMode = (isEdit: boolean) => {
      if (componentRef && componentRef.current) {
        UIManager.dispatchViewManagerCommand(
          findNodeHandle(componentRef.current),
          Platform.OS === 'ios'
            ? (UIManager.getViewManagerConfig(ComponentName).Commands
                .setEditMode as number)
            : 'setEditMode',
          [isEdit]
        );
      }
    };

    const undoAction = () => {
      if (componentRef && componentRef.current) {
        UIManager.dispatchViewManagerCommand(
          findNodeHandle(componentRef.current),
          Platform.OS === 'ios'
            ? (UIManager.getViewManagerConfig(ComponentName).Commands
                .undoAction as number)
            : 'undoAction',
          []
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
          []
        );
      }
    };

    const cancelEditAction = () => {
      if (componentRef && componentRef.current) {
        UIManager.dispatchViewManagerCommand(
          findNodeHandle(componentRef.current),
          Platform.OS === 'ios'
            ? (UIManager.getViewManagerConfig(ComponentName).Commands
                .cancelEditAction as number)
            : 'cancelEditAction',
          []
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
          []
        );
      }
    };

    const getURLs = (nativeEvent: { url?: string[] | null }): string[] | null => {
      const value = nativeEvent.url;
      return Array.isArray(value) ? value : null;
    };

    return (
      <RNComponentViewManager
        ref={componentRef}
        options={mergedOptions}
        onSavePDF={(event: SyntheticEvent) =>
          onSavePDF?.(getURLs(event.nativeEvent as { url?: string[] | null }))
        }
        {...props}
      />
    );
  }
);
