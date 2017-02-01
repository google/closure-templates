/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.html.passes;

import static com.google.common.base.CharMatcher.whitespace;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.html.HtmlDefinitions;
import com.google.template.soy.html.IncrementalHtmlAttributeNode;
import com.google.template.soy.html.IncrementalHtmlCloseTagNode;
import com.google.template.soy.html.IncrementalHtmlOpenTagNode;
import com.google.template.soy.html.InferredElementNamespace;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.AutoescapeMode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CssNode;
import com.google.template.soy.soytree.HtmlContext;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LogNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.NamespaceDeclaration;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.LoopNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.XidNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Translates fragments of HTML tags, text nodes and attributes found in {@link RawTextNode}s to the
 * following nodes:
 *
 * <ul>
 *   <li>{@link IncrementalHtmlAttributeNode}
 *   <li>{@link IncrementalHtmlCloseTagNode}
 *   <li>{@link IncrementalHtmlOpenTagNode}
 * </ul>
 *
 * Also annotates msg and print nodes with their {@link HtmlContext}.
 *
 * <p>{@link RawTextNode}s not found in a place where HTML or attributes may be present, such as in
 * a {@link XidNode}, are left alone.
 */
public final class HtmlTransformVisitor extends AbstractSoyNodeVisitor<Void> {
  private static final SoyErrorKind ENDING_STATE_MISMATCH =
      SoyErrorKind.of(
          "Ending context of the content within a Soy tag must match the starting context. "
              + "Transition was from {0} to {1}");

  private static final SoyErrorKind EXPECTED_ATTRIBUTE_VALUE =
      SoyErrorKind.of("Expected to find a quoted " + "attribute value, but found \"{0}\".");

  private static final SoyErrorKind EXPECTED_TAG_CLOSE =
      SoyErrorKind.of("Expected to find the tag close character, >, but found \"{0}\".");

  private static final SoyErrorKind INVALID_SELF_CLOSING_TAG =
      SoyErrorKind.of(
          "Invalid self-closing tag for \"{0}\". Self-closing tags are only valid for void tags and"
              + " SVG content (partially supported). For a list of void elements, see "
              + "https://www.w3.org/TR/html5/syntax.html#void-elements.");

  private static final SoyErrorKind SOY_TAG_BEFORE_ATTR_VALUE =
      SoyErrorKind.of(
          "Soy statements are not "
              + "allowed before an attribute value. They should be moved inside a quotation mark.");

  private static final SoyErrorKind MISSING_TAG_NAME =
      SoyErrorKind.of("Found a tag with an empty tag " + "name.");

  private static final SoyErrorKind NON_STRICT_FILE =
      SoyErrorKind.of("The incremental HTML Soy backend " + "requires strict autoescape mode");

  private static final SoyErrorKind NON_STRICT_TEMPLATE =
      SoyErrorKind.of(
          "The incremental HTML Soy "
              + "backend requires strict autoescape mode for all templates.");

  private static final SoyErrorKind UNKNOWN_CONTENT_KIND =
      SoyErrorKind.of(
          "The incremental HTML Soy backend requires all let statements and parameters with "
              + "content to have a content kind");

  private static final SoyErrorKind INVALID_CSS_NODE_LOCATION =
      SoyErrorKind.of(
          "The incremental HTML Soy backend does not allow '{'css'}' nodes to appear in HTML "
              + "outside of attribute values.");

  private static final SoyErrorKind INVALID_XID_NODE_LOCATION =
      SoyErrorKind.of(
          "The incremental HTML Soy backend does not allow '{'xid'}' nodes to appear in HTML "
              + "outside of attribute values.");

  /** The last {@link HtmlContext} encountered. */
  private HtmlContext currentState = HtmlContext.HTML_PCDATA;

  /** True if we're expecting '>' after '/'. Will only be true in TAG. */
  private boolean isSelfClosingTag;

  /** The name of the current tag. */
  private String currentTag = "";

  /**
   * The current 'token' being built up. This may correspond to a tag name, attribute name,
   * attribute value or text node.
   */
  private final StringBuilder currentText = new StringBuilder();

  /** The name of the current attribute being examined. */
  private String currentAttributeName = "";

  /** The {@link StandaloneNode}s that make up the value of the current attribute. */
  private List<StandaloneNode> currentAttributeValues = new ArrayList<>();

  /**
   * The node that should be the parent of the nodes representing attributes or control structures
   * containing attributes in the portion of the tree currently being visited.
   */
  private ParentSoyNode<StandaloneNode> currentAttributesParent;

  /** Used to give newly created Nodes an id. */
  private IdGenerator idGen = null;

  /** Keeps track of the current open elements within a template */
  private final Deque<IncrementalHtmlOpenTagNode> openElementsDeque = new ArrayDeque<>();

  /**
   * Maps a RawTextNode to nodes corresponding to one or more HTML tag pieces or attributes. This is
   * added to by {@link #visitRawTextNode(RawTextNode)} whenever the end of a piece is encountered.
   * After {@link #exec(SoyNode)} finishes, the RawTextNodes are replaced with the corresponding
   * nodes.
   */
  private final ListMultimap<RawTextNode, StandaloneNode> transformMapping =
      ArrayListMultimap.create();

  /** The {@link RawTextNode}s that have been visited and should be removed. */
  private final Set<RawTextNode> visitedRawTextNodes = new HashSet<>();

  /**
   * Used to prevent reporting an error on each token after an equals if a non-quoted attribute
   * value is used, allowing the visitor to visit the rest of the tree looking for issues without a
   * flood of errors being generated.
   */
  private boolean suppressExpectedAttributeValueError = false;

  private final ErrorReporter errorReporter;

  public HtmlTransformVisitor(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  /**
   * Transforms all the {@link RawTextNode}s corresponding to HTML to the corresponding Html*Node.
   * Additionally, nodes that occur in HTML data or attributes declarations are annotated with
   * {@link HtmlContext}.
   *
   * @see AbstractSoyNodeVisitor#exec(com.google.template.soy.basetree.Node)
   */
  @Override
  public Void exec(SoyNode node) {
    super.exec(node);
    applyTransforms();

    return null;
  }

  /**
   * Applies the built up transforms, changing {@link RawTextNode}s to the corresponding Html*Nodes,
   * if any. If a node itself does not correspond to any new nodes, it is simply removed.
   */
  private void applyTransforms() {
    for (RawTextNode node : visitedRawTextNodes) {
      ParentSoyNode<StandaloneNode> parent = node.getParent();

      parent.addChildren(parent.getChildIndex(node), transformMapping.get(node));
      parent.removeChild(node);
    }
  }

  private HtmlContext getState() {
    return currentState;
  }

  private void setState(HtmlContext state) {
    currentState = state;
    isSelfClosingTag = false;
  }

  private void setSelfClosingTagState() {
    currentState = HtmlContext.HTML_TAG;
    isSelfClosingTag = true;
  }

  /**
   * Derives a {@link SourceLocation} from the given {@link RawTextNode}, for text the length of
   * charSequence ending at endOffset.
   */
  private SourceLocation deriveSourceLocation(RawTextNode node) {
    // TODO(sparhami) Since the parser strips templates and combines whitespace, including newlines
    // we can't find the correct source location based on where we are in the RawTextNode. The
    // parser needs to be modified to not strip whitespace and to not combine text before and after
    // comments. Doing the former is difficult due to commands like {\n}.
    return node.getSourceLocation();
  }

  /**
   * Creates a new {@link RawTextNode} in HTML context and maps it to node.
   *
   * @param node The node that the mapped node comes from.
   */
  private void createTextNode(RawTextNode node) {
    Preconditions.checkState(getState() == HtmlContext.HTML_PCDATA);
    String currentString = consumeText();

    if (currentString.length() > 0) {
      SourceLocation sl = deriveSourceLocation(node);
      transformMapping.put(node, new RawTextNode(idGen.genId(), currentString, sl, getState()));
    }
  }

  /**
   * Creates a {@link RawTextNode} for the current part of an attribute value and adds it to the
   * pending attribute value array.
   *
   * @param node The node that the mapped node comes from.
   */
  private void createAttributeValueNode(RawTextNode node) {
    Preconditions.checkState(getState() == HtmlContext.HTML_NORMAL_ATTR_VALUE);
    String currentString = consumeText();

    // Check to see if the currentText is empty. This may occur when we have something like
    // disabled="" or disabled="{$foo}" after the print tag is finished.
    if (currentString.length() > 0) {
      SourceLocation sl = deriveSourceLocation(node);
      currentAttributeValues.add(new RawTextNode(idGen.genId(), currentString, sl, getState()));
    }
  }

  /**
   * Creates a new {@link IncrementalHtmlAttributeNode} and maps it to node, taking all the
   * attribute values (text, conditionals, print statements) and adding them to the new attribute
   * node.
   *
   * @param node The node that the mapped node comes from.
   */
  private void createAttribute(RawTextNode node) {
    SourceLocation sl = deriveSourceLocation(node);
    IncrementalHtmlAttributeNode htmlAttributeNode =
        new IncrementalHtmlAttributeNode(idGen.genId(), currentAttributeName, sl);
    htmlAttributeNode.addChildren(currentAttributeValues);

    if (currentAttributesParent != null
        && !SoyTreeUtils.isDescendantOf(node, currentAttributesParent)) {
      currentAttributesParent.addChild(htmlAttributeNode);
    } else {
      transformMapping.put(node, htmlAttributeNode);
    }

    currentAttributeValues = new ArrayList<>();
  }

  /**
   * Handles a character within {@link HtmlContext#PCDATA}, where either a text node may be present
   * or the start of a new tag.
   *
   * @param node The node that the current character belongs to.
   * @param c The current character being examined.
   */
  private void handleHtmlPcData(RawTextNode node, char c) {
    if (c == '<') {
      // If we are encountering the start of a new tag, check to see if a text node with data is
      // being completed.
      createTextNode(node);
      setState(HtmlContext.HTML_TAG_NAME);
    } else {
      currentText.append(c);
    }
  }

  private String consumeText() {
    String token = currentText.toString();
    currentText.setLength(0);
    return token;
  }

  private void startCapturingAttributes() {
    currentAttributeValues = new ArrayList<>();
    setState(HtmlContext.HTML_TAG);
  }

  /**
   * Handles a character within {@link HtmlContext#TAG_NAME}, where the name of a tag must be
   * present.
   *
   * @param node The node that the current character belongs to.
   * @param c The current character being examined.
   */
  private void handleHtmlTagName(RawTextNode node, char c) {
    if (whitespace().matches(c) || c == '>' || (currentText.length() != 0 && c == '/')) {
      currentTag = consumeText();

      // No tag name, saw something like <> or <  >.
      if (currentTag.length() <= 0) {
        SourceLocation sl = deriveSourceLocation(node);
        errorReporter.report(sl, MISSING_TAG_NAME);
      }

      // Currently, closing tags and open tags are handled through the states. If this is not a
      // closing tag, then an open tag needs to be started.
      if (!currentTag.startsWith("/")) {
        SourceLocation sl = deriveSourceLocation(node);
        currentAttributesParent =
            new IncrementalHtmlOpenTagNode(idGen.genId(), currentTag, getNamespace(currentTag), sl);
      }

      if (c == '>' || c == '/') {
        // Handle close tags and tags that only have a tag name (e.g. <div>).
        handleHtmlTag(node, c);
      } else {
        startCapturingAttributes();
      }
    } else {
      currentText.append(c);
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
    if (tagName.equalsIgnoreCase("foreignObject") || openElementsDeque.isEmpty()) {
      return InferredElementNamespace.XHTML;
    }

    return openElementsDeque.peek().getNamespace();
  }

  private void emitHtmlOpenTagNode(
      String tagName, boolean isSelfClosing, RawTextNode node, SourceLocation sl) {
    InferredElementNamespace namespace = getNamespace(tagName);
    IncrementalHtmlOpenTagNode htmlOpenTagNode =
        (IncrementalHtmlOpenTagNode) currentAttributesParent;

    transformMapping.put(node, htmlOpenTagNode);
    openElementsDeque.push(htmlOpenTagNode);
    currentAttributesParent = null;

    if (!isSelfClosing) {
      return;
    }

    /*
     * Per spec, only two types of tags allow self-closing: void tags (e.g. input) and
     * svg/math content. We ignore math content since only Firefox supports it. Having a self
     * closing tag on all others is a parse error. For more info, look for "self-closing flag" in
     * the spec.
     * TODO(sparhami) we currently track the open elements stack on a per-template basis. That means
     * if you have the svg element itself declared in one template, and svg content with a self
     * closing tag in another, we will currently trip up on it. If all the callsites for a
     * particular template are all within svg content, we could inherit that initial namespace.
     */
    if (namespace == InferredElementNamespace.SVG) {
      emitHtmlCloseTagNode(tagName, node, sl);
    } else if (!HtmlDefinitions.HTML5_VOID_ELEMENTS.contains(currentTag)) {
      // Continuing parsing while treating the tag as a simple start tag matches how browsers
      // handle this case. Note: a close tag is not emitted.
      errorReporter.report(sl, INVALID_SELF_CLOSING_TAG, currentTag);
    }
  }

  private void emitHtmlCloseTagNode(String tagName, RawTextNode node, SourceLocation sl) {
    transformMapping.put(node, new IncrementalHtmlCloseTagNode(idGen.genId(), tagName, sl));

    boolean tagMatches = false;
    // When encountering a closing tag, need to pop off any unclosed tags.
    while (!openElementsDeque.isEmpty() && !tagMatches) {
      IncrementalHtmlOpenTagNode htmlOpenTagNode = openElementsDeque.pop();
      tagMatches = tagName.equalsIgnoreCase(htmlOpenTagNode.getTagName());
    }
  }

  /**
   * Handles a character within {@link HtmlContext#TAG}, where either an attribute declaration or
   * the end of a tag may appear.
   *
   * @param node The node that the current character belongs to.
   * @param c The current character being examined.
   */
  private void handleHtmlTag(RawTextNode node, char c) {
    if (c == '>') {
      // Found the end of the tag - create the appropriate open tag or close tag node, depending
      // on which we are ending.
      SourceLocation sl = deriveSourceLocation(node);

      if (currentTag.startsWith("/")) {
        emitHtmlCloseTagNode(currentTag.substring(1), node, sl);
      } else {
        emitHtmlOpenTagNode(currentTag, false, node, sl);
      }

      setState(HtmlContext.HTML_PCDATA);
    } else if (c == '/') {
      setSelfClosingTagState();
    } else if (whitespace().matches(c)) {
      // Skip whitespace characters.
    } else {
      setState(HtmlContext.HTML_ATTRIBUTE_NAME);
      currentText.append(c);
    }
  }

  /** Handles 8.2.4.43 of the W3C HTML5 spec */
  private void handleHtmlSelfClosingStartTag(RawTextNode node, char c) {
    SourceLocation sl = deriveSourceLocation(node);

    if (c != '>') {
      errorReporter.report(sl, EXPECTED_TAG_CLOSE, c);
      setState(HtmlContext.HTML_TAG);
      consumeCharacter(node, c);
      return;
    }

    emitHtmlOpenTagNode(currentTag, true, node, sl);
    setState(HtmlContext.HTML_PCDATA);
  }

  /**
   * Handles the state where an attribute name is being declared. If an =, > or whitespace character
   * is encountered, then the attribute name is completed.
   *
   * @param node The node that the current character belongs to
   * @param c The current character being examined
   */
  private void handleHtmlAttributeName(RawTextNode node, char c) {
    if (c == '=') {
      // Next thing we should see is " to start the attribute value.
      currentAttributeName = consumeText();
      setState(HtmlContext.HTML_BEFORE_ATTRIBUTE_VALUE);
      suppressExpectedAttributeValueError = false;
    } else if (c == '>') {
      // Tag ended with an attribute with no value (e.g. disabled) - create an attribute, then
      // handle the tag end.
      currentAttributeName = consumeText();
      createAttribute(node);
      handleHtmlTag(node, c);
    } else if (whitespace().matches(c)) {
      // Handle a value-less attribute, then start looking for another attribute or the end of the
      // tag.
      currentAttributeName = consumeText();
      createAttribute(node);
      setState(HtmlContext.HTML_TAG);
    } else {
      currentText.append(c);
    }
  }

  /**
   * Handle the next character after the equals in the attribute declaration. The only allowed
   * character is a double quote.
   *
   * @param node The node that the current character belongs to.
   * @param c The current character being examined.
   */
  private void handleHtmlBeforeAttributeValue(RawTextNode node, char c) {
    if (c == '"') {
      setState(HtmlContext.HTML_NORMAL_ATTR_VALUE);
    } else if (!suppressExpectedAttributeValueError) {
      SourceLocation sl = deriveSourceLocation(node);
      errorReporter.report(sl, EXPECTED_ATTRIBUTE_VALUE, c);
      suppressExpectedAttributeValueError = true;
    }

    // Just move on if we see a space or closing bracket so that the rest of the tree can be checked
    // for issues.
    if (c == '>') {
      handleHtmlTag(node, c);
    } else if (whitespace().matches(c)) {
      setState(HtmlContext.HTML_TAG);
    }
  }

  /**
   * Handles an HTML attribute value. When an end quote is encountered, a new {@link
   * IncrementalHtmlAttributeNode} is created with the {@link SoyNode}s that make up the value.
   *
   * @param node The node that the current character belongs to.
   * @param c The current character being examined.
   */
  private void handleHtmlNormalAttrValue(RawTextNode node, char c) {
    if (c == '"') {
      createAttributeValueNode(node);
      createAttribute(node);
      setState(HtmlContext.HTML_TAG);
    } else {
      currentText.append(c);
    }
  }

  /**
   * Consumes a single character, taking action to create a node if necessary or just adding it to
   * the current pending text.
   *
   * @param node The node that the current character belongs to.
   * @param c The current character being examined.
   */
  private void consumeCharacter(RawTextNode node, char c) {
    switch (getState()) {
      case HTML_PCDATA:
        handleHtmlPcData(node, c);
        break;
      case HTML_TAG_NAME:
        handleHtmlTagName(node, c);
        break;
      case HTML_TAG:
        if (isSelfClosingTag) {
          handleHtmlSelfClosingStartTag(node, c);
        } else {
          handleHtmlTag(node, c);
        }
        break;
      case HTML_ATTRIBUTE_NAME:
        handleHtmlAttributeName(node, c);
        break;
      case HTML_BEFORE_ATTRIBUTE_VALUE:
        handleHtmlBeforeAttributeValue(node, c);
        break;
      case HTML_NORMAL_ATTR_VALUE:
        handleHtmlNormalAttrValue(node, c);
        break;
      default:
        break;
    }
  }

  /**
   * Visits a {@link RawTextNode}, going through each of the characters and building up the HTML
   * pieces (e.g. {@link IncrementalHtmlOpenTagNode} and {@link IncrementalHtmlCloseTagNode}). The
   * new pieces are mapped to the {@link RawTextNode} where they ended. The {@link
   * #applyTransforms()} method actually performs the replacement.
   */
  @Override
  protected void visitRawTextNode(RawTextNode node) {
    String content = node.getRawText();

    // Mark all visited RawTextNodes for removal. A single RawTextNode may not map to any Html*Nodes
    // by itself, but we still want to remove it.
    visitedRawTextNodes.add(node);

    for (int i = 0; i < content.length(); i += 1) {
      consumeCharacter(node, content.charAt(i));
    }

    switch (getState()) {
      case HTML_TAG_NAME:
        /*
         * Force the end of a tag in the case we have something like:
         * <div{if $foo}...{/if} ...>
         */
        consumeCharacter(node, ' ');
        break;
      case HTML_PCDATA:
        createTextNode(node);
        break;
      case HTML_ATTRIBUTE_NAME:
        // Value-less attribute inside a soy block, e.g. {if $condition}disabled{/if}
        consumeCharacter(node, ' ');
        break;
      case HTML_NORMAL_ATTR_VALUE:
        /*
         * Reached the end of a RawTextNode with some text, for example from:
         *
         *   <div foo="bar {if $condition}...{/if}">
         *
         * Take the text up until the end of the RawTextNode, "bar ", and add it to the attribute
         * values.
         */
        createAttributeValueNode(node);
        break;
      default:
        break;
    }
  }

  /**
   * Checks to see if a given {@link SoyNode} is valid in the current context, reporting an error if
   * it is not.
   */
  private void checkForValidSoyNodeLocation(SoyNode node) {
    switch (getState()) {
      case HTML_BEFORE_ATTRIBUTE_VALUE:
        errorReporter.report(node.getSourceLocation(), SOY_TAG_BEFORE_ATTR_VALUE);
        break;
      default:
        break;
    }
  }

  /**
   * Visits a {@link PrintNode}, annotating it with an {@link HtmlContext}. This allows the code
   * generator to handle HTML print statements separately and know the state in which they occurred.
   * If the {@link PrintNode} occurs in {@link HtmlContext#HTML_NORMAL_ATTR_VALUE}, the print node
   * becomes part of the current attribute's value.
   */
  @Override
  protected void visitPrintNode(PrintNode node) {
    checkForValidSoyNodeLocation(node);

    if (getState() == HtmlContext.HTML_NORMAL_ATTR_VALUE) {
      // A PrintNode in an attribute value, add it to the current attribute values, which will get
      // added to the attribute node once the attribute value ends.
      currentAttributeValues.add(node);
      node.getParent().removeChild(node);
    } else if (getState() == HtmlContext.HTML_TAG) {
      moveToCurrentAttributesParent(node);
    }
    node.setHtmlContext(getState());
  }

  /**
   * Visit {@link LetContentNode}s and {@link CallParamContentNode}s, transforming the {@link
   * RawTextNode}s inside to The corresponding Html* nodes.
   *
   * <ul>
   *   <li>For {@link ContentKind#HTML}, it simply visits the children and does the normal
   *       transformation.
   *   <li>For {@link ContentKind#ATTRIBUTES}, it transforms the children as if they were in the
   *       attribute declaration portion of an HTML tag.
   *   <li>All other kinds {@link ContentKind}s are ignored by this visitor, leaving content within
   *       things like kind="text" alone.
   * </ul>
   *
   * @param node A {@link LetContentNode}or {@link CallParamContentNode}
   */
  private void visitLetParamContentNode(RenderUnitNode node) {
    checkForValidSoyNodeLocation(node);

    if (node.getContentKind() == null) {
      errorReporter.report(node.getSourceLocation(), UNKNOWN_CONTENT_KIND);
    } else if (node.getContentKind() == ContentKind.HTML) {
      visitSoyNode(node, true);
    } else if (node.getContentKind() == ContentKind.ATTRIBUTES) {
      HtmlContext startState = getState();
      startCapturingAttributes();
      currentAttributesParent = node;
      visitChildrenAllowingConcurrentModification(node);
      currentAttributesParent = null;
      setState(startState);
    } else {
      new ContextSetterVisitor(HtmlContext.TEXT).exec(node);
    }
  }

  @Override
  protected void visitLetContentNode(LetContentNode node) {
    visitLetParamContentNode(node);
  }

  @Override
  protected void visitCallParamContentNode(CallParamContentNode node) {
    visitLetParamContentNode(node);
  }

  /** Visits a {@link SoyFileNode}, making sure it has strict autoescape. */
  @Override
  protected void visitSoyFileNode(SoyFileNode node) {
    NamespaceDeclaration namespaceDeclaration = node.getNamespaceDeclaration();
    if (namespaceDeclaration.getDefaultAutoescapeMode() != AutoescapeMode.STRICT) {
      errorReporter.report(namespaceDeclaration.getAutoescapeModeLocation(), NON_STRICT_FILE);
    }

    visitChildren(node);
  }

  /** Visits a {@link SoyFileNode}, getting its id generator. */
  @Override
  protected void visitSoyFileSetNode(SoyFileSetNode node) {
    idGen = node.getNodeIdGenerator();

    visitChildren(node);
  }

  /**
   * Visits a {@link TemplateNode}, processing those that have kind html or attributes and making
   * sure that the autoescape mode is strict.
   */
  @Override
  protected void visitTemplateNode(TemplateNode node) {
    switch (node.getContentKind()) {
      case HTML:
        currentState = HtmlContext.HTML_PCDATA;
        break;
      case ATTRIBUTES:
        currentState = HtmlContext.HTML_TAG;
        currentAttributesParent = node;
        break;
      default:
        new ContextSetterVisitor(HtmlContext.TEXT).exec(node);
        return; // only need to do transformations for HTML / attributes
    }

    if (node.getAutoescapeMode() != AutoescapeMode.STRICT) {
      errorReporter.report(node.getSourceLocation(), NON_STRICT_TEMPLATE);
    }

    openElementsDeque.clear();
    visitSoyNode(node, true);
    currentAttributesParent = null;
  }

  /** Visits a {@link CallNode} - makes sure that the node does not occur in an invalid location. */
  @Override
  protected void visitCallNode(CallNode node) {
    checkForValidSoyNodeLocation(node);
    visitSoyNode(node);
  }

  @Override
  protected void visitIfCondNode(IfCondNode node) {
    visitSoyNode(node, true);
  }

  @Override
  protected void visitIfElseNode(IfElseNode node) {
    visitSoyNode(node, true);
  }

  @Override
  protected void visitSwitchCaseNode(SwitchCaseNode node) {
    visitSoyNode(node, true);
  }

  @Override
  protected void visitSwitchDefaultNode(SwitchDefaultNode node) {
    visitSoyNode(node, true);
  }

  @Override
  protected void visitLoopNode(LoopNode node) {
    visitSoyNode(node, true);
  }

  @Override
  protected void visitCssNode(CssNode node) {
    if (getState() != HtmlContext.HTML_NORMAL_ATTR_VALUE) {
      errorReporter.report(node.getSourceLocation(), INVALID_CSS_NODE_LOCATION);
    }

    visitSoyNode(node);
  }

  @Override
  protected void visitXidNode(XidNode node) {
    if (getState() != HtmlContext.HTML_NORMAL_ATTR_VALUE) {
      errorReporter.report(node.getSourceLocation(), INVALID_XID_NODE_LOCATION);
    }

    visitSoyNode(node);
  }

  @Override
  protected void visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
    node.setHtmlContext(getState());
    visitSoyNode(node);
  }

  @Override
  protected void visitLogNode(LogNode node) {
    // The contents of a {log} statement are always text.
    new ContextSetterVisitor(HtmlContext.TEXT).exec(node);
  }

  private void visitSoyNode(SoyNode node, boolean enforceState) {
    switch (getState()) {
      case HTML_BEFORE_ATTRIBUTE_VALUE:
        errorReporter.report(node.getSourceLocation(), SOY_TAG_BEFORE_ATTR_VALUE);
        break;
      case HTML_NORMAL_ATTR_VALUE:
        if (node instanceof StandaloneNode) {
          StandaloneNode standaloneNode = (StandaloneNode) node;
          standaloneNode.getParent().removeChild(standaloneNode);
          currentAttributeValues.add(standaloneNode);
          // We don't need to transform the children, but we do need to set their contexts.
          new ContextSetterVisitor(getState()) {
            // {param} tags inside attributes can only be TEXT.
            @Override
            protected void visitCallParamContentNode(CallParamContentNode node) {
              new ContextSetterVisitor(HtmlContext.TEXT).exec(node);
            }
          }.exec(node);
        }
        break;
      case HTML_TAG:
        moveToCurrentAttributesParent(node);
        visitChildrenAndCheckState(node, enforceState);
        break;
      case HTML_PCDATA:
        visitChildrenAndCheckState(node, enforceState);
        break;
      default:
        break;
    }
  }

  private void visitChildrenAndCheckState(SoyNode node, boolean enforceState) {
    if (node instanceof ParentSoyNode) {
      HtmlContext startState = getState();
      visitChildrenAllowingConcurrentModification((ParentSoyNode<?>) node);
      HtmlContext endState = getState();

      if (enforceState && startState != endState) {
        errorReporter.report(node.getSourceLocation(), ENDING_STATE_MISMATCH, startState, endState);
      }

      consumeText();
    }
  }

  /**
   * Moves the given node under the current attributes parent node, if it's not in its subtree
   * already.
   */
  private void moveToCurrentAttributesParent(SoyNode node) {
    if (currentAttributesParent != null
        && !SoyTreeUtils.isDescendantOf(node, currentAttributesParent)
        && node instanceof StandaloneNode) {
      StandaloneNode standaloneNode = (StandaloneNode) node;
      standaloneNode.getParent().removeChild(standaloneNode);
      currentAttributesParent.addChild(standaloneNode);
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.

  @Override
  protected void visitSoyNode(SoyNode node) {
    visitSoyNode(node, false);
  }

  /**
   * Sets the HtmlContext for all supported nodes to a specified value. The main visitor class calls
   * this visitor instead visiting nodes in textual contexts, since they don't need anything else.
   */
  private static class ContextSetterVisitor extends AbstractSoyNodeVisitor<Void> {
    private final HtmlContext value;

    ContextSetterVisitor(HtmlContext value) {
      this.value = value;
    }

    @Override
    protected void visitSoyNode(SoyNode node) {
      if (node instanceof ParentSoyNode<?>) {
        visitChildren((ParentSoyNode<?>) node);
      }
    }

    @Override
    protected void visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
      node.setHtmlContext(value);
      visitChildren(node);
    }

    @Override
    protected void visitPrintNode(PrintNode node) {
      node.setHtmlContext(value);
    }

    @Override
    protected void visitRawTextNode(RawTextNode node) {
      node.setHtmlContext(value);
    }
  }
}
