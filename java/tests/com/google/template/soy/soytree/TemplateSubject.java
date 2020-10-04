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
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyError;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import javax.annotation.Nullable;

/**
 * Truth custom subject for testing templates.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public final class TemplateSubject extends Subject {

  private final String actual;
  private SourceLocation actualSourceLocation;
  private SoyFileNode fileNode;

  TemplateSubject(FailureMetadata failureMetadata, String s) {
    super(failureMetadata, s);
    this.actual = s;
  }

  public static TemplateSubject assertThatTemplateContent(String input) {
    return Truth.assertAbout(TemplateSubject::new).that(input);
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
    expectedLine += 2; // Compensate for the extra lines of template wrapper
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
    isWellFormed();
    Preconditions.checkNotNull(fileNode);
    Preconditions.checkArgument(fileNode.numChildren() == 1);
    return (TemplateNode) fileNode.getChild(0);
  }

  public void isWellFormed() {
    ErrorReporter errorReporter = doParse();
    assertWithMessage("the template parsed successfully").that(errorReporter.getErrors()).isEmpty();
  }

  public void isNotWellFormed() {
    ErrorReporter errorReporter = doParse();
    Truth.assertThat(errorReporter.hasErrors()).isTrue();
  }

  private ErrorReporter doParse() {
    SoyFileSupplier sourceFile =
        SoyFileSupplier.Factory.create(
            "{namespace test}\n" + "{template .foo}\n" + actual + "\n" + "{/template}",
            SourceFilePath.create("example.soy"));
    ErrorReporter errorReporter =
        ErrorReporter.create(ImmutableMap.of(sourceFile.getFilePath(), sourceFile));
    SoyFileSetNode fileSet =
        SoyFileSetParserBuilder.forSuppliers(sourceFile)
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
