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
 *
 * <p>TODO(b/32091399): destroy this.
 */
public enum SyntaxVersion {
  // NOTE: new entries should NEVER be added. Supporting multiple language versions has been a
  // disaster in Soy.
  /**
   * V1.0 allows these legacy deprecated items:
   *
   * <ul>
   *   <li>Some prevalent forms of incorrect param declarations in template SoyDoc.
   *   <li>Expressions that cannot be parsed as a Soy V2 expression.
   *   <li>Not declaring all the parameters you use or using all the parameters you delcare is
   *       allowed.
   *   <li>Failure to pass a required parameter is not an error.
   * </ul>
   */
  V1_0,
  /** V2.0 is the syntax that has historically been enforced by the Soy compiler. */
  V2_0,
  ;

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
