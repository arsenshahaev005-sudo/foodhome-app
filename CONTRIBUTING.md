# Contributing

This is a source-visible product repository. External contributions may be reviewed, but acceptance and response times are not guaranteed.

Pull requests must:

- preserve the remote-first architecture described in [docs/architecture/overview.md](docs/architecture/overview.md);
- keep product UI and business logic out of the native shells;
- preserve exact-origin, main-frame, and fail-closed bridge validation;
- include focused automated tests or a precise verification path;
- pass the unsigned GitHub Actions checks;
- contain no credentials, private user data, signing material, signed artifacts, or production configuration secrets.

Keep changes small and scoped. Do not weaken security checks or introduce a new cross-platform runtime without a separately reviewed architectural decision.

For security reports, follow [SECURITY.md](SECURITY.md) instead of opening a public issue.
