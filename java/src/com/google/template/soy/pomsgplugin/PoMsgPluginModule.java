/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.google.template.soy.pomsgplugin;

import com.google.inject.AbstractModule;
import com.google.template.soy.msgs.SoyMsgPlugin;

/**
 * Guice module to bind the PoMsgPlugin.
 *
 * @author Stephen Searles <stephen@leapingbrain.com>
 */
public class PoMsgPluginModule extends AbstractModule {

  @Override protected void configure() {

    bind(SoyMsgPlugin.class).to(PoMsgPlugin.class);
  }
}
