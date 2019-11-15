/**
 * @fileoverview
 *
 * Functions necessary to interact with the Soy-Idom runtime.
 * @suppress {lintChecks}
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
/**
 * A Renderer that keeps track of whether it was ever called to render anything,
 * but never actually does anything  This is used to check whether an HTML value
 * is empty (if it's used in an `{if}` or conditional operator).
 */
var FalsinessRenderer = /** @class */ (function() {
  function FalsinessRenderer() {
    this.rendered = false;
  }
  FalsinessRenderer.prototype.visit = function(el) {};
  FalsinessRenderer.prototype.pushManualKey = function(key) {};
  FalsinessRenderer.prototype.popManualKey = function() {};
  FalsinessRenderer.prototype.pushKey = function(key) {
    return '';
  };
  FalsinessRenderer.prototype.getNewKey = function(key) {
    return '';
  };
  FalsinessRenderer.prototype.popKey = function(oldKey) {};
  FalsinessRenderer.prototype.getCurrentKeyStack = function() {
    return '';
  };
  FalsinessRenderer.prototype.enter = function() {};
  FalsinessRenderer.prototype.exit = function() {};
  FalsinessRenderer.prototype.toNullRenderer = function() {
    return this;
  };
  FalsinessRenderer.prototype.toDefaultRenderer = function() {
    return this;
  };
  FalsinessRenderer.prototype.setLogger = function(logger) {};
  FalsinessRenderer.prototype.getLogger = function() {
    return null;
  };
  FalsinessRenderer.prototype.verifyLogOnly = function(logOnly) {
    throw new Error('Cannot evaluate VE functions in conditions.');
  };
  FalsinessRenderer.prototype.evalLoggingFunction = function(
      name, args, placeHolder) {
    return placeHolder;
  };
  /** Checks whether any DOM was rendered. */
  FalsinessRenderer.prototype.didRender = function() {
    return this.rendered;
  };
  FalsinessRenderer.prototype.open = function(nameOrCtor, key) {
    this.rendered = true;
  };
  FalsinessRenderer.prototype.openSSR = function(nameOrCtor, key) {
    this.rendered = true;
    // Always skip, since we already know that we rendered things.
    return false;
  };
  FalsinessRenderer.prototype.maybeSkip = function() {
    this.rendered = true;
    // Always skip, since we already know that we rendered things.
    return true;
  };
  FalsinessRenderer.prototype.close = function() {
    this.rendered = true;
  };
  FalsinessRenderer.prototype.text = function(value) {
    this.rendered = true;
  };
  FalsinessRenderer.prototype.attr = function(name, value) {
    this.rendered = true;
  };
  FalsinessRenderer.prototype.currentPointer = function() {
    return null;
  };
  FalsinessRenderer.prototype.applyAttrs = function() {
    this.rendered = true;
  };
  FalsinessRenderer.prototype.applyStatics = function(statics) {
    this.rendered = true;
  };
  FalsinessRenderer.prototype.skip = function() {
    this.rendered = true;
  };
  FalsinessRenderer.prototype.key = function(val) {};
  FalsinessRenderer.prototype.currentElement = function() {};
  FalsinessRenderer.prototype.skipNode = function() {
    this.rendered = true;
  };
  return FalsinessRenderer;
}());
exports.FalsinessRenderer = FalsinessRenderer;
//#
//sourceMappingURL=data:application/json;base64,eyJ2ZXJzaW9uIjozLCJmaWxlIjoiYXBpX2lkb20uanMiLCJzb3VyY2VSb290IjoiIiwic291cmNlcyI6WyIuLi8uLi8uLi8uLi8uLi8uLi9qYXZhc2NyaXB0L3RlbXBsYXRlL3NveS9hcGlfaWRvbS50cyJdLCJuYW1lcyI6W10sIm1hcHBpbmdzIjoiQUFBQTs7OztHQUlHOzs7O0FBRUgsa0RBQXdCLENBQUMsZ0RBQWdEO0FBR3pFLHlDQUE0RSxDQUFFLGdEQUFnRDtBQUM5SCwwRkFBaUQsQ0FBRSwrREFBK0Q7QUFRbEgsSUFBTSxXQUFXLEdBQStCO0lBQzlDLE9BQU8sRUFDSCxVQUFDLFNBQVMsRUFBRSxVQUFVLEVBQUUsa0JBQWtCLEVBQUUsV0FBVyxFQUN0RCxpQkFBaUIsSUFBSyxPQUFBLFVBQVUsS0FBSyxrQkFBa0I7UUFDeEQsYUFBYSxDQUFDLFdBQVcsRUFBRSxpQkFBaUIsQ0FBQyxFQUR0QixDQUNzQjtDQUNsRCxDQUFDO0FBRUYsc0VBQXNFO0FBQ3pELFFBQUEsVUFBVSxHQUFHLEVBQUUsQ0FBQztBQUU3QiwyQ0FBMkM7QUFDOUIsUUFBQSxVQUFVLEdBQUcsY0FBYyxDQUFDLGdCQUFnQixDQUFDLFdBQVcsQ0FBQyxDQUFDO0FBQ3ZFLDJDQUEyQztBQUM5QixRQUFBLFVBQVUsR0FBRyxjQUFjLENBQUMsZ0JBQWdCLENBQUMsV0FBVyxDQUFDLENBQUM7QUFDdkUsMkNBQTJDO0FBQzlCLFFBQUEsS0FBSyxHQUFHLGtCQUFVLENBQUM7QUFzQ2hDOzs7O0dBSUc7QUFDSDtJQUFBO1FBQ0UsOEVBQThFO1FBQzlFLG1FQUFtRTtRQUNuRSw4REFBOEQ7UUFDOUQsb0JBQW9CO1FBQ3BCLDJFQUEyRTtRQUMzRSwrQkFBK0I7UUFDL0IsMEVBQTBFO1FBQzFFLHdFQUF3RTtRQUN4RSx5Q0FBeUM7UUFDekMseUVBQXlFO1FBQ3pFLHNEQUFzRDtRQUNyQyxtQkFBYyxHQUFhLEVBQUUsQ0FBQztRQUN2QyxXQUFNLEdBQWdCLElBQUksQ0FBQztJQTJNckMsQ0FBQztJQXpNQzs7O09BR0c7SUFDSCxxQ0FBSSxH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QUN2QixDQUFDO0lBQ0gsd0JBQUM7QUFBRCxDQUFDLEFBM0ZELElBMkZDO0FBM0ZZLDhDQUFpQiIsInNvdXJjZXNDb250ZW50IjpbIi8qKlxuICogQGZpbGVvdmVydmlld1xuICpcbiAqIEZ1bmN0aW9ucyBuZWNlc3NhcnkgdG8gaW50ZXJhY3Qgd2l0aCB0aGUgU295LUlkb20gcnVudGltZS5cbiAqL1xuXG5pbXBvcnQgJ2dvb2c6c295LnZlbG9nJzsgLy8gZnJvbSAvL2phdmFzY3JpcHQvdGVtcGxhdGUvc295OnNveXV0aWxzX3ZlbG9nXG5cbmltcG9ydCAqIGFzIGdvb2dTb3kgZnJvbSAnZ29vZzpnb29nLnNveSc7ICAvLyBmcm9tIC8vamF2YXNjcmlwdC9jbG9zdXJlL3NveVxuaW1wb3J0IHskJFZpc3VhbEVsZW1lbnREYXRhLCBFbGVtZW50TWV0YWRhdGEsIExvZ2dlcn0gZnJvbSAnZ29vZzpzb3kudmVsb2cnOyAgLy8gZnJvbSAvL2phdmFzY3JpcHQvdGVtcGxhdGUvc295OnNveXV0aWxzX3ZlbG9nXG5pbXBvcnQgKiBhcyBpbmNyZW1lbnRhbGRvbSBmcm9tICdpbmNyZW1lbnRhbGRvbSc7ICAvLyBmcm9tIC8vdGhpcmRfcGFydHkvamF2YXNjcmlwdC9pbmNyZW1lbnRhbF9kb206aW5jcmVtZW50YWxkb21cblxuZGVjbGFyZSBnbG9iYWwge1xuICBpbnRlcmZhY2UgTm9kZSB7XG4gICAgX19sYXN0UGFyYW1zOiBzdHJpbmd8dW5kZWZpbmVkO1xuICB9XG59XG5cbmNvbnN0IHBhdGNoQ29uZmlnOiBpbmNyZW1lbnRhbGRvbS5QYXRjaENvbmZpZyA9IHtcbiAgbWF0Y2hlczpcbiAgICAgIChtYXRjaE5vZGUsIG5hbWVPckN0b3IsIGV4cGVjdGVkTmFtZU9yQ3RvciwgcHJvcG9zZWRLZXksXG4gICAgICAgY3VycmVudFBvaW50ZXJLZXkpID0+IG5hbWVPckN0b3IgPT09IGV4cGVjdGVkTmFtZU9yQ3RvciAmJlxuICAgICAgaXNNYXRjaGluZ0tleShwcm9wb3NlZEtleSwgY3VycmVudFBvaW50ZXJLZXkpXG59O1xuXG4vKiogVG9rZW4gZm9yIHNraXBwaW5nIHRoZSBlbGVtZW50LiBUaGlzIGlzIHJldHVybmVkIGluIG9wZW4gY2FsbHMuICovXG5leHBvcnQgY29uc3QgU0tJUF9UT0tFTiA9IHt9O1xuXG4vKiogUGF0Y2hJbm5lciB1c2luZyBTb3ktSURPTSBzZW1hbnRpY3MuICovXG5leHBvcnQgY29uc3QgcGF0Y2hJbm5lciA9IGluY3JlbWVudGFsZG9tLmNyZWF0ZVBhdGNoSW5uZXIocGF0Y2hDb25maWcpO1xuLyoqIFBhdGNoT3V0ZXIgdXNpbmcgU295LUlET00gc2VtYW50aWNzLiAqL1xuZXhwb3J0IGNvbnN0IHBhdGNoT3V0ZXIgPSBpbmNyZW1lbnRhbGRvbS5jcmVhdGVQYXRjaE91dGVyKHBhdGNoQ29uZmlnKTtcbi8qKiBQYXRjaElubmVyIHVzaW5nIFNveS1JRE9NIHNlbWFudGljcy4gKi9cbmV4cG9ydCBjb25zdCBwYXRjaCA9IHBhdGNoSW5uZXI7XG5cbi8qKiBUeXBlIGZvciBIVE1MIHRlbXBsYXRlcyAqL1xuZXhwb3J0IHR5cGUgVGVtcGxhdGU8VD4gPVxuICAgIC8vIHRzbGludDpkaXNhYmxlLW5leHQtbGluZTpuby1hbnlcbiAgICAocmVuZGVyZXI6IEluY3JlbWVudGFsRG9tUmVuZGVyZXIsIGFyZ3M6IFQsIGlqRGF0YT86IGdvb2dTb3kuSWpEYXRhKSA9PiBhbnk7XG5cbmludGVyZmFjZSBJZG9tUmVuZGVyZXJBcGkge1xuICBvcGVuKG5hbWVPckN0b3I6IHN0cmluZywga2V5Pzogc3RyaW5nKTogdm9pZHxIVE1MRWxlbWVudDtcbiAgb3BlblNTUihuYW1lT3JDdG9yOiBzdHJpbmcsIGtleT86IHN0cmluZywgZGF0YT86IHVua25vd24pOiBib29sZWFuO1xuICB2aXNpdChlbDogdm9pZHxIVE1MRWxlbWVudCk6IHZvaWQ7XG4gIG1heWJlU2tpcChyZW5kZXJlcjogSW5jcmVtZW50YWxEb21SZW5kZXJlciwgdmFsOiB1bmtub3duKTogYm9vbGVhbjtcbiAgcHVzaE1hbnVhbEtleShrZXk6IGluY3JlbWVudGFsZG9tLktleSk6IHZvaWQ7XG4gIHBvcE1hbnVhbEtleSgpOiB2b2lkO1xuICBwdXNoS2V5KGtleTogc3RyaW5nKTogc3RyaW5nO1xuICBnZXROZXdLZXkoa2V5OiBzdHJpbmcpOiBzdHJpbmc7XG4gIHBvcEtleShvbGRLZXk6IHN0cmluZyk6IHZvaWQ7XG4gIGdldEN1cnJlbnRLZXlTdGFjaygpOiBzdHJpbmc7XG4gIGNsb3NlKCk6IHZvaWR8RWxlbWVudDtcbiAgdGV4dCh2YWx1ZTogc3RyaW5nKTogdm9pZHxUZXh0O1xuICBhdHRyKG5hbWU6IHN0cmluZywgdmFsdWU6IHN0cmluZyk6IHZvaWQ7XG4gIGN1cnJlbnRQb2ludGVyKCk6IE5vZGV8bnVsbDtcbiAgc2tpcCgpOiB2b2lkO1xuICBjdXJyZW50RWxlbWVudCgpOiB2b2lkfEVsZW1lbnQ7XG4gIHNraXBOb2RlKCk6IHZvaWQ7XG4gIGFwcGx5QXR0cnMoKTogdm9pZDtcbiAgYXBwbHlTdGF0aWNzKHN0YXRpY3M6IGluY3JlbWVudGFsZG9tLlN0YXRpY3MpOiB2b2lkO1xuICBlbnRlcih2ZURhdGE6ICQkVmlzdWFsRWxlbWVudERhdGEsIGxvZ09ubHk6IGJvb2xlYW4pOiB2b2lkO1xuICBleGl0KCk6IHZvaWQ7XG4gIHRvTnVsbFJlbmRlcmVyKCk6IElkb21SZW5kZXJlckFwaTtcbiAgdG9EZWZhdWx0UmVuZGVyZXIoKTogSWRvbVJlbmRlcmVyQXBpO1xuICBzZXRMb2dnZXIobG9nZ2VyOiBMb2dnZXJ8bnVsbCk6IHZvaWQ7XG4gIGdldExvZ2dlcigpOiBMb2dnZXJ8bnVsbDtcbiAgdmVyaWZ5TG9nT25seShsb2dPbmx5OiBib29sZWFuKTogYm9vbGVhbjtcbiAgZXZhbExvZ2dpbmdGdW5jdGlvbihuYW1lOiBzdHJpbmcsIGFyZ3M6IEFycmF5PHt9PiwgcGxhY2VIb2xkZXI6IHN0cmluZyk6XG4gICAgICBzdHJpbmc7XG59XG5cbi8qKlxuICogQ2xhc3MgdGhhdCBtb3N0bHkgZGVsZWdhdGVzIHRvIGdsb2JhbCBJbmNyZW1lbnRhbCBET00gcnVudGltZS4gVGhpcyB3aWxsXG4gKiBldmVudHVhbGx5IHRha2UgaW4gYSBsb2dnZXIgYW5kIGNvbmRpdGlvbmFsbHkgbXV0ZS4gVGhlc2UgbWV0aG9kcyBtYXlcbiAqIHJldHVybiB2b2lkIHdoZW4gaWRvbSBjb21tYW5kcyBhcmUgbXV0ZWQgZm9yIHZlbG9nZ2luZy5cbiAqL1xuZXhwb3J0IGNsYXNzIEluY3JlbWVudGFsRG9tUmVuZGVyZXIgaW1wbGVtZW50cyBJZG9tUmVuZGVyZXJBcGkge1xuICAvLyBTdGFjayAoaG9sZGVyKSBvZiBrZXkgc3RhY2tzIGZvciB0aGUgY3VycmVudCB0ZW1wbGF0ZSBiZWluZyByZW5kZXJlZCwgd2hpY2hcbiAgLy8gaGFzIGNvbnRleHQgb24gd2hlcmUgdGhlIHRlbXBsYXRlIHdhcyBjYWxsZWQgZnJvbSBhbmQgaXMgdXNlZCB0b1xuICAvLyBrZXkgZWFjaCB0ZW1wbGF0ZSBjYWxsIChzZWUgZ28vc295LWlkb20tZGlmZmluZy1zZW1hbnRpY3MpLlxuICAvLyBXb3JrcyBhcyBmb2xsb3dzOlxuICAvLyAtIEEgbmV3IGtleSBpcyBwdXNoZWQgb250byB0aGUgdG9wbW9zdCBrZXkgc3RhY2sgYmVmb3JlIGEgdGVtcGxhdGUgY2FsbCxcbiAgLy8gLSBhbmQgcG9wcGVkIGFmdGVyIHRoZSBjYWxsLlxuICAvLyAtIEEgbmV3IHN0YWNrIGlzIHB1c2hlZCBvbnRvIHRoZSBob2xkZXIgYmVmb3JlIGEgbWFudWFsbHkga2V5ZWQgZWxlbWVudFxuICAvLyAgIGlzIG9wZW5lZCwgYW5kIHBvcHBlZCBiZWZvcmUgdGhlIGVsZW1lbnQgaXMgY2xvc2VkLiBUaGlzIGlzIGJlY2F1c2VcbiAgLy8gICBtYW51YWwga2V5cyBcInJlc2V0XCIgdGhlIGtleSBjb250ZXh0LlxuICAvLyBOb3RlIHRoYXQgZm9yIHBlcmZvcm1hbmNlLCB0aGUgXCJzdGFja1wiIGlzIGltcGxlbWVudGVkIGFzIGEgc3RyaW5nIHdpdGhcbiAgLy8gdGhlIGl0ZW1zIGJlaW5nIGAke1NJWkUgT0YgS0VZfSR7REVMSU1JVEVSfSR7S0VZfWAuXG4gIHByaXZhdGUgcmVhZG9ubHkga2V5U3RhY2tIb2xkZXI6IHN0cmluZ1tdID0gW107XG4gIHByaXZhdGUgbG9nZ2VyOiBMb2dnZXJ8bnVsbCA9IG51bGw7XG5cbiAgLyoqXG4gICAqIFB1c2hlcy9wb3BzIHRoZSBnaXZlbiBrZXkgZnJvbSBga2V5U3RhY2tgICh2ZXJzdXMgYEFycmF5I2NvbmNhdGApXG4gICAqIHRvIGF2b2lkIGFsbG9jYXRpbmcgYSBuZXcgYXJyYXkgZm9yIGV2ZXJ5IGVsZW1lbnQgb3Blbi5cbiAgICovXG4gIG9wZW4obmFtZU9yQ3Rvcjogc3RyaW5nLCBrZXkgPSAnJyk6IEhUTUxFbGVtZW50fHZvaWQge1xuICAgIGNvbnN0IGVsID0gaW5jcmVtZW50YWxkb20ub3BlbihuYW1lT3JDdG9yLCB0aGlzLmdldE5ld0tleShrZXkpKTtcbiAgICB0aGlzLnZpc2l0KGVsKTtcbiAgICByZXR1cm4gZWw7XG4gIH1cblxuICAvKipcbiAgICogT3BlbiBidXQgZm9yIG5vZGVzIHRoYXQgdXNlIHtza2lwfS4gVGhpcyB1c2VzIGEga2V5IHRoYXQgaXMgc2VyaWFsaXplZFxuICAgKiBhdCBzZXJ2ZXItc2lkZSByZW5kZXJpbmcuXG4gICAqIEZvciBtb3JlIGluZm9ybWF0aW9uLCBzZWUgZ28vdHlwZWQtaHRtbC10ZW1wbGF0ZXMuXG4gICAqL1xuICBvcGVuU1NSKG5hbWVPckN0b3I6IHN0cmluZywga2V5ID0gJycsIGRhdGE6IHVua25vd24gPSBudWxsKSB7XG4gICAgY29uc3QgZWwgPSBpbmNyZW1lbnRhbGRvbS5vcGVuKG5hbWVPckN0b3IsIGtleSk7XG4gICAgdGhpcy52aXNpdChlbCk7XG4gICAgaWYgKGdvb2cuREVCVUcpIHtcbiAgICAgIHRoaXMuYXR0cignc295LXNlcnZlci1rZXknLCBrZXkpO1xuICAgIH1cbiAgICAvLyBLZWVwIGdvaW5nIHNpbmNlIGVpdGhlciBlbGVtZW50cyBhcmUgYmVpbmcgY3JlYXRlZCBvciBjb250aW51aW5nIHdpbGxcbiAgICAvLyBiZSBhIG5vLW9wLlxuICAgIGlmICghZWwgfHwgIWVsLmhhc0NoaWxkTm9kZXMoKSkge1xuICAgICAgcmV0dXJuIHRydWU7XG4gICAgfVxuICAgIC8vIERhdGEgaXMgb25seSBwYXNzZWQgYnkge3NraXB9IGVsZW1lbnRzIHRoYXQgYXJlIHJvb3RzIG9mIHRlbXBsYXRlcy5cbiAgICBpZiAoZ29vZy5ERUJVRyAmJiBkYXRhKSB7XG4gICAgICBtYXliZVJlcG9ydEVycm9ycyhlbCwgZGF0YSk7XG4gICAgfVxuICAgIC8vIENhdmVhdDogaWYgdGhlIGVsZW1lbnQgaGFzIG9ubHkgYXR0cmlidXRlcywgd2Ugd2lsbCBza2lwIHJlZ2FyZGxlc3MuXG4gICAgdGhpcy5za2lwKCk7XG4gICAgdGhpcy5jbG9zZSgpO1xuICAgIHJldHVybiBmYWxzZTtcbiAgfVxuXG4gIC8vIEZvciB1c2VycyBleHRlbmRpbmcgSW5jcmVtZW50YWxEb21SZW5kZXJlclxuICB2aXNpdChlbDogSFRNTEVsZW1lbnR8dm9pZCkge31cblxuICAvKipcbiAgICogQ2FsbGVkIG9uIHRoZSByZXR1cm4gdmFsdWUgb2Ygb3Blbi4gVGhpcyBpcyBvbmx5IHRydWUgaWYgaXQgaXMgZXhhY3RseVxuICAgKiB0aGUgc2tpcCB0b2tlbi4gVGhpcyBoYXMgdGhlIHNpZGUgZWZmZWN0IG9mIHBlcmZvcm1pbmcgdGhlIHNraXAuXG4gICAqL1xuICBtYXliZVNraXAocmVuZGVyZXI6IEluY3JlbWVudGFsRG9tUmVuZGVyZXIsIHZhbDogdW5rbm93bikge1xuICAgIGlmICh2YWwgPT09IFNLSVBfVE9LRU4pIHtcbiAgICAgIHJlbmRlcmVyLnNraXAoKTtcbiAgICAgIHJlbmRlcmVyLmNsb3NlKCk7XG4gICAgICByZXR1cm4gdHJ1ZTtcbiAgICB9XG4gICAgcmV0dXJuIGZhbHNlO1xuICB9XG5cbiAgLyoqXG4gICAqIENhbGxlZCAoZnJvbSBnZW5lcmF0ZWQgdGVtcGxhdGUgcmVuZGVyIGZ1bmN0aW9uKSBiZWZvcmUgT1BFTklOR1xuICAgKiBrZXllZCBlbGVtZW50cy5cbiAgICovXG4gIHB1c2hNYW51YWxLZXkoa2V5OiBpbmNyZW1lbnRhbGRvbS5LZXkpIHtcbiAgICB0aGlzLmtleVN0YWNrSG9sZGVyLnB1c2goc2VyaWFsaXplS2V5KGtleSkpO1xuICB9XG5cbiAgLyoqXG4gICAqIENhbGxlZCAoZnJvbSBnZW5lcmF0ZWQgdGVtcGxhdGUgcmVuZGVyIGZ1bmN0aW9uKSBiZWZvcmUgQ0xPU0lOR1xuICAgKiBrZXllZCBlbGVtZW50cy5cbiAgICovXG4gIHBvcE1hbnVhbEtleSgpIHtcbiAgICB0aGlzLmtleVN0YWNrSG9sZGVyLnBvcCgpO1xuICB9XG5cbiAgLyoqXG4gICAqIENhbGxlZCAoZnJvbSBnZW5lcmF0ZWQgdGVtcGxhdGUgcmVuZGVyIGZ1bmN0aW9uKSBCRUZPUkUgdGVtcGxhdGVcbiAgICogY2FsbHMuXG4gICAqL1xuICBwdXNoS2V5KGtleTogc3RyaW5nKSB7XG4gICAgY29uc3Qgb2xkS2V5ID0gdGhpcy5nZXRDdXJyZW50S2V5U3RhY2soKTtcbiAgICB0aGlzLmtleVN0YWNrSG9sZGVyW3RoaXMua2V5U3RhY2tIb2xkZXIubGVuZ3RoIC0gMV0gPSB0aGlzLmdldE5ld0tleShrZXkpO1xuICAgIHJldHVybiBvbGRLZXk7XG4gIH1cblxuICBnZXROZXdLZXkoa2V5OiBzdHJpbmcpIHtcbiAgICBjb25zdCBvbGRLZXkgPSB0aGlzLmdldEN1cnJlbnRLZXlTdGFjaygpO1xuICAgIGNvbnN0IHNlcmlhbGl6ZWRLZXkgPSBzZXJpYWxpemVLZXkoa2V5KTtcbiAgICByZXR1cm4gc2VyaWFsaXplZEtleSArIG9sZEtleTtcbiAgfVxuXG4gIC8qKlxuICAgKiBDYWxsZWQgKGZyb20gZ2VuZXJhdGVkIHRlbXBsYXRlIHJlbmRlciBmdW5jdGlvbikgQUZURVIgdGVtcGxhdGVcbiAgICogY2FsbHMuXG4gICAqL1xuICBwb3BLZXkob2xkS2V5OiBzdHJpbmcpIHtcbiAgICB0aGlzLmtleVN0YWNrSG9sZGVyW3RoaXMua2V5U3RhY2tIb2xkZXIubGVuZ3RoIC0gMV0gPSBvbGRLZXk7XG4gIH1cblxuICAvKipcbiAgICogUmV0dXJucyB0aGUgc3RhY2sgb24gdG9wIG9mIHRoZSBob2xkZXIuIFRoaXMgcmVwcmVzZW50cyB0aGUgY3VycmVudFxuICAgKiBjaGFpbiBvZiBrZXlzLlxuICAgKi9cbiAgZ2V0Q3VycmVudEtleVN0YWNrKCk6IHN0cmluZyB7XG4gICAgcmV0dXJuIHRoaXMua2V5U3RhY2tIb2xkZXJbdGhpcy5rZXlTdGFja0hvbGRlci5sZW5ndGggLSAxXSB8fCAnJztcbiAgfVxuXG4gIGNsb3NlKCk6IEVsZW1lbnR8dm9pZCB7XG4gICAgcmV0dXJuIGluY3JlbWVudGFsZG9tLmNsb3NlKCk7XG4gIH1cblxuICB0ZXh0KHZhbHVlOiBzdHJpbmcpOiBUZXh0fHZvaWQge1xuICAgIHJldHVybiBpbmNyZW1lbnRhbGRvbS50ZXh0KHZhbHVlKTtcbiAgfVxuXG4gIGF0dHIobmFtZTogc3RyaW5nLCB2YWx1ZTogc3RyaW5nKSB7XG4gICAgaW5jcmVtZW50YWxkb20uYXR0cihuYW1lLCB2YWx1ZSk7XG4gIH1cblxuICBjdXJyZW50UG9pbnRlcigpOiBOb2RlfG51bGwge1xuICAgIHJldHVybiBpbmNyZW1lbnRhbGRvbS5jdXJyZW50UG9pbnRlcigpO1xuICB9XG5cbiAgc2tpcCgpIHtcbiAgICBpbmNyZW1lbnRhbGRvbS5za2lwKCk7XG4gIH1cblxuICBjdXJyZW50RWxlbWVudCgpOiBFbGVtZW50fHZvaWQge1xuICAgIHJldHVybiBpbmNyZW1lbnRhbGRvbS5jdXJyZW50RWxlbWVudCgpO1xuICB9XG5cbiAgc2tpcE5vZGUoKSB7XG4gICAgaW5jcmVtZW50YWxkb20uc2tpcE5vZGUoKTtcbiAgfVxuXG4gIGFwcGx5QXR0cnMoKSB7XG4gICAgaW5jcmVtZW50YWxkb20uYXBwbHlBdHRycygpO1xuICB9XG5cbiAgYXBwbHlTdGF0aWNzKHN0YXRpY3M6IGluY3JlbWVudGFsZG9tLlN0YXRpY3MpIHtcbiAgICBpbmNyZW1lbnRhbGRvbS5hcHBseVN0YXRpY3Moc3RhdGljcyk7XG4gIH1cblxuICAvKipcbiAgICogQ2FsbGVkIHdoZW4gYSBge3ZlbG9nfWAgc3RhdGVtZW50IGlzIGVudGVyZWQuXG4gICAqL1xuICBlbnRlcih2ZURhdGE6ICQkVmlzdWFsRWxlbWVudERhdGEsIGxvZ09ubHk6IGJvb2xlYW4pIHtcbiAgICBpZiAodGhpcy5sb2dnZXIpIHtcbiAgICAgIHRoaXMubG9nZ2VyLmVudGVyKG5ldyBFbGVtZW50TWV0YWRhdGEoXG4gICAgICAgICAgdmVEYXRhLmdldFZlKCkuZ2V0SWQoKSwgdmVEYXRhLmdldERhdGEoKSwgbG9nT25seSkpO1xuICAgIH1cbiAgfVxuXG4gIC8qKlxuICAgKiBDYWxsZWQgd2hlbiBhIGB7dmVsb2d9YCBzdGF0ZW1lbnQgaXMgZXhpdGVkLlxuICAgKi9cbiAgZXhpdCgpIHtcbiAgICBpZiAodGhpcy5sb2dnZXIpIHtcbiAgICAgIHRoaXMubG9nZ2VyLmV4aXQoKTtcbiAgICB9XG4gIH1cblxuICAvKipcbiAgICogU3dpdGNoZXMgcnVudGltZSB0byBwcm9kdWNlIGluY3JlbWVudGFsIGRvbSBjYWxscyB0aGF0IGRvIG5vdCB0cmF2ZXJzZVxuICAgKiB0aGUgRE9NLiBUaGlzIGhhcHBlbnMgd2hlbiBsb2dPbmx5IGluIGEgdmVsb2dnaW5nIG5vZGUgaXMgc2V0IHRvIHRydWUuXG4gICAqIEZvciBtb3JlIGluZm8sIHNlZSBodHRwOi8vZ28vc295L3JlZmVyZW5jZS92ZWxvZyN0aGUtbG9nb25seS1hdHRyaWJ1dGVcbiAgICovXG4gIHRvTnVsbFJlbmRlcmVyKCkge1xuICAgIGNvbnN0IG51bGxSZW5kZXJlciA9IG5ldyBOdWxsUmVuZGVyZXIodGhpcyk7XG4gICAgcmV0dXJuIG51bGxSZW5kZXJlcjtcbiAgfVxuXG4gIHRvRGVmYXVsdFJlbmRlcmVyKCk6IEluY3JlbWVudGFsRG9tUmVuZGVyZXIge1xuICAgIHRocm93IG5ldyBFcnJvcihcbiAgICAgICAgJ0Nhbm5vdCB0cmFuc2l0aW9uIGEgZGVmYXVsdCByZW5kZXJlciB0byBhIGRlZmF1bHQgcmVuZGVyZXInKTtcbiAgfVxuXG4gIC8qKiBDYWxsZWQgYnkgdXNlciBjb2RlIHRvIGNvbmZpZ3VyZSBsb2dnaW5nICovXG4gIHNldExvZ2dlcihsb2dnZXI6IExvZ2dlcnxudWxsKSB7XG4gICAgdGhpcy5sb2dnZXIgPSBsb2dnZXI7XG4gIH1cblxuICBnZXRMb2dnZXIoKSB7XG4gICAgcmV0dXJuIHRoaXMubG9nZ2VyO1xuICB9XG5cbiAgLyoqXG4gICAqIFVzZWQgdG8gdHJpZ2dlciB0aGUgcmVxdWlyZW1lbnQgdGhhdCBsb2dPbmx5IGNhbiBvbmx5IGJlIHRydWUgd2hlbiBhXG4gICAqIGxvZ2dlciBpcyBjb25maWd1cmVkLiBPdGhlcndpc2UsIGl0IGlzIGEgcGFzc3Rocm91Z2ggZnVuY3Rpb24uXG4gICAqL1xuICB2ZXJpZnlMb2dPbmx5KGxvZ09ubHk6IGJvb2xlYW4pIHtcbiAgICBpZiAoIXRoaXMubG9nZ2VyICYmIGxvZ09ubHkpIHtcbiAgICAgIHRocm93IG5ldyBFcnJvcihcbiAgICAgICAgICAnQ2Fubm90IHNldCBsb2dvbmx5PVwidHJ1ZVwiIHVubGVzcyB0aGVyZSBpcyBhIGxvZ2dlciBjb25maWd1cmVkJyk7XG4gICAgfVxuICAgIHJldHVybiBsb2dPbmx5O1xuICB9XG5cbiAgLypcbiAgICogQ2FsbGVkIHdoZW4gYSBsb2dnaW5nIGZ1bmN0aW9uIGlzIGV2YWx1YXRlZC5cbiAgICovXG4gIGV2YWxMb2dnaW5nRnVuY3Rpb24obmFtZTogc3RyaW5nLCBhcmdzOiBBcnJheTx7fT4sIHBsYWNlSG9sZGVyOiBzdHJpbmcpOlxuICAgICAgc3RyaW5nIHtcbiAgICBpZiAodGhpcy5sb2dnZXIpIHtcbiAgICAgIHJldHVybiB0aGlzLmxvZ2dlci5ldmFsTG9nZ2luZ0Z1bmN0aW9uKG5hbWUsIGFyZ3MpO1xuICAgIH1cbiAgICByZXR1cm4gcGxhY2VIb2xkZXI7XG4gIH1cbn1cblxuLyoqXG4gKiBSZW5kZXJlciB0aGF0IG11dGVzIGFsbCBJRE9NIGNvbW1hbmRzIGFuZCByZXR1cm5zIHZvaWQuXG4gKiBGb3IgbW9yZSBpbmZvLCBzZWUgaHR0cDovL2dvL3NveS9yZWZlcmVuY2UvdmVsb2cjdGhlLWxvZ29ubHktYXR0cmlidXRlXG4gKi9cbmV4cG9ydCBjbGFzcyBOdWxsUmVuZGVyZXIgZXh0ZW5kcyBJbmNyZW1lbnRhbERvbVJlbmRlcmVyIHtcbiAgY29uc3RydWN0b3IocHJpdmF0ZSByZWFkb25seSByZW5kZXJlcjogSW5jcmVtZW50YWxEb21SZW5kZXJlcikge1xuICAgIHN1cGVyKCk7XG4gICAgdGhpcy5zZXRMb2dnZXIocmVuZGVyZXIuZ2V0TG9nZ2VyKCkpO1xuICB9XG5cbiAgb3BlbihuYW1lT3JDdG9yOiBzdHJpbmcsIGtleT86IHN0cmluZykge31cblxuICBvcGVuU1NSKG5hbWVPckN0b3I6IHN0cmluZywga2V5Pzogc3RyaW5nKSB7XG4gICAgcmV0dXJuIHRydWU7XG4gIH1cblxuICBjbG9zZSgpIHt9XG5cbiAgdGV4dCh2YWx1ZTogc3RyaW5nKSB7fVxuXG4gIGF0dHIobmFtZTogc3RyaW5nLCB2YWx1ZTogc3RyaW5nKSB7fVxuXG4gIGN1cnJlbnRQb2ludGVyKCkge1xuICAgIHJldHVybiBudWxsO1xuICB9XG5cbiAgYXBwbHlBdHRycygpIHt9XG5cbiAgYXBwbHlTdGF0aWNzKHN0YXRpY3M6IGluY3JlbWVudGFsZG9tLlN0YXRpY3MpIHt9XG5cbiAgc2tpcCgpIHt9XG5cbiAga2V5KHZhbDogc3RyaW5nKSB7fVxuXG4gIGN1cnJlbnRFbGVtZW50KCkge31cblxuICBza2lwTm9kZSgpIHt9XG5cbiAgLyoqIFJldHVybnMgdG8gdGhlIGRlZmF1bHQgcmVuZGVyZXIgd2hpY2ggd2lsbCB0cmF2ZXJzZSB0aGUgRE9NLiAqL1xuICB0b0RlZmF1bHRSZW5kZXJlcigpIHtcbiAgICB0aGlzLnJlbmRlcmVyIS5zZXRMb2dnZXIodGhpcy5nZXRMb2dnZXIoKSk7XG4gICAgcmV0dXJuIHRoaXMucmVuZGVyZXI7XG4gIH1cbn1cblxuLyoqXG4gKiBQcm92aWRlcyBhIGNvbXBhY3Qgc2VyaWFsaXphdGlvbiBmb3JtYXQgZm9yIHRoZSBrZXkgc3RydWN0dXJlLlxuICovXG5leHBvcnQgZnVuY3Rpb24gc2VyaWFsaXplS2V5KGl0ZW06IHN0cmluZ3xudW1iZXJ8bnVsbHx1bmRlZmluZWQpIHtcbiAgY29uc3Qgc3RyaW5naWZpZWQgPSBTdHJpbmcoaXRlbSk7XG4gIGxldCBkZWxpbWl0ZXI7XG4gIGlmIChpdGVtID09IG51bGwpIHtcbiAgICBkZWxpbWl0ZXIgPSAnXyc7XG4gIH0gZWxzZSBpZiAodHlwZW9mIGl0ZW0gPT09ICdudW1iZXInKSB7XG4gICAgZGVsaW1pdGVyID0gJyMnO1xuICB9IGVsc2Uge1xuICAgIGRlbGltaXRlciA9ICc6JztcbiAgfVxuICByZXR1cm4gYCR7c3RyaW5naWZpZWQubGVuZ3RofSR7ZGVsaW1pdGVyfSR7c3RyaW5naWZpZWR9YDtcbn1cblxuLyoqXG4gKiBSZXR1cm5zIHdoZXRoZXIgdGhlIHByb3Bvc2VkIGtleSBpcyBhIHByZWZpeCBvZiB0aGUgY3VycmVudCBrZXkgb3IgdmljZVxuICogdmVyc2EuXG4gKiBGb3IgZXhhbXBsZTpcbiAqIC0gcHJvcG9zZWRLZXk6IFsnYicsICdjJ10sIGN1cnJlbnRQb2ludGVyS2V5OiBbJ2EnLCAnYicsICdjJ10gPT4gdHJ1ZVxuICogICAgIHByb3Bvc2VkS2V5IC0+IDFjMWIsIGN1cnJlbnRQb2ludGVyS2V5IC0+IDFjMWIxYVxuICogLSBwcm9wb3NlZEtleTogWydhJywgJ2InLCAnYyddLCBjdXJyZW50UG9pbnRlcktleTogWydiJywgJ2MnXSwgID0+IHRydWVcbiAqICAgICBwcm9wb3NlZEtleSAtPiAxYzFiMWEsIGN1cnJlbnRQb2ludGVyS2V5IC0+IDFjMWJcbiAqIC0gcHJvcG9zZWRLZXk6IFsnYicsICdjJ10sIGN1cnJlbnRQb2ludGVyS2V5OiBbJ2EnLCAnYicsICdjJywgJ2QnXSA9PiBmYWxzZVxuICogICAgIHByb3Bvc2VkS2V5IC0+IDFjMWIsIGN1cnJlbnRQb2ludGVyS2V5IC0+IDFkMWMxYjFhXG4gKi9cbmV4cG9ydCBmdW5jdGlvbiBpc01hdGNoaW5nS2V5KFxuICAgIHByb3Bvc2VkS2V5OiB1bmtub3duLCBjdXJyZW50UG9pbnRlcktleTogdW5rbm93bikge1xuICAvLyBUaGlzIGlzIGFsd2F5cyB0cnVlIGluIFNveS1JRE9NLCBidXQgSW5jcmVtZW50YWwgRE9NIGJlbGlldmVzIHRoYXQgaXQgbWF5XG4gIC8vIGJlIG51bGwgb3IgbnVtYmVyLlxuICBpZiAodHlwZW9mIHByb3Bvc2VkS2V5ID09PSAnc3RyaW5nJyAmJlxuICAgICAgdHlwZW9mIGN1cnJlbnRQb2ludGVyS2V5ID09PSAnc3RyaW5nJykge1xuICAgIHJldHVybiBwcm9wb3NlZEtleS5zdGFydHNXaXRoKGN1cnJlbnRQb2ludGVyS2V5KSB8fFxuICAgICAgICBjdXJyZW50UG9pbnRlcktleS5zdGFydHNXaXRoKHByb3Bvc2VkS2V5KTtcbiAgfVxuICAvLyBVc2luZyBcIj09XCIgaW5zdGVhZCBvZiBcIj09PVwiIGlzIGludGVudGlvbmFsLiBTU1Igc2VyaWFsaXplcyBhdHRyaWJ1dGVzXG4gIC8vIGRpZmZlcmVudGx5IHRoYW4gdGhlIHR5cGUgdGhhdCBrZXlzIGFyZS4gRm9yIGV4YW1wbGUgXCIwXCIgPT0gMC5cbiAgLy8gdHNsaW50OmRpc2FibGUtbmV4dC1saW5lOnRyaXBsZS1lcXVhbHNcbiAgcmV0dXJuIHByb3Bvc2VkS2V5ID09IGN1cnJlbnRQb2ludGVyS2V5O1xufVxuXG5mdW5jdGlvbiBtYXliZVJlcG9ydEVycm9ycyhlbDogSFRNTEVsZW1lbnQsIGRhdGE6IHVua25vd24pIHtcbiAgY29uc3Qgc3RyaW5naWZpZWRQYXJhbXMgPSBKU09OLnN0cmluZ2lmeShkYXRhLCBudWxsLCAyKTtcbiAgaWYgKCFlbC5fX2xhc3RQYXJhbXMpIHtcbiAgICBlbC5fX2xhc3RQYXJhbXMgPSBzdHJpbmdpZmllZFBhcmFtcztcbiAgICByZXR1cm47XG4gIH1cbiAgaWYgKHN0cmluZ2lmaWVkUGFyYW1zICE9PSBlbC5fX2xhc3RQYXJhbXMpIHtcbiAgICB0aHJvdyBuZXcgRXJyb3IoYFxuVHJpZWQgdG8gcmVyZW5kZXIgYSB7c2tpcH0gdGVtcGxhdGUgd2l0aCBkaWZmZXJlbnQgcGFyYW1ldGVycyFcbk1ha2Ugc3VyZSB0aGF0IHlvdSBuZXZlciBwYXNzIGEgcGFyYW1ldGVyIHRoYXQgY2FuIGNoYW5nZSB0byBhIHRlbXBsYXRlIHRoYXQgaGFzXG57c2tpcH0sIHNpbmNlIGNoYW5nZXMgdG8gdGhhdCBwYXJhbWV0ZXIgd29uJ3QgYWZmZWN0IHRoZSBza2lwcGVkIGNvbnRlbnQuXG5cbk9sZCBwYXJhbWV0ZXJzOiAke2VsLl9fbGFzdFBhcmFtc31cbk5ldyBwYXJhbWV0ZXJzOiAke3N0cmluZ2lmaWVkUGFyYW1zfVxuXG5FbGVtZW50OlxuJHtlbC5kYXRhc2V0WydkZWJ1Z1NveSddIHx8IGVsLm91dGVySFRNTH1gKTtcbiAgfVxufVxuXG5cbi8qKlxuICogQSBSZW5kZXJlciB0aGF0IGtlZXBzIHRyYWNrIG9mIHdoZXRoZXIgaXQgd2FzIGV2ZXIgY2FsbGVkIHRvIHJlbmRlciBhbnl0aGluZyxcbiAqIGJ1dCBuZXZlciBhY3R1YWxseSBkb2VzIGFueXRoaW5nICBUaGlzIGlzIHVzZWQgdG8gY2hlY2sgd2hldGhlciBhbiBIVE1MIHZhbHVlXG4gKiBpcyBlbXB0eSAoaWYgaXQncyB1c2VkIGluIGFuIGB7aWZ9YCBvciBjb25kaXRpb25hbCBvcGVyYXRvcikuXG4gKi9cbmV4cG9ydCBjbGFzcyBGYWxzaW5lc3NSZW5kZXJlciBpbXBsZW1lbnRzIElkb21SZW5kZXJlckFwaSB7XG4gIHZpc2l0KGVsOiB2b2lkfEhUTUxFbGVtZW50KTogdm9pZCB7fVxuICBwdXNoTWFudWFsS2V5KGtleTogaW5jcmVtZW50YWxkb20uS2V5KSB7fVxuICBwb3BNYW51YWxLZXkoKTogdm9pZCB7fVxuICBwdXNoS2V5KGtleTogc3RyaW5nKTogc3RyaW5nIHtcbiAgICByZXR1cm4gJyc7XG4gIH1cbiAgZ2V0TmV3S2V5KGtleTogc3RyaW5nKTogc3RyaW5nIHtcbiAgICByZXR1cm4gJyc7XG4gIH1cbiAgcG9wS2V5KG9sZEtleTogc3RyaW5nKTogdm9pZCB7fVxuICBnZXRDdXJyZW50S2V5U3RhY2soKTogc3RyaW5nIHtcbiAgICByZXR1cm4gJyc7XG4gIH1cbiAgZW50ZXIoKTogdm9pZCB7fVxuICBleGl0KCk6IHZvaWQge31cbiAgdG9OdWxsUmVuZGVyZXIoKTogSWRvbVJlbmRlcmVyQXBpIHtcbiAgICByZXR1cm4gdGhpcztcbiAgfVxuICB0b0RlZmF1bHRSZW5kZXJlcigpOiBJZG9tUmVuZGVyZXJBcGkge1xuICAgIHJldHVybiB0aGlzO1xuICB9XG4gIHNldExvZ2dlcihsb2dnZXI6IExvZ2dlcnxudWxsKTogdm9pZCB7fVxuICBnZXRMb2dnZXIoKTogTG9nZ2VyfG51bGwge1xuICAgIHJldHVybiBudWxsO1xuICB9XG4gIHZlcmlmeUxvZ09ubHkobG9nT25seTogYm9vbGVhbik6IGJvb2xlYW4ge1xuICAgIHRocm93IG5ldyBFcnJvcignQ2Fubm90IGV2YWx1YXRlIFZFIGZ1bmN0aW9ucyBpbiBjb25kaXRpb25zLicpO1xuICB9XG4gIGV2YWxMb2dnaW5nRnVuY3Rpb24obmFtZTogc3RyaW5nLCBhcmdzOiBBcnJheTx7fT4sIHBsYWNlSG9sZGVyOiBzdHJpbmcpOlxuICAgICAgc3RyaW5nIHtcbiAgICByZXR1cm4gcGxhY2VIb2xkZXI7XG4gIH1cbiAgcHJpdmF0ZSByZW5kZXJlZCA9IGZhbHNlO1xuXG4gIC8qKiBDaGVja3Mgd2hldGhlciBhbnkgRE9NIHdhcyByZW5kZXJlZC4gKi9cbiAgZGlkUmVuZGVyKCkge1xuICAgIHJldHVybiB0aGlzLnJlbmRlcmVkO1xuICB9XG5cbiAgb3BlbihuYW1lT3JDdG9yOiBzdHJpbmcsIGtleT86IHN0cmluZykge1xuICAgIHRoaXMucmVuZGVyZWQgPSB0cnVlO1xuICB9XG5cbiAgb3BlblNTUihuYW1lT3JDdG9yOiBzdHJpbmcsIGtleT86IHN0cmluZykge1xuICAgIHRoaXMucmVuZGVyZWQgPSB0cnVlO1xuICAgIC8vIEFsd2F5cyBza2lwLCBzaW5jZSB3ZSBhbHJlYWR5IGtub3cgdGhhdCB3ZSByZW5kZXJlZCB0aGluZ3MuXG4gICAgcmV0dXJuIGZhbHNlO1xuICB9XG5cbiAgbWF5YmVTa2lwKCkge1xuICAgIHRoaXMucmVuZGVyZWQgPSB0cnVlO1xuICAgIC8vIEFsd2F5cyBza2lwLCBzaW5jZSB3ZSBhbHJlYWR5IGtub3cgdGhhdCB3ZSByZW5kZXJlZCB0aGluZ3MuXG4gICAgcmV0dXJuIHRydWU7XG4gIH1cblxuICBjbG9zZSgpIHtcbiAgICB0aGlzLnJlbmRlcmVkID0gdHJ1ZTtcbiAgfVxuXG4gIHRleHQodmFsdWU6IHN0cmluZykge1xuICAgIHRoaXMucmVuZGVyZWQgPSB0cnVlO1xuICB9XG5cbiAgYXR0cihuYW1lOiBzdHJpbmcsIHZhbHVlOiBzdHJpbmcpIHtcbiAgICB0aGlzLnJlbmRlcmVkID0gdHJ1ZTtcbiAgfVxuXG4gIGN1cnJlbnRQb2ludGVyKCkge1xuICAgIHJldHVybiBudWxsO1xuICB9XG5cbiAgYXBwbHlBdHRycygpIHtcbiAgICB0aGlzLnJlbmRlcmVkID0gdHJ1ZTtcbiAgfVxuXG4gIGFwcGx5U3RhdGljcyhzdGF0aWNzOiBpbmNyZW1lbnRhbGRvbS5TdGF0aWNzKSB7XG4gICAgdGhpcy5yZW5kZXJlZCA9IHRydWU7XG4gIH1cblxuICBza2lwKCkge1xuICAgIHRoaXMucmVuZGVyZWQgPSB0cnVlO1xuICB9XG5cbiAga2V5KHZhbDogc3RyaW5nKSB7fVxuXG4gIGN1cnJlbnRFbGVtZW50KCkge31cblxuICBza2lwTm9kZSgpIHtcbiAgICB0aGlzLnJlbmRlcmVkID0gdHJ1ZTtcbiAgfVxufVxuIl19
