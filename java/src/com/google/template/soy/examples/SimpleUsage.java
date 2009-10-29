/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.examples;

import com.google.common.io.Resources;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.data.SoyListData;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.tofu.SoyTofu;


/**
 * Usage of the simple examples.
 *
 * @author Kai Huang
 */
public class SimpleUsage {

  private SimpleUsage() {}


  /** Counter for the number of examples written so far. */
  private static int numExamples = 0;


  /**
   * Prints the generated HTML to stdout.
   * @param args Not used.
   */
  public static void main(String[] args) {

    // Compile the template.
    SoyFileSet sfs = (new SoyFileSet.Builder()).add(Resources.getResource("simple.soy")).build();
    SoyTofu tofu = sfs.compileToJavaObj();

    // Example 1.
    writeExampleHeader();
    System.out.println(tofu.render("soy.examples.simple.helloWorld", (SoyMapData) null, null));

    // Create a namespaced tofu object to make calls more concise.
    SoyTofu simpleTofu = tofu.forNamespace("soy.examples.simple");

    // Example 2.
    writeExampleHeader();
    System.out.println(simpleTofu.render(".helloName", new SoyMapData("name", "Ana"), null));

    // Example 3.
    writeExampleHeader();
    System.out.println(simpleTofu.render(
        ".helloNames", new SoyMapData("names", new SoyListData("Bob", "Cid", "Dee")), null));
  }


  /**
   * Private helper to write the header for each example.
     */
    private static void writeExampleHeader() {
      numExamples++;
      System.out.println("----------------------------------------------------------------");
      System.out.println("[" + numExamples + "]");
    }

}
