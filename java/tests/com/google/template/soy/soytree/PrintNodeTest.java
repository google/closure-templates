/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.soytree;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprparse.SoyParsingContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for PrintNode.
 *
 */
@RunWith(JUnit4.class)
public final class PrintNodeTest {

  private static final SoyParsingContext FAIL = SoyParsingContext.exploding();

  @Test
  public void testPlaceholderMethods() throws SoySyntaxException {
    PrintNode pn =
        new PrintNode.Builder(0, true /* isImplicit */, SourceLocation.UNKNOWN)
            .exprText("$boo")
            .build(FAIL);
    assertThat(pn.genBasePhName()).isEqualTo("BOO");
    assertThat(pn.genSamenessKey())
        .isEqualTo(
            new PrintNode.Builder(4, true /* isImplicit */, SourceLocation.UNKNOWN)
                .exprText("$boo")
                .build(FAIL)
                .genSamenessKey());
    assertThat(pn.genSamenessKey())
        .isEqualTo(
            new PrintNode.Builder(4, true /* isImplicit */, SourceLocation.UNKNOWN)
                .exprText("  $boo  ")
                .build(FAIL)
                .genSamenessKey());

    pn =
        new PrintNode.Builder(0, true /* isImplicit */, SourceLocation.UNKNOWN)
            .exprText("$boo.foo")
            .build(FAIL);
    assertThat(pn.genBasePhName()).isEqualTo("FOO");
    assertThat(pn.genSamenessKey())
        .isNotEqualTo(
            new PrintNode.Builder(4, true /* isImplicit */, SourceLocation.UNKNOWN)
                .exprText("$boo")
                .build(FAIL)
                .genSamenessKey());

    pn =
        new PrintNode.Builder(0, true /* isImplicit */, SourceLocation.UNKNOWN)
            .exprText("$boo.foo")
            .build(FAIL);
    pn.addChild(
        new PrintDirectiveNode.Builder(0, "|insertWordBreaks", "8", SourceLocation.UNKNOWN)
            .build(FAIL));
    assertThat(pn.genBasePhName()).isEqualTo("FOO");
    assertThat(pn.genSamenessKey())
        .isNotEqualTo(
            new PrintNode.Builder(4, true /* isImplicit */, SourceLocation.UNKNOWN)
                .exprText("$boo.foo")
                .build(FAIL)
                .genSamenessKey());

    pn =
        new PrintNode.Builder(0, true /* isImplicit */, SourceLocation.UNKNOWN)
            .exprText("$boo['foo']")
            .build(FAIL);
    assertWithMessage("Fallback value expected.").that(pn.genBasePhName()).isEqualTo("XXX");

    pn =
        new PrintNode.Builder(0, true /* isImplicit */, SourceLocation.UNKNOWN)
            .exprText("$boo + $foo")
            .build(FAIL);
    assertWithMessage("Fallback value expected.").that(pn.genBasePhName()).isEqualTo("XXX");
  }

  @Test
  public void testToSourceString() {
    PrintNode pn =
        new PrintNode.Builder(0, true /* isImplicit */, SourceLocation.UNKNOWN)
            .exprText("$boo")
            .build(FAIL);
    assertThat(pn.toSourceString()).isEqualTo("{$boo}");

    pn =
        new PrintNode.Builder(0, false /* isImplicit */, SourceLocation.UNKNOWN)
            .exprText("$boo")
            .build(FAIL);
    assertThat(pn.toSourceString()).isEqualTo("{print $boo}");
  }
}
