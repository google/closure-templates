/*
 * Copyright 2014 Google Inc.
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

package com.google.template.soy.sharedpasses;

import com.google.template.soy.base.SourceLocation;

/**
 * Exception thrown when a call is made to a template that is not visible
 * from the call site (for example, calling a template marked
 * {@code visibility="private"} is called from a template in another file).
 *
 * @author brndn@google.com (Brendan Linn)
 */
public class VisibilityException extends RuntimeException {

  // TODO(brndn): make a nice error message from these.
  private final SourceLocation callSite;
  private final SourceLocation definitionSite;

  public VisibilityException(
      SourceLocation callSite, SourceLocation definitionSite) {
    this.callSite = callSite;
    this.definitionSite = definitionSite;
  }
}

