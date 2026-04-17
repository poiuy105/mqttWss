# SSL Untrusted Certificates Support - Product Requirement Document

## Overview
- **Summary**: Add an "Allow untrusted certificates" checkbox to the MQTT connection interface, enabling users to connect to Mosquitto brokers with self-signed or untrusted SSL certificates.
- **Purpose**: Address the current limitation where the app can only connect to SSL brokers with trusted certificates, by providing an option to bypass certificate validation for testing and development environments.
- **Target Users**: Developers, testers, and users working with Mosquitto brokers using self-signed certificates in non-production environments.

## Goals
- Add an "Allow untrusted certificates" checkbox to the connection fragment
- Implement logic to use insecure socket factory when the checkbox is enabled for SSL connections
- Ensure the setting is saved and loaded properly
- Maintain backward compatibility with existing SSL connections

## Non-Goals (Out of Scope)
- Modifying the SSL certificate validation logic for trusted connections
- Adding support for custom certificate authorities
- Changing the existing certificate validation behavior for WSS connections
- Changing any functionality for TCP or WS connections

## Background & Context
- The app currently uses a fixed certificate for SSL connections, which limits users to only connecting to brokers with trusted certificates
- Many developers and testers use self-signed certificates for Mosquitto brokers in development environments
- The SSLUtils class already has a getInsecureSocketFactory() method that can be used for this purpose
- TCP, WS, and WSS connections are already working correctly

## Functional Requirements
- **FR-1**: Add "Allow untrusted certificates" checkbox to the connection fragment
- **FR-2**: Implement logic to use getInsecureSocketFactory() when the checkbox is enabled for SSL connections
- **FR-3**: Save and load the checkbox state from configuration
- **FR-4**: Ensure the checkbox is only visible when SSL protocol is selected

## Non-Functional Requirements
- **NFR-1**: The checkbox should be clearly labeled and positioned near the SSL protocol options
- **NFR-2**: The setting should be persistent across app restarts
- **NFR-3**: The change should not affect existing SSL connections that use trusted certificates
- **NFR-4**: The change should not affect TCP, WS, or WSS connections

## Constraints
- **Technical**: Must use the existing SSLUtils.getInsecureSocketFactory() method
- **Dependencies**: None

## Assumptions
- Users understand the security implications of allowing untrusted certificates
- The getInsecureSocketFactory() method works correctly for bypassing certificate validation
- TCP, WS, and WSS connections are already working correctly

## Acceptance Criteria

### AC-1: Checkbox Display
- **Given**: The user is on the connection fragment
- **When**: The user selects SSL protocol
- **Then**: The "Allow untrusted certificates" checkbox becomes visible
- **Verification**: `human-judgment`

### AC-2: Checkbox Functionality
- **Given**: The user has selected SSL protocol
- **When**: The user checks the "Allow untrusted certificates" checkbox and connects
- **Then**: The app uses the insecure socket factory for the SSL connection
- **Verification**: `programmatic`

### AC-3: Setting Persistence
- **Given**: The user has enabled "Allow untrusted certificates"
- **When**: The user restarts the app
- **Then**: The checkbox remains checked
- **Verification**: `programmatic`

### AC-4: Backward Compatibility
- **Given**: The user has not checked the "Allow untrusted certificates" checkbox
- **When**: The user connects to an SSL broker
- **Then**: The app uses the existing trusted certificate validation
- **Verification**: `programmatic`

### AC-5: Other Protocols Unaffected
- **Given**: The user is using TCP, WS, or WSS protocol
- **When**: The user interacts with the app
- **Then**: The app behavior for these protocols remains unchanged
- **Verification**: `programmatic`

## Open Questions
- [ ] What should be the default state of the checkbox?