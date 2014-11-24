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

import com.google.common.base.Preconditions;
import com.google.template.soy.types.SoyType;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * A parameter declared in the template header.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
@Immutable
public final class HeaderParam extends TemplateParam {

  /** The original source string for the param type. May be null if unavailable. */
  @Nullable private final String typeSrc;

  public HeaderParam(
      String name,
      String typeSrc,
      SoyType type,
      boolean isRequired,
      boolean isInjected,
      @Nullable String desc) {
    super(name, type, isRequired, isInjected, desc);
    Preconditions.checkArgument(type != null);
    this.typeSrc = typeSrc;
  }

  private HeaderParam(HeaderParam param) {
    super(param);
    this.typeSrc = param.typeSrc;
  }

  @Override public DeclLoc declLoc() {
    return DeclLoc.HEADER;
  }

  /** Returns the original source string for the param type. May be null if unavailable. */
  public String typeSrc() {
    return typeSrc;
  }

  @Override public HeaderParam cloneEssential() {
    // Note: 'typeSrc' and 'desc' are nonessential.
    HeaderParam headerParam = new HeaderParam(name(), null, type, isRequired(), isInjected(), null);
    headerParam.setLocalVariableIndex(localVariableIndex());
    return headerParam;
  }

  @Override public boolean equals(Object o) {
    if (this == o) { return true; }
    if (o == null || this.getClass() != o.getClass()) { return false; }
    HeaderParam other = (HeaderParam) o;
    // Note: 'typeSrc' is nonessential.
    return super.abstractEquals(o) && this.type.equals(other.type);
  }

  @Override public int hashCode() {
    // Note: 'typeSrc' is nonessential.
    return super.abstractHashCode() * 31 + type.hashCode();
  }

  @Override public HeaderParam clone() {
    return new HeaderParam(this);
  }
}
