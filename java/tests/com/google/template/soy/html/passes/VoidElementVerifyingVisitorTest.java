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

package com.google.template.soy.html.passes;

import static com.google.common.truth.Truth.assertThat;

import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.FormattingErrorReporter;
import com.google.template.soy.shared.AutoEscapingType;
import com.google.template.soy.soytree.SoyFileSetNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link VoidElementVerifyingVisitor}. */
@RunWith(JUnit4.class)
public final class VoidElementVerifyingVisitorTest {
  private static final String ERROR_MSG =
      "Closing tag for a void HTML Element was not "
          + "immediately preceeded by an open tag for the same element. Void HTML Elements are not "
          + "allowed to have any content. See: "
          + "http://www.w3.org/TR/html-markup/syntax.html#void-element";

  private static SoyFileSetNode performVisitor(String templateBody, ErrorReporter er) {
    SoyFileSetNode sfsn =
        SoyFileSetParserBuilder.forTemplateContents(AutoEscapingType.STRICT, templateBody)
            .parse()
            .fileSet();

    new HtmlTransformVisitor(er).exec(sfsn);
    new VoidElementVerifyingVisitor(er).exec(sfsn);

    return sfsn;
  }

  @Test
  public void testNonVoidElement() {
    String templateBody = "{@param foo : ?}\n" + "<span></span>{if $foo}<div>{/if}</div>";

    FormattingErrorReporter fer = new FormattingErrorReporter();
    performVisitor(templateBody, fer);

    assertThat(fer.getErrorMessages()).isEmpty();
  }

  @Test
  public void testNoClosingTag() {
    String templateBody = "<input><div></div>";

    FormattingErrorReporter fer = new FormattingErrorReporter();
    performVisitor(templateBody, fer);

    assertThat(fer.getErrorMessages()).isEmpty();
  }

  @Test
  public void testClosingTagWithNoContent() {
    String templateBody = "<input></input>";

    FormattingErrorReporter fer = new FormattingErrorReporter();
    performVisitor(templateBody, fer);

    assertThat(fer.getErrorMessages()).isEmpty();
  }

  @Test
  public void testAtStartOfBlock() {
    String templateBody = "{@param foo : ?}\n<input>{if $foo}</input>{/if}";

    FormattingErrorReporter fer = new FormattingErrorReporter();
    performVisitor(templateBody, fer);

    assertThat(fer.getErrorMessages()).contains(ERROR_MSG);
  }

  @Test
  public void testVoidElementWithContent() {
    String templateBody = "<input>Not valid</input>";

    FormattingErrorReporter fer = new FormattingErrorReporter();
    performVisitor(templateBody, fer);

    assertThat(fer.getErrorMessages()).containsExactly(ERROR_MSG);
  }

  @Test
  public void testMultipleErrors() {
    String templateBody = "<input>Not valid</input></input>";

    FormattingErrorReporter fer = new FormattingErrorReporter();
    performVisitor(templateBody, fer);

    assertThat(fer.getErrorMessages()).containsExactly(ERROR_MSG, ERROR_MSG);
  }
}
