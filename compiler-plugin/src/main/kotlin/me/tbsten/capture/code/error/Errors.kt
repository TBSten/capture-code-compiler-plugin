package me.tbsten.capture.code.error

/**
 * SSoT for **plugin-wide cross-cutting errors** that are not bound to a single
 * feature/logic. Feature-local errors live next to the feature
 * (see `feature/<feature>/.../[Feature]Errors.kt`).
 *
 * task-121: introduced as an empty placeholder. The restructure goals
 * (`.local/restructure-compiler-plugin/goals.md` §3.1) keep the cross-cutting
 * error catalogue empty for the 0.2.0 release — every error currently in flight
 * is feature-local.
 */
public object Errors
