/*
 * Copyright 2026 Google Inc.
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

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.shared.internal.Sanitizers;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.HtmlTagNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.types.SoyProtoEnumType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypes;
import java.util.HashSet;
import java.util.Set;

/** Adds a data-proto-source-loc attribute to HTML open tags that contain proto accesses. */
@RunAfter(ResolveExpressionTypesPass.class)
@RunBefore(DesugarHtmlNodesPass.class)
final class AddProtoSourceLocationsPass implements CompilerFilePass {

  public static final String DATA_PROTO_SOURCE_LOC = "data-proto-source-loc";

  public static final String FILE_PREFIX = "";

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    new Visitor(nodeIdGen).exec(file);
  }

  private static final class Visitor extends AbstractSoyNodeVisitor<Void> {
    private final IdGenerator nodeIdGen;

    Visitor(IdGenerator nodeIdGen) {
      this.nodeIdGen = nodeIdGen;
    }

    @Override
    protected void visitHtmlOpenTagNode(HtmlOpenTagNode node) {
      // We always want to visit our children.
      visitChildren(node);

      if (!node.getTagName().isStatic()
          || node.getTagName().getStaticTagName().equals("parameter")
          || node.getDirectAttributeNamed(DATA_PROTO_SOURCE_LOC) != null) {
        return;
      }

      Set<String> protoLocations = new HashSet<>();

      // Check for proto accesses within the open tag itself (including attributes).
      collectProtoLocations(node, protoLocations);

      // Check proto accesses in the content between the open and close tags, if they exist.
      if (!node.getTaggedPairs().isEmpty()) {
        HtmlTagNode closeTag = node.getTaggedPairs().get(0);
        ParentSoyNode<StandaloneNode> parent = node.getParent();
        int startIndex = parent.getChildIndex(node);
        int endIndex = parent.getChildIndex(closeTag);

        for (int i = startIndex + 1; i < endIndex; i++) {
          StandaloneNode child = parent.getChild(i);
          collectProtoLocations(child, protoLocations);
        }
      }

      // Add the attribute if any proto locations were found.
      if (!protoLocations.isEmpty()) {
        SourceLocation currentLoc = node.getSourceLocation();
        HtmlAttributeNode protoLocAttr =
            new HtmlAttributeNode(nodeIdGen.genId(), currentLoc, currentLoc.getBeginPoint());
        protoLocAttr.addChild(
            new RawTextNode(nodeIdGen.genId(), DATA_PROTO_SOURCE_LOC, currentLoc));
        HtmlAttributeValueNode protoLocAttrValue =
            new HtmlAttributeValueNode(
                nodeIdGen.genId(), currentLoc, HtmlAttributeValueNode.Quotes.DOUBLE);
        protoLocAttr.addChild(protoLocAttrValue);
        String joinedLocs =
            String.join(
                ",",
                protoLocations.stream()
                    .map(loc -> FILE_PREFIX + loc)
                    .sorted()
                    .toArray(String[]::new));
        protoLocAttrValue.addChild(
            new RawTextNode(
                nodeIdGen.genId(), Sanitizers.escapeHtmlAttribute(joinedLocs), currentLoc));
        node.addChild(protoLocAttr);
      }
    }

    @Override
    protected void visitSoyNode(SoyNode node) {
      if (node instanceof ParentSoyNode<?> parentSoyNode) {
        visitChildren(parentSoyNode);
      }
    }

    /** Collects proto file names from all {@link ExprNode} instances within the given node. */
    private static void collectProtoLocations(SoyNode node, Set<String> protoLocations) {
      SoyTreeUtils.allNodesOfType(node, ExprNode.class)
          .forEach(
              expr -> {
                SoyType type = expr.getType();
                if (type != null) {
                  SoyTypes.flattenUnion(type)
                      .filter(
                          t ->
                              t.getKind() == SoyType.Kind.PROTO
                                  || t.getKind() == SoyType.Kind.PROTO_ENUM)
                      .forEach(
                          t -> {
                            if (t.getKind() == SoyType.Kind.PROTO) {
                              protoLocations.add(
                                  ((SoyProtoType) t).getDescriptor().getFile().getName());
                            } else if (t.getKind() == SoyType.Kind.PROTO_ENUM) {
                              protoLocations.add(
                                  ((SoyProtoEnumType) t).getDescriptor().getFile().getName());
                            }
                          });
                }
              });
    }
  }
}
