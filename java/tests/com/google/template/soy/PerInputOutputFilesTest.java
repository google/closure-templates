/*
 * Copyright 2019 Google Inc.
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
import static org.junit.Assert.fail;

import com.google.common.base.Joiner;
import java.io.File;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PerInputOutputFilesTest {
  private final PerInputOutputFiles outputFiles =
      new PerInputOutputFiles("soy.js", Joiner.on("\n"));

  private SoyCmdLineParser cmdLineParser;

  @Before
  public final void constructParser() {
    cmdLineParser = new SoyCmdLineParser(/*loader=*/ null);
    cmdLineParser.registerFlagsObject(outputFiles);
  }

  @Test
  public void testValidate_setBothSets() throws Exception {
    cmdLineParser.parseArgument("--outputPathFormat", "foo/bar.js", "--outputDirectory", "foo/");
    try {
      outputFiles.validateFlags();
      fail();
    } catch (CommandLineError cme) {
      assertThat(cme)
          .hasMessageThat()
          .isEqualTo(
              "Error: Must set either --outputPathFormat or --outputDirectory and --inputRoots.");
    }
  }

  @Test
  public void testValidate_setNeither() throws Exception {
    cmdLineParser.parseArgument();
    try {
      outputFiles.validateFlags();
      fail();
    } catch (CommandLineError cme) {
      assertThat(cme)
          .hasMessageThat()
          .isEqualTo("Error: Must set at least one of --outputPathFormat or --outputDirectory.");
    }
  }

  @Test
  public void testGetOutputPath_inputRoots() throws Exception {
    cmdLineParser.parseArgument("--outputDirectory", "out", "--inputRoots", "root");
    assertThat(outputFiles.getOutputPath(new File("root/foo/bar.soy"), /*locale=*/ null).toString())
        .isEqualTo("out/foo/bar.soy.js");
    // files not under a root are copied as is
    assertThat(outputFiles.getOutputPath(new File("src/foo/bar.soy"), /*locale=*/ null).toString())
        .isEqualTo("out/src/foo/bar.soy.js");
  }

  // just a basic test, there are more tests in MainEntryPointUtilsTest
  @Test
  public void testGetOutputPath_outputPathFormat() throws Exception {
    cmdLineParser.parseArgument("--outputPathFormat", "foo.js");
    assertThat(outputFiles.getOutputPath(new File("root/foo/bar.soy"), /*locale=*/ null).toString())
        .isEqualTo("foo.js");
  }
}
