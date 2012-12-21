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

import com.google.template.soy.sharedpasses.render.RenderException;

import javax.annotation.Nullable;


/**
 * Exception thrown when an error occurs during template rendering.
 *
 * @author Kai Huang
 */
public class SoyTofuException extends RuntimeException {


  /** The name of the template with the syntax error if any. */
  private String templateName;


  /**
   * @param message A detailed description of the error.
   */
  public SoyTofuException(String message) {
    super(message);
    this.templateName = null;
  }


  /**
   * @param message A detailed description of the error.
   * @param cause The underlying error.
   */
  public SoyTofuException(String message, Throwable cause) {
    super(message, cause);
    this.templateName = null;
  }


  /**
   * Creates an instance by copying a RenderException.
   * @param re The RenderException to copy.
   */
  public SoyTofuException(RenderException re) {
    super(re.getRawMessage(), re);
    this.templateName = re.getTemplateName();
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
  public SoyTofuException setTemplateName(String templateName) {
    this.templateName = templateName;
    return this;
  }


  @Override public String getMessage() {
    return
        ((templateName != null) ? "In template " + templateName + ": " : "") + super.getMessage();
  }

}
