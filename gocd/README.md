Local Development Server
========================

This directory contains a [docker-compose](https://docs.docker.com/compose/)
file for running a server (and optionally, an agent). To test out the plugin,
first bring up the server:

```
$ cd gocd
$ docker-compose up -d server
```

Once you've made changes to the code, from the project root:

```
$ make docker-install
```

This will rebuild any necessary artifacts, copy the resulting plugin into the
docker server's directory, then restarts the server.
