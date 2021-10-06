/*
 * Copyright 2021 Google Inc.
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

package com.google.template.soy.idom;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.passes.CheckTemplateHeaderVarsPass;
import com.google.template.soy.passes.PassManager.PassContinuationRule;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class IdomMetadataCalculatorTest {
  private static final Joiner LINES = Joiner.on("\n");

  @Test
  public void testTemplate_regular() {
    assertIdomMetadata(
        LINES.join("TEMPLATE:main_ns.render", ""),
        createFile("main.soy", "{namespace main_ns}{template .render}{/template}"));
  }

  @Test
  public void testTemplate_deltemplate() {
    assertIdomMetadata(
        LINES.join("TEMPLATE:other.render", ""),
        createFile("main.soy", "{namespace main_ns}{deltemplate other.render}{/deltemplate}"));
  }

  @Test
  public void testFocusable_element() {
    assertIdomMetadata(
        LINES.join("TEMPLATE:main_ns.render", "  FOCUSABLE_ELEMENT", ""),
        createFile(
            "main.soy",
            "{namespace main_ns}",
            "{template .render}",
            "  <a href=\"x\">test</a>",
            "{/template}"));
  }

  @Test
  public void testFocusable_tabIndex() {
    assertIdomMetadata(
        LINES.join("TEMPLATE:main_ns.render", "  FOCUSABLE_ELEMENT", ""),
        createFile(
            "main.soy",
            "{namespace main_ns}",
            "{template .render}",
            "  <div tabindex=\"0\"></div>",
            "{/template}"));
    assertIdomMetadata(
        LINES.join("TEMPLATE:main_ns.render", ""),
        createFile(
            "main.soy",
            "{namespace main_ns}",
            "{template .render}",
            "  <div tabindex=\"-1\"></div>",
            "{/template}"));
  }

  @Test
  public void testForLoopChildrenWithoutKey_elements() {
    assertIdomMetadata(
        LINES.join(
            "TEMPLATE:main_ns.render",
            "  FOR_LOOP_ROOT_WITHOUT_KEY",
            "  FOR_LOOP_ROOT_WITHOUT_KEY",
            ""),
        createFile(
            "main.soy",
            "{namespace main_ns}",
            "{template .render}",
            "  {for $x in [1]}",
            "    <div><b></b></div>",
            "    <span></span>",
            "  {/for}",
            "{/template}"));
  }

  @Test
  public void testForLoopChildrenWithoutKey_siblingLoops() {
    assertIdomMetadata(
        LINES.join(
            "TEMPLATE:main_ns.render",
            "  FOR_LOOP_ROOT_WITHOUT_KEY",
            "  FOR_LOOP_ROOT_WITHOUT_KEY",
            ""),
        createFile(
            "main.soy",
            "{namespace main_ns}",
            "{template .render}",
            "  {for $x in [1]}",
            "    <div></div>",
            "  {/for}",
            "  {for $x in [1]}",
            "    <div></div>",
            "  {/for}",
            "{/template}"));
  }

  @Test
  public void testForLoopChildrenWithoutKey_nestedLoops() {
    assertIdomMetadata(
        LINES.join(
            "TEMPLATE:main_ns.render",
            "  FOR_LOOP_ROOT_WITHOUT_KEY",
            "    FOR_LOOP_ROOT_WITHOUT_KEY",
            ""),
        createFile(
            "main.soy",
            "{namespace main_ns}",
            "{template .render}",
            "  {for $x in [1]}",
            "    <div>",
            "      {for $y in [1]}",
            "        <span></span>",
            "      {/for}",
            "    </div>",
            "  {/for}",
            "{/template}"));
  }

  @Test
  public void testSkip_element() {
    assertIdomMetadata(
        LINES.join("TEMPLATE:main_ns.render", "  SKIP", ""),
        createFile(
            "main.soy",
            "{namespace main_ns}",
            "{template .render}",
            "  <div {skip}></div>",
            "{/template}"));
  }

  @Test
  public void testSkip_nesting() {
    assertIdomMetadata(
        LINES.join("TEMPLATE:main_ns.render", "  SKIP", "    SKIP", ""),
        createFile(
            "main.soy",
            "{namespace main_ns}",
            "{template .render}",
            "  <div {skip}>",
            "    <div {skip}></div>",
            "  </div>",
            "{/template}"));
  }

  @Test
  public void testSkip_sibling() {
    assertIdomMetadata(
        LINES.join("TEMPLATE:main_ns.render", "  SKIP", "  SKIP", ""),
        createFile(
            "main.soy",
            "{namespace main_ns}",
            "{template .render}",
            "  <div {skip}></div>",
            "  <div {skip}></div>",
            "{/template}"));
  }

  @Test
  public void testHtml_conditionals_balanced() {
    assertIdomMetadata(
        LINES.join(
            "TEMPLATE:main_ns.render",
            "  FOR_LOOP_ROOT_WITHOUT_KEY",
            "  FOR_LOOP_ROOT_WITHOUT_KEY",
            ""),
        createFile(
            "main.soy",
            "{namespace main_ns}",
            "{template .render}",
            "  {for $x in [1]}",
            "    {if true}",
            "      <div></div>",
            "    {else}",
            "      <span></span>",
            "    {/if}",
            "  {/for}",
            "{/template}"));
  }

  @Test
  public void testHtml_conditionals_multipleOpenTags() {
    assertIdomMetadata(
        LINES.join(
            "TEMPLATE:main_ns.render",
            "  FOR_LOOP_ROOT_WITHOUT_KEY",
            "  FOR_LOOP_ROOT_WITHOUT_KEY",
            ""),
        createFile(
            "main.soy",
            "{namespace main_ns}",
            "{template .render}",
            "  {for $x in [1]}",
            "    {if true}",
            "      <div>",
            "    {else}",
            "      <div>",
            "    {/if}",
            "    </div>",
            "  {/for}",
            "{/template}"));
  }

  @Test
  public void testHtml_conditionals_multipleCloseTags() {
    assertIdomMetadata(
        LINES.join("TEMPLATE:main_ns.render", "  FOR_LOOP_ROOT_WITHOUT_KEY", ""),
        createFile(
            "main.soy",
            "{namespace main_ns}",
            "{template .render}",
            "  {for $x in [1]}",
            "    <div>",
            "    {if true}",
            "      </div>",
            "    {else}",
            "      </div>",
            "    {/if}",
            "  {/for}",
            "{/template}"));
  }

  @Test
  public void testHtml_conditionals_interleavedTags() {
    assertIdomMetadata(
        LINES.join(
            "TEMPLATE:main_ns.render",
            "  FOR_LOOP_ROOT_WITHOUT_KEY",
            "  FOR_LOOP_ROOT_WITHOUT_KEY",
            ""),
        createFile(
            "main.soy",
            "{namespace main_ns}",
            "{template .render}",
            "  {for $x in [1]}",
            "    {if true}",
            "      <div>",
            "    {else}",
            "      <span>",
            "    {/if}",
            "    {if true}",
            "      </div>",
            "    {else}",
            "      </span>",
            "    {/if}",
            "  {/for}",
            "{/template}"));
  }

  private static void assertIdomMetadata(String metadata, SoyFileSupplier... files) {
    SoyFileSetNode fileSet =
        SoyFileSetParserBuilder.forSuppliers(files)
            // Emulate a header compile.
            // See SoyFileSet.compileMinimallyForHeaders().
            .addPassContinuationRule(
                CheckTemplateHeaderVarsPass.class, PassContinuationRule.STOP_BEFORE_PASS)
            .parse()
            .fileSet();
    IdomMetadata idomMetadata = IdomMetadataCalculator.calcMetadata(fileSet).get(0);
    assertThat(idomMetadataToString(idomMetadata)).isEqualTo(metadata);
  }

  private static SoyFileSupplier createFile(String fileName, String... lines) {
    return SoyFileSupplier.Factory.create(LINES.join(lines), SourceFilePath.create(fileName));
  }

  private static String idomMetadataToString(IdomMetadata metadata) {
    if (metadata == null) {
      return "";
    }
    return appendIdomMetadataString(new StringBuilder(), metadata, "").toString();
  }

  private static StringBuilder appendIdomMetadataString(
      StringBuilder str, IdomMetadata metadata, String prefix) {
    str.append(prefix).append(metadata.kind().name());
    if (metadata.name() != null) {
      str.append(":").append(metadata.name());
    }
    str.append("\n");
    for (IdomMetadata child : metadata.children()) {
      appendIdomMetadataString(str, child, prefix + "  ");
    }
    return str;
  }
}
