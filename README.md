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

1. Install the Vault Plugin on GoCD as specified in the **Installation** section of this README.
2. Navigate to the Secret Management pane in GoCD.
3. Add a new secret configuration, and fill out the details as desired.
4. Set up the pipeline or environment you wish to use. You can specify secret params as described below.

### Secret Params
In GoCD, you can refer to secret params like this:
```
{{SECRET:[<SECRET CONFIG ID>][<SECRET KEY>]}}
```

If this does not look familiar to you, you may want to check out the [GoCD secret management docs](https://docs.gocd.org/current/configuration/secrets_management.html).

Here's how to set up a secret param using the Vault plugin (please note that these are whitespace and case sensitive):

##### Token Generation
```
{{SECRET:[<ID SPECIFIED IN PART 3>][TOKEN:]}}
```
If you want that token to inherit specific policies, you can specify them after `TOKEN:` and seperated by commas:
```
{{SECRET:[<ID SPECIFIED IN PART 3>][TOKEN:<POLICY1>,<POLICY2>,<POLICY3>]}}
```

##### KV API V1, Generic Secret Engine
```
{{SECRET:[<ID SPECIFIED IN PART 3>][<VAULT PATH>]}}
```
If you wish to access a specific key at that path, you can do so as follows:
```
{{SECRET:[<ID SPECIFIED IN PART 3>][<VAULT PATH>#<KEY>]}}
```
This works with arbitrary levels of nested maps as well:
```
{{SECRET:[<ID SPECIFIED IN PART 3>][<VAULT PATH>#<KEY1>#<KEY2>#<KEY3>]}}
```
where `<VAULT PATH>#<KEY1>` returns a mapping with `KEY2` in it and so on.

##### KV API V2, KV Secret Engine
```
{{SECRET:[<ID SPECIFIED IN PART 3>][<MOUNT>/data/<REST OF VAULT PATH>#data]}}
```
If you wish to access a specific key at that path, you can do so as follows:
```
{{SECRET:[<ID SPECIFIED IN PART 3>][<MOUNT>/data/<REST OF VAULT PATH>#data#<KEY>]}}
```
This works with arbitrary levels of nested maps as well:
```
{{SECRET:[<ID SPECIFIED IN PART 3>][<MOUNT>/data/<REST OF VAULT PATH>#data#<KEY1>#<KEY2>#<KEY3>]}}
```
where `<MOUNT>/data/<REST OF VAULT PATH>#data#<KEY1>` returns a mapping with `KEY2` in it and so on.

## FAQ

##### Why not just store secrets in the pipelines directly?
Operational Complexity caused by Storing Secrets in the Pipelines:
- No coherent form of version management, you should be able to roll back secrets across all pipelines simultaneously and push updates to all pipelines simaultaneously
- Duplicated secrets accross pipelines can cause a lot of problems with consistency. It's difficult to tell whether two secrets across pipelines are the same since they may have different hashes
- Secrets will almost definitely be needed outside of GoCD as well, making multiple different secret sources rely on manually being configured to be the same
- Secrets should be rotated frequently, which is a real pain
- Difficult to tell what secrets are since they can only be decrypted by GoCD

Security Risks caused by Storing Secrets in the Pipelines directly:
- Not safe to store any credentials (even encrypted) in our SCM repos
- Increased attack/leak area since secrets will be duplicated
- Slower response after leaked credentials since it's harder to roll secrets

##### Why not just ust the [GoCD file-based](https://github.com/gocd/gocd-file-based-secrets-plugin) secrets plugin?
Assuming you're using Vault:
- Secrets will almost definitely be needed outside of GoCD as well, making multiple different secret sources rely on manually being configured to be the same
- Increased attack/leak area since secrets will be duplicated
- Slower response after leaked credentials since pipelines it's harder to roll secrets
- The whole point behind Vault is to be a central store for all your secrets, let it do what it does well

If you're not using Vault:
- You may have no other choice but to use the GoCD file-based secrets plugin
- You should consider using some central store to manage your secrets

##### When should I use this plugin or [the other GoCD Vault Secrets Plugin](https://github.com/gocd/gocd-vault-secret-plugin)?
Benifits of this plugin (currently) not implemented in the other plugin:
- Supports AWS IAM Authentication
- Supports logical path secret lookup, thereby supporting every secrets engine
- Supports nested data access within secrets
- Open source so you can add new features easily
- Requires little background knowledge to jump in and add features

Benifits of the other plugin (currently) not implemented in this plugin:
- Supports `approle` and `cert` authentication
- Built specifically for `kvv2` secret engines, so accessing those secrets is simpler

##### I have a new feature idea, what do I do now?
Great! First step is you should open an issue on our repo. You should include whether you are creating the new feature yourself, or would like another dev to build it. Next, feel free to either fork the repo and build it yourself (see the local dev section below) or wait for another contributor to build it.

##### I'm not positive how to use this plugin, who can I ask?
If you don't understand the plugin, that is a bug in our documentation. Please open an issue and we'll update the docs as fast as we can!

## Local Development

If you're planning to work on the plugin code locally, see the [`gocd`](gocd)
directory for running a local GoCD server. The file you will almost definitely be contributing to is: [`src/amperity/gocd/secret/vault/plugin.clj`](./src/amperity/gocd/secret/vault/plugin.clj).

### Setting up a local Vault server

1. First, spin up a dev vault server.
```bash
vault server -dev
```

2. In a new terminal tab/window (so you can keep viewing the Vault logs), set up a `generic` secret store.
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
