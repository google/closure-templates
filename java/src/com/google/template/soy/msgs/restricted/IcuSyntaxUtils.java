/*
 * Copyright 2010 Google Inc.
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

package com.google.template.soy.msgs.restricted;


/**
 * Static helper functions for generating ICU syntax strings for plural and select commands
 * in a Soy message.
 *
 * A typical Plural command is as follows:
 * {plural $num_people offset="1"}
 *   {case 0}Case 0 statement.
 *   {case 1}Case 1 statement.
 *   {default}Default statement for {remainder{$num_people}} out of {$num_people}.
 * {/plural}
 *
 * The corresponding ICU syntax string is:
 * {numPeople,plural,offset=1
 *   =0{Case 0 statement.}
 *   =1{Case 1 statement}
 *   other{Default statement for # out of {$numPeople}.}
 * }
 *
 * (The variable name "numPeople" may be different depending on what purpose the
 * string is generated.)
 *
 * Similarly, a typical select case:
 *
 * {select $gender}
 *   {case 'female'}{$person} added you to her circle.
 *   {default}{$person} added you to his circle.
 * {/select}
 *
 * The corresponding ICU syntax string is:
 * {gender,select,
 *   female{{$person} added you to her circle.}
 *   other{{$person} added you to his circle.}
 * }
 *
 * (The variable names "gender" and "person" may be different depending on what purpose the
 * string is generated.)
 *
 * This string needs to be generated in several places in Soy code, so the general
 * functions are provided here.
 *
 * @author Mohamed Eldawy
 */
public class IcuSyntaxUtils {

  private IcuSyntaxUtils() {}

  // Plural related strings.


  /**
   * Gets the opening (left) string for a plural statement.
   * @param varName The plural var name.
   * @param offset The offset.
   * @return the ICU syntax string for the plural opening string.
   */
  public static String getPluralOpenString(String varName, int offset) {
    StringBuilder openingPartSb = new StringBuilder();
    openingPartSb.append("{").append(varName).append(",plural,");
    if (offset != 0) {
      openingPartSb.append("offset:").append(offset).append(" ");
    }
    return openingPartSb.toString();
  }


  /**
   * Gets the closing (right) string for a plural statement.
   * @return the ICU syntax string for the plural closing string.
   */
  public static String getPluralCloseString() {
    return "}";
  }


  /**
   * Gets the opening (left) string for a plural case statement.
   * @param caseNumber The case number, or null if it is the default statement.
   * @return the ICU syntax string for the plural case opening string.
   */
  public static String getPluralCaseOpenString(Integer caseNumber) {
    return (caseNumber == null ? "other" : "=" + caseNumber.toString()) + "{";
  }


 /**
   * Gets the closing (right) string for a plural case statement.
   * @return the ICU syntax string for the plural case closing string.
   */
  public static String getPluralCaseCloseString() {
    return "}";
  }


  /**
   * Gets the closing string for a plural remainder statement.
   * @return the ICU syntax string for the plural remainder string.
   */
  public static String getPluralRemainderString() {
    return "#";
  }


  // Select related strings.


  /**
   * Gets the opening (left) string for a select statement.
   * @param varName The select var name.
   * @return the ICU syntax string for the select opening string.
   */
  public static String getSelectOpenString(String varName) {
    return "{" + varName + ",select,";
  }


  /**
   * Gets the closing (right) string for a select statement.
   * @return the ICU syntax string for the select closing string.
   */
  public static String getSelectCloseString() {
    return "}";
  }


  /**
   * Gets the opening (left) string for a select case statement.
   * @param caseValue The case value, or {@code null} is it is the default statement.
   * @return the ICU syntax string for the select case opening string.
   */
  public static String getSelectCaseOpenString(String caseValue) {
    if (caseValue == null) {
      return "other{";
    }
    return caseValue + "{";
  }


  /**
   * Gets the closing string for a plural remainder statement.
   * @return the ICU syntax string for the plural remainder string.
   */
  public static String getSelectCaseCloseString() {
    return "}";
  }

}
