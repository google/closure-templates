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

import com.google.common.base.Preconditions;
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
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ErrorReporter.Checkpoint;
import com.google.template.soy.error.SoyError;
import com.google.template.soy.soyparse.ParseException;
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
    ErrorReporterImpl errorReporter = new ErrorReporterImpl();
    try {
      new TemplateParser(new FixedIdGenerator(), getSubject(), "example.soy", 1, 0, errorReporter)
          .parseTemplateContent();
    } finally {
      Truth.assertThat(errorReporter.locationsForError.keySet()).contains(error);
      actualSourceLocation = Iterables.getFirst(errorReporter.locationsForError.get(error), null);
      return this;
    }
  }

  public void isWellFormed() {
    ErrorReporterImpl errorReporter = new ErrorReporterImpl();
    try {
      new TemplateParser(new FixedIdGenerator(), getSubject(), "example.soy", 1, 0, errorReporter)
          .parseTemplateContent();
    } catch (ParseException e) {
      throw Throwables.propagate(e);
    }
    Truth.assertThat(errorReporter.locationsForError).isEmpty();
  }

  public void isNotWellFormed() {
    ErrorReporterImpl errorReporter = new ErrorReporterImpl();
    try {
      new TemplateParser(new FixedIdGenerator(), getSubject(), "example.soy", 1, 0, errorReporter)
          .parseTemplateContent();
    } catch (Throwable e) {
      return; // expected
    }
    Truth.assertThat(errorReporter.locationsForError).isNotEmpty();
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

  private static final class ErrorReporterImpl implements ErrorReporter {

    private final List<SoyError> soyErrors = new ArrayList<>();
    private final ListMultimap<SoyError, SourceLocation> locationsForError
        = ArrayListMultimap.create();

    @Override
    public Checkpoint checkpoint() {
      return new CheckpointImpl(soyErrors.size());
    }

    @Override
    public boolean errorsSince(Checkpoint checkpoint) {
      Preconditions.checkArgument(checkpoint instanceof CheckpointImpl);
      return soyErrors.size() > ((CheckpointImpl) checkpoint).numErrors;
    }

    @Override
    public void report(SourceLocation sourceLocation, SoyError error, Object... args) {
      soyErrors.add(error);
      locationsForError.put(error, sourceLocation);
    }
  }

  private static final class CheckpointImpl implements Checkpoint {
    private final int numErrors;

    CheckpointImpl(int numErrors) {
      this.numErrors = numErrors;
    }
  }
}
