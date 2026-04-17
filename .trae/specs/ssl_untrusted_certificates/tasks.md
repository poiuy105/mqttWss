# SSL Untrusted Certificates Support - Implementation Plan

## [x] Task 1: Add "Allow untrusted certificates" checkbox to connection fragment layout
- **Priority**: P0
- **Depends On**: None
- **Description**:
  - Add a checkbox to fragment_connection.xml
  - Position it near the protocol selection
  - Ensure it's only visible when SSL is selected
- **Acceptance Criteria Addressed**: AC-1
- **Test Requirements**:
  - `human-judgment` TR-1.1: Checkbox is visible when SSL is selected
  - `human-judgment` TR-1.2: Checkbox is hidden when TCP/WS/WSS is selected
- **Notes**: Use a LinearLayout with TextView and CheckBox

## [x] Task 2: Update ConnectionFragment.kt to handle the checkbox
- **Priority**: P0
- **Depends On**: Task 1
- **Description**:
  - Add a reference to the checkbox in ConnectionFragment
  - Update protocol selection listener to show/hide the checkbox
  - Add the checkbox state to the saveCurrentConfig() method
  - Add the checkbox state to the loadSavedConfig() method
- **Acceptance Criteria Addressed**: AC-1, AC-3
- **Test Requirements**:
  - `programmatic` TR-2.1: Checkbox visibility toggles based on protocol
  - `programmatic` TR-2.2: Checkbox state is saved and loaded
- **Notes**: Use View.VISIBLE and View.GONE for visibility

## [x] Task 3: Update Connection.kt to use insecure socket factory
- **Priority**: P0
- **Depends On**: Task 2
- **Description**:
  - Add a parameter to the Connection constructor for allowUntrusted
  - Update the mqttConnectOptions getter to use getInsecureSocketFactory() when allowUntrusted is true and protocol is SSL
  - Update ConnectionFragment to pass the checkbox state
- **Acceptance Criteria Addressed**: AC-2, AC-4, AC-5
- **Test Requirements**:
  - `programmatic` TR-3.1: Insecure socket factory is used when checkbox is checked for SSL
  - `programmatic` TR-3.2: Regular socket factory is used when checkbox is unchecked for SSL
  - `programmatic` TR-3.3: Other protocols are unaffected
- **Notes**: Use SSLUtils.getInsecureSocketFactory() when allowUntrusted is true

## [x] Task 4: Update ConfigManager to store allowUntrusted setting
- **Priority**: P0
- **Depends On**: Task 2
- **Description**:
  - Add allowUntrusted property to ConfigManager
  - Update saveConnectionConfig() to include allowUntrusted
  - Update loadConnectionConfig() to include allowUntrusted
- **Acceptance Criteria Addressed**: AC-3
- **Test Requirements**:
  - `programmatic` TR-4.1: allowUntrusted setting is saved to SharedPreferences
  - `programmatic` TR-4.2: allowUntrusted setting is loaded from SharedPreferences
- **Notes**: Use a boolean preference with default value false

## [/] Task 5: Test the implementation
- **Priority**: P1
- **Depends On**: Tasks 1, 2, 3, 4
- **Description**:
  - Test that the checkbox appears/disappears correctly
  - Test that the setting is saved and loaded
  - Test that SSL connections work with the checkbox both checked and unchecked
  - Test that other protocols (TCP, WS, WSS) continue to work
- **Acceptance Criteria Addressed**: AC-1, AC-2, AC-3, AC-4, AC-5
- **Test Requirements**:
  - `human-judgment` TR-5.1: UI behavior is correct
  - `programmatic` TR-5.2: SSL connections work with untrusted certificates
  - `programmatic` TR-5.3: Existing functionality is not broken
  - `programmatic` TR-5.4: Other protocols continue to work
- **Notes**: Test with both trusted and self-signed certificates