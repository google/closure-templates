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

package com.google.template.soy.jssrc.internal;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcPrintDirective;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.soytree.SoyNode;

import junit.framework.TestCase;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unit tests for GenDirectivePluginRequiresVisitor.
 *
 */
public class GenDirectivePluginRequiresVisitorTest extends TestCase {


  private static class TestPrintDirective implements SoyLibraryAssistedJsSrcPrintDirective {
    @Override public ImmutableSet<String> getRequiredJsLibNames() {
      return ImmutableSet.of("test.closure.name");
    }

    @Override public String getName() {
      return "|test";
    }

    @Override public boolean shouldCancelAutoescape() {
      return false;
    }

    @Override public Set<Integer> getValidArgsSizes() {
      return ImmutableSet.of(0);
    }

    @Override public JsExpr applyForJsSrc(JsExpr value, List<JsExpr> args) {
      throw new UnsupportedOperationException();
    }
  }


  private static class AnotherTestPrintDirective implements SoyLibraryAssistedJsSrcPrintDirective {
    @Override public ImmutableSet<String> getRequiredJsLibNames() {
      return ImmutableSet.of("another.closure.name");
    }

    @Override public String getName() {
      return "|another";
    }

    @Override public boolean shouldCancelAutoescape() {
      return false;
    }

    @Override public Set<Integer> getValidArgsSizes() {
      return ImmutableSet.of(0);
    }

    @Override public JsExpr applyForJsSrc(JsExpr value, List<JsExpr> args) {
      throw new UnsupportedOperationException();
    }
  }

  /** Map of all SoyLibraryAssistedJsSrcPrintDirectives */
  private static final Map<String, SoyLibraryAssistedJsSrcPrintDirective> testDirectivesMap =
      ImmutableMap.of("|test", new TestPrintDirective(),
                      "|another", new AnotherTestPrintDirective());


  public void testUnmatchedDirective() {
    assertGeneratedLibs(ImmutableSet.<String>of(), "{@param boo : ?}", "{$boo |goosfraba}\n");
  }


  public void testSingleDirective() {
    assertGeneratedLibs(ImmutableSet.of("test.closure.name"), "{@param boo : ?}", "{$boo |test}\n");
  }


  public void testMultipleDirectives() {
    assertGeneratedLibs(
        ImmutableSet.of("test.closure.name", "another.closure.name"),
        "{@param boo : ?}",
        "{@param goo : ?}",
        "{$boo |test}\n{$goo |another}\n");
  }


  private static void assertGeneratedLibs(Set<String> expectedLibs, String... soyCodeLines) {
    SoyNode node =
        SharedTestUtils.getNode(
            SoyFileSetParserBuilder.forTemplateContents(Joiner.on('\n').join(soyCodeLines))
                .parse()
                .fileSet());
    GenDirectivePluginRequiresVisitor gdprv =
        new GenDirectivePluginRequiresVisitor(testDirectivesMap);
    Set<String> actualLibs = gdprv.exec(node);
    assertThat(expectedLibs).isEqualTo(actualLibs);
  }

}
