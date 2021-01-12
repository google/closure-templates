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

package com.google.template.soy.soyparse;

import static com.google.common.base.Ascii.toLowerCase;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Ascii;
import com.google.common.base.CharMatcher;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.FormatMethod;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ErrorReporter.Checkpoint;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.AstEdits;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.CaseOrDefaultNode;
import com.google.template.soy.soytree.ConstNode;
import com.google.template.soy.soytree.DebuggerNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForNonemptyNode;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode.Quotes;
import com.google.template.soy.soytree.HtmlCloseTagNode;
import com.google.template.soy.soytree.HtmlCommentNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.HtmlTagNode;
import com.google.template.soy.soytree.HtmlTagNode.TagExistence;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.ImportNode;
import com.google.template.soy.soytree.KeyNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.LogNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.MsgPluralNode;
import com.google.template.soy.soytree.MsgSelectNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.RawTextNode.Provenance;
import com.google.template.soy.soytree.SkipNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.SoyNode.MsgBlockNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TagName;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.VeLogNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 *   <li>Soy has special commands for manipulating text, notably: <code> {nil}, {sp}, {\n}</code>
 *       cause difficulties when writing grammar productions for html elements. This would be
 *       manageable by preventing the use of these commands within html tags (though some of them
 *       such as <code> {sp}</code> are popular so there are some compatibility concerns)
 *   <li>Soy has a <code> {literal}...{/literal}</code> command. such commands often contain html
 *       tags so within the grammar we would need to start writing grammar production which match
 *       the contents of literal blocks. This is possible but would require duplicating all the
 *       lexical states for literals.
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
 * pass, the {@link com.google.template.soy.passes.StrictHtmlValidationPass}.
 */
final class HtmlRewriter {

  /**
   * If set to true, causes every state transition to be logged to stderr. Useful when debugging.
   */
  private static final boolean DEBUG = false;

  private static final SoyErrorKind BLOCK_CHANGES_CONTEXT =
      SoyErrorKind.of(
          "{0} changes context from ''{1}'' to ''{2}''.{3}", StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind SPECIAL_TOKEN_NOT_ALLOWED_WITHIN_HTML_TAG =
      SoyErrorKind.of(
          "Special tokens are not allowed inside html tags, except for within quoted attribute"
              + " values.");

  private static final SoyErrorKind BLOCK_ENDS_IN_INVALID_STATE =
      SoyErrorKind.of("''{0}'' block ends in an invalid state ''{1}''.");

  private static final SoyErrorKind BLOCK_TRANSITION_DISALLOWED =
      SoyErrorKind.of("{0} started in ''{1}'', cannot create a {2}.");

  private static final SoyErrorKind
      CONDITIONAL_BLOCK_ISNT_GUARANTEED_TO_PRODUCE_ONE_ATTRIBUTE_VALUE =
          SoyErrorKind.of(
              "Expected exactly one attribute value, the {0} isn''t guaranteed to produce exactly "
                  + "one.");

  private static final SoyErrorKind CONTROL_FLOW_IN_HTML_TAG_NAME =
      SoyErrorKind.of(
          "Invalid location for a ''{0}'' node, html tag names can only be constants or "
              + "print nodes.");

  private static final SoyErrorKind EXPECTED_ATTRIBUTE_NAME =
      SoyErrorKind.of("Expected an attribute name.");

  private static final SoyErrorKind EXPECTED_ATTRIBUTE_VALUE =
      SoyErrorKind.of("Expected an attribute value.");

  private static final SoyErrorKind EXPECTED_TAG_NAME =
      SoyErrorKind.of("Expected an html tag name.");

  private static final SoyErrorKind EXPECTED_WS_EQ_OR_CLOSE_AFTER_ATTRIBUTE_NAME =
      SoyErrorKind.of(
          "Expected whitespace, ''='' or tag close after an attribute name.  If you "
              + "are trying to create an attribute from a mix of text and print nodes, try moving "
              + "it all inside the print node. For example, instead of ''data-'{$foo}''' write "
              + "'''{'''data-'' + $foo'}'''.");

  private static final SoyErrorKind EXPECTED_WS_OR_CLOSE_AFTER_TAG_OR_ATTRIBUTE =
      SoyErrorKind.of(
          "Expected whitespace or tag close after a tag name or attribute. If you "
              + "are trying to create an attribute or tag name from a mix of text and print nodes, "
              + "try moving it all inside the print node. For example, instead of ''data-'{$foo}'''"
              + " write '''{'''data-'' + $foo'}'''.");

  private static final SoyErrorKind FOUND_END_OF_ATTRIBUTE_STARTED_IN_ANOTHER_BLOCK =
      SoyErrorKind.of(
          "Found the end of an html attribute that was started in another block. Html attributes "
              + "should be opened and closed in the same block.");

  private static final SoyErrorKind FOUND_END_TAG_STARTED_IN_ANOTHER_BLOCK =
      SoyErrorKind.of(
          "Found the end of a tag that was started in another block. Html tags should be opened "
              + "and closed in the same block.");

  private static final SoyErrorKind FOUND_END_COMMENT_STARTED_IN_ANOTHER_BLOCK =
      SoyErrorKind.of(
          "Found the end of an html comment that was started in another block. Html comments"
              + " should be opened and closed in the same block.");

  private static final SoyErrorKind FOUND_EQ_WITH_ATTRIBUTE_IN_ANOTHER_BLOCK =
      SoyErrorKind.of("Found an ''='' character in a different block than the attribute name.");

  private static final SoyErrorKind HTML_COMMENT_WITHIN_MSG_BLOCK =
      SoyErrorKind.of("Found HTML comment within ''msg'' block. This is not allowed.");

  private static final SoyErrorKind ILLEGAL_HTML_ATTRIBUTE_CHARACTER =
      SoyErrorKind.of("Illegal unquoted attribute value character.");

  private static final SoyErrorKind BAD_TAG_NAME = SoyErrorKind.of("Illegal tag name character.");

  private static final SoyErrorKind BAD_ATTRIBUTE_NAME =
      SoyErrorKind.of("Illegal attribute name character.");

  private static final SoyErrorKind INVALID_LOCATION_FOR_NONPRINTABLE =
      SoyErrorKind.of(
          "Invalid location for a non-printable node: {0}", StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind INVALID_TAG_NAME =
      SoyErrorKind.of(
          "Tag names may only be raw text or print nodes, consider extracting a '''{'let...'' "
              + "variable.");

  private static final SoyErrorKind SELF_CLOSING_CLOSE_TAG =
      SoyErrorKind.of("Close tags should not be self closing.");

  private static final SoyErrorKind SMART_QUOTE_ERROR =
      SoyErrorKind.of(
          "Unexpected smart quote character ''”'',  Did you mean ''\"''?  If this is intentional,"
              + " replace with the HTML entity ''&ldquo;'' or ''&rdquo;''.");

  private static final SoyErrorKind UNEXPECTED_WS_AFTER_LT =
      SoyErrorKind.of("Unexpected whitespace after ''<'', did you mean ''&lt;''?");

  private static final SoyErrorKind UNEXPECTED_CLOSE_TAG =
      SoyErrorKind.of("Unexpected close tag for context-changing tag.");

  private static final SoyErrorKind VELOG_CAN_ONLY_BE_USED_IN_PCDATA =
      SoyErrorKind.of(
          "'{'velog ...'}' commands can only be used in pcdata context.", StyleAllowance.NO_CAPS);

  private static final SoyErrorKind SUSPICIOUS_PARTIAL_END_TAG_IN_RCDATA =
      SoyErrorKind.of("Found suspicious partial end tag inside of a {0} tag.");

  private static final SoyErrorKind UNEXPECTED_LITERAL_BEGIN =
      SoyErrorKind.of("Invalid location for literal start command.");

  private static final SoyErrorKind UNEXPECTED_LITERAL_END =
      SoyErrorKind.of("Invalid location for literal end command.");

  private static final SoyErrorKind UNEXPECTED_LITERAL_SPAN =
      SoyErrorKind.of("Literal blocks may not span multiple attribute values.");

  private static final String DISALLOWED_SCRIPT_SEQUENCE_TEXT =
      "Within script tags, the character sequences ''<script'' and ''<!--'' are disallowed by the"
          + " HTML spec. Consider adding whitespace or backslashes to break up the sequence. See"
          + " https://html.spec.whatwg.org/multipage/scripting.html#restrictions-for-contents-of-script-elements"
          + " for more details.";

  private static final SoyErrorKind DISALLOWED_SCRIPT_SEQUENCE =
      SoyErrorKind.of(DISALLOWED_SCRIPT_SEQUENCE_TEXT);

  private static final SoyErrorKind DISALLOWED_SCRIPT_SEQUENCE_PREFIX =
      SoyErrorKind.of(
          DISALLOWED_SCRIPT_SEQUENCE_TEXT
              + " Prefixes of"
              + " these sequences are also disallowed if they appear at the end of a block of"
              + " text.");

  /** Represents features of the parser states. */
  private enum StateFeature {
    /** Means the state is part of an html 'tag' of a node (but not, inside an attribute value). */
    TAG,
    RCDATA,
    INVALID_END_STATE_FOR_BLOCK,
    /** Means a literal block may start or stop here. */
    LITERAL_ALLOWED,
    /** Means a literal block may start here, in which case it must end before the state changes. */
    LITERAL_STRICT;
  }

  /**
   * Represents the contexutal state of the parser.
   *
   * <p>NOTE: {@link #reconcile(State)} depends on the ordering. So don't change the order without
   * also inspecting {@link #reconcile(State)}.
   */
  private enum State {
    NONE(StateFeature.LITERAL_ALLOWED),
    PCDATA(StateFeature.LITERAL_ALLOWED),
    RCDATA_SCRIPT(StateFeature.RCDATA, StateFeature.LITERAL_ALLOWED),
    RCDATA_TEXTAREA(StateFeature.RCDATA, StateFeature.LITERAL_ALLOWED),
    RCDATA_TITLE(StateFeature.RCDATA, StateFeature.LITERAL_ALLOWED),
    RCDATA_STYLE(StateFeature.RCDATA, StateFeature.LITERAL_ALLOWED),
    RCDATA_XMP(StateFeature.RCDATA, StateFeature.LITERAL_ALLOWED),
    HTML_COMMENT(StateFeature.LITERAL_ALLOWED),
    CDATA(StateFeature.LITERAL_ALLOWED),
    /**
     * <!doctype, <!element, or <?xml> these work like normal tags but don't require attribute
     * values to be matched with attribute names
     */
    XML_DECLARATION,
    SINGLE_QUOTED_XML_ATTRIBUTE_VALUE(StateFeature.LITERAL_STRICT),
    DOUBLE_QUOTED_XML_ATTRIBUTE_VALUE(StateFeature.LITERAL_STRICT),
    HTML_TAG_NAME,
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
    AFTER_ATTRIBUTE_NAME(StateFeature.TAG),
    BEFORE_ATTRIBUTE_VALUE(StateFeature.INVALID_END_STATE_FOR_BLOCK),
    SINGLE_QUOTED_ATTRIBUTE_VALUE(StateFeature.LITERAL_STRICT),
    DOUBLE_QUOTED_ATTRIBUTE_VALUE(StateFeature.LITERAL_STRICT),
    UNQUOTED_ATTRIBUTE_VALUE,
    AFTER_TAG_NAME_OR_ATTRIBUTE(StateFeature.TAG),
    BEFORE_ATTRIBUTE_NAME(StateFeature.TAG),
    ;

    /** Gets the {@link State} for the given kind. */
    static State fromKind(@Nullable SanitizedContentKind kind) {
      if (kind == null) {
        return NONE;
      }
      switch (kind) {
        case ATTRIBUTES:
          return BEFORE_ATTRIBUTE_NAME;
        case HTML:
        case HTML_ELEMENT:
          return PCDATA;
          // You might be thinking that some of these should be RCDATA_STYLE or RCDATA_SCRIPT, but
          // that wouldn't be accurate since rcdata is specific to the context of js on an html page
          // in a script tag.  General js has different limitations and the autoescaper knows how to
          // escape js into rcdata_style
        case CSS:
        case JS:
        case TEXT:
        case TRUSTED_RESOURCE_URI:
        case URI:
          return NONE;
      }
      throw new AssertionError("unhandled kind: " + kind);
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
          && (that == UNQUOTED_ATTRIBUTE_VALUE
              || that == AFTER_TAG_NAME_OR_ATTRIBUTE
              || that == BEFORE_ATTRIBUTE_NAME)) {
        // These aren't exactly compatible, but rather are an allowed transition because
        // 1. before an unquoted attribute value and in an unquoted attribute value are not that
        //   different
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
        case RCDATA_XMP:
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
      }
      throw new AssertionError("unexpected state: " + this);
    }

    boolean isTagState() {
      return stateTypes.contains(StateFeature.TAG);
    }

    boolean isInvalidForEndOfBlock() {
      return stateTypes.contains(StateFeature.INVALID_END_STATE_FOR_BLOCK);
    }

    boolean isRcDataState() {
      return stateTypes.contains(StateFeature.RCDATA);
    }

    boolean isLegalLiteralBegin() {
      return stateTypes.contains(StateFeature.LITERAL_ALLOWED)
          || stateTypes.contains(StateFeature.LITERAL_STRICT);
    }

    boolean isLegalLiteralEnd() {
      return isLegalLiteralBegin();
    }

    boolean requiresFullyNestedLiterals() {
      return stateTypes.contains(StateFeature.LITERAL_STRICT);
    }

    @Override
    public String toString() {
      return Ascii.toLowerCase(name().replace('_', ' '));
    }
  }

  private HtmlRewriter() {}

  /* Rewrites the file to contain html tags*/
  public static void rewrite(SoyFileNode file, IdGenerator nodeIdGen, ErrorReporter errorReporter) {
    new Visitor(nodeIdGen, file.getFilePath(), errorReporter).exec(file);
  }

  private static final class Visitor extends AbstractSoyNodeVisitor<Void> {
    /**
     * Spec: http://www.w3.org/TR/html5/syntax.html#tag-name-state -- however, unlike the spec,
     * which appears to allow arbitrary Unicode chars after the first char, we only parse ASCII
     * identifier tag names.
     */
    static final Pattern TAG_NAME = Pattern.compile("[a-z][a-z0-9:-]*", Pattern.CASE_INSENSITIVE);
    /**
     * Regex for allowed attribute names. Intentionally more restrictive than spec:
     * https://html.spec.whatwg.org/multipage/syntax.html#attribute-name-state Allows {@code
     * data-foo} and other dashed attribute names, but intentionally disallows "--" as an attribute
     * name so that a tag ending after a value-less attribute named "--" cannot be confused with an
     * HTML comment end ("-->"). Also prevents unicode normalized characters. Regular expression is
     * a case insensitive match of any number of whitespace characters followed by a capture group
     * for an attribute name composed of an alphabetic character followed by any number of alpha,
     * numeric, underscore color and dash, ending in alpha, numeric, question or dollar characters.
     */
    static final Pattern ATTRIBUTE_NAME =
        Pattern.compile("[a-z_$_@](?:[a-z0-9_:?$\\\\-]*[a-z0-9?$_])?", Pattern.CASE_INSENSITIVE);

    /**
     * Matches raw text in a tag that isn't a special character or whitespace.
     *
     * <p>This is based on the attribute parsing spec:
     * https://www.w3.org/TR/html5/syntax.html#attributes-0 also see
     * https://www.w3.org/TR/html51/syntax.html#attribute-name-state
     */
    static final CharMatcher TAG_DELIMITER_MATCHER =
        // delimiter characters
        CharMatcher.whitespace()
            .or(CharMatcher.anyOf(">=/"))
            .or(
                new CharMatcher() {
                  @Override
                  public boolean matches(char c) {
                    return Character.getType(c) == Character.CONTROL;
                  }
                })
            .negate()
            .precomputed();

    // see https://www.w3.org/TR/html5/syntax.html#attributes-0
    /** Matches all the characters that are allowed to appear in an unquoted attribute value. */
    static final CharMatcher UNQUOTED_ATTRIBUTE_VALUE_MATCHER =
        CharMatcher.whitespace().or(CharMatcher.anyOf("<>='\"`")).negate().precomputed();
    /** Matches the delimiter characters of an unquoted attribute value. */
    static final CharMatcher UNQUOTED_ATTRIBUTE_VALUE_DELIMITER =
        CharMatcher.whitespace().or(CharMatcher.is('>')).precomputed();

    static final CharMatcher SMART_QUOTE = CharMatcher.anyOf("“”").precomputed();
    static final CharMatcher NOT_DOUBLE_QUOTE = CharMatcher.isNot('"').precomputed();
    static final CharMatcher NOT_DOUBLE_QUOTE_NOR_SMART_QUOTE =
        SMART_QUOTE.negate().and(NOT_DOUBLE_QUOTE).precomputed();
    static final CharMatcher NOT_SINGLE_QUOTE = CharMatcher.isNot('\'').precomputed();
    static final CharMatcher NOT_LT = CharMatcher.isNot('<').precomputed();
    static final CharMatcher NOT_RSQUARE_BRACE = CharMatcher.isNot(']').precomputed();
    static final CharMatcher NOT_HYPHEN = CharMatcher.isNot('-').precomputed();
    static final CharMatcher XML_DECLARATION_NON_DELIMITERS =
        CharMatcher.noneOf(">\"'").precomputed();

    final IdGenerator nodeIdGen;
    final SourceFilePath filePath;
    final AstEdits edits = new AstEdits();
    final ErrorReporter errorReporter;

    // RawText handling fields.
    RawTextNode currentRawTextNode;
    String currentRawText;
    int currentRawTextOffset;
    int currentRawTextIndex;

    ParsingContext context;

    /**
     * Whether or not we are currently (directly) inside of a <code>{msg ...}</code> node.
     * 'directly' means that if we are inside a nested <code>{param ...}{/param}</code> block inside
     * a {@code msg} block this parameter should be false.
     */
    boolean inMsgNode;

    /**
     * @param nodeIdGen The id generator
     * @param filePath The current file path
     * @param errorReporter The error reporter
     */
    Visitor(IdGenerator nodeIdGen, SourceFilePath filePath, ErrorReporter errorReporter) {
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
     *   <li>Postcondition: They have either advanced the current index or changed states (generally
     *       both)
     * </ul>
     *
     * <p>NOTE: a consequence of these conditions is that they are only guaranteed to be able to
     * consume a single character.
     *
     * <p>At the end of visiting a raw text node, all the input will be consumed.
     */
    @Override
    protected void visitRawTextNode(RawTextNode node) {
      maybeThrowNoSpecialTokensAllowedError(node);
      currentRawTextNode = node;
      currentRawText = node.getRawText();
      currentRawTextOffset = 0;
      currentRawTextIndex = 0;
      int prevStartIndex = -1;

      boolean isLiteral = node.getProvenance() == Provenance.LITERAL;
      boolean watchForLiteralStateChange = false;
      State originalState = context.getState();

      if (isLiteral) {
        if (!originalState.isLegalLiteralBegin()) {
          errorReporter.report(node.getSourceLocation(), UNEXPECTED_LITERAL_BEGIN);
        } else if (originalState.requiresFullyNestedLiterals()) {
          // We can start a literal here but we must complete the literal without changing state.
          watchForLiteralStateChange = true;
        }
      }

      while (currentRawTextIndex < currentRawText.length()) {
        int startIndex = currentRawTextIndex;
        // if whitespace was trimmed prior to the current character (e.g. leading whitespace)
        // handle it.
        // However, we should only handle it once, otherwise state transitions which don't consume
        // input may cause the same joined whitespace to be handled multiple times.
        if (startIndex != prevStartIndex && currentRawTextNode.missingWhitespaceAt(startIndex)) {
          handleJoinedWhitespace(currentPoint());
        }
        prevStartIndex = startIndex;
        State startState = context.getState();

        if (watchForLiteralStateChange && originalState != startState) {
          errorReporter.report(node.getSourceLocation(), UNEXPECTED_LITERAL_SPAN);
          watchForLiteralStateChange = false; // Set to false so that only one error is printed.
        }

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
            handleRcData(TagName.RcDataTagName.STYLE);
            break;
          case RCDATA_TITLE:
            handleRcData(TagName.RcDataTagName.TITLE);
            break;
          case RCDATA_XMP:
            handleRcData(TagName.RcDataTagName.XMP);
            break;
          case RCDATA_SCRIPT:
            handleRcData(TagName.RcDataTagName.SCRIPT);
            break;
          case RCDATA_TEXTAREA:
            handleRcData(TagName.RcDataTagName.TEXTAREA);
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
      if (currentRawTextIndex != currentRawText.length()) {
        throw new AssertionError("failed to visit all of the raw text");
      }
      if (isLiteral && !context.getState().isLegalLiteralEnd()) {
        errorReporter.report(node.getSourceLocation(), UNEXPECTED_LITERAL_END);
      }
      if (currentRawTextOffset < currentRawTextIndex && currentRawTextOffset != 0) {
        // This handles all the states that just advance to the end without consuming
        // TODO(lukes): maybe this should be an error and all such states will need to consume?
        RawTextNode suffix = consumeAsRawText();
        edits.replace(node, suffix);
      }
      // handle trailing joined whitespace.
      if (currentRawTextNode.missingWhitespaceAt(currentRawText.length())) {
        handleJoinedWhitespace(currentRawTextNode.getSourceLocation().getEndPoint());
      }
      // empty raw text nodes won't get visited in the loop above, just delete them here.
      // the parser produces empty raw text nodes to track where whitespace is trimmed.  We take
      // advantage of this in the handleJoinedWhitespace method to tell where unquoted attributes
      // end. Keep nodes that represent the "{nil}" command char because the formatter needs to know
      // about them.
      if (currentRawTextNode.isEmpty() && !currentRawTextNode.isNilCommandChar()) {
        edits.remove(currentRawTextNode);
      } else {
        maybeReparentNilNode(node);
      }
    }

    /** Reparents {nil} nodes in states where we've inserted new ast nodes (like HTML_OPEN_TAG). */
    void maybeReparentNilNode(RawTextNode node) {
      if (!node.isNilCommandChar()) {
        return;
      }

      switch (context.getState()) {
        case DOUBLE_QUOTED_ATTRIBUTE_VALUE:
        case SINGLE_QUOTED_ATTRIBUTE_VALUE:
          context.addAttributeValuePart(node);
          break;
        case HTML_COMMENT:
          context.addCommentChild(node);
          break;
        case NONE:
        case PCDATA:
        case BEFORE_ATTRIBUTE_VALUE:
        case AFTER_TAG_NAME_OR_ATTRIBUTE:
        case BEFORE_ATTRIBUTE_NAME:
        case UNQUOTED_ATTRIBUTE_VALUE:
        case AFTER_ATTRIBUTE_NAME:
        case HTML_TAG_NAME:
        case RCDATA_STYLE:
        case RCDATA_TITLE:
        case RCDATA_XMP:
        case RCDATA_SCRIPT:
        case RCDATA_TEXTAREA:
        case CDATA:
        case XML_DECLARATION:
        case DOUBLE_QUOTED_XML_ATTRIBUTE_VALUE:
        case SINGLE_QUOTED_XML_ATTRIBUTE_VALUE:
          break;
      }
    }

    /**
     * Throws a "no special tokens allowed within html tags" error, if we're inside an html tag and
     * NOT within an attribute value.
     */
    private void maybeThrowNoSpecialTokensAllowedError(RawTextNode node) {
      if (!node.isCommandCharacter()) {
        return;
      }

      switch (context.getState()) {
        case AFTER_TAG_NAME_OR_ATTRIBUTE:
        case AFTER_ATTRIBUTE_NAME:
        case BEFORE_ATTRIBUTE_NAME:
        case HTML_TAG_NAME:
        case BEFORE_ATTRIBUTE_VALUE:
        case XML_DECLARATION:
        case UNQUOTED_ATTRIBUTE_VALUE:
          errorReporter.report(node.getSourceLocation(), SPECIAL_TOKEN_NOT_ALLOWED_WITHIN_HTML_TAG);
          break;
        case NONE:
        case DOUBLE_QUOTED_XML_ATTRIBUTE_VALUE:
        case SINGLE_QUOTED_XML_ATTRIBUTE_VALUE:
        case DOUBLE_QUOTED_ATTRIBUTE_VALUE:
        case SINGLE_QUOTED_ATTRIBUTE_VALUE:
        case HTML_COMMENT:
        case PCDATA:
        case CDATA:
        case RCDATA_SCRIPT:
        case RCDATA_STYLE:
        case RCDATA_TEXTAREA:
        case RCDATA_TITLE:
        case RCDATA_XMP:
          return;
      }
    }

    /** Called to handle whitespace that was completely removed from a raw text node. */
    void handleJoinedWhitespace(SourceLocation.Point point) {
      // Whitespace isn't meaningful within a tag, but it does show where users separated things
      // so treat the 'joined' whitespace like real whitespace.
      // TODO(lukes): it would be nice to be able to treat joined whitespace as 'real whitespace'
      // then the normal loops in the handle* methods would just #dealwithit.
      switch (context.getState()) {
        case UNQUOTED_ATTRIBUTE_VALUE:
          context.createUnquotedAttributeValue(point);
          // fall-through
        case AFTER_TAG_NAME_OR_ATTRIBUTE:
          context.setState(State.BEFORE_ATTRIBUTE_NAME, point);
          return;
        case AFTER_ATTRIBUTE_NAME:
          int currentChar = currentChar();
          // We are at the end of the raw text, or it is some character other than whitespace or an
          // equals sign -> BEFORE_ATTRIBUTE_NAME
          if (currentChar == -1
              || (!CharMatcher.whitespace().matches((char) currentChar)
                  && '=' != (char) currentChar)) {
            context.setState(State.BEFORE_ATTRIBUTE_NAME, point);
            return;
          }
          // fall through
        case BEFORE_ATTRIBUTE_VALUE:
        case BEFORE_ATTRIBUTE_NAME:
        case DOUBLE_QUOTED_XML_ATTRIBUTE_VALUE:
        case HTML_TAG_NAME:
        case HTML_COMMENT:
        case CDATA:
        case NONE:
        case SINGLE_QUOTED_ATTRIBUTE_VALUE:
        case DOUBLE_QUOTED_ATTRIBUTE_VALUE:
          // TODO(lukes): line joining and whitespace removal can happen within quoted attribute
          // values... we could take steps to undo it... should we?
        case PCDATA:
        case RCDATA_SCRIPT:
        case RCDATA_STYLE:
        case RCDATA_TEXTAREA:
        case RCDATA_TITLE:
        case RCDATA_XMP:
        case SINGLE_QUOTED_XML_ATTRIBUTE_VALUE:
        case XML_DECLARATION:
          // no op
          return;
      }
      throw new AssertionError();
    }

    /**
     * Handle rcdata blocks (script, style, title, textarea).
     *
     * <p>Scans for {@code </tagName} and if it finds it parses it as a close tag.
     */
    void handleRcData(TagName.RcDataTagName tagName) {
      boolean foundLt = advanceWhileMatches(NOT_LT);
      if (foundLt) {
        String expectedEndTag = "</" + tagName;
        if (matchPrefixIgnoreCase(expectedEndTag, /* advance= */ false)) {
          // pseudo re-enter pcdata so that we trigger the normal logic for starting a tag
          handlePcData();
        } else {
          // <script>var x = "</scrip{$foo}"</script>  should be disallowed.
          if (matchPrefixIgnoreCasePastEnd(expectedEndTag)) {
            errorReporter.report(
                currentLocation().extend(currentRawTextNode.getSourceLocation().getEndLocation()),
                SUSPICIOUS_PARTIAL_END_TAG_IN_RCDATA,
                tagName.toString());
          }
          if (tagName == TagName.RcDataTagName.SCRIPT) {
            // in scripts we also need to watch out for <script and <!-- since the spec requires
            // them to be balanced if present but that is typically non-sensical so we just disallow
            // them.
            // we also need to be concerned about raw text ending in a prefix of one of these tokens
            // so that we don't need to worry about partial sequences being completed in dynamic
            // content.
            // see
            // https://html.spec.whatwg.org/multipage/scripting.html#restrictions-for-contents-of-script-elements
            // TODO(b/144050436): upgrade to error
            if (matchPrefixIgnoreCase("<script", /* advance= */ false)
                || matchPrefixIgnoreCase("<!--", /* advance= */ false)) {
              errorReporter.report(currentLocation(), DISALLOWED_SCRIPT_SEQUENCE);
            } else if (matchPrefixIgnoreCasePastEnd("<script")
                || matchPrefixIgnoreCasePastEnd("<!--")) {
              errorReporter.report(
                  currentLocation().extend(currentRawTextNode.getSourceLocation().getEndLocation()),
                  DISALLOWED_SCRIPT_SEQUENCE_PREFIX);
            }
          }
          advance(); // skip past the '<' character and keep parsing
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
        if (matchPrefix("]]>", /* advance=*/ true)) {
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
        if (matchPrefix("-->", /* advance= */ false)) {
          // consume all raw text preceding the hyphen (or end)
          RawTextNode remainingTextNode = consumeAsRawText();
          if (remainingTextNode != null) {
            context.addCommentChild(remainingTextNode);
          }

          // Consume the suffix here ("-->"), but keep track of the ">" location.
          SourceLocation.Point beginEndBracketLocation = currentPoint();
          advance(2);
          consume();
          SourceLocation.Point endBracketLocation = currentPoint();
          advance(1);
          consume();

          // At this point we haven't remove the current raw text node (which contains -->) yet.
          edits.remove(currentRawTextNode);
          if (context.hasCommentBegin()) {
            context.setState(context.createHtmlComment(endBracketLocation), currentPointOrEnd());
          } else {
            errorReporter.report(
                new SourceLocation(filePath, beginEndBracketLocation, endBracketLocation),
                FOUND_END_COMMENT_STARTED_IN_ANOTHER_BLOCK);
            throw new AbortParsingBlockError();
          }
        } else {
          advance();
        }
      } else {
        RawTextNode remainingTextNode = consumeAsRawText();
        if (remainingTextNode != null) {
          context.addCommentChild(remainingTextNode);
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
        SourceLocation.Point currentPoint = currentPoint();
        advance();
        if (c == '"') {
          context.setState(State.DOUBLE_QUOTED_XML_ATTRIBUTE_VALUE, currentPoint);
        } else if (c == '\'') {
          context.setState(State.SINGLE_QUOTED_XML_ATTRIBUTE_VALUE, currentPoint);
        } else if (c == '>') {
          context.setState(State.PCDATA, currentPoint);
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
        context.setState(State.XML_DECLARATION, currentPointOrEnd());
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
          // TODO(lukes): we should probably ban CDATA and XML_DECLARATION within msg blocks as well
          if (inMsgNode) {
            errorReporter.report(
                ltPoint.asLocation(filePath).offsetEndCol(4), HTML_COMMENT_WITHIN_MSG_BLOCK);
          }
          // Consume the prefix "<!--" here.
          // We have already advance {@link #currentRawTextIndex} in matchPrefix.
          consume();
          context.startComment(currentRawTextNode, ltPoint);
        } else if (matchPrefixIgnoreCase("<![cdata", true)) {
          context.setState(State.CDATA, ltPoint);
        } else if (matchPrefix("<!", true) || matchPrefix("<?", true)) {
          context.setState(State.XML_DECLARATION, ltPoint);
        } else {
          // if it isn't either of those special cases, enter a tag
          boolean isCloseTag = matchPrefix("</", false);
          context.startTag(currentRawTextNode, isCloseTag, ltPoint);
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
      RawTextNode node =
          context.isCloseTag
              ? maybeConsumeHtmlIdentifier()
              : consumeHtmlIdentifier(EXPECTED_TAG_NAME);
      if (node == null) {
        // consumeHtmlIdentifier will have already reported an error, try to keep going
        context.setTagNameNode(
            new RawTextNode(nodeIdGen.genId(), TagName.WILDCARD, currentLocation()));
      } else {
        validateIdentifier(node, TAG_NAME, BAD_TAG_NAME);
        context.setTagNameNode(node);
      }
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
        // this can happen:
        // in the case of kind="attributes" blocks which start in this state, or
        // if raw text nodes are split strangely or
        // we reported an error on an earlier character
        // We have to return in case we hit the end of the raw text.
        return;
      }
      RawTextNode identifier = consumeHtmlIdentifier(EXPECTED_ATTRIBUTE_NAME);
      if (identifier == null) {
        // consumeHtmlIdentifier will have already reported an error
        context.resetAttribute();
        // resetAtttribute doesn't change our state, and we need to either advance
        // state or advance a character to not go into an infinite loop.  So we just advance a
        // single character. This may cause spammy errors.
        advance();
        return;
      } else {
        validateIdentifier(identifier, ATTRIBUTE_NAME, BAD_ATTRIBUTE_NAME);
      }
      context.startAttribute(identifier);
    }

    /**
     * Reports an error if the identifier doesn't match the validation pattern.
     *
     * <p>The error message should point at the first range of characters that fail to match the
     * pattern.
     */
    private void validateIdentifier(
        RawTextNode identifier, Pattern validator, SoyErrorKind badIdentifierError) {
      Matcher matcher = validator.matcher(identifier.getRawText());
      if (!matcher.matches()) {
        matcher.reset();
        int startErrorIndex = 0;
        int endErrorIndex = identifier.getRawText().length();
        if (matcher.find()) {
          if (matcher.start() > 0) {
            endErrorIndex = matcher.start();
          } else {
            startErrorIndex = matcher.end();
          }
        }
        errorReporter.report(
            identifier.substringLocation(startErrorIndex, endErrorIndex), badIdentifierError);
      }
    }

    /**
     * Handle immediately after an attribute name.
     *
     * <p>Look for an '=' sign to signal the presence of an attribute value
     */
    void handleAfterAttributeName() {
      boolean ws = consumeWhitespace();
      int current = currentChar();
      if (current == '=') {
        SourceLocation.Point equalsSignPoint = currentPoint();
        advance();
        consume(); // eat the '='
        consumeWhitespace();
        context.setEqualsSignLocation(equalsSignPoint, currentPointOrEnd());
      } else {
        // we must have seen some non '=' character (or the end of the text), it doesn't matter
        // what it is, switch to the next state.  (creation of the attribute will happen
        // automatically if it hasn't already).
        context.setState(
            ws ? State.BEFORE_ATTRIBUTE_NAME : State.AFTER_TAG_NAME_OR_ATTRIBUTE,
            currentPointOrEnd());
      }
    }

    /**
     * Handle immediately before an attribute value.
     *
     * <p>Look for a quote character to signal the beginning of a quoted attribute value or switch
     * to UNQUOTED_ATTRIBUTE_VALUE to handle that.
     */
    void handleBeforeAttributeValue() {
      // per https://www.w3.org/TR/html5/syntax.html#attributes-0
      // we are allowed an arbitrary amount of whitespace preceding an attribute value.
      boolean ws = consumeWhitespace();
      if (ws) {
        // return without changing states to handle eof conditions
        return;
      }
      int c = currentChar();
      if (c == '\'' || c == '"') {
        SourceLocation.Point quotePoint = currentPoint();
        context.startQuotedAttributeValue(
            currentRawTextNode,
            quotePoint,
            c == '"' ? State.DOUBLE_QUOTED_ATTRIBUTE_VALUE : State.SINGLE_QUOTED_ATTRIBUTE_VALUE);
        advance();
        consume();
      } else {
        if (SMART_QUOTE.matches((char) c)) {
          errorReporter.report(currentLocation(), SMART_QUOTE_ERROR);
        }
        context.setState(State.UNQUOTED_ATTRIBUTE_VALUE, currentPoint());
      }
    }

    /**
     * Handle unquoted attribute values.
     *
     * <p>Search for whitespace or the end of the tag as a delimiter.
     */
    void handleUnquotedAttributeValue() {
      boolean foundDelimiter = advanceWhileMatches(UNQUOTED_ATTRIBUTE_VALUE_MATCHER);
      RawTextNode node = consumeAsRawText();
      if (node != null) {
        context.addAttributeValuePart(node);
      }
      if (foundDelimiter) {
        context.createUnquotedAttributeValue(currentPoint());
        char c = (char) currentChar();
        if (!UNQUOTED_ATTRIBUTE_VALUE_DELIMITER.matches(c)) {
          errorReporter.report(currentLocation(), ILLEGAL_HTML_ATTRIBUTE_CHARACTER);
          // go past it and consume it
          advance();
          consume();
        }
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
      boolean hasQuote =
          advanceWhileMatches(doubleQuoted ? NOT_DOUBLE_QUOTE_NOR_SMART_QUOTE : NOT_SINGLE_QUOTE);
      RawTextNode data = consumeAsRawText();
      if (data != null) {
        context.addAttributeValuePart(data);
      }
      if (hasQuote) {
        if (doubleQuoted && SMART_QUOTE.matches((char) currentChar())) {
          errorReporter.report(currentLocation(), SMART_QUOTE_ERROR);
        }

        if (context.hasQuotedAttributeValueParts()) {
          context.createQuotedAttributeValue(currentRawTextNode, doubleQuoted, currentPoint());
        } else {
          errorReporter.report(currentLocation(), FOUND_END_OF_ATTRIBUTE_STARTED_IN_ANOTHER_BLOCK);
          throw new AbortParsingBlockError();
        }
        // consume the quote
        advance();
        consume();
      }
    }

    /** Attempts to finish the current tag, returns true if it did. */
    boolean tryCreateTagEnd() {
      int c = currentChar();
      if (c == '>') {
        if (context.hasTagStart()) {
          SourceLocation.Point point = currentPoint();
          context.setState(context.createTag(currentRawTextNode, false, point), point);
        } else {
          errorReporter.report(currentLocation(), FOUND_END_TAG_STARTED_IN_ANOTHER_BLOCK);
          throw new AbortParsingBlockError();
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
          errorReporter.report(currentLocation(), FOUND_END_TAG_STARTED_IN_ANOTHER_BLOCK);
          throw new AbortParsingBlockError();
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
      edits.remove(currentRawTextNode); // mark the current node for removal
      return currentRawTextIndex != startIndex;
    }

    /**
     * Scans until the next whitespace, > or />, validates that the matched text is an html
     * identifier and returns it.
     *
     * <p>Requires that we are not at the end of input.
     */
    @Nullable
    RawTextNode consumeHtmlIdentifier(SoyErrorKind errorForMissingIdentifier) {
      RawTextNode node = maybeConsumeHtmlIdentifier();
      if (node == null) {
        errorReporter.report(currentLocation(), errorForMissingIdentifier);
      }
      return node;
    }

    @Nullable
    RawTextNode maybeConsumeHtmlIdentifier() {
      // rather than use a regex to match the prefix, we just consume all non-whitespace/non-meta
      // characters and then validate the text afterwards.
      advanceWhileMatches(TAG_DELIMITER_MATCHER);
      RawTextNode node = consumeAsRawText();
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

    /**
     * Returns true if the beginning of the input matches the given string ignoring ASCII case. If
     * the input ends prematurely, we assume that it would continue to match until the end.
     */
    boolean matchPrefixIgnoreCasePastEnd(String s) {
      int charsLeft = currentRawText.length() - currentRawTextIndex;
      if (s.length() > charsLeft) {
        s = s.substring(0, charsLeft);
      }
      return matchPrefixIgnoreCase(s, /* advance= */ false);
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
    protected void visitConstNode(ConstNode node) {
      // do nothing
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
    protected void visitKeyNode(KeyNode node) {
      processNonPrintableNode(node);
    }

    @Override
    protected void visitSkipNode(SkipNode node) {
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
    protected void visitVeLogNode(VeLogNode node) {
      if (context.getState() != State.PCDATA) {
        errorReporter.report(node.getSourceLocation(), VELOG_CAN_ONLY_BE_USED_IN_PCDATA);
      }
      visitScopedBlock(SanitizedContentKind.HTML, node, "velog");
    }

    @Override
    protected void visitCallParamValueNode(CallParamValueNode node) {
      // do nothing
    }

    @Override
    protected void visitCallNode(CallNode node) {
      // save/restore the inMsgNode flag to handle call params inside of msg noes.
      // consider
      // {msg desc="foo"}YYY{call .foo}{param p}ZZZ{/param}{/call}{/msg}
      // YYY is inside a msg node but ZZZ is not.
      boolean oldInMsgNode = this.inMsgNode;
      this.inMsgNode = false;
      visitChildren(node);
      this.inMsgNode = oldInMsgNode;
      processPrintableNode(node);
      if (context.getState() == State.PCDATA) {
        node.setIsPcData(true);
      }
    }

    @Override
    protected void visitMsgFallbackGroupNode(final MsgFallbackGroupNode node) {
      // messages act a lot like a nested sequence of switch statements.
      // at the top level it is a msg or a fallback
      // below that it is a select or a plural (optional)
      // below that it is another select or plural (also optional)
      // across all those branches exactly one thing will execute.
      // So we collect all the branches and treat the whole thing like a switch statement.
      visitControlFlowStructure(
          node,
          collectMsgBranches(node),
          "msg",
          input -> {
            switch (input.getKind()) {
              case MSG_FALLBACK_GROUP_NODE:
                return "fallbackmsg";
              case MSG_NODE:
                return "msg";
              case MSG_PLURAL_CASE_NODE:
              case MSG_SELECT_CASE_NODE:
                return "case block";
              case MSG_PLURAL_DEFAULT_NODE:
              case MSG_SELECT_DEFAULT_NODE:
                return "default block";
              default:
                throw new AssertionError("unexpected node: " + input);
            }
          },
          true, // exactly one branch will execute once
          true); // at least one branch will execute once
    }

    // control flow blocks

    @Override
    protected void visitForNode(ForNode node) {
      visitControlFlowStructure(
          node,
          node.getChildren(),
          node.getCommandName() + " loop",
          input -> {
            if (input instanceof ForNonemptyNode) {
              return "loop body";
            }
            return "ifempty block";
          },
          /* willExactlyOneBranchExecuteOnce= */ false,
          node.hasIfEmptyBlock() /* one branch will execute if there is an ifempty block. */);
    }

    @Override
    protected void visitIfNode(final IfNode node) {
      boolean hasElse = node.hasElse();
      visitControlFlowStructure(
          node,
          node.getChildren(),
          "if",
          input -> {
            if (input instanceof IfCondNode) {
              if (node.getChild(0) == input) {
                return "if block";
              }
              return "elseif block";
            }
            return "else block";
          },
          // one and only one child will execute if we have an else
          hasElse,
          hasElse);
    }

    @Override
    protected void visitSwitchNode(SwitchNode node) {
      boolean hasDefault = node.hasDefaultCase();
      visitControlFlowStructure(
          node,
          node.getChildren(),
          "switch",
          input -> {
            if (input instanceof SwitchCaseNode) {
              return "case block";
            }
            return "default block";
          },
          // one and only one child will execute if we have a default
          hasDefault,
          hasDefault);
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
    protected void visitPrintNode(PrintNode node) {
      processPrintableNode(node);
      // no need to visit children. The only children are PrintDirectiveNodes which are more like
      // expressions than soy nodes.
    }

    void processNonPrintableNode(StandaloneNode node) {
      switch (context.getState()) {
        case AFTER_TAG_NAME_OR_ATTRIBUTE:
        case BEFORE_ATTRIBUTE_NAME:
        case AFTER_ATTRIBUTE_NAME:
          context.addTagChild(node);
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
          context.addCommentChild(node);
          break;
        case NONE:
        case PCDATA:
        case RCDATA_SCRIPT:
        case RCDATA_STYLE:
        case RCDATA_TEXTAREA:
        case RCDATA_TITLE:
        case RCDATA_XMP:
        case XML_DECLARATION:
        case CDATA:
        case DOUBLE_QUOTED_XML_ATTRIBUTE_VALUE:
        case SINGLE_QUOTED_XML_ATTRIBUTE_VALUE:
          // do nothing
          break;
      }
    }

    /** Printable nodes are things like {xid..} and {print ..}. */
    void processPrintableNode(StandaloneNode node) {
      checkState(node.getKind() != Kind.RAW_TEXT_NODE);
      switch (context.getState()) {
        case AFTER_TAG_NAME_OR_ATTRIBUTE:
          errorReporter.report(
              node.getSourceLocation(), EXPECTED_WS_OR_CLOSE_AFTER_TAG_OR_ATTRIBUTE);
          break;

        case AFTER_ATTRIBUTE_NAME:
          errorReporter.report(
              node.getSourceLocation(), EXPECTED_WS_EQ_OR_CLOSE_AFTER_ATTRIBUTE_NAME);
          break;

        case BEFORE_ATTRIBUTE_NAME:
          context.startAttribute(node);
          break;

        case HTML_TAG_NAME:
          if (node.getKind() == Kind.PRINT_NODE) {
            // We have to copy so we can re-parent in the tag safely.  In theory we could get away
            // with just a shallow copy, which would be more efficient.
            context.setTagNameNode(node.copy(new CopyState()));
            edits.remove(node);
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
          context.addCommentChild(node);
          break;
        case NONE:
        case PCDATA:
        case RCDATA_SCRIPT:
        case RCDATA_STYLE:
        case RCDATA_TEXTAREA:
        case RCDATA_TITLE:
        case RCDATA_XMP:
        case XML_DECLARATION:
        case CDATA:
        case DOUBLE_QUOTED_XML_ATTRIBUTE_VALUE:
        case SINGLE_QUOTED_XML_ATTRIBUTE_VALUE:
          // do nothing
          break;
      }
    }

    @Override
    protected void visitSoyFileNode(SoyFileNode node) {
      visitChildren(node);
    }

    @Override
    protected void visitImportNode(ImportNode node) {}

    @Override
    protected void visitSoyNode(SoyNode node) {
      throw new UnsupportedOperationException(node.getKind() + " isn't supported yet");
    }

    /** Visits a block whose content is in an entirely separate content scope. */
    void visitScopedBlock(SanitizedContentKind blockKind, BlockNode parent, String name) {
      State startState = State.fromKind(blockKind);
      Checkpoint checkpoint = errorReporter.checkpoint();
      ParsingContext newCtx =
          newParsingContext(name, startState, parent.getSourceLocation().getBeginPoint());
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
     * @param children The child blocks. We don't use {@code parent.getChildren()} directly to make
     *     it possible to handle ForNodes using this method.
     * @param overallName The name, for error reporting purposes, to assign to the control flow
     *     structure
     * @param blockNamer A function to provide a name for each child block, the key is the index of
     *     the block
     * @param willExactlyOneBranchExecuteOnce Whether or not it is guaranteed that exactly one
     *     branch of the structure will execute exactly one time.
     * @param willAtLeastOneBranchExecute Whether or not it is guaranteed that at least one of the
     *     branches will execute (as opposed to no branches executing).
     */
    void visitControlFlowStructure(
        StandaloneNode parent,
        List<? extends BlockNode> children,
        String overallName,
        Function<? super BlockNode, String> blockNamer,
        boolean willExactlyOneBranchExecuteOnce,
        boolean willAtLeastOneBranchExecute) {

      // this insane case can happen with SwitchNodes.
      if (children.isEmpty()) {
        return;
      }
      State startingState = context.getState();
      State endingState = visitBranches(children, blockNamer);
      SourceLocation.Point endPoint = parent.getSourceLocation().getEndPoint();
      switch (startingState) {
        case AFTER_TAG_NAME_OR_ATTRIBUTE:
        case BEFORE_ATTRIBUTE_NAME:
        case AFTER_ATTRIBUTE_NAME:
          context.addTagChild(parent);
          // at this point we are in AFTER_TAG_NAME_OR_ATTRIBUTE, switch to whatever the branches
          // ended in, the reconcilation logic may have calculated a better state (like
          // BEFORE_ATTRIBUTE_NAME).
          context.setState(endingState, endPoint);
          break;
        case HTML_TAG_NAME:
          errorReporter.report(
              parent.getSourceLocation(), CONTROL_FLOW_IN_HTML_TAG_NAME, overallName);
          // give up on parsing this tag :(
          throw new AbortParsingBlockError();
        case BEFORE_ATTRIBUTE_VALUE:
          if (!willExactlyOneBranchExecuteOnce) {
            errorReporter.report(
                parent.getSourceLocation(),
                CONDITIONAL_BLOCK_ISNT_GUARANTEED_TO_PRODUCE_ONE_ATTRIBUTE_VALUE,
                overallName);
            // we continue and pretend like everything was ok
          }
          // theoretically we might want to support x={if $p}y{else}z{/if}w, in which case this
          // should be an attribute value part. We could support this if the branch ending state
          // was UNQUOTED_ATTRIBUTE_VALUE and at least one of the branches will execute
          if (willAtLeastOneBranchExecute && endingState == State.UNQUOTED_ATTRIBUTE_VALUE) {
            context.addAttributeValuePart(parent);
            context.setState(State.UNQUOTED_ATTRIBUTE_VALUE, endPoint);
          } else {
            context.setAttributeValue(parent);
            if (willAtLeastOneBranchExecute && endingState == State.BEFORE_ATTRIBUTE_NAME) {
              context.setState(State.BEFORE_ATTRIBUTE_NAME, endPoint);
            }
          }
          break;
        case UNQUOTED_ATTRIBUTE_VALUE:
        case DOUBLE_QUOTED_ATTRIBUTE_VALUE:
        case SINGLE_QUOTED_ATTRIBUTE_VALUE:
          context.addAttributeValuePart(parent);
          // no need to tweak any state, addAttributeValuePart doesn't modify anything
          break;
        case HTML_COMMENT:
          context.addCommentChild(parent);
          context.setState(endingState, endPoint);
          break;
        case NONE:
        case PCDATA:
        case RCDATA_SCRIPT:
        case RCDATA_STYLE:
        case RCDATA_TEXTAREA:
        case RCDATA_TITLE:
        case RCDATA_XMP:
        case XML_DECLARATION:
        case CDATA:
        case DOUBLE_QUOTED_XML_ATTRIBUTE_VALUE:
        case SINGLE_QUOTED_XML_ATTRIBUTE_VALUE:
          // do nothing
          break;
      }
    }

    /** Visit the branches of a control flow structure. */
    State visitBranches(
        List<? extends BlockNode> children, Function<? super BlockNode, String> blockNamer) {
      Checkpoint checkpoint = errorReporter.checkpoint();
      State startState = context.getState();
      State endingState = null;
      for (BlockNode block : children) {
        if (block instanceof MsgBlockNode) {
          this.inMsgNode = true;
        }
        String blockName = blockNamer.apply(block);
        ParsingContext newCtx =
            newParsingContext(blockName, startState, block.getSourceLocation().getBeginPoint());
        ParsingContext oldCtx = setContext(newCtx);
        endingState = visitBlock(startState, block, blockName, checkpoint);
        setContext(oldCtx); // restore
        if (block instanceof MsgBlockNode) {
          this.inMsgNode = false;
        }
      }

      if (errorReporter.errorsSince(checkpoint)) {
        return startState;
      }
      return endingState;
    }

    /** Visits a block and returns the finalState. */
    State visitBlock(State startState, BlockNode node, String blockName, Checkpoint checkpoint) {
      try {
        visitChildren(node);
      } catch (AbortParsingBlockError abortProcessingError) {
        // we reported some error and just gave up on the block
        // try to switch back to a reasonable state based on the start state and keep going.
        switch (startState) {
          case AFTER_ATTRIBUTE_NAME:
          case AFTER_TAG_NAME_OR_ATTRIBUTE:
          case BEFORE_ATTRIBUTE_NAME:
          case BEFORE_ATTRIBUTE_VALUE:
          case SINGLE_QUOTED_ATTRIBUTE_VALUE:
          case DOUBLE_QUOTED_ATTRIBUTE_VALUE:
          case UNQUOTED_ATTRIBUTE_VALUE:
          case HTML_TAG_NAME:
            context.resetAttribute();
            context.setState(State.BEFORE_ATTRIBUTE_NAME, node.getSourceLocation().getEndPoint());
            break;
          case CDATA:
          case DOUBLE_QUOTED_XML_ATTRIBUTE_VALUE:
          case HTML_COMMENT:
          case NONE:
          case PCDATA:
          case RCDATA_SCRIPT:
          case RCDATA_STYLE:
          case RCDATA_TEXTAREA:
          case RCDATA_TITLE:
          case RCDATA_XMP:
          case SINGLE_QUOTED_XML_ATTRIBUTE_VALUE:
          case XML_DECLARATION:
            context.reset();
            context.setState(startState, node.getSourceLocation().getEndPoint());
            break;
        }
      }
      context.finishBlock();
      State finalState = context.getState();
      SourceLocation.Point finalStateTransitionPoint = context.getStateTransitionPoint();
      if (finalState.isInvalidForEndOfBlock()) {
        errorReporter.report(
            node.getSourceLocation(), BLOCK_ENDS_IN_INVALID_STATE, blockName, finalState);
        finalState = startState;
      }
      if (!errorReporter.errorsSince(checkpoint)) {
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
        // an error occurred, restore the start state to help avoid an error explosion
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
      // TODO(lukes): consider eliminating this method by moving the logic for reparenting into
      // ParsingContext and do it as part of creating the nodes.
      switch (finalState) {
        case AFTER_TAG_NAME_OR_ATTRIBUTE:
          blockCtx.maybeFinishPendingAttribute(parent.getSourceLocation().getEndPoint());
          // fall-through
        case BEFORE_ATTRIBUTE_NAME:
        case AFTER_ATTRIBUTE_NAME:
          blockCtx.reparentDirectTagChildren(parent);
          break;
        case UNQUOTED_ATTRIBUTE_VALUE:
        case DOUBLE_QUOTED_ATTRIBUTE_VALUE:
        case SINGLE_QUOTED_ATTRIBUTE_VALUE:
          blockCtx.reparentAttributeValueChildren(parent);
          break;
        case HTML_COMMENT:
          blockCtx.reparentDirectCommentChildren(parent);
          break;
        case NONE:
        case PCDATA:
        case RCDATA_SCRIPT:
        case RCDATA_STYLE:
        case RCDATA_TEXTAREA:
        case RCDATA_TITLE:
        case RCDATA_XMP:
        case XML_DECLARATION:
        case CDATA:
        case DOUBLE_QUOTED_XML_ATTRIBUTE_VALUE:
        case SINGLE_QUOTED_XML_ATTRIBUTE_VALUE:
          // do nothing.. there should be nothing in context
          break;
        case BEFORE_ATTRIBUTE_VALUE:
        case HTML_TAG_NAME:
          // impossible?
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
          return didYouForgetToCloseThe("<textarea> block");
        case RCDATA_TITLE:
          return didYouForgetToCloseThe("<title> block");
        case RCDATA_XMP:
          return didYouForgetToCloseThe("<xmp> block");
        case HTML_TAG_NAME: // kind of crazy
        case AFTER_ATTRIBUTE_NAME:
        case AFTER_TAG_NAME_OR_ATTRIBUTE:
        case BEFORE_ATTRIBUTE_NAME:
        case XML_DECLARATION:
        case UNQUOTED_ATTRIBUTE_VALUE:
          // if this wasn't reconciled, it means they probably forgot to close the tag
          if (startState == State.PCDATA) {
            return "Did you forget to close the tag?";
          }
          return null;
        case NONE: // should be impossible, there are no transitions into NONE from non-NONE
      }
      throw new AssertionError("unexpected final state: " + finalState);
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
    ParsingContext newParsingContext(
        String blockName, State state, SourceLocation.Point startPoint) {
      return new ParsingContext(
          blockName, state, startPoint, filePath, edits, errorReporter, nodeIdGen);
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
   *
   * <p>The handling of attributes is particularly tricky. It is difficult to decide when to
   * complete an attribute (that is, create the {@link HtmlAttributeNode}). In theory it should be
   * obvious, the attribute is 'done' when we see one of the following:
   *
   * <ul>
   *   <li>A non '=' character after an attribute name (a value-less attribute)
   *   <li>A ' or " character after a quoted attribute value
   *   <li>A whitespace character after an unquoted attribute value
   * </ul>
   *
   * However, we want to support various control flow for creating attribute values. For example,
   * <code>href={if $v2}"/foo2"{else}"/foo"{/if}</code>. Here when we see the closing double quote
   * characters we know that the attribute value is done, but it is too early to close the
   * attribute. So we need to delay. Thus the rules for when we 'finish' an attribute are:
   *
   * <ul>
   *   <li>If a block starts in {@link State#BEFORE_ATTRIBUTE_VALUE} then the block must be the
   *       attribute value
   *   <li>If we see the beginning of a new attribute, we should finish the previous one
   *   <li>If we see the end of a tag, we should finish the previous attribute.
   *   <li>At the end of a block, we should complete attribute nodes if the block started in a tag
   *       state
   * </ul>
   */
  private static final class ParsingContext {
    final String blockName;
    final State startingState;
    final SourceFilePath filePath;
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
    StandaloneNode tagNameNode;
    State tagStartState;

    // TODO(lukes): consider lazily allocating these lists.
    /** All the 'direct' children of the current tag. */
    final List<StandaloneNode> directTagChildren = new ArrayList<>();

    // for tracking the current attribute being built

    StandaloneNode attributeName;

    SourceLocation.Point equalsSignLocation;
    StandaloneNode attributeValue;

    // For the attribute value,  where the quoted attribute value started

    /** Where the open quote of a quoted attribute value starts. */
    SourceLocation.Point quotedAttributeValueStart;

    /** all the direct children of the attribute value. */
    final List<StandaloneNode> attributeValueChildren = new ArrayList<>();

    // For tracking the HTML comment node
    SourceLocation.Point commentStartPoint;
    RawTextNode commentStartNode;
    final List<StandaloneNode> directCommentChildren = new ArrayList<>();

    ParsingContext(
        String blockName,
        State startingState,
        SourceLocation.Point startPoint,
        SourceFilePath filePath,
        AstEdits edits,
        ErrorReporter errorReporter,
        IdGenerator nodeIdGen) {
      this.blockName = checkNotNull(blockName);
      this.startingState = checkNotNull(startingState);
      this.state = checkNotNull(startingState);
      this.stateTransitionPoint = checkNotNull(startPoint);
      this.filePath = checkNotNull(filePath);
      this.nodeIdGen = checkNotNull(nodeIdGen);
      this.edits = checkNotNull(edits);
      this.errorReporter = checkNotNull(errorReporter);
    }

    /** Called at the end of a block to finish any pending attribute nodes. */
    void finishBlock() {
      if (startingState.isTagState()) {
        maybeFinishPendingAttribute(stateTransitionPoint);
      }
    }

    /** Attaches the attributeValueChildren to the parent. */
    void reparentAttributeValueChildren(BlockNode parent) {
      edits.addChildren(parent, attributeValueChildren);
      attributeValueChildren.clear();
    }

    /** Attaches the directTagChildren to the parent. */
    void reparentDirectTagChildren(BlockNode parent) {
      if (attributeValue != null) {
        edits.addChild(parent, attributeValue);
        attributeValue = null;
      }

      edits.addChildren(parent, directTagChildren);
      directTagChildren.clear();
    }

    /** Attaches the directCommentChildren to the parent. */
    void reparentDirectCommentChildren(BlockNode parent) {
      edits.addChildren(parent, directCommentChildren);
      directCommentChildren.clear();
    }

    /** Returns true if this has accumulated parts of an unquoted attribute value. */
    boolean hasUnquotedAttributeValueParts() {
      return quotedAttributeValueStart == null && !attributeValueChildren.isEmpty();
    }

    /** Returns true if this has accumulated parts of a quoted attribute value. */
    boolean hasQuotedAttributeValueParts() {
      return quotedAttributeValueStart != null;
    }

    boolean hasTagStart() {
      return tagStartNode != null && tagStartPoint != null;
    }

    boolean hasCommentBegin() {
      return commentStartPoint != null && commentStartNode != null;
    }

    /** Sets the given node as a direct child of the tag currently being built. */
    void addTagChild(StandaloneNode node) {
      maybeFinishPendingAttribute(node.getSourceLocation().getBeginPoint());
      checkNotNull(node);
      directTagChildren.add(node);
      edits.remove(node);
      setState(State.AFTER_TAG_NAME_OR_ATTRIBUTE, node.getSourceLocation().getEndPoint());
    }

    void addCommentChild(StandaloneNode node) {
      checkNotNull(node);
      directCommentChildren.add(node);
      edits.remove(node);
    }

    /** Asserts that the context is empty. */
    @FormatMethod
    void checkEmpty(String fmt, Object... args) {
      StringBuilder error = null;

      if (!directTagChildren.isEmpty()) {
        error = format(error, "Expected directTagChildren to be empty, got: %s", directTagChildren);
      }
      if (attributeName != null) {
        error = format(error, "Expected attributeName to be null, got: %s", attributeName);
      }
      if (equalsSignLocation != null) {
        error =
            format(error, "Expected equalsSignLocation to be null, got: %s", equalsSignLocation);
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
      if (tagNameNode != null) {
        error = format(error, "Expected tagName to be null, got: %s", tagNameNode.toSourceString());
      }
      if (tagStartNode != null) {
        error = format(error, "Expected tagStartNode to be null, got: %s", tagStartNode);
      }
      if (tagStartState != null) {
        error = format(error, "Expected tagStartState to be null, got: %s", tagStartState);
      }
      if (quotedAttributeValueStart != null) {
        error =
            format(
                error,
                "Expected quotedAttributeValueStart to be null, got: %s",
                quotedAttributeValueStart);
      }
      if (commentStartPoint != null) {
        error = format(error, "Expected commentStartPoint to be null, got: %s", commentStartPoint);
      }
      if (commentStartNode != null) {
        error = format(error, "Expected commentStartNode to be null, got: %s", commentStartNode);
      }
      if (!directCommentChildren.isEmpty()) {
        error =
            format(
                error,
                "Expected directCommentChildren to be empty, got: %s",
                directCommentChildren);
      }
      if (error != null) {
        throw new IllegalStateException(String.format(fmt + "\n", args) + error);
      }
    }

    @FormatMethod
    private static StringBuilder format(StringBuilder error, String fmt, Object... args) {
      if (error == null) {
        error = new StringBuilder();
      }
      error.append(String.format(fmt, args));
      error.append('\n');
      return error;
    }

    /** Resets all parsing state, this is useful for error recovery. */
    void reset() {
      tagStartPoint = null;
      tagStartNode = null;
      tagNameNode = null;
      tagStartState = null;
      directTagChildren.clear();
      directCommentChildren.clear();
      resetAttribute();
    }

    void resetAttribute() {
      attributeName = null;
      equalsSignLocation = null;
      attributeValue = null;
      quotedAttributeValueStart = null;
      attributeValueChildren.clear();
    }

    /**
     * Records the start of an html tag
     *
     * @param tagStartNode The node where it started
     * @param isCloseTag If the current tag is a close tag.
     * @param point the source location of the {@code <} character.
     */
    void startTag(RawTextNode tagStartNode, boolean isCloseTag, SourceLocation.Point point) {
      checkState(this.tagStartPoint == null);
      checkState(this.tagStartNode == null);
      checkState(this.tagStartState == null);
      checkState(this.directTagChildren.isEmpty());

      // need to check if it is safe to transition into a tag.
      // this is only true if our starting location is pcdata, this is how we prevent people from
      // escaping kind=js blocks with a '</script'
      if (startingState != State.PCDATA) {
        errorReporter.report(
            point.asLocation(filePath),
            BLOCK_TRANSITION_DISALLOWED,
            blockName,
            startingState,
            "tag");
        throw new AbortParsingBlockError();
      }

      this.tagStartState = state;
      this.tagStartPoint = checkNotNull(point);
      this.tagStartNode = checkNotNull(tagStartNode);
      this.isCloseTag = isCloseTag;
    }

    /** Records the start of an HTML comment. */
    void startComment(RawTextNode commentStartNode, SourceLocation.Point commentStartPoint) {
      checkState(this.commentStartPoint == null);
      checkState(this.commentStartNode == null);
      checkState(this.directCommentChildren.isEmpty());
      this.commentStartPoint = checkNotNull(commentStartPoint);
      this.commentStartNode = checkNotNull(commentStartNode);
      setState(State.HTML_COMMENT, commentStartPoint);
    }

    /** Returns the tag start location, for error reporting. */
    SourceLocation tagStartLocation() {
      return tagStartPoint.asLocation(filePath);
    }

    /** Sets the node that holds the tag name of the tag currently being built. */
    void setTagNameNode(StandaloneNode rawTextOrPrintNode) {
      this.tagNameNode = checkNotNull(rawTextOrPrintNode);
      edits.remove(rawTextOrPrintNode);
      setState(
          State.AFTER_TAG_NAME_OR_ATTRIBUTE, rawTextOrPrintNode.getSourceLocation().getEndPoint());
    }

    void startAttribute(StandaloneNode attrName) {
      maybeFinishPendingAttribute(attrName.getSourceLocation().getBeginPoint());
      checkNotNull(attrName);
      checkState(attributeName == null);
      if (startingState == State.BEFORE_ATTRIBUTE_VALUE) {
        errorReporter.report(
            attrName.getSourceLocation(),
            BLOCK_TRANSITION_DISALLOWED,
            blockName,
            startingState,
            "attribute");
        throw new AbortParsingBlockError();
      }
      edits.remove(attrName);
      attributeName = attrName;
      setState(State.AFTER_ATTRIBUTE_NAME, attrName.getSourceLocation().getEndPoint());
    }

    void setEqualsSignLocation(
        SourceLocation.Point equalsSignPoint, SourceLocation.Point stateTransitionPoint) {
      checkNotNull(equalsSignPoint);
      if (attributeName == null) {
        // the attribute must have been started in another block
        errorReporter.report(
            stateTransitionPoint.asLocation(filePath), FOUND_EQ_WITH_ATTRIBUTE_IN_ANOTHER_BLOCK);
        throw new AbortParsingBlockError();
      }
      checkState(equalsSignLocation == null);
      equalsSignLocation = equalsSignPoint;
      setState(State.BEFORE_ATTRIBUTE_VALUE, stateTransitionPoint);
    }

    void setAttributeValue(StandaloneNode node) {
      checkNotNull(node);
      checkState(
          attributeValue == null, "attribute value already set at: %s", node.getSourceLocation());
      edits.remove(node);
      attributeValue = node;
      setState(State.AFTER_TAG_NAME_OR_ATTRIBUTE, node.getSourceLocation().getEndPoint());
    }

    /**
     * Records the start of a quoted attribute value.
     *
     * @param node The node where it started
     * @param point The source location where it started.
     * @param nextState The next state, either {@link State#DOUBLE_QUOTED_ATTRIBUTE_VALUE} or {@link
     *     State#SINGLE_QUOTED_ATTRIBUTE_VALUE}.
     */
    void startQuotedAttributeValue(RawTextNode node, SourceLocation.Point point, State nextState) {
      checkState(!hasQuotedAttributeValueParts());
      checkState(!hasUnquotedAttributeValueParts());
      edits.remove(node);
      quotedAttributeValueStart = checkNotNull(point);
      setState(nextState, point);
    }

    /** Adds a new attribute value part and marks the node for removal. */
    void addAttributeValuePart(StandaloneNode node) {
      attributeValueChildren.add(node);
      edits.remove(node);
    }

    /** Completes an unquoted attribute value. */
    void createUnquotedAttributeValue(SourceLocation.Point endPoint) {
      if (!hasUnquotedAttributeValueParts()) {
        if (attributeName != null) {
          errorReporter.report(endPoint.asLocation(filePath), EXPECTED_ATTRIBUTE_VALUE);
        } else {
          errorReporter.report(
              endPoint.asLocation(filePath), FOUND_END_OF_ATTRIBUTE_STARTED_IN_ANOTHER_BLOCK);
          throw new AbortParsingBlockError();
        }
        resetAttribute();
        setState(State.AFTER_TAG_NAME_OR_ATTRIBUTE, endPoint);
        return;
      }
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
      maybeFinishPendingAttribute(endPoint);
      HtmlTagNode replacement;
      SourceLocation sourceLocation = new SourceLocation(filePath, tagStartPoint, endPoint);
      if (isCloseTag) {
        // we allow for attributes in close tags in the parser since there is a usecase for msg
        // tags
        // this is validated after parsing  (we can't validate it here because the full nodes are
        // not constructed yet, children aren't attached.)
        if (selfClosing) {
          errorReporter.report(
              endPoint.asLocation(filePath).offsetStartCol(-1), SELF_CLOSING_CLOSE_TAG);
        }
        replacement =
            new HtmlCloseTagNode(
                nodeIdGen.genId(), tagNameNode, sourceLocation, TagExistence.IN_TEMPLATE);
      } else {
        replacement =
            new HtmlOpenTagNode(
                nodeIdGen.genId(),
                tagNameNode,
                sourceLocation,
                selfClosing,
                TagExistence.IN_TEMPLATE);
      }
      // Depending on the tag name, we may need to enter a special state after the tag.
      State nextState = getNextState(replacement.getTagName());
      // if we see a naked </script report an error
      if (isCloseTag && nextState.isRcDataState() && tagStartState != nextState) {
        errorReporter.report(tagStartLocation(), UNEXPECTED_CLOSE_TAG);
      }
      if (selfClosing || isCloseTag) {
        // next state for close tags is always pcdata (special blocks don't recursively nest)
        nextState = State.PCDATA;
      }
      edits.remove(tagEndNode);
      edits.addChildren(replacement, directTagChildren);
      edits.replace(tagStartNode, replacement);
      directTagChildren.clear();
      tagStartPoint = null;
      tagNameNode = null;
      tagStartState = null;
      tagStartNode = null;
      checkEmpty("Expected state to be empty after completing a tag");
      return nextState;
    }

    /** Creates an {code HtmlCommentNode}. */
    State createHtmlComment(SourceLocation.Point commentEndPoint) {
      SourceLocation sourceLocation =
          new SourceLocation(filePath, commentStartPoint, commentEndPoint);
      HtmlCommentNode replacement = new HtmlCommentNode(nodeIdGen.genId(), sourceLocation);
      edits.addChildren(replacement, directCommentChildren);
      // cast is safe because HtmlCommentNode implements StandaloneNode
      edits.replace(commentStartNode, (StandaloneNode) replacement);
      directCommentChildren.clear();
      commentStartPoint = null;
      commentStartNode = null;
      return State.PCDATA;
    }

    private static State getNextState(TagName tagName) {
      if (tagName.getRcDataTagName() == null) {
        return State.PCDATA;
      }
      switch (tagName.getRcDataTagName()) {
        case SCRIPT:
          return State.RCDATA_SCRIPT;
        case STYLE:
          return State.RCDATA_STYLE;
        case TEXTAREA:
          return State.RCDATA_TEXTAREA;
        case TITLE:
          return State.RCDATA_TITLE;
        case XMP:
          return State.RCDATA_XMP;
      }
      throw new AssertionError(tagName.getRcDataTagName());
    }

    void maybeFinishPendingAttribute(SourceLocation.Point currentPoint) {
      // For quoted attribute values we should have already finished them (when we saw the closing
      // quote).  But for unquoted attribute values we delay closing them until we see a delimiter
      // so create one now if we have parts.
      if (hasUnquotedAttributeValueParts()) {
        createUnquotedAttributeValue(currentPoint);
      } else if (hasQuotedAttributeValueParts()) {
        // if there is a quoted attribute, it should have been finished
        // which means the only way we could get here is if the attribute was not finished
        // in a block
        errorReporter.report(
            currentPoint.asLocation(filePath), FOUND_END_OF_ATTRIBUTE_STARTED_IN_ANOTHER_BLOCK);
        resetAttribute();
      }
      if (attributeName != null) {
        SourceLocation location = attributeName.getSourceLocation();
        HtmlAttributeNode attribute;
        if (attributeValue != null) {
          location = location.extend(attributeValue.getSourceLocation());
          attribute =
              new HtmlAttributeNode(nodeIdGen.genId(), location, checkNotNull(equalsSignLocation));
          edits.addChild(attribute, attributeName);
          edits.addChild(attribute, attributeValue);
        } else {
          attribute = new HtmlAttributeNode(nodeIdGen.genId(), location, null);
          edits.addChild(attribute, attributeName);
        }
        attributeName = null;
        equalsSignLocation = null;
        attributeValue = null;
        // We don't call addDirectTagChild to avoid
        // 1. calling maybeFinishPendingAttribute recursively
        // 2. to avoid changing the state field
        directTagChildren.add(attribute);
        edits.remove(attribute);
      }
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

  private static List<? extends MsgBlockNode> collectMsgBranches(MsgFallbackGroupNode node) {
    List<MsgBlockNode> msgBranches = new ArrayList<>();
    for (MsgNode child : node.getChildren()) {
      collectMsgBranches(child, msgBranches);
    }
    return msgBranches;
  }

  private static void collectMsgBranches(MsgBlockNode parent, List<MsgBlockNode> msgBranches) {
    StandaloneNode firstChild = Iterables.getFirst(parent.getChildren(), null);
    if (firstChild instanceof MsgPluralNode) {
      for (CaseOrDefaultNode caseOrDefault : ((MsgPluralNode) firstChild).getChildren()) {
        collectMsgBranches((MsgBlockNode) caseOrDefault, msgBranches);
      }
    } else if (firstChild instanceof MsgSelectNode) {
      for (CaseOrDefaultNode caseOrDefault : ((MsgSelectNode) firstChild).getChildren()) {
        collectMsgBranches((MsgBlockNode) caseOrDefault, msgBranches);
      }
    } else {
      msgBranches.add(parent);
    }
  }

  /** A custom error to halt processing of a given control flow block. */
  private static final class AbortParsingBlockError extends Error {}
}
