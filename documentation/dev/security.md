# Security

Many web applications suffer from
[cross-site scripting](http://en.wikipedia.org/wiki/Cross-site_scripting) (XSS)
vulnerabilities. XSS was number 3 in
[OWASP's top 10 application security risks](http://www.owasp.org/index.php/Category:OWASP_Top_Ten_Project)
in 2013. Soy has features to automatically prevent XSS in your application.

This page describes how to use Soy's security features. See the
[Security Reference](../reference/security) page for more details about how the
security features work.

[TOC]

## Autoescaping in Soy {#autoescaping}

XSS vulnerabilities typically occur when dynamic text from an untrusted source
is embedded into an HTML document. To prevent these vulnerabilities, *escaping*
is used. Escaping is the process of converting text to be properly displayed in
its context, such as turning angle brackets into `&lt;` and `&gt;` so they are
not interpreted as tags.

Soy automatically determines and performs the type of escaping needed based on
the context in the document where the value appears. For example, a value that
appears inside a `<style>` tag needs to be escaped differently than a value that
appears in a URI.

Soy's *autoescaping* ensures that every dynamic value is escaped in a
context-appropriate way.

## Anatomy of an XSS Hack (and its prevention) {#example}

Template systems make it easy to compose content from static HTML and dynamic
values. Soy's autoescaping makes it even easier by letting you use the same
values in many contexts without having to explicitly specify encoding.

### The hack

An enterprising hacker might try to sneak a malicious value into your template
to take it over via XSS. Perhaps using

```js
var x = 'javascript:/*</style></script>/**//<script>1/(alert(1337))//</script>';
```

Say it's passed into a naive template, like:

```js
`
  <a href="{$x}"
   onclick="{$x}"
   >{$x}</a>
  <script>var x = '{$x}'</script>
  <style>
    p {
      font-family: "{$x}";
      background: url(/images?q={$x});
      left: {$x}
    }
  </style>
`;
```

This attack succeeds. The above template produces:

```html
  <a href="javascript:/*</style></script>/**/ /<script>1/(alert(1337))//</script>"
   onclick="javascript:/*</style></script>/**/ /<script>1/(alert(1337))//</script>"
   >javascript:/*</style></script>/**/ /<script>1/(alert(1337))//</script></a>
  <script>var x = 'javascript:/*</style></script>/**/ /<script>1/(alert(1337))//</script></script>
  <style>
    p {
      font-family: "javascript:/*</style></script>/**/ /<script>1/(alert(1337))//</script>";
      background: url(/images?q=javascript:/*</style></script>/**/ /<script>1/(alert(1337))//</script>);
      left: javascript:/*</style></script>/**/ /<script>1/(alert(1337))//</script>
    }
  </style>
```

The page now triggers 6 "1337" alerts, and a seventh if you click the link.

### The explanation

Let's take another look at that malicious input to figure out why the attack
succeeds:

*   `javascript:` - At the beginning of a URL, this changes the rest of the
    content into JavaScript. In a script statement, this is just an unused
    label.

*   `/*</style></script>/**/` - This breaks out of any `style` or `script`
    element. If already in a script attribute value, this just looks like a
    comment. It prematurely ends any unquoted attribute value and its containing
    tag.

*   `/<script>1/` - If outside a script, this starts a script tag with a useless
    division. Inside a script, this is a self-contained regular expression
    literal.

*   `(alert(1337))` - If preceded by a regular expression literal, this tries to
    call it, but only after executing the real malicious code, `alert(1337)`.

*   `//</script>` - If inside a script tag, this closes it correctly. If inside
    a `javascript:` URL attribute or event handler attribute, this is a harmless
    comment.

### The prevention

Many of the pieces of that malicious input depend on being interpreted different
ways by different parts of a browser. Autoescaping defangs this and other
malicious inputs by choosing a single consistent meaning for a dynamic value,
and choosing an escaping scheme that makes sure the browser will interpret it
the same way.

So if we pass that same malicious input to an autoescaped template:

```soy
{template .foo}
  <a href="{$x}"
   onclick="{$x}"
   >{$x}</a>
  <script>var x = '{$x}'</script>
  <style>
    p {
      font-family: "{$x}";
      background: url(/images?q={$x});
      left: {$x}
    }
  </style>
{/template}
```

We get a very different output; one that is altogether saner:

```html
  <a href="#zSoyz"
   onclick="'javascript:/*&lt;/style&gt;&lt;/script&gt;/**/ /&lt;script&gt;1/(alert(1337))//&lt;/script&gt;'"
   >javascript:/*&lt;/style&gt;&lt;/script&gt;/**/ /&lt;script&gt;1/(alert(1337))//&lt;/script&gt;</a>
  <script>var x = 'javascript:/*\x3c/style\x3e\x3c/script\x3e/**/ /\x3cscript\x3e1/(alert(1337))//\x3c/script\x3e'</script>
  <style>
    p {
      font-family: "javascript:/*\3c /style\3e \3c /script\3e /**/ /\3c script\3e 1/(alert(1337))//\3c /script\3e ";
      background: url(/images?q=javascript%3A%2F%2A%3E%2Fstyle%3C%3E%2Fscript%3C%2F%2A%2A%2F%20%2F%3Escript%3C1%2F%28alert%281337%29%29%2F%2F%3E%2Fscript%3C);
      left: zSoyz
    }
  </style>
```

*   When `{$x}` appeared inside HTML text, we entity-encoded it (< → \&lt;).
*   When `{$x}` appeared inside a URL or as a CSS quantity, we rejected it
    because it had a protocol `javascript:` that was not `http` or `https`, and
    instead output a safe value `#zSoyz`. Had `{$x}` appeared in the query
    portion of a URL, we would have percent-encoded it instead of rejecting it
    outright (< → %3C).
*   When `{$x}` appeared in JavaScript, we wrapped it in quotes (if not already
    inside quotes) and escaped HTML special characters (< → \x3c).
*   When `{$x}` appeared inside CSS quotes, we did something similar to
    JavaScript, but using CSS escaping conventions (< → \3c).

The malicious output was defanged.

## Strict Autoescaping {#strict}

The most secure way to use Soy is with strict autoescaping. Strict templates are
recursively guaranteed not to underescape the output. Every last dynamic value
is printed with the correct escaping technique.

The output of a strict template is not a plain string, but a `SanitizedContent`
object, which associates a *content kind* with the text. The content kind
represents how the content is intended to be used, and the type of escaping, if
any, that has already been applied to it. This information is particularly
important in cases where the output of one template is used as an input
parameter to another template.

For every dynamic value that appears in the output of a template, Closure
Templates identifies the *output context* at the point of use, determined by the
surrounding text.

These two factors (content kind and output context) determine what kind of
escaping is applied to the text. For example, if the text has already been
URI-escaped, and it's being used in a URI context, then there's no need to
escape it again. This prevents "double-escaping" of the text.

### Content Kinds {#content_kinds}

The different kinds of content are:

Content kind         | Description                                                                                                                             | Example                                   | Notes
-------------------- | --------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------- | -----
html                 | HTML markup                                                                                                                             | `<div>Hello!</div>`                       |
attributes           | HTML attribute-value pairs                                                                                                              | `class="foo" width="100%"`                | Represents the combination of both attribute names and attribute values, and must include the quotation marks around the attribute value. If the template output is intended to be just an attribute value alone (the part inside the quotes) then use either the text or html content kind.
text                 | Plain text, not yet escaped                                                                                                             | `Hello!`                                  |
uri                  | URIs                                                                                                                                    | `http://www.google.com/search?q=android`  |
css                  | Stylesheet text                                                                                                                         | `.myClass{ color: red; display: block; }` |
js                   | JavaScript or JSON                                                                                                                      | `{"a": 1, "b": 2}`                        |
trusted_resource_uri | A URL which is under application control and from which script, CSS, and other resources that represent executable code can be fetched. | `https://www.google.com/test.js`          | Currently Soy requires trusted_resource_uri for script srcs. In the future, this may apply to other kinds of resources, such as stylesheets.

The content kind isn't a compiler type; you won't get an error or warning if you
use a `text` kind in a `css` context or vice versa. Rather, the content kind is
an indication that the text is safe for a given context and therefore does not
need additional escaping.

For input values that are not `SanitizedContent` objects, a strict template
coerces the value to a `text` string, and then applies escaping based on the
context.

### Usage

Strict autoescaping is on by default for all templates.

By default, the output of a strict template has kind `html`. If your template
produces a different kind of content, you must add `kind` attributes to your
template. For example, a strict template that produces a URI might look like
this:

```soy
{template .googleUri kind="uri"}
  http://www.google.com/
{/template}
```

The `kind` attribute can be added to the following Soy commands:

| Command     | Notes                                                     |
| ----------- | --------------------------------------------------------- |
| template    | Optional. Assumed to be kind="html" if omitted.           |
| deltemplate | Optional. Assumed to be kind="html" if omitted. All       |
:             : matching delegates must have the same kind.               :
| let         | Required only for [block form let                         |
:             : statements](../reference/let).                            :
| param       | Required only for [block form param                       |
:             : statements](../reference/calls#construct-values-to-pass). :

The following example illustrates the usage of the `kind` attribute:

```soy
{template .foo kind="text"}
  // Block-form 'let' command, 'kind' is required.
  {let $message kind="text"}
    {msg}Hi, {$name}!{/msg}
  {/let}

  // Short form 'let', no 'kind' attribute.
  {let $category: $categoryList[0] /}

  {call .bar}
    // Block-form 'param' command, kind is required.
    {param attributes kind="attributes"}
      title="{$message}"{sp}
      onclick="foo('{$message}')"
    {/param}
    {param content kind="html"}
      <b>{$message}</b>
    {/param}

    // Short-form 'param' command, no 'kind' attribute.
    {param visible: true /}
  {/call}
{/template}
```

Short-form commands don't need the `kind` attribute because they pass values
rather than constructing strings, and values keep whatever kind they already
have.

### Passing parameters to strict templates {#passing_params}

For ordinary content that doesn't contain markup, you can just pass in the
string values as template parameters as before, and they will get escaped.


Soy treats `SafeHtml` and the other safe contract types (`SafeStyle`, `SafeUrl`,
etc.) as exempt from re-escaping and filtering.


## Content Security Policy (CSP) {#content_security_policy}

Soy supports [Content Security Policy](http://www.w3.org/TR/CSP/) nonces. CSP
nonces are a defense-in-depth technique for restricting the execution of
`<script>` and `<style>` blocks. With CSP nonces, even if an attacker can inject
scripts into your document, they will be unable to execute unless they can also
guess the CSP nonce. (See
[this article](https://blog.mozilla.org/security/2014/10/04/csp-for-the-web-we-have)
for a good overview.)

With CSP nonces in Soy, templates get `nonce="..."` added to the following tags:

*   `<script>`
*   `<style>`
*   `<link rel="import">`
*   `<link rel="preload" as="script">`
*   `<link rel="preload" as="style">`
*   `<link rel="stylesheet">`

For example:

```html
<script>...</script>
```

becomes

```soy
<script{if $csp_nonce} nonce="{$csp_nonce}"{/if}>...</script>
```

Stylesheets use their own nonce if it is set:

```html
<style>...</style>
```

becomes

```soy
<style{if $csp_style_nonce} nonce="{$csp_style_nonce}"{/if}>...</style>
```

### Configuring CSP nonces

To configure CSP nonces with Soy:

1.  Configure your web server to compute nonces and send them in CSP response
    headers.
2.  Make the nonces computed in step 1 available to your templates.

Step 1 is outside the scope of this document. General considerations for nonces
include generating strong random numbers
([article](https://www.securecoding.cert.org/confluence/display/java/MSC02-J.+Generate+strong+random+numbers))
and not reusing nonces
([article](https://www.securecoding.cert.org/confluence/display/java/MSC59-J.+Limit+the+lifetime+of+sensitive+data)).

For step 2, render with an [injected data](../concepts/ij-data) bundle that
includes an `$csp_nonce` value that is a
[valid nonce](https://www.w3.org/TR/CSP3/#grammardef-base64-value).
