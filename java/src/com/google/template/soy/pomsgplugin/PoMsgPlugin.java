/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.google.template.soy.pomsgplugin;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.SoyMsgBundleHandler.OutputFileOptions;
import com.google.template.soy.msgs.SoyMsgException;
import com.google.template.soy.msgs.SoyMsgPlugin;

/**
 *
 * @author Stephen Searles <stephen@leapingbrain.com>
 */

@Singleton
public class PoMsgPlugin implements SoyMsgPlugin {

  @Inject
  public PoMsgPlugin() {}


  @Override public CharSequence generateExtractedMsgsFile(
      SoyMsgBundle msgBundle, OutputFileOptions options)
      throws SoyMsgException {

    return PoGenerator.generatePo(
        msgBundle, options.getSourceLocaleString(), options.getTargetLocaleString());
  }


  @Override public SoyMsgBundle parseTranslatedMsgsFile(String inputFileContent)
      throws SoyMsgException {

    try {
      return PoParser.parsePoTargetMsgs(inputFileContent);
    } catch (PoException e) {
      throw new SoyMsgException(e);
    }
  }

}
