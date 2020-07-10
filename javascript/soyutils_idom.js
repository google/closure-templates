/*
 * @fileoverview Helper utilities for incremental dom code generation in Soy.
 * Copyright 2016 Google Inc.
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
/** @fileoverview @suppress {lintChecks} */
goog.module('google3.javascript.template.soy.soyutils_idom');
var module = module || { id: 'javascript/template/soy/soyutils_idom.js' };
var tslib_1 = goog.require('google3.third_party.javascript.tslib.tslib');
var googSoy = goog.require('goog.soy'); // from //javascript/closure/soy
var goog_goog_soy_data_SanitizedContent_1 = goog.require('goog.soy.data.SanitizedContent');  // from //javascript/closure/soy:data
var goog_goog_soy_data_SanitizedContentKind_1 = goog.require('goog.soy.data.SanitizedContentKind'); // from //javascript/closure/soy:data
var goog_goog_soy_data_SanitizedHtml_1 = goog.require('goog.soy.data.SanitizedHtml'); // from //javascript/closure/soy:data
var googString = goog.require('goog.string'); // from //javascript/closure/string
var soy = goog.require('soy'); // from //javascript/template/soy:soy_usegoog_js
var goog_soy_checks_1 = goog.require('soy.checks'); // from //javascript/template/soy:checks
var goog_soydata_VERY_UNSAFE_1 = goog.require('soydata.VERY_UNSAFE'); // from //javascript/template/soy:soy_usegoog_js
var incrementaldom = goog.require('google3.third_party.javascript.incremental_dom.index'); // from //third_party/javascript/incremental_dom:incrementaldom
var api_idom_1 = goog.require('google3.javascript.template.soy.api_idom');
var element_lib_idom_1 = goog.require('google3.javascript.template.soy.element_lib_idom');
exports.$SoyElement = element_lib_idom_1.SoyElement;
var global_1 = goog.require('google3.javascript.template.soy.global');
// Declare properties that need to be applied not as attributes but as
// actual DOM properties.
var attributes = incrementaldom.attributes;
var defaultIdomRenderer = new api_idom_1.IncrementalDomRenderer();
// tslint:disable-next-line:no-any
attributes['checked'] = function (el, name, value) {
    // We don't use !!value because:
    // 1. If value is '' (this is the case where a user uses <div checked />),
    //    the checked value should be true, but '' is falsy.
    // 2. If value is 'false', the checked value should be false, but
    //    'false' is truthy.
    el.setAttribute('checked', value);
    el.checked =
        !(value === false || value === 'false' || value === undefined);
};
// tslint:disable-next-line:no-any
attributes['value'] = function (el, name, value) {
    el.value = value;
    el.setAttribute('value', value);
};
// Soy uses the {key} command syntax, rather than HTML attributes, to
// indicate element keys.
incrementaldom.setKeyAttributeName('soy-server-key');
/**
 * Tries to find an existing Soy element, if it exists. Otherwise, it creates
 * one. Afterwards, it queues up a Soy element (see docs for queueSoyElement)
 * and then proceeds to render the Soy element.
 */
function handleSoyElement(incrementaldom, elementClassCtor, firstElementKey, data, ijData) {
  // If we're just testing truthiness, record an element but don't do anythng.
  if (incrementaldom instanceof api_idom_1.FalsinessRenderer) {
    incrementaldom.open('div');
    incrementaldom.close();
    return null;
  }
    var soyElementKey = firstElementKey + incrementaldom.getCurrentKeyStack();
    var currentPointer = incrementaldom.currentPointer();
    var el = null;
    while (currentPointer != null) {
        var maybeSoyEl = global_1.getSoyUntyped(currentPointer);
        // We cannot use the current key of the element because many layers
        // of template calls may have happened. We can only be sure that the Soy
        // element was the same if the key constructed is matching the key current
        // when the {element} command was created.
        if (maybeSoyEl instanceof elementClassCtor &&
            api_idom_1.isMatchingKey(soyElementKey, maybeSoyEl.key)) {
            el = maybeSoyEl;
            break;
        }
        currentPointer = currentPointer.nextSibling;
    }
    if (!el) {
        el = new elementClassCtor(data, ijData);
        el.key = soyElementKey;
    }
    el.queueSoyElement(incrementaldom, data);
    el.renderInternal(incrementaldom, data);
    return el;
}
exports.$$handleSoyElement = handleSoyElement;
// tslint:disable-next-line:no-any Attaching arbitrary attributes to function.
function makeHtml(idomFn) {
    idomFn.toString = function (renderer) {
        if (renderer === void 0) { renderer = defaultIdomRenderer; }
        return htmlToString(idomFn, renderer);
    };
    idomFn.toBoolean = function() {
      return isTruthy(idomFn);
    };
    idomFn.contentKind = goog_goog_soy_data_SanitizedContentKind_1.HTML;
    return idomFn;
}
exports.$$makeHtml = makeHtml;
// tslint:disable-next-line:no-any Attaching arbitrary attributes to function.
function makeAttributes(idomFn) {
    idomFn.toString = function () { return attributesToString(idomFn); };
    idomFn.toBoolean = function() {
      return isTruthy(idomFn);
    };
    idomFn.contentKind = goog_goog_soy_data_SanitizedContentKind_1.ATTRIBUTES;
    return idomFn;
}
exports.$$makeAttributes = makeAttributes;
/**
 * TODO(tomnguyen): Issue a warning in these cases so that users know that
 * expensive behavior is happening.
 */
function htmlToString(fn, renderer) {
    if (renderer === void 0) { renderer = defaultIdomRenderer; }
    var el = document.createElement('div');
    api_idom_1.patch(el, function () {
        fn(renderer);
    });
    return el.innerHTML;
}
exports.$$htmlToString = htmlToString;
function attributesFactory(fn) {
    return function () {
        incrementaldom.open('div');
        fn(defaultIdomRenderer);
        incrementaldom.applyAttrs();
        incrementaldom.close();
    };
}
/**
 * TODO(tomnguyen): Issue a warning in these cases so that users know that
 * expensive behavior is happening.
 */
function attributesToString(fn) {
    var elFn = attributesFactory(fn);
    var el = document.createElement('div');
    api_idom_1.patchOuter(el, elFn);
    var s = [];
    for (var i = 0; i < el.attributes.length; i++) {
        s.push(el.attributes[i].name + "=" + el.attributes[i].value);
    }
    // The sort is important because attribute order varies per browser.
    return s.sort().join(' ');
}
/**
 * Calls an expression in case of a function or outputs it as text content.
 */
function renderDynamicContent(incrementaldom, expr) {
    // TODO(lukes): check content kind == html
    if (typeof expr === 'function') {
        // The Soy compiler will validate the content kind of the parameter.
        expr(incrementaldom);
    }
    else {
        incrementaldom.text(String(expr));
    }
}
/**
 * Matches an HTML attribute name value pair.
 * Name is in group 1.  Value, if present, is in one of group (2,3,4)
 * depending on how it's quoted.
 *
 * This RegExp was derived from visual inspection of
 *   html.spec.whatwg.org/multipage/parsing.html#before-attribute-name-state
 * and following states.
 */
var htmlAttributeRegExp = /([^\t\n\f\r />=]+)[\t\n\f\r ]*(?:=[\t\n\f\r ]*(?:"([^"]*)"?|'([^']*)'?|([^\t\n\f\r >]*)))?/g;
function splitAttributes(attributes) {
    var nameValuePairs = [];
    String(attributes)
        .replace(htmlAttributeRegExp, function(s, name, dq, sq, uq) {
          nameValuePairs.push(
              [name, googString.unescapeEntities(dq || sq || uq || '')]);
          return ' ';
        });
    return nameValuePairs;
}
/**
 * Calls an expression in case of a function or outputs it as text content.
 */
function callDynamicAttributes(incrementaldom,
// tslint:disable-next-line:no-any
expr, data, ij) {
    // tslint:disable-next-line:no-any Attaching arbitrary attributes to function.
    var type = expr.contentKind;
    if (type === goog_goog_soy_data_SanitizedContentKind_1.ATTRIBUTES) {
        expr(incrementaldom, data, ij);
    }
    else {
        var val = void 0;
        if (type === goog_goog_soy_data_SanitizedContentKind_1.HTML) {
            // This effectively negates the value of splitting a string. However,
            // This can be removed if Soy decides to treat attribute printing
            // and attribute names differently.
            val = soy.$$filterHtmlAttributes(htmlToString(function () {
                expr(defaultIdomRenderer, data, ij);
            }));
        }
        else {
            val = expr(data, ij);
        }
        printDynamicAttr(incrementaldom, val);
    }
}
exports.$$callDynamicAttributes = callDynamicAttributes;
/**
 * Prints an expression whose type is not statically known to be of type
 * "attributes". The expression is tested at runtime and evaluated depending
 * on what type it is. For example, if a string is printed in a context
 * that expects attributes, the string is evaluated dynamically to compute
 * attributes.
 */
function printDynamicAttr(incrementaldom, expr) {
    var e_1, _a;
    if (typeof expr === 'function' &&
        expr.contentKind ===
            goog_goog_soy_data_SanitizedContentKind_1.ATTRIBUTES) {
      // tslint:disable-next-line:no-any
      expr(incrementaldom);
      return;
    }
    var attributes = splitAttributes(expr.toString());
    var isExprAttribute = goog_soy_checks_1.isAttribute(expr);
    try {
        for (var attributes_1 = tslib_1.__values(attributes), attributes_1_1 = attributes_1.next(); !attributes_1_1.done; attributes_1_1 = attributes_1.next()) {
            var attribute = attributes_1_1.value;
            var attrName = isExprAttribute ? attribute[0] :
                soy.$$filterHtmlAttributes(attribute[0]);
            if (attrName === 'zSoyz') {
                incrementaldom.attr(attrName, '');
            }
            else {
                incrementaldom.attr(String(attrName), String(attribute[1]));
            }
        }
    }
    catch (e_1_1) { e_1 = { error: e_1_1 }; }
    finally {
        try {
            if (attributes_1_1 && !attributes_1_1.done && (_a = attributes_1.return)) _a.call(attributes_1);
        }
        finally { if (e_1) throw e_1.error; }
    }
}
exports.$$printDynamicAttr = printDynamicAttr;
/**
 * Calls an expression in case of a function or outputs it as text content.
 */
function callDynamicHTML(incrementaldom, expr, data, ij) {
    // tslint:disable-next-line:no-any Attaching arbitrary attributes to function.
    var type = expr.contentKind;
    if (type === goog_goog_soy_data_SanitizedContentKind_1.HTML) {
        expr(incrementaldom, data, ij);
    }
    else if (type === goog_goog_soy_data_SanitizedContentKind_1.ATTRIBUTES) {
        var val = attributesToString(function () {
            expr(defaultIdomRenderer, data, ij);
        });
        incrementaldom.text(val);
    }
    else {
        var val = expr(data, ij);
        incrementaldom.text(String(val));
    }
}
exports.$$callDynamicHTML = callDynamicHTML;
function callDynamicCss(
// tslint:disable-next-line:no-any Attaching  attributes to function.
incrementaldom, expr, data, ij) {
    var val = callDynamicText(expr, data, ij, soy.$$filterCssValue);
    incrementaldom.text(String(val));
}
exports.$$callDynamicCss = callDynamicCss;
function callDynamicJs(
// tslint:disable-next-line:no-any Attaching attributes to function.
incrementaldom, expr, data, ij) {
    var val = callDynamicText(expr, data, ij, soy.$$escapeJsValue);
    incrementaldom.text(String(val));
}
exports.$$callDynamicJs = callDynamicJs;
/**
 * Calls an expression and coerces it to a string for cases where an IDOM
 * function needs to be concatted to a string.
 */
function callDynamicText(
// tslint:disable-next-line:no-any
expr, data, ij, escFn) {
    var transformFn = escFn ? escFn : function (a) { return a; };
    // tslint:disable-next-line:no-any Attaching arbitrary attributes to function.
    var type = expr.contentKind;
    var val;
    if (type === goog_goog_soy_data_SanitizedContentKind_1.HTML) {
        val = transformFn(htmlToString(function () {
            expr(defaultIdomRenderer, data, ij);
        }));
    }
    else if (type === goog_goog_soy_data_SanitizedContentKind_1.ATTRIBUTES) {
        val = transformFn(attributesToString(function () {
            expr(defaultIdomRenderer, data, ij);
        }));
    }
    else {
        val = expr(data, ij);
    }
    return val;
}
exports.$$callDynamicText = callDynamicText;
/**
 * Prints an expression depending on its type.
 */
function print(incrementaldom, expr, isSanitizedContent) {
    if (expr instanceof goog_goog_soy_data_SanitizedHtml_1 || isSanitizedContent) {
        var content = String(expr);
        // If the string has no < or &, it's definitely not HTML. Otherwise
        // proceed with caution.
        if (content.indexOf('<') < 0 && content.indexOf('&') < 0) {
            incrementaldom.text(content);
        }
        else {
            // For HTML content we need to insert a custom element where we can place
            // the content without incremental dom modifying it.
            var el = incrementaldom.open('html-blob');
            if (el && el.__innerHTML !== content) {
                googSoy.renderHtml(el, goog_soydata_VERY_UNSAFE_1.ordainSanitizedHtml(content));
                el.__innerHTML = content;
            }
            incrementaldom.skip();
            incrementaldom.close();
        }
    }
    else {
        renderDynamicContent(incrementaldom, expr);
    }
}
exports.$$print = print;
function visitHtmlCommentNode(incrementaldom, val) {
    var currNode = incrementaldom.currentElement();
    if (!currNode) {
        return;
    }
    if (currNode.nextSibling != null &&
        currNode.nextSibling.nodeType === Node.COMMENT_NODE) {
        currNode.nextSibling.textContent = val;
        // This is the case where we are creating new DOM from an empty element.
    }
    else {
        currNode.appendChild(document.createComment(val));
    }
    incrementaldom.skipNode();
}
exports.$$visitHtmlCommentNode = visitHtmlCommentNode;
function isTruthy(expr) {
  if (!expr) return false;
  if (expr instanceof goog_goog_soy_data_SanitizedContent_1)
    return !!expr.getContent();
  // idom callbacks.
  if (typeof expr === 'function') {
    var renderer = new api_idom_1.FalsinessRenderer();
    expr(renderer);
    return renderer.didRender();
  }
  // true, numbers, strings.
  if (typeof expr !== 'object') return !!String(expr);
  // Objects, arrays.
  return true;
}
exports.$$isTruthy = isTruthy;
//#
//sourceMappingURL=data:application/json;base64,eyJ2ZXJzaW9uIjozLCJmaWxlIjoic295dXRpbHNfaWRvbS5qcyIsInNvdXJjZVJvb3QiOiIiLCJzb3VyY2VzIjpbIi4uLy4uLy4uLy4uLy4uLy4uL2phdmFzY3JpcHQvdGVtcGxhdGUvc295L3NveXV0aWxzX2lkb20udHMiXSwibmFtZXMiOltdLCJtYXBwaW5ncyI6IkFBQUE7Ozs7Ozs7Ozs7Ozs7OztHQWVHOzs7O0FBRUgsdUNBQXlDLENBQUUsZ0NBQWdDO0FBQzNFLDJGQUFtRSxDQUFDLHFDQUFxQztBQUN6RyxtR0FBMkUsQ0FBQyxxQ0FBcUM7QUFDakgscUZBQTZELENBQUMscUNBQXFDO0FBRW5HLDZDQUErQyxDQUFFLG1DQUFtQztBQUNwRiw4QkFBZ0MsQ0FBRSxnREFBZ0Q7QUFDbEYsbURBQTRDLENBQUUsd0NBQXdDO0FBQ3RGLHFFQUE2RCxDQUFFLGdEQUFnRDtBQUMvRywwRkFBaUQsQ0FBRSwrREFBK0Q7QUFFbEgsMEVBQXVHO0FBQ3ZHLDBGQUEyRTtBQW1XM0Qsc0JBbldxQiw2QkFBVSxDQW1XcEI7QUFsVzNCLHNFQUF1QztBQUV2QyxzRUFBc0U7QUFDdEUseUJBQXlCO0FBQ2xCLElBQUEsc0NBQVUsQ0FBbUI7QUFFcEMsSUFBTSxtQkFBbUIsR0FBRyxJQUFJLGlDQUFzQixFQUFFLENBQUM7QUFRekQsa0NBQWtDO0FBQ2xDLFVBQVUsQ0FBQyxTQUFTLENBQUMsR0FBRyxVQUFDLEVBQVcsRUFBRSxJQUFZLEVBQUUsS0FBVTtJQUM1RCxnQ0FBZ0M7SUFDaEMsMEVBQTBFO0lBQzFFLHdEQUF3RDtJQUN4RCxpRUFBaUU7SUFDakUsd0JBQXdCO0lBQ3hCLEVBQUUsQ0FBQyxZQUFZLENBQUMsU0FBUyxFQUFFLEtBQUssQ0FBQyxDQUFDO0lBQ2pDLEVBQXVCLENBQUMsT0FBTztRQUM1QixDQUFDLENBQUMsS0FBSyxLQUFLLEtBQUssSUFBSSxLQUFLLEtBQUssT0FBTyxJQUFJLEtBQUssS0FBSyxTQUFTLENBQUMsQ0FBQztBQUNyRSxDQUFDLENBQUM7QUFFRixrQ0FBa0M7QUFDbEMsVUFBVSxDQUFD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RSxHQUFXO0lBQ3JELElBQU0sUUFBUSxHQUFHLGNBQWMsQ0FBQyxjQUFjLEVBQUUsQ0FBQztJQUNqRCxJQUFJLENBQUMsUUFBUSxFQUFFO1FBQ2IsT0FBTztLQUNSO0lBQ0QsSUFBSSxRQUFRLENBQUMsV0FBVyxJQUFJLElBQUk7UUFDNUIsUUFBUSxDQUFDLFdBQVcsQ0FBQyxRQUFRLEtBQUssSUFBSSxDQUFDLFlBQVksRUFBRTtRQUN2RCxRQUFRLENBQUMsV0FBVyxDQUFDLFdBQVcsR0FBRyxHQUFHLENBQUM7UUFDdkMsd0VBQXdFO0tBQ3pFO1NBQU07UUFDTCxRQUFRLENBQUMsV0FBVyxDQUFDLFFBQVEsQ0FBQyxhQUFhLENBQUMsR0FBRyxDQUFDLENBQUMsQ0FBQztLQUNuRDtJQUNELGNBQWMsQ0FBQyxRQUFRLEVBQUUsQ0FBQztBQUM1QixDQUFDO0FBa0N5QixzREFBc0I7QUFoQ2hELFNBQVMsUUFBUSxDQUFDLElBQWE7SUFDN0IsSUFBSSxDQUFDLElBQUk7UUFBRSxPQUFPLEtBQUssQ0FBQztJQUN4QixJQUFJLElBQUksaURBQTRCO1FBQUUsT0FBTyxDQUFDLENBQUMsSUFBSSxDQUFDLFVBQVUsRUFBRSxDQUFDO0lBRWpFLGtCQUFrQjtJQUNsQixJQUFJLE9BQU8sSUFBSSxLQUFLLFVBQVUsRUFBRTtRQUM5QixJQUFNLFFBQVEsR0FBRyxJQUFJLDRCQUFpQixFQUFFLENBQUM7UUFDekMsSUFBSSxDQUFDLFFBQVEsQ0FBQyxDQUFDO1FBQ2YsT0FBTyxRQUFRLENBQUMsU0FBUyxFQUFFLENBQUM7S0FDN0I7SUFFRCwwQkFBMEI7SUFDMUIsSUFBSSxPQUFPLElBQUksS0FBSyxRQUFRO1FBQUUsT0FBTyxDQUFDLENBQUMsTUFBTSxDQUFDLElBQUksQ0FBQyxDQUFDO0lBRXBELG1CQUFtQjtJQUNuQixPQUFPLElBQUksQ0FBQztBQUNkLENBQUM7QUFJYSw4QkFBVSIsInNvdXJjZXNDb250ZW50IjpbIi8qXG4gKiBAZmlsZW92ZXJ2aWV3IEhlbHBlciB1dGlsaXRpZXMgZm9yIGluY3JlbWVudGFsIGRvbSBjb2RlIGdlbmVyYXRpb24gaW4gU295LlxuICogQ29weXJpZ2h0IDIwMTYgR29vZ2xlIEluYy5cbiAqXG4gKiBMaWNlbnNlZCB1bmRlciB0aGUgQXBhY2hlIExpY2Vuc2UsIFZlcnNpb24gMi4wICh0aGUgXCJMaWNlbnNlXCIpO1xuICogeW91IG1heSBub3QgdXNlIHRoaXMgZmlsZSBleGNlcHQgaW4gY29tcGxpYW5jZSB3aXRoIHRoZSBMaWNlbnNlLlxuICogWW91IG1heSBvYnRhaW4gYSBjb3B5IG9mIHRoZSBMaWNlbnNlIGF0XG4gKlxuICogICAgIGh0dHA6Ly93d3cuYXBhY2hlLm9yZy9saWNlbnNlcy9MSUNFTlNFLTIuMFxuICpcbiAqIFVubGVzcyByZXF1aXJlZCBieSBhcHBsaWNhYmxlIGxhdyBvciBhZ3JlZWQgdG8gaW4gd3JpdGluZywgc29mdHdhcmVcbiAqIGRpc3RyaWJ1dGVkIHVuZGVyIHRoZSBMaWNlbnNlIGlzIGRpc3RyaWJ1dGVkIG9uIGFuIFwiQVMgSVNcIiBCQVNJUyxcbiAqIFdJVEhPVVQgV0FSUkFOVElFUyBPUiBDT05ESVRJT05TIE9GIEFOWSBLSU5ELCBlaXRoZXIgZXhwcmVzcyBvciBpbXBsaWVkLlxuICogU2VlIHRoZSBMaWNlbnNlIGZvciB0aGUgc3BlY2lmaWMgbGFuZ3VhZ2UgZ292ZXJuaW5nIHBlcm1pc3Npb25zIGFuZFxuICogbGltaXRhdGlvbnMgdW5kZXIgdGhlIExpY2Vuc2UuXG4gKi9cblxuaW1wb3J0ICogYXMgZ29vZ1NveSBmcm9tICdnb29nOmdvb2cuc295JzsgIC8vIGZyb20gLy9qYXZhc2NyaXB0L2Nsb3N1cmUvc295XG5pbXBvcnQgU2FuaXRpemVkQ29udGVudCBmcm9tICdnb29nOmdvb2cuc295LmRhdGEuU2FuaXRpemVkQ29udGVudCc7IC8vIGZyb20gLy9qYXZhc2NyaXB0L2Nsb3N1cmUvc295OmRhdGFcbmltcG9ydCBTYW5pdGl6ZWRDb250ZW50S2luZCBmcm9tICdnb29nOmdvb2cuc295LmRhdGEuU2FuaXRpemVkQ29udGVudEtpbmQnOyAvLyBmcm9tIC8vamF2YXNjcmlwdC9jbG9zdXJlL3NveTpkYXRhXG5pbXBvcnQgU2FuaXRpemVkSHRtbCBmcm9tICdnb29nOmdvb2cuc295LmRhdGEuU2FuaXRpemVkSHRtbCc7IC8vIGZyb20gLy9qYXZhc2NyaXB0L2Nsb3N1cmUvc295OmRhdGFcbmltcG9ydCBTYW5pdGl6ZWRIdG1sQXR0cmlidXRlIGZyb20gJ2dvb2c6Z29vZy5zb3kuZGF0YS5TYW5pdGl6ZWRIdG1sQXR0cmlidXRlJzsgLy8gZnJvbSAvL2phdmFzY3JpcHQvY2xvc3VyZS9zb3k6ZGF0YVxuaW1wb3J0ICogYXMgZ29vZ1N0cmluZyBmcm9tICdnb29nOmdvb2cuc3RyaW5nJzsgIC8vIGZyb20gLy9qYXZhc2NyaXB0L2Nsb3N1cmUvc3RyaW5nXG5pbXBvcnQgKiBhcyBzb3kgZnJvbSAnZ29vZzpzb3knOyAgLy8gZnJvbSAvL2phdmFzY3JpcHQvdGVtcGxhdGUvc295OnNveV91c2Vnb29nX2pzXG5pbXBvcnQge2lzQXR0cmlidXRlfSBmcm9tICdnb29nOnNveS5jaGVja3MnOyAgLy8gZnJvbSAvL2phdmFzY3JpcHQvdGVtcGxhdGUvc295OmNoZWNrc1xuaW1wb3J0IHtvcmRhaW5TYW5pdGl6ZWRIdG1sfSBmcm9tICdnb29nOnNveWRhdGEuVkVSWV9VTlNBRkUnOyAgLy8gZnJvbSAvL2phdmFzY3JpcHQvdGVtcGxhdGUvc295OnNveV91c2Vnb29nX2pzXG5pbXBvcnQgKiBhcyBpbmNyZW1lbnRhbGRvbSBmcm9tICdpbmNyZW1lbnRhbGRvbSc7ICAvLyBmcm9tIC8vdGhpcmRfcGFydHkvamF2YXNjcmlwdC9pbmNyZW1lbnRhbF9kb206aW5jcmVtZW50YWxkb21cblxuaW1wb3J0IHtGYWxzaW5lc3NSZW5kZXJlciwgSW5jcmVtZW50YWxEb21SZW5kZXJlciwgaXNNYXRjaGluZ0tleSwgcGF0Y2gsIHBhdGNoT3V0ZXJ9IGZyb20gJy4vYXBpX2lkb20nO1xuaW1wb3J0IHtJZG9tRnVuY3Rpb24sIFBhdGNoRnVuY3Rpb24sIFNveUVsZW1lbnR9IGZyb20gJy4vZWxlbWVudF9saWJfaWRvbSc7XG5pbXBvcnQge2dldFNveVVudHlwZWR9IGZyb20gJy4vZ2xvYmFsJztcblxuLy8gRGVjbGFyZSBwcm9wZXJ0aWVzIHRoYXQgbmVlZCB0byBiZSBhcHBsaWVkIG5vdCBhcyBhdHRyaWJ1dGVzIGJ1dCBhc1xuLy8gYWN0dWFsIERPTSBwcm9wZXJ0aWVzLlxuY29uc3Qge2F0dHJpYnV0ZXN9ID0gaW5jcmVtZW50YWxkb207XG5cbmNvbnN0IGRlZmF1bHRJZG9tUmVuZGVyZXIgPSBuZXcgSW5jcmVtZW50YWxEb21SZW5kZXJlcigpO1xuXG50eXBlIElkb21UZW1wbGF0ZTxBLCBCPiA9XG4gICAgKGlkb206IEluY3JlbWVudGFsRG9tUmVuZGVyZXIsIHBhcmFtczogQSwgaWpEYXRhOiBCKSA9PiB2b2lkO1xudHlwZSBTb3lUZW1wbGF0ZTxBLCBCPiA9IChwYXJhbXM6IEEsIGlqRGF0YTogQikgPT4gc3RyaW5nfFNhbml0aXplZENvbnRlbnQ7XG50eXBlIExldEZ1bmN0aW9uID0gKGlkb206IEluY3JlbWVudGFsRG9tUmVuZGVyZXIpID0+IHZvaWQ7XG50eXBlIFRlbXBsYXRlPEEsIEI+ID0gSWRvbVRlbXBsYXRlPEEsIEI+fFNveVRlbXBsYXRlPEEsIEI+O1xuXG4vLyB0c2xpbnQ6ZGlzYWJsZS1uZXh0LWxpbmU6bm8tYW55XG5hdHRyaWJ1dGVzWydjaGVja2VkJ10gPSAoZWw6IEVsZW1lbnQsIG5hbWU6IHN0cmluZywgdmFsdWU6IGFueSkgPT4ge1xuICAvLyBXZSBkb24ndCB1c2UgISF2YWx1ZSBiZWNhdXNlOlxuICAvLyAxLiBJZiB2YWx1ZSBpcyAnJyAodGhpcyBpcyB0aGUgY2FzZSB3aGVyZSBhIHVzZXIgdXNlcyA8ZGl2IGNoZWNrZWQgLz4pLFxuICAvLyAgICB0aGUgY2hlY2tlZCB2YWx1ZSBzaG91bGQgYmUgdHJ1ZSwgYnV0ICcnIGlzIGZhbHN5LlxuICAvLyAyLiBJZiB2YWx1ZSBpcyAnZmFsc2UnLCB0aGUgY2hlY2tlZCB2YWx1ZSBzaG91bGQgYmUgZmFsc2UsIGJ1dFxuICAvLyAgICAnZmFsc2UnIGlzIHRydXRoeS5cbiAgZWwuc2V0QXR0cmlidXRlKCdjaGVja2VkJywgdmFsdWUpO1xuICAoZWwgYXMgSFRNTElucHV0RWxlbWVudCkuY2hlY2tlZCA9XG4gICAgICAhKHZhbHVlID09PSBmYWxzZSB8fCB2YWx1ZSA9PT0gJ2ZhbHNlJyB8fCB2YWx1ZSA9PT0gdW5kZWZpbmVkKTtcbn07XG5cbi8vIHRzbGludDpkaXNhYmxlLW5leHQtbGluZTpuby1hbnlcbmF0dHJpYnV0ZXNbJ3ZhbHVlJ10gPSAoZWw6IEVsZW1lbnQsIG5hbWU6IHN0cmluZywgdmFsdWU6IGFueSkgPT4ge1xuICAoZWwgYXMgSFRNTElucHV0RWxlbWVudCkudmFsdWUgPSB2YWx1ZTtcbiAgZWwuc2V0QXR0cmlidXRlKCd2YWx1ZScsIHZhbHVlKTtcbn07XG5cbi8vIFNveSB1c2VzIHRoZSB7a2V5fSBjb21tYW5kIHN5bnRheCwgcmF0aGVyIHRoYW4gSFRNTCBhdHRyaWJ1dGVzLCB0b1xuLy8gaW5kaWNhdGUgZWxlbWVudCBrZXlzLlxuaW5jcmVtZW50YWxkb20uc2V0S2V5QXR0cmlidXRlTmFtZSgnc295LXNlcnZlci1rZXknKTtcblxuLyoqXG4gKiBUcmllcyB0byBmaW5kIGFuIGV4aXN0aW5nIFNveSBlbGVtZW50LCBpZiBpdCBleGlzdHMuIE90aGVyd2lzZSwgaXQgY3JlYXRlc1xuICogb25lLiBBZnRlcndhcmRzLCBpdCBxdWV1ZXMgdXAgYSBTb3kgZWxlbWVudCAoc2VlIGRvY3MgZm9yIHF1ZXVlU295RWxlbWVudClcbiAqIGFuZCB0aGVuIHByb2NlZWRzIHRvIHJlbmRlciB0aGUgU295IGVsZW1lbnQuXG4gKi9cbmZ1bmN0aW9uIGhhbmRsZVNveUVsZW1lbnQ8REFUQSwgVCBleHRlbmRzIFNveUVsZW1lbnQ8REFUQSwge30+PihcbiAgICBpbmNyZW1lbnRhbGRvbTogSW5jcmVtZW50YWxEb21SZW5kZXJlcixcbiAgICBlbGVtZW50Q2xhc3NDdG9yOiBuZXcgKGRhdGE6IERBVEEsIGlqOiB1bmtub3duKSA9PiBULFxuICAgIGZpcnN0RWxlbWVudEtleTogc3RyaW5nLCBkYXRhOiBEQVRBLCBpakRhdGE6IHVua25vd24pIHtcbiAgLy8gSWYgd2UncmUganVzdCB0ZXN0aW5nIHRydXRoaW5lc3MsIHJlY29yZCBhbiBlbGVtZW50IGJ1dCBkb24ndCBkbyBhbnl0aG5nLlxuICBpZiAoaW5jcmVtZW50YWxkb20gaW5zdGFuY2VvZiBGYWxzaW5lc3NSZW5kZXJlcikge1xuICAgIGluY3JlbWVudGFsZG9tLm9wZW4oJ2RpdicpO1xuICAgIGluY3JlbWVudGFsZG9tLmNsb3NlKCk7XG4gICAgcmV0dXJuIG51bGw7XG4gIH1cbiAgY29uc3Qgc295RWxlbWVudEtleSA9IGZpcnN0RWxlbWVudEtleSArIGluY3JlbWVudGFsZG9tLmdldEN1cnJlbnRLZXlTdGFjaygpO1xuICBsZXQgY3VycmVudFBvaW50ZXIgPSBpbmNyZW1lbnRhbGRvbS5jdXJyZW50UG9pbnRlcigpO1xuICBsZXQgZWw6IFR8bnVsbCA9IG51bGw7XG4gIHdoaWxlIChjdXJyZW50UG9pbnRlciAhPSBudWxsKSB7XG4gICAgY29uc3QgbWF5YmVTb3lFbCA9IGdldFNveVVudHlwZWQoY3VycmVudFBvaW50ZXIpIGFzIFQ7XG4gICAgLy8gV2UgY2Fubm90IHVzZSB0aGUgY3VycmVudCBrZXkgb2YgdGhlIGVsZW1lbnQgYmVjYXVzZSBtYW55IGxheWVyc1xuICAgIC8vIG9mIHRlbXBsYXRlIGNhbGxzIG1heSBoYXZlIGhhcHBlbmVkLiBXZSBjYW4gb25seSBiZSBzdXJlIHRoYXQgdGhlIFNveVxuICAgIC8vIGVsZW1lbnQgd2FzIHRoZSBzYW1lIGlmIHRoZSBrZXkgY29uc3RydWN0ZWQgaXMgbWF0Y2hpbmcgdGhlIGtleSBjdXJyZW50XG4gICAgLy8gd2hlbiB0aGUge2VsZW1lbnR9IGNvbW1hbmQgd2FzIGNyZWF0ZWQuXG4gICAgaWYgKG1heWJlU295RWwgaW5zdGFuY2VvZiBlbGVtZW50Q2xhc3NDdG9yICYmXG4gICAgICAgIGlzTWF0Y2hpbmdLZXkoc295RWxlbWVudEtleSwgbWF5YmVTb3lFbC5rZXkpKSB7XG4gICAgICBlbCA9IG1heWJlU295RWw7XG4gICAgICBicmVhaztcbiAgICB9XG4gICAgY3VycmVudFBvaW50ZXIgPSBjdXJyZW50UG9pbnRlci5uZXh0U2libGluZztcbiAgfVxuICBpZiAoIWVsKSB7XG4gICAgZWwgPSBuZXcgZWxlbWVudENsYXNzQ3RvcihkYXRhLCBpakRhdGEpO1xuICAgIGVsLmtleSA9IHNveUVsZW1lbnRLZXk7XG4gIH1cbiAgZWwucXVldWVTb3lFbGVtZW50KGluY3JlbWVudGFsZG9tLCBkYXRhKTtcbiAgZWwucmVuZGVySW50ZXJuYWwoaW5jcmVtZW50YWxkb20sIGRhdGEpO1xuICByZXR1cm4gZWw7XG59XG5cbi8vIHRzbGludDpkaXNhYmxlLW5leHQtbGluZTpuby1hbnkgQXR0YWNoaW5nIGFyYml0cmFyeSBhdHRyaWJ1dGVzIHRvIGZ1bmN0aW9uLlxuZnVuY3Rpb24gbWFrZUh0bWwoaWRvbUZuOiBhbnkpOiBJZG9tRnVuY3Rpb24ge1xuICBpZG9tRm4udG9TdHJpbmcgPSAocmVuZGVyZXI6IEluY3JlbWVudGFsRG9tUmVuZGVyZXIgPSBkZWZhdWx0SWRvbVJlbmRlcmVyKSA9PlxuICAgICAgaHRtbFRvU3RyaW5nKGlkb21GbiwgcmVuZGVyZXIpO1xuICBpZG9tRm4udG9Cb29sZWFuID0gKCkgPT4gaXNUcnV0aHkoaWRvbUZuKTtcbiAgaWRvbUZuLmNvbnRlbnRLaW5kID0gU2FuaXRpemVkQ29udGVudEtpbmQuSFRNTDtcbiAgcmV0dXJuIGlkb21GbiBhcyBJZG9tRnVuY3Rpb247XG59XG5cbi8vIHRzbGludDpkaXNhYmxlLW5leHQtbGluZTpuby1hbnkgQXR0YWNoaW5nIGFyYml0cmFyeSBhdHRyaWJ1dGVzIHRvIGZ1bmN0aW9uLlxuZnVuY3Rpb24gbWFrZUF0dHJpYnV0ZXMoaWRvbUZuOiBhbnkpOiBJZG9tRnVuY3Rpb24ge1xuICBpZG9tRm4udG9TdHJpbmcgPSAoKSA9PiBhdHRyaWJ1dGVzVG9TdHJpbmcoaWRvbUZuKTtcbiAgaWRvbUZuLnRvQm9vbGVhbiA9ICgpID0+IGlzVHJ1dGh5KGlkb21Gbik7XG4gIGlkb21Gbi5jb250ZW50S2luZCA9IFNhbml0aXplZENvbnRlbnRLaW5kLkFUVFJJQlVURVM7XG4gIHJldHVybiBpZG9tRm4gYXMgSWRvbUZ1bmN0aW9uO1xufVxuXG4vKipcbiAqIFRPRE8odG9tbmd1eWVuKTogSXNzdWUgYSB3YXJuaW5nIGluIHRoZXNlIGNhc2VzIHNvIHRoYXQgdXNlcnMga25vdyB0aGF0XG4gKiBleHBlbnNpdmUgYmVoYXZpb3IgaXMgaGFwcGVuaW5nLlxuICovXG5mdW5jdGlvbiBodG1sVG9TdHJpbmcoXG4gICAgZm46IExldEZ1bmN0aW9uLCByZW5kZXJlcjogSW5jcmVtZW50YWxEb21SZW5kZXJlciA9IGRlZmF1bHRJZG9tUmVuZGVyZXIpIHtcbiAgY29uc3QgZWwgPSBkb2N1bWVudC5jcmVhdGVFbGVtZW50KCdkaXYnKTtcbiAgcGF0Y2goZWwsICgpID0+IHtcbiAgICBmbihyZW5kZXJlcik7XG4gIH0pO1xuICByZXR1cm4gZWwuaW5uZXJIVE1MO1xufVxuXG5mdW5jdGlvbiBhdHRyaWJ1dGVzRmFjdG9yeShmbjogUGF0Y2hGdW5jdGlvbik6IFBhdGNoRnVuY3Rpb24ge1xuICByZXR1cm4gKCkgPT4ge1xuICAgIGluY3JlbWVudGFsZG9tLm9wZW4oJ2RpdicpO1xuICAgIGZuKGRlZmF1bHRJZG9tUmVuZGVyZXIpO1xuICAgIGluY3JlbWVudGFsZG9tLmFwcGx5QXR0cnMoKTtcbiAgICBpbmNyZW1lbnRhbGRvbS5jbG9zZSgpO1xuICB9O1xufVxuXG4vKipcbiAqIFRPRE8odG9tbmd1eWVuKTogSXNzdWUgYSB3YXJuaW5nIGluIHRoZXNlIGNhc2VzIHNvIHRoYXQgdXNlcnMga25vdyB0aGF0XG4gKiBleHBlbnNpdmUgYmVoYXZpb3IgaXMgaGFwcGVuaW5nLlxuICovXG5mdW5jdGlvbiBhdHRyaWJ1dGVzVG9TdHJpbmcoZm46IFBhdGNoRnVuY3Rpb24pOiBzdHJpbmcge1xuICBjb25zdCBlbEZuID0gYXR0cmlidXRlc0ZhY3RvcnkoZm4pO1xuICBjb25zdCBlbCA9IGRvY3VtZW50LmNyZWF0ZUVsZW1lbnQoJ2RpdicpO1xuICBwYXRjaE91dGVyKGVsLCBlbEZuKTtcbiAgY29uc3Qgczogc3RyaW5nW10gPSBbXTtcbiAgZm9yIChsZXQgaSA9IDA7IGkgPCBlbC5hdHRyaWJ1dGVzLmxlbmd0aDsgaSsrKSB7XG4gICAgcy5wdXNoKGAke2VsLmF0dHJpYnV0ZXNbaV0ubmFtZX09JHtlbC5hdHRyaWJ1dGVzW2ldLnZhbHVlfWApO1xuICB9XG4gIC8vIFRoZSBzb3J0IGlzIGltcG9ydGFudCBiZWNhdXNlIGF0dHJpYnV0ZSBvcmRlciB2YXJpZXMgcGVyIGJyb3dzZXIuXG4gIHJldHVybiBzLnNvcnQoKS5qb2luKCcgJyk7XG59XG5cbi8qKlxuICogQ2FsbHMgYW4gZXhwcmVzc2lvbiBpbiBjYXNlIG9mIGEgZnVuY3Rpb24gb3Igb3V0cHV0cyBpdCBhcyB0ZXh0IGNvbnRlbnQuXG4gKi9cbmZ1bmN0aW9uIHJlbmRlckR5bmFtaWNDb250ZW50KFxuICAgIGluY3JlbWVudGFsZG9tOiBJbmNyZW1lbnRhbERvbVJlbmRlcmVyLCBleHByOiB1bmtub3duKSB7XG4gIC8vIFRPRE8obHVrZXMpOiBjaGVjayBjb250ZW50IGtpbmQgPT0gaHRtbFxuICBpZiAodHlwZW9mIGV4cHIgPT09ICdmdW5jdGlvbicpIHtcbiAgICAvLyBUaGUgU295IGNvbXBpbGVyIHdpbGwgdmFsaWRhdGUgdGhlIGNvbnRlbnQga2luZCBvZiB0aGUgcGFyYW1ldGVyLlxuICAgIGV4cHIoaW5jcmVtZW50YWxkb20pO1xuICB9IGVsc2Uge1xuICAgIGluY3JlbWVudGFsZG9tLnRleHQoU3RyaW5nKGV4cHIpKTtcbiAgfVxufVxuXG4vKipcbiAqIE1hdGNoZXMgYW4gSFRNTCBhdHRyaWJ1dGUgbmFtZSB2YWx1ZSBwYWlyLlxuICogTmFtZSBpcyBpbiBncm91cCAxLiAgVmFsdWUsIGlmIHByZXNlbnQsIGlzIGluIG9uZSBvZiBncm91cCAoMiwzLDQpXG4gKiBkZXBlbmRpbmcgb24gaG93IGl0J3MgcXVvdGVkLlxuICpcbiAqIFRoaXMgUmVnRXhwIHdhcyBkZXJpdmVkIGZyb20gdmlzdWFsIGluc3BlY3Rpb24gb2ZcbiAqICAgaHRtbC5zcGVjLndoYXR3Zy5vcmcvbXVsdGlwYWdlL3BhcnNpbmcuaHRtbCNiZWZvcmUtYXR0cmlidXRlLW5hbWUtc3RhdGVcbiAqIGFuZCBmb2xsb3dpbmcgc3RhdGVzLlxuICovXG5jb25zdCBodG1sQXR0cmlidXRlUmVnRXhwOiBSZWdFeHAgPVxuICAgIC8oW15cXHRcXG5cXGZcXHIgLz49XSspW1xcdFxcblxcZlxcciBdKig/Oj1bXFx0XFxuXFxmXFxyIF0qKD86XCIoW15cIl0qKVwiP3wnKFteJ10qKSc/fChbXlxcdFxcblxcZlxcciA+XSopKSk/L2c7XG5cbmZ1bmN0aW9uIHNwbGl0QXR0cmlidXRlcyhhdHRyaWJ1dGVzOiBzdHJpbmcpIHtcbiAgY29uc3QgbmFtZVZhbHVlUGFpcnM6IHN0cmluZ1tdW10gPSBbXTtcbiAgU3RyaW5nKGF0dHJpYnV0ZXMpLnJlcGxhY2UoaHRtbEF0dHJpYnV0ZVJlZ0V4cCwgKHMsIG5hbWUsIGRxLCBzcSwgdXEpID0+IHtcbiAgICBuYW1lVmFsdWVQYWlycy5wdXNoKFxuICAgICAgICBbbmFtZSwgZ29vZ1N0cmluZy51bmVzY2FwZUVudGl0aWVzKGRxIHx8IHNxIHx8IHVxIHx8ICcnKV0pO1xuICAgIHJldHVybiAnICc7XG4gIH0pO1xuICByZXR1cm4gbmFtZVZhbHVlUGFpcnM7XG59XG5cbi8qKlxuICogQ2FsbHMgYW4gZXhwcmVzc2lvbiBpbiBjYXNlIG9mIGEgZnVuY3Rpb24gb3Igb3V0cHV0cyBpdCBhcyB0ZXh0IGNvbnRlbnQuXG4gKi9cbmZ1bmN0aW9uIGNhbGxEeW5hbWljQXR0cmlidXRlczxBLCBCPihcbiAgICBpbmNyZW1lbnRhbGRvbTogSW5jcmVtZW50YWxEb21SZW5kZXJlcixcbiAgICAvLyB0c2xpbnQ6ZGlzYWJsZS1uZXh0LWxpbmU6bm8tYW55XG4gICAgZXhwcjogVGVtcGxhdGU8QSwgQj4sIGRhdGE6IEEsIGlqOiBCKSB7XG4gIC8vIHRzbGludDpkaXNhYmxlLW5leHQtbGluZTpuby1hbnkgQXR0YWNoaW5nIGFyYml0cmFyeSBhdHRyaWJ1dGVzIHRvIGZ1bmN0aW9uLlxuICBjb25zdCB0eXBlID0gKGV4cHIgYXMgYW55IGFzIElkb21GdW5jdGlvbikuY29udGVudEtpbmQ7XG4gIGlmICh0eXBlID09PSBTYW5pdGl6ZWRDb250ZW50S2luZC5BVFRSSUJVVEVTKSB7XG4gICAgKGV4cHIgYXMgSWRvbVRlbXBsYXRlPEEsIEI+KShpbmNyZW1lbnRhbGRvbSwgZGF0YSwgaWopO1xuICB9IGVsc2Uge1xuICAgIGxldCB2YWw6IHN0cmluZ3xTYW5pdGl6ZWRIdG1sQXR0cmlidXRlO1xuICAgIGlmICh0eXBlID09PSBTYW5pdGl6ZWRDb250ZW50S2luZC5IVE1MKSB7XG4gICAgICAvLyBUaGlzIGVmZmVjdGl2ZWx5IG5lZ2F0ZXMgdGhlIHZhbHVlIG9mIHNwbGl0dGluZyBhIHN0cmluZy4gSG93ZXZlcixcbiAgICAgIC8vIFRoaXMgY2FuIGJlIHJlbW92ZWQgaWYgU295IGRlY2lkZXMgdG8gdHJlYXQgYXR0cmlidXRlIHByaW50aW5nXG4gICAgICAvLyBhbmQgYXR0cmlidXRlIG5hbWVzIGRpZmZlcmVudGx5LlxuICAgICAgdmFsID0gc295LiQkZmlsdGVySHRtbEF0dHJpYnV0ZXMoaHRtbFRvU3RyaW5nKCgpID0+IHtcbiAgICAgICAgKGV4cHIgYXMgSWRvbVRlbXBsYXRlPEEsIEI+KShkZWZhdWx0SWRvbVJlbmRlcmVyLCBkYXRhLCBpaik7XG4gICAgICB9KSk7XG4gICAgfSBlbHNlIHtcbiAgICAgIHZhbCA9IChleHByIGFzIFNveVRlbXBsYXRlPEEsIEI+KShkYXRhLCBpaikgYXMgU2FuaXRpemVkSHRtbEF0dHJpYnV0ZTtcbiAgICB9XG4gICAgcHJpbnREeW5hbWljQXR0cihpbmNyZW1lbnRhbGRvbSwgdmFsKTtcbiAgfVxufVxuXG4vKipcbiAqIFByaW50cyBhbiBleHByZXNzaW9uIHdob3NlIHR5cGUgaXMgbm90IHN0YXRpY2FsbHkga25vd24gdG8gYmUgb2YgdHlwZVxuICogXCJhdHRyaWJ1dGVzXCIuIFRoZSBleHByZXNzaW9uIGlzIHRlc3RlZCBhdCBydW50aW1lIGFuZCBldmFsdWF0ZWQgZGVwZW5kaW5nXG4gKiBvbiB3aGF0IHR5cGUgaXQgaXMuIEZvciBleGFtcGxlLCBpZiBhIHN0cmluZyBpcyBwcmludGVkIGluIGEgY29udGV4dFxuICogdGhhdCBleHBlY3RzIGF0dHJpYnV0ZXMsIHRoZSBzdHJpbmcgaXMgZXZhbHVhdGVkIGR5bmFtaWNhbGx5IHRvIGNvbXB1dGVcbiAqIGF0dHJpYnV0ZXMuXG4gKi9cbmZ1bmN0aW9uIHByaW50RHluYW1pY0F0dHIoXG4gICAgaW5jcmVtZW50YWxkb206IEluY3JlbWVudGFsRG9tUmVuZGVyZXIsXG4gICAgZXhwcjogU2FuaXRpemVkSHRtbEF0dHJpYnV0ZXxzdHJpbmd8Ym9vbGVhbnxJZG9tRnVuY3Rpb24pIHtcbiAgaWYgKGdvb2cuaXNGdW5jdGlvbihleHByKSAmJlxuICAgICAgKGV4cHIgYXMgSWRvbUZ1bmN0aW9uKS5jb250ZW50S2luZCA9PT0gU2FuaXRpemVkQ29udGVudEtpbmQuQVRUUklCVVRFUykge1xuICAgIC8vIHRzbGludDpkaXNhYmxlLW5leHQtbGluZTpuby1hbnlcbiAgICAoZXhwciBhcyBhbnkgYXMgTGV0RnVuY3Rpb24pKGluY3JlbWVudGFsZG9tKTtcbiAgICByZXR1cm47XG4gIH1cbiAgY29uc3QgYXR0cmlidXRlcyA9IHNwbGl0QXR0cmlidXRlcyhleHByLnRvU3RyaW5nKCkpO1xuICBjb25zdCBpc0V4cHJBdHRyaWJ1dGUgPSBpc0F0dHJpYnV0ZShleHByKTtcbiAgZm9yIChjb25zdCBhdHRyaWJ1dGUgb2YgYXR0cmlidXRlcykge1xuICAgIGNvbnN0IGF0dHJOYW1lID0gaXNFeHByQXR0cmlidXRlID8gYXR0cmlidXRlWzBdIDpcbiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIHNveS4kJGZpbHRlckh0bWxBdHRyaWJ1dGVzKGF0dHJpYnV0ZVswXSk7XG4gICAgaWYgKGF0dHJOYW1lID09PSAnelNveXonKSB7XG4gICAgICBpbmNyZW1lbnRhbGRvbS5hdHRyKGF0dHJOYW1lLCAnJyk7XG4gICAgfSBlbHNlIHtcbiAgICAgIGluY3JlbWVudGFsZG9tLmF0dHIoU3RyaW5nKGF0dHJOYW1lKSwgU3RyaW5nKGF0dHJpYnV0ZVsxXSkpO1xuICAgIH1cbiAgfVxufVxuXG4vKipcbiAqIENhbGxzIGFuIGV4cHJlc3Npb24gaW4gY2FzZSBvZiBhIGZ1bmN0aW9uIG9yIG91dHB1dHMgaXQgYXMgdGV4dCBjb250ZW50LlxuICovXG5mdW5jdGlvbiBjYWxsRHluYW1pY0hUTUw8QSwgQj4oXG4gICAgaW5jcmVtZW50YWxkb206IEluY3JlbWVudGFsRG9tUmVuZGVyZXIsIGV4cHI6IFRlbXBsYXRlPEEsIEI+LCBkYXRhOiBBLFxuICAgIGlqOiBCKSB7XG4gIC8vIHRzbGludDpkaXNhYmxlLW5leHQtbGluZTpuby1hbnkgQXR0YWNoaW5nIGFyYml0cmFyeSBhdHRyaWJ1dGVzIHRvIGZ1bmN0aW9uLlxuICBjb25zdCB0eXBlID0gKGV4cHIgYXMgYW55IGFzIElkb21GdW5jdGlvbikuY29udGVudEtpbmQ7XG4gIGlmICh0eXBlID09PSBTYW5pdGl6ZWRDb250ZW50S2luZC5IVE1MKSB7XG4gICAgKGV4cHIgYXMgSWRvbVRlbXBsYXRlPEEsIEI+KShpbmNyZW1lbnRhbGRvbSwgZGF0YSwgaWopO1xuICB9IGVsc2UgaWYgKHR5cGUgPT09IFNhbml0aXplZENvbnRlbnRLaW5kLkFUVFJJQlVURVMpIHtcbiAgICBjb25zdCB2YWwgPSBhdHRyaWJ1dGVzVG9TdHJpbmcoKCkgPT4ge1xuICAgICAgKGV4cHIgYXMgSWRvbVRlbXBsYXRlPEEsIEI+KShkZWZhdWx0SWRvbVJlbmRlcmVyLCBkYXRhLCBpaik7XG4gICAgfSk7XG4gICAgaW5jcmVtZW50YWxkb20udGV4dCh2YWwpO1xuICB9IGVsc2Uge1xuICAgIGNvbnN0IHZhbCA9IChleHByIGFzIFNveVRlbXBsYXRlPEEsIEI+KShkYXRhLCBpaik7XG4gICAgaW5jcmVtZW50YWxkb20udGV4dChTdHJpbmcodmFsKSk7XG4gIH1cbn1cblxuZnVuY3Rpb24gY2FsbER5bmFtaWNDc3M8QSwgQj4oXG4gICAgLy8gdHNsaW50OmRpc2FibGUtbmV4dC1saW5lOm5vLWFueSBBdHRhY2hpbmcgIGF0dHJpYnV0ZXMgdG8gZnVuY3Rpb24uXG4gICAgaW5jcmVtZW50YWxkb206IEluY3JlbWVudGFsRG9tUmVuZGVyZXIsIGV4cHI6IChhOiBBLCBiOiBCKSA9PiBhbnksIGRhdGE6IEEsXG4gICAgaWo6IEIpIHtcbiAgY29uc3QgdmFsID0gY2FsbER5bmFtaWNUZXh0PEEsIEI+KGV4cHIsIGRhdGEsIGlqLCBzb3kuJCRmaWx0ZXJDc3NWYWx1ZSk7XG4gIGluY3JlbWVudGFsZG9tLnRleHQoU3RyaW5nKHZhbCkpO1xufVxuXG5mdW5jdGlvbiBjYWxsRHluYW1pY0pzPEEsIEI+KFxuICAgIC8vIHRzbGludDpkaXNhYmxlLW5leHQtbGluZTpuby1hbnkgQXR0YWNoaW5nIGF0dHJpYnV0ZXMgdG8gZnVuY3Rpb24uXG4gICAgaW5jcmVtZW50YWxkb206IEluY3JlbWVudGFsRG9tUmVuZGVyZXIsIGV4cHI6IChhOiBBLCBiOiBCKSA9PiBhbnksIGRhdGE6IEEsXG4gICAgaWo6IEIpIHtcbiAgY29uc3QgdmFsID0gY2FsbER5bmFtaWNUZXh0PEEsIEI+KGV4cHIsIGRhdGEsIGlqLCBzb3kuJCRlc2NhcGVKc1ZhbHVlKTtcbiAgaW5jcmVtZW50YWxkb20udGV4dChTdHJpbmcodmFsKSk7XG59XG5cbi8qKlxuICogQ2FsbHMgYW4gZXhwcmVzc2lvbiBhbmQgY29lcmNlcyBpdCB0byBhIHN0cmluZyBmb3IgY2FzZXMgd2hlcmUgYW4gSURPTVxuICogZnVuY3Rpb24gbmVlZHMgdG8gYmUgY29uY2F0dGVkIHRvIGEgc3RyaW5nLlxuICovXG5mdW5jdGlvbiBjYWxsRHluYW1pY1RleHQ8QSwgQj4oXG4gICAgLy8gdHNsaW50OmRpc2FibGUtbmV4dC1saW5lOm5vLWFueVxuICAgIGV4cHI6IFRlbXBsYXRlPEEsIEI+LCBkYXRhOiBBLCBpajogQiwgZXNjRm4/OiAoaTogc3RyaW5nKSA9PiBzdHJpbmcpIHtcbiAgY29uc3QgdHJhbnNmb3JtRm4gPSBlc2NGbiA/IGVzY0ZuIDogKGE6IHN0cmluZykgPT4gYTtcbiAgLy8gdHNsaW50OmRpc2FibGUtbmV4dC1saW5lOm5vLWFueSBBdHRhY2hpbmcgYXJiaXRyYXJ5IGF0dHJpYnV0ZXMgdG8gZnVuY3Rpb24uXG4gIGNvbnN0IHR5cGUgPSAoZXhwciBhcyBhbnkgYXMgSWRvbUZ1bmN0aW9uKS5jb250ZW50S2luZDtcbiAgbGV0IHZhbDogc3RyaW5nfFNhbml0aXplZENvbnRlbnQ7XG4gIGlmICh0eXBlID09PSBTYW5pdGl6ZWRDb250ZW50S2luZC5IVE1MKSB7XG4gICAgdmFsID0gdHJhbnNmb3JtRm4oaHRtbFRvU3RyaW5nKCgpID0+IHtcbiAgICAgIChleHByIGFzIElkb21UZW1wbGF0ZTxBLCBCPikoZGVmYXVsdElkb21SZW5kZXJlciwgZGF0YSwgaWopO1xuICAgIH0pKTtcbiAgfSBlbHNlIGlmICh0eXBlID09PSBTYW5pdGl6ZWRDb250ZW50S2luZC5BVFRSSUJVVEVTKSB7XG4gICAgdmFsID0gdHJhbnNmb3JtRm4oYXR0cmlidXRlc1RvU3RyaW5nKCgpID0+IHtcbiAgICAgIChleHByIGFzIElkb21UZW1wbGF0ZTxBLCBCPikoZGVmYXVsdElkb21SZW5kZXJlciwgZGF0YSwgaWopO1xuICAgIH0pKTtcbiAgfSBlbHNlIHtcbiAgICB2YWwgPSAoZXhwciBhcyBTb3lUZW1wbGF0ZTxBLCBCPikoZGF0YSwgaWopO1xuICB9XG4gIHJldHVybiB2YWw7XG59XG5cbmRlY2xhcmUgZ2xvYmFsIHtcbiAgaW50ZXJmYWNlIEVsZW1lbnQge1xuICAgIF9faW5uZXJIVE1MOiBzdHJpbmc7XG4gIH1cbn1cblxuLyoqXG4gKiBQcmludHMgYW4gZXhwcmVzc2lvbiBkZXBlbmRpbmcgb24gaXRzIHR5cGUuXG4gKi9cbmZ1bmN0aW9uIHByaW50KFxuICAgIGluY3JlbWVudGFsZG9tOiBJbmNyZW1lbnRhbERvbVJlbmRlcmVyLCBleHByOiB1bmtub3duLFxuICAgIGlzU2FuaXRpemVkQ29udGVudD86IGJvb2xlYW58dW5kZWZpbmVkKSB7XG4gIGlmIChleHByIGluc3RhbmNlb2YgU2FuaXRpemVkSHRtbCB8fCBpc1Nhbml0aXplZENvbnRlbnQpIHtcbiAgICBjb25zdCBjb250ZW50ID0gU3RyaW5nKGV4cHIpO1xuICAgIC8vIElmIHRoZSBzdHJpbmcgaGFzIG5vIDwgb3IgJiwgaXQncyBkZWZpbml0ZWx5IG5vdCBIVE1MLiBPdGhlcndpc2VcbiAgICAvLyBwcm9jZWVkIHdpdGggY2F1dGlvbi5cbiAgICBpZiAoY29udGVudC5pbmRleE9mKCc8JykgPCAwICYmIGNvbnRlbnQuaW5kZXhPZignJicpIDwgMCkge1xuICAgICAgaW5jcmVtZW50YWxkb20udGV4dChjb250ZW50KTtcbiAgICB9IGVsc2Uge1xuICAgICAgLy8gRm9yIEhUTUwgY29udGVudCB3ZSBuZWVkIHRvIGluc2VydCBhIGN1c3RvbSBlbGVtZW50IHdoZXJlIHdlIGNhbiBwbGFjZVxuICAgICAgLy8gdGhlIGNvbnRlbnQgd2l0aG91dCBpbmNyZW1lbnRhbCBkb20gbW9kaWZ5aW5nIGl0LlxuICAgICAgY29uc3QgZWwgPSBpbmNyZW1lbnRhbGRvbS5vcGVuKCdodG1sLWJsb2InKTtcbiAgICAgIGlmIChlbCAmJiBlbC5fX2lubmVySFRNTCAhPT0gY29udGVudCkge1xuICAgICAgICBnb29nU295LnJlbmRlckh0bWwoZWwsIG9yZGFpblNhbml0aXplZEh0bWwoY29udGVudCkpO1xuICAgICAgICBlbC5fX2lubmVySFRNTCA9IGNvbnRlbnQ7XG4gICAgICB9XG4gICAgICBpbmNyZW1lbnRhbGRvbS5za2lwKCk7XG4gICAgICBpbmNyZW1lbnRhbGRvbS5jbG9zZSgpO1xuICAgIH1cbiAgfSBlbHNlIHtcbiAgICByZW5kZXJEeW5hbWljQ29udGVudChpbmNyZW1lbnRhbGRvbSwgZXhwcik7XG4gIH1cbn1cblxuZnVuY3Rpb24gdmlzaXRIdG1sQ29tbWVudE5vZGUoXG4gICAgaW5jcmVtZW50YWxkb206IEluY3JlbWVudGFsRG9tUmVuZGVyZXIsIHZhbDogc3RyaW5nKSB7XG4gIGNvbnN0IGN1cnJOb2RlID0gaW5jcmVtZW50YWxkb20uY3VycmVudEVsZW1lbnQoKTtcbiAgaWYgKCFjdXJyTm9kZSkge1xuICAgIHJldHVybjtcbiAgfVxuICBpZiAoY3Vyck5vZGUubmV4dFNpYmxpbmcgIT0gbnVsbCAmJlxuICAgICAgY3Vyck5vZGUubmV4dFNpYmxpbmcubm9kZVR5cGUgPT09IE5vZGUuQ09NTUVOVF9OT0RFKSB7XG4gICAgY3Vyck5vZGUubmV4dFNpYmxpbmcudGV4dENvbnRlbnQgPSB2YWw7XG4gICAgLy8gVGhpcyBpcyB0aGUgY2FzZSB3aGVyZSB3ZSBhcmUgY3JlYXRpbmcgbmV3IERPTSBmcm9tIGFuIGVtcHR5IGVsZW1lbnQuXG4gIH0gZWxzZSB7XG4gICAgY3Vyck5vZGUuYXBwZW5kQ2hpbGQoZG9jdW1lbnQuY3JlYXRlQ29tbWVudCh2YWwpKTtcbiAgfVxuICBpbmNyZW1lbnRhbGRvbS5za2lwTm9kZSgpO1xufVxuXG5mdW5jdGlvbiBpc1RydXRoeShleHByOiB1bmtub3duKTogYm9vbGVhbiB7XG4gIGlmICghZXhwcikgcmV0dXJuIGZhbHNlO1xuICBpZiAoZXhwciBpbnN0YW5jZW9mIFNhbml0aXplZENvbnRlbnQpIHJldHVybiAhIWV4cHIuZ2V0Q29udGVudCgpO1xuXG4gIC8vIGlkb20gY2FsbGJhY2tzLlxuICBpZiAodHlwZW9mIGV4cHIgPT09ICdmdW5jdGlvbicpIHtcbiAgICBjb25zdCByZW5kZXJlciA9IG5ldyBGYWxzaW5lc3NSZW5kZXJlcigpO1xuICAgIGV4cHIocmVuZGVyZXIpO1xuICAgIHJldHVybiByZW5kZXJlci5kaWRSZW5kZXIoKTtcbiAgfVxuXG4gIC8vIHRydWUsIG51bWJlcnMsIHN0cmluZ3MuXG4gIGlmICh0eXBlb2YgZXhwciAhPT0gJ29iamVjdCcpIHJldHVybiAhIVN0cmluZyhleHByKTtcblxuICAvLyBPYmplY3RzLCBhcnJheXMuXG4gIHJldHVybiB0cnVlO1xufVxuXG5leHBvcnQge1xuICBTb3lFbGVtZW50IGFzICRTb3lFbGVtZW50LFxuICBpc1RydXRoeSBhcyAkJGlzVHJ1dGh5LFxuICBwcmludCBhcyAkJHByaW50LFxuICBodG1sVG9TdHJpbmcgYXMgJCRodG1sVG9TdHJpbmcsXG4gIG1ha2VIdG1sIGFzICQkbWFrZUh0bWwsXG4gIG1ha2VBdHRyaWJ1dGVzIGFzICQkbWFrZUF0dHJpYnV0ZXMsXG4gIGNhbGxEeW5hbWljSnMgYXMgJCRjYWxsRHluYW1pY0pzLFxuICBjYWxsRHluYW1pY0NzcyBhcyAkJGNhbGxEeW5hbWljQ3NzLFxuICBjYWxsRHluYW1pY0hUTUwgYXMgJCRjYWxsRHluYW1pY0hUTUwsXG4gIGNhbGxEeW5hbWljQXR0cmlidXRlcyBhcyAkJGNhbGxEeW5hbWljQXR0cmlidXRlcyxcbiAgY2FsbER5bmFtaWNUZXh0IGFzICQkY2FsbER5bmFtaWNUZXh0LFxuICBoYW5kbGVTb3lFbGVtZW50IGFzICQkaGFuZGxlU295RWxlbWVudCxcbiAgcHJpbnREeW5hbWljQXR0ciBhcyAkJHByaW50RHluYW1pY0F0dHIsXG4gIHZpc2l0SHRtbENvbW1lbnROb2RlIGFzICQkdmlzaXRIdG1sQ29tbWVudE5vZGUsXG59O1xuIl19
