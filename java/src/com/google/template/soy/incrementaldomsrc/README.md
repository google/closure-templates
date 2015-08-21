# Incremental DOM Source Generation

This package compiles a soy tree into a series of function calls that can be used to render HTML. 
This source generating backend is only useful for templates that are used to generate HTML output. 
If arbitrary text templating is required, other backends should be used instead.

## Background

The JS source backend provides very good performance when rendering HTML, but does not provide the 
runtime with any knowledge of the HTML structure of templates. This backend aims to translate 
templates into a series of function calls representing tags, attributes and text. The extra layer 
of function calls adds some overhead in terms of payload and runtime, but provides the client-side 
code with knowledge about the structure of the markup.

## Overview

A template is translated into several fundamental function calls:

* [`ie_open`](https://github.com/google/incremental-dom/blob/master/src/virtual_elements.js#L185) - 
  an open tag and its attributes
* [`ie_close`](https://github.com/google/incremental-dom/blob/master/src/virtual_elements.js#L262) -
  a close tag
* [`itext`](https://github.com/google/incremental-dom/blob/master/src/virtual_elements.js#L300) -
  text inside of a tag

Additionally, the following three function calls are generated when a tag has a Soy tag in the 
middle of the attribute declaration (e.g. `<div {if}name="value"{/if}></div>`).

* [`ie_open_start`](https://github.com/google/incremental-dom/blob/master/src/virtual_elements.js#L215)
  - the start of an open tag, including the tag's name
* [`ie_open_end`](https://github.com/google/incremental-dom/blob/master/src/virtual_elements.js#L247)
  - then end of an open tag
* [`iattr`](https://github.com/google/incremental-dom/blob/master/src/virtual_elements.js#L235)
  - an attribute name and its value

For example, the folowing template:

```soy
{template .someName}
  {@param someCondition : boolean}
  {@param class : text}

  <div class="${class}">
    Hello
    {if $someCondition}
        <strong>world</strong>
    {/if}
  </div>
{/template}
```

Might be translated to something like:

```javascript
function someName(params) {
  ie_open('div', null, null,
      'class', params.class);
    itext('Hello');
    if (params.someCondition) {
      ie_open('strong');
        itext('world');
      ie_close('strong');
    }
  ie_close('div');
}
```

A runtime library will be used to translate the function calls into rendered markup. How the 
transformation is accomplished is left to the library itself.

As the runtime has knowledge of where attribute values and text are being printed, the output 
generated does not escape any of the variables. Instead, the runtime can escape them or otherwise 
render them in a safe way.

## Transformation

Prior to generating the source file, several transformations are applied to the incoming Soy tree:

1. `HtmlTransformVisitor` - translate `RawTextNode`s in the tree into Html*Nodes.
2. `OpenTagCollapsingVisitor`* - transform `HtmlOpenTagStartNode`, ..., 
  `HtmlOpenTagEndNode` sequences into `HtmlOpenTagNode`s where appropriate.
3. `ElementCollapsingVisitor`* - transform `HtmlOpenTagNode`, `HtmlCloseTagNode` pairs 
  into `HtmlVoidTagNode`s where appropriate.

\* These are an optimizations for runtime speed and payload size, but are not strictly necessary.

## Code generation

The code generation itself heavily leverages the jssrc package to do much of the JavaScript code 
generation for templates, control structures and expressions. The few key differences are:

* Html*Nodes are visited and translated to the appropriate function calls
* Statements are not concatenated, but rather simply output consecutively
* Let/Param nodes of kind="attributes" or kind="html" are wrapped in a function and invoked when 
  printed

While generating the instructions, the code generator indents the code based on the tag structure, 
indenting tags by 2 and attributes by 4. A JavaScript minifier can be used to remove any extra 
whitespace.
