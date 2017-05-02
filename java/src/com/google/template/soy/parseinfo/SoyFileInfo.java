/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.parseinfo;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Parsed info about a Soy file.
 *
 */
public class SoyFileInfo {

  /**
   * Enum for whether there are prefix expressions in the 'css' tags that a CSS name appears in.
   * Note that it's possible for the same CSS name to appear in multiple 'css' tags, some of which
   * contain prefixes and some of which don't.
   */
  public enum CssTagsPrefixPresence {
    ALWAYS,
    NEVER,
    SOMETIMES;
  }

  /** The source Soy file's name. */
  private final String fileName;

  /** The Soy file's namespace. */
  private final String namespace;

  /** List of public basic templates in this Soy file. */
  private final ImmutableList<SoyTemplateInfo> templates;

  /**
   * List of all deltemplate names in the Soy file.
   *
   * <p>These are the mangled names.
   */
  private final ImmutableList<String> deltemplateNames;

  /** Map from each CSS name appearing in this file to its CssTagsPrefixPresence state. */
  private final ImmutableMap<String, CssTagsPrefixPresence> cssNameMap;

  /**
   * Constructor for internal use only.
   *
   * <p>Important: Do not construct SoyFileInfo objects outside of Soy internal or Soy-generated
   * code. User code that constructs SoyFileInfo objects will be broken by future Soy changes.
   *
   * @param fileName The source Soy file's name.
   * @param namespace The Soy file's namespace.
   * @param templates List of templates in this Soy file.
   */
  public SoyFileInfo(
      String fileName,
      String namespace,
      ImmutableList<SoyTemplateInfo> templates,
      ImmutableMap<String, CssTagsPrefixPresence> cssNameMap,
      ImmutableList<String> deltemplateNames) {
    this.fileName = fileName;
    this.namespace = namespace;
    this.templates = templates;
    this.cssNameMap = cssNameMap;
    this.deltemplateNames = deltemplateNames;
  }

  /** Returns the source Soy file's name. */
  public final String getFileName() {
    return fileName;
  }

  /** Returns the Soy file's namespace. */
  public final String getNamespace() {
    return namespace;
  }

  /** Returns the list of templates in this Soy file. */
  public final ImmutableList<SoyTemplateInfo> getTemplates() {
    return templates;
  }

  /** Returns a map from each CSS name appearing in this file to its CssTagsPrefixPresence state. */
  public final ImmutableMap<String, CssTagsPrefixPresence> getCssNames() {
    return cssNameMap;
  }

  /**
   * Returns a list of any protocol buffer types used by the templates. The elements of the list are
   * the default object for a given proto type.
   */
  public ImmutableList<Object> getProtoTypes() {
    return ImmutableList.of();
  }

  /** Returns the fully qualified names of all deltemplates in the file. */
  public final ImmutableList<String> getDelTemplateNames() {
    return deltemplateNames;
  }
}
