/*
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

/**
 * @fileoverview Helper utilities for incremental dom code generation in Soy.
 *
 */

goog.module('soy.idom');

var SanitizedHtml = goog.require('goog.soy.data.SanitizedHtml');
var incrementaldom = goog.require('incrementaldom');
var soy = goog.require('goog.soy');

/**
 * Calls an expression in case of a function or outputs it as text content.
 * @param {string|number|boolean|function()?} expr
 */
exports.renderDynamicContent = function(expr) {
  if (goog.isFunction(expr)) {
    expr();
  } else if (expr != null) {
    incrementaldom.text(expr);
  }
};

/**
 * Prints an expression depending on its type.
 * @param {!SanitizedHtml|string|number|boolean|function()} expr
 */
exports.print = function(expr) {
  if (expr instanceof SanitizedHtml) {
    // For HTML content we need to insert a custom element where we can place
    // the content without incremental dom modifying it.
    var el = incrementaldom.elementOpen('html-blob');
    var content = expr.toString();
    if (el.__innerHTML !== content) {
      soy.renderHtml(el, expr);
      el.__innerHTML = content;
    }
    incrementaldom.skip();
    incrementaldom.elementClose('html-blob');
  } else {
    exports.renderDynamicContent(expr);
  }
};
