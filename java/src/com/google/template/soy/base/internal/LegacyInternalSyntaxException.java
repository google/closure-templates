/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.base.internal;

import com.google.common.base.Preconditions;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SoySyntaxException;

/*
 * Copyright 2015 Google Inc.
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

import javax.annotation.Nullable;

/**
 * A SoySyntaxException that is meant for internal use in Soy. For example, for aborting execution
 * of a visitor in a compiler pass.
 *
 * <p>Errors should generally be reported via the ErrorReporter rather than these exceptions.
 */
public class LegacyInternalSyntaxException extends SoySyntaxException {

  /**
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param message The error message.
   * @return The new SoySyntaxException object.
   * @deprecated Prefer {@link LegacyInternalSyntaxException#createWithMetaInfo}. There's no good
   *     reason for not knowing where an error comes from.
   */
  @Deprecated
  public static LegacyInternalSyntaxException createWithoutMetaInfo(String message) {
    return new LegacyInternalSyntaxException(message);
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
  public static LegacyInternalSyntaxException createWithMetaInfo(
      String message,
      @Nullable SourceLocation srcLoc,
      @Nullable String filePath,
      @Nullable String templateName) {

    return createWithoutMetaInfo(message).associateMetaInfo(srcLoc, filePath, templateName);
  }

  /**
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param message The error message.
   * @param srcLoc The source location of the error, or null if unknown.
   * @return The new SoySyntaxException object.
   */
  public static LegacyInternalSyntaxException createWithMetaInfo(
      String message, SourceLocation srcLoc) {
    return createWithoutMetaInfo(message)
        .associateMetaInfo(srcLoc, null /* filePath */, null /* templateName */);
  }

  /** The location in the soy file at which the error occurred. */
  protected SourceLocation srcLoc = SourceLocation.UNKNOWN;

  /** The name of the template with the syntax error if any. */
  protected String templateName;

  protected LegacyInternalSyntaxException(Throwable cause) {
    super(cause);
  }

  protected LegacyInternalSyntaxException(String message, Throwable cause) {
    super(message, cause);
  }

  protected LegacyInternalSyntaxException(String message) {
    super(message);
  }

  public LegacyInternalSyntaxException associateMetaInfo(
      @Nullable SourceLocation srcLoc, @Nullable String filePath, @Nullable String templateName) {
    if (srcLoc != null) {
      Preconditions.checkArgument(filePath == null); // srcLoc and filePath can't both be nonnull
      // If srcLoc not yet set, then set it, else assert existing value equals new value.
      if (this.srcLoc == SourceLocation.UNKNOWN) {
        this.srcLoc = srcLoc;
      } else {
        Preconditions.checkState(this.srcLoc.equals(srcLoc));
      }
    }
    if (filePath != null) {
      // If srcLoc not yet set, then set it (with line number -1), else assert existing file path
      // equals new file path.
      if (this.srcLoc == SourceLocation.UNKNOWN) {
        this.srcLoc = new SourceLocation(filePath);
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

  /** Returns the original exception message, without any source location formatting. */
  public String getOriginalMessage() {
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
}
