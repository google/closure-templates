/*
 * Copyright 2012 Google Inc.
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

package com.google.template.soy.i18ndirectives;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.NumberData;
import com.google.template.soy.plugin.java.restricted.JavaPluginContext;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptPluginContext;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptValue;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptValueFactory;
import com.google.template.soy.plugin.javascript.restricted.SoyJavaScriptSourceFunction;
import com.google.template.soy.plugin.python.restricted.PythonPluginContext;
import com.google.template.soy.plugin.python.restricted.PythonValue;
import com.google.template.soy.plugin.python.restricted.PythonValueFactory;
import com.google.template.soy.plugin.python.restricted.SoyPythonSourceFunction;
import com.google.template.soy.pysrc.restricted.PyExprUtils;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.ibm.icu.util.ULocale;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A function that formats an input number based on Locale of the current SoyMsgBundle. The function
 * takes one required argument and up to four optional arguments:
 *
 * <p>The first is the value to format.
 *
 * <p>The second is a lower-case string describing the type of format to apply, which can be one of
 * 'decimal', 'currency', 'percent', 'scientific', 'compact_short', or 'compact_long'. If this
 * argument is not provided, the default 'decimal' will be used.
 *
 * <p>The third argument is the "numbers" keyword passed to the ICU4J's locale. For instance, it can
 * be "native" so that we show native characters in languages like arabic (this argument is ignored
 * for templates running in JavaScript).
 *
 * <p>The fourth and fifth arguments are min and max fractional digits. If only one is supplied,
 * this will be treated as # of significant digits after the decimal point, and applied as both min
 * and max fractional digits; trailing zeros will not be stripped in this case. If you wish to have
 * trailing zeros removed, minFractionalDigits should be set to 0.
 *
 * <p>Note: the fourth and fifth arguments are not supported in pysrc. Using them will cause a
 * runtime error.
 *
 * <p>Usage examples: {@code {formatNum($value)} {formatNum($value, 'decimal')} {formatNum($value,
 * 'decimal', 'native')} {formatNum($value, 'decimal', 'native', 1)} {formatNum($value,
 * 'decimal','native', 1, 3)}}
 */
@SoyFunctionSignature(
    name = "formatNum",
    value = {
      @Signature(
          parameterTypes = {"?"},
          returnType = "string"),
      @Signature(
          parameterTypes = {"?", "string"},
          returnType = "string"),
      @Signature(
          parameterTypes = {"?", "string", "string"},
          returnType = "string"),
      @Signature(
          parameterTypes = {"?", "string", "string", "number"},
          returnType = "string"),
      @Signature(
          parameterTypes = {"?", "string", "string", "number", "number"},
          returnType = "string"),
    },
    callableAsDeprecatedPrintDirective = true)
class FormatNumFunction
    implements SoyJavaSourceFunction, SoyJavaScriptSourceFunction, SoyPythonSourceFunction {

  private static final String DEFAULT_FORMAT = "decimal";

  private static final ImmutableMap<String, String> JS_ARGS_TO_ENUM =
      ImmutableMap.<String, String>builder()
          .put(DEFAULT_FORMAT, "goog.i18n.NumberFormat.Format.DECIMAL")
          .put("currency", "goog.i18n.NumberFormat.Format.CURRENCY")
          .put("percent", "goog.i18n.NumberFormat.Format.PERCENT")
          .put("scientific", "goog.i18n.NumberFormat.Format.SCIENTIFIC")
          .put("compact_short", "goog.i18n.NumberFormat.Format.COMPACT_SHORT")
          .put("compact_long", "goog.i18n.NumberFormat.Format.COMPACT_LONG")
          .build();

  FormatNumFunction() {}

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class Methods {
    static final Method FORMAT_NUM =
        JavaValueFactory.createMethod(
            I18NDirectivesRuntime.class,
            "formatNum",
            ULocale.class,
            SoyValue.class,
            String.class,
            String.class,
            NumberData.class,
            NumberData.class);
  }

  @Override
  public JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
    return factory.callStaticMethod(
        Methods.FORMAT_NUM,
        context.getULocale(),
        args.get(0),
        getArgOrDefault(args, 1, factory.constant(DEFAULT_FORMAT)),
        getArgOrDefault(args, 2, factory.constant("local")),
        getArgOrDefault(args, 3, factory.constantNull()),
        getArgOrDefault(args, 4, factory.constantNull()));
  }

  @Override
  public JavaScriptValue applyForJavaScriptSource(
      JavaScriptValueFactory factory, List<JavaScriptValue> args, JavaScriptPluginContext context) {
    List<JavaScriptValue> jsArgs = new ArrayList<>(args);
    if (jsArgs.size() == 1) {
      jsArgs.add(factory.global(JS_ARGS_TO_ENUM.get(DEFAULT_FORMAT)));
    } else {
      Optional<String> formatArg = args.get(1).asStringLiteral();
      if (!formatArg.isPresent()) {
        throw new IllegalArgumentException(
            "The second parameter to formatNum must be a string literal");
      }
      String formatEnum = JS_ARGS_TO_ENUM.get(formatArg.get());
      if (formatEnum == null) {
        throw new IllegalArgumentException(
            "The second parameter to formatNum must be one of {"
                + Joiner.on(", ").join(JS_ARGS_TO_ENUM.keySet())
                + "} but was "
                + formatArg.get());
      }
      jsArgs.set(1, factory.global(formatEnum));
    }

    if (jsArgs.size() > 2) {
      jsArgs.remove(2); // Don't pass the 3rd param to JavaScript.
    }
    return factory.callModuleFunction("soy.i18n", "$$formatNum", jsArgs);
  }

  @Override
  public PythonValue applyForPythonSource(
      PythonValueFactory factory, List<PythonValue> args, PythonPluginContext context) {
    List<PythonValue> pyArgs = new ArrayList<>(args);
    if (pyArgs.size() > 2) {
      pyArgs.remove(2); // Don't pass the 3rd param to Python.
    }
    return factory.global(PyExprUtils.TRANSLATOR_NAME + ".format_num").call(pyArgs);
  }

  private static JavaValue getArgOrDefault(List<JavaValue> args, int index, JavaValue defaultVal) {
    return args.size() > index ? args.get(index) : defaultVal;
  }
}
