# SSL Untrusted Certificates Support - Verification Checklist

- [ ] "Allow untrusted certificates" checkbox is visible when SSL protocol is selected
- [ ] "Allow untrusted certificates" checkbox is hidden when TCP protocol is selected
- [ ] "Allow untrusted certificates" checkbox is hidden when WS protocol is selected
- [ ] "Allow untrusted certificates" checkbox is hidden when WSS protocol is selected
- [ ] Checkbox state is saved when app is closed and reopened
- [ ] SSL connection works with checkbox unchecked (trusted certificates)
- [ ] SSL connection works with checkbox checked (untrusted certificates)
- [ ] TCP connection continues to work as before
- [ ] WS connection continues to work as before
- [ ] WSS connection continues to work as before