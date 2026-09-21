/*
 * Copyright 2024 Google Inc.
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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyError;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ValidateExternsPassTest {

  public static String renderPrimary() {
    return "primary";
  }

  public static String renderFallback() {
    return "fallback";
  }

  public static List<String> listPrimary() {
    return ImmutableList.of();
  }

  public static List<String> listFallback() {
    return ImmutableList.of();
  }

  public String instanceFallback() {
    return "instanceFallback";
  }

  @Test
  public void testOutputFunctionValid() {
    ErrorReporter reporter = ErrorReporter.create();
    SoyFileSetParserBuilder.forFileContents(
            "{namespace ns}\n"
                + "{outputfunction myFunc: () => string}\n"
                + "  {javaimpl class=\""
                + ValidateExternsPassTest.class.getName()
                + "\" method=\"renderPrimary\" fallbackMethod=\"renderFallback\" params=\"\""
                + " return=\"java.lang.String\" /}\n"
                + "{/outputfunction}\n")
        .errorReporter(reporter)
        .parse();
    assertThat(reporter.getErrors()).isEmpty();
  }

  @Test
  public void testOutputFunctionMissingFallbackMethod() {
    ErrorReporter reporter = ErrorReporter.create();
    SoyFileSetParserBuilder.forFileContents(
            "{namespace ns}\n"
                + "{outputfunction myFunc: () => string}\n"
                + "  {javaimpl class=\""
                + ValidateExternsPassTest.class.getName()
                + "\" method=\"renderPrimary\" params=\"\" return=\"java.lang.String\" /}\n"
                + "{/outputfunction}\n")
        .errorReporter(reporter)
        .parse();
    SoyError error = Iterables.getOnlyElement(reporter.getErrors());
    assertThat(error.message())
        .isEqualTo(
            "Output function 'myFunc' must define the 'fallbackMethod' attribute in {javaimpl}.");
  }

  @Test
  public void testOutputFunctionNonRenderableReturnType() {
    ErrorReporter reporter = ErrorReporter.create();
    SoyFileSetParserBuilder.forFileContents(
            "{namespace ns}\n"
                + "{outputfunction myFunc: () => list<string>}\n"
                + "  {javaimpl class=\""
                + ValidateExternsPassTest.class.getName()
                + "\" method=\"listPrimary\" fallbackMethod=\"listFallback\" params=\"\""
                + " return=\"java.util.List\" /}\n"
                + "{/outputfunction}\n")
        .errorReporter(reporter)
        .parse();
    SoyError error = Iterables.getOnlyElement(reporter.getErrors());
    assertThat(error.message())
        .isEqualTo(
            "Output function 'myFunc' must have a renderable return type (string, html, css, uri,"
                + " attributes, int, float, or bool), but found 'list<string>'.");
  }

  @Test
  public void testExternDisallowsFallbackMethod() {
    ErrorReporter reporter = ErrorReporter.create();
    SoyFileSetParserBuilder.forFileContents(
            "{namespace ns}\n"
                + "{extern myFunc: () => string}\n"
                + "  {javaimpl class=\""
                + ValidateExternsPassTest.class.getName()
                + "\" method=\"renderPrimary\" fallbackMethod=\"renderFallback\" params=\"\""
                + " return=\"java.lang.String\" /}\n"
                + "{/extern}\n")
        .errorReporter(reporter)
        .parse();
    SoyError error = Iterables.getOnlyElement(reporter.getErrors());
    assertThat(error.message())
        .isEqualTo(
            "Extern 'myFunc' cannot specify the 'fallbackMethod' attribute. Use {outputfunction}"
                + " instead.");
  }

  @Test
  public void testFallbackMethodTypeMismatch() {
    ErrorReporter reporter = ErrorReporter.create();
    SoyFileSetParserBuilder.forFileContents(
            "{namespace ns}\n"
                + "{outputfunction myFunc: () => string}\n"
                + "  {javaimpl class=\""
                + ValidateExternsPassTest.class.getName()
                + "\" method=\"renderPrimary\" fallbackMethod=\"instanceFallback\" params=\"\""
                + " return=\"java.lang.String\" /}\n"
                + "{/outputfunction}\n")
        .errorReporter(reporter)
        .parse();
    SoyError error = Iterables.getOnlyElement(reporter.getErrors());
    assertThat(error.message())
        .isEqualTo(
            "Fallback method 'instanceFallback' must have matching method type (static vs"
                + " instance).");
  }
}
