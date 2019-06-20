/**
 * @fileoverview
 *
 * Functions necessary to interact with the Soy-Idom runtime.
 */

import 'goog:soy.velog'; // from //javascript/template/soy:soyutils_velog

import * as googSoy from 'goog:goog.soy';  // from //javascript/closure/soy
import {$$VisualElementData, ElementMetadata, Logger} from 'goog:soy.velog';  // from //javascript/template/soy:soyutils_velog
import * as incrementaldom from 'incrementaldom';  // from //third_party/javascript/incremental_dom:incrementaldom

const patchConfig: incrementaldom.PatchConfig = {
  matches:
      (matchNode, nameOrCtor, expectedNameOrCtor, proposedKey,
       currentPointerKey) => nameOrCtor === expectedNameOrCtor &&
      isMatchingKey(proposedKey, currentPointerKey)
};

/** PatchInner using Soy-IDOM semantics. */
export const patchInner = incrementaldom.createPatchInner(patchConfig);
/** PatchOuter using Soy-IDOM semantics. */
export const patchOuter = incrementaldom.createPatchOuter(patchConfig);
/** PatchInner using Soy-IDOM semantics. */
export const patch = patchInner;

/** Type for HTML templates */
export type Template<T> =
    // tslint:disable-next-line:no-any
    (renderer: IncrementalDomRenderer, args: T, ijData?: googSoy.IjData) => any;

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
  // Note that for performance, the "stack" is implemented as a string with
  // the items being `${SIZE OF KEY}${DELIMITER}${KEY}`.
  private readonly keyStackHolder: string[] = [];
  private logger: Logger|null = null;

  /**
   * Wrapper over `elementOpen/elementOpenStart` calls.
   * Pushes/pops the given key from `keyStack` (versus `Array#concat`)
   * to avoid allocating a new array for every element open.
   */
  private elementOpenWrapper(
      elementOpenFn:
          (name: string, key?: string|null, statics?: string[]) => void,
      nameOrCtor: string, key?: string, statics?: string[]) {
    let oldKey: string;
    if (key !== undefined) {
      oldKey = this.pushKey(key);
    }
    const keyStack = this.getCurrentKeyStack();
    const el = elementOpenFn(nameOrCtor, keyStack, statics);
    if (key !== undefined) {
      this.popKey(oldKey!);
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
  pushManualKey(key: incrementaldom.Key) {
    this.keyStackHolder.push(serializeKey(key));
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
    const serializedKey = serializeKey(key);
    this.keyStackHolder[this.keyStackHolder.length - 1] =
        serializedKey + oldKey;
    return oldKey;
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

  elementOpen(nameOrCtor: string, key?: string, statics?: string[]): HTMLElement
      |void {
    return this.elementOpenWrapper(
        incrementaldom.elementOpen, nameOrCtor, key, statics);
  }

  elementClose(name: string): Element|void {
    return incrementaldom.elementClose(name);
  }

  elementOpenStart(name: string, key?: string, statics?: string[]) {
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
      nameOrCtor: string, key?: string, statics?: string[],
      ...varArgs: string[]) {}

  alignWithDOM(name: string, key: string) {}

  elementClose(name: string) {}

  elementOpenStart(name: string, key?: string, statics?: string[]) {}

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
 * Provides a compact serialization format for the key structure.
 */
export function serializeKey(item: string|number|null|undefined) {
  const stringified = String(item);
  let delimiter;
  if (item == null) {
    delimiter = '_';
  } else if (typeof item === 'number') {
    delimiter = '#';
  } else {
    delimiter = ':';
  }
  return `${stringified.length}${delimiter}${stringified}`;
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
  return proposedKey === currentPointerKey;
}
