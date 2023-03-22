/*
 * Copyright 2020 Google Inc.
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
 * Utility functions and classes for supporting template types.
 *
 * <p>
 * This file contains utilities that should only be called by Soy-generated
 * JS code. Please do not use these functions directly from your hand-written
 * code. Their names all start with '$$'.
 */

goog.module('soy.templates');
const {IjData} = goog.requireType('goog.soy');
const {IncrementalDomRenderer} = goog.requireType('google3.javascript.template.soy.api_idom');

/** @return {string} */
function zSoyZFunction() {
  return 'zSoyTemplatez';
}

/**
 * Marks a function as being a Soy template type.
 * @param {T} fn
 * @param {string=} name
 * @return {T} fn
 * @template T
 */
exports.$$markTemplate = function(fn, name) {
  if (goog.DEBUG) {
    fn.toString = function() {
      return '** FOR DEBUGGING ONLY: ' + name + ' **';
    };
  } else {
    fn.toString = zSoyZFunction;
  }
  return fn;
};

/**
 * @param {function(T, ?IjData=)} fn
 * @param {?} data
 * @return {function(T, ?IjData=)}
 * @template T
 */
exports.$$bindTemplateParams = function(fn, data) {
  const boundTemplate = function(opt_data, opt_ijData) {
    return fn(opt_data == null ? data : {...data, ...opt_data}, opt_ijData);
  };
  return exports.$$markTemplate(boundTemplate);
};

/**
 * @param {function(!IncrementalDomRenderer, T, ?IjData=)} fn
 * @param {?} data
 * @return {function(!IncrementalDomRenderer, T, ?IjData=)}
 * @template T
 */
exports.$$bindTemplateParamsForIdom = function(fn, data) {
  const boundTemplate = function(idomRenderer, opt_data, opt_ijData) {
    fn(idomRenderer, opt_data == null ? data : {...data, ...opt_data},
       opt_ijData);
  };
  /** @type {?} */ (boundTemplate).contentKind =
      /** @type {?} */ (fn).contentKind;
  return exports.$$markTemplate(boundTemplate);
};
