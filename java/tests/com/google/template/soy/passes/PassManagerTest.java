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

package com.google.template.soy.passes;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.passes.PassManager.PassContinuationRule;
import com.google.template.soy.shared.SoyGeneralOptions;
import com.google.template.soy.types.SoyTypeRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PassManagerTest {
  private static PassManager.Builder builder() {
    return new PassManager.Builder()
        .setGeneralOptions(new SoyGeneralOptions())
        .setSoyPrintDirectiveMap(ImmutableMap.of())
        .setTypeRegistry(new SoyTypeRegistry())
        .setErrorReporter(ErrorReporter.exploding());
  }

  private static class NoSuchPass extends CompilerPass {}

  @Test
  public void testInvalidRule() {
    try {
      builder()
          .addPassContinuationRule(NoSuchPass.class, PassContinuationRule.STOP_AFTER_PASS)
          .build();
      fail();
    } catch (IllegalStateException expected) {
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo(
              "The following continuation rules don't match any pass: "
                  + "{class com.google.template.soy.passes.PassManagerTest$NoSuchPass="
                  + "STOP_AFTER_PASS}");
    }
  }

  @Test
  public void testContinuationRule_stopAfter() throws Exception {
    PassManager manager =
        builder()
            .addPassContinuationRule(
                ResolveTemplateParamTypesPass.class, PassContinuationRule.STOP_AFTER_PASS)
            .build();

    assertThat(names(manager.singleFilePasses))
        .containsExactly("ContentSecurityPolicyNonceInjection", "ResolveTemplateParamTypes");
    assertThat(names(manager.crossTemplateCheckingPasses)).isEmpty();
  }

  @Test
  public void testContinuationRule_stopBefore() throws Exception {
    PassManager manager =
        builder()
            .addPassContinuationRule(
                ResolveTemplateParamTypesPass.class, PassContinuationRule.STOP_BEFORE_PASS)
            .build();

    assertThat(names(manager.singleFilePasses))
        .containsExactly("ContentSecurityPolicyNonceInjection");
    assertThat(names(manager.crossTemplateCheckingPasses)).isEmpty();
  }

  private static <T extends CompilerPass> ImmutableList<String> names(ImmutableList<T> passes) {
    return passes.stream().map(CompilerPass::name).collect(toImmutableList());
  }
}
