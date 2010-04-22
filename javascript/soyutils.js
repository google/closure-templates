/*
 * Copyright 2008 Google Inc.
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

// Utility functions and classes for Soy.
//
// The top portion of this file contains utilities for Soy users:
//   + soy.StringBuilder: Compatible with the 'stringbuilder' code style.
//   + soy.renderElement: Render template and set as innerHTML of an element.
//   + soy.renderAsFragment: Render template and return as HTML fragment.
//
// The bottom portion of this file contains utilities that should only be called
// by Soy-generated JS code. Please do not use these functions directly from
// your hand-writen code. Their names all start with '$$'.

/**
 * Base name for the soy utilities, when used outside of Closure Library.
 * Check to see soy is already defined in the current scope before asigning to
 * prevent clobbering if soyutils.js is loaded more than once.
 * @type {Object}
 */
var soy = soy || {};


// Just enough browser detection for this file.
(function() {
  var ua = navigator.userAgent;
  var isOpera = ua.indexOf('Opera') == 0;
  /**
   * @type {boolean}
   * @private
   */
  soy.IS_OPERA_ = isOpera;
  /**
   * @type {boolean}
   * @private
   */
  soy.IS_IE_ = !isOpera && ua.indexOf('MSIE') != -1;
  /**
   * @type {boolean}
   * @private
   */
  soy.IS_WEBKIT_ = !isOpera && ua.indexOf('WebKit') != -1;
})();


// -----------------------------------------------------------------------------
// StringBuilder (compatible with the 'stringbuilder' code style).


/**
 * Utility class to facilitate much faster string concatenation in IE,
 * using Array.join() rather than the '+' operator.  For other browsers
 * we simply use the '+' operator.
 *
 * @param {Object|number|string|boolean=} opt_a1 Optional first initial item
 *     to append.
 * @param {Object|number|string|boolean} var_args Other initial items to
 *     append, e.g., new soy.StringBuilder('foo', 'bar').
 * @constructor
 */
soy.StringBuilder = function(opt_a1, var_args) {

  /**
   * Internal buffer for the string to be concatenated.
   * @type {string|Array}
   * @private
   */
  this.buffer_ = soy.IS_IE_ ? [] : '';

  if (opt_a1 != null) {
    this.append.apply(this, arguments);
  }
};


/**
 * Length of internal buffer (faster than calling buffer_.length).
 * Only used for IE.
 * @type {number}
 * @private
 */
soy.StringBuilder.prototype.bufferLength_ = 0;


/**
 * Appends one or more items to the string.
 *
 * Calling this with null, undefined, or empty arguments is an error.
 *
 * @param {Object|number|string|boolean} a1 Required first string.
 * @param {Object|number|string|boolean=} opt_a2 Optional second string.
 * @param {Object|number|string|boolean} var_args Other items to append,
 *     e.g., sb.append('foo', 'bar', 'baz').
 * @return {soy.StringBuilder} This same StringBuilder object.
 */
soy.StringBuilder.prototype.append = function(a1, opt_a2, var_args) {

  if (soy.IS_IE_) {
    if (opt_a2 == null) {  // no second argument (note: undefined == null)
      // Array assignment is 2x faster than Array push.  Also, use a1
      // directly to avoid arguments instantiation, another 2x improvement.
      this.buffer_[this.bufferLength_++] = a1;
    } else {
      this.buffer_.push.apply(this.buffer_, arguments);
      this.bufferLength_ = this.buffer_.length;
    }

  } else {

    // Use a1 directly to avoid arguments instantiation for single-arg case.
    this.buffer_ += a1;
    if (opt_a2 != null) {  // no second argument (note: undefined == null)
      for (var i = 1; i < arguments.length; i++) {
        this.buffer_ += arguments[i];
      }
    }
  }

  return this;
};


/**
 * Clears the string.
 */
soy.StringBuilder.prototype.clear = function() {

  if (soy.IS_IE_) {
     this.buffer_.length = 0;  // reuse array to avoid creating new object
     this.bufferLength_ = 0;

   } else {
     this.buffer_ = '';
   }
};


/**
 * Returns the concatenated string.
 *
 * @return {string} The concatenated string.
 */
soy.StringBuilder.prototype.toString = function() {

  if (soy.IS_IE_) {
    var str = this.buffer_.join('');
    // Given a string with the entire contents, simplify the StringBuilder by
    // setting its contents to only be this string, rather than many fragments.
    this.clear();
    if (str) {
      this.append(str);
    }
    return str;

  } else {
    return /** @type {string} */ (this.buffer_);
  }
};


// -----------------------------------------------------------------------------
// Public utilities.


/**
 * Helper function to render a Soy template and then set the output string as
 * the innerHTML of an element. It is recommended to use this helper function
 * instead of directly setting innerHTML in your hand-written code, so that it
 * will be easier to audit the code for cross-site scripting vulnerabilities.
 *
 * @param {Element} element The element whose content we are rendering.
 * @param {Function} template The Soy template defining the element's content.
 * @param {Object=} opt_templateData The data for the template.
 */
soy.renderElement = function(element, template, opt_templateData) {
  element.innerHTML = template(opt_templateData);
};


/**
 * Helper function to render a Soy template into a single node or a document
 * fragment. If the rendered HTML string represents a single node, then that
 * node is returned. Otherwise a document fragment is returned containing the
 * rendered nodes.
 *
 * @param {Function} template The Soy template defining the element's content.
 * @param {Object=} opt_templateData The data for the template.
 * @return {Node} The resulting node or document fragment.
 */
soy.renderAsFragment = function(template, opt_templateData) {

  var tempDiv = document.createElement('div');
  tempDiv.innerHTML = template(opt_templateData);
  if (tempDiv.childNodes.length == 1) {
    return tempDiv.firstChild;
  } else {
    var fragment = document.createDocumentFragment();
    while (tempDiv.firstChild) {
      fragment.appendChild(tempDiv.firstChild);
    }
    return fragment;
  }
};


// -----------------------------------------------------------------------------
// Below are private utilities to be used by Soy-generated code only.


/**
 * Builds an augmented data object to be passed when a template calls another,
 * and needs to pass both original data and additional params. The returned
 * object will contain both the original data and the additional params. If the
 * same key appears in both, then the value from the additional params will be
 * visible, while the value from the original data will be hidden. The original
 * data object will be used, but not modified.
 *
 * @param {!Object} origData The original data to pass.
 * @param {Object} additionalParams The additional params to pass.
 * @return {Object} An augmented data object containing both the original data
 *     and the additional params.
 */
soy.$$augmentData = function(origData, additionalParams) {

  // Create a new object whose '__proto__' field is set to origData.
  /** @constructor */
  function tempCtor() {};
  tempCtor.prototype = origData;
  var newData = new tempCtor();

  // Add the additional params to the new object.
  for (var key in additionalParams) {
    newData[key] = additionalParams[key];
  }

  return newData;
};


/**
 * Escapes HTML special characters in a string. Escapes double quote '"' in
 * addition to '&', '<', and '>' so that a string can be included in an HTML
 * tag attribute value within double quotes.
 *
 * @param {*} str The string to be escaped. Can be other types, but the value
 *     will be coerced to a string.
 * @return {string} An escaped copy of the string.
*/
soy.$$escapeHtml = function(str) {

  str = String(str);

  // This quick test helps in the case when there are no chars to replace, in
  // the worst case this makes barely a difference to the time taken.
  if (!soy.$$EscapeHtmlRe_.ALL_SPECIAL_CHARS.test(str)) {
    return str;
  }

  // Since we're only checking one char at a time, we use String.indexOf(),
  // which is faster than RegExp.test(). Important: Must replace '&' first!
  if (str.indexOf('&') != -1) {
    str = str.replace(soy.$$EscapeHtmlRe_.AMP, '&amp;');
  }
  if (str.indexOf('<') != -1) {
    str = str.replace(soy.$$EscapeHtmlRe_.LT, '&lt;');
  }
  if (str.indexOf('>') != -1) {
    str = str.replace(soy.$$EscapeHtmlRe_.GT, '&gt;');
  }
  if (str.indexOf('"') != -1) {
    str = str.replace(soy.$$EscapeHtmlRe_.QUOT, '&quot;');
  }
  return str;
};

/**
 * Regular expressions used within escapeHtml().
 * @enum {RegExp}
 * @private
 */
soy.$$EscapeHtmlRe_ = {
  ALL_SPECIAL_CHARS: /[&<>\"]/,
  AMP: /&/g,
  LT: /</g,
  GT: />/g,
  QUOT: /\"/g
};


/**
 * Escapes characters in the string to make it a valid content for a JS string literal.
 *
 * @param {*} s The string to be escaped. Can be other types, but the value
 *     will be coerced to a string.
 * @return {string} An escaped copy of the string.
*/
soy.$$escapeJs = function(s) {
  s = String(s);
  var sb = [];
  for (var i = 0; i < s.length; i++) {
    sb[i] = soy.$$escapeChar(s.charAt(i));
  }
  return sb.join('');
};


/**
 * Takes a character and returns the escaped string for that character. For
 * example escapeChar(String.fromCharCode(15)) -> "\\x0E".
 * @param {string} c The character to escape.
 * @return {string} An escaped string representing {@code c}.
 */
soy.$$escapeChar = function(c) {
  if (c in soy.$$escapeCharJs_) {
    return soy.$$escapeCharJs_[c];
  }
  var rv = c;
  var cc = c.charCodeAt(0);
  if (cc > 31 && cc < 127) {
    rv = c;
  } else {
    // tab is 9 but handled above
    if (cc < 256) {
      rv = '\\x';
      if (cc < 16 || cc > 256) {
        rv += '0';
      }
    } else {
      rv = '\\u';
      if (cc < 4096) { // \u1000
        rv += '0';
      }
    }
    rv += cc.toString(16).toUpperCase();
  }

  return soy.$$escapeCharJs_[c] = rv;
};

/**
 * Character mappings used internally for soy.$$escapeJs
 * @private
 * @type {Object}
 */
soy.$$escapeCharJs_ = {
  '\b': '\\b',
  '\f': '\\f',
  '\n': '\\n',
  '\r': '\\r',
  '\t': '\\t',
  '\x0B': '\\x0B', // '\v' is not supported in JScript
  '"': '\\"',
  '\'': '\\\'',
  '\\': '\\\\'
};


/**
 * Escapes a string so that it can be safely included in a URI.
 *
 * @param {*} str The string to be escaped. Can be other types, but the value
 *     will be coerced to a string.
 * @return {string} An escaped copy of the string.
*/
soy.$$escapeUri = function(str) {

  str = String(str);

  // Checking if the search matches before calling encodeURIComponent avoids an
  // extra allocation in IE6. This adds about 10us time in FF and a similiar
  // over head in IE6 for lower working set apps, but for large working set
  // apps, it saves about 70us per call.
  if (!soy.$$ENCODE_URI_REGEXP_.test(str)) {
    return encodeURIComponent(str);
  } else {
    return str;
  }
};

/**
 * Regular expression used for determining if a string needs to be encoded.
 * @type {RegExp}
 * @private
 */
soy.$$ENCODE_URI_REGEXP_ = /^[a-zA-Z0-9\-_.!~*'()]*$/;


/**
 * Inserts word breaks ('wbr' tags) into a HTML string at a given interval. The
 * counter is reset if a space is encountered. Word breaks aren't inserted into
 * HTML tags or entities. Entites count towards the character count; HTML tags
 * do not.
 *
 * @param {*} str The HTML string to insert word breaks into. Can be other
 *     types, but the value will be coerced to a string.
 * @param {number} maxCharsBetweenWordBreaks Maximum number of non-space
 *     characters to allow before adding a word break.
 * @return {string} The string including word breaks.
 */
soy.$$insertWordBreaks = function(str, maxCharsBetweenWordBreaks) {

  str = String(str);

  var resultArr = [];
  var resultArrLen = 0;

  // These variables keep track of important state while looping through str.
  var isInTag = false;  // whether we're inside an HTML tag
  var isMaybeInEntity = false;  // whether we might be inside an HTML entity
  var numCharsWithoutBreak = 0;  // number of characters since last word break
  var flushIndex = 0;  // index of first char not yet flushed to resultArr

  for (var i = 0, n = str.length; i < n; ++i) {
    var charCode = str.charCodeAt(i);

    // If hit maxCharsBetweenWordBreaks, and not space next, then add <wbr>.
    if (numCharsWithoutBreak >= maxCharsBetweenWordBreaks &&
        charCode != soy.$$CharCode_.SPACE) {
      resultArr[resultArrLen++] = str.substring(flushIndex, i);
      flushIndex = i;
      resultArr[resultArrLen++] = soy.WORD_BREAK_;
      numCharsWithoutBreak = 0;
    }

    if (isInTag) {
      // If inside an HTML tag and we see '>', it's the end of the tag.
      if (charCode == soy.$$CharCode_.GREATER_THAN) {
        isInTag = false;
      }

    } else if (isMaybeInEntity) {
      switch (charCode) {
        // If maybe inside an entity and we see ';', it's the end of the entity.
        // The entity that just ended counts as one char, so increment
        // numCharsWithoutBreak.
        case soy.$$CharCode_.SEMI_COLON:
          isMaybeInEntity = false;
          ++numCharsWithoutBreak;
          break;
        // If maybe inside an entity and we see '<', we weren't actually in an
        // entity. But now we're inside and HTML tag.
        case soy.$$CharCode_.LESS_THAN:
          isMaybeInEntity = false;
          isInTag = true;
          break;
        // If maybe inside an entity and we see ' ', we weren't actually in an
        // entity. Just correct the state and reset the numCharsWithoutBreak
        // since we just saw a space.
        case soy.$$CharCode_.SPACE:
          isMaybeInEntity = false;
          numCharsWithoutBreak = 0;
          break;
      }

    } else {  // !isInTag && !isInEntity
      switch (charCode) {
        // When not within a tag or an entity and we see '<', we're now inside
        // an HTML tag.
        case soy.$$CharCode_.LESS_THAN:
          isInTag = true;
          break;
        // When not within a tag or an entity and we see '&', we might be inside
        // an entity.
        case soy.$$CharCode_.AMPERSAND:
          isMaybeInEntity = true;
          break;
        // When we see a space, reset the numCharsWithoutBreak count.
        case soy.$$CharCode_.SPACE:
          numCharsWithoutBreak = 0;
          break;
        // When we see a non-space, increment the numCharsWithoutBreak.
        default:
          ++numCharsWithoutBreak;
          break;
      }
    }
  }

  // Flush the remaining chars at the end of the string.
  resultArr[resultArrLen++] = str.substring(flushIndex);

  return resultArr.join('');
};

/**
 * Special characters used within insertWordBreaks().
 * @enum {number}
 * @private
 */
soy.$$CharCode_ = {
  SPACE: 32,  // ' '.charCodeAt(0)
  AMPERSAND: 38,  // '&'.charCodeAt(0)
  SEMI_COLON: 59,  // ';'.charCodeAt(0)
  LESS_THAN: 60,  // '<'.charCodeAt(0)
  GREATER_THAN: 62  // '>'.charCodeAt(0)
};

/**
 * String inserted as a word break by insertWordBreaks(). Safari requires
 * <wbr></wbr>, Opera needs the 'shy' entity, though this will give a visible
 * hyphen at breaks. Other browsers just use <wbr>.
 * @type {string}
 * @private
 */
soy.WORD_BREAK_ =
    soy.IS_WEBKIT_ ? '<wbr></wbr>' : soy.IS_OPERA_ ? '&shy;' : '<wbr>';


/**
 * Converts \r\n, \r, and \n to <br>s
 * @param {*} str The string in which to convert newlines.
 * @return {string} A copy of {@code str} with converted newlines.
 */
soy.$$changeNewlineToBr = function(str) {

  str = String(str);

  // This quick test helps in the case when there are no chars to replace, in
  // the worst case this makes barely a difference to the time taken.
  if (!soy.$$CHANGE_NEWLINE_TO_BR_RE_.test(str)) {
    return str;
  }

  return str.replace(/(\r\n|\r|\n)/g, '<br>');
};

/**
 * Regular expression used within $$changeNewlineToBr().
 * @type {RegExp}
 * @private
 */
soy.$$CHANGE_NEWLINE_TO_BR_RE_ = /[\r\n]/;


/**
 * Estimate the overall directionality of text. If opt_isHtml, makes sure to
 * ignore the LTR nature of the mark-up and escapes in text, making the logic
 * suitable for HTML and HTML-escaped text.
 * @param {string} text The text whose directionality is to be estimated.
 * @param {boolean=} opt_isHtml Whether text is HTML/HTML-escaped.
 *     Default: false.
 * @return {number} 1 if text is LTR, -1 if it is RTL, and 0 if it is neutral.
 */
soy.$$bidiTextDir = function(text, opt_isHtml) {
  text = soy.$$bidiStripHtmlIfNecessary_(text, opt_isHtml);
  if (!text) {
    return 0;
  }
  return soy.$$bidiDetectRtlDirectionality_(text) ? -1 : 1;
};


/**
 * Returns "dir=ltr" or "dir=rtl", depending on text's estimated
 * directionality, if it is not the same as bidiGlobalDir.
 * Otherwise, returns the empty string.
 * If opt_isHtml, makes sure to ignore the LTR nature of the mark-up and escapes
 * in text, making the logic suitable for HTML and HTML-escaped text.
 * @param {number} bidiGlobalDir The global directionality context: 1 if ltr, -1
 *     if rtl, 0 if unknown.
 * @param {string} text The text whose directionality is to be estimated.
 * @param {boolean=} opt_isHtml Whether text is HTML/HTML-escaped.
 *     Default: false.
 * @return {string} "dir=rtl" for RTL text in non-RTL context; "dir=ltr" for LTR
 *     text in non-LTR context; else, the empty string.
 */
soy.$$bidiDirAttr = function(bidiGlobalDir, text, opt_isHtml) {
  var dir = soy.$$bidiTextDir(text, opt_isHtml);
  if (dir != bidiGlobalDir) {
    return dir < 0 ? 'dir=rtl' : dir > 0 ? 'dir=ltr' : '';
  }
  return '';
};


/**
 * Returns a Unicode BiDi mark matching bidiGlobalDir (LRM or RLM) if the
 * directionality or the exit directionality of text are opposite to
 * bidiGlobalDir. Otherwise returns the empty string.
 * If opt_isHtml, makes sure to ignore the LTR nature of the mark-up and escapes
 * in text, making the logic suitable for HTML and HTML-escaped text.
 * @param {number} bidiGlobalDir The global directionality context: 1 if ltr, -1
 *     if rtl, 0 if unknown.
 * @param {string} text The text whose directionality is to be estimated.
 * @param {boolean=} opt_isHtml Whether text is HTML/HTML-escaped.
 *     Default: false.
 * @return {string} A Unicode bidi mark matching bidiGlobalDir, or
 *     the empty string when text's overall and exit directionalities both match
 *     bidiGlobalDir.
 */
soy.$$bidiMarkAfter = function(bidiGlobalDir, text, opt_isHtml) {
  var dir = soy.$$bidiTextDir(text, opt_isHtml);
  return soy.$$bidiMarkAfterKnownDir(bidiGlobalDir, dir, text, opt_isHtml);
};


/**
 * Returns a Unicode BiDi mark matching bidiGlobalDir (LRM or RLM) if the
 * directionality or the exit directionality of text are opposite to
 * bidiGlobalDir. Otherwise returns the empty string.
 * If opt_isHtml, makes sure to ignore the LTR nature of the mark-up and escapes
 * in text, making the logic suitable for HTML and HTML-escaped text.
 * @param {number} bidiGlobalDir The global directionality context: 1 if ltr, -1
 *     if rtl, 0 if unknown.
 * @param {number} dir text's directionality: 1 if ltr, -1 if rtl, 0 if unknown.
 * @param {string} text The text whose directionality is to be estimated.
 * @param {boolean=} opt_isHtml Whether text is HTML/HTML-escaped.
 *     Default: false.
 * @return {string} A Unicode bidi mark matching bidiGlobalDir, or
 *     the empty string when text's overall and exit directionalities both match
 *     bidiGlobalDir.
 */
soy.$$bidiMarkAfterKnownDir = function(bidiGlobalDir, dir, text, opt_isHtml) {
  return (
      bidiGlobalDir > 0 && (dir < 0 ||
          soy.$$bidiIsRtlExitText_(text, opt_isHtml)) ? '\u200E' : // LRM
      bidiGlobalDir < 0 && (dir > 0 ||
          soy.$$bidiIsLtrExitText_(text, opt_isHtml)) ? '\u200F' : // RLM
      '');
};


/**
 * Strips str of any HTML mark-up and escapes. Imprecise in several ways, but
 * precision is not very important, since the result is only meant to be used
 * for directionality detection.
 * @param {string} str The string to be stripped.
 * @param {boolean=} opt_isHtml Whether str is HTML / HTML-escaped.
 *     Default: false.
 * @return {string} The stripped string.
 * @private
 */
soy.$$bidiStripHtmlIfNecessary_ = function(str, opt_isHtml) {
  return opt_isHtml ? str.replace(soy.$$BIDI_HTML_SKIP_RE_, ' ') : str;
};


/**
 * Simplified regular expression for am HTML tag (opening or closing) or an HTML
 * escape - the things we want to skip over in order to ignore their ltr
 * characters.
 * @type {RegExp}
 * @private
 */
soy.$$BIDI_HTML_SKIP_RE_ = /<[^>]*>|&[^;]+;/g;


/**
 * Returns str wrapped in a <span dir=ltr|rtl> according to its directionality -
 * but only if that is neither neutral nor the same as the global context.
 * Otherwise, returns str unchanged.
 * Always treats str as HTML/HTML-escaped, i.e. ignores mark-up and escapes when
 * estimating str's directionality.
 * @param {number} bidiGlobalDir The global directionality context: 1 if ltr, -1
 *     if rtl, 0 if unknown.
 * @param {*} str The string to be wrapped. Can be other types, but the value
 *     will be coerced to a string.
 * @return {string} The wrapped string.
 */
soy.$$bidiSpanWrap = function(bidiGlobalDir, str) {
  str = String(str);
  var textDir = soy.$$bidiTextDir(str, true);
  var reset = soy.$$bidiMarkAfterKnownDir(bidiGlobalDir, textDir, str, true);
  if (textDir > 0 && bidiGlobalDir <= 0) {
    str = '<span dir=ltr>' + str + '</span>';
  } else if (textDir < 0 && bidiGlobalDir >= 0) {
    str = '<span dir=rtl>' + str + '</span>';
  }
  return str + reset;
};


/**
 * Returns str wrapped in Unicode BiDi formatting characters according to its
 * directionality, i.e. either LRE or RLE at the beginning and PDF at the end -
 * but only if str's directionality is neither neutral nor the same as the
 * global context. Otherwise, returns str unchanged.
 * Always treats str as HTML/HTML-escaped, i.e. ignores mark-up and escapes when
 * estimating str's directionality.
 * @param {number} bidiGlobalDir The global directionality context: 1 if ltr, -1
 *     if rtl, 0 if unknown.
 * @param {*} str The string to be wrapped. Can be other types, but the value
 *     will be coerced to a string.
 * @return {string} The wrapped string.
 */
soy.$$bidiUnicodeWrap = function(bidiGlobalDir, str) {
  str = String(str);
  var textDir = soy.$$bidiTextDir(str, true);
  var reset = soy.$$bidiMarkAfterKnownDir(bidiGlobalDir, textDir, str, true);
  if (textDir > 0 && bidiGlobalDir <= 0) {
    str = '\u202A' + str + '\u202C';
  } else if (textDir < 0 && bidiGlobalDir >= 0) {
    str = '\u202B' + str + '\u202C';
  }
  return str + reset;
};


/**
 * A practical pattern to identify strong LTR character. This pattern is not
 * theoretically correct according to unicode standard. It is simplified for
 * performance and small code size.
 * @type {string}
 * @private
 */
soy.$$bidiLtrChars_ =
    'A-Za-z\u00C0-\u00D6\u00D8-\u00F6\u00F8-\u02B8\u0300-\u0590\u0800-\u1FFF' +
    '\u2C00-\uFB1C\uFDFE-\uFE6F\uFEFD-\uFFFF';


/**
 * A practical pattern to identify strong neutral and weak character. This
 * pattern is not theoretically correct according to unicode standard. It is
 * simplified for performance and small code size.
 * @type {string}
 * @private
 */
soy.$$bidiNeutralChars_ =
    '\u0000-\u0020!-@[-`{-\u00BF\u00D7\u00F7\u02B9-\u02FF\u2000-\u2BFF';


/**
 * A practical pattern to identify strong RTL character. This pattern is not
 * theoretically correct according to unicode standard. It is simplified for
 * performance and small code size.
 * @type {string}
 * @private
 */
soy.$$bidiRtlChars_ = '\u0591-\u07FF\uFB1D-\uFDFD\uFE70-\uFEFC';


/**
 * Regular expressions to check if a piece of text is of RTL directionality
 * on first character with strong directionality.
 * @type {RegExp}
 * @private
 */
soy.$$bidiRtlDirCheckRe_ = new RegExp(
    '^[^' + soy.$$bidiLtrChars_ + ']*[' + soy.$$bidiRtlChars_ + ']');


/**
 * Regular expressions to check if a piece of text is of neutral directionality.
 * Url are considered as neutral.
 * @type {RegExp}
 * @private
 */
soy.$$bidiNeutralDirCheckRe_ = new RegExp(
    '^[' + soy.$$bidiNeutralChars_ + ']*$|^http://');


/**
 * Check the directionality of the a piece of text based on the first character
 * with strong directionality.
 * @param {string} str string being checked.
 * @return {boolean} return true if rtl directionality is being detected.
 * @private
 */
soy.$$bidiIsRtlText_ = function(str) {
  return soy.$$bidiRtlDirCheckRe_.test(str);
};


/**
 * Check the directionality of the a piece of text based on the first character
 * with strong directionality.
 * @param {string} str string being checked.
 * @return {boolean} true if all characters have neutral directionality.
 * @private
 */
soy.$$bidiIsNeutralText_ = function(str) {
  return soy.$$bidiNeutralDirCheckRe_.test(str);
};


/**
 * This constant controls threshold of rtl directionality.
 * @type {number}
 * @private
 */
soy.$$bidiRtlDetectionThreshold_ = 0.40;


/**
 * Returns the RTL ratio based on word count.
 * @param {string} str the string that need to be checked.
 * @return {number} the ratio of RTL words among all words with directionality.
 * @private
 */
soy.$$bidiRtlWordRatio_ = function(str) {
  var rtlCount = 0;
  var totalCount = 0;
  var tokens = str.split(' ');
  for (var i = 0; i < tokens.length; i++) {
    if (soy.$$bidiIsRtlText_(tokens[i])) {
      rtlCount++;
      totalCount++;
    } else if (!soy.$$bidiIsNeutralText_(tokens[i])) {
      totalCount++;
    }
  }

  return totalCount == 0 ? 0 : rtlCount / totalCount;
};


/**
 * Check the directionality of a piece of text, return true if the piece of
 * text should be laid out in RTL direction.
 * @param {string} str The piece of text that need to be detected.
 * @return {boolean} true if this piece of text should be laid out in RTL.
 * @private
 */
soy.$$bidiDetectRtlDirectionality_ = function(str) {
  return soy.$$bidiRtlWordRatio_(str) >
    soy.$$bidiRtlDetectionThreshold_;
};


/**
 * Regular expressions to check if the last strongly-directional character in a
 * piece of text is LTR.
 * @type {RegExp}
 * @private
 */
soy.$$bidiLtrExitDirCheckRe_ = new RegExp(
    '[' + soy.$$bidiLtrChars_ + '][^' + soy.$$bidiRtlChars_ + ']*$');


/**
 * Regular expressions to check if the last strongly-directional character in a
 * piece of text is RTL.
 * @type {RegExp}
 * @private
 */
soy.$$bidiRtlExitDirCheckRe_ = new RegExp(
    '[' + soy.$$bidiRtlChars_ + '][^' + soy.$$bidiLtrChars_ + ']*$');


/**
 * Check if the exit directionality a piece of text is LTR, i.e. if the last
 * strongly-directional character in the string is LTR.
 * @param {string} str string being checked.
 * @param {boolean=} opt_isHtml Whether str is HTML / HTML-escaped.
 *     Default: false.
 * @return {boolean} Whether LTR exit directionality was detected.
 * @private
 */
soy.$$bidiIsLtrExitText_ = function(str, opt_isHtml) {
  str = soy.$$bidiStripHtmlIfNecessary_(str, opt_isHtml);
  return soy.$$bidiLtrExitDirCheckRe_.test(str);
};


/**
 * Check if the exit directionality a piece of text is RTL, i.e. if the last
 * strongly-directional character in the string is RTL.
 * @param {string} str string being checked.
 * @param {boolean=} opt_isHtml Whether str is HTML / HTML-escaped.
 *     Default: false.
 * @return {boolean} Whether RTL exit directionality was detected.
 * @private
 */
soy.$$bidiIsRtlExitText_ = function(str, opt_isHtml) {
  str = soy.$$bidiStripHtmlIfNecessary_(str, opt_isHtml);
  return soy.$$bidiRtlExitDirCheckRe_.test(str);
};
