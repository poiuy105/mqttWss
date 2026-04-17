# Android 10 Car Display Adaptation - Product Requirement Document

## Overview
- **Summary**: Adapt the MQTT app to run on Android 10 (API 29) car displays with 1080x1830 resolution (240dpi), fixing TTS initialization failure and crash issues when enabling auto capture voice.
- **Purpose**: Ensure the app works correctly on Android 10 car displays, resolving critical bugs that prevent basic functionality.
- **Target Users**: Users with Android 10 car displays who need MQTT functionality.

## Goals
- Fix TTS initialization failure (error code -1) on Android 10
- Fix crash when enabling auto capture voice on Android 10
- Ensure app compatibility with 1080x1830 resolution (240dpi) displays
- Maintain existing functionality while addressing Android 10-specific issues

## Non-Goals (Out of Scope)
- Redesigning the entire UI for car displays
- Adding new features specific to car environments
- Supporting other Android versions beyond API 29
- Optimizing for other screen resolutions or densities

## Background & Context
- The app currently fails to initialize TTS on Android 10 with error code -1
- The app crashes when auto capture voice is enabled on Android 10
- The car display has a resolution of 1080x1830 with 240dpi density
- Android 10 introduced changes to permissions and background processing that may be causing these issues

## Functional Requirements
- **FR-1**: Fix TTS initialization failure on Android 10 (API 29)
- **FR-2**: Fix crash when enabling auto capture voice on Android 10 (API 29)
- **FR-3**: Ensure app displays correctly on 1080x1830 resolution (240dpi) displays
- **FR-4**: Maintain all existing app functionality after fixes

## Non-Functional Requirements
- **NFR-1**: App should launch and run without crashes on Android 10
- **NFR-2**: TTS functionality should work correctly on Android 10
- **NFR-3**: Auto capture voice feature should work without crashing on Android 10
- **NFR-4**: UI should be properly scaled for 1080x1830 resolution (240dpi)

## Constraints
- **Technical**: Must target Android 10 (API 29) while maintaining compatibility with existing versions
- **Dependencies**: May require updates to TTS initialization logic and permission handling

## Assumptions
- The TTS initialization failure is related to Android 10 permission changes or API differences
- The crash when enabling auto capture voice is related to accessibility service permissions or background processing restrictions in Android 10
- The display resolution and density do not require UI changes beyond ensuring proper scaling

## Acceptance Criteria

### AC-1: TTS Initialization Fix
- **Given**: The app is installed on an Android 10 device
- **When**: The app is launched
- **Then**: TTS initializes successfully without error code -1
- **Verification**: `programmatic`

### AC-2: Auto Capture Voice Fix
- **Given**: The app is running on an Android 10 device
- **When**: Auto capture voice is enabled
- **Then**: The app does not crash and auto capture voice works
- **Verification**: `programmatic`

### AC-3: Display Compatibility
- **Given**: The app is running on a 1080x1830 (240dpi) display
- **When**: The app is used normally
- **Then**: The UI is properly scaled and all elements are visible
- **Verification**: `human-judgment`

### AC-4: Existing Functionality
- **Given**: The app is running on Android 10
- **When**: All existing features are used
- **Then**: All features work as expected
- **Verification**: `human-judgment`

## Open Questions
- [ ] What specific Android 10 changes are causing the TTS initialization failure?
- [ ] What specific Android 10 changes are causing the auto capture voice crash?
- [ ] Are there any other Android 10-specific issues that need to be addressed?