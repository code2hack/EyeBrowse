# ADR-0001 — Kotlin-only first-party codebase

- **Status:** Accepted
- **Scope:** Entire EyeBrowse repository and all future versions
- **Owner decision:** Project Owner, 2026-09-18

## Context

EyeBrowse is an Android-first project with Phone and Rokid Glasses applications plus shared/core logic. Early v0.0.1 implementation work began in Java because no language constraint had been recorded. The Project Owner subsequently clarified that Kotlin is not merely a v0.0.1 preference: it is the language constraint for the whole project.

Without a repository-wide rule, future tickets or versions could reintroduce Java and create a mixed-language codebase, increasing migration cost, review surface, interop friction, and architectural inconsistency.

## Decision

EyeBrowse is a **Kotlin-only first-party codebase**.

1. All first-party Android application code, shared/core code, unit tests, instrumentation tests, and maintained JVM/Android project tooling are written in Kotlin.
2. Gradle configuration uses Kotlin DSL (`.gradle.kts`).
3. No new first-party Java source may be introduced anywhere in the repository.
4. Existing first-party Java is migration debt, not a permanent exception. When an area is actively changed, its Java implementation must be migrated rather than expanded. Active production work must not be accepted while it adds new Java or preserves avoidable Java in the touched implementation.
5. The standing exceptions are only generated code and vendored/third-party source that EyeBrowse does not maintain.
6. Any other temporary exception requires an explicit Project Owner decision recorded by superseding or amending this ADR.

## Consequences

- Current Java product code from early v0.0.1 work must migrate to Kotlin.
- The active hosting implementation must be Kotlin before acceptance.
- Future tickets and versions start in Kotlin by default; planners, workers, reviewers, and tooling should treat Java first-party source as a violation unless covered by an explicit Owner exception.
- Migration should preserve behavior and evidence rather than becoming an unrelated redesign.
- Generated/vendor Java may remain when EyeBrowse does not maintain it.

## Supersedes

This ADR supersedes any earlier ticket-plan or implementation-language choice that treated Java as an acceptable EyeBrowse first-party source language.
