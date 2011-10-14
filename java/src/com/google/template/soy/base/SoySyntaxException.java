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

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNullableByDefault;

/**
 * Exception for Soy syntax errors.
 *
 * @author Kai Huang
 */
@ParametersAreNullableByDefault
public class SoySyntaxException extends RuntimeException {


  /** The location in the soy file at which the error occurred. */
  private SourceLocation sourceLocation = SourceLocation.UNKNOWN;

  /** The name of the template with the syntax error if any. */
  private String templateName;


  /**
   * @param message A detailed description of what the syntax error is.
   */
  public SoySyntaxException(String message) {
    super(message);
  }


  /**
   * @param message A detailed description of what the syntax error is.
   * @param cause The Throwable underlying this syntax error.
   */
  public SoySyntaxException(String message, Throwable cause) {
    super(message, cause);
  }


  /**
   * Note: For this constructor, the message will be set to the cause's message.
   * @param cause The Throwable underlying this syntax error.
   */
  public SoySyntaxException(Throwable cause) {
    super(cause.getMessage(), cause);
  }


  /**
   * The source location at which the error occurred or {@link SourceLocation#UNKNOWN}.
   */
  public SourceLocation getSourceLocation() {
    return sourceLocation;
  }


  /**
   * The name of the template in which the problem occurred or {@code null} if not known.
   */
  public @Nullable String getTemplateName() {
    return templateName;
  }


  /**
   * Sets the file name for this SoySyntaxException instance.
   * @param filePath The path of the file containing the syntax error.
   * @return This same instance.
   */
  public SoySyntaxException setFilePath(String filePath) {
    return setSourceLocation(new SourceLocation(filePath, 0));
  }


  /**
   * Sets the source location at which the problem occurred.
   * @return This same instance.
   */
  public SoySyntaxException setSourceLocation(SourceLocation sourceLocation) {
    this.sourceLocation = sourceLocation;
    return this;
  }


  /**
   * Sets the template name for this SoySyntaxException instance.
   * @param templateName The name of the template containing the syntax error.
   * @return This same instance.
   */
  public SoySyntaxException setTemplateName(String templateName) {
    this.templateName = templateName;
    return this;
  }


  @Override public String getMessage() {
    boolean locationKnown = sourceLocation.isKnown();
    boolean templateKnown = templateName != null;
    String message = super.getMessage();
    if (locationKnown) {
      if (templateKnown) {
        return "In file " + sourceLocation + ", template " + templateName + ": " + message;
      } else {
        return "In file " + sourceLocation + ": " + message;
      }
    } else if (templateKnown) {
      return "In template " + templateName + ": " + message;
    } else {
      return message;
    }
  }

  
  /**
   * @return The original error message from the Soy compiler without any
   *     metadata about the location where the error appears.  
   */
  public String getOriginalMessage() {
    return super.getMessage();
  }
}
