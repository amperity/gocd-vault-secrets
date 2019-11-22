Change Log
==========

All notable changes to this project will be documented in this file. This change
log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]
...

## [0.3.1] - 2019-11-22
- Added an option to ignore TTLs specified in Vault and force-read secrets
  [#26](https://github.com/amperity/gocd-vault-secrets/issues/26)

## [0.3.0] - 2019-11-20
- Added Token Lookup option using `TOKENS:<POLICY>,<POLICY>,...`
  [#23](https://github.com/amperity/gocd-vault-secrets/issues/23)

## [0.2.0] - 2019-10-29
- Added AWS Auth method
  [#12](https://github.com/amperity/gocd-vault-secrets/pull/12)
- Fixed bug where secret lookup would fail after server restart
  [#9](https://github.com/amperity/gocd-vault-secrets/issues/9)

## [0.1.0] - 2019-10-22
- Initial plugin release
  [#0](https://media.giphy.com/media/o0eOCNkn7cSD6/giphy.gif)
- Supports one Auth Method, direct Vault tokens
  [#4](https://github.com/amperity/gocd-vault-secrets/pull/4)

[Unreleased]: https://github.com/amperity/gocd-vault-secrets/compare/v0.3.1...HEAD
[0.3.1]: https://github.com/amperity/gocd-vault-secrets/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/amperity/gocd-vault-secrets/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/amperity/gocd-vault-secrets/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/amperity/gocd-vault-secrets/releases/tag/v0.1.0
