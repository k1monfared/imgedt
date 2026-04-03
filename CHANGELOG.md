# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-04-01

### Added
- OpenGL ES 2.0 filter pipeline with 10 real-time adjustments: exposure, contrast, saturation, warmth, highlights, shadows, sharpen, fade, grain, vignette
- GLSL shaders ported from Telegram's photo editor (GPL v2)
- Crop and rotate with 6 aspect ratio presets (Free, 1:1, 4:3, 3:4, 16:9, 9:16)
- Free rotation from -90 to +90 degrees with rotation wheel
- 90-degree rotation button and horizontal mirror
- Rotation mode toggle (keep in frame vs. allow black areas)
- Freehand drawing with 8 color options and adjustable brush size
- Brush opacity slider
- Eraser tool
- Undo support (up to 20 steps)
- Photo picker with Android 13+ permission handling
- Save edited photos to gallery via MediaStore
- Two-module architecture: app shell + editor library
- GPL v2 license

### Fixed
- Saved photos were black (GL rendering ran on wrong thread)
- Eraser had no effect (was clearing an already-transparent layer)
- Tool highlight was off by 2 positions
- Crop reset did not restore orientation or mirror state

[Unreleased]: https://github.com/k1monfared/mobile_photo_editor/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/k1monfared/mobile_photo_editor/releases/tag/v0.1.0
