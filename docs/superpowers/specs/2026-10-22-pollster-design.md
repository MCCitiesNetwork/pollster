# Pollster — Design

Date: 2026-10-22
Status: Approved in conversation, pending written-spec review

## Purpose

Pollster lets server staff run surveys for players on one Paper server and
publish the results. Players answer through Paper dialogs. Staff build
surveys through dialogs too. Results can be read in-game or shared as a link
to a static web page.

Success looks like this:

- A staff member can build a survey of 40 questions in-game, open it, and
  publish the results as a link, without touching a file.
- A question with 12 options fits on one screen.
- Every player who answers sees exactly the wording that was published. Once
  a survey is open, its questions cannot change.
- An anonymous survey never shows who gave which answer, in-game or on the web.

## Scope

In scope:

- Question types: free text, single choice, multiple choice, yes/no.
- Question order, optional questions, and conditional questions.
- Rewards on completion.
- Surveys that need a permission to take.
- A featured survey shown on join, with a cooldown.
- `/surveys` to list and resume surveys.
- Reminders for unfinished surveys.
- Configurable sounds.
- In-game reports, JSON export, and a web report viewer.
- A per-survey anonymous flag.

Out of scope:

- Inventory (chest) menus. All UI is dialogs and chat.
- Running across several servers. Pollster assumes one server owns its survey
  files and its database.
- SQLite or any database other than MariaDB/MySQL.
- Editing the questions of a survey that is open. Staff duplicate it instead.

`local/` is reserved for uncommitted operator tooling. It is listed in
`.gitignore` and nothing in it is part of the project.

## Platform

- Paper 26.3 and later only. `api-version: "26.3"` in `paper-plugin.yml`.
- Java 25 toolchain.
- Gradle Kotlin DSL with convention plugins in `buildSrc`, and a
  configuration-cache-clean build.
- Release version comes from `-PreleaseVersion`, defaulting to
  `1.0.0-SNAPSHOT`.

## Modules

| Module | Role | Depends on Bukkit? |
|---|---|---|
| `pollster-api` | Survey model records, the condition language, sealed result types, the `PollsterApi` interface, Bukkit events | Events only |
| `pollster-backend` | MyBatis mappers, MariaDB access, schema migrations | No |
| `pollster-paper` | The plugin: configs, commands, dialogs, survey file store, sessions, rewards, reminders, publishing | Yes |
| `pollster-viewer` | React + Vite static site for reports | No (Node) |

The condition parser and evaluator, the session state machine, and report
building live in `pollster-api` or `pollster-backend` so they can be tested
without a server.

The plugin depends on `com.minecraftcitiesnetwork:plugin-infrastructure` for
`MessageContainer`, the MiniMessage `Component` serializer, and date
formatting. That library is not relocated. Everything else that is shaded is
relocated under `com.minecraftcitiesnetwork.pollster.libraries`.

Main dependencies:

- `org.incendo:cloud-paper` 2.x for commands.
- `org.spongepowered:configurate-yaml` 4.2.x for all configuration and survey
  files.
- `org.mybatis:mybatis` 3.5.x with annotation mappers, and
  `org.mariadb.jdbc:mariadb-java-client`.
- `com.fasterxml.jackson.core:jackson-databind` for the snapshot JSON.
- Tests: JUnit 5, Mockito, Testcontainers with MariaDB.

Base package: `com.minecraftcitiesnetwork.pollster`.

## Survey model

### Files

Each survey is one YAML file, `plugins/Pollster/surveys/<id>.yml`. The file is
the source of truth for the survey's definition. The in-game editor reads and
writes these files through Configurate.

The survey `id` is a slug: lower case letters, digits and `-`, 3 to 48
characters, unique. It is also the file name.

```yaml
name: "Approval Ratings — October"
description: "Rate the cabinet and congress. Takes about 3 minutes."
status: OPEN            # DRAFT | OPEN | CLOSED
anonymous: true
featured: true
requires-permission: false   # when true, needs pollster.survey.<id>
rewards:
  - message: "<green>Thanks for taking part!"
  - command: "eco give {player} 500"
questions:
  - key: president
    type: SINGLE
    text: "Do you approve of the President's performance?"
    options:
      - { key: approve, label: "Approve" }
      - { key: disapprove, label: "Disapprove" }
      - { key: abstain, label: "No opinion" }
  - key: party
    type: SINGLE
    text: "Which party do you primarily affiliate with?"
    required: false
    options: [ ... ]
  - key: party-reason
    type: TEXT
    text: "Why do you support that party?"
    required: false
    show-if: 'answer(party) != "none"'
  - key: issues
    type: MULTIPLE
    text: "Which issues matter most to you?"
    min: 1
    max: 3
    options: [ ... ]
```

### Questions

| Field | Meaning |
|---|---|
| `key` | Stable slug, unique within the survey. Answers are stored by key. |
| `type` | `TEXT`, `SINGLE`, `MULTIPLE` or `YES_NO`. |
| `text` | The question as shown, MiniMessage. |
| `options` | For `SINGLE` and `MULTIPLE`: an ordered list of `{key, label}`. 2 to 50 options. Option keys are unique within the question. |
| `required` | Default `true`. An optional question has a Skip button. |
| `min`, `max` | For `MULTIPLE` only. Defaults `1` and the option count. `0 ≤ min ≤ max ≤ options`. A `min` of 0 is allowed only when the question is optional. |
| `show-if` | Optional condition. The question is shown only when it is true. |

The order of the `questions` list is the order players see. `YES_NO` stores the
option keys `yes` and `no`.

### Validation

A survey file is validated when it is loaded and before the editor saves it.
A file that fails validation is not loaded. The log line names the file, the
question key and the problem. Other surveys still load.

Rules:

- Unique survey id, question keys and option keys.
- Required fields present for each type.
- Every condition parses.
- A condition refers only to questions that come before it.
- A condition compares a question to option keys that exist on that question.

### Lifecycle

```
DRAFT ──open──▶ OPEN ──close──▶ CLOSED
                  ▲                │
                  └────reopen──────┘
```

- **DRAFT.** Fully editable. Admins can preview it. Players cannot see it.
- **OPEN.** Players can take it. The question content is locked: the
  `questions` list, including text, type, options, order, `required`,
  `min`/`max` and conditions. These can still change: `name`, `description`,
  `featured`, `requires-permission`, `rewards`, and `status`.
- **CLOSED.** No new responses. Reports still work. Only `status` can change.
- **Duplicate.** Copies any survey into a new DRAFT under a new id. This is
  the only way to change the questions of a survey that has been opened.
- **Delete.** A DRAFT with no responses is deleted after one confirmation. A
  survey with responses needs two confirmations, and deleting it removes its
  responses from the database.

A survey that has been opened never returns to DRAFT.

### Content lock

When a survey moves from DRAFT to OPEN, Pollster computes a SHA-256 hash over a
canonical form of its question content and stores it in the database.

On load and before each response is accepted, the hash of the file is compared
with the stored hash. If they differ, someone has edited the file by hand.
Pollster then:

- logs a warning naming the survey,
- shows the survey to admins as "Content changed — locked",
- refuses new sessions and submissions for it.

To recover, an admin restores the file or duplicates the survey.

### Conditions

A condition is a small expression in `show-if`. It is checked on the server
each time the session moves to another question.

Grammar:

```
expr       := or
or         := and ( "or" and )*
and        := not ( "and" not )*
not        := "not" not | atom
atom       := "(" expr ")" | comparison | "permission(" STRING ")"
comparison := "answer(" KEY ")" op value
op         := "==" | "!=" | "in" | "has" | "answered"
value      := STRING | "[" STRING ( "," STRING )* "]"
```

| Form | True when |
|---|---|
| `answer(q) == "a"` | The answer to `q` is option `a`. For `TEXT`, the text equals `a` ignoring case. |
| `answer(q) != "a"` | The question was answered and the answer is not `a`. |
| `answer(q) in ["a","b"]` | The answer is one of the listed options. |
| `answer(q) has "a"` | `MULTIPLE` only: option `a` is among the selections. |
| `answer(q) answered` | The question was shown and not skipped. (`answered` takes no value.) |
| `permission("node")` | The player has the permission. |

A question that was hidden or skipped has no answer. Every comparison except
`answered` is false for it, and `answered` is false too.

The parser reports errors with a column number, for example
`Unknown question 'partyy' at column 8`.

### Rewards

Rewards run once, on the main thread, after a response is saved. They do not
run in preview. `{player}` is replaced with the player's name. `{survey}` is
replaced with the survey name.

| Entry | Effect |
|---|---|
| `command: "..."` | Runs as the console. |
| `player-command: "..."` | Runs as the player. |
| `message: "..."` | Sends a MiniMessage line to the player. |
| `title: { title: "...", subtitle: "..." }` | Shows a title. |
| `broadcast: "..."` | Sends a MiniMessage line to everyone. |

A reward that throws is logged and does not stop the others.

## Player experience

All screens use the shared dialog style described in "Dialog style".

### Finding a survey

`/surveys` opens the survey list. It shows every OPEN survey the player may
see: one button per survey labelled with its name and the player's state,
`New`, `In progress` or `Completed` (gray, not clickable). A survey that
requires a permission is hidden from players who lack it. When nothing is
available, the dialog says so.

Choosing a survey opens its intro dialog:

- name and description,
- the number of questions,
- an anonymity line: "Your answers are anonymous." or "Your name is recorded
  with your answers.",
- "Completing this survey gives rewards." when it has rewards,
- **Start** and **Close**.

### Featured surveys

When a player joins and `featured.on-join` is enabled, Pollster looks for a
featured OPEN survey the player may take and has not completed. If it finds
one, and the player has not been shown a featured survey within the cooldown,
it opens the intro dialog after the configured delay. The intro then also has
a **Not now** button. Showing the dialog records the time in `pollster_player_state`.

When several surveys are featured, the oldest opened one is shown first.

### Answering

Each question is its own dialog. The header is the survey name, then
`Question 3 of 12`, then the question text. The count is the number of
questions the player will see given the answers so far. It may change when
an answer changes which questions are shown.

| Type | Body | Buttons |
|---|---|---|
| `SINGLE` | — | One button per option, two columns. Choosing one answers and moves on. |
| `MULTIPLE` | One checkbox per option | **Next** |
| `YES_NO` | — | Green **Yes**, red **No** |
| `TEXT` | Multi-line text input, `max-length` from settings (default 255) | **Next** |

Every question also has **Back** (except the first) and **Skip** (optional
questions only). Each answer the player has already given is shown as the
starting value.

When an answer is invalid (a required text left empty, or a multiple-choice
selection outside `min`/`max`), the same dialog reopens with a red line
explaining the problem and the player's input kept.

Going back lets the player change an earlier answer. Conditions are checked
again on every move. Answers to questions that are now hidden are dropped.

### Review and submit

After the last shown question, a review dialog lists each shown question and
its answer, with **Submit**, **Back** and **Cancel**.

Submit saves the whole response in one transaction. Then rewards run, a
thank-you dialog opens, and the finish sound plays. If the player already
responded (a race between two submits), they see "You have already completed
this survey." and no rewards run.

### Leaving partway

Closing a dialog or pressing Escape pauses the session in memory.

- After `reminders.idle` (default 60 seconds) without progress, the player
  gets a chat line, "You have an unfinished survey — click to continue", which
  runs `/surveys resume`.
- The reminder repeats every `reminders.repeat` (default 300 seconds; `0`
  sends it once). Reminders can be turned off.
- A paused session survives a disconnect and is kept until the server stops
  or the player finishes or cancels it.
- Starting a different survey replaces the paused one after a confirmation.

Nothing is written to the database until Submit.

### Preview

Admins can preview any survey from the editor. Preview runs the full flow,
including conditions, but saves nothing and runs no rewards. Every preview dialog has a
`PREVIEW` marker in its title.

## Admin editor

`/pollster` opens the editor for players with `pollster.admin`.

### Survey list

A paginated list. Each button reads `id · Name [STATUS] · n responses`.
Buttons: **Create survey**, page controls, **Close**. A survey whose content
lock failed shows `[LOCKED]`.

### Creation wizard

1. **Basics:** id, name, description (text inputs). **Next** / **Cancel**.
2. **Settings:** checkboxes for anonymous, featured, requires permission.
   **Next** / **Back**.
3. **Confirm:** a summary. **Create** / **Back** / **Cancel**.

A bad or duplicate id sends the player back to step 1 with an inline error.
Create writes the file as a DRAFT and opens the survey hub.

### Survey hub

Body: status, responses, questions, anonymous, featured, permission, reward
count.

Buttons, each showing its current value:

- **Name**, **Description**
- **Questions (n)**
- **Rewards (n)**
- **Anonymous**, **Featured**, **Requires permission** (toggles; Anonymous
  is a DRAFT-only toggle)
- **Preview**
- **Report**
- The status action: **Open survey** (DRAFT; a confirmation explains that
  questions lock), **Close survey** (OPEN), or **Reopen** (CLOSED)
- **Duplicate** (asks for the new id)
- **Delete** (red; see Lifecycle for confirmations)
- **Back**

### Questions

The question list shows one button per question, for example
`1. [SINGLE] President ⓘ`, where `ⓘ` marks a question with a condition.
**Add question** and **Back** follow. On a survey that is not a DRAFT, every
button opens a read-only view.

The question editor (DRAFT only):

- Inputs: key, text, type (single option), required.
- **Options** (`SINGLE`, `MULTIPLE`): opens the option list. Each option has
  Edit (key and label), Remove, and ▲/▼. **Bulk add** takes a text box with
  one option per line, in the form `key: Label` or just `Label`; a missing key
  is generated from the label.
- **Min / max** (`MULTIPLE`): two sliders.
- **Condition:** a text input. It is validated on save, and errors show inline
  with the column.
- **Move ▲/▼:** blocked, with an explanation, when the move would put a
  question before one it refers to, or after one that refers to it.
- **Delete question:** confirmation. Blocked when another question's
  condition refers to it.
- **Save** / **Back**.

### Rewards

A list of reward entries, each with a ✕ to remove. **Add reward** asks for
the type, then the value.

### Report

- Totals: participants (submitted responses), average completion time.
- Per question:
  - Choice questions: each option with a 10-block bar, percentage and votes,
    and "Shown to n of N" for conditional questions.
  - Text questions: the answer count and **View answers**, a paginated list.
    Names are shown only when the survey is not anonymous.
- Buttons: **Publish to web**, **Export JSON**, **Back**.

Publish posts a clickable link in chat. Export writes
`plugins/Pollster/exports/<id>-<timestamp>.json` and posts the path.

### Feedback

Validation errors show inside the dialog. Results of actions (saved,
published, failed to publish) go to chat, and the dialog is rebuilt so it
shows current values.

## Dialog style

All dialogs are built through one builder in `pollster-paper`. Screens are
classes that build their dialog on construction and are opened fresh each
time, so they always show current data. Button callbacks are
`DialogAction.customClick` with a 10-minute lifetime.

Colors and text come from `messages.yml`. The defaults:

| Element | Style |
|---|---|
| Titles | gold, bold |
| Destructive or confirmation titles | red, bold |
| Field labels | aqua; values gray; emphasis white bold |
| Primary action (Start, Submit, Save, Create) | green, bold |
| Edit and navigation into a sub-screen | yellow, bold |
| Destructive (Delete, Remove) | red, bold |
| Back, Close, Cancel, page controls | gray |
| Inline errors | red line at the top of the body |

Shared screens: Confirmation (Confirm / Cancel), Error (message and OK), and
Loading (a notice with no buttons, shown while a database call runs).

## Backend

### Schema

MariaDB. Every table is prefixed `pollster_` so the database can be shared
with other plugins. Migrations are SQL files in
`pollster-backend/src/main/resources/sql/migrations/V{n}__{name}.sql`, applied
in order by plugin-infrastructure's `SchemaMigrator` that records each version in `pollster_schema_version`, commits
after each step, and refuses to start against a schema newer than it knows.

```sql
CREATE TABLE pollster_survey_state (
  survey_id     VARCHAR(48) PRIMARY KEY,
  content_hash  CHAR(64)    NOT NULL,
  opened_at     TIMESTAMP   NOT NULL,
  closed_at     TIMESTAMP   NULL
);

CREATE TABLE pollster_responses (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  survey_id     VARCHAR(48) NOT NULL,
  player_uuid   BINARY(16)  NOT NULL,
  started_at    TIMESTAMP   NOT NULL,
  submitted_at  TIMESTAMP   NOT NULL,
  UNIQUE KEY uq_response (survey_id, player_uuid),
  KEY ix_survey (survey_id)
);

CREATE TABLE pollster_answers (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  response_id   BIGINT       NOT NULL,
  question_key  VARCHAR(48)  NOT NULL,
  option_key    VARCHAR(48)  NULL,
  text_value    TEXT         NULL,
  FOREIGN KEY (response_id) REFERENCES pollster_responses(id) ON DELETE CASCADE,
  KEY ix_question (response_id, question_key)
);

CREATE TABLE pollster_player_state (
  player_uuid       BINARY(16) PRIMARY KEY,
  last_featured_at  TIMESTAMP  NULL
);
```

Pollster stores only the player's UUID, never their name. Names for
non-anonymous reports are looked up by UUID when the report is built, from
the server's user cache (or a player-directory plugin when one is installed);
an unknown player shows as a shortened UUID.

A `MULTIPLE` answer is one row per selected option. A skipped question has
one row with both `option_key` and `text_value` null. A hidden question has
no rows.

### Access

- MyBatis annotation mappers with a database-neutral mapper interface and a
  MariaDB subinterface holding the SQL. Entities are records.
- A UUID type handler stores UUIDs as `BINARY(16)`.
- All calls run on a fixed database executor. Results hop back to the main
  thread through the server's main-thread executor.
- Backend methods return sealed result types, for example:
  - `SubmitResult`: `Saved(responseId) | AlreadyResponded | SurveyNotOpen | ContentChanged`
  - `OpenResult`: `Opened | AlreadyOpen`

### Reports

A report is built from aggregate queries: counts per option, counts of
respondents shown each question, average of `submitted_at - started_at`, and
for text questions the answers themselves. "Shown to" is the number of
responses with any answer row for the question, including skip rows, so
hidden and skipped questions can be told apart.

## Configuration

Configurate YAML, loaded as `@ConfigSerializable` records. Defaults and
validation live in each record's compact constructor. On load, the bundled
file is merged into the live file so new keys appear, and a reference copy
is written to `defaults/default-<name>.yml`. All settings are held in
atomic references and swapped by `/pollster reload`, which runs off the main
thread.

| File | Contents |
|---|---|
| `database.yml` | `url` (without `jdbc:`), `username`, `password`. Ships blank. The plugin disables itself with an explanation when `url` is empty. |
| `settings.yml` | Featured on-join, delay and cooldown; reminders enabled, idle and repeat; text answer max length; sounds; publishing paste-service URL and viewer URL. |
| `messages.yml` | Every chat and dialog string, MiniMessage, with keys in a `MessageKeys` constants class. Player-supplied text is always inserted with unparsed placeholders. |
| `surveys/*.yml` | One survey per file. |

Sounds are `{ key, volume, pitch }` for `open`, `next`, `error` and `finish`.
An empty key disables a sound.

A survey file that fails to load is skipped and logged. Bundled config files
are loaded by guard tests exactly as the plugin loads them.

## Commands and permissions

Incendo Cloud, one record per command.

| Command | Permission | Default |
|---|---|---|
| `/surveys` | `pollster.use` | true |
| `/surveys resume` | `pollster.use` | true |
| `/pollster` | `pollster.admin` | op |
| `/pollster report <id>` | `pollster.admin.report` | op |
| `/pollster publish <id>` | `pollster.admin.report` | op |
| `/pollster export <id>` | `pollster.admin.report` | op |
| `/pollster reload` | `pollster.admin.reload` | op |
| — (take a permission-gated survey) | `pollster.survey.<id>` | false |

All permission nodes are declared in `paper-plugin.yml` with descriptions.

## API

`pollster-api` exposes a `PollsterApi` service (registered with the services
manager) for reading surveys and a player's completion state, and these
events:

| Event | When | Cancellable |
|---|---|---|
| `SurveyStartEvent` | A player starts a survey (not preview) | Yes |
| `SurveySubmitEvent` | Before a response is saved | Yes |
| `SurveyCompletedEvent` | After a response is saved and rewards ran | No |

## Web report

### Snapshot

Publishing builds a snapshot from the current results, serialises it with
Jackson, and uploads it with a `POST` to a bytebin-compatible paste service
(`publishing.paste-url`). The response's key becomes the link
`<publishing.viewer-url>/#<key>`.

```json
{
  "format": 1,
  "generatedAt": "2026-10-22T18:00:00Z",
  "survey": {
    "id": "approval-2026-10",
    "name": "...",
    "description": "...",
    "anonymous": true,
    "openedAt": "...",
    "closedAt": null
  },
  "totals": { "participants": 52, "averageSeconds": 184 },
  "questions": [
    {
      "key": "president",
      "type": "SINGLE",
      "text": "...",
      "shownTo": 52,
      "skipped": 0,
      "options": [ { "key": "approve", "label": "Approve", "votes": 28 } ]
    },
    {
      "key": "issues",
      "type": "MULTIPLE",
      "shownTo": 52,
      "options": [ ... ],
      "selectionsPerRespondent": { "1": 10, "2": 25, "3": 17 }
    },
    {
      "key": "party-reason",
      "type": "TEXT",
      "shownTo": 30,
      "answers": [ { "text": "...", "player": null } ]
    }
  ]
}
```

For an anonymous survey `player` is always `null` and text answers are
shuffled. For other surveys `player` is the player's current name, looked up
by UUID when the snapshot is built.
Question text is converted from MiniMessage to plain text.

Publishing failures (network, non-2xx) are reported in chat with the reason.

### Viewer

`pollster-viewer` is a React + Vite site built to static files and deployed to
GitHub Pages by a workflow.

- Reads the paste key from the URL fragment, fetches the snapshot from the
  paste service, and renders it. No backend, no tracking.
- Header: name, description, "Anonymous survey" badge, tiles for
  participants and average time.
- Choice questions: horizontal bars sorted by votes, with counts and
  percentages, and "Shown to n of N" when a question was conditional or
  skippable.
- Multiple choice also shows the selections-per-respondent distribution.
- Text questions: a searchable, paginated list.
- Error screens for a missing key, a paste that cannot be fetched, and a
  `format` newer than the viewer supports.
- Light and dark themes. A Download JSON button.

The paste service URL the viewer fetches from is a build-time setting with a
default, and can be overridden by a `?paste=` query parameter.

## Testing

- `pollster-api`: unit tests for the condition parser (valid forms, each
  error with its column), the evaluator, survey validation, the content hash
  (stable under formatting changes, different under content changes), and
  the session state machine (forward, back, re-evaluation dropping answers,
  skip).
- `pollster-backend`: Testcontainers with MariaDB. Migrations from empty,
  submit, the one-response rule, two concurrent submits for one player,
  report aggregates including hidden versus skipped, cascade delete.
- `pollster-paper`: guard tests that load each bundled config file and a
  sample survey file; survey YAML round-trips through the store unchanged.
- `pollster-viewer`: vitest rendering of sample snapshots, including an
  anonymous one and an unsupported format.

Test names follow `subject_condition`.

## Repository

- `.gitignore` covers build output, IDE files, `run/`, `.env`, Node output,
  and `local/`.
- CI: build all modules and run tests; build and deploy the viewer to Pages
  from `main`.
- Docs: specs and plans under `docs/superpowers/{specs,plans}/`.
- Nullability with `org.jetbrains.annotations`. Records and sealed
  interfaces where they fit. Comments explain why, not what.
- Manual constructor injection from the plugin class, which is the
  composition root. No static `Bukkit` access outside it.
- `database.yml` and any secrets are never committed. A `compose.dev.yml`
  starts a development MariaDB for `runServer`.
