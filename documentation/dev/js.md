# Rendering a Template in JS

[TOC]

## How do I render a Soy template using JavaScript?

### Generating JavaScript functions from Soy files

The first step is to use the Soy compiler to compile a `.soy` file to a
corresponding JavaScript file.

See [Compiling Templates](dir.md) for more details.

Your `foo.soy` file generates a `foo.soy.js` file. Each template in `foo.soy`
generates a JavaScript function of the same name in `foo.soy.js`, which you can
call from handwritten JS to render the template. For example, the following
template:

```soy
{namespace soy.examples}

/**
 * Says hello to the world.
 */
{template .helloWorld}
  Hello world!
{/template}
```

will be compiled to something like

```js
goog.provide('soy.examples.helloWorld');

/**
 * @param {Object<string, *>=} opt_data
 * @param {Object<string, *>=} opt_ijData
 * @return {!goog.soy.data.SanitizedHtml}
 */
soy.examples.helloWorld = function(opt_data, opt_ijData) {
  // implementation details here
};
```

The exact signature of the generated JavaScript function is an implementation
detail.


The generated JavaScript function takes optional params representing the
different kinds of data that a Soy template can use. It returns a
[`SanitizedContent`][sanitized-content] object representing the rendered result.

The exact return type of the generated JavaScript function depends on the
template's [content kind](security#content_kinds). Most templates have a content
kind of `html`, so most generated JavaScript functions return a
[`SanitizedHtml`][sanitized-html] object, a subclass of `SanitizedContent`. The
other `SanitizedContent` objects correspond to the other content kinds:
`SanitizedCss` for `kind="css"`, etc. Templates of `kind="text"` return a raw
JavaScript `string`.

### Calling JavaScript functions from user code

Rendering templates from user code is simply a matter of calling the generated
JavaScript function. For example:

```js
goog.require('soy.examples.helloWorld');

const output = soy.examples.helloWorld();
```

Here, `output` is a `SanitizedContent` object containing the string `'Hello
world!'`. Note that because the `.helloWorld` template did not declare any
params, it is legal to call the generated JavaScript function without any
arguments. The next section discusses how to pass template data.

### Passing template data {#template-data}

To render a template with data, you can construct a one-off JavaScript object to
pass into a generated template function, or you can reuse an object that already
exists in your code (in which case it can contain keys that are not parameters
to the templates that you're calling). For example:

```soy
{namespace soy.examples}

/**
 * Says hello to a person.
 */
{template .helloName}
  {@param name: string}
  Hello {$name}!
{/template}
```

Example usage:

```js
const output1 = soy.examples.helloName({name: 'Alice'});
const data = {
  name: 'Alice',
  id: 1,
};
const output2 = soy.examples.helloName(data);
```

Here, both `output1` and `output2` would be SanitizedContent objects with the
content `'Hello Alice!'`. Note that `data` contains an additional key-value
pair. This does not affect the output of the rendered template, as long as the
required template data is present.

The JavaScript implementations of the template data types are:

Template Type             | JavaScript Type
------------------------- | -----------------
`null`                    | `null`
`bool`                    | `boolean`
`int`                     | `number`
`float`                   | `number`
`string`                  | `string`
`list<T>`                 | `Array<T>`
`map<K, V>`               | `Map`, `jspb.Map`
`legacy_object_map<K, V>` | `Object`

**Warning:** [Maps](../reference/types#map) and
[legacy object maps](../reference/types#legacy_object_map) are distinct types in
Soy's type system, and generate different JS code. Maps must be rendered with
JavaScript `Map` or `jspb.Map` instances; legacy object maps must be rendered
with plain JavaScript `Object` instances. Rendering a template with a `map`
parameter using a plain JavaScript `Object`, or vice versa, will cause a runtime
error. This means that when you change a template parameter from
`legacy_object_map` to `map`, you must also change the JavaScript value used to
render it from a plain `Object` to a `Map`.

**Tip:** If you are using Closure Compiler, there is an `@typedef` generated for
the parameters of a template that may be useful. If the template is called
`foo.bar` the parameters will be `foo.bar.Params`. This may be useful for
annotating locals or specifying method parameter types.

<!-- References -->

[sanitized-content]: https://github.com/google/closure-library/blob/master/closure/goog/soy/data.js
[sanitized-html]: https://github.com/google/closure-library/blob/master/closure/goog/soy/data.js

