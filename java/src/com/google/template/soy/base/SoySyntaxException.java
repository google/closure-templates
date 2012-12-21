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

import com.google.common.base.Preconditions;

import javax.annotation.Nullable;


/**
 * Exception for Soy syntax errors.
 *
 * <p> Important: Users outside of Soy code may call the getters on a SoySyntaxException object
 * created by the Soy compiler, but should not create or mutate SoySyntaxException objects
 * themselves (treat constructors, creation functions, and mutating methods as
 * superpackage-private).
 *
 * @author Kai Huang
 */
public class SoySyntaxException extends RuntimeException {


  /** The location in the soy file at which the error occurred. */
  private SourceLocation srcLoc = SourceLocation.UNKNOWN;

  /** The name of the template with the syntax error if any. */
  private String templateName;


  /**
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param message The error message.
   * @return The new SoySyntaxException object.
   */
  @SuppressWarnings({"deprecation"})
  public static SoySyntaxException createWithoutMetaInfo(String message) {
    return new SoySyntaxException(message);
  }


  /**
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param message The error message, or null to use the message from the cause.
   * @param cause The cause of this exception.
   * @return The new SoySyntaxException object.
   */
  @SuppressWarnings({"deprecation"})
  public static SoySyntaxException createCausedWithoutMetaInfo(
      @Nullable String message, Throwable cause) {

    Preconditions.checkNotNull(cause);
    if (message != null) {
      return new SoySyntaxException(message, cause);
    } else {
      return new SoySyntaxException(cause);
    }
  }


  /**
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param message The error message.
   * @param srcLoc The source location of the error, or null if unknown. At most one of srcLoc and
   *     filePath may be nonnull (prefer srcLoc since it contains more info).
   * @param filePath The file path of the file containing the error. At most one of srcLoc and
   *     filePath may be nonnull (prefer srcLoc since it contains more info).
   * @param templateName The name of the template containing the error, or null if not available.
   * @return The new SoySyntaxException object.
   */
  public static SoySyntaxException createWithMetaInfo(
      String message, @Nullable SourceLocation srcLoc, @Nullable String filePath,
      @Nullable String templateName) {

    return createWithoutMetaInfo(message).associateMetaInfo(srcLoc, filePath, templateName);
  }


  /**
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param message The error message, or null to use the message from the cause.
   * @param cause The cause of this exception.
   * @param srcLoc The source location of the error, or null if unknown. At most one of srcLoc and
   *     filePath may be nonnull (prefer srcLoc since it contains more info).
   * @param filePath The file path of the file containing the error. At most one of srcLoc and
   *     filePath may be nonnull (prefer srcLoc since it contains more info).
   * @param templateName The name of the template containing the error, or null if not available.
   * @return The new SoySyntaxException object.
   */
  public static SoySyntaxException createCausedWithMetaInfo(
      @Nullable String message, Throwable cause, @Nullable SourceLocation srcLoc,
      @Nullable String filePath, @Nullable String templateName) {

    Preconditions.checkNotNull(cause);
    return createCausedWithoutMetaInfo(message, cause)
        .associateMetaInfo(srcLoc, filePath, templateName);
  }


  /**
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param message A detailed description of what the syntax error is.
   * @deprecated  Do not use outside of Soy code (treat as superpackage-private).
   */
  @Deprecated public SoySyntaxException(String message) {
    super(message);
  }


  /**
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param message A detailed description of what the syntax error is.
   * @param cause The Throwable underlying this syntax error.
   */
  protected SoySyntaxException(String message, Throwable cause) {
    super(message, cause);
  }


  /**
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * <p> Note: For this constructor, the message will be set to the cause's message.
   *
   * @param cause The Throwable underlying this syntax error.
   */
  protected SoySyntaxException(Throwable cause) {
    super(cause.getMessage(), cause);
  }


  /**
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param srcLoc The source location of the error, or null if unknown. At most one of srcLoc and
   *     filePath may be nonnull (prefer srcLoc since it contains more info).
   * @param filePath The file path of the file containing the error. At most one of srcLoc and
   *     filePath may be nonnull (prefer srcLoc since it contains more info).
   * @param templateName The name of the template containing the error, or null if not available.
   * @return This same SoySyntaxException object, for convenience.
   */
  public SoySyntaxException associateMetaInfo(
      @Nullable SourceLocation srcLoc, @Nullable String filePath, @Nullable String templateName) {

    if (srcLoc != null) {
      Preconditions.checkArgument(filePath == null);  // srcLoc and filePath can't both be nonnull
      // If srcLoc not yet set, then set it, else assert existing value equals new value.
      if (this.srcLoc == SourceLocation.UNKNOWN) {
        this.srcLoc = srcLoc;
      } else {
        Preconditions.checkState(this.srcLoc.equals(srcLoc));
      }
    }
    if (filePath != null) {
      // If srcLoc not yet set, then set it (with line number 0), else assert existing file path
      // equals new file path.
      if (this.srcLoc == SourceLocation.UNKNOWN) {
        this.srcLoc = new SourceLocation(filePath, 0);
      } else {
        Preconditions.checkState(this.srcLoc.getFilePath().equals(filePath));
      }
    }

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


  /**
   * The source location at which the error occurred or {@link SourceLocation#UNKNOWN}.
   */
  public SourceLocation getSourceLocation() {
    return srcLoc;
  }


  /**
   * The name of the template in which the problem occurred or {@code null} if not known.
   */
  @Nullable public String getTemplateName() {
    return templateName;
  }


  @Override public String getMessage() {
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
   * @return The original error message from the Soy compiler without any
   *     metadata about the location where the error appears.  
   */
  public String getOriginalMessage() {
    return super.getMessage();
  }

}
