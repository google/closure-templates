/*
 * Copyright 2023 Google Inc.
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

import com.google.common.base.Joiner;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class BanDuplicateNamespacesPassTest {

  @Test
  public void externOnlyDuplicateNamespace_reportsError() {
    ErrorReporter errorReporter = ErrorReporter.create();
    ParseResult unused =
        SoyFileSetParserBuilder.forSuppliers(
                SoyFileSupplier.Factory.create(
                    Joiner.on("\n")
                        .join(
                            "{namespace my.duplicate.namespace.testing}",
                            "{template t}",
                            "{/template}"),
                    SourceFilePath.forTest("file1.soy")),
                SoyFileSupplier.Factory.create(
                    Joiner.on("\n")
                        .join(
                            "{namespace my.duplicate.namespace.testing}",
                            "{extern myExtern: (s: string) => string}",
                            "  {jsimpl namespace=\"goog.string\" function=\"capitalize\" /}",
                            "{/extern}"),
                    SourceFilePath.forTest("file2.soy")))
            .errorReporter(errorReporter)
            .build()
            .parse();

    assertThat(errorReporter.getErrors()).hasSize(2);
    assertThat(errorReporter.getErrors().get(0).message())
        .isEqualTo(
            "Found another file 'file2.soy' with the same namespace.  All files must have unique"
                + " namespaces.");
    assertThat(errorReporter.getErrors().get(1).message())
        .isEqualTo(
            "Found another file 'file1.soy' with the same namespace.  All files must have unique"
                + " namespaces.");
  }
}
