import React from 'react';
import { ViewStyle, StyleProp } from 'react-native';
import type { Float } from 'react-native/Libraries/Types/CodegenTypes';
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
export declare const PDFEditorView: React.ForwardRefExoticComponent<RNComponentProps & React.RefAttributes<ExtRef>>;
export {};
//# sourceMappingURL=PDFEditorView.d.ts.map