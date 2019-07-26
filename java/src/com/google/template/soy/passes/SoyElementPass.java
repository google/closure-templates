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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.HtmlElementMetadataP;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.HtmlTagNode;
import com.google.template.soy.soytree.KeyNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.TemplateElementNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.VeLogNode;
import javax.annotation.Nullable;

/** Validates restrictions specific to Soy elements. */
public final class SoyElementPass extends CompilerFilePass {

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
          "Soy elements must contain exactly one top-level HTML element (e.g, span, div).");

  private static final ImmutableSet<SoyNode.Kind> ALLOWED_CHILD_NODES =
      Sets.immutableEnumSet(
          SoyNode.Kind.LET_CONTENT_NODE, SoyNode.Kind.LET_VALUE_NODE, SoyNode.Kind.LOG_NODE);

  private static final String DYNAMIC_ELEMENT_TAG = "?";
  private final ErrorReporter errorReporter;

  SoyElementPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (TemplateNode template : file.getChildren()) {
      ErrorReporter bufferedErrorReporter = ErrorReporter.create(ImmutableMap.of());
      // scan through all children skipping 'ALLOWED_CHILD_KINDS' until we find an HtmlOpenTagNode
      // or a VeLogNode
      // validate the corresponding dom structure ensuring that our constrains are met:
      // * unambiguous close tag
      // * no key
      // * tag name is not dynamic
      // then mark the open tag as an element root.
      boolean foundRootTags = false;
      VeLogNode veLogNode = null;
      HtmlOpenTagNode openTag = null;
      for (int i = 0; i < template.numChildren(); i++) {
        SoyNode child = template.getChild(i);
        if (ALLOWED_CHILD_NODES.contains(child.getKind())) {
          continue;
        }
        if (!foundRootTags && child instanceof HtmlOpenTagNode) {
          HtmlTagNode closeTag =
              checkHtmlOpenTag(template, (HtmlOpenTagNode) child, bufferedErrorReporter);
          if (closeTag == null) {
            break;
          }
          // jump ahead to just after the close tag
          i = template.getChildIndex(closeTag);
          foundRootTags = true;
          openTag = ((HtmlOpenTagNode) child);
        } else if (!foundRootTags && child instanceof VeLogNode) {
          veLogNode = (VeLogNode) child;
          HtmlOpenTagNode maybeOpenTagNode = veLogNode.getOpenTagNode();
          if (maybeOpenTagNode == null) {
            // ve log validation should have failed already
            checkState(errorReporter.hasErrors());
          } else {
            HtmlTagNode closeTag =
                checkHtmlOpenTag(veLogNode, maybeOpenTagNode, bufferedErrorReporter);
            if (closeTag == null) {
              break; // skip reporting additional errors
            }
            openTag = maybeOpenTagNode;
            foundRootTags = true;
          }
        } else {
          bufferedErrorReporter.report(child.getSourceLocation(), SOY_ELEMENT_EXACTLY_ONE_TAG);
          break; // break after first error
        }
      }
      if (template instanceof TemplateElementNode) {
        bufferedErrorReporter.copyTo(errorReporter);
        if (openTag != null) {
          openTag.setElementRoot();
        }
      }
      // openTag being null means that the template isn't kind HTML.
      boolean isValid =
          openTag != null && template.numChildren() > 0 && !bufferedErrorReporter.hasErrors();
      HtmlElementMetadataP.Builder builder = HtmlElementMetadataP.newBuilder();
      if (isValid) {
        builder.setTag(
            openTag.getTagName().isStatic()
                ? openTag.getTagName().getStaticTagName()
                : DYNAMIC_ELEMENT_TAG);
      }
      template.setHtmlElementMetadata(
          builder.setIsHtmlElement(isValid).setIsVelogged(veLogNode != null).build());
    }
  }

  @Nullable
  private static HtmlTagNode checkHtmlOpenTag(
      BlockNode parent, HtmlOpenTagNode openTagNode, ErrorReporter errorReporter) {
    validateNoKey(openTagNode, errorReporter);
    validateNoDynamicTag(openTagNode, errorReporter);
    if (openTagNode.isSelfClosing()
        || (openTagNode.getTagName().isDefinitelyVoid()
            && openTagNode.getTaggedPairs().isEmpty())) {
      // simple void element, like <input> or <input />
      return openTagNode;
    } else {
      // this is a 'normal' tag, so it should have a close tag
      if (openTagNode.getTaggedPairs().isEmpty()) {
        return null;
      }
      if (openTagNode.getTaggedPairs().size() == 1) {
        HtmlTagNode closeTag = openTagNode.getTaggedPairs().get(0);
        if (closeTag.getParent() != parent) {
          errorReporter.report(
              openTagNode.getSourceLocation(), SOY_ELEMENT_OPEN_TAG_CLOSE_AMBIGUOUS);
          return null;
        }
        return closeTag;
      } else {
        errorReporter.report(openTagNode.getSourceLocation(), SOY_ELEMENT_OPEN_TAG_CLOSE_AMBIGUOUS);
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
