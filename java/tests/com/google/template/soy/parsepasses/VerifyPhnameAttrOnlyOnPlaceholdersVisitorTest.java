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

package com.google.template.soy.parsepasses;

import static com.google.common.truth.Truth.assertThat;

import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.soytree.SoyFileSetNode;

import junit.framework.TestCase;

/**
 * Unit tests for VerifyPhnameAttrOnlyOnPlaceholdersVisitor.
 *
 */
public final class VerifyPhnameAttrOnlyOnPlaceholdersVisitorTest extends TestCase {

  public void testVerifyPhnameAttrOnlyOnPlaceholders() {
    assertInvalidSoyCode("{$boo phname=\"foo\"}");
    assertInvalidSoyCode("{call .helper phname=\"foo\" /}");
    assertValidSoyCode("{msg desc=\"\"}{$boo phname=\"foo\"}{/msg}");
    assertValidSoyCode("{msg desc=\"\"}{call .helper phname=\"foo\" /}{/msg}");
  }

  private void assertValidSoyCode(String soyCode) {
    ErrorReporter boom = ExplodingErrorReporter.get();
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forTemplateContents(soyCode)
        .errorReporter(boom)
        .parse();
    new VerifyPhnameAttrOnlyOnPlaceholdersVisitor(boom).exec(soyTree);
  }

  private void assertInvalidSoyCode(String soyCode) {
    ErrorReporter boom = ExplodingErrorReporter.get();
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forTemplateContents(soyCode)
        .errorReporter(boom)
        .parse();
    try {
      new VerifyPhnameAttrOnlyOnPlaceholdersVisitor(boom).exec(soyTree);
      fail();
    } catch (SoySyntaxException sse) {
      // TODO(user): even though the visitor has an error reporter, it doesn't *use* the error
      // reporter yet. Remove this try-catch once it does.
      assertThat(sse.getMessage()).contains("Found 'phname' attribute not on a msg placeholder");
    }
  }
}
