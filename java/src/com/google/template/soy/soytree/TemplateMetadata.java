/*
 * Copyright 2018 Google Inc.
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

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.types.TemplateType;
import javax.annotation.Nullable;

/**
 * An abstract representation of a template that provides the minimal amount of information needed
 * compiling against dependency templates.
 *
 * <p>When compiling with dependencies the compiler needs to examine certain information from
 * dependent templates in order to validate calls and escape call sites. Traditionally, the Soy
 * compiler accomplished this by having each compilation parse all transitive dependencies. This is
 * an expensive solution. So instead of that we instead use this object to represent the minimal
 * information we need about dependencies.
 *
 * <p>The APIs on this class mirror ones available on {@link TemplateNode}.
 */
public interface TemplateMetadata {

  SoyFileKind getSoyFileKind();

  /**
   * The source location of the template. For non {@code SOURCE} templates this will merely refer to
   * the file path, line and column information isn't recorded.
   */
  SourceLocation getSourceLocation();

  @Nullable
  HtmlElementMetadataP getHtmlElement();

  @Nullable
  SoyElementMetadataP getSoyElement();

  String getTemplateName();

  /** Guaranteed to be non-null for deltemplates or mod templates, null otherwise. */
  @Nullable
  String getDelTemplateName();

  /**
   * Guaranteed to be non-null for deltemplates or mod templates (possibly empty string), null
   * otherwise.
   */
  @Nullable
  String getDelTemplateVariant();

  TemplateType getTemplateType();

  Visibility getVisibility();

  @Nullable
  String getModName();

  boolean getComponent();
}
