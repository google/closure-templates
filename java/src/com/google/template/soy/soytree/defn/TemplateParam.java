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

package com.google.template.soy.soytree.defn;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.types.SoyType;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * An explicitly declared template parameter.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
@Immutable
public abstract class TemplateParam extends AbstractVarDefn implements TemplateHeaderVarDefn {
  /** Enum for the location of the declaration. */
  public static enum DeclLoc {
    // Declaration in template SoyDoc, e.g.
    //     @param foo Blah blah blah.
    SOY_DOC,
    // Declaration in template header, e.g.
    //     {@param foo: list<int>}  /** Blah blah blah. */
    HEADER,
  }

  private final String desc;

  /** Whether the param is required. */
  private final boolean isRequired;

  /** Whether the param is an injected param. */
  private final boolean isInjected;

  public TemplateParam(
      String name,
      SoyType type,
      boolean isRequired,
      boolean isInjected,
      @Nullable String desc,
      @Nullable SourceLocation nameLocation) {
    super(name, nameLocation, type);
    this.isRequired = isRequired;
    this.isInjected = isInjected;
    this.desc = desc;
  }

  TemplateParam(TemplateParam param) {
    super(param);
    this.isRequired = param.isRequired;
    this.isInjected = param.isInjected;
    this.desc = param.desc;
  }

  @Override
  public Kind kind() {
    return Kind.PARAM;
  }

  /** Returns whether the param is an injected (declared with {@code @inject}) or not. */
  @Override
  public boolean isInjected() {
    return isInjected;
  }

  @Override
  public boolean isRequired() {
    return isRequired;
  }

  @Override
  public @Nullable String desc() {
    return desc;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{name = " + name() + ", desc = " + desc + "}";
  }

  /** Returns the location of the parameter declaration. */
  public abstract DeclLoc declLoc();

  public abstract TemplateParam copyEssential();
}
