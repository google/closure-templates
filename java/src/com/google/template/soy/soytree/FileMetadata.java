/*
 * Copyright 2021 Google Inc.
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

package com.google.template.soy.soytree;

import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.types.FunctionType;
import com.google.template.soy.types.SoyType;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Metadata about a soy file that is available from soy header deps or after the AST is mostly
 * processed by the compiler.
 */
public interface FileMetadata extends PartialFileMetadata {

  /** Java object version of {@link ConstantP}. */
  interface Constant {
    String getName();

    SoyType getType();
  }

  /** Java object version of {@link ExternP}. */
  interface Extern {
    String getName();

    FunctionType getSignature();
  }

  @Nullable
  TemplateMetadata getTemplate(String name);

  /** Returns all templates in this file, including possible naming collisions. */
  Collection<TemplateMetadata> getTemplates();

  @Nullable
  Constant getConstant(String name);

  Collection<? extends Constant> getConstants();

  Collection<? extends Extern> getExterns();

  List<? extends Extern> getExterns(String name);

  SoyFileKind getSoyFileKind();
}
