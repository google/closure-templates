# Translation (Msg, Plurals and Gender)

[TOC]

## msg {#msg}

Syntax (basic form):

```soy
{msg desc="<help_text_for_translators>"}
  ...
{/msg}
```

With all optional attributes:

```soy
{msg desc="<help_text_for_translators>" meaning="<differentiator>"}
  ...
{/msg}
```

Use the `msg` tag to mark a section of the template as a message that needs to
be translated. The required `desc` attribute provides explanation text to the
(human) translators.

<p class="note">NOTE: Do not break up a sentence or phrase into multiple messages.
Translation is most effective with the context of the entire sentence or phrase.</p>

Within a `msg` block, the compiler turns HTML tags and Soy tags into
placeholders, with the exception of template tags that generate raw text (e.g.
special character commands and `literal` blocks), which are simply substituted.
Messages that contain placeholders might be confusing to translators, so you
should consider providing (in the `desc` attribute) an example of how the
message looks to the user.

Here are details on how Soy generates placeholders in messages:

-   The compiler uses fixed names for common HTML tags, e.g. `<a ...>` becomes
    `{START_LINK}` and `<br />` becomes `{BREAK}`.

-   A simple `print` tag that prints a data reference turns into a placeholder
    that's named according to the last key name, by default. For example,
    `{$user.emailAddress}` turns into the placeholder `{EMAIL_ADDRESS}`.

-   A function call or more complex expressions turns into a single placeholder,
    named `{XXX}` by default. To aid translation, be sure to provide a clear
    example of what the message might look like in the `desc` attribute and
    consider setting a `phname` or `phex`.

    Examples of these transforms can be seen below:

    *   `$fooBar` -> FOO_BAR
    *   `$aaa_bbb` -> AAA_BBB
    *   `$foo.bar` -> BAR
    *   `$arr[0]` -> XXX
    *   `$arr[0].bar` -> BAR
    *   `$num + 1` -> XXX
    *   `length($aaa)` -> XXX
    *   `$foo.getBar()` -> XXX

-   A complete `call` block (up to the closing `/call`) turns into a single
    placeholder. The placeholder is named `{XXX}` by default, so if you put a
    `call` inside a `msg`, be sure to provide a clear example of what the
    message might look like in the `desc` attribute and consider setting a
    `phname`.

-   If you find a need to specify a placeholder name for a `print`, `call`,
    `plural`, `select` or HTML tag instead of allowing the compiler to generate
    the default name, you can add the `phname` attribute. The value follows
    standard variable naming conventions (must start with alphabet character,
    and contain only alpha-numerics and underscore). By convention, it is
    recommended that all names be UPPER_SNAKE_CASE. For historic reasons, the
    generated placeholder name for `print`, `call`, and tag is converted to
    UPPER_SNAKE_CASE, but not `plural` or `select`. For example, `{call
    .emailAddr phname="emailAddress" /}` causes the placeholder name to be
    `EMAIL_ADDRESS` instead of `XXX`. Note that changing a placeholder name
    changes the message id, causing retranslation.

-   You can set placeholder examples to aid translation. For example, `{$url
    phex="www.google.com"}` or `{$name phex="Alice"}`. Placeholder examples are
    configured via the `phex` attribute and can be specified everywhere that
    `phname` attributes can. `phex` attributes act like the `desc` attribute on
    messages in that they do not change identifiers.

-   If a message contains multiple placeholders that would result in the same
    placeholder name, then the compiler appends number suffixes to distinguish
    them, e.g. `START_LINK_1`, `START_LINK_2`.

-   Template commands for control flow (`if`, `switch`, `for`) are not allowed
    in `msg` blocks. However, you can put them in a `let` block and then print
    the resulting reference from a within a `msg` block. If you do this, make
    sure the `let` block has absolutely no translatable content.

Example message:

```soy
{msg desc="Says hello and tells user to click a link."}
  Hello {$userName}! Please click <a href="{$url}">here</a>.
{/msg}
```

Example of what the translators see:

```
Hello {USER_NAME}! Please click {START_LINK}here{END_LINK}.
```

Example translation that one translator might give us:

```
{START_LINK}Zhere{END_LINK} zclick zplease. {USER_NAME} zhello!
```

Example output (for `userName = 'Jess'` and `url = 'http://www.google.com/'`):

```
<a href="http://www.google.com/">Zhere</a> zclick zplease. Jess zhello!
```

### Meaning

Use the `meaning` attribute when you have two messages that are exactly the same
string in English, but might be translated to different strings in other
languages. The `meaning` should then be a **short** string that distinguishes
the two messages, and is used for generating different message ids. Translators
do **not** see the `meaning` attribute, so you must still communicate all the
details in the `desc`. For example:

```soy
{msg desc="The word 'Archive' used as a noun, i.e. an information store." meaning="noun"}
  Archive
{/msg}
{msg desc="The word 'Archive' used as a verb, i.e. to store information." meaning="verb"}
  Archive
{/msg}
```

See more information about translating messages in the
[Translation](../dev/localization.md) chapter.

### Genders

There are three methods for varying messages based on gender in Soy:

1.  **Gender Appropriate UI (GAUI):** For messages that vary based on the
    *viewer's* grammatical gender in languages other than English.
2.  **The `genders` attribute:** For messages that vary based on the gender of
    one or more individuals (viewer or third party) in languages other than
    English.
3.  **The `select` keyword:** For messages that require different phrasing based
    on gender *in English*.

#### 1. Gender Appropriate UI (GAUI)

Use GAUI when a message's translation depends on the viewer's grammatical
gender, but the English source text remains the same. GAUI is a system designed
to address users in a manner grammatically appropriate to their self-selected
gender.

*   **Key Characteristics:**
    *   Does not affect English messages.
    *   Depends only on the viewer's globally configured grammatical gender.
    *   Translators provide gendered variants as needed for each language.

With GAUI, no special syntax is needed within the individual Soy `{msg}` command
to specify the viewer's gender, as this is handled at the infrastructure level.
Translators can provide different translations based on the viewer's gender
settings.

*Configuration of the user's grammatical gender in the server is
application-specific.*

NOTE: GAUI cannot be used to create gender variants for messages in English.

#### 2. The `genders` Attribute

Use the `genders` attribute when a message in a non-English language needs to
vary based on the grammatical gender of someone who may or may not be the viewer
(e.g., a third party). The English source text remains the same.

*   **Key Characteristics:**
    *   Does not affect English messages.
    *   Can depend on the gender of the viewer and/or other individuals.

The syntax in Soy is `{msg desc="..." genders="..."}`. The `genders` attribute
accepts one or more comma-separated expressions.

**Example:**

```soy
{msg desc="Invitation for the user to join a community hosted by a target person." genders="$userGender, $targetGender"}
  Join {$targetName}'s community.
{/msg}
```

In this example, some languages might inflect the verb "Join" based on
`$userGender`, and other parts of the sentence based on the grammatical gender
of `$targetName`.

The `genders` attribute provides metadata for translators. Each expression in
the `genders` list should evaluate to a string. The recognized values are:

*   `'female'`
*   `'male'`
*   Any other string (treated as `'unknown/other'` gender).

**Best Practice:** Use clear data references for gender expressions, such as
`$userInfo.userGender` or `$targetGender`, rather than complex expressions. The
last part of the data reference (e.g., `userGender`, `targetGender`) is visible
to translators, helping them understand which gender applies to which part of
the message.

#### 3. The `select` Operation {#gender-vary}

Use the `select` operation when a message needs different phrasing *in English*
based on gender.

*   **Key Characteristic:**
    *   Enables different English text based on gender variables.

**Example:**

```soy
{msg desc="Notification about a document being shared."}
  {select $ownerGender}
    {case 'female'}
      {select $targetGender}
        {case 'female'}She shared a document with her.
        {case 'male'}She shared a document with him.
        {default}She shared a document with them.
      {/select}
    {case 'male'}
      {select $targetGender}
        {case 'female'}He shared a document with her.
        {case 'male'}He shared a document with him.
        {default}He shared a document with them.
      {/select}
    {default}
      {select $targetGender}
        {case 'female'}They shared a document with her.
        {case 'male'}They shared a document with him.
        {default}They shared a document with them.
      {/select}
  {/select}
{/msg}
```

### Combining `select` and `genders`

If a message varies in English for only *some* genders, use `select` for the
genders causing the English variation. For any other genders that *don't* affect
the English text but might be needed for translation in other languages, include
them in the `genders` attribute.

**Example:**

In the message below, the English text varies based on `$targetGender` but not
`$userGender`. Thus, `$targetGender` uses a `select`, while `$userGender` is
passed to translators via the `genders` attribute.

```soy
{msg desc="Call to action to reply to the target person." genders="$userGender"}
  {select $targetGender}
    {case 'female'}Reply to her.
    {case 'male'}Reply to him.
    {default}Reply to them.
  {/select}
{/msg}
```

Each `select` block becomes a placeholder in the message for translation. The
placeholder name is taken from the `phname` attribute if provided, otherwise it
defaults to the UPPER_SNAKE_CASE version of the variable name (e.g.,
`TARGET_GENDER`). By convention, all placeholder names should be
UPPER_SNAKE_CASE.

### Plural {#plural}

Message pluralization syntax:

```soy
{msg desc="..."}
  {plural $numDrafts}
    {case 0}No drafts
    {case 1}1 draft
    {default}{$numDrafts} drafts
  {/plural}
{/msg}
```

Each `plural` block appears as a placeholder in the containing message. The
placeholder name is `phname` (if present) or the UPPER_SNAKE_CASE version of the
variable name (`NUM_DRAFTS` in this example). By convention, it is recommended
that all names be UPPER_SNAKE_CASE.

If a message is the same in all cases in English, you should still declare it as
a plural, so that translator can add cases according to their languages. It is
mandatory to specify cases at least for `1`, and `default`. Translators will add
one or more of the available cases (`0`, `1`, `2`, `few`, `many`, `other`), if
they apply in their target language.

WARNING: You cannot nest `plural` tags.

NOTE: If you need to use `plural` and `select` in the same message (i.e. the
message contains a number and varies in English due to a gender value), then you
must put `plural` within `select`, not the other way around.

#### Offset and remainder {#offset-and-remainder}

There is a rarely used `offset` attribute that works in conjunction with a
special `remainder` function:

```soy
{let $firstAttendeeGender: $attendees[0]?.gender /}
{let $secondAttendeeGender: $attendees[1]?.gender /}
{msg desc="Says how many people are attending, listing up to 2 names."
     genders="$firstAttendeeGender, $secondAttendeeGender"}
  {plural $attendees.length offset="2"}
    // Note: length() is never 0.
    {case 1}{$attendees[0].name} is attending
    {case 2}{$attendees[0].name} and {$attendees[1]?.name} are attending
    {case 3}{$attendees[0].name}, {$attendees[1]?.name}, and 1 other are attending
    {default}{$attendees[0].name}, {$attendees[1]?.name}, and {remainder($attendees.length)} others are attending
  {/plural}
{/msg}
```

The expression inside the `remainder` function must be exactly identical to the
expression in the `plural` tag (in this case `$attendees.length`). However, the
above example could just as well be written without the use of `offset` and
`remainder`. It would be:

```soy
{let $firstAttendeeGender: $attendees[0]?.gender /}
{let $secondAttendeeGender: $attendees[1]?.gender /}
{msg desc="Says how many people are attending, listing up to 2 names."
     genders="$firstAttendeeGender, $secondAttendeeGender"}
  {plural $attendees.length}
    // Note: length() is never 0.
    {case 1}{$attendees[0].name} is attending
    {case 2}{$attendees[0].name} and {$attendees[1]?.name} are attending
    {case 3}{$attendees[0].name}, {$attendees[1]?.name}, and 1 other are attending
    {default}{$attendees[0].name}, {$attendees[1]?.name}, and {$attendees.length - 2} others are attending
  {/plural}
{/msg}
```

NOTE: In the example above, the `attendees` subkeys are accessed using null-safe
accesses (e.g. `$attendees[1]?.gender` instead of `$attendees[1].gender`) see
the [pitfalls section](#placholder_error) for more information about this.

## fallbackmsg {#fallbackmsg}

Syntax:

```soy
{msg <new_attributes>}
  <new_message>
{fallbackmsg <old_attributes>}
  <old_message>
{/msg}
```

If you want to change a message in-place, without worrying about accidentally
displaying the untranslated new source message to foreign-language users while
the new message is getting translated, use the `fallbackmsg` feature. The
compiler automatically chooses the appropriate version of the message to use. If
the new message's translation is in the message bundle, then it is used, else
the compiler falls back to using the old message's translation. (This decision
happens at message insertion time in JavaScript, and at render time in Java.)

When changing a message, move the old message attributes and content to the
`fallbackmsg` portion. Then, write the new message attributes and content in the
`msg` portion.

For example, to fix a typo in this message:

```soy
{msg desc="button label" meaning="verb" }
  Arcive
{/msg}
```

replace it with the new message:

```soy
{msg desc="button label" meaning="verb" }
  Archive
{fallbackmsg desc="button label" meaning="verb"}
  Arcive
{/msg}
```

When replacing an existing message, you can change the message structure,
meaning, placeholders, etc. There are no technical limitations on the
relationship between the old and new messages.

Periodically, you might want to clean up your code by grepping for old
`fallbackmsg`s and removing them. Although, it's not particularly harmful to
leave `fallbackmsg`s in the code longer than necessary.

## Pitfalls

### Runtime errors in `plural`/`select` placeholders {#placholder_error}

The syntax for `plural` and `select` resemble a `switch` statement, however the
actual runtime behavior does not conditionally execute the different 'branches'.
Instead all placeholders in all branches are evaluated eagerly and then passed
to a formatting library. This is because the actual branch that is selected
depends on the translation itself and cannot be easily predicted. This can cause
issues if some branches have placeholders that will cause runtime errors if
evaluated when their branch won't be expected.

```soy
{msg desc="Welcomes everybody."}
  {plural $people.length offset="2"}
    {case 0}Nobody showed up :(
    {case 1}Greetings {$people[0].name}
    {case 2}Greeting {$people[0].name} and {$people[1].name})}
    {default}Greetings y'all
  {/plural}
{/msg}
```

The problem is that if there are 0 items in the list, then all the placeholders
will fail at runtime trying to access the `name` field of `null`.

To fix this you can use null safe access patterns:

```soy
{msg desc="Welcomes everybody."}
  {plural $people.length offset="2"}
    {case 0}Nobody showed up :(
    {case 1}Greetings {$people[0]?.name}
    {case 2}Greetings {$people[0]?.name} and {$people[1]?.name})}
    {default}Greetings y'all
  {/plural}
{/msg}
```

Now, if the list has too few people in it, the later placeholders will just
evaluate to `undefined` instead of throwing an error.
