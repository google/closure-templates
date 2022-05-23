/*
 * Copyright 2018 Google Inc.
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
 * @fileoverview Provides Soy runtime checks for safe types.
 */

goog.module('soy.checks');

const asserts = goog.require('goog.asserts');

const {SanitizedContentKind, SanitizedCss, SanitizedHtml, SanitizedHtmlAttribute, SanitizedJs, SanitizedTrustedResourceUri, SanitizedUri} = goog.require('goog.soy.data');

/**
 * Checks whether a given value is of a given content kind.
 *
 * @param {?} value The value to be examined.
 * @param {!SanitizedContentKind} contentKind The desired content
 *     kind.
 * @param {!Object} constructor
 * @return {boolean} Whether the given value is of the given kind.
 */
const isContentKind_ = function(value, contentKind, constructor) {
  const ret = value != null && value.contentKind === contentKind;
  if (ret) {
    asserts.assert(value.constructor === constructor);
  }
  return ret;
};

/**
 * @param {?} value
 * @return {boolean}
 */
exports.isHtml = function(value) {
  return isContentKind_(value, SanitizedContentKind.HTML, SanitizedHtml);
};

/**
 * @param {?} value
 * @return {boolean}
 */
exports.isCss = function(value) {
  return isContentKind_(value, SanitizedContentKind.CSS, SanitizedCss);
};

/**
 * @param {?} value
 * @return {boolean}
 */
exports.isAttribute = function(value) {
  return isContentKind_(
      value, SanitizedContentKind.ATTRIBUTES, SanitizedHtmlAttribute);
};

/**
 * @param {?} value
 * @return {boolean}
 */
exports.isJS = function(value) {
  return isContentKind_(value, SanitizedContentKind.JS, SanitizedJs);
};

/**
 * @param {?} value
 * @return {boolean}
 */
exports.isTrustedResourceURI = function(value) {
  return isContentKind_(
      value, SanitizedContentKind.TRUSTED_RESOURCE_URI,
      SanitizedTrustedResourceUri);
};

/**
 * @param {?} value
 * @return {boolean}
 */
exports.isURI = function(value) {
  return isContentKind_(value, SanitizedContentKind.URI, SanitizedUri);
};
