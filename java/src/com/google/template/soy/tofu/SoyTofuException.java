/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.tofu;

import com.google.template.soy.data.SoyFutureException;
import com.google.template.soy.sharedpasses.render.RenderException;

/**
 * Exception thrown when an error occurs during template rendering.
 *
 * <p>There are several kinds of errors that you might encounter:
 *
 * <ul>
 *   <li>Type errors. Thrown if the static type of a variable does not match the runtime type.
 *   <li>Plugin errors. Errors thrown from functions will be wrapped in SoyTofuException with the
 *       original exception maintained in the {@link Throwable#getCause() cause} field.
 *   <li>Future errors. Errors thrown when dereferencing {@link Future} instances passed as
 *       parameters. In this case, the failure cause will be {@link SoyFutureException}, with the
 *       failure cause attached to that.
 *   <li>TODO(lukes): fill in more examples
 * </ul>
 *
 */
public class SoyTofuException extends RuntimeException {

  /** @param message A detailed description of the error. */
  public SoyTofuException(String message) {
    super(message);
  }

  /**
   * Creates an instance by copying a RenderException.
   *
   * @param re The RenderException to copy.
   */
  public SoyTofuException(RenderException re) {
    super(re.getMessage(), re.getCause());
    // At this point, the stack trace aggregation logic in RenderException can be considered done.
    // Set the stack trace of both the current SoyTofuException class as well as the
    // RenderException class.
    re.finalizeStackTrace(this);
    // Maintain suppressed exceptions.
    for (Throwable suppressed : re.getSuppressed()) {
      addSuppressed(suppressed);
    }
  }
}
