// **** GENERATED CODE, DO NOT MODIFY ****
// This file was generated via preprocessing from input:
// java/com/google/common/html/types/SafeHtmlBuilder.java.tpl
// Please make changes to the template and regenerate this file with:
// webutil/html/types/codegen/update_generated_source_files.sh
// ***************************************
/*
 * Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.common.html.types;

import static com.google.common.html.types.BuilderUtils.coerceToInterchangeValid;
import static com.google.common.html.types.BuilderUtils.escapeHtmlInternal;

import com.google.common.annotations.GwtCompatible;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.CompileTimeConstant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Generated;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Builder for HTML elements which conform to the {@link SafeHtml} contract. Supports setting
 * element name, individual attributes and content.
 *
 * <p>While this builder disallows some invalid HTML constructs (for example, void elements with
 * content) it does not guarantee well-formed HTML (for example, attribute values are not strictly
 * enforced if they pose no security risk). A large number of runtime failures are possible and it
 * is therefore recommended to thoroughly unit test code using this builder.
 */
@CheckReturnValue
@Generated(value = "//java/com/google/common/html/types:gen_srcs.sh")
@GwtCompatible
@NotThreadSafe
public final class SafeHtmlBuilder {
  // Keep these regular expressions compatible across Java and JavaScript native implementations.
  // They are uncompiled because we couldn't depend on java.util.regex.Pattern or
  // com.google.gwt.regexp.shared.RegExp
  private static final String VALID_ELEMENT_NAMES_REGEXP = "[a-z0-9-]+";
  private static final String VALID_DATA_ATTRIBUTES_REGEXP = "data-[a-zA-Z-]+";

  private static final ImmutableSet<String> UNSUPPORTED_ELEMENTS =
      ImmutableSet.of("applet", "base", "embed", "math", "meta", "object", "svg", "template");

  private static final ImmutableSet<String> SCRIPT_ELEMENTS = ImmutableSet.of("script");

  private static final ImmutableSet<String> STYLESHEET_ELEMENTS = ImmutableSet.of("style");

  private static final ImmutableSet<String> VOID_ELEMENTS =
      ImmutableSet.of(
          "area", "br", "col", "hr", "img", "input", "link", "param", "source", "track", "wbr");

  private final String elementName;
  /** We use LinkedHashMap to maintain attribute insertion order. */
  private final Map<String, String> attributes = new LinkedHashMap<>();

  private final List<String> contents = new ArrayList<>();

  private boolean useSlashOnVoid = false;

  private enum AttributeContract {
    SAFE_URL,
    TRUSTED_RESOURCE_URL
  }

  /** Contract of the value currently assigned to the {@code href} attribute. */
  private AttributeContract hrefValueContract = AttributeContract.TRUSTED_RESOURCE_URL;

  /**
   * Creates a builder for the given {@code elementName}, which must consist only of lowercase
   * letters, digits and {@code -}.
   *
   * <p>If {@code elementName} is not a void element then the string representation of the builder
   * is {@code <elementName[optional attributes]>[optional content]</elementName>}. If {@code
   * elementName} is a void element then the string representation is {@code <elementName[optional
   * attributes]>}. Contents between the element's start and end tag can be set via, for example,
   * {@code appendContent()}.
   *
   * <p>{@code embed}, {@code object}, {@code template} are not supported because their content has
   * special semantics, and they can result the execution of code not under application control.
   * Some of these have dedicated creation methods.
   *
   * @throws IllegalArgumentException if {@code elementName} contains invalid characters or is not
   *     supported
   * @see http://whatwg.org/html/syntax.html#void-elements
   */
  public SafeHtmlBuilder(@CompileTimeConstant final String elementName) {
    if (elementName == null) {
      throw new NullPointerException();
    }
    if (!elementName.matches(VALID_ELEMENT_NAMES_REGEXP)) {
      throw new IllegalArgumentException(
          "Invalid element name \""
              + elementName
              + "\". "
              + "Only lowercase letters, numbers and '-' allowed.");
    }
    if (UNSUPPORTED_ELEMENTS.contains(elementName)) {
      throw new IllegalArgumentException("Element \"" + elementName + "\" is not supported.");
    }
    this.elementName = elementName;
  }

  /**
   * Causes the builder to use a slash on the tag of a void element, emitting e.g. {@code <br/>}
   * instead of the default {@code <br>}. Slashes are required if rendering XHTML and optional in
   * HTML 5.
   *
   * <p>This setting has no effect for non-void elements.
   *
   * @see http://www.w3.org/TR/html5/syntax.html#start-tags
   */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder useSlashOnVoid() {
    useSlashOnVoid = true;
    return this;
  }
  /** These elements are allowlisted to use accept with a String value. */
  private static final ImmutableSet<String> ACCEPT_STRING_ELEMENT_ALLOWLIST =
      ImmutableSet.of("input");

  /**
   * Sets the {@code accept} attribute for this element.
   *
   * <p>The attribute {@code accept} with a {@code String} value is allowed on these elements:
   *
   * <ul>
   *   <li>{@code input}
   * </ul>
   *
   * @throws IllegalArgumentException if the {@code accept} attribute with a {@code String} value is
   *     not allowed on this element
   */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAccept(String value) {
    if (!ACCEPT_STRING_ELEMENT_ALLOWLIST.contains(elementName)) {
      throw new IllegalArgumentException(
          "Attribute \"accept\" with a String value can only be used "
              + "by one of the following elements: "
              + ACCEPT_STRING_ELEMENT_ALLOWLIST);
    }
    return setAttribute("accept", value);
  }

  /** These elements are allowlisted to use action with a SafeUrl value. */
  private static final ImmutableSet<String> ACTION_SAFE_URL_ELEMENT_ALLOWLIST =
      ImmutableSet.of("form");

  /**
   * Sets the {@code action} attribute for this element.
   *
   * <p>The attribute {@code action} with a {@code SafeUrl} value is allowed on these elements:
   *
   * <ul>
   *   <li>{@code form}
   * </ul>
   *
   * @throws IllegalArgumentException if the {@code action} attribute with a {@code SafeUrl} value
   *     is not allowed on this element
   */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAction(SafeUrl value) {
    if (!ACTION_SAFE_URL_ELEMENT_ALLOWLIST.contains(elementName)) {
      throw new IllegalArgumentException(
          "Attribute \"action\" with a SafeUrl value can only be used "
              + "by one of the following elements: "
              + ACTION_SAFE_URL_ELEMENT_ALLOWLIST);
    }
    return setAttribute("action", value.getSafeUrlString());
  }

  /** Sets the {@code align} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAlign(String value) {
    return setAttribute("align", value);
  }

  /** Sets the {@code alt} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAlt(String value) {
    return setAttribute("alt", value);
  }

  /** Sets the {@code aria-activedescendant} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaActivedescendant(@CompileTimeConstant final String value) {
    return setAttribute("aria-activedescendant", value);
  }

  /**
   * Sets the {@code aria-activedescendant} attribute for this element to a {@link
   * CompileTimeConstant} {@code prefix} and a {@code value} joined by a hyphen.
   *
   * @throws IllegalArgumentException if {@code prefix} is an empty string
   */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaActivedescendantWithPrefix(
      @CompileTimeConstant final String prefix, String value) {
    if (prefix.trim().length() == 0) {
      throw new IllegalArgumentException("Prefix cannot be empty string");
    }
    return setAttribute("aria-activedescendant", prefix + "-" + value);
  }

  /** Sets the {@code aria-atomic} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaAtomic(String value) {
    return setAttribute("aria-atomic", value);
  }

  /** Sets the {@code aria-autocomplete} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaAutocomplete(String value) {
    return setAttribute("aria-autocomplete", value);
  }

  /** Sets the {@code aria-busy} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaBusy(String value) {
    return setAttribute("aria-busy", value);
  }

  /** Sets the {@code aria-checked} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaChecked(String value) {
    return setAttribute("aria-checked", value);
  }

  /** Sets the {@code aria-controls} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaControls(@CompileTimeConstant final String value) {
    return setAttribute("aria-controls", value);
  }

  /**
   * Sets the {@code aria-controls} attribute for this element to a {@link CompileTimeConstant}
   * {@code prefix} and a {@code value} joined by a hyphen.
   *
   * @throws IllegalArgumentException if {@code prefix} is an empty string
   */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaControlsWithPrefix(
      @CompileTimeConstant final String prefix, String value) {
    if (prefix.trim().length() == 0) {
      throw new IllegalArgumentException("Prefix cannot be empty string");
    }
    return setAttribute("aria-controls", prefix + "-" + value);
  }

  /** Sets the {@code aria-current} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaCurrent(String value) {
    return setAttribute("aria-current", value);
  }

  /** Sets the {@code aria-disabled} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaDisabled(String value) {
    return setAttribute("aria-disabled", value);
  }

  /** Sets the {@code aria-dropeffect} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaDropeffect(String value) {
    return setAttribute("aria-dropeffect", value);
  }

  /** Sets the {@code aria-expanded} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaExpanded(String value) {
    return setAttribute("aria-expanded", value);
  }

  /** Sets the {@code aria-haspopup} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaHaspopup(String value) {
    return setAttribute("aria-haspopup", value);
  }

  /** Sets the {@code aria-hidden} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaHidden(String value) {
    return setAttribute("aria-hidden", value);
  }

  /** Sets the {@code aria-invalid} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaInvalid(String value) {
    return setAttribute("aria-invalid", value);
  }

  /** Sets the {@code aria-label} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaLabel(String value) {
    return setAttribute("aria-label", value);
  }

  /** Sets the {@code aria-labelledby} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaLabelledby(@CompileTimeConstant final String value) {
    return setAttribute("aria-labelledby", value);
  }

  /**
   * Sets the {@code aria-labelledby} attribute for this element to a {@link CompileTimeConstant}
   * {@code prefix} and a {@code value} joined by a hyphen.
   *
   * @throws IllegalArgumentException if {@code prefix} is an empty string
   */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaLabelledbyWithPrefix(
      @CompileTimeConstant final String prefix, String value) {
    if (prefix.trim().length() == 0) {
      throw new IllegalArgumentException("Prefix cannot be empty string");
    }
    return setAttribute("aria-labelledby", prefix + "-" + value);
  }

  /** Sets the {@code aria-level} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaLevel(String value) {
    return setAttribute("aria-level", value);
  }

  /** Sets the {@code aria-live} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaLive(String value) {
    return setAttribute("aria-live", value);
  }

  /** Sets the {@code aria-multiline} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaMultiline(String value) {
    return setAttribute("aria-multiline", value);
  }

  /** Sets the {@code aria-multiselectable} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaMultiselectable(String value) {
    return setAttribute("aria-multiselectable", value);
  }

  /** Sets the {@code aria-orientation} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaOrientation(String value) {
    return setAttribute("aria-orientation", value);
  }

  /** Sets the {@code aria-owns} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaOwns(@CompileTimeConstant final String value) {
    return setAttribute("aria-owns", value);
  }

  /**
   * Sets the {@code aria-owns} attribute for this element to a {@link CompileTimeConstant} {@code
   * prefix} and a {@code value} joined by a hyphen.
   *
   * @throws IllegalArgumentException if {@code prefix} is an empty string
   */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaOwnsWithPrefix(
      @CompileTimeConstant final String prefix, String value) {
    if (prefix.trim().length() == 0) {
      throw new IllegalArgumentException("Prefix cannot be empty string");
    }
    return setAttribute("aria-owns", prefix + "-" + value);
  }

  /** Sets the {@code aria-posinset} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaPosinset(String value) {
    return setAttribute("aria-posinset", value);
  }

  /** Sets the {@code aria-pressed} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaPressed(String value) {
    return setAttribute("aria-pressed", value);
  }

  /** Sets the {@code aria-readonly} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaReadonly(String value) {
    return setAttribute("aria-readonly", value);
  }

  /** Sets the {@code aria-relevant} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaRelevant(String value) {
    return setAttribute("aria-relevant", value);
  }

  /** Sets the {@code aria-required} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaRequired(String value) {
    return setAttribute("aria-required", value);
  }

  /** Sets the {@code aria-selected} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaSelected(String value) {
    return setAttribute("aria-selected", value);
  }

  /** Sets the {@code aria-setsize} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaSetsize(String value) {
    return setAttribute("aria-setsize", value);
  }

  /** Sets the {@code aria-sort} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaSort(String value) {
    return setAttribute("aria-sort", value);
  }

  /** Sets the {@code aria-valuemax} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaValuemax(String value) {
    return setAttribute("aria-valuemax", value);
  }

  /** Sets the {@code aria-valuemin} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaValuemin(String value) {
    return setAttribute("aria-valuemin", value);
  }

  /** Sets the {@code aria-valuenow} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaValuenow(String value) {
    return setAttribute("aria-valuenow", value);
  }

  /** Sets the {@code aria-valuetext} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAriaValuetext(String value) {
    return setAttribute("aria-valuetext", value);
  }

  /** Values that can be passed to {@link #setAsync(AsyncValue)}. */
  public enum AsyncValue {

    /** Value of {@code async}. */
    ASYNC("async");

    private final String value;

    private AsyncValue(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return value;
    }
  }

  /** Sets the {@code async} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAsync(AsyncValue value) {
    return setAttribute("async", value.toString());
  }

  /** Sets the {@code autocapitalize} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAutocapitalize(String value) {
    return setAttribute("autocapitalize", value);
  }

  /** Sets the {@code autocomplete} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAutocomplete(String value) {
    return setAttribute("autocomplete", value);
  }

  /** Sets the {@code autocorrect} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAutocorrect(String value) {
    return setAttribute("autocorrect", value);
  }

  /** Sets the {@code autofocus} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAutofocus(String value) {
    return setAttribute("autofocus", value);
  }

  /** Sets the {@code autoplay} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setAutoplay(String value) {
    return setAttribute("autoplay", value);
  }

  /** Sets the {@code bgcolor} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setBgcolor(String value) {
    return setAttribute("bgcolor", value);
  }

  /** Sets the {@code border} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setBorder(String value) {
    return setAttribute("border", value);
  }

  /** Sets the {@code cellpadding} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setCellpadding(String value) {
    return setAttribute("cellpadding", value);
  }

  /** Sets the {@code cellspacing} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setCellspacing(String value) {
    return setAttribute("cellspacing", value);
  }

  /** Sets the {@code checked} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setChecked(String value) {
    return setAttribute("checked", value);
  }

  /** Sets the {@code cite} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setCite(SafeUrl value) {
    return setAttribute("cite", value.getSafeUrlString());
  }

  /** Sets the {@code class} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setClass(String value) {
    return setAttribute("class", value);
  }

  /** Sets the {@code color} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setColor(String value) {
    return setAttribute("color", value);
  }

  /** Sets the {@code cols} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setCols(String value) {
    return setAttribute("cols", value);
  }

  /** Sets the {@code colspan} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setColspan(String value) {
    return setAttribute("colspan", value);
  }

  /** Sets the {@code contenteditable} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setContenteditable(String value) {
    return setAttribute("contenteditable", value);
  }

  /** Sets the {@code controls} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setControls(String value) {
    return setAttribute("controls", value);
  }

  /** Sets the {@code datetime} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setDatetime(String value) {
    return setAttribute("datetime", value);
  }

  /** These elements are allowlisted to use defer with a String value. */
  private static final ImmutableSet<String> DEFER_STRING_ELEMENT_ALLOWLIST =
      ImmutableSet.of("script");

  /**
   * Sets the {@code defer} attribute for this element.
   *
   * <p>The attribute {@code defer} with a {@code String} value is allowed on these elements:
   *
   * <ul>
   *   <li>{@code script}
   * </ul>
   *
   * @throws IllegalArgumentException if the {@code defer} attribute with a {@code String} value is
   *     not allowed on this element
   */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setDefer(String value) {
    if (!DEFER_STRING_ELEMENT_ALLOWLIST.contains(elementName)) {
      throw new IllegalArgumentException(
          "Attribute \"defer\" with a String value can only be used "
              + "by one of the following elements: "
              + DEFER_STRING_ELEMENT_ALLOWLIST);
    }
    return setAttribute("defer", value);
  }

  /** Values that can be passed to {@link #setDir(DirValue)}. */
  public enum DirValue {

    /** Value of {@code auto}. */
    AUTO("auto"),
    /** Value of {@code ltr}. */
    LTR("ltr"),
    /** Value of {@code rtl}. */
    RTL("rtl");

    private final String value;

    private DirValue(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return value;
    }
  }

  /** Sets the {@code dir} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setDir(DirValue value) {
    return setAttribute("dir", value.toString());
  }

  /** Sets the {@code disabled} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setDisabled(String value) {
    return setAttribute("disabled", value);
  }

  /** Sets the {@code download} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setDownload(String value) {
    return setAttribute("download", value);
  }

  /** Sets the {@code draggable} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setDraggable(String value) {
    return setAttribute("draggable", value);
  }

  /** Sets the {@code enctype} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setEnctype(String value) {
    return setAttribute("enctype", value);
  }

  /** Sets the {@code face} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setFace(String value) {
    return setAttribute("face", value);
  }

  /** Sets the {@code for} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setFor(@CompileTimeConstant final String value) {
    return setAttribute("for", value);
  }

  /**
   * Sets the {@code for} attribute for this element to a {@link CompileTimeConstant} {@code prefix}
   * and a {@code value} joined by a hyphen.
   *
   * @throws IllegalArgumentException if {@code prefix} is an empty string
   */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setForWithPrefix(@CompileTimeConstant final String prefix, String value) {
    if (prefix.trim().length() == 0) {
      throw new IllegalArgumentException("Prefix cannot be empty string");
    }
    return setAttribute("for", prefix + "-" + value);
  }

  /** These elements are allowlisted to use formaction with a SafeUrl value. */
  private static final ImmutableSet<String> FORMACTION_SAFE_URL_ELEMENT_ALLOWLIST =
      ImmutableSet.of("button", "input");

  /**
   * Sets the {@code formaction} attribute for this element.
   *
   * <p>The attribute {@code formaction} with a {@code SafeUrl} value is allowed on these elements:
   *
   * <ul>
   *   <li>{@code button}
   *   <li>{@code input}
   * </ul>
   *
   * @throws IllegalArgumentException if the {@code formaction} attribute with a {@code SafeUrl}
   *     value is not allowed on this element
   */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setFormaction(SafeUrl value) {
    if (!FORMACTION_SAFE_URL_ELEMENT_ALLOWLIST.contains(elementName)) {
      throw new IllegalArgumentException(
          "Attribute \"formaction\" with a SafeUrl value can only be used "
              + "by one of the following elements: "
              + FORMACTION_SAFE_URL_ELEMENT_ALLOWLIST);
    }
    return setAttribute("formaction", value.getSafeUrlString());
  }

  /** Sets the {@code formenctype} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setFormenctype(String value) {
    return setAttribute("formenctype", value);
  }

  /** These elements are allowlisted to use formmethod with a String value. */
  private static final ImmutableSet<String> FORMMETHOD_STRING_ELEMENT_ALLOWLIST =
      ImmutableSet.of("button", "input");

  /**
   * Sets the {@code formmethod} attribute for this element.
   *
   * <p>The attribute {@code formmethod} with a {@code String} value is allowed on these elements:
   *
   * <ul>
   *   <li>{@code button}
   *   <li>{@code input}
   * </ul>
   *
   * @throws IllegalArgumentException if the {@code formmethod} attribute with a {@code String}
   *     value is not allowed on this element
   */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setFormmethod(String value) {
    if (!FORMMETHOD_STRING_ELEMENT_ALLOWLIST.contains(elementName)) {
      throw new IllegalArgumentException(
          "Attribute \"formmethod\" with a String value can only be used "
              + "by one of the following elements: "
              + FORMMETHOD_STRING_ELEMENT_ALLOWLIST);
    }
    return setAttribute("formmethod", value);
  }

  /** Sets the {@code frameborder} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setFrameborder(String value) {
    return setAttribute("frameborder", value);
  }

  /** Sets the {@code height} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setHeight(String value) {
    return setAttribute("height", value);
  }

  /** Sets the {@code hidden} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setHidden(String value) {
    return setAttribute("hidden", value);
  }

  /** These elements are allowlisted to use href with a SafeUrl value. */
  private static final ImmutableSet<String> HREF_SAFE_URL_ELEMENT_ALLOWLIST =
      ImmutableSet.of("a", "area");
  /**
   * On {@code link} elements, the {@code href} attribute may be set to {@code SafeUrl} values only
   * for these values of the {@code rel} attribute.
   */
  private static final ImmutableSet<String> LINK_HREF_SAFE_URL_REL_ALLOWLIST =
      ImmutableSet.of(
          "alternate",
          "author",
          "bookmark",
          "canonical",
          "cite",
          "help",
          "icon",
          "license",
          "next",
          "prefetch",
          "dns-prefetch",
          "prerender",
          "preconnect",
          "preload",
          "prev",
          "search",
          "subresource");

  /**
   * Sets the {@code href} attribute for this element.
   *
   * <p>The attribute {@code href} with a {@code SafeUrl} value is allowed on these elements:
   *
   * <ul>
   *   <li>{@code a}
   *   <li>{@code area}
   *   <li>{@code link}
   * </ul>
   *
   * <p>On {@code link} elements, {@code href} may only be set to a SafeUrl value if {@code rel} is
   * one of the following values:
   *
   * <ul>
   *   <li>{@code alternate}
   *   <li>{@code author}
   *   <li>{@code bookmark}
   *   <li>{@code canonical}
   *   <li>{@code cite}
   *   <li>{@code help}
   *   <li>{@code icon}
   *   <li>{@code license}
   *   <li>{@code next}
   *   <li>{@code prefetch}
   *   <li>{@code dns-prefetch}
   *   <li>{@code prerender}
   *   <li>{@code preconnect}
   *   <li>{@code preload}
   *   <li>{@code prev}
   *   <li>{@code search}
   *   <li>{@code subresource}
   * </ul>
   *
   * @throws IllegalArgumentException if the {@code href} attribute with a {@code SafeUrl} value is
   *     not allowed on this element
   * @throws IllegalArgumentException if this a {@code link} element and the value of {@code rel}
   *     does not allow the SafeUrl contract on {@code href}
   */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setHref(SafeUrl value) {
    if (!HREF_SAFE_URL_ELEMENT_ALLOWLIST.contains(elementName) && !elementName.equals("link")) {
      throw new IllegalArgumentException(
          "Attribute \"href\" with a SafeUrl value can only be used "
              + "by one of the following elements: "
              + HREF_SAFE_URL_ELEMENT_ALLOWLIST);
    }
    if (elementName.equals("link")) {
      checkLinkDependentAttributes(attributes.get("rel"), AttributeContract.SAFE_URL);
    }
    hrefValueContract = AttributeContract.SAFE_URL;
    return setAttribute("href", value.getSafeUrlString());
  }

  /** Sets the {@code href} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setHref(TrustedResourceUrl value) {
    hrefValueContract = AttributeContract.TRUSTED_RESOURCE_URL;
    return setAttribute("href", value.getTrustedResourceUrlString());
  }

  /** Sets the {@code hreflang} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setHreflang(String value) {
    return setAttribute("hreflang", value);
  }

  /** Sets the {@code id} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setId(@CompileTimeConstant final String value) {
    return setAttribute("id", value);
  }

  /**
   * Sets the {@code id} attribute for this element to a {@link CompileTimeConstant} {@code prefix}
   * and a {@code value} joined by a hyphen.
   *
   * @throws IllegalArgumentException if {@code prefix} is an empty string
   */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setIdWithPrefix(@CompileTimeConstant final String prefix, String value) {
    if (prefix.trim().length() == 0) {
      throw new IllegalArgumentException("Prefix cannot be empty string");
    }
    return setAttribute("id", prefix + "-" + value);
  }

  /** Sets the {@code ismap} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setIsmap(String value) {
    return setAttribute("ismap", value);
  }

  /** Sets the {@code itemid} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setItemid(String value) {
    return setAttribute("itemid", value);
  }

  /** Sets the {@code itemprop} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setItemprop(String value) {
    return setAttribute("itemprop", value);
  }

  /** Sets the {@code itemref} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setItemref(String value) {
    return setAttribute("itemref", value);
  }

  /** Sets the {@code itemscope} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setItemscope(String value) {
    return setAttribute("itemscope", value);
  }

  /** Sets the {@code itemtype} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setItemtype(String value) {
    return setAttribute("itemtype", value);
  }

  /** Sets the {@code label} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setLabel(String value) {
    return setAttribute("label", value);
  }

  /** Sets the {@code lang} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setLang(String value) {
    return setAttribute("lang", value);
  }

  /** Sets the {@code list} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setList(@CompileTimeConstant final String value) {
    return setAttribute("list", value);
  }

  /**
   * Sets the {@code list} attribute for this element to a {@link CompileTimeConstant} {@code
   * prefix} and a {@code value} joined by a hyphen.
   *
   * @throws IllegalArgumentException if {@code prefix} is an empty string
   */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setListWithPrefix(@CompileTimeConstant final String prefix, String value) {
    if (prefix.trim().length() == 0) {
      throw new IllegalArgumentException("Prefix cannot be empty string");
    }
    return setAttribute("list", prefix + "-" + value);
  }

  /** Values that can be passed to {@link #setLoading(LoadingValue)}. */
  public enum LoadingValue {

    /** Value of {@code eager}. */
    EAGER("eager"),
    /** Value of {@code lazy}. */
    LAZY("lazy");

    private final String value;

    private LoadingValue(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return value;
    }
  }

  /** Sets the {@code loading} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setLoading(LoadingValue value) {
    return setAttribute("loading", value.toString());
  }

  /** Sets the {@code loop} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setLoop(String value) {
    return setAttribute("loop", value);
  }

  /** Sets the {@code max} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setMax(String value) {
    return setAttribute("max", value);
  }

  /** Sets the {@code maxlength} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setMaxlength(String value) {
    return setAttribute("maxlength", value);
  }

  /** Sets the {@code media} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setMedia(String value) {
    return setAttribute("media", value);
  }

  /** These elements are allowlisted to use method with a String value. */
  private static final ImmutableSet<String> METHOD_STRING_ELEMENT_ALLOWLIST =
      ImmutableSet.of("form");

  /**
   * Sets the {@code method} attribute for this element.
   *
   * <p>The attribute {@code method} with a {@code String} value is allowed on these elements:
   *
   * <ul>
   *   <li>{@code form}
   * </ul>
   *
   * @throws IllegalArgumentException if the {@code method} attribute with a {@code String} value is
   *     not allowed on this element
   */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setMethod(String value) {
    if (!METHOD_STRING_ELEMENT_ALLOWLIST.contains(elementName)) {
      throw new IllegalArgumentException(
          "Attribute \"method\" with a String value can only be used "
              + "by one of the following elements: "
              + METHOD_STRING_ELEMENT_ALLOWLIST);
    }
    return setAttribute("method", value);
  }

  /** Sets the {@code min} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setMin(String value) {
    return setAttribute("min", value);
  }

  /** Sets the {@code minlength} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setMinlength(String value) {
    return setAttribute("minlength", value);
  }

  /** Sets the {@code multiple} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setMultiple(String value) {
    return setAttribute("multiple", value);
  }

  /** Sets the {@code muted} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setMuted(String value) {
    return setAttribute("muted", value);
  }

  /** Sets the {@code name} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setName(@CompileTimeConstant final String value) {
    return setAttribute("name", value);
  }

  /**
   * Sets the {@code name} attribute for this element to a {@link CompileTimeConstant} {@code
   * prefix} and a {@code value} joined by a hyphen.
   *
   * @throws IllegalArgumentException if {@code prefix} is an empty string
   */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setNameWithPrefix(@CompileTimeConstant final String prefix, String value) {
    if (prefix.trim().length() == 0) {
      throw new IllegalArgumentException("Prefix cannot be empty string");
    }
    return setAttribute("name", prefix + "-" + value);
  }

  /** Sets the {@code nonce} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setNonce(String value) {
    return setAttribute("nonce", value);
  }

  /** Sets the {@code open} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setOpen(String value) {
    return setAttribute("open", value);
  }

  /** These elements are allowlisted to use pattern with a String value. */
  private static final ImmutableSet<String> PATTERN_STRING_ELEMENT_ALLOWLIST =
      ImmutableSet.of("input");

  /**
   * Sets the {@code pattern} attribute for this element.
   *
   * <p>The attribute {@code pattern} with a {@code String} value is allowed on these elements:
   *
   * <ul>
   *   <li>{@code input}
   * </ul>
   *
   * @throws IllegalArgumentException if the {@code pattern} attribute with a {@code String} value
   *     is not allowed on this element
   */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setPattern(String value) {
    if (!PATTERN_STRING_ELEMENT_ALLOWLIST.contains(elementName)) {
      throw new IllegalArgumentException(
          "Attribute \"pattern\" with a String value can only be used "
              + "by one of the following elements: "
              + PATTERN_STRING_ELEMENT_ALLOWLIST);
    }
    return setAttribute("pattern", value);
  }

  /** Sets the {@code placeholder} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setPlaceholder(String value) {
    return setAttribute("placeholder", value);
  }

  /** Sets the {@code poster} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setPoster(SafeUrl value) {
    return setAttribute("poster", value.getSafeUrlString());
  }

  /** Sets the {@code preload} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setPreload(String value) {
    return setAttribute("preload", value);
  }

  /** These elements are allowlisted to use readonly with a String value. */
  private static final ImmutableSet<String> READONLY_STRING_ELEMENT_ALLOWLIST =
      ImmutableSet.of("input", "textarea");

  /**
   * Sets the {@code readonly} attribute for this element.
   *
   * <p>The attribute {@code readonly} with a {@code String} value is allowed on these elements:
   *
   * <ul>
   *   <li>{@code input}
   *   <li>{@code textarea}
   * </ul>
   *
   * @throws IllegalArgumentException if the {@code readonly} attribute with a {@code String} value
   *     is not allowed on this element
   */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setReadonly(String value) {
    if (!READONLY_STRING_ELEMENT_ALLOWLIST.contains(elementName)) {
      throw new IllegalArgumentException(
          "Attribute \"readonly\" with a String value can only be used "
              + "by one of the following elements: "
              + READONLY_STRING_ELEMENT_ALLOWLIST);
    }
    return setAttribute("readonly", value);
  }

  /**
   * Sets the {@code rel} attribute for this element.
   *
   * <p>If this is a {@code link} element, and {@code href} has been set from a {@link SafeUrl},
   * then {@code value} has to be an allowed value. See {@link #setHref(SafeUrl)}.
   *
   * @throws IllegalArgumentException if this is a {@code link} element and this value of {@code
   *     rel} is not allowed
   */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setRel(String value) {
    if (elementName.equals("link")) {
      checkLinkDependentAttributes(value, hrefValueContract);
    }
    return setAttribute("rel", value);
  }

  /** Sets the {@code required} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setRequired(String value) {
    return setAttribute("required", value);
  }

  /** Sets the {@code reversed} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setReversed(String value) {
    return setAttribute("reversed", value);
  }

  /** Sets the {@code role} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setRole(String value) {
    return setAttribute("role", value);
  }

  /** Sets the {@code rows} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setRows(String value) {
    return setAttribute("rows", value);
  }

  /** Sets the {@code rowspan} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setRowspan(String value) {
    return setAttribute("rowspan", value);
  }

  /** Sets the {@code selected} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setSelected(String value) {
    return setAttribute("selected", value);
  }

  /** Sets the {@code shape} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setShape(String value) {
    return setAttribute("shape", value);
  }

  /** Sets the {@code size} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setSize(String value) {
    return setAttribute("size", value);
  }

  /** Sets the {@code sizes} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setSizes(String value) {
    return setAttribute("sizes", value);
  }

  /** Sets the {@code slot} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setSlot(String value) {
    return setAttribute("slot", value);
  }

  /** Sets the {@code span} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setSpan(String value) {
    return setAttribute("span", value);
  }

  /** Sets the {@code spellcheck} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setSpellcheck(String value) {
    return setAttribute("spellcheck", value);
  }

  /** These elements are allowlisted to use src with a SafeUrl value. */
  private static final ImmutableSet<String> SRC_SAFE_URL_ELEMENT_ALLOWLIST =
      ImmutableSet.of("audio", "img", "input", "source", "video");

  /**
   * Sets the {@code src} attribute for this element.
   *
   * <p>The attribute {@code src} with a {@code SafeUrl} value is allowed on these elements:
   *
   * <ul>
   *   <li>{@code audio}
   *   <li>{@code img}
   *   <li>{@code input}
   *   <li>{@code source}
   *   <li>{@code video}
   * </ul>
   *
   * @throws IllegalArgumentException if the {@code src} attribute with a {@code SafeUrl} value is
   *     not allowed on this element
   */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setSrc(SafeUrl value) {
    if (!SRC_SAFE_URL_ELEMENT_ALLOWLIST.contains(elementName)) {
      throw new IllegalArgumentException(
          "Attribute \"src\" with a SafeUrl value can only be used "
              + "by one of the following elements: "
              + SRC_SAFE_URL_ELEMENT_ALLOWLIST);
    }
    return setAttribute("src", value.getSafeUrlString());
  }

  /** Sets the {@code src} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setSrc(TrustedResourceUrl value) {
    return setAttribute("src", value.getTrustedResourceUrlString());
  }

  /** These elements are allowlisted to use srcdoc with a SafeHtml value. */
  private static final ImmutableSet<String> SRCDOC_SAFE_HTML_ELEMENT_ALLOWLIST =
      ImmutableSet.of("iframe");

  /**
   * Sets the {@code srcdoc} attribute for this element.
   *
   * <p>The attribute {@code srcdoc} with a {@code SafeHtml} value is allowed on these elements:
   *
   * <ul>
   *   <li>{@code iframe}
   * </ul>
   *
   * @throws IllegalArgumentException if the {@code srcdoc} attribute with a {@code SafeHtml} value
   *     is not allowed on this element
   */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setSrcdoc(SafeHtml value) {
    if (!SRCDOC_SAFE_HTML_ELEMENT_ALLOWLIST.contains(elementName)) {
      throw new IllegalArgumentException(
          "Attribute \"srcdoc\" with a SafeHtml value can only be used "
              + "by one of the following elements: "
              + SRCDOC_SAFE_HTML_ELEMENT_ALLOWLIST);
    }
    return setAttribute("srcdoc", value.getSafeHtmlString());
  }

  /** Sets the {@code start} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setStart(String value) {
    return setAttribute("start", value);
  }

  /** Sets the {@code step} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setStep(String value) {
    return setAttribute("step", value);
  }

  /** Sets the {@code style} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setStyle(SafeStyle value) {
    return setAttribute("style", value.getSafeStyleString());
  }

  /** Sets the {@code summary} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setSummary(String value) {
    return setAttribute("summary", value);
  }

  /** Sets the {@code tabindex} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setTabindex(String value) {
    return setAttribute("tabindex", value);
  }

  /** Values that can be passed to {@link #setTarget(TargetValue)}. */
  public enum TargetValue {

    /** Value of {@code _blank}. */
    BLANK("_blank"),
    /** Value of {@code _self}. */
    SELF("_self");

    private final String value;

    private TargetValue(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return value;
    }
  }

  /** Sets the {@code target} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setTarget(TargetValue value) {
    return setAttribute("target", value.toString());
  }

  /** Sets the {@code title} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setTitle(String value) {
    return setAttribute("title", value);
  }

  /** Sets the {@code translate} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setTranslate(String value) {
    return setAttribute("translate", value);
  }

  /** Sets the {@code type} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setType(String value) {
    return setAttribute("type", value);
  }

  /** Sets the {@code valign} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setValign(String value) {
    return setAttribute("valign", value);
  }

  /** Sets the {@code value} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setValue(String value) {
    return setAttribute("value", value);
  }

  /** Sets the {@code width} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setWidth(String value) {
    return setAttribute("width", value);
  }

  /** Sets the {@code wrap} attribute for this element. */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setWrap(String value) {
    return setAttribute("wrap", value);
  }

  /**
   * Sets a custom data attribute, {@code name}, to {@code value} for this element. {@code value}
   * must consist only of letters and {@code -}.
   *
   * @param name including the "data-" prefix, e.g. "data-tooltip"
   * @throws IllegalArgumentException if the attribute name isn't valid
   * @see
   *     http://www.w3.org/TR/html5/dom.html#embedding-custom-non-visible-data-with-the-data-*-attributes
   */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder setDataAttribute(@CompileTimeConstant final String name, String value) {
    if (!name.matches(VALID_DATA_ATTRIBUTES_REGEXP)) {
      throw new IllegalArgumentException(
          "Invalid data attribute name \""
              + name
              + "\"."
              + "Name must start with \"data-\" and be followed by letters and '-'.");
    }
    return setAttribute(name, value);
  }

  /**
   * Appends the given {@code htmls} as this element's content, in sequence.
   *
   * @throws IllegalStateException if this builder represents an element that does not contain HTML.
   */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder appendContent(SafeHtml... htmls) {
    appendContent(Arrays.asList(htmls));
    return this;
  }

  /**
   * Appends the given {@code htmls} as this element's content, in the sequence the Iterable returns
   * them.
   *
   * @throws IllegalStateException if this builder represents an element that does not contain HTML.
   */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder appendContent(Iterable<SafeHtml> htmls) {
    appendContent(htmls.iterator());
    return this;
  }

  /**
   * Appends the given {@code htmls} as this element's content, in the sequence the Iterator returns
   * them.
   *
   * @throws IllegalStateException if this builder represents represents an element that does not
   *     contain HTML.
   */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder appendContent(Iterator<SafeHtml> htmls) {
    checkSafeHtmlElement();
    while (htmls.hasNext()) {
      contents.add(htmls.next().getSafeHtmlString());
    }
    return this;
  }

  /**
   * Appends the given {@code script} as this element's content.
   *
   * @throws IllegalStateException if this builder represents an element that does not contain
   *     JavaScript.
   */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder appendScriptContent(SafeScript script) {
    checkSafeScriptElement();
    contents.add(script.getSafeScriptString());
    return this;
  }

  /**
   * Appends the given {@code style} as this element's content.
   *
   * @throws IllegalStateException if this builder represents an element that does not contain CSS.
   */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder appendStyleContent(SafeStyleSheet style) {
    checkSafeStyleSheetElement();
    contents.add(style.getSafeStyleSheetString());
    return this;
  }

  /**
   * Checks that this combination of rel value and href contract is safe.
   *
   * @param relValue is the value of rel or null if rel isn't present.
   * @throws IllegalArgumentException if this value and contract combination is not allowed.
   */
  private static void checkLinkDependentAttributes(
      @Nullable String relValue, AttributeContract hrefValueContract) {

    if (hrefValueContract.equals(AttributeContract.SAFE_URL)
        && relValue != null
        && !LINK_HREF_SAFE_URL_REL_ALLOWLIST.contains(relValue.toLowerCase(Locale.ENGLISH))) {
      throw new IllegalArgumentException(
          "SafeUrl values for the href attribute are not allowed on <link rel="
              + relValue
              + ">. Did you intend to use a TrustedResourceUrl?");
    }
  }

  private void checkSafeHtmlElement() {
    checkNotVoidElement();
    Preconditions.checkState(
        !SCRIPT_ELEMENTS.contains(elementName),
        "Element \"" + elementName + "\" requires SafeScript contents, not SafeHTML or text.");
    Preconditions.checkState(
        !STYLESHEET_ELEMENTS.contains(elementName),
        "Element \"" + elementName + "\" requires SafeStyleSheet contents, not SafeHTML or text.");
  }

  private void checkSafeScriptElement() {
    Preconditions.checkState(
        SCRIPT_ELEMENTS.contains(elementName),
        "Element \"" + elementName + "\" must not contain SafeScript.");
  }

  private void checkSafeStyleSheetElement() {
    Preconditions.checkState(
        STYLESHEET_ELEMENTS.contains(elementName),
        "Element \"" + elementName + "\" must not contain SafeStyleSheet.");
  }

  private void checkNotVoidElement() {
    Preconditions.checkState(
        !VOID_ELEMENTS.contains(elementName),
        "Element \"" + elementName + "\" is a void element and so cannot have content.");
  }

  /**
   * HTML-escapes and appends {@code text} to this element's content.
   *
   * @throws IllegalStateException if this builder represents a void element
   */
  @CanIgnoreReturnValue
  public SafeHtmlBuilder escapeAndAppendContent(String text) {
    // htmlEscape() already unicode-coerces.
    return appendContent(SafeHtmls.htmlEscape(text));
  }

  public SafeHtml build() {
    StringBuilder sb = new StringBuilder("<" + elementName);
    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      sb.append(" " + entry.getKey() + "=\"" + escapeHtmlInternal(entry.getValue()) + "\"");
    }

    boolean isVoid = VOID_ELEMENTS.contains(elementName);
    if (isVoid && useSlashOnVoid) {
      sb.append("/");
    }
    sb.append(">");
    if (!isVoid) {
      for (String content : contents) {
        sb.append(content);
      }
      sb.append("</" + elementName + ">");
    }
    return SafeHtmls.create(sb.toString());
  }

  @CanIgnoreReturnValue
  private SafeHtmlBuilder setAttribute(@CompileTimeConstant final String name, String value) {
    if (value == null) {
      throw new NullPointerException("setAttribute requires a non-null value.");
    }
    attributes.put(name, coerceToInterchangeValid(value));
    return this;
  }
}
