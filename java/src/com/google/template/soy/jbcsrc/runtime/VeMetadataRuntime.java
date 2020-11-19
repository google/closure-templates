/*
 * Copyright 2020 Google Inc.
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

package com.google.template.soy.jbcsrc.runtime;

import com.google.common.io.Resources;
import com.google.protobuf.ExtensionRegistry;
import com.google.template.soy.logging.RuntimeVeMetadata;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/** Runtime functions related to VE metadata. */
public final class VeMetadataRuntime {

  private VeMetadataRuntime() {}

  /**
   * Loads the VE metadata in the given Java resource (a binary encoded RuntimeVeMetadata proto).
   * Note that the resource URL here is a parameter so that the resource can be a resource of the
   * caller, and use the caller's ClassLoader to find the resource.
   */
  public static RuntimeVeMetadata loadMetadata(URL resource) {
    try (InputStream input = Resources.asByteSource(resource).openStream()) {
      return RuntimeVeMetadata.parseFrom(
          input,
          ExtensionRegistry.getEmptyRegistry()
          );
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }
}
