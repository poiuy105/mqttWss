# Home Assistant Integration - Product Requirement Document

## Overview

* **Summary**: Add Home Assistant integration features to the MQTT Assistant app, including UI elements for Home Assistant configuration and voice-to-Home Assistant communication functionality.

* **Purpose**: Enable users to send voice commands to Home Assistant through the MQTT Assistant app, leveraging the existing voice capture functionality.

* **Target Users**: Users who have Home Assistant set up and want to control it through voice commands via the MQTT Assistant app.

## Goals

* Add Home Assistant configuration container to the Setting page

* Add "Send to Home Assistant" checkbox to the Voice page

* Implement voice-to-Home Assistant integration functionality

* Display and TTS broadcast Home Assistant responses

## Non-Goals (Out of Scope)

* Modifying existing MQTT functionality

* Adding Home Assistant discovery or auto-configuration

* Implementing two-way communication beyond the initial command and response

## Background & Context

* The app already has voice capture functionality

* Home Assistant provides a conversation API that accepts text commands and returns responses

* The integration will use curl-style HTTP POST requests to communicate with Home Assistant

## Functional Requirements

* **FR-1**: Add Home Assistant configuration container to Setting page with HA address, long-lived token, language input fields, and HTTPS checkbox

* **FR-2**: Add "Send to Home Assistant" checkbox to Voice page

* **FR-3**: Implement logic to send captured text to Home Assistant when the checkbox is enabled

* **FR-4**: Parse Home Assistant response and display/broadcast the speech response

* **FR-5**: Persist Home Assistant configuration settings

* **FR-6**: Implement debounce mechanism to prevent duplicate voice commands

## Non-Functional Requirements

* **NFR-1**: The Home Assistant container should be aesthetically integrated into the existing UI

* **NFR-2**: The voice-to-Home Assistant communication should be responsive

* **NFR-3**: Error handling for Home Assistant communication failures

* **NFR-4**: Debounce mechanism to eliminate jitter and duplicate commands

## Constraints

* **Technical**: Must use HTTP/HTTPS requests to communicate with Home Assistant

* **Dependencies**: Home Assistant instance with conversation API enabled

## Assumptions

* Users have a Home Assistant instance set up

* Users have generated a long-lived access token for Home Assistant

* Home Assistant API is accessible from the device running the app

## Acceptance Criteria

### AC-1: Home Assistant Configuration UI

* **Given**: User is on the Setting page

* **When**: User views the page

* **Then**: Home Assistant configuration container is visible between Protocol and Features containers

* **Verification**: `human-judgment`

### AC-2: Home Assistant Configuration Fields

* **Given**: User is on the Setting page

* **When**: User views the Home Assistant container

* **Then**: HA address field is present with default "homeassistant.local", long-lived token field with default "abcdefg", language field with default "zh", and HTTPS checkbox with default checked

* **Verification**: `human-judgment`

### AC-3: Send to Home Assistant Checkbox

* **Given**: User is on the Voice page

* **When**: User views the page

* **Then**: "Send to Home Assistant" checkbox is present under the prefix container

* **Verification**: `human-judgment`

### AC-4: Voice Command to Home Assistant

* **Given**: User has configured Home Assistant settings and enabled "Send to Home Assistant"

* **When**: User speaks a command that is captured

* **Then**: App sends the command to Home Assistant and displays/broadcasts the response

* **Verification**: `programmatic`

### AC-5: Configuration Persistence

* **Given**: User has configured Home Assistant settings

* **When**: User restarts the app

* **Then**: Home Assistant settings are preserved

* **Verification**: `programmatic`

### AC-6: Debounce Mechanism

* **Given**: User has enabled "Send to Home Assistant"

* **When**: User speaks a command that is captured

* **Then**: App sends the command to Home Assistant, clicks the back button, and ignores any voice commands within the next 1 second

* **Verification**: `programmatic`

## Open Questions

* [ ] How to handle Home Assistant API errors?

* [ ] What timeout should be used for Home Assistant API requests?

