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

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.base.internal.SoyFileKind;
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
@AutoValue
public abstract class TemplateMetadata {
  // TODO(lukes): add support for representing template parameters
  // TODO(lukes): add a serialized form of this object

  /** Builds a Template from a parsed TemplateNode. */
  public static TemplateMetadata fromTemplate(TemplateNode template) {
    TemplateMetadata.Builder builder =
        new AutoValue_TemplateMetadata.Builder()
            .setTemplateName(template.getTemplateName())
            .setSourceLocation(template.getSourceLocation())
            .setSoyFileKind(template.getParent().getSoyFileKind())
            .setContentKind(template.getContentKind())
            .setStrictHtml(template.isStrictHtml())
            .setDelPackageName(template.getDelPackageName())
            .setVisibility(template.getVisibility())
            .setTemplateNodeForTemporaryCompatibility(template)
            .setTemplateNode(
                template.getParent().getSoyFileKind() == SoyFileKind.SRC ? template : null);
    switch (template.getKind()) {
      case TEMPLATE_BASIC_NODE:
        builder.setTemplateKind(Kind.BASIC);
        break;
      case TEMPLATE_DELEGATE_NODE:
        builder.setTemplateKind(Kind.DELTEMPLATE);
        TemplateDelegateNode deltemplate = (TemplateDelegateNode) template;
        builder.setDelTemplateName(deltemplate.getDelTemplateName());
        builder.setDelTemplateVariant(deltemplate.getDelTemplateVariant());
        break;
      case TEMPLATE_ELEMENT_NODE:
        builder.setTemplateKind(Kind.ELEMENT);
        break;
      default:
        throw new AssertionError("unexpected template kind: " + template.getKind());
    }
    return builder.build();
  }

  /** The kind of template. */
  public enum Kind {
    BASIC,
    DELTEMPLATE,
    ELEMENT;
  }

  public abstract SoyFileKind getSoyFileKind();

  public abstract SourceLocation getSourceLocation();

  public abstract Kind getTemplateKind();

  public abstract String getTemplateName();

  /** Guaranteed to be non-null for deltemplates, null otherwise. */
  @Nullable
  public abstract String getDelTemplateName();

  @Nullable
  public abstract String getDelTemplateVariant();

  @Nullable
  public abstract SanitizedContentKind getContentKind();

  public abstract boolean isStrictHtml();

  public abstract Visibility getVisibility();

  @Nullable
  public abstract String getDelPackageName();

  /**
   * The actual parsed template. Will only be non-null for templates with {@link #getSoyFileKind} of
   * {@link SoyFileKind#SRC}
   */
  @Nullable
  public abstract TemplateNode getTemplateNode();

  /**
   * Same as {@link #getTemplateNode} but is available for non {@link SoyFileKind#SRC} templates.
   * This is provided for temporary compatibility while we fill out this API.
   *
   * <p>TODO(b/63212073): migrate all callers off of this API
   */
  public abstract TemplateNode getTemplateNodeForTemporaryCompatibility();

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setSoyFileKind(SoyFileKind location);

    abstract Builder setSourceLocation(SourceLocation location);

    abstract Builder setTemplateKind(Kind kind);

    abstract Builder setTemplateName(String templateName);

    abstract Builder setDelTemplateName(String delTemplateName);

    abstract Builder setDelTemplateVariant(String delTemplateVariant);

    abstract Builder setContentKind(@Nullable SanitizedContentKind contentKind);

    abstract Builder setTemplateNode(@Nullable TemplateNode template);

    abstract Builder setTemplateNodeForTemporaryCompatibility(TemplateNode template);

    abstract Builder setStrictHtml(boolean strictHtml);

    abstract Builder setDelPackageName(@Nullable String delPackageName);

    abstract Builder setVisibility(Visibility visibility);

    final TemplateMetadata build() {
      TemplateMetadata built = autobuild();
      if (built.getSoyFileKind() == SoyFileKind.SRC) {
        checkState(built.getTemplateNode() != null, "source templates must have a templatenode");
      } else {
        checkState(
            built.getTemplateNode() == null, "non-source templates must not have a templatenode");
      }
      if (built.getTemplateKind() == Kind.DELTEMPLATE) {
        checkState(built.getDelTemplateName() != null, "Deltemplates must have a deltemplateName");
      } else {
        checkState(
            built.getDelTemplateVariant() == null, "non-Deltemplates must not have a variant");
        checkState(
            built.getDelTemplateName() == null, "non-Deltemplates must not have a deltemplateName");
      }
      return built;
    }

    abstract TemplateMetadata autobuild();
  }
}
