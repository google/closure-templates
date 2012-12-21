/*
 * Copyright 2012 Google Inc.
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

package com.google.template.soy.data;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.io.Resources;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;

import java.io.IOException;
import java.nio.charset.Charset;

import javax.annotation.ParametersAreNonnullByDefault;


/**
 * Creation utilities for SanitizedContent objects for common use cases.
 *
 * <p>This should contain utilities that have extremely broad application. More specific utilities
 * should reside with the specific project.
 *
 * <p>All utilities here should be extremely difficult to abuse in a way that could create
 * attacker-controlled SanitizedContent objects. Java's type system is a great tool to achieve
 * this.
 *
 * @author Garrett Boyer
 */
@ParametersAreNonnullByDefault
public final class SanitizedContents {

  /** No constructor. */
  private SanitizedContents() {}


  /**
   * Creates an empty string constant.
   */
  public static SanitizedContent emptyString(ContentKind kind) throws IOException {
    return new SanitizedContent("", kind);
  }


  /**
   * Loads assumed-safe content from a Java resource.
   *
   * This performs ZERO VALIDATION of the data, and takes you on your word that the input is valid. 
   * We assume that resources should be safe because they are part of the binary, and therefore not
   * attacker controlled, unless the source code is compromised (in which there's nothing we can
   * do).
   *
   * @param contextClass Class relative to which to load the resource.
   * @param resourceName The name of the resource, relative to the context class.
   * @param charset The character set to use, usually Charsets.UTF_8.
   * @param kind The content kind of the resource.
   */
  public static SanitizedContent fromResource(
      Class<?> contextClass, String resourceName, Charset charset, ContentKind kind)
      throws IOException {
    pretendValidateResource(resourceName, kind);
    return new SanitizedContent(
        Resources.toString(Resources.getResource(contextClass, resourceName), charset), kind);
  }


  /**
   * Very basic but strict validation that the resource's extension matches the content kind.
   *
   * <p>In practice, this may be unnecessary, but it's always good to start out strict. This list
   * can either be expanded as needed, or removed if too onerous.
   */
  @VisibleForTesting static void pretendValidateResource(String resourceName, ContentKind kind) {
    int index = resourceName.lastIndexOf('.');
    Preconditions.checkArgument(index >= 0,
        "Currently, we only validate resources with explicit extensions.");
    String fileExtension = resourceName.substring(index + 1).toLowerCase();

    switch (kind) {
      case JS:
        Preconditions.checkArgument(fileExtension.equals("js"));
        break;
      case HTML:
        Preconditions.checkArgument(fileExtension.equals("html"));
        break;
      case CSS:
        Preconditions.checkArgument(fileExtension.equals("css"));
        break;
      default:
        throw new IllegalArgumentException("Don't know how to validate resources of kind " + kind);
    }
  }
}
