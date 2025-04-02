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

/**
 * @fileoverview
 * Utility functions and classes for Soy gencode
 *
 * <p>
 * This file contains utilities that should only be called by Soy-generated
 * JS code. Please do not use these functions directly from
 * your hand-written code. Their names all start with '$$'
 *
 */
goog.module('soy');
goog.module.declareLegacyNamespace();

const BidiFormatter = goog.require('goog.i18n.BidiFormatter');
const asserts = goog.require('goog.asserts');
const bidi = goog.require('goog.i18n.bidi');
const googArray = goog.require('goog.array');
const googDebug = goog.require('goog.debug');
const googFormat = goog.require('goog.format');
const googSoy = goog.requireType('goog.soy');
const googString = goog.require('goog.string');
const {Message} = goog.requireType('jspb');
const {SafeHtml, SafeScript, SafeStyleSheet, TrustedResourceUrl, isUrl, unwrapHtml, unwrapResourceUrl, unwrapScript, unwrapStyleSheet, unwrapUrl} = goog.require('safevalues');
const {SanitizedContent, SanitizedContentKind, SanitizedCss, SanitizedHtml, SanitizedHtmlAttribute, SanitizedJs, SanitizedTrustedResourceUri, SanitizedUri} = goog.require('goog.soy.data');
const {compareBigInt} = goog.require('google3.javascript.common.bigint.index');
const {defaultImmutableInstance} = goog.require('jspb.immutable_message');
const {htmlSafeByReview} = goog.require('safevalues.restricted.reviewed');
const {isReadonly} = goog.require('google3.javascript.apps.jspb.types.is_readonly');

// -----------------------------------------------------------------------------
// soydata: Defines typed strings, e.g. an HTML string `"a<b>c"` is
// semantically distinct from the plain text string `"a<b>c"` and smart
// templates can take that distinction into account.


/** @typedef {!SanitizedContent|{isInvokableFn: boolean}} */
let IdomFunction;

/**
 * Returns a given value's contentDir property, constrained to a
 * bidi.Dir value or null. Returns null if the value is null,
 * undefined, a primitive or does not have a contentDir property, or the
 * property's value is not 1 (for LTR), -1 (for RTL), or 0 (for neutral).
 *
 * @param {?} value The value whose contentDir property, if any, is to
 *     be returned.
 * @return {?bidi.Dir} The contentDir property.
 */
const getContentDir = function(value) {
  if (value != null) {
    switch (value.contentDir) {
      case bidi.Dir.LTR:
        return bidi.Dir.LTR;
      case bidi.Dir.RTL:
        return bidi.Dir.RTL;
      case bidi.Dir.NEUTRAL:
        return bidi.Dir.NEUTRAL;
    }
  }
  return null;
};

/**
 * Sets the value's contentDir property if it exists to a bidi.Dir dir value.
 *
 * @param {?} value The value whose contentDir property, if any, is to be set.
 * @param {?bidi.Dir} dir The dir value to set.
 * @package
 */
const cacheContentDir_ = function(value, dir) {
  if (value != null && value.contentDir !== undefined) {
    value.contentDir = dir;
  }
};

/**
 * Returns a SanitizedHtml object for a particular value. The content direction
 * is preserved.
 *
 * This HTML-escapes the value unless it is already SanitizedHtml or SafeHtml.
 *
 * @param {?} value The value to convert. If it is already a SanitizedHtml
 *     object, it is left alone.
 * @return {!SanitizedHtml} A SanitizedHtml object derived from
 *     the stringified value. It is escaped unless the input is SanitizedHtml or
 *     SafeHtml.
 */
const createSanitizedHtml = function(value) {
  if ($$isHtml(value)) {
    return /** @type {!SanitizedHtml} */ (value);
  }
  if (value instanceof SafeHtml) {
    return VERY_UNSAFE.ordainSanitizedHtml(unwrapHtml(value).toString());
  }
  return VERY_UNSAFE.ordainSanitizedHtml(
      $$escapeHtmlHelper(String(value)), getContentDir(value));
};


/**
 * Empty string, used as a type in Soy templates.
 * @enum {string}
 */
const $$EMPTY_STRING_ = {
  VALUE: '',
};


/**
 * Creates a factory for SanitizedContent types.
 *
 * This is a hack so that the VERY_UNSAFE.ordainSanitized* can
 * instantiate Sanitized* classes, without making the Sanitized* constructors
 * publicly usable. Requiring all construction to use the VERY_UNSAFE names
 * helps callers and their reviewers easily tell that creating SanitizedContent
 * is not always safe and calls for careful review.
 *
 * @param {function(new: T)} ctor A constructor.
 * @return {function(*, ?bidi.Dir=): T} A factory that takes
 *     content and an optional content direction and returns a new instance. If
 *     the content direction is undefined, ctor.prototype.contentDir is used.
 * @template T
 * @private
 */
const $$makeSanitizedContentFactory_ = function(ctor) {
  /**
   * @param {string} content
   * @constructor
   * @extends {SanitizedContent}
   */
  function InstantiableCtor(content) {
    /** @override */
    this.content = content;
  }
  InstantiableCtor.prototype = ctor.prototype;
  /**
   * Creates a ctor-type SanitizedContent instance.
   *
   * @param {?} content The content to put in the instance.
   * @param {?bidi.Dir=} contentDir The content direction. If
   *     undefined, ctor.prototype.contentDir is used.
   * @return {!SanitizedContent} The new instance. It is actually
   *     of type T above (ctor's type, a descendant of SanitizedContent), but
   *     there is no way to express that here.
   */
  function sanitizedContentFactory(content, contentDir) {
    const result = new InstantiableCtor(String(content));
    if (contentDir !== undefined) {
      result.contentDir = contentDir;
    }
    return result;
  }
  return sanitizedContentFactory;
};


/**
 * Creates a factory for SanitizedContent types that should always have their
 * default directionality.
 *
 * This is a hack so that the VERY_UNSAFE.ordainSanitized* can
 * instantiate Sanitized* classes, without making the Sanitized* constructors
 * publicly usable. Requiring all construction to use the VERY_UNSAFE names
 * helps callers and their reviewers easily tell that creating SanitizedContent
 * is not always safe and calls for careful review.
 *
 * @param {function(new: T, string)} ctor A constructor.
 * @return {function(*): T} A factory that takes content and returns a new
 *     instance (with default directionality, i.e. ctor.prototype.contentDir).
 * @template T
 */
const $$makeSanitizedContentFactoryWithDefaultDirOnly_ = function(ctor) {
  /**
   * @param {string} content
   * @constructor
   * @extends {SanitizedContent}
   */
  function InstantiableCtor(content) {
    /** @override */
    this.content = content;
  }
  InstantiableCtor.prototype = ctor.prototype;
  /**
   * Creates a ctor-type SanitizedContent instance.
   *
   * @param {?} content The content to put in the instance.
   * @return {!SanitizedContent} The new instance. It is actually
   *     of type T above (ctor's type, a descendant of SanitizedContent), but
   *     there is no way to express that here.
   */
  function sanitizedContentFactory(content) {
    const result = new InstantiableCtor(String(content));
    return result;
  }
  return sanitizedContentFactory;
};


// -----------------------------------------------------------------------------
// Sanitized content ordainers. Please use these with extreme caution. A good
// recommendation is to limit usage of these to just a handful of files in your
// source tree where usages can be carefully audited.

/** @struct */
const VERY_UNSAFE = {};

/**
 * Takes a leap of faith that the provided content is "safe" HTML.
 *
 * @param {?} content A string of HTML that can safely be embedded in
 *     a PCDATA context in your app. If you would be surprised to find that an
 *     HTML sanitizer produced `s` (e.g. it runs code or fetches bad URLs)
 *     and you wouldn't write a template that produces `s` on security or
 *     privacy grounds, then don't pass `s` here.
 * @param {?bidi.Dir=} contentDir The content direction; null if
 *     unknown and thus to be estimated when necessary. Default: null.
 * @return {!SanitizedHtml} Sanitized content wrapper that
 *     indicates to Soy not to escape when printed as HTML.
 */
VERY_UNSAFE.ordainSanitizedHtml = $$makeSanitizedContentFactory_(SanitizedHtml);


/**
 * Takes a leap of faith that the provided content is "safe" (non-attacker-
 * controlled, XSS-free) Javascript.
 *
 * @param {?} content Javascript source that when evaluated does not
 *     execute any attacker-controlled scripts.
 * @return {!SanitizedJs} Sanitized content wrapper that indicates
 *     to Soy not to escape when printed as Javascript source.
 */
VERY_UNSAFE.ordainSanitizedJs =
    $$makeSanitizedContentFactoryWithDefaultDirOnly_(SanitizedJs);


/**
 * Takes a leap of faith that the provided content is "safe" to use as a URI
 * in a Soy template.
 *
 * This creates a Soy SanitizedContent object which indicates to Soy there is
 * no need to escape it when printed as a URI (e.g. in an href or src
 * attribute), such as if it's already been encoded or  if it's a Javascript:
 * URI.
 *
 * @param {?} content A chunk of URI that the caller knows is safe to
 *     emit in a template.
 * @return {!SanitizedUri} Sanitized content wrapper that
 *     indicates to Soy not to escape or filter when printed in URI context.
 */
VERY_UNSAFE.ordainSanitizedUri =
    $$makeSanitizedContentFactoryWithDefaultDirOnly_(SanitizedUri);


/**
 * Takes a leap of faith that the provided content is "safe" to use as a
 * TrustedResourceUri in a Soy template.
 *
 * This creates a Soy SanitizedContent object which indicates to Soy there is
 * no need to filter it when printed as a TrustedResourceUri.
 *
 * @param {?} content A chunk of TrustedResourceUri such as that the caller
 *     knows is safe to emit in a template.
 * @return {!SanitizedTrustedResourceUri} Sanitized content
 *     wrapper that indicates to Soy not to escape or filter when printed in
 *     TrustedResourceUri context.
 */
VERY_UNSAFE.ordainSanitizedTrustedResourceUri =
    $$makeSanitizedContentFactoryWithDefaultDirOnly_(
        SanitizedTrustedResourceUri);


/**
 * Takes a leap of faith that the provided content is "safe" to use as an
 * HTML attribute.
 *
 * @param {?} content An attribute name and value, such as
 *     `dir="ltr"`.
 * @return {!SanitizedHtmlAttribute} Sanitized content wrapper
 *     that indicates to Soy not to escape when printed as an HTML attribute.
 */
VERY_UNSAFE.ordainSanitizedHtmlAttribute =
    $$makeSanitizedContentFactoryWithDefaultDirOnly_(SanitizedHtmlAttribute);


/**
 * Takes a leap of faith that the provided content is "safe" to use as CSS
 * in a style block.
 *
 * @param {?} content CSS, such as `color:#c3d9ff`.
 * @return {!SanitizedCss} Sanitized CSS wrapper that indicates to
 *     Soy there is no need to escape or filter when printed in CSS context.
 */
VERY_UNSAFE.ordainSanitizedCss =
    $$makeSanitizedContentFactoryWithDefaultDirOnly_(SanitizedCss);

// Utilities related to defining and stubbing soy templates


/**
 * A map that allows us to dynamically replace templates.
 *
 * The key is the fully qualified template name and the value is a replacement
 * to call instead.
 *
 * @type {?Object<string, !Function>}
 * @const
 */
const $$stubsMap = goog.DEBUG ? {} : null;


// -----------------------------------------------------------------------------
// Soy-generated utilities in the soy namespace.  Contains implementations for
// common soyfunctions (e.g. keys()) and escaping/print directives.


/**
 * Provides a compact serialization format for the key structure.
 * @param {?} item
 * @return {string}
 */
const $$serializeKey = function(item) {
  let stringified = String(item);
  let delimiter;
  if (item == null) {
    delimiter = '_';
    stringified = 'null';  // handle null and undefined equivalently
  } else if (typeof item === 'number') {
    delimiter = '#';
  } else {
    delimiter = ':';
  }
  return `${stringified.length}${delimiter}${stringified}`;
};



/**
 * Whether the locale is right-to-left.
 *
 * @type {boolean}
 */
const $$IS_LOCALE_RTL = bidi.IS_RTL;



/**
 * Copies extra properties into an object if they do not already exist. The
 * destination object is mutated in the process.
 *
 * @param {?} obj The destination object to update.
 * @param {?} defaults An object with default properties to apply.
 * @return {?} The destination object for convenience.
 */
const $$assignDefaults = function(obj, defaults) {
  for (let key in defaults) {
    if (!(key in obj)) {
      obj[key] = defaults[key];
    }
  }

  return obj;
};


/**
 * Gets the keys in a map as an array. There are no guarantees on the order.
 * @param {!Object} map The map to get the keys of.
 * @return {!Array<string>} The array of keys in the given map.
 */
const $$getMapKeys = function(map) {
  const mapKeys = [];
  for (let key in map) {
    mapKeys.push(key);
  }
  return mapKeys;
};


/**
 * Returns the argument if it is not null.
 *
 * @param {T} val The value to check
 * @return {T_NOT_UNDEFINED} val if is isn't null
 * @template T
 * @template T_NOT_UNDEFINED :=
 *     cond(isUnknown(T), unknown(),
 *       mapunion(T, (X) =>
 *         cond(eq(X, 'undefined'), none(), cond(eq(X, 'null'), none(), X))))
 * =:
 */
const $$checkNotNull = function(val) {
  if (val == null) {
    throw Error('unexpected null value');
  }
  return val;
};


/**
 * Parses the given string into a base 10 integer. Returns null if parse is
 * unsuccessful.
 * @param {?string} str The string to parse
 * @param {number=} radix The base of the string
 * @return {?number} The string parsed as an integer, or null if unsuccessful
 */
const $$parseInt = function(str, radix = 10) {
  const parsed = parseInt(String(str), radix);
  return isNaN(parsed) ? null : parsed;
};

/**
 * When equals comparison cannot be expressed using JS runtime semantics for ==,
 * bail out to a runtime function. In practice, this only means comparisons
 * of boolean, string and number are valid for equals, and everything else needs
 * this function. Some sanitized content may be functions or objects that need
 * to be coerced to a string.
 * @param {?} valueOne
 * @param {?} valueTwo
 * @return {boolean}
 */
const $$equals = function(valueOne, valueTwo) {
  // Incremental DOM functions have to be coerced to a string. At runtime
  // they are tagged with a type for ATTR or HTML. They both need to be
  // the same to be considered structurally equal. Beware, as this is a
  // very expensive function.
  if ((valueOne && valueTwo) &&
      (valueOne.isInvokableFn && valueTwo.isInvokableFn)) {
    if ((/** @type {?} */ (valueOne)).contentKind !==
        (/** @type {?} */ (valueTwo)).contentKind) {
      return false;
    } else {
      return valueOne.toString() === valueTwo.toString();
    }
  }

  // Likewise for sanitized content.
  if (valueOne instanceof SanitizedContent &&
      valueTwo instanceof SanitizedContent) {
    if (valueOne.contentKind != valueTwo.contentKind) {
      return false;
    } else {
      return valueOne.toString() == valueTwo.toString();
    }
  }

  // Rely on javascript semantics for comparing two objects.
  return valueOne == valueTwo;
};


/**
 * @param {?} value
 * @return {boolean}
 */
const $$isFunction = function(value) {
  return typeof value === 'function';
};

/**
 * Parses the given string into a float. Returns null if parse is unsuccessful.
 * @param {?string} str The string to parse
 * @return {?number} The string parsed as a float, or null if unsuccessful.
 */
const $$parseFloat = function(str) {
  const parsed = parseFloat(str);
  return isNaN(parsed) ? null : parsed;
};

/**
 * Returns a random integer.
 * @return {number} a random integer between 0 and num
 */
const $$randomInt = function(/** number */ num) {
  return Math.floor(Math.random() * num);
};

/**
 * Rounds the given value to the closest decimal point left (negative numbers)
 * or right (positive numbers) of the decimal point
 *
 * TODO(b/112835292): This is probably not something that anyone should use,
 * instead they should use an i18n friendly number formatting routine.
 *
 * @return {number} the rounded value
 */
const $$round = function(/** number */ num, /** number */ numDigitsAfterPt) {
  const shift = Math.pow(10, numDigitsAfterPt);
  return Math.round(num * shift) / shift;
};

/** @return {boolean} returns whether the needle was found in the haystack */
const $$strContains = function(/** string */ haystack, /** string */ needle) {
  return haystack.indexOf(needle) != -1;
};

/**
 * Coerce the given value into a bool.
 *
 * @param {*} arg The argument to coerce.
 * @return {boolean}
 */
const $$coerceToBoolean = function(arg) {
  return !!arg;
};


/**
 * Returns whether `it` is a Soy iterable.
 *
 * @param {*} it The argument to test.
 * @return {boolean}
 */
const $$isIterable = function(it) {
  return it != null && typeof it === 'object' &&  // omit string
      typeof it[Symbol.iterator] === 'function' &&
      !(it instanceof Map);  // omit map
};


/**
 * Returns if the value is truthy or is a sanitized content with content.
 *
 * @param {*} arg The argument to coerce.
 * @return {boolean}
 */
const $$isTruthyNonEmpty = function(arg) {
  if (arg instanceof SanitizedContent) {
    return !!arg.getContent();
  }
  return !!arg;
};


/**
 * For sanitized types, returns true if content it not empty. Otherwise returns
 * standard boolean coercion.
 *
 * @param {*} arg The argument to coerce.
 * @return {boolean}
 */
const $$hasContent = function(arg) {
  return $$isTruthyNonEmpty(arg);
};


/**
 * Changes empty string/sanitized content to null.
 *
 * @param {*} arg The argument to coerce.
 * @return {*}
 */
const $$emptyToUndefined = function(arg) {
  return $$isTruthyNonEmpty(arg) ? arg : undefined;
};


/**
 * Gets a consistent unique id for the given delegate template name. Two calls
 * to this function will return the same id if and only if the input names are
 * the same.
 *
 * <p> Important: This function must always be called with a string constant.
 *
 * <p> If Closure Compiler is not being used, then this is just this identity
 * function. If Closure Compiler is being used, then each call to this function
 * will be replaced with a short string constant, which will be consistent per
 * input name.
 *
 * @param {string} delTemplateName The delegate template name for which to get a
 *     consistent unique id.
 * @return {string} A unique id that is consistent per input name.
 *
 * @idGenerator {consistent}
 */
const $$getDelTemplateId = function(delTemplateName) {
  return delTemplateName;
};


/**
 * Map from registered delegate template key to the priority of the
 * implementation.
 * @const {!Object<number>}
 */
const DELEGATE_REGISTRY_PRIORITIES_ = {};

/**
 * Map from registered delegate template key to the implementation function.
 * @const {!Object<!Function>}
 */
const DELEGATE_REGISTRY_FUNCTIONS_ = {};


/**
 * Returns a function for an empty deltemplate.
 *
 * @param {string} delTemplateId The name of the template.
 * @return {!Function} The generated empty template function.
 */
const $$makeEmptyTemplateFn = function(delTemplateId) {
  const generateFn = function(opt_data, opt_ijData) {
    if (goog.DEBUG && soy.$$stubsMap[delTemplateId]) {
      const $ijData = /** @type {!googSoy.IjData} */ (opt_ijData);
      return soy.$$stubsMap[delTemplateId](opt_data, $ijData);
    }
    return '';
  };
  if (goog.DEBUG) {
    /**
     * @nocollapse
     * @type {string}
     */
    generateFn.soyTemplateName = delTemplateId;
  }
  return generateFn;
};

/**
 * Registers a delegate implementation. If the same delegate template key (id
 * and variant) has been registered previously, then priority values are
 * compared and only the higher priority implementation is stored (if
 * priorities are equal, an error is thrown).
 *
 * @param {string} delTemplateId The delegate template id.
 * @param {string} delTemplateVariant The delegate template variant (can be
 *     empty string).
 * @param {number} delPriority The implementation's priority value.
 * @param {!Function} delFn The implementation function.
 */
const $$registerDelegateFn = function(
    delTemplateId, delTemplateVariant, delPriority, delFn) {
  const mapKey = 'key_' + delTemplateId + ':' + delTemplateVariant;
  const currPriority = DELEGATE_REGISTRY_PRIORITIES_[mapKey];
  if (currPriority === undefined || delPriority > currPriority) {
    // Registering new or higher-priority function: replace registry entry.
    DELEGATE_REGISTRY_PRIORITIES_[mapKey] = delPriority;
    DELEGATE_REGISTRY_FUNCTIONS_[mapKey] = delFn;
  } else if (delPriority == currPriority) {
    // Registering same-priority function: error.
    throw Error(
        'Encountered two active delegates with the same priority ("' +
        delTemplateId + ':' + delTemplateVariant + '").');
  } else {
    // Registering lower-priority function: do nothing.
  }
};


/**
 * Retrieves the (highest-priority) implementation that has been registered for
 * a given delegate template key (id and variant). If no implementation has
 * been registered for the key, then the fallback is the same id with empty
 * variant. If the fallback is also not registered, and allowsEmptyDefault is
 * true, then returns an implementation that is equivalent to an empty template
 * (i.e. rendered output would be empty string).
 *
 * @param {string} delTemplateId The delegate template id.
 * @param {string=} delTemplateVariant The delegate template variant (can be
 *     empty string).
 * @return {!Function} The retrieved implementation function.
 */
const $$getDelegateFn = function(delTemplateId, delTemplateVariant) {
  let delFn =
      DELEGATE_REGISTRY_FUNCTIONS_['key_' + delTemplateId + ':' + (delTemplateVariant || '')];
  if (!delFn && delTemplateVariant !== '') {
    // Fallback to empty variant.
    delFn = DELEGATE_REGISTRY_FUNCTIONS_['key_' + delTemplateId + ':'];
  }

  if (delFn) {
    return delFn;
  } else {
    return $$EMPTY_TEMPLATE_FN_;
  }
};


/**
 * Private helper soy.$$getDelegateFn(). This is the empty template function
 * that is returned whenever there's no delegate implementation found.
 *
 * Note: This is also used for idom.
 *
 * @return {string}
 */
const $$EMPTY_TEMPLATE_FN_ = function() {
  return '';
};


// -----------------------------------------------------------------------------
// Escape/filter/normalize.


/**
 * Returns a SanitizedHtml object for a particular value. The content direction
 * is preserved.
 *
 * This HTML-escapes the value unless it is already SanitizedHtml. Escapes
 * double quote '"' in addition to '&', '<', and '>' so that a string can be
 * included in an HTML tag attribute value within double quotes.
 *
 * @param {?} value The value to convert. If it is already a SanitizedHtml
 *     object, it is left alone.
 * @return {!SanitizedHtml} An escaped version of value.
 */
const $$escapeHtml = function(value) {
  return createSanitizedHtml(value);
};


/**
 * Strips unsafe tags to convert a string of untrusted HTML into HTML that
 * is safe to embed. The content direction is preserved.
 *
 * @param {?} value The string-like value to be escaped. May not be a string,
 *     but the value will be coerced to a string.
 * @param {?ReadonlyArray<string>=} safeTags Additional tag names to whitelist.
 * @return {!SanitizedHtml} A sanitized and normalized version of
 *     value.
 */
const $$cleanHtml = function(value, safeTags) {
  if ($$isHtmlOrHtmlTemplate(value)) {
    return /** @type {!SanitizedHtml} */ (value);
  }
  let tagWhitelist;
  if (safeTags) {
    tagWhitelist = Object.fromEntries(safeTags.map((tag) => [tag, true]));
    Object.assign(tagWhitelist, $$SAFE_TAG_WHITELIST_);
  } else {
    tagWhitelist = $$SAFE_TAG_WHITELIST_;
  }
  return VERY_UNSAFE.ordainSanitizedHtml(
      $$stripHtmlTags(value, tagWhitelist), getContentDir(value));
};


// LINT.IfChange(htmlToText)
/**
 * Converts HTML to plain text by removing tags, normalizing spaces and
 * converting entities.
 *
 * The last two parameters are idom functions.
 * @param {string|?SanitizedHtml|?SafeHtml|
 *     ?IdomFunction|?Function|undefined} value
 * @return {string}
 */
const $$htmlToText = function(value) {
  if (value == null) {
    return '';
  }
  let html;
  if (value instanceof SafeHtml) {
    html = unwrapHtml(value).toString();
  } else if ($$isHtmlOrHtmlTemplate(value)) {
    html = value.toString();
  } else {
    return asserts.assertString(value);
  }
  let text = '';
  let start = 0;
  // Tag name to stop removing contents, e.g. '/script'.
  let removingUntil = '';
  const preserveWhitespaceStack = [];
  const tagRe =
      /<(?:!--.*?--|(?:!|(\/?[a-z][\w:-]*))((?:[^>'"]|"[^"]*"|'[^']*')*))>|$/gi;
  for (let match; match = tagRe.exec(html);) {
    const tag = match[1];
    const attrs = match[2];
    const offset = match.index;
    const lowerCaseTag = tag ? tag.toLowerCase() : null;
    if (!removingUntil) {
      let chunk = html.substring(start, offset);
      chunk = googString.unescapeEntities(chunk);

      let preserveWhitespace =
          $$shouldPreserveWhitespace_(preserveWhitespaceStack);
      if (!preserveWhitespace) {
        // We are not inside <pre>, normalize spaces.
        chunk = chunk.replace(/[ \t\r\n]+/g, ' ');
        if (!/[^ \t\r\n]$/.test(text)) {
          // Strip leading space unless after non-whitespace.
          chunk = chunk.replace(/^ /, '');
        }
      }
      text += chunk;

      if (lowerCaseTag) {
        if (/^(script|style|textarea|title)$/.test(lowerCaseTag)) {
          removingUntil = '/' + lowerCaseTag;
        } else if (/^br$/.test(lowerCaseTag)) {
          // <br> adds newline even after newline.
          text += '\n';
        } else if (BLOCK_TAGS_RE_.test(lowerCaseTag)) {
          if (/[^\n]$/.test(text)) {
            // Block tags don't add more consecutive newlines.
            text += '\n';
          }
        } else if (/^(td|th)$/.test(lowerCaseTag)) {
          // We add \t even after newline to support more leading <td>.
          text += '\t';
        }

        if (!$$HTML5_VOID_ELEMENTS_.test('<' + lowerCaseTag + '>')) {
          $$updatePreserveWhitespaceStack(
              preserveWhitespaceStack, lowerCaseTag, attrs);
        }
      }
    } else if (removingUntil === lowerCaseTag) {
      removingUntil = '';
    }
    if (!match[0]) {
      break;
    }
    start = offset + match[0].length;
  }
  // replace non-breaking spaces with spaces, then return text;
  return text.replace(/\u00A0/g, ' ');
};

/**
 * A struct that holds two fields: an HTML tag, and whether that tag should
 * preserve whitespace within it.
 * @struct
 */
class TagPreservesWhitespace {
  constructor(tag, preserveWhitespace) {
    /** @const {string} */
    this.tag = tag;

    /** @const {boolean} */
    this.preserveWhitespace = preserveWhitespace;
  }
}

/**
 * Determines if whitespace should currently be preserved by inspecting the top
 * element of the stack.
 *
 * @param {!ReadonlyArray<!TagPreservesWhitespace>} preserveWhitespaceStack, an
 *     array of structs with properties tag and preserveWhitespace. The last
 *     element in the array is the top of the stack.
 * @return {boolean}
 */
const $$shouldPreserveWhitespace_ = function(preserveWhitespaceStack) {
  const stackSize = preserveWhitespaceStack.length;
  if (stackSize > 0) {
    return preserveWhitespaceStack[stackSize - 1].preserveWhitespace;
  }
  return false;
};

/**
 * Determines if whitespace should currently be preserved by inspecting the top
 * element of the stack.
 *
 * @param {string} style the style attribute value
 * @return {?boolean} true if the style says to preserve whitespace, false if it
 * says not to preserve whitespace, and null if it doesn't say either.
 */
const $$getStylePreservesWhitespace_ = function(style) {
  const styleRe =
      /[\t\n\r ]*([^:;\t\n\r ]*)[\t\n\r ]*:[\t\n\r ]*([^:;\t\n\r ]*)[\t\n\r ]*(?:;|$)/g;
  for (let styleMatch; styleMatch = styleRe.exec(style);) {
    const styleAttribute = styleMatch[1];
    if (/^white-space$/i.test(styleAttribute)) {
      const whitespaceStyle = styleMatch[2];
      if (/^(pre|pre-wrap|break-spaces)$/i.test(whitespaceStyle)) {
        return true;
      } else if (/^(normal|nowrap)$/i.test(whitespaceStyle)) {
        return false;
      }
    }
  }
  return null;
};

/**
 * Determines whether the specified attributes indicate whitespace should be
 * preserved. Returns null if the attributes don't indicate anything about
 * whitespace preservation.
 *
 * @param {string} attrs the attributes for the current tag.
 * @return {?boolean} true if the attrs say to preserve whitespace, false if
 * they say not to preserve whitespace, and null if they don't say either.
 */
const $$getAttributesPreserveWhitespace_ = function(attrs) {
  if (attrs !== '') {
    for (let attrMatch; attrMatch = $$HTML_ATTRIBUTE_REGEX_.exec(attrs);) {
      const attributeName = attrMatch[1];
      if (/^style$/i.test(attributeName)) {
        let style = attrMatch[2];
        // We've matched style and won't finish iterating through the attr
        // regex. Reset the regex matcher.
        $$HTML_ATTRIBUTE_REGEX_.lastIndex = 0;

        if (style !== '') {
          // Strip quotes if the attribute value was quoted.
          if (style.charAt(0) === '\'' || style.charAt(0) === '"') {
            style = style.substr(1, style.length - 2);
          }
          return $$getStylePreservesWhitespace_(style);
        }
        return null;
      }
    }
  }
  return null;
};

/**
 * Updates the preserveWhitespaceStack with the current tag and attributes. If
 * it is a closing tag, this will pop elements off the stack, or if it's an
 * open tag, it will deduce whether the tag or attrs indicate to preserve
 * whitespace, and push a new element on the stack accordingly.
 *
 * @param {!Array<!TagPreservesWhitespace>} preserveWhitespaceStack, an array of
 * structs with properties tag and preserveWhitespace. The last element in the
 * array is the top of the stack.
 * @param {string} lowerCaseTag the current tag, in lower case
 * @param {string} attrs the attributes for the current tag
 */
const $$updatePreserveWhitespaceStack = function(
    preserveWhitespaceStack, lowerCaseTag, attrs) {
  if (lowerCaseTag.charAt(0) === '/') {
    const closedTag = lowerCaseTag.substring(1);
    // Pop tags until we pop one that matches the tag that's being closed. This
    // means we're effectively automatically closing tags that aren't closed.
    while (preserveWhitespaceStack.length > 0 &&
           preserveWhitespaceStack.pop().tag !== closedTag) {
    }
  } else if (/^pre$/.test(lowerCaseTag)) {
    preserveWhitespaceStack.push(
        new TagPreservesWhitespace(lowerCaseTag, true));
  } else {
    let preserveWhitespace = $$getAttributesPreserveWhitespace_(attrs);
    if (preserveWhitespace == null) {
      preserveWhitespace = $$shouldPreserveWhitespace_(preserveWhitespaceStack);
    }
    preserveWhitespaceStack.push(
        new TagPreservesWhitespace(lowerCaseTag, preserveWhitespace));
  }
};

/** @const */
const BLOCK_TAGS_RE_ =
    /^\/?(address|blockquote|dd|div|dl|dt|h[1-6]|hr|li|ol|p|pre|table|tr|ul)$/;
// LINT.ThenChange(
//     ../../../third_party/java_src/soy/java/com/google/template/soy/basicfunctions/HtmlToText.java,
//     ../../../third_party/java_src/soy/python/runtime/sanitize.py:htmlToText)


/**
 * Escapes HTML, except preserves entities.
 *
 * Used mainly internally for escaping message strings in attribute and rcdata
 * context, where we explicitly want to preserve any existing entities.
 *
 * @param {?} value Value to normalize.
 * @return {string} A value safe to insert in HTML without any quotes or angle
 *     brackets.
 */
const $$normalizeHtml = function(value) {
  return $$normalizeHtmlHelper(value);
};


/**
 * Escapes HTML special characters in a string so that it can be embedded in
 * RCDATA.
 * <p>
 * Escapes HTML special characters so that the value will not prematurely end
 * the body of a tag like `<textarea>` or `<title>`. RCDATA tags
 * cannot contain other HTML entities, so it is not strictly necessary to escape
 * HTML special characters except when part of that text looks like an HTML
 * entity or like a close tag : `</textarea>`.
 * <p>
 * Will normalize known safe HTML to make sure that sanitized HTML (which could
 * contain an innocuous `</textarea>` don't prematurely end an RCDATA
 * element.
 *
 * @param {?} value The string-like value to be escaped. May not be a string,
 *     but the value will be coerced to a string.
 * @return {string} An escaped version of value.
 */
const $$escapeHtmlRcdata = function(value) {
  if ($$isHtml(value)) {
    return $$normalizeHtmlHelper(value.getContent());
  }
  return $$escapeHtmlHelper(value);
};


/**
 * Matches any/only HTML5 void elements' start tags.
 * See http://www.w3.org/TR/html-markup/syntax.html#syntax-elements
 * @const {!RegExp}
 */
const $$HTML5_VOID_ELEMENTS_ = new RegExp(
    '^<(?:area|base|br|col|command|embed|hr|img|input' +
    '|keygen|link|meta|param|source|track|wbr)\\b');


/**
 * Removes HTML tags from a string of known safe HTML.
 * If opt_tagWhitelist is not specified or is empty, then
 * the result can be used as an attribute value.
 *
 * @param {?} value The HTML to be escaped. May not be a string, but the
 *     value will be coerced to a string.
 * @param {?Object<string, boolean>=} tagWhitelist Has an own property whose
 *     name is a lower-case tag name and whose value is `1` for
 *     each element that is allowed in the output.
 * @return {string} A representation of value without disallowed tags,
 *     HTML comments, or other non-text content.
 */
const $$stripHtmlTags = function(value, tagWhitelist) {
  if (!tagWhitelist) {
    // If we have no white-list, then use a fast track which elides all tags.
    return $$replaceHtmlTags_(String(value), (match, tag) => '')
        // This is just paranoia since callers should normalize the result
        // anyway, but if they didn't, it would be necessary to ensure that
        // after the first replace non-tag uses of < do not recombine into
        // tags as in "<<foo>script>alert(1337)</<foo>script>".
        .replace($$LT_REGEX_, '&lt;');
  }

  // Escapes '[' so that we can use [123] below to mark places where tags
  // have been removed.
  let html = String(value).replace(/\[/g, '&#91;');

  // Consider all uses of '<' and replace whitelisted tags with markers like
  // [1] which are indices into a list of approved tag names.
  // Replace all other uses of < and > with entities.
  const tags = [];
  const attrs = [];
  html = $$replaceHtmlTags_(html, (tok, tagName) => {
    if (tagName) {
      tagName = tagName.toLowerCase();
      if (tagWhitelist.hasOwnProperty(tagName) && tagWhitelist[tagName]) {
        const isClose = tok.charAt(1) === '/';
        const index = tags.length;
        let start = '</';
        let attributes = '';
        if (!isClose) {
          start = '<';
          let match;
          while ((match = $$HTML_ATTRIBUTE_REGEX_.exec(tok))) {
            if (match[1] && match[1].toLowerCase() === 'dir') {
              let dir = match[2];
              if (dir) {
                if (dir.charAt(0) === '\'' || dir.charAt(0) === '"') {
                  dir = dir.substr(1, dir.length - 2);
                }
                dir = dir.toLowerCase();
                if (dir === 'ltr' || dir === 'rtl' || dir === 'auto') {
                  attributes = ' dir="' + dir + '"';
                }
              }
              break;
            }
          }
          $$HTML_ATTRIBUTE_REGEX_.lastIndex = 0;
        }
        tags[index] = start + tagName + '>';
        attrs[index] = attributes;
        return '[' + index + ']';
      }
    }
    return '';
  });

  // Escape HTML special characters. Now there are no '<' in html that could
  // start a tag.
  html = $$normalizeHtmlHelper(html);

  const finalCloseTags = $$balanceTags_(tags);

  // Now html contains no tags or less-than characters that could become
  // part of a tag via a replacement operation and tags only contains
  // approved tags.
  // Reinsert the white-listed tags.
  html = html.replace(/\[(\d+)\]/g, function(_, index) {
    if (attrs[index] && tags[index]) {
      return tags[index].substr(0, tags[index].length - 1) + attrs[index] + '>';
    }
    return tags[index];
  });

  // Close any still open tags.
  // This prevents unclosed formatting elements like <ol> and <table> from
  // breaking the layout of containing HTML.
  return html + finalCloseTags;
};

/**
 * @enum {number}
 */
const STATE = {
  DEFAULT: 0,
  TAG: 1,
};


/** @type {boolean} */
const hasNativeY = RegExp.prototype.hasOwnProperty('sticky');

/**
 * Matches the start of an HTML comment, open, or close tag after encountering
 * a '<' character.
 *
 * The 'y' qualifier means this regex is sticky, meaning that it matches as
 * though it starts with ^ and is applied to s.substring(lastIndex).
 *
 * @type {!RegExp}
 */
const $$HTML_TAG_FIRST_TOKEN_ = new RegExp(
    (hasNativeY ? '' : '^') + '(?:!|\/?([a-zA-Z][a-zA-Z0-9:-]*))',
    hasNativeY ? 'gy' : 'g');

/**
 * Replaces all matches of an HTML tag (matching Java's HTML_TAG_CONTENT
 * Pattern) in `s` with the contents returned by `callback`.
 *
 * @param {string} s
 * @param {function(string, ?string):string} callback
 * @return {string}
 * @private
 */
function $$replaceHtmlTags_(s, callback) {
  const buffer = [];
  const l = s.length;

  let state = STATE.DEFAULT;
  /** @type {!Array<string>} */
  let tagBuffer = [];
  let tagName;
  let tagStartIdx;

  const reset = () => {
    state = STATE.DEFAULT;
    tagBuffer = [];
    tagName = null;
    tagStartIdx = null;
  };

  let i = 0;
  while (i < l) {
    switch (state) {
      case STATE.DEFAULT:
        const nextLt = s.indexOf('<', i);
        if (nextLt < 0) {
          // No more < found, push remaining string on buffer and exit.
          if (buffer.length === 0) {
            return s;
          }
          buffer.push(s.substring(i));
          i = l;
        } else {
          // Push up to < onto buffer.
          buffer.push(s.substring(i, nextLt));
          tagStartIdx = nextLt;
          i = nextLt + 1;

          // Search for required token after <
          let match;
          if (hasNativeY) {
            $$HTML_TAG_FIRST_TOKEN_.lastIndex = i;
            match = $$HTML_TAG_FIRST_TOKEN_.exec(s);
          } else {
            $$HTML_TAG_FIRST_TOKEN_.lastIndex = 0;
            match = $$HTML_TAG_FIRST_TOKEN_.exec(s.substring(i));
          }
          if (match) {
            // We found a start tag, push contents onto tag buffer.
            tagBuffer = ['<', match[0]];
            tagName = match[1];
            state = STATE.TAG;
            i += match[0].length;
          } else {
            // Otherwise push < to the buffer and continue.
            buffer.push('<');
          }
        }
        break;

      case STATE.TAG:
        const char = s.charAt(i++);
        switch (char) {
          case '\'':
          case '"':
            // Find the corresponding closing quote.
            let nextQuote = s.indexOf(char, i);
            if (nextQuote < 0) {
              // If non closing we will have to backtrack.
              i = l;
            } else {
              // Push full quote token onto tag buffer.
              tagBuffer.push(char, s.substring(i, nextQuote + 1));
              i = nextQuote + 1;
            }
            break;

          case '>':
            // We found the end of the tag!
            tagBuffer.push(char);
            buffer.push(callback(tagBuffer.join(''), tagName));
            reset();
            break;

          default:
            tagBuffer.push(char);
        }
        break;
      default:
        throw new Error();
    }

    // Check if we exhausted the input without completing the tag. In this case
    // we need to backtrack because we may have skipped over fully formed tags
    // while we thought we were in a tag. e.g.: <b'<b>
    if (state === STATE.TAG && i >= l) {
      // Push the < that started the incomplete tag and backtrack to the next
      // character.
      i = tagStartIdx + 1;
      buffer.push('<');
      reset();
    }
  }

  return buffer.join('');
}
// LINT.ThenChange(//depot/google3/third_party/java_src/soy/java/com/google/template/soy/shared/internal/Sanitizers.java)

/**
 * Escapes tokens that are not allowed for interpolated style values.
 *
 * When stringMode is false, the value is considered a trusted stylesheet values
 * that follows the SafeStyleSheet contract. In which case, it escapes enough to
 * prevent the value from closing the style element.
 * When stringMode is true, the value is considered a string that is
 * interpolated into a style value. Such values are not allowed to create CSS
 * declaration blocks. In addition to the previous escaping, this mode escapes
 * enough '{', '}', '/*' and adds a space after a potential trailing backslash.
 * This ensures that values cannot mess with the trusted CSS they are embedded
 * in.
 * @param {string} css
 * @param {boolean} stringMode
 * @return {string}
 */
const $$embedCssIntoHtml_ = function(css, stringMode) {
  // Port of a method of the same name in
  // com.google.template.soy.shared.restricted.Sanitizers
  const htmlEscaped = css.replace(/<\//g, '<\\/').replace(/\]\]>/g, ']]\\>');
  if (stringMode) {
    return htmlEscaped.replace(/{/g, ' \\{')
        .replace(/}/g, ' \\}')
        .replace(/\/\*/g, '/ *')
        .replace(/\\$/, '\\ ');
  }
  return htmlEscaped;
};


/**
 * Throw out any close tags that don't correspond to start tags.
 * If `<table>` is used for formatting, embedded HTML shouldn't be able
 * to use a mismatched `</table>` to break page layout.
 *
 * @param {!Array<string>} tags Array of open/close tags (e.g. '<p>', '</p>')
 *    that will be modified in place to be either an open tag, one or more close
 *    tags concatenated, or the empty string.
 * @return {string} zero or more closed tags that close all elements that are
 *    opened in tags but not closed.
 * @package
 */
const $$balanceTags_ = function(tags) {
  const open = [];
  for (let i = 0, n = tags.length; i < n; ++i) {
    const tag = tags[i];
    if (tag.charAt(1) == '/') {
      const openTagIndex = open.lastIndexOf(tag);
      if (openTagIndex < 0) {
        tags[i] = '';  // Drop close tag with no corresponding open tag.
      } else {
        tags[i] = open.slice(openTagIndex).reverse().join('');
        open.length = openTagIndex;
      }
    } else if (
        tag == '<li>' && open.lastIndexOf('</ol>') < 0 &&
        open.lastIndexOf('</ul>') < 0) {
      // Drop <li> if it isn't nested in a parent <ol> or <ul>.
      tags[i] = '';
    } else if (!$$HTML5_VOID_ELEMENTS_.test(tag)) {
      open.push('</' + tag.substring(1));
    }
  }
  return open.reverse().join('');
};


/**
 * Escapes HTML special characters in an HTML attribute value.
 *
 * @param {?} value The HTML to be escaped. May not be a string, but the
 *     value will be coerced to a string.
 * @return {string} An escaped version of value.
 */
const $$escapeHtmlAttribute = function(value) {
  // NOTE: We don't accept ATTRIBUTES here because ATTRIBUTES is actually not
  // the attribute value context, but instead k/v pairs.
  if ($$isHtml(value)) {
    // NOTE: After removing tags, we also escape quotes ("normalize") so that
    // the HTML can be embedded in attribute context.
    return $$normalizeHtmlHelper($$stripHtmlTags(value.getContent()));
  }
  return $$escapeHtmlHelper(value);
};


/**
 * Escapes HTML special characters in an HTML attribute value containing HTML
 * code, such as <iframe srcdoc>.
 *
 * @param {?} value The HTML to be escaped. May not be a string, but the
 *     value will be coerced to a string.
 * @return {string} An escaped version of value.
 */
const $$escapeHtmlHtmlAttribute = function(value) {
  return String($$escapeHtml(value));
};


/**
 * Escapes HTML special characters in a string including space and other
 * characters that can end an unquoted HTML attribute value.
 *
 * @param {?} value The HTML to be escaped. May not be a string, but the
 *     value will be coerced to a string.
 * @return {string} An escaped version of value.
 */
const $$escapeHtmlAttributeNospace = function(value) {
  if ($$isHtml(value)) {
    return $$normalizeHtmlNospaceHelper($$stripHtmlTags(value.getContent()));
  }
  return $$escapeHtmlNospaceHelper(value);
};

/**
 * Filters out strings that cannot be valid content in a <script> tag with
 * non-JS content.
 *
 * This disallows `</script`, and `<!--` as substrings as well as
 * prefixes of those strings that occur at the end of the value.  This combined
 * with a similar rule enforced in the parser ensures that these substrings
 * cannot occur.
 *
 * @param {?} value The value to escape. May not be a string, but the value
 *     will be coerced to a string.
 * @return {string} The value coerced to a string or `"zSoyz"` if the input is
 *    invalid.
 */
const $$filterHtmlScriptPhrasingData = function(value) {
  const valueAsString = String(value);
  /**
   * Returns whether there is a case insensitive match for needle within
   * haystack starting at offset, or if haystack ends with a non empty prefix of
   * needle.
   * @return {boolean}
   */
  const matchPrefixIgnoreCasePastEnd =
      (/** string */ needle, /** string */ haystack, /** number */ offset) => {
        asserts.assert(
            offset >= 0 && offset < haystack.length,
            'offset must point at a valid character of haystack');
        asserts.assert(
            needle === $$strToAsciiLowerCase(needle),
            'needle must be lowercase');
        const charsLeft = haystack.length - offset;
        const charsToScan = Math.min(charsLeft, needle.length);
        for (let i = 0; i < charsToScan; i++) {
          if (needle[i] !== $$charToAsciiLowerCase_(haystack[offset + i])) {
            return false;
          }
        }
        return true;
      };
  let start = 0;
  let indexOfLt;
  while ((indexOfLt = valueAsString.indexOf('<', start)) != -1) {
    if (matchPrefixIgnoreCasePastEnd('</script', valueAsString, indexOfLt) ||
        matchPrefixIgnoreCasePastEnd('<!--', valueAsString, indexOfLt)) {
      asserts.fail(
          'Bad value `%s` for |filterHtmlScriptPhrasingData', [valueAsString]);
      return 'zSoyz';
    }
    start = indexOfLt + 1;
  }
  return valueAsString;
};

/**
 * Filters out strings that cannot be a substring of a valid HTML attribute.
 *
 * Note the input is expected to be key=value pairs.
 *
 * @param {?} value The value to escape. May not be a string, but the value
 *     will be coerced to a string.
 * @return {string} A valid HTML attribute name part or name/value pair.
 *     `"zSoyz"` if the input is invalid.
 */
const $$filterHtmlAttributes = function(value) {
  // NOTE: Explicitly no support for SanitizedContentKind.HTML, since that is
  // meaningless in this context, which is generally *between* html attributes.
  if ($$isAttribute(value)) {
    return value.getContent();
  }
  // TODO: Dynamically inserting attributes that aren't marked as trusted is
  // probably unnecessary.  Any filtering done here will either be inadequate
  // for security or not flexible enough.  Having clients use kind="attributes"
  // in parameters seems like a wiser idea.
  return $$filterHtmlAttributesHelper(value);
};

/**
 * Conditionally prepends a single space if value is not empty.
 *
 * @param {?} value The value.
 * @return {string} value, possibly with an extra leading space.
 */
const $$whitespaceHtmlAttributes = function(value) {
  if ($$isAttribute(value)) {
    value = value.getContent();
  }
  return (value && !value.startsWith(' ') ? ' ' : '') + value;
};

/**
 * Allows only decimal and floating-point numbers.
 * @param {?} value
 * @return {number} The number.
 */
const $$filterNumber = function(value) {
  return /^\d*\.?\d+$/.test(value) ? value : 'zSoyz';
};


/**
 * Filters out strings that cannot be a substring of a valid HTML element name.
 *
 * @param {?} value The value to escape. May not be a string, but the value
 *     will be coerced to a string.
 * @return {string} A valid HTML element name part.
 *     `"zSoyz"` if the input is invalid.
 */
const $$filterHtmlElementName = function(value) {
  // NOTE: We don't accept any SanitizedContent here. HTML indicates valid
  // PCDATA, not tag names. A sloppy developer shouldn't be able to cause an
  // exploit:
  // ... {let userInput}script src=http://evil.com/evil.js{/let} ...
  // ... {param tagName kind="html"}{$userInput}{/param} ...
  // ... <{$tagName}>Hello World</{$tagName}>
  return $$filterHtmlElementNameHelper(value);
};


/**
 * Escapes characters in the value to make it valid content for a JS string
 * literal.
 *
 * @param {?} value The value to escape. May not be a string, but the value
 *     will be coerced to a string.
 * @return {string} An escaped version of value.
 */
const $$escapeJsString = function(value) {
  return $$escapeJsStringHelper(value);
};


/**
 * Encodes a value as a JavaScript literal.
 *
 * @param {?} value The value to escape. May not be a string, but the value
 *     will be coerced to a string.
 * @return {string} A JavaScript code representation of the input.
 */
const $$escapeJsValue = function(value) {
  // We surround values with spaces so that they can't be interpolated into
  // identifiers by accident.
  // We could use parentheses but those might be interpreted as a function call.
  if (value == null) {  // Intentionally matches undefined.
    // Java returns null from maps where there is no corresponding key while
    // JS returns undefined.
    // We always output null for compatibility with Java which does not have a
    // distinct undefined value.
    return ' null ';
  }
  if ($$isJS(value)) {
    return value.getContent();
  }
  if (value instanceof SafeScript) {
    return unwrapScript(value).toString();
  }
  switch (typeof value) {
    case 'boolean':
    case 'number':
      return ' ' + value + ' ';
    default:
      return '\'' + $$escapeJsStringHelper(String(value)) + '\'';
  }
};


/**
 * Escapes characters in the string to make it valid content for a JS regular
 * expression literal.
 *
 * @param {?} value The value to escape. May not be a string, but the value
 *     will be coerced to a string.
 * @return {string} An escaped version of value.
 */
const $$escapeJsRegex = function(value) {
  return $$escapeJsRegexHelper(value);
};


/**
 * Matches all URI mark characters that conflict with HTML attribute delimiters
 * or that cannot appear in a CSS uri.
 * From <a href="http://www.w3.org/TR/CSS2/grammar.html">G.2: CSS grammar</a>
 * <pre>
 *     url        ([!#$%&*-~]|{nonascii}|{escape})*
 * </pre>
 *
 * @const {!RegExp}
 */
const $$problematicUriMarks_ = /['()]/g;

/**
 * @param {string} ch A single character in {@link $$problematicUriMarks_}.
 * @return {string}
 */
const $$pctEncode_ = function(ch) {
  return '%' + ch.charCodeAt(0).toString(16);
};

/**
 * Escapes a string so that it can be safely included in a URI.
 *
 * @param {?} value The value to escape. May not be a string, but the value
 *     will be coerced to a string.
 * @return {string} An escaped version of value.
 */
const $$escapeUri = function(value) {
  // NOTE: We don't check for SanitizedUri or SafeUri, because just because
  // something is already a valid complete URL doesn't mean we don't want to
  // encode it as a component.  For example, it would be bad if
  // ?redirect={$url} didn't escape ampersands, because in that template, the
  // continue URL should be treated as a single unit.

  // Apostophes and parentheses are not matched by encodeURIComponent.
  // They are technically special in URIs, but only appear in the obsolete mark
  // production in Appendix D.2 of RFC 3986, so can be encoded without changing
  // semantics.
  const encoded = $$escapeUriHelper(value);
  $$problematicUriMarks_.lastIndex = 0;
  if ($$problematicUriMarks_.test(encoded)) {
    return encoded.replace($$problematicUriMarks_, $$pctEncode_);
  }
  return encoded;
};


/**
 * Removes rough edges from a URI by escaping any raw HTML/JS string delimiters.
 *
 * @param {?} value The value to escape. May not be a string, but the value
 *     will be coerced to a string.
 * @return {string} An escaped version of value.
 */
const $$normalizeUri = function(value) {
  return $$normalizeUriHelper(value);
};


/**
 * Vets a URI's protocol and removes rough edges from a URI by escaping
 * any raw HTML/JS string delimiters.
 *
 * @param {?} value The value to escape. May not be a string, but the value
 *     will be coerced to a string.
 * @return {string} An escaped version of value.
 */
const $$filterNormalizeUri = function(value) {
  if ($$isURI(value)) {
    return $$normalizeUri(value);
  }
  if (isUrl(value)) {
    return $$normalizeUri(unwrapUrl(value));
  }
  if (value instanceof TrustedResourceUrl) {
    return $$normalizeUri(unwrapResourceUrl(value).toString());
  }
  return $$filterNormalizeUriHelper(value);
};


/**
 * Vets a URI for usage as an image source.
 *
 * @param {?} value The value to filter. Might not be a string, but the value
 *     will be coerced to a string.
 * @return {string} An escaped version of value.
 */
const $$filterNormalizeMediaUri = function(value) {
  // Image URIs are filtered strictly more loosely than other types of URIs.
  // TODO(shwetakarwa): Add tests for this in soyutils_test_helper while adding
  // tests for filterTrustedResourceUri.
  if ($$isURI(value)) {
    return $$normalizeUri(value);
  }
  if (isUrl(value)) {
    return $$normalizeUri(unwrapUrl(value));
  }
  if (value instanceof TrustedResourceUrl) {
    return $$normalizeUri(unwrapResourceUrl(value).toString());
  }
  return $$filterNormalizeMediaUriHelper(value);
};


/**
 * Like filterNormalizeUri but also escapes ';'.
 * @param {?} value The value to filter.
 * @return {string} An escaped version of value.
 */
const $$filterNormalizeRefreshUri = function(value) {
  return $$filterNormalizeUri(value).replace(/;/g, '%3B');
};


/**
 * Vets a URI for usage as a resource. Makes sure the input value is a compile
 * time constant or a TrustedResource not in attacker's control.
 *
 * @param {?} value The value to filter.
 * @return {string} The value content.
 */
const $$filterTrustedResourceUri = function(value) {
  if ($$isTrustedResourceURI(value)) {
    return value.getContent();
  }
  if (value instanceof TrustedResourceUrl) {
    return unwrapResourceUrl(value).toString();
  }
  asserts.fail('Bad value `%s` for |filterTrustedResourceUri', [String(value)]);
  return 'about:invalid#zSoyz';
};


/**
 * Allows only data-protocol image URI's.
 *
 * @param {?} value The value to process. May not be a string, but the value
 *     will be coerced to a string.
 * @return {!SanitizedUri} An escaped version of value.
 */
const $$filterImageDataUri = function(value) {
  // NOTE: Even if it's a SanitizedUri, we will still filter it.
  return VERY_UNSAFE.ordainSanitizedUri($$filterImageDataUriHelper(value));
};


/**
 * Allows only sip URIs.
 *
 * @param {?} value The value to process. May not be a string, but the value
 *     will be coerced to a string.
 * @return {!SanitizedUri} An escaped version of value.
 */
const $$filterSipUri = function(value) {
  // NOTE: Even if it's a SanitizedUri, we will still filter it.
  return VERY_UNSAFE.ordainSanitizedUri($$filterSipUriHelper(value));
};

/**
 * Function that converts sms uri string to a sanitized uri
 *
 * @param {string} value sms uri
 * @return {!SanitizedUri} An sanitized version of the sms uri.
 */
const $$strSmsUriToUri = function(value) {
  // NOTE: Even if it's a SanitizedUri, we will still filter it.
  return VERY_UNSAFE.ordainSanitizedUri($$filterSmsUriHelper(value));
};


/**
 * Allows only tel URIs.
 *
 * @param {?} value The value to process. May not be a string, but the value
 *     will be coerced to a string.
 * @return {!SanitizedUri} An escaped version of value.
 */
const $$filterTelUri = function(value) {
  // NOTE: Even if it's a SanitizedUri, we will still filter it.
  return VERY_UNSAFE.ordainSanitizedUri($$filterTelUriHelper(value));
};

/**
 * Escapes a string so it can safely be included inside a quoted CSS string.
 *
 * @param {?} value The value to escape. May not be a string, but the value
 *     will be coerced to a string.
 * @return {string} An escaped version of value.
 */
const $$escapeCssString = function(value) {
  return $$escapeCssStringHelper(value);
};


/**
 * Encodes a value as a CSS identifier part, keyword, or quantity.
 *
 * @param {?} value The value to escape. May not be a string, but the value
 *     will be coerced to a string.
 * @return {string} A safe CSS identifier part, keyword, or quanitity.
 */
const $$filterCssValue = function(value) {
  if ($$isCss(value)) {
    return $$embedCssIntoHtml_(value.getContent(), false);
  }
  // Uses == to intentionally match null and undefined for Java compatibility.
  if (value == null) {
    return '';
  }
  // Note: SoyToJsSrcCompiler uses $$filterCssValue both for the contents of
  // <style> (list of rules) and for the contents of style="" (one set of
  // declarations). We support SafeStyleSheet here to be used inside <style> but
  // it also wrongly allows it inside style="". We should instead change
  // SoyToJsSrcCompiler to use a different function inside <style>.
  if (value instanceof SafeStyleSheet) {
    return $$embedCssIntoHtml_(unwrapStyleSheet(value), false);
  }
  return $$embedCssIntoHtml_(String(value), true);
};

/**
 * Encodes a value as a CSP nonce value.
 *
 * @param {?} value The value to escape. Does not have to be a string, but the
 *     value will be coerced to a string.
 * @return {string} A safe CSP nonce value.
 */
const $$filterCspNonceValue = function(value) {
  return $$filterCspNonceValueHelper(value);
};


// -----------------------------------------------------------------------------
// Basic directives/functions.


/**
 * Converts \r\n, \r, and \n to <br>s
 * @param {?} value The string in which to convert newlines.
 * @return {string|!SanitizedHtml} A copy of `value` with
 *     converted newlines. If `value` is SanitizedHtml, the return value
 *     is also SanitizedHtml, of the same known directionality.
 */
const $$changeNewlineToBr = function(value) {
  const result = googString.newLineToBr(String(value), false);
  if ($$isHtmlOrHtmlTemplate(value)) {
    return VERY_UNSAFE.ordainSanitizedHtml(result, getContentDir(value));
  }
  return result;
};


/**
 * Inserts word breaks ('wbr' tags) into a HTML string at a given interval. The
 * counter is reset if a space is encountered. Word breaks aren't inserted into
 * HTML tags or entities. Entities count towards the character count; HTML tags
 * do not.
 *
 * @param {?} value The HTML string to insert word breaks into. Can be other
 *     types, but the value will be coerced to a string.
 * @param {number} maxCharsBetweenWordBreaks Maximum number of non-space
 *     characters to allow before adding a word break.
 * @return {string|!SanitizedHtml} The string including word
 *     breaks. If `value` is SanitizedHtml, the return value
 *     is also SanitizedHtml, of the same known directionality.
 * @deprecated The |insertWordBreaks directive is deprecated.
 *     Prefer wrapping with CSS white-space: break-word.
 */
const $$insertWordBreaks = function(value, maxCharsBetweenWordBreaks) {
  const result =
      googFormat.insertWordBreaks(String(value), maxCharsBetweenWordBreaks);
  if ($$isHtmlOrHtmlTemplate(value)) {
    return VERY_UNSAFE.ordainSanitizedHtml(result, getContentDir(value));
  }
  return result;
};

/**
 * Joins items with a semicolon, filtering out falsey values.
 * @param {...(string|SanitizedCss!|boolean|null|undefined|!ReadonlyArray<?>)}
 *     values The values to join.
 * @return {string} The joined string.
 */
const $$buildAttrValue = function(...values) {
  return values.flat().filter((s) => $$isTruthyNonEmpty(s)).join(';');
};


/**
 * Joins items with a space, filtering out falsey values.
 * @param {...(string|SanitizedCss!|boolean|null|undefined|!ReadonlyArray<?>)}
 *     values The values to join.
 * @return {string} The joined string.
 */
const $$buildClassValue = function(...values) {
  return values.flat().filter((s) => s).join(' ');
};


/**
 * Joins items with a semicolon, filtering out falsey values.
 * @param {...(string|SanitizedCss!|boolean|null|undefined)} values The
 *     values to join. If passing a raw string, it must be a single CSS
 *     declaration.
 * @return {SanitizedCss!|$$EMPTY_STRING_!} The joined string.
 */
const $$buildStyleValue = function(...values) {
  return VERY_UNSAFE.ordainSanitizedCss(
      values.filter((s) => s)
          .map((s) => {
            if (typeof s === 'string') {
              const firstColonPos = s.indexOf(':');
              if (firstColonPos != -1) {
                const name = s.substr(0, firstColonPos);
                if (name.match(/^\s*[\w-]+\s*$/)) {
                  const value = s.substr(firstColonPos + 1);
                  return name.trim() + ':' +
                      $$filterCssValue(value.trim().replaceAll(/;$/g, ''));
                }
              }
            }
            return $$filterCssValue(s);
          })
          .join(';'));
};


/**
 * Joins items with the correct delimiter, filtering out falsey values and
 * returns an attribute key/value pair.
 * @param {string} attrName The name of the attribute.
 * @param {...(string|SanitizedCss!|boolean|null|undefined)} values The
 *     values to join.
 * @return {SanitizedHtmlAttribute!} The joined string.
 */
const $$buildAttr = function(attrName, ...values) {
  const joined = attrName === 'class' ? $$buildClassValue(...values) :
                                        $$buildAttrValue(...values);
  if (!joined) {
    return VERY_UNSAFE.ordainSanitizedHtmlAttribute('');
  }
  return VERY_UNSAFE.ordainSanitizedHtmlAttribute(
      `${attrName}="${$$escapeHtmlAttribute(joined)}"`);
};

/**
 * Conditionally concatenates two attribute values with a delimiter if they are
 * both non-empty.
 *
 * @param {string} l
 * @param {string} r
 * @param {string} delimiter
 * @return {string}
 */
const $$concatAttributeValues = function(l, r, delimiter) {
  if (!l) {
    return r;
  }
  if (!r) {
    return l;
  }
  return l + delimiter + r;
};


/**
 * Conditionally concatenates two attribute values with a delimiter if they are
 * both non-empty.
 *
 * @param {!SanitizedCss} l
 * @param {!SanitizedCss} r
 * @return {!SanitizedCss|!$$EMPTY_STRING_}
 */
const $$concatCssValues = function(l, r) {
  if (!l || !r) {
    return l || r || $$EMPTY_STRING_;
  }

  if (l.getContent() !== $$EMPTY_STRING_.VALUE) {
    asserts.assertInstanceof(l, SanitizedCss);
  }
  if (r.getContent() !== $$EMPTY_STRING_.VALUE) {
    asserts.assertInstanceof(r, SanitizedCss);
  }
  return VERY_UNSAFE.ordainSanitizedCss(
      $$concatAttributeValues(l.getContent(), r.getContent(), ';'));
};


/**
 * Truncates a string to a given max length (if it's currently longer),
 * optionally adding ellipsis at the end.
 *
 * @param {?} str The string to truncate. Can be other types, but the value will
 *     be coerced to a string.
 * @param {number} maxLen The maximum length of the string after truncation
 *     (including ellipsis, if applicable).
 * @param {boolean} doAddEllipsis Whether to add ellipsis if the string needs
 *     truncation.
 * @return {string} The string after truncation.
 */
const $$truncate = function(str, maxLen, doAddEllipsis) {
  str = String(str);
  if (str.length <= maxLen) {
    return str;  // no need to truncate
  }

  // If doAddEllipsis, either reduce maxLen to compensate, or else if maxLen is
  // too small, just turn off doAddEllipsis.
  if (doAddEllipsis) {
    if (maxLen > 3) {
      maxLen -= 3;
    } else {
      doAddEllipsis = false;
    }
  }

  // Make sure truncating at maxLen doesn't cut up a unicode surrogate pair.
  if ($$isHighSurrogate_(str.charCodeAt(maxLen - 1)) &&
      $$isLowSurrogate_(str.charCodeAt(maxLen))) {
    maxLen -= 1;
  }

  // Truncate.
  str = str.substring(0, maxLen);

  // Add ellipsis.
  if (doAddEllipsis) {
    str += '...';
  }

  return str;
};

/**
 * Private helper for $$truncate() to check whether a char is a high surrogate.
 * @param {number} cc The codepoint to check.
 * @return {boolean} Whether the given codepoint is a unicode high surrogate.
 */
const $$isHighSurrogate_ = function(cc) {
  return 0xD800 <= cc && cc <= 0xDBFF;
};

/**
 * Private helper for $$truncate() to check whether a char is a low surrogate.
 * @param {number} cc The codepoint to check.
 * @return {boolean} Whether the given codepoint is a unicode low surrogate.
 */
const $$isLowSurrogate_ = function(cc) {
  return 0xDC00 <= cc && cc <= 0xDFFF;
};


/**
 * Checks if the list contains the given element.
 * @param {!ReadonlyArray<?>} list
 * @param {*} val
 * @return {boolean}
 */
const $$listContains = function(list, val) {
  return $$listIndexOf(list, val) >= 0;
};


/**
 * Returns the index of val in list or -1
 * @param {!ReadonlyArray<?>} list
 * @param {*} val
 * @param {number=} startIndex
 * @return {number}
 */
const $$listIndexOf = function(list, val, startIndex = 0) {
  const clampedStartIndex = clampArrayStartIndex(list, startIndex);
  const indexInSublist =
      googArray.findIndex(list.slice(clampedStartIndex), (el) => val === el);
  return indexInSublist === -1 ? -1 : indexInSublist + clampedStartIndex;
};

/**
 * Reverses a list and returns it. The original list passed is unaffected.
 * @param {!ReadonlyArray<T>} list
 * @return {!Array<T>}
 * @template T
 */
const $$listReverse = function(list) {
  let listCopy = [...list];
  return listCopy.reverse();
};

/**
 * A helper for Array methods that have optional startIndex
 *
 * @param {ReadonlyArray!} arr
 * @param {number} startIndex
 * @return {number}
 */
const clampArrayStartIndex = function(arr, startIndex) {
  return Math.floor(
      Math.max(0, startIndex >= 0 ? startIndex : arr.length + startIndex));
};

/**
 * Removes duplicates from a list and returns it.
 * The original list passed is unaffected.
 * @param {!ReadonlyArray<T>} list
 * @return {!Array<T>}
 * @template T
 */
const $$listUniq = function(list) {
  // Javascript preserves insertion order for set iteration, so this method
  // doesn't change the order of elements within the list.
  return [...new Set(list)];
};

/**
 * Flattens a nested list. Delegates to Array.prototype.flat.
 * @param {!ReadonlyArray} list
 * @param {number=} depth
 * @return {!Array}
 */
const $$listFlat = function(list, depth) {
  return /** @type {!Array<?>} */ (list).flat(depth);
};

/**
 * A helper function to provide tight type inference on array literals.
 * @param {...T} args
 * @return {!Array<T>}
 * @template T
 */
const $$makeArray = function(...args) {
  return args;
};

/**
 * A helper function to provide tight type inference on array loops.
 * @param {!Array<T>|!ReadonlyArray<T>} arr
 * @return {!ReadonlyArray<T>}
 * @template T
 */
const $$asReadonlyArray = function(arr) {
  return arr;
};

/**
 * A helper for list comprehension.
 * @param {!IArrayLike<T>} list
 * @param {function(T,number):boolean} filter
 * @param {function(T,number):V} map
 * @return {!Array<V>}
 * @template T, V
 */
const $$filterAndMap = function(list, filter, map) {
  let array = [];
  for (let i = 0; i < list.length; i++) {
    if (filter(list[i], i)) {
      array.push(map(list[i], i));
    }
  }
  return array;
};

/**
 * Sorts a list of numbers in numerical order.
 * @param {!IArrayLike<T>} list
 * @return {!Array<T>}
 * @template T extends number
 */
const $$numberListSort = function(list) {
  return googArray.toArray(list).sort((a, b) => a - b);
};

/**
 * Sorts a list of gbigints in numerical order.
 * @param {!IArrayLike<!gbigint>} list
 * @return {!Array<!gbigint>}
 */
const $$gbigintListSort = function(list) {
  return googArray.toArray(list).sort(compareBigInt);
};

/**
 * Sorts a list of strings in lexicographic order.
 * @param {!IArrayLike<string>} list
 * @return {!Array<string>}
 */
const $$stringListSort = function(list) {
  return googArray.toArray(list).sort();
};


/**
 * Converts the ASCII characters in the given string to lower case.
 * @param {string} s
 * @return {string}
 */
const $$strToAsciiLowerCase = function(s) {
  return googArray.map(s, $$charToAsciiLowerCase_).join('');
};

/**
 * Lowercases a single character string.
 * @return {string}
 */
const $$charToAsciiLowerCase_ = (/** string */ c) => {
  asserts.assert(c.length === 1);
  return 'A' <= c && c <= 'Z' ? c.toLowerCase() : c;
};

/**
 * Uppercases a single character string.
 * @return {string}
 */
const $$charToAsciiUpperCase_ = (/** string */ c) => {
  asserts.assert(c.length === 1);
  return 'a' <= c && c <= 'z' ? c.toUpperCase() : c;
};

/**
 * Converts the ASCII characters in the given string to upper case.
 * @param {string} s
 * @return {string}
 */
const $$strToAsciiUpperCase = function(s) {
  return googArray.map(s, $$charToAsciiUpperCase_).join('');
};


/**
 * Replaces all occurrences in s of match with token.
 * @param {string} s
 * @param {string} match
 * @param {string} token
 * @return {string}
 */
const $$strReplaceAll = function(s, match, token) {
  return googString.replaceAll(s, match, token);
};


// -----------------------------------------------------------------------------
// Bidi directives/functions.


/**
 * Cache of bidi formatter by context directionality, so we don't keep on
 * creating new objects.
 * @type {!Object<!BidiFormatter>}
 */
const bidiFormatterCache_ = {};


/**
 * Returns cached bidi formatter for bidiGlobalDir, or creates a new one.
 * @param {number} bidiGlobalDir The global directionality context: 1 if ltr, -1
 *     if rtl, 0 if unknown.
 * @return {!BidiFormatter} A formatter for bidiGlobalDir.
 */
const getBidiFormatterInstance_ = function(bidiGlobalDir) {
  return bidiFormatterCache_[bidiGlobalDir] ||
      (bidiFormatterCache_[bidiGlobalDir] = new BidiFormatter(bidiGlobalDir));
};


/**
 * Estimate the overall directionality of text. If opt_isHtml, makes sure to
 * ignore the LTR nature of the mark-up and escapes in text, making the logic
 * suitable for HTML and HTML-escaped text.
 * If text has a bidi.Dir-valued contentDir, this is used instead of
 * estimating the directionality.
 *
 * @param {?} text The content whose directionality is to be estimated.
 * @param {boolean=} isHtml Whether text is HTML/HTML-escaped.
 *     Default: false.
 * @return {!bidi.Dir} 1 if text is LTR, -1 if it is RTL, and 0 if it is
 *     neutral.
 */
const $$bidiTextDir = function(text, isHtml) {
  const contentDir = getContentDir(text);
  if (contentDir != null) {
    return contentDir;
  }
  isHtml = isHtml || $$isHtmlOrHtmlTemplate(text);
  const estimatedDir = bidi.estimateDirection(text + '', isHtml);
  cacheContentDir_(text, estimatedDir);
  return estimatedDir;
};


/**
 * Returns 'dir="ltr"' or 'dir="rtl"', depending on text's estimated
 * directionality, if it is not the same as bidiGlobalDir.
 * Otherwise, returns the empty string.
 * If opt_isHtml, makes sure to ignore the LTR nature of the mark-up and escapes
 * in text, making the logic suitable for HTML and HTML-escaped text.
 * If text has a bidi.Dir-valued contentDir, this is used instead of
 * estimating the directionality.
 *
 * @param {number} bidiGlobalDir The global directionality context: 1 if ltr, -1
 *     if rtl, 0 if unknown.
 * @param {?} text The content whose directionality is to be estimated.
 * @param {boolean=} isHtml Whether text is HTML/HTML-escaped.
 *     Default: false.
 * @return {!SanitizedHtmlAttribute} 'dir="rtl"' for RTL text in
 *     non-RTL context; 'dir="ltr"' for LTR text in non-LTR context;
 *     else, the empty string.
 */
const $$bidiDirAttr = function(bidiGlobalDir, text, isHtml) {
  const contentDir = $$bidiTextDir(text, isHtml);

  const formatter = getBidiFormatterInstance_(bidiGlobalDir);
  return VERY_UNSAFE.ordainSanitizedHtmlAttribute(
      formatter.knownDirAttr(contentDir));
};

/**
 * Returns 'ltr' or 'rtl', depending on text's estimated directionality, if it
 * is not the same as bidiGlobalDir. Otherwise, returns undefined.
 * If opt_isHtml, makes sure to ignore the LTR nature of the mark-up and escapes
 * in text, making the logic suitable for HTML and HTML-escaped text.
 * If text has a bidi.Dir-valued contentDir, this is used instead of
 * estimating the directionality.
 *
 * @param {number} bidiGlobalDir The global directionality context: 1 if ltr, -1
 *     if rtl, 0 if unknown.
 * @param {?} text The content whose directionality is to be estimated.
 * @param {boolean=} isHtml Whether text is HTML/HTML-escaped.
 *     Default: false.
 * @return {string|undefined} 'dir="rtl"' for RTL text in
 *     non-RTL context; 'dir="ltr"' for LTR text in non-LTR context;
 *     else, undefined
 */
const $$bidiDirAttrValue = function(bidiGlobalDir, text, isHtml) {
  const contentDir = $$bidiTextDir(text, isHtml);

  // We want this to behave the same as knownDirAttr(dir) in
  // goog.i18n.BidiFormatter, but without the dir= part.
  // formatter.knownDirAttrValue behaves a bit differently so we have to
  // explicitly check for where knownDirAttr would have returned an empty
  // string.
  if (contentDir === bidiGlobalDir || contentDir === bidi.Dir.NEUTRAL) {
    return undefined;
  }

  const formatter = getBidiFormatterInstance_(bidiGlobalDir);
  return formatter.knownDirAttrValue(contentDir);
};
/**
 * Returns the name of the start edge ('left' or 'right') for the current global
 * bidi directionality.
 *
 * @return {string}
 */
const $$bidiStartEdge = function(/** number */ dir) {
  return dir < 0 ? 'right' : 'left';
};

/**
 * Returns the name of the end edge ('left' or 'right') for the current global
 * bidi directionality.
 *
 * @return {string}
 */
const $$bidiEndEdge = function(/** number */ dir) {
  return dir < 0 ? 'left' : 'right';
};

/**
 * Returns a bidi mark character (LRM or RLM) for the given bidi directionality.
 *
 * @return {string}
 */
const $$bidiMark = function(/** number */ dir) {
  return dir < 0 ? '\u200F' /*RLM*/ : '\u200E' /*LRM*/;
};


/**
 * Returns a Unicode BiDi mark matching bidiGlobalDir (LRM or RLM) if the
 * directionality or the exit directionality of text are opposite to
 * bidiGlobalDir. Otherwise returns the empty string.
 * If opt_isHtml, makes sure to ignore the LTR nature of the mark-up and escapes
 * in text, making the logic suitable for HTML and HTML-escaped text.
 * If text has a bidi.Dir-valued contentDir, this is used instead of
 * estimating the directionality.
 *
 * @param {number} bidiGlobalDir The global directionality context: 1 if ltr, -1
 *     if rtl, 0 if unknown.
 * @param {?} text The content whose directionality is to be estimated.
 * @param {boolean=} isHtml Whether text is HTML/HTML-escaped.
 *     Default: false.
 * @return {string} A Unicode bidi mark matching bidiGlobalDir, or the empty
 *     string when text's overall and exit directionalities both match
 *     bidiGlobalDir, or bidiGlobalDir is 0 (unknown).
 */
const $$bidiMarkAfter = function(bidiGlobalDir, text, isHtml) {
  isHtml = isHtml || $$isHtmlOrHtmlTemplate(text);
  const dir = $$bidiTextDir(text, isHtml);
  const formatter = getBidiFormatterInstance_(bidiGlobalDir);
  return formatter.markAfterKnownDir(dir, text + '', isHtml);
};


/**
 * Returns text wrapped in a <span dir="ltr|rtl"> according to its
 * directionality - but only if that is neither neutral nor the same as the
 * global context. Otherwise, returns text unchanged.
 * Always treats text as HTML/HTML-escaped, i.e. ignores mark-up and escapes
 * when estimating text's directionality.
 * If text has a bidi.Dir-valued contentDir, this is used instead of
 * estimating the directionality.
 *
 * @param {number} bidiGlobalDir The global directionality context: 1 if ltr, -1
 *     if rtl, 0 if unknown.
 * @param {?} text The string to be wrapped. Can be other types, but the value
 *     will be coerced to a string.
 * @return {string} The wrapped text.
 */
const $$bidiSpanWrap = function(bidiGlobalDir, text) {
  const formatter = getBidiFormatterInstance_(bidiGlobalDir);

  // We always treat the value as HTML, because span-wrapping is only useful
  // when its output will be treated as HTML (without escaping), and because
  // |bidiSpanWrap is not itself specified to do HTML escaping in Soy. (Both
  // explicit and automatic HTML escaping, if any, is done before calling
  // |bidiSpanWrap because the BidiSpanWrapDirective Java class implements
  // SanitizedContentOperator, but this does not mean that the input has to be
  // HTML SanitizedContent.
  const html = htmlSafeByReview(
      String(text),
      {justification: 'Soy |bidiSpanWrap is applied on an autoescaped text.'});
  const dir = $$bidiTextDir(text, /** isHtml= */ true);
  const wrappedHtml = formatter.spanWrapSafeHtmlWithKnownDir(dir, html);

  // Like other directives whose Java class implements SanitizedContentOperator,
  // |bidiSpanWrap is called after the escaping (if any) has already been done,
  // and thus there is no need for it to produce actual SanitizedContent.
  return unwrapHtml(wrappedHtml).toString();
};


/**
 * Returns text wrapped in Unicode BiDi formatting characters according to its
 * directionality, i.e. either LRE or RLE at the beginning and PDF at the end -
 * but only if text's directionality is neither neutral nor the same as the
 * global context. Otherwise, returns text unchanged.
 * Only treats SanitizedHtml as HTML/HTML-escaped, i.e. ignores mark-up
 * and escapes when estimating text's directionality.
 * If text has a bidi.Dir-valued contentDir, this is used instead of
 * estimating the directionality.
 *
 * @param {number} bidiGlobalDir The global directionality context: 1 if ltr, -1
 *     if rtl, 0 if unknown.
 * @param {?} text The string to be wrapped. Can be other types, but the value
 *     will be coerced to a string.
 * @return {!SanitizedHtml|string} The wrapped string.
 */
const $$bidiUnicodeWrap = function(bidiGlobalDir, text) {
  const formatter = getBidiFormatterInstance_(bidiGlobalDir);

  // We treat the value as HTML if and only if it says it's HTML.
  const isHtml = $$isHtmlOrHtmlTemplate(text);
  const dir = $$bidiTextDir(text, isHtml);
  const wrappedText = formatter.unicodeWrapWithKnownDir(dir, text + '', isHtml);

  // Bidi-wrapping a value converts it to the context directionality. Since it
  // does not cost us anything, we will indicate this known direction in the
  // output SanitizedContent, even though the intended consumer of that
  // information - a bidi wrapping directive - has already been run.
  const wrappedTextDir = formatter.getContextDir();

  // Unicode-wrapping safe HTML string data gives valid, safe HTML string data.
  if (isHtml) {
    return VERY_UNSAFE.ordainSanitizedHtml(wrappedText, wrappedTextDir);
  }

  // Unicode-wrapping does not conform to the syntax of the other types of
  // content. For lack of anything better to do, we do not declare a content
  // kind at all by falling through to the non-SanitizedContent case below.
  // TODO(user): Consider throwing a runtime error on receipt of
  // SanitizedContent other than HTML.

  // The input was not SanitizedContent, so our output isn't SanitizedContent
  // either.
  return wrappedText;
};

// -----------------------------------------------------------------------------
// Assertion methods used by runtime.

/**
 * Checks if the type assertion is true if asserts.ENABLE_ASSERTS is
 * true. Report errors on runtime types if goog.DEBUG is true.
 * @param {boolean} condition The type check condition.
 * @param {string} paramName The Soy name of the parameter.
 * @param {?} param The JS object for the parameter.
 * @param {string} paramKind Whether it is a normal parameter, an injected
 *     parameter, or a state variable.
 * @param {string} jsDocTypeStr SoyDoc type str.
 * @return {?} the param value
 * @throws {!asserts.AssertionError} When the condition evaluates to false.
 */
const assertParamType = function(
    condition, paramName, param, paramKind, jsDocTypeStr) {
  if (asserts.ENABLE_ASSERTS && !condition) {
    if (goog.DEBUG) {
      asserts.fail(
          'expected ' + paramKind + ' ' + paramName + ' of type ' +
          jsDocTypeStr + ', but got ' + googDebug.runtimeType(param) + '.');
    }
    asserts.fail('parameter type error. Enable DEBUG to see details.');
  }
  return param;
};

/**
 * An object to mark internal callsites with, this should make accidentally
 * calling these things less likely.
 * @const
 * @type {!Object}
 */
const $$internalCallMarkerDoNotUse = {};

/**
 * A debug time check that our internal call sites are only called by other soy
 * templates.
 *
 * @param {?} marker
 * @return {void}
 */
const $$areYouAnInternalCaller = (marker) => {
  asserts.assert(
      marker === $$internalCallMarkerDoNotUse,
      'found an incorrect call marker, was an internal function called from the top level?');
};

// -----------------------------------------------------------------------------
// Used for inspecting Soy template information from rendered pages.

/**
 * Whether we should generate additional HTML comments.
 * @type {boolean}
 */
let $$debugSoyTemplateInfo = false;

/**
 * Configures whether we should generate additional HTML comments for
 * inspecting Soy template information from rendered pages.
 */
function setDebugSoyTemplateInfo(/** boolean */ debugSoyTemplateInfo) {
  $$debugSoyTemplateInfo = debugSoyTemplateInfo;
}

/** @return {boolean}  Whether we should generate additional debugging data */
function $$getDebugSoyTemplateInfo() {
  return $$debugSoyTemplateInfo;
}

/**
 * Best effort to freeze a data structure in DEBUG mode (to poison tests that
 * may try to mutate Soy constants in application JS code).
 *
 * @param {T} object
 * @return {T}
 * @template T
 */
function $$freeze(object) {
  if (Object.freeze) {
    if (!object || typeof object !== 'object' ||
        Object.isFrozen(/** @type {!Object} */ (object))) {
      return object;
    }
    const prototype = Object.getPrototypeOf(object);
    // Only freeze objects and literals. In particular JSPBs will break.
    if (prototype !== Object.prototype && prototype !== Array.prototype) {
      return object;
    }
    Object.freeze(object);
    Object.getOwnPropertyNames(object).forEach(function(name) {
      const property = object[name];
      $$freeze(property);
    });
  }
  return object;
}

// Whether createConst and getConst are in debug mode or not.
// Read the value into a module local so that both createConst and getConst are
// in sync.  Some tests monkey patch ENABLE_ASSERTS.
const /** boolean */ DEBUG_CONSTANTS = asserts.ENABLE_ASSERTS;

/**
 * Creates a Soy constant.  In debug mode this will return a function that
 * enforces that only internal callers access the constant, in production modes
 * it will simply return the constant.
 *
 * To access, call `$$getConst`.
 *
 * @param {function():T} valueFn
 * The return type should be T|function(!Object):T but that type is not useful
 * and function assignment rules are complex enough that it doesn't always work.
 * Our callers always use a correct type so we can just return `?` and allow the
 * caller to declare a better type.
 * @return {?}
 * @template T
 * @nosideeffects
 */
function $$createConst(valueFn) {
  let value = valueFn();
  // We need to put the if-statement here so that this still optimizes in search
  // which limits the number of optimization iterations.
  if (goog.DEBUG) {
    value = $$freeze(value);
  }
  if (DEBUG_CONSTANTS) {
    return /** @return {T} */ (/** !Object */ areYouAnInternalCaller) => {
      $$areYouAnInternalCaller(areYouAnInternalCaller);
      return value;
    };
  }
  return value;
}

/**
 * Return the value of a constant constructed by `$$createConst`
 * @param {function(!Object):T} value
 * @param {!Object} areYouAnInternalCaller
 * @return {T}
 * @template T
 */
function $$getConst(value, areYouAnInternalCaller) {
  if (DEBUG_CONSTANTS) {
    return value(areYouAnInternalCaller);
  }
  return value;
}

// TODO(b/230911572): roll this out to all environments. First tests, then
// goog.DEBUG, then production.
const /** boolean */ SOY_CREATED_PROTOS_ARE_IMMUTABLE = false;

/**
 * Conditionally returns either a default immutable instance or constructs an
 * empty proto. It is the callers responsibility to add a suitable type.
 * @return {?}
 */
function $$emptyProto(/** function(new:Message,?Array<?>=)*/ ctor) {
  if (SOY_CREATED_PROTOS_ARE_IMMUTABLE) {
    return defaultImmutableInstance(ctor);
  }
  return new ctor();
}

/**
 * Conditionally makes the parameter immutable or returns as is.
 * It is the callers responsibility to add a suitable type.
 * @return {?}
 */
function $$maybeMakeImmutableProto(/** !Message*/ message) {
  if (SOY_CREATED_PROTOS_ARE_IMMUTABLE) {
    return message.toImmutable();
  }
  return message;
}

/**
 * Checks whether a given value is of a given content kind.
 *
 * @param {?} value The value to be examined.
 * @param {!SanitizedContentKind} contentKind The desired content
 *     kind.
 * @param {?Object=} constructor
 * @return {boolean} Whether the given value is of the given kind.
 */
const isContentKind_ = function(value, contentKind, constructor) {
  const ret = value != null && value.contentKind === contentKind;
  if (ret && constructor) {
    asserts.assert(value instanceof constructor);
  }
  return ret;
};

/**
 * @param {?} value
 * @return {boolean}
 */
function $$isHtml(value) {
  return isContentKind_(value, SanitizedContentKind.HTML, SanitizedHtml);
}

/**
 * @param {?} value
 * @return {boolean}
 */
function $$isHtmlOrHtmlTemplate(value) {
  return isContentKind_(value, SanitizedContentKind.HTML);
}

/**
 * @param {?} value
 * @return {boolean}
 */
function $$isCss(value) {
  return isContentKind_(value, SanitizedContentKind.CSS, SanitizedCss);
}

/**
 * @param {?} value
 * @return {boolean}
 */
function $$isAttribute(value) {
  return isContentKind_(
      value, SanitizedContentKind.ATTRIBUTES, SanitizedHtmlAttribute);
}

/**
 * @param {?} value
 * @return {boolean}
 */
function $$isJS(value) {
  return isContentKind_(value, SanitizedContentKind.JS, SanitizedJs);
}

/**
 * @param {?} value
 * @return {boolean}
 */
function $$isTrustedResourceURI(value) {
  return isContentKind_(
      value, SanitizedContentKind.TRUSTED_RESOURCE_URI,
      SanitizedTrustedResourceUri);
}

/**
 * @param {?} value
 * @return {boolean}
 */
function $$isURI(value) {
  return isContentKind_(value, SanitizedContentKind.URI, SanitizedUri) ||
      $$isTrustedResourceURI(value);
}

/**
 * @param {?} value
 * @return {boolean}
 */
function $$isRecord(value) {
  return value && value.constructor === Object;
}

/**
 * Wrapper for JSPB's isReadonly.
 * @param {?} ctor JSPB constructor.
 * @param {?} msg JSPB instance.
 * @return {boolean}
 */
const $$isReadonly = function(ctor, msg) {
  const guard = /** @type {function(?): boolean} */ (isReadonly(ctor));
  return guard(msg);
};

/**
 * Wrapper for Function.bind.
 * @param {!Function} f
 * @param {!Array} params
 * @return {!Function}
 */
const $$bindFunctionParams =
    function(f, params) {
  return f.bind(null, ...params);
};

/** @define {boolean} Whether to do lazy execution. */
const LAZY_EXECUTION = goog.define('soy.lazyexecution', true);

/** @return {boolean} */
function isLazyExecutionEnabled() {
  if (!COMPILED) {
    return isLazyExecutionEnabledUncompiled;
  }
  return LAZY_EXECUTION;
}

let isLazyExecutionEnabledUncompiled = LAZY_EXECUTION;

/** @param {boolean} val */
function setLazyExeuctionUncompiled(val) {
  isLazyExecutionEnabledUncompiled = val;
}

/**
 * @param {function(): !SanitizedHtml} lambda
 * @return {!NodeBuilder|!SanitizedHtml}
 */
function $$createNodeBuilder(lambda) {
  return isLazyExecutionEnabled() ? new NodeBuilder(lambda) : lambda();
}

class NodeBuilder {
  /**
   * @param {function(): *} target
   */
  constructor(target) {
    this.target = target;
  }

  /** @return {*} */
  render() {
    return this.target();
  }
}

/**
 * Builds an output buffer for Soy JS.
 *
 * @param {...*} initialVal The initial value of the buffer.
 * @return {!StringAppendingHtmlOutputBuffer|!ArrayHtmlOutputBuffer}
 */
const $$createHtmlOutputBuffer = function(...initialVal) {
  return isLazyExecutionEnabled() ?
      ArrayHtmlOutputBuffer.create(...initialVal) :
      StringAppendingHtmlOutputBuffer.create(...initialVal);
};

class StringAppendingHtmlOutputBuffer extends SanitizedHtml {
  /** @param {...*} initialVal The initial value of the buffer. */
  constructor(...initialVal) {
    super();
    /** @type {string} */
    this.content = initialVal.join('');
  }

  static create(...initialVal) {
    /**
     * @param {...*} initialVal
     * @constructor
     * @extends {SanitizedHtml}
     */
    function InstantiableCtor(...initialVal) {
      /** @type {string} */
      this.content = initialVal.join('');
    }
    // Security through obfuscation hack... See $$makeSanitizedContentFactory_
    InstantiableCtor.prototype = StringAppendingHtmlOutputBuffer.prototype;
    return new InstantiableCtor(...initialVal);
  }

  /** @param {*} val */
  append(val) {
    this.content += val;
  }
}

class ArrayHtmlOutputBuffer extends SanitizedHtml {
  /** @param {...*} initialVal The initial value of the buffer. */
  constructor(...initialVal) {
    super();
    /** @type {!Array<*>} */
    this.content = initialVal;
  }

  static create(...initialVal) {
    /**
     * @param {...*} initialVal
     * @constructor
     * @extends {SanitizedContent}
     */
    function InstantiableCtor(...initialVal) {
      /** @type {!Array<*>} */
      this.content = initialVal;
    }
    // Security through obfuscation hack... See $$makeSanitizedContentFactory_
    InstantiableCtor.prototype = ArrayHtmlOutputBuffer.prototype;
    return new InstantiableCtor(...initialVal);
  }

  /** @param {*} val */
  append(val) {
    this.content.push(val);
  }

  /** @return {string} */
  render() {
    let output = '';
    for (let c of this.content) {
      if (c instanceof ArrayHtmlOutputBuffer || c instanceof NodeBuilder) {
        output += c.render();
      } else {
        output += c;
      }
    }
    return output;
  }

  /**
   * @override
   * @return {string}
   */
  toString() {
    return this.render();
  }

  /**
   * @override
   * @return {string}
   */
  getContent() {
    return this.render();
  }
}

exports = {
  $$maybeMakeImmutableProto,
  $$emptyProto,
  $$createConst,
  $$getConst,
  $$serializeKey,
  $$IS_LOCALE_RTL,
  $$asReadonlyArray,
  $$assignDefaults,
  $$getMapKeys,
  $$checkNotNull,
  $$parseInt,
  $$equals,
  $$isFunction,
  $$parseFloat,
  $$randomInt,
  $$round,
  $$strContains,
  $$coerceToBoolean,
  $$isIterable,
  $$isTruthyNonEmpty,
  $$hasContent,
  $$emptyToUndefined,
  $$makeEmptyTemplateFn,
  $$registerDelegateFn,
  $$getDelTemplateId,
  $$getDelegateFn,
  $$escapeHtml,
  $$cleanHtml,
  $$htmlToText,
  $$normalizeHtml,
  $$escapeHtmlRcdata,
  $$stripHtmlTags,
  $$escapeHtmlAttribute,
  $$escapeHtmlHtmlAttribute,
  $$escapeHtmlAttributeNospace,
  $$filterHtmlScriptPhrasingData,
  $$filterHtmlAttributes,
  $$whitespaceHtmlAttributes,
  $$filterNumber,
  $$filterHtmlElementName,
  $$escapeJsString,
  $$escapeJsValue,
  $$escapeJsRegex,
  $$escapeUri,
  $$normalizeUri,
  $$filterNormalizeUri,
  $$filterNormalizeMediaUri,
  $$filterNormalizeRefreshUri,
  $$filterTrustedResourceUri,
  $$filterImageDataUri,
  $$filterSipUri,
  $$strSmsUriToUri,
  $$filterTelUri,
  $$escapeCssString,
  $$filterCssValue,
  $$filterCspNonceValue,
  $$changeNewlineToBr,
  $$insertWordBreaks,
  $$concatAttributeValues,
  $$concatCssValues,
  $$buildAttr,
  $$buildAttrValue,
  $$buildClassValue,
  $$buildStyleValue,
  $$truncate,
  $$listContains,
  $$listIndexOf,
  $$listReverse,
  $$listUniq,
  $$listFlat,
  $$makeArray,
  $$filterAndMap,
  $$numberListSort,
  $$gbigintListSort,
  $$stringListSort,
  $$strToAsciiLowerCase,
  $$strToAsciiUpperCase,
  $$strReplaceAll,
  $$bidiDirAttr,
  $$bidiDirAttrValue,
  $$bidiTextDir,
  $$bidiStartEdge,
  $$bidiEndEdge,
  $$bidiMark,
  $$bidiMarkAfter,
  $$bidiSpanWrap,
  $$bidiUnicodeWrap,
  assertParamType,
  setDebugSoyTemplateInfo,
  $$getDebugSoyTemplateInfo,
  $$EMPTY_STRING_,
  getContentDir,
  VERY_UNSAFE,
  IdomFunction,
  createSanitizedHtml,
  $$stubsMap,
  $$internalCallMarkerDoNotUse,
  $$areYouAnInternalCaller,
  $$bindFunctionParams,
  $$createNodeBuilder,
  $$createHtmlOutputBuffer,
  // The following are exported just for tests
  setLazyExeuctionUncompiled,
  $$balanceTags_,
  $$isRecord,
  $$isHtml,
  $$isURI,
  $$isTrustedResourceURI,
  $$isCss,
  $$isJS,
  $$isAttribute,
  $$isReadonly,
};
// -----------------------------------------------------------------------------
// Generated code.



// START GENERATED CODE FOR ESCAPERS.

/**
 * @type {function (*) : string}
 */
const $$escapeUriHelper = function(v) {
  return encodeURIComponent(String(v));
};

/**
 * Maps characters to the escaped versions for the named escape directives.
 * @type {!Object<string, string>}
 */
const $$ESCAPE_MAP_FOR_ESCAPE_HTML__AND__NORMALIZE_HTML__AND__ESCAPE_HTML_NOSPACE__AND__NORMALIZE_HTML_NOSPACE_ = {
  '\x00': '\x26#0;',
  '\x09': '\x26#9;',
  '\x0a': '\x26#10;',
  '\x0b': '\x26#11;',
  '\x0c': '\x26#12;',
  '\x0d': '\x26#13;',
  ' ': '\x26#32;',
  '\x22': '\x26quot;',
  '\x26': '\x26amp;',
  '\x27': '\x26#39;',
  '-': '\x26#45;',
  '\/': '\x26#47;',
  '\x3c': '\x26lt;',
  '\x3d': '\x26#61;',
  '\x3e': '\x26gt;',
  '`': '\x26#96;',
  '\x85': '\x26#133;',
  '\xa0': '\x26#160;',
  '\u2028': '\x26#8232;',
  '\u2029': '\x26#8233;',
};

/**
 * A function that can be used with String.replace.
 * @param {string} ch A single character matched by a compatible matcher.
 * @return {string} A token in the output language.
 */
const $$REPLACER_FOR_ESCAPE_HTML__AND__NORMALIZE_HTML__AND__ESCAPE_HTML_NOSPACE__AND__NORMALIZE_HTML_NOSPACE_ = function(ch) {
  return $$ESCAPE_MAP_FOR_ESCAPE_HTML__AND__NORMALIZE_HTML__AND__ESCAPE_HTML_NOSPACE__AND__NORMALIZE_HTML_NOSPACE_[ch];
};

/**
 * Maps characters to the escaped versions for the named escape directives.
 * @type {!Object<string, string>}
 */
const $$ESCAPE_MAP_FOR_ESCAPE_JS_STRING__AND__ESCAPE_JS_REGEX_ = {
  '\x00': '\\x00',
  '\x08': '\\x08',
  '\x09': '\\t',
  '\x0a': '\\n',
  '\x0b': '\\x0b',
  '\x0c': '\\f',
  '\x0d': '\\r',
  '\x22': '\\x22',
  '$': '\\x24',
  '\x26': '\\x26',
  '\x27': '\\x27',
  '(': '\\x28',
  ')': '\\x29',
  '*': '\\x2a',
  '+': '\\x2b',
  ',': '\\x2c',
  '-': '\\x2d',
  '.': '\\x2e',
  '\/': '\\\/',
  ':': '\\x3a',
  '\x3c': '\\x3c',
  '\x3d': '\\x3d',
  '\x3e': '\\x3e',
  '?': '\\x3f',
  '\x5b': '\\x5b',
  '\\': '\\\\',
  '\x5d': '\\x5d',
  '^': '\\x5e',
  '\x7b': '\\x7b',
  '|': '\\x7c',
  '\x7d': '\\x7d',
  '\x85': '\\x85',
  '\u2028': '\\u2028',
  '\u2029': '\\u2029',
};

/**
 * A function that can be used with String.replace.
 * @param {string} ch A single character matched by a compatible matcher.
 * @return {string} A token in the output language.
 */
const $$REPLACER_FOR_ESCAPE_JS_STRING__AND__ESCAPE_JS_REGEX_ = function(ch) {
  return $$ESCAPE_MAP_FOR_ESCAPE_JS_STRING__AND__ESCAPE_JS_REGEX_[ch];
};

/**
 * Maps characters to the escaped versions for the named escape directives.
 * @type {!Object<string, string>}
 */
const $$ESCAPE_MAP_FOR_ESCAPE_CSS_STRING_ = {
  '\x00': '\\0 ',
  '\x08': '\\8 ',
  '\x09': '\\9 ',
  '\x0a': '\\a ',
  '\x0b': '\\b ',
  '\x0c': '\\c ',
  '\x0d': '\\d ',
  '\x22': '\\22 ',
  '\x26': '\\26 ',
  '\x27': '\\27 ',
  '(': '\\28 ',
  ')': '\\29 ',
  '*': '\\2a ',
  '\/': '\\2f ',
  ':': '\\3a ',
  ';': '\\3b ',
  '\x3c': '\\3c ',
  '\x3d': '\\3d ',
  '\x3e': '\\3e ',
  '@': '\\40 ',
  '\\': '\\5c ',
  '\x7b': '\\7b ',
  '\x7d': '\\7d ',
  '\x85': '\\85 ',
  '\xa0': '\\a0 ',
  '\u2028': '\\2028 ',
  '\u2029': '\\2029 ',
};

/**
 * A function that can be used with String.replace.
 * @param {string} ch A single character matched by a compatible matcher.
 * @return {string} A token in the output language.
 */
const $$REPLACER_FOR_ESCAPE_CSS_STRING_ = function(ch) {
  return $$ESCAPE_MAP_FOR_ESCAPE_CSS_STRING_[ch];
};

/**
 * Maps characters to the escaped versions for the named escape directives.
 * @type {!Object<string, string>}
 */
const $$ESCAPE_MAP_FOR_NORMALIZE_URI__AND__FILTER_NORMALIZE_URI__AND__FILTER_NORMALIZE_MEDIA_URI_ = {
  '\x00': '%00',
  '\x01': '%01',
  '\x02': '%02',
  '\x03': '%03',
  '\x04': '%04',
  '\x05': '%05',
  '\x06': '%06',
  '\x07': '%07',
  '\x08': '%08',
  '\x09': '%09',
  '\x0a': '%0A',
  '\x0b': '%0B',
  '\x0c': '%0C',
  '\x0d': '%0D',
  '\x0e': '%0E',
  '\x0f': '%0F',
  '\x10': '%10',
  '\x11': '%11',
  '\x12': '%12',
  '\x13': '%13',
  '\x14': '%14',
  '\x15': '%15',
  '\x16': '%16',
  '\x17': '%17',
  '\x18': '%18',
  '\x19': '%19',
  '\x1a': '%1A',
  '\x1b': '%1B',
  '\x1c': '%1C',
  '\x1d': '%1D',
  '\x1e': '%1E',
  '\x1f': '%1F',
  ' ': '%20',
  '\x22': '%22',
  '\x27': '%27',
  '(': '%28',
  ')': '%29',
  '\x3c': '%3C',
  '\x3e': '%3E',
  '\\': '%5C',
  '\x7b': '%7B',
  '\x7d': '%7D',
  '\x7f': '%7F',
  '\x85': '%C2%85',
  '\xa0': '%C2%A0',
  '\u2028': '%E2%80%A8',
  '\u2029': '%E2%80%A9',
  '\uff01': '%EF%BC%81',
  '\uff03': '%EF%BC%83',
  '\uff04': '%EF%BC%84',
  '\uff06': '%EF%BC%86',
  '\uff07': '%EF%BC%87',
  '\uff08': '%EF%BC%88',
  '\uff09': '%EF%BC%89',
  '\uff0a': '%EF%BC%8A',
  '\uff0b': '%EF%BC%8B',
  '\uff0c': '%EF%BC%8C',
  '\uff0f': '%EF%BC%8F',
  '\uff1a': '%EF%BC%9A',
  '\uff1b': '%EF%BC%9B',
  '\uff1d': '%EF%BC%9D',
  '\uff1f': '%EF%BC%9F',
  '\uff20': '%EF%BC%A0',
  '\uff3b': '%EF%BC%BB',
  '\uff3d': '%EF%BC%BD',
};

/**
 * A function that can be used with String.replace.
 * @param {string} ch A single character matched by a compatible matcher.
 * @return {string} A token in the output language.
 */
const $$REPLACER_FOR_NORMALIZE_URI__AND__FILTER_NORMALIZE_URI__AND__FILTER_NORMALIZE_MEDIA_URI_ = function(ch) {
  return $$ESCAPE_MAP_FOR_NORMALIZE_URI__AND__FILTER_NORMALIZE_URI__AND__FILTER_NORMALIZE_MEDIA_URI_[ch];
};

/**
 * Matches characters that need to be escaped for the named directives.
 * @type {!RegExp}
 */
const $$MATCHER_FOR_ESCAPE_HTML_ = /[\x00\x22\x26\x27\x3c\x3e]/g;

/**
 * Matches characters that need to be escaped for the named directives.
 * @type {!RegExp}
 */
const $$MATCHER_FOR_NORMALIZE_HTML_ = /[\x00\x22\x27\x3c\x3e]/g;

/**
 * Matches characters that need to be escaped for the named directives.
 * @type {!RegExp}
 */
const $$MATCHER_FOR_ESCAPE_HTML_NOSPACE_ = /[\x00\x09-\x0d \x22\x26\x27\x2d\/\x3c-\x3e`\x85\xa0\u2028\u2029]/g;

/**
 * Matches characters that need to be escaped for the named directives.
 * @type {!RegExp}
 */
const $$MATCHER_FOR_NORMALIZE_HTML_NOSPACE_ = /[\x00\x09-\x0d \x22\x27\x2d\/\x3c-\x3e`\x85\xa0\u2028\u2029]/g;

/**
 * Matches characters that need to be escaped for the named directives.
 * @type {!RegExp}
 */
const $$MATCHER_FOR_ESCAPE_JS_STRING_ = /[\x00\x08-\x0d\x22\x26\x27\/\x3c-\x3e\x5b-\x5d\x7b\x7d\x85\u2028\u2029]/g;

/**
 * Matches characters that need to be escaped for the named directives.
 * @type {!RegExp}
 */
const $$MATCHER_FOR_ESCAPE_JS_REGEX_ = /[\x00\x08-\x0d\x22\x24\x26-\/\x3a\x3c-\x3f\x5b-\x5e\x7b-\x7d\x85\u2028\u2029]/g;

/**
 * Matches characters that need to be escaped for the named directives.
 * @type {!RegExp}
 */
const $$MATCHER_FOR_ESCAPE_CSS_STRING_ = /[\x00\x08-\x0d\x22\x26-\x2a\/\x3a-\x3e@\\\x7b\x7d\x85\xa0\u2028\u2029]/g;

/**
 * Matches characters that need to be escaped for the named directives.
 * @type {!RegExp}
 */
const $$MATCHER_FOR_NORMALIZE_URI__AND__FILTER_NORMALIZE_URI__AND__FILTER_NORMALIZE_MEDIA_URI_ = /[\x00- \x22\x27-\x29\x3c\x3e\\\x7b\x7d\x7f\x85\xa0\u2028\u2029\uff01\uff03\uff04\uff06-\uff0c\uff0f\uff1a\uff1b\uff1d\uff1f\uff20\uff3b\uff3d]/g;

/**
 * A pattern that vets values produced by the named directives.
 * @type {!RegExp}
 */
const $$FILTER_FOR_FILTER_NORMALIZE_URI_ = /^(?!javascript:)(?:[a-z0-9+.-]+:|[^&:\/?#]*(?:[\/?#]|$))/i;

/**
 * A pattern that vets values produced by the named directives.
 * @type {!RegExp}
 */
const $$FILTER_FOR_FILTER_NORMALIZE_MEDIA_URI_ = /^[^&:\/?#]*(?:[\/?#]|$)|^https?:|^ftp:|^data:image\/[a-z0-9+-]+;base64,[a-z0-9+\/]+=*$|^blob:/i;

/**
 * A pattern that vets values produced by the named directives.
 * @type {!RegExp}
 */
const $$FILTER_FOR_FILTER_IMAGE_DATA_URI_ = /^data:image\/(?:bmp|gif|jpe?g|png|tiff|webp|x-icon);base64,[a-z0-9+\/]+=*$/i;

/**
 * A pattern that vets values produced by the named directives.
 * @type {!RegExp}
 */
const $$FILTER_FOR_FILTER_SIP_URI_ = /^sip:[0-9a-z;=\-+._!~*'\u0020\/():&$#?@,]+$/i;

/**
 * A pattern that vets values produced by the named directives.
 * @type {!RegExp}
 */
const $$FILTER_FOR_FILTER_SMS_URI_ = /^sms:[0-9a-z;=\-+._!~*'\u0020\/():&$#?@,]+$/i;

/**
 * A pattern that vets values produced by the named directives.
 * @type {!RegExp}
 */
const $$FILTER_FOR_FILTER_TEL_URI_ = /^tel:(?:[0-9a-z;=\-+._!~*'\u0020\/():&$#?@,]|%23|%2C|%3B)+$/i;

/**
 * A pattern that vets values produced by the named directives.
 * @type {!RegExp}
 */
const $$FILTER_FOR_FILTER_HTML_ATTRIBUTES_ = /^(?!on|src|(?:action|archive|background|cite|classid|codebase|content|data|dsync|href|http-equiv|longdesc|style|usemap)\s*$)(?:[a-z0-9_$:-]*)$/i;

/**
 * A pattern that vets values produced by the named directives.
 * @type {!RegExp}
 */
const $$FILTER_FOR_FILTER_HTML_ELEMENT_NAME_ = /^(?!base|iframe|link|noframes|noscript|object|script|style|textarea|title|xmp)[a-z0-9_$:-]*$/i;

/**
 * A pattern that vets values produced by the named directives.
 * @type {!RegExp}
 */
const $$FILTER_FOR_FILTER_CSP_NONCE_VALUE_ = /^[a-zA-Z0-9+\/_-]+={0,2}$/;

/**
 * A helper for the Soy directive |escapeHtml
 * @param {?} value Can be of any type but will be coerced to a string.
 * @return {string} The escaped text.
 */
const $$escapeHtmlHelper = function(value) {
  const str = String(value);
  return str.replace(
      $$MATCHER_FOR_ESCAPE_HTML_,
      $$REPLACER_FOR_ESCAPE_HTML__AND__NORMALIZE_HTML__AND__ESCAPE_HTML_NOSPACE__AND__NORMALIZE_HTML_NOSPACE_);
};

/**
 * A helper for the Soy directive |normalizeHtml
 * @param {?} value Can be of any type but will be coerced to a string.
 * @return {string} The escaped text.
 */
const $$normalizeHtmlHelper = function(value) {
  const str = String(value);
  return str.replace(
      $$MATCHER_FOR_NORMALIZE_HTML_,
      $$REPLACER_FOR_ESCAPE_HTML__AND__NORMALIZE_HTML__AND__ESCAPE_HTML_NOSPACE__AND__NORMALIZE_HTML_NOSPACE_);
};

/**
 * A helper for the Soy directive |escapeHtmlNospace
 * @param {?} value Can be of any type but will be coerced to a string.
 * @return {string} The escaped text.
 */
const $$escapeHtmlNospaceHelper = function(value) {
  const str = String(value);
  return str.replace(
      $$MATCHER_FOR_ESCAPE_HTML_NOSPACE_,
      $$REPLACER_FOR_ESCAPE_HTML__AND__NORMALIZE_HTML__AND__ESCAPE_HTML_NOSPACE__AND__NORMALIZE_HTML_NOSPACE_);
};

/**
 * A helper for the Soy directive |normalizeHtmlNospace
 * @param {?} value Can be of any type but will be coerced to a string.
 * @return {string} The escaped text.
 */
const $$normalizeHtmlNospaceHelper = function(value) {
  const str = String(value);
  return str.replace(
      $$MATCHER_FOR_NORMALIZE_HTML_NOSPACE_,
      $$REPLACER_FOR_ESCAPE_HTML__AND__NORMALIZE_HTML__AND__ESCAPE_HTML_NOSPACE__AND__NORMALIZE_HTML_NOSPACE_);
};

/**
 * A helper for the Soy directive |escapeJsString
 * @param {?} value Can be of any type but will be coerced to a string.
 * @return {string} The escaped text.
 */
const $$escapeJsStringHelper = function(value) {
  const str = String(value);
  return str.replace(
      $$MATCHER_FOR_ESCAPE_JS_STRING_,
      $$REPLACER_FOR_ESCAPE_JS_STRING__AND__ESCAPE_JS_REGEX_);
};

/**
 * A helper for the Soy directive |escapeJsRegex
 * @param {?} value Can be of any type but will be coerced to a string.
 * @return {string} The escaped text.
 */
const $$escapeJsRegexHelper = function(value) {
  const str = String(value);
  return str.replace(
      $$MATCHER_FOR_ESCAPE_JS_REGEX_,
      $$REPLACER_FOR_ESCAPE_JS_STRING__AND__ESCAPE_JS_REGEX_);
};

/**
 * A helper for the Soy directive |escapeCssString
 * @param {?} value Can be of any type but will be coerced to a string.
 * @return {string} The escaped text.
 */
const $$escapeCssStringHelper = function(value) {
  const str = String(value);
  return str.replace(
      $$MATCHER_FOR_ESCAPE_CSS_STRING_,
      $$REPLACER_FOR_ESCAPE_CSS_STRING_);
};

/**
 * A helper for the Soy directive |normalizeUri
 * @param {?} value Can be of any type but will be coerced to a string.
 * @return {string} The escaped text.
 */
const $$normalizeUriHelper = function(value) {
  const str = String(value);
  return str.replace(
      $$MATCHER_FOR_NORMALIZE_URI__AND__FILTER_NORMALIZE_URI__AND__FILTER_NORMALIZE_MEDIA_URI_,
      $$REPLACER_FOR_NORMALIZE_URI__AND__FILTER_NORMALIZE_URI__AND__FILTER_NORMALIZE_MEDIA_URI_);
};

/**
 * A helper for the Soy directive |filterNormalizeUri
 * @param {?} value Can be of any type but will be coerced to a string.
 * @return {string} The escaped text.
 */
const $$filterNormalizeUriHelper = function(value) {
  const str = String(value);
  if (!$$FILTER_FOR_FILTER_NORMALIZE_URI_.test(str)) {
    asserts.fail('Bad value `%s` for |filterNormalizeUri', [str]);
    return 'about:invalid#zSoyz';
  }
  return str.replace(
      $$MATCHER_FOR_NORMALIZE_URI__AND__FILTER_NORMALIZE_URI__AND__FILTER_NORMALIZE_MEDIA_URI_,
      $$REPLACER_FOR_NORMALIZE_URI__AND__FILTER_NORMALIZE_URI__AND__FILTER_NORMALIZE_MEDIA_URI_);
};

/**
 * A helper for the Soy directive |filterNormalizeMediaUri
 * @param {?} value Can be of any type but will be coerced to a string.
 * @return {string} The escaped text.
 */
const $$filterNormalizeMediaUriHelper = function(value) {
  const str = String(value);
  if (!$$FILTER_FOR_FILTER_NORMALIZE_MEDIA_URI_.test(str)) {
    asserts.fail('Bad value `%s` for |filterNormalizeMediaUri', [str]);
    return 'about:invalid#zSoyz';
  }
  return str.replace(
      $$MATCHER_FOR_NORMALIZE_URI__AND__FILTER_NORMALIZE_URI__AND__FILTER_NORMALIZE_MEDIA_URI_,
      $$REPLACER_FOR_NORMALIZE_URI__AND__FILTER_NORMALIZE_URI__AND__FILTER_NORMALIZE_MEDIA_URI_);
};

/**
 * A helper for the Soy directive |filterImageDataUri
 * @param {?} value Can be of any type but will be coerced to a string.
 * @return {string} The escaped text.
 */
const $$filterImageDataUriHelper = function(value) {
  const str = String(value);
  if (!$$FILTER_FOR_FILTER_IMAGE_DATA_URI_.test(str)) {
    asserts.fail('Bad value `%s` for |filterImageDataUri', [str]);
    return 'data:image/gif;base64,zSoyz';
  }
  return str;
};

/**
 * A helper for the Soy directive |filterSipUri
 * @param {?} value Can be of any type but will be coerced to a string.
 * @return {string} The escaped text.
 */
const $$filterSipUriHelper = function(value) {
  const str = String(value);
  if (!$$FILTER_FOR_FILTER_SIP_URI_.test(str)) {
    asserts.fail('Bad value `%s` for |filterSipUri', [str]);
    return 'about:invalid#zSoyz';
  }
  return str;
};

/**
 * A helper for the Soy directive |filterSmsUri
 * @param {?} value Can be of any type but will be coerced to a string.
 * @return {string} The escaped text.
 */
const $$filterSmsUriHelper = function(value) {
  const str = String(value);
  if (!$$FILTER_FOR_FILTER_SMS_URI_.test(str)) {
    asserts.fail('Bad value `%s` for |filterSmsUri', [str]);
    return 'about:invalid#zSoyz';
  }
  return str;
};

/**
 * A helper for the Soy directive |filterTelUri
 * @param {?} value Can be of any type but will be coerced to a string.
 * @return {string} The escaped text.
 */
const $$filterTelUriHelper = function(value) {
  const str = String(value);
  if (!$$FILTER_FOR_FILTER_TEL_URI_.test(str)) {
    asserts.fail('Bad value `%s` for |filterTelUri', [str]);
    return 'about:invalid#zSoyz';
  }
  return str;
};

/**
 * A helper for the Soy directive |filterHtmlAttributes
 * @param {?} value Can be of any type but will be coerced to a string.
 * @return {string} The escaped text.
 */
const $$filterHtmlAttributesHelper = function(value) {
  const str = String(value);
  if (!$$FILTER_FOR_FILTER_HTML_ATTRIBUTES_.test(str)) {
    asserts.fail('Bad value `%s` for |filterHtmlAttributes', [str]);
    return 'zSoyz';
  }
  return str;
};

/**
 * A helper for the Soy directive |filterHtmlElementName
 * @param {?} value Can be of any type but will be coerced to a string.
 * @return {string} The escaped text.
 */
const $$filterHtmlElementNameHelper = function(value) {
  const str = String(value);
  if (!$$FILTER_FOR_FILTER_HTML_ELEMENT_NAME_.test(str)) {
    asserts.fail('Bad value `%s` for |filterHtmlElementName', [str]);
    return 'zSoyz';
  }
  return str;
};

/**
 * A helper for the Soy directive |filterCspNonceValue
 * @param {?} value Can be of any type but will be coerced to a string.
 * @return {string} The escaped text.
 */
const $$filterCspNonceValueHelper = function(value) {
  const str = String(value);
  if (!$$FILTER_FOR_FILTER_CSP_NONCE_VALUE_.test(str)) {
    asserts.fail('Bad value `%s` for |filterCspNonceValue', [str]);
    return 'zSoyz';
  }
  return str;
};

/**
 * Matches all occurrences of '<'.
 *
 * @type {!RegExp}
 */
const $$LT_REGEX_ = /</g;

/**
 * Maps lower-case names of innocuous tags to true.
 *
 * @type {!Object<string, boolean>}
 */
const $$SAFE_TAG_WHITELIST_ = {'b': true, 'br': true, 'em': true, 'i': true, 's': true, 'strong': true, 'sub': true, 'sup': true, 'u': true};

/**
 * Pattern for matching attribute name and value, where value is single-quoted
 * or double-quoted.
 * See http://www.w3.org/TR/2011/WD-html5-20110525/syntax.html#attributes-0
 *
 * @type {!RegExp}
 */
const $$HTML_ATTRIBUTE_REGEX_ = /([a-zA-Z][a-zA-Z0-9:\-]*)[\t\n\r\u0020]*=[\t\n\r\u0020]*("[^"]*"|'[^']*')/g;

// END GENERATED CODE
