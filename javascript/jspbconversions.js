/**
 * @fileoverview Functions to convert safe HTML types under {@code goog.html}
 * to/from their JSPB protocol message representation.
 *
 * @visibility {//visibility:public}
 */

goog.provide('security.html.jspbconversions');

goog.require('goog.html.SafeHtml');
goog.require('goog.html.SafeScript');
goog.require('goog.html.SafeStyle');
goog.require('goog.html.SafeStyleSheet');
goog.require('goog.html.SafeUrl');
goog.require('goog.html.TrustedResourceUrl');
goog.require('goog.html.uncheckedconversions');
goog.require('goog.string.Const');
goog.require('proto.webutil.html.types.SafeHtmlProto');
goog.require('proto.webutil.html.types.SafeScriptProto');
goog.require('proto.webutil.html.types.SafeStyleProto');
goog.require('proto.webutil.html.types.SafeStyleSheetProto');
goog.require('proto.webutil.html.types.SafeUrlProto');
goog.require('proto.webutil.html.types.TrustedResourceUrlProto');

/**
 * Converts a protocol-message form of a goog.html.SafeHtml into a
 * goog.html.SafeHtml instance.
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
 * @param {!proto.webutil.html.types.SafeHtmlProto} proto Protocol message to
 *   convert from.
 * @return {!goog.html.SafeHtml}
 */
security.html.jspbconversions.safeHtmlFromProto = function(proto) {
  return goog.html.uncheckedconversions.
      safeHtmlFromStringKnownToSatisfyTypeContract(
          goog.string.Const.from('From proto message. b/12014412'),
          proto.getPrivateDoNotAccessOrElseSafeHtmlWrappedValue() || '');
};


/**
 * Returns a protocol message representation of the given goog.html.SafeHtml.
 *
 * Such a representation lets the SafeHtml type be preserved across protocol
 * buffer serialization and deserialization.
 *
 * Protocol message forms of this type are intended to be opaque. The fields
 * of the returned protocol message should be considered encapsulated and are
 * not intended for direct inspection or manipulation. Protocol messages can
 * be converted back into an instance of this type using
 * security.html.jspbconversions.safeHtmlFromProto.
 * @param {!goog.html.SafeHtml} safehtml SafeHtml to serialize.
 * @return {!proto.webutil.html.types.SafeHtmlProto}
 */
security.html.jspbconversions.safeHtmlToProto = function(safehtml) {
  var protoHtml = new proto.webutil.html.types.SafeHtmlProto();
  var string = goog.html.SafeHtml.unwrap(safehtml);
  protoHtml.setPrivateDoNotAccessOrElseSafeHtmlWrappedValue(string);
  return protoHtml;
};


/**
 * Converts a protocol-message form of a goog.html.SafeScript into a
 * goog.html.SafeScript instance.
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
 * @param {!proto.webutil.html.types.SafeScriptProto} proto Protocol message to
 *   convert from.
 * @return {!goog.html.SafeScript}
 * @suppress {visibility}
 */
security.html.jspbconversions.safeScriptFromProto = function(proto) {
  return goog.html.uncheckedconversions
      .safeScriptFromStringKnownToSatisfyTypeContract(
          goog.string.Const.from('From proto message. b/12014412'),
          proto.getPrivateDoNotAccessOrElseSafeScriptWrappedValue() || '');
};


/**
 * Returns a protocol message representation of the given goog.html.SafeScript.
 *
 * Such a representation lets the SafeScript type be preserved across protocol
 * buffer serialization and deserialization.
 *
 * Protocol message forms of this type are intended to be opaque. The fields
 * of the returned protocol message should be considered encapsulated and are
 * not intended for direct inspection or manipulation. Protocol messages can
 * be converted back into an instance of this type using
 * security.html.jspbconversions.safeScriptFromProto.
 * @param {!goog.html.SafeScript} script SafeScript to serialize.
 * @return {!proto.webutil.html.types.SafeScriptProto}
 */
security.html.jspbconversions.safeScriptToProto = function(script) {
  var protoScript = new proto.webutil.html.types.SafeScriptProto();
  var string = goog.html.SafeScript.unwrap(script);
  protoScript.setPrivateDoNotAccessOrElseSafeScriptWrappedValue(string);
  return protoScript;
};


/**
 * Converts a protocol-message form of a goog.html.SafeStyle into a
 * goog.html.SafeStyle instance.
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
 * @param {!proto.webutil.html.types.SafeStyleProto} proto Protocol message to
 *   convert from.
 * @return {!goog.html.SafeStyle}
 * @suppress {visibility}
 */
security.html.jspbconversions.safeStyleFromProto = function(proto) {
  return goog.html.uncheckedconversions.
      safeStyleFromStringKnownToSatisfyTypeContract(
          goog.string.Const.from('From proto message. b/12014412'),
          proto.getPrivateDoNotAccessOrElseSafeStyleWrappedValue() || '');
};


/**
 * Returns a protocol message representation of the given goog.html.SafeStyle.
 *
 * Such a representation lets the SafeStyle type be preserved across protocol
 * buffer serialization and deserialization.
 *
 * Protocol message forms of this type are intended to be opaque. The fields
 * of the returned protocol message should be considered encapsulated and are
 * not intended for direct inspection or manipulation. Protocol messages can
 * be converted back into an instance of this type using
 * security.html.jspbconversions.safeStyleFromProto.
 * @param {!goog.html.SafeStyle} style SafeStyle to serialize.
 * @return {!proto.webutil.html.types.SafeStyleProto}
 */
security.html.jspbconversions.safeStyleToProto = function(style) {
  var protoStyle = new proto.webutil.html.types.SafeStyleProto();
  var string = goog.html.SafeStyle.unwrap(style);
  protoStyle.setPrivateDoNotAccessOrElseSafeStyleWrappedValue(string);
  return protoStyle;
};


/**
 * Converts a protocol-message form of a goog.html.SafeStyleSheet into a
 * goog.html.SafeStyleSheet instance.
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
 * @param {!proto.webutil.html.types.SafeStyleSheetProto} proto Protocol message to
 *   convert from.
 * @return {!goog.html.SafeStyleSheet}
 * @suppress {visibility}
 */
security.html.jspbconversions.safeStyleSheetFromProto = function(proto) {
  return goog.html.uncheckedconversions
      .safeStyleSheetFromStringKnownToSatisfyTypeContract(
          goog.string.Const.from('From proto message. b/12014412'),
          proto.getPrivateDoNotAccessOrElseSafeStyleSheetWrappedValue() || '');
};


/**
 * Returns a protocol message representation of the given
 * goog.html.SafeStyleSheet.
 *
 * Such a representation lets the SafeStyleSheet type be preserved across
 * protocol buffer serialization and deserialization.
 *
 * Protocol message forms of this type are intended to be opaque. The fields
 * of the returned protocol message should be considered encapsulated and are
 * not intended for direct inspection or manipulation. Protocol messages can
 * be converted back into an instance of this type using
 * security.html.jspbconversions.safeStyleSheetFromProto.
 * @param {!goog.html.SafeStyleSheet} styleSheet SafeStyleSheet to serialize.
 * @return {!proto.webutil.html.types.SafeStyleSheetProto}
 */
security.html.jspbconversions.safeStyleSheetToProto = function(styleSheet) {
  var protoStyleSheet = new proto.webutil.html.types.SafeStyleSheetProto();
  var string = goog.html.SafeStyleSheet.unwrap(styleSheet);
  protoStyleSheet.setPrivateDoNotAccessOrElseSafeStyleSheetWrappedValue(string);
  return protoStyleSheet;
};


/**
 * Converts a protocol-message form of a goog.html.SafeUrl into a
 * goog.html.SafeUrl instance.
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
 * @param {!proto.webutil.html.types.SafeUrlProto} proto Protocol message to
 *   convert from.
 * @return {!goog.html.SafeUrl}
 * @suppress {visibility}
 */
security.html.jspbconversions.safeUrlFromProto = function(proto) {
  return goog.html.uncheckedconversions.
      safeUrlFromStringKnownToSatisfyTypeContract(
          goog.string.Const.from('From proto message. b/12014412'),
          proto.getPrivateDoNotAccessOrElseSafeUrlWrappedValue() || '');
};


/**
 * Returns a protocol message representation of the given goog.html.SafeUrl.
 *
 * Such a representation lets the SafeUrl type be preserved across protocol
 * buffer serialization and deserialization.
 *
 * Protocol message forms of this type are intended to be opaque. The fields
 * of the returned protocol message should be considered encapsulated and are
 * not intended for direct inspection or manipulation. Protocol messages can
 * be converted back into an instance of this type using
 * security.html.jspbconversions.safeUrlFromProto.
 * @param {!goog.html.SafeUrl} url SafeUrl to serialize.
 * @return {!proto.webutil.html.types.SafeUrlProto}
 */
security.html.jspbconversions.safeUrlToProto = function(url) {
  var protoUrl = new proto.webutil.html.types.SafeUrlProto();
  var string = goog.html.SafeUrl.unwrap(url);
  protoUrl.setPrivateDoNotAccessOrElseSafeUrlWrappedValue(string);
  return protoUrl;
};


/**
 * Converts a protocol-message form of a goog.html.TrustedResourceUrl into a
 * goog.html.TrustedResourceUrl instance.
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
 * @param {!proto.webutil.html.types.TrustedResourceUrlProto} proto Protocol message to
 *   convert from.
 * @return {!goog.html.TrustedResourceUrl}
 * @suppress {visibility}
 */
security.html.jspbconversions.trustedResourceUrlFromProto = function(proto) {
  return goog.html.uncheckedconversions.
      trustedResourceUrlFromStringKnownToSatisfyTypeContract(
          goog.string.Const.from('From proto message. b/12014412'),
          proto.getPrivateDoNotAccessOrElseTrustedResourceUrlWrappedValue() ||
              '');
};


/**
 * Returns a protocol message representation of the given
 * goog.html.TrustedResourceUrl.
 *
 * Such a representation lets the TrustedResourceUrl type be preserved across
 * protocol buffer serialization and deserialization.
 *
 * Protocol message forms of this type are intended to be opaque. The fields
 * of the returned protocol message should be considered encapsulated and are
 * not intended for direct inspection or manipulation. Protocol messages can
 * be converted back into an instance of this type using
 * security.html.jspbconversions.trustedResourceUrlFromProto.
 * @param {!goog.html.TrustedResourceUrl} url TrustedResourceUrl to serialize.
 * @return {!proto.webutil.html.types.TrustedResourceUrlProto}
 */
security.html.jspbconversions.trustedResourceUrlToProto = function(url) {
  var protoUrl = new proto.webutil.html.types.TrustedResourceUrlProto();
  var string = goog.html.TrustedResourceUrl.unwrap(url);
  protoUrl.setPrivateDoNotAccessOrElseTrustedResourceUrlWrappedValue(string);
  return protoUrl;
};
