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

package com.google.template.soy.soytree;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;
import com.google.template.soy.soytree.defn.TemplateHeaderVarDefn;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.soytree.defn.TemplateStateVar;
import java.util.List;
import java.util.Set;

/**
 * Builder for TemplateElementNode.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class TemplateElementNodeBuilder extends TemplateNodeBuilder {

  private static final SoyErrorKind DUPLICATE_DECLARATION =
      SoyErrorKind.of("Param ''{0}'' is a duplicate of state var ''{0}''.");

  protected static final ImmutableSet<String> BANNED_ATTRIBUTE_NAMES =
      ImmutableSet.of("autoescape", "kind", "stricthtml", "visibility");

  private static final SoyErrorKind BANNED_ATTRIBUTE_NAMES_ERROR =
      SoyErrorKind.of("Attribute ''{0}'' is not allowed on Soy elements.");

  private List<CommandTagAttribute> attrs = ImmutableList.of();

  /** The state variables from template header. */
  private ImmutableList<TemplateStateVar> stateVars = ImmutableList.of();

  /** @param soyFileHeaderInfo Info from the containing Soy file's header declarations. */
  public TemplateElementNodeBuilder(
      SoyFileHeaderInfo soyFileHeaderInfo, ErrorReporter errorReporter) {
    super(soyFileHeaderInfo, errorReporter);
    setAutoescapeInfo(AutoescapeMode.STRICT, SanitizedContentKind.HTML, this.sourceLocation);
  }

  @Override
  public TemplateElementNodeBuilder setId(int id) {
    return (TemplateElementNodeBuilder) super.setId(id);
  }

  @Override
  public TemplateElementNodeBuilder setSourceLocation(SourceLocation location) {
    return (TemplateElementNodeBuilder) super.setSourceLocation(location);
  }

  @Override
  public TemplateNodeBuilder setCommandValues(
      Identifier templateName, List<CommandTagAttribute> attrs) {
    this.attrs = attrs;
    this.cmdText = templateName.identifier() + " " + Joiner.on(' ').join(attrs);
    setCommonCommandValues(attrs);

    setTemplateNames(
        soyFileHeaderInfo.namespace + templateName.identifier(),
        templateName.identifier());
    return this;
  }

  public TemplateNodeBuilder setStateVars(List<TemplateStateVar> newStateVars) {
    this.stateVars = ImmutableList.copyOf(newStateVars);
    checkDuplicateHeaderVars(params, stateVars, errorReporter);
    return this;
  }

  @Override
  public TemplateElementNodeBuilder setSoyDoc(String soyDoc, SourceLocation soyDocLocation) {
    return (TemplateElementNodeBuilder) super.setSoyDoc(soyDoc, soyDocLocation);
  }

  @Override
  public TemplateElementNodeBuilder addParams(Iterable<? extends TemplateParam> allParams) {
    super.addParams(allParams);
    checkDuplicateHeaderVars(params, stateVars, errorReporter);
    return this;
  }

  @Override
  public TemplateElementNode build() {
    Preconditions.checkState(id != null && cmdText != null);
    for (CommandTagAttribute attr : attrs) {
      if (BANNED_ATTRIBUTE_NAMES.contains(attr.getName().identifier())) {
        this.errorReporter.report(
            this.sourceLocation, BANNED_ATTRIBUTE_NAMES_ERROR, attr.getName().identifier());
      }
    }
    return new TemplateElementNode(this, soyFileHeaderInfo, params, stateVars);
  }

  /**
   * Check for duplicate header variable names and append error text for each duplicate to the
   * `errorReporter`. For example, this is an error:
   *
   * <pre>{@code
   * {@param s: bool}
   * {@state s: bool}
   * }</pre>
   *
   * Note that it is not possible to have duplicate names of the same declaration type. Any
   * duplicate {@code @state} or {@code @param} will have been flagged as error during the resolve-
   * names pass or in {@link #addParams(Iterable)}.
   */
  @VisibleForTesting
  static void checkDuplicateHeaderVars(
      ImmutableList<? extends TemplateHeaderVarDefn> params,
      ImmutableList<? extends TemplateHeaderVarDefn> stateVars,
      ErrorReporter errorReporter) {

    final Set<String> stateVarNames =
        stateVars.stream().map(TemplateHeaderVarDefn::name).collect(toImmutableSet());

    Iterable<? extends TemplateHeaderVarDefn> duplicateVars =
        Iterables.filter(params, param -> stateVarNames.contains(param.name()));
    for (TemplateHeaderVarDefn duplicateVar : duplicateVars) {
      errorReporter.report(duplicateVar.nameLocation(), DUPLICATE_DECLARATION, duplicateVar.name());
    }
  }
}
