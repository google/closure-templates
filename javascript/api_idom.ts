/**
 * @fileoverview
 *
 * Functions necessary to interact with the Soy-Idom runtime.
 */

import * as googSoy from 'goog:goog.soy';  // from //javascript/closure/soy
import Message from 'goog:jspb.Message'; // from //javascript/apps/jspb:message_lib
import * as soy from 'goog:soy';  // from //javascript/template/soy:soy_usegoog_js
import {$$VisualElementData, ElementMetadata, Logger} from 'goog:soy.velog';  // from //javascript/template/soy:soyutils_velog
import * as incrementaldom from 'incrementaldom';  // from //third_party/javascript/incremental_dom:incrementaldom

declare global {
  interface Node {
    __lastParams: string|undefined;
  }
}

const patchConfig: incrementaldom.PatchConfig = {
  matches:
      (matchNode, nameOrCtor, expectedNameOrCtor, proposedKey,
       currentPointerKey) => nameOrCtor === expectedNameOrCtor &&
      isMatchingKey(proposedKey, currentPointerKey)
};

/** Token for skipping the element. This is returned in open calls. */
export const SKIP_TOKEN = {};

/** PatchInner using Soy-IDOM semantics. */
export const patchInner = incrementaldom.createPatchInner(patchConfig);
/** PatchOuter using Soy-IDOM semantics. */
export const patchOuter = incrementaldom.createPatchOuter(patchConfig);
/** PatchInner using Soy-IDOM semantics. */
export const patch = patchInner;

/** Type for HTML templates */
export type Template<T> =
    // tslint:disable-next-line:no-any
    (renderer: IncrementalDomRenderer, args: T, ijData?: googSoy.IjData|null) =>
        void;

interface IdomRendererApi {
  open(nameOrCtor: string, key?: string): void|HTMLElement;
  openSSR(nameOrCtor: string, key?: string, data?: unknown): boolean;
  visit(el: void|HTMLElement): void;
  maybeSkip(renderer: IncrementalDomRenderer, val: unknown): boolean;
  pushManualKey(key: incrementaldom.Key): void;
  popManualKey(): void;
  pushKey(key: string): string;
  getNewKey(key: string): string;
  popKey(oldKey: string): void;
  getCurrentKeyStack(): string;
  elementClose(): void|Element;
  close(): void|Element;
  text(value: string): void|Text;
  attr(name: string, value: string): void;
  currentPointer(): Node|null;
  skip(): void;
  currentElement(): void|Element;
  skipNode(): void;
  applyAttrs(): void;
  applyStatics(statics: incrementaldom.Statics): void;
  enter(veData: $$VisualElementData, logOnly: boolean): void;
  exit(): void;
  toNullRenderer(): IdomRendererApi;
  toDefaultRenderer(): IdomRendererApi;
  setLogger(logger: Logger|null): void;
  getLogger(): Logger|null;
  verifyLogOnly(logOnly: boolean): boolean;
  evalLoggingFunction(name: string, args: Array<{}>, placeHolder: string):
      string;
}

/**
 * Class that mostly delegates to global Incremental DOM runtime. This will
 * eventually take in a logger and conditionally mute. These methods may
 * return void when idom commands are muted for velogging.
 */
export class IncrementalDomRenderer implements IdomRendererApi {
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
  private logger: Logger|null = null;

  /**
   * Pushes/pops the given key from `keyStack` (versus `Array#concat`)
   * to avoid allocating a new array for every element open.
   */
  open(nameOrCtor: string, key = ''): HTMLElement|void {
    const el = incrementaldom.open(nameOrCtor, this.getNewKey(key));
    this.visit(el);
    return el;
  }

  /**
   * Open but for nodes that use {skip}. This uses a key that is serialized
   * at server-side rendering.
   * For more information, see go/typed-html-templates.
   */
  openSSR(nameOrCtor: string, key = '', data: unknown = null) {
    key = this.getNewKey(key);
    const el = incrementaldom.open(nameOrCtor, key);
    this.visit(el);

    // `data` is only passed by {skip} elements that are roots of templates.
    if (goog.DEBUG && el && data) {
      maybeReportErrors(el, data);
    }

    // If the element has already been rendered, tell the template to skip it.
    // Caveat: if the element has only attributes, we will skip regardless.
    if (el && el.hasChildNodes()) {
      this.skip();
      // And exit its node so that we will continue with the next node.
      this.close();
      return false;
    }

    // Only set the marker attribute when actually populating the element.
    if (goog.DEBUG) {
      this.attr('soy-skip-key-debug', key);
    }

    // If we have not yet populated this element, tell the template to do so.
    return true;
  }

  // For users extending IncrementalDomRenderer
  visit(el: HTMLElement|void) {}

  /**
   * Called on the return value of open. This is only true if it is exactly
   * the skip token. This has the side effect of performing the skip.
   */
  maybeSkip(renderer: IncrementalDomRenderer, val: unknown) {
    if (val === SKIP_TOKEN) {
      renderer.skip();
      renderer.close();
      return true;
    }
    return false;
  }

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
    const oldKey = this.getCurrentKeyStack();
    this.keyStackHolder[this.keyStackHolder.length - 1] = this.getNewKey(key);
    return oldKey;
  }

  getNewKey(key: string) {
    const oldKey = this.getCurrentKeyStack();
    const serializedKey = soy.$$serializeKey(key);
    return serializedKey + oldKey;
  }

  /**
   * Called (from generated template render function) AFTER template
   * calls.
   */
  popKey(oldKey: string) {
    this.keyStackHolder[this.keyStackHolder.length - 1] = oldKey;
  }

  /**
   * Returns the stack on top of the holder. This represents the current
   * chain of keys.
   */
  getCurrentKeyStack(): string {
    return this.keyStackHolder[this.keyStackHolder.length - 1] || '';
  }

  close(): Element|void {
    return incrementaldom.close();
  }

  elementClose(): Element|void {
    const el = this.close();
    if (el && el.__soy_patch_handler) {
      el.__soy_patch_handler();
    }
    return el;
  }

  text(value: string): Text|void {
    return incrementaldom.text(value);
  }

  attr(name: string, value: string) {
    incrementaldom.attr(name, value);
  }

  currentPointer(): Node|null {
    return incrementaldom.currentPointer();
  }

  skip() {
    incrementaldom.skip();
  }

  currentElement(): Element|void {
    return incrementaldom.currentElement();
  }

  skipNode() {
    incrementaldom.skipNode();
  }

  applyAttrs() {
    incrementaldom.applyAttrs();
  }

  applyStatics(statics: incrementaldom.Statics) {
    incrementaldom.applyStatics(statics);
  }

  /**
   * Called when a `{velog}` statement is entered.
   */
  enter(veData: $$VisualElementData, logOnly: boolean) {
    if (this.logger) {
      this.logger.enter(new ElementMetadata(
          veData.getVe().getId(), veData.getData(), logOnly));
    }
  }

  /**
   * Called when a `{velog}` statement is exited.
   */
  exit() {
    if (this.logger) {
      this.logger.exit();
    }
  }

  /**
   * Switches runtime to produce incremental dom calls that do not traverse
   * the DOM. This happens when logOnly in a velogging node is set to true.
   * For more info, see http://go/soy/reference/velog#the-logonly-attribute
   */
  toNullRenderer() {
    const nullRenderer = new NullRenderer(this);
    return nullRenderer;
  }

  toDefaultRenderer(): IncrementalDomRenderer {
    throw new Error(
        'Cannot transition a default renderer to a default renderer');
  }

  /** Called by user code to configure logging */
  setLogger(logger: Logger|null) {
    this.logger = logger;
  }

  getLogger() {
    return this.logger;
  }

  /**
   * Used to trigger the requirement that logOnly can only be true when a
   * logger is configured. Otherwise, it is a passthrough function.
   */
  verifyLogOnly(logOnly: boolean) {
    if (!this.logger && logOnly) {
      throw new Error(
          'Cannot set logonly="true" unless there is a logger configured');
    }
    return logOnly;
  }

  /*
   * Called when a logging function is evaluated.
   */
  evalLoggingFunction(name: string, args: Array<{}>, placeHolder: string):
      string {
    if (this.logger) {
      return this.logger.evalLoggingFunction(name, args);
    }
    return placeHolder;
  }
}

/**
 * Renderer that mutes all IDOM commands and returns void.
 * For more info, see http://go/soy/reference/velog#the-logonly-attribute
 */
export class NullRenderer extends IncrementalDomRenderer {
  constructor(private readonly renderer: IncrementalDomRenderer) {
    super();
    this.setLogger(renderer.getLogger());
  }

  open(nameOrCtor: string, key?: string) {}

  openSSR(nameOrCtor: string, key?: string) {
    return true;
  }

  close() {}
  elementClose() {}

  text(value: string) {}

  attr(name: string, value: string) {}

  currentPointer() {
    return null;
  }

  applyAttrs() {}

  applyStatics(statics: incrementaldom.Statics) {}

  skip() {}

  key(val: string) {}

  currentElement() {}

  skipNode() {}

  /** Returns to the default renderer which will traverse the DOM. */
  toDefaultRenderer() {
    this.renderer!.setLogger(this.getLogger());
    return this.renderer;
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
    proposedKey: unknown, currentPointerKey: unknown) {
  // This is always true in Soy-IDOM, but Incremental DOM believes that it may
  // be null or number.
  if (typeof proposedKey === 'string' &&
      typeof currentPointerKey === 'string') {
    return proposedKey.startsWith(currentPointerKey) ||
        currentPointerKey.startsWith(proposedKey);
  }
  // Using "==" instead of "===" is intentional. SSR serializes attributes
  // differently than the type that keys are. For example "0" == 0.
  // tslint:disable-next-line:triple-equals
  return proposedKey == currentPointerKey;
}

function maybeReportErrors(el: HTMLElement, data: unknown) {
  const stringifiedParams = JSON.stringify(data, jsonProtoReplacer, 2);
  if (!el.__lastParams) {
    el.__lastParams = stringifiedParams;
    return;
  }
  if (stringifiedParams !== el.__lastParams) {
    throw new Error(`
Tried to rerender a {skip} template with different parameters!
Make sure that you never pass a parameter that can change to a template that has
{skip}, since changes to that parameter won't affect the skipped content.

Old parameters: ${el.__lastParams}
New parameters: ${stringifiedParams}

Element:
${el.dataset['debugSoy'] || el.outerHTML}`);
  }
}

/** Serializes JSPB protos using toObject if available. */
function jsonProtoReplacer(key: string, value: unknown) {
  if (value instanceof Message && !COMPILED &&
      // tslint:disable-next-line:no-any Call undeclared function.
      typeof (value as any)['toObject'] === 'function') {
    // tslint:disable-next-line:no-any Call undeclared function.
    JSON.stringify((value as any)['toObject'](), null, 2);
  }
  // All other values are serialized as-is, which will recursibly call this.
  return value;
}


/**
 * A Renderer that keeps track of whether it was ever called to render anything,
 * but never actually does anything  This is used to check whether an HTML value
 * is empty (if it's used in an `{if}` or conditional operator).
 */
export class FalsinessRenderer implements IdomRendererApi {
  visit(el: void|HTMLElement): void {}
  pushManualKey(key: incrementaldom.Key) {}
  popManualKey(): void {}
  pushKey(key: string): string {
    return '';
  }
  getNewKey(key: string): string {
    return '';
  }
  popKey(oldKey: string): void {}
  getCurrentKeyStack(): string {
    return '';
  }
  enter(): void {}
  exit(): void {}
  toNullRenderer(): IdomRendererApi {
    return this;
  }
  toDefaultRenderer(): IdomRendererApi {
    return this;
  }
  setLogger(logger: Logger|null): void {}
  getLogger(): Logger|null {
    return null;
  }
  verifyLogOnly(logOnly: boolean): boolean {
    throw new Error('Cannot evaluate VE functions in conditions.');
  }
  evalLoggingFunction(name: string, args: Array<{}>, placeHolder: string):
      string {
    return placeHolder;
  }
  private rendered = false;

  /** Checks whether any DOM was rendered. */
  didRender() {
    return this.rendered;
  }

  open(nameOrCtor: string, key?: string) {
    this.rendered = true;
  }

  openSSR(nameOrCtor: string, key?: string) {
    this.rendered = true;
    // Always skip, since we already know that we rendered things.
    return false;
  }

  maybeSkip() {
    this.rendered = true;
    // Always skip, since we already know that we rendered things.
    return true;
  }

  close() {
    this.rendered = true;
  }

  elementClose() {
    this.rendered = true;
  }

  text(value: string) {
    this.rendered = true;
  }

  attr(name: string, value: string) {
    this.rendered = true;
  }

  currentPointer() {
    return null;
  }

  applyAttrs() {
    this.rendered = true;
  }

  applyStatics(statics: incrementaldom.Statics) {
    this.rendered = true;
  }

  skip() {
    this.rendered = true;
  }

  key(val: string) {}

  currentElement() {}

  skipNode() {
    this.rendered = true;
  }
}
