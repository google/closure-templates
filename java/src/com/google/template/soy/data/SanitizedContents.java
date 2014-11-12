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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import com.google.template.soy.data.SanitizedContent.ContentKind;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Set;

import javax.annotation.Nullable;
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
 */
@ParametersAreNonnullByDefault
public final class SanitizedContents {
  /**
   * Extensions for static resources that we allow to be treated as safe HTML.
   */
  private static final Set<String> SAFE_HTML_FILE_EXTENSIONS = ImmutableSet.of("html", "svg");

  /** No constructor. */
  private SanitizedContents() {}


  /**
   * Creates an empty string constant.
   */
  public static SanitizedContent emptyString(ContentKind kind) {
    return new SanitizedContent("", kind, Dir.NEUTRAL);  // Empty string is neutral.
  }


  /**
   * Creates a SanitizedContent object of kind TEXT of a given direction (null if unknown).
   *
   * <p>This is useful when stubbing out a function that needs to create a SanitizedContent object.
   */
  public static SanitizedContent unsanitizedText(String text, @Nullable Dir dir) {
    return new SanitizedContent(text, ContentKind.TEXT, dir);
  }


  /**
   * Creates a SanitizedContent object of kind TEXT and unknown direction.
   *
   * <p>This is useful when stubbing out a function that needs to create a SanitizedContent object.
   */
  public static SanitizedContent unsanitizedText(String text) {
    return unsanitizedText(text, null);
  }


  /**
   * Concatenate the contents of multiple {@link SanitizedContent} objects of kind HTML.
   *
   * @param contents The HTML content to combine.
   */
  public static SanitizedContent concatHtml(SanitizedContent... contents) {
    for (SanitizedContent content : contents) {
      checkArgument(content.getContentKind() == ContentKind.HTML, "Can only concat HTML");
    }

    StringBuilder combined = new StringBuilder();
    Dir dir = Dir.NEUTRAL;  // Empty string is neutral.
    for (SanitizedContent content : contents) {
      combined.append(content.getContent());
      if (dir == Dir.NEUTRAL) {
        // neutral + x -> x
        dir = content.getContentDirection();
      } else if (content.getContentDirection() == dir ||
          content.getContentDirection() == Dir.NEUTRAL) {
        // x + x|neutral -> x, so leave dir unchanged.
      } else {
        // LTR|unknown + RTL|unknown -> unknown
        // RTL|unknown + LTR|unknown -> unknown
        dir = null;
      }
    }
    return new SanitizedContent(combined.toString(), ContentKind.HTML, dir);
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
        Resources.toString(Resources.getResource(contextClass, resourceName), charset), kind,
        // Text resources are usually localized, so one might think that the locale direction should
        // be assumed for them. We do not do that because:
        // - We do not know the locale direction here.
        // - Some messages do not get translated.
        // - This method currently can't be used for text resources (see pretendValidateResource()).
        getDefaultDir(kind));
  }

  /**
   * Loads assumed-safe content from a Java resource.
   *
   * This performs ZERO VALIDATION of the data, and takes you on your word that the input is valid.
   * We assume that resources should be safe because they are part of the binary, and therefore not
   * attacker controlled, unless the source code is compromised (in which there's nothing we can
   * do).
   *
   * @param resourceName The name of the resource to be found using
   *                     {@linkplain Thread#getContextClassLoader() context class loader}.
   * @param charset The character set to use, usually Charsets.UTF_8.
   * @param kind The content kind of the resource.
   */
  public static SanitizedContent fromResource(
      String resourceName, Charset charset, ContentKind kind)
      throws IOException {
    pretendValidateResource(resourceName, kind);
    return new SanitizedContent(
        Resources.toString(Resources.getResource(resourceName), charset), kind,
        // Text resources are usually localized, so one might think that the locale direction should
        // be assumed for them. We do not do that because:
        // - We do not know the locale direction here.
        // - Some messages do not get translated.
        // - This method currently can't be used for text resources (see pretendValidateResource()).
        getDefaultDir(kind));
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
        Preconditions.checkArgument(SAFE_HTML_FILE_EXTENSIONS.contains(fileExtension));
        break;
      case CSS:
        Preconditions.checkArgument(fileExtension.equals("css"));
        break;
      default:
        throw new IllegalArgumentException("Don't know how to validate resources of kind " + kind);
    }
  }

  /*
   * Returns the default direction per content kind: LTR for JS, URI, ATTRIBUTES, and CSS content,
   * and otherwise unknown.
   */
  @VisibleForTesting static Dir getDefaultDir(ContentKind kind) {
    switch (kind) {
      case JS:
      case URI:
      case ATTRIBUTES:
      case CSS:
        return Dir.LTR;
      default:
        return null;
    }
  }
}
