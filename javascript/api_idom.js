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
    return proposedKey === currentPointerKey;
}
exports.isMatchingKey = isMatchingKey;
//# sourceMappingURL=data:application/json;base64,eyJ2ZXJzaW9uIjozLCJmaWxlIjoiYXBpX2lkb20uanMiLCJzb3VyY2VSb290IjoiIiwic291cmNlcyI6WyIuLi8uLi8uLi8uLi8uLi8uLi9qYXZhc2NyaXB0L3RlbXBsYXRlL3NveS9hcGlfaWRvbS50cyJdLCJuYW1lcyI6W10sIm1hcHBpbmdzIjoiQUFBQTs7OztHQUlHOzs7O0FBRUgsa0RBQXdCLENBQUMsZ0RBQWdEO0FBR3pFLHlDQUE0RSxDQUFFLGdEQUFnRDtBQUM5SCwwRkFBaUQsQ0FBRSwrREFBK0Q7QUFFbEgsSUFBTSxXQUFXLEdBQStCO0lBQzlDLE9BQU8sRUFDSCxVQUFDLFNBQVMsRUFBRSxVQUFVLEVBQUUsa0JBQWtCLEVBQUUsV0FBVyxFQUN0RCxpQkFBaUIsSUFBSyxPQUFBLFVBQVUsS0FBSyxrQkFBa0I7UUFDeEQsYUFBYSxDQUFDLFdBQVcsRUFBRSxpQkFBaUIsQ0FBQyxFQUR0QixDQUNzQjtDQUNsRCxDQUFDO0FBRUYsc0VBQXNFO0FBQ3pELFFBQUEsVUFBVSxHQUFHLEVBQUUsQ0FBQztBQUU3QiwyQ0FBMkM7QUFDOUIsUUFBQSxVQUFVLEdBQUcsY0FBYyxDQUFDLGdCQUFnQixDQUFDLFdBQVcsQ0FBQyxDQUFDO0FBQ3ZFLDJDQUEyQztBQUM5QixRQUFBLFVBQVUsR0FBRyxjQUFjLENBQUMsZ0JBQWdCLENBQUMsV0FBVyxDQUFDLENBQUM7QUFDdkUsMkNBQTJDO0FBQzlCLFFBQUEsS0FBSyxHQUFHLGtCQUFVLENBQUM7QUFPaEM7Ozs7R0FJRztBQUNIO0lBQUE7UUFDRSw4RUFBOEU7UUFDOUUsbUVBQW1FO1FBQ25FLDhEQUE4RDtRQUM5RCxvQkFBb0I7UUFDcEIsMkVBQTJFO1FBQzNFLCtCQUErQjtRQUMvQiwwRUFBMEU7UUFDMUUsd0VBQXdFO1FBQ3hFLHlDQUF5QztRQUN6Qyx5RUFBeUU7UUFDekUsc0RBQXNEO1FBQ3JDLG1CQUFjLEdBQWEsRUFBRSxDQUFDO1FBQ3ZDLFdBQU0sR0FBZ0IsSUFBSSxDQUFDO0lBcUxyQyxDQUFDO0lBbkxDOzs7T0FHRztJQUNILHFDQUFJLEdB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QixDQUFDO0FBQzNDLENBQUM7QUFWRCxzQ0FVQyIsInNvdXJjZXNDb250ZW50IjpbIi8qKlxuICogQGZpbGVvdmVydmlld1xuICpcbiAqIEZ1bmN0aW9ucyBuZWNlc3NhcnkgdG8gaW50ZXJhY3Qgd2l0aCB0aGUgU295LUlkb20gcnVudGltZS5cbiAqL1xuXG5pbXBvcnQgJ2dvb2c6c295LnZlbG9nJzsgLy8gZnJvbSAvL2phdmFzY3JpcHQvdGVtcGxhdGUvc295OnNveXV0aWxzX3ZlbG9nXG5cbmltcG9ydCAqIGFzIGdvb2dTb3kgZnJvbSAnZ29vZzpnb29nLnNveSc7ICAvLyBmcm9tIC8vamF2YXNjcmlwdC9jbG9zdXJlL3NveVxuaW1wb3J0IHskJFZpc3VhbEVsZW1lbnREYXRhLCBFbGVtZW50TWV0YWRhdGEsIExvZ2dlcn0gZnJvbSAnZ29vZzpzb3kudmVsb2cnOyAgLy8gZnJvbSAvL2phdmFzY3JpcHQvdGVtcGxhdGUvc295OnNveXV0aWxzX3ZlbG9nXG5pbXBvcnQgKiBhcyBpbmNyZW1lbnRhbGRvbSBmcm9tICdpbmNyZW1lbnRhbGRvbSc7ICAvLyBmcm9tIC8vdGhpcmRfcGFydHkvamF2YXNjcmlwdC9pbmNyZW1lbnRhbF9kb206aW5jcmVtZW50YWxkb21cblxuY29uc3QgcGF0Y2hDb25maWc6IGluY3JlbWVudGFsZG9tLlBhdGNoQ29uZmlnID0ge1xuICBtYXRjaGVzOlxuICAgICAgKG1hdGNoTm9kZSwgbmFtZU9yQ3RvciwgZXhwZWN0ZWROYW1lT3JDdG9yLCBwcm9wb3NlZEtleSxcbiAgICAgICBjdXJyZW50UG9pbnRlcktleSkgPT4gbmFtZU9yQ3RvciA9PT0gZXhwZWN0ZWROYW1lT3JDdG9yICYmXG4gICAgICBpc01hdGNoaW5nS2V5KHByb3Bvc2VkS2V5LCBjdXJyZW50UG9pbnRlcktleSlcbn07XG5cbi8qKiBUb2tlbiBmb3Igc2tpcHBpbmcgdGhlIGVsZW1lbnQuIFRoaXMgaXMgcmV0dXJuZWQgaW4gb3BlbiBjYWxscy4gKi9cbmV4cG9ydCBjb25zdCBTS0lQX1RPS0VOID0ge307XG5cbi8qKiBQYXRjaElubmVyIHVzaW5nIFNveS1JRE9NIHNlbWFudGljcy4gKi9cbmV4cG9ydCBjb25zdCBwYXRjaElubmVyID0gaW5jcmVtZW50YWxkb20uY3JlYXRlUGF0Y2hJbm5lcihwYXRjaENvbmZpZyk7XG4vKiogUGF0Y2hPdXRlciB1c2luZyBTb3ktSURPTSBzZW1hbnRpY3MuICovXG5leHBvcnQgY29uc3QgcGF0Y2hPdXRlciA9IGluY3JlbWVudGFsZG9tLmNyZWF0ZVBhdGNoT3V0ZXIocGF0Y2hDb25maWcpO1xuLyoqIFBhdGNoSW5uZXIgdXNpbmcgU295LUlET00gc2VtYW50aWNzLiAqL1xuZXhwb3J0IGNvbnN0IHBhdGNoID0gcGF0Y2hJbm5lcjtcblxuLyoqIFR5cGUgZm9yIEhUTUwgdGVtcGxhdGVzICovXG5leHBvcnQgdHlwZSBUZW1wbGF0ZTxUPiA9XG4gICAgLy8gdHNsaW50OmRpc2FibGUtbmV4dC1saW5lOm5vLWFueVxuICAgIChyZW5kZXJlcjogSW5jcmVtZW50YWxEb21SZW5kZXJlciwgYXJnczogVCwgaWpEYXRhPzogZ29vZ1NveS5JakRhdGEpID0+IGFueTtcblxuLyoqXG4gKiBDbGFzcyB0aGF0IG1vc3RseSBkZWxlZ2F0ZXMgdG8gZ2xvYmFsIEluY3JlbWVudGFsIERPTSBydW50aW1lLiBUaGlzIHdpbGxcbiAqIGV2ZW50dWFsbHkgdGFrZSBpbiBhIGxvZ2dlciBhbmQgY29uZGl0aW9uYWxseSBtdXRlLiBUaGVzZSBtZXRob2RzIG1heVxuICogcmV0dXJuIHZvaWQgd2hlbiBpZG9tIGNvbW1hbmRzIGFyZSBtdXRlZCBmb3IgdmVsb2dnaW5nLlxuICovXG5leHBvcnQgY2xhc3MgSW5jcmVtZW50YWxEb21SZW5kZXJlciB7XG4gIC8vIFN0YWNrIChob2xkZXIpIG9mIGtleSBzdGFja3MgZm9yIHRoZSBjdXJyZW50IHRlbXBsYXRlIGJlaW5nIHJlbmRlcmVkLCB3aGljaFxuICAvLyBoYXMgY29udGV4dCBvbiB3aGVyZSB0aGUgdGVtcGxhdGUgd2FzIGNhbGxlZCBmcm9tIGFuZCBpcyB1c2VkIHRvXG4gIC8vIGtleSBlYWNoIHRlbXBsYXRlIGNhbGwgKHNlZSBnby9zb3ktaWRvbS1kaWZmaW5nLXNlbWFudGljcykuXG4gIC8vIFdvcmtzIGFzIGZvbGxvd3M6XG4gIC8vIC0gQSBuZXcga2V5IGlzIHB1c2hlZCBvbnRvIHRoZSB0b3Btb3N0IGtleSBzdGFjayBiZWZvcmUgYSB0ZW1wbGF0ZSBjYWxsLFxuICAvLyAtIGFuZCBwb3BwZWQgYWZ0ZXIgdGhlIGNhbGwuXG4gIC8vIC0gQSBuZXcgc3RhY2sgaXMgcHVzaGVkIG9udG8gdGhlIGhvbGRlciBiZWZvcmUgYSBtYW51YWxseSBrZXllZCBlbGVtZW50XG4gIC8vICAgaXMgb3BlbmVkLCBhbmQgcG9wcGVkIGJlZm9yZSB0aGUgZWxlbWVudCBpcyBjbG9zZWQuIFRoaXMgaXMgYmVjYXVzZVxuICAvLyAgIG1hbnVhbCBrZXlzIFwicmVzZXRcIiB0aGUga2V5IGNvbnRleHQuXG4gIC8vIE5vdGUgdGhhdCBmb3IgcGVyZm9ybWFuY2UsIHRoZSBcInN0YWNrXCIgaXMgaW1wbGVtZW50ZWQgYXMgYSBzdHJpbmcgd2l0aFxuICAvLyB0aGUgaXRlbXMgYmVpbmcgYCR7U0laRSBPRiBLRVl9JHtERUxJTUlURVJ9JHtLRVl9YC5cbiAgcHJpdmF0ZSByZWFkb25seSBrZXlTdGFja0hvbGRlcjogc3RyaW5nW10gPSBbXTtcbiAgcHJpdmF0ZSBsb2dnZXI6IExvZ2dlcnxudWxsID0gbnVsbDtcblxuICAvKipcbiAgICogUHVzaGVzL3BvcHMgdGhlIGdpdmVuIGtleSBmcm9tIGBrZXlTdGFja2AgKHZlcnN1cyBgQXJyYXkjY29uY2F0YClcbiAgICogdG8gYXZvaWQgYWxsb2NhdGluZyBhIG5ldyBhcnJheSBmb3IgZXZlcnkgZWxlbWVudCBvcGVuLlxuICAgKi9cbiAgb3BlbihuYW1lT3JDdG9yOiBzdHJpbmcsIGtleSA9ICcnKTogSFRNTEVsZW1lbnR8dm9pZCB7XG4gICAgY29uc3QgZWwgPSBpbmNyZW1lbnRhbGRvbS5vcGVuKG5hbWVPckN0b3IsIHRoaXMuZ2V0TmV3S2V5KGtleSkpO1xuICAgIHRoaXMudmlzaXQoZWwpO1xuICAgIHJldHVybiBlbDtcbiAgfVxuXG4gIC8vIEZvciB1c2VycyBleHRlbmRpbmcgSW5jcmVtZW50YWxEb21SZW5kZXJlclxuICB2aXNpdChlbDogSFRNTEVsZW1lbnR8dm9pZCkge31cblxuICBhbGlnbldpdGhET00odGFnTmFtZTogc3RyaW5nLCBrZXk6IHN0cmluZykge1xuICAgIGluY3JlbWVudGFsZG9tLmFsaWduV2l0aERPTSh0YWdOYW1lLCBrZXkpO1xuICB9XG5cbiAgLyoqXG4gICAqIENhbGxlZCBvbiB0aGUgcmV0dXJuIHZhbHVlIG9mIG9wZW4uIFRoaXMgaXMgb25seSB0cnVlIGlmIGl0IGlzIGV4YWN0bHlcbiAgICogdGhlIHNraXAgdG9rZW4uIFRoaXMgaGFzIHRoZSBzaWRlIGVmZmVjdCBvZiBwZXJmb3JtaW5nIHRoZSBza2lwLlxuICAgKi9cbiAgbWF5YmVTa2lwKHJlbmRlcmVyOiBJbmNyZW1lbnRhbERvbVJlbmRlcmVyLCB2YWw6IHVua25vd24pIHtcbiAgICBpZiAodmFsID09PSBTS0lQX1RPS0VOKSB7XG4gICAgICByZW5kZXJlci5za2lwKCk7XG4gICAgICByZW5kZXJlci5jbG9zZSgpO1xuICAgICAgcmV0dXJuIHRydWU7XG4gICAgfVxuICAgIHJldHVybiBmYWxzZTtcbiAgfVxuXG4gIC8qKlxuICAgKiBDYWxsZWQgKGZyb20gZ2VuZXJhdGVkIHRlbXBsYXRlIHJlbmRlciBmdW5jdGlvbikgYmVmb3JlIE9QRU5JTkdcbiAgICoga2V5ZWQgZWxlbWVudHMuXG4gICAqL1xuICBwdXNoTWFudWFsS2V5KGtleTogaW5jcmVtZW50YWxkb20uS2V5KSB7XG4gICAgdGhpcy5rZXlTdGFja0hvbGRlci5wdXNoKHNlcmlhbGl6ZUtleShrZXkpKTtcbiAgfVxuXG4gIC8qKlxuICAgKiBDYWxsZWQgKGZyb20gZ2VuZXJhdGVkIHRlbXBsYXRlIHJlbmRlciBmdW5jdGlvbikgYmVmb3JlIENMT1NJTkdcbiAgICoga2V5ZWQgZWxlbWVudHMuXG4gICAqL1xuICBwb3BNYW51YWxLZXkoKSB7XG4gICAgdGhpcy5rZXlTdGFja0hvbGRlci5wb3AoKTtcbiAgfVxuXG4gIC8qKlxuICAgKiBDYWxsZWQgKGZyb20gZ2VuZXJhdGVkIHRlbXBsYXRlIHJlbmRlciBmdW5jdGlvbikgQkVGT1JFIHRlbXBsYXRlXG4gICAqIGNhbGxzLlxuICAgKi9cbiAgcHVzaEtleShrZXk6IHN0cmluZykge1xuICAgIGNvbnN0IG9sZEtleSA9IHRoaXMuZ2V0Q3VycmVudEtleVN0YWNrKCk7XG4gICAgdGhpcy5rZXlTdGFja0hvbGRlclt0aGlzLmtleVN0YWNrSG9sZGVyLmxlbmd0aCAtIDFdID0gdGhpcy5nZXROZXdLZXkoa2V5KTtcbiAgICByZXR1cm4gb2xkS2V5O1xuICB9XG5cbiAgZ2V0TmV3S2V5KGtleTogc3RyaW5nKSB7XG4gICAgY29uc3Qgb2xkS2V5ID0gdGhpcy5nZXRDdXJyZW50S2V5U3RhY2soKTtcbiAgICBjb25zdCBzZXJpYWxpemVkS2V5ID0gc2VyaWFsaXplS2V5KGtleSk7XG4gICAgcmV0dXJuIHNlcmlhbGl6ZWRLZXkgKyBvbGRLZXk7XG4gIH1cblxuICAvKipcbiAgICogQ2FsbGVkIChmcm9tIGdlbmVyYXRlZCB0ZW1wbGF0ZSByZW5kZXIgZnVuY3Rpb24pIEFGVEVSIHRlbXBsYXRlXG4gICAqIGNhbGxzLlxuICAgKi9cbiAgcG9wS2V5KG9sZEtleTogc3RyaW5nKSB7XG4gICAgdGhpcy5rZXlTdGFja0hvbGRlclt0aGlzLmtleVN0YWNrSG9sZGVyLmxlbmd0aCAtIDFdID0gb2xkS2V5O1xuICB9XG5cbiAgLyoqXG4gICAqIFJldHVybnMgdGhlIHN0YWNrIG9uIHRvcCBvZiB0aGUgaG9sZGVyLiBUaGlzIHJlcHJlc2VudHMgdGhlIGN1cnJlbnRcbiAgICogY2hhaW4gb2Yga2V5cy5cbiAgICovXG4gIGdldEN1cnJlbnRLZXlTdGFjaygpOiBzdHJpbmcge1xuICAgIHJldHVybiB0aGlzLmtleVN0YWNrSG9sZGVyW3RoaXMua2V5U3RhY2tIb2xkZXIubGVuZ3RoIC0gMV0gfHwgJyc7XG4gIH1cblxuICBjbG9zZSgpOiBFbGVtZW50fHZvaWQge1xuICAgIHJldHVybiBpbmNyZW1lbnRhbGRvbS5jbG9zZSgpO1xuICB9XG5cbiAgdGV4dCh2YWx1ZTogc3RyaW5nKTogVGV4dHx2b2lkIHtcbiAgICByZXR1cm4gaW5jcmVtZW50YWxkb20udGV4dCh2YWx1ZSk7XG4gIH1cblxuICBhdHRyKG5hbWU6IHN0cmluZywgdmFsdWU6IHN0cmluZykge1xuICAgIGluY3JlbWVudGFsZG9tLmF0dHIobmFtZSwgdmFsdWUpO1xuICB9XG5cbiAgY3VycmVudFBvaW50ZXIoKTogTm9kZXxudWxsIHtcbiAgICByZXR1cm4gaW5jcmVtZW50YWxkb20uY3VycmVudFBvaW50ZXIoKTtcbiAgfVxuXG4gIHNraXAoKSB7XG4gICAgaW5jcmVtZW50YWxkb20uc2tpcCgpO1xuICB9XG5cbiAgY3VycmVudEVsZW1lbnQoKTogRWxlbWVudHx2b2lkIHtcbiAgICByZXR1cm4gaW5jcmVtZW50YWxkb20uY3VycmVudEVsZW1lbnQoKTtcbiAgfVxuXG4gIHNraXBOb2RlKCkge1xuICAgIGluY3JlbWVudGFsZG9tLnNraXBOb2RlKCk7XG4gIH1cblxuICBhcHBseUF0dHJzKCkge1xuICAgIGluY3JlbWVudGFsZG9tLmFwcGx5QXR0cnMoKTtcbiAgfVxuXG4gIGFwcGx5U3RhdGljcyhzdGF0aWNzOiBpbmNyZW1lbnRhbGRvbS5TdGF0aWNzKSB7XG4gICAgaW5jcmVtZW50YWxkb20uYXBwbHlTdGF0aWNzKHN0YXRpY3MpO1xuICB9XG5cbiAgLyoqXG4gICAqIENhbGxlZCB3aGVuIGEgYHt2ZWxvZ31gIHN0YXRlbWVudCBpcyBlbnRlcmVkLlxuICAgKi9cbiAgZW50ZXIodmVEYXRhOiAkJFZpc3VhbEVsZW1lbnREYXRhLCBsb2dPbmx5OiBib29sZWFuKSB7XG4gICAgaWYgKHRoaXMubG9nZ2VyKSB7XG4gICAgICB0aGlzLmxvZ2dlci5lbnRlcihuZXcgRWxlbWVudE1ldGFkYXRhKFxuICAgICAgICAgIHZlRGF0YS5nZXRWZSgpLmdldElkKCksIHZlRGF0YS5nZXREYXRhKCksIGxvZ09ubHkpKTtcbiAgICB9XG4gIH1cblxuICAvKipcbiAgICogQ2FsbGVkIHdoZW4gYSBge3ZlbG9nfWAgc3RhdGVtZW50IGlzIGV4aXRlZC5cbiAgICovXG4gIGV4aXQoKSB7XG4gICAgaWYgKHRoaXMubG9nZ2VyKSB7XG4gICAgICB0aGlzLmxvZ2dlci5leGl0KCk7XG4gICAgfVxuICB9XG5cbiAgLyoqXG4gICAqIFN3aXRjaGVzIHJ1bnRpbWUgdG8gcHJvZHVjZSBpbmNyZW1lbnRhbCBkb20gY2FsbHMgdGhhdCBkbyBub3QgdHJhdmVyc2VcbiAgICogdGhlIERPTS4gVGhpcyBoYXBwZW5zIHdoZW4gbG9nT25seSBpbiBhIHZlbG9nZ2luZyBub2RlIGlzIHNldCB0byB0cnVlLlxuICAgKiBGb3IgbW9yZSBpbmZvLCBzZWUgaHR0cDovL2dvL3NveS9yZWZlcmVuY2UvdmVsb2cjdGhlLWxvZ29ubHktYXR0cmlidXRlXG4gICAqL1xuICB0b051bGxSZW5kZXJlcigpIHtcbiAgICBjb25zdCBudWxsUmVuZGVyZXIgPSBuZXcgTnVsbFJlbmRlcmVyKHRoaXMpO1xuICAgIHJldHVybiBudWxsUmVuZGVyZXI7XG4gIH1cblxuICB0b0RlZmF1bHRSZW5kZXJlcigpOiBJbmNyZW1lbnRhbERvbVJlbmRlcmVyIHtcbiAgICB0aHJvdyBuZXcgRXJyb3IoXG4gICAgICAgICdDYW5ub3QgdHJhbnNpdGlvbiBhIGRlZmF1bHQgcmVuZGVyZXIgdG8gYSBkZWZhdWx0IHJlbmRlcmVyJyk7XG4gIH1cblxuICAvKiogQ2FsbGVkIGJ5IHVzZXIgY29kZSB0byBjb25maWd1cmUgbG9nZ2luZyAqL1xuICBzZXRMb2dnZXIobG9nZ2VyOiBMb2dnZXJ8bnVsbCkge1xuICAgIHRoaXMubG9nZ2VyID0gbG9nZ2VyO1xuICB9XG5cbiAgZ2V0TG9nZ2VyKCkge1xuICAgIHJldHVybiB0aGlzLmxvZ2dlcjtcbiAgfVxuXG4gIC8qKlxuICAgKiBVc2VkIHRvIHRyaWdnZXIgdGhlIHJlcXVpcmVtZW50IHRoYXQgbG9nT25seSBjYW4gb25seSBiZSB0cnVlIHdoZW4gYVxuICAgKiBsb2dnZXIgaXMgY29uZmlndXJlZC4gT3RoZXJ3aXNlLCBpdCBpcyBhIHBhc3N0aHJvdWdoIGZ1bmN0aW9uLlxuICAgKi9cbiAgdmVyaWZ5TG9nT25seShsb2dPbmx5OiBib29sZWFuKSB7XG4gICAgaWYgKCF0aGlzLmxvZ2dlciAmJiBsb2dPbmx5KSB7XG4gICAgICB0aHJvdyBuZXcgRXJyb3IoXG4gICAgICAgICAgJ0Nhbm5vdCBzZXQgbG9nb25seT1cInRydWVcIiB1bmxlc3MgdGhlcmUgaXMgYSBsb2dnZXIgY29uZmlndXJlZCcpO1xuICAgIH1cbiAgICByZXR1cm4gbG9nT25seTtcbiAgfVxuXG4gIC8qXG4gICAqIENhbGxlZCB3aGVuIGEgbG9nZ2luZyBmdW5jdGlvbiBpcyBldmFsdWF0ZWQuXG4gICAqL1xuICBldmFsTG9nZ2luZ0Z1bmN0aW9uKG5hbWU6IHN0cmluZywgYXJnczogQXJyYXk8e30+LCBwbGFjZUhvbGRlcjogc3RyaW5nKTpcbiAgICAgIHN0cmluZyB7XG4gICAgaWYgKHRoaXMubG9nZ2VyKSB7XG4gICAgICByZXR1cm4gdGhpcy5sb2dnZXIuZXZhbExvZ2dpbmdGdW5jdGlvbihuYW1lLCBhcmdzKTtcbiAgICB9XG4gICAgcmV0dXJuIHBsYWNlSG9sZGVyO1xuICB9XG59XG5cbi8qKlxuICogUmVuZGVyZXIgdGhhdCBtdXRlcyBhbGwgSURPTSBjb21tYW5kcyBhbmQgcmV0dXJucyB2b2lkLlxuICogRm9yIG1vcmUgaW5mbywgc2VlIGh0dHA6Ly9nby9zb3kvcmVmZXJlbmNlL3ZlbG9nI3RoZS1sb2dvbmx5LWF0dHJpYnV0ZVxuICovXG5leHBvcnQgY2xhc3MgTnVsbFJlbmRlcmVyIGV4dGVuZHMgSW5jcmVtZW50YWxEb21SZW5kZXJlciB7XG4gIGNvbnN0cnVjdG9yKHByaXZhdGUgcmVhZG9ubHkgcmVuZGVyZXI6IEluY3JlbWVudGFsRG9tUmVuZGVyZXIpIHtcbiAgICBzdXBlcigpO1xuICAgIHRoaXMuc2V0TG9nZ2VyKHJlbmRlcmVyLmdldExvZ2dlcigpKTtcbiAgfVxuXG4gIG9wZW4obmFtZU9yQ3Rvcjogc3RyaW5nLCBrZXk/OiBzdHJpbmcpIHt9XG5cbiAgYWxpZ25XaXRoRE9NKG5hbWU6IHN0cmluZywga2V5OiBzdHJpbmcpIHt9XG5cbiAgY2xvc2UoKSB7fVxuXG4gIHRleHQodmFsdWU6IHN0cmluZykge31cblxuICBhdHRyKG5hbWU6IHN0cmluZywgdmFsdWU6IHN0cmluZykge31cblxuICBjdXJyZW50UG9pbnRlcigpIHtcbiAgICByZXR1cm4gbnVsbDtcbiAgfVxuXG4gIGFwcGx5QXR0cnMoKSB7fVxuXG4gIGFwcGx5U3RhdGljcyhzdGF0aWNzOiBpbmNyZW1lbnRhbGRvbS5TdGF0aWNzKSB7fVxuXG4gIHNraXAoKSB7fVxuXG4gIGtleSh2YWw6IHN0cmluZykge31cblxuICBjdXJyZW50RWxlbWVudCgpIHt9XG5cbiAgc2tpcE5vZGUoKSB7fVxuXG4gIC8qKiBSZXR1cm5zIHRvIHRoZSBkZWZhdWx0IHJlbmRlcmVyIHdoaWNoIHdpbGwgdHJhdmVyc2UgdGhlIERPTS4gKi9cbiAgdG9EZWZhdWx0UmVuZGVyZXIoKSB7XG4gICAgdGhpcy5yZW5kZXJlciEuc2V0TG9nZ2VyKHRoaXMuZ2V0TG9nZ2VyKCkpO1xuICAgIHJldHVybiB0aGlzLnJlbmRlcmVyO1xuICB9XG59XG5cbi8qKlxuICogUHJvdmlkZXMgYSBjb21wYWN0IHNlcmlhbGl6YXRpb24gZm9ybWF0IGZvciB0aGUga2V5IHN0cnVjdHVyZS5cbiAqL1xuZXhwb3J0IGZ1bmN0aW9uIHNlcmlhbGl6ZUtleShpdGVtOiBzdHJpbmd8bnVtYmVyfG51bGx8dW5kZWZpbmVkKSB7XG4gIGNvbnN0IHN0cmluZ2lmaWVkID0gU3RyaW5nKGl0ZW0pO1xuICBsZXQgZGVsaW1pdGVyO1xuICBpZiAoaXRlbSA9PSBudWxsKSB7XG4gICAgZGVsaW1pdGVyID0gJ18nO1xuICB9IGVsc2UgaWYgKHR5cGVvZiBpdGVtID09PSAnbnVtYmVyJykge1xuICAgIGRlbGltaXRlciA9ICcjJztcbiAgfSBlbHNlIHtcbiAgICBkZWxpbWl0ZXIgPSAnOic7XG4gIH1cbiAgcmV0dXJuIGAke3N0cmluZ2lmaWVkLmxlbmd0aH0ke2RlbGltaXRlcn0ke3N0cmluZ2lmaWVkfWA7XG59XG5cbi8qKlxuICogUmV0dXJucyB3aGV0aGVyIHRoZSBwcm9wb3NlZCBrZXkgaXMgYSBwcmVmaXggb2YgdGhlIGN1cnJlbnQga2V5IG9yIHZpY2VcbiAqIHZlcnNhLlxuICogRm9yIGV4YW1wbGU6XG4gKiAtIHByb3Bvc2VkS2V5OiBbJ2InLCAnYyddLCBjdXJyZW50UG9pbnRlcktleTogWydhJywgJ2InLCAnYyddID0+IHRydWVcbiAqICAgICBwcm9wb3NlZEtleSAtPiAxYzFiLCBjdXJyZW50UG9pbnRlcktleSAtPiAxYzFiMWFcbiAqIC0gcHJvcG9zZWRLZXk6IFsnYScsICdiJywgJ2MnXSwgY3VycmVudFBvaW50ZXJLZXk6IFsnYicsICdjJ10sICA9PiB0cnVlXG4gKiAgICAgcHJvcG9zZWRLZXkgLT4gMWMxYjFhLCBjdXJyZW50UG9pbnRlcktleSAtPiAxYzFiXG4gKiAtIHByb3Bvc2VkS2V5OiBbJ2InLCAnYyddLCBjdXJyZW50UG9pbnRlcktleTogWydhJywgJ2InLCAnYycsICdkJ10gPT4gZmFsc2VcbiAqICAgICBwcm9wb3NlZEtleSAtPiAxYzFiLCBjdXJyZW50UG9pbnRlcktleSAtPiAxZDFjMWIxYVxuICovXG5leHBvcnQgZnVuY3Rpb24gaXNNYXRjaGluZ0tleShcbiAgICBwcm9wb3NlZEtleTogdW5rbm93biwgY3VycmVudFBvaW50ZXJLZXk6IHVua25vd24pIHtcbiAgLy8gVGhpcyBpcyBhbHdheXMgdHJ1ZSBpbiBTb3ktSURPTSwgYnV0IEluY3JlbWVudGFsIERPTSBiZWxpZXZlcyB0aGF0IGl0IG1heVxuICAvLyBiZSBudWxsIG9yIG51bWJlci5cbiAgaWYgKHR5cGVvZiBwcm9wb3NlZEtleSA9PT0gJ3N0cmluZycgJiZcbiAgICAgIHR5cGVvZiBjdXJyZW50UG9pbnRlcktleSA9PT0gJ3N0cmluZycpIHtcbiAgICByZXR1cm4gcHJvcG9zZWRLZXkuc3RhcnRzV2l0aChjdXJyZW50UG9pbnRlcktleSkgfHxcbiAgICAgICAgY3VycmVudFBvaW50ZXJLZXkuc3RhcnRzV2l0aChwcm9wb3NlZEtleSk7XG4gIH1cbiAgcmV0dXJuIHByb3Bvc2VkS2V5ID09PSBjdXJyZW50UG9pbnRlcktleTtcbn1cbiJdfQ==