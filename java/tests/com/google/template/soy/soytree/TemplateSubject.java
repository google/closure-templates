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

import com.google.common.base.Throwables;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.common.truth.Truth;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.FixedIdGenerator;
import com.google.template.soy.soyparse.ErrorReporterImpl;
import com.google.template.soy.soyparse.ParseException;
import com.google.template.soy.soyparse.SoyError;
import com.google.template.soy.soyparse.TemplateParser;

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

  public TemplateSubject causesError(SoyError error) {
    ErrorReporter errorReporter = new ErrorReporter();
    try {
      new TemplateParser(new FixedIdGenerator(), getSubject(), "example.soy", 1, errorReporter)
          .parseTemplateContent();
    } catch (ParseException e) {
      throw Throwables.propagate(e);
    }

    Truth.assertThat(errorReporter.locationsForError.keySet()).contains(error);
    actualSourceLocation = Iterables.getFirst(errorReporter.locationsForError.get(error), null);
    return this;
  }

  public void at(int expectedLine, int expectedColumn) {
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

  private static final class ErrorReporter extends ErrorReporterImpl {

    private final List<SoyError> soyErrors = new ArrayList<>();
    private final ListMultimap<SoyError, SourceLocation> locationsForError
        = ArrayListMultimap.create();

    @Override
    public void report(SourceLocation sourceLocation, SoyError error, String... args) {
      super.report(sourceLocation, error, args);
      soyErrors.add(error);
      locationsForError.put(error, sourceLocation);
    }
  }
}
