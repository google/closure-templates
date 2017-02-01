/*
 * Copyright 2013 Google Inc.
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

package com.google.template.soy.parsepasses.contextautoesc;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Ordering;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.exprparse.SoyParsingContext;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.EscapingMode;
import com.google.template.soy.soytree.ExprUnion;
import com.google.template.soy.soytree.HtmlContext;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.defn.InjectedParam;
import com.google.template.soy.types.primitive.StringType;
import java.util.List;

/**
 * Inserts attributes into templates to bless inline {@code <script>} and {@code <style>} elements
 * and inline event handler and style attributes so that the browser can distinguish scripts
 * specified by the template author from ones injected via XSS.
 *
 * <p>This class converts templates by adding {@code nonce="..."} to {@code <script>} and {@code
 * <style>} elements, so
 *
 * <blockquote>
 *
 * {@code <script>...</script>}
 *
 * </blockquote>
 *
 * becomes
 *
 * <blockquote>
 *
 * {@code <script{if $ij.csp_nonce} nonce="{$ij.csp_nonce}"{/if}>...</script>}
 *
 * </blockquote>
 *
 * which authorize scripts in HTML pages that are governed by the <i>Content Security Policy</i>.
 *
 * <p>This class assumes that the value of {@code $ij.csp_nonce} will either be null or a valid <a
 * href="//dvcs.w3.org/hg/content-security-policy/raw-file/tip/csp-specification.dev.html#dfn-a-valid-nonce"
 * >CSP-style "nonce"</a>, an unguessable string consisting of Latin Alpha-numeric characters, plus
 * ({@code '+'}), and solidus ({@code '/'}).
 *
 * <blockquote>
 *
 * {@code nonce-value = 1*( ALPHA / DIGIT / "+" / "/" )}
 *
 * </blockquote>
 *
 * <h3>Dependencies</h3>
 *
 * <p>If inline event handlers or styles are used, then the page should also load {@code
 * security.CspVerifier} which verifies event handler values.
 *
 * <h3>Caveats</h3>
 *
 * <p>This class does not add any {@code <meta http-equiv="content-security-policy" ...>} elements
 * to the template. The application developer must specify the CSP policy headers and include the
 * nonce there.
 *
 * <p>Nonces should be of sufficient length, and from a crypto-strong source of randomness. The
 * stock <code>java.util.Random</code> is not strong enough, though a properly seeded <code>
 * SecureRandom</code> is ok.
 *
 */
public final class ContentSecurityPolicyPass {

  private ContentSecurityPolicyPass() {
    // Not instantiable.
  }

  /** The unprefixed name of the injected variable that holds the CSP nonce value for the page. */
  public static final String CSP_NONCE_VARIABLE_NAME = "csp_nonce";

  /** The name of the CSP nonce attribute, equals sign, and opening double quote. */
  private static final String NONCE_ATTR_BEFORE_VALUE = " nonce=\"";

  /** The closing double quote that appears after an attribute value. */
  private static final String ATTR_AFTER_VALUE = "\"";

  /**
   * A variable definition for {@code $ij.csp_nonce}.
   *
   * <p>Since this pass implicitly blesses scripts that appear in the template text, authors should
   * not explicitly mention {@code $id.csp_nonce} in their template signatures, so we do not look
   * for a declared variable definition.
   */
  private static final InjectedParam IMPLICIT_CSP_NONCE_DEFN =
      new InjectedParam(CSP_NONCE_VARIABLE_NAME, StringType.getInstance());

  // ---------------------------------------------------------------------------------------------
  // Predicates used to identify HTML element and attribute boundaries in templates.
  // ---------------------------------------------------------------------------------------------

  /**
   * True for any context that occurs within a {@code <script>} or {@code <style>} open tag. {@code
   * [START]} and {@code [END]} mark ranges of positions for which this predicate is true. {@code
   * <script[START] src=[END]foo[START]>[END]body()</script>}.
   */
  private static final Predicate<? super Context> IN_SCRIPT_OR_STYLE_TAG_PREDICATE =
      new Predicate<Context>() {
        @Override
        public boolean apply(Context c) {
          return (
          // In a script tag or style,
          (c.elType == Context.ElementType.SCRIPT || c.elType == Context.ElementType.STYLE)
              && c.state == HtmlContext.HTML_TAG
              // but not in an attribute
              && c.attrType == Context.AttributeType.NONE);
        }
      };

  /**
   * True between the end of a {@code <script>} or {@code <style>}tag and the start of its end tag.
   * {@code [START]} and {@code [END]} mark ranges of positions for which this predicate is true.
   * {@code <script src=foo]>[START]body()[END]</script>}.
   */
  private static final Predicate<? super Context> IN_SCRIPT_OR_STYLE_BODY_PREDICATE =
      new Predicate<Context>() {
        @Override
        public boolean apply(Context c) {
          return (
          // If we're not in an attribute,
          c.attrType == Context.AttributeType.NONE
              // but we're in JS or CSS, then we must be in a script or style body.
              && (c.state == HtmlContext.JS || c.state == HtmlContext.CSS));
        }
      };

  /** True immediately before an HTML attribute value. */
  public static final Predicate<? super Context> HTML_BEFORE_ATTRIBUTE_VALUE =
      new Predicate<Context>() {
        @Override
        public boolean apply(Context c) {
          return c.state == HtmlContext.HTML_BEFORE_ATTRIBUTE_VALUE;
        }
      };

  // ---------------------------------------------------------------------------------------------
  // Generators for Soy nodes that mark JS as safe to run.
  // ---------------------------------------------------------------------------------------------

  /** Generates Soy nodes to inject at a specific location in a raw text node. */
  private abstract static class InjectedSoyGenerator {

    /** The raw text node into which to inject nodes. */
    final RawTextNode rawTextNode;

    /** The offset into rawTextNode's text at which to inject the nodes. */
    final int offset;

    /**
     * @param rawTextNode The raw text node into which to inject nodes.
     * @param offset the offset into rawTextNode's text at which to inject the nodes.
     */
    InjectedSoyGenerator(RawTextNode rawTextNode, int offset) {
      Preconditions.checkElementIndex(offset, rawTextNode.getRawText().length(), "text offset");
      this.rawTextNode = rawTextNode;
      this.offset = offset;
    }

    /**
     * Generates standalone Soy nodes to inject at {@link #offset} in {@link #rawTextNode} and adds
     * them to out.
     *
     * @param idGenerator generates IDs for newly created nodes.
     * @param out receives nodes to add in the order they should be added.
     */
    abstract void addNodesToInject(
        IdGenerator idGenerator, ImmutableList.Builder<? super SoyNode.StandaloneNode> out);
  }

  private static final class NonceAttrGenerator extends InjectedSoyGenerator {

    NonceAttrGenerator(RawTextNode rawTextNode, int offset) {
      super(rawTextNode, offset);
    }

    /** Adds `<code> nonce="{$ij.csp_nonce}"</code>`. */
    @Override
    void addNodesToInject(
        IdGenerator idGenerator, ImmutableList.Builder<? super SoyNode.StandaloneNode> out) {
      out.add(
          new RawTextNode(
              idGenerator.genId(), NONCE_ATTR_BEFORE_VALUE, rawTextNode.getSourceLocation()));
      out.add(
          makeInjectedCspNoncePrintNode(
              rawTextNode.getSourceLocation(), idGenerator, EscapingMode.FILTER_CSP_NONCE_VALUE));
      out.add(
          new RawTextNode(idGenerator.genId(), ATTR_AFTER_VALUE, rawTextNode.getSourceLocation()));
    }
  }

  /** A group of InjectedSoyGenerators with the same raw text node and offset. */
  private static final class GroupOfInjectedSoyGenerator extends InjectedSoyGenerator {
    final ImmutableList<InjectedSoyGenerator> members;

    /** @param group InjectedSoyGenerator with the same raw text node and offset. */
    GroupOfInjectedSoyGenerator(List<? extends InjectedSoyGenerator> group) {
      super(group.get(0).rawTextNode, group.get(0).offset);
      members = ImmutableList.copyOf(group);
      for (InjectedSoyGenerator member : members) {
        if (member.rawTextNode != rawTextNode || member.offset != offset) {
          throw new IllegalArgumentException("Invalid group member");
        }
      }
    }

    /** delegates to each member in-order to add nodes to out. */
    @Override
    void addNodesToInject(
        IdGenerator idGenerator, ImmutableList.Builder<? super SoyNode.StandaloneNode> out) {
      for (InjectedSoyGenerator member : members) {
        member.addNodesToInject(idGenerator, out);
      }
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Soy tree traversal that injects Soy nodes to mark JS in templates as safe to run.
  // ---------------------------------------------------------------------------------------------

  /**
   * Add attributes to author-specified scripts and styles so that they will continue to run even
   * though the browser's CSP policy blocks injected scripts and styles.
   */
  public static void blessAuthorSpecifiedScripts(
      Iterable<? extends SlicedRawTextNode> slicedRawTextNodes) {
    // Given
    //   <script type="text/javascript">
    //     alert(1337)
    //   </script>
    // we want to produce
    //   <script type="text/javascript"{if $ij.csp_nonce} nonce="{$ij.csp_nonce}"{/if}>
    //     alert(1337)
    //   </script>
    // We need the nonce value to be unguessable which means not reliably reusing the same value
    // from one page render to the next.

    // We do this in several steps.
    // 1. Identify the end of each <script> and <style> tag.
    //    <script type="text/javascript">alert(1337)</script>
    //                                  ^-- Can insert more attributes here
    //    We use the contexts from the contextual auto-escaper to identify the boundary between
    //    the tag that starts a script element and its body.
    // 2. Walk backwards over ">" and "/>" to find a place where it is safe to insert atttributes.
    // 3. Create an InjectedSoyGenerator instance that encapsulates the content to insert.
    //    <script type="text/javascript">alert(1337)</script>
    //                                  ^-- Remember this location.
    // 4. Group InjectedSoyGenerators at the same location so that we could inject multiple chunks
    //    of content at the same slice offset.
    // 5. Create a conditional check at each unique location, {if $ij.csp_nonce}...{/if}, so that we
    //    don't insert CSP attributes when the template is applied without a secret.
    // 6. Create Soy nodes to fill out the {if}
    //    <script>  ->  <script{if $ij.csp_nonce} nonce="{$ij.csp_nonce}"{/if}>

    ImmutableList.Builder<InjectedSoyGenerator> injectedSoyGenerators = ImmutableList.builder();

    // We look for the end of attributes before the end of tags so that the stable sort we use to
    // group generators leaves any at attribute ends before the ones at the end of a tag.

    findNonceAttrLocations(slicedRawTextNodes, injectedSoyGenerators);

    ImmutableListMultimap<RawTextNode, InjectedSoyGenerator> groupedInjectedAttrs =
        sortAndGroup(injectedSoyGenerators.build());

    generateAndInsertSoyNodesWrappedInIfNode(groupedInjectedAttrs);
  }

  /**
   * Handles steps 3-5 by creating a NonceAttrGenerator for each location at the ^ in {@code <script
   * foo=bar^>} immediately after the run of attributes in a script tag.
   */
  private static void findNonceAttrLocations(
      Iterable<? extends SlicedRawTextNode> slicedRawTextNodes,
      ImmutableList.Builder<InjectedSoyGenerator> out) {

    // Step 3: identify slices that end a <script> element so we can find a location where it it is
    // safe to insert an attribute.
    for (SlicedRawTextNode.RawTextSlice slice :
        SlicedRawTextNode.find(
            slicedRawTextNodes,
            null,
            IN_SCRIPT_OR_STYLE_TAG_PREDICATE,
            IN_SCRIPT_OR_STYLE_BODY_PREDICATE)) {
      String rawText = slice.getRawText();
      int rawTextLen = rawText.length();
      // Step 4: find a safe place to insert attributes.
      if (rawText.charAt(rawTextLen - 1) != '>') {
        throw new IllegalStateException("Invalid tag end: " + rawText);
      }
      int insertionPoint = rawTextLen - 1;
      // We can't put an attribute in the middle of an XML-style "/>" tag terminator.
      if (insertionPoint - 1 >= 0 && rawText.charAt(insertionPoint - 1) == '/') {
        --insertionPoint;
      }

      // Step 5: create a generator for the CSP nonce attribute.
      out.add(
          new NonceAttrGenerator(
              slice.slicedRawTextNode.getRawTextNode(), slice.getStartOffset() + insertionPoint));
    }
  }

  private static final Ordering<InjectedSoyGenerator> BY_OFFSET =
      new Ordering<InjectedSoyGenerator>() {
        @Override
        public int compare(InjectedSoyGenerator o1, InjectedSoyGenerator o2) {
          return Integer.compare(o1.offset, o2.offset);
        }
      };

  /**
   * Handles step 6 by converting a list of InjectedSoyGenerators into an equivalent list where
   * there is only one per text node and offset, and where the list is sorted by text node ID and
   * offset.
   */
  private static ImmutableListMultimap<RawTextNode, InjectedSoyGenerator> sortAndGroup(
      List<InjectedSoyGenerator> ungrouped) {
    // Sort by node ID & offset
    ListMultimap<RawTextNode, InjectedSoyGenerator> byNode =
        MultimapBuilder.hashKeys().arrayListValues().build();
    for (InjectedSoyGenerator generator : ungrouped) {
      byNode.put(generator.rawTextNode, generator);
    }
    // Walk over list grouping members with the same raw text node and offset.
    ImmutableListMultimap.Builder<RawTextNode, InjectedSoyGenerator> groupedAndSorted =
        ImmutableListMultimap.builder();
    for (RawTextNode node : byNode.keySet()) {
      List<InjectedSoyGenerator> group = BY_OFFSET.sortedCopy(byNode.get(node));
      for (int i = 0, end; i < group.size(); i++) {
        InjectedSoyGenerator firstGroupMember = group.get(i);
        end = i + 1;
        while (end < group.size() && group.get(end).offset == firstGroupMember.offset) {
          ++end;
        }
        // NOTE: currently it doesn't appear to be possible for there to be multiple injectors at
        // the same offset, but we support it nonetheless.
        InjectedSoyGenerator groupGenerator =
            end == i + 1
                ? firstGroupMember
                : new GroupOfInjectedSoyGenerator(group.subList(i, end));
        groupedAndSorted.put(node, groupGenerator);
      }
    }
    return groupedAndSorted.build();
  }

  /**
   * Handles steps 7 and 8 by applying the generators to create Soy nodes and injects them at the
   * location in the template specified by {@link InjectedSoyGenerator#rawTextNode} and {@link
   * InjectedSoyGenerator#offset}, splitting and replacing text nodes as necessary.
   *
   * <p>{@link RawTextNode}'s text cannot be changed, so generators with the same {@link
   * RawTextNode} cannot be applied separately. This method takes a list of generators, so it can
   * apply them in a batch and avoid conflicts.
   *
   * @param groupedInjectedAttrs A sorted, grouped, list of generators.
   */
  private static void generateAndInsertSoyNodesWrappedInIfNode(
      ImmutableListMultimap<RawTextNode, InjectedSoyGenerator> groupedInjectedAttrs) {
    for (RawTextNode rawTextNode : groupedInjectedAttrs.keySet()) {
      String rawText = rawTextNode.getRawText();
      SoyNode.BlockNode parent = rawTextNode.getParent();
      IdGenerator idGenerator =
          parent.getNearestAncestor(SoyFileSetNode.class).getNodeIdGenerator();

      // Split rawTextNode on the offsets, and at each split, insert a nonce value.
      int textStart = 0;
      int childIndex = parent.getChildIndex(rawTextNode);
      parent.removeChild(rawTextNode);
      for (InjectedSoyGenerator generator : groupedInjectedAttrs.get(rawTextNode)) {
        int offset = generator.offset;
        if (offset != textStart) {
          RawTextNode textBefore =
              new RawTextNode(
                  idGenerator.genId(),
                  rawText.substring(textStart, offset),
                  rawTextNode.getSourceLocation());
          parent.addChild(childIndex, textBefore);
          ++childIndex;
          textStart = offset;
        }

        // Step 7: add an {if $ij.csp_nonce}...{/if} to prevent generation of CSP nonce when the
        // template is applied without a secret.
        IfNode ifNode = new IfNode(idGenerator.genId(), rawTextNode.getSourceLocation());
        IfCondNode ifCondNode =
            new IfCondNode(
                idGenerator.genId(),
                rawTextNode.getSourceLocation(),
                "if",
                new ExprUnion(makeReferenceToInjectedCspNonce(rawTextNode.getSourceLocation())));
        parent.addChild(childIndex, ifNode);
        ++childIndex;
        ifNode.addChild(ifCondNode);

        // Step 8: inject Soy nodes into the {if}.
        ImmutableList.Builder<SoyNode.StandaloneNode> newChildren = ImmutableList.builder();
        generator.addNodesToInject(idGenerator, newChildren);
        ifCondNode.addChildren(newChildren.build());
      }

      if (textStart != rawText.length()) {
        RawTextNode textTail =
            new RawTextNode(
                idGenerator.genId(), rawText.substring(textStart), rawTextNode.getSourceLocation());
        parent.addChild(childIndex, textTail);
      }
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Methods to programmatically create Soy commands and expressions.
  // ---------------------------------------------------------------------------------------------

  /** Builds the Soy expression {@code $ij.csp_nonce} with an appropriate type. */
  private static VarRefNode makeReferenceToInjectedCspNonce(SourceLocation location) {
    return new VarRefNode(
        CSP_NONCE_VARIABLE_NAME, location, true /*injected*/, IMPLICIT_CSP_NONCE_DEFN);
  }

  /** Builds the Soy command {@code {$ij.csp_nonce |escapeHtmlAttributeNospace}}. */
  private static PrintNode makeInjectedCspNoncePrintNode(
      SourceLocation location, IdGenerator idGenerator, EscapingMode escapeMode) {
    PrintNode printNode =
        new PrintNode.Builder(
                idGenerator.genId(),
                true, // Implicit.  {$ij.csp_nonce} not {print $ij.csp_nonce}
                location)
            .exprUnion(new ExprUnion(makeReferenceToInjectedCspNonce(location)))
            .build(SoyParsingContext.exploding());
    // Add an escaping directive to ensure that malicious csp_nonce values don't introduce XSSs
    printNode.addChild(
        new PrintDirectiveNode.Builder(idGenerator.genId(), escapeMode.directiveName, "", location)
            .build(SoyParsingContext.exploding()));
    return printNode;
  }
}
