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
goog.module('google3.javascript.template.soy.soyutils_idom');
var module = module || { id: 'javascript/template/soy/soyutils_idom.js' };
var tslib_1 = goog.require('google3.third_party.javascript.tslib.tslib_closure');
var googSoy = goog.require('goog.soy'); // from //javascript/closure/soy
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
var attributes = incrementaldom.attributes, getKey = incrementaldom.getKey, isDataInitialized = incrementaldom.isDataInitialized;
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
incrementaldom.setKeyAttributeName(null);
/**
 * Tries to find an existing Soy element, if it exists. Otherwise, it creates
 * one. Afterwards, it queues up a Soy element (see docs for queueSoyElement)
 * and then proceeds to render the Soy element.
 */
function handleSoyElement(incrementaldom, elementClassCtor, firstElementKey, data, ijData) {
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
    idomFn.toBoolean = function () { return toBoolean(idomFn); };
    idomFn.contentKind = goog_goog_soy_data_SanitizedContentKind_1.HTML;
    return idomFn;
}
exports.$$makeHtml = makeHtml;
// tslint:disable-next-line:no-any Attaching arbitrary attributes to function.
function makeAttributes(idomFn) {
    idomFn.toString = function () { return attributesToString(idomFn); };
    idomFn.toBoolean = function () { return toBoolean(idomFn); };
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
function toBoolean(fn) {
    return fn.toString().length > 0;
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
    String(attributes).replace(htmlAttributeRegExp, function (_, name, dq, sq, uq) {
        nameValuePairs.push([name, googString.unescapeEntities(dq || sq || uq || '')]);
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
    if (goog.isFunction(expr) &&
        expr.contentKind === goog_goog_soy_data_SanitizedContentKind_1.ATTRIBUTES) {
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
//# sourceMappingURL=data:application/json;base64,eyJ2ZXJzaW9uIjozLCJmaWxlIjoic295dXRpbHNfaWRvbS5qcyIsInNvdXJjZVJvb3QiOiIiLCJzb3VyY2VzIjpbIi4uLy4uLy4uLy4uLy4uLy4uL2phdmFzY3JpcHQvdGVtcGxhdGUvc295L3NveXV0aWxzX2lkb20udHMiXSwibmFtZXMiOltdLCJtYXBwaW5ncyI6IkFBQUE7Ozs7Ozs7Ozs7Ozs7OztHQWVHOzs7O0FBRUgsdUNBQXlDLENBQUUsZ0NBQWdDO0FBRTNFLG1HQUEyRSxDQUFDLHFDQUFxQztBQUNqSCxxRkFBNkQsQ0FBQyxxQ0FBcUM7QUFJbkcsNkNBQStDLENBQUUsbUNBQW1DO0FBQ3BGLDhCQUFnQyxDQUFFLGdEQUFnRDtBQUNsRixtREFBNEMsQ0FBRSx3Q0FBd0M7QUFDdEYscUVBQTZELENBQUUsZ0RBQWdEO0FBQy9HLDBGQUFpRCxDQUFFLCtEQUErRDtBQUVsSCwwRUFBa0c7QUFDbEcsMEZBQTJFO0FBK1UzRCxzQkEvVXFCLDZCQUFVLENBK1VwQjtBQTlVM0Isc0VBQXVDO0FBRXZDLHNFQUFzRTtBQUN0RSx5QkFBeUI7QUFDbEIsSUFBQSxzQ0FBVSxFQUFFLDhCQUFNLEVBQUUsb0RBQWlCLENBQW1CO0FBRS9ELElBQU0sbUJBQW1CLEdBQUcsSUFBSSxpQ0FBc0IsRUFBRSxDQUFDO0FBUXpELGtDQUFrQztBQUNsQyxVQUFVLENBQUMsU0FBUyxDQUFDLEdBQUcsVUFBQyxFQUFXLEVBQUUsSUFBWSxFQUFFLEtBQVU7SUFDNUQsZ0NBQWdDO0lBQ2hDLDBFQUEwRTtJQUMxRSx3REFBd0Q7SUFDeEQsaUVBQWlFO0lBQ2pFLHdCQUF3QjtJQUN4QixFQUFFLENBQUMsWUFBWSxDQUFDLFNBQVMsRUFBRSxLQUFLLENBQUMsQ0FBQztJQUNqQyxFQUF1QixDQUFDLE9BQU87UUFDNUIsQ0FBQyxDQUFDLEtBQUssS0FBSyxLQUFLLElBQUksS0FBSyxLQUFLLE9BQU8sSUFBSSxLQUFLLEtBQUssU0FBUyxDQUFDLENBQUM7QUFDckUsQ0FBQyxDQUFDO0FBRUYsa0NBQWtDO0FBQ2xDLFVBQVUsQ0FBQyxP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QUNqRCxJQUFJLENBQUMsUUFBUSxFQUFFO1FBQ2IsT0FBTztLQUNSO0lBQ0QsSUFBSSxRQUFRLENBQUMsV0FBVyxJQUFJLElBQUk7UUFDNUIsUUFBUSxDQUFDLFdBQVcsQ0FBQyxRQUFRLEtBQUssSUFBSSxDQUFDLFlBQVksRUFBRTtRQUN2RCxRQUFRLENBQUMsV0FBVyxDQUFDLFdBQVcsR0FBRyxHQUFHLENBQUM7UUFDdkMsd0VBQXdFO0tBQ3pFO1NBQU07UUFDTCxRQUFRLENBQUMsV0FBVyxDQUFDLFFBQVEsQ0FBQyxhQUFhLENBQUMsR0FBRyxDQUFDLENBQUMsQ0FBQztLQUNuRDtJQUNELGNBQWMsQ0FBQyxRQUFRLEVBQUUsQ0FBQztBQUM1QixDQUFDO0FBZXlCLHNEQUFzQiIsInNvdXJjZXNDb250ZW50IjpbIi8qXG4gKiBAZmlsZW92ZXJ2aWV3IEhlbHBlciB1dGlsaXRpZXMgZm9yIGluY3JlbWVudGFsIGRvbSBjb2RlIGdlbmVyYXRpb24gaW4gU295LlxuICogQ29weXJpZ2h0IDIwMTYgR29vZ2xlIEluYy5cbiAqXG4gKiBMaWNlbnNlZCB1bmRlciB0aGUgQXBhY2hlIExpY2Vuc2UsIFZlcnNpb24gMi4wICh0aGUgXCJMaWNlbnNlXCIpO1xuICogeW91IG1heSBub3QgdXNlIHRoaXMgZmlsZSBleGNlcHQgaW4gY29tcGxpYW5jZSB3aXRoIHRoZSBMaWNlbnNlLlxuICogWW91IG1heSBvYnRhaW4gYSBjb3B5IG9mIHRoZSBMaWNlbnNlIGF0XG4gKlxuICogICAgIGh0dHA6Ly93d3cuYXBhY2hlLm9yZy9saWNlbnNlcy9MSUNFTlNFLTIuMFxuICpcbiAqIFVubGVzcyByZXF1aXJlZCBieSBhcHBsaWNhYmxlIGxhdyBvciBhZ3JlZWQgdG8gaW4gd3JpdGluZywgc29mdHdhcmVcbiAqIGRpc3RyaWJ1dGVkIHVuZGVyIHRoZSBMaWNlbnNlIGlzIGRpc3RyaWJ1dGVkIG9uIGFuIFwiQVMgSVNcIiBCQVNJUyxcbiAqIFdJVEhPVVQgV0FSUkFOVElFUyBPUiBDT05ESVRJT05TIE9GIEFOWSBLSU5ELCBlaXRoZXIgZXhwcmVzcyBvciBpbXBsaWVkLlxuICogU2VlIHRoZSBMaWNlbnNlIGZvciB0aGUgc3BlY2lmaWMgbGFuZ3VhZ2UgZ292ZXJuaW5nIHBlcm1pc3Npb25zIGFuZFxuICogbGltaXRhdGlvbnMgdW5kZXIgdGhlIExpY2Vuc2UuXG4gKi9cblxuaW1wb3J0ICogYXMgZ29vZ1NveSBmcm9tICdnb29nOmdvb2cuc295JzsgIC8vIGZyb20gLy9qYXZhc2NyaXB0L2Nsb3N1cmUvc295XG5pbXBvcnQgU2FuaXRpemVkQ29udGVudCBmcm9tICdnb29nOmdvb2cuc295LmRhdGEuU2FuaXRpemVkQ29udGVudCc7IC8vIGZyb20gLy9qYXZhc2NyaXB0L2Nsb3N1cmUvc295OmRhdGFcbmltcG9ydCBTYW5pdGl6ZWRDb250ZW50S2luZCBmcm9tICdnb29nOmdvb2cuc295LmRhdGEuU2FuaXRpemVkQ29udGVudEtpbmQnOyAvLyBmcm9tIC8vamF2YXNjcmlwdC9jbG9zdXJlL3NveTpkYXRhXG5pbXBvcnQgU2FuaXRpemVkSHRtbCBmcm9tICdnb29nOmdvb2cuc295LmRhdGEuU2FuaXRpemVkSHRtbCc7IC8vIGZyb20gLy9qYXZhc2NyaXB0L2Nsb3N1cmUvc295OmRhdGFcbmltcG9ydCBTYW5pdGl6ZWRIdG1sQXR0cmlidXRlIGZyb20gJ2dvb2c6Z29vZy5zb3kuZGF0YS5TYW5pdGl6ZWRIdG1sQXR0cmlidXRlJzsgLy8gZnJvbSAvL2phdmFzY3JpcHQvY2xvc3VyZS9zb3k6ZGF0YVxuaW1wb3J0IFNhbml0aXplZEpzIGZyb20gJ2dvb2c6Z29vZy5zb3kuZGF0YS5TYW5pdGl6ZWRKcyc7IC8vIGZyb20gLy9qYXZhc2NyaXB0L2Nsb3N1cmUvc295OmRhdGFcbmltcG9ydCBTYW5pdGl6ZWRVcmkgZnJvbSAnZ29vZzpnb29nLnNveS5kYXRhLlNhbml0aXplZFVyaSc7IC8vIGZyb20gLy9qYXZhc2NyaXB0L2Nsb3N1cmUvc295OmRhdGFcbmltcG9ydCAqIGFzIGdvb2dTdHJpbmcgZnJvbSAnZ29vZzpnb29nLnN0cmluZyc7ICAvLyBmcm9tIC8vamF2YXNjcmlwdC9jbG9zdXJlL3N0cmluZ1xuaW1wb3J0ICogYXMgc295IGZyb20gJ2dvb2c6c295JzsgIC8vIGZyb20gLy9qYXZhc2NyaXB0L3RlbXBsYXRlL3NveTpzb3lfdXNlZ29vZ19qc1xuaW1wb3J0IHtpc0F0dHJpYnV0ZX0gZnJvbSAnZ29vZzpzb3kuY2hlY2tzJzsgIC8vIGZyb20gLy9qYXZhc2NyaXB0L3RlbXBsYXRlL3NveTpjaGVja3NcbmltcG9ydCB7b3JkYWluU2FuaXRpemVkSHRtbH0gZnJvbSAnZ29vZzpzb3lkYXRhLlZFUllfVU5TQUZFJzsgIC8vIGZyb20gLy9qYXZhc2NyaXB0L3RlbXBsYXRlL3NveTpzb3lfdXNlZ29vZ19qc1xuaW1wb3J0ICogYXMgaW5jcmVtZW50YWxkb20gZnJvbSAnaW5jcmVtZW50YWxkb20nOyAgLy8gZnJvbSAvL3RoaXJkX3BhcnR5L2phdmFzY3JpcHQvaW5jcmVtZW50YWxfZG9tOmluY3JlbWVudGFsZG9tXG5cbmltcG9ydCB7SW5jcmVtZW50YWxEb21SZW5kZXJlciwgaXNNYXRjaGluZ0tleSwgcGF0Y2gsIHBhdGNoT3V0ZXIsIHNlcmlhbGl6ZUtleX0gZnJvbSAnLi9hcGlfaWRvbSc7XG5pbXBvcnQge0lkb21GdW5jdGlvbiwgUGF0Y2hGdW5jdGlvbiwgU295RWxlbWVudH0gZnJvbSAnLi9lbGVtZW50X2xpYl9pZG9tJztcbmltcG9ydCB7Z2V0U295VW50eXBlZH0gZnJvbSAnLi9nbG9iYWwnO1xuXG4vLyBEZWNsYXJlIHByb3BlcnRpZXMgdGhhdCBuZWVkIHRvIGJlIGFwcGxpZWQgbm90IGFzIGF0dHJpYnV0ZXMgYnV0IGFzXG4vLyBhY3R1YWwgRE9NIHByb3BlcnRpZXMuXG5jb25zdCB7YXR0cmlidXRlcywgZ2V0S2V5LCBpc0RhdGFJbml0aWFsaXplZH0gPSBpbmNyZW1lbnRhbGRvbTtcblxuY29uc3QgZGVmYXVsdElkb21SZW5kZXJlciA9IG5ldyBJbmNyZW1lbnRhbERvbVJlbmRlcmVyKCk7XG5cbnR5cGUgSWRvbVRlbXBsYXRlPEEsIEI+ID1cbiAgICAoaWRvbTogSW5jcmVtZW50YWxEb21SZW5kZXJlciwgcGFyYW1zOiBBLCBpakRhdGE6IEIpID0+IHZvaWQ7XG50eXBlIFNveVRlbXBsYXRlPEEsIEI+ID0gKHBhcmFtczogQSwgaWpEYXRhOiBCKSA9PiBzdHJpbmd8U2FuaXRpemVkQ29udGVudDtcbnR5cGUgTGV0RnVuY3Rpb24gPSAoaWRvbTogSW5jcmVtZW50YWxEb21SZW5kZXJlcikgPT4gdm9pZDtcbnR5cGUgVGVtcGxhdGU8QSwgQj4gPSBJZG9tVGVtcGxhdGU8QSwgQj58U295VGVtcGxhdGU8QSwgQj47XG5cbi8vIHRzbGludDpkaXNhYmxlLW5leHQtbGluZTpuby1hbnlcbmF0dHJpYnV0ZXNbJ2NoZWNrZWQnXSA9IChlbDogRWxlbWVudCwgbmFtZTogc3RyaW5nLCB2YWx1ZTogYW55KSA9PiB7XG4gIC8vIFdlIGRvbid0IHVzZSAhIXZhbHVlIGJlY2F1c2U6XG4gIC8vIDEuIElmIHZhbHVlIGlzICcnICh0aGlzIGlzIHRoZSBjYXNlIHdoZXJlIGEgdXNlciB1c2VzIDxkaXYgY2hlY2tlZCAvPiksXG4gIC8vICAgIHRoZSBjaGVja2VkIHZhbHVlIHNob3VsZCBiZSB0cnVlLCBidXQgJycgaXMgZmFsc3kuXG4gIC8vIDIuIElmIHZhbHVlIGlzICdmYWxzZScsIHRoZSBjaGVja2VkIHZhbHVlIHNob3VsZCBiZSBmYWxzZSwgYnV0XG4gIC8vICAgICdmYWxzZScgaXMgdHJ1dGh5LlxuICBlbC5zZXRBdHRyaWJ1dGUoJ2NoZWNrZWQnLCB2YWx1ZSk7XG4gIChlbCBhcyBIVE1MSW5wdXRFbGVtZW50KS5jaGVja2VkID1cbiAgICAgICEodmFsdWUgPT09IGZhbHNlIHx8IHZhbHVlID09PSAnZmFsc2UnIHx8IHZhbHVlID09PSB1bmRlZmluZWQpO1xufTtcblxuLy8gdHNsaW50OmRpc2FibGUtbmV4dC1saW5lOm5vLWFueVxuYXR0cmlidXRlc1sndmFsdWUnXSA9IChlbDogRWxlbWVudCwgbmFtZTogc3RyaW5nLCB2YWx1ZTogYW55KSA9PiB7XG4gIChlbCBhcyBIVE1MSW5wdXRFbGVtZW50KS52YWx1ZSA9IHZhbHVlO1xuICBlbC5zZXRBdHRyaWJ1dGUoJ3ZhbHVlJywgdmFsdWUpO1xufTtcblxuLy8gU295IHVzZXMgdGhlIHtrZXl9IGNvbW1hbmQgc3ludGF4LCByYXRoZXIgdGhhbiBIVE1MIGF0dHJpYnV0ZXMsIHRvXG4vLyBpbmRpY2F0ZSBlbGVtZW50IGtleXMuXG5pbmNyZW1lbnRhbGRvbS5zZXRLZXlBdHRyaWJ1dGVOYW1lKG51bGwpO1xuXG4vKipcbiAqIFRyaWVzIHRvIGZpbmQgYW4gZXhpc3RpbmcgU295IGVsZW1lbnQsIGlmIGl0IGV4aXN0cy4gT3RoZXJ3aXNlLCBpdCBjcmVhdGVzXG4gKiBvbmUuIEFmdGVyd2FyZHMsIGl0IHF1ZXVlcyB1cCBhIFNveSBlbGVtZW50IChzZWUgZG9jcyBmb3IgcXVldWVTb3lFbGVtZW50KVxuICogYW5kIHRoZW4gcHJvY2VlZHMgdG8gcmVuZGVyIHRoZSBTb3kgZWxlbWVudC5cbiAqL1xuZnVuY3Rpb24gaGFuZGxlU295RWxlbWVudDxEQVRBLCBUIGV4dGVuZHMgU295RWxlbWVudDxEQVRBLCB7fT4+KFxuICAgIGluY3JlbWVudGFsZG9tOiBJbmNyZW1lbnRhbERvbVJlbmRlcmVyLFxuICAgIGVsZW1lbnRDbGFzc0N0b3I6IG5ldyAoZGF0YTogREFUQSwgaWo6IHVua25vd24pID0+IFQsXG4gICAgZmlyc3RFbGVtZW50S2V5OiBzdHJpbmcsIGRhdGE6IERBVEEsIGlqRGF0YTogdW5rbm93bikge1xuICBjb25zdCBzb3lFbGVtZW50S2V5ID0gZmlyc3RFbGVtZW50S2V5ICsgaW5jcmVtZW50YWxkb20uZ2V0Q3VycmVudEtleVN0YWNrKCk7XG4gIGxldCBjdXJyZW50UG9pbnRlciA9IGluY3JlbWVudGFsZG9tLmN1cnJlbnRQb2ludGVyKCk7XG4gIGxldCBlbDogVHxudWxsID0gbnVsbDtcbiAgd2hpbGUgKGN1cnJlbnRQb2ludGVyICE9IG51bGwpIHtcbiAgICBjb25zdCBtYXliZVNveUVsID0gZ2V0U295VW50eXBlZChjdXJyZW50UG9pbnRlcikgYXMgVDtcbiAgICAvLyBXZSBjYW5ub3QgdXNlIHRoZSBjdXJyZW50IGtleSBvZiB0aGUgZWxlbWVudCBiZWNhdXNlIG1hbnkgbGF5ZXJzXG4gICAgLy8gb2YgdGVtcGxhdGUgY2FsbHMgbWF5IGhhdmUgaGFwcGVuZWQuIFdlIGNhbiBvbmx5IGJlIHN1cmUgdGhhdCB0aGUgU295XG4gICAgLy8gZWxlbWVudCB3YXMgdGhlIHNhbWUgaWYgdGhlIGtleSBjb25zdHJ1Y3RlZCBpcyBtYXRjaGluZyB0aGUga2V5IGN1cnJlbnRcbiAgICAvLyB3aGVuIHRoZSB7ZWxlbWVudH0gY29tbWFuZCB3YXMgY3JlYXRlZC5cbiAgICBpZiAobWF5YmVTb3lFbCBpbnN0YW5jZW9mIGVsZW1lbnRDbGFzc0N0b3IgJiZcbiAgICAgICAgaXNNYXRjaGluZ0tleShzb3lFbGVtZW50S2V5LCBtYXliZVNveUVsLmtleSkpIHtcbiAgICAgIGVsID0gbWF5YmVTb3lFbDtcbiAgICAgIGJyZWFrO1xuICAgIH1cbiAgICBjdXJyZW50UG9pbnRlciA9IGN1cnJlbnRQb2ludGVyLm5leHRTaWJsaW5nO1xuICB9XG4gIGlmICghZWwpIHtcbiAgICBlbCA9IG5ldyBlbGVtZW50Q2xhc3NDdG9yKGRhdGEsIGlqRGF0YSk7XG4gICAgZWwua2V5ID0gc295RWxlbWVudEtleTtcbiAgfVxuICBlbC5xdWV1ZVNveUVsZW1lbnQoaW5jcmVtZW50YWxkb20sIGRhdGEpO1xuICBlbC5yZW5kZXJJbnRlcm5hbChpbmNyZW1lbnRhbGRvbSwgZGF0YSk7XG4gIHJldHVybiBlbDtcbn1cblxuLy8gdHNsaW50OmRpc2FibGUtbmV4dC1saW5lOm5vLWFueSBBdHRhY2hpbmcgYXJiaXRyYXJ5IGF0dHJpYnV0ZXMgdG8gZnVuY3Rpb24uXG5mdW5jdGlvbiBtYWtlSHRtbChpZG9tRm46IGFueSk6IElkb21GdW5jdGlvbiB7XG4gIGlkb21Gbi50b1N0cmluZyA9IChyZW5kZXJlcjogSW5jcmVtZW50YWxEb21SZW5kZXJlciA9IGRlZmF1bHRJZG9tUmVuZGVyZXIpID0+XG4gICAgICBodG1sVG9TdHJpbmcoaWRvbUZuLCByZW5kZXJlcik7XG4gIGlkb21Gbi50b0Jvb2xlYW4gPSAoKSA9PiB0b0Jvb2xlYW4oaWRvbUZuKTtcbiAgaWRvbUZuLmNvbnRlbnRLaW5kID0gU2FuaXRpemVkQ29udGVudEtpbmQuSFRNTDtcbiAgcmV0dXJuIGlkb21GbiBhcyBJZG9tRnVuY3Rpb247XG59XG5cbi8vIHRzbGludDpkaXNhYmxlLW5leHQtbGluZTpuby1hbnkgQXR0YWNoaW5nIGFyYml0cmFyeSBhdHRyaWJ1dGVzIHRvIGZ1bmN0aW9uLlxuZnVuY3Rpb24gbWFrZUF0dHJpYnV0ZXMoaWRvbUZuOiBhbnkpOiBJZG9tRnVuY3Rpb24ge1xuICBpZG9tRm4udG9TdHJpbmcgPSAoKSA9PiBhdHRyaWJ1dGVzVG9TdHJpbmcoaWRvbUZuKTtcbiAgaWRvbUZuLnRvQm9vbGVhbiA9ICgpID0+IHRvQm9vbGVhbihpZG9tRm4pO1xuICBpZG9tRm4uY29udGVudEtpbmQgPSBTYW5pdGl6ZWRDb250ZW50S2luZC5BVFRSSUJVVEVTO1xuICByZXR1cm4gaWRvbUZuIGFzIElkb21GdW5jdGlvbjtcbn1cblxuLyoqXG4gKiBUT0RPKHRvbW5ndXllbik6IElzc3VlIGEgd2FybmluZyBpbiB0aGVzZSBjYXNlcyBzbyB0aGF0IHVzZXJzIGtub3cgdGhhdFxuICogZXhwZW5zaXZlIGJlaGF2aW9yIGlzIGhhcHBlbmluZy5cbiAqL1xuZnVuY3Rpb24gaHRtbFRvU3RyaW5nKFxuICAgIGZuOiBMZXRGdW5jdGlvbiwgcmVuZGVyZXI6IEluY3JlbWVudGFsRG9tUmVuZGVyZXIgPSBkZWZhdWx0SWRvbVJlbmRlcmVyKSB7XG4gIGNvbnN0IGVsID0gZG9jdW1lbnQuY3JlYXRlRWxlbWVudCgnZGl2Jyk7XG4gIHBhdGNoKGVsLCAoKSA9PiB7XG4gICAgZm4ocmVuZGVyZXIpO1xuICB9KTtcbiAgcmV0dXJuIGVsLmlubmVySFRNTDtcbn1cblxuZnVuY3Rpb24gYXR0cmlidXRlc0ZhY3RvcnkoZm46IFBhdGNoRnVuY3Rpb24pOiBQYXRjaEZ1bmN0aW9uIHtcbiAgcmV0dXJuICgpID0+IHtcbiAgICBpbmNyZW1lbnRhbGRvbS5vcGVuKCdkaXYnKTtcbiAgICBmbihkZWZhdWx0SWRvbVJlbmRlcmVyKTtcbiAgICBpbmNyZW1lbnRhbGRvbS5hcHBseUF0dHJzKCk7XG4gICAgaW5jcmVtZW50YWxkb20uY2xvc2UoKTtcbiAgfTtcbn1cblxuLyoqXG4gKiBUT0RPKHRvbW5ndXllbik6IElzc3VlIGEgd2FybmluZyBpbiB0aGVzZSBjYXNlcyBzbyB0aGF0IHVzZXJzIGtub3cgdGhhdFxuICogZXhwZW5zaXZlIGJlaGF2aW9yIGlzIGhhcHBlbmluZy5cbiAqL1xuZnVuY3Rpb24gYXR0cmlidXRlc1RvU3RyaW5nKGZuOiBQYXRjaEZ1bmN0aW9uKTogc3RyaW5nIHtcbiAgY29uc3QgZWxGbiA9IGF0dHJpYnV0ZXNGYWN0b3J5KGZuKTtcbiAgY29uc3QgZWwgPSBkb2N1bWVudC5jcmVhdGVFbGVtZW50KCdkaXYnKTtcbiAgcGF0Y2hPdXRlcihlbCwgZWxGbik7XG4gIGNvbnN0IHM6IHN0cmluZ1tdID0gW107XG4gIGZvciAobGV0IGkgPSAwOyBpIDwgZWwuYXR0cmlidXRlcy5sZW5ndGg7IGkrKykge1xuICAgIHMucHVzaChgJHtlbC5hdHRyaWJ1dGVzW2ldLm5hbWV9PSR7ZWwuYXR0cmlidXRlc1tpXS52YWx1ZX1gKTtcbiAgfVxuICAvLyBUaGUgc29ydCBpcyBpbXBvcnRhbnQgYmVjYXVzZSBhdHRyaWJ1dGUgb3JkZXIgdmFyaWVzIHBlciBicm93c2VyLlxuICByZXR1cm4gcy5zb3J0KCkuam9pbignICcpO1xufVxuXG5mdW5jdGlvbiB0b0Jvb2xlYW4oZm46IElkb21GdW5jdGlvbikge1xuICByZXR1cm4gZm4udG9TdHJpbmcoKS5sZW5ndGggPiAwO1xufVxuXG4vKipcbiAqIENhbGxzIGFuIGV4cHJlc3Npb24gaW4gY2FzZSBvZiBhIGZ1bmN0aW9uIG9yIG91dHB1dHMgaXQgYXMgdGV4dCBjb250ZW50LlxuICovXG5mdW5jdGlvbiByZW5kZXJEeW5hbWljQ29udGVudChcbiAgICBpbmNyZW1lbnRhbGRvbTogSW5jcmVtZW50YWxEb21SZW5kZXJlciwgZXhwcjogdW5rbm93bikge1xuICAvLyBUT0RPKGx1a2VzKTogY2hlY2sgY29udGVudCBraW5kID09IGh0bWxcbiAgaWYgKHR5cGVvZiBleHByID09PSAnZnVuY3Rpb24nKSB7XG4gICAgLy8gVGhlIFNveSBjb21waWxlciB3aWxsIHZhbGlkYXRlIHRoZSBjb250ZW50IGtpbmQgb2YgdGhlIHBhcmFtZXRlci5cbiAgICBleHByKGluY3JlbWVudGFsZG9tKTtcbiAgfSBlbHNlIHtcbiAgICBpbmNyZW1lbnRhbGRvbS50ZXh0KFN0cmluZyhleHByKSk7XG4gIH1cbn1cblxuLyoqXG4gKiBNYXRjaGVzIGFuIEhUTUwgYXR0cmlidXRlIG5hbWUgdmFsdWUgcGFpci5cbiAqIE5hbWUgaXMgaW4gZ3JvdXAgMS4gIFZhbHVlLCBpZiBwcmVzZW50LCBpcyBpbiBvbmUgb2YgZ3JvdXAgKDIsMyw0KVxuICogZGVwZW5kaW5nIG9uIGhvdyBpdCdzIHF1b3RlZC5cbiAqXG4gKiBUaGlzIFJlZ0V4cCB3YXMgZGVyaXZlZCBmcm9tIHZpc3VhbCBpbnNwZWN0aW9uIG9mXG4gKiAgIGh0bWwuc3BlYy53aGF0d2cub3JnL211bHRpcGFnZS9wYXJzaW5nLmh0bWwjYmVmb3JlLWF0dHJpYnV0ZS1uYW1lLXN0YXRlXG4gKiBhbmQgZm9sbG93aW5nIHN0YXRlcy5cbiAqL1xuY29uc3QgaHRtbEF0dHJpYnV0ZVJlZ0V4cDogUmVnRXhwID1cbiAgICAvKFteXFx0XFxuXFxmXFxyIC8+PV0rKVtcXHRcXG5cXGZcXHIgXSooPzo9W1xcdFxcblxcZlxcciBdKig/OlwiKFteXCJdKilcIj98JyhbXiddKiknP3woW15cXHRcXG5cXGZcXHIgPl0qKSkpPy9nO1xuXG5mdW5jdGlvbiBzcGxpdEF0dHJpYnV0ZXMoYXR0cmlidXRlczogc3RyaW5nKSB7XG4gIGNvbnN0IG5hbWVWYWx1ZVBhaXJzOiBzdHJpbmdbXVtdID0gW107XG4gIFN0cmluZyhhdHRyaWJ1dGVzKS5yZXBsYWNlKGh0bWxBdHRyaWJ1dGVSZWdFeHAsIChfLCBuYW1lLCBkcSwgc3EsIHVxKSA9PiB7XG4gICAgbmFtZVZhbHVlUGFpcnMucHVzaChcbiAgICAgICAgW25hbWUsIGdvb2dTdHJpbmcudW5lc2NhcGVFbnRpdGllcyhkcSB8fCBzcSB8fCB1cSB8fCAnJyldKTtcbiAgICByZXR1cm4gJyAnO1xuICB9KTtcbiAgcmV0dXJuIG5hbWVWYWx1ZVBhaXJzO1xufVxuXG4vKipcbiAqIENhbGxzIGFuIGV4cHJlc3Npb24gaW4gY2FzZSBvZiBhIGZ1bmN0aW9uIG9yIG91dHB1dHMgaXQgYXMgdGV4dCBjb250ZW50LlxuICovXG5mdW5jdGlvbiBjYWxsRHluYW1pY0F0dHJpYnV0ZXM8QSwgQj4oXG4gICAgaW5jcmVtZW50YWxkb206IEluY3JlbWVudGFsRG9tUmVuZGVyZXIsXG4gICAgLy8gdHNsaW50OmRpc2FibGUtbmV4dC1saW5lOm5vLWFueVxuICAgIGV4cHI6IFRlbXBsYXRlPEEsIEI+LCBkYXRhOiBBLCBpajogQikge1xuICAvLyB0c2xpbnQ6ZGlzYWJsZS1uZXh0LWxpbmU6bm8tYW55IEF0dGFjaGluZyBhcmJpdHJhcnkgYXR0cmlidXRlcyB0byBmdW5jdGlvbi5cbiAgY29uc3QgdHlwZSA9IChleHByIGFzIGFueSBhcyBJZG9tRnVuY3Rpb24pLmNvbnRlbnRLaW5kO1xuICBpZiAodHlwZSA9PT0gU2FuaXRpemVkQ29udGVudEtpbmQuQVRUUklCVVRFUykge1xuICAgIChleHByIGFzIElkb21UZW1wbGF0ZTxBLCBCPikoaW5jcmVtZW50YWxkb20sIGRhdGEsIGlqKTtcbiAgfSBlbHNlIHtcbiAgICBsZXQgdmFsOiBzdHJpbmd8U2FuaXRpemVkSHRtbEF0dHJpYnV0ZTtcbiAgICBpZiAodHlwZSA9PT0gU2FuaXRpemVkQ29udGVudEtpbmQuSFRNTCkge1xuICAgICAgLy8gVGhpcyBlZmZlY3RpdmVseSBuZWdhdGVzIHRoZSB2YWx1ZSBvZiBzcGxpdHRpbmcgYSBzdHJpbmcuIEhvd2V2ZXIsXG4gICAgICAvLyBUaGlzIGNhbiBiZSByZW1vdmVkIGlmIFNveSBkZWNpZGVzIHRvIHRyZWF0IGF0dHJpYnV0ZSBwcmludGluZ1xuICAgICAgLy8gYW5kIGF0dHJpYnV0ZSBuYW1lcyBkaWZmZXJlbnRseS5cbiAgICAgIHZhbCA9IHNveS4kJGZpbHRlckh0bWxBdHRyaWJ1dGVzKGh0bWxUb1N0cmluZygoKSA9PiB7XG4gICAgICAgIChleHByIGFzIElkb21UZW1wbGF0ZTxBLCBCPikoZGVmYXVsdElkb21SZW5kZXJlciwgZGF0YSwgaWopO1xuICAgICAgfSkpO1xuICAgIH0gZWxzZSB7XG4gICAgICB2YWwgPSAoZXhwciBhcyBTb3lUZW1wbGF0ZTxBLCBCPikoZGF0YSwgaWopIGFzIFNhbml0aXplZEh0bWxBdHRyaWJ1dGU7XG4gICAgfVxuICAgIHByaW50RHluYW1pY0F0dHIoaW5jcmVtZW50YWxkb20sIHZhbCk7XG4gIH1cbn1cblxuLyoqXG4gKiBQcmludHMgYW4gZXhwcmVzc2lvbiB3aG9zZSB0eXBlIGlzIG5vdCBzdGF0aWNhbGx5IGtub3duIHRvIGJlIG9mIHR5cGVcbiAqIFwiYXR0cmlidXRlc1wiLiBUaGUgZXhwcmVzc2lvbiBpcyB0ZXN0ZWQgYXQgcnVudGltZSBhbmQgZXZhbHVhdGVkIGRlcGVuZGluZ1xuICogb24gd2hhdCB0eXBlIGl0IGlzLiBGb3IgZXhhbXBsZSwgaWYgYSBzdHJpbmcgaXMgcHJpbnRlZCBpbiBhIGNvbnRleHRcbiAqIHRoYXQgZXhwZWN0cyBhdHRyaWJ1dGVzLCB0aGUgc3RyaW5nIGlzIGV2YWx1YXRlZCBkeW5hbWljYWxseSB0byBjb21wdXRlXG4gKiBhdHRyaWJ1dGVzLlxuICovXG5mdW5jdGlvbiBwcmludER5bmFtaWNBdHRyKFxuICAgIGluY3JlbWVudGFsZG9tOiBJbmNyZW1lbnRhbERvbVJlbmRlcmVyLFxuICAgIGV4cHI6IFNhbml0aXplZEh0bWxBdHRyaWJ1dGV8c3RyaW5nfGJvb2xlYW58SWRvbUZ1bmN0aW9uKSB7XG4gIGlmIChnb29nLmlzRnVuY3Rpb24oZXhwcikgJiZcbiAgICAgIChleHByIGFzIElkb21GdW5jdGlvbikuY29udGVudEtpbmQgPT09IFNhbml0aXplZENvbnRlbnRLaW5kLkFUVFJJQlVURVMpIHtcbiAgICAvLyB0c2xpbnQ6ZGlzYWJsZS1uZXh0LWxpbmU6bm8tYW55XG4gICAgKGV4cHIgYXMgYW55IGFzIExldEZ1bmN0aW9uKShpbmNyZW1lbnRhbGRvbSk7XG4gICAgcmV0dXJuO1xuICB9XG4gIGNvbnN0IGF0dHJpYnV0ZXMgPSBzcGxpdEF0dHJpYnV0ZXMoZXhwci50b1N0cmluZygpKTtcbiAgY29uc3QgaXNFeHByQXR0cmlidXRlID0gaXNBdHRyaWJ1dGUoZXhwcik7XG4gIGZvciAoY29uc3QgYXR0cmlidXRlIG9mIGF0dHJpYnV0ZXMpIHtcbiAgICBjb25zdCBhdHRyTmFtZSA9IGlzRXhwckF0dHJpYnV0ZSA/IGF0dHJpYnV0ZVswXSA6XG4gICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICBzb3kuJCRmaWx0ZXJIdG1sQXR0cmlidXRlcyhhdHRyaWJ1dGVbMF0pO1xuICAgIGlmIChhdHRyTmFtZSA9PT0gJ3pTb3l6Jykge1xuICAgICAgaW5jcmVtZW50YWxkb20uYXR0cihhdHRyTmFtZSwgJycpO1xuICAgIH0gZWxzZSB7XG4gICAgICBpbmNyZW1lbnRhbGRvbS5hdHRyKFN0cmluZyhhdHRyTmFtZSksIFN0cmluZyhhdHRyaWJ1dGVbMV0pKTtcbiAgICB9XG4gIH1cbn1cblxuLyoqXG4gKiBDYWxscyBhbiBleHByZXNzaW9uIGluIGNhc2Ugb2YgYSBmdW5jdGlvbiBvciBvdXRwdXRzIGl0IGFzIHRleHQgY29udGVudC5cbiAqL1xuZnVuY3Rpb24gY2FsbER5bmFtaWNIVE1MPEEsIEI+KFxuICAgIGluY3JlbWVudGFsZG9tOiBJbmNyZW1lbnRhbERvbVJlbmRlcmVyLCBleHByOiBUZW1wbGF0ZTxBLCBCPiwgZGF0YTogQSxcbiAgICBpajogQikge1xuICAvLyB0c2xpbnQ6ZGlzYWJsZS1uZXh0LWxpbmU6bm8tYW55IEF0dGFjaGluZyBhcmJpdHJhcnkgYXR0cmlidXRlcyB0byBmdW5jdGlvbi5cbiAgY29uc3QgdHlwZSA9IChleHByIGFzIGFueSBhcyBJZG9tRnVuY3Rpb24pLmNvbnRlbnRLaW5kO1xuICBpZiAodHlwZSA9PT0gU2FuaXRpemVkQ29udGVudEtpbmQuSFRNTCkge1xuICAgIChleHByIGFzIElkb21UZW1wbGF0ZTxBLCBCPikoaW5jcmVtZW50YWxkb20sIGRhdGEsIGlqKTtcbiAgfSBlbHNlIGlmICh0eXBlID09PSBTYW5pdGl6ZWRDb250ZW50S2luZC5BVFRSSUJVVEVTKSB7XG4gICAgY29uc3QgdmFsID0gYXR0cmlidXRlc1RvU3RyaW5nKCgpID0+IHtcbiAgICAgIChleHByIGFzIElkb21UZW1wbGF0ZTxBLCBCPikoZGVmYXVsdElkb21SZW5kZXJlciwgZGF0YSwgaWopO1xuICAgIH0pO1xuICAgIGluY3JlbWVudGFsZG9tLnRleHQodmFsKTtcbiAgfSBlbHNlIHtcbiAgICBjb25zdCB2YWwgPSAoZXhwciBhcyBTb3lUZW1wbGF0ZTxBLCBCPikoZGF0YSwgaWopO1xuICAgIGluY3JlbWVudGFsZG9tLnRleHQoU3RyaW5nKHZhbCkpO1xuICB9XG59XG5cbmZ1bmN0aW9uIGNhbGxEeW5hbWljQ3NzPEEsIEI+KFxuICAgIC8vIHRzbGludDpkaXNhYmxlLW5leHQtbGluZTpuby1hbnkgQXR0YWNoaW5nICBhdHRyaWJ1dGVzIHRvIGZ1bmN0aW9uLlxuICAgIGluY3JlbWVudGFsZG9tOiBJbmNyZW1lbnRhbERvbVJlbmRlcmVyLCBleHByOiAoYTogQSwgYjogQikgPT4gYW55LCBkYXRhOiBBLFxuICAgIGlqOiBCKSB7XG4gIGNvbnN0IHZhbCA9IGNhbGxEeW5hbWljVGV4dDxBLCBCPihleHByLCBkYXRhLCBpaiwgc295LiQkZmlsdGVyQ3NzVmFsdWUpO1xuICBpbmNyZW1lbnRhbGRvbS50ZXh0KFN0cmluZyh2YWwpKTtcbn1cblxuZnVuY3Rpb24gY2FsbER5bmFtaWNKczxBLCBCPihcbiAgICAvLyB0c2xpbnQ6ZGlzYWJsZS1uZXh0LWxpbmU6bm8tYW55IEF0dGFjaGluZyBhdHRyaWJ1dGVzIHRvIGZ1bmN0aW9uLlxuICAgIGluY3JlbWVudGFsZG9tOiBJbmNyZW1lbnRhbERvbVJlbmRlcmVyLCBleHByOiAoYTogQSwgYjogQikgPT4gYW55LCBkYXRhOiBBLFxuICAgIGlqOiBCKSB7XG4gIGNvbnN0IHZhbCA9IGNhbGxEeW5hbWljVGV4dDxBLCBCPihleHByLCBkYXRhLCBpaiwgc295LiQkZXNjYXBlSnNWYWx1ZSk7XG4gIGluY3JlbWVudGFsZG9tLnRleHQoU3RyaW5nKHZhbCkpO1xufVxuXG4vKipcbiAqIENhbGxzIGFuIGV4cHJlc3Npb24gYW5kIGNvZXJjZXMgaXQgdG8gYSBzdHJpbmcgZm9yIGNhc2VzIHdoZXJlIGFuIElET01cbiAqIGZ1bmN0aW9uIG5lZWRzIHRvIGJlIGNvbmNhdHRlZCB0byBhIHN0cmluZy5cbiAqL1xuZnVuY3Rpb24gY2FsbER5bmFtaWNUZXh0PEEsIEI+KFxuICAgIC8vIHRzbGludDpkaXNhYmxlLW5leHQtbGluZTpuby1hbnlcbiAgICBleHByOiBUZW1wbGF0ZTxBLCBCPiwgZGF0YTogQSwgaWo6IEIsIGVzY0ZuPzogKGk6IHN0cmluZykgPT4gc3RyaW5nKSB7XG4gIGNvbnN0IHRyYW5zZm9ybUZuID0gZXNjRm4gPyBlc2NGbiA6IChhOiBzdHJpbmcpID0+IGE7XG4gIC8vIHRzbGludDpkaXNhYmxlLW5leHQtbGluZTpuby1hbnkgQXR0YWNoaW5nIGFyYml0cmFyeSBhdHRyaWJ1dGVzIHRvIGZ1bmN0aW9uLlxuICBjb25zdCB0eXBlID0gKGV4cHIgYXMgYW55IGFzIElkb21GdW5jdGlvbikuY29udGVudEtpbmQ7XG4gIGxldCB2YWw6IHN0cmluZ3xTYW5pdGl6ZWRDb250ZW50O1xuICBpZiAodHlwZSA9PT0gU2FuaXRpemVkQ29udGVudEtpbmQuSFRNTCkge1xuICAgIHZhbCA9IHRyYW5zZm9ybUZuKGh0bWxUb1N0cmluZygoKSA9PiB7XG4gICAgICAoZXhwciBhcyBJZG9tVGVtcGxhdGU8QSwgQj4pKGRlZmF1bHRJZG9tUmVuZGVyZXIsIGRhdGEsIGlqKTtcbiAgICB9KSk7XG4gIH0gZWxzZSBpZiAodHlwZSA9PT0gU2FuaXRpemVkQ29udGVudEtpbmQuQVRUUklCVVRFUykge1xuICAgIHZhbCA9IHRyYW5zZm9ybUZuKGF0dHJpYnV0ZXNUb1N0cmluZygoKSA9PiB7XG4gICAgICAoZXhwciBhcyBJZG9tVGVtcGxhdGU8QSwgQj4pKGRlZmF1bHRJZG9tUmVuZGVyZXIsIGRhdGEsIGlqKTtcbiAgICB9KSk7XG4gIH0gZWxzZSB7XG4gICAgdmFsID0gKGV4cHIgYXMgU295VGVtcGxhdGU8QSwgQj4pKGRhdGEsIGlqKTtcbiAgfVxuICByZXR1cm4gdmFsO1xufVxuXG5kZWNsYXJlIGdsb2JhbCB7XG4gIGludGVyZmFjZSBFbGVtZW50IHtcbiAgICBfX2lubmVySFRNTDogc3RyaW5nO1xuICB9XG59XG5cbi8qKlxuICogUHJpbnRzIGFuIGV4cHJlc3Npb24gZGVwZW5kaW5nIG9uIGl0cyB0eXBlLlxuICovXG5mdW5jdGlvbiBwcmludChcbiAgICBpbmNyZW1lbnRhbGRvbTogSW5jcmVtZW50YWxEb21SZW5kZXJlciwgZXhwcjogdW5rbm93bixcbiAgICBpc1Nhbml0aXplZENvbnRlbnQ/OiBib29sZWFufHVuZGVmaW5lZCkge1xuICBpZiAoZXhwciBpbnN0YW5jZW9mIFNhbml0aXplZEh0bWwgfHwgaXNTYW5pdGl6ZWRDb250ZW50KSB7XG4gICAgY29uc3QgY29udGVudCA9IFN0cmluZyhleHByKTtcbiAgICAvLyBJZiB0aGUgc3RyaW5nIGhhcyBubyA8IG9yICYsIGl0J3MgZGVmaW5pdGVseSBub3QgSFRNTC4gT3RoZXJ3aXNlXG4gICAgLy8gcHJvY2VlZCB3aXRoIGNhdXRpb24uXG4gICAgaWYgKGNvbnRlbnQuaW5kZXhPZignPCcpIDwgMCAmJiBjb250ZW50LmluZGV4T2YoJyYnKSA8IDApIHtcbiAgICAgIGluY3JlbWVudGFsZG9tLnRleHQoY29udGVudCk7XG4gICAgfSBlbHNlIHtcbiAgICAgIC8vIEZvciBIVE1MIGNvbnRlbnQgd2UgbmVlZCB0byBpbnNlcnQgYSBjdXN0b20gZWxlbWVudCB3aGVyZSB3ZSBjYW4gcGxhY2VcbiAgICAgIC8vIHRoZSBjb250ZW50IHdpdGhvdXQgaW5jcmVtZW50YWwgZG9tIG1vZGlmeWluZyBpdC5cbiAgICAgIGNvbnN0IGVsID0gaW5jcmVtZW50YWxkb20ub3BlbignaHRtbC1ibG9iJyk7XG4gICAgICBpZiAoZWwgJiYgZWwuX19pbm5lckhUTUwgIT09IGNvbnRlbnQpIHtcbiAgICAgICAgZ29vZ1NveS5yZW5kZXJIdG1sKGVsLCBvcmRhaW5TYW5pdGl6ZWRIdG1sKGNvbnRlbnQpKTtcbiAgICAgICAgZWwuX19pbm5lckhUTUwgPSBjb250ZW50O1xuICAgICAgfVxuICAgICAgaW5jcmVtZW50YWxkb20uc2tpcCgpO1xuICAgICAgaW5jcmVtZW50YWxkb20uY2xvc2UoKTtcbiAgICB9XG4gIH0gZWxzZSB7XG4gICAgcmVuZGVyRHluYW1pY0NvbnRlbnQoaW5jcmVtZW50YWxkb20sIGV4cHIpO1xuICB9XG59XG5cbmZ1bmN0aW9uIHZpc2l0SHRtbENvbW1lbnROb2RlKFxuICAgIGluY3JlbWVudGFsZG9tOiBJbmNyZW1lbnRhbERvbVJlbmRlcmVyLCB2YWw6IHN0cmluZykge1xuICBjb25zdCBjdXJyTm9kZSA9IGluY3JlbWVudGFsZG9tLmN1cnJlbnRFbGVtZW50KCk7XG4gIGlmICghY3Vyck5vZGUpIHtcbiAgICByZXR1cm47XG4gIH1cbiAgaWYgKGN1cnJOb2RlLm5leHRTaWJsaW5nICE9IG51bGwgJiZcbiAgICAgIGN1cnJOb2RlLm5leHRTaWJsaW5nLm5vZGVUeXBlID09PSBOb2RlLkNPTU1FTlRfTk9ERSkge1xuICAgIGN1cnJOb2RlLm5leHRTaWJsaW5nLnRleHRDb250ZW50ID0gdmFsO1xuICAgIC8vIFRoaXMgaXMgdGhlIGNhc2Ugd2hlcmUgd2UgYXJlIGNyZWF0aW5nIG5ldyBET00gZnJvbSBhbiBlbXB0eSBlbGVtZW50LlxuICB9IGVsc2Uge1xuICAgIGN1cnJOb2RlLmFwcGVuZENoaWxkKGRvY3VtZW50LmNyZWF0ZUNvbW1lbnQodmFsKSk7XG4gIH1cbiAgaW5jcmVtZW50YWxkb20uc2tpcE5vZGUoKTtcbn1cblxuZXhwb3J0IHtcbiAgU295RWxlbWVudCBhcyAkU295RWxlbWVudCxcbiAgcHJpbnQgYXMgJCRwcmludCxcbiAgaHRtbFRvU3RyaW5nIGFzICQkaHRtbFRvU3RyaW5nLFxuICBtYWtlSHRtbCBhcyAkJG1ha2VIdG1sLFxuICBtYWtlQXR0cmlidXRlcyBhcyAkJG1ha2VBdHRyaWJ1dGVzLFxuICBjYWxsRHluYW1pY0pzIGFzICQkY2FsbER5bmFtaWNKcyxcbiAgY2FsbER5bmFtaWNDc3MgYXMgJCRjYWxsRHluYW1pY0NzcyxcbiAgY2FsbER5bmFtaWNIVE1MIGFzICQkY2FsbER5bmFtaWNIVE1MLFxuICBjYWxsRHluYW1pY0F0dHJpYnV0ZXMgYXMgJCRjYWxsRHluYW1pY0F0dHJpYnV0ZXMsXG4gIGNhbGxEeW5hbWljVGV4dCBhcyAkJGNhbGxEeW5hbWljVGV4dCxcbiAgaGFuZGxlU295RWxlbWVudCBhcyAkJGhhbmRsZVNveUVsZW1lbnQsXG4gIHByaW50RHluYW1pY0F0dHIgYXMgJCRwcmludER5bmFtaWNBdHRyLFxuICB2aXNpdEh0bWxDb21tZW50Tm9kZSBhcyAkJHZpc2l0SHRtbENvbW1lbnROb2RlLFxufTtcbiJdfQ==