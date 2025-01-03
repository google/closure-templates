/**
 * @fileoverview
 *
 * Functions necessary to interact with the Soy-Idom runtime.
 */

import {ordainSanitizedHtml} from 'goog:soydata.VERY_UNSAFE'; // from //javascript/template/soy:soy_usegoog_js
import * as soy from 'google3/javascript/template/soy/soyutils_usegoog';
import {
  $$VisualElementData,
  ElementMetadata,
  Logger,
  LoggingAttrs,
} from 'google3/javascript/template/soy/soyutils_velog';
import * as log from 'google3/third_party/javascript/closure/log/log';
import {SanitizedHtml} from 'google3/third_party/javascript/closure/soy/data';
import * as googSoy from 'google3/third_party/javascript/closure/soy/soy';
import {truncate} from 'google3/third_party/javascript/closure/string/string';
import * as incrementaldom from 'incrementaldom'; // from //third_party/javascript/incremental_dom:incrementaldom
import {SafeHtml, unwrapHtml, unwrapResourceUrl} from 'safevalues';
import {resourceUrlSafeByReview} from 'safevalues/restricted/reviewed';
import {attributes} from './api_idom_attributes';
import {IdomFunction, SoyElement} from './element_lib_idom';
import {getSoyUntyped} from './global';
import {TemplateAcceptor} from './soyutils_idom';
import {IjData, IdomTemplate as Template} from './templates';

export {attributes} from './api_idom_attributes';
export {type IdomTemplate as Template} from './templates';

const logger = log.getLogger('api_idom');

declare global {
  interface Node {
    __lastParams: string | undefined;
    __lastParamsPretty: string | undefined;
    __hasBeenRendered?: boolean;
  }
}

const patchConfig: incrementaldom.PatchConfig = {
  matches: (
    matchNode,
    nameOrCtor,
    expectedNameOrCtor,
    proposedKey,
    currentPointerKey,
  ) =>
    nameOrCtor === expectedNameOrCtor &&
    isMatchingKey(proposedKey, currentPointerKey),
};

/**
 * Wraps an idom `createPatch*<T>()` method to return a generic function instead
 * of taking a type parameter and returning a function fixed to that type.
 *
 * This lets our exported `patch()` methods be called type-safely with any type.
 *
 * In short, this function moves `<T>` from the `createPatchInner()` call to the
 * actual (returned) `patchInner()` function.
 *
 * @return A `PatchFunction` that has its own type parameter, instead of coupled
 *     to a specific `<T>`
 */
function wrapAsGeneric<R>(
  fnCreator: <T>(
    patchConfig: incrementaldom.PatchConfig,
  ) => incrementaldom.PatchFunction<T, R>,
  patchConfig: incrementaldom.PatchConfig,
): <T>(
  node: Element | DocumentFragment,
  template: (a: T | undefined) => void,
  data?: T | undefined,
) => R {
  return fnCreator(patchConfig);
}

/** PatchInner using Soy-IDOM semantics. */
export const patchInner = wrapAsGeneric(
  incrementaldom.createPatchInner,
  patchConfig,
);
/** PatchOuter using Soy-IDOM semantics. */
export const patchOuter = wrapAsGeneric(
  incrementaldom.createPatchOuter,
  patchConfig,
);
/** PatchInner using Soy-IDOM semantics. */
export const patch = patchInner;

/** Interface for idom renderers. */
export interface IncrementalDomRenderer {
  open(nameOrCtor: string, key?: string): void;
  openSimple(nameOrCtor: string, key?: string): void;
  keepGoing(
    data: unknown,
    continueFn: (renderer: IncrementalDomRenderer) => void,
  ): void;
  visit(el: void | HTMLElement): void;
  pushManualKey(key: incrementaldom.Key): void;
  popManualKey(): void;
  pushKey(key: string): void;
  popKey(): void;
  elementClose(): void;
  close(): void;
  text(value: string): void;
  print(expr: unknown, isSanitizedContent?: boolean | undefined): void;
  visitHtmlCommentNode(val: string): void;
  appendCloneToCurrent(content: HTMLTemplateElement): void;
  attr(name: string, value: string): void;
  currentPointer(): Node | null;
  skip(): void;
  currentElement(): void | Element;
  skipNode(): void;
  applyAttrs(): void;
  applyStatics(statics: incrementaldom.Statics): void;
  enterVeLog(
    veData: $$VisualElementData,
    logOnly?: boolean,
  ): IncrementalDomRenderer;
  exitVeLog(): IncrementalDomRenderer;
  setLogger(logger: Logger | undefined): void;
  getLogger(): Logger | undefined;
  evalLoggingFunction(
    name: string,
    args: Array<{}>,
    placeHolder: string,
  ): string;
  loggingFunctionAttr(
    attrName: string,
    loggingFuncName: string,
    args: Array<{}>,
    placeHolder: string,
  ): void;
  handleSoyElement<T extends TemplateAcceptor<{}>>(
    elementClassCtor: new () => T,
    firstElementKey: string,
    tagName: string,
    data: {},
    ijData: IjData,
    template: Template<unknown>,
  ): void;
}

/**
 * Class that mostly delegates to global Incremental DOM runtime. This will
 * eventually take in a logger and conditionally mute. These methods may
 * return void when idom commands are muted for velogging.
 */
export class IncrementalDomRendererImpl implements IncrementalDomRenderer {
  // Stack (holder) of key stacks for the current template being rendered, which
  // has context on where the template was called from and is used to
  // key each template call (see go/soy-idom-diffing-semantics).
  // Works as follows:
  // - A new key is pushed onto the topmost key stack before a template call,
  // - and popped after the call.
  // - A new stack is pushed onto the holder before a manually keyed element
  //   is opened, and popped before the element is closed. This is because
  //   manual keys "reset" the key context.
  // Note that for performance, the "stack" is implemented as a string with
  // the items being `${SIZE OF KEY}${DELIMITER}${KEY}`.
  private readonly keyStackHolder: string[] = [];
  private logger: Logger | undefined;
  private pendingAttrs: LoggingAttrs | undefined;
  private currentElementIsScript = false;

  /**
   * Pushes/pops the given key from `keyStack` (versus `Array#concat`)
   * to avoid allocating a new array for every element open.
   */
  open(nameOrCtor: string, key?: string): void {
    this.openInternal(nameOrCtor, key);
  }

  private openInternal(
    nameOrCtor: string,
    key: string | undefined,
  ): HTMLElement | void {
    const el = incrementaldom.open(nameOrCtor, this.getNewKey(key));
    this.visit(el);
    this.currentElementIsScript = nameOrCtor === 'script';
    return el;
  }

  openSimple(nameOrCtor: string, key?: string): void {
    const el = incrementaldom.open(nameOrCtor, key);
    this.visit(el);
    this.currentElementIsScript = nameOrCtor === 'script';
  }

  keepGoing(
    data: unknown,
    continueFn: (renderer: IncrementalDomRenderer) => void,
  ) {
    const el = this.currentElement() as HTMLElement;
    // `data` is only passed by {skip} elements that are roots of templates.
    if (!COMPILED && goog.DEBUG && el && data) {
      maybeReportErrors(el, data);
    }

    // We want to skip if this element has already been rendered. In The
    // client-side rendering use case, this is straight forward because
    // we can tag the element. in SSR, we do best effort guessing using
    // child nodes.
    if (!el || el.__hasBeenRendered || el.hasChildNodes()) {
      this.skip();
      // And exit its node so that we will continue with the next node.
      this.close();
      return;
    }
    el.__hasBeenRendered = true;

    // Only set the marker attribute when actually populating the element.
    if (goog.DEBUG && el) {
      el.setAttribute('soy-skip-key-debug', String(incrementaldom.getKey(el)));
    }

    // If we have not yet populated this element, tell the template to do so.
    continueFn(this);
  }

  // For users extending IncrementalDomRenderer
  visit(el: HTMLElement | void) {}

  /**
   * Called (from generated template render function) before OPENING
   * keyed elements.
   */
  pushManualKey(key: incrementaldom.Key) {
    this.keyStackHolder.push(soy.$$serializeKey(key));
  }

  /**
   * Called (from generated template render function) before CLOSING
   * keyed elements.
   */
  popManualKey() {
    this.keyStackHolder.pop();
  }

  /**
   * Called (from generated template render function) BEFORE template
   * calls.
   */
  pushKey(key: string) {
    this.keyStackHolder[this.keyStackHolder.length - 1] = this.getNewKey(key);
  }

  private getNewKey(key: string | undefined) {
    const oldKey = this.getCurrentKeyStack();
    // This happens in the case where an element has a manual key. The very next
    // key should be undefined.
    if (key === undefined) {
      return oldKey;
    }
    const serializedKey = soy.$$serializeKey(key);
    return serializedKey + oldKey;
  }

  /**
   * Called (from generated template render function) AFTER template
   * calls.
   */
  popKey() {
    const currentKeyStack = this.getCurrentKeyStack();
    if (!currentKeyStack) {
      // This can happen when templates are not fully idom compatible, eg
      log.warning(logger, 'Key stack overrun!');
      return;
    }
    const topKeySizeString = currentKeyStack.match(/[0-9]+/)![0];
    const topKeySizeNumber = Number(topKeySizeString);
    this.keyStackHolder[this.keyStackHolder.length - 1] =
      currentKeyStack.substring(topKeySizeString.length + 1 + topKeySizeNumber);
  }

  /**
   * Returns the stack on top of the holder. This represents the current
   * chain of keys.
   */
  private getCurrentKeyStack(): string {
    return this.keyStackHolder[this.keyStackHolder.length - 1] || '';
  }

  protected closeInternal(): Element | void {
    this.currentElementIsScript = false;
    return incrementaldom.close();
  }

  close() {
    this.closeInternal();
  }

  elementClose() {
    const el = this.closeInternal();
    if (el && el.__soy_patch_handler) {
      el.__soy_patch_handler();
    }
  }

  text(value: string) {
    // This helps ensure that hydrations on the server are consistent with
    // client-side renders.
    if (value) {
      incrementaldom.text(value);
    }
  }

  /**
   * Prints an expression depending on its type.
   */
  print(expr: unknown, isSanitizedContent?: boolean | undefined) {
    if (
      expr instanceof SanitizedHtml ||
      isSanitizedContent ||
      expr instanceof SafeHtml
    ) {
      const content =
        expr instanceof SafeHtml ? unwrapHtml(expr).toString() : String(expr);
      if (!/&|</.test(content)) {
        this.text(content);
        return;
      }
      // For HTML content we need to insert a custom element where we can
      // place the content without incremental dom modifying it.
      const el = document.createElement('html-blob');
      googSoy.renderHtml(el, ordainSanitizedHtml(content));
      const childNodes = Array.from(el.childNodes);
      for (const child of childNodes) {
        const currentPointer = this.currentPointer();
        const currentElement = this.currentElement();
        child.__originalContent = expr;
        if (currentElement) {
          if (!currentPointer) {
            currentElement.appendChild(child);
          } else if (currentPointer.__originalContent !== expr) {
            currentElement.insertBefore(child, currentPointer);
          }
        }
        this.skipNode();
      }
    } else if (expr !== undefined) {
      this.renderDynamicContent(expr as IdomFunction);
    }
  }

  /**
   * Calls an expression in case of a function or outputs it as text content.
   */
  private renderDynamicContent(expr: IdomFunction) {
    // TODO(lukes): check content kind == html
    if (expr && expr.isInvokableFn) {
      // The Soy compiler will validate the content kind of the parameter.
      expr.invoke(this);
    } else {
      this.text(String(expr));
    }
  }

  visitHtmlCommentNode(val: string) {
    const currNode = this.currentElement();
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
    this.skipNode();
  }

  appendCloneToCurrent(content: HTMLTemplateElement) {
    const currentElement = this.currentElement();
    if (!currentElement) {
      return;
    }
    let currentPointer = this.currentPointer();
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

  attr(name: string, value: string) {
    // We're assigning to `script#src` and in order to be compatible with
    // go/trusted-types we have to wrap the value in a TrustedScriptUrl. This
    // is safe from a security POV since this value has already been sanitized
    // by soy. See b/217214585.
    if (this.currentElementIsScript && name === 'src') {
      incrementaldom.attr(
        name,
        unwrapResourceUrl(
          resourceUrlSafeByReview(value, {
            justification: 'Value has been pre-sanitized by Soy.',
          }),
        ),
      );
    } else {
      incrementaldom.attr(name, value);
    }
  }

  currentPointer(): Node | null {
    return incrementaldom.currentPointer();
  }

  skip() {
    incrementaldom.skip();
  }

  currentElement(): Element | void {
    return incrementaldom.currentElement();
  }

  skipNode() {
    incrementaldom.skipNode();
  }

  applyAttrs() {
    incrementaldom.applyAttrs(attributes);
    const pendingAttrs = this.pendingAttrs;
    if (pendingAttrs) {
      const el = this.currentElement()!;
      // If we have already been rendered, then we can overwrite the attrs.
      pendingAttrs.applyToInternalOnly(el as HTMLElement, el.__hasBeenRendered);
      el.__hasBeenRendered = true;
      this.pendingAttrs = undefined;
    }
  }

  applyStatics(statics: incrementaldom.Statics) {
    incrementaldom.applyStatics(statics, attributes);
  }

  /**
   * Called when a `{velog}` statement is entered.
   */
  enterVeLog(
    veData: $$VisualElementData,
    logOnly?: boolean,
  ): IncrementalDomRenderer {
    const logger = this.logger;
    if (logger) {
      this.pendingAttrs = logger.enter(
        new ElementMetadata(
          veData.getVe().getId(),
          veData.getData(),
          !!logOnly,
        ),
      );
    } else if (logOnly) {
      throw new Error(
        'Cannot set logonly="true" unless there is a logger configured',
      );
    }
    return logOnly ? new NullRenderer(this) : this;
  }

  /**
   * Called when a `{velog}` statement is exited.
   */
  exitVeLog(): IncrementalDomRenderer {
    this.logger?.exit();
    // If we somehow fail to apply to an element just clear them out.  This
    // would naturally happen for a logonly block, but could also happen if
    // the developer simply failed to nest an element.
    this.pendingAttrs = undefined;
    return this;
  }

  /** Called by user code to configure logging */
  setLogger(logger: Logger | undefined) {
    this.logger = logger;
  }

  getLogger() {
    return this.logger;
  }

  /*
   * Called when a logging function is evaluated.
   */
  evalLoggingFunction(
    name: string,
    args: Array<{}>,
    placeHolder: string,
  ): string {
    if (this.logger) {
      return this.logger.evalLoggingFunction(name, args);
    }
    return placeHolder;
  }

  /**
   * Sets the attribute named "attrName" on the current element to the result
   * of evaluating the specified logging function.
   */
  loggingFunctionAttr(
    attrName: string,
    loggingFuncName: string,
    args: Array<{}>,
    placeHolder: string,
  ) {
    const attrValue = this.logger
      ? this.logger.evalLoggingFunction(loggingFuncName, args)
      : placeHolder;
    this.attr(attrName, attrValue);
  }

  /**
   * Tries to find an existing Soy element, if it exists. Otherwise, it creates
   * one. Afterwards, it queues up a Soy element (see docs for queueSoyElement)
   * and then proceeds to render the Soy element.
   */
  handleSoyElement<T extends TemplateAcceptor<{}>>(
    elementClassCtor: new () => T,
    firstElementKey: string,
    tagName: string,
    data: {},
    ijData: IjData,
    template: Template<unknown>,
  ): void {
    const soyElementKey = firstElementKey + this.getCurrentKeyStack();
    let soyElement: SoyElement<{}, {}> =
      new elementClassCtor() as unknown as SoyElement<{}, {}>;
    soyElement.data = data;
    soyElement.ijData = ijData;
    soyElement.key = soyElementKey;
    soyElement.template = template.bind(soyElement);
    // NOTE(b/166257386): Without this, SoyElement re-renders don't have logging
    soyElement.setLogger(this.getLogger());
    const isTemplateCloning =
      ijData && (ijData as {[key: string]: unknown})['inTemplateCloning'];
    /**
     * Open the element early in order to execute lifecycle hooks. Suppress the
     * next element open since we've already opened it.
     */
    let element = isTemplateCloning
      ? null
      : this.openInternal(tagName, firstElementKey);

    const oldOpen = this.openInternal;
    this.openInternal = (tagName, soyElementKey) => {
      if (element) {
        if (soyElementKey !== firstElementKey) {
          throw new Error('Expected tag name and key to match.');
        }
      } else {
        element = oldOpen.call(this, tagName, soyElementKey);
        soyElement.node = element!;
        element!.__soy = soyElement;
      }
      this.openInternal = oldOpen;
      return element!;
    };

    if (isTemplateCloning) {
      soyElement.syncStateFromData(data);
      soyElement.renderInternal(this, data);
      return;
    }

    if (!element) {
      // Template still needs to execute in order to trigger logging.
      template.call(soyElement, this, data, ijData);
      return;
    }
    const untypedElement = getSoyUntyped(element);
    if (untypedElement instanceof elementClassCtor) {
      soyElement = untypedElement;
    }
    const maybeSkip = soyElement.handleSoyElementRuntime(element, data);
    soyElement.template = template.bind(soyElement);
    if (maybeSkip) {
      this.skip();
      this.close();
      this.openInternal = oldOpen;
      return;
    }
    soyElement.renderInternal(this, data);
  }
}

/**
 * Renderer that mutes all IDOM commands and returns void.
 */
export class NullRenderer extends IncrementalDomRendererImpl {
  private depth = 1;
  constructor(private readonly renderer: IncrementalDomRenderer) {
    super();
    this.setLogger(renderer.getLogger());
  }

  override open(nameOrCtor: string, key?: string) {
    return undefined;
  }

  override openSimple(nameOrCtor: string, key?: string) {
    return undefined;
  }

  override keepGoing(
    data: unknown,
    continueFn: (renderer: IncrementalDomRenderer) => void,
  ) {}

  override close() {}
  override elementClose() {}

  override text(value: string) {}

  override attr(name: string, value: string) {}

  override currentPointer() {
    return null;
  }

  override applyAttrs() {}

  override applyStatics(statics: incrementaldom.Statics) {}

  override skip() {}

  key(val: string) {}

  override currentElement() {}

  override skipNode() {}

  override enterVeLog(
    data: $$VisualElementData,
    logOnly?: boolean,
  ): IncrementalDomRenderer {
    super.enterVeLog(data, logOnly);
    this.depth++;
    return this;
  }

  /** Returns to the default renderer which will traverse the DOM. */
  override exitVeLog(): IncrementalDomRenderer {
    super.exitVeLog();
    this.depth--;
    if (this.depth === 0) {
      return this.renderer;
    }
    return this;
  }
}

/**
 * Returns whether the proposed key is a prefix of the current key or vice
 * versa.
 * For example:
 * - proposedKey: ['b', 'c'], currentPointerKey: ['a', 'b', 'c'] => true
 *     proposedKey -> 1c1b, currentPointerKey -> 1c1b1a
 * - proposedKey: ['a', 'b', 'c'], currentPointerKey: ['b', 'c'],  => true
 *     proposedKey -> 1c1b1a, currentPointerKey -> 1c1b
 * - proposedKey: ['b', 'c'], currentPointerKey: ['a', 'b', 'c', 'd'] => false
 *     proposedKey -> 1c1b, currentPointerKey -> 1d1c1b1a
 */
export function isMatchingKey(
  proposedKey: unknown,
  currentPointerKey: unknown,
) {
  // Using "==" instead of "===" is intentional. SSR serializes attributes
  // differently than the type that keys are. For example "0" == 0.
  // tslint:disable-next-line:triple-equals
  if (proposedKey == currentPointerKey) {
    return true;
  }
  // This is always true in Soy-IDOM, but Incremental DOM believes that it may
  // be null or number.
  if (
    typeof proposedKey === 'string' &&
    typeof currentPointerKey === 'string'
  ) {
    return (
      proposedKey.startsWith(currentPointerKey) ||
      currentPointerKey.startsWith(proposedKey)
    );
  }
  return false;
}

function debugReplacerIgnoringSerializationChanges(
  k: unknown,
  v: unknown,
): unknown {
  if (v == null) return undefined;
  // Allow booleans represented as numbers.
  if (typeof v === 'boolean') v = +v;
  // Allow numbers/bigints represented as strings.
  if (typeof v === 'number' || typeof v === 'bigint') v = `${v}`;
  return v;
}

function maybeReportErrors(el: HTMLElement, data: unknown) {
  const stringifiedParams = JSON.stringify(
    data,
    debugReplacerIgnoringSerializationChanges,
  );
  const stringifiedParamsPretty = JSON.stringify(data, undefined, 2);
  if (!el.__lastParams) {
    el.__lastParams = stringifiedParams;
    el.__lastParamsPretty = stringifiedParamsPretty;
    return;
  }
  if (stringifiedParams !== el.__lastParams) {
    throw new Error(`
Tried to rerender a {skip} template with different parameters!
Make sure that you never pass a parameter that can change to a template that has
{skip}, since changes to that parameter won't affect the skipped content.

Old parameters: ${el.__lastParamsPretty}
New parameters: ${stringifiedParamsPretty}

Element:
${el.dataset['debugSoy'] || truncate(el.outerHTML, 256)}`);
  }
}

/**
 * A Renderer that keeps track of whether it was ever called to render anything,
 * but never actually does anything  This is used to check whether an HTML value
 * is empty (if it's used in an `{if}` or conditional operator).
 */
export class FalsinessRenderer extends IncrementalDomRendererImpl {
  override visit(el: void | HTMLElement): void {}
  override pushManualKey(key: incrementaldom.Key) {}
  override popManualKey(): void {}
  override pushKey(key: string): void {}
  override popKey(): void {}
  override enterVeLog(): IncrementalDomRenderer {
    return this;
  }
  override exitVeLog(): IncrementalDomRenderer {
    return this;
  }
  override setLogger(logger: Logger | undefined): void {}
  override getLogger(): Logger | undefined {
    return undefined;
  }

  override evalLoggingFunction(
    name: string,
    args: Array<{}>,
    placeHolder: string,
  ): string {
    return placeHolder;
  }

  private rendered = false;

  override loggingFunctionAttr(
    attrName: string,
    loggingFuncName: string,
    args: Array<{}>,
    placeHolder: string,
  ) {
    this.rendered = true;
  }

  /** Checks whether any DOM was rendered. */
  didRender() {
    return this.rendered;
  }

  override open(nameOrCtor: string, key?: string) {
    this.rendered = true;
  }

  override openSimple(nameOrCtor: string, key?: string) {}

  override keepGoing(
    data: unknown,
    continueFn: (renderer: IncrementalDomRenderer) => void,
  ) {}

  override close() {
    this.rendered = true;
  }

  override elementClose() {
    this.rendered = true;
  }

  override text(value: string) {
    // This helps ensure that hydrations on the server are consistent with
    // client-side renders.
    if (value) {
      this.rendered = true;
    }
  }

  override attr(name: string, value: string) {
    this.rendered = true;
  }

  override currentPointer() {
    return null;
  }

  override applyAttrs() {
    this.rendered = true;
  }

  override applyStatics(statics: incrementaldom.Statics) {
    this.rendered = true;
  }

  override skip() {
    this.rendered = true;
  }

  key(val: string) {}

  override currentElement() {}

  override skipNode() {
    this.rendered = true;
  }

  override handleSoyElement<T extends TemplateAcceptor<{}>>(
    elementClassCtor: new () => T,
    firstElementKey: string,
    tagName: string,
    data: {},
    ijData: IjData,
    template: Template<unknown>,
  ) {
    // If we're just testing truthiness, record an element but don't do anythng.
    this.open('div');
    this.close();
  }
}

const noArgCallConsts = {
  popManualKey: (actual: IncrementalDomRenderer) => {
    actual.popManualKey();
  },
  popKey: (actual: IncrementalDomRenderer) => {
    actual.popKey();
  },
  exitVeLog: (actual: IncrementalDomRenderer) => {
    actual.exitVeLog();
  },
  close: (actual: IncrementalDomRenderer) => {
    actual.close();
  },
  elementClose: (actual: IncrementalDomRenderer) => {
    actual.elementClose();
  },
  applyAttrs: (actual: IncrementalDomRenderer) => {
    actual.applyAttrs();
  },
  skip: (actual: IncrementalDomRenderer) => {
    actual.skip();
  },
  skipNode: (actual: IncrementalDomRenderer) => {
    actual.skipNode();
  },
};

/**
 * A renderer that stores all calls and can be replayed onto another renderer.
 */
export class BufferingIncrementalDomRenderer implements IncrementalDomRenderer {
  private readonly buffer: Array<(actual: IncrementalDomRenderer) => void> = [];

  visit(el: void | HTMLElement): void {
    this.buffer.push((actual) => {
      actual.visit(el);
    });
  }
  pushManualKey(key: incrementaldom.Key) {
    this.buffer.push((actual) => {
      actual.pushManualKey(key);
    });
  }
  popManualKey(): void {
    this.buffer.push(noArgCallConsts.popManualKey);
  }
  pushKey(key: string): void {
    this.buffer.push((actual) => {
      actual.pushKey(key);
    });
  }
  popKey(): void {
    this.buffer.push(noArgCallConsts.popKey);
  }
  enterVeLog(
    veData: $$VisualElementData,
    logOnly?: boolean,
  ): IncrementalDomRenderer {
    this.buffer.push((actual) => {
      actual.enterVeLog(veData, logOnly);
    });
    return logOnly ? new NullRenderer(this) : this;
  }
  exitVeLog(): IncrementalDomRenderer {
    this.buffer.push(noArgCallConsts.exitVeLog);
    return this;
  }

  setLogger(logger: Logger | undefined): void {
    throw new Error(
      'Tried to call setLogger on BufferingIncrementalDomRenderer.',
    );
  }
  getLogger(): Logger | undefined {
    throw new Error(
      'Tried to call getLogger on BufferingIncrementalDomRenderer.',
    );
  }
  evalLoggingFunction(
    name: string,
    args: Array<{}>,
    placeHolder: string,
  ): string {
    throw new Error(
      'Tried to call evalLoggingFunction on BufferingIncrementalDomRenderer. ' +
        'errorfallback is incompatible with passing attributes with ' +
        'element composition.',
    );
  }
  loggingFunctionAttr(
    attrName: string,
    name: string,
    args: Array<{}>,
    placeHolder: string,
  ) {
    this.buffer.push((actual) => {
      actual.loggingFunctionAttr(attrName, name, args, placeHolder);
    });
  }
  open(nameOrCtor: string, key?: string) {
    this.buffer.push((actual) => {
      actual.open(nameOrCtor, key);
    });
  }

  openSimple(nameOrCtor: string, key?: string) {
    this.buffer.push((actual) => {
      actual.openSimple(nameOrCtor, key);
    });
  }

  keepGoing(
    data: unknown,
    continueFn: (renderer: IncrementalDomRenderer) => void,
  ) {
    const innerBuffer = new BufferingIncrementalDomRenderer();
    continueFn(innerBuffer);
    this.buffer.push((actual) => {
      actual.keepGoing(data, (innerRenderer: IncrementalDomRenderer) => {
        innerBuffer.replayOn(innerRenderer);
      });
    });
  }

  close() {
    this.buffer.push(noArgCallConsts.close);
  }

  elementClose() {
    this.buffer.push(noArgCallConsts.elementClose);
  }

  text(value: string) {
    this.buffer.push((actual) => {
      actual.text(value);
    });
  }

  print(expr: unknown, isSanitizedContent?: boolean | undefined) {
    this.buffer.push((actual) => {
      actual.print(expr, isSanitizedContent);
    });
  }

  visitHtmlCommentNode(val: string) {
    this.buffer.push((actual) => {
      actual.visitHtmlCommentNode(val);
    });
  }

  appendCloneToCurrent(content: HTMLTemplateElement) {
    this.buffer.push((actual) => {
      actual.appendCloneToCurrent(content);
    });
  }

  attr(name: string, value: string) {
    this.buffer.push((actual) => {
      actual.attr(name, value);
    });
  }

  currentPointer(): never {
    throw new Error(
      'Tried to call currentPointer() on BufferingIncrementalDomRenderer',
    );
  }

  applyAttrs() {
    this.buffer.push(noArgCallConsts.applyAttrs);
  }

  applyStatics(statics: incrementaldom.Statics) {
    this.buffer.push((actual) => {
      actual.applyStatics(statics);
    });
  }

  skip() {
    this.buffer.push(noArgCallConsts.skip);
  }

  currentElement() {
    throw new Error(
      'Tried to call currentElement() on BufferingIncrementalDomRenderer',
    );
  }

  skipNode() {
    this.buffer.push(noArgCallConsts.skipNode);
  }

  handleSoyElement<T extends TemplateAcceptor<{}>>(
    elementClassCtor: new () => T,
    firstElementKey: string,
    tagName: string,
    data: {},
    ijData: IjData,
    template: Template<unknown>,
  ) {
    const innerBuffer = new BufferingIncrementalDomRenderer();
    const soyElement = new elementClassCtor() as unknown as SoyElement<{}, {}>;
    soyElement.ijData = ijData;
    template.call(soyElement, innerBuffer, data);
    this.buffer.push((actual) => {
      actual.handleSoyElement(
        elementClassCtor,
        firstElementKey,
        tagName,
        data,
        ijData,
        (innerRenderer: IncrementalDomRenderer) => {
          innerBuffer.replayOn(innerRenderer);
        },
      );
    });
  }

  replayOn(actual: IncrementalDomRenderer) {
    this.buffer.forEach((fn) => {
      fn(actual);
    });
  }
}
