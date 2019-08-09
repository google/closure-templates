/*
 * Copyright 2018 Google Inc.
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

package com.google.template.soy;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.auto.value.AutoValue;
import com.google.common.io.Files;
import com.google.template.soy.css.CssMetadata;
import com.google.template.soy.soytree.CompilationUnit;
import com.google.template.soy.soytree.DataAllCallSituationP;
import com.google.template.soy.soytree.ParameterP;
import com.google.template.soy.soytree.SanitizedContentKindP;
import com.google.template.soy.soytree.SoyFileP;
import com.google.template.soy.soytree.SoyTypeP;
import com.google.template.soy.soytree.TemplateKindP;
import com.google.template.soy.soytree.TemplateMetadataP;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SoyHeaderCompilerTest {
  @Rule public final TemporaryFolder temp = new TemporaryFolder();

  @AutoValue
  abstract static class HeaderOutput {
    static HeaderOutput create(CompilationUnit unit, CssMetadata cssMeta) {
      return new AutoValue_SoyHeaderCompilerTest_HeaderOutput(unit, cssMeta);
    }

    abstract CompilationUnit unit();

    abstract CssMetadata cssMeta();
  }

  private HeaderOutput getHeaderOutput(String content) throws Exception {
    File soyFile1 = temp.newFile("temp.soy");
    Files.asCharSink(soyFile1, UTF_8).write(content);
    File outputFile = temp.newFile("temp.soyh");
    File cssMetadataOutputFile = temp.newFile("temp.css_meta");

    int exitCode =
        new SoyHeaderCompiler()
            .run(
                new String[] {
                  "--output", outputFile.toString(),
                  "--cssMetadataOutput", cssMetadataOutputFile.toString(),
                  "--srcs", soyFile1.toString(),
                },
                System.err);
    assertThat(exitCode).isEqualTo(0);
    CompilationUnit unit;
    try (InputStream is = new GZIPInputStream(new FileInputStream(outputFile))) {
      unit = CompilationUnit.parseFrom(is);
    } catch (Exception e) {
      return null;
    }
    CssMetadata cssMetadata;
    try (InputStream is = new GZIPInputStream(new FileInputStream(cssMetadataOutputFile))) {
      cssMetadata = CssMetadata.parseFrom(is);
    } catch (Exception e) {
      return null;
    }
    SoyFileP file = unit.getFile(0);
    assertThat(file.getFilePath()).isEqualTo(soyFile1.getPath());
    return HeaderOutput.create(unit, cssMetadata);
  }

  @Test
  public void testOutputFileFlag() throws Exception {
    HeaderOutput output =
        getHeaderOutput(
            "{namespace ns requirecss=\"ns.foo\"}\n"
                + "{template .a requirecss=\"ns.bar\"}{@param p: string}{call .a"
                + " data='all'/}<div class=\"{css('%foo')} {css('bar')}\"></div>{/template}");
    CompilationUnit unit = output.unit();
    assertThat(unit.getFileList()).hasSize(1);
    SoyFileP file = unit.getFile(0);
    assertThat(file.getDelpackage()).isEmpty();
    assertThat(file.getNamespace()).isEqualTo("ns");
    assertThat(file.getTemplateList()).hasSize(1);
    TemplateMetadataP template = file.getTemplate(0);
    assertThat(template.getTemplateName()).isEqualTo(".a");
    assertThat(template.getTemplateKind()).isEqualTo(TemplateKindP.BASIC);
    assertThat(template.getContentKind()).isEqualTo(SanitizedContentKindP.HTML);
    assertThat(template.getParameterList())
        .containsExactly(
            ParameterP.newBuilder()
                .setName("p")
                .setType(SoyTypeP.newBuilder().setPrimitive(SoyTypeP.PrimitiveTypeP.STRING))
                .setRequired(true)
                .build());
    assertThat(template.getDataAllCallSituationList())
        .containsExactly(DataAllCallSituationP.newBuilder().setTemplateName(".a").build());

    CssMetadata cssMeta = output.cssMeta();
    assertThat(cssMeta.getRequireCssNamesList()).containsExactly("ns.foo", "ns.bar").inOrder();
    assertThat(cssMeta.getCssClassNamesList()).containsExactly("nsFoofoo", "bar");
  }

  @Test
  public void testHtmlElementMetadata() throws Exception {
    CompilationUnit unit =
        getHeaderOutput("{namespace ns}\n" + "{template .a}<div></div>{/template}").unit();
    SoyFileP file = unit.getFile(0);
    TemplateMetadataP template = file.getTemplate(0);
    assertThat(template.getHtmlElement().getTag()).isEqualTo("div");
    assertThat(template.getHtmlElement().getIsHtmlElement()).isTrue();
    assertThat(template.getHtmlElement().getIsVelogged()).isFalse();
    assertThat(template.getSoyElement().getIsSoyElement()).isFalse();
  }

  @Test
  public void testHtmlElementDynamicTag() throws Exception {
    CompilationUnit unit =
        getHeaderOutput(
                "{namespace ns}\n"
                    + "{template .a}{@param foo: string}<{$foo}></{$foo}>{/template}")
            .unit();
    SoyFileP file = unit.getFile(0);
    TemplateMetadataP template = file.getTemplate(0);
    assertThat(template.getHtmlElement().getTag()).isEqualTo("?");
    assertThat(template.getHtmlElement().getIsHtmlElement()).isTrue();
    assertThat(template.getHtmlElement().getIsVelogged()).isFalse();
    assertThat(template.getSoyElement().getIsSoyElement()).isFalse();
  }

  @Test
  public void testFragment() throws Exception {
    CompilationUnit unit =
        getHeaderOutput("{namespace ns}\n" + "{template .a}<div></div><div></div>{/template}")
            .unit();
    SoyFileP file = unit.getFile(0);
    TemplateMetadataP template = file.getTemplate(0);
    assertThat(template.getHtmlElement().getTag()).isEqualTo("");
    assertThat(template.getHtmlElement().getIsHtmlElement()).isFalse();
    assertThat(template.getHtmlElement().getIsVelogged()).isFalse();
    assertThat(template.getSoyElement().getIsSoyElement()).isFalse();
  }

  @Test
  public void testSoyElementMetadata() throws Exception {
    CompilationUnit unit =
        getHeaderOutput("{namespace ns}\n" + "{element .a}<span></span>{/element}").unit();
    SoyFileP file = unit.getFile(0);
    TemplateMetadataP template = file.getTemplate(0);
    assertThat(template.getSoyElement().getIsSoyElement()).isTrue();
    assertThat(template.getHtmlElement().getIsVelogged()).isFalse();
    assertThat(template.getHtmlElement().getTag()).isEqualTo("span");
  }

  @Test
  public void testSoyElementMetadataSelfClosingTag() throws Exception {
    CompilationUnit unit =
        getHeaderOutput("{namespace ns}\n" + "{element .a}<input />{/element}").unit();
    SoyFileP file = unit.getFile(0);
    TemplateMetadataP template = file.getTemplate(0);
    assertThat(template.getSoyElement().getIsSoyElement()).isTrue();
    assertThat(template.getHtmlElement().getIsVelogged()).isFalse();
    assertThat(template.getHtmlElement().getTag()).isEqualTo("input");
  }

  @Test
  public void testSoyElementVelog() throws Exception {
    CompilationUnit unit =
        getHeaderOutput(
                "{namespace ns}\n"
                    + "{element .a}{@param vedata: ve_data}{velog"
                    + " $vedata}<span></span>{/velog}{/element}")
            .unit();
    SoyFileP file = unit.getFile(0);
    TemplateMetadataP template = file.getTemplate(0);
    assertThat(template.getSoyElement().getIsSoyElement()).isTrue();
    assertThat(template.getHtmlElement().getIsVelogged()).isTrue();
    assertThat(template.getHtmlElement().getTag()).isEqualTo("span");
  }

  @Test
  public void testTemplateVelog() throws Exception {
    CompilationUnit unit =
        getHeaderOutput(
                "{namespace ns}\n"
                    + "{template .a}{@param vedata: ve_data}{velog"
                    + " $vedata}<span></span>{/velog}{/template}")
            .unit();
    SoyFileP file = unit.getFile(0);
    TemplateMetadataP template = file.getTemplate(0);
    assertThat(template.getSoyElement().getIsSoyElement()).isFalse();
    assertThat(template.getHtmlElement().getIsVelogged()).isTrue();
    assertThat(template.getHtmlElement().getTag()).isEqualTo("span");
  }

  @Test
  public void testCssMetadata() throws Exception {
    CssMetadata cssMeta =
        getHeaderOutput(
                "{namespace ns requirecss='a'}\n"
                    + "{template .a requirecss='b,c,d' cssbase='Base'}"
                    + "<div class='{css('%A')} {css('B')} {css('%C')}'>"
                    + "</div>"
                    + "{/template}"
                    + "{template .b}"
                    + "<div class='{css('%E')}'>"
                    + "</div>"
                    + "{/template}")
            .cssMeta();

    assertThat(cssMeta.getRequireCssNamesList()).containsExactly("a", "b", "c", "d").inOrder();
    assertThat(cssMeta.getCssClassNamesList()).containsExactly("baseA", "B", "baseC", "aE");
  }
}
