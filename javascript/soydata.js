/*
 * Copyright 2010 Google Inc.
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
 * Defines typed strings, e.g. an HTML string {@code "a<b>c"} is
 * semantically distinct from the plain text string {@code "a<b>c"} and smart
 * templates can take that distinction into account.
 *
 * @author Mike Samuel
 */


goog.provide('soydata');
goog.provide('soydata.SanitizedHtml');
goog.provide('soydata.SanitizedHtmlAttribute');
goog.provide('soydata.SanitizedJsStrChars');
goog.provide('soydata.SanitizedUri');


/**
 * A type of textual content.
 * @enum
 */
soydata.SanitizedContentKind = {

  /**
   * A snippet of HTML that does not start or end inside a tag, comment, entity,
   * or DOCTYPE; and that does not contain any executable code
   * (JS, {@code <object>}s, etc.) from a different trust domain.
   */
  HTML: 0,

  /**
   * A sequence of code units that can appear between quotes (either kind) in a
   * JS program without causing a parse error, and without causing any side
   * effects.
   * <p>
   * The content should not contain unescaped quotes, newlines, or anything else
   * that would cause parsing to fail or to cause a JS parser to finish the
   * string its parsing inside the content.
   * <p>
   * The content must also not end inside an escape sequence ; no partial octal
   * escape sequences or odd number of '{@code \}'s at the end.
   */
  JS_STR_CHARS: 1,

  /** A properly encoded portion of a URI. */
  URI: 2,

  /** An attribute name and value such as {@code dir="ltr"}. */
  HTML_ATTRIBUTE: 3
};


/**
 * A string-like object that carries a content-type.
 * @constructor
 * @private
 */
soydata.SanitizedContent = function() {};

/** The textual content.  @type {string} */
soydata.SanitizedContent.prototype.content;

/** @type {soydata.SanitizedContentKind} */
soydata.SanitizedContent.prototype.contentKind;

/**
 * @return {string}
 */
soydata.SanitizedContent.prototype.toString = function() {
  return this.content;
};


/**
 * Content of type {@link soydata.SanitizedContentKind.HTML}.
 * @constructor
 * @param {string!} content A string of HTML that can safely be embedded in
 *     a PCDATA context in your app.  If you would be surprised to find that an
 *     HTML sanitizer produced {@code s} (e.g. it runs code or fetches bad URLs)
 *     and you wouldn't write a template that produces {@code s} on security or
 *     privacy grounds, then don't pass {@code s} here.
 */
soydata.SanitizedHtml = function(content) {
  this.content = content;
};

/** @override */
soydata.SanitizedHtml.prototype = new soydata.SanitizedContent();

/** @override */
soydata.SanitizedHtml.prototype.contentKind = soydata.SanitizedContentKind.HTML;


/**
 * Content of type {@link soydata.SanitizedContentKind.JS_STR_CHARS}.
 * @constructor
 * @param {string!} content A string of JS that when evaled, produces a
 *     value that does not depend on any sensitive data and has no side effects
 *     <b>OR</b> a string of JS that does not reference any variables or have
 *     any side effects not known statically to the app authors.
 */
soydata.SanitizedJsStrChars = function(content) {
  this.content = content;
};

/** @override */
soydata.SanitizedJsStrChars.prototype = new soydata.SanitizedContent();

/** @override */
soydata.SanitizedJsStrChars.prototype.contentKind =
    soydata.SanitizedContentKind.JS_STR_CHARS;


/**
 * Content of type {@link soydata.SanitizedContentKind.URI}.
 * @constructor
 * @param {string!} content A chunk of URI that the caller knows is safe to
 *     emit in a template.
 */
soydata.SanitizedUri = function(content) {
  this.content = content;
};

/** @override */
soydata.SanitizedUri.prototype = new soydata.SanitizedContent();

/** @override */
soydata.SanitizedUri.prototype.contentKind = soydata.SanitizedContentKind.URI;


/**
 * Content of type {@link soydata.SanitizedContentKind.HTML_ATTRIBUTE}.
 * @constructor
 * @param {string!} content An attribute name and value, such as
 *     {@code dir="ltr"}..
 */
soydata.SanitizedHtmlAttribute = function(content) {
  this.content = content;
};

/** @override */
soydata.SanitizedHtmlAttribute.prototype = new soydata.SanitizedContent();

/** @override */
soydata.SanitizedHtmlAttribute.prototype.contentKind =
    soydata.SanitizedContentKind.HTML_ATTRIBUTE;
