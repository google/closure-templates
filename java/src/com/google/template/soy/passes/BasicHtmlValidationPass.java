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

import static com.google.template.soy.soytree.MessagePlaceholder.PHEX_ATTR;
import static com.google.template.soy.soytree.MessagePlaceholder.PHNAME_ATTR;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode;
import com.google.template.soy.soytree.HtmlCloseTagNode;
import com.google.template.soy.soytree.HtmlTagNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.SwitchNode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * A compiler pass that performs HTML validation that is always enabled, as opposed to
 * StrictHtmlValidationPass which is opt-out.
 */
final class BasicHtmlValidationPass implements CompilerFilePass {
  private static final SoyErrorKind MULTIPLE_ATTRIBUTES =
      SoyErrorKind.of("Found multiple ''{0}'' attributes with the same name.");

  private static final SoyErrorKind UNEXPECTED_CLOSE_TAG_CONTENT =
      SoyErrorKind.of("Unexpected close tag content, only whitespace is allowed in close tags.");
  private static final SoyErrorKind BAD_ID_VALUE =
      SoyErrorKind.of(
          "Html id attributes should not be valid JavaScript identifiers, consider hyphenating the"
              + " id."
          );
  private static final SoyErrorKind XID_ID_VALUE =
      SoyErrorKind.of(
          "Html id attributes should not be valid JavaScript identifiers and the xid() function can"
              + " return a JavaScript identifier. Consider adding a hyphen outside of the xid()"
              + " call."
          );

  private final ErrorReporter errorReporter;

  BasicHtmlValidationPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    SoyTreeUtils.allNodesOfType(file, HtmlTagNode.class)
        .forEach(
            node -> {
              checkForDuplicateAttributes(node);
              if (node instanceof HtmlCloseTagNode) {
                checkCloseTagChildren((HtmlCloseTagNode) node);
              }
            });
    SoyTreeUtils.allNodesOfType(file, RenderUnitNode.class)
        .filter(
            unit ->
                !unit.isImplicitContentKind()
                    && unit.getContentKind() == SanitizedContentKind.ATTRIBUTES)
        .forEach(this::checkForDuplicateAttributes);
    SoyTreeUtils.allNodesOfType(file, HtmlAttributeNode.class)
        .forEach(this::warnOnIdAttributesMatchingJsIdentifiers);
  }

  /**
   * Report an error when we find a duplicate attribute.
   * https://html.spec.whatwg.org/multipage/syntax.html#attributes-2
   */
  private void checkForDuplicateAttributes(ParentSoyNode<StandaloneNode> parentNode) {
    DuplicateAttributesVisitor visitor = new DuplicateAttributesVisitor();
    List<StandaloneNode> children = parentNode.getChildren();
    if (parentNode instanceof HtmlTagNode) {
      // the first child is the tag name, which isn't interesting, so skip it
      children = children.subList(1, children.size());
    }
    for (SoyNode child : children) {
      visitor.exec(child);
    }
  }

  // https://developer.mozilla.org/en-US/docs/Glossary/Identifier
  private static final Pattern JS_IDENTIFIER_PATTERN =
      Pattern.compile("^[$_\\p{IsLetter}][$_\\p{IsLetter}\\p{IsDigit}]*$");

  private enum IdShape {
    YES {
      @Override
      SoyErrorKind getError() {
        return BAD_ID_VALUE;
      }
    },
    XID {
      @Override
      SoyErrorKind getError() {
        return XID_ID_VALUE;
      }
    },
    NO;

    @Nullable
    SoyErrorKind getError() {
      return null;
    }
  }

  private static IdShape getIdShape(HtmlAttributeValueNode node) {
    if (node.numChildren() != 1) {
      return IdShape.NO;
    }
    StandaloneNode attrValueNode = node.getChild(0);
    if (attrValueNode instanceof RawTextNode) {
      if (JS_IDENTIFIER_PATTERN.matcher(((RawTextNode) attrValueNode).getRawText()).matches()) {
        return IdShape.YES;
      }
    } else if (attrValueNode instanceof PrintNode) {
      ExprNode exprRoot = ((PrintNode) attrValueNode).getExpr().getRoot();
      // Cannot
      if (exprRoot instanceof FunctionNode
          && ((FunctionNode) exprRoot).getFunctionName().equals("xid")) {
        return IdShape.XID;
      }
    }
    return IdShape.NO;
  }

  private void warnOnIdAttributesMatchingJsIdentifiers(HtmlAttributeNode attributeNode) {
    if (attributeNode.definitelyMatchesAttributeName("id") && attributeNode.hasValue()) {
      SoyNode child = attributeNode.getChild(1);
      if (child instanceof HtmlAttributeValueNode) {
        SoyErrorKind error = getIdShape((HtmlAttributeValueNode) child).getError();
        if (error != null) {
          errorReporter.warn(attributeNode.getChild(1).getSourceLocation(), error);
        }
      }
    }
  }

  private final class DuplicateAttributesVisitor extends AbstractSoyNodeVisitor<Set<String>> {
    private final Set<String> foundSoFar;

    DuplicateAttributesVisitor() {
      this(ImmutableSet.of());
    }

    DuplicateAttributesVisitor(Set<String> foundSoFar) {
      this.foundSoFar = new HashSet<>(foundSoFar);
    }

    @Override
    public Set<String> exec(SoyNode n) {
      visit(n);
      return foundSoFar;
    }

    @Override
    protected void visitHtmlAttributeNode(HtmlAttributeNode node) {
      // don't visit children, we only care about attribute names
      String attributeKey = node.getStaticKey();
      if (attributeKey != null) {
        // attribute keys are case insensitive under ascii rules
        attributeKey = Ascii.toLowerCase(attributeKey);
        if (!foundSoFar.add(attributeKey)) {
          errorReporter.report(node.getSourceLocation(), MULTIPLE_ATTRIBUTES, attributeKey);
        }
      }
    }

    @Override
    protected void visitIfNode(IfNode node) {
      visitControlFlowNode(node, /* exhaustive= */ node.hasElse());
    }

    @Override
    protected void visitSwitchNode(SwitchNode node) {
      visitControlFlowNode(node, /* exhaustive= */ node.hasDefaultCase());
    }

    @Override
    protected void visitForNode(ForNode node) {
      // loops are a little weird, consider reporting an error for all static attributes within the
      // loop body.
      visitControlFlowNode(node, /* exhaustive= */ false);
    }

    private void visitControlFlowNode(
        SplitLevelTopNode<? extends BlockNode> parent, boolean exhaustive) {
      if (exhaustive) {
        Set<String> definiteBlockAttrs = null;
        for (BlockNode block : parent.getChildren()) {
          Set<String> blockAttrs = new DuplicateAttributesVisitor(foundSoFar).exec(block);
          if (definiteBlockAttrs == null) {
            definiteBlockAttrs = new HashSet<>(Sets.difference(blockAttrs, foundSoFar));
          } else {
            definiteBlockAttrs.retainAll(blockAttrs); // only retain the intersection
          }
        }
        foundSoFar.addAll(definiteBlockAttrs);
      } else {
        for (BlockNode block : parent.getChildren()) {
          new DuplicateAttributesVisitor(foundSoFar).exec(block);
        }
      }
    }

    @Override
    protected void visitCallNode(CallNode node) {
      // don't visit children
    }

    @Override
    protected void visitLetContentNode(LetContentNode node) {
      // don't visit children
    }

    @Override
    protected void visitSoyNode(SoyNode node) {
      if (node instanceof ParentSoyNode<?> parent) {
        visitChildren(parent);
      }
    }
  }

  /**
   * Validates that the only children of close tags can be {@code phname} attributes.
   *
   * <p>Later passes validate that phnames for close tags only appear in messages.
   */
  private void checkCloseTagChildren(HtmlCloseTagNode closeTag) {
    HtmlAttributeNode phNameAttribute = closeTag.getDirectAttributeNamed(PHNAME_ATTR);
    HtmlAttributeNode phExAttribute = closeTag.getDirectAttributeNamed(PHEX_ATTR);
    // the child at index 0 is the tag name
    for (int i = 1; i < closeTag.numChildren(); i++) {
      StandaloneNode child = closeTag.getChild(i);
      if (child == phNameAttribute || child == phExAttribute) {
        continue; // the phname and phex attributes are validated later and allowed in close nodes
      }
      errorReporter.report(child.getSourceLocation(), UNEXPECTED_CLOSE_TAG_CONTENT);
    }
  }
}
