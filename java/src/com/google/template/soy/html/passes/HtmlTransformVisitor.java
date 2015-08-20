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

import com.google.common.base.CharMatcher;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyError;
import com.google.template.soy.html.HtmlAttributeNode;
import com.google.template.soy.html.HtmlCloseTagNode;
import com.google.template.soy.html.HtmlOpenTagEndNode;
import com.google.template.soy.html.HtmlOpenTagStartNode;
import com.google.template.soy.html.HtmlPrintNode;
import com.google.template.soy.html.HtmlState;
import com.google.template.soy.html.HtmlTextNode;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.AutoescapeMode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.LoopNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.XidNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Translates fragments of HTML tags, text nodes and attributes found in {@link RawTextNode}s to
 * the following nodes:
 *
 * <ul>
 *   <li>{@link HtmlAttributeNode}</li>
 *   <li>{@link HtmlCloseTagNode}</li>
 *   <li>{@link HtmlOpenTagEndNode}</li>
 *   <li>{@link HtmlOpenTagStartNode}</li>
 *   <li>{@link HtmlPrintNode}</li>
 *   <li>{@link HtmlTextNode}</li>
 * </ul>
 *
 * {@link RawTextNode}s not found in a place where HTML or attributes may be present, such as in a
 * {@link XidNode}, are left alone.
 */
public final class HtmlTransformVisitor extends AbstractSoyNodeVisitor<Void> {
  private static final SoyError ENDING_STATE_MISMATCH = SoyError.of("Ending context of the content "
      + "within a Soy tag must match the starting context. Transition was from {0} to {1}");

  private static final SoyError EXPECTED_ATTRIBUTE_VALUE = SoyError.of("Expected to find a quoted "
      + "attribute value, but found \"{0}\".");

  private static final SoyError SOY_TAG_IN_ATTR_NAME = SoyError.of("Soy statements are not allowed "
      + "in an attribute name declaration.");

  private static final SoyError SOY_TAG_BEFORE_ATTR_VALUE = SoyError.of("Soy statements are not "
      + "allowed before an attribute value. They should be moved inside a quotation mark.");

  private static final SoyError MISSING_TAG_NAME = SoyError.of("Found a tag with an empty tag "
      + "name.");

  private static final SoyError NON_STRICT_FILE = SoyError.of("The incremental HTML Soy backend "
      + "requires strict autoescape mode");

  private static final SoyError NON_STRICT_TEMPLATE = SoyError.of("The incremental HTML Soy "
      + "backend requires strict autoescape mode for all templates.");

  private static final SoyError TEMPLATE_CALL_IN_TAG = SoyError.of("The incremental HTML Soy "
      + "backend does not support template calls within HTML tag declarations.");

  private static final SoyError UNKNOWN_CONTENT_KIND = SoyError.of("The incremental HTML Soy "
      + "backend requires all let statements and parameters with content to have a content kind");

  /** The last {@link HtmlState} encountered. */
  private HtmlState currentState = HtmlState.PCDATA;

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

  /** Used to give newly created Nodes an id. */
  private IdGenerator idGen = null;

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
   * value is used, allowing the visitor to visit the rest of the tree looking for issues without
   * a flood of errors being generated.
   */
  private boolean suppressExpectedAttributeValueError = false;
  
  private final ErrorReporter errorReporter;

  public HtmlTransformVisitor(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  /**
   * Transforms all the {@link RawTextNode}s corresponding to HTML to the
   * corresponding Html*Node. Additionally, PrintNodes that occur in HTML data
   * or attributes declarations are wrapped with an {@link HtmlPrintNode}.
   *
   * @see AbstractSoyNodeVisitor#exec(com.google.template.soy.basetree.Node)
   */
  @Override public Void exec(SoyNode node) {
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

  private HtmlState getState() {
    return currentState;
  }

  private void setState(HtmlState state) {
    currentState = state;
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
   * Creates a new {@link HtmlTextNode} and maps it to node.
   * @param node The node that the mapped node comes from.
   */
  private void createTextNode(RawTextNode node) {
    // Consume text, removing unnecessary whitespace
    String currentString = consumeText(true);

    if (currentString.length() > 0) {
      SourceLocation sl = deriveSourceLocation(node);
      transformMapping.put(node, new HtmlTextNode(idGen.genId(), currentString, sl));
    }
  }

  /**
   * Creates a {@link RawTextNode} for the current part of an attribute value and adds it to the
   * pending attribute value array.
   * @param node The node that the mapped node comes from.
   */
  private void createAttributeValueNode(RawTextNode node) {
    String currentString = consumeText(false);

    // Check to see if the currentText is empty. This may occur when we have something like
    // disabled="" or disabled="{$foo}" after the print tag is finished.
    if (currentString.length() > 0) {
      SourceLocation sl = deriveSourceLocation(node);
      currentAttributeValues.add(new RawTextNode(idGen.genId(), currentString, sl));
    }
  }

  /**
   * Creates a new {@link HtmlAttributeNode} and maps it to node, taking all the attribute values
   * (text, conditionals, print statements) and adding them to the new attribute node.
   * @param node The node that the mapped node comes from.
   */
  private void createAttribute(RawTextNode node) {
    SourceLocation sl = deriveSourceLocation(node);
    HtmlAttributeNode htmlAttributeNode = new HtmlAttributeNode(
        idGen.genId(),
        currentAttributeName,
        sl);
    htmlAttributeNode.addChildren(currentAttributeValues);
    transformMapping.put(node, htmlAttributeNode);

    currentAttributeValues = new ArrayList<>();
  }

  /**
   * Handles a character within {@link HtmlState#PCDATA}, where either a text node may be present
   * or the start of a new tag.
   * @param node The node that the current character belongs to.
   * @param c The current character being examined.
   */
  private void handleHtmlPcData(RawTextNode node, char c) {
    if (c == '<') {
      // If we are encountering the start of a new tag, check to see if a text node with data is
      // being completed.
      createTextNode(node);
      setState(HtmlState.TAG_NAME);
    } else {
      currentText.append(c);
    }
  }

  private String consumeText(boolean trim) {
    String token;

    if (trim) {
      token = CharMatcher.WHITESPACE.trimFrom(currentText);
    } else {
      token = currentText.toString();
    }

    currentText.setLength(0);
    return token;
  }

  /**
   * Handles a character within {@link HtmlState#TAG_NAME}, where the name of a tag must be present.
   * @param node The node that the current character belongs to.
   * @param c The current character being examined.
   */
  private void handleHtmlTagName(RawTextNode node, char c) {
    if (CharMatcher.WHITESPACE.matches(c) || c == '>') {
      currentTag = consumeText(false);

      // No tag name, saw something like <> or <  >.
      if (currentTag.length() <= 0) {
        SourceLocation sl = deriveSourceLocation(node);
        errorReporter.report(sl, MISSING_TAG_NAME);
      }

      // Currently, closing tags and open tags are handled through the states. If this is not a
      // closing tag, then an open tag needs to be started.
      if (!currentTag.startsWith("/")) {
        SourceLocation sl = deriveSourceLocation(node);
        transformMapping.put(node, new HtmlOpenTagStartNode(idGen.genId(), currentTag, sl));
      }

      if (c == '>') {
        // Handle close tags and tags that only have a tag name (e.g. <div>).
        handleHtmlTag(node, c);
      } else {
        // Get ready to capture attributes.
        currentAttributeValues = new ArrayList<>();
        setState(HtmlState.TAG);
      }
    } else {
      currentText.append(c);
    }
  }

  /**
   * Handles a character within {@link HtmlState#TAG}, where either an attribute declaration or the
   * end of a tag may appear.
   * @param node The node that the current character belongs to.
   * @param c The current character being examined.
   */
  private void handleHtmlTag(RawTextNode node, char c) {
    if (c == '>') {
      // Found the end of the tag - create the appropriate open tag or close tag node, depending
      // on which we are ending.
      SourceLocation sl = deriveSourceLocation(node);

      if (currentTag.startsWith("/")) {
        transformMapping.put(node, new HtmlCloseTagNode(
            idGen.genId(),
            currentTag.substring(1),
            sl));
      } else {
        transformMapping.put(node, new HtmlOpenTagEndNode(idGen.genId(), currentTag, sl));
      }

      setState(HtmlState.PCDATA);
    } else if (CharMatcher.WHITESPACE.matches(c)) {
      // Skip whitespace characters.
    } else {
      setState(HtmlState.ATTRIBUTE_NAME);
      currentText.append(c);
    }
  }

  /**
   * Handles the state where an attribute name is being declared. If an =, > or whitespace character
   * is encountered, then the attribute name is completed.
   * @param node The node that the current character belongs to
   * @param c The current character being examined
   */
  private void handleHtmlAttributeName(RawTextNode node, char c) {
    if (c == '=') {
      // Next thing we should see is " to start the attribute value.
      currentAttributeName = consumeText(false);
      setState(HtmlState.BEFORE_ATTRIBUTE_VALUE);
      suppressExpectedAttributeValueError = false;
    } else if (c == '>') {
      // Tag ended with an attribute with no value (e.g. disabled) - create an attribute, then
      // handle the tag end.
      currentAttributeName = consumeText(false);
      createAttribute(node);
      handleHtmlTag(node, c);
    } else if (CharMatcher.WHITESPACE.matches(c)) {
      // Handle a value-less attribute, then start looking for another attribute or the end of the
      // tag.
      currentAttributeName = consumeText(false);
      createAttribute(node);
      setState(HtmlState.TAG);
    } else {
      currentText.append(c);
    }
  }

  /**
   * Handle the next character after the equals in the attribute declaration. The only allowed
   * character is a double quote.
   * @param node The node that the current character belongs to.
   * @param c The current character being examined.
   */
  private void handleHtmlBeforeAttributeValue(RawTextNode node, char c) {
    if (c == '"') {
      setState(HtmlState.ATTR_VALUE);
    } else if (!suppressExpectedAttributeValueError) {
      SourceLocation sl = deriveSourceLocation(node);
      errorReporter.report(sl,  EXPECTED_ATTRIBUTE_VALUE, c);
      suppressExpectedAttributeValueError  = true;
    }

    // Just move on if we see a space or closing bracket so that the rest of the tree can be checked
    // for issues.
    if (c == '>') {
      handleHtmlTag(node, c);
    } else if (CharMatcher.WHITESPACE.matches(c)) {
      setState(HtmlState.TAG);
    }
  }

  /**
   * Handles an HTML attribute value. When an end quote is encountered, a new {@link
   * HtmlAttributeNode} is created with the {@link SoyNode}s that make up the value.
   * @param node The node that the current character belongs to.
   * @param c The current character being examined.
   */
  private void handleHtmlNormalAttrValue(RawTextNode node, char c) {
    if (c == '"') {
      createAttributeValueNode(node);
      createAttribute(node);
      setState(HtmlState.TAG);
    } else {
      currentText.append(c);
    }
  }

  /**
   * Consumes a single character, taking action to create a node if necessary or just adding it to
   * the current pending text.
   * @param node The node that the current character belongs to.
   * @param c The current character being examined.
   */
  private void consumeCharacter(RawTextNode node, char c) {
    switch(getState()) {
      case PCDATA:
        handleHtmlPcData(node, c);
        break;
      case TAG_NAME:
        handleHtmlTagName(node, c);
        break;
      case TAG:
        handleHtmlTag(node, c);
        break;
      case ATTRIBUTE_NAME:
        handleHtmlAttributeName(node, c);
        break;
      case BEFORE_ATTRIBUTE_VALUE:
        handleHtmlBeforeAttributeValue(node, c);
        break;
      case ATTR_VALUE:
        handleHtmlNormalAttrValue(node, c);
        break;
      default:
        break;
    }
  }

  /**
   * Visits a {@link RawTextNode}, going through each of the characters and building up the HTML
   * pieces (e.g. {@link HtmlOpenTagStartNode} and {@link HtmlOpenTagEndNode}). The new pieces are
   * mapped to the {@link RawTextNode} where they ended. The {@link #applyTransforms()} method
   * actually performs the replacement.
   */
  @Override protected void visitRawTextNode(RawTextNode node) {
    String content = node.getRawText();

    // Mark all visited RawTextNodes for removal. A single RawTextNode may not map to any Html*Nodes
    // by itself, but we still want to remove it.
    visitedRawTextNodes.add(node);
    
    // Just skip empty nodes
    if (CharMatcher.WHITESPACE.matchesAllOf(content)) {
      return;
    }

    for (int i = 0; i < content.length(); i += 1) {
      consumeCharacter(node, content.charAt(i));
    }

    switch(getState()) {
      case TAG_NAME:
        /*
         * Force the end of a tag in the case we have something like:
         * <div{if $foo}...{/if} ...>
         */
        consumeCharacter(node, ' ');
        break;
      case PCDATA:
        createTextNode(node);
        break;
      case ATTR_VALUE:
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
    switch(getState()) {
      case ATTRIBUTE_NAME:
        errorReporter.report(node.getSourceLocation(), SOY_TAG_IN_ATTR_NAME);
        break;
      case BEFORE_ATTRIBUTE_VALUE:
        errorReporter.report(node.getSourceLocation(), SOY_TAG_BEFORE_ATTR_VALUE);
        break;
      default:
        break;
    }
  }

  /**
   * Visits a {@link PrintNode}, wrapping it with an HtmlPrintNode node if it occurs in {@link
   * HtmlState#TAG} or {@link HtmlState#PCDATA}. This allows the code generator to handle those
   * print statements separately and know the state in which they occurred. If the {@link PrintNode}
   * occurs in {@link HtmlState#ATTR_VALUE}, then the print node becomes a part of the
   * current attribute's value.
   */
  @Override protected void visitPrintNode(PrintNode node) {
    checkForValidSoyNodeLocation(node);

    HtmlPrintNode htmlPrintNode;
    switch(getState()) {
      case ATTR_VALUE:
        // A PrintNode in an attribute value, add it to the current attribute values, which will get
        // added to the attribute node once the attribute value ends.
        currentAttributeValues.add(node);
        node.getParent().removeChild(node);
        break;
      case TAG:
        // A PrintNode in the tag context - this is a print of something that has kind="attributes",
        // keep track of the context using an HtmlPrintNode so that the code generator knows what.
        // to do with it.
        htmlPrintNode = new HtmlPrintNode(
            idGen.genId(),
            node,
            HtmlPrintNode.Context.HTML_TAG,
            node.getSourceLocation());
        node.getParent().replaceChild(node, htmlPrintNode);
        break;
      case PCDATA:
        // A PrintNode in the pcdata context - this is a print of something that is the child of
        // an HTML element. This could be html, text, css, etc., just need to keep track of the
        // the context and the code generator will do the right thing with each type.
        htmlPrintNode = new HtmlPrintNode(
            idGen.genId(),
            node,
            HtmlPrintNode.Context.HTML_PCDATA,
            node.getSourceLocation());
        node.getParent().replaceChild(node, htmlPrintNode);
        break;
      default:
        break;
    }
  }

  /**
   * Visit {@link LetContentNode}s and {@link CallParamContentNode}s, transforming the {@link
   * RawTextNode}s inside to The corresponding Html* nodes.
   * <ul>
   * <li>For {@link ContentKind#HTML}, it simply visits the children and does the normal
   * transformation.</li>
   * <li>For {@link ContentKind#ATTRIBUTES}, it transforms the children as if they were in the
   * attribute declaration portion of an HTML tag.</li>
   * <li>All other kinds {@link ContentKind}s are ignored by this visitor, leaving content
   * within things like kind="text" alone.</li>
   * </ul>
   * @param node A {@link LetContentNode}or {@link CallParamContentNode}
   */
  private void visitLetParamContentNode(RenderUnitNode node) {
    checkForValidSoyNodeLocation(node);

    if (node.getContentKind() == null) {
      errorReporter.report(node.getSourceLocation(), UNKNOWN_CONTENT_KIND);
    } else if (node.getContentKind() == ContentKind.HTML) {
      visitSoyNode(node, true);
    } else if (node.getContentKind() == ContentKind.ATTRIBUTES) {
      HtmlState startState = getState();
      setState(HtmlState.TAG);
      visitChildren(node);
      setState(startState);
    }
  }

  @Override protected void visitLetContentNode(LetContentNode node) {
    visitLetParamContentNode(node);
  }

  @Override protected void visitCallParamContentNode(CallParamContentNode node) {
    visitLetParamContentNode(node);
  }

  /**
   * Visits a {@link SoyFileNode}, making sure it has strict autoescape.
   */
  @Override protected void visitSoyFileNode(SoyFileNode node) {
    if (node.getDefaultAutoescapeMode() != AutoescapeMode.STRICT) {
      errorReporter.report(node.getSourceLocation(), NON_STRICT_FILE);
    }

    visitChildren(node);
  }

  /**
   * Visits a {@link SoyFileNode}, getting its id generator.
   */
  @Override
  protected void visitSoyFileSetNode(SoyFileSetNode node) {
    idGen = node.getNodeIdGenerator();

    visitChildren(node);
  }

  /**
   * Visits a {@link TemplateNode}, processing those that have kind html or
   * attributes and making sure that the autoescape mode is strict.
   */
  @Override protected void visitTemplateNode(TemplateNode node) {
    if (node.getContentKind() != ContentKind.HTML
        && node.getContentKind() != ContentKind.ATTRIBUTES) {
      return;
    }

    if (node.getAutoescapeMode() != AutoescapeMode.STRICT) {
      errorReporter.report(node.getSourceLocation(), NON_STRICT_TEMPLATE);
    }

    visitSoyNode(node, true);
  }

  /**
   * Visits a {@link CallNode} - makes sure that the node does not occur within an attribute state
   * (e.g. after {@code <div} and before {@code >}).
   */
  @Override protected void visitCallNode(CallNode node) {
    checkForValidSoyNodeLocation(node);

    if (getState().isAttributeState()) {
      errorReporter.report(node.getSourceLocation(), TEMPLATE_CALL_IN_TAG);
    }

    visitChildren(node);
  }

  @Override protected void visitIfCondNode(IfCondNode node) {
    visitSoyNode(node, true);
  }

  @Override protected void visitIfElseNode(IfElseNode node) {
    visitSoyNode(node, true);
  }

  @Override protected void visitSwitchCaseNode(SwitchCaseNode node) {
    visitSoyNode(node, true);
  }

  @Override protected void visitSwitchDefaultNode(SwitchDefaultNode node) {
    visitSoyNode(node, true);
  }

  @Override
  protected void visitLoopNode(LoopNode node) {
    visitSoyNode(node, true);
  }

  private void visitSoyNode(SoyNode node, boolean enforceState) {
    switch(getState()) {
      case ATTRIBUTE_NAME:
        errorReporter.report(node.getSourceLocation(), SOY_TAG_IN_ATTR_NAME);
        break;
      case BEFORE_ATTRIBUTE_VALUE:
        errorReporter.report(node.getSourceLocation(), SOY_TAG_BEFORE_ATTR_VALUE);
        break;
      case ATTR_VALUE:
        if (node instanceof StandaloneNode) {
          StandaloneNode standaloneNode = (StandaloneNode) node;
          standaloneNode.getParent().removeChild(standaloneNode);
          currentAttributeValues.add(standaloneNode);
        }
        break;
      case TAG:
      case PCDATA:
        if (node instanceof ParentSoyNode<?>) {
          HtmlState startState = getState();
          visitChildrenAllowingConcurrentModification((ParentSoyNode<?>) node);
          HtmlState endState = getState();

          if (enforceState && startState != endState) {
            errorReporter.report(
                node.getSourceLocation(),
                ENDING_STATE_MISMATCH,
                startState, endState);
          }

          consumeText(false);
        }
        break;
      default:
        break;
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.

  @Override protected void visitSoyNode(SoyNode node) {
    visitSoyNode(node, false);
  }
}
