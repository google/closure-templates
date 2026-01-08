/*
 * Copyright 2017 Google Inc.
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

/**
 * @fileoverview
 * Utility functions and classes for supporting visual element logging in the
 * client side.
 *
 * <p>
 * This file contains utilities that should only be called by Soy-generated
 * JS code. Please do not use these functions directly from your hand-written
 * code. Their names all start with '$$'.
 */

goog.module('soy.velog');

const ImmutableLoggableElementMetadata = goog.require('proto.soy.ImmutableLoggableElementMetadata');
const ReadonlyLoggableElementMetadata = goog.requireType('proto.soy.ReadonlyLoggableElementMetadata');
const {Message} = goog.require('jspb');
const {SafeUrl, safeAttrPrefix, trySanitizeUrl} = goog.require('google3.third_party.javascript.safevalues.index');
const {assert, assertString} = goog.require('goog.asserts');
const {setAnchorHref, setElementPrefixedAttribute} = goog.require('google3.third_party.javascript.safevalues.dom.index');
const {startsWith} = goog.require('goog.string');


/**
 * @param {string} match The full match
 * @param {string} g The captured group
 * @return {string} The replaced string
 */
function camelCaseReplacer(match, g) {
  return g.toUpperCase();
}

/**
 * @param {string} key The data attribute key
 * @return {string} The corresponding dataset property key
 */
function dataAttrToDataSetProperty(key) {
  assert(key.startsWith('data-'), 'key must start with data-');
  // turn data-foo-bar into fooBar
  return key.substring(5).toLowerCase().replace(/-([a-z])/g, camelCaseReplacer);
}

/**
 * @param {!HTMLElement} el
 * @return {!HTMLAnchorElement}
 */
function asAnchor(el) {
  if (el.tagName !== 'A') {
    throw new Error(
        'logger attempted to add anchor attributes to a non-anchor element.');
  }
  return /** @type {!HTMLAnchorElement} */ (el);
}


/**
 * Checks if the given attribute already exists on the element.
 * @param {!HTMLElement} element
 * @param {string} attrName
 */
function checkNotDuplicate(element, attrName) {
  if (element.hasAttribute(attrName)) {
    throw new Error(
        'logger attempted to override an attribute ' + attrName +
        ' that already exists.');
  }
}

/**
 * A type-safe wrapper for a set of attributes to be added to an element.
 *
 * Logger implementations can return this to add attributes to the root element
 * of the VE tree.  If for some reason the root element is not well defined,
 * then neither is this.
 * @final
 */
class LoggingAttrs {
  constructor() {
    /** @private @const {!Array<string|(function(!HTMLElement):void)>} */
    this.setters = [];
  }
  /**
   * Adds a `data-` attribute to the list of attributes to be added to the
   * element.
   * @param {string} key Must start with `data-`
   * @param {string} value Can be any string
   * @return {!LoggingAttrs} returns this
   */
  addDataAttr(key, value) {
    assertString(key, 'key');
    assertString(value, 'value');
    // turn data-foo-bar into fooBar
    const propertyName = dataAttrToDataSetProperty(key);
    this.setters.push(key, (/** !HTMLElement */ el) => {
      el.dataset[propertyName] = value;
    });
    return this;
  }
  /**
   * Adds an `href` attribute to the list of attributes to be added to the
   * element.  The element must be an anchor.
   * @param {string|!SafeUrl} value Can be any string
   * @return {!LoggingAttrs} returns this
   */
  addAnchorHrefAttr(value) {
    const checkedValue = trySanitizeUrl(value);
    if (checkedValue == null) {
      throw new Error('Invalid href attribute: ' + value);
    }
    // help out the type inference system.
    const nonNullCheckedValue = checkedValue;
    this.setters.push('href', (/** !HTMLElement */ el) => {
      a.setAttribute('href', String(nonNullCheckedValue));
    });
    return this;
  }

  /**
   * Adds a `ping` attribute to the list of attributes to be added to the
   * element.  The element must be an anchor.
   * @param {...(string|!SafeUrl)} value Can be any string
   * @return {!LoggingAttrs} returns this
   */
  addAnchorPingAttr(...value) {
    const final = value
                      .map(v => {
                        v = trySanitizeUrl(v);
                        if (v == null) {
                          throw new Error('Invalid ping attribute: ' + v);
                        }
                        return v;
                      })
                      .join(' ');
    this.setters.push('ping', (/** !HTMLElement */ el) => {
      asAnchor(el).setAttribute('ping', final);
    });
    return this;
  }

  /**
   * Returns a string representation of the attributes to be added to the
   * element.
   * @package
   * @return {string}
   */
  toDebugStringForTesting() {
    if (!goog.DEBUG) {
      throw new Error();
    }
    const a = /** @type {!HTMLAnchorElement} */ (document.createElement('a'));
    this.applyToInternalOnly(a, true);
    const attrs = [];
    a.getAttributeNames().forEach((attr) => {
      attrs.push(attr + '=' + JSON.stringify(a.getAttribute(attr)));
    });
    return attrs.join(' ');
  }

  /**
   * Applies the attributes to the element.
   *
   * @param {!HTMLElement} element The element to apply the attributes to.
   * @param {boolean=} allowOverwrites Whether to allow overwriting existing
   *     attributes.
   * @package
   */
  applyToInternalOnly(element, allowOverwrites) {
    const setters = this.setters;
    for (let i = 0; i < setters.length; i += 2) {
      if (!allowOverwrites) {
        checkNotDuplicate(element, /** @type {string} */ (setters[i]));
      }
      /** @type {function(!HTMLElement)} */ (setters[i + 1])(element);
    }
  }
}

/** @final */
class ElementMetadata {
  /**
   * @param {number} id
   * @param {?Message|undefined} data
   * @param {boolean} logOnly
   * @param {?ReadonlyLoggableElementMetadata|undefined=}
   *     loggableElementMetadata
   */
  constructor(id, data, logOnly, loggableElementMetadata = undefined) {
    /**
     * The identifier for the logging element
     * @const {number}
     */
    this.id = id;

    /**
     * The optional payload from the `data` attribute. This is guaranteed to
     * match the proto_type specified in the logging configuration.
     * @const {?Message|undefined}
     */
    this.data = data;

    /**
     * Whether or not this element is in logOnly mode. In logOnly mode the log
     * records are collected but the actual elements are not rendered.
     * @const {boolean}
     */
    this.logOnly = logOnly;

    /**
     * Additional metadata to be included with the loggable element.
     * @const {?ReadonlyLoggableElementMetadata|undefined}
     */
    this.loggableElementMetadata = loggableElementMetadata;
  }
}

/** @package @final */
class FunctionMetadata {
  /**
   * @param {string} name
   * @param {!Array<?>} args
   */
  constructor(name, args) {
    this.name = name;
    this.args = args;
  }
}

/** @package @final */
class Metadata {
  constructor() {
    /** @type {!Array<!ElementMetadata>} */
    this.elements = [];
    /** @type {!Array<!FunctionMetadata>} */
    this.functions = [];
  }
}

/** @type {?Metadata} */ let metadata;


// NOTE: we need to use toLowerCase in case the xid contains upper case
// characters, browsers normalize keys to their ascii lowercase versions when
// accessing attributes via the programmatic APIs (as we do below).
/** @package */ const ELEMENT_ATTR = 'data-soylog';

/** @package */ const FUNCTION_ATTR = 'data-soyloggingfunction-';
/** @private */ const SAFE_ATTR_PREFIXES = [

/**
 * Tag name used for fragments.
 */
const WRAPPER_ELEMENT_TAG_NAME = 'VELOG';

/** Sets up the global metadata object before rendering any templates. */
function setUpLogging() {
  assert(
      !metadata,
      'Logging metadata already exists. Please call ' +
          'soy.velog.tearDownLogging after rendering a template.');
  metadata = new Metadata();
}

/**
 * Clears the global metadata object after logging so that we won't leak any
 * information between templates.
 */
function tearDownLogging() {
  assert(
      metadata,
      'Logging metadata does not exist. ' +
          'Please call soy.velog.setUpLogging before rendering a template.');
  metadata = null;
}

/**
 * Checks if the global metadata object exists. This is only used by generated
 * code, to avoid directly access the object.
 *
 * @return {boolean}
 */
function $$hasMetadata() {
  return !!metadata;
}

/**
 * Testonly method that sets the fake meta data for testing.
 * @param {!Metadata} testdata
 * @package
 */
function setMetadataTestOnly(testdata) {
  metadata = testdata;
}

/**
 * Records the id and additional data into the global metadata structure.
 *
 * @param {!$$VisualElementData} veData The VE to log.
 * @param {boolean} logOnly Whether to enable counterfactual logging.
 *
 * @return {string} The HTML attribute that will be stored in the DOM.
 */
function $$getLoggingAttribute(veData, logOnly) {
  const dataIdx = storeElementData(veData, logOnly);
  if (dataIdx === -1) {
    return '';
  }
  // Insert a whitespace at the beginning. In VeLogInstrumentationVisitor,
  // we insert the return value of this method as a plain string instead of a
  // HTML attribute, therefore the desugaring pass does not know how to handle
  // whitespaces.
  // Trailing whitespace is not needed since we always add this at the end of
  // a HTML tag.
  return ' ' + ELEMENT_ATTR + '="' + dataIdx + '"';
}

/**
 * Records the data index.
 *
 * @param {!$$VisualElementData} veData The VE to log.
 * @param {boolean} logOnly Whether to enable counterfactual logging.
 *
 * @return {number} The index where the metadata was stored in
 *     Metadata#elements, -1 if not recorded.
 */
function storeElementData(veData, logOnly) {
  if (!metadata) {
    if (logOnly) {
      // If logonly is true but no logger has been configured, we throw an error
      // since this is clearly a misconfiguration.
      throw new Error(
          'Cannot set logonly="true" unless there is a logger configured');
    }
    return -1;
  }
  const elementMetadata = new ElementMetadata(
      veData.getVe().getId(), veData.getData(), logOnly,
      veData.getVe().getMetadata());
  const dataIdx = metadata.elements.push(elementMetadata) - 1;
  return dataIdx;
}

/**
 * Returns the id and additional data into the global metadata structure. This
 * will be used to set the logging data attribute via DOM APIs.
 *
 * @param {!$$VisualElementData} veData The VE to log.
 * @param {boolean} logOnly Whether to enable counterfactual logging.
 *
 * @return {!Array<string | number> | undefined} Tuple containing the Soy
 *     logging attribute name and its corresponding id number value.
 */
function getLoggingAttributeEntry(veData, logOnly) {
  const dataIdx = storeElementData(veData, logOnly);
  if (dataIdx === -1) {
    return undefined;
  }
  return [ELEMENT_ATTR, dataIdx];
}


/**
 * Registers the logging function in the metadata.
 *
 * @param {string} name Obfuscated logging function name.
 * @param {!Array<?>} args List of arguments for the logging function.
 * @param {string} attr The original HTML attribute name.
 *
 * @return {string} The HTML attribute that will be stored in the DOM.
 */
function $$getLoggingFunctionAttribute(name, args, attr) {
  if (metadata) {
    const functionIdx =
        metadata.functions.push(new FunctionMetadata(name, args)) - 1;
    return ' ' + FUNCTION_ATTR + attr + '="' + functionIdx + '"';
  } else {
    return '';
  }
}

/**
 * For a given rendered HTML element, go through the DOM tree and emit logging
 * commands if necessary. This method also discards visual elements that are
 * marked as log only (counterfactual) and promotes `<velog>` element children
 * to be direct siblings.
 *
 * Returns the same element or a fragment with children that replaced it. If the
 * provided {@link element} is replaced with new elements, the caller is
 * responsible for using the returned {@link !DocumentFragment} as the
 * replacement for the element.
 *
 * @param {!Element|!DocumentFragment} element The rendered HTML element.
 * @param {!Logger} logger The logger that actually does stuffs.
 * @return {!Element|!DocumentFragment} The same element or a fragment with the
 *     nodes that replaced it.
 */
function emitLoggingCommands(element, logger) {
  const newElements = doLogEmit(element, logger);
  if (newElements.length === 1) {
    return newElements[0];
  }
  const fragment = document.createDocumentFragment();
  for (const child of newElements) {
    fragment.appendChild(child);
  }
  return fragment;
}

/**
 * For a given rendered HTML element, go through the DOM tree and emit logging
 * commands if necessary. This method also discards visual elements that are
 * marked as log only (counterfactual) and promotes `<velog>` element children
 * to be direct siblings.
 *
 * Does replacements of the provided {@link element} in-place, returning an
 * array with itself or elements that replaced it.
 *
 * @param {!Element|!DocumentFragment} element The rendered HTML element.
 * @param {!Logger} logger The logger that actually does stuffs.
 * @return {!ReadonlyArray<!Element|!DocumentFragment>} The same input element
 *     or a list of elements that replaced it.
 */
function doLogEmit(element, logger) {
  if (element instanceof Element) {
    const newElements = visit(element, logger);
    if (element.parentNode !== null) {
      replaceChild(element.parentNode, element, newElements);
    }
    return newElements;
  } else {
    emitCommandsForRange(element, Array.from(element.childNodes), logger);
    return [element];
  }
}

/**
 * Emit logging commands for a subset of children of a given parent. All
 * mutations are done in-place.
 *
 * If any child is replaced with new elements, the resulting array is returned.
 *
 * @param {!Element|!DocumentFragment} parent The parent element.
 * @param {!ReadonlyArray<!Node>} children The subset of
 *     elements that are children of {@link parent}.
 * @param {!Logger} logger The logger that actually does stuffs.
 * @return {?Array<!Node>} `null` if no replacements were made, otherwise
 *     an array with the new nodes. This can be used to replace the original
 *     {@link children} array.
 */
function emitCommandsForRange(parent, children, logger) {
  /** @type {?Array<!Node>} */
  let result = null;
  for (let i = 0; i < children.length; i++) {
    const child = children[i];
    if (!(child instanceof Element)) {
      continue;
    }

    const newChildren = visit(child, logger);
    if (newChildren.length === 1 && newChildren[0] === child) {
      continue;
    }

    if (!result) {
      result = children.slice(0, i);
    }
    result.push(...newChildren);
    replaceChild(parent, child, newChildren);
  }
  return result;
}

/**
 *
 * @param {!Element|!DocumentFragment} element The rendered HTML element.
 * @param {!Logger} logger The logger that actually does stuffs.
 * @return {!Array<!Element|!DocumentFragment>} The element(s) to replace the
 *     incoming element with. This can be of length zero (which means remove the
 *     element) or contain only the incoming element (which means leave the
 *     element as is).
 */
function visit(element, logger) {
  if (!(element instanceof Element)) {
    return [element];
  }
  let logIndex = -1;
  let pendingAttrs = undefined;
  if (element.hasAttribute(ELEMENT_ATTR)) {
    logIndex = getDataAttribute(element, ELEMENT_ATTR);
    assert(metadata.elements.length > logIndex, 'Invalid logging attribute.');
    if (logIndex != -1) {
      pendingAttrs = logger.enter(metadata.elements[logIndex]);
    }
  }
  replaceFunctionAttributes(element, logger);
  if (element.children) {
    let children = Array.from(element.children);
    for (let i = 0; i < children.length; i++) {
      const newChildren = visit(children[i], logger);
      // VEATTR nodes only have one children and are a direct replacement.
      if (children[i].tagName === 'VEATTR') {
        replaceChild(
            element, children[i], visit(children[i].children[0], logger));
      } else {
        replaceChild(element, children[i], newChildren);
      }
    }
  }
  if (logIndex === -1) {
    return [element];
  }
  logger.exit();
  if (metadata.elements[logIndex].logOnly) {
    return [];
  }
  let result = [element];
  if (element.tagName !== WRAPPER_ELEMENT_TAG_NAME) {
    element.removeAttribute(ELEMENT_ATTR);
  } else if (element.childNodes) {
    result = Array.from(element.childNodes);
  }
  if (pendingAttrs && result.length > 0) {
    pendingAttrs.applyToInternalOnly(result[0]);
  }
  return result;
}

/**
 * Replaces oldChild (a child of parent) with newChildren. If newChildren is
 * length zero, this removes oldChild. If newChildren contains only oldChild,
 * this does nothing.
 *
 * @param {!Node} parent
 * @param {!Node} oldChild
 * @param {!Array<!Element|!DocumentFragment>} newChildren
 */
function replaceChild(parent, oldChild, newChildren) {
  if (newChildren.length === 0) {
    parent.removeChild(oldChild);
  } else if (newChildren.length === 1) {
    if (oldChild !== newChildren[0]) {
      parent.replaceChild(newChildren[0], oldChild);
    }
  } else {
    for (const newChild of newChildren) {
      parent.insertBefore(newChild, oldChild);
    }
    parent.removeChild(oldChild);
  }
}

/**
 * Evaluates and replaces the data attributes related to logging functions.
 *
 * @param {!Element} element
 * @param {!Logger} logger
 */
function replaceFunctionAttributes(element, logger) {
  let /** !Array<string>|undefined */ newAttrs;
  // Iterates from the end to the beginning, since we are removing attributes
  // in place.
  let elementToAddTo = element;
  if (element.tagName === 'VEATTR') {
    // The attribute being replaced belongs on the direct child.
    elementToAddTo = /** @type {!Element} */ (element.firstElementChild);
  }
  const attrs = element.attributes;
  for (let i = attrs.length - 1; i >= 0; --i) {
    const attr = attrs[i];
    const attributeName = attr.name;
    if (startsWith(attributeName, FUNCTION_ATTR)) {
      // Delay evaluation of the attributes until we reach the element itself.
      if (elementToAddTo.hasAttribute(ELEMENT_ATTR) &&
          element.tagName === 'VEATTR') {
        elementToAddTo.setAttribute(
            attributeName, attr.value);
        continue;
      }
      const funcIndex = parseInt(attr.value, 10);
      assert(
          !Number.isNaN(funcIndex) && funcIndex < metadata.functions.length,
          'Invalid logging attribute.');
      const funcMetadata = metadata.functions[funcIndex];
      const actualAttrName = attributeName.substring(FUNCTION_ATTR.length);
      (newAttrs ??= [])
          .push(
              actualAttrName,
              logger.evalLoggingFunction(funcMetadata.name, funcMetadata.args));
      elementToAddTo.removeAttribute(attributeName);
    }
  }
  if (newAttrs) {
    for (let i = 0; i < newAttrs.length; i += 2) {
      const attrName = newAttrs[i];
      const attrValue = newAttrs[i + 1];
      elementToAddTo.setAttribute(attrName, attrValue);
    }
  }
}

/**
 * Gets and parses the data-soylog attribute for a given element. Returns -1 if
 * it does not contain related attributes.
 *
 * @param {!Element} element The current element.
 * @param {string} attr The name of the data attribute.
 * @return {number}
 */
function getDataAttribute(element, attr) {
  let logIndex = element.getAttribute(attr);
  if (logIndex) {
    logIndex = parseInt(logIndex, 10);
    assert(!Number.isNaN(logIndex), 'Invalid logging attribute.');
    return logIndex;
  }
  return -1;
}

/**
 * Logging interface for client side.
 * @interface
 */
class Logger {
  /**
   * Called when a `{velog}` statement is entered.
   * @param {!ElementMetadata} elementMetadata
   * @return {!LoggingAttrs|undefined}
   */
  enter(elementMetadata) {}

  /**
   * Called when a `{velog}` statement is exited.
   */
  exit() {}

  /**
   * Called in Incremental DOM when a Soy element is or root template is
   * about to be rerendered. This is meant to be used to implement INSERT_DEDUPE
   * grafting.
   * @param {!Element} el
   * @param {!Function} fn Used for rendering and needs to be called directly.
   */
  logGraft(el, fn) {}

  /**
   * Called when a logging function is evaluated.
   * @param {string} name function name, as obfuscated by the `xid` function.
   * @param {!Array<?>} args List of arguments needed for the function.
   * @return {string} The evaluated return value that will be shown in the DOM.
   */
  evalLoggingFunction(name, args) {}

  /**
   * Resets the logger so that subsequent logging calls will use the provided
   * builder (if set), or a new, separate VE tree builder.
   */
  resetBuilder() {}
}

/** The ID of the UndefinedVe. */
const UNDEFINED_VE_ID = -1;

/**
 * Function that takes two VisualElements and returns true if they have the
 * same id
 * @param {!$$VisualElement} ve1
 * @param {!$$VisualElement} ve2
 * @return {boolean}
 */
function $$veHasSameId(ve1, ve2) {
  return ve1.hasSameId(ve2);
}

/**
 * Soy's runtime representation of objects of the Soy `ve` type.
 *
 * <p>This is for use only in Soy internal code and Soy generated JS. DO NOT use
 * this from handwritten code.
 *
 * create instances for tests.
 *
 */
class $$VisualElement {
  /**
   * @param {number} id
   * @param {!ReadonlyLoggableElementMetadata|undefined} metadata
   * @param {string=} name
   */
  constructor(id, metadata, name = undefined) {
    /** @private @const {number} */
    this.id_ = id;
    /** @private @const {!ReadonlyLoggableElementMetadata} */
    this.metadata_ =
        metadata || ImmutableLoggableElementMetadata.getDefaultInstance();
    /** @private @const {string|undefined} */
    this.name_ = name;
  }

  /**
   * @param {!$$VisualElement} ve
   * @return {boolean}
   */
  hasSameId(ve) {
    return this.id_ === ve.getId();
  }

  /** @return {number} */
  getId() {
    return this.id_;
  }

  /** @return {!ReadonlyLoggableElementMetadata} */
  getMetadata() {
    return this.metadata_;
  }

  /** @package @return {string} */
  toDebugString() {
    return `ve(${this.name_})`;
  }

  /**
   * @override
   * @return {string}
   */
  toString() {
    if (goog.DEBUG) {
      return `**FOR DEBUGGING ONLY ${this.toDebugString()}, id: ${this.id_}**`;
    } else {
      return 'zSoyVez';
    }
  }

  /** @return {string} */
  getNameForDebugging() {
    return goog.DEBUG ? this.name_ || '' : '';
  }
}

/**
 * Soy's runtime representation of objects of the Soy `ve_data` type.
 *
 * <p>This is for use only in Soy internal code and Soy generated JS. DO NOT use
 * this from handwritten code.
 *
 * create instances for tests.
 *
 */
class $$VisualElementData {
  /**
   * @param {!$$VisualElement} ve
   * @param {?Message=} data
   */
  constructor(ve, data) {
    /** @private @const {!$$VisualElement} */
    this.ve_ = ve;
    /** @private @const {?Message|undefined} */
    this.data_ = data;
  }

  /** @return {!$$VisualElement} */
  getVe() {
    return this.ve_;
  }

  /** @return {?Message|undefined} */
  getData() {
    return this.data_;
  }

  /**
   * @override
   * @return {string}
   */
  toString() {
    if (goog.DEBUG) {
      return `**FOR DEBUGGING ONLY ve_data(${this.ve_.toDebugString()}${
          this.data_ ? ', ' + this.data_ : ''})**`;
    } else {
      return 'zSoyVeDz';
    }
  }
}

/**
 * @param {!$$VisualElement} ve
 * @return {!ReadonlyLoggableElementMetadata}
 */
function $$getMetadata(ve) {
  return ve.getMetadata();
}

/**
 * @param {!$$VisualElementData} veData
 * @return {!ReadonlyLoggableElementMetadata}
 */
function $$getVeMetadata(veData) {
  return $$getMetadata(veData.getVe());
}

/**
 * Returns a logger that does nothing.  Occasionally useful for testing.
 * @return {!Logger}
 */
function getNullLogger() {
  /** @implements {Logger} */
  class NullLogger {
    /**
     * @override
     * @param {!ElementMetadata} metaData
     */
    enter(metaData) {}

    /** @override */
    exit() {}

    /**
     * @override
     * @param {!Element} el
     * @param {!Function} fn
     * @return {void}
     */
    logGraft(el, fn) {
      fn();
    }
    /**
     * @override
     * @param {string} name
     * @param {!Array<?>} args
     * @return {string}
     */
    evalLoggingFunction(name, args) {
      return '';
    }
    /** @override */
    resetBuilder() {}
  }
  return new NullLogger();
}


exports = {
  $$hasMetadata,
  $$getLoggingAttribute,
  $$getLoggingFunctionAttribute,
  getLoggingAttributeEntry,
  WRAPPER_ELEMENT_TAG_NAME,
  ELEMENT_ATTR,
  FUNCTION_ATTR,
  ElementMetadata,
  FunctionMetadata,
  Logger,
  UNDEFINED_VE_ID,
  Metadata,
  $$VisualElement,
  $$VisualElementData,
  emitLoggingCommands,
  emitCommandsForRange,
  doLogEmit,
  setMetadataTestOnly,
  setUpLogging,
  tearDownLogging,
  $$getMetadata,
  $$getVeMetadata,
  $$veHasSameId,
  getNullLogger,
  LoggingAttrs,
};
