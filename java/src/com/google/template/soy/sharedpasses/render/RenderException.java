/*
 * Copyright 2011 Google Inc.
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

package com.google.template.soy.sharedpasses.render;

import javax.annotation.Nullable;


/**
 * Exception thrown when a rendering or evaluation attempt fails.
 *
 * @author Kai Huang
 */
public class RenderException extends RuntimeException {


  /** The name of the template with the syntax error if any. */
  private String templateName;


  /**
   * @param message A detailed description of the error.
   */
  public RenderException(String message) {
    super(message);
    this.templateName = null;
  }


  /**
   * The name of the template in which the problem occurred or {@code null} if not known.
   */
  public @Nullable String getTemplateName() {
    return templateName;
  }


  /**
   * Sets the template name for this error.
   * @param templateName The name of the template containing this error.
   * @return This same instance.
   */
  public RenderException setTemplateName(String templateName) {
    this.templateName = templateName;
    return this;
  }


  /**
   * Returns the message without the templateName added, even if templateName is known.
   */
  public String getRawMessage() {
    return super.getMessage();
  }


  @Override public String getMessage() {
    return
        ((templateName != null) ? "In template " + templateName + ": " : "") + super.getMessage();
  }

}
