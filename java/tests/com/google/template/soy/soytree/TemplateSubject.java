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

package com.google.template.soy.soytree;

import static com.google.common.base.Strings.lenientFormat;
import static com.google.common.truth.Fact.simpleFact;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyError;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.testing.SharedTestUtils;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import javax.annotation.Nullable;

/**
 * Truth custom subject for testing templates.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public final class TemplateSubject extends Subject {

  private final String actual;
  private final boolean isTemplateContent;
  private SourceLocation actualSourceLocation;
  private SoyFileNode fileNode;

  static TemplateSubject newForTemplate(FailureMetadata failureMetadata, String s) {
    return new TemplateSubject(failureMetadata, s, true);
  }

  static TemplateSubject newForFile(FailureMetadata failureMetadata, String s) {
    return new TemplateSubject(failureMetadata, s, false);
  }

  private TemplateSubject(FailureMetadata failureMetadata, String s, boolean isTemplateContent) {
    super(failureMetadata, s);
    this.actual = s;
    this.isTemplateContent = isTemplateContent;
  }

  public static TemplateSubject assertThatTemplateContent(String input) {
    return assertAbout(TemplateSubject::newForTemplate).that(input);
  }

  public static TemplateSubject assertThatFileContent(String input) {
    return assertAbout(TemplateSubject::newForFile).that(input);
  }

  public TemplateSubject causesError(SoyErrorKind error) {
    ErrorReporter errorReporter = doParse();
    SoyError report = getFirstReport(error, errorReporter);
    if (report == null) {
      failWithoutActual(
          simpleFact(
              lenientFormat(
                  "<%s> should have failed to parse with <%s>, instead had errors: %s",
                  actual, error, errorReporter.getErrors())));
    }
    actualSourceLocation = report.location();
    return this;
  }

  public TemplateSubject causesError(String message) {
    ErrorReporter errorReporter = doParse();
    SoyError report = getFirstReport(message, errorReporter);
    if (report == null) {
      failWithoutActual(
          simpleFact(
              lenientFormat(
                  "<%s> should have failed to parse with <%s>, instead had errors: %s",
                  actual, message, errorReporter.getErrors())));
    }
    actualSourceLocation = report.location();
    return this;
  }

  public void at(int expectedLine, int expectedColumn) {
    expectedLine += 3; // Compensate for the extra lines of template wrapper
    if (expectedLine != actualSourceLocation.getBeginLine()
        || expectedColumn != actualSourceLocation.getBeginColumn()) {
      failWithoutActual(
          simpleFact(
              String.format(
                  "expected error to point to %d:%d, but it actually points to %d:%d",
                  expectedLine,
                  expectedColumn,
                  actualSourceLocation.getBeginLine(),
                  actualSourceLocation.getBeginColumn())));
    }
  }

  public TemplateNode getTemplateNode() {
    return getTemplateNode(0);
  }

  public TemplateNode getTemplateNode(int index) {
    isWellFormed();
    Preconditions.checkNotNull(fileNode);
    return fileNode.getTemplates().get(index);
  }

  public void isWellFormed() {
    ErrorReporter errorReporter = doParse();
    assertWithMessage("the template parsed successfully").that(errorReporter.getErrors()).isEmpty();
  }

  public void isNotWellFormed() {
    ErrorReporter errorReporter = doParse();
    assertThat(errorReporter.hasErrors()).isTrue();
  }

  private ErrorReporter doParse() {
    String content =
        isTemplateContent
            ? SharedTestUtils.buildTestSoyFileContent(actual)
            : (actual.contains("{namespace ") ? actual : SharedTestUtils.NS + actual);
    ErrorReporter errorReporter = ErrorReporter.create(ImmutableMap.of());
    SoyFileSetNode fileSet =
        SoyFileSetParserBuilder.forFileContents(content)
            .errorReporter(errorReporter)
            .parse()
            .fileSet();
    fileNode = fileSet.numChildren() == 1 ? fileSet.getChild(0) : null;
    return errorReporter;
  }

  @Nullable
  private static SoyError getFirstReport(SoyErrorKind errorKind, ErrorReporter reporter) {
    for (SoyError error : reporter.getErrors()) {
      if (error.errorKind().equals(errorKind)) {
        return error;
      }
    }
    return null;
  }

  @Nullable
  private static SoyError getFirstReport(String message, ErrorReporter reporter) {
    for (SoyError error : reporter.getErrors()) {
      if (error.message().equals(message)) {
        return error;
      }
    }
    return null;
  }
}
