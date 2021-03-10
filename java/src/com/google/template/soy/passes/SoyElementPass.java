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

import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.TemplateContentKind;
import com.google.template.soy.base.internal.TemplateContentKind.ElementContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.MethodCallNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.HtmlElementMetadataP;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.HtmlTagNode;
import com.google.template.soy.soytree.KeyNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SkipNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.TagName;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateElementNode;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.VeLogNode;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/** Validates restrictions specific to Soy elements. */
@RunAfter(StrictHtmlValidationPass.class)
public final class SoyElementPass implements CompilerFileSetPass {

  private static final SoyErrorKind SOYELEMENT_CANNOT_BE_SKIPPED =
      SoyErrorKind.of("Soy elements cannot be skipped.");

  private static final SoyErrorKind SOY_ELEMENT_MUST_HAVE_STATIC_TAG =
      SoyErrorKind.of("Soy elements must have static tags.");

  private static final SoyErrorKind SOYELEMENT_CANNOT_WRAP_ITSELF_RECURSIVELY =
      SoyErrorKind.of(
          "The root node of Soy elements must not recursively call itself. The cycle is ''{0}''.");

  private static final SoyErrorKind SOYELEMENT_CANNOT_WRAP_SOY_ELEMENT =
      SoyErrorKind.of("The root node of Soy elements must not be another Soy element.");

  private static final SoyErrorKind ROOT_HAS_KEY_NODE =
      SoyErrorKind.of(
          "The root node of Soy elements must not have a key. "
              + "Instead, consider wrapping the Soy element in a keyed tag node.");

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
  private final Supplier<TemplateRegistry> templateRegistryFromDeps;

  SoyElementPass(ErrorReporter errorReporter, Supplier<TemplateRegistry> templateRegistryFromDeps) {
    this.errorReporter = errorReporter;
    this.templateRegistryFromDeps = templateRegistryFromDeps;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    Map<String, TemplateNode> templatesInLibrary = new LinkedHashMap<>();
    Set<TemplateNode> delegateTemplates = new HashSet<>();
    for (SoyFileNode file : sourceFiles) {
      // Create an intermediatary data structure for template name -> template node so that
      // we can use it like a TemplateRegistry, but for templates in the immediate compilation unit.
      for (TemplateNode template : file.getTemplates()) {
        if (!template.getContentKind().isHtml()) {
          template.setHtmlElementMetadata(DEFAULT_HTML_METADATA);
        } else if (template instanceof TemplateDelegateNode) {
          delegateTemplates.add(template);
        } else {
          templatesInLibrary.put(template.getTemplateName(), template);
        }
      }
    }
    for (TemplateNode template : templatesInLibrary.values()) {
      Set<TemplateNode> visited = new HashSet<>();
      getTemplateMetadata(template, templatesInLibrary, visited);
    }
    for (TemplateNode template : delegateTemplates) {
      Set<TemplateNode> visited = new HashSet<>();
      getTemplateMetadata(template, templatesInLibrary, visited);
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
        if (template.getTemplateContentKind() instanceof ElementContentKind) {
          errorReporter.report(child.getSourceLocation(), ELEMENT_TEMPLATE_EXACTLY_ONE_TAG);
        }
        if (isSoyElement && ((CallBasicNode) child).getKeyExpr() != null) {
          this.errorReporter.report(
              ((CallBasicNode) child).getSourceCalleeLocation(), ROOT_HAS_KEY_NODE);
        }
        return getTemplateMetadataForStaticCall(
            template,
            ((CallBasicNode) child).getCalleeName(),
            child.getSourceLocation(),
            templatesInLibrary,
            visited);
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
        } else {
          List<CallBasicNode> callNodes =
              veLogNode.getChildren().stream()
                  .filter(p -> p instanceof CallBasicNode)
                  .map(CallBasicNode.class::cast)
                  .collect(toImmutableList());
          if (callNodes.size() == 1 && callNodes.get(0).isStaticCall()) {
            if (isSoyElement && callNodes.get(0).getKeyExpr() != null) {
              this.errorReporter.report(callNodes.get(0).getSourceLocation(), ROOT_HAS_KEY_NODE);
            }
            return getTemplateMetadataForStaticCall(
                template,
                callNodes.get(0).getCalleeName(),
                callNodes.get(0).getSourceLocation(),
                templatesInLibrary,
                visited);
          } else if (isSoyElement) {
            this.errorReporter.report(veLogNode.getSourceLocation(), SOY_ELEMENT_EXACTLY_ONE_TAG);
          }
        }
      } else {
        openTag = null;
        closeTag = null;
        if (isSoyElement) {
          errorReporter.report(child.getSourceLocation(), SOY_ELEMENT_EXACTLY_ONE_TAG);
        }
        if (template.getTemplateContentKind() instanceof ElementContentKind) {
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
    boolean hasSkipNode = false;
    String delegateTemplate = null;
    String tagName = "";
    if (isValid) {
      for (StandaloneNode child : openTag.getChildren()) {
        if (child instanceof SkipNode) {
          hasSkipNode = true;
        }
      }
      delegateTemplate = getStaticDelegateCall(openTag);
      tagName =
          openTag.getTagName().isStatic()
              ? openTag.getTagName().getStaticTagName()
              : tryGetDelegateTagName(delegateTemplate, templatesInLibrary);
      if (hasSkipNode && template instanceof TemplateElementNode) {
        errorReporter.report(openTag.getSourceLocation(), SOYELEMENT_CANNOT_BE_SKIPPED);
      }
    } else if (isSoyElement) {
      this.errorReporter.report(template.getSourceLocation(), ELEMENT_TEMPLATE_EXACTLY_ONE_TAG);
    }
    String finalCallee = "";
    if (delegateTemplate != null) {
      HtmlElementMetadataP htmlMetadata =
          getTemplateMetadataForStaticCall(
              template, delegateTemplate, openTag.getSourceLocation(), templatesInLibrary, visited);
      if (htmlMetadata.getFinalCallee().isEmpty()) {
        finalCallee = delegateTemplate;
      } else {
        finalCallee = htmlMetadata.getFinalCallee();
      }
    }
    HtmlElementMetadataP info =
        HtmlElementMetadataP.newBuilder()
            .setIsHtmlElement(isValid)
            .setTag(tagName)
            .setIsVelogged(veLogNode != null)
            .setIsSkip(hasSkipNode)
            .setDelegateElement(nullToEmpty(delegateTemplate))
            .setFinalCallee(finalCallee)
            .build();
    template.setHtmlElementMetadata(info);
    return info;
  }

  private String tryGetDelegateTagName(String delegateName, Map<String, TemplateNode> templates) {
    if (delegateName == null) {
      return DYNAMIC_ELEMENT_TAG;
    }

    TemplateContentKind calleeKind;
    TemplateNode callee = templates.get(delegateName);
    if (callee != null) {
      calleeKind = callee.getTemplateContentKind();
    } else {
      TemplateMetadata metadata =
          templateRegistryFromDeps.get().getBasicTemplateOrElement(delegateName);
      Preconditions.checkNotNull(metadata, "No metadata for %s", delegateName);
      calleeKind = metadata.getTemplateType().getContentKind();
    }

    if (calleeKind instanceof ElementContentKind) {
      return ((ElementContentKind) calleeKind).getTagName();
    }

    return DYNAMIC_ELEMENT_TAG;
  }

  /**
   * Returns the FQN template name of the template to which this element delegates, or null if this
   * template does not delegate.
   */
  private static String getStaticDelegateCall(HtmlOpenTagNode openTag) {
    // The normal TagName.isTemplateCall() doesn't work before ResolveExpressionTypesPass.
    TagName tagName = openTag.getTagName();
    if (tagName.isStatic()) {
      return null;
    }
    PrintNode printNode = tagName.getDynamicTagName();
    ExprNode exprNode = printNode.getExpr().getRoot();
    if (exprNode instanceof TemplateLiteralNode) {
      return ((TemplateLiteralNode) exprNode).getResolvedName();
    }
    if (!(exprNode.getKind() == ExprNode.Kind.METHOD_CALL_NODE
        && ((MethodCallNode) exprNode).getMethodName().identifier().equals("bind"))) {
      return null;
    }

    MethodCallNode bind = (MethodCallNode) exprNode;
    if (bind.getChild(0).getKind() != ExprNode.Kind.TEMPLATE_LITERAL_NODE) {
      return null;
    }

    return ((TemplateLiteralNode) bind.getChild(0)).getResolvedName();
  }

  /**
   * The templates processed here have exactly one (static) call, which may or may not be an HTML
   * template.
   */
  private HtmlElementMetadataP getTemplateMetadataForStaticCall(
      TemplateNode template,
      String callee,
      SourceLocation calleeSourceLocation,
      Map<String, TemplateNode> templatesInLibrary,
      Set<TemplateNode> visited) {

    HtmlElementMetadataP calleeMetadata = null;
    boolean isCalleeSoyElement = false;
    TemplateMetadata templateMetadata =
        templateRegistryFromDeps.get().getBasicTemplateOrElement(callee);

    if (templateMetadata != null) {
      calleeMetadata = templateMetadata.getHtmlElement();
      isCalleeSoyElement = templateMetadata.getSoyElement().getIsSoyElement();
    } else if (templatesInLibrary.containsKey(callee)) {
      TemplateNode calledTemplate = templatesInLibrary.get(callee);
      calleeMetadata = calledTemplate.getHtmlElementMetadata();
      if (calleeMetadata == null) {
        calleeMetadata = getTemplateMetadata(calledTemplate, templatesInLibrary, visited);
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
    String finalCallee;
    if (calleeMetadata.getFinalCallee().isEmpty()) {
      finalCallee = callee;
    } else {
      finalCallee = calleeMetadata.getFinalCallee();
    }
    calleeMetadata =
        calleeMetadata.toBuilder()
            .clearDelegateElement()
            .setDelegateCallee(callee)
            .setFinalCallee(finalCallee)
            .build();
    template.setHtmlElementMetadata(calleeMetadata);
    if (template instanceof TemplateElementNode) {
      if (calleeMetadata.getIsSkip()) {
        errorReporter.report(calleeSourceLocation, SOYELEMENT_CANNOT_BE_SKIPPED);
      }
      if (isCalleeSoyElement) {
        errorReporter.report(calleeSourceLocation, SOYELEMENT_CANNOT_WRAP_SOY_ELEMENT);
      }
      if (!calleeMetadata.getIsHtmlElement()) {
        errorReporter.report(calleeSourceLocation, SOY_ELEMENT_EXACTLY_ONE_TAG);
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
      validateOpenTagProperties(openTagNode, errorReporter);
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
  private static void validateOpenTagProperties(
      HtmlOpenTagNode firstTagNode, ErrorReporter errorReporter) {
    if (firstTagNode.getTagName().isLegacyDynamicTagName()) {
      errorReporter.report(firstTagNode.getSourceLocation(), SOY_ELEMENT_MUST_HAVE_STATIC_TAG);
    }
    for (SoyNode child : firstTagNode.getChildren()) {
      if (child instanceof KeyNode) {
        errorReporter.report(firstTagNode.getSourceLocation(), ROOT_HAS_KEY_NODE);
      }
    }
  }
}
