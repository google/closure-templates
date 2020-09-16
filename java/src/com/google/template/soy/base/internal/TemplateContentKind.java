/*
 * Copyright 2020 Google Inc.
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.error.SoyErrorKind;
import java.util.Optional;

/**
 * The different types for template kind="" values. These have a many-to-one relationship with
 * {@link SanitizedContentKind} (for example, kind="html", kind="html<?>" would all map to
 * ContentKind.HTML).
 */
public abstract class TemplateContentKind {

  // TODO(b/163796852): Update error to add element.
  public static final SoyErrorKind INVALID_ATTRIBUTE_VALUE =
      SoyErrorKind.of(
          "Invalid value for template attribute ''kind'', expected one of "
              + BasicTemplateContentKind.KINDS_BY_ATTR_VALUE.keySet()
              + ".");

  /**
   * Parses and returns the kind for the given kind= attribute value. Or {@code null} if it is
   * invalid.
   */
  public static Optional<TemplateContentKind> fromAttributeValue(String attrValue) {
    checkNotNull(attrValue);
    if (attrValue.equals("html<?>")) {
      return Optional.of(ElementContentKind.ELEMENT);
    }
    if (BasicTemplateContentKind.KINDS_BY_ATTR_VALUE.containsKey(attrValue)) {
      return Optional.of(BasicTemplateContentKind.KINDS_BY_ATTR_VALUE.get(attrValue));
    }
    return Optional.empty();
  }

  public static TemplateContentKind fromSanitizedContentKind(
      SanitizedContentKind sanitizedContentKind) {
    checkNotNull(sanitizedContentKind);

    return BasicTemplateContentKind.KINDS_BY_KIND.get(sanitizedContentKind);
  }

  public static final TemplateContentKind HTML =
      BasicTemplateContentKind.KINDS_BY_ATTR_VALUE.get(
          SanitizedContentKind.HTML.asAttributeValue());

  /**
   * Simple template content kinds that map 1:1 with {@link SanitizedContentKind} types. For
   * example, "uri", "css", and "html" are basic kinds, but "element" and "element<div>" are not.
   */
  public static class BasicTemplateContentKind extends TemplateContentKind {

    private static final ImmutableMap<SanitizedContentKind, TemplateContentKind> KINDS_BY_KIND;
    private static final ImmutableMap<String, TemplateContentKind> KINDS_BY_ATTR_VALUE;

    static {
      ImmutableMap.Builder<SanitizedContentKind, TemplateContentKind> kindsByKind =
          new ImmutableMap.Builder<>();
      ImmutableMap.Builder<String, TemplateContentKind> kindsByAttributeValue =
          new ImmutableMap.Builder<>();
      for (SanitizedContentKind kind : SanitizedContentKind.values()) {
        TemplateContentKind contentKind;
        if (kind == SanitizedContentKind.HTML_ELEMENT) {
          contentKind = new ElementContentKind("html<?>");
        } else {
          contentKind = new BasicTemplateContentKind(kind);
        }
        kindsByKind.put(kind, contentKind);
        kindsByAttributeValue.put(kind.asAttributeValue(), contentKind);
      }
      KINDS_BY_KIND = kindsByKind.build();
      KINDS_BY_ATTR_VALUE = kindsByAttributeValue.build();
    }

    private final SanitizedContentKind sanitizedContentKind;

    private BasicTemplateContentKind(SanitizedContentKind kind) {
      this.sanitizedContentKind = checkNotNull(kind);
    }

    @Override
    public String asAttributeValue() {
      return sanitizedContentKind.asAttributeValue();
    }

    @Override
    public SanitizedContentKind getSanitizedContentKind() {
      return sanitizedContentKind;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof BasicTemplateContentKind)) {
        return false;
      }
      BasicTemplateContentKind other = (BasicTemplateContentKind) o;
      return this.sanitizedContentKind == other.sanitizedContentKind;
    }

    @Override
    public int hashCode() {
      return this.sanitizedContentKind.hashCode();
    }

    @Override
    public String toString() {
      return this.sanitizedContentKind.toString();
    }
  }

  /**
   * Class for kind="element" types. Currently this just supports "element", but will likely be
   * expanded to allow "element<div>", etc. TODO: Probably pull this into another file when we add
   * element<div> etc?
   */
  public static class ElementContentKind extends TemplateContentKind {

    public static final ElementContentKind ELEMENT = new ElementContentKind("html<?>");

    // TODO(b/163796852): Flip when ready.
    public static final boolean IS_GA = false;

    private final String attrValue;

    private ElementContentKind(String attrValue) {
      this.attrValue = attrValue;
    }

    @Override
    public String asAttributeValue() {
      return attrValue;
    }

    @Override
    public SanitizedContentKind getSanitizedContentKind() {
      return SanitizedContentKind.HTML_ELEMENT;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof ElementContentKind)) {
        return false;
      }
      ElementContentKind other = (ElementContentKind) o;
      return this.attrValue.equals(other.attrValue);
    }

    @Override
    public int hashCode() {
      return this.attrValue.hashCode();
    }

    @Override
    public String toString() {
      return this.attrValue;
    }
  }

  /** Returns the kind formatted as it would be for an attribute value. */
  public abstract String asAttributeValue();

  /** Returns the sanitized content type for this template kind. */
  public abstract SanitizedContentKind getSanitizedContentKind();

  @Override
  public abstract boolean equals(Object o);

  @Override
  public abstract int hashCode();

  /** String representation used in error messages. */
  @Override
  public abstract String toString();
}
