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

package com.google.template.soy.msgs;


/**
 * Exception for errors related to messages/translation.
 *
 */
public class SoyMsgException extends RuntimeException {


  /** The name of the file or resource associated with this error. */
  private String fileOrResourceName = null;


  /**
   * @param message A detailed description of the error.
   */
  public SoyMsgException(String message) {
    super(message);
  }


  /**
   * @param message A detailed description of the error.
   * @param cause The underlying cause.
   */
  public SoyMsgException(String message, Throwable cause) {
    super(message, cause);
  }


  /**
   * @param cause The underlying cause.
   */
  public SoyMsgException(Throwable cause) {
    super(cause);
  }


  /**
   * Sets the name of the file or resource associated with this error.
   * @param fileOrResourceName The file or resource name to set.
   */
  public void setFileOrResourceName(String fileOrResourceName) {
    this.fileOrResourceName = fileOrResourceName;
  }


  /**
   * Returns the name of the file or resource associated with this error.
   */
  public String getFileOrResourceName() {
    return fileOrResourceName;
  }


  @Override public String getMessage() {
    if (fileOrResourceName != null) {
      return "While processing \"" + fileOrResourceName + "\": " + super.getMessage();
    } else {
      return super.getMessage();
    }
  }

}
