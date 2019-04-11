/**
 * @fileoverview
 *
 * Functions necessary to interact with the Soy-Idom runtime.
 */

import 'goog:soy.velog'; // from //javascript/template/soy:soyutils_velog

import UnsanitizedText from 'goog:goog.soy.data.UnsanitizedText'; // from //javascript/closure/soy:data
import * as soy from 'goog:soy';  // from //javascript/template/soy:soy_usegoog_js
import {$$VisualElementData, ElementMetadata, Logger} from 'goog:soy.velog';  // from //javascript/template/soy:soyutils_velog
import * as incrementaldom from 'incrementaldom';  // from //third_party/javascript/incremental_dom:incrementaldom

/** A key as stored in the stack.  This is JSON-serialzed for idom APIs. */
type ElementKey = string|number|null;

/** A key as passed from compiled Soy. Soy includes undefined in null. */
type ElementKeyParam = ElementKey|UnsanitizedText|undefined;

/** Unwraps UnsanitizedText objects passed from Soy so they match strings. */
function unwrapKey(key: ElementKeyParam): ElementKey {
  if (key instanceof UnsanitizedText) return key.toString();
  if (key === undefined) return null;
  return key;
}

/** Type for HTML templates */
export type Template<T> =
    // tslint:disable-next-line:no-any
    (renderer: IncrementalDomRenderer, args: T, ijData?: soy.IjData) => any;

/**
 * Class that mostly delegates to global Incremental DOM runtime. This will
 * eventually take in a logger and conditionally mute. These methods may
 * return void when idom commands are muted for velogging.
 */
export class IncrementalDomRenderer {
  // Stack (holder) of key stacks for the current template being rendered, which
  // has context on where the template was called from and is used to
  // key each template call (see go/soy-idom-diffing-semantics).
  // Works as follows:
  // - A new key is pushed onto the topmost key stack before a template call,
  // - and popped after the call.
  // - A new stack is pushed onto the holder before a manually keyed element
  //   is opened, and popped before the element is closed. This is because
  //   manual keys "reset" the key context.
  private keyStackHolder: ElementKey[][] = [[]];
  private logger: Logger|null = null;

  /**
   * Wrapper over `elementOpen/elementOpenStart` calls.
   * Pushes/pops the given key from `keyStack` (versus `Array#concat`)
   * to avoid allocating a new array for every element open.
   */
  private elementOpenWrapper(
      elementOpenFn:
          (name: string, key?: string|null, statics?: string[]) => void,
      nameOrCtor: string, key?: ElementKeyParam, statics?: string[]) {
    if (key !== undefined) {
      this.pushKey(unwrapKey(key));
    }
    const keyStack = this.getCurrentKeyStack();
    const el =
        elementOpenFn(nameOrCtor, getKeyForCurrentPointer(keyStack), statics);
    if (key !== undefined) {
      this.popKey();
    }
    return el;
  }

  alignWithDOM(tagName: string, key: string) {
    incrementaldom.alignWithDOM(tagName, key);
  }

  /**
   * Called (from generated template render function) before OPENING
   * keyed elements.
   */
  pushManualKey(key: ElementKeyParam) {
    this.keyStackHolder.push([unwrapKey(key)]);
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
  pushKey(key: ElementKeyParam) {
    const keyStack = this.keyStackHolder[this.keyStackHolder.length - 1];
    keyStack.push(unwrapKey(key));
  }

  /**
   * Called (from generated template render function) AFTER template
   * calls.
   */
  popKey() {
    const keyStack = this.keyStackHolder[this.keyStackHolder.length - 1];
    keyStack.pop();
  }

  /**
   * Returns the stack on top of the holder. This represents the current
   * chain of keys.
   */
  getCurrentKeyStack(): ElementKey[] {
    return this.keyStackHolder[this.keyStackHolder.length - 1];
  }

  elementOpen(nameOrCtor: string, key?: ElementKeyParam, statics?: string[]):
      HTMLElement|void {
    return this.elementOpenWrapper(
        incrementaldom.elementOpen, nameOrCtor, key, statics);
  }

  elementClose(name: string): Element|void {
    return incrementaldom.elementClose(name);
  }

  elementOpenStart(name: string, key?: ElementKeyParam, statics?: string[]) {
    return this.elementOpenWrapper(
        incrementaldom.elementOpenStart, name, key, statics);
  }

  elementOpenEnd(): HTMLElement|void {
    return incrementaldom.elementOpenEnd();
  }

  text(value: string): Text|void {
    return incrementaldom.text(value);
  }

  attr(name: string, value: string) {
    return incrementaldom.attr(name, value);
  }

  currentPointer(): Node|null {
    return incrementaldom.currentPointer();
  }

  skip() {
    return incrementaldom.skip();
  }

  currentElement(): HTMLElement|void {
    return incrementaldom.currentElement();
  }

  skipNode() {
    return incrementaldom.skipNode();
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

  elementOpen(
      nameOrCtor: string, key?: ElementKey, statics?: string[],
      ...varArgs: string[]) {}

  alignWithDOM(name: string, key: string) {}

  elementClose(name: string) {}

  elementOpenStart(name: string, key?: ElementKey, statics?: string[]) {}

  elementOpenEnd() {}

  text(value: string) {}

  attr(name: string, value: string) {}

  currentPointer() {
    return null;
  }

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
 * For the current pointer, returns the correct key - either the original
 * key if the proposed key is a suffix (this means that we're patching
 * a subtree of the originally rendered template), or the proposed key
 * otherwise (go/soy-idom-suffix-matching-strategy).
 */
function getKeyForCurrentPointer(proposedKeyArr?: ElementKey[]): string|null {
  if (!proposedKeyArr) return null;

  const currentPointer = incrementaldom.currentPointer();
  if (!currentPointer || !incrementaldom.isDataInitialized(currentPointer)) {
    // If there is no current pointer or its data has not been initialized,
    // this means the element is being rendered or hydrated for the first
    // time, so just use the proposed key.
    return JSON.stringify(proposedKeyArr);
  }

  const currentPointerKey = incrementaldom.getKey(currentPointer) as string;
  // If the current pointer has no key, just use the proposed key.
  // This is expected to happen when doing the initial client-side hydration
  // of server-side rendered DOM.
  if (!currentPointerKey) return JSON.stringify(proposedKeyArr);

  const currentPointerKeyArr = JSON.parse(currentPointerKey);
  return isProposedKeySuffixOfCurrentKey(proposedKeyArr, currentPointerKeyArr) ?
      // Just use the current (original) key.
      currentPointerKey :
      JSON.stringify(proposedKeyArr);
}

/**
 * Returns whether the proposed key is a suffix of the current key.
 * For example:
 * - proposedKeyArr: ['b', 'c'], currentKeyArr: ['a', 'b', 'c'] => true
 * - proposedKeyArr: ['b', 'c'], currentKeyArr: ['a', 'b', 'c', 'd'] => false
 */
export function isProposedKeySuffixOfCurrentKey(
    proposedKeyArr: ElementKey[], currentPointerKeyArr: ElementKey[]) {
  let i = proposedKeyArr.length - 1;
  let j = currentPointerKeyArr.length - 1;
  while (i >= 0 && j >= 0 && proposedKeyArr[i] === currentPointerKeyArr[j]) {
    i--;
    j--;
  }
  return i < 0;
}
