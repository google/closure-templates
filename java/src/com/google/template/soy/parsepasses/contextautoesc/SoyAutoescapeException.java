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
import com.google.template.soy.soytree.SoySyntaxExceptionUtils;

import javax.annotation.Nullable;


/**
 * Indicates failure to propagate contexts through a template or an existing escaping directive on a
 * 'print' tag that is inconsistent with the contexts in which it appears.
 */
public final class SoyAutoescapeException extends SoySyntaxException {


  /**
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param message The error message.
   * @return The new SoyAutoescapeException object.
   */
  public static SoyAutoescapeException createWithoutMetaInfo(String message) {
    return new SoyAutoescapeException(message);
  }


  /**
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param message The error message, or null to use the message from the cause.
   * @param cause The cause of this exception.
   * @return The new SoyAutoescapeException object.
   */
  public static SoyAutoescapeException createCausedWithoutMetaInfo(
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
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param message The error message.
   * @param node The node from which to derive the exception meta info.
   * @return The new SoyAutoescapeException object.
   */
  public static SoyAutoescapeException createWithNode(String message, SoyNode node) {

    return SoyAutoescapeException.createWithoutMetaInfo(message).associateNode(node);
  }


  /**
   * Creates a SoyAutoescapeException, with meta info filled in based on the given Soy node.
   *
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param message The error message, or null to use the message from the cause.
   * @param cause The cause of this exception.
   * @param node The node from which to derive the exception meta info.
   * @return The new SoyAutoescapeException object.
   */
  public static SoyAutoescapeException createCausedWithNode(
      @Nullable String message, Throwable cause, SoyNode node) {

    return SoyAutoescapeException.createCausedWithoutMetaInfo(message, cause).associateNode(node);
  }


  /**
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param message A detailed description of what the error is.
   */
  @SuppressWarnings({"deprecation"})
  private SoyAutoescapeException(String message) {
    super(message);
  }


  /**
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param message A detailed description of what the error is.
   * @param cause The Throwable underlying this error.
   */
  private SoyAutoescapeException(String message, Throwable cause) {
    super(message, cause);
  }


  /**
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * <p> Note: For this constructor, the message will be set to the cause's message.
   *
   * @param cause The Throwable underlying this error.
   */
  private SoyAutoescapeException(Throwable cause) {
    super(cause.getMessage(), cause);
  }


  /**
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param node The node from which to derive the exception meta info.
   * @return This same SoyAutoescapeException object, for convenience.
   */
  public SoyAutoescapeException associateNode(SoyNode node) {
    SoySyntaxExceptionUtils.associateNode(this, node);
    return this;
  }


  /**
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param node The node from which to derive the exception meta info.
   * @return This same SoyAutoescapeException object, for convenience.
   */
  public SoyAutoescapeException maybeAssociateNode(SoyNode node) {
    if (getSourceLocation() == SourceLocation.UNKNOWN) {
      associateNode(node);
    }
    return this;
  }

}
