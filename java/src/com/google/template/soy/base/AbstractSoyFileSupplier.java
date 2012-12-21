/*
 * Copyright 2012 Google Inc.
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

package com.google.template.soy.base;


/**
 * Abstract base implementation of SoyFileSupplier.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
abstract class AbstractSoyFileSupplier implements SoyFileSupplier {


  /** Whether this input file is only included because it's a dependency. */
  protected final SoyFileKind soyFileKind;

  /** Returns the file path (used for messages only). */
  protected final String filePath;


  /**
   * @param soyFileKind The kind of this input Soy file.
   * @param filePath The path to the Soy file (used for messages only).
   */
  public AbstractSoyFileSupplier(SoyFileKind soyFileKind, String filePath) {
    this.soyFileKind = soyFileKind;
    this.filePath = filePath;
  }


  @Override public SoyFileKind getSoyFileKind() {
    return soyFileKind;
  }


  @Override public String getFilePath() {
    return filePath;
  }

}
