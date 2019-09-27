gocd-vault-secrets
==================

<img height="80" width="80" align="right" src="resources/amperity/gocd/secret/vault/logo.svg"/>

[![CircleCI](https://circleci.com/gh/amperity/gocd-vault-secrets.svg?style=shield&circle-token=b9256c6d46160ab045b44cdfe5add3954dd0cbf2)](https://circleci.com/gh/amperity/gocd-vault-secrets)
[![codecov](https://codecov.io/gh/amperity/gocd-vault-secrets/branch/master/graph/badge.svg)](https://codecov.io/gh/amperity/gocd-vault-secrets)

A plugin for [GoCD](https://www.gocd.org/) providing secret material support via
[HashiCorp Vault](https://www.vaultproject.io/).

<br/>


## Installation

Releases are published on the [GitHub project](https://github.com/amperity/gocd-vault-secrets/releases).
Download the latest version of the plugin and
[place it in your server's external plugin directory](https://docs.gocd.org/current/extension_points/plugin_user_guide.html).
Once installed, restart the GoCD server to load the plugin. The plugin should
appear in your server's `Admin - Plugins` page when it is back up.


## Configuration

**TODO:** documentation


## Local Development

If you're planning to work on the plugin code locally, see the [`gocd`](gocd)
directory for running a local GoCD server.

**TODO:** more documentation


## License

Licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file
for rights and restrictions.
