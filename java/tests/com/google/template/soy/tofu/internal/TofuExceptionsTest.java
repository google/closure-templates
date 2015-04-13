/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.tofu.internal;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;

import com.google.common.base.Joiner;
import com.google.common.util.concurrent.Futures;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.data.SoyEasyDict;
import com.google.template.soy.data.SoyFutureException;
import com.google.template.soy.data.SoyValueHelper;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.tofu.SoyTofu;
import com.google.template.soy.tofu.SoyTofuException;
import com.google.template.soy.tofu.internal.BaseTofu.BaseTofuFactory;

import junit.framework.TestCase;

/**
 * Unit tests for exception behavior of Tofu.
 */
public final class TofuExceptionsTest extends TestCase {
  private static final SoyValueHelper VALUE_HELPER = SoyValueHelper.UNCUSTOMIZED_INSTANCE;
  private static final Injector INJECTOR = Guice.createInjector(new TofuModule());

  private static final String SOY_FILE = Joiner.on('\n').join(
      "{namespace ns}",
      "",
      "/** */",
      "{template .callerTemplate}",
      "  {call .calleeTemplate data=\"all\" /}",
      "{/template}",
      "",  // line 7
      "{template .calleeTemplate}",
      "  {@param foo: [boo: int, bad: string]}",
      "  {$foo.boo}",
      "  {$foo.bad}",
      "{/template}",
      "", // line 13
      "{template .transclusionCaller}",
      "  {@param foo: int}",
      "  {call .transclusionCallee}",
      "    {param content}{$foo}{/param}",
      "  {/call}",
      "{/template}",
      "", // line 20
      "{template .transclusionCallee}",
      "  {@param content: string}",
      "  {$content}",
      "{/template}");

  private SoyTofu tofu;

  @Override protected void setUp() throws Exception {
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(SOY_FILE).parse();
    tofu = INJECTOR.getInstance(BaseTofuFactory.class)
        .create(soyTree, false /* isCaching */, ExplodingErrorReporter.get());
  }

  public void testExceptions_undefined() throws Exception {
    SoyEasyDict data = VALUE_HELPER.newEasyDict("foo.boo", 42);
    // This is an exception that occurs during expression evaluation
    try {
      tofu.newRenderer("ns.callerTemplate").setData(data).render();
      fail();
    } catch (SoyTofuException ste) {
      assertThat(ste.getCause()).isNull();
      assertThat(ste).hasMessage("In 'print' tag, expression \"$foo.bad\" evaluates to undefined.");
      assertThat(ste.getStackTrace()[0].toString()).isEqualTo("ns.calleeTemplate(no-path:11)");
      assertThat(ste.getStackTrace()[1].toString()).isEqualTo("ns.callerTemplate(no-path:5)");
    }
  }

  public void testExceptions_badType() throws Exception {
    SoyEasyDict data = VALUE_HELPER.newEasyDict("foo", "not a record");
    // This is an exception that occurs during template calling due to a type checkin
    try {
      tofu.newRenderer("ns.callerTemplate").setData(data).render();
      fail();
    } catch (SoyTofuException ste) {
      assertThat(ste.getCause()).isNull();
      assertThat(ste).hasMessage(
          "Parameter type mismatch: attempt to bind value 'not a record' to parameter "
          + "'foo' which has declared type '[bad: string, boo: int]'.");
      assertThat(ste.getStackTrace()[0].toString()).isEqualTo("ns.calleeTemplate(no-path:8)");
      assertThat(ste.getStackTrace()[1].toString()).isEqualTo("ns.callerTemplate(no-path:5)");
    }
  }

  public void testExceptions_failedFuture() {
    Exception futureFailureCause = new Exception("boom");
    SoyEasyDict data = VALUE_HELPER.newEasyDict("foo", immediateFailedFuture(futureFailureCause));
    // This error occurs due to a failed future.
    try {
      tofu.newRenderer("ns.callerTemplate").setData(data).render();
      fail();
    } catch (SoyTofuException ste) {
      assertThat(ste).hasMessage("When evaluating \"$foo.boo\": Error dereferencing future");
      SoyFutureException sfe = (SoyFutureException) ste.getCause();
      assertThat(sfe).hasMessage("Error dereferencing future");
      assertThat(sfe.getCause()).isEqualTo(futureFailureCause);
      assertThat(ste.getStackTrace()[0].toString()).isEqualTo("ns.calleeTemplate(no-path:10)");
      assertThat(ste.getStackTrace()[1].toString()).isEqualTo("ns.callerTemplate(no-path:5)");
    }
  }

  public void testExceptions_wrongTypeFuture() {
    SoyEasyDict data = VALUE_HELPER.newEasyDict("foo", Futures.immediateFuture("not a record"));
    // This error occurs due to data of the wrong type, hidden behind a future.
    try {
      tofu.newRenderer("ns.callerTemplate").setData(data).render();
      fail();
    } catch (SoyTofuException ste) {
      assertThat(ste.getCause()).isNull();
      assertThat(ste).hasMessage(
          "When evaluating \"$foo.boo\": Parameter type mismatch: attempt to bind value "
          + "'not a record' to parameter 'foo' which has declared type "
          + "'[bad: string, boo: int]'.");
      assertThat(ste.getStackTrace()[0].toString()).isEqualTo("ns.calleeTemplate(no-path:8)");
      assertThat(ste.getStackTrace()[1].toString()).isEqualTo("ns.calleeTemplate(no-path:10)");
      assertThat(ste.getStackTrace()[2].toString()).isEqualTo("ns.callerTemplate(no-path:5)");
    }
  }

  public void testExceptions_transclusion_wrongTypeFuture() {
    SoyEasyDict data = VALUE_HELPER.newEasyDict("foo", Futures.immediateFuture("not an int"));
    try {
      tofu.newRenderer("ns.transclusionCaller").setData(data).render();
      fail();
    } catch (SoyTofuException ste) {
      assertThat(ste.getCause()).isNull();
      assertThat(ste).hasMessage(
          "When evaluating \"$foo\": Parameter type mismatch: attempt to bind value "
          + "'not an int' to parameter 'foo' which has declared type 'int'.");
      assertThat(ste.getStackTrace()[0].toString()).isEqualTo("ns.transclusionCaller(no-path:14)");
      assertThat(ste.getStackTrace()[1].toString()).isEqualTo("ns.transclusionCaller(no-path:17)");
      assertThat(ste.getStackTrace()[2].toString()).isEqualTo("ns.transclusionCallee(no-path:23)");
      assertThat(ste.getStackTrace()[3].toString()).isEqualTo("ns.transclusionCaller(no-path:16)");
    }
  }

  public void testExceptions_transclusion_failedFuture() {
    Exception futureFailureCause = new Exception("boom");
    SoyEasyDict data = VALUE_HELPER.newEasyDict("foo", immediateFailedFuture(futureFailureCause));
    try {
      tofu.newRenderer("ns.transclusionCaller").setData(data).render();
      fail();
    } catch (SoyTofuException ste) {
      SoyFutureException sfe = (SoyFutureException) ste.getCause();
      assertThat(sfe).hasMessage("Error dereferencing future");
      assertThat(sfe.getCause()).isEqualTo(futureFailureCause);
      assertThat(ste).hasMessage("When evaluating \"$foo\": Error dereferencing future");
      assertThat(ste.getStackTrace()[0].toString()).isEqualTo("ns.transclusionCaller(no-path:17)");
      assertThat(ste.getStackTrace()[1].toString()).isEqualTo("ns.transclusionCallee(no-path:23)");
      assertThat(ste.getStackTrace()[2].toString()).isEqualTo("ns.transclusionCaller(no-path:16)");
    }
  }
}
