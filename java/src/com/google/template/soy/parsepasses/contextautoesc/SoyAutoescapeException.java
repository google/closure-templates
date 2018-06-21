/*
 * Copyright 2010 Google Inc.
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

package com.google.template.soy.parsepasses.contextautoesc;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.TemplateNode;
import javax.annotation.Nullable;

/**
 * Indicates failure to propagate contexts through a template or an existing escaping directive on a
 * 'print' tag that is inconsistent with the contexts in which it appears.
 */
final class SoyAutoescapeException extends RuntimeException {
  /**
   * Creates a SoyAutoescapeException, with meta info filled in based on the given Soy node.
   *
   * @param message The error message.
   * @param node The node from which to derive the exception meta info.
   * @return The new SoyAutoescapeException object.
   */
  static SoyAutoescapeException createWithNode(String message, SoyNode node) {
    return new SoyAutoescapeException(message, /*cause=*/ null, node);
  }

  /**
   * Creates a SoyAutoescapeException, with meta info filled in based on the given Soy node.
   *
   * @param message The error message.
   * @param cause The cause of this exception.
   * @param node The node from which to derive the exception meta info.
   * @return The new SoyAutoescapeException object.
   */
  static SoyAutoescapeException createCausedWithNode(
      String message, Throwable cause, SoyNode node) {
    return new SoyAutoescapeException(message, cause, node);
  }

  /** The location in the soy file at which the error occurred. */
  private final SourceLocation srcLoc;

  /** The name of the template with the syntax error if any. */
  @Nullable private final String templateName;

  private SoyAutoescapeException(String message, Throwable cause, SoyNode node) {
    super(message, cause);
    // If srcLoc not yet set, then set it, else assert existing value equals new value.
    this.srcLoc = node.getSourceLocation();

    TemplateNode template = node.getNearestAncestor(TemplateNode.class);
    // TemplateName not existing is just for unit tests
    this.templateName = (template != null) ? template.getTemplateNameForUserMsgs() : null;
  }

  /** The source location at which the error occurred or {@link SourceLocation#UNKNOWN}. */
  SourceLocation getSourceLocation() {
    return srcLoc;
  }

  /** Returns the original exception message, without any source location formatting. */
  String getOriginalMessage() {
    return super.getMessage();
  }

  @Override
  public String getMessage() {
    boolean templateKnown = templateName != null;
    String message = super.getMessage();
    if (templateKnown) {
      return "In file " + srcLoc + ", template " + templateName + ": " + message;
    } else {
      return "In file " + srcLoc + ": " + message;
    }
  }
}
