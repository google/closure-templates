# Message Localization


[TOC]

## Message translation

### Marking a Message for Translation {#marking}

In Soy, messages for translation are inlined rather than stored in separate
files. To mark a message for translation, surround the message text with the
`msg` tag as described in the [Commands chapter](../reference/messages.md#msg).
Soy can extract your message into the XLIFF. Furthermore, use translated message
files in the same format and insert the translated text back into your template.

### Adding new messages {#newmessages}

When a new message is added to a `.soy` file, there is no translation available
for that message yet. If the message is immediately used in the product, it may
appear in English to users of other languages. Therefore, the message should be
introduced to be translated before it is actually used.

There are two ways to do this, depending on whether you are adding a new message
or modifying an existing message.

For new messages, add the new message in an unused template:

```soy
{template .unusedTemplateForTranslations}
  {msg desc="..."}...{/msg}
{/template}
```

When modifying existing messages, use the `fallbackmsg` command described in the
[commands documentation](../reference/messages.md#fallbackmsg).

### Extracting Messages {#extractingmessages}

To parse and extract messages from a bundle of `.soy` files, download
[`closure-templates-msg-extractor-latest.zip`](https://dl.google.com/closure-templates/closure-templates-msg-extractor-latest.zip)
and extract the latest revision of `SoyMsgExtractor.jar`. By default,
`SoyMsgExtractor` uses the plugin `XliffMsgPlugin`, which supports the industry
standard [XLIFF message file
format](http://docs.oasis-open.org/xliff/xliff-core/xliff-core.html). If you
need to extract a message to a different message file format, follow the
instructions in the [Message Plugins](plugins.md#message) section of the Plugins
chapter.

After you have downloaded the message extractor, run it to extract your messages
by passing in the source `.soy` file and the filename for the extracted
messages. For example, if you have two files `aaa.soy` and `bbb.soy`, and you
want to extract messages to a file called `extracted_msgs.xlf`, run the
following command:

```shell
$ java -jar SoyMsgExtractor.jar  --outputFile extracted_msgs.xlf  aaa.soy bbb.soy
```

To specify the target language as seen by message plugins, use the
`--targetLocaleString` flag.

```shell
$ java -jar SoyMsgExtractor.jar --outputFile extracted_msgs.xlf --targetLocaleString zh-TW *.soy
```

Instead of specifying a single output file, you can use `--outputPathFormat` to
derive it from the input file paths.

```shell
$ java -jar SoyMsgExtractor.jar --outputPathFormat "{INPUT_DIRECTORY}/../messages/{INPUT_FILE_NAME_NO_EXT}.xlf" ...
```

To see a description of all the flags, run

```shell
$ java -jar SoyMsgExtractor.jar
```

without any options.

### Inserting Messages {#insertingmessages}

Because the template compiler can use the translated messages file directly with
the help of an appropriate message plugin, you don't need to run an additional
preprocessing step to change them to a different format.

#### In Java usage

To insert messages in Java usage, use a `SoyMsgBundleHandler` to create a
`SoyMsgBundle` that contains your translated messages. For example, if you have
a translated messages file called `translated_msgs_pt-BR.xlf` for the locale
`pt-BR`, use the code snippets below to create a corresponding `SoyMsgBundle`:

```java
SoyMsgBundleHandler msgBundleHandler = new SoyMsgBundleHandler(new XliffMsgPlugin());
SoyMsgBundle msgBundle = msgBundleHandler.createFromFile(new File("translated_msgs_pt-BR.xlf"));
```

After you have a `SoyMsgBundle` object that contains your translated messages,
you can pass it to your `SoyTofu.Renderer` or `SoySauce.Renderer` object's
`setMsgBundle()` method.

#### In JavaScript usage

To insert messages in JavaScript usage, pass the translated messages file to
`SoyToJsSrcCompiler`, which generates JavaScript code that contains the
translated messages. For example, if you want to create a translated messages
file called `translated_msgs_pt-BR.xlf` for locale `pt-BR`, run this command:

```shell
$ java -jar SoyToJsSrcCompiler.jar  --locales pt-BR  --messageFilePathFormat translated_msgs_pt-BR.xlf  \
      --outputPathFormat '{INPUT_FILE_NAME_NO_EXT}_pt-BR.js'  aaa.soy bbb.soy
```

This command generates the files `aaa_pt-BR.js` and `bbb_pt-BR.js`.

The `SoyToJsSrcCompiler` can simultaneously compile to multiple locales. For
example, to compile to locales `en`, `de`, and `pt-BR` (assuming your translated
messages files are named `translated_msgs_en.xlf`, `translated_msgs_de.xlf`, and
`translated_msgs_pt-BR.xlf`), run this command:

```shell
$ java -jar SoyToJsSrcCompiler.jar  --locales en,de,pt-BR  --messageFilePathFormat translated_msgs_{LOCALE}.xlf \
      --outputPathFormat '{INPUT_FILE_NAME_NO_EXT}_{LOCALE}.js'  aaa.soy bbb.soy
```

This command generates six files, one for each combination of `.soy` source file
and locale: `aaa_en.js`, `bbb_en.js`, `aaa_de.js`, `bbb_de.js`, `aaa_pt-BR.js`,
and `bbb_pt-BR.js`.

### Letting Closure Compiler Handle Translation {#closurecompiler}

If your project only uses Closure from JavaScript, an alternative translation
solution is to let Closure Compiler handle the translation of messages, just as
it would for your hand-written JavaScript. However, if you share templates
between Java and JavaScript, you should **always** use Soy to handle messages
because of correctness issues.

To let Closure Compiler handle the extraction and insertion of messages, run the
`SoyToJsSrcCompiler` with these options:

-   `--should_generate_goog_msg_defs`: causes the compiler to turn all `msg`
    blocks into `goog.getMsg` definitions (and their corresponding usages).
    These `goog.getMsg` definitions can be translated by the JS Compiler.

-   `--bidi_global_dir=<1/-1>`: provides the bidi global directionality
    (ltr=`1`, rtl=`-1`) to the compiler so it can correctly handle [bidi
    functions and directives](#bidi_functions).

For example, consider this `msg` block:

```soy
{msg desc="Says hello and tells user to click a link."}
  Hello {$userName}! Please click <a href="{$url}">here</a>.
{/msg}
```

If you compiled this template with the option `--should_generate_goog_msg_defs`,
then the resulting `goog.getMsg` definition might be:

```js
/** @desc Says hello and tells user to click a link. */
var MSG_UNNAMED_42 = goog.getMsg(
    'Hello {$userName}! Please click {$startLink}here{$endLink}.',
    {'userName': soy.$$escapeHtml(opt_data.userName),
     'startLink': '<a href="' + soy.$$escapeHtml(opt_data.url) + '">',
     'endLink': '</a>'});
```


## Using Multiple Natural Languages (Bidi)

Soy templates support bidirectional text (bidi) with seven functions and two
print directives. Note that you might suffer a slight performance loss when
using some of these functions or directives because some of them can only be
applied at render time.

By default, the template compiler determines a global directionality (LTR or
RTL) for the template content based on the language of the message bundle.
Provide the message bundle at compile time for the JavaScript source backend and
at render time for the Java object backend. If you don't provide a message
bundle, the global directionality defaults to LTR. The global directionality
only affects the operation of Soy when you explicitly use bidi functions or
directives.

For an example of a template that uses Bidi functions, see
[`examples/features.soy`](https://github.com/google/closure-templates/blob/master/examples/features.soy).

### Bidi Functions in Soy {#bidi_functions}


<table>
<thead>
<tr>
<th>Function</th>
<th>Usage</th>
</tr>
</thead>

<tbody>
<tr>
<td><code>bidiGlobalDir()</code>

</td>
<td>Provides a way to check the current
global directionality. Returns 1 for LTR
or -1 for RTL.</td>
</tr>
<tr>
<td><code>bidiDirAttr(text, opt_isHtml)</code>


</td>
<td>If the overall directionality of <code>text</code>
is different from the global
directionality, then this function
generates the attribute <code>dir=ltr</code> or
<code>dir=rtl</code>, which you can include in the
HTML tag surrounding that piece of text.
If the overall directionality of <code>text</code>
is the same as the global
directionality, this function returns
the empty string. Set the optional
second parameter to <code>true</code> if <code>text</code>
contains or can contain HTML tags or
HTML escape sequences (default <code>false</code>).</td>
</tr>
<tr>
<td><code>bidiMark()</code>

</td>
<td>Generates the bidi mark formatting
character (LRM or RLM) that corresponds
to the global directionality. Note that
if you don't want to insert this mark
unconditionally, you should use
<code>bidiMarkAfter(text)</code> instead.</td>
</tr>
<tr>
<td><code>bidiMarkAfter(text, opt_isHtml)</code>


</td>
<td>If the exit (not overall) directionality
of <code>text</code> is different from the global
directionality, then this function
generates either the LRM or RLM
character that corresponds to the global
directionality. If the exit
directionality of <code>text</code> is the same as
the global directionality, this function
returns the empty string. Set the
optional second parameter to <code>true</code> if
<code>text</code> contains or can contain HTML tags
or HTML escape sequences (default
<code>false</code>). You should use this function
for an inline section of text that might
be opposite directionality from the
global directionality. Also, set <code>text</code>
to the text that precedes this function.</td>
</tr>
<tr>
<td><code>bidiStartEdge()</code>

</td>
<td>Generates the string "left" or the
string "right", if the global
directionality is LTR or RTL,
respectively.</td>
</tr>
<tr>
<td><code>bidiEndEdge()</code>


</td>
<td>Generates the string "right" or the
string "left", if the global
directionality is LTR or RTL,
respectively.</td>
</tr>
<tr>
<td><code>bidiTextDir(text, opt_isHtml)</code>

</td>
<td>Checks the provided text for its overall
(i.e. dominant) directionality. Returns
1 for LTR, -1 for RTL, or 0 for neutral
(neither LTR nor RTL). Set the optional
second parameter to <code>true</code> if <code>text</code>
contains or can contain HTML tags or
HTML "escapes" (default <code>false</code>).</td>
</tr>
</tbody>
</table>




<table>
<thead>
<tr>
<th>Function</th>
<th>Usage</th>
</tr>
</thead>

<tbody>
<tr>
<td><code>│bidiSpanWrap</code>








</td>
<td>If the overall directionality of the <code>print</code> command is
different from the global directionality, then the
compiler wraps the <code>print</code> command output in a span
with <code>dir=ltr</code> or <code>dir=rtl</code>.<br><br>The template
compiler applies autoescaping before evaluating
<code>│bidiSpanWrap</code>, which is safe because <code>│bidiSpanWrap</code>
correctly handles HTML-escaped text. If you're manually
escaping the output using <code>│escapeHtml</code>, be sure to put
<code>│escapeHtml</code> before <code>│bidiSpanWrap</code>, or else you'll
end up escaping any span tags that are generated.)</td>
</tr>
<tr>
<td><code>│bidiUnicodeWrap</code>






</td>
<td>If the overall directionality the <code>print</code> command is
different from the global directionality, then the
compiler wraps the <code>print</code> command output with Unicode
bidi formatting characters LRE or RLE at the start and
PDF at the end.<br><br>This directive serves the same
purpose as <code>│bidiSpanWrap</code>, but you should only use it
in situations where HTML markup is not applicable, for
example inside an HTML <code>&lt;option&gt;</code> element.</td>
</tr>
</tbody>
</table>



## Message Plugins {#message_plugins}

To write a plugin for a custom message type, use the package `xliffmsgplugin` as
an example by writing a class that implements the `SoyMsgPlugin` interface.

To use your message plugin with the executable jars, pass the plugin's Guice
module class to the command-line flag `--messagePlugin`.
