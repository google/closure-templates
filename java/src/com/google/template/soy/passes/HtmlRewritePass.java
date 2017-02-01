/*
 * Copyright 2016 Google Inc.
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

import static com.google.common.base.Ascii.toLowerCase;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Multimaps.asMap;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.CharMatcher;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Sets;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ErrorReporter.Checkpoint;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.CssNode;
import com.google.template.soy.soytree.DebuggerNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForeachNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode.Quotes;
import com.google.template.soy.soytree.HtmlCloseTagNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.LogNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TagName;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.XidNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/**
 * Rewrites templates and blocks of {@code kind="html"} or {@code kind="attributes"} to contain
 * {@link HtmlOpenTagNode}, {@link HtmlCloseTagNode}, {@link HtmlAttributeNode}, and {@link
 * HtmlAttributeValueNode}.
 *
 * <p>The strategy is to parse the raw text nodes for html tokens and then trigger AST rewriting
 * based on what we find.
 *
 * <p>An obvious question upon reading this should be "Why isn't this implemented in the grammar?"
 * the answer is that soy has some language features that interfere with writing such a grammar.
 *
 * <ul>
 *   <li>Soy has special commands for manipulating text, notably: {@code {nil}, {sp}, {\n}} cause
 *       difficulties when writing grammar productions for html elements. This would be manageable
 *       by preventing the use of these commands within html tags (though some of them such as
 *       {@code {sp}} are popular so there are some compatibility concerns)
 *   <li>Soy has a {@code {literal}...{/literal}} command. such commands often contain html tags so
 *       within the grammar we would need to start writing grammar production which match the
 *       contents of literal blocks. This is possible but would require duplicating all the lexical
 *       states for literals. (Also we would need to deal with tags split across literal blocks e.g.
 *       {@code <div{literal} a="foo">{/literal}}).
 *   <li>Transitioning between lexical states after seeing a {@code <script>} tag or after parsing a
 *       {@code kind="html"} attributes is complex (And 'semantic lexical transitions' are not
 *       recommended).
 * </ul>
 *
 * <p>On the other hand by implementing this here, a lot of these issues go away since all the text
 * has already been processed. Of course this doesn't mean it is easy since we need to implement our
 * own parser and state tracking system that is normally handled by the javacc grammar.
 *
 * <p>This class tries to be faithful to the <a
 * href="https://www.w3.org/TR/html5/syntax.html#syntax">Html syntax standard</a>. Though we do not
 * attempt to implement the contextual element model, and matching tags is handled by a different
 * pass, the {@link StrictHtmlValidationPass}.
 *
 * <p>TODO(lukes): there are some missing features:
 *
 * <ul>
 *   <li>Remove the parsing of {@link MsgHtmlTagNode} from the parser and move it here
 *   <li>Some of the error messages are spammy (1 error triggers 3-4 reports). We should short
 *       circuit earlier for some errors.
 * </ul>
 */
@VisibleForTesting
public final class HtmlRewritePass extends CompilerFilePass {

  /**
   * If set to true, causes every state transition to be logged to stderr. Useful when debugging.
   */
  private static final boolean DEBUG = false;

  private static final SoyErrorKind BLOCK_CHANGES_CONTEXT =
      SoyErrorKind.of("{0} changes context from ''{1}'' to ''{2}''.{3}");

  private static final SoyErrorKind BLOCK_ENDS_IN_INVALID_STATE =
      SoyErrorKind.of("''{0}'' block ends in an invalid state ''{1}''");

  private static final SoyErrorKind
      CONDITIONAL_BLOCK_ISNT_GUARANTEED_TO_PRODUCE_ONE_ATTRIBUTE_VALUE =
          SoyErrorKind.of(
              "expected exactly one attribute value, the {0} isn''t guaranteed to produce exactly "
                  + "one");

  private static final SoyErrorKind EXPECTED_ATTRIBUTE_VALUE =
      SoyErrorKind.of("expected an attribute value");

  private static final SoyErrorKind EXPECTED_WS_EQ_OR_CLOSE_AFTER_ATTRIBUTE_NAME =
      SoyErrorKind.of("expected whitespace, ''='' or tag close after a attribute name");

  private static final SoyErrorKind EXPECTED_WS_OR_CLOSE_AFTER_TAG_OR_ATTRIBUTE =
      SoyErrorKind.of("expected whitespace or tag close after a tag name or attribute");

  private static final SoyErrorKind FOUND_END_OF_ATTRIBUTE_STARTED_IN_ANOTHER_BLOCK =
      SoyErrorKind.of(
          "found the end of an html attribute that was started in another block. Html attributes "
              + "should be opened and closed in the same block");

  private static final SoyErrorKind FOUND_END_TAG_STARTED_IN_ANOTHER_BLOCK =
      SoyErrorKind.of(
          "found the end of a tag that was started in another block. Html tags should be opened "
              + "and closed in the same block");

  private static final SoyErrorKind GENERIC_UNEXPECTED_CHAR =
      SoyErrorKind.of("unexpected character, expected ''{0}'' instead");

  private static final SoyErrorKind ILLEGAL_HTML_ATTRIBUTE_CHARACTER =
      SoyErrorKind.of("illegal unquoted attribute value character");

  private static final SoyErrorKind INVALID_IDENTIFIER =
      SoyErrorKind.of("invalid html identifier, ''{0}'' is an illegal character");

  private static final SoyErrorKind INVALID_LOCATION_FOR_CONTROL_FLOW =
      SoyErrorKind.of("invalid location for a ''{0}'' node, {1}");

  private static final SoyErrorKind INVALID_LOCATION_FOR_NONPRINTABLE =
      SoyErrorKind.of("invalid location for a non-printable node: {0}");

  private static final SoyErrorKind INVALID_TAG_NAME =
      SoyErrorKind.of(
          "tag names may only be raw text or print nodes, consider extracting a '''{'let...'' "
              + "variable");

  private static final SoyErrorKind SELF_CLOSING_CLOSE_TAG =
      SoyErrorKind.of("close tags should not be self closing");

  private static final SoyErrorKind UNEXPECTED_CLOSE_TAG_CONTENT =
      SoyErrorKind.of("unexpected close tag content, only whitespace is allowed in close tags");

  private static final SoyErrorKind UNEXPECTED_WS_AFTER_LT =
      SoyErrorKind.of("unexpected whitespace after ''<'', did you mean ''&lt;''?");

  /** Represents features of the parser states. */
  private enum StateFeature {
    /** Means the state is part of an html 'tag' of a node. */
    TAG,
    INVALID_END_STATE_FOR_RAW_TEXT,
    INVALID_END_STATE_FOR_BLOCK;
  }

  /**
   * Represents the contexutal state of the parser.
   *
   * <p>NOTE: {@link #reconcile(State)} depends on the ordering. So don't change the order without
   * also inspecting {@link #reconcile(State)}.
   */
  private enum State {
    NONE,
    PCDATA,
    RCDATA_SCRIPT,
    RCDATA_TEXTAREA,
    RCDATA_TITLE,
    RCDATA_STYLE,
    HTML_COMMENT,
    CDATA,
    /**
     * <!doctype, <!element, or <?xml> these work like normal tags but don't require attribute
     * values to be matched with attribute names
     */
    XML_DECLARATION,
    SINGLE_QUOTED_XML_ATTRIBUTE_VALUE,
    DOUBLE_QUOTED_XML_ATTRIBUTE_VALUE,
    HTML_TAG_NAME,
    AFTER_TAG_NAME_OR_ATTRIBUTE(StateFeature.TAG),
    BEFORE_ATTRIBUTE_NAME(StateFeature.TAG),
    /**
     * This state is weird - it is for <code>
     *   <pre>foo ="bar"
     *           ^
     *   </pre>
     * </code>
     *
     * <p>The state right after the attribute name. This is normally not useful (we could transition
     * right to BEFORE_ATTRIBUTE_VALUE by looking ahead for an '=' character, but we need it so we
     * can have dynamic attribute names. e.g. {xid foo}=bar.
     */
    AFTER_ATTRIBUTE_NAME(StateFeature.TAG, StateFeature.INVALID_END_STATE_FOR_RAW_TEXT),
    BEFORE_ATTRIBUTE_VALUE(StateFeature.INVALID_END_STATE_FOR_BLOCK),
    SINGLE_QUOTED_ATTRIBUTE_VALUE,
    DOUBLE_QUOTED_ATTRIBUTE_VALUE,
    /**
     * This state is 'zero-width' it is intended for delaying the creation of attributes across
     * conditional blocks.
     */
    AFTER_QUOTED_ATTRIBUTE_VALUE(StateFeature.TAG),
    UNQUOTED_ATTRIBUTE_VALUE(StateFeature.TAG),
    ;

    /** Gets the {@link State} for the given kind. */
    static State fromKind(@Nullable ContentKind kind) {
      if (kind == null) {
        return NONE;
      }
      switch (kind) {
        case ATTRIBUTES:
          return BEFORE_ATTRIBUTE_NAME;
        case HTML:
          return PCDATA;
        case CSS:
        case JS:
        case TEXT:
        case TRUSTED_RESOURCE_URI:
        case URI:
          return NONE;
        default:
          throw new AssertionError("unhandled kind: " + kind);
      }
    }

    private final ImmutableSet<StateFeature> stateTypes;

    State(StateFeature... stateTypes) {
      EnumSet<StateFeature> set = EnumSet.noneOf(StateFeature.class);
      Collections.addAll(set, stateTypes);
      this.stateTypes = Sets.immutableEnumSet(set);
    }

    /**
     * Given 2 states, return a state that is compatible with both of them. This is useful for
     * calculating states when 2 branches of a conditional don't end in the same state. Returns
     * {@code null} if no such state exists.
     */
    @Nullable
    State reconcile(State that) {
      checkNotNull(that);
      if (that == this) {
        return this;
      }
      if (this.compareTo(that) > 0) {
        return that.reconcile(this);
      }
      // the order of comparisons here depends on the compareTo above to ensure 'this < that'
      if (this == BEFORE_ATTRIBUTE_VALUE
          && (that == AFTER_QUOTED_ATTRIBUTE_VALUE || that == UNQUOTED_ATTRIBUTE_VALUE)) {
        // These aren't exactly compatible, but rather are an allowed transition because
        // 1. before and unquoted attribute and in an unquoted attribute are not that different
        // 2. a complete attribute value is a reasonable thing to constitute a block.  This enables
        //    code like class={if $foo}"bar"{else}"baz"{/if}
        //    and it depends on additional support in the handling of control flow nodes.
        return that;
      }
      if (isTagState() && that.isTagState()) {
        return AFTER_TAG_NAME_OR_ATTRIBUTE;
      }
      switch (this) {
        case NONE:
        case PCDATA:
        case RCDATA_STYLE:
        case RCDATA_TITLE:
        case RCDATA_SCRIPT:
        case RCDATA_TEXTAREA:
        case DOUBLE_QUOTED_ATTRIBUTE_VALUE:
        case SINGLE_QUOTED_ATTRIBUTE_VALUE:
        case BEFORE_ATTRIBUTE_VALUE:
        case HTML_COMMENT:
        case HTML_TAG_NAME:
        case XML_DECLARATION:
        case CDATA:
        case DOUBLE_QUOTED_XML_ATTRIBUTE_VALUE:
        case SINGLE_QUOTED_XML_ATTRIBUTE_VALUE:
        case AFTER_ATTRIBUTE_NAME:
        case UNQUOTED_ATTRIBUTE_VALUE:
        case BEFORE_ATTRIBUTE_NAME:
        case AFTER_TAG_NAME_OR_ATTRIBUTE:
          // these all require exact matches
          return null;
        default:
          throw new AssertionError("unexpected state: " + this);
      }
    }

    boolean isTagState() {
      return stateTypes.contains(StateFeature.TAG);
    }

    boolean isInvalidForEndOfRawText() {
      return stateTypes.contains(StateFeature.INVALID_END_STATE_FOR_RAW_TEXT);
    }

    boolean isInvalidForEndOfBlock() {
      return stateTypes.contains(StateFeature.INVALID_END_STATE_FOR_BLOCK);
    }

    @Override
    public String toString() {
      return Ascii.toLowerCase(name().replace('_', ' '));
    }
  }

  private final ErrorReporter errorReporter;
  private final boolean enabled;

  @VisibleForTesting
  public HtmlRewritePass(ImmutableList<String> experimentalFeatures, ErrorReporter errorReporter) {
    // TODO(lukes): this is currently conditionally enabled for stricthtml to enable testing.
    // Turn it on unconditionally.
    this.enabled = experimentalFeatures.contains("stricthtml");
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    if (enabled) {
      new Visitor(nodeIdGen, file.getFilePath(), errorReporter).exec(file);
    }
  }

  private static final class Visitor extends AbstractSoyNodeVisitor<Void> {
    // https://www.w3.org/TR/REC-xml/#NT-Name

    /** Matches the first character of an xml name. */
    static final CharMatcher NAME_START_CHAR_MATCHER =
        CharMatcher.anyOf(":_")
            .or(CharMatcher.inRange('A', 'Z'))
            .or(CharMatcher.inRange('a', 'z'))
            .or(CharMatcher.inRange('\u00C0', '\u00D6'))
            .or(CharMatcher.inRange('\u00D8', '\u00F6'))
            .or(CharMatcher.inRange('\u00F8', '\u02FF'))
            .or(CharMatcher.inRange('\u0370', '\u037D'))
            .or(CharMatcher.inRange('\u037F', '\u1FFF'))
            .or(CharMatcher.inRange('\u200C', '\u200D'))
            .or(CharMatcher.inRange('\u2070', '\u218F'))
            .or(CharMatcher.inRange('\u2C00', '\u2FEF'))
            .or(CharMatcher.inRange('\u3001', '\uD7FF'))
            .or(CharMatcher.inRange('\uF900', '\uFDCF'))
            .or(CharMatcher.inRange('\uFDF0', '\uFFFD'))
            .precomputed();

    /** Matches a 'non-first' character in an xml name. */
    static final CharMatcher NAME_PART_CHAR_MATCHER =
        CharMatcher.anyOf(".-\u00B7")
            .or(CharMatcher.inRange('0', '9'))
            .or(CharMatcher.inRange('\u0300', '\u036F'))
            .or(CharMatcher.inRange('\u203F', '\u2040'))
            .or(NAME_START_CHAR_MATCHER)
            .precomputed();

    static int indexOfInvalidNameCharacter(String name) {
      if (!NAME_START_CHAR_MATCHER.matches(name.charAt(0))) {
        return 0;
      }
      for (int i = 1; i < name.length(); i++) {
        if (!NAME_PART_CHAR_MATCHER.matches(name.charAt(i))) {
          return i;
        }
      }
      return -1;
    }

    /** Matches invalid characters for unquoted attribute values. */
    static final CharMatcher BAD_UNQUOTED_ATTRIBUTE_CHARACTER_MATCHER =
        CharMatcher.anyOf("\"'`=<> \t\r\n").precomputed();

    /** Matches raw text in a tag that isn't a special character or whitespace. */
    static final CharMatcher TAG_RAW_TEXT_MATCHER =
        CharMatcher.whitespace().or(CharMatcher.anyOf(">='\"/")).negate().precomputed();

    static final CharMatcher NOT_DOUBLE_QUOTE = CharMatcher.isNot('"').precomputed();
    static final CharMatcher NOT_SINGLE_QUOTE = CharMatcher.isNot('\'').precomputed();
    static final CharMatcher NOT_LT = CharMatcher.isNot('<').precomputed();
    static final CharMatcher NOT_RSQUARE_BRACE = CharMatcher.isNot(']').precomputed();
    static final CharMatcher NOT_HYPHEN = CharMatcher.isNot('-').precomputed();
    static final CharMatcher XML_DECLARATION_NON_DELIMITERS =
        CharMatcher.noneOf(">\"'").precomputed();

    final IdGenerator nodeIdGen;
    final String filePath;
    final AstEdits edits = new AstEdits();
    final ErrorReporter errorReporter;

    // RawText handling fields.
    RawTextNode currentRawTextNode;
    String currentRawText;
    int currentRawTextOffset;
    int currentRawTextIndex;

    ParsingContext context;

    Visitor(IdGenerator nodeIdGen, String filePath, ErrorReporter errorReporter) {
      this.nodeIdGen = nodeIdGen;
      this.filePath = filePath;
      this.errorReporter = errorReporter;
    }

    /**
     * For RawText we need to examine every character.
     *
     * <p>We track an index and an offset into the current RawTextNode (currentRawTextIndex and
     * currentRawTextOffset respectively). 'advance' methods move the index and 'consume' methods
     * optionally move the index and always set the offset == index. (they 'consume' the text
     * between the offset and the index.
     *
     * <p>handle* methods will 'handle the current state'
     *
     * <ul>
     *   <li>Precondition : They are in the given state and not at the end of the input
     *   <li> Postcondition: They have either advanced the current index or changed states
     *       (generally both)
     * </ul>
     *
     * <p>NOTE: a consequence of these conditions is that they are only guaranteed to be able to
     * consume a single character.
     *
     * <p>At the end of visiting a raw text node, all the input will be consumed.
     */
    @Override
    protected void visitRawTextNode(RawTextNode node) {
      currentRawTextNode = node;
      currentRawText = node.getRawText();
      currentRawTextOffset = 0;
      currentRawTextIndex = 0;
      while (currentRawTextIndex < currentRawText.length()) {
        int startIndex = currentRawTextIndex;
        State startState = context.getState();
        switch (startState) {
          case NONE:
            // no replacements, no parsing, just jump to the end
            currentRawTextIndex = currentRawTextOffset = currentRawText.length();
            break;
          case PCDATA:
            handlePcData();
            break;
          case DOUBLE_QUOTED_ATTRIBUTE_VALUE:
            handleQuotedAttributeValue(true);
            break;
          case SINGLE_QUOTED_ATTRIBUTE_VALUE:
            handleQuotedAttributeValue(false);
            break;
          case AFTER_QUOTED_ATTRIBUTE_VALUE:
            // TODO(lukes): consider making accessing the currentPoint lazy
            handleAfterQuotedAttributeValue(currentPoint());
            break;
          case BEFORE_ATTRIBUTE_VALUE:
            handleBeforeAttributeValue();
            break;
          case AFTER_TAG_NAME_OR_ATTRIBUTE:
            handleAfterTagNameOrAttribute();
            break;
          case BEFORE_ATTRIBUTE_NAME:
            handleBeforeAttributeName();
            break;
          case UNQUOTED_ATTRIBUTE_VALUE:
            handleUnquotedAttributeValue();
            break;
          case AFTER_ATTRIBUTE_NAME:
            handleAfterAttributeName();
            break;
          case HTML_TAG_NAME:
            handleHtmlTagName();
            break;
          case RCDATA_STYLE:
            handleRcData(TagName.SpecialTagName.STYLE);
            break;
          case RCDATA_TITLE:
            handleRcData(TagName.SpecialTagName.TITLE);
            break;
          case RCDATA_SCRIPT:
            handleRcData(TagName.SpecialTagName.SCRIPT);
            break;
          case RCDATA_TEXTAREA:
            handleRcData(TagName.SpecialTagName.TEXTAREA);
            break;
          case CDATA:
            handleCData();
            break;
          case HTML_COMMENT:
            handleHtmlComment();
            break;
          case XML_DECLARATION:
            handleXmlDeclaration();
            break;
          case DOUBLE_QUOTED_XML_ATTRIBUTE_VALUE:
            handleXmlAttributeQuoted(true);
            break;
          case SINGLE_QUOTED_XML_ATTRIBUTE_VALUE:
            handleXmlAttributeQuoted(false);
            break;
          default:
            throw new UnsupportedOperationException("cant yet handle: " + startState);
        }
        if (context.getState() == startState && startIndex == currentRawTextIndex) {
          // sanity! make sure we are making progress.  Calling handle* should ensure that we
          // advance at least one character or change states.  (generally both will happen, but
          // after producing an error we may only switch states).
          throw new IllegalStateException(
              "failed to make progress in state: "
                  + startState.name()
                  + " at "
                  + currentLocation());
        }
        if (currentRawTextOffset > currentRawTextIndex) {
          throw new IllegalStateException(
              "offset is greater than index! offset: "
                  + currentRawTextOffset
                  + " index: "
                  + currentRawTextIndex);
        }
      }
      if (context.getState().isInvalidForEndOfRawText()) {
        throw new AssertionError("Shouldn't end a raw text block in state: " + context.getState());
      }
      if (currentRawTextIndex != currentRawText.length()) {
        throw new AssertionError("failed to visit all of the raw text");
      }
      if (currentRawTextOffset < currentRawTextIndex && currentRawTextOffset != 0) {
        // This handles all the states that just advance to the end without consuming
        // TODO(lukes): maybe this should be an error and all such states will need to consume?
        RawTextNode suffix = consumeAsRawText();
        edits.replace(node, suffix);
      }
    }

    /**
     * Handle rcdata blocks (script, style, title, textarea).
     *
     * <p>Scans for {@code </tagName} and if it finds it, enters {@link State#PCDATA}.
     */
    void handleRcData(TagName.SpecialTagName tagName) {
      boolean foundLt = advanceWhileMatches(NOT_LT);
      if (foundLt) {
        if (matchPrefixIgnoreCase("</" + tagName, false /* don't advance */)) {
          // we don't advance.  instead we just switch to pcdata and since the current index is on
          // a '<' character, this will cause us to parse a close tag, which is what we want
          context.setState(State.PCDATA, currentPoint());
        } else {
          advance();
        }
      }
    }

    /**
     * Handle cdata.
     *
     * <p>Scans for {@code ]]>} and if it finds it, enters {@link State#PCDATA}.
     */
    void handleCData() {
      boolean foundBrace = advanceWhileMatches(NOT_RSQUARE_BRACE);
      if (foundBrace) {
        if (matchPrefix("]]>", true)) {
          context.setState(State.PCDATA, currentPointOrEnd());
        } else {
          advance();
        }
      }
    }

    /**
     * Handle html comments.
     *
     * <p>Scans for {@code -->} and if it finds it, enters {@link State#PCDATA}.
     */
    void handleHtmlComment() {
      boolean foundHyphen = advanceWhileMatches(NOT_HYPHEN);
      if (foundHyphen) {
        if (matchPrefix("-->", true)) {
          context.setState(State.PCDATA, currentPointOrEnd());
        } else {
          advance();
        }
      }
    }

    /**
     * Handle {@link State#XML_DECLARATION}.
     *
     * <p>This is for things like {@code <!DOCTYPE HTML PUBLIC
     * "http://www.w3.org/TR/html4/strict.dtd">}. . We are looking for the end or a quoted
     * 'attribute'.
     */
    void handleXmlDeclaration() {
      boolean foundDelimiter = advanceWhileMatches(XML_DECLARATION_NON_DELIMITERS);
      if (foundDelimiter) {
        int c = currentChar();
        advance();
        if (c == '"') {
          context.setState(State.DOUBLE_QUOTED_XML_ATTRIBUTE_VALUE, currentPoint());
        } else if (c == '\'') {
          context.setState(State.SINGLE_QUOTED_XML_ATTRIBUTE_VALUE, currentPoint());
        } else if (c == '>') {
          context.setState(State.PCDATA, currentPoint());
        } else {
          throw new AssertionError("unexpected character: " + c);
        }
      }
    }

    /** Handle an xml quoted attribute. We just scan for the appropriate quote character. */
    void handleXmlAttributeQuoted(boolean doubleQuoted) {
      boolean foundQuote = advanceWhileMatches(doubleQuoted ? NOT_DOUBLE_QUOTE : NOT_SINGLE_QUOTE);
      if (foundQuote) {
        advance();
        context.setState(State.XML_DECLARATION, currentPoint());
      }
    }

    /**
     * Handle pcdata.
     *
     * <p>Scan for {@code <} if we find it we know we are at the start of a tag, a comment, cdata or
     * an xml declaration. Create a text node for everything up to the beginning of the tag-like
     * thing.
     */
    void handlePcData() {
      boolean foundLt = advanceWhileMatches(NOT_LT);
      RawTextNode node = consumeAsRawText();
      if (node != null) {
        edits.replace(currentRawTextNode, node);
      }
      // if there is more, then we stopped advancing because we hit a '<' character
      if (foundLt) {
        SourceLocation.Point ltPoint = currentPoint();
        if (matchPrefix("<!--", true)) {
          context.setState(State.HTML_COMMENT, ltPoint);
        } else if (matchPrefixIgnoreCase("<![cdata", true)) {
          context.setState(State.CDATA, ltPoint);
        } else if (matchPrefix("<!", true) || matchPrefix("<?", true)) {
          context.setState(State.XML_DECLARATION, ltPoint);
        } else {
          // if it isn't either of those special cases, enter a tag
          boolean isCloseTag = matchPrefix("</", false);
          context.startTag(currentRawTextNode, isCloseTag, currentPoint());
          advance(); // go past the '<'
          if (isCloseTag) {
            advance(); // go past the '/'
          }
          consume(); // set the offset to the current index
          context.setState(State.HTML_TAG_NAME, ltPoint);
        }
      }
    }

    /**
     * Handle parsing an html tag name.
     *
     * <p>Look for an html identifier and transition to AFTER_ATTRIBUTE_OR_TAG_NAME.
     */
    void handleHtmlTagName() {
      // special case error message handle whitespace following a <, report an error and assume it
      // wasn't the start of a tag.
      if (consumeWhitespace()) {
        errorReporter.report(context.tagStartLocation(), UNEXPECTED_WS_AFTER_LT);
        context.reset();
        context.setState(State.PCDATA, currentPointOrEnd());
        return;
      }
      RawTextNode node = consumeHtmlIdentifier();
      if (node == null) {
        // we must have immediately come across a delimiter like <, =, >, ' or "
        errorReporter.report(currentLocation(), GENERIC_UNEXPECTED_CHAR, "an html tag");
        context.setTagName(
            new TagName(new RawTextNode(nodeIdGen.genId(), "$parse-error$", currentLocation())));
      } else {
        context.setTagName(new TagName(node));
      }
      context.setState(State.AFTER_TAG_NAME_OR_ATTRIBUTE, currentPointOrEnd());
    }

    /**
     * Handle the state immediately after an attribute or tag.
     *
     * <p>Look for either whitespace or the end of the tag.
     */
    void handleAfterTagNameOrAttribute() {
      if (consumeWhitespace()) {
        context.setState(State.BEFORE_ATTRIBUTE_NAME, currentPointOrEnd());
        return;
      }
      if (!tryCreateTagEnd()) {
        errorReporter.report(currentLocation(), EXPECTED_WS_OR_CLOSE_AFTER_TAG_OR_ATTRIBUTE);
        // transition to a new state and try to keep going, note, we don't consume the current
        // character
        context.setState(State.BEFORE_ATTRIBUTE_NAME, currentPoint());
        advance(); // move ahead
      }
    }

    /**
     * Handle the state where we are right before an attribute.
     *
     * <p>This state is kind of confusing, it just means we are in the middle of a tag and are
     * definitely after some whitespace.
     */
    void handleBeforeAttributeName() {
      if (tryCreateTagEnd()) {
        return;
      }
      if (consumeWhitespace()) {
        // if we consumed whitespace, return and keep going.
        // we don't necessarily expect whitespace, but it is ok if there is extra whitespace here
        // this can happen in the case of kind="attributes" blocks which start in this state, or
        // if raw text nodes are split strangely.
        // We have to return in case we hit the end of the raw text.
        return;
      }
      RawTextNode identifier = consumeHtmlIdentifier();
      if (identifier == null) {
        // consumeHtmlIdentifier will have already reported an error
        context.resetAttribute();
        return;
      }
      context.setAttributeName(identifier);
      int current = currentChar();
      // if we have hit the end, complete the attribute since there is no way there could be an
      // '=' sign after this point.
      if (current == -1) {
        context.createAttributeNode();
        context.setState(
            State.AFTER_TAG_NAME_OR_ATTRIBUTE,
            currentRawTextNode.getSourceLocation().getEndPoint());
      } else {
        context.setState(State.AFTER_ATTRIBUTE_NAME, currentPoint());
      }
    }

    /**
     * Handle immediately after an attribute name.
     *
     * <p>Look for an '=' sign to signal the presense of an attribute value
     */
    void handleAfterAttributeName() {
      boolean ws = consumeWhitespace();
      int current = currentChar();
      if (current == '=') {
        context.setEqualsSignLocation(currentPoint());
        advance();
        consume(); // eat the '='
        consumeWhitespace();
        context.setState(State.BEFORE_ATTRIBUTE_VALUE, currentPointOrEnd());
      } else {
        // we must have seen some non '=' character (or the end of the text), it doesn't matter
        // what it is, finish the attribute.
        context.createAttributeNode();
        if (ws) {
          context.setState(State.BEFORE_ATTRIBUTE_NAME, currentPointOrEnd());
        } else {
          context.setState(State.AFTER_TAG_NAME_OR_ATTRIBUTE, currentPointOrEnd());
        }
      }
    }

    /**
     * Handle immediately before an attribute value.
     *
     * <p>Look for a quote character to signal the beginning of a quoted attribute value or switch
     * to UNQUOTED_ATTRIBUTE_VALUE to handle that.
     */
    void handleBeforeAttributeValue() {
      int c = currentChar();
      if (c == '\'' || c == '"') {
        SourceLocation.Point quotePoint = currentPoint();
        context.startQuotedAttributeValue(currentRawTextNode, quotePoint);
        advance();
        consume();
        context.setState(
            c == '"' ? State.DOUBLE_QUOTED_ATTRIBUTE_VALUE : State.SINGLE_QUOTED_ATTRIBUTE_VALUE,
            quotePoint);
      } else {
        context.setState(State.UNQUOTED_ATTRIBUTE_VALUE, currentPoint());
      }
    }

    /**
     * Handle unquoted attribute values.
     *
     * <p>Search for whitespace or the end of the tag as a delimiter.
     */
    void handleUnquotedAttributeValue() {
      boolean foundDelimiter = advanceWhileMatches(TAG_RAW_TEXT_MATCHER);
      RawTextNode node = consumeAsRawText();
      if (node != null) {
        int badCharIndex = BAD_UNQUOTED_ATTRIBUTE_CHARACTER_MATCHER.indexIn(node.getRawText());
        if (badCharIndex != -1) {
          errorReporter.report(
              node.substringLocation(badCharIndex, badCharIndex + 1),
              ILLEGAL_HTML_ATTRIBUTE_CHARACTER);
          // keep going
        }
        context.addAttributeValuePart(node);
      }
      if (foundDelimiter) {
        if (context.hasUnquotedAttributeValueParts()) {
          context.createUnquotedAttributeValue();
          if (context.hasAttributePartsWithValue()) {
            context.createAttributeNode();
          } else {
            context.resetAttribute();
            errorReporter.report(
                currentLocation(), FOUND_END_OF_ATTRIBUTE_STARTED_IN_ANOTHER_BLOCK);
          }
        } else {
          context.resetAttribute();
          errorReporter.report(currentLocation(), EXPECTED_ATTRIBUTE_VALUE);
        }
        context.setState(State.AFTER_TAG_NAME_OR_ATTRIBUTE, currentPoint());
      }
      // otherwise keep going, to support things like
      //  <div a=a{$p}>
      //  <div a={$p}a>
      //  <div a=a{$p}a>
    }

    /**
     * Handle a quoted attribute value.
     *
     * <p>These are easy we just look for the end quote.
     */
    void handleQuotedAttributeValue(boolean doubleQuoted) {
      boolean hasQuote = advanceWhileMatches(doubleQuoted ? NOT_DOUBLE_QUOTE : NOT_SINGLE_QUOTE);
      RawTextNode data = consumeAsRawText();
      if (data != null) {
        context.addAttributeValuePart(data);
      }
      if (hasQuote) {
        if (context.hasQuotedAttributeValueParts()) {
          context.createQuotedAttributeValue(currentRawTextNode, doubleQuoted, currentPoint());
          context.setState(State.AFTER_QUOTED_ATTRIBUTE_VALUE, currentPoint());
        } else {
          errorReporter.report(currentLocation(), FOUND_END_OF_ATTRIBUTE_STARTED_IN_ANOTHER_BLOCK);
          context.resetAttribute();
          context.setState(State.BEFORE_ATTRIBUTE_NAME, currentPoint());
        }
        // consume the quote
        advance();
        consume();
      }
    }

    /**
     * Handle the state immediately after a quoted attribute value.
     *
     * <p>This is a special state that exists to support constructs like {@code <div style={if
     * $p}"foo"{else}"bar"{/if}>}. What we are trying to do is decide whether or not to complete the
     * current attribute, by separating it into a separate state from {@link
     * #handleQuotedAttributeValue(boolean)} we can delay it until after a block completes and thus
     * collect attribute values from multiple branches.
     *
     * <p>NOTE: unlike all the other handle methods, this one is called both from visitRawTextNode
     * and some other methods. So it should avoid accessing any of the raw text node fields.
     */
    void handleAfterQuotedAttributeValue(SourceLocation.Point currentPoint) {
      // we don't actually care what the current character is!
      if (context.hasAttributePartsWithValue()) {
        context.createAttributeNode();
      } else {
        errorReporter.report(
            currentPoint.asLocation(filePath), FOUND_END_OF_ATTRIBUTE_STARTED_IN_ANOTHER_BLOCK);
        context.resetAttribute();
      }
      // TODO(lukes): consider switching to BEFORE_ATTRIBUTE_NAME and pretending we saw some
      // whitespace. Lots of user agents support things like <div a="b"c="d"> just fine, and since
      // it isn't really ambiguous we could easily support it too.
      context.setState(State.AFTER_TAG_NAME_OR_ATTRIBUTE, currentPoint);
    }

    /** Attempts to finish the current tag, returns true if it did. */
    boolean tryCreateTagEnd() {
      int c = currentChar();
      if (c == '>') {
        if (context.hasTagStart()) {
          SourceLocation.Point point = currentPoint();
          context.setState(context.createTag(currentRawTextNode, false, point), point);
        } else {
          context.reset();
          errorReporter.report(currentLocation(), FOUND_END_TAG_STARTED_IN_ANOTHER_BLOCK);
        }
        advance();
        consume();
        return true;
      } else if (matchPrefix("/>", false)) {
        // position the index on the '>' so that the end location of the tag calculated by
        // currentPoint is accurate
        advance();
        if (context.hasTagStart()) {
          SourceLocation.Point point = currentPoint();
          context.setState(context.createTag(currentRawTextNode, true, point), point);
        } else {
          context.reset();
          errorReporter.report(currentLocation(), FOUND_END_TAG_STARTED_IN_ANOTHER_BLOCK);
        }
        // consume the rest of the '/>'
        advance();
        consume();
        return true;
      }
      return false;
    }

    /** Returns {@code true} if we haven't reached the end of the string. */
    boolean advanceWhileMatches(CharMatcher c) {
      int next = currentChar();
      while (next != -1 && c.matches((char) next)) {
        advance();
        next = currentChar();
      }
      return next != -1;
    }

    /**
     * Eats all whitespace from the input prefix. Returns {@code true} if we matched any whitespace
     * characters.
     */
    boolean consumeWhitespace() {
      int startIndex = currentRawTextIndex;
      advanceWhileMatches(CharMatcher.whitespace());
      consume();
      return currentRawTextIndex != startIndex;
    }

    /**
     * Scans until the next whitespace, > or />, validates that the matched text is an html
     * identifier and returns it.
     */
    @Nullable
    RawTextNode consumeHtmlIdentifier() {
      // rather than use a regex to match the prefix, we just consume all non-whitespace/non-meta
      // characters and then validate the text afterwards.
      advanceWhileMatches(TAG_RAW_TEXT_MATCHER);
      RawTextNode node = consumeAsRawText();
      if (node != null) {
        int invalidChar = indexOfInvalidNameCharacter(node.getRawText());
        if (invalidChar != -1) {
          errorReporter.report(
              node.substringLocation(invalidChar, invalidChar + 1),
              INVALID_IDENTIFIER,
              node.getRawText().charAt(invalidChar));
        }
      } else {
        errorReporter.report(currentLocation(), GENERIC_UNEXPECTED_CHAR, "an html identifier");
        // consume the character
        advance();
        consume();
      }
      return node;
    }

    /**
     * Returns [{@link #currentRawTextOffset}, {@link #currentRawTextIndex}) as a RawTextNode, or
     * {@code null} if it is empty.
     */
    @Nullable
    RawTextNode consumeAsRawText() {
      if (currentRawTextIndex == currentRawTextOffset) {
        return null;
      }
      edits.remove(currentRawTextNode);
      RawTextNode node =
          currentRawTextNode.substring(
              nodeIdGen.genId(), currentRawTextOffset, currentRawTextIndex);
      consume();
      return node;
    }

    /** Returns the location of the current character. */
    SourceLocation currentLocation() {
      return currentRawTextNode.substringLocation(currentRawTextIndex, currentRawTextIndex + 1);
    }

    /** The {@code SourceLocation.Point} of the {@code currentRawTextIndex}. */
    SourceLocation.Point currentPoint() {
      return currentRawTextNode.locationOf(currentRawTextIndex);
    }

    /**
     * The {@code SourceLocation.Point} of the {@code currentRawTextIndex} or the end of the raw
     * text if we are at the end.
     */
    SourceLocation.Point currentPointOrEnd() {
      if (currentRawText.length() > currentRawTextIndex) {
        return currentPoint();
      }
      return currentRawTextNode.getSourceLocation().getEndPoint();
    }

    /** Returns the current character or {@code -1} if we are at the end of the output. */
    int currentChar() {
      if (currentRawTextIndex < currentRawText.length()) {
        return currentRawText.charAt(currentRawTextIndex);
      }
      return -1;
    }

    /** Advances the {@link #currentRawTextIndex} by {@code n} */
    void advance(int n) {
      checkArgument(n > 0);
      for (int i = 0; i < n; i++) {
        advance();
      }
    }
    /** Advances the {@link #currentRawTextIndex} by {@code 1} */
    void advance() {
      if (currentRawTextIndex >= currentRawText.length()) {
        throw new AssertionError("already advanced to the end, shouldn't advance any more");
      }
      currentRawTextIndex++;
    }

    /** Sets the {@link #currentRawTextOffset} to be equal to {@link #currentRawTextIndex}. */
    void consume() {
      currentRawTextOffset = currentRawTextIndex;
    }

    /**
     * Returns true if the beginning of the input matches the given prefix.
     *
     * @param advance if the prefix matches, advance the length of the prefix.
     */
    boolean matchPrefix(String prefix, boolean advance) {
      if (currentRawText.startsWith(prefix, currentRawTextIndex)) {
        if (advance) {
          advance(prefix.length());
        }
        return true;
      }
      return false;
    }

    /**
     * Returns true if the beginning of the input matches the given prefix ignoring ASCII case.
     *
     * @param advance if the prefix matches, advance the length of the prefix.
     */
    boolean matchPrefixIgnoreCase(String s, boolean advance) {
      if (currentRawTextIndex + s.length() <= currentRawText.length()) {
        // we use an explicit loop instead of Ascii.equalsIgnoringCase + substring to avoid the
        // allocations implied by substring
        for (int i = 0; i < s.length(); i++) {
          char c1 = s.charAt(i);
          char c2 = currentRawText.charAt(i + currentRawTextIndex);
          if (c1 != c2 && toLowerCase(c1) != toLowerCase(c2)) {
            return false;
          }
        }
        if (advance) {
          advance(s.length());
        }
        return true;
      }
      return false;
    }

    // scoped blocks, each one of these can enter/exit a new state
    @Override
    protected void visitTemplateNode(TemplateNode node) {
      // reset everything for each template
      edits.clear();
      context = null;

      Checkpoint checkPoint = errorReporter.checkpoint();
      visitScopedBlock(node.getContentKind(), node, "template");

      // we only rewrite the template if there were no new errors while parsing it
      if (!errorReporter.errorsSince(checkPoint)) {
        edits.apply();
      }
    }

    @Override
    protected void visitLetValueNode(LetValueNode node) {
      processNonPrintableNode(node);
    }

    @Override
    protected void visitLetContentNode(LetContentNode node) {
      visitScopedBlock(node.getContentKind(), node, "let");
      processNonPrintableNode(node);
    }

    @Override
    protected void visitDebuggerNode(DebuggerNode node) {
      processNonPrintableNode(node);
    }

    @Override
    protected void visitCallParamContentNode(CallParamContentNode node) {
      visitScopedBlock(node.getContentKind(), node, "param");
    }

    @Override
    protected void visitCallParamValueNode(CallParamValueNode node) {
      // do nothing
    }

    @Override
    protected void visitCallNode(CallNode node) {
      visitChildren(node);
      processPrintableNode(node);
      if (context.getState() == State.PCDATA) {
        node.setIsPcData(true);
      }
    }

    @Override
    protected void visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
      // TODO(lukes): Start parsing html tags in {msg} nodes here instead of in the parser.
      // to avoid doing it now, we don't visit children
      processPrintableNode(node);
    }

    // control flow blocks

    @Override
    protected void visitForNode(ForNode node) {
      visitControlFlowStructure(
          node,
          ImmutableList.<BlockNode>of(node),
          "for loop",
          Functions.constant("loop body"),
          false /* no guarantee that the children will only execute once. */);
    }

    @Override
    protected void visitForeachNode(ForeachNode node) {
      visitControlFlowStructure(
          node,
          node.getChildren(),
          "foreach loop",
          new Function<BlockNode, String>() {
            @Override
            public String apply(@Nullable BlockNode input) {
              if (input instanceof ForeachNonemptyNode) {
                return "loop body";
              }
              return "ifempty block";
            }
          },
          false /* no guarantee that the children will only execute once. */);
    }

    @Override
    protected void visitIfNode(final IfNode node) {
      visitControlFlowStructure(
          node,
          node.getChildren(),
          "if",
          new Function<BlockNode, String>() {
            @Override
            public String apply(@Nullable BlockNode input) {
              if (input instanceof IfCondNode) {
                if (node.getChild(0) == input) {
                  return "if block";
                }
                return "elseif block";
              }
              return "else block";
            }
          },
          // at least one child will execute if we have an else
          Iterables.getLast(node.getChildren()) instanceof IfElseNode);
    }

    @Override
    protected void visitSwitchNode(SwitchNode node) {
      visitControlFlowStructure(
          node,
          node.getChildren(),
          "switch",
          new Function<BlockNode, String>() {
            @Override
            public String apply(@Nullable BlockNode input) {
              if (input instanceof SwitchCaseNode) {
                return "case block";
              }
              return "default block";
            }
          },
          // at least one child will execute if we have an default case
          Iterables.getLast(node.getChildren()) instanceof SwitchDefaultNode);
    }

    @Override
    protected void visitLogNode(LogNode node) {
      // we don't need to create a new context, just set the state to NONE there is no transition
      // from NONE to anything else.
      State oldState = context.setState(State.NONE, node.getSourceLocation().getBeginPoint());
      visitChildren(node);
      context.setState(oldState, node.getSourceLocation().getEndPoint());
      processNonPrintableNode(node);
    }

    @Override
    protected void visitCssNode(CssNode node) {
      processPrintableNode(node);
    }

    @Override
    protected void visitPrintNode(PrintNode node) {
      processPrintableNode(node);
      // no need to visit children. The only children are PrintDirectiveNodes which are more like
      // expressions than soy nodes.
    }

    @Override
    protected void visitXidNode(XidNode node) {
      processPrintableNode(node);
    }

    void processNonPrintableNode(StandaloneNode node) {
      switch (context.getState()) {
        case AFTER_QUOTED_ATTRIBUTE_VALUE:
          handleAfterQuotedAttributeValue(node.getSourceLocation().getBeginPoint());
          // fall-through
        case AFTER_TAG_NAME_OR_ATTRIBUTE:
        case BEFORE_ATTRIBUTE_NAME:
          context.addTagChild(node);
          break;
        case AFTER_ATTRIBUTE_NAME:
          context.createAttributeNode();
          context.addTagChild(node);
          context.setState(
              State.AFTER_TAG_NAME_OR_ATTRIBUTE, node.getSourceLocation().getEndPoint());
          break;
        case BEFORE_ATTRIBUTE_VALUE:
          errorReporter.report(
              node.getSourceLocation(),
              INVALID_LOCATION_FOR_NONPRINTABLE,
              "move it before the start of the tag or after the tag name");
          break;
        case HTML_TAG_NAME:
          errorReporter.report(
              node.getSourceLocation(),
              INVALID_LOCATION_FOR_NONPRINTABLE,
              "it creates ambiguity with an unquoted attribute value");
          break;
        case UNQUOTED_ATTRIBUTE_VALUE:
        case DOUBLE_QUOTED_ATTRIBUTE_VALUE:
        case SINGLE_QUOTED_ATTRIBUTE_VALUE:
          context.addAttributeValuePart(node);
          break;
        case HTML_COMMENT:
        case NONE:
        case PCDATA:
        case RCDATA_SCRIPT:
        case RCDATA_STYLE:
        case RCDATA_TEXTAREA:
        case RCDATA_TITLE:
        case XML_DECLARATION:
        case CDATA:
        case DOUBLE_QUOTED_XML_ATTRIBUTE_VALUE:
        case SINGLE_QUOTED_XML_ATTRIBUTE_VALUE:
          // do nothing
          break;
        default:
          throw new AssertionError();
      }
    }

    /**
     * Checks whether the given control flow node is in a valid state.
     *
     * <p>This should be called prior to visiting the children, to decide whether or not to recurse.
     */
    @CheckReturnValue
    boolean processControlFlowNode(StandaloneNode node, String nodeName) {
      switch (context.getState()) {
        case AFTER_QUOTED_ATTRIBUTE_VALUE:
          handleAfterQuotedAttributeValue(node.getSourceLocation().getBeginPoint());
          // fall-through
        case AFTER_TAG_NAME_OR_ATTRIBUTE:
        case BEFORE_ATTRIBUTE_NAME:
          context.addTagChild(node);
          return true;
        case AFTER_ATTRIBUTE_NAME:
          context.createAttributeNode();
          context.addTagChild(node);
          context.setState(
              State.AFTER_TAG_NAME_OR_ATTRIBUTE, node.getSourceLocation().getEndPoint());
          return true;
        case HTML_TAG_NAME:
          errorReporter.report(
              node.getSourceLocation(),
              INVALID_LOCATION_FOR_CONTROL_FLOW,
              nodeName,
              "html tag names can only be constants or print nodes");
          // give up on parsing this tag :(
          context.reset();
          context.setState(State.PCDATA, node.getSourceLocation().getBeginPoint());
          return false;
        case BEFORE_ATTRIBUTE_VALUE:
        case UNQUOTED_ATTRIBUTE_VALUE:
        case DOUBLE_QUOTED_ATTRIBUTE_VALUE:
        case SINGLE_QUOTED_ATTRIBUTE_VALUE:
          context.addAttributeValuePart(node);
          return true;
        case HTML_COMMENT:
        case NONE:
        case PCDATA:
        case RCDATA_SCRIPT:
        case RCDATA_STYLE:
        case RCDATA_TEXTAREA:
        case RCDATA_TITLE:
        case XML_DECLARATION:
        case CDATA:
        case DOUBLE_QUOTED_XML_ATTRIBUTE_VALUE:
        case SINGLE_QUOTED_XML_ATTRIBUTE_VALUE:
          // do nothing
          return true;

        default:
          throw new AssertionError("unexpected control flow exit state: " + context.getState());
      }
    }

    /** Printable nodes are things like {xid..} and {print ..}. */
    void processPrintableNode(StandaloneNode node) {
      checkState(node.getKind() != Kind.RAW_TEXT_NODE);
      switch (context.getState()) {
        case AFTER_QUOTED_ATTRIBUTE_VALUE:
          // finish the pending attribute
          handleAfterQuotedAttributeValue(node.getSourceLocation().getBeginPoint());

          context.setAttributeName(node);
          context.setState(State.AFTER_ATTRIBUTE_NAME, node.getSourceLocation().getEndPoint());
          break;

        case AFTER_TAG_NAME_OR_ATTRIBUTE:
          errorReporter.report(
              node.getSourceLocation(), EXPECTED_WS_OR_CLOSE_AFTER_TAG_OR_ATTRIBUTE);
          break;

        case AFTER_ATTRIBUTE_NAME:
          errorReporter.report(
              node.getSourceLocation(), EXPECTED_WS_EQ_OR_CLOSE_AFTER_ATTRIBUTE_NAME);
          break;

        case BEFORE_ATTRIBUTE_NAME:
          context.setAttributeName(node);
          context.setState(State.AFTER_ATTRIBUTE_NAME, node.getSourceLocation().getEndPoint());
          break;

        case HTML_TAG_NAME:
          if (node.getKind() == Kind.PRINT_NODE) {
            context.setTagName(new TagName((PrintNode) node));
            edits.remove(node);
            context.setState(
                State.AFTER_TAG_NAME_OR_ATTRIBUTE, node.getSourceLocation().getEndPoint());
          } else {
            errorReporter.report(node.getSourceLocation(), INVALID_TAG_NAME);
          }
          break;

        case BEFORE_ATTRIBUTE_VALUE:
          // we didn't see a quote, so just turn this into an attribute value.
          context.setState(
              State.UNQUOTED_ATTRIBUTE_VALUE, node.getSourceLocation().getBeginPoint());
          // fall through
        case DOUBLE_QUOTED_ATTRIBUTE_VALUE:
        case SINGLE_QUOTED_ATTRIBUTE_VALUE:
        case UNQUOTED_ATTRIBUTE_VALUE:
          context.addAttributeValuePart(node);
          break;
        case HTML_COMMENT:
        case NONE:
        case PCDATA:
        case RCDATA_SCRIPT:
        case RCDATA_STYLE:
        case RCDATA_TEXTAREA:
        case RCDATA_TITLE:
        case XML_DECLARATION:
        case CDATA:
        case DOUBLE_QUOTED_XML_ATTRIBUTE_VALUE:
        case SINGLE_QUOTED_XML_ATTRIBUTE_VALUE:
          // do nothing
          break;

        default:
          throw new AssertionError("unexpected state: " + context.getState());
      }
    }

    @Override
    protected void visitSoyFileNode(SoyFileNode node) {
      visitChildren(node);
    }

    @Override
    protected void visitSoyNode(SoyNode node) {
      throw new UnsupportedOperationException(node.getKind() + " isn't supported yet");
    }

    /** Visits a block whose content is in an entirely separate content scope. */
    void visitScopedBlock(ContentKind blockKind, BlockNode parent, String name) {
      State startState = State.fromKind(blockKind);
      Checkpoint checkpoint = errorReporter.checkpoint();
      ParsingContext newCtx =
          newParsingContext(startState, parent.getSourceLocation().getBeginPoint());
      ParsingContext oldCtx = setContext(newCtx);
      visitBlock(startState, parent, name, checkpoint);
      setContext(oldCtx); // restore
    }

    /**
     * Visits a control flow structure like an if, switch or a loop.
     *
     * <p>The main thing this is responsible for is calculating what state to enter after the
     * control flow is complete.
     *
     * @param parent The parent node, each child will be a block representing one of the branches
     * @param overallName The name, for error reporting purposes, to assign to the control flow
     *     structure
     * @param blockNamer A function to provide a name for each child block, the key is the index of
     *     the block
     * @param willExactlyOneBranchExecuteOnce Whether or not it is guaranteed that exactly one
     *     branch of the structure will execute exactly one time.
     */
    void visitControlFlowStructure(
        StandaloneNode parent,
        List<? extends BlockNode> children,
        String overallName,
        Function<? super BlockNode, String> blockNamer,
        boolean willExactlyOneBranchExecuteOnce) {
      if (!processControlFlowNode(parent, overallName)) {
        // this means that the node is in a bad location and we reported an error, don't visit
        // children and just keep going
        return;
      }

      // this insane case can happen with SwitchNodes.
      if (children.isEmpty()) {
        return;
      }

      Checkpoint checkpoint = errorReporter.checkpoint();
      State startState = context.getState();
      State endingState = null;
      for (BlockNode block : children) {
        ParsingContext newCtx =
            newParsingContext(startState, block.getSourceLocation().getBeginPoint());
        ParsingContext oldCtx = setContext(newCtx);
        endingState = visitBlock(startState, block, blockNamer.apply(block), checkpoint);
        setContext(oldCtx); // restore
      }

      if (!errorReporter.errorsSince(checkpoint)) {
        if (startState == State.BEFORE_ATTRIBUTE_VALUE
            && endingState == State.AFTER_QUOTED_ATTRIBUTE_VALUE) {
          // This special case exists to support constructs like
          // <div class={if $p}"foo"{else}"bar"{/if}>
          //
          // If we were starting from scratch we might require the quotation marks to go outside the
          // if, but this is difficult now since this pattern is quite popular.
          // To handle it correctly we need to know that
          if (!willExactlyOneBranchExecuteOnce) {
            errorReporter.report(
                parent.getSourceLocation(),
                CONDITIONAL_BLOCK_ISNT_GUARANTEED_TO_PRODUCE_ONE_ATTRIBUTE_VALUE,
                overallName);
          } else {
            // Since we started in BEFORE_ATTRIBUTE_VALUE. the whole control flow node will have
            // been added as an attributeValue in context. But since we finished in
            // AFTER_QUOTED_ATTRIBUTE_VALUE we know that this isn't actually a 'part' but rather the
            // whole thing.  Just move it into attribute parts.
            // TODO(lukes): This seems arbitrary, maybe we should delay the logic in
            // processControlFlowNode until after visiting all branches?
            context.setAttributeValue(Iterables.getOnlyElement(context.attributeValueChildren));
            context.attributeValueChildren.clear();
            context.setState(endingState, parent.getSourceLocation().getEndPoint());
          }
        } else {
          context.setState(endingState, parent.getSourceLocation().getEndPoint());
        }
      }
    }

    /** Visits a block and returns the finalState. */
    State visitBlock(State startState, BlockNode node, String blockName, Checkpoint checkpoint) {
      visitChildren(node);
      State finalState = context.getState();
      SourceLocation.Point finalStateTransitionPoint = context.getStateTransitionPoint();
      if (finalState.isInvalidForEndOfBlock()) {
        errorReporter.report(
            node.getSourceLocation(), BLOCK_ENDS_IN_INVALID_STATE, blockName, finalState);
        finalState = startState;
      }
      if (!errorReporter.errorsSince(checkpoint)) {
        // we need to handle a few special cases involving attribute values at the end of blocks.
        // consider:
        // 1. {if $p}class=foo{if} -> finish the attribute value and attribute
        // 2. class={if $p}foo{else}bar{/if} -> do nothing, stay in unquoted attribute
        // 3. class=x{if $p}foo{else}bar{/if} -> do nothing, stay in unquoted attribute
        // 4. {if $p}class="foo"{if} -> complete the attribute, go to after attribute or tag name
        // 5. class={if $p}"foo"{else}"bar"{/if} -> do nothing, stay in after quoted attribute
        // value
        if (startState != finalState
            && (finalState == State.AFTER_QUOTED_ATTRIBUTE_VALUE
                || finalState == State.UNQUOTED_ATTRIBUTE_VALUE)) {
          if (finalState == State.AFTER_QUOTED_ATTRIBUTE_VALUE) {
            if (context.hasAttributePartsWithValue()) {
              context.createAttributeNode();
              finalState = State.AFTER_TAG_NAME_OR_ATTRIBUTE;
            }
          } else if (context.hasAttributePartsForValue()) {
            context.createUnquotedAttributeValue();
            context.createAttributeNode();
            finalState = State.AFTER_TAG_NAME_OR_ATTRIBUTE;
          }
        }
        State reconciled = startState.reconcile(finalState);
        if (reconciled == null) {
          String suggestion = reconciliationFailureHint(startState, finalState);
          errorReporter.report(
              finalStateTransitionPoint.asLocation(filePath),
              BLOCK_CHANGES_CONTEXT,
              blockName,
              startState,
              finalState,
              suggestion != null ? " " + suggestion : "");
        } else {
          finalState = reconciled;
          reparentNodes(node, context, finalState);
        }
      } else {
        // an error occured, restore the start state to help avoid an error explosion
        finalState = startState;
      }
      context.setState(finalState, node.getSourceLocation().getEndPoint());
      return finalState;
    }

    /**
     * After visiting a block, this will transfer all the partial values in the blockCtx to the
     * parent.
     *
     * @param parent The block node
     * @param blockCtx The context after visiting the block
     * @param finalState The reconciled state after visiting the block
     */
    static void reparentNodes(BlockNode parent, ParsingContext blockCtx, State finalState) {
      // if there were no errors we may need to conditionally add new children, this only really
      // applies to attributes which may be partially finished (to allow for things like
      // foo=a{if $x}b{/if}
      switch (finalState) {
        case BEFORE_ATTRIBUTE_NAME:
        case AFTER_TAG_NAME_OR_ATTRIBUTE:
        case AFTER_ATTRIBUTE_NAME:
          blockCtx.reparentDirectTagChildren(parent);
          break;
        case AFTER_QUOTED_ATTRIBUTE_VALUE:
          blockCtx.reparentAttributeParts(parent);
          break;
        case UNQUOTED_ATTRIBUTE_VALUE:
        case DOUBLE_QUOTED_ATTRIBUTE_VALUE:
        case SINGLE_QUOTED_ATTRIBUTE_VALUE:
          blockCtx.reparentAttributeValueChildren(parent);
          break;
        case HTML_COMMENT:
        case NONE:
        case PCDATA:
        case RCDATA_SCRIPT:
        case RCDATA_STYLE:
        case RCDATA_TEXTAREA:
        case RCDATA_TITLE:
        case XML_DECLARATION:
        case CDATA:
        case DOUBLE_QUOTED_XML_ATTRIBUTE_VALUE:
        case SINGLE_QUOTED_XML_ATTRIBUTE_VALUE:
          // do nothing.. there should be nothing in context
          break;
        case BEFORE_ATTRIBUTE_VALUE:
        case HTML_TAG_NAME:
          // impossible?
        default:
          throw new AssertionError(
              "found non-empty context for unexpected state: " + blockCtx.getState());
      }
      blockCtx.checkEmpty("context not fully reparented after '%s'", finalState);
    }

    /** Gives a hint when we fail to reconcile to states. */
    static String reconciliationFailureHint(State startState, State finalState) {
      switch (finalState) {
        case PCDATA: // we could suggest for this one based on the start state maybe?
          return null; // no suggestion
        case BEFORE_ATTRIBUTE_VALUE:
          return "Expected an attribute value before the end of the block";
        case CDATA:
          return didYouForgetToCloseThe("CDATA section");
        case SINGLE_QUOTED_ATTRIBUTE_VALUE:
        case DOUBLE_QUOTED_ATTRIBUTE_VALUE:
        case DOUBLE_QUOTED_XML_ATTRIBUTE_VALUE:
        case SINGLE_QUOTED_XML_ATTRIBUTE_VALUE:
          return didYouForgetToCloseThe("attribute value");
        case HTML_COMMENT:
          return didYouForgetToCloseThe("html comment");
        case RCDATA_SCRIPT:
          return didYouForgetToCloseThe("<script> block");
        case RCDATA_STYLE:
          return didYouForgetToCloseThe("<style> block");
        case RCDATA_TEXTAREA:
          return didYouForgetToCloseThe("<textare> block");
        case RCDATA_TITLE:
          return didYouForgetToCloseThe("<title> block");
        case HTML_TAG_NAME: // kind of crazy
        case AFTER_ATTRIBUTE_NAME:
        case AFTER_TAG_NAME_OR_ATTRIBUTE:
        case BEFORE_ATTRIBUTE_NAME:
        case XML_DECLARATION:
        case UNQUOTED_ATTRIBUTE_VALUE:
        case AFTER_QUOTED_ATTRIBUTE_VALUE:
          // if this wasn't reconciled, it means they probably forgot to close the tag
          if (startState == State.PCDATA) {
            return "Did you forget to close the tag?";
          }
          return null;
        case NONE: // should be impossible, there are no transitions into NONE from non-NONE
        default:
          throw new AssertionError("unexpected final state: " + finalState);
      }
    }

    static String didYouForgetToCloseThe(String thing) {
      return "Did you forget to close the " + thing + "?";
    }

    ParsingContext setContext(ParsingContext ctx) {
      ParsingContext old = context;
      context = ctx;
      return old;
    }

    /** @param state The state to start the context in. */
    ParsingContext newParsingContext(State state, SourceLocation.Point startPoint) {
      return new ParsingContext(state, startPoint, filePath, edits, errorReporter, nodeIdGen);
    }
  }

  /**
   * A class to record all the edits to the AST we need to make.
   *
   * <p>Instead of editing the AST as we go, we record the edits and wait until the end, this makes
   * a few things easier.
   *
   * <ul>
   *   <li>we don't have to worry about editing nodes while visiting them
   *   <li>we can easily avoid making any edits if errors were recorded.
   *       <p>This is encapsulated in its own class so we can easily pass it around and to provide
   *       some encapsulation.
   * </ul>
   */
  private static final class AstEdits {
    final Set<StandaloneNode> toRemove = new LinkedHashSet<>();
    final ListMultimap<StandaloneNode, StandaloneNode> replacements =
        MultimapBuilder.linkedHashKeys().arrayListValues().build();
    final ListMultimap<BlockNode, StandaloneNode> newChildren =
        MultimapBuilder.linkedHashKeys().arrayListValues().build();

    /** Apply all edits. */
    void apply() {
      for (StandaloneNode nodeToRemove : toRemove) {
        BlockNode parent = nodeToRemove.getParent();
        List<StandaloneNode> children = replacements.get(nodeToRemove);
        if (!children.isEmpty()) {
          parent.addChildren(parent.getChildIndex(nodeToRemove), children);
        }
        parent.removeChild(nodeToRemove);
      }
      for (Map.Entry<BlockNode, List<StandaloneNode>> entry : asMap(newChildren).entrySet()) {
        entry.getKey().addChildren(entry.getValue());
      }
      clear();
    }

    /** Mark a node for removal. */
    void remove(StandaloneNode node) {
      checkNotNull(node);
      // only record this if the node is actually in the tree already.  Sometimes we call remove
      // on new nodes that don't have parents yet.
      if (node.getParent() != null) {
        toRemove.add(node);
      }
    }

    /** Add children to the given parent. */
    void addChildren(BlockNode parent, Iterable<StandaloneNode> children) {
      checkNotNull(parent);
      newChildren.putAll(parent, children);
    }

    /** Adds the child to the given parent. */
    void addChild(BlockNode parent, StandaloneNode child) {
      checkNotNull(parent);
      checkNotNull(child);
      newChildren.put(parent, child);
    }

    /** Replace a given node with the new nodes. */
    void replace(StandaloneNode oldNode, Iterable<StandaloneNode> newNodes) {
      checkState(oldNode.getParent() != null, "oldNode must be in the tree in order to replace it");
      remove(oldNode);
      replacements.putAll(oldNode, newNodes);
    }

    /** Replace a given node with the new node. */
    void replace(StandaloneNode oldNode, StandaloneNode newNode) {
      replace(oldNode, ImmutableList.of(newNode));
    }

    /** Clear all the edits. */
    void clear() {
      toRemove.clear();
      replacements.clear();
      newChildren.clear();
    }

  }

  /**
   * Parsing context records the current {@link State} as well as all the extra information needed
   * to produce our new nodes.
   *
   * <p>This is extracted into a separate class so we can create new ones when visiting branches.
   *
   * <p>This isn't a perfect abstraction. It is set up to parse full HTML tags, but sometimes we
   * only want to parse attributes or attribute values (or parts of attribute values). In theory, we
   * could split it into 3-4 classes one for each case, but I'm not sure it simplifies things over
   * the current solution. See {@link Visitor#reparentNodes} for how we handle those cases.
   */
  private static final class ParsingContext {
    final String filePath;
    final IdGenerator nodeIdGen;
    final ErrorReporter errorReporter;
    final AstEdits edits;

    // The current parser state.
    State state;
    SourceLocation.Point stateTransitionPoint;

    // for tracking the current tag being built

    /** Whether the current tag is a close tag. e.g. {@code </div>} */
    boolean isCloseTag;

    SourceLocation.Point tagStartPoint;
    RawTextNode tagStartNode;
    TagName tagName;

    // TODO(lukes): consider lazily allocating these lists.
    /** All the 'direct' children of the current tag. */
    final List<StandaloneNode> directTagChildren = new ArrayList<>();

    // for tracking the current attribute being built

    /** All the parts of the attribute. */
    StandaloneNode attributeName;

    SourceLocation.Point equalsSignLocation;
    StandaloneNode attributeValue;

    // For the attribute value,  where the quoted attribute value started

    /** Where the open quote of a quoted attribute value starts. */
    SourceLocation.Point quotedAttributeValueStart;

    /** all the direct children of the attribute value. */
    final List<StandaloneNode> attributeValueChildren = new ArrayList<>();

    ParsingContext(
        State startingState,
        SourceLocation.Point startPoint,
        String filePath,
        AstEdits edits,
        ErrorReporter errorReporter,
        IdGenerator nodeIdGen) {
      this.state = checkNotNull(startingState);
      this.stateTransitionPoint = checkNotNull(startPoint);
      this.filePath = checkNotNull(filePath);
      this.nodeIdGen = checkNotNull(nodeIdGen);
      this.edits = checkNotNull(edits);
      this.errorReporter = checkNotNull(errorReporter);
    }

    /** Attaches the attributeValueChildren to the parent. */
    void reparentAttributeValueChildren(BlockNode parent) {
      edits.addChildren(parent, attributeValueChildren);
      attributeValueChildren.clear();
    }

    /** Attaches the directTagChildren to the parent. */
    void reparentDirectTagChildren(BlockNode parent) {
      edits.addChildren(parent, directTagChildren);
      directTagChildren.clear();
    }

    void reparentAttributeParts(BlockNode parent) {
      edits.addChild(parent, checkNotNull(attributeValue));
      attributeValue = null;
    }

    /** Returns true if this has accumulated parts of an unquoted attribute value. */
    boolean hasUnquotedAttributeValueParts() {
      return quotedAttributeValueStart == null && !attributeValueChildren.isEmpty();
    }

    /** Returns true if this has accumulated parts of a quoted attribute value. */
    boolean hasQuotedAttributeValueParts() {
      return quotedAttributeValueStart != null;
    }

    /** Returns true if this contains an attribute name and an equals sign but no value yet. */
    boolean hasAttributePartsForValue() {
      return attributeName != null && equalsSignLocation != null;
    }

    /** Returns true if this context contains all the parts of a completed attribute with a value */
    boolean hasAttributePartsWithValue() {
      return attributeName != null && attributeValue != null;
    }

    boolean hasTagStart() {
      return tagStartNode != null && tagStartPoint != null;
    }

    /** Sets the given node as a direct child of the tag currently being built. */
    void addTagChild(StandaloneNode node) {
      checkNotNull(node);
      directTagChildren.add(node);
      edits.remove(node);
    }

    /** Asserts that the context is empty. */
    void checkEmpty(String fmt, Object... args) {
      StringBuilder error = null;

      if (!directTagChildren.isEmpty()) {
        error = format(error, "Expected directTagChildren to be empty, got: %s", directTagChildren);
      }
      if (attributeName != null) {
        error = format(error, "Expected attributeName to be null, got: %s", attributeName);
      }
      if (attributeValue != null) {
        error = format(error, "Expected attributeValue to be null, got: %s", attributeValue);
      }
      if (!attributeValueChildren.isEmpty()) {
        error =
            format(
                error,
                "Expected attributeValueChildren to be empty, got: %s",
                attributeValueChildren);
      }
      if (tagStartPoint != null) {
        error = format(error, "Expected tagStartPoint to be null, got: %s", tagStartPoint);
      }
      if (tagName != null) {
        error = format(error, "Expected tagName to be null, got: %s", tagName);
      }
      if (tagStartNode != null) {
        error = format(error, "Expected tagStartNode to be null, got: %s", tagStartNode);
      }
      if (quotedAttributeValueStart != null) {
        error =
            format(
                error,
                "Expected quotedAttributeValueStart to be null, got: %s",
                quotedAttributeValueStart);
      }
      if (tagName != null) {
        error = format(error, "Expected tagName to be null, got: %s", tagName);
      }
      if (error != null) {
        throw new IllegalStateException(String.format(fmt + "\n", args) + error);
      }
    }

    private static StringBuilder format(StringBuilder error, String fmt, Object... args) {
      if (error == null) {
        error = new StringBuilder();
      }
      error.append(String.format(fmt, args));
      error.append('\n');
      return error;
    }

    /** Resets parsing state, this is useful for error recovery. */
    void reset() {
      tagStartPoint = null;
      tagStartNode = null;
      tagName = null;
      directTagChildren.clear();
      resetAttribute();
      resetAttributeValue();
    }

    void resetAttribute() {
      attributeName = null;
      equalsSignLocation = null;
      attributeValue = null;
    }

    void resetAttributeValue() {
      quotedAttributeValueStart = null;
      attributeValueChildren.clear();
    }

    /**
     * Records the start of an html tag
     *
     * @param tagStartNode The node where it started
     * @param isCloseTag is is a close tag
     * @param point the source location of the {@code <} character.
     */
    void startTag(RawTextNode tagStartNode, boolean isCloseTag, SourceLocation.Point point) {
      checkState(this.tagStartPoint == null);
      checkState(this.tagStartNode == null);
      checkState(this.directTagChildren.isEmpty());
      this.tagStartPoint = checkNotNull(point);
      this.tagStartNode = checkNotNull(tagStartNode);
      this.isCloseTag = isCloseTag;
    }

    /** Returns the tag start location, for error reporting. */
    SourceLocation tagStartLocation() {
      return tagStartPoint.asLocation(filePath);
    }

    /** Sets the tag name of the tag currently being built. */
    void setTagName(TagName tagName) {
      checkState(tagStartPoint != null);
      this.tagName = checkNotNull(tagName);
    }
    
    void setAttributeName(StandaloneNode node) {
      checkNotNull(node);
      checkState(attributeName == null);
      edits.remove(node);
      attributeName = node;
    }

    void setEqualsSignLocation(SourceLocation.Point location) {
      checkNotNull(location);
      checkState(equalsSignLocation == null);
      equalsSignLocation = location;
    }

    void setAttributeValue(StandaloneNode node) {
      checkNotNull(node);
      checkState(attributeValue == null);
      edits.remove(node);
      attributeValue = node;
    }

    /**
     * Records the start of a quoted attribute value.
     *
     * @param node The node where it started
     * @param point The source location where it started.
     */
    void startQuotedAttributeValue(RawTextNode node, SourceLocation.Point point) {
      checkState(quotedAttributeValueStart == null);
      checkState(attributeValueChildren.isEmpty());
      edits.remove(node);
      quotedAttributeValueStart = checkNotNull(point);
    }

    /** Adds a new attribute value part and marks the node for removal. */
    void addAttributeValuePart(StandaloneNode node) {
      attributeValueChildren.add(node);
      edits.remove(node);
    }

    /** Completes the unquoted attribute value. */
    void createUnquotedAttributeValue() {
      HtmlAttributeValueNode valueNode =
          new HtmlAttributeValueNode(
              nodeIdGen.genId(), getLocationOf(attributeValueChildren), Quotes.NONE);
      edits.addChildren(valueNode, attributeValueChildren);
      attributeValueChildren.clear();
      setAttributeValue(valueNode);
    }

    /** Completes a quoted attribute value. */
    void createQuotedAttributeValue(
        RawTextNode end, boolean doubleQuoted, SourceLocation.Point endPoint) {
      HtmlAttributeValueNode valueNode =
          new HtmlAttributeValueNode(
              nodeIdGen.genId(),
              new SourceLocation(filePath, quotedAttributeValueStart, endPoint),
              doubleQuoted ? Quotes.DOUBLE : Quotes.SINGLE);
      edits.remove(end);
      edits.addChildren(valueNode, attributeValueChildren);
      attributeValueChildren.clear();
      quotedAttributeValueStart = null;
      setAttributeValue(valueNode);
    }

    /** Creates an {@link HtmlAttributeNode}. */
    void createAttributeNode() {
      SourceLocation location = attributeName.getSourceLocation();
      HtmlAttributeNode attribute;
      if (attributeValue != null) {
        attribute =
            new HtmlAttributeNode(nodeIdGen.genId(), location, checkNotNull(equalsSignLocation));
        location = location.extend(attributeValue.getSourceLocation());
        edits.addChild(attribute, attributeName);
        edits.addChild(attribute, attributeValue);
      } else {
        attribute = new HtmlAttributeNode(nodeIdGen.genId(), location, null);
        edits.addChild(attribute, attributeName);
      }
      attributeName = null;
      equalsSignLocation = null;
      attributeValue = null;
      directTagChildren.add(attribute);
    }

    /**
     * Creates an HtmlOpenTagNode or an HtmlCloseTagNode
     *
     * @param tagEndNode The node where the tag ends
     * @param selfClosing Whether it is self closing, e.g. {@code <div />} is self closing
     * @param endPoint The point where the {@code >} character is.
     * @return The state to transition into, typically this is {@link State#PCDATA} but could be one
     *     of the rcdata states for special tags.
     */
    State createTag(RawTextNode tagEndNode, boolean selfClosing, SourceLocation.Point endPoint) {
      BlockNode replacement;
      SourceLocation sourceLocation = new SourceLocation(filePath, tagStartPoint, endPoint);
      if (isCloseTag) {
        // TODO(lukes): move the error reporting into the caller?
        if (!directTagChildren.isEmpty()) {
          errorReporter.report(
              directTagChildren.get(0).getSourceLocation(), UNEXPECTED_CLOSE_TAG_CONTENT);
        }
        if (selfClosing) {
          errorReporter.report(
              endPoint.asLocation(filePath).offsetStartCol(-1), SELF_CLOSING_CLOSE_TAG);
        }
        replacement = new HtmlCloseTagNode(nodeIdGen.genId(), tagName, sourceLocation);
      } else {
        replacement = new HtmlOpenTagNode(nodeIdGen.genId(), tagName, sourceLocation, selfClosing);
      }
      // Depending on the tag name, we may need to enter a special state after the tag.
      State nextState = State.PCDATA;
      if (!selfClosing && !isCloseTag) {
        TagName.SpecialTagName specialTag = tagName.getSpecialTagName();
        if (specialTag != null) {
          switch (specialTag) {
            case SCRIPT:
              nextState = State.RCDATA_SCRIPT;
              break;
            case STYLE:
              nextState = State.RCDATA_STYLE;
              break;
            case TEXTAREA:
              nextState = State.RCDATA_TEXTAREA;
              break;
            case TITLE:
              nextState = State.RCDATA_TITLE;
              break;
            default:
              throw new AssertionError(specialTag);
          }
        }
      }
      edits.remove(tagEndNode);
      edits.addChildren(replacement, directTagChildren);
      // cast is safe because Html(Open|Close)TagNode implement BlockNode and StandaloneNode
      edits.replace(tagStartNode, (StandaloneNode) replacement);
      directTagChildren.clear();
      tagStartPoint = null;
      tagName = null;
      tagStartNode = null;
      checkEmpty("Expected state to be empty after completing a tag");
      return nextState;
    }

    /**
     * Changes the state of this context.
     *
     * @param s the new state
     * @param point the current location where the transition ocurred.
     * @return the previous state
     */
    State setState(State s, SourceLocation.Point point) {
      State old = state;
      state = checkNotNull(s);
      stateTransitionPoint = checkNotNull(point);
      if (DEBUG) {
        System.err.println(
            point.asLocation(filePath) + "\tState: " + s.name() + " errors: " + errorReporter);
      }
      return old;
    }

    State getState() {
      checkState(state != null);
      return state;
    }

    SourceLocation.Point getStateTransitionPoint() {
      checkState(stateTransitionPoint != null);
      return stateTransitionPoint;
    }

    static SourceLocation getLocationOf(List<StandaloneNode> nodes) {
      SourceLocation location = nodes.get(0).getSourceLocation();
      if (nodes.size() > 1) {
        location = location.extend(Iterables.getLast(nodes).getSourceLocation());
      }
      return location;
    }
  }
}
