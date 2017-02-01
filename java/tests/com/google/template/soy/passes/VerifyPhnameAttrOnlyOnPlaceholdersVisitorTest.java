/*
 * Copyright 2011 Google Inc.
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

import static com.google.common.truth.Truth.assertThat;

import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.error.FormattingErrorReporter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for VerifyPhnameAttrOnlyOnPlaceholdersVisitor.
 *
 */
@RunWith(JUnit4.class)
public final class VerifyPhnameAttrOnlyOnPlaceholdersVisitorTest {

  @Test
  public void testVerifyPhnameAttrOnlyOnPlaceholders() {
    assertInvalidSoyCode("{@param boo : ?}\n{$boo phname=\"foo\"}");
    assertInvalidSoyCode("{call .helper phname=\"foo\" /}");
    assertValidSoyCode("{@param boo : ?}\n{msg desc=\"\"}{$boo phname=\"foo\"}{/msg}");
    assertValidSoyCode("{msg desc=\"\"}{call .helper phname=\"foo\" /}{/msg}");
  }

  private void assertValidSoyCode(String soyCode) {
    // this pass is part of the default passes, so we can just fire away
    SoyFileSetParserBuilder.forTemplateContents(soyCode)
        .errorReporter(ExplodingErrorReporter.get())
        .parse();
  }

  private void assertInvalidSoyCode(String soyCode) {
    FormattingErrorReporter errors = new FormattingErrorReporter();
    SoyFileSetParserBuilder.forTemplateContents(soyCode).errorReporter(errors).parse();
    assertThat(errors.getErrorMessages())
        .contains("'phname' attributes are only valid inside '{msg...' tags");
  }
}
