/*
 * Copyright 2021 Google Inc.
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

package com.google.template.soy.idom;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.idom.IdomMetadataP.Kind;
import com.google.template.soy.soytree.ForNonemptyNode;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import java.util.ArrayList;
import java.util.List;

/** Pass that calculates the IdomMetadata for templates. */
public final class IdomMetadataCalculator {
  public static ImmutableList<IdomMetadata> calcMetadata(SoyFileSetNode fileSet) {
    ImmutableList.Builder<IdomMetadata> templateMetadatas = ImmutableList.builder();
    for (SoyFileNode sourceFile : fileSet.getChildren()) {
      for (TemplateNode tplNode : sourceFile.getTemplates()) {
        templateMetadatas.add(new IdomMetadataCalculator().calcTemplateMetadata(tplNode));
      }
    }
    return templateMetadatas.build();
  }

  private static final ImmutableSet<SoyNode.Kind> CONDITIONAL_NODES =
      ImmutableSet.of(
          SoyNode.Kind.IF_NODE,
          SoyNode.Kind.IF_COND_NODE,
          SoyNode.Kind.IF_ELSE_NODE,
          SoyNode.Kind.SWITCH_NODE,
          SoyNode.Kind.SWITCH_CASE_NODE,
          SoyNode.Kind.SWITCH_DEFAULT_NODE);

  private final List<SoyNode> htmlStack = new ArrayList<>();

  private IdomMetadata calcTemplateMetadata(TemplateNode tplNode) {
    return IdomMetadata.newBuilder(Kind.TEMPLATE)
        .name(getTemplateName(tplNode))
        .location(tplNode.getOpenTagLocation())
        .addChildren(getSoyMetadata(tplNode.getChildren()))
        .build();
  }

  private ImmutableList<IdomMetadata> getSoyMetadata(List<? extends SoyNode> nodes) {
    ImmutableList.Builder<IdomMetadata> allMetadatas = ImmutableList.builder();
    for (int i = 0; i < nodes.size(); i++) {
      SoyNode node = nodes.get(i);
      boolean isConditionalNode = CONDITIONAL_NODES.contains(node.getKind());
      if (!isConditionalNode) {
        htmlStack.add(node);
      }
      if (node instanceof HtmlOpenTagNode) {
        HtmlOpenTagNode openTag = (HtmlOpenTagNode) node;
        ImmutableList<IdomMetadata> htmlChildrenMetadata = ImmutableList.of();
        // For HtmlOpenTagNodes that have a single end node in the same parent we treat the
        // nodes in between as children and recurse into them and skip them in this loop to be
        // able to represent this in the metadata hierarchy.
        if (openTag.getTaggedPairs().size() == 1) {
          int endNodeIndex = nodes.indexOf(openTag.getTaggedPairs().get(0));
          if (endNodeIndex >= 0) {
            htmlChildrenMetadata = getSoyMetadata(nodes.subList(i + 1, endNodeIndex));
            i = endNodeIndex;
          }
        }
        allMetadatas.addAll(getHtmlTagMetadata(openTag, htmlChildrenMetadata));
      } else if (node instanceof ParentSoyNode) {
        allMetadatas.addAll(getSoyMetadata(((ParentSoyNode<?>) node).getChildren()));
      }
      if (!isConditionalNode) {
        htmlStack.remove(htmlStack.size() - 1);
      }
    }
    return allMetadatas.build();
  }

  private ImmutableList<IdomMetadata> getHtmlTagMetadata(
      HtmlOpenTagNode openTag, ImmutableList<IdomMetadata> htmlChildrenMetadata) {
    ImmutableList<IdomMetadata> metadatas = htmlChildrenMetadata;
    if (isFocusable(openTag)) {
      metadatas =
          ImmutableList.<IdomMetadata>builder()
              .add(
                  IdomMetadata.newBuilder(Kind.FOCUSABLE_ELEMENT)
                      .location(openTag.getSourceLocation())
                      .build())
              .addAll(metadatas)
              .build();
    }
    metadatas = addForLoopRootIfNeeded(openTag, openTag.getKeyNode() != null, metadatas);
    if (openTag.isSkipRoot()) {
      metadatas =
          ImmutableList.of(
              IdomMetadata.newBuilder(Kind.SKIP)
                  .location(openTag.getSourceLocation())
                  .addChildren(metadatas)
                  .build());
    }
    return metadatas;
  }

  private ImmutableList<IdomMetadata> addForLoopRootIfNeeded(
      SoyNode node, boolean hasKey, ImmutableList<IdomMetadata> metadatas) {
    if (hasKey
        || htmlStack.size() < 2
        || !(htmlStack.get(htmlStack.size() - 2) instanceof ForNonemptyNode)) {
      return metadatas;
    }
    return ImmutableList.of(
        IdomMetadata.newBuilder(Kind.FOR_LOOP_ROOT_WITHOUT_KEY)
            .location(node.getSourceLocation())
            .addChildren(metadatas)
            .build());
  }

  private static String getTemplateName(TemplateNode tplNode) {
    if (tplNode instanceof TemplateDelegateNode) {
      return ((TemplateDelegateNode) tplNode).getDelTemplateName();
    }
    return tplNode.getTemplateName();
  }

  private static boolean isFocusable(HtmlOpenTagNode node) {
    if (node.getTagName().isFocusable()) {
      return true;
    }
    HtmlAttributeNode tabIndexAttr = node.getDirectAttributeNamed("tabindex");
    if (tabIndexAttr != null && !"-1".equals(tabIndexAttr.getStaticContent())) {
      return true;
    }
    return false;
  }
}
