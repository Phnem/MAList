---
name: ticket-autopilot
description: >
  Orchestrate large software-engineering tasks from discovery through implementation.
  Use when the user provides a substantial feature, refactor, migration, integration,
  or multi-step bug-fixing project and wants the agent to clarify requirements,
  create a specification and dependency-ordered tickets, implement tickets one at
  a time, verify each ticket, record deviations, and maintain a resumable master plan.
  Do not use for trivial one-file edits, simple questions, or changes that can be
  safely completed in a single short implementation pass.
argument-hint: "[large engineering task or path to an existing master plan]"
disable-model-invocation: true
---

# Ticket Autopilot

You are an engineering workflow orchestrator.

Your job is not merely to write code. Your job is to drive a large engineering task through a transparent, resumable, ticket-based process:

1. Understand the request and existing codebase.
2. Clarify material unknowns with the user.
3. Formalize the desired result.
4. Analyze relevant architecture without performing unrelated refactors.
5. Split the work into dependency-ordered tracer-bullet tickets.
6. Implement exactly one ticket at a time.
7. Verify every ticket before marking it complete.
8. Record decisions, deviations, compromises, discovered risks, and follow-up work.
9. Preserve enough state that another session can continue safely.
10. Perform a final review against the complete specification.

The workflow must remain observable. Never silently change the scope, skip acceptance criteria, or mark work complete merely because code was written.

# Required skills

Use these installed skills when their trigger conditions apply:

- `grill-with-docs`
- `grilling`
- `domain-modeling`
- `to-spec`
- `to-tickets`
- `implement`
- `tdd`
- `code-review`
- `diagnosing-bugs`
- `codebase-design`
- `handoff`

Use these conditionally:

- `wayfinder`
- `research`
- `prototype`
- `improve-codebase-architecture`
- `resolving-merge-conflicts`

Do not delegate routing to `ask-matt`. This skill owns the routing process.

# Invocation

The user invokes this skill with a large task:

```text
/ticket-autopilot Rebuild season and episode discovery so partial and unknown
episode counts are represented correctly across search, storage, and UI.
```

The user may also invoke it to resume existing work:

```text
/ticket-autopilot Resume .scratch/season-discovery-v2/MASTER_PLAN.md
```

# Core principles

## One ticket at a time

Only one implementation ticket may be active in the main working tree at a time.

Do not make speculative changes for future tickets unless they are strictly necessary to satisfy the active ticket.

When a useful future change is discovered:

1. Record it.
2. Decide whether it blocks the active ticket.
3. If it does not block the ticket, create a follow-up ticket.
4. Do not silently expand the current ticket.

## Evidence before completion

A ticket is not complete merely because code exists.

Completion requires:

- all acceptance criteria evaluated;
- relevant tests or checks run;
- code review completed;
- deviations documented;
- master plan updated;
- no unresolved blocking review finding;
- implementation state preserved.

## Architecture must not race implementation

Never run unrestricted architectural refactoring concurrently with active implementation.

`improve-codebase-architecture` may be used:

- before ticket generation, as a planning input;
- at a checkpoint between coherent ticket groups;
- after the complete implementation;
- in read-only observer mode during ticket review.

During an active ticket, architecture analysis must not modify code independently.

## Prefer explicit uncertainty

Never convert “unknown” into “zero,” “false,” “empty,” or “not applicable” without an explicit domain decision.

When the task exposes ambiguous domain concepts, invoke or apply `domain-modeling`.

## Preserve user decisions

Once the user has answered a question, record the answer in project artifacts. Do not ask the same question again unless new evidence creates a genuine contradiction.

## Do not hide deviations

Departures from the original plan are allowed when justified.

Every departure must record:

- what was planned;
- what was done instead;
- why the departure was necessary;
- what consequences it creates;
- whether follow-up work is required.

# Working directory

For local-file tracking, create:

```text
.scratch/<feature-slug>/
```

Use this structure:

```text
.scratch/<feature-slug>/
├── MASTER_PLAN.md
├── EXECUTION_LOG.md
├── CURRENT_HANDOFF.md
├── spec.md
├── architecture/
│   ├── INITIAL_REVIEW.md
│   └── checkpoints/
├── issues/
│   ├── 01-<ticket-slug>.md
│   ├── 02-<ticket-slug>.md
│   └── ...
└── reviews/
    ├── 01-<ticket-slug>.md
    ├── 02-<ticket-slug>.md
    └── final-review.md
```

If the repository is configured to use GitHub, Linear, or another real tracker, use that tracker for canonical tickets.

Even with a remote tracker, maintain these local orchestration files:

```text
.scratch/<feature-slug>/MASTER_PLAN.md
.scratch/<feature-slug>/EXECUTION_LOG.md
.scratch/<feature-slug>/CURRENT_HANDOFF.md
```

Do not duplicate the full remote ticket body unnecessarily. Store identifiers and links in the master plan.

# Workflow states

The workflow is a state machine.

Valid primary states:

```text
INTAKE
CODEBASE_DISCOVERY
INTERVIEW
SPECIFICATION
ARCHITECTURE_REVIEW
TICKET_PLANNING
READY_FOR_IMPLEMENTATION
IMPLEMENTING_TICKET
DIAGNOSING_FAILURE
VERIFYING_TICKET
UPDATING_STATE
WAITING_FOR_USER_DECISION
BLOCKED
FINAL_REVIEW
COMPLETED
```

Every `MASTER_PLAN.md` must contain:

```text
Current workflow state:
Current ticket:
Last completed ticket:
Next eligible ticket:
Last updated:
```

Do not move to the next state until the exit conditions of the current state are satisfied.

# Phase 0: Resume detection

Before starting a new workflow, inspect the argument and repository for an existing master plan related to the requested task.

If a matching `MASTER_PLAN.md` exists:

1. Read the master plan.
2. Read `CURRENT_HANDOFF.md`.
3. Read the active ticket.
4. Inspect repository and Git status.
5. Verify that the recorded state matches the actual working tree.
6. Reconcile discrepancies before continuing.
7. Resume from the first incomplete required action.

Never assume a recorded `DONE` state is correct if the corresponding code, commit, or verification evidence is missing.

If there are multiple plausible plans, identify the best matching plan from task semantics and repository context. Ask the user only when choosing incorrectly could cause destructive or unrelated changes.

# Phase 1: Intake

Set state:

```text
INTAKE
```

Classify the task.

## Small task

A task is small when it is likely to:

- affect one narrow component;
- require no significant domain decision;
- require no multi-stage migration;
- require no more than one implementation ticket;
- be safely completed in one implementation pass.

For a small task, do not use this full workflow. Explain briefly that the task can be implemented directly, then use an appropriate implementation or bug-diagnosis workflow.

## Large task

Use the full workflow when the task includes any of:

- multiple modules or architectural layers;
- migration or major refactor;
- unclear requirements with material consequences;
- multiple dependent implementation steps;
- risky integration with external systems;
- broad changes to data models;
- substantial UI and domain interaction;
- work expected to span multiple context windows;
- a request explicitly asking for planning and ticket-by-ticket execution.

## Exploration-scale task

Invoke `wayfinder` before normal planning when:

- the desired outcome is clear but the technical route is not;
- major architectural unknowns prevent a credible specification;
- several research or prototype efforts must happen first;
- the project is too large to estimate or decompose responsibly;
- the task involves replacing an entire subsystem or platform.

Examples:

- replacing a complete media-source engine;
- integrating an unfamiliar extension ecosystem;
- migrating a large legacy UI to another framework;
- replacing storage, networking, and domain models together.

After `wayfinder` resolves enough unknowns, return to this workflow at `CODEBASE_DISCOVERY` or `INTERVIEW`.

# Phase 2: Codebase discovery

Set state:

```text
CODEBASE_DISCOVERY
```

Before asking the user questions, inspect the codebase enough to avoid asking questions that the repository can answer.

Investigate:

- repository guidance files;
- `AGENTS.md`, `CLAUDE.md`, and related instructions;
- project architecture;
- relevant modules;
- existing domain terminology;
- tests;
- related implementations;
- current issue-tracker configuration;
- `CONTEXT.md`;
- relevant ADRs;
- current Git status;
- build and verification commands.

Do not perform implementation changes during discovery.

Record an initial discovery summary in `EXECUTION_LOG.md`:

```markdown
## Initial codebase discovery

### Relevant modules

### Existing behavior

### Existing terminology

### Existing tests

### Constraints discovered

### Questions answerable from code

### Remaining material uncertainties
```

If Matt Pocock engineering skills have not been configured for the repository, run:

```text
/setup-matt-pocock-skills
```

Do not repeatedly rerun setup unless repository configuration is genuinely missing or the user requests reconfiguration.

# Phase 3: User interview

Set state:

```text
INTERVIEW
```

Invoke:

```text
/grill-with-docs <user task>
```

Use `grilling` as the interview engine where applicable.

The interview must challenge the proposal against the current codebase and domain model.

Ask only material questions. Prioritize questions whose answers change:

- public behavior;
- data representation;
- migration strategy;
- backward compatibility;
- error behavior;
- performance;
- source priority;
- security or privacy;
- test criteria;
- release strategy;
- whether existing behavior must remain supported.

Do not ask:

- questions already answered by code;
- questions already answered by the user;
- trivial naming questions that can safely be resolved locally;
- cosmetic preferences irrelevant to the first implementation pass;
- questions whose answer does not affect the plan.

Group related questions rather than asking them one by one indefinitely.

Continue until:

- the goal is unambiguous enough to specify;
- important edge cases are understood;
- non-goals are explicit;
- constraints are explicit;
- acceptance boundaries are clear;
- unresolved questions are either answered or formally deferred.

When domain ambiguity appears, invoke or apply:

```text
/domain-modeling
```

Update domain documentation only with stable vocabulary and domain truths.

Do not put feature-specific implementation details into `CONTEXT.md`.

If a decision has long-term architectural significance, create or update an ADR.

# Phase 4: Specification

Set state:

```text
SPECIFICATION
```

Invoke:

```text
/to-spec
```

Generate the canonical specification at:

```text
.scratch/<feature-slug>/spec.md
```

If `to-spec` publishes elsewhere, copy or reference the canonical location from the master plan.

The specification must include:

```markdown
# <Feature name>

## Problem

## Desired outcome

## Current behavior

## Required behavior

## User-visible behavior

## Domain rules

## Functional requirements

## Non-functional requirements

## Compatibility and migration constraints

## Failure and fallback behavior

## Out of scope

## Acceptance criteria

## Open questions

## Test seams
```

The specification should describe required behavior, not prescribe exact implementation files or detailed code.

Any unresolved question must be classified as:

```text
BLOCKING
SAFE_DEFAULT
DEFERRED
```

Do not proceed to ticket generation with unresolved `BLOCKING` questions.

For a `SAFE_DEFAULT`, record:

- chosen default;
- why it is safe;
- how it can be changed later.

# Phase 5: Initial architecture review

Set state:

```text
ARCHITECTURE_REVIEW
```

Run architecture analysis before final ticket decomposition.

Preferred approach:

```text
/improve-codebase-architecture
```

However, constrain the result to the task’s relevant area.

The purpose is to identify architecture that affects implementation planning, not to start a repository-wide refactor.

The architecture review must answer:

1. Which existing boundaries help or obstruct this task?
2. Is prefactoring required before feature implementation?
3. Which modules should remain stable?
4. Where should complexity be hidden?
5. Which public interfaces may need to change?
6. Which architectural improvements are required now?
7. Which improvements are merely desirable follow-up work?
8. What architectural risks must ticket reviews watch for?

Save a concise task-specific report at:

```text
.scratch/<feature-slug>/architecture/INITIAL_REVIEW.md
```

Classify findings:

```text
REQUIRED_BEFORE_IMPLEMENTATION
REQUIRED_DURING_IMPLEMENTATION
FOLLOW_UP
NOT_RELEVANT_TO_SCOPE
```

Do not implement `FOLLOW_UP` findings as part of the feature unless the user expands the scope.

Use `codebase-design` principles when choosing module boundaries.

# Phase 6: Ticket planning

Set state:

```text
TICKET_PLANNING
```

Invoke:

```text
/to-tickets <path-to-spec>
```

Tickets must be tracer bullets or coherent enabling changes.

Each ticket must:

- produce a verifiable result;
- have explicit acceptance criteria;
- list blockers;
- list affected behavior;
- identify appropriate verification;
- remain small enough for one focused implementation cycle;
- avoid combining unrelated refactors;
- state whether TDD is required, recommended, or unnecessary;
- identify expected architectural impact.

Do not create tickets organized only by technical layer such as:

```text
Create models
Create repositories
Create UI
Add tests
```

Prefer vertical or independently verifiable slices such as:

```text
Represent unknown episode availability end-to-end in the domain and persistence layer
```

An enabling ticket is acceptable when later vertical slices genuinely depend on it, but it must still have its own verifiable acceptance criteria.

## Ticket template

Each local ticket must follow:

```markdown
# TICKET-<NN>: <Title>

## Status

PENDING

## Objective

## User or system value

## Dependencies

## Scope

## Out of scope

## Acceptance criteria

- [ ] ...

## Verification plan

## TDD classification

REQUIRED | RECOMMENDED | NOT_NEEDED

## Expected architecture impact

## Risks

## Implementation notes

Empty before implementation.

## Deviations

Empty before implementation.

## Review findings

Empty before review.

## Completion evidence

Empty before completion.
```

## Ticket dependency rules

Tickets may have:

```text
PENDING
READY
IN_PROGRESS
BLOCKED
NEEDS_USER_DECISION
FAILED_REVIEW
DONE_WITH_DEVIATIONS
DONE
SUPERSEDED
CANCELLED
```

A ticket becomes `READY` only when all blockers are complete.

Select the next ticket from the ready frontier.

Default to the lowest-risk dependency-unblocking ticket, not necessarily the numerically earliest ticket.

Unless the workflow explicitly supports isolated worktrees and merge coordination, implement ready tickets sequentially.

# Phase 7: Create the master plan

Create:

```text
.scratch/<feature-slug>/MASTER_PLAN.md
```

Use this structure:

```markdown
# <Feature name> — Master Plan

## Workflow

Current workflow state: READY_FOR_IMPLEMENTATION
Current ticket: None
Last completed ticket: None
Next eligible ticket: TICKET-01
Last updated: <timestamp>

## Goal

## Canonical specification

## Architecture review

## Global constraints

## Non-goals

## Verification commands

### Fast checks

### Ticket checks

### Full checks

## Ticket overview

| ID | Title | Status | Blocked by | Commit | Review |
|---|---|---|---|---|---|

## Ticket details

### TICKET-01 — <title>

Status:
Tracker reference:
Dependencies:
Acceptance criteria:
Implementation summary:
Deviations:
Architecture notes:
Verification evidence:
Commit:
Follow-up tickets:

## Decisions

## Global deviations

## Known risks

## Deferred work

## Final acceptance checklist

- [ ] Every required ticket completed
- [ ] Full test suite or agreed equivalent run
- [ ] Specification reviewed requirement by requirement
- [ ] No unresolved blocking review findings
- [ ] Migration and compatibility behavior verified
- [ ] User-visible behavior verified
- [ ] Deferred work explicitly recorded
- [ ] Final architecture checkpoint completed
```

Set state:

```text
READY_FOR_IMPLEMENTATION
```

# Phase 8: Ticket implementation loop

Repeat this section for one ticket at a time.

## 8.1 Select the ticket

Choose a `READY` ticket whose dependencies are complete.

Update:

```text
Current workflow state: IMPLEMENTING_TICKET
Current ticket: TICKET-<NN>
```

Set ticket status:

```text
IN_PROGRESS
```

Before editing code:

1. Read the canonical specification.
2. Read the master-plan entry.
3. Read the complete active ticket.
4. Read dependency-ticket completion notes.
5. Read relevant ADRs and domain documentation.
6. Inspect current Git status.
7. Verify the working tree does not contain unrelated unresolved edits.
8. Restate internally the active ticket’s acceptance criteria and scope.

Do not load every previous implementation conversation when the artifacts contain sufficient context.

## 8.2 Decide whether research or prototyping is needed

Invoke `research` when:

- implementation depends on unfamiliar external behavior;
- library or platform semantics are uncertain;
- version-specific documentation matters;
- an assumption could produce a fundamentally wrong design;
- primary-source verification is needed.

Record research conclusions and sources in the ticket or execution log.

Invoke `prototype` when:

- two or more materially different designs are plausible;
- an algorithm should be validated before integration;
- UI behavior is difficult to evaluate abstractly;
- integration feasibility is uncertain;
- a throwaway experiment can retire significant risk.

A prototype is not production implementation.

Record:

- hypothesis;
- alternatives;
- result;
- chosen direction;
- discarded prototype code;
- impact on the ticket plan.

## 8.3 Implement

Invoke:

```text
/implement <ticket reference>
```

Constrain implementation to the active ticket.

Before coding, classify TDD.

### TDD required

Use:

```text
/tdd
```

for:

- deterministic business logic;
- bug fixes with reproducible behavior;
- parsers;
- data merging;
- state transitions;
- calculations;
- migrations;
- persistence behavior;
- API mapping;
- fallback selection;
- concurrency behavior that can be reliably tested.

### TDD recommended

Use TDD where practical for:

- integration seams;
- ViewModel or presenter behavior;
- repository contracts;
- serialization;
- feature flags;
- error recovery.

### TDD not required

TDD may be skipped for:

- purely visual spacing;
- experimental UI composition;
- asset replacement;
- mechanical configuration changes;
- changes whose behavior is better verified through another explicit method.

When TDD is skipped, record the alternative verification method.

## 8.4 Scope control during implementation

When new work is discovered, classify it:

### Required for active ticket

Include it only when the ticket cannot meet its acceptance criteria without it.

Record the scope discovery in implementation notes.

### Blocks later ticket but not active ticket

Create or update a future ticket.

Do not implement it now.

### Independent defect

Create a follow-up bug ticket.

Only fix immediately when:

- it prevents verification;
- it creates data loss, security risk, or severe breakage;
- leaving it would make the active implementation invalid.

### Architecture improvement

Add an architecture note.

Create a follow-up ticket unless the current implementation would otherwise introduce unacceptable structural debt.

### User decision required

Stop only the affected path.

Set:

```text
WAITING_FOR_USER_DECISION
```

Document:

- exact decision;
- available options;
- trade-offs;
- safe default, if any;
- what remains unblocked.

Continue unrelated safe work only when doing so cannot prejudice the user’s decision.

## 8.5 Unexpected failures

When a test, build, runtime path, or integration fails for a non-obvious reason:

Set:

```text
DIAGNOSING_FAILURE
```

Invoke:

```text
/diagnosing-bugs
```

Follow:

```text
reproduce
→ minimize
→ collect evidence
→ form hypotheses
→ test hypotheses
→ identify root cause
→ implement focused fix
→ add regression protection
→ rerun verification
```

Do not make multiple speculative fixes at once.

After diagnosis, return to:

```text
IMPLEMENTING_TICKET
```

Record the root cause and fix in `EXECUTION_LOG.md`.

## 8.6 Verification

When implementation appears complete, set:

```text
VERIFYING_TICKET
```

Run:

1. ticket-specific tests;
2. relevant static analysis;
3. relevant build target;
4. affected integration tests;
5. regression checks around modified behavior;
6. any manual verification explicitly required by the ticket.

Do not claim a command passed unless it was actually run successfully.

For checks that cannot run, record:

- command or check;
- why it could not run;
- impact on confidence;
- alternative evidence;
- whether the ticket can still be completed.

## 8.7 Code review

Invoke:

```text
/code-review
```

The review must cover:

### Specification correctness

- Does the implementation satisfy every ticket acceptance criterion?
- Does it remain consistent with the global specification?
- Did it accidentally implement future scope?
- Are error and fallback cases correct?

### Code quality

- Is complexity hidden behind appropriate boundaries?
- Are naming and domain concepts accurate?
- Did the implementation duplicate logic?
- Did public API surface grow unnecessarily?
- Are dependencies pointing in the right direction?
- Is the code testable and maintainable?

### Regression risk

- Which existing paths may have changed?
- Are compatibility assumptions valid?
- Are migrations safe?
- Do tests protect the most likely regressions?

### Architecture observer review

Perform a read-only architecture review of the ticket diff.

Ask:

1. Did this ticket create new architectural debt?
2. Did it expose an existing structural problem?
3. Is correction required before ticket completion?
4. Can the issue safely become a follow-up ticket?
5. Did the implementation deepen or weaken module boundaries?

Do not allow the architecture observer to edit code independently.

Classify review findings:

```text
BLOCKING
IMPORTANT_FOLLOW_UP
OPTIONAL
FALSE_POSITIVE
```

Resolve all `BLOCKING` findings before completion.

For each rejected review finding, record why it is not applicable.

## 8.8 Determine ticket outcome

### DONE

Use only when:

- all acceptance criteria are satisfied;
- all required verification passed;
- no material departure from the ticket occurred;
- review has no unresolved blockers.

### DONE_WITH_DEVIATIONS

Use when:

- the intended user or system outcome is achieved;
- one or more implementation details or planned approaches changed;
- deviations are explicitly documented;
- no unresolved blocker remains.

### FAILED_REVIEW

Use when:

- implementation exists;
- review found unresolved blocking problems.

Return to implementation.

### BLOCKED

Use when:

- an external or dependency condition prevents completion;
- no safe implementation path exists without resolving it.

### NEEDS_USER_DECISION

Use when:

- multiple valid product or architectural choices exist;
- choosing automatically would materially affect behavior or scope.

Never use `DONE_WITH_DEVIATIONS` to hide missing acceptance criteria.

If an acceptance criterion is intentionally dropped, either:

- update the specification with explicit user approval; or
- keep the ticket incomplete.

## 8.9 Commit discipline

When Git usage is appropriate and permitted:

1. Ensure the diff contains only the active ticket.
2. Run required checks before committing.
3. Create one focused commit for the ticket.
4. Use a message that references the ticket.

Example:

```text
feat(episodes): distinguish unknown availability [TICKET-03]
```

Do not amend or rewrite unrelated user commits.

Do not force-push.

If a merge conflict occurs, invoke:

```text
/resolving-merge-conflicts
```

Resolve by preserving both sides’ intent, not by blindly selecting ours or theirs.

## 8.10 Update state artifacts

Set:

```text
UPDATING_STATE
```

Update the ticket file and master plan immediately after completion.

Record:

```markdown
Implementation summary:

Deviations:

- Planned:
- Actual:
- Reason:
- Consequence:
- Follow-up:

Architecture notes:

Verification evidence:

- Command:
- Result:

Review findings:

Commit:

Files or modules affected:

New tickets created:

Known limitations:
```

Append an execution-log entry:

```markdown
## <timestamp> — TICKET-<NN>

### Outcome

DONE | DONE_WITH_DEVIATIONS | BLOCKED | NEEDS_USER_DECISION

### Work completed

### Decisions made

### Deviations

### Root causes discovered

### Verification

### Review result

### Architecture observations

### New risks

### Follow-up work

### Next eligible ticket
```

## 8.11 Create handoff

Invoke or apply:

```text
/handoff
```

Write the latest resumable state to:

```text
.scratch/<feature-slug>/CURRENT_HANDOFF.md
```

It must contain:

```markdown
# Current handoff

## Original goal

## Canonical artifacts

## Current workflow state

## Completed tickets

## Active ticket

## Next eligible ticket

## Decisions that must be preserved

## Deviations that affect later work

## Current repository state

## Relevant commits

## Verification already performed

## Known failures or blockers

## Files most relevant to the next ticket

## Exact recommended next action
```

Then select the next ready ticket.

Do not proceed when no ticket is ready and unresolved blockers exist.

# Architecture checkpoints

Run a broader architecture checkpoint:

- after a coherent group of foundational tickets;
- after a major migration boundary;
- when several ticket reviews report the same structural concern;
- before final review;
- when implementation evidence invalidates the initial architecture plan.

Save checkpoints under:

```text
.scratch/<feature-slug>/architecture/checkpoints/
```

A checkpoint may propose:

- modifying future tickets;
- adding a prefactoring ticket;
- merging or splitting tickets;
- cancelling an obsolete ticket;
- creating follow-up architecture work.

It must not retroactively rewrite history.

When a ticket changes:

```markdown
Original ticket:
Change:
Reason:
Impact on dependencies:
Impact on acceptance criteria:
User approval required: yes/no
```

Do not automatically add unrelated architecture cleanup to the active project.

# Plan mutation rules

The master plan is expected to evolve, but every mutation must be auditable.

## Splitting a ticket

Allowed when the ticket is too broad or contains independently verifiable outcomes.

Mark the original:

```text
SUPERSEDED
```

Link replacement tickets.

## Merging tickets

Allowed only before either ticket has meaningful implementation work.

Preserve original IDs in the execution log.

## Adding a ticket

Record:

- discovery source;
- why existing tickets do not cover it;
- whether it is required for the specification;
- dependencies;
- scope impact.

## Cancelling a ticket

Record:

- why it is no longer necessary;
- what replaced it;
- whether the specification changed.

## Changing acceptance criteria

Do not silently weaken criteria.

Material changes require:

- evidence;
- updated specification;
- user approval when product behavior changes.

# Final review

When all required tickets are complete, set:

```text
FINAL_REVIEW
```

Run a complete review against the canonical specification, not merely the ticket list.

Invoke:

```text
/code-review
```

Review the cumulative diff or feature branch.

Also perform a final architecture checkpoint.

## Requirement-by-requirement audit

For every specification requirement, record:

```text
PASS
PARTIAL
FAIL
NOT_VERIFIED
NOT_APPLICABLE
```

Provide evidence for `PASS`.

Explain gaps for `PARTIAL`, `FAIL`, and `NOT_VERIFIED`.

## Final verification

Run the broadest reasonable checks:

- full relevant test suite;
- build;
- static analysis;
- migration checks;
- integration tests;
- user-visible flow verification;
- compatibility checks;
- performance checks where required by the specification.

## Final scope audit

Confirm:

- no accidental unrelated refactor was included;
- no temporary debug code remains;
- no prototype code remains unintentionally;
- no acceptance criterion was silently dropped;
- deferred work is clearly separated from required work;
- documentation reflects stable decisions.

## Final status

Set `COMPLETED` only if:

- all mandatory specification requirements pass;
- all required tickets are complete;
- no blocking review findings remain;
- remaining limitations are explicitly accepted or out of scope;
- master plan and handoff are current.

Otherwise choose:

```text
BLOCKED
WAITING_FOR_USER_DECISION
READY_FOR_IMPLEMENTATION
```

as appropriate.

# Final report

When complete, provide the user with:

```markdown
# Completion report

## Result

## Tickets completed

## Major implementation decisions

## Deviations from the original plan

## Architecture impact

## Verification performed

## Known limitations

## Deferred follow-up work

## Relevant commits

## How to reproduce or verify
```

Do not bury limitations at the end.

# User communication

Keep the user informed at meaningful boundaries:

- after initial discovery;
- when the interview is complete;
- after specification and ticket planning;
- after each completed ticket;
- when a blocker or material deviation appears;
- before requesting a required product decision;
- after final verification.

A ticket-completion update should include:

```text
Completed:
Verification:
Deviation:
Architecture note:
Next:
```

Do not send low-level narration for every file read or command executed.

# Safety and repository protection

Never:

- discard unrelated working-tree changes;
- reset, clean, or overwrite user work without explicit permission;
- modify secrets or credentials;
- commit generated secrets;
- force-push;
- rewrite unrelated history;
- claim tests passed when they were not run;
- mark tickets done solely because code compiles;
- perform broad architecture refactoring without scope justification;
- let an architecture agent modify the same code concurrently;
- continue through an unresolved destructive migration decision;
- hide failures to preserve the appearance of progress.

When the working tree contains unrelated user changes:

1. Identify them.
2. Avoid modifying overlapping files where possible.
3. Preserve them.
4. Record the constraint.
5. Ask for intervention only when safe isolation is impossible.

# Failure recovery

If context is lost or the session restarts:

1. Find `MASTER_PLAN.md`.
2. Read `CURRENT_HANDOFF.md`.
3. Inspect Git status and recent commits.
4. Read the active or next ticket.
5. Compare recorded status to repository evidence.
6. Repair state documents if stale.
7. Resume from the earliest incomplete verification or implementation step.

Never restart the entire planning process when valid artifacts already exist.

# Quality heuristics

Prefer:

- narrow interfaces hiding substantial complexity;
- explicit domain states;
- vertical implementation slices;
- deterministic tests around business rules;
- small focused commits;
- reversible migrations;
- written decisions;
- observable failure modes;
- separate required work from optional cleanup.

Avoid:

- giant manager or coordinator classes;
- nullable or sentinel values representing several meanings;
- architecture changes justified only by aesthetics;
- tickets that touch every layer without a testable intermediate result;
- broad “clean up while here” work;
- parallel edits to the same modules;
- plans that cannot be resumed without chat history.

# Invocation map

Use this routing map.

## Standard large feature

```text
/grill-with-docs
→ /domain-modeling when needed
→ /to-spec
→ /improve-codebase-architecture in planning mode
→ /to-tickets
→ ticket loop
→ final /code-review
```

## Complex reproducible bug project

```text
/grill-with-docs
→ /diagnosing-bugs
→ /to-spec
→ /to-tickets
→ per-ticket /tdd + /implement
→ /code-review
```

## Highly uncertain project

```text
/wayfinder
→ /research and/or /prototype
→ /grill-with-docs
→ /to-spec
→ architecture review
→ /to-tickets
→ ticket loop
```

## Active ticket

```text
/implement <ticket>
→ /tdd when applicable
→ /diagnosing-bugs on unexpected failure
→ verification
→ /code-review
→ read-only architecture observer
→ update master plan
→ /handoff
```

## Merge conflict

```text
/resolving-merge-conflicts
→ rerun affected verification
→ repeat code review if the resolved diff materially changed
```

# First response behavior

On a fresh invocation:

1. Briefly acknowledge the task.
2. State that you will first inspect the relevant code and existing project guidance.
3. Begin codebase discovery.
4. Ask questions only after repository inspection.
5. Do not begin implementation before the specification and tickets exist, unless the user explicitly overrides the workflow.

On resume:

1. State the recovered workflow state.
2. State the last verified completed ticket.
3. State the next action.
4. Reconcile repository state.
5. Continue without repeating the original interview.

# Definition of success

This skill succeeds when the final repository state is not only implemented, but also:

- traceable to an agreed specification;
- decomposed into auditable tickets;
- verified ticket by ticket;
- reviewed for correctness and maintainability;
- explicit about deviations and limitations;
- safe to resume in another session;
- understandable without relying on hidden chat context.
