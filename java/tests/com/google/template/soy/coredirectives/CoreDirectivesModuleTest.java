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
package com.google.template.soy.coredirectives;

import static com.google.common.truth.Truth.assertThat;

import com.google.inject.Guice;
import com.google.inject.Key;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcPrintDirective;
import com.google.template.soy.jssrc.restricted.SoyJsSrcPrintDirective;
import com.google.template.soy.pysrc.restricted.SoyPySrcPrintDirective;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CoreDirectivesModuleTest {
  @Test
  public void testAllCoreDirectivesSupportAllBackends() {
    Set<SoyPrintDirective> coreDirectives =
        Guice.createInjector(new CoreDirectivesModule())
            .getInstance(new Key<Set<SoyPrintDirective>>() {});
    for (SoyPrintDirective directive : coreDirectives) {
      assertThat(directive).isInstanceOf(SoyJsSrcPrintDirective.class);
      assertThat(directive).isInstanceOf(SoyJavaPrintDirective.class);
      assertThat(directive).isInstanceOf(SoyJbcSrcPrintDirective.class);
      // These two are not implemented for pysrc intentionally
      if (!directive.getName().equals(NoAutoescapeDirective.NAME)
          && !directive.getName().equals(IdDirective.NAME)) {
        assertThat(directive).isInstanceOf(SoyPySrcPrintDirective.class);
      }
    }
  }
}
