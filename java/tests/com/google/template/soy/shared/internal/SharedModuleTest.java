/*
 * Copyright 2017 Google Inc.
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
package com.google.template.soy.shared.internal;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.template.soy.coredirectives.IdDirective;
import com.google.template.soy.coredirectives.NoAutoescapeDirective;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcFunction;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcPrintDirective;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.jssrc.restricted.SoyJsSrcPrintDirective;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.pysrc.restricted.SoyPySrcPrintDirective;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SharedModuleTest {

  // pysrc has intentionally not implemented a few directives.
  private static final ImmutableSet<String> PYSRC_DIRECTIVE_BLACKLIST =
      ImmutableSet.of(NoAutoescapeDirective.NAME, IdDirective.NAME, "|insertWordBreaks");
  private static final ImmutableSet<String> JBCSRC_DIRECTIVE_BLACKLIST =
      ImmutableSet.of("|bidiSpanWrap", "|bidiUnicodeWrap", "|formatNum");

  private Injector injector;

  @Before
  public void setUp() {
    injector = Guice.createInjector(new SharedModule());
  }

  @Test
  public void testBuiltinPluginsSupportAllBackends() throws Exception {
    for (SoyPrintDirective directive : injector.getInstance(new Key<Set<SoyPrintDirective>>() {})) {
      assertThat(directive).isInstanceOf(SoyJsSrcPrintDirective.class);
      assertThat(directive).isInstanceOf(SoyJavaPrintDirective.class);
      if (!JBCSRC_DIRECTIVE_BLACKLIST.contains(directive.getName())) {
        assertThat(directive).isInstanceOf(SoyJbcSrcPrintDirective.class);
      }
      if (!PYSRC_DIRECTIVE_BLACKLIST.contains(directive.getName())) {
        assertThat(directive).isInstanceOf(SoyPySrcPrintDirective.class);
      }
    }
  }

  @Test
  public void testFunctionsSupportAllBackends() {
    for (SoyFunction function : injector.getInstance(new Key<Set<SoyFunction>>() {})) {
      assertThat(function).isInstanceOf(SoyJsSrcFunction.class);
      assertThat(function).isInstanceOf(SoyJavaFunction.class);
      assertThat(function).isInstanceOf(SoyJbcSrcFunction.class);
      assertThat(function).isInstanceOf(SoyPySrcFunction.class);
    }
  }
}
