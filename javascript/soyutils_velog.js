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

const LoggableElementMetadata = goog.require('proto.soy.LoggableElementMetadata');
const Message = goog.require('jspb.Message');
const {assert} = goog.require('goog.asserts');
const {startsWith} = goog.require('goog.string');


/** @final */
class ElementMetadata {
  /**
   * @param {number} id
   * @param {?Message} data
   * @param {boolean} logOnly
   */
  constructor(id, data, logOnly) {
    /**
     * The identifier for the logging element
     * @const {number}
     */
    this.id = id;

    /**
     * The optional payload from the `data` attribute. This is guaranteed to
     * match the proto_type specified in the logging configuration.
     * @const {?Message}
     */
    this.data = data;

    /**
     * Whether or not this element is in logOnly mode. In logOnly mode the log
     * records are collected but the actual elements are not rendered.
     * @const {boolean}
     */
    this.logOnly = logOnly;
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

/** Sets up the global metadata object before rendering any templates. */
function setUpLogging() {
  assert(
      !$$hasMetadata(),
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
      $$hasMetadata(),
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
  if ($$hasMetadata()) {
    const dataIdx = metadata.elements.push(new ElementMetadata(
                        veData.getVe().getId(), veData.getData(), logOnly)) -
        1;
    // Insert a whitespace at the beginning. In VeLogInstrumentationVisitor,
    // we insert the return value of this method as a plain string instead of a
    // HTML attribute, therefore the desugaring pass does not know how to handle
    // whitespaces.
    // Trailing whitespace is not needed since we always add this at the end of
    // a HTML tag.
    return ' ' + ELEMENT_ATTR + '="' + dataIdx + '"';
  } else if (logOnly) {
    // If logonly is true but no longger has been configured, we throw an error
    // since this is clearly a misconfiguration.
    throw new Error(
        'Cannot set logonly="true" unless there is a logger configured');
  } else {
    // If logger has not been configured, return an empty string to avoid adding
    // unnecessary information in the DOM.
    return '';
  }
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
  if ($$hasMetadata()) {
    const functionIdx =
        metadata.functions.push(new FunctionMetadata(name, args)) - 1;
    return ' ' + FUNCTION_ATTR + attr + '="' + functionIdx + '"';
  } else {
    return '';
  }
}

/**
 * For a given rendered HTML element, go through the DOM tree and emits logging
 * commands if necessary. This method also discards visual elements that are'
 * marked as log only (counterfactual).
 *
 * @param {!Element|!DocumentFragment} element The rendered HTML element.
 * @param {!Logger} logger The logger that actually does stuffs.
 * @return {!Element|!DocumentFragment}
 */
function emitLoggingCommands(element, logger) {
  if (element instanceof Element) {
    const newElements = visit(element, logger);
    if (element.parentNode !== null) {
      replaceChild(element.parentNode, element, newElements);
    }
    if (newElements.length === 1) {
      return newElements[0];
    }
    const fragment = document.createDocumentFragment();
    for (const child of newElements) {
      fragment.appendChild(child);
    }
    return fragment;
  } else {
    const children = Array.from(element.childNodes);
    for (let i = 0; i < children.length; i++) {
      const child = children[i];
      if (child instanceof Element) {
        const newChildren = visit(child, logger);
        replaceChild(element, child, newChildren);
      }
    }
    return element;
  }
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
  let logIndex = -1;
  if (!(element instanceof Element)) {
    return [element];
  }
  if (element.hasAttribute(ELEMENT_ATTR)) {
    logIndex = getDataAttribute(element, ELEMENT_ATTR);
    assert(metadata.elements.length > logIndex, 'Invalid logging attribute.');
    if (logIndex != -1) {
      logger.enter(metadata.elements[logIndex]);
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
  if (element.tagName !== 'VELOG') {
    element.removeAttribute(ELEMENT_ATTR);
    return [element];
  } else if (element.childNodes) {
    return Array.from(element.childNodes);
  }
  return [element];
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
  const attributeMap = {};
  // Iterates from the end to the beginning, since we are removing attributes
  // in place.
  let elementWithAttribute = element;
  if (element.tagName === 'VEATTR') {
    // The attribute being replaced belongs on the direct child.
    elementWithAttribute = element.firstElementChild;
  }
  for (let i = element.attributes.length - 1; i >= 0; --i) {
    const attributeName = element.attributes[i].name;
    if (startsWith(attributeName, FUNCTION_ATTR)) {
      // Delay evaluation of the attributes until we reach the element itself.
      if (elementWithAttribute.hasAttribute(ELEMENT_ATTR) &&
          element.tagName === 'VEATTR') {
        elementWithAttribute.setAttribute(
            attributeName, element.attributes[i].value);
        continue;
      }
      const funcIndex = parseInt(element.attributes[i].value, 10);
      assert(
          !Number.isNaN(funcIndex) && funcIndex < metadata.functions.length,
          'Invalid logging attribute.');
      const funcMetadata = metadata.functions[funcIndex];
      const attr = attributeName.substring(FUNCTION_ATTR.length);
      attributeMap[attr] =
          logger.evalLoggingFunction(funcMetadata.name, funcMetadata.args);
      elementWithAttribute.removeAttribute(attributeName);
    }
  }
  for (const attributeName in attributeMap) {
    elementWithAttribute.setAttribute(
        attributeName, attributeMap[attributeName]);
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

class $$VisualElement {
  /**
   * @param {number} id
   * @param {!LoggableElementMetadata|undefined} metadata
   * @param {string=} name
   */
  constructor(id, metadata, name = undefined) {
    /** @private @const {number} */
    this.id_ = id;
    /** @private @const {!LoggableElementMetadata|undefined} */
    this.metadata_ = metadata;
    /** @private @const {string|undefined} */
    this.name_ = name;
  }

  /** @return {number} */
  getId() {
    return this.id_;
  }

  /** @return {!LoggableElementMetadata} */
  getMetadata() {
    return this.metadata_ === undefined ? new LoggableElementMetadata() :
                                          this.metadata_;
  }

  /** @package @return {string} */
  toDebugString() {
    return `ve(${this.name_})`;
  }

  /** @override */
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

class $$VisualElementData {
  /**
   * @param {!$$VisualElement} ve
   * @param {?Message} data
   */
  constructor(ve, data) {
    /** @private @const {!$$VisualElement} */
    this.ve_ = ve;
    /** @private @const {?Message} */
    this.data_ = data;
  }

  /** @return {!$$VisualElement} */
  getVe() {
    return this.ve_;
  }

  /** @return {?Message} */
  getData() {
    return this.data_;
  }

  /** @override */
  toString() {
    if (goog.DEBUG) {
      return `**FOR DEBUGGING ONLY ve_data(${this.ve_.toDebugString()}, ${
          this.data_})**`;
    } else {
      return 'zSoyVeDz';
    }
  }
}

/**
 * @param {!$$VisualElement} ve
 * @return {!LoggableElementMetadata}
 */
function $$getMetadata(ve) {
  return ve.getMetadata();
}

/**
 * @param {!$$VisualElementData} veData
 * @return {!LoggableElementMetadata}
 */
function $$getVeMetadata(veData) {
  return $$getMetadata(veData.getVe());
}

exports = {
  $$hasMetadata,
  $$getLoggingAttribute,
  $$getLoggingFunctionAttribute,
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
  setMetadataTestOnly,
  setUpLogging,
  tearDownLogging,
  $$getMetadata,
  $$getVeMetadata,
};
