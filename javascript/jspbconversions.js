/**
 * @fileoverview Functions to convert safe HTML types under `goog.html`
 * to/from their JSPB protocol message representation.
 *
 */

goog.module('security.html.jspbconversions');
goog.module.declareLegacyNamespace();

const Const = goog.require('goog.string.Const');
const SafeHtml = goog.require('goog.html.SafeHtml');
const SafeHtmlProto = goog.require('proto.webutil.html.types.SafeHtmlProto');
const SafeScript = goog.require('goog.html.SafeScript');
const SafeScriptProto = goog.require('proto.webutil.html.types.SafeScriptProto');
const SafeStyle = goog.require('goog.html.SafeStyle');
const SafeStyleProto = goog.require('proto.webutil.html.types.SafeStyleProto');
const SafeStyleSheet = goog.require('goog.html.SafeStyleSheet');
const SafeStyleSheetProto = goog.require('proto.webutil.html.types.SafeStyleSheetProto');
const SafeUrl = goog.require('goog.html.SafeUrl');
const SafeUrlProto = goog.require('proto.webutil.html.types.SafeUrlProto');
const TrustedResourceUrl = goog.require('goog.html.TrustedResourceUrl');
const TrustedResourceUrlProto = goog.require('proto.webutil.html.types.TrustedResourceUrlProto');
const uncheckedconversions = goog.require('goog.html.uncheckedconversions');

/**
 * Converts a protocol-message form of a SafeHtml into a
 * SafeHtml instance.
 *
 * Protocol-message forms are intended to be opaque. The fields of the
 * protocol message should be considered encapsulated and are not intended for
 * direct inspection or manipulation. Protocol message forms of this type
 * should be produced by safeHtmlToProto or its equivalent in other
 * implementation languages.
 *
 * Important: It is generally unsafe to invoke this method on a protocol
 * message that has been received from an entity outside the application's
 * trust domain. Data coming from the browser is outside the application's trust
 * domain.
 * @param {!SafeHtmlProto} proto Protocol message to
 *   convert from.
 * @return {!SafeHtml}
 */
function safeHtmlFromProto(proto) {
  return uncheckedconversions.safeHtmlFromStringKnownToSatisfyTypeContract(
      Const.from('From proto message. b/12014412'),
      proto.getPrivateDoNotAccessOrElseSafeHtmlWrappedValue() || '');
}

/**
 * Returns a protocol message representation of the given SafeHtml.
 *
 * Such a representation lets the SafeHtml type be preserved across protocol
 * buffer serialization and deserialization.
 *
 * Protocol message forms of this type are intended to be opaque. The fields
 * of the returned protocol message should be considered encapsulated and are
 * not intended for direct inspection or manipulation. Protocol messages can
 * be converted back into an instance of this type using
 * safeHtmlFromProto.
 * @param {!SafeHtml} safehtml SafeHtml to serialize.
 * @return {!SafeHtmlProto}
 */
function safeHtmlToProto(safehtml) {
  var protoHtml = new SafeHtmlProto();
  var string = SafeHtml.unwrap(safehtml);
  protoHtml.setPrivateDoNotAccessOrElseSafeHtmlWrappedValue(string);
  return protoHtml;
}

/**
 * Converts a protocol-message form of a SafeScript into a
 * SafeScript instance.
 *
 * Protocol-message forms are intended to be opaque. The fields of the
 * protocol message should be considered encapsulated and are not intended for
 * direct inspection or manipulation. Protocol message forms of this type
 * should be produced by SafeScriptToProto or its equivalent in other
 * implementation languages.
 *
 * Important: It is generally unsafe to invoke this method on a protocol
 * message that has been received from an entity outside the application's
 * trust domain. Data coming from the browser is outside the application's trust
 * domain.
 * @param {!SafeScriptProto} proto Protocol message to
 *   convert from.
 * @return {!SafeScript}
 * @suppress {visibility}
 */
function safeScriptFromProto(proto) {
  return uncheckedconversions.safeScriptFromStringKnownToSatisfyTypeContract(
      Const.from('From proto message. b/12014412'),
      proto.getPrivateDoNotAccessOrElseSafeScriptWrappedValue() || '');
}

/**
 * Returns a protocol message representation of the given SafeScript.
 *
 * Such a representation lets the SafeScript type be preserved across protocol
 * buffer serialization and deserialization.
 *
 * Protocol message forms of this type are intended to be opaque. The fields
 * of the returned protocol message should be considered encapsulated and are
 * not intended for direct inspection or manipulation. Protocol messages can
 * be converted back into an instance of this type using
 * safeScriptFromProto.
 * @param {!SafeScript} script SafeScript to serialize.
 * @return {!SafeScriptProto}
 */
function safeScriptToProto(script) {
  var protoScript = new SafeScriptProto();
  var string = SafeScript.unwrap(script);
  protoScript.setPrivateDoNotAccessOrElseSafeScriptWrappedValue(string);
  return protoScript;
}

/**
 * Converts a protocol-message form of a SafeStyle into a
 * SafeStyle instance.
 *
 * Protocol-message forms are intended to be opaque. The fields of the
 * protocol message should be considered encapsulated and are not intended for
 * direct inspection or manipulation. Protocol message forms of this type
 * should be produced by safeStyleToProto or its equivalent in other
 * implementation languages.
 *
 * Important: It is generally unsafe to invoke this method on a protocol
 * message that has been received from an entity outside the application's
 * trust domain. Data coming from the browser is outside the application's trust
 * domain.
 * @param {!SafeStyleProto} proto Protocol message to
 *   convert from.
 * @return {!SafeStyle}
 * @suppress {visibility}
 */
function safeStyleFromProto(proto) {
  return uncheckedconversions.safeStyleFromStringKnownToSatisfyTypeContract(
      Const.from('From proto message. b/12014412'),
      proto.getPrivateDoNotAccessOrElseSafeStyleWrappedValue() || '');
}

/**
 * Returns a protocol message representation of the given SafeStyle.
 *
 * Such a representation lets the SafeStyle type be preserved across protocol
 * buffer serialization and deserialization.
 *
 * Protocol message forms of this type are intended to be opaque. The fields
 * of the returned protocol message should be considered encapsulated and are
 * not intended for direct inspection or manipulation. Protocol messages can
 * be converted back into an instance of this type using
 * safeStyleFromProto.
 * @param {!SafeStyle} style SafeStyle to serialize.
 * @return {!SafeStyleProto}
 */
function safeStyleToProto(style) {
  var protoStyle = new SafeStyleProto();
  var string = SafeStyle.unwrap(style);
  protoStyle.setPrivateDoNotAccessOrElseSafeStyleWrappedValue(string);
  return protoStyle;
}

/**
 * Converts a protocol-message form of a SafeStyleSheet into a
 * SafeStyleSheet instance.
 *
 * Protocol-message forms are intended to be opaque. The fields of the
 * protocol message should be considered encapsulated and are not intended for
 * direct inspection or manipulation. Protocol message forms of this type
 * should be produced by SafeStyleSheetToProto or its equivalent in other
 * implementation languages.
 *
 * Important: It is generally unsafe to invoke this method on a protocol
 * message that has been received from an entity outside the application's
 * trust domain. Data coming from the browser is outside the application's trust
 * domain.
 * @param {!SafeStyleSheetProto} proto Protocol message to
 *   convert from.
 * @return {!SafeStyleSheet}
 * @suppress {visibility}
 */
function safeStyleSheetFromProto(proto) {
  return uncheckedconversions
      .safeStyleSheetFromStringKnownToSatisfyTypeContract(
          Const.from('From proto message. b/12014412'),
          proto.getPrivateDoNotAccessOrElseSafeStyleSheetWrappedValue() || '');
}

/**
 * Returns a protocol message representation of the given
 * SafeStyleSheet.
 *
 * Such a representation lets the SafeStyleSheet type be preserved across
 * protocol buffer serialization and deserialization.
 *
 * Protocol message forms of this type are intended to be opaque. The fields
 * of the returned protocol message should be considered encapsulated and are
 * not intended for direct inspection or manipulation. Protocol messages can
 * be converted back into an instance of this type using
 * safeStyleSheetFromProto.
 * @param {!SafeStyleSheet} styleSheet SafeStyleSheet to serialize.
 * @return {!SafeStyleSheetProto}
 */
function safeStyleSheetToProto(styleSheet) {
  var protoStyleSheet = new SafeStyleSheetProto();
  var string = SafeStyleSheet.unwrap(styleSheet);
  protoStyleSheet.setPrivateDoNotAccessOrElseSafeStyleSheetWrappedValue(string);
  return protoStyleSheet;
}

/**
 * Converts a protocol-message form of a SafeUrl into a
 * SafeUrl instance.
 *
 * Protocol-message forms are intended to be opaque. The fields of the
 * protocol message should be considered encapsulated and are not intended for
 * direct inspection or manipulation. Protocol message forms of this type
 * should be produced by safeUrlToProto or its equivalent in other
 * implementation languages.
 *
 * Important: It is generally unsafe to invoke this method on a protocol
 * message that has been received from an entity outside the application's
 * trust domain. Data coming from the browser is outside the application's trust
 * domain.
 * @param {!SafeUrlProto} proto Protocol message to
 *   convert from.
 * @return {!SafeUrl}
 * @suppress {visibility}
 */
function safeUrlFromProto(proto) {
  return uncheckedconversions.safeUrlFromStringKnownToSatisfyTypeContract(
      Const.from('From proto message. b/12014412'),
      proto.getPrivateDoNotAccessOrElseSafeUrlWrappedValue() || '');
}

/**
 * Returns a protocol message representation of the given SafeUrl.
 *
 * Such a representation lets the SafeUrl type be preserved across protocol
 * buffer serialization and deserialization.
 *
 * Protocol message forms of this type are intended to be opaque. The fields
 * of the returned protocol message should be considered encapsulated and are
 * not intended for direct inspection or manipulation. Protocol messages can
 * be converted back into an instance of this type using
 * safeUrlFromProto.
 * @param {!SafeUrl} url SafeUrl to serialize.
 * @return {!SafeUrlProto}
 */
function safeUrlToProto(url) {
  var protoUrl = new SafeUrlProto();
  var string = SafeUrl.unwrap(url);
  protoUrl.setPrivateDoNotAccessOrElseSafeUrlWrappedValue(string);
  return protoUrl;
}

/**
 * Converts a protocol-message form of a TrustedResourceUrl into a
 * TrustedResourceUrl instance.
 *
 * Protocol-message forms are intended to be opaque. The fields of the
 * protocol message should be considered encapsulated and are not intended for
 * direct inspection or manipulation. Protocol message forms of this type
 * should be produced by trustedResourceUrlToProto or its equivalent in other
 * implementation languages.
 *
 * Important: It is generally unsafe to invoke this method on a protocol
 * message that has been received from an entity outside the application's
 * trust domain. Data coming from the browser is outside the application's trust
 * domain.
 * @param {!TrustedResourceUrlProto} proto Protocol message to
 *   convert from.
 * @return {!TrustedResourceUrl}
 * @suppress {visibility}
 */
function trustedResourceUrlFromProto(proto) {
  return uncheckedconversions
      .trustedResourceUrlFromStringKnownToSatisfyTypeContract(
          Const.from('From proto message. b/12014412'),
          proto.getPrivateDoNotAccessOrElseTrustedResourceUrlWrappedValue() ||
              '');
}

/**
 * Returns a protocol message representation of the given
 * TrustedResourceUrl.
 *
 * Such a representation lets the TrustedResourceUrl type be preserved across
 * protocol buffer serialization and deserialization.
 *
 * Protocol message forms of this type are intended to be opaque. The fields
 * of the returned protocol message should be considered encapsulated and are
 * not intended for direct inspection or manipulation. Protocol messages can
 * be converted back into an instance of this type using
 * trustedResourceUrlFromProto.
 * @param {!TrustedResourceUrl} url TrustedResourceUrl to serialize.
 * @return {!TrustedResourceUrlProto}
 */
function trustedResourceUrlToProto(url) {
  var protoUrl = new TrustedResourceUrlProto();
  var string = TrustedResourceUrl.unwrap(url);
  protoUrl.setPrivateDoNotAccessOrElseTrustedResourceUrlWrappedValue(string);
  return protoUrl;
}

exports = {
  safeHtmlFromProto,
  safeHtmlToProto,
  safeScriptFromProto,
  safeScriptToProto,
  safeStyleFromProto,
  safeStyleSheetFromProto,
  safeStyleSheetToProto,
  safeStyleToProto,
  safeUrlFromProto,
  safeUrlToProto,
  trustedResourceUrlFromProto,
  trustedResourceUrlToProto,
};
