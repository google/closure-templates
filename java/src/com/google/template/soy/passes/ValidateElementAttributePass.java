/*
 * Copyright 2020 Google Inc.
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

package com.google.template.soy.passes;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.template.soy.passes.RewriteElementAttributePass.getElementOpen;
import static java.util.stream.Collectors.toMap;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.TemplateContentKind.ElementContentKind;
import com.google.template.soy.compilermetrics.Impression;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.FileSetMetadata;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.AttrParam;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Enforces some rules on the usage of @attribute parameters within a template that can run before
 * type resolution has run.
 */
@RunAfter({
  ResolveNamesPass.class, // Needs full template names resolved.
  SoyElementPass.class // Uses HtmlElementMetadataP
})
@RunBefore({
  FinalizeTemplateRegistryPass.class, // Mutates template metadata
  SoyElementCompositionPass.class,
  AutoescaperPass.class // since it inserts print directives
})
final class ValidateElementAttributePass implements CompilerFileSetPass {

  private static final SoyErrorKind DELTEMPLATE_USING_ELEMENT_CONTENT_KIND =
      SoyErrorKind.of(
          "Deltemplates cannot set kind=\"html<...>\".",
          Impression.ERROR_ELEMENT_ATTRIBUTE_PASS_DELTEMPLATE_USING_ELEMENT_CONTENT_KIND);

  private static final SoyErrorKind UNUSED_ATTRIBUTE =
      SoyErrorKind.of(
          "Declared @attribute unused in template element.",
          Impression.ERROR_ELEMENT_ATTRIBUTE_PASS_UNUSED_ATTRIBUTE);

  private static final SoyErrorKind ATTRIBUTE_USED_OUTSIDE_OF_TAG =
      SoyErrorKind.of(
          "Attributes may not be referenced explicitly.",
          Impression.ERROR_ELEMENT_ATTRIBUTE_PASS_ATTRIBUTE_USED_OUTSIDE_OF_TAG);

  private static final SoyErrorKind UNRECOGNIZED_ATTRIBUTE =
      SoyErrorKind.of(
          "''{0}'' is not a declared @attribute of the template.{1}",
          Impression.ERROR_ELEMENT_ATTRIBUTE_PASS_UNRECOGNIZED_ATTRIBUTE,
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind PLAIN_ATTRIBUTE =
      SoyErrorKind.of(
          "HTML attribute masks Soy attribute. Did you mean ''{0}''?",
          Impression.ERROR_ELEMENT_ATTRIBUTE_PASS_PLAIN_ATTRIBUTE);

  private static final SoyErrorKind ATTRIBUTE_NOT_REQUIRED =
      SoyErrorKind.of(
          "@attribute ''{0}'' must be set as optional to be used here.",
          Impression.ERROR_ELEMENT_ATTRIBUTE_PASS_ATTRIBUTE_NOT_REQUIRED);

  private static final SoyErrorKind ROOT_TAG_KIND_MISMATCH =
      SoyErrorKind.of(
          "Expected root tag to be ''{0}''.",
          Impression.ERROR_ELEMENT_ATTRIBUTE_PASS_ROOT_TAG_KIND_MISMATCH);

  private static final SoyErrorKind DELEGATE_KIND_MISMATCH =
      SoyErrorKind.of(
          "Expected the called template to have root tag ''{0}'', found ''{1}''.",
          Impression.ERROR_ELEMENT_ATTRIBUTE_PASS_DELEGATE_KIND_MISMATCH);

  private final ErrorReporter errorReporter;
  private final Supplier<FileSetMetadata> templateRegistryFromDeps;

  ValidateElementAttributePass(
      ErrorReporter errorReporter, Supplier<FileSetMetadata> templateRegistryFromDeps) {
    this.errorReporter = errorReporter;
    this.templateRegistryFromDeps = templateRegistryFromDeps;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    // Collect all elements in this compilation first. This cache will be used to look up elements,
    // followed by the secondary cache libRegistry, which includes all elements from deps.
    ImmutableMap<String, TemplateNode> allAstElements =
        sourceFiles.stream()
            .flatMap(fn -> fn.getTemplates().stream())
            .filter(
                t ->
                    !(t instanceof TemplateDelegateNode)
                        && t.getTemplateContentKind() instanceof ElementContentKind
                        && t.getHtmlElementMetadata() != null)
            .collect(toImmutableMap(TemplateNode::getTemplateName, t -> t));

    for (SoyFileNode file : sourceFiles) {
      run(file, allAstElements);
    }

    checkRootElementTagNames(allAstElements.values());

    return Result.CONTINUE;
  }

  private void run(SoyFileNode file, ImmutableMap<String, TemplateNode> allAstElements) {

    ImmutableList.Builder<TemplateNode> delegatingElementsWithAllAttrs = ImmutableList.builder();

    // Rewrite all @attribute values in root elements.
    file.getTemplates().stream()
        .filter(
            t ->
                t.getTemplateContentKind() instanceof ElementContentKind
                    && t.getHtmlElementMetadata() != null)
        .forEach(
            t -> {
              if (t instanceof TemplateDelegateNode) {
                errorReporter.report(
                    t.getOpenTagLocation(), DELTEMPLATE_USING_ELEMENT_CONTENT_KIND);
                return;
              }
              processTemplate(t, delegatingElementsWithAllAttrs::add);
            });

    updateReservedAttributesForDelegateCalls(
        delegatingElementsWithAllAttrs.build(), allAstElements);
  }

  private void processTemplate(
      TemplateNode templateNode, Consumer<TemplateNode> delegatingElementsWithAllAttrs) {
    ImmutableMap<String, AttrParam> attrs =
        templateNode.getAllParams().stream()
            .filter(AttrParam.class::isInstance)
            .map(AttrParam.class::cast)
            .collect(toImmutableMap(AttrParam::getAttrName, Function.identity()));

    Set<AttrParam> unseenParams = new HashSet<>(attrs.values());
    checkAttributeRefs(templateNode, unseenParams);

    HtmlOpenTagNode openTagNode = getElementOpen(templateNode);
    if (openTagNode == null) {
      return;
    }

    ImmutableSet.Builder<String> foundNormalAttr = ImmutableSet.builder();
    SoyTreeUtils.allNodesOfType(openTagNode, HtmlAttributeNode.class)
        .filter(attr -> attr.getStaticKey() != null)
        .forEach(
            attrNode -> {
              String attrKey = attrNode.getStaticKey();
              if (attrKey.equals(AddDebugAttributesPass.DATA_DEBUG_SOY)) {
                return;
              }
              // Remove the @ at the beginning of the attribute.
              boolean isSoyAttr = attrNode.isSoyAttr();
              String attrName = isSoyAttr ? attrKey.substring(1) : attrKey;

              if (!isSoyAttr) {
                foundNormalAttr.add(attrName);

                // e.g. Not allowed to write aria-label= if @aria-label is in scope.
                if (attrs.containsKey(attrName)) {
                  errorReporter.report(
                      attrNode.getSourceLocation(), PLAIN_ATTRIBUTE, "@" + attrName);
                }
                return;
              }

              if (!attrs.containsKey(attrName)) {
                String didYouMeanMessage = SoyErrors.getDidYouMeanMessage(attrs.keySet(), attrName);
                errorReporter.report(
                    attrNode.getSourceLocation(),
                    UNRECOGNIZED_ATTRIBUTE,
                    attrName,
                    didYouMeanMessage);
                return;
              }

              AttrParam attr = attrs.get(attrName);
              unseenParams.remove(attr);

              if (attrNode.hasValue() && attr.isRequired()) {
                errorReporter.report(
                    attrNode.getSourceLocation(), ATTRIBUTE_NOT_REQUIRED, attr.getAttrName());
              }
            });

    if (templateNode.getAllowExtraAttributes()) {
      String delegateTemplateName = getDelegateCall(templateNode);
      boolean iAmAnElementCallingAnElement = !delegateTemplateName.isEmpty();
      templateNode.setReservedAttributes(foundNormalAttr.build());
      if (iAmAnElementCallingAnElement) {
        delegatingElementsWithAllAttrs.accept(templateNode);
      }
    }
    warnUnusedAttributes(unseenParams);
  }

  private void updateReservedAttributesForDelegateCalls(
      ImmutableList<TemplateNode> templates, ImmutableMap<String, TemplateNode> allAstElements) {

    Map<String, String> templateFqnCall =
        templates.stream()
            .collect(
                toMap(
                    TemplateNode::getTemplateName, ValidateElementAttributePass::getDelegateCall));

    // Simple topological sort.
    while (!templateFqnCall.isEmpty()) {
      List<Map.Entry<String, String>> leaves =
          templateFqnCall.entrySet().stream()
              .filter(e -> !templateFqnCall.containsKey(e.getValue()))
              .collect(Collectors.toList());
      if (leaves.isEmpty()) {
        throw new IllegalArgumentException("Cyclical graph: " + templateFqnCall);
      }
      for (Map.Entry<String, String> leaf : leaves) {
        TemplateNode callee = allAstElements.get(leaf.getValue());
        ImmutableSet<String> reservedAttr;
        if (callee != null) {
          reservedAttr = callee.getReservedAttributes();
        } else {
          reservedAttr =
              templateRegistryFromDeps
                  .get()
                  .getBasicTemplateOrElement(leaf.getValue())
                  .getTemplateType()
                  .getReservedAttributes();
        }
        TemplateNode caller = allAstElements.get(leaf.getKey());
        caller.setReservedAttributes(
            ImmutableSet.<String>builder()
                .addAll(caller.getReservedAttributes())
                .addAll(reservedAttr)
                .build());
        templateFqnCall.remove(leaf.getKey());
      }
    }
  }

  private void checkRootElementTagNames(ImmutableCollection<TemplateNode> elements) {
    for (TemplateNode node : elements) {
      ElementContentKind contentKind = (ElementContentKind) node.getTemplateContentKind();
      String expectedTagName = contentKind.getTagName();
      // html<?> matches anything
      if (expectedTagName.isEmpty()) {
        continue;
      }

      String tag = node.getHtmlElementMetadata().getTag();
      if ("?".equals(tag) || expectedTagName.equals(tag)) {
        continue;
      }

      HtmlOpenTagNode openTag = getElementOpen(node);
      SourceLocation errorLoc =
          node.numChildren() > 0 ? node.getChild(0).getSourceLocation() : node.getSourceLocation();
      if (openTag != null) {
        errorLoc = openTag.getTagName().getTagLocation();
      } else {
        SoyNode firstContent =
            node.getChildren().stream().filter(StandaloneNode::isRendered).findFirst().orElse(null);
        if (firstContent instanceof CallBasicNode call) {
          errorLoc = call.getOpenTagLocation();
        }
      }

      if (openTag != null && openTag.getTagName().isStatic()) {
        errorReporter.report(errorLoc, ROOT_TAG_KIND_MISMATCH, expectedTagName);
      } else {
        errorReporter.report(errorLoc, DELEGATE_KIND_MISMATCH, expectedTagName, tag);
      }
    }
  }

  /**
   * Returns the FQN template name of the template to which this element delegates, or null if this
   * template does not delegate.
   */
  private static String getDelegateCall(TemplateNode templateNode) {
    return templateNode.getHtmlElementMetadata().getDelegateElement();
  }

  private void checkAttributeRefs(TemplateNode templateNode, Set<AttrParam> attrs) {
    // No standard var refs to @attribute params are allowed.
    SoyTreeUtils.allNodesOfType(templateNode, VarRefNode.class)
        .filter(ref -> attrs.contains(ref.getDefnDecl()))
        .forEach(
            attrRef ->
                errorReporter.report(attrRef.getSourceLocation(), ATTRIBUTE_USED_OUTSIDE_OF_TAG));
  }

  private void warnUnusedAttributes(Iterable<AttrParam> unseenParams) {
    unseenParams.forEach(
        attrParam -> errorReporter.warn(attrParam.getSourceLocation(), UNUSED_ATTRIBUTE));
  }
}
