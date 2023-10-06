# Soy's Evaluation Order and Side effects

go/soy-evaluation-order

<!--*
# Document freshness: For more information, see go/fresh-source.
freshness: { owner: 'lukes' reviewed: '2023-10-05' }
*-->

TL;DR: Soy has no defined evaluation semantics and templates should not rely on
Soy executing code in a particular order.

Soy provides strong guarantees about the results of rendering but provides no
particular guarantees about things that happen during rendering that don't
affect rendering.

## Example

For example, consider this following template

```soy
{template a}
  {template foo}
  {@param a: ?}
  {let $foo kind="html"}
    {log}Foo-Begin{/log}
    {$a}
    {log}Foo-End{/log}
  {/let}
  {let $bar kind="html"}
    {log}Bar-Begin{/log}
    {$a}
    {log}Bar-End{/log}
  {/let}
  {$bar}
  {$foo}
  {$bar}
{/template}
```

What does this template log? It turns out it is dependent upon what backend you
use and the value of `$a`!

*   SoyJS, Soy Python?

    -   In these backends evaluation is always eager.

    ```
    Foo-Begin
    Foo-End
    Bar-Begin
    Bar-End
    ```

*   SoyIdom?

    -   In this backend `html` blocks are modeled as functions backends
        evaluation is always eager.

    ```
    Bar-Begin
    Bar-End
    Foo-Begin
    Foo-End
    Bar-Begin
    Bar-End
    ```

    -   If the blocks were `kind="text"` SoyIdom would behave the same as SoyJs

*   Java (JBCSRC)?

    -   SoySauce uses some [advanced rendering](./adv-java) strategies to
        support asynchronous IO. As such evaluation is sometimes lazy and
        sometimes eager and will respond to the presence of unresolved `Future`
        objects passed as parameters.
    -   When using lazy evaluation?

    ```
    Bar-Begin
    Bar-End
    Foo-Begin
    Foo-End
    ```

    or - When using eager evaluation?

    ```
    Foo-Begin
    Foo-End
    Bar-Begin
    Bar-End
    ```

    or - When using eager evaluation, but `a` is an unresolved `Future`?

    ```
    Foo-Begin
    Bar-Begin
    Bar-End
    Foo-End
    ```

Note, the examples above are not definitive statements or guarantees, but rather
they demonstrate a known and expected divergence in behavior. As such the
specific orderings may change in the future.

## Rules

We explicitly do not define an evaluation order for Soy instead we simply define
how templates *render*. A consequence of this, is that if some code doesn't
affect rendering, it may not ever be evaluated. Furthermore, even if your code
is important for rendering, we don't believe the *order* of evaluation to be
important. Different backends have different optimization goals and to support
that we allow them to flexibly generate code that doesn't respect code ordering.

### What about externs and side effects?

If you write an extern that has side effects, you will have to make the
*rendering* process dependent on it.

Notably, code like `{let $unused: someSideEffect()/}` or `{if
someSideEffect}{/if}` is simply not guaranteed to execute at all. Instead you
need to ensure that the *result* of evaluating the function is consumed. The
simplest way is to just make the side effect extern return the empty string (or
the empty `html`) and render that, e.g. `{someSideEffect()}`. However, even in
this case the function should be *idempotent* since it still may be called
multiple times.

### What about relative ordering?

Consider

```soy
{before()}
{...render something...}
{after()}
```

where the `before` and `after` functions manipulate some sort of global state.
In this case, because they are ordered sequentially with `print` commands, they
will evaluate in the stated order. However, what isn't guaranteed is that
`...render something...` is the only thing that occurs between those two
statements. It is possible that other soy expressions will be evaluated between
those two statements as well.

This is demonstrated above in the 3rd example for `SoySauce` evaluation.
