/*
 * Copyright 2010 Google Inc.
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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.HtmlContext;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateNode;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test for {@link SlicedRawTextNode}.
 *
 */
@RunWith(JUnit4.class)
public final class SlicedRawTextNodeTest {

  /** Custom print directives used in tests below. */
  private static final ImmutableMap<String, SoyPrintDirective> SOY_PRINT_DIRECTIVES =
      ImmutableMap.of();

  @Test
  public void testTrivialTemplate() throws Exception {
    assertInjected(
        join("{template .foo}\n", "Hello, World!\n", "{/template}"),
        join("{template .foo}\n", "Hello, World!\n", "{/template}"));
  }

  @Test
  public void testOneScriptWithBody() throws Exception {
    assertInjected(
        join(
            "{template .foo}\n",
            "<script INJE='CTED'>alert('Hello, World!')</script>\n",
            "{/template}"),
        join("{template .foo}\n", "<script>alert('Hello, World!')</script>\n", "{/template}"));
  }

  @Test
  public void testOneSrcedScript() throws Exception {
    assertInjected(
        join("{template .foo}\n", "<script src=\"app.js\" INJE='CTED'></script>\n", "{/template}"),
        join("{template .foo}\n", "<script src=\"app.js\"></script>\n", "{/template}"));
  }

  @Test
  public void testManyScripts() throws Exception {
    assertInjected(
        join(
            "{template .foo}\n",
            "<script src=\"one.js\" INJE='CTED'></script>",
            "<script src=two.js INJE='CTED'></script>",
            "<script src=three.js  INJE='CTED'/></script>",
            "<h1>Not a script</h1>",
            "<script type='text/javascript' INJE='CTED'>main()</script>\n",
            "{/template}"),
        join(
            "{template .foo}\n",
            "<script src=\"one.js\"></script>",
            "<script src=two.js></script>",
            "<script src=three.js /></script>",
            "<h1>Not a script</h1>",
            "<script type='text/javascript'>main()</script>\n",
            "{/template}"));
  }

  @Test
  public void testFakeScripts() throws Exception {
    assertInjected(
        join(
            "{template .foo}\n",
            "<noscript></noscript>",
            "<script INJE='CTED'>alert('Hi');</script>",
            "<!-- <script>notAScript()</script> -->",
            "<textarea><script>notAScript()</script></textarea>",
            "<script is-script=yes>document.write('<script>not()<\\/script>');</script>",
            "<a href=\"//google.com/search?q=<script>hi()</script>\">Link</a>\n",
            "{/template}"),
        join(
            "{template .foo}\n",
            "<noscript></noscript>",
            // An actual script in a sea of imposters.
            "<script>alert('Hi');</script>",
            // Injecting a nonce into something that is not a script might be bad.
            "<!-- <script>notAScript()</script> -->",
            "<textarea><script>notAScript()</script></textarea>",
            "<script is-script=yes>document.write('<script>not()<\\/script>');</script>",
            "<a href=\"//google.com/search?q=<script>hi()</script>\">Link</a>\n",
            "{/template}"));
  }

  @Test
  public void testPrintDirectiveInScriptTag() throws Exception {
    assertInjected(
        join(
            "{template .foo}\n",
            "  {@param appScriptUrl: ?}\n",
            "<script src='{$appScriptUrl |filterTrustedResourceUri |escapeHtmlAttribute}' ",
            "INJE='CTED'>",
            "alert('Hello, World!')</script>\n",
            "{/template}"),
        join(
            "{template .foo}\n",
            "  {@param appScriptUrl: ?}\n",
            "<script src='{$appScriptUrl}'>",
            "alert('Hello, World!')</script>\n",
            "{/template}"));
  }

  @Test
  public void testContextAssumptionsUpheld() throws Exception {
    try {
      parseAndInjectIntoScriptTags(
          join("{template .foo}\n", "<script src='foo.js'></script>\n", "{/template}"),
          " title='unclosed");
    } catch (SoyAutoescapeException ex) {
      assertThat(ex)
          .hasMessage(
              "In file no-path:3:16, template ns.foo:"
                  + " Inserting ` title='unclosed` would cause text node to end in context"
                  + " (Context HTML_NORMAL_ATTR_VALUE SCRIPT PLAIN_TEXT SINGLE_QUOTE) instead of"
                  + " (Context HTML_PCDATA)");
      return;
    }
    fail("Expected SoyAutoescapeException");
  }

  @Test
  public void testMergeAdjacentSlicesWithSameContext() throws Exception {
    // Insert slices in a way that we end up with multiple adjacent slices with the
    // same context arranged thus:
    // Index   0 1 2 3 4 5 6 7 8 9 A B C D E F
    // Char    H e l l o ,   < W o r l d > !
    // Slice   0 0 0 0 1 1 1 2 2 2 2 3 5 5 6
    // Context a a a a a a a b b b b b b b a
    RawTextNode rawTextNode = new RawTextNode(0, "Hello, <World>!", SourceLocation.UNKNOWN);
    Context a = Context.HTML_PCDATA;
    Context b = Context.HTML_PCDATA.derive(HtmlContext.HTML_TAG_NAME);
    SlicedRawTextNode slicedNode = new SlicedRawTextNode(rawTextNode, a);
    slicedNode.insertSlice(0, a, 4); // "Hell"
    slicedNode.insertSlice(1, a, 3); // "o, "
    slicedNode.insertSlice(2, b, 4); // "<Wor"
    slicedNode.insertSlice(3, b, 1); // "l"
    slicedNode.insertSlice(4, b, 0); // ""
    slicedNode.insertSlice(5, b, 2); // "d>"
    slicedNode.insertSlice(6, a, 1); // "!"
    slicedNode.setEndContext(a);

    assertThat(slicedNode.getSlices()).hasSize(7);
    assertThat(slicesToString(slicedNode.getSlices()))
        .isEqualTo(
            "\"Hell\"#0:HTML_PCDATA, "
                + "\"o, \"#0:HTML_PCDATA, "
                + "\"<Wor\"#0:HTML_TAG_NAME, "
                + "\"l\"#0:HTML_TAG_NAME, "
                + "\"\"#0:HTML_TAG_NAME, "
                + "\"d>\"#0:HTML_TAG_NAME, "
                + "\"!\"#0:HTML_PCDATA");

    slicedNode.mergeAdjacentSlicesWithSameContext();

    assertThat(slicedNode.getSlices()).hasSize(3);
    assertThat(slicesToString(slicedNode.getSlices()))
        .isEqualTo(
            "\"Hello, \"#0:HTML_PCDATA, "
                + "\"<World>\"#0:HTML_TAG_NAME, "
                + "\"!\"#0:HTML_PCDATA");
  }

  /**
   * Renders slices to a comma separated list with elements like {@code
   * "<text>"#<node-id>:<context>}.
   */
  private static final String slicesToString(List<SlicedRawTextNode.RawTextSlice> slices) {
    StringBuilder sb = new StringBuilder();
    for (SlicedRawTextNode.RawTextSlice slice : slices) {
      if (sb.length() != 0) {
        sb.append(", ");
      }
      sb.append(slice);
      sb.append(":");
      sb.append(slice.context.state);
    }
    return sb.toString();
  }

  private static String join(String... lines) {
    return Joiner.on("").join(lines);
  }

  private SoyFileSetNode parseAndInjectIntoScriptTags(String input, String toInject) {
    String namespace = "{namespace ns autoescape=\"deprecated-contextual\"}\n\n";
    ErrorReporter boom = ExplodingErrorReporter.get();
    ParseResult parseResult =
        SoyFileSetParserBuilder.forFileContents(namespace + input).errorReporter(boom).parse();
    SoyFileSetNode soyTree = parseResult.fileSet();

    ContextualAutoescaper contextualAutoescaper = new ContextualAutoescaper(SOY_PRINT_DIRECTIVES);
    List<TemplateNode> extras =
        contextualAutoescaper.rewrite(soyTree, parseResult.registry(), boom);

    SoyFileNode file = soyTree.getChild(soyTree.numChildren() - 1);
    file.addChildren(file.numChildren(), extras);

    insertTextAtEndOfScriptOpenTag(contextualAutoescaper.getSlicedRawTextNodes(), toInject);
    return soyTree;
  }

  /**
   * Returns the contextually rewritten source.
   *
   * <p>The Soy tree may have multiple files, but only the source code for the first is returned.
   */
  private void assertInjected(String expectedOutput, String input) {
    SoyFileSetNode soyTree = parseAndInjectIntoScriptTags(input, " INJE='CTED'");

    StringBuilder src = new StringBuilder();
    src.append(soyTree.getChild(0).toSourceString());

    String output = src.toString().trim();
    if (output.startsWith("{namespace ns")) {
      output = output.substring(output.indexOf('}') + 1).trim();
    }

    assertThat(output).isEqualTo(expectedOutput);
  }

  private static void insertTextAtEndOfScriptOpenTag(
      List<SlicedRawTextNode> slicedRawTextNodes, String toInject) {
    Predicate<? super Context> inScriptTag =
        new Predicate<Context>() {
          @Override
          public boolean apply(Context c) {
            return (
            // In a script tag,
            c.elType == Context.ElementType.SCRIPT
                && c.state == HtmlContext.HTML_TAG
                // but not in an attribute
                && c.attrType == Context.AttributeType.NONE);
          }
        };
    Predicate<? super Context> inScriptBody =
        new Predicate<Context>() {
          @Override
          public boolean apply(Context c) {
            return (
            // If we're not in an attribute,
            c.attrType == Context.AttributeType.NONE
                // but we're in JS, then we must be in a script body.
                && c.state == HtmlContext.JS);
          }
        };

    for (SlicedRawTextNode.RawTextSlice slice :
        SlicedRawTextNode.find(slicedRawTextNodes, null, inScriptTag, inScriptBody)) {
      String rawText = slice.getRawText();
      int rawTextLen = rawText.length();
      assertThat(rawText.charAt(rawTextLen - 1)).isEqualTo('>');
      int insertionPoint = rawTextLen - 1;
      // Do not insert in the middle of a "/>" tag terminator.
      if (insertionPoint - 1 >= 0 && rawText.charAt(insertionPoint - 1) == '/') {
        --insertionPoint;
      }
      slice.insertText(insertionPoint, toInject);
    }
  }
}
