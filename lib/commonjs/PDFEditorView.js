"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.PDFEditorView = void 0;
var _react = _interopRequireWildcard(require("react"));
var _reactNative = require("react-native");
function _interopRequireWildcard(e, t) { if ("function" == typeof WeakMap) var r = new WeakMap(), n = new WeakMap(); return (_interopRequireWildcard = function (e, t) { if (!t && e && e.__esModule) return e; var o, i, f = { __proto__: null, default: e }; if (null === e || "object" != typeof e && "function" != typeof e) return f; if (o = t ? n : r) { if (o.has(e)) return o.get(e); o.set(e, f); } for (const t in e) "default" !== t && {}.hasOwnProperty.call(e, t) && ((i = (o = Object.defineProperty) && Object.getOwnPropertyDescriptor(e, t)) && (i.get || i.set) ? o(f, t, i) : f[t] = e[t]); return f; })(e, t); }
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
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
const RNComponentViewManager = (0, _reactNative.requireNativeComponent)(ComponentName);
const PDFEditorView = exports.PDFEditorView = /*#__PURE__*/(0, _react.forwardRef)(({
  onSavePDF,
  options,
  ...props
}, extRef) => {
  const mergedOptions = (0, _react.useMemo)(() => ({
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
  (0, _react.useImperativeHandle)(extRef, () => ({
    undoAction,
    clearAction,
    cancelEditAction,
    saveAction,
    setEditMode
  }));
  const componentRef = (0, _react.useRef)(null);
  const setEditMode = isEdit => {
    if (componentRef && componentRef.current) {
      _reactNative.UIManager.dispatchViewManagerCommand((0, _reactNative.findNodeHandle)(componentRef.current), _reactNative.Platform.OS === 'ios' ? _reactNative.UIManager.getViewManagerConfig(ComponentName).Commands.setEditMode : 'setEditMode', [isEdit]);
    }
  };
  const undoAction = () => {
    if (componentRef && componentRef.current) {
      _reactNative.UIManager.dispatchViewManagerCommand((0, _reactNative.findNodeHandle)(componentRef.current), _reactNative.Platform.OS === 'ios' ? _reactNative.UIManager.getViewManagerConfig(ComponentName).Commands.undoAction : 'undoAction', []);
    }
  };
  const clearAction = () => {
    if (componentRef && componentRef.current) {
      _reactNative.UIManager.dispatchViewManagerCommand((0, _reactNative.findNodeHandle)(componentRef.current), _reactNative.Platform.OS === 'ios' ? _reactNative.UIManager.getViewManagerConfig(ComponentName).Commands.clearAction : 'clearAction', []);
    }
  };
  const cancelEditAction = () => {
    if (componentRef && componentRef.current) {
      _reactNative.UIManager.dispatchViewManagerCommand((0, _reactNative.findNodeHandle)(componentRef.current), _reactNative.Platform.OS === 'ios' ? _reactNative.UIManager.getViewManagerConfig(ComponentName).Commands.cancelEditAction : 'cancelEditAction', []);
    }
  };
  const saveAction = () => {
    if (componentRef && componentRef.current) {
      _reactNative.UIManager.dispatchViewManagerCommand((0, _reactNative.findNodeHandle)(componentRef.current), _reactNative.Platform.OS === 'ios' ? _reactNative.UIManager.getViewManagerConfig(ComponentName).Commands.saveAction : 'saveAction', []);
    }
  };
  const getURLs = nativeEvent => {
    const value = nativeEvent.url;
    return Array.isArray(value) ? value : null;
  };
  return /*#__PURE__*/_react.default.createElement(RNComponentViewManager, _extends({
    ref: componentRef,
    options: mergedOptions,
    onSavePDF: event => onSavePDF === null || onSavePDF === void 0 ? void 0 : onSavePDF(getURLs(event.nativeEvent))
  }, props));
});
//# sourceMappingURL=PDFEditorView.js.map