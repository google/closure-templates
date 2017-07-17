/*
 * Copyright 2017 Google Inc.
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

package com.google.template.soy.incrementaldomsrc;

import static com.google.common.base.Preconditions.checkState;

import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CssNode;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode;
import com.google.template.soy.soytree.HtmlCloseTagNode;
import com.google.template.soy.soytree.HtmlCommentNode;
import com.google.template.soy.soytree.HtmlContext;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LogNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.XidNode;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;

/**
 * A visitor that populates the {@link HtmlContext} fields of {@link MsgFallbackGroupNode}, {@link
 * RawTextNode} and {@link PrintNode} and also enforces some additional restrictions for incremental
 * dom.
 *
 * <p>The additional restrictions are:
 *
 * <ul>
 *   <li>Tags must have literal names
 *   <li>Attributes with values must have literal names
 *   <li>{@code xid} and {@code css} commands can only appear in attribute value context.
 *   <li>Enforces the correct usage of self closing tags.
 * </ul>
 *
 * <p>TODO(lukes): A lot of this logic should really be handled by the autoescaper, but
 * incrementaldom doesn't currently run the autoescaper (which is weird, it probably should at least
 * to get attribute escaping correct).
 */
final class HtmlContextVisitor extends AbstractSoyNodeVisitor<Void> {

  private static final SoyErrorKind INVALID_NODE_LOCATION_OUTSIDE_OF_ATTRIBUTE_VALUE =
      SoyErrorKind.of(
          "The incremental HTML Soy backend does not allow '{'{0}'}' nodes to appear in HTML "
              + "outside of attribute values.");

  // These restrictions seem arbitrary.  Remove them
  private static final SoyErrorKind DYNAMIC_TAG_NAME =
      SoyErrorKind.of("IncrementalDom does not support dynamic tag names.");

  private static final SoyErrorKind DYNAMIC_ATTRIBUTE_NAME =
      SoyErrorKind.of("IncrementalDom does not support dynamic attribute names.");

  // This prevents the use of patterns like <div foo={if $foo}bar{else}baz{/if}>
  // which seems like it could be supported but isn't.
  private static final SoyErrorKind SOY_TAG_BEFORE_ATTR_VALUE =
      SoyErrorKind.of(
          "Soy statements are not "
              + "allowed before an attribute value. They should be moved inside a quotation mark.");

  private static final SoyErrorKind INVALID_SELF_CLOSING_TAG =
      SoyErrorKind.of(
          "Invalid self-closing tag for \"{0}\". Self-closing tags are only valid for void tags and"
              + " SVG content (partially supported). For a list of void elements, see "
              + "https://www.w3.org/TR/html5/syntax.html#void-elements.");

  private static final SoyErrorKind UNSUPPORTED_HTML_COMMENTS_FOR_IDOM =
      SoyErrorKind.of(
          "Found HTML comments \"{0}\". HTML comments in Soy templates are incompatible "
              + "with Incremental DOM backend.");

  /**
   * The namespaces that Elements can be in, excluding MathML. Used by {@link HtmlTransformVisitor}
   * to keep track of the current element namespace. This allows it to handle self-closing tags
   * somewhat correctly.
   */
  private enum InferredElementNamespace {
    SVG,
    XHTML
  }

  private final ErrorReporter errorReporter;

  private final ArrayDeque<HtmlContext> stateStack = new ArrayDeque<>();

  // TODO(lukes): These two fields are used to record the current inferred namespace for a node.
  // this replicates logic by the old HtmlTransformVisitor but the logic is fundamentally broken
  // since it fails to account for control flow.  In the future incrementaldom templates should be
  // forced to rely on the stricthtml pass (and require stricthtml="true"), or otherwise simply
  // trust what the user wrote for self closing tags.
  private final ArrayDeque<HtmlOpenTagNode> openTagStack = new ArrayDeque<>();
  private final IdentityHashMap<HtmlOpenTagNode, InferredElementNamespace>
      openTagToInferredNamesapce = new IdentityHashMap<>();

  public HtmlContextVisitor(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  protected void visitTemplateNode(TemplateNode node) {
    checkState(stateStack.isEmpty());
    SanitizedContentKind contentKind = node.getContentKind();
    pushState(contextForKind(contentKind));
    visitChildren(node);
    popState();
    checkState(stateStack.isEmpty());
  }

  @Override
  protected void visitCallParamContentNode(CallParamContentNode node) {
    pushState(contextForKind(node.getContentKind()));
    visitChildren(node);
    popState();
  }

  @Override
  protected void visitLetContentNode(LetContentNode node) {
    pushState(contextForKind(node.getContentKind()));
    visitChildren(node);
    popState();
  }

  @Override
  protected void visitHtmlCloseTagNode(HtmlCloseTagNode node) {
    if (!node.getTagName().isStatic()) {
      errorReporter.report(node.getTagName().getTagLocation(), DYNAMIC_TAG_NAME);
      return;
    }
    pushState(HtmlContext.HTML_TAG_NAME);
    visitChildren(node);
    popState();

    boolean tagMatches = false;
    // When encountering a closing tag, need to pop off any unclosed tags.
    while (!openTagStack.isEmpty() && !tagMatches) {
      HtmlOpenTagNode htmlOpenTagNode = openTagStack.pop();
      tagMatches =
          htmlOpenTagNode
              .getTagName()
              .getStaticTagNameAsLowerCase()
              .equals(node.getTagName().getStaticTagNameAsLowerCase());
    }
  }

  @Override
  protected void visitHtmlOpenTagNode(HtmlOpenTagNode node) {
    if (!node.getTagName().isStatic()) {
      errorReporter.report(node.getTagName().getTagLocation(), DYNAMIC_TAG_NAME);
      return;
    }
    String tagName = node.getTagName().getStaticTagNameAsLowerCase();
    InferredElementNamespace namespace = getNamespace(tagName);
    if (node.isSelfClosing()
        && namespace == InferredElementNamespace.XHTML
        && !node.getTagName().isDefinitelyVoid()) {
      errorReporter.report(node.getTagName().getTagLocation(), INVALID_SELF_CLOSING_TAG, tagName);
    } else {
      openTagStack.push(node);
      openTagToInferredNamesapce.put(node, namespace);
    }

    pushState(HtmlContext.HTML_TAG_NAME);
    visit(node.getChild(0));
    popState();
    pushState(HtmlContext.HTML_TAG);
    for (int i = 1; i < node.numChildren(); i++) {
      visit(node.getChild(i));
    }
    popState();
  }

  @Override
  protected void visitHtmlAttributeNode(HtmlAttributeNode node) {
    visit(node.getChild(0)); // visit the name (or dynamic attribute)
    if (node.hasValue()) {
      // if there is a value, the name must be a constant.... who knows why though
      if (node.getChild(0).getKind() != Kind.RAW_TEXT_NODE) {
        errorReporter.report(node.getChild(0).getSourceLocation(), DYNAMIC_ATTRIBUTE_NAME);
      }
      StandaloneNode attributeValue = node.getChild(1);
      if (attributeValue.getKind() != Kind.HTML_ATTRIBUTE_VALUE_NODE) {
        // TODO(lukes): this should is impossible,  validate that
        errorReporter.report(attributeValue.getSourceLocation(), SOY_TAG_BEFORE_ATTR_VALUE);
      }
      for (int i = 1; i < node.numChildren(); i++) {
        visit(node.getChild(i));
      }
    }
  }

  @Override
  protected void visitHtmlAttributeValueNode(HtmlAttributeValueNode node) {
    // This is consistent with the old HtmlTransformVisitor, but doesn't really make sense, sometime
    // this is JS, URL or CSS...
    pushState(HtmlContext.HTML_NORMAL_ATTR_VALUE);
    super.visitHtmlAttributeValueNode(node);
    popState();
  }

  @Override
  protected void visitHtmlCommentNode(HtmlCommentNode node) {
    // Report an error if we see HTML comments in idom backend.
    errorReporter.report(
        node.getSourceLocation(), UNSUPPORTED_HTML_COMMENTS_FOR_IDOM, node.toSourceString());
  }

  @Override
  protected void visitLogNode(LogNode node) {
    // The contents of a {log} statement are always text.
    pushState(HtmlContext.TEXT);
    visitChildren(node);
    popState();
  }

  @Override
  protected void visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
    node.setHtmlContext(getState());
    visitChildren(node);
  }

  @Override
  protected void visitPrintNode(PrintNode node) {
    node.setHtmlContext(getState());
  }

  @Override
  protected void visitRawTextNode(RawTextNode node) {
    node.setHtmlContext(getState());
  }

  @Override
  protected void visitCssNode(CssNode node) {
    if (getState() != HtmlContext.HTML_NORMAL_ATTR_VALUE && getState() != HtmlContext.TEXT) {
      errorReporter.report(
          node.getSourceLocation(), INVALID_NODE_LOCATION_OUTSIDE_OF_ATTRIBUTE_VALUE, "css");
    }
    super.visitCssNode(node);
  }

  @Override
  protected void visitXidNode(XidNode node) {
    if (getState() != HtmlContext.HTML_NORMAL_ATTR_VALUE && getState() != HtmlContext.TEXT) {
      errorReporter.report(
          node.getSourceLocation(), INVALID_NODE_LOCATION_OUTSIDE_OF_ATTRIBUTE_VALUE, "xid");
    }
    super.visitXidNode(node);
  }

  @Override
  protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }

  /**
   * @param tagName The tag name to get the namespace for, given the current stack of open Elements.
   */
  private InferredElementNamespace getNamespace(String tagName) {
    if (tagName.equalsIgnoreCase("svg")) {
      return InferredElementNamespace.SVG;
    }

    // If at the root of a template, treat it as being in the XHTML namespace. Ideally, we would
    // be able to check the union of the callsites and figure out what namespace were from there.
    // Ultimately we cannot tell if a template will be rendered into an SVG at runtime. For almost
    // all cases, XHTML is the right value however.
    if (tagName.equalsIgnoreCase("foreignObject") || openTagStack.isEmpty()) {
      return InferredElementNamespace.XHTML;
    }

    return openTagToInferredNamesapce.get(openTagStack.peek());
  }

  private void pushState(HtmlContext context) {
    stateStack.push(context);
  }

  private void popState() {
    stateStack.pop();
  }

  private HtmlContext getState() {
    return stateStack.peek();
  }

  private HtmlContext contextForKind(SanitizedContentKind contentKind) {
    switch (contentKind) {
      case HTML:
        return HtmlContext.HTML_PCDATA;
      case ATTRIBUTES:
        return HtmlContext.HTML_TAG;
      default:
        return HtmlContext.TEXT;
    }
  }
}
