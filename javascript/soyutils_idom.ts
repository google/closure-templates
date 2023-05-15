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

import {ordainSanitizedHtml} from 'goog:soydata.VERY_UNSAFE'; // from //javascript/template/soy:soy_usegoog_js
import {isAttribute} from 'google3/javascript/template/soy/checks';
import * as soy from 'google3/javascript/template/soy/soyutils_usegoog';
import {Logger} from 'google3/javascript/template/soy/soyutils_velog';
import {cacheReturnValue} from 'google3/third_party/javascript/closure/functions/functions';
import {SafeHtml} from 'google3/third_party/javascript/closure/html/safehtml';
import {
  SanitizedContent,
  SanitizedContentKind,
  SanitizedHtml,
  SanitizedHtmlAttribute,
} from 'google3/third_party/javascript/closure/soy/data';
import * as googSoy from 'google3/third_party/javascript/closure/soy/soy';
import * as incrementaldom from 'incrementaldom'; // from //third_party/javascript/incremental_dom:incrementaldom

import {
  attributes,
  FalsinessRenderer,
  IncrementalDomRenderer,
  NullRenderer,
  patch,
  patchOuter,
} from './api_idom';
import {splitAttributes} from './attributes';
import {IdomFunction, PatchFunction, SoyElement} from './element_lib_idom';
import {getSoyUntyped, USE_TEMPLATE_CLONING} from './global';
import {
  IdomSyncState,
  IdomTemplate,
  IjData,
  SoyTemplate,
  Template,
} from './templates';

// Declare properties that need to be applied not as attributes but as
// actual DOM properties.

const defaultIdomRenderer = new IncrementalDomRenderer();
const htmlToStringRenderer = new IncrementalDomRenderer();

const NODE_PART = '<?child-node-part?><?/child-node-part?>';

/**
 * A template acceptor is an object that a template can receive context from.
 * This acceptor receive `template.bind(acceptor)` and the template will use
 * state and data fields from the acceptor.
 */
interface TemplateAcceptor<TDATA extends {}> {
  template: IdomTemplate<TDATA>;
  renderInternal(renderer: IncrementalDomRenderer): void;
  render(renderer?: IncrementalDomRenderer): void;
  handleCustomElementRuntime(): boolean;
  /**
   * This is the idom template sync state function. Should not call this
   * directly.
   */
  idomSyncFn: IdomSyncState<TDATA>;
  /**
   * This is a wrapper around idomSyncFn. Only this should be called directly.
   */
  syncStateFromProps: IdomSyncState<TDATA>;
  setIdomSkipTemplate(idomSkipTemplate: () => void): void;
  hasLogger(): boolean;
  setLogger(logger: Logger | null): void;
}

/**
 * Takes a custom element and attaches relevant fields to it necessary for
 * rendering.
 */
function upgrade<X extends {}, T extends TemplateAcceptor<X>>(
  acceptor: new () => T,
  template: IdomTemplate<X>,
  sync: IdomSyncState<X>,
  init: (this: T) => void
) {
  acceptor.prototype.init = init;
  acceptor.prototype.idomSyncFn = sync;
  acceptor.prototype.idomRenderer = new IncrementalDomRenderer();
  acceptor.prototype.idomPatcher = patchOuter;
  acceptor.prototype.idomTemplate = template;
  acceptor.prototype.render = function (
    this: T,
    renderer = new IncrementalDomRenderer()
  ) {
    const self = this as TemplateAcceptor<X>;
    patchOuter(this as unknown as HTMLElement, () => {
      // this cast may be unsafe...
      self.renderInternal(renderer);
    });
  };
}

attributes['checked'] = (el: Element, name: string, value: unknown) => {
  // We don't use !!value because:
  // 1. If value is '' (this is the case where a user uses <div checked />),
  //    the checked value should be true, but '' is falsy.
  // 2. If value is 'false', the checked value should be false, but
  //    'false' is truthy.
  if (value == null) {
    el.removeAttribute('checked');
    (el as HTMLInputElement).checked = false;
  } else {
    el.setAttribute('checked', String(value));
    (el as HTMLInputElement).checked = !(value === false || value === 'false');
  }
};

attributes['value'] = (el: Element, name: string, value: unknown) => {
  if (value == null) {
    el.removeAttribute('value');
    (el as HTMLInputElement).value = '';
  } else {
    el.setAttribute('value', String(value));
    (el as HTMLInputElement).value = String(value);
  }
};

// Soy uses the {key} command syntax, rather than HTML attributes, to
// indicate element keys.
incrementaldom.setKeyAttributeName('ssk');

/**
 * Tries to find an existing Soy element, if it exists. Otherwise, it creates
 * one. Afterwards, it queues up a Soy element (see docs for queueSoyElement)
 * and then proceeds to render the Soy element.
 */
function handleSoyElement<T extends TemplateAcceptor<{}>>(
  incrementaldom: IncrementalDomRenderer,
  elementClassCtor: new () => T,
  firstElementKey: string,
  tagName: string,
  data: {},
  ijData: IjData,
  template: IdomTemplate<unknown>
): T | null {
  // If we're just testing truthiness, record an element but don't do anythng.
  if (incrementaldom instanceof FalsinessRenderer) {
    incrementaldom.open('div');
    incrementaldom.close();
    return null;
  }
  const soyElementKey = firstElementKey + incrementaldom.getCurrentKeyStack();
  let soyElement: SoyElement<{}, {}> =
    new elementClassCtor() as unknown as SoyElement<{}, {}>;
  soyElement = new elementClassCtor() as unknown as SoyElement<{}, {}>;
  soyElement.data = data;
  soyElement.ijData = ijData;
  soyElement.key = soyElementKey;
  soyElement.template = template.bind(soyElement);
  // NOTE(b/166257386): Without this, SoyElement re-renders don't have logging
  soyElement.setLogger(incrementaldom.getLogger());
  const isTemplateCloning =
    ijData && (ijData as {[key: string]: unknown})['inTemplateCloning'];
  /**
   * Open the element early in order to execute lifeycle hooks. Suppress the
   * next element open since we've already opened it.
   */
  let element = isTemplateCloning
    ? null
    : incrementaldom.open(tagName, firstElementKey);

  const oldOpen = incrementaldom.open;
  incrementaldom.open = (tagName, soyElementKey) => {
    if (element) {
      if (soyElementKey !== firstElementKey) {
        throw new Error('Expected tag name and key to match.');
      }
    } else {
      element = oldOpen.call(incrementaldom, tagName, soyElementKey);
      soyElement.node = element!;
      element!.__soy = soyElement;
    }
    incrementaldom.open = oldOpen;
    return element!;
  };

  if (ijData && (ijData as {[key: string]: unknown})['inTemplateCloning']) {
    soyElement.syncStateFromData(data);
    return soyElement as unknown as T;
  }

  if (!element) {
    // Template still needs to execute in order to trigger logging.
    template.call(soyElement, incrementaldom, data, ijData);
    return null;
  }
  if (getSoyUntyped(element) instanceof elementClassCtor) {
    soyElement = getSoyUntyped(element)!;
  }
  const maybeSkip = soyElement.handleSoyElementRuntime(element, data);
  soyElement.template = template.bind(soyElement);
  soyElement.ijData = ijData;
  if (maybeSkip) {
    incrementaldom.skip();
    incrementaldom.close();
    incrementaldom.open = oldOpen;
    return null;
  }
  return soyElement as unknown as T;
}

function makeHtml(idomFn: PatchFunction): IdomFunction {
  const fn = ((renderer: IncrementalDomRenderer = defaultIdomRenderer) => {
    idomFn(renderer);
  }) as unknown as SanitizedHtml & IdomFunction;
  fn.invoke = (renderer: IncrementalDomRenderer = defaultIdomRenderer) => {
    idomFn(renderer);
  };
  fn.toString = (renderer: IncrementalDomRenderer = htmlToStringRenderer) =>
    htmlToString(idomFn, renderer);
  fn.getContent = fn.toString;
  fn.contentKind = SanitizedContentKind.HTML;
  fn.toSafeHtml = () => ordainSanitizedHtml(fn.getContent()).toSafeHtml();
  fn.renderElement = (el: Element | ShadowRoot) => {
    incrementaldom.patch(el, () => {
      fn.invoke(defaultIdomRenderer);
    });
  };
  fn.renderAsElement = () => {
    const el = document.createElement('div');
    incrementaldom.patch(el, () => {
      fn.invoke(defaultIdomRenderer);
    });
    if (el.childNodes.length === 1 && el.childNodes[0] instanceof Element) {
      return el.childNodes[0];
    }
    return el;
  };
  fn.isInvokableFn = true;
  return fn;
}

/**
 * Wraps an idom callback into an `attributes`-typed IdomFunction.
 *
 * The returned object can also be used as a non-idom SanitizedHtmlAttribute
 *
 * @param idomFn A callback from a Soy template.
 * @param stringContent The correctly-escaped content that the callback renders.
 *     If this is omitted, calling `toString()` will execute the callback into a
 *     temporary element, which is slow.  If this is a function, it will only be
 *     called on demand, but will be cached.
 */
function makeAttributes(
  idomFn: PatchFunction,
  stringContent?: string | (() => string)
): IdomFunction & SanitizedHtmlAttribute {
  const fn = (() => {
    throw new Error('Should not be called directly');
  }) as unknown as SanitizedHtmlAttribute & IdomFunction;

  Object.setPrototypeOf(fn, SanitizedHtmlAttribute.prototype);
  fn.invoke = (renderer: IncrementalDomRenderer = defaultIdomRenderer) => {
    idomFn(renderer);
  };

  if (stringContent) {
    fn.toString = toLazyFunction(stringContent);
  } else {
    fn.toString = () => attributesToString(idomFn);
  }

  fn.getContent = fn.toString;
  fn.contentKind = SanitizedContentKind.ATTRIBUTES;
  fn.isInvokableFn = true;
  return fn;
}

function toLazyFunction<T extends string | number>(fn: T | (() => T)): () => T {
  if (typeof fn === 'function') return cacheReturnValue(fn);
  return () => fn;
}

/**
 * TODO(tomnguyen): Issue a warning in these cases so that users know that
 * expensive behavior is happening.
 */
function htmlToString(
  fn: PatchFunction,
  renderer: IncrementalDomRenderer = htmlToStringRenderer
) {
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
  // idom's PatchFunction type has an optional parameter.
  patchOuter(el, elFn as (a?: IncrementalDomRenderer) => void);
  const s: string[] = [];
  for (let i = 0; i < el.attributes.length; i++) {
    if (el.attributes[i].value === '') {
      s.push(el.attributes[i].name);
    } else {
      s.push(
        `${el.attributes[i].name}='${soy.$$escapeHtmlAttribute(
          el.attributes[i].value
        )}'`
      );
    }
  }
  // The sort is important because attribute order varies per browser.
  return s.sort().join(' ');
}

/**
 * Calls an expression in case of a function or outputs it as text content.
 */
function renderDynamicContent(
  incrementaldom: IncrementalDomRenderer,
  expr: IdomFunction
) {
  // TODO(lukes): check content kind == html
  if (expr && expr.isInvokableFn) {
    // The Soy compiler will validate the content kind of the parameter.
    expr.invoke(incrementaldom);
  } else {
    incrementaldom.text(String(expr));
  }
}

/** Determines whether the template is idom */
function isIdom<TParams>(
  template: Template<TParams>
): template is IdomTemplate<TParams> {
  const contentKind = (template as IdomFunction).contentKind;
  return (
    contentKind &&
    // idom generates regular templates for all other content kinds.
    (contentKind === SanitizedContentKind.HTML ||
      contentKind === SanitizedContentKind.ATTRIBUTES)
  );
}

/**
 * Calls an expression in case of a function or outputs it as text content.
 */
function callDynamicAttributes<TParams>(
  incrementaldom: IncrementalDomRenderer,
  expr: Template<TParams>,
  data: TParams,
  ij: IjData
) {
  if (isIdom(expr)) {
    switch ((expr as IdomFunction).contentKind) {
      case SanitizedContentKind.ATTRIBUTES:
        expr(incrementaldom, data, ij);
        break;
      case SanitizedContentKind.HTML:
        // This effectively negates the value of splitting a string. However,
        // This can be removed if Soy decides to treat attribute printing
        // and attribute names differently.
        const val = soy.$$filterHtmlAttributes(
          htmlToString(() => {
            expr(defaultIdomRenderer, data, ij);
          })
        );
        printDynamicAttr(incrementaldom, val);
        break;
      default:
        throw new Error('Bad content kind');
    }
  } else {
    const val = expr(data, ij) as SanitizedHtmlAttribute;
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
  expr: SanitizedHtmlAttribute | string | boolean | IdomFunction
) {
  if (
    (expr as IdomFunction).isInvokableFn &&
    (expr as IdomFunction).contentKind === SanitizedContentKind.ATTRIBUTES
  ) {
    (expr as IdomFunction).invoke(incrementaldom);
    return;
  }
  const attributes = splitAttributes(expr.toString());
  const isExprAttribute = isAttribute(expr);
  for (const attribute of attributes) {
    const attrName = isExprAttribute
      ? attribute[0]
      : soy.$$filterHtmlAttributes(attribute[0]);
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
function callDynamicHTML<TParams>(
  incrementaldom: IncrementalDomRenderer,
  expr: Template<TParams>,
  data: TParams,
  ij: IjData,
  variant?: string
) {
  if (isIdom(expr)) {
    switch ((expr as IdomFunction).contentKind) {
      case SanitizedContentKind.HTML:
        expr(incrementaldom, data, ij, variant);
        break;
      case SanitizedContentKind.ATTRIBUTES:
        const val = attributesToString(() => {
          expr(defaultIdomRenderer, data, ij, variant);
        });
        incrementaldom.text(val);
        break;
      default:
        throw new Error('Bad content kind');
    }
  } else {
    const val = expr(data, ij, variant);
    incrementaldom.text(String(val));
  }
}

function callDynamicCss<TParams>(
  incrementaldom: IncrementalDomRenderer,
  expr: Template<TParams>,
  data: TParams,
  ij: IjData
) {
  const val = callDynamicText<TParams>(expr, data, ij, soy.$$filterCssValue);
  incrementaldom.text(String(val));
}

function callDynamicJs<TParams>(
  incrementaldom: IncrementalDomRenderer,
  expr: Template<TParams>,
  data: TParams,
  ij: IjData
) {
  const val = callDynamicText<TParams>(expr, data, ij, soy.$$escapeJsValue);
  return String(val);
}

/**
 * Calls an expression and coerces it to a string for cases where an IDOM
 * function needs to be concatted to a string.
 */
function callDynamicText<TParams>(
  expr: Template<TParams>,
  data: TParams,
  ij: IjData,
  escFn?: (i: string) => string,
  variant?: string
) {
  const transformFn = escFn ? escFn : (a: string) => a;
  if (isIdom(expr)) {
    switch ((expr as IdomFunction).contentKind) {
      case SanitizedContentKind.HTML:
        return transformFn(
          htmlToString(() => {
            expr(defaultIdomRenderer, data, ij, variant);
          })
        );
      case SanitizedContentKind.ATTRIBUTES:
        return transformFn(
          attributesToString(() => {
            expr(defaultIdomRenderer, data, ij, variant);
          })
        );
      default:
        throw new Error('Bad content kind');
    }
  } else {
    return expr(data, ij, variant);
  }
}

declare global {
  interface Node {
    __originalContent: unknown;
    __clonePointer: NodeIterator | null;
  }
}

function getOriginalSanitizedContent(el: Element) {
  return el.__originalContent;
}

/**
 * Prints an expression depending on its type.
 */
function print(
  incrementaldom: IncrementalDomRenderer,
  expr: unknown,
  isSanitizedContent?: boolean | undefined
) {
  if (USE_TEMPLATE_CLONING) {
    incrementaldom.openNodePart();
  }
  if (
    expr instanceof SanitizedHtml ||
    isSanitizedContent ||
    expr instanceof SafeHtml
  ) {
    const content =
      expr instanceof SafeHtml ? SafeHtml.unwrap(expr) : String(expr);
    if (!/&|</.test(content)) {
      incrementaldom.text(content);
      return;
    }
    // For HTML content we need to insert a custom element where we can
    // place the content without incremental dom modifying it.
    const el = document.createElement('html-blob');
    googSoy.renderHtml(el, ordainSanitizedHtml(content));
    const childNodes = Array.from(el.childNodes);
    for (const child of childNodes) {
      const currentPointer = incrementaldom.currentPointer();
      const currentElement = incrementaldom.currentElement();
      child.__originalContent = expr;
      if (currentElement) {
        if (!currentPointer) {
          currentElement.appendChild(child);
        } else if (currentPointer.__originalContent !== expr) {
          currentElement.insertBefore(child, currentPointer);
        }
      }
      incrementaldom.skipNode();
    }
  } else if (expr !== undefined) {
    renderDynamicContent(incrementaldom, expr as IdomFunction);
  }
  if (USE_TEMPLATE_CLONING) {
    incrementaldom.closeNodePart();
  }
}

function visitHtmlCommentNode(
  incrementaldom: IncrementalDomRenderer,
  val: string
) {
  const currNode = incrementaldom.currentElement();
  if (!currNode) {
    return;
  }
  if (
    currNode.nextSibling != null &&
    currNode.nextSibling.nodeType === Node.COMMENT_NODE
  ) {
    currNode.nextSibling.textContent = val;
    // This is the case where we are creating new DOM from an empty element.
  } else {
    currNode.appendChild(document.createComment(val));
  }
  incrementaldom.skipNode();
}

function isTruthy(expr: unknown): boolean {
  if (!expr) return false;

  // idom callbacks.
  if ((expr as IdomFunction).isInvokableFn) {
    const renderer = new FalsinessRenderer();
    (expr as IdomFunction).invoke(
      renderer as unknown as IncrementalDomRenderer
    );
    return renderer.didRender();
  }

  if (expr instanceof SanitizedContent) return !!expr.getContent();

  // true, numbers, strings.
  if (typeof expr !== 'object') return !!String(expr);

  // Objects, arrays.
  return true;
}

let uidCounter = 0;

/**
 * Holds an ID, and gives a useful error if you read it before it was set.
 *
 * This class is only used in DEBUG builds; in production, a simple object (that
 * does no validation) is used instead.
 */
class IdHolderForDebug implements IdHolder {
  backing?: string;
  get id(): string | undefined {
    if (!this.backing) {
      throw new Error(
        `
Cannot read 'idHolder.id' until the element with the 'uniqueAttribute()' call is
patched.  If you're trying to print {$idHolder.id} first, swap the usage around,
so that the first element calls uniqueAttribute(), and the second element prints
{$idHolder.id}.`.trim()
      );
    }
    return this.backing;
  }
  set id(value: string | undefined) {
    if (this.backing && this.backing !== value) {
      throw new Error('Cannot render the same idHolder instance twice.');
    }
    this.backing = value;
  }
}
interface IdHolder {
  id?: string;
}

function passToIdHolder(value: string, idHolder?: IdHolder) {
  if (idHolder) idHolder.id = value;
  return value;
}

function compileToTemplate(content: SanitizedContent): HTMLTemplateElement {
  const el = document.createElement('template');
  googSoy.renderHtml(el, content);
  return el;
}

function appendCloneToCurrent(
  content: HTMLTemplateElement,
  renderer: IncrementalDomRenderer
) {
  const currentElement = renderer.currentElement();
  if (!currentElement) {
    return;
  }
  let currentPointer = renderer.currentPointer();
  // We are at the inside of a node part created by a message. We should insert
  // before the end of the node part.
  if (
    currentPointer === null &&
    currentElement.nodeType === Node.COMMENT_NODE &&
    (currentElement as unknown as Comment).data === '?/child-node-part?'
  ) {
    currentPointer = currentElement;
  }
  const clone = content.cloneNode(true) as HTMLTemplateElement;
  if (currentPointer?.nodeType === Node.COMMENT_NODE) {
    if (clone.content) {
      currentPointer.parentNode?.insertBefore(clone.content, currentPointer);
    } else {
      const childNodes = Array.from(clone.childNodes);
      for (const child of childNodes) {
        currentPointer.parentNode?.insertBefore(child, currentPointer);
      }
    }
  } else {
    if (clone.content) {
      currentElement.appendChild(clone.content);
    } else {
      const childNodes = Array.from(clone.childNodes);
      for (const child of childNodes) {
        currentElement.appendChild(child);
      }
    }
  }
}

/**
 * Returns an idom- and classic- Soy compatible attribute with a unique value.
 *
 * When called from idom, it will preserve any already-rendered value.
 *
 * This is exposed via the `uniqueAttribute()` Soy extern function.
 */
function stableUniqueAttribute(
  attributeName: string,
  idHolder?: IdHolder
): IdomFunction & SanitizedHtmlAttribute {
  attributeName = soy.$$filterHtmlAttributes(attributeName);

  // Note that the prefix must be different from other unique-value functions.
  return makeAttributes(
    // idom callback:
    (idomRenderer: IncrementalDomRenderer) => {
      // If we aren't rendering into actual elements, don't affect any state.
      // This prevents {if} truthiness checks for affecting idHolder.
      if (
        idomRenderer instanceof NullRenderer ||
        idomRenderer instanceof FalsinessRenderer
      ) {
        // This should never actually render anywhere.
        idomRenderer.attr(attributeName, 'zSoyz: no id');
        return;
      }

      // If the current element already has an ID, reuse it.
      const existingId = incrementaldom
        .tryGetCurrentElement()
        ?.getAttribute(attributeName);
      idomRenderer.attr(
        attributeName,
        passToIdHolder(existingId ?? `ucc-${uidCounter++}`, idHolder)
      );
    },
    // Callback to generate a string for classic Soy:
    () => {
      return `${attributeName}="${soy.$$escapeHtmlAttribute(
        passToIdHolder(`ucc-${uidCounter++}`, idHolder)
      )}"`;
    }
  );
}

/**
 * Creates an IdHolder object to pass to `uniqueAttribute()`.
 *
 * This is exposed via the `idHolder()` Soy extern function.
 *
 * Note: This returns a mutable object; this implementation is a bit hacky.
 */
function stableUniqueAttributeIdHolder(): IdHolder {
  return goog.DEBUG ? new IdHolderForDebug() : {};
}

export {
  SoyTemplate as $SoyTemplate,
  SoyElement as $SoyElement,
  isIdom as $$isIdom,
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
  stableUniqueAttribute as $$stableUniqueAttribute,
  stableUniqueAttributeIdHolder as $$stableUniqueAttributeIdHolder,
  visitHtmlCommentNode as $$visitHtmlCommentNode,
  upgrade as $$upgrade,
  getOriginalSanitizedContent,
  defaultIdomRenderer as $$defaultIdomRenderer,
  compileToTemplate as $$compileToTemplate,
  appendCloneToCurrent as $$appendCloneToCurrent,
  NODE_PART
};

export {USE_TEMPLATE_CLONING} from './global';
