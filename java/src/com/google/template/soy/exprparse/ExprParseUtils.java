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

package com.google.template.soy.exprparse;

import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprtree.DataRefNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.GlobalNode;

import java.util.List;


/**
 * Utilities for parsing expressions.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class ExprParseUtils {


  private ExprParseUtils() {}


  /**
   * Attempts to parse the given exprText as a Soy expression list. If successful, returns the
   * expression list. If unsuccessful, throws a SoySyntaxException.
   *
   *
   * @param exprListText The text to parse.
   * @param errorMsg The error message for the SoySyntaxException when parsing is unsuccessful.
   * @return The parsed expression list.
   */
  public static List<ExprRootNode<?>> parseExprListElseThrowSoySyntaxException(
      String exprListText, String errorMsg) {

    try {
      return (new ExpressionParser(exprListText)).parseExpressionList();
    } catch (TokenMgrError tme) {
      throw SoySyntaxException.createCausedWithoutMetaInfo(errorMsg, tme);
    } catch (ParseException pe) {
      throw SoySyntaxException.createCausedWithoutMetaInfo(errorMsg, pe);
    }
  }


  /**
   * Attempts to parse the given exprText as a Soy expression. If successful, returns the
   * expression. If unsuccessful, throws a SoySyntaxException.
   *
   * @param exprText The text to parse.
   * @param errorMsg The error message for the SoySyntaxException when parsing is unsuccessful.
   * @return The parsed expression.
   */
  public static ExprRootNode<?> parseExprElseThrowSoySyntaxException(
      String exprText, String errorMsg) {

    try {
      return (new ExpressionParser(exprText)).parseExpression();
    } catch (TokenMgrError tme) {
      throw SoySyntaxException.createCausedWithoutMetaInfo(errorMsg, tme);
    } catch (ParseException pe) {
      throw SoySyntaxException.createCausedWithoutMetaInfo(errorMsg, pe);
    }
  }


  /**
   * Attempts to parse the given exprText as a Soy expression. If successful, returns the
   * expression. If unsuccessful, returns null.
   *
   * <p> This function is used for situations where the expression might be in Soy V1 syntax (i.e.
   * older commands like 'print' and 'if').
   *
   * @param exprText The text to parse.
   * @return The parsed expression, or null if parsing was unsuccessful.
   */
  public static ExprRootNode<?> parseExprElseNull(String exprText) {

    try {
      return (new ExpressionParser(exprText)).parseExpression();
    } catch (TokenMgrError tme) {
      return null;
    } catch (ParseException pe) {
      return null;
    }
  }


  /**
   * Attempts to parse the given exprText as a Soy variable. If successful, returns the
   * variable's name. If unsuccessful, throws a SoySyntaxException.
   *
   * @param exprText The text to parse.
   * @param errorMsg The error message for the SoySyntaxException when parsing is unsuccessful.
   * @return The name of the parsed variable.
   */
  public static String parseVarNameElseThrowSoySyntaxException(
      String exprText, String errorMsg) {

    try {
      return (new ExpressionParser(exprText)).parseVariable().getChild(0).getName();
    } catch (TokenMgrError tme) {
      throw SoySyntaxException.createCausedWithoutMetaInfo(errorMsg, tme);
    } catch (ParseException pe) {
      throw SoySyntaxException.createCausedWithoutMetaInfo(errorMsg, pe);
    }
  }


  /**
   * Attempts to parse the given exprText as a Soy data reference. If successful, returns the
   * data reference. If unsuccessful, throws a SoySyntaxException.
   *
   * @param exprText The text to parse.
   * @param errorMsg The error message for the SoySyntaxException when parsing is unsuccessful.
   * @return The parsed data reference.
   */
  public static ExprRootNode<DataRefNode> parseDataRefElseThrowSoySyntaxException(
      String exprText, String errorMsg) {

    try {
      return (new ExpressionParser(exprText)).parseDataReference();
    } catch (TokenMgrError tme) {
      throw SoySyntaxException.createCausedWithoutMetaInfo(errorMsg, tme);
    } catch (ParseException pe) {
      throw SoySyntaxException.createCausedWithoutMetaInfo(errorMsg, pe);
    }
  }


  /**
   * Attempts to parse the given exprText as a Soy global. If successful, returns the
   * global. If unsuccessful, throws a SoySyntaxException.
   *
   * @param exprText The text to parse.
   * @param errorMsg The error message for the SoySyntaxException when parsing is unsuccessful.
   * @return The parsed global.
   */
  public static ExprRootNode<GlobalNode> parseGlobalElseThrowSoySyntaxException(
      String exprText, String errorMsg) {

    try {
      return (new ExpressionParser(exprText)).parseGlobal();
    } catch (TokenMgrError tme) {
      throw SoySyntaxException.createCausedWithoutMetaInfo(errorMsg, tme);
    } catch (ParseException pe) {
      throw SoySyntaxException.createCausedWithoutMetaInfo(errorMsg, pe);
    }
  }

}
