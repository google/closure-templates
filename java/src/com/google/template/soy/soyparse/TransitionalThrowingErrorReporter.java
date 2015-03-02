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

package com.google.template.soy.soyparse;

/**
 * {@link ErrorReporter} implementation that allows callers to give up and throw an exception
 * if any errors were encountered.
 * TODO(user): remove. Soy has traditionally thrown exceptions for every kind of error.
 * We are in the process of changing this to enable reporting of multiple errors, error recovery,
 * and so on. This class should be used solely during this transition, in call sites that do not
 * yet have a proper error manager to report errors to.
 *
 * @author brndn@google.com (Brendan Linn)
 */
@Deprecated
public final class TransitionalThrowingErrorReporter extends ErrorReporterImpl {
  public void throwIfErrorsPresent() {
    if (!errors.isEmpty()) {
      throw errors.get(0);
    }
  }
}
