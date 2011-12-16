//javascript/closure/base.js
// Copyright 2006 The Closure Library Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @fileoverview Bootstrap for the Google JS Library (Closure).
 *
 * In uncompiled mode base.js will write out Closure's deps file, unless the
 * global <code>CLOSURE_NO_DEPS</code> is set to true.  This allows projects to
 * include their own deps file(s) from different locations.
 *
 */


/**
 * @define {boolean} Overridden to true by the compiler when --closure_pass
 *     or --mark_as_compiled is specified.
 */
var COMPILED = false;


/**
 * Base namespace for the Closure library.  Checks to see goog is
 * already defined in the current scope before assigning to prevent
 * clobbering if base.js is loaded more than once.
 *
 * @const
 */
var goog = goog || {}; // Identifies this file as the Closure base.


/**
 * Reference to the global context.  In most cases this will be 'window'.
 */
goog.global = this;


/**
 * @define {boolean} DEBUG is provided as a convenience so that debugging code
 * that should not be included in a production js_binary can be easily stripped
 * by specifying --define goog.DEBUG=false to the JSCompiler. For example, most
 * toString() methods should be declared inside an "if (goog.DEBUG)" conditional
 * because they are generally used for debugging purposes and it is difficult
 * for the JSCompiler to statically determine whether they are used.
 */
goog.DEBUG = true;


/**
 * @define {string} LOCALE defines the locale being used for compilation. It is
 * used to select locale specific data to be compiled in js binary. BUILD rule
 * can specify this value by "--define goog.LOCALE=<locale_name>" as JSCompiler
 * option.
 *
 * Take into account that the locale code format is important. You should use
 * the canonical Unicode format with hyphen as a delimiter. Language must be
 * lowercase, Language Script - Capitalized, Region - UPPERCASE.
 * There are few examples: pt-BR, en, en-US, sr-Latin-BO, zh-Hans-CN.
 *
 * See more info about locale codes here:
 * http://www.unicode.org/reports/tr35/#Unicode_Language_and_Locale_Identifiers
 *
 * For language codes you should use values defined by ISO 693-1. See it here
 * http://www.w3.org/WAI/ER/IG/ert/iso639.htm. There is only one exception from
 * this rule: the Hebrew language. For legacy reasons the old code (iw) should
 * be used instead of the new code (he), see http://wiki/Main/IIISynonyms.
 */
goog.LOCALE = 'en';  // default to en


/**
 * Creates object stubs for a namespace.  The presence of one or more
 * goog.provide() calls indicate that the file defines the given
 * objects/namespaces.  Build tools also scan for provide/require statements
 * to discern dependencies, build dependency files (see deps.js), etc.
 * @see goog.require
 * @param {string} name Namespace provided by this file in the form
 *     "goog.package.part".
 */
goog.provide = function(name) {
  if (!COMPILED) {
    // Ensure that the same namespace isn't provided twice. This is intended
    // to teach new developers that 'goog.provide' is effectively a variable
    // declaration. And when JSCompiler transforms goog.provide into a real
    // variable declaration, the compiled JS should work the same as the raw
    // JS--even when the raw JS uses goog.provide incorrectly.
    if (goog.isProvided_(name)) {
      throw Error('Namespace "' + name + '" already declared.');
    }
    delete goog.implicitNamespaces_[name];

    var namespace = name;
    while ((namespace = namespace.substring(0, namespace.lastIndexOf('.')))) {
      if (goog.getObjectByName(namespace)) {
        break;
      }
      goog.implicitNamespaces_[namespace] = true;
    }
  }

  goog.exportPath_(name);
};


/**
 * Marks that the current file should only be used for testing, and never for
 * live code in production.
 * @param {string=} opt_message Optional message to add to the error that's
 *     raised when used in production code.
 */
goog.setTestOnly = function(opt_message) {
  if (COMPILED && !goog.DEBUG) {
    opt_message = opt_message || '';
    throw Error('Importing test-only code into non-debug environment' +
                opt_message ? ': ' + opt_message : '.');
  }
};


if (!COMPILED) {

  /**
   * Check if the given name has been goog.provided. This will return false for
   * names that are available only as implicit namespaces.
   * @param {string} name name of the object to look for.
   * @return {boolean} Whether the name has been provided.
   * @private
   */
  goog.isProvided_ = function(name) {
    return !goog.implicitNamespaces_[name] && !!goog.getObjectByName(name);
  };

  /**
   * Namespaces implicitly defined by goog.provide. For example,
   * goog.provide('goog.events.Event') implicitly declares
   * that 'goog' and 'goog.events' must be namespaces.
   *
   * @type {Object}
   * @private
   */
  goog.implicitNamespaces_ = {};
}


/**
 * Builds an object structure for the provided namespace path,
 * ensuring that names that already exist are not overwritten. For
 * example:
 * "a.b.c" -> a = {};a.b={};a.b.c={};
 * Used by goog.provide and goog.exportSymbol.
 * @param {string} name name of the object that this file defines.
 * @param {*=} opt_object the object to expose at the end of the path.
 * @param {Object=} opt_objectToExportTo The object to add the path to; default
 *     is |goog.global|.
 * @private
 */
goog.exportPath_ = function(name, opt_object, opt_objectToExportTo) {
  var parts = name.split('.');
  var cur = opt_objectToExportTo || goog.global;

  // Internet Explorer exhibits strange behavior when throwing errors from
  // methods externed in this manner.  See the testExportSymbolExceptions in
  // base_test.html for an example.
  if (!(parts[0] in cur) && cur.execScript) {
    cur.execScript('var ' + parts[0]);
  }

  // Certain browsers cannot parse code in the form for((a in b); c;);
  // This pattern is produced by the JSCompiler when it collapses the
  // statement above into the conditional loop below. To prevent this from
  // happening, use a for-loop and reserve the init logic as below.

  // Parentheses added to eliminate strict JS warning in Firefox.
  for (var part; parts.length && (part = parts.shift());) {
    if (!parts.length && goog.isDef(opt_object)) {
      // last part and we have an object; use it
      cur[part] = opt_object;
    } else if (cur[part]) {
      cur = cur[part];
    } else {
      cur = cur[part] = {};
    }
  }
};


/**
 * Returns an object based on its fully qualified external name.  If you are
 * using a compilation pass that renames property names beware that using this
 * function will not find renamed properties.
 *
 * @param {string} name The fully qualified name.
 * @param {Object=} opt_obj The object within which to look; default is
 *     |goog.global|.
 * @return {?} The value (object or primitive) or, if not found, null.
 */
goog.getObjectByName = function(name, opt_obj) {
  var parts = name.split('.');
  var cur = opt_obj || goog.global;
  for (var part; part = parts.shift(); ) {
    if (goog.isDefAndNotNull(cur[part])) {
      cur = cur[part];
    } else {
      return null;
    }
  }
  return cur;
};


/**
 * Globalizes a whole namespace, such as goog or goog.lang.
 *
 * @param {Object} obj The namespace to globalize.
 * @param {Object=} opt_global The object to add the properties to.
 * @deprecated Properties may be explicitly exported to the global scope, but
 *     this should no longer be done in bulk.
 */
goog.globalize = function(obj, opt_global) {
  var global = opt_global || goog.global;
  for (var x in obj) {
    global[x] = obj[x];
  }
};


/**
 * Adds a dependency from a file to the files it requires.
 * @param {string} relPath The path to the js file.
 * @param {Array} provides An array of strings with the names of the objects
 *                         this file provides.
 * @param {Array} requires An array of strings with the names of the objects
 *                         this file requires.
 */
goog.addDependency = function(relPath, provides, requires) {
  if (!COMPILED) {
    var provide, require;
    var path = relPath.replace(/\\/g, '/');
    var deps = goog.dependencies_;
    for (var i = 0; provide = provides[i]; i++) {
      deps.nameToPath[provide] = path;
      if (!(path in deps.pathToNames)) {
        deps.pathToNames[path] = {};
      }
      deps.pathToNames[path][provide] = true;
    }
    for (var j = 0; require = requires[j]; j++) {
      if (!(path in deps.requires)) {
        deps.requires[path] = {};
      }
      deps.requires[path][require] = true;
    }
  }
};




// NOTE(user): The debug DOM loader was included in base.js as an orignal
// way to do "debug-mode" development.  The dependency system can sometimes
// be confusing, as can the debug DOM loader's asyncronous nature.
//
// With the DOM loader, a call to goog.require() is not blocking -- the
// script will not load until some point after the current script.  If a
// namespace is needed at runtime, it needs to be defined in a previous
// script, or loaded via require() with its registered dependencies.
// User-defined namespaces may need their own deps file.  See http://go/js_deps,
// http://go/genjsdeps, or, externally, DepsWriter.
// http://code.google.com/closure/library/docs/depswriter.html
//
// Because of legacy clients, the DOM loader can't be easily removed from
// base.js.  Work is being done to make it disableable or replaceable for
// different environments (DOM-less JavaScript interpreters like Rhino or V8,
// for example). See bootstrap/ for more information.


/**
 * @define {boolean} Whether to enable the debug loader.
 *
 * If enabled, a call to goog.require() will attempt to load the namespace by
 * appending a script tag to the DOM (if the namespace has been registered).
 *
 * If disabled, goog.require() will simply assert that the namespace has been
 * provided (and depend on the fact that some outside tool correctly ordered
 * the script).
 */
goog.ENABLE_DEBUG_LOADER = true;


/**
 * Implements a system for the dynamic resolution of dependencies
 * that works in parallel with the BUILD system. Note that all calls
 * to goog.require will be stripped by the JSCompiler when the
 * --closure_pass option is used.
 * @see goog.provide
 * @param {string} name Namespace to include (as was given in goog.provide())
 *     in the form "goog.package.part".
 */
goog.require = function(name) {

  // if the object already exists we do not need do do anything
  // TODO(arv): If we start to support require based on file name this has
  //            to change
  // TODO(arv): If we allow goog.foo.* this has to change
  // TODO(arv): If we implement dynamic load after page load we should probably
  //            not remove this code for the compiled output
  if (!COMPILED) {
    if (goog.isProvided_(name)) {
      return;
    }

    if (goog.ENABLE_DEBUG_LOADER) {
      var path = goog.getPathFromDeps_(name);
      if (path) {
        goog.included_[path] = true;
        goog.writeScripts_();
        return;
      }
    }

    var errorMessage = 'goog.require could not find: ' + name;
    if (goog.global.console) {
      goog.global.console['error'](errorMessage);
    }


      throw Error(errorMessage);

  }
};


/**
 * Path for included scripts
 * @type {string}
 */
goog.basePath = '';


/**
 * A hook for overriding the base path.
 * @type {string|undefined}
 */
goog.global.CLOSURE_BASE_PATH;


/**
 * Whether to write out Closure's deps file. By default,
 * the deps are written.
 * @type {boolean|undefined}
 */
goog.global.CLOSURE_NO_DEPS;


/**
 * A function to import a single script. This is meant to be overridden when
 * Closure is being run in non-HTML contexts, such as web workers. It's defined
 * in the global scope so that it can be set before base.js is loaded, which
 * allows deps.js to be imported properly.
 *
 * The function is passed the script source, which is a relative URI. It should
 * return true if the script was imported, false otherwise.
 */
goog.global.CLOSURE_IMPORT_SCRIPT;


/**
 * Null function used for default values of callbacks, etc.
 * @return {void} Nothing.
 */
goog.nullFunction = function() {};


/**
 * The identity function. Returns its first argument.
 *
 * @param {...*} var_args The arguments of the function.
 * @return {*} The first argument.
 * @deprecated Use goog.functions.identity instead.
 */
goog.identityFunction = function(var_args) {
  return arguments[0];
};


/**
 * When defining a class Foo with an abstract method bar(), you can do:
 *
 * Foo.prototype.bar = goog.abstractMethod
 *
 * Now if a subclass of Foo fails to override bar(), an error
 * will be thrown when bar() is invoked.
 *
 * Note: This does not take the name of the function to override as
 * an argument because that would make it more difficult to obfuscate
 * our JavaScript code.
 *
 * @type {!Function}
 * @throws {Error} when invoked to indicate the method should be
 *   overridden.
 */
goog.abstractMethod = function() {
  throw Error('unimplemented abstract method');
};


/**
 * Adds a {@code getInstance} static method that always return the same instance
 * object.
 * @param {!Function} ctor The constructor for the class to add the static
 *     method to.
 */
goog.addSingletonGetter = function(ctor) {
  ctor.getInstance = function() {
    return ctor.instance_ || (ctor.instance_ = new ctor());
  };
};


if (!COMPILED && goog.ENABLE_DEBUG_LOADER) {
  /**
   * Object used to keep track of urls that have already been added. This
   * record allows the prevention of circular dependencies.
   * @type {Object}
   * @private
   */
  goog.included_ = {};


  /**
   * This object is used to keep track of dependencies and other data that is
   * used for loading scripts
   * @private
   * @type {Object}
   */
  goog.dependencies_ = {
    pathToNames: {}, // 1 to many
    nameToPath: {}, // 1 to 1
    requires: {}, // 1 to many
    // used when resolving dependencies to prevent us from
    // visiting the file twice
    visited: {},
    written: {} // used to keep track of script files we have written
  };


  /**
   * Tries to detect whether is in the context of an HTML document.
   * @return {boolean} True if it looks like HTML document.
   * @private
   */
  goog.inHtmlDocument_ = function() {
    var doc = goog.global.document;
    return typeof doc != 'undefined' &&
           'write' in doc;  // XULDocument misses write.
  };


  /**
   * Tries to detect the base path of the base.js script that bootstraps Closure
   * @private
   */
  goog.findBasePath_ = function() {
    if (goog.global.CLOSURE_BASE_PATH) {
      goog.basePath = goog.global.CLOSURE_BASE_PATH;
      return;
    } else if (!goog.inHtmlDocument_()) {
      return;
    }
    var doc = goog.global.document;
    var scripts = doc.getElementsByTagName('script');
    // Search backwards since the current script is in almost all cases the one
    // that has base.js.
    for (var i = scripts.length - 1; i >= 0; --i) {
      var src = scripts[i].src;
      var qmark = src.lastIndexOf('?');
      var l = qmark == -1 ? src.length : qmark;
      if (src.substr(l - 7, 7) == 'base.js') {
        goog.basePath = src.substr(0, l - 7);
        return;
      }
    }
  };


  /**
   * Imports a script if, and only if, that script hasn't already been imported.
   * (Must be called at execution time)
   * @param {string} src Script source.
   * @private
   */
  goog.importScript_ = function(src) {
    var importScript = goog.global.CLOSURE_IMPORT_SCRIPT ||
        goog.writeScriptTag_;
    if (!goog.dependencies_.written[src] && importScript(src)) {
      goog.dependencies_.written[src] = true;
    }
  };


  /**
   * The default implementation of the import function. Writes a script tag to
   * import the script.
   *
   * @param {string} src The script source.
   * @return {boolean} True if the script was imported, false otherwise.
   * @private
   */
  goog.writeScriptTag_ = function(src) {
    if (goog.inHtmlDocument_()) {
      var doc = goog.global.document;
      doc.write(
          '<script type="text/javascript" src="' + src + '"></' + 'script>');
      return true;
    } else {
      return false;
    }
  };


  /**
   * Resolves dependencies based on the dependencies added using addDependency
   * and calls importScript_ in the correct order.
   * @private
   */
  goog.writeScripts_ = function() {
    // the scripts we need to write this time
    var scripts = [];
    var seenScript = {};
    var deps = goog.dependencies_;

    function visitNode(path) {
      if (path in deps.written) {
        return;
      }

      // we have already visited this one. We can get here if we have cyclic
      // dependencies
      if (path in deps.visited) {
        if (!(path in seenScript)) {
          seenScript[path] = true;
          scripts.push(path);
        }
        return;
      }

      deps.visited[path] = true;

      if (path in deps.requires) {
        for (var requireName in deps.requires[path]) {
          // If the required name is defined, we assume that it was already
          // bootstrapped by other means.
          if (!goog.isProvided_(requireName)) {
            if (requireName in deps.nameToPath) {
              visitNode(deps.nameToPath[requireName]);
            } else {
              throw Error('Undefined nameToPath for ' + requireName);
            }
          }
        }
      }

      if (!(path in seenScript)) {
        seenScript[path] = true;
        scripts.push(path);
      }
    }

    for (var path in goog.included_) {
      if (!deps.written[path]) {
        visitNode(path);
      }
    }

    for (var i = 0; i < scripts.length; i++) {
      if (scripts[i]) {
        goog.importScript_(goog.basePath + scripts[i]);
      } else {
        throw Error('Undefined script input');
      }
    }
  };


  /**
   * Looks at the dependency rules and tries to determine the script file that
   * fulfills a particular rule.
   * @param {string} rule In the form goog.namespace.Class or project.script.
   * @return {?string} Url corresponding to the rule, or null.
   * @private
   */
  goog.getPathFromDeps_ = function(rule) {
    if (rule in goog.dependencies_.nameToPath) {
      return goog.dependencies_.nameToPath[rule];
    } else {
      return null;
    }
  };

  goog.findBasePath_();

  // Allow projects to manage the deps files themselves.
  if (!goog.global.CLOSURE_NO_DEPS) {
    goog.importScript_(goog.basePath + 'deps.js');
  }
}



//==============================================================================
// Language Enhancements
//==============================================================================


/**
 * This is a "fixed" version of the typeof operator.  It differs from the typeof
 * operator in such a way that null returns 'null' and arrays return 'array'.
 * @param {*} value The value to get the type of.
 * @return {string} The name of the type.
 */
goog.typeOf = function(value) {
  var s = typeof value;
  if (s == 'object') {
    if (value) {
      // Check these first, so we can avoid calling Object.prototype.toString if
      // possible.
      //
      // IE improperly marshals tyepof across execution contexts, but a
      // cross-context object will still return false for "instanceof Object".
      if (value instanceof Array) {
        return 'array';
      } else if (value instanceof Object) {
        return s;
      }

      // HACK: In order to use an Object prototype method on the arbitrary
      //   value, the compiler requires the value be cast to type Object,
      //   even though the ECMA spec explicitly allows it.
      var className = Object.prototype.toString.call(
          /** @type {Object} */ (value));
      // In Firefox 3.6, attempting to access iframe window objects' length
      // property throws an NS_ERROR_FAILURE, so we need to special-case it
      // here.
      if (className == '[object Window]') {
        return 'object';
      }

      // We cannot always use constructor == Array or instanceof Array because
      // different frames have different Array objects. In IE6, if the iframe
      // where the array was created is destroyed, the array loses its
      // prototype. Then dereferencing val.splice here throws an exception, so
      // we can't use goog.isFunction. Calling typeof directly returns 'unknown'
      // so that will work. In this case, this function will return false and
      // most array functions will still work because the array is still
      // array-like (supports length and []) even though it has lost its
      // prototype.
      // Mark Miller noticed that Object.prototype.toString
      // allows access to the unforgeable [[Class]] property.
      //  15.2.4.2 Object.prototype.toString ( )
      //  When the toString method is called, the following steps are taken:
      //      1. Get the [[Class]] property of this object.
      //      2. Compute a string value by concatenating the three strings
      //         "[object ", Result(1), and "]".
      //      3. Return Result(2).
      // and this behavior survives the destruction of the execution context.
      if ((className == '[object Array]' ||
           // In IE all non value types are wrapped as objects across window
           // boundaries (not iframe though) so we have to do object detection
           // for this edge case
           typeof value.length == 'number' &&
           typeof value.splice != 'undefined' &&
           typeof value.propertyIsEnumerable != 'undefined' &&
           !value.propertyIsEnumerable('splice')

          )) {
        return 'array';
      }
      // HACK: There is still an array case that fails.
      //     function ArrayImpostor() {}
      //     ArrayImpostor.prototype = [];
      //     var impostor = new ArrayImpostor;
      // this can be fixed by getting rid of the fast path
      // (value instanceof Array) and solely relying on
      // (value && Object.prototype.toString.vall(value) === '[object Array]')
      // but that would require many more function calls and is not warranted
      // unless closure code is receiving objects from untrusted sources.

      // IE in cross-window calls does not correctly marshal the function type
      // (it appears just as an object) so we cannot use just typeof val ==
      // 'function'. However, if the object has a call property, it is a
      // function.
      if ((className == '[object Function]' ||
          typeof value.call != 'undefined' &&
          typeof value.propertyIsEnumerable != 'undefined' &&
          !value.propertyIsEnumerable('call'))) {
        return 'function';
      }


    } else {
      return 'null';
    }

  } else if (s == 'function' && typeof value.call == 'undefined') {
    // In Safari typeof nodeList returns 'function', and on Firefox
    // typeof behaves similarly for HTML{Applet,Embed,Object}Elements
    // and RegExps.  We would like to return object for those and we can
    // detect an invalid function by making sure that the function
    // object has a call method.
    return 'object';
  }
  return s;
};


/**
 * Safe way to test whether a property is enumarable.  It allows testing
 * for enumerable on objects where 'propertyIsEnumerable' is overridden or
 * does not exist (like DOM nodes in IE). Does not use browser native
 * Object.propertyIsEnumerable.
 * @param {Object} object The object to test if the property is enumerable.
 * @param {string} propName The property name to check for.
 * @return {boolean} True if the property is enumarable.
 * @private
 */
goog.propertyIsEnumerableCustom_ = function(object, propName) {
  // KJS in Safari 2 is not ECMAScript compatible and lacks crucial methods
  // such as propertyIsEnumerable.  We therefore use a workaround.
  // Does anyone know a more efficient work around?
  if (propName in object) {
    for (var key in object) {
      if (key == propName &&
          Object.prototype.hasOwnProperty.call(object, propName)) {
        return true;
      }
    }
  }
  return false;
};


/**
 * Safe way to test whether a property is enumarable.  It allows testing
 * for enumerable on objects where 'propertyIsEnumerable' is overridden or
 * does not exist (like DOM nodes in IE).
 * @param {Object} object The object to test if the property is enumerable.
 * @param {string} propName The property name to check for.
 * @return {boolean} True if the property is enumarable.
 * @private
 */
goog.propertyIsEnumerable_ = function(object, propName) {
  // In IE if object is from another window, cannot use propertyIsEnumerable
  // from this window's Object. Will raise a 'JScript object expected' error.
  if (object instanceof Object) {
    return Object.prototype.propertyIsEnumerable.call(object, propName);
  } else {
    return goog.propertyIsEnumerableCustom_(object, propName);
  }
};


/**
 * Returns true if the specified value is not |undefined|.
 * WARNING: Do not use this to test if an object has a property. Use the in
 * operator instead.  Additionally, this function assumes that the global
 * undefined variable has not been redefined.
 * @param {*} val Variable to test.
 * @return {boolean} Whether variable is defined.
 */
goog.isDef = function(val) {
  return val !== undefined;
};


/**
 * Returns true if the specified value is |null|
 * @param {*} val Variable to test.
 * @return {boolean} Whether variable is null.
 */
goog.isNull = function(val) {
  return val === null;
};


/**
 * Returns true if the specified value is defined and not null
 * @param {*} val Variable to test.
 * @return {boolean} Whether variable is defined and not null.
 */
goog.isDefAndNotNull = function(val) {
  // Note that undefined == null.
  return val != null;
};


/**
 * Returns true if the specified value is an array
 * @param {*} val Variable to test.
 * @return {boolean} Whether variable is an array.
 */
goog.isArray = function(val) {
  return goog.typeOf(val) == 'array';
};


/**
 * Returns true if the object looks like an array. To qualify as array like
 * the value needs to be either a NodeList or an object with a Number length
 * property.
 * @param {*} val Variable to test.
 * @return {boolean} Whether variable is an array.
 */
goog.isArrayLike = function(val) {
  var type = goog.typeOf(val);
  return type == 'array' || type == 'object' && typeof val.length == 'number';
};


/**
 * Returns true if the object looks like a Date. To qualify as Date-like
 * the value needs to be an object and have a getFullYear() function.
 * @param {*} val Variable to test.
 * @return {boolean} Whether variable is a like a Date.
 */
goog.isDateLike = function(val) {
  return goog.isObject(val) && typeof val.getFullYear == 'function';
};


/**
 * Returns true if the specified value is a string
 * @param {*} val Variable to test.
 * @return {boolean} Whether variable is a string.
 */
goog.isString = function(val) {
  return typeof val == 'string';
};


/**
 * Returns true if the specified value is a boolean
 * @param {*} val Variable to test.
 * @return {boolean} Whether variable is boolean.
 */
goog.isBoolean = function(val) {
  return typeof val == 'boolean';
};


/**
 * Returns true if the specified value is a number
 * @param {*} val Variable to test.
 * @return {boolean} Whether variable is a number.
 */
goog.isNumber = function(val) {
  return typeof val == 'number';
};


/**
 * Returns true if the specified value is a function
 * @param {*} val Variable to test.
 * @return {boolean} Whether variable is a function.
 */
goog.isFunction = function(val) {
  return goog.typeOf(val) == 'function';
};


/**
 * Returns true if the specified value is an object.  This includes arrays
 * and functions.
 * @param {*} val Variable to test.
 * @return {boolean} Whether variable is an object.
 */
goog.isObject = function(val) {
  var type = goog.typeOf(val);
  return type == 'object' || type == 'array' || type == 'function';
};


/**
 * Gets a unique ID for an object. This mutates the object so that further
 * calls with the same object as a parameter returns the same value. The unique
 * ID is guaranteed to be unique across the current session amongst objects that
 * are passed into {@code getUid}. There is no guarantee that the ID is unique
 * or consistent across sessions. It is unsafe to generate unique ID for
 * function prototypes.
 *
 * @param {Object} obj The object to get the unique ID for.
 * @return {number} The unique ID for the object.
 */
goog.getUid = function(obj) {
  // TODO(arv): Make the type stricter, do not accept null.

  // In Opera window.hasOwnProperty exists but always returns false so we avoid
  // using it. As a consequence the unique ID generated for BaseClass.prototype
  // and SubClass.prototype will be the same.
  return obj[goog.UID_PROPERTY_] ||
      (obj[goog.UID_PROPERTY_] = ++goog.uidCounter_);
};


/**
 * Removes the unique ID from an object. This is useful if the object was
 * previously mutated using {@code goog.getUid} in which case the mutation is
 * undone.
 * @param {Object} obj The object to remove the unique ID field from.
 */
goog.removeUid = function(obj) {
  // TODO(arv): Make the type stricter, do not accept null.

  // DOM nodes in IE are not instance of Object and throws exception
  // for delete. Instead we try to use removeAttribute
  if ('removeAttribute' in obj) {
    obj.removeAttribute(goog.UID_PROPERTY_);
  }
  /** @preserveTry */
  try {
    delete obj[goog.UID_PROPERTY_];
  } catch (ex) {
  }
};


/**
 * Name for unique ID property. Initialized in a way to help avoid collisions
 * with other closure javascript on the same page.
 * @type {string}
 * @private
 */
goog.UID_PROPERTY_ = 'closure_uid_' +
    Math.floor(Math.random() * 2147483648).toString(36);


/**
 * Counter for UID.
 * @type {number}
 * @private
 */
goog.uidCounter_ = 0;


/**
 * Adds a hash code field to an object. The hash code is unique for the
 * given object.
 * @param {Object} obj The object to get the hash code for.
 * @return {number} The hash code for the object.
 * @deprecated Use goog.getUid instead.
 */
goog.getHashCode = goog.getUid;


/**
 * Removes the hash code field from an object.
 * @param {Object} obj The object to remove the field from.
 * @deprecated Use goog.removeUid instead.
 */
goog.removeHashCode = goog.removeUid;


/**
 * Clones a value. The input may be an Object, Array, or basic type. Objects and
 * arrays will be cloned recursively.
 *
 * WARNINGS:
 * <code>goog.cloneObject</code> does not detect reference loops. Objects that
 * refer to themselves will cause infinite recursion.
 *
 * <code>goog.cloneObject</code> is unaware of unique identifiers, and copies
 * UIDs created by <code>getUid</code> into cloned results.
 *
 * @param {*} obj The value to clone.
 * @return {*} A clone of the input value.
 * @deprecated goog.cloneObject is unsafe. Prefer the goog.object methods.
 */
goog.cloneObject = function(obj) {
  var type = goog.typeOf(obj);
  if (type == 'object' || type == 'array') {
    if (obj.clone) {
      return obj.clone();
    }
    var clone = type == 'array' ? [] : {};
    for (var key in obj) {
      clone[key] = goog.cloneObject(obj[key]);
    }
    return clone;
  }

  return obj;
};


/**
 * Forward declaration for the clone method. This is necessary until the
 * compiler can better support duck-typing constructs as used in
 * goog.cloneObject.
 *
 * TODO(user): Remove once the JSCompiler can infer that the check for
 * proto.clone is safe in goog.cloneObject.
 *
 * @type {Function}
 */
Object.prototype.clone;


/**
 * A native implementation of goog.bind.
 * @param {Function} fn A function to partially apply.
 * @param {Object|undefined} selfObj Specifies the object which |this| should
 *     point to when the function is run.
 * @param {...*} var_args Additional arguments that are partially
 *     applied to the function.
 * @return {!Function} A partially-applied form of the function bind() was
 *     invoked as a method of.
 * @private
 * @suppress {deprecated} The compiler thinks that Function.prototype.bind
 *     is deprecated because some people have declared a pure-JS version.
 *     Only the pure-JS version is truly deprecated.
 */
goog.bindNative_ = function(fn, selfObj, var_args) {
  return /** @type {!Function} */ (fn.call.apply(fn.bind, arguments));
};


/**
 * A pure-JS implementation of goog.bind.
 * @param {Function} fn A function to partially apply.
 * @param {Object|undefined} selfObj Specifies the object which |this| should
 *     point to when the function is run.
 * @param {...*} var_args Additional arguments that are partially
 *     applied to the function.
 * @return {!Function} A partially-applied form of the function bind() was
 *     invoked as a method of.
 * @private
 */
goog.bindJs_ = function(fn, selfObj, var_args) {
  if (!fn) {
    throw new Error();
  }

  if (arguments.length > 2) {
    var boundArgs = Array.prototype.slice.call(arguments, 2);
    return function() {
      // Prepend the bound arguments to the current arguments.
      var newArgs = Array.prototype.slice.call(arguments);
      Array.prototype.unshift.apply(newArgs, boundArgs);
      return fn.apply(selfObj, newArgs);
    };

  } else {
    return function() {
      return fn.apply(selfObj, arguments);
    };
  }
};


/**
 * Partially applies this function to a particular 'this object' and zero or
 * more arguments. The result is a new function with some arguments of the first
 * function pre-filled and the value of |this| 'pre-specified'.<br><br>
 *
 * Remaining arguments specified at call-time are appended to the pre-
 * specified ones.<br><br>
 *
 * Also see: {@link #partial}.<br><br>
 *
 * Usage:
 * <pre>var barMethBound = bind(myFunction, myObj, 'arg1', 'arg2');
 * barMethBound('arg3', 'arg4');</pre>
 *
 * @param {Function} fn A function to partially apply.
 * @param {Object|undefined} selfObj Specifies the object which |this| should
 *     point to when the function is run.
 * @param {...*} var_args Additional arguments that are partially
 *     applied to the function.
 * @return {!Function} A partially-applied form of the function bind() was
 *     invoked as a method of.
 * @suppress {deprecated} See above.
 */
goog.bind = function(fn, selfObj, var_args) {
  // TODO(nicksantos): narrow the type signature.
  if (Function.prototype.bind &&
      // NOTE(nicksantos): Somebody pulled base.js into the default
      // Chrome extension environment. This means that for Chrome extensions,
      // they get the implementation of Function.prototype.bind that
      // calls goog.bind instead of the native one. Even worse, we don't want
      // to introduce a circular dependency between goog.bind and
      // Function.prototype.bind, so we have to hack this to make sure it
      // works correctly.
      Function.prototype.bind.toString().indexOf('native code') != -1) {
    goog.bind = goog.bindNative_;
  } else {
    goog.bind = goog.bindJs_;
  }
  return goog.bind.apply(null, arguments);
};


/**
 * Like bind(), except that a 'this object' is not required. Useful when the
 * target function is already bound.
 *
 * Usage:
 * var g = partial(f, arg1, arg2);
 * g(arg3, arg4);
 *
 * @param {Function} fn A function to partially apply.
 * @param {...*} var_args Additional arguments that are partially
 *     applied to fn.
 * @return {!Function} A partially-applied form of the function bind() was
 *     invoked as a method of.
 */
goog.partial = function(fn, var_args) {
  var args = Array.prototype.slice.call(arguments, 1);
  return function() {
    // Prepend the bound arguments to the current arguments.
    var newArgs = Array.prototype.slice.call(arguments);
    newArgs.unshift.apply(newArgs, args);
    return fn.apply(this, newArgs);
  };
};


/**
 * Copies all the members of a source object to a target object. This method
 * does not work on all browsers for all objects that contain keys such as
 * toString or hasOwnProperty. Use goog.object.extend for this purpose.
 * @param {Object} target Target.
 * @param {Object} source Source.
 */
goog.mixin = function(target, source) {
  for (var x in source) {
    target[x] = source[x];
  }

  // For IE7 or lower, the for-in-loop does not contain any properties that are
  // not enumerable on the prototype object (for example, isPrototypeOf from
  // Object.prototype) but also it will not include 'replace' on objects that
  // extend String and change 'replace' (not that it is common for anyone to
  // extend anything except Object).
};


/**
 * @return {number} An integer value representing the number of milliseconds
 *     between midnight, January 1, 1970 and the current time.
 */
goog.now = Date.now || (function() {
  // Unary plus operator converts its operand to a number which in the case of
  // a date is done by calling getTime().
  return +new Date();
});


/**
 * Evals javascript in the global scope.  In IE this uses execScript, other
 * browsers use goog.global.eval. If goog.global.eval does not evaluate in the
 * global scope (for example, in Safari), appends a script tag instead.
 * Throws an exception if neither execScript or eval is defined.
 * @param {string} script JavaScript string.
 */
goog.globalEval = function(script) {
  if (goog.global.execScript) {
    goog.global.execScript(script, 'JavaScript');
  } else if (goog.global.eval) {
    // Test to see if eval works
    if (goog.evalWorksForGlobals_ == null) {
      goog.global.eval('var _et_ = 1;');
      if (typeof goog.global['_et_'] != 'undefined') {
        delete goog.global['_et_'];
        goog.evalWorksForGlobals_ = true;
      } else {
        goog.evalWorksForGlobals_ = false;
      }
    }

    if (goog.evalWorksForGlobals_) {
      goog.global.eval(script);
    } else {
      var doc = goog.global.document;
      var scriptElt = doc.createElement('script');
      scriptElt.type = 'text/javascript';
      scriptElt.defer = false;
      // Note(user): can't use .innerHTML since "t('<test>')" will fail and
      // .text doesn't work in Safari 2.  Therefore we append a text node.
      scriptElt.appendChild(doc.createTextNode(script));
      doc.body.appendChild(scriptElt);
      doc.body.removeChild(scriptElt);
    }
  } else {
    throw Error('goog.globalEval not available');
  }
};


/**
 * Indicates whether or not we can call 'eval' directly to eval code in the
 * global scope. Set to a Boolean by the first call to goog.globalEval (which
 * empirically tests whether eval works for globals). @see goog.globalEval
 * @type {?boolean}
 * @private
 */
goog.evalWorksForGlobals_ = null;


/**
 * Optional map of CSS class names to obfuscated names used with
 * goog.getCssName().
 * @type {Object|undefined}
 * @private
 * @see goog.setCssNameMapping
 */
goog.cssNameMapping_;


/**
 * Optional obfuscation style for CSS class names. Should be set to either
 * 'BY_WHOLE' or 'BY_PART' if defined.
 * @type {string|undefined}
 * @private
 * @see goog.setCssNameMapping
 */
goog.cssNameMappingStyle_;


/**
 * Handles strings that are intended to be used as CSS class names.
 *
 * This function works in tandem with @see goog.setCssNameMapping.
 *
 * Without any mapping set, the arguments are simple joined with a
 * hyphen and passed through unaltered.
 *
 * When there is a mapping, there are two possible styles in which
 * these mappings are used. In the BY_PART style, each part (i.e. in
 * between hyphens) of the passed in css name is rewritten according
 * to the map. In the BY_WHOLE style, the full css name is looked up in
 * the map directly. If a rewrite is not specified by the map, the
 * compiler will output a warning.
 *
 * When the mapping is passed to the compiler, it will replace calls
 * to goog.getCssName with the strings from the mapping, e.g.
 *     var x = goog.getCssName('foo');
 *     var y = goog.getCssName(this.baseClass, 'active');
 *  becomes:
 *     var x= 'foo';
 *     var y = this.baseClass + '-active';
 *
 * If one argument is passed it will be processed, if two are passed
 * only the modifier will be processed, as it is assumed the first
 * argument was generated as a result of calling goog.getCssName.
 *
 * @param {string} className The class name.
 * @param {string=} opt_modifier A modifier to be appended to the class name.
 * @return {string} The class name or the concatenation of the class name and
 *     the modifier.
 */
goog.getCssName = function(className, opt_modifier) {
  var getMapping = function(cssName) {
    return goog.cssNameMapping_[cssName] || cssName;
  };

  var renameByParts = function(cssName) {
    // Remap all the parts individually.
    var parts = cssName.split('-');
    var mapped = [];
    for (var i = 0; i < parts.length; i++) {
      mapped.push(getMapping(parts[i]));
    }
    return mapped.join('-');
  };

  var rename;
  if (goog.cssNameMapping_) {
    rename = goog.cssNameMappingStyle_ == 'BY_WHOLE' ?
        getMapping : renameByParts;
  } else {
    rename = function(a) {
      return a;
    };
  }

  if (opt_modifier) {
    return className + '-' + rename(opt_modifier);
  } else {
    return rename(className);
  }
};


/**
 * Sets the map to check when returning a value from goog.getCssName(). Example:
 * <pre>
 * goog.setCssNameMapping({
 *   "goog": "a",
 *   "disabled": "b",
 * });
 *
 * var x = goog.getCssName('goog');
 * // The following evaluates to: "a a-b".
 * goog.getCssName('goog') + ' ' + goog.getCssName(x, 'disabled')
 * </pre>
 * When declared as a map of string literals to string literals, the JSCompiler
 * will replace all calls to goog.getCssName() using the supplied map if the
 * --closure_pass flag is set.
 *
 * @param {!Object} mapping A map of strings to strings where keys are possible
 *     arguments to goog.getCssName() and values are the corresponding values
 *     that should be returned.
 * @param {string=} opt_style The style of css name mapping. There are two valid
 *     options: 'BY_PART', and 'BY_WHOLE'.
 * @see goog.getCssName for a description.
 */
goog.setCssNameMapping = function(mapping, opt_style) {
  goog.cssNameMapping_ = mapping;
  goog.cssNameMappingStyle_ = opt_style;
};


/**
 * To use CSS renaming in compiled mode, one of the input files should have a
 * call to goog.setCssNameMapping() with an object literal that the JSCompiler
 * can extract and use to replace all calls to goog.getCssName(). In uncompiled
 * mode, JavaScript code should be loaded before this base.js file that declares
 * a global variable, CLOSURE_CSS_NAME_MAPPING, which is used below. This is
 * to ensure that the mapping is loaded before any calls to goog.getCssName()
 * are made in uncompiled mode.
 *
 * A hook for overriding the CSS name mapping.
 * @type {Object|undefined}
 */
goog.global.CLOSURE_CSS_NAME_MAPPING;


if (!COMPILED && goog.global.CLOSURE_CSS_NAME_MAPPING) {
  // This does not call goog.setCssNameMapping() because the JSCompiler
  // requires that goog.setCssNameMapping() be called with an object literal.
  goog.cssNameMapping_ = goog.global.CLOSURE_CSS_NAME_MAPPING;
}


/**
 * Abstract implementation of goog.getMsg for use with localized messages.
 * @param {string} str Translatable string, places holders in the form {$foo}.
 * @param {Object=} opt_values Map of place holder name to value.
 * @return {string} message with placeholders filled.
 */
goog.getMsg = function(str, opt_values) {
  var values = opt_values || {};
  for (var key in values) {
    var value = ('' + values[key]).replace(/\$/g, '$$$$');
    str = str.replace(new RegExp('\\{\\$' + key + '\\}', 'gi'), value);
  }
  return str;
};


/**
 * Exposes an unobfuscated global namespace path for the given object.
 * Note that fields of the exported object *will* be obfuscated,
 * unless they are exported in turn via this function or
 * goog.exportProperty
 *
 * <p>Also handy for making public items that are defined in anonymous
 * closures.
 *
 * ex. goog.exportSymbol('Foo', Foo);
 *
 * ex. goog.exportSymbol('public.path.Foo.staticFunction',
 *                       Foo.staticFunction);
 *     public.path.Foo.staticFunction();
 *
 * ex. goog.exportSymbol('public.path.Foo.prototype.myMethod',
 *                       Foo.prototype.myMethod);
 *     new public.path.Foo().myMethod();
 *
 * @param {string} publicPath Unobfuscated name to export.
 * @param {*} object Object the name should point to.
 * @param {Object=} opt_objectToExportTo The object to add the path to; default
 *     is |goog.global|.
 */
goog.exportSymbol = function(publicPath, object, opt_objectToExportTo) {
  goog.exportPath_(publicPath, object, opt_objectToExportTo);
};


/**
 * Exports a property unobfuscated into the object's namespace.
 * ex. goog.exportProperty(Foo, 'staticFunction', Foo.staticFunction);
 * ex. goog.exportProperty(Foo.prototype, 'myMethod', Foo.prototype.myMethod);
 * @param {Object} object Object whose static property is being exported.
 * @param {string} publicName Unobfuscated name to export.
 * @param {*} symbol Object the name should point to.
 */
goog.exportProperty = function(object, publicName, symbol) {
  object[publicName] = symbol;
};


/**
 * Inherit the prototype methods from one constructor into another.
 *
 * Usage:
 * <pre>
 * function ParentClass(a, b) { }
 * ParentClass.prototype.foo = function(a) { }
 *
 * function ChildClass(a, b, c) {
 *   goog.base(this, a, b);
 * }
 * goog.inherits(ChildClass, ParentClass);
 *
 * var child = new ChildClass('a', 'b', 'see');
 * child.foo(); // works
 * </pre>
 *
 * In addition, a superclass' implementation of a method can be invoked
 * as follows:
 *
 * <pre>
 * ChildClass.prototype.foo = function(a) {
 *   ChildClass.superClass_.foo.call(this, a);
 *   // other code
 * };
 * </pre>
 *
 * @param {Function} childCtor Child class.
 * @param {Function} parentCtor Parent class.
 */
goog.inherits = function(childCtor, parentCtor) {
  /** @constructor */
  function tempCtor() {};
  tempCtor.prototype = parentCtor.prototype;
  childCtor.superClass_ = parentCtor.prototype;
  childCtor.prototype = new tempCtor();
  childCtor.prototype.constructor = childCtor;
};


/**
 * Call up to the superclass.
 *
 * If this is called from a constructor, then this calls the superclass
 * contructor with arguments 1-N.
 *
 * If this is called from a prototype method, then you must pass
 * the name of the method as the second argument to this function. If
 * you do not, you will get a runtime error. This calls the superclass'
 * method with arguments 2-N.
 *
 * This function only works if you use goog.inherits to express
 * inheritance relationships between your classes.
 *
 * This function is a compiler primitive. At compile-time, the
 * compiler will do macro expansion to remove a lot of
 * the extra overhead that this function introduces. The compiler
 * will also enforce a lot of the assumptions that this function
 * makes, and treat it as a compiler error if you break them.
 *
 * @param {!Object} me Should always be "this".
 * @param {*=} opt_methodName The method name if calling a super method.
 * @param {...*} var_args The rest of the arguments.
 * @return {*} The return value of the superclass method.
 */
goog.base = function(me, opt_methodName, var_args) {
  var caller = arguments.callee.caller;
  if (caller.superClass_) {
    // This is a constructor. Call the superclass constructor.
    return caller.superClass_.constructor.apply(
        me, Array.prototype.slice.call(arguments, 1));
  }

  var args = Array.prototype.slice.call(arguments, 2);
  var foundCaller = false;
  for (var ctor = me.constructor;
       ctor; ctor = ctor.superClass_ && ctor.superClass_.constructor) {
    if (ctor.prototype[opt_methodName] === caller) {
      foundCaller = true;
    } else if (foundCaller) {
      return ctor.prototype[opt_methodName].apply(me, args);
    }
  }

  // If we did not find the caller in the prototype chain,
  // then one of two things happened:
  // 1) The caller is an instance method.
  // 2) This method was not called by the right caller.
  if (me[opt_methodName] === caller) {
    return me.constructor.prototype[opt_methodName].apply(me, args);
  } else {
    throw Error(
        'goog.base called from a method of one name ' +
        'to a method of a different name');
  }
};


/**
 * Allow for aliasing within scope functions.  This function exists for
 * uncompiled code - in compiled code the calls will be inlined and the
 * aliases applied.  In uncompiled code the function is simply run since the
 * aliases as written are valid JavaScript.
 * @param {function()} fn Function to call.  This function can contain aliases
 *     to namespaces (e.g. "var dom = goog.dom") or classes
 *    (e.g. "var Timer = goog.Timer").
 */
goog.scope = function(fn) {
  fn.call(goog.global);
};



//javascript/closure/deps.js
// Copyright 2011 The Closure Library Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// This file has been auto-generated by GenJsDeps, please do not edit.

goog.addDependency('../../third_party/javascript/closure/caja/string/html/htmlparser.js', ['goog.string.html.HtmlParser', 'goog.string.html.HtmlParser.EFlags', 'goog.string.html.HtmlParser.Elements', 'goog.string.html.HtmlParser.Entities', 'goog.string.html.HtmlSaxHandler'], []);
goog.addDependency('../../third_party/javascript/closure/caja/string/html/htmlsanitizer.js', ['goog.string.html.HtmlSanitizer', 'goog.string.html.HtmlSanitizer.AttributeType', 'goog.string.html.HtmlSanitizer.Attributes', 'goog.string.html.htmlSanitize'], ['goog.string.StringBuffer', 'goog.string.html.HtmlParser', 'goog.string.html.HtmlParser.EFlags', 'goog.string.html.HtmlParser.Elements', 'goog.string.html.HtmlSaxHandler']);
goog.addDependency('../../third_party/javascript/closure/dojo/dom/query.js', ['goog.dom.query'], ['goog.array', 'goog.dom', 'goog.functions', 'goog.string', 'goog.userAgent']);
goog.addDependency('../../third_party/javascript/closure/jpeg_encoder/jpeg_encoder_basic.js', ['goog.crypt.JpegEncoder'], ['goog.crypt.base64']);
goog.addDependency('../../third_party/javascript/closure/loremipsum/text/loremipsum.js', ['goog.text.LoremIpsum'], ['goog.array', 'goog.math', 'goog.string', 'goog.structs.Map', 'goog.structs.Set']);
goog.addDependency('../../third_party/javascript/closure/mochikit/async/deferred.js', ['goog.async.Deferred', 'goog.async.Deferred.AlreadyCalledError', 'goog.async.Deferred.CancelledError'], ['goog.array', 'goog.asserts', 'goog.debug.Error']);
goog.addDependency('../../third_party/javascript/closure/mochikit/async/deferredlist.js', ['goog.async.DeferredList'], ['goog.array', 'goog.async.Deferred']);
goog.addDependency('../../third_party/javascript/closure/osapi/osapi.js', ['goog.osapi'], []);
goog.addDependency('../../third_party/javascript/closure/silverlight/clipboardbutton.js', ['goog.silverlight.ClipboardButton', 'goog.silverlight.ClipboardButtonType', 'goog.silverlight.ClipboardEvent', 'goog.silverlight.CopyButton', 'goog.silverlight.PasteButton', 'goog.silverlight.PasteButtonEvent'], ['goog.asserts', 'goog.events.Event', 'goog.math.Size', 'goog.silverlight', 'goog.ui.Component']);
goog.addDependency('../../third_party/javascript/closure/silverlight/silverlight.js', ['goog.silverlight'], []);
goog.addDependency('../../third_party/javascript/closure/silverlight/supporteduseragent.js', ['goog.silverlight.supportedUserAgent'], []);
goog.addDependency('array/array.js', ['goog.array', 'goog.array.ArrayLike'], ['goog.asserts']);
goog.addDependency('asserts/asserts.js', ['goog.asserts', 'goog.asserts.AssertionError'], ['goog.debug.Error', 'goog.string']);
goog.addDependency('async/conditionaldelay.js', ['goog.async.ConditionalDelay'], ['goog.Disposable', 'goog.async.Delay']);
goog.addDependency('async/delay.js', ['goog.Delay', 'goog.async.Delay'], ['goog.Disposable', 'goog.Timer']);
goog.addDependency('async/throttle.js', ['goog.Throttle', 'goog.async.Throttle'], ['goog.Disposable', 'goog.Timer']);
goog.addDependency('base.js', ['goog'], []);
goog.addDependency('color/alpha.js', ['goog.color.alpha'], ['goog.color']);
goog.addDependency('color/color.js', ['goog.color'], ['goog.color.names', 'goog.math']);
goog.addDependency('color/names.js', ['goog.color.names'], []);
goog.addDependency('crypt/arc4.js', ['goog.crypt.Arc4'], ['goog.asserts']);
goog.addDependency('crypt/base64.js', ['goog.crypt.base64'], ['goog.crypt', 'goog.userAgent']);
goog.addDependency('crypt/basen.js', ['goog.crypt.baseN'], []);
goog.addDependency('crypt/blobhasher.js', ['goog.crypt.BlobHasher', 'goog.crypt.BlobHasher.EventType'], ['goog.asserts', 'goog.crypt', 'goog.crypt.Hash', 'goog.debug.Logger', 'goog.events.EventTarget', 'goog.fs']);
goog.addDependency('crypt/crypt.js', ['goog.crypt'], ['goog.array']);
goog.addDependency('crypt/hash.js', ['goog.crypt.Hash'], []);
goog.addDependency('crypt/hash32.js', ['goog.crypt.hash32'], ['goog.crypt']);
goog.addDependency('crypt/hash_test.js', ['goog.crypt.hash_test'], ['goog.testing.asserts']);
goog.addDependency('crypt/hmac.js', ['goog.crypt.Hmac'], ['goog.asserts', 'goog.crypt.Hash']);
goog.addDependency('crypt/md5.js', ['goog.crypt.Md5'], ['goog.crypt.Hash']);
goog.addDependency('crypt/sha1.js', ['goog.crypt.Sha1'], ['goog.crypt.Hash']);
goog.addDependency('cssom/cssom.js', ['goog.cssom', 'goog.cssom.CssRuleType'], ['goog.array', 'goog.dom']);
goog.addDependency('cssom/iframe/style.js', ['goog.cssom.iframe.style'], ['goog.cssom', 'goog.dom', 'goog.dom.NodeType', 'goog.dom.classes', 'goog.string', 'goog.style', 'goog.userAgent']);
goog.addDependency('datasource/datamanager.js', ['goog.ds.DataManager'], ['goog.ds.BasicNodeList', 'goog.ds.DataNode', 'goog.ds.Expr', 'goog.string', 'goog.structs', 'goog.structs.Map']);
goog.addDependency('datasource/datasource.js', ['goog.ds.BaseDataNode', 'goog.ds.BasicNodeList', 'goog.ds.DataNode', 'goog.ds.DataNodeList', 'goog.ds.EmptyNodeList', 'goog.ds.LoadState', 'goog.ds.SortedNodeList', 'goog.ds.Util', 'goog.ds.logger'], ['goog.array', 'goog.debug.Logger']);
goog.addDependency('datasource/expr.js', ['goog.ds.Expr'], ['goog.ds.BasicNodeList', 'goog.ds.EmptyNodeList', 'goog.string']);
goog.addDependency('datasource/fastdatanode.js', ['goog.ds.AbstractFastDataNode', 'goog.ds.FastDataNode', 'goog.ds.FastListNode', 'goog.ds.PrimitiveFastDataNode'], ['goog.ds.DataManager', 'goog.ds.EmptyNodeList', 'goog.string']);
goog.addDependency('datasource/jsdatasource.js', ['goog.ds.JsDataSource', 'goog.ds.JsPropertyDataSource'], ['goog.ds.BaseDataNode', 'goog.ds.BasicNodeList', 'goog.ds.DataManager', 'goog.ds.EmptyNodeList', 'goog.ds.LoadState']);
goog.addDependency('datasource/jsondatasource.js', ['goog.ds.JsonDataSource'], ['goog.Uri', 'goog.dom', 'goog.ds.DataManager', 'goog.ds.JsDataSource', 'goog.ds.LoadState', 'goog.ds.logger']);
goog.addDependency('datasource/jsxmlhttpdatasource.js', ['goog.ds.JsXmlHttpDataSource'], ['goog.Uri', 'goog.ds.DataManager', 'goog.ds.FastDataNode', 'goog.ds.LoadState', 'goog.ds.logger', 'goog.events', 'goog.net.EventType', 'goog.net.XhrIo']);
goog.addDependency('datasource/xmldatasource.js', ['goog.ds.XmlDataSource', 'goog.ds.XmlHttpDataSource'], ['goog.Uri', 'goog.dom.NodeType', 'goog.dom.xml', 'goog.ds.BasicNodeList', 'goog.ds.DataManager', 'goog.ds.LoadState', 'goog.ds.logger', 'goog.net.XhrIo', 'goog.string']);
goog.addDependency('date/date.js', ['goog.date', 'goog.date.Date', 'goog.date.DateTime', 'goog.date.Interval', 'goog.date.month', 'goog.date.weekDay'], ['goog.asserts', 'goog.date.DateLike', 'goog.i18n.DateTimeSymbols', 'goog.string']);
goog.addDependency('date/datelike.js', ['goog.date.DateLike'], []);
goog.addDependency('date/daterange.js', ['goog.date.DateRange', 'goog.date.DateRange.Iterator', 'goog.date.DateRange.StandardDateRangeKeys'], ['goog.date.Date', 'goog.date.Interval', 'goog.iter.Iterator', 'goog.iter.StopIteration']);
goog.addDependency('date/relative.js', ['goog.date.relative'], ['goog.i18n.DateTimeFormat']);
goog.addDependency('date/utcdatetime.js', ['goog.date.UtcDateTime'], ['goog.date', 'goog.date.Date', 'goog.date.DateTime', 'goog.date.Interval']);
goog.addDependency('db/db.js', ['goog.db'], ['goog.async.Deferred', 'goog.db.Error', 'goog.db.IndexedDb']);
goog.addDependency('db/error.js', ['goog.db.Error', 'goog.db.Error.ErrorCode', 'goog.db.Error.VersionChangeBlockedError'], ['goog.debug.Error']);
goog.addDependency('db/index.js', ['goog.db.Index'], ['goog.async.Deferred', 'goog.db.Error', 'goog.debug']);
goog.addDependency('db/indexeddb.js', ['goog.db.IndexedDb'], ['goog.async.Deferred', 'goog.db.Error', 'goog.db.Error.VersionChangeBlockedError', 'goog.db.ObjectStore', 'goog.db.Transaction', 'goog.db.Transaction.TransactionMode']);
goog.addDependency('db/objectstore.js', ['goog.db.ObjectStore'], ['goog.async.Deferred', 'goog.db.Error', 'goog.db.Index', 'goog.debug']);
goog.addDependency('db/transaction.js', ['goog.db.Transaction', 'goog.db.Transaction.TransactionMode'], ['goog.db.Error', 'goog.db.ObjectStore', 'goog.events.EventHandler', 'goog.events.EventTarget']);
goog.addDependency('debug/console.js', ['goog.debug.Console'], ['goog.debug.LogManager', 'goog.debug.Logger.Level', 'goog.debug.TextFormatter']);
goog.addDependency('debug/debug.js', ['goog.debug'], ['goog.array', 'goog.string', 'goog.structs.Set', 'goog.userAgent']);
goog.addDependency('debug/debugwindow.js', ['goog.debug.DebugWindow'], ['goog.debug.HtmlFormatter', 'goog.debug.LogManager', 'goog.structs.CircularBuffer', 'goog.userAgent']);
goog.addDependency('debug/devcss/devcss.js', ['goog.debug.DevCss', 'goog.debug.DevCss.UserAgent'], ['goog.cssom', 'goog.dom.classes', 'goog.events', 'goog.events.EventType', 'goog.string', 'goog.userAgent']);
goog.addDependency('debug/devcss/devcssrunner.js', ['goog.debug.devCssRunner'], ['goog.debug.DevCss']);
goog.addDependency('debug/divconsole.js', ['goog.debug.DivConsole'], ['goog.debug.HtmlFormatter', 'goog.debug.LogManager', 'goog.style']);
goog.addDependency('debug/entrypointregistry.js', ['goog.debug.EntryPointMonitor', 'goog.debug.entryPointRegistry'], ['goog.asserts']);
goog.addDependency('debug/error.js', ['goog.debug.Error'], []);
goog.addDependency('debug/errorhandler.js', ['goog.debug.ErrorHandler', 'goog.debug.ErrorHandler.ProtectedFunctionError'], ['goog.asserts', 'goog.debug', 'goog.debug.EntryPointMonitor', 'goog.debug.Trace']);
goog.addDependency('debug/errorhandlerweakdep.js', ['goog.debug.errorHandlerWeakDep'], []);
goog.addDependency('debug/errorreporter.js', ['goog.debug.ErrorReporter', 'goog.debug.ErrorReporter.ExceptionEvent'], ['goog.debug', 'goog.debug.ErrorHandler', 'goog.debug.Logger', 'goog.events', 'goog.events.Event', 'goog.events.EventTarget', 'goog.net.XhrIo', 'goog.object', 'goog.string', 'goog.uri.utils']);
goog.addDependency('debug/fancywindow.js', ['goog.debug.FancyWindow'], ['goog.debug.DebugWindow', 'goog.debug.LogManager', 'goog.debug.Logger', 'goog.debug.Logger.Level', 'goog.dom.DomHelper', 'goog.object', 'goog.string', 'goog.userAgent']);
goog.addDependency('debug/formatter.js', ['goog.debug.Formatter', 'goog.debug.HtmlFormatter', 'goog.debug.TextFormatter'], ['goog.debug.RelativeTimeProvider', 'goog.string']);
goog.addDependency('debug/fpsdisplay.js', ['goog.debug.FpsDisplay'], ['goog.asserts', 'goog.fx.anim', 'goog.ui.Component']);
goog.addDependency('debug/gcdiagnostics.js', ['goog.debug.GcDiagnostics'], ['goog.debug.Logger', 'goog.debug.Trace', 'goog.userAgent']);
goog.addDependency('debug/logbuffer.js', ['goog.debug.LogBuffer'], ['goog.asserts', 'goog.debug.LogRecord']);
goog.addDependency('debug/logger.js', ['goog.debug.LogManager', 'goog.debug.Logger', 'goog.debug.Logger.Level'], ['goog.array', 'goog.asserts', 'goog.debug', 'goog.debug.LogBuffer', 'goog.debug.LogRecord']);
goog.addDependency('debug/logrecord.js', ['goog.debug.LogRecord'], []);
goog.addDependency('debug/logrecordserializer.js', ['goog.debug.logRecordSerializer'], ['goog.debug.LogRecord', 'goog.debug.Logger.Level', 'goog.json', 'goog.object']);
goog.addDependency('debug/reflect.js', ['goog.debug.reflect'], []);
goog.addDependency('debug/relativetimeprovider.js', ['goog.debug.RelativeTimeProvider'], []);
goog.addDependency('debug/tracer.js', ['goog.debug.Trace'], ['goog.array', 'goog.debug.Logger', 'goog.iter', 'goog.structs.Map', 'goog.structs.SimplePool']);
goog.addDependency('disposable/disposable.js', ['goog.Disposable', 'goog.dispose'], ['goog.disposable.IDisposable']);
goog.addDependency('disposable/idisposable.js', ['goog.disposable.IDisposable'], []);
goog.addDependency('dom/a11y.js', ['goog.dom.a11y', 'goog.dom.a11y.Announcer', 'goog.dom.a11y.LivePriority', 'goog.dom.a11y.Role', 'goog.dom.a11y.State'], ['goog.Disposable', 'goog.dom', 'goog.object']);
goog.addDependency('dom/abstractmultirange.js', ['goog.dom.AbstractMultiRange'], ['goog.array', 'goog.dom', 'goog.dom.AbstractRange']);
goog.addDependency('dom/abstractrange.js', ['goog.dom.AbstractRange', 'goog.dom.RangeIterator', 'goog.dom.RangeType'], ['goog.dom', 'goog.dom.NodeType', 'goog.dom.SavedCaretRange', 'goog.dom.TagIterator', 'goog.userAgent']);
goog.addDependency('dom/annotate.js', ['goog.dom.annotate'], ['goog.array', 'goog.dom', 'goog.dom.NodeType', 'goog.string']);
goog.addDependency('dom/browserfeature.js', ['goog.dom.BrowserFeature'], ['goog.userAgent']);
goog.addDependency('dom/browserrange/abstractrange.js', ['goog.dom.browserrange.AbstractRange'], ['goog.dom', 'goog.dom.NodeType', 'goog.dom.RangeEndpoint', 'goog.dom.TagName', 'goog.dom.TextRangeIterator', 'goog.iter', 'goog.string', 'goog.string.StringBuffer', 'goog.userAgent']);
goog.addDependency('dom/browserrange/browserrange.js', ['goog.dom.browserrange', 'goog.dom.browserrange.Error'], ['goog.dom', 'goog.dom.browserrange.GeckoRange', 'goog.dom.browserrange.IeRange', 'goog.dom.browserrange.OperaRange', 'goog.dom.browserrange.W3cRange', 'goog.dom.browserrange.WebKitRange', 'goog.userAgent']);
goog.addDependency('dom/browserrange/geckorange.js', ['goog.dom.browserrange.GeckoRange'], ['goog.dom.browserrange.W3cRange']);
goog.addDependency('dom/browserrange/ierange.js', ['goog.dom.browserrange.IeRange'], ['goog.array', 'goog.debug.Logger', 'goog.dom', 'goog.dom.NodeIterator', 'goog.dom.NodeType', 'goog.dom.RangeEndpoint', 'goog.dom.TagName', 'goog.dom.browserrange.AbstractRange', 'goog.iter', 'goog.iter.StopIteration', 'goog.string']);
goog.addDependency('dom/browserrange/operarange.js', ['goog.dom.browserrange.OperaRange'], ['goog.dom.browserrange.W3cRange']);
goog.addDependency('dom/browserrange/w3crange.js', ['goog.dom.browserrange.W3cRange'], ['goog.dom', 'goog.dom.NodeType', 'goog.dom.RangeEndpoint', 'goog.dom.browserrange.AbstractRange', 'goog.string']);
goog.addDependency('dom/browserrange/webkitrange.js', ['goog.dom.browserrange.WebKitRange'], ['goog.dom.RangeEndpoint', 'goog.dom.browserrange.W3cRange', 'goog.userAgent']);
goog.addDependency('dom/classes.js', ['goog.dom.classes'], ['goog.array']);
goog.addDependency('dom/controlrange.js', ['goog.dom.ControlRange', 'goog.dom.ControlRangeIterator'], ['goog.array', 'goog.dom', 'goog.dom.AbstractMultiRange', 'goog.dom.AbstractRange', 'goog.dom.RangeIterator', 'goog.dom.RangeType', 'goog.dom.SavedRange', 'goog.dom.TagWalkType', 'goog.dom.TextRange', 'goog.iter.StopIteration', 'goog.userAgent']);
goog.addDependency('dom/dataset.js', ['goog.dom.dataset'], ['goog.string']);
goog.addDependency('dom/dom.js', ['goog.dom', 'goog.dom.DomHelper', 'goog.dom.NodeType'], ['goog.array', 'goog.dom.BrowserFeature', 'goog.dom.TagName', 'goog.dom.classes', 'goog.math.Coordinate', 'goog.math.Size', 'goog.object', 'goog.string', 'goog.userAgent']);
goog.addDependency('dom/dom_test.js', ['goog.dom.dom_test'], ['goog.dom', 'goog.dom.DomHelper', 'goog.dom.NodeType', 'goog.dom.TagName', 'goog.testing.asserts', 'goog.userAgent', 'goog.userAgent.product', 'goog.userAgent.product.isVersion']);
goog.addDependency('dom/fontsizemonitor.js', ['goog.dom.FontSizeMonitor', 'goog.dom.FontSizeMonitor.EventType'], ['goog.dom', 'goog.events', 'goog.events.EventTarget', 'goog.events.EventType', 'goog.userAgent']);
goog.addDependency('dom/forms.js', ['goog.dom.forms'], ['goog.structs.Map']);
goog.addDependency('dom/iframe.js', ['goog.dom.iframe'], ['goog.dom']);
goog.addDependency('dom/iter.js', ['goog.dom.iter.AncestorIterator', 'goog.dom.iter.ChildIterator', 'goog.dom.iter.SiblingIterator'], ['goog.iter.Iterator', 'goog.iter.StopIteration']);
goog.addDependency('dom/multirange.js', ['goog.dom.MultiRange', 'goog.dom.MultiRangeIterator'], ['goog.array', 'goog.debug.Logger', 'goog.dom.AbstractMultiRange', 'goog.dom.AbstractRange', 'goog.dom.RangeIterator', 'goog.dom.RangeType', 'goog.dom.SavedRange', 'goog.dom.TextRange', 'goog.iter.StopIteration']);
goog.addDependency('dom/nodeiterator.js', ['goog.dom.NodeIterator'], ['goog.dom.TagIterator']);
goog.addDependency('dom/nodeoffset.js', ['goog.dom.NodeOffset'], ['goog.Disposable', 'goog.dom.TagName']);
goog.addDependency('dom/pattern/abstractpattern.js', ['goog.dom.pattern.AbstractPattern'], ['goog.dom.pattern.MatchType']);
goog.addDependency('dom/pattern/allchildren.js', ['goog.dom.pattern.AllChildren'], ['goog.dom.pattern.AbstractPattern', 'goog.dom.pattern.MatchType']);
goog.addDependency('dom/pattern/callback/callback.js', ['goog.dom.pattern.callback'], ['goog.dom', 'goog.dom.TagWalkType', 'goog.iter']);
goog.addDependency('dom/pattern/callback/counter.js', ['goog.dom.pattern.callback.Counter'], []);
goog.addDependency('dom/pattern/callback/test.js', ['goog.dom.pattern.callback.Test'], ['goog.iter.StopIteration']);
goog.addDependency('dom/pattern/childmatches.js', ['goog.dom.pattern.ChildMatches'], ['goog.dom.pattern.AllChildren', 'goog.dom.pattern.MatchType']);
goog.addDependency('dom/pattern/endtag.js', ['goog.dom.pattern.EndTag'], ['goog.dom.TagWalkType', 'goog.dom.pattern.Tag']);
goog.addDependency('dom/pattern/fulltag.js', ['goog.dom.pattern.FullTag'], ['goog.dom.pattern.MatchType', 'goog.dom.pattern.StartTag', 'goog.dom.pattern.Tag']);
goog.addDependency('dom/pattern/matcher.js', ['goog.dom.pattern.Matcher'], ['goog.dom.TagIterator', 'goog.dom.pattern.MatchType', 'goog.iter']);
goog.addDependency('dom/pattern/nodetype.js', ['goog.dom.pattern.NodeType'], ['goog.dom.pattern.AbstractPattern', 'goog.dom.pattern.MatchType']);
goog.addDependency('dom/pattern/pattern.js', ['goog.dom.pattern', 'goog.dom.pattern.MatchType'], []);
goog.addDependency('dom/pattern/repeat.js', ['goog.dom.pattern.Repeat'], ['goog.dom.NodeType', 'goog.dom.pattern.AbstractPattern', 'goog.dom.pattern.MatchType']);
goog.addDependency('dom/pattern/sequence.js', ['goog.dom.pattern.Sequence'], ['goog.dom.NodeType', 'goog.dom.pattern.AbstractPattern', 'goog.dom.pattern.MatchType']);
goog.addDependency('dom/pattern/starttag.js', ['goog.dom.pattern.StartTag'], ['goog.dom.TagWalkType', 'goog.dom.pattern.Tag']);
goog.addDependency('dom/pattern/tag.js', ['goog.dom.pattern.Tag'], ['goog.dom.pattern', 'goog.dom.pattern.AbstractPattern', 'goog.dom.pattern.MatchType', 'goog.object']);
goog.addDependency('dom/pattern/text.js', ['goog.dom.pattern.Text'], ['goog.dom.NodeType', 'goog.dom.pattern', 'goog.dom.pattern.AbstractPattern', 'goog.dom.pattern.MatchType']);
goog.addDependency('dom/range.js', ['goog.dom.Range'], ['goog.dom', 'goog.dom.AbstractRange', 'goog.dom.ControlRange', 'goog.dom.MultiRange', 'goog.dom.NodeType', 'goog.dom.TextRange', 'goog.userAgent']);
goog.addDependency('dom/rangeendpoint.js', ['goog.dom.RangeEndpoint'], []);
goog.addDependency('dom/savedcaretrange.js', ['goog.dom.SavedCaretRange'], ['goog.array', 'goog.dom', 'goog.dom.SavedRange', 'goog.dom.TagName', 'goog.string']);
goog.addDependency('dom/savedrange.js', ['goog.dom.SavedRange'], ['goog.Disposable', 'goog.debug.Logger']);
goog.addDependency('dom/selection.js', ['goog.dom.selection'], ['goog.string', 'goog.userAgent']);
goog.addDependency('dom/tagiterator.js', ['goog.dom.TagIterator', 'goog.dom.TagWalkType'], ['goog.dom.NodeType', 'goog.iter.Iterator', 'goog.iter.StopIteration']);
goog.addDependency('dom/tagname.js', ['goog.dom.TagName'], []);
goog.addDependency('dom/textrange.js', ['goog.dom.TextRange'], ['goog.array', 'goog.dom', 'goog.dom.AbstractRange', 'goog.dom.RangeType', 'goog.dom.SavedRange', 'goog.dom.TagName', 'goog.dom.TextRangeIterator', 'goog.dom.browserrange', 'goog.string', 'goog.userAgent']);
goog.addDependency('dom/textrangeiterator.js', ['goog.dom.TextRangeIterator'], ['goog.array', 'goog.dom.NodeType', 'goog.dom.RangeIterator', 'goog.dom.TagName', 'goog.iter.StopIteration']);
goog.addDependency('dom/viewportsizemonitor.js', ['goog.dom.ViewportSizeMonitor'], ['goog.dom', 'goog.events', 'goog.events.EventTarget', 'goog.events.EventType', 'goog.math.Size', 'goog.userAgent']);
goog.addDependency('dom/xml.js', ['goog.dom.xml'], ['goog.dom', 'goog.dom.NodeType']);
goog.addDependency('editor/browserfeature.js', ['goog.editor.BrowserFeature'], ['goog.editor.defines', 'goog.userAgent', 'goog.userAgent.product', 'goog.userAgent.product.isVersion']);
goog.addDependency('editor/clicktoeditwrapper.js', ['goog.editor.ClickToEditWrapper'], ['goog.Disposable', 'goog.asserts', 'goog.debug.Logger', 'goog.dom', 'goog.dom.Range', 'goog.dom.TagName', 'goog.editor.BrowserFeature', 'goog.editor.Command', 'goog.editor.Field.EventType', 'goog.editor.range', 'goog.events.BrowserEvent.MouseButton', 'goog.events.EventHandler', 'goog.events.EventType']);
goog.addDependency('editor/command.js', ['goog.editor.Command'], []);
goog.addDependency('editor/defines.js', ['goog.editor.defines'], []);
goog.addDependency('editor/field.js', ['goog.editor.Field', 'goog.editor.Field.EventType'], ['goog.array', 'goog.async.Delay', 'goog.debug.Logger', 'goog.dom', 'goog.dom.Range', 'goog.dom.TagName', 'goog.dom.classes', 'goog.editor.BrowserFeature', 'goog.editor.Command', 'goog.editor.Plugin', 'goog.editor.icontent', 'goog.editor.icontent.FieldFormatInfo', 'goog.editor.icontent.FieldStyleInfo', 'goog.editor.node', 'goog.editor.range', 'goog.events', 'goog.events.BrowserEvent', 'goog.events.EventHandler', 'goog.events.EventType', 'goog.events.KeyCodes', 'goog.functions', 'goog.string', 'goog.string.Unicode', 'goog.style', 'goog.userAgent']);
goog.addDependency('editor/focus.js', ['goog.editor.focus'], ['goog.dom.selection']);
goog.addDependency('editor/icontent.js', ['goog.editor.icontent', 'goog.editor.icontent.FieldFormatInfo', 'goog.editor.icontent.FieldStyleInfo'], ['goog.editor.BrowserFeature', 'goog.style', 'goog.userAgent']);
goog.addDependency('editor/link.js', ['goog.editor.Link'], ['goog.dom', 'goog.dom.NodeType', 'goog.dom.Range', 'goog.editor.BrowserFeature', 'goog.editor.node', 'goog.editor.range', 'goog.string.Unicode', 'goog.uri.utils']);
goog.addDependency('editor/node.js', ['goog.editor.node'], ['goog.dom', 'goog.dom.NodeType', 'goog.dom.TagName', 'goog.dom.iter.ChildIterator', 'goog.dom.iter.SiblingIterator', 'goog.iter', 'goog.object', 'goog.string', 'goog.string.Unicode']);
goog.addDependency('editor/plugin.js', ['goog.editor.Plugin'], ['goog.debug.Logger', 'goog.editor.Command', 'goog.events.EventTarget', 'goog.functions', 'goog.object', 'goog.reflect']);
goog.addDependency('editor/plugins/abstractbubbleplugin.js', ['goog.editor.plugins.AbstractBubblePlugin'], ['goog.dom', 'goog.dom.NodeType', 'goog.dom.Range', 'goog.dom.TagName', 'goog.editor.Plugin', 'goog.editor.style', 'goog.events', 'goog.events.EventHandler', 'goog.events.EventType', 'goog.functions', 'goog.string.Unicode', 'goog.ui.Component.EventType', 'goog.ui.editor.Bubble', 'goog.userAgent']);
goog.addDependency('editor/plugins/abstractdialogplugin.js', ['goog.editor.plugins.AbstractDialogPlugin', 'goog.editor.plugins.AbstractDialogPlugin.EventType'], ['goog.dom', 'goog.dom.Range', 'goog.editor.Field.EventType', 'goog.editor.Plugin', 'goog.editor.range', 'goog.events', 'goog.ui.editor.AbstractDialog.EventType']);
goog.addDependency('editor/plugins/abstracttabhandler.js', ['goog.editor.plugins.AbstractTabHandler'], ['goog.editor.Plugin', 'goog.events.KeyCodes']);
goog.addDependency('editor/plugins/basictextformatter.js', ['goog.editor.plugins.BasicTextFormatter', 'goog.editor.plugins.BasicTextFormatter.COMMAND'], ['goog.array', 'goog.debug.Logger', 'goog.dom', 'goog.dom.NodeType', 'goog.dom.TagName', 'goog.editor.BrowserFeature', 'goog.editor.Link', 'goog.editor.Plugin', 'goog.editor.node', 'goog.editor.range', 'goog.iter', 'goog.object', 'goog.string', 'goog.string.Unicode', 'goog.style', 'goog.ui.editor.messages', 'goog.userAgent']);
goog.addDependency('editor/plugins/blockquote.js', ['goog.editor.plugins.Blockquote'], ['goog.debug.Logger', 'goog.dom', 'goog.dom.NodeType', 'goog.dom.TagName', 'goog.dom.classes', 'goog.editor.BrowserFeature', 'goog.editor.Command', 'goog.editor.Plugin', 'goog.editor.node', 'goog.functions']);
goog.addDependency('editor/plugins/emoticons.js', ['goog.editor.plugins.Emoticons'], ['goog.dom.TagName', 'goog.editor.Plugin', 'goog.functions', 'goog.ui.emoji.Emoji']);
goog.addDependency('editor/plugins/enterhandler.js', ['goog.editor.plugins.EnterHandler'], ['goog.dom', 'goog.dom.AbstractRange', 'goog.dom.NodeOffset', 'goog.dom.NodeType', 'goog.dom.TagName', 'goog.editor.BrowserFeature', 'goog.editor.Plugin', 'goog.editor.node', 'goog.editor.plugins.Blockquote', 'goog.editor.range', 'goog.editor.style', 'goog.events.KeyCodes', 'goog.string', 'goog.userAgent']);
goog.addDependency('editor/plugins/equationeditorbubble.js', ['goog.editor.plugins.equation.EquationBubble'], ['goog.dom', 'goog.dom.TagName', 'goog.editor.Command', 'goog.editor.plugins.AbstractBubblePlugin', 'goog.string.Unicode', 'goog.ui.editor.Bubble', 'goog.ui.equation.ImageRenderer']);
goog.addDependency('editor/plugins/equationeditorplugin.js', ['goog.editor.plugins.EquationEditorPlugin'], ['goog.editor.Command', 'goog.editor.plugins.AbstractDialogPlugin', 'goog.editor.range', 'goog.functions', 'goog.ui.editor.AbstractDialog.Builder', 'goog.ui.editor.EquationEditorDialog', 'goog.ui.editor.EquationEditorOkEvent', 'goog.ui.equation.EquationEditor', 'goog.ui.equation.ImageRenderer', 'goog.ui.equation.TexEditor']);
goog.addDependency('editor/plugins/headerformatter.js', ['goog.editor.plugins.HeaderFormatter'], ['goog.editor.Command', 'goog.editor.Plugin', 'goog.userAgent']);
goog.addDependency('editor/plugins/linkbubble.js', ['goog.editor.plugins.LinkBubble', 'goog.editor.plugins.LinkBubble.Action'], ['goog.array', 'goog.dom', 'goog.editor.BrowserFeature', 'goog.editor.Command', 'goog.editor.Link', 'goog.editor.plugins.AbstractBubblePlugin', 'goog.editor.range', 'goog.string', 'goog.style', 'goog.ui.editor.messages', 'goog.uri.utils', 'goog.window']);
goog.addDependency('editor/plugins/linkdialogplugin.js', ['goog.editor.plugins.LinkDialogPlugin'], ['goog.array', 'goog.editor.Command', 'goog.editor.plugins.AbstractDialogPlugin', 'goog.events.EventHandler', 'goog.functions', 'goog.ui.editor.AbstractDialog.EventType', 'goog.ui.editor.LinkDialog', 'goog.ui.editor.LinkDialog.EventType', 'goog.ui.editor.LinkDialog.OkEvent', 'goog.uri.utils']);
goog.addDependency('editor/plugins/listtabhandler.js', ['goog.editor.plugins.ListTabHandler'], ['goog.dom.TagName', 'goog.editor.Command', 'goog.editor.plugins.AbstractTabHandler']);
goog.addDependency('editor/plugins/loremipsum.js', ['goog.editor.plugins.LoremIpsum'], ['goog.asserts', 'goog.dom', 'goog.editor.Command', 'goog.editor.Plugin', 'goog.editor.node', 'goog.functions']);
goog.addDependency('editor/plugins/removeformatting.js', ['goog.editor.plugins.RemoveFormatting'], ['goog.dom', 'goog.dom.NodeType', 'goog.dom.Range', 'goog.dom.TagName', 'goog.editor.BrowserFeature', 'goog.editor.Plugin', 'goog.editor.node', 'goog.editor.range', 'goog.string']);
goog.addDependency('editor/plugins/spacestabhandler.js', ['goog.editor.plugins.SpacesTabHandler'], ['goog.dom', 'goog.dom.TagName', 'goog.editor.plugins.AbstractTabHandler', 'goog.editor.range']);
goog.addDependency('editor/plugins/tableeditor.js', ['goog.editor.plugins.TableEditor'], ['goog.array', 'goog.dom', 'goog.dom.TagName', 'goog.editor.Plugin', 'goog.editor.Table', 'goog.editor.node', 'goog.editor.range', 'goog.object']);
goog.addDependency('editor/plugins/tagonenterhandler.js', ['goog.editor.plugins.TagOnEnterHandler'], ['goog.dom', 'goog.dom.NodeType', 'goog.dom.Range', 'goog.dom.TagName', 'goog.editor.Command', 'goog.editor.node', 'goog.editor.plugins.EnterHandler', 'goog.editor.range', 'goog.editor.style', 'goog.events.KeyCodes', 'goog.string', 'goog.style', 'goog.userAgent']);
goog.addDependency('editor/plugins/undoredo.js', ['goog.editor.plugins.UndoRedo'], ['goog.debug.Logger', 'goog.dom', 'goog.dom.NodeOffset', 'goog.dom.Range', 'goog.editor.BrowserFeature', 'goog.editor.Command', 'goog.editor.Field.EventType', 'goog.editor.Plugin', 'goog.editor.plugins.UndoRedoManager', 'goog.editor.plugins.UndoRedoState', 'goog.events', 'goog.events.EventHandler']);
goog.addDependency('editor/plugins/undoredomanager.js', ['goog.editor.plugins.UndoRedoManager', 'goog.editor.plugins.UndoRedoManager.EventType'], ['goog.editor.plugins.UndoRedoState', 'goog.events.EventTarget']);
goog.addDependency('editor/plugins/undoredostate.js', ['goog.editor.plugins.UndoRedoState'], ['goog.events.EventTarget']);
goog.addDependency('editor/range.js', ['goog.editor.range', 'goog.editor.range.Point'], ['goog.array', 'goog.dom', 'goog.dom.NodeType', 'goog.dom.Range', 'goog.dom.RangeEndpoint', 'goog.dom.SavedCaretRange', 'goog.editor.BrowserFeature', 'goog.editor.node', 'goog.editor.style', 'goog.iter']);
goog.addDependency('editor/seamlessfield.js', ['goog.editor.SeamlessField'], ['goog.cssom.iframe.style', 'goog.debug.Logger', 'goog.dom', 'goog.dom.Range', 'goog.dom.TagName', 'goog.editor.BrowserFeature', 'goog.editor.Field', 'goog.editor.Field.EventType', 'goog.editor.icontent', 'goog.editor.icontent.FieldFormatInfo', 'goog.editor.icontent.FieldStyleInfo', 'goog.editor.node', 'goog.events', 'goog.events.EventType', 'goog.style']);
goog.addDependency('editor/seamlessfield_test.js', ['goog.editor.seamlessfield_test'], ['goog.dom', 'goog.editor.BrowserFeature', 'goog.editor.SeamlessField', 'goog.events', 'goog.style', 'goog.testing.MockClock', 'goog.testing.MockRange', 'goog.testing.jsunit']);
goog.addDependency('editor/style.js', ['goog.editor.style'], ['goog.dom', 'goog.dom.NodeType', 'goog.editor.BrowserFeature', 'goog.events.EventType', 'goog.object', 'goog.style', 'goog.userAgent']);
goog.addDependency('editor/table.js', ['goog.editor.Table', 'goog.editor.TableCell', 'goog.editor.TableRow'], ['goog.debug.Logger', 'goog.dom', 'goog.dom.NodeType', 'goog.dom.TagName', 'goog.string.Unicode', 'goog.style']);
goog.addDependency('events/actioneventwrapper.js', ['goog.events.actionEventWrapper'], ['goog.events', 'goog.events.EventHandler', 'goog.events.EventType', 'goog.events.EventWrapper', 'goog.events.KeyCodes']);
goog.addDependency('events/actionhandler.js', ['goog.events.ActionEvent', 'goog.events.ActionHandler', 'goog.events.ActionHandler.EventType', 'goog.events.BeforeActionEvent'], ['goog.events', 'goog.events.BrowserEvent', 'goog.events.EventTarget', 'goog.events.EventType', 'goog.events.KeyCodes', 'goog.userAgent']);
goog.addDependency('events/browserevent.js', ['goog.events.BrowserEvent', 'goog.events.BrowserEvent.MouseButton'], ['goog.events.BrowserFeature', 'goog.events.Event', 'goog.events.EventType', 'goog.reflect', 'goog.userAgent']);
goog.addDependency('events/browserfeature.js', ['goog.events.BrowserFeature'], ['goog.userAgent']);
goog.addDependency('events/event.js', ['goog.events.Event'], ['goog.Disposable']);
goog.addDependency('events/eventhandler.js', ['goog.events.EventHandler'], ['goog.Disposable', 'goog.array', 'goog.events', 'goog.events.EventWrapper']);
goog.addDependency('events/events.js', ['goog.events'], ['goog.array', 'goog.debug.entryPointRegistry', 'goog.debug.errorHandlerWeakDep', 'goog.events.BrowserEvent', 'goog.events.BrowserFeature', 'goog.events.Event', 'goog.events.EventWrapper', 'goog.events.Listener', 'goog.object', 'goog.userAgent']);
goog.addDependency('events/eventtarget.js', ['goog.events.EventTarget'], ['goog.Disposable', 'goog.events']);
goog.addDependency('events/eventtype.js', ['goog.events.EventType'], ['goog.userAgent']);
goog.addDependency('events/eventwrapper.js', ['goog.events.EventWrapper'], []);
goog.addDependency('events/filedrophandler.js', ['goog.events.FileDropHandler', 'goog.events.FileDropHandler.EventType'], ['goog.array', 'goog.debug.Logger', 'goog.dom', 'goog.events', 'goog.events.BrowserEvent', 'goog.events.EventHandler', 'goog.events.EventTarget', 'goog.events.EventType']);
goog.addDependency('events/focushandler.js', ['goog.events.FocusHandler', 'goog.events.FocusHandler.EventType'], ['goog.events', 'goog.events.BrowserEvent', 'goog.events.EventTarget', 'goog.userAgent']);
goog.addDependency('events/imehandler.js', ['goog.events.ImeHandler', 'goog.events.ImeHandler.Event', 'goog.events.ImeHandler.EventType'], ['goog.events.Event', 'goog.events.EventHandler', 'goog.events.EventTarget', 'goog.events.EventType', 'goog.events.KeyCodes', 'goog.userAgent', 'goog.userAgent.product']);
goog.addDependency('events/inputhandler.js', ['goog.events.InputHandler', 'goog.events.InputHandler.EventType'], ['goog.Timer', 'goog.dom', 'goog.events', 'goog.events.BrowserEvent', 'goog.events.EventHandler', 'goog.events.EventTarget', 'goog.events.KeyCodes', 'goog.userAgent']);
goog.addDependency('events/keycodes.js', ['goog.events.KeyCodes'], ['goog.userAgent']);
goog.addDependency('events/keyhandler.js', ['goog.events.KeyEvent', 'goog.events.KeyHandler', 'goog.events.KeyHandler.EventType'], ['goog.events', 'goog.events.BrowserEvent', 'goog.events.EventTarget', 'goog.events.EventType', 'goog.events.KeyCodes', 'goog.userAgent']);
goog.addDependency('events/keynames.js', ['goog.events.KeyNames'], []);
goog.addDependency('events/listener.js', ['goog.events.Listener'], []);
goog.addDependency('events/mousewheelhandler.js', ['goog.events.MouseWheelEvent', 'goog.events.MouseWheelHandler', 'goog.events.MouseWheelHandler.EventType'], ['goog.events', 'goog.events.BrowserEvent', 'goog.events.EventTarget', 'goog.math', 'goog.userAgent']);
goog.addDependency('events/onlinehandler.js', ['goog.events.OnlineHandler', 'goog.events.OnlineHandler.EventType'], ['goog.Timer', 'goog.events.BrowserFeature', 'goog.events.EventHandler', 'goog.events.EventTarget', 'goog.userAgent']);
goog.addDependency('events/pastehandler.js', ['goog.events.PasteHandler', 'goog.events.PasteHandler.EventType', 'goog.events.PasteHandler.State'], ['goog.Timer', 'goog.async.ConditionalDelay', 'goog.debug.Logger', 'goog.events.BrowserEvent', 'goog.events.EventHandler', 'goog.events.EventTarget', 'goog.events.EventType', 'goog.events.KeyCodes']);
goog.addDependency('format/emailaddress.js', ['goog.format.EmailAddress'], ['goog.string']);
goog.addDependency('format/format.js', ['goog.format'], ['goog.i18n.GraphemeBreak', 'goog.string', 'goog.userAgent']);
goog.addDependency('format/htmlprettyprinter.js', ['goog.format.HtmlPrettyPrinter', 'goog.format.HtmlPrettyPrinter.Buffer'], ['goog.object', 'goog.string.StringBuffer']);
goog.addDependency('format/jsonprettyprinter.js', ['goog.format.JsonPrettyPrinter', 'goog.format.JsonPrettyPrinter.HtmlDelimiters', 'goog.format.JsonPrettyPrinter.TextDelimiters'], ['goog.json', 'goog.json.Serializer', 'goog.string', 'goog.string.StringBuffer', 'goog.string.format']);
goog.addDependency('fs/entry.js', ['goog.fs.DirectoryEntry', 'goog.fs.DirectoryEntry.Behavior', 'goog.fs.Entry', 'goog.fs.FileEntry'], ['goog.array', 'goog.async.Deferred', 'goog.fs.Error', 'goog.fs.FileWriter', 'goog.functions', 'goog.string']);
goog.addDependency('fs/error.js', ['goog.fs.Error', 'goog.fs.Error.ErrorCode'], ['goog.debug.Error', 'goog.string']);
goog.addDependency('fs/filereader.js', ['goog.fs.FileReader', 'goog.fs.FileReader.EventType', 'goog.fs.FileReader.ReadyState'], ['goog.async.Deferred', 'goog.events.Event', 'goog.events.EventTarget', 'goog.fs.Error', 'goog.fs.ProgressEvent']);
goog.addDependency('fs/filesaver.js', ['goog.fs.FileSaver', 'goog.fs.FileSaver.EventType', 'goog.fs.FileSaver.ProgressEvent', 'goog.fs.FileSaver.ReadyState'], ['goog.events.Event', 'goog.events.EventTarget', 'goog.fs.Error', 'goog.fs.ProgressEvent']);
goog.addDependency('fs/filesystem.js', ['goog.fs.FileSystem'], ['goog.fs.DirectoryEntry']);
goog.addDependency('fs/filewriter.js', ['goog.fs.FileWriter'], ['goog.fs.Error', 'goog.fs.FileSaver']);
goog.addDependency('fs/fs.js', ['goog.fs'], ['goog.async.Deferred', 'goog.events', 'goog.fs.Error', 'goog.fs.FileReader', 'goog.fs.FileSystem']);
goog.addDependency('fs/progressevent.js', ['goog.fs.ProgressEvent'], ['goog.events.Event']);
goog.addDependency('functions/functions.js', ['goog.functions'], []);
goog.addDependency('fx/abstractdragdrop.js', ['goog.fx.AbstractDragDrop', 'goog.fx.AbstractDragDrop.EventType', 'goog.fx.DragDropEvent', 'goog.fx.DragDropItem'], ['goog.dom', 'goog.dom.classes', 'goog.events', 'goog.events.Event', 'goog.events.EventTarget', 'goog.events.EventType', 'goog.fx.Dragger', 'goog.fx.Dragger.EventType', 'goog.math.Box', 'goog.math.Coordinate', 'goog.style']);
goog.addDependency('fx/anim/anim.js', ['goog.fx.anim', 'goog.fx.anim.Animated'], ['goog.Timer', 'goog.events', 'goog.object']);
goog.addDependency('fx/animation.js', ['goog.fx.Animation', 'goog.fx.Animation.EventType', 'goog.fx.Animation.State', 'goog.fx.AnimationEvent'], ['goog.array', 'goog.events.Event', 'goog.fx.Transition', 'goog.fx.Transition.EventType', 'goog.fx.TransitionBase.State', 'goog.fx.anim', 'goog.fx.anim.Animated']);
goog.addDependency('fx/animationqueue.js', ['goog.fx.AnimationParallelQueue', 'goog.fx.AnimationQueue', 'goog.fx.AnimationSerialQueue'], ['goog.array', 'goog.asserts', 'goog.events.EventHandler', 'goog.fx.Transition.EventType', 'goog.fx.TransitionBase', 'goog.fx.TransitionBase.State']);
goog.addDependency('fx/css3/fx.js', ['goog.fx.css3'], ['goog.fx.css3.Transition']);
goog.addDependency('fx/css3/transition.js', ['goog.fx.css3.Transition'], ['goog.Timer', 'goog.fx.TransitionBase', 'goog.style', 'goog.style.transition']);
goog.addDependency('fx/cssspriteanimation.js', ['goog.fx.CssSpriteAnimation'], ['goog.fx.Animation']);
goog.addDependency('fx/dom.js', ['goog.fx.dom', 'goog.fx.dom.BgColorTransform', 'goog.fx.dom.ColorTransform', 'goog.fx.dom.Fade', 'goog.fx.dom.FadeIn', 'goog.fx.dom.FadeInAndShow', 'goog.fx.dom.FadeOut', 'goog.fx.dom.FadeOutAndHide', 'goog.fx.dom.PredefinedEffect', 'goog.fx.dom.Resize', 'goog.fx.dom.ResizeHeight', 'goog.fx.dom.ResizeWidth', 'goog.fx.dom.Scroll', 'goog.fx.dom.Slide', 'goog.fx.dom.SlideFrom', 'goog.fx.dom.Swipe'], ['goog.color', 'goog.events', 'goog.fx.Animation', 'goog.fx.Transition.EventType', 'goog.style']);
goog.addDependency('fx/dragdrop.js', ['goog.fx.DragDrop'], ['goog.fx.AbstractDragDrop', 'goog.fx.DragDropItem']);
goog.addDependency('fx/dragdropgroup.js', ['goog.fx.DragDropGroup'], ['goog.dom', 'goog.fx.AbstractDragDrop', 'goog.fx.DragDropItem']);
goog.addDependency('fx/dragger.js', ['goog.fx.DragEvent', 'goog.fx.Dragger', 'goog.fx.Dragger.EventType'], ['goog.dom', 'goog.events', 'goog.events.BrowserEvent.MouseButton', 'goog.events.Event', 'goog.events.EventHandler', 'goog.events.EventTarget', 'goog.events.EventType', 'goog.math.Coordinate', 'goog.math.Rect', 'goog.userAgent']);
goog.addDependency('fx/draglistgroup.js', ['goog.fx.DragListDirection', 'goog.fx.DragListGroup', 'goog.fx.DragListGroup.EventType', 'goog.fx.DragListGroupEvent'], ['goog.asserts', 'goog.dom', 'goog.dom.NodeType', 'goog.dom.classes', 'goog.events.Event', 'goog.events.EventHandler', 'goog.events.EventTarget', 'goog.events.EventType', 'goog.fx.Dragger', 'goog.fx.Dragger.EventType', 'goog.math.Coordinate', 'goog.style']);
goog.addDependency('fx/dragscrollsupport.js', ['goog.fx.DragScrollSupport'], ['goog.Disposable', 'goog.Timer', 'goog.dom', 'goog.events.EventHandler', 'goog.events.EventType', 'goog.math.Coordinate', 'goog.style']);
goog.addDependency('fx/easing.js', ['goog.fx.easing'], []);
goog.addDependency('fx/fx.js', ['goog.fx'], ['goog.asserts', 'goog.fx.Animation', 'goog.fx.Animation.EventType', 'goog.fx.Animation.State', 'goog.fx.AnimationEvent', 'goog.fx.Transition.EventType', 'goog.fx.easing']);
goog.addDependency('fx/transition.js', ['goog.fx.Transition', 'goog.fx.Transition.EventType'], []);
goog.addDependency('fx/transitionbase.js', ['goog.fx.TransitionBase', 'goog.fx.TransitionBase.State'], ['goog.events.EventTarget', 'goog.fx.Transition', 'goog.fx.Transition.EventType']);
goog.addDependency('gears/basestore.js', ['goog.gears.BaseStore', 'goog.gears.BaseStore.SchemaType'], ['goog.Disposable']);
goog.addDependency('gears/database.js', ['goog.gears.Database', 'goog.gears.Database.EventType', 'goog.gears.Database.TransactionEvent'], ['goog.array', 'goog.debug', 'goog.debug.Logger', 'goog.events.Event', 'goog.events.EventTarget', 'goog.gears', 'goog.json']);
goog.addDependency('gears/fakeworkerpool.js', ['goog.gears.FakeWorkerPool'], ['goog.Uri', 'goog.gears', 'goog.gears.WorkerPool', 'goog.net.XmlHttp']);
goog.addDependency('gears/gears.js', ['goog.gears'], ['goog.string']);
goog.addDependency('gears/httprequest.js', ['goog.gears.HttpRequest'], ['goog.Timer', 'goog.gears', 'goog.net.WrapperXmlHttpFactory', 'goog.net.XmlHttp']);
goog.addDependency('gears/loggerclient.js', ['goog.gears.LoggerClient'], ['goog.Disposable', 'goog.debug', 'goog.debug.Logger']);
goog.addDependency('gears/loggerserver.js', ['goog.gears.LoggerServer'], ['goog.Disposable', 'goog.debug.Logger', 'goog.debug.Logger.Level', 'goog.gears.Worker.EventType']);
goog.addDependency('gears/logstore.js', ['goog.gears.LogStore', 'goog.gears.LogStore.Query'], ['goog.async.Delay', 'goog.debug.LogManager', 'goog.debug.LogRecord', 'goog.debug.Logger', 'goog.debug.Logger.Level', 'goog.gears.BaseStore', 'goog.gears.BaseStore.SchemaType', 'goog.json']);
goog.addDependency('gears/managedresourcestore.js', ['goog.gears.ManagedResourceStore', 'goog.gears.ManagedResourceStore.EventType', 'goog.gears.ManagedResourceStore.UpdateStatus', 'goog.gears.ManagedResourceStoreEvent'], ['goog.debug.Logger', 'goog.events.Event', 'goog.events.EventTarget', 'goog.gears', 'goog.string']);
goog.addDependency('gears/multipartformdata.js', ['goog.gears.MultipartFormData'], ['goog.asserts', 'goog.gears', 'goog.string']);
goog.addDependency('gears/statustype.js', ['goog.gears.StatusType'], []);
goog.addDependency('gears/urlcapture.js', ['goog.gears.UrlCapture', 'goog.gears.UrlCapture.Event', 'goog.gears.UrlCapture.EventType'], ['goog.Uri', 'goog.debug.Logger', 'goog.events.Event', 'goog.events.EventTarget', 'goog.gears']);
goog.addDependency('gears/worker.js', ['goog.gears.Worker', 'goog.gears.Worker.EventType', 'goog.gears.WorkerEvent'], ['goog.events.Event', 'goog.events.EventTarget']);
goog.addDependency('gears/workerchannel.js', ['goog.gears.WorkerChannel'], ['goog.Disposable', 'goog.debug', 'goog.debug.Logger', 'goog.events', 'goog.gears.Worker', 'goog.gears.Worker.EventType', 'goog.gears.WorkerEvent', 'goog.json', 'goog.messaging.AbstractChannel']);
goog.addDependency('gears/workerpool.js', ['goog.gears.WorkerPool', 'goog.gears.WorkerPool.Event', 'goog.gears.WorkerPool.EventType'], ['goog.events.Event', 'goog.events.EventTarget', 'goog.gears', 'goog.gears.Worker']);
goog.addDependency('graphics/abstractgraphics.js', ['goog.graphics.AbstractGraphics'], ['goog.graphics.Path', 'goog.math.Coordinate', 'goog.math.Size', 'goog.style', 'goog.ui.Component']);
goog.addDependency('graphics/affinetransform.js', ['goog.graphics.AffineTransform'], ['goog.math']);
goog.addDependency('graphics/canvaselement.js', ['goog.graphics.CanvasEllipseElement', 'goog.graphics.CanvasGroupElement', 'goog.graphics.CanvasImageElement', 'goog.graphics.CanvasPathElement', 'goog.graphics.CanvasRectElement', 'goog.graphics.CanvasTextElement'], ['goog.array', 'goog.dom', 'goog.dom.TagName', 'goog.graphics.EllipseElement', 'goog.graphics.GroupElement', 'goog.graphics.ImageElement', 'goog.graphics.Path', 'goog.graphics.PathElement', 'goog.graphics.RectElement', 'goog.graphics.TextElement']);
goog.addDependency('graphics/canvasgraphics.js', ['goog.graphics.CanvasGraphics'], ['goog.dom', 'goog.events.EventType', 'goog.graphics.AbstractGraphics', 'goog.graphics.CanvasEllipseElement', 'goog.graphics.CanvasGroupElement', 'goog.graphics.CanvasImageElement', 'goog.graphics.CanvasPathElement', 'goog.graphics.CanvasRectElement', 'goog.graphics.CanvasTextElement', 'goog.graphics.Font', 'goog.graphics.LinearGradient', 'goog.graphics.SolidFill', 'goog.graphics.Stroke', 'goog.math.Size']);
goog.addDependency('graphics/element.js', ['goog.graphics.Element'], ['goog.events', 'goog.events.EventTarget', 'goog.graphics.AffineTransform', 'goog.math']);
goog.addDependency('graphics/ellipseelement.js', ['goog.graphics.EllipseElement'], ['goog.graphics.StrokeAndFillElement']);
goog.addDependency('graphics/ext/coordinates.js', ['goog.graphics.ext.coordinates'], ['goog.string']);
goog.addDependency('graphics/ext/element.js', ['goog.graphics.ext.Element'], ['goog.events', 'goog.events.EventTarget', 'goog.functions', 'goog.graphics', 'goog.graphics.ext.coordinates']);
goog.addDependency('graphics/ext/ellipse.js', ['goog.graphics.ext.Ellipse'], ['goog.graphics.ext.StrokeAndFillElement']);
goog.addDependency('graphics/ext/ext.js', ['goog.graphics.ext'], ['goog.graphics.ext.Ellipse', 'goog.graphics.ext.Graphics', 'goog.graphics.ext.Group', 'goog.graphics.ext.Image', 'goog.graphics.ext.Rectangle', 'goog.graphics.ext.Shape', 'goog.graphics.ext.coordinates']);
goog.addDependency('graphics/ext/graphics.js', ['goog.graphics.ext.Graphics'], ['goog.events.EventType', 'goog.graphics.ext.Group']);
goog.addDependency('graphics/ext/group.js', ['goog.graphics.ext.Group'], ['goog.graphics.ext.Element']);
goog.addDependency('graphics/ext/image.js', ['goog.graphics.ext.Image'], ['goog.graphics.ext.Element']);
goog.addDependency('graphics/ext/path.js', ['goog.graphics.ext.Path'], ['goog.graphics.AffineTransform', 'goog.graphics.Path', 'goog.math', 'goog.math.Rect']);
goog.addDependency('graphics/ext/rectangle.js', ['goog.graphics.ext.Rectangle'], ['goog.graphics.ext.StrokeAndFillElement']);
goog.addDependency('graphics/ext/shape.js', ['goog.graphics.ext.Shape'], ['goog.graphics.ext.Path', 'goog.graphics.ext.StrokeAndFillElement', 'goog.math.Rect']);
goog.addDependency('graphics/ext/strokeandfillelement.js', ['goog.graphics.ext.StrokeAndFillElement'], ['goog.graphics.ext.Element']);
goog.addDependency('graphics/fill.js', ['goog.graphics.Fill'], []);
goog.addDependency('graphics/font.js', ['goog.graphics.Font'], []);
goog.addDependency('graphics/graphics.js', ['goog.graphics'], ['goog.graphics.CanvasGraphics', 'goog.graphics.SvgGraphics', 'goog.graphics.VmlGraphics', 'goog.userAgent']);
goog.addDependency('graphics/groupelement.js', ['goog.graphics.GroupElement'], ['goog.graphics.Element']);
goog.addDependency('graphics/imageelement.js', ['goog.graphics.ImageElement'], ['goog.graphics.Element']);
goog.addDependency('graphics/lineargradient.js', ['goog.graphics.LinearGradient'], ['goog.asserts', 'goog.graphics.Fill']);
goog.addDependency('graphics/path.js', ['goog.graphics.Path', 'goog.graphics.Path.Segment'], ['goog.array', 'goog.math']);
goog.addDependency('graphics/pathelement.js', ['goog.graphics.PathElement'], ['goog.graphics.StrokeAndFillElement']);
goog.addDependency('graphics/paths.js', ['goog.graphics.paths'], ['goog.graphics.Path', 'goog.math.Coordinate']);
goog.addDependency('graphics/rectelement.js', ['goog.graphics.RectElement'], ['goog.graphics.StrokeAndFillElement']);
goog.addDependency('graphics/solidfill.js', ['goog.graphics.SolidFill'], ['goog.graphics.Fill']);
goog.addDependency('graphics/stroke.js', ['goog.graphics.Stroke'], []);
goog.addDependency('graphics/strokeandfillelement.js', ['goog.graphics.StrokeAndFillElement'], ['goog.graphics.Element']);
goog.addDependency('graphics/svgelement.js', ['goog.graphics.SvgEllipseElement', 'goog.graphics.SvgGroupElement', 'goog.graphics.SvgImageElement', 'goog.graphics.SvgPathElement', 'goog.graphics.SvgRectElement', 'goog.graphics.SvgTextElement'], ['goog.dom', 'goog.graphics.EllipseElement', 'goog.graphics.GroupElement', 'goog.graphics.ImageElement', 'goog.graphics.PathElement', 'goog.graphics.RectElement', 'goog.graphics.TextElement']);
goog.addDependency('graphics/svggraphics.js', ['goog.graphics.SvgGraphics'], ['goog.Timer', 'goog.dom', 'goog.events.EventHandler', 'goog.events.EventType', 'goog.graphics.AbstractGraphics', 'goog.graphics.Font', 'goog.graphics.LinearGradient', 'goog.graphics.SolidFill', 'goog.graphics.Stroke', 'goog.graphics.SvgEllipseElement', 'goog.graphics.SvgGroupElement', 'goog.graphics.SvgImageElement', 'goog.graphics.SvgPathElement', 'goog.graphics.SvgRectElement', 'goog.graphics.SvgTextElement', 'goog.math.Size', 'goog.style', 'goog.userAgent']);
goog.addDependency('graphics/textelement.js', ['goog.graphics.TextElement'], ['goog.graphics.StrokeAndFillElement']);
goog.addDependency('graphics/vmlelement.js', ['goog.graphics.VmlEllipseElement', 'goog.graphics.VmlGroupElement', 'goog.graphics.VmlImageElement', 'goog.graphics.VmlPathElement', 'goog.graphics.VmlRectElement', 'goog.graphics.VmlTextElement'], ['goog.dom', 'goog.graphics.EllipseElement', 'goog.graphics.GroupElement', 'goog.graphics.ImageElement', 'goog.graphics.PathElement', 'goog.graphics.RectElement', 'goog.graphics.TextElement']);
goog.addDependency('graphics/vmlgraphics.js', ['goog.graphics.VmlGraphics'], ['goog.array', 'goog.dom', 'goog.events.EventHandler', 'goog.events.EventType', 'goog.graphics.AbstractGraphics', 'goog.graphics.Font', 'goog.graphics.LinearGradient', 'goog.graphics.SolidFill', 'goog.graphics.Stroke', 'goog.graphics.VmlEllipseElement', 'goog.graphics.VmlGroupElement', 'goog.graphics.VmlImageElement', 'goog.graphics.VmlPathElement', 'goog.graphics.VmlRectElement', 'goog.graphics.VmlTextElement', 'goog.math.Size', 'goog.string', 'goog.style']);
goog.addDependency('history/event.js', ['goog.history.Event'], ['goog.events.Event', 'goog.history.EventType']);
goog.addDependency('history/eventtype.js', ['goog.history.EventType'], []);
goog.addDependency('history/history.js', ['goog.History', 'goog.History.Event', 'goog.History.EventType'], ['goog.Timer', 'goog.dom', 'goog.events', 'goog.events.BrowserEvent', 'goog.events.Event', 'goog.events.EventHandler', 'goog.events.EventTarget', 'goog.events.EventType', 'goog.history.Event', 'goog.history.EventType', 'goog.string', 'goog.userAgent']);
goog.addDependency('history/html5history.js', ['goog.history.Html5History', 'goog.history.Html5History.TokenTransformer'], ['goog.asserts', 'goog.events', 'goog.events.EventTarget', 'goog.events.EventType', 'goog.history.Event', 'goog.history.EventType']);
goog.addDependency('i18n/bidi.js', ['goog.i18n.bidi'], []);
goog.addDependency('i18n/bidiformatter.js', ['goog.i18n.BidiFormatter'], ['goog.i18n.bidi', 'goog.string']);
goog.addDependency('i18n/charlistdecompressor.js', ['goog.i18n.CharListDecompressor'], ['goog.array', 'goog.i18n.uChar']);
goog.addDependency('i18n/charpickerdata.js', ['goog.i18n.CharPickerData'], []);
goog.addDependency('i18n/currency.js', ['goog.i18n.currency'], []);
goog.addDependency('i18n/currencycodemap.js', ['goog.i18n.currencyCodeMap'], []);
goog.addDependency('i18n/datetimeformat.js', ['goog.i18n.DateTimeFormat', 'goog.i18n.DateTimeFormat.Format'], ['goog.asserts', 'goog.date.DateLike', 'goog.i18n.DateTimeSymbols', 'goog.i18n.TimeZone', 'goog.string']);
goog.addDependency('i18n/datetimeparse.js', ['goog.i18n.DateTimeParse'], ['goog.date.DateLike', 'goog.i18n.DateTimeFormat', 'goog.i18n.DateTimeSymbols']);
goog.addDependency('i18n/datetimepatterns.js', ['goog.i18n.DateTimePatterns', 'goog.i18n.DateTimePatterns_am', 'goog.i18n.DateTimePatterns_ar', 'goog.i18n.DateTimePatterns_bg', 'goog.i18n.DateTimePatterns_bn', 'goog.i18n.DateTimePatterns_ca', 'goog.i18n.DateTimePatterns_cs', 'goog.i18n.DateTimePatterns_da', 'goog.i18n.DateTimePatterns_de', 'goog.i18n.DateTimePatterns_de_AT', 'goog.i18n.DateTimePatterns_de_CH', 'goog.i18n.DateTimePatterns_el', 'goog.i18n.DateTimePatterns_en', 'goog.i18n.DateTimePatterns_en_AU', 'goog.i18n.DateTimePatterns_en_GB', 'goog.i18n.DateTimePatterns_en_IE', 'goog.i18n.DateTimePatterns_en_IN', 'goog.i18n.DateTimePatterns_en_SG', 'goog.i18n.DateTimePatterns_en_US', 'goog.i18n.DateTimePatterns_en_ZA', 'goog.i18n.DateTimePatterns_es', 'goog.i18n.DateTimePatterns_et', 'goog.i18n.DateTimePatterns_eu', 'goog.i18n.DateTimePatterns_fa', 'goog.i18n.DateTimePatterns_fi', 'goog.i18n.DateTimePatterns_fil', 'goog.i18n.DateTimePatterns_fr', 'goog.i18n.DateTimePatterns_fr_CA', 'goog.i18n.DateTimePatterns_gl', 'goog.i18n.DateTimePatterns_gsw', 'goog.i18n.DateTimePatterns_gu', 'goog.i18n.DateTimePatterns_he', 'goog.i18n.DateTimePatterns_hi', 'goog.i18n.DateTimePatterns_hr', 'goog.i18n.DateTimePatterns_hu', 'goog.i18n.DateTimePatterns_id', 'goog.i18n.DateTimePatterns_in', 'goog.i18n.DateTimePatterns_is', 'goog.i18n.DateTimePatterns_it', 'goog.i18n.DateTimePatterns_iw', 'goog.i18n.DateTimePatterns_ja', 'goog.i18n.DateTimePatterns_kn', 'goog.i18n.DateTimePatterns_ko', 'goog.i18n.DateTimePatterns_ln', 'goog.i18n.DateTimePatterns_lt', 'goog.i18n.DateTimePatterns_lv', 'goog.i18n.DateTimePatterns_ml', 'goog.i18n.DateTimePatterns_mo', 'goog.i18n.DateTimePatterns_mr', 'goog.i18n.DateTimePatterns_ms', 'goog.i18n.DateTimePatterns_mt', 'goog.i18n.DateTimePatterns_nl', 'goog.i18n.DateTimePatterns_no', 'goog.i18n.DateTimePatterns_or', 'goog.i18n.DateTimePatterns_pl', 'goog.i18n.DateTimePatterns_pt', 'goog.i18n.DateTimePatterns_pt_BR', 'goog.i18n.DateTimePatterns_pt_PT', 'goog.i18n.DateTimePatterns_ro', 'goog.i18n.DateTimePatterns_ru', 'goog.i18n.DateTimePatterns_sk', 'goog.i18n.DateTimePatterns_sl', 'goog.i18n.DateTimePatterns_sq', 'goog.i18n.DateTimePatterns_sr', 'goog.i18n.DateTimePatterns_sv', 'goog.i18n.DateTimePatterns_sw', 'goog.i18n.DateTimePatterns_ta', 'goog.i18n.DateTimePatterns_te', 'goog.i18n.DateTimePatterns_th', 'goog.i18n.DateTimePatterns_tl', 'goog.i18n.DateTimePatterns_tr', 'goog.i18n.DateTimePatterns_uk', 'goog.i18n.DateTimePatterns_ur', 'goog.i18n.DateTimePatterns_vi', 'goog.i18n.DateTimePatterns_zh', 'goog.i18n.DateTimePatterns_zh_CN', 'goog.i18n.DateTimePatterns_zh_HK', 'goog.i18n.DateTimePatterns_zh_TW'], []);
goog.addDependency('i18n/datetimepatternsext.js', ['goog.i18n.DateTimePatternsExt', 'goog.i18n.DateTimePatterns_af', 'goog.i18n.DateTimePatterns_af_NA', 'goog.i18n.DateTimePatterns_af_ZA', 'goog.i18n.DateTimePatterns_ak', 'goog.i18n.DateTimePatterns_ak_GH', 'goog.i18n.DateTimePatterns_am_ET', 'goog.i18n.DateTimePatterns_ar_AE', 'goog.i18n.DateTimePatterns_ar_BH', 'goog.i18n.DateTimePatterns_ar_DZ', 'goog.i18n.DateTimePatterns_ar_EG', 'goog.i18n.DateTimePatterns_ar_IQ', 'goog.i18n.DateTimePatterns_ar_JO', 'goog.i18n.DateTimePatterns_ar_KW', 'goog.i18n.DateTimePatterns_ar_LB', 'goog.i18n.DateTimePatterns_ar_LY', 'goog.i18n.DateTimePatterns_ar_MA', 'goog.i18n.DateTimePatterns_ar_OM', 'goog.i18n.DateTimePatterns_ar_QA', 'goog.i18n.DateTimePatterns_ar_SA', 'goog.i18n.DateTimePatterns_ar_SD', 'goog.i18n.DateTimePatterns_ar_SY', 'goog.i18n.DateTimePatterns_ar_TN', 'goog.i18n.DateTimePatterns_ar_YE', 'goog.i18n.DateTimePatterns_as', 'goog.i18n.DateTimePatterns_as_IN', 'goog.i18n.DateTimePatterns_asa', 'goog.i18n.DateTimePatterns_asa_TZ', 'goog.i18n.DateTimePatterns_az', 'goog.i18n.DateTimePatterns_az_Cyrl', 'goog.i18n.DateTimePatterns_az_Cyrl_AZ', 'goog.i18n.DateTimePatterns_az_Latn', 'goog.i18n.DateTimePatterns_az_Latn_AZ', 'goog.i18n.DateTimePatterns_be', 'goog.i18n.DateTimePatterns_be_BY', 'goog.i18n.DateTimePatterns_bem', 'goog.i18n.DateTimePatterns_bem_ZM', 'goog.i18n.DateTimePatterns_bez', 'goog.i18n.DateTimePatterns_bez_TZ', 'goog.i18n.DateTimePatterns_bg_BG', 'goog.i18n.DateTimePatterns_bm', 'goog.i18n.DateTimePatterns_bm_ML', 'goog.i18n.DateTimePatterns_bn_BD', 'goog.i18n.DateTimePatterns_bn_IN', 'goog.i18n.DateTimePatterns_bo', 'goog.i18n.DateTimePatterns_bo_CN', 'goog.i18n.DateTimePatterns_bo_IN', 'goog.i18n.DateTimePatterns_bs', 'goog.i18n.DateTimePatterns_bs_BA', 'goog.i18n.DateTimePatterns_ca_ES', 'goog.i18n.DateTimePatterns_cgg', 'goog.i18n.DateTimePatterns_cgg_UG', 'goog.i18n.DateTimePatterns_chr', 'goog.i18n.DateTimePatterns_chr_US', 'goog.i18n.DateTimePatterns_cs_CZ', 'goog.i18n.DateTimePatterns_cy', 'goog.i18n.DateTimePatterns_cy_GB', 'goog.i18n.DateTimePatterns_da_DK', 'goog.i18n.DateTimePatterns_dav', 'goog.i18n.DateTimePatterns_dav_KE', 'goog.i18n.DateTimePatterns_de_BE', 'goog.i18n.DateTimePatterns_de_DE', 'goog.i18n.DateTimePatterns_de_LI', 'goog.i18n.DateTimePatterns_de_LU', 'goog.i18n.DateTimePatterns_ebu', 'goog.i18n.DateTimePatterns_ebu_KE', 'goog.i18n.DateTimePatterns_ee', 'goog.i18n.DateTimePatterns_ee_GH', 'goog.i18n.DateTimePatterns_ee_TG', 'goog.i18n.DateTimePatterns_el_CY', 'goog.i18n.DateTimePatterns_el_GR', 'goog.i18n.DateTimePatterns_en_AS', 'goog.i18n.DateTimePatterns_en_BE', 'goog.i18n.DateTimePatterns_en_BW', 'goog.i18n.DateTimePatterns_en_BZ', 'goog.i18n.DateTimePatterns_en_CA', 'goog.i18n.DateTimePatterns_en_GU', 'goog.i18n.DateTimePatterns_en_HK', 'goog.i18n.DateTimePatterns_en_JM', 'goog.i18n.DateTimePatterns_en_MH', 'goog.i18n.DateTimePatterns_en_MP', 'goog.i18n.DateTimePatterns_en_MT', 'goog.i18n.DateTimePatterns_en_MU', 'goog.i18n.DateTimePatterns_en_NA', 'goog.i18n.DateTimePatterns_en_NZ', 'goog.i18n.DateTimePatterns_en_PH', 'goog.i18n.DateTimePatterns_en_PK', 'goog.i18n.DateTimePatterns_en_TT', 'goog.i18n.DateTimePatterns_en_UM', 'goog.i18n.DateTimePatterns_en_US_POSIX', 'goog.i18n.DateTimePatterns_en_VI', 'goog.i18n.DateTimePatterns_en_ZW', 'goog.i18n.DateTimePatterns_eo', 'goog.i18n.DateTimePatterns_es_419', 'goog.i18n.DateTimePatterns_es_AR', 'goog.i18n.DateTimePatterns_es_BO', 'goog.i18n.DateTimePatterns_es_CL', 'goog.i18n.DateTimePatterns_es_CO', 'goog.i18n.DateTimePatterns_es_CR', 'goog.i18n.DateTimePatterns_es_DO', 'goog.i18n.DateTimePatterns_es_EC', 'goog.i18n.DateTimePatterns_es_ES', 'goog.i18n.DateTimePatterns_es_GQ', 'goog.i18n.DateTimePatterns_es_GT', 'goog.i18n.DateTimePatterns_es_HN', 'goog.i18n.DateTimePatterns_es_MX', 'goog.i18n.DateTimePatterns_es_NI', 'goog.i18n.DateTimePatterns_es_PA', 'goog.i18n.DateTimePatterns_es_PE', 'goog.i18n.DateTimePatterns_es_PR', 'goog.i18n.DateTimePatterns_es_PY', 'goog.i18n.DateTimePatterns_es_SV', 'goog.i18n.DateTimePatterns_es_US', 'goog.i18n.DateTimePatterns_es_UY', 'goog.i18n.DateTimePatterns_es_VE', 'goog.i18n.DateTimePatterns_et_EE', 'goog.i18n.DateTimePatterns_eu_ES', 'goog.i18n.DateTimePatterns_fa_AF', 'goog.i18n.DateTimePatterns_fa_IR', 'goog.i18n.DateTimePatterns_ff', 'goog.i18n.DateTimePatterns_ff_SN', 'goog.i18n.DateTimePatterns_fi_FI', 'goog.i18n.DateTimePatterns_fil_PH', 'goog.i18n.DateTimePatterns_fo', 'goog.i18n.DateTimePatterns_fo_FO', 'goog.i18n.DateTimePatterns_fr_BE', 'goog.i18n.DateTimePatterns_fr_BF', 'goog.i18n.DateTimePatterns_fr_BI', 'goog.i18n.DateTimePatterns_fr_BJ', 'goog.i18n.DateTimePatterns_fr_BL', 'goog.i18n.DateTimePatterns_fr_CD', 'goog.i18n.DateTimePatterns_fr_CF', 'goog.i18n.DateTimePatterns_fr_CG', 'goog.i18n.DateTimePatterns_fr_CH', 'goog.i18n.DateTimePatterns_fr_CI', 'goog.i18n.DateTimePatterns_fr_CM', 'goog.i18n.DateTimePatterns_fr_DJ', 'goog.i18n.DateTimePatterns_fr_FR', 'goog.i18n.DateTimePatterns_fr_GA', 'goog.i18n.DateTimePatterns_fr_GN', 'goog.i18n.DateTimePatterns_fr_GP', 'goog.i18n.DateTimePatterns_fr_GQ', 'goog.i18n.DateTimePatterns_fr_KM', 'goog.i18n.DateTimePatterns_fr_LU', 'goog.i18n.DateTimePatterns_fr_MC', 'goog.i18n.DateTimePatterns_fr_MF', 'goog.i18n.DateTimePatterns_fr_MG', 'goog.i18n.DateTimePatterns_fr_ML', 'goog.i18n.DateTimePatterns_fr_MQ', 'goog.i18n.DateTimePatterns_fr_NE', 'goog.i18n.DateTimePatterns_fr_RE', 'goog.i18n.DateTimePatterns_fr_RW', 'goog.i18n.DateTimePatterns_fr_SN', 'goog.i18n.DateTimePatterns_fr_TD', 'goog.i18n.DateTimePatterns_fr_TG', 'goog.i18n.DateTimePatterns_ga', 'goog.i18n.DateTimePatterns_ga_IE', 'goog.i18n.DateTimePatterns_gl_ES', 'goog.i18n.DateTimePatterns_gsw_CH', 'goog.i18n.DateTimePatterns_gu_IN', 'goog.i18n.DateTimePatterns_guz', 'goog.i18n.DateTimePatterns_guz_KE', 'goog.i18n.DateTimePatterns_gv', 'goog.i18n.DateTimePatterns_gv_GB', 'goog.i18n.DateTimePatterns_ha', 'goog.i18n.DateTimePatterns_ha_Latn', 'goog.i18n.DateTimePatterns_ha_Latn_GH', 'goog.i18n.DateTimePatterns_ha_Latn_NE', 'goog.i18n.DateTimePatterns_ha_Latn_NG', 'goog.i18n.DateTimePatterns_haw', 'goog.i18n.DateTimePatterns_haw_US', 'goog.i18n.DateTimePatterns_he_IL', 'goog.i18n.DateTimePatterns_hi_IN', 'goog.i18n.DateTimePatterns_hr_HR', 'goog.i18n.DateTimePatterns_hu_HU', 'goog.i18n.DateTimePatterns_hy', 'goog.i18n.DateTimePatterns_hy_AM', 'goog.i18n.DateTimePatterns_id_ID', 'goog.i18n.DateTimePatterns_ig', 'goog.i18n.DateTimePatterns_ig_NG', 'goog.i18n.DateTimePatterns_ii', 'goog.i18n.DateTimePatterns_ii_CN', 'goog.i18n.DateTimePatterns_is_IS', 'goog.i18n.DateTimePatterns_it_CH', 'goog.i18n.DateTimePatterns_it_IT', 'goog.i18n.DateTimePatterns_ja_JP', 'goog.i18n.DateTimePatterns_jmc', 'goog.i18n.DateTimePatterns_jmc_TZ', 'goog.i18n.DateTimePatterns_ka', 'goog.i18n.DateTimePatterns_ka_GE', 'goog.i18n.DateTimePatterns_kab', 'goog.i18n.DateTimePatterns_kab_DZ', 'goog.i18n.DateTimePatterns_kam', 'goog.i18n.DateTimePatterns_kam_KE', 'goog.i18n.DateTimePatterns_kde', 'goog.i18n.DateTimePatterns_kde_TZ', 'goog.i18n.DateTimePatterns_kea', 'goog.i18n.DateTimePatterns_kea_CV', 'goog.i18n.DateTimePatterns_khq', 'goog.i18n.DateTimePatterns_khq_ML', 'goog.i18n.DateTimePatterns_ki', 'goog.i18n.DateTimePatterns_ki_KE', 'goog.i18n.DateTimePatterns_kk', 'goog.i18n.DateTimePatterns_kk_Cyrl', 'goog.i18n.DateTimePatterns_kk_Cyrl_KZ', 'goog.i18n.DateTimePatterns_kl', 'goog.i18n.DateTimePatterns_kl_GL', 'goog.i18n.DateTimePatterns_kln', 'goog.i18n.DateTimePatterns_kln_KE', 'goog.i18n.DateTimePatterns_km', 'goog.i18n.DateTimePatterns_km_KH', 'goog.i18n.DateTimePatterns_kn_IN', 'goog.i18n.DateTimePatterns_ko_KR', 'goog.i18n.DateTimePatterns_kok', 'goog.i18n.DateTimePatterns_kok_IN', 'goog.i18n.DateTimePatterns_kw', 'goog.i18n.DateTimePatterns_kw_GB', 'goog.i18n.DateTimePatterns_lag', 'goog.i18n.DateTimePatterns_lag_TZ', 'goog.i18n.DateTimePatterns_lg', 'goog.i18n.DateTimePatterns_lg_UG', 'goog.i18n.DateTimePatterns_lt_LT', 'goog.i18n.DateTimePatterns_luo', 'goog.i18n.DateTimePatterns_luo_KE', 'goog.i18n.DateTimePatterns_luy', 'goog.i18n.DateTimePatterns_luy_KE', 'goog.i18n.DateTimePatterns_lv_LV', 'goog.i18n.DateTimePatterns_mas', 'goog.i18n.DateTimePatterns_mas_KE', 'goog.i18n.DateTimePatterns_mas_TZ', 'goog.i18n.DateTimePatterns_mer', 'goog.i18n.DateTimePatterns_mer_KE', 'goog.i18n.DateTimePatterns_mfe', 'goog.i18n.DateTimePatterns_mfe_MU', 'goog.i18n.DateTimePatterns_mg', 'goog.i18n.DateTimePatterns_mg_MG', 'goog.i18n.DateTimePatterns_mk', 'goog.i18n.DateTimePatterns_mk_MK', 'goog.i18n.DateTimePatterns_ml_IN', 'goog.i18n.DateTimePatterns_mr_IN', 'goog.i18n.DateTimePatterns_ms_BN', 'goog.i18n.DateTimePatterns_ms_MY', 'goog.i18n.DateTimePatterns_mt_MT', 'goog.i18n.DateTimePatterns_my', 'goog.i18n.DateTimePatterns_my_MM', 'goog.i18n.DateTimePatterns_naq', 'goog.i18n.DateTimePatterns_naq_NA', 'goog.i18n.DateTimePatterns_nb', 'goog.i18n.DateTimePatterns_nb_NO', 'goog.i18n.DateTimePatterns_nd', 'goog.i18n.DateTimePatterns_nd_ZW', 'goog.i18n.DateTimePatterns_ne', 'goog.i18n.DateTimePatterns_ne_IN', 'goog.i18n.DateTimePatterns_ne_NP', 'goog.i18n.DateTimePatterns_nl_BE', 'goog.i18n.DateTimePatterns_nl_NL', 'goog.i18n.DateTimePatterns_nn', 'goog.i18n.DateTimePatterns_nn_NO', 'goog.i18n.DateTimePatterns_nyn', 'goog.i18n.DateTimePatterns_nyn_UG', 'goog.i18n.DateTimePatterns_om', 'goog.i18n.DateTimePatterns_om_ET', 'goog.i18n.DateTimePatterns_om_KE', 'goog.i18n.DateTimePatterns_or_IN', 'goog.i18n.DateTimePatterns_pa', 'goog.i18n.DateTimePatterns_pa_Arab', 'goog.i18n.DateTimePatterns_pa_Arab_PK', 'goog.i18n.DateTimePatterns_pa_Guru', 'goog.i18n.DateTimePatterns_pa_Guru_IN', 'goog.i18n.DateTimePatterns_pl_PL', 'goog.i18n.DateTimePatterns_ps', 'goog.i18n.DateTimePatterns_ps_AF', 'goog.i18n.DateTimePatterns_pt_GW', 'goog.i18n.DateTimePatterns_pt_MZ', 'goog.i18n.DateTimePatterns_rm', 'goog.i18n.DateTimePatterns_rm_CH', 'goog.i18n.DateTimePatterns_ro_MD', 'goog.i18n.DateTimePatterns_ro_RO', 'goog.i18n.DateTimePatterns_rof', 'goog.i18n.DateTimePatterns_rof_TZ', 'goog.i18n.DateTimePatterns_ru_MD', 'goog.i18n.DateTimePatterns_ru_RU', 'goog.i18n.DateTimePatterns_ru_UA', 'goog.i18n.DateTimePatterns_rw', 'goog.i18n.DateTimePatterns_rw_RW', 'goog.i18n.DateTimePatterns_rwk', 'goog.i18n.DateTimePatterns_rwk_TZ', 'goog.i18n.DateTimePatterns_saq', 'goog.i18n.DateTimePatterns_saq_KE', 'goog.i18n.DateTimePatterns_seh', 'goog.i18n.DateTimePatterns_seh_MZ', 'goog.i18n.DateTimePatterns_ses', 'goog.i18n.DateTimePatterns_ses_ML', 'goog.i18n.DateTimePatterns_sg', 'goog.i18n.DateTimePatterns_sg_CF', 'goog.i18n.DateTimePatterns_shi', 'goog.i18n.DateTimePatterns_shi_Latn', 'goog.i18n.DateTimePatterns_shi_Latn_MA', 'goog.i18n.DateTimePatterns_shi_Tfng', 'goog.i18n.DateTimePatterns_shi_Tfng_MA', 'goog.i18n.DateTimePatterns_si', 'goog.i18n.DateTimePatterns_si_LK', 'goog.i18n.DateTimePatterns_sk_SK', 'goog.i18n.DateTimePatterns_sl_SI', 'goog.i18n.DateTimePatterns_sn', 'goog.i18n.DateTimePatterns_sn_ZW', 'goog.i18n.DateTimePatterns_so', 'goog.i18n.DateTimePatterns_so_DJ', 'goog.i18n.DateTimePatterns_so_ET', 'goog.i18n.DateTimePatterns_so_KE', 'goog.i18n.DateTimePatterns_so_SO', 'goog.i18n.DateTimePatterns_sq_AL', 'goog.i18n.DateTimePatterns_sr_Cyrl', 'goog.i18n.DateTimePatterns_sr_Cyrl_BA', 'goog.i18n.DateTimePatterns_sr_Cyrl_ME', 'goog.i18n.DateTimePatterns_sr_Cyrl_RS', 'goog.i18n.DateTimePatterns_sr_Latn', 'goog.i18n.DateTimePatterns_sr_Latn_BA', 'goog.i18n.DateTimePatterns_sr_Latn_ME', 'goog.i18n.DateTimePatterns_sr_Latn_RS', 'goog.i18n.DateTimePatterns_sv_FI', 'goog.i18n.DateTimePatterns_sv_SE', 'goog.i18n.DateTimePatterns_sw_KE', 'goog.i18n.DateTimePatterns_sw_TZ', 'goog.i18n.DateTimePatterns_ta_IN', 'goog.i18n.DateTimePatterns_ta_LK', 'goog.i18n.DateTimePatterns_te_IN', 'goog.i18n.DateTimePatterns_teo', 'goog.i18n.DateTimePatterns_teo_KE', 'goog.i18n.DateTimePatterns_teo_UG', 'goog.i18n.DateTimePatterns_th_TH', 'goog.i18n.DateTimePatterns_ti', 'goog.i18n.DateTimePatterns_ti_ER', 'goog.i18n.DateTimePatterns_ti_ET', 'goog.i18n.DateTimePatterns_to', 'goog.i18n.DateTimePatterns_to_TO', 'goog.i18n.DateTimePatterns_tr_TR', 'goog.i18n.DateTimePatterns_tzm', 'goog.i18n.DateTimePatterns_tzm_Latn', 'goog.i18n.DateTimePatterns_tzm_Latn_MA', 'goog.i18n.DateTimePatterns_uk_UA', 'goog.i18n.DateTimePatterns_ur_IN', 'goog.i18n.DateTimePatterns_ur_PK', 'goog.i18n.DateTimePatterns_uz', 'goog.i18n.DateTimePatterns_uz_Arab', 'goog.i18n.DateTimePatterns_uz_Arab_AF', 'goog.i18n.DateTimePatterns_uz_Cyrl', 'goog.i18n.DateTimePatterns_uz_Cyrl_UZ', 'goog.i18n.DateTimePatterns_uz_Latn', 'goog.i18n.DateTimePatterns_uz_Latn_UZ', 'goog.i18n.DateTimePatterns_vi_VN', 'goog.i18n.DateTimePatterns_vun', 'goog.i18n.DateTimePatterns_vun_TZ', 'goog.i18n.DateTimePatterns_xog', 'goog.i18n.DateTimePatterns_xog_UG', 'goog.i18n.DateTimePatterns_yo', 'goog.i18n.DateTimePatterns_yo_NG', 'goog.i18n.DateTimePatterns_zh_Hans', 'goog.i18n.DateTimePatterns_zh_Hans_CN', 'goog.i18n.DateTimePatterns_zh_Hans_HK', 'goog.i18n.DateTimePatterns_zh_Hans_MO', 'goog.i18n.DateTimePatterns_zh_Hans_SG', 'goog.i18n.DateTimePatterns_zh_Hant', 'goog.i18n.DateTimePatterns_zh_Hant_HK', 'goog.i18n.DateTimePatterns_zh_Hant_MO', 'goog.i18n.DateTimePatterns_zh_Hant_TW', 'goog.i18n.DateTimePatterns_zu', 'goog.i18n.DateTimePatterns_zu_ZA'], ['goog.i18n.DateTimePatterns']);
goog.addDependency('i18n/datetimesymbols.js', ['goog.i18n.DateTimeSymbols', 'goog.i18n.DateTimeSymbols_am', 'goog.i18n.DateTimeSymbols_ar', 'goog.i18n.DateTimeSymbols_bg', 'goog.i18n.DateTimeSymbols_bn', 'goog.i18n.DateTimeSymbols_ca', 'goog.i18n.DateTimeSymbols_cs', 'goog.i18n.DateTimeSymbols_da', 'goog.i18n.DateTimeSymbols_de', 'goog.i18n.DateTimeSymbols_de_AT', 'goog.i18n.DateTimeSymbols_de_CH', 'goog.i18n.DateTimeSymbols_el', 'goog.i18n.DateTimeSymbols_en', 'goog.i18n.DateTimeSymbols_en_AU', 'goog.i18n.DateTimeSymbols_en_GB', 'goog.i18n.DateTimeSymbols_en_IE', 'goog.i18n.DateTimeSymbols_en_IN', 'goog.i18n.DateTimeSymbols_en_ISO', 'goog.i18n.DateTimeSymbols_en_SG', 'goog.i18n.DateTimeSymbols_en_US', 'goog.i18n.DateTimeSymbols_en_ZA', 'goog.i18n.DateTimeSymbols_es', 'goog.i18n.DateTimeSymbols_es_419', 'goog.i18n.DateTimeSymbols_et', 'goog.i18n.DateTimeSymbols_eu', 'goog.i18n.DateTimeSymbols_fa', 'goog.i18n.DateTimeSymbols_fi', 'goog.i18n.DateTimeSymbols_fil', 'goog.i18n.DateTimeSymbols_fr', 'goog.i18n.DateTimeSymbols_fr_CA', 'goog.i18n.DateTimeSymbols_gl', 'goog.i18n.DateTimeSymbols_gsw', 'goog.i18n.DateTimeSymbols_gu', 'goog.i18n.DateTimeSymbols_he', 'goog.i18n.DateTimeSymbols_hi', 'goog.i18n.DateTimeSymbols_hr', 'goog.i18n.DateTimeSymbols_hu', 'goog.i18n.DateTimeSymbols_id', 'goog.i18n.DateTimeSymbols_in', 'goog.i18n.DateTimeSymbols_is', 'goog.i18n.DateTimeSymbols_it', 'goog.i18n.DateTimeSymbols_iw', 'goog.i18n.DateTimeSymbols_ja', 'goog.i18n.DateTimeSymbols_kn', 'goog.i18n.DateTimeSymbols_ko', 'goog.i18n.DateTimeSymbols_ln', 'goog.i18n.DateTimeSymbols_lt', 'goog.i18n.DateTimeSymbols_lv', 'goog.i18n.DateTimeSymbols_ml', 'goog.i18n.DateTimeSymbols_mr', 'goog.i18n.DateTimeSymbols_ms', 'goog.i18n.DateTimeSymbols_mt', 'goog.i18n.DateTimeSymbols_nl', 'goog.i18n.DateTimeSymbols_no', 'goog.i18n.DateTimeSymbols_or', 'goog.i18n.DateTimeSymbols_pl', 'goog.i18n.DateTimeSymbols_pt', 'goog.i18n.DateTimeSymbols_pt_BR', 'goog.i18n.DateTimeSymbols_pt_PT', 'goog.i18n.DateTimeSymbols_ro', 'goog.i18n.DateTimeSymbols_ru', 'goog.i18n.DateTimeSymbols_sk', 'goog.i18n.DateTimeSymbols_sl', 'goog.i18n.DateTimeSymbols_sq', 'goog.i18n.DateTimeSymbols_sr', 'goog.i18n.DateTimeSymbols_sv', 'goog.i18n.DateTimeSymbols_sw', 'goog.i18n.DateTimeSymbols_ta', 'goog.i18n.DateTimeSymbols_te', 'goog.i18n.DateTimeSymbols_th', 'goog.i18n.DateTimeSymbols_tl', 'goog.i18n.DateTimeSymbols_tr', 'goog.i18n.DateTimeSymbols_uk', 'goog.i18n.DateTimeSymbols_ur', 'goog.i18n.DateTimeSymbols_vi', 'goog.i18n.DateTimeSymbols_zh', 'goog.i18n.DateTimeSymbols_zh_CN', 'goog.i18n.DateTimeSymbols_zh_HK', 'goog.i18n.DateTimeSymbols_zh_TW'], []);
goog.addDependency('i18n/datetimesymbolsext.js', ['goog.i18n.DateTimeSymbolsExt', 'goog.i18n.DateTimeSymbols_aa', 'goog.i18n.DateTimeSymbols_aa_DJ', 'goog.i18n.DateTimeSymbols_aa_ER', 'goog.i18n.DateTimeSymbols_aa_ET', 'goog.i18n.DateTimeSymbols_af', 'goog.i18n.DateTimeSymbols_af_NA', 'goog.i18n.DateTimeSymbols_af_ZA', 'goog.i18n.DateTimeSymbols_ak', 'goog.i18n.DateTimeSymbols_ak_GH', 'goog.i18n.DateTimeSymbols_am_ET', 'goog.i18n.DateTimeSymbols_ar_AE', 'goog.i18n.DateTimeSymbols_ar_BH', 'goog.i18n.DateTimeSymbols_ar_DZ', 'goog.i18n.DateTimeSymbols_ar_EG', 'goog.i18n.DateTimeSymbols_ar_IQ', 'goog.i18n.DateTimeSymbols_ar_JO', 'goog.i18n.DateTimeSymbols_ar_KW', 'goog.i18n.DateTimeSymbols_ar_LB', 'goog.i18n.DateTimeSymbols_ar_LY', 'goog.i18n.DateTimeSymbols_ar_MA', 'goog.i18n.DateTimeSymbols_ar_OM', 'goog.i18n.DateTimeSymbols_ar_QA', 'goog.i18n.DateTimeSymbols_ar_SA', 'goog.i18n.DateTimeSymbols_ar_SD', 'goog.i18n.DateTimeSymbols_ar_SY', 'goog.i18n.DateTimeSymbols_ar_TN', 'goog.i18n.DateTimeSymbols_ar_YE', 'goog.i18n.DateTimeSymbols_as', 'goog.i18n.DateTimeSymbols_as_IN', 'goog.i18n.DateTimeSymbols_asa', 'goog.i18n.DateTimeSymbols_asa_TZ', 'goog.i18n.DateTimeSymbols_az', 'goog.i18n.DateTimeSymbols_az_Cyrl', 'goog.i18n.DateTimeSymbols_az_Cyrl_AZ', 'goog.i18n.DateTimeSymbols_az_Latn', 'goog.i18n.DateTimeSymbols_az_Latn_AZ', 'goog.i18n.DateTimeSymbols_be', 'goog.i18n.DateTimeSymbols_be_BY', 'goog.i18n.DateTimeSymbols_bem', 'goog.i18n.DateTimeSymbols_bem_ZM', 'goog.i18n.DateTimeSymbols_bez', 'goog.i18n.DateTimeSymbols_bez_TZ', 'goog.i18n.DateTimeSymbols_bg_BG', 'goog.i18n.DateTimeSymbols_bm', 'goog.i18n.DateTimeSymbols_bm_ML', 'goog.i18n.DateTimeSymbols_bn_BD', 'goog.i18n.DateTimeSymbols_bn_IN', 'goog.i18n.DateTimeSymbols_bo', 'goog.i18n.DateTimeSymbols_bo_CN', 'goog.i18n.DateTimeSymbols_bo_IN', 'goog.i18n.DateTimeSymbols_br', 'goog.i18n.DateTimeSymbols_br_FR', 'goog.i18n.DateTimeSymbols_brx', 'goog.i18n.DateTimeSymbols_brx_IN', 'goog.i18n.DateTimeSymbols_bs', 'goog.i18n.DateTimeSymbols_bs_BA', 'goog.i18n.DateTimeSymbols_byn', 'goog.i18n.DateTimeSymbols_byn_ER', 'goog.i18n.DateTimeSymbols_ca_ES', 'goog.i18n.DateTimeSymbols_cch', 'goog.i18n.DateTimeSymbols_cch_NG', 'goog.i18n.DateTimeSymbols_cgg', 'goog.i18n.DateTimeSymbols_cgg_UG', 'goog.i18n.DateTimeSymbols_chr', 'goog.i18n.DateTimeSymbols_chr_US', 'goog.i18n.DateTimeSymbols_ckb', 'goog.i18n.DateTimeSymbols_ckb_Arab', 'goog.i18n.DateTimeSymbols_ckb_Arab_IQ', 'goog.i18n.DateTimeSymbols_ckb_Arab_IR', 'goog.i18n.DateTimeSymbols_ckb_IQ', 'goog.i18n.DateTimeSymbols_ckb_IR', 'goog.i18n.DateTimeSymbols_ckb_Latn', 'goog.i18n.DateTimeSymbols_ckb_Latn_IQ', 'goog.i18n.DateTimeSymbols_cs_CZ', 'goog.i18n.DateTimeSymbols_cy', 'goog.i18n.DateTimeSymbols_cy_GB', 'goog.i18n.DateTimeSymbols_da_DK', 'goog.i18n.DateTimeSymbols_dav', 'goog.i18n.DateTimeSymbols_dav_KE', 'goog.i18n.DateTimeSymbols_de_BE', 'goog.i18n.DateTimeSymbols_de_DE', 'goog.i18n.DateTimeSymbols_de_LI', 'goog.i18n.DateTimeSymbols_de_LU', 'goog.i18n.DateTimeSymbols_dz', 'goog.i18n.DateTimeSymbols_dz_BT', 'goog.i18n.DateTimeSymbols_ebu', 'goog.i18n.DateTimeSymbols_ebu_KE', 'goog.i18n.DateTimeSymbols_ee', 'goog.i18n.DateTimeSymbols_ee_GH', 'goog.i18n.DateTimeSymbols_ee_TG', 'goog.i18n.DateTimeSymbols_el_CY', 'goog.i18n.DateTimeSymbols_el_GR', 'goog.i18n.DateTimeSymbols_el_POLYTON', 'goog.i18n.DateTimeSymbols_en_AS', 'goog.i18n.DateTimeSymbols_en_BE', 'goog.i18n.DateTimeSymbols_en_BW', 'goog.i18n.DateTimeSymbols_en_BZ', 'goog.i18n.DateTimeSymbols_en_CA', 'goog.i18n.DateTimeSymbols_en_Dsrt', 'goog.i18n.DateTimeSymbols_en_Dsrt_US', 'goog.i18n.DateTimeSymbols_en_GU', 'goog.i18n.DateTimeSymbols_en_HK', 'goog.i18n.DateTimeSymbols_en_JM', 'goog.i18n.DateTimeSymbols_en_MH', 'goog.i18n.DateTimeSymbols_en_MP', 'goog.i18n.DateTimeSymbols_en_MT', 'goog.i18n.DateTimeSymbols_en_MU', 'goog.i18n.DateTimeSymbols_en_NA', 'goog.i18n.DateTimeSymbols_en_NZ', 'goog.i18n.DateTimeSymbols_en_PH', 'goog.i18n.DateTimeSymbols_en_PK', 'goog.i18n.DateTimeSymbols_en_Shaw', 'goog.i18n.DateTimeSymbols_en_TT', 'goog.i18n.DateTimeSymbols_en_UM', 'goog.i18n.DateTimeSymbols_en_VI', 'goog.i18n.DateTimeSymbols_en_ZW', 'goog.i18n.DateTimeSymbols_eo', 'goog.i18n.DateTimeSymbols_es_AR', 'goog.i18n.DateTimeSymbols_es_BO', 'goog.i18n.DateTimeSymbols_es_CL', 'goog.i18n.DateTimeSymbols_es_CO', 'goog.i18n.DateTimeSymbols_es_CR', 'goog.i18n.DateTimeSymbols_es_DO', 'goog.i18n.DateTimeSymbols_es_EC', 'goog.i18n.DateTimeSymbols_es_ES', 'goog.i18n.DateTimeSymbols_es_GQ', 'goog.i18n.DateTimeSymbols_es_GT', 'goog.i18n.DateTimeSymbols_es_HN', 'goog.i18n.DateTimeSymbols_es_MX', 'goog.i18n.DateTimeSymbols_es_NI', 'goog.i18n.DateTimeSymbols_es_PA', 'goog.i18n.DateTimeSymbols_es_PE', 'goog.i18n.DateTimeSymbols_es_PR', 'goog.i18n.DateTimeSymbols_es_PY', 'goog.i18n.DateTimeSymbols_es_SV', 'goog.i18n.DateTimeSymbols_es_US', 'goog.i18n.DateTimeSymbols_es_UY', 'goog.i18n.DateTimeSymbols_es_VE', 'goog.i18n.DateTimeSymbols_et_EE', 'goog.i18n.DateTimeSymbols_eu_ES', 'goog.i18n.DateTimeSymbols_fa_AF', 'goog.i18n.DateTimeSymbols_fa_IR', 'goog.i18n.DateTimeSymbols_ff', 'goog.i18n.DateTimeSymbols_ff_SN', 'goog.i18n.DateTimeSymbols_fi_FI', 'goog.i18n.DateTimeSymbols_fil_PH', 'goog.i18n.DateTimeSymbols_fo', 'goog.i18n.DateTimeSymbols_fo_FO', 'goog.i18n.DateTimeSymbols_fr_BE', 'goog.i18n.DateTimeSymbols_fr_BF', 'goog.i18n.DateTimeSymbols_fr_BI', 'goog.i18n.DateTimeSymbols_fr_BJ', 'goog.i18n.DateTimeSymbols_fr_BL', 'goog.i18n.DateTimeSymbols_fr_CD', 'goog.i18n.DateTimeSymbols_fr_CF', 'goog.i18n.DateTimeSymbols_fr_CG', 'goog.i18n.DateTimeSymbols_fr_CH', 'goog.i18n.DateTimeSymbols_fr_CI', 'goog.i18n.DateTimeSymbols_fr_CM', 'goog.i18n.DateTimeSymbols_fr_DJ', 'goog.i18n.DateTimeSymbols_fr_FR', 'goog.i18n.DateTimeSymbols_fr_GA', 'goog.i18n.DateTimeSymbols_fr_GN', 'goog.i18n.DateTimeSymbols_fr_GP', 'goog.i18n.DateTimeSymbols_fr_GQ', 'goog.i18n.DateTimeSymbols_fr_KM', 'goog.i18n.DateTimeSymbols_fr_LU', 'goog.i18n.DateTimeSymbols_fr_MC', 'goog.i18n.DateTimeSymbols_fr_MF', 'goog.i18n.DateTimeSymbols_fr_MG', 'goog.i18n.DateTimeSymbols_fr_ML', 'goog.i18n.DateTimeSymbols_fr_MQ', 'goog.i18n.DateTimeSymbols_fr_NE', 'goog.i18n.DateTimeSymbols_fr_RE', 'goog.i18n.DateTimeSymbols_fr_RW', 'goog.i18n.DateTimeSymbols_fr_SN', 'goog.i18n.DateTimeSymbols_fr_TD', 'goog.i18n.DateTimeSymbols_fr_TG', 'goog.i18n.DateTimeSymbols_fur', 'goog.i18n.DateTimeSymbols_fur_IT', 'goog.i18n.DateTimeSymbols_ga', 'goog.i18n.DateTimeSymbols_ga_IE', 'goog.i18n.DateTimeSymbols_gaa', 'goog.i18n.DateTimeSymbols_gaa_GH', 'goog.i18n.DateTimeSymbols_gl_ES', 'goog.i18n.DateTimeSymbols_gsw_CH', 'goog.i18n.DateTimeSymbols_gu_IN', 'goog.i18n.DateTimeSymbols_guz', 'goog.i18n.DateTimeSymbols_guz_KE', 'goog.i18n.DateTimeSymbols_gv', 'goog.i18n.DateTimeSymbols_gv_GB', 'goog.i18n.DateTimeSymbols_ha', 'goog.i18n.DateTimeSymbols_ha_Latn', 'goog.i18n.DateTimeSymbols_ha_Latn_GH', 'goog.i18n.DateTimeSymbols_ha_Latn_NE', 'goog.i18n.DateTimeSymbols_ha_Latn_NG', 'goog.i18n.DateTimeSymbols_haw', 'goog.i18n.DateTimeSymbols_haw_US', 'goog.i18n.DateTimeSymbols_he_IL', 'goog.i18n.DateTimeSymbols_hi_IN', 'goog.i18n.DateTimeSymbols_hr_HR', 'goog.i18n.DateTimeSymbols_hu_HU', 'goog.i18n.DateTimeSymbols_hy', 'goog.i18n.DateTimeSymbols_hy_AM', 'goog.i18n.DateTimeSymbols_ia', 'goog.i18n.DateTimeSymbols_id_ID', 'goog.i18n.DateTimeSymbols_ig', 'goog.i18n.DateTimeSymbols_ig_NG', 'goog.i18n.DateTimeSymbols_ii', 'goog.i18n.DateTimeSymbols_ii_CN', 'goog.i18n.DateTimeSymbols_is_IS', 'goog.i18n.DateTimeSymbols_it_CH', 'goog.i18n.DateTimeSymbols_it_IT', 'goog.i18n.DateTimeSymbols_ja_JP', 'goog.i18n.DateTimeSymbols_jmc', 'goog.i18n.DateTimeSymbols_jmc_TZ', 'goog.i18n.DateTimeSymbols_ka', 'goog.i18n.DateTimeSymbols_ka_GE', 'goog.i18n.DateTimeSymbols_kab', 'goog.i18n.DateTimeSymbols_kab_DZ', 'goog.i18n.DateTimeSymbols_kaj', 'goog.i18n.DateTimeSymbols_kaj_NG', 'goog.i18n.DateTimeSymbols_kam', 'goog.i18n.DateTimeSymbols_kam_KE', 'goog.i18n.DateTimeSymbols_kcg', 'goog.i18n.DateTimeSymbols_kcg_NG', 'goog.i18n.DateTimeSymbols_kde', 'goog.i18n.DateTimeSymbols_kde_TZ', 'goog.i18n.DateTimeSymbols_kea', 'goog.i18n.DateTimeSymbols_kea_CV', 'goog.i18n.DateTimeSymbols_khq', 'goog.i18n.DateTimeSymbols_khq_ML', 'goog.i18n.DateTimeSymbols_ki', 'goog.i18n.DateTimeSymbols_ki_KE', 'goog.i18n.DateTimeSymbols_kk', 'goog.i18n.DateTimeSymbols_kk_Cyrl', 'goog.i18n.DateTimeSymbols_kk_Cyrl_KZ', 'goog.i18n.DateTimeSymbols_kl', 'goog.i18n.DateTimeSymbols_kl_GL', 'goog.i18n.DateTimeSymbols_kln', 'goog.i18n.DateTimeSymbols_kln_KE', 'goog.i18n.DateTimeSymbols_km', 'goog.i18n.DateTimeSymbols_km_KH', 'goog.i18n.DateTimeSymbols_kn_IN', 'goog.i18n.DateTimeSymbols_ko_KR', 'goog.i18n.DateTimeSymbols_kok', 'goog.i18n.DateTimeSymbols_kok_IN', 'goog.i18n.DateTimeSymbols_ksb', 'goog.i18n.DateTimeSymbols_ksb_TZ', 'goog.i18n.DateTimeSymbols_ksh', 'goog.i18n.DateTimeSymbols_ksh_DE', 'goog.i18n.DateTimeSymbols_ku', 'goog.i18n.DateTimeSymbols_ku_Arab', 'goog.i18n.DateTimeSymbols_ku_Arab_IQ', 'goog.i18n.DateTimeSymbols_ku_Arab_IR', 'goog.i18n.DateTimeSymbols_ku_Latn', 'goog.i18n.DateTimeSymbols_ku_Latn_SY', 'goog.i18n.DateTimeSymbols_ku_Latn_TR', 'goog.i18n.DateTimeSymbols_kw', 'goog.i18n.DateTimeSymbols_kw_GB', 'goog.i18n.DateTimeSymbols_ky', 'goog.i18n.DateTimeSymbols_ky_KG', 'goog.i18n.DateTimeSymbols_lag', 'goog.i18n.DateTimeSymbols_lag_TZ', 'goog.i18n.DateTimeSymbols_lg', 'goog.i18n.DateTimeSymbols_lg_UG', 'goog.i18n.DateTimeSymbols_ln_CD', 'goog.i18n.DateTimeSymbols_ln_CG', 'goog.i18n.DateTimeSymbols_lo', 'goog.i18n.DateTimeSymbols_lo_LA', 'goog.i18n.DateTimeSymbols_lt_LT', 'goog.i18n.DateTimeSymbols_luo', 'goog.i18n.DateTimeSymbols_luo_KE', 'goog.i18n.DateTimeSymbols_luy', 'goog.i18n.DateTimeSymbols_luy_KE', 'goog.i18n.DateTimeSymbols_lv_LV', 'goog.i18n.DateTimeSymbols_mas', 'goog.i18n.DateTimeSymbols_mas_KE', 'goog.i18n.DateTimeSymbols_mas_TZ', 'goog.i18n.DateTimeSymbols_mer', 'goog.i18n.DateTimeSymbols_mer_KE', 'goog.i18n.DateTimeSymbols_mfe', 'goog.i18n.DateTimeSymbols_mfe_MU', 'goog.i18n.DateTimeSymbols_mg', 'goog.i18n.DateTimeSymbols_mg_MG', 'goog.i18n.DateTimeSymbols_mk', 'goog.i18n.DateTimeSymbols_mk_MK', 'goog.i18n.DateTimeSymbols_ml_IN', 'goog.i18n.DateTimeSymbols_mn', 'goog.i18n.DateTimeSymbols_mn_Cyrl', 'goog.i18n.DateTimeSymbols_mn_Cyrl_MN', 'goog.i18n.DateTimeSymbols_mn_Mong', 'goog.i18n.DateTimeSymbols_mn_Mong_CN', 'goog.i18n.DateTimeSymbols_mr_IN', 'goog.i18n.DateTimeSymbols_ms_BN', 'goog.i18n.DateTimeSymbols_ms_MY', 'goog.i18n.DateTimeSymbols_mt_MT', 'goog.i18n.DateTimeSymbols_my', 'goog.i18n.DateTimeSymbols_my_MM', 'goog.i18n.DateTimeSymbols_naq', 'goog.i18n.DateTimeSymbols_naq_NA', 'goog.i18n.DateTimeSymbols_nb', 'goog.i18n.DateTimeSymbols_nb_NO', 'goog.i18n.DateTimeSymbols_nd', 'goog.i18n.DateTimeSymbols_nd_ZW', 'goog.i18n.DateTimeSymbols_nds', 'goog.i18n.DateTimeSymbols_nds_DE', 'goog.i18n.DateTimeSymbols_ne', 'goog.i18n.DateTimeSymbols_ne_IN', 'goog.i18n.DateTimeSymbols_ne_NP', 'goog.i18n.DateTimeSymbols_nl_BE', 'goog.i18n.DateTimeSymbols_nl_NL', 'goog.i18n.DateTimeSymbols_nn', 'goog.i18n.DateTimeSymbols_nn_NO', 'goog.i18n.DateTimeSymbols_nr', 'goog.i18n.DateTimeSymbols_nr_ZA', 'goog.i18n.DateTimeSymbols_nso', 'goog.i18n.DateTimeSymbols_nso_ZA', 'goog.i18n.DateTimeSymbols_nyn', 'goog.i18n.DateTimeSymbols_nyn_UG', 'goog.i18n.DateTimeSymbols_oc', 'goog.i18n.DateTimeSymbols_oc_FR', 'goog.i18n.DateTimeSymbols_om', 'goog.i18n.DateTimeSymbols_om_ET', 'goog.i18n.DateTimeSymbols_om_KE', 'goog.i18n.DateTimeSymbols_or_IN', 'goog.i18n.DateTimeSymbols_pa', 'goog.i18n.DateTimeSymbols_pa_Arab', 'goog.i18n.DateTimeSymbols_pa_Arab_PK', 'goog.i18n.DateTimeSymbols_pa_Guru', 'goog.i18n.DateTimeSymbols_pa_Guru_IN', 'goog.i18n.DateTimeSymbols_pl_PL', 'goog.i18n.DateTimeSymbols_ps', 'goog.i18n.DateTimeSymbols_ps_AF', 'goog.i18n.DateTimeSymbols_pt_AO', 'goog.i18n.DateTimeSymbols_pt_GW', 'goog.i18n.DateTimeSymbols_pt_MZ', 'goog.i18n.DateTimeSymbols_rm', 'goog.i18n.DateTimeSymbols_rm_CH', 'goog.i18n.DateTimeSymbols_ro_MD', 'goog.i18n.DateTimeSymbols_ro_RO', 'goog.i18n.DateTimeSymbols_rof', 'goog.i18n.DateTimeSymbols_rof_TZ', 'goog.i18n.DateTimeSymbols_ru_MD', 'goog.i18n.DateTimeSymbols_ru_RU', 'goog.i18n.DateTimeSymbols_ru_UA', 'goog.i18n.DateTimeSymbols_rw', 'goog.i18n.DateTimeSymbols_rw_RW', 'goog.i18n.DateTimeSymbols_rwk', 'goog.i18n.DateTimeSymbols_rwk_TZ', 'goog.i18n.DateTimeSymbols_saq', 'goog.i18n.DateTimeSymbols_saq_KE', 'goog.i18n.DateTimeSymbols_se', 'goog.i18n.DateTimeSymbols_se_FI', 'goog.i18n.DateTimeSymbols_se_NO', 'goog.i18n.DateTimeSymbols_seh', 'goog.i18n.DateTimeSymbols_seh_MZ', 'goog.i18n.DateTimeSymbols_ses', 'goog.i18n.DateTimeSymbols_ses_ML', 'goog.i18n.DateTimeSymbols_sg', 'goog.i18n.DateTimeSymbols_sg_CF', 'goog.i18n.DateTimeSymbols_shi', 'goog.i18n.DateTimeSymbols_shi_Latn', 'goog.i18n.DateTimeSymbols_shi_Latn_MA', 'goog.i18n.DateTimeSymbols_shi_Tfng', 'goog.i18n.DateTimeSymbols_shi_Tfng_MA', 'goog.i18n.DateTimeSymbols_si', 'goog.i18n.DateTimeSymbols_si_LK', 'goog.i18n.DateTimeSymbols_sid', 'goog.i18n.DateTimeSymbols_sid_ET', 'goog.i18n.DateTimeSymbols_sk_SK', 'goog.i18n.DateTimeSymbols_sl_SI', 'goog.i18n.DateTimeSymbols_sn', 'goog.i18n.DateTimeSymbols_sn_ZW', 'goog.i18n.DateTimeSymbols_so', 'goog.i18n.DateTimeSymbols_so_DJ', 'goog.i18n.DateTimeSymbols_so_ET', 'goog.i18n.DateTimeSymbols_so_KE', 'goog.i18n.DateTimeSymbols_so_SO', 'goog.i18n.DateTimeSymbols_sq_AL', 'goog.i18n.DateTimeSymbols_sr_Cyrl', 'goog.i18n.DateTimeSymbols_sr_Cyrl_BA', 'goog.i18n.DateTimeSymbols_sr_Cyrl_ME', 'goog.i18n.DateTimeSymbols_sr_Cyrl_RS', 'goog.i18n.DateTimeSymbols_sr_Latn', 'goog.i18n.DateTimeSymbols_sr_Latn_BA', 'goog.i18n.DateTimeSymbols_sr_Latn_ME', 'goog.i18n.DateTimeSymbols_sr_Latn_RS', 'goog.i18n.DateTimeSymbols_ss', 'goog.i18n.DateTimeSymbols_ss_SZ', 'goog.i18n.DateTimeSymbols_ss_ZA', 'goog.i18n.DateTimeSymbols_ssy', 'goog.i18n.DateTimeSymbols_ssy_ER', 'goog.i18n.DateTimeSymbols_st', 'goog.i18n.DateTimeSymbols_st_LS', 'goog.i18n.DateTimeSymbols_st_ZA', 'goog.i18n.DateTimeSymbols_sv_FI', 'goog.i18n.DateTimeSymbols_sv_SE', 'goog.i18n.DateTimeSymbols_sw_KE', 'goog.i18n.DateTimeSymbols_sw_TZ', 'goog.i18n.DateTimeSymbols_ta_IN', 'goog.i18n.DateTimeSymbols_ta_LK', 'goog.i18n.DateTimeSymbols_te_IN', 'goog.i18n.DateTimeSymbols_teo', 'goog.i18n.DateTimeSymbols_teo_KE', 'goog.i18n.DateTimeSymbols_teo_UG', 'goog.i18n.DateTimeSymbols_tg', 'goog.i18n.DateTimeSymbols_tg_Cyrl', 'goog.i18n.DateTimeSymbols_tg_Cyrl_TJ', 'goog.i18n.DateTimeSymbols_th_TH', 'goog.i18n.DateTimeSymbols_ti', 'goog.i18n.DateTimeSymbols_ti_ER', 'goog.i18n.DateTimeSymbols_ti_ET', 'goog.i18n.DateTimeSymbols_tig', 'goog.i18n.DateTimeSymbols_tig_ER', 'goog.i18n.DateTimeSymbols_tn', 'goog.i18n.DateTimeSymbols_tn_ZA', 'goog.i18n.DateTimeSymbols_to', 'goog.i18n.DateTimeSymbols_to_TO', 'goog.i18n.DateTimeSymbols_tr_TR', 'goog.i18n.DateTimeSymbols_trv', 'goog.i18n.DateTimeSymbols_trv_TW', 'goog.i18n.DateTimeSymbols_ts', 'goog.i18n.DateTimeSymbols_ts_ZA', 'goog.i18n.DateTimeSymbols_tzm', 'goog.i18n.DateTimeSymbols_tzm_Latn', 'goog.i18n.DateTimeSymbols_tzm_Latn_MA', 'goog.i18n.DateTimeSymbols_uk_UA', 'goog.i18n.DateTimeSymbols_ur_IN', 'goog.i18n.DateTimeSymbols_ur_PK', 'goog.i18n.DateTimeSymbols_uz', 'goog.i18n.DateTimeSymbols_uz_Arab', 'goog.i18n.DateTimeSymbols_uz_Arab_AF', 'goog.i18n.DateTimeSymbols_uz_Cyrl', 'goog.i18n.DateTimeSymbols_uz_Cyrl_UZ', 'goog.i18n.DateTimeSymbols_uz_Latn', 'goog.i18n.DateTimeSymbols_uz_Latn_UZ', 'goog.i18n.DateTimeSymbols_ve', 'goog.i18n.DateTimeSymbols_ve_ZA', 'goog.i18n.DateTimeSymbols_vi_VN', 'goog.i18n.DateTimeSymbols_vun', 'goog.i18n.DateTimeSymbols_vun_TZ', 'goog.i18n.DateTimeSymbols_wal', 'goog.i18n.DateTimeSymbols_wal_ET', 'goog.i18n.DateTimeSymbols_xh', 'goog.i18n.DateTimeSymbols_xh_ZA', 'goog.i18n.DateTimeSymbols_xog', 'goog.i18n.DateTimeSymbols_xog_UG', 'goog.i18n.DateTimeSymbols_yo', 'goog.i18n.DateTimeSymbols_yo_NG', 'goog.i18n.DateTimeSymbols_zh_Hans', 'goog.i18n.DateTimeSymbols_zh_Hans_CN', 'goog.i18n.DateTimeSymbols_zh_Hans_HK', 'goog.i18n.DateTimeSymbols_zh_Hans_MO', 'goog.i18n.DateTimeSymbols_zh_Hans_SG', 'goog.i18n.DateTimeSymbols_zh_Hant', 'goog.i18n.DateTimeSymbols_zh_Hant_HK', 'goog.i18n.DateTimeSymbols_zh_Hant_MO', 'goog.i18n.DateTimeSymbols_zh_Hant_TW', 'goog.i18n.DateTimeSymbols_zu', 'goog.i18n.DateTimeSymbols_zu_ZA'], ['goog.i18n.DateTimeSymbols']);
goog.addDependency('i18n/graphemebreak.js', ['goog.i18n.GraphemeBreak'], ['goog.structs.InversionMap']);
goog.addDependency('i18n/messageformat.js', ['goog.i18n.MessageFormat'], ['goog.asserts', 'goog.i18n.NumberFormat', 'goog.i18n.pluralRules']);
goog.addDependency('i18n/mime.js', ['goog.i18n.mime', 'goog.i18n.mime.encode'], []);
goog.addDependency('i18n/numberformat.js', ['goog.i18n.NumberFormat', 'goog.i18n.NumberFormat.CurrencyStyle', 'goog.i18n.NumberFormat.Format'], ['goog.i18n.NumberFormatSymbols', 'goog.i18n.currency']);
goog.addDependency('i18n/numberformatsymbols.js', ['goog.i18n.NumberFormatSymbols', 'goog.i18n.NumberFormatSymbols_am', 'goog.i18n.NumberFormatSymbols_am_ET', 'goog.i18n.NumberFormatSymbols_ar', 'goog.i18n.NumberFormatSymbols_ar_EG', 'goog.i18n.NumberFormatSymbols_bg', 'goog.i18n.NumberFormatSymbols_bg_BG', 'goog.i18n.NumberFormatSymbols_bn', 'goog.i18n.NumberFormatSymbols_bn_BD', 'goog.i18n.NumberFormatSymbols_ca', 'goog.i18n.NumberFormatSymbols_ca_ES', 'goog.i18n.NumberFormatSymbols_cs', 'goog.i18n.NumberFormatSymbols_cs_CZ', 'goog.i18n.NumberFormatSymbols_da', 'goog.i18n.NumberFormatSymbols_da_DK', 'goog.i18n.NumberFormatSymbols_de', 'goog.i18n.NumberFormatSymbols_de_AT', 'goog.i18n.NumberFormatSymbols_de_BE', 'goog.i18n.NumberFormatSymbols_de_CH', 'goog.i18n.NumberFormatSymbols_de_DE', 'goog.i18n.NumberFormatSymbols_de_LU', 'goog.i18n.NumberFormatSymbols_el', 'goog.i18n.NumberFormatSymbols_el_GR', 'goog.i18n.NumberFormatSymbols_el_POLYTON', 'goog.i18n.NumberFormatSymbols_en', 'goog.i18n.NumberFormatSymbols_en_AS', 'goog.i18n.NumberFormatSymbols_en_AU', 'goog.i18n.NumberFormatSymbols_en_Dsrt', 'goog.i18n.NumberFormatSymbols_en_Dsrt_US', 'goog.i18n.NumberFormatSymbols_en_GB', 'goog.i18n.NumberFormatSymbols_en_GU', 'goog.i18n.NumberFormatSymbols_en_IE', 'goog.i18n.NumberFormatSymbols_en_IN', 'goog.i18n.NumberFormatSymbols_en_MH', 'goog.i18n.NumberFormatSymbols_en_MP', 'goog.i18n.NumberFormatSymbols_en_SG', 'goog.i18n.NumberFormatSymbols_en_UM', 'goog.i18n.NumberFormatSymbols_en_US', 'goog.i18n.NumberFormatSymbols_en_VI', 'goog.i18n.NumberFormatSymbols_en_ZA', 'goog.i18n.NumberFormatSymbols_en_ZZ', 'goog.i18n.NumberFormatSymbols_es', 'goog.i18n.NumberFormatSymbols_es_ES', 'goog.i18n.NumberFormatSymbols_et', 'goog.i18n.NumberFormatSymbols_et_EE', 'goog.i18n.NumberFormatSymbols_eu', 'goog.i18n.NumberFormatSymbols_eu_ES', 'goog.i18n.NumberFormatSymbols_fa', 'goog.i18n.NumberFormatSymbols_fa_IR', 'goog.i18n.NumberFormatSymbols_fi', 'goog.i18n.NumberFormatSymbols_fi_FI', 'goog.i18n.NumberFormatSymbols_fil', 'goog.i18n.NumberFormatSymbols_fil_PH', 'goog.i18n.NumberFormatSymbols_fr', 'goog.i18n.NumberFormatSymbols_fr_BL', 'goog.i18n.NumberFormatSymbols_fr_CA', 'goog.i18n.NumberFormatSymbols_fr_FR', 'goog.i18n.NumberFormatSymbols_fr_GP', 'goog.i18n.NumberFormatSymbols_fr_MC', 'goog.i18n.NumberFormatSymbols_fr_MF', 'goog.i18n.NumberFormatSymbols_fr_MQ', 'goog.i18n.NumberFormatSymbols_fr_RE', 'goog.i18n.NumberFormatSymbols_gl', 'goog.i18n.NumberFormatSymbols_gl_ES', 'goog.i18n.NumberFormatSymbols_gsw', 'goog.i18n.NumberFormatSymbols_gsw_CH', 'goog.i18n.NumberFormatSymbols_gu', 'goog.i18n.NumberFormatSymbols_gu_IN', 'goog.i18n.NumberFormatSymbols_he', 'goog.i18n.NumberFormatSymbols_he_IL', 'goog.i18n.NumberFormatSymbols_hi', 'goog.i18n.NumberFormatSymbols_hi_IN', 'goog.i18n.NumberFormatSymbols_hr', 'goog.i18n.NumberFormatSymbols_hr_HR', 'goog.i18n.NumberFormatSymbols_hu', 'goog.i18n.NumberFormatSymbols_hu_HU', 'goog.i18n.NumberFormatSymbols_id', 'goog.i18n.NumberFormatSymbols_id_ID', 'goog.i18n.NumberFormatSymbols_in', 'goog.i18n.NumberFormatSymbols_is', 'goog.i18n.NumberFormatSymbols_is_IS', 'goog.i18n.NumberFormatSymbols_it', 'goog.i18n.NumberFormatSymbols_it_IT', 'goog.i18n.NumberFormatSymbols_iw', 'goog.i18n.NumberFormatSymbols_ja', 'goog.i18n.NumberFormatSymbols_ja_JP', 'goog.i18n.NumberFormatSymbols_kn', 'goog.i18n.NumberFormatSymbols_kn_IN', 'goog.i18n.NumberFormatSymbols_ko', 'goog.i18n.NumberFormatSymbols_ko_KR', 'goog.i18n.NumberFormatSymbols_ln', 'goog.i18n.NumberFormatSymbols_ln_CD', 'goog.i18n.NumberFormatSymbols_lt', 'goog.i18n.NumberFormatSymbols_lt_LT', 'goog.i18n.NumberFormatSymbols_lv', 'goog.i18n.NumberFormatSymbols_lv_LV', 'goog.i18n.NumberFormatSymbols_ml', 'goog.i18n.NumberFormatSymbols_ml_IN', 'goog.i18n.NumberFormatSymbols_mo', 'goog.i18n.NumberFormatSymbols_mr', 'goog.i18n.NumberFormatSymbols_mr_IN', 'goog.i18n.NumberFormatSymbols_ms', 'goog.i18n.NumberFormatSymbols_ms_MY', 'goog.i18n.NumberFormatSymbols_mt', 'goog.i18n.NumberFormatSymbols_mt_MT', 'goog.i18n.NumberFormatSymbols_nl', 'goog.i18n.NumberFormatSymbols_nl_NL', 'goog.i18n.NumberFormatSymbols_no', 'goog.i18n.NumberFormatSymbols_or', 'goog.i18n.NumberFormatSymbols_or_IN', 'goog.i18n.NumberFormatSymbols_pl', 'goog.i18n.NumberFormatSymbols_pl_PL', 'goog.i18n.NumberFormatSymbols_pt', 'goog.i18n.NumberFormatSymbols_pt_BR', 'goog.i18n.NumberFormatSymbols_pt_PT', 'goog.i18n.NumberFormatSymbols_ro', 'goog.i18n.NumberFormatSymbols_ro_RO', 'goog.i18n.NumberFormatSymbols_ru', 'goog.i18n.NumberFormatSymbols_ru_RU', 'goog.i18n.NumberFormatSymbols_sk', 'goog.i18n.NumberFormatSymbols_sk_SK', 'goog.i18n.NumberFormatSymbols_sl', 'goog.i18n.NumberFormatSymbols_sl_SI', 'goog.i18n.NumberFormatSymbols_sq', 'goog.i18n.NumberFormatSymbols_sq_AL', 'goog.i18n.NumberFormatSymbols_sr', 'goog.i18n.NumberFormatSymbols_sr_Cyrl_RS', 'goog.i18n.NumberFormatSymbols_sr_Latn_RS', 'goog.i18n.NumberFormatSymbols_sr_RS', 'goog.i18n.NumberFormatSymbols_sv', 'goog.i18n.NumberFormatSymbols_sv_SE', 'goog.i18n.NumberFormatSymbols_sw', 'goog.i18n.NumberFormatSymbols_sw_TZ', 'goog.i18n.NumberFormatSymbols_ta', 'goog.i18n.NumberFormatSymbols_ta_IN', 'goog.i18n.NumberFormatSymbols_te', 'goog.i18n.NumberFormatSymbols_te_IN', 'goog.i18n.NumberFormatSymbols_th', 'goog.i18n.NumberFormatSymbols_th_TH', 'goog.i18n.NumberFormatSymbols_tl', 'goog.i18n.NumberFormatSymbols_tl_PH', 'goog.i18n.NumberFormatSymbols_tr', 'goog.i18n.NumberFormatSymbols_tr_TR', 'goog.i18n.NumberFormatSymbols_uk', 'goog.i18n.NumberFormatSymbols_uk_UA', 'goog.i18n.NumberFormatSymbols_ur', 'goog.i18n.NumberFormatSymbols_ur_PK', 'goog.i18n.NumberFormatSymbols_vi', 'goog.i18n.NumberFormatSymbols_vi_VN', 'goog.i18n.NumberFormatSymbols_zh', 'goog.i18n.NumberFormatSymbols_zh_CN', 'goog.i18n.NumberFormatSymbols_zh_HK', 'goog.i18n.NumberFormatSymbols_zh_Hans', 'goog.i18n.NumberFormatSymbols_zh_Hans_CN', 'goog.i18n.NumberFormatSymbols_zh_TW'], []);
goog.addDependency('i18n/numberformatsymbolsext.js', ['goog.i18n.NumberFormatSymbolsExt', 'goog.i18n.NumberFormatSymbols_aa', 'goog.i18n.NumberFormatSymbols_aa_DJ', 'goog.i18n.NumberFormatSymbols_aa_ER', 'goog.i18n.NumberFormatSymbols_aa_ET', 'goog.i18n.NumberFormatSymbols_af', 'goog.i18n.NumberFormatSymbols_af_NA', 'goog.i18n.NumberFormatSymbols_af_ZA', 'goog.i18n.NumberFormatSymbols_ak', 'goog.i18n.NumberFormatSymbols_ak_GH', 'goog.i18n.NumberFormatSymbols_ar_AE', 'goog.i18n.NumberFormatSymbols_ar_BH', 'goog.i18n.NumberFormatSymbols_ar_DZ', 'goog.i18n.NumberFormatSymbols_ar_IQ', 'goog.i18n.NumberFormatSymbols_ar_JO', 'goog.i18n.NumberFormatSymbols_ar_KW', 'goog.i18n.NumberFormatSymbols_ar_LB', 'goog.i18n.NumberFormatSymbols_ar_LY', 'goog.i18n.NumberFormatSymbols_ar_MA', 'goog.i18n.NumberFormatSymbols_ar_OM', 'goog.i18n.NumberFormatSymbols_ar_QA', 'goog.i18n.NumberFormatSymbols_ar_SA', 'goog.i18n.NumberFormatSymbols_ar_SD', 'goog.i18n.NumberFormatSymbols_ar_SY', 'goog.i18n.NumberFormatSymbols_ar_TN', 'goog.i18n.NumberFormatSymbols_ar_YE', 'goog.i18n.NumberFormatSymbols_as', 'goog.i18n.NumberFormatSymbols_as_IN', 'goog.i18n.NumberFormatSymbols_asa', 'goog.i18n.NumberFormatSymbols_asa_TZ', 'goog.i18n.NumberFormatSymbols_az', 'goog.i18n.NumberFormatSymbols_az_AZ', 'goog.i18n.NumberFormatSymbols_az_Arab', 'goog.i18n.NumberFormatSymbols_az_Arab_IR', 'goog.i18n.NumberFormatSymbols_az_Cyrl', 'goog.i18n.NumberFormatSymbols_az_Cyrl_AZ', 'goog.i18n.NumberFormatSymbols_az_IR', 'goog.i18n.NumberFormatSymbols_az_Latn', 'goog.i18n.NumberFormatSymbols_az_Latn_AZ', 'goog.i18n.NumberFormatSymbols_be', 'goog.i18n.NumberFormatSymbols_be_BY', 'goog.i18n.NumberFormatSymbols_bem', 'goog.i18n.NumberFormatSymbols_bem_ZM', 'goog.i18n.NumberFormatSymbols_bez', 'goog.i18n.NumberFormatSymbols_bez_TZ', 'goog.i18n.NumberFormatSymbols_bm', 'goog.i18n.NumberFormatSymbols_bm_ML', 'goog.i18n.NumberFormatSymbols_bn_IN', 'goog.i18n.NumberFormatSymbols_bo', 'goog.i18n.NumberFormatSymbols_bo_CN', 'goog.i18n.NumberFormatSymbols_bo_IN', 'goog.i18n.NumberFormatSymbols_br', 'goog.i18n.NumberFormatSymbols_br_FR', 'goog.i18n.NumberFormatSymbols_brx', 'goog.i18n.NumberFormatSymbols_brx_IN', 'goog.i18n.NumberFormatSymbols_bs', 'goog.i18n.NumberFormatSymbols_bs_BA', 'goog.i18n.NumberFormatSymbols_byn', 'goog.i18n.NumberFormatSymbols_byn_ER', 'goog.i18n.NumberFormatSymbols_cch', 'goog.i18n.NumberFormatSymbols_cch_NG', 'goog.i18n.NumberFormatSymbols_cgg', 'goog.i18n.NumberFormatSymbols_cgg_UG', 'goog.i18n.NumberFormatSymbols_chr', 'goog.i18n.NumberFormatSymbols_chr_US', 'goog.i18n.NumberFormatSymbols_ckb', 'goog.i18n.NumberFormatSymbols_ckb_Arab', 'goog.i18n.NumberFormatSymbols_ckb_Arab_IQ', 'goog.i18n.NumberFormatSymbols_ckb_Arab_IR', 'goog.i18n.NumberFormatSymbols_ckb_IQ', 'goog.i18n.NumberFormatSymbols_ckb_IR', 'goog.i18n.NumberFormatSymbols_ckb_Latn', 'goog.i18n.NumberFormatSymbols_ckb_Latn_IQ', 'goog.i18n.NumberFormatSymbols_cy', 'goog.i18n.NumberFormatSymbols_cy_GB', 'goog.i18n.NumberFormatSymbols_dav', 'goog.i18n.NumberFormatSymbols_dav_KE', 'goog.i18n.NumberFormatSymbols_de_LI', 'goog.i18n.NumberFormatSymbols_dv', 'goog.i18n.NumberFormatSymbols_dv_MV', 'goog.i18n.NumberFormatSymbols_dz', 'goog.i18n.NumberFormatSymbols_dz_BT', 'goog.i18n.NumberFormatSymbols_ebu', 'goog.i18n.NumberFormatSymbols_ebu_KE', 'goog.i18n.NumberFormatSymbols_ee', 'goog.i18n.NumberFormatSymbols_ee_GH', 'goog.i18n.NumberFormatSymbols_ee_TG', 'goog.i18n.NumberFormatSymbols_el_CY', 'goog.i18n.NumberFormatSymbols_en_BE', 'goog.i18n.NumberFormatSymbols_en_BW', 'goog.i18n.NumberFormatSymbols_en_BZ', 'goog.i18n.NumberFormatSymbols_en_CA', 'goog.i18n.NumberFormatSymbols_en_HK', 'goog.i18n.NumberFormatSymbols_en_JM', 'goog.i18n.NumberFormatSymbols_en_MT', 'goog.i18n.NumberFormatSymbols_en_MU', 'goog.i18n.NumberFormatSymbols_en_NA', 'goog.i18n.NumberFormatSymbols_en_NZ', 'goog.i18n.NumberFormatSymbols_en_PH', 'goog.i18n.NumberFormatSymbols_en_PK', 'goog.i18n.NumberFormatSymbols_en_Shaw', 'goog.i18n.NumberFormatSymbols_en_TT', 'goog.i18n.NumberFormatSymbols_en_ZW', 'goog.i18n.NumberFormatSymbols_eo', 'goog.i18n.NumberFormatSymbols_es_419', 'goog.i18n.NumberFormatSymbols_es_AR', 'goog.i18n.NumberFormatSymbols_es_BO', 'goog.i18n.NumberFormatSymbols_es_CL', 'goog.i18n.NumberFormatSymbols_es_CO', 'goog.i18n.NumberFormatSymbols_es_CR', 'goog.i18n.NumberFormatSymbols_es_DO', 'goog.i18n.NumberFormatSymbols_es_EC', 'goog.i18n.NumberFormatSymbols_es_GQ', 'goog.i18n.NumberFormatSymbols_es_GT', 'goog.i18n.NumberFormatSymbols_es_HN', 'goog.i18n.NumberFormatSymbols_es_MX', 'goog.i18n.NumberFormatSymbols_es_NI', 'goog.i18n.NumberFormatSymbols_es_PA', 'goog.i18n.NumberFormatSymbols_es_PE', 'goog.i18n.NumberFormatSymbols_es_PR', 'goog.i18n.NumberFormatSymbols_es_PY', 'goog.i18n.NumberFormatSymbols_es_SV', 'goog.i18n.NumberFormatSymbols_es_US', 'goog.i18n.NumberFormatSymbols_es_UY', 'goog.i18n.NumberFormatSymbols_es_VE', 'goog.i18n.NumberFormatSymbols_fa_AF', 'goog.i18n.NumberFormatSymbols_ff', 'goog.i18n.NumberFormatSymbols_ff_SN', 'goog.i18n.NumberFormatSymbols_fo', 'goog.i18n.NumberFormatSymbols_fo_FO', 'goog.i18n.NumberFormatSymbols_fr_BE', 'goog.i18n.NumberFormatSymbols_fr_BF', 'goog.i18n.NumberFormatSymbols_fr_BI', 'goog.i18n.NumberFormatSymbols_fr_BJ', 'goog.i18n.NumberFormatSymbols_fr_CD', 'goog.i18n.NumberFormatSymbols_fr_CF', 'goog.i18n.NumberFormatSymbols_fr_CG', 'goog.i18n.NumberFormatSymbols_fr_CH', 'goog.i18n.NumberFormatSymbols_fr_CI', 'goog.i18n.NumberFormatSymbols_fr_CM', 'goog.i18n.NumberFormatSymbols_fr_DJ', 'goog.i18n.NumberFormatSymbols_fr_GA', 'goog.i18n.NumberFormatSymbols_fr_GN', 'goog.i18n.NumberFormatSymbols_fr_GQ', 'goog.i18n.NumberFormatSymbols_fr_KM', 'goog.i18n.NumberFormatSymbols_fr_LU', 'goog.i18n.NumberFormatSymbols_fr_MG', 'goog.i18n.NumberFormatSymbols_fr_ML', 'goog.i18n.NumberFormatSymbols_fr_NE', 'goog.i18n.NumberFormatSymbols_fr_RW', 'goog.i18n.NumberFormatSymbols_fr_SN', 'goog.i18n.NumberFormatSymbols_fr_TD', 'goog.i18n.NumberFormatSymbols_fr_TG', 'goog.i18n.NumberFormatSymbols_fur', 'goog.i18n.NumberFormatSymbols_fur_IT', 'goog.i18n.NumberFormatSymbols_ga', 'goog.i18n.NumberFormatSymbols_ga_IE', 'goog.i18n.NumberFormatSymbols_gaa', 'goog.i18n.NumberFormatSymbols_gaa_GH', 'goog.i18n.NumberFormatSymbols_gez', 'goog.i18n.NumberFormatSymbols_gez_ER', 'goog.i18n.NumberFormatSymbols_gez_ET', 'goog.i18n.NumberFormatSymbols_guz', 'goog.i18n.NumberFormatSymbols_guz_KE', 'goog.i18n.NumberFormatSymbols_gv', 'goog.i18n.NumberFormatSymbols_gv_GB', 'goog.i18n.NumberFormatSymbols_ha', 'goog.i18n.NumberFormatSymbols_ha_Arab', 'goog.i18n.NumberFormatSymbols_ha_Arab_NG', 'goog.i18n.NumberFormatSymbols_ha_Arab_SD', 'goog.i18n.NumberFormatSymbols_ha_GH', 'goog.i18n.NumberFormatSymbols_ha_Latn', 'goog.i18n.NumberFormatSymbols_ha_Latn_GH', 'goog.i18n.NumberFormatSymbols_ha_Latn_NE', 'goog.i18n.NumberFormatSymbols_ha_Latn_NG', 'goog.i18n.NumberFormatSymbols_ha_NE', 'goog.i18n.NumberFormatSymbols_ha_NG', 'goog.i18n.NumberFormatSymbols_ha_SD', 'goog.i18n.NumberFormatSymbols_haw', 'goog.i18n.NumberFormatSymbols_haw_US', 'goog.i18n.NumberFormatSymbols_hy', 'goog.i18n.NumberFormatSymbols_hy_AM', 'goog.i18n.NumberFormatSymbols_ia', 'goog.i18n.NumberFormatSymbols_ig', 'goog.i18n.NumberFormatSymbols_ig_NG', 'goog.i18n.NumberFormatSymbols_ii', 'goog.i18n.NumberFormatSymbols_ii_CN', 'goog.i18n.NumberFormatSymbols_it_CH', 'goog.i18n.NumberFormatSymbols_iu', 'goog.i18n.NumberFormatSymbols_jmc', 'goog.i18n.NumberFormatSymbols_jmc_TZ', 'goog.i18n.NumberFormatSymbols_ka', 'goog.i18n.NumberFormatSymbols_ka_GE', 'goog.i18n.NumberFormatSymbols_kab', 'goog.i18n.NumberFormatSymbols_kab_DZ', 'goog.i18n.NumberFormatSymbols_kaj', 'goog.i18n.NumberFormatSymbols_kaj_NG', 'goog.i18n.NumberFormatSymbols_kam', 'goog.i18n.NumberFormatSymbols_kam_KE', 'goog.i18n.NumberFormatSymbols_kcg', 'goog.i18n.NumberFormatSymbols_kcg_NG', 'goog.i18n.NumberFormatSymbols_kde', 'goog.i18n.NumberFormatSymbols_kde_TZ', 'goog.i18n.NumberFormatSymbols_kea', 'goog.i18n.NumberFormatSymbols_kea_CV', 'goog.i18n.NumberFormatSymbols_kfo', 'goog.i18n.NumberFormatSymbols_kfo_CI', 'goog.i18n.NumberFormatSymbols_khq', 'goog.i18n.NumberFormatSymbols_khq_ML', 'goog.i18n.NumberFormatSymbols_ki', 'goog.i18n.NumberFormatSymbols_ki_KE', 'goog.i18n.NumberFormatSymbols_kk', 'goog.i18n.NumberFormatSymbols_kk_Cyrl', 'goog.i18n.NumberFormatSymbols_kk_Cyrl_KZ', 'goog.i18n.NumberFormatSymbols_kk_KZ', 'goog.i18n.NumberFormatSymbols_kl', 'goog.i18n.NumberFormatSymbols_kl_GL', 'goog.i18n.NumberFormatSymbols_kln', 'goog.i18n.NumberFormatSymbols_kln_KE', 'goog.i18n.NumberFormatSymbols_km', 'goog.i18n.NumberFormatSymbols_km_KH', 'goog.i18n.NumberFormatSymbols_kok', 'goog.i18n.NumberFormatSymbols_kok_IN', 'goog.i18n.NumberFormatSymbols_kpe', 'goog.i18n.NumberFormatSymbols_kpe_GN', 'goog.i18n.NumberFormatSymbols_kpe_LR', 'goog.i18n.NumberFormatSymbols_ksb', 'goog.i18n.NumberFormatSymbols_ksb_TZ', 'goog.i18n.NumberFormatSymbols_ksh', 'goog.i18n.NumberFormatSymbols_ksh_DE', 'goog.i18n.NumberFormatSymbols_ku', 'goog.i18n.NumberFormatSymbols_ku_Arab', 'goog.i18n.NumberFormatSymbols_ku_Arab_IQ', 'goog.i18n.NumberFormatSymbols_ku_Arab_IR', 'goog.i18n.NumberFormatSymbols_ku_IQ', 'goog.i18n.NumberFormatSymbols_ku_IR', 'goog.i18n.NumberFormatSymbols_ku_Latn', 'goog.i18n.NumberFormatSymbols_ku_Latn_SY', 'goog.i18n.NumberFormatSymbols_ku_Latn_TR', 'goog.i18n.NumberFormatSymbols_ku_SY', 'goog.i18n.NumberFormatSymbols_ku_TR', 'goog.i18n.NumberFormatSymbols_kw', 'goog.i18n.NumberFormatSymbols_kw_GB', 'goog.i18n.NumberFormatSymbols_ky', 'goog.i18n.NumberFormatSymbols_ky_KG', 'goog.i18n.NumberFormatSymbols_lag', 'goog.i18n.NumberFormatSymbols_lag_TZ', 'goog.i18n.NumberFormatSymbols_lg', 'goog.i18n.NumberFormatSymbols_lg_UG', 'goog.i18n.NumberFormatSymbols_ln_CG', 'goog.i18n.NumberFormatSymbols_lo', 'goog.i18n.NumberFormatSymbols_lo_LA', 'goog.i18n.NumberFormatSymbols_luo', 'goog.i18n.NumberFormatSymbols_luo_KE', 'goog.i18n.NumberFormatSymbols_luy', 'goog.i18n.NumberFormatSymbols_luy_KE', 'goog.i18n.NumberFormatSymbols_mas', 'goog.i18n.NumberFormatSymbols_mas_KE', 'goog.i18n.NumberFormatSymbols_mas_TZ', 'goog.i18n.NumberFormatSymbols_mer', 'goog.i18n.NumberFormatSymbols_mer_KE', 'goog.i18n.NumberFormatSymbols_mfe', 'goog.i18n.NumberFormatSymbols_mfe_MU', 'goog.i18n.NumberFormatSymbols_mg', 'goog.i18n.NumberFormatSymbols_mg_MG', 'goog.i18n.NumberFormatSymbols_mi', 'goog.i18n.NumberFormatSymbols_mi_NZ', 'goog.i18n.NumberFormatSymbols_mk', 'goog.i18n.NumberFormatSymbols_mk_MK', 'goog.i18n.NumberFormatSymbols_mn', 'goog.i18n.NumberFormatSymbols_mn_CN', 'goog.i18n.NumberFormatSymbols_mn_Cyrl', 'goog.i18n.NumberFormatSymbols_mn_Cyrl_MN', 'goog.i18n.NumberFormatSymbols_mn_MN', 'goog.i18n.NumberFormatSymbols_mn_Mong', 'goog.i18n.NumberFormatSymbols_mn_Mong_CN', 'goog.i18n.NumberFormatSymbols_ms_BN', 'goog.i18n.NumberFormatSymbols_my', 'goog.i18n.NumberFormatSymbols_my_MM', 'goog.i18n.NumberFormatSymbols_naq', 'goog.i18n.NumberFormatSymbols_naq_NA', 'goog.i18n.NumberFormatSymbols_nb', 'goog.i18n.NumberFormatSymbols_nb_NO', 'goog.i18n.NumberFormatSymbols_nd', 'goog.i18n.NumberFormatSymbols_nd_ZW', 'goog.i18n.NumberFormatSymbols_nds', 'goog.i18n.NumberFormatSymbols_nds_DE', 'goog.i18n.NumberFormatSymbols_ne', 'goog.i18n.NumberFormatSymbols_ne_IN', 'goog.i18n.NumberFormatSymbols_ne_NP', 'goog.i18n.NumberFormatSymbols_nl_BE', 'goog.i18n.NumberFormatSymbols_nn', 'goog.i18n.NumberFormatSymbols_nn_NO', 'goog.i18n.NumberFormatSymbols_nr', 'goog.i18n.NumberFormatSymbols_nr_ZA', 'goog.i18n.NumberFormatSymbols_nso', 'goog.i18n.NumberFormatSymbols_nso_ZA', 'goog.i18n.NumberFormatSymbols_ny', 'goog.i18n.NumberFormatSymbols_ny_MW', 'goog.i18n.NumberFormatSymbols_nyn', 'goog.i18n.NumberFormatSymbols_nyn_UG', 'goog.i18n.NumberFormatSymbols_oc', 'goog.i18n.NumberFormatSymbols_oc_FR', 'goog.i18n.NumberFormatSymbols_om', 'goog.i18n.NumberFormatSymbols_om_ET', 'goog.i18n.NumberFormatSymbols_om_KE', 'goog.i18n.NumberFormatSymbols_pa', 'goog.i18n.NumberFormatSymbols_pa_Arab', 'goog.i18n.NumberFormatSymbols_pa_Arab_PK', 'goog.i18n.NumberFormatSymbols_pa_Guru', 'goog.i18n.NumberFormatSymbols_pa_Guru_IN', 'goog.i18n.NumberFormatSymbols_pa_IN', 'goog.i18n.NumberFormatSymbols_pa_PK', 'goog.i18n.NumberFormatSymbols_ps', 'goog.i18n.NumberFormatSymbols_ps_AF', 'goog.i18n.NumberFormatSymbols_pt_AO', 'goog.i18n.NumberFormatSymbols_pt_GW', 'goog.i18n.NumberFormatSymbols_pt_MZ', 'goog.i18n.NumberFormatSymbols_rm', 'goog.i18n.NumberFormatSymbols_rm_CH', 'goog.i18n.NumberFormatSymbols_ro_MD', 'goog.i18n.NumberFormatSymbols_rof', 'goog.i18n.NumberFormatSymbols_rof_TZ', 'goog.i18n.NumberFormatSymbols_ru_MD', 'goog.i18n.NumberFormatSymbols_ru_UA', 'goog.i18n.NumberFormatSymbols_rw', 'goog.i18n.NumberFormatSymbols_rw_RW', 'goog.i18n.NumberFormatSymbols_rwk', 'goog.i18n.NumberFormatSymbols_rwk_TZ', 'goog.i18n.NumberFormatSymbols_sa', 'goog.i18n.NumberFormatSymbols_sa_IN', 'goog.i18n.NumberFormatSymbols_saq', 'goog.i18n.NumberFormatSymbols_saq_KE', 'goog.i18n.NumberFormatSymbols_se', 'goog.i18n.NumberFormatSymbols_se_FI', 'goog.i18n.NumberFormatSymbols_se_NO', 'goog.i18n.NumberFormatSymbols_seh', 'goog.i18n.NumberFormatSymbols_seh_MZ', 'goog.i18n.NumberFormatSymbols_ses', 'goog.i18n.NumberFormatSymbols_ses_ML', 'goog.i18n.NumberFormatSymbols_sg', 'goog.i18n.NumberFormatSymbols_sg_CF', 'goog.i18n.NumberFormatSymbols_sh', 'goog.i18n.NumberFormatSymbols_sh_BA', 'goog.i18n.NumberFormatSymbols_sh_CS', 'goog.i18n.NumberFormatSymbols_sh_YU', 'goog.i18n.NumberFormatSymbols_shi', 'goog.i18n.NumberFormatSymbols_shi_Latn', 'goog.i18n.NumberFormatSymbols_shi_Latn_MA', 'goog.i18n.NumberFormatSymbols_shi_MA', 'goog.i18n.NumberFormatSymbols_shi_Tfng', 'goog.i18n.NumberFormatSymbols_shi_Tfng_MA', 'goog.i18n.NumberFormatSymbols_si', 'goog.i18n.NumberFormatSymbols_si_LK', 'goog.i18n.NumberFormatSymbols_sid', 'goog.i18n.NumberFormatSymbols_sid_ET', 'goog.i18n.NumberFormatSymbols_sn', 'goog.i18n.NumberFormatSymbols_sn_ZW', 'goog.i18n.NumberFormatSymbols_so', 'goog.i18n.NumberFormatSymbols_so_DJ', 'goog.i18n.NumberFormatSymbols_so_ET', 'goog.i18n.NumberFormatSymbols_so_KE', 'goog.i18n.NumberFormatSymbols_so_SO', 'goog.i18n.NumberFormatSymbols_sr_BA', 'goog.i18n.NumberFormatSymbols_sr_CS', 'goog.i18n.NumberFormatSymbols_sr_Cyrl', 'goog.i18n.NumberFormatSymbols_sr_Cyrl_BA', 'goog.i18n.NumberFormatSymbols_sr_Cyrl_CS', 'goog.i18n.NumberFormatSymbols_sr_Cyrl_ME', 'goog.i18n.NumberFormatSymbols_sr_Cyrl_YU', 'goog.i18n.NumberFormatSymbols_sr_Latn', 'goog.i18n.NumberFormatSymbols_sr_Latn_BA', 'goog.i18n.NumberFormatSymbols_sr_Latn_CS', 'goog.i18n.NumberFormatSymbols_sr_Latn_ME', 'goog.i18n.NumberFormatSymbols_sr_Latn_YU', 'goog.i18n.NumberFormatSymbols_sr_ME', 'goog.i18n.NumberFormatSymbols_sr_YU', 'goog.i18n.NumberFormatSymbols_ss', 'goog.i18n.NumberFormatSymbols_ss_SZ', 'goog.i18n.NumberFormatSymbols_ss_ZA', 'goog.i18n.NumberFormatSymbols_ssy', 'goog.i18n.NumberFormatSymbols_ssy_ER', 'goog.i18n.NumberFormatSymbols_st', 'goog.i18n.NumberFormatSymbols_st_LS', 'goog.i18n.NumberFormatSymbols_st_ZA', 'goog.i18n.NumberFormatSymbols_sv_FI', 'goog.i18n.NumberFormatSymbols_sw_KE', 'goog.i18n.NumberFormatSymbols_syr', 'goog.i18n.NumberFormatSymbols_syr_SY', 'goog.i18n.NumberFormatSymbols_ta_LK', 'goog.i18n.NumberFormatSymbols_teo', 'goog.i18n.NumberFormatSymbols_teo_KE', 'goog.i18n.NumberFormatSymbols_teo_UG', 'goog.i18n.NumberFormatSymbols_tg', 'goog.i18n.NumberFormatSymbols_tg_Cyrl', 'goog.i18n.NumberFormatSymbols_tg_Cyrl_TJ', 'goog.i18n.NumberFormatSymbols_tg_TJ', 'goog.i18n.NumberFormatSymbols_ti', 'goog.i18n.NumberFormatSymbols_ti_ER', 'goog.i18n.NumberFormatSymbols_ti_ET', 'goog.i18n.NumberFormatSymbols_tig', 'goog.i18n.NumberFormatSymbols_tig_ER', 'goog.i18n.NumberFormatSymbols_tn', 'goog.i18n.NumberFormatSymbols_tn_ZA', 'goog.i18n.NumberFormatSymbols_to', 'goog.i18n.NumberFormatSymbols_to_TO', 'goog.i18n.NumberFormatSymbols_trv', 'goog.i18n.NumberFormatSymbols_trv_TW', 'goog.i18n.NumberFormatSymbols_ts', 'goog.i18n.NumberFormatSymbols_ts_ZA', 'goog.i18n.NumberFormatSymbols_tt', 'goog.i18n.NumberFormatSymbols_tt_RU', 'goog.i18n.NumberFormatSymbols_tzm', 'goog.i18n.NumberFormatSymbols_tzm_Latn', 'goog.i18n.NumberFormatSymbols_tzm_Latn_MA', 'goog.i18n.NumberFormatSymbols_tzm_MA', 'goog.i18n.NumberFormatSymbols_ug', 'goog.i18n.NumberFormatSymbols_ug_Arab', 'goog.i18n.NumberFormatSymbols_ug_Arab_CN', 'goog.i18n.NumberFormatSymbols_ug_CN', 'goog.i18n.NumberFormatSymbols_ur_IN', 'goog.i18n.NumberFormatSymbols_uz', 'goog.i18n.NumberFormatSymbols_uz_AF', 'goog.i18n.NumberFormatSymbols_uz_Arab', 'goog.i18n.NumberFormatSymbols_uz_Arab_AF', 'goog.i18n.NumberFormatSymbols_uz_Cyrl', 'goog.i18n.NumberFormatSymbols_uz_Cyrl_UZ', 'goog.i18n.NumberFormatSymbols_uz_Latn', 'goog.i18n.NumberFormatSymbols_uz_Latn_UZ', 'goog.i18n.NumberFormatSymbols_uz_UZ', 'goog.i18n.NumberFormatSymbols_ve', 'goog.i18n.NumberFormatSymbols_ve_ZA', 'goog.i18n.NumberFormatSymbols_vun', 'goog.i18n.NumberFormatSymbols_vun_TZ', 'goog.i18n.NumberFormatSymbols_wal', 'goog.i18n.NumberFormatSymbols_wal_ET', 'goog.i18n.NumberFormatSymbols_wo', 'goog.i18n.NumberFormatSymbols_wo_Latn', 'goog.i18n.NumberFormatSymbols_wo_Latn_SN', 'goog.i18n.NumberFormatSymbols_wo_SN', 'goog.i18n.NumberFormatSymbols_xh', 'goog.i18n.NumberFormatSymbols_xh_ZA', 'goog.i18n.NumberFormatSymbols_xog', 'goog.i18n.NumberFormatSymbols_xog_UG', 'goog.i18n.NumberFormatSymbols_yo', 'goog.i18n.NumberFormatSymbols_yo_NG', 'goog.i18n.NumberFormatSymbols_zh_Hans_HK', 'goog.i18n.NumberFormatSymbols_zh_Hans_MO', 'goog.i18n.NumberFormatSymbols_zh_Hans_SG', 'goog.i18n.NumberFormatSymbols_zh_Hant', 'goog.i18n.NumberFormatSymbols_zh_Hant_HK', 'goog.i18n.NumberFormatSymbols_zh_Hant_MO', 'goog.i18n.NumberFormatSymbols_zh_Hant_TW', 'goog.i18n.NumberFormatSymbols_zh_MO', 'goog.i18n.NumberFormatSymbols_zh_SG', 'goog.i18n.NumberFormatSymbols_zu', 'goog.i18n.NumberFormatSymbols_zu_ZA'], ['goog.i18n.NumberFormatSymbols']);
goog.addDependency('i18n/pluralrules.js', ['goog.i18n.pluralRules'], []);
goog.addDependency('i18n/timezone.js', ['goog.i18n.TimeZone'], ['goog.array', 'goog.date.DateLike', 'goog.string']);
goog.addDependency('i18n/uchar.js', ['goog.i18n.uChar'], []);
goog.addDependency('iter/iter.js', ['goog.iter', 'goog.iter.Iterator', 'goog.iter.StopIteration'], ['goog.array', 'goog.asserts']);
goog.addDependency('jsaction/context.js', ['goog.jsaction.Context'], []);
goog.addDependency('jsaction/dispatcher.js', ['goog.jsaction.Dispatcher', 'goog.jsaction.HandlerFunction', 'goog.jsaction.LoaderFunction'], ['goog.asserts', 'goog.jsaction.Context', 'goog.jsaction.EventContract', 'goog.jsaction.replay', 'goog.jsaction.util']);
goog.addDependency('jsaction/eventcontract.js', ['goog.jsaction.EventContract', 'goog.jsaction.EventType', 'goog.jsaction.ReplayInfo'], ['goog.jsaction.util', 'goog.object']);
goog.addDependency('jsaction/jsprops.js', ['goog.jsaction.jsprops'], ['goog.json']);
goog.addDependency('jsaction/replay.js', ['goog.jsaction.replay'], ['goog.asserts', 'goog.jsaction.EventContract']);
goog.addDependency('jsaction/util.js', ['goog.jsaction.util'], []);
goog.addDependency('json/json.js', ['goog.json', 'goog.json.Serializer'], []);
goog.addDependency('labs/net/xhr.js', ['goog.labs.net.xhr', 'goog.labs.net.xhr.Error', 'goog.labs.net.xhr.HttpError', 'goog.labs.net.xhr.TimeoutError'], ['goog.async.Deferred', 'goog.debug.Error', 'goog.json', 'goog.net.HttpStatus', 'goog.net.XmlHttp', 'goog.string', 'goog.uri.utils']);
goog.addDependency('locale/countries.js', ['goog.locale.countries'], []);
goog.addDependency('locale/defaultlocalenameconstants.js', ['goog.locale.defaultLocaleNameConstants'], []);
goog.addDependency('locale/genericfontnames.js', ['goog.locale.genericFontNames'], []);
goog.addDependency('locale/genericfontnamesdata.js', ['goog.locale.genericFontNamesData'], ['goog.locale']);
goog.addDependency('locale/locale.js', ['goog.locale'], ['goog.locale.nativeNameConstants']);
goog.addDependency('locale/nativenameconstants.js', ['goog.locale.nativeNameConstants'], []);
goog.addDependency('locale/scriptToLanguages.js', ['goog.locale.scriptToLanguages'], ['goog.locale']);
goog.addDependency('locale/timezonedetection.js', ['goog.locale.timeZoneDetection'], ['goog.locale', 'goog.locale.TimeZoneFingerprint']);
goog.addDependency('locale/timezonefingerprint.js', ['goog.locale.TimeZoneFingerprint'], ['goog.locale']);
goog.addDependency('locale/timezonelist.js', ['goog.locale.TimeZoneList'], ['goog.locale']);
goog.addDependency('math/bezier.js', ['goog.math.Bezier'], ['goog.math', 'goog.math.Coordinate']);
goog.addDependency('math/box.js', ['goog.math.Box'], ['goog.math.Coordinate']);
goog.addDependency('math/coordinate.js', ['goog.math.Coordinate'], []);
goog.addDependency('math/coordinate3.js', ['goog.math.Coordinate3'], []);
goog.addDependency('math/exponentialbackoff.js', ['goog.math.ExponentialBackoff'], ['goog.asserts']);
goog.addDependency('math/integer.js', ['goog.math.Integer'], []);
goog.addDependency('math/line.js', ['goog.math.Line'], ['goog.math', 'goog.math.Coordinate']);
goog.addDependency('math/long.js', ['goog.math.Long'], []);
goog.addDependency('math/math.js', ['goog.math'], ['goog.array']);
goog.addDependency('math/matrix.js', ['goog.math.Matrix'], ['goog.array', 'goog.math', 'goog.math.Size']);
goog.addDependency('math/range.js', ['goog.math.Range'], []);
goog.addDependency('math/rangeset.js', ['goog.math.RangeSet'], ['goog.array', 'goog.iter.Iterator', 'goog.iter.StopIteration', 'goog.math.Range']);
goog.addDependency('math/rect.js', ['goog.math.Rect'], ['goog.math.Box', 'goog.math.Size']);
goog.addDependency('math/size.js', ['goog.math.Size'], []);
goog.addDependency('math/vec2.js', ['goog.math.Vec2'], ['goog.math', 'goog.math.Coordinate']);
goog.addDependency('math/vec3.js', ['goog.math.Vec3'], ['goog.math', 'goog.math.Coordinate3']);
goog.addDependency('memoize/memoize.js', ['goog.memoize'], []);
goog.addDependency('messaging/abstractchannel.js', ['goog.messaging.AbstractChannel'], ['goog.Disposable', 'goog.debug', 'goog.debug.Logger', 'goog.json', 'goog.messaging.MessageChannel']);
goog.addDependency('messaging/bufferedchannel.js', ['goog.messaging.BufferedChannel'], ['goog.Timer', 'goog.Uri', 'goog.debug.Error', 'goog.debug.Logger', 'goog.events', 'goog.messaging.MessageChannel', 'goog.messaging.MultiChannel']);
goog.addDependency('messaging/deferredchannel.js', ['goog.messaging.DeferredChannel'], ['goog.async.Deferred', 'goog.messaging.MessageChannel']);
goog.addDependency('messaging/loggerclient.js', ['goog.messaging.LoggerClient'], ['goog.Disposable', 'goog.debug', 'goog.debug.LogManager', 'goog.debug.Logger']);
goog.addDependency('messaging/loggerserver.js', ['goog.messaging.LoggerServer'], ['goog.Disposable', 'goog.debug.Logger']);
goog.addDependency('messaging/messagechannel.js', ['goog.messaging.MessageChannel'], []);
goog.addDependency('messaging/messaging.js', ['goog.messaging'], ['goog.messaging.MessageChannel']);
goog.addDependency('messaging/multichannel.js', ['goog.messaging.MultiChannel', 'goog.messaging.MultiChannel.VirtualChannel'], ['goog.Disposable', 'goog.debug.Logger', 'goog.events.EventHandler', 'goog.messaging.MessageChannel', 'goog.object']);
goog.addDependency('messaging/portcaller.js', ['goog.messaging.PortCaller'], ['goog.Disposable', 'goog.async.Deferred', 'goog.messaging.DeferredChannel', 'goog.messaging.PortChannel', 'goog.messaging.PortNetwork', 'goog.object']);
goog.addDependency('messaging/portchannel.js', ['goog.messaging.PortChannel'], ['goog.Timer', 'goog.array', 'goog.async.Deferred', 'goog.debug', 'goog.debug.Logger', 'goog.dom', 'goog.dom.DomHelper', 'goog.events', 'goog.events.EventType', 'goog.json', 'goog.messaging.AbstractChannel', 'goog.messaging.DeferredChannel', 'goog.object', 'goog.string']);
goog.addDependency('messaging/portnetwork.js', ['goog.messaging.PortNetwork'], []);
goog.addDependency('messaging/portoperator.js', ['goog.messaging.PortOperator'], ['goog.Disposable', 'goog.asserts', 'goog.debug.Logger', 'goog.messaging.PortChannel', 'goog.messaging.PortNetwork', 'goog.object']);
goog.addDependency('messaging/testdata/portchannel_worker.js', ['goog.messaging.testdata.portchannel_worker'], ['goog.messaging.PortChannel']);
goog.addDependency('messaging/testdata/portnetwork_worker1.js', ['goog.messaging.testdata.portnetwork_worker1'], ['goog.messaging.PortCaller', 'goog.messaging.PortChannel']);
goog.addDependency('messaging/testdata/portnetwork_worker2.js', ['goog.messaging.testdata.portnetwork_worker2'], ['goog.messaging.PortCaller', 'goog.messaging.PortChannel']);
goog.addDependency('module/abstractmoduleloader.js', ['goog.module.AbstractModuleLoader'], []);
goog.addDependency('module/basemodule.js', ['goog.module.BaseModule'], ['goog.Disposable']);
goog.addDependency('module/basemoduleloader.js', ['goog.module.BaseModuleLoader'], ['goog.Disposable', 'goog.debug.Logger', 'goog.module.AbstractModuleLoader']);
goog.addDependency('module/loader.js', ['goog.module.Loader'], ['goog.Timer', 'goog.array', 'goog.dom', 'goog.object']);
goog.addDependency('module/module.js', ['goog.module'], ['goog.array', 'goog.module.Loader']);
goog.addDependency('module/moduleinfo.js', ['goog.module.ModuleInfo'], ['goog.Disposable', 'goog.functions', 'goog.module.BaseModule', 'goog.module.ModuleLoadCallback']);
goog.addDependency('module/moduleloadcallback.js', ['goog.module.ModuleLoadCallback'], ['goog.debug.entryPointRegistry', 'goog.debug.errorHandlerWeakDep']);
goog.addDependency('module/moduleloader.js', ['goog.module.ModuleLoader'], ['goog.array', 'goog.debug.Logger', 'goog.dom', 'goog.events.EventHandler', 'goog.module.BaseModuleLoader', 'goog.net.BulkLoader', 'goog.net.EventType', 'goog.net.jsloader']);
goog.addDependency('module/modulemanager.js', ['goog.module.ModuleManager', 'goog.module.ModuleManager.CallbackType', 'goog.module.ModuleManager.FailureType'], ['goog.Disposable', 'goog.array', 'goog.asserts', 'goog.async.Deferred', 'goog.debug.Logger', 'goog.debug.Trace', 'goog.module.AbstractModuleLoader', 'goog.module.ModuleInfo', 'goog.module.ModuleLoadCallback']);
goog.addDependency('module/testdata/modA_1.js', ['goog.module.testdata.modA_1'], []);
goog.addDependency('module/testdata/modA_2.js', ['goog.module.testdata.modA_2'], ['goog.module.ModuleManager']);
goog.addDependency('module/testdata/modB_1.js', ['goog.module.testdata.modB_1'], ['goog.module.ModuleManager']);
goog.addDependency('net/browserchannel.js', ['goog.net.BrowserChannel', 'goog.net.BrowserChannel.Error', 'goog.net.BrowserChannel.Event', 'goog.net.BrowserChannel.Handler', 'goog.net.BrowserChannel.LogSaver', 'goog.net.BrowserChannel.QueuedMap', 'goog.net.BrowserChannel.Stat', 'goog.net.BrowserChannel.StatEvent', 'goog.net.BrowserChannel.State', 'goog.net.BrowserChannel.TimingEvent'], ['goog.Uri', 'goog.array', 'goog.debug.Logger', 'goog.debug.TextFormatter', 'goog.events.Event', 'goog.events.EventTarget', 'goog.json', 'goog.net.BrowserTestChannel', 'goog.net.ChannelDebug', 'goog.net.ChannelRequest', 'goog.net.ChannelRequest.Error', 'goog.net.XhrIo', 'goog.net.tmpnetwork', 'goog.string', 'goog.structs', 'goog.structs.CircularBuffer', 'goog.userAgent']);
goog.addDependency('net/browsertestchannel.js', ['goog.net.BrowserTestChannel'], ['goog.json', 'goog.net.ChannelRequest', 'goog.net.ChannelRequest.Error', 'goog.net.tmpnetwork', 'goog.userAgent']);
goog.addDependency('net/bulkloader.js', ['goog.net.BulkLoader'], ['goog.debug.Logger', 'goog.events.Event', 'goog.events.EventHandler', 'goog.events.EventTarget', 'goog.net.BulkLoaderHelper', 'goog.net.EventType', 'goog.net.XhrIo']);
goog.addDependency('net/bulkloaderhelper.js', ['goog.net.BulkLoaderHelper'], ['goog.Disposable', 'goog.debug.Logger']);
goog.addDependency('net/channeldebug.js', ['goog.net.ChannelDebug'], ['goog.debug.Logger', 'goog.json']);
goog.addDependency('net/channelrequest.js', ['goog.net.ChannelRequest', 'goog.net.ChannelRequest.Error'], ['goog.Timer', 'goog.events', 'goog.events.EventHandler', 'goog.net.EventType', 'goog.net.XmlHttp.ReadyState', 'goog.object', 'goog.userAgent']);
goog.addDependency('net/cookies.js', ['goog.net.Cookies', 'goog.net.cookies'], ['goog.userAgent']);
goog.addDependency('net/crossdomainrpc.js', ['goog.net.CrossDomainRpc'], ['goog.Uri.QueryData', 'goog.debug.Logger', 'goog.dom', 'goog.events', 'goog.events.EventTarget', 'goog.events.EventType', 'goog.json', 'goog.net.EventType', 'goog.net.HttpStatus', 'goog.userAgent']);
goog.addDependency('net/errorcode.js', ['goog.net.ErrorCode'], []);
goog.addDependency('net/eventtype.js', ['goog.net.EventType'], []);
goog.addDependency('net/filedownloader.js', ['goog.net.FileDownloader', 'goog.net.FileDownloader.Error'], ['goog.Disposable', 'goog.asserts', 'goog.async.Deferred', 'goog.crypt.hash32', 'goog.debug.Error', 'goog.events.EventHandler', 'goog.fs', 'goog.fs.DirectoryEntry.Behavior', 'goog.fs.Error.ErrorCode', 'goog.fs.FileSaver.EventType', 'goog.net.EventType', 'goog.net.XhrIo.ResponseType', 'goog.net.XhrIoPool']);
goog.addDependency('net/httpstatus.js', ['goog.net.HttpStatus'], []);
goog.addDependency('net/iframeio.js', ['goog.net.IframeIo', 'goog.net.IframeIo.IncrementalDataEvent'], ['goog.Timer', 'goog.Uri', 'goog.debug', 'goog.debug.Logger', 'goog.dom', 'goog.events', 'goog.events.EventTarget', 'goog.events.EventType', 'goog.json', 'goog.net.ErrorCode', 'goog.net.EventType', 'goog.reflect', 'goog.string', 'goog.structs', 'goog.userAgent']);
goog.addDependency('net/iframeloadmonitor.js', ['goog.net.IframeLoadMonitor'], ['goog.dom', 'goog.events', 'goog.events.EventTarget', 'goog.events.EventType', 'goog.userAgent']);
goog.addDependency('net/imageloader.js', ['goog.net.ImageLoader'], ['goog.dom', 'goog.events.EventHandler', 'goog.events.EventTarget', 'goog.events.EventType', 'goog.net.EventType', 'goog.object', 'goog.userAgent']);
goog.addDependency('net/ipaddress.js', ['goog.net.IpAddress', 'goog.net.Ipv4Address', 'goog.net.Ipv6Address'], ['goog.array', 'goog.math.Integer', 'goog.object', 'goog.string']);
goog.addDependency('net/jsloader.js', ['goog.net.jsloader', 'goog.net.jsloader.Error'], ['goog.array', 'goog.async.Deferred', 'goog.debug.Error', 'goog.dom', 'goog.userAgent']);
goog.addDependency('net/jsonp.js', ['goog.net.Jsonp'], ['goog.Uri', 'goog.dom', 'goog.net.jsloader']);
goog.addDependency('net/mockiframeio.js', ['goog.net.MockIFrameIo'], ['goog.events.EventTarget', 'goog.net.ErrorCode', 'goog.net.IframeIo', 'goog.net.IframeIo.IncrementalDataEvent']);
goog.addDependency('net/mockxhrlite.js', ['goog.net.MockXhrLite'], ['goog.testing.net.XhrIo']);
goog.addDependency('net/multiiframeloadmonitor.js', ['goog.net.MultiIframeLoadMonitor'], ['goog.net.IframeLoadMonitor']);
goog.addDependency('net/networktester.js', ['goog.net.NetworkTester'], ['goog.Timer', 'goog.Uri', 'goog.debug.Logger']);
goog.addDependency('net/testdata/jsloader_test1.js', ['goog.net.testdata.jsloader_test1'], []);
goog.addDependency('net/testdata/jsloader_test2.js', ['goog.net.testdata.jsloader_test2'], []);
goog.addDependency('net/testdata/jsloader_test3.js', ['goog.net.testdata.jsloader_test3'], []);
goog.addDependency('net/testdata/jsloader_test4.js', ['goog.net.testdata.jsloader_test4'], []);
goog.addDependency('net/tmpnetwork.js', ['goog.net.tmpnetwork'], ['goog.Uri', 'goog.net.ChannelDebug']);
goog.addDependency('net/websocket.js', ['goog.net.WebSocket', 'goog.net.WebSocket.ErrorEvent', 'goog.net.WebSocket.EventType', 'goog.net.WebSocket.MessageEvent'], ['goog.Timer', 'goog.asserts', 'goog.debug.Logger', 'goog.debug.entryPointRegistry', 'goog.events', 'goog.events.Event', 'goog.events.EventTarget']);
goog.addDependency('net/wrapperxmlhttpfactory.js', ['goog.net.WrapperXmlHttpFactory'], ['goog.net.XmlHttpFactory']);
goog.addDependency('net/xhrio.js', ['goog.net.XhrIo', 'goog.net.XhrIo.ResponseType'], ['goog.Timer', 'goog.debug.Logger', 'goog.debug.entryPointRegistry', 'goog.debug.errorHandlerWeakDep', 'goog.events.EventTarget', 'goog.json', 'goog.net.ErrorCode', 'goog.net.EventType', 'goog.net.HttpStatus', 'goog.net.XmlHttp', 'goog.object', 'goog.structs', 'goog.structs.Map', 'goog.uri.utils']);
goog.addDependency('net/xhriopool.js', ['goog.net.XhrIoPool'], ['goog.net.XhrIo', 'goog.structs', 'goog.structs.PriorityPool']);
goog.addDependency('net/xhrlite.js', ['goog.net.XhrLite'], ['goog.net.XhrIo']);
goog.addDependency('net/xhrlitepool.js', ['goog.net.XhrLitePool'], ['goog.net.XhrIoPool']);
goog.addDependency('net/xhrmanager.js', ['goog.net.XhrManager', 'goog.net.XhrManager.Event', 'goog.net.XhrManager.Request'], ['goog.Disposable', 'goog.events', 'goog.events.Event', 'goog.events.EventHandler', 'goog.events.EventTarget', 'goog.net.EventType', 'goog.net.XhrIo', 'goog.net.XhrIoPool', 'goog.structs.Map']);
goog.addDependency('net/xmlhttp.js', ['goog.net.DefaultXmlHttpFactory', 'goog.net.XmlHttp', 'goog.net.XmlHttp.OptionType', 'goog.net.XmlHttp.ReadyState'], ['goog.net.WrapperXmlHttpFactory', 'goog.net.XmlHttpFactory']);
goog.addDependency('net/xmlhttpfactory.js', ['goog.net.XmlHttpFactory'], []);
goog.addDependency('net/xpc/crosspagechannel.js', ['goog.net.xpc.CrossPageChannel'], ['goog.Disposable', 'goog.Uri', 'goog.dom', 'goog.events', 'goog.json', 'goog.messaging.AbstractChannel', 'goog.net.xpc', 'goog.net.xpc.CrossPageChannelRole', 'goog.net.xpc.FrameElementMethodTransport', 'goog.net.xpc.IframePollingTransport', 'goog.net.xpc.IframeRelayTransport', 'goog.net.xpc.NativeMessagingTransport', 'goog.net.xpc.NixTransport', 'goog.net.xpc.Transport', 'goog.userAgent']);
goog.addDependency('net/xpc/crosspagechannelrole.js', ['goog.net.xpc.CrossPageChannelRole'], []);
goog.addDependency('net/xpc/frameelementmethodtransport.js', ['goog.net.xpc.FrameElementMethodTransport'], ['goog.net.xpc', 'goog.net.xpc.CrossPageChannelRole', 'goog.net.xpc.Transport']);
goog.addDependency('net/xpc/iframepollingtransport.js', ['goog.net.xpc.IframePollingTransport', 'goog.net.xpc.IframePollingTransport.Receiver', 'goog.net.xpc.IframePollingTransport.Sender'], ['goog.array', 'goog.dom', 'goog.net.xpc', 'goog.net.xpc.CrossPageChannelRole', 'goog.net.xpc.Transport', 'goog.userAgent']);
goog.addDependency('net/xpc/iframerelaytransport.js', ['goog.net.xpc.IframeRelayTransport'], ['goog.dom', 'goog.events', 'goog.net.xpc', 'goog.net.xpc.Transport', 'goog.userAgent']);
goog.addDependency('net/xpc/nativemessagingtransport.js', ['goog.net.xpc.NativeMessagingTransport'], ['goog.events', 'goog.net.xpc', 'goog.net.xpc.CrossPageChannelRole', 'goog.net.xpc.Transport']);
goog.addDependency('net/xpc/nixtransport.js', ['goog.net.xpc.NixTransport'], ['goog.net.xpc', 'goog.net.xpc.CrossPageChannelRole', 'goog.net.xpc.Transport', 'goog.reflect']);
goog.addDependency('net/xpc/relay.js', ['goog.net.xpc.relay'], []);
goog.addDependency('net/xpc/transport.js', ['goog.net.xpc.Transport'], ['goog.Disposable', 'goog.dom', 'goog.net.xpc']);
goog.addDependency('net/xpc/xpc.js', ['goog.net.xpc', 'goog.net.xpc.CfgFields', 'goog.net.xpc.ChannelStates', 'goog.net.xpc.TransportNames', 'goog.net.xpc.TransportTypes', 'goog.net.xpc.UriCfgFields'], ['goog.debug.Logger']);
goog.addDependency('object/object.js', ['goog.object'], []);
goog.addDependency('positioning/absoluteposition.js', ['goog.positioning.AbsolutePosition'], ['goog.math.Box', 'goog.math.Coordinate', 'goog.math.Size', 'goog.positioning', 'goog.positioning.AbstractPosition']);
goog.addDependency('positioning/abstractposition.js', ['goog.positioning.AbstractPosition'], ['goog.math.Box', 'goog.math.Size', 'goog.positioning.Corner']);
goog.addDependency('positioning/anchoredposition.js', ['goog.positioning.AnchoredPosition'], ['goog.math.Box', 'goog.positioning', 'goog.positioning.AbstractPosition']);
goog.addDependency('positioning/anchoredviewportposition.js', ['goog.positioning.AnchoredViewportPosition'], ['goog.functions', 'goog.math.Box', 'goog.positioning', 'goog.positioning.AnchoredPosition', 'goog.positioning.Corner', 'goog.positioning.Overflow', 'goog.positioning.OverflowStatus']);
goog.addDependency('positioning/clientposition.js', ['goog.positioning.ClientPosition'], ['goog.math.Box', 'goog.math.Coordinate', 'goog.math.Size', 'goog.positioning', 'goog.positioning.AbstractPosition']);
goog.addDependency('positioning/menuanchoredposition.js', ['goog.positioning.MenuAnchoredPosition'], ['goog.math.Box', 'goog.math.Size', 'goog.positioning', 'goog.positioning.AnchoredViewportPosition', 'goog.positioning.Corner', 'goog.positioning.Overflow']);
goog.addDependency('positioning/positioning.js', ['goog.positioning', 'goog.positioning.Corner', 'goog.positioning.CornerBit', 'goog.positioning.Overflow', 'goog.positioning.OverflowStatus'], ['goog.dom', 'goog.dom.TagName', 'goog.math.Box', 'goog.math.Coordinate', 'goog.math.Size', 'goog.style']);
goog.addDependency('positioning/viewportclientposition.js', ['goog.positioning.ViewportClientPosition'], ['goog.math.Box', 'goog.math.Coordinate', 'goog.math.Size', 'goog.positioning.ClientPosition']);
goog.addDependency('positioning/viewportposition.js', ['goog.positioning.ViewportPosition'], ['goog.math.Box', 'goog.math.Coordinate', 'goog.math.Size', 'goog.positioning.AbstractPosition']);
goog.addDependency('proto/proto.js', ['goog.proto'], ['goog.proto.Serializer']);
goog.addDependency('proto/serializer.js', ['goog.proto.Serializer'], ['goog.json.Serializer', 'goog.string']);
goog.addDependency('proto2/descriptor.js', ['goog.proto2.Descriptor', 'goog.proto2.Metadata'], ['goog.array', 'goog.object', 'goog.proto2.Util']);
goog.addDependency('proto2/fielddescriptor.js', ['goog.proto2.FieldDescriptor'], ['goog.proto2.Util', 'goog.string']);
goog.addDependency('proto2/lazydeserializer.js', ['goog.proto2.LazyDeserializer'], ['goog.proto2.Serializer', 'goog.proto2.Util']);
goog.addDependency('proto2/message.js', ['goog.proto2.Message'], ['goog.proto2.Descriptor', 'goog.proto2.FieldDescriptor', 'goog.proto2.Util', 'goog.string']);
goog.addDependency('proto2/objectserializer.js', ['goog.proto2.ObjectSerializer'], ['goog.proto2.Serializer', 'goog.proto2.Util', 'goog.string']);
goog.addDependency('proto2/package_test.pb.js', ['someprotopackage.TestPackageTypes'], ['goog.proto2.Message', 'proto2.TestAllTypes']);
goog.addDependency('proto2/pbliteserializer.js', ['goog.proto2.PbLiteSerializer'], ['goog.proto2.LazyDeserializer', 'goog.proto2.Util']);
goog.addDependency('proto2/serializer.js', ['goog.proto2.Serializer'], ['goog.proto2.Descriptor', 'goog.proto2.FieldDescriptor', 'goog.proto2.Message', 'goog.proto2.Util']);
goog.addDependency('proto2/test.pb.js', ['proto2.TestAllTypes', 'proto2.TestAllTypes.NestedEnum', 'proto2.TestAllTypes.NestedMessage', 'proto2.TestAllTypes.OptionalGroup', 'proto2.TestAllTypes.RepeatedGroup'], ['goog.proto2.Message']);
goog.addDependency('proto2/textformatserializer.js', ['goog.proto2.TextFormatSerializer', 'goog.proto2.TextFormatSerializer.Parser'], ['goog.json', 'goog.proto2.Serializer', 'goog.proto2.Util', 'goog.string']);
goog.addDependency('proto2/util.js', ['goog.proto2.Util'], ['goog.asserts']);
goog.addDependency('pubsub/pubsub.js', ['goog.pubsub.PubSub'], ['goog.Disposable', 'goog.array']);
goog.addDependency('reflect/reflect.js', ['goog.reflect'], []);
goog.addDependency('soy/renderer.js', ['goog.soy.InjectedDataSupplier', 'goog.soy.Renderer'], ['goog.dom', 'goog.soy']);
goog.addDependency('soy/soy.js', ['goog.soy'], ['goog.dom', 'goog.dom.NodeType', 'goog.dom.TagName']);
goog.addDependency('soy/soy_test.js', ['goog.soy.testHelper'], ['goog.dom', 'goog.string', 'goog.userAgent']);
goog.addDependency('spell/spellcheck.js', ['goog.spell.SpellCheck', 'goog.spell.SpellCheck.WordChangedEvent'], ['goog.Timer', 'goog.events.EventTarget', 'goog.structs.Set']);
goog.addDependency('stats/basicstat.js', ['goog.stats.BasicStat'], ['goog.array', 'goog.debug.Logger', 'goog.iter', 'goog.object', 'goog.string.format', 'goog.structs.CircularBuffer']);
goog.addDependency('storage/collectablestorage.js', ['goog.storage.CollectableStorage'], ['goog.array', 'goog.asserts', 'goog.iter', 'goog.storage.ErrorCode', 'goog.storage.ExpiringStorage', 'goog.storage.RichStorage.Wrapper', 'goog.storage.mechanism.IterableMechanism']);
goog.addDependency('storage/encryptedstorage.js', ['goog.storage.EncryptedStorage'], ['goog.crypt', 'goog.crypt.Arc4', 'goog.crypt.Sha1', 'goog.crypt.base64', 'goog.json', 'goog.json.Serializer', 'goog.storage.CollectableStorage', 'goog.storage.ErrorCode', 'goog.storage.RichStorage', 'goog.storage.RichStorage.Wrapper', 'goog.storage.mechanism.IterableMechanism']);
goog.addDependency('storage/errorcode.js', ['goog.storage.ErrorCode'], []);
goog.addDependency('storage/expiringstorage.js', ['goog.storage.ExpiringStorage'], ['goog.storage.RichStorage', 'goog.storage.RichStorage.Wrapper', 'goog.storage.mechanism.Mechanism']);
goog.addDependency('storage/mechanism/errorcode.js', ['goog.storage.mechanism.ErrorCode'], []);
goog.addDependency('storage/mechanism/html5localstorage.js', ['goog.storage.mechanism.HTML5LocalStorage'], ['goog.asserts', 'goog.iter.Iterator', 'goog.iter.StopIteration', 'goog.storage.mechanism.ErrorCode', 'goog.storage.mechanism.IterableMechanism']);
goog.addDependency('storage/mechanism/ieuserdata.js', ['goog.storage.mechanism.IEUserData'], ['goog.asserts', 'goog.iter.Iterator', 'goog.iter.StopIteration', 'goog.storage.mechanism.ErrorCode', 'goog.storage.mechanism.IterableMechanism', 'goog.structs.Map', 'goog.userAgent']);
goog.addDependency('storage/mechanism/iterablemechanism.js', ['goog.storage.mechanism.IterableMechanism'], ['goog.array', 'goog.asserts', 'goog.iter', 'goog.iter.Iterator', 'goog.storage.mechanism.Mechanism']);
goog.addDependency('storage/mechanism/iterablemechanism_test.js', ['goog.storage.mechanism.iterablemechanism_test'], ['goog.iter.Iterator', 'goog.storage.mechanism.IterableMechanism', 'goog.testing.asserts']);
goog.addDependency('storage/mechanism/mechanism.js', ['goog.storage.mechanism.Mechanism'], []);
goog.addDependency('storage/mechanism/mechanism_test.js', ['goog.storage.mechanism.mechanism_test'], ['goog.storage.mechanism.ErrorCode', 'goog.storage.mechanism.HTML5LocalStorage', 'goog.storage.mechanism.Mechanism', 'goog.testing.asserts', 'goog.userAgent.product', 'goog.userAgent.product.isVersion']);
goog.addDependency('storage/mechanism/mechanismfactory.js', ['goog.storage.mechanism.mechanismfactory'], ['goog.storage.mechanism.HTML5LocalStorage', 'goog.storage.mechanism.IEUserData', 'goog.storage.mechanism.IterableMechanism', 'goog.storage.mechanism.PrefixedMechanism']);
goog.addDependency('storage/mechanism/prefixedmechanism.js', ['goog.storage.mechanism.PrefixedMechanism'], ['goog.iter.Iterator', 'goog.storage.mechanism.IterableMechanism']);
goog.addDependency('storage/mechanism/prefixedmechanism_test.js', ['goog.storage.mechanism.prefixedmechanism_test'], ['goog.iter.Iterator', 'goog.storage.mechanism.IterableMechanism', 'goog.testing.asserts']);
goog.addDependency('storage/richstorage.js', ['goog.storage.RichStorage', 'goog.storage.RichStorage.Wrapper'], ['goog.storage.ErrorCode', 'goog.storage.Storage', 'goog.storage.mechanism.Mechanism']);
goog.addDependency('storage/storage.js', ['goog.storage.Storage'], ['goog.json', 'goog.json.Serializer', 'goog.storage.ErrorCode', 'goog.storage.mechanism.Mechanism']);
goog.addDependency('storage/storage_test.js', ['goog.storage.storage_test'], ['goog.storage.Storage', 'goog.structs.Map', 'goog.testing.asserts']);
goog.addDependency('string/linkify.js', ['goog.string.linkify'], ['goog.string']);
goog.addDependency('string/path.js', ['goog.string.path'], ['goog.array', 'goog.string']);
goog.addDependency('string/string.js', ['goog.string', 'goog.string.Unicode'], []);
goog.addDependency('string/stringbuffer.js', ['goog.string.StringBuffer'], ['goog.userAgent.jscript']);
goog.addDependency('string/stringformat.js', ['goog.string.format'], ['goog.string']);
goog.addDependency('structs/avltree.js', ['goog.structs.AvlTree', 'goog.structs.AvlTree.Node'], ['goog.structs', 'goog.structs.Collection']);
goog.addDependency('structs/circularbuffer.js', ['goog.structs.CircularBuffer'], []);
goog.addDependency('structs/collection.js', ['goog.structs.Collection'], []);
goog.addDependency('structs/heap.js', ['goog.structs.Heap'], ['goog.array', 'goog.object', 'goog.structs.Node']);
goog.addDependency('structs/inversionmap.js', ['goog.structs.InversionMap'], ['goog.array']);
goog.addDependency('structs/linkedmap.js', ['goog.structs.LinkedMap'], ['goog.structs.Map']);
goog.addDependency('structs/map.js', ['goog.structs.Map'], ['goog.iter.Iterator', 'goog.iter.StopIteration', 'goog.object', 'goog.structs']);
goog.addDependency('structs/node.js', ['goog.structs.Node'], []);
goog.addDependency('structs/pool.js', ['goog.structs.Pool'], ['goog.Disposable', 'goog.structs.Queue', 'goog.structs.Set']);
goog.addDependency('structs/prioritypool.js', ['goog.structs.PriorityPool'], ['goog.structs.Pool', 'goog.structs.PriorityQueue']);
goog.addDependency('structs/priorityqueue.js', ['goog.structs.PriorityQueue'], ['goog.structs', 'goog.structs.Heap']);
goog.addDependency('structs/quadtree.js', ['goog.structs.QuadTree', 'goog.structs.QuadTree.Node', 'goog.structs.QuadTree.Point'], ['goog.math.Coordinate']);
goog.addDependency('structs/queue.js', ['goog.structs.Queue'], ['goog.array']);
goog.addDependency('structs/set.js', ['goog.structs.Set'], ['goog.structs', 'goog.structs.Collection', 'goog.structs.Map']);
goog.addDependency('structs/simplepool.js', ['goog.structs.SimplePool'], ['goog.Disposable']);
goog.addDependency('structs/stringset.js', ['goog.structs.StringSet'], ['goog.iter']);
goog.addDependency('structs/structs.js', ['goog.structs'], ['goog.array', 'goog.object']);
goog.addDependency('structs/treenode.js', ['goog.structs.TreeNode'], ['goog.array', 'goog.asserts', 'goog.structs.Node']);
goog.addDependency('structs/trie.js', ['goog.structs.Trie'], ['goog.object', 'goog.structs']);
goog.addDependency('style/cursor.js', ['goog.style.cursor'], ['goog.userAgent']);
goog.addDependency('style/style.js', ['goog.style'], ['goog.array', 'goog.dom', 'goog.math.Box', 'goog.math.Coordinate', 'goog.math.Rect', 'goog.math.Size', 'goog.object', 'goog.string', 'goog.userAgent']);
goog.addDependency('style/style_test.js', ['goog.style_test'], ['goog.dom', 'goog.style', 'goog.testing.asserts']);
goog.addDependency('style/transition.js', ['goog.style.transition', 'goog.style.transition.Css3Property'], ['goog.array', 'goog.asserts']);
goog.addDependency('testing/asserts.js', ['goog.testing.JsUnitException', 'goog.testing.asserts'], ['goog.testing.stacktrace']);
goog.addDependency('testing/async/mockcontrol.js', ['goog.testing.async.MockControl'], ['goog.asserts', 'goog.async.Deferred', 'goog.debug', 'goog.testing.asserts', 'goog.testing.mockmatchers.IgnoreArgument']);
goog.addDependency('testing/asynctestcase.js', ['goog.testing.AsyncTestCase', 'goog.testing.AsyncTestCase.ControlBreakingException'], ['goog.testing.TestCase', 'goog.testing.TestCase.Test', 'goog.testing.asserts']);
goog.addDependency('testing/benchmark.js', ['goog.testing.benchmark'], ['goog.dom', 'goog.dom.TagName', 'goog.testing.PerformanceTable', 'goog.testing.PerformanceTimer', 'goog.testing.TestCase']);
goog.addDependency('testing/benchmarks/jsbinarysizebutton.js', ['goog.ui.benchmarks.jsbinarysizebutton'], ['goog.array', 'goog.dom', 'goog.events', 'goog.ui.Button', 'goog.ui.ButtonSide', 'goog.ui.Component.EventType', 'goog.ui.CustomButton']);
goog.addDependency('testing/benchmarks/jsbinarysizetoolbar.js', ['goog.ui.benchmarks.jsbinarysizetoolbar'], ['goog.array', 'goog.dom', 'goog.events', 'goog.object', 'goog.ui.Component.EventType', 'goog.ui.Option', 'goog.ui.Toolbar', 'goog.ui.ToolbarButton', 'goog.ui.ToolbarSelect', 'goog.ui.ToolbarSeparator']);
goog.addDependency('testing/continuationtestcase.js', ['goog.testing.ContinuationTestCase', 'goog.testing.ContinuationTestCase.Step', 'goog.testing.ContinuationTestCase.Test'], ['goog.array', 'goog.events.EventHandler', 'goog.testing.TestCase', 'goog.testing.TestCase.Test', 'goog.testing.asserts']);
goog.addDependency('testing/deferredtestcase.js', ['goog.testing.DeferredTestCase'], ['goog.async.Deferred', 'goog.testing.AsyncTestCase', 'goog.testing.TestCase']);
goog.addDependency('testing/dom.js', ['goog.testing.dom'], ['goog.dom', 'goog.dom.NodeIterator', 'goog.dom.NodeType', 'goog.dom.TagIterator', 'goog.dom.TagName', 'goog.dom.classes', 'goog.iter', 'goog.object', 'goog.string', 'goog.style', 'goog.testing.asserts', 'goog.userAgent']);
goog.addDependency('testing/editor/dom.js', ['goog.testing.editor.dom'], ['goog.dom.NodeType', 'goog.dom.TagIterator', 'goog.dom.TagWalkType', 'goog.iter', 'goog.string', 'goog.testing.asserts']);
goog.addDependency('testing/editor/fieldmock.js', ['goog.testing.editor.FieldMock'], ['goog.dom', 'goog.dom.Range', 'goog.editor.Field', 'goog.testing.LooseMock']);
goog.addDependency('testing/editor/testhelper.js', ['goog.testing.editor.TestHelper'], ['goog.Disposable', 'goog.dom', 'goog.dom.Range', 'goog.editor.BrowserFeature', 'goog.editor.node', 'goog.testing.dom']);
goog.addDependency('testing/events/eventobserver.js', ['goog.testing.events.EventObserver'], ['goog.array']);
goog.addDependency('testing/events/events.js', ['goog.testing.events', 'goog.testing.events.Event'], ['goog.events', 'goog.events.BrowserEvent', 'goog.events.BrowserEvent.MouseButton', 'goog.events.BrowserFeature', 'goog.events.Event', 'goog.events.EventType', 'goog.events.KeyCodes', 'goog.object', 'goog.style', 'goog.userAgent']);
goog.addDependency('testing/events/matchers.js', ['goog.testing.events.EventMatcher'], ['goog.events.Event', 'goog.testing.mockmatchers.ArgumentMatcher']);
goog.addDependency('testing/expectedfailures.js', ['goog.testing.ExpectedFailures'], ['goog.debug.DivConsole', 'goog.debug.Logger', 'goog.dom', 'goog.dom.TagName', 'goog.events', 'goog.events.EventType', 'goog.style', 'goog.testing.JsUnitException', 'goog.testing.TestCase', 'goog.testing.asserts']);
goog.addDependency('testing/fs/blob.js', ['goog.testing.fs.Blob'], ['goog.crypt.base64']);
goog.addDependency('testing/fs/entry.js', ['goog.testing.fs.DirectoryEntry', 'goog.testing.fs.Entry', 'goog.testing.fs.FileEntry'], ['goog.Timer', 'goog.array', 'goog.async.Deferred', 'goog.fs.DirectoryEntry', 'goog.fs.DirectoryEntry.Behavior', 'goog.fs.Error', 'goog.functions', 'goog.object', 'goog.string', 'goog.testing.fs.File', 'goog.testing.fs.FileWriter']);
goog.addDependency('testing/fs/file.js', ['goog.testing.fs.File'], ['goog.testing.fs.Blob']);
goog.addDependency('testing/fs/filereader.js', ['goog.testing.fs.FileReader'], ['goog.Timer', 'goog.events.EventTarget', 'goog.fs.Error', 'goog.fs.FileReader.EventType', 'goog.fs.FileReader.ReadyState', 'goog.testing.fs.File', 'goog.testing.fs.ProgressEvent']);
goog.addDependency('testing/fs/filesystem.js', ['goog.testing.fs.FileSystem'], ['goog.testing.fs.DirectoryEntry']);
goog.addDependency('testing/fs/filewriter.js', ['goog.testing.fs.FileWriter'], ['goog.Timer', 'goog.events.Event', 'goog.events.EventTarget', 'goog.fs.Error', 'goog.fs.FileSaver.EventType', 'goog.fs.FileSaver.ReadyState', 'goog.string', 'goog.testing.fs.File', 'goog.testing.fs.ProgressEvent']);
goog.addDependency('testing/fs/fs.js', ['goog.testing.fs'], ['goog.Timer', 'goog.array', 'goog.fs', 'goog.testing.fs.Blob', 'goog.testing.fs.FileSystem']);
goog.addDependency('testing/fs/progressevent.js', ['goog.testing.fs.ProgressEvent'], ['goog.events.Event']);
goog.addDependency('testing/functionmock.js', ['goog.testing', 'goog.testing.FunctionMock', 'goog.testing.GlobalFunctionMock', 'goog.testing.MethodMock'], ['goog.object', 'goog.testing.LooseMock', 'goog.testing.Mock', 'goog.testing.MockInterface', 'goog.testing.PropertyReplacer', 'goog.testing.StrictMock']);
goog.addDependency('testing/graphics.js', ['goog.testing.graphics'], ['goog.graphics.Path.Segment', 'goog.testing.asserts']);
goog.addDependency('testing/jsunit.js', ['goog.testing.jsunit'], ['goog.testing.TestCase', 'goog.testing.TestRunner']);
goog.addDependency('testing/loosemock.js', ['goog.testing.LooseExpectationCollection', 'goog.testing.LooseMock'], ['goog.array', 'goog.structs.Map', 'goog.testing.Mock']);
goog.addDependency('testing/messaging/mockmessagechannel.js', ['goog.testing.messaging.MockMessageChannel'], ['goog.messaging.AbstractChannel', 'goog.testing.asserts']);
goog.addDependency('testing/messaging/mockmessageevent.js', ['goog.testing.messaging.MockMessageEvent'], ['goog.events.BrowserEvent', 'goog.events.EventType', 'goog.testing.events']);
goog.addDependency('testing/messaging/mockmessageport.js', ['goog.testing.messaging.MockMessagePort'], ['goog.events.EventTarget']);
goog.addDependency('testing/messaging/mockportnetwork.js', ['goog.testing.messaging.MockPortNetwork'], ['goog.messaging.PortNetwork', 'goog.testing.messaging.MockMessageChannel']);
goog.addDependency('testing/mock.js', ['goog.testing.Mock', 'goog.testing.MockExpectation'], ['goog.array', 'goog.testing.JsUnitException', 'goog.testing.MockInterface', 'goog.testing.mockmatchers']);
goog.addDependency('testing/mockclassfactory.js', ['goog.testing.MockClassFactory', 'goog.testing.MockClassRecord'], ['goog.array', 'goog.object', 'goog.testing.LooseMock', 'goog.testing.StrictMock', 'goog.testing.TestCase', 'goog.testing.mockmatchers']);
goog.addDependency('testing/mockclock.js', ['goog.testing.MockClock'], ['goog.Disposable', 'goog.testing.PropertyReplacer']);
goog.addDependency('testing/mockcontrol.js', ['goog.testing.MockControl'], ['goog.array', 'goog.testing', 'goog.testing.LooseMock', 'goog.testing.MockInterface', 'goog.testing.StrictMock']);
goog.addDependency('testing/mockinterface.js', ['goog.testing.MockInterface'], []);
goog.addDependency('testing/mockmatchers.js', ['goog.testing.mockmatchers', 'goog.testing.mockmatchers.ArgumentMatcher', 'goog.testing.mockmatchers.IgnoreArgument', 'goog.testing.mockmatchers.InstanceOf', 'goog.testing.mockmatchers.ObjectEquals', 'goog.testing.mockmatchers.RegexpMatch', 'goog.testing.mockmatchers.SaveArgument', 'goog.testing.mockmatchers.TypeOf'], ['goog.array', 'goog.dom', 'goog.testing.asserts']);
goog.addDependency('testing/mockrandom.js', ['goog.testing.MockRandom'], ['goog.Disposable']);
goog.addDependency('testing/mockrange.js', ['goog.testing.MockRange'], ['goog.dom.AbstractRange', 'goog.testing.LooseMock']);
goog.addDependency('testing/mockstorage.js', ['goog.testing.MockStorage'], ['goog.structs.Map']);
goog.addDependency('testing/mockuseragent.js', ['goog.testing.MockUserAgent'], ['goog.Disposable', 'goog.userAgent']);
goog.addDependency('testing/multitestrunner.js', ['goog.testing.MultiTestRunner', 'goog.testing.MultiTestRunner.TestFrame'], ['goog.Timer', 'goog.array', 'goog.dom', 'goog.dom.classes', 'goog.events.EventHandler', 'goog.functions', 'goog.string', 'goog.ui.Component', 'goog.ui.ServerChart', 'goog.ui.ServerChart.ChartType', 'goog.ui.TableSorter']);
goog.addDependency('testing/net/xhrio.js', ['goog.testing.net.XhrIo'], ['goog.array', 'goog.dom.xml', 'goog.events', 'goog.events.EventTarget', 'goog.json', 'goog.net.ErrorCode', 'goog.net.EventType', 'goog.net.HttpStatus', 'goog.net.XhrIo.ResponseType', 'goog.net.XmlHttp', 'goog.object', 'goog.structs.Map', 'goog.uri.utils']);
goog.addDependency('testing/net/xhriopool.js', ['goog.testing.net.XhrIoPool'], ['goog.net.XhrIoPool', 'goog.testing.net.XhrIo']);
goog.addDependency('testing/objectpropertystring.js', ['goog.testing.ObjectPropertyString'], []);
goog.addDependency('testing/performancetable.js', ['goog.testing.PerformanceTable'], ['goog.dom', 'goog.testing.PerformanceTimer']);
goog.addDependency('testing/performancetimer.js', ['goog.testing.PerformanceTimer'], ['goog.array', 'goog.math']);
goog.addDependency('testing/propertyreplacer.js', ['goog.testing.PropertyReplacer'], ['goog.userAgent']);
goog.addDependency('testing/pseudorandom.js', ['goog.testing.PseudoRandom'], ['goog.Disposable']);
goog.addDependency('testing/recordfunction.js', ['goog.testing.FunctionCall', 'goog.testing.recordConstructor', 'goog.testing.recordFunction'], []);
goog.addDependency('testing/shardingtestcase.js', ['goog.testing.ShardingTestCase'], ['goog.asserts', 'goog.testing.TestCase']);
goog.addDependency('testing/singleton.js', ['goog.testing.singleton'], ['goog.array']);
goog.addDependency('testing/stacktrace.js', ['goog.testing.stacktrace', 'goog.testing.stacktrace.Frame'], []);
goog.addDependency('testing/strictmock.js', ['goog.testing.StrictMock'], ['goog.array', 'goog.testing.Mock']);
goog.addDependency('testing/style/layoutasserts.js', ['goog.testing.style.layoutasserts'], ['goog.style', 'goog.testing.asserts', 'goog.testing.style']);
goog.addDependency('testing/style/style.js', ['goog.testing.style'], ['goog.math.Rect', 'goog.style']);
goog.addDependency('testing/testcase.js', ['goog.testing.TestCase', 'goog.testing.TestCase.Error', 'goog.testing.TestCase.Order', 'goog.testing.TestCase.Result', 'goog.testing.TestCase.Test'], ['goog.testing.asserts', 'goog.testing.stacktrace']);
goog.addDependency('testing/testqueue.js', ['goog.testing.TestQueue'], []);
goog.addDependency('testing/testrunner.js', ['goog.testing.TestRunner'], ['goog.testing.TestCase']);
goog.addDependency('testing/ui/rendererasserts.js', ['goog.testing.ui.rendererasserts'], ['goog.testing.asserts']);
goog.addDependency('testing/ui/rendererharness.js', ['goog.testing.ui.RendererHarness'], ['goog.Disposable', 'goog.dom.NodeType', 'goog.testing.asserts']);
goog.addDependency('testing/ui/style.js', ['goog.testing.ui.style'], ['goog.array', 'goog.dom', 'goog.dom.classes', 'goog.testing.asserts']);
goog.addDependency('timer/timer.js', ['goog.Timer'], ['goog.events.EventTarget']);
goog.addDependency('tweak/entries.js', ['goog.tweak.BaseEntry', 'goog.tweak.BasePrimitiveSetting', 'goog.tweak.BaseSetting', 'goog.tweak.BooleanGroup', 'goog.tweak.BooleanInGroupSetting', 'goog.tweak.BooleanSetting', 'goog.tweak.ButtonAction', 'goog.tweak.NumericSetting', 'goog.tweak.StringSetting'], ['goog.array', 'goog.asserts', 'goog.debug.Logger', 'goog.object']);
goog.addDependency('tweak/registry.js', ['goog.tweak.Registry'], ['goog.asserts', 'goog.debug.Logger', 'goog.object', 'goog.string', 'goog.tweak.BaseEntry', 'goog.uri.utils']);
goog.addDependency('tweak/testhelpers.js', ['goog.tweak.testhelpers'], ['goog.tweak']);
goog.addDependency('tweak/tweak.js', ['goog.tweak', 'goog.tweak.ConfigParams'], ['goog.asserts', 'goog.tweak.BooleanGroup', 'goog.tweak.BooleanInGroupSetting', 'goog.tweak.BooleanSetting', 'goog.tweak.ButtonAction', 'goog.tweak.NumericSetting', 'goog.tweak.Registry', 'goog.tweak.StringSetting']);
goog.addDependency('tweak/tweakui.js', ['goog.tweak.EntriesPanel', 'goog.tweak.TweakUi'], ['goog.array', 'goog.asserts', 'goog.dom.DomHelper', 'goog.object', 'goog.style', 'goog.tweak', 'goog.ui.Zippy', 'goog.userAgent']);
goog.addDependency('ui/abstractspellchecker.js', ['goog.ui.AbstractSpellChecker', 'goog.ui.AbstractSpellChecker.AsyncResult'], ['goog.asserts', 'goog.dom', 'goog.dom.classes', 'goog.dom.selection', 'goog.events.EventType', 'goog.math.Coordinate', 'goog.spell.SpellCheck', 'goog.structs.Set', 'goog.style', 'goog.ui.MenuItem', 'goog.ui.MenuSeparator', 'goog.ui.PopupMenu']);
goog.addDependency('ui/activitymonitor.js', ['goog.ui.ActivityMonitor'], ['goog.array', 'goog.dom', 'goog.events', 'goog.events.EventHandler', 'goog.events.EventTarget', 'goog.events.EventType']);
goog.addDependency('ui/advancedtooltip.js', ['goog.ui.AdvancedTooltip'], ['goog.events.EventType', 'goog.math.Coordinate', 'goog.ui.Tooltip', 'goog.userAgent']);
goog.addDependency('ui/animatedzippy.js', ['goog.ui.AnimatedZippy'], ['goog.dom', 'goog.events', 'goog.fx.Animation', 'goog.fx.Animation.EventType', 'goog.fx.Transition.EventType', 'goog.fx.easing', 'goog.ui.Zippy', 'goog.ui.ZippyEvent']);
goog.addDependency('ui/attachablemenu.js', ['goog.ui.AttachableMenu'], ['goog.dom.a11y', 'goog.dom.a11y.State', 'goog.events.KeyCodes', 'goog.ui.ItemEvent', 'goog.ui.MenuBase']);
goog.addDependency('ui/autocomplete/arraymatcher.js', ['goog.ui.AutoComplete.ArrayMatcher'], ['goog.iter', 'goog.string', 'goog.ui.AutoComplete']);
goog.addDependency('ui/autocomplete/autocomplete.js', ['goog.ui.AutoComplete', 'goog.ui.AutoComplete.EventType'], ['goog.events', 'goog.events.EventTarget']);
goog.addDependency('ui/autocomplete/basic.js', ['goog.ui.AutoComplete.Basic'], ['goog.ui.AutoComplete', 'goog.ui.AutoComplete.ArrayMatcher', 'goog.ui.AutoComplete.InputHandler', 'goog.ui.AutoComplete.Renderer']);
goog.addDependency('ui/autocomplete/inputhandler.js', ['goog.ui.AutoComplete.InputHandler'], ['goog.Disposable', 'goog.Timer', 'goog.dom', 'goog.dom.a11y', 'goog.dom.selection', 'goog.events.EventHandler', 'goog.events.EventType', 'goog.events.KeyCodes', 'goog.events.KeyHandler', 'goog.events.KeyHandler.EventType', 'goog.string', 'goog.ui.AutoComplete', 'goog.userAgent', 'goog.userAgent.product']);
goog.addDependency('ui/autocomplete/remote.js', ['goog.ui.AutoComplete.Remote'], ['goog.ui.AutoComplete', 'goog.ui.AutoComplete.InputHandler', 'goog.ui.AutoComplete.RemoteArrayMatcher', 'goog.ui.AutoComplete.Renderer']);
goog.addDependency('ui/autocomplete/remotearraymatcher.js', ['goog.ui.AutoComplete.RemoteArrayMatcher'], ['goog.Disposable', 'goog.Uri', 'goog.events', 'goog.json', 'goog.net.XhrIo', 'goog.ui.AutoComplete']);
goog.addDependency('ui/autocomplete/renderer.js', ['goog.ui.AutoComplete.Renderer', 'goog.ui.AutoComplete.Renderer.CustomRenderer'], ['goog.dispose', 'goog.dom', 'goog.dom.a11y', 'goog.dom.classes', 'goog.events.Event', 'goog.events.EventTarget', 'goog.events.EventType', 'goog.fx.dom.FadeInAndShow', 'goog.fx.dom.FadeOutAndHide', 'goog.iter', 'goog.string', 'goog.style', 'goog.ui.AutoComplete', 'goog.ui.AutoComplete.EventType', 'goog.ui.IdGenerator', 'goog.userAgent']);
goog.addDependency('ui/autocomplete/richinputhandler.js', ['goog.ui.AutoComplete.RichInputHandler'], ['goog.ui.AutoComplete', 'goog.ui.AutoComplete.InputHandler']);
goog.addDependency('ui/autocomplete/richremote.js', ['goog.ui.AutoComplete.RichRemote'], ['goog.ui.AutoComplete', 'goog.ui.AutoComplete.Remote', 'goog.ui.AutoComplete.Renderer', 'goog.ui.AutoComplete.RichInputHandler', 'goog.ui.AutoComplete.RichRemoteArrayMatcher']);
goog.addDependency('ui/autocomplete/richremotearraymatcher.js', ['goog.ui.AutoComplete.RichRemoteArrayMatcher'], ['goog.ui.AutoComplete', 'goog.ui.AutoComplete.RemoteArrayMatcher']);
goog.addDependency('ui/basicmenu.js', ['goog.ui.BasicMenu', 'goog.ui.BasicMenu.Item', 'goog.ui.BasicMenu.Separator'], ['goog.array', 'goog.dom', 'goog.dom.a11y', 'goog.events.EventType', 'goog.positioning', 'goog.positioning.AnchoredPosition', 'goog.positioning.Corner', 'goog.ui.AttachableMenu', 'goog.ui.ItemEvent']);
goog.addDependency('ui/bidiinput.js', ['goog.ui.BidiInput'], ['goog.events', 'goog.events.InputHandler', 'goog.i18n.bidi', 'goog.ui.Component']);
goog.addDependency('ui/bubble.js', ['goog.ui.Bubble'], ['goog.Timer', 'goog.dom', 'goog.events', 'goog.events.Event', 'goog.events.EventType', 'goog.math.Box', 'goog.positioning', 'goog.positioning.AbsolutePosition', 'goog.positioning.AbstractPosition', 'goog.positioning.AnchoredPosition', 'goog.positioning.Corner', 'goog.style', 'goog.ui.Component', 'goog.ui.Popup', 'goog.ui.Popup.AnchoredPosition']);
goog.addDependency('ui/button.js', ['goog.ui.Button', 'goog.ui.Button.Side'], ['goog.events.KeyCodes', 'goog.ui.ButtonRenderer', 'goog.ui.ButtonSide', 'goog.ui.Control', 'goog.ui.ControlContent', 'goog.ui.NativeButtonRenderer']);
goog.addDependency('ui/buttonrenderer.js', ['goog.ui.ButtonRenderer'], ['goog.dom.a11y', 'goog.dom.a11y.Role', 'goog.dom.a11y.State', 'goog.ui.ButtonSide', 'goog.ui.Component.State', 'goog.ui.ControlRenderer']);
goog.addDependency('ui/buttonside.js', ['goog.ui.ButtonSide'], []);
goog.addDependency('ui/cccbutton.js', ['goog.ui.CccButton'], ['goog.dom', 'goog.dom.classes', 'goog.events', 'goog.events.Event', 'goog.events.EventType', 'goog.ui.DeprecatedButton', 'goog.userAgent']);
goog.addDependency('ui/charcounter.js', ['goog.ui.CharCounter', 'goog.ui.CharCounter.Display'], ['goog.dom', 'goog.events', 'goog.events.EventTarget', 'goog.events.InputHandler']);
goog.addDependency('ui/charpicker.js', ['goog.ui.CharPicker'], ['goog.array', 'goog.dom', 'goog.events', 'goog.events.EventHandler', 'goog.events.EventType', 'goog.events.InputHandler', 'goog.events.KeyHandler', 'goog.i18n.CharListDecompressor', 'goog.i18n.uChar', 'goog.structs.Set', 'goog.style', 'goog.ui.Button', 'goog.ui.Component', 'goog.ui.ContainerScroller', 'goog.ui.FlatButtonRenderer', 'goog.ui.HoverCard', 'goog.ui.LabelInput', 'goog.ui.Menu', 'goog.ui.MenuButton', 'goog.ui.MenuItem', 'goog.ui.Tooltip.ElementTooltipPosition']);
goog.addDependency('ui/checkbox.js', ['goog.ui.Checkbox', 'goog.ui.Checkbox.State'], ['goog.dom.a11y', 'goog.dom.a11y.State', 'goog.events.EventType', 'goog.events.KeyCodes', 'goog.ui.CheckboxRenderer', 'goog.ui.Component.EventType', 'goog.ui.Component.State', 'goog.ui.Control', 'goog.ui.registry']);
goog.addDependency('ui/checkboxmenuitem.js', ['goog.ui.CheckBoxMenuItem'], ['goog.ui.ControlContent', 'goog.ui.MenuItem', 'goog.ui.registry']);
goog.addDependency('ui/checkboxrenderer.js', ['goog.ui.CheckboxRenderer'], ['goog.array', 'goog.asserts', 'goog.dom.a11y', 'goog.dom.a11y.Role', 'goog.dom.a11y.State', 'goog.dom.classes', 'goog.object', 'goog.ui.ControlRenderer']);
goog.addDependency('ui/colorbutton.js', ['goog.ui.ColorButton'], ['goog.ui.Button', 'goog.ui.ColorButtonRenderer', 'goog.ui.registry']);
goog.addDependency('ui/colorbuttonrenderer.js', ['goog.ui.ColorButtonRenderer'], ['goog.dom.classes', 'goog.functions', 'goog.ui.ColorMenuButtonRenderer']);
goog.addDependency('ui/colormenubutton.js', ['goog.ui.ColorMenuButton'], ['goog.array', 'goog.object', 'goog.ui.ColorMenuButtonRenderer', 'goog.ui.ColorPalette', 'goog.ui.Component.EventType', 'goog.ui.ControlContent', 'goog.ui.Menu', 'goog.ui.MenuButton', 'goog.ui.registry']);
goog.addDependency('ui/colormenubuttonrenderer.js', ['goog.ui.ColorMenuButtonRenderer'], ['goog.color', 'goog.dom.classes', 'goog.ui.ControlContent', 'goog.ui.MenuButtonRenderer', 'goog.userAgent']);
goog.addDependency('ui/colorpalette.js', ['goog.ui.ColorPalette'], ['goog.array', 'goog.color', 'goog.dom', 'goog.style', 'goog.ui.Palette', 'goog.ui.PaletteRenderer']);
goog.addDependency('ui/colorpicker.js', ['goog.ui.ColorPicker', 'goog.ui.ColorPicker.EventType'], ['goog.ui.ColorPalette', 'goog.ui.Component', 'goog.ui.Component.State']);
goog.addDependency('ui/colorsplitbehavior.js', ['goog.ui.ColorSplitBehavior'], ['goog.ui.ColorButton', 'goog.ui.ColorMenuButton', 'goog.ui.SplitBehavior']);
goog.addDependency('ui/combobox.js', ['goog.ui.ComboBox', 'goog.ui.ComboBoxItem'], ['goog.Timer', 'goog.debug.Logger', 'goog.dom.classes', 'goog.events', 'goog.events.InputHandler', 'goog.events.KeyCodes', 'goog.events.KeyHandler', 'goog.string', 'goog.style', 'goog.ui.Component', 'goog.ui.ItemEvent', 'goog.ui.LabelInput', 'goog.ui.Menu', 'goog.ui.MenuItem', 'goog.ui.registry', 'goog.userAgent']);
goog.addDependency('ui/component.js', ['goog.ui.Component', 'goog.ui.Component.Error', 'goog.ui.Component.EventType', 'goog.ui.Component.State'], ['goog.array', 'goog.array.ArrayLike', 'goog.dom', 'goog.events.EventHandler', 'goog.events.EventTarget', 'goog.object', 'goog.style', 'goog.ui.IdGenerator']);
goog.addDependency('ui/container.js', ['goog.ui.Container', 'goog.ui.Container.EventType', 'goog.ui.Container.Orientation'], ['goog.dom', 'goog.dom.a11y', 'goog.dom.a11y.State', 'goog.events.EventType', 'goog.events.KeyCodes', 'goog.events.KeyHandler', 'goog.events.KeyHandler.EventType', 'goog.style', 'goog.ui.Component', 'goog.ui.Component.Error', 'goog.ui.Component.EventType', 'goog.ui.Component.State', 'goog.ui.ContainerRenderer']);
goog.addDependency('ui/containerrenderer.js', ['goog.ui.ContainerRenderer'], ['goog.array', 'goog.dom', 'goog.dom.a11y', 'goog.dom.classes', 'goog.string', 'goog.style', 'goog.ui.Separator', 'goog.ui.registry', 'goog.userAgent']);
goog.addDependency('ui/containerscroller.js', ['goog.ui.ContainerScroller'], ['goog.Timer', 'goog.events.EventHandler', 'goog.style', 'goog.ui.Component', 'goog.ui.Component.EventType', 'goog.ui.Container.EventType']);
goog.addDependency('ui/control.js', ['goog.ui.Control'], ['goog.array', 'goog.dom', 'goog.events.BrowserEvent.MouseButton', 'goog.events.Event', 'goog.events.EventType', 'goog.events.KeyCodes', 'goog.events.KeyHandler', 'goog.events.KeyHandler.EventType', 'goog.string', 'goog.ui.Component', 'goog.ui.Component.Error', 'goog.ui.Component.EventType', 'goog.ui.Component.State', 'goog.ui.ControlContent', 'goog.ui.ControlRenderer', 'goog.ui.decorate', 'goog.ui.registry', 'goog.userAgent']);
goog.addDependency('ui/controlcontent.js', ['goog.ui.ControlContent'], []);
goog.addDependency('ui/controlrenderer.js', ['goog.ui.ControlRenderer'], ['goog.array', 'goog.dom', 'goog.dom.a11y', 'goog.dom.a11y.State', 'goog.dom.classes', 'goog.object', 'goog.style', 'goog.ui.Component.State', 'goog.ui.ControlContent', 'goog.userAgent']);
goog.addDependency('ui/cookieeditor.js', ['goog.ui.CookieEditor'], ['goog.dom', 'goog.dom.TagName', 'goog.events.EventType', 'goog.net.cookies', 'goog.string', 'goog.style', 'goog.ui.Component']);
goog.addDependency('ui/css3buttonrenderer.js', ['goog.ui.Css3ButtonRenderer'], ['goog.dom', 'goog.dom.TagName', 'goog.dom.classes', 'goog.ui.Button', 'goog.ui.ButtonRenderer', 'goog.ui.ControlContent', 'goog.ui.INLINE_BLOCK_CLASSNAME', 'goog.ui.registry']);
goog.addDependency('ui/css3menubuttonrenderer.js', ['goog.ui.Css3MenuButtonRenderer'], ['goog.dom', 'goog.dom.TagName', 'goog.ui.ControlContent', 'goog.ui.INLINE_BLOCK_CLASSNAME', 'goog.ui.MenuButton', 'goog.ui.MenuButtonRenderer', 'goog.ui.registry']);
goog.addDependency('ui/cssnames.js', ['goog.ui.INLINE_BLOCK_CLASSNAME'], []);
goog.addDependency('ui/custombutton.js', ['goog.ui.CustomButton'], ['goog.ui.Button', 'goog.ui.ControlContent', 'goog.ui.CustomButtonRenderer', 'goog.ui.registry']);
goog.addDependency('ui/custombuttonrenderer.js', ['goog.ui.CustomButtonRenderer'], ['goog.dom', 'goog.dom.classes', 'goog.string', 'goog.ui.ButtonRenderer', 'goog.ui.ControlContent', 'goog.ui.INLINE_BLOCK_CLASSNAME']);
goog.addDependency('ui/customcolorpalette.js', ['goog.ui.CustomColorPalette'], ['goog.color', 'goog.dom', 'goog.ui.ColorPalette']);
goog.addDependency('ui/datepicker.js', ['goog.ui.DatePicker', 'goog.ui.DatePicker.Events', 'goog.ui.DatePickerEvent'], ['goog.date', 'goog.date.Date', 'goog.date.Interval', 'goog.dom', 'goog.dom.a11y', 'goog.dom.classes', 'goog.events', 'goog.events.Event', 'goog.events.EventType', 'goog.events.KeyHandler', 'goog.events.KeyHandler.EventType', 'goog.i18n.DateTimeFormat', 'goog.i18n.DateTimeSymbols', 'goog.style', 'goog.ui.Component']);
goog.addDependency('ui/decorate.js', ['goog.ui.decorate'], ['goog.ui.registry']);
goog.addDependency('ui/deprecatedbutton.js', ['goog.ui.DeprecatedButton'], ['goog.dom', 'goog.events', 'goog.events.Event', 'goog.events.EventTarget', 'goog.events.EventType']);
goog.addDependency('ui/dialog.js', ['goog.ui.Dialog', 'goog.ui.Dialog.ButtonSet', 'goog.ui.Dialog.ButtonSet.DefaultButtons', 'goog.ui.Dialog.DefaultButtonCaptions', 'goog.ui.Dialog.DefaultButtonKeys', 'goog.ui.Dialog.Event', 'goog.ui.Dialog.EventType'], ['goog.asserts', 'goog.dom', 'goog.dom.NodeType', 'goog.dom.TagName', 'goog.dom.a11y', 'goog.dom.classes', 'goog.events.Event', 'goog.events.EventType', 'goog.events.KeyCodes', 'goog.fx.Dragger', 'goog.math.Rect', 'goog.structs', 'goog.structs.Map', 'goog.style', 'goog.ui.ModalPopup', 'goog.userAgent']);
goog.addDependency('ui/dimensionpicker.js', ['goog.ui.DimensionPicker'], ['goog.events.EventType', 'goog.math.Size', 'goog.ui.Control', 'goog.ui.DimensionPickerRenderer', 'goog.ui.registry']);
goog.addDependency('ui/dimensionpickerrenderer.js', ['goog.ui.DimensionPickerRenderer'], ['goog.dom', 'goog.dom.TagName', 'goog.i18n.bidi', 'goog.style', 'goog.ui.ControlRenderer', 'goog.userAgent']);
goog.addDependency('ui/dragdropdetector.js', ['goog.ui.DragDropDetector', 'goog.ui.DragDropDetector.EventType', 'goog.ui.DragDropDetector.ImageDropEvent', 'goog.ui.DragDropDetector.LinkDropEvent'], ['goog.dom', 'goog.dom.TagName', 'goog.events.Event', 'goog.events.EventHandler', 'goog.events.EventTarget', 'goog.events.EventType', 'goog.math.Coordinate', 'goog.string', 'goog.style', 'goog.userAgent']);
goog.addDependency('ui/drilldownrow.js', ['goog.ui.DrilldownRow'], ['goog.dom', 'goog.dom.classes', 'goog.events', 'goog.ui.Component']);
goog.addDependency('ui/editor/abstractdialog.js', ['goog.ui.editor.AbstractDialog', 'goog.ui.editor.AbstractDialog.Builder', 'goog.ui.editor.AbstractDialog.EventType'], ['goog.dom', 'goog.dom.classes', 'goog.events.EventTarget', 'goog.ui.Dialog', 'goog.ui.Dialog.ButtonSet', 'goog.ui.Dialog.DefaultButtonKeys', 'goog.ui.Dialog.Event', 'goog.ui.Dialog.EventType']);
goog.addDependency('ui/editor/bubble.js', ['goog.ui.editor.Bubble'], ['goog.debug.Logger', 'goog.dom', 'goog.dom.ViewportSizeMonitor', 'goog.editor.style', 'goog.events', 'goog.events.EventHandler', 'goog.events.EventType', 'goog.positioning', 'goog.string', 'goog.style', 'goog.ui.Component.EventType', 'goog.ui.PopupBase', 'goog.ui.PopupBase.EventType', 'goog.userAgent']);
goog.addDependency('ui/editor/defaulttoolbar.js', ['goog.ui.editor.DefaultToolbar'], ['goog.dom', 'goog.dom.TagName', 'goog.dom.classes', 'goog.editor.Command', 'goog.string.StringBuffer', 'goog.style', 'goog.ui.ControlContent', 'goog.ui.editor.ToolbarFactory', 'goog.ui.editor.messages']);
goog.addDependency('ui/editor/equationeditordialog.js', ['goog.ui.editor.EquationEditorDialog'], ['goog.editor.Command', 'goog.ui.editor.AbstractDialog', 'goog.ui.editor.EquationEditorOkEvent', 'goog.ui.equation.ChangeEvent', 'goog.ui.equation.TexEditor']);
goog.addDependency('ui/editor/equationeditorokevent.js', ['goog.ui.editor.EquationEditorOkEvent'], ['goog.events.Event', 'goog.ui.editor.AbstractDialog']);
goog.addDependency('ui/editor/linkdialog.js', ['goog.ui.editor.LinkDialog', 'goog.ui.editor.LinkDialog.BeforeTestLinkEvent', 'goog.ui.editor.LinkDialog.EventType', 'goog.ui.editor.LinkDialog.OkEvent'], ['goog.dom', 'goog.dom.DomHelper', 'goog.dom.TagName', 'goog.dom.classes', 'goog.dom.selection', 'goog.editor.BrowserFeature', 'goog.editor.Link', 'goog.editor.focus', 'goog.events', 'goog.events.EventHandler', 'goog.events.EventType', 'goog.events.InputHandler', 'goog.events.InputHandler.EventType', 'goog.string', 'goog.style', 'goog.ui.Button', 'goog.ui.LinkButtonRenderer', 'goog.ui.editor.AbstractDialog', 'goog.ui.editor.AbstractDialog.Builder', 'goog.ui.editor.AbstractDialog.EventType', 'goog.ui.editor.TabPane', 'goog.ui.editor.messages', 'goog.userAgent', 'goog.window']);
goog.addDependency('ui/editor/messages.js', ['goog.ui.editor.messages'], []);
goog.addDependency('ui/editor/tabpane.js', ['goog.ui.editor.TabPane'], ['goog.dom.TagName', 'goog.events.EventHandler', 'goog.ui.Component', 'goog.ui.Control', 'goog.ui.Tab', 'goog.ui.TabBar']);
goog.addDependency('ui/editor/toolbarcontroller.js', ['goog.ui.editor.ToolbarController'], ['goog.editor.Field.EventType', 'goog.events.EventHandler', 'goog.events.EventTarget', 'goog.ui.Component.EventType']);
goog.addDependency('ui/editor/toolbarfactory.js', ['goog.ui.editor.ToolbarFactory'], ['goog.array', 'goog.dom', 'goog.string', 'goog.string.Unicode', 'goog.style', 'goog.ui.Component.State', 'goog.ui.Container.Orientation', 'goog.ui.ControlContent', 'goog.ui.Option', 'goog.ui.Toolbar', 'goog.ui.ToolbarButton', 'goog.ui.ToolbarColorMenuButton', 'goog.ui.ToolbarMenuButton', 'goog.ui.ToolbarRenderer', 'goog.ui.ToolbarSelect', 'goog.userAgent']);
goog.addDependency('ui/emoji/emoji.js', ['goog.ui.emoji.Emoji'], []);
goog.addDependency('ui/emoji/emojipalette.js', ['goog.ui.emoji.EmojiPalette'], ['goog.events.Event', 'goog.events.EventType', 'goog.net.ImageLoader', 'goog.ui.Palette', 'goog.ui.emoji.Emoji', 'goog.ui.emoji.EmojiPaletteRenderer']);
goog.addDependency('ui/emoji/emojipaletterenderer.js', ['goog.ui.emoji.EmojiPaletteRenderer'], ['goog.dom', 'goog.dom.a11y', 'goog.ui.PaletteRenderer', 'goog.ui.emoji.Emoji', 'goog.ui.emoji.SpriteInfo']);
goog.addDependency('ui/emoji/emojipicker.js', ['goog.ui.emoji.EmojiPicker'], ['goog.debug.Logger', 'goog.dom', 'goog.ui.Component', 'goog.ui.TabPane', 'goog.ui.TabPane.TabPage', 'goog.ui.emoji.Emoji', 'goog.ui.emoji.EmojiPalette', 'goog.ui.emoji.EmojiPaletteRenderer', 'goog.ui.emoji.ProgressiveEmojiPaletteRenderer']);
goog.addDependency('ui/emoji/popupemojipicker.js', ['goog.ui.emoji.PopupEmojiPicker'], ['goog.dom', 'goog.events.EventType', 'goog.positioning.AnchoredPosition', 'goog.ui.Component', 'goog.ui.Popup', 'goog.ui.emoji.EmojiPicker']);
goog.addDependency('ui/emoji/progressiveemojipaletterenderer.js', ['goog.ui.emoji.ProgressiveEmojiPaletteRenderer'], ['goog.ui.emoji.EmojiPaletteRenderer']);
goog.addDependency('ui/emoji/spriteinfo.js', ['goog.ui.emoji.SpriteInfo'], []);
goog.addDependency('ui/equation/arrowpalette.js', ['goog.ui.equation.ArrowPalette'], ['goog.math.Size', 'goog.ui.equation.Palette']);
goog.addDependency('ui/equation/changeevent.js', ['goog.ui.equation.ChangeEvent'], ['goog.events.Event', 'goog.events.EventType']);
goog.addDependency('ui/equation/comparisonpalette.js', ['goog.ui.equation.ComparisonPalette'], ['goog.math.Size', 'goog.ui.equation.Palette']);
goog.addDependency('ui/equation/editorpane.js', ['goog.ui.equation.EditorPane'], ['goog.dom', 'goog.style', 'goog.ui.Component']);
goog.addDependency('ui/equation/equationeditor.js', ['goog.ui.equation.EquationEditor'], ['goog.dom', 'goog.events', 'goog.ui.Component', 'goog.ui.Tab', 'goog.ui.TabBar', 'goog.ui.equation.EditorPane', 'goog.ui.equation.ImageRenderer', 'goog.ui.equation.TexPane']);
goog.addDependency('ui/equation/equationeditordialog.js', ['goog.ui.equation.EquationEditorDialog'], ['goog.dom', 'goog.ui.Dialog', 'goog.ui.Dialog.ButtonSet', 'goog.ui.equation.EquationEditor', 'goog.ui.equation.ImageRenderer', 'goog.ui.equation.TexEditor']);
goog.addDependency('ui/equation/greekpalette.js', ['goog.ui.equation.GreekPalette'], ['goog.math.Size', 'goog.ui.equation.Palette']);
goog.addDependency('ui/equation/imagerenderer.js', ['goog.ui.equation.ImageRenderer'], ['goog.dom.TagName', 'goog.dom.classes', 'goog.string', 'goog.uri.utils']);
goog.addDependency('ui/equation/mathpalette.js', ['goog.ui.equation.MathPalette'], ['goog.math.Size', 'goog.ui.equation.Palette']);
goog.addDependency('ui/equation/menupalette.js', ['goog.ui.equation.MenuPalette', 'goog.ui.equation.MenuPaletteRenderer'], ['goog.math.Size', 'goog.style', 'goog.ui.equation.Palette', 'goog.ui.equation.PaletteRenderer']);
goog.addDependency('ui/equation/palette.js', ['goog.ui.equation.Palette', 'goog.ui.equation.PaletteEvent', 'goog.ui.equation.PaletteRenderer'], ['goog.dom', 'goog.dom.TagName', 'goog.ui.Palette', 'goog.ui.equation.ImageRenderer']);
goog.addDependency('ui/equation/palettemanager.js', ['goog.ui.equation.PaletteManager'], ['goog.Timer', 'goog.events.EventTarget', 'goog.ui.equation.ArrowPalette', 'goog.ui.equation.ComparisonPalette', 'goog.ui.equation.GreekPalette', 'goog.ui.equation.MathPalette', 'goog.ui.equation.MenuPalette', 'goog.ui.equation.Palette', 'goog.ui.equation.SymbolPalette']);
goog.addDependency('ui/equation/symbolpalette.js', ['goog.ui.equation.SymbolPalette'], ['goog.math.Size', 'goog.ui.equation.Palette']);
goog.addDependency('ui/equation/texeditor.js', ['goog.ui.equation.TexEditor'], ['goog.dom', 'goog.ui.Component', 'goog.ui.equation.ImageRenderer', 'goog.ui.equation.TexPane']);
goog.addDependency('ui/equation/texpane.js', ['goog.ui.equation.TexPane'], ['goog.Timer', 'goog.dom', 'goog.dom.TagName', 'goog.dom.selection', 'goog.events', 'goog.events.EventType', 'goog.events.InputHandler', 'goog.string', 'goog.style', 'goog.ui.Component', 'goog.ui.equation.ChangeEvent', 'goog.ui.equation.EditorPane', 'goog.ui.equation.ImageRenderer', 'goog.ui.equation.PaletteManager']);
goog.addDependency('ui/filteredmenu.js', ['goog.ui.FilteredMenu'], ['goog.dom', 'goog.events.EventType', 'goog.events.InputHandler', 'goog.events.KeyCodes', 'goog.string', 'goog.ui.FilterObservingMenuItem', 'goog.ui.Menu']);
goog.addDependency('ui/filterobservingmenuitem.js', ['goog.ui.FilterObservingMenuItem'], ['goog.ui.ControlContent', 'goog.ui.FilterObservingMenuItemRenderer', 'goog.ui.MenuItem', 'goog.ui.registry']);
goog.addDependency('ui/filterobservingmenuitemrenderer.js', ['goog.ui.FilterObservingMenuItemRenderer'], ['goog.ui.MenuItemRenderer']);
goog.addDependency('ui/flatbuttonrenderer.js', ['goog.ui.FlatButtonRenderer'], ['goog.dom.classes', 'goog.ui.Button', 'goog.ui.ButtonRenderer', 'goog.ui.INLINE_BLOCK_CLASSNAME', 'goog.ui.registry']);
goog.addDependency('ui/flatmenubuttonrenderer.js', ['goog.ui.FlatMenuButtonRenderer'], ['goog.style', 'goog.ui.ControlContent', 'goog.ui.FlatButtonRenderer', 'goog.ui.INLINE_BLOCK_CLASSNAME', 'goog.ui.Menu', 'goog.ui.MenuButton', 'goog.ui.MenuRenderer', 'goog.ui.registry']);
goog.addDependency('ui/formpost.js', ['goog.ui.FormPost'], ['goog.array', 'goog.dom.TagName', 'goog.string', 'goog.string.StringBuffer', 'goog.ui.Component']);
goog.addDependency('ui/gauge.js', ['goog.ui.Gauge', 'goog.ui.GaugeColoredRange'], ['goog.dom', 'goog.dom.a11y', 'goog.fx.Animation', 'goog.fx.Animation.EventType', 'goog.fx.Transition.EventType', 'goog.fx.easing', 'goog.graphics', 'goog.graphics.Font', 'goog.graphics.Path', 'goog.graphics.SolidFill', 'goog.ui.Component', 'goog.ui.GaugeTheme']);
goog.addDependency('ui/gaugetheme.js', ['goog.ui.GaugeTheme'], ['goog.graphics.LinearGradient', 'goog.graphics.SolidFill', 'goog.graphics.Stroke']);
goog.addDependency('ui/hovercard.js', ['goog.ui.HoverCard', 'goog.ui.HoverCard.EventType', 'goog.ui.HoverCard.TriggerEvent'], ['goog.dom', 'goog.events', 'goog.events.EventType', 'goog.ui.AdvancedTooltip']);
goog.addDependency('ui/hsvapalette.js', ['goog.ui.HsvaPalette'], ['goog.array', 'goog.color', 'goog.color.alpha', 'goog.events.EventType', 'goog.ui.Component.EventType', 'goog.ui.HsvPalette']);
goog.addDependency('ui/hsvpalette.js', ['goog.ui.HsvPalette'], ['goog.color', 'goog.dom', 'goog.dom.DomHelper', 'goog.events', 'goog.events.Event', 'goog.events.EventType', 'goog.events.InputHandler', 'goog.style', 'goog.ui.Component', 'goog.ui.Component.EventType', 'goog.userAgent']);
goog.addDependency('ui/idgenerator.js', ['goog.ui.IdGenerator'], []);
goog.addDependency('ui/idletimer.js', ['goog.ui.IdleTimer'], ['goog.Timer', 'goog.events', 'goog.events.EventTarget', 'goog.structs.Set', 'goog.ui.ActivityMonitor']);
goog.addDependency('ui/iframemask.js', ['goog.ui.IframeMask'], ['goog.Disposable', 'goog.Timer', 'goog.dom', 'goog.dom.DomHelper', 'goog.dom.iframe', 'goog.events.EventHandler', 'goog.events.EventTarget', 'goog.style']);
goog.addDependency('ui/imagelessbuttonrenderer.js', ['goog.ui.ImagelessButtonRenderer'], ['goog.ui.Button', 'goog.ui.ControlContent', 'goog.ui.CustomButtonRenderer', 'goog.ui.INLINE_BLOCK_CLASSNAME', 'goog.ui.registry']);
goog.addDependency('ui/imagelessmenubuttonrenderer.js', ['goog.ui.ImagelessMenuButtonRenderer'], ['goog.dom', 'goog.dom.TagName', 'goog.ui.ControlContent', 'goog.ui.INLINE_BLOCK_CLASSNAME', 'goog.ui.MenuButton', 'goog.ui.MenuButtonRenderer', 'goog.ui.registry']);
goog.addDependency('ui/imagelessroundedcorner.js', ['goog.ui.AbstractImagelessRoundedCorner', 'goog.ui.CanvasRoundedCorner', 'goog.ui.ImagelessRoundedCorner', 'goog.ui.VmlRoundedCorner'], ['goog.dom.DomHelper', 'goog.graphics.Path', 'goog.graphics.SolidFill', 'goog.graphics.Stroke', 'goog.graphics.VmlGraphics', 'goog.userAgent']);
goog.addDependency('ui/inputdatepicker.js', ['goog.ui.InputDatePicker'], ['goog.date.DateTime', 'goog.dom', 'goog.i18n.DateTimeParse', 'goog.string', 'goog.ui.Component', 'goog.ui.PopupDatePicker']);
goog.addDependency('ui/itemevent.js', ['goog.ui.ItemEvent'], ['goog.events.Event']);
goog.addDependency('ui/keyboardshortcuthandler.js', ['goog.ui.KeyboardShortcutEvent', 'goog.ui.KeyboardShortcutHandler', 'goog.ui.KeyboardShortcutHandler.EventType'], ['goog.Timer', 'goog.events', 'goog.events.Event', 'goog.events.EventTarget', 'goog.events.EventType', 'goog.events.KeyCodes', 'goog.events.KeyNames', 'goog.object']);
goog.addDependency('ui/labelinput.js', ['goog.ui.LabelInput'], ['goog.Timer', 'goog.dom', 'goog.dom.a11y', 'goog.dom.a11y.State', 'goog.dom.classes', 'goog.events.EventHandler', 'goog.events.EventType', 'goog.ui.Component', 'goog.userAgent']);
goog.addDependency('ui/linkbuttonrenderer.js', ['goog.ui.LinkButtonRenderer'], ['goog.ui.Button', 'goog.ui.FlatButtonRenderer', 'goog.ui.registry']);
goog.addDependency('ui/media/flashobject.js', ['goog.ui.media.FlashObject', 'goog.ui.media.FlashObject.ScriptAccessLevel', 'goog.ui.media.FlashObject.Wmodes'], ['goog.asserts', 'goog.debug.Logger', 'goog.events.EventHandler', 'goog.string', 'goog.structs.Map', 'goog.style', 'goog.ui.Component', 'goog.ui.Component.Error', 'goog.userAgent', 'goog.userAgent.flash']);
goog.addDependency('ui/media/flickr.js', ['goog.ui.media.FlickrSet', 'goog.ui.media.FlickrSetModel'], ['goog.object', 'goog.ui.media.FlashObject', 'goog.ui.media.Media', 'goog.ui.media.MediaModel', 'goog.ui.media.MediaModel.Player', 'goog.ui.media.MediaRenderer']);
goog.addDependency('ui/media/googlevideo.js', ['goog.ui.media.GoogleVideo', 'goog.ui.media.GoogleVideoModel'], ['goog.string', 'goog.ui.media.FlashObject', 'goog.ui.media.Media', 'goog.ui.media.MediaModel', 'goog.ui.media.MediaModel.Player', 'goog.ui.media.MediaRenderer']);
goog.addDependency('ui/media/media.js', ['goog.ui.media.Media', 'goog.ui.media.MediaRenderer'], ['goog.style', 'goog.ui.Component.State', 'goog.ui.Control', 'goog.ui.ControlRenderer']);
goog.addDependency('ui/media/mediamodel.js', ['goog.ui.media.MediaModel', 'goog.ui.media.MediaModel.Category', 'goog.ui.media.MediaModel.Credit', 'goog.ui.media.MediaModel.Credit.Role', 'goog.ui.media.MediaModel.Credit.Scheme', 'goog.ui.media.MediaModel.Medium', 'goog.ui.media.MediaModel.MimeType', 'goog.ui.media.MediaModel.Player', 'goog.ui.media.MediaModel.SubTitle', 'goog.ui.media.MediaModel.Thumbnail'], ['goog.array']);
goog.addDependency('ui/media/mp3.js', ['goog.ui.media.Mp3'], ['goog.string', 'goog.ui.media.FlashObject', 'goog.ui.media.Media', 'goog.ui.media.MediaRenderer']);
goog.addDependency('ui/media/photo.js', ['goog.ui.media.Photo'], ['goog.ui.media.Media', 'goog.ui.media.MediaRenderer']);
goog.addDependency('ui/media/picasa.js', ['goog.ui.media.PicasaAlbum', 'goog.ui.media.PicasaAlbumModel'], ['goog.object', 'goog.ui.media.FlashObject', 'goog.ui.media.Media', 'goog.ui.media.MediaModel', 'goog.ui.media.MediaModel.Player', 'goog.ui.media.MediaRenderer']);
goog.addDependency('ui/media/vimeo.js', ['goog.ui.media.Vimeo', 'goog.ui.media.VimeoModel'], ['goog.string', 'goog.ui.media.FlashObject', 'goog.ui.media.Media', 'goog.ui.media.MediaModel', 'goog.ui.media.MediaModel.Player', 'goog.ui.media.MediaRenderer']);
goog.addDependency('ui/media/youtube.js', ['goog.ui.media.Youtube', 'goog.ui.media.YoutubeModel'], ['goog.string', 'goog.ui.Component.Error', 'goog.ui.Component.State', 'goog.ui.media.FlashObject', 'goog.ui.media.Media', 'goog.ui.media.MediaModel', 'goog.ui.media.MediaModel.Player', 'goog.ui.media.MediaModel.Thumbnail', 'goog.ui.media.MediaRenderer']);
goog.addDependency('ui/menu.js', ['goog.ui.Menu', 'goog.ui.Menu.EventType'], ['goog.math.Coordinate', 'goog.string', 'goog.style', 'goog.ui.Component.EventType', 'goog.ui.Component.State', 'goog.ui.Container', 'goog.ui.Container.Orientation', 'goog.ui.MenuHeader', 'goog.ui.MenuItem', 'goog.ui.MenuRenderer', 'goog.ui.MenuSeparator']);
goog.addDependency('ui/menubase.js', ['goog.ui.MenuBase'], ['goog.events.EventHandler', 'goog.events.EventType', 'goog.events.KeyHandler', 'goog.events.KeyHandler.EventType', 'goog.ui.Popup']);
goog.addDependency('ui/menubutton.js', ['goog.ui.MenuButton'], ['goog.Timer', 'goog.dom', 'goog.dom.a11y', 'goog.dom.a11y.State', 'goog.events.EventType', 'goog.events.KeyCodes', 'goog.events.KeyHandler.EventType', 'goog.math.Box', 'goog.math.Rect', 'goog.positioning.Corner', 'goog.positioning.MenuAnchoredPosition', 'goog.style', 'goog.ui.Button', 'goog.ui.Component.EventType', 'goog.ui.Component.State', 'goog.ui.ControlContent', 'goog.ui.Menu', 'goog.ui.MenuButtonRenderer', 'goog.ui.registry', 'goog.userAgent', 'goog.userAgent.product']);
goog.addDependency('ui/menubuttonrenderer.js', ['goog.ui.MenuButtonRenderer'], ['goog.dom', 'goog.style', 'goog.ui.CustomButtonRenderer', 'goog.ui.INLINE_BLOCK_CLASSNAME', 'goog.ui.Menu', 'goog.ui.MenuRenderer', 'goog.userAgent']);
goog.addDependency('ui/menuheader.js', ['goog.ui.MenuHeader'], ['goog.ui.Component.State', 'goog.ui.Control', 'goog.ui.MenuHeaderRenderer', 'goog.ui.registry']);
goog.addDependency('ui/menuheaderrenderer.js', ['goog.ui.MenuHeaderRenderer'], ['goog.dom', 'goog.dom.classes', 'goog.ui.ControlRenderer']);
goog.addDependency('ui/menuitem.js', ['goog.ui.MenuItem'], ['goog.array', 'goog.dom', 'goog.dom.classes', 'goog.events.KeyCodes', 'goog.math.Coordinate', 'goog.string', 'goog.ui.Component.State', 'goog.ui.Control', 'goog.ui.ControlContent', 'goog.ui.MenuItemRenderer', 'goog.ui.registry']);
goog.addDependency('ui/menuitemrenderer.js', ['goog.ui.MenuItemRenderer'], ['goog.dom', 'goog.dom.a11y', 'goog.dom.a11y.Role', 'goog.dom.classes', 'goog.ui.Component.State', 'goog.ui.ControlContent', 'goog.ui.ControlRenderer']);
goog.addDependency('ui/menurenderer.js', ['goog.ui.MenuRenderer'], ['goog.dom', 'goog.dom.a11y', 'goog.dom.a11y.Role', 'goog.dom.a11y.State', 'goog.ui.ContainerRenderer', 'goog.ui.Separator']);
goog.addDependency('ui/menuseparator.js', ['goog.ui.MenuSeparator'], ['goog.ui.MenuSeparatorRenderer', 'goog.ui.Separator', 'goog.ui.registry']);
goog.addDependency('ui/menuseparatorrenderer.js', ['goog.ui.MenuSeparatorRenderer'], ['goog.dom', 'goog.dom.classes', 'goog.ui.ControlContent', 'goog.ui.ControlRenderer']);
goog.addDependency('ui/mockactivitymonitor.js', ['goog.ui.MockActivityMonitor'], ['goog.events.EventType', 'goog.ui.ActivityMonitor']);
goog.addDependency('ui/modalpopup.js', ['goog.ui.ModalPopup'], ['goog.Timer', 'goog.asserts', 'goog.dom', 'goog.dom.TagName', 'goog.dom.classes', 'goog.dom.iframe', 'goog.events', 'goog.events.EventType', 'goog.events.FocusHandler', 'goog.style', 'goog.ui.Component', 'goog.ui.PopupBase.EventType', 'goog.userAgent']);
goog.addDependency('ui/nativebuttonrenderer.js', ['goog.ui.NativeButtonRenderer'], ['goog.dom.classes', 'goog.events.EventType', 'goog.ui.ButtonRenderer', 'goog.ui.Component.State']);
goog.addDependency('ui/offlineinstalldialog.js', ['goog.ui.OfflineInstallDialog', 'goog.ui.OfflineInstallDialog.ButtonKeyType', 'goog.ui.OfflineInstallDialog.EnableScreen', 'goog.ui.OfflineInstallDialog.InstallScreen', 'goog.ui.OfflineInstallDialog.InstallingGearsScreen', 'goog.ui.OfflineInstallDialog.ScreenType', 'goog.ui.OfflineInstallDialog.UpgradeScreen', 'goog.ui.OfflineInstallDialogScreen'], ['goog.Disposable', 'goog.dom.classes', 'goog.gears', 'goog.string', 'goog.string.StringBuffer', 'goog.ui.Dialog', 'goog.ui.Dialog.ButtonSet', 'goog.ui.Dialog.EventType', 'goog.window']);
goog.addDependency('ui/offlinestatuscard.js', ['goog.ui.OfflineStatusCard', 'goog.ui.OfflineStatusCard.EventType'], ['goog.dom', 'goog.events.EventType', 'goog.gears.StatusType', 'goog.structs.Map', 'goog.style', 'goog.ui.Component', 'goog.ui.Component.EventType', 'goog.ui.ProgressBar']);
goog.addDependency('ui/offlinestatuscomponent.js', ['goog.ui.OfflineStatusComponent', 'goog.ui.OfflineStatusComponent.StatusClassNames'], ['goog.dom.classes', 'goog.events.EventType', 'goog.gears.StatusType', 'goog.positioning', 'goog.positioning.AnchoredPosition', 'goog.positioning.Corner', 'goog.positioning.Overflow', 'goog.ui.Component', 'goog.ui.OfflineStatusCard.EventType', 'goog.ui.Popup']);
goog.addDependency('ui/option.js', ['goog.ui.Option'], ['goog.ui.Component.EventType', 'goog.ui.ControlContent', 'goog.ui.MenuItem', 'goog.ui.registry']);
goog.addDependency('ui/palette.js', ['goog.ui.Palette'], ['goog.array', 'goog.dom', 'goog.events.EventType', 'goog.events.KeyCodes', 'goog.math.Size', 'goog.ui.Component.Error', 'goog.ui.Component.EventType', 'goog.ui.Control', 'goog.ui.PaletteRenderer', 'goog.ui.SelectionModel']);
goog.addDependency('ui/paletterenderer.js', ['goog.ui.PaletteRenderer'], ['goog.array', 'goog.dom', 'goog.dom.NodeType', 'goog.dom.a11y', 'goog.dom.classes', 'goog.style', 'goog.ui.ControlRenderer', 'goog.userAgent']);
goog.addDependency('ui/plaintextspellchecker.js', ['goog.ui.PlainTextSpellChecker'], ['goog.Timer', 'goog.dom', 'goog.dom.a11y', 'goog.events.EventHandler', 'goog.events.EventType', 'goog.events.KeyCodes', 'goog.events.KeyHandler', 'goog.events.KeyHandler.EventType', 'goog.style', 'goog.ui.AbstractSpellChecker', 'goog.ui.AbstractSpellChecker.AsyncResult', 'goog.ui.Component.EventType', 'goog.userAgent']);
goog.addDependency('ui/popup.js', ['goog.ui.Popup', 'goog.ui.Popup.AbsolutePosition', 'goog.ui.Popup.AnchoredPosition', 'goog.ui.Popup.AnchoredViewPortPosition', 'goog.ui.Popup.ClientPosition', 'goog.ui.Popup.Corner', 'goog.ui.Popup.Overflow', 'goog.ui.Popup.ViewPortClientPosition', 'goog.ui.Popup.ViewPortPosition'], ['goog.math.Box', 'goog.positioning', 'goog.positioning.AbsolutePosition', 'goog.positioning.AnchoredPosition', 'goog.positioning.AnchoredViewportPosition', 'goog.positioning.ClientPosition', 'goog.positioning.Corner', 'goog.positioning.Overflow', 'goog.positioning.OverflowStatus', 'goog.positioning.ViewportClientPosition', 'goog.positioning.ViewportPosition', 'goog.style', 'goog.ui.PopupBase']);
goog.addDependency('ui/popupbase.js', ['goog.ui.PopupBase', 'goog.ui.PopupBase.EventType', 'goog.ui.PopupBase.Type'], ['goog.Timer', 'goog.dom', 'goog.events.EventHandler', 'goog.events.EventTarget', 'goog.events.EventType', 'goog.events.KeyCodes', 'goog.fx.Transition', 'goog.fx.Transition.EventType', 'goog.style', 'goog.userAgent']);
goog.addDependency('ui/popupcolorpicker.js', ['goog.ui.PopupColorPicker'], ['goog.dom.classes', 'goog.events.EventType', 'goog.positioning.AnchoredPosition', 'goog.positioning.Corner', 'goog.ui.ColorPicker', 'goog.ui.ColorPicker.EventType', 'goog.ui.Component', 'goog.ui.Popup']);
goog.addDependency('ui/popupdatepicker.js', ['goog.ui.PopupDatePicker'], ['goog.events.EventType', 'goog.positioning.AnchoredPosition', 'goog.positioning.Corner', 'goog.style', 'goog.ui.Component', 'goog.ui.DatePicker', 'goog.ui.DatePicker.Events', 'goog.ui.Popup', 'goog.ui.PopupBase.EventType']);
goog.addDependency('ui/popupmenu.js', ['goog.ui.PopupMenu'], ['goog.events.EventType', 'goog.positioning.AnchoredViewportPosition', 'goog.positioning.Corner', 'goog.positioning.MenuAnchoredPosition', 'goog.positioning.ViewportClientPosition', 'goog.structs', 'goog.structs.Map', 'goog.style', 'goog.ui.Component.EventType', 'goog.ui.Menu', 'goog.ui.PopupBase', 'goog.userAgent']);
goog.addDependency('ui/progressbar.js', ['goog.ui.ProgressBar', 'goog.ui.ProgressBar.Orientation'], ['goog.dom', 'goog.dom.a11y', 'goog.dom.classes', 'goog.events', 'goog.events.EventType', 'goog.ui.Component', 'goog.ui.Component.EventType', 'goog.ui.RangeModel', 'goog.userAgent']);
goog.addDependency('ui/prompt.js', ['goog.ui.Prompt'], ['goog.Timer', 'goog.dom', 'goog.events', 'goog.events.EventType', 'goog.functions', 'goog.ui.Component.Error', 'goog.ui.Dialog', 'goog.ui.Dialog.ButtonSet', 'goog.ui.Dialog.DefaultButtonKeys', 'goog.ui.Dialog.EventType', 'goog.userAgent']);
goog.addDependency('ui/rangemodel.js', ['goog.ui.RangeModel'], ['goog.events.EventTarget', 'goog.ui.Component.EventType']);
goog.addDependency('ui/ratings.js', ['goog.ui.Ratings', 'goog.ui.Ratings.EventType'], ['goog.dom.a11y', 'goog.dom.classes', 'goog.events.EventType', 'goog.ui.Component']);
goog.addDependency('ui/registry.js', ['goog.ui.registry'], ['goog.dom.classes']);
goog.addDependency('ui/richtextspellchecker.js', ['goog.ui.RichTextSpellChecker'], ['goog.Timer', 'goog.dom', 'goog.dom.NodeType', 'goog.events', 'goog.events.EventType', 'goog.string.StringBuffer', 'goog.ui.AbstractSpellChecker', 'goog.ui.AbstractSpellChecker.AsyncResult']);
goog.addDependency('ui/roundedpanel.js', ['goog.ui.BaseRoundedPanel', 'goog.ui.CssRoundedPanel', 'goog.ui.GraphicsRoundedPanel', 'goog.ui.RoundedPanel', 'goog.ui.RoundedPanel.Corner'], ['goog.dom', 'goog.dom.classes', 'goog.graphics', 'goog.graphics.SolidFill', 'goog.graphics.Stroke', 'goog.math.Coordinate', 'goog.style', 'goog.ui.Component', 'goog.userAgent']);
goog.addDependency('ui/roundedtabrenderer.js', ['goog.ui.RoundedTabRenderer'], ['goog.dom', 'goog.ui.Tab', 'goog.ui.TabBar.Location', 'goog.ui.TabRenderer', 'goog.ui.registry']);
goog.addDependency('ui/scrollfloater.js', ['goog.ui.ScrollFloater', 'goog.ui.ScrollFloater.EventType'], ['goog.dom', 'goog.dom.classes', 'goog.events.EventType', 'goog.object', 'goog.style', 'goog.ui.Component', 'goog.userAgent']);
goog.addDependency('ui/select.js', ['goog.ui.Select'], ['goog.dom.a11y', 'goog.dom.a11y.Role', 'goog.dom.a11y.State', 'goog.events.EventType', 'goog.ui.Component.EventType', 'goog.ui.ControlContent', 'goog.ui.MenuButton', 'goog.ui.SelectionModel', 'goog.ui.registry']);
goog.addDependency('ui/selectionmenubutton.js', ['goog.ui.SelectionMenuButton', 'goog.ui.SelectionMenuButton.SelectionState'], ['goog.events.EventType', 'goog.ui.Component.EventType', 'goog.ui.Menu', 'goog.ui.MenuButton', 'goog.ui.MenuItem']);
goog.addDependency('ui/selectionmodel.js', ['goog.ui.SelectionModel'], ['goog.array', 'goog.events.EventTarget', 'goog.events.EventType']);
goog.addDependency('ui/separator.js', ['goog.ui.Separator'], ['goog.dom.a11y', 'goog.ui.Component.State', 'goog.ui.Control', 'goog.ui.MenuSeparatorRenderer', 'goog.ui.registry']);
goog.addDependency('ui/serverchart.js', ['goog.ui.ServerChart', 'goog.ui.ServerChart.AxisDisplayType', 'goog.ui.ServerChart.ChartType', 'goog.ui.ServerChart.EncodingType', 'goog.ui.ServerChart.Event', 'goog.ui.ServerChart.LegendPosition', 'goog.ui.ServerChart.MaximumValue', 'goog.ui.ServerChart.MultiAxisAlignment', 'goog.ui.ServerChart.MultiAxisType', 'goog.ui.ServerChart.UriParam', 'goog.ui.ServerChart.UriTooLongEvent'], ['goog.Uri', 'goog.array', 'goog.asserts', 'goog.events.Event', 'goog.string', 'goog.ui.Component']);
goog.addDependency('ui/slider.js', ['goog.ui.Slider', 'goog.ui.Slider.Orientation'], ['goog.dom', 'goog.dom.a11y', 'goog.dom.a11y.Role', 'goog.ui.SliderBase', 'goog.ui.SliderBase.Orientation']);
goog.addDependency('ui/sliderbase.js', ['goog.ui.SliderBase', 'goog.ui.SliderBase.Orientation'], ['goog.Timer', 'goog.dom', 'goog.dom.a11y', 'goog.dom.a11y.Role', 'goog.dom.a11y.State', 'goog.dom.classes', 'goog.events', 'goog.events.EventType', 'goog.events.KeyCodes', 'goog.events.KeyHandler', 'goog.events.KeyHandler.EventType', 'goog.events.MouseWheelHandler', 'goog.events.MouseWheelHandler.EventType', 'goog.fx.AnimationParallelQueue', 'goog.fx.Dragger', 'goog.fx.Dragger.EventType', 'goog.fx.Transition.EventType', 'goog.fx.dom.ResizeHeight', 'goog.fx.dom.ResizeWidth', 'goog.fx.dom.SlideFrom', 'goog.math', 'goog.math.Coordinate', 'goog.style', 'goog.ui.Component', 'goog.ui.Component.EventType', 'goog.ui.RangeModel']);
goog.addDependency('ui/splitbehavior.js', ['goog.ui.SplitBehavior', 'goog.ui.SplitBehavior.DefaultHandlers'], ['goog.Disposable', 'goog.array', 'goog.dispose', 'goog.dom', 'goog.dom.DomHelper', 'goog.dom.classes', 'goog.events', 'goog.events.EventHandler', 'goog.events.EventType', 'goog.string', 'goog.ui.ButtonSide', 'goog.ui.Component', 'goog.ui.Component.Error', 'goog.ui.INLINE_BLOCK_CLASSNAME', 'goog.ui.decorate', 'goog.ui.registry']);
goog.addDependency('ui/splitpane.js', ['goog.ui.SplitPane', 'goog.ui.SplitPane.Orientation'], ['goog.dom', 'goog.dom.classes', 'goog.events.EventType', 'goog.fx.Dragger', 'goog.fx.Dragger.EventType', 'goog.math.Rect', 'goog.math.Size', 'goog.style', 'goog.ui.Component', 'goog.ui.Component.EventType', 'goog.userAgent']);
goog.addDependency('ui/style/app/buttonrenderer.js', ['goog.ui.style.app.ButtonRenderer'], ['goog.ui.Button', 'goog.ui.ControlContent', 'goog.ui.CustomButtonRenderer', 'goog.ui.INLINE_BLOCK_CLASSNAME', 'goog.ui.registry']);
goog.addDependency('ui/style/app/menubuttonrenderer.js', ['goog.ui.style.app.MenuButtonRenderer'], ['goog.array', 'goog.dom', 'goog.dom.a11y.Role', 'goog.style', 'goog.ui.ControlContent', 'goog.ui.Menu', 'goog.ui.MenuRenderer', 'goog.ui.style.app.ButtonRenderer']);
goog.addDependency('ui/style/app/primaryactionbuttonrenderer.js', ['goog.ui.style.app.PrimaryActionButtonRenderer'], ['goog.ui.Button', 'goog.ui.registry', 'goog.ui.style.app.ButtonRenderer']);
goog.addDependency('ui/submenu.js', ['goog.ui.SubMenu'], ['goog.Timer', 'goog.dom', 'goog.dom.classes', 'goog.events.KeyCodes', 'goog.positioning.AnchoredViewportPosition', 'goog.positioning.Corner', 'goog.style', 'goog.ui.Component', 'goog.ui.Component.EventType', 'goog.ui.Component.State', 'goog.ui.ControlContent', 'goog.ui.Menu', 'goog.ui.MenuItem', 'goog.ui.SubMenuRenderer', 'goog.ui.registry']);
goog.addDependency('ui/submenurenderer.js', ['goog.ui.SubMenuRenderer'], ['goog.dom', 'goog.dom.a11y', 'goog.dom.a11y.State', 'goog.dom.classes', 'goog.style', 'goog.ui.Menu', 'goog.ui.MenuItemRenderer']);
goog.addDependency('ui/tab.js', ['goog.ui.Tab'], ['goog.ui.Component.State', 'goog.ui.Control', 'goog.ui.ControlContent', 'goog.ui.TabRenderer', 'goog.ui.registry']);
goog.addDependency('ui/tabbar.js', ['goog.ui.TabBar', 'goog.ui.TabBar.Location'], ['goog.ui.Component.EventType', 'goog.ui.Container', 'goog.ui.Container.Orientation', 'goog.ui.Tab', 'goog.ui.TabBarRenderer', 'goog.ui.registry']);
goog.addDependency('ui/tabbarrenderer.js', ['goog.ui.TabBarRenderer'], ['goog.dom.a11y.Role', 'goog.object', 'goog.ui.ContainerRenderer']);
goog.addDependency('ui/tablesorter.js', ['goog.ui.TableSorter', 'goog.ui.TableSorter.EventType'], ['goog.array', 'goog.dom', 'goog.dom.TagName', 'goog.dom.classes', 'goog.events', 'goog.events.EventType', 'goog.functions', 'goog.ui.Component']);
goog.addDependency('ui/tabpane.js', ['goog.ui.TabPane', 'goog.ui.TabPane.Events', 'goog.ui.TabPane.TabLocation', 'goog.ui.TabPane.TabPage', 'goog.ui.TabPaneEvent'], ['goog.dom', 'goog.dom.classes', 'goog.events', 'goog.events.Event', 'goog.events.EventTarget', 'goog.events.EventType', 'goog.events.KeyCodes', 'goog.style']);
goog.addDependency('ui/tabrenderer.js', ['goog.ui.TabRenderer'], ['goog.dom.a11y.Role', 'goog.ui.Component.State', 'goog.ui.ControlRenderer']);
goog.addDependency('ui/textarea.js', ['goog.ui.Textarea'], ['goog.Timer', 'goog.events.EventType', 'goog.events.KeyCodes', 'goog.style', 'goog.ui.Control', 'goog.ui.TextareaRenderer', 'goog.userAgent', 'goog.userAgent.product']);
goog.addDependency('ui/textarearenderer.js', ['goog.ui.TextareaRenderer'], ['goog.ui.Component.State', 'goog.ui.ControlRenderer']);
goog.addDependency('ui/togglebutton.js', ['goog.ui.ToggleButton'], ['goog.ui.Button', 'goog.ui.Component.State', 'goog.ui.ControlContent', 'goog.ui.CustomButtonRenderer', 'goog.ui.registry']);
goog.addDependency('ui/toolbar.js', ['goog.ui.Toolbar'], ['goog.ui.Container', 'goog.ui.ToolbarRenderer']);
goog.addDependency('ui/toolbarbutton.js', ['goog.ui.ToolbarButton'], ['goog.ui.Button', 'goog.ui.ControlContent', 'goog.ui.ToolbarButtonRenderer', 'goog.ui.registry']);
goog.addDependency('ui/toolbarbuttonrenderer.js', ['goog.ui.ToolbarButtonRenderer'], ['goog.ui.CustomButtonRenderer']);
goog.addDependency('ui/toolbarcolormenubutton.js', ['goog.ui.ToolbarColorMenuButton'], ['goog.ui.ColorMenuButton', 'goog.ui.ControlContent', 'goog.ui.ToolbarColorMenuButtonRenderer', 'goog.ui.registry']);
goog.addDependency('ui/toolbarcolormenubuttonrenderer.js', ['goog.ui.ToolbarColorMenuButtonRenderer'], ['goog.dom.classes', 'goog.ui.ColorMenuButtonRenderer', 'goog.ui.ControlContent', 'goog.ui.MenuButtonRenderer', 'goog.ui.ToolbarMenuButtonRenderer']);
goog.addDependency('ui/toolbarmenubutton.js', ['goog.ui.ToolbarMenuButton'], ['goog.ui.ControlContent', 'goog.ui.MenuButton', 'goog.ui.ToolbarMenuButtonRenderer', 'goog.ui.registry']);
goog.addDependency('ui/toolbarmenubuttonrenderer.js', ['goog.ui.ToolbarMenuButtonRenderer'], ['goog.ui.MenuButtonRenderer']);
goog.addDependency('ui/toolbarrenderer.js', ['goog.ui.ToolbarRenderer'], ['goog.dom.a11y.Role', 'goog.ui.Container.Orientation', 'goog.ui.ContainerRenderer', 'goog.ui.Separator', 'goog.ui.ToolbarSeparatorRenderer']);
goog.addDependency('ui/toolbarselect.js', ['goog.ui.ToolbarSelect'], ['goog.ui.ControlContent', 'goog.ui.Select', 'goog.ui.ToolbarMenuButtonRenderer', 'goog.ui.registry']);
goog.addDependency('ui/toolbarseparator.js', ['goog.ui.ToolbarSeparator'], ['goog.ui.Separator', 'goog.ui.ToolbarSeparatorRenderer', 'goog.ui.registry']);
goog.addDependency('ui/toolbarseparatorrenderer.js', ['goog.ui.ToolbarSeparatorRenderer'], ['goog.dom.classes', 'goog.ui.INLINE_BLOCK_CLASSNAME', 'goog.ui.MenuSeparatorRenderer']);
goog.addDependency('ui/toolbartogglebutton.js', ['goog.ui.ToolbarToggleButton'], ['goog.ui.ControlContent', 'goog.ui.ToggleButton', 'goog.ui.ToolbarButtonRenderer', 'goog.ui.registry']);
goog.addDependency('ui/tooltip.js', ['goog.ui.Tooltip', 'goog.ui.Tooltip.CursorTooltipPosition', 'goog.ui.Tooltip.ElementTooltipPosition', 'goog.ui.Tooltip.State'], ['goog.Timer', 'goog.array', 'goog.dom', 'goog.events', 'goog.events.EventType', 'goog.math.Box', 'goog.math.Coordinate', 'goog.positioning', 'goog.positioning.AnchoredPosition', 'goog.positioning.Corner', 'goog.positioning.Overflow', 'goog.positioning.OverflowStatus', 'goog.positioning.ViewportPosition', 'goog.structs.Set', 'goog.style', 'goog.ui.Popup', 'goog.ui.PopupBase']);
goog.addDependency('ui/tree/basenode.js', ['goog.ui.tree.BaseNode', 'goog.ui.tree.BaseNode.EventType'], ['goog.Timer', 'goog.asserts', 'goog.dom.a11y', 'goog.events.KeyCodes', 'goog.string', 'goog.string.StringBuffer', 'goog.style', 'goog.ui.Component', 'goog.userAgent']);
goog.addDependency('ui/tree/treecontrol.js', ['goog.ui.tree.TreeControl'], ['goog.debug.Logger', 'goog.dom.a11y', 'goog.dom.classes', 'goog.events.EventType', 'goog.events.FocusHandler', 'goog.events.KeyHandler', 'goog.events.KeyHandler.EventType', 'goog.ui.tree.BaseNode', 'goog.ui.tree.TreeNode', 'goog.ui.tree.TypeAhead', 'goog.userAgent']);
goog.addDependency('ui/tree/treenode.js', ['goog.ui.tree.TreeNode'], ['goog.ui.tree.BaseNode']);
goog.addDependency('ui/tree/typeahead.js', ['goog.ui.tree.TypeAhead', 'goog.ui.tree.TypeAhead.Offset'], ['goog.array', 'goog.events.KeyCodes', 'goog.string', 'goog.structs.Trie']);
goog.addDependency('ui/tristatemenuitem.js', ['goog.ui.TriStateMenuItem', 'goog.ui.TriStateMenuItem.State'], ['goog.dom.classes', 'goog.ui.Component.EventType', 'goog.ui.Component.State', 'goog.ui.ControlContent', 'goog.ui.MenuItem', 'goog.ui.TriStateMenuItemRenderer', 'goog.ui.registry']);
goog.addDependency('ui/tristatemenuitemrenderer.js', ['goog.ui.TriStateMenuItemRenderer'], ['goog.dom.classes', 'goog.ui.MenuItemRenderer']);
goog.addDependency('ui/twothumbslider.js', ['goog.ui.TwoThumbSlider'], ['goog.dom', 'goog.dom.a11y', 'goog.dom.a11y.Role', 'goog.ui.SliderBase']);
goog.addDependency('ui/zippy.js', ['goog.ui.Zippy', 'goog.ui.Zippy.Events', 'goog.ui.ZippyEvent'], ['goog.dom', 'goog.dom.a11y', 'goog.dom.classes', 'goog.events', 'goog.events.Event', 'goog.events.EventHandler', 'goog.events.EventTarget', 'goog.events.EventType', 'goog.events.KeyCodes', 'goog.style']);
goog.addDependency('uri/uri.js', ['goog.Uri', 'goog.Uri.QueryData'], ['goog.array', 'goog.string', 'goog.structs', 'goog.structs.Map', 'goog.uri.utils', 'goog.uri.utils.ComponentIndex']);
goog.addDependency('uri/utils.js', ['goog.uri.utils', 'goog.uri.utils.ComponentIndex', 'goog.uri.utils.QueryArray', 'goog.uri.utils.QueryValue', 'goog.uri.utils.StandardQueryParam'], ['goog.asserts', 'goog.string']);
goog.addDependency('useragent/adobereader.js', ['goog.userAgent.adobeReader'], ['goog.string', 'goog.userAgent']);
goog.addDependency('useragent/flash.js', ['goog.userAgent.flash'], ['goog.string']);
goog.addDependency('useragent/iphoto.js', ['goog.userAgent.iphoto'], ['goog.string', 'goog.userAgent']);
goog.addDependency('useragent/jscript.js', ['goog.userAgent.jscript'], ['goog.string']);
goog.addDependency('useragent/picasa.js', ['goog.userAgent.picasa'], ['goog.string', 'goog.userAgent']);
goog.addDependency('useragent/platform.js', ['goog.userAgent.platform'], ['goog.userAgent']);
goog.addDependency('useragent/product.js', ['goog.userAgent.product'], ['goog.userAgent']);
goog.addDependency('useragent/product_isversion.js', ['goog.userAgent.product.isVersion'], ['goog.userAgent.product']);
goog.addDependency('useragent/useragent.js', ['goog.userAgent'], ['goog.string']);
goog.addDependency('vec/float32array.js', ['goog.vec.Float32Array'], []);
goog.addDependency('vec/mat3.js', ['goog.vec.Mat3'], ['goog.vec', 'goog.vec.Vec3']);
goog.addDependency('vec/mat4.js', ['goog.vec.Mat4'], ['goog.vec', 'goog.vec.Vec3', 'goog.vec.Vec4']);
goog.addDependency('vec/matrix3.js', ['goog.vec.Matrix3'], ['goog.vec']);
goog.addDependency('vec/matrix4.js', ['goog.vec.Matrix4'], ['goog.vec', 'goog.vec.Vec3', 'goog.vec.Vec4']);
goog.addDependency('vec/quaternion.js', ['goog.vec.Quaternion'], ['goog.vec', 'goog.vec.Vec3', 'goog.vec.Vec4']);
goog.addDependency('vec/ray.js', ['goog.vec.Ray'], ['goog.vec.Vec3']);
goog.addDependency('vec/vec.js', ['goog.vec'], ['goog.vec.Float32Array']);
goog.addDependency('vec/vec3.js', ['goog.vec.Vec3'], ['goog.vec']);
goog.addDependency('vec/vec4.js', ['goog.vec.Vec4'], ['goog.vec']);
goog.addDependency('window/window.js', ['goog.window'], ['goog.string', 'goog.userAgent']);

//javascript/closure/debug/error.js
// Copyright 2009 The Closure Library Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @fileoverview Provides a base class for custom Error objects such that the
 * stack is correctly maintained.
 *
 * You should never need to throw goog.debug.Error(msg) directly, Error(msg) is
 * sufficient.
 *
 */

goog.provide('goog.debug.Error');



/**
 * Base class for custom error objects.
 * @param {*=} opt_msg The message associated with the error.
 * @constructor
 * @extends {Error}
 */
goog.debug.Error = function(opt_msg) {

  // Ensure there is a stack trace.
  this.stack = new Error().stack || '';

  if (opt_msg) {
    this.message = String(opt_msg);
  }
};
goog.inherits(goog.debug.Error, Error);


/** @override */
goog.debug.Error.prototype.name = 'CustomError';

//javascript/closure/string/string.js
// Copyright 2006 The Closure Library Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @fileoverview Utilities for string manipulation.
 */


/**
 * Namespace for string utilities
 */
goog.provide('goog.string');
goog.provide('goog.string.Unicode');


/**
 * Common Unicode string characters.
 * @enum {string}
 */
goog.string.Unicode = {
  NBSP: '\xa0'
};


/**
 * Fast prefix-checker.
 * @param {string} str The string to check.
 * @param {string} prefix A string to look for at the start of {@code str}.
 * @return {boolean} True if {@code str} begins with {@code prefix}.
 */
goog.string.startsWith = function(str, prefix) {
  return str.lastIndexOf(prefix, 0) == 0;
};


/**
 * Fast suffix-checker.
 * @param {string} str The string to check.
 * @param {string} suffix A string to look for at the end of {@code str}.
 * @return {boolean} True if {@code str} ends with {@code suffix}.
 */
goog.string.endsWith = function(str, suffix) {
  var l = str.length - suffix.length;
  return l >= 0 && str.indexOf(suffix, l) == l;
};


/**
 * Case-insensitive prefix-checker.
 * @param {string} str The string to check.
 * @param {string} prefix  A string to look for at the end of {@code str}.
 * @return {boolean} True if {@code str} begins with {@code prefix} (ignoring
 *     case).
 */
goog.string.caseInsensitiveStartsWith = function(str, prefix) {
  return goog.string.caseInsensitiveCompare(
      prefix, str.substr(0, prefix.length)) == 0;
};


/**
 * Case-insensitive suffix-checker.
 * @param {string} str The string to check.
 * @param {string} suffix A string to look for at the end of {@code str}.
 * @return {boolean} True if {@code str} ends with {@code suffix} (ignoring
 *     case).
 */
goog.string.caseInsensitiveEndsWith = function(str, suffix) {
  return goog.string.caseInsensitiveCompare(
      suffix, str.substr(str.length - suffix.length, suffix.length)) == 0;
};


/**
 * Does simple python-style string substitution.
 * subs("foo%s hot%s", "bar", "dog") becomes "foobar hotdog".
 * @param {string} str The string containing the pattern.
 * @param {...*} var_args The items to substitute into the pattern.
 * @return {string} A copy of {@code str} in which each occurrence of
 *     {@code %s} has been replaced an argument from {@code var_args}.
 */
goog.string.subs = function(str, var_args) {
  // This appears to be slow, but testing shows it compares more or less
  // equivalent to the regex.exec method.
  for (var i = 1; i < arguments.length; i++) {
    // We cast to String in case an argument is a Function.  Replacing $&, for
    // example, with $$$& stops the replace from subsituting the whole match
    // into the resultant string.  $$$& in the first replace becomes $$& in the
    //  second, which leaves $& in the resultant string.  Also:
    // $$, $`, $', $n $nn
    var replacement = String(arguments[i]).replace(/\$/g, '$$$$');
    str = str.replace(/\%s/, replacement);
  }
  return str;
};


/**
 * Converts multiple whitespace chars (spaces, non-breaking-spaces, new lines
 * and tabs) to a single space, and strips leading and trailing whitespace.
 * @param {string} str Input string.
 * @return {string} A copy of {@code str} with collapsed whitespace.
 */
goog.string.collapseWhitespace = function(str) {
  // Since IE doesn't include non-breaking-space (0xa0) in their \s character
  // class (as required by section 7.2 of the ECMAScript spec), we explicitly
  // include it in the regexp to enforce consistent cross-browser behavior.
  return str.replace(/[\s\xa0]+/g, ' ').replace(/^\s+|\s+$/g, '');
};


/**
 * Checks if a string is empty or contains only whitespaces.
 * @param {string} str The string to check.
 * @return {boolean} True if {@code str} is empty or whitespace only.
 */
goog.string.isEmpty = function(str) {
  // testing length == 0 first is actually slower in all browsers (about the
  // same in Opera).
  // Since IE doesn't include non-breaking-space (0xa0) in their \s character
  // class (as required by section 7.2 of the ECMAScript spec), we explicitly
  // include it in the regexp to enforce consistent cross-browser behavior.
  return /^[\s\xa0]*$/.test(str);
};


/**
 * Checks if a string is null, empty or contains only whitespaces.
 * @param {*} str The string to check.
 * @return {boolean} True if{@code str} is null, empty, or whitespace only.
 */
goog.string.isEmptySafe = function(str) {
  return goog.string.isEmpty(goog.string.makeSafe(str));
};


/**
 * Checks if a string is all breaking whitespace.
 * @param {string} str The string to check.
 * @return {boolean} Whether the string is all breaking whitespace.
 */
goog.string.isBreakingWhitespace = function(str) {
  return !/[^\t\n\r ]/.test(str);
};


/**
 * Checks if a string contains all letters.
 * @param {string} str string to check.
 * @return {boolean} True if {@code str} consists entirely of letters.
 */
goog.string.isAlpha = function(str) {
  return !/[^a-zA-Z]/.test(str);
};


/**
 * Checks if a string contains only numbers.
 * @param {*} str string to check. If not a string, it will be
 *     casted to one.
 * @return {boolean} True if {@code str} is numeric.
 */
goog.string.isNumeric = function(str) {
  return !/[^0-9]/.test(str);
};


/**
 * Checks if a string contains only numbers or letters.
 * @param {string} str string to check.
 * @return {boolean} True if {@code str} is alphanumeric.
 */
goog.string.isAlphaNumeric = function(str) {
  return !/[^a-zA-Z0-9]/.test(str);
};


/**
 * Checks if a character is a space character.
 * @param {string} ch Character to check.
 * @return {boolean} True if {code ch} is a space.
 */
goog.string.isSpace = function(ch) {
  return ch == ' ';
};


/**
 * Checks if a character is a valid unicode character.
 * @param {string} ch Character to check.
 * @return {boolean} True if {code ch} is a valid unicode character.
 */
goog.string.isUnicodeChar = function(ch) {
  return ch.length == 1 && ch >= ' ' && ch <= '~' ||
         ch >= '\u0080' && ch <= '\uFFFD';
};


/**
 * Takes a string and replaces newlines with a space. Multiple lines are
 * replaced with a single space.
 * @param {string} str The string from which to strip newlines.
 * @return {string} A copy of {@code str} stripped of newlines.
 */
goog.string.stripNewlines = function(str) {
  return str.replace(/(\r\n|\r|\n)+/g, ' ');
};


/**
 * Replaces Windows and Mac new lines with unix style: \r or \r\n with \n.
 * @param {string} str The string to in which to canonicalize newlines.
 * @return {string} {@code str} A copy of {@code} with canonicalized newlines.
 */
goog.string.canonicalizeNewlines = function(str) {
  return str.replace(/(\r\n|\r|\n)/g, '\n');
};


/**
 * Normalizes whitespace in a string, replacing all whitespace chars with
 * a space.
 * @param {string} str The string in which to normalize whitespace.
 * @return {string} A copy of {@code str} with all whitespace normalized.
 */
goog.string.normalizeWhitespace = function(str) {
  return str.replace(/\xa0|\s/g, ' ');
};


/**
 * Normalizes spaces in a string, replacing all consecutive spaces and tabs
 * with a single space. Replaces non-breaking space with a space.
 * @param {string} str The string in which to normalize spaces.
 * @return {string} A copy of {@code str} with all consecutive spaces and tabs
 *    replaced with a single space.
 */
goog.string.normalizeSpaces = function(str) {
  return str.replace(/\xa0|[ \t]+/g, ' ');
};


/**
 * Removes the breaking spaces from the left and right of the string and
 * collapses the sequences of breaking spaces in the middle into single spaces.
 * The original and the result strings render the same way in HTML.
 * @param {string} str A string in which to collapse spaces.
 * @return {string} Copy of the string with normalized breaking spaces.
 */
goog.string.collapseBreakingSpaces = function(str) {
  return str.replace(/[\t\r\n ]+/g, ' ').replace(
      /^[\t\r\n ]+|[\t\r\n ]+$/g, '');
};


/**
 * Trims white spaces to the left and right of a string.
 * @param {string} str The string to trim.
 * @return {string} A trimmed copy of {@code str}.
 */
goog.string.trim = function(str) {
  // Since IE doesn't include non-breaking-space (0xa0) in their \s character
  // class (as required by section 7.2 of the ECMAScript spec), we explicitly
  // include it in the regexp to enforce consistent cross-browser behavior.
  return str.replace(/^[\s\xa0]+|[\s\xa0]+$/g, '');
};


/**
 * Trims whitespaces at the left end of a string.
 * @param {string} str The string to left trim.
 * @return {string} A trimmed copy of {@code str}.
 */
goog.string.trimLeft = function(str) {
  // Since IE doesn't include non-breaking-space (0xa0) in their \s character
  // class (as required by section 7.2 of the ECMAScript spec), we explicitly
  // include it in the regexp to enforce consistent cross-browser behavior.
  return str.replace(/^[\s\xa0]+/, '');
};


/**
 * Trims whitespaces at the right end of a string.
 * @param {string} str The string to right trim.
 * @return {string} A trimmed copy of {@code str}.
 */
goog.string.trimRight = function(str) {
  // Since IE doesn't include non-breaking-space (0xa0) in their \s character
  // class (as required by section 7.2 of the ECMAScript spec), we explicitly
  // include it in the regexp to enforce consistent cross-browser behavior.
  return str.replace(/[\s\xa0]+$/, '');
};


/**
 * A string comparator that ignores case.
 * -1 = str1 less than str2
 *  0 = str1 equals str2
 *  1 = str1 greater than str2
 *
 * @param {string} str1 The string to compare.
 * @param {string} str2 The string to compare {@code str1} to.
 * @return {number} The comparator result, as described above.
 */
goog.string.caseInsensitiveCompare = function(str1, str2) {
  var test1 = String(str1).toLowerCase();
  var test2 = String(str2).toLowerCase();

  if (test1 < test2) {
    return -1;
  } else if (test1 == test2) {
    return 0;
  } else {
    return 1;
  }
};


/**
 * Regular expression used for splitting a string into substrings of fractional
 * numbers, integers, and non-numeric characters.
 * @type {RegExp}
 * @private
 */
goog.string.numerateCompareRegExp_ = /(\.\d+)|(\d+)|(\D+)/g;


/**
 * String comparison function that handles numbers in a way humans might expect.
 * Using this function, the string "File 2.jpg" sorts before "File 10.jpg". The
 * comparison is mostly case-insensitive, though strings that are identical
 * except for case are sorted with the upper-case strings before lower-case.
 *
 * This comparison function is significantly slower (about 500x) than either
 * the default or the case-insensitive compare. It should not be used in
 * time-critical code, but should be fast enough to sort several hundred short
 * strings (like filenames) with a reasonable delay.
 *
 * @param {string} str1 The string to compare in a numerically sensitive way.
 * @param {string} str2 The string to compare {@code str1} to.
 * @return {number} less than 0 if str1 < str2, 0 if str1 == str2, greater than
 *     0 if str1 > str2.
 */
goog.string.numerateCompare = function(str1, str2) {
  if (str1 == str2) {
    return 0;
  }
  if (!str1) {
    return -1;
  }
  if (!str2) {
    return 1;
  }

  // Using match to split the entire string ahead of time turns out to be faster
  // for most inputs than using RegExp.exec or iterating over each character.
  var tokens1 = str1.toLowerCase().match(goog.string.numerateCompareRegExp_);
  var tokens2 = str2.toLowerCase().match(goog.string.numerateCompareRegExp_);

  var count = Math.min(tokens1.length, tokens2.length);

  for (var i = 0; i < count; i++) {
    var a = tokens1[i];
    var b = tokens2[i];

    // Compare pairs of tokens, returning if one token sorts before the other.
    if (a != b) {

      // Only if both tokens are integers is a special comparison required.
      // Decimal numbers are sorted as strings (e.g., '.09' < '.1').
      var num1 = parseInt(a, 10);
      if (!isNaN(num1)) {
        var num2 = parseInt(b, 10);
        if (!isNaN(num2) && num1 - num2) {
          return num1 - num2;
        }
      }
      return a < b ? -1 : 1;
    }
  }

  // If one string is a substring of the other, the shorter string sorts first.
  if (tokens1.length != tokens2.length) {
    return tokens1.length - tokens2.length;
  }

  // The two strings must be equivalent except for case (perfect equality is
  // tested at the head of the function.) Revert to default ASCII-betical string
  // comparison to stablize the sort.
  return str1 < str2 ? -1 : 1;
};


/**
 * Regular expression used for determining if a string needs to be encoded.
 * @type {RegExp}
 * @private
 */
goog.string.encodeUriRegExp_ = /^[a-zA-Z0-9\-_.!~*'()]*$/;


/**
 * URL-encodes a string
 * @param {*} str The string to url-encode.
 * @return {string} An encoded copy of {@code str} that is safe for urls.
 *     Note that '#', ':', and other characters used to delimit portions
 *     of URLs *will* be encoded.
 */
goog.string.urlEncode = function(str) {
  str = String(str);
  // Checking if the search matches before calling encodeURIComponent avoids an
  // extra allocation in IE6. This adds about 10us time in FF and a similiar
  // over head in IE6 for lower working set apps, but for large working set
  // apps like Gmail, it saves about 70us per call.
  if (!goog.string.encodeUriRegExp_.test(str)) {
    return encodeURIComponent(str);
  }
  return str;
};


/**
 * URL-decodes the string. We need to specially handle '+'s because
 * the javascript library doesn't convert them to spaces.
 * @param {string} str The string to url decode.
 * @return {string} The decoded {@code str}.
 */
goog.string.urlDecode = function(str) {
  return decodeURIComponent(str.replace(/\+/g, ' '));
};


/**
 * Converts \n to <br>s or <br />s.
 * @param {string} str The string in which to convert newlines.
 * @param {boolean=} opt_xml Whether to use XML compatible tags.
 * @return {string} A copy of {@code str} with converted newlines.
 */
goog.string.newLineToBr = function(str, opt_xml) {
  return str.replace(/(\r\n|\r|\n)/g, opt_xml ? '<br />' : '<br>');
};


/**
 * Escape double quote '"' characters in addition to '&', '<', and '>' so that a
 * string can be included in an HTML tag attribute value within double quotes.
 *
 * It should be noted that > doesn't need to be escaped for the HTML or XML to
 * be valid, but it has been decided to escape it for consistency with other
 * implementations.
 *
 * NOTE(user):
 * HtmlEscape is often called during the generation of large blocks of HTML.
 * Using statics for the regular expressions and strings is an optimization
 * that can more than half the amount of time IE spends in this function for
 * large apps, since strings and regexes both contribute to GC allocations.
 *
 * Testing for the presence of a character before escaping increases the number
 * of function calls, but actually provides a speed increase for the average
 * case -- since the average case often doesn't require the escaping of all 4
 * characters and indexOf() is much cheaper than replace().
 * The worst case does suffer slightly from the additional calls, therefore the
 * opt_isLikelyToContainHtmlChars option has been included for situations
 * where all 4 HTML entities are very likely to be present and need escaping.
 *
 * Some benchmarks (times tended to fluctuate +-0.05ms):
 *                                     FireFox                     IE6
 * (no chars / average (mix of cases) / all 4 chars)
 * no checks                     0.13 / 0.22 / 0.22         0.23 / 0.53 / 0.80
 * indexOf                       0.08 / 0.17 / 0.26         0.22 / 0.54 / 0.84
 * indexOf + re test             0.07 / 0.17 / 0.28         0.19 / 0.50 / 0.85
 *
 * An additional advantage of checking if replace actually needs to be called
 * is a reduction in the number of object allocations, so as the size of the
 * application grows the difference between the various methods would increase.
 *
 * @param {string} str string to be escaped.
 * @param {boolean=} opt_isLikelyToContainHtmlChars Don't perform a check to see
 *     if the character needs replacing - use this option if you expect each of
 *     the characters to appear often. Leave false if you expect few html
 *     characters to occur in your strings, such as if you are escaping HTML.
 * @return {string} An escaped copy of {@code str}.
 */
goog.string.htmlEscape = function(str, opt_isLikelyToContainHtmlChars) {

  if (opt_isLikelyToContainHtmlChars) {
    return str.replace(goog.string.amperRe_, '&amp;')
          .replace(goog.string.ltRe_, '&lt;')
          .replace(goog.string.gtRe_, '&gt;')
          .replace(goog.string.quotRe_, '&quot;');

  } else {
    // quick test helps in the case when there are no chars to replace, in
    // worst case this makes barely a difference to the time taken
    if (!goog.string.allRe_.test(str)) return str;

    // str.indexOf is faster than regex.test in this case
    if (str.indexOf('&') != -1) {
      str = str.replace(goog.string.amperRe_, '&amp;');
    }
    if (str.indexOf('<') != -1) {
      str = str.replace(goog.string.ltRe_, '&lt;');
    }
    if (str.indexOf('>') != -1) {
      str = str.replace(goog.string.gtRe_, '&gt;');
    }
    if (str.indexOf('"') != -1) {
      str = str.replace(goog.string.quotRe_, '&quot;');
    }
    return str;
  }
};


/**
 * Regular expression that matches an ampersand, for use in escaping.
 * @type {RegExp}
 * @private
 */
goog.string.amperRe_ = /&/g;


/**
 * Regular expression that matches a less than sign, for use in escaping.
 * @type {RegExp}
 * @private
 */
goog.string.ltRe_ = /</g;


/**
 * Regular expression that matches a greater than sign, for use in escaping.
 * @type {RegExp}
 * @private
 */
goog.string.gtRe_ = />/g;


/**
 * Regular expression that matches a double quote, for use in escaping.
 * @type {RegExp}
 * @private
 */
goog.string.quotRe_ = /\"/g;


/**
 * Regular expression that matches any character that needs to be escaped.
 * @type {RegExp}
 * @private
 */
goog.string.allRe_ = /[&<>\"]/;


/**
 * Unescapes an HTML string.
 *
 * @param {string} str The string to unescape.
 * @return {string} An unescaped copy of {@code str}.
 */
goog.string.unescapeEntities = function(str) {
  if (goog.string.contains(str, '&')) {
    // We are careful not to use a DOM if we do not have one. We use the []
    // notation so that the JSCompiler will not complain about these objects and
    // fields in the case where we have no DOM.
    if ('document' in goog.global) {
      return goog.string.unescapeEntitiesUsingDom_(str);
    } else {
      // Fall back on pure XML entities
      return goog.string.unescapePureXmlEntities_(str);
    }
  }
  return str;
};


/**
 * Unescapes an HTML string using a DOM to resolve non-XML, non-numeric
 * entities. This function is XSS-safe and whitespace-preserving.
 * @private
 * @param {string} str The string to unescape.
 * @return {string} The unescaped {@code str} string.
 */
goog.string.unescapeEntitiesUsingDom_ = function(str) {
  var seen = {'&amp;': '&', '&lt;': '<', '&gt;': '>', '&quot;': '"'};
  var div = document.createElement('div');
  // Match as many valid entity characters as possible. If the actual entity
  // happens to be shorter, it will still work as innerHTML will return the
  // trailing characters unchanged. Since the entity characters do not include
  // open angle bracket, there is no chance of XSS from the innerHTML use.
  // Since no whitespace is passed to innerHTML, whitespace is preserved.
  return str.replace(goog.string.HTML_ENTITY_PATTERN_, function(s, entity) {
    // Check for cached entity.
    var value = seen[s];
    if (value) {
      return value;
    }
    // Check for numeric entity.
    if (entity.charAt(0) == '#') {
      // Prefix with 0 so that hex entities (e.g. &#x10) parse as hex numbers.
      var n = Number('0' + entity.substr(1));
      if (!isNaN(n)) {
        value = String.fromCharCode(n);
      }
    }
    // Fall back to innerHTML otherwise.
    if (!value) {
      // Append a non-entity character to avoid a bug in Webkit that parses
      // an invalid entity at the end of innerHTML text as the empty string.
      div.innerHTML = s + ' ';
      // Then remove the trailing character from the result.
      value = div.firstChild.nodeValue.slice(0, -1);
    }
    // Cache and return.
    return seen[s] = value;
  });
};


/**
 * Unescapes XML entities.
 * @private
 * @param {string} str The string to unescape.
 * @return {string} An unescaped copy of {@code str}.
 */
goog.string.unescapePureXmlEntities_ = function(str) {
  return str.replace(/&([^;]+);/g, function(s, entity) {
    switch (entity) {
      case 'amp':
        return '&';
      case 'lt':
        return '<';
      case 'gt':
        return '>';
      case 'quot':
        return '"';
      default:
        if (entity.charAt(0) == '#') {
          // Prefix with 0 so that hex entities (e.g. &#x10) parse as hex.
          var n = Number('0' + entity.substr(1));
          if (!isNaN(n)) {
            return String.fromCharCode(n);
          }
        }
        // For invalid entities we just return the entity
        return s;
    }
  });
};


/**
 * Regular expression that matches an HTML entity.
 * See also HTML5: Tokenization / Tokenizing character references.
 * @private
 * @type {!RegExp}
 */
goog.string.HTML_ENTITY_PATTERN_ = /&([^;\s<&]+);?/g;


/**
 * Do escaping of whitespace to preserve spatial formatting. We use character
 * entity #160 to make it safer for xml.
 * @param {string} str The string in which to escape whitespace.
 * @param {boolean=} opt_xml Whether to use XML compatible tags.
 * @return {string} An escaped copy of {@code str}.
 */
goog.string.whitespaceEscape = function(str, opt_xml) {
  return goog.string.newLineToBr(str.replace(/  /g, ' &#160;'), opt_xml);
};


/**
 * Strip quote characters around a string.  The second argument is a string of
 * characters to treat as quotes.  This can be a single character or a string of
 * multiple character and in that case each of those are treated as possible
 * quote characters. For example:
 *
 * <pre>
 * goog.string.stripQuotes('"abc"', '"`') --> 'abc'
 * goog.string.stripQuotes('`abc`', '"`') --> 'abc'
 * </pre>
 *
 * @param {string} str The string to strip.
 * @param {string} quoteChars The quote characters to strip.
 * @return {string} A copy of {@code str} without the quotes.
 */
goog.string.stripQuotes = function(str, quoteChars) {
  var length = quoteChars.length;
  for (var i = 0; i < length; i++) {
    var quoteChar = length == 1 ? quoteChars : quoteChars.charAt(i);
    if (str.charAt(0) == quoteChar && str.charAt(str.length - 1) == quoteChar) {
      return str.substring(1, str.length - 1);
    }
  }
  return str;
};


/**
 * Truncates a string to a certain length and adds '...' if necessary.  The
 * length also accounts for the ellipsis, so a maximum length of 10 and a string
 * 'Hello World!' produces 'Hello W...'.
 * @param {string} str The string to truncate.
 * @param {number} chars Max number of characters.
 * @param {boolean=} opt_protectEscapedCharacters Whether to protect escaped
 *     characters from being cut off in the middle.
 * @return {string} The truncated {@code str} string.
 */
goog.string.truncate = function(str, chars, opt_protectEscapedCharacters) {
  if (opt_protectEscapedCharacters) {
    str = goog.string.unescapeEntities(str);
  }

  if (str.length > chars) {
    str = str.substring(0, chars - 3) + '...';
  }

  if (opt_protectEscapedCharacters) {
    str = goog.string.htmlEscape(str);
  }

  return str;
};


/**
 * Truncate a string in the middle, adding "..." if necessary,
 * and favoring the beginning of the string.
 * @param {string} str The string to truncate the middle of.
 * @param {number} chars Max number of characters.
 * @param {boolean=} opt_protectEscapedCharacters Whether to protect escaped
 *     characters from being cutoff in the middle.
 * @param {number=} opt_trailingChars Optional number of trailing characters to
 *     leave at the end of the string, instead of truncating as close to the
 *     middle as possible.
 * @return {string} A truncated copy of {@code str}.
 */
goog.string.truncateMiddle = function(str, chars,
    opt_protectEscapedCharacters, opt_trailingChars) {
  if (opt_protectEscapedCharacters) {
    str = goog.string.unescapeEntities(str);
  }

  if (opt_trailingChars && str.length > chars) {
    if (opt_trailingChars > chars) {
      opt_trailingChars = chars;
    }
    var endPoint = str.length - opt_trailingChars;
    var startPoint = chars - opt_trailingChars;
    str = str.substring(0, startPoint) + '...' + str.substring(endPoint);
  } else if (str.length > chars) {
    // Favor the beginning of the string:
    var half = Math.floor(chars / 2);
    var endPos = str.length - half;
    half += chars % 2;
    str = str.substring(0, half) + '...' + str.substring(endPos);
  }

  if (opt_protectEscapedCharacters) {
    str = goog.string.htmlEscape(str);
  }

  return str;
};


/**
 * Special chars that need to be escaped for goog.string.quote.
 * @private
 * @type {Object}
 */
goog.string.specialEscapeChars_ = {
  '\0': '\\0',
  '\b': '\\b',
  '\f': '\\f',
  '\n': '\\n',
  '\r': '\\r',
  '\t': '\\t',
  '\x0B': '\\x0B', // '\v' is not supported in JScript
  '"': '\\"',
  '\\': '\\\\'
};


/**
 * Character mappings used internally for goog.string.escapeChar.
 * @private
 * @type {Object}
 */
goog.string.jsEscapeCache_ = {
  '\'': '\\\''
};


/**
 * Encloses a string in double quotes and escapes characters so that the
 * string is a valid JS string.
 * @param {string} s The string to quote.
 * @return {string} A copy of {@code s} surrounded by double quotes.
 */
goog.string.quote = function(s) {
  s = String(s);
  if (s.quote) {
    return s.quote();
  } else {
    var sb = ['"'];
    for (var i = 0; i < s.length; i++) {
      var ch = s.charAt(i);
      var cc = ch.charCodeAt(0);
      sb[i + 1] = goog.string.specialEscapeChars_[ch] ||
          ((cc > 31 && cc < 127) ? ch : goog.string.escapeChar(ch));
    }
    sb.push('"');
    return sb.join('');
  }
};


/**
 * Takes a string and returns the escaped string for that character.
 * @param {string} str The string to escape.
 * @return {string} An escaped string representing {@code str}.
 */
goog.string.escapeString = function(str) {
  var sb = [];
  for (var i = 0; i < str.length; i++) {
    sb[i] = goog.string.escapeChar(str.charAt(i));
  }
  return sb.join('');
};


/**
 * Takes a character and returns the escaped string for that character. For
 * example escapeChar(String.fromCharCode(15)) -> "\\x0E".
 * @param {string} c The character to escape.
 * @return {string} An escaped string representing {@code c}.
 */
goog.string.escapeChar = function(c) {
  if (c in goog.string.jsEscapeCache_) {
    return goog.string.jsEscapeCache_[c];
  }

  if (c in goog.string.specialEscapeChars_) {
    return goog.string.jsEscapeCache_[c] = goog.string.specialEscapeChars_[c];
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

  return goog.string.jsEscapeCache_[c] = rv;
};


/**
 * Takes a string and creates a map (Object) in which the keys are the
 * characters in the string. The value for the key is set to true. You can
 * then use goog.object.map or goog.array.map to change the values.
 * @param {string} s The string to build the map from.
 * @return {Object} The map of characters used.
 */
// TODO(arv): It seems like we should have a generic goog.array.toMap. But do
//            we want a dependency on goog.array in goog.string?
goog.string.toMap = function(s) {
  var rv = {};
  for (var i = 0; i < s.length; i++) {
    rv[s.charAt(i)] = true;
  }
  return rv;
};


/**
 * Checks whether a string contains a given character.
 * @param {string} s The string to test.
 * @param {string} ss The substring to test for.
 * @return {boolean} True if {@code s} contains {@code ss}.
 */
goog.string.contains = function(s, ss) {
  return s.indexOf(ss) != -1;
};


/**
 * Returns the non-overlapping occurrences of ss in s.
 * If either s or ss evalutes to false, then returns zero.
 * @param {string} s The string to look in.
 * @param {string} ss The string to look for.
 * @return {number} Number of occurrences of ss in s.
 */
goog.string.countOf = function(s, ss) {
  return s && ss ? s.split(ss).length - 1 : 0;
};


/**
 * Removes a substring of a specified length at a specific
 * index in a string.
 * @param {string} s The base string from which to remove.
 * @param {number} index The index at which to remove the substring.
 * @param {number} stringLength The length of the substring to remove.
 * @return {string} A copy of {@code s} with the substring removed or the full
 *     string if nothing is removed or the input is invalid.
 */
goog.string.removeAt = function(s, index, stringLength) {
  var resultStr = s;
  // If the index is greater or equal to 0 then remove substring
  if (index >= 0 && index < s.length && stringLength > 0) {
    resultStr = s.substr(0, index) +
        s.substr(index + stringLength, s.length - index - stringLength);
  }
  return resultStr;
};


/**
 *  Removes the first occurrence of a substring from a string.
 *  @param {string} s The base string from which to remove.
 *  @param {string} ss The string to remove.
 *  @return {string} A copy of {@code s} with {@code ss} removed or the full
 *      string if nothing is removed.
 */
goog.string.remove = function(s, ss) {
  var re = new RegExp(goog.string.regExpEscape(ss), '');
  return s.replace(re, '');
};


/**
 *  Removes all occurrences of a substring from a string.
 *  @param {string} s The base string from which to remove.
 *  @param {string} ss The string to remove.
 *  @return {string} A copy of {@code s} with {@code ss} removed or the full
 *      string if nothing is removed.
 */
goog.string.removeAll = function(s, ss) {
  var re = new RegExp(goog.string.regExpEscape(ss), 'g');
  return s.replace(re, '');
};


/**
 * Escapes characters in the string that are not safe to use in a RegExp.
 * @param {*} s The string to escape. If not a string, it will be casted
 *     to one.
 * @return {string} A RegExp safe, escaped copy of {@code s}.
 */
goog.string.regExpEscape = function(s) {
  return String(s).replace(/([-()\[\]{}+?*.$\^|,:#<!\\])/g, '\\$1').
      replace(/\x08/g, '\\x08');
};


/**
 * Repeats a string n times.
 * @param {string} string The string to repeat.
 * @param {number} length The number of times to repeat.
 * @return {string} A string containing {@code length} repetitions of
 *     {@code string}.
 */
goog.string.repeat = function(string, length) {
  return new Array(length + 1).join(string);
};


/**
 * Pads number to given length and optionally rounds it to a given precision.
 * For example:
 * <pre>padNumber(1.25, 2, 3) -> '01.250'
 * padNumber(1.25, 2) -> '01.25'
 * padNumber(1.25, 2, 1) -> '01.3'
 * padNumber(1.25, 0) -> '1.25'</pre>
 *
 * @param {number} num The number to pad.
 * @param {number} length The desired length.
 * @param {number=} opt_precision The desired precision.
 * @return {string} {@code num} as a string with the given options.
 */
goog.string.padNumber = function(num, length, opt_precision) {
  var s = goog.isDef(opt_precision) ? num.toFixed(opt_precision) : String(num);
  var index = s.indexOf('.');
  if (index == -1) {
    index = s.length;
  }
  return goog.string.repeat('0', Math.max(0, length - index)) + s;
};


/**
 * Returns a string representation of the given object, with
 * null and undefined being returned as the empty string.
 *
 * @param {*} obj The object to convert.
 * @return {string} A string representation of the {@code obj}.
 */
goog.string.makeSafe = function(obj) {
  return obj == null ? '' : String(obj);
};


/**
 * Concatenates string expressions. This is useful
 * since some browsers are very inefficient when it comes to using plus to
 * concat strings. Be careful when using null and undefined here since
 * these will not be included in the result. If you need to represent these
 * be sure to cast the argument to a String first.
 * For example:
 * <pre>buildString('a', 'b', 'c', 'd') -> 'abcd'
 * buildString(null, undefined) -> ''
 * </pre>
 * @param {...*} var_args A list of strings to concatenate. If not a string,
 *     it will be casted to one.
 * @return {string} The concatenation of {@code var_args}.
 */
goog.string.buildString = function(var_args) {
  return Array.prototype.join.call(arguments, '');
};


/**
 * Returns a string with at least 64-bits of randomness.
 *
 * Doesn't trust Javascript's random function entirely. Uses a combination of
 * random and current timestamp, and then encodes the string in base-36 to
 * make it shorter.
 *
 * @return {string} A random string, e.g. sn1s7vb4gcic.
 */
goog.string.getRandomString = function() {
  var x = 2147483648;
  return Math.floor(Math.random() * x).toString(36) +
         Math.abs(Math.floor(Math.random() * x) ^ goog.now()).toString(36);
};


/**
 * Compares two version numbers.
 *
 * @param {string|number} version1 Version of first item.
 * @param {string|number} version2 Version of second item.
 *
 * @return {number}  1 if {@code version1} is higher.
 *                   0 if arguments are equal.
 *                  -1 if {@code version2} is higher.
 */
goog.string.compareVersions = function(version1, version2) {
  var order = 0;
  // Trim leading and trailing whitespace and split the versions into
  // subversions.
  var v1Subs = goog.string.trim(String(version1)).split('.');
  var v2Subs = goog.string.trim(String(version2)).split('.');
  var subCount = Math.max(v1Subs.length, v2Subs.length);

  // Iterate over the subversions, as long as they appear to be equivalent.
  for (var subIdx = 0; order == 0 && subIdx < subCount; subIdx++) {
    var v1Sub = v1Subs[subIdx] || '';
    var v2Sub = v2Subs[subIdx] || '';

    // Split the subversions into pairs of numbers and qualifiers (like 'b').
    // Two different RegExp objects are needed because they are both using
    // the 'g' flag.
    var v1CompParser = new RegExp('(\\d*)(\\D*)', 'g');
    var v2CompParser = new RegExp('(\\d*)(\\D*)', 'g');
    do {
      var v1Comp = v1CompParser.exec(v1Sub) || ['', '', ''];
      var v2Comp = v2CompParser.exec(v2Sub) || ['', '', ''];
      // Break if there are no more matches.
      if (v1Comp[0].length == 0 && v2Comp[0].length == 0) {
        break;
      }

      // Parse the numeric part of the subversion. A missing number is
      // equivalent to 0.
      var v1CompNum = v1Comp[1].length == 0 ? 0 : parseInt(v1Comp[1], 10);
      var v2CompNum = v2Comp[1].length == 0 ? 0 : parseInt(v2Comp[1], 10);

      // Compare the subversion components. The number has the highest
      // precedence. Next, if the numbers are equal, a subversion without any
      // qualifier is always higher than a subversion with any qualifier. Next,
      // the qualifiers are compared as strings.
      order = goog.string.compareElements_(v1CompNum, v2CompNum) ||
          goog.string.compareElements_(v1Comp[2].length == 0,
              v2Comp[2].length == 0) ||
          goog.string.compareElements_(v1Comp[2], v2Comp[2]);
      // Stop as soon as an inequality is discovered.
    } while (order == 0);
  }

  return order;
};


/**
 * Compares elements of a version number.
 *
 * @param {string|number|boolean} left An element from a version number.
 * @param {string|number|boolean} right An element from a version number.
 *
 * @return {number}  1 if {@code left} is higher.
 *                   0 if arguments are equal.
 *                  -1 if {@code right} is higher.
 * @private
 */
goog.string.compareElements_ = function(left, right) {
  if (left < right) {
    return -1;
  } else if (left > right) {
    return 1;
  }
  return 0;
};


/**
 * Maximum value of #goog.string.hashCode, exclusive. 2^32.
 * @type {number}
 * @private
 */
goog.string.HASHCODE_MAX_ = 0x100000000;


/**
 * String hash function similar to java.lang.String.hashCode().
 * The hash code for a string is computed as
 * s[0] * 31 ^ (n - 1) + s[1] * 31 ^ (n - 2) + ... + s[n - 1],
 * where s[i] is the ith character of the string and n is the length of
 * the string. We mod the result to make it between 0 (inclusive) and 2^32
 * (exclusive).
 * @param {string} str A string.
 * @return {number} Hash value for {@code str}, between 0 (inclusive) and 2^32
 *  (exclusive). The empty string returns 0.
 */
goog.string.hashCode = function(str) {
  var result = 0;
  for (var i = 0; i < str.length; ++i) {
    result = 31 * result + str.charCodeAt(i);
    // Normalize to 4 byte range, 0 ... 2^32.
    result %= goog.string.HASHCODE_MAX_;
  }
  return result;
};


/**
 * The most recent unique ID. |0 is equivalent to Math.floor in this case.
 * @type {number}
 * @private
 */
goog.string.uniqueStringCounter_ = Math.random() * 0x80000000 | 0;


/**
 * Generates and returns a string which is unique in the current document.
 * This is useful, for example, to create unique IDs for DOM elements.
 * @return {string} A unique id.
 */
goog.string.createUniqueString = function() {
  return 'goog_' + goog.string.uniqueStringCounter_++;
};


/**
 * Converts the supplied string to a number, which may be Ininity or NaN.
 * This function strips whitespace: (toNumber(' 123') === 123)
 * This function accepts scientific notation: (toNumber('1e1') === 10)
 *
 * This is better than Javascript's built-in conversions because, sadly:
 *     (Number(' ') === 0) and (parseFloat('123a') === 123)
 *
 * @param {string} str The string to convert.
 * @return {number} The number the supplied string represents, or NaN.
 */
goog.string.toNumber = function(str) {
  var num = Number(str);
  if (num == 0 && goog.string.isEmpty(str)) {
    return NaN;
  }
  return num;
};


/**
 * A memoized cache for goog.string.toCamelCase.
 * @type {Object.<string>}
 * @private
 */
goog.string.toCamelCaseCache_ = {};


/**
 * Converts a string from selector-case to camelCase (e.g. from
 * "multi-part-string" to "multiPartString"), useful for converting
 * CSS selectors and HTML dataset keys to their equivalent JS properties.
 * @param {string} str The string in selector-case form.
 * @return {string} The string in camelCase form.
 */
goog.string.toCamelCase = function(str) {
  return goog.string.toCamelCaseCache_[str] ||
      (goog.string.toCamelCaseCache_[str] =
          String(str).replace(/\-([a-z])/g, function(all, match) {
            return match.toUpperCase();
          }));
};


/**
 * A memoized cache for goog.string.toSelectorCase.
 * @type {Object.<string>}
 * @private
 */
goog.string.toSelectorCaseCache_ = {};


/**
 * Converts a string from camelCase to selector-case (e.g. from
 * "multiPartString" to "multi-part-string"), useful for converting JS
 * style and dataset properties to equivalent CSS selectors and HTML keys.
 * @param {string} str The string in camelCase form.
 * @return {string} The string in selector-case form.
 */
goog.string.toSelectorCase = function(str) {
  return goog.string.toSelectorCaseCache_[str] ||
      (goog.string.toSelectorCaseCache_[str] =
          String(str).replace(/([A-Z])/g, '-$1').toLowerCase());
};

//javascript/closure/asserts/asserts.js
// Copyright 2008 The Closure Library Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @fileoverview Utilities to check the preconditions, postconditions and
 * invariants runtime.
 *
 * Methods in this package should be given special treatment by the compiler
 * for type-inference. For example, <code>goog.asserts.assert(foo)</code>
 * will restrict <code>foo</code> to a truthy value.
 *
 * The compiler has an option to disable asserts. So code like:
 * <code>
 * var x = goog.asserts.assert(foo()); goog.asserts.assert(bar());
 * </code>
 * will be transformed into:
 * <code>
 * var x = foo();
 * </code>
 * The compiler will leave in foo() (because its return value is used),
 * but it will remove bar() because it assumes it does not have side-effects.
 *
 * @author pallosp@google.com (Peter Pallos)
 * @author agrieve@google.com (Andrew Grieve)
 */

goog.provide('goog.asserts');
goog.provide('goog.asserts.AssertionError');

goog.require('goog.debug.Error');
goog.require('goog.string');


/**
 * @define {boolean} Whether to strip out asserts or to leave them in.
 */
goog.asserts.ENABLE_ASSERTS = goog.DEBUG;



/**
 * Error object for failed assertions.
 * @param {string} messagePattern The pattern that was used to form message.
 * @param {!Array.<*>} messageArgs The items to substitute into the pattern.
 * @constructor
 * @extends {goog.debug.Error}
 */
goog.asserts.AssertionError = function(messagePattern, messageArgs) {
  messageArgs.unshift(messagePattern);
  goog.debug.Error.call(this, goog.string.subs.apply(null, messageArgs));
  // Remove the messagePattern afterwards to avoid permenantly modifying the
  // passed in array.
  messageArgs.shift();

  /**
   * The message pattern used to format the error message. Error handlers can
   * use this to uniquely identify the assertion.
   * @type {string}
   */
  this.messagePattern = messagePattern;
};
goog.inherits(goog.asserts.AssertionError, goog.debug.Error);


/** @override */
goog.asserts.AssertionError.prototype.name = 'AssertionError';


/**
 * Throws an exception with the given message and "Assertion failed" prefixed
 * onto it.
 * @param {string} defaultMessage The message to use if givenMessage is empty.
 * @param {Array.<*>} defaultArgs The substitution arguments for defaultMessage.
 * @param {string|undefined} givenMessage Message supplied by the caller.
 * @param {Array.<*>} givenArgs The substitution arguments for givenMessage.
 * @throws {goog.asserts.AssertionError} When the value is not a number.
 * @private
 */
goog.asserts.doAssertFailure_ =
    function(defaultMessage, defaultArgs, givenMessage, givenArgs) {
  var message = 'Assertion failed';
  if (givenMessage) {
    message += ': ' + givenMessage;
    var args = givenArgs;
  } else if (defaultMessage) {
    message += ': ' + defaultMessage;
    args = defaultArgs;
  }
  // The '' + works around an Opera 10 bug in the unit tests. Without it,
  // a stack trace is added to var message above. With this, a stack trace is
  // not added until this line (it causes the extra garbage to be added after
  // the assertion message instead of in the middle of it).
  throw new goog.asserts.AssertionError('' + message, args || []);
};


/**
 * Checks if the condition evaluates to true if goog.asserts.ENABLE_ASSERTS is
 * true.
 * @param {*} condition The condition to check.
 * @param {string=} opt_message Error message in case of failure.
 * @param {...*} var_args The items to substitute into the failure message.
 * @return {*} The value of the condition.
 * @throws {goog.asserts.AssertionError} When the condition evaluates to false.
 */
goog.asserts.assert = function(condition, opt_message, var_args) {
  if (goog.asserts.ENABLE_ASSERTS && !condition) {
    goog.asserts.doAssertFailure_('', null, opt_message,
        Array.prototype.slice.call(arguments, 2));
  }
  return condition;
};


/**
 * Fails if goog.asserts.ENABLE_ASSERTS is true. This function is useful in case
 * when we want to add a check in the unreachable area like switch-case
 * statement:
 *
 * <pre>
 *  switch(type) {
 *    case FOO: doSomething(); break;
 *    case BAR: doSomethingElse(); break;
 *    default: goog.assert.fail('Unrecognized type: ' + type);
 *      // We have only 2 types - "default:" section is unreachable code.
 *  }
 * </pre>
 *
 * @param {string=} opt_message Error message in case of failure.
 * @param {...*} var_args The items to substitute into the failure message.
 * @throws {goog.asserts.AssertionError} Failure.
 */
goog.asserts.fail = function(opt_message, var_args) {
  if (goog.asserts.ENABLE_ASSERTS) {
    throw new goog.asserts.AssertionError(
        'Failure' + (opt_message ? ': ' + opt_message : ''),
        Array.prototype.slice.call(arguments, 1));
  }
};


/**
 * Checks if the value is a number if goog.asserts.ENABLE_ASSERTS is true.
 * @param {*} value The value to check.
 * @param {string=} opt_message Error message in case of failure.
 * @param {...*} var_args The items to substitute into the failure message.
 * @return {number} The value, guaranteed to be a number when asserts enabled.
 * @throws {goog.asserts.AssertionError} When the value is not a number.
 */
goog.asserts.assertNumber = function(value, opt_message, var_args) {
  if (goog.asserts.ENABLE_ASSERTS && !goog.isNumber(value)) {
    goog.asserts.doAssertFailure_('Expected number but got %s: %s.',
        [goog.typeOf(value), value], opt_message,
        Array.prototype.slice.call(arguments, 2));
  }
  return /** @type {number} */ (value);
};


/**
 * Checks if the value is a string if goog.asserts.ENABLE_ASSERTS is true.
 * @param {*} value The value to check.
 * @param {string=} opt_message Error message in case of failure.
 * @param {...*} var_args The items to substitute into the failure message.
 * @return {string} The value, guaranteed to be a string when asserts enabled.
 * @throws {goog.asserts.AssertionError} When the value is not a string.
 */
goog.asserts.assertString = function(value, opt_message, var_args) {
  if (goog.asserts.ENABLE_ASSERTS && !goog.isString(value)) {
    goog.asserts.doAssertFailure_('Expected string but got %s: %s.',
        [goog.typeOf(value), value], opt_message,
        Array.prototype.slice.call(arguments, 2));
  }
  return /** @type {string} */ (value);
};


/**
 * Checks if the value is a function if goog.asserts.ENABLE_ASSERTS is true.
 * @param {*} value The value to check.
 * @param {string=} opt_message Error message in case of failure.
 * @param {...*} var_args The items to substitute into the failure message.
 * @return {!Function} The value, guaranteed to be a function when asserts
 *     enabled.
 * @throws {goog.asserts.AssertionError} When the value is not a function.
 */
goog.asserts.assertFunction = function(value, opt_message, var_args) {
  if (goog.asserts.ENABLE_ASSERTS && !goog.isFunction(value)) {
    goog.asserts.doAssertFailure_('Expected function but got %s: %s.',
        [goog.typeOf(value), value], opt_message,
        Array.prototype.slice.call(arguments, 2));
  }
  return /** @type {!Function} */ (value);
};


/**
 * Checks if the value is an Object if goog.asserts.ENABLE_ASSERTS is true.
 * @param {*} value The value to check.
 * @param {string=} opt_message Error message in case of failure.
 * @param {...*} var_args The items to substitute into the failure message.
 * @return {!Object} The value, guaranteed to be a non-null object.
 * @throws {goog.asserts.AssertionError} When the value is not an object.
 */
goog.asserts.assertObject = function(value, opt_message, var_args) {
  if (goog.asserts.ENABLE_ASSERTS && !goog.isObject(value)) {
    goog.asserts.doAssertFailure_('Expected object but got %s: %s.',
        [goog.typeOf(value), value],
        opt_message, Array.prototype.slice.call(arguments, 2));
  }
  return /** @type {!Object} */ (value);
};


/**
 * Checks if the value is an Array if goog.asserts.ENABLE_ASSERTS is true.
 * @param {*} value The value to check.
 * @param {string=} opt_message Error message in case of failure.
 * @param {...*} var_args The items to substitute into the failure message.
 * @return {!Array} The value, guaranteed to be a non-null array.
 * @throws {goog.asserts.AssertionError} When the value is not an array.
 */
goog.asserts.assertArray = function(value, opt_message, var_args) {
  if (goog.asserts.ENABLE_ASSERTS && !goog.isArray(value)) {
    goog.asserts.doAssertFailure_('Expected array but got %s: %s.',
        [goog.typeOf(value), value], opt_message,
        Array.prototype.slice.call(arguments, 2));
  }
  return /** @type {!Array} */ (value);
};


/**
 * Checks if the value is a boolean if goog.asserts.ENABLE_ASSERTS is true.
 * @param {*} value The value to check.
 * @param {string=} opt_message Error message in case of failure.
 * @param {...*} var_args The items to substitute into the failure message.
 * @return {boolean} The value, guaranteed to be a boolean when asserts are
 *     enabled.
 * @throws {goog.asserts.AssertionError} When the value is not a boolean.
 */
goog.asserts.assertBoolean = function(value, opt_message, var_args) {
  if (goog.asserts.ENABLE_ASSERTS && !goog.isBoolean(value)) {
    goog.asserts.doAssertFailure_('Expected boolean but got %s: %s.',
        [goog.typeOf(value), value], opt_message,
        Array.prototype.slice.call(arguments, 2));
  }
  return /** @type {boolean} */ (value);
};


/**
 * Checks if the value is an instance of the user-defined type if
 * goog.asserts.ENABLE_ASSERTS is true.
 * @param {*} value The value to check.
 * @param {!Function} type A user-defined constructor.
 * @param {string=} opt_message Error message in case of failure.
 * @param {...*} var_args The items to substitute into the failure message.
 * @throws {goog.asserts.AssertionError} When the value is not an instance of
 *     type.
 */
goog.asserts.assertInstanceof = function(value, type, opt_message, var_args) {
  if (goog.asserts.ENABLE_ASSERTS && !(value instanceof type)) {
    goog.asserts.doAssertFailure_('instanceof check failed.', null,
        opt_message, Array.prototype.slice.call(arguments, 3));
  }
};


//javascript/closure/i18n/datetimesymbols.js
// Copyright 2011 The Closure Library Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @fileoverview Date/time formatting symbols for all locales.
 *
 * This file is autogenerated by script.  See
 * http://go/generate_datetime_constants.py using the --for_closure flag.
 *
 * To reduce the file size (which may cause issues in some JS
 * developing environments), this file will only contain locales
 * that are usually supported by google products. It is a super
 * set of 40 languages. Rest of the data can be found in another file
 * named "datetimesymbolsext.js", which will be generated at the same
 * time as this file.
 * Before checkin, this file could have been manually edited. This is
 * to incorporate changes before we could correct CLDR. All manual
 * modification must be documented in this section, and should be
 * removed after those changes land to CLDR.
 */

goog.provide('goog.i18n.DateTimeSymbols');
goog.provide('goog.i18n.DateTimeSymbols_am');
goog.provide('goog.i18n.DateTimeSymbols_ar');
goog.provide('goog.i18n.DateTimeSymbols_bg');
goog.provide('goog.i18n.DateTimeSymbols_bn');
goog.provide('goog.i18n.DateTimeSymbols_ca');
goog.provide('goog.i18n.DateTimeSymbols_cs');
goog.provide('goog.i18n.DateTimeSymbols_da');
goog.provide('goog.i18n.DateTimeSymbols_de');
goog.provide('goog.i18n.DateTimeSymbols_de_AT');
goog.provide('goog.i18n.DateTimeSymbols_de_CH');
goog.provide('goog.i18n.DateTimeSymbols_el');
goog.provide('goog.i18n.DateTimeSymbols_en');
goog.provide('goog.i18n.DateTimeSymbols_en_AU');
goog.provide('goog.i18n.DateTimeSymbols_en_GB');
goog.provide('goog.i18n.DateTimeSymbols_en_IE');
goog.provide('goog.i18n.DateTimeSymbols_en_IN');
goog.provide('goog.i18n.DateTimeSymbols_en_ISO');
goog.provide('goog.i18n.DateTimeSymbols_en_SG');
goog.provide('goog.i18n.DateTimeSymbols_en_US');
goog.provide('goog.i18n.DateTimeSymbols_en_ZA');
goog.provide('goog.i18n.DateTimeSymbols_es');
goog.provide('goog.i18n.DateTimeSymbols_es_419');
goog.provide('goog.i18n.DateTimeSymbols_et');
goog.provide('goog.i18n.DateTimeSymbols_eu');
goog.provide('goog.i18n.DateTimeSymbols_fa');
goog.provide('goog.i18n.DateTimeSymbols_fi');
goog.provide('goog.i18n.DateTimeSymbols_fil');
goog.provide('goog.i18n.DateTimeSymbols_fr');
goog.provide('goog.i18n.DateTimeSymbols_fr_CA');
goog.provide('goog.i18n.DateTimeSymbols_gl');
goog.provide('goog.i18n.DateTimeSymbols_gsw');
goog.provide('goog.i18n.DateTimeSymbols_gu');
goog.provide('goog.i18n.DateTimeSymbols_he');
goog.provide('goog.i18n.DateTimeSymbols_hi');
goog.provide('goog.i18n.DateTimeSymbols_hr');
goog.provide('goog.i18n.DateTimeSymbols_hu');
goog.provide('goog.i18n.DateTimeSymbols_id');
goog.provide('goog.i18n.DateTimeSymbols_in');
goog.provide('goog.i18n.DateTimeSymbols_is');
goog.provide('goog.i18n.DateTimeSymbols_it');
goog.provide('goog.i18n.DateTimeSymbols_iw');
goog.provide('goog.i18n.DateTimeSymbols_ja');
goog.provide('goog.i18n.DateTimeSymbols_kn');
goog.provide('goog.i18n.DateTimeSymbols_ko');
goog.provide('goog.i18n.DateTimeSymbols_ln');
goog.provide('goog.i18n.DateTimeSymbols_lt');
goog.provide('goog.i18n.DateTimeSymbols_lv');
goog.provide('goog.i18n.DateTimeSymbols_ml');
goog.provide('goog.i18n.DateTimeSymbols_mr');
goog.provide('goog.i18n.DateTimeSymbols_ms');
goog.provide('goog.i18n.DateTimeSymbols_mt');
goog.provide('goog.i18n.DateTimeSymbols_nl');
goog.provide('goog.i18n.DateTimeSymbols_no');
goog.provide('goog.i18n.DateTimeSymbols_or');
goog.provide('goog.i18n.DateTimeSymbols_pl');
goog.provide('goog.i18n.DateTimeSymbols_pt');
goog.provide('goog.i18n.DateTimeSymbols_pt_BR');
goog.provide('goog.i18n.DateTimeSymbols_pt_PT');
goog.provide('goog.i18n.DateTimeSymbols_ro');
goog.provide('goog.i18n.DateTimeSymbols_ru');
goog.provide('goog.i18n.DateTimeSymbols_sk');
goog.provide('goog.i18n.DateTimeSymbols_sl');
goog.provide('goog.i18n.DateTimeSymbols_sq');
goog.provide('goog.i18n.DateTimeSymbols_sr');
goog.provide('goog.i18n.DateTimeSymbols_sv');
goog.provide('goog.i18n.DateTimeSymbols_sw');
goog.provide('goog.i18n.DateTimeSymbols_ta');
goog.provide('goog.i18n.DateTimeSymbols_te');
goog.provide('goog.i18n.DateTimeSymbols_th');
goog.provide('goog.i18n.DateTimeSymbols_tl');
goog.provide('goog.i18n.DateTimeSymbols_tr');
goog.provide('goog.i18n.DateTimeSymbols_uk');
goog.provide('goog.i18n.DateTimeSymbols_ur');
goog.provide('goog.i18n.DateTimeSymbols_vi');
goog.provide('goog.i18n.DateTimeSymbols_zh');
goog.provide('goog.i18n.DateTimeSymbols_zh_CN');
goog.provide('goog.i18n.DateTimeSymbols_zh_HK');
goog.provide('goog.i18n.DateTimeSymbols_zh_TW');


/**
 * Date/time formatting symbols for locale en_ISO.
 */
goog.i18n.DateTimeSymbols_en_ISO = {
  ERAS: ['BC', 'AD'],
  ERANAMES: ['Before Christ', 'Anno Domini'],
  NARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O',
      'N', 'D'],
  MONTHS: ['January', 'February', 'March', 'April', 'May', 'June', 'July',
      'August', 'September', 'October', 'November', 'December'],
  STANDALONEMONTHS: ['January', 'February', 'March', 'April', 'May', 'June',
      'July', 'August', 'September', 'October', 'November', 'December'],
  SHORTMONTHS: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep',
      'Oct', 'Nov', 'Dec'],
  STANDALONESHORTMONTHS: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul',
      'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
  WEEKDAYS: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday',
      'Saturday'],
  STANDALONEWEEKDAYS: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday',
      'Friday', 'Saturday'],
  SHORTWEEKDAYS: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
  STANDALONESHORTWEEKDAYS: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
  NARROWWEEKDAYS: ['S', 'M', 'T', 'W', 'T', 'F', 'S'],
  STANDALONENARROWWEEKDAYS: ['S', 'M', 'T', 'W', 'T', 'F', 'S'],
  SHORTQUARTERS: ['Q1', 'Q2', 'Q3', 'Q4'],
  QUARTERS: ['1st quarter', '2nd quarter', '3rd quarter', '4th quarter'],
  AMPMS: ['AM', 'PM'],
  DATEFORMATS: ['EEEE, y MMMM dd', 'y MMMM d', 'y MMM d', 'yyyy-MM-dd'],
  TIMEFORMATS: ['HH:mm:ss v', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  AVAILABLEFORMATS: {'Md': 'M/d', 'MMMMd': 'MMMM d', 'MMMd': 'MMM d'},
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale am.
 */
goog.i18n.DateTimeSymbols_am = {
  ERAS: ['ዓ/ዓ', 'ዓ/ም'],
  ERANAMES: ['ዓመተ ዓለም', 'ዓመተ ምሕረት'],
  NARROWMONTHS: ['ጃ', 'ፌ', 'ማ', 'ኤ', 'ሜ', 'ጁ', 'ጁ', 'ኦ', 'ሴ',
      'ኦ', 'ኖ', 'ዲ'],
  STANDALONENARROWMONTHS: ['ጃ', 'ፌ', 'ማ', 'ኤ', 'ሜ', 'ጁ', 'ጁ',
      'ኦ', 'ሴ', 'ኦ', 'ኖ', 'ዲ'],
  MONTHS: ['ጃንዩወሪ', 'ፌብሩወሪ', 'ማርች', 'ኤፕረል',
      'ሜይ', 'ጁን', 'ጁላይ', 'ኦገስት', 'ሴፕቴምበር',
      'ኦክተውበር', 'ኖቬምበር', 'ዲሴምበር'],
  STANDALONEMONTHS: ['ጃንዩወሪ', 'ፌብሩወሪ', 'ማርች',
      'ኤፕረል', 'ሜይ', 'ጁን', 'ጁላይ', 'ኦገስት',
      'ሴፕቴምበር', 'ኦክተውበር', 'ኖቬምበር',
      'ዲሴምበር'],
  SHORTMONTHS: ['ጃንዩ', 'ፌብሩ', 'ማርች', 'ኤፕረ', 'ሜይ',
      'ጁን', 'ጁላይ', 'ኦገስ', 'ሴፕቴ', 'ኦክተ', 'ኖቬም',
      'ዲሴም'],
  STANDALONESHORTMONTHS: ['ጃንዩ', 'ፌብሩ', 'ማርች', 'ኤፕረ',
      'ሜይ', 'ጁን', 'ጁላይ', 'ኦገስ', 'ሴፕቴ', 'ኦክተ',
      'ኖቬም', 'ዲሴም'],
  WEEKDAYS: ['እሑድ', 'ሰኞ', 'ማክሰኞ', 'ረቡዕ', 'ሐሙስ',
      'ዓርብ', 'ቅዳሜ'],
  STANDALONEWEEKDAYS: ['እሑድ', 'ሰኞ', 'ማክሰኞ', 'ረቡዕ',
      'ሐሙስ', 'ዓርብ', 'ቅዳሜ'],
  SHORTWEEKDAYS: ['እሑድ', 'ሰኞ', 'ማክሰ', 'ረቡዕ', 'ሐሙስ',
      'ዓርብ', 'ቅዳሜ'],
  STANDALONESHORTWEEKDAYS: ['እሑድ', 'ሰኞ', 'ማክሰ', 'ረቡዕ',
      'ሐሙስ', 'ዓርብ', 'ቅዳሜ'],
  NARROWWEEKDAYS: ['እ', 'ሰ', 'ማ', 'ረ', 'ሐ', 'ዓ', 'ቅ'],
  STANDALONENARROWWEEKDAYS: ['እ', 'ሰ', 'ማ', 'ረ', 'ሐ', 'ዓ', 'ቅ'],
  SHORTQUARTERS: ['Q1', 'Q2', 'Q3', 'Q4'],
  QUARTERS: ['1ኛው ሩብ', 'ሁለተኛው ሩብ', '3ኛው ሩብ',
      '4ኛው ሩብ'],
  AMPMS: ['ጡዋት', 'ከሳዓት'],
  DATEFORMATS: ['EEEE, d MMMM y', 'd MMMM y', 'd MMM y', 'dd/MM/yyyy'],
  TIMEFORMATS: ['h:mm:ss a zzzz', 'h:mm:ss a z', 'h:mm:ss a', 'h:mm a'],
  FIRSTDAYOFWEEK: 5,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 1
};


/**
 * Date/time formatting symbols for locale ar.
 */
goog.i18n.DateTimeSymbols_ar = {
  ERAS: ['ق.م', 'م'],
  ERANAMES: ['قبل الميلاد', 'ميلادي'],
  NARROWMONTHS: ['ي', 'ف', 'م', 'أ', 'و', 'ن', 'ل', 'غ', 'س', 'ك',
      'ب', 'د'],
  STANDALONENARROWMONTHS: ['ي', 'ف', 'م', 'أ', 'و', 'ن', 'ل', 'غ', 'س',
      'ك', 'ب', 'د'],
  MONTHS: ['يناير', 'فبراير', 'مارس', 'أبريل', 'مايو',
      'يونيو', 'يوليو', 'أغسطس', 'سبتمبر', 'أكتوبر',
      'نوفمبر', 'ديسمبر'],
  STANDALONEMONTHS: ['يناير', 'فبراير', 'مارس', 'أبريل',
      'مايو', 'يونيو', 'يوليو', 'أغسطس', 'سبتمبر',
      'أكتوبر', 'نوفمبر', 'ديسمبر'],
  SHORTMONTHS: ['يناير', 'فبراير', 'مارس', 'أبريل',
      'مايو', 'يونيو', 'يوليو', 'أغسطس', 'سبتمبر',
      'أكتوبر', 'نوفمبر', 'ديسمبر'],
  STANDALONESHORTMONTHS: ['يناير', 'فبراير', 'مارس',
      'أبريل', 'مايو', 'يونيو', 'يوليو', 'أغسطس',
      'سبتمبر', 'أكتوبر', 'نوفمبر', 'ديسمبر'],
  WEEKDAYS: ['الأحد', 'الاثنين', 'الثلاثاء',
      'الأربعاء', 'الخميس', 'الجمعة', 'السبت'],
  STANDALONEWEEKDAYS: ['الأحد', 'الاثنين', 'الثلاثاء',
      'الأربعاء', 'الخميس', 'الجمعة', 'السبت'],
  SHORTWEEKDAYS: ['الأحد', 'الاثنين', 'الثلاثاء',
      'الأربعاء', 'الخميس', 'الجمعة', 'السبت'],
  STANDALONESHORTWEEKDAYS: ['الأحد', 'الاثنين', 'الثلاثاء',
      'الأربعاء', 'الخميس', 'الجمعة', 'السبت'],
  NARROWWEEKDAYS: ['ح', 'ن', 'ث', 'ر', 'خ', 'ج', 'س'],
  STANDALONENARROWWEEKDAYS: ['ح', 'ن', 'ث', 'ر', 'خ', 'ج', 'س'],
  SHORTQUARTERS: ['الربع الأول', 'الربع الثاني',
      'الربع الثالث', 'الربع الرابع'],
  QUARTERS: ['الربع الأول', 'الربع الثاني',
      'الربع الثالث', 'الربع الرابع'],
  AMPMS: ['ص', 'م'],
  DATEFORMATS: ['EEEE، d MMMM، y', 'd MMMM، y', 'dd‏/MM‏/yyyy',
      'd‏/M‏/yyyy'],
  TIMEFORMATS: ['zzzz h:mm:ss a', 'z h:mm:ss a', 'h:mm:ss a', 'h:mm a'],
  FIRSTDAYOFWEEK: 5,
  WEEKENDRANGE: [4, 5],
  FIRSTWEEKCUTOFFDAY: 1
};


/**
 * Date/time formatting symbols for locale bg.
 */
goog.i18n.DateTimeSymbols_bg = {
  ERAS: ['пр. н. е.', 'от н. е.'],
  ERANAMES: ['пр.Хр.', 'сл.Хр.'],
  NARROWMONTHS: ['я', 'ф', 'м', 'а', 'м', 'ю', 'ю', 'а', 'с', 'о',
      'н', 'д'],
  STANDALONENARROWMONTHS: ['я', 'ф', 'м', 'а', 'м', 'ю', 'ю', 'а', 'с',
      'о', 'н', 'д'],
  MONTHS: ['януари', 'февруари', 'март', 'април',
      'май', 'юни', 'юли', 'август', 'септември',
      'октомври', 'ноември', 'декември'],
  STANDALONEMONTHS: ['януари', 'февруари', 'март',
      'април', 'май', 'юни', 'юли', 'август',
      'септември', 'октомври', 'ноември',
      'декември'],
  SHORTMONTHS: ['ян.', 'февр.', 'март', 'апр.', 'май', 'юни',
      'юли', 'авг.', 'септ.', 'окт.', 'ноем.', 'дек.'],
  STANDALONESHORTMONTHS: ['ян.', 'февр.', 'март', 'апр.', 'май',
      'юни', 'юли', 'авг.', 'септ.', 'окт.', 'ноем.',
      'дек.'],
  WEEKDAYS: ['неделя', 'понеделник', 'вторник',
      'сряда', 'четвъртък', 'петък', 'събота'],
  STANDALONEWEEKDAYS: ['неделя', 'понеделник', 'вторник',
      'сряда', 'четвъртък', 'петък', 'събота'],
  SHORTWEEKDAYS: ['нд', 'пн', 'вт', 'ср', 'чт', 'пт', 'сб'],
  STANDALONESHORTWEEKDAYS: ['нд', 'пн', 'вт', 'ср', 'чт', 'пт',
      'сб'],
  NARROWWEEKDAYS: ['н', 'п', 'в', 'с', 'ч', 'п', 'с'],
  STANDALONENARROWWEEKDAYS: ['н', 'п', 'в', 'с', 'ч', 'п', 'с'],
  SHORTQUARTERS: ['I трим.', 'II трим.', 'III трим.',
      'IV трим.'],
  QUARTERS: ['1-во тримесечие', '2-ро тримесечие',
      '3-то тримесечие', '4-то тримесечие'],
  AMPMS: ['пр. об.', 'сл. об.'],
  DATEFORMATS: ['dd MMMM y, EEEE', 'dd MMMM y', 'dd.MM.yyyy', 'dd.MM.yy'],
  TIMEFORMATS: ['HH:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale bn.
 */
goog.i18n.DateTimeSymbols_bn = {
  ERAS: ['খৃষ্টপূর্ব', 'খৃষ্টাব্দ'],
  ERANAMES: ['খৃষ্টপূর্ব', 'খৃষ্টাব্দ'],
  NARROWMONTHS: ['জা', 'ফে', 'মা', 'এ', 'মে', 'জুন',
      'জু', 'আ', 'সে', 'অ', 'ন', 'ডি'],
  STANDALONENARROWMONTHS: ['জা', 'ফে', 'মা', 'এ', 'মে',
      'জুন', 'জু', 'আ', 'সে', 'অ', 'ন', 'ডি'],
  MONTHS: ['জানুয়ারী', 'ফেব্রুয়ারী',
      'মার্চ', 'এপ্রিল', 'মে', 'জুন',
      'জুলাই', 'আগস্ট', 'সেপ্টেম্বর',
      'অক্টোবর', 'নভেম্বর',
      'ডিসেম্বর'],
  STANDALONEMONTHS: ['জানুয়ারী',
      'ফেব্রুয়ারী', 'মার্চ',
      'এপ্রিল', 'মে', 'জুন', 'জুলাই',
      'আগস্ট', 'সেপ্টেম্বর',
      'অক্টোবর', 'নভেম্বর',
      'ডিসেম্বর'],
  SHORTMONTHS: ['জানুয়ারী',
      'ফেব্রুয়ারী', 'মার্চ',
      'এপ্রিল', 'মে', 'জুন', 'জুলাই',
      'আগস্ট', 'সেপ্টেম্বর',
      'অক্টোবর', 'নভেম্বর',
      'ডিসেম্বর'],
  STANDALONESHORTMONTHS: ['জানুয়ারী',
      'ফেব্রুয়ারী', 'মার্চ',
      'এপ্রিল', 'মে', 'জুন', 'জুলাই',
      'আগস্ট', 'সেপ্টেম্বর',
      'অক্টোবর', 'নভেম্বর',
      'ডিসেম্বর'],
  WEEKDAYS: ['রবিবার', 'সোমবার',
      'মঙ্গলবার', 'বুধবার',
      'বৃহষ্পতিবার', 'শুক্রবার',
      'শনিবার'],
  STANDALONEWEEKDAYS: ['রবিবার', 'সোমবার',
      'মঙ্গলবার', 'বুধবার',
      'বৃহষ্পতিবার', 'শুক্রবার',
      'শনিবার'],
  SHORTWEEKDAYS: ['রবি', 'সোম', 'মঙ্গল', 'বুধ',
      'বৃহস্পতি', 'শুক্র', 'শনি'],
  STANDALONESHORTWEEKDAYS: ['রবি', 'সোম', 'মঙ্গল',
      'বুধ', 'বৃহস্পতি', 'শুক্র', 'শনি'],
  NARROWWEEKDAYS: ['র', 'সো', 'ম', 'বু', 'বৃ', 'শু', 'শ'],
  STANDALONENARROWWEEKDAYS: ['র', 'সো', 'ম', 'বু', 'বৃ',
      'শু', 'শ'],
  SHORTQUARTERS: ['চতুর্থাংশ ১',
      'চতুর্থাংশ ২', 'চতুর্থাংশ ৩',
      'চতুর্থাংশ ৪'],
  QUARTERS: ['প্রথম চতুর্থাংশ',
      'দ্বিতীয় চতুর্থাংশ',
      'তৃতীয় চতুর্থাংশ',
      'চতুর্থ চতুর্থাংশ'],
  AMPMS: ['am', 'pm'],
  DATEFORMATS: ['EEEE, d MMMM, y', 'd MMMM, y', 'd MMM, y', 'd/M/yy'],
  TIMEFORMATS: ['h:mm:ss a zzzz', 'h:mm:ss a z', 'h:mm:ss a', 'h:mm a'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale ca.
 */
goog.i18n.DateTimeSymbols_ca = {
  ERAS: ['aC', 'dC'],
  ERANAMES: ['abans de Crist', 'després de Crist'],
  NARROWMONTHS: ['G', 'F', 'M', 'A', 'M', 'J', 'G', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['g', 'f', 'm', 'a', 'm', 'j', 'j', 'a', 's', 'o',
      'n', 'd'],
  MONTHS: ['de gener', 'de febrer', 'de març', 'd’abril', 'de maig',
      'de juny', 'de juliol', 'd’agost', 'de setembre', 'd’octubre',
      'de novembre', 'de desembre'],
  STANDALONEMONTHS: ['gener', 'febrer', 'març', 'abril', 'maig', 'juny',
      'juliol', 'agost', 'setembre', 'octubre', 'novembre', 'desembre'],
  SHORTMONTHS: ['de gen.', 'de febr.', 'de març', 'd’abr.', 'de maig',
      'de juny', 'de jul.', 'd’ag.', 'de set.', 'd’oct.', 'de nov.',
      'de des.'],
  STANDALONESHORTMONTHS: ['gen.', 'febr.', 'març', 'abr.', 'maig', 'juny',
      'jul.', 'ag.', 'set.', 'oct.', 'nov.', 'des.'],
  WEEKDAYS: ['diumenge', 'dilluns', 'dimarts', 'dimecres', 'dijous',
      'divendres', 'dissabte'],
  STANDALONEWEEKDAYS: ['Diumenge', 'Dilluns', 'Dimarts', 'Dimecres', 'Dijous',
      'Divendres', 'Dissabte'],
  SHORTWEEKDAYS: ['dg.', 'dl.', 'dt.', 'dc.', 'dj.', 'dv.', 'ds.'],
  STANDALONESHORTWEEKDAYS: ['dg', 'dl', 'dt', 'dc', 'dj', 'dv', 'ds'],
  NARROWWEEKDAYS: ['G', 'l', 'T', 'C', 'J', 'V', 'S'],
  STANDALONENARROWWEEKDAYS: ['g', 'l', 't', 'c', 'j', 'v', 's'],
  SHORTQUARTERS: ['1T', '2T', '3T', '4T'],
  QUARTERS: ['1r trimestre', '2n trimestre', '3r trimestre', '4t trimestre'],
  AMPMS: ['a.m.', 'p.m.'],
  DATEFORMATS: ['EEEE d MMMM \'de\' y', 'd MMMM \'de\' y', 'dd/MM/yyyy',
      'dd/MM/yy'],
  TIMEFORMATS: ['H:mm:ss zzzz', 'H:mm:ss z', 'H:mm:ss', 'H:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale cs.
 */
goog.i18n.DateTimeSymbols_cs = {
  ERAS: ['př. n. l.', 'n. l.'],
  ERANAMES: ['př. n. l.', 'n. l.'],
  NARROWMONTHS: ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12'],
  STANDALONENARROWMONTHS: ['l', 'ú', 'b', 'd', 'k', 'č', 'č', 's', 'z', 'ř',
      'l', 'p'],
  MONTHS: ['ledna', 'února', 'března', 'dubna', 'května', 'června',
      'července', 'srpna', 'září', 'října', 'listopadu', 'prosince'],
  STANDALONEMONTHS: ['leden', 'únor', 'březen', 'duben', 'květen', 'červen',
      'červenec', 'srpen', 'září', 'říjen', 'listopad', 'prosinec'],
  SHORTMONTHS: ['Led', 'Úno', 'Bře', 'Dub', 'Kvě', 'Čer', 'Čvc', 'Srp',
      'Zář', 'Říj', 'Lis', 'Pro'],
  STANDALONESHORTMONTHS: ['1.', '2.', '3.', '4.', '5.', '6.', '7.', '8.', '9.',
      '10.', '11.', '12.'],
  WEEKDAYS: ['neděle', 'pondělí', 'úterý', 'středa', 'čtvrtek', 'pátek',
      'sobota'],
  STANDALONEWEEKDAYS: ['neděle', 'pondělí', 'úterý', 'středa', 'čtvrtek',
      'pátek', 'sobota'],
  SHORTWEEKDAYS: ['ne', 'po', 'út', 'st', 'čt', 'pá', 'so'],
  STANDALONESHORTWEEKDAYS: ['ne', 'po', 'út', 'st', 'čt', 'pá', 'so'],
  NARROWWEEKDAYS: ['N', 'P', 'Ú', 'S', 'Č', 'P', 'S'],
  STANDALONENARROWWEEKDAYS: ['N', 'P', 'Ú', 'S', 'Č', 'P', 'S'],
  SHORTQUARTERS: ['Q1', 'Q2', 'Q3', 'Q4'],
  QUARTERS: ['1. čtvrtletí', '2. čtvrtletí', '3. čtvrtletí',
      '4. čtvrtletí'],
  AMPMS: ['dop.', 'odp.'],
  DATEFORMATS: ['EEEE, d. MMMM y', 'd. MMMM y', 'd.M.yyyy', 'dd.MM.yy'],
  TIMEFORMATS: ['H:mm:ss zzzz', 'H:mm:ss z', 'H:mm:ss', 'H:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale da.
 */
goog.i18n.DateTimeSymbols_da = {
  ERAS: ['f.Kr.', 'e.Kr.'],
  ERANAMES: ['f.Kr.', 'e.Kr.'],
  NARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O',
      'N', 'D'],
  MONTHS: ['januar', 'februar', 'marts', 'april', 'maj', 'juni', 'juli',
      'august', 'september', 'oktober', 'november', 'december'],
  STANDALONEMONTHS: ['januar', 'februar', 'marts', 'april', 'maj', 'juni',
      'juli', 'august', 'september', 'oktober', 'november', 'december'],
  SHORTMONTHS: ['jan.', 'feb.', 'mar.', 'apr.', 'maj', 'jun.', 'jul.', 'aug.',
      'sep.', 'okt.', 'nov.', 'dec.'],
  STANDALONESHORTMONTHS: ['jan', 'feb', 'mar', 'apr', 'maj', 'jun', 'jul',
      'aug', 'sep', 'okt', 'nov', 'dec'],
  WEEKDAYS: ['søndag', 'mandag', 'tirsdag', 'onsdag', 'torsdag', 'fredag',
      'lørdag'],
  STANDALONEWEEKDAYS: ['søndag', 'mandag', 'tirsdag', 'onsdag', 'torsdag',
      'fredag', 'lørdag'],
  SHORTWEEKDAYS: ['søn', 'man', 'tir', 'ons', 'tor', 'fre', 'lør'],
  STANDALONESHORTWEEKDAYS: ['søn', 'man', 'tir', 'ons', 'tor', 'fre', 'lør'],
  NARROWWEEKDAYS: ['S', 'M', 'T', 'O', 'T', 'F', 'L'],
  STANDALONENARROWWEEKDAYS: ['S', 'M', 'T', 'O', 'T', 'F', 'L'],
  SHORTQUARTERS: ['K1', 'K2', 'K3', 'K4'],
  QUARTERS: ['1. kvartal', '2. kvartal', '3. kvartal', '4. kvartal'],
  AMPMS: ['f.m.', 'e.m.'],
  DATEFORMATS: ['EEEE \'den\' d. MMMM y', 'd. MMM y', 'dd/MM/yyyy', 'dd/MM/yy'],
  TIMEFORMATS: ['HH.mm.ss zzzz', 'HH.mm.ss z', 'HH.mm.ss', 'HH.mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale de.
 */
goog.i18n.DateTimeSymbols_de = {
  ERAS: ['v. Chr.', 'n. Chr.'],
  ERANAMES: ['v. Chr.', 'n. Chr.'],
  NARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O',
      'N', 'D'],
  MONTHS: ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli',
      'August', 'September', 'Oktober', 'November', 'Dezember'],
  STANDALONEMONTHS: ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni',
      'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'],
  SHORTMONTHS: ['Jan', 'Feb', 'Mär', 'Apr', 'Mai', 'Jun', 'Jul', 'Aug', 'Sep',
      'Okt', 'Nov', 'Dez'],
  STANDALONESHORTMONTHS: ['Jan', 'Feb', 'Mär', 'Apr', 'Mai', 'Jun', 'Jul',
      'Aug', 'Sep', 'Okt', 'Nov', 'Dez'],
  WEEKDAYS: ['Sonntag', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag',
      'Freitag', 'Samstag'],
  STANDALONEWEEKDAYS: ['Sonntag', 'Montag', 'Dienstag', 'Mittwoch',
      'Donnerstag', 'Freitag', 'Samstag'],
  SHORTWEEKDAYS: ['So.', 'Mo.', 'Di.', 'Mi.', 'Do.', 'Fr.', 'Sa.'],
  STANDALONESHORTWEEKDAYS: ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'],
  NARROWWEEKDAYS: ['S', 'M', 'D', 'M', 'D', 'F', 'S'],
  STANDALONENARROWWEEKDAYS: ['S', 'M', 'D', 'M', 'D', 'F', 'S'],
  SHORTQUARTERS: ['Q1', 'Q2', 'Q3', 'Q4'],
  QUARTERS: ['1. Quartal', '2. Quartal', '3. Quartal', '4. Quartal'],
  AMPMS: ['vorm.', 'nachm.'],
  DATEFORMATS: ['EEEE, d. MMMM y', 'd. MMMM y', 'dd.MM.yyyy', 'dd.MM.yy'],
  TIMEFORMATS: ['HH:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale de_AT.
 */
goog.i18n.DateTimeSymbols_de_AT = {
  ERAS: ['v. Chr.', 'n. Chr.'],
  ERANAMES: ['v. Chr.', 'n. Chr.'],
  NARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O',
      'N', 'D'],
  MONTHS: ['Jänner', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli',
      'August', 'September', 'Oktober', 'November', 'Dezember'],
  STANDALONEMONTHS: ['Jänner', 'Februar', 'März', 'April', 'Mai', 'Juni',
      'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'],
  SHORTMONTHS: ['Jän', 'Feb', 'Mär', 'Apr', 'Mai', 'Jun', 'Jul', 'Aug', 'Sep',
      'Okt', 'Nov', 'Dez'],
  STANDALONESHORTMONTHS: ['Jän', 'Feb', 'Mär', 'Apr', 'Mai', 'Jun', 'Jul',
      'Aug', 'Sep', 'Okt', 'Nov', 'Dez'],
  WEEKDAYS: ['Sonntag', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag',
      'Freitag', 'Samstag'],
  STANDALONEWEEKDAYS: ['Sonntag', 'Montag', 'Dienstag', 'Mittwoch',
      'Donnerstag', 'Freitag', 'Samstag'],
  SHORTWEEKDAYS: ['So.', 'Mo.', 'Di.', 'Mi.', 'Do.', 'Fr.', 'Sa.'],
  STANDALONESHORTWEEKDAYS: ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'],
  NARROWWEEKDAYS: ['S', 'M', 'D', 'M', 'D', 'F', 'S'],
  STANDALONENARROWWEEKDAYS: ['S', 'M', 'D', 'M', 'D', 'F', 'S'],
  SHORTQUARTERS: ['Q1', 'Q2', 'Q3', 'Q4'],
  QUARTERS: ['1. Quartal', '2. Quartal', '3. Quartal', '4. Quartal'],
  AMPMS: ['vorm.', 'nachm.'],
  DATEFORMATS: ['EEEE, dd. MMMM y', 'dd. MMMM y', 'dd.MM.yyyy', 'dd.MM.yy'],
  TIMEFORMATS: ['HH:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale de_CH.
 */
goog.i18n.DateTimeSymbols_de_CH = goog.i18n.DateTimeSymbols_de;


/**
 * Date/time formatting symbols for locale el.
 */
goog.i18n.DateTimeSymbols_el = {
  ERAS: ['π.Χ.', 'μ.Χ.'],
  ERANAMES: ['π.Χ.', 'μ.Χ.'],
  NARROWMONTHS: ['Ι', 'Φ', 'Μ', 'Α', 'Μ', 'Ι', 'Ι', 'Α', 'Σ', 'Ο',
      'Ν', 'Δ'],
  STANDALONENARROWMONTHS: ['Ι', 'Φ', 'Μ', 'Α', 'Μ', 'Ι', 'Ι', 'Α', 'Σ',
      'Ο', 'Ν', 'Δ'],
  MONTHS: ['Ιανουαρίου', 'Φεβρουαρίου', 'Μαρτίου',
      'Απριλίου', 'Μαΐου', 'Ιουνίου', 'Ιουλίου',
      'Αυγούστου', 'Σεπτεμβρίου', 'Οκτωβρίου',
      'Νοεμβρίου', 'Δεκεμβρίου'],
  STANDALONEMONTHS: ['Ιανουάριος', 'Φεβρουάριος',
      'Μάρτιος', 'Απρίλιος', 'Μάιος', 'Ιούνιος',
      'Ιούλιος', 'Αύγουστος', 'Σεπτέμβριος',
      'Οκτώβριος', 'Νοέμβριος', 'Δεκέμβριος'],
  SHORTMONTHS: ['Ιαν', 'Φεβ', 'Μαρ', 'Απρ', 'Μαϊ', 'Ιουν',
      'Ιουλ', 'Αυγ', 'Σεπ', 'Οκτ', 'Νοε', 'Δεκ'],
  STANDALONESHORTMONTHS: ['Ιαν', 'Φεβ', 'Μάρ', 'Απρ', 'Μάι',
      'Ιούν', 'Ιούλ', 'Αυγ', 'Σεπ', 'Οκτ', 'Νοέ', 'Δεκ'],
  WEEKDAYS: ['Κυριακή', 'Δευτέρα', 'Τρίτη', 'Τετάρτη',
      'Πέμπτη', 'Παρασκευή', 'Σάββατο'],
  STANDALONEWEEKDAYS: ['Κυριακή', 'Δευτέρα', 'Τρίτη',
      'Τετάρτη', 'Πέμπτη', 'Παρασκευή', 'Σάββατο'],
  SHORTWEEKDAYS: ['Κυρ', 'Δευ', 'Τρι', 'Τετ', 'Πεμ', 'Παρ',
      'Σαβ'],
  STANDALONESHORTWEEKDAYS: ['Κυρ', 'Δευ', 'Τρί', 'Τετ', 'Πέμ',
      'Παρ', 'Σάβ'],
  NARROWWEEKDAYS: ['Κ', 'Δ', 'Τ', 'Τ', 'Π', 'Π', 'Σ'],
  STANDALONENARROWWEEKDAYS: ['Κ', 'Δ', 'Τ', 'Τ', 'Π', 'Π', 'Σ'],
  SHORTQUARTERS: ['Τ1', 'Τ2', 'Τ3', 'Τ4'],
  QUARTERS: ['1ο τρίμηνο', '2ο τρίμηνο', '3ο τρίμηνο',
      '4ο τρίμηνο'],
  AMPMS: ['π.μ.', 'μ.μ.'],
  DATEFORMATS: ['EEEE, d MMMM y', 'd MMMM y', 'd MMM y', 'd/M/yy'],
  TIMEFORMATS: ['h:mm:ss a zzzz', 'h:mm:ss a z', 'h:mm:ss a', 'h:mm a'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale en.
 */
goog.i18n.DateTimeSymbols_en = {
  ERAS: ['BC', 'AD'],
  ERANAMES: ['Before Christ', 'Anno Domini'],
  NARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O',
      'N', 'D'],
  MONTHS: ['January', 'February', 'March', 'April', 'May', 'June', 'July',
      'August', 'September', 'October', 'November', 'December'],
  STANDALONEMONTHS: ['January', 'February', 'March', 'April', 'May', 'June',
      'July', 'August', 'September', 'October', 'November', 'December'],
  SHORTMONTHS: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep',
      'Oct', 'Nov', 'Dec'],
  STANDALONESHORTMONTHS: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul',
      'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
  WEEKDAYS: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday',
      'Saturday'],
  STANDALONEWEEKDAYS: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday',
      'Friday', 'Saturday'],
  SHORTWEEKDAYS: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
  STANDALONESHORTWEEKDAYS: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
  NARROWWEEKDAYS: ['S', 'M', 'T', 'W', 'T', 'F', 'S'],
  STANDALONENARROWWEEKDAYS: ['S', 'M', 'T', 'W', 'T', 'F', 'S'],
  SHORTQUARTERS: ['Q1', 'Q2', 'Q3', 'Q4'],
  QUARTERS: ['1st quarter', '2nd quarter', '3rd quarter', '4th quarter'],
  AMPMS: ['AM', 'PM'],
  DATEFORMATS: ['EEEE, MMMM d, y', 'MMMM d, y', 'MMM d, y', 'M/d/yy'],
  TIMEFORMATS: ['h:mm:ss a zzzz', 'h:mm:ss a z', 'h:mm:ss a', 'h:mm a'],
  FIRSTDAYOFWEEK: 6,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 2
};


/**
 * Date/time formatting symbols for locale en_AU.
 */
goog.i18n.DateTimeSymbols_en_AU = {
  ERAS: ['BC', 'AD'],
  ERANAMES: ['Before Christ', 'Anno Domini'],
  NARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O',
      'N', 'D'],
  MONTHS: ['January', 'February', 'March', 'April', 'May', 'June', 'July',
      'August', 'September', 'October', 'November', 'December'],
  STANDALONEMONTHS: ['January', 'February', 'March', 'April', 'May', 'June',
      'July', 'August', 'September', 'October', 'November', 'December'],
  SHORTMONTHS: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep',
      'Oct', 'Nov', 'Dec'],
  STANDALONESHORTMONTHS: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul',
      'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
  WEEKDAYS: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday',
      'Saturday'],
  STANDALONEWEEKDAYS: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday',
      'Friday', 'Saturday'],
  SHORTWEEKDAYS: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
  STANDALONESHORTWEEKDAYS: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
  NARROWWEEKDAYS: ['S', 'M', 'T', 'W', 'T', 'F', 'S'],
  STANDALONENARROWWEEKDAYS: ['S', 'M', 'T', 'W', 'T', 'F', 'S'],
  SHORTQUARTERS: ['Q1', 'Q2', 'Q3', 'Q4'],
  QUARTERS: ['1st quarter', '2nd quarter', '3rd quarter', '4th quarter'],
  AMPMS: ['AM', 'PM'],
  DATEFORMATS: ['EEEE, d MMMM y', 'd MMMM y', 'dd/MM/yyyy', 'd/MM/yy'],
  TIMEFORMATS: ['h:mm:ss a zzzz', 'h:mm:ss a z', 'h:mm:ss a', 'h:mm a'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale en_GB.
 */
goog.i18n.DateTimeSymbols_en_GB = {
  ERAS: ['BC', 'AD'],
  ERANAMES: ['Before Christ', 'Anno Domini'],
  NARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O',
      'N', 'D'],
  MONTHS: ['January', 'February', 'March', 'April', 'May', 'June', 'July',
      'August', 'September', 'October', 'November', 'December'],
  STANDALONEMONTHS: ['January', 'February', 'March', 'April', 'May', 'June',
      'July', 'August', 'September', 'October', 'November', 'December'],
  SHORTMONTHS: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep',
      'Oct', 'Nov', 'Dec'],
  STANDALONESHORTMONTHS: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul',
      'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
  WEEKDAYS: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday',
      'Saturday'],
  STANDALONEWEEKDAYS: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday',
      'Friday', 'Saturday'],
  SHORTWEEKDAYS: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
  STANDALONESHORTWEEKDAYS: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
  NARROWWEEKDAYS: ['S', 'M', 'T', 'W', 'T', 'F', 'S'],
  STANDALONENARROWWEEKDAYS: ['S', 'M', 'T', 'W', 'T', 'F', 'S'],
  SHORTQUARTERS: ['Q1', 'Q2', 'Q3', 'Q4'],
  QUARTERS: ['1st quarter', '2nd quarter', '3rd quarter', '4th quarter'],
  AMPMS: ['AM', 'PM'],
  DATEFORMATS: ['EEEE, d MMMM y', 'd MMMM y', 'd MMM y', 'dd/MM/yyyy'],
  TIMEFORMATS: ['HH:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale en_IE.
 */
goog.i18n.DateTimeSymbols_en_IE = {
  ERAS: ['BC', 'AD'],
  ERANAMES: ['Before Christ', 'Anno Domini'],
  NARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O',
      'N', 'D'],
  MONTHS: ['January', 'February', 'March', 'April', 'May', 'June', 'July',
      'August', 'September', 'October', 'November', 'December'],
  STANDALONEMONTHS: ['January', 'February', 'March', 'April', 'May', 'June',
      'July', 'August', 'September', 'October', 'November', 'December'],
  SHORTMONTHS: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep',
      'Oct', 'Nov', 'Dec'],
  STANDALONESHORTMONTHS: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul',
      'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
  WEEKDAYS: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday',
      'Saturday'],
  STANDALONEWEEKDAYS: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday',
      'Friday', 'Saturday'],
  SHORTWEEKDAYS: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
  STANDALONESHORTWEEKDAYS: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
  NARROWWEEKDAYS: ['S', 'M', 'T', 'W', 'T', 'F', 'S'],
  STANDALONENARROWWEEKDAYS: ['S', 'M', 'T', 'W', 'T', 'F', 'S'],
  SHORTQUARTERS: ['Q1', 'Q2', 'Q3', 'Q4'],
  QUARTERS: ['1st quarter', '2nd quarter', '3rd quarter', '4th quarter'],
  AMPMS: ['a.m.', 'p.m.'],
  DATEFORMATS: ['EEEE d MMMM y', 'd MMMM y', 'd MMM y', 'dd/MM/yyyy'],
  TIMEFORMATS: ['HH:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale en_IN.
 */
goog.i18n.DateTimeSymbols_en_IN = {
  ERAS: ['BC', 'AD'],
  ERANAMES: ['Before Christ', 'Anno Domini'],
  NARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O',
      'N', 'D'],
  MONTHS: ['January', 'February', 'March', 'April', 'May', 'June', 'July',
      'August', 'September', 'October', 'November', 'December'],
  STANDALONEMONTHS: ['January', 'February', 'March', 'April', 'May', 'June',
      'July', 'August', 'September', 'October', 'November', 'December'],
  SHORTMONTHS: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep',
      'Oct', 'Nov', 'Dec'],
  STANDALONESHORTMONTHS: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul',
      'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
  WEEKDAYS: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday',
      'Saturday'],
  STANDALONEWEEKDAYS: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday',
      'Friday', 'Saturday'],
  SHORTWEEKDAYS: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
  STANDALONESHORTWEEKDAYS: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
  NARROWWEEKDAYS: ['S', 'M', 'T', 'W', 'T', 'F', 'S'],
  STANDALONENARROWWEEKDAYS: ['S', 'M', 'T', 'W', 'T', 'F', 'S'],
  SHORTQUARTERS: ['Q1', 'Q2', 'Q3', 'Q4'],
  QUARTERS: ['1st quarter', '2nd quarter', '3rd quarter', '4th quarter'],
  AMPMS: ['AM', 'PM'],
  DATEFORMATS: ['EEEE d MMMM y', 'd MMMM y', 'dd-MMM-y', 'dd/MM/yy'],
  TIMEFORMATS: ['h:mm:ss a zzzz', 'h:mm:ss a z', 'h:mm:ss a', 'h:mm a'],
  FIRSTDAYOFWEEK: 6,
  WEEKENDRANGE: [6, 6],
  FIRSTWEEKCUTOFFDAY: 2
};


/**
 * Date/time formatting symbols for locale en_SG.
 */
goog.i18n.DateTimeSymbols_en_SG = {
  ERAS: ['BC', 'AD'],
  ERANAMES: ['Before Christ', 'Anno Domini'],
  NARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O',
      'N', 'D'],
  MONTHS: ['January', 'February', 'March', 'April', 'May', 'June', 'July',
      'August', 'September', 'October', 'November', 'December'],
  STANDALONEMONTHS: ['January', 'February', 'March', 'April', 'May', 'June',
      'July', 'August', 'September', 'October', 'November', 'December'],
  SHORTMONTHS: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep',
      'Oct', 'Nov', 'Dec'],
  STANDALONESHORTMONTHS: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul',
      'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
  WEEKDAYS: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday',
      'Saturday'],
  STANDALONEWEEKDAYS: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday',
      'Friday', 'Saturday'],
  SHORTWEEKDAYS: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
  STANDALONESHORTWEEKDAYS: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
  NARROWWEEKDAYS: ['S', 'M', 'T', 'W', 'T', 'F', 'S'],
  STANDALONENARROWWEEKDAYS: ['S', 'M', 'T', 'W', 'T', 'F', 'S'],
  SHORTQUARTERS: ['Q1', 'Q2', 'Q3', 'Q4'],
  QUARTERS: ['1st quarter', '2nd quarter', '3rd quarter', '4th quarter'],
  AMPMS: ['AM', 'PM'],
  DATEFORMATS: ['EEEE, d MMMM, y', 'd MMMM, y', 'd MMM, y', 'd/M/yy'],
  TIMEFORMATS: ['h:mm:ss a zzzz', 'h:mm:ss a z', 'h:mm:ss a', 'h:mm a'],
  FIRSTDAYOFWEEK: 6,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 2
};


/**
 * Date/time formatting symbols for locale en_US.
 */
goog.i18n.DateTimeSymbols_en_US = goog.i18n.DateTimeSymbols_en;


/**
 * Date/time formatting symbols for locale en_ZA.
 */
goog.i18n.DateTimeSymbols_en_ZA = {
  ERAS: ['BC', 'AD'],
  ERANAMES: ['Before Christ', 'Anno Domini'],
  NARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O',
      'N', 'D'],
  MONTHS: ['January', 'February', 'March', 'April', 'May', 'June', 'July',
      'August', 'September', 'October', 'November', 'December'],
  STANDALONEMONTHS: ['January', 'February', 'March', 'April', 'May', 'June',
      'July', 'August', 'September', 'October', 'November', 'December'],
  SHORTMONTHS: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep',
      'Oct', 'Nov', 'Dec'],
  STANDALONESHORTMONTHS: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul',
      'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
  WEEKDAYS: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday',
      'Saturday'],
  STANDALONEWEEKDAYS: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday',
      'Friday', 'Saturday'],
  SHORTWEEKDAYS: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
  STANDALONESHORTWEEKDAYS: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
  NARROWWEEKDAYS: ['S', 'M', 'T', 'W', 'T', 'F', 'S'],
  STANDALONENARROWWEEKDAYS: ['S', 'M', 'T', 'W', 'T', 'F', 'S'],
  SHORTQUARTERS: ['Q1', 'Q2', 'Q3', 'Q4'],
  QUARTERS: ['1st quarter', '2nd quarter', '3rd quarter', '4th quarter'],
  AMPMS: ['AM', 'PM'],
  DATEFORMATS: ['EEEE dd MMMM y', 'dd MMMM y', 'dd MMM y', 'yyyy/MM/dd'],
  TIMEFORMATS: ['h:mm:ss a zzzz', 'h:mm:ss a z', 'h:mm:ss a', 'h:mm a'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale es.
 */
goog.i18n.DateTimeSymbols_es = {
  ERAS: ['a.C.', 'd.C.'],
  ERANAMES: ['antes de Cristo', 'anno Dómini'],
  NARROWMONTHS: ['E', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['E', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O',
      'N', 'D'],
  MONTHS: ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio',
      'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'],
  STANDALONEMONTHS: ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
      'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'],
  SHORTMONTHS: ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep',
      'oct', 'nov', 'dic'],
  STANDALONESHORTMONTHS: ['ene', 'feb', 'mar', 'abr', 'mayo', 'jun', 'jul',
      'ago', 'sep', 'oct', 'nov', 'dic'],
  WEEKDAYS: ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes',
      'sábado'],
  STANDALONEWEEKDAYS: ['domingo', 'lunes', 'martes', 'miércoles', 'jueves',
      'viernes', 'sábado'],
  SHORTWEEKDAYS: ['dom', 'lun', 'mar', 'mié', 'jue', 'vie', 'sáb'],
  STANDALONESHORTWEEKDAYS: ['dom', 'lun', 'mar', 'mié', 'jue', 'vie', 'sáb'],
  NARROWWEEKDAYS: ['D', 'L', 'M', 'M', 'J', 'V', 'S'],
  STANDALONENARROWWEEKDAYS: ['D', 'L', 'M', 'M', 'J', 'V', 'S'],
  SHORTQUARTERS: ['T1', 'T2', 'T3', 'T4'],
  QUARTERS: ['1er trimestre', '2º trimestre', '3er trimestre',
      '4º trimestre'],
  AMPMS: ['a.m.', 'p.m.'],
  DATEFORMATS: ['EEEE d \'de\' MMMM \'de\' y', 'd \'de\' MMMM \'de\' y',
      'dd/MM/yyyy', 'dd/MM/yy'],
  TIMEFORMATS: ['HH:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale es_419.
 */
goog.i18n.DateTimeSymbols_es_419 = goog.i18n.DateTimeSymbols_es;


/**
 * Date/time formatting symbols for locale et.
 */
goog.i18n.DateTimeSymbols_et = {
  ERAS: ['e.m.a.', 'm.a.j.'],
  ERANAMES: ['enne meie aega', 'meie aja järgi'],
  NARROWMONTHS: ['J', 'V', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['J', 'V', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O',
      'N', 'D'],
  MONTHS: ['jaanuar', 'veebruar', 'märts', 'aprill', 'mai', 'juuni', 'juuli',
      'august', 'september', 'oktoober', 'november', 'detsember'],
  STANDALONEMONTHS: ['jaanuar', 'veebruar', 'märts', 'aprill', 'mai', 'juuni',
      'juuli', 'august', 'september', 'oktoober', 'november', 'detsember'],
  SHORTMONTHS: ['jaan', 'veebr', 'märts', 'apr', 'mai', 'juuni', 'juuli',
      'aug', 'sept', 'okt', 'nov', 'dets'],
  STANDALONESHORTMONTHS: ['jaan', 'veebr', 'märts', 'apr', 'mai', 'juuni',
      'juuli', 'aug', 'sept', 'okt', 'nov', 'dets'],
  WEEKDAYS: ['pühapäev', 'esmaspäev', 'teisipäev', 'kolmapäev',
      'neljapäev', 'reede', 'laupäev'],
  STANDALONEWEEKDAYS: ['pühapäev', 'esmaspäev', 'teisipäev', 'kolmapäev',
      'neljapäev', 'reede', 'laupäev'],
  SHORTWEEKDAYS: ['P', 'E', 'T', 'K', 'N', 'R', 'L'],
  STANDALONESHORTWEEKDAYS: ['P', 'E', 'T', 'K', 'N', 'R', 'L'],
  NARROWWEEKDAYS: ['P', 'E', 'T', 'K', 'N', 'R', 'L'],
  STANDALONENARROWWEEKDAYS: ['P', 'E', 'T', 'K', 'N', 'R', 'L'],
  SHORTQUARTERS: ['K1', 'K2', 'K3', 'K4'],
  QUARTERS: ['1. kvartal', '2. kvartal', '3. kvartal', '4. kvartal'],
  AMPMS: ['enne keskpäeva', 'pärast keskpäeva'],
  DATEFORMATS: ['EEEE, d. MMMM y', 'd. MMMM y', 'dd.MM.yyyy', 'dd.MM.yy'],
  TIMEFORMATS: ['H:mm.ss zzzz', 'H:mm.ss z', 'H:mm.ss', 'H:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale eu.
 */
goog.i18n.DateTimeSymbols_eu = {
  ERAS: ['K.a.', 'K.o.'],
  ERANAMES: ['K.a.', 'K.o.'],
  NARROWMONTHS: ['U', 'O', 'M', 'A', 'M', 'E', 'U', 'A', 'I', 'U', 'A', 'A'],
  STANDALONENARROWMONTHS: ['U', 'O', 'M', 'A', 'M', 'E', 'U', 'A', 'I', 'U',
      'A', 'A'],
  MONTHS: ['urtarrila', 'otsaila', 'martxoa', 'apirila', 'maiatza', 'ekaina',
      'uztaila', 'abuztua', 'iraila', 'urria', 'azaroa', 'abendua'],
  STANDALONEMONTHS: ['urtarrila', 'otsaila', 'martxoa', 'apirila', 'maiatza',
      'ekaina', 'uztaila', 'abuztua', 'iraila', 'urria', 'azaroa', 'abendua'],
  SHORTMONTHS: ['urt', 'ots', 'mar', 'api', 'mai', 'eka', 'uzt', 'abu', 'ira',
      'urr', 'aza', 'abe'],
  STANDALONESHORTMONTHS: ['urt', 'ots', 'mar', 'api', 'mai', 'eka', 'uzt',
      'abu', 'ira', 'urr', 'aza', 'abe'],
  WEEKDAYS: ['igandea', 'astelehena', 'asteartea', 'asteazkena', 'osteguna',
      'ostirala', 'larunbata'],
  STANDALONEWEEKDAYS: ['igandea', 'astelehena', 'asteartea', 'asteazkena',
      'osteguna', 'ostirala', 'larunbata'],
  SHORTWEEKDAYS: ['ig', 'al', 'as', 'az', 'og', 'or', 'lr'],
  STANDALONESHORTWEEKDAYS: ['ig', 'al', 'as', 'az', 'og', 'or', 'lr'],
  NARROWWEEKDAYS: ['I', 'M', 'A', 'A', 'A', 'O', 'I'],
  STANDALONENARROWWEEKDAYS: ['I', 'M', 'A', 'L', 'A', 'O', 'I'],
  SHORTQUARTERS: ['1Hh', '2Hh', '3Hh', '4Hh'],
  QUARTERS: ['1. hiruhilekoa', '2. hiruhilekoa', '3. hiruhilekoa',
      '4. hiruhilekoa'],
  AMPMS: ['AM', 'PM'],
  DATEFORMATS: ['EEEE, y\'eko\' MMMM\'ren\' dd\'a\'',
      'y\'eko\' MMM\'ren\' dd\'a\'', 'y MMM d', 'yyyy-MM-dd'],
  TIMEFORMATS: ['HH:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale fa.
 */
goog.i18n.DateTimeSymbols_fa = {
  ERAS: ['ق.م.', 'م.'],
  ERANAMES: ['قبل از میلاد', 'میلادی'],
  NARROWMONTHS: ['ژ', 'ف', 'م', 'آ', 'م', 'ژ', 'ژ', 'ا', 'س', 'ا',
      'ن', 'د'],
  STANDALONENARROWMONTHS: ['ژ', 'ف', 'م', 'آ', 'م', 'ژ', 'ژ', 'ا', 'س',
      'ا', 'ن', 'د'],
  MONTHS: ['ژانویهٔ', 'فوریهٔ', 'مارس', 'آوریل', 'مهٔ',
      'ژوئن', 'ژوئیهٔ', 'اوت', 'سپتامبر', 'اکتبر',
      'نوامبر', 'دسامبر'],
  STANDALONEMONTHS: ['ژانویه', 'فوریه', 'مارس', 'آوریل',
      'مه', 'ژوئن', 'ژوئیه', 'اوت', 'سپتامبر',
      'اکتبر', 'نوامبر', 'دسامبر'],
  SHORTMONTHS: ['ژانویهٔ', 'فوریهٔ', 'مارس', 'آوریل',
      'مهٔ', 'ژوئن', 'ژوئیهٔ', 'اوت', 'سپتامبر',
      'اکتبر', 'نوامبر', 'دسامبر'],
  STANDALONESHORTMONTHS: ['ژانویه', 'فوریه', 'مارس',
      'آوریل', 'مه', 'ژوئن', 'ژوئیه', 'اوت',
      'سپتامبر', 'اکتبر', 'نوامبر', 'دسامبر'],
  WEEKDAYS: ['یکشنبه', 'دوشنبه', 'سه‌شنبه',
      'چهارشنبه', 'پنجشنبه', 'جمعه', 'شنبه'],
  STANDALONEWEEKDAYS: ['یکشنبه', 'دوشنبه', 'سه‌شنبه',
      'چهارشنبه', 'پنجشنبه', 'جمعه', 'شنبه'],
  SHORTWEEKDAYS: ['یکشنبه', 'دوشنبه', 'سه‌شنبه',
      'چهارشنبه', 'پنجشنبه', 'جمعه', 'شنبه'],
  STANDALONESHORTWEEKDAYS: ['یکشنبه', 'دوشنبه', 'سه‌شنبه',
      'چهارشنبه', 'پنجشنبه', 'جمعه', 'شنبه'],
  NARROWWEEKDAYS: ['ی', 'د', 'س', 'چ', 'پ', 'ج', 'ش'],
  STANDALONENARROWWEEKDAYS: ['ی', 'د', 'س', 'چ', 'پ', 'ج', 'ش'],
  SHORTQUARTERS: ['س‌م۱', 'س‌م۲', 'س‌م۳', 'س‌م۴'],
  QUARTERS: ['سه‌ماههٔ اول', 'سه‌ماههٔ دوم',
      'سه‌ماههٔ سوم', 'سه‌ماههٔ چهارم'],
  AMPMS: ['قبل‌ازظهر', 'بعدازظهر'],
  DATEFORMATS: ['EEEE d MMMM y', 'd MMMM y', 'd MMM y', 'yyyy/M/d'],
  TIMEFORMATS: ['H:mm:ss (zzzz)', 'H:mm:ss (z)', 'H:mm:ss', 'H:mm'],
  FIRSTDAYOFWEEK: 5,
  WEEKENDRANGE: [3, 4],
  FIRSTWEEKCUTOFFDAY: 1
};


/**
 * Date/time formatting symbols for locale fi.
 */
goog.i18n.DateTimeSymbols_fi = {
  ERAS: ['eKr.', 'jKr.'],
  ERANAMES: ['ennen Kristuksen syntymää', 'jälkeen Kristuksen syntymän'],
  NARROWMONTHS: ['T', 'H', 'M', 'H', 'T', 'K', 'H', 'E', 'S', 'L', 'M', 'J'],
  STANDALONENARROWMONTHS: ['T', 'H', 'M', 'H', 'T', 'K', 'H', 'E', 'S', 'L',
      'M', 'J'],
  MONTHS: ['tammikuuta', 'helmikuuta', 'maaliskuuta', 'huhtikuuta',
      'toukokuuta', 'kesäkuuta', 'heinäkuuta', 'elokuuta', 'syyskuuta',
      'lokakuuta', 'marraskuuta', 'joulukuuta'],
  STANDALONEMONTHS: ['tammikuu', 'helmikuu', 'maaliskuu', 'huhtikuu',
      'toukokuu', 'kesäkuu', 'heinäkuu', 'elokuu', 'syyskuu', 'lokakuu',
      'marraskuu', 'joulukuu'],
  SHORTMONTHS: ['tammikuuta', 'helmikuuta', 'maaliskuuta', 'huhtikuuta',
      'toukokuuta', 'kesäkuuta', 'heinäkuuta', 'elokuuta', 'syyskuuta',
      'lokakuuta', 'marraskuuta', 'joulukuuta'],
  STANDALONESHORTMONTHS: ['tammi', 'helmi', 'maalis', 'huhti', 'touko', 'kesä',
      'heinä', 'elo', 'syys', 'loka', 'marras', 'joulu'],
  WEEKDAYS: ['sunnuntaina', 'maanantaina', 'tiistaina', 'keskiviikkona',
      'torstaina', 'perjantaina', 'lauantaina'],
  STANDALONEWEEKDAYS: ['sunnuntai', 'maanantai', 'tiistai', 'keskiviikko',
      'torstai', 'perjantai', 'lauantai'],
  SHORTWEEKDAYS: ['su', 'ma', 'ti', 'ke', 'to', 'pe', 'la'],
  STANDALONESHORTWEEKDAYS: ['su', 'ma', 'ti', 'ke', 'to', 'pe', 'la'],
  NARROWWEEKDAYS: ['S', 'M', 'T', 'K', 'T', 'P', 'L'],
  STANDALONENARROWWEEKDAYS: ['S', 'M', 'T', 'K', 'T', 'P', 'L'],
  SHORTQUARTERS: ['1. nelj.', '2. nelj.', '3. nelj.', '4. nelj.'],
  QUARTERS: ['1. neljännes', '2. neljännes', '3. neljännes',
      '4. neljännes'],
  AMPMS: ['ap.', 'ip.'],
  DATEFORMATS: ['cccc, d. MMMM y', 'd. MMMM y', 'd.M.yyyy', 'd.M.yyyy'],
  TIMEFORMATS: ['H.mm.ss zzzz', 'H.mm.ss z', 'H.mm.ss', 'H.mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale fil.
 */
goog.i18n.DateTimeSymbols_fil = {
  ERAS: ['BC', 'AD'],
  ERANAMES: ['BC', 'AD'],
  NARROWMONTHS: ['E', 'P', 'M', 'A', 'M', 'H', 'H', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['E', 'P', 'M', 'A', 'M', 'H', 'H', 'A', 'S', 'O',
      'N', 'D'],
  MONTHS: ['Enero', 'Pebrero', 'Marso', 'Abril', 'Mayo', 'Hunyo', 'Hulyo',
      'Agosto', 'Setyembre', 'Oktubre', 'Nobyembre', 'Disyembre'],
  STANDALONEMONTHS: ['Enero', 'Pebrero', 'Marso', 'Abril', 'Mayo', 'Hunyo',
      'Hulyo', 'Agosto', 'Setyembre', 'Oktubre', 'Nobyembre', 'Disyembre'],
  SHORTMONTHS: ['Ene', 'Peb', 'Mar', 'Abr', 'May', 'Hun', 'Hul', 'Ago', 'Set',
      'Okt', 'Nob', 'Dis'],
  STANDALONESHORTMONTHS: ['Ene', 'Peb', 'Mar', 'Abr', 'May', 'Hun', 'Hul',
      'Ago', 'Set', 'Okt', 'Nob', 'Dis'],
  WEEKDAYS: ['Linggo', 'Lunes', 'Martes', 'Miyerkules', 'Huwebes', 'Biyernes',
      'Sabado'],
  STANDALONEWEEKDAYS: ['Linggo', 'Lunes', 'Martes', 'Miyerkules', 'Huwebes',
      'Biyernes', 'Sabado'],
  SHORTWEEKDAYS: ['Lin', 'Lun', 'Mar', 'Mye', 'Huw', 'Bye', 'Sab'],
  STANDALONESHORTWEEKDAYS: ['Lin', 'Lun', 'Mar', 'Miy', 'Huw', 'Biy', 'Sab'],
  NARROWWEEKDAYS: ['L', 'L', 'M', 'M', 'H', 'B', 'S'],
  STANDALONENARROWWEEKDAYS: ['L', 'L', 'M', 'M', 'H', 'B', 'S'],
  SHORTQUARTERS: ['Q1', 'Q2', 'Q3', 'Q4'],
  QUARTERS: ['ika-1 sangkapat', 'ika-2 sangkapat', 'ika-3 quarter',
      'ika-4 na quarter'],
  AMPMS: ['AM', 'PM'],
  DATEFORMATS: ['EEEE, MMMM dd y', 'MMMM d, y', 'MMM d, y', 'M/d/yy'],
  TIMEFORMATS: ['HH:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 6,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 2
};


/**
 * Date/time formatting symbols for locale fr.
 */
goog.i18n.DateTimeSymbols_fr = {
  ERAS: ['av. J.-C.', 'ap. J.-C.'],
  ERANAMES: ['avant Jésus-Christ', 'après Jésus-Christ'],
  NARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O',
      'N', 'D'],
  MONTHS: ['janvier', 'février', 'mars', 'avril', 'mai', 'juin', 'juillet',
      'août', 'septembre', 'octobre', 'novembre', 'décembre'],
  STANDALONEMONTHS: ['janvier', 'février', 'mars', 'avril', 'mai', 'juin',
      'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre'],
  SHORTMONTHS: ['janv.', 'févr.', 'mars', 'avr.', 'mai', 'juin', 'juil.',
      'août', 'sept.', 'oct.', 'nov.', 'déc.'],
  STANDALONESHORTMONTHS: ['janv.', 'févr.', 'mars', 'avr.', 'mai', 'juin',
      'juil.', 'août', 'sept.', 'oct.', 'nov.', 'déc.'],
  WEEKDAYS: ['dimanche', 'lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi',
      'samedi'],
  STANDALONEWEEKDAYS: ['dimanche', 'lundi', 'mardi', 'mercredi', 'jeudi',
      'vendredi', 'samedi'],
  SHORTWEEKDAYS: ['dim.', 'lun.', 'mar.', 'mer.', 'jeu.', 'ven.', 'sam.'],
  STANDALONESHORTWEEKDAYS: ['dim.', 'lun.', 'mar.', 'mer.', 'jeu.', 'ven.',
      'sam.'],
  NARROWWEEKDAYS: ['D', 'L', 'M', 'M', 'J', 'V', 'S'],
  STANDALONENARROWWEEKDAYS: ['D', 'L', 'M', 'M', 'J', 'V', 'S'],
  SHORTQUARTERS: ['T1', 'T2', 'T3', 'T4'],
  QUARTERS: ['1er trimestre', '2e trimestre', '3e trimestre', '4e trimestre'],
  AMPMS: ['AM', 'PM'],
  DATEFORMATS: ['EEEE d MMMM y', 'd MMMM y', 'd MMM y', 'dd/MM/yy'],
  TIMEFORMATS: ['HH:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale fr_CA.
 */
goog.i18n.DateTimeSymbols_fr_CA = {
  ERAS: ['av. J.-C.', 'ap. J.-C.'],
  ERANAMES: ['avant Jésus-Christ', 'après Jésus-Christ'],
  NARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O',
      'N', 'D'],
  MONTHS: ['janvier', 'février', 'mars', 'avril', 'mai', 'juin', 'juillet',
      'août', 'septembre', 'octobre', 'novembre', 'décembre'],
  STANDALONEMONTHS: ['janvier', 'février', 'mars', 'avril', 'mai', 'juin',
      'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre'],
  SHORTMONTHS: ['janv.', 'févr.', 'mars', 'avr.', 'mai', 'juin', 'juil.',
      'août', 'sept.', 'oct.', 'nov.', 'déc.'],
  STANDALONESHORTMONTHS: ['janv.', 'févr.', 'mars', 'avr.', 'mai', 'juin',
      'juil.', 'août', 'sept.', 'oct.', 'nov.', 'déc.'],
  WEEKDAYS: ['dimanche', 'lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi',
      'samedi'],
  STANDALONEWEEKDAYS: ['dimanche', 'lundi', 'mardi', 'mercredi', 'jeudi',
      'vendredi', 'samedi'],
  SHORTWEEKDAYS: ['dim.', 'lun.', 'mar.', 'mer.', 'jeu.', 'ven.', 'sam.'],
  STANDALONESHORTWEEKDAYS: ['dim.', 'lun.', 'mar.', 'mer.', 'jeu.', 'ven.',
      'sam.'],
  NARROWWEEKDAYS: ['D', 'L', 'M', 'M', 'J', 'V', 'S'],
  STANDALONENARROWWEEKDAYS: ['D', 'L', 'M', 'M', 'J', 'V', 'S'],
  SHORTQUARTERS: ['T1', 'T2', 'T3', 'T4'],
  QUARTERS: ['1er trimestre', '2e trimestre', '3e trimestre', '4e trimestre'],
  AMPMS: ['AM', 'PM'],
  DATEFORMATS: ['EEEE d MMMM y', 'd MMMM y', 'yyyy-MM-dd', 'yy-MM-dd'],
  TIMEFORMATS: ['HH \'h\' mm \'min\' ss \'s\' zzzz', 'HH:mm:ss z', 'HH:mm:ss',
      'HH:mm'],
  FIRSTDAYOFWEEK: 6,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 2
};


/**
 * Date/time formatting symbols for locale gl.
 */
goog.i18n.DateTimeSymbols_gl = {
  ERAS: ['a.C.', 'd.C.'],
  ERANAMES: ['antes de Cristo', 'despois de Cristo'],
  NARROWMONTHS: ['X', 'F', 'M', 'A', 'M', 'X', 'X', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['X', 'F', 'M', 'A', 'M', 'X', 'X', 'A', 'S', 'O',
      'N', 'D'],
  MONTHS: ['Xaneiro', 'Febreiro', 'Marzo', 'Abril', 'Maio', 'Xuño', 'Xullo',
      'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Decembro'],
  STANDALONEMONTHS: ['Xaneiro', 'Febreiro', 'Marzo', 'Abril', 'Maio', 'Xuño',
      'Xullo', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Decembro'],
  SHORTMONTHS: ['Xan', 'Feb', 'Mar', 'Abr', 'Mai', 'Xuñ', 'Xul', 'Ago', 'Set',
      'Out', 'Nov', 'Dec'],
  STANDALONESHORTMONTHS: ['Xan', 'Feb', 'Mar', 'Abr', 'Mai', 'Xuñ', 'Xul',
      'Ago', 'Set', 'Out', 'Nov', 'Dec'],
  WEEKDAYS: ['Domingo', 'Luns', 'Martes', 'Mércores', 'Xoves', 'Venres',
      'Sábado'],
  STANDALONEWEEKDAYS: ['Domingo', 'Luns', 'Martes', 'Mércores', 'Xoves',
      'Venres', 'Sábado'],
  SHORTWEEKDAYS: ['Dom', 'Lun', 'Mar', 'Mér', 'Xov', 'Ven', 'Sáb'],
  STANDALONESHORTWEEKDAYS: ['Dom', 'Lun', 'Mar', 'Mér', 'Xov', 'Ven', 'Sáb'],
  NARROWWEEKDAYS: ['D', 'L', 'M', 'M', 'X', 'V', 'S'],
  STANDALONENARROWWEEKDAYS: ['D', 'L', 'M', 'M', 'X', 'V', 'S'],
  SHORTQUARTERS: ['T1', 'T2', 'T3', 'T4'],
  QUARTERS: ['1o trimestre', '2o trimestre', '3o trimestre', '4o trimestre'],
  AMPMS: ['a.m.', 'p.m.'],
  DATEFORMATS: ['EEEE dd MMMM y', 'dd MMMM y', 'd MMM, y', 'dd/MM/yy'],
  TIMEFORMATS: ['HH:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale gsw.
 */
goog.i18n.DateTimeSymbols_gsw = {
  ERAS: ['v. Chr.', 'n. Chr.'],
  ERANAMES: ['v. Chr.', 'n. Chr.'],
  NARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O',
      'N', 'D'],
  MONTHS: ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli',
      'Auguscht', 'Septämber', 'Oktoober', 'Novämber', 'Dezämber'],
  STANDALONEMONTHS: ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni',
      'Juli', 'Auguscht', 'Septämber', 'Oktoober', 'Novämber', 'Dezämber'],
  SHORTMONTHS: ['Jan', 'Feb', 'Mär', 'Apr', 'Mai', 'Jun', 'Jul', 'Aug', 'Sep',
      'Okt', 'Nov', 'Dez'],
  STANDALONESHORTMONTHS: ['Jan', 'Feb', 'Mär', 'Apr', 'Mai', 'Jun', 'Jul',
      'Aug', 'Sep', 'Okt', 'Nov', 'Dez'],
  WEEKDAYS: ['Sunntig', 'Määntig', 'Ziischtig', 'Mittwuch', 'Dunschtig',
      'Friitig', 'Samschtig'],
  STANDALONEWEEKDAYS: ['Sunntig', 'Määntig', 'Ziischtig', 'Mittwuch',
      'Dunschtig', 'Friitig', 'Samschtig'],
  SHORTWEEKDAYS: ['Su.', 'Mä.', 'Zi.', 'Mi.', 'Du.', 'Fr.', 'Sa.'],
  STANDALONESHORTWEEKDAYS: ['Su.', 'Mä.', 'Zi.', 'Mi.', 'Du.', 'Fr.', 'Sa.'],
  NARROWWEEKDAYS: ['S', 'M', 'D', 'M', 'D', 'F', 'S'],
  STANDALONENARROWWEEKDAYS: ['S', 'M', 'D', 'M', 'D', 'F', 'S'],
  SHORTQUARTERS: ['Q1', 'Q2', 'Q3', 'Q4'],
  QUARTERS: ['1. Quartal', '2. Quartal', '3. Quartal', '4. Quartal'],
  AMPMS: ['vorm.', 'nam.'],
  DATEFORMATS: ['EEEE, d. MMMM y', 'd. MMMM y', 'dd.MM.yyyy', 'dd.MM.yy'],
  TIMEFORMATS: ['HH:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale gu.
 */
goog.i18n.DateTimeSymbols_gu = {
  ERAS: ['ઈલુના જન્મ પહેસાં',
      'ઇસવીસન'],
  ERANAMES: ['ઈસવીસન પૂર્વે', 'ઇસવીસન'],
  NARROWMONTHS: ['જા', 'ફે', 'મા', 'એ', 'મે', 'જૂ',
      'જુ', 'ઑ', 'સ', 'ઑ', 'ન', 'ડિ'],
  STANDALONENARROWMONTHS: ['જા', 'ફે', 'મા', 'એ', 'મે',
      'જૂ', 'જુ', 'ઑ', 'સ', 'ઑ', 'ન', 'ડિ'],
  MONTHS: ['જાન્યુઆરી', 'ફેબ્રુઆરી',
      'માર્ચ', 'એપ્રિલ', 'મે', 'જૂન',
      'જુલાઈ', 'ઑગસ્ટ', 'સપ્ટેમ્બર',
      'ઑક્ટ્બર', 'નવેમ્બર',
      'ડિસેમ્બર'],
  STANDALONEMONTHS: ['જાન્યુઆરી',
      'ફેબ્રુઆરી', 'માર્ચ', 'એપ્રિલ',
      'મે', 'જૂન', 'જુલાઈ', 'ઑગસ્ટ',
      'સપ્ટેમ્બર', 'ઑક્ટ્બર',
      'નવેમ્બર', 'ડિસેમ્બર'],
  SHORTMONTHS: ['જાન્યુ', 'ફેબ્રુ', 'માર્ચ',
      'એપ્રિલ', 'મે', 'જૂન', 'જુલાઈ',
      'ઑગસ્ટ', 'સપ્ટે', 'ઑક્ટો', 'નવે',
      'ડિસે'],
  STANDALONESHORTMONTHS: ['જાન્યુ', 'ફેબ્રુ',
      'માર્ચ', 'એપ્રિલ', 'મે', 'જૂન',
      'જુલાઈ', 'ઑગસ્ટ', 'સપ્ટે',
      'ઑક્ટો', 'નવે', 'ડિસે'],
  WEEKDAYS: ['રવિવાર', 'સોમવાર',
      'મંગળવાર', 'બુધવાર', 'ગુરુવાર',
      'શુક્રવાર', 'શનિવાર'],
  STANDALONEWEEKDAYS: ['રવિવાર', 'સોમવાર',
      'મંગળવાર', 'બુધવાર', 'ગુરુવાર',
      'શુક્રવાર', 'શનિવાર'],
  SHORTWEEKDAYS: ['રવિ', 'સોમ', 'મંગળ', 'બુધ',
      'ગુરુ', 'શુક્ર', 'શનિ'],
  STANDALONESHORTWEEKDAYS: ['રવિ', 'સોમ', 'મંગળ',
      'બુધ', 'ગુરુ', 'શુક્ર', 'શનિ'],
  NARROWWEEKDAYS: ['ર', 'સો', 'મં', 'બુ', 'ગુ', 'શુ',
      'શ'],
  STANDALONENARROWWEEKDAYS: ['ર', 'સો', 'મં', 'બુ', 'ગુ',
      'શુ', 'શ'],
  SHORTQUARTERS: ['પેહલા હંત 1', 'Q2', 'Q3',
      'ચૌતા હંત 4'],
  QUARTERS: ['પેહલા હંત 1', 'ડૂસઋા હંત 2',
      'તીસઋા હંત 3', 'ચૌતા હંત 4'],
  AMPMS: ['પૂર્વ મધ્યાહ્ન',
      'ઉત્તર મધ્યાહ્ન'],
  DATEFORMATS: ['EEEE, d MMMM, y', 'd MMMM, y', 'd MMM, y', 'd-MM-yy'],
  TIMEFORMATS: ['hh:mm:ss a zzzz', 'hh:mm:ss a z', 'hh:mm:ss a', 'hh:mm a'],
  FIRSTDAYOFWEEK: 6,
  WEEKENDRANGE: [6, 6],
  FIRSTWEEKCUTOFFDAY: 2
};


/**
 * Date/time formatting symbols for locale he.
 */
goog.i18n.DateTimeSymbols_he = {
  ERAS: ['לפנה״ס', 'לסה״נ'],
  ERANAMES: ['לפני הספירה', 'לספירה'],
  NARROWMONTHS: ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12'],
  STANDALONENARROWMONTHS: ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10',
      '11', '12'],
  MONTHS: ['ינואר', 'פברואר', 'מרץ', 'אפריל', 'מאי',
      'יוני', 'יולי', 'אוגוסט', 'ספטמבר', 'אוקטובר',
      'נובמבר', 'דצמבר'],
  STANDALONEMONTHS: ['ינואר', 'פברואר', 'מרץ', 'אפריל',
      'מאי', 'יוני', 'יולי', 'אוגוסט', 'ספטמבר',
      'אוקטובר', 'נובמבר', 'דצמבר'],
  SHORTMONTHS: ['ינו', 'פבר', 'מרץ', 'אפר', 'מאי', 'יונ',
      'יול', 'אוג', 'ספט', 'אוק', 'נוב', 'דצמ'],
  STANDALONESHORTMONTHS: ['ינו׳', 'פבר׳', 'מרץ', 'אפר׳',
      'מאי', 'יונ׳', 'יול׳', 'אוג׳', 'ספט׳', 'אוק׳',
      'נוב׳', 'דצמ׳'],
  WEEKDAYS: ['יום ראשון', 'יום שני', 'יום שלישי',
      'יום רביעי', 'יום חמישי', 'יום שישי',
      'יום שבת'],
  STANDALONEWEEKDAYS: ['יום ראשון', 'יום שני',
      'יום שלישי', 'יום רביעי', 'יום חמישי',
      'יום שישי', 'יום שבת'],
  SHORTWEEKDAYS: ['יום א׳', 'יום ב׳', 'יום ג׳', 'יום ד׳',
      'יום ה׳', 'יום ו׳', 'שבת'],
  STANDALONESHORTWEEKDAYS: ['יום א׳', 'יום ב׳', 'יום ג׳',
      'יום ד׳', 'יום ה׳', 'יום ו׳', 'שבת'],
  NARROWWEEKDAYS: ['א', 'ב', 'ג', 'ד', 'ה', 'ו', 'ש'],
  STANDALONENARROWWEEKDAYS: ['א', 'ב', 'ג', 'ד', 'ה', 'ו', 'ש'],
  SHORTQUARTERS: ['רבעון 1', 'רבעון 2', 'רבעון 3',
      'רבעון 4'],
  QUARTERS: ['רבעון 1', 'רבעון 2', 'רבעון 3', 'רבעון 4'],
  AMPMS: ['לפנה״צ', 'אחה״צ'],
  DATEFORMATS: ['EEEE, d בMMMM y', 'd בMMMM y', 'd בMMM yyyy', 'dd/MM/yy'],
  TIMEFORMATS: ['HH:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 6,
  WEEKENDRANGE: [4, 5],
  FIRSTWEEKCUTOFFDAY: 2
};


/**
 * Date/time formatting symbols for locale hi.
 */
goog.i18n.DateTimeSymbols_hi = {
  ERAS: ['ईसापूर्व', 'सन'],
  ERANAMES: ['ईसापूर्व', 'सन'],
  NARROWMONTHS: ['ज', 'फ़', 'मा', 'अ', 'म', 'जू', 'जु',
      'अ', 'सि', 'अ', 'न', 'दि'],
  STANDALONENARROWMONTHS: ['ज', 'फ़', 'मा', 'अ', 'म', 'जू',
      'जु', 'अ', 'सि', 'अ', 'न', 'दि'],
  MONTHS: ['जनवरी', 'फरवरी', 'मार्च',
      'अप्रैल', 'मई', 'जून', 'जुलाई',
      'अगस्त', 'सितम्बर', 'अक्तूबर',
      'नवम्बर', 'दिसम्बर'],
  STANDALONEMONTHS: ['जनवरी', 'फरवरी', 'मार्च',
      'अप्रैल', 'मई', 'जून', 'जुलाई',
      'अगस्त', 'सितम्बर', 'अक्तूबर',
      'नवम्बर', 'दिसम्बर'],
  SHORTMONTHS: ['जनवरी', 'फरवरी', 'मार्च',
      'अप्रैल', 'मई', 'जून', 'जुलाई',
      'अगस्त', 'सितम्बर', 'अक्तूबर',
      'नवम्बर', 'दिसम्बर'],
  STANDALONESHORTMONTHS: ['जनवरी', 'फरवरी',
      'मार्च', 'अप्रैल', 'मई', 'जून',
      'जुलाई', 'अगस्त', 'सितम्बर',
      'अक्तूबर', 'नवम्बर', 'दिसम्बर'],
  WEEKDAYS: ['रविवार', 'सोमवार',
      'मंगलवार', 'बुधवार',
      'बृहस्पतिवार', 'शुक्रवार',
      'शनिवार'],
  STANDALONEWEEKDAYS: ['रविवार', 'सोमवार',
      'मंगलवार', 'बुधवार',
      'बृहस्पतिवार', 'शुक्रवार',
      'शनिवार'],
  SHORTWEEKDAYS: ['रवि.', 'सोम.', 'मंगल.', 'बुध.',
      'बृह.', 'शुक्र.', 'शनि.'],
  STANDALONESHORTWEEKDAYS: ['रवि.', 'सोम.', 'मंगल.',
      'बुध.', 'बृह.', 'शुक्र.', 'शनि.'],
  NARROWWEEKDAYS: ['र', 'सो', 'मं', 'बु', 'गु', 'शु',
      'श'],
  STANDALONENARROWWEEKDAYS: ['र', 'सो', 'मं', 'बु', 'गु',
      'शु', 'श'],
  SHORTQUARTERS: ['तिमाही', 'दूसरी तिमाही',
      'तीसरी तिमाही', 'चौथी तिमाही'],
  QUARTERS: ['तिमाही', 'दूसरी तिमाही',
      'तीसरी तिमाही', 'चौथी तिमाही'],
  AMPMS: ['पूर्वाह्न', 'अपराह्न'],
  DATEFORMATS: ['EEEE, d MMMM y', 'd MMMM y', 'dd-MM-yyyy', 'd-M-yy'],
  TIMEFORMATS: ['h:mm:ss a zzzz', 'h:mm:ss a z', 'h:mm:ss a', 'h:mm a'],
  FIRSTDAYOFWEEK: 6,
  WEEKENDRANGE: [6, 6],
  FIRSTWEEKCUTOFFDAY: 2
};


/**
 * Date/time formatting symbols for locale hr.
 */
goog.i18n.DateTimeSymbols_hr = {
  ERAS: ['p. n. e.', 'A. D.'],
  ERANAMES: ['Prije Krista', 'Poslije Krista'],
  NARROWMONTHS: ['1.', '2.', '3.', '4.', '5.', '6.', '7.', '8.', '9.', '10.',
      '11.', '12.'],
  STANDALONENARROWMONTHS: ['1.', '2.', '3.', '4.', '5.', '6.', '7.', '8.', '9.',
      '10.', '11.', '12.'],
  MONTHS: ['siječnja', 'veljače', 'ožujka', 'travnja', 'svibnja', 'lipnja',
      'srpnja', 'kolovoza', 'rujna', 'listopada', 'studenoga', 'prosinca'],
  STANDALONEMONTHS: ['siječanj', 'veljača', 'ožujak', 'travanj', 'svibanj',
      'lipanj', 'srpanj', 'kolovoz', 'rujan', 'listopad', 'studeni',
      'prosinac'],
  SHORTMONTHS: ['sij', 'velj', 'ožu', 'tra', 'svi', 'lip', 'srp', 'kol', 'ruj',
      'lis', 'stu', 'pro'],
  STANDALONESHORTMONTHS: ['sij', 'velj', 'ožu', 'tra', 'svi', 'lip', 'srp',
      'kol', 'ruj', 'lis', 'stu', 'pro'],
  WEEKDAYS: ['nedjelja', 'ponedjeljak', 'utorak', 'srijeda', 'četvrtak',
      'petak', 'subota'],
  STANDALONEWEEKDAYS: ['nedjelja', 'ponedjeljak', 'utorak', 'srijeda',
      'četvrtak', 'petak', 'subota'],
  SHORTWEEKDAYS: ['ned', 'pon', 'uto', 'sri', 'čet', 'pet', 'sub'],
  STANDALONESHORTWEEKDAYS: ['ned', 'pon', 'uto', 'sri', 'čet', 'pet', 'sub'],
  NARROWWEEKDAYS: ['N', 'P', 'U', 'S', 'Č', 'P', 'S'],
  STANDALONENARROWWEEKDAYS: ['n', 'p', 'u', 's', 'č', 'p', 's'],
  SHORTQUARTERS: ['1kv', '2kv', '3kv', '4kv'],
  QUARTERS: ['1. kvartal', '2. kvartal', '3. kvartal', '4. kvartal'],
  AMPMS: ['prije podne', 'PM'],
  DATEFORMATS: ['EEEE, d. MMMM y.', 'd. MMMM y.', 'd. M. y.', 'd.M.y.'],
  TIMEFORMATS: ['HH:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale hu.
 */
goog.i18n.DateTimeSymbols_hu = {
  ERAS: ['i. e.', 'i. sz.'],
  ERANAMES: ['időszámításunk előtt', 'időszámításunk szerint'],
  NARROWMONTHS: ['J', 'F', 'M', 'Á', 'M', 'J', 'J', 'Á', 'Sz', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['J', 'F', 'M', 'Á', 'M', 'J', 'J', 'A', 'Sz', 'O',
      'N', 'D'],
  MONTHS: ['január', 'február', 'március', 'április', 'május', 'június',
      'július', 'augusztus', 'szeptember', 'október', 'november', 'december'],
  STANDALONEMONTHS: ['január', 'február', 'március', 'április', 'május',
      'június', 'július', 'augusztus', 'szeptember', 'október', 'november',
      'december'],
  SHORTMONTHS: ['jan.', 'febr.', 'márc.', 'ápr.', 'máj.', 'jún.', 'júl.',
      'aug.', 'szept.', 'okt.', 'nov.', 'dec.'],
  STANDALONESHORTMONTHS: ['jan.', 'febr.', 'márc.', 'ápr.', 'máj.', 'jún.',
      'júl.', 'aug.', 'szept.', 'okt.', 'nov.', 'dec.'],
  WEEKDAYS: ['vasárnap', 'hétfő', 'kedd', 'szerda', 'csütörtök',
      'péntek', 'szombat'],
  STANDALONEWEEKDAYS: ['vasárnap', 'hétfő', 'kedd', 'szerda', 'csütörtök',
      'péntek', 'szombat'],
  SHORTWEEKDAYS: ['V', 'H', 'K', 'Sze', 'Cs', 'P', 'Szo'],
  STANDALONESHORTWEEKDAYS: ['V', 'H', 'K', 'Sze', 'Cs', 'P', 'Szo'],
  NARROWWEEKDAYS: ['V', 'H', 'K', 'Sz', 'Cs', 'P', 'Sz'],
  STANDALONENARROWWEEKDAYS: ['V', 'H', 'K', 'Sz', 'Cs', 'P', 'Sz'],
  SHORTQUARTERS: ['N1', 'N2', 'N3', 'N4'],
  QUARTERS: ['I. negyedév', 'II. negyedév', 'III. negyedév',
      'IV. negyedév'],
  AMPMS: ['de.', 'du.'],
  DATEFORMATS: ['y. MMMM d., EEEE', 'y. MMMM d.', 'yyyy.MM.dd.', 'yyyy.MM.dd.'],
  TIMEFORMATS: ['H:mm:ss zzzz', 'H:mm:ss z', 'H:mm:ss', 'H:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale id.
 */
goog.i18n.DateTimeSymbols_id = {
  ERAS: ['SM', 'M'],
  ERANAMES: ['SM', 'M'],
  NARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O',
      'N', 'D'],
  MONTHS: ['Januari', 'Februari', 'Maret', 'April', 'Mei', 'Juni', 'Juli',
      'Agustus', 'September', 'Oktober', 'November', 'Desember'],
  STANDALONEMONTHS: ['Januari', 'Februari', 'Maret', 'April', 'Mei', 'Juni',
      'Juli', 'Agustus', 'September', 'Oktober', 'November', 'Desember'],
  SHORTMONTHS: ['Jan', 'Feb', 'Mar', 'Apr', 'Mei', 'Jun', 'Jul', 'Agt', 'Sep',
      'Okt', 'Nov', 'Des'],
  STANDALONESHORTMONTHS: ['Jan', 'Feb', 'Mar', 'Apr', 'Mei', 'Jun', 'Jul',
      'Agt', 'Sep', 'Okt', 'Nov', 'Des'],
  WEEKDAYS: ['Minggu', 'Senin', 'Selasa', 'Rabu', 'Kamis', 'Jumat', 'Sabtu'],
  STANDALONEWEEKDAYS: ['Minggu', 'Senin', 'Selasa', 'Rabu', 'Kamis', 'Jumat',
      'Sabtu'],
  SHORTWEEKDAYS: ['Min', 'Sen', 'Sel', 'Rab', 'Kam', 'Jum', 'Sab'],
  STANDALONESHORTWEEKDAYS: ['Min', 'Sen', 'Sel', 'Rab', 'Kam', 'Jum', 'Sab'],
  NARROWWEEKDAYS: ['M', 'S', 'S', 'R', 'K', 'J', 'S'],
  STANDALONENARROWWEEKDAYS: ['M', 'S', 'S', 'R', 'K', 'J', 'S'],
  SHORTQUARTERS: ['K1', 'K2', 'K3', 'K4'],
  QUARTERS: ['kuartal pertama', 'kuartal kedua', 'kuartal ketiga',
      'kuartal keempat'],
  AMPMS: ['pagi', 'malam'],
  DATEFORMATS: ['EEEE, dd MMMM yyyy', 'd MMMM yyyy', 'd MMM yyyy', 'dd/MM/yy'],
  TIMEFORMATS: ['H:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale in.
 */
goog.i18n.DateTimeSymbols_in = {
  ERAS: ['SM', 'M'],
  ERANAMES: ['SM', 'M'],
  NARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O',
      'N', 'D'],
  MONTHS: ['Januari', 'Februari', 'Maret', 'April', 'Mei', 'Juni', 'Juli',
      'Agustus', 'September', 'Oktober', 'November', 'Desember'],
  STANDALONEMONTHS: ['Januari', 'Februari', 'Maret', 'April', 'Mei', 'Juni',
      'Juli', 'Agustus', 'September', 'Oktober', 'November', 'Desember'],
  SHORTMONTHS: ['Jan', 'Feb', 'Mar', 'Apr', 'Mei', 'Jun', 'Jul', 'Agt', 'Sep',
      'Okt', 'Nov', 'Des'],
  STANDALONESHORTMONTHS: ['Jan', 'Feb', 'Mar', 'Apr', 'Mei', 'Jun', 'Jul',
      'Agt', 'Sep', 'Okt', 'Nov', 'Des'],
  WEEKDAYS: ['Minggu', 'Senin', 'Selasa', 'Rabu', 'Kamis', 'Jumat', 'Sabtu'],
  STANDALONEWEEKDAYS: ['Minggu', 'Senin', 'Selasa', 'Rabu', 'Kamis', 'Jumat',
      'Sabtu'],
  SHORTWEEKDAYS: ['Min', 'Sen', 'Sel', 'Rab', 'Kam', 'Jum', 'Sab'],
  STANDALONESHORTWEEKDAYS: ['Min', 'Sen', 'Sel', 'Rab', 'Kam', 'Jum', 'Sab'],
  NARROWWEEKDAYS: ['M', 'S', 'S', 'R', 'K', 'J', 'S'],
  STANDALONENARROWWEEKDAYS: ['M', 'S', 'S', 'R', 'K', 'J', 'S'],
  SHORTQUARTERS: ['K1', 'K2', 'K3', 'K4'],
  QUARTERS: ['kuartal pertama', 'kuartal kedua', 'kuartal ketiga',
      'kuartal keempat'],
  AMPMS: ['pagi', 'malam'],
  DATEFORMATS: ['EEEE, dd MMMM yyyy', 'd MMMM yyyy', 'd MMM yyyy', 'dd/MM/yy'],
  TIMEFORMATS: ['H:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale is.
 */
goog.i18n.DateTimeSymbols_is = {
  ERAS: ['fyrir Krist', 'eftir Krist'],
  ERANAMES: ['fyrir Krist', 'eftir Krist'],
  NARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'Á', 'L', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['j', 'f', 'm', 'a', 'm', 'j', 'j', 'á', 's', 'o',
      'n', 'd'],
  MONTHS: ['janúar', 'febrúar', 'mars', 'apríl', 'maí', 'júní', 'júlí',
      'ágúst', 'september', 'október', 'nóvember', 'desember'],
  STANDALONEMONTHS: ['janúar', 'febrúar', 'mars', 'apríl', 'maí', 'júní',
      'júlí', 'ágúst', 'september', 'október', 'nóvember', 'desember'],
  SHORTMONTHS: ['jan', 'feb', 'mar', 'apr', 'maí', 'jún', 'júl', 'ágú',
      'sep', 'okt', 'nóv', 'des'],
  STANDALONESHORTMONTHS: ['jan', 'feb', 'mar', 'apr', 'maí', 'jún', 'júl',
      'ágú', 'sep', 'okt', 'nóv', 'des'],
  WEEKDAYS: ['sunnudagur', 'mánudagur', 'þriðjudagur', 'miðvikudagur',
      'fimmtudagur', 'föstudagur', 'laugardagur'],
  STANDALONEWEEKDAYS: ['sunnudagur', 'mánudagur', 'þriðjudagur',
      'miðvikudagur', 'fimmtudagur', 'föstudagur', 'laugardagur'],
  SHORTWEEKDAYS: ['sun', 'mán', 'þri', 'mið', 'fim', 'fös', 'lau'],
  STANDALONESHORTWEEKDAYS: ['sun', 'mán', 'þri', 'mið', 'fim', 'fös',
      'lau'],
  NARROWWEEKDAYS: ['S', 'M', 'Þ', 'M', 'F', 'F', 'L'],
  STANDALONENARROWWEEKDAYS: ['s', 'm', 'þ', 'm', 'f', 'f', 'l'],
  SHORTQUARTERS: ['F1', 'F2', 'F3', 'F4'],
  QUARTERS: ['1st fjórðungur', '2nd fjórðungur', '3rd fjórðungur',
      '4th fjórðungur'],
  AMPMS: ['f.h.', 'e.h.'],
  DATEFORMATS: ['EEEE, d. MMMM y', 'd. MMMM y', 'd.M.yyyy', 'd.M.yyyy'],
  TIMEFORMATS: ['HH:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale it.
 */
goog.i18n.DateTimeSymbols_it = {
  ERAS: ['aC', 'dC'],
  ERANAMES: ['a.C.', 'd.C'],
  NARROWMONTHS: ['G', 'F', 'M', 'A', 'M', 'G', 'L', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['G', 'F', 'M', 'A', 'M', 'G', 'L', 'A', 'S', 'O',
      'N', 'D'],
  MONTHS: ['gennaio', 'febbraio', 'marzo', 'aprile', 'maggio', 'giugno',
      'luglio', 'agosto', 'settembre', 'ottobre', 'novembre', 'dicembre'],
  STANDALONEMONTHS: ['Gennaio', 'Febbraio', 'Marzo', 'Aprile', 'Maggio',
      'Giugno', 'Luglio', 'Agosto', 'Settembre', 'Ottobre', 'Novembre',
      'Dicembre'],
  SHORTMONTHS: ['gen', 'feb', 'mar', 'apr', 'mag', 'giu', 'lug', 'ago', 'set',
      'ott', 'nov', 'dic'],
  STANDALONESHORTMONTHS: ['gen', 'feb', 'mar', 'apr', 'mag', 'giu', 'lug',
      'ago', 'set', 'ott', 'nov', 'dic'],
  WEEKDAYS: ['domenica', 'lunedì', 'martedì', 'mercoledì', 'giovedì',
      'venerdì', 'sabato'],
  STANDALONEWEEKDAYS: ['Domenica', 'Lunedì', 'Martedì', 'Mercoledì',
      'Giovedì', 'Venerdì', 'Sabato'],
  SHORTWEEKDAYS: ['dom', 'lun', 'mar', 'mer', 'gio', 'ven', 'sab'],
  STANDALONESHORTWEEKDAYS: ['dom', 'lun', 'mar', 'mer', 'gio', 'ven', 'sab'],
  NARROWWEEKDAYS: ['D', 'L', 'M', 'M', 'G', 'V', 'S'],
  STANDALONENARROWWEEKDAYS: ['D', 'L', 'M', 'M', 'G', 'V', 'S'],
  SHORTQUARTERS: ['T1', 'T2', 'T3', 'T4'],
  QUARTERS: ['1o trimestre', '2o trimestre', '3o trimestre', '4o trimestre'],
  AMPMS: ['m.', 'p.'],
  DATEFORMATS: ['EEEE d MMMM y', 'dd MMMM y', 'dd/MMM/y', 'dd/MM/yy'],
  TIMEFORMATS: ['HH:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale iw.
 */
goog.i18n.DateTimeSymbols_iw = {
  ERAS: ['לפנה״ס', 'לסה״נ'],
  ERANAMES: ['לפני הספירה', 'לספירה'],
  NARROWMONTHS: ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12'],
  STANDALONENARROWMONTHS: ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10',
      '11', '12'],
  MONTHS: ['ינואר', 'פברואר', 'מרץ', 'אפריל', 'מאי',
      'יוני', 'יולי', 'אוגוסט', 'ספטמבר', 'אוקטובר',
      'נובמבר', 'דצמבר'],
  STANDALONEMONTHS: ['ינואר', 'פברואר', 'מרץ', 'אפריל',
      'מאי', 'יוני', 'יולי', 'אוגוסט', 'ספטמבר',
      'אוקטובר', 'נובמבר', 'דצמבר'],
  SHORTMONTHS: ['ינו', 'פבר', 'מרץ', 'אפר', 'מאי', 'יונ',
      'יול', 'אוג', 'ספט', 'אוק', 'נוב', 'דצמ'],
  STANDALONESHORTMONTHS: ['ינו׳', 'פבר׳', 'מרץ', 'אפר׳',
      'מאי', 'יונ׳', 'יול׳', 'אוג׳', 'ספט׳', 'אוק׳',
      'נוב׳', 'דצמ׳'],
  WEEKDAYS: ['יום ראשון', 'יום שני', 'יום שלישי',
      'יום רביעי', 'יום חמישי', 'יום שישי',
      'יום שבת'],
  STANDALONEWEEKDAYS: ['יום ראשון', 'יום שני',
      'יום שלישי', 'יום רביעי', 'יום חמישי',
      'יום שישי', 'יום שבת'],
  SHORTWEEKDAYS: ['יום א׳', 'יום ב׳', 'יום ג׳', 'יום ד׳',
      'יום ה׳', 'יום ו׳', 'שבת'],
  STANDALONESHORTWEEKDAYS: ['יום א׳', 'יום ב׳', 'יום ג׳',
      'יום ד׳', 'יום ה׳', 'יום ו׳', 'שבת'],
  NARROWWEEKDAYS: ['א', 'ב', 'ג', 'ד', 'ה', 'ו', 'ש'],
  STANDALONENARROWWEEKDAYS: ['א', 'ב', 'ג', 'ד', 'ה', 'ו', 'ש'],
  SHORTQUARTERS: ['רבעון 1', 'רבעון 2', 'רבעון 3',
      'רבעון 4'],
  QUARTERS: ['רבעון 1', 'רבעון 2', 'רבעון 3', 'רבעון 4'],
  AMPMS: ['לפנה״צ', 'אחה״צ'],
  DATEFORMATS: ['EEEE, d בMMMM y', 'd בMMMM y', 'd בMMM yyyy', 'dd/MM/yy'],
  TIMEFORMATS: ['HH:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale ja.
 */
goog.i18n.DateTimeSymbols_ja = {
  ERAS: ['紀元前', '西暦'],
  ERANAMES: ['紀元前', '西暦'],
  NARROWMONTHS: ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12'],
  STANDALONENARROWMONTHS: ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10',
      '11', '12'],
  MONTHS: ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月',
      '9月', '10月', '11月', '12月'],
  STANDALONEMONTHS: ['1月', '2月', '3月', '4月', '5月', '6月', '7月',
      '8月', '9月', '10月', '11月', '12月'],
  SHORTMONTHS: ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月',
      '9月', '10月', '11月', '12月'],
  STANDALONESHORTMONTHS: ['1月', '2月', '3月', '4月', '5月', '6月',
      '7月', '8月', '9月', '10月', '11月', '12月'],
  WEEKDAYS: ['日曜日', '月曜日', '火曜日', '水曜日', '木曜日',
      '金曜日', '土曜日'],
  STANDALONEWEEKDAYS: ['日曜日', '月曜日', '火曜日', '水曜日',
      '木曜日', '金曜日', '土曜日'],
  SHORTWEEKDAYS: ['日', '月', '火', '水', '木', '金', '土'],
  STANDALONESHORTWEEKDAYS: ['日', '月', '火', '水', '木', '金', '土'],
  NARROWWEEKDAYS: ['日', '月', '火', '水', '木', '金', '土'],
  STANDALONENARROWWEEKDAYS: ['日', '月', '火', '水', '木', '金', '土'],
  SHORTQUARTERS: ['Q1', 'Q2', 'Q3', 'Q4'],
  QUARTERS: ['第1四半期', '第2四半期', '第3四半期',
      '第4四半期'],
  AMPMS: ['午前', '午後'],
  DATEFORMATS: ['y年M月d日EEEE', 'y年M月d日', 'yyyy/MM/dd', 'yy/MM/dd'],
  TIMEFORMATS: ['H時mm分ss秒 zzzz', 'H:mm:ss z', 'H:mm:ss', 'H:mm'],
  FIRSTDAYOFWEEK: 6,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 2
};


/**
 * Date/time formatting symbols for locale kn.
 */
goog.i18n.DateTimeSymbols_kn = {
  ERAS: ['ಕ್ರಿ.ಪೂ', 'ಜಾಹೀ'],
  ERANAMES: ['ಈಸಪೂವ೯.', 'ಕ್ರಿಸ್ತ ಶಕ'],
  NARROWMONTHS: ['ಜ', 'ಫೆ', 'ಮಾ', 'ಎ', 'ಮೇ', 'ಜೂ', 'ಜು',
      'ಆ', 'ಸೆ', 'ಅ', 'ನ', 'ಡಿ'],
  STANDALONENARROWMONTHS: ['ಜ', 'ಫೆ', 'ಮಾ', 'ಎ', 'ಮೇ', 'ಜೂ',
      'ಜು', 'ಆ', 'ಸೆ', 'ಅ', 'ನ', 'ಡಿ'],
  MONTHS: ['ಜನವರೀ', 'ಫೆಬ್ರವರೀ', 'ಮಾರ್ಚ್',
      'ಎಪ್ರಿಲ್', 'ಮೆ', 'ಜೂನ್', 'ಜುಲೈ',
      'ಆಗಸ್ಟ್', 'ಸಪ್ಟೆಂಬರ್',
      'ಅಕ್ಟೋಬರ್', 'ನವೆಂಬರ್',
      'ಡಿಸೆಂಬರ್'],
  STANDALONEMONTHS: ['ಜನವರೀ', 'ಫೆಬ್ರವರೀ',
      'ಮಾರ್ಚ್', 'ಎಪ್ರಿಲ್', 'ಮೆ', 'ಜೂನ್',
      'ಜುಲೈ', 'ಆಗಸ್ಟ್', 'ಸಪ್ಟೆಂಬರ್',
      'ಅಕ್ಟೋಬರ್', 'ನವೆಂಬರ್',
      'ಡಿಸೆಂಬರ್'],
  SHORTMONTHS: ['ಜನವರೀ', 'ಫೆಬ್ರವರೀ',
      'ಮಾರ್ಚ್', 'ಎಪ್ರಿಲ್', 'ಮೆ', 'ಜೂನ್',
      'ಜುಲೈ', 'ಆಗಸ್ಟ್', 'ಸಪ್ಟೆಂಬರ್',
      'ಅಕ್ಟೋಬರ್', 'ನವೆಂಬರ್',
      'ಡಿಸೆಂಬರ್'],
  STANDALONESHORTMONTHS: ['ಜನವರೀ', 'ಫೆಬ್ರವರೀ',
      'ಮಾರ್ಚ್', 'ಎಪ್ರಿಲ್', 'ಮೆ', 'ಜೂನ್',
      'ಜುಲೈ', 'ಆಗಸ್ಟ್', 'ಸಪ್ಟೆಂಬರ್',
      'ಅಕ್ಟೋಬರ್', 'ನವೆಂಬರ್',
      'ಡಿಸೆಂಬರ್'],
  WEEKDAYS: ['ರವಿವಾರ', 'ಸೋಮವಾರ',
      'ಮಂಗಳವಾರ', 'ಬುಧವಾರ', 'ಗುರುವಾರ',
      'ಶುಕ್ರವಾರ', 'ಶನಿವಾರ'],
  STANDALONEWEEKDAYS: ['ರವಿವಾರ', 'ಸೋಮವಾರ',
      'ಮಂಗಳವಾರ', 'ಬುಧವಾರ', 'ಗುರುವಾರ',
      'ಶುಕ್ರವಾರ', 'ಶನಿವಾರ'],
  SHORTWEEKDAYS: ['ರ.', 'ಸೋ.', 'ಮಂ.', 'ಬು.', 'ಗು.', 'ಶು.',
      'ಶನಿ.'],
  STANDALONESHORTWEEKDAYS: ['ರ.', 'ಸೋ.', 'ಮಂ.', 'ಬು.', 'ಗು.',
      'ಶು.', 'ಶನಿ.'],
  NARROWWEEKDAYS: ['ರ', 'ಸೋ', 'ಮಂ', 'ಬು', 'ಗು', 'ಶು',
      'ಶ'],
  STANDALONENARROWWEEKDAYS: ['ರ', 'ಸೋ', 'ಮಂ', 'ಬು', 'ಗು',
      'ಶು', 'ಶ'],
  SHORTQUARTERS: ['ಒಂದು 1', 'ಎರಡು 2', 'ಮೂರು 3',
      'ನಾಲೃಕ 4'],
  QUARTERS: ['ಒಂದು 1', 'ಎರಡು 2', 'ಮೂರು 3',
      'ನಾಲೃಕ 4'],
  AMPMS: ['AM', 'PM'],
  DATEFORMATS: ['EEEE d MMMM y', 'd MMMM y', 'd MMM y', 'd-M-yy'],
  TIMEFORMATS: ['hh:mm:ss a zzzz', 'hh:mm:ss a z', 'hh:mm:ss a', 'hh:mm a'],
  FIRSTDAYOFWEEK: 6,
  WEEKENDRANGE: [6, 6],
  FIRSTWEEKCUTOFFDAY: 2
};


/**
 * Date/time formatting symbols for locale ko.
 */
goog.i18n.DateTimeSymbols_ko = {
  ERAS: ['기원전', '서기'],
  ERANAMES: ['서력기원전', '서력기원'],
  NARROWMONTHS: ['1월', '2월', '3월', '4월', '5월', '6월', '7월', '8월',
      '9월', '10월', '11월', '12월'],
  STANDALONENARROWMONTHS: ['1월', '2월', '3월', '4월', '5월', '6월',
      '7월', '8월', '9월', '10월', '11월', '12월'],
  MONTHS: ['1월', '2월', '3월', '4월', '5월', '6월', '7월', '8월',
      '9월', '10월', '11월', '12월'],
  STANDALONEMONTHS: ['1월', '2월', '3월', '4월', '5월', '6월', '7월',
      '8월', '9월', '10월', '11월', '12월'],
  SHORTMONTHS: ['1월', '2월', '3월', '4월', '5월', '6월', '7월', '8월',
      '9월', '10월', '11월', '12월'],
  STANDALONESHORTMONTHS: ['1월', '2월', '3월', '4월', '5월', '6월',
      '7월', '8월', '9월', '10월', '11월', '12월'],
  WEEKDAYS: ['일요일', '월요일', '화요일', '수요일', '목요일',
      '금요일', '토요일'],
  STANDALONEWEEKDAYS: ['일요일', '월요일', '화요일', '수요일',
      '목요일', '금요일', '토요일'],
  SHORTWEEKDAYS: ['일', '월', '화', '수', '목', '금', '토'],
  STANDALONESHORTWEEKDAYS: ['일', '월', '화', '수', '목', '금', '토'],
  NARROWWEEKDAYS: ['일', '월', '화', '수', '목', '금', '토'],
  STANDALONENARROWWEEKDAYS: ['일', '월', '화', '수', '목', '금', '토'],
  SHORTQUARTERS: ['1분기', '2분기', '3분기', '4분기'],
  QUARTERS: ['제 1/4분기', '제 2/4분기', '제 3/4분기',
      '제 4/4분기'],
  AMPMS: ['오전', '오후'],
  DATEFORMATS: ['y년 M월 d일 EEEE', 'y년 M월 d일', 'yyyy. M. d.',
      'yy. M. d.'],
  TIMEFORMATS: ['a h시 m분 s초 zzzz', 'a h시 m분 s초 z', 'a h:mm:ss',
      'a h:mm'],
  FIRSTDAYOFWEEK: 6,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 2
};


/**
 * Date/time formatting symbols for locale ln.
 */
goog.i18n.DateTimeSymbols_ln = {
  ERAS: ['libóso ya', 'nsima ya Y'],
  ERANAMES: ['Yambo ya Yézu Krís', 'Nsima ya Yézu Krís'],
  NARROWMONTHS: ['y', 'f', 'm', 'a', 'm', 'y', 'y', 'a', 's', 'ɔ', 'n', 'd'],
  STANDALONENARROWMONTHS: ['y', 'f', 'm', 'a', 'm', 'y', 'y', 'a', 's', 'ɔ',
      'n', 'd'],
  MONTHS: ['sánzá ya yambo', 'sánzá ya míbalé', 'sánzá ya mísáto',
      'sánzá ya mínei', 'sánzá ya mítáno', 'sánzá ya motóbá',
      'sánzá ya nsambo', 'sánzá ya mwambe', 'sánzá ya libwa',
      'sánzá ya zómi', 'sánzá ya zómi na mɔ̌kɔ́',
      'sánzá ya zómi na míbalé'],
  STANDALONEMONTHS: ['sánzá ya yambo', 'sánzá ya míbalé',
      'sánzá ya mísáto', 'sánzá ya mínei', 'sánzá ya mítáno',
      'sánzá ya motóbá', 'sánzá ya nsambo', 'sánzá ya mwambe',
      'sánzá ya libwa', 'sánzá ya zómi', 'sánzá ya zómi na mɔ̌kɔ́',
      'sánzá ya zómi na míbalé'],
  SHORTMONTHS: ['yan', 'fbl', 'msi', 'apl', 'mai', 'yun', 'yul', 'agt', 'stb',
      'ɔtb', 'nvb', 'dsb'],
  STANDALONESHORTMONTHS: ['yan', 'fbl', 'msi', 'apl', 'mai', 'yun', 'yul',
      'agt', 'stb', 'ɔtb', 'nvb', 'dsb'],
  WEEKDAYS: ['eyenga', 'mokɔlɔ mwa yambo', 'mokɔlɔ mwa míbalé',
      'mokɔlɔ mwa mísáto', 'mokɔlɔ ya mínéi', 'mokɔlɔ ya mítáno',
      'mpɔ́sɔ'],
  STANDALONEWEEKDAYS: ['eyenga', 'mokɔlɔ mwa yambo', 'mokɔlɔ mwa míbalé',
      'mokɔlɔ mwa mísáto', 'mokɔlɔ ya mínéi', 'mokɔlɔ ya mítáno',
      'mpɔ́sɔ'],
  SHORTWEEKDAYS: ['eye', 'ybo', 'mbl', 'mst', 'min', 'mtn', 'mps'],
  STANDALONESHORTWEEKDAYS: ['eye', 'ybo', 'mbl', 'mst', 'min', 'mtn', 'mps'],
  NARROWWEEKDAYS: ['e', 'y', 'm', 'm', 'm', 'm', 'p'],
  STANDALONENARROWWEEKDAYS: ['e', 'y', 'm', 'm', 'm', 'm', 'p'],
  SHORTQUARTERS: ['SM1', 'SM2', 'SM3', 'SM4'],
  QUARTERS: ['sánzá mísáto ya yambo', 'sánzá mísáto ya míbalé',
      'sánzá mísáto ya mísáto', 'sánzá mísáto ya mínei'],
  AMPMS: ['ntɔ́ngɔ́', 'mpókwa'],
  DATEFORMATS: ['EEEE d MMMM y', 'd MMMM y', 'd MMM y', 'd/M/yyyy'],
  TIMEFORMATS: ['HH:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale lt.
 */
goog.i18n.DateTimeSymbols_lt = {
  ERAS: ['pr. Kr.', 'po Kr.'],
  ERANAMES: ['prieš Kristų', 'po Kristaus'],
  NARROWMONTHS: ['S', 'V', 'K', 'B', 'G', 'B', 'L', 'R', 'R', 'S', 'L', 'G'],
  STANDALONENARROWMONTHS: ['S', 'V', 'K', 'B', 'G', 'B', 'L', 'R', 'R', 'S',
      'L', 'G'],
  MONTHS: ['sausio', 'vasaris', 'kovas', 'balandis', 'gegužė', 'birželis',
      'liepa', 'rugpjūtis', 'rugsėjis', 'spalis', 'lapkritis', 'gruodis'],
  STANDALONEMONTHS: ['Sausis', 'Vasaris', 'Kovas', 'Balandis', 'Gegužė',
      'Birželis', 'Liepa', 'Rugpjūtis', 'Rugsėjis', 'Spalis', 'Lapkritis',
      'Gruodis'],
  SHORTMONTHS: ['Saus.', 'Vas', 'Kov.', 'Bal.', 'Geg.', 'Bir.', 'Liep.',
      'Rugp.', 'Rugs.', 'Spal.', 'Lapkr.', 'Gruod.'],
  STANDALONESHORTMONTHS: ['Saus.', 'Vas.', 'Kov.', 'Bal.', 'Geg.', 'Bir.',
      'Liep.', 'Rugp.', 'Rugs.', 'Spal.', 'Lapkr.', 'Gruod.'],
  WEEKDAYS: ['sekmadienis', 'pirmadienis', 'antradienis', 'trečiadienis',
      'ketvirtadienis', 'penktadienis', 'šeštadienis'],
  STANDALONEWEEKDAYS: ['sekmadienis', 'pirmadienis', 'antradienis',
      'trečiadienis', 'ketvirtadienis', 'penktadienis', 'šeštadienis'],
  SHORTWEEKDAYS: ['Sk', 'Pr', 'An', 'Tr', 'Kt', 'Pn', 'Št'],
  STANDALONESHORTWEEKDAYS: ['Sk', 'Pr', 'An', 'Tr', 'Kt', 'Pn', 'Št'],
  NARROWWEEKDAYS: ['S', 'P', 'A', 'T', 'K', 'P', 'Š'],
  STANDALONENARROWWEEKDAYS: ['S', 'P', 'A', 'T', 'K', 'P', 'Š'],
  SHORTQUARTERS: ['I k.', 'II k.', 'III k.', 'IV ketv.'],
  QUARTERS: ['I ketvirtis', 'II ketvirtis', 'III ketvirtis', 'IV ketvirtis'],
  AMPMS: ['priešpiet', 'popiet'],
  DATEFORMATS: ['y \'m\'. MMMM d \'d\'., EEEE', 'y \'m\'. MMMM d \'d\'.',
      'y MMM d', 'yyyy-MM-dd'],
  TIMEFORMATS: ['HH:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale lv.
 */
goog.i18n.DateTimeSymbols_lv = {
  ERAS: ['p.m.ē.', 'm.ē.'],
  ERANAMES: ['pirms mūsu ēras', 'mūsu ērā'],
  NARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O',
      'N', 'D'],
  MONTHS: ['janvāris', 'februāris', 'marts', 'aprīlis', 'maijs', 'jūnijs',
      'jūlijs', 'augusts', 'septembris', 'oktobris', 'novembris', 'decembris'],
  STANDALONEMONTHS: ['janvāris', 'februāris', 'marts', 'aprīlis', 'maijs',
      'jūnijs', 'jūlijs', 'augusts', 'septembris', 'oktobris', 'novembris',
      'decembris'],
  SHORTMONTHS: ['janv.', 'febr.', 'marts', 'apr.', 'maijs', 'jūn.', 'jūl.',
      'aug.', 'sept.', 'okt.', 'nov.', 'dec.'],
  STANDALONESHORTMONTHS: ['janv.', 'febr.', 'marts', 'apr.', 'maijs', 'jūn.',
      'jūl.', 'aug.', 'sept.', 'okt.', 'nov.', 'dec.'],
  WEEKDAYS: ['svētdiena', 'pirmdiena', 'otrdiena', 'trešdiena', 'ceturtdiena',
      'piektdiena', 'sestdiena'],
  STANDALONEWEEKDAYS: ['svētdiena', 'pirmdiena', 'otrdiena', 'trešdiena',
      'ceturtdiena', 'piektdiena', 'sestdiena'],
  SHORTWEEKDAYS: ['Sv', 'Pr', 'Ot', 'Tr', 'Ce', 'Pk', 'Se'],
  STANDALONESHORTWEEKDAYS: ['Sv', 'Pr', 'Ot', 'Tr', 'Ce', 'Pk', 'Se'],
  NARROWWEEKDAYS: ['S', 'P', 'O', 'T', 'C', 'P', 'S'],
  STANDALONENARROWWEEKDAYS: ['S', 'P', 'O', 'T', 'C', 'P', 'S'],
  SHORTQUARTERS: ['C1', 'C2', 'C3', 'C4'],
  QUARTERS: ['1. ceturksnis', '2. ceturksnis', '3. ceturksnis',
      '4. ceturksnis'],
  AMPMS: ['priekšpusdienā', 'pēcpusdienā'],
  DATEFORMATS: ['EEEE, y. \'gada\' d. MMMM', 'y. \'gada\' d. MMMM',
      'y. \'gada\' d. MMM', 'dd.MM.yy'],
  TIMEFORMATS: ['HH:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale ml.
 */
goog.i18n.DateTimeSymbols_ml = {
  ERAS: ['ക്രി.മൂ', 'ക്രി.പി.'],
  ERANAMES: ['ക്രിസ്തുവിനു് മുമ്പ്‌',
      'ക്രിസ്തുവിന് പിന്‍പ്'],
  NARROWMONTHS: ['ജ', 'ഫെ', 'മാ', 'ഏ', 'മേ', 'ജൂ', 'ജൂ',
      'ഓ', 'സെ', 'ഒ', 'ന', 'ഡി'],
  STANDALONENARROWMONTHS: ['ജ', 'ഫെ', 'മാ', 'ഏ', 'മേ', 'ജൂ',
      'ജൂ', 'ഓ', 'സെ', 'ഒ', 'ന', 'ഡി'],
  MONTHS: ['ജനുവരി', 'ഫെബ്രുവരി',
      'മാര്‍ച്ച്', 'ഏപ്രില്‍', 'മേയ്',
      'ജൂണ്‍', 'ജൂലൈ', 'ആഗസ്റ്റ്',
      'സെപ്റ്റംബര്‍', 'ഒക്ടോബര്‍',
      'നവംബര്‍', 'ഡിസംബര്‍'],
  STANDALONEMONTHS: ['ജനുവരി', 'ഫെബ്രുവരി',
      'മാര്‍ച്ച്', 'ഏപ്രില്‍', 'മേയ്',
      'ജൂണ്‍', 'ജൂലൈ', 'ആഗസ്റ്റ്',
      'സെപ്റ്റംബര്‍', 'ഒക്ടോബര്‍',
      'നവംബര്‍', 'ഡിസംബര്‍'],
  SHORTMONTHS: ['ജനു', 'ഫെബ്രു', 'മാര്‍',
      'ഏപ്രി', 'മേയ്', 'ജൂണ്‍', 'ജൂലൈ',
      'ഓഗ', 'സെപ്റ്റം', 'ഒക്ടോ', 'നവം',
      'ഡിസം'],
  STANDALONESHORTMONTHS: ['ജനു', 'ഫെബ്രു', 'മാര്‍',
      'ഏപ്രി', 'മേയ്', 'ജൂണ്‍', 'ജൂലൈ',
      'ഓഗ', 'സെപ്റ്റം', 'ഒക്ടോ', 'നവം',
      'ഡിസം'],
  WEEKDAYS: ['ഞായറാഴ്ച', 'തിങ്കളാഴ്ച',
      'ചൊവ്വാഴ്ച', 'ബുധനാഴ്ച',
      'വ്യാഴാഴ്ച', 'വെള്ളിയാഴ്ച',
      'ശനിയാഴ്ച'],
  STANDALONEWEEKDAYS: ['ഞായറാഴ്ച',
      'തിങ്കളാഴ്ച', 'ചൊവ്വാഴ്ച',
      'ബുധനാഴ്ച', 'വ്യാഴാഴ്ച',
      'വെള്ളിയാഴ്ച', 'ശനിയാഴ്ച'],
  SHORTWEEKDAYS: ['ഞായര്‍', 'തിങ്കള്‍',
      'ചൊവ്വ', 'ബുധന്‍', 'വ്യാഴം',
      'വെള്ളി', 'ശനി'],
  STANDALONESHORTWEEKDAYS: ['ഞായര്‍', 'തിങ്കള്‍',
      'ചൊവ്വ', 'ബുധന്‍', 'വ്യാഴം',
      'വെള്ളി', 'ശനി'],
  NARROWWEEKDAYS: ['ഞാ', 'തി', 'ചൊ', 'ബു', 'വ്യാ',
      'വെ', 'ശ'],
  STANDALONENARROWWEEKDAYS: ['ഞാ', 'തി', 'ചൊ', 'ബു',
      'വ്യാ', 'വെ', 'ശ'],
  SHORTQUARTERS: ['Q1', 'Q2', 'Q3', 'Q4'],
  QUARTERS: ['ഒന്നാം പാദം',
      'രണ്ടാം പാദം', 'മൂന്നാം പാദം',
      'നാലാം പാദം'],
  AMPMS: ['രാവിലെ', 'വൈകുന്നേരം'],
  DATEFORMATS: ['y, MMMM d, EEEE', 'y, MMMM d', 'y, MMM d', 'dd/MM/yy'],
  TIMEFORMATS: ['h:mm:ss a zzzz', 'h:mm:ss a z', 'h:mm:ss a', 'h:mm a'],
  FIRSTDAYOFWEEK: 6,
  WEEKENDRANGE: [6, 6],
  FIRSTWEEKCUTOFFDAY: 2
};


/**
 * Date/time formatting symbols for locale mr.
 */
goog.i18n.DateTimeSymbols_mr = {
  ERAS: ['ईसापूर्व', 'सन'],
  ERANAMES: ['ईसवीसनपूर्व', 'ईसवीसन'],
  NARROWMONTHS: ['जा', 'फे', 'मा', 'ए', 'मे', 'जू',
      'जु', 'ऑ', 'स', 'ऑ', 'नो', 'डि'],
  STANDALONENARROWMONTHS: ['जा', 'फे', 'मा', 'ए', 'मे',
      'जू', 'जु', 'ऑ', 'स', 'ऑ', 'नो', 'डि'],
  MONTHS: ['जानेवारी', 'फेब्रुवारी',
      'मार्च', 'एप्रिल', 'मे', 'जून',
      'जुलै', 'ऑगस्ट', 'सप्टेंबर',
      'ऑक्टोबर', 'नोव्हेंबर',
      'डिसेंबर'],
  STANDALONEMONTHS: ['जानेवारी',
      'फेब्रुवारी', 'मार्च', 'एप्रिल',
      'मे', 'जून', 'जुलै', 'ऑगस्ट',
      'सप्टेंबर', 'ऑक्टोबर',
      'नोव्हेंबर', 'डिसेंबर'],
  SHORTMONTHS: ['जाने', 'फेब्रु', 'मार्च',
      'एप्रि', 'मे', 'जून', 'जुलै', 'ऑग',
      'सेप्टें', 'ऑक्टोबर', 'नोव्हें',
      'डिसें'],
  STANDALONESHORTMONTHS: ['जाने', 'फेब्रु',
      'मार्च', 'एप्रि', 'मे', 'जून',
      'जुलै', 'ऑग', 'सेप्टें',
      'ऑक्टोबर', 'नोव्हें', 'डिसें'],
  WEEKDAYS: ['रविवार', 'सोमवार',
      'मंगळवार', 'बुधवार', 'गुरुवार',
      'शुक्रवार', 'शनिवार'],
  STANDALONEWEEKDAYS: ['रविवार', 'सोमवार',
      'मंगळवार', 'बुधवार', 'गुरुवार',
      'शुक्रवार', 'शनिवार'],
  SHORTWEEKDAYS: ['रवि', 'सोम', 'मंगळ', 'बुध',
      'गुरु', 'शुक्र', 'शनि'],
  STANDALONESHORTWEEKDAYS: ['रवि', 'सोम', 'मंगळ',
      'बुध', 'गुरु', 'शुक्र', 'शनि'],
  NARROWWEEKDAYS: ['र', 'सो', 'मं', 'बु', 'गु', 'शु',
      'श'],
  STANDALONENARROWWEEKDAYS: ['र', 'सो', 'मं', 'बु', 'गु',
      'शु', 'श'],
  SHORTQUARTERS: ['ति 1', '2 री तिमाही', 'ति 3',
      'ति 4'],
  QUARTERS: ['प्रथम तिमाही',
      'द्वितीय तिमाही',
      'तृतीय तिमाही',
      'चतुर्थ तिमाही'],
  AMPMS: ['म.पू.', 'म.नं.'],
  DATEFORMATS: ['EEEE d MMMM y', 'd MMMM y', 'd MMM y', 'd-M-yy'],
  TIMEFORMATS: ['h-mm-ss a zzzz', 'h-mm-ss a z', 'h-mm-ss a', 'h-mm a'],
  FIRSTDAYOFWEEK: 6,
  WEEKENDRANGE: [6, 6],
  FIRSTWEEKCUTOFFDAY: 2
};


/**
 * Date/time formatting symbols for locale ms.
 */
goog.i18n.DateTimeSymbols_ms = {
  ERAS: ['S.M.', 'TM'],
  ERANAMES: ['S.M.', 'TM'],
  NARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'O', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'O', 'S', 'O',
      'N', 'D'],
  MONTHS: ['Januari', 'Februari', 'Mac', 'April', 'Mei', 'Jun', 'Julai', 'Ogos',
      'September', 'Oktober', 'November', 'Disember'],
  STANDALONEMONTHS: ['Januari', 'Februari', 'Mac', 'April', 'Mei', 'Jun',
      'Julai', 'Ogos', 'September', 'Oktober', 'November', 'Disember'],
  SHORTMONTHS: ['Jan', 'Feb', 'Mac', 'Apr', 'Mei', 'Jun', 'Jul', 'Ogos', 'Sep',
      'Okt', 'Nov', 'Dis'],
  STANDALONESHORTMONTHS: ['Jan', 'Feb', 'Mac', 'Apr', 'Mei', 'Jun', 'Jul',
      'Ogos', 'Sep', 'Okt', 'Nov', 'Dis'],
  WEEKDAYS: ['Ahad', 'Isnin', 'Selasa', 'Rabu', 'Khamis', 'Jumaat', 'Sabtu'],
  STANDALONEWEEKDAYS: ['Ahad', 'Isnin', 'Selasa', 'Rabu', 'Khamis', 'Jumaat',
      'Sabtu'],
  SHORTWEEKDAYS: ['Ahd', 'Isn', 'Sel', 'Rab', 'Kha', 'Jum', 'Sab'],
  STANDALONESHORTWEEKDAYS: ['Ahd', 'Isn', 'Sel', 'Rab', 'Kha', 'Jum', 'Sab'],
  NARROWWEEKDAYS: ['A', 'I', 'S', 'R', 'K', 'J', 'S'],
  STANDALONENARROWWEEKDAYS: ['A', 'I', 'S', 'R', 'K', 'J', 'S'],
  SHORTQUARTERS: ['Suku 1', 'Suku Ke-2', 'Suku Ke-3', 'Suku Ke-4'],
  QUARTERS: ['Suku pertama', 'Suku Ke-2', 'Suku Ke-3', 'Suku Ke-4'],
  AMPMS: ['PG', 'PTG'],
  DATEFORMATS: ['EEEE, d MMMM y', 'd MMMM y', 'dd/MM/yyyy', 'd/MM/yy'],
  TIMEFORMATS: ['h:mm:ss a zzzz', 'h:mm:ss a z', 'h:mm:ss a', 'h:mm a'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale mt.
 */
goog.i18n.DateTimeSymbols_mt = {
  ERAS: ['QK', 'WK'],
  ERANAMES: ['Qabel Kristu', 'Wara Kristu'],
  NARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'Ġ', 'L', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'Ġ', 'L', 'A', 'S', 'O',
      'N', 'D'],
  MONTHS: ['Jannar', 'Frar', 'Marzu', 'April', 'Mejju', 'Ġunju', 'Lulju',
      'Awwissu', 'Settembru', 'Ottubru', 'Novembru', 'Diċembru'],
  STANDALONEMONTHS: ['Jannar', 'Frar', 'Marzu', 'April', 'Mejju', 'Ġunju',
      'Lulju', 'Awwissu', 'Settembru', 'Ottubru', 'Novembru', 'Diċembru'],
  SHORTMONTHS: ['Jan', 'Fra', 'Mar', 'Apr', 'Mej', 'Ġun', 'Lul', 'Aww', 'Set',
      'Ott', 'Nov', 'Diċ'],
  STANDALONESHORTMONTHS: ['Jan', 'Fra', 'Mar', 'Apr', 'Mej', 'Ġun', 'Lul',
      'Aww', 'Set', 'Ott', 'Nov', 'Diċ'],
  WEEKDAYS: ['Il-Ħadd', 'It-Tnejn', 'It-Tlieta', 'L-Erbgħa', 'Il-Ħamis',
      'Il-Ġimgħa', 'Is-Sibt'],
  STANDALONEWEEKDAYS: ['Il-Ħadd', 'It-Tnejn', 'It-Tlieta', 'L-Erbgħa',
      'Il-Ħamis', 'Il-Ġimgħa', 'Is-Sibt'],
  SHORTWEEKDAYS: ['Ħad', 'Tne', 'Tli', 'Erb', 'Ħam', 'Ġim', 'Sib'],
  STANDALONESHORTWEEKDAYS: ['Ħad', 'Tne', 'Tli', 'Erb', 'Ħam', 'Ġim', 'Sib'],
  NARROWWEEKDAYS: ['Ħ', 'T', 'T', 'E', 'Ħ', 'Ġ', 'S'],
  STANDALONENARROWWEEKDAYS: ['Ħ', 'T', 'T', 'E', 'Ħ', 'Ġ', 'S'],
  SHORTQUARTERS: ['K1', 'K2', 'K3', 'K4'],
  QUARTERS: ['K1', 'K2', 'K3', 'K4'],
  AMPMS: ['QN', 'WN'],
  DATEFORMATS: ['EEEE, d \'ta\'’ MMMM y', 'd \'ta\'’ MMMM y', 'dd MMM y',
      'dd/MM/yyyy'],
  TIMEFORMATS: ['HH:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 6,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 2
};


/**
 * Date/time formatting symbols for locale nl.
 */
goog.i18n.DateTimeSymbols_nl = {
  ERAS: ['v. Chr.', 'n. Chr.'],
  ERANAMES: ['Voor Christus', 'na Christus'],
  NARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O',
      'N', 'D'],
  MONTHS: ['januari', 'februari', 'maart', 'april', 'mei', 'juni', 'juli',
      'augustus', 'september', 'oktober', 'november', 'december'],
  STANDALONEMONTHS: ['januari', 'februari', 'maart', 'april', 'mei', 'juni',
      'juli', 'augustus', 'september', 'oktober', 'november', 'december'],
  SHORTMONTHS: ['jan.', 'feb.', 'mrt.', 'apr.', 'mei', 'jun.', 'jul.', 'aug.',
      'sep.', 'okt.', 'nov.', 'dec.'],
  STANDALONESHORTMONTHS: ['jan', 'feb', 'mrt', 'apr', 'mei', 'jun', 'jul',
      'aug', 'sep', 'okt', 'nov', 'dec'],
  WEEKDAYS: ['zondag', 'maandag', 'dinsdag', 'woensdag', 'donderdag', 'vrijdag',
      'zaterdag'],
  STANDALONEWEEKDAYS: ['zondag', 'maandag', 'dinsdag', 'woensdag', 'donderdag',
      'vrijdag', 'zaterdag'],
  SHORTWEEKDAYS: ['zo', 'ma', 'di', 'wo', 'do', 'vr', 'za'],
  STANDALONESHORTWEEKDAYS: ['zo', 'ma', 'di', 'wo', 'do', 'vr', 'za'],
  NARROWWEEKDAYS: ['Z', 'M', 'D', 'W', 'D', 'V', 'Z'],
  STANDALONENARROWWEEKDAYS: ['Z', 'M', 'D', 'W', 'D', 'V', 'Z'],
  SHORTQUARTERS: ['K1', 'K2', 'K3', 'K4'],
  QUARTERS: ['1e kwartaal', '2e kwartaal', '3e kwartaal', '4e kwartaal'],
  AMPMS: ['AM', 'PM'],
  DATEFORMATS: ['EEEE d MMMM y', 'd MMMM y', 'd MMM y', 'dd-MM-yy'],
  TIMEFORMATS: ['HH:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale no.
 */
goog.i18n.DateTimeSymbols_no = {
  ERAS: ['f.Kr.', 'e.Kr.'],
  ERANAMES: ['f.Kr.', 'e.Kr.'],
  NARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O',
      'N', 'D'],
  MONTHS: ['januar', 'februar', 'mars', 'april', 'mai', 'juni', 'juli',
      'august', 'september', 'oktober', 'november', 'desember'],
  STANDALONEMONTHS: ['januar', 'februar', 'mars', 'april', 'mai', 'juni',
      'juli', 'august', 'september', 'oktober', 'november', 'desember'],
  SHORTMONTHS: ['jan.', 'feb.', 'mars', 'apr.', 'mai', 'juni', 'juli', 'aug.',
      'sep.', 'okt.', 'nov.', 'des.'],
  STANDALONESHORTMONTHS: ['jan', 'feb', 'mar', 'apr', 'mai', 'jun', 'jul',
      'aug', 'sep', 'okt', 'nov', 'des'],
  WEEKDAYS: ['søndag', 'mandag', 'tirsdag', 'onsdag', 'torsdag', 'fredag',
      'lørdag'],
  STANDALONEWEEKDAYS: ['søndag', 'mandag', 'tirsdag', 'onsdag', 'torsdag',
      'fredag', 'lørdag'],
  SHORTWEEKDAYS: ['søn.', 'man.', 'tir.', 'ons.', 'tor.', 'fre.', 'lør.'],
  STANDALONESHORTWEEKDAYS: ['sø.', 'ma.', 'ti.', 'on.', 'to.', 'fr.', 'lø.'],
  NARROWWEEKDAYS: ['S', 'M', 'T', 'O', 'T', 'F', 'L'],
  STANDALONENARROWWEEKDAYS: ['S', 'M', 'T', 'O', 'T', 'F', 'L'],
  SHORTQUARTERS: ['K1', 'K2', 'K3', 'K4'],
  QUARTERS: ['1. kvartal', '2. kvartal', '3. kvartal', '4. kvartal'],
  AMPMS: ['AM', 'PM'],
  DATEFORMATS: ['EEEE d. MMMM y', 'd. MMMM y', 'd. MMM y', 'dd.MM.yy'],
  TIMEFORMATS: ['\'kl\'. HH:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale or.
 */
goog.i18n.DateTimeSymbols_or = {
  ERAS: ['BCE', 'CE'],
  ERANAMES: ['BCE', 'CE'],
  NARROWMONTHS: ['ଜା', 'ଫେ', 'ମା', 'ଅ', 'ମେ', 'ଜୁ',
      'ଜୁ', 'ଅ', 'ସେ', 'ଅ', 'ନ', 'ଡି'],
  STANDALONENARROWMONTHS: ['ଜା', 'ଫେ', 'ମା', 'ଅ', 'ମେ',
      'ଜୁ', 'ଜୁ', 'ଅ', 'ସେ', 'ଅ', 'ନ', 'ଡି'],
  MONTHS: ['ଜାନୁଆରୀ', 'ଫେବ୍ରୁୟାରୀ',
      'ମାର୍ଚ୍ଚ', 'ଅପ୍ରେଲ', 'ମେ', 'ଜୁନ',
      'ଜୁଲାଇ', 'ଅଗଷ୍ଟ', 'ସେପ୍ଟେମ୍ବର',
      'ଅକ୍ଟୋବର', 'ନଭେମ୍ବର',
      'ଡିସେମ୍ବର'],
  STANDALONEMONTHS: ['ଜାନୁଆରୀ', 'ଫେବ୍ରୁୟାରୀ',
      'ମାର୍ଚ୍ଚ', 'ଅପ୍ରେଲ', 'ମେ', 'ଜୁନ',
      'ଜୁଲାଇ', 'ଅଗଷ୍ଟ', 'ସେପ୍ଟେମ୍ବର',
      'ଅକ୍ଟୋବର', 'ନଭେମ୍ବର',
      'ଡିସେମ୍ବର'],
  SHORTMONTHS: ['ଜାନୁଆରୀ', 'ଫେବ୍ରୁୟାରୀ',
      'ମାର୍ଚ୍ଚ', 'ଅପ୍ରେଲ', 'ମେ', 'ଜୁନ',
      'ଜୁଲାଇ', 'ଅଗଷ୍ଟ', 'ସେପ୍ଟେମ୍ବର',
      'ଅକ୍ଟୋବର', 'ନଭେମ୍ବର',
      'ଡିସେମ୍ବର'],
  STANDALONESHORTMONTHS: ['ଜାନୁଆରୀ',
      'ଫେବ୍ରୁୟାରୀ', 'ମାର୍ଚ୍ଚ',
      'ଅପ୍ରେଲ', 'ମେ', 'ଜୁନ', 'ଜୁଲାଇ',
      'ଅଗଷ୍ଟ', 'ସେପ୍ଟେମ୍ବର',
      'ଅକ୍ଟୋବର', 'ନଭେମ୍ବର',
      'ଡିସେମ୍ବର'],
  WEEKDAYS: ['ରବିବାର', 'ସୋମବାର',
      'ମଙ୍ଗଳବାର', 'ବୁଧବାର', 'ଗୁରୁବାର',
      'ଶୁକ୍ରବାର', 'ଶନିବାର'],
  STANDALONEWEEKDAYS: ['ରବିବାର', 'ସୋମବାର',
      'ମଙ୍ଗଳବାର', 'ବୁଧବାର', 'ଗୁରୁବାର',
      'ଶୁକ୍ରବାର', 'ଶନିବାର'],
  SHORTWEEKDAYS: ['ରବି', 'ସୋମ', 'ମଙ୍ଗଳ', 'ବୁଧ',
      'ଗୁରୁ', 'ଶୁକ୍ର', 'ଶନି'],
  STANDALONESHORTWEEKDAYS: ['ରବି', 'ସୋମ', 'ମଙ୍ଗଳ',
      'ବୁଧ', 'ଗୁରୁ', 'ଶୁକ୍ର', 'ଶନି'],
  NARROWWEEKDAYS: ['ର', 'ସୋ', 'ମ', 'ବୁ', 'ଗୁ', 'ଶୁ', 'ଶ'],
  STANDALONENARROWWEEKDAYS: ['ର', 'ସୋ', 'ମ', 'ବୁ', 'ଗୁ',
      'ଶୁ', 'ଶ'],
  SHORTQUARTERS: ['Q1', 'Q2', 'Q3', 'Q4'],
  QUARTERS: ['Q1', 'Q2', 'Q3', 'Q4'],
  AMPMS: ['am', 'pm'],
  DATEFORMATS: ['EEEE, d MMMM y', 'd MMMM y', 'd MMM y', 'd-M-yy'],
  TIMEFORMATS: ['h:mm:ss a zzzz', 'h:mm:ss a z', 'h:mm:ss a', 'h:mm a'],
  FIRSTDAYOFWEEK: 6,
  WEEKENDRANGE: [6, 6],
  FIRSTWEEKCUTOFFDAY: 2
};


/**
 * Date/time formatting symbols for locale pl.
 */
goog.i18n.DateTimeSymbols_pl = {
  ERAS: ['p.n.e.', 'n.e.'],
  ERANAMES: ['p.n.e.', 'n.e.'],
  NARROWMONTHS: ['s', 'l', 'm', 'k', 'm', 'c', 'l', 's', 'w', 'p', 'l', 'g'],
  STANDALONENARROWMONTHS: ['s', 'l', 'm', 'k', 'm', 'c', 'l', 's', 'w', 'p',
      'l', 'g'],
  MONTHS: ['stycznia', 'lutego', 'marca', 'kwietnia', 'maja', 'czerwca',
      'lipca', 'sierpnia', 'września', 'października', 'listopada',
      'grudnia'],
  STANDALONEMONTHS: ['styczeń', 'luty', 'marzec', 'kwiecień', 'maj',
      'czerwiec', 'lipiec', 'sierpień', 'wrzesień', 'październik',
      'listopad', 'grudzień'],
  SHORTMONTHS: ['sty', 'lut', 'mar', 'kwi', 'maj', 'cze', 'lip', 'sie', 'wrz',
      'paź', 'lis', 'gru'],
  STANDALONESHORTMONTHS: ['sty', 'lut', 'mar', 'kwi', 'maj', 'cze', 'lip',
      'sie', 'wrz', 'paź', 'lis', 'gru'],
  WEEKDAYS: ['niedziela', 'poniedziałek', 'wtorek', 'środa', 'czwartek',
      'piątek', 'sobota'],
  STANDALONEWEEKDAYS: ['niedziela', 'poniedziałek', 'wtorek', 'środa',
      'czwartek', 'piątek', 'sobota'],
  SHORTWEEKDAYS: ['niedz.', 'pon.', 'wt.', 'śr.', 'czw.', 'pt.', 'sob.'],
  STANDALONESHORTWEEKDAYS: ['niedz.', 'pon.', 'wt.', 'śr.', 'czw.', 'pt.',
      'sob.'],
  NARROWWEEKDAYS: ['N', 'P', 'W', 'Ś', 'C', 'P', 'S'],
  STANDALONENARROWWEEKDAYS: ['N', 'P', 'W', 'Ś', 'C', 'P', 'S'],
  SHORTQUARTERS: ['K1', 'K2', 'K3', 'K4'],
  QUARTERS: ['I kwartał', 'II kwartał', 'III kwartał', 'IV kwartał'],
  AMPMS: ['AM', 'PM'],
  DATEFORMATS: ['EEEE, d MMMM y', 'd MMMM y', 'd MMM y', 'dd.MM.yyyy'],
  TIMEFORMATS: ['HH:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale pt.
 */
goog.i18n.DateTimeSymbols_pt = {
  ERAS: ['a.C.', 'd.C.'],
  ERANAMES: ['Antes de Cristo', 'Ano do Senhor'],
  NARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O',
      'N', 'D'],
  MONTHS: ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho',
      'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'],
  STANDALONEMONTHS: ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho',
      'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'],
  SHORTMONTHS: ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set',
      'out', 'nov', 'dez'],
  STANDALONESHORTMONTHS: ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul',
      'ago', 'set', 'out', 'nov', 'dez'],
  WEEKDAYS: ['domingo', 'segunda-feira', 'terça-feira', 'quarta-feira',
      'quinta-feira', 'sexta-feira', 'sábado'],
  STANDALONEWEEKDAYS: ['domingo', 'segunda-feira', 'terça-feira',
      'quarta-feira', 'quinta-feira', 'sexta-feira', 'sábado'],
  SHORTWEEKDAYS: ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb'],
  STANDALONESHORTWEEKDAYS: ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb'],
  NARROWWEEKDAYS: ['D', 'S', 'T', 'Q', 'Q', 'S', 'S'],
  STANDALONENARROWWEEKDAYS: ['D', 'S', 'T', 'Q', 'Q', 'S', 'S'],
  SHORTQUARTERS: ['T1', 'T2', 'T3', 'T4'],
  QUARTERS: ['1º trimestre', '2º trimestre', '3º trimestre',
      '4º trimestre'],
  AMPMS: ['AM', 'PM'],
  DATEFORMATS: ['EEEE, d \'de\' MMMM \'de\' y', 'd \'de\' MMMM \'de\' y',
      'dd/MM/yyyy', 'dd/MM/yy'],
  TIMEFORMATS: ['HH\'h\'mm\'min\'ss\'s\' zzzz', 'HH\'h\'mm\'min\'ss\'s\' z',
      'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 6,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 2
};


/**
 * Date/time formatting symbols for locale pt_BR.
 */
goog.i18n.DateTimeSymbols_pt_BR = goog.i18n.DateTimeSymbols_pt;


/**
 * Date/time formatting symbols for locale pt_PT.
 */
goog.i18n.DateTimeSymbols_pt_PT = {
  ERAS: ['a.C.', 'd.C.'],
  ERANAMES: ['Antes de Cristo', 'Ano do Senhor'],
  NARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O',
      'N', 'D'],
  MONTHS: ['Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho', 'Julho',
      'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'],
  STANDALONEMONTHS: ['Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
      'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'],
  SHORTMONTHS: ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun', 'Jul', 'Ago', 'Set',
      'Out', 'Nov', 'Dez'],
  STANDALONESHORTMONTHS: ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun', 'Jul',
      'Ago', 'Set', 'Out', 'Nov', 'Dez'],
  WEEKDAYS: ['Domingo', 'Segunda-feira', 'Terça-feira', 'Quarta-feira',
      'Quinta-feira', 'Sexta-feira', 'Sábado'],
  STANDALONEWEEKDAYS: ['Domingo', 'Segunda-feira', 'Terça-feira',
      'Quarta-feira', 'Quinta-feira', 'Sexta-feira', 'Sábado'],
  SHORTWEEKDAYS: ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb'],
  STANDALONESHORTWEEKDAYS: ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb'],
  NARROWWEEKDAYS: ['D', 'S', 'T', 'Q', 'Q', 'S', 'S'],
  STANDALONENARROWWEEKDAYS: ['D', 'S', 'T', 'Q', 'Q', 'S', 'S'],
  SHORTQUARTERS: ['T1', 'T2', 'T3', 'T4'],
  QUARTERS: ['1.º trimestre', '2.º trimestre', '3.º trimestre',
      '4.º trimestre'],
  AMPMS: ['a.m.', 'p.m.'],
  DATEFORMATS: ['EEEE, d \'de\' MMMM \'de\' y', 'd \'de\' MMMM \'de\' y',
      'dd/MM/yyyy', 'dd/MM/yy'],
  TIMEFORMATS: ['H:mm:ss zzzz', 'H:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale ro.
 */
goog.i18n.DateTimeSymbols_ro = {
  ERAS: ['î.Hr.', 'd.Hr.'],
  ERANAMES: ['înainte de Hristos', 'după Hristos'],
  NARROWMONTHS: ['I', 'F', 'M', 'A', 'M', 'I', 'I', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['I', 'F', 'M', 'A', 'M', 'I', 'I', 'A', 'S', 'O',
      'N', 'D'],
  MONTHS: ['ianuarie', 'februarie', 'martie', 'aprilie', 'mai', 'iunie',
      'iulie', 'august', 'septembrie', 'octombrie', 'noiembrie', 'decembrie'],
  STANDALONEMONTHS: ['ianuarie', 'februarie', 'martie', 'aprilie', 'mai',
      'iunie', 'iulie', 'august', 'septembrie', 'octombrie', 'noiembrie',
      'decembrie'],
  SHORTMONTHS: ['ian.', 'feb.', 'mar.', 'apr.', 'mai', 'iun.', 'iul.', 'aug.',
      'sept.', 'oct.', 'nov.', 'dec.'],
  STANDALONESHORTMONTHS: ['ian.', 'feb.', 'mar.', 'apr.', 'mai', 'iun.', 'iul.',
      'aug.', 'sept.', 'oct.', 'nov.', 'dec.'],
  WEEKDAYS: ['duminică', 'luni', 'marți', 'miercuri', 'joi', 'vineri',
      'sâmbătă'],
  STANDALONEWEEKDAYS: ['duminică', 'luni', 'marți', 'miercuri', 'joi',
      'vineri', 'sâmbătă'],
  SHORTWEEKDAYS: ['Du', 'Lu', 'Ma', 'Mi', 'Jo', 'Vi', 'Sâ'],
  STANDALONESHORTWEEKDAYS: ['Du', 'Lu', 'Ma', 'Mi', 'Jo', 'Vi', 'Sâ'],
  NARROWWEEKDAYS: ['D', 'L', 'M', 'M', 'J', 'V', 'S'],
  STANDALONENARROWWEEKDAYS: ['D', 'L', 'M', 'M', 'J', 'V', 'S'],
  SHORTQUARTERS: ['trim. I', 'trim. II', 'trim. III', 'trim. IV'],
  QUARTERS: ['trimestrul I', 'trimestrul al II-lea', 'trimestrul al III-lea',
      'trimestrul al IV-lea'],
  AMPMS: ['AM', 'PM'],
  DATEFORMATS: ['EEEE, d MMMM y', 'd MMMM y', 'dd.MM.yyyy', 'dd.MM.yyyy'],
  TIMEFORMATS: ['HH:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale ru.
 */
goog.i18n.DateTimeSymbols_ru = {
  ERAS: ['до н.э.', 'н.э.'],
  ERANAMES: ['до н.э.', 'н.э.'],
  NARROWMONTHS: ['Я', 'Ф', 'М', 'А', 'М', 'И', 'И', 'А', 'С', 'О',
      'Н', 'Д'],
  STANDALONENARROWMONTHS: ['Я', 'Ф', 'М', 'А', 'М', 'И', 'И', 'А', 'С',
      'О', 'Н', 'Д'],
  MONTHS: ['января', 'февраля', 'марта', 'апреля',
      'мая', 'июня', 'июля', 'августа', 'сентября',
      'октября', 'ноября', 'декабря'],
  STANDALONEMONTHS: ['Январь', 'Февраль', 'Март',
      'Апрель', 'Май', 'Июнь', 'Июль', 'Август',
      'Сентябрь', 'Октябрь', 'Ноябрь', 'Декабрь'],
  SHORTMONTHS: ['янв', 'фев', 'мар', 'апр', 'мая', 'июн',
      'июл', 'авг', 'сен', 'окт', 'ноя', 'дек'],
  STANDALONESHORTMONTHS: ['Янв.', 'Февр.', 'Март', 'Апр.',
      'Май', 'Июнь', 'Июль', 'Авг.', 'Сент.', 'Окт.',
      'Нояб.', 'Дек.'],
  WEEKDAYS: ['воскресенье', 'понедельник',
      'вторник', 'среда', 'четверг', 'пятница',
      'суббота'],
  STANDALONEWEEKDAYS: ['Воскресенье', 'Понедельник',
      'Вторник', 'Среда', 'Четверг', 'Пятница',
      'Суббота'],
  SHORTWEEKDAYS: ['вс', 'пн', 'вт', 'ср', 'чт', 'пт', 'сб'],
  STANDALONESHORTWEEKDAYS: ['Вс', 'Пн', 'Вт', 'Ср', 'Чт', 'Пт',
      'Сб'],
  NARROWWEEKDAYS: ['В', 'Пн', 'Вт', 'С', 'Ч', 'П', 'С'],
  STANDALONENARROWWEEKDAYS: ['В', 'П', 'В', 'С', 'Ч', 'П', 'С'],
  SHORTQUARTERS: ['1-й кв.', '2-й кв.', '3-й кв.', '4-й кв.'],
  QUARTERS: ['1-й квартал', '2-й квартал',
      '3-й квартал', '4-й квартал'],
  AMPMS: ['до полудня', 'после полудня'],
  DATEFORMATS: ['EEEE, d MMMM y \'г\'.', 'd MMMM y \'г\'.', 'dd.MM.yyyy',
      'dd.MM.yy'],
  TIMEFORMATS: ['H:mm:ss zzzz', 'H:mm:ss z', 'H:mm:ss', 'H:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale sk.
 */
goog.i18n.DateTimeSymbols_sk = {
  ERAS: ['pred n.l.', 'n.l.'],
  ERANAMES: ['pred n.l.', 'n.l.'],
  NARROWMONTHS: ['j', 'f', 'm', 'a', 'm', 'j', 'j', 'a', 's', 'o', 'n', 'd'],
  STANDALONENARROWMONTHS: ['j', 'f', 'm', 'a', 'm', 'j', 'j', 'a', 's', 'o',
      'n', 'd'],
  MONTHS: ['januára', 'februára', 'marca', 'apríla', 'mája', 'júna',
      'júla', 'augusta', 'septembra', 'októbra', 'novembra', 'decembra'],
  STANDALONEMONTHS: ['január', 'február', 'marec', 'apríl', 'máj', 'jún',
      'júl', 'august', 'september', 'október', 'november', 'december'],
  SHORTMONTHS: ['jan', 'feb', 'mar', 'apr', 'máj', 'jún', 'júl', 'aug',
      'sep', 'okt', 'nov', 'dec'],
  STANDALONESHORTMONTHS: ['jan', 'feb', 'mar', 'apr', 'máj', 'jún', 'júl',
      'aug', 'sep', 'okt', 'nov', 'dec'],
  WEEKDAYS: ['nedeľa', 'pondelok', 'utorok', 'streda', 'štvrtok', 'piatok',
      'sobota'],
  STANDALONEWEEKDAYS: ['nedeľa', 'pondelok', 'utorok', 'streda', 'štvrtok',
      'piatok', 'sobota'],
  SHORTWEEKDAYS: ['ne', 'po', 'ut', 'st', 'št', 'pi', 'so'],
  STANDALONESHORTWEEKDAYS: ['ne', 'po', 'ut', 'st', 'št', 'pi', 'so'],
  NARROWWEEKDAYS: ['N', 'P', 'U', 'S', 'Š', 'P', 'S'],
  STANDALONENARROWWEEKDAYS: ['N', 'P', 'U', 'S', 'Š', 'P', 'S'],
  SHORTQUARTERS: ['Q1', 'Q2', 'Q3', 'Q4'],
  QUARTERS: ['1. štvrťrok', '2. štvrťrok', '3. štvrťrok',
      '4. štvrťrok'],
  AMPMS: ['dopoludnia', 'popoludní'],
  DATEFORMATS: ['EEEE, d. MMMM y', 'd. MMMM y', 'd.M.yyyy', 'd.M.yyyy'],
  TIMEFORMATS: ['H:mm:ss zzzz', 'H:mm:ss z', 'H:mm:ss', 'H:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale sl.
 */
goog.i18n.DateTimeSymbols_sl = {
  ERAS: ['pr. n. št.', 'po Kr.'],
  ERANAMES: ['pred našim štetjem', 'naše štetje'],
  NARROWMONTHS: ['j', 'f', 'm', 'a', 'm', 'j', 'j', 'a', 's', 'o', 'n', 'd'],
  STANDALONENARROWMONTHS: ['j', 'f', 'm', 'a', 'm', 'j', 'j', 'a', 's', 'o',
      'n', 'd'],
  MONTHS: ['januar', 'februar', 'marec', 'april', 'maj', 'junij', 'julij',
      'avgust', 'september', 'oktober', 'november', 'december'],
  STANDALONEMONTHS: ['januar', 'februar', 'marec', 'april', 'maj', 'junij',
      'julij', 'avgust', 'september', 'oktober', 'november', 'december'],
  SHORTMONTHS: ['jan.', 'feb.', 'mar.', 'apr.', 'maj', 'jun.', 'jul.', 'avg.',
      'sep.', 'okt.', 'nov.', 'dec.'],
  STANDALONESHORTMONTHS: ['jan', 'feb', 'mar', 'apr', 'maj', 'jun', 'jul',
      'avg', 'sep', 'okt', 'nov', 'dec'],
  WEEKDAYS: ['nedelja', 'ponedeljek', 'torek', 'sreda', 'četrtek', 'petek',
      'sobota'],
  STANDALONEWEEKDAYS: ['nedelja', 'ponedeljek', 'torek', 'sreda', 'četrtek',
      'petek', 'sobota'],
  SHORTWEEKDAYS: ['ned.', 'pon.', 'tor.', 'sre.', 'čet.', 'pet.', 'sob.'],
  STANDALONESHORTWEEKDAYS: ['ned', 'pon', 'tor', 'sre', 'čet', 'pet', 'sob'],
  NARROWWEEKDAYS: ['n', 'p', 't', 's', 'č', 'p', 's'],
  STANDALONENARROWWEEKDAYS: ['n', 'p', 't', 's', 'č', 'p', 's'],
  SHORTQUARTERS: ['Q1', 'Q2', 'Q3', 'Q4'],
  QUARTERS: ['1. četrtletje', '2. četrtletje', '3. četrtletje',
      '4. četrtletje'],
  AMPMS: ['dop.', 'pop.'],
  DATEFORMATS: ['EEEE, dd. MMMM y', 'dd. MMMM y', 'd. MMM yyyy', 'd. MM. yy'],
  TIMEFORMATS: ['HH:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale sq.
 */
goog.i18n.DateTimeSymbols_sq = {
  ERAS: ['p.e.r.', 'n.e.r.'],
  ERANAMES: ['p.e.r.', 'n.e.r.'],
  NARROWMONTHS: ['J', 'S', 'M', 'P', 'M', 'Q', 'K', 'G', 'S', 'T', 'N', 'D'],
  STANDALONENARROWMONTHS: ['J', 'S', 'M', 'P', 'M', 'Q', 'K', 'G', 'S', 'T',
      'N', 'D'],
  MONTHS: ['janar', 'shkurt', 'mars', 'prill', 'maj', 'qershor', 'korrik',
      'gusht', 'shtator', 'tetor', 'nëntor', 'dhjetor'],
  STANDALONEMONTHS: ['janar', 'shkurt', 'mars', 'prill', 'maj', 'qershor',
      'korrik', 'gusht', 'shtator', 'tetor', 'nëntor', 'dhjetor'],
  SHORTMONTHS: ['Jan', 'Shk', 'Mar', 'Pri', 'Maj', 'Qer', 'Kor', 'Gsh', 'Sht',
      'Tet', 'Nën', 'Dhj'],
  STANDALONESHORTMONTHS: ['Jan', 'Shk', 'Mar', 'Pri', 'Maj', 'Qer', 'Kor',
      'Gsh', 'Sht', 'Tet', 'Nën', 'Dhj'],
  WEEKDAYS: ['e diel', 'e hënë', 'e martë', 'e mërkurë', 'e enjte',
      'e premte', 'e shtunë'],
  STANDALONEWEEKDAYS: ['e diel', 'e hënë', 'e martë', 'e mërkurë',
      'e enjte', 'e premte', 'e shtunë'],
  SHORTWEEKDAYS: ['Die', 'Hën', 'Mar', 'Mër', 'Enj', 'Pre', 'Sht'],
  STANDALONESHORTWEEKDAYS: ['Die', 'Hën', 'Mar', 'Mër', 'Enj', 'Pre', 'Sht'],
  NARROWWEEKDAYS: ['D', 'H', 'M', 'M', 'E', 'P', 'S'],
  STANDALONENARROWWEEKDAYS: ['D', 'H', 'M', 'M', 'E', 'P', 'S'],
  SHORTQUARTERS: ['Q1', 'Q2', 'Q3', 'Q4'],
  QUARTERS: ['Q1', 'Q2', 'Q3', 'Q4'],
  AMPMS: ['PD', 'MD'],
  DATEFORMATS: ['EEEE, dd MMMM y', 'dd MMMM y', 'yyyy-MM-dd', 'yy-MM-dd'],
  TIMEFORMATS: ['h.mm.ss.a zzzz', 'h.mm.ss.a z', 'h.mm.ss.a', 'h.mm.a'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale sr.
 */
goog.i18n.DateTimeSymbols_sr = {
  ERAS: ['п. н. е.', 'н. е.'],
  ERANAMES: ['Пре нове ере', 'Нове ере'],
  NARROWMONTHS: ['ј', 'ф', 'м', 'а', 'м', 'ј', 'ј', 'а', 'с', 'о',
      'н', 'д'],
  STANDALONENARROWMONTHS: ['ј', 'ф', 'м', 'а', 'м', 'ј', 'ј', 'а', 'с',
      'о', 'н', 'д'],
  MONTHS: ['јануар', 'фебруар', 'март', 'април', 'мај',
      'јун', 'јул', 'август', 'септембар',
      'октобар', 'новембар', 'децембар'],
  STANDALONEMONTHS: ['јануар', 'фебруар', 'март', 'април',
      'мај', 'јун', 'јул', 'август', 'септембар',
      'октобар', 'новембар', 'децембар'],
  SHORTMONTHS: ['јан', 'феб', 'мар', 'апр', 'мај', 'јун',
      'јул', 'авг', 'сеп', 'окт', 'нов', 'дец'],
  STANDALONESHORTMONTHS: ['јан', 'феб', 'мар', 'апр', 'мај',
      'јун', 'јул', 'авг', 'сеп', 'окт', 'нов', 'дец'],
  WEEKDAYS: ['недеља', 'понедељак', 'уторак', 'среда',
      'четвртак', 'петак', 'субота'],
  STANDALONEWEEKDAYS: ['недеља', 'понедељак', 'уторак',
      'среда', 'четвртак', 'петак', 'субота'],
  SHORTWEEKDAYS: ['нед', 'пон', 'уто', 'сре', 'чет', 'пет',
      'суб'],
  STANDALONESHORTWEEKDAYS: ['нед', 'пон', 'уто', 'сре', 'чет',
      'пет', 'суб'],
  NARROWWEEKDAYS: ['н', 'п', 'у', 'с', 'ч', 'п', 'с'],
  STANDALONENARROWWEEKDAYS: ['н', 'п', 'у', 'с', 'ч', 'п', 'с'],
  SHORTQUARTERS: ['К1', 'К2', 'К3', 'К4'],
  QUARTERS: ['Прво тромесечје', 'Друго тромесечје',
      'Треће тромесечје', 'Четврто тромесечје'],
  AMPMS: ['пре подне', 'поподне'],
  DATEFORMATS: ['EEEE, dd. MMMM y.', 'dd. MMMM y.', 'dd.MM.y.', 'd.M.yy.'],
  TIMEFORMATS: ['HH.mm.ss zzzz', 'HH.mm.ss z', 'HH.mm.ss', 'HH.mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale sv.
 */
goog.i18n.DateTimeSymbols_sv = {
  ERAS: ['f.Kr.', 'e.Kr.'],
  ERANAMES: ['före Kristus', 'efter Kristus'],
  NARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O',
      'N', 'D'],
  MONTHS: ['januari', 'februari', 'mars', 'april', 'maj', 'juni', 'juli',
      'augusti', 'september', 'oktober', 'november', 'december'],
  STANDALONEMONTHS: ['januari', 'februari', 'mars', 'april', 'maj', 'juni',
      'juli', 'augusti', 'september', 'oktober', 'november', 'december'],
  SHORTMONTHS: ['jan', 'feb', 'mar', 'apr', 'maj', 'jun', 'jul', 'aug', 'sep',
      'okt', 'nov', 'dec'],
  STANDALONESHORTMONTHS: ['jan', 'feb', 'mar', 'apr', 'maj', 'jun', 'jul',
      'aug', 'sep', 'okt', 'nov', 'dec'],
  WEEKDAYS: ['söndag', 'måndag', 'tisdag', 'onsdag', 'torsdag', 'fredag',
      'lördag'],
  STANDALONEWEEKDAYS: ['söndag', 'måndag', 'tisdag', 'onsdag', 'torsdag',
      'fredag', 'lördag'],
  SHORTWEEKDAYS: ['sön', 'mån', 'tis', 'ons', 'tors', 'fre', 'lör'],
  STANDALONESHORTWEEKDAYS: ['sön', 'mån', 'tis', 'ons', 'tor', 'fre', 'lör'],
  NARROWWEEKDAYS: ['S', 'M', 'T', 'O', 'T', 'F', 'L'],
  STANDALONENARROWWEEKDAYS: ['S', 'M', 'T', 'O', 'T', 'F', 'L'],
  SHORTQUARTERS: ['K1', 'K2', 'K3', 'K4'],
  QUARTERS: ['1:a kvartalet', '2:a kvartalet', '3:e kvartalet',
      '4:e kvartalet'],
  AMPMS: ['fm', 'em'],
  DATEFORMATS: ['EEEE\'en\' \'den\' d:\'e\' MMMM y', 'd MMMM y', 'd MMM y',
      'yyyy-MM-dd'],
  TIMEFORMATS: ['\'kl\'. HH:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale sw.
 */
goog.i18n.DateTimeSymbols_sw = {
  ERAS: ['KK', 'BK'],
  ERANAMES: ['Kabla ya Kristo', 'Baada ya Kristo'],
  NARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O',
      'N', 'D'],
  MONTHS: ['Januari', 'Februari', 'Machi', 'Aprili', 'Mei', 'Juni', 'Julai',
      'Agosti', 'Septemba', 'Oktoba', 'Novemba', 'Desemba'],
  STANDALONEMONTHS: ['Januari', 'Februari', 'Machi', 'Aprili', 'Mei', 'Juni',
      'Julai', 'Agosti', 'Septemba', 'Oktoba', 'Novemba', 'Desemba'],
  SHORTMONTHS: ['Jan', 'Feb', 'Mac', 'Apr', 'Mei', 'Jun', 'Jul', 'Ago', 'Sep',
      'Okt', 'Nov', 'Des'],
  STANDALONESHORTMONTHS: ['Jan', 'Feb', 'Mac', 'Apr', 'Mei', 'Jun', 'Jul',
      'Ago', 'Sep', 'Okt', 'Nov', 'Des'],
  WEEKDAYS: ['Jumapili', 'Jumatatu', 'Jumanne', 'Jumatano', 'Alhamisi',
      'Ijumaa', 'Jumamosi'],
  STANDALONEWEEKDAYS: ['Jumapili', 'Jumatatu', 'Jumanne', 'Jumatano',
      'Alhamisi', 'Ijumaa', 'Jumamosi'],
  SHORTWEEKDAYS: ['J2', 'J3', 'J4', 'J5', 'Alh', 'Ij', 'J1'],
  STANDALONESHORTWEEKDAYS: ['J2', 'J3', 'J4', 'J5', 'Alh', 'Ij', 'J1'],
  NARROWWEEKDAYS: ['2', '3', '4', '5', 'A', 'I', '1'],
  STANDALONENARROWWEEKDAYS: ['2', '3', '4', '5', 'A', 'I', '1'],
  SHORTQUARTERS: ['R1', 'R2', 'R3', 'R4'],
  QUARTERS: ['Robo 1', 'Robo 2', 'Robo 3', 'Robo 4'],
  AMPMS: ['asubuhi', 'alasiri'],
  DATEFORMATS: ['EEEE, d MMMM y', 'd MMMM y', 'd MMM y', 'dd/MM/yyyy'],
  TIMEFORMATS: ['h:mm:ss a zzzz', 'h:mm:ss a z', 'h:mm:ss a', 'h:mm a'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale ta.
 */
goog.i18n.DateTimeSymbols_ta = {
  ERAS: ['கி.மு.', 'கி.பி.'],
  ERANAMES: ['கிறிஸ்துவுக்கு முன்',
      'அனோ டோமினி'],
  NARROWMONTHS: ['ஜ', 'பி', 'மா', 'ஏ', 'மே', 'ஜூ', 'ஜூ',
      'ஆ', 'செ', 'அ', 'ந', 'டி'],
  STANDALONENARROWMONTHS: ['ஜ', 'பி', 'மா', 'ஏ', 'மே', 'ஜூ',
      'ஜூ', 'ஆ', 'செ', 'அ', 'ந', 'டி'],
  MONTHS: ['ஜனவரி', 'பிப்ரவரி', 'மார்ச்',
      'ஏப்ரல்', 'மே', 'ஜூன்', 'ஜூலை',
      'ஆகஸ்ட்', 'செப்டம்பர்',
      'அக்டோபர்', 'நவம்பர்',
      'டிசம்பர்'],
  STANDALONEMONTHS: ['ஜனவரி', 'பிப்ரவரி',
      'மார்ச்', 'ஏப்ரல்', 'மே', 'ஜூன்',
      'ஜூலை', 'ஆகஸ்டு', 'செப்டம்பர்',
      'அக்டோபர்', 'நவம்பர்',
      'டிசம்பர்'],
  SHORTMONTHS: ['ஜன.', 'பிப்.', 'மார்.', 'ஏப்.',
      'மே', 'ஜூன்', 'ஜூலை', 'ஆக.', 'செப்.',
      'அக்.', 'நவ.', 'டிச.'],
  STANDALONESHORTMONTHS: ['ஜன.', 'பிப்.', 'மார்.',
      'ஏப்.', 'மே', 'ஜூன்', 'ஜூலை', 'ஆக.',
      'செப்.', 'அக்.', 'நவ.', 'டிச.'],
  WEEKDAYS: ['ஞாயிறு', 'திங்கள்',
      'செவ்வாய்', 'புதன்', 'வியாழன்',
      'வெள்ளி', 'சனி'],
  STANDALONEWEEKDAYS: ['ஞாயிறு', 'திங்கள்',
      'செவ்வாய்', 'புதன்', 'வியாழன்',
      'வெள்ளி', 'சனி'],
  SHORTWEEKDAYS: ['ஞா', 'தி', 'செ', 'பு', 'வி', 'வெ',
      'ச'],
  STANDALONESHORTWEEKDAYS: ['ஞா', 'தி', 'செ', 'பு', 'வி',
      'வெ', 'ச'],
  NARROWWEEKDAYS: ['ஞா', 'தி', 'செ', 'பு', 'வி', 'வெ',
      'ச'],
  STANDALONENARROWWEEKDAYS: ['ஞா', 'தி', 'செ', 'பு', 'வி',
      'வெ', 'ச'],
  SHORTQUARTERS: ['காலாண்டு1', 'காலாண்டு2',
      'காலாண்டு3', 'காலாண்டு4'],
  QUARTERS: ['முதல் காலாண்டு',
      'இரண்டாம் காலாண்டு',
      'மூன்றாம் காலாண்டு',
      'நான்காம் காலாண்டு'],
  AMPMS: ['முற்பகல்', 'பிற்பகல்'],
  DATEFORMATS: ['EEEE, d MMMM, y', 'd MMMM, y', 'd MMM, y', 'd-M-yy'],
  TIMEFORMATS: ['h:mm:ss a zzzz', 'h:mm:ss a z', 'h:mm:ss a', 'h:mm a'],
  FIRSTDAYOFWEEK: 6,
  WEEKENDRANGE: [6, 6],
  FIRSTWEEKCUTOFFDAY: 2
};


/**
 * Date/time formatting symbols for locale te.
 */
goog.i18n.DateTimeSymbols_te = {
  ERAS: ['ఈసాపూర్వ.', 'సన్.'],
  ERANAMES: ['ఈసాపూర్వ.', 'సన్.'],
  NARROWMONTHS: ['జ', 'ఫి', 'మా', 'ఏ', 'మె', 'జు', 'జు',
      'ఆ', 'సె', 'అ', 'న', 'డి'],
  STANDALONENARROWMONTHS: ['జ', 'ఫి', 'మ', 'ఎ', 'మె', 'జు',
      'జు', 'ఆ', 'సె', 'అ', 'న', 'డి'],
  MONTHS: ['జనవరి', 'ఫిబ్రవరి', 'మార్చి',
      'ఎప్రిల్', 'మే', 'జూన్', 'జూలై',
      'ఆగస్టు', 'సెప్టెంబర్',
      'అక్టోబర్', 'నవంబర్',
      'డిసెంబర్'],
  STANDALONEMONTHS: ['జనవరి', 'ఫిబ్రవరి',
      'మార్చి', 'ఎప్రిల్', 'మే', 'జూన్',
      'జూలై', 'ఆగస్టు', 'సెప్టెంబర్',
      'అక్టోబర్', 'నవంబర్',
      'డిసెంబర్'],
  SHORTMONTHS: ['జన', 'ఫిబ్ర', 'మార్చి',
      'ఏప్రి', 'మే', 'జూన్', 'జూలై',
      'ఆగస్టు', 'సెప్టెంబర్',
      'అక్టోబర్', 'నవంబర్',
      'డిసెంబర్'],
  STANDALONESHORTMONTHS: ['జన', 'ఫిబ్ర', 'మార్చి',
      'ఏప్రి', 'మే', 'జూన్', 'జూలై',
      'ఆగస్టు', 'సెప్టెంబర్',
      'అక్టోబర్', 'నవంబర్',
      'డిసెంబర్'],
  WEEKDAYS: ['ఆదివారం', 'సోమవారం',
      'మంగళవారం', 'బుధవారం',
      'గురువారం', 'శుక్రవారం',
      'శనివారం'],
  STANDALONEWEEKDAYS: ['ఆదివారం', 'సోమవారం',
      'మంగళవారం', 'బుధవారం',
      'గురువారం', 'శుక్రవారం',
      'శనివారం'],
  SHORTWEEKDAYS: ['ఆది', 'సోమ', 'మంగళ', 'బుధ',
      'గురు', 'శుక్ర', 'శని'],
  STANDALONESHORTWEEKDAYS: ['ఆది', 'సోమ', 'మంగళ',
      'బుధ', 'గురు', 'శుక్ర', 'శని'],
  NARROWWEEKDAYS: ['ఆ', 'సో', 'మ', 'భు', 'గు', 'శు', 'శ'],
  STANDALONENARROWWEEKDAYS: ['ఆ', 'సో', 'మ', 'భు', 'గు',
      'శు', 'శ'],
  SHORTQUARTERS: ['ఒకటి 1', 'రెండు 2', 'మూడు 3',
      'నాలుగు 4'],
  QUARTERS: ['ఒకటి 1', 'రెండు 2', 'మూడు 3',
      'నాలుగు 4'],
  AMPMS: ['పూర్వాహ్నం', 'అపరాహ్నం'],
  DATEFORMATS: ['EEEE d MMMM y', 'd MMMM y', 'd MMM y', 'dd-MM-yy'],
  TIMEFORMATS: ['h:mm:ss a zzzz', 'h:mm:ss a z', 'h:mm:ss a', 'h:mm a'],
  FIRSTDAYOFWEEK: 6,
  WEEKENDRANGE: [6, 6],
  FIRSTWEEKCUTOFFDAY: 2
};


/**
 * Date/time formatting symbols for locale th.
 */
goog.i18n.DateTimeSymbols_th = {
  ERAS: ['ปีก่อน ค.ศ.', 'ค.ศ.'],
  ERANAMES: ['ปีก่อนคริสต์ศักราช',
      'คริสต์ศักราช'],
  NARROWMONTHS: ['ม.ค.', 'ก.พ.', 'มี.ค.', 'เม.ย.',
      'พ.ค.', 'มิ.ย', 'ก.ค.', 'ส.ค.', 'ก.ย.', 'ต.ค.',
      'พ.ย.', 'ธ.ค.'],
  STANDALONENARROWMONTHS: ['ม.ค.', 'ก.พ.', 'มี.ค.', 'เม.ย.',
      'พ.ค.', 'มิ.ย.', 'ก.ค.', 'ส.ค.', 'ก.ย.', 'ต.ค.',
      'พ.ย.', 'ธ.ค.'],
  MONTHS: ['มกราคม', 'กุมภาพันธ์',
      'มีนาคม', 'เมษายน', 'พฤษภาคม',
      'มิถุนายน', 'กรกฎาคม',
      'สิงหาคม', 'กันยายน', 'ตุลาคม',
      'พฤศจิกายน', 'ธันวาคม'],
  STANDALONEMONTHS: ['มกราคม', 'กุมภาพันธ์',
      'มีนาคม', 'เมษายน', 'พฤษภาคม',
      'มิถุนายน', 'กรกฎาคม',
      'สิงหาคม', 'กันยายน', 'ตุลาคม',
      'พฤศจิกายน', 'ธันวาคม'],
  SHORTMONTHS: ['ม.ค.', 'ก.พ.', 'มี.ค.', 'เม.ย.',
      'พ.ค.', 'มิ.ย.', 'ก.ค.', 'ส.ค.', 'ก.ย.', 'ต.ค.',
      'พ.ย.', 'ธ.ค.'],
  STANDALONESHORTMONTHS: ['ม.ค.', 'ก.พ.', 'มี.ค.', 'เม.ย.',
      'พ.ค.', 'มิ.ย.', 'ก.ค.', 'ส.ค.', 'ก.ย.', 'ต.ค.',
      'พ.ย.', 'ธ.ค.'],
  WEEKDAYS: ['วันอาทิตย์', 'วันจันทร์',
      'วันอังคาร', 'วันพุธ',
      'วันพฤหัสบดี', 'วันศุกร์',
      'วันเสาร์'],
  STANDALONEWEEKDAYS: ['วันอาทิตย์',
      'วันจันทร์', 'วันอังคาร',
      'วันพุธ', 'วันพฤหัสบดี',
      'วันศุกร์', 'วันเสาร์'],
  SHORTWEEKDAYS: ['อา.', 'จ.', 'อ.', 'พ.', 'พฤ.', 'ศ.', 'ส.'],
  STANDALONESHORTWEEKDAYS: ['อา.', 'จ.', 'อ.', 'พ.', 'พฤ.',
      'ศ.', 'ส.'],
  NARROWWEEKDAYS: ['อ', 'จ', 'อ', 'พ', 'พ', 'ศ', 'ส'],
  STANDALONENARROWWEEKDAYS: ['อ', 'จ', 'อ', 'พ', 'พ', 'ศ', 'ส'],
  SHORTQUARTERS: ['Q1', 'Q2', 'Q3', 'Q4'],
  QUARTERS: ['ไตรมาส 1', 'ไตรมาส 2',
      'ไตรมาส 3', 'ไตรมาส 4'],
  AMPMS: ['ก่อนเที่ยง', 'หลังเที่ยง'],
  DATEFORMATS: ['EEEEที่ d MMMM G y', 'd MMMM y', 'd MMM y', 'd/M/yyyy'],
  TIMEFORMATS: [
      'H นาฬิกา m นาที ss วินาที zzzz',
      'H นาฬิกา m นาที ss วินาที z', 'H:mm:ss',
      'H:mm'],
  FIRSTDAYOFWEEK: 6,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 2
};


/**
 * Date/time formatting symbols for locale tl.
 */
goog.i18n.DateTimeSymbols_tl = {
  ERAS: ['BC', 'AD'],
  ERANAMES: ['BC', 'AD'],
  NARROWMONTHS: ['E', 'P', 'M', 'A', 'M', 'H', 'H', 'A', 'S', 'O', 'N', 'D'],
  STANDALONENARROWMONTHS: ['E', 'P', 'M', 'A', 'M', 'H', 'H', 'A', 'S', 'O',
      'N', 'D'],
  MONTHS: ['Enero', 'Pebrero', 'Marso', 'Abril', 'Mayo', 'Hunyo', 'Hulyo',
      'Agosto', 'Setyembre', 'Oktubre', 'Nobyembre', 'Disyembre'],
  STANDALONEMONTHS: ['Enero', 'Pebrero', 'Marso', 'Abril', 'Mayo', 'Hunyo',
      'Hulyo', 'Agosto', 'Setyembre', 'Oktubre', 'Nobyembre', 'Disyembre'],
  SHORTMONTHS: ['Ene', 'Peb', 'Mar', 'Abr', 'May', 'Hun', 'Hul', 'Ago', 'Set',
      'Okt', 'Nob', 'Dis'],
  STANDALONESHORTMONTHS: ['Ene', 'Peb', 'Mar', 'Abr', 'May', 'Hun', 'Hul',
      'Ago', 'Set', 'Okt', 'Nob', 'Dis'],
  WEEKDAYS: ['Linggo', 'Lunes', 'Martes', 'Miyerkules', 'Huwebes', 'Biyernes',
      'Sabado'],
  STANDALONEWEEKDAYS: ['Linggo', 'Lunes', 'Martes', 'Miyerkules', 'Huwebes',
      'Biyernes', 'Sabado'],
  SHORTWEEKDAYS: ['Lin', 'Lun', 'Mar', 'Mye', 'Huw', 'Bye', 'Sab'],
  STANDALONESHORTWEEKDAYS: ['Lin', 'Lun', 'Mar', 'Miy', 'Huw', 'Biy', 'Sab'],
  NARROWWEEKDAYS: ['L', 'L', 'M', 'M', 'H', 'B', 'S'],
  STANDALONENARROWWEEKDAYS: ['L', 'L', 'M', 'M', 'H', 'B', 'S'],
  SHORTQUARTERS: ['Q1', 'Q2', 'Q3', 'Q4'],
  QUARTERS: ['ika-1 sangkapat', 'ika-2 sangkapat', 'ika-3 quarter',
      'ika-4 na quarter'],
  AMPMS: ['AM', 'PM'],
  DATEFORMATS: ['EEEE, MMMM dd y', 'MMMM d, y', 'MMM d, y', 'M/d/yy'],
  TIMEFORMATS: ['HH:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 6,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 2
};


/**
 * Date/time formatting symbols for locale tr.
 */
goog.i18n.DateTimeSymbols_tr = {
  ERAS: ['MÖ', 'MS'],
  ERANAMES: ['Milattan Önce', 'Milattan Sonra'],
  NARROWMONTHS: ['O', 'Ş', 'M', 'N', 'M', 'H', 'T', 'A', 'E', 'E', 'K', 'A'],
  STANDALONENARROWMONTHS: ['O', 'Ş', 'M', 'N', 'M', 'H', 'T', 'A', 'E', 'E',
      'K', 'A'],
  MONTHS: ['Ocak', 'Şubat', 'Mart', 'Nisan', 'Mayıs', 'Haziran', 'Temmuz',
      'Ağustos', 'Eylül', 'Ekim', 'Kasım', 'Aralık'],
  STANDALONEMONTHS: ['Ocak', 'Şubat', 'Mart', 'Nisan', 'Mayıs', 'Haziran',
      'Temmuz', 'Ağustos', 'Eylül', 'Ekim', 'Kasım', 'Aralık'],
  SHORTMONTHS: ['Oca', 'Şub', 'Mar', 'Nis', 'May', 'Haz', 'Tem', 'Ağu', 'Eyl',
      'Eki', 'Kas', 'Ara'],
  STANDALONESHORTMONTHS: ['Oca', 'Şub', 'Mar', 'Nis', 'May', 'Haz', 'Tem',
      'Ağu', 'Eyl', 'Eki', 'Kas', 'Ara'],
  WEEKDAYS: ['Pazar', 'Pazartesi', 'Salı', 'Çarşamba', 'Perşembe', 'Cuma',
      'Cumartesi'],
  STANDALONEWEEKDAYS: ['Pazar', 'Pazartesi', 'Salı', 'Çarşamba', 'Perşembe',
      'Cuma', 'Cumartesi'],
  SHORTWEEKDAYS: ['Paz', 'Pzt', 'Sal', 'Çar', 'Per', 'Cum', 'Cmt'],
  STANDALONESHORTWEEKDAYS: ['Paz', 'Pzt', 'Sal', 'Çar', 'Per', 'Cum', 'Cmt'],
  NARROWWEEKDAYS: ['P', 'P', 'S', 'Ç', 'P', 'C', 'C'],
  STANDALONENARROWWEEKDAYS: ['P', 'P', 'S', 'Ç', 'P', 'C', 'C'],
  SHORTQUARTERS: ['Ç1', 'Ç2', 'Ç3', 'Ç4'],
  QUARTERS: ['1. çeyrek', '2. çeyrek', '3. çeyrek', '4. çeyrek'],
  AMPMS: ['AM', 'PM'],
  DATEFORMATS: ['d MMMM y EEEE', 'd MMMM y', 'd MMM y', 'dd MM yyyy'],
  TIMEFORMATS: ['HH:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale uk.
 */
goog.i18n.DateTimeSymbols_uk = {
  ERAS: ['до н.е.', 'н.е.'],
  ERANAMES: ['до нашої ери', 'нашої ери'],
  NARROWMONTHS: ['С', 'Л', 'Б', 'К', 'Т', 'Ч', 'Л', 'С', 'В', 'Ж',
      'Л', 'Г'],
  STANDALONENARROWMONTHS: ['С', 'Л', 'Б', 'К', 'Т', 'Ч', 'Л', 'С', 'В',
      'Ж', 'Л', 'Г'],
  MONTHS: ['січня', 'лютого', 'березня', 'квітня',
      'травня', 'червня', 'липня', 'серпня',
      'вересня', 'жовтня', 'листопада', 'грудня'],
  STANDALONEMONTHS: ['Січень', 'Лютий', 'Березень',
      'Квітень', 'Травень', 'Червень', 'Липень',
      'Серпень', 'Вересень', 'Жовтень',
      'Листопад', 'Грудень'],
  SHORTMONTHS: ['січ.', 'лют.', 'бер.', 'квіт.', 'трав.',
      'черв.', 'лип.', 'серп.', 'вер.', 'жовт.', 'лист.',
      'груд.'],
  STANDALONESHORTMONTHS: ['Січ', 'Лют', 'Бер', 'Кві', 'Тра',
      'Чер', 'Лип', 'Сер', 'Вер', 'Жов', 'Лис', 'Гру'],
  WEEKDAYS: ['Неділя', 'Понеділок', 'Вівторок',
      'Середа', 'Четвер', 'Пʼятниця', 'Субота'],
  STANDALONEWEEKDAYS: ['Неділя', 'Понеділок', 'Вівторок',
      'Середа', 'Четвер', 'Пʼятниця', 'Субота'],
  SHORTWEEKDAYS: ['Нд', 'Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб'],
  STANDALONESHORTWEEKDAYS: ['Нд', 'Пн', 'Вт', 'Ср', 'Чт', 'Пт',
      'Сб'],
  NARROWWEEKDAYS: ['Н', 'П', 'В', 'С', 'Ч', 'П', 'С'],
  STANDALONENARROWWEEKDAYS: ['Н', 'П', 'В', 'С', 'Ч', 'П', 'С'],
  SHORTQUARTERS: ['I кв.', 'II кв.', 'III кв.', 'IV кв.'],
  QUARTERS: ['I квартал', 'II квартал', 'III квартал',
      'IV квартал'],
  AMPMS: ['дп', 'пп'],
  DATEFORMATS: ['EEEE, d MMMM y \'р\'.', 'd MMMM y \'р\'.', 'd MMM y',
      'dd.MM.yy'],
  TIMEFORMATS: ['HH:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale ur.
 */
goog.i18n.DateTimeSymbols_ur = {
  ERAS: ['ق م', 'عيسوی سن'],
  ERANAMES: ['قبل مسيح', 'عيسوی سن'],
  NARROWMONTHS: ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12'],
  STANDALONENARROWMONTHS: ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10',
      '11', '12'],
  MONTHS: ['جنوری', 'فروری', 'مار چ', 'اپريل', 'مئ',
      'جون', 'جولائ', 'اگست', 'ستمبر', 'اکتوبر',
      'نومبر', 'دسمبر'],
  STANDALONEMONTHS: ['جنوری', 'فروری', 'مار چ', 'اپريل',
      'مئ', 'جون', 'جولائ', 'اگست', 'ستمبر', 'اکتوبر',
      'نومبر', 'دسمبر'],
  SHORTMONTHS: ['جنوری', 'فروری', 'مار چ', 'اپريل', 'مئ',
      'جون', 'جولائ', 'اگست', 'ستمبر', 'اکتوبر',
      'نومبر', 'دسمبر'],
  STANDALONESHORTMONTHS: ['جنوری', 'فروری', 'مار چ', 'اپريل',
      'مئ', 'جون', 'جولائ', 'اگست', 'ستمبر', 'اکتوبر',
      'نومبر', 'دسمبر'],
  WEEKDAYS: ['اتوار', 'پير', 'منگل', 'بده', 'جمعرات',
      'جمعہ', 'ہفتہ'],
  STANDALONEWEEKDAYS: ['اتوار', 'پير', 'منگل', 'بده',
      'جمعرات', 'جمعہ', 'ہفتہ'],
  SHORTWEEKDAYS: ['اتوار', 'پير', 'منگل', 'بده', 'جمعرات',
      'جمعہ', 'ہفتہ'],
  STANDALONESHORTWEEKDAYS: ['اتوار', 'پير', 'منگل', 'بده',
      'جمعرات', 'جمعہ', 'ہفتہ'],
  NARROWWEEKDAYS: ['1', '2', '3', '4', '5', '6', '7'],
  STANDALONENARROWWEEKDAYS: ['1', '2', '3', '4', '5', '6', '7'],
  SHORTQUARTERS: ['پہلی سہ ماہی', 'دوسری سہ ماہی',
      'تيسری سہ ماہی', 'چوتهی سہ ماہی'],
  QUARTERS: ['پہلی سہ ماہی', 'دوسری سہ ماہی',
      'تيسری سہ ماہی', 'چوتهی سہ ماہی'],
  AMPMS: ['دن', 'رات'],
  DATEFORMATS: ['EEEE؍ d؍ MMMM y', 'd؍ MMMM y', 'd؍ MMM y', 'd/M/yy'],
  TIMEFORMATS: ['h:mm:ss a zzzz', 'h:mm:ss a z', 'h:mm:ss a', 'h:mm a'],
  FIRSTDAYOFWEEK: 6,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 2
};


/**
 * Date/time formatting symbols for locale vi.
 */
goog.i18n.DateTimeSymbols_vi = {
  ERAS: ['tr. CN', 'sau CN'],
  ERANAMES: ['tr. CN', 'sau CN'],
  NARROWMONTHS: ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12'],
  STANDALONENARROWMONTHS: ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10',
      '11', '12'],
  MONTHS: ['tháng một', 'tháng hai', 'tháng ba', 'tháng tư',
      'tháng năm', 'tháng sáu', 'tháng bảy', 'tháng tám',
      'tháng chín', 'tháng mười', 'tháng mười một',
      'tháng mười hai'],
  STANDALONEMONTHS: ['tháng một', 'tháng hai', 'tháng ba', 'tháng tư',
      'tháng năm', 'tháng sáu', 'tháng bảy', 'tháng tám',
      'tháng chín', 'tháng mười', 'tháng mười một',
      'tháng mười hai'],
  SHORTMONTHS: ['thg 1', 'thg 2', 'thg 3', 'thg 4', 'thg 5', 'thg 6', 'thg 7',
      'thg 8', 'thg 9', 'thg 10', 'thg 11', 'thg 12'],
  STANDALONESHORTMONTHS: ['thg 1', 'thg 2', 'thg 3', 'thg 4', 'thg 5', 'thg 6',
      'thg 7', 'thg 8', 'thg 9', 'thg 10', 'thg 11', 'thg 12'],
  WEEKDAYS: ['Chủ nhật', 'Thứ hai', 'Thứ ba', 'Thứ tư', 'Thứ năm',
      'Thứ sáu', 'Thứ bảy'],
  STANDALONEWEEKDAYS: ['Chủ nhật', 'Thứ hai', 'Thứ ba', 'Thứ tư',
      'Thứ năm', 'Thứ sáu', 'Thứ bảy'],
  SHORTWEEKDAYS: ['CN', 'Th 2', 'Th 3', 'Th 4', 'Th 5', 'Th 6', 'Th 7'],
  STANDALONESHORTWEEKDAYS: ['CN', 'Th 2', 'Th 3', 'Th 4', 'Th 5', 'Th 6',
      'Th 7'],
  NARROWWEEKDAYS: ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'],
  STANDALONENARROWWEEKDAYS: ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'],
  SHORTQUARTERS: ['Q1', 'Q2', 'Q3', 'Q4'],
  QUARTERS: ['Quý 1', 'Quý 2', 'Quý 3', 'Quý 4'],
  AMPMS: ['SA', 'CH'],
  DATEFORMATS: ['EEEE, \'ngày\' dd MMMM \'năm\' y',
      '\'Ngày\' dd \'tháng\' M \'năm\' y', 'dd-MM-yyyy', 'dd/MM/yyyy'],
  TIMEFORMATS: ['HH:mm:ss zzzz', 'HH:mm:ss z', 'HH:mm:ss', 'HH:mm'],
  FIRSTDAYOFWEEK: 0,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 3
};


/**
 * Date/time formatting symbols for locale zh.
 */
goog.i18n.DateTimeSymbols_zh = {
  ERAS: ['公元前', '公元'],
  ERANAMES: ['公元前', '公元'],
  NARROWMONTHS: ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12'],
  STANDALONENARROWMONTHS: ['1月', '2月', '3月', '4月', '5月', '6月',
      '7月', '8月', '9月', '10月', '11月', '12月'],
  MONTHS: ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月',
      '9月', '10月', '11月', '12月'],
  STANDALONEMONTHS: ['一月', '二月', '三月', '四月', '五月', '六月',
      '七月', '八月', '九月', '十月', '十一月', '十二月'],
  SHORTMONTHS: ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月',
      '9月', '10月', '11月', '12月'],
  STANDALONESHORTMONTHS: ['一月', '二月', '三月', '四月', '五月',
      '六月', '七月', '八月', '九月', '十月', '十一月',
      '十二月'],
  WEEKDAYS: ['星期日', '星期一', '星期二', '星期三', '星期四',
      '星期五', '星期六'],
  STANDALONEWEEKDAYS: ['星期日', '星期一', '星期二', '星期三',
      '星期四', '星期五', '星期六'],
  SHORTWEEKDAYS: ['周日', '周一', '周二', '周三', '周四', '周五',
      '周六'],
  STANDALONESHORTWEEKDAYS: ['周日', '周一', '周二', '周三', '周四',
      '周五', '周六'],
  NARROWWEEKDAYS: ['日', '一', '二', '三', '四', '五', '六'],
  STANDALONENARROWWEEKDAYS: ['日', '一', '二', '三', '四', '五', '六'],
  SHORTQUARTERS: ['1季', '2季', '3季', '4季'],
  QUARTERS: ['第1季度', '第2季度', '第3季度', '第4季度'],
  AMPMS: ['上午', '下午'],
  DATEFORMATS: ['y年M月d日EEEE', 'y年M月d日', 'yyyy-M-d', 'yy-M-d'],
  TIMEFORMATS: ['zzzzah时mm分ss秒', 'zah时mm分ss秒', 'ah:mm:ss', 'ah:mm'],
  FIRSTDAYOFWEEK: 6,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 2
};


/**
 * Date/time formatting symbols for locale zh_CN.
 */
goog.i18n.DateTimeSymbols_zh_CN = goog.i18n.DateTimeSymbols_zh;


/**
 * Date/time formatting symbols for locale zh_HK.
 */
goog.i18n.DateTimeSymbols_zh_HK = {
  ERAS: ['西元前', '西元'],
  ERANAMES: ['西元前', '西元'],
  NARROWMONTHS: ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12'],
  STANDALONENARROWMONTHS: ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10',
      '11', '12'],
  MONTHS: ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月',
      '9月', '10月', '11月', '12月'],
  STANDALONEMONTHS: ['一月', '二月', '三月', '四月', '五月', '六月',
      '七月', '八月', '九月', '十月', '十一月', '十二月'],
  SHORTMONTHS: ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月',
      '9月', '10月', '11月', '12月'],
  STANDALONESHORTMONTHS: ['1月', '2月', '3月', '4月', '5月', '6月',
      '7月', '8月', '9月', '10月', '11月', '12月'],
  WEEKDAYS: ['星期日', '星期一', '星期二', '星期三', '星期四',
      '星期五', '星期六'],
  STANDALONEWEEKDAYS: ['星期日', '星期一', '星期二', '星期三',
      '星期四', '星期五', '星期六'],
  SHORTWEEKDAYS: ['週日', '週一', '週二', '週三', '週四', '週五',
      '週六'],
  STANDALONESHORTWEEKDAYS: ['周日', '周一', '周二', '周三', '周四',
      '周五', '周六'],
  NARROWWEEKDAYS: ['日', '一', '二', '三', '四', '五', '六'],
  STANDALONENARROWWEEKDAYS: ['日', '一', '二', '三', '四', '五', '六'],
  SHORTQUARTERS: ['1季', '2季', '3季', '4季'],
  QUARTERS: ['第1季', '第2季', '第3季', '第4季'],
  AMPMS: ['上午', '下午'],
  DATEFORMATS: ['y年M月d日EEEE', 'y年M月d日', 'y年M月d日',
      'yy年M月d日'],
  TIMEFORMATS: ['ah:mm:ss [zzzz]', 'ah:mm:ss [z]', 'ahh:mm:ss', 'ah:mm'],
  FIRSTDAYOFWEEK: 6,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 2
};


/**
 * Date/time formatting symbols for locale zh_TW.
 */
goog.i18n.DateTimeSymbols_zh_TW = {
  ERAS: ['西元前', '西元'],
  ERANAMES: ['西元前', '西元'],
  NARROWMONTHS: ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12'],
  STANDALONENARROWMONTHS: ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10',
      '11', '12'],
  MONTHS: ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月',
      '9月', '10月', '11月', '12月'],
  STANDALONEMONTHS: ['一月', '二月', '三月', '四月', '五月', '六月',
      '七月', '八月', '九月', '十月', '十一月', '十二月'],
  SHORTMONTHS: ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月',
      '9月', '10月', '11月', '12月'],
  STANDALONESHORTMONTHS: ['1月', '2月', '3月', '4月', '5月', '6月',
      '7月', '8月', '9月', '10月', '11月', '12月'],
  WEEKDAYS: ['星期日', '星期一', '星期二', '星期三', '星期四',
      '星期五', '星期六'],
  STANDALONEWEEKDAYS: ['星期日', '星期一', '星期二', '星期三',
      '星期四', '星期五', '星期六'],
  SHORTWEEKDAYS: ['週日', '週一', '週二', '週三', '週四', '週五',
      '週六'],
  STANDALONESHORTWEEKDAYS: ['周日', '周一', '周二', '周三', '周四',
      '周五', '周六'],
  NARROWWEEKDAYS: ['日', '一', '二', '三', '四', '五', '六'],
  STANDALONENARROWWEEKDAYS: ['日', '一', '二', '三', '四', '五', '六'],
  SHORTQUARTERS: ['1季', '2季', '3季', '4季'],
  QUARTERS: ['第1季', '第2季', '第3季', '第4季'],
  AMPMS: ['上午', '下午'],
  DATEFORMATS: ['y年M月d日EEEE', 'y年M月d日', 'yyyy/M/d', 'yy/M/d'],
  TIMEFORMATS: ['zzzzah時mm分ss秒', 'zah時mm分ss秒', 'ah:mm:ss', 'ah:mm'],
  FIRSTDAYOFWEEK: 6,
  WEEKENDRANGE: [5, 6],
  FIRSTWEEKCUTOFFDAY: 2
};


/**
 * Selected date/time formatting symbols by locale.
 * "switch" statement won't work here. JsCompiler cannot handle it yet.
 */
if (goog.LOCALE == 'am') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_am;
} else if (goog.LOCALE == 'ar') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_ar;
} else if (goog.LOCALE == 'bg') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_bg;
} else if (goog.LOCALE == 'bn') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_bn;
} else if (goog.LOCALE == 'ca') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_ca;
} else if (goog.LOCALE == 'cs') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_cs;
} else if (goog.LOCALE == 'da') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_da;
} else if (goog.LOCALE == 'de') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_de;
} else if (goog.LOCALE == 'de_AT' || goog.LOCALE == 'de-AT') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_de_AT;
} else if (goog.LOCALE == 'de_CH' || goog.LOCALE == 'de-CH') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_de;
} else if (goog.LOCALE == 'el') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_el;
} else if (goog.LOCALE == 'en') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_en;
} else if (goog.LOCALE == 'en_AU' || goog.LOCALE == 'en-AU') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_en_AU;
} else if (goog.LOCALE == 'en_GB' || goog.LOCALE == 'en-GB') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_en_GB;
} else if (goog.LOCALE == 'en_IE' || goog.LOCALE == 'en-IE') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_en_IE;
} else if (goog.LOCALE == 'en_IN' || goog.LOCALE == 'en-IN') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_en_IN;
} else if (goog.LOCALE == 'en_SG' || goog.LOCALE == 'en-SG') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_en_SG;
} else if (goog.LOCALE == 'en_US' || goog.LOCALE == 'en-US') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_en;
} else if (goog.LOCALE == 'en_ZA' || goog.LOCALE == 'en-ZA') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_en_ZA;
} else if (goog.LOCALE == 'es') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_es;
} else if (goog.LOCALE == 'es_419' || goog.LOCALE == 'es-419') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_es;
} else if (goog.LOCALE == 'et') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_et;
} else if (goog.LOCALE == 'eu') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_eu;
} else if (goog.LOCALE == 'fa') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_fa;
} else if (goog.LOCALE == 'fi') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_fi;
} else if (goog.LOCALE == 'fil') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_fil;
} else if (goog.LOCALE == 'fr') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_fr;
} else if (goog.LOCALE == 'fr_CA' || goog.LOCALE == 'fr-CA') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_fr_CA;
} else if (goog.LOCALE == 'gl') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_gl;
} else if (goog.LOCALE == 'gsw') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_gsw;
} else if (goog.LOCALE == 'gu') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_gu;
} else if (goog.LOCALE == 'he') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_he;
} else if (goog.LOCALE == 'hi') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_hi;
} else if (goog.LOCALE == 'hr') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_hr;
} else if (goog.LOCALE == 'hu') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_hu;
} else if (goog.LOCALE == 'id') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_id;
} else if (goog.LOCALE == 'in') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_in;
} else if (goog.LOCALE == 'is') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_is;
} else if (goog.LOCALE == 'it') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_it;
} else if (goog.LOCALE == 'iw') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_iw;
} else if (goog.LOCALE == 'ja') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_ja;
} else if (goog.LOCALE == 'kn') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_kn;
} else if (goog.LOCALE == 'ko') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_ko;
} else if (goog.LOCALE == 'ln') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_ln;
} else if (goog.LOCALE == 'lt') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_lt;
} else if (goog.LOCALE == 'lv') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_lv;
} else if (goog.LOCALE == 'ml') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_ml;
} else if (goog.LOCALE == 'mr') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_mr;
} else if (goog.LOCALE == 'ms') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_ms;
} else if (goog.LOCALE == 'mt') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_mt;
} else if (goog.LOCALE == 'nl') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_nl;
} else if (goog.LOCALE == 'no') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_no;
} else if (goog.LOCALE == 'or') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_or;
} else if (goog.LOCALE == 'pl') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_pl;
} else if (goog.LOCALE == 'pt') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_pt;
} else if (goog.LOCALE == 'pt_BR' || goog.LOCALE == 'pt-BR') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_pt;
} else if (goog.LOCALE == 'pt_PT' || goog.LOCALE == 'pt-PT') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_pt_PT;
} else if (goog.LOCALE == 'ro') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_ro;
} else if (goog.LOCALE == 'ru') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_ru;
} else if (goog.LOCALE == 'sk') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_sk;
} else if (goog.LOCALE == 'sl') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_sl;
} else if (goog.LOCALE == 'sq') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_sq;
} else if (goog.LOCALE == 'sr') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_sr;
} else if (goog.LOCALE == 'sv') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_sv;
} else if (goog.LOCALE == 'sw') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_sw;
} else if (goog.LOCALE == 'ta') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_ta;
} else if (goog.LOCALE == 'te') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_te;
} else if (goog.LOCALE == 'th') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_th;
} else if (goog.LOCALE == 'tl') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_tl;
} else if (goog.LOCALE == 'tr') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_tr;
} else if (goog.LOCALE == 'uk') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_uk;
} else if (goog.LOCALE == 'ur') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_ur;
} else if (goog.LOCALE == 'vi') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_vi;
} else if (goog.LOCALE == 'zh') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_zh;
} else if (goog.LOCALE == 'zh_CN' || goog.LOCALE == 'zh-CN') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_zh;
} else if (goog.LOCALE == 'zh_HK' || goog.LOCALE == 'zh-HK') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_zh_HK;
} else if (goog.LOCALE == 'zh_TW' || goog.LOCALE == 'zh-TW') {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_zh_TW;
} else {
  goog.i18n.DateTimeSymbols = goog.i18n.DateTimeSymbols_en;
}


//javascript/closure/date/datelike.js
// Copyright 2010 The Closure Library Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @fileoverview Typedefs for working with dates.
 *
 * @author nicksantos@google.com (Nick Santos)
 */

goog.provide('goog.date.DateLike');


/**
 * @typedef {(Date|goog.date.Date)}
 */
goog.date.DateLike;

//javascript/closure/date/date.js
// Copyright 2006 The Closure Library Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @fileoverview Functions and objects for date representation and manipulation.
 *
 * @author eae@google.com (Emil A Eklund)
 * @author pallosp@google.com (Peter Pallos)
 */

goog.provide('goog.date');
goog.provide('goog.date.Date');
goog.provide('goog.date.DateTime');
goog.provide('goog.date.Interval');
goog.provide('goog.date.month');
goog.provide('goog.date.weekDay');

goog.require('goog.asserts');
goog.require('goog.date.DateLike');
goog.require('goog.i18n.DateTimeSymbols');
goog.require('goog.string');


/**
 * Constants for weekdays.
 * @enum {number}
 */
goog.date.weekDay = {
  MON: 0,
  TUE: 1,
  WED: 2,
  THU: 3,
  FRI: 4,
  SAT: 5,
  SUN: 6
};


/**
 * Constants for months.
 * @enum {number}
 */
goog.date.month = {
  JAN: 0,
  FEB: 1,
  MAR: 2,
  APR: 3,
  MAY: 4,
  JUN: 5,
  JUL: 6,
  AUG: 7,
  SEP: 8,
  OCT: 9,
  NOV: 10,
  DEC: 11
};


/**
 * Formats a month/year string.
 * Example: "January 2008"
 *
 * @param {string} monthName The month name to use in the result.
 * @param {number} yearNum The numeric year to use in the result.
 * @return {string} A formatted month/year string.
 */
goog.date.formatMonthAndYear = function(monthName, yearNum) {
  /** @desc Month/year format given the month name and the numeric year. */
  var MSG_MONTH_AND_YEAR = goog.getMsg(
      '{$monthName} {$yearNum}',
      { 'monthName' : monthName, 'yearNum' : yearNum });
  return MSG_MONTH_AND_YEAR;
};


/**
 * Regular expression for splitting date parts from ISO 8601 styled string.
 * Examples: '20060210' or '2005-02-22' or '20050222' or '2005-08'
 * or '2005-W22' or '2005W22' or '2005-W22-4', etc.
 * For explanation and more examples, see:
 * {@link http://en.wikipedia.org/wiki/ISO_8601}
 *
 * @type {RegExp}
 * @private
 */
goog.date.splitDateStringRegex_ = new RegExp(
    '^(\\d{4})(?:(?:-?(\\d{2})(?:-?(\\d{2}))?)|' +
    '(?:-?(\\d{3}))|(?:-?W(\\d{2})(?:-?([1-7]))?))?$');


/**
 * Regular expression for splitting time parts from ISO 8601 styled string.
 * Examples: '18:46:39.994' or '184639.994'
 *
 * @type {RegExp}
 * @private
 */
goog.date.splitTimeStringRegex_ =
    /^(\d{2})(?::?(\d{2})(?::?(\d{2})(\.\d+)?)?)?$/;


/**
 * Regular expression for splitting timezone parts from ISO 8601 styled string.
 * Example: The part after the '+' in '18:46:39+07:00'.  Or '09:30Z' (UTC).
 *
 * @type {RegExp}
 * @private
 */
goog.date.splitTimezoneStringRegex_ = /Z|(?:([-+])(\d{2})(?::?(\d{2}))?)$/;


/**
 * Regular expression for splitting duration parts from ISO 8601 styled string.
 * Example: '-P1Y2M3DT4H5M6.7S'
 *
 * @type {RegExp}
 * @private
 */
goog.date.splitDurationRegex_ = new RegExp(
    '^(-)?P(?:(\\d+)Y)?(?:(\\d+)M)?(?:(\\d+)D)?' +
    '(T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+(?:\\.\\d+)?)S)?)?$');


/**
 * Returns whether the given year is a leap year.
 *
 * @param {number} year Year part of date.
 * @return {boolean} Whether the given year is a leap year.
 */
goog.date.isLeapYear = function(year) {
  // Leap year logic; the 4-100-400 rule
  return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
};


/**
 * Returns whether the given year is a long ISO year.
 * See {@link http://www.phys.uu.nl/~vgent/calendar/isocalendar_text3.htm}.
 *
 * @param {number} year Full year part of date.
 * @return {boolean} Whether the given year is a long ISO year.
 */
goog.date.isLongIsoYear = function(year) {
  var n = 5 * year + 12 - 4 * (Math.floor(year / 100) - Math.floor(year / 400));
  n += Math.floor((year - 100) / 400) - Math.floor((year - 102) / 400);
  n += Math.floor((year - 200) / 400) - Math.floor((year - 199) / 400);

  return n % 28 < 5;
};


/**
 * Returns the number of days for a given month.
 *
 * @param {number} year Year part of date.
 * @param {number} month Month part of date.
 * @return {number} The number of days for the given month.
 */
goog.date.getNumberOfDaysInMonth = function(year, month) {
  switch (month) {
    case goog.date.month.FEB:
      return goog.date.isLeapYear(year) ? 29 : 28;
    case goog.date.month.JUN:
    case goog.date.month.SEP:
    case goog.date.month.NOV:
    case goog.date.month.APR:
      return 30;
  }
  return 31;
};


/**
 * Returns true if the 2 dates are in the same day.
 * @param {goog.date.DateLike} date The time to check.
 * @param {goog.date.DateLike=} opt_now The current time.
 * @return {boolean} Whether the dates are on the same day.
 */
goog.date.isSameDay = function(date, opt_now) {
  var now = opt_now || new Date(goog.now());
  return date.getDate() == now.getDate() &&
      goog.date.isSameMonth(date, now);
};


/**
 * Returns true if the 2 dates are in the same month.
 * @param {goog.date.DateLike} date The time to check.
 * @param {goog.date.DateLike=} opt_now The current time.
 * @return {boolean} Whether the dates are in the same calendar month.
 */
goog.date.isSameMonth = function(date, opt_now) {
  var now = opt_now || new Date(goog.now());
  return date.getMonth() == now.getMonth() &&
      goog.date.isSameYear(date, now);
};


/**
 * Returns true if the 2 dates are in the same year.
 * @param {goog.date.DateLike} date The time to check.
 * @param {goog.date.DateLike=} opt_now The current time.
 * @return {boolean} Whether the dates are in the same calendar year.
 */
goog.date.isSameYear = function(date, opt_now) {
  var now = opt_now || new Date(goog.now());
  return date.getFullYear() == now.getFullYear();
};


/**
 * Static function for week number calculation. ISO 8601 implementation.
 *
 * @param {number} year Year part of date.
 * @param {number} month Month part of date (0-11).
 * @param {number} date Day part of date (1-31).
 * @param {number=} opt_weekDay Cut off weekday, defaults to Thursday.
 * @param {number=} opt_firstDayOfWeek First day of the week, defaults to
 *     Monday.
 *     Monday=0, Sunday=6.
 * @return {number} The week number (1-53).
 */
goog.date.getWeekNumber = function(year, month, date, opt_weekDay,
    opt_firstDayOfWeek) {
  var d = new Date(year, month, date);

  // Default to Thursday for cut off as per ISO 8601.
  var cutoff = opt_weekDay || goog.date.weekDay.THU;

  // Default to Monday for first day of the week as per ISO 8601.
  var firstday = opt_firstDayOfWeek || goog.date.weekDay.MON;

  // 1 day in milliseconds.
  var ONE_DAY = 24 * 60 * 60 * 1000;

  // The d.getDay() has to be converted first to ISO weekday (Monday=0).
  var isoday = (d.getDay() + 6) % 7;

  // Position of given day in the picker grid w.r.t. first day of week
  var daypos = (isoday - firstday + 7) % 7;

  // Position of cut off day in the picker grid w.r.t. first day of week
  var cutoffpos = (cutoff - firstday + 7) % 7;

  // Unix timestamp of the midnight of the cutoff day in the week of 'd'.
  // There might be +-1 hour shift in the result due to the daylight saving,
  // but it doesn't affect the year.
  var cutoffSameWeek = d.valueOf() + (cutoffpos - daypos) * ONE_DAY;

  // Unix timestamp of January 1 in the year of 'cutoffSameWeek'.
  var jan1 = new Date(new Date(cutoffSameWeek).getFullYear(), 0, 1).valueOf();

  // Number of week. The round() eliminates the effect of daylight saving.
  return Math.floor(Math.round((cutoffSameWeek - jan1) / ONE_DAY) / 7) + 1;
};


/**
 * Creates a DateTime from a datetime string expressed in ISO 8601 format.
 *
 * @param {string} formatted A date or datetime expressed in ISO 8601 format.
 * @return {goog.date.DateTime} Parsed date or null if parse fails.
 */
goog.date.fromIsoString = function(formatted) {
  var ret = new goog.date.DateTime(2000);
  return goog.date.setIso8601DateTime(ret, formatted) ? ret : null;
};


/**
 * Parses a datetime string expressed in ISO 8601 format. Overwrites the date
 * and optionally the time part of the given object with the parsed values.
 *
 * @param {!goog.date.DateTime} dateTime Object whose fields will be set.
 * @param {string} formatted A date or datetime expressed in ISO 8601 format.
 * @return {boolean} Whether the parsing succeeded.
 */
goog.date.setIso8601DateTime = function(dateTime, formatted) {
  formatted = goog.string.trim(formatted);
  var delim = formatted.indexOf('T') == -1 ? ' ' : 'T';
  var parts = formatted.split(delim);
  return goog.date.setIso8601DateOnly_(dateTime, parts[0]) &&
      (parts.length < 2 || goog.date.setIso8601TimeOnly_(dateTime, parts[1]));
};


/**
 * Sets date fields based on an ISO 8601 format string.
 *
 * @param {!goog.date.DateTime} d Object whose fields will be set.
 * @param {string} formatted A date expressed in ISO 8601 format.
 * @return {boolean} Whether the parsing succeeded.
 * @private
 */
goog.date.setIso8601DateOnly_ = function(d, formatted) {
  // split the formatted ISO date string into its date fields
  var parts = formatted.match(goog.date.splitDateStringRegex_);
  if (!parts) {
    return false;
  }

  var year = Number(parts[1]);
  var month = Number(parts[2]);
  var date = Number(parts[3]);
  var dayOfYear = Number(parts[4]);
  var week = Number(parts[5]);
  // ISO weekdays start with 1, native getDay() values start with 0
  var dayOfWeek = Number(parts[6]) || 1;

  d.setFullYear(year);

  if (dayOfYear) {
    d.setDate(1);
    d.setMonth(0);
    var offset = dayOfYear - 1; // offset, so 1-indexed, i.e., skip day 1
    d.add(new goog.date.Interval(goog.date.Interval.DAYS, offset));
  } else if (week) {
    goog.date.setDateFromIso8601Week_(d, week, dayOfWeek);
  } else {
    if (month) {
      d.setDate(1);
      d.setMonth(month - 1);
    }
    if (date) {
      d.setDate(date);
    }
  }

  return true;
};


/**
 * Sets date fields based on an ISO 8601 week string.
 * See {@link http://en.wikipedia.org/wiki/ISO_week_date}, "Relation with the
 * Gregorian Calendar".  The first week of a new ISO year is the week with the
 * majority of its days in the new Gregorian year.  I.e., ISO Week 1's Thursday
 * is in that year.  ISO weeks always start on Monday. So ISO Week 1 can
 * contain a few days from the previous Gregorian year.  And ISO weeks always
 * end on Sunday, so the last ISO week (Week 52 or 53) can have a few days from
 * the following Gregorian year.
 * Example: '1997-W01' lasts from 1996-12-30 to 1997-01-05.  January 1, 1997 is
 * a Wednesday. So W01's Monday is Dec.30, 1996, and Sunday is January 5, 1997.
 *
 * @param {goog.date.DateTime} d Object whose fields will be set.
 * @param {number} week ISO week number.
 * @param {number} dayOfWeek ISO day of week.
 * @private
 */
goog.date.setDateFromIso8601Week_ = function(d, week, dayOfWeek) {
  // calculate offset for first week
  d.setMonth(0);
  d.setDate(1);
  var jsDay = d.getDay();
  // switch Sunday (0) to index 7; ISO days are 1-indexed
  var jan1WeekDay = jsDay || 7;

  var THURSDAY = 4;
  if (jan1WeekDay <= THURSDAY) {
    // was extended back to Monday
    var startDelta = 1 - jan1WeekDay; // e.g., Thu(4) ==> -3
  } else {
    // was extended forward to Monday
    startDelta = 8 - jan1WeekDay; // e.g., Fri(5) ==> +3
  }

  // find the absolute number of days to offset from the start of year
  // to arrive close to the Gregorian equivalent (pending adjustments above)
  // Note: decrement week multiplier by one because 1st week is
  // represented by dayOfWeek value
  var absoluteDays = Number(dayOfWeek) + (7 * (Number(week) - 1));

  // convert from ISO weekday format to Gregorian calendar date
  // note: subtract 1 because 1-indexed; offset should not include 1st of month
  var delta = startDelta + absoluteDays - 1;
  var interval = new goog.date.Interval(goog.date.Interval.DAYS, delta);
  d.add(interval);
};


/**
 * Sets time fields based on an ISO 8601 format string.
 * Note: only time fields, not date fields.
 *
 * @param {!goog.date.DateTime} d Object whose fields will be set.
 * @param {string} formatted A time expressed in ISO 8601 format.
 * @return {boolean} Whether the parsing succeeded.
 * @private
 */
goog.date.setIso8601TimeOnly_ = function(d, formatted) {
  // first strip timezone info from the end
  var parts = formatted.match(goog.date.splitTimezoneStringRegex_);

  var offset = 0; // local time if no timezone info
  if (parts) {
    if (parts[0] != 'Z') {
      offset = parts[2] * 60 + Number(parts[3]);
      offset *= parts[1] == '-' ? 1 : -1;
    }
    offset -= d.getTimezoneOffset();
    formatted = formatted.substr(0, formatted.length - parts[0].length);
  }

  // then work out the time
  parts = formatted.match(goog.date.splitTimeStringRegex_);
  if (!parts) {
    return false;
  }

  d.setHours(Number(parts[1]));
  d.setMinutes(Number(parts[2]) || 0);
  d.setSeconds(Number(parts[3]) || 0);
  d.setMilliseconds(parts[4] ? parts[4] * 1000 : 0);

  if (offset != 0) {
    // adjust the date and time according to the specified timezone
    d.setTime(d.getTime() + offset * 60000);
  }

  return true;
};



/**
 * Class representing a date/time interval. Used for date calculations.
 * <pre>
 * new goog.date.Interval(0, 1) // One month
 * new goog.date.Interval(0, 0, 3, 1) // Three days and one hour
 * new goog.date.Interval(goog.date.Interval.DAYS, 1) // One day
 * </pre>
 *
 * @param {number|string=} opt_years Years or string representing date part.
 * @param {number=} opt_months Months or number of whatever date part specified
 *     by first parameter.
 * @param {number=} opt_days Days.
 * @param {number=} opt_hours Hours.
 * @param {number=} opt_minutes Minutes.
 * @param {number=} opt_seconds Seconds.
 * @constructor
 */
goog.date.Interval = function(opt_years, opt_months, opt_days, opt_hours,
                              opt_minutes, opt_seconds) {
  if (goog.isString(opt_years)) {
    var type = opt_years;
    var interval = /** @type {number} */ (opt_months);
    this.years = type == goog.date.Interval.YEARS ? interval : 0;
    this.months = type == goog.date.Interval.MONTHS ? interval : 0;
    this.days = type == goog.date.Interval.DAYS ? interval : 0;
    this.hours = type == goog.date.Interval.HOURS ? interval : 0;
    this.minutes = type == goog.date.Interval.MINUTES ? interval : 0;
    this.seconds = type == goog.date.Interval.SECONDS ? interval : 0;
  } else {
    this.years = /** @type {number} */ (opt_years) || 0;
    this.months = opt_months || 0;
    this.days = opt_days || 0;
    this.hours = opt_hours || 0;
    this.minutes = opt_minutes || 0;
    this.seconds = opt_seconds || 0;
  }
};


/**
 * Parses an XML Schema duration (ISO 8601 extended).
 * @see http://www.w3.org/TR/xmlschema-2/#duration
 *
 * @param  {string} duration An XML schema duration in textual format.
 *     Recurring durations and weeks are not supported.
 * @return {goog.date.Interval} The duration as a goog.date.Interval or null
 *     if the parse fails.
 */
goog.date.Interval.fromIsoString = function(duration) {
  var parts = duration.match(goog.date.splitDurationRegex_);
  if (!parts) {
    return null;
  }

  var timeEmpty = !(parts[6] || parts[7] || parts[8]);
  var dateTimeEmpty = timeEmpty && !(parts[2] || parts[3] || parts[4]);
  if (dateTimeEmpty || timeEmpty && parts[5]) {
    return null;
  }

  var negative = parts[1];
  var years = parseInt(parts[2], 10) || 0;
  var months = parseInt(parts[3], 10) || 0;
  var days = parseInt(parts[4], 10) || 0;
  var hours = parseInt(parts[6], 10) || 0;
  var minutes = parseInt(parts[7], 10) || 0;
  var seconds = parseFloat(parts[8]) || 0;
  return negative ? new goog.date.Interval(-years, -months, -days,
                                           -hours, -minutes, -seconds) :
                    new goog.date.Interval(years, months, days,
                                           hours, minutes, seconds);
};


/**
 * Serializes goog.date.Interval into XML Schema duration (ISO 8601 extended).
 * @see http://www.w3.org/TR/xmlschema-2/#duration
 *
 * @param {boolean=} opt_verbose Include zero fields in the duration string.
 * @return {?string} An XML schema duration in ISO 8601 extended format,
 *     or null if the interval contains both positive and negative fields.
 */
goog.date.Interval.prototype.toIsoString = function(opt_verbose) {
  var minField = Math.min(this.years, this.months, this.days,
                          this.hours, this.minutes, this.seconds);
  var maxField = Math.max(this.years, this.months, this.days,
                          this.hours, this.minutes, this.seconds);
  if (minField < 0 && maxField > 0) {
    return null;
  }

  // Return 0 seconds if all fields are zero.
  if (!opt_verbose && minField == 0 && maxField == 0) {
    return 'PT0S';
  }

  var res = [];

  // Add sign and 'P' prefix.
  if (minField < 0) {
    res.push('-');
  }
  res.push('P');

  // Add date.
  if (this.years || opt_verbose) {
    res.push(Math.abs(this.years) + 'Y');
  }
  if (this.months || opt_verbose) {
    res.push(Math.abs(this.months) + 'M');
  }
  if (this.days || opt_verbose) {
    res.push(Math.abs(this.days) + 'D');
  }

  // Add time.
  if (this.hours || this.minutes || this.seconds || opt_verbose) {
    res.push('T');
    if (this.hours || opt_verbose) {
      res.push(Math.abs(this.hours) + 'H');
    }
    if (this.minutes || opt_verbose) {
      res.push(Math.abs(this.minutes) + 'M');
    }
    if (this.seconds || opt_verbose) {
      res.push(Math.abs(this.seconds) + 'S');
    }
  }

  return res.join('');
};


/**
 * Tests whether the given interval is equal to this interval.
 * Note, this is a simple field-by-field comparison, it doesn't
 * account for comparisons like "12 months == 1 year".
 *
 * @param {goog.date.Interval} other The interval to test.
 * @return {boolean} Whether the intervals are equal.
 */
goog.date.Interval.prototype.equals = function(other) {
  return other.years == this.years &&
         other.months == this.months &&
         other.days == this.days &&
         other.hours == this.hours &&
         other.minutes == this.minutes &&
         other.seconds == this.seconds;
};


/**
 * @return {!goog.date.Interval} A clone of the interval object.
 */
goog.date.Interval.prototype.clone = function() {
  return new goog.date.Interval(
      this.years, this.months, this.days,
      this.hours, this.minutes, this.seconds);
};


/**
 * Years constant for the date parts.
 * @type {string}
 */
goog.date.Interval.YEARS = 'y';


/**
 * Months constant for the date parts.
 * @type {string}
 */
goog.date.Interval.MONTHS = 'm';


/**
 * Days constant for the date parts.
 * @type {string}
 */
goog.date.Interval.DAYS = 'd';


/**
 * Hours constant for the date parts.
 * @type {string}
 */
goog.date.Interval.HOURS = 'h';


/**
 * Minutes constant for the date parts.
 * @type {string}
 */
goog.date.Interval.MINUTES = 'n';


/**
 * Seconds constant for the date parts.
 * @type {string}
 */
goog.date.Interval.SECONDS = 's';


/**
 * @return {!goog.date.Interval} Negative of this interval.
 */
goog.date.Interval.prototype.getInverse = function() {
  return this.times(-1);
};


/**
 * Calculates n * (this interval) by memberwise multiplication.
 * @param {number} n An integer.
 * @return {!goog.date.Interval} n * this.
 */
goog.date.Interval.prototype.times = function(n) {
  return new goog.date.Interval(this.years * n,
                                this.months * n,
                                this.days * n,
                                this.hours * n,
                                this.minutes * n,
                                this.seconds * n);
};


/**
 * Gets the total number of seconds in the time interval. Assumes that months
 * and years are empty.
 * @return {number} Total number of seconds in the interval.
 */
goog.date.Interval.prototype.getTotalSeconds = function() {
  goog.asserts.assert(this.years == 0 && this.months == 0);
  return ((this.days * 24 + this.hours) * 60 + this.minutes) * 60 +
      this.seconds;
};


/**
 * Adds the Interval in the argument to this Interval field by field.
 *
 * @param {goog.date.Interval} interval The Interval to add.
 */
goog.date.Interval.prototype.add = function(interval) {
  this.years += interval.years;
  this.months += interval.months;
  this.days += interval.days;
  this.hours += interval.hours;
  this.minutes += interval.minutes;
  this.seconds += interval.seconds;
};



/**
 * Class representing a date. Defaults to current date if none is specified.
 *
 * Implements most methods of the native js Date object (except the time related
 * ones, {@see goog.date.DateTime}) and can be used interchangeably with it just
 * as if goog.date.Date was a synonym of Date. To make this more transparent,
 * Closure APIs should accept goog.date.DateLike instead of the real Date
 * object.
 *
 * To allow goog.date.Date objects to be passed as arguments to methods
 * expecting Date objects this class is marked as extending the built in Date
 * object even though that's not strictly true.
 *
 * @param {number|Object=} opt_year Four digit year or a date-like object. If
 *     not set, the created object will contain the date determined by
 *     goog.now().
 * @param {number=} opt_month Month, 0 = Jan, 11 = Dec.
 * @param {number=} opt_date Date of month, 1 - 31.
 * @constructor
 * @see goog.date.DateTime
 */
goog.date.Date = function(opt_year, opt_month, opt_date) {
  // goog.date.DateTime assumes that only this.date_ is added in this ctor.
  if (goog.isNumber(opt_year)) {
    this.date_ = new Date(opt_year, opt_month || 0, opt_date || 1);
    this.maybeFixDst_(opt_date || 1);
  } else if (goog.isObject(opt_year)) {
    this.date_ = new Date(opt_year.getFullYear(), opt_year.getMonth(),
                          opt_year.getDate());
    this.maybeFixDst_(opt_year.getDate());
  } else {
    this.date_ = new Date(goog.now());
    this.date_.setHours(0);
    this.date_.setMinutes(0);
    this.date_.setSeconds(0);
    this.date_.setMilliseconds(0);
  }
};


/**
 * First day of week. 0 = Mon, 6 = Sun.
 * @type {number}
 * @private
 */
goog.date.Date.prototype.firstDayOfWeek_ =
    goog.i18n.DateTimeSymbols.FIRSTDAYOFWEEK;


/**
 * The cut off weekday used for week number calculations. 0 = Mon, 6 = Sun.
 * @type {number}
 * @private
 */
goog.date.Date.prototype.firstWeekCutOffDay_ =
    goog.i18n.DateTimeSymbols.FIRSTWEEKCUTOFFDAY;


/**
 * @return {!goog.date.Date} A clone of the date object.
 */
goog.date.Date.prototype.clone = function() {
  var date = new goog.date.Date(this.date_);
  date.firstDayOfWeek_ = this.firstDayOfWeek_;
  date.firstWeekCutOffDay_ = this.firstWeekCutOffDay_;

  return date;
};


/**
 * @return {number} The four digit year of date.
 */
goog.date.Date.prototype.getFullYear = function() {
  return this.date_.getFullYear();
};


/**
 * Alias for getFullYear.
 *
 * @return {number} The four digit year of date.
 * @see #getFullyear
 */
goog.date.Date.prototype.getYear = function() {
  return this.getFullYear();
};


/**
 * @return {goog.date.month} The month of date, 0 = Jan, 11 = Dec.
 */
goog.date.Date.prototype.getMonth = function() {
  return /** @type {goog.date.month} */ (this.date_.getMonth());
};


/**
 * @return {number} The date of month.
 */
goog.date.Date.prototype.getDate = function() {
  return this.date_.getDate();
};


/**
 * Returns the number of milliseconds since 1 January 1970 00:00:00.
 *
 * @return {number} The number of milliseconds since 1 January 1970 00:00:00.
 */
goog.date.Date.prototype.getTime = function() {
  return this.date_.getTime();
};


/**
 * @return {goog.date.weekDay} The day of week, US style. 0 = Sun, 6 = Sat.
 */
goog.date.Date.prototype.getDay = function() {
  return /** @type {goog.date.weekDay} */ (this.date_.getDay());
};


/**
 * @return {number} The day of week, ISO style. 0 = Mon, 6 = Sun.
 */
goog.date.Date.prototype.getIsoWeekday = function() {
  return (this.getDay() + 6) % 7;
};


/**
 * @return {number} The day of week according to firstDayOfWeek setting.
 */
goog.date.Date.prototype.getWeekday = function() {
  return (this.getIsoWeekday() - this.firstDayOfWeek_ + 7) % 7;
};


/**
 * @return {number} The four digit year of date according to universal time.
 */
goog.date.Date.prototype.getUTCFullYear = function() {
  return this.date_.getUTCFullYear();
};


/**
 * @return {goog.date.month} The month of date according to universal time,
 *     0 = Jan, 11 = Dec.
 */
goog.date.Date.prototype.getUTCMonth = function() {
  return /** @type {goog.date.month} */ (this.date_.getUTCMonth());
};


/**
 * @return {number} The date of month according to universal time.
 */
goog.date.Date.prototype.getUTCDate = function() {
  return this.date_.getUTCDate();
};


/**
 * @return {goog.date.weekDay} The day of week according to universal time,
 *     US style. 0 = Sun, 1 = Mon, 6 = Sat.
 */
goog.date.Date.prototype.getUTCDay = function() {
  return /** @type {goog.date.weekDay} */ (this.date_.getDay());
};


/**
 * @return {number} The hours value according to universal time.
 */
goog.date.Date.prototype.getUTCHours = function() {
  return this.date_.getUTCHours();
};


/**
 * @return {number} The hours value according to universal time.
 */
goog.date.Date.prototype.getUTCMinutes = function() {
  return this.date_.getUTCMinutes();
};


/**
 * @return {number} The day of week according to universal time, ISO style.
 *     0 = Mon, 6 = Sun.
 */
goog.date.Date.prototype.getUTCIsoWeekday = function() {
  return (this.date_.getUTCDay() + 6) % 7;
};


/**
 * @return {number} The day of week according to universal time and
 *     firstDayOfWeek setting.
 */
goog.date.Date.prototype.getUTCWeekday = function() {
  return (this.getUTCIsoWeekday() - this.firstDayOfWeek_ + 7) % 7;
};


/**
 * @return {number} The first day of the week. 0 = Mon, 6 = Sun.
 */
goog.date.Date.prototype.getFirstDayOfWeek = function() {
  return this.firstDayOfWeek_;
};


/**
 * @return {number} The cut off weekday used for week number calculations.
 *     0 = Mon, 6 = Sun.
 */
goog.date.Date.prototype.getFirstWeekCutOffDay = function() {
  return this.firstWeekCutOffDay_;
};


/**
 * @return {number} The number of days for the selected month.
 */
goog.date.Date.prototype.getNumberOfDaysInMonth = function() {
  return goog.date.getNumberOfDaysInMonth(this.getFullYear(), this.getMonth());
};


/**
 * @return {number} The week number.
 */
goog.date.Date.prototype.getWeekNumber = function() {
  return goog.date.getWeekNumber(
      this.getFullYear(), this.getMonth(), this.getDate(),
      this.firstWeekCutOffDay_, this.firstDayOfWeek_);
};


/**
 * @return {number} The day of year.
 */
goog.date.Date.prototype.getDayOfYear = function() {
  var dayOfYear = this.getDate();
  var year = this.getFullYear();
  for (var m = this.getMonth() - 1; m >= 0; m--) {
    dayOfYear += goog.date.getNumberOfDaysInMonth(year, m);
  }

  return dayOfYear;
};


/**
 * Returns timezone offset. The timezone offset is the delta in minutes between
 * UTC and your local time. E.g., UTC+10 returns -600. Daylight savings time
 * prevents this value from being constant.
 *
 * @return {number} The timezone offset.
 */
goog.date.Date.prototype.getTimezoneOffset = function() {
  return this.date_.getTimezoneOffset();
};


/**
 * Returns timezone offset as a string. Returns offset in [+-]HH:mm format or Z
 * for UTC.
 *
 * @return {string} The timezone offset as a string.
 */
goog.date.Date.prototype.getTimezoneOffsetString = function() {
  var tz;
  var offset = this.getTimezoneOffset();

  if (offset == 0) {
    tz = 'Z';
  } else {
    var n = Math.abs(offset) / 60;
    var h = Math.floor(n);
    var m = (n - h) * 60;
    tz = (offset > 0 ? '-' : '+') +
        goog.string.padNumber(h, 2) + ':' +
        goog.string.padNumber(m, 2);
  }

  return tz;
};


/**
 * Sets the date.
 *
 * @param {goog.date.Date} date Date object to set date from.
 */
goog.date.Date.prototype.set = function(date) {
  this.date_ = new Date(date.getFullYear(), date.getMonth(), date.getDate());
};


/**
 * Sets the year part of the date.
 *
 * @param {number} year Four digit year.
 */
goog.date.Date.prototype.setFullYear = function(year) {
  this.date_.setFullYear(year);
};


/**
 * Alias for setFullYear.
 *
 * @param {number} year Four digit year.
 * @see #setFullYear
 */
goog.date.Date.prototype.setYear = function(year) {
  this.setFullYear(year);
};


/**
 * Sets the month part of the date.
 *
 * TODO(nnaze): Update type to goog.date.month.
 *
 * @param {number} month The month, where 0 = Jan, 11 = Dec.
 */
goog.date.Date.prototype.setMonth = function(month) {
  this.date_.setMonth(month);
};


/**
 * Sets the day part of the date.
 *
 * @param {number} date The day part.
 */
goog.date.Date.prototype.setDate = function(date) {
  this.date_.setDate(date);
};


/**
 * Sets the value of the date object as expressed in the number of milliseconds
 * since 1 January 1970 00:00:00.
 *
 * @param {number} ms Number of milliseconds since 1 Jan 1970.
 */
goog.date.Date.prototype.setTime = function(ms) {
  this.date_.setTime(ms);
};


/**
 * Sets the year part of the date according to universal time.
 *
 * @param {number} year Four digit year.
 */
goog.date.Date.prototype.setUTCFullYear = function(year) {
  this.date_.setUTCFullYear(year);
};


/**
 * Sets the month part of the date according to universal time.
 *
 * @param {number} month The month, where 0 = Jan, 11 = Dec.
 */
goog.date.Date.prototype.setUTCMonth = function(month) {
  this.date_.setUTCMonth(month);
};


/**
 * Sets the day part of the date according to universal time.
 *
 * @param {number} date The UTC date.
 */
goog.date.Date.prototype.setUTCDate = function(date) {
  this.date_.setUTCDate(date);
};


/**
 * Sets the first day of week.
 *
 * @param {number} day 0 = Mon, 6 = Sun.
 */
goog.date.Date.prototype.setFirstDayOfWeek = function(day) {
  this.firstDayOfWeek_ = day;
};


/**
 * Sets cut off weekday used for week number calculations. 0 = Mon, 6 = Sun.
 *
 * @param {number} day The cut off weekday.
 */
goog.date.Date.prototype.setFirstWeekCutOffDay = function(day) {
  this.firstWeekCutOffDay_ = day;
};


/**
 * Performs date calculation by adding the supplied interval to the date.
 *
 * @param {goog.date.Interval} interval Date interval to add.
 */
goog.date.Date.prototype.add = function(interval) {
  if (interval.years || interval.months) {
    // As months have different number of days adding a month to Jan 31 by just
    // setting the month would result in a date in early March rather than Feb
    // 28 or 29. Doing it this way overcomes that problem.

    // adjust year and month, accounting for both directions
    var month = this.getMonth() + interval.months + interval.years * 12;
    var year = this.getYear() + Math.floor(month / 12);
    month %= 12;
    if (month < 0) {
      month += 12;
    }

    var daysInTargetMonth = goog.date.getNumberOfDaysInMonth(year, month);
    var date = Math.min(daysInTargetMonth, this.getDate());

    // avoid inadvertently causing rollovers to adjacent months
    this.setDate(1);

    this.setFullYear(year);
    this.setMonth(month);
    this.setDate(date);
  }

  if (interval.days) {
    // Convert the days to milliseconds and add it to the UNIX timestamp.
    // Taking noon helps to avoid 1 day error due to the daylight saving.
    var noon = new Date(this.getYear(), this.getMonth(), this.getDate(), 12);
    var result = new Date(noon.getTime() + interval.days * 86400000);

    // Set date to 1 to prevent rollover caused by setting the year or month.
    this.setDate(1);
    this.setFullYear(result.getFullYear());
    this.setMonth(result.getMonth());
    this.setDate(result.getDate());

    this.maybeFixDst_(result.getDate());
  }
};


/**
 * Returns ISO 8601 string representation of date.
 *
 * @param {boolean=} opt_verbose Whether the verbose format should be used
 *     instead of the default compact one.
 * @param {boolean=} opt_tz Whether the timezone offset should be included
 *     in the string.
 * @return {string} ISO 8601 string representation of date.
 */
goog.date.Date.prototype.toIsoString = function(opt_verbose, opt_tz) {
  var str = [
    this.getFullYear(),
    goog.string.padNumber(this.getMonth() + 1, 2),
    goog.string.padNumber(this.getDate(), 2)
  ];

  return str.join((opt_verbose) ? '-' : '') +
         (opt_tz ? this.getTimezoneOffsetString() : '');
};


/**
 * Returns ISO 8601 string representation of date according to universal time.
 *
 * @param {boolean=} opt_verbose Whether the verbose format should be used
 *     instead of the default compact one.
 * @param {boolean=} opt_tz Whether the timezone offset should be included in
 *     the string.
 * @return {string} ISO 8601 string representation of date according to
 *     universal time.
 */
goog.date.Date.prototype.toUTCIsoString = function(opt_verbose, opt_tz) {
  var str = [
    this.getUTCFullYear(),
    goog.string.padNumber(this.getUTCMonth() + 1, 2),
    goog.string.padNumber(this.getUTCDate(), 2)
  ];

  return str.join((opt_verbose) ? '-' : '') + (opt_tz ? 'Z' : '');
};


/**
 * Tests whether given date is equal to this Date.
 * Note: This ignores units more precise than days (hours and below)
 * and also ignores timezone considerations.
 *
 * @param {goog.date.Date} other The date to compare.
 * @return {boolean} Whether the given date is equal to this one.
 */
goog.date.Date.prototype.equals = function(other) {
  return this.getYear() == other.getYear() &&
         this.getMonth() == other.getMonth() &&
         this.getDate() == other.getDate();
};


/**
 * Overloaded toString method for object.
 * @return {string} ISO 8601 string representation of date.
 */
goog.date.Date.prototype.toString = function() {
  return this.toIsoString();
};


/**
 * Fixes date to account for daylight savings time in browsers that fail to do
 * so automatically.
 * @param {number} expected Expected date.
 * @private
 */
goog.date.Date.prototype.maybeFixDst_ = function(expected) {
  if (this.getDate() != expected) {
    var dir = this.getDate() < expected ? 1 : -1;
    this.date_.setUTCHours(this.date_.getUTCHours() + dir);
  }
};


/**
 * @return {number} Value of wrapped date.
 */
goog.date.Date.prototype.valueOf = function() {
  return this.date_.valueOf();
};


/**
 * Compares two dates.  May be used as a sorting function.
 * @see goog.array.sort
 * @param {!goog.date.DateLike} date1 Date to compare.
 * @param {!goog.date.DateLike} date2 Date to compare.
 * @return {number} Comparison result. 0 if dates are the same, less than 0 if
 *     date1 is earlier than date2, greater than 0 if date1 is later than date2.
 */
goog.date.Date.compare = function(date1, date2) {
  return date1.getTime() - date2.getTime();
};



/**
 * Class representing a date and time. Defaults to current date and time if none
 * is specified.
 *
 * Implements most methods of the native js Date object and can be used
 * interchangeably with it just as if goog.date.DateTime was a subclass of Date.
 *
 * @param {number|Object=} opt_year Four digit year or a date-like object. If
 *     not set, the created object will contain the date determined by
 *     goog.now().
 * @param {number=} opt_month Month, 0 = Jan, 11 = Dec.
 * @param {number=} opt_date Date of month, 1 - 31.
 * @param {number=} opt_hours Hours, 0 - 24.
 * @param {number=} opt_minutes Minutes, 0 - 59.
 * @param {number=} opt_seconds Seconds, 0 - 61.
 * @param {number=} opt_milliseconds Milliseconds, 0 - 999.
 * @constructor
 * @extends {goog.date.Date}
 */
goog.date.DateTime = function(opt_year, opt_month, opt_date, opt_hours,
                              opt_minutes, opt_seconds, opt_milliseconds) {
  if (goog.isNumber(opt_year)) {
    this.date_ = new Date(opt_year, opt_month || 0, opt_date || 1,
                          opt_hours || 0, opt_minutes || 0, opt_seconds || 0,
                          opt_milliseconds || 0);
  } else {
    this.date_ = new Date(opt_year ? opt_year.getTime() : goog.now());
  }
};
goog.inherits(goog.date.DateTime, goog.date.Date);


/**
 * Creates a DateTime from a datetime string expressed in RFC 822 format.
 *
 * @param {string} formatted A date or datetime expressed in RFC 822 format.
 * @return {goog.date.DateTime} Parsed date or null if parse fails.
 */
goog.date.DateTime.fromRfc822String = function(formatted) {
  var date = new Date(formatted);
  return !isNaN(date.getTime()) ? new goog.date.DateTime(date) : null;
};


/**
 * Returns the hours part of the datetime.
 *
 * @return {number} An integer between 0 and 23, representing the hour.
 */
goog.date.DateTime.prototype.getHours = function() {
  return this.date_.getHours();
};


/**
 * Returns the minutes part of the datetime.
 *
 * @return {number} An integer between 0 and 59, representing the minutes.
 */
goog.date.DateTime.prototype.getMinutes = function() {
  return this.date_.getMinutes();
};


/**
 * Returns the seconds part of the datetime.
 *
 * @return {number} An integer between 0 and 59, representing the seconds.
 */
goog.date.DateTime.prototype.getSeconds = function() {
  return this.date_.getSeconds();
};


/**
 * Returns the milliseconds part of the datetime.
 *
 * @return {number} An integer between 0 and 999, representing the milliseconds.
 */
goog.date.DateTime.prototype.getMilliseconds = function() {
  return this.date_.getMilliseconds();
};


/**
 * Returns the day of week according to universal time, US style.
 *
 * @return {goog.date.weekDay} Day of week, 0 = Sun, 1 = Mon, 6 = Sat.
 */
goog.date.DateTime.prototype.getUTCDay = function() {
  return /** @type {goog.date.weekDay} */ (this.date_.getUTCDay());
};


/**
 * Returns the hours part of the datetime according to universal time.
 *
 * @return {number} An integer between 0 and 23, representing the hour.
 */
goog.date.DateTime.prototype.getUTCHours = function() {
  return this.date_.getUTCHours();
};


/**
 * Returns the minutes part of the datetime according to universal time.
 *
 * @return {number} An integer between 0 and 59, representing the minutes.
 */
goog.date.DateTime.prototype.getUTCMinutes = function() {
  return this.date_.getUTCMinutes();
};


/**
 * Returns the seconds part of the datetime according to universal time.
 *
 * @return {number} An integer between 0 and 59, representing the seconds.
 */
goog.date.DateTime.prototype.getUTCSeconds = function() {
  return this.date_.getUTCSeconds();
};


/**
 * Returns the milliseconds part of the datetime according to universal time.
 *
 * @return {number} An integer between 0 and 999, representing the milliseconds.
 */
goog.date.DateTime.prototype.getUTCMilliseconds = function() {
  return this.date_.getUTCMilliseconds();
};


/**
 * Sets the hours part of the datetime.
 *
 * @param {number} hours An integer between 0 and 23, representing the hour.
 */
goog.date.DateTime.prototype.setHours = function(hours) {
  this.date_.setHours(hours);
};


/**
 * Sets the minutes part of the datetime.
 *
 * @param {number} minutes Integer between 0 and 59, representing the minutes.
 */
goog.date.DateTime.prototype.setMinutes = function(minutes) {
  this.date_.setMinutes(minutes);
};


/**
 * Sets the seconds part of the datetime.
 *
 * @param {number} seconds Integer between 0 and 59, representing the seconds.
 */
goog.date.DateTime.prototype.setSeconds = function(seconds) {
  this.date_.setSeconds(seconds);
};


/**
 * Sets the seconds part of the datetime.
 *
 * @param {number} ms Integer between 0 and 999, representing the milliseconds.
 */
goog.date.DateTime.prototype.setMilliseconds = function(ms) {
  this.date_.setMilliseconds(ms);
};


/**
 * Sets the hours part of the datetime according to universal time.
 *
 * @param {number} hours An integer between 0 and 23, representing the hour.
 */
goog.date.DateTime.prototype.setUTCHours = function(hours) {
  this.date_.setUTCHours(hours);
};


/**
 * Sets the minutes part of the datetime according to universal time.
 *
 * @param {number} minutes Integer between 0 and 59, representing the minutes.
 */
goog.date.DateTime.prototype.setUTCMinutes = function(minutes) {
  this.date_.setUTCMinutes(minutes);
};


/**
 * Sets the seconds part of the datetime according to universal time.
 *
 * @param {number} seconds Integer between 0 and 59, representing the seconds.
 */
goog.date.DateTime.prototype.setUTCSeconds = function(seconds) {
  this.date_.setUTCSeconds(seconds);
};


/**
 * Sets the seconds part of the datetime according to universal time.
 *
 * @param {number} ms Integer between 0 and 999, representing the milliseconds.
 */
goog.date.DateTime.prototype.setUTCMilliseconds = function(ms) {
  this.date_.setUTCMilliseconds(ms);
};


/**
 * Performs date calculation by adding the supplied interval to the date.
 *
 * @param {goog.date.Interval} interval Date interval to add.
 */
goog.date.DateTime.prototype.add = function(interval) {
  goog.date.Date.prototype.add.call(this, interval);

  if (interval.hours) {
    this.setHours(this.date_.getHours() + interval.hours);
  }
  if (interval.minutes) {
    this.setMinutes(this.date_.getMinutes() + interval.minutes);
  }
  if (interval.seconds) {
    this.setSeconds(this.date_.getSeconds() + interval.seconds);
  }
};


/**
 * Returns ISO 8601 string representation of date/time.
 *
 * @param {boolean=} opt_verbose Whether the verbose format should be used
 *     instead of the default compact one.
 * @param {boolean=} opt_tz Whether the timezone offset should be included
 *     in the string.
 * @return {string} ISO 8601 string representation of date/time.
 */
goog.date.DateTime.prototype.toIsoString = function(opt_verbose, opt_tz) {
  var dateString = goog.date.Date.prototype.toIsoString.call(this, opt_verbose);

  if (opt_verbose) {
    return dateString + ' ' +
        goog.string.padNumber(this.getHours(), 2) + ':' +
        goog.string.padNumber(this.getMinutes(), 2) + ':' +
        goog.string.padNumber(this.getSeconds(), 2) +
        (opt_tz ? this.getTimezoneOffsetString() : '');
  }

  return dateString + 'T' +
      goog.string.padNumber(this.getHours(), 2) +
      goog.string.padNumber(this.getMinutes(), 2) +
      goog.string.padNumber(this.getSeconds(), 2) +
      (opt_tz ? this.getTimezoneOffsetString() : '');
};


/**
 * Returns XML Schema 2 string representation of date/time.
 * The return value is also ISO 8601 compliant.
 *
 * @param {boolean=} opt_timezone Should the timezone offset be included in the
 *     string?.
 * @return {string} XML Schema 2 string representation of date/time.
 */
goog.date.DateTime.prototype.toXmlDateTime = function(opt_timezone) {
  return goog.date.Date.prototype.toIsoString.call(this, true) + 'T' +
      goog.string.padNumber(this.getHours(), 2) + ':' +
      goog.string.padNumber(this.getMinutes(), 2) + ':' +
      goog.string.padNumber(this.getSeconds(), 2) +
      (opt_timezone ? this.getTimezoneOffsetString() : '');
};


/**
 * Returns ISO 8601 string representation of date/time according to universal
 * time.
 *
 * @param {boolean=} opt_verbose Whether the opt_verbose format should be
 *     returned instead of the default compact one.
 * @param {boolean=} opt_tz Whether the the timezone offset should be included
 *     in the string.
 * @return {string} ISO 8601 string representation of date/time according to
 *     universal time.
 */
goog.date.DateTime.prototype.toUTCIsoString = function(opt_verbose, opt_tz) {
  var dateStr = goog.date.Date.prototype.toUTCIsoString.call(this, opt_verbose);

  if (opt_verbose) {
    return dateStr + ' ' +
        goog.string.padNumber(this.getUTCHours(), 2) + ':' +
        goog.string.padNumber(this.getUTCMinutes(), 2) + ':' +
        goog.string.padNumber(this.getUTCSeconds(), 2) +
        (opt_tz ? 'Z' : '');
  }

  return dateStr + 'T' +
      goog.string.padNumber(this.getUTCHours(), 2) +
      goog.string.padNumber(this.getUTCMinutes(), 2) +
      goog.string.padNumber(this.getUTCSeconds(), 2) +
      (opt_tz ? 'Z' : '');
};


/**
 * Tests whether given datetime is exactly equal to this DateTime.
 *
 * @param {goog.date.DateTime} other The datetime to compare.
 * @return {boolean} Whether the given datetime is exactly equal to this one.
 */
goog.date.DateTime.prototype.equals = function(other) {
  return this.getTime() == other.getTime();
};


/**
 * Overloaded toString method for object.
 * @return {string} ISO 8601 string representation of date/time.
 */
goog.date.DateTime.prototype.toString = function() {
  return this.toIsoString();
};


/**
 * Generates time label for the datetime, e.g., '5:30am'.
 * By default this does not pad hours (e.g., to '05:30') and it does add
 * an am/pm suffix.
 * TODO(timman): i18n -- hardcoding time format like this is bad.  E.g., in CJK
 *               locales, need Chinese characters for hour and minute units.
 * @param {boolean=} opt_padHours Whether to pad hours, e.g., '05:30' vs '5:30'.
 * @param {boolean=} opt_showAmPm Whether to show the 'am' and 'pm' suffix.
 * @param {boolean=} opt_omitZeroMinutes E.g., '5:00pm' becomes '5pm',
 *                                      but '5:01pm' remains '5:01pm'.
 * @return {string} The time label.
 */
goog.date.DateTime.prototype.toUsTimeString = function(opt_padHours,
                                                       opt_showAmPm,
                                                       opt_omitZeroMinutes) {
  var hours = this.getHours();

  // show am/pm marker by default
  if (!goog.isDef(opt_showAmPm)) {
    opt_showAmPm = true;
  }

  // 12pm
  var isPM = hours == 12;

  // change from 1-24 to 1-12 basis
  if (hours > 12) {
    hours -= 12;
    isPM = true;
  }

  // midnight is expressed as "12am", but if am/pm marker omitted, keep as '0'
  if (hours == 0 && opt_showAmPm) {
    hours = 12;
  }

  var label = opt_padHours ? goog.string.padNumber(hours, 2) : String(hours);
  var minutes = this.getMinutes();
  if (!opt_omitZeroMinutes || minutes > 0) {
    label += ':' + goog.string.padNumber(minutes, 2);
  }

  // by default, show am/pm suffix
  if (opt_showAmPm) {
    /**
     * @desc Suffix for morning times.
     */
    var MSG_TIME_AM = goog.getMsg('am');

    /**
     * @desc Suffix for afternoon times.
     */
    var MSG_TIME_PM = goog.getMsg('pm');

    label += isPM ? MSG_TIME_PM : MSG_TIME_AM;
  }
  return label;
};


/**
 * Generates time label for the datetime in standard ISO 24-hour time format.
 * E.g., '06:00:00' or '23:30:15'.
 * @param {boolean=} opt_showSeconds Whether to shows seconds. Defaults to TRUE.
 * @return {string} The time label.
 */
goog.date.DateTime.prototype.toIsoTimeString = function(opt_showSeconds) {
  var hours = this.getHours();
  var label = goog.string.padNumber(hours, 2) +
              ':' +
              goog.string.padNumber(this.getMinutes(), 2);
  if (!goog.isDef(opt_showSeconds) || opt_showSeconds) {
    label += ':' + goog.string.padNumber(this.getSeconds(), 2);
  }
  return label;
};


/**
 * @return {!goog.date.DateTime} A clone of the datetime object.
 */
goog.date.DateTime.prototype.clone = function() {
  var date = new goog.date.DateTime(this.date_);
  date.setFirstDayOfWeek(this.getFirstDayOfWeek());
  date.setFirstWeekCutOffDay(this.getFirstWeekCutOffDay());
  return date;
};

//javascript/closure/array/array.js
// Copyright 2006 The Closure Library Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @fileoverview Utilities for manipulating arrays.
 *
 * @author pupius@google.com (Daniel Pupius)
 * @author arv@google.com (Erik Arvidsson)
 * @author pallosp@google.com (Peter Pallos)
 */


goog.provide('goog.array');
goog.provide('goog.array.ArrayLike');

goog.require('goog.asserts');


/**
 * @define {boolean} NATIVE_ARRAY_PROTOTYPES indicates whether the code should
 * rely on Array.prototype functions, if available.
 *
 * The Array.prototype functions can be defined by external libraries like
 * Prototype and setting this flag to false forces closure to use its own
 * goog.array implementation.
 *
 * If your javascript can be loaded by a third party site and you are wary about
 * relying on the prototype functions, specify
 * "--define goog.NATIVE_ARRAY_PROTOTYPES=false" to the JSCompiler.
 */
goog.NATIVE_ARRAY_PROTOTYPES = true;


/**
 * @typedef {Array|NodeList|Arguments|{length: number}}
 */
goog.array.ArrayLike;


/**
 * Returns the last element in an array without removing it.
 * @param {goog.array.ArrayLike} array The array.
 * @return {*} Last item in array.
 */
goog.array.peek = function(array) {
  return array[array.length - 1];
};


/**
 * Reference to the original {@code Array.prototype}.
 * @private
 */
goog.array.ARRAY_PROTOTYPE_ = Array.prototype;


// NOTE(arv): Since most of the array functions are generic it allows you to
// pass an array-like object. Strings have a length and are considered array-
// like. However, the 'in' operator does not work on strings so we cannot just
// use the array path even if the browser supports indexing into strings. We
// therefore end up splitting the string.


/**
 * Returns the index of the first element of an array with a specified
 * value, or -1 if the element is not present in the array.
 *
 * See {@link http://tinyurl.com/developer-mozilla-org-array-indexof}
 *
 * @param {goog.array.ArrayLike} arr The array to be searched.
 * @param {*} obj The object for which we are searching.
 * @param {number=} opt_fromIndex The index at which to start the search. If
 *     omitted the search starts at index 0.
 * @return {number} The index of the first matching array element.
 */
goog.array.indexOf = goog.NATIVE_ARRAY_PROTOTYPES &&
                     goog.array.ARRAY_PROTOTYPE_.indexOf ?
    function(arr, obj, opt_fromIndex) {
      goog.asserts.assert(arr.length != null);

      return goog.array.ARRAY_PROTOTYPE_.indexOf.call(arr, obj, opt_fromIndex);
    } :
    function(arr, obj, opt_fromIndex) {
      var fromIndex = opt_fromIndex == null ?
          0 : (opt_fromIndex < 0 ?
               Math.max(0, arr.length + opt_fromIndex) : opt_fromIndex);

      if (goog.isString(arr)) {
        // Array.prototype.indexOf uses === so only strings should be found.
        if (!goog.isString(obj) || obj.length != 1) {
          return -1;
        }
        return arr.indexOf(obj, fromIndex);
      }

      for (var i = fromIndex; i < arr.length; i++) {
        if (i in arr && arr[i] === obj)
          return i;
      }
      return -1;
    };


/**
 * Returns the index of the last element of an array with a specified value, or
 * -1 if the element is not present in the array.
 *
 * See {@link http://tinyurl.com/developer-mozilla-org-array-lastindexof}
 *
 * @param {goog.array.ArrayLike} arr The array to be searched.
 * @param {*} obj The object for which we are searching.
 * @param {?number=} opt_fromIndex The index at which to start the search. If
 *     omitted the search starts at the end of the array.
 * @return {number} The index of the last matching array element.
 */
goog.array.lastIndexOf = goog.NATIVE_ARRAY_PROTOTYPES &&
                         goog.array.ARRAY_PROTOTYPE_.lastIndexOf ?
    function(arr, obj, opt_fromIndex) {
      goog.asserts.assert(arr.length != null);

      // Firefox treats undefined and null as 0 in the fromIndex argument which
      // leads it to always return -1
      var fromIndex = opt_fromIndex == null ? arr.length - 1 : opt_fromIndex;
      return goog.array.ARRAY_PROTOTYPE_.lastIndexOf.call(arr, obj, fromIndex);
    } :
    function(arr, obj, opt_fromIndex) {
      var fromIndex = opt_fromIndex == null ? arr.length - 1 : opt_fromIndex;

      if (fromIndex < 0) {
        fromIndex = Math.max(0, arr.length + fromIndex);
      }

      if (goog.isString(arr)) {
        // Array.prototype.lastIndexOf uses === so only strings should be found.
        if (!goog.isString(obj) || obj.length != 1) {
          return -1;
        }
        return arr.lastIndexOf(obj, fromIndex);
      }

      for (var i = fromIndex; i >= 0; i--) {
        if (i in arr && arr[i] === obj)
          return i;
      }
      return -1;
    };


/**
 * Calls a function for each element in an array.
 *
 * See {@link http://tinyurl.com/developer-mozilla-org-array-foreach}
 *
 * @param {goog.array.ArrayLike} arr Array or array like object over
 *     which to iterate.
 * @param {?function(this: T, ...)} f The function to call for every element.
 *     This function takes 3 arguments (the element, the index and the array).
 *     The return value is ignored. The function is called only for indexes of
 *     the array which have assigned values; it is not called for indexes which
 *     have been deleted or which have never been assigned values.
 * @param {T=} opt_obj The object to be used as the value of 'this'
 *     within f.
 * @template T
 */
goog.array.forEach = goog.NATIVE_ARRAY_PROTOTYPES &&
                     goog.array.ARRAY_PROTOTYPE_.forEach ?
    function(arr, f, opt_obj) {
      goog.asserts.assert(arr.length != null);

      goog.array.ARRAY_PROTOTYPE_.forEach.call(arr, f, opt_obj);
    } :
    function(arr, f, opt_obj) {
      var l = arr.length;  // must be fixed during loop... see docs
      var arr2 = goog.isString(arr) ? arr.split('') : arr;
      for (var i = 0; i < l; i++) {
        if (i in arr2) {
          f.call(opt_obj, arr2[i], i, arr);
        }
      }
    };


/**
 * Calls a function for each element in an array, starting from the last
 * element rather than the first.
 *
 * @param {goog.array.ArrayLike} arr The array over which to iterate.
 * @param {Function} f The function to call for every element. This function
 *     takes 3 arguments (the element, the index and the array). The return
 *     value is ignored.
 * @param {Object=} opt_obj The object to be used as the value of 'this'
 *     within f.
 */
goog.array.forEachRight = function(arr, f, opt_obj) {
  var l = arr.length;  // must be fixed during loop... see docs
  var arr2 = goog.isString(arr) ? arr.split('') : arr;
  for (var i = l - 1; i >= 0; --i) {
    if (i in arr2) {
      f.call(opt_obj, arr2[i], i, arr);
    }
  }
};


/**
 * Calls a function for each element in an array, and if the function returns
 * true adds the element to a new array.
 *
 * See {@link http://tinyurl.com/developer-mozilla-org-array-filter}
 *
 * @param {goog.array.ArrayLike} arr The array over which to iterate.
 * @param {Function} f The function to call for every element. This function
 *     takes 3 arguments (the element, the index and the array) and must
 *     return a Boolean. If the return value is true the element is added to the
 *     result array. If it is false the element is not included.
 * @param {Object=} opt_obj The object to be used as the value of 'this'
 *     within f.
 * @return {!Array} a new array in which only elements that passed the test are
 *     present.
 */
goog.array.filter = goog.NATIVE_ARRAY_PROTOTYPES &&
                    goog.array.ARRAY_PROTOTYPE_.filter ?
    function(arr, f, opt_obj) {
      goog.asserts.assert(arr.length != null);

      return goog.array.ARRAY_PROTOTYPE_.filter.call(arr, f, opt_obj);
    } :
    function(arr, f, opt_obj) {
      var l = arr.length;  // must be fixed during loop... see docs
      var res = [];
      var resLength = 0;
      var arr2 = goog.isString(arr) ? arr.split('') : arr;
      for (var i = 0; i < l; i++) {
        if (i in arr2) {
          var val = arr2[i];  // in case f mutates arr2
          if (f.call(opt_obj, val, i, arr)) {
            res[resLength++] = val;
          }
        }
      }
      return res;
    };


/**
 * Calls a function for each element in an array and inserts the result into a
 * new array.
 *
 * See {@link http://tinyurl.com/developer-mozilla-org-array-map}
 *
 * @param {goog.array.ArrayLike} arr The array over which to iterate.
 * @param {Function} f The function to call for every element. This function
 *     takes 3 arguments (the element, the index and the array) and should
 *     return something. The result will be inserted into a new array.
 * @param {Object=} opt_obj The object to be used as the value of 'this'
 *     within f.
 * @return {!Array} a new array with the results from f.
 */
goog.array.map = goog.NATIVE_ARRAY_PROTOTYPES &&
                 goog.array.ARRAY_PROTOTYPE_.map ?
    function(arr, f, opt_obj) {
      goog.asserts.assert(arr.length != null);

      return goog.array.ARRAY_PROTOTYPE_.map.call(arr, f, opt_obj);
    } :
    function(arr, f, opt_obj) {
      var l = arr.length;  // must be fixed during loop... see docs
      var res = new Array(l);
      var arr2 = goog.isString(arr) ? arr.split('') : arr;
      for (var i = 0; i < l; i++) {
        if (i in arr2) {
          res[i] = f.call(opt_obj, arr2[i], i, arr);
        }
      }
      return res;
    };


/**
 * Passes every element of an array into a function and accumulates the result.
 *
 * See {@link http://tinyurl.com/developer-mozilla-org-array-reduce}
 *
 * For example:
 * var a = [1, 2, 3, 4];
 * goog.array.reduce(a, function(r, v, i, arr) {return r + v;}, 0);
 * returns 10
 *
 * @param {goog.array.ArrayLike} arr The array over which to iterate.
 * @param {Function} f The function to call for every element. This function
 *     takes 4 arguments (the function's previous result or the initial value,
 *     the value of the current array element, the current array index, and the
 *     array itself)
 *     function(previousValue, currentValue, index, array).
 * @param {*} val The initial value to pass into the function on the first call.
 * @param {Object=} opt_obj  The object to be used as the value of 'this'
 *     within f.
 * @return {*} Result of evaluating f repeatedly across the values of the array.
 */
goog.array.reduce = function(arr, f, val, opt_obj) {
  if (arr.reduce) {
    if (opt_obj) {
      return arr.reduce(goog.bind(f, opt_obj), val);
    } else {
      return arr.reduce(f, val);
    }
  }
  var rval = val;
  goog.array.forEach(arr, function(val, index) {
    rval = f.call(opt_obj, rval, val, index, arr);
  });
  return rval;
};


/**
 * Passes every element of an array into a function and accumulates the result,
 * starting from the last element and working towards the first.
 *
 * See {@link http://tinyurl.com/developer-mozilla-org-array-reduceright}
 *
 * For example:
 * var a = ['a', 'b', 'c'];
 * goog.array.reduceRight(a, function(r, v, i, arr) {return r + v;}, '');
 * returns 'cba'
 *
 * @param {goog.array.ArrayLike} arr The array over which to iterate.
 * @param {Function} f The function to call for every element. This function
 *     takes 4 arguments (the function's previous result or the initial value,
 *     the value of the current array element, the current array index, and the
 *     array itself)
 *     function(previousValue, currentValue, index, array).
 * @param {*} val The initial value to pass into the function on the first call.
 * @param {Object=} opt_obj The object to be used as the value of 'this'
 *     within f.
 * @return {*} Object returned as a result of evaluating f repeatedly across the
 *     values of the array.
 */
goog.array.reduceRight = function(arr, f, val, opt_obj) {
  if (arr.reduceRight) {
    if (opt_obj) {
      return arr.reduceRight(goog.bind(f, opt_obj), val);
    } else {
      return arr.reduceRight(f, val);
    }
  }
  var rval = val;
  goog.array.forEachRight(arr, function(val, index) {
    rval = f.call(opt_obj, rval, val, index, arr);
  });
  return rval;
};


/**
 * Calls f for each element of an array. If any call returns true, some()
 * returns true (without checking the remaining elements). If all calls
 * return false, some() returns false.
 *
 * See {@link http://tinyurl.com/developer-mozilla-org-array-some}
 *
 * @param {goog.array.ArrayLike} arr The array to check.
 * @param {Function} f The function to call for every element. This function
 *     takes 3 arguments (the element, the index and the array) and must
 *     return a Boolean.
 * @param {Object=} opt_obj  The object to be used as the value of 'this'
 *     within f.
 * @return {boolean} true if any element passes the test.
 */
goog.array.some = goog.NATIVE_ARRAY_PROTOTYPES &&
                  goog.array.ARRAY_PROTOTYPE_.some ?
    function(arr, f, opt_obj) {
      goog.asserts.assert(arr.length != null);

      return goog.array.ARRAY_PROTOTYPE_.some.call(arr, f, opt_obj);
    } :
    function(arr, f, opt_obj) {
      var l = arr.length;  // must be fixed during loop... see docs
      var arr2 = goog.isString(arr) ? arr.split('') : arr;
      for (var i = 0; i < l; i++) {
        if (i in arr2 && f.call(opt_obj, arr2[i], i, arr)) {
          return true;
        }
      }
      return false;
    };


/**
 * Call f for each element of an array. If all calls return true, every()
 * returns true. If any call returns false, every() returns false and
 * does not continue to check the remaining elements.
 *
 * See {@link http://tinyurl.com/developer-mozilla-org-array-every}
 *
 * @param {goog.array.ArrayLike} arr The array to check.
 * @param {Function} f The function to call for every element. This function
 *     takes 3 arguments (the element, the index and the array) and must
 *     return a Boolean.
 * @param {Object=} opt_obj The object to be used as the value of 'this'
 *     within f.
 * @return {boolean} false if any element fails the test.
 */
goog.array.every = goog.NATIVE_ARRAY_PROTOTYPES &&
                   goog.array.ARRAY_PROTOTYPE_.every ?
    function(arr, f, opt_obj) {
      goog.asserts.assert(arr.length != null);

      return goog.array.ARRAY_PROTOTYPE_.every.call(arr, f, opt_obj);
    } :
    function(arr, f, opt_obj) {
      var l = arr.length;  // must be fixed during loop... see docs
      var arr2 = goog.isString(arr) ? arr.split('') : arr;
      for (var i = 0; i < l; i++) {
        if (i in arr2 && !f.call(opt_obj, arr2[i], i, arr)) {
          return false;
        }
      }
      return true;
    };


/**
 * Search an array for the first element that satisfies a given condition and
 * return that element.
 * @param {goog.array.ArrayLike} arr The array to search.
 * @param {Function} f The function to call for every element. This function
 *     takes 3 arguments (the element, the index and the array) and should
 *     return a boolean.
 * @param {Object=} opt_obj An optional "this" context for the function.
 * @return {*} The first array element that passes the test, or null if no
 *     element is found.
 */
goog.array.find = function(arr, f, opt_obj) {
  var i = goog.array.findIndex(arr, f, opt_obj);
  return i < 0 ? null : goog.isString(arr) ? arr.charAt(i) : arr[i];
};


/**
 * Search an array for the first element that satisfies a given condition and
 * return its index.
 * @param {goog.array.ArrayLike} arr The array to search.
 * @param {Function} f The function to call for every element. This function
 *     takes 3 arguments (the element, the index and the array) and should
 *     return a boolean.
 * @param {Object=} opt_obj An optional "this" context for the function.
 * @return {number} The index of the first array element that passes the test,
 *     or -1 if no element is found.
 */
goog.array.findIndex = function(arr, f, opt_obj) {
  var l = arr.length;  // must be fixed during loop... see docs
  var arr2 = goog.isString(arr) ? arr.split('') : arr;
  for (var i = 0; i < l; i++) {
    if (i in arr2 && f.call(opt_obj, arr2[i], i, arr)) {
      return i;
    }
  }
  return -1;
};


/**
 * Search an array (in reverse order) for the last element that satisfies a
 * given condition and return that element.
 * @param {goog.array.ArrayLike} arr The array to search.
 * @param {Function} f The function to call for every element. This function
 *     takes 3 arguments (the element, the index and the array) and should
 *     return a boolean.
 * @param {Object=} opt_obj An optional "this" context for the function.
 * @return {*} The last array element that passes the test, or null if no
 *     element is found.
 */
goog.array.findRight = function(arr, f, opt_obj) {
  var i = goog.array.findIndexRight(arr, f, opt_obj);
  return i < 0 ? null : goog.isString(arr) ? arr.charAt(i) : arr[i];
};


/**
 * Search an array (in reverse order) for the last element that satisfies a
 * given condition and return its index.
 * @param {goog.array.ArrayLike} arr The array to search.
 * @param {Function} f The function to call for every element. This function
 *     takes 3 arguments (the element, the index and the array) and should
 *     return a boolean.
 * @param {Object=} opt_obj An optional "this" context for the function.
 * @return {number} The index of the last array element that passes the test,
 *     or -1 if no element is found.
 */
goog.array.findIndexRight = function(arr, f, opt_obj) {
  var l = arr.length;  // must be fixed during loop... see docs
  var arr2 = goog.isString(arr) ? arr.split('') : arr;
  for (var i = l - 1; i >= 0; i--) {
    if (i in arr2 && f.call(opt_obj, arr2[i], i, arr)) {
      return i;
    }
  }
  return -1;
};


/**
 * Whether the array contains the given object.
 * @param {goog.array.ArrayLike} arr The array to test for the presence of the
 *     element.
 * @param {*} obj The object for which to test.
 * @return {boolean} true if obj is present.
 */
goog.array.contains = function(arr, obj) {
  return goog.array.indexOf(arr, obj) >= 0;
};


/**
 * Whether the array is empty.
 * @param {goog.array.ArrayLike} arr The array to test.
 * @return {boolean} true if empty.
 */
goog.array.isEmpty = function(arr) {
  return arr.length == 0;
};


/**
 * Clears the array.
 * @param {goog.array.ArrayLike} arr Array or array like object to clear.
 */
goog.array.clear = function(arr) {
  // For non real arrays we don't have the magic length so we delete the
  // indices.
  if (!goog.isArray(arr)) {
    for (var i = arr.length - 1; i >= 0; i--) {
      delete arr[i];
    }
  }
  arr.length = 0;
};


/**
 * Pushes an item into an array, if it's not already in the array.
 * @param {Array} arr Array into which to insert the item.
 * @param {*} obj Value to add.
 */
goog.array.insert = function(arr, obj) {
  if (!goog.array.contains(arr, obj)) {
    arr.push(obj);
  }
};


/**
 * Inserts an object at the given index of the array.
 * @param {goog.array.ArrayLike} arr The array to modify.
 * @param {*} obj The object to insert.
 * @param {number=} opt_i The index at which to insert the object. If omitted,
 *      treated as 0. A negative index is counted from the end of the array.
 */
goog.array.insertAt = function(arr, obj, opt_i) {
  goog.array.splice(arr, opt_i, 0, obj);
};


/**
 * Inserts at the given index of the array, all elements of another array.
 * @param {goog.array.ArrayLike} arr The array to modify.
 * @param {goog.array.ArrayLike} elementsToAdd The array of elements to add.
 * @param {number=} opt_i The index at which to insert the object. If omitted,
 *      treated as 0. A negative index is counted from the end of the array.
 */
goog.array.insertArrayAt = function(arr, elementsToAdd, opt_i) {
  goog.partial(goog.array.splice, arr, opt_i, 0).apply(null, elementsToAdd);
};


/**
 * Inserts an object into an array before a specified object.
 * @param {Array} arr The array to modify.
 * @param {*} obj The object to insert.
 * @param {*=} opt_obj2 The object before which obj should be inserted. If obj2
 *     is omitted or not found, obj is inserted at the end of the array.
 */
goog.array.insertBefore = function(arr, obj, opt_obj2) {
  var i;
  if (arguments.length == 2 || (i = goog.array.indexOf(arr, opt_obj2)) < 0) {
    arr.push(obj);
  } else {
    goog.array.insertAt(arr, obj, i);
  }
};


/**
 * Removes the first occurrence of a particular value from an array.
 * @param {goog.array.ArrayLike} arr Array from which to remove value.
 * @param {*} obj Object to remove.
 * @return {boolean} True if an element was removed.
 */
goog.array.remove = function(arr, obj) {
  var i = goog.array.indexOf(arr, obj);
  var rv;
  if ((rv = i >= 0)) {
    goog.array.removeAt(arr, i);
  }
  return rv;
};


/**
 * Removes from an array the element at index i
 * @param {goog.array.ArrayLike} arr Array or array like object from which to
 *     remove value.
 * @param {number} i The index to remove.
 * @return {boolean} True if an element was removed.
 */
goog.array.removeAt = function(arr, i) {
  goog.asserts.assert(arr.length != null);

  // use generic form of splice
  // splice returns the removed items and if successful the length of that
  // will be 1
  return goog.array.ARRAY_PROTOTYPE_.splice.call(arr, i, 1).length == 1;
};


/**
 * Removes the first value that satisfies the given condition.
 * @param {goog.array.ArrayLike} arr Array from which to remove value.
 * @param {Function} f The function to call for every element. This function
 *     takes 3 arguments (the element, the index and the array) and should
 *     return a boolean.
 * @param {Object=} opt_obj An optional "this" context for the function.
 * @return {boolean} True if an element was removed.
 */
goog.array.removeIf = function(arr, f, opt_obj) {
  var i = goog.array.findIndex(arr, f, opt_obj);
  if (i >= 0) {
    goog.array.removeAt(arr, i);
    return true;
  }
  return false;
};


/**
 * Returns a new array that is the result of joining the arguments.  If arrays
 * are passed then their items are added, however, if non-arrays are passed they
 * will be added to the return array as is.
 *
 * Note that ArrayLike objects will be added as is, rather than having their
 * items added.
 *
 * goog.array.concat([1, 2], [3, 4]) -> [1, 2, 3, 4]
 * goog.array.concat(0, [1, 2]) -> [0, 1, 2]
 * goog.array.concat([1, 2], null) -> [1, 2, null]
 *
 * There is bug in all current versions of IE (6, 7 and 8) where arrays created
 * in an iframe become corrupted soon (not immediately) after the iframe is
 * destroyed. This is common if loading data via goog.net.IframeIo, for example.
 * This corruption only affects the concat method which will start throwing
 * Catastrophic Errors (#-2147418113).
 *
 * See http://endoflow.com/scratch/corrupted-arrays.html for a test case.
 *
 * Internally goog.array should use this, so that all methods will continue to
 * work on these broken array objects.
 *
 * @param {...*} var_args Items to concatenate.  Arrays will have each item
 *     added, while primitives and objects will be added as is.
 * @return {!Array} The new resultant array.
 */
goog.array.concat = function(var_args) {
  return goog.array.ARRAY_PROTOTYPE_.concat.apply(
      goog.array.ARRAY_PROTOTYPE_, arguments);
};


/**
 * Does a shallow copy of an array.
 * @param {goog.array.ArrayLike} arr  Array or array-like object to clone.
 * @return {!Array} Clone of the input array.
 */
goog.array.clone = function(arr) {
  if (goog.isArray(arr)) {
    return goog.array.concat(/** @type {!Array} */ (arr));
  } else { // array like
    // Concat does not work with non arrays.
    var rv = [];
    for (var i = 0, len = arr.length; i < len; i++) {
      rv[i] = arr[i];
    }
    return rv;
  }
};


/**
 * Converts an object to an array.
 * @param {goog.array.ArrayLike} object  The object to convert to an array.
 * @return {!Array} The object converted into an array. If object has a
 *     length property, every property indexed with a non-negative number
 *     less than length will be included in the result. If object does not
 *     have a length property, an empty array will be returned.
 */
goog.array.toArray = function(object) {
  if (goog.isArray(object)) {
    // This fixes the JS compiler warning and forces the Object to an Array type
    return goog.array.concat(/** @type {!Array} */ (object));
  }
  // Clone what we hope to be an array-like object to an array.
  // We could check isArrayLike() first, but no check we perform would be as
  // reliable as simply making the call.
  return goog.array.clone(/** @type {Array} */ (object));
};


/**
 * Extends an array with another array, element, or "array like" object.
 * This function operates 'in-place', it does not create a new Array.
 *
 * Example:
 * var a = [];
 * goog.array.extend(a, [0, 1]);
 * a; // [0, 1]
 * goog.array.extend(a, 2);
 * a; // [0, 1, 2]
 *
 * @param {Array} arr1  The array to modify.
 * @param {...*} var_args The elements or arrays of elements to add to arr1.
 */
goog.array.extend = function(arr1, var_args) {
  for (var i = 1; i < arguments.length; i++) {
    var arr2 = arguments[i];
    // If we have an Array or an Arguments object we can just call push
    // directly.
    var isArrayLike;
    if (goog.isArray(arr2) ||
        // Detect Arguments. ES5 says that the [[Class]] of an Arguments object
        // is "Arguments" but only V8 and JSC/Safari gets this right. We instead
        // detect Arguments by checking for array like and presence of "callee".
        (isArrayLike = goog.isArrayLike(arr2)) &&
            // The getter for callee throws an exception in strict mode
            // according to section 10.6 in ES5 so check for presence instead.
            arr2.hasOwnProperty('callee')) {
      arr1.push.apply(arr1, arr2);

    } else if (isArrayLike) {
      // Otherwise loop over arr2 to prevent copying the object.
      var len1 = arr1.length;
      var len2 = arr2.length;
      for (var j = 0; j < len2; j++) {
        arr1[len1 + j] = arr2[j];
      }
    } else {
      arr1.push(arr2);
    }
  }
};


/**
 * Adds or removes elements from an array. This is a generic version of Array
 * splice. This means that it might work on other objects similar to arrays,
 * such as the arguments object.
 *
 * @param {goog.array.ArrayLike} arr The array to modify.
 * @param {number|undefined} index The index at which to start changing the
 *     array. If not defined, treated as 0.
 * @param {number} howMany How many elements to remove (0 means no removal. A
 *     value below 0 is treated as zero and so is any other non number. Numbers
 *     are floored).
 * @param {...*} var_args Optional, additional elements to insert into the
 *     array.
 * @return {!Array} the removed elements.
 */
goog.array.splice = function(arr, index, howMany, var_args) {
  goog.asserts.assert(arr.length != null);

  return goog.array.ARRAY_PROTOTYPE_.splice.apply(
      arr, goog.array.slice(arguments, 1));
};


/**
 * Returns a new array from a segment of an array. This is a generic version of
 * Array slice. This means that it might work on other objects similar to
 * arrays, such as the arguments object.
 *
 * @param {goog.array.ArrayLike} arr The array from which to copy a segment.
 * @param {number} start The index of the first element to copy.
 * @param {number=} opt_end The index after the last element to copy.
 * @return {!Array} A new array containing the specified segment of the original
 *     array.
 */
goog.array.slice = function(arr, start, opt_end) {
  goog.asserts.assert(arr.length != null);

  // passing 1 arg to slice is not the same as passing 2 where the second is
  // null or undefined (in that case the second argument is treated as 0).
  // we could use slice on the arguments object and then use apply instead of
  // testing the length
  if (arguments.length <= 2) {
    return goog.array.ARRAY_PROTOTYPE_.slice.call(arr, start);
  } else {
    return goog.array.ARRAY_PROTOTYPE_.slice.call(arr, start, opt_end);
  }
};


/**
 * Removes all duplicates from an array (retaining only the first
 * occurrence of each array element).  This function modifies the
 * array in place and doesn't change the order of the non-duplicate items.
 *
 * For objects, duplicates are identified as having the same unique ID as
 * defined by {@link goog.getUid}.
 *
 * Runtime: N,
 * Worstcase space: 2N (no dupes)
 *
 * @param {goog.array.ArrayLike} arr The array from which to remove duplicates.
 * @param {Array=} opt_rv An optional array in which to return the results,
 *     instead of performing the removal inplace.  If specified, the original
 *     array will remain unchanged.
 */
goog.array.removeDuplicates = function(arr, opt_rv) {
  var returnArray = opt_rv || arr;

  var seen = {}, cursorInsert = 0, cursorRead = 0;
  while (cursorRead < arr.length) {
    var current = arr[cursorRead++];

    // Prefix each type with a single character representing the type to
    // prevent conflicting keys (e.g. true and 'true').
    var key = goog.isObject(current) ?
        'o' + goog.getUid(current) :
        (typeof current).charAt(0) + current;

    if (!Object.prototype.hasOwnProperty.call(seen, key)) {
      seen[key] = true;
      returnArray[cursorInsert++] = current;
    }
  }
  returnArray.length = cursorInsert;
};


/**
 * Searches the specified array for the specified target using the binary
 * search algorithm.  If no opt_compareFn is specified, elements are compared
 * using <code>goog.array.defaultCompare</code>, which compares the elements
 * using the built in < and > operators.  This will produce the expected
 * behavior for homogeneous arrays of String(s) and Number(s). The array
 * specified <b>must</b> be sorted in ascending order (as defined by the
 * comparison function).  If the array is not sorted, results are undefined.
 * If the array contains multiple instances of the specified target value, any
 * of these instances may be found.
 *
 * Runtime: O(log n)
 *
 * @param {goog.array.ArrayLike} arr The array to be searched.
 * @param {*} target The sought value.
 * @param {Function=} opt_compareFn Optional comparison function by which the
 *     array is ordered. Should take 2 arguments to compare, and return a
 *     negative number, zero, or a positive number depending on whether the
 *     first argument is less than, equal to, or greater than the second.
 * @return {number} Lowest index of the target value if found, otherwise
 *     (-(insertion point) - 1). The insertion point is where the value should
 *     be inserted into arr to preserve the sorted property.  Return value >= 0
 *     iff target is found.
 */
goog.array.binarySearch = function(arr, target, opt_compareFn) {
  return goog.array.binarySearch_(arr,
      opt_compareFn || goog.array.defaultCompare, false /* isEvaluator */,
      target);
};


/**
 * Selects an index in the specified array using the binary search algorithm.
 * The evaluator receives an element and determines whether the desired index
 * is before, at, or after it.  The evaluator must be consistent (formally,
 * goog.array.map(goog.array.map(arr, evaluator, opt_obj), goog.math.sign)
 * must be monotonically non-increasing).
 *
 * Runtime: O(log n)
 *
 * @param {goog.array.ArrayLike} arr The array to be searched.
 * @param {Function} evaluator Evaluator function that receives 3 arguments
 *     (the element, the index and the array). Should return a negative number,
 *     zero, or a positive number depending on whether the desired index is
 *     before, at, or after the element passed to it.
 * @param {Object=} opt_obj The object to be used as the value of 'this'
 *     within evaluator.
 * @return {number} Index of the leftmost element matched by the evaluator, if
 *     such exists; otherwise (-(insertion point) - 1). The insertion point is
 *     the index of the first element for which the evaluator returns negative,
 *     or arr.length if no such element exists. The return value is non-negative
 *     iff a match is found.
 */
goog.array.binarySelect = function(arr, evaluator, opt_obj) {
  return goog.array.binarySearch_(arr, evaluator, true /* isEvaluator */,
      undefined /* opt_target */, opt_obj);
};


/**
 * Implementation of a binary search algorithm which knows how to use both
 * comparison functions and evaluators. If an evaluator is provided, will call
 * the evaluator with the given optional data object, conforming to the
 * interface defined in binarySelect. Otherwise, if a comparison function is
 * provided, will call the comparison function against the given data object.
 *
 * This implementation purposefully does not use goog.bind or goog.partial for
 * performance reasons.
 *
 * Runtime: O(log n)
 *
 * @param {goog.array.ArrayLike} arr The array to be searched.
 * @param {Function} compareFn Either an evaluator or a comparison function,
 *     as defined by binarySearch and binarySelect above.
 * @param {boolean} isEvaluator Whether the function is an evaluator or a
 *     comparison function.
 * @param {*=} opt_target If the function is a comparison function, then this is
 *     the target to binary search for.
 * @param {Object=} opt_selfObj If the function is an evaluator, this is an
  *    optional this object for the evaluator.
 * @return {number} Lowest index of the target value if found, otherwise
 *     (-(insertion point) - 1). The insertion point is where the value should
 *     be inserted into arr to preserve the sorted property.  Return value >= 0
 *     iff target is found.
 * @private
 */
goog.array.binarySearch_ = function(arr, compareFn, isEvaluator, opt_target,
    opt_selfObj) {
  var left = 0;  // inclusive
  var right = arr.length;  // exclusive
  var found;
  while (left < right) {
    var middle = (left + right) >> 1;
    var compareResult;
    if (isEvaluator) {
      compareResult = compareFn.call(opt_selfObj, arr[middle], middle, arr);
    } else {
      compareResult = compareFn(opt_target, arr[middle]);
    }
    if (compareResult > 0) {
      left = middle + 1;
    } else {
      right = middle;
      // We are looking for the lowest index so we can't return immediately.
      found = !compareResult;
    }
  }
  // left is the index if found, or the insertion point otherwise.
  // ~left is a shorthand for -left - 1.
  return found ? left : ~left;
};


/**
 * Sorts the specified array into ascending order.  If no opt_compareFn is
 * specified, elements are compared using
 * <code>goog.array.defaultCompare</code>, which compares the elements using
 * the built in < and > operators.  This will produce the expected behavior
 * for homogeneous arrays of String(s) and Number(s), unlike the native sort,
 * but will give unpredictable results for heterogenous lists of strings and
 * numbers with different numbers of digits.
 *
 * This sort is not guaranteed to be stable.
 *
 * Runtime: Same as <code>Array.prototype.sort</code>
 *
 * @param {Array} arr The array to be sorted.
 * @param {Function=} opt_compareFn Optional comparison function by which the
 *     array is to be ordered. Should take 2 arguments to compare, and return a
 *     negative number, zero, or a positive number depending on whether the
 *     first argument is less than, equal to, or greater than the second.
 */
goog.array.sort = function(arr, opt_compareFn) {
  // TODO(arv): Update type annotation since null is not accepted.
  goog.asserts.assert(arr.length != null);

  goog.array.ARRAY_PROTOTYPE_.sort.call(
      arr, opt_compareFn || goog.array.defaultCompare);
};


/**
 * Sorts the specified array into ascending order in a stable way.  If no
 * opt_compareFn is specified, elements are compared using
 * <code>goog.array.defaultCompare</code>, which compares the elements using
 * the built in < and > operators.  This will produce the expected behavior
 * for homogeneous arrays of String(s) and Number(s).
 *
 * Runtime: Same as <code>Array.prototype.sort</code>, plus an additional
 * O(n) overhead of copying the array twice.
 *
 * @param {Array} arr The array to be sorted.
 * @param {function(*, *): number=} opt_compareFn Optional comparison function
 *     by which the array is to be ordered. Should take 2 arguments to compare,
 *     and return a negative number, zero, or a positive number depending on
 *     whether the first argument is less than, equal to, or greater than the
 *     second.
 */
goog.array.stableSort = function(arr, opt_compareFn) {
  for (var i = 0; i < arr.length; i++) {
    arr[i] = {index: i, value: arr[i]};
  }
  var valueCompareFn = opt_compareFn || goog.array.defaultCompare;
  function stableCompareFn(obj1, obj2) {
    return valueCompareFn(obj1.value, obj2.value) || obj1.index - obj2.index;
  };
  goog.array.sort(arr, stableCompareFn);
  for (var i = 0; i < arr.length; i++) {
    arr[i] = arr[i].value;
  }
};


/**
 * Sorts an array of objects by the specified object key and compare
 * function. If no compare function is provided, the key values are
 * compared in ascending order using <code>goog.array.defaultCompare</code>.
 * This won't work for keys that get renamed by the compiler. So use
 * {'foo': 1, 'bar': 2} rather than {foo: 1, bar: 2}.
 * @param {Array.<Object>} arr An array of objects to sort.
 * @param {string} key The object key to sort by.
 * @param {Function=} opt_compareFn The function to use to compare key
 *     values.
 */
goog.array.sortObjectsByKey = function(arr, key, opt_compareFn) {
  var compare = opt_compareFn || goog.array.defaultCompare;
  goog.array.sort(arr, function(a, b) {
    return compare(a[key], b[key]);
  });
};


/**
 * Tells if the array is sorted.
 * @param {!Array} arr The array.
 * @param {Function=} opt_compareFn Function to compare the array elements.
 *     Should take 2 arguments to compare, and return a negative number, zero,
 *     or a positive number depending on whether the first argument is less
 *     than, equal to, or greater than the second.
 * @param {boolean=} opt_strict If true no equal elements are allowed.
 * @return {boolean} Whether the array is sorted.
 */
goog.array.isSorted = function(arr, opt_compareFn, opt_strict) {
  var compare = opt_compareFn || goog.array.defaultCompare;
  for (var i = 1; i < arr.length; i++) {
    var compareResult = compare(arr[i - 1], arr[i]);
    if (compareResult > 0 || compareResult == 0 && opt_strict) {
      return false;
    }
  }
  return true;
};


/**
 * Compares two arrays for equality. Two arrays are considered equal if they
 * have the same length and their corresponding elements are equal according to
 * the comparison function.
 *
 * @param {goog.array.ArrayLike} arr1 The first array to compare.
 * @param {goog.array.ArrayLike} arr2 The second array to compare.
 * @param {Function=} opt_equalsFn Optional comparison function.
 *     Should take 2 arguments to compare, and return true if the arguments
 *     are equal. Defaults to {@link goog.array.defaultCompareEquality} which
 *     compares the elements using the built-in '===' operator.
 * @return {boolean} Whether the two arrays are equal.
 */
goog.array.equals = function(arr1, arr2, opt_equalsFn) {
  if (!goog.isArrayLike(arr1) || !goog.isArrayLike(arr2) ||
      arr1.length != arr2.length) {
    return false;
  }
  var l = arr1.length;
  var equalsFn = opt_equalsFn || goog.array.defaultCompareEquality;
  for (var i = 0; i < l; i++) {
    if (!equalsFn(arr1[i], arr2[i])) {
      return false;
    }
  }
  return true;
};


/**
 * @deprecated Use {@link goog.array.equals}.
 * @param {goog.array.ArrayLike} arr1 See {@link goog.array.equals}.
 * @param {goog.array.ArrayLike} arr2 See {@link goog.array.equals}.
 * @param {Function=} opt_equalsFn See {@link goog.array.equals}.
 * @return {boolean} See {@link goog.array.equals}.
 */
goog.array.compare = function(arr1, arr2, opt_equalsFn) {
  return goog.array.equals(arr1, arr2, opt_equalsFn);
};


/**
 * 3-way array compare function.
 * @param {!goog.array.ArrayLike} arr1 The first array to compare.
 * @param {!goog.array.ArrayLike} arr2 The second array to compare.
 * @param {(function(*, *): number)=} opt_compareFn Optional comparison function
 *     by which the array is to be ordered. Should take 2 arguments to compare,
 *     and return a negative number, zero, or a positive number depending on
 *     whether the first argument is less than, equal to, or greater than the
 *     second.
 * @return {number} Negative number, zero, or a positive number depending on
 *     whether the first argument is less than, equal to, or greater than the
 *     second.
 */
goog.array.compare3 = function(arr1, arr2, opt_compareFn) {
  var compare = opt_compareFn || goog.array.defaultCompare;
  var l = Math.min(arr1.length, arr2.length);
  for (var i = 0; i < l; i++) {
    var result = compare(arr1[i], arr2[i]);
    if (result != 0) {
      return result;
    }
  }
  return goog.array.defaultCompare(arr1.length, arr2.length);
};


/**
 * Compares its two arguments for order, using the built in < and >
 * operators.
 * @param {*} a The first object to be compared.
 * @param {*} b The second object to be compared.
 * @return {number} A negative number, zero, or a positive number as the first
 *     argument is less than, equal to, or greater than the second.
 */
goog.array.defaultCompare = function(a, b) {
  return a > b ? 1 : a < b ? -1 : 0;
};


/**
 * Compares its two arguments for equality, using the built in === operator.
 * @param {*} a The first object to compare.
 * @param {*} b The second object to compare.
 * @return {boolean} True if the two arguments are equal, false otherwise.
 */
goog.array.defaultCompareEquality = function(a, b) {
  return a === b;
};


/**
 * Inserts a value into a sorted array. The array is not modified if the
 * value is already present.
 * @param {Array} array The array to modify.
 * @param {*} value The object to insert.
 * @param {Function=} opt_compareFn Optional comparison function by which the
 *     array is ordered. Should take 2 arguments to compare, and return a
 *     negative number, zero, or a positive number depending on whether the
 *     first argument is less than, equal to, or greater than the second.
 * @return {boolean} True if an element was inserted.
 */
goog.array.binaryInsert = function(array, value, opt_compareFn) {
  var index = goog.array.binarySearch(array, value, opt_compareFn);
  if (index < 0) {
    goog.array.insertAt(array, value, -(index + 1));
    return true;
  }
  return false;
};


/**
 * Removes a value from a sorted array.
 * @param {Array} array The array to modify.
 * @param {*} value The object to remove.
 * @param {Function=} opt_compareFn Optional comparison function by which the
 *     array is ordered. Should take 2 arguments to compare, and return a
 *     negative number, zero, or a positive number depending on whether the
 *     first argument is less than, equal to, or greater than the second.
 * @return {boolean} True if an element was removed.
 */
goog.array.binaryRemove = function(array, value, opt_compareFn) {
  var index = goog.array.binarySearch(array, value, opt_compareFn);
  return (index >= 0) ? goog.array.removeAt(array, index) : false;
};


/**
 * Splits an array into disjoint buckets according to a splitting function.
 * @param {Array} array The array.
 * @param {Function} sorter Function to call for every element.  This
 *     takes 3 arguments (the element, the index and the array) and must
 *     return a valid object key (a string, number, etc), or undefined, if
 *     that object should not be placed in a bucket.
 * @return {!Object} An object, with keys being all of the unique return values
 *     of sorter, and values being arrays containing the items for
 *     which the splitter returned that key.
 */
goog.array.bucket = function(array, sorter) {
  var buckets = {};

  for (var i = 0; i < array.length; i++) {
    var value = array[i];
    var key = sorter(value, i, array);
    if (goog.isDef(key)) {
      // Push the value to the right bucket, creating it if necessary.
      var bucket = buckets[key] || (buckets[key] = []);
      bucket.push(value);
    }
  }

  return buckets;
};


/**
 * Returns an array consisting of the given value repeated N times.
 *
 * @param {*} value The value to repeat.
 * @param {number} n The repeat count.
 * @return {!Array.<*>} An array with the repeated value.
 */
goog.array.repeat = function(value, n) {
  var array = [];
  for (var i = 0; i < n; i++) {
    array[i] = value;
  }
  return array;
};


/**
 * Returns an array consisting of every argument with all arrays
 * expanded in-place recursively.
 *
 * @param {...*} var_args The values to flatten.
 * @return {!Array.<*>} An array containing the flattened values.
 */
goog.array.flatten = function(var_args) {
  var result = [];
  for (var i = 0; i < arguments.length; i++) {
    var element = arguments[i];
    if (goog.isArray(element)) {
      result.push.apply(result, goog.array.flatten.apply(null, element));
    } else {
      result.push(element);
    }
  }
  return result;
};


/**
 * Rotates an array in-place. After calling this method, the element at
 * index i will be the element previously at index (i - n) %
 * array.length, for all values of i between 0 and array.length - 1,
 * inclusive.
 *
 * For example, suppose list comprises [t, a, n, k, s]. After invoking
 * rotate(array, 1) (or rotate(array, -4)), array will comprise [s, t, a, n, k].
 *
 * @param {!Array.<*>} array The array to rotate.
 * @param {number} n The amount to rotate.
 * @return {!Array.<*>} The array.
 */
goog.array.rotate = function(array, n) {
  goog.asserts.assert(array.length != null);

  if (array.length) {
    n %= array.length;
    if (n > 0) {
      goog.array.ARRAY_PROTOTYPE_.unshift.apply(array, array.splice(-n, n));
    } else if (n < 0) {
      goog.array.ARRAY_PROTOTYPE_.push.apply(array, array.splice(0, -n));
    }
  }
  return array;
};


/**
 * Creates a new array for which the element at position i is an array of the
 * ith element of the provided arrays.  The returned array will only be as long
 * as the shortest array provided; additional values are ignored.  For example,
 * the result of zipping [1, 2] and [3, 4, 5] is [[1,3], [2, 4]].
 *
 * This is similar to the zip() function in Python.  See {@link
 * http://docs.python.org/library/functions.html#zip}
 *
 * @param {...!goog.array.ArrayLike} var_args Arrays to be combined.
 * @return {!Array.<!Array>} A new array of arrays created from provided arrays.
 */
goog.array.zip = function(var_args) {
  if (!arguments.length) {
    return [];
  }
  var result = [];
  for (var i = 0; true; i++) {
    var value = [];
    for (var j = 0; j < arguments.length; j++) {
      var arr = arguments[j];
      // If i is larger than the array length, this is the shortest array.
      if (i >= arr.length) {
        return result;
      }
      value.push(arr[i]);
    }
    result.push(value);
  }
};


/**
 * Shuffles the values in the specified array using the Fisher-Yates in-place
 * shuffle (also known as the Knuth Shuffle). By default, calls Math.random()
 * and so resets the state of that random number generator. Similarly, may reset
 * the state of the any other specified random number generator.
 *
 * Runtime: O(n)
 *
 * @param {!Array} arr The array to be shuffled.
 * @param {Function=} opt_randFn Optional random function to use for shuffling.
 *     Takes no arguments, and returns a random number on the interval [0, 1).
 *     Defaults to Math.random() using JavaScript's built-in Math library.
 */
goog.array.shuffle = function(arr, opt_randFn) {
  var randFn = opt_randFn || Math.random;

  for (var i = arr.length - 1; i > 0; i--) {
    // Choose a random array index in [0, i] (inclusive with i).
    var j = Math.floor(randFn() * (i + 1));

    var tmp = arr[i];
    arr[i] = arr[j];
    arr[j] = tmp;
  }
};

//javascript/closure/math/coordinate.js
// Copyright 2006 The Closure Library Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @fileoverview A utility class for representing two-dimensional positions.
 * @author pupius@google.com (Daniel Pupius)
 */


goog.provide('goog.math.Coordinate');



/**
 * Class for representing coordinates and positions.
 * @param {number=} opt_x Left, defaults to 0.
 * @param {number=} opt_y Top, defaults to 0.
 * @constructor
 */
goog.math.Coordinate = function(opt_x, opt_y) {
  /**
   * X-value
   * @type {number}
   */
  this.x = goog.isDef(opt_x) ? opt_x : 0;

  /**
   * Y-value
   * @type {number}
   */
  this.y = goog.isDef(opt_y) ? opt_y : 0;
};


/**
 * Returns a new copy of the coordinate.
 * @return {!goog.math.Coordinate} A clone of this coordinate.
 */
goog.math.Coordinate.prototype.clone = function() {
  return new goog.math.Coordinate(this.x, this.y);
};


if (goog.DEBUG) {
  /**
   * Returns a nice string representing the coordinate.
   * @return {string} In the form (50, 73).
   */
  goog.math.Coordinate.prototype.toString = function() {
    return '(' + this.x + ', ' + this.y + ')';
  };
}


/**
 * Compares coordinates for equality.
 * @param {goog.math.Coordinate} a A Coordinate.
 * @param {goog.math.Coordinate} b A Coordinate.
 * @return {boolean} True iff the coordinates are equal, or if both are null.
 */
goog.math.Coordinate.equals = function(a, b) {
  if (a == b) {
    return true;
  }
  if (!a || !b) {
    return false;
  }
  return a.x == b.x && a.y == b.y;
};


/**
 * Returns the distance between two coordinates.
 * @param {!goog.math.Coordinate} a A Coordinate.
 * @param {!goog.math.Coordinate} b A Coordinate.
 * @return {number} The distance between {@code a} and {@code b}.
 */
goog.math.Coordinate.distance = function(a, b) {
  var dx = a.x - b.x;
  var dy = a.y - b.y;
  return Math.sqrt(dx * dx + dy * dy);
};


/**
 * Returns the squared distance between two coordinates. Squared distances can
 * be used for comparisons when the actual value is not required.
 *
 * Performance note: eliminating the square root is an optimization often used
 * in lower-level languages, but the speed difference is not nearly as
 * pronounced in JavaScript (only a few percent.)
 *
 * @param {!goog.math.Coordinate} a A Coordinate.
 * @param {!goog.math.Coordinate} b A Coordinate.
 * @return {number} The squared distance between {@code a} and {@code b}.
 */
goog.math.Coordinate.squaredDistance = function(a, b) {
  var dx = a.x - b.x;
  var dy = a.y - b.y;
  return dx * dx + dy * dy;
};


/**
 * Returns the difference between two coordinates as a new
 * goog.math.Coordinate.
 * @param {!goog.math.Coordinate} a A Coordinate.
 * @param {!goog.math.Coordinate} b A Coordinate.
 * @return {!goog.math.Coordinate} A Coordinate representing the difference
 *     between {@code a} and {@code b}.
 */
goog.math.Coordinate.difference = function(a, b) {
  return new goog.math.Coordinate(a.x - b.x, a.y - b.y);
};


/**
 * Returns the sum of two coordinates as a new goog.math.Coordinate.
 * @param {!goog.math.Coordinate} a A Coordinate.
 * @param {!goog.math.Coordinate} b A Coordinate.
 * @return {!goog.math.Coordinate} A Coordinate representing the sum of the two
 *     coordinates.
 */
goog.math.Coordinate.sum = function(a, b) {
  return new goog.math.Coordinate(a.x + b.x, a.y + b.y);
};

//javascript/closure/math/size.js
// Copyright 2007 The Closure Library Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @fileoverview A utility class for representing two-dimensional sizes.
 * @author pupius@google.com (Dan Pupius)
 * @author brenneman@google.com (Shawn Brenneman)
 */


goog.provide('goog.math.Size');



/**
 * Class for representing sizes consisting of a width and height. Undefined
 * width and height support is deprecated and results in compiler warning.
 * @param {number} width Width.
 * @param {number} height Height.
 * @constructor
 */
goog.math.Size = function(width, height) {
  /**
   * Width
   * @type {number}
   */
  this.width = width;

  /**
   * Height
   * @type {number}
   */
  this.height = height;
};


/**
 * Compares sizes for equality.
 * @param {goog.math.Size} a A Size.
 * @param {goog.math.Size} b A Size.
 * @return {boolean} True iff the sizes have equal widths and equal
 *     heights, or if both are null.
 */
goog.math.Size.equals = function(a, b) {
  if (a == b) {
    return true;
  }
  if (!a || !b) {
    return false;
  }
  return a.width == b.width && a.height == b.height;
};


/**
 * @return {!goog.math.Size} A new copy of the Size.
 */
goog.math.Size.prototype.clone = function() {
  return new goog.math.Size(this.width, this.height);
};


if (goog.DEBUG) {
  /**
   * Returns a nice string representing size.
   * @return {string} In the form (50 x 73).
   */
  goog.math.Size.prototype.toString = function() {
    return '(' + this.width + ' x ' + this.height + ')';
  };
}


/**
 * @return {number} The longer of the two dimensions in the size.
 */
goog.math.Size.prototype.getLongest = function() {
  return Math.max(this.width, this.height);
};


/**
 * @return {number} The shorter of the two dimensions in the size.
 */
goog.math.Size.prototype.getShortest = function() {
  return Math.min(this.width, this.height);
};


/**
 * @return {number} The area of the size (width * height).
 */
goog.math.Size.prototype.area = function() {
  return this.width * this.height;
};


/**
 * @return {number} The perimeter of the size (width + height) * 2.
 */
goog.math.Size.prototype.perimeter = function() {
  return (this.width + this.height) * 2;
};


/**
 * @return {number} The ratio of the size's width to its height.
 */
goog.math.Size.prototype.aspectRatio = function() {
  return this.width / this.height;
};


/**
 * @return {boolean} True if the size has zero area, false if both dimensions
 *     are non-zero numbers.
 */
goog.math.Size.prototype.isEmpty = function() {
  return !this.area();
};


/**
 * Clamps the width and height parameters upward to integer values.
 * @return {!goog.math.Size} This size with ceil'd components.
 */
goog.math.Size.prototype.ceil = function() {
  this.width = Math.ceil(this.width);
  this.height = Math.ceil(this.height);
  return this;
};


/**
 * @param {!goog.math.Size} target The target size.
 * @return {boolean} True if this Size is the same size or smaller than the
 *     target size in both dimensions.
 */
goog.math.Size.prototype.fitsInside = function(target) {
  return this.width <= target.width && this.height <= target.height;
};


/**
 * Clamps the width and height parameters downward to integer values.
 * @return {!goog.math.Size} This size with floored components.
 */
goog.math.Size.prototype.floor = function() {
  this.width = Math.floor(this.width);
  this.height = Math.floor(this.height);
  return this;
};


/**
 * Rounds the width and height parameters to integer values.
 * @return {!goog.math.Size} This size with rounded components.
 */
goog.math.Size.prototype.round = function() {
  this.width = Math.round(this.width);
  this.height = Math.round(this.height);
  return this;
};


/**
 * Scales the size uniformly by a factor.
 * @param {number} s The scale factor.
 * @return {!goog.math.Size} This Size object after scaling.
 */
goog.math.Size.prototype.scale = function(s) {
  this.width *= s;
  this.height *= s;
  return this;
};


/**
 * Uniformly scales the size to fit inside the dimensions of a given size. The
 * original aspect ratio will be preserved.
 *
 * This function assumes that both Sizes contain strictly positive dimensions.
 * @param {!goog.math.Size} target The target size.
 * @return {!goog.math.Size} This Size object, after optional scaling.
 */
goog.math.Size.prototype.scaleToFit = function(target) {
  var s = this.aspectRatio() > target.aspectRatio() ?
      target.width / this.width :
      target.height / this.height;

  return this.scale(s);
};

//javascript/closure/object/object.js
// Copyright 2006 The Closure Library Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @fileoverview Utilities for manipulating objects/maps/hashes.
 * @author pupius@google.com (Daniel Pupius)
 * @author arv@google.com (Erik Arvidsson)
 * @author pallosp@google.com (Peter Pallos)
 */

goog.provide('goog.object');


/**
 * Calls a function for each element in an object/map/hash.
 *
 * @param {Object} obj The object over which to iterate.
 * @param {Function} f The function to call for every element. This function
 *     takes 3 arguments (the element, the index and the object)
 *     and the return value is irrelevant.
 * @param {Object=} opt_obj This is used as the 'this' object within f.
 */
goog.object.forEach = function(obj, f, opt_obj) {
  for (var key in obj) {
    f.call(opt_obj, obj[key], key, obj);
  }
};


/**
 * Calls a function for each element in an object/map/hash. If that call returns
 * true, adds the element to a new object.
 *
 * @param {Object} obj The object over which to iterate.
 * @param {Function} f The function to call for every element. This
 *     function takes 3 arguments (the element, the index and the object)
 *     and should return a boolean. If the return value is true the
 *     element is added to the result object. If it is false the
 *     element is not included.
 * @param {Object=} opt_obj This is used as the 'this' object within f.
 * @return {!Object} a new object in which only elements that passed the test
 *     are present.
 */
goog.object.filter = function(obj, f, opt_obj) {
  var res = {};
  for (var key in obj) {
    if (f.call(opt_obj, obj[key], key, obj)) {
      res[key] = obj[key];
    }
  }
  return res;
};


/**
 * For every element in an object/map/hash calls a function and inserts the
 * result into a new object.
 *
 * @param {Object} obj The object over which to iterate.
 * @param {Function} f The function to call for every element. This function
 *     takes 3 arguments (the element, the index and the object)
 *     and should return something. The result will be inserted
 *     into a new object.
 * @param {Object=} opt_obj This is used as the 'this' object within f.
 * @return {!Object} a new object with the results from f.
 */
goog.object.map = function(obj, f, opt_obj) {
  var res = {};
  for (var key in obj) {
    res[key] = f.call(opt_obj, obj[key], key, obj);
  }
  return res;
};


/**
 * Calls a function for each element in an object/map/hash. If any
 * call returns true, returns true (without checking the rest). If
 * all calls return false, returns false.
 *
 * @param {Object} obj The object to check.
 * @param {Function} f The function to call for every element. This function
 *     takes 3 arguments (the element, the index and the object) and should
 *     return a boolean.
 * @param {Object=} opt_obj This is used as the 'this' object within f.
 * @return {boolean} true if any element passes the test.
 */
goog.object.some = function(obj, f, opt_obj) {
  for (var key in obj) {
    if (f.call(opt_obj, obj[key], key, obj)) {
      return true;
    }
  }
  return false;
};


/**
 * Calls a function for each element in an object/map/hash. If
 * all calls return true, returns true. If any call returns false, returns
 * false at this point and does not continue to check the remaining elements.
 *
 * @param {Object} obj The object to check.
 * @param {Function} f The function to call for every element. This function
 *     takes 3 arguments (the element, the index and the object) and should
 *     return a boolean.
 * @param {Object=} opt_obj This is used as the 'this' object within f.
 * @return {boolean} false if any element fails the test.
 */
goog.object.every = function(obj, f, opt_obj) {
  for (var key in obj) {
    if (!f.call(opt_obj, obj[key], key, obj)) {
      return false;
    }
  }
  return true;
};


/**
 * Returns the number of key-value pairs in the object map.
 *
 * @param {Object} obj The object for which to get the number of key-value
 *     pairs.
 * @return {number} The number of key-value pairs in the object map.
 */
goog.object.getCount = function(obj) {
  // JS1.5 has __count__ but it has been deprecated so it raises a warning...
  // in other words do not use. Also __count__ only includes the fields on the
  // actual object and not in the prototype chain.
  var rv = 0;
  for (var key in obj) {
    rv++;
  }
  return rv;
};


/**
 * Returns one key from the object map, if any exists.
 * For map literals the returned key will be the first one in most of the
 * browsers (a know exception is Konqueror).
 *
 * @param {Object} obj The object to pick a key from.
 * @return {string|undefined} The key or undefined if the object is empty.
 */
goog.object.getAnyKey = function(obj) {
  for (var key in obj) {
    return key;
  }
};


/**
 * Returns one value from the object map, if any exists.
 * For map literals the returned value will be the first one in most of the
 * browsers (a know exception is Konqueror).
 *
 * @param {Object} obj The object to pick a value from.
 * @return {*} The value or undefined if the object is empty.
 */
goog.object.getAnyValue = function(obj) {
  for (var key in obj) {
    return obj[key];
  }
};


/**
 * Whether the object/hash/map contains the given object as a value.
 * An alias for goog.object.containsValue(obj, val).
 *
 * @param {Object} obj The object in which to look for val.
 * @param {*} val The object for which to check.
 * @return {boolean} true if val is present.
 */
goog.object.contains = function(obj, val) {
  return goog.object.containsValue(obj, val);
};


/**
 * Returns the values of the object/map/hash.
 *
 * @param {Object} obj The object from which to get the values.
 * @return {!Array} The values in the object/map/hash.
 */
goog.object.getValues = function(obj) {
  var res = [];
  var i = 0;
  for (var key in obj) {
    res[i++] = obj[key];
  }
  return res;
};


/**
 * Returns the keys of the object/map/hash.
 *
 * @param {Object} obj The object from which to get the keys.
 * @return {!Array.<string>} Array of property keys.
 */
goog.object.getKeys = function(obj) {
  var res = [];
  var i = 0;
  for (var key in obj) {
    res[i++] = key;
  }
  return res;
};


/**
 * Get a value from an object multiple levels deep.  This is useful for
 * pulling values from deeply nested objects, such as JSON responses.
 * Example usage: getValueByKeys(jsonObj, 'foo', 'entries', 3)
 *
 * @param {!Object} obj An object to get the value from.  Can be array-like.
 * @param {...(string|number|!Array.<number|string>)} var_args A number of keys
 *     (as strings, or nubmers, for array-like objects).  Can also be
 *     specified as a single array of keys.
 * @return {*} The resulting value.  If, at any point, the value for a key
 *     is undefined, returns undefined.
 */
goog.object.getValueByKeys = function(obj, var_args) {
  var isArrayLike = goog.isArrayLike(var_args);
  var keys = isArrayLike ? var_args : arguments;

  // Start with the 2nd parameter for the variable parameters syntax.
  for (var i = isArrayLike ? 0 : 1; i < keys.length; i++) {
    obj = obj[keys[i]];
    if (!goog.isDef(obj)) {
      break;
    }
  }

  return obj;
};


/**
 * Whether the object/map/hash contains the given key.
 *
 * @param {Object} obj The object in which to look for key.
 * @param {*} key The key for which to check.
 * @return {boolean} true If the map contains the key.
 */
goog.object.containsKey = function(obj, key) {
  return key in obj;
};


/**
 * Whether the object/map/hash contains the given value. This is O(n).
 *
 * @param {Object} obj The object in which to look for val.
 * @param {*} val The value for which to check.
 * @return {boolean} true If the map contains the value.
 */
goog.object.containsValue = function(obj, val) {
  for (var key in obj) {
    if (obj[key] == val) {
      return true;
    }
  }
  return false;
};


/**
 * Searches an object for an element that satisfies the given condition and
 * returns its key.
 * @param {Object} obj The object to search in.
 * @param {function(*, string, Object): boolean} f The function to call for
 *     every element. Takes 3 arguments (the value, the key and the object) and
 *     should return a boolean.
 * @param {Object=} opt_this An optional "this" context for the function.
 * @return {string|undefined} The key of an element for which the function
 *     returns true or undefined if no such element is found.
 */
goog.object.findKey = function(obj, f, opt_this) {
  for (var key in obj) {
    if (f.call(opt_this, obj[key], key, obj)) {
      return key;
    }
  }
  return undefined;
};


/**
 * Searches an object for an element that satisfies the given condition and
 * returns its value.
 * @param {Object} obj The object to search in.
 * @param {function(*, string, Object): boolean} f The function to call for
 *     every element. Takes 3 arguments (the value, the key and the object) and
 *     should return a boolean.
 * @param {Object=} opt_this An optional "this" context for the function.
 * @return {*} The value of an element for which the function returns true or
 *     undefined if no such element is found.
 */
goog.object.findValue = function(obj, f, opt_this) {
  var key = goog.object.findKey(obj, f, opt_this);
  return key && obj[key];
};


/**
 * Whether the object/map/hash is empty.
 *
 * @param {Object} obj The object to test.
 * @return {boolean} true if obj is empty.
 */
goog.object.isEmpty = function(obj) {
  for (var key in obj) {
    return false;
  }
  return true;
};


/**
 * Removes all key value pairs from the object/map/hash.
 *
 * @param {Object} obj The object to clear.
 */
goog.object.clear = function(obj) {
  for (var i in obj) {
    delete obj[i];
  }
};


/**
 * Removes a key-value pair based on the key.
 *
 * @param {Object} obj The object from which to remove the key.
 * @param {*} key The key to remove.
 * @return {boolean} Whether an element was removed.
 */
goog.object.remove = function(obj, key) {
  var rv;
  if ((rv = key in obj)) {
    delete obj[key];
  }
  return rv;
};


/**
 * Adds a key-value pair to the object. Throws an exception if the key is
 * already in use. Use set if you want to change an existing pair.
 *
 * @param {Object} obj The object to which to add the key-value pair.
 * @param {string} key The key to add.
 * @param {*} val The value to add.
 */
goog.object.add = function(obj, key, val) {
  if (key in obj) {
    throw Error('The object already contains the key "' + key + '"');
  }
  goog.object.set(obj, key, val);
};


/**
 * Returns the value for the given key.
 *
 * @param {Object} obj The object from which to get the value.
 * @param {string} key The key for which to get the value.
 * @param {*=} opt_val The value to return if no item is found for the given
 *     key (default is undefined).
 * @return {*} The value for the given key.
 */
goog.object.get = function(obj, key, opt_val) {
  if (key in obj) {
    return obj[key];
  }
  return opt_val;
};


/**
 * Adds a key-value pair to the object/map/hash.
 *
 * @param {Object} obj The object to which to add the key-value pair.
 * @param {string} key The key to add.
 * @param {*} value The value to add.
 */
goog.object.set = function(obj, key, value) {
  obj[key] = value;
};


/**
 * Adds a key-value pair to the object/map/hash if it doesn't exist yet.
 *
 * @param {Object} obj The object to which to add the key-value pair.
 * @param {string} key The key to add.
 * @param {*} value The value to add if the key wasn't present.
 * @return {*} The value of the entry at the end of the function.
 */
goog.object.setIfUndefined = function(obj, key, value) {
  return key in obj ? obj[key] : (obj[key] = value);
};


/**
 * Does a flat clone of the object.
 *
 * @param {Object} obj Object to clone.
 * @return {!Object} Clone of the input object.
 */
goog.object.clone = function(obj) {
  // We cannot use the prototype trick because a lot of methods depend on where
  // the actual key is set.

  var res = {};
  for (var key in obj) {
    res[key] = obj[key];
  }
  return res;
  // We could also use goog.mixin but I wanted this to be independent from that.
};


/**
 * Clones a value. The input may be an Object, Array, or basic type. Objects and
 * arrays will be cloned recursively.
 *
 * WARNINGS:
 * <code>goog.object.unsafeClone</code> does not detect reference loops. Objects
 * that refer to themselves will cause infinite recursion.
 *
 * <code>goog.object.unsafeClone</code> is unaware of unique identifiers, and
 * copies UIDs created by <code>getUid</code> into cloned results.
 *
 * @param {*} obj The value to clone.
 * @return {*} A clone of the input value.
 */
goog.object.unsafeClone = function(obj) {
  var type = goog.typeOf(obj);
  if (type == 'object' || type == 'array') {
    if (obj.clone) {
      return obj.clone();
    }
    var clone = type == 'array' ? [] : {};
    for (var key in obj) {
      clone[key] = goog.object.unsafeClone(obj[key]);
    }
    return clone;
  }

  return obj;
};


/**
 * Returns a new object in which all the keys and values are interchanged
 * (keys become values and values become keys). If multiple keys map to the
 * same value, the chosen transposed value is implementation-dependent.
 *
 * @param {Object} obj The object to transpose.
 * @return {!Object} The transposed object.
 */
goog.object.transpose = function(obj) {
  var transposed = {};
  for (var key in obj) {
    transposed[obj[key]] = key;
  }
  return transposed;
};


/**
 * The names of the fields that are defined on Object.prototype.
 * @type {Array.<string>}
 * @private
 */
goog.object.PROTOTYPE_FIELDS_ = [
  'constructor',
  'hasOwnProperty',
  'isPrototypeOf',
  'propertyIsEnumerable',
  'toLocaleString',
  'toString',
  'valueOf'
];


/**
 * Extends an object with another object.
 * This operates 'in-place'; it does not create a new Object.
 *
 * Example:
 * var o = {};
 * goog.object.extend(o, {a: 0, b: 1});
 * o; // {a: 0, b: 1}
 * goog.object.extend(o, {c: 2});
 * o; // {a: 0, b: 1, c: 2}
 *
 * @param {Object} target  The object to modify.
 * @param {...Object} var_args The objects from which values will be copied.
 */
goog.object.extend = function(target, var_args) {
  var key, source;
  for (var i = 1; i < arguments.length; i++) {
    source = arguments[i];
    for (key in source) {
      target[key] = source[key];
    }

    // For IE the for-in-loop does not contain any properties that are not
    // enumerable on the prototype object (for example isPrototypeOf from
    // Object.prototype) and it will also not include 'replace' on objects that
    // extend String and change 'replace' (not that it is common for anyone to
    // extend anything except Object).

    for (var j = 0; j < goog.object.PROTOTYPE_FIELDS_.length; j++) {
      key = goog.object.PROTOTYPE_FIELDS_[j];
      if (Object.prototype.hasOwnProperty.call(source, key)) {
        target[key] = source[key];
      }
    }
  }
};


/**
 * Creates a new object built from the key-value pairs provided as arguments.
 * @param {...*} var_args If only one argument is provided and it is an array
 *     then this is used as the arguments,  otherwise even arguments are used as
 *     the property names and odd arguments are used as the property values.
 * @return {!Object} The new object.
 * @throws {Error} If there are uneven number of arguments or there is only one
 *     non array argument.
 */
goog.object.create = function(var_args) {
  var argLength = arguments.length;
  if (argLength == 1 && goog.isArray(arguments[0])) {
    return goog.object.create.apply(null, arguments[0]);
  }

  if (argLength % 2) {
    throw Error('Uneven number of arguments');
  }

  var rv = {};
  for (var i = 0; i < argLength; i += 2) {
    rv[arguments[i]] = arguments[i + 1];
  }
  return rv;
};


/**
 * Creates a new object where the property names come from the arguments but
 * the value is always set to true
 * @param {...*} var_args If only one argument is provided and it is an array
 *     then this is used as the arguments,  otherwise the arguments are used
 *     as the property names.
 * @return {!Object} The new object.
 */
goog.object.createSet = function(var_args) {
  var argLength = arguments.length;
  if (argLength == 1 && goog.isArray(arguments[0])) {
    return goog.object.createSet.apply(null, arguments[0]);
  }

  var rv = {};
  for (var i = 0; i < argLength; i++) {
    rv[arguments[i]] = true;
  }
  return rv;
};

//javascript/closure/useragent/useragent.js
// Copyright 2006 The Closure Library Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @fileoverview Rendering engine detection.
 * @see <a href="http://www.useragentstring.com/">User agent strings</a>
 * For information on the browser brand (such as Safari versus Chrome), see
 * goog.userAgent.product.
 * @author pupius@google.com (Daniel Pupius)
 * @author arv@google.com (Erik Arvidsson)
 * @see ../demos/useragent.html
 */

goog.provide('goog.userAgent');

goog.require('goog.string');


/**
 * @define {boolean} Whether we know at compile-time that the browser is IE.
 */
goog.userAgent.ASSUME_IE = false;


/**
 * @define {boolean} Whether we know at compile-time that the browser is GECKO.
 */
goog.userAgent.ASSUME_GECKO = false;


/**
 * @define {boolean} Whether we know at compile-time that the browser is WEBKIT.
 */
goog.userAgent.ASSUME_WEBKIT = false;


/**
 * @define {boolean} Whether we know at compile-time that the browser is a
 *     mobile device running WebKit e.g. iPhone or Android.
 */
goog.userAgent.ASSUME_MOBILE_WEBKIT = false;


/**
 * @define {boolean} Whether we know at compile-time that the browser is OPERA.
 */
goog.userAgent.ASSUME_OPERA = false;


/**
 * @define {boolean} Whether the {@code goog.userAgent.isVersion} function will
 *     return true for any version.
 */
goog.userAgent.ASSUME_ANY_VERSION = false;


/**
 * Whether we know the browser engine at compile-time.
 * @type {boolean}
 * @private
 */
goog.userAgent.BROWSER_KNOWN_ =
    goog.userAgent.ASSUME_IE ||
    goog.userAgent.ASSUME_GECKO ||
    goog.userAgent.ASSUME_MOBILE_WEBKIT ||
    goog.userAgent.ASSUME_WEBKIT ||
    goog.userAgent.ASSUME_OPERA;


/**
 * Returns the userAgent string for the current browser.
 * Some user agents (I'm thinking of you, Gears WorkerPool) do not expose a
 * navigator object off the global scope.  In that case we return null.
 *
 * @return {?string} The userAgent string or null if there is none.
 */
goog.userAgent.getUserAgentString = function() {
  return goog.global['navigator'] ? goog.global['navigator'].userAgent : null;
};


/**
 * @return {Object} The native navigator object.
 */
goog.userAgent.getNavigator = function() {
  // Need a local navigator reference instead of using the global one,
  // to avoid the rare case where they reference different objects.
  // (goog.gears.FakeWorkerPool, for example).
  return goog.global['navigator'];
};


/**
 * Initializer for goog.userAgent.
 *
 * This is a named function so that it can be stripped via the jscompiler
 * option for stripping types.
 * @private
 */
goog.userAgent.init_ = function() {
  /**
   * Whether the user agent string denotes Opera.
   * @type {boolean}
   * @private
   */
  goog.userAgent.detectedOpera_ = false;

  /**
   * Whether the user agent string denotes Internet Explorer. This includes
   * other browsers using Trident as its rendering engine. For example AOL
   * and Netscape 8
   * @type {boolean}
   * @private
   */
  goog.userAgent.detectedIe_ = false;

  /**
   * Whether the user agent string denotes WebKit. WebKit is the rendering
   * engine that Safari, Android and others use.
   * @type {boolean}
   * @private
   */
  goog.userAgent.detectedWebkit_ = false;

  /**
   * Whether the user agent string denotes a mobile device.
   * @type {boolean}
   * @private
   */
  goog.userAgent.detectedMobile_ = false;

  /**
   * Whether the user agent string denotes Gecko. Gecko is the rendering
   * engine used by Mozilla, Mozilla Firefox, Camino and many more.
   * @type {boolean}
   * @private
   */
  goog.userAgent.detectedGecko_ = false;

  var ua;
  if (!goog.userAgent.BROWSER_KNOWN_ &&
      (ua = goog.userAgent.getUserAgentString())) {
    var navigator = goog.userAgent.getNavigator();
    goog.userAgent.detectedOpera_ = ua.indexOf('Opera') == 0;
    goog.userAgent.detectedIe_ = !goog.userAgent.detectedOpera_ &&
        ua.indexOf('MSIE') != -1;
    goog.userAgent.detectedWebkit_ = !goog.userAgent.detectedOpera_ &&
        ua.indexOf('WebKit') != -1;
    // WebKit also gives navigator.product string equal to 'Gecko'.
    goog.userAgent.detectedMobile_ = goog.userAgent.detectedWebkit_ &&
        ua.indexOf('Mobile') != -1;
    goog.userAgent.detectedGecko_ = !goog.userAgent.detectedOpera_ &&
        !goog.userAgent.detectedWebkit_ && navigator.product == 'Gecko';
  }
};


if (!goog.userAgent.BROWSER_KNOWN_) {
  goog.userAgent.init_();
}


/**
 * Whether the user agent is Opera.
 * @type {boolean}
 */
goog.userAgent.OPERA = goog.userAgent.BROWSER_KNOWN_ ?
    goog.userAgent.ASSUME_OPERA : goog.userAgent.detectedOpera_;


/**
 * Whether the user agent is Internet Explorer. This includes other browsers
 * using Trident as its rendering engine. For example AOL and Netscape 8
 * @type {boolean}
 */
goog.userAgent.IE = goog.userAgent.BROWSER_KNOWN_ ?
    goog.userAgent.ASSUME_IE : goog.userAgent.detectedIe_;


/**
 * Whether the user agent is Gecko. Gecko is the rendering engine used by
 * Mozilla, Mozilla Firefox, Camino and many more.
 * @type {boolean}
 */
goog.userAgent.GECKO = goog.userAgent.BROWSER_KNOWN_ ?
    goog.userAgent.ASSUME_GECKO :
    goog.userAgent.detectedGecko_;


/**
 * Whether the user agent is WebKit. WebKit is the rendering engine that
 * Safari, Android and others use.
 * @type {boolean}
 */
goog.userAgent.WEBKIT = goog.userAgent.BROWSER_KNOWN_ ?
    goog.userAgent.ASSUME_WEBKIT || goog.userAgent.ASSUME_MOBILE_WEBKIT :
    goog.userAgent.detectedWebkit_;


/**
 * Whether the user agent is running on a mobile device.
 * @type {boolean}
 */
goog.userAgent.MOBILE = goog.userAgent.ASSUME_MOBILE_WEBKIT ||
                        goog.userAgent.detectedMobile_;


/**
 * Used while transitioning code to use WEBKIT instead.
 * @type {boolean}
 * @deprecated Use {@link goog.userAgent.product.SAFARI} instead.
 * TODO(nicksantos): Delete this from goog.userAgent.
 */
goog.userAgent.SAFARI = goog.userAgent.WEBKIT;


/**
 * @return {string} the platform (operating system) the user agent is running
 *     on. Default to empty string because navigator.platform may not be defined
 *     (on Rhino, for example).
 * @private
 */
goog.userAgent.determinePlatform_ = function() {
  var navigator = goog.userAgent.getNavigator();
  return navigator && navigator.platform || '';
};


/**
 * The platform (operating system) the user agent is running on. Default to
 * empty string because navigator.platform may not be defined (on Rhino, for
 * example).
 * @type {string}
 */
goog.userAgent.PLATFORM = goog.userAgent.determinePlatform_();


/**
 * @define {boolean} Whether the user agent is running on a Macintosh operating
 *     system.
 */
goog.userAgent.ASSUME_MAC = false;


/**
 * @define {boolean} Whether the user agent is running on a Windows operating
 *     system.
 */
goog.userAgent.ASSUME_WINDOWS = false;


/**
 * @define {boolean} Whether the user agent is running on a Linux operating
 *     system.
 */
goog.userAgent.ASSUME_LINUX = false;


/**
 * @define {boolean} Whether the user agent is running on a X11 windowing
 *     system.
 */
goog.userAgent.ASSUME_X11 = false;


/**
 * @type {boolean}
 * @private
 */
goog.userAgent.PLATFORM_KNOWN_ =
    goog.userAgent.ASSUME_MAC ||
    goog.userAgent.ASSUME_WINDOWS ||
    goog.userAgent.ASSUME_LINUX ||
    goog.userAgent.ASSUME_X11;


/**
 * Initialize the goog.userAgent constants that define which platform the user
 * agent is running on.
 * @private
 */
goog.userAgent.initPlatform_ = function() {
  /**
   * Whether the user agent is running on a Macintosh operating system.
   * @type {boolean}
   * @private
   */
  goog.userAgent.detectedMac_ = goog.string.contains(goog.userAgent.PLATFORM,
      'Mac');

  /**
   * Whether the user agent is running on a Windows operating system.
   * @type {boolean}
   * @private
   */
  goog.userAgent.detectedWindows_ = goog.string.contains(
      goog.userAgent.PLATFORM, 'Win');

  /**
   * Whether the user agent is running on a Linux operating system.
   * @type {boolean}
   * @private
   */
  goog.userAgent.detectedLinux_ = goog.string.contains(goog.userAgent.PLATFORM,
      'Linux');

  /**
   * Whether the user agent is running on a X11 windowing system.
   * @type {boolean}
   * @private
   */
  goog.userAgent.detectedX11_ = !!goog.userAgent.getNavigator() &&
      goog.string.contains(goog.userAgent.getNavigator()['appVersion'] || '',
          'X11');
};


if (!goog.userAgent.PLATFORM_KNOWN_) {
  goog.userAgent.initPlatform_();
}


/**
 * Whether the user agent is running on a Macintosh operating system.
 * @type {boolean}
 */
goog.userAgent.MAC = goog.userAgent.PLATFORM_KNOWN_ ?
    goog.userAgent.ASSUME_MAC : goog.userAgent.detectedMac_;


/**
 * Whether the user agent is running on a Windows operating system.
 * @type {boolean}
 */
goog.userAgent.WINDOWS = goog.userAgent.PLATFORM_KNOWN_ ?
    goog.userAgent.ASSUME_WINDOWS : goog.userAgent.detectedWindows_;


/**
 * Whether the user agent is running on a Linux operating system.
 * @type {boolean}
 */
goog.userAgent.LINUX = goog.userAgent.PLATFORM_KNOWN_ ?
    goog.userAgent.ASSUME_LINUX : goog.userAgent.detectedLinux_;


/**
 * Whether the user agent is running on a X11 windowing system.
 * @type {boolean}
 */
goog.userAgent.X11 = goog.userAgent.PLATFORM_KNOWN_ ?
    goog.userAgent.ASSUME_X11 : goog.userAgent.detectedX11_;


/**
 * @return {string} The string that describes the version number of the user
 *     agent.
 * @private
 */
goog.userAgent.determineVersion_ = function() {
  // All browsers have different ways to detect the version and they all have
  // different naming schemes.

  // version is a string rather than a number because it may contain 'b', 'a',
  // and so on.
  var version = '', re;

  if (goog.userAgent.OPERA && goog.global['opera']) {
    var operaVersion = goog.global['opera'].version;
    version = typeof operaVersion == 'function' ? operaVersion() : operaVersion;
  } else {
    if (goog.userAgent.GECKO) {
      re = /rv\:([^\);]+)(\)|;)/;
    } else if (goog.userAgent.IE) {
      re = /MSIE\s+([^\);]+)(\)|;)/;
    } else if (goog.userAgent.WEBKIT) {
      // WebKit/125.4
      re = /WebKit\/(\S+)/;
    }
    if (re) {
      var arr = re.exec(goog.userAgent.getUserAgentString());
      version = arr ? arr[1] : '';
    }
  }
  if (goog.userAgent.IE) {
    // IE9 can be in document mode 9 but be reporting an inconsistent user agent
    // version.  If it is identifying as a version lower than 9 we take the
    // documentMode as the version instead.  IE8 has similar behavior.
    // It is recommended to set the X-UA-Compatible header to ensure that IE9
    // uses documentMode 9.
    var docMode = goog.userAgent.getDocumentMode_();
    if (docMode > parseFloat(version)) {
      return String(docMode);
    }
  }
  return version;
};


/**
 * @return {number|undefined} Returns the document mode (for testing).
 * @private
 */
goog.userAgent.getDocumentMode_ = function() {
  // NOTE(user): goog.userAgent may be used in context where there is no DOM.
  var doc = goog.global['document'];
  return doc ? doc['documentMode'] : undefined;
};


/**
 * The version of the user agent. This is a string because it might contain
 * 'b' (as in beta) as well as multiple dots.
 * @type {string}
 */
goog.userAgent.VERSION = goog.userAgent.determineVersion_();


/**
 * Compares two version numbers.
 *
 * @param {string} v1 Version of first item.
 * @param {string} v2 Version of second item.
 *
 * @return {number}  1 if first argument is higher
 *                   0 if arguments are equal
 *                  -1 if second argument is higher.
 * @deprecated Use goog.string.compareVersions.
 */
goog.userAgent.compare = function(v1, v2) {
  return goog.string.compareVersions(v1, v2);
};


/**
 * Cache for {@link goog.userAgent.isVersion}. Calls to compareVersions are
 * surprisingly expensive and as a browsers version number is unlikely to change
 * during a session we cache the results.
 * @type {Object}
 * @private
 */
goog.userAgent.isVersionCache_ = {};


/**
 * Whether the user agent version is higher or the same as the given version.
 * NOTE: When checking the version numbers for Firefox and Safari, be sure to
 * use the engine's version, not the browser's version number.  For example,
 * Firefox 3.0 corresponds to Gecko 1.9 and Safari 3.0 to Webkit 522.11.
 * Opera and Internet Explorer versions match the product release number.<br>
 * @see <a href="http://en.wikipedia.org/wiki/Safari_version_history">
 *     Webkit</a>
 * @see <a href="http://en.wikipedia.org/wiki/Gecko_engine">Gecko</a>
 *
 * @param {string|number} version The version to check.
 * @return {boolean} Whether the user agent version is higher or the same as
 *     the given version.
 */
goog.userAgent.isVersion = function(version) {
  return goog.userAgent.ASSUME_ANY_VERSION ||
      goog.userAgent.isVersionCache_[version] ||
      (goog.userAgent.isVersionCache_[version] =
          goog.string.compareVersions(goog.userAgent.VERSION, version) >= 0);
};


/**
 * Cache for {@link goog.userAgent.isDocumentMode}.
 * Browsers document mode version number is unlikely to change during a session
 * we cache the results.
 * @type {Object}
 * @private
 */
goog.userAgent.isDocumentModeCache_ = {};


/**
 * Whether the IE effective document mode is higher or the same as the given
 * document mode version.
 * NOTE: Only for IE, return false for another browser.
 *
 * @param {number} documentMode The document mode version to check.
 * @return {boolean} Whether the IE effective document mode is higher or the
 *     same as the given version.
 */
goog.userAgent.isDocumentMode = function(documentMode) {
  return goog.userAgent.isDocumentModeCache_[documentMode] ||
      (goog.userAgent.isDocumentModeCache_[documentMode] = goog.userAgent.IE &&
      document.documentMode && document.documentMode >= documentMode);
};

//javascript/closure/dom/browserfeature.js
// Copyright 2010 The Closure Library Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @fileoverview Browser capability checks for the dom package.
 *
 */


goog.provide('goog.dom.BrowserFeature');

goog.require('goog.userAgent');


/**
 * Enum of browser capabilities.
 * @enum {boolean}
 */
goog.dom.BrowserFeature = {
  /**
   * Whether attributes 'name' and 'type' can be added to an element after it's
   * created. False in Internet Explorer prior to version 9.
   */
  CAN_ADD_NAME_OR_TYPE_ATTRIBUTES: !goog.userAgent.IE ||
      goog.userAgent.isDocumentMode(9),

  /**
   * Whether we can use element.children to access an element's Element
   * children. Available since Gecko 1.9.1, IE 9. (IE<9 also includes comment
   * nodes in the collection.)
   */
  CAN_USE_CHILDREN_ATTRIBUTE: !goog.userAgent.GECKO && !goog.userAgent.IE ||
      goog.userAgent.IE && goog.userAgent.isDocumentMode(9) ||
      goog.userAgent.GECKO && goog.userAgent.isVersion('1.9.1'),

  /**
   * Opera, Safari 3, and Internet Explorer 9 all support innerText but they
   * include text nodes in script and style tags. Not document-mode-dependent.
   */
  CAN_USE_INNER_TEXT: goog.userAgent.IE && !goog.userAgent.isVersion('9'),

  /**
   * MSIE, Opera, and Safari>=4 support element.parentElement to access an
   * element's parent if it is an Element.
   */
  CAN_USE_PARENT_ELEMENT_PROPERTY: goog.userAgent.IE || goog.userAgent.OPERA ||
      goog.userAgent.WEBKIT,

  /**
   * Whether NoScope elements need a scoped element written before them in
   * innerHTML.
   * MSDN: http://msdn.microsoft.com/en-us/library/ms533897(VS.85).aspx#1
   */
  INNER_HTML_NEEDS_SCOPED_ELEMENT: goog.userAgent.IE
};

//javascript/closure/dom/classes.js
// Copyright 2006 The Closure Library Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @fileoverview Utilities for adding, removing and setting classes.
 *
 */


goog.provide('goog.dom.classes');

goog.require('goog.array');


/**
 * Sets the entire class name of an element.
 * @param {Node} element DOM node to set class of.
 * @param {string} className Class name(s) to apply to element.
 */
goog.dom.classes.set = function(element, className) {
  element.className = className;
};


/**
 * Gets an array of class names on an element
 * @param {Node} element DOM node to get class of.
 * @return {Array} Class names on {@code element}.
 */
goog.dom.classes.get = function(element) {
  var className = element.className;
  // Some types of elements don't have a className in IE (e.g. iframes).
  // Furthermore, in Firefox, className is not a string when the element is
  // an SVG element.
  return className && typeof className.split == 'function' ?
      className.split(/\s+/) : [];
};


/**
 * Adds a class or classes to an element. Does not add multiples of class names.
 * @param {Node} element DOM node to add class to.
 * @param {...string} var_args Class names to add.
 * @return {boolean} Whether class was added (or all classes were added).
 */
goog.dom.classes.add = function(element, var_args) {
  var classes = goog.dom.classes.get(element);
  var args = goog.array.slice(arguments, 1);

  var b = goog.dom.classes.add_(classes, args);
  element.className = classes.join(' ');

  return b;
};


/**
 * Removes a class or classes from an element.
 * @param {Node} element DOM node to remove class from.
 * @param {...string} var_args Class name(s) to remove.
 * @return {boolean} Whether all classes in {@code var_args} were found and
 *     removed.
 */
goog.dom.classes.remove = function(element, var_args) {
  var classes = goog.dom.classes.get(element);
  var args = goog.array.slice(arguments, 1);

  var b = goog.dom.classes.remove_(classes, args);
  element.className = classes.join(' ');

  return b;
};


/**
 * Helper method for {@link goog.dom.classes.add} and
 * {@link goog.dom.classes.addRemove}. Adds one or more classes to the supplied
 * classes array.
 * @param {Array.<string>} classes All class names for the element, will be
 *     updated to have the classes supplied in {@code args} added.
 * @param {Array.<string>} args Class names to add.
 * @return {boolean} Whether all classes in were added.
 * @private
 */
goog.dom.classes.add_ = function(classes, args) {
  var rv = 0;
  for (var i = 0; i < args.length; i++) {
    if (!goog.array.contains(classes, args[i])) {
      classes.push(args[i]);
      rv++;
    }
  }
  return rv == args.length;
};


/**
 * Helper method for {@link goog.dom.classes.remove} and
 * {@link goog.dom.classes.addRemove}. Removes one or more classes from the
 * supplied classes array.
 * @param {Array.<string>} classes All class names for the element, will be
 *     updated to have the classes supplied in {@code args} removed.
 * @param {Array.<string>} args Class names to remove.
 * @return {boolean} Whether all classes in were found and removed.
 * @private
 */
goog.dom.classes.remove_ = function(classes, args) {
  var rv = 0;
  for (var i = 0; i < classes.length; i++) {
    if (goog.array.contains(args, classes[i])) {
      goog.array.splice(classes, i--, 1);
      rv++;
    }
  }
  return rv == args.length;
};


/**
 * Switches a class on an element from one to another without disturbing other
 * classes. If the fromClass isn't removed, the toClass won't be added.
 * @param {Node} element DOM node to swap classes on.
 * @param {string} fromClass Class to remove.
 * @param {string} toClass Class to add.
 * @return {boolean} Whether classes were switched.
 */
goog.dom.classes.swap = function(element, fromClass, toClass) {
  var classes = goog.dom.classes.get(element);

  var removed = false;
  for (var i = 0; i < classes.length; i++) {
    if (classes[i] == fromClass) {
      goog.array.splice(classes, i--, 1);
      removed = true;
    }
  }

  if (removed) {
    classes.push(toClass);
    element.className = classes.join(' ');
  }

  return removed;
};


/**
 * Adds zero or more classes to an element and removes zero or more as a single
 * operation. Unlike calling {@link goog.dom.classes.add} and
 * {@link goog.dom.classes.remove} separately, this is more efficient as it only
 * parses the class property once.
 *
 * If a class is in both the remove and add lists, it will be added. Thus,
 * you can use this instead of {@link goog.dom.classes.swap} when you have
 * more than two class names that you want to swap.
 *
 * @param {Node} element DOM node to swap classes on.
 * @param {string|Array.<string>|null} classesToRemove Class or classes to
 *     remove, if null no classes are removed.
 * @param {string|Array.<string>|null} classesToAdd Class or classes to add, if
 *     null no classes are added.
 */
goog.dom.classes.addRemove = function(element, classesToRemove, classesToAdd) {
  var classes = goog.dom.classes.get(element);
  if (goog.isString(classesToRemove)) {
    goog.array.remove(classes, classesToRemove);
  } else if (goog.isArray(classesToRemove)) {
    goog.dom.classes.remove_(classes, classesToRemove);
  }

  if (goog.isString(classesToAdd) &&
      !goog.array.contains(classes, classesToAdd)) {
    classes.push(classesToAdd);
  } else if (goog.isArray(classesToAdd)) {
    goog.dom.classes.add_(classes, classesToAdd);
  }

  element.className = classes.join(' ');
};


/**
 * Returns true if an element has a class.
 * @param {Node} element DOM node to test.
 * @param {string} className Class name to test for.
 * @return {boolean} Whether element has the class.
 */
goog.dom.classes.has = function(element, className) {
  return goog.array.contains(goog.dom.classes.get(element), className);
};


/**
 * Adds or removes a class depending on the enabled argument.
 * @param {Node} element DOM node to add or remove the class on.
 * @param {string} className Class name to add or remove.
 * @param {boolean} enabled Whether to add or remove the class (true adds,
 *     false removes).
 */
goog.dom.classes.enable = function(element, className, enabled) {
  if (enabled) {
    goog.dom.classes.add(element, className);
  } else {
    goog.dom.classes.remove(element, className);
  }
};


/**
 * Removes a class if an element has it, and adds it the element doesn't have
 * it.  Won't affect other classes on the node.
 * @param {Node} element DOM node to toggle class on.
 * @param {string} className Class to toggle.
 * @return {boolean} True if class was added, false if it was removed
 *     (in other words, whether element has the class after this function has
 *     been called).
 */
goog.dom.classes.toggle = function(element, className) {
  var add = !goog.dom.classes.has(element, className);
  goog.dom.classes.enable(element, className, add);
  return add;
};

//javascript/closure/dom/tagname.js
// Copyright 2007 The Closure Library Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @fileoverview Defines the goog.dom.TagName enum.  This enumerates
 * all html tag names specified by the W3C HTML 4.01 Specification.
 * Reference http://www.w3.org/TR/html401/index/elements.html.
 */
goog.provide('goog.dom.TagName');


/**
 * Enum of all html tag names specified by the W3C HTML 4.01 Specification.
 * Reference http://www.w3.org/TR/html401/index/elements.html
 * @enum {string}
 */
goog.dom.TagName = {
  A: 'A',
  ABBR: 'ABBR',
  ACRONYM: 'ACRONYM',
  ADDRESS: 'ADDRESS',
  APPLET: 'APPLET',
  AREA: 'AREA',
  B: 'B',
  BASE: 'BASE',
  BASEFONT: 'BASEFONT',
  BDO: 'BDO',
  BIG: 'BIG',
  BLOCKQUOTE: 'BLOCKQUOTE',
  BODY: 'BODY',
  BR: 'BR',
  BUTTON: 'BUTTON',
  CANVAS: 'CANVAS',
  CAPTION: 'CAPTION',
  CENTER: 'CENTER',
  CITE: 'CITE',
  CODE: 'CODE',
  COL: 'COL',
  COLGROUP: 'COLGROUP',
  DD: 'DD',
  DEL: 'DEL',
  DFN: 'DFN',
  DIR: 'DIR',
  DIV: 'DIV',
  DL: 'DL',
  DT: 'DT',
  EM: 'EM',
  FIELDSET: 'FIELDSET',
  FONT: 'FONT',
  FORM: 'FORM',
  FRAME: 'FRAME',
  FRAMESET: 'FRAMESET',
  H1: 'H1',
  H2: 'H2',
  H3: 'H3',
  H4: 'H4',
  H5: 'H5',
  H6: 'H6',
  HEAD: 'HEAD',
  HR: 'HR',
  HTML: 'HTML',
  I: 'I',
  IFRAME: 'IFRAME',
  IMG: 'IMG',
  INPUT: 'INPUT',
  INS: 'INS',
  ISINDEX: 'ISINDEX',
  KBD: 'KBD',
  LABEL: 'LABEL',
  LEGEND: 'LEGEND',
  LI: 'LI',
  LINK: 'LINK',
  MAP: 'MAP',
  MENU: 'MENU',
  META: 'META',
  NOFRAMES: 'NOFRAMES',
  NOSCRIPT: 'NOSCRIPT',
  OBJECT: 'OBJECT',
  OL: 'OL',
  OPTGROUP: 'OPTGROUP',
  OPTION: 'OPTION',
  P: 'P',
  PARAM: 'PARAM',
  PRE: 'PRE',
  Q: 'Q',
  S: 'S',
  SAMP: 'SAMP',
  SCRIPT: 'SCRIPT',
  SELECT: 'SELECT',
  SMALL: 'SMALL',
  SPAN: 'SPAN',
  STRIKE: 'STRIKE',
  STRONG: 'STRONG',
  STYLE: 'STYLE',
  SUB: 'SUB',
  SUP: 'SUP',
  TABLE: 'TABLE',
  TBODY: 'TBODY',
  TD: 'TD',
  TEXTAREA: 'TEXTAREA',
  TFOOT: 'TFOOT',
  TH: 'TH',
  THEAD: 'THEAD',
  TITLE: 'TITLE',
  TR: 'TR',
  TT: 'TT',
  U: 'U',
  UL: 'UL',
  VAR: 'VAR'
};

//javascript/closure/dom/dom.js
// Copyright 2006 The Closure Library Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @fileoverview Utilities for manipulating the browser's Document Object Model
 * Inspiration taken *heavily* from mochikit (http://mochikit.com/).
 *
 * You can use {@link goog.dom.DomHelper} to create new dom helpers that refer
 * to a different document object.  This is useful if you are working with
 * frames or multiple windows.
 *
 */


// TODO(arv): Rename/refactor getTextContent and getRawTextContent. The problem
// is that getTextContent should mimic the DOM3 textContent. We should add a
// getInnerText (or getText) which tries to return the visible text, innerText.


goog.provide('goog.dom');
goog.provide('goog.dom.DomHelper');
goog.provide('goog.dom.NodeType');

goog.require('goog.array');
goog.require('goog.dom.BrowserFeature');
goog.require('goog.dom.TagName');
goog.require('goog.dom.classes');
goog.require('goog.math.Coordinate');
goog.require('goog.math.Size');
goog.require('goog.object');
goog.require('goog.string');
goog.require('goog.userAgent');


/**
 * @define {boolean} Whether we know at compile time that the browser is in
 * quirks mode.
 */
goog.dom.ASSUME_QUIRKS_MODE = false;


/**
 * @define {boolean} Whether we know at compile time that the browser is in
 * standards compliance mode.
 */
goog.dom.ASSUME_STANDARDS_MODE = false;


/**
 * Whether we know the compatibility mode at compile time.
 * @type {boolean}
 * @private
 */
goog.dom.COMPAT_MODE_KNOWN_ =
    goog.dom.ASSUME_QUIRKS_MODE || goog.dom.ASSUME_STANDARDS_MODE;


/**
 * Enumeration for DOM node types (for reference)
 * @enum {number}
 */
goog.dom.NodeType = {
  ELEMENT: 1,
  ATTRIBUTE: 2,
  TEXT: 3,
  CDATA_SECTION: 4,
  ENTITY_REFERENCE: 5,
  ENTITY: 6,
  PROCESSING_INSTRUCTION: 7,
  COMMENT: 8,
  DOCUMENT: 9,
  DOCUMENT_TYPE: 10,
  DOCUMENT_FRAGMENT: 11,
  NOTATION: 12
};


/**
 * Gets the DomHelper object for the document where the element resides.
 * @param {Node|Window=} opt_element If present, gets the DomHelper for this
 *     element.
 * @return {!goog.dom.DomHelper} The DomHelper.
 */
goog.dom.getDomHelper = function(opt_element) {
  return opt_element ?
      new goog.dom.DomHelper(goog.dom.getOwnerDocument(opt_element)) :
      (goog.dom.defaultDomHelper_ ||
          (goog.dom.defaultDomHelper_ = new goog.dom.DomHelper()));
};


/**
 * Cached default DOM helper.
 * @type {goog.dom.DomHelper}
 * @private
 */
goog.dom.defaultDomHelper_;


/**
 * Gets the document object being used by the dom library.
 * @return {!Document} Document object.
 */
goog.dom.getDocument = function() {
  return document;
};


/**
 * Alias for getElementById. If a DOM node is passed in then we just return
 * that.
 * @param {string|Element} element Element ID or a DOM node.
 * @return {Element} The element with the given ID, or the node passed in.
 */
goog.dom.getElement = function(element) {
  return goog.isString(element) ?
      document.getElementById(element) : element;
};


/**
 * Alias for getElement.
 * @param {string|Element} element Element ID or a DOM node.
 * @return {Element} The element with the given ID, or the node passed in.
 * @deprecated Use {@link goog.dom.getElement} instead.
 */
goog.dom.$ = goog.dom.getElement;


/**
 * Looks up elements by both tag and class name, using browser native functions
 * ({@code querySelectorAll}, {@code getElementsByTagName} or
 * {@code getElementsByClassName}) where possible. This function
 * is a useful, if limited, way of collecting a list of DOM elements
 * with certain characteristics.  {@code goog.dom.query} offers a
 * more powerful and general solution which allows matching on CSS3
 * selector expressions, but at increased cost in code size. If all you
 * need is particular tags belonging to a single class, this function
 * is fast and sleek.
 *
 * @see {goog.dom.query}
 *
 * @param {?string=} opt_tag Element tag name.
 * @param {?string=} opt_class Optional class name.
 * @param {Document|Element=} opt_el Optional element to look in.
 * @return { {length: number} } Array-like list of elements (only a length
 *     property and numerical indices are guaranteed to exist).
 */
goog.dom.getElementsByTagNameAndClass = function(opt_tag, opt_class, opt_el) {
  return goog.dom.getElementsByTagNameAndClass_(document, opt_tag, opt_class,
                                                opt_el);
};


/**
 * Returns an array of all the elements with the provided className.
 * @see {goog.dom.query}
 * @param {string} className the name of the class to look for.
 * @param {Document|Element=} opt_el Optional element to look in.
 * @return { {length: number} } The items found with the class name provided.
 */
goog.dom.getElementsByClass = function(className, opt_el) {
  var parent = opt_el || document;
  if (goog.dom.canUseQuerySelector_(parent)) {
    return parent.querySelectorAll('.' + className);
  } else if (parent.getElementsByClassName) {
    return parent.getElementsByClassName(className);
  }
  return goog.dom.getElementsByTagNameAndClass_(
      document, '*', className, opt_el);
};


/**
 * Returns the first element with the provided className.
 * @see {goog.dom.query}
 * @param {string} className the name of the class to look for.
 * @param {Element|Document=} opt_el Optional element to look in.
 * @return {Element} The first item with the class name provided.
 */
goog.dom.getElementByClass = function(className, opt_el) {
  var parent = opt_el || document;
  var retVal = null;
  if (goog.dom.canUseQuerySelector_(parent)) {
    retVal = parent.querySelector('.' + className);
  } else {
    retVal = goog.dom.getElementsByClass(className, opt_el)[0];
  }
  return retVal || null;
};


/**
 * Prefer the standardized (http://www.w3.org/TR/selectors-api/), native and
 * fast W3C Selectors API. However, the version of WebKit that shipped with
 * Safari 3.1 and Chrome has a bug where it will not correctly match mixed-
 * case class name selectors in quirks mode.
 * @param {!Element|Document} parent The parent document object.
 * @return {boolean} whether or not we can use parent.querySelector* APIs.
 * @private
 */
goog.dom.canUseQuerySelector_ = function(parent) {
  return parent.querySelectorAll &&
         parent.querySelector &&
         (!goog.userAgent.WEBKIT || goog.dom.isCss1CompatMode_(document) ||
          goog.userAgent.isVersion('528'));
};


/**
 * Helper for {@code getElementsByTagNameAndClass}.
 * @param {!Document} doc The document to get the elements in.
 * @param {?string=} opt_tag Element tag name.
 * @param {?string=} opt_class Optional class name.
 * @param {Document|Element=} opt_el Optional element to look in.
 * @return { {length: number} } Array-like list of elements (only a length
 *     property and numerical indices are guaranteed to exist).
 * @private
 */
goog.dom.getElementsByTagNameAndClass_ = function(doc, opt_tag, opt_class,
                                                  opt_el) {
  var parent = opt_el || doc;
  var tagName = (opt_tag && opt_tag != '*') ? opt_tag.toUpperCase() : '';

  if (goog.dom.canUseQuerySelector_(parent) &&
      (tagName || opt_class)) {
    var query = tagName + (opt_class ? '.' + opt_class : '');
    return parent.querySelectorAll(query);
  }

  // Use the native getElementsByClassName if available, under the assumption
  // that even when the tag name is specified, there will be fewer elements to
  // filter through when going by class than by tag name
  if (opt_class && parent.getElementsByClassName) {
    var els = parent.getElementsByClassName(opt_class);

    if (tagName) {
      var arrayLike = {};
      var len = 0;

      // Filter for specific tags if requested.
      for (var i = 0, el; el = els[i]; i++) {
        if (tagName == el.nodeName) {
          arrayLike[len++] = el;
        }
      }
      arrayLike.length = len;

      return arrayLike;
    } else {
      return els;
    }
  }

  var els = parent.getElementsByTagName(tagName || '*');

  if (opt_class) {
    var arrayLike = {};
    var len = 0;
    for (var i = 0, el; el = els[i]; i++) {
      var className = el.className;
      // Check if className has a split function since SVG className does not.
      if (typeof className.split == 'function' &&
          goog.array.contains(className.split(/\s+/), opt_class)) {
        arrayLike[len++] = el;
      }
    }
    arrayLike.length = len;
    return arrayLike;
  } else {
    return els;
  }
};


/**
 * Alias for {@code getElementsByTagNameAndClass}.
 * @param {?string=} opt_tag Element tag name.
 * @param {?string=} opt_class Optional class name.
 * @param {Element=} opt_el Optional element to look in.
 * @return { {length: number} } Array-like list of elements (only a length
 *     property and numerical indices are guaranteed to exist).
 * @deprecated Use {@link goog.dom.getElementsByTagNameAndClass} instead.
 */
goog.dom.$$ = goog.dom.getElementsByTagNameAndClass;


/**
 * Sets multiple properties on a node.
 * @param {Element} element DOM node to set properties on.
 * @param {Object} properties Hash of property:value pairs.
 */
goog.dom.setProperties = function(element, properties) {
  goog.object.forEach(properties, function(val, key) {
    if (key == 'style') {
      element.style.cssText = val;
    } else if (key == 'class') {
      element.className = val;
    } else if (key == 'for') {
      element.htmlFor = val;
    } else if (key in goog.dom.DIRECT_ATTRIBUTE_MAP_) {
      element.setAttribute(goog.dom.DIRECT_ATTRIBUTE_MAP_[key], val);
    } else if (goog.string.startsWith(key, 'aria-')) {
      element.setAttribute(key, val);
    } else {
      element[key] = val;
    }
  });
};


/**
 * Map of attributes that should be set using
 * element.setAttribute(key, val) instead of element[key] = val.  Used
 * by goog.dom.setProperties.
 *
 * @type {Object}
 * @private
 */
goog.dom.DIRECT_ATTRIBUTE_MAP_ = {
  'cellpadding': 'cellPadding',
  'cellspacing': 'cellSpacing',
  'colspan': 'colSpan',
  'rowspan': 'rowSpan',
  'valign': 'vAlign',
  'height': 'height',
  'width': 'width',
  'usemap': 'useMap',
  'frameborder': 'frameBorder',
  'maxlength': 'maxLength',
  'type': 'type'
};


/**
 * Gets the dimensions of the viewport.
 *
 * Gecko Standards mode:
 * docEl.clientWidth  Width of viewport excluding scrollbar.
 * win.innerWidth     Width of viewport including scrollbar.
 * body.clientWidth   Width of body element.
 *
 * docEl.clientHeight Height of viewport excluding scrollbar.
 * win.innerHeight    Height of viewport including scrollbar.
 * body.clientHeight  Height of document.
 *
 * Gecko Backwards compatible mode:
 * docEl.clientWidth  Width of viewport excluding scrollbar.
 * win.innerWidth     Width of viewport including scrollbar.
 * body.clientWidth   Width of viewport excluding scrollbar.
 *
 * docEl.clientHeight Height of document.
 * win.innerHeight    Height of viewport including scrollbar.
 * body.clientHeight  Height of viewport excluding scrollbar.
 *
 * IE6/7 Standards mode:
 * docEl.clientWidth  Width of viewport excluding scrollbar.
 * win.innerWidth     Undefined.
 * body.clientWidth   Width of body element.
 *
 * docEl.clientHeight Height of viewport excluding scrollbar.
 * win.innerHeight    Undefined.
 * body.clientHeight  Height of document element.
 *
 * IE5 + IE6/7 Backwards compatible mode:
 * docEl.clientWidth  0.
 * win.innerWidth     Undefined.
 * body.clientWidth   Width of viewport excluding scrollbar.
 *
 * docEl.clientHeight 0.
 * win.innerHeight    Undefined.
 * body.clientHeight  Height of viewport excluding scrollbar.
 *
 * Opera 9 Standards and backwards compatible mode:
 * docEl.clientWidth  Width of viewport excluding scrollbar.
 * win.innerWidth     Width of viewport including scrollbar.
 * body.clientWidth   Width of viewport excluding scrollbar.
 *
 * docEl.clientHeight Height of document.
 * win.innerHeight    Height of viewport including scrollbar.
 * body.clientHeight  Height of viewport excluding scrollbar.
 *
 * WebKit:
 * Safari 2
 * docEl.clientHeight Same as scrollHeight.
 * docEl.clientWidth  Same as innerWidth.
 * win.innerWidth     Width of viewport excluding scrollbar.
 * win.innerHeight    Height of the viewport including scrollbar.
 * frame.innerHeight  Height of the viewport exluding scrollbar.
 *
 * Safari 3 (tested in 522)
 *
 * docEl.clientWidth  Width of viewport excluding scrollbar.
 * docEl.clientHeight Height of viewport excluding scrollbar in strict mode.
 * body.clientHeight  Height of viewport excluding scrollbar in quirks mode.
 *
 * @param {Window=} opt_window Optional window element to test.
 * @return {!goog.math.Size} Object with values 'width' and 'height'.
 */
goog.dom.getViewportSize = function(opt_window) {
  // TODO(arv): This should not take an argument
  return goog.dom.getViewportSize_(opt_window || window);
};


/**
 * Helper for {@code getViewportSize}.
 * @param {Window} win The window to get the view port size for.
 * @return {!goog.math.Size} Object with values 'width' and 'height'.
 * @private
 */
goog.dom.getViewportSize_ = function(win) {
  var doc = win.document;

  if (goog.userAgent.WEBKIT && !goog.userAgent.isVersion('500') &&
      !goog.userAgent.MOBILE) {
    // TODO(user): Sometimes we get something that isn't a valid window
    // object. In this case we just revert to the current window. We need to
    // figure out when this happens and find a real fix for it.
    // See the comments on goog.dom.getWindow.
    if (typeof win.innerHeight == 'undefined') {
      win = window;
    }
    var innerHeight = win.innerHeight;
    var scrollHeight = win.document.documentElement.scrollHeight;

    if (win == win.top) {
      if (scrollHeight < innerHeight) {
        innerHeight -= 15; // Scrollbars are 15px wide on Mac
      }
    }
    return new goog.math.Size(win.innerWidth, innerHeight);
  }

  var el = goog.dom.isCss1CompatMode_(doc) ? doc.documentElement : doc.body;

  return new goog.math.Size(el.clientWidth, el.clientHeight);
};


/**
 * Calculates the height of the document.
 *
 * @return {number} The height of the current document.
 */
goog.dom.getDocumentHeight = function() {
  return goog.dom.getDocumentHeight_(window);
};


/**
 * Calculates the height of the document of the given window.
 *
 * Function code copied from the opensocial gadget api:
 *   gadgets.window.adjustHeight(opt_height)
 *
 * @private
 * @param {Window} win The window whose document height to retrieve.
 * @return {number} The height of the document of the given window.
 */
goog.dom.getDocumentHeight_ = function(win) {
  // NOTE(eae): This method will return the window size rather than the document
  // size in webkit quirks mode.
  var doc = win.document;
  var height = 0;

  if (doc) {
    // Calculating inner content height is hard and different between
    // browsers rendering in Strict vs. Quirks mode.  We use a combination of
    // three properties within document.body and document.documentElement:
    // - scrollHeight
    // - offsetHeight
    // - clientHeight
    // These values differ significantly between browsers and rendering modes.
    // But there are patterns.  It just takes a lot of time and persistence
    // to figure out.

    // Get the height of the viewport
    var vh = goog.dom.getViewportSize_(win).height;
    var body = doc.body;
    var docEl = doc.documentElement;
    if (goog.dom.isCss1CompatMode_(doc) && docEl.scrollHeight) {
      // In Strict mode:
      // The inner content height is contained in either:
      //    document.documentElement.scrollHeight
      //    document.documentElement.offsetHeight
      // Based on studying the values output by different browsers,
      // use the value that's NOT equal to the viewport height found above.
      height = docEl.scrollHeight != vh ?
          docEl.scrollHeight : docEl.offsetHeight;
    } else {
      // In Quirks mode:
      // documentElement.clientHeight is equal to documentElement.offsetHeight
      // except in IE.  In most browsers, document.documentElement can be used
      // to calculate the inner content height.
      // However, in other browsers (e.g. IE), document.body must be used
      // instead.  How do we know which one to use?
      // If document.documentElement.clientHeight does NOT equal
      // document.documentElement.offsetHeight, then use document.body.
      var sh = docEl.scrollHeight;
      var oh = docEl.offsetHeight;
      if (docEl.clientHeight != oh) {
        sh = body.scrollHeight;
        oh = body.offsetHeight;
      }

      // Detect whether the inner content height is bigger or smaller
      // than the bounding box (viewport).  If bigger, take the larger
      // value.  If smaller, take the smaller value.
      if (sh > vh) {
        // Content is larger
        height = sh > oh ? sh : oh;
      } else {
        // Content is smaller
        height = sh < oh ? sh : oh;
      }
    }
  }

  return height;
};


/**
 * Gets the page scroll distance as a coordinate object.
 *
 * @param {Window=} opt_window Optional window element to test.
 * @return {!goog.math.Coordinate} Object with values 'x' and 'y'.
 * @deprecated Use {@link goog.dom.getDocumentScroll} instead.
 */
goog.dom.getPageScroll = function(opt_window) {
  var win = opt_window || goog.global || window;
  return goog.dom.getDomHelper(win.document).getDocumentScroll();
};


/**
 * Gets the document scroll distance as a coordinate object.
 *
 * @return {!goog.math.Coordinate} Object with values 'x' and 'y'.
 */
goog.dom.getDocumentScroll = function() {
  return goog.dom.getDocumentScroll_(document);
};


/**
 * Helper for {@code getDocumentScroll}.
 *
 * @param {!Document} doc The document to get the scroll for.
 * @return {!goog.math.Coordinate} Object with values 'x' and 'y'.
 * @private
 */
goog.dom.getDocumentScroll_ = function(doc) {
  var el = goog.dom.getDocumentScrollElement_(doc);
  var win = goog.dom.getWindow_(doc);
  return new goog.math.Coordinate(win.pageXOffset || el.scrollLeft,
      win.pageYOffset || el.scrollTop);
};


/**
 * Gets the document scroll element.
 * @return {Element} Scrolling element.
 */
goog.dom.getDocumentScrollElement = function() {
  return goog.dom.getDocumentScrollElement_(document);
};


/**
 * Helper for {@code getDocumentScrollElement}.
 * @param {!Document} doc The document to get the scroll element for.
 * @return {Element} Scrolling element.
 * @private
 */
goog.dom.getDocumentScrollElement_ = function(doc) {
  // Safari (2 and 3) needs body.scrollLeft in both quirks mode and strict mode.
  return !goog.userAgent.WEBKIT && goog.dom.isCss1CompatMode_(doc) ?
      doc.documentElement : doc.body;
};


/**
 * Gets the window object associated with the given document.
 *
 * @param {Document=} opt_doc  Document object to get window for.
 * @return {!Window} The window associated with the given document.
 */
goog.dom.getWindow = function(opt_doc) {
  // TODO(arv): This should not take an argument.
  return opt_doc ? goog.dom.getWindow_(opt_doc) : window;
};


/**
 * Helper for {@code getWindow}.
 *
 * @param {!Document} doc  Document object to get window for.
 * @return {!Window} The window associated with the given document.
 * @private
 */
goog.dom.getWindow_ = function(doc) {
  return doc.parentWindow || doc.defaultView;
};


/**
 * Returns a dom node with a set of attributes.  This function accepts varargs
 * for subsequent nodes to be added.  Subsequent nodes will be added to the
 * first node as childNodes.
 *
 * So:
 * <code>createDom('div', null, createDom('p'), createDom('p'));</code>
 * would return a div with two child paragraphs
 *
 * @param {string} tagName Tag to create.
 * @param {Object|Array.<string>|string=} opt_attributes If object, then a map
 *     of name-value pairs for attributes. If a string, then this is the
 *     className of the new element. If an array, the elements will be joined
 *     together as the className of the new element.
 * @param {...Object|string|Array|NodeList} var_args Further DOM nodes or
 *     strings for text nodes. If one of the var_args is an array or NodeList,i
 *     its elements will be added as childNodes instead.
 * @return {!Element} Reference to a DOM node.
 */
goog.dom.createDom = function(tagName, opt_attributes, var_args) {
  return goog.dom.createDom_(document, arguments);
};


/**
 * Helper for {@code createDom}.
 * @param {!Document} doc The document to create the DOM in.
 * @param {!Arguments} args Argument object passed from the callers. See
 *     {@code goog.dom.createDom} for details.
 * @return {!Element} Reference to a DOM node.
 * @private
 */
goog.dom.createDom_ = function(doc, args) {
  var tagName = args[0];
  var attributes = args[1];

  // Internet Explorer is dumb: http://msdn.microsoft.com/workshop/author/
  //                            dhtml/reference/properties/name_2.asp
  // Also does not allow setting of 'type' attribute on 'input' or 'button'.
  if (!goog.dom.BrowserFeature.CAN_ADD_NAME_OR_TYPE_ATTRIBUTES && attributes &&
      (attributes.name || attributes.type)) {
    var tagNameArr = ['<', tagName];
    if (attributes.name) {
      tagNameArr.push(' name="', goog.string.htmlEscape(attributes.name),
                      '"');
    }
    if (attributes.type) {
      tagNameArr.push(' type="', goog.string.htmlEscape(attributes.type),
                      '"');

      // Clone attributes map to remove 'type' without mutating the input.
      var clone = {};
      goog.object.extend(clone, attributes);
      attributes = clone;
      delete attributes.type;
    }
    tagNameArr.push('>');
    tagName = tagNameArr.join('');
  }

  var element = doc.createElement(tagName);

  if (attributes) {
    if (goog.isString(attributes)) {
      element.className = attributes;
    } else if (goog.isArray(attributes)) {
      goog.dom.classes.add.apply(null, [element].concat(attributes));
    } else {
      goog.dom.setProperties(element, attributes);
    }
  }

  if (args.length > 2) {
    goog.dom.append_(doc, element, args, 2);
  }

  return element;
};


/**
 * Appends a node with text or other nodes.
 * @param {!Document} doc The document to create new nodes in.
 * @param {!Node} parent The node to append nodes to.
 * @param {!Arguments} args The values to add. See {@code goog.dom.append}.
 * @param {number} startIndex The index of the array to start from.
 * @private
 */
goog.dom.append_ = function(doc, parent, args, startIndex) {
  function childHandler(child) {
    // TODO(user): More coercion, ala MochiKit?
    if (child) {
      parent.appendChild(goog.isString(child) ?
          doc.createTextNode(child) : child);
    }
  }

  for (var i = startIndex; i < args.length; i++) {
    var arg = args[i];
    // TODO(attila): Fix isArrayLike to return false for a text node.
    if (goog.isArrayLike(arg) && !goog.dom.isNodeLike(arg)) {
      // If the argument is a node list, not a real array, use a clone,
      // because forEach can't be used to mutate a NodeList.
      goog.array.forEach(goog.dom.isNodeList(arg) ?
          goog.array.clone(arg) : arg,
          childHandler);
    } else {
      childHandler(arg);
    }
  }
};


/**
 * Alias for {@code createDom}.
 * @param {string} tagName Tag to create.
 * @param {string|Object=} opt_attributes If object, then a map of name-value
 *     pairs for attributes. If a string, then this is the className of the new
 *     element.
 * @param {...Object|string|Array|NodeList} var_args Further DOM nodes or
 *     strings for text nodes. If one of the var_args is an array, its
 *     children will be added as childNodes instead.
 * @return {!Element} Reference to a DOM node.
 * @deprecated Use {@link goog.dom.createDom} instead.
 */
goog.dom.$dom = goog.dom.createDom;


/**
 * Creates a new element.
 * @param {string} name Tag name.
 * @return {!Element} The new element.
 */
goog.dom.createElement = function(name) {
  return document.createElement(name);
};


/**
 * Creates a new text node.
 * @param {string} content Content.
 * @return {!Text} The new text node.
 */
goog.dom.createTextNode = function(content) {
  return document.createTextNode(content);
};


/**
 * Create a table.
 * @param {number} rows The number of rows in the table.  Must be >= 1.
 * @param {number} columns The number of columns in the table.  Must be >= 1.
 * @param {boolean=} opt_fillWithNbsp If true, fills table entries with nsbps.
 * @return {!Element} The created table.
 */
goog.dom.createTable = function(rows, columns, opt_fillWithNbsp) {
  return goog.dom.createTable_(document, rows, columns, !!opt_fillWithNbsp);
};


/**
 * Create a table.
 * @param {!Document} doc Document object to use to create the table.
 * @param {number} rows The number of rows in the table.  Must be >= 1.
 * @param {number} columns The number of columns in the table.  Must be >= 1.
 * @param {boolean} fillWithNbsp If true, fills table entries with nsbps.
 * @return {!Element} The created table.
 * @private
 */
goog.dom.createTable_ = function(doc, rows, columns, fillWithNbsp) {
  var rowHtml = ['<tr>'];
  for (var i = 0; i < columns; i++) {
    rowHtml.push(fillWithNbsp ? '<td>&nbsp;</td>' : '<td></td>');
  }
  rowHtml.push('</tr>');
  rowHtml = rowHtml.join('');
  var totalHtml = ['<table>'];
  for (i = 0; i < rows; i++) {
    totalHtml.push(rowHtml);
  }
  totalHtml.push('</table>');

  var elem = doc.createElement(goog.dom.TagName.DIV);
  elem.innerHTML = totalHtml.join('');
  return /** @type {!Element} */ (elem.removeChild(elem.firstChild));
};


/**
 * Converts an HTML string into a document fragment.
 *
 * @param {string} htmlString The HTML string to convert.
 * @return {!Node} The resulting document fragment.
 */
goog.dom.htmlToDocumentFragment = function(htmlString) {
  return goog.dom.htmlToDocumentFragment_(document, htmlString);
};


/**
 * Helper for {@code htmlToDocumentFragment}.
 *
 * @param {!Document} doc The document.
 * @param {string} htmlString The HTML string to convert.
 * @return {!Node} The resulting document fragment.
 * @private
 */
goog.dom.htmlToDocumentFragment_ = function(doc, htmlString) {
  var tempDiv = doc.createElement('div');
  if (goog.dom.BrowserFeature.INNER_HTML_NEEDS_SCOPED_ELEMENT) {
    tempDiv.innerHTML = '<br>' + htmlString;
    tempDiv.removeChild(tempDiv.firstChild);
  } else {
    tempDiv.innerHTML = htmlString;
  }
  if (tempDiv.childNodes.length == 1) {
    return /** @type {!Node} */ (tempDiv.removeChild(tempDiv.firstChild));
  } else {
    var fragment = doc.createDocumentFragment();
    while (tempDiv.firstChild) {
      fragment.appendChild(tempDiv.firstChild);
    }
    return fragment;
  }
};


/**
 * Returns the compatMode of the document.
 * @return {string} The result is either CSS1Compat or BackCompat.
 * @deprecated use goog.dom.isCss1CompatMode instead.
 */
goog.dom.getCompatMode = function() {
  return goog.dom.isCss1CompatMode() ? 'CSS1Compat' : 'BackCompat';
};


/**
 * Returns true if the browser is in "CSS1-compatible" (standards-compliant)
 * mode, false otherwise.
 * @return {boolean} True if in CSS1-compatible mode.
 */
goog.dom.isCss1CompatMode = function() {
  return goog.dom.isCss1CompatMode_(document);
};


/**
 * Returns true if the browser is in "CSS1-compatible" (standards-compliant)
 * mode, false otherwise.
 * @param {Document} doc The document to check.
 * @return {boolean} True if in CSS1-compatible mode.
 * @private
 */
goog.dom.isCss1CompatMode_ = function(doc) {
  if (goog.dom.COMPAT_MODE_KNOWN_) {
    return goog.dom.ASSUME_STANDARDS_MODE;
  }

  return doc.compatMode == 'CSS1Compat';
};


/**
 * Determines if the given node can contain children, intended to be used for
 * HTML generation.
 *
 * IE natively supports node.canHaveChildren but has inconsistent behavior.
 * Prior to IE8 the base tag allows children and in IE9 all nodes return true
 * for canHaveChildren.
 *
 * In practice all non-IE browsers allow you to add children to any node, but
 * the behavior is inconsistent:
 *
 * <pre>
 *   var a = document.createElement('br');
 *   a.appendChild(document.createTextNode('foo'));
 *   a.appendChild(document.createTextNode('bar'));
 *   console.log(a.childNodes.length);  // 2
 *   console.log(a.innerHTML);  // Chrome: "", IE9: "foobar", FF3.5: "foobar"
 * </pre>
 *
 * TODO(user): Rename shouldAllowChildren() ?
 *
 * @param {Node} node The node to check.
 * @return {boolean} Whether the node can contain children.
 */
goog.dom.canHaveChildren = function(node) {
  if (node.nodeType != goog.dom.NodeType.ELEMENT) {
    return false;
  }
  switch (node.tagName) {
    case goog.dom.TagName.APPLET:
    case goog.dom.TagName.AREA:
    case goog.dom.TagName.BASE:
    case goog.dom.TagName.BR:
    case goog.dom.TagName.COL:
    case goog.dom.TagName.FRAME:
    case goog.dom.TagName.HR:
    case goog.dom.TagName.IMG:
    case goog.dom.TagName.INPUT:
    case goog.dom.TagName.IFRAME:
    case goog.dom.TagName.ISINDEX:
    case goog.dom.TagName.LINK:
    case goog.dom.TagName.NOFRAMES:
    case goog.dom.TagName.NOSCRIPT:
    case goog.dom.TagName.META:
    case goog.dom.TagName.OBJECT:
    case goog.dom.TagName.PARAM:
    case goog.dom.TagName.SCRIPT:
    case goog.dom.TagName.STYLE:
      return false;
  }
  return true;
};


/**
 * Appends a child to a node.
 * @param {Node} parent Parent.
 * @param {Node} child Child.
 */
goog.dom.appendChild = function(parent, child) {
  parent.appendChild(child);
};


/**
 * Appends a node with text or other nodes.
 * @param {!Node} parent The node to append nodes to.
 * @param {...goog.dom.Appendable} var_args The things to append to the node.
 *     If this is a Node it is appended as is.
 *     If this is a string then a text node is appended.
 *     If this is an array like object then fields 0 to length - 1 are appended.
 */
goog.dom.append = function(parent, var_args) {
  goog.dom.append_(goog.dom.getOwnerDocument(parent), parent, arguments, 1);
};


/**
 * Removes all the child nodes on a DOM node.
 * @param {Node} node Node to remove children from.
 */
goog.dom.removeChildren = function(node) {
  // Note: Iterations over live collections can be slow, this is the fastest
  // we could find. The double parenthesis are used to prevent JsCompiler and
  // strict warnings.
  var child;
  while ((child = node.firstChild)) {
    node.removeChild(child);
  }
};


/**
 * Inserts a new node before an existing reference node (i.e. as the previous
 * sibling). If the reference node has no parent, then does nothing.
 * @param {Node} newNode Node to insert.
 * @param {Node} refNode Reference node to insert before.
 */
goog.dom.insertSiblingBefore = function(newNode, refNode) {
  if (refNode.parentNode) {
    refNode.parentNode.insertBefore(newNode, refNode);
  }
};


/**
 * Inserts a new node after an existing reference node (i.e. as the next
 * sibling). If the reference node has no parent, then does nothing.
 * @param {Node} newNode Node to insert.
 * @param {Node} refNode Reference node to insert after.
 */
goog.dom.insertSiblingAfter = function(newNode, refNode) {
  if (refNode.parentNode) {
    refNode.parentNode.insertBefore(newNode, refNode.nextSibling);
  }
};


/**
 * Insert a child at a given index. If index is larger than the number of child
 * nodes that the parent currently has, the node is inserted as the last child
 * node.
 * @param {Element} parent The element into which to insert the child.
 * @param {Node} child The element to insert.
 * @param {number} index The index at which to insert the new child node. Must
 *     not be negative.
 */
goog.dom.insertChildAt = function(parent, child, index) {
  // Note that if the second argument is null, insertBefore
  // will append the child at the end of the list of children.
  parent.insertBefore(child, parent.childNodes[index] || null);
};


/**
 * Removes a node from its parent.
 * @param {Node} node The node to remove.
 * @return {Node} The node removed if removed; else, null.
 */
goog.dom.removeNode = function(node) {
  return node && node.parentNode ? node.parentNode.removeChild(node) : null;
};


/**
 * Replaces a node in the DOM tree. Will do nothing if {@code oldNode} has no
 * parent.
 * @param {Node} newNode Node to insert.
 * @param {Node} oldNode Node to replace.
 */
goog.dom.replaceNode = function(newNode, oldNode) {
  var parent = oldNode.parentNode;
  if (parent) {
    parent.replaceChild(newNode, oldNode);
  }
};


/**
 * Flattens an element. That is, removes it and replace it with its children.
 * Does nothing if the element is not in the document.
 * @param {Element} element The element to flatten.
 * @return {Element|undefined} The original element, detached from the document
 *     tree, sans children; or undefined, if the element was not in the
 *     document to begin with.
 */
goog.dom.flattenElement = function(element) {
  var child, parent = element.parentNode;
  if (parent && parent.nodeType != goog.dom.NodeType.DOCUMENT_FRAGMENT) {
    // Use IE DOM method (supported by Opera too) if available
    if (element.removeNode) {
      return /** @type {Element} */ (element.removeNode(false));
    } else {
      // Move all children of the original node up one level.
      while ((child = element.firstChild)) {
        parent.insertBefore(child, element);
      }

      // Detach the original element.
      return /** @type {Element} */ (goog.dom.removeNode(element));
    }
  }
};


/**
 * Returns an array containing just the element children of the given element.
 * @param {Element} element The element whose element children we want.
 * @return {Array|NodeList} An array or array-like list of just the element
 *     children of the given element.
 */
goog.dom.getChildren = function(element) {
  // We check if the children attribute is supported for child elements
  // since IE8 misuses the attribute by also including comments.
  if (goog.dom.BrowserFeature.CAN_USE_CHILDREN_ATTRIBUTE &&
      element.children != undefined) {
    return element.children;
  }
  // Fall back to manually filtering the element's child nodes.
  return goog.array.filter(element.childNodes, function(node) {
    return node.nodeType == goog.dom.NodeType.ELEMENT;
  });
};


/**
 * Returns the first child node that is an element.
 * @param {Node} node The node to get the first child element of.
 * @return {Element} The first child node of {@code node} that is an element.
 */
goog.dom.getFirstElementChild = function(node) {
  if (node.firstElementChild != undefined) {
    return /** @type {Element} */(node).firstElementChild;
  }
  return goog.dom.getNextElementNode_(node.firstChild, true);
};


/**
 * Returns the last child node that is an element.
 * @param {Node} node The node to get the last child element of.
 * @return {Element} The last child node of {@code node} that is an element.
 */
goog.dom.getLastElementChild = function(node) {
  if (node.lastElementChild != undefined) {
    return /** @type {Element} */(node).lastElementChild;
  }
  return goog.dom.getNextElementNode_(node.lastChild, false);
};


/**
 * Returns the first next sibling that is an element.
 * @param {Node} node The node to get the next sibling element of.
 * @return {Element} The next sibling of {@code node} that is an element.
 */
goog.dom.getNextElementSibling = function(node) {
  if (node.nextElementSibling != undefined) {
    return /** @type {Element} */(node).nextElementSibling;
  }
  return goog.dom.getNextElementNode_(node.nextSibling, true);
};


/**
 * Returns the first previous sibling that is an element.
 * @param {Node} node The node to get the previous sibling element of.
 * @return {Element} The first previous sibling of {@code node} that is
 *     an element.
 */
goog.dom.getPreviousElementSibling = function(node) {
  if (node.previousElementSibling != undefined) {
    return /** @type {Element} */(node).previousElementSibling;
  }
  return goog.dom.getNextElementNode_(node.previousSibling, false);
};


/**
 * Returns the first node that is an element in the specified direction,
 * starting with {@code node}.
 * @param {Node} node The node to get the next element from.
 * @param {boolean} forward Whether to look forwards or backwards.
 * @return {Element} The first element.
 * @private
 */
goog.dom.getNextElementNode_ = function(node, forward) {
  while (node && node.nodeType != goog.dom.NodeType.ELEMENT) {
    node = forward ? node.nextSibling : node.previousSibling;
  }

  return /** @type {Element} */ (node);
};


/**
 * Returns the next node in source order from the given node.
 * @param {Node} node The node.
 * @return {Node} The next node in the DOM tree, or null if this was the last
 *     node.
 */
goog.dom.getNextNode = function(node) {
  if (!node) {
    return null;
  }

  if (node.firstChild) {
    return node.firstChild;
  }

  while (node && !node.nextSibling) {
    node = node.parentNode;
  }

  return node ? node.nextSibling : null;
};


/**
 * Returns the previous node in source order from the given node.
 * @param {Node} node The node.
 * @return {Node} The previous node in the DOM tree, or null if this was the
 *     first node.
 */
goog.dom.getPreviousNode = function(node) {
  if (!node) {
    return null;
  }

  if (!node.previousSibling) {
    return node.parentNode;
  }

  node = node.previousSibling;
  while (node && node.lastChild) {
    node = node.lastChild;
  }

  return node;
};


/**
 * Whether the object looks like a DOM node.
 * @param {*} obj The object being tested for node likeness.
 * @return {boolean} Whether the object looks like a DOM node.
 */
goog.dom.isNodeLike = function(obj) {
  return goog.isObject(obj) && obj.nodeType > 0;
};


/**
 * Whether the object looks like an Element.
 * @param {*} obj The object being tested for Element likeness.
 * @return {boolean} Whether the object looks like an Element.
 */
goog.dom.isElement = function(obj) {
  return goog.isObject(obj) && obj.nodeType == goog.dom.NodeType.ELEMENT;
};


/**
 * Returns true if the specified value is a Window object. This includes the
 * global window for HTML pages, and iframe windows.
 * @param {*} obj Variable to test.
 * @return {boolean} Whether the variable is a window.
 */
goog.dom.isWindow = function(obj) {
  return goog.isObject(obj) && obj['window'] == obj;
};


/**
 * Returns an element's parent, if it's an Element.
 * @param {Element} element The DOM element.
 * @return {Element} The parent, or null if not an Element.
 */
goog.dom.getParentElement = function(element) {
  if (goog.dom.BrowserFeature.CAN_USE_PARENT_ELEMENT_PROPERTY) {
    return element.parentElement;
  }
  var parent = element.parentNode;
  return goog.dom.isElement(parent) ? (/** @type {!Element} */ parent) : null;
};


/**
 * Whether a node contains another node.
 * @param {Node} parent The node that should contain the other node.
 * @param {Node} descendant The node to test presence of.
 * @return {boolean} Whether the parent node contains the descendent node.
 */
goog.dom.contains = function(parent, descendant) {
  // We use browser specific methods for this if available since it is faster
  // that way.

  // IE DOM
  if (parent.contains && descendant.nodeType == goog.dom.NodeType.ELEMENT) {
    return parent == descendant || parent.contains(descendant);
  }

  // W3C DOM Level 3
  if (typeof parent.compareDocumentPosition != 'undefined') {
    return parent == descendant ||
        Boolean(parent.compareDocumentPosition(descendant) & 16);
  }

  // W3C DOM Level 1
  while (descendant && parent != descendant) {
    descendant = descendant.parentNode;
  }
  return descendant == parent;
};


/**
 * Compares the document order of two nodes, returning 0 if they are the same
 * node, a negative number if node1 is before node2, and a positive number if
 * node2 is before node1.  Note that we compare the order the tags appear in the
 * document so in the tree <b><i>text</i></b> the B node is considered to be
 * before the I node.
 *
 * @param {Node} node1 The first node to compare.
 * @param {Node} node2 The second node to compare.
 * @return {number} 0 if the nodes are the same node, a negative number if node1
 *     is before node2, and a positive number if node2 is before node1.
 */
goog.dom.compareNodeOrder = function(node1, node2) {
  // Fall out quickly for equality.
  if (node1 == node2) {
    return 0;
  }

  // Use compareDocumentPosition where available
  if (node1.compareDocumentPosition) {
    // 4 is the bitmask for FOLLOWS.
    return node1.compareDocumentPosition(node2) & 2 ? 1 : -1;
  }

  // Process in IE using sourceIndex - we check to see if the first node has
  // a source index or if its parent has one.
  if ('sourceIndex' in node1 ||
      (node1.parentNode && 'sourceIndex' in node1.parentNode)) {
    var isElement1 = node1.nodeType == goog.dom.NodeType.ELEMENT;
    var isElement2 = node2.nodeType == goog.dom.NodeType.ELEMENT;

    if (isElement1 && isElement2) {
      return node1.sourceIndex - node2.sourceIndex;
    } else {
      var parent1 = node1.parentNode;
      var parent2 = node2.parentNode;

      if (parent1 == parent2) {
        return goog.dom.compareSiblingOrder_(node1, node2);
      }

      if (!isElement1 && goog.dom.contains(parent1, node2)) {
        return -1 * goog.dom.compareParentsDescendantNodeIe_(node1, node2);
      }


      if (!isElement2 && goog.dom.contains(parent2, node1)) {
        return goog.dom.compareParentsDescendantNodeIe_(node2, node1);
      }

      return (isElement1 ? node1.sourceIndex : parent1.sourceIndex) -
             (isElement2 ? node2.sourceIndex : parent2.sourceIndex);
    }
  }

  // For Safari, we compare ranges.
  var doc = goog.dom.getOwnerDocument(node1);

  var range1, range2;
  range1 = doc.createRange();
  range1.selectNode(node1);
  range1.collapse(true);

  range2 = doc.createRange();
  range2.selectNode(node2);
  range2.collapse(true);

  return range1.compareBoundaryPoints(goog.global['Range'].START_TO_END,
      range2);
};


/**
 * Utility function to compare the position of two nodes, when
 * {@code textNode}'s parent is an ancestor of {@code node}.  If this entry
 * condition is not met, this function will attempt to reference a null object.
 * @param {Node} textNode The textNode to compare.
 * @param {Node} node The node to compare.
 * @return {number} -1 if node is before textNode, +1 otherwise.
 * @private
 */
goog.dom.compareParentsDescendantNodeIe_ = function(textNode, node) {
  var parent = textNode.parentNode;
  if (parent == node) {
    // If textNode is a child of node, then node comes first.
    return -1;
  }
  var sibling = node;
  while (sibling.parentNode != parent) {
    sibling = sibling.parentNode;
  }
  return goog.dom.compareSiblingOrder_(sibling, textNode);
};


/**
 * Utility function to compare the position of two nodes known to be non-equal
 * siblings.
 * @param {Node} node1 The first node to compare.
 * @param {Node} node2 The second node to compare.
 * @return {number} -1 if node1 is before node2, +1 otherwise.
 * @private
 */
goog.dom.compareSiblingOrder_ = function(node1, node2) {
  var s = node2;
  while ((s = s.previousSibling)) {
    if (s == node1) {
      // We just found node1 before node2.
      return -1;
    }
  }

  // Since we didn't find it, node1 must be after node2.
  return 1;
};


/**
 * Find the deepest common ancestor of the given nodes.
 * @param {...Node} var_args The nodes to find a common ancestor of.
 * @return {Node} The common ancestor of the nodes, or null if there is none.
 *     null will only be returned if two or more of the nodes are from different
 *     documents.
 */
goog.dom.findCommonAncestor = function(var_args) {
  var i, count = arguments.length;
  if (!count) {
    return null;
  } else if (count == 1) {
    return arguments[0];
  }

  var paths = [];
  var minLength = Infinity;
  for (i = 0; i < count; i++) {
    // Compute the list of ancestors.
    var ancestors = [];
    var node = arguments[i];
    while (node) {
      ancestors.unshift(node);
      node = node.parentNode;
    }

    // Save the list for comparison.
    paths.push(ancestors);
    minLength = Math.min(minLength, ancestors.length);
  }
  var output = null;
  for (i = 0; i < minLength; i++) {
    var first = paths[0][i];
    for (var j = 1; j < count; j++) {
      if (first != paths[j][i]) {
        return output;
      }
    }
    output = first;
  }
  return output;
};


/**
 * Returns the owner document for a node.
 * @param {Node|Window} node The node to get the document for.
 * @return {!Document} The document owning the node.
 */
goog.dom.getOwnerDocument = function(node) {
  // TODO(arv): Remove IE5 code.
  // IE5 uses document instead of ownerDocument
  return /** @type {!Document} */ (
      node.nodeType == goog.dom.NodeType.DOCUMENT ? node :
      node.ownerDocument || node.document);
};


/**
 * Cross-browser function for getting the document element of a frame or iframe.
 * @param {Element} frame Frame element.
 * @return {!Document} The frame content document.
 */
goog.dom.getFrameContentDocument = function(frame) {
  var doc = frame.contentDocument || frame.contentWindow.document;
  return doc;
};


/**
 * Cross-browser function for getting the window of a frame or iframe.
 * @param {HTMLIFrameElement|HTMLFrameElement} frame Frame element.
 * @return {Window} The window associated with the given frame.
 */
goog.dom.getFrameContentWindow = function(frame) {
  return frame.contentWindow ||
      goog.dom.getWindow_(goog.dom.getFrameContentDocument(frame));
};


/**
 * Cross-browser function for setting the text content of an element.
 * @param {Element} element The element to change the text content of.
 * @param {string} text The string that should replace the current element
 *     content.
 */
goog.dom.setTextContent = function(element, text) {
  if ('textContent' in element) {
    element.textContent = text;
  } else if (element.firstChild &&
             element.firstChild.nodeType == goog.dom.NodeType.TEXT) {
    // If the first child is a text node we just change its data and remove the
    // rest of the children.
    while (element.lastChild != element.firstChild) {
      element.removeChild(element.lastChild);
    }
    element.firstChild.data = text;
  } else {
    goog.dom.removeChildren(element);
    var doc = goog.dom.getOwnerDocument(element);
    element.appendChild(doc.createTextNode(text));
  }
};


/**
 * Gets the outerHTML of a node, which islike innerHTML, except that it
 * actually contains the HTML of the node itself.
 * @param {Element} element The element to get the HTML of.
 * @return {string} The outerHTML of the given element.
 */
goog.dom.getOuterHtml = function(element) {
  // IE, Opera and WebKit all have outerHTML.
  if ('outerHTML' in element) {
    return element.outerHTML;
  } else {
    var doc = goog.dom.getOwnerDocument(element);
    var div = doc.createElement('div');
    div.appendChild(element.cloneNode(true));
    return div.innerHTML;
  }
};


/**
 * Finds the first descendant node that matches the filter function, using
 * a depth first search. This function offers the most general purpose way
 * of finding a matching element. You may also wish to consider
 * {@code goog.dom.query} which can express many matching criteria using
 * CSS selector expressions. These expressions often result in a more
 * compact representation of the desired result.
 * @see goog.dom.query
 *
 * @param {Node} root The root of the tree to search.
 * @param {function(Node) : boolean} p The filter function.
 * @return {Node|undefined} The found node or undefined if none is found.
 */
goog.dom.findNode = function(root, p) {
  var rv = [];
  var found = goog.dom.findNodes_(root, p, rv, true);
  return found ? rv[0] : undefined;
};


/**
 * Finds all the descendant nodes that match the filter function, using a
 * a depth first search. This function offers the most general-purpose way
 * of finding a set of matching elements. You may also wish to consider
 * {@code goog.dom.query} which can express many matching criteria using
 * CSS selector expressions. These expressions often result in a more
 * compact representation of the desired result.

 * @param {Node} root The root of the tree to search.
 * @param {function(Node) : boolean} p The filter function.
 * @return {Array.<Node>} The found nodes or an empty array if none are found.
 */
goog.dom.findNodes = function(root, p) {
  var rv = [];
  goog.dom.findNodes_(root, p, rv, false);
  return rv;
};


/**
 * Finds the first or all the descendant nodes that match the filter function,
 * using a depth first search.
 * @param {Node} root The root of the tree to search.
 * @param {function(Node) : boolean} p The filter function.
 * @param {Array.<Node>} rv The found nodes are added to this array.
 * @param {boolean} findOne If true we exit after the first found node.
 * @return {boolean} Whether the search is complete or not. True in case findOne
 *     is true and the node is found. False otherwise.
 * @private
 */
goog.dom.findNodes_ = function(root, p, rv, findOne) {
  if (root != null) {
    var child = root.firstChild;
    while (child) {
      if (p(child)) {
        rv.push(child);
        if (findOne) {
          return true;
        }
      }
      if (goog.dom.findNodes_(child, p, rv, findOne)) {
        return true;
      }
      child = child.nextSibling;
    }
  }
  return false;
};


/**
 * Map of tags whose content to ignore when calculating text length.
 * @type {Object}
 * @private
 */
goog.dom.TAGS_TO_IGNORE_ = {
  'SCRIPT': 1,
  'STYLE': 1,
  'HEAD': 1,
  'IFRAME': 1,
  'OBJECT': 1
};


/**
 * Map of tags which have predefined values with regard to whitespace.
 * @type {Object}
 * @private
 */
goog.dom.PREDEFINED_TAG_VALUES_ = {'IMG': ' ', 'BR': '\n'};


/**
 * Returns true if the element has a tab index that allows it to receive
 * keyboard focus (tabIndex >= 0), false otherwise.  Note that form elements
 * natively support keyboard focus, even if they have no tab index.
 * @param {Element} element Element to check.
 * @return {boolean} Whether the element has a tab index that allows keyboard
 *     focus.
 * @see http://fluidproject.org/blog/2008/01/09/getting-setting-and-removing-tabindex-values-with-javascript/
 */
goog.dom.isFocusableTabIndex = function(element) {
  // IE returns 0 for an unset tabIndex, so we must use getAttributeNode(),
  // which returns an object with a 'specified' property if tabIndex is
  // specified.  This works on other browsers, too.
  var attrNode = element.getAttributeNode('tabindex'); // Must be lowercase!
  if (attrNode && attrNode.specified) {
    var index = element.tabIndex;
    // NOTE: IE9 puts tabIndex in 16-bit int, e.g. -2 is 65534.
    return goog.isNumber(index) && index >= 0 && index < 32768;
  }
  return false;
};


/**
 * Enables or disables keyboard focus support on the element via its tab index.
 * Only elements for which {@link goog.dom.isFocusableTabIndex} returns true
 * (or elements that natively support keyboard focus, like form elements) can
 * receive keyboard focus.  See http://go/tabindex for more info.
 * @param {Element} element Element whose tab index is to be changed.
 * @param {boolean} enable Whether to set or remove a tab index on the element
 *     that supports keyboard focus.
 */
goog.dom.setFocusableTabIndex = function(element, enable) {
  if (enable) {
    element.tabIndex = 0;
  } else {
    // Set tabIndex to -1 first, then remove it. This is a workaround for
    // Safari (confirmed in version 4 on Windows). When removing the attribute
    // without setting it to -1 first, the element remains keyboard focusable
    // despite not having a tabIndex attribute anymore.
    element.tabIndex = -1;
    element.removeAttribute('tabIndex'); // Must be camelCase!
  }
};


/**
 * Returns the text content of the current node, without markup and invisible
 * symbols. New lines are stripped and whitespace is collapsed,
 * such that each character would be visible.
 *
 * In browsers that support it, innerText is used.  Other browsers attempt to
 * simulate it via node traversal.  Line breaks are canonicalized in IE.
 *
 * @param {Node} node The node from which we are getting content.
 * @return {string} The text content.
 */
goog.dom.getTextContent = function(node) {
  var textContent;
  // Note(arv): IE9, Opera, and Safari 3 support innerText but they include
  // text nodes in script tags. So we revert to use a user agent test here.
  if (goog.dom.BrowserFeature.CAN_USE_INNER_TEXT && ('innerText' in node)) {
    textContent = goog.string.canonicalizeNewlines(node.innerText);
    // Unfortunately .innerText() returns text with &shy; symbols
    // We need to filter it out and then remove duplicate whitespaces
  } else {
    var buf = [];
    goog.dom.getTextContent_(node, buf, true);
    textContent = buf.join('');
  }

  // Strip &shy; entities. goog.format.insertWordBreaks inserts them in Opera.
  textContent = textContent.replace(/ \xAD /g, ' ').replace(/\xAD/g, '');
  // Strip &#8203; entities. goog.format.insertWordBreaks inserts them in IE8.
  textContent = textContent.replace(/\u200B/g, '');

  // Skip this replacement on old browsers with working innerText, which
  // automatically turns &nbsp; into ' ' and / +/ into ' ' when reading
  // innerText.
  if (!goog.dom.BrowserFeature.CAN_USE_INNER_TEXT) {
    textContent = textContent.replace(/ +/g, ' ');
  }
  if (textContent != ' ') {
    textContent = textContent.replace(/^\s*/, '');
  }

  return textContent;
};


/**
 * Returns the text content of the current node, without markup.
 *
 * Unlike {@code getTextContent} this method does not collapse whitespaces
 * or normalize lines breaks.
 *
 * @param {Node} node The node from which we are getting content.
 * @return {string} The raw text content.
 */
goog.dom.getRawTextContent = function(node) {
  var buf = [];
  goog.dom.getTextContent_(node, buf, false);

  return buf.join('');
};


/**
 * Recursive support function for text content retrieval.
 *
 * @param {Node} node The node from which we are getting content.
 * @param {Array} buf string buffer.
 * @param {boolean} normalizeWhitespace Whether to normalize whitespace.
 * @private
 */
goog.dom.getTextContent_ = function(node, buf, normalizeWhitespace) {
  if (node.nodeName in goog.dom.TAGS_TO_IGNORE_) {
    // ignore certain tags
  } else if (node.nodeType == goog.dom.NodeType.TEXT) {
    if (normalizeWhitespace) {
      buf.push(String(node.nodeValue).replace(/(\r\n|\r|\n)/g, ''));
    } else {
      buf.push(node.nodeValue);
    }
  } else if (node.nodeName in goog.dom.PREDEFINED_TAG_VALUES_) {
    buf.push(goog.dom.PREDEFINED_TAG_VALUES_[node.nodeName]);
  } else {
    var child = node.firstChild;
    while (child) {
      goog.dom.getTextContent_(child, buf, normalizeWhitespace);
      child = child.nextSibling;
    }
  }
};


/**
 * Returns the text length of the text contained in a node, without markup. This
 * is equivalent to the selection length if the node was selected, or the number
 * of cursor movements to traverse the node. Images & BRs take one space.  New
 * lines are ignored.
 *
 * @param {Node} node The node whose text content length is being calculated.
 * @return {number} The length of {@code node}'s text content.
 */
goog.dom.getNodeTextLength = function(node) {
  return goog.dom.getTextContent(node).length;
};


/**
 * Returns the text offset of a node relative to one of its ancestors. The text
 * length is the same as the length calculated by goog.dom.getNodeTextLength.
 *
 * @param {Node} node The node whose offset is being calculated.
 * @param {Node=} opt_offsetParent The node relative to which the offset will
 *     be calculated. Defaults to the node's owner document's body.
 * @return {number} The text offset.
 */
goog.dom.getNodeTextOffset = function(node, opt_offsetParent) {
  var root = opt_offsetParent || goog.dom.getOwnerDocument(node).body;
  var buf = [];
  while (node && node != root) {
    var cur = node;
    while ((cur = cur.previousSibling)) {
      buf.unshift(goog.dom.getTextContent(cur));
    }
    node = node.parentNode;
  }
  // Trim left to deal with FF cases when there might be line breaks and empty
  // nodes at the front of the text
  return goog.string.trimLeft(buf.join('')).replace(/ +/g, ' ').length;
};


/**
 * Returns the node at a given offset in a parent node.  If an object is
 * provided for the optional third parameter, the node and the remainder of the
 * offset will stored as properties of this object.
 * @param {Node} parent The parent node.
 * @param {number} offset The offset into the parent node.
 * @param {Object=} opt_result Object to be used to store the return value. The
 *     return value will be stored in the form {node: Node, remainder: number}
 *     if this object is provided.
 * @return {Node} The node at the given offset.
 */
goog.dom.getNodeAtOffset = function(parent, offset, opt_result) {
  var stack = [parent], pos = 0, cur;
  while (stack.length > 0 && pos < offset) {
    cur = stack.pop();
    if (cur.nodeName in goog.dom.TAGS_TO_IGNORE_) {
      // ignore certain tags
    } else if (cur.nodeType == goog.dom.NodeType.TEXT) {
      var text = cur.nodeValue.replace(/(\r\n|\r|\n)/g, '').replace(/ +/g, ' ');
      pos += text.length;
    } else if (cur.nodeName in goog.dom.PREDEFINED_TAG_VALUES_) {
      pos += goog.dom.PREDEFINED_TAG_VALUES_[cur.nodeName].length;
    } else {
      for (var i = cur.childNodes.length - 1; i >= 0; i--) {
        stack.push(cur.childNodes[i]);
      }
    }
  }
  if (goog.isObject(opt_result)) {
    opt_result.remainder = cur ? cur.nodeValue.length + offset - pos - 1 : 0;
    opt_result.node = cur;
  }

  return cur;
};


/**
 * Returns true if the object is a {@code NodeList}.  To qualify as a NodeList,
 * the object must have a numeric length property and an item function (which
 * has type 'string' on IE for some reason).
 * @param {Object} val Object to test.
 * @return {boolean} Whether the object is a NodeList.
 */
goog.dom.isNodeList = function(val) {
  // TODO(attila): Now the isNodeList is part of goog.dom we can use
  // goog.userAgent to make this simpler.
  // A NodeList must have a length property of type 'number' on all platforms.
  if (val && typeof val.length == 'number') {
    // A NodeList is an object everywhere except Safari, where it's a function.
    if (goog.isObject(val)) {
      // A NodeList must have an item function (on non-IE platforms) or an item
      // property of type 'string' (on IE).
      return typeof val.item == 'function' || typeof val.item == 'string';
    } else if (goog.isFunction(val)) {
      // On Safari, a NodeList is a function with an item property that is also
      // a function.
      return typeof val.item == 'function';
    }
  }

  // Not a NodeList.
  return false;
};


/**
 * Walks up the DOM hierarchy returning the first ancestor that has the passed
 * tag name and/or class name. If the passed element matches the specified
 * criteria, the element itself is returned.
 * @param {Node} element The DOM node to start with.
 * @param {?string=} opt_tag The tag name to match (or null/undefined to match
 *     any node regardless of tag name). Must be uppercase (goog.dom.TagName).
 * @param {?string=} opt_class The class name to match (or null/undefined to
 *     match any node regardless of class name).
 * @return {Node} The first ancestor that matches the passed criteria, or
 *     null if none match.
 */
goog.dom.getAncestorByTagNameAndClass = function(element, opt_tag, opt_class) {
  var tagName = opt_tag ? opt_tag.toUpperCase() : null;
  return goog.dom.getAncestor(element,
      function(node) {
        return (!tagName || node.nodeName == tagName) &&
               (!opt_class || goog.dom.classes.has(node, opt_class));
      }, true);
};


/**
 * Walks up the DOM hierarchy returning the first ancestor that has the passed
 * class name. If the passed element matches the specified criteria, the
 * element itself is returned.
 * @param {Node} element The DOM node to start with.
 * @param {?string=} opt_class The class name to match (or null/undefined to
 *     match any node regardless of class name).
 * @return {Node} The first ancestor that matches the passed criteria, or
 *     null if none match.
 */
goog.dom.getAncestorByClass = function(element, opt_class) {
  return goog.dom.getAncestorByTagNameAndClass(element, null, opt_class);
};


/**
 * Walks up the DOM hierarchy returning the first ancestor that passes the
 * matcher function.
 * @param {Node} element The DOM node to start with.
 * @param {function(Node) : boolean} matcher A function that returns true if the
 *     passed node matches the desired criteria.
 * @param {boolean=} opt_includeNode If true, the node itself is included in
 *     the search (the first call to the matcher will pass startElement as
 *     the node to test).
 * @param {number=} opt_maxSearchSteps Maximum number of levels to search up the
 *     dom.
 * @return {Node} DOM node that matched the matcher, or null if there was
 *     no match.
 */
goog.dom.getAncestor = function(
    element, matcher, opt_includeNode, opt_maxSearchSteps) {
  if (!opt_includeNode) {
    element = element.parentNode;
  }
  var ignoreSearchSteps = opt_maxSearchSteps == null;
  var steps = 0;
  while (element && (ignoreSearchSteps || steps <= opt_maxSearchSteps)) {
    if (matcher(element)) {
      return element;
    }
    element = element.parentNode;
    steps++;
  }
  // Reached the root of the DOM without a match
  return null;
};


/**
 * Determines the active element in the given document.
 * @param {Document} doc The document to look in.
 * @return {Element} The active element.
 */
goog.dom.getActiveElement = function(doc) {
  try {
    return doc && doc.activeElement;
  } catch (e) {
    // NOTE(nicksantos): Sometimes, evaluating document.activeElement in IE
    // throws an exception. I'm not 100% sure why, but I suspect it chokes
    // on document.activeElement if the activeElement has been recently
    // removed from the DOM by a JS operation.
    //
    // We assume that an exception here simply means
    // "there is no active element."
  }

  return null;
};



/**
 * Create an instance of a DOM helper with a new document object.
 * @param {Document=} opt_document Document object to associate with this
 *     DOM helper.
 * @constructor
 */
goog.dom.DomHelper = function(opt_document) {
  /**
   * Reference to the document object to use
   * @type {!Document}
   * @private
   */
  this.document_ = opt_document || goog.global.document || document;
};


/**
 * Gets the dom helper object for the document where the element resides.
 * @param {Node=} opt_node If present, gets the DomHelper for this node.
 * @return {!goog.dom.DomHelper} The DomHelper.
 */
goog.dom.DomHelper.prototype.getDomHelper = goog.dom.getDomHelper;


/**
 * Sets the document object.
 * @param {!Document} document Document object.
 */
goog.dom.DomHelper.prototype.setDocument = function(document) {
  this.document_ = document;
};


/**
 * Gets the document object being used by the dom library.
 * @return {!Document} Document object.
 */
goog.dom.DomHelper.prototype.getDocument = function() {
  return this.document_;
};


/**
 * Alias for {@code getElementById}. If a DOM node is passed in then we just
 * return that.
 * @param {string|Element} element Element ID or a DOM node.
 * @return {Element} The element with the given ID, or the node passed in.
 */
goog.dom.DomHelper.prototype.getElement = function(element) {
  if (goog.isString(element)) {
    return this.document_.getElementById(element);
  } else {
    return element;
  }
};


/**
 * Alias for {@code getElement}.
 * @param {string|Element} element Element ID or a DOM node.
 * @return {Element} The element with the given ID, or the node passed in.
 * @deprecated Use {@link goog.dom.DomHelper.prototype.getElement} instead.
 */
goog.dom.DomHelper.prototype.$ = goog.dom.DomHelper.prototype.getElement;


/**
 * Looks up elements by both tag and class name, using browser native functions
 * ({@code querySelectorAll}, {@code getElementsByTagName} or
 * {@code getElementsByClassName}) where possible. The returned array is a live
 * NodeList or a static list depending on the code path taken.
 *
 * @see goog.dom.query
 *
 * @param {?string=} opt_tag Element tag name or * for all tags.
 * @param {?string=} opt_class Optional class name.
 * @param {Document|Element=} opt_el Optional element to look in.
 * @return { {length: number} } Array-like list of elements (only a length
 *     property and numerical indices are guaranteed to exist).
 */
goog.dom.DomHelper.prototype.getElementsByTagNameAndClass = function(opt_tag,
                                                                     opt_class,
                                                                     opt_el) {
  return goog.dom.getElementsByTagNameAndClass_(this.document_, opt_tag,
                                                opt_class, opt_el);
};


/**
 * Returns an array of all the elements with the provided className.
 * @see {goog.dom.query}
 * @param {string} className the name of the class to look for.
 * @param {Element|Document=} opt_el Optional element to look in.
 * @return { {length: number} } The items found with the class name provided.
 */
goog.dom.DomHelper.prototype.getElementsByClass = function(className, opt_el) {
  var doc = opt_el || this.document_;
  return goog.dom.getElementsByClass(className, doc);
};


/**
 * Returns the first element we find matching the provided class name.
 * @see {goog.dom.query}
 * @param {string} className the name of the class to look for.
 * @param {Element|Document=} opt_el Optional element to look in.
 * @return {Element} The first item found with the class name provided.
 */
goog.dom.DomHelper.prototype.getElementByClass = function(className, opt_el) {
  var doc = opt_el || this.document_;
  return goog.dom.getElementByClass(className, doc);
};


/**
 * Alias for {@code getElementsByTagNameAndClass}.
 * @deprecated Use DomHelper getElementsByTagNameAndClass.
 * @see goog.dom.query
 *
 * @param {?string=} opt_tag Element tag name.
 * @param {?string=} opt_class Optional class name.
 * @param {Element=} opt_el Optional element to look in.
 * @return { {length: number} } Array-like list of elements (only a length
 *     property and numerical indices are guaranteed to exist).
 */
goog.dom.DomHelper.prototype.$$ =
    goog.dom.DomHelper.prototype.getElementsByTagNameAndClass;


/**
 * Sets a number of properties on a node.
 * @param {Element} element DOM node to set properties on.
 * @param {Object} properties Hash of property:value pairs.
 */
goog.dom.DomHelper.prototype.setProperties = goog.dom.setProperties;


/**
 * Gets the dimensions of the viewport.
 * @param {Window=} opt_window Optional window element to test. Defaults to
 *     the window of the Dom Helper.
 * @return {!goog.math.Size} Object with values 'width' and 'height'.
 */
goog.dom.DomHelper.prototype.getViewportSize = function(opt_window) {
  // TODO(arv): This should not take an argument. That breaks the rule of a
  // a DomHelper representing a single frame/window/document.
  return goog.dom.getViewportSize(opt_window || this.getWindow());
};


/**
 * Calculates the height of the document.
 *
 * @return {number} The height of the document.
 */
goog.dom.DomHelper.prototype.getDocumentHeight = function() {
  return goog.dom.getDocumentHeight_(this.getWindow());
};


/**
 * Typedef for use with goog.dom.createDom and goog.dom.append.
 * @typedef {Object|string|Array|NodeList}
 */
goog.dom.Appendable;


/**
 * Returns a dom node with a set of attributes.  This function accepts varargs
 * for subsequent nodes to be added.  Subsequent nodes will be added to the
 * first node as childNodes.
 *
 * So:
 * <code>createDom('div', null, createDom('p'), createDom('p'));</code>
 * would return a div with two child paragraphs
 *
 * An easy way to move all child nodes of an existing element to a new parent
 * element is:
 * <code>createDom('div', null, oldElement.childNodes);</code>
 * which will remove all child nodes from the old element and add them as
 * child nodes of the new DIV.
 *
 * @param {string} tagName Tag to create.
 * @param {Object|string=} opt_attributes If object, then a map of name-value
 *     pairs for attributes. If a string, then this is the className of the new
 *     element.
 * @param {...goog.dom.Appendable} var_args Further DOM nodes or
 *     strings for text nodes. If one of the var_args is an array or
 *     NodeList, its elements will be added as childNodes instead.
 * @return {!Element} Reference to a DOM node.
 */
goog.dom.DomHelper.prototype.createDom = function(tagName,
                                                  opt_attributes,
                                                  var_args) {
  return goog.dom.createDom_(this.document_, arguments);
};


/**
 * Alias for {@code createDom}.
 * @param {string} tagName Tag to create.
 * @param {Object|string=} opt_attributes If object, then a map of name-value
 *     pairs for attributes. If a string, then this is the className of the new
 *     element.
 * @param {...goog.dom.Appendable} var_args Further DOM nodes or strings for
 *     text nodes.  If one of the var_args is an array, its children will be
 *     added as childNodes instead.
 * @return {!Element} Reference to a DOM node.
 * @deprecated Use {@link goog.dom.DomHelper.prototype.createDom} instead.
 */
goog.dom.DomHelper.prototype.$dom = goog.dom.DomHelper.prototype.createDom;


/**
 * Creates a new element.
 * @param {string} name Tag name.
 * @return {!Element} The new element.
 */
goog.dom.DomHelper.prototype.createElement = function(name) {
  return this.document_.createElement(name);
};


/**
 * Creates a new text node.
 * @param {string} content Content.
 * @return {!Text} The new text node.
 */
goog.dom.DomHelper.prototype.createTextNode = function(content) {
  return this.document_.createTextNode(content);
};


/**
 * Create a table.
 * @param {number} rows The number of rows in the table.  Must be >= 1.
 * @param {number} columns The number of columns in the table.  Must be >= 1.
 * @param {boolean=} opt_fillWithNbsp If true, fills table entries with nsbps.
 * @return {!Element} The created table.
 */
goog.dom.DomHelper.prototype.createTable = function(rows, columns,
    opt_fillWithNbsp) {
  return goog.dom.createTable_(this.document_, rows, columns,
      !!opt_fillWithNbsp);
};


/**
 * Converts an HTML string into a node or a document fragment.  A single Node
 * is used if the {@code htmlString} only generates a single node.  If the
 * {@code htmlString} generates multiple nodes then these are put inside a
 * {@code DocumentFragment}.
 *
 * @param {string} htmlString The HTML string to convert.
 * @return {!Node} The resulting node.
 */
goog.dom.DomHelper.prototype.htmlToDocumentFragment = function(htmlString) {
  return goog.dom.htmlToDocumentFragment_(this.document_, htmlString);
};


/**
 * Returns the compatMode of the document.
 * @return {string} The result is either CSS1Compat or BackCompat.
 * @deprecated use goog.dom.DomHelper.prototype.isCss1CompatMode instead.
 */
goog.dom.DomHelper.prototype.getCompatMode = function() {
  return this.isCss1CompatMode() ? 'CSS1Compat' : 'BackCompat';
};


/**
 * Returns true if the browser is in "CSS1-compatible" (standards-compliant)
 * mode, false otherwise.
 * @return {boolean} True if in CSS1-compatible mode.
 */
goog.dom.DomHelper.prototype.isCss1CompatMode = function() {
  return goog.dom.isCss1CompatMode_(this.document_);
};


/**
 * Gets the window object associated with the document.
 * @return {!Window} The window associated with the given document.
 */
goog.dom.DomHelper.prototype.getWindow = function() {
  return goog.dom.getWindow_(this.document_);
};


/**
 * Gets the document scroll element.
 * @return {Element} Scrolling element.
 */
goog.dom.DomHelper.prototype.getDocumentScrollElement = function() {
  return goog.dom.getDocumentScrollElement_(this.document_);
};


/**
 * Gets the document scroll distance as a coordinate object.
 * @return {!goog.math.Coordinate} Object with properties 'x' and 'y'.
 */
goog.dom.DomHelper.prototype.getDocumentScroll = function() {
  return goog.dom.getDocumentScroll_(this.document_);
};


/**
 * Appends a child to a node.
 * @param {Node} parent Parent.
 * @param {Node} child Child.
 */
goog.dom.DomHelper.prototype.appendChild = goog.dom.appendChild;


/**
 * Appends a node with text or other nodes.
 * @param {!Node} parent The node to append nodes to.
 * @param {...goog.dom.Appendable} var_args The things to append to the node.
 *     If this is a Node it is appended as is.
 *     If this is a string then a text node is appended.
 *     If this is an array like object then fields 0 to length - 1 are appended.
 */
goog.dom.DomHelper.prototype.append = goog.dom.append;


/**
 * Removes all the child nodes on a DOM node.
 * @param {Node} node Node to remove children from.
 */
goog.dom.DomHelper.prototype.removeChildren = goog.dom.removeChildren;


/**
 * Inserts a new node before an existing reference node (i.e., as the previous
 * sibling). If the reference node has no parent, then does nothing.
 * @param {Node} newNode Node to insert.
 * @param {Node} refNode Reference node to insert before.
 */
goog.dom.DomHelper.prototype.insertSiblingBefore = goog.dom.insertSiblingBefore;


/**
 * Inserts a new node after an existing reference node (i.e., as the next
 * sibling). If the reference node has no parent, then does nothing.
 * @param {Node} newNode Node to insert.
 * @param {Node} refNode Reference node to insert after.
 */
goog.dom.DomHelper.prototype.insertSiblingAfter = goog.dom.insertSiblingAfter;


/**
 * Removes a node from its parent.
 * @param {Node} node The node to remove.
 * @return {Node} The node removed if removed; else, null.
 */
goog.dom.DomHelper.prototype.removeNode = goog.dom.removeNode;


/**
 * Replaces a node in the DOM tree. Will do nothing if {@code oldNode} has no
 * parent.
 * @param {Node} newNode Node to insert.
 * @param {Node} oldNode Node to replace.
 */
goog.dom.DomHelper.prototype.replaceNode = goog.dom.replaceNode;


/**
 * Flattens an element. That is, removes it and replace it with its children.
 * @param {Element} element The element to flatten.
 * @return {Element|undefined} The original element, detached from the document
 *     tree, sans children, or undefined if the element was already not in the
 *     document.
 */
goog.dom.DomHelper.prototype.flattenElement = goog.dom.flattenElement;


/**
 * Returns the first child node that is an element.
 * @param {Node} node The node to get the first child element of.
 * @return {Element} The first child node of {@code node} that is an element.
 */
goog.dom.DomHelper.prototype.getFirstElementChild =
    goog.dom.getFirstElementChild;


/**
 * Returns the last child node that is an element.
 * @param {Node} node The node to get the last child element of.
 * @return {Element} The last child node of {@code node} that is an element.
 */
goog.dom.DomHelper.prototype.getLastElementChild = goog.dom.getLastElementChild;


/**
 * Returns the first next sibling that is an element.
 * @param {Node} node The node to get the next sibling element of.
 * @return {Element} The next sibling of {@code node} that is an element.
 */
goog.dom.DomHelper.prototype.getNextElementSibling =
    goog.dom.getNextElementSibling;


/**
 * Returns the first previous sibling that is an element.
 * @param {Node} node The node to get the previous sibling element of.
 * @return {Element} The first previous sibling of {@code node} that is
 *     an element.
 */
goog.dom.DomHelper.prototype.getPreviousElementSibling =
    goog.dom.getPreviousElementSibling;


/**
 * Returns the next node in source order from the given node.
 * @param {Node} node The node.
 * @return {Node} The next node in the DOM tree, or null if this was the last
 *     node.
 */
goog.dom.DomHelper.prototype.getNextNode =
    goog.dom.getNextNode;


/**
 * Returns the previous node in source order from the given node.
 * @param {Node} node The node.
 * @return {Node} The previous node in the DOM tree, or null if this was the
 *     first node.
 */
goog.dom.DomHelper.prototype.getPreviousNode =
    goog.dom.getPreviousNode;


/**
 * Whether the object looks like a DOM node.
 * @param {*} obj The object being tested for node likeness.
 * @return {boolean} Whether the object looks like a DOM node.
 */
goog.dom.DomHelper.prototype.isNodeLike = goog.dom.isNodeLike;


/**
 * Whether a node contains another node.
 * @param {Node} parent The node that should contain the other node.
 * @param {Node} descendant The node to test presence of.
 * @return {boolean} Whether the parent node contains the descendent node.
 */
goog.dom.DomHelper.prototype.contains = goog.dom.contains;


/**
 * Returns the owner document for a node.
 * @param {Node} node The node to get the document for.
 * @return {!Document} The document owning the node.
 */
goog.dom.DomHelper.prototype.getOwnerDocument = goog.dom.getOwnerDocument;


/**
 * Cross browser function for getting the document element of an iframe.
 * @param {HTMLIFrameElement|HTMLFrameElement} iframe Iframe element.
 * @return {!HTMLDocument} The frame content document.
 */
goog.dom.DomHelper.prototype.getFrameContentDocument =
    goog.dom.getFrameContentDocument;


/**
 * Cross browser function for getting the window of a frame or iframe.
 * @param {HTMLIFrameElement|HTMLFrameElement} frame Frame element.
 * @return {Window} The window associated with the given frame.
 */
goog.dom.DomHelper.prototype.getFrameContentWindow =
    goog.dom.getFrameContentWindow;


/**
 * Cross browser function for setting the text content of an element.
 * @param {Element} element The element to change the text content of.
 * @param {string} text The string that should replace the current element
 *     content with.
 */
goog.dom.DomHelper.prototype.setTextContent = goog.dom.setTextContent;


/**
 * Finds the first descendant node that matches the filter function. This does
 * a depth first search.
 * @param {Node} root The root of the tree to search.
 * @param {function(Node) : boolean} p The filter function.
 * @return {(Node, undefined)} The found node or undefined if none is found.
 */
goog.dom.DomHelper.prototype.findNode = goog.dom.findNode;


/**
 * Finds all the descendant nodes that matches the filter function. This does a
 * depth first search.
 * @param {Node} root The root of the tree to search.
 * @param {function(Node) : boolean} p The filter function.
 * @return {Array.<Node>} The found nodes or an empty array if none are found.
 */
goog.dom.DomHelper.prototype.findNodes = goog.dom.findNodes;


/**
 * Returns the text contents of the current node, without markup. New lines are
 * stripped and whitespace is collapsed, such that each character would be
 * visible.
 *
 * In browsers that support it, innerText is used.  Other browsers attempt to
 * simulate it via node traversal.  Line breaks are canonicalized in IE.
 *
 * @param {Node} node The node from which we are getting content.
 * @return {string} The text content.
 */
goog.dom.DomHelper.prototype.getTextContent = goog.dom.getTextContent;


/**
 * Returns the text length of the text contained in a node, without markup. This
 * is equivalent to the selection length if the node was selected, or the number
 * of cursor movements to traverse the node. Images & BRs take one space.  New
 * lines are ignored.
 *
 * @param {Node} node The node whose text content length is being calculated.
 * @return {number} The length of {@code node}'s text content.
 */
goog.dom.DomHelper.prototype.getNodeTextLength = goog.dom.getNodeTextLength;


/**
 * Returns the text offset of a node relative to one of its ancestors. The text
 * length is the same as the length calculated by
 * {@code goog.dom.getNodeTextLength}.
 *
 * @param {Node} node The node whose offset is being calculated.
 * @param {Node=} opt_offsetParent Defaults to the node's owner document's body.
 * @return {number} The text offset.
 */
goog.dom.DomHelper.prototype.getNodeTextOffset = goog.dom.getNodeTextOffset;


/**
 * Walks up the DOM hierarchy returning the first ancestor that has the passed
 * tag name and/or class name. If the passed element matches the specified
 * criteria, the element itself is returned.
 * @param {Node} element The DOM node to start with.
 * @param {?string=} opt_tag The tag name to match (or null/undefined to match
 *     any node regardless of tag name). Must be uppercase (goog.dom.TagName).
 * @param {?string=} opt_class The class name to match (or null/undefined to
 *     match any node regardless of class name).
 * @return {Node} The first ancestor that matches the passed criteria, or
 *     null if none match.
 */
goog.dom.DomHelper.prototype.getAncestorByTagNameAndClass =
    goog.dom.getAncestorByTagNameAndClass;


/**
 * Walks up the DOM hierarchy returning the first ancestor that has the passed
 * class name. If the passed element matches the specified criteria, the
 * element itself is returned.
 * @param {Node} element The DOM node to start with.
 * @param {?string=} opt_class The class name to match (or null/undefined to
 *     match any node regardless of class name).
 * @return {Node} The first ancestor that matches the passed criteria, or
 *     null if none match.
 */
goog.dom.DomHelper.prototype.getAncestorByClass =
    goog.dom.getAncestorByClass;


/**
 * Walks up the DOM hierarchy returning the first ancestor that passes the
 * matcher function.
 * @param {Node} element The DOM node to start with.
 * @param {function(Node) : boolean} matcher A function that returns true if the
 *     passed node matches the desired criteria.
 * @param {boolean=} opt_includeNode If true, the node itself is included in
 *     the search (the first call to the matcher will pass startElement as
 *     the node to test).
 * @param {number=} opt_maxSearchSteps Maximum number of levels to search up the
 *     dom.
 * @return {Node} DOM node that matched the matcher, or null if there was
 *     no match.
 */
goog.dom.DomHelper.prototype.getAncestor = goog.dom.getAncestor;

//javascript/closure/structs/inversionmap.js
// Copyright 2008 The Closure Library Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @fileoverview Provides inversion and inversion map functionality for storing
 * integer ranges and corresponding values.
 *
 */

goog.provide('goog.structs.InversionMap');

goog.require('goog.array');



/**
 * Maps ranges to values using goog.structs.Inversion.
 * @param {Array.<number>} rangeArray An array of monotonically
 *     increasing integer values, with at least one instance.
 * @param {Array.<*>} valueArray An array of corresponding values.
 *     Length must be the same as rangeArray.
 * @param {boolean=} opt_delta If true, saves only delta from previous value.
 * @constructor
 */
goog.structs.InversionMap = function(rangeArray, valueArray, opt_delta) {
  if (rangeArray.length != valueArray.length) {
    // rangeArray and valueArray has to match in number of entries.
    return null;
  }
  this.storeInversion_(rangeArray, opt_delta);

  /**
   * @type {Array}
   * @protected
   */
  this.values = valueArray;
};


/**
 * @type {Array}
 * @protected
 */
goog.structs.InversionMap.prototype.rangeArray;


/**
 * Stores the integers as ranges (half-open).
 * If delta is true, the integers are delta from the previous value and
 * will be restored to the absolute value.
 * When used as a set, even indices are IN, and odd are OUT.
 * @param {Array.<number?>} rangeArray An array of monotonically
 *     increasing integer values, with at least one instance.
 * @param {boolean=} opt_delta If true, saves only delta from previous value.
 * @private
 */
goog.structs.InversionMap.prototype.storeInversion_ = function(rangeArray,
    opt_delta) {
  this.rangeArray = rangeArray;

  for (var i = 1; i < rangeArray.length; i++) {
    if (rangeArray[i] == null) {
      rangeArray[i] = rangeArray[i - 1] + 1;
    } else if (opt_delta) {
      rangeArray[i] += rangeArray[i - 1];
    }
  }
};


/**
 * Splices a range -> value map into this inversion map.
 * @param {Array.<number>} rangeArray An array of monotonically
 *     increasing integer values, with at least one instance.
 * @param {Array.<*>} valueArray An array of corresponding values.
 *     Length must be the same as rangeArray.
 * @param {boolean=} opt_delta If true, saves only delta from previous value.
 */
goog.structs.InversionMap.prototype.spliceInversion = function(
    rangeArray, valueArray, opt_delta) {
  // By building another inversion map, we build the arrays that we need
  // to splice in.
  var otherMap = new goog.structs.InversionMap(
      rangeArray, valueArray, opt_delta);

  // Figure out where to splice those arrays.
  var startRange = otherMap.rangeArray[0];
  var endRange =
      (/** @type {number} */ goog.array.peek(otherMap.rangeArray));
  var startSplice = this.getLeast(startRange);
  var endSplice = this.getLeast(endRange);

  // The inversion map works by storing the start points of ranges...
  if (startRange != this.rangeArray[startSplice]) {
    // ...if we're splicing in a start point that isn't already here,
    // then we need to insert it after the insertion point.
    startSplice++;
  } // otherwise we overwrite the insertion point.

  var spliceLength = endSplice - startSplice + 1;
  goog.partial(goog.array.splice, this.rangeArray, startSplice,
      spliceLength).apply(null, otherMap.rangeArray);
  goog.partial(goog.array.splice, this.values, startSplice,
      spliceLength).apply(null, otherMap.values);
};


/**
 * Gets the value corresponding to a number from the inversion map.
 * @param {number} intKey The number for which value needs to be retrieved
 *     from inversion map.
 * @return {*} Value retrieved from inversion map; null if not found.
 */
goog.structs.InversionMap.prototype.at = function(intKey) {
  var index = this.getLeast(intKey);
  if (index < 0) {
    return null;
  }
  return this.values[index];
};


/**
 * Gets the largest index such that rangeArray[index] <= intKey from the
 * inversion map.
 * @param {number} intKey The probe for which rangeArray is searched.
 * @return {number} Largest index such that rangeArray[index] <= intKey.
 * @protected
 */
goog.structs.InversionMap.prototype.getLeast = function(intKey) {
  var arr = this.rangeArray;
  var low = 0;
  var high = arr.length;
  while (high - low > 8) {
    var mid = (high + low) >> 1;
    if (arr[mid] <= intKey) {
      low = mid;
    } else {
      high = mid;
    }
  }
  for (; low < high; ++low) {
    if (intKey < arr[low]) {
      break;
    }
  }
  return low - 1;
};

//javascript/closure/i18n/graphemebreak.js
// Copyright 2006 The Closure Library Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @fileoverview Detect Grapheme Cluster Break in a pair of codepoints. Follows
 * Unicode 5.1 UAX#29.
 *
 */

goog.provide('goog.i18n.GraphemeBreak');
goog.require('goog.structs.InversionMap');


/**
 * Enum for all Grapheme Cluster Break properties.
 * These enums directly corresponds to Grapheme_Cluster_Break property values
 * mentioned in http://unicode.org/reports/tr29 table 2.
 *
 * CR and LF are moved to the bottom of the list because they occur only once
 * and so good candidates to take 2 decimal digit values.
 * @enum {number}
 * @protected
 */
goog.i18n.GraphemeBreak.property = {
  ANY: 0,
  CONTROL: 1,
  EXTEND: 2,
  PREPEND: 3,
  SPACING_MARK: 4,
  L: 5,
  V: 6,
  T: 7,
  LV: 8,
  LVT: 9,
  CR: 10,
  LF: 11
};


/**
 * Grapheme Cluster Break property values for all codepoints as inversion map.
 * Constructed lazily.
 *
 * @type {goog.structs.InversionMap}
 * @private
 */
goog.i18n.GraphemeBreak.inversions_ = null;


/**
 * There are two kinds of grapheme clusters: 1) Legacy 2)Extended. This method
 * is to check for legacy rules.
 *
 * @param {number} prop_a The property enum value of the first character.
 * @param {number} prop_b The property enum value of the second character.
 * @return {boolean} True if a & b do not form a cluster; False otherwise.
 * @private
 */
goog.i18n.GraphemeBreak.applyLegacyBreakRules_ = function(prop_a, prop_b) {

  var prop = goog.i18n.GraphemeBreak.property;

  if (prop_a == prop.CR && prop_b == prop.LF) {
    return false;
  }
  if (prop_a == prop.CONTROL || prop_a == prop.CR || prop_a == prop.LF) {
    return true;
  }
  if (prop_b == prop.CONTROL || prop_b == prop.CR || prop_b == prop.LF) {
    return true;
  }
  if ((prop_a == prop.L) &&
    (prop_b == prop.L || prop_b == prop.V ||
     prop_b == prop.LV || prop_b == prop.LVT)) {
    return false;
  }
  if ((prop_a == prop.LV || prop_a == prop.V) &&
    (prop_b == prop.V || prop_b == prop.T)) {
    return false;
  }
  if ((prop_a == prop.LVT || prop_a == prop.T) && (prop_b == prop.T)) {
    return false;
  }
  if (prop_b == prop.EXTEND) {
    return false;
  }
  return true;
};


/**
 * Method to return property enum value of the codepoint. If it is Hangul LV or
 * LVT, then it is computed; for the rest it is picked from the inversion map.
 * @param {number} acode The code point value of the character.
 * @return {number} Property enum value of codepoint.
 * @private
 */
goog.i18n.GraphemeBreak.getBreakProp_ = function(acode) {
  if (0xAC00 <= acode && acode <= 0xD7A3) {
    var prop = goog.i18n.GraphemeBreak.property;
    if (acode % 0x1C == 0x10) {
      return prop.LV;
    }
    return prop.LVT;
  } else {
    if (!goog.i18n.GraphemeBreak.inversions_) {
      goog.i18n.GraphemeBreak.inversions_ = new goog.structs.InversionMap(
          [0, 10, 1, 2, 1, 18, 95, 33, 13, 1, 594, 112, 275, 7, 263, 45, 1, 1,
           1, 2, 1, 2, 1, 1, 56, 4, 12, 11, 48, 20, 17, 1, 101, 7, 1, 7, 2, 2,
           1, 4, 33, 1, 1, 1, 30, 27, 91, 11, 58, 9, 269, 2, 1, 56, 1, 1, 3, 8,
           4, 1, 3, 4, 13, 2, 29, 1, 2, 56, 1, 1, 1, 2, 6, 6, 1, 9, 1, 10, 2,
           29, 2, 1, 56, 2, 3, 17, 30, 2, 3, 14, 1, 56, 1, 1, 3, 8, 4, 1, 20,
           2, 29, 1, 2, 56, 1, 1, 2, 1, 6, 6, 11, 10, 2, 30, 1, 59, 1, 1, 1,
           12, 1, 9, 1, 41, 3, 58, 3, 5, 17, 11, 2, 30, 2, 56, 1, 1, 1, 1, 2,
           1, 3, 1, 5, 11, 11, 2, 30, 2, 58, 1, 2, 5, 7, 11, 10, 2, 30, 2, 70,
           6, 2, 6, 7, 19, 2, 60, 11, 5, 5, 1, 1, 8, 97, 13, 3, 5, 3, 6, 74, 2,
           27, 1, 1, 1, 1, 1, 4, 2, 49, 14, 1, 5, 1, 2, 8, 45, 9, 1, 100, 2, 4,
           1, 6, 1, 2, 2, 2, 23, 2, 2, 4, 3, 1, 3, 2, 7, 3, 4, 13, 1, 2, 2, 6,
           1, 1, 1, 112, 96, 72, 82, 357, 1, 946, 3, 29, 3, 29, 2, 30, 2, 64,
           2, 1, 7, 8, 1, 2, 11, 9, 1, 45, 3, 155, 1, 118, 3, 4, 2, 9, 1, 6, 3,
           116, 17, 7, 2, 77, 2, 3, 228, 4, 1, 47, 1, 1, 5, 1, 1, 5, 1, 2, 38,
           9, 12, 2, 1, 30, 1, 4, 2, 2, 1, 121, 8, 8, 2, 2, 392, 64, 523, 1, 2,
           2, 24, 7, 49, 16, 96, 33, 3311, 32, 554, 6, 105, 2, 30164, 4, 9, 2,
           388, 1, 3, 1, 4, 1, 23, 2, 2, 1, 88, 2, 50, 16, 1, 97, 8, 25, 11, 2,
           213, 6, 2, 2, 2, 2, 12, 1, 8, 1, 1, 434, 11172, 1116, 1024, 6942, 1,
           737, 16, 16, 7, 216, 1, 158, 2, 89, 3, 513, 1, 2051, 15, 40, 8,
           50981, 1, 1, 3, 3, 1, 5, 8, 8, 2, 7, 30, 4, 148, 3, 798140, 255],
          [1, 11, 1, 10, 1, 0, 1, 0, 1, 0, 2, 0, 2, 0, 2, 0, 2, 0, 2, 0, 2, 0,
           2, 0, 1, 0, 2, 0, 2, 0, 2, 0, 2, 1, 2, 0, 2, 0, 2, 0, 1, 0, 2, 0, 2,
           0, 2, 0, 2, 0, 2, 4, 0, 2, 0, 4, 2, 4, 2, 0, 2, 0, 2, 0, 2, 4, 0, 2,
           0, 2, 4, 2, 4, 2, 0, 2, 0, 2, 0, 2, 4, 0, 2, 4, 2, 0, 2, 0, 2, 4, 0,
           2, 0, 4, 2, 4, 2, 0, 2, 0, 2, 4, 0, 2, 0, 2, 4, 2, 4, 2, 0, 2, 0, 2,
           0, 2, 4, 2, 4, 2, 0, 2, 0, 4, 0, 2, 4, 2, 0, 2, 0, 4, 0, 2, 0, 4, 2,
           4, 2, 4, 2, 4, 2, 0, 2, 0, 4, 0, 2, 4, 2, 4, 2, 0, 2, 0, 4, 0, 2, 4,
           2, 4, 2, 4, 0, 2, 0, 3, 2, 0, 2, 0, 2, 0, 3, 0, 2, 0, 2, 0, 2, 0, 2,
           0, 2, 0, 4, 0, 2, 4, 2, 0, 2, 0, 2, 0, 2, 0, 4, 2, 4, 2, 4, 2, 4, 2,
           0, 4, 2, 0, 2, 0, 4, 0, 4, 0, 2, 0, 2, 4, 2, 4, 2, 0, 4, 0, 5, 6, 7,
           0, 2, 0, 2, 0, 2, 0, 2, 0, 2, 0, 1, 4, 2, 4, 2, 4, 2, 0, 2, 0, 2, 0,
           2, 0, 2, 4, 2, 4, 2, 4, 2, 0, 4, 0, 4, 0, 2, 4, 0, 2, 4, 0, 2, 4, 2,
           4, 2, 4, 2, 4, 0, 2, 0, 2, 4, 0, 4, 2, 4, 2, 4, 0, 4, 2, 4, 2, 0, 2,
           0, 1, 2, 1, 0, 1, 0, 1, 0, 2, 0, 2, 0, 2, 0, 2, 0, 2, 0, 2, 0, 2, 0,
           2, 0, 2, 0, 4, 2, 4, 0, 4, 0, 4, 2, 0, 2, 0, 2, 4, 0, 2, 4, 2, 4, 2,
           0, 2, 0, 2, 4, 0, 9, 0, 2, 0, 2, 0, 2, 0, 2, 0, 1, 0, 2, 0, 1, 0, 2,
           0, 2, 0, 2, 0, 2, 4, 2, 0, 4, 2, 1, 2, 0, 2, 0, 2, 0, 2, 0, 1, 2],
          true);
    }
    return (/** @type {number} */
        goog.i18n.GraphemeBreak.inversions_.at(acode));
  }
};


/**
 * There are two kinds of grapheme clusters: 1) Legacy 2)Extended. This method
 * is to check for both using a boolean flag to switch between them.
 * @param {number} a The code point value of the first character.
 * @param {number} b The code point value of the second character.
 * @param {boolean=} opt_extended If true, indicates extended grapheme cluster;
 *     If false, indicates legacy cluster.
 * @return {boolean} True if a & b do not form a cluster; False otherwise.
 */
goog.i18n.GraphemeBreak.hasGraphemeBreak = function(a, b, opt_extended) {

  var prop_a = goog.i18n.GraphemeBreak.getBreakProp_(a);
  var prop_b = goog.i18n.GraphemeBreak.getBreakProp_(b);
  var prop = goog.i18n.GraphemeBreak.property;

  return goog.i18n.GraphemeBreak.applyLegacyBreakRules_(prop_a, prop_b) &&
    !(opt_extended && (prop_a == prop.PREPEND || prop_b == prop.SPACING_MARK));
};

//javascript/closure/format/format.js
// Copyright 2006 The Closure Library Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @fileoverview Provides utility functions for formatting strings, numbers etc.
 *
 */

goog.provide('goog.format');

goog.require('goog.i18n.GraphemeBreak');
goog.require('goog.string');
goog.require('goog.userAgent');


/**
 * Formats a number of bytes in human readable form.
 * 54, 450K, 1.3M, 5G etc.
 * @param {number} bytes The number of bytes to show.
 * @param {number=} opt_decimals The number of decimals to use.  Defaults to 2.
 * @return {string} The human readable form of the byte size.
 */
goog.format.fileSize = function(bytes, opt_decimals) {
  return goog.format.numBytesToString(bytes, opt_decimals, false);
};


/**
 * Checks whether string value containing scaling units (K, M, G, T, P, m,
 * u, n) can be converted to a number.
 *
 * Where there is a decimal, there must be a digit to the left of the
 * decimal point.
 *
 * Negative numbers are valid.
 *
 * Examples:
 *   0, 1, 1.0, 10.4K, 2.3M, -0.3P, 1.2m
 *
 * @param {string} val String value to check.
 * @return {boolean} True if string could be converted to a numeric value.
 */
goog.format.isConvertableScaledNumber = function(val) {
  return goog.format.SCALED_NUMERIC_RE_.test(val);
};


/**
 * Converts a string to numeric value, taking into account the units.
 * If string ends in 'B', use binary conversion.
 * @param {string} stringValue String to be converted to numeric value.
 * @return {number} Numeric value for string.
 */
goog.format.stringToNumericValue = function(stringValue) {
  if (goog.string.endsWith(stringValue, 'B')) {
    return goog.format.stringToNumericValue_(
        stringValue, goog.format.NUMERIC_SCALES_BINARY_);
  }
  return goog.format.stringToNumericValue_(
      stringValue, goog.format.NUMERIC_SCALES_SI_);
};


/**
 * Converts a string to number of bytes, taking into account the units.
 * Binary conversion.
 * @param {string} stringValue String to be converted to numeric value.
 * @return {number} Numeric value for string.
 */
goog.format.stringToNumBytes = function(stringValue) {
  return goog.format.stringToNumericValue_(
      stringValue, goog.format.NUMERIC_SCALES_BINARY_);
};


/**
 * Converts a numeric value to string representation. SI conversion.
 * @param {number} val Value to be converted.
 * @param {number=} opt_decimals The number of decimals to use.  Defaults to 2.
 * @return {string} String representation of number.
 */
goog.format.numericValueToString = function(val, opt_decimals) {
  return goog.format.numericValueToString_(
      val, goog.format.NUMERIC_SCALES_SI_, opt_decimals);
};


/**
 * Converts number of bytes to string representation. Binary conversion.
 * Default is to return the additional 'B' suffix, e.g. '10.5KB' to minimize
 * confusion with counts that are scaled by powers of 1000.
 * @param {number} val Value to be converted.
 * @param {number=} opt_decimals The number of decimals to use.  Defaults to 2.
 * @param {boolean=} opt_suffix If true, include trailing 'B' in returned
 *     string.  Default is true.
 * @return {string} String representation of number of bytes.
 */
goog.format.numBytesToString = function(val, opt_decimals, opt_suffix) {
  var suffix = '';
  if (!goog.isDef(opt_suffix) || opt_suffix) {
    suffix = 'B';
  }
  return goog.format.numericValueToString_(
      val, goog.format.NUMERIC_SCALES_BINARY_, opt_decimals, suffix);
};


/**
 * Converts a string to numeric value, taking into account the units.
 * @param {string} stringValue String to be converted to numeric value.
 * @param {Object} conversion Dictionary of conversion scales.
 * @return {number} Numeric value for string.  If it cannot be converted,
 *    returns NaN.
 * @private
 */
goog.format.stringToNumericValue_ = function(stringValue, conversion) {
  var match = stringValue.match(goog.format.SCALED_NUMERIC_RE_);
  if (!match) {
    return NaN;
  }
  var val = match[1] * conversion[match[2]];
  return val;
};


/**
 * Converts a numeric value to string, using specified conversion
 * scales.
 * @param {number} val Value to be converted.
 * @param {Object} conversion Dictionary of scaling factors.
 * @param {number=} opt_decimals The number of decimals to use.  Default is 2.
 * @param {string=} opt_suffix Optional suffix to append.
 * @return {string} The human readable form of the byte size.
 * @private
 */
goog.format.numericValueToString_ = function(val, conversion,
                                             opt_decimals, opt_suffix) {
  var prefixes = goog.format.NUMERIC_SCALE_PREFIXES_;
  var orig_val = val;
  var symbol = '';
  var scale = 1;
  if (val < 0) {
    val = -val;
  }
  for (var i = 0; i < prefixes.length; i++) {
    var unit = prefixes[i];
    scale = conversion[unit];
    if (val >= scale || (scale <= 1 && val > 0.1 * scale)) {
      // Treat values less than 1 differently, allowing 0.5 to be "0.5" rather
      // than "500m"
      symbol = unit;
      break;
    }
  }
  if (!symbol) {
    scale = 1;
  } else if (opt_suffix) {
    symbol += opt_suffix;
  }
  var ex = Math.pow(10, goog.isDef(opt_decimals) ? opt_decimals : 2);
  return Math.round(orig_val / scale * ex) / ex + symbol;
};


/**
 * Regular expression for detecting scaling units, such as K, M, G, etc. for
 * converting a string representation to a numeric value.
 *
 * Also allow 'k' to be aliased to 'K'.  These could be used for SI (powers
 * of 1000) or Binary (powers of 1024) conversions.
 *
 * Also allow final 'B' to be interpreted as byte-count, implicitly triggering
 * binary conversion (e.g., '10.2MB').
 *
 * @type {RegExp}
 * @private
 */
goog.format.SCALED_NUMERIC_RE_ = /^([-]?\d+\.?\d*)([K,M,G,T,P,k,m,u,n]?)[B]?$/;


/**
 * Ordered list of scaling prefixes in decreasing order.
 * @type {Array}
 * @private
 */
goog.format.NUMERIC_SCALE_PREFIXES_ = [
  'P', 'T', 'G', 'M', 'K', '', 'm', 'u', 'n'
];


/**
 * Scaling factors for conversion of numeric value to string.  SI conversion.
 * @type {Object}
 * @private
 */
goog.format.NUMERIC_SCALES_SI_ = {
  '': 1,
  'n': 1e-9,
  'u': 1e-6,
  'm': 1e-3,
  'k': 1e3,
  'K': 1e3,
  'M': 1e6,
  'G': 1e9,
  'T': 1e12,
  'P': 1e15
};


/**
 * Scaling factors for conversion of numeric value to string.  Binary
 * conversion.
 * @type {Object}
 * @private
 */
goog.format.NUMERIC_SCALES_BINARY_ = {
  '': 1,
  'n': Math.pow(1024, -3),
  'u': Math.pow(1024, -2),
  'm': 1.0 / 1024,
  'k': 1024,
  'K': 1024,
  'M': Math.pow(1024, 2),
  'G': Math.pow(1024, 3),
  'T': Math.pow(1024, 4),
  'P': Math.pow(1024, 5)
};


/**
 * First Unicode code point that has the Mark property.
 * @type {number}
 * @private
 */
goog.format.FIRST_GRAPHEME_EXTEND_ = 0x300;


/**
 * Inserts word breaks into an HTML string at a given interval.  The counter is
 * reset if a space is encountered.  WBRs aren't inserted into HTML tags or
 * entities.  Entites count towards the character count, HTML tags do not.
 *
 * With common strings aliased, objects allocations are constant based on the
 * length of the string: N + 3. This guarantee does not hold if the string
 * contains an element >= U+0300 and hasGraphemeBreak is non-trivial.
 *
 * @param {string} str HTML to insert word breaks into.
 * @param {function(number, number, boolean): boolean} hasGraphemeBreak A
 *     function determining if there is a grapheme break between two characters,
 *     in the same signature as goog.i18n.GraphemeBreak.hasGraphemeBreak.
 * @param {number=} opt_maxlen Maximum length after which to ensure
 *     there is a break.  Default is 10 characters.
 * @return {string} The string including word breaks.
 * @private
 */
goog.format.insertWordBreaksGeneric_ = function(str, hasGraphemeBreak,
    opt_maxlen) {
  var maxlen = opt_maxlen || 10;
  if (maxlen > str.length) return str;

  var rv = [];
  var n = 0; // The length of the current token

  // This will contain the ampersand or less-than character if one of the
  // two has been seen; otherwise, the value is zero.
  var nestingCharCode = 0;

  // First character position from input string that has not been outputted.
  var lastDumpPosition = 0;

  var charCode = 0;
  for (var i = 0; i < str.length; i++) {
    // Using charCodeAt versus charAt avoids allocating new string objects.
    var lastCharCode = charCode;
    charCode = str.charCodeAt(i);

    // Don't add a WBR before characters that might be grapheme extending.
    var isPotentiallyGraphemeExtending =
        charCode >= goog.format.FIRST_GRAPHEME_EXTEND_ &&
        !hasGraphemeBreak(lastCharCode, charCode, true);

    // Don't add a WBR at the end of a word.  For simplicity, all control
    // characters are treated as whitespace.
    if (n >= maxlen &&
        charCode > goog.format.WbrToken_.SPACE &&
        !isPotentiallyGraphemeExtending) {
      // Flush everything seen so far, and append a word break.
      rv.push(str.substring(lastDumpPosition, i), goog.format.WORD_BREAK_HTML);
      lastDumpPosition = i;
      n = 0;
    }

    if (!nestingCharCode) {
      // Not currently within an HTML tag or entity

      if (charCode == goog.format.WbrToken_.LT ||
          charCode == goog.format.WbrToken_.AMP) {

        // Entering an HTML Entity '&' or open tag '<'
        nestingCharCode = charCode;
      } else if (charCode <= goog.format.WbrToken_.SPACE) {

        // A space or control character -- reset the token length
        n = 0;
      } else {

        // A normal flow character - increment.  For grapheme extending
        // characters, this is not *technically* a new character.  However,
        // since the grapheme break detector might be overly conservative,
        // we have to continue incrementing, or else we won't even be able
        // to add breaks when we get to things like punctuation.  For the
        // case where we have a full grapheme break detector, it is okay if
        // we occasionally break slightly early.
        n++;
      }
    } else if (charCode == goog.format.WbrToken_.GT &&
        nestingCharCode == goog.format.WbrToken_.LT) {

      // Leaving an HTML tag, treat the tag as zero-length
      nestingCharCode = 0;
    } else if (charCode == goog.format.WbrToken_.SEMI_COLON &&
        nestingCharCode == goog.format.WbrToken_.AMP) {

      // Leaving an HTML entity, treat it as length one
      nestingCharCode = 0;
      n++;
    }
  }

  // Take care of anything we haven't flushed so far.
  rv.push(str.substr(lastDumpPosition));

  return rv.join('');
};


/**
 * Inserts word breaks into an HTML string at a given interval.
 *
 * This method is as aggressive as possible, using a full table of Unicode
 * characters where it is legal to insert word breaks; however, this table
 * comes at a 2.5k pre-gzip (~1k post-gzip) size cost.  Consider using
 * insertWordBreaksBasic to minimize the size impact.
 *
 * @param {string} str HTML to insert word breaks into.
 * @param {number=} opt_maxlen Maximum length after which to ensure there is a
 *     break.  Default is 10 characters.
 * @return {string} The string including word breaks.
 */
goog.format.insertWordBreaks = function(str, opt_maxlen) {
  return goog.format.insertWordBreaksGeneric_(str,
      goog.i18n.GraphemeBreak.hasGraphemeBreak, opt_maxlen);
};


/**
 * Determines conservatively if a character has a Grapheme break.
 *
 * Conforms to a similar signature as goog.i18n.GraphemeBreak, but is overly
 * conservative, returning true only for characters in common scripts that
 * are simple to account for.
 *
 * @param {number} lastCharCode The previous character code.  Ignored.
 * @param {number} charCode The character code under consideration.  It must be
 *     at least \u0300 as a precondition -- this case is covered by
 *     insertWordBreaksGeneric_.
 * @param {boolean=} opt_extended Ignored, to conform with the interface.
 * @return {boolean} Whether it is one of the recognized subsets of characters
 *     with a grapheme break.
 * @private
 */
goog.format.conservativelyHasGraphemeBreak_ = function(
    lastCharCode, charCode, opt_extended) {
  // Return false for everything except the most common Cyrillic characters.
  // Don't worry about Latin characters, because insertWordBreaksGeneric_
  // itself already handles those.
  // TODO(gboyer): Also account for Greek, Armenian, and Georgian if it is
  // simple to do so.
  return charCode >= 0x400 && charCode < 0x523;
};


// TODO(gboyer): Consider using a compile-time flag to switch implementations
// rather than relying on the developers to toggle implementations.
/**
 * Inserts word breaks into an HTML string at a given interval.
 *
 * This method is less aggressive than insertWordBreaks, only inserting
 * breaks next to punctuation and between Latin or Cyrillic characters.
 * However, this is good enough for the common case of URLs.  It also
 * works for all Latin and Cyrillic languages, plus CJK has no need for word
 * breaks.  When this method is used, goog.i18n.GraphemeBreak may be dead
 * code eliminated.
 *
 * @param {string} str HTML to insert word breaks into.
 * @param {number=} opt_maxlen Maximum length after which to ensure there is a
 *     break.  Default is 10 characters.
 * @return {string} The string including word breaks.
 */
goog.format.insertWordBreaksBasic = function(str, opt_maxlen) {
  return goog.format.insertWordBreaksGeneric_(str,
      goog.format.conservativelyHasGraphemeBreak_, opt_maxlen);
};


/**
 * True iff the current userAgent is IE8 or above.
 * @type {boolean}
 * @private
 */
goog.format.IS_IE8_OR_ABOVE_ = goog.userAgent.IE &&
    goog.userAgent.isVersion(8);


/**
 * Constant for the WBR replacement used by insertWordBreaks.  Safari requires
 * <wbr></wbr>, Opera needs the &shy; entity, though this will give a visible
 * hyphen at breaks.  IE8 uses a zero width space.
 * Other browsers just use <wbr>.
 * @type {string}
 */
goog.format.WORD_BREAK_HTML =
    goog.userAgent.WEBKIT ?
        '<wbr></wbr>' : goog.userAgent.OPERA ?
            '&shy;' : goog.format.IS_IE8_OR_ABOVE_ ?
                '&#8203;' : '<wbr>';


/**
 * Tokens used within insertWordBreaks.
 * @private
 * @enum {number}
 */
goog.format.WbrToken_ = {
  LT: 60, // '<'.charCodeAt(0)
  GT: 62, // '>'.charCodeAt(0)
  AMP: 38, // '&'.charCodeAt(0)
  SEMI_COLON: 59, // ';'.charCodeAt(0)
  SPACE: 32 // ' '.charCodeAt(0)
};

//javascript/closure/i18n/bidi.js
// Copyright 2007 The Closure Library Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @fileoverview Utility functions for supporting Bidi issues.
 */


/**
 * Namespace for bidi supporting functions.
 */
goog.provide('goog.i18n.bidi');


/**
 * @define {boolean} FORCE_RTL forces the {@link goog.i18n.bidi.IS_RTL} constant
 * to say that the current locale is a RTL locale.  This should only be used
 * if you want to override the default behavior for deciding whether the
 * current locale is RTL or not.
 *
 * {@see goog.i18n.bidi.IS_RTL}
 */
goog.i18n.bidi.FORCE_RTL = false;


/**
 * Constant that defines whether or not the current locale is a RTL locale.
 * If {@link goog.i18n.bidi.FORCE_RTL} is not true, this constant will default
 * to check that {@link goog.LOCALE} is one of a few major RTL locales.
 *
 * <p>Since this constant refers to the directionality of the locale, it is up
 * to the caller to determine if this constant should also be used for the
 * direction of the UI.
 *
 * {@see goog.LOCALE}
 *
 * @type {boolean}
 *
 * TODO(user): write a test that checks that this is a compile-time constant.
 * For example, for the default goog.LOCALE, compiling
 * "if (goog.i18n.bidi.IS_RTL) alert('rtl') else {}" should produce no code.
 */
goog.i18n.bidi.IS_RTL = goog.i18n.bidi.FORCE_RTL ||
    (goog.LOCALE.substring(0, 2).toLowerCase() == 'ar' ||
     goog.LOCALE.substring(0, 2).toLowerCase() == 'fa' ||
     goog.LOCALE.substring(0, 2).toLowerCase() == 'he' ||
     goog.LOCALE.substring(0, 2).toLowerCase() == 'iw' ||
     goog.LOCALE.substring(0, 2).toLowerCase() == 'ur' ||
     goog.LOCALE.substring(0, 2).toLowerCase() == 'yi') &&
    (goog.LOCALE.length == 2 ||
     goog.LOCALE.substring(2, 3) == '-' ||
     goog.LOCALE.substring(2, 3) == '_');


/**
 * Unicode formatting characters and directionality string constants.
 * @enum {string}
 */
goog.i18n.bidi.Format = {
  /** Unicode "Left-To-Right Embedding" (LRE) character. */
  LRE: '\u202A',
  /** Unicode "Right-To-Left Embedding" (RLE) character. */
  RLE: '\u202B',
  /** Unicode "Pop Directional Formatting" (PDF) character. */
  PDF: '\u202C',
  /** Unicode "Left-To-Right Mark" (LRM) character. */
  LRM: '\u200E',
  /** Unicode "Right-To-Left Mark" (RLM) character. */
  RLM: '\u200F'
};


/**
 * Directionality enum.
 * @enum {number}
 */
goog.i18n.bidi.Dir = {
  RTL: -1,
  UNKNOWN: 0,
  LTR: 1
};


/**
 * 'right' string constant.
 * @type {string}
 */
goog.i18n.bidi.RIGHT = 'right';


/**
 * 'left' string constant.
 * @type {string}
 */
goog.i18n.bidi.LEFT = 'left';


/**
 * 'left' if locale is RTL, 'right' if not.
 * @type {string}
 */
goog.i18n.bidi.I18N_RIGHT = goog.i18n.bidi.IS_RTL ? goog.i18n.bidi.LEFT :
    goog.i18n.bidi.RIGHT;


/**
 * 'right' if locale is RTL, 'left' if not.
 * @type {string}
 */
goog.i18n.bidi.I18N_LEFT = goog.i18n.bidi.IS_RTL ? goog.i18n.bidi.RIGHT :
    goog.i18n.bidi.LEFT;


/**
 * Convert a directionality given in various formats to a goog.i18n.bidi.Dir
 * constant. Useful for interaction with different standards of directionality
 * representation.
 *
 * @param {goog.i18n.bidi.Dir|number|boolean} givenDir Directionality given in
 *     one of the following formats:
 *     1. A goog.i18n.bidi.Dir constant.
 *     2. A number (positive = LRT, negative = RTL, 0 = unknown).
 *     3. A boolean (true = RTL, false = LTR).
 * @return {goog.i18n.bidi.Dir} A goog.i18n.bidi.Dir constant matching the given
 *     directionality.
 */
goog.i18n.bidi.toDir = function(givenDir) {
  if (typeof givenDir == 'number') {
    return givenDir > 0 ? goog.i18n.bidi.Dir.LTR :
        givenDir < 0 ? goog.i18n.bidi.Dir.RTL : goog.i18n.bidi.Dir.UNKNOWN;
  } else {
    return givenDir ? goog.i18n.bidi.Dir.RTL : goog.i18n.bidi.Dir.LTR;
  }
};


/**
 * A practical pattern to identify strong LTR characters. This pattern is not
 * theoretically correct according to the Unicode standard. It is simplified for
 * performance and small code size.
 * @type {string}
 * @private
 */
goog.i18n.bidi.ltrChars_ =
    'A-Za-z\u00C0-\u00D6\u00D8-\u00F6\u00F8-\u02B8\u0300-\u0590\u0800-\u1FFF' +
    '\u2C00-\uFB1C\uFE00-\uFE6F\uFEFD-\uFFFF';


/**
 * A practical pattern to identify strong RTL character. This pattern is not
 * theoretically correct according to the Unicode standard. It is simplified
 * for performance and small code size.
 * @type {string}
 * @private
 */
goog.i18n.bidi.rtlChars_ = '\u0591-\u07FF\uFB1D-\uFDFF\uFE70-\uFEFC';


/**
 * Simplified regular expression for an HTML tag (opening or closing) or an HTML
 * escape. We might want to skip over such expressions when estimating the text
 * directionality.
 * @type {RegExp}
 * @private
 */
goog.i18n.bidi.htmlSkipReg_ = /<[^>]*>|&[^;]+;/g;


/**
 * Returns the input text with spaces instead of HTML tags or HTML escapes, if
 * opt_isStripNeeded is true. Else returns the input as is.
 * Useful for text directionality estimation.
 * Note: the function should not be used in other contexts; it is not 100%
 * correct, but rather a good-enough implementation for directionality
 * estimation purposes.
 * @param {string} str The given string.
 * @param {boolean=} opt_isStripNeeded Whether to perform the stripping.
 *     Default: false (to retain consistency with calling functions).
 * @return {string} The given string cleaned of HTML tags / escapes.
 * @private
 */
goog.i18n.bidi.stripHtmlIfNeeded_ = function(str, opt_isStripNeeded) {
  return opt_isStripNeeded ? str.replace(goog.i18n.bidi.htmlSkipReg_, ' ') :
      str;
};


/**
 * Regular expression to check for RTL characters.
 * @type {RegExp}
 * @private
 */
goog.i18n.bidi.rtlCharReg_ = new RegExp('[' + goog.i18n.bidi.rtlChars_ + ']');


/**
 * Regular expression to check for LTR characters.
 * @type {RegExp}
 * @private
 */
goog.i18n.bidi.ltrCharReg_ = new RegExp('[' + goog.i18n.bidi.ltrChars_ + ']');


/**
 * Test whether the given string has any RTL characters in it.
 * @param {string} str The given string that need to be tested.
 * @param {boolean=} opt_isHtml Whether str is HTML / HTML-escaped.
 *     Default: false.
 * @return {boolean} Whether the string contains RTL characters.
 */
goog.i18n.bidi.hasAnyRtl = function(str, opt_isHtml) {
  return goog.i18n.bidi.rtlCharReg_.test(goog.i18n.bidi.stripHtmlIfNeeded_(
      str, opt_isHtml));
};


/**
 * Test whether the given string has any RTL characters in it.
 * @param {string} str The given string that need to be tested.
 * @return {boolean} Whether the string contains RTL characters.
 * @deprecated Use hasAnyRtl.
 */
goog.i18n.bidi.hasRtlChar = goog.i18n.bidi.hasAnyRtl;


/**
 * Test whether the given string has any LTR characters in it.
 * @param {string} str The given string that need to be tested.
 * @param {boolean=} opt_isHtml Whether str is HTML / HTML-escaped.
 *     Default: false.
 * @return {boolean} Whether the string contains LTR characters.
 */
goog.i18n.bidi.hasAnyLtr = function(str, opt_isHtml) {
  return goog.i18n.bidi.ltrCharReg_.test(goog.i18n.bidi.stripHtmlIfNeeded_(
      str, opt_isHtml));
};


/**
 * Regular expression pattern to check if the first character in the string
 * is LTR.
 * @type {RegExp}
 * @private
 */
goog.i18n.bidi.ltrRe_ = new RegExp('^[' + goog.i18n.bidi.ltrChars_ + ']');


/**
 * Regular expression pattern to check if the first character in the string
 * is RTL.
 * @type {RegExp}
 * @private
 */
goog.i18n.bidi.rtlRe_ = new RegExp('^[' + goog.i18n.bidi.rtlChars_ + ']');


/**
 * Check if the first character in the string is RTL or not.
 * @param {string} str The given string that need to be tested.
 * @return {boolean} Whether the first character in str is an RTL char.
 */
goog.i18n.bidi.isRtlChar = function(str) {
  return goog.i18n.bidi.rtlRe_.test(str);
};


/**
 * Check if the first character in the string is LTR or not.
 * @param {string} str The given string that need to be tested.
 * @return {boolean} Whether the first character in str is an LTR char.
 */
goog.i18n.bidi.isLtrChar = function(str) {
  return goog.i18n.bidi.ltrRe_.test(str);
};


/**
 * Check if the first character in the string is neutral or not.
 * @param {string} str The given string that need to be tested.
 * @return {boolean} Whether the first character in str is a neutral char.
 */
goog.i18n.bidi.isNeutralChar = function(str) {
  return !goog.i18n.bidi.isLtrChar(str) && !goog.i18n.bidi.isRtlChar(str);
};


/**
 * Regular expressions to check if a piece of text if of LTR directionality
 * on first character with strong directionality.
 * @type {RegExp}
 * @private
 */
goog.i18n.bidi.ltrDirCheckRe_ = new RegExp(
    '^[^' + goog.i18n.bidi.rtlChars_ + ']*[' + goog.i18n.bidi.ltrChars_ + ']');


/**
 * Regular expressions to check if a piece of text if of RTL directionality
 * on first character with strong directionality.
 * @type {RegExp}
 * @private
 */
goog.i18n.bidi.rtlDirCheckRe_ = new RegExp(
    '^[^' + goog.i18n.bidi.ltrChars_ + ']*[' + goog.i18n.bidi.rtlChars_ + ']');


/**
 * Check whether the first strongly directional character (if any) is RTL.
 * @param {string} str String being checked.
 * @param {boolean=} opt_isHtml Whether str is HTML / HTML-escaped.
 *     Default: false.
 * @return {boolean} Whether RTL directionality is detected using the first
 *     strongly-directional character method.
 */
goog.i18n.bidi.startsWithRtl = function(str, opt_isHtml) {
  return goog.i18n.bidi.rtlDirCheckRe_.test(goog.i18n.bidi.stripHtmlIfNeeded_(
      str, opt_isHtml));
};


/**
 * Check whether the first strongly directional character (if any) is RTL.
 * @param {string} str String being checked.
 * @param {boolean=} opt_isHtml Whether str is HTML / HTML-escaped.
 *     Default: false.
 * @return {boolean} Whether RTL directionality is detected using the first
 *     strongly-directional character method.
 * @deprecated Use startsWithRtl.
 */
goog.i18n.bidi.isRtlText = goog.i18n.bidi.startsWithRtl;


/**
 * Check whether the first strongly directional character (if any) is LTR.
 * @param {string} str String being checked.
 * @param {boolean=} opt_isHtml Whether str is HTML / HTML-escaped.
 *     Default: false.
 * @return {boolean} Whether LTR directionality is detected using the first
 *     strongly-directional character method.
 */
goog.i18n.bidi.startsWithLtr = function(str, opt_isHtml) {
  return goog.i18n.bidi.ltrDirCheckRe_.test(goog.i18n.bidi.stripHtmlIfNeeded_(
      str, opt_isHtml));
};


/**
 * Check whether the first strongly directional character (if any) is LTR.
 * @param {string} str String being checked.
 * @param {boolean=} opt_isHtml Whether str is HTML / HTML-escaped.
 *     Default: false.
 * @return {boolean} Whether LTR directionality is detected using the first
 *     strongly-directional character method.
 * @deprecated Use startsWithLtr.
 */
goog.i18n.bidi.isLtrText = goog.i18n.bidi.startsWithLtr;


/**
 * Regular expression to check if a string looks like something that must
 * always be LTR even in RTL text, e.g. a URL. When estimating the
 * directionality of text containing these, we treat these as weakly LTR,
 * like numbers.
 * @type {RegExp}
 * @private
 */
goog.i18n.bidi.isRequiredLtrRe_ = /^http:\/\/.*/;


/**
 * Check whether the input string either contains no strongly directional
 * characters or looks like a url.
 * @param {string} str String being checked.
 * @param {boolean=} opt_isHtml Whether str is HTML / HTML-escaped.
 *     Default: false.
 * @return {boolean} Whether neutral directionality is detected.
 */
goog.i18n.bidi.isNeutralText = function(str, opt_isHtml) {
  str = goog.i18n.bidi.stripHtmlIfNeeded_(str, opt_isHtml);
  return goog.i18n.bidi.isRequiredLtrRe_.test(str) ||
      !goog.i18n.bidi.hasAnyLtr(str) && !goog.i18n.bidi.hasAnyRtl(str);
};


/**
 * Regular expressions to check if the last strongly-directional character in a
 * piece of text is LTR.
 * @type {RegExp}
 * @private
 */
goog.i18n.bidi.ltrExitDirCheckRe_ = new RegExp(
    '[' + goog.i18n.bidi.ltrChars_ + '][^' + goog.i18n.bidi.rtlChars_ + ']*$');


/**
 * Regular expressions to check if the last strongly-directional character in a
 * piece of text is RTL.
 * @type {RegExp}
 * @private
 */
goog.i18n.bidi.rtlExitDirCheckRe_ = new RegExp(
    '[' + goog.i18n.bidi.rtlChars_ + '][^' + goog.i18n.bidi.ltrChars_ + ']*$');


/**
 * Check if the exit directionality a piece of text is LTR, i.e. if the last
 * strongly-directional character in the string is LTR.
 * @param {string} str String being checked.
 * @param {boolean=} opt_isHtml Whether str is HTML / HTML-escaped.
 *     Default: false.
 * @return {boolean} Whether LTR exit directionality was detected.
 */
goog.i18n.bidi.endsWithLtr = function(str, opt_isHtml) {
  return goog.i18n.bidi.ltrExitDirCheckRe_.test(
      goog.i18n.bidi.stripHtmlIfNeeded_(str, opt_isHtml));
};


/**
 * Check if the exit directionality a piece of text is LTR, i.e. if the last
 * strongly-directional character in the string is LTR.
 * @param {string} str String being checked.
 * @param {boolean=} opt_isHtml Whether str is HTML / HTML-escaped.
 *     Default: false.
 * @return {boolean} Whether LTR exit directionality was detected.
 * @deprecated Use endsWithLtr.
 */
goog.i18n.bidi.isLtrExitText = goog.i18n.bidi.endsWithLtr;


/**
 * Check if the exit directionality a piece of text is RTL, i.e. if the last
 * strongly-directional character in the string is RTL.
 * @param {string} str String being checked.
 * @param {boolean=} opt_isHtml Whether str is HTML / HTML-escaped.
 *     Default: false.
 * @return {boolean} Whether RTL exit directionality was detected.
 */
goog.i18n.bidi.endsWithRtl = function(str, opt_isHtml) {
  return goog.i18n.bidi.rtlExitDirCheckRe_.test(
      goog.i18n.bidi.stripHtmlIfNeeded_(str, opt_isHtml));
};


/**
 * Check if the exit directionality a piece of text is RTL, i.e. if the last
 * strongly-directional character in the string is RTL.
 * @param {string} str String being checked.
 * @param {boolean=} opt_isHtml Whether str is HTML / HTML-escaped.
 *     Default: false.
 * @return {boolean} Whether RTL exit directionality was detected.
 * @deprecated Use endsWithRtl.
 */
goog.i18n.bidi.isRtlExitText = goog.i18n.bidi.endsWithRtl;


/**
 * A regular expression for matching right-to-left language codes.
 * See {@link #isRtlLanguage} for the design.
 * @type {RegExp}
 * @private
 */
goog.i18n.bidi.rtlLocalesRe_ = new RegExp(
    '^(ar|dv|he|iw|fa|nqo|ps|sd|ug|ur|yi|.*[-_](Arab|Hebr|Thaa|Nkoo|Tfng))' +
    '(?!.*[-_](Latn|Cyrl)($|-|_))($|-|_)');


/**
 * Check if a BCP 47 / III language code indicates an RTL language, i.e. either:
 * - a language code explicitly specifying one of the right-to-left scripts,
 *   e.g. "az-Arab", or<p>
 * - a language code specifying one of the languages normally written in a
 *   right-to-left script, e.g. "fa" (Farsi), except ones explicitly specifying
 *   Latin or Cyrillic script (which are the usual LTR alternatives).<p>
 * The list of right-to-left scripts appears in the 100-199 range in
 * http://www.unicode.org/iso15924/iso15924-num.html, of which Arabic and
 * Hebrew are by far the most widely used. We also recognize Thaana, N'Ko, and
 * Tifinagh, which also have significant modern usage. The rest (Syriac,
 * Samaritan, Mandaic, etc.) seem to have extremely limited or no modern usage
 * and are not recognized to save on code size.
 * The languages usually written in a right-to-left script are taken as those
 * with Suppress-Script: Hebr|Arab|Thaa|Nkoo|Tfng  in
 * http://www.iana.org/assignments/language-subtag-registry,
 * as well as Sindhi (sd) and Uyghur (ug).
 * Other subtags of the language code, e.g. regions like EG (Egypt), are
 * ignored.
 * @param {string} lang BCP 47 (a.k.a III) language code.
 * @return {boolean} Whether the language code is an RTL language.
 */
goog.i18n.bidi.isRtlLanguage = function(lang) {
  return goog.i18n.bidi.rtlLocalesRe_.test(lang);
};


/**
 * Regular expression for bracket guard replacement in html.
 * @type {RegExp}
 * @private
 */
goog.i18n.bidi.bracketGuardHtmlRe_ =
    /(\(.*?\)+)|(\[.*?\]+)|(\{.*?\}+)|(&lt;.*?(&gt;)+)/g;


/**
 * Regular expression for bracket guard replacement in text.
 * @type {RegExp}
 * @private
 */
goog.i18n.bidi.bracketGuardTextRe_ =
    /(\(.*?\)+)|(\[.*?\]+)|(\{.*?\}+)|(<.*?>+)/g;


/**
 * Apply bracket guard using html span tag. This is to address the problem of
 * messy bracket display frequently happens in RTL layout.
 * @param {string} s The string that need to be processed.
 * @param {boolean=} opt_isRtlContext specifies default direction (usually
 *     direction of the UI).
 * @return {string} The processed string, with all bracket guarded.
 */
goog.i18n.bidi.guardBracketInHtml = function(s, opt_isRtlContext) {
  var useRtl = opt_isRtlContext === undefined ?
      goog.i18n.bidi.hasAnyRtl(s) : opt_isRtlContext;
  if (useRtl) {
    return s.replace(goog.i18n.bidi.bracketGuardHtmlRe_,
        '<span dir=rtl>$&</span>');
  }
  return s.replace(goog.i18n.bidi.bracketGuardHtmlRe_,
      '<span dir=ltr>$&</span>');
};


/**
 * Apply bracket guard using LRM and RLM. This is to address the problem of
 * messy bracket display frequently happens in RTL layout.
 * This version works for both plain text and html. But it does not work as
 * good as guardBracketInHtml in some cases.
 * @param {string} s The string that need to be processed.
 * @param {boolean=} opt_isRtlContext specifies default direction (usually
 *     direction of the UI).
 * @return {string} The processed string, with all bracket guarded.
 */
goog.i18n.bidi.guardBracketInText = function(s, opt_isRtlContext) {
  var useRtl = opt_isRtlContext === undefined ?
      goog.i18n.bidi.hasAnyRtl(s) : opt_isRtlContext;
  var mark = useRtl ? goog.i18n.bidi.Format.RLM : goog.i18n.bidi.Format.LRM;
  return s.replace(goog.i18n.bidi.bracketGuardTextRe_, mark + '$&' + mark);
};


/**
 * Enforce the html snippet in RTL directionality regardless overall context.
 * If the html piece was enclosed by tag, dir will be applied to existing
 * tag, otherwise a span tag will be added as wrapper. For this reason, if
 * html snippet start with with tag, this tag must enclose the whole piece. If
 * the tag already has a dir specified, this new one will override existing
 * one in behavior (tested on FF and IE).
 * @param {string} html The string that need to be processed.
 * @return {string} The processed string, with directionality enforced to RTL.
 */
goog.i18n.bidi.enforceRtlInHtml = function(html) {
  if (html.charAt(0) == '<') {
    return html.replace(/<\w+/, '$& dir=rtl');
  }
  // '\n' is important for FF so that it won't incorrectly merge span groups
  return '\n<span dir=rtl>' + html + '</span>';
};


/**
 * Enforce RTL on both end of the given text piece using unicode BiDi formatting
 * characters RLE and PDF.
 * @param {string} text The piece of text that need to be wrapped.
 * @return {string} The wrapped string after process.
 */
goog.i18n.bidi.enforceRtlInText = function(text) {
  return goog.i18n.bidi.Format.RLE + text + goog.i18n.bidi.Format.PDF;
};


/**
 * Enforce the html snippet in RTL directionality regardless overall context.
 * If the html piece was enclosed by tag, dir will be applied to existing
 * tag, otherwise a span tag will be added as wrapper. For this reason, if
 * html snippet start with with tag, this tag must enclose the whole piece. If
 * the tag already has a dir specified, this new one will override existing
 * one in behavior (tested on FF and IE).
 * @param {string} html The string that need to be processed.
 * @return {string} The processed string, with directionality enforced to RTL.
 */
goog.i18n.bidi.enforceLtrInHtml = function(html) {
  if (html.charAt(0) == '<') {
    return html.replace(/<\w+/, '$& dir=ltr');
  }
  // '\n' is important for FF so that it won't incorrectly merge span groups
  return '\n<span dir=ltr>' + html + '</span>';
};


/**
 * Enforce LTR on both end of the given text piece using unicode BiDi formatting
 * characters LRE and PDF.
 * @param {string} text The piece of text that need to be wrapped.
 * @return {string} The wrapped string after process.
 */
goog.i18n.bidi.enforceLtrInText = function(text) {
  return goog.i18n.bidi.Format.LRE + text + goog.i18n.bidi.Format.PDF;
};


/**
 * Regular expression to find dimensions such as "padding: .3 0.4ex 5px 6;"
 * @type {RegExp}
 * @private
 */
goog.i18n.bidi.dimensionsRe_ =
     /:\s*([.\d][.\w]*)\s+([.\d][.\w]*)\s+([.\d][.\w]*)\s+([.\d][.\w]*)/g;


/**
 * Regular expression for left.
 * @type {RegExp}
 * @private
 */
goog.i18n.bidi.leftRe_ = /left/gi;


/**
 * Regular expression for right.
 * @type {RegExp}
 * @private
 */
goog.i18n.bidi.rightRe_ = /right/gi;


/**
 * Placeholder regular expression for swapping.
 * @type {RegExp}
 * @private
 */
goog.i18n.bidi.tempRe_ = /%%%%/g;


/**
 * Swap location parameters and 'left'/'right' in CSS specification. The
 * processed string will be suited for RTL layout. Though this function can
 * cover most cases, there are always exceptions. It is suggested to put
 * those exceptions in separate group of CSS string.
 * @param {string} cssStr CSS spefication string.
 * @return {string} Processed CSS specification string.
 */
goog.i18n.bidi.mirrorCSS = function(cssStr) {
  return cssStr.
    // reverse dimensions
    replace(goog.i18n.bidi.dimensionsRe_, ':$1 $4 $3 $2').
    replace(goog.i18n.bidi.leftRe_, '%%%%').          // swap left and right
    replace(goog.i18n.bidi.rightRe_, goog.i18n.bidi.LEFT).
    replace(goog.i18n.bidi.tempRe_, goog.i18n.bidi.RIGHT);
};


/**
 * Regular expression for hebrew double quote substitution, finding quote
 * directly after hebrew characters.
 * @type {RegExp}
 * @private
 */
goog.i18n.bidi.doubleQuoteSubstituteRe_ = /([\u0591-\u05f2])"/g;


/**
 * Regular expression for hebrew single quote substitution, finding quote
 * directly after hebrew characters.
 * @type {RegExp}
 * @private
 */
goog.i18n.bidi.singleQuoteSubstituteRe_ = /([\u0591-\u05f2])'/g;


/**
 * Replace the double and single quote directly after a Hebrew character with
 * GERESH and GERSHAYIM. In such case, most likely that's user intention.
 * @param {string} str String that need to be processed.
 * @return {string} Processed string with double/single quote replaced.
 */
goog.i18n.bidi.normalizeHebrewQuote = function(str) {
  return str.
    replace(goog.i18n.bidi.doubleQuoteSubstituteRe_, '$1\u05f4').
    replace(goog.i18n.bidi.singleQuoteSubstituteRe_, '$1\u05f3');
};


/**
 * Regular expression to split a string into "words" for directionality
 * estimation based on relative word counts.
 * @type {RegExp}
 * @private
 */
goog.i18n.bidi.wordSeparatorRe_ = /\s+/;


/**
 * Regular expression to check if a string contains any numerals. Used to
 * differentiate between completely neutral strings and those containing
 * numbers, which are weakly LTR.
 * @type {RegExp}
 * @private
 */
goog.i18n.bidi.hasNumeralsRe_ = /\d/;


/**
 * This constant controls threshold of RTL directionality.
 * @type {number}
 * @private
 */
goog.i18n.bidi.rtlDetectionThreshold_ = 0.40;


/**
 * Estimates the directionality of a string based on relative word counts.
 * If the number of RTL words is above a certain percentage of the total number
 * of strongly directional words, returns RTL.
 * Otherwise, if any words are strongly or weakly LTR, returns LTR.
 * Otherwise, returns UNKNOWN, which is used to mean "neutral".
 * Numbers are counted as weakly LTR.
 * @param {string} str The string to be checked.
 * @param {boolean=} opt_isHtml Whether str is HTML / HTML-escaped.
 *     Default: false.
 * @return {goog.i18n.bidi.Dir} Estimated overall directionality of {@code str}.
 */
goog.i18n.bidi.estimateDirection = function(str, opt_isHtml) {
  var rtlCount = 0;
  var totalCount = 0;
  var hasWeaklyLtr = false;
  var tokens = goog.i18n.bidi.stripHtmlIfNeeded_(str, opt_isHtml).
      split(goog.i18n.bidi.wordSeparatorRe_);
  for (var i = 0; i < tokens.length; i++) {
    var token = tokens[i];
    if (goog.i18n.bidi.startsWithRtl(token)) {
      rtlCount++;
      totalCount++;
    } else if (goog.i18n.bidi.isRequiredLtrRe_.test(token)) {
      hasWeaklyLtr = true;
    } else if (goog.i18n.bidi.hasAnyLtr(token)) {
      totalCount++;
    } else if (goog.i18n.bidi.hasNumeralsRe_.test(token)) {
      hasWeaklyLtr = true;
    }
  }

  return totalCount == 0 ?
      (hasWeaklyLtr ? goog.i18n.bidi.Dir.LTR : goog.i18n.bidi.Dir.UNKNOWN) :
      (rtlCount / totalCount > goog.i18n.bidi.rtlDetectionThreshold_ ?
          goog.i18n.bidi.Dir.RTL : goog.i18n.bidi.Dir.LTR);
};


/**
 * Check the directionality of a piece of text, return true if the piece of
 * text should be laid out in RTL direction.
 * @param {string} str The piece of text that need to be detected.
 * @param {boolean=} opt_isHtml Whether str is HTML / HTML-escaped.
 *     Default: false.
 * @return {boolean} Whether this piece of text should be laid out in RTL.
 */
goog.i18n.bidi.detectRtlDirectionality = function(str, opt_isHtml) {
  return goog.i18n.bidi.estimateDirection(str, opt_isHtml) ==
      goog.i18n.bidi.Dir.RTL;
};


/**
 * Sets text input element's directionality and text alignment based on a
 * given directionality.
 * @param {Element} element Input field element to set directionality to.
 * @param {goog.i18n.bidi.Dir|number|boolean} dir Desired directionality, given
 *     in one of the following formats:
 *     1. A goog.i18n.bidi.Dir constant.
 *     2. A number (positive = LRT, negative = RTL, 0 = unknown).
 *     3. A boolean (true = RTL, false = LTR).
 */
goog.i18n.bidi.setElementDirAndAlign = function(element, dir) {
  if (element &&
      (dir = goog.i18n.bidi.toDir(dir)) != goog.i18n.bidi.Dir.UNKNOWN) {
    element.style.textAlign = dir == goog.i18n.bidi.Dir.RTL ? 'right' : 'left';
    element.dir = dir == goog.i18n.bidi.Dir.RTL ? 'rtl' : 'ltr';
  }
};

//javascript/closure/i18n/bidiformatter.js
// Copyright 2009 The Closure Library Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @fileoverview Utility for formatting text for display in a potentially
 * opposite-directionality context without garbling.
 * Mostly a port of http://go/formatter.cc.
 * @author tomerigo@google.com (Tomer Greenberg)
 */


goog.provide('goog.i18n.BidiFormatter');

goog.require('goog.i18n.bidi');
goog.require('goog.string');



/**
 * Utility class for formatting text for display in a potentially
 * opposite-directionality context without garbling. Provides the following
 * functionality:
 *
 * 1. BiDi Wrapping
 * When text in one language is mixed into a document in another, opposite-
 * directionality language, e.g. when an English business name is embedded in a
 * Hebrew web page, both the inserted string and the text following it may be
 * displayed incorrectly unless the inserted string is explicitly separated
 * from the surrounding text in a "wrapper" that declares its directionality at
 * the start and then resets it back at the end. This wrapping can be done in
 * HTML mark-up (e.g. a 'span dir=rtl' tag) or - only in contexts where mark-up
 * can not be used - in Unicode BiDi formatting codes (LRE|RLE and PDF).
 * Providing such wrapping services is the basic purpose of the BiDi formatter.
 *
 * 2. Directionality estimation
 * How does one know whether a string about to be inserted into surrounding
 * text has the same directionality? Well, in many cases, one knows that this
 * must be the case when writing the code doing the insertion, e.g. when a
 * localized message is inserted into a localized page. In such cases there is
 * no need to involve the BiDi formatter at all. In the remaining cases, e.g.
 * when the string is user-entered or comes from a database, the language of
 * the string (and thus its directionality) is not known a priori, and must be
 * estimated at run-time. The BiDi formatter does this automatically.
 *
 * 3. Escaping
 * When wrapping plain text - i.e. text that is not already HTML or HTML-
 * escaped - in HTML mark-up, the text must first be HTML-escaped to prevent XSS
 * attacks and other nasty business. This of course is always true, but the
 * escaping can not be done after the string has already been wrapped in
 * mark-up, so the BiDi formatter also serves as a last chance and includes
 * escaping services.
 *
 * Thus, in a single call, the formatter will escape the input string as
 * specified, determine its directionality, and wrap it as necessary. It is
 * then up to the caller to insert the return value in the output.
 *
 * See http://wiki/Main/TemplatesAndBiDi for more information.
 *
 * @param {goog.i18n.bidi.Dir|number|boolean} contextDir The context
 *     directionality. May be supplied either as a goog.i18n.bidi.Dir constant,
 *     as a number (positive = LRT, negative = RTL, 0 = unknown) or as a boolean
 *     (true = RTL, false = LTR).
 * @param {boolean=} opt_alwaysSpan Whether {@link #spanWrap} should always
 *     use a 'span' tag, even when the input directionality is neutral or
 *     matches the context, so that the DOM structure of the output does not
 *     depend on the combination of directionalities. Default: false.
 * @constructor
 */
goog.i18n.BidiFormatter = function(contextDir, opt_alwaysSpan) {
  /**
   * The overall directionality of the context in which the formatter is being
   * used.
   * @type {goog.i18n.bidi.Dir}
   * @private
   */
  this.contextDir_ = goog.i18n.bidi.toDir(contextDir);

  /**
   * Whether {@link #spanWrap} and similar methods should always use the same
   * span structure, regardless of the combination of directionalities, for a
   * stable DOM structure.
   * @type {boolean}
   * @private
   */
  this.alwaysSpan_ = !!opt_alwaysSpan;
};


/**
 * @return {goog.i18n.bidi.Dir} The context directionality.
 */
goog.i18n.BidiFormatter.prototype.getContextDir = function() {
  return this.contextDir_;
};


/**
 * @return {boolean} Whether alwaysSpan is set.
 */
goog.i18n.BidiFormatter.prototype.getAlwaysSpan = function() {
  return this.alwaysSpan_;
};


/**
 * @param {goog.i18n.bidi.Dir|number|boolean} contextDir The context
 *     directionality. May be supplied either as a goog.i18n.bidi.Dir constant,
 *     as a number (positive = LRT, negative = RTL, 0 = unknown) or as a boolean
 *     (true = RTL, false = LTR).
 */
goog.i18n.BidiFormatter.prototype.setContextDir = function(contextDir) {
  this.contextDir_ = goog.i18n.bidi.toDir(contextDir);
};


/**
 * @param {boolean} alwaysSpan Whether {@link #spanWrap} should always use a
 *     'span' tag, even when the input directionality is neutral or matches the
 *     context, so that the DOM structure of the output does not depend on the
 *     combination of directionalities.
 */
goog.i18n.BidiFormatter.prototype.setAlwaysSpan = function(alwaysSpan) {
  this.alwaysSpan_ = alwaysSpan;
};


/**
 * Returns the directionality of input argument {@code str}.
 * Identical to {@link goog.i18n.bidi.estimateDirection}.
 *
 * @param {string} str The input text.
 * @param {boolean=} opt_isHtml Whether {@code str} is HTML / HTML-escaped.
 *     Default: false.
 * @return {goog.i18n.bidi.Dir} Estimated overall directionality of {@code str}.
 */
goog.i18n.BidiFormatter.prototype.estimateDirection =
    goog.i18n.bidi.estimateDirection;


/**
 * Returns true if two given directionalities are opposite.
 * Note: the implementation is based on the numeric values of the Dir enum.
 *
 * @param {goog.i18n.bidi.Dir} dir1 1st directionality.
 * @param {goog.i18n.bidi.Dir} dir2 2nd directionality.
 * @return {boolean} Whether the directionalities are opposite.
 * @private
 */
goog.i18n.BidiFormatter.prototype.areDirectionalitiesOpposite_ = function(dir1,
    dir2) {
  return dir1 * dir2 < 0;
};


/**
 * Returns a unicode BiDi mark matching the context directionality (LRM or
 * RLM) if {@code opt_dirReset}, and if either the directionality or the exit
 * directionality of {@code str} is opposite to the context directionality.
 * Otherwise returns the empty string.
 *
 * @param {string} str The input text.
 * @param {goog.i18n.bidi.Dir} dir {@code str}'s overall directionality.
 * @param {boolean=} opt_isHtml Whether {@code str} is HTML / HTML-escaped.
 *     Default: false.
 * @param {boolean=} opt_dirReset Whether to perform the reset. Default: false.
 * @return {string} A unicode BiDi mark or the empty string.
 * @private
 */
goog.i18n.BidiFormatter.prototype.dirResetIfNeeded_ = function(str, dir,
    opt_isHtml, opt_dirReset) {
  // endsWithRtl and endsWithLtr are called only if needed (short-circuit).
  if (opt_dirReset &&
      (this.areDirectionalitiesOpposite_(dir, this.contextDir_) ||
       (this.contextDir_ == goog.i18n.bidi.Dir.LTR &&
        goog.i18n.bidi.endsWithRtl(str, opt_isHtml)) ||
       (this.contextDir_ == goog.i18n.bidi.Dir.RTL &&
        goog.i18n.bidi.endsWithLtr(str, opt_isHtml)))) {
   return this.contextDir_ == goog.i18n.bidi.Dir.LTR ?
       goog.i18n.bidi.Format.LRM : goog.i18n.bidi.Format.RLM;
  } else {
    return '';
  }
};


/**
 * Returns "rtl" if {@code str}'s estimated directionality is RTL, and "ltr" if
 * it is LTR. In case it's UNKNOWN, returns "rtl" if the context directionality
 * is RTL, and "ltr" otherwise.
 * Needed for GXP, which can't handle dirAttr.
 * Example use case:
 * <td expr:dir='bidiFormatter.dirAttrValue(foo)'><gxp:eval expr='foo'></td>
 *
 * @param {string} str Text whose directionality is to be estimated.
 * @param {boolean=} opt_isHtml Whether {@code str} is HTML / HTML-escaped.
 *     Default: false.
 * @return {string} "rtl" or "ltr", according to the logic described above.
 */
goog.i18n.BidiFormatter.prototype.dirAttrValue = function(str, opt_isHtml) {
  return this.knownDirAttrValue(this.estimateDirection(str, opt_isHtml));
};


/**
 * Returns "rtl" if the given directionality is RTL, and "ltr" if it is LTR. In
 * case it's UNKNOWN, returns "rtl" if the context directionality is RTL, and
 * "ltr" otherwise.
 *
 * @param {goog.i18n.bidi.Dir} dir A directionality.
 * @return {string} "rtl" or "ltr", according to the logic described above.
 */
goog.i18n.BidiFormatter.prototype.knownDirAttrValue = function(dir) {
  if (dir == goog.i18n.bidi.Dir.UNKNOWN) {
    dir = this.contextDir_;
  }

  return dir == goog.i18n.bidi.Dir.RTL ? 'rtl' : 'ltr';
};


/**
 * Returns "dir=ltr" or "dir=rtl", depending on {@code str}'s estimated
 * directionality, if it is not the same as the context directionality.
 * Otherwise, returns the empty string.
 *
 * @param {string} str Text whose directionality is to be estimated.
 * @param {boolean=} opt_isHtml Whether {@code str} is HTML / HTML-escaped.
 *     Default: false.
 * @return {string} "dir=rtl" for RTL text in non-RTL context; "dir=ltr" for LTR
 *     text in non-LTR context; else, the empty string.
 */
goog.i18n.BidiFormatter.prototype.dirAttr = function(str, opt_isHtml) {
  return this.knownDirAttr(this.estimateDirection(str, opt_isHtml));
};


/**
 * Returns "dir=ltr" or "dir=rtl", depending on the given directionality, if it
 * is not the same as the context directionality. Otherwise, returns the empty
 * string.
 *
 * @param {goog.i18n.bidi.Dir} dir A directionality.
 * @return {string} "dir=rtl" for RTL text in non-RTL context; "dir=ltr" for LTR
 *     text in non-LTR context; else, the empty string.
 */
goog.i18n.BidiFormatter.prototype.knownDirAttr = function(dir) {
  if (dir != this.contextDir_) {
    return dir == goog.i18n.bidi.Dir.RTL ? 'dir=rtl' :
        dir == goog.i18n.bidi.Dir.LTR ? 'dir=ltr' : '';
  }
  return '';
};


/**
 * Formats a string of unknown directionality for use in HTML output of the
 * context directionality, so an opposite-directionality string is neither
 * garbled nor garbles what follows it.
 * The algorithm: estimates the directionality of input argument {@code str}. In
 * case its directionality doesn't match the context directionality, wraps it
 * with a 'span' tag and adds a "dir" attribute (either 'dir=rtl' or 'dir=ltr').
 * If setAlwaysSpan(true) was used, the input is always wrapped with 'span',
 * skipping just the dir attribute when it's not needed.
 *
 * If {@code opt_dirReset}, and if the overall directionality or the exit
 * directionality of {@code str} are opposite to the context directionality, a
 * trailing unicode BiDi mark matching the context directionality is appened
 * (LRM or RLM).
 *
 * If !{@code opt_isHtml}, HTML-escapes {@code str} regardless of wrapping.
 *
 * @param {string} str The input text.
 * @param {boolean=} opt_isHtml Whether {@code str} is HTML / HTML-escaped.
 *     Default: false.
 * @param {boolean=} opt_dirReset Whether to append a trailing unicode bidi mark
 *     matching the context directionality, when needed, to prevent the possible
 *     garbling of whatever may follow {@code str}. Default: true.
 * @return {string} Input text after applying the above processing.
 */
goog.i18n.BidiFormatter.prototype.spanWrap = function(str, opt_isHtml,
    opt_dirReset) {
  var dir = this.estimateDirection(str, opt_isHtml);
  return this.spanWrapWithKnownDir(dir, str, opt_isHtml, opt_dirReset);
};


/**
 * Formats a string of given directionality for use in HTML output of the
 * context directionality, so an opposite-directionality string is neither
 * garbled nor garbles what follows it.
 * The algorithm: If {@code dir} doesn't match the context directionality, wraps
 * {@code str} with a 'span' tag and adds a "dir" attribute (either 'dir=rtl' or
 * 'dir=ltr'). If setAlwaysSpan(true) was used, the input is always wrapped with
 * 'span', skipping just the dir attribute when it's not needed.
 *
 * If {@code opt_dirReset}, and if {@code dir} or the exit directionality of
 * {@code str} are opposite to the context directionality, a trailing unicode
 * BiDi mark matching the context directionality is appened (LRM or RLM).
 *
 * If !{@code opt_isHtml}, HTML-escapes {@code str} regardless of wrapping.
 *
 * @param {goog.i18n.bidi.Dir} dir {@code str}'s overall directionality.
 * @param {string} str The input text.
 * @param {boolean=} opt_isHtml Whether {@code str} is HTML / HTML-escaped.
 *     Default: false.
 * @param {boolean=} opt_dirReset Whether to append a trailing unicode bidi mark
 *     matching the context directionality, when needed, to prevent the possible
 *     garbling of whatever may follow {@code str}. Default: true.
 * @return {string} Input text after applying the above processing.
 */
goog.i18n.BidiFormatter.prototype.spanWrapWithKnownDir = function(dir, str,
    opt_isHtml, opt_dirReset) {
  opt_dirReset = opt_dirReset || (opt_dirReset == undefined);
  // Whether to add the "dir" attribute.
  var dirCondition = dir != goog.i18n.bidi.Dir.UNKNOWN && dir !=
      this.contextDir_;
  if (!opt_isHtml) {
    str = goog.string.htmlEscape(str);
  }

  var result = [];
  if (this.alwaysSpan_ || dirCondition) {  // Wrap is needed
    result.push('<span');
    if (dirCondition) {
      result.push(dir == goog.i18n.bidi.Dir.RTL ? ' dir=rtl' : ' dir=ltr');
    }
    result.push('>' + str + '</span>');
  } else {
    result.push(str);
  }

  result.push(this.dirResetIfNeeded_(str, dir, true, opt_dirReset));
  return result.join('');
};


/**
 * Formats a string of unknown directionality for use in plain-text output of
 * the context directionality, so an opposite-directionality string is neither
 * garbled nor garbles what follows it.
 * As opposed to {@link #spanWrap}, this makes use of unicode BiDi formatting
 * characters. In HTML, its *only* valid use is inside of elements that do not
 * allow mark-up, e.g. an 'option' tag.
 * The algorithm: estimates the directionality of input argument {@code str}.
 * In case it doesn't match  the context directionality, wraps it with Unicode
 * BiDi formatting characters: RLE{@code str}PDF for RTL text, and
 * LRE{@code str}PDF for LTR text.
 *
 * If {@code opt_dirReset}, and if the overall directionality or the exit
 * directionality of {@code str} are opposite to the context directionality, a
 * trailing unicode BiDi mark matching the context directionality is appended
 * (LRM or RLM).
 *
 * Does *not* do HTML-escaping regardless of the value of {@code opt_isHtml}.
 * The return value can be HTML-escaped as necessary.
 *
 * @param {string} str The input text.
 * @param {boolean=} opt_isHtml Whether {@code str} is HTML / HTML-escaped.
 *     Default: false.
 * @param {boolean=} opt_dirReset Whether to append a trailing unicode bidi mark
 *     matching the context directionality, when needed, to prevent the possible
 *     garbling of whatever may follow {@code str}. Default: true.
 * @return {string} Input text after applying the above processing.
 */
goog.i18n.BidiFormatter.prototype.unicodeWrap = function(str, opt_isHtml,
    opt_dirReset) {
  var dir = this.estimateDirection(str, opt_isHtml);
  return this.unicodeWrapWithKnownDir(dir, str, opt_isHtml, opt_dirReset);
};


/**
 * Formats a string of given directionality for use in plain-text output of the
 * context directionality, so an opposite-directionality string is neither
 * garbled nor garbles what follows it.
 * As opposed to {@link #spanWrapWithKnownDir}, makes use of unicode BiDi
 * formatting characters. In HTML, its *only* valid use is inside of elements
 * that do not allow mark-up, e.g. an 'option' tag.
 * The algorithm: If {@code dir} doesn't match the context directionality, wraps
 * {@code str} with Unicode BiDi formatting characters: RLE{@code str}PDF for
 * RTL text, and LRE{@code str}PDF for LTR text.
 *
 * If {@code opt_dirReset}, and if the overall directionality or the exit
 * directionality of {@code str} are opposite to the context directionality, a
 * trailing unicode BiDi mark matching the context directionality is appended
 * (LRM or RLM).
 *
 * Does *not* do HTML-escaping regardless of the value of {@code opt_isHtml}.
 * The return value can be HTML-escaped as necessary.
 *
 * @param {goog.i18n.bidi.Dir} dir {@code str}'s overall directionality.
 * @param {string} str The input text.
 * @param {boolean=} opt_isHtml Whether {@code str} is HTML / HTML-escaped.
 *     Default: false.
 * @param {boolean=} opt_dirReset Whether to append a trailing unicode bidi mark
 *     matching the context directionality, when needed, to prevent the possible
 *     garbling of whatever may follow {@code str}. Default: true.
 * @return {string} Input text after applying the above processing.
 */
goog.i18n.BidiFormatter.prototype.unicodeWrapWithKnownDir = function(dir, str,
    opt_isHtml, opt_dirReset) {
  opt_dirReset = opt_dirReset || (opt_dirReset == undefined);
  var result = [];
  if (dir != goog.i18n.bidi.Dir.UNKNOWN && dir != this.contextDir_) {
    result.push(dir == goog.i18n.bidi.Dir.RTL ? goog.i18n.bidi.Format.RLE :
                                                goog.i18n.bidi.Format.LRE);
    result.push(str);
    result.push(goog.i18n.bidi.Format.PDF);
  } else {
    result.push(str);
  }

  result.push(this.dirResetIfNeeded_(str, dir, opt_isHtml, opt_dirReset));
  return result.join('');
};


/**
 * Returns a Unicode BiDi mark matching the context directionality (LRM or RLM)
 * if the directionality or the exit directionality of {@code str} are opposite
 * to the context directionality. Otherwise returns the empty string.
 *
 * @param {string} str The input text.
 * @param {boolean=} opt_isHtml Whether {@code str} is HTML / HTML-escaped.
 *     Default: false.
 * @return {string} A Unicode bidi mark matching the global directionality or
 *     the empty string.
 */
goog.i18n.BidiFormatter.prototype.markAfter = function(str, opt_isHtml) {
  return this.dirResetIfNeeded_(str,
      this.estimateDirection(str, opt_isHtml), opt_isHtml, true);
};


/**
 * Returns the Unicode BiDi mark matching the context directionality (LRM for
 * LTR context directionality, RLM for RTL context directionality), or the
 * empty string for neutral / unknown context directionality.
 *
 * @return {string} LRM for LTR context directionality and RLM for RTL context
 *     directionality.
 */
goog.i18n.BidiFormatter.prototype.mark = function() {
  switch (this.contextDir_) {
  case (goog.i18n.bidi.Dir.LTR):
      return goog.i18n.bidi.Format.LRM;
  case (goog.i18n.bidi.Dir.RTL):
      return goog.i18n.bidi.Format.RLM;
  default:
      return '';
  }
};


/**
 * Returns 'right' for RTL context directionality. Otherwise (LTR or neutral /
 * unknown context directionality) returns 'left'.
 *
 * @return {string} 'right' for RTL context directionality and 'left' for other
 *     context directionality.
 */
goog.i18n.BidiFormatter.prototype.startEdge = function() {
  return this.contextDir_ == goog.i18n.bidi.Dir.RTL ?
      goog.i18n.bidi.RIGHT : goog.i18n.bidi.LEFT;
};


/**
 * Returns 'left' for RTL context directionality. Otherwise (LTR or neutral /
 * unknown context directionality) returns 'right'.
 *
 * @return {string} 'left' for RTL context directionality and 'right' for other
 *     context directionality.
 */
goog.i18n.BidiFormatter.prototype.endEdge = function() {
  return this.contextDir_ == goog.i18n.bidi.Dir.RTL ?
      goog.i18n.bidi.LEFT : goog.i18n.bidi.RIGHT;
};

//javascript/closure/i18n/timezone.js
// Copyright 2008 The Closure Library Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @fileoverview Functions to provide timezone information for use with
 * date/time format.
 * @author shanjian@google.com (Shanjian Li)
 */

goog.provide('goog.i18n.TimeZone');

goog.require('goog.array');
goog.require('goog.date.DateLike');
goog.require('goog.string');



/**
 * TimeZone class implemented a time zone resolution and name information
 * source for client applications. The time zone object is initiated from
 * a time zone information object. Application can initiate a time zone
 * statically, or it may choose to initiate from a data obtained from server.
 * Each time zone information array is small, but the whole set of data
 * is too much for client application to download. If end user is allowed to
 * change time zone setting, dynamic retrieval should be the method to use.
 * In case only time zone offset is known, there is a decent fallback
 * that only use the time zone offset to create a TimeZone object.
 * A whole set of time zone information array was available under
 * http://go/js_locale_data. It is generated based on CLDR and
 * Olson time zone data base (through pytz), and will be updated timely.
 *
 * @constructor
 */
goog.i18n.TimeZone = function() {
  /**
   * The standard time zone id.
   * @type {string}
   * @private
   */
  this.timeZoneId_;


  /**
   * The standard, non-daylight time zone offset, in minutes WEST of UTC.
   * @type {number}
   * @private
   */
  this.standardOffset_;


  /**
   * An array of strings that can have 2 or 4 elements.  The first two elements
   * are the long and short names for standard time in this time zone, and the
   * last two elements (if present) are the long and short names for daylight
   * time in this time zone.
   * @type {Array.<string>}
   * @private
   */
  this.tzNames_;


  /**
   * This array specifies the Daylight Saving Time transitions for this time
   * zone.  This is a flat array of numbers which are interpreted in pairs:
   * [time1, adjustment1, time2, adjustment2, ...] where each time is a DST
   * transition point given as a number of hours since 00:00 UTC, January 1,
   * 1970, and each adjustment is the adjustment to apply for times after the
   * DST transition, given as minutes EAST of UTC.
   * @type {Array.<number>}
   * @private
   */
  this.transitions_;
};


/**
 * The number of milliseconds in an hour.
 * @type {number}
 * @private
 */
goog.i18n.TimeZone.MILLISECONDS_PER_HOUR_ = 3600 * 1000;


/**
 * Indices into the array of time zone names.
 * @enum {number}
 */
goog.i18n.TimeZone.NameType = {
  STD_SHORT_NAME: 0,
  STD_LONG_NAME: 1,
  DLT_SHORT_NAME: 2,
  DLT_LONG_NAME: 3
};


/**
 * This factory method creates a time zone instance.  It takes either an object
 * containing complete time zone information, or a single number representing a
 * constant time zone offset.  If the latter form is used, DST functionality is
 * not available.
 *
 * @param {number|Object} timeZoneData If this parameter is a number, it should
 *     indicate minutes WEST of UTC to be used as a constant time zone offset.
 *     Otherwise, it should be an object with these four fields:
 *     <ul>
 *     <li>id: A string ID for the time zone.
 *     <li>std_offset: The standard time zone offset in minutes EAST of UTC.
 *     <li>names: An array of four names (standard short name, standard long
 *           name, daylight short name, daylight long, name)
 *     <li>transitions: An array of numbers which are interpreted in pairs:
 *           [time1, adjustment1, time2, adjustment2, ...] where each time is
 *           a DST transition point given as a number of hours since 00:00 UTC,
 *           January 1, 1970, and each adjustment is the adjustment to apply
 *           for times after the DST transition, given as minutes EAST of UTC.
 *     </ul>
 * @return {goog.i18n.TimeZone} A goog.i18n.TimeZone object for the given
 *     time zone data.
 */
goog.i18n.TimeZone.createTimeZone = function(timeZoneData) {
  if (typeof timeZoneData == 'number') {
    return goog.i18n.TimeZone.createSimpleTimeZone_(timeZoneData);
  }
  var tz = new goog.i18n.TimeZone();
  tz.timeZoneId_ = timeZoneData['id'];
  tz.standardOffset_ = -timeZoneData['std_offset'];
  tz.tzNames_ = timeZoneData['names'];
  tz.transitions_ = timeZoneData['transitions'];
  return tz;
};


/**
 * This factory method creates a time zone object with a constant offset.
 * @param {number} timeZoneOffsetInMinutes Offset in minutes WEST of UTC.
 * @return {goog.i18n.TimeZone} A time zone object with the given constant
 *     offset.  Note that the time zone ID of this object will use the POSIX
 *     convention, which has a reversed sign ("Etc/GMT+8" means UTC-8 or PST).
 * @private
 */
goog.i18n.TimeZone.createSimpleTimeZone_ = function(timeZoneOffsetInMinutes) {
  var tz = new goog.i18n.TimeZone();
  tz.standardOffset_ = timeZoneOffsetInMinutes;
  tz.timeZoneId_ =
      goog.i18n.TimeZone.composePosixTimeZoneID_(timeZoneOffsetInMinutes);
  var str = goog.i18n.TimeZone.composeUTCString_(timeZoneOffsetInMinutes);
  tz.tzNames_ = [str, str];
  tz.transitions_ = [];
  return tz;
};


/**
 * Generate a GMT-relative string for a constant time zone offset.
 * @param {number} offset The time zone offset in minutes WEST of UTC.
 * @return {string} The GMT string for this offset, which will indicate
 *     hours EAST of UTC.
 * @private
 */
goog.i18n.TimeZone.composeGMTString_ = function(offset) {
  var parts = ['GMT'];
  parts.push(offset <= 0 ? '+' : '-');
  offset = Math.abs(offset);
  parts.push(goog.string.padNumber(Math.floor(offset / 60) % 100, 2),
             ':', goog.string.padNumber(offset % 60, 2));
  return parts.join('');
};


/**
 * Generate a POSIX time zone ID for a constant time zone offset.
 * @param {number} offset The time zone offset in minutes WEST of UTC.
 * @return {string} The POSIX time zone ID for this offset, which will indicate
 *     hours WEST of UTC.
 * @private
 */
goog.i18n.TimeZone.composePosixTimeZoneID_ = function(offset) {
  if (offset == 0) {
    return 'Etc/GMT';
  }
  var parts = ['Etc/GMT', offset < 0 ? '-' : '+'];
  offset = Math.abs(offset);
  parts.push(Math.floor(offset / 60) % 100);
  offset = offset % 60;
  if (offset != 0) {
    parts.push(':', goog.string.padNumber(offset, 2));
  }
  return parts.join('');
};


/**
 * Generate a UTC-relative string for a constant time zone offset.
 * @param {number} offset The time zone offset in minutes WEST of UTC.
 * @return {string} The UTC string for this offset, which will indicate
 *     hours EAST of UTC.
 * @private
 */
goog.i18n.TimeZone.composeUTCString_ = function(offset) {
  if (offset == 0) {
    return 'UTC';
  }
  var parts = ['UTC', offset < 0 ? '+' : '-'];
  offset = Math.abs(offset);
  parts.push(Math.floor(offset / 60) % 100);
  offset = offset % 60;
  if (offset != 0) {
    parts.push(':', offset);
  }
  return parts.join('');
};


/**
 * Convert the contents of time zone object to a timeZoneData object, suitable
 * for passing to goog.i18n.TimeZone.createTimeZone.
 * @return {Object} A timeZoneData object (see the documentation for
 *     goog.i18n.TimeZone.createTimeZone).
 */
goog.i18n.TimeZone.prototype.getTimeZoneData = function() {
  return {
    'id': this.timeZoneId_,
    'std_offset': -this.standardOffset_,  // note createTimeZone flips the sign
    'names': goog.array.clone(this.tzNames_),  // avoid aliasing the array
    'transitions': goog.array.clone(this.transitions_)  // avoid aliasing
  };
};


/**
 * Return the DST adjustment to the time zone offset for a given time.
 * While Daylight Saving Time is in effect, this number is positive.
 * Otherwise, it is zero.
 * @param {goog.date.DateLike} date The time to check.
 * @return {number} The DST adjustment in minutes EAST of UTC.
 */
goog.i18n.TimeZone.prototype.getDaylightAdjustment = function(date) {
  var timeInMs = Date.UTC(date.getUTCFullYear(), date.getUTCMonth(),
                          date.getUTCDate(), date.getUTCHours(),
                          date.getUTCMinutes());
  var timeInHours = timeInMs / goog.i18n.TimeZone.MILLISECONDS_PER_HOUR_;
  var index = 0;
  while (index < this.transitions_.length &&
         timeInHours >= this.transitions_[index]) {
    index += 2;
  }
  return (index == 0) ? 0 : this.transitions_[index - 1];
};


/**
 * Return the GMT representation of this time zone object.
 * @param {goog.date.DateLike} date The date for which time to retrieve
 *     GMT string.
 * @return {string} GMT representation string.
 */
goog.i18n.TimeZone.prototype.getGMTString = function(date) {
  return goog.i18n.TimeZone.composeGMTString_(this.getOffset(date));
};


/**
 * Get the long time zone name for a given date/time.
 * @param {goog.date.DateLike} date The time for which to retrieve
 *     the long time zone name.
 * @return {string} The long time zone name.
 */
goog.i18n.TimeZone.prototype.getLongName = function(date) {
  return this.tzNames_[this.isDaylightTime(date) ?
      goog.i18n.TimeZone.NameType.DLT_LONG_NAME :
      goog.i18n.TimeZone.NameType.STD_LONG_NAME];
};


/**
 * Get the time zone offset in minutes WEST of UTC for a given date/time.
 * @param {goog.date.DateLike} date The time for which to retrieve
 *     the time zone offset.
 * @return {number} The time zone offset in minutes WEST of UTC.
 */
goog.i18n.TimeZone.prototype.getOffset = function(date) {
  return this.standardOffset_ - this.getDaylightAdjustment(date);
};


/**
 * Get the RFC representation of the time zone for a given date/time.
 * @param {goog.date.DateLike} date The time for which to retrieve the
 *     RFC time zone string.
 * @return {string} The RFC time zone string.
 */
goog.i18n.TimeZone.prototype.getRFCTimeZoneString = function(date) {
  var offset = -this.getOffset(date);
  var parts = [offset < 0 ? '-' : '+'];
  offset = Math.abs(offset);
  parts.push(goog.string.padNumber(Math.floor(offset / 60) % 100, 2),
             goog.string.padNumber(offset % 60, 2));
  return parts.join('');
};


/**
 * Get the short time zone name for given date/time.
 * @param {goog.date.DateLike} date The time for which to retrieve
 *     the short time zone name.
 * @return {string} The short time zone name.
 */
goog.i18n.TimeZone.prototype.getShortName = function(date) {
  return this.tzNames_[this.isDaylightTime(date) ?
      goog.i18n.TimeZone.NameType.DLT_SHORT_NAME :
      goog.i18n.TimeZone.NameType.STD_SHORT_NAME];
};


/**
 * Return the time zone ID for this time zone.
 * @return {string} The time zone ID.
 */
goog.i18n.TimeZone.prototype.getTimeZoneId = function() {
  return this.timeZoneId_;
};


/**
 * Check if Daylight Saving Time is in effect at a given time in this time zone.
 * @param {goog.date.DateLike} date The time to check.
 * @return {boolean} True if Daylight Saving Time is in effect.
 */
goog.i18n.TimeZone.prototype.isDaylightTime = function(date) {
  return this.getDaylightAdjustment(date) > 0;
};


//javascript/closure/i18n/datetimeformat.js
// Copyright 2006 The Closure Library Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @fileoverview Functions for dealing with date/time formatting.
 * @author shanjian@google.com (Shanjian Li)
 */


/**
 * Namespace for i18n date/time formatting functions
 */
goog.provide('goog.i18n.DateTimeFormat');
goog.provide('goog.i18n.DateTimeFormat.Format');

goog.require('goog.asserts');
goog.require('goog.date.DateLike');
goog.require('goog.i18n.DateTimeSymbols');
goog.require('goog.i18n.TimeZone');
goog.require('goog.string');


/**
 * Datetime formatting functions following the pattern specification as defined
 * in JDK, ICU and CLDR, with minor modification for typical usage in JS.
 * Pattern specification: (Refer to JDK/ICU/CLDR)
 * <pre>
 * Symbol Meaning Presentation        Example
 * ------   -------                 ------------        -------
 * G        era designator          (Text)              AD
 * y#       year                    (Number)            1996
 * Y*       year (week of year)     (Number)            1997
 * u*       extended year           (Number)            4601
 * M        month in year           (Text & Number)     July & 07
 * d        day in month            (Number)            10
 * h        hour in am/pm (1~12)    (Number)            12
 * H        hour in day (0~23)      (Number)            0
 * m        minute in hour          (Number)            30
 * s        second in minute        (Number)            55
 * S        fractional second       (Number)            978
 * E        day of week             (Text)              Tuesday
 * e*       day of week (local 1~7) (Number)            2
 * D*       day in year             (Number)            189
 * F*       day of week in month    (Number)            2 (2nd Wed in July)
 * w*       week in year            (Number)            27
 * W*       week in month           (Number)            2
 * a        am/pm marker            (Text)              PM
 * k        hour in day (1~24)      (Number)            24
 * K        hour in am/pm (0~11)    (Number)            0
 * z        time zone               (Text)              Pacific Standard Time
 * Z        time zone (RFC 822)     (Number)            -0800
 * v        time zone (generic)     (Text)              Pacific Time
 * g*       Julian day              (Number)            2451334
 * A*       milliseconds in day     (Number)            69540000
 * '        escape for text         (Delimiter)         'Date='
 * ''       single quote            (Literal)           'o''clock'
 *
 * Item marked with '*' are not supported yet.
 * Item marked with '#' works different than java
 *
 * The count of pattern letters determine the format.
 * (Text): 4 or more, use full form, <4, use short or abbreviated form if it
 * exists. (e.g., "EEEE" produces "Monday", "EEE" produces "Mon")
 *
 * (Number): the minimum number of digits. Shorter numbers are zero-padded to
 * this amount (e.g. if "m" produces "6", "mm" produces "06"). Year is handled
 * specially; that is, if the count of 'y' is 2, the Year will be truncated to
 * 2 digits. (e.g., if "yyyy" produces "1997", "yy" produces "97".) Unlike other
 * fields, fractional seconds are padded on the right with zero.
 *
 * (Text & Number): 3 or over, use text, otherwise use number. (e.g., "M"
 * produces "1", "MM" produces "01", "MMM" produces "Jan", and "MMMM" produces
 * "January".)
 *
 * Any characters in the pattern that are not in the ranges of ['a'..'z'] and
 * ['A'..'Z'] will be treated as quoted text. For instance, characters like ':',
 * '.', ' ', '#' and '@' will appear in the resulting time text even they are
 * not embraced within single quotes.
 * </pre>
 */



/**
 * Construct a DateTimeFormat object based on current locale.
 * @constructor
 * @param {string|number} pattern pattern specification or pattern type.
 */
goog.i18n.DateTimeFormat = function(pattern) {
  goog.asserts.assert(goog.isDef(pattern), 'Pattern must be defined');
  this.patternParts_ = [];
  if (typeof pattern == 'number') {
    this.applyStandardPattern_(pattern);
  } else {
    this.applyPattern_(pattern);
  }
};


/**
 * Enum to identify predefined Date/Time format pattern.
 * @enum {number}
 */
goog.i18n.DateTimeFormat.Format = {
  FULL_DATE: 0,
  LONG_DATE: 1,
  MEDIUM_DATE: 2,
  SHORT_DATE: 3,
  FULL_TIME: 4,
  LONG_TIME: 5,
  MEDIUM_TIME: 6,
  SHORT_TIME: 7,
  FULL_DATETIME: 8,
  LONG_DATETIME: 9,
  MEDIUM_DATETIME: 10,
  SHORT_DATETIME: 11
};


/**
 * regular expression pattern for parsing pattern string
 * @type {Array.<RegExp>}
 * @private
 */
goog.i18n.DateTimeFormat.TOKENS_ = [
  //quote string
  /^\'(?:[^\']|\'\')*\'/,
  // pattern chars
  /^(?:G+|y+|M+|k+|S+|E+|a+|h+|K+|H+|c+|L+|Q+|d+|m+|s+|v+|z+|Z+)/,
  // and all the other chars
  /^[^\'GyMkSEahKHcLQdmsvzZ]+/  // and all the other chars
];


/**
 * These are token types, corresponding to above token definitions.
 * @enum {number}
 * @private
 */
goog.i18n.DateTimeFormat.PartTypes_ = {
  QUOTED_STRING: 0,
  FIELD: 1,
  LITERAL: 2
};


/**
 * Apply specified pattern to this formatter object.
 * @param {string} pattern String specifying how the date should be formatted.
 * @private
 */
goog.i18n.DateTimeFormat.prototype.applyPattern_ = function(pattern) {
  // lex the pattern, once for all uses
  while (pattern) {
    for (var i = 0; i < goog.i18n.DateTimeFormat.TOKENS_.length; ++i) {
      var m = pattern.match(goog.i18n.DateTimeFormat.TOKENS_[i]);
      if (m) {
        var part = m[0];
        pattern = pattern.substring(part.length);
        if (i == goog.i18n.DateTimeFormat.PartTypes_.QUOTED_STRING) {
          if (part == "''") {
            part = "'";  // '' -> '
          } else {
            part = part.substring(1, part.length - 1); // strip quotes
            part = part.replace(/\'\'/, "'");
          }
        }
        this.patternParts_.push({ text: part, type: i });
        break;
      }
    }
  }
};


/**
 * Format the given date object according to preset pattern and current lcoale.
 * @param {goog.date.DateLike} date The Date object that is being formatted.
 * @param {goog.i18n.TimeZone=} opt_timeZone optional, if specified, time
 *    related fields will be formatted based on its setting. When this field
 *    is not specified, "undefined" will be pass around and those function
 *    that really need time zone service will create a default one.
 * @return {string} Formatted string for the given date.
 */
goog.i18n.DateTimeFormat.prototype.format = function(date, opt_timeZone) {
  // We don't want to write code to calculate each date field because we
  // want to maximize performance and minimize code size.
  // JavaScript only provide API to render local time.
  // Suppose target date is: 16:00 GMT-0400
  // OS local time is:       12:00 GMT-0800
  // We want to create a Local Date Object : 16:00 GMT-0800, and fix the
  // time zone display ourselves.
  // Thing get a little bit tricky when daylight time transition happens. For
  // example, suppose OS timeZone is America/Los_Angeles, it is impossible to
  // represent "2006/4/2 02:30" even for those timeZone that has no transition
  // at this time. Because 2:00 to 3:00 on that day does not exising in
  // America/Los_Angeles time zone. To avoid calculating date field through
  // our own code, we uses 3 Date object instead, one for "Year, month, day",
  // one for time within that day, and one for timeZone object since it need
  // the real time to figure out actual time zone offset.
  var diff = opt_timeZone ?
      (date.getTimezoneOffset() - opt_timeZone.getOffset(date)) * 60000 : 0;
  var dateForDate = diff ? new Date(date.getTime() + diff) : date;
  var dateForTime = dateForDate;
  // in daylight time switch on/off hour, diff adjustment could alter time
  // because of timeZone offset change, move 1 day forward or backward.
  if (opt_timeZone &&
      dateForDate.getTimezoneOffset() != date.getTimezoneOffset()) {
    diff += diff > 0 ? -24 * 60 * 60000 : 24 * 60 * 60000;
    dateForTime = new Date(date.getTime() + diff);
  }

  var out = [];
  for (var i = 0; i < this.patternParts_.length; ++i) {
    var text = this.patternParts_[i].text;
    if (goog.i18n.DateTimeFormat.PartTypes_.FIELD ==
        this.patternParts_[i].type) {
      out.push(this.formatField_(text, date, dateForDate, dateForTime,
                                 opt_timeZone));
    } else {
      out.push(text);
    }
  }
  return out.join('');
};


/**
 * Apply a predefined pattern as identified by formatType, which is stored in
 * locale specific repository.
 * @param {number} formatType A number that identified the predefined pattern.
 * @private
 */
goog.i18n.DateTimeFormat.prototype.applyStandardPattern_ =
    function(formatType) {
  var pattern;
  if (formatType < 4) {
    pattern = goog.i18n.DateTimeSymbols.DATEFORMATS[formatType];
  } else if (formatType < 8) {
    pattern = goog.i18n.DateTimeSymbols.TIMEFORMATS[formatType - 4];
  } else if (formatType < 12) {
    pattern = goog.i18n.DateTimeSymbols.DATEFORMATS[formatType - 8] +
              ' ' + goog.i18n.DateTimeSymbols.TIMEFORMATS[formatType - 8];
  } else {
    this.applyStandardPattern_(goog.i18n.DateTimeFormat.Format.MEDIUM_DATETIME);
    return;
  }
  this.applyPattern_(pattern);
};


/**
 * Formats Era field according to pattern specified.
 *
 * @param {number} count Number of time pattern char repeats, it controls
 *     how a field should be formatted.
 * @param {goog.date.DateLike} date It holds the date object to be formatted.
 * @return {string} Formatted string that represent this field.
 * @private
 */
goog.i18n.DateTimeFormat.prototype.formatEra_ = function(count, date) {
  var value = date.getFullYear() > 0 ? 1 : 0;
  return count >= 4 ? goog.i18n.DateTimeSymbols.ERANAMES[value] :
                      goog.i18n.DateTimeSymbols.ERAS[value];
};


/**
 * Formats Year field according to pattern specified
 *   Javascript Date object seems incapable handling 1BC and
 *   year before. It can show you year 0 which does not exists.
 *   following we just keep consistent with javascript's
 *   toString method. But keep in mind those things should be
 *   unsupported.
 * @param {number} count Number of time pattern char repeats, it controls
 *     how a field should be formatted.
 * @param {goog.date.DateLike} date It holds the date object to be formatted.
 * @return {string} Formatted string that represent this field.
 * @private
 */
goog.i18n.DateTimeFormat.prototype.formatYear_ = function(count, date) {
  var value = date.getFullYear();
  if (value < 0) {
    value = -value;
  }
  return count == 2 ?
      goog.string.padNumber(value % 100, 2) :
      String(value);
};


/**
 * Formats Month field according to pattern specified
 *
 * @param {number} count Number of time pattern char repeats, it controls
 *     how a field should be formatted.
 * @param {goog.date.DateLike} date It holds the date object to be formatted.
 * @return {string} Formatted string that represent this field.
 * @private
 */
goog.i18n.DateTimeFormat.prototype.formatMonth_ = function(count, date) {
  var value = date.getMonth();
  switch (count) {
    case 5: return goog.i18n.DateTimeSymbols.NARROWMONTHS[value];
    case 4: return goog.i18n.DateTimeSymbols.MONTHS[value];
    case 3: return goog.i18n.DateTimeSymbols.SHORTMONTHS[value];
    default:
      return goog.string.padNumber(value + 1, count);
  }
};


/**
 * Formats (1..24) Hours field according to pattern specified
 *
 * @param {number} count Number of time pattern char repeats. This controls
 *     how a field should be formatted.
 * @param {goog.date.DateLike} date It holds the date object to be formatted.
 * @return {string} Formatted string that represent this field.
 * @private
 */
goog.i18n.DateTimeFormat.prototype.format24Hours_ =
    function(count, date) {
  return goog.string.padNumber(date.getHours() || 24, count);
};


/**
 * Formats Fractional seconds field according to pattern
 * specified
 *
 * @param {number} count Number of time pattern char repeats, it controls
 *     how a field should be formatted.
 * @param {goog.date.DateLike} date It holds the date object to be formatted.
 *
 * @return {string} Formatted string that represent this field.
 * @private
 */
goog.i18n.DateTimeFormat.prototype.formatFractionalSeconds_ =
    function(count, date) {
  // Fractional seconds left-justify, append 0 for precision beyond 3
  var value = date.getTime() % 1000 / 1000;
  return value.toFixed(Math.min(3, count)).substr(2) +
         (count > 3 ? goog.string.padNumber(0, count - 3) : '');
};


/**
 * Formats Day of week field according to pattern specified
 *
 * @param {number} count Number of time pattern char repeats, it controls
 *     how a field should be formatted.
 * @param {goog.date.DateLike} date It holds the date object to be formatted.
 * @return {string} Formatted string that represent this field.
 * @private
 */
goog.i18n.DateTimeFormat.prototype.formatDayOfWeek_ =
    function(count, date) {
  var value = date.getDay();
  return count >= 4 ? goog.i18n.DateTimeSymbols.WEEKDAYS[value] :
                      goog.i18n.DateTimeSymbols.SHORTWEEKDAYS[value];
};


/**
 * Formats Am/Pm field according to pattern specified
 *
 * @param {number} count Number of time pattern char repeats, it controls
 *     how a field should be formatted.
 * @param {goog.date.DateLike} date It holds the date object to be formatted.
 * @return {string} Formatted string that represent this field.
 * @private
 */
goog.i18n.DateTimeFormat.prototype.formatAmPm_ = function(count, date) {
  var hours = date.getHours();
  return goog.i18n.DateTimeSymbols.AMPMS[hours >= 12 && hours < 24 ? 1 : 0];
};


/**
 * Formats (1..12) Hours field according to pattern specified
 *
 * @param {number} count Number of time pattern char repeats, it controls
 *     how a field should be formatted.
 * @param {goog.date.DateLike} date It holds the date object to be formatted.
 * @return {string} formatted string that represent this field.
 * @private
 */
goog.i18n.DateTimeFormat.prototype.format1To12Hours_ =
    function(count, date) {
  return goog.string.padNumber(date.getHours() % 12 || 12, count);
};


/**
 * Formats (0..11) Hours field according to pattern specified
 *
 * @param {number} count Number of time pattern char repeats, it controls
 *     how a field should be formatted.
 * @param {goog.date.DateLike} date It holds the date object to be formatted.
 * @return {string} formatted string that represent this field.
 * @private
 */
goog.i18n.DateTimeFormat.prototype.format0To11Hours_ =
    function(count, date) {
  return goog.string.padNumber(date.getHours() % 12, count);
};


/**
 * Formats (0..23) Hours field according to pattern specified
 *
 * @param {number} count Number of time pattern char repeats, it controls
 *     how a field should be formatted.
 * @param {goog.date.DateLike} date It holds the date object to be formatted.
 * @return {string} formatted string that represent this field.
 * @private
 */
goog.i18n.DateTimeFormat.prototype.format0To23Hours_ =
    function(count, date) {
  return goog.string.padNumber(date.getHours(), count);
};


/**
 * Formats Standalone weekday field according to pattern specified
 *
 * @param {number} count Number of time pattern char repeats, it controls
 *     how a field should be formatted.
 * @param {goog.date.DateLike} date It holds the date object to be formatted.
 * @return {string} formatted string that represent this field.
 * @private
 */
goog.i18n.DateTimeFormat.prototype.formatStandaloneDay_ =
    function(count, date) {
  var value = date.getDay();
  switch (count) {
    case 5:
      return goog.i18n.DateTimeSymbols.STANDALONENARROWWEEKDAYS[value];
    case 4:
      return goog.i18n.DateTimeSymbols.STANDALONEWEEKDAYS[value];
    case 3:
      return goog.i18n.DateTimeSymbols.STANDALONESHORTWEEKDAYS[value];
    default:
      return goog.string.padNumber(value, 1);
  }
};


/**
 * Formats Standalone Month field according to pattern specified
 *
 * @param {number} count Number of time pattern char repeats, it controls
 *     how a field should be formatted.
 * @param {goog.date.DateLike} date It holds the date object to be formatted.
 * @return {string} formatted string that represent this field.
 * @private
 */
goog.i18n.DateTimeFormat.prototype.formatStandaloneMonth_ =
    function(count, date) {
  var value = date.getMonth();
  switch (count) {
    case 5:
      return goog.i18n.DateTimeSymbols.STANDALONENARROWMONTHS[value];
    case 4:
      return goog.i18n.DateTimeSymbols.STANDALONEMONTHS[value];
    case 3:
      return goog.i18n.DateTimeSymbols.STANDALONESHORTMONTHS[value];
    default:
      return goog.string.padNumber(value + 1, count);
  }
};


/**
 * Formats Quarter field according to pattern specified
 *
 * @param {number} count Number of time pattern char repeats, it controls
 *     how a field should be formatted.
 * @param {goog.date.DateLike} date It holds the date object to be formatted.
 * @return {string} Formatted string that represent this field.
 * @private
 */
goog.i18n.DateTimeFormat.prototype.formatQuarter_ =
    function(count, date) {
  var value = Math.floor(date.getMonth() / 3);
  return count < 4 ? goog.i18n.DateTimeSymbols.SHORTQUARTERS[value] :
                     goog.i18n.DateTimeSymbols.QUARTERS[value];
};


/**
 * Formats Date field according to pattern specified
 *
 * @param {number} count Number of time pattern char repeats, it controls
 *     how a field should be formatted.
 * @param {goog.date.DateLike} date It holds the date object to be formatted.
 * @return {string} Formatted string that represent this field.
 * @private
 */
goog.i18n.DateTimeFormat.prototype.formatDate_ = function(count, date) {
  return goog.string.padNumber(date.getDate(), count);
};


/**
 * Formats Minutes field according to pattern specified
 *
 * @param {number} count Number of time pattern char repeats, it controls
 *     how a field should be formatted.
 * @param {goog.date.DateLike} date It holds the date object to be formatted.
 * @return {string} Formatted string that represent this field.
 * @private
 */
goog.i18n.DateTimeFormat.prototype.formatMinutes_ =
    function(count, date) {
  return goog.string.padNumber(date.getMinutes(), count);
};


/**
 * Formats Seconds field according to pattern specified
 *
 * @param {number} count Number of time pattern char repeats, it controls
 *     how a field should be formatted.
 * @param {goog.date.DateLike} date It holds the date object to be formatted.
 * @return {string} Formatted string that represent this field.
 * @private
 */
goog.i18n.DateTimeFormat.prototype.formatSeconds_ =
    function(count, date) {
  return goog.string.padNumber(date.getSeconds(), count);
};


/**
 * Formats TimeZone field following RFC
 *
 * @param {number} count Number of time pattern char repeats, it controls
 *     how a field should be formatted.
 * @param {goog.date.DateLike} date It holds the date object to be formatted.
 * @param {goog.i18n.TimeZone=} opt_timeZone This holds current time zone info.
 * @return {string} Formatted string that represent this field.
 * @private
 */
goog.i18n.DateTimeFormat.prototype.formatTimeZoneRFC_ =
    function(count, date, opt_timeZone) {
  opt_timeZone = opt_timeZone ||
      goog.i18n.TimeZone.createTimeZone(date.getTimezoneOffset());
  return count < 4 ? opt_timeZone.getRFCTimeZoneString(date) :
                     opt_timeZone.getGMTString(date);
};


/**
 * Generate GMT timeZone string for given date
 * @param {number} count Number of time pattern char repeats, it controls
 *     how a field should be formatted.
 * @param {goog.date.DateLike} date Whose value being evaluated.
 * @param {goog.i18n.TimeZone=} opt_timeZone This holds current time zone info.
 * @return {string} GMT timeZone string.
 * @private
 */
goog.i18n.DateTimeFormat.prototype.formatTimeZone_ =
    function(count, date, opt_timeZone) {
  opt_timeZone = opt_timeZone ||
      goog.i18n.TimeZone.createTimeZone(date.getTimezoneOffset());
  return count < 4 ? opt_timeZone.getShortName(date) :
             opt_timeZone.getLongName(date);
};


/**
 * Generate GMT timeZone string for given date
 * @param {goog.date.DateLike} date Whose value being evaluated.
 * @param {goog.i18n.TimeZone=} opt_timeZone This holds current time zone info.
 * @return {string} GMT timeZone string.
 * @private
 */
goog.i18n.DateTimeFormat.prototype.formatTimeZoneId_ =
    function(date, opt_timeZone) {
  opt_timeZone = opt_timeZone ||
      goog.i18n.TimeZone.createTimeZone(date.getTimezoneOffset());
  return opt_timeZone.getTimeZoneId();
};


/**
 * Formatting one date field.
 * @param {string} patternStr The pattern string for the field being formatted.
 * @param {goog.date.DateLike} date represents the real date to be formatted.
 * @param {goog.date.DateLike} dateForDate used to resolve date fields
 *     for formatting.
 * @param {goog.date.DateLike} dateForTime used to resolve time fields
 *     for formatting.
 * @param {goog.i18n.TimeZone=} opt_timeZone This holds current time zone info.
 * @return {string} string representation for the given field.
 * @private
 */
goog.i18n.DateTimeFormat.prototype.formatField_ =
    function(patternStr, date, dateForDate, dateForTime, opt_timeZone) {
  var count = patternStr.length;
  switch (patternStr.charAt(0)) {
    case 'G': return this.formatEra_(count, dateForDate);
    case 'y': return this.formatYear_(count, dateForDate);
    case 'M': return this.formatMonth_(count, dateForDate);
    case 'k': return this.format24Hours_(count, dateForTime);
    case 'S': return this.formatFractionalSeconds_(count, dateForTime);
    case 'E': return this.formatDayOfWeek_(count, dateForDate);
    case 'a': return this.formatAmPm_(count, dateForTime);
    case 'h': return this.format1To12Hours_(count, dateForTime);
    case 'K': return this.format0To11Hours_(count, dateForTime);
    case 'H': return this.format0To23Hours_(count, dateForTime);
    case 'c': return this.formatStandaloneDay_(count, dateForDate);
    case 'L': return this.formatStandaloneMonth_(count, dateForDate);
    case 'Q': return this.formatQuarter_(count, dateForDate);
    case 'd': return this.formatDate_(count, dateForDate);
    case 'm': return this.formatMinutes_(count, dateForTime);
    case 's': return this.formatSeconds_(count, dateForTime);
    case 'v': return this.formatTimeZoneId_(date, opt_timeZone);
    case 'z': return this.formatTimeZone_(count, date, opt_timeZone);
    case 'Z': return this.formatTimeZoneRFC_(count, date, opt_timeZone);
    default: return '';
  }
};


//javascript/closure/soy/soy.js
// Copyright 2011 The Closure Library Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @fileoverview Provides utility methods to render soy template.
 * @author kai@google.com (Kai Huang)
 * @author ptucker@google.com (Philip Tucker)
 * @author chrishenry@google.com (Chris Henry)
 */

goog.provide('goog.soy');

goog.require('goog.dom');
goog.require('goog.dom.NodeType');
goog.require('goog.dom.TagName');



/**
 * Renders a Soy template and then set the output string as
 * the innerHTML of an element. It is recommended to use this helper function
 * instead of directly setting innerHTML in your hand-written code, so that it
 * will be easier to audit the code for cross-site scripting vulnerabilities.
 *
 * @param {Element} element The element whose content we are rendering into.
 * @param {Function} template The Soy template defining the element's content.
 * @param {Object=} opt_templateData The data for the template.
 * @param {Object=} opt_injectedData The injected data for the template.
 */
goog.soy.renderElement = function(element, template, opt_templateData,
                                  opt_injectedData) {
  element.innerHTML = template(
      opt_templateData || goog.soy.defaultTemplateData_, undefined,
      opt_injectedData);
};


/**
 * Renders a Soy template into a single node or a document
 * fragment. If the rendered HTML string represents a single node, then that
 * node is returned (note that this is *not* a fragment, despite them name of
 * the method). Otherwise a document fragment is returned containing the
 * rendered nodes.
 *
 * @param {Function} template The Soy template defining the element's content.
 * @param {Object=} opt_templateData The data for the template.
 * @param {Object=} opt_injectedData The injected data for the template.
 * @param {goog.dom.DomHelper=} opt_domHelper The DOM helper used to
 *     create DOM nodes; defaults to {@code goog.dom.getDomHelper}.
 * @return {!Node} The resulting node or document fragment.
 */
goog.soy.renderAsFragment = function(template, opt_templateData,
                                     opt_injectedData, opt_domHelper) {
  var dom = opt_domHelper || goog.dom.getDomHelper();
  return dom.htmlToDocumentFragment(
      template(opt_templateData || goog.soy.defaultTemplateData_,
               undefined, opt_injectedData));
};


/**
 * Renders a Soy template into a single node. If the rendered
 * HTML string represents a single node, then that node is returned. Otherwise,
 * a DIV element is returned containing the rendered nodes.
 *
 * @param {Function} template The Soy template defining the element's content.
 * @param {Object=} opt_templateData The data for the template.
 * @param {Object=} opt_injectedData The injected data for the template.
 * @param {goog.dom.DomHelper=} opt_domHelper The DOM helper used to
 *     create DOM nodes; defaults to {@code goog.dom.getDomHelper}.
 * @return {!Element} Rendered template contents, wrapped in a parent DIV
 *     element if necessary.
 */
goog.soy.renderAsElement = function(template, opt_templateData,
                                    opt_injectedData, opt_domHelper) {
  var dom = opt_domHelper || goog.dom.getDomHelper();
  var wrapper = dom.createElement(goog.dom.TagName.DIV);
  wrapper.innerHTML = template(
      opt_templateData || goog.soy.defaultTemplateData_,
      undefined, opt_injectedData);

  // If the template renders as a single element, return it.
  if (wrapper.childNodes.length == 1) {
    var firstChild = wrapper.firstChild;
    if (firstChild.nodeType == goog.dom.NodeType.ELEMENT) {
      return /** @type {!Element} */ (firstChild);
    }
  }

  // Otherwise, return the wrapper DIV.
  return wrapper;
};


/**
 * Immutable object that is passed into templates that are rendered
 * without any data.
 * @type {Object}
 * @private
 */
goog.soy.defaultTemplateData_ = {};

//javascript/closure/useragent/jscript.js
// Copyright 2007 The Closure Library Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @fileoverview Detection of JScript version.
 *
 * @author arv@google.com (Erik Arvidsson)
 */


goog.provide('goog.userAgent.jscript');

goog.require('goog.string');


/**
 * @define {boolean} True if it is known at compile time that the runtime
 *     environment will not be using JScript.
 */
goog.userAgent.jscript.ASSUME_NO_JSCRIPT = false;


/**
 * Initializer for goog.userAgent.jscript.  Detects if the user agent is using
 * Microsoft JScript and which version of it.
 *
 * This is a named function so that it can be stripped via the jscompiler
 * option for stripping types.
 * @private
 */
goog.userAgent.jscript.init_ = function() {
  var hasScriptEngine = 'ScriptEngine' in goog.global;

  /**
   * @type {boolean}
   * @private
   */
  goog.userAgent.jscript.DETECTED_HAS_JSCRIPT_ =
      hasScriptEngine && goog.global['ScriptEngine']() == 'JScript';

  /**
   * @type {string}
   * @private
   */
  goog.userAgent.jscript.DETECTED_VERSION_ =
      goog.userAgent.jscript.DETECTED_HAS_JSCRIPT_ ?
      (goog.global['ScriptEngineMajorVersion']() + '.' +
       goog.global['ScriptEngineMinorVersion']() + '.' +
       goog.global['ScriptEngineBuildVersion']()) :
      '0';
};

if (!goog.userAgent.jscript.ASSUME_NO_JSCRIPT) {
  goog.userAgent.jscript.init_();
}


/**
 * Whether we detect that the user agent is using Microsoft JScript.
 * @type {boolean}
 */
goog.userAgent.jscript.HAS_JSCRIPT = goog.userAgent.jscript.ASSUME_NO_JSCRIPT ?
    false : goog.userAgent.jscript.DETECTED_HAS_JSCRIPT_;


/**
 * The installed version of JScript.
 * @type {string}
 */
goog.userAgent.jscript.VERSION = goog.userAgent.jscript.ASSUME_NO_JSCRIPT ?
    '0' : goog.userAgent.jscript.DETECTED_VERSION_;


/**
 * Whether the installed version of JScript is as new or newer than a given
 * version.
 * @param {string} version The version to check.
 * @return {boolean} Whether the installed version of JScript is as new or
 *     newer than the given version.
 */
goog.userAgent.jscript.isVersion = function(version) {
  return goog.string.compareVersions(goog.userAgent.jscript.VERSION,
                                     version) >= 0;
};

//javascript/closure/string/stringbuffer.js
// Copyright 2006 The Closure Library Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @fileoverview Utility for fast string concatenation.
 * @author timman@google.com (Tim Mansfield)
 */

goog.provide('goog.string.StringBuffer');

goog.require('goog.userAgent.jscript');



/**
 * Utility class to facilitate much faster string concatenation in IE,
 * using Array.join() rather than the '+' operator.  For other browsers
 * we simply use the '+' operator.
 *
 * @param {Object|number|string|boolean=} opt_a1 Optional first initial item
 *     to append.
 * @param {...Object|number|string|boolean} var_args Other initial items to
 *     append, e.g., new goog.string.StringBuffer('foo', 'bar').
 * @constructor
 */
goog.string.StringBuffer = function(opt_a1, var_args) {
  /**
   * Internal buffer for the string to be concatenated.
   * @type {string|Array}
   * @private
   */
  this.buffer_ = goog.userAgent.jscript.HAS_JSCRIPT ? [] : '';

  if (opt_a1 != null) {
    this.append.apply(this, arguments);
  }
};


/**
 * Sets the contents of the string buffer object, replacing what's currently
 * there.
 *
 * @param {string} s String to set.
 */
goog.string.StringBuffer.prototype.set = function(s) {
  this.clear();
  this.append(s);
};


if (goog.userAgent.jscript.HAS_JSCRIPT) {
  /**
   * Length of internal buffer (faster than calling buffer_.length).
   * Only used if buffer_ is an array.
   * @type {number}
   * @private
   */
  goog.string.StringBuffer.prototype.bufferLength_ = 0;

  /**
   * Appends one or more items to the buffer.
   *
   * Calling this with null, undefined, or empty arguments is an error.
   *
   * @param {Object|number|string|boolean} a1 Required first string.
   * @param {Object|number|string|boolean=} opt_a2 Optional second string.
   * @param {...Object|number|string|boolean} var_args Other items to append,
   *     e.g., sb.append('foo', 'bar', 'baz').
   * @return {goog.string.StringBuffer} This same StringBuffer object.
   */
  goog.string.StringBuffer.prototype.append = function(a1, opt_a2, var_args) {
    // IE version.

    if (opt_a2 == null) { // second argument is undefined (null == undefined)
      // Array assignment is 2x faster than Array push.  Also, use a1
      // directly to avoid arguments instantiation, another 2x improvement.
      this.buffer_[this.bufferLength_++] = a1;
    } else {
      this.buffer_.push.apply(/** @type {Array} */ (this.buffer_), arguments);
      this.bufferLength_ = this.buffer_.length;
    }
    return this;
  };
} else {

  /**
   * Appends one or more items to the buffer.
   *
   * Calling this with null, undefined, or empty arguments is an error.
   *
   * @param {Object|number|string|boolean} a1 Required first string.
   * @param {Object|number|string|boolean=} opt_a2 Optional second string.
   * @param {...Object|number|string|boolean} var_args Other items to append,
   *     e.g., sb.append('foo', 'bar', 'baz').
   * @return {goog.string.StringBuffer} This same StringBuffer object.
   * @suppress {duplicate}
   */
  goog.string.StringBuffer.prototype.append = function(a1, opt_a2, var_args) {
    // W3 version.

    // Use a1 directly to avoid arguments instantiation for single-arg case.
    this.buffer_ += a1;
    if (opt_a2 != null) { // second argument is undefined (null == undefined)
      for (var i = 1; i < arguments.length; i++) {
        this.buffer_ += arguments[i];
      }
    }
    return this;
  };
}


/**
 * Clears the internal buffer.
 */
goog.string.StringBuffer.prototype.clear = function() {
  if (goog.userAgent.jscript.HAS_JSCRIPT) {
     this.buffer_.length = 0;  // Reuse the array to avoid creating new object.
     this.bufferLength_ = 0;
   } else {
     this.buffer_ = '';
   }
};


/**
 * Returns the length of the current contents of the buffer.  In IE, this is
 * O(n) where n = number of appends, so to avoid quadratic behavior, do not call
 * this after every append.
 *
 * @return {number} the length of the current contents of the buffer.
 */
goog.string.StringBuffer.prototype.getLength = function() {
   return this.toString().length;
};


/**
 * Returns the concatenated string.
 *
 * @return {string} The concatenated string.
 */
goog.string.StringBuffer.prototype.toString = function() {
  if (goog.userAgent.jscript.HAS_JSCRIPT) {
    var str = this.buffer_.join('');
    // Given a string with the entire contents, simplify the StringBuffer by
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

//javascript/template/soy/soyutils_usegoog.js
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
 * Utility functions and classes for Soy.
 *
 * <p>
 * The top portion of this file contains utilities for Soy users:<ul>
 *   <li> soy.StringBuilder: Compatible with the 'stringbuilder' code style.
 *   <li> soy.renderElement: Render template and set as innerHTML of an element.
 *   <li> soy.renderAsFragment: Render template and return as HTML fragment.
 * </ul>
 *
 * <p>
 * The bottom portion of this file contains utilities that should only be called
 * by Soy-generated JS code. Please do not use these functions directly from
 * your hand-writen code. Their names all start with '$$'.
 *
 * @author Mike Samuel
 * @author Kai Huang
 * @author Aharon Lenin
 */

goog.provide('soy');
goog.provide('soy.StringBuilder');
goog.provide('soy.esc');
goog.provide('soydata');
goog.provide('soydata.SanitizedHtml');
goog.provide('soydata.SanitizedHtmlAttribute');
goog.provide('soydata.SanitizedJsStrChars');
goog.provide('soydata.SanitizedUri');

goog.require('goog.asserts');
goog.require('goog.dom.DomHelper');
goog.require('goog.format');
goog.require('goog.i18n.BidiFormatter');
goog.require('goog.i18n.bidi');
goog.require('goog.soy');
goog.require('goog.string');
goog.require('goog.string.StringBuffer');


// -----------------------------------------------------------------------------
// StringBuilder (compatible with the 'stringbuilder' code style).


/**
 * Utility class to facilitate much faster string concatenation in IE,
 * using Array.join() rather than the '+' operator.  For other browsers
 * we simply use the '+' operator.
 *
 * @param {Object} var_args Initial items to append,
 *     e.g., new soy.StringBuilder('foo', 'bar').
 * @constructor
 */
soy.StringBuilder = goog.string.StringBuffer;


// -----------------------------------------------------------------------------
// soydata: Defines typed strings, e.g. an HTML string {@code "a<b>c"} is
// semantically distinct from the plain text string {@code "a<b>c"} and smart
// templates can take that distinction into account.

/**
 * A type of textual content.
 * @enum {number}
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
 * @param {string} content
 * @constructor
 * @private
 */
soydata.SanitizedContent = function(content) {
  /**
   * The textual content.
   * @type {string}
   */
  this.content = content;
};

/** @type {soydata.SanitizedContentKind} */
soydata.SanitizedContent.prototype.contentKind;

/** @override */
soydata.SanitizedContent.prototype.toString = function() {
  return this.content;
};


/**
 * Content of type {@link soydata.SanitizedContentKind.HTML}.
 * @param {string} content A string of HTML that can safely be embedded in
 *     a PCDATA context in your app.  If you would be surprised to find that an
 *     HTML sanitizer produced {@code s} (e.g. it runs code or fetches bad URLs)
 *     and you wouldn't write a template that produces {@code s} on security or
 *     privacy grounds, then don't pass {@code s} here.
 * @constructor
 * @extends {soydata.SanitizedContent}
 */
soydata.SanitizedHtml = function(content) {
  soydata.SanitizedContent.call(this, content);
};
goog.inherits(soydata.SanitizedHtml, soydata.SanitizedContent);

/** @override */
soydata.SanitizedHtml.prototype.contentKind = soydata.SanitizedContentKind.HTML;


/**
 * Content of type {@link soydata.SanitizedContentKind.JS_STR_CHARS}.
 * @param {string} content A string of JS that when evaled, produces a
 *     value that does not depend on any sensitive data and has no side effects
 *     <b>OR</b> a string of JS that does not reference any variables or have
 *     any side effects not known statically to the app authors.
 * @constructor
 * @extends {soydata.SanitizedContent}
 */
soydata.SanitizedJsStrChars = function(content) {
  soydata.SanitizedContent.call(this, content);
};
goog.inherits(soydata.SanitizedJsStrChars, soydata.SanitizedContent);

/** @override */
soydata.SanitizedJsStrChars.prototype.contentKind =
    soydata.SanitizedContentKind.JS_STR_CHARS;


/**
 * Content of type {@link soydata.SanitizedContentKind.URI}.
 * @param {string} content A chunk of URI that the caller knows is safe to
 *     emit in a template.
 * @constructor
 * @extends {soydata.SanitizedContent}
 */
soydata.SanitizedUri = function(content) {
  soydata.SanitizedContent.call(this, content);
};
goog.inherits(soydata.SanitizedUri, soydata.SanitizedContent);

/** @override */
soydata.SanitizedUri.prototype.contentKind = soydata.SanitizedContentKind.URI;


/**
 * Content of type {@link soydata.SanitizedContentKind.HTML_ATTRIBUTE}.
 * @param {string} content An attribute name and value, such as
 *     {@code dir="ltr"}.
 * @constructor
 * @extends {soydata.SanitizedContent}
 */
soydata.SanitizedHtmlAttribute = function(content) {
  soydata.SanitizedContent.call(this, content);
};
goog.inherits(soydata.SanitizedHtmlAttribute, soydata.SanitizedContent);

/** @override */
soydata.SanitizedHtmlAttribute.prototype.contentKind =
    soydata.SanitizedContentKind.HTML_ATTRIBUTE;


// -----------------------------------------------------------------------------
// Public utilities.


/**
 * Helper function to render a Soy template and then set the output string as
 * the innerHTML of an element. It is recommended to use this helper function
 * instead of directly setting innerHTML in your hand-written code, so that it
 * will be easier to audit the code for cross-site scripting vulnerabilities.
 *
 * NOTE: New code should consider using goog.soy.renderElement instead.
 *
 * @param {Element} element The element whose content we are rendering.
 * @param {Function} template The Soy template defining the element's content.
 * @param {Object=} opt_templateData The data for the template.
 * @param {Object=} opt_injectedData The injected data for the template.
 */
soy.renderElement = goog.soy.renderElement;


/**
 * Helper function to render a Soy template into a single node or a document
 * fragment. If the rendered HTML string represents a single node, then that
 * node is returned (note that this is *not* a fragment, despite them name of
 * the method). Otherwise a document fragment is returned containing the
 * rendered nodes.
 *
 * NOTE: New code should consider using goog.soy.renderAsFragment
 * instead (note that the arguments are different).
 *
 * @param {Function} template The Soy template defining the element's content.
 * @param {Object=} opt_templateData The data for the template.
 * @param {Document=} opt_document The document used to create DOM nodes. If not
 *     specified, global document object is used.
 * @param {Object=} opt_injectedData The injected data for the template.
 * @return {!Node} The resulting node or document fragment.
 */
soy.renderAsFragment = function(
    template, opt_templateData, opt_document, opt_injectedData) {
  return goog.soy.renderAsFragment(
      template, opt_templateData, opt_injectedData,
      new goog.dom.DomHelper(opt_document));
};


/**
 * Helper function to render a Soy template into a single node. If the rendered
 * HTML string represents a single node, then that node is returned. Otherwise,
 * a DIV element is returned containing the rendered nodes.
 *
 * NOTE: New code should consider using goog.soy.renderAsElement
 * instead (note that the arguments are different).
 *
 * @param {Function} template The Soy template defining the element's content.
 * @param {Object=} opt_templateData The data for the template.
 * @param {Document=} opt_document The document used to create DOM nodes. If not
 *     specified, global document object is used.
 * @param {Object=} opt_injectedData The injected data for the template.
 * @return {!Element} Rendered template contents, wrapped in a parent DIV
 *     element if necessary.
 */
soy.renderAsElement = function(
    template, opt_templateData, opt_document, opt_injectedData) {
  return goog.soy.renderAsElement(
      template, opt_templateData, opt_injectedData,
      new goog.dom.DomHelper(opt_document));
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
  function TempCtor() {}
  TempCtor.prototype = origData;
  var newData = new TempCtor();

  // Add the additional params to the new object.
  for (var key in additionalParams) {
    newData[key] = additionalParams[key];
  }

  return newData;
};


/**
 * Gets the keys in a map as an array. There are no guarantees on the order.
 * @param {Object} map The map to get the keys of.
 * @return {Array.<string>} The array of keys in the given map.
 */
soy.$$getMapKeys = function(map) {
  var mapKeys = [];
  for (var key in map) {
    mapKeys.push(key);
  }
  return mapKeys;
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
 * @consistentIdGenerator
 */
soy.$$getDelegateId = function(delTemplateName) {
  return delTemplateName;
};


/**
 * Map from registered delegate template id/name to the priority of the
 * implementation.
 * @type {Object}
 * @private
 */
soy.$$DELEGATE_REGISTRY_PRIORITIES_ = {};

/**
 * Map from registered delegate template id/name to the implementation function.
 * @type {Object}
 * @private
 */
soy.$$DELEGATE_REGISTRY_FUNCTIONS_ = {};


/**
 * Registers a delegate implementation. If the same delegate template id/name
 * has been registered previously, then priority values are compared and only
 * the higher priority implementation is stored (if priorities are equal, an
 * error is thrown).
 *
 * @param {string} delTemplateId The delegate template id/name to register.
 * @param {number} delPriority The implementation's priority value.
 * @param {Function} delFn The implementation function.
 */
soy.$$registerDelegateFn = function(delTemplateId, delPriority, delFn) {
  var mapKey = 'key_' + delTemplateId;
  var currPriority = soy.$$DELEGATE_REGISTRY_PRIORITIES_[mapKey];
  if (currPriority === undefined || delPriority > currPriority) {
    // Registering new or higher-priority function: replace registry entry.
    soy.$$DELEGATE_REGISTRY_PRIORITIES_[mapKey] = delPriority;
    soy.$$DELEGATE_REGISTRY_FUNCTIONS_[mapKey] = delFn;
  } else if (delPriority == currPriority) {
    // Registering same-priority function: error.
    throw Error(
        'Encountered two active delegates with same priority (id/name "' +
        delTemplateId + '").');
  } else {
    // Registering lower-priority function: do nothing.
  }
};


/**
 * Retrieves the (highest-priority) implementation that has been registered for
 * a given delegate template id/name. If no implementation has been registered
 * for the id/name, then returns an implementation that is equivalent to an
 * empty template (i.e. rendered output would be empty string).
 *
 * @param {string} delTemplateId The delegate template id/name to get.
 * @return {Function} The retrieved implementation function.
 */
soy.$$getDelegateFn = function(delTemplateId) {
  var delFn = soy.$$DELEGATE_REGISTRY_FUNCTIONS_['key_' + delTemplateId];
  return delFn ? delFn : soy.$$EMPTY_TEMPLATE_FN_;
};


/**
 * Private helper soy.$$getDelegateFn(). This is the empty template function
 * that is returned whenever there's no delegate implementation found.
 *
 * @param {Object.<string, *>=} opt_data
 * @param {soy.StringBuilder=} opt_sb
 * @param {Object.<string, *>=} opt_ijData
 * @return {string}
 * @private
 */
soy.$$EMPTY_TEMPLATE_FN_ = function(opt_data, opt_sb, opt_ijData) {
  return '';
};


// -----------------------------------------------------------------------------
// Escape/filter/normalize.


/**
 * Escapes HTML special characters in a string.  Escapes double quote '"' in
 * addition to '&', '<', and '>' so that a string can be included in an HTML
 * tag attribute value within double quotes.
 * Will emit known safe HTML as-is.
 *
 * @param {*} value The string-like value to be escaped.  May not be a string,
 *     but the value will be coerced to a string.
 * @return {string} An escaped version of value.
 */
soy.$$escapeHtml = function(value) {
  if (typeof value === 'object' && value &&
      value.contentKind === soydata.SanitizedContentKind.HTML) {
    return value.content;
  }
  return soy.esc.$$escapeHtmlHelper(value);
};


/**
 * Escapes HTML special characters in a string so that it can be embedded in
 * RCDATA.
 * <p>
 * Escapes HTML special characters so that the value will not prematurely end
 * the body of a tag like {@code <textarea>} or {@code <title>}.  RCDATA tags
 * cannot contain other HTML entities, so it is not strictly necessary to escape
 * HTML special characters except when part of that text looks like an HTML
 * entity or like a close tag : {@code </textarea>}.
 * <p>
 * Will normalize known safe HTML to make sure that sanitized HTML (which could
 * contain an innocuous {@code </textarea>} don't prematurely end an RCDATA
 * element.
 *
 * @param {*} value The string-like value to be escaped.  May not be a string,
 *     but the value will be coerced to a string.
 * @return {string} An escaped version of value.
 */
soy.$$escapeHtmlRcdata = function(value) {
  if (typeof value === 'object' && value &&
      value.contentKind === soydata.SanitizedContentKind.HTML) {
    return soy.esc.$$normalizeHtmlHelper(value.content);
  }
  return soy.esc.$$escapeHtmlHelper(value);
};


/**
 * Removes HTML tags from a string of known safe HTML so it can be used as an
 * attribute value.
 *
 * @param {*} value The HTML to be escaped.  May not be a string, but the
 *     value will be coerced to a string.
 * @return {string} A representation of value without tags, HTML comments, or
 *     other content.
 */
soy.$$stripHtmlTags = function(value) {
  return String(value).replace(soy.esc.$$HTML_TAG_REGEX_, '');
};


/**
 * Escapes HTML special characters in an HTML attribute value.
 *
 * @param {*} value The HTML to be escaped.  May not be a string, but the
 *     value will be coerced to a string.
 * @return {string} An escaped version of value.
 */
soy.$$escapeHtmlAttribute = function(value) {
  if (typeof value === 'object' && value &&
      value.contentKind === soydata.SanitizedContentKind.HTML) {
    return soy.esc.$$normalizeHtmlHelper(soy.$$stripHtmlTags(value.content));
  }
  return soy.esc.$$escapeHtmlHelper(value);
};


/**
 * Escapes HTML special characters in a string including space and other
 * characters that can end an unquoted HTML attribute value.
 *
 * @param {*} value The HTML to be escaped.  May not be a string, but the
 *     value will be coerced to a string.
 * @return {string} An escaped version of value.
 */
soy.$$escapeHtmlAttributeNospace = function(value) {
  if (typeof value === 'object' && value &&
      value.contentKind === soydata.SanitizedContentKind.HTML) {
    return soy.esc.$$normalizeHtmlNospaceHelper(
        soy.$$stripHtmlTags(value.content));
  }
  return soy.esc.$$escapeHtmlNospaceHelper(value);
};


/**
 * Filters out strings that cannot be a substring of a valid HTML attribute.
 *
 * @param {*} value The value to escape.  May not be a string, but the value
 *     will be coerced to a string.
 * @return {string} A valid HTML attribute name part or name/value pair.
 *     {@code "zSoyz"} if the input is invalid.
 */
soy.$$filterHtmlAttribute = function(value) {
  if (typeof value === 'object' && value &&
      value.contentKind === soydata.SanitizedContentKind.HTML_ATTRIBUTE) {
    return value.content.replace(/=([^"']*)$/, '="$1"');
  }
  return soy.esc.$$filterHtmlAttributeHelper(value);
};


/**
 * Filters out strings that cannot be a substring of a valid HTML element name.
 *
 * @param {*} value The value to escape.  May not be a string, but the value
 *     will be coerced to a string.
 * @return {string} A valid HTML element name part.
 *     {@code "zSoyz"} if the input is invalid.
 */
soy.$$filterHtmlElementName = function(value) {
  return soy.esc.$$filterHtmlElementNameHelper(value);
};


/**
 * Escapes characters in the value to make it valid content for a JS string
 * literal.
 *
 * @param {*} value The value to escape.  May not be a string, but the value
 *     will be coerced to a string.
 * @return {string} An escaped version of value.
 * @deprecated
 */
soy.$$escapeJs = function(value) {
  return soy.$$escapeJsString(value);
};


/**
 * Escapes characters in the value to make it valid content for a JS string
 * literal.
 *
 * @param {*} value The value to escape.  May not be a string, but the value
 *     will be coerced to a string.
 * @return {string} An escaped version of value.
 */
soy.$$escapeJsString = function(value) {
  if (typeof value === 'object' &&
      value.contentKind === soydata.SanitizedContentKind.JS_STR_CHARS) {
    return value.content;
  }
  return soy.esc.$$escapeJsStringHelper(value);
};


/**
 * Encodes a value as a JavaScript literal.
 *
 * @param {*} value The value to escape.  May not be a string, but the value
 *     will be coerced to a string.
 * @return {string} A JavaScript code representation of the input.
 */
soy.$$escapeJsValue = function(value) {
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
  switch (typeof value) {
    case 'boolean': case 'number':
      return ' ' + value + ' ';
    default:
      return "'" + soy.esc.$$escapeJsStringHelper(String(value)) + "'";
  }
};


/**
 * Escapes characters in the string to make it valid content for a JS regular
 * expression literal.
 *
 * @param {*} value The value to escape.  May not be a string, but the value
 *     will be coerced to a string.
 * @return {string} An escaped version of value.
 */
soy.$$escapeJsRegex = function(value) {
  return soy.esc.$$escapeJsRegexHelper(value);
};


/**
 * Matches all URI mark characters that conflict with HTML attribute delimiters
 * or that cannot appear in a CSS uri.
 * From <a href="http://www.w3.org/TR/CSS2/grammar.html">G.2: CSS grammar</a>
 * <pre>
 *     url        ([!#$%&*-~]|{nonascii}|{escape})*
 * </pre>
 *
 * @type {RegExp}
 * @private
 */
soy.$$problematicUriMarks_ = /['()]/g;

/**
 * @param {string} ch A single character in {@link soy.$$problematicUriMarks_}.
 * @return {string}
 * @private
 */
soy.$$pctEncode_ = function(ch) {
  return '%' + ch.charCodeAt(0).toString(16);
};

/**
 * Escapes a string so that it can be safely included in a URI.
 *
 * @param {*} value The value to escape.  May not be a string, but the value
 *     will be coerced to a string.
 * @return {string} An escaped version of value.
 */
soy.$$escapeUri = function(value) {
  if (typeof value === 'object' &&
      value.contentKind === soydata.SanitizedContentKind.URI) {
    return soy.$$normalizeUri(value);
  }
  // Apostophes and parentheses are not matched by encodeURIComponent.
  // They are technically special in URIs, but only appear in the obsolete mark
  // production in Appendix D.2 of RFC 3986, so can be encoded without changing
  // semantics.
  var encoded = soy.esc.$$escapeUriHelper(value);
  soy.$$problematicUriMarks_.lastIndex = 0;
  if (soy.$$problematicUriMarks_.test(encoded)) {
    return encoded.replace(soy.$$problematicUriMarks_, soy.$$pctEncode_);
  }
  return encoded;
};


/**
 * Removes rough edges from a URI by escaping any raw HTML/JS string delimiters.
 *
 * @param {*} value The value to escape.  May not be a string, but the value
 *     will be coerced to a string.
 * @return {string} An escaped version of value.
 */
soy.$$normalizeUri = function(value) {
  return soy.esc.$$normalizeUriHelper(value);
};


/**
 * Vets a URI's protocol and removes rough edges from a URI by escaping
 * any raw HTML/JS string delimiters.
 *
 * @param {*} value The value to escape.  May not be a string, but the value
 *     will be coerced to a string.
 * @return {string} An escaped version of value.
 */
soy.$$filterNormalizeUri = function(value) {
  return soy.esc.$$filterNormalizeUriHelper(value);
};


/**
 * Escapes a string so it can safely be included inside a quoted CSS string.
 *
 * @param {*} value The value to escape.  May not be a string, but the value
 *     will be coerced to a string.
 * @return {string} An escaped version of value.
 */
soy.$$escapeCssString = function(value) {
  return soy.esc.$$escapeCssStringHelper(value);
};


/**
 * Encodes a value as a CSS identifier part, keyword, or quantity.
 *
 * @param {*} value The value to escape.  May not be a string, but the value
 *     will be coerced to a string.
 * @return {string} A safe CSS identifier part, keyword, or quanitity.
 */
soy.$$filterCssValue = function(value) {
  // Uses == to intentionally match null and undefined for Java compatibility.
  if (value == null) {
    return '';
  }
  return soy.esc.$$filterCssValueHelper(value);
};


// -----------------------------------------------------------------------------
// Basic directives/functions.


/**
 * Converts \r\n, \r, and \n to <br>s
 * @param {*} str The string in which to convert newlines.
 * @return {string} A copy of {@code str} with converted newlines.
 */
soy.$$changeNewlineToBr = function(str) {
  return goog.string.newLineToBr(String(str), false);
};


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
  return goog.format.insertWordBreaks(String(str), maxCharsBetweenWordBreaks);
};


/**
 * Truncates a string to a given max length (if it's currently longer),
 * optionally adding ellipsis at the end.
 *
 * @param {*} str The string to truncate. Can be other types, but the value will
 *     be coerced to a string.
 * @param {number} maxLen The maximum length of the string after truncation
 *     (including ellipsis, if applicable).
 * @param {boolean} doAddEllipsis Whether to add ellipsis if the string needs
 *     truncation.
 * @return {string} The string after truncation.
 */
soy.$$truncate = function(str, maxLen, doAddEllipsis) {

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
  if (soy.$$isHighSurrogate_(str.charAt(maxLen - 1)) &&
      soy.$$isLowSurrogate_(str.charAt(maxLen))) {
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
 * @param {string} ch The char to check.
 * @return {boolean} Whether the given char is a unicode high surrogate.
 * @private
 */
soy.$$isHighSurrogate_ = function(ch) {
  return 0xD800 <= ch && ch <= 0xDBFF;
};

/**
 * Private helper for $$truncate() to check whether a char is a low surrogate.
 * @param {string} ch The char to check.
 * @return {boolean} Whether the given char is a unicode low surrogate.
 * @private
 */
soy.$$isLowSurrogate_ = function(ch) {
  return 0xDC00 <= ch && ch <= 0xDFFF;
};


// -----------------------------------------------------------------------------
// Bidi directives/functions.


/**
 * Cache of bidi formatter by context directionality, so we don't keep on
 * creating new objects.
 * @type {!Object.<!goog.i18n.BidiFormatter>}
 * @private
 */
soy.$$bidiFormatterCache_ = {};


/**
 * Returns cached bidi formatter for bidiGlobalDir, or creates a new one.
 * @param {number} bidiGlobalDir The global directionality context: 1 if ltr, -1
 *     if rtl, 0 if unknown.
 * @return {goog.i18n.BidiFormatter} A formatter for bidiGlobalDir.
 * @private
 */
soy.$$getBidiFormatterInstance_ = function(bidiGlobalDir) {
  return soy.$$bidiFormatterCache_[bidiGlobalDir] ||
         (soy.$$bidiFormatterCache_[bidiGlobalDir] =
             new goog.i18n.BidiFormatter(bidiGlobalDir));
};


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
  if (!text) {
    return 0;
  }
  return goog.i18n.bidi.detectRtlDirectionality(text, opt_isHtml) ? -1 : 1;
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
 * @return {soydata.SanitizedHtmlAttribute} "dir=rtl" for RTL text in non-RTL
 *     context; "dir=ltr" for LTR text in non-LTR context;
 *     else, the empty string.
 */
soy.$$bidiDirAttr = function(bidiGlobalDir, text, opt_isHtml) {
  return new soydata.SanitizedHtmlAttribute(
      soy.$$getBidiFormatterInstance_(bidiGlobalDir).dirAttr(text, opt_isHtml));
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
 * @return {string} A Unicode bidi mark matching bidiGlobalDir, or the empty
 *     string when text's overall and exit directionalities both match
 *     bidiGlobalDir, or bidiGlobalDir is 0 (unknown).
 */
soy.$$bidiMarkAfter = function(bidiGlobalDir, text, opt_isHtml) {
  var formatter = soy.$$getBidiFormatterInstance_(bidiGlobalDir);
  return formatter.markAfter(text, opt_isHtml);
};


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
  var formatter = soy.$$getBidiFormatterInstance_(bidiGlobalDir);
  return formatter.spanWrap(str + '', true);
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
  var formatter = soy.$$getBidiFormatterInstance_(bidiGlobalDir);
  return formatter.unicodeWrap(str + '', true);
};


// -----------------------------------------------------------------------------
// Generated code.


// START GENERATED CODE FOR ESCAPERS.

/**
 * @type {function (*) : string}
 */
soy.esc.$$escapeUriHelper = function(v) {
  return goog.string.urlEncode(String(v));
};

/**
 * Maps charcters to the escaped versions for the named escape directives.
 * @type {Object.<string, string>}
 * @private
 */
soy.esc.$$ESCAPE_MAP_FOR_ESCAPE_HTML__AND__NORMALIZE_HTML__AND__ESCAPE_HTML_NOSPACE__AND__NORMALIZE_HTML_NOSPACE_ = {
  '\x00': '\x26#0;',
  '\x22': '\x26quot;',
  '\x26': '\x26amp;',
  '\x27': '\x26#39;',
  '\x3c': '\x26lt;',
  '\x3e': '\x26gt;',
  '\x09': '\x26#9;',
  '\x0a': '\x26#10;',
  '\x0b': '\x26#11;',
  '\x0c': '\x26#12;',
  '\x0d': '\x26#13;',
  ' ': '\x26#32;',
  '-': '\x26#45;',
  '\/': '\x26#47;',
  '\x3d': '\x26#61;',
  '`': '\x26#96;',
  '\x85': '\x26#133;',
  '\xa0': '\x26#160;',
  '\u2028': '\x26#8232;',
  '\u2029': '\x26#8233;'
};

/**
 * A function that can be used with String.replace..
 * @param {string} ch A single character matched by a compatible matcher.
 * @return {string} A token in the output language.
 * @private
 */
soy.esc.$$REPLACER_FOR_ESCAPE_HTML__AND__NORMALIZE_HTML__AND__ESCAPE_HTML_NOSPACE__AND__NORMALIZE_HTML_NOSPACE_ = function(ch) {
  return soy.esc.$$ESCAPE_MAP_FOR_ESCAPE_HTML__AND__NORMALIZE_HTML__AND__ESCAPE_HTML_NOSPACE__AND__NORMALIZE_HTML_NOSPACE_[ch];
};

/**
 * Maps charcters to the escaped versions for the named escape directives.
 * @type {Object.<string, string>}
 * @private
 */
soy.esc.$$ESCAPE_MAP_FOR_ESCAPE_JS_STRING__AND__ESCAPE_JS_REGEX_ = {
  '\x00': '\\x00',
  '\x08': '\\x08',
  '\x09': '\\t',
  '\x0a': '\\n',
  '\x0b': '\\x0b',
  '\x0c': '\\f',
  '\x0d': '\\r',
  '\x22': '\\x22',
  '\x26': '\\x26',
  '\x27': '\\x27',
  '\/': '\\\/',
  '\x3c': '\\x3c',
  '\x3d': '\\x3d',
  '\x3e': '\\x3e',
  '\\': '\\\\',
  '\x85': '\\x85',
  '\u2028': '\\u2028',
  '\u2029': '\\u2029',
  '$': '\\x24',
  '(': '\\x28',
  ')': '\\x29',
  '*': '\\x2a',
  '+': '\\x2b',
  ',': '\\x2c',
  '-': '\\x2d',
  '.': '\\x2e',
  ':': '\\x3a',
  '?': '\\x3f',
  '[': '\\x5b',
  ']': '\\x5d',
  '^': '\\x5e',
  '{': '\\x7b',
  '|': '\\x7c',
  '}': '\\x7d'
};

/**
 * A function that can be used with String.replace..
 * @param {string} ch A single character matched by a compatible matcher.
 * @return {string} A token in the output language.
 * @private
 */
soy.esc.$$REPLACER_FOR_ESCAPE_JS_STRING__AND__ESCAPE_JS_REGEX_ = function(ch) {
  return soy.esc.$$ESCAPE_MAP_FOR_ESCAPE_JS_STRING__AND__ESCAPE_JS_REGEX_[ch];
};

/**
 * Maps charcters to the escaped versions for the named escape directives.
 * @type {Object.<string, string>}
 * @private
 */
soy.esc.$$ESCAPE_MAP_FOR_ESCAPE_CSS_STRING_ = {
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
  '{': '\\7b ',
  '}': '\\7d ',
  '\x85': '\\85 ',
  '\xa0': '\\a0 ',
  '\u2028': '\\2028 ',
  '\u2029': '\\2029 '
};

/**
 * A function that can be used with String.replace..
 * @param {string} ch A single character matched by a compatible matcher.
 * @return {string} A token in the output language.
 * @private
 */
soy.esc.$$REPLACER_FOR_ESCAPE_CSS_STRING_ = function(ch) {
  return soy.esc.$$ESCAPE_MAP_FOR_ESCAPE_CSS_STRING_[ch];
};

/**
 * Maps charcters to the escaped versions for the named escape directives.
 * @type {Object.<string, string>}
 * @private
 */
soy.esc.$$ESCAPE_MAP_FOR_NORMALIZE_URI__AND__FILTER_NORMALIZE_URI_ = {
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
  '{': '%7B',
  '}': '%7D',
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
  '\uff3d': '%EF%BC%BD'
};

/**
 * A function that can be used with String.replace..
 * @param {string} ch A single character matched by a compatible matcher.
 * @return {string} A token in the output language.
 * @private
 */
soy.esc.$$REPLACER_FOR_NORMALIZE_URI__AND__FILTER_NORMALIZE_URI_ = function(ch) {
  return soy.esc.$$ESCAPE_MAP_FOR_NORMALIZE_URI__AND__FILTER_NORMALIZE_URI_[ch];
};

/**
 * Matches characters that need to be escaped for the named directives.
 * @type RegExp
 * @private
 */
soy.esc.$$MATCHER_FOR_ESCAPE_HTML_ = /[\x00\x22\x26\x27\x3c\x3e]/g;

/**
 * Matches characters that need to be escaped for the named directives.
 * @type RegExp
 * @private
 */
soy.esc.$$MATCHER_FOR_NORMALIZE_HTML_ = /[\x00\x22\x27\x3c\x3e]/g;

/**
 * Matches characters that need to be escaped for the named directives.
 * @type RegExp
 * @private
 */
soy.esc.$$MATCHER_FOR_ESCAPE_HTML_NOSPACE_ = /[\x00\x09-\x0d \x22\x26\x27\x2d\/\x3c-\x3e`\x85\xa0\u2028\u2029]/g;

/**
 * Matches characters that need to be escaped for the named directives.
 * @type RegExp
 * @private
 */
soy.esc.$$MATCHER_FOR_NORMALIZE_HTML_NOSPACE_ = /[\x00\x09-\x0d \x22\x27\x2d\/\x3c-\x3e`\x85\xa0\u2028\u2029]/g;

/**
 * Matches characters that need to be escaped for the named directives.
 * @type RegExp
 * @private
 */
soy.esc.$$MATCHER_FOR_ESCAPE_JS_STRING_ = /[\x00\x08-\x0d\x22\x26\x27\/\x3c-\x3e\\\x85\u2028\u2029]/g;

/**
 * Matches characters that need to be escaped for the named directives.
 * @type RegExp
 * @private
 */
soy.esc.$$MATCHER_FOR_ESCAPE_JS_REGEX_ = /[\x00\x08-\x0d\x22\x24\x26-\/\x3a\x3c-\x3f\x5b-\x5e\x7b-\x7d\x85\u2028\u2029]/g;

/**
 * Matches characters that need to be escaped for the named directives.
 * @type RegExp
 * @private
 */
soy.esc.$$MATCHER_FOR_ESCAPE_CSS_STRING_ = /[\x00\x08-\x0d\x22\x26-\x2a\/\x3a-\x3e@\\\x7b\x7d\x85\xa0\u2028\u2029]/g;

/**
 * Matches characters that need to be escaped for the named directives.
 * @type RegExp
 * @private
 */
soy.esc.$$MATCHER_FOR_NORMALIZE_URI__AND__FILTER_NORMALIZE_URI_ = /[\x00- \x22\x27-\x29\x3c\x3e\\\x7b\x7d\x7f\x85\xa0\u2028\u2029\uff01\uff03\uff04\uff06-\uff0c\uff0f\uff1a\uff1b\uff1d\uff1f\uff20\uff3b\uff3d]/g;

/**
 * A pattern that vets values produced by the named directives.
 * @type RegExp
 * @private
 */
soy.esc.$$FILTER_FOR_FILTER_CSS_VALUE_ = /^(?!-*(?:expression|(?:moz-)?binding))(?:[.#]?-?(?:[_a-z0-9-]+)(?:-[_a-z0-9-]+)*-?|-?(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)(?:[a-z]{1,2}|%)?|!important|)$/i;

/**
 * A pattern that vets values produced by the named directives.
 * @type RegExp
 * @private
 */
soy.esc.$$FILTER_FOR_FILTER_NORMALIZE_URI_ = /^(?:(?:https?|mailto):|[^&:\/?#]*(?:[\/?#]|$))/i;

/**
 * A pattern that vets values produced by the named directives.
 * @type RegExp
 * @private
 */
soy.esc.$$FILTER_FOR_FILTER_HTML_ATTRIBUTE_ = /^(?!style|on|action|archive|background|cite|classid|codebase|data|dsync|href|longdesc|src|usemap)(?:[a-z0-9_$:-]*)$/i;

/**
 * A pattern that vets values produced by the named directives.
 * @type RegExp
 * @private
 */
soy.esc.$$FILTER_FOR_FILTER_HTML_ELEMENT_NAME_ = /^(?!script|style|title|textarea|xmp|no)[a-z0-9_$:-]*$/i;

/**
 * A helper for the Soy directive |escapeHtml
 * @param {*} value Can be of any type but will be coerced to a string.
 * @return {string} The escaped text.
 */
soy.esc.$$escapeHtmlHelper = function(value) {
  var str = String(value);
  return str.replace(
      soy.esc.$$MATCHER_FOR_ESCAPE_HTML_,
      soy.esc.$$REPLACER_FOR_ESCAPE_HTML__AND__NORMALIZE_HTML__AND__ESCAPE_HTML_NOSPACE__AND__NORMALIZE_HTML_NOSPACE_);
};

/**
 * A helper for the Soy directive |normalizeHtml
 * @param {*} value Can be of any type but will be coerced to a string.
 * @return {string} The escaped text.
 */
soy.esc.$$normalizeHtmlHelper = function(value) {
  var str = String(value);
  return str.replace(
      soy.esc.$$MATCHER_FOR_NORMALIZE_HTML_,
      soy.esc.$$REPLACER_FOR_ESCAPE_HTML__AND__NORMALIZE_HTML__AND__ESCAPE_HTML_NOSPACE__AND__NORMALIZE_HTML_NOSPACE_);
};

/**
 * A helper for the Soy directive |escapeHtmlNospace
 * @param {*} value Can be of any type but will be coerced to a string.
 * @return {string} The escaped text.
 */
soy.esc.$$escapeHtmlNospaceHelper = function(value) {
  var str = String(value);
  return str.replace(
      soy.esc.$$MATCHER_FOR_ESCAPE_HTML_NOSPACE_,
      soy.esc.$$REPLACER_FOR_ESCAPE_HTML__AND__NORMALIZE_HTML__AND__ESCAPE_HTML_NOSPACE__AND__NORMALIZE_HTML_NOSPACE_);
};

/**
 * A helper for the Soy directive |normalizeHtmlNospace
 * @param {*} value Can be of any type but will be coerced to a string.
 * @return {string} The escaped text.
 */
soy.esc.$$normalizeHtmlNospaceHelper = function(value) {
  var str = String(value);
  return str.replace(
      soy.esc.$$MATCHER_FOR_NORMALIZE_HTML_NOSPACE_,
      soy.esc.$$REPLACER_FOR_ESCAPE_HTML__AND__NORMALIZE_HTML__AND__ESCAPE_HTML_NOSPACE__AND__NORMALIZE_HTML_NOSPACE_);
};

/**
 * A helper for the Soy directive |escapeJsString
 * @param {*} value Can be of any type but will be coerced to a string.
 * @return {string} The escaped text.
 */
soy.esc.$$escapeJsStringHelper = function(value) {
  var str = String(value);
  return str.replace(
      soy.esc.$$MATCHER_FOR_ESCAPE_JS_STRING_,
      soy.esc.$$REPLACER_FOR_ESCAPE_JS_STRING__AND__ESCAPE_JS_REGEX_);
};

/**
 * A helper for the Soy directive |escapeJsRegex
 * @param {*} value Can be of any type but will be coerced to a string.
 * @return {string} The escaped text.
 */
soy.esc.$$escapeJsRegexHelper = function(value) {
  var str = String(value);
  return str.replace(
      soy.esc.$$MATCHER_FOR_ESCAPE_JS_REGEX_,
      soy.esc.$$REPLACER_FOR_ESCAPE_JS_STRING__AND__ESCAPE_JS_REGEX_);
};

/**
 * A helper for the Soy directive |escapeCssString
 * @param {*} value Can be of any type but will be coerced to a string.
 * @return {string} The escaped text.
 */
soy.esc.$$escapeCssStringHelper = function(value) {
  var str = String(value);
  return str.replace(
      soy.esc.$$MATCHER_FOR_ESCAPE_CSS_STRING_,
      soy.esc.$$REPLACER_FOR_ESCAPE_CSS_STRING_);
};

/**
 * A helper for the Soy directive |filterCssValue
 * @param {*} value Can be of any type but will be coerced to a string.
 * @return {string} The escaped text.
 */
soy.esc.$$filterCssValueHelper = function(value) {
  var str = String(value);
  if (!soy.esc.$$FILTER_FOR_FILTER_CSS_VALUE_.test(str)) {
    goog.asserts.fail('Bad value `%s` for |filterCssValue', [str]);
    return 'zSoyz';
  }
  return str;
};

/**
 * A helper for the Soy directive |normalizeUri
 * @param {*} value Can be of any type but will be coerced to a string.
 * @return {string} The escaped text.
 */
soy.esc.$$normalizeUriHelper = function(value) {
  var str = String(value);
  return str.replace(
      soy.esc.$$MATCHER_FOR_NORMALIZE_URI__AND__FILTER_NORMALIZE_URI_,
      soy.esc.$$REPLACER_FOR_NORMALIZE_URI__AND__FILTER_NORMALIZE_URI_);
};

/**
 * A helper for the Soy directive |filterNormalizeUri
 * @param {*} value Can be of any type but will be coerced to a string.
 * @return {string} The escaped text.
 */
soy.esc.$$filterNormalizeUriHelper = function(value) {
  var str = String(value);
  if (!soy.esc.$$FILTER_FOR_FILTER_NORMALIZE_URI_.test(str)) {
    goog.asserts.fail('Bad value `%s` for |filterNormalizeUri', [str]);
    return 'zSoyz';
  }
  return str.replace(
      soy.esc.$$MATCHER_FOR_NORMALIZE_URI__AND__FILTER_NORMALIZE_URI_,
      soy.esc.$$REPLACER_FOR_NORMALIZE_URI__AND__FILTER_NORMALIZE_URI_);
};

/**
 * A helper for the Soy directive |filterHtmlAttribute
 * @param {*} value Can be of any type but will be coerced to a string.
 * @return {string} The escaped text.
 */
soy.esc.$$filterHtmlAttributeHelper = function(value) {
  var str = String(value);
  if (!soy.esc.$$FILTER_FOR_FILTER_HTML_ATTRIBUTE_.test(str)) {
    goog.asserts.fail('Bad value `%s` for |filterHtmlAttribute', [str]);
    return 'zSoyz';
  }
  return str;
};

/**
 * A helper for the Soy directive |filterHtmlElementName
 * @param {*} value Can be of any type but will be coerced to a string.
 * @return {string} The escaped text.
 */
soy.esc.$$filterHtmlElementNameHelper = function(value) {
  var str = String(value);
  if (!soy.esc.$$FILTER_FOR_FILTER_HTML_ELEMENT_NAME_.test(str)) {
    goog.asserts.fail('Bad value `%s` for |filterHtmlElementName', [str]);
    return 'zSoyz';
  }
  return str;
};

/**
 * Matches all tags, HTML comments, and DOCTYPEs in tag soup HTML.
 *
 * @type {RegExp}
 * @private
 */
soy.esc.$$HTML_TAG_REGEX_ = /<(?:!|\/?[a-zA-Z])(?:[^>'"]|"[^"]*"|'[^']*')*>/g;

// END GENERATED CODE

