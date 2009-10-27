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

package com.google.template.soy.soyparse;

import com.google.template.soy.base.IdGenerator;
import com.google.template.soy.base.IntegerIdGenerator;
import com.google.template.soy.base.SoyFileSupplier;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;

import java.io.IOException;
import java.io.Reader;
import java.util.List;


/**
 * Static functions for parsing a set of Soy files into a {@link SoyFileSetNode}.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class SoyFileSetParser {

  private SoyFileSetParser() {}


  /**
   * Parses a set of Soy files and returns the parse tree.
   *
   * @param soyFileSuppliers The suppliers for the Soy files.
   * @return The resulting parse tree.
   * @throws SoySyntaxException If there is an error reading the file or a syntax error is found.
   */
  public static SoyFileSetNode parseSoyFiles(List<SoyFileSupplier> soyFileSuppliers)
      throws SoySyntaxException {

    IdGenerator nodeIdGen = new IntegerIdGenerator();
    SoyFileSetNode soyTree = new SoyFileSetNode(nodeIdGen.genStringId(), nodeIdGen);

    for (SoyFileSupplier soyFileSupplier : soyFileSuppliers) {
      soyTree.addChild(parseSoyFileHelper(soyFileSupplier, nodeIdGen));
    }

    return soyTree;
  }


  /**
   * Private helper for {@code parseSoyFiles()} and {@code parseSoyFileContents()} to parse one
   * Soy file.
   *
   * @param soyFileSupplier Supplier of the Soy file content and path.
   * @param nodeIdGen The generator of node ids.
   * @return The resulting parse tree for one Soy file.
   * @throws SoySyntaxException If there is an error reading the file or a syntax error is found.
   */
  private static SoyFileNode parseSoyFileHelper(
      SoyFileSupplier soyFileSupplier, IdGenerator nodeIdGen)
      throws SoySyntaxException {

    Reader soyFileReader;
    try {
      soyFileReader = soyFileSupplier.getInput();
    } catch (IOException ioe) {
      throw new SoySyntaxException(
          "Error opening Soy file " + soyFileSupplier.getPath() + ": " + ioe);
    }

    try {
      SoyFileNode soyFile = (new SoyFileParser(soyFileReader, nodeIdGen)).parseSoyFile();
      soyFile.setFilePath(soyFileSupplier.getPath());
      return soyFile;

    } catch (TokenMgrError tme) {
      throw (new SoySyntaxException(tme)).setFilePath(soyFileSupplier.getPath());
    } catch (ParseException pe) {
      throw (new SoySyntaxException(pe)).setFilePath(soyFileSupplier.getPath());
    } catch (SoySyntaxException sse) {
      throw sse.setFilePath(soyFileSupplier.getPath());

    } finally {
      // Close the Reader.
      try {
        soyFileReader.close();
      } catch (IOException ioe) {
        throw new SoySyntaxException(
            "Error closing Soy file " + soyFileSupplier.getPath() + ": " + ioe);
      }
    }
  }

}
