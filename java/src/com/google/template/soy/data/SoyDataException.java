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

package com.google.template.soy.data;


/**
 * Exception thrown when an error occurs in the data package.
 *
 */
public class SoyDataException extends RuntimeException {


  /** The data path where this error occurred. */
  private String dataPath;


  /**
   * @param message A detailed description of the error.
   */
  public SoyDataException(String message) {
    this(null, message);
  }


  /**
   * @param dataPath The data path where the error occurred.
   * @param message A detailed description of the error.
   */
  public SoyDataException(String dataPath, String message) {
    super(message);
    this.dataPath = dataPath;
  }


  /**
   * @param message A detailed description of the error.
   * @param cause The throwable that is causing this exception.
   */
  public SoyDataException(String message, Throwable cause) {
    this(null, message, cause);
  }


  /**
   * @param dataPath The data path where the error occurred.
   * @param message A detailed description of the error.
   * @param cause The throwable that is causing this exception.
   */
  public SoyDataException(String dataPath, String message, Throwable cause) {
    super(message, cause);
    this.dataPath = dataPath;
  }


  /**
   * Prepends a key to the data path where this error occurred.
   * E.g. if the dataPath was previously 'foo.goo' and the key to prepend is 'boo', then the new
   * data path will be 'boo.foo.goo'.
   * @param key The key to prepend.
   */
  public void prependKeyToDataPath(String key) {
    if (dataPath == null) {
      dataPath = key;
    } else {
      dataPath = key + ((dataPath.charAt(0) == '[') ? "" : ".") + dataPath;
    }
  }


  /**
   * Prepends an index to the data path where this error occurred.
   * E.g. if the dataPath was previously 'foo.goo' and the index to prepend is 2, then the new
   * data path will be '[2].foo.goo'.
   * @param index The index to prepend.
   */
  public void prependIndexToDataPath(int index) {
    prependKeyToDataPath("[" + index + "]");
  }


  @Override public String getMessage() {
    return (dataPath == null) ? super.getMessage()
        : "At data path '" + dataPath + "': " + super.getMessage();
  }

}
