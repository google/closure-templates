/*
 * Copyright 2025 Google Inc.
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

package com.google.template.soy.jbcsrc;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Lists;
import com.google.template.soy.SoyFileSetParser;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.jbcsrc.api.OutputAppendable;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.shared.SoyIdRenamingMap;
import com.google.template.soy.shared.SoyJsIdTracker;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import java.io.IOException;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SoyJsIdTrackerTest {

  @Test
  public void testCallingTemplates_recordJsObjectIds() throws IOException {
    List<String> jsIds = Lists.newArrayList();
    List<String> jsXids = Lists.newArrayList();
    SoyFileSetParserBuilder builder =
        SoyFileSetParserBuilder.forSuppliers(
                SoyFileSupplier.Factory.create(
                    "{namespace ns1}\n"
                        + "{template controller}\n"
                        + "<div jscontroller='{xid('foo.bar.controller')}'></div>\n"
                        + "{/template}\n"
                        + "{template model}\n"
                        + "<div jsmodel='{xid('foo.bar.model')}'></div>\n"
                        + "{/template}\n"
                        + "{template callback}\n"
                        + "<div jscallback='{xid('foo.bar.callback')}'></div>\n"
                        + "{/template}",
                    SourceFilePath.forTest("path/file1.soy")))
            .runAutoescaper(true);
    SoyFileSetParser parser = builder.build();
    ParseResult parseResult = parser.parse();
    CompiledTemplates templates =
        BytecodeCompiler.compile(
                parseResult.registry(),
                parseResult.fileSet(),
                ErrorReporter.exploding(),
                parser.soyFileSuppliers(),
                builder.getTypeRegistry())
            .get();
    RenderContext ctx =
        TemplateTester.getDefaultContext(templates).toBuilder()
            .withJsIdTracker(
                new SoyJsIdTracker() {
                  @Override
                  public void trackJsXid(String xid) {
                    jsXids.add(xid);
                  }

                  @Override
                  public void trackRawJsId(String id, String xid) {
                    jsXids.add(xid);
                    jsIds.add(id);
                  }
                })
            .withXidRenamingMap(
                new SoyIdRenamingMap() {
                  @Nullable
                  @Override
                  public String get(String key) {
                    return key + "_";
                  }
                })
            .build();

    StringBuilder sb = new StringBuilder();
    OutputAppendable output = OutputAppendable.create(sb);

    assertThat(
            templates
                .getTemplate("ns1.controller")
                .render(null, ParamStore.EMPTY_INSTANCE, output, ctx))
        .isNull();
    assertThat(
            templates.getTemplate("ns1.model").render(null, ParamStore.EMPTY_INSTANCE, output, ctx))
        .isNull();
    assertThat(
            templates
                .getTemplate("ns1.callback")
                .render(null, ParamStore.EMPTY_INSTANCE, output, ctx))
        .isNull();

    assertThat(jsIds).containsExactly("foo.bar.controller", "foo.bar.model", "foo.bar.callback");
    assertThat(jsXids)
        .containsExactly("foo.bar.controller_", "foo.bar.model_", "foo.bar.callback_");
  }
}
