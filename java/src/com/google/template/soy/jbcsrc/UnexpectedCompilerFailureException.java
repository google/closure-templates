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

package com.google.template.soy.jbcsrc;

import com.google.common.base.Joiner;
import com.google.template.soy.base.SourceLocation;

import java.util.ArrayDeque;

/**
 * A wrapper for an unexpected compilation failure. Allows for associating unexpected errors with
 * the soy source locations that led to them.
 */
final class UnexpectedCompilerFailureException extends RuntimeException {
  private final ArrayDeque<SourceLocation> compilationPath = new ArrayDeque<>();
  
  UnexpectedCompilerFailureException(SourceLocation original, Throwable cause) {
    super("unexpected compile failure", cause, false, false);
    compilationPath.add(original);
  }

  void addLocation(SourceLocation sourceLocation) {
    compilationPath.add(sourceLocation);
  }

  SourceLocation getOriginalLocation() {
    return compilationPath.getFirst();
  }

  String printSoyStack() {
    return Joiner.on('\n').join(compilationPath);
  }
}
