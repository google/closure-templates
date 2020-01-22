/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.base.internal;

import com.google.common.io.CharSource;
import java.io.IOException;
import java.io.Reader;

/**
 * Record for one input Soy file. Contains a {@link CharSource} to supply a {@code Reader} for the
 * file content, and also the file path.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class StableSoyFileSupplier extends AbstractSoyFileSupplier {

  /** Source for the Soy file content. */
  private final CharSource contentSource;

  /**
   * Creates a new {@code SoyFileSupplier} given a {@code CharSource} for the file content, as well
   * as the desired file path for messages.
   *
   * @param contentSource Source for the Soy file content.
   * @param filePath The path to the Soy file, used for as a unique map/set key and for messages.
   */
  public StableSoyFileSupplier(CharSource contentSource, String filePath) {
    super(filePath);
    this.contentSource = contentSource;
  }

  @Override
  public Reader open() throws IOException {
    return contentSource.openStream();
  }

  @Override
  public Version getVersion() {
    return Version.STABLE_VERSION;
  }
}
