/*
 * Copyright 2019 Google Inc.
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
package com.google.template.soy.pysrc.internal;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.template.soy.pysrc.restricted.PyExprUtils.maybeProtect;

import com.google.common.base.Throwables;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.plugin.python.restricted.PythonPluginContext;
import com.google.template.soy.plugin.python.restricted.PythonValue;
import com.google.template.soy.plugin.python.restricted.PythonValueFactory;
import com.google.template.soy.plugin.python.restricted.SoyPythonSourceFunction;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyExprUtils;
import com.google.template.soy.pysrc.restricted.PyStringExpr;
import java.util.ArrayList;
import java.util.List;

/** A {@link PythonValueFactory} implementation that can also manage invoking the plugins. */
final class PythonValueFactoryImpl extends PythonValueFactory {

  private static final PythonValueImpl ERROR_VALUE =
      new PythonValueImpl(
          new PyStringExpr(
              "uh oh, if you see this the soy compiler has swallowed an error", Integer.MIN_VALUE));

  private static final SoyErrorKind NULL_RETURN =
      SoyErrorKind.of(
          formatPlain("{2}.applyForPythonSource returned null."), StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind UNEXPECTED_ERROR =
      SoyErrorKind.of(formatPlain("{2}"), StyleAllowance.NO_PUNCTUATION);

  private static String formatPlain(String innerFmt) {
    return "Error in plugin implementation for function ''{0}''."
        + "\n"
        + innerFmt
        + "\nPlugin implementation: {1}";
  }

  private final PythonPluginContext context;
  private final ErrorReporter reporter;

  PythonValueFactoryImpl(ErrorReporter reporter, final BidiGlobalDir bidiDir) {
    this.reporter = checkNotNull(reporter);
    checkNotNull(bidiDir);
    this.context =
        new PythonPluginContext() {
          @Override
          public PythonValue getBidiDir() {
            return new PythonValueImpl(new PyExpr(bidiDir.getCodeSnippet(), Integer.MIN_VALUE));
          }
        };
  }

  PyExpr applyFunction(
      SourceLocation location, String name, SoyPythonSourceFunction fn, List<PyExpr> args) {
    PythonValueImpl result;
    try {
      result = (PythonValueImpl) fn.applyForPythonSource(this, wrapParams(args), context);
      if (result == null) {
        report(location, name, fn, NULL_RETURN, fn.getClass().getSimpleName());
        result = ERROR_VALUE;
      }
    } catch (Throwable t) {
      BaseUtils.trimStackTraceTo(t, getClass());
      report(location, name, fn, UNEXPECTED_ERROR, Throwables.getStackTraceAsString(t));
      result = ERROR_VALUE;
    }
    return result.expr;
  }

  private void report(
      SourceLocation location,
      String name,
      SoyPythonSourceFunction fn,
      SoyErrorKind error,
      Object... additionalArgs) {
    Object[] args = new Object[additionalArgs.length + 2];
    args[0] = name;
    args[1] = fn.getClass().getName();
    System.arraycopy(additionalArgs, 0, args, 2, additionalArgs.length);
    reporter.report(location, error, args);
  }

  private static List<PythonValue> wrapParams(List<PyExpr> params) {
    List<PythonValue> exprs = new ArrayList<>(params.size());
    for (PyExpr e : params) {
      exprs.add(new PythonValueImpl(e));
    }
    return exprs;
  }

  @Override
  public PythonValue constant(long num) {
    return new PythonValueImpl(new PyExpr(Long.toString(num), Integer.MAX_VALUE));
  }

  @Override
  public PythonValue constant(double num) {
    return new PythonValueImpl(new PyExpr(Double.toString(num), Integer.MAX_VALUE));
  }

  @Override
  public PythonValue constant(String str) {
    return new PythonValueImpl(new PyStringExpr("'" + str + "'", Integer.MAX_VALUE));
  }

  @Override
  public PythonValue constant(boolean bool) {
    return new PythonValueImpl(new PyExpr(bool ? "True" : "False", Integer.MAX_VALUE));
  }

  @Override
  public PythonValue constantNull() {
    return new PythonValueImpl(new PyExpr("None", Integer.MAX_VALUE));
  }

  @Override
  public PythonValue global(String globalSymbol) {
    return new PythonValueImpl(new PyExpr(globalSymbol, Integer.MAX_VALUE));
  }

  private static final class PythonValueImpl implements PythonValue {
    final PyExpr expr;

    PythonValueImpl(PyExpr expr) {
      this.expr = checkNotNull(expr);
    }

    @Override
    public PythonValue isNull() {
      return new PythonValueImpl(PyExprUtils.genPyNullCheck(expr));
    }

    @Override
    public PythonValue isNonNull() {
      return new PythonValueImpl(PyExprUtils.genPyNotNullCheck(expr));
    }

    @Override
    public PythonValue getProp(String ident) {
      return new PythonValueImpl(
          new PyExpr(
              maybeProtect(expr, PyExprUtils.GETPROP_PRECEDENCE).getText() + "." + ident,
              PyExprUtils.GETPROP_PRECEDENCE));
    }

    @Override
    public PythonValue call(PythonValue... args) {
      StringBuilder sb =
          new StringBuilder()
              .append(maybeProtect(expr, PyExprUtils.CALL_PRECEDENCE).getText())
              .append("(");
      boolean isFirst = true;
      for (PythonValue arg : args) {
        if (isFirst) {
          isFirst = false;
        } else {
          sb.append(", ");
        }
        sb.append(unwrap(arg).getText());
      }
      sb.append(")");
      return new PythonValueImpl(new PyExpr(sb.toString(), PyExprUtils.CALL_PRECEDENCE));
    }

    @Override
    public PythonValue plus(PythonValue value) {
      int plusPrecedence = PyExprUtils.pyPrecedenceForOperator(Operator.PLUS);
      return new PythonValueImpl(
          new PyExpr(
              maybeProtect(expr, plusPrecedence).getText()
                  + " + "
                  + maybeProtect(unwrap(value), plusPrecedence).getText(),
              plusPrecedence));
    }

    @Override
    public PythonValue coerceToString() {
      return new PythonValueImpl(expr.toPyString());
    }

    @Override
    public PythonValue in(PythonValue value) {
      return new PythonValueImpl(
          new PyExpr(
              maybeProtect(expr, PyExprUtils.IN_PRECEDENCE).getText()
                  + " in "
                  + maybeProtect(unwrap(value), PyExprUtils.IN_PRECEDENCE).getText(),
              PyExprUtils.IN_PRECEDENCE));
    }

    @Override
    public PythonValue getItem(PythonValue key) {
      return new PythonValueImpl(
          new PyExpr(
              maybeProtect(expr, PyExprUtils.SUBSCRIPT_PRECEDENCE).getText()
                  + "["
                  + unwrap(key).getText()
                  + "]",
              PyExprUtils.SUBSCRIPT_PRECEDENCE));
    }

    private static PyExpr unwrap(PythonValue start) {
      return ((PythonValueImpl) start).expr;
    }

    @Override
    public String toString() {
      return expr.getText();
    }
  }
}
