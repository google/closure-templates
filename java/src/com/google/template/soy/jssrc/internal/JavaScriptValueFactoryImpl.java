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
package com.google.template.soy.jssrc.internal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.CodeChunkUtils;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.GoogRequire;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptPluginContext;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptValue;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptValueFactory;
import com.google.template.soy.plugin.javascript.restricted.SoyJavaScriptSourceFunction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of {@link JavaScriptValueFactory} that delegates to the {@link Expression} API.
 */
public final class JavaScriptValueFactoryImpl extends JavaScriptValueFactory {
  private static final JavaScriptValueImpl ERROR_VALUE =
      new JavaScriptValueImpl(
          Expression.fromExpr(
              new JsExpr(
                  "(function(){throw new Error('if you see this, the soy compiler has swallowed "
                      + "an error :-(');})()",
                  Integer.MAX_VALUE),
              ImmutableList.of()));

  private static final SoyErrorKind NULL_RETURN =
      SoyErrorKind.of(
          formatPlain("{2}.applyForJavaScriptSource returned null."),
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind UNEXPECTED_ERROR =
      SoyErrorKind.of(formatPlain("{2}"), StyleAllowance.NO_PUNCTUATION);

  private static String formatPlain(String innerFmt) {
    return "Error in plugin implementation for function ''{0}''."
        + "\n"
        + innerFmt
        + "\nPlugin implementation: {1}";
  }

  private final ErrorReporter reporter;
  private final BidiGlobalDir dir;

  private JavaScriptPluginContext createContext(final CodeChunk.Generator codeGenerator) {
    return new JavaScriptPluginContext() {
      @Override
      public JavaScriptValue getBidiDir() {
        if (dir.isStaticValue()) {
          return new JavaScriptValueImpl(Expression.number(dir.getStaticValue()));
        }
        return new JavaScriptValueImpl(
            Expression.ifExpression(JsRuntime.SOY_IS_LOCALE_RTL, Expression.number(-1))
                .setElse(Expression.number(1))
                .build(codeGenerator));
      }
    };
  }

  public JavaScriptValueFactoryImpl(BidiGlobalDir dir, ErrorReporter reporter) {
    this.dir = dir;
    this.reporter = reporter;
  }

  Expression applyFunction(
      SourceLocation location,
      String name,
      SoyJavaScriptSourceFunction fn,
      List<Expression> args,
      CodeChunk.Generator codeGenerator) {
    JavaScriptValueImpl result;
    try {
      result =
          (JavaScriptValueImpl)
              fn.applyForJavaScriptSource(this, wrapParams(args), createContext(codeGenerator));
      if (result == null) {
        report(location, name, fn, NULL_RETURN, fn.getClass().getSimpleName());
        result = ERROR_VALUE;
      }
    } catch (Throwable t) {
      BaseUtils.trimStackTraceTo(t, getClass());
      report(location, name, fn, UNEXPECTED_ERROR, Throwables.getStackTraceAsString(t));
      result = ERROR_VALUE;
    }
    return result.impl;
  }

  private void report(
      SourceLocation location,
      String name,
      SoyJavaScriptSourceFunction fn,
      SoyErrorKind error,
      Object... additionalArgs) {
    Object[] args = new Object[additionalArgs.length + 2];
    args[0] = name;
    args[1] = fn.getClass().getName();
    System.arraycopy(additionalArgs, 0, args, 2, additionalArgs.length);
    reporter.report(location, error, args);
  }

  private Expression chainedDotAccess(Expression base, String suffix) {
    for (String part : Splitter.on('.').splitToList(suffix)) {
      base = base.dotAccess(part);
    }
    return base;
  }

  private Expression referenceModuleExport(String moduleName, String export) {
    // Just use goog.module.get().  It isn't currently possible to create an alias without
    // potentially creating name conflicts.
    // TODO(b/35203585): come up with an aliasing strategy
    return chainedDotAccess(GoogRequire.create(moduleName).googModuleGet(), export);
  }

  @Override
  public JavaScriptValue getModuleExport(String moduleName, String export) {
    return new JavaScriptValueImpl(referenceModuleExport(moduleName, export));
  }

  @Override
  public JavaScriptValue callModuleFunction(
      String moduleName, String functionName, JavaScriptValue... params) {
    return new JavaScriptValueImpl(
        referenceModuleExport(moduleName, functionName).call(unwrapParams(Arrays.asList(params))));
  }

  @Override
  public JavaScriptValue callNamespaceFunction(
      String googProvide, String fullFunctionName, JavaScriptValue... params) {
    checkArgument(
        fullFunctionName.startsWith(googProvide)
            && (fullFunctionName.length() == googProvide.length()
                || fullFunctionName.charAt(googProvide.length()) == '.'),
        "expected '%s' to be in the namespace of '%s'. '%s' should be fully qualified",
        fullFunctionName,
        googProvide,
        fullFunctionName);
    GoogRequire require = GoogRequire.create(googProvide);
    Expression function;
    if (fullFunctionName.length() == googProvide.length()) {
      function = require.reference();
    } else {
      function =
          chainedDotAccess(
              require.reference(), fullFunctionName.substring(googProvide.length() + 1));
    }
    return new JavaScriptValueImpl(function.call(unwrapParams(Arrays.asList(params))));
  }

  @Override
  public JavaScriptValueImpl unsafeUncheckedExpression(String expr) {
    return new JavaScriptValueImpl(
        Expression.fromExpr(new JsExpr(expr, /*precedence=*/ 0), ImmutableList.of()));
  }

  @Override
  public JavaScriptValueImpl constant(long num) {
    return new JavaScriptValueImpl(Expression.number(num));
  }

  @Override
  public JavaScriptValueImpl constant(double num) {
    return new JavaScriptValueImpl(Expression.number(num));
  }

  @Override
  public JavaScriptValueImpl constant(String str) {
    return new JavaScriptValueImpl(Expression.stringLiteral(str));
  }

  @Override
  public JavaScriptValueImpl constant(boolean bool) {
    return new JavaScriptValueImpl(bool ? Expression.LITERAL_TRUE : Expression.LITERAL_FALSE);
  }

  @Override
  public JavaScriptValue constantNull() {
    return new JavaScriptValueImpl(Expression.LITERAL_NULL);
  }

  @Override
  public JavaScriptValueImpl global(String globalSymbol) {
    return new JavaScriptValueImpl(Expression.dottedIdNoRequire(globalSymbol));
  }

  private static List<Expression> unwrapParams(List<JavaScriptValue> params) {
    List<Expression> exprs = new ArrayList<>(params.size());
    for (JavaScriptValue v : params) {
      exprs.add(((JavaScriptValueImpl) v).impl);
    }
    return exprs;
  }

  private static List<JavaScriptValue> wrapParams(List<Expression> params) {
    List<JavaScriptValue> exprs = new ArrayList<>(params.size());
    for (Expression e : params) {
      exprs.add(new JavaScriptValueImpl(e));
    }
    return exprs;
  }

  @VisibleForTesting
  static final class JavaScriptValueImpl implements JavaScriptValue {
    @VisibleForTesting final Expression impl;

    JavaScriptValueImpl(Expression impl) {
      this.impl = checkNotNull(impl);
    }

    @Override
    public JavaScriptValueImpl isNonNull() {
      return new JavaScriptValueImpl(impl.doubleNotEquals(Expression.LITERAL_NULL));
    }

    @Override
    public JavaScriptValueImpl isNull() {
      return new JavaScriptValueImpl(impl.doubleEquals(Expression.LITERAL_NULL));
    }

    @Override
    public Optional<String> asStringLiteral() {
      return impl.asStringLiteral();
    }

    @Override
    public JavaScriptValueImpl coerceToString() {
      return new JavaScriptValueImpl(
          CodeChunkUtils.concatChunksForceString(ImmutableList.of(impl)));
    }

    @Override
    public JavaScriptValueImpl invokeMethod(String methodName, JavaScriptValue... args) {
      return new JavaScriptValueImpl(
          impl.dotAccess(methodName).call(unwrapParams(Arrays.asList(args))));
    }

    @Override
    public JavaScriptValueImpl accessProperty(String ident) {
      return new JavaScriptValueImpl(impl.dotAccess(ident));
    }

    @Override
    public String toString() {
      return impl.getCode();
    }
  }
}
