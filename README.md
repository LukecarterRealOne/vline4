# VLine Practice Tools (Fabric, Minecraft 26.1.2)

A visible practice helper intended for **offline single-player worlds only**. It is not a stealth tool and contains no anti-cheat bypass logic.

## Features

- **F6 — Toggle hitbox display:** uses Minecraft's own entity hitbox debug renderer. It is visible and affects the vanilla hitbox debug display.
- **F7 — Toggle proximity alerts:** shows an action-bar notice when your aim ray passes through a mob's slightly expanded bounding box, with its name and approximate distance.
- **F8 — Toggle training auto-hit:** attacks only `Mob` entities, only when vanilla's own crosshair result selects that same mob, only after vanilla attack cooldown, and only in single-player. It never targets player entities, adds no reach, and does not attack through walls.

The helper intentionally disables its features outside single-player. Auto-hit is off by default. Use Options → Controls to rebind the keys.

## Build on GitHub Actions

1. Create/open a repository and upload the **contents** of this project ZIP (including `.github/workflows/build.yml`) into the repository root. Alternatively, put the project ZIP itself in the root; the workflow can unpack it.
2. Commit/push the files.
3. Open the **Actions** tab and run **Build VLine Practice Tools**.
4. Download the `vline-practice-tools-minecraft-26.1.2` artifact from a successful run. Put the main `vline-practice-tools-1.0.0.jar` (not `-sources.jar`) in your Minecraft `mods` folder.

## Requirements

- Minecraft Java Edition 26.1.2
- Fabric Loader 0.19.5 or newer
- Fabric API 0.155.3+26.1.2
- Java 25

## Important notes

This repository is a source project, not a precompiled mod JAR. The build needs to run on GitHub Actions or another machine with Java 25 and network access. I have not run a full build or in-game test from this environment, so wait for the workflow to pass before installing it. This mod is intentionally not designed to hide features from server monitoring or to automate attacks against other players.
