# Debugger


[TOC]

The `{debugger}` command can be used to help debug your templates. The command
takes no parameters and prints no content but in various backends it can trigger
specific debugging utilities.

In Javascript, it outputs `debugger;` which will trigger a breakpoint in JS
environments that support it.

In Python, it outputs a call to `pdb.set_trace()`

In Java (`soysauce` only), it calls
`com.google.template.soy.jbcsrc.runtime.JbcSrcRuntime.debugger` which will log a
stack trace. You can place a breakpoint here in order to aid stepping through
and inspecting render state.

For example:

```soy
{template .helloTemplate}
  {log}hello world{/log}
  ...
  {debugger} // how is execution getting here?? maybe the debugger will help?
  ...
{/template}
```
