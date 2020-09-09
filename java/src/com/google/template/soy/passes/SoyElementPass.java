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

package com.google.template.soy.passes;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.base.internal.TemplateContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.HtmlElementMetadataP;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.HtmlTagNode;
import com.google.template.soy.soytree.ImportsContext.ImportsTemplateRegistry;
import com.google.template.soy.soytree.KeyNode;
import com.google.template.soy.soytree.SkipNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateElementNode;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.VeLogNode;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/** Validates restrictions specific to Soy elements. */
@RunAfter(StrictHtmlValidationPass.class)
public final class SoyElementPass implements CompilerFileSetPass {

  private static final SoyErrorKind SOYELEMENT_CANNOT_BE_SKIPPED =
      SoyErrorKind.of("Soy elements cannot be skipped.");

  private static final SoyErrorKind SOYELEMENT_CANNOT_WRAP_ITSELF_RECURSIVELY =
      SoyErrorKind.of(
          "The root node of Soy elements must not recursively call itself. The cycle is ''{0}''.");

  private static final SoyErrorKind SOYELEMENT_CANNOT_WRAP_SOY_ELEMENT =
      SoyErrorKind.of("The root node of Soy elements must not be another Soy element.");

  private static final SoyErrorKind ROOT_HAS_KEY_NODE =
      SoyErrorKind.of(
          "The root node of Soy elements must not have a key. "
              + "Instead, consider wrapping the Soy element in a keyed tag node.");

  private static final SoyErrorKind ROOT_IS_DYNAMIC_TAG =
      SoyErrorKind.of("The root node of Soy elements must not be a dynamic HTML tag.");

  private static final SoyErrorKind SOY_ELEMENT_OPEN_TAG_CLOSE_AMBIGUOUS =
      SoyErrorKind.of("Soy element open tags must map to exactly one close tag.");

  private static final SoyErrorKind SOY_ELEMENT_EXACTLY_ONE_TAG =
      SoyErrorKind.of(
          "Soy elements must contain exactly one top-level HTML element (e.g, span, div). Calls to"
              + " templates (but not deltemplates) that contain one top-level HTML element are"
              + " also allowed.");

  private static final SoyErrorKind ELEMENT_TEMPLATE_EXACTLY_ONE_TAG =
      SoyErrorKind.of(
          "Templates with kind=\"html<?>\" must contain exactly one top-level HTML element (e.g,"
              + " span, div).");

  static final ImmutableSet<SoyNode.Kind> ALLOWED_CHILD_NODES =
      Sets.immutableEnumSet(
          SoyNode.Kind.LET_CONTENT_NODE,
          SoyNode.Kind.LET_VALUE_NODE,
          SoyNode.Kind.DEBUGGER_NODE,
          SoyNode.Kind.LOG_NODE);

  private static final HtmlElementMetadataP DEFAULT_HTML_METADATA =
      HtmlElementMetadataP.newBuilder().setIsHtmlElement(false).setIsVelogged(false).build();

  private static final String DYNAMIC_ELEMENT_TAG = "?";
  private final ErrorReporter errorReporter;

  SoyElementPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    Map<String, TemplateNode> templatesInLibrary = new LinkedHashMap<>();
    for (SoyFileNode file : sourceFiles) {
      // Create an intermediatary data structure for template name -> template node so that
      // we can use it like a TemplateRegistry, but for templates in the immediate compilation unit.
      for (TemplateNode template : file.getTemplates()) {
        if (!(template instanceof TemplateDelegateNode)
            && template.getContentKind() == SanitizedContentKind.HTML) {
          templatesInLibrary.put(template.getTemplateName(), template);
        } else {
          template.setHtmlElementMetadata(DEFAULT_HTML_METADATA);
        }
      }
    }
    for (TemplateNode template : templatesInLibrary.values()) {
      Set<TemplateNode> visited = new HashSet<>();
      getTemplateMetadata(
          template, templatesInLibrary, template.getParent().getTemplateRegistry(), visited);
    }
    return Result.CONTINUE;
  }

  /**
   * Because templates can reference each other recursively, we need to treat this as a graph
   * traversal.
   */
  private HtmlElementMetadataP getTemplateMetadata(
      TemplateNode template,
      Map<String, TemplateNode> templatesInLibrary,
      ImportsTemplateRegistry registry,
      Set<TemplateNode> visited) {
    if (visited.contains(template)) {
      if (template instanceof TemplateElementNode) {
        errorReporter.report(
            template.getSourceLocation(),
            SOYELEMENT_CANNOT_WRAP_ITSELF_RECURSIVELY,
            visited.stream().map(TemplateNode::getTemplateName).sorted().collect(toImmutableSet()));
      }
      template.setHtmlElementMetadata(DEFAULT_HTML_METADATA);
      return null;
    }
    visited.add(template);
    boolean isSoyElement = template instanceof TemplateElementNode;
    // scan through all children skipping 'ALLOWED_CHILD_KINDS' until we find an HtmlOpenTagNode
    // or a VeLogNode
    // validate the corresponding dom structure ensuring that our constrains are met:
    // * unambiguous close tag
    // * no key
    // * tag name is not dynamic
    // then mark the open tag as an element root.
    VeLogNode veLogNode = null;
    HtmlOpenTagNode openTag = null;
    HtmlTagNode closeTag = null;
    for (int i = 0; i < template.numChildren(); i++) {
      SoyNode child = template.getChild(i);
      if (ALLOWED_CHILD_NODES.contains(child.getKind())) {
        continue;
      }

      // If the template is a static call, then it may be a Soy element if it's the last child.
      // TODO(tomnguyen): Merge this logic with velog validation pass.
      // TODO(cwgordon): There is no way to make guarantees about the root element of a dynamic
      // call. Consider adding some way to indicate this constraint in template type declarations.
      if (openTag == null
          && child instanceof CallBasicNode
          && ((CallBasicNode) child).isStaticCall()
          && i == template.numChildren() - 1) {
        if (template.getTemplateContentKind() instanceof TemplateContentKind.ElementContentKind) {
          errorReporter.report(child.getSourceLocation(), ELEMENT_TEMPLATE_EXACTLY_ONE_TAG);
        }
        return getTemplateMetadataForStaticCall(
            template, (CallBasicNode) child, templatesInLibrary, registry, visited);
      } else if (openTag == null && child instanceof HtmlOpenTagNode) {
        closeTag = checkHtmlOpenTag(template, (HtmlOpenTagNode) child, errorReporter, isSoyElement);
        if (closeTag == null) {
          break;
        }
        // jump ahead to just after the close tag
        i = template.getChildIndex(closeTag);
        openTag = ((HtmlOpenTagNode) child);
      } else if (openTag == null && child instanceof VeLogNode) {
        veLogNode = (VeLogNode) child;
        HtmlOpenTagNode maybeOpenTagNode = veLogNode.getOpenTagNode();
        if (maybeOpenTagNode != null) {
          closeTag = checkHtmlOpenTag(veLogNode, maybeOpenTagNode, errorReporter, isSoyElement);
          if (closeTag == null) {
            break; // skip reporting additional errors
          }
          openTag = maybeOpenTagNode;
        }
      } else {
        openTag = null;
        closeTag = null;
        if (isSoyElement) {
          errorReporter.report(child.getSourceLocation(), SOY_ELEMENT_EXACTLY_ONE_TAG);
        }
        if (template.getTemplateContentKind() instanceof TemplateContentKind.ElementContentKind) {
          errorReporter.report(child.getSourceLocation(), ELEMENT_TEMPLATE_EXACTLY_ONE_TAG);
        }
        break; // break after first error
      }
    }
    if (openTag != null) {
      openTag.setElementRoot();
    }
    // openTag being null means that the template isn't kind HTML.
    boolean isValid = openTag != null && closeTag != null;
    HtmlElementMetadataP.Builder builder = HtmlElementMetadataP.newBuilder();
    boolean hasSkipNode = false;
    if (isValid) {
      for (StandaloneNode child : openTag.getChildren()) {
        if (child instanceof SkipNode) {
          hasSkipNode = true;
        }
      }
      builder.setTag(
          openTag.getTagName().isStatic()
              ? openTag.getTagName().getStaticTagName()
              : DYNAMIC_ELEMENT_TAG);
      if (hasSkipNode && template instanceof TemplateElementNode) {
        errorReporter.report(openTag.getSourceLocation(), SOYELEMENT_CANNOT_BE_SKIPPED);
      }
    }
    HtmlElementMetadataP info =
        builder
            .setIsHtmlElement(isValid)
            .setIsVelogged(veLogNode != null)
            .setIsSkip(hasSkipNode)
            .build();
    template.setHtmlElementMetadata(info);
    return info;
  }

  /**
   * The templates processed here have exactly one (static) call, which may or may not be an HTML
   * template.
   */
  private HtmlElementMetadataP getTemplateMetadataForStaticCall(
      TemplateNode template,
      CallBasicNode call,
      Map<String, TemplateNode> templatesInLibrary,
      ImportsTemplateRegistry registry,
      Set<TemplateNode> visited) {

    String callee = call.getCalleeName();
    HtmlElementMetadataP calleeMetadata = null;
    boolean isCalleeSoyElement = false;
    TemplateMetadata templateMetadata = registry.getBasicTemplateOrElement(callee);

    if (templateMetadata != null) {
      calleeMetadata = templateMetadata.getHtmlElement();
      isCalleeSoyElement = templateMetadata.getSoyElement().getIsSoyElement();
    } else if (templatesInLibrary.containsKey(callee)) {
      TemplateNode calledTemplate = templatesInLibrary.get(callee);
      calleeMetadata = calledTemplate.getHtmlElementMetadata();
      if (calleeMetadata == null) {
        calleeMetadata = getTemplateMetadata(calledTemplate, templatesInLibrary, registry, visited);
        // Cycle was detected
        if (calleeMetadata == null) {
          template.setHtmlElementMetadata(DEFAULT_HTML_METADATA);
          return null;
        }
      }
      isCalleeSoyElement = calledTemplate instanceof TemplateElementNode;
    } else {
      // These are text/css/uri/etc/deltemplate nodes
      isCalleeSoyElement = false;
      calleeMetadata = DEFAULT_HTML_METADATA;
    }
    template.setHtmlElementMetadata(calleeMetadata);
    if (template instanceof TemplateElementNode) {
      if (calleeMetadata.getIsSkip()) {
        errorReporter.report(call.getSourceLocation(), SOYELEMENT_CANNOT_BE_SKIPPED);
      }
      if (isCalleeSoyElement) {
        errorReporter.report(call.getSourceLocation(), SOYELEMENT_CANNOT_WRAP_SOY_ELEMENT);
      }
      if (!calleeMetadata.getIsHtmlElement()) {
        errorReporter.report(call.getSourceLocation(), SOY_ELEMENT_EXACTLY_ONE_TAG);
      }
    }
    return calleeMetadata;
  }

  @Nullable
  static HtmlTagNode checkHtmlOpenTag(
      BlockNode parent,
      HtmlOpenTagNode openTagNode,
      ErrorReporter errorReporter,
      boolean isSoyElement) {
    if (isSoyElement) {
      validateNoKey(openTagNode, errorReporter);
      validateNoDynamicTag(openTagNode, errorReporter);
    }
    if (openTagNode.isSelfClosing()
        || (openTagNode.getTagName().isDefinitelyVoid()
            && openTagNode.getTaggedPairs().isEmpty())) {
      // simple void element, like <input> or <input />
      return openTagNode;
      // This is a graceful fallback in case there is an error in HTML validation.
    } else if (openTagNode.getTaggedPairs().isEmpty()) {
      return openTagNode;
    } else {
      // this is a 'normal' tag, so it should have a close tag
      if (openTagNode.getTaggedPairs().size() == 1) {
        HtmlTagNode closeTag = openTagNode.getTaggedPairs().get(0);
        if (closeTag.getParent() != parent) {
          if (isSoyElement) {
            errorReporter.report(
                openTagNode.getSourceLocation(), SOY_ELEMENT_OPEN_TAG_CLOSE_AMBIGUOUS);
          }
          return null;
        }
        return closeTag;
      } else {
        if (isSoyElement) {
          errorReporter.report(
              openTagNode.getSourceLocation(), SOY_ELEMENT_OPEN_TAG_CLOSE_AMBIGUOUS);
        }
        return null;
      }
    }
  }

  // See go/soy-element-keyed-roots for reasoning on why this is disallowed.
  private static void validateNoKey(HtmlOpenTagNode firstTagNode, ErrorReporter errorReporter) {
    for (SoyNode child : firstTagNode.getChildren()) {
      if (child instanceof KeyNode) {
        errorReporter.report(firstTagNode.getSourceLocation(), ROOT_HAS_KEY_NODE);
      }
    }
  }

  private static void validateNoDynamicTag(
      HtmlOpenTagNode firstTagNode, ErrorReporter errorReporter) {
    if (!firstTagNode.getTagName().isStatic()) {
      errorReporter.report(firstTagNode.getSourceLocation(), ROOT_IS_DYNAMIC_TAG);
    }
  }
}
