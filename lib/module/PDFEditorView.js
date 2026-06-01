function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
import React, { forwardRef, useImperativeHandle, useMemo, useRef } from 'react';
import { Platform, requireNativeComponent, UIManager, findNodeHandle } from 'react-native';
const ComponentName = 'RNPDFEditorView';
const DEFAULT_OPTIONS = {
  drawLine: {
    color: '#555555',
    width: 10
  },
  icons: {
    unselectedColor: '#FFFFFF',
    undoRedoColor: '#FFFFFF'
  }
};
const RNComponentViewManager = requireNativeComponent(ComponentName);
export const PDFEditorView = /*#__PURE__*/forwardRef(({
  onSavePDF,
  options,
  ...props
}, extRef) => {
  const mergedOptions = useMemo(() => ({
    ...DEFAULT_OPTIONS,
    ...options,
    drawLine: {
      ...DEFAULT_OPTIONS.drawLine,
      ...(options.drawLine ?? {})
    },
    icons: {
      ...DEFAULT_OPTIONS.icons,
      ...(options.icons ?? {})
    }
  }), [options]);
  useImperativeHandle(extRef, () => ({
    undoAction,
    clearAction,
    cancelEditAction,
    saveAction,
    setEditMode
  }));
  const componentRef = useRef(null);
  const setEditMode = isEdit => {
    if (componentRef && componentRef.current) {
      UIManager.dispatchViewManagerCommand(findNodeHandle(componentRef.current), Platform.OS === 'ios' ? UIManager.getViewManagerConfig(ComponentName).Commands.setEditMode : 'setEditMode', [isEdit]);
    }
  };
  const undoAction = () => {
    if (componentRef && componentRef.current) {
      UIManager.dispatchViewManagerCommand(findNodeHandle(componentRef.current), Platform.OS === 'ios' ? UIManager.getViewManagerConfig(ComponentName).Commands.undoAction : 'undoAction', []);
    }
  };
  const clearAction = () => {
    if (componentRef && componentRef.current) {
      UIManager.dispatchViewManagerCommand(findNodeHandle(componentRef.current), Platform.OS === 'ios' ? UIManager.getViewManagerConfig(ComponentName).Commands.clearAction : 'clearAction', []);
    }
  };
  const cancelEditAction = () => {
    if (componentRef && componentRef.current) {
      UIManager.dispatchViewManagerCommand(findNodeHandle(componentRef.current), Platform.OS === 'ios' ? UIManager.getViewManagerConfig(ComponentName).Commands.cancelEditAction : 'cancelEditAction', []);
    }
  };
  const saveAction = () => {
    if (componentRef && componentRef.current) {
      UIManager.dispatchViewManagerCommand(findNodeHandle(componentRef.current), Platform.OS === 'ios' ? UIManager.getViewManagerConfig(ComponentName).Commands.saveAction : 'saveAction', []);
    }
  };
  const getURLs = nativeEvent => {
    const value = nativeEvent.url;
    return Array.isArray(value) ? value : null;
  };
  return /*#__PURE__*/React.createElement(RNComponentViewManager, _extends({
    ref: componentRef,
    options: mergedOptions,
    onSavePDF: event => onSavePDF === null || onSavePDF === void 0 ? void 0 : onSavePDF(getURLs(event.nativeEvent))
  }, props));
});
//# sourceMappingURL=PDFEditorView.js.map