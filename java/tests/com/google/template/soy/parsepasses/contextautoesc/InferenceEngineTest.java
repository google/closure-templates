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

package com.google.template.soy.parsepasses.contextautoesc;

import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class InferenceEngineTest {

  @Test
  public void testPcData() {
    assertTransitions(
        SanitizedContentKind.HTML,
        "Hello <{assert('HTML_BEFORE_OPEN_TAG_NAME')} {assert('HTML_TAG NORMAL')}> "
            + "{assert('HTML_PCDATA')}");
    // Test special tags.
    assertTransitions(
        SanitizedContentKind.HTML,
        "<script type='text/javascript' {assert('HTML_TAG SCRIPT')}>{assert('JS REGEX')}</script>");
    assertTransitions(
        SanitizedContentKind.HTML,
        "<ScRipt type='text/javascript' {assert('HTML_TAG SCRIPT')}></script>");
    assertTransitions(
        SanitizedContentKind.HTML, "<style type='text/css' {assert('HTML_TAG STYLE')}></style>");
    assertTransitions(
        SanitizedContentKind.HTML, "<sTyLe type='text/css' {assert('HTML_TAG STYLE')}></style>");
    assertTransitions(
        SanitizedContentKind.HTML,
        "<textarea name='text' {assert('HTML_TAG TEXTAREA')}></textarea>");
    assertTransitions(
        SanitizedContentKind.HTML, "<title lang='en' {assert('HTML_TAG TITLE')}></title>");
    assertTransitions(SanitizedContentKind.HTML, "<xmp id='en' {assert('HTML_TAG XMP')}></xmp>");

    // attributes
    assertTransitions(
        SanitizedContentKind.HTML,
        "<a title='{assert('HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT SINGLE_QUOTE')}' "
            + "data-foo='{assert('HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT SINGLE_QUOTE')}' "
            + "onclick=\"{assert('JS NORMAL SCRIPT DOUBLE_QUOTE REGEX')}\" "
            + "ng-init=\"{assert('JS NORMAL SCRIPT DOUBLE_QUOTE REGEX')}\">");

    assertTransitions(
        SanitizedContentKind.HTML,
        "<a onclick=\"</script>{assert('JS_REGEX NORMAL SCRIPT DOUBLE_QUOTE')}\">");
    assertTransitions(
        SanitizedContentKind.HTML, "<xmp style=\"{assert('CSS XMP STYLE DOUBLE_QUOTE')}\"></xmp>");
    assertTransitions(
        SanitizedContentKind.HTML,
        "<xmp style=\'/*{assert('CSS_COMMENT XMP STYLE SINGLE_QUOTE')}*/\'></xmp>");
    assertTransitions(
        SanitizedContentKind.HTML,
        "<script src={assert('URI SCRIPT URI SPACE_OR_TAG_END START TRUSTED_RESOURCE')}></script>");
    assertTransitions(
        SanitizedContentKind.HTML,
        "<script src='/search?q={assert('URI SCRIPT URI SINGLE_QUOTE QUERY TRUSTED_RESOURCE')}'>"
            + "</script>");

    assertTransitions(
        SanitizedContentKind.HTML,
        "<script src='/foo#{assert('URI SCRIPT URI SINGLE_QUOTE FRAGMENT TRUSTED_RESOURCE')}'>"
            + "</script>");
    assertTransitions(
        SanitizedContentKind.HTML,
        "<img src={assert('URI MEDIA URI SPACE_OR_TAG_END START MEDIA')}>");
    assertTransitions(
        SanitizedContentKind.HTML,
        "<img url src={assert('URI MEDIA URI SPACE_OR_TAG_END START MEDIA')}>");
    // Make sure the URI type doesn't carry over if a URI has no attribute value.
    assertTransitions(
        SanitizedContentKind.HTML,
        "<img src href={assert('URI MEDIA URI SPACE_OR_TAG_END START NORMAL')}>");
    assertTransitions(
        SanitizedContentKind.HTML,
        "<img src alt={assert('HTML_NORMAL_ATTR_VALUE MEDIA PLAIN_TEXT SPACE_OR_TAG_END')}>");
    // TODO(gboyer): Consider supporting video, audio, and source.
    assertTransitions(
        SanitizedContentKind.HTML,
        "<video src={assert('URI NORMAL URI SPACE_OR_TAG_END START NORMAL')}>");
    assertTransitions(
        SanitizedContentKind.HTML,
        "<video><source src={assert('URI NORMAL URI SPACE_OR_TAG_END START NORMAL')}>");
    assertTransitions(
        SanitizedContentKind.HTML,
        "<audio src={assert('URI NORMAL URI SPACE_OR_TAG_END START NORMAL')}>");
    assertTransitions(
        SanitizedContentKind.HTML,
        "<source src={assert('URI NORMAL URI SPACE_OR_TAG_END START NORMAL')}>");
    assertTransitions(
        SanitizedContentKind.HTML,
        "<image xlink:href={assert('URI MEDIA URI SPACE_OR_TAG_END START MEDIA')}>");
    assertTransitions(
        SanitizedContentKind.HTML,
        "<a href=mailto:{assert('URI NORMAL URI SPACE_OR_TAG_END AUTHORITY_OR_PATH NORMAL')}>");
    assertTransitions(
        SanitizedContentKind.HTML,
        "<input type=button value='' onclick={assert('JS NORMAL SCRIPT SPACE_OR_TAG_END REGEX')}>");
    assertTransitions(
        SanitizedContentKind.HTML, "<input type=button value=''>{assert('HTML_PCDATA')}");
  }

  @Test
  public void testTags() throws Exception {
    assertTransitions(
        SanitizedContentKind.HTML,
        join(
            "<{assert('HTML_BEFORE_OPEN_TAG_NAME')}>",
            "</{assert('HTML_BEFORE_CLOSE_TAG_NAME')}>",
            "<svg:font-fact "
                + "id='{assert('HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT SINGLE_QUOTE')}'/>",
            "<textarea>{assert('HTML_RCDATA TEXTAREA')}</textarea>",
            "<title>{assert('HTML_RCDATA TITLE')}</title>",
            "<script>{assert('JS REGEX')}</script>",
            "<style>{assert('CSS')}</style>",
            "<xmp>{assert('HTML_RCDATA XMP')}</xmp>"));
    assertTransitions(
        SanitizedContentKind.ATTRIBUTES,
        join(
            "{assert('HTML_TAG')} ",
            "checked ",
            "{assert('HTML_TAG')} ",
            "xlink:href={assert('URI URI SPACE_OR_TAG_END START NORMAL')} ",
            "g:url={assert('URI URI SPACE_OR_TAG_END START NORMAL')} ",
            "g:hourly ",
            "{assert('HTML_TAG')} ",
            "xmlnsxyz ",
            "{assert('HTML_TAG')} ",
            "xmlnsxyz? ",
            "{assert('HTML_TAG')} ",
            "xml?nsxyz ",
            "{assert('HTML_TAG')} ",
            "xmlnsxyz$ ",
            "{assert('HTML_TAG')} ",
            "xml$nsxyz ",
            "{assert('HTML_TAG')} ",
            "xmlns:foo='{assert('URI URI SINGLE_QUOTE START NORMAL')}' ",
            "svg:style='{assert('CSS STYLE SINGLE_QUOTE')}'",
            ""));
  }

  @Test
  public void testAttrName() throws Exception {
    assertTransitions(
        SanitizedContentKind.HTML,
        join(
            "<xmp xml:url={assert('URI XMP URI SPACE_OR_TAG_END START NORMAL')}></xmp>",
            "<xmp {assert('HTML_TAG XMP')}='foo'></xmp>",
            "<textarea {assert('HTML_TAG TEXTAREA')} = 'foo'></textarea>",
            "<div {assert('HTML_TAG NORMAL')} = 'foo'>",
            ""));
  }

  @Test
  public void testAttrValue() throws Exception {
    assertTransitions(
        SanitizedContentKind.HTML,
        join(
            "<a href={assert('URI NORMAL URI SPACE_OR_TAG_END START NORMAL')}>",
            "<a href='{assert('URI NORMAL URI SINGLE_QUOTE START NORMAL')}'>",
            "<a href=\"{assert('URI NORMAL URI DOUBLE_QUOTE START NORMAL')}\">",
            "<a onclick={assert('JS NORMAL SCRIPT SPACE_OR_TAG_END REGEX')}>",
            "<a onclick='{assert('JS NORMAL SCRIPT SINGLE_QUOTE REGEX')}'>",
            "<a onclick=\"{assert('JS NORMAL SCRIPT DOUBLE_QUOTE REGEX')}\">",
            "<a style={assert('CSS NORMAL STYLE SPACE_OR_TAG_END')}>",
            "<a style='{assert('CSS NORMAL STYLE SINGLE_QUOTE')}'>",
            "<a style=\"{assert('CSS NORMAL STYLE DOUBLE_QUOTE')}\">",
            "<a data-foo={assert('HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT SPACE_OR_TAG_END')}>",
            "<a data-foo='{assert('HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT SINGLE_QUOTE')}'>",
            "<a data-foo=\"{assert('HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT DOUBLE_QUOTE')}\">",
            "{assert('HTML_PCDATA')}",
            ""));
  }

  @Test
  public void testLink() throws Exception {
    assertTransitions(
        SanitizedContentKind.HTML,
        join(
            "<link {assert('HTML_TAG LINK_EXECUTABLE')}>",
            "<link rel='stylesheet' {assert('HTML_TAG LINK_EXECUTABLE')}>",
            "<link rel='import' {assert('HTML_TAG LINK_EXECUTABLE')}>",
            "<link rel='shortcut icon' {assert('HTML_TAG NORMAL')}>",
            "{let $rel: 'icon'/}<link rel='{$rel}' {assert('HTML_TAG LINK_EXECUTABLE')}>",
            ""));
  }

  @Test
  public void testCss() throws Exception {
    assertTransitions(
        SanitizedContentKind.CSS,
        join(
            "{assert('CSS')} p {lb} color: red; {rb} {assert('CSS')} ",
            "p.clazz#id {lb}{\\n}  border: 2px;{\\n}{rb} {assert('CSS')}",
            "'{assert('CSS_SQ_STRING')}' \"{assert('CSS_DQ_STRING')}\"",
            ""));
  }

  @Test
  public void testJs() {
    assertTransitions(
        SanitizedContentKind.HTML,
        join(
            "<script>'{assert('JS_SQ_STRING')}</script>{assert('HTML_PCDATA')}",
            "<script>\"{assert('JS_DQ_STRING')}</script>{assert('HTML_PCDATA')}",
            "<script>/{assert('JS_REGEX')}</script>{assert('HTML_PCDATA')}",
            "<script>''/{assert('JS REGEX')}</script>{assert('HTML_PCDATA')}",
            ""));
  }

  @Test
  public void testRcdata() throws Exception {
    assertTransitions(
        SanitizedContentKind.HTML,
        "<xmp>"
            + "{assert('HTML_RCDATA XMP')} foo {assert('HTML_RCDATA XMP')} "
            + "<div class='{assert('HTML_RCDATA XMP')}'>"
            + "<{assert('HTML_RCDATA XMP')}>"
            + "</textarea>"
            + "{assert('HTML_RCDATA XMP')}"
            + "</xmp>");
  }

  @Test
  public void testHtmlComment() throws Exception {
    assertTransitions(
        SanitizedContentKind.HTML,
        "{assert('HTML_PCDATA')}"
            + "<!--\n"
            + "  {assert('HTML_COMMENT')}"
            + "  <script>\n"
            + "    {assert('HTML_COMMENT')}"
            + "    console.log('Hello');\n"
            + "    {assert('HTML_COMMENT')}"
            + "  </script>\n"
            + "  {assert('HTML_COMMENT')}\n"
            + "-->"
            + "{assert('HTML_PCDATA')}");
  }

  @Test
  public void testTemplateElementNesting() throws Exception {
    assertTransitions(
        SanitizedContentKind.HTML,
        join(
            "<template>",
            "{assert('HTML_PCDATA templateNestDepth=1')}",
            "<template>",
            "{assert('HTML_PCDATA templateNestDepth=2')}",
            "</template>",
            "{assert('HTML_PCDATA templateNestDepth=1')}",
            "<script>",
            "{assert('JS REGEX templateNestDepth=1')}",
            "</template>",
            "</script>",
            "</template>",
            "{assert('HTML_PCDATA')}",
            ""));
  }

  private static final class AssertFunction implements SoyFunction {
    @Override
    public String getName() {
      return "assert";
    }

    @Override
    public ImmutableSet<Integer> getValidArgsSizes() {
      return ImmutableSet.of(1);
    }
  }

  private static void assertTransitions(SanitizedContentKind kind, String src) {
    ParseResult result =
        SoyFileSetParserBuilder.forFileContents(
                "{namespace ns}\n{template .foo"
                    + (kind == SanitizedContentKind.HTML
                        ? ""
                        : " kind=\"" + Ascii.toLowerCase(kind.toString()) + '"')
                    + " stricthtml=\"false\"}"
                    + src
                    + "{/template}")
            .addSoyFunction(new AssertFunction())
            // typically the default is what we want but in this case disable desugaring so the
            // html nodes are preserved and the autoescaper can see them
            .desugarHtmlNodes(false)
            .parse();
    Inferences inferences = new Inferences(result.registry());
    TemplateNode template = result.fileSet().getChild(0).getChild(0);
    InferenceEngine.inferTemplateEndContext(
        template,
        Context.getStartContextForContentKind(kind),
        inferences,
        ErrorReporter.exploding());
    for (PrintNode print : SoyTreeUtils.getAllNodesOfType(template, PrintNode.class)) {
      if (print.getExpr().getChild(0) instanceof FunctionNode) {
        FunctionNode fn = (FunctionNode) print.getExpr().getChild(0);
        if (fn.getSoyFunction() instanceof AssertFunction) {
          assertWithMessage(
                  "expected print node at " + print.getSourceLocation() + " to have context")
              .that(contextString(inferences.getContextForNode(print)))
              .isEqualTo(((StringNode) fn.getChild(0)).getValue());
        }
      }
    }
  }

  private static String contextString(Context ctx) {
    String s = ctx.toString();
    return s.substring("(Context ".length(), s.length() - 1);
  }

  private static String join(String... strings) {
    return Joiner.on("").join(strings);
  }
}
