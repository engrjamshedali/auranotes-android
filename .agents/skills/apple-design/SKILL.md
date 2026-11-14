---
name: apple-design
description: Apple's approach to interface design and fluid, physical motion, translated for the web and native interfaces. Use when building or reviewing gesture-driven UI, spring animations, drag/swipe/sheet interactions, momentum and interruptible transitions, translucent materials and depth, typography (optical sizing, tracking, leading), reduced-motion, or the design foundations (feedback, spatial consistency, restraint) behind Apple-style interfaces.
---

# Apple Design

How Apple builds interfaces that stop feeling like a computer and start feeling like an extension of you. Distilled from Apple's WWDC design talks — chiefly *Designing Fluid Interfaces* (WWDC 2018), *The Details of UI Typography* (WWDC 2020), and *Principles of Great Design* (WWDC 2026).

## Quick Reference

| Need | Technique | Concrete value |
| --- | --- | --- |
| Default UI spring | Critically damped, no overshoot | `damping 1.0`, `response 0.3–0.4` |
| Momentum / flick spring | Under-damped, slight bounce | `damping ~0.8`, `response 0.3–0.4` |
| Gesture → spring velocity | Hand off release velocity | `gestureVelocity / (target − current)` |
| Flick landing point | Project momentum | `current + (v/1000)·d/(1−d)`, `d ≈ 0.998` |
| Interrupt cleanly | Start from presentation (live) value | read the on-screen transform |
| 1:1 drag | Pointer Events + capture | respect the grab offset |
| Feedback | On pointer-down, continuous | never only at the end |
| Boundary | Rubber-band, don't hard-stop | progressive resistance `c = 0.55` |
| Translucent chrome | Translucent material + specular edge | content scrolls under with edge mask |
| Type tracking | Size-specific, never fixed | tighten large text (`-0.02em`), body `0`, small `+0.02em` |
| Reduced motion | Cross-fade, not slide/spring | `prefers-reduced-motion` |
