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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.FormatOptions;
import com.google.template.soy.plugin.javascript.restricted.SoyJavaScriptSourceFunction;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class JavaScriptValueFactoryImplTest {

  static final SoyJavaScriptSourceFunction callModuleFn =
      (factory, args, context) -> factory.callModuleFunction("foo.bar", "baz");

  static final SoyJavaScriptSourceFunction moduleExportFn =
      (factory, args, context) -> factory.getModuleExport("foo.bar", "baz.qux");

  static final SoyJavaScriptSourceFunction callNamespaceFn =
      (factory, args, context) -> factory.callNamespaceFunction("foo.bar", "foo.bar.baz");

  @Test
  public void testGetModuleExport() throws Exception {
    Expression expr = applyFunction(moduleExportFn);

    assertThat(getRequires(expr)).isEqualTo("goog.require('foo.bar');\n");
    assertThat(expr.getCode(FormatOptions.JSSRC)).isEqualTo("goog.module.get('foo.bar').baz.qux;");
  }

  @Test
  public void testCallModuleFunction() throws Exception {
    Expression expr = applyFunction(callModuleFn);

    assertThat(getRequires(expr)).isEqualTo("goog.require('foo.bar');\n");
    assertThat(expr.getCode(FormatOptions.JSSRC)).isEqualTo("goog.module.get('foo.bar').baz();");
  }

  @Test
  public void testCallNamespaceFnDirect() throws Exception {

    Expression expr =
        applyFunction(
            (factory, args, context) -> factory.callNamespaceFunction("foo.bar", "foo.bar"));

    assertThat(getRequires(expr)).isEqualTo("goog.require('foo.bar');\n");
    assertThat(expr.getCode(FormatOptions.JSSRC)).isEqualTo("goog.module.get('foo.bar')();");
  }

  @Test
  public void testCallNamespaceFn() throws Exception {
    Expression expr =
        applyFunction(
            (factory, args, context) -> factory.callNamespaceFunction("foo.bar", "foo.bar.baz"));

    assertThat(getRequires(expr)).isEqualTo("goog.require('foo.bar');\n");
    assertThat(expr.getCode(FormatOptions.JSSRC)).isEqualTo("goog.module.get('foo.bar').baz();");
  }

  @Test
  public void testConstant() {
    JavaScriptValueFactoryImpl factory =
        new JavaScriptValueFactoryImpl(BidiGlobalDir.LTR, ErrorReporter.exploding());
    assertThat(factory.constant(1).toString()).isEqualTo("1;");
    assertThat(factory.constant(1.1).toString()).isEqualTo("1.1;");
    assertThat(factory.constant(false).toString()).isEqualTo("false;");
    assertThat(factory.constant("false").toString()).isEqualTo("'false';");
    assertThat(factory.constantNull().toString()).isEqualTo("null;");
  }

  @Test
  public void testInvokeMethod() {
    JavaScriptValueFactoryImpl factory =
        new JavaScriptValueFactoryImpl(BidiGlobalDir.LTR, ErrorReporter.exploding());
    assertThat(factory.constant("str").invokeMethod("indexOf", factory.constant("s")).toString())
        .isEqualTo("'str'.indexOf('s');");
  }

  static String getRequires(CodeChunk chunk) {
    StringBuilder sb = new StringBuilder();
    chunk.collectRequires(req -> req.writeTo(sb));
    return sb.toString();
  }

  static Expression applyFunction(SoyJavaScriptSourceFunction fn, Expression... args) {
    return new JavaScriptValueFactoryImpl(BidiGlobalDir.LTR, ErrorReporter.exploding())
        .applyFunction(
            SourceLocation.UNKNOWN, "foo", fn, ImmutableList.copyOf(args), newGenerator());
  }

  static CodeChunk.Generator newGenerator() {
    return CodeChunk.Generator.create(JsSrcNameGenerators.forLocalVariables());
  }
}
