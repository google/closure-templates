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

package com.google.template.soy.passes;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.CaseFormat;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.base.internal.TemplateContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.CallableExpr.ParamsStyle;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.passes.CompilerFileSetPass.Result;
import com.google.template.soy.passes.IndirectParamsCalculator.IndirectParamsInfo;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.FileSetMetadata;
import com.google.template.soy.soytree.ImportNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.NullType;
import com.google.template.soy.types.SanitizedType;
import com.google.template.soy.types.SanitizedType.ElementType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.TemplateImportType;
import com.google.template.soy.types.TemplateType;
import com.google.template.soy.types.UnionType;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * This compiler pass runs several checks on {@code CallNode}s.
 *
 * <ul>
 *   <li>Checks that calling arguments match the declared parameter types of the called template.
 *   <li>Checks missing, unused or duplicate parameters.
 *   <li>Checks strict-html templates can only call other strict-html templates from an html
 *       context.
 * </ul>
 *
 * <p>In addition to checking that static types match and flagging errors, this visitor also stores
 * a set of TemplateParam object in each {@code CallNode} for all the params that require runtime
 * checking.
 *
 * <p>Note: This pass requires that the ResolveExpressionTypesPass has already been run.
 */
@RunAfter(FinalizeTemplateRegistryPass.class)
public final class CheckTemplateCallsPass implements CompilerFileSetPass {

  static final SoyErrorKind ARGUMENT_TYPE_MISMATCH =
      SoyErrorKind.of(
          "Type mismatch on param {0}: expected: {1}, actual: {2}", StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind DUPLICATE_PARAM = SoyErrorKind.of("Duplicate param ''{0}''.");
  private static final SoyErrorKind PASSES_UNUSED_PARAM =
      SoyErrorKind.of(
          "''{0}'' is not a declared parameter of {1} or any indirect callee.{2}",
          StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind EXPECTED_NAMED_PARAMETERS =
      SoyErrorKind.of("Expected named parameters.");
  private static final SoyErrorKind NO_DEFAULT_DELTEMPLATE =
      SoyErrorKind.of(
          "No default deltemplate found for {0}. Please add a default deltemplate, even if it is "
              + "empty. NOTE: This check can be bypassed with 'allowemptydefault=\"true\"', but "
              + "that feature is deprecated and will be removed soon.");
  private static final SoyErrorKind NO_IMPORT_DEFAULT_DELTEMPLATE =
      SoyErrorKind.of(
          "Delcall without without import to file containing default deltemplate ''{0}''. Add:"
              + " import * as unused{1} from ''{2}'';\n"
              + "NOTE: This check can be bypassed with 'allowemptydefault=\"true\"', but that "
              + "feature is deprecated and will be removed soon.");
  private static final SoyErrorKind ALLOW_EMPTY_DEFAULT_ERROR =
      SoyErrorKind.of(
          "Allowemptydefault=\"true\" is deprecated. Please remove it and add a default "
              + "deltemplate and ensure the file that provides it is imported. NOTE: Some files "
              + "are temporarily passlisted while the LSC to remove all usages are in progress.");
  private static final SoyErrorKind ALLOW_EMPTY_DEFAULT_WARNING =
      SoyErrorKind.deprecation(
          "Allowemptydefault=\"true\" is deprecated. Please remove it and add a default "
              + "deltemplate and ensure the file that provides it is imported. NOTE: This file "
              + "is temporarily passlisted while the LSC to remove all usages are in progress.");
  private static final SoyErrorKind NO_USEVARIANTTYPE =
      SoyErrorKind.of("Cannot specify \"variant\" unless the callee specifies \"usevarianttype\".");
  private static final SoyErrorKind BAD_VARIANT_TYPE =
      SoyErrorKind.of("Expected variant of type {0}, found type {1}.");
  private static final SoyErrorKind MISSING_PARAM = SoyErrorKind.of("Call missing required {0}.");
  private static final SoyErrorKind STRICT_HTML =
      SoyErrorKind.of(
          "Found call to non stricthtml template. Strict HTML template "
              + "can only call other strict HTML templates from an HTML context.");
  private static final SoyErrorKind CAN_ONLY_CALL_TEMPLATE_TYPES =
      SoyErrorKind.of("'{'call'}' is only valid on template types, but found type ''{0}''.");
  private static final SoyErrorKind CANNOT_CALL_MIXED_CONTENT_TYPE =
      SoyErrorKind.of("Cannot call expressions of different content types; found {0} and {1}.");

  private static final SoyErrorKind INVALID_DATA_EXPR =
      SoyErrorKind.of("''data='' should be a record type, found ''{0}''.", StyleAllowance.NO_CAPS);

  /** The error reporter that is used in this compiler pass. */
  private final ErrorReporter errorReporter;

  private final Supplier<FileSetMetadata> templateRegistryFull;

  CheckTemplateCallsPass(
      ErrorReporter errorReporter, Supplier<FileSetMetadata> templateRegistryFull) {
    this.errorReporter = errorReporter;
    this.templateRegistryFull = templateRegistryFull;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    CheckCallsHelper helper = new CheckCallsHelper(templateRegistryFull.get());
    for (SoyFileNode file : sourceFiles) {
      for (TemplateNode template : file.getTemplates()) {
        for (CallBasicNode callNode :
            SoyTreeUtils.getAllNodesOfType(template, CallBasicNode.class)) {
          helper.checkCall(template, callNode);
        }
        for (CallDelegateNode callNode :
            SoyTreeUtils.getAllNodesOfType(template, CallDelegateNode.class)) {
          helper.checkCall(file, template, callNode, file.getFilePath().path());
        }
        for (PrintNode printNode : SoyTreeUtils.getAllNodesOfType(template, PrintNode.class)) {
          if (printNode.getExpr().getRoot() instanceof FunctionNode
              && (((FunctionNode) printNode.getExpr().getRoot()).allowedToInvokeAsFunction()
                  || printNode.getExpr().getRoot().getType() instanceof ElementType)) {
            helper.checkFnCall(template, printNode, (FunctionNode) printNode.getExpr().getRoot());
          }
        }
      }
    }

    return Result.CONTINUE;
  }

  private static final ImmutableSet<String> DEFAULT_DELTEMPLATE_PASSLIST =
      ImmutableSet.of(
          "boq.ads.townsquare.marketplaceui.components.carousel.templates.item", // circular import
          "boq.ads.townsquare.marketplaceui.components.grid.templates.item", // circular import
          "boq.ads.townsquare.marketplaceui.components.grid.templates.itemKey", // circular import
          "boq.dashersecurityadminconsoleui.reauth.common.templates.learnMoreLink_", // circular imp
          "boq.dynamitewebui.member.templates.isOneOnOneDm", // circular dep
          "boq.dynamitewebui.member.templates.readSingleUserFromDmHumanMembersList", // circular dep
          "boq.googlefinanceui.views.bundles.portfolio.templates.portfolioLayoutRedesign", // circular import
          "boq.privacy.one.privacypage.product.components.privacypagepresentation.tabs.dependentlibraries.ps_dependencies_insights_info.templates.columns", // circular import
          "boq.protoshop.viewer.templates.topLevelMessage", // circular dep
          "boq.saveui.listitem.templates.cardHeader", // circualr deps, cl/455220729 breaks tests
          "boq.shopping.property.ui.components.carousel.templates.item", // circular import
          "boq.shopping.property.ui.components.componentregistry.classes.templates.type", // cir dep
          "boq.shopping.property.ui.components.componentregistry.ghost.templates.type", // cir dep
          "boq.shopping.property.ui.components.componentregistry.templates.type", // circular dep
          "boq.shopping.property.ui.components.grid.templates.item", // circular import
          "boq.shopping.property.ui.components.grid.templates.itemKey", // circular import
          "boq.shopping.property.ui.components.grid.templates.renderPopoutTooltipArrow", // c import
          "boq.taskswebshared.ssl.ui.app_view.templates.bundleView", // circular import
          "boq.townsquare.frontend.ui.components.carousel.templates.item", // circular imporrt
          "boq.townsquare.frontend.ui.components.grid.templates.item", // circular imporrt
          "boq.townsquare.frontend.ui.components.grid.templates.itemKey", // circular imporrt
          "boq.townsquare.frontend.ui.components.grid.templates.renderPopoutTooltipArrow", // circular import
          "boq.travel.frontend.core.map.web.layers.hotels.summarycards.content_enable_links_to_embedded_placesheet.templates.relativePath", // circular import
          "boq.travelfrontendhotelssearchweb.component.bookingmodule.hotels.show_organic_prices.templates.bookingModuleHeader", // circular import
          "boq.travelfrontendhotelssearchweb.home.header.enable_expand_when_reach_bottom.templates.enableExpandWhenScrollToBottom", // circular import
          "boq.travelfrontendhotelssearchweb.home.header.enable_expand_when_scroll_up.templates.enableExpandWhenScrollUp", // circular import
          "boq.visualfrontendui.explorepanel.templates.closeButton",
          "boq.visualfrontendviewer.imagecard.templates.ampViewerTray",
          "boq.visualfrontendviewer.imagecard.templates.navigationButtons", // circular dep
          "boq.visualfrontendui.explorepanel.templates.header",
          "Corp.Projectmgmt.Primavera.App.Workflow.Template.Core", // gencode
          "drive.search.filetypeicons.delSvg.templates", // gencode
          "gcal.mat.detaildialog.event.soy.detaildialog.rsvpButton", // circular import
          "gcal.mat.detaildialog.event.soy.detaildialog.rsvpButtonClasses", // circular import
          "gitiles.footerFormatBadge",
          "gitiles.logEntry",
          "materialdesign.wiz.icon.svgs.delSvg.templates", // gencode
          "renderComponentWithUiReference",
          "wiztest.pkg.deltemplates.passthroughTemplate",
          // In contact with team, b/233902965
          // https://groups.google.com/a/google.com/g/soy-dev/c/YV0d64vm0I4/m/4tI4J7PYAwAJ
          "boq.educms.fields.dispatcher.templates.enumfield",
          "boq.educms.fields.dispatcher.templates.groupfield",
          "boq.educms.fields.dispatcher.templates.nodefield",
          "boq.educms.fields.dispatcher.templates.repeatedenumfield",
          "boq.educms.fields.dispatcher.templates.repeatedgroupfield",
          "boq.educms.fields.dispatcher.templates.repeatednodefield",
          "boq.educms.fields.dispatcher.templates.repeatedstringfield",
          "boq.educms.fields.dispatcher.templates.stringfield",
          "boq.educms.renderer.templates.component",
          "boq.educms.renderer.templates.componentlist",
          "boq.educmseditor.fields.templates.editor",
          "boq.educmseditor.fields.templates.preview",
          // MAILED
          "boq.ads.townsquare.marketplaceui.components.componentregistry.templates.ghost", // cl/455004511
          "boq.ads.townsquare.marketplaceui.components.componentregistry.templates.type");

  private static final ImmutableSet<String> ALLOWEMPTYDEFAULT_PASSLIST =
      ImmutableSet.of(
          "delegateForUnitTest", // For unit test
          // Entries below generated with:
          // $ experimental/users/nicholasyu/del/regendata.sh
          // $ grep allowemptydefault /tmp/delcalls.txt | awk '{printf "\"%s\",\n", $3}' | sort |
          // uniq | grep -v '^"boq.dpanelchromeui.'
          "boq.accountsettingsnotifications.securityevent.templates.eventDetails",
          "boq.accountssigninui.discovery.identifier.internal.templates.contentText",
          "boq.accountssigninui.discovery.identifier.internal.templates.emailLabel",
          "boq.accountssigninui.discovery.identifier.internal.templates.enterpriseEnrollmentLabel",
          "boq.accountssigninui.discovery.identifier.internal.templates.extraJsaction",
          "boq.accountssigninui.discovery.identifier.internal.templates.guestModeLabel",
          "boq.accountssigninui.discovery.identifier.internal.templates.headerText",
          "boq.accountssigninui.discovery.identifier.internal.templates.keyboardOptionsLabel",
          "boq.accountssigninui.discovery.identifier.internal.templates.prefilledIdentifier",
          "boq.accountssigninui.discovery.identifier.internal.templates.secondaryActionLabel",
          "boq.accountssigninui.discovery.identifier.internal.templates.supplementaryActionUri",
          "boq.accounts.wireframe.api.nameinput.templates.isDense",
          "boq.additnowappdetail.templates.showFeaturesContent",
          "boq.additnowappdetail.templates.showFeaturesTab",
          "boq.additnowappfeatures.templates.showFeatures",
          "boq.additnowstoreui.category.templates.filterBar",
          "boq.additnowstoreui.home.templates.filterBar",
          "boq.additnowstoreui.mydomainapps.templates.filterBar",
          "boq.adshomeservicesconsumerui.components.searchboxentrymodule.templates.searchboxEntryModule",
          "boq.adsintegrityopsuisupport.chrome.navigation.appbar.templates.user_photo",
          "boq.ads.townsquare.marketplaceui.components.componentregistry.classes.templates.type",
          "boq.androidgantryui.common.local.templates.localBanner",
          "boq.audiopub.show.templates.podcastsLink",
          "boq.audiopub.show.templates.subscribers",
          "boq.baselinemessageconsoleui.searchbar.templates.advancedFiltersButton",
          "boq.calendaradminconsoleresource.addresource.templates.maybeInRoomBooking",
          "boq.calendaradminconsoleresource.buildingsandresources.templates.isRoomHealthEnabled",
          "boq.calendaradminconsoleresource.common.downtime.messages.templates.scheduleDowntimeLabelInternal",
          "boq.calendaradminconsoleresource.managefeatures.roomguard.templates.guardedOption",
          "boq.calendaradminconsoleresource.resource.fieldgroup.templates.inRoomBookingSetting",
          "boq.calendaradminconsoleresource.resource.sidebar.templates.maintenanceRole",
          "boq.calendaradminconsoleresource.resource.sidebar.templates.status",
          "boq.calendaradminconsoleresource.resourcestable.settingsmenu.templates.aclMenuItem",
          "boq.calendaradminconsoleresource.resourcestable.settingsmenu.templates.cleanCalendarMenuItem",
          "boq.calendaradminconsoleresource.resourcestable.settingsmenu.templates.editFeaturesMenuItem",
          "boq.calendaradminconsoleresource.resourcestable.settingsmenu.templates.inRoomBookingMenuItems",
          "boq.calendaradminconsoleresource.resourcestable.settingsmenu.templates.scheduleDowntimeMenuItem",
          "boq.calendaradminconsoleresource.resourcestable.templates.buildingFilterChip",
          "boq.calendaradminconsoleresource.resourcestable.templates.bulkActions",
          "boq.calendaradminconsoleresource.resourcestable.templates.bulkRemoveDowntimeEnabled",
          "boq.calendaradminconsoleresource.resourcestable.templates.capacityFilterChip",
          "boq.calendaradminconsoleresource.resourcestable.templates.disableGoogleSettings",
          "boq.calendaradminconsoleresource.resourcestable.templates.domainFilterChip",
          "boq.calendaradminconsoleresource.resourcestable.templates.floorIdFilterChip",
          "boq.calendaradminconsoleresource.resourcestable.templates.internalDescriptionFilterChip",
          "boq.calendaradminconsoleresource.resourcestable.templates.isBuildingFiltersEnabled",
          "boq.calendaradminconsoleresource.resourcestable.templates.isEnableResourceFeatureEdit",
          "boq.calendaradminconsoleresource.resourcestable.templates.isInRoomBookingEnabled",
          "boq.calendaradminconsoleresource.resourcestable.templates.isRoomguardEnabled",
          "boq.calendaradminconsoleresource.resourcestable.templates.maintenanceRole",
          "boq.calendaradminconsoleresource.resourcestable.templates.newToolbar",
          "boq.calendaradminconsoleresource.resourcestable.templates.offlineOnlineFilterChip",
          "boq.calendaradminconsoleresource.resourcestable.templates.resourceIdFilterChip",
          "boq.calendaradminconsoleresource.resourcestable.templates.roomIdFilterChip",
          "boq.calendaradminconsoleresource.resourcestable.templates.steadyActions",
          "boq.calendaradminconsoleresource.resourcestable.templates.typeFilterChip",
          "boq.calendaradminconsoleresource.resourcestable.templates.userGroupFilterChip",
          "boq.calendaradminconsoleresource.resourcestable.templates.userVisibleDescriptionFilterChip",
          "boq.calendaradminconsoleresource.resource.summary.templates.isInRoomBookingEnabled",
          "boq.calendaradminconsoleresource.resource.summary.templates.summaryCardForAuditLogs",
          "boq.calendaradminconsoleresource.roomsettings.templates.inRoomBookingExpanded",
          "boq.calendaradminconsoleresource.roomsettings.templates.inRoomBookingLabel",
          "boq.calendaradminconsoleresource.roomsettings.templates.inRoomBookingSummaryValue",
          "boq.calendaradminconsoleresource.roomsettings.templates.isInRoomBookingEnabled",
          "boq.calendaradminconsoleresource.toolbaractions.templates.downloadSelectedLabel",
          "boq.calendaradminconsoleroominsights.utilization.daterangedialog_weekdayFilter.templates.weekdayFilter",
          "boq.calendaradminconsoleroominsights.utilization_weekdayFilter.templates.weekdayFilter",
          "boq.calendaradminconsolesettings.interop.settinggroup.templates.defaultEndpointTitle",
          "boq.cloudbillingipxui.editdeal.templates.flexCudConfigurationHeader",
          "boq.cloudbillingipxui.editdeal.templates.flexCudConfigurationStep",
          "boq.cloudbillingipxui.editdeal.templates.useFlexCudConfigurationOffset",
          "boq.cloudcastpartnerportalui.pages.titledetaildecoupling.components.launchpacksection.ratingcertificate.templates.addOnContentIarcConfig",
          "boq.cloudcastpartnerportalui.pages.titledetaildecoupling.components.launchpacksection.ratingcertificate.templates.iarcRatingCheckbox",
          "boq.cloudcastpartnerportalui.pages.titledetaildecoupling.components.launchpacksection.ratingcertificate.templates.preorderContentIarcConfig",
          "boq.cloudcastpartnerportalui.pages.titledetaildecoupling.components.launchpacksection.releasedatetimezone.templates.targetReleaseDateTooltip",
          "boq.cloudcastpartnerportalui.pages.titledetaildecoupling.components.launchpacksection.tsfremovalm2.templates.enableTsfRemovalMigrationM2",
          "boq.cloudcastpartnerportalui.pages.titledetaildecoupling.components.launchpacksection.tsfremovalm2.templates.notificationBar",
          "boq.cloudcastpartnerportalui.pages.titledetaildecoupling.components.launchpacksection.tsfremoval.templates.notificationBar",
          "boq.cloudcastportalfecommon.widgets.streamabledialog.dialog_model.templates.closingJsactions",
          "boq.cloudcastportalfecommon.widgets.streamabledialog.dialog_model.templates.unstreamableJsactions",
          "boq.dasheradminconsolefrontendapps.servicestatus.messages.templates.marketplaceTitleText",
          "boq.dasheradminconsolefrontendapps.settingslayout.settingsshell.retention.templates.enableInitialization",
          "boq.dasheradminconsolefrontendshell.chrome.navtree.persistcollpasedmode.templates.persistCollapsedModeEnabled",
          "boq.dasheradminconsolefrontendshell.help.enableiphwidget.templates.experimentOn",
          "boq.dasheradminconsolefrontendshell.landing.leftnav.globalparamsmod.templates.globalParamsForCollapsed",
          "boq.dasheradminconsoleui.accountsettings.profile.institute.warning.templates.div",
          "boq.dasheradminconsoleui.accountsettings.reordermod.templates.experimentOn",
          "boq.dasheradminconsoleui.appshealth.grid.templates.uptimeGrid",
          "boq.dasheradminconsoleui.apps.list.additionalgoogleapps.restrictionpanel.templates.main",
          "boq.dasheradminconsoleui.apps.list.common.templates.manageOtherServicesButton",
          "boq.dasheradminconsoleui.companyprofile.legalandcompliance.cloudprivacy.templates.isLaunched",
          "boq.dasheradminconsoleui.companyprofile.preferences.mod.templates.enableOnboardingPromotions",
          "boq.dasheradminconsoleui.domains.allowlisted.limitcheck.templates.isDomainsLimitExperimentEnabled",
          "boq.dasheradminconsoleui.domains.manage.addadomaindialog.scrr.templates.showSCRRDialog",
          "boq.dasheradminconsoleui.domains.manage.changeprimarydomaintoolbarbutton.templates.showToolbarButton",
          "boq.dasheradminconsoleui.group.entitycard.investigation.templates.investigateGroupAction",
          "boq.dasheradminconsoleui.group.list.bulkOperation.templates.bulkDownloadGroupsToolbarMenu",
          "boq.dasheradminconsoleui.group.list.bulkOperation.templates.bulkUploadGroupsToolbarMenu",
          "boq.dasheradminconsoleui.group.list.investigation.templates.groupsInvestigationToolbarMenu",
          "boq.dasheradminconsoleui.group.members.templates.transitiveMembersDownload",
          "boq.dasheradminconsoleui.roles.admins.removedeletedroleassignmentsmod.templates.experimentOn",
          "boq.dasheradminconsoleui.roles.admins.table.templates.bulkSetConditionButton",
          "boq.dasheradminconsoleui.roles.admins.table.templates.conditionColumn",
          "boq.dasheradminconsoleui.roles.admins.table.templates.setConditionButton",
          "boq.dasheradminconsoleui.roles.common.enableroleassignmentsforgroups.templates.isExperimentEnabled",
          "boq.dasheradminconsoleui.roles.list.templates.enableroleslimit.mod.templates.isExperimentEnabled",
          "boq.dasheradminconsoleui.roles.roleassignmentcreator.enableroleslimit.mod.templates.isExperimentEnabled",
          "boq.dasheradminconsoleui.roles.roleassignmentcreator.rows.templates.conditionColumnDataCell",
          "boq.dasheradminconsoleui.roles.roleassignmentcreator.rows.templates.conditionColumnHeader",
          "boq.dasheradminconsoleui.roles.serviceaccountroleassignmentcreator.rows.templates.conditionColumnDataCell",
          "boq.dasheradminconsoleui.roles.serviceaccountroleassignmentcreator.rows.templates.conditionColumnHeader",
          "boq.dasheradminconsoleui.roles.unassignrole.templates.adminrolewarning",
          "boq.dasheradminconsoleui.settings.directory.profileinformation.about.templates.aboutCheckbox",
          "boq.dasheradminconsoleui.settings.directory.profileinformation.limitedprofile.templates.limitedprofileCheckbox",
          "boq.dasheradminconsoleui.settings.directory.profileinformation.location.templates.locationCheckbox",
          "boq.dasheradminconsoleui.settings.directory.profileinformation.mission.templates.missionCheckbox",
          "boq.dasheradminconsoleui.settings.directory.profileinformation.personal.templates.personalCheckbox",
          "boq.dasheradminconsoleui.settings.directory.profileinformation.pronoun.templates.pronounCheckbox",
          "boq.dasheradminconsoleui.settings.serviceonoff.view.templates.marketplaceAppTitle",
          "boq.dasheradminconsoleui.user.bulkdelete.svr.templates.dialogAdditionalClasses",
          "boq.dasheradminconsoleui.user.bulkuserdownload.spreadsheetmod.templates.selectFormat",
          "boq.dasheradminconsoleui.user.deleteuser.svr.templates.dialogAdditionalClasses",
          "boq.dasheradminconsoleui.user.details.scorecardenabled.templates.scorecardEnabled",
          "boq.dasheradminconsoleui.user.details.templates.enableAgeKnownUi",
          "boq.dasheradminconsoleui.user.details.templates.useViewLicensingData",
          "boq.dasheradminconsoleui.user.entitycard.disableoverflow.templates.experimentOn",
          "boq.dasheradminconsoleui.user.evictuser.enablechatwarningmessage.templates.enablechatwarningmessage",
          "boq.dasheradminconsoleui.user.licenses.batch.toolbar.templates.toolbarActions",
          "boq.dasheradminconsoleui.user.list.bulkdelete.templates.button",
          "boq.dasheradminconsoleui.user.list.bulkmoveou.templates.bulkMoveOuButton",
          "boq.dasheradminconsoleui.user.list.bulkuploadusers.templates.menuItem",
          "boq.dasheradminconsoleui.user.list.bulkuserdownload.templates.toolbarButton",
          "boq.dasheradminconsoleui.user.list.cibulkuploadmod.templates.experimentOn",
          "boq.dasheradminconsoleui.user.list.customschema.templates.button",
          "boq.dasheradminconsoleui.user.list.deletedusers.templates.showName",
          "boq.dasheradminconsoleui.user.list.enableentitylist.templates.enableentitylist",
          "boq.dasheradminconsoleui.user.list.restoreusersmod.templates.experimentOn",
          "boq.dasheradminconsoleui.user.list.svrchangesmod.templates.experimentOn",
          "boq.dasheradminconsoleui.user.list.workspacestorage.templates.datacell",
          "boq.dasheradminconsoleui.user.list.workspacestorage.templates.headerCell",
          "boq.dasheradminconsoleui.user.list.workspacestorage.templates.headerLabel",
          "boq.dasheradminconsoleui.user.list.workspacestorage.templates.storageTooltip",
          "boq.dasheradminconsoleui.user.profile.contactsharing.templates.contactsharinglink",
          "boq.dasheradminconsoleui.user.profile.emailrouting.templates.emailroutingSection",
          "boq.dasheradminconsoleui.user.profile.gplusprofile.templates.gplusProfileSection",
          "boq.dasheradminconsoleui.user.profile.templates.contactSharing",
          "boq.dasheradminconsoleui.user.profile.templates.customSchemaList",
          "boq.dasheradminconsoleui.user.profile.useraddress.templates.userAddress",
          "boq.dasheradminconsoleui.user.renameuser.warningmod.templates.showRenameWarning",
          "boq.dasheradminconsoleui.user.unmanaged.enablebulkupdatemod.templates.experimentOn",
          "boq.dasheradminconsoleui.user.userexceptions.gmail.templates.restoreGmailButton",
          "boq.dasheradminconsoleui.user.userexceptions.underaged.templates.updateBirthdayButton",
          "boq.dasheradminconsoleui.user.userroles.enableroleslimit.mod.templates.isExperimentEnabled",
          "boq.dasheradminconsoleui.user.userroles.templates.adminroleoffwarning",
          "boq.dasheradminconsoleui.user.userroles.templates.conditionColumn",
          "boq.dasheradminconsoleui.user.usersmenu.addalternateemails.templates.addAlternateEmailsButton",
          "boq.dasheradminconsoleui.user.usersmenu.moveou.templates.moveToOrg",
          "boq.dasheradminconsoleui.user.usersmenu.moveou.templates.moveToOrgInner",
          "boq.dasheradminconsoleui.user.usersmenu.moveou.templates.moveToOrgOuter",
          "boq.dasheradminconsoleui.user.usersmenu.usermenuscrrmod.templates.enabled",
          "boq.dinerfrontendui.scraper.templates.reviewsResultGroupScraperAttributes",
          "boq.dinerfrontendui.scraper.templates.webResultScraperAttributes",
          "boq.dotssplash.group.templates.isGm3AlphaEnabled",
          "boq.dotssplash.storypanel.templates.isGm3AlphaEnabled",
          "boq.driveadminconsolesettingsweb.defaultthemessettingsgroup.templates.formsDefaultThemesEditView",
          "boq.driveadminconsolesettingsweb.defaultthemessettingsgroup.templates.formsDefaultThemesOverview",
          "boq.driveadminconsolesettingsweb.defaultthemessettingsgroup.templates.sitesDefaultThemesEditView",
          "boq.driveadminconsolesettingsweb.defaultthemessettingsgroup.templates.sitesDefaultThemesOverview",
          "boq.driveadminconsoleui.manageshareddrives.shareddrivestable.appealdisabledshareddrives.templates.moreButtons",
          "boq.driveadminconsoleui.manageshareddrives.shareddrivestable.ou.templates.moreButtons",
          "boq.driveadminconsoleui.manageshareddrives.shareddrivestable.ou.templates.tableTitleWithOuFilter",
          "boq.driveadminconsoleui.manageshareddrives.shareddrivestable.workspacestorage.templates.bulkActions",
          "boq.driveadminconsoleui.manageshareddrives.shareddrivestable.workspacestorage.templates.moreButtons",
          "boq.driveadminconsoleui.manageshareddrives.shareddrivestable.workspacestorage.templates.workspaceStorageEnabled",
          "boq.drivesharedialog.dialogs.aclfixer.templates.dontShareText",
          "boq.drivesharedialog.dialogs.aclfixer.templates.helpLink",
          "boq.drivesharedialog.dialogs.aclfixer.templates.linkVisibilityText",
          "boq.drivesharedialog.dialogs.aclfixer.templates.popoverText",
          "boq.foodorderingfrontendui.views.homepage.foodresultstabpanel.templates.mapHeaderContent",
          "boq.foodorderingfrontendui.widgets.restaurantmap.maptab.templates.jsaction",
          "boq.footprintsmyactivityui.components.locationhistoryreconsentheader.templates.gmmHeader",
          "boq.geocalypsoui.viewport.entities.loading_state_logger.templates",
          "boq.geocalypsoui.viewport.panels.inspector.templates.fprint",
          "boq.geocalypsoui.viewport.panels.inspector.templates.speedLimits",
          "boq.geocalypsoui.viewport.panels.layers.feature_filter.templates.sweepsLayer",
          "boq.geocalypsoui.viewport.tools.separateroadsmod.templates",
          "boq.geomerchantprestoadslandingsharedv2.common.url.templates.buildUrlImpl",
          "boq.geo.ugc.maps.roads.about.start.templates.findOutMore",
          "boq.geo.ugc.maps.roads.about.start.templates.mapping",
          "boq.geo.ugc.maps.roads.challenges.templates.showInDevelopmentMissions",
          "boq.gmfe.embeddedui.editprofile.products.templates.pointyBanner",
          "boq.gmfe.posts.posts.templates.postCreationCardFooter",
          "boq.gmfe.verify.common.steps.common.templates.SetupContentItemClass",
          "boq.googlefinanceui.views.bundles.bundle.ads.templates.imageAd",
          "boq.googlefinanceui.views.bundles.portfolio.templates.plaidErrorBanner",
          "boq.googlefinanceui.views.bundles.watchlist.templates.renderFollowButton",
          "boq.googlefinanceui.views.bundles.watchlist.templates.renderUnfollowButton",
          "boq.googlefinanceui.views.settings.templates.darkThemeSelect",
          "boq.hiringapplicationmanagementui.application.applicationdetails.templates.jobCode",
          "boq.hiringapplicationmanagementui.application.applicationdetails.templates.locationPreferences",
          "boq.hiringapplicationmanagementui.application.applicationdetails.templates.screenerJobDetails",
          "boq.hiringapplicationmanagementui.application.applicationdetails.templates.tenure",
          "boq.hiringcportalfrontendui.applymain.templates.skillsSections",
          "boq.hiringpipelineui.pipeline.pipelinetabs.activecandidatestab.templates.batchremove",
          "boq.hiringpipelineui.pipeline.pipelinetabs.offerdecisioncandidatestab.templates.batchremove",
          "boq.hiringpipelineui.pipeline.pipelinetabs.passivecandidatestab.templates.batchremove",
          "boq.hotrodapiflarequeryserviceui.dashboard.templates.warningSection",
          "boq.infocardui.sharedtemplates.igaupsell.templates.upsell",
          "boq.lensweb.card.common.failureinfo.templates.autoFailureImageSource",
          "boq.lensweb.card.exploregrid.templates.eagerLoadCellImage",
          "boq.localplacesheetmodules.photogallery.templates.verificationBanner",
          "boq.medicaltrapperkeeperui.sandbox_msrl_adherence.exceptions.templates.exceptionExplorerAccordions",
          "boq.medicaltrapperkeeperui.sandbox_msrl_adherence.exceptions.templates.sandboxExceptionGclFilepath",
          "boq.meetingscommon.call.companion.companionscreen.templates.dasherUserLink",
          "boq.meetingscommon.call.companion.companionscreen.templates.eduUserLink",
          "boq.meetingscommon.pip.call.controls.mutebutton.templates.a11yAttributes",
          "boq.meetingscommon.pip.call.controls.mutebutton.templates.deviceMute",
          "boq.meetingscommon.pip.call.controls.mutebutton.templates.homeCameraAvailable",
          "boq.meetingscommon.ui.settings.templates.displayButton",
          "boq.meetingscommon.ui.settings.templates.displayPanel",
          "boq.momafrontendui.teams.teamprofile.contactpanel.contactpanel_edit_issue_tracker.templates.render",
          "boq.momateamsgraphexplorerui.home.sidepanel.enableAaap.templates.navItem",
          "boq.onereviewertoolui.detailedview.common.templates.starToggle",
          "boq.onereviewertoolui.detailedview.launchoverview.templates.crossPwgButtons",
          "boq.onereviewertoolui.revieweritemstable.row.templates.starCell",
          "boq.onereviewertoolui.revieweritemstable.templates.StarHeader",
          "boq.photosui.common.uplabels.templates.lockedFolderModded",
          "boq.photosui.photo.common.movetolockedfolder.templates.itemLockedFolderStateChangedJsactionModded",
          "boq.photosui.photo.common.movetolockedfolder.templates.moveToLockedFolderMenuItemModded",
          "boq.plusappmenu.modadmincustomstream.templates.customStreamMenuContainer",
          "boq.plusappmenu.modbookmarkposts.templates.bookmarksMenu",
          "boq.plusappmenu.modgooglematerialfont.templates.joinIconClass",
          "boq.plusappmenu.modstreamliner.templates.managementToolsMenu",
          "boq.plusappmenu.modstreamliner.templates.managementToolsMenuLink",
          "boq.plusapp.view.templates.scollableClasses",
          "boq.podcastsui.root.templates.showUnsupportedBrowserToast",
          "boq.podcastsui.settings.aadc.templates.aadcToggle",
          "boq.podcastsui.settings.personalization.templates.personalizationToggle",
          "boq.profilepicturepicker.ui.body.templates.darkMode",
          "boq.rtcadminsettingswallpapersweb.settinggroup.templates.canvasDarkGroup",
          "boq.rtcadminsettingswallpapersweb.settinggroup.templates.templateDownloadButton",
          "boq.rtcsupportmqtcommon.ogb.templates.helpLink",
          "boq.rtcsupportmqtcommon.ogb.templates.shortn",
          "boq.saveshared.internalweb.common.templates.darkThemeParam",
          "boq.scriptzui.deobfuscator.profile.templates.render",
          "boq.search.boq.chrome.searchboxweb.desktop.clientadapter.templates.outsideFormOverlays",
          "boq.searchconsole.navigation.templates.httpsDrilldownImpl",
          "boq.searchconsole.navigation.templates.httpsSummaryImpl",
          "boq.searchconsole.navigation.templates.robots",
          "boq.searchconsole.navigation.templates.sc4kEntityLookupHistoryImpl",
          "boq.searchconsole.navigation.templates.sc4kEntityLookupItemDrilldownImpl",
          "boq.searchconsole.navigation.templates.sc4kEntityLookupSummaryImpl",
          "boq.searchconsole.navigation.templates.useTheForceLinkImpl",
          "boq.searchconsole.navigation.templates.videoIndexingDrilldownImpl",
          "boq.searchconsole.navigation.templates.videoIndexingSummaryImpl",
          "boq.searchconsole.verification.templates.dnsCname",
          "boq.sendkit.core.whatabout.templates.whatAbout",
          "boq.shopping.property.ui.components.componentregistry.classes.templates.type",
          "boq.smartprofile.v2.cards.gplusposts.modacuvue.templates.cardClass",
          "boq.smartprofile.v2.cards.gplusposts.modacuvue.templates.postEngagementFooter",
          "boq.snapcatsettings.accountdetails.templates.groupPublisherListSection",
          "boq.squaresmembers.activity.modgooglematerialcommunity.templates.titleControlColor",
          "boq.squaresmembers.memberslist.modgooglematerialstream.templates.footerClass",
          "boq.storageadminconsolesharedweb.storagesummary.templates.chatScorecardItem",
          "boq.streamactionbar.actionbuttons.modbookmarkposts.templates.bookmarkButton",
          "boq.surveyssharedui.confirmation.templates.feedback",
          "boq.surveysui.confirmationview.templates.feedback",
          "boq.surveysui.mysurveys.templates.responseCountCellMod",
          "boq.surveysui.surveycreator.templates.addImageButton",
          "boq.taskswebshared.ssl.ui.starred_view.templates.additionalActions",
          "boq.taskswebshared.ssl.ui.starred_view.templates.bundleModel",
          "boq.taskswebshared.ssl.ui.starred_view.templates.drop_zone",
          "boq.taskswebshared.ssl.ui.starred_view.templates.enablePrinting",
          "boq.taskswebshared.ssl.ui.task_list_switcher.templates.createNewListIcon",
          "boq.taskswebshared.ssl.ui.task_list_switcher.templates.ItemWithIconClass",
          "boq.travel.frontend.ui.body.templates.forPullingInBaseGss",
          "boq.unifiedappsadminconsoleui.list.templates.addPrivateIosAppButton",
          "boq.unifiedappsadminconsoleui.list.templates.privateAppDeletionWarning",
          "boq.unifiedappsadminconsoleui.search.templates.fixLightweightAppMgmt",
          "boq.unifieddevicesui.home.templates.chromeFishFoodLink",
          "boq.unifieddevicesui.home.templates.chromeTrustedTesterLink",
          "boq.visualfrontendviewer.imagecard.templates.actionsOverlay",
          "boq.visualfrontendviewer.imagecard.templates.belowImageBar",
          "boq.visualfrontendviewer.imagecard.templates.footerTypographyClass",
          "boq.visualfrontendviewer.imagecard.templates.hostLinkTitleAttribute",
          "boq.visualfrontendviewer.imagecard.templates.imageAttributes",
          "boq.visualfrontendviewer.imagecard.templates.inlineRelated",
          "boq.visualfrontendviewer.imagecard.templates.mainImageAttrs",
          "boq.visualfrontendviewer.imagecard.templates.mainVideo",
          "boq.visualfrontendviewer.imagecard.templates.navigationButtons",
          "boq.visualfrontendviewer.imagecard.templates.pdfTag",
          "boq.visualfrontendviewer.imagecard.templates.perspectiveImagesStyleAttrs",
          "boq.visualfrontendviewer.imagecard.templates.relatedPlaOfferContentMod",
          "boq.visualfrontendviewer.imagecard.templates.restrictedThumbnailLoader",
          "boq.visualfrontendviewer.imagecard.templates.resultMetadata",
          "boq.visualfrontendviewer.imagecard.templates.sizing",
          "boq.visualfrontendviewer.imagecard.templates.sizingScript",
          "boq.visualfrontendviewer.imagecard.templates.videoDurationOverlay",
          "boq.visualfrontendviewer.imagecard.templates.youtubeVideo",
          "boq.visualfrontendviewer.image.templates.imageAttrs",
          "boq.visualfrontendviewer.image.viewer_long_press.templates.imageAttributes",
          "boq.visualfrontendviewer.ingridsidebysidevisa.templates.render",
          "boq.visualfrontendviewer.ingridvisa.templates.render",
          "boq.visualfrontendviewer.layout.result.templates.nestedCluster",
          "boq.visualfrontendviewer.multiimagecollagevisa.templates.render",
          "boq.visualfrontendviewer.viewerchip.templates.render",
          "boq.visualfrontendviewer.viewercolor.color_classes_xc_viewer.templates.containerClass",
          "boq.visualfrontendviewer.viewercolor.color_classes_xc_viewer.templates.renderStyleTag",
          "boq.voyagerfrontendui.customsurveys.templates.enablebulkimport",
          "boq.voyagerfrontendui.custom.templates.chooseLanguage",
          "boq.voyagerfrontendui.surveycreator.templates.imageranking",
          "boq.voyagerfrontendui.surveycreator.templates.imageslider",
          "boq.voyagerfrontendui.targetaudiencecreatorenableeducationlevelfilter.templates.educationLevelFilter",
          "boq.voyagerfrontendui.targetaudiencecreator.targetingfilters.audienceCategory.templates.affinityAudienceCategoryList",
          "del2.implByInactiveDelPkg",
          "delegateBoo",
          "drive.upload.soy.cellomaillink.contents",
          "drive.upload.soy.debugscottylink.contents",
          "drive.upload.soy.debugscottylink.scottyDashURIDelegate",
          "gcal.mat.bubble.create.soy.common.tasksRemindersMigrationJsActions",
          "gcal.mat.bubble.create.soy.createbubble.reminderMigrationActions",
          "gcal.mat.bubble.edit.reminder.soy.bubble.infobanner",
          "gcal.mat.ui.attendeelist.soy.attendeelist.attendeeLocationIcon",
          "gcal.mat.ui.attendeelist.soy.attendeelist.workingLocation",
          "gcal.mat.ui.attendeelist.soy.attendeelist.workingLocationEverywhereActions",
          "gcal.mat.ui.attendeelist.soy.attendeelist.workingLocationEverywhereActionsAction",
          "gcal.mat.ui.roompicker.autobook.soy.common.autobookMeetupPointNudge",
          "gcal.mat.views.generalsettings.workinghourscard.soy.workinghourscard.columnHeaders",
          "gcal.mat.views.generalsettings.workinghourscard.soy.workinghourscard.jsaction",
          "gcal.mat.views.generalsettings.workinghourscard.soy.workinghourscard.multipleWorkingHoursDetails",
          "gcal.mat.views.generalsettings.workinghourscard.soy.workinghourscard.userLocation",
          "gcal.mat.views.generalsettings.workinghourscard.soy.workinghourscard.workingHoursToggleLabelDescription",
          "gcal.mat.views.generalsettings.workinghourscard.soy.workinghourscard.workingLocation",
          "gcal.mat.views.generalsettings.workinghourscard.soy.workinghourscard.workingLocationToggle",
          "gls.debug.controller.templates.highlightOverlay",
          "gls.debug.controller.templates.main",
          "gls.debug.controller.templates.row",
          "ic.soy.url.isIdentityColorSchemeDark",
          "lifescience.csp.frontend.common.debugurls.templates.account",
          "lifescience.csp.frontend.common.debugurls.templates.adminaccounts",
          "lifescience.csp.frontend.common.mods.experiments.addressconfiglaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.admintasks.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.apivushelpmessagelaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.authorizedusers.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.autopopulatedquestionrendering.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.autopopulateformbuilderlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.casebookenableedcoptin.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.casebookimagetab.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.casebookirtworkflow.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.casebookworklistlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.clonequestionnairelaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.consentcontentchanges.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.consentprintpreviewtruncatedfixlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.consentrefactorlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.consentstudiofontsizelaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.consentstudiogcsuploaderlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.consentstudiolocalizationlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.consentstudiopdffrontendlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.consentstudiopdfpageborderlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.consentstudiosignaturehandwrittenlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.consentstudiosignatureskippablelaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.consentstudiosignaturethirdpartylaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.consentstudiotemplatesearchlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.consentstudiotraceabilityconsentviewlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.consentstudiotraceabilitylaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.consentvideoslaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.ContactInformationApiFromParticipantFrontendLaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.covidpediatricsv1launch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.dedicatededcform.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.deeplinkqueries.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.detroittimezonelabelfixlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.disabledsurveygroupbuttoncolorandtextchange.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.disablelegacymemberportalbannerahalaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.disablelegacymemberportalbannerbhslaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.disablelegacymemberportalbannerc19rlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.disablelegacymemberportalbannerherolaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.disablelegacymemberportalbannerlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.disablelegacymemberportalbannerregistrylaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.displayidverificationstatusinmanagestudies.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.documentidvuxlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.epturndownlaunch.templates.isLaunched",
          "lifescience.csp.frontend.common.mods.experiments.fdacompliantpdflaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.fieldlengthvalidationlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.filterqueriesbyreporterlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.generalizereportspagelaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.halfhourschedulinglaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.headerinformedconsentrendering.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.hidetaskcardappointmentifmissing.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.hlpropagationregistryconsentfromsoylaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.honorsplitregistriesaccountlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.horizontalscalequestionlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.i18naddressusterritorieslaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.imageuploadformbuilder.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.inquirysitelaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.inquirysortcolumnslaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.inquirysubjectidlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.irterrormessage.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.irtinventoryreport.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.irtkitscanning.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.irtreturntodepotreport.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.irtreturntodepot.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.irtsitedestructionreport.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.irtsitedestruction.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.irtsiteinventory.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.irttaskscard.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.languagepicker.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.manageinactivestudieslaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.managestudiesrendererlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.managestudyremovedefaultphonelaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.memberportalbannersourcelaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.migratetimeofday.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.monitoringdateenrolledcolumnlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.monitoringdateenrolledindicatorlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.monitoringfilternotyetactiveparticipantslaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.monitoringnewparticipantstudystateslaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.monitoringopenappointmenticonlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.monitoringsortlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.monitoringstudydayfilterlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.monitoringstudystarttimecolumnlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.monitoringviewrenderinglaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.multifeedlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.multipleconsentcheckboxeslaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.newsfeedapiahalaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.newsfeedapibaselinelaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.newsfeedapic19researchlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.newsfeedapicommunitylaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.newsfeedapicovidreferrallaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.newsfeedapiheroregistrylaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.newsfeedapilaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.newsfeeddisablec19tlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.newsfeeddisableherotherorlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.nonRegistryChainedWorkflowLaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.oneclickunsubscribepagelaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.pastconsentsinmanagestudieslaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.photoreviewlist.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.prttaskgeneraltasklaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.questionnairetranslationproviderui.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.reducelegacymemberportalbannertimelaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.registrysplitconsentoutrocontent.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.registrysplitlistaccountv2.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.renderlocalizedinprogresslaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.rendermemberhomefedslaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.retireunusedshareonfacebooklabel.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.schedulingclosurelaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.sendauthlanguagepropagationlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.sensordatatasksummarycarddescriptionmigrationlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.serverrenderedgdprbanner.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.serverrenderedprivacyfooter.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.settingspageapilaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.settingspagegdprbannerlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.settingsremovedefaultphonelaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.sexatbirthlabel.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.shastaconsentcardbutton.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.shastafunneloptimizationslaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.shastahidebannerandnewsfeed.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.showcookiepolicyinprivacyfooter.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.showgdprconsentbannerlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.showparticipantsubmissiontime.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.showprivacyfooterlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.showsubjectidlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.showtimezone.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.showtopcard.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.signaturelabelsdateconsentchanges.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.stopsupplynumberpaste.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.studystudioaddadminrolefrontendlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.studystudioconfigureconsentdialoglaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.studystudioeditcheckfrontendlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.studystudiorolebasedreviewlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.studystudioroleconfigexportlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.studystudioscheduleofassessmentsdatamodelmigration.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.studystudioscheduleofassessmentsfrontendlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.studystudiositemetadatafrontendlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.supportmanagestudyphoneavailabilitylaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.surveygroupstartbuttonlabelserverlocalized.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.surveyviewexitbutton.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.taskintrosecondarybuttonlabellaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.tasksummarycarddescriptionmigrationlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.tasksummarycarddescriptionmigrationwithsubtitlefallbacklaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.tasksummarycardheadermigrationlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.translatephonenumbercountrieslaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.usenewherotogetherlogo.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.useractivitysummarycontenttranslationlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.usermanagementallowlist.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.usermanagementpage.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.verticalscalequestionlaunch.templates.isEnabled",
          "lifescience.csp.frontend.common.mods.experiments.webnewscardslargerimagelaunch.templates.isEnabled",
          "lso.extraMeta",
          "lso.pageBody",
          "lso.pageHead",
          "lso.pageTitle",
          "nova.images.templates.bordeaux.flip35gWireframe",
          "nova.images.templates.ellen.landingLazyPricingIllo",
          "nova.images.templates.partner_retail_setup.bestBuyLogo",
          "pinto.locker.LockerControlsDialogSoy.attachmentDownloadControlSection",
          "pinto.masthead.metile.MeTileSoy.calendarStatusIcon",
          "pinto.nav.hub.activityfeed.ActivityFeedLinkSoy.activityFeedLink",
          "pinto.nav.hub.tabbednav.appcustomization.AppCustomizationDialogSoy.chatIsVisible",
          "pinto.nav.hub.tabbednav.appcustomization.AppCustomizationDialogSoy.meetIsVisible",
          "pinto.prefsui.ChatSettingsSoy.moddedChatAndMeetTabTitleMsgOrEmpty",
          "pinto.prefsui.ChatSettingsSoy.moddedMeetOnlyTabTitleMsgOrEmpty",
          "roads.suspended.templates.alert",
          "soyfmt.deltemp");

  private final class CheckCallsHelper {

    /** Registry of all templates in the Soy tree. */
    private final FileSetMetadata fileSetMetadata;

    /** Map of all template parameters, both explicit and implicit, organized by template. */
    private final Map<TemplateType, TemplateParamTypes> paramTypesMap = new HashMap<>();

    CheckCallsHelper(FileSetMetadata registry) {
      this.fileSetMetadata = registry;
    }

    void checkCall(TemplateNode callerTemplate, CallBasicNode node) {
      SoyType calleeType = node.getCalleeExpr().getType();
      if (calleeType.getKind() == SoyType.Kind.TEMPLATE) {
        checkCall(callerTemplate, node, (TemplateType) calleeType);
      } else if (calleeType.getKind() == SoyType.Kind.UNION) {
        TemplateContentKind templateContentKind = null;
        for (SoyType member : ((UnionType) calleeType).getMembers()) {
          if (member.getKind() == SoyType.Kind.TEMPLATE) {
            // Check that all members of a union type have the same content kind.
            TemplateType templateType = (TemplateType) member;
            if (templateContentKind == null) {
              templateContentKind = templateType.getContentKind();
            } else if (!templateType.getContentKind().equals(templateContentKind)) {
              errorReporter.report(
                  node.getSourceLocation(),
                  CANNOT_CALL_MIXED_CONTENT_TYPE,
                  templateContentKind,
                  templateType.getContentKind());
            }
            checkCall(callerTemplate, node, templateType);
          } else {
            errorReporter.report(
                node.getSourceLocation(), CAN_ONLY_CALL_TEMPLATE_TYPES, calleeType);
            GlobalNode.replaceExprWithError(node.getCalleeExpr().getChild(0));
          }
        }
      } else if (calleeType.getKind() == SoyType.Kind.UNKNOWN) {
        // We may end up with UNKNOWN here for external calls.
      } else {
        errorReporter.report(node.getSourceLocation(), CAN_ONLY_CALL_TEMPLATE_TYPES, calleeType);
        GlobalNode.replaceExprWithError(node.getCalleeExpr().getChild(0));
      }
    }

    void checkCall(TemplateNode callerTemplate, CallBasicNode node, TemplateType calleeType) {
      checkCallParamNames(node, calleeType);
      checkPassesUnusedParams(node, calleeType);
      checkStrictHtml(callerTemplate, node, calleeType);
      checkCallParamTypes(callerTemplate, node, calleeType);
      checkVariant(node, calleeType);
    }

    void checkCall(
        SoyFileNode file,
        TemplateNode callerTemplate,
        CallDelegateNode node,
        String callerFilename) {
      ImmutableList<TemplateMetadata> potentialCallees =
          fileSetMetadata
              .getDelTemplateSelector()
              .delTemplateNameToValues()
              .get(node.getDelCalleeName());
      for (TemplateMetadata delTemplate : potentialCallees) {
        TemplateType delTemplateType = delTemplate.getTemplateType();
        checkCallParamTypes(callerTemplate, node, delTemplateType);
        checkCallParamNames(node, delTemplateType);
        // We don't call checkPassesUnusedParams here because we might not know all delegates.
      }
      if (shouldEnforceDefaultDeltemplate(node.getDelCalleeName()) && !node.allowEmptyDefault()) {
        ImmutableList<TemplateMetadata> defaultImpl =
            potentialCallees.stream()
                .filter(
                    delTemplate ->
                        delTemplate.getDelPackageName() == null
                            && isNullOrEmpty(delTemplate.getDelTemplateVariant()))
                .collect(toImmutableList());
        if (defaultImpl.isEmpty()) {
          errorReporter.report(
              node.getSourceLocation(), NO_DEFAULT_DELTEMPLATE, node.getDelCalleeName());
        } else {
          SourceFilePath defaultLocation = defaultImpl.get(0).getSourceLocation().getFilePath();
          if (!defaultLocation.equals(file.getSourceLocation().getFilePath())
              && SoyTreeUtils.getAllNodesOfType(file, ImportNode.class).stream()
                  .noneMatch(imp -> imp.getSourceFilePath().equals(defaultLocation))) {
            errorReporter.report(
                node.getSourceLocation(),
                NO_IMPORT_DEFAULT_DELTEMPLATE,
                node.getDelCalleeName(),
                CaseFormat.LOWER_UNDERSCORE.to(
                    CaseFormat.UPPER_CAMEL, defaultLocation.fileName().replaceAll(".soy$", "")),
                defaultLocation.path());
          }
        }
      }
      if (node.allowEmptyDefault()) {
        if (isPasslistedForAllowEmptyDefault(node.getDelCalleeName(), callerFilename)) {
          errorReporter.warn(node.getSourceLocation(), ALLOW_EMPTY_DEFAULT_WARNING);
        } else {
          errorReporter.report(node.getSourceLocation(), ALLOW_EMPTY_DEFAULT_ERROR);
        }
      }

      // NOTE: we only need to check one of them.  If there is more than one of them and they have
      // different content kinds of stricthtml settings then the CheckDelegatesPass will flag that
      // as an error independently.
      if (!potentialCallees.isEmpty()) {
        checkStrictHtml(callerTemplate, node, potentialCallees.get(0).getTemplateType());
      }
    }

    boolean isPasslistedForAllowEmptyDefault(String delCalleeName, String callerFilename) {
      return ALLOWEMPTYDEFAULT_PASSLIST.contains(delCalleeName)
          || delCalleeName.startsWith("boq.dpanelchromeui.") // gencode
          || callerFilename.contains("/java_src/soy/integrationtest");
    }

    boolean shouldEnforceDefaultDeltemplate(String delCalleeName) {
      return !DEFAULT_DELTEMPLATE_PASSLIST.contains(delCalleeName);
    }

    void checkFnCall(TemplateNode callerTemplate, PrintNode printNode, FunctionNode fnNode) {
      TemplateType type;
      if (fnNode.getNameExpr().getType() instanceof TemplateImportType) {
        type = ((TemplateImportType) fnNode.getNameExpr().getType()).getBasicTemplateType();
      } else {
        type = (TemplateType) fnNode.getNameExpr().getType();
      }
      if (fnNode.getParamsStyle() == ParamsStyle.POSITIONAL) {
        errorReporter.report(fnNode.getSourceLocation(), EXPECTED_NAMED_PARAMETERS);
        return;
      }
      checkCallParamNames(fnNode, type);
      checkPassesUnusedParams(fnNode, type);
      checkStrictHtml(callerTemplate, printNode, type);
      checkCallParamTypes(fnNode, type);
    }

    /**
     * Checks that the parameters being passed have compatble types and reports errors if they do
     * not.
     */
    private void checkCallParamTypes(
        TemplateNode callerTemplate, CallNode call, TemplateType callee) {
      TemplateParamTypes calleeParamTypes = getTemplateParamTypes(callee);
      // Explicit params being passed by the CallNode
      Set<String> explicitParams = new HashSet<>();

      // First check all the {param} blocks of the caller to make sure that the types match.
      for (CallParamNode callerParam : call.getChildren()) {
        String paramName = callerParam.getKey().identifier();
        // The types of the parameters. If this is an explicitly declared parameter,
        // then this collection will have only one member; If it's an implicit
        // parameter then this may contain multiple types. Note we don't use
        // a union here because the rules are a bit different than the normal rules
        // for assigning union types.
        // It's possible that the set may be empty, because all of the callees
        // are external. In that case there's nothing we can do, so we don't
        // report anything.
        Collection<SoyType> declaredParamTypes = calleeParamTypes.params.get(paramName);

        // Type of the param value.
        SoyType argType;
        if (callerParam.getKind() == SoyNode.Kind.CALL_PARAM_VALUE_NODE) {
          CallParamValueNode node = (CallParamValueNode) callerParam;
          argType =
              RuntimeTypeCoercion.maybeCoerceType(node.getExpr().getRoot(), declaredParamTypes);
        } else if (callerParam.getKind() == SoyNode.Kind.CALL_PARAM_CONTENT_NODE) {
          SanitizedContentKind contentKind = ((CallParamContentNode) callerParam).getContentKind();
          argType =
              contentKind == null
                  ? StringType.getInstance()
                  : SanitizedType.getTypeForContentKind(contentKind);
        } else {
          throw new AssertionError(); // CallParamNode shouldn't have any other kind of child
        }

        for (SoyType formalType : declaredParamTypes) {
          checkArgumentAgainstParamType(
              callerParam.getSourceLocation(), paramName, argType, formalType, calleeParamTypes);
        }

        explicitParams.add(paramName);
      }

      // If the caller is passing data via data="all" then we look for matching static param
      // declarations in the callers template and see if there are type errors there.
      if (call.isPassingData()) {
        if (call.isPassingAllData()) {
          // Check indirect params that are passed via data="all".
          // We only need to check explicit params of calling template here.
          for (TemplateParam callerParam : callerTemplate.getParams()) {
            String paramName = callerParam.name();

            // The parameter is explicitly overridden with another value, which we
            // already checked.
            if (explicitParams.contains(paramName)) {
              continue;
            }

            Collection<SoyType> declaredParamTypes = calleeParamTypes.params.get(paramName);
            for (SoyType formalType : declaredParamTypes) {
              checkArgumentAgainstParamType(
                  call.getSourceLocation(),
                  paramName,
                  callerParam.type(),
                  formalType,
                  calleeParamTypes);
            }
          }
        } else {
          ExprNode dataExpr = call.getDataExpr();
          // TODO(b/168852179): enforce that the correct set of properties are present
          if (!SoyTypes.isKindOrUnionOfKind(dataExpr.getType(), SoyType.Kind.RECORD)
              && dataExpr.getType().getKind() != SoyType.Kind.UNKNOWN
              // We allow 'any' due to a convention in wiz components :(
              && dataExpr.getType().getKind() != SoyType.Kind.ANY) {
            errorReporter.report(
                dataExpr.getSourceLocation(), INVALID_DATA_EXPR, dataExpr.getType());
          }
        }
      }
    }

    private void checkCallParamTypes(FunctionNode call, TemplateType callee) {
      TemplateParamTypes calleeParamTypes = getTemplateParamTypes(callee);

      // First check all the {param} blocks of the caller to make sure that the types match.
      for (int i = 0; i < call.getParamNames().size(); i++) {
        Identifier callerParam = call.getParamName(i);
        String paramName = callerParam.identifier();
        // The types of the parameters. If this is an explicitly declared parameter,
        // then this collection will have only one member; If it's an implicit
        // parameter then this may contain multiple types. Note we don't use
        // a union here because the rules are a bit different than the normal rules
        // for assigning union types.
        // It's possible that the set may be empty, because all of the callees
        // are external. In that case there's nothing we can do, so we don't
        // report anything.
        Collection<SoyType> declaredParamTypes = calleeParamTypes.params.get(paramName);

        // Type of the param value.
        SoyType argType =
            RuntimeTypeCoercion.maybeCoerceType(call.getParams().get(i), declaredParamTypes);

        for (SoyType formalType : declaredParamTypes) {
          checkArgumentAgainstParamType(
              callerParam.location(), paramName, argType, formalType, calleeParamTypes);
        }
      }
    }

    /**
     * Check that the argument passed to the template is compatible with the template parameter
     * type.
     *
     * @param location The location to report a type check error.
     * @param paramName the name of the parameter.
     * @param argType The type of the value being passed.
     * @param formalType The type of the parameter.
     * @param calleeParams metadata about the callee parameters
     */
    private void checkArgumentAgainstParamType(
        SourceLocation location,
        String paramName,
        SoyType argType,
        SoyType formalType,
        TemplateParamTypes calleeParams) {
      // We use loose assignability because soy templates generate runtime type checking code for
      // parameter types.  So passing loosely typed values will generally be checked at runtime.
      // Our runtime type checking code isn't perfect and varies by backend.
      if (!formalType.isAssignableFromLoose(argType)) {
        if (calleeParams.isIndirect(paramName)
            && formalType.isAssignableFromLoose(SoyTypes.tryRemoveNull(argType))) {
          // Special case for indirect params: Allow a nullable type to be assigned
          // to a non-nullable type if the non-nullable type is an indirect parameter type.
          // The reason is because without flow analysis, we can't know whether or not
          // there are if-statements preventing null from being passed as an indirect
          // param, so we assume all indirect params are optional.
          return;
        }
        errorReporter.report(location, ARGUMENT_TYPE_MISMATCH, paramName, formalType, argType);
      }
    }

    /**
     * Get the parameter types for a callee.
     *
     * @param callee The template being called.
     * @return The set of template parameters, both explicit and implicit.
     */
    private TemplateParamTypes getTemplateParamTypes(TemplateType callee) {
      return paramTypesMap.computeIfAbsent(callee, this::computeTemplateParamTypes);
    }

    private TemplateParamTypes computeTemplateParamTypes(TemplateType callee) {
      TemplateParamTypes paramTypes = new TemplateParamTypes();

      // Store all of the explicitly declared param types
      for (TemplateType.Parameter param : callee.getParameters()) {
        paramTypes.params.put(param.getName(), param.getType());
      }

      // Store indirect params where there's no conflict with explicit params.
      // Note that we don't check here whether the explicit type and the implicit
      // types are in agreement - that will be done when it's this template's
      // turn to be analyzed as a caller.
      IndirectParamsInfo ipi =
          new IndirectParamsCalculator(fileSetMetadata).calculateIndirectParams(callee);
      for (String indirectParamName : ipi.indirectParamTypes.keySet()) {
        if (paramTypes.params.containsKey(indirectParamName)) {
          continue;
        }
        paramTypes.params.putAll(indirectParamName, ipi.indirectParamTypes.get(indirectParamName));
        paramTypes.indirectParamNames.add(indirectParamName);
      }
      return paramTypes;
    }

    /**
     * Helper method that reports an error if a strict html template calls a non-strict html
     * template from HTML context.
     */
    private void checkStrictHtml(
        TemplateNode callerTemplate, CallNode caller, TemplateType callee) {
      // We should only check strict html if 1) the current template
      // sets stricthtml to true, and 2) the current call node is in HTML context.
      // Then we report an error if the callee is HTML but is not a strict HTML template.
      if (callerTemplate.isStrictHtml()
          && caller.getIsPcData()
          && (callee.getContentKind().getSanitizedContentKind().isHtml()
              && !callee.isStrictHtml())) {
        errorReporter.report(caller.getSourceLocation(), STRICT_HTML);
      }
    }

    private void checkStrictHtml(
        TemplateNode callerTemplate, PrintNode caller, TemplateType callee) {
      if (callerTemplate.isStrictHtml()
          && (callee.getContentKind().getSanitizedContentKind().isHtml()
              && !callee.isStrictHtml())) {
        errorReporter.report(caller.getSourceLocation(), STRICT_HTML);
      }
    }

    /**
     * Helper method that reports an error if:
     *
     * <ul>
     *   <li>There are duplicate params for the caller.
     *   <li>Required parameters in callee template are not presented in the caller.
     * </ul>
     */
    private void checkCallParamNames(CallNode caller, TemplateType callee) {
      // Get param keys passed by caller.
      Set<String> callerParamKeys = Sets.newHashSet();
      for (CallParamNode callerParam : caller.getChildren()) {
        boolean isUnique = callerParamKeys.add(callerParam.getKey().identifier());
        if (!isUnique) {
          // Found a duplicate param.
          errorReporter.report(
              callerParam.getKey().location(), DUPLICATE_PARAM, callerParam.getKey().identifier());
        }
      }
      // If all the data keys being passed are listed using 'param' commands, then check that all
      // required params of the callee are included.
      if (!caller.isPassingData()) {
        // Check param keys required by callee.
        List<String> missingParamKeys = Lists.newArrayListWithCapacity(2);
        for (TemplateType.Parameter calleeParam : callee.getParameters()) {
          if (calleeParam.isRequired() && !callerParamKeys.contains(calleeParam.getName())) {
            missingParamKeys.add(calleeParam.getName());
          }
        }
        // Report errors.
        if (!missingParamKeys.isEmpty()) {
          String errorMsgEnd =
              (missingParamKeys.size() == 1)
                  ? "param '" + missingParamKeys.get(0) + "'"
                  : "params " + missingParamKeys;
          errorReporter.report(caller.getSourceLocation(), MISSING_PARAM, errorMsgEnd);
        }
      }
    }

    private void checkCallParamNames(FunctionNode caller, TemplateType callee) {
      // Get param keys passed by caller.
      Set<String> callerParamKeys = Sets.newHashSet();
      for (int i = 0; i < caller.getParams().size(); i++) {
        boolean isUnique = callerParamKeys.add(caller.getParamNames().get(i).identifier());
        if (!isUnique) {
          // Found a duplicate param.
          errorReporter.report(
              caller.getParamNames().get(i).location(),
              DUPLICATE_PARAM,
              caller.getParamNames().get(i).identifier());
        }
      }
      List<String> missingParamKeys = Lists.newArrayListWithCapacity(2);
      for (TemplateType.Parameter calleeParam : callee.getParameters()) {
        if (calleeParam.isRequired() && !callerParamKeys.contains(calleeParam.getName())) {
          missingParamKeys.add(calleeParam.getName());
        }
      }
      // Report errors.
      if (!missingParamKeys.isEmpty()) {
        String errorMsgEnd =
            (missingParamKeys.size() == 1)
                ? "param '" + missingParamKeys.get(0) + "'"
                : "params " + missingParamKeys;
        errorReporter.report(caller.getSourceLocation(), MISSING_PARAM, errorMsgEnd);
      }
    }

    /** Reports error if unused params are passed to a template. */
    private void checkPassesUnusedParams(CallNode caller, TemplateType callee) {
      if (caller.numChildren() == 0) {
        return;
      }
      Set<String> paramNames = Sets.newHashSet();
      for (TemplateType.Parameter param : callee.getParameters()) {
        paramNames.add(param.getName());
      }
      IndirectParamsInfo ipi = null; // Compute only if necessary.
      for (CallParamNode callerParam : caller.getChildren()) {
        String paramName = callerParam.getKey().identifier();
        if (paramNames.contains(paramName)) {
          continue;
        }
        if (ipi == null) {
          ipi = new IndirectParamsCalculator(fileSetMetadata).calculateIndirectParams(callee);
          // If the callee has unknown indirect params then we can't validate that this isn't one
          // of them. So just give up.
          if (ipi.mayHaveIndirectParamsInExternalCalls
              || ipi.mayHaveIndirectParamsInExternalDelCalls) {
            return;
          }
        }
        if (!ipi.indirectParams.containsKey(paramName)) {
          Set<String> allParams =
              ImmutableSet.<String>builder()
                  .addAll(paramNames)
                  .addAll(ipi.indirectParams.keySet())
                  .build();
          errorReporter.report(
              callerParam.getKey().location(),
              PASSES_UNUSED_PARAM,
              paramName,
              callee.getIdentifierForDebugging(),
              SoyErrors.getDidYouMeanMessage(allParams, paramName));
        }
      }
    }

    private void checkPassesUnusedParams(FunctionNode caller, TemplateType callee) {
      if (caller.numChildren() == 0) {
        return;
      }
      Set<String> paramNames = Sets.newHashSet();
      for (TemplateType.Parameter param : callee.getParameters()) {
        paramNames.add(param.getName());
      }
      IndirectParamsInfo ipi = null; // Compute only if necessary.
      for (Identifier callerParam : caller.getParamNames()) {
        String paramName = callerParam.identifier();
        if (paramNames.contains(paramName)) {
          continue;
        }
        if (ipi == null) {
          ipi = new IndirectParamsCalculator(fileSetMetadata).calculateIndirectParams(callee);
          // If the callee has unknown indirect params then we can't validate that this isn't one
          // of them. So just give up.
          if (ipi.mayHaveIndirectParamsInExternalCalls
              || ipi.mayHaveIndirectParamsInExternalDelCalls) {
            return;
          }
        }
        if (!ipi.indirectParams.containsKey(paramName)) {
          Set<String> allParams =
              ImmutableSet.<String>builder()
                  .addAll(paramNames)
                  .addAll(ipi.indirectParams.keySet())
                  .build();
          errorReporter.report(
              callerParam.location(),
              PASSES_UNUSED_PARAM,
              paramName,
              callee.getIdentifierForDebugging(),
              SoyErrors.getDidYouMeanMessage(allParams, paramName));
        }
      }
    }

    /** Validates the "variant" attribute. */
    private void checkVariant(CallBasicNode node, TemplateType calleeType) {
      if (node.getVariantExpr() == null) {
        return;
      }
      if (calleeType.getUseVariantType().equals(NullType.getInstance())) {
        errorReporter.report(node.getSourceLocation(), NO_USEVARIANTTYPE);
      } else if (!calleeType
          .getUseVariantType()
          .isAssignableFromStrict(node.getVariantExpr().getType())) {
        errorReporter.report(
            node.getVariantExpr().getSourceLocation(),
            BAD_VARIANT_TYPE,
            calleeType.getUseVariantType(),
            node.getVariantExpr().getType());
      }
    }
  }

  private static final class TemplateParamTypes {
    final Multimap<String, SoyType> params = HashMultimap.create();
    final Set<String> indirectParamNames = new HashSet<>();

    boolean isIndirect(String paramName) {
      return indirectParamNames.contains(paramName);
    }
  }
}
