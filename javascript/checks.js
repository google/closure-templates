/**
 * @fileoverview Provides Soy runtime checks for safe types.
 */

goog.module('soy.checks');
goog.module.declareLegacyNamespace();

var SanitizedContentKind = goog.require('goog.soy.data.SanitizedContentKind');
var SanitizedCss = goog.require('goog.soy.data.SanitizedCss');
var SanitizedHtml = goog.require('goog.soy.data.SanitizedHtml');
var SanitizedHtmlAttribute = goog.require('goog.soy.data.SanitizedHtmlAttribute');
var SanitizedJs = goog.require('goog.soy.data.SanitizedJs');
var SanitizedTrustedResourceUri = goog.require('goog.soy.data.SanitizedTrustedResourceUri');
var SanitizedUri = goog.require('goog.soy.data.SanitizedUri');
var UnsanitizedText = goog.require('goog.soy.data.UnsanitizedText');
var asserts = goog.require('goog.asserts');


/**
 * Checks whether a given value is of a given content kind.
 *
 * @param {?} value The value to be examined.
 * @param {!SanitizedContentKind} contentKind The desired content
 *     kind.
 * @param {!Object} constructor
 * @return {boolean} Whether the given value is of the given kind.
 */
function isContentKind(value, contentKind, constructor) {
  var ret = value != null && value.contentKind === contentKind;
  if (ret) {
    asserts.assert(value.constructor === constructor);
  }
  return ret;
}

/**
 * @param {?} value
 * @return {boolean}
 */
function isHtml(value) {
  return isContentKind(value, SanitizedContentKind.HTML, SanitizedHtml);
}

/**
 * @param {?} value
 * @return {boolean}
 */
function isCss(value) {
  return isContentKind(value, SanitizedContentKind.CSS, SanitizedCss);
}

/**
 * @param {?} value
 * @return {boolean}
 */
function isAttribute(value) {
  return isContentKind(
      value, SanitizedContentKind.ATTRIBUTES, SanitizedHtmlAttribute);
}

/**
 * @param {?} value
 * @return {boolean}
 */
function isJS(value) {
  return isContentKind(value, SanitizedContentKind.JS, SanitizedJs);
}

/**
 * @param {?} value
 * @return {boolean}
 */
function isTrustedResourceURI(value) {
  return isContentKind(
      value, SanitizedContentKind.TRUSTED_RESOURCE_URI,
      SanitizedTrustedResourceUri);
}

/**
 * @param {?} value
 * @return {boolean}
 */
function isURI(value) {
  return isContentKind(value, SanitizedContentKind.URI, SanitizedUri);
}

/**
 * @param {?} value
 * @return {boolean}
 */
function isText(value) {
  return isContentKind(value, SanitizedContentKind.TEXT, UnsanitizedText);
}

exports = {
  isHtml: isHtml,
  isCss: isCss,
  isAttribute: isAttribute,
  isJS: isJS,
  isTrustedResourceURI: isTrustedResourceURI,
  isURI: isURI,
  isText: isText,
};
