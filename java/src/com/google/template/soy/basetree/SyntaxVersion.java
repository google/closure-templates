/*
 * Copyright 2013 Google Inc.
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

package com.google.template.soy.basetree;

import com.google.common.collect.ImmutableMap;
import java.util.Map;

/**
 * Enum for the syntax version.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public enum SyntaxVersion {
  /**
   * V1.0 allows these legacy deprecated items:
   *
   * <ul>
   *   <li>No 'namespace' declaration in Soy file.
   *   <li>Some prevalent forms of incorrect param declarations in template SoyDoc.
   *   <li>Template name that isn't a dot followed by an identifier (i.e. relative to namespace).
   *   <li>Expressions that cannot be parsed as a Soy V2 expression.
   * </ul>
   */
  V1_0,
  /** V2.0 is the syntax that has historically been enforced by the Soy compiler. */
  V2_0,
  /**
   * Syntax that causes V2.3+ to be inferred:
   *
   * <ul>
   *   <li>Usage of template header @param decls (as opposed to SoyDoc @param decls).
   * </ul>
   *
   * Behavior changes if V2.3+ is declared (not inferred):
   *
   * <ul>
   *   <li>Logical operators ('and', 'or') output type bool instead of unknown.
   * </ul>
   *
   * Checks:
   *
   * <ul>
   *   <li>Type bool can no longer be used in nonbool contexts.
   *   <li>Type bool can no longer be printed.
   * </ul>
   */
  V2_3,
  /**
   * Syntax that causes V2.4+ to be inferred:
   *
   * <ul>
   *   <li>Usage of template header @inject decls (as opposed to $ij variables).
   * </ul>
   */
  V2_4,
  /** For internal use only. Represents a nonexistent future version. */
  V9_9,
  ;

  /** Current maximum version that users can declare. */
  // TODO: This is ready to be increased to V2.3 whenever some project actually wants to use it.
  // We're just being conservative in not increasing it yet until actually needed, in case we happen
  // to think up some new checks that we want to add to syntax versions 2.1-2.3.
  private static final SyntaxVersion MAX_PUBLIC_SYNTAX_VERSION = V2_0;

  private static final Map<String, SyntaxVersion> NAME_TO_INSTANCE_MAP;

  static {
    ImmutableMap.Builder<String, SyntaxVersion> nameToInstanceMapBuilder = ImmutableMap.builder();
    for (SyntaxVersion version : SyntaxVersion.values()) {
      nameToInstanceMapBuilder.put(version.name, version);
    }
    NAME_TO_INSTANCE_MAP = nameToInstanceMapBuilder.build();
  }

  public static SyntaxVersion forName(String name) {
    SyntaxVersion version = NAME_TO_INSTANCE_MAP.get(name);
    if (version == null) {
      throw new RuntimeException("Invalid Soy syntax version \"" + name + "\".");
    } else if (version.num > MAX_PUBLIC_SYNTAX_VERSION.num) {
      throw new RuntimeException(
          "It appears you are one of the first users attempting to manually declare Soy syntax"
              + " version "
              + name
              + ". It's not currently enabled for declaration, but the Soy"
              + " team can probably enable it if you drop them a note.");
    } else {
      return version;
    }
  }

  /** The string name. */
  public final String name;

  /** The number for ordering. */
  public final int num;

  SyntaxVersion() {
    String[] parts = this.name().substring(1).split("_", 2);
    this.name = parts[0] + "." + parts[1];
    this.num = Integer.parseInt(parts[0]) * 1000 + Integer.parseInt(parts[1]);
  }

  @Override
  public String toString() {
    return name;
  }
}
