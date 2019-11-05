# Closure Templates
Closure Templates are a client- and server-side templating system that helps you
dynamically build reusable HTML and UI elements. They have a simple syntax
that is natural for programmers, and you can customize them to fit your
application's needs.  In contrast to traditional templating systems,in which
you must create one monolithic template per page, you can think of
Closure Templates as small components that you compose to form your user
interface. You can also use the built-in message support to easily localize
your applications.

Closure Templates are implemented for both JavaScript and Java, so that you can
use the same templates on both the server and client side. They use a data model
and expression syntax that work for either language. For the client side,
Closure Templates are precompiled into efficient JavaScript.

## What are the benefits of using Closure Templates?
* **Convenience**. Closure Templates provide an easy way to build pieces of HTML
  for your application's UI and help you separate application logic from
   display.
* **Language-neutral**. Closure Templates work with JavaScript or Java. You can
  write one template and share it between your client- and server-side code.
* **Client-side speed**. Closure Templates are compiled to efficient JavaScript
  functions for maximum client-side performance.
* **Easy to read**. You can clearly see the structure of the output HTML from
  the structure of the template code. Messages for translation are inline for
  extra readability.
* **Designed for programmers**. Templates are simply functions that can call
  each other. The syntax includes constructs familiar to programmers.
  You can put multiple templates in one source file.
* **A tool, not a framework**. Works well in any web application environment
  in conjunction with any libraries, frameworks, or other tools.
* **Battle-tested**. Closure Templates are used extensively in some of the
  largest web applications in the world, including Gmail and Google Docs.
* **Secure**. Closure Templates are contextually autoescaped to reduce the risk
  of XSS.

## Getting Started

*   Download the latest release on
    [GitHub](https://github.com/google/closure-templates/releases) or
    [Maven Central](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.google.template%22%20AND%20a%3A%22soy%22)
*   Read the
    [Documentation](https://github.com/google/closure-templates/tree/master/documentation).

## Support and Project status

Closure Templates is widely used and well maintained internally at Google but
does not currently have staffing to support the open source release.  As such
this project is mostly a 'code dump' and support is _minimal_.  For certain
issues, like build integration we are in an especially bad position to offer
support.

To get assistance you can use any of the following forums

1. Look through the [documentation](https://github.com/google/closure-templates/tree/master/documentation).
2. Post a question to the [closure-templates-discuss](https://groups.google.com/forum/#!forum/closure-templates-discuss)
   mailing list.
3. File a [bug on github](https://github.com/google/closure-templates/issues)

Though, given our support staffing, we may not be able to help.

## Using Closure Templates with other open source frameworks

There are many Closure Template integrations with other popular open source
frameworks. Here are a few options for getting started:

* Node.js
  * https://github.com/Medium/soynode
* Gulp
  * https://www.npmjs.com/package/gulp-soynode
  * https://www.npmjs.com/package/gulp-soy
* Grunt
  * https://www.npmjs.com/package/grunt-closure-soy
  * https://www.npmjs.com/package/grunt-soy
* NPM
  * https://www.npmjs.com/package/google-closure-templates
* Maven
  * http://mvnrepository.com/artifact/com.google.template/soy
* Yeoman
  * https://github.com/andrewpmckenzie/generator-closure-stack
* Bazel
  * https://github.com/bazelbuild/rules_closure/#closure_js_template_library
