# Security Reference


[TOC]

See the [Dev Guide Security page](../dev/security.md) for how to use Soy's
security features. This page describes in more detail how they work.

## Escaping: the fine details {#details}

### Substitutions in HTML {#in_html}

When a print command appears where normal HTML text could appear, then the
result is HTML entity-escaped. For example, in

```soy
<div title="{$shortMessage}">{$longMessage}</div>
```

given

```js
({ "shortMessage": "I <3 ponies", "longMessage": "OMG! <3 <3 <3!" })
```

produces

```html
<div title="I &lt;3 ponies!">OMG!  &lt;3 &lt;3 &lt;3!</div>
```

You can safely substitute data anywhere a tag can appear or in a plain attribute
value. It's good practice to quote all your attributes, but if you do forget
quotes, the autoescaper makes sure the attribute value cannot be split by spaces
in the dynamic value. Given the input above,

```soy
<div title={$shortMessage}>
```

becomes

```html
<div title=I&#32;&lt;3&#32;ponies!>
```

Spaces, which would normally end an unquoted attribute value, are encoded to
keep the value together.

To avoid over-escaping of *known safe* HTML, you can use sanitized content. The
template

```soy
<div>{$foo}</div>
```

given

```js
{ foo: soydata.SanitizedHtml.from(
    new goog.html.sanitizer.HtmlSanitizer().sanitize("<b>Foo</b>")) }
```

produces output that is not re-escaped:

```html
<div><b>Foo</b></div>
```

instead of the over-escaped version that would have been produced if the
`SanitizedHtml.from(new HtmlSanitizer().sanitize())` wrapper were not there:

```html
<div>&lt;b&gt;Foo&lt;/b&gt;</div>
```

Sanitized content is safe to use with attributes and with elements that cannot
contain tags such as `TEXTAREA`. The template

```soy
<div title="{$foo}">{$foo}</div>
```

given the input above produces a sensible output:

```html
<div title="Foo"><b>Foo</b></div>
```

When embedded in an HTML attribute, sanitized content will have tags stripped
first.

### Substitutions in Tag and Attribute Names {#in_tags_and_attrs}

Substitutions in tag and attribute names are sanity-checked rather than
entity-encoded.

```soy
<h{$headerLevel}>Foo</h{$headerLevel>
```

for `headerLevel=3` becomes

```html
<h3>Foo</h3>
```

but for

```
headerLevel='><script>alert(1337)<script'
```

you get

```html
<hzSoyz>Foo</hzSoyz>
```

You'll also get a log message in Java, and in JavaScript, if you're running with
[closure
asserts](https://github.com/google/closure-library/blob/master/closure/goog/asserts/asserts.js)
enabled, you get an assert.

Don't try to specify special tag names; like `script` or `style`; or special
attribute names; like `href`, `style`, or `onclick`; dynamically. Trying to use

```soy
<{$name}>{$content}</{$name}>
```

with

```js
({ "name": "script", "content": "alert(1337)" })
```

or

```soy
<a {$name}="{$content}">
```

with

```js
({ "name": "onmouseover", "content": "alert(1337)" })
```

is asking for trouble. Since the autoescaper cannot distinguish JavaScript, CSS,
or URLs from plain HTML with those tag and attribute names, it must reject them.

### Substitutions in URLs {#in_urls}

Values that are substituted into different parts of URIs are treated
differently. Substitutions in the query part are URI-escaped.

#### Basic substitutions

##### Entity-escape and filter out non-TrustedResourceUri {#trusted_resource_url}

For certain HTML attributes that can trigger code to be loaded or run we require
that dynamic content be a `TrustedResourceUri`. For example:

Original entity: `<script src="{$x}">`

Value                                 | Substitution
------------------------------------- | ------------------------------------
`({ "x": "foo") })`                   | `<script src="about:invalid#zSoyz">`
`({ "x": "/foo?a=b&c=d" })`           | `<script src="about:invalid#zSoyz">`
`({ "x": "javascript:alert(1337)" })` | `<script src="about:invalid#zSoyz">`

This escaping logic applies to:

*   `script.src`
*   `iframe.src`
*   `base.href`
*   `link.href` unless the `link` also has a whitelisted `rel` attribute, one
    of:
    *   alternate
    *   amphtml
    *   apple-touch-icon
    *   apple-touch-icon-precomposed
    *   apple-touch-startup-image
    *   author
    *   bookmark
    *   canonical
    *   cite
    *   dns-prefetch
    *   help
    *   icon
    *   license
    *   next
    *   prefetch
    *   preload
    *   prerender
    *   prev
    *   search
    *   shortcut
    *   subresource
    *   tag

##### Just entity-escape

Original entity: `<a href="/foo/{$x}">`

Value                      | Substitution
-------------------------- | ---------------------------------
`({ "x": "bar" })`         | `<a href="/foo/bar">`
`({ "x": "bar&baz/boo" })` | `<a href="/foo/bar&amp;baz/boo">`

##### Percent encode inside query

Original entity: `<a href="/foo?q={$x}">`

Value                                                              | Substitution
------------------------------------------------------------------ | ------------
`({ "x": "bar&baz=boo" })`                                         | `<a href="/foo?q=bar%26baz%3dboo">`
`({ "x": soydata.VERY_UNSAFE.ordainSanitizedUri("bar&baz=boo") })` | `<a href="/foo?q=bar&amp;baz=boo">`
`({ "x": "A is #1" })`                                             | `<a href="/foo?q=A%20is%20%231">`

As long as you stick to [standard HTML attribute
names](http://www.w3.org/TR/html4/index/attributes.html), the autoescaper
figures out which attributes contain URLs, which contain CSS, etc. If you do
decide to define custom attributes such as `data-…` attributes, you can still
use a naming convention to tell the autoescaper which attributes have URL
content: Names that start or end with "URL" or "URI", ignoring case, will be
treated as having URL values. For example, the autoescaper treats
`data-secondaryUrl`, `foo:urlForLogin`, and `data-thesauri` as having URL
content; but not `data-curliewurly`. Precisely, `/\bur[il]|ur[il]s?$/i` is the
set of custom attribute names with URL values.

#### Substitutions in Trusted Resource URLs {#in_trusted_resource_urls}

Values that are substituted in Trusted Resource URIs are almost same as in URIs
except that the value needs to be TrustedResourceUrl.

##### Entity-escape and filter out non-TrustedResourceUri

Original entity: `<script src="{$x}">`

Value                                                                                                    | Substitution
-------------------------------------------------------------------------------------------------------- | ------------
`({ "x": "foo") })`                                                                                      | `<script src="about:invalid#zSoyz">`
`({ "x": goog.html.TrustedResourceUrl.fromConstant(goog.string.Const.from( "https://foo.com/") })`       | `<script src="https://foo.com/">`
`({ "x": goog.html.TrustedResourceUrl.fromConstant(goog.string.Const.from( "/foo?a=b&c=d") })`           | `<script src="/foo?a=b&amp;c=d">`
`({ "x": goog.html.TrustedResourceUrl.fromConstant(goog.string.Const.from( "javascript:alert(1337)") })` | `<script src="javascript:alert(1337)">`

##### Entity-escape and filter out non-TrustedResourceUri

Original entity: `<script src="/foo/{$x}">`


| Value                                | Substitution                          |
| ------------------------------------ | ------------------------------------- |
| `({ "x":                             | `<script src="/foo/bar">`             |
: goog.html.TrustedResourceUrl.        :                                       :
: fromConstant(goog.string.Const.from( :                                       :
: "bar") })`                           :                                       :
| `({ "x":                             | `<script src="/foo/bar&amp;baz/boo">` |
: goog.html.TrustedResourceUrl.        :                                       :
: fromConstant(goog.string.Const.from( :                                       :
: "bar&baz/boo") })`                   :                                       :


<table>
<thead>
<tr>
<th>Value</th>
<th>Substitution</th>
</tr>
</thead>

<tbody>
<tr>
<td><code>({ "x": "foo") })</code></td>
<td><code>&lt;script src="about:invalid#zSoyz"&gt;</code></td>
</tr>
<tr>
<td><code>({ "x": "/foo?a=b&amp;c=d" })</code></td>
<td><code>&lt;script src="about:invalid#zSoyz"&gt;</code></td>
</tr>
<tr>
<td><code>({ "x": "javascript:alert(1337)" })</code></td>
<td><code>&lt;&lt;script src="about:invalid#zSoyz"&gt;</code></td>
</tr>
</tbody>
</table>


##### Entity-escape and filter out non-TrustedResourceUri

Original entity: `<script src="/foo?q={$x}">`


| Value                                | Substitution                    |
| ------------------------------------ | ------------------------------- |
| `({ "x":                             | `<script                        |
: goog.html.TrustedResourceUrl.        : src="/foo?q=bar&amp;baz=boo">`  :
: fromConstant(goog.string.Const.from( :                                 :
: "bar&baz=boo") })`                   :                                 :
| `({ "x":                             | `<script src="/foo?q=A is #1">` |
: goog.html.TrustedResourceUrl.        :                                 :
: fromConstant(goog.string.Const.from( :                                 :
: "A is #1") })`                       :                                 :


<table>
<thead>
<tr>
<th>Value</th>
<th>Substitution</th>
</tr>
</thead>

<tbody>
<tr>
<td><code>({ "x":
goog.html.TrustedResourceUrl.
fromConstant(goog.string.Const.from(
"bar&amp;baz=boo") })</code></td>
<td><code>&lt;script
src="/foo?q=bar&amp;amp;baz=boo"&gt;</code>

</td>
</tr>
<tr>
<td><code>({ "x":
goog.html.TrustedResourceUrl.
fromConstant(goog.string.Const.from(
"A is #1") })</code></td>
<td><code>&lt;script src="/foo?q=A is #1"&gt;</code>


</td>
</tr>
</tbody>
</table>


### Substitutions in JavaScript {#in_js}

Values in JavaScript that are inside quotes are dealt with differently from
those outside quotes.

#### Basic substitutions

##### Escaped inside quotes

Original entity: `<script>alert('{$x}');</script>`


| Value                         | Substitution                                 |
| ----------------------------- | -------------------------------------------- |
| `({ "x": "O'Reilly Books" })` | `<script>alert('O\'Reilly Books');</script>` |
| `({ "x": new                  | `<script>alert('O\'Reilly Books');</script>` |
: goog.soy.data.SanitizedJs(    :                                              :
: "O\\'Reilly Books") })`       :                                              :


<table>
<thead>
<tr>
<th>Value</th>
<th>Substitution</th>
</tr>
</thead>

<tbody>
<tr>
<td><code>({ "x": "O'Reilly Books" })</code></td>
<td><code>&lt;script&gt;alert('O\'Reilly Books');&lt;/script&gt;</code></td>
</tr>
<tr>
<td><code>({ "x": new
goog.soy.data.SanitizedJs(
"O\\'Reilly Books") })</code></td>
<td><code>&lt;script&gt;alert('O\'Reilly Books');&lt;/script&gt;</code>

</td>
</tr>
</tbody>
</table>


##### Without quotes, treated as a value

Original entity: `<script>alert({$x});</script>`

Value                         | Substitution
----------------------------- | --------------------------------------------
`({ "x": "O'Reilly Books" })` | `<script>alert('O\'Reilly Books');</script>`
`({ "x": 42 })`               | `<script>alert(42);</script>`
`({ "x": true })`             | `<script>alert(true);</script>`

### Substitutions in CSS {#in_css}

Values in CSS can be parts of classes, IDs, quantities, colors, or URLs.

#### Basic substitutions

##### Classes and IDs

Original entity: `<style>div#{$id} {lb} {rb}</style>`

Value                   | Substitution
----------------------- | --------------------------------
`({ "id": "foo-bar" })` | `<style>div#foo-bar { }</style>`

##### Quantities

Original entity: `<div style="color: {$x}">`

Value                                    | Substitution
---------------------------------------- | ----------------------------
`({ "x": "red" })`                       | `<div style="color: red">`
`({ "x": "#f00" })`                      | `<div style="color: #foo">`
`({ "x": "expression('alert(1337)')" })` | `<div style="color: zSoyz">`

##### Property Names

Original entity: `<div style="margin-{$ltr-dir}: 1em">`

Value                      | Substitution
-------------------------- | ---------------------------------
`({ "ltr-dir": "left" })`  | `<div style="margin-left: 1em">`
`({ "ltr-dir": "right" })` | `<div style="margin-right: 1em">`

##### Quoted Values

Original entity: `<style>p {lb} font-family: '{$x}' {rb}</style>`

Value                   | Substitution
----------------------- | ------------------------------------------------------
`({ "x": "Arial" })`    | `<style>p { font-family: 'Arial' }</style>`
`({ "x": "</style>" })` | `<style>p { font-family: '\3c \2f style\3e '}</style>`

##### URLs in CSS are handled as in attributes above

Original entity: `<div style="background: url({$x})">`


| Value                             | Substitution                             |
| --------------------------------- | ---------------------------------------- |
| `({ "x": "/foo/bar" })`           | `<div style="background:                 |
:                                   : url(/foo/bar)">`                         :
| `({ "x": "javascript:alert(1337)" | `<div style="background: url(#zSoyz)">`  |
: })`                               :                                          :
| `({ "x": "?q=(O'Reilly) OR Books" | `<div style="background:                 |
: })`                               : url(?q=%28O%27Reilly%29%20OR%20Books)">` :


<table>
<thead>
<tr>
<th>Value</th>
<th>Substitution</th>
</tr>
</thead>

<tbody>
<tr>
<td><code>({ "x": "/foo/bar" })</code>
</td>
<td><code>&lt;div style="background:
url(/foo/bar)"&gt;</code></td>
</tr>
<tr>
<td><code>({ "x": "javascript:alert(1337)"
})</code></td>
<td><code>&lt;div style="background: url(#zSoyz)"&gt;</code>
</td>
</tr>
<tr>
<td><code>({ "x": "?q=(O'Reilly) OR Books"
})</code></td>
<td><code>&lt;div style="background:
url(?q=%28O%27Reilly%29%20OR%20Books)"&gt;</code></td>
</tr>
</tbody>
</table>


## Print Directives {#print_directives}

Autoescaping works by automatically adding [print directives](print-directives)
to templates, so you can remove the print directives that you explicitly added,
including `|escapeUri`.

In case you have defined custom [print directives](../dev/plugins) and your
custom directive expects already-escaped input instead of plain text, you can
implement `SanitizedContentOperator` to get the autoescaper to insert escaping
directives *before* your directive so they produce the already-escaped input and
pipe it to your directive.

## Guarantees

Autoescaping augments Soy to choose an appropriate encoding for each dynamic
value so that even if a particular dynamic value can be controlled by an
attacker, certain safety properties hold.

Specifically the following properties hold:

#### Structure is preserved

If you, the Soy author, write `<b>{$x}</b>`, then the tags `<b>` and `</b>`
always correspond to matched tags in the template output regardless of the value
of `$x`.

No dynamic value can change the meaning of an HTML, CSS, or JavaScript token in
the template, or correspondences between pairs of matched tokens.

#### Only code in the template is executed

Dynamic values cannot specify unsafe code. Any code hidden in dynamic values
(whether via `<script>` elements, `javascript:` URIs, or some other mechanism)
are treated as plain text and encoded properly on output instead of being
rendered as code.

Dynamic values that appear in JavaScript, like `$message` in

```soy
<script>alert('{$message}')</script>
```

are encoded to expressions without side effects or free variables (to preserve
privacy constraints). Given

```js
{ "message": "'//\ndoEvil()//" }
```

the template produces

```html
<script>alert('\x27//\ndoEvil()//');</script>
```

which alerts the garbage string passed in instead of calling `doEvil`.

#### All code in the template is executed

A dynamic value cannot cause code to fail to parse. Some applications have
security-critical code that they need to run if JavaScript is enabled. Take for
example the following template:

```js
<script>
  var s = '{$s}';
  doSecurityCriticalStuff();
</script>
```

If the value of the variable `s` is a newline character "\\n", then a
non-autoescaped template would produce the following output:

```js
<script>
  var s = '
';
  doSecurityCriticalStuff();
</script>
```

The autoescaped version of the template instead produces:

```js
<script>
  var s = '\n';
  doSecurityCriticalStuff();
</script>
```

which parses properly.
