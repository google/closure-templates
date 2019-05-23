/*
 * Copyright 2017 Google Inc.
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

package com.google.template.soy.conformance;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.conformance.Requirement.RequirementTypeCase;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import java.lang.reflect.Constructor;

/** A validated wrapper for {@link ConformanceConfig}. */
public final class ValidatedConformanceConfig {
  /** An empty configuration. */
  public static final ValidatedConformanceConfig EMPTY =
      new ValidatedConformanceConfig(ImmutableList.of());

  private static final Escaper MESSAGE_FORMAT =
      Escapers.builder().addEscape('{', "'{").addEscape('}', "}'").build();

  /**
   * Creates a validated configuration object.
   *
   * @throws IllegalArgumentException if there is an error in the config object.
   */
  public static ValidatedConformanceConfig create(ConformanceConfig config) {
    ImmutableList.Builder<RuleWithWhitelists> rulesBuilder = new ImmutableList.Builder<>();
    for (Requirement requirement : config.getRequirementList()) {
      Preconditions.checkArgument(
          !requirement.getErrorMessage().isEmpty(), "requirement missing error message");
      Preconditions.checkArgument(
          requirement.getRequirementTypeCase() != RequirementTypeCase.REQUIREMENTTYPE_NOT_SET,
          "requirement missing type");
      Rule<? extends Node> rule = forRequirement(requirement);
      ImmutableList<String> whitelists = ImmutableList.copyOf(requirement.getWhitelistList());
      ImmutableList<String> onlyApplyToPaths =
          ImmutableList.copyOf(requirement.getOnlyApplyToList());
      rulesBuilder.add(RuleWithWhitelists.create(rule, whitelists, onlyApplyToPaths));
    }
    return new ValidatedConformanceConfig(rulesBuilder.build());
  }

  private final ImmutableList<RuleWithWhitelists> rules;

  private ValidatedConformanceConfig(ImmutableList<RuleWithWhitelists> rules) {
    this.rules = rules;
  }

  ImmutableList<RuleWithWhitelists> getRules() {
    return rules;
  }

  private static Rule<? extends Node> forRequirement(Requirement requirement) {
    SoyErrorKind error =
        SoyErrorKind.of(
            MESSAGE_FORMAT.escape(requirement.getErrorMessage()), StyleAllowance.values());
    switch (requirement.getRequirementTypeCase()) {
      case CUSTOM:
        return createCustomRule(requirement.getCustom().getJavaClass(), error);
      case BANNED_CSS_SELECTOR:
        Requirement.BannedCssSelector bannedCss = requirement.getBannedCssSelector();
        return new BannedCssSelector(ImmutableSet.copyOf(bannedCss.getSelectorList()), error);
      case BANNED_DIRECTIVE:
        Requirement.BannedDirective bannedDirective = requirement.getBannedDirective();
        return new BannedDirective(ImmutableSet.copyOf(bannedDirective.getDirectiveList()), error);
      case BANNED_FUNCTION:
        Requirement.BannedFunction bannedFunction = requirement.getBannedFunction();
        return new BannedFunction(ImmutableSet.copyOf(bannedFunction.getFunctionList()), error);
      case BANNED_RAW_TEXT:
        Requirement.BannedRawText bannedRawText = requirement.getBannedRawText();
        return new BannedRawText(
            ImmutableSet.copyOf(bannedRawText.getTextList()),
            ImmutableSet.copyOf(bannedRawText.getExceptInHtmlAttributeList()),
            error);
      case BANNED_HTML_TAG:
        Requirement.BannedHtmlTag bannedHtmlTag = requirement.getBannedHtmlTag();
        return new BannedHtmlTag(
            bannedHtmlTag.getTagList(), bannedHtmlTag.getWhenAttributePossiblyPresentList(), error);
      case BAN_XID_FOR_CSS_OBFUSCATION:
        return new BanXidForCssObfuscation(error);
      case REQUIREMENTTYPE_NOT_SET:
        throw new AssertionError(
            "unexpected requirement type: " + requirement.getRequirementTypeCase());
    }
    throw new AssertionError(requirement.getRequirementTypeCase());
  }

  /**
   * Instantiates a custom conformance check from its fully-qualified class name, specified in the
   * conformance protobuf.
   *
   * <p>Custom conformance checks must extend {@link Rule}. They must also have a binary constructor
   * with {@link ErrorReporter} and {@link SoyErrorKind} parameters.
   */
  private static Rule<?> createCustomRule(String javaClass, SoyErrorKind error) {
    Class<? extends Rule<?>> customRuleClass;
    try {
      @SuppressWarnings("unchecked")
      Class<? extends Rule<?>> asSubclass =
          (Class<? extends Rule<?>>) Class.forName(javaClass).asSubclass(Rule.class);
      customRuleClass = asSubclass;
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException("custom rule class " + javaClass + " not found", e);
    }
    try {
      // It is ok for the constructor to be non-public as long as it is defined in this package
      // if it is non public and defined in another package, this will throw an
      // IllegalAccessException which seems about right.
      Constructor<? extends Rule<?>> ctor =
          customRuleClass.getDeclaredConstructor(SoyErrorKind.class);
      return ctor.newInstance(error);
    } catch (ReflectiveOperationException e) {
      throw new IllegalArgumentException(
          "unable to construct custom rule class: " + javaClass + ": " + e.getMessage(), e);
    }
  }

  public ValidatedConformanceConfig concat(ValidatedConformanceConfig other) {
    return new ValidatedConformanceConfig(
        ImmutableList.<RuleWithWhitelists>builder().addAll(rules).addAll(other.rules).build());
  }
}
