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
incrementaldom.setKeyAttributeName('soy-server-key');
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
//#
// sourceMappingURL=data:application/json;base64,eyJ2ZXJzaW9uIjozLCJmaWxlIjoic295dXRpbHNfaWRvbS5qcyIsInNvdXJjZVJvb3QiOiIiLCJzb3VyY2VzIjpbIi4uLy4uLy4uLy4uLy4uLy4uL2phdmFzY3JpcHQvdGVtcGxhdGUvc295L3NveXV0aWxzX2lkb20udHMiXSwibmFtZXMiOltdLCJtYXBwaW5ncyI6IkFBQUE7Ozs7Ozs7Ozs7Ozs7OztHQWVHOzs7O0FBRUgsdUNBQXlDLENBQUUsZ0NBQWdDO0FBRTNFLG1HQUEyRSxDQUFDLHFDQUFxQztBQUNqSCxxRkFBNkQsQ0FBQyxxQ0FBcUM7QUFJbkcsNkNBQStDLENBQUUsbUNBQW1DO0FBQ3BGLDhCQUFnQyxDQUFFLGdEQUFnRDtBQUNsRixtREFBNEMsQ0FBRSx3Q0FBd0M7QUFDdEYscUVBQTZELENBQUUsZ0RBQWdEO0FBQy9HLDBGQUFpRCxDQUFFLCtEQUErRDtBQUVsSCwwRUFBa0c7QUFDbEcsMEZBQTJFO0FBK1UzRCxzQkEvVXFCLDZCQUFVLENBK1VwQjtBQTlVM0Isc0VBQXVDO0FBRXZDLHNFQUFzRTtBQUN0RSx5QkFBeUI7QUFDbEIsSUFBQSxzQ0FBVSxFQUFFLDhCQUFNLEVBQUUsb0RBQWlCLENBQW1CO0FBRS9ELElBQU0sbUJBQW1CLEdBQUcsSUFBSSxpQ0FBc0IsRUFBRSxDQUFDO0FBUXpELGtDQUFrQztBQUNsQyxVQUFVLENBQUMsU0FBUyxDQUFDLEdBQUcsVUFBQyxFQUFXLEVBQUUsSUFBWSxFQUFFLEtBQVU7SUFDNUQsZ0NBQWdDO0lBQ2hDLDBFQUEwRTtJQUMxRSx3REFBd0Q7SUFDeEQsaUVBQWlFO0lBQ2pFLHdCQUF3QjtJQUN4QixFQUFFLENBQUMsWUFBWSxDQUFDLFNBQVMsRUFBRSxLQUFLLENBQUMsQ0FBQztJQUNqQyxFQUF1QixDQUFDLE9BQU87UUFDNUIsQ0FBQyxDQUFDLEtBQUssS0FBSyxLQUFLLElBQUksS0FBSyxLQUFLLE9BQU8sSUFBSSxLQUFLLEtBQUssU0FBUyxDQUFDLENBQUM7QUFDckUsQ0FBQyxDQUFDO0FBRUYsa0NBQWtDO0FBQ2xDLFVBQVUsQ0FBQyxP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O0lBQ2pELElBQUksQ0FBQyxRQUFRLEVBQUU7UUFDYixPQUFPO0tBQ1I7SUFDRCxJQUFJLFFBQVEsQ0FBQyxXQUFXLElBQUksSUFBSTtRQUM1QixRQUFRLENBQUMsV0FBVyxDQUFDLFFBQVEsS0FBSyxJQUFJLENBQUMsWUFBWSxFQUFFO1FBQ3ZELFFBQVEsQ0FBQyxXQUFXLENBQUMsV0FBVyxHQUFHLEdBQUcsQ0FBQztRQUN2Qyx3RUFBd0U7S0FDekU7U0FBTTtRQUNMLFFBQVEsQ0FBQyxXQUFXLENBQUMsUUFBUSxDQUFDLGFBQWEsQ0FBQyxHQUFHLENBQUMsQ0FBQyxDQUFDO0tBQ25EO0lBQ0QsY0FBYyxDQUFDLFFBQVEsRUFBRSxDQUFDO0FBQzVCLENBQUM7QUFleUIsc0RBQXNCIiwic291cmNlc0NvbnRlbnQiOlsiLypcbiAqIEBmaWxlb3ZlcnZpZXcgSGVscGVyIHV0aWxpdGllcyBmb3IgaW5jcmVtZW50YWwgZG9tIGNvZGUgZ2VuZXJhdGlvbiBpbiBTb3kuXG4gKiBDb3B5cmlnaHQgMjAxNiBHb29nbGUgSW5jLlxuICpcbiAqIExpY2Vuc2VkIHVuZGVyIHRoZSBBcGFjaGUgTGljZW5zZSwgVmVyc2lvbiAyLjAgKHRoZSBcIkxpY2Vuc2VcIik7XG4gKiB5b3UgbWF5IG5vdCB1c2UgdGhpcyBmaWxlIGV4Y2VwdCBpbiBjb21wbGlhbmNlIHdpdGggdGhlIExpY2Vuc2UuXG4gKiBZb3UgbWF5IG9idGFpbiBhIGNvcHkgb2YgdGhlIExpY2Vuc2UgYXRcbiAqXG4gKiAgICAgaHR0cDovL3d3dy5hcGFjaGUub3JnL2xpY2Vuc2VzL0xJQ0VOU0UtMi4wXG4gKlxuICogVW5sZXNzIHJlcXVpcmVkIGJ5IGFwcGxpY2FibGUgbGF3IG9yIGFncmVlZCB0byBpbiB3cml0aW5nLCBzb2Z0d2FyZVxuICogZGlzdHJpYnV0ZWQgdW5kZXIgdGhlIExpY2Vuc2UgaXMgZGlzdHJpYnV0ZWQgb24gYW4gXCJBUyBJU1wiIEJBU0lTLFxuICogV0lUSE9VVCBXQVJSQU5USUVTIE9SIENPTkRJVElPTlMgT0YgQU5ZIEtJTkQsIGVpdGhlciBleHByZXNzIG9yIGltcGxpZWQuXG4gKiBTZWUgdGhlIExpY2Vuc2UgZm9yIHRoZSBzcGVjaWZpYyBsYW5ndWFnZSBnb3Zlcm5pbmcgcGVybWlzc2lvbnMgYW5kXG4gKiBsaW1pdGF0aW9ucyB1bmRlciB0aGUgTGljZW5zZS5cbiAqL1xuXG5pbXBvcnQgKiBhcyBnb29nU295IGZyb20gJ2dvb2c6Z29vZy5zb3knOyAgLy8gZnJvbSAvL2phdmFzY3JpcHQvY2xvc3VyZS9zb3lcbmltcG9ydCBTYW5pdGl6ZWRDb250ZW50IGZyb20gJ2dvb2c6Z29vZy5zb3kuZGF0YS5TYW5pdGl6ZWRDb250ZW50JzsgLy8gZnJvbSAvL2phdmFzY3JpcHQvY2xvc3VyZS9zb3k6ZGF0YVxuaW1wb3J0IFNhbml0aXplZENvbnRlbnRLaW5kIGZyb20gJ2dvb2c6Z29vZy5zb3kuZGF0YS5TYW5pdGl6ZWRDb250ZW50S2luZCc7IC8vIGZyb20gLy9qYXZhc2NyaXB0L2Nsb3N1cmUvc295OmRhdGFcbmltcG9ydCBTYW5pdGl6ZWRIdG1sIGZyb20gJ2dvb2c6Z29vZy5zb3kuZGF0YS5TYW5pdGl6ZWRIdG1sJzsgLy8gZnJvbSAvL2phdmFzY3JpcHQvY2xvc3VyZS9zb3k6ZGF0YVxuaW1wb3J0IFNhbml0aXplZEh0bWxBdHRyaWJ1dGUgZnJvbSAnZ29vZzpnb29nLnNveS5kYXRhLlNhbml0aXplZEh0bWxBdHRyaWJ1dGUnOyAvLyBmcm9tIC8vamF2YXNjcmlwdC9jbG9zdXJlL3NveTpkYXRhXG5pbXBvcnQgU2FuaXRpemVkSnMgZnJvbSAnZ29vZzpnb29nLnNveS5kYXRhLlNhbml0aXplZEpzJzsgLy8gZnJvbSAvL2phdmFzY3JpcHQvY2xvc3VyZS9zb3k6ZGF0YVxuaW1wb3J0IFNhbml0aXplZFVyaSBmcm9tICdnb29nOmdvb2cuc295LmRhdGEuU2FuaXRpemVkVXJpJzsgLy8gZnJvbSAvL2phdmFzY3JpcHQvY2xvc3VyZS9zb3k6ZGF0YVxuaW1wb3J0ICogYXMgZ29vZ1N0cmluZyBmcm9tICdnb29nOmdvb2cuc3RyaW5nJzsgIC8vIGZyb20gLy9qYXZhc2NyaXB0L2Nsb3N1cmUvc3RyaW5nXG5pbXBvcnQgKiBhcyBzb3kgZnJvbSAnZ29vZzpzb3knOyAgLy8gZnJvbSAvL2phdmFzY3JpcHQvdGVtcGxhdGUvc295OnNveV91c2Vnb29nX2pzXG5pbXBvcnQge2lzQXR0cmlidXRlfSBmcm9tICdnb29nOnNveS5jaGVja3MnOyAgLy8gZnJvbSAvL2phdmFzY3JpcHQvdGVtcGxhdGUvc295OmNoZWNrc1xuaW1wb3J0IHtvcmRhaW5TYW5pdGl6ZWRIdG1sfSBmcm9tICdnb29nOnNveWRhdGEuVkVSWV9VTlNBRkUnOyAgLy8gZnJvbSAvL2phdmFzY3JpcHQvdGVtcGxhdGUvc295OnNveV91c2Vnb29nX2pzXG5pbXBvcnQgKiBhcyBpbmNyZW1lbnRhbGRvbSBmcm9tICdpbmNyZW1lbnRhbGRvbSc7ICAvLyBmcm9tIC8vdGhpcmRfcGFydHkvamF2YXNjcmlwdC9pbmNyZW1lbnRhbF9kb206aW5jcmVtZW50YWxkb21cblxuaW1wb3J0IHtJbmNyZW1lbnRhbERvbVJlbmRlcmVyLCBpc01hdGNoaW5nS2V5LCBwYXRjaCwgcGF0Y2hPdXRlciwgc2VyaWFsaXplS2V5fSBmcm9tICcuL2FwaV9pZG9tJztcbmltcG9ydCB7SWRvbUZ1bmN0aW9uLCBQYXRjaEZ1bmN0aW9uLCBTb3lFbGVtZW50fSBmcm9tICcuL2VsZW1lbnRfbGliX2lkb20nO1xuaW1wb3J0IHtnZXRTb3lVbnR5cGVkfSBmcm9tICcuL2dsb2JhbCc7XG5cbi8vIERlY2xhcmUgcHJvcGVydGllcyB0aGF0IG5lZWQgdG8gYmUgYXBwbGllZCBub3QgYXMgYXR0cmlidXRlcyBidXQgYXNcbi8vIGFjdHVhbCBET00gcHJvcGVydGllcy5cbmNvbnN0IHthdHRyaWJ1dGVzLCBnZXRLZXksIGlzRGF0YUluaXRpYWxpemVkfSA9IGluY3JlbWVudGFsZG9tO1xuXG5jb25zdCBkZWZhdWx0SWRvbVJlbmRlcmVyID0gbmV3IEluY3JlbWVudGFsRG9tUmVuZGVyZXIoKTtcblxudHlwZSBJZG9tVGVtcGxhdGU8QSwgQj4gPVxuICAgIChpZG9tOiBJbmNyZW1lbnRhbERvbVJlbmRlcmVyLCBwYXJhbXM6IEEsIGlqRGF0YTogQikgPT4gdm9pZDtcbnR5cGUgU295VGVtcGxhdGU8QSwgQj4gPSAocGFyYW1zOiBBLCBpakRhdGE6IEIpID0+IHN0cmluZ3xTYW5pdGl6ZWRDb250ZW50O1xudHlwZSBMZXRGdW5jdGlvbiA9IChpZG9tOiBJbmNyZW1lbnRhbERvbVJlbmRlcmVyKSA9PiB2b2lkO1xudHlwZSBUZW1wbGF0ZTxBLCBCPiA9IElkb21UZW1wbGF0ZTxBLCBCPnxTb3lUZW1wbGF0ZTxBLCBCPjtcblxuLy8gdHNsaW50OmRpc2FibGUtbmV4dC1saW5lOm5vLWFueVxuYXR0cmlidXRlc1snY2hlY2tlZCddID0gKGVsOiBFbGVtZW50LCBuYW1lOiBzdHJpbmcsIHZhbHVlOiBhbnkpID0+IHtcbiAgLy8gV2UgZG9uJ3QgdXNlICEhdmFsdWUgYmVjYXVzZTpcbiAgLy8gMS4gSWYgdmFsdWUgaXMgJycgKHRoaXMgaXMgdGhlIGNhc2Ugd2hlcmUgYSB1c2VyIHVzZXMgPGRpdiBjaGVja2VkIC8+KSxcbiAgLy8gICAgdGhlIGNoZWNrZWQgdmFsdWUgc2hvdWxkIGJlIHRydWUsIGJ1dCAnJyBpcyBmYWxzeS5cbiAgLy8gMi4gSWYgdmFsdWUgaXMgJ2ZhbHNlJywgdGhlIGNoZWNrZWQgdmFsdWUgc2hvdWxkIGJlIGZhbHNlLCBidXRcbiAgLy8gICAgJ2ZhbHNlJyBpcyB0cnV0aHkuXG4gIGVsLnNldEF0dHJpYnV0ZSgnY2hlY2tlZCcsIHZhbHVlKTtcbiAgKGVsIGFzIEhUTUxJbnB1dEVsZW1lbnQpLmNoZWNrZWQgPVxuICAgICAgISh2YWx1ZSA9PT0gZmFsc2UgfHwgdmFsdWUgPT09ICdmYWxzZScgfHwgdmFsdWUgPT09IHVuZGVmaW5lZCk7XG59O1xuXG4vLyB0c2xpbnQ6ZGlzYWJsZS1uZXh0LWxpbmU6bm8tYW55XG5hdHRyaWJ1dGVzWyd2YWx1ZSddID0gKGVsOiBFbGVtZW50LCBuYW1lOiBzdHJpbmcsIHZhbHVlOiBhbnkpID0+IHtcbiAgKGVsIGFzIEhUTUxJbnB1dEVsZW1lbnQpLnZhbHVlID0gdmFsdWU7XG4gIGVsLnNldEF0dHJpYnV0ZSgndmFsdWUnLCB2YWx1ZSk7XG59O1xuXG4vLyBTb3kgdXNlcyB0aGUge2tleX0gY29tbWFuZCBzeW50YXgsIHJhdGhlciB0aGFuIEhUTUwgYXR0cmlidXRlcywgdG9cbi8vIGluZGljYXRlIGVsZW1lbnQga2V5cy5cbmluY3JlbWVudGFsZG9tLnNldEtleUF0dHJpYnV0ZU5hbWUoJ3NveS1zZXJ2ZXIta2V5Jyk7XG5cbi8qKlxuICogVHJpZXMgdG8gZmluZCBhbiBleGlzdGluZyBTb3kgZWxlbWVudCwgaWYgaXQgZXhpc3RzLiBPdGhlcndpc2UsIGl0IGNyZWF0ZXNcbiAqIG9uZS4gQWZ0ZXJ3YXJkcywgaXQgcXVldWVzIHVwIGEgU295IGVsZW1lbnQgKHNlZSBkb2NzIGZvciBxdWV1ZVNveUVsZW1lbnQpXG4gKiBhbmQgdGhlbiBwcm9jZWVkcyB0byByZW5kZXIgdGhlIFNveSBlbGVtZW50LlxuICovXG5mdW5jdGlvbiBoYW5kbGVTb3lFbGVtZW50PERBVEEsIFQgZXh0ZW5kcyBTb3lFbGVtZW50PERBVEEsIHt9Pj4oXG4gICAgaW5jcmVtZW50YWxkb206IEluY3JlbWVudGFsRG9tUmVuZGVyZXIsXG4gICAgZWxlbWVudENsYXNzQ3RvcjogbmV3IChkYXRhOiBEQVRBLCBpajogdW5rbm93bikgPT4gVCxcbiAgICBmaXJzdEVsZW1lbnRLZXk6IHN0cmluZywgZGF0YTogREFUQSwgaWpEYXRhOiB1bmtub3duKSB7XG4gIGNvbnN0IHNveUVsZW1lbnRLZXkgPSBmaXJzdEVsZW1lbnRLZXkgKyBpbmNyZW1lbnRhbGRvbS5nZXRDdXJyZW50S2V5U3RhY2soKTtcbiAgbGV0IGN1cnJlbnRQb2ludGVyID0gaW5jcmVtZW50YWxkb20uY3VycmVudFBvaW50ZXIoKTtcbiAgbGV0IGVsOiBUfG51bGwgPSBudWxsO1xuICB3aGlsZSAoY3VycmVudFBvaW50ZXIgIT0gbnVsbCkge1xuICAgIGNvbnN0IG1heWJlU295RWwgPSBnZXRTb3lVbnR5cGVkKGN1cnJlbnRQb2ludGVyKSBhcyBUO1xuICAgIC8vIFdlIGNhbm5vdCB1c2UgdGhlIGN1cnJlbnQga2V5IG9mIHRoZSBlbGVtZW50IGJlY2F1c2UgbWFueSBsYXllcnNcbiAgICAvLyBvZiB0ZW1wbGF0ZSBjYWxscyBtYXkgaGF2ZSBoYXBwZW5lZC4gV2UgY2FuIG9ubHkgYmUgc3VyZSB0aGF0IHRoZSBTb3lcbiAgICAvLyBlbGVtZW50IHdhcyB0aGUgc2FtZSBpZiB0aGUga2V5IGNvbnN0cnVjdGVkIGlzIG1hdGNoaW5nIHRoZSBrZXkgY3VycmVudFxuICAgIC8vIHdoZW4gdGhlIHtlbGVtZW50fSBjb21tYW5kIHdhcyBjcmVhdGVkLlxuICAgIGlmIChtYXliZVNveUVsIGluc3RhbmNlb2YgZWxlbWVudENsYXNzQ3RvciAmJlxuICAgICAgICBpc01hdGNoaW5nS2V5KHNveUVsZW1lbnRLZXksIG1heWJlU295RWwua2V5KSkge1xuICAgICAgZWwgPSBtYXliZVNveUVsO1xuICAgICAgYnJlYWs7XG4gICAgfVxuICAgIGN1cnJlbnRQb2ludGVyID0gY3VycmVudFBvaW50ZXIubmV4dFNpYmxpbmc7XG4gIH1cbiAgaWYgKCFlbCkge1xuICAgIGVsID0gbmV3IGVsZW1lbnRDbGFzc0N0b3IoZGF0YSwgaWpEYXRhKTtcbiAgICBlbC5rZXkgPSBzb3lFbGVtZW50S2V5O1xuICB9XG4gIGVsLnF1ZXVlU295RWxlbWVudChpbmNyZW1lbnRhbGRvbSwgZGF0YSk7XG4gIGVsLnJlbmRlckludGVybmFsKGluY3JlbWVudGFsZG9tLCBkYXRhKTtcbiAgcmV0dXJuIGVsO1xufVxuXG4vLyB0c2xpbnQ6ZGlzYWJsZS1uZXh0LWxpbmU6bm8tYW55IEF0dGFjaGluZyBhcmJpdHJhcnkgYXR0cmlidXRlcyB0byBmdW5jdGlvbi5cbmZ1bmN0aW9uIG1ha2VIdG1sKGlkb21GbjogYW55KTogSWRvbUZ1bmN0aW9uIHtcbiAgaWRvbUZuLnRvU3RyaW5nID0gKHJlbmRlcmVyOiBJbmNyZW1lbnRhbERvbVJlbmRlcmVyID0gZGVmYXVsdElkb21SZW5kZXJlcikgPT5cbiAgICAgIGh0bWxUb1N0cmluZyhpZG9tRm4sIHJlbmRlcmVyKTtcbiAgaWRvbUZuLnRvQm9vbGVhbiA9ICgpID0+IHRvQm9vbGVhbihpZG9tRm4pO1xuICBpZG9tRm4uY29udGVudEtpbmQgPSBTYW5pdGl6ZWRDb250ZW50S2luZC5IVE1MO1xuICByZXR1cm4gaWRvbUZuIGFzIElkb21GdW5jdGlvbjtcbn1cblxuLy8gdHNsaW50OmRpc2FibGUtbmV4dC1saW5lOm5vLWFueSBBdHRhY2hpbmcgYXJiaXRyYXJ5IGF0dHJpYnV0ZXMgdG8gZnVuY3Rpb24uXG5mdW5jdGlvbiBtYWtlQXR0cmlidXRlcyhpZG9tRm46IGFueSk6IElkb21GdW5jdGlvbiB7XG4gIGlkb21Gbi50b1N0cmluZyA9ICgpID0+IGF0dHJpYnV0ZXNUb1N0cmluZyhpZG9tRm4pO1xuICBpZG9tRm4udG9Cb29sZWFuID0gKCkgPT4gdG9Cb29sZWFuKGlkb21Gbik7XG4gIGlkb21Gbi5jb250ZW50S2luZCA9IFNhbml0aXplZENvbnRlbnRLaW5kLkFUVFJJQlVURVM7XG4gIHJldHVybiBpZG9tRm4gYXMgSWRvbUZ1bmN0aW9uO1xufVxuXG4vKipcbiAqIFRPRE8odG9tbmd1eWVuKTogSXNzdWUgYSB3YXJuaW5nIGluIHRoZXNlIGNhc2VzIHNvIHRoYXQgdXNlcnMga25vdyB0aGF0XG4gKiBleHBlbnNpdmUgYmVoYXZpb3IgaXMgaGFwcGVuaW5nLlxuICovXG5mdW5jdGlvbiBodG1sVG9TdHJpbmcoXG4gICAgZm46IExldEZ1bmN0aW9uLCByZW5kZXJlcjogSW5jcmVtZW50YWxEb21SZW5kZXJlciA9IGRlZmF1bHRJZG9tUmVuZGVyZXIpIHtcbiAgY29uc3QgZWwgPSBkb2N1bWVudC5jcmVhdGVFbGVtZW50KCdkaXYnKTtcbiAgcGF0Y2goZWwsICgpID0+IHtcbiAgICBmbihyZW5kZXJlcik7XG4gIH0pO1xuICByZXR1cm4gZWwuaW5uZXJIVE1MO1xufVxuXG5mdW5jdGlvbiBhdHRyaWJ1dGVzRmFjdG9yeShmbjogUGF0Y2hGdW5jdGlvbik6IFBhdGNoRnVuY3Rpb24ge1xuICByZXR1cm4gKCkgPT4ge1xuICAgIGluY3JlbWVudGFsZG9tLm9wZW4oJ2RpdicpO1xuICAgIGZuKGRlZmF1bHRJZG9tUmVuZGVyZXIpO1xuICAgIGluY3JlbWVudGFsZG9tLmFwcGx5QXR0cnMoKTtcbiAgICBpbmNyZW1lbnRhbGRvbS5jbG9zZSgpO1xuICB9O1xufVxuXG4vKipcbiAqIFRPRE8odG9tbmd1eWVuKTogSXNzdWUgYSB3YXJuaW5nIGluIHRoZXNlIGNhc2VzIHNvIHRoYXQgdXNlcnMga25vdyB0aGF0XG4gKiBleHBlbnNpdmUgYmVoYXZpb3IgaXMgaGFwcGVuaW5nLlxuICovXG5mdW5jdGlvbiBhdHRyaWJ1dGVzVG9TdHJpbmcoZm46IFBhdGNoRnVuY3Rpb24pOiBzdHJpbmcge1xuICBjb25zdCBlbEZuID0gYXR0cmlidXRlc0ZhY3RvcnkoZm4pO1xuICBjb25zdCBlbCA9IGRvY3VtZW50LmNyZWF0ZUVsZW1lbnQoJ2RpdicpO1xuICBwYXRjaE91dGVyKGVsLCBlbEZuKTtcbiAgY29uc3Qgczogc3RyaW5nW10gPSBbXTtcbiAgZm9yIChsZXQgaSA9IDA7IGkgPCBlbC5hdHRyaWJ1dGVzLmxlbmd0aDsgaSsrKSB7XG4gICAgcy5wdXNoKGAke2VsLmF0dHJpYnV0ZXNbaV0ubmFtZX09JHtlbC5hdHRyaWJ1dGVzW2ldLnZhbHVlfWApO1xuICB9XG4gIC8vIFRoZSBzb3J0IGlzIGltcG9ydGFudCBiZWNhdXNlIGF0dHJpYnV0ZSBvcmRlciB2YXJpZXMgcGVyIGJyb3dzZXIuXG4gIHJldHVybiBzLnNvcnQoKS5qb2luKCcgJyk7XG59XG5cbmZ1bmN0aW9uIHRvQm9vbGVhbihmbjogSWRvbUZ1bmN0aW9uKSB7XG4gIHJldHVybiBmbi50b1N0cmluZygpLmxlbmd0aCA+IDA7XG59XG5cbi8qKlxuICogQ2FsbHMgYW4gZXhwcmVzc2lvbiBpbiBjYXNlIG9mIGEgZnVuY3Rpb24gb3Igb3V0cHV0cyBpdCBhcyB0ZXh0IGNvbnRlbnQuXG4gKi9cbmZ1bmN0aW9uIHJlbmRlckR5bmFtaWNDb250ZW50KFxuICAgIGluY3JlbWVudGFsZG9tOiBJbmNyZW1lbnRhbERvbVJlbmRlcmVyLCBleHByOiB1bmtub3duKSB7XG4gIC8vIFRPRE8obHVrZXMpOiBjaGVjayBjb250ZW50IGtpbmQgPT0gaHRtbFxuICBpZiAodHlwZW9mIGV4cHIgPT09ICdmdW5jdGlvbicpIHtcbiAgICAvLyBUaGUgU295IGNvbXBpbGVyIHdpbGwgdmFsaWRhdGUgdGhlIGNvbnRlbnQga2luZCBvZiB0aGUgcGFyYW1ldGVyLlxuICAgIGV4cHIoaW5jcmVtZW50YWxkb20pO1xuICB9IGVsc2Uge1xuICAgIGluY3JlbWVudGFsZG9tLnRleHQoU3RyaW5nKGV4cHIpKTtcbiAgfVxufVxuXG4vKipcbiAqIE1hdGNoZXMgYW4gSFRNTCBhdHRyaWJ1dGUgbmFtZSB2YWx1ZSBwYWlyLlxuICogTmFtZSBpcyBpbiBncm91cCAxLiAgVmFsdWUsIGlmIHByZXNlbnQsIGlzIGluIG9uZSBvZiBncm91cCAoMiwzLDQpXG4gKiBkZXBlbmRpbmcgb24gaG93IGl0J3MgcXVvdGVkLlxuICpcbiAqIFRoaXMgUmVnRXhwIHdhcyBkZXJpdmVkIGZyb20gdmlzdWFsIGluc3BlY3Rpb24gb2ZcbiAqICAgaHRtbC5zcGVjLndoYXR3Zy5vcmcvbXVsdGlwYWdlL3BhcnNpbmcuaHRtbCNiZWZvcmUtYXR0cmlidXRlLW5hbWUtc3RhdGVcbiAqIGFuZCBmb2xsb3dpbmcgc3RhdGVzLlxuICovXG5jb25zdCBodG1sQXR0cmlidXRlUmVnRXhwOiBSZWdFeHAgPVxuICAgIC8oW15cXHRcXG5cXGZcXHIgLz49XSspW1xcdFxcblxcZlxcciBdKig/Oj1bXFx0XFxuXFxmXFxyIF0qKD86XCIoW15cIl0qKVwiP3wnKFteJ10qKSc/fChbXlxcdFxcblxcZlxcciA+XSopKSk/L2c7XG5cbmZ1bmN0aW9uIHNwbGl0QXR0cmlidXRlcyhhdHRyaWJ1dGVzOiBzdHJpbmcpIHtcbiAgY29uc3QgbmFtZVZhbHVlUGFpcnM6IHN0cmluZ1tdW10gPSBbXTtcbiAgU3RyaW5nKGF0dHJpYnV0ZXMpLnJlcGxhY2UoaHRtbEF0dHJpYnV0ZVJlZ0V4cCwgKF8sIG5hbWUsIGRxLCBzcSwgdXEpID0+IHtcbiAgICBuYW1lVmFsdWVQYWlycy5wdXNoKFxuICAgICAgICBbbmFtZSwgZ29vZ1N0cmluZy51bmVzY2FwZUVudGl0aWVzKGRxIHx8IHNxIHx8IHVxIHx8ICcnKV0pO1xuICAgIHJldHVybiAnICc7XG4gIH0pO1xuICByZXR1cm4gbmFtZVZhbHVlUGFpcnM7XG59XG5cbi8qKlxuICogQ2FsbHMgYW4gZXhwcmVzc2lvbiBpbiBjYXNlIG9mIGEgZnVuY3Rpb24gb3Igb3V0cHV0cyBpdCBhcyB0ZXh0IGNvbnRlbnQuXG4gKi9cbmZ1bmN0aW9uIGNhbGxEeW5hbWljQXR0cmlidXRlczxBLCBCPihcbiAgICBpbmNyZW1lbnRhbGRvbTogSW5jcmVtZW50YWxEb21SZW5kZXJlcixcbiAgICAvLyB0c2xpbnQ6ZGlzYWJsZS1uZXh0LWxpbmU6bm8tYW55XG4gICAgZXhwcjogVGVtcGxhdGU8QSwgQj4sIGRhdGE6IEEsIGlqOiBCKSB7XG4gIC8vIHRzbGludDpkaXNhYmxlLW5leHQtbGluZTpuby1hbnkgQXR0YWNoaW5nIGFyYml0cmFyeSBhdHRyaWJ1dGVzIHRvIGZ1bmN0aW9uLlxuICBjb25zdCB0eXBlID0gKGV4cHIgYXMgYW55IGFzIElkb21GdW5jdGlvbikuY29udGVudEtpbmQ7XG4gIGlmICh0eXBlID09PSBTYW5pdGl6ZWRDb250ZW50S2luZC5BVFRSSUJVVEVTKSB7XG4gICAgKGV4cHIgYXMgSWRvbVRlbXBsYXRlPEEsIEI+KShpbmNyZW1lbnRhbGRvbSwgZGF0YSwgaWopO1xuICB9IGVsc2Uge1xuICAgIGxldCB2YWw6IHN0cmluZ3xTYW5pdGl6ZWRIdG1sQXR0cmlidXRlO1xuICAgIGlmICh0eXBlID09PSBTYW5pdGl6ZWRDb250ZW50S2luZC5IVE1MKSB7XG4gICAgICAvLyBUaGlzIGVmZmVjdGl2ZWx5IG5lZ2F0ZXMgdGhlIHZhbHVlIG9mIHNwbGl0dGluZyBhIHN0cmluZy4gSG93ZXZlcixcbiAgICAgIC8vIFRoaXMgY2FuIGJlIHJlbW92ZWQgaWYgU295IGRlY2lkZXMgdG8gdHJlYXQgYXR0cmlidXRlIHByaW50aW5nXG4gICAgICAvLyBhbmQgYXR0cmlidXRlIG5hbWVzIGRpZmZlcmVudGx5LlxuICAgICAgdmFsID0gc295LiQkZmlsdGVySHRtbEF0dHJpYnV0ZXMoaHRtbFRvU3RyaW5nKCgpID0+IHtcbiAgICAgICAgKGV4cHIgYXMgSWRvbVRlbXBsYXRlPEEsIEI+KShkZWZhdWx0SWRvbVJlbmRlcmVyLCBkYXRhLCBpaik7XG4gICAgICB9KSk7XG4gICAgfSBlbHNlIHtcbiAgICAgIHZhbCA9IChleHByIGFzIFNveVRlbXBsYXRlPEEsIEI+KShkYXRhLCBpaikgYXMgU2FuaXRpemVkSHRtbEF0dHJpYnV0ZTtcbiAgICB9XG4gICAgcHJpbnREeW5hbWljQXR0cihpbmNyZW1lbnRhbGRvbSwgdmFsKTtcbiAgfVxufVxuXG4vKipcbiAqIFByaW50cyBhbiBleHByZXNzaW9uIHdob3NlIHR5cGUgaXMgbm90IHN0YXRpY2FsbHkga25vd24gdG8gYmUgb2YgdHlwZVxuICogXCJhdHRyaWJ1dGVzXCIuIFRoZSBleHByZXNzaW9uIGlzIHRlc3RlZCBhdCBydW50aW1lIGFuZCBldmFsdWF0ZWQgZGVwZW5kaW5nXG4gKiBvbiB3aGF0IHR5cGUgaXQgaXMuIEZvciBleGFtcGxlLCBpZiBhIHN0cmluZyBpcyBwcmludGVkIGluIGEgY29udGV4dFxuICogdGhhdCBleHBlY3RzIGF0dHJpYnV0ZXMsIHRoZSBzdHJpbmcgaXMgZXZhbHVhdGVkIGR5bmFtaWNhbGx5IHRvIGNvbXB1dGVcbiAqIGF0dHJpYnV0ZXMuXG4gKi9cbmZ1bmN0aW9uIHByaW50RHluYW1pY0F0dHIoXG4gICAgaW5jcmVtZW50YWxkb206IEluY3JlbWVudGFsRG9tUmVuZGVyZXIsXG4gICAgZXhwcjogU2FuaXRpemVkSHRtbEF0dHJpYnV0ZXxzdHJpbmd8Ym9vbGVhbnxJZG9tRnVuY3Rpb24pIHtcbiAgaWYgKGdvb2cuaXNGdW5jdGlvbihleHByKSAmJlxuICAgICAgKGV4cHIgYXMgSWRvbUZ1bmN0aW9uKS5jb250ZW50S2luZCA9PT0gU2FuaXRpemVkQ29udGVudEtpbmQuQVRUUklCVVRFUykge1xuICAgIC8vIHRzbGludDpkaXNhYmxlLW5leHQtbGluZTpuby1hbnlcbiAgICAoZXhwciBhcyBhbnkgYXMgTGV0RnVuY3Rpb24pKGluY3JlbWVudGFsZG9tKTtcbiAgICByZXR1cm47XG4gIH1cbiAgY29uc3QgYXR0cmlidXRlcyA9IHNwbGl0QXR0cmlidXRlcyhleHByLnRvU3RyaW5nKCkpO1xuICBjb25zdCBpc0V4cHJBdHRyaWJ1dGUgPSBpc0F0dHJpYnV0ZShleHByKTtcbiAgZm9yIChjb25zdCBhdHRyaWJ1dGUgb2YgYXR0cmlidXRlcykge1xuICAgIGNvbnN0IGF0dHJOYW1lID0gaXNFeHByQXR0cmlidXRlID8gYXR0cmlidXRlWzBdIDpcbiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIHNveS4kJGZpbHRlckh0bWxBdHRyaWJ1dGVzKGF0dHJpYnV0ZVswXSk7XG4gICAgaWYgKGF0dHJOYW1lID09PSAnelNveXonKSB7XG4gICAgICBpbmNyZW1lbnRhbGRvbS5hdHRyKGF0dHJOYW1lLCAnJyk7XG4gICAgfSBlbHNlIHtcbiAgICAgIGluY3JlbWVudGFsZG9tLmF0dHIoU3RyaW5nKGF0dHJOYW1lKSwgU3RyaW5nKGF0dHJpYnV0ZVsxXSkpO1xuICAgIH1cbiAgfVxufVxuXG4vKipcbiAqIENhbGxzIGFuIGV4cHJlc3Npb24gaW4gY2FzZSBvZiBhIGZ1bmN0aW9uIG9yIG91dHB1dHMgaXQgYXMgdGV4dCBjb250ZW50LlxuICovXG5mdW5jdGlvbiBjYWxsRHluYW1pY0hUTUw8QSwgQj4oXG4gICAgaW5jcmVtZW50YWxkb206IEluY3JlbWVudGFsRG9tUmVuZGVyZXIsIGV4cHI6IFRlbXBsYXRlPEEsIEI+LCBkYXRhOiBBLFxuICAgIGlqOiBCKSB7XG4gIC8vIHRzbGludDpkaXNhYmxlLW5leHQtbGluZTpuby1hbnkgQXR0YWNoaW5nIGFyYml0cmFyeSBhdHRyaWJ1dGVzIHRvIGZ1bmN0aW9uLlxuICBjb25zdCB0eXBlID0gKGV4cHIgYXMgYW55IGFzIElkb21GdW5jdGlvbikuY29udGVudEtpbmQ7XG4gIGlmICh0eXBlID09PSBTYW5pdGl6ZWRDb250ZW50S2luZC5IVE1MKSB7XG4gICAgKGV4cHIgYXMgSWRvbVRlbXBsYXRlPEEsIEI+KShpbmNyZW1lbnRhbGRvbSwgZGF0YSwgaWopO1xuICB9IGVsc2UgaWYgKHR5cGUgPT09IFNhbml0aXplZENvbnRlbnRLaW5kLkFUVFJJQlVURVMpIHtcbiAgICBjb25zdCB2YWwgPSBhdHRyaWJ1dGVzVG9TdHJpbmcoKCkgPT4ge1xuICAgICAgKGV4cHIgYXMgSWRvbVRlbXBsYXRlPEEsIEI+KShkZWZhdWx0SWRvbVJlbmRlcmVyLCBkYXRhLCBpaik7XG4gICAgfSk7XG4gICAgaW5jcmVtZW50YWxkb20udGV4dCh2YWwpO1xuICB9IGVsc2Uge1xuICAgIGNvbnN0IHZhbCA9IChleHByIGFzIFNveVRlbXBsYXRlPEEsIEI+KShkYXRhLCBpaik7XG4gICAgaW5jcmVtZW50YWxkb20udGV4dChTdHJpbmcodmFsKSk7XG4gIH1cbn1cblxuZnVuY3Rpb24gY2FsbER5bmFtaWNDc3M8QSwgQj4oXG4gICAgLy8gdHNsaW50OmRpc2FibGUtbmV4dC1saW5lOm5vLWFueSBBdHRhY2hpbmcgIGF0dHJpYnV0ZXMgdG8gZnVuY3Rpb24uXG4gICAgaW5jcmVtZW50YWxkb206IEluY3JlbWVudGFsRG9tUmVuZGVyZXIsIGV4cHI6IChhOiBBLCBiOiBCKSA9PiBhbnksIGRhdGE6IEEsXG4gICAgaWo6IEIpIHtcbiAgY29uc3QgdmFsID0gY2FsbER5bmFtaWNUZXh0PEEsIEI+KGV4cHIsIGRhdGEsIGlqLCBzb3kuJCRmaWx0ZXJDc3NWYWx1ZSk7XG4gIGluY3JlbWVudGFsZG9tLnRleHQoU3RyaW5nKHZhbCkpO1xufVxuXG5mdW5jdGlvbiBjYWxsRHluYW1pY0pzPEEsIEI+KFxuICAgIC8vIHRzbGludDpkaXNhYmxlLW5leHQtbGluZTpuby1hbnkgQXR0YWNoaW5nIGF0dHJpYnV0ZXMgdG8gZnVuY3Rpb24uXG4gICAgaW5jcmVtZW50YWxkb206IEluY3JlbWVudGFsRG9tUmVuZGVyZXIsIGV4cHI6IChhOiBBLCBiOiBCKSA9PiBhbnksIGRhdGE6IEEsXG4gICAgaWo6IEIpIHtcbiAgY29uc3QgdmFsID0gY2FsbER5bmFtaWNUZXh0PEEsIEI+KGV4cHIsIGRhdGEsIGlqLCBzb3kuJCRlc2NhcGVKc1ZhbHVlKTtcbiAgaW5jcmVtZW50YWxkb20udGV4dChTdHJpbmcodmFsKSk7XG59XG5cbi8qKlxuICogQ2FsbHMgYW4gZXhwcmVzc2lvbiBhbmQgY29lcmNlcyBpdCB0byBhIHN0cmluZyBmb3IgY2FzZXMgd2hlcmUgYW4gSURPTVxuICogZnVuY3Rpb24gbmVlZHMgdG8gYmUgY29uY2F0dGVkIHRvIGEgc3RyaW5nLlxuICovXG5mdW5jdGlvbiBjYWxsRHluYW1pY1RleHQ8QSwgQj4oXG4gICAgLy8gdHNsaW50OmRpc2FibGUtbmV4dC1saW5lOm5vLWFueVxuICAgIGV4cHI6IFRlbXBsYXRlPEEsIEI+LCBkYXRhOiBBLCBpajogQiwgZXNjRm4/OiAoaTogc3RyaW5nKSA9PiBzdHJpbmcpIHtcbiAgY29uc3QgdHJhbnNmb3JtRm4gPSBlc2NGbiA/IGVzY0ZuIDogKGE6IHN0cmluZykgPT4gYTtcbiAgLy8gdHNsaW50OmRpc2FibGUtbmV4dC1saW5lOm5vLWFueSBBdHRhY2hpbmcgYXJiaXRyYXJ5IGF0dHJpYnV0ZXMgdG8gZnVuY3Rpb24uXG4gIGNvbnN0IHR5cGUgPSAoZXhwciBhcyBhbnkgYXMgSWRvbUZ1bmN0aW9uKS5jb250ZW50S2luZDtcbiAgbGV0IHZhbDogc3RyaW5nfFNhbml0aXplZENvbnRlbnQ7XG4gIGlmICh0eXBlID09PSBTYW5pdGl6ZWRDb250ZW50S2luZC5IVE1MKSB7XG4gICAgdmFsID0gdHJhbnNmb3JtRm4oaHRtbFRvU3RyaW5nKCgpID0+IHtcbiAgICAgIChleHByIGFzIElkb21UZW1wbGF0ZTxBLCBCPikoZGVmYXVsdElkb21SZW5kZXJlciwgZGF0YSwgaWopO1xuICAgIH0pKTtcbiAgfSBlbHNlIGlmICh0eXBlID09PSBTYW5pdGl6ZWRDb250ZW50S2luZC5BVFRSSUJVVEVTKSB7XG4gICAgdmFsID0gdHJhbnNmb3JtRm4oYXR0cmlidXRlc1RvU3RyaW5nKCgpID0+IHtcbiAgICAgIChleHByIGFzIElkb21UZW1wbGF0ZTxBLCBCPikoZGVmYXVsdElkb21SZW5kZXJlciwgZGF0YSwgaWopO1xuICAgIH0pKTtcbiAgfSBlbHNlIHtcbiAgICB2YWwgPSAoZXhwciBhcyBTb3lUZW1wbGF0ZTxBLCBCPikoZGF0YSwgaWopO1xuICB9XG4gIHJldHVybiB2YWw7XG59XG5cbmRlY2xhcmUgZ2xvYmFsIHtcbiAgaW50ZXJmYWNlIEVsZW1lbnQge1xuICAgIF9faW5uZXJIVE1MOiBzdHJpbmc7XG4gIH1cbn1cblxuLyoqXG4gKiBQcmludHMgYW4gZXhwcmVzc2lvbiBkZXBlbmRpbmcgb24gaXRzIHR5cGUuXG4gKi9cbmZ1bmN0aW9uIHByaW50KFxuICAgIGluY3JlbWVudGFsZG9tOiBJbmNyZW1lbnRhbERvbVJlbmRlcmVyLCBleHByOiB1bmtub3duLFxuICAgIGlzU2FuaXRpemVkQ29udGVudD86IGJvb2xlYW58dW5kZWZpbmVkKSB7XG4gIGlmIChleHByIGluc3RhbmNlb2YgU2FuaXRpemVkSHRtbCB8fCBpc1Nhbml0aXplZENvbnRlbnQpIHtcbiAgICBjb25zdCBjb250ZW50ID0gU3RyaW5nKGV4cHIpO1xuICAgIC8vIElmIHRoZSBzdHJpbmcgaGFzIG5vIDwgb3IgJiwgaXQncyBkZWZpbml0ZWx5IG5vdCBIVE1MLiBPdGhlcndpc2VcbiAgICAvLyBwcm9jZWVkIHdpdGggY2F1dGlvbi5cbiAgICBpZiAoY29udGVudC5pbmRleE9mKCc8JykgPCAwICYmIGNvbnRlbnQuaW5kZXhPZignJicpIDwgMCkge1xuICAgICAgaW5jcmVtZW50YWxkb20udGV4dChjb250ZW50KTtcbiAgICB9IGVsc2Uge1xuICAgICAgLy8gRm9yIEhUTUwgY29udGVudCB3ZSBuZWVkIHRvIGluc2VydCBhIGN1c3RvbSBlbGVtZW50IHdoZXJlIHdlIGNhbiBwbGFjZVxuICAgICAgLy8gdGhlIGNvbnRlbnQgd2l0aG91dCBpbmNyZW1lbnRhbCBkb20gbW9kaWZ5aW5nIGl0LlxuICAgICAgY29uc3QgZWwgPSBpbmNyZW1lbnRhbGRvbS5vcGVuKCdodG1sLWJsb2InKTtcbiAgICAgIGlmIChlbCAmJiBlbC5fX2lubmVySFRNTCAhPT0gY29udGVudCkge1xuICAgICAgICBnb29nU295LnJlbmRlckh0bWwoZWwsIG9yZGFpblNhbml0aXplZEh0bWwoY29udGVudCkpO1xuICAgICAgICBlbC5fX2lubmVySFRNTCA9IGNvbnRlbnQ7XG4gICAgICB9XG4gICAgICBpbmNyZW1lbnRhbGRvbS5za2lwKCk7XG4gICAgICBpbmNyZW1lbnRhbGRvbS5jbG9zZSgpO1xuICAgIH1cbiAgfSBlbHNlIHtcbiAgICByZW5kZXJEeW5hbWljQ29udGVudChpbmNyZW1lbnRhbGRvbSwgZXhwcik7XG4gIH1cbn1cblxuZnVuY3Rpb24gdmlzaXRIdG1sQ29tbWVudE5vZGUoXG4gICAgaW5jcmVtZW50YWxkb206IEluY3JlbWVudGFsRG9tUmVuZGVyZXIsIHZhbDogc3RyaW5nKSB7XG4gIGNvbnN0IGN1cnJOb2RlID0gaW5jcmVtZW50YWxkb20uY3VycmVudEVsZW1lbnQoKTtcbiAgaWYgKCFjdXJyTm9kZSkge1xuICAgIHJldHVybjtcbiAgfVxuICBpZiAoY3Vyck5vZGUubmV4dFNpYmxpbmcgIT0gbnVsbCAmJlxuICAgICAgY3Vyck5vZGUubmV4dFNpYmxpbmcubm9kZVR5cGUgPT09IE5vZGUuQ09NTUVOVF9OT0RFKSB7XG4gICAgY3Vyck5vZGUubmV4dFNpYmxpbmcudGV4dENvbnRlbnQgPSB2YWw7XG4gICAgLy8gVGhpcyBpcyB0aGUgY2FzZSB3aGVyZSB3ZSBhcmUgY3JlYXRpbmcgbmV3IERPTSBmcm9tIGFuIGVtcHR5IGVsZW1lbnQuXG4gIH0gZWxzZSB7XG4gICAgY3Vyck5vZGUuYXBwZW5kQ2hpbGQoZG9jdW1lbnQuY3JlYXRlQ29tbWVudCh2YWwpKTtcbiAgfVxuICBpbmNyZW1lbnRhbGRvbS5za2lwTm9kZSgpO1xufVxuXG5leHBvcnQge1xuICBTb3lFbGVtZW50IGFzICRTb3lFbGVtZW50LFxuICBwcmludCBhcyAkJHByaW50LFxuICBodG1sVG9TdHJpbmcgYXMgJCRodG1sVG9TdHJpbmcsXG4gIG1ha2VIdG1sIGFzICQkbWFrZUh0bWwsXG4gIG1ha2VBdHRyaWJ1dGVzIGFzICQkbWFrZUF0dHJpYnV0ZXMsXG4gIGNhbGxEeW5hbWljSnMgYXMgJCRjYWxsRHluYW1pY0pzLFxuICBjYWxsRHluYW1pY0NzcyBhcyAkJGNhbGxEeW5hbWljQ3NzLFxuICBjYWxsRHluYW1pY0hUTUwgYXMgJCRjYWxsRHluYW1pY0hUTUwsXG4gIGNhbGxEeW5hbWljQXR0cmlidXRlcyBhcyAkJGNhbGxEeW5hbWljQXR0cmlidXRlcyxcbiAgY2FsbER5bmFtaWNUZXh0IGFzICQkY2FsbER5bmFtaWNUZXh0LFxuICBoYW5kbGVTb3lFbGVtZW50IGFzICQkaGFuZGxlU295RWxlbWVudCxcbiAgcHJpbnREeW5hbWljQXR0ciBhcyAkJHByaW50RHluYW1pY0F0dHIsXG4gIHZpc2l0SHRtbENvbW1lbnROb2RlIGFzICQkdmlzaXRIdG1sQ29tbWVudE5vZGUsXG59O1xuIl19