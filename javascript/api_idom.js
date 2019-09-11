/**
 * @fileoverview
 *
 * Functions necessary to interact with the Soy-Idom runtime.
 */
goog.module('google3.javascript.template.soy.api_idom');
var module = module || { id: 'javascript/template/soy/api_idom.js' };
var tslib_1 = goog.require('google3.third_party.javascript.tslib.tslib_closure');
var tsickle_module_1_ = goog.require('soy.velog'); // from //javascript/template/soy:soyutils_velog
var goog_soy_velog_1 = tsickle_module_1_; // from //javascript/template/soy:soyutils_velog
var incrementaldom = goog.require('google3.third_party.javascript.incremental_dom.index'); // from //third_party/javascript/incremental_dom:incrementaldom
var patchConfig = {
    matches: function (matchNode, nameOrCtor, expectedNameOrCtor, proposedKey, currentPointerKey) { return nameOrCtor === expectedNameOrCtor &&
        isMatchingKey(proposedKey, currentPointerKey); }
};
/** Token for skipping the element. This is returned in open calls. */
exports.SKIP_TOKEN = {};
/** PatchInner using Soy-IDOM semantics. */
exports.patchInner = incrementaldom.createPatchInner(patchConfig);
/** PatchOuter using Soy-IDOM semantics. */
exports.patchOuter = incrementaldom.createPatchOuter(patchConfig);
/** PatchInner using Soy-IDOM semantics. */
exports.patch = exports.patchInner;
/**
 * Class that mostly delegates to global Incremental DOM runtime. This will
 * eventually take in a logger and conditionally mute. These methods may
 * return void when idom commands are muted for velogging.
 */
var IncrementalDomRenderer = /** @class */ (function () {
    function IncrementalDomRenderer() {
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
        this.keyStackHolder = [];
        this.logger = null;
    }
    /**
     * Pushes/pops the given key from `keyStack` (versus `Array#concat`)
     * to avoid allocating a new array for every element open.
     */
    IncrementalDomRenderer.prototype.open = function (nameOrCtor, key) {
        if (key === void 0) { key = ''; }
        var el = incrementaldom.open(nameOrCtor, this.getNewKey(key));
        this.visit(el);
        return el;
    };
    /**
     * Open but for nodes that use {skip}. This uses a key that is serialized
     * at server-side rendering.
     * For more information, see go/typed-html-templates.
     */
    IncrementalDomRenderer.prototype.openSSR = function(nameOrCtor, key, data) {
      if (key === void 0) {
        key = '';
      }
      if (data === void 0) {
        data = null;
      }
      var el = incrementaldom.open(nameOrCtor, key);
      this.visit(el);
      if (goog.DEBUG) {
        this.attr('soy-server-key', key);
      }
      // Keep going since either elements are being created or continuing will
      // be a no-op.
      if (!el || !el.hasChildNodes()) {
        return true;
      }
      // Data is only passed by {skip} elements that are roots of templates.
      if (goog.DEBUG && data) {
        maybeReportErrors(el, data);
      }
      // Caveat: if the element has only attributes, we will skip regardless.
      this.skip();
      this.close();
      return false;
    };
    // For users extending IncrementalDomRenderer
    IncrementalDomRenderer.prototype.visit = function (el) { };
    IncrementalDomRenderer.prototype.alignWithDOM = function (tagName, key) {
        incrementaldom.alignWithDOM(tagName, key);
    };
    /**
     * Called on the return value of open. This is only true if it is exactly
     * the skip token. This has the side effect of performing the skip.
     */
    IncrementalDomRenderer.prototype.maybeSkip = function (renderer, val) {
        if (val === exports.SKIP_TOKEN) {
            renderer.skip();
            renderer.close();
            return true;
        }
        return false;
    };
    /**
     * Called (from generated template render function) before OPENING
     * keyed elements.
     */
    IncrementalDomRenderer.prototype.pushManualKey = function (key) {
        this.keyStackHolder.push(serializeKey(key));
    };
    /**
     * Called (from generated template render function) before CLOSING
     * keyed elements.
     */
    IncrementalDomRenderer.prototype.popManualKey = function () {
        this.keyStackHolder.pop();
    };
    /**
     * Called (from generated template render function) BEFORE template
     * calls.
     */
    IncrementalDomRenderer.prototype.pushKey = function (key) {
        var oldKey = this.getCurrentKeyStack();
        this.keyStackHolder[this.keyStackHolder.length - 1] = this.getNewKey(key);
        return oldKey;
    };
    IncrementalDomRenderer.prototype.getNewKey = function (key) {
        var oldKey = this.getCurrentKeyStack();
        var serializedKey = serializeKey(key);
        return serializedKey + oldKey;
    };
    /**
     * Called (from generated template render function) AFTER template
     * calls.
     */
    IncrementalDomRenderer.prototype.popKey = function (oldKey) {
        this.keyStackHolder[this.keyStackHolder.length - 1] = oldKey;
    };
    /**
     * Returns the stack on top of the holder. This represents the current
     * chain of keys.
     */
    IncrementalDomRenderer.prototype.getCurrentKeyStack = function () {
        return this.keyStackHolder[this.keyStackHolder.length - 1] || '';
    };
    IncrementalDomRenderer.prototype.close = function () {
        return incrementaldom.close();
    };
    IncrementalDomRenderer.prototype.text = function (value) {
        return incrementaldom.text(value);
    };
    IncrementalDomRenderer.prototype.attr = function (name, value) {
        incrementaldom.attr(name, value);
    };
    IncrementalDomRenderer.prototype.currentPointer = function () {
        return incrementaldom.currentPointer();
    };
    IncrementalDomRenderer.prototype.skip = function () {
        incrementaldom.skip();
    };
    IncrementalDomRenderer.prototype.currentElement = function () {
        return incrementaldom.currentElement();
    };
    IncrementalDomRenderer.prototype.skipNode = function () {
        incrementaldom.skipNode();
    };
    IncrementalDomRenderer.prototype.applyAttrs = function () {
        incrementaldom.applyAttrs();
    };
    IncrementalDomRenderer.prototype.applyStatics = function (statics) {
        incrementaldom.applyStatics(statics);
    };
    /**
     * Called when a `{velog}` statement is entered.
     */
    IncrementalDomRenderer.prototype.enter = function (veData, logOnly) {
        if (this.logger) {
            this.logger.enter(new goog_soy_velog_1.ElementMetadata(veData.getVe().getId(), veData.getData(), logOnly));
        }
    };
    /**
     * Called when a `{velog}` statement is exited.
     */
    IncrementalDomRenderer.prototype.exit = function () {
        if (this.logger) {
            this.logger.exit();
        }
    };
    IncrementalDomRenderer.prototype.toNullRenderer = function () {
        var nullRenderer = new NullRenderer(this);
        return nullRenderer;
    };
    IncrementalDomRenderer.prototype.toDefaultRenderer = function () {
        throw new Error('Cannot transition a default renderer to a default renderer');
    };
    /** Called by user code to configure logging */
    IncrementalDomRenderer.prototype.setLogger = function (logger) {
        this.logger = logger;
    };
    IncrementalDomRenderer.prototype.getLogger = function () {
        return this.logger;
    };
    /**
     * Used to trigger the requirement that logOnly can only be true when a
     * logger is configured. Otherwise, it is a passthrough function.
     */
    IncrementalDomRenderer.prototype.verifyLogOnly = function (logOnly) {
        if (!this.logger && logOnly) {
            throw new Error('Cannot set logonly="true" unless there is a logger configured');
        }
        return logOnly;
    };
    /*
     * Called when a logging function is evaluated.
     */
    IncrementalDomRenderer.prototype.evalLoggingFunction = function (name, args, placeHolder) {
        if (this.logger) {
            return this.logger.evalLoggingFunction(name, args);
        }
        return placeHolder;
    };
    return IncrementalDomRenderer;
}());
exports.IncrementalDomRenderer = IncrementalDomRenderer;
var NullRenderer = /** @class */ (function (_super) {
    tslib_1.__extends(NullRenderer, _super);
    function NullRenderer(renderer) {
        var _this = _super.call(this) || this;
        _this.renderer = renderer;
        _this.setLogger(renderer.getLogger());
        return _this;
    }
    NullRenderer.prototype.open = function (nameOrCtor, key) { };
    NullRenderer.prototype.openSSR = function(nameOrCtor, key) {
      return true;
    };
    NullRenderer.prototype.alignWithDOM = function (name, key) { };
    NullRenderer.prototype.close = function () { };
    NullRenderer.prototype.text = function (value) { };
    NullRenderer.prototype.attr = function (name, value) { };
    NullRenderer.prototype.currentPointer = function () {
        return null;
    };
    NullRenderer.prototype.applyAttrs = function () { };
    NullRenderer.prototype.applyStatics = function (statics) { };
    NullRenderer.prototype.skip = function () { };
    NullRenderer.prototype.key = function (val) { };
    NullRenderer.prototype.currentElement = function () { };
    NullRenderer.prototype.skipNode = function () { };
    /** Returns to the default renderer which will traverse the DOM. */
    NullRenderer.prototype.toDefaultRenderer = function () {
        this.renderer.setLogger(this.getLogger());
        return this.renderer;
    };
    return NullRenderer;
}(IncrementalDomRenderer));
exports.NullRenderer = NullRenderer;
/**
 * Provides a compact serialization format for the key structure.
 */
function serializeKey(item) {
    var stringified = String(item);
    var delimiter;
    if (item == null) {
        delimiter = '_';
    }
    else if (typeof item === 'number') {
        delimiter = '#';
    }
    else {
        delimiter = ':';
    }
    return "" + stringified.length + delimiter + stringified;
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
 */
function isMatchingKey(proposedKey, currentPointerKey) {
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
exports.isMatchingKey = isMatchingKey;
function maybeReportErrors(el, data) {
  var stringifiedParams = JSON.stringify(data, null, 2);
  if (!el.__lastParams) {
    el.__lastParams = stringifiedParams;
    return;
  }
  if (stringifiedParams !== el.__lastParams) {
    throw new Error(
        '\nTried to rerender a {skip} template with different parameters!\nMake sure that you never pass a parameter that can change to a template that has\n{skip}, since changes to that parameter won\'t affect the skipped content.\n\nOld parameters: ' +
        el.__lastParams + '\nNew parameters: ' + stringifiedParams +
        '\n\nElement:\n' + (el.dataset['debugSoy'] || el.outerHTML));
  }
}
//#