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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.common.truth.Truth;
import com.google.template.soy.ErrorReporterImpl;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.FixedIdGenerator;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.error.PrettyErrorFactory;
import com.google.template.soy.error.SnippetFormatter;
import com.google.template.soy.error.SoyError;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soyparse.SoyFileParser;
import com.google.template.soy.types.SoyTypeRegistry;
import java.io.IOException;
import javax.annotation.Nullable;

/**
 * Truth custom subject for testing templates.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public final class TemplateSubject extends Subject<TemplateSubject, String> {

  private SourceLocation actualSourceLocation;
  private ContentKind kind = ContentKind.HTML;

  private static final SubjectFactory<TemplateSubject, String> FACTORY =
      new SubjectFactory<TemplateSubject, String>() {
        @Override
        public TemplateSubject getSubject(FailureStrategy failureStrategy, String s) {
          return new TemplateSubject(failureStrategy, s);
        }
      };

  TemplateSubject(FailureStrategy failureStrategy, String s) {
    super(failureStrategy, s);
  }

  public static TemplateSubject assertThatTemplateContent(String input) {
    return Truth.assertAbout(FACTORY).that(input);
  }

  public TemplateSubject withKind(ContentKind kind) {
    this.kind = checkNotNull(kind);
    return this;
  }

  public TemplateSubject causesError(SoyErrorKind error) {
    ErrorReporterImpl errorReporter = doParse();
    SoyError report = getFirstReport(error, errorReporter);
    if (report == null) {
      failWithRawMessage(
          "%s should have failed to parse with <%s>, instead had errors: %s",
          actualAsString(), error, errorReporter.getErrors());
    }
    actualSourceLocation = report.location();
    return this;
  }

  public TemplateSubject causesError(String message) {
    ErrorReporterImpl errorReporter = doParse();
    SoyError report = getFirstReport(message, errorReporter);
    if (report == null) {
      failWithRawMessage(
          "%s should have failed to parse with <%s>, instead had errors: %s",
          actualAsString(), message, errorReporter.getErrors());
    }
    actualSourceLocation = report.location();
    return this;
  }

  public void isWellFormed() {
    ErrorReporterImpl errorReporter = doParse();
    Truth.assertThat(errorReporter.getErrors()).isEmpty();
  }

  public void isNotWellFormed() {
    ErrorReporterImpl errorReporter = doParse();
    Truth.assertThat(errorReporter.getErrors()).isNotEmpty();
  }

  private ErrorReporterImpl doParse() {
    SoyFileSupplier sourceFile =
        SoyFileSupplier.Factory.create(
            "{namespace test}\n{template .foo"
                + (kind != ContentKind.HTML ? " kind=\"" + kind.name().toLowerCase() + "\"" : "")
                + "}\n"
                + actual()
                + "\n{/template}",
            SoyFileKind.SRC,
            "example.soy");
    ErrorReporterImpl errorReporter =
        new ErrorReporterImpl(
            new PrettyErrorFactory(
                new SnippetFormatter(ImmutableMap.of(sourceFile.getFilePath(), sourceFile))));
    try {
      new SoyFileParser(
              new SoyTypeRegistry(),
              new FixedIdGenerator(),
              sourceFile.open(),
              sourceFile.getSoyFileKind(),
              sourceFile.getFilePath(),
              errorReporter)
          .parseSoyFile();
    } catch (IOException e) {
      throw new AssertionError(e); // impossible
    }
    return errorReporter;
  }

  public void at(int expectedLine, int expectedColumn) {
    expectedLine += 2; // Compensate for the extra lines of template wrapper
    if (expectedLine != actualSourceLocation.getBeginLine()
        || expectedColumn != actualSourceLocation.getBeginColumn()) {
      failWithRawMessage(
          String.format(
              "expected error to point to %d:%d, but it actually points to %d:%d",
              expectedLine,
              expectedColumn,
              actualSourceLocation.getBeginLine(),
              actualSourceLocation.getBeginColumn()));
    }
  }

  @Nullable
  private static SoyError getFirstReport(SoyErrorKind errorKind, ErrorReporterImpl reporter) {
    for (SoyError error : reporter.getErrors()) {
      if (error.errorKind().equals(errorKind)) {
        return error;
      }
    }
    return null;
  }

  @Nullable
  private static SoyError getFirstReport(String message, ErrorReporterImpl reporter) {
    for (SoyError error : reporter.getErrors()) {
      if (error.message().equals(message)) {
        return error;
      }
    }
    return null;
  }
}
