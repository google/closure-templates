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

package com.google.template.soy.jssrc.internal;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.types.SoyEnumType;
import com.google.template.soy.types.SoyObjectType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.aggregate.ListType;
import com.google.template.soy.types.aggregate.MapType;
import com.google.template.soy.types.aggregate.RecordType;
import com.google.template.soy.types.aggregate.UnionType;
import com.google.template.soy.types.primitive.SanitizedType;

import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

/**
 * Shared utilities specific to the JS Src backend.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class JsSrcUtils {


  private JsSrcUtils() {}


  /**
   * Builds a version of the given string that has literal Unicode Format characters (Unicode
   * category "Cf") changed to valid JavaScript Unicode escapes (i.e. &92;u####). If the provided
   * string doesn't have any Unicode Format characters, then the same string is returned.
   *
   * @param str The string to escape.
   * @return A version of the given string that has literal Unicode Format characters (Unicode
   * category "Cf") changed to valid JavaScript Unicode escapes (i.e. &92;u####).
   */
  public static String escapeUnicodeFormatChars(String str) {

    int len = str.length();

    // Do a quick check first, because most strings do not contain Unicode format characters.
    boolean hasFormatChar = false;
    for (int i = 0; i < len; i++) {
      if (Character.getType(str.charAt(i)) == Character.FORMAT) {
        hasFormatChar = true;
        break;
      }
    }
    if (!hasFormatChar) {
      return str;
    }

    // Now we actually need to build a new string.
    StringBuilder out = new StringBuilder(len * 4 / 3);
    int codePoint;
    for (int i = 0; i < len; i += Character.charCount(codePoint)) {
      codePoint = str.codePointAt(i);
      if (Character.getType(codePoint) == Character.FORMAT) {
        BaseUtils.appendHexEscape(out, codePoint);
      } else {
        out.appendCodePoint(codePoint);
      }
    }
    return out.toString();
  }


  /**
   * Given a Soy type, return the corresponding jscompiler doc type expression.
   */
  public static String getJsTypeExpr(SoyType type) {
    return getJsTypeExpr(type, false, true);
  }


  public static String getJsTypeExpr(
      SoyType type,
      boolean addParensIfNeeded,
      boolean addRequiredIfNeeded) {
    String nonNullablePrefix = addRequiredIfNeeded ? "!" : "?";
    switch (type.getKind()) {
      case ANY:
        return "*";

      case UNKNOWN:
        // Add parens to avoid confusion w/ the leading ? of a nullable type
        return "(?)";

      case NULL:
        return "null";

      case BOOL:
        return "boolean";

      case STRING:
        return "string";

      case INT:
      case FLOAT:
        return "number";

      case LIST: {
        ListType listType = (ListType) type;
        if (listType.getElementType().getKind() == SoyType.Kind.ANY) {
          return nonNullablePrefix + "Array";
        }
        return nonNullablePrefix
            + "Array<" + getJsTypeExpr(listType.getElementType(), false, true) + ">";
      }

      case MAP: {
        MapType mapType = (MapType) type;
        if (mapType.getKeyType().getKind() == SoyType.Kind.ANY
            && mapType.getValueType().getKind() == SoyType.Kind.ANY) {
          return nonNullablePrefix + "Object<?,?>";
        }
        String keyTypeName = getJsTypeExpr(mapType.getKeyType(), false, true);
        String valueTypeName = getJsTypeExpr(mapType.getValueType(), false, true);
        return nonNullablePrefix + "Object<" + keyTypeName + "," + valueTypeName + ">";
      }

      case RECORD: {
        RecordType recordType = (RecordType) type;
        if (recordType.getMembers().isEmpty()) {
          return "!Object";
        }
        List<String> members = Lists.newArrayListWithExpectedSize(recordType.getMembers().size());
        for (Map.Entry<String, SoyType> member : recordType.getMembers().entrySet()) {
          members.add(member.getKey() + ": " + getJsTypeExpr(member.getValue(), true, true));
        }
        return "{" + Joiner.on(", ").join(members) + "}";
      }

      case UNION: {
        UnionType unionType = (UnionType) type;
        SortedSet<String> typeNames = Sets.newTreeSet();
        boolean isNullable = unionType.isNullable();
        boolean hasNullableMember = false;
        for (SoyType memberType : unionType.getMembers()) {
          if (memberType.getKind() == SoyType.Kind.NULL) {
            continue;
          }
          if (memberType instanceof SanitizedType) {
            typeNames.add((isNullable ? "?" : "!") + getJsTypeName(memberType));
            typeNames.add("string");
            hasNullableMember = true;
            continue;
          }
          if (JsSrcUtils.isDefaultOptional(memberType)) {
            hasNullableMember = true;
          }
          String typeExpr = getJsTypeExpr(memberType, false, !isNullable);
          if (typeExpr.equals("?")) {
            throw new IllegalStateException("Type: " + unionType + " contains an unknown");
          }
          typeNames.add(typeExpr);
        }
        if (isNullable && !hasNullableMember) {
          typeNames.add("null");
        }
        if (isNullable) {
          typeNames.add("undefined");
        }
        if (typeNames.size() != 1) {
          String result = Joiner.on("|").join(typeNames);
          if (addParensIfNeeded) {
            result = "(" + result + ")";
          }
          return result;
        } else {
          return typeNames.first();
        }
      }

      default:
        if (type instanceof SanitizedType) {
          String result = nonNullablePrefix + NodeContentKinds.toJsSanitizedContentCtorName(
              ((SanitizedType) type).getContentKind()) + "|string";
          if (addParensIfNeeded) {
            result = "(" + result + ")";
          }
          return result;
        }
        return getJsTypeName(type);
    }
  }


  /**
   * Given a Soy type, return the corresponding jscompiler type name. Only
   * handles types which have names and have a declared constructor - not
   * arbitrary type expressions.
   */
  public static String getJsTypeName(SoyType type) {
    if (type instanceof SanitizedType) {
      return NodeContentKinds.toJsSanitizedContentCtorName(
          ((SanitizedType) type).getContentKind());
    } else if (type.getKind() == SoyType.Kind.OBJECT) {
      return ((SoyObjectType) type).getNameForBackend(SoyBackendKind.JS_SRC);
    } else if (type.getKind() == SoyType.Kind.ENUM) {
      return ((SoyEnumType) type).getNameForBackend(SoyBackendKind.JS_SRC);
    } else {
      throw new AssertionError("Unsupported type: " + type);
    }
  }


  /** Returns true if the given type is optional by default (in the jscompiler). */
  public static boolean isDefaultOptional(SoyType type) {
    switch (type.getKind()) {
      case OBJECT:
      case LIST:
      case MAP:
        return true;

      default:
        return type instanceof SanitizedType;
    }
  }


  /**
   * Returns true if key is a JavaScript reserved word.
   */
  public static boolean isReservedWord(String key) {
    return JS_RESERVED_WORDS.contains(key);
  }


  /**
   * Traverses up the stack of local variable name mappings to get the generated local variable
   * name for the given variable.
   *
   * @param identity The local name of the variable
   * @param localVarTranslations The translations from local variables to generated variable name
   * @return The generated name of the variable if it is found, or null if it is not
   */
  public static String getVariableName(String identity,
      Deque<Map<String, JsExpr>> localVarTranslations) {
    for (Map<String, JsExpr> localVarTranslationsFrame : localVarTranslations) {
      JsExpr translation = localVarTranslationsFrame.get(identity);
      if (translation != null) {
        return translation.getText();
      }
    }

    return null;
  }


  /**
   * Set of words that JavaScript considers reserved words.  These words cannot
   * be used as identifiers.  This list is from the ECMA-262 v5, section 7.6.1:
   * http://www.ecma-international.org/publications/files/drafts/tc39-2009-050.pdf
   * plus the keywords for boolean values and {@code null}.
   * (Also includes the identifiers "soy" and "soydata" which are used internally by
   * Soy.)
   */
  private static final ImmutableSet<String> JS_RESERVED_WORDS = ImmutableSet.of(
      "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete", "do",
      "else", "enum", "export", "extends", "false", "finally", "for", "function", "if",
      "implements", "import", "in", "instanceof", "interface", "let", "null", "new", "package",
      "private", "protected", "public", "return", "soy", "soydata", "static", "super",
      "switch", "this", "throw", "true", "try", "typeof", "var", "void", "while",
      "with", "yield");
}
