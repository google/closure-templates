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

import static com.google.template.soy.base.internal.BaseUtils.convertToUpperUnderscore;
import static com.google.template.soy.soytree.MessagePlaceholder.PHEX_ATTR;
import static com.google.template.soy.soytree.MessagePlaceholder.PHNAME_ATTR;
import static com.google.template.soy.soytree.MessagePlaceholder.validatePlaceholderExample;
import static com.google.template.soy.soytree.MessagePlaceholder.validatePlaceholderName;

import com.google.auto.value.AutoValue;
import com.google.common.base.Ascii;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprEquivalence;
import com.google.template.soy.soytree.SoyNode.MsgPlaceholderInitialNode;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Node representing an HTML tag within a {@code msg} statement/block.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class MsgHtmlTagNode extends AbstractBlockNode implements MsgPlaceholderInitialNode {

  private static final SoyErrorKind DYNAMIC_TAG_NAME_IN_MSG_BLOCK =
      SoyErrorKind.of("HTML tags within within ''msg'' blocks must use constant tag names.");
  private static final SoyErrorKind INVALID_ATTRIBUTE =
      SoyErrorKind.of("''{0}'' attribute is not a constant.");

  /**
   * Creates a {@link MsgHtmlTagNode} from a {@link HtmlTagNode}.
   *
   * <p>If the node contains a {@code phname} attribute, it will be <em>removed</em> from the node
   * and used as the placeholder name, it will _not_ be rendered.
   */
  public static MsgHtmlTagNode fromNode(
      int id, HtmlTagNode tagNode, @Nullable VeLogNode velogParent, ErrorReporter errorReporter) {
    RawTextNode phExampleNode = getAttributeValue(tagNode, PHEX_ATTR, errorReporter);
    Optional<String> phExample =
        Optional.ofNullable(
            (phExampleNode == null)
                ? null
                : validatePlaceholderExample(
                    phExampleNode.getRawText(), phExampleNode.getSourceLocation(), errorReporter));
    RawTextNode userSuppliedPhNameNode = getAttributeValue(tagNode, PHNAME_ATTR, errorReporter);

    // calculate after removing the attributes, since we don't care about example and the phname is
    // part of the samenesskey
    String fullTagText = getFullTagText(tagNode);
    String lcTagName = getLcTagName(errorReporter, tagNode.getTagName());
    boolean isSelfEnding = false;
    if (tagNode instanceof HtmlCloseTagNode) {
      // TODO(lukes): the lcTagName logic below requires this leading '/' for close tags.  Just make
      // it understand the node type instead.
      lcTagName = "/" + lcTagName;
    } else if (tagNode instanceof HtmlOpenTagNode) {
      isSelfEnding = ((HtmlOpenTagNode) tagNode).isSelfClosing();
    }

    // Include the velog node sameness key if we are the open or close tag node of the velog.
    // close tag nodes don't really need the sameness key given our implementations.
    VeLogNode.SamenessKey key = velogParent != null ? velogParent.getSamenessKey() : null;
    MessagePlaceholder placeholder = null;
    if (userSuppliedPhNameNode != null) {
      SourceLocation phNameLocation = userSuppliedPhNameNode.getSourceLocation();
      String phName =
          validatePlaceholderName(
              userSuppliedPhNameNode.getRawText(), phNameLocation, errorReporter);
      if (phName != null) {
        placeholder =
            MessagePlaceholder.createWithUserSuppliedName(
                convertToUpperUnderscore(phName), phName, phNameLocation, phExample);
      }
    }
    if (placeholder == null) {
      placeholder = MessagePlaceholder.create(genBasePhName(lcTagName, isSelfEnding), phExample);
    }
    return new MsgHtmlTagNode(
        id,
        tagNode.getSourceLocation(),
        placeholder,
        lcTagName,
        isSelfEnding,
        fullTagText != null ? SamenessKeyImpl.create(placeholder.name(), fullTagText, key) : null,
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
    return SoyTreeUtils.allNodes(openTagNode)
            .anyMatch(
                node ->
                    !(node instanceof RawTextNode
                        || node instanceof HtmlAttributeNode
                        || node instanceof HtmlAttributeValueNode
                        || node instanceof HtmlOpenTagNode
                        || node instanceof HtmlCloseTagNode))
        ? null
        // toSourceString is lame, but how this worked before
        : openTagNode.toSourceString();
  }

  /**
   * Returns the {@code RawTextNode} of the given attribute and removes it from the tag if it
   * exists, otherwise returns {@code null}.
   *
   * @param tagNode The owning tag
   * @param name The attribute name
   * @param errorReporter The error reporter
   */
  @Nullable
  private static RawTextNode getAttributeValue(
      HtmlTagNode tagNode, String name, ErrorReporter errorReporter) {
    HtmlAttributeNode attribute = tagNode.getDirectAttributeNamed(name);
    if (attribute == null) {
      return null;
    }
    RawTextNode value = getAttributeValue(attribute, name, errorReporter);
    // Remove it, we don't actually want to render it
    tagNode.removeChild(attribute);
    return value;
  }

  /** Validates and returns the given attribute, or {@code null} if it doesn't exist. */
  @Nullable
  private static RawTextNode getAttributeValue(
      HtmlAttributeNode htmlAttributeNode, String name, ErrorReporter errorReporter) {
    StandaloneNode valueNode = htmlAttributeNode.getChild(1);
    if (valueNode instanceof HtmlAttributeValueNode) {
      HtmlAttributeValueNode attributeValueNode = (HtmlAttributeValueNode) valueNode;
      if (attributeValueNode.numChildren() == 1
          && attributeValueNode.getChild(0) instanceof RawTextNode) {
        return (RawTextNode) attributeValueNode.getChild(0);
      }
    }
    errorReporter.report(valueNode.getSourceLocation(), INVALID_ATTRIBUTE, name);
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

  @Nullable private final SamenessKey samenessKey;

  private final MessagePlaceholder placeholder;

  private MsgHtmlTagNode(
      int id,
      SourceLocation sourceLocation,
      MessagePlaceholder placeholder,
      String lcTagName,
      boolean isSelfEnding,
      @Nullable SamenessKey samenessKey,
      HtmlTagNode child) {
    super(id, sourceLocation);
    this.placeholder = placeholder;
    this.lcTagName = lcTagName;
    this.isSelfEnding = isSelfEnding;
    this.samenessKey = samenessKey;
    addChild(child);
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private MsgHtmlTagNode(MsgHtmlTagNode orig, CopyState copyState) {
    super(orig, copyState);
    this.lcTagName = orig.lcTagName;
    this.isSelfEnding = orig.isSelfEnding;
    this.samenessKey = orig.samenessKey != null ? orig.samenessKey.copy(copyState) : null;
    this.placeholder = orig.placeholder;
    // we may have handed out a copy to ourselves via genSamenessKey()
    copyState.updateRefs(orig, this);
  }

  @Override
  public Kind getKind() {
    return Kind.MSG_HTML_TAG_NODE;
  }

  /** Returns the lower-case HTML tag name (includes '/' for end tags). */
  public String getLcTagName() {
    return lcTagName;
  }

  @Override
  public MessagePlaceholder getPlaceholder() {
    return placeholder;
  }

  private static final CharMatcher INVALID_PLACEHOLDER_CHARS =
      CharMatcher.inRange('a', 'z')
          .or(CharMatcher.inRange('A', 'Z'))
          .or(CharMatcher.inRange('0', '9'))
          .or(CharMatcher.is('_'))
          .negate()
          .precomputed();

  private static String genBasePhName(String lcTagName, boolean isSelfEnding) {
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
        LC_TAG_NAME_TO_PLACEHOLDER_NAME_MAP.getOrDefault(baseLcTagName, baseLcTagName);
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

  @Override
  public SamenessKey genSamenessKey() {
    return samenessKey == null ? new IdentitySamenessKey(this) : samenessKey;
  }

  @AutoValue
  abstract static class SamenessKeyImpl implements SamenessKey {
    static SamenessKeyImpl create(
        String userSuppliedPlaceholderName, String fullTagText, VeLogNode.SamenessKey key) {
      if (userSuppliedPlaceholderName == null && fullTagText == null && key == null) {
        throw new IllegalArgumentException("at least one parameter should be nonnull");
      }
      return new AutoValue_MsgHtmlTagNode_SamenessKeyImpl(
          userSuppliedPlaceholderName, fullTagText, key);
    }

    @Override
    public SamenessKeyImpl copy(CopyState copyState) {
      return create(
          userSuppliedPlaceholderName(),
          fullTagText(),
          logKey() == null ? null : logKey().copy(copyState));
    }

    // at least one of these is nonnull
    @Nullable
    abstract String userSuppliedPlaceholderName();

    @Nullable
    abstract String fullTagText();

    @Nullable
    abstract VeLogNode.SamenessKey logKey();
  }

  @Override
  public String toSourceString() {

    StringBuilder sb = new StringBuilder();

    appendSourceStringForChildren(sb);
    int indexBeforeClose;
    if (isSelfEnding) {
      indexBeforeClose = sb.length() - 2;
      if (!sb.substring(indexBeforeClose).equals("/>")) {
        throw new AssertionError();
      }
    } else {
      indexBeforeClose = sb.length() - 1;
      if (!sb.substring(indexBeforeClose).equals(">")) {
        throw new AssertionError();
      }
    }

    placeholder.example().ifPresent(phex -> sb.insert(indexBeforeClose, " phex=\"" + phex + "\""));
    placeholder
        .userSuppliedName()
        .ifPresent(phname -> sb.insert(indexBeforeClose, " phname=\"" + phname + "\""));

    return sb.toString();
  }

  @Override
  public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }

  @Override
  public MsgHtmlTagNode copy(CopyState copyState) {
    return new MsgHtmlTagNode(this, copyState);
  }
}
