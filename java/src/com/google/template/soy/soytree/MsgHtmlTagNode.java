/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.soytree;

import com.google.common.base.Ascii;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.basetree.NodeVisitor;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprEquivalence;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.soytree.SoyNode.MsgPlaceholderInitialNode;
import javax.annotation.Nullable;

/**
 * Node representing an HTML tag within a {@code msg} statement/block.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class MsgHtmlTagNode extends AbstractBlockNode implements MsgPlaceholderInitialNode {

  private static final SoyErrorKind DYNAMIC_TAG_NAME_IN_MSG_BLOCK =
      SoyErrorKind.of("HTML tags within within ''msg'' blocks must use constant tag names.");
  private static final SoyErrorKind INVALID_PHNAME_ATTRIBUTE =
      SoyErrorKind.of("''phname'' attribute is not a valid identifier.");
  private static final SoyErrorKind MULTIPLE_PHNAME_ATTRIBUTES =
      SoyErrorKind.of("Multiple ''phname'' attributes in HTML tag.");

  /**
   * Creates a {@link MsgHtmlTagNode} from a {@link HtmlTagNode}.
   *
   * <p>If the node contains a {@code phname} attribute, it will be <em>removed</em> from the node
   * and used as the placeholder name, it will _not_ be rendered.
   */
  public static MsgHtmlTagNode fromNode(int id, HtmlTagNode tagNode, ErrorReporter errorReporter) {
    String fullTagText = getFullTagText(tagNode); // do this before removing the phname attribute
    String userSpecifiedPhName = getUserSpecifiedPhName(tagNode, errorReporter);
    String lcTagName = getLcTagName(errorReporter, tagNode.getTagName());
    if (tagNode instanceof HtmlCloseTagNode) {
      // TODO(lukes): the lcTagName logic below requires this leading '/' for close tags.  Just make
      // it understand the node type instead.
      lcTagName = "/" + lcTagName;
    }
    return new MsgHtmlTagNode(
        id,
        tagNode.getSourceLocation(),
        userSpecifiedPhName,
        lcTagName,
        tagNode instanceof HtmlOpenTagNode && ((HtmlOpenTagNode) tagNode).isSelfClosing(),
        fullTagText != null,
        fullTagText,
        tagNode);
  }

  /**
   * This method calculates a string that can be used to tell if two tags that were turned into
   * placeholders are equivalent and thus could be turned into identical placeholders.
   *
   * <p>In theory we should use something like {@link ExprEquivalence} to see if two nodes would
   * render the same thing. However, for backwards compatibility we need to use a different
   * heuristic. The old code would simply detect if the node had a single child and use the {@link
   * SoyNode#toSourceString()} of it for the tag text. Due to how the children were constructed,
   * this would only happen if the tag was a single {@link RawTextNode}, e.g. {@code <foo
   * class=bar>}. Now that we are actually parsing the html tags the rules are more complex. We
   * should instead only use the {@link SoyNode#toSourceString()} if the only (transitive) children
   * are {@link RawTextNode}, {@link HtmlAttributeNode} or {@link HtmlAttributeValueNode}.
   */
  @Nullable
  private static String getFullTagText(HtmlTagNode openTagNode) {
    final boolean[] isConstantContent = {true};
    SoyTreeUtils.visitAllNodes(
        openTagNode,
        new NodeVisitor<Node, Boolean>() {
          @Override
          public Boolean exec(Node node) {
            if (node instanceof RawTextNode
                || node instanceof HtmlAttributeNode
                || node instanceof HtmlAttributeValueNode
                || node instanceof HtmlOpenTagNode
                || node instanceof HtmlCloseTagNode) {
              return true; // keep going
            }
            isConstantContent[0] = false;
            return false; // abort
          }
        });
    if (isConstantContent[0]) {
      // toSourceString is lame, but how this worked before
      return openTagNode.toSourceString();
    }
    return null;
  }

  /**
   * Returns the value of the {@code phname} attribute and removes it from the tag if one exists,
   * otherwise returns {@code null}.
   */
  @Nullable
  private static String getUserSpecifiedPhName(HtmlTagNode tagNode, ErrorReporter errorReporter) {
    HtmlAttributeNode phNameAttribute = tagNode.getPhNameNode();
    if (phNameAttribute == null) {
      return null;
    }
    String userSpecifiedPhName = getUserSpecifiedPhName(phNameAttribute, errorReporter);
    // Remove it, we don't actually want to render it
    tagNode.removeChild(phNameAttribute);
    // see if there is more than one.
    phNameAttribute = tagNode.getPhNameNode();
    // TODO(lukes): we should probably have a check that no tag contains multiple copies of an
    // attribute since it is disallowed:
    // https://html.spec.whatwg.org/multipage/syntax.html#attributes-2
    if (phNameAttribute != null) {
      errorReporter.report(phNameAttribute.getSourceLocation(), MULTIPLE_PHNAME_ATTRIBUTES);
    }
    return userSpecifiedPhName;
  }

  /** Validates and returns the phname attribute, or {@code null} if there is no phname. */
  @Nullable
  private static String getUserSpecifiedPhName(
      HtmlAttributeNode htmlAttributeNode, ErrorReporter errorReporter) {
    StandaloneNode valueNode = htmlAttributeNode.getChild(1);
    if (valueNode instanceof HtmlAttributeValueNode) {
      HtmlAttributeValueNode attributeValueNode = (HtmlAttributeValueNode) valueNode;
      if (attributeValueNode.numChildren() == 1
          && attributeValueNode.getChild(0) instanceof RawTextNode) {
        String attributeName = ((RawTextNode) attributeValueNode.getChild(0)).getRawText();
        if (BaseUtils.isIdentifier(attributeName)) {
          // delete the attribute
          return attributeName;
        }
      }
    }
    errorReporter.report(valueNode.getSourceLocation(), INVALID_PHNAME_ATTRIBUTE);
    return null;
  }

  private static String getLcTagName(ErrorReporter errorReporter, TagName tagName) {
    // TODO(lukes): consider removing this restriction, it is only necessary due to how we calculate
    // placeholdernames, we should be able to use the placeholder logic for print nodes as a
    // substitute.
    String lcTagName;
    if (!tagName.isStatic()) {
      errorReporter.report(tagName.getTagLocation(), DYNAMIC_TAG_NAME_IN_MSG_BLOCK);
      lcTagName = "error";
    } else {
      lcTagName = tagName.getStaticTagNameAsLowerCase();
    }
    return lcTagName;
  }

  /**
   * Map from lower-case HTML tag name to human-readable placeholder name. For HTML tags not listed
   * here, the base placeholder name should simply be the tag name in all caps.
   */
  private static final ImmutableMap<String, String> LC_TAG_NAME_TO_PLACEHOLDER_NAME_MAP =
      ImmutableMap.<String, String>builder()
          .put("a", "link")
          .put("br", "break")
          .put("b", "bold")
          .put("i", "italic")
          .put("li", "item")
          .put("ol", "ordered_list")
          .put("ul", "unordered_list")
          .put("p", "paragraph")
          .put("img", "image")
          .put("em", "emphasis")
          .build();

  /** The lower-case HTML tag name (includes '/' for end tags). */
  private final String lcTagName;

  /** Whether this HTML tag is self-ending (i.e. ends with "/>") */
  private final boolean isSelfEnding;

  /** Whether this HTML tag only has raw text (i.e. has only a single RawTextNode child). */
  private final boolean isOnlyRawText;

  /** Only applicable when isOnlyRawText is true. The full tag text. */
  @Nullable private final String fullTagText;

  /** The user-supplied placeholder name, or null if not supplied or not applicable. */
  @Nullable private final String userSuppliedPlaceholderName;

  private MsgHtmlTagNode(
      int id,
      SourceLocation sourceLocation,
      String userSuppliedPlaceholderName,
      String lcTagName,
      boolean isSelfEnding,
      boolean isOnlyRawText,
      String fullTagText,
      StandaloneNode child) {
    super(id, sourceLocation);
    this.userSuppliedPlaceholderName = userSuppliedPlaceholderName;
    this.lcTagName = lcTagName;
    this.isSelfEnding = isSelfEnding;
    this.isOnlyRawText = isOnlyRawText;
    this.fullTagText = fullTagText;
    addChild(child);
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  private MsgHtmlTagNode(MsgHtmlTagNode orig, CopyState copyState) {
    super(orig, copyState);
    this.lcTagName = orig.lcTagName;
    this.isSelfEnding = orig.isSelfEnding;
    this.isOnlyRawText = orig.isOnlyRawText;
    this.fullTagText = orig.fullTagText;
    this.userSuppliedPlaceholderName = orig.userSuppliedPlaceholderName;
  }


  @Override public Kind getKind() {
    return Kind.MSG_HTML_TAG_NODE;
  }


  /** Returns the lower-case HTML tag name (includes '/' for end tags). */
  public String getLcTagName() {
    return lcTagName;
  }


  /** Returns tag text used to determine whether two nodes refer to the same placeholder. */
  public @Nullable String getFullTagText() {
    return fullTagText;
  }


  @Nullable
  @Override
  public String getUserSuppliedPhName() {
    return userSuppliedPlaceholderName;
  }

  private static final CharMatcher INVALID_PLACEHOLDER_CHARS =
      CharMatcher.inRange('a', 'z')
          .or(CharMatcher.inRange('A', 'Z'))
          .or(CharMatcher.inRange('0', '9'))
          .or(CharMatcher.is('_'))
          .negate()
          .precomputed();

  @Override
  public String genBasePhName() {

    if (userSuppliedPlaceholderName != null) {
      return BaseUtils.convertToUpperUnderscore(userSuppliedPlaceholderName);
    }

    boolean isEndTag;
    String baseLcTagName;
    if (lcTagName.startsWith("/")) {
      isEndTag = true;
      baseLcTagName = lcTagName.substring(1);
    } else {
      isEndTag = false;
      baseLcTagName = lcTagName;
    }
    String basePlaceholderName =
        LC_TAG_NAME_TO_PLACEHOLDER_NAME_MAP.containsKey(baseLcTagName) ?
            LC_TAG_NAME_TO_PLACEHOLDER_NAME_MAP.get(baseLcTagName) : baseLcTagName;
    if (isEndTag) {
      basePlaceholderName = "end_" + basePlaceholderName;
    } else if (!isSelfEnding) {
      basePlaceholderName = "start_" + basePlaceholderName;
    }
    // placeholders should be limited to just ascii numeric chars (and underscore).  Anything else
    // causes jscompiler errors.
    // TODO(lukes): track down some documentation for these rules and add placeholder validation
    // in more places.
    basePlaceholderName = INVALID_PLACEHOLDER_CHARS.replaceFrom(basePlaceholderName, '_');
    return Ascii.toUpperCase(basePlaceholderName);
  }


  @Override public Object genSamenessKey() {
    // If two MsgHtmlTagNodes are both only raw text, then they are considered the same placeholder
    // if they both have the same user-supplied placeholder name (if any) and the same tag text.
    // If one of the MsgHtmlTagNodes is not only raw text, then the two MsgHtmlTagNodes are never
    // considered the same placeholder (so use the instance as the sameness key)
    return isOnlyRawText ?
        Pair.of(userSuppliedPlaceholderName, fullTagText) : this;
  }


  @Override public String toSourceString() {

    StringBuilder sb = new StringBuilder();

    appendSourceStringForChildren(sb);

    if (userSuppliedPlaceholderName != null) {
      int indexBeforeClose;
      if (isSelfEnding) {
        indexBeforeClose = sb.length() - 2;
        if (! sb.substring(indexBeforeClose).equals("/>")) {
          throw new AssertionError();
        }
      } else {
        indexBeforeClose = sb.length() - 1;
        if (! sb.substring(indexBeforeClose).equals(">")) {
          throw new AssertionError();
        }
      }
      sb.insert(indexBeforeClose, " phname=\"" + userSuppliedPlaceholderName + "\"");
    }

    return sb.toString();
  }


  @Override public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }


  @Override public MsgHtmlTagNode copy(CopyState copyState) {
    return new MsgHtmlTagNode(this, copyState);
  }
}
