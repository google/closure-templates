/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.jbcsrc.shared;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.template.soy.data.SanitizedContent.ContentKind;

import java.lang.annotation.Retention;

/**
 * An annotation added to compiled templates for preserving certain bits of template metadata.
 * 
 * <p>For now this just records the strict content kind of a template (if any), in the future 
 * consider adding:
 * <ul>
 *     <li>Names of called templates
 *     <li>Is this a deltemplate? if so what delpackage? variant?
 *     <li>Metadata about params and their requisiteness settings.
 * </ul>
 */
@Retention(RUNTIME)
public @interface TemplateMetadata {
  /**
   * The content kind of the template.  This will be one of the {@link ContentKind} constant names
   * or {@code ""} which means that this isn't a strict template.
   */
  String contentKind();
}
