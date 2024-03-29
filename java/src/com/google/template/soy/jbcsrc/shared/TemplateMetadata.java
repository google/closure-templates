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
 *
 * <ul>
 *   <li>Metadata about params and their requisiteness settings.
 * </ul>
 */
@Retention(RUNTIME)
public @interface TemplateMetadata {
  /** The content kind of the template. */
  ContentKind contentKind();

  /**
   * Whether or not the {@link #positionalParams} value is valid, to distinguish empty and absent.
   *
   * <p>For Testing only.
   */
  boolean hasPositionalSignature() default false;
  /**
   * Returns the list of params in order for templates with positional signatures.
   *
   * <p>For Testing only.
   */
  String[] positionalParams() default {};

  /**
   * Returns the list of injected params, both {@code $ij.foo} variables and {@code @inject} params
   */
  String[] injectedParams() default {};

  /** The required css for this template, plus all file level requiredcss. */
  String[] requiredCssNames() default {};

  /** The required css for this template, plus all file level requiredcss. */
  String[] requiredCssPaths() default {};

  /** Returns the fully qualified names of all the basic templates called by this template. */
  String[] callees() default {};

  /** Returns the fully qualified names of all the delegate templates called by this template. */
  String[] delCallees() default {};

  /**
   * Returns metadata for deltemplates. If this is not a deltemplate it will have an empty {@link
   * DelTemplateMetadata#name}.
   */
  DelTemplateMetadata deltemplateMetadata() default @DelTemplateMetadata;

  @Retention(RUNTIME)
  @interface DelTemplateMetadata {
    /**
     * The name of the {modname} this is in. If this is a default deltemplate it will be {@code ""}.
     */
    String modName() default "";

    /** The name of the deltemplate this template is implementing. */
    String name() default "";

    /** The variant of the deltemplate */
    String variant() default "";
  }
}
