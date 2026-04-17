# Fix SSL Untrusted Checkbox in Setting Fragment

## Problem Analysis

The "Allow untrusted certificates" checkbox is not appearing in the Setting fragment when SSL is selected because:

1. **Missing checkbox references**: SettingFragment doesn't have references to the checkbox and its container
2. **Incomplete protocol listener**: The protocol listener only handles port and path visibility, not the SSL checkbox
3. **Hardcoded allowUntrusted value**: saveConnectionConfig is called with allowUntrusted = false
4. **Missing parameter in Connection constructor**: Connection is created without the allowUntrusted parameter
5. **Listener setup order**: The protocol listener is set up after loadSavedConfig, so saved SSL protocol doesn't trigger checkbox visibility

## Solution

Add the missing checkbox references, update the protocol listener, and ensure the allowUntrusted setting is properly handled in SettingFragment.

## Implementation Steps

1. **Add checkbox references**
   - Add mAllowUntrustedCheckbox and mSslUntrustedContainer properties
   - Initialize them in setUpView()

2. **Update protocol listener**
   - Add SSL checkbox visibility handling to the protocol listener
   - Move the protocol listener before loadSavedConfig()

3. **Update configuration handling**
   - Update saveCurrentConfig() to save the checkbox state
   - Update loadSavedConfig() to load the checkbox state

4. **Update Connection constructor call**
   - Add allowUntrusted parameter to the Connection constructor call

## Files to Modify

- `app/src/main/java/io/emqx/mqtt/SettingFragment.kt`

## Expected Result

After the fix:
- When user selects SSL protocol in Setting fragment, the "Allow untrusted certificates" checkbox will appear
- When user switches away from SSL, the checkbox will be hidden
- The checkbox state will be saved and loaded correctly
- SSL connections will use the appropriate socket factory based on the checkbox state