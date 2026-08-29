# Real-device QA checklist

Record the device, OS version, build identifier, environment, result, and redacted evidence for every scenario.

1. **Fresh launch** opens the trusted production origin without a security warning.
2. **Existing session** remains authenticated through ordinary app restart.
3. **Logout** clears server authority without broad destructive cache recovery.
4. **Offline launch** shows branded recovery and succeeds after connectivity returns.
5. **TLS failure** is blocked without a bypass option.
6. **External HTTPS link** leaves the trusted bridge-enabled container.
7. **Unknown scheme** is denied without executing application content.
8. **Verified deep link** reaches the intended public route after cold start.
9. **Protected deep link** waits for login and remains server-authorized.
10. **Push permission denial** keeps core web functionality usable.
11. **Push token refresh** updates the installation without exposing the token in logs.
12. **Push tap** fetches authoritative protected data after routing.
13. **Photo selection** supports cancellation and selected-library access.
14. **Camera capture** returns a temporary file and cleans it after completion.
15. **Large media** fails safely and permits a user-directed retry.
16. **Location confirmation** appears only after explicit user action.
17. **Location denial** preserves manual address entry.
18. **Native share** shares only a validated first-party HTTPS URL.
19. **External handoff** receives no native bridge capability.
20. **Return routing** resumes only a matching, unexpired opaque context.
21. **Process kill during handoff** restores safely without replaying checkout.
22. **WebView process termination** recovers without weakening origin policy.
23. **Background restriction** is exercised on a representative Android OEM device.
24. **Old shell with new web** preserves fallback behavior and contract compatibility.
25. **Accessibility pass** covers navigation, focus, text sizing, and recovery screens.
