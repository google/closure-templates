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
import com.google.common.html.types.SafeHtml;
import com.google.common.html.types.SafeHtmlProto;
import com.google.common.html.types.SafeHtmls;
import com.google.common.html.types.SafeScript;
import com.google.common.html.types.SafeScriptProto;
import com.google.common.html.types.SafeScripts;
import com.google.common.html.types.SafeStyle;
import com.google.common.html.types.SafeStyleProto;
import com.google.common.html.types.SafeStyleSheet;
import com.google.common.html.types.SafeStyleSheetProto;
import com.google.common.html.types.SafeStyleSheets;
import com.google.common.html.types.SafeStyles;
import com.google.common.html.types.SafeUrl;
import com.google.common.html.types.SafeUrlProto;
import com.google.common.html.types.SafeUrls;
import com.google.common.html.types.TrustedResourceUrl;
import com.google.common.html.types.TrustedResourceUrlProto;
import com.google.common.html.types.TrustedResourceUrls;
import com.google.common.io.Resources;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import java.io.IOException;
import java.nio.charset.Charset;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Creation utilities for SanitizedContent objects for common use cases.
 *
 * <p>This should contain utilities that have extremely broad application. More specific utilities
 * should reside with the specific project.
 *
 * <p>All utilities here should be extremely difficult to abuse in a way that could create
 * attacker-controlled SanitizedContent objects. Java's type system is a great tool to achieve this.
 *
 */
@ParametersAreNonnullByDefault
public final class SanitizedContents {
  /** Extensions for static resources that we allow to be treated as safe HTML. */
  private static final ImmutableSet<String> SAFE_HTML_FILE_EXTENSIONS =
      ImmutableSet.of("html", "svg");

  /** No constructor. */
  private SanitizedContents() {}

  /** Creates an empty string constant. */
  public static SanitizedContent emptyString(ContentKind kind) {
    if (kind == ContentKind.TEXT) {
      return UnsanitizedString.create("", Dir.NEUTRAL);
    }
    return SanitizedContent.create("", kind, Dir.NEUTRAL); // Empty string is neutral.
  }

  /**
   * Creates a SanitizedContent object of kind TEXT of a given direction (null if unknown).
   *
   * <p>This is useful when stubbing out a function that needs to create a SanitizedContent object.
   */
  public static UnsanitizedString unsanitizedText(String text, @Nullable Dir dir) {
    return UnsanitizedString.create(text, dir);
  }

  /**
   * Creates a SanitizedContent object of kind TEXT and unknown direction.
   *
   * <p>This is useful when stubbing out a function that needs to create a SanitizedContent object.
   */
  public static UnsanitizedString unsanitizedText(String text) {
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
    Dir dir = Dir.NEUTRAL; // Empty string is neutral.
    for (SanitizedContent content : contents) {
      combined.append(content.getContent());
      if (dir == Dir.NEUTRAL) {
        // neutral + x -> x
        dir = content.getContentDirection();
      } else if (content.getContentDirection() == dir
          || content.getContentDirection() == Dir.NEUTRAL) {
        // x + x|neutral -> x, so leave dir unchanged.
      } else {
        // LTR|unknown + RTL|unknown -> unknown
        // RTL|unknown + LTR|unknown -> unknown
        dir = null;
      }
    }
    return SanitizedContent.create(combined.toString(), ContentKind.HTML, dir);
  }

  /**
   * Loads assumed-safe content from a Java resource.
   *
   * <p>This performs ZERO VALIDATION of the data, and takes you on your word that the input is
   * valid. We assume that resources should be safe because they are part of the binary, and
   * therefore not attacker controlled, unless the source code is compromised (in which there's
   * nothing we can do).
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
    return SanitizedContent.create(
        Resources.toString(Resources.getResource(contextClass, resourceName), charset),
        kind,
        // Text resources are usually localized, so one might think that the locale direction should
        // be assumed for them. We do not do that because:
        // - We do not know the locale direction here.
        // - Some messages do not get translated.
        // - This method currently can't be used for text resources (see pretendValidateResource()).
        kind.getDefaultDir());
  }

  /**
   * Loads assumed-safe content from a Java resource.
   *
   * <p>This performs ZERO VALIDATION of the data, and takes you on your word that the input is
   * valid. We assume that resources should be safe because they are part of the binary, and
   * therefore not attacker controlled, unless the source code is compromised (in which there's
   * nothing we can do).
   *
   * @param resourceName The name of the resource to be found using {@linkplain
   *     Thread#getContextClassLoader() context class loader}.
   * @param charset The character set to use, usually Charsets.UTF_8.
   * @param kind The content kind of the resource.
   */
  public static SanitizedContent fromResource(
      String resourceName, Charset charset, ContentKind kind) throws IOException {
    pretendValidateResource(resourceName, kind);
    return SanitizedContent.create(
        Resources.toString(Resources.getResource(resourceName), charset),
        kind,
        // Text resources are usually localized, so one might think that the locale direction should
        // be assumed for them. We do not do that because:
        // - We do not know the locale direction here.
        // - Some messages do not get translated.
        // - This method currently can't be used for text resources (see pretendValidateResource()).
        kind.getDefaultDir());
  }

  /**
   * Wraps an assumed-safe URI constant.
   *
   * <p>This only accepts compile-time constants, based on the assumption that URLs that are
   * controlled by the application (and not user input) are considered safe.
   */
  public static SanitizedContent constantUri(@CompileTimeConstant final String constant) {
    return fromConstant(constant, ContentKind.URI, Dir.LTR);
  }

  /**
   * Wraps an assumed-safe constant string that specifies a safe, balanced, document fragment.
   *
   * <p>This only accepts compile-time constants, based on the assumption that HTML snippets that
   * are controlled by the application (and not user input) are considered safe.
   */
  public static SanitizedContent constantHtml(@CompileTimeConstant final String constant) {
    return fromConstant(constant, ContentKind.HTML, null);
  }

  /**
   * Wraps an assumed-safe constant string that specifies an attribute.
   *
   * <p>This only accepts compile-time constants, based on the assumption that attributes that are
   * controlled by the application (and not user input) are considered safe.
   */
  public static SanitizedContent constantAttributes(@CompileTimeConstant final String constant) {
    return fromConstant(constant, ContentKind.ATTRIBUTES, Dir.LTR);
  }

  /**
   * Wraps an assumed-safe CSS constant.
   *
   * <p>This only accepts compile-time constants, based on the assumption that URLs that are
   * controlled by the application (and not user input) are considered safe.
   */
  public static SanitizedContent constantCss(@CompileTimeConstant final String constant) {
    return fromConstant(constant, ContentKind.CSS, Dir.LTR);
  }

  /**
   * Creates JS from a number.
   *
   * <p>Soy prints numbers as floats and it wraps them in spaces. This is undesirable if the source
   * code is presented to the user, e.g. in {@code <textarea>}. This function allows converting the
   * number to JS which is then printed as is.
   */
  public static SanitizedContent numberJs(final long number) {
    return SanitizedContent.create(String.valueOf(number), ContentKind.JS);
  }

  /**
   * Creates JS from a number.
   *
   * @see #numberJs(long)
   */
  public static SanitizedContent numberJs(final double number) {
    return SanitizedContent.create(String.valueOf(number), ContentKind.JS);
  }

  /** Wraps an assumed-safe constant string. */
  @SuppressWarnings("ReferenceEquality") // need to use a reference check to ensure it is a constant
  private static SanitizedContent fromConstant(
      @CompileTimeConstant final String constant, ContentKind kind, @Nullable Dir dir) {
    // Extra runtime check in case the compile-time check doesn't work.
    Preconditions.checkArgument(
        constant.intern() == constant,
        "The provided argument does not look like a compile-time constant.");
    return SanitizedContent.create(constant, kind, dir);
  }

  /** Converts a {@link SafeHtml} into a Soy {@link SanitizedContent} of kind HTML. */
  public static SanitizedContent fromSafeHtml(SafeHtml html) {
    return SanitizedContent.create(html.getSafeHtmlString(), ContentKind.HTML);
  }

  /** Converts a {@link SafeHtmlProto} into a Soy {@link SanitizedContent} of kind HTML. */
  public static SanitizedContent fromSafeHtmlProto(SafeHtmlProto html) {
    return SanitizedContent.create(SafeHtmls.fromProto(html).getSafeHtmlString(), ContentKind.HTML);
  }

  /** Converts a {@link SafeScript} into a Soy {@link SanitizedContent} of kind JS. */
  public static SanitizedContent fromSafeScript(SafeScript script) {
    return SanitizedContent.create(script.getSafeScriptString(), ContentKind.JS);
  }

  /** Converts a {@link SafeScriptProto} into a Soy {@link SanitizedContent} of kind JS. */
  public static SanitizedContent fromSafeScriptProto(SafeScriptProto script) {
    return SanitizedContent.create(
        SafeScripts.fromProto(script).getSafeScriptString(), ContentKind.JS);
  }

  /** Converts a {@link SafeStyle} into a Soy {@link SanitizedContent} of kind CSS. */
  public static SanitizedContent fromSafeStyle(SafeStyle style) {
    return SanitizedContent.create(style.getSafeStyleString(), ContentKind.CSS);
  }

  /** Converts a {@link SafeStyleProto} into a Soy {@link SanitizedContent} of kind CSS. */
  public static SanitizedContent fromSafeStyleProto(SafeStyleProto style) {
    return SanitizedContent.create(
        SafeStyles.fromProto(style).getSafeStyleString(), ContentKind.CSS);
  }

  /** Converts a {@link SafeStyleSheet} into a Soy {@link SanitizedContent} of kind CSS. */
  public static SanitizedContent fromSafeStyleSheet(SafeStyleSheet styleSheet) {
    return SanitizedContent.create(styleSheet.getSafeStyleSheetString(), ContentKind.CSS);
  }

  /** Converts a {@link SafeStyleSheetProto} into a Soy {@link SanitizedContent} of kind CSS. */
  public static SanitizedContent fromSafeStyleSheetProto(SafeStyleSheetProto styleSheet) {
    return SanitizedContent.create(
        SafeStyleSheets.fromProto(styleSheet).getSafeStyleSheetString(), ContentKind.CSS);
  }

  /** Converts a {@link SafeUrl} into a Soy {@link SanitizedContent} of kind URI. */
  public static SanitizedContent fromSafeUrl(SafeUrl url) {
    return SanitizedContent.create(url.getSafeUrlString(), ContentKind.URI);
  }

  /** Converts a {@link SafeUrlProto} into a Soy {@link SanitizedContent} of kind URI. */
  public static SanitizedContent fromSafeUrlProto(SafeUrlProto url) {
    return SanitizedContent.create(SafeUrls.fromProto(url).getSafeUrlString(), ContentKind.URI);
  }

  /**
   * Converts a {@link TrustedResourceUrl} into a Soy {@link SanitizedContent} of kind
   * TRUSTED_RESOURCE_URI.
   */
  public static SanitizedContent fromTrustedResourceUrl(TrustedResourceUrl url) {
    return SanitizedContent.create(
        url.getTrustedResourceUrlString(), ContentKind.TRUSTED_RESOURCE_URI);
  }

  /**
   * Converts a {@link TrustedResourceUrlProto} into a Soy {@link SanitizedContent} of kind
   * TRUSTED_RESOURCE_URI.
   */
  public static SanitizedContent fromTrustedResourceUrlProto(TrustedResourceUrlProto url) {
    return SanitizedContent.create(
        TrustedResourceUrls.fromProto(url).getTrustedResourceUrlString(),
        ContentKind.TRUSTED_RESOURCE_URI);
  }

  /**
   * Very basic but strict validation that the resource's extension matches the content kind.
   *
   * <p>In practice, this may be unnecessary, but it's always good to start out strict. This list
   * can either be expanded as needed, or removed if too onerous.
   */
  @VisibleForTesting
  static void pretendValidateResource(String resourceName, ContentKind kind) {
    int index = resourceName.lastIndexOf('.');
    Preconditions.checkArgument(
        index >= 0, "Currently, we only validate resources with explicit extensions.");
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
}
