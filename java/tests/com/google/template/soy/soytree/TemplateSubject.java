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

import com.google.auto.value.AutoValue;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.common.truth.Truth;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.FixedIdGenerator;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.error.AbstractErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soyparse.SoyFileParser;
import com.google.template.soy.types.SoyTypeRegistry;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Truth custom subject for testing templates.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public final class TemplateSubject extends Subject<TemplateSubject, String> {

  private SourceLocation actualSourceLocation;

  private static final SubjectFactory<TemplateSubject, String> FACTORY
      = new SubjectFactory<TemplateSubject, String>() {
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

  public TemplateSubject causesError(SoyErrorKind error) {
    ErrorReporterImpl errorReporter = doParse();
    Report report = errorReporter.getFirstReport(error);
    if (report == null) {
      failWithRawMessage(
          "%s should have failed to parse with <%s>, instead had errors: %s",
          getDisplaySubject(),
          error,
          errorReporter.reports);
    }
    actualSourceLocation = report.location();
    return this;
  }

  public TemplateSubject causesError(String message) {
    ErrorReporterImpl errorReporter = doParse();
    Report report = errorReporter.getFirstReport(message);
    if (report == null) {
      failWithRawMessage(
          "%s should have failed to parse with <%s>, instead had errors: %s",
          getDisplaySubject(),
          message,
          errorReporter.reports);
    }
    actualSourceLocation = report.location();
    return this;
  }

  public void isWellFormed() {
    ErrorReporterImpl errorReporter = doParse();
    Truth.assertThat(errorReporter.reports).isEmpty();
  }

  public void isNotWellFormed() {
    ErrorReporterImpl errorReporter = doParse();
    Truth.assertThat(errorReporter.reports).isNotEmpty();
  }

  private ErrorReporterImpl doParse() {
    ErrorReporterImpl errorReporter = new ErrorReporterImpl();
    try {
      new SoyFileParser(
            new SoyTypeRegistry(),
            new FixedIdGenerator(),
            new StringReader("{namespace test}{template .foo}\n" + getSubject() + "{/template}"),
            SoyFileKind.SRC,
            "example.soy",
            errorReporter)
        .parseSoyFile();
    } catch (Throwable e) {
      errorReporter.report(SourceLocation.UNKNOWN, SoyErrorKind.of("{0}"), e.getMessage());
    }
    return errorReporter;
  }

  public void at(int expectedLine, int expectedColumn) {
    expectedLine++;  // Compensate for the extra line of template wrapper
    if (expectedLine != actualSourceLocation.getLineNumber()
        || expectedColumn != actualSourceLocation.getBeginColumn()) {
      failWithRawMessage(
          String.format("expected error to point to %d:%d, but it actually points to %d:%d",
              expectedLine,
              expectedColumn,
              actualSourceLocation.getLineNumber(),
              actualSourceLocation.getBeginColumn()));
    }
  }

  private static final class ErrorReporterImpl extends AbstractErrorReporter {

    private final List<Report> reports = new ArrayList<>();

    @Override
    public void report(SourceLocation sourceLocation, SoyErrorKind error, Object... args) {
      reports.add(new AutoValue_TemplateSubject_Report(error, sourceLocation, error.format(args)));
    }

    Report getFirstReport(SoyErrorKind kind) {
      for (Report report : reports) {
        if (report.kind() == kind) {
          return report;
        }
      }
      return null;
    }

    Report getFirstReport(String message) {
      for (Report report : reports) {
        if (report.formatted().equals(message)) {
          return report;
        }
      }
      return null;
    }

    @Override
    protected int getCurrentNumberOfErrors() {
      return reports.size();
    }
  }

  @AutoValue
  abstract static class Report {
    abstract SoyErrorKind kind();

    abstract SourceLocation location();

    abstract String formatted();
  }
}
