# Auto-escaping

[TOC]

## What is auto-escaping?

Because HTML allows programming instructions to be embedded in text, it is never
safe to display arbitrary textual content on a web page. This vulnerability is
known as
[cross-site scripting](http://en.wikipedia.org/wiki/Cross-site_scripting) (XSS),
and Soy has several features to mitigate XSS, including auto escaping.

NOTE: There are many web applications suffering from XSS vulnerabilities: XSS
was number three in [OWASP's top ten application security
risks](http://www.owasp.org/index.php/Category:OWASP_Top_Ten_Project).

XSS vulnerabilities typically occur when dynamic text from an untrusted source
is embedded into an HTML document. To prevent these vulnerabilities, _escaping_
is used: any code that doesn't fit the context, and is therefore potentially
malicious, is removed. "Context" is determined by a template's "content kind"
value.

## How does auto-escaping work?

Every template has a content kind that describes the HTML context in which its
output is meant to appear: JS, CSS, a URL, etc. The default content kind is
HTML, which is appropriate for most templates.

When Soy renders a template, it knows the HTML context of the point in the
document where the template will appear, and automatically escapes the
template's output so that it is sanitized—safe and correct for that context.

For example, in an HTML context, Soy would escape angle brackets, replacing them
with `&lt;` and `&gt;` so they are not interpreted as tags.

## What does auto-escaping look like?

If your template produces a kind of content other than HTML, you have to
manually define its `kind` attribute. For example, a strict template that
produces a URI might look like this:

```soy
{template .googleUri kind="uri"}
  http://www.google.com/
{/template}
```

Strict auto-escaping is on by default for all templates. "Strict" auto-escaping
means templates are recursively guaranteed not to under-escape the output.

### What content kinds are there?

Soy supports the following basic content kinds:

*   `html` - HTML markup

    ```html
    <div>Hello!</div>
    ```

*   `attributes` - HTML attribute-value pairs. Represents the combination of
    both attribute names and attribute values, and must include the quotation
    marks around the attribute value. If the template output is intended to be
    just an attribute value alone (the part inside the quotes) then use either
    the text or html content kind.

    ```
    class="foo" width="100%"
    ```

*   `text` - Plain text, not yet escaped

    ```
    Hello!
    ```

*   `uri` - URIs

    ```
    http://www.google.com/search?q=android
    ```

*   `css` - Stylesheet text

    ```css
    .myClass{ color: red; display: block; }
    ```

*   `js` - JavaScript or JSON

    ```js
    {"a": 1, "b": 2}
    ```

*   `trusted_resource_uri` - A URL which is under application control and from
    which script, CSS, and other resources that represent executable code can be
    fetched. Currently Soy requires trusted_resource_uri for script srcs. In the
    future, this may apply to other kinds of resources, such as stylesheets.

    ```
    https://www.google.com/test.js
    ```

NOTE: The intersection of SanitizedContent types, content kinds, safe strings,
auto-escaped strings, etc., not to mention different language implementations of
these concepts, is admittedly byzantine. For the most part however, all these
safe types interoperate. For a full list see our [reference
section](../reference/types.md).

<br>

--------------------------------------------------------------------------------

<section class="nextButton"><a href="type-system.md">CONTINUE >></a></section>

<br>
