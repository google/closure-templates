/*
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
 */

import * as googSoy from 'goog:goog.soy';  // from //javascript/closure/soy
import SanitizedContent from 'goog:goog.soy.data.SanitizedContent'; // from //javascript/closure/soy:data
import SanitizedContentKind from 'goog:goog.soy.data.SanitizedContentKind'; // from //javascript/closure/soy:data
import SanitizedHtml from 'goog:goog.soy.data.SanitizedHtml'; // from //javascript/closure/soy:data
import SanitizedHtmlAttribute from 'goog:goog.soy.data.SanitizedHtmlAttribute'; // from //javascript/closure/soy:data
import SanitizedJs from 'goog:goog.soy.data.SanitizedJs'; // from //javascript/closure/soy:data
import SanitizedUri from 'goog:goog.soy.data.SanitizedUri'; // from //javascript/closure/soy:data
import UnsanitizedText from 'goog:goog.soy.data.UnsanitizedText'; // from //javascript/closure/soy:data
import * as googString from 'goog:goog.string';  // from //javascript/closure/string
import * as soy from 'goog:soy';  // from //javascript/template/soy:soy_usegoog_js
import {isAttribute} from 'goog:soy.checks';  // from //javascript/template/soy:checks
import {ordainSanitizedHtml} from 'goog:soydata.VERY_UNSAFE';  // from //javascript/template/soy:soy_usegoog_js
import * as incrementaldom from 'incrementaldom';  // from //third_party/javascript/incremental_dom:incrementaldom

import {IncrementalDomRenderer, isProposedKeySuffixOfCurrentKey} from './api_idom';
import {IdomFunction, PatchFunction, SoyElement} from './element_lib_idom';
import {getSoyUntyped} from './global';

type TextualValue =
    UnsanitizedText|SanitizedUri|SanitizedJs|string|number|boolean;

// Declare properties that need to be applied not as attributes but as
// actual DOM properties.
const {attributes, getKey, isDataInitialized} = incrementaldom;

const defaultIdomRenderer = new IncrementalDomRenderer();

type IdomTemplate<A, B> =
    (idom: IncrementalDomRenderer, params: A, ijData: B) => void;
type SoyTemplate<A, B> = (params: A, ijData: B) => string|SanitizedContent;
type LetFunction = (idom: IncrementalDomRenderer) => void;
type Template<A, B> = IdomTemplate<A, B>|SoyTemplate<A, B>;

// tslint:disable-next-line:no-any
attributes['checked'] = (el: Element, name: string, value: any) => {
  // We don't use !!value because:
  // 1. If value is '' (this is the case where a user uses <div checked />),
  //    the checked value should be true, but '' is falsy.
  // 2. If value is 'false', the checked value should be false, but
  //    'false' is truthy.
  el.setAttribute('checked', value);
  (el as HTMLInputElement).checked =
      !(value === false || value === 'false' || value === undefined);
};

// tslint:disable-next-line:no-any
attributes['value'] = (el: Element, name: string, value: any) => {
  (el as HTMLInputElement).value = value;
  el.setAttribute('value', value);
};

// Soy uses the {key} command syntax, rather than HTML attributes, to
// indicate element keys.
incrementaldom.setKeyAttributeName(null);

/**
 * Returns the template object stored in the currentPointer element if it is the
 * correct type.  Otherwise, returns null.
 */
function tryGetElement<T extends SoyElement<{}, {}>>(
    incrementaldom: IncrementalDomRenderer, elementClassCtor: new () => T,
    firstElementKey: string) {
  let currentPointer = incrementaldom.currentPointer();
  while (currentPointer != null) {
    const el = getSoyUntyped(currentPointer);
    if (el instanceof elementClassCtor && isDataInitialized(currentPointer)) {
      const currentPointerKey = getKey(currentPointer) as string;
      const currentPointerKeyArr = JSON.parse(currentPointerKey);
      if (isProposedKeySuffixOfCurrentKey(
              incrementaldom.getCurrentKeyStack().concat(firstElementKey),
              currentPointerKeyArr)) {
        return el;
      }
    }
    currentPointer = currentPointer.nextSibling;
  }
  return null;
}

// tslint:disable-next-line:no-any Attaching arbitrary attributes to function.
function makeHtml(idomFn: any): IdomFunction {
  idomFn.toString = (renderer: IncrementalDomRenderer = defaultIdomRenderer) =>
      htmlToString(idomFn, renderer);
  idomFn.toBoolean = () => toBoolean(idomFn);
  idomFn.contentKind = SanitizedContentKind.HTML;
  return idomFn as IdomFunction;
}

// tslint:disable-next-line:no-any Attaching arbitrary attributes to function.
function makeAttributes(idomFn: any): IdomFunction {
  idomFn.toString = () => attributesToString(idomFn);
  idomFn.toBoolean = () => toBoolean(idomFn);
  idomFn.contentKind = SanitizedContentKind.ATTRIBUTES;
  return idomFn as IdomFunction;
}

/**
 * TODO(tomnguyen): Issue a warning in these cases so that users know that
 * expensive behavior is happening.
 */
function htmlToString(
    fn: LetFunction, renderer: IncrementalDomRenderer = defaultIdomRenderer) {
  const el = document.createElement('div');
  incrementaldom.patch(el, () => fn(renderer));
  return el.innerHTML;
}

function attributesFactory(fn: PatchFunction): PatchFunction {
  return () => {
    incrementaldom.elementOpenStart('div');
    fn(defaultIdomRenderer);
    incrementaldom.elementOpenEnd();
    incrementaldom.elementClose('div');
  };
}

/**
 * TODO(tomnguyen): Issue a warning in these cases so that users know that
 * expensive behavior is happening.
 */
function attributesToString(fn: PatchFunction): string {
  const elFn = attributesFactory(fn);
  const el = document.createElement('div');
  incrementaldom.patchOuter(el, elFn);
  const s: string[] = [];
  for (let i = 0; i < el.attributes.length; i++) {
    s.push(`${el.attributes[i].name}=${el.attributes[i].value}`);
  }
  // The sort is important because attribute order varies per browser.
  return s.sort().join(' ');
}

function toBoolean(fn: IdomFunction) {
  return fn.toString().length > 0;
}

/**
 * Calls an expression in case of a function or outputs it as text content.
 */
function renderDynamicContent(
    incrementaldom: IncrementalDomRenderer, expr: unknown) {
  // TODO(lukes): check content kind == html
  if (typeof expr === 'function') {
    // The Soy compiler will validate the content kind of the parameter.
    expr(incrementaldom);
  } else {
    incrementaldom.text(String(expr));
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
 */
const htmlAttributeRegExp: RegExp =
    /([^\t\n\f\r />=]+)[\t\n\f\r ]*(?:=[\t\n\f\r ]*(?:"([^"]*)"?|'([^']*)'?|([^\t\n\f\r >]*)))?/g;

function splitAttributes(attributes: string) {
  const nameValuePairs: string[][] = [];
  String(attributes).replace(htmlAttributeRegExp, (_, name, dq, sq, uq) => {
    nameValuePairs.push(
        [name, googString.unescapeEntities(dq || sq || uq || '')]);
    return ' ';
  });
  return nameValuePairs;
}

/**
 * Calls an expression in case of a function or outputs it as text content.
 */
function callDynamicAttributes<A, B>(
    incrementaldom: IncrementalDomRenderer,
    // tslint:disable-next-line:no-any
    expr: Template<A, B>, data: A, ij: B) {
  // tslint:disable-next-line:no-any Attaching arbitrary attributes to function.
  const type = (expr as any as IdomFunction).contentKind;
  if (type === SanitizedContentKind.ATTRIBUTES) {
    (expr as IdomTemplate<A, B>)(incrementaldom, data, ij);
  } else {
    let val: string|SanitizedHtmlAttribute;
    if (type === SanitizedContentKind.HTML) {
      // This effectively negates the value of splitting a string. However,
      // This can be removed if Soy decides to treat attribute printing
      // and attribute names differently.
      val = soy.$$filterHtmlAttributes(htmlToString(
          () => (expr as IdomTemplate<A, B>)(defaultIdomRenderer, data, ij)));
    } else {
      val = (expr as SoyTemplate<A, B>)(data, ij) as SanitizedHtmlAttribute;
    }
    printDynamicAttr(incrementaldom, val);
  }
}

/**
 * Prints an expression whose type is not statically known to be of type
 * "attributes". The expression is tested at runtime and evaluated depending
 * on what type it is. For example, if a string is printed in a context
 * that expects attributes, the string is evaluated dynamically to compute
 * attributes.
 */
function printDynamicAttr(
    incrementaldom: IncrementalDomRenderer,
    expr: SanitizedHtmlAttribute|string|boolean|IdomFunction) {
  if (goog.isFunction(expr) &&
      (expr as IdomFunction).contentKind === SanitizedContentKind.ATTRIBUTES) {
    // tslint:disable-next-line:no-any
    (expr as any as LetFunction)(incrementaldom);
    return;
  }
  const attributes = splitAttributes(expr.toString());
  const isExprAttribute = isAttribute(expr);
  for (const attribute of attributes) {
    const attrName = isExprAttribute ? attribute[0] :
                                       soy.$$filterHtmlAttributes(attribute[0]);
    if (attrName === 'zSoyz') {
      incrementaldom.attr(attrName, '');
    } else {
      incrementaldom.attr(String(attrName), String(attribute[1]));
    }
  }
}

/**
 * Calls an expression in case of a function or outputs it as text content.
 */
function callDynamicHTML<A, B>(
    incrementaldom: IncrementalDomRenderer, expr: Template<A, B>, data: A,
    ij: B) {
  // tslint:disable-next-line:no-any Attaching arbitrary attributes to function.
  const type = (expr as any as IdomFunction).contentKind;
  if (type === SanitizedContentKind.HTML) {
    (expr as IdomTemplate<A, B>)(incrementaldom, data, ij);
  } else if (type === SanitizedContentKind.ATTRIBUTES) {
    const val = attributesToString(
        () => (expr as IdomTemplate<A, B>)(defaultIdomRenderer, data, ij));
    incrementaldom.text(val);
  } else {
    const val = (expr as SoyTemplate<A, B>)(data, ij);
    incrementaldom.text(String(val));
  }
}

function callDynamicCss<A, B>(
    // tslint:disable-next-line:no-any Attaching  attributes to function.
    incrementaldom: IncrementalDomRenderer, expr: (a: A, b: B) => any, data: A,
    ij: B) {
  const val = callDynamicText<A, B>(expr, data, ij, soy.$$filterCssValue);
  incrementaldom.text(String(val));
}

function callDynamicJs<A, B>(
    // tslint:disable-next-line:no-any Attaching attributes to function.
    incrementaldom: IncrementalDomRenderer, expr: (a: A, b: B) => any, data: A,
    ij: B) {
  const val = callDynamicText<A, B>(expr, data, ij, soy.$$escapeJsValue);
  incrementaldom.text(String(val));
}

/**
 * Calls an expression and coerces it to a string for cases where an IDOM
 * function needs to be concatted to a string.
 */
function callDynamicText<A, B>(
    // tslint:disable-next-line:no-any
    expr: Template<A, B>, data: A, ij: B, escFn?: (i: string) => string) {
  const transformFn = escFn ? escFn : (a: string) => a;
  // tslint:disable-next-line:no-any Attaching arbitrary attributes to function.
  const type = (expr as any as IdomFunction).contentKind;
  let val: string|SanitizedContent;
  if (type === SanitizedContentKind.HTML) {
    val = transformFn(htmlToString(
        () => (expr as IdomTemplate<A, B>)(defaultIdomRenderer, data, ij)));
  } else if (type === SanitizedContentKind.ATTRIBUTES) {
    val = transformFn(attributesToString(
        () => (expr as IdomTemplate<A, B>)(defaultIdomRenderer, data, ij)));
  } else {
    val = (expr as SoyTemplate<A, B>)(data, ij);
  }
  return val;
}

declare global {
  interface Element {
    __innerHTML: string;
  }
}

/**
 * Prints an expression depending on its type.
 */
function print(
    incrementaldom: IncrementalDomRenderer, expr: unknown,
    isSanitizedContent?: boolean|undefined) {
  if (expr instanceof SanitizedHtml || isSanitizedContent) {
    const content = String(expr);
    // If the string has no < or &, it's definitely not HTML. Otherwise
    // proceed with caution.
    if (content.indexOf('<') < 0 && content.indexOf('&') < 0) {
      incrementaldom.text(content);
    } else {
      // For HTML content we need to insert a custom element where we can place
      // the content without incremental dom modifying it.
      const el = incrementaldom.elementOpen('html-blob');
      if (el && el.__innerHTML !== content) {
        googSoy.renderHtml(el, ordainSanitizedHtml(content));
        el.__innerHTML = content;
      }
      incrementaldom.skip();
      incrementaldom.elementClose('html-blob');
    }
  } else {
    renderDynamicContent(incrementaldom, expr);
  }
}

function visitHtmlCommentNode(
    incrementaldom: IncrementalDomRenderer, val: string) {
  const currNode = incrementaldom.currentElement();
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
  incrementaldom.skipNode();
}

export {
  SoyElement as $SoyElement,
  print as $$print,
  htmlToString as $$htmlToString,
  makeHtml as $$makeHtml,
  makeAttributes as $$makeAttributes,
  callDynamicJs as $$callDynamicJs,
  callDynamicCss as $$callDynamicCss,
  callDynamicHTML as $$callDynamicHTML,
  callDynamicAttributes as $$callDynamicAttributes,
  callDynamicText as $$callDynamicText,
  tryGetElement as $$tryGetElement,
  printDynamicAttr as $$printDynamicAttr,
  visitHtmlCommentNode as $$visitHtmlCommentNode,
};
