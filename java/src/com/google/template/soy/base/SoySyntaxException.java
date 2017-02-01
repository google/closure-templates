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

package com.google.template.soy.base;

/**
 * Common super type for Soy syntax errors.
 *
 */
public abstract class SoySyntaxException extends RuntimeException {

  protected SoySyntaxException() {
    super();
  }

  /**
   * Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param message A detailed description of what the syntax error is.
   * @deprecated Do not use outside of Soy code (treat as superpackage-private).
   */
  @Deprecated
  public SoySyntaxException(String message) {
    super(message);
  }

  /**
   * Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param message A detailed description of what the syntax error is.
   * @param cause The Throwable underlying this syntax error.
   */
  protected SoySyntaxException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * <p>Note: For this constructor, the message will be set to the cause's message.
   *
   * @param cause The Throwable underlying this syntax error.
   */
  protected SoySyntaxException(Throwable cause) {
    super(cause.getMessage(), cause);
  }
}
