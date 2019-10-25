gocd-vault-secrets
==================

<img height="80" width="80" align="right" src="resources/amperity/gocd/secret/vault/logo.svg"/>

[![CircleCI](https://circleci.com/gh/amperity/gocd-vault-secrets.svg?style=shield&circle-token=b9256c6d46160ab045b44cdfe5add3954dd0cbf2)](https://circleci.com/gh/amperity/gocd-vault-secrets)
[![codecov](https://codecov.io/gh/amperity/gocd-vault-secrets/branch/master/graph/badge.svg)](https://codecov.io/gh/amperity/gocd-vault-secrets)

A plugin for [GoCD](https://www.gocd.org/) providing secret material support via
[HashiCorp Vault](https://www.vaultproject.io/).

This plugin has not yet reached `v1`, but feel free to download a `v0` release (see the installation guide below).


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

### Setting up a local Vault server

1. First, spin up a dev vault server.
```bash
vault server -dev
```

2. In a new terminal tab/window (so you can keep viewing the Vault logs), set up a `generic` secret store.
Right now, `vault-clj` supports the `generic` secrets catalog, but not the newer `kv` catalog. Until that's updated,
this plugin will only support `generic` secrets stores as well.
```bash
export VAULT_ADDR='http://127.0.0.1:8200'
vault secrets enable --path=<PATH> generic
```

**Important Note**: At this point I will assume all Vault CLI commands are run in a session that has the `VAULT_ADDR` environment variable set to your local Vault instance.

3. Write some testing data to the path from part 2.
```bash
vault write <PATH> <KEY>=<VALUE>
```

4. Ensure you can read that data.
```bash
vault read <PATH>
```

### Configuring GoCD

If you have not completed all the above steps, go back and finish those before configuring GoCD.

1. First, ensure the plugin loaded successfully by checking http://localhost:8153/go/admin/plugins.

2. Then, add a new secret config at http://localhost:8153/go/admin/secret_configs.
    - You will probably be running GoCD locally in a docker container and a Vault docker container. If this is the case, make sure that you set the
    Vault URL configuration option in GoCD to `http://host.docker.internal:<PORT>` where `<PORT>` is the port exposing your running Vault dev server.
    You can find it at the top of your `vault -dev` server logs.
	- You will probably be using the *token* authentication method for local dev.
	If this is the case, copy the root `Vault Token` from your running Vault dev server. You can find it at the top of your dev server logs or by copying the id found from running `vault token lookup`.
	**Note**: Using *token* as your authentication method should not be done in production. While the GoCD plugin does store a hashed version of this token, it is still not recommended for secure systems.


## License

Licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file
for rights and restrictions.
