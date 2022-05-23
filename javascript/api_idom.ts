/**
 * @fileoverview
 *
 * Functions necessary to interact with the Soy-Idom runtime.
 */

import {toObjectForTesting} from 'google3/javascript/apps/jspb/debug';
import {Message} from 'google3/javascript/apps/jspb/message';
import * as soy from 'google3/javascript/template/soy/soyutils_usegoog';
import {$$VisualElementData, ElementMetadata, Logger} from 'google3/javascript/template/soy/soyutils_velog';
import * as incrementaldom from 'incrementaldom';  // from //third_party/javascript/incremental_dom:incrementaldom

export {IdomTemplate as Template} from './templates';

declare global {
  interface Node {
    __lastParams: string|undefined;
  }
}

/** Constructor for a custom element. */
export interface ElementConstructor {
  new(): Element;
  getTagName(): string;
}

const patchConfig: incrementaldom.PatchConfig = {
  matches: (
      matchNode, nameOrCtor, expectedNameOrCtor, proposedKey,
      currentPointerKey) => {
    if (typeof expectedNameOrCtor === 'function' &&
        matchNode instanceof Element) {
      expectedNameOrCtor = matchNode.tagName.toLowerCase();
    }
    if (typeof nameOrCtor === 'function' &&
        typeof (nameOrCtor as ElementConstructor).getTagName === 'function') {
      // Reverse map constructor to tag name for validation.
      nameOrCtor = (nameOrCtor as ElementConstructor).getTagName();
    }
    return nameOrCtor === expectedNameOrCtor &&
        isMatchingKey(proposedKey, currentPointerKey);
  }
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
    fnCreator: <T>(patchConfig: incrementaldom.PatchConfig) =>
        incrementaldom.PatchFunction<T, R>,
    patchConfig: incrementaldom.PatchConfig,
    ):
    <T>(node: Element|DocumentFragment, template: (a: T|undefined) => void,
        data?: T|undefined) => R {
  return fnCreator(patchConfig);
}

/** PatchInner using Soy-IDOM semantics. */
export const patchInner =
    wrapAsGeneric(incrementaldom.createPatchInner, patchConfig);
/** PatchOuter using Soy-IDOM semantics. */
export const patchOuter =
    wrapAsGeneric(incrementaldom.createPatchOuter, patchConfig);
/** PatchInner using Soy-IDOM semantics. */
export const patch = patchInner;

/**
 * Attributes object used only for Soy users. If using a mix of Soy and
 * non-Soy, isolate usages of attributes using your own attributes object.
 */
export const attributes = incrementaldom.createAttributeMap();

interface IdomRendererApi {
  open(nameOrCtor: string|ElementConstructor, key?: string): void|HTMLElement;
  openSSR(nameOrCtor: string|ElementConstructor, key?: string, data?: unknown):
      boolean;
  visit(el: void|HTMLElement): void;
  pushManualKey(key: incrementaldom.Key): void;
  popManualKey(): void;
  pushKey(key: string): string;
  getNewKey(key: string|undefined): string;
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
  open(nameOrCtor: string|ElementConstructor, key: string|undefined):
      HTMLElement|void {
    const el = incrementaldom.open(nameOrCtor, this.getNewKey(key));
    this.visit(el);
    return el;
  }

  /**
   * Open but for nodes that use {skip}. This uses a key that is serialized
   * at server-side rendering.
   * For more information, see go/typed-html-templates.
   */
  openSSR(
      nameOrCtor: string|ElementConstructor, key = '', data: unknown = null) {
    key = this.getNewKey(key);
    const el = incrementaldom.open(nameOrCtor, key);
    this.visit(el);

    // `data` is only passed by {skip} elements that are roots of templates.
    if (!COMPILED && goog.DEBUG && el && data) {
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

  getNewKey(key: string|undefined) {
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
    // This helps ensure that hydrations on the server are consistent with
    // client-side renders.
    if (value) {
      return incrementaldom.text(value);
    }
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
    incrementaldom.applyAttrs(attributes);
  }

  applyStatics(statics: incrementaldom.Statics) {
    incrementaldom.applyStatics(statics, attributes);
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
 */
export class NullRenderer extends IncrementalDomRenderer {
  constructor(private readonly renderer: IncrementalDomRenderer) {
    super();
    this.setLogger(renderer.getLogger());
  }

  override open(nameOrCtor: string|ElementConstructor, key?: string) {
    return undefined;
  }

  override openSSR(nameOrCtor: string|ElementConstructor, key?: string) {
    return true;
  }

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

  /** Returns to the default renderer which will traverse the DOM. */
  override toDefaultRenderer() {
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
  // Serializes JSPB protos using toObjectForTesting. This is important as
  // jspb protos modify themselves sometimes just by reading them (e.g. when a
  // nested proto is created it will fill in empty repeated fields
  // into the internal array).
  // Note that we can't use the replacer argument of JSON.stringify as Message
  // contains a toJSON method, which prevents the message instance to be passed
  // to the JSON.stringify replacer.
  // tslint:disable-next-line:no-any Replace private function.
  const msgProto = Message.prototype as any;
  const msgProtoToJSON = msgProto['toJSON'];
  msgProto['toJSON'] = function(this: Message) {
    return toObjectForTesting(this);
  };
  const stringifiedParams = JSON.stringify(data, null, 2);
  msgProto['toJSON'] = msgProtoToJSON;
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
  getNewKey(key: string|undefined): string {
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
    return logOnly;
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

  open(nameOrCtor: string|ElementConstructor, key?: string) {
    this.rendered = true;
    return undefined;
  }

  openSSR(nameOrCtor: string|ElementConstructor, key?: string) {
    this.rendered = true;
    // Always skip, since we already know that we rendered things.
    return false;
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
