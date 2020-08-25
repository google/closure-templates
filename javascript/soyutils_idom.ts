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

import SafeHtml from 'goog:goog.html.SafeHtml'; // from //javascript/closure/html:safehtml
import * as googSoy from 'goog:goog.soy';  // from //javascript/closure/soy
import SanitizedContent from 'goog:goog.soy.data.SanitizedContent'; // from //javascript/closure/soy:data
import SanitizedContentKind from 'goog:goog.soy.data.SanitizedContentKind'; // from //javascript/closure/soy:data
import SanitizedHtml from 'goog:goog.soy.data.SanitizedHtml'; // from //javascript/closure/soy:data
import SanitizedHtmlAttribute from 'goog:goog.soy.data.SanitizedHtmlAttribute'; // from //javascript/closure/soy:data
import * as soy from 'goog:soy';  // from //javascript/template/soy:soy_usegoog_js
import {isAttribute} from 'goog:soy.checks';  // from //javascript/template/soy:checks
import {ordainSanitizedHtml} from 'goog:soydata.VERY_UNSAFE';  // from //javascript/template/soy:soy_usegoog_js
import * as incrementaldom from 'incrementaldom';  // from //third_party/javascript/incremental_dom:incrementaldom

import {FalsinessRenderer, IncrementalDomRenderer, isMatchingKey, patch, patchOuter} from './api_idom';
import {splitAttributes} from './attributes';
import {IdomFunction, PatchFunction, SoyElement} from './element_lib_idom';
import {getSoyUntyped} from './global';

// Declare properties that need to be applied not as attributes but as
// actual DOM properties.
const {attributes, currentContext} = incrementaldom;

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
  if (value == null) {
    el.removeAttribute('checked');
    (el as HTMLInputElement).checked = false;
  } else {
    el.setAttribute('checked', value);
    (el as HTMLInputElement).checked = !(value === false || value === 'false');
  }
};

// tslint:disable-next-line:no-any
attributes['value'] = (el: Element, name: string, value: any) => {
  if (value == null) {
    el.removeAttribute('value');
    (el as HTMLInputElement).value = '';
  } else {
    el.setAttribute('value', value);
    (el as HTMLInputElement).value = value;
  }
};

// Soy uses the {key} command syntax, rather than HTML attributes, to
// indicate element keys.
incrementaldom.setKeyAttributeName('soy-server-key');

/**
 * Tries to find an existing Soy element, if it exists. Otherwise, it creates
 * one. Afterwards, it queues up a Soy element (see docs for queueSoyElement)
 * and then proceeds to render the Soy element.
 */
function handleSoyElement<DATA, T extends SoyElement<DATA, {}>>(
    incrementaldom: IncrementalDomRenderer,
    elementClassCtor: new (data: DATA, ij: unknown) => T,
    firstElementKey: string, data: DATA, ijData: unknown) {
  // If we're just testing truthiness, record an element but don't do anythng.
  if (incrementaldom instanceof FalsinessRenderer) {
    incrementaldom.open('div');
    incrementaldom.close();
    return null;
  }
  const soyElementKey = firstElementKey + incrementaldom.getCurrentKeyStack();
  let currentPointer = incrementaldom.currentPointer();
  let el: T|null = null;
  while (currentPointer != null) {
    const maybeSoyEl = getSoyUntyped(currentPointer) as T;
    // We cannot use the current key of the element because many layers
    // of template calls may have happened. We can only be sure that the Soy
    // element was the same if the key constructed is matching the key current
    // when the {element} command was created.
    if (maybeSoyEl instanceof elementClassCtor &&
        isMatchingKey(soyElementKey, maybeSoyEl.key)) {
      el = maybeSoyEl;
      break;
    }
    // If we extend beyond the current scope of the patch, we may reach an
    // element of an already hydrated element.
    if (currentContext()?.node === currentPointer) {
      break;
    }
    currentPointer = currentPointer.nextSibling;
  }
  if (!el) {
    el = new elementClassCtor(data, ijData);
    el.key = soyElementKey;
    // NOTE(b/166257386): Without this, SoyElement re-renders don't have logging
    el.setLogger(incrementaldom.getLogger());
  }
  el.queueSoyElement(incrementaldom, data);
  el.renderInternal(incrementaldom, data);
  return el;
}

// tslint:disable-next-line:no-any Attaching arbitrary attributes to function.
function makeHtml(idomFn: any): IdomFunction {
  const fn = (() => {
               throw new Error('Should not be called directly');
             }) as unknown as (SanitizedHtml & IdomFunction);
  // tslint:disable-next-line:no-any Hack :(
  (fn as any).prototype = SanitizedHtml;
  fn.invoke = (renderer: IncrementalDomRenderer = defaultIdomRenderer) =>
      idomFn(renderer);
  fn.toString = (renderer: IncrementalDomRenderer = defaultIdomRenderer) =>
      htmlToString(idomFn, renderer);
  fn.getContent = fn.toString;
  fn.toBoolean = () => isTruthy(idomFn);
  fn.contentKind = SanitizedContentKind.HTML;
  fn.isInvokableFn = true;
  return fn as IdomFunction;
}

// tslint:disable-next-line:no-any Attaching arbitrary attributes to function.
function makeAttributes(idomFn: any): IdomFunction&SanitizedHtmlAttribute {
  const fn = (() => {
               throw new Error('Should not be called directly');
             }) as unknown as (SanitizedHtmlAttribute & IdomFunction);
  // tslint:disable-next-line:no-any Hack :(
  (fn as any).prototype = SanitizedHtmlAttribute;
  fn.invoke = (renderer: IncrementalDomRenderer = defaultIdomRenderer) =>
      idomFn(renderer);
  fn.toString = () => attributesToString(idomFn);
  fn.getContent = fn.toString;
  fn.toBoolean = () => isTruthy(idomFn);
  fn.contentKind = SanitizedContentKind.ATTRIBUTES;
  fn.isInvokableFn = true;
  return fn as IdomFunction & SanitizedHtmlAttribute;
}

/**
 * TODO(tomnguyen): Issue a warning in these cases so that users know that
 * expensive behavior is happening.
 */
function htmlToString(
    fn: LetFunction, renderer: IncrementalDomRenderer = defaultIdomRenderer) {
  const el = document.createElement('div');
  patch(el, () => {
    fn(renderer);
  });
  return el.innerHTML;
}

function attributesFactory(fn: PatchFunction): PatchFunction {
  return () => {
    incrementaldom.open('div');
    fn(defaultIdomRenderer);
    incrementaldom.applyAttrs();
    incrementaldom.close();
  };
}

/**
 * TODO(tomnguyen): Issue a warning in these cases so that users know that
 * expensive behavior is happening.
 */
function attributesToString(fn: PatchFunction): string {
  const elFn = attributesFactory(fn);
  const el = document.createElement('div');
  patchOuter(el, elFn);
  const s: string[] = [];
  for (let i = 0; i < el.attributes.length; i++) {
    if (el.attributes[i].value === '') {
      s.push(el.attributes[i].name);
    } else {
      s.push(`${el.attributes[i].name}=\'${
          soy.$$escapeHtmlAttribute(el.attributes[i].value)}\'`);
    }
  }
  // The sort is important because attribute order varies per browser.
  return s.sort().join(' ');
}

/**
 * Calls an expression in case of a function or outputs it as text content.
 */
function renderDynamicContent(
    incrementaldom: IncrementalDomRenderer, expr: IdomFunction) {
  // TODO(lukes): check content kind == html
  if (expr && expr.isInvokableFn) {
    // The Soy compiler will validate the content kind of the parameter.
    expr.invoke(incrementaldom);
  } else {
    incrementaldom.text(String(expr));
  }
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
      val = soy.$$filterHtmlAttributes(htmlToString(() => {
        (expr as IdomTemplate<A, B>)(defaultIdomRenderer, data, ij);
      }));
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
  if ((expr as IdomFunction).isInvokableFn &&
      (expr as IdomFunction).contentKind === SanitizedContentKind.ATTRIBUTES) {
    // tslint:disable-next-line:no-any
    (expr as IdomFunction).invoke(incrementaldom);
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
    const val = attributesToString(() => {
      (expr as IdomTemplate<A, B>)(defaultIdomRenderer, data, ij);
    });
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
    val = transformFn(htmlToString(() => {
      (expr as IdomTemplate<A, B>)(defaultIdomRenderer, data, ij);
    }));
  } else if (type === SanitizedContentKind.ATTRIBUTES) {
    val = transformFn(attributesToString(() => {
      (expr as IdomTemplate<A, B>)(defaultIdomRenderer, data, ij);
    }));
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
  if (expr instanceof SanitizedHtml || isSanitizedContent ||
      expr instanceof SafeHtml) {
    const content =
        expr instanceof SafeHtml ? SafeHtml.unwrap(expr) : String(expr);
    // If the string has no < or &, it's definitely not HTML. Otherwise
    // proceed with caution.
    if (!content.includes('<') && !content.includes('&')) {
      incrementaldom.text(content);
    } else {
      // For HTML content we need to insert a custom element where we can place
      // the content without incremental dom modifying it.
      const el = incrementaldom.open('html-blob');
      if (el && el.__innerHTML !== content) {
        googSoy.renderHtml(el, ordainSanitizedHtml(content));
        el.__innerHTML = content;
      }
      incrementaldom.skip();
      incrementaldom.close();
    }
  } else {
    renderDynamicContent(incrementaldom, expr as IdomFunction);
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

function isTruthy(expr: unknown): boolean {
  if (!expr) return false;
  if (expr instanceof SanitizedContent) return !!expr.getContent();

  // idom callbacks.
  if ((expr as IdomFunction).isInvokableFn) {
    const renderer = new FalsinessRenderer();
    (expr as IdomFunction)
        .invoke(renderer as unknown as IncrementalDomRenderer);
    return renderer.didRender();
  }

  // true, numbers, strings.
  if (typeof expr !== 'object') return !!String(expr);

  // Objects, arrays.
  return true;
}

export {
  SoyTemplate as $SoyTemplate,
  SoyElement as $SoyElement,
  isTruthy as $$isTruthy,
  print as $$print,
  htmlToString as $$htmlToString,
  makeHtml as $$makeHtml,
  makeAttributes as $$makeAttributes,
  callDynamicJs as $$callDynamicJs,
  callDynamicCss as $$callDynamicCss,
  callDynamicHTML as $$callDynamicHTML,
  callDynamicAttributes as $$callDynamicAttributes,
  callDynamicText as $$callDynamicText,
  handleSoyElement as $$handleSoyElement,
  printDynamicAttr as $$printDynamicAttr,
  visitHtmlCommentNode as $$visitHtmlCommentNode,
};
