# Home Assistant Integration - Implementation Plan

## [x] Task 1: Add Home Assistant configuration container to Setting page layout
- **Priority**: P0
- **Depends On**: None
- **Description**:
  - Add Home Assistant container to fragment_connection.xml
  - Include HA address, long-lived token, language input fields, and HTTPS checkbox
  - Position between Protocol and Features containers
- **Acceptance Criteria Addressed**: AC-1, AC-2
- **Test Requirements**:
  - `human-judgment` TR-1.1: Home Assistant container is visible in correct position
  - `human-judgment` TR-1.2: All input fields and checkbox are present with correct defaults
- **Notes**: Use LinearLayout with appropriate styling to match existing UI

## [x] Task 2: Update SettingFragment to handle Home Assistant configuration
- **Priority**: P0
- **Depends On**: Task 1
- **Description**:
  - Add references to Home Assistant UI elements
  - Update saveCurrentConfig() to save Home Assistant settings
  - Update loadSavedConfig() to load Home Assistant settings
- **Acceptance Criteria Addressed**: AC-2, AC-5
- **Test Requirements**:
  - `programmatic` TR-2.1: Home Assistant settings are saved to SharedPreferences
  - `programmatic` TR-2.2: Home Assistant settings are loaded from SharedPreferences
- **Notes**: Add new keys to ConfigManager for Home Assistant settings

## [x] Task 3: Update ConfigManager to store Home Assistant settings
- **Priority**: P0
- **Depends On**: Task 2
- **Description**:
  - Add Home Assistant related properties to ConfigManager
  - Update saveConnectionConfig() and loadConnectionConfig() to include Home Assistant settings
- **Acceptance Criteria Addressed**: AC-5
- **Test Requirements**:
  - `programmatic` TR-3.1: Home Assistant settings are properly stored and retrieved
- **Notes**: Use default values as specified in requirements

## [x] Task 4: Modify Voice page layout
- **Priority**: P0
- **Depends On**: None
- **Description**:
  - Reduce height of Only Capture Frame and Tap App Name containers
  - Add "Send to Home Assistant" checkbox under prefix container
- **Acceptance Criteria Addressed**: AC-3
- **Test Requirements**:
  - `human-judgment` TR-4.1: Checkbox is present in correct position
  - `human-judgment` TR-4.2: Container sizes are appropriately adjusted
- **Notes**: Use existing checkbox styling to match UI

## [x] Task 5: Update ConnectionFragment to handle Home Assistant checkbox
- **Priority**: P0
- **Depends On**: Task 4
- **Description**:
  - Add reference to "Send to Home Assistant" checkbox
  - Update saveCurrentConfig() and loadSavedConfig() to handle checkbox state
- **Acceptance Criteria Addressed**: AC-3, AC-5
- **Test Requirements**:
  - `programmatic` TR-5.1: Checkbox state is saved and loaded
- **Notes**: Add new property to ConfigManager for this setting

## [x] Task 6: Implement Home Assistant communication service
- **Priority**: P0
- **Depends On**: Task 3
- **Description**:
  - Create HomeAssistantService class
  - Implement method to send text commands to Home Assistant
  - Implement response parsing
- **Acceptance Criteria Addressed**: AC-4
- **Test Requirements**:
  - `programmatic` TR-6.1: HTTP requests are sent correctly
  - `programmatic` TR-6.2: Responses are parsed correctly
- **Notes**: Use OkHttp for HTTP requests

## [x] Task 7: Integrate Home Assistant service with voice capture
- **Priority**: P0
- **Depends On**: Task 6, Task 5
- **Description**:
  - Update CapturedTextManager to send text to Home Assistant when enabled
  - Handle response by displaying popup and TTS
  - Implement debounce mechanism to prevent duplicate commands
- **Acceptance Criteria Addressed**: AC-4, AC-6
- **Test Requirements**:
  - `programmatic` TR-7.1: Voice commands are sent to Home Assistant
  - `programmatic` TR-7.2: Responses are displayed and broadcast
  - `programmatic` TR-7.3: Debounce mechanism works correctly
- **Notes**: Use existing TTS and popup functionality; implement 1-second debounce window

## [ ] Task 8: Test the implementation
- **Priority**: P1
- **Depends On**: Tasks 1-7
- **Description**:
  - Test Home Assistant configuration UI
  - Test voice-to-Home Assistant integration
  - Test error handling
- **Acceptance Criteria Addressed**: AC-1, AC-2, AC-3, AC-4, AC-5
- **Test Requirements**:
  - `human-judgment` TR-8.1: UI elements are properly displayed
  - `programmatic` TR-8.2: Integration works end-to-end
  - `programmatic` TR-8.3: Error handling works correctly
- **Notes**: Test with a real Home Assistant instance if possible