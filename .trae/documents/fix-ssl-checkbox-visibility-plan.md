# Fix SSL Untrusted Checkbox Visibility Issue

## Problem Analysis

When the app loads a saved configuration with SSL protocol selected, the "Allow untrusted certificates" checkbox does not appear because:

1. `loadSavedConfig()` is called on line 141 of ConnectionFragment.kt
2. `mProtocol.setOnCheckedChangeListener` is set up on line 183
3. When `loadSavedConfig()` calls `mProtocol.check(R.id.protocol_ssl)`, the listener hasn't been registered yet
4. Therefore, the checkbox visibility update code (lines 199-203) is never executed

## Solution

Move the `mProtocol.setOnCheckedChangeListener` setup to BEFORE `loadSavedConfig()` is called, so that when the saved protocol is loaded and set, the listener will trigger and update the checkbox visibility correctly.

## Implementation Steps

1. **Move protocol listener setup before loadSavedConfig()**
   - Find the current location of `mProtocol.setOnCheckedChangeListener` (around line 183)
   - Move it to before `loadSavedConfig()` call (before line 141)

2. **Verify the fix**
   - Ensure the checkbox now appears when SSL is selected
   - Test that the checkbox is hidden when switching to other protocols

## Files to Modify

- `app/src/main/java/io/emqx/mqtt/ConnectionFragment.kt`

## Expected Result

After the fix:
- When user selects SSL protocol, the "Allow untrusted certificates" checkbox will appear
- When user switches away from SSL, the checkbox will be hidden
- The checkbox state will be saved and loaded correctly