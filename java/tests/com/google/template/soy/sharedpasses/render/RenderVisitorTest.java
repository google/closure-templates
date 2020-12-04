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

package com.google.template.soy.sharedpasses.render;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.Futures;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyAbstractValue;
import com.google.template.soy.data.SoyDict;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverterUtility;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.internal.MsgUtils;
import com.google.template.soy.msgs.restricted.SoyMsg;
import com.google.template.soy.msgs.restricted.SoyMsgBundleImpl;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.SoyGeneralOptions;
import com.google.template.soy.shared.SoyIdRenamingMap;
import com.google.template.soy.shared.internal.DelTemplateSelector;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.testing.SharedTestUtils;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import com.google.template.soy.testing.TestAnnotations.ExperimentalFeatures;
import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.Flushable;
import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for RenderVisitor.
 *
 */
@RunWith(JUnit4.class)
public class RenderVisitorTest {

  private static final SoyRecord TEST_DATA;

  private Description testDescription;

  @Rule
  public final TestWatcher testWatcher =
      new TestWatcher() {
        @Override
        protected void starting(Description description) {
          testDescription = description;
        }
      };

  static {
    SoyList tri = SoyValueConverterUtility.newList(1, 3, 6, 10, 15, 21);
    TEST_DATA =
        SoyValueConverterUtility.newDict(
            "boo",
            8,
            "foo.bar",
            "baz",
            "foo.goo2",
            tri,
            "goo",
            tri,
            "moo",
            3.14,
            "t",
            true,
            "f",
            false,
            "map0",
            SoyValueConverterUtility.newDict(),
            "list0",
            SoyValueConverterUtility.newList(),
            "list1",
            SoyValueConverterUtility.newList(1, 2, 3),
            "component",
            "comp",
            "plainText",
            "<plaintext id=foo>",
            "sanitizedContent",
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                "<plaintext id=foo>", SanitizedContent.ContentKind.HTML),
            "greekA",
            "alpha",
            "greekB",
            "beta",
            "toStringTestValue",
            createToStringTestValue());
  }

  private static final SoyRecord TEST_IJ_DATA =
      SoyValueConverterUtility.newDict("ijBool", true, "ijInt", 26, "ijStr", "injected");

  private static final SoyIdRenamingMap TEST_XID_RENAMING_MAP =
      new SoyIdRenamingMap() {
        @Nullable
        @Override
        public String get(String key) {
          return key + "_id_renamed";
        }
      };

  private static final SoyCssRenamingMap TEST_CSS_RENAMING_MAP =
      new SoyCssRenamingMap() {
        @Nullable
        @Override
        public String get(String key) {
          return key + "_renamed";
        }
      };

  private static final ErrorReporter FAIL = ErrorReporter.exploding();

  private SoyIdRenamingMap xidRenamingMap = null;
  private SoyCssRenamingMap cssRenamingMap = null;

  /**
   * Asserts that the given input string (should be a template body) renders to the given result.
   *
   * @param templateBody The input string to render (should be a template body).
   * @param result The expected rendered result.
   * @throws Exception If the assertion is not true or if there's an error.
   */
  private void assertRender(String templateBody, String result) throws Exception {
    assertThat(render(templateBody)).isEqualTo(result);
  }

  /**
   * Asserts that the given input string (should be a template body) renders to the given result.
   *
   * @param templateBody The input string to render (should be a template body).
   * @param data The SoyRecord data used for testing.
   * @param result The expected rendered result.
   * @throws Exception If the assertion is not true or if there's an error.
   */
  private void assertRenderWithData(String templateBody, SoyRecord data, String result)
      throws Exception {
    assertThat(renderWithData(templateBody, data)).isEqualTo(result);
  }

  /**
   * Asserts that the given input string (should be a template body) renders to the given result.
   *
   * @param templateBody The input string to render (should be a template body).
   * @param errorMessage The expected error message.
   * @throws Exception If an expected exception is not thrown, or the error message is different
   *     from expected.
   */
  private void assertRenderException(String templateBody, String errorMessage) throws Exception {
    assertRenderExceptionWithDataAndMsgBundle(templateBody, TEST_DATA, null, errorMessage);
  }

  /**
   * Asserts that the given input string (should be a template body) renders to the given result.
   *
   * @param templateBody The input string to render (should be a template body).
   * @param data The SoyRecord data used for testing.
   * @param errorMessage The expected error message.
   * @throws Exception If an expected exception is not thrown, or the error message is different
   *     from expected.
   */
  private void assertRenderExceptionWithData(
      String templateBody, SoyRecord data, String errorMessage) throws Exception {
    assertRenderExceptionWithDataAndMsgBundle(templateBody, data, null, errorMessage);
  }

  /**
   * Asserts that the given input string (should be a template body) renders to the given result.
   *
   * @param templateBody The input string to render (should be a template body).
   * @param data The SoyRecord data used for testing.
   * @param msgBundle The bundle of translated messages.
   * @param errorMessage The expected error message.
   * @throws Exception If an expected exception is not thrown, or the error message is different
   *     from expected.
   */
  private void assertRenderExceptionWithDataAndMsgBundle(
      String templateBody, SoyRecord data, @Nullable SoyMsgBundle msgBundle, String errorMessage)
      throws Exception {
    try {
      String result = renderWithDataAndMsgBundle(templateBody, data, msgBundle);
      fail(
          "Invalid template body didn't throw exception. Template body was:\n"
              + templateBody
              + "\n result was:\n"
              + result);
    } catch (RenderException e) {
      assertThat(e).hasMessageThat().contains(errorMessage);
    }
  }

  /**
   * Renders the given input string (should be a template body) and returns the result.
   *
   * @param templateBody The input string to render (should be a template body).
   * @return The rendered result.
   * @throws Exception If there's an error.
   */
  private String render(String templateBody) throws Exception {
    return renderWithData(templateBody, TEST_DATA);
  }

  /**
   * Renders the given input string (should be a template body) and returns the result.
   *
   * @param templateBody The input string to render (should be a template body).
   * @param data The soy data as a map of variables to objects.
   * @return The rendered result.
   * @throws Exception If there's an error.
   */
  private String renderWithData(String templateBody, SoyRecord data) throws Exception {
    return renderWithDataAndMsgBundle(templateBody, data, null);
  }

  /**
   * Renders the given input string (should be a template body) and returns the result.
   *
   * @param templateBody The input string to render (should be a template body).
   * @param data The soy data as a map of variables to objects.
   * @param msgBundle The bundle of translated messages.
   * @return The rendered result.
   * @throws Exception If there's an error.
   */
  private String renderWithDataAndMsgBundle(
      String templateBody, SoyRecord data, @Nullable SoyMsgBundle msgBundle) throws Exception {

    ErrorReporter boom = ErrorReporter.exploding();
    ExperimentalFeatures experimentalFeatures =
        testDescription.getAnnotation(ExperimentalFeatures.class);
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forTemplateContents(templateBody)
            .enableExperimentalFeatures(
                experimentalFeatures == null
                    ? ImmutableList.of()
                    : ImmutableList.copyOf(experimentalFeatures.value()))
            .errorReporter(boom)
            .parse()
            .fileSet();
    TemplateNode templateNode = (TemplateNode) SharedTestUtils.getNode(soyTree);

    StringBuilder outputSb = new StringBuilder();
    RenderVisitor rv =
        new RenderVisitor(
            new EvalVisitorFactoryImpl(),
            outputSb,
            ImmutableMap.of(),
            new DelTemplateSelector.Builder<TemplateDelegateNode>().build(),
            data,
            TEST_IJ_DATA,
            arg -> false,
            msgBundle,
            xidRenamingMap,
            cssRenamingMap,
            false,
            /* pluginInstances= */ ImmutableMap.of());
    rv.exec(templateNode);
    return outputSb.toString();
  }

  private String renderTemplateInFile(
      String soyFileContent,
      String templateName,
      SoyRecord data,
      SoyRecord ijData,
      Predicate<String> activeDelPackageNames) {
    return renderTemplateInFile(
        SoyFileSetParserBuilder.forFileContents(soyFileContent).errorReporter(FAIL).parse(),
        templateName,
        data,
        ijData,
        activeDelPackageNames);
  }

  private String renderTemplateInFile(
      ParseResult parseResult,
      String templateName,
      SoyRecord data,
      SoyRecord ijData,
      Predicate<String> activeDelPackageNames) {
    return renderTemplateInFile(
        parseResult, templateName, data, ijData, activeDelPackageNames, new StringBuilder());
  }

  private String renderTemplateInFile(
      ParseResult parseResult,
      String templateName,
      SoyRecord data,
      SoyRecord ijData,
      Predicate<String> activeDelPackageNames,
      StringBuilder outputSb) {
    ImmutableMap<String, TemplateNode> basicTemplates = getBasicTemplates(parseResult.fileSet());
    RenderVisitor rv =
        new RenderVisitor(
            new EvalVisitorFactoryImpl(),
            outputSb,
            basicTemplates,
            getDeltemplateSelector(parseResult.fileSet()),
            data,
            ijData,
            activeDelPackageNames,
            null,
            xidRenamingMap,
            cssRenamingMap,
            false,
            /* pluginInstances= */ ImmutableMap.of());
    TemplateNode templateNode = basicTemplates.get(templateName);
    rv.exec(templateNode);
    return outputSb.toString();
  }

  // -----------------------------------------------------------------------------------------------
  // Tests begin here.

  @Test
  public void testRenderRawText() throws Exception {
    String templateBody =
        "  {sp} aaa bbb  \n"
            + "  ccc {lb}{rb} ddd {\\n}\n"
            + "  {literal}eee\n"
            + "fff }{  {/literal}  \n"
            + "  \u2222\uEEEE\u9EC4\u607A\n";

    assertRender(templateBody, "  aaa bbb ccc {} ddd \neee\nfff }{  \u2222\uEEEE\u9EC4\u607A");
  }

  @Test
  public void testRenderComments() throws Exception {
    String templateBody =
        "  {sp}  // {sp}\n"
            + // first {sp} outside of comments
            "  /* {sp} {sp} */  // {sp}\n"
            + "  /* {sp} */{sp}/* {sp} */\n"
            + // middle {sp} outside of comments
            "  /* {sp}\n"
            + "  {sp} */{sp}\n"
            + // last {sp} outside of comments
            "  // {sp} /* {sp} */\n";

    assertRender(templateBody, "   ");
  }

  @Test
  public void testRenderPrintStmt() throws Exception {
    String templateBody =
        "{@param foo : ? }\n"
            + "{@param boo : ? }\n"
            + "{@param f : ? }\n"
            + "{@param goo : ? }\n"
            + "{@param undefined : ? }\n"
            + "{@param toStringTestValue : ? }\n"
            + "{@inject ijStr : ? }\n"
            + "  {$boo} {$foo.bar}{sp}\n"
            + "  {$ijStr}\n"
            + "  {$goo[5] + 1}{sp}\n"
            + "  {$f ?: ''} {$undefined ?: -1}{sp}\n"
            + "  {' blah aablahblahblah' |insertWordBreaks:8}{sp}\n"
            + "  {$toStringTestValue}\n";

    assertRender(
        templateBody, "8 baz injected22 false -1  blah aablahbl<wbr>ahblah coerceToString()");
  }

  @Test
  public void testRenderMsgStmt() throws Exception {
    String templateBody =
        "{@param foo: ?}\n"
            + "{@inject ijInt: ?}\n"
            + "  {msg desc=\"Tells user's quota usage.\"}\n"
            + "    You're currently using {$ijInt} MB of your quota.{sp}\n"
            + "    <a href=\"{$foo.bar}\">Learn more</A>\n"
            + "    <br /><br />\n"
            + "  {/msg}\n"
            + "  {msg meaning=\"noun\" desc=\"\" hidden=\"true\"}Archive{/msg}\n"
            + "  {msg meaning=\"noun\" desc=\"The archive (noun).\"}Archive{/msg}\n"
            + "  {msg meaning=\"verb\" desc=\"\"}Archive{/msg}\n"
            + "  {msg desc=\"\"}Archive{/msg}\n";

    assertRender(
        templateBody,
        "You're currently using 26 MB of your quota. "
            + "<a href=\"baz\">Learn more</A>"
            + "<br/><br/>"
            + "ArchiveArchiveArchiveArchive");
  }

  @Test
  public void testRenderMsgStmtWithFallback() throws Exception {
    String templateBody =
        ""
            + "  {msg desc=\"\"}\n"
            + "    blah\n"
            + "  {fallbackmsg desc=\"\"}\n"
            + "    bleh\n"
            + "  {/msg}\n";

    // Without msg bundle.
    assertRender(templateBody, "blah");

    // With msg bundle.
    SoyFileNode file =
        SoyFileSetParserBuilder.forFileContents(
                "{namespace test}\n{template .foo}\n" + templateBody + "{/template}")
            .parse()
            .fileSet()
            .getChild(0);

    MsgFallbackGroupNode msgGroup =
        SoyTreeUtils.getAllNodesOfType(file.getChild(0), MsgFallbackGroupNode.class).get(0);
    MsgNode fallbackMsg = msgGroup.getChild(1);
    SoyMsg translatedFallbackMsg =
        SoyMsg.builder()
            .setId(MsgUtils.computeMsgIdForDualFormat(fallbackMsg))
            .setLocaleString("x-zz")
            .setParts(ImmutableList.<SoyMsgPart>of(SoyMsgRawTextPart.of("zbleh")))
            .build();
    SoyMsgBundle msgBundle =
        new SoyMsgBundleImpl("x-zz", Lists.newArrayList(translatedFallbackMsg));
    assertThat(renderWithDataAndMsgBundle(templateBody, TEST_DATA, msgBundle)).isEqualTo("zbleh");
  }

  @Test
  public void testRenderSimpleSelect() throws Exception {
    String templateBody =
        "{@param person: ?}\n"
            + "{@param gender: ?}\n"
            + "  {msg desc=\"Simple select message\"}\n"
            + "    {select $gender}\n"
            + "      {case 'female'}{$person} shared her photos.\n"
            + "      {default}{$person} shared his photos.\n"
            + "    {/select}\n"
            + "  {/msg}\n";

    SoyDict data = SoyValueConverterUtility.newDict("person", "The president", "gender", "female");
    assertRenderWithData(templateBody, data, "The president shared her photos.");

    data = SoyValueConverterUtility.newDict("person", "The president", "gender", "male");
    assertRenderWithData(templateBody, data, "The president shared his photos.");
  }

  @Test
  public void testRenderSimplePlural() throws Exception {
    String templateBody =
        "{@param n_people: ?}\n"
            + "{@param person: ?}\n"
            + "  {msg desc=\"Simple plural message\"}\n"
            + "    {plural $n_people offset=\"1\"}\n"
            + "      {case 0}Nobody shared photos.\n"
            + "      {case 1}Only {$person} shared photos.\n"
            + "      {default}{$person} and {remainder($n_people)} others shared photos.\n"
            + "    {/plural}\n"
            + "  {/msg}\n";

    SoyDict data = SoyValueConverterUtility.newDict("person", "Bob", "n_people", 0);
    assertRenderWithData(templateBody, data, "Nobody shared photos.");

    data = SoyValueConverterUtility.newDict("person", "Bob", "n_people", 1);
    assertRenderWithData(templateBody, data, "Only Bob shared photos.");

    data = SoyValueConverterUtility.newDict("person", "Bob", "n_people", 10);
    assertRenderWithData(templateBody, data, "Bob and 9 others shared photos.");
  }

  @Test
  public void testRenderNestedSelects() throws Exception {
    String templateBody =
        "{@param gender1: ?}\n"
            + "{@param gender2: ?}\n"
            + "{@param person1: ?}\n"
            + "{@param person2: ?}\n"
            + "  {msg desc=\"Nested selects\"}\n"
            + "     {select $gender1}\n"
            + "      {case 'female'}\n"
            + "        {select $gender2}\n"
            + "          {case 'female'}\n"
            + "             {$person1} shared her photos with {$person2} and her friends.\n"
            + "          {default}\n"
            + "             {$person1} shared her photos with {$person2} and his friends.\n"
            + "        {/select}\n"
            + "      {default}\n"
            + "        {select $gender2}\n"
            + "          {case 'female'}"
            + "              {$person1} shared his photos with {$person2} and her friends.\n"
            + "          {default}"
            + "              {$person1} shared his photos with {$person2} and his friends.\n"
            + "        {/select}\n"
            + "    {/select}\n"
            + "  {/msg}\n";

    SoyDict data =
        SoyValueConverterUtility.newDict(
            "person1", "Alice", "gender1", "female", "person2", "Lara", "gender2", "female");
    assertRenderWithData(templateBody, data, "Alice shared her photos with Lara and her friends.");

    data =
        SoyValueConverterUtility.newDict(
            "person1", "Alice", "gender1", "female", "person2", "Mark", "gender2", "male");
    assertRenderWithData(templateBody, data, "Alice shared her photos with Mark and his friends.");

    data =
        SoyValueConverterUtility.newDict(
            "person1", "Bob", "gender1", "male", "person2", "Mark", "gender2", "male");
    assertRenderWithData(
        templateBody, data, "              Bob shared his photos with Mark and his friends.");

    data =
        SoyValueConverterUtility.newDict(
            "person1", "Bob", "gender1", "male", "person2", "Lara", "gender2", "female");
    assertRenderWithData(
        templateBody, data, "              Bob shared his photos with Lara and her friends.");
  }

  @Test
  public void testRenderPluralNestedInSelect() throws Exception {
    String templateBody =
        "  {@param person : ?}\n"
            + "  {@param n_people : ?}\n"
            + "  {@param gender : ?}\n"
            + "  {msg desc=\"Plural nested inside select\"}\n"
            + "    {select $gender}\n"
            + "      {case 'female'}\n"
            + "        {plural $n_people}\n"
            + "          {case 0}{$person} added nobody to her circle.\n"
            + "          {case 1}{$person} added one person to her circle.\n"
            + "          {default}{$person} added {$n_people} people to her circle.\n"
            + "        {/plural}\n"
            + "      {default}\n"
            + "        {plural $n_people}\n"
            + "          {case 0}{$person} added nobody to his circle.\n"
            + "          {case 1}{$person} added one person to his circle.\n"
            + "          {default}{$person} added {$n_people} people to his circle.\n"
            + "        {/plural}\n"
            + "   {/select}\n"
            + "  {/msg}\n";

    SoyDict data =
        SoyValueConverterUtility.newDict("person", "Alice", "gender", "female", "n_people", 0);
    assertRenderWithData(templateBody, data, "Alice added nobody to her circle.");

    data = SoyValueConverterUtility.newDict("person", "Alice", "gender", "female", "n_people", 1);
    assertRenderWithData(templateBody, data, "Alice added one person to her circle.");

    data = SoyValueConverterUtility.newDict("person", "Alice", "gender", "female", "n_people", 10);
    assertRenderWithData(templateBody, data, "Alice added 10 people to her circle.");

    data = SoyValueConverterUtility.newDict("person", "Bob", "gender", "male", "n_people", 10);
    assertRenderWithData(templateBody, data, "Bob added 10 people to his circle.");
  }

  @Test
  public void testRenderPlrselComplexConstructs() throws Exception {
    String templateBody =
        "  {@param person : [gender: string, name: string]}\n"
            + "  {@param invitees : list<string>}\n"
            + "  {msg desc=\"[ICU Syntax] A sample nested message with complex constructs\"}\n"
            + "    {select $person.gender}\n"
            + "      {case 'female'}\n"
            + "        {plural length($invitees) offset=\"1\"}\n"
            + "          {case 0}{$person.name} added nobody to her circle.\n"
            + "          {case 1}{$person.name} added {$invitees[0]} to her circle.\n"
            + "          {default}{$person.name} added {$invitees[0]} and "
            + "{remainder(length($invitees))} others to her circle.\n"
            + "        {/plural}\n"
            + "      {default}\n"
            + "        {plural length($invitees) offset=\"1\"}\n"
            + "          {case 0}{$person.name} added nobody to his circle.\n"
            + "          {case 1}{$person.name} added {$invitees[0]} to his circle.\n"
            + "          {default}{$person.name} added {$invitees[0]} and "
            + "{remainder(length($invitees))} others to his circle.\n"
            + "        {/plural}\n"
            + "    {/select}\n"
            + "  {/msg}\n";

    SoyDict data =
        SoyValueConverterUtility.newDict(
            "person", SoyValueConverterUtility.newDict("name", "Alice", "gender", "female"),
            "invitees", SoyValueConverterUtility.newList("Anna", "Brent", "Chris", "Darin"));
    assertRenderWithData(templateBody, data, "Alice added Anna and 3 others to her circle.");

    data =
        SoyValueConverterUtility.newDict(
            "person", SoyValueConverterUtility.newDict("name", "Bob", "gender", "male"),
            "invitees", SoyValueConverterUtility.newList("Anna", "Brent", "Chris", "Darin"));
    assertRenderWithData(templateBody, data, "Bob added Anna and 3 others to his circle.");
  }

  @Test
  public void testRenderPluralWithEmbeddedHtmlElements() throws Exception {
    /**
     * Link to open up the email options dialog.
     *
     * @param num {number} Number of people who will be notified via email.
     */
    String templateBody =
        "{@param num: ?}\n"
            + "{msg desc=\"[ICU Syntax] Explanatory text saying that with current\n"
            + "     settings, $num people will be notified via email\"}\n"
            + "   {plural $num}\n"
            + "     {case 0}\n"
            + "         Notify people via email &rsaquo;\n"
            + "     {case 1}\n"
            + "         Notify{sp}\n"
            + "         <span class=\"{css('sharebox-id-email-number')}\">{$num}</span>{sp}\n"
            + "         person via email &rsaquo;\n"
            + "     {default}\n"
            + "         Notify{sp}\n"
            + "         <span class=\"{css('sharebox-id-email-number')}\">{$num}</span>{sp}\n"
            + "         people via email &rsaquo;\n"
            + "   {/plural}\n"
            + " {/msg}\n";

    SoyDict data = SoyValueConverterUtility.newDict("num", 1);
    assertRenderWithData(
        templateBody,
        data,
        "Notify <span class=\"sharebox-id-email-number\">1</span> person via email &rsaquo;");

    data = SoyValueConverterUtility.newDict("num", 10);
    assertRenderWithData(
        templateBody,
        data,
        "Notify <span class=\"sharebox-id-email-number\">10</span> people via email &rsaquo;");
  }


  @Test
  public void testRenderPluralWithRuntimeErrors() throws Exception {
    String templateBody =
        "{@param n_people: ?}\n"
            + "{@param person: ?}\n"
            + "  {msg desc=\"Simple plural message\"}\n"
            + "    {plural $n_people offset=\"1\"}\n"
            + "      {case 0}Nobody shared photos.\n"
            + "      {case 1}Only {$person} shared photos.\n"
            + "      {default}{$person} and {remainder($n_people)} others shared photos.\n"
            + "    {/plural}\n"
            + "  {/msg}\n";

    SoyDict data = SoyValueConverterUtility.newDict("person", "Bob", "n_people", "nobody");
    assertRenderExceptionWithData(
        templateBody, data, "Plural expression \"$n_people\" doesn't evaluate to number.");
  }

  @Test
  public void testRenderLetStmt() throws Exception {
    String templateBody =
        "{@param foo: ?}\n"
            + "  {let $alpha: $foo.goo2[1] /}\n"
            + "  {let $beta kind=\"text\"}Boo!{/let}\n"
            + "  {let $gamma kind=\"text\"}\n"
            + "    {for $i in range($alpha)}\n"
            + "      {$i}{$beta}\n"
            + "    {/for}\n"
            + "  {/let}\n"
            + "  {$alpha}{$beta}{$gamma}\n";

    assertRender(templateBody, "3Boo!0Boo!1Boo!2Boo!");
  }

  @Test
  public void testRenderIfStmt() throws Exception {
    String templateBody =
        "{@param boo: ?}\n"
            + "{@param goo: ?}\n"
            + "{@param moo: ?}\n"
            + "{@param f: ?}\n"
            + "{@inject ijBool: ?}\n"
            + "  {if $boo}{$boo}{/if}\n"
            + "  {if ''}-{else}+{/if}\n"
            + "  {if $f ?: 0.0}\n"
            + "    Blah\n"
            + "  {elseif $goo[2] > 2 and $ijBool}\n"
            + "    {$moo}\n"
            + "  {else}\n"
            + "    Blah {$moo}\n"
            + "  {/if}\n";

    assertRender(templateBody, "8+3.14");
  }

  @Test
  public void testRenderSwitchStmt() throws Exception {
    String templateBody =
        "{@param foo: ?}\n"
            + "{@param boo: ?}\n"
            + "{@param goo: ?}\n"
            + "{@param t: ?}\n"
            + "  {switch $boo} {case 0}Blah\n"
            + "    {case $goo[1]+1}\n"
            + "      Bleh\n"
            + "    {case -1, 1, $goo[2]+2}\n"
            + "      Bluh\n"
            + "    {default}\n"
            + "      Bloh\n"
            + "  {/switch}{sp}\n"
            + "  {switch $foo.bar}{case 'baz',$boo}baz{default}zab{/switch}\n"
            + "  {switch true}{case not $t}daz{default}zad{/switch}\n";

    assertRender(templateBody, "Bluh bazzad");
  }

  @Test
  public void testRenderForStmt1() throws Exception {
    String templateBody =
        ""
            + "{@param goo : list<?> }\n"
            + "{@param list0 : list<?> }\n"
            + "{@param foo : ? }\n"
            + "{@param boo : ? }\n"
            + "  {for $n in $goo}\n"
            + "    {if not isFirst($n)}{\\n}{/if}\n"
            + "    {$n} = Sum of 1 through {index($n) + 1}.\n"
            + "  {/for}\n"
            + "  {\\n}\n"
            + "  {for $i in $goo}\n"
            + "    {for $j in $foo.goo2}\n"
            + "      {if $i == $j} {$i + $j}{/if}\n"
            + "    {/for}\n"
            + "  {/for}\n"
            + "  {sp}\n"
            + "  {for $item in $list0}\n"
            + "    Blah\n"
            + "  {ifempty}\n"
            + "    Bluh\n"
            + "  {/for}\n"
            + "  {for $item in $list0}\n"
            + "    Blah\n"
            + "  {/for}\n"
            + "  {for $item in ['blah', 123, $boo]}\n"
            + "    {sp}{$item}\n"
            + "  {/for}\n";

    assertRender(
        templateBody,
        ""
            + "1 = Sum of 1 through 1.\n"
            + "3 = Sum of 1 through 2.\n"
            + "6 = Sum of 1 through 3.\n"
            + "10 = Sum of 1 through 4.\n"
            + "15 = Sum of 1 through 5.\n"
            + "21 = Sum of 1 through 6.\n"
            + " 2 6 12 20 30 42 Bluh blah 123 8");

    // Test iteration over map keys.
    templateBody =
        ""
            + "{@param myMap : map<string, ?> }\n"
            + "  {for $key in mapKeys($myMap)}\n"
            + "    {if isFirst($key)}\n"
            + "      [\n"
            + "    {/if}\n"
            + "    {$key}: {$myMap[$key]}\n"
            + "    {if isLast($key)}\n"
            + "      ]\n"
            + "    {else}\n"
            + "      ,{sp}\n"
            + "    {/if}\n"
            + "  {/for}\n";

    SoyDict data =
        SoyValueConverterUtility.newDict(
            "myMap", SoyValueConverterUtility.newDict("aaa", "Blah", "bbb", 17));
    String output = renderWithData(templateBody, data);
    assertThat(ImmutableSet.of("[aaa: Blah, bbb: 17]", "[bbb: 17, aaa: Blah]")).contains(output);
  }

  @Test
  public void testRenderForStmt2() throws Exception {
    String templateBody =
        "{@param goo : list<?> }\n"
            + "  {for $n in $goo}\n"
            + "    {if not isFirst($n)}{\\n}{/if}\n"
            + "    {$n} ={sp}\n"
            + "    {for $i in range(1, index($n)+2)}\n"
            + "      {if $i != 1} + {/if}\n"
            + "      {$i}\n"
            + "    {/for}\n"
            + "  {/for}\n";

    assertRender(
        templateBody,
        "1 = 1\n"
            + "3 = 1 + 2\n"
            + "6 = 1 + 2 + 3\n"
            + "10 = 1 + 2 + 3 + 4\n"
            + "15 = 1 + 2 + 3 + 4 + 5\n"
            + "21 = 1 + 2 + 3 + 4 + 5 + 6");
  }

  @Test
  public void testRenderWithXidRenaming() throws Exception {
    xidRenamingMap = TEST_XID_RENAMING_MAP;
    String templateBody = "..{xid('ident')}";
    assertRender(templateBody, "..ident_id_renamed");
  }

  @Test
  public void testRenderWithoutXidRenaming() throws Exception {
    String templateBody = "..{xid('ident')}";
    assertRender(templateBody, "..ident_");
  }

  @Test
  public void testRenderWithCssRenaming() throws Exception {
    cssRenamingMap = TEST_CSS_RENAMING_MAP;
    String templateBody =
        "{@param component : ? }\n" + "{css('class')} {css($component, 'selector')}";

    assertRender(templateBody, "class_renamed comp-selector_renamed");
  }

  @Test
  public void testRenderWithoutCssRenaming() throws Exception {
    String templateBody =
        "{@param component : ? }\n" + "{css('class')} {css($component, 'selector')}";

    assertRender(templateBody, "class comp-selector");
  }

  @Test
  public void testRenderPcdataWithKnownSafeHtml() throws Exception {
    String templateBody =
        "{@param sanitizedContent : ?}\n" + "html:  {$sanitizedContent}{\\n}" + "The end.";

    assertRender(templateBody, "html:  <plaintext id=foo>\n" + "The end.");
  }

  @Test
  public void testRenderBasicCall() throws Exception {
    String soyFileContent =
        "{namespace ns}\n"
            + "\n"
            + "{template .callerTemplate}\n"
            + "  {@param boo: ?}\n"
            + "  {@param foo: ?}\n"
            + "  {@param goo: ?}\n"
            + "  {call .calleeTemplate data=\"all\" /}\n"
            + "  {call .calleeTemplate data=\"$foo\" /}\n"
            + "  {call .calleeTemplate data=\"all\"}\n"
            + "    {param boo: $foo.boo /}\n"
            + "  {/call}\n"
            + "  {call .calleeTemplate data=\"all\"}\n"
            + "    {param boo: 'moo' /}\n"
            + "  {/call}\n"
            + "  {call .calleeTemplate data=\"$foo\"}\n"
            + "    {param boo kind=\"text\"}moo{/param}\n"
            + "  {/call}\n"
            + "  {call .calleeTemplate}\n"
            + "    {param boo kind=\"text\"}zoo{/param}\n"
            + "    {param goo: $foo.goo /}\n"
            + "  {/call}\n"
            + "{/template}\n"
            + "\n"
            + "{template .calleeTemplate}\n"
            + "  {@param boo: ?}\n"
            + "  {@param goo: ?}\n"
            + "  {$boo}\n"
            + "  {for $n in $goo} {$n}{/for}{\\n}\n"
            + "{/template}\n";

    SoyDict foo =
        SoyValueConverterUtility.newDict(
            "boo", "foo", "goo", SoyValueConverterUtility.newList(3, 2, 1));
    SoyDict data =
        SoyValueConverterUtility.newDict(
            "boo", "boo", "foo", foo, "goo", SoyValueConverterUtility.newList(1, 2, 3));

    String expectedOutput =
        "boo 1 2 3\n"
            + "foo 3 2 1\n"
            + "foo 1 2 3\n"
            + "moo 1 2 3\n"
            + "moo 3 2 1\n"
            + "zoo 3 2 1\n";

    assertThat(
            renderTemplateInFile(
                soyFileContent, "ns.callerTemplate", data, TEST_IJ_DATA, arg -> false))
        .isEqualTo(expectedOutput);
  }

  private static class TestFuture extends AbstractFuture<String> {
    private int isDoneCounter;
    private final StringBuilder progress;

    TestFuture(String val, StringBuilder progress) {
      this.set(val);
      this.progress = progress;
    }

    @Override
    public boolean isDone() {
      // Return false twice. We check each future once whether we need to check upon rendering
      // whether it is done and once when actually rendering.
      if (isDoneCounter >= 2) {
        return true;
      }
      isDoneCounter++;
      return false;
    }

    @Override
    public String get() throws InterruptedException, ExecutionException {
      String val = super.get();
      progress.append(val);
      return val;
    }

    @Override
    public String toString() {
      // override toString since super.toString() may call .get()
      return "a future";
    }
  }

  @Test
  public void testRenderFuture() throws Exception {
    final StringBuilder progress = new StringBuilder();

    Flushable flushable =
        new Flushable() {
          @Override
          public void flush() {
            progress.append("flush;");
          }
        };
    String soyFileContent =
        "{namespace ns}\n"
            + "\n"
            + "{template .callerTemplate}\n"
            + "  {@param boo: ?}\n"
            + "  {@param foo: ?}\n"
            + "  {@param goo: ?}\n"
            + "  {call .calleeTemplate data=\"all\" /}\n"
            + "  {call .calleeTemplate data=\"$foo\" /}\n"
            + "  {call .calleeTemplate data=\"all\"}\n"
            + "    {param boo: $foo.boo /}\n"
            + "  {/call}\n"
            + "  {call .calleeTemplate data=\"all\"}\n"
            + "    {param boo: 'moo' /}\n"
            + "  {/call}\n"
            + "  {call .calleeTemplate data=\"$foo\"}\n"
            + "    {param boo kind=\"text\"}moo{/param}\n"
            + "  {/call}\n"
            + "  {call .calleeTemplate}\n"
            + "    {param boo kind=\"text\"}zoo{/param}\n"
            + "    {param goo: $foo.goo /}\n"
            + "  {/call}\n"
            + "{/template}\n"
            + "\n"
            + "{template .calleeTemplate}\n"
            + "  {@param boo: ?}\n"
            + "  {@param goo: ?}\n"
            + "  {@inject future: ?}\n"
            + "  {$boo}{$future}\n"
            + "  {for $n in $goo} {$n}{/for}{\\n}\n"
            + "{/template}\n";

    SoyFileSetNode fileSet =
        SoyFileSetParserBuilder.forFileContents(soyFileContent)
            .errorReporter(FAIL)
            .parse()
            .fileSet();

    SoyDict foo =
        SoyValueConverterUtility.newDict(
            "boo",
            new TestFuture("foo", progress),
            "goo",
            SoyValueConverterUtility.newList(3, 2, 1));
    SoyDict data =
        SoyValueConverterUtility.newDict(
            "boo",
            new TestFuture("boo", progress),
            "foo",
            foo,
            "goo",
            SoyValueConverterUtility.newList(1, 2, 3));

    SoyRecord testIj = SoyValueConverterUtility.newDict("future", new TestFuture("ij", progress));

    StringBuilder outputSb = new StringBuilder();
    CountingFlushableAppendable output = new CountingFlushableAppendable(outputSb, flushable);

    ImmutableMap<String, TemplateNode> basicTemplates = getBasicTemplates(fileSet);
    RenderVisitor rv =
        new RenderVisitor(
            new EvalVisitorFactoryImpl(),
            output,
            basicTemplates,
            getDeltemplateSelector(fileSet),
            data,
            testIj,
            arg -> false,
            null,
            xidRenamingMap,
            cssRenamingMap,
            false,
            /* pluginInstances= */ ImmutableMap.of());
    rv.exec(basicTemplates.get("ns.callerTemplate"));

    String expectedOutput =
        "booij 1 2 3\n"
            + "fooij 3 2 1\n"
            + "fooij 1 2 3\n"
            + "mooij 1 2 3\n"
            + "mooij 3 2 1\n"
            + "zooij 3 2 1\n";

    assertThat(outputSb.toString()).isEqualTo(expectedOutput);
    assertThat(progress.toString()).isEqualTo("booflush;ijflush;foo");
  }

  @Test
  public void testRenderDelegateCall() throws Exception {
    String soyFileContent1 =
        "{namespace ns1}\n"
            + "\n"
            + "/***/\n"
            + "{template .callerTemplate}\n"
            + "  {delcall myApp.myDelegate}\n"
            + "    {param boo: 'aaaaaah' /}\n"
            + "  {/delcall}\n"
            + "{/template}\n"
            + "\n"
            + "{deltemplate myApp.myDelegate}\n"
            + "  {@param boo: ?}\n"
            + // default implementation (doesn't use $boo)
            "  000\n"
            + "{/deltemplate}\n";

    String soyFileContent2 =
        "{delpackage SecretFeature}\n"
            + "{namespace ns2}\n"
            + "\n"
            + "{deltemplate myApp.myDelegate}\n"
            + "  {@param boo: ?}\n"
            + // implementation in SecretFeature
            "  111 {$boo}\n"
            + "{/deltemplate}\n";

    String soyFileContent3 =
        "{delpackage AlternateSecretFeature}\n"
            + "{namespace ns3}\n"
            + "\n"
            + "{deltemplate myApp.myDelegate}\n"
            + "  {@param boo: ?}\n"
            + // implementation in AlternateSecretFeature
            "  222 {call .helper data=\"all\" /}\n"
            + "{/deltemplate}\n";

    String soyFileContent4 =
        "{delpackage AlternateSecretFeature}\n"
            + "{namespace ns3}\n"
            + "\n"
            + "{template .helper}\n"
            + "  {@param boo: ?}\n"
            + "  {@inject ijStr: ?}\n"
            + "  {$boo} {$ijStr}\n"
            + "{/template}\n";

    final ParseResult parseResult =
        SoyFileSetParserBuilder.forFileContents(
                soyFileContent1, soyFileContent2, soyFileContent3, soyFileContent4)
            .errorReporter(FAIL)
            .parse();
    final SoyRecord data = SoyValueConverterUtility.newDict();

    Predicate<String> activeDelPackageNames = arg -> false;
    assertThat(
            renderTemplateInFile(
                parseResult, "ns1.callerTemplate", data, TEST_IJ_DATA, activeDelPackageNames))
        .isEqualTo("000");

    activeDelPackageNames = "SecretFeature"::equals;
    assertThat(
            renderTemplateInFile(
                parseResult, "ns1.callerTemplate", data, TEST_IJ_DATA, activeDelPackageNames))
        .isEqualTo("111 aaaaaah");

    activeDelPackageNames = "AlternateSecretFeature"::equals;
    assertThat(
            renderTemplateInFile(
                parseResult, "ns1.callerTemplate", data, TEST_IJ_DATA, activeDelPackageNames))
        .isEqualTo("222 aaaaaah injected");

    activeDelPackageNames = "NonexistentFeature"::equals;
    assertThat(
            renderTemplateInFile(
                parseResult, "ns1.callerTemplate", data, TEST_IJ_DATA, activeDelPackageNames))
        .isEqualTo("000");

    activeDelPackageNames =
        ImmutableSet.of("NonexistentFeature", "AlternateSecretFeature")::contains;
    assertThat(
            renderTemplateInFile(
                parseResult, "ns1.callerTemplate", data, TEST_IJ_DATA, activeDelPackageNames))
        .isEqualTo("222 aaaaaah injected");

    try {
      renderTemplateInFile(
          parseResult,
          "ns1.callerTemplate",
          data,
          TEST_IJ_DATA,
          ImmutableSet.of("SecretFeature", "AlternateSecretFeature")::contains);
      fail("expected RenderException");
    } catch (RenderException e) {
      assertThat(e)
          .hasMessageThat()
          .contains(
              "For delegate template 'myApp.myDelegate', found two active implementations with"
                  + " equal priority");
    }
  }

  @Test
  public void testRenderDelegateVariantCall() throws Exception {
    String soyFileContent1 =
        ""
            + "{namespace ns1}\n"
            + "\n"
            + "{template .callerTemplate}\n"
            + "  {@param greekB: ?}\n"
            + "  {delcall myApp.myDelegate variant=\"'alpha'\"}\n"
            + // variant is string
            "    {param boo: 'zzz' /}\n"
            + "  {/delcall}\n"
            + "  {delcall myApp.myDelegate variant=\"$greekB\"}\n"
            + // variant is expression
            "    {param boo: 'zzz' /}\n"
            + "  {/delcall}\n"
            + "  {delcall myApp.myDelegate variant=\"'gamma'\"}\n"
            + // variant "gamma" not implemented
            "    {param boo: 'zzz' /}\n"
            + "  {/delcall}\n"
            + "  {delcall myApp.myDelegate variant=\"test.GLOBAL\"}\n"
            + // variant is a global expression
            "    {param boo: 'zzz' /}\n"
            + "  {/delcall}\n"
            + "{/template}\n"
            + "\n"
            + "{deltemplate myApp.myDelegate}\n"
            + "  {@param boo: ?}\n"
            + // variant "" default
            "  000empty\n"
            + "{/deltemplate}\n"
            + "\n"
            + "{deltemplate myApp.myDelegate variant=\"'alpha'\"}\n"
            + "  {@param boo: ?}\n"
            + // variant "alpha" default
            "  000alpha\n"
            + "{/deltemplate}\n"
            + "\n"
            + "{deltemplate myApp.myDelegate variant=\"'beta'\"}\n"
            + "  {@param boo: ?}\n"
            + // variant "beta" default
            "  000beta\n"
            + "{/deltemplate}\n"
            + "{deltemplate myApp.myDelegate variant=\"test.GLOBAL\"}\n"
            + "  {@param boo: ?}\n"
            + // variant using global
            "  000global\n"
            + "{/deltemplate}\n";

    String soyFileContent2 =
        ""
            + "{delpackage SecretFeature}\n"
            + "{namespace ns2}\n"
            + "\n"
            + "{deltemplate myApp.myDelegate}\n"
            + "  {@param boo: ?}\n"
            + // variant "" in SecretFeature
            "  111empty\n"
            + "{/deltemplate}\n"
            + "\n"
            + "{deltemplate myApp.myDelegate variant=\"'alpha'\"}\n"
            + "  {@param boo: ?}\n"
            + // "alpha" in SecretFeature
            "  111alpha\n"
            + "{/deltemplate}\n"
            + "\n"
            + "{deltemplate myApp.myDelegate variant=\"'beta'\"}\n"
            + "  {@param boo: ?}\n"
            + // "beta" in SecretFeature
            "  111beta\n"
            + "{/deltemplate}\n"
            + "{deltemplate myApp.myDelegate variant=\"test.GLOBAL\"}\n"
            + "  {@param boo: ?}\n"
            + // variant using global
            "  111global\n"
            + "{/deltemplate}\n";

    String soyFileContent3 =
        ""
            + "{delpackage AlternateSecretFeature}\n"
            + "{namespace ns3}\n"
            + "\n"
            + "{deltemplate myApp.myDelegate}\n"
            + "  {@param boo: ?}\n"
            + // variant "" in AlternateSecretFeature
            "  222empty\n"
            + "{/deltemplate}\n"
            + "\n"
            + "{deltemplate myApp.myDelegate variant=\"'alpha'\"}\n"
            + "  {@param boo: ?}\n"
            + // variant "alpha" in Alternate
            "  222alpha\n"
            + "{/deltemplate}\n"
            + "{deltemplate myApp.myDelegate variant=\"test.GLOBAL\"}\n"
            + "  {@param boo: ?}\n"
            + // variant using global
            "  222global\n"
            + "{/deltemplate}\n"; // Note: No variant "beta" in AlternateSecretFeature.

    SoyGeneralOptions options = new SoyGeneralOptions();
    options.setCompileTimeGlobals(ImmutableMap.<String, Object>of("test.GLOBAL", 1));
    ParseResult result =
        SoyFileSetParserBuilder.forFileContents(soyFileContent1, soyFileContent2, soyFileContent3)
            .options(options)
            .runOptimizer(true)
            .errorReporter(FAIL)
            .parse();

    Predicate<String> activeDelPackageNames = arg -> false;
    assertThat(
            renderTemplateInFile(
                result, "ns1.callerTemplate", TEST_DATA, TEST_IJ_DATA, activeDelPackageNames))
        .isEqualTo("000alpha000beta000empty000global");

    activeDelPackageNames = "SecretFeature"::equals;
    assertThat(
            renderTemplateInFile(
                result, "ns1.callerTemplate", TEST_DATA, TEST_IJ_DATA, activeDelPackageNames))
        .isEqualTo("111alpha111beta111empty111global");

    activeDelPackageNames = "AlternateSecretFeature"::equals;
    assertThat(
            renderTemplateInFile(
                result, "ns1.callerTemplate", TEST_DATA, TEST_IJ_DATA, activeDelPackageNames))
        .isEqualTo("222alpha000beta222empty222global");

    activeDelPackageNames = "NonexistentFeature"::equals;
    assertThat(
            renderTemplateInFile(
                result, "ns1.callerTemplate", TEST_DATA, TEST_IJ_DATA, activeDelPackageNames))
        .isEqualTo("000alpha000beta000empty000global");

    activeDelPackageNames =
        ImmutableSet.of("NonexistentFeature", "AlternateSecretFeature")::contains;
    assertThat(
            renderTemplateInFile(
                result, "ns1.callerTemplate", TEST_DATA, TEST_IJ_DATA, activeDelPackageNames))
        .isEqualTo("222alpha000beta222empty222global");
    try {
      renderTemplateInFile(
          result,
          "ns1.callerTemplate",
          TEST_DATA,
          TEST_IJ_DATA,
          ImmutableSet.of("SecretFeature", "AlternateSecretFeature")::contains);
      fail("expected RenderException");
    } catch (RenderException e) {
      assertThat(e)
          .hasMessageThat()
          .contains(
              "For delegate template 'myApp.myDelegate:alpha', found two active implementations "
                  + "with equal priority");
    }
  }

  @Test
  public void testRenderDelegateCallWithoutDefault() throws Exception {
    String soyFileContent1a =
        "{namespace ns1}\n"
            + "\n"
            + "/***/\n"
            + "{template .callerTemplate}\n"
            + "  {delcall myApp.myDelegate allowemptydefault=\"true\"}\n"
            + "    {param boo: 'aaaaaah' /}\n"
            + "  {/delcall}\n"
            + "{/template}\n";

    String soyFileContent1b =
        "{namespace ns1}\n"
            + "\n"
            + "/***/\n"
            + "{template .callerTemplate}\n"
            + "  {delcall myApp.myDelegate}\n"
            + "    {param boo: 'aaaaaah' /}\n"
            + "  {/delcall}\n"
            + "{/template}\n";

    String soyFileContent2 =
        "{delpackage SecretFeature}\n"
            + "{namespace ns2}\n"
            + "\n"
            + "{deltemplate myApp.myDelegate}\n"
            + "  {@param boo: ?}\n"
            + // implementation in SecretFeature
            "  111 {$boo}\n"
            + "{/deltemplate}\n";

    SoyRecord data = SoyValueConverterUtility.newDict();
    ParseResult parseResult;

    // ------ Test with only file 1a in bundle. ------

    parseResult = SoyFileSetParserBuilder.forFileContents(soyFileContent1a).parse();

    Predicate<String> activeDelPackageNames = arg -> false;
    assertThat(
            renderTemplateInFile(
                parseResult, "ns1.callerTemplate", data, null, activeDelPackageNames))
        .isEmpty();

    activeDelPackageNames = "SecretFeature"::equals;
    assertThat(
            renderTemplateInFile(
                parseResult, "ns1.callerTemplate", data, null, activeDelPackageNames))
        .isEmpty();

    // ------ Test with both files 1a and 2 in bundle. ------

    parseResult =
        SoyFileSetParserBuilder.forFileContents(soyFileContent1a, soyFileContent2).parse();

    activeDelPackageNames = arg -> false;
    assertThat(
            renderTemplateInFile(
                parseResult, "ns1.callerTemplate", data, null, activeDelPackageNames))
        .isEmpty();

    activeDelPackageNames = "SecretFeature"::equals;
    assertThat(
            renderTemplateInFile(
                parseResult, "ns1.callerTemplate", data, null, activeDelPackageNames))
        .isEqualTo("111 aaaaaah");

    activeDelPackageNames = "NonexistentFeature"::equals;
    assertThat(
            renderTemplateInFile(
                parseResult, "ns1.callerTemplate", data, null, activeDelPackageNames))
        .isEmpty();

    activeDelPackageNames = ImmutableSet.of("NonexistentFeature", "SecretFeature")::contains;
    assertThat(
            renderTemplateInFile(
                parseResult, "ns1.callerTemplate", data, null, activeDelPackageNames))
        .isEqualTo("111 aaaaaah");

    // ------ Test with only file 1b in bundle. ------
    activeDelPackageNames = arg -> false;
    try {
      renderTemplateInFile(
          SoyFileSetParserBuilder.forFileContents(soyFileContent1b).parse(),
          "ns1.callerTemplate",
          data,
          null,
          activeDelPackageNames);
      fail("expected RenderException");
    } catch (RenderException e) {
      assertThat(e).hasMessageThat().contains("Found no active impl for delegate call");
    }

    // ------ Test with both files 1b and 2 in bundle. ------

    try {
      renderTemplateInFile(
          SoyFileSetParserBuilder.forFileContents(soyFileContent1b, soyFileContent2).parse(),
          "ns1.callerTemplate",
          data,
          null,
          activeDelPackageNames);
      fail("expected RenderException");
    } catch (RenderException e) {
      assertThat(e).hasMessageThat().contains("Found no active impl for delegate call");
    }

    activeDelPackageNames = "SecretFeature"::equals;
    assertThat(
            renderTemplateInFile(
                SoyFileSetParserBuilder.forFileContents(soyFileContent1b, soyFileContent2).parse(),
                "ns1.callerTemplate",
                data,
                null,
                activeDelPackageNames))
        .isEqualTo("111 aaaaaah");
  }

  @Test
  public void testRenderLogStmt() throws Exception {
    String templateBody =
        ""
            + "{@param foo: ?}\n"
            + "{@param boo: ?}\n"
            + "{@param moo: ?}\n"
            + "{if true}\n"
            + "  {$foo.bar}\n"
            + "  {log}Blah {$boo}.{/log}\n"
            + "  {$moo}\n"
            + "{/if}\n";

    // Send stdout to my own buffer.
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    System.setOut(new PrintStream(buffer));

    assertRender(templateBody, "baz3.14");
    assertThat(buffer.toString()).isEqualTo("Blah 8.\n");

    // Restore stdout.
    System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
  }

  @Test
  public void testRenderLogStmtOrdering() throws Exception {
    String templateBody =
        ""
            + "{let $gamma kind=\"text\"}\n"
            + "  {log}let-block{/log}\n"
            + "  let-block\n"
            + "{/let}\n"
            + "before{sp}{log}before{/log}\n"
            + "{$gamma}\n"
            + "{sp}after{log}after{/log}\n"
            + "{sp}{$gamma}\n";
    // Send stdout to my own buffer.
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    System.setOut(new PrintStream(buffer));
    // the let block is evaluated exactly once.
    assertRender(templateBody, "before let-block after let-block");
    assertThat(buffer.toString()).isEqualTo("before\nlet-block\nafter\n");

    System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
  }

  @Test
  public void testRenderCallLazyParamContentNode() throws Exception {
    String soyFileContent =
        "{namespace ns}\n"
            + "\n"
            + "/** */\n"
            + "{template .callerTemplate}\n"
            + "  {call .calleeTemplate}\n"
            + "    {param foo kind=\"text\"}\n"
            + "      param{log}param{/log}\n"
            + "    {/param}\n"
            + "  {/call}\n"
            + "{/template}\n"
            + "\n"
            + "{template .calleeTemplate}\n"
            + "  {@param foo: ?}\n"
            + "  callee{log}callee{/log}\n"
            + "  {sp}{$foo}{sp}{$foo}\n"
            + "{/template}\n";

    // Send stdout to my own buffer.
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    System.setOut(new PrintStream(buffer));
    assertThat(
            renderTemplateInFile(
                soyFileContent,
                "ns.callerTemplate",
                SoyValueConverterUtility.newDict(),
                null,
                arg -> false))
        .isEqualTo("callee param param");
    assertThat(buffer.toString()).isEqualTo("callee\nparam\n");
    // Restore stdout.
    System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
  }

  @Test
  public void testRenderExceptionsHaveExtraInfo() throws Exception {

    assertRenderException(
        "{@param undefined: ?}\n" + "  Hello {$undefined}\n",
        "In 'print' tag, expression \"$undefined\" evaluates to undefined.");

    assertRenderException(
        "{@param undefined: ?}\n" + "  Hello {$undefined + 'foo'}\n",
        "When evaluating \"$undefined + 'foo'\":");

    assertRenderException(
        "{@param undefined: ?}\n" + "  Hello {$undefined + 3}\n",
        "When evaluating \"$undefined + 3\":");
  }

  @Test
  public void testParamTypeCheckSuccess() throws Exception {
    assertRender("{@param boo: int}\n{$boo}\n", "8");
    assertRender("{@param list1: list<int>}\n{$list1[0]}\n", "1");
  }

  @Test
  public void testInjectedParamTypeCheckSuccess() throws Exception {
    assertRender("{@inject ijInt: int}\n{$ijInt}\n", "26");
    assertRender("{@inject ijStr: string}\n{$ijStr}\n", "injected");
  }

  @Test
  public void testDelayedCheckingOfCachingProviders() {
    String soyFileContent =
        "{namespace ns}\n"
            + "\n"
            + "{template .template}\n"
            + "  {@param foo: int}\n"
            + "  Before: {$foo}\n"
            + "{/template}\n";
    final StringBuilder outputSb = new StringBuilder();
    final AtomicReference<String> outputAtFutureGetTime = new AtomicReference<>();
    AbstractFuture<Integer> fooFuture =
        new AbstractFuture<Integer>() {
          {
            set(1);
          }

          @Override
          public Integer get() throws InterruptedException, ExecutionException {
            outputAtFutureGetTime.set(outputSb.toString());
            return super.get();
          }
        };
    SoyRecord data = SoyValueConverterUtility.newDict("foo", fooFuture);
    assertThat(
            renderTemplateInFile(
                SoyFileSetParserBuilder.forFileContents(soyFileContent).parse(),
                "ns.template",
                data,
                TEST_IJ_DATA,
                arg -> false,
                outputSb))
        .isEqualTo("Before: 1");
    assertThat(outputAtFutureGetTime.get()).isEqualTo("Before: ");
  }

  @Test
  public void testDelayedCheckingOfCachingProviders_typeCheckFailure() {
    String soyFileContent =
        "{namespace ns}\n"
            + "\n"
            + "{template .template}\n"
            + "  {@param foo: int}\n"
            + "  Before: {$foo}\n"
            + "{/template}\n";
    final StringBuilder outputSb = new StringBuilder();
    try {
      renderTemplateInFile(
          SoyFileSetParserBuilder.forFileContents(soyFileContent).errorReporter(FAIL).parse(),
          "ns.template",
          SoyValueConverterUtility.newDict("foo", Futures.immediateFuture("hello world")),
          TEST_IJ_DATA,
          arg -> false,
          outputSb);
      fail("expected RenderException");
    } catch (RenderException e) {
      assertThat(outputSb.toString()).isEqualTo("Before: ");
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              "When evaluating \"$foo\": Parameter type mismatch: attempt to bind value "
                  + "'hello world' (a StringData) to parameter "
                  + "'foo' which has a declared type of 'int'.");
    }
  }

  @Test
  public void testStreamLazyParamsToOutputStreamDirectly() {
    String soyFileContent =
        Joiner.on("\n")
            .join(
                "{namespace ns}",
                "",
                "{template .callee}",
                "  {@param body: html}",
                "  <div>",
                "    {$body}",
                "  </div>",
                "{/template}",
                "",
                "{template .caller}",
                "  {@param future: string}",
                "  {call .callee}",
                "    {param body kind=\"html\"}",
                "      static-content{sp}",
                "      {$future}",
                "    {/param}",
                "  {/call}",
                "{/template}");
    final StringBuilder outputSb = new StringBuilder();
    final AtomicReference<String> outputAtFutureGetTime = new AtomicReference<>();
    AbstractFuture<String> future =
        new AbstractFuture<String>() {
          {
            set("future-content");
          }

          @Override
          public String get() throws InterruptedException, ExecutionException {
            outputAtFutureGetTime.set(outputSb.toString());
            return super.get();
          }
        };
    SoyRecord data = SoyValueConverterUtility.newDict("future", future);
    assertThat(
            renderTemplateInFile(
                SoyFileSetParserBuilder.forFileContents(soyFileContent).parse(),
                "ns.caller",
                data,
                TEST_IJ_DATA,
                arg -> false,
                outputSb))
        .isEqualTo("<div>static-content future-content</div>");
    // We immediately type check the future, and so when .get() is called we haven't rendered
    // anything
    assertThat(outputAtFutureGetTime.get()).isEmpty();
  }

  @Test
  public void testParamTypeCheckFailed() throws Exception {
    assertRenderException("{@param boo: string}\n{$boo}\n", "Parameter type mismatch");
    assertRenderException("{@param list1: list<string>}\n{$list1[0]}\n", "Expected value of type");
    assertRenderException("{@inject ijInt: string}\n{$ijInt}\n", "Parameter type mismatch");
  }

  @Test
  public void testKeyNodeIsNoOp() throws Exception {
    assertRender("<div {key 'foo'}></div>", "<div></div>");
  }

  private static SoyValue createToStringTestValue() {
    return new SoyAbstractValue() {
      @Override
      public String toString() {
        // NOTE: Soy should not print the toString() values, only the coerceToString() values.
        return "toString()";
      }

      @Override
      public String coerceToString() {
        return "coerceToString()";
      }

      @Override
      public void render(LoggingAdvisingAppendable appendable) throws IOException {
        appendable.append(coerceToString());
      }

      @Override
      public boolean coerceToBoolean() {
        return true;
      }

      @Override
      public boolean equals(Object other) {
        if (other == null) {
          return false;
        }
        return this.getClass() == other.getClass();
      }

      @Override
      public int hashCode() {
        return this.getClass().hashCode();
      }
    };
  }

  static ImmutableMap<String, TemplateNode> getBasicTemplates(SoyFileSetNode fileSet) {
    ImmutableMap.Builder<String, TemplateNode> basicTemplates = ImmutableMap.builder();
    for (SoyFileNode fileNode : fileSet.getChildren()) {
      for (TemplateNode template : SoyTreeUtils.getAllNodesOfType(fileNode, TemplateNode.class)) {
        if (!(template instanceof TemplateDelegateNode)) {
          basicTemplates.put(template.getTemplateName(), template);
        }
      }
    }
    return basicTemplates.build();
  }

  static DelTemplateSelector<TemplateDelegateNode> getDeltemplateSelector(SoyFileSetNode fileSet) {

    DelTemplateSelector.Builder<TemplateDelegateNode> deltemplates =
        new DelTemplateSelector.Builder<>();
    for (SoyFileNode fileNode : fileSet.getChildren()) {
      for (TemplateNode template : SoyTreeUtils.getAllNodesOfType(fileNode, TemplateNode.class)) {
        if (template instanceof TemplateDelegateNode) {
          TemplateDelegateNode delegateNode = (TemplateDelegateNode) template;
          String delTemplateName = delegateNode.getDelTemplateName();
          String delPackageName = delegateNode.getDelPackageName();
          String variant = delegateNode.getDelTemplateVariant();
          if (delPackageName == null) {
            deltemplates.addDefault(delTemplateName, variant, delegateNode);
          } else {
            deltemplates.add(delTemplateName, delPackageName, variant, delegateNode);
          }
        }
      }
    }
    return deltemplates.build();
  }
}
