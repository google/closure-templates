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

import com.google.common.base.Preconditions;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.TemplateNode;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Indicates failure to propagate contexts through a template or an existing escaping directive on a
 * 'print' tag that is inconsistent with the contexts in which it appears.
 */
final class SoyAutoescapeException extends SoySyntaxException {

  /**
   * Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param message The error message.
   * @return The new SoyAutoescapeException object.
   * @deprecated
   */
  @Deprecated
  static SoyAutoescapeException createWithoutMetaInfo(String message) {
    return new SoyAutoescapeException(message);
  }

  /**
   * Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param message The error message, or null to use the message from the cause.
   * @param cause The cause of this exception.
   * @return The new SoyAutoescapeException object.
   * @deprecated
   */
  @Deprecated
  static SoyAutoescapeException createCausedWithoutMetaInfo(
      @Nullable String message, Throwable cause) {

    Preconditions.checkNotNull(cause);
    if (message != null) {
      return new SoyAutoescapeException(message, cause);
    } else {
      return new SoyAutoescapeException(cause);
    }
  }

  /**
   * Creates a SoyAutoescapeException, with meta info filled in based on the given Soy node.
   *
   * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param message The error message.
   * @param node The node from which to derive the exception meta info.
   * @return The new SoyAutoescapeException object.
   */
  static SoyAutoescapeException createWithNode(String message, SoyNode node) {

    return SoyAutoescapeException.createWithoutMetaInfo(message).associateNode(node);
  }

  /**
   * Creates a SoyAutoescapeException, with meta info filled in based on the given Soy node.
   *
   * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param message The error message, or null to use the message from the cause.
   * @param cause The cause of this exception.
   * @param node The node from which to derive the exception meta info.
   * @return The new SoyAutoescapeException object.
   */
  static SoyAutoescapeException createCausedWithNode(
      @Nullable String message, Throwable cause, SoyNode node) {

    return SoyAutoescapeException.createCausedWithoutMetaInfo(message, cause).associateNode(node);
  }

  /** The location in the soy file at which the error occurred. */
  private SourceLocation srcLoc = SourceLocation.UNKNOWN;

  /** The name of the template with the syntax error if any. */
  private String templateName;

  /**
   * Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param message A detailed description of what the error is.
   */
  @SuppressWarnings({"deprecation"})
  private SoyAutoescapeException(String message) {
    super(message);
  }

  /**
   * Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param message A detailed description of what the error is.
   * @param cause The Throwable underlying this error.
   */
  private SoyAutoescapeException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * <p>Note: For this constructor, the message will be set to the cause's message.
   *
   * @param cause The Throwable underlying this error.
   */
  private SoyAutoescapeException(Throwable cause) {
    super(cause.getMessage(), cause);
  }

  /**
   * Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param node The node from which to derive the exception meta info.
   * @return This same SoyAutoescapeException object, for convenience.
   */
  SoyAutoescapeException associateNode(SoyNode node) {
    TemplateNode template = node.getNearestAncestor(TemplateNode.class);
    // This special case is just for unit tests
    SourceLocation nodeLocation = node.getSourceLocation();
    // If srcLoc not yet set, then set it, else assert existing value equals new value.
    if (!this.srcLoc.isKnown()) {
      this.srcLoc = nodeLocation;
    } else {
      Preconditions.checkState(this.srcLoc.equals(nodeLocation));
    }

    String templateName = (template != null) ? template.getTemplateNameForUserMsgs() : null;
    if (templateName != null) {
      // If templateName not yet set, then set it, else assert existing value equals new value.
      if (this.templateName == null) {
        this.templateName = templateName;
      } else {
        Preconditions.checkState(this.templateName.equals(templateName));
      }
    }
    return this;
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
    boolean locationKnown = srcLoc.isKnown();
    boolean templateKnown = templateName != null;
    String message = super.getMessage();
    if (locationKnown) {
      if (templateKnown) {
        return "In file " + srcLoc + ", template " + templateName + ": " + message;
      } else {
        return "In file " + srcLoc + ": " + message;
      }
    } else if (templateKnown) {
      return "In template " + templateName + ": " + message;
    } else {
      return message;
    }
  }

  /**
   * Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param node The node from which to derive the exception meta info.
   * @return This same SoyAutoescapeException object, for convenience.
   */
  SoyAutoescapeException maybeAssociateNode(SoyNode node) {
    if (Objects.equals(srcLoc, SourceLocation.UNKNOWN)) {
      associateNode(node);
    }
    return this;
  }
}
