/**
 *
 * @fileoverview
 *
 * Functions necessary to interact with the Soy-Idom runtime.
 *
 * @suppress {checkTypes,constantProperty,extraRequire,missingOverride,missingReturn,unusedPrivateMembers,uselessCode}
 * checked by tsc
 */
goog.module('google3.javascript.template.soy.api_idom');
var module = module || {id: 'javascript/template/soy/api_idom.closure.js'};
module = module;
exports = {};
const tsickle_soy_1 = goog.requireType('goog.soy');
const tsickle_velog_2 = goog.requireType('soy.velog');
const tsickle_incremental_dom_3 = goog.requireType('incrementaldom');
const tsickle_types_4 = goog.requireType('incrementaldom.src.types');
const tsickle_module_1_ = goog.require('soy.velog');  // from //javascript/template/soy:soyutils_velog
// from //javascript/closure/soy
const goog_soy_velog_1 =
    tsickle_module_1_;  // from //javascript/template/soy:soyutils_velog
// from //javascript/template/soy:soyutils_velog
const incrementaldom = goog.require('incrementaldom');  // from //third_party/javascript/incremental_dom:incrementaldom
// from //third_party/javascript/incremental_dom:incrementaldom
/**
 * @type {{matches: (undefined|function(!Node,
 *     (string|!tsickle_types_4.ElementConstructor),
 *     (string|!tsickle_types_4.ElementConstructor),
 *     (undefined|null|string|number), (undefined|null|string|number)):
 *     boolean)}}
 */
const patchConfig = {
  matches: (/**
             * @param {!Node} matchNode
             * @param {(string|!tsickle_types_4.ElementConstructor)} nameOrCtor
             * @param {(string|!tsickle_types_4.ElementConstructor)}
             *     expectedNameOrCtor
             * @param {(undefined|null|string|number)} proposedKey
             * @param {(undefined|null|string|number)} currentPointerKey
             * @return {boolean}
             */
            (matchNode, nameOrCtor, expectedNameOrCtor, proposedKey,
             currentPointerKey) => nameOrCtor === expectedNameOrCtor &&
                isMatchingKey(proposedKey, currentPointerKey))
};
/**
 * PatchInner using Soy-IDOM semantics.
 * @type {function((!Element|!DocumentFragment), function((undefined|?)): void,
 *     (undefined|?)=): !Node}
 */
exports.patchInner = incrementaldom.createPatchInner(patchConfig);
/**
 * PatchOuter using Soy-IDOM semantics.
 * @type {function((!Element|!DocumentFragment), function((undefined|?)): void,
 *     (undefined|?)=): (null|!Node)}
 */
exports.patchOuter = incrementaldom.createPatchOuter(patchConfig);
/**
 * PatchInner using Soy-IDOM semantics.
 * @type {function((!Element|!DocumentFragment), function((undefined|?)): void,
 *     (undefined|?)=): !Node}
 */
exports.patch = exports.patchInner;
/**
 * Type for HTML templates
 * @typedef {function(!IncrementalDomRenderer, ?,
 * (undefined|!tsickle_soy_1.IjData)=): ?}
 */
exports.Template;
/**
 * Class that mostly delegates to global Incremental DOM runtime. This will
 * eventually take in a logger and conditionally mute. These methods may
 * return void when idom commands are muted for velogging.
 */
class IncrementalDomRenderer {
  constructor() {
    // Stack (holder) of key stacks for the current template being rendered,
    // which has context on where the template was called from and is used to
    // key each template call (see go/soy-idom-diffing-semantics).
    // Works as follows:
    // - A new key is pushed onto the topmost key stack before a template call,
    // - and popped after the call.
    // - A new stack is pushed onto the holder before a manually keyed element
    //   is opened, and popped before the element is closed. This is because
    //   manual keys "reset" the key context.
    // Note that for performance, the "stack" is implemented as a string with
    // the items being `${SIZE OF KEY}${DELIMITER}${KEY}`.
    this.keyStackHolder = [];
    this.logger = null;
  }
  /**
   * Wrapper over `elementOpen/elementOpenStart` calls.
   * Pushes/pops the given key from `keyStack` (versus `Array#concat`)
   * to avoid allocating a new array for every element open.
   * @private
   * @param {function(string, (undefined|null|string)=,
   *     (undefined|!Array<string>)=): void} elementOpenFn
   * @param {string} nameOrCtor
   * @param {(undefined|string)=} key
   * @param {(undefined|!Array<string>)=} statics
   * @return {void}
   */
  elementOpenWrapper(elementOpenFn, nameOrCtor, key, statics) {
    /** @type {string} */
    let oldKey;
    if (key !== undefined) {
      oldKey = this.pushKey(key);
    }
    /** @type {string} */
    const keyStack = this.getCurrentKeyStack();
    /** @type {void} */
    const el = elementOpenFn(nameOrCtor, keyStack, statics);
    if (key !== undefined) {
      this.popKey((/** @type {string} */ (oldKey)));
    }
    return el;
  }
  /**
   * @param {string} tagName
   * @param {string} key
   * @return {void}
   */
  alignWithDOM(tagName, key) {
    incrementaldom.alignWithDOM(tagName, key);
  }
  /**
   * Called (from generated template render function) before OPENING
   * keyed elements.
   * @param {(undefined|null|string|number)} key
   * @return {void}
   */
  pushManualKey(key) {
    this.keyStackHolder.push(serializeKey(key));
  }
  /**
   * Called (from generated template render function) before CLOSING
   * keyed elements.
   * @return {void}
   */
  popManualKey() {
    this.keyStackHolder.pop();
  }
  /**
   * Called (from generated template render function) BEFORE template
   * calls.
   * @param {string} key
   * @return {string}
   */
  pushKey(key) {
    /** @type {string} */
    const oldKey = this.getCurrentKeyStack();
    /** @type {string} */
    const serializedKey = serializeKey(key);
    this.keyStackHolder[this.keyStackHolder.length - 1] =
        serializedKey + oldKey;
    return oldKey;
  }
  /**
   * Called (from generated template render function) AFTER template
   * calls.
   * @param {string} oldKey
   * @return {void}
   */
  popKey(oldKey) {
    this.keyStackHolder[this.keyStackHolder.length - 1] = oldKey;
  }
  /**
   * Returns the stack on top of the holder. This represents the current
   * chain of keys.
   * @return {string}
   */
  getCurrentKeyStack() {
    return this.keyStackHolder[this.keyStackHolder.length - 1] || '';
  }
  /**
   * @param {string} nameOrCtor
   * @param {(undefined|string)=} key
   * @param {(undefined|!Array<string>)=} statics
   * @return {(void|!HTMLElement)}
   */
  elementOpen(nameOrCtor, key, statics) {
    return this.elementOpenWrapper(
        incrementaldom.elementOpen, nameOrCtor, key, statics);
  }
  /**
   * @param {string} name
   * @return {(void|!Element)}
   */
  elementClose(name) {
    return incrementaldom.elementClose(name);
  }
  /**
   * @param {string} name
   * @param {(undefined|string)=} key
   * @param {(undefined|!Array<string>)=} statics
   * @return {void}
   */
  elementOpenStart(name, key, statics) {
    return this.elementOpenWrapper(
        incrementaldom.elementOpenStart, name, key, statics);
  }
  /**
   * @return {(void|!HTMLElement)}
   */
  elementOpenEnd() {
    return incrementaldom.elementOpenEnd();
  }
  /**
   * @param {string} value
   * @return {(void|!Text)}
   */
  text(value) {
    return incrementaldom.text(value);
  }
  /**
   * @param {string} name
   * @param {string} value
   * @return {void}
   */
  attr(name, value) {
    return incrementaldom.attr(name, value);
  }
  /**
   * @return {(null|!Node)}
   */
  currentPointer() {
    return incrementaldom.currentPointer();
  }
  /**
   * @return {void}
   */
  skip() {
    return incrementaldom.skip();
  }
  /**
   * @return {(void|!HTMLElement)}
   */
  currentElement() {
    return incrementaldom.currentElement();
  }
  /**
   * @return {void}
   */
  skipNode() {
    return incrementaldom.skipNode();
  }
  /**
   * Called when a `{velog}` statement is entered.
   * @param {!module$contents$soy$velog_$$VisualElementData} veData
   * @param {boolean} logOnly
   * @return {void}
   */
  enter(veData, logOnly) {
    if (this.logger) {
      this.logger.enter(new goog_soy_velog_1.ElementMetadata(
          veData.getVe().getId(), veData.getData(), logOnly));
    }
  }
  /**
   * Called when a `{velog}` statement is exited.
   * @return {void}
   */
  exit() {
    if (this.logger) {
      this.logger.exit();
    }
  }
  toNullRenderer() {
    /** @type {!NullRenderer} */
    const nullRenderer = new NullRenderer(this);
    return nullRenderer;
  }
  /**
   * @return {!IncrementalDomRenderer}
   */
  toDefaultRenderer() {
    throw new Error(
        'Cannot transition a default renderer to a default renderer');
  }
  /**
   * Called by user code to configure logging
   * @param {(null|!module$contents$soy$velog_Logger)} logger
   * @return {void}
   */
  setLogger(logger) {
    this.logger = logger;
  }
  /**
   * @return {(null|!module$contents$soy$velog_Logger)}
   */
  getLogger() {
    return this.logger;
  }
  /**
   * Used to trigger the requirement that logOnly can only be true when a
   * logger is configured. Otherwise, it is a passthrough function.
   * @param {boolean} logOnly
   * @return {boolean}
   */
  verifyLogOnly(logOnly) {
    if (!this.logger && logOnly) {
      throw new Error(
          'Cannot set logonly="true" unless there is a logger configured');
    }
    return logOnly;
  }
  /*
   * Called when a logging function is evaluated.
   */
  /**
   * @param {string} name
   * @param {!Array<*>} args
   * @param {string} placeHolder
   * @return {string}
   */
  evalLoggingFunction(name, args, placeHolder) {
    if (this.logger) {
      return this.logger.evalLoggingFunction(name, args);
    }
    return placeHolder;
  }
}
exports.IncrementalDomRenderer = IncrementalDomRenderer;
if (false) {
  /**
   * @type {!Array<string>}
   * @private
   */
  IncrementalDomRenderer.prototype.keyStackHolder;
  /**
   * @type {(null|!module$contents$soy$velog_Logger)}
   * @private
   */
  IncrementalDomRenderer.prototype.logger;
}
class NullRenderer extends IncrementalDomRenderer {
  /**
   * @param {!IncrementalDomRenderer} renderer
   */
  constructor(renderer) {
    super();
    this.renderer = renderer;
    this.setLogger(renderer.getLogger());
  }
  /**
   * @param {string} nameOrCtor
   * @param {(undefined|string)=} key
   * @param {(undefined|!Array<string>)=} statics
   * @param {...string} varArgs
   * @return {void}
   */
  elementOpen(nameOrCtor, key, statics, ...varArgs) {}
  /**
   * @param {string} name
   * @param {string} key
   * @return {void}
   */
  alignWithDOM(name, key) {}
  /**
   * @param {string} name
   * @return {void}
   */
  elementClose(name) {}
  /**
   * @param {string} name
   * @param {(undefined|string)=} key
   * @param {(undefined|!Array<string>)=} statics
   * @return {void}
   */
  elementOpenStart(name, key, statics) {}
  /**
   * @return {void}
   */
  elementOpenEnd() {}
  /**
   * @param {string} value
   * @return {void}
   */
  text(value) {}
  /**
   * @param {string} name
   * @param {string} value
   * @return {void}
   */
  attr(name, value) {}
  /**
   * @return {null}
   */
  currentPointer() {
    return null;
  }
  /**
   * @return {void}
   */
  skip() {}
  /**
   * @param {string} val
   * @return {void}
   */
  key(val) {}
  /**
   * @return {void}
   */
  currentElement() {}
  /**
   * @return {void}
   */
  skipNode() {}
  /**
   * Returns to the default renderer which will traverse the DOM.
   * @return {!IncrementalDomRenderer}
   */
  toDefaultRenderer() {
    (/** @type {!IncrementalDomRenderer} */ (this.renderer))
        .setLogger(this.getLogger());
    return this.renderer;
  }
}
exports.NullRenderer = NullRenderer;
if (false) {
  /**
   * @type {!IncrementalDomRenderer}
   * @private
   */
  NullRenderer.prototype.renderer;
}
/**
 * Provides a compact serialization format for the key structure.
 * @param {(undefined|null|string|number)} item
 * @return {string}
 */
function serializeKey(item) {
  /** @type {string} */
  const stringified = String(item);
  /** @type {?} */
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
exports.serializeKey = serializeKey;
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
 * @param {*} proposedKey
 * @param {*} currentPointerKey
 * @return {boolean}
 */
function isMatchingKey(proposedKey, currentPointerKey) {
  // This is always true in Soy-IDOM, but Incremental DOM believes that it may
  // be null or number.
  if (typeof proposedKey === 'string' &&
      typeof currentPointerKey === 'string') {
    return proposedKey.startsWith(currentPointerKey) ||
        currentPointerKey.startsWith(proposedKey);
  }
  return proposedKey === currentPointerKey;
}
exports.isMatchingKey = isMatchingKey;
//#