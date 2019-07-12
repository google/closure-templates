/**
 * @fileoverview Helper utilities for incremental dom code generation in Soy.
 * Copyright 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @suppress {checkTypes,constantProperty,extraRequire,missingOverride,missingReturn,unusedPrivateMembers,uselessCode}
 * checked by tsc
 */
goog.module('google3.javascript.template.soy.soyutils_idom');
var module = module || {id: 'javascript/template/soy/soyutils_idom.closure.js'};
module = module;
exports = {};
const tsickle_soy_1 = goog.requireType('goog.soy');
const tsickle_SanitizedContent_2 = goog.requireType('goog.soy.data.SanitizedContent');
const tsickle_SanitizedContentKind_3 = goog.requireType('goog.soy.data.SanitizedContentKind');
const tsickle_SanitizedHtml_4 = goog.requireType('goog.soy.data.SanitizedHtml');
const tsickle_SanitizedHtmlAttribute_5 = goog.requireType('goog.soy.data.SanitizedHtmlAttribute');
const tsickle_SanitizedJs_6 = goog.requireType('goog.soy.data.SanitizedJs');
const tsickle_SanitizedUri_7 = goog.requireType('goog.soy.data.SanitizedUri');
const tsickle_string_8 = goog.requireType('goog.string');
const tsickle_goog_soy_9 = goog.requireType('soy');
const tsickle_checks_10 = goog.requireType('soy.checks');
const tsickle_VERY_UNSAFE_11 = goog.requireType('soydata.VERY_UNSAFE');
const tsickle_incremental_dom_12 = goog.requireType('incrementaldom');
const tsickle_api_idom_13 = goog.requireType('google3.javascript.template.soy.api_idom');
const tsickle_element_lib_idom_14 = goog.requireType('google3.javascript.template.soy.element_lib_idom');
const tsickle_global_15 = goog.requireType('google3.javascript.template.soy.global');
const googSoy = goog.require('goog.soy');  // from //javascript/closure/soy
// from //javascript/closure/soy:data
const goog_goog_soy_data_SanitizedContentKind_1 = goog.require('goog.soy.data.SanitizedContentKind');  // from //javascript/closure/soy:data
// from //javascript/closure/soy:data
const goog_goog_soy_data_SanitizedHtml_1 = goog.require('goog.soy.data.SanitizedHtml');  // from //javascript/closure/soy:data
// from //javascript/closure/soy:data
const googString = goog.require('goog.string');  // from //javascript/closure/string
// from //javascript/closure/string
const soy = goog.require('soy');  // from //javascript/template/soy:soy_usegoog_js
// from //javascript/template/soy:soy_usegoog_js
const goog_soy_checks_1 = goog.require('soy.checks');  // from //javascript/template/soy:checks
// from //javascript/template/soy:checks
const goog_soydata_VERY_UNSAFE_1 = goog.require('soydata.VERY_UNSAFE');  // from //javascript/template/soy:soy_usegoog_js
// from //javascript/template/soy:soy_usegoog_js
const {attributes, getKey, isDataInitialized, setKeyAttributeName, currentPointer, elementOpen, elementOpenStart, elementOpenEnd, elementClose, text, attr, skip, currentElement, skipNode} = goog.require('incrementaldom');  // from //third_party/javascript/incremental_dom:incrementaldom
// from //third_party/javascript/incremental_dom:incrementaldom
const api_idom_1 = goog.require('google3.javascript.template.soy.api_idom');
const element_lib_idom_1 = goog.require('google3.javascript.template.soy.element_lib_idom');
exports.$SoyElement = element_lib_idom_1.SoyElement;
const global_1 = goog.require('google3.javascript.template.soy.global');
// Declare properties that need to be applied not as attributes but as
// actual DOM properties.
/** @type {!tsickle_api_idom_13.IncrementalDomRenderer} */
const defaultIdomRenderer = new api_idom_1.IncrementalDomRenderer();
/**
 * @typedef {function(!tsickle_api_idom_13.IncrementalDomRenderer, ?, ?): void}
 */
var IdomTemplate;
/** @typedef {function(?, ?): (string|!tsickle_SanitizedContent_2)} */
var SoyTemplate;
/** @typedef {function(!tsickle_api_idom_13.IncrementalDomRenderer): void} */
var LetFunction;
/**
 * @typedef {(function(!tsickle_api_idom_13.IncrementalDomRenderer, ?, ?):
 * void|function(?, ?): (string|!tsickle_SanitizedContent_2))}
 */
var Template;
// tslint:disable-next-line:no-any
attributes['checked'] =
    (/**
     * @param {!Element} el
     * @param {string} name
     * @param {?} value
     * @return {void}
     */
        (el, name, value) => {
      // We don't use !!value because:
      // 1. If value is '' (this is the case where a user uses <div checked
      // />),
      //    the checked value should be true, but '' is falsy.
      // 2. If value is 'false', the checked value should be false, but
      //    'false' is truthy.
      el.setAttribute('checked', value);
      ((/** @type {!HTMLInputElement} */ (el))).checked =
          !(value === false || value === 'false' || value === undefined);
    });
// tslint:disable-next-line:no-any
attributes['value'] = (/**
 * @param {!Element} el
 * @param {string} name
 * @param {?} value
 * @return {void}
 */
    (el, name, value) => {
  ((/** @type {!HTMLInputElement} */ (el))).value =
      value;
  el.setAttribute('value', value);
});
// Soy uses the {key} command syntax, rather than HTML attributes, to
// indicate element keys.
setKeyAttributeName(null);
/**
 * Returns the template object stored in the currentPointer element if it is the
 * correct type.  Otherwise, returns null.
 * @template T
 * @param {!tsickle_api_idom_13.IncrementalDomRenderer} idom
 * @param {function(new:T)} elementClassCtor
 * @param {string} firstElementKey
 * @return {(null|T)}
 */
function tryGetElement(idom, elementClassCtor, firstElementKey) {
  /** @type {(null|!Node)} */
  let currentPointer = idom.currentPointer();
  while (currentPointer != null) {
    /** @type {(null|!tsickle_element_lib_idom_14.SoyElement<*, ?>)} */
    const el = global_1.getSoyUntyped(currentPointer);
    if (el instanceof elementClassCtor && isDataInitialized(currentPointer)) {
      /** @type {string} */
      const currentPointerKey =
          (/** @type {string} */ (getKey(currentPointer)));
      if (api_idom_1.isMatchingKey(
          api_idom_1.serializeKey(firstElementKey) +
          idom.getCurrentKeyStack(),
          currentPointerKey)) {
        return el;
      }
    }
    currentPointer = currentPointer.nextSibling;
  }
  return null;
}
exports.$$tryGetElement = tryGetElement;
// tslint:disable-next-line:no-any Attaching arbitrary attributes to function.
/**
 * @param {?} idomFn
 * @return {!tsickle_element_lib_idom_14.IdomFunction}
 */
function makeHtml(idomFn) {
  idomFn.toString =
      (/**
       * @param {!tsickle_api_idom_13.IncrementalDomRenderer=} renderer
       * @return {string}
       */
          (renderer = defaultIdomRenderer) => htmlToString(idomFn, renderer));
  idomFn.toBoolean = (/**
   * @return {boolean}
   */
      () => toBoolean(idomFn));
  idomFn.contentKind = goog_goog_soy_data_SanitizedContentKind_1.HTML;
  return (/** @type {!tsickle_element_lib_idom_14.IdomFunction} */ (idomFn));
}
exports.$$makeHtml = makeHtml;
// tslint:disable-next-line:no-any Attaching arbitrary attributes to function.
/**
 * @param {?} idomFn
 * @return {!tsickle_element_lib_idom_14.IdomFunction}
 */
function makeAttributes(idomFn) {
  idomFn.toString = (/**
   * @return {string}
   */
      () => attributesToString(idomFn));
  idomFn.toBoolean = (/**
   * @return {boolean}
   */
      () => toBoolean(idomFn));
  idomFn.contentKind = goog_goog_soy_data_SanitizedContentKind_1.ATTRIBUTES;
  return (/** @type {!tsickle_element_lib_idom_14.IdomFunction} */ (idomFn));
}
exports.$$makeAttributes = makeAttributes;
/**
 * TODO(tomnguyen): Issue a warning in these cases so that users know that
 * expensive behavior is happening.
 * @param {function(!tsickle_api_idom_13.IncrementalDomRenderer): void} fn
 * @param {!tsickle_api_idom_13.IncrementalDomRenderer=} renderer
 * @return {string}
 */
function htmlToString(fn, renderer = defaultIdomRenderer) {
  /** @type {!HTMLDivElement} */
  const el = document.createElement('div');
  api_idom_1.patch(
      el,
      (/**
       * @return {void}
       */
          () => fn(renderer)));
  return el.innerHTML;
}
exports.$$htmlToString = htmlToString;
/**
 * @param {function(*=): void} fn
 * @return {function(*=): void}
 */
function attributesFactory(fn) {
  return (/**
   * @return {void}
   */
      () => {
    elementOpenStart('div');
    fn(defaultIdomRenderer);
    elementOpenEnd();
    elementClose('div');
  });
}
/**
 * TODO(tomnguyen): Issue a warning in these cases so that users know that
 * expensive behavior is happening.
 * @param {function(*=): void} fn
 * @return {string}
 */
function attributesToString(fn) {
  /** @type {function(*=): void} */
  const elFn = attributesFactory(fn);
  /** @type {!HTMLDivElement} */
  const el = document.createElement('div');
  api_idom_1.patchOuter(el, elFn);
  /** @type {!Array<string>} */
  const s = [];
  for (let i = 0; i < el.attributes.length; i++) {
    s.push(`${el.attributes[i].name}=${el.attributes[i].value}`);
  }
  // The sort is important because attribute order varies per browser.
  return s.sort().join(' ');
}
/**
 * @param {!tsickle_element_lib_idom_14.IdomFunction} fn
 * @return {boolean}
 */
function toBoolean(fn) {
  return fn.toString().length > 0;
}
/**
 * Calls an expression in case of a function or outputs it as text content.
 * @param {!tsickle_api_idom_13.IncrementalDomRenderer} renderer
 * @param {*} expr
 * @return {void}
 */
function renderDynamicContent(renderer, expr) {
  // TODO(lukes): check content kind == html
  if (typeof expr === 'function') {
    // The Soy compiler will validate the content kind of the parameter.
    expr(renderer);
  } else {
    text(String(expr));
  }
}
/**
 * Matches an HTML attribute name value pair.
 * Name is in group 1.  Value, if present, is in one of group (2,3,4)
 * depending on how it's quoted.
 *
 * This RegExp was derived from visual inspection of
 *   html.spec.whatwg.org/multipage/parsing.html#before-attribute-name-state
 * and following states.
 * @type {!RegExp}
 */
const htmlAttributeRegExp =
    /([^\t\n\f\r />=]+)[\t\n\f\r ]*(?:=[\t\n\f\r ]*(?:"([^"]*)"?|'([^']*)'?|([^\t\n\f\r >]*)))?/g;
/**
 * @param {string} attributes
 * @return {!Array<!Array<string>>}
 */
function splitAttributes(attributes) {
  /** @type {!Array<!Array<string>>} */
  const nameValuePairs = [];
  String(attributes)
      .replace(
          htmlAttributeRegExp,
          (/**
           * @param {string} _
           * @param {?} name
           * @param {?} dq
           * @param {?} sq
           * @param {?} uq
           * @return {string}
           */
              (_, name, dq, sq, uq) => {
            nameValuePairs.push(
                [name, googString.unescapeEntities(dq || sq || uq || '')]);
            return ' ';
          }));
  return nameValuePairs;
}
/**
 * Calls an expression in case of a function or outputs it as text content.
 * @template A, B
 * @param {!tsickle_api_idom_13.IncrementalDomRenderer} incrementaldom
 * @param {(function(!tsickle_api_idom_13.IncrementalDomRenderer, A, B):
 *     void|function(A, B): (string|!tsickle_SanitizedContent_2))} expr
 * @param {A} data
 * @param {B} ij
 * @return {void}
 */
function callDynamicAttributes(
    incrementaldom,
    // tslint:disable-next-line:no-any
    expr, data, ij) {
  // tslint:disable-next-line:no-any Attaching arbitrary attributes to function.
  /** @type {?} */
  const type = ((/** @type {!tsickle_element_lib_idom_14.IdomFunction} */ (
      (/** @type {?} */ (expr)))))
      .contentKind;
  if (type === goog_goog_soy_data_SanitizedContentKind_1.ATTRIBUTES) {
    ((/**
     @type {function(!tsickle_api_idom_13.IncrementalDomRenderer, A, B):
             void}
     */
        (expr)))(incrementaldom, data, ij);
  } else {
    /** @type {(string|!tsickle_SanitizedHtmlAttribute_5)} */
    let val;
    if (type === goog_goog_soy_data_SanitizedContentKind_1.HTML) {
      // This effectively negates the value of splitting a string. However,
      // This can be removed if Soy decides to treat attribute printing
      // and attribute names differently.
      val = soy.$$filterHtmlAttributes(
          htmlToString((        /**
           * @return {void}
           */
              () => ((/**
           @type {function(!tsickle_api_idom_13.IncrementalDomRenderer,
                                       A, B): void}
           */
              (expr)))(defaultIdomRenderer, data, ij))));
    } else {
      val = (/** @type {!tsickle_SanitizedHtmlAttribute_5} */ ((
          (/** @type {function(A, B): (string|!tsickle_SanitizedContent_2)} */ (
              expr)))(data, ij)));
    }
    printDynamicAttr(incrementaldom, val);
  }
}
exports.$$callDynamicAttributes = callDynamicAttributes;
/**
 * Prints an expression whose type is not statically known to be of type
 * "attributes". The expression is tested at runtime and evaluated depending
 * on what type it is. For example, if a string is printed in a context
 * that expects attributes, the string is evaluated dynamically to compute
 * attributes.
 * @param {!tsickle_api_idom_13.IncrementalDomRenderer} incrementaldom
 * @param {(string|boolean|!tsickle_element_lib_idom_14.IdomFunction|!tsickle_SanitizedHtmlAttribute_5)}
 *     expr
 * @return {void}
 */
function printDynamicAttr(incrementaldom, expr) {
  if (goog.isFunction(expr) &&
      ((/** @type {!tsickle_element_lib_idom_14.IdomFunction} */ (expr)))
          .contentKind ===
      goog_goog_soy_data_SanitizedContentKind_1.ATTRIBUTES) {
    // tslint:disable-next-line:no-any
    ((/** @type {function(!tsickle_api_idom_13.IncrementalDomRenderer): void} */
        ((/** @type {?} */ (expr)))))(incrementaldom);
    return;
  }
  /** @type {!Array<!Array<string>>} */
  const attributes = splitAttributes(expr.toString());
  /** @type {boolean} */
  const isExprAttribute = goog_soy_checks_1.isAttribute(expr);
  for (const attribute of attributes) {
    /** @type {string} */
    const attrName = isExprAttribute ? attribute[0] :
        soy.$$filterHtmlAttributes(attribute[0]);
    if (attrName === 'zSoyz') {
      attr(attrName, '');
    } else {
      attr(String(attrName), String(attribute[1]));
    }
  }
}
exports.$$printDynamicAttr = printDynamicAttr;
/**
 * Calls an expression in case of a function or outputs it as text content.
 * @template A, B
 * @param {!tsickle_api_idom_13.IncrementalDomRenderer} incrementaldom
 * @param {(function(!tsickle_api_idom_13.IncrementalDomRenderer, A, B):
 *     void|function(A, B): (string|!tsickle_SanitizedContent_2))} expr
 * @param {A} data
 * @param {B} ij
 * @return {void}
 */
function callDynamicHTML(incrementaldom, expr, data, ij) {
  // tslint:disable-next-line:no-any Attaching arbitrary attributes to function.
  /** @type {?} */
  const type = ((/** @type {!tsickle_element_lib_idom_14.IdomFunction} */ (
      (/** @type {?} */ (expr)))))
      .contentKind;
  if (type === goog_goog_soy_data_SanitizedContentKind_1.HTML) {
    ((/**
     @type {function(!tsickle_api_idom_13.IncrementalDomRenderer, A, B):
             void}
     */
        (expr)))(incrementaldom, data, ij);
  } else if (type === goog_goog_soy_data_SanitizedContentKind_1.ATTRIBUTES) {
    /** @type {string} */
    const val =
        attributesToString((        /**
         * @return {void}
         */
            () => ((/**
         @type {function(!tsickle_api_idom_13.IncrementalDomRenderer,
                                           A, B): void}
         */
            (expr)))(defaultIdomRenderer, data, ij)));
    text(val);
  } else {
    /** @type {(string|!tsickle_SanitizedContent_2)} */
    const val =
        ((/** @type {function(A, B): (string|!tsickle_SanitizedContent_2)} */ (
            expr)))(data, ij);
    text(String(val));
  }
}
exports.$$callDynamicHTML = callDynamicHTML;
/**
 * @template A, B
 * @param {!tsickle_api_idom_13.IncrementalDomRenderer} incrementaldom
 * @param {function(A, B): ?} expr
 * @param {A} data
 * @param {B} ij
 * @return {void}
 */
function callDynamicCss(
    // tslint:disable-next-line:no-any Attaching  attributes to function.
    incrementaldom, expr, data, ij) {
  /** @type {(string|!tsickle_SanitizedContent_2)} */
  const val = callDynamicText(expr, data, ij, soy.$$filterCssValue);
  text(String(val));
}
exports.$$callDynamicCss = callDynamicCss;
/**
 * @template A, B
 * @param {!tsickle_api_idom_13.IncrementalDomRenderer} incrementaldom
 * @param {function(A, B): ?} expr
 * @param {A} data
 * @param {B} ij
 * @return {void}
 */
function callDynamicJs(
    // tslint:disable-next-line:no-any Attaching attributes to function.
    incrementaldom, expr, data, ij) {
  /** @type {(string|!tsickle_SanitizedContent_2)} */
  const val = callDynamicText(expr, data, ij, soy.$$escapeJsValue);
  text(String(val));
}
exports.$$callDynamicJs = callDynamicJs;
/**
 * Calls an expression and coerces it to a string for cases where an IDOM
 * function needs to be concatted to a string.
 * @template A, B
 * @param {(function(!tsickle_api_idom_13.IncrementalDomRenderer, A, B):
 *     void|function(A, B): (string|!tsickle_SanitizedContent_2))} expr
 * @param {A} data
 * @param {B} ij
 * @param {(undefined|function(string): string)=} escFn
 * @return {(string|!tsickle_SanitizedContent_2)}
 */
function callDynamicText(
    // tslint:disable-next-line:no-any
    expr, data, ij, escFn) {
  /** @type {function(string): string} */
  const transformFn = escFn ? escFn :
      (/**
       * @param {string} a
       * @return {string}
       */
          (a) => a);
  // tslint:disable-next-line:no-any Attaching arbitrary attributes to function.
  /** @type {?} */
  const type = ((/** @type {!tsickle_element_lib_idom_14.IdomFunction} */ (
      (/** @type {?} */ (expr)))))
      .contentKind;
  /** @type {(string|!tsickle_SanitizedContent_2)} */
  let val;
  if (type === goog_goog_soy_data_SanitizedContentKind_1.HTML) {
    val = transformFn(
        htmlToString((        /**
         * @return {void}
         */
            () => ((/**
         @type {function(!tsickle_api_idom_13.IncrementalDomRenderer,
                                     A, B): void}
         */
            (expr)))(defaultIdomRenderer, data, ij))));
  } else if (type === goog_goog_soy_data_SanitizedContentKind_1.ATTRIBUTES) {
    val = transformFn(
        attributesToString((        /**
         * @return {void}
         */
            () => ((/**
         @type {function(!tsickle_api_idom_13.IncrementalDomRenderer,
                                           A, B): void}
         */
            (expr)))(defaultIdomRenderer, data, ij))));
  } else {
    val =
        ((/** @type {function(A, B): (string|!tsickle_SanitizedContent_2)} */ (
            expr)))(data, ij);
  }
  return val;
}
exports.$$callDynamicText = callDynamicText;
/**
 * Prints an expression depending on its type.
 * @param {!tsickle_api_idom_13.IncrementalDomRenderer} incrementaldom
 * @param {*} expr
 * @param {(undefined|boolean)=} isSanitizedContent
 * @return {void}
 */
function print(incrementaldom, expr, isSanitizedContent) {
  if (expr instanceof goog_goog_soy_data_SanitizedHtml_1 ||
      isSanitizedContent) {
    /** @type {string} */
    const content = String(expr);
    // If the string has no < or &, it's definitely not HTML. Otherwise
    // proceed with caution.
    if (content.indexOf('<') < 0 && content.indexOf('&') < 0) {
      text(content);
    } else {
      // For HTML content we need to insert a custom element where we can place
      // the content without incremental dom modifying it.
      /** @type {(void|!HTMLElement)} */
      const el = elementOpen('html-blob');
      if (el && el.__innerHTML !== content) {
        googSoy.renderHtml(
            el, goog_soydata_VERY_UNSAFE_1.ordainSanitizedHtml(content));
        el.__innerHTML = content;
      }
      skip();
      elementClose('html-blob');
    }
  } else {
    renderDynamicContent(incrementaldom, expr);
  }
}
exports.$$print = print;
/**
 * @param {!tsickle_api_idom_13.IncrementalDomRenderer} incrementaldom
 * @param {string} val
 * @return {void}
 */
function visitHtmlCommentNode(incrementaldom, val) {
  /** @type {(void|!HTMLElement)} */
  const currNode = currentElement();
  if (!currNode) {
    return;
  }
  if (currNode.nextSibling != null &&
      currNode.nextSibling.nodeType === Node.COMMENT_NODE) {
    currNode.nextSibling.textContent = val;
    // This is the case where we are creating new DOM from an empty element.
  } else {
    currNode.appendChild(document.createComment(val));
  }
  skipNode();
}
exports.$$visitHtmlCommentNode = visitHtmlCommentNode;
//#
// sourceMappingURL=data:application/json;base64,eyJ2ZXJzaW9uIjozLCJmaWxlIjoic295dXRpbHNfaWRvbS5qcyIsInNvdXJjZVJvb3QiOiIiLCJzb3VyY2VzIjpbIi4uLy4uLy4uLy4uLy4uLy4uL2phdmFzY3JpcHQvdGVtcGxhdGUvc295L3NveXV0aWxzX2lkb20udHMiXSwibmFtZXMiOltdLCJtYXBwaW5ncyI6Ijs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7OztBQWlCQSx5Q0FBeUMsQ0FBRSxnQ0FBZ0M7O0FBRTNFLHFHQUEyRSxDQUFDLHFDQUFxQzs7QUFDakgsdUZBQTZELENBQUMscUNBQXFDOztBQUluRywrQ0FBK0MsQ0FBRSxtQ0FBbUM7O0FBQ3BGLGdDQUFnQyxDQUFFLGdEQUFnRDs7QUFDbEYscURBQTRDLENBQUUsd0NBQXdDOztBQUN0Rix1RUFBNkQsQ0FBRSxnREFBZ0Q7O0FBQy9HLDRGQUFpRCxDQUFFLCtEQUErRDs7QUFFbEgsNEVBQWtHO0FBQ2xHLDRGQUEyRTtBQStUM0Qsc0JBL1RxQiw2QkFBVSxDQStUcEI7QUE5VDNCLHdFQUF1Qzs7O01BSWpDLEVBQUMsVUFBVSxFQUFFLE1BQU0sRUFBRSxpQkFBaUIsRUFBQyxHQUFHLGNBQWM7O01BRXhELG1CQUFtQixHQUFHLElBQUksaUNBQXNCLEVBQUU7O0FBRXhELGlCQUNpRTs7QUFDakUsZ0JBQTJFOztBQUMzRSxnQkFBMEQ7O0FBQzFELGFBQTJEOztBQUczRCxVQUFVLENBQUMsU0FBUyxDQUFDOzs7Ozs7QUFBRyxDQUFDLEVBQVcsRUFBRSxJQUFZLEVBQUUsS0FBVSxFQUFFLEVBQUU7SUFDaEUsZ0NBQWdDO0lBQ2hDLDBFQUEwRTtJQUMxRSx3REFBd0Q7SUFDeEQsaUVBQWlFO0lBQ2pFLHdCQUF3QjtJQUN4QixFQUFFLENBQUMsWUFBWSxDQUFDLFNBQVMsRUFBRSxLQUFLLENBQUMsQ0FBQztJQUNsQyxDQUFDLG1DQUFBLEVBQUUsRUFBb0IsQ0FBQyxDQUFDLE9BQU87UUFDNUIsQ0FBQyxDQUFDLEtBQUssS0FBSyxLQUFLLElBQUksS0FBSyxLQUFLLE9BQU8sSUFB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QUcsR0FBRyxDQUFDO1FBQ3ZDLHdFQUF3RTtLQUN6RTtTQUFNO1FBQ0wsUUFBUSxDQUFDLFdBQVcsQ0FBQyxRQUFRLENBQUMsYUFBYSxDQUFDLEdBQUcsQ0FBQyxDQUFDLENBQUM7S0FDbkQ7SUFDRCxjQUFjLENBQUMsUUFBUSxFQUFFLENBQUM7QUFDNUIsQ0FBQztBQWV5QixzREFBc0IiLCJzb3VyY2VzQ29udGVudCI6WyIvKlxuICogQGZpbGVvdmVydmlldyBIZWxwZXIgdXRpbGl0aWVzIGZvciBpbmNyZW1lbnRhbCBkb20gY29kZSBnZW5lcmF0aW9uIGluIFNveS5cbiAqIENvcHlyaWdodCAyMDE2IEdvb2dsZSBJbmMuXG4gKlxuICogTGljZW5zZWQgdW5kZXIgdGhlIEFwYWNoZSBMaWNlbnNlLCBWZXJzaW9uIDIuMCAodGhlIFwiTGljZW5zZVwiKTtcbiAqIHlvdSBtYXkgbm90IHVzZSB0aGlzIGZpbGUgZXhjZXB0IGluIGNvbXBsaWFuY2Ugd2l0aCB0aGUgTGljZW5zZS5cbiAqIFlvdSBtYXkgb2J0YWluIGEgY29weSBvZiB0aGUgTGljZW5zZSBhdFxuICpcbiAqICAgICBodHRwOi8vd3d3LmFwYWNoZS5vcmcvbGljZW5zZXMvTElDRU5TRS0yLjBcbiAqXG4gKiBVbmxlc3MgcmVxdWlyZWQgYnkgYXBwbGljYWJsZSBsYXcgb3IgYWdyZWVkIHRvIGluIHdyaXRpbmcsIHNvZnR3YXJlXG4gKiBkaXN0cmlidXRlZCB1bmRlciB0aGUgTGljZW5zZSBpcyBkaXN0cmlidXRlZCBvbiBhbiBcIkFTIElTXCIgQkFTSVMsXG4gKiBXSVRIT1VUIFdBUlJBTlRJRVMgT1IgQ09ORElUSU9OUyBPRiBBTlkgS0lORCwgZWl0aGVyIGV4cHJlc3Mgb3IgaW1wbGllZC5cbiAqIFNlZSB0aGUgTGljZW5zZSBmb3IgdGhlIHNwZWNpZmljIGxhbmd1YWdlIGdvdmVybmluZyBwZXJtaXNzaW9ucyBhbmRcbiAqIGxpbWl0YXRpb25zIHVuZGVyIHRoZSBMaWNlbnNlLlxuICovXG5cbmltcG9ydCAqIGFzIGdvb2dTb3kgZnJvbSAnZ29vZzpnb29nLnNveSc7ICAvLyBmcm9tIC8vamF2YXNjcmlwdC9jbG9zdXJlL3NveVxuaW1wb3J0IFNhbml0aXplZENvbnRlbnQgZnJvbSAnZ29vZzpnb29nLnNveS5kYXRhLlNhbml0aXplZENvbnRlbnQnOyAvLyBmcm9tIC8vamF2YXNjcmlwdC9jbG9zdXJlL3NveTpkYXRhXG5pbXBvcnQgU2FuaXRpemVkQ29udGVudEtpbmQgZnJvbSAnZ29vZzpnb29nLnNveS5kYXRhLlNhbml0aXplZENvbnRlbnRLaW5kJzsgLy8gZnJvbSAvL2phdmFzY3JpcHQvY2xvc3VyZS9zb3k6ZGF0YVxuaW1wb3J0IFNhbml0aXplZEh0bWwgZnJvbSAnZ29vZzpnb29nLnNveS5kYXRhLlNhbml0aXplZEh0bWwnOyAvLyBmcm9tIC8vamF2YXNjcmlwdC9jbG9zdXJlL3NveTpkYXRhXG5pbXBvcnQgU2FuaXRpemVkSHRtbEF0dHJpYnV0ZSBmcm9tICdnb29nOmdvb2cuc295LmRhdGEuU2FuaXRpemVkSHRtbEF0dHJpYnV0ZSc7IC8vIGZyb20gLy9qYXZhc2NyaXB0L2Nsb3N1cmUvc295OmRhdGFcbmltcG9ydCBTYW5pdGl6ZWRKcyBmcm9tICdnb29nOmdvb2cuc295LmRhdGEuU2FuaXRpemVkSnMnOyAvLyBmcm9tIC8vamF2YXNjcmlwdC9jbG9zdXJlL3NveTpkYXRhXG5pbXBvcnQgU2FuaXRpemVkVXJpIGZyb20gJ2dvb2c6Z29vZy5zb3kuZGF0YS5TYW5pdGl6ZWRVcmknOyAvLyBmcm9tIC8vamF2YXNjcmlwdC9jbG9zdXJlL3NveTpkYXRhXG5pbXBvcnQgKiBhcyBnb29nU3RyaW5nIGZyb20gJ2dvb2c6Z29vZy5zdHJpbmcnOyAgLy8gZnJvbSAvL2phdmFzY3JpcHQvY2xvc3VyZS9zdHJpbmdcbmltcG9ydCAqIGFzIHNveSBmcm9tICdnb29nOnNveSc7ICAvLyBmcm9tIC8vamF2YXNjcmlwdC90ZW1wbGF0ZS9zb3k6c295X3VzZWdvb2dfanNcbmltcG9ydCB7aXNBdHRyaWJ1dGV9IGZyb20gJ2dvb2c6c295LmNoZWNrcyc7ICAvLyBmcm9tIC8vamF2YXNjcmlwdC90ZW1wbGF0ZS9zb3k6Y2hlY2tzXG5pbXBvcnQge29yZGFpblNhbml0aXplZEh0bWx9IGZyb20gJ2dvb2c6c295ZGF0YS5WRVJZX1VOU0FGRSc7ICAvLyBmcm9tIC8vamF2YXNjcmlwdC90ZW1wbGF0ZS9zb3k6c295X3VzZWdvb2dfanNcbmltcG9ydCAqIGFzIGluY3JlbWVudGFsZG9tIGZyb20gJ2luY3JlbWVudGFsZG9tJzsgIC8vIGZyb20gLy90aGlyZF9wYXJ0eS9qYXZhc2NyaXB0L2luY3JlbWVudGFsX2RvbTppbmNyZW1lbnRhbGRvbVxuXG5pbXBvcnQge0luY3JlbWVudGFsRG9tUmVuZGVyZXIsIGlzTWF0Y2hpbmdLZXksIHBhdGNoLCBwYXRjaE91dGVyLCBzZXJpYWxpemVLZXl9IGZyb20gJy4vYXBpX2lkb20nO1xuaW1wb3J0IHtJZG9tRnVuY3Rpb24sIFBhdGNoRnVuY3Rpb24sIFNveUVsZW1lbnR9IGZyb20gJy4vZWxlbWVudF9saWJfaWRvbSc7XG5pbXBvcnQge2dldFNveVVudHlwZWR9IGZyb20gJy4vZ2xvYmFsJztcblxuLy8gRGVjbGFyZSBwcm9wZXJ0aWVzIHRoYXQgbmVlZCB0byBiZSBhcHBsaWVkIG5vdCBhcyBhdHRyaWJ1dGVzIGJ1dCBhc1xuLy8gYWN0dWFsIERPTSBwcm9wZXJ0aWVzLlxuY29uc3Qge2F0dHJpYnV0ZXMsIGdldEtleSwgaXNEYXRhSW5pdGlhbGl6ZWR9ID0gaW5jcmVtZW50YWxkb207XG5cbmNvbnN0IGRlZmF1bHRJZG9tUmVuZGVyZXIgPSBuZXcgSW5jcmVtZW50YWxEb21SZW5kZXJlcigpO1xuXG50eXBlIElkb21UZW1wbGF0ZTxBLCBCPiA9XG4gICAgKGlkb206IEluY3JlbWVudGFsRG9tUmVuZGVyZXIsIHBhcmFtczogQSwgaWpEYXRhOiBCKSA9PiB2b2lkO1xudHlwZSBTb3lUZW1wbGF0ZTxBLCBCPiA9IChwYXJhbXM6IEEsIGlqRGF0YTogQikgPT4gc3RyaW5nfFNhbml0aXplZENvbnRlbnQ7XG50eXBlIExldEZ1bmN0aW9uID0gKGlkb206IEluY3JlbWVudGFsRG9tUmVuZGVyZXIpID0+IHZvaWQ7XG50eXBlIFRlbXBsYXRlPEEsIEI+ID0gSWRvbVRlbXBsYXRlPEEsIEI+fFNveVRlbXBsYXRlPEEsIEI+O1xuXG4vLyB0c2xpbnQ6ZGlzYWJsZS1uZXh0LWxpbmU6bm8tYW55XG5hdHRyaWJ1dGVzWydjaGVja2VkJ10gPSAoZWw6IEVsZW1lbnQsIG5hbWU6IHN0cmluZywgdmFsdWU6IGFueSkgPT4ge1xuICAvLyBXZSBkb24ndCB1c2UgISF2YWx1ZSBiZWNhdXNlOlxuICAvLyAxLiBJZiB2YWx1ZSBpcyAnJyAodGhpcyBpcyB0aGUgY2FzZSB3aGVyZSBhIHVzZXIgdXNlcyA8ZGl2IGNoZWNrZWQgLz4pLFxuICAvLyAgICB0aGUgY2hlY2tlZCB2YWx1ZSBzaG91bGQgYmUgdHJ1ZSwgYnV0ICcnIGlzIGZhbHN5LlxuICAvLyAyLiBJZiB2YWx1ZSBpcyAnZmFsc2UnLCB0aGUgY2hlY2tlZCB2YWx1ZSBzaG91bGQgYmUgZmFsc2UsIGJ1dFxuICAvLyAgICAnZmFsc2UnIGlzIHRydXRoeS5cbiAgZWwuc2V0QXR0cmlidXRlKCdjaGVja2VkJywgdmFsdWUpO1xuICAoZWwgYXMgSFRNTElucHV0RWxlbWVudCkuY2hlY2tlZCA9XG4gICAgICAhKHZhbHVlID09PSBmYWxzZSB8fCB2YWx1ZSA9PT0gJ2ZhbHNlJyB8fCB2YWx1ZSA9PT0gdW5kZWZpbmVkKTtcbn07XG5cbi8vIHRzbGludDpkaXNhYmxlLW5leHQtbGluZTpuby1hbnlcbmF0dHJpYnV0ZXNbJ3ZhbHVlJ10gPSAoZWw6IEVsZW1lbnQsIG5hbWU6IHN0cmluZywgdmFsdWU6IGFueSkgPT4ge1xuICAoZWwgYXMgSFRNTElucHV0RWxlbWVudCkudmFsdWUgPSB2YWx1ZTtcbiAgZWwuc2V0QXR0cmlidXRlKCd2YWx1ZScsIHZhbHVlKTtcbn07XG5cbi8vIFNveSB1c2VzIHRoZSB7a2V5fSBjb21tYW5kIHN5bnRheCwgcmF0aGVyIHRoYW4gSFRNTCBhdHRyaWJ1dGVzLCB0b1xuLy8gaW5kaWNhdGUgZWxlbWVudCBrZXlzLlxuaW5jcmVtZW50YWxkb20uc2V0S2V5QXR0cmlidXRlTmFtZShudWxsKTtcblxuLyoqXG4gKiBSZXR1cm5zIHRoZSB0ZW1wbGF0ZSBvYmplY3Qgc3RvcmVkIGluIHRoZSBjdXJyZW50UG9pbnRlciBlbGVtZW50IGlmIGl0IGlzIHRoZVxuICogY29ycmVjdCB0eXBlLiAgT3RoZXJ3aXNlLCByZXR1cm5zIG51bGwuXG4gKi9cbmZ1bmN0aW9uIHRyeUdldEVsZW1lbnQ8VCBleHRlbmRzIFNveUVsZW1lbnQ8e30sIHt9Pj4oXG4gICAgaW5jcmVtZW50YWxkb206IEluY3JlbWVudGFsRG9tUmVuZGVyZXIsIGVsZW1lbnRDbGFzc0N0b3I6IG5ldyAoKSA9PiBULFxuICAgIGZpcnN0RWxlbWVudEtleTogc3RyaW5nKSB7XG4gIGxldCBjdXJyZW50UG9pbnRlciA9IGluY3JlbWVudGFsZG9tLmN1cnJlbnRQb2ludGVyKCk7XG4gIHdoaWxlIChjdXJyZW50UG9pbnRlciAhPSBudWxsKSB7XG4gICAgY29uc3QgZWwgPSBnZXRTb3lVbnR5cGVkKGN1cnJlbnRQb2ludGVyKTtcbiAgICBpZiAoZWwgaW5zdGFuY2VvZiBlbGVtZW50Q2xhc3NDdG9yICYmIGlzRGF0YUluaXRpYWxpemVkKGN1cnJlbnRQb2ludGVyKSkge1xuICAgICAgY29uc3QgY3VycmVudFBvaW50ZXJLZXkgPSBnZXRLZXkoY3VycmVudFBvaW50ZXIpIGFzIHN0cmluZztcbiAgICAgIGlmIChpc01hdGNoaW5nS2V5KFxuICAgICAgICAgICAgICBzZXJpYWxpemVLZXkoZmlyc3RFbGVtZW50S2V5KSArXG4gICAgICAgICAgICAgICAgICBpbmNyZW1lbnRhbGRvbS5nZXRDdXJyZW50S2V5U3RhY2soKSxcbiAgICAgICAgICAgICAgY3VycmVudFBvaW50ZXJLZXkpKSB7XG4gICAgICAgIHJldHVybiBlbDtcbiAgICAgIH1cbiAgICB9XG4gICAgY3VycmVudFBvaW50ZXIgPSBjdXJyZW50UG9pbnRlci5uZXh0U2libGluZztcbiAgfVxuICByZXR1cm4gbnVsbDtcbn1cblxuLy8gdHNsaW50OmRpc2FibGUtbmV4dC1saW5lOm5vLWFueSBBdHRhY2hpbmcgYXJiaXRyYXJ5IGF0dHJpYnV0ZXMgdG8gZnVuY3Rpb24uXG5mdW5jdGlvbiBtYWtlSHRtbChpZG9tRm46IGFueSk6IElkb21GdW5jdGlvbiB7XG4gIGlkb21Gbi50b1N0cmluZyA9IChyZW5kZXJlcjogSW5jcmVtZW50YWxEb21SZW5kZXJlciA9IGRlZmF1bHRJZG9tUmVuZGVyZXIpID0+XG4gICAgICBodG1sVG9TdHJpbmcoaWRvbUZuLCByZW5kZXJlcik7XG4gIGlkb21Gbi50b0Jvb2xlYW4gPSAoKSA9PiB0b0Jvb2xlYW4oaWRvbUZuKTtcbiAgaWRvbUZuLmNvbnRlbnRLaW5kID0gU2FuaXRpemVkQ29udGVudEtpbmQuSFRNTDtcbiAgcmV0dXJuIGlkb21GbiBhcyBJZG9tRnVuY3Rpb247XG59XG5cbi8vIHRzbGludDpkaXNhYmxlLW5leHQtbGluZTpuby1hbnkgQXR0YWNoaW5nIGFyYml0cmFyeSBhdHRyaWJ1dGVzIHRvIGZ1bmN0aW9uLlxuZnVuY3Rpb24gbWFrZUF0dHJpYnV0ZXMoaWRvbUZuOiBhbnkpOiBJZG9tRnVuY3Rpb24ge1xuICBpZG9tRm4udG9TdHJpbmcgPSAoKSA9PiBhdHRyaWJ1dGVzVG9TdHJpbmcoaWRvbUZuKTtcbiAgaWRvbUZuLnRvQm9vbGVhbiA9ICgpID0+IHRvQm9vbGVhbihpZG9tRm4pO1xuICBpZG9tRm4uY29udGVudEtpbmQgPSBTYW5pdGl6ZWRDb250ZW50S2luZC5BVFRSSUJVVEVTO1xuICByZXR1cm4gaWRvbUZuIGFzIElkb21GdW5jdGlvbjtcbn1cblxuLyoqXG4gKiBUT0RPKHRvbW5ndXllbik6IElzc3VlIGEgd2FybmluZyBpbiB0aGVzZSBjYXNlcyBzbyB0aGF0IHVzZXJzIGtub3cgdGhhdFxuICogZXhwZW5zaXZlIGJlaGF2aW9yIGlzIGhhcHBlbmluZy5cbiAqL1xuZnVuY3Rpb24gaHRtbFRvU3RyaW5nKFxuICAgIGZuOiBMZXRGdW5jdGlvbiwgcmVuZGVyZXI6IEluY3JlbWVudGFsRG9tUmVuZGVyZXIgPSBkZWZhdWx0SWRvbVJlbmRlcmVyKSB7XG4gIGNvbnN0IGVsID0gZG9jdW1lbnQuY3JlYXRlRWxlbWVudCgnZGl2Jyk7XG4gIHBhdGNoKGVsLCAoKSA9PiBmbihyZW5kZXJlcikpO1xuICByZXR1cm4gZWwuaW5uZXJIVE1MO1xufVxuXG5mdW5jdGlvbiBhdHRyaWJ1dGVzRmFjdG9yeShmbjogUGF0Y2hGdW5jdGlvbik6IFBhdGNoRnVuY3Rpb24ge1xuICByZXR1cm4gKCkgPT4ge1xuICAgIGluY3JlbWVudGFsZG9tLmVsZW1lbnRPcGVuU3RhcnQoJ2RpdicpO1xuICAgIGZuKGRlZmF1bHRJZG9tUmVuZGVyZXIpO1xuICAgIGluY3JlbWVudGFsZG9tLmVsZW1lbnRPcGVuRW5kKCk7XG4gICAgaW5jcmVtZW50YWxkb20uZWxlbWVudENsb3NlKCdkaXYnKTtcbiAgfTtcbn1cblxuLyoqXG4gKiBUT0RPKHRvbW5ndXllbik6IElzc3VlIGEgd2FybmluZyBpbiB0aGVzZSBjYXNlcyBzbyB0aGF0IHVzZXJzIGtub3cgdGhhdFxuICogZXhwZW5zaXZlIGJlaGF2aW9yIGlzIGhhcHBlbmluZy5cbiAqL1xuZnVuY3Rpb24gYXR0cmlidXRlc1RvU3RyaW5nKGZuOiBQYXRjaEZ1bmN0aW9uKTogc3RyaW5nIHtcbiAgY29uc3QgZWxGbiA9IGF0dHJpYnV0ZXNGYWN0b3J5KGZuKTtcbiAgY29uc3QgZWwgPSBkb2N1bWVudC5jcmVhdGVFbGVtZW50KCdkaXYnKTtcbiAgcGF0Y2hPdXRlcihlbCwgZWxGbik7XG4gIGNvbnN0IHM6IHN0cmluZ1tdID0gW107XG4gIGZvciAobGV0IGkgPSAwOyBpIDwgZWwuYXR0cmlidXRlcy5sZW5ndGg7IGkrKykge1xuICAgIHMucHVzaChgJHtlbC5hdHRyaWJ1dGVzW2ldLm5hbWV9PSR7ZWwuYXR0cmlidXRlc1tpXS52YWx1ZX1gKTtcbiAgfVxuICAvLyBUaGUgc29ydCBpcyBpbXBvcnRhbnQgYmVjYXVzZSBhdHRyaWJ1dGUgb3JkZXIgdmFyaWVzIHBlciBicm93c2VyLlxuICByZXR1cm4gcy5zb3J0KCkuam9pbignICcpO1xufVxuXG5mdW5jdGlvbiB0b0Jvb2xlYW4oZm46IElkb21GdW5jdGlvbikge1xuICByZXR1cm4gZm4udG9TdHJpbmcoKS5sZW5ndGggPiAwO1xufVxuXG4vKipcbiAqIENhbGxzIGFuIGV4cHJlc3Npb24gaW4gY2FzZSBvZiBhIGZ1bmN0aW9uIG9yIG91dHB1dHMgaXQgYXMgdGV4dCBjb250ZW50LlxuICovXG5mdW5jdGlvbiByZW5kZXJEeW5hbWljQ29udGVudChcbiAgICBpbmNyZW1lbnRhbGRvbTogSW5jcmVtZW50YWxEb21SZW5kZXJlciwgZXhwcjogdW5rbm93bikge1xuICAvLyBUT0RPKGx1a2VzKTogY2hlY2sgY29udGVudCBraW5kID09IGh0bWxcbiAgaWYgKHR5cGVvZiBleHByID09PSAnZnVuY3Rpb24nKSB7XG4gICAgLy8gVGhlIFNveSBjb21waWxlciB3aWxsIHZhbGlkYXRlIHRoZSBjb250ZW50IGtpbmQgb2YgdGhlIHBhcmFtZXRlci5cbiAgICBleHByKGluY3JlbWVudGFsZG9tKTtcbiAgfSBlbHNlIHtcbiAgICBpbmNyZW1lbnRhbGRvbS50ZXh0KFN0cmluZyhleHByKSk7XG4gIH1cbn1cblxuLyoqXG4gKiBNYXRjaGVzIGFuIEhUTUwgYXR0cmlidXRlIG5hbWUgdmFsdWUgcGFpci5cbiAqIE5hbWUgaXMgaW4gZ3JvdXAgMS4gIFZhbHVlLCBpZiBwcmVzZW50LCBpcyBpbiBvbmUgb2YgZ3JvdXAgKDIsMyw0KVxuICogZGVwZW5kaW5nIG9uIGhvdyBpdCdzIHF1b3RlZC5cbiAqXG4gKiBUaGlzIFJlZ0V4cCB3YXMgZGVyaXZlZCBmcm9tIHZpc3VhbCBpbnNwZWN0aW9uIG9mXG4gKiAgIGh0bWwuc3BlYy53aGF0d2cub3JnL211bHRpcGFnZS9wYXJzaW5nLmh0bWwjYmVmb3JlLWF0dHJpYnV0ZS1uYW1lLXN0YXRlXG4gKiBhbmQgZm9sbG93aW5nIHN0YXRlcy5cbiAqL1xuY29uc3QgaHRtbEF0dHJpYnV0ZVJlZ0V4cDogUmVnRXhwID1cbiAgICAvKFteXFx0XFxuXFxmXFxyIC8+PV0rKVtcXHRcXG5cXGZcXHIgXSooPzo9W1xcdFxcblxcZlxcciBdKig/OlwiKFteXCJdKilcIj98JyhbXiddKiknP3woW15cXHRcXG5cXGZcXHIgPl0qKSkpPy9nO1xuXG5mdW5jdGlvbiBzcGxpdEF0dHJpYnV0ZXMoYXR0cmlidXRlczogc3RyaW5nKSB7XG4gIGNvbnN0IG5hbWVWYWx1ZVBhaXJzOiBzdHJpbmdbXVtdID0gW107XG4gIFN0cmluZyhhdHRyaWJ1dGVzKS5yZXBsYWNlKGh0bWxBdHRyaWJ1dGVSZWdFeHAsIChfLCBuYW1lLCBkcSwgc3EsIHVxKSA9PiB7XG4gICAgbmFtZVZhbHVlUGFpcnMucHVzaChcbiAgICAgICAgW25hbWUsIGdvb2dTdHJpbmcudW5lc2NhcGVFbnRpdGllcyhkcSB8fCBzcSB8fCB1cSB8fCAnJyldKTtcbiAgICByZXR1cm4gJyAnO1xuICB9KTtcbiAgcmV0dXJuIG5hbWVWYWx1ZVBhaXJzO1xufVxuXG4vKipcbiAqIENhbGxzIGFuIGV4cHJlc3Npb24gaW4gY2FzZSBvZiBhIGZ1bmN0aW9uIG9yIG91dHB1dHMgaXQgYXMgdGV4dCBjb250ZW50LlxuICovXG5mdW5jdGlvbiBjYWxsRHluYW1pY0F0dHJpYnV0ZXM8QSwgQj4oXG4gICAgaW5jcmVtZW50YWxkb206IEluY3JlbWVudGFsRG9tUmVuZGVyZXIsXG4gICAgLy8gdHNsaW50OmRpc2FibGUtbmV4dC1saW5lOm5vLWFueVxuICAgIGV4cHI6IFRlbXBsYXRlPEEsIEI+LCBkYXRhOiBBLCBpajogQikge1xuICAvLyB0c2xpbnQ6ZGlzYWJsZS1uZXh0LWxpbmU6bm8tYW55IEF0dGFjaGluZyBhcmJpdHJhcnkgYXR0cmlidXRlcyB0byBmdW5jdGlvbi5cbiAgY29uc3QgdHlwZSA9IChleHByIGFzIGFueSBhcyBJZG9tRnVuY3Rpb24pLmNvbnRlbnRLaW5kO1xuICBpZiAodHlwZSA9PT0gU2FuaXRpemVkQ29udGVudEtpbmQuQVRUUklCVVRFUykge1xuICAgIChleHByIGFzIElkb21UZW1wbGF0ZTxBLCBCPikoaW5jcmVtZW50YWxkb20sIGRhdGEsIGlqKTtcbiAgfSBlbHNlIHtcbiAgICBsZXQgdmFsOiBzdHJpbmd8U2FuaXRpemVkSHRtbEF0dHJpYnV0ZTtcbiAgICBpZiAodHlwZSA9PT0gU2FuaXRpemVkQ29udGVudEtpbmQuSFRNTCkge1xuICAgICAgLy8gVGhpcyBlZmZlY3RpdmVseSBuZWdhdGVzIHRoZSB2YWx1ZSBvZiBzcGxpdHRpbmcgYSBzdHJpbmcuIEhvd2V2ZXIsXG4gICAgICAvLyBUaGlzIGNhbiBiZSByZW1vdmVkIGlmIFNveSBkZWNpZGVzIHRvIHRyZWF0IGF0dHJpYnV0ZSBwcmludGluZ1xuICAgICAgLy8gYW5kIGF0dHJpYnV0ZSBuYW1lcyBkaWZmZXJlbnRseS5cbiAgICAgIHZhbCA9IHNveS4kJGZpbHRlckh0bWxBdHRyaWJ1dGVzKGh0bWxUb1N0cmluZyhcbiAgICAgICAgICAoKSA9PiAoZXhwciBhcyBJZG9tVGVtcGxhdGU8QSwgQj4pKGRlZmF1bHRJZG9tUmVuZGVyZXIsIGRhdGEsIGlqKSkpO1xuICAgIH0gZWxzZSB7XG4gICAgICB2YWwgPSAoZXhwciBhcyBTb3lUZW1wbGF0ZTxBLCBCPikoZGF0YSwgaWopIGFzIFNhbml0aXplZEh0bWxBdHRyaWJ1dGU7XG4gICAgfVxuICAgIHByaW50RHluYW1pY0F0dHIoaW5jcmVtZW50YWxkb20sIHZhbCk7XG4gIH1cbn1cblxuLyoqXG4gKiBQcmludHMgYW4gZXhwcmVzc2lvbiB3aG9zZSB0eXBlIGlzIG5vdCBzdGF0aWNhbGx5IGtub3duIHRvIGJlIG9mIHR5cGVcbiAqIFwiYXR0cmlidXRlc1wiLiBUaGUgZXhwcmVzc2lvbiBpcyB0ZXN0ZWQgYXQgcnVudGltZSBhbmQgZXZhbHVhdGVkIGRlcGVuZGluZ1xuICogb24gd2hhdCB0eXBlIGl0IGlzLiBGb3IgZXhhbXBsZSwgaWYgYSBzdHJpbmcgaXMgcHJpbnRlZCBpbiBhIGNvbnRleHRcbiAqIHRoYXQgZXhwZWN0cyBhdHRyaWJ1dGVzLCB0aGUgc3RyaW5nIGlzIGV2YWx1YXRlZCBkeW5hbWljYWxseSB0byBjb21wdXRlXG4gKiBhdHRyaWJ1dGVzLlxuICovXG5mdW5jdGlvbiBwcmludER5bmFtaWNBdHRyKFxuICAgIGluY3JlbWVudGFsZG9tOiBJbmNyZW1lbnRhbERvbVJlbmRlcmVyLFxuICAgIGV4cHI6IFNhbml0aXplZEh0bWxBdHRyaWJ1dGV8c3RyaW5nfGJvb2xlYW58SWRvbUZ1bmN0aW9uKSB7XG4gIGlmIChnb29nLmlzRnVuY3Rpb24oZXhwcikgJiZcbiAgICAgIChleHByIGFzIElkb21GdW5jdGlvbikuY29udGVudEtpbmQgPT09IFNhbml0aXplZENvbnRlbnRLaW5kLkFUVFJJQlVURVMpIHtcbiAgICAvLyB0c2xpbnQ6ZGlzYWJsZS1uZXh0LWxpbmU6bm8tYW55XG4gICAgKGV4cHIgYXMgYW55IGFzIExldEZ1bmN0aW9uKShpbmNyZW1lbnRhbGRvbSk7XG4gICAgcmV0dXJuO1xuICB9XG4gIGNvbnN0IGF0dHJpYnV0ZXMgPSBzcGxpdEF0dHJpYnV0ZXMoZXhwci50b1N0cmluZygpKTtcbiAgY29uc3QgaXNFeHByQXR0cmlidXRlID0gaXNBdHRyaWJ1dGUoZXhwcik7XG4gIGZvciAoY29uc3QgYXR0cmlidXRlIG9mIGF0dHJpYnV0ZXMpIHtcbiAgICBjb25zdCBhdHRyTmFtZSA9IGlzRXhwckF0dHJpYnV0ZSA/IGF0dHJpYnV0ZVswXSA6XG4gICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICBzb3kuJCRmaWx0ZXJIdG1sQXR0cmlidXRlcyhhdHRyaWJ1dGVbMF0pO1xuICAgIGlmIChhdHRyTmFtZSA9PT0gJ3pTb3l6Jykge1xuICAgICAgaW5jcmVtZW50YWxkb20uYXR0cihhdHRyTmFtZSwgJycpO1xuICAgIH0gZWxzZSB7XG4gICAgICBpbmNyZW1lbnRhbGRvbS5hdHRyKFN0cmluZyhhdHRyTmFtZSksIFN0cmluZyhhdHRyaWJ1dGVbMV0pKTtcbiAgICB9XG4gIH1cbn1cblxuLyoqXG4gKiBDYWxscyBhbiBleHByZXNzaW9uIGluIGNhc2Ugb2YgYSBmdW5jdGlvbiBvciBvdXRwdXRzIGl0IGFzIHRleHQgY29udGVudC5cbiAqL1xuZnVuY3Rpb24gY2FsbER5bmFtaWNIVE1MPEEsIEI+KFxuICAgIGluY3JlbWVudGFsZG9tOiBJbmNyZW1lbnRhbERvbVJlbmRlcmVyLCBleHByOiBUZW1wbGF0ZTxBLCBCPiwgZGF0YTogQSxcbiAgICBpajogQikge1xuICAvLyB0c2xpbnQ6ZGlzYWJsZS1uZXh0LWxpbmU6bm8tYW55IEF0dGFjaGluZyBhcmJpdHJhcnkgYXR0cmlidXRlcyB0byBmdW5jdGlvbi5cbiAgY29uc3QgdHlwZSA9IChleHByIGFzIGFueSBhcyBJZG9tRnVuY3Rpb24pLmNvbnRlbnRLaW5kO1xuICBpZiAodHlwZSA9PT0gU2FuaXRpemVkQ29udGVudEtpbmQuSFRNTCkge1xuICAgIChleHByIGFzIElkb21UZW1wbGF0ZTxBLCBCPikoaW5jcmVtZW50YWxkb20sIGRhdGEsIGlqKTtcbiAgfSBlbHNlIGlmICh0eXBlID09PSBTYW5pdGl6ZWRDb250ZW50S2luZC5BVFRSSUJVVEVTKSB7XG4gICAgY29uc3QgdmFsID0gYXR0cmlidXRlc1RvU3RyaW5nKFxuICAgICAgICAoKSA9PiAoZXhwciBhcyBJZG9tVGVtcGxhdGU8QSwgQj4pKGRlZmF1bHRJZG9tUmVuZGVyZXIsIGRhdGEsIGlqKSk7XG4gICAgaW5jcmVtZW50YWxkb20udGV4dCh2YWwpO1xuICB9IGVsc2Uge1xuICAgIGNvbnN0IHZhbCA9IChleHByIGFzIFNveVRlbXBsYXRlPEEsIEI+KShkYXRhLCBpaik7XG4gICAgaW5jcmVtZW50YWxkb20udGV4dChTdHJpbmcodmFsKSk7XG4gIH1cbn1cblxuZnVuY3Rpb24gY2FsbER5bmFtaWNDc3M8QSwgQj4oXG4gICAgLy8gdHNsaW50OmRpc2FibGUtbmV4dC1saW5lOm5vLWFueSBBdHRhY2hpbmcgIGF0dHJpYnV0ZXMgdG8gZnVuY3Rpb24uXG4gICAgaW5jcmVtZW50YWxkb206IEluY3JlbWVudGFsRG9tUmVuZGVyZXIsIGV4cHI6IChhOiBBLCBiOiBCKSA9PiBhbnksIGRhdGE6IEEsXG4gICAgaWo6IEIpIHtcbiAgY29uc3QgdmFsID0gY2FsbER5bmFtaWNUZXh0PEEsIEI+KGV4cHIsIGRhdGEsIGlqLCBzb3kuJCRmaWx0ZXJDc3NWYWx1ZSk7XG4gIGluY3JlbWVudGFsZG9tLnRleHQoU3RyaW5nKHZhbCkpO1xufVxuXG5mdW5jdGlvbiBjYWxsRHluYW1pY0pzPEEsIEI+KFxuICAgIC8vIHRzbGludDpkaXNhYmxlLW5leHQtbGluZTpuby1hbnkgQXR0YWNoaW5nIGF0dHJpYnV0ZXMgdG8gZnVuY3Rpb24uXG4gICAgaW5jcmVtZW50YWxkb206IEluY3JlbWVudGFsRG9tUmVuZGVyZXIsIGV4cHI6IChhOiBBLCBiOiBCKSA9PiBhbnksIGRhdGE6IEEsXG4gICAgaWo6IEIpIHtcbiAgY29uc3QgdmFsID0gY2FsbER5bmFtaWNUZXh0PEEsIEI+KGV4cHIsIGRhdGEsIGlqLCBzb3kuJCRlc2NhcGVKc1ZhbHVlKTtcbiAgaW5jcmVtZW50YWxkb20udGV4dChTdHJpbmcodmFsKSk7XG59XG5cbi8qKlxuICogQ2FsbHMgYW4gZXhwcmVzc2lvbiBhbmQgY29lcmNlcyBpdCB0byBhIHN0cmluZyBmb3IgY2FzZXMgd2hlcmUgYW4gSURPTVxuICogZnVuY3Rpb24gbmVlZHMgdG8gYmUgY29uY2F0dGVkIHRvIGEgc3RyaW5nLlxuICovXG5mdW5jdGlvbiBjYWxsRHluYW1pY1RleHQ8QSwgQj4oXG4gICAgLy8gdHNsaW50OmRpc2FibGUtbmV4dC1saW5lOm5vLWFueVxuICAgIGV4cHI6IFRlbXBsYXRlPEEsIEI+LCBkYXRhOiBBLCBpajogQiwgZXNjRm4/OiAoaTogc3RyaW5nKSA9PiBzdHJpbmcpIHtcbiAgY29uc3QgdHJhbnNmb3JtRm4gPSBlc2NGbiA/IGVzY0ZuIDogKGE6IHN0cmluZykgPT4gYTtcbiAgLy8gdHNsaW50OmRpc2FibGUtbmV4dC1saW5lOm5vLWFueSBBdHRhY2hpbmcgYXJiaXRyYXJ5IGF0dHJpYnV0ZXMgdG8gZnVuY3Rpb24uXG4gIGNvbnN0IHR5cGUgPSAoZXhwciBhcyBhbnkgYXMgSWRvbUZ1bmN0aW9uKS5jb250ZW50S2luZDtcbiAgbGV0IHZhbDogc3RyaW5nfFNhbml0aXplZENvbnRlbnQ7XG4gIGlmICh0eXBlID09PSBTYW5pdGl6ZWRDb250ZW50S2luZC5IVE1MKSB7XG4gICAgdmFsID0gdHJhbnNmb3JtRm4oaHRtbFRvU3RyaW5nKFxuICAgICAgICAoKSA9PiAoZXhwciBhcyBJZG9tVGVtcGxhdGU8QSwgQj4pKGRlZmF1bHRJZG9tUmVuZGVyZXIsIGRhdGEsIGlqKSkpO1xuICB9IGVsc2UgaWYgKHR5cGUgPT09IFNhbml0aXplZENvbnRlbnRLaW5kLkFUVFJJQlVURVMpIHtcbiAgICB2YWwgPSB0cmFuc2Zvcm1GbihhdHRyaWJ1dGVzVG9TdHJpbmcoXG4gICAgICAgICgpID0+IChleHByIGFzIElkb21UZW1wbGF0ZTxBLCBCPikoZGVmYXVsdElkb21SZW5kZXJlciwgZGF0YSwgaWopKSk7XG4gIH0gZWxzZSB7XG4gICAgdmFsID0gKGV4cHIgYXMgU295VGVtcGxhdGU8QSwgQj4pKGRhdGEsIGlqKTtcbiAgfVxuICByZXR1cm4gdmFsO1xufVxuXG5kZWNsYXJlIGdsb2JhbCB7XG4gIGludGVyZmFjZSBFbGVtZW50IHtcbiAgICBfX2lubmVySFRNTDogc3RyaW5nO1xuICB9XG59XG5cbi8qKlxuICogUHJpbnRzIGFuIGV4cHJlc3Npb24gZGVwZW5kaW5nIG9uIGl0cyB0eXBlLlxuICovXG5mdW5jdGlvbiBwcmludChcbiAgICBpbmNyZW1lbnRhbGRvbTogSW5jcmVtZW50YWxEb21SZW5kZXJlciwgZXhwcjogdW5rbm93bixcbiAgICBpc1Nhbml0aXplZENvbnRlbnQ/OiBib29sZWFufHVuZGVmaW5lZCkge1xuICBpZiAoZXhwciBpbnN0YW5jZW9mIFNhbml0aXplZEh0bWwgfHwgaXNTYW5pdGl6ZWRDb250ZW50KSB7XG4gICAgY29uc3QgY29udGVudCA9IFN0cmluZyhleHByKTtcbiAgICAvLyBJZiB0aGUgc3RyaW5nIGhhcyBubyA8IG9yICYsIGl0J3MgZGVmaW5pdGVseSBub3QgSFRNTC4gT3RoZXJ3aXNlXG4gICAgLy8gcHJvY2VlZCB3aXRoIGNhdXRpb24uXG4gICAgaWYgKGNvbnRlbnQuaW5kZXhPZignPCcpIDwgMCAmJiBjb250ZW50LmluZGV4T2YoJyYnKSA8IDApIHtcbiAgICAgIGluY3JlbWVudGFsZG9tLnRleHQoY29udGVudCk7XG4gICAgfSBlbHNlIHtcbiAgICAgIC8vIEZvciBIVE1MIGNvbnRlbnQgd2UgbmVlZCB0byBpbnNlcnQgYSBjdXN0b20gZWxlbWVudCB3aGVyZSB3ZSBjYW4gcGxhY2VcbiAgICAgIC8vIHRoZSBjb250ZW50IHdpdGhvdXQgaW5jcmVtZW50YWwgZG9tIG1vZGlmeWluZyBpdC5cbiAgICAgIGNvbnN0IGVsID0gaW5jcmVtZW50YWxkb20uZWxlbWVudE9wZW4oJ2h0bWwtYmxvYicpO1xuICAgICAgaWYgKGVsICYmIGVsLl9faW5uZXJIVE1MICE9PSBjb250ZW50KSB7XG4gICAgICAgIGdvb2dTb3kucmVuZGVySHRtbChlbCwgb3JkYWluU2FuaXRpemVkSHRtbChjb250ZW50KSk7XG4gICAgICAgIGVsLl9faW5uZXJIVE1MID0gY29udGVudDtcbiAgICAgIH1cbiAgICAgIGluY3JlbWVudGFsZG9tLnNraXAoKTtcbiAgICAgIGluY3JlbWVudGFsZG9tLmVsZW1lbnRDbG9zZSgnaHRtbC1ibG9iJyk7XG4gICAgfVxuICB9IGVsc2Uge1xuICAgIHJlbmRlckR5bmFtaWNDb250ZW50KGluY3JlbWVudGFsZG9tLCBleHByKTtcbiAgfVxufVxuXG5mdW5jdGlvbiB2aXNpdEh0bWxDb21tZW50Tm9kZShcbiAgICBpbmNyZW1lbnRhbGRvbTogSW5jcmVtZW50YWxEb21SZW5kZXJlciwgdmFsOiBzdHJpbmcpIHtcbiAgY29uc3QgY3Vyck5vZGUgPSBpbmNyZW1lbnRhbGRvbS5jdXJyZW50RWxlbWVudCgpO1xuICBpZiAoIWN1cnJOb2RlKSB7XG4gICAgcmV0dXJuO1xuICB9XG4gIGlmIChjdXJyTm9kZS5uZXh0U2libGluZyAhPSBudWxsICYmXG4gICAgICBjdXJyTm9kZS5uZXh0U2libGluZy5ub2RlVHlwZSA9PT0gTm9kZS5DT01NRU5UX05PREUpIHtcbiAgICBjdXJyTm9kZS5uZXh0U2libGluZy50ZXh0Q29udGVudCA9IHZhbDtcbiAgICAvLyBUaGlzIGlzIHRoZSBjYXNlIHdoZXJlIHdlIGFyZSBjcmVhdGluZyBuZXcgRE9NIGZyb20gYW4gZW1wdHkgZWxlbWVudC5cbiAgfSBlbHNlIHtcbiAgICBjdXJyTm9kZS5hcHBlbmRDaGlsZChkb2N1bWVudC5jcmVhdGVDb21tZW50KHZhbCkpO1xuICB9XG4gIGluY3JlbWVudGFsZG9tLnNraXBOb2RlKCk7XG59XG5cbmV4cG9ydCB7XG4gIFNveUVsZW1lbnQgYXMgJFNveUVsZW1lbnQsXG4gIHByaW50IGFzICQkcHJpbnQsXG4gIGh0bWxUb1N0cmluZyBhcyAkJGh0bWxUb1N0cmluZyxcbiAgbWFrZUh0bWwgYXMgJCRtYWtlSHRtbCxcbiAgbWFrZUF0dHJpYnV0ZXMgYXMgJCRtYWtlQXR0cmlidXRlcyxcbiAgY2FsbER5bmFtaWNKcyBhcyAkJGNhbGxEeW5hbWljSnMsXG4gIGNhbGxEeW5hbWljQ3NzIGFzICQkY2FsbER5bmFtaWNDc3MsXG4gIGNhbGxEeW5hbWljSFRNTCBhcyAkJGNhbGxEeW5hbWljSFRNTCxcbiAgY2FsbER5bmFtaWNBdHRyaWJ1dGVzIGFzICQkY2FsbER5bmFtaWNBdHRyaWJ1dGVzLFxuICBjYWxsRHluYW1pY1RleHQgYXMgJCRjYWxsRHluYW1pY1RleHQsXG4gIHRyeUdldEVsZW1lbnQgYXMgJCR0cnlHZXRFbGVtZW50LFxuICBwcmludER5bmFtaWNBdHRyIGFzICQkcHJpbnREeW5hbWljQXR0cixcbiAgdmlzaXRIdG1sQ29tbWVudE5vZGUgYXMgJCR2aXNpdEh0bWxDb21tZW50Tm9kZSxcbn07XG4iXX0=
