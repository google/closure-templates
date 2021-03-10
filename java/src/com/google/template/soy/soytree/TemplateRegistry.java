/*
 * Copyright 2011 Google Inc.
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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.shared.internal.DelTemplateSelector;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.TemplateType;
import com.google.template.soy.types.TemplateType.TemplateKind;
import com.google.template.soy.types.UnionType;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A registry or index of all in-scope templates.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public interface TemplateRegistry {

  TemplateRegistry EMPTY = FileSetTemplateRegistry.builder(ErrorReporter.exploding()).build();

  /** Look up possible targets for a call. */
  default ImmutableList<TemplateType> getTemplates(CallNode node) {
    if (node instanceof CallBasicNode) {
      SoyType calleeType = ((CallBasicNode) node).getCalleeExpr().getType();
      if (calleeType == null) {
        return ImmutableList.of();
      }
      if (calleeType.getKind() == SoyType.Kind.TEMPLATE) {
        return ImmutableList.of((TemplateType) calleeType);
      } else if (calleeType.getKind() == SoyType.Kind.UNION) {
        ImmutableList.Builder<TemplateType> signatures = ImmutableList.builder();
        for (SoyType member : ((UnionType) calleeType).getMembers()) {
          // Rely on CheckTemplateCallsPass to catch this with nice error messages.
          Preconditions.checkState(member.getKind() == SoyType.Kind.TEMPLATE);
          signatures.add((TemplateType) member);
        }
        return signatures.build();
      } else if (calleeType.getKind() == SoyType.Kind.UNKNOWN) {
        // We may end up with UNKNOWN here for external calls.
        return ImmutableList.of();
      } else {
        // Rely on previous passes to catch this with nice error messages.
        throw new IllegalStateException(
            "Unexpected type in call: " + calleeType.getClass() + " - " + node.toSourceString());
      }
    } else {
      String calleeName = ((CallDelegateNode) node).getDelCalleeName();
      return getDelTemplateSelector().delTemplateNameToValues().get(calleeName).stream()
          .map(TemplateMetadata::getTemplateType)
          .collect(toImmutableList());
    }
  }

  @Nullable
  TemplateMetadata getMetadata(String templateFqn);

  default TemplateMetadata getMetadata(TemplateNode node) {
    return Preconditions.checkNotNull(getMetadata(node.getTemplateName()));
  }

  /**
   * Retrieves a template or element given the template name.
   *
   * @param templateFqn The basic template name to retrieve.
   * @return The corresponding template/element, or null if the name is not defined.
   */
  @Nullable
  default TemplateMetadata getBasicTemplateOrElement(String templateFqn) {
    TemplateMetadata metadata = getMetadata(templateFqn);
    if (metadata == null) {
      return null;
    }
    TemplateKind kind = metadata.getTemplateType().getTemplateKind();
    return kind == TemplateKind.BASIC || kind == TemplateKind.ELEMENT ? metadata : null;
  }

  /** Returns a multimap from delegate template name to set of keys. */
  DelTemplateSelector<TemplateMetadata> getDelTemplateSelector();

  /**
   * Returns all registered templates ({@link TemplateBasicNode basic} and {@link
   * TemplateDelegateNode delegate} nodes), in no particular order.
   */
  ImmutableCollection<TemplateMetadata> getAllTemplates();

  /**
   * Gets the content kind that a call results in. If used with delegate calls, the delegate
   * templates must use strict autoescaping. This relies on the fact that all delegate calls must
   * have the same kind when using strict autoescaping. This is enforced by CheckDelegatesPass.
   *
   * @param node The {@link CallBasicNode} or {@link CallDelegateNode}.
   * @return The kind of content that the call results in.
   */
  default Optional<SanitizedContentKind> getCallContentKind(CallNode node) {
    ImmutableList<TemplateType> templateNodes = getTemplates(node);
    // For per-file compilation, we may not have any of the delegate templates in the compilation
    // unit.
    if (!templateNodes.isEmpty()) {
      return Optional.of(templateNodes.get(0).getContentKind().getSanitizedContentKind());
    }
    // The template node may be null if the template is being compiled in isolation.
    return Optional.empty();
  }
}
