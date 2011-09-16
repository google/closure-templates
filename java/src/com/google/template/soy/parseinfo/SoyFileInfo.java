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
import com.google.common.collect.ImmutableSortedSet;


/**
 * Parsed info about a Soy file.
 *
 * @author Kai Huang
 */
public class SoyFileInfo {


  /**
   * Enum for whether there are prefix expressions in the 'css' tags that a CSS name appears in.
   * Note that it's possible for the same CSS name to appear in multiple 'css' tags, some of which
   * contain prefixes and some of which don't.
   */
  public static enum CssTagsPrefixPresence {
    ALWAYS,
    NEVER,
    SOMETIMES;
  }


  /** The source Soy file's name. */
  private final String fileName;

  /** The Soy file's namespace. */
  private final String namespace;

  /** Sorted set of params from all templates in this Soy file. */
  private final ImmutableSortedSet<String> paramsFromAllTemplates;

  /** List of templates in this Soy file. */
  private final ImmutableList<SoyTemplateInfo> templates;

  /** Map from each CSS name appearing in this file to its CssTagsPrefixPresence state. */
  private final ImmutableMap<String, CssTagsPrefixPresence> cssNamesMap;


  /**
   * @param fileName The source Soy file's name.
   * @param namespace The Soy file's namespace.
   * @param paramsFromAllTemplates Sorted list of params from all templates in this Soy file.
   * @param templates List of templates in this Soy file.
   */
  public SoyFileInfo(
      String fileName, String namespace, ImmutableSortedSet<String> paramsFromAllTemplates,
      ImmutableList<SoyTemplateInfo> templates,
      ImmutableMap<String, CssTagsPrefixPresence> cssNamesMap) {
    this.fileName = fileName;
    this.namespace = namespace;
    this.paramsFromAllTemplates = paramsFromAllTemplates;
    this.templates = templates;
    this.cssNamesMap = cssNamesMap;
  }


  /** Returns the source Soy file's name. */
  public String getFileName() {
    return fileName;
  }

  /** Returns the Soy file's namespace. */
  public String getNamespace() {
    return namespace;
  }

  /** Returns the set of params from all templates in this Soy file. */
  public ImmutableSortedSet<String> getParamsFromAllTemplates() {
    return paramsFromAllTemplates;
  }

  /** Returns the list of templates in this Soy file. */
  public ImmutableList<SoyTemplateInfo> getTemplates() {
    return templates;
  }

  /** Returns a map from each CSS name appearing in this file to its CssTagsPrefixPresence state. */
  public ImmutableMap<String, CssTagsPrefixPresence> getCssNames() {
    return cssNamesMap;
  }

}
