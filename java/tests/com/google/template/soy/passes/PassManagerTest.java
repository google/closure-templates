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
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.passes.PassManager.AstRewrites;
import com.google.template.soy.passes.PassManager.PassContinuationRule;
import com.google.template.soy.shared.SoyGeneralOptions;
import com.google.template.soy.types.SoyTypeRegistryBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PassManagerTest {
  private static PassManager.Builder builder() {
    return new PassManager.Builder()
        .setGeneralOptions(new SoyGeneralOptions())
        .setSoyPrintDirectives(ImmutableList.of())
        .setTypeRegistry(SoyTypeRegistryBuilder.create())
        .setErrorReporter(ErrorReporter.exploding())
        .setCssRegistry(Optional.empty());
  }

  private static class NoSuchPass implements CompilerPass {}

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
            .addPassContinuationRule(ResolvePluginsPass.class, PassContinuationRule.STOP_AFTER_PASS)
            .build();

    ImmutableList<String> passes = names(manager.passes);
    assertThat(Iterables.getLast(passes)).isEqualTo("ResolvePlugins");

    // CheckNonEmptyMsgNodesPass is a pass installed right after ResolvePluginsPass.
    assertThat(names(manager.passes)).doesNotContain("CheckNonEmptyMsgNodes");
  }

  @Test
  public void testContinuationRule_stopBefore() throws Exception {
    PassManager manager =
        builder()
            .addPassContinuationRule(
                ResolvePluginsPass.class, PassContinuationRule.STOP_BEFORE_PASS)
            .build();

    assertThat(names(manager.passes)).doesNotContain("ResolvePlugins");
  }

  @Test
  public void testOrdering() {
    // do nothing, passmanagers enforce ordering as a side effect of construction.
    forAllPassManagers(manager -> {});
  }

  // Make sure new passes are annotated
  @Test
  public void testPassesAreAnnotated() {
    Set<Class<? extends CompilerPass>> passesWithoutAnnotations = new LinkedHashSet<>();
    forAllPassManagers(
        manager ->
            Streams.<CompilerPass>concat(manager.parsePasses.stream(), manager.passes.stream())
                .filter(pass -> pass.runBefore().isEmpty() && pass.runAfter().isEmpty())
                .map(pass -> pass.getClass())
                .forEach(passesWithoutAnnotations::add));
    // Over time this list should decrease in size, it is, however,  reasonable that some passes may
    // never be removed for example if they could really run at any time or need to run at multiple
    // times (e.g. CombineConsecutiveRawTextNodesPass)
    ImmutableList<Class<? extends CompilerPass>> unannotatedAllowList =
        ImmutableList.of(
            BasicHtmlValidationPass.class,
            CheckDeclaredTypesPass.class,
            CheckGlobalsPass.class,
            CheckNonEmptyMsgNodesPass.class,
            CombineConsecutiveRawTextNodesPass.class,
            DesugarGroupNodesPass.class,
            DesugarHtmlNodesPass.class,
            DesugarStateNodesPass.class,
            EnforceExperimentalFeaturesPass.class,
            GetExtensionRewriteParamPass.class,
            InsertMsgPlaceholderNodesPass.class,
            KeyCommandPass.class,
            NullSafeAccessPass.class,
            OptimizationPass.class,
            ResolveExpressionTypesPass.class,
            RestoreGlobalsPass.class,
            ResolveNamesPass.class,
            ResolvePackageRelativeCssNamesPass.class,
            ResolveTemplateParamTypesPass.class,
            RewriteGenderMsgsPass.class,
            SimplifyAssertNonNullPass.class,
            UnknownJsGlobalPass.class,
            ValidateAliasesPass.class,
            ValidateSkipNodesPass.class,
            VeLogRewritePass.class,
            VeLogValidationPass.class,
            VeRewritePass.class);
    assertWithMessage("Passes with annotations should be removed from the allowlist")
        .that(passesWithoutAnnotations)
        .containsAtLeastElementsIn(unannotatedAllowList);
    assertWithMessage("New passes should be annotated")
        .that(unannotatedAllowList)
        .containsAtLeastElementsIn(passesWithoutAnnotations);
  }

  private static void forAllPassManagers(Consumer<PassManager> consumer) {
    for (SoyGeneralOptions soyGeneralOptions : allOptions()) {
      for (boolean allowUnknownGlobals : bools()) {
        for (boolean allowUnknownJsGlobals : bools()) {
          for (boolean disableAllTypeChecking : bools()) {
            for (boolean desugarHtmlAndStateNodes : bools()) {
              for (boolean optimize : bools()) {
                for (boolean insertEscapingDirectives : bools()) {
                  for (boolean addHtmlAttributesForDebugging : bools()) {
                    for (AstRewrites astRewrite : AstRewrites.values()) {
                      PassManager.Builder builder =
                          builder().setGeneralOptions(soyGeneralOptions).astRewrites(astRewrite);
                      if (allowUnknownGlobals) {
                        builder.allowUnknownGlobals();
                      }
                      if (allowUnknownJsGlobals) {
                        builder.allowUnknownJsGlobals();
                      }
                      if (disableAllTypeChecking) {
                        builder.disableAllTypeChecking();
                      }
                      builder
                          .desugarHtmlAndStateNodes(desugarHtmlAndStateNodes)
                          .optimize(optimize)
                          .insertEscapingDirectives(insertEscapingDirectives)
                          .addHtmlAttributesForDebugging(addHtmlAttributesForDebugging);
                      consumer.accept(builder.build());
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  private static Iterable<Boolean> bools() {
    return Arrays.asList(true, false);
  }

  private static Iterable<SoyGeneralOptions> allOptions() {
    List<SoyGeneralOptions> allOptions = new ArrayList<>();
    for (boolean enableNonNullAssertionOperator : bools()) {
      SoyGeneralOptions options = new SoyGeneralOptions();
      if (enableNonNullAssertionOperator) {
        options.setExperimentalFeatures(Arrays.asList("enableNonNullAssertionOperator"));
      }
      allOptions.add(options);
    }
    return allOptions;
  }

  private static <T extends CompilerPass> ImmutableList<String> names(ImmutableList<T> passes) {
    return passes.stream().map(CompilerPass::name).collect(toImmutableList());
  }
}
