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

package com.google.template.soy;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.base.internal.ErrorPrettyPrinter;
import com.google.template.soy.jssrc.SoyJsSrcOptions;

import junit.framework.TestCase;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

/**
 * Tests for {@link com.google.template.soy.base.internal.ErrorPrettyPrinter}.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public final class ErrorPrettyPrinterTest extends TestCase {

  public void testMultipleErrorReports() throws IOException {
    String input = "{namespace ns}\n"
            + "/** Template. */\n"
            + "{template .foo}\n"
            + "{call /}\n"
            + " {delcall 123 /}\n"
            + "  {foreach foo in bar}\n"
            + "   {let /}\n"
            + "{/template}";

    final SoyFileSet soyFileSet = SoyFileSet.builder()
        .add(input, "input.soy")
        .build();

    CompilationResult result = soyFileSet.compileToJsSrcFiles(
        new File("./tmp", "output.js").getPath(),
        "",
        new SoyJsSrcOptions(),
        ImmutableList.<String>of(),
        null);
    assertThat(result.isSuccess()).isFalse();

    ImmutableList<String> errorReports = ImmutableList.copyOf(
        Iterables.transform(
            result.getErrors(),
            new Function<SoySyntaxException, String>() {
              @Override
              public String apply(SoySyntaxException e) {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                PrintStream err = new PrintStream(outputStream);
                ErrorPrettyPrinter prettyPrinter = soyFileSet.getErrorPrettyPrinter(err);
                prettyPrinter.print(e);
                return outputStream.toString();
              }
            }));

    assertThat(errorReports).hasSize(4);
    assertThat(errorReports.get(0)).isEqualTo(
        "In file input.soy:4:1: Invalid 'call' command missing callee name: {call }.\n"
            + "{call /}\n"
            + "^\n");
    assertThat(errorReports.get(1)).isEqualTo(
        "In file input.soy:5:2: Invalid delegate name \"123\" for 'delcall' command.\n"
            + " {delcall 123 /}\n"
            + " ^\n");
    assertThat(errorReports.get(2)).isEqualTo(
        "In file input.soy:6:3: Invalid 'foreach' command text \"foo in bar\".\n"
            + "  {foreach foo in bar}\n"
            + "  ^\n");
    assertThat(errorReports.get(3)).isEqualTo(
        "In file input.soy:7:4: Invalid 'let' command text \"\".\n"
            + "   {let /}\n"
            + "   ^\n");
  }
}
