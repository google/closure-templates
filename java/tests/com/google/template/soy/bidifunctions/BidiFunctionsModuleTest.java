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
package com.google.template.soy.bidifunctions;

import static com.google.common.truth.Truth.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Key;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcFunction;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class BidiFunctionsModuleTest {

  @Test
  public void testAllBidiFunctionsSupportAllBackends() {
    Set<SoyFunction> bidiFunctions =
        Guice.createInjector(
                new BidiFunctionsModule(),
                new AbstractModule() {
                  @Override
                  protected void configure() {
                    bind(BidiGlobalDir.class).toInstance(BidiGlobalDir.LTR);
                  }
                })
            .getInstance(new Key<Set<SoyFunction>>() {});
    for (SoyFunction bidiFunction : bidiFunctions) {
      assertThat(bidiFunction).isInstanceOf(SoyJsSrcFunction.class);
      assertThat(bidiFunction).isInstanceOf(SoyJavaFunction.class);
      assertThat(bidiFunction).isInstanceOf(SoyPySrcFunction.class);
      assertThat(bidiFunction).isInstanceOf(SoyJbcSrcFunction.class);
    }
  }
}
