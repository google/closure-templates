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
 * Exception for Soy syntax errors.
 *
 * @author Kai Huang
 */
public class SoySyntaxException extends RuntimeException {


  /** The path of the Soy file with the syntax error. */
  public String filePath;

  /** The name of the template with the syntax error. */
  public String templateName;


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
   * Sets the file name for this SoySyntaxException instance.
   * @param filePath The path of the file containing the syntax error.
   * @return This same instance.
   */
  public SoySyntaxException setFilePath(String filePath) {
    this.filePath = filePath;
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
    if (filePath != null && templateName != null) {
      return "In file " + filePath + ", template " + templateName + ": " + super.getMessage();
    } else if (filePath != null) {
      return "In file " + filePath + ": " + super.getMessage();
    } else {
      return super.getMessage();
    }
  }

}
