/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.types.proto;

import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;

/**
 * A collection of protobuf utility methods.
 */
public final class Protos {

  private Protos() {
    // Static only.
  }

  /** Returns the expected javascript package for protos based on the .proto file. */
  static String getJsPackage(FileDescriptor file) {
    return "proto." + file.getPackage();
  }

  static boolean shouldJsIgnoreField(FieldDescriptor fieldDescriptor) {
    return false;
  }

  static boolean hasJsMapKey(FieldDescriptor fieldDescriptor) {
    return false;
  }

  static String getJsMapKeyFieldName(FieldDescriptor fieldDescriptor) {
    return null;
  }

  public static boolean hasJsType(FieldDescriptor fieldDescriptor) {
    return false;
  }

  public static JsType getJsType(FieldDescriptor fieldDescriptor) {
    return null;
  }

  static String calculateJsEnumName(EnumDescriptor descriptor) {
    throw new UnsupportedOperationException("JS enum expressions not supported yet");
  }



  /**
   * Correspond to JavaScript types that a .proto file author might want to specify as the
   * representation for a proto field's value instead of leaving it up to Soy's inference rules.
   */
  public enum JsType {
    /**
     * JavaScript's number type is a float with a 52 bit mantissa, so can precisely represent all
     * signed 52b integers.
     */
    INT52,
    NUMBER,
    STRING,
    ;
  }


}
