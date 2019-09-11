.PHONY: all clean uberjar plugin docker-install
.SUFFIXES:

version := $(shell grep defproject project.clj | cut -d ' ' -f 3 | tr -d \")
plugin_name := gocd-vault-secrets-plugin-$(version).jar

uberjar_path := target/uberjar/gocd-vault-secret-plugin-$(version)-standalone.jar
plugin_path := target/plugin/$(plugin_name)
install_path := gocd/server/plugins/external/$(plugin_name)

all: plugin

clean:
	rm -rf target

$(uberjar_path): project.clj $(shell find resources -type f) $(shell find src -type f)
	lein uberjar

uberjar: $(uberjar_path)

$(plugin_path): $(uberjar_path)
	@mkdir -p target/plugin
	cd target/plugin; jar xf ../../$(uberjar_path)
	rm target/plugin/*.class
	rm target/plugin/org/apache/thrift/transport/TFileTransport*.class
	rm -r target/plugin/mozilla
	find target/plugin -type f -path 'target/plugin/clojure/repl*' -delete
	find target/plugin -type d -empty -delete
	cd target/plugin; jar cmf META-INF/MANIFEST.MF $(plugin_name) plugin.xml amperity clojure com org

plugin: $(plugin_path)

$(install_path): $(plugin_path)
	cp $^ $@
	cd gocd; docker-compose restart server

docker-install: $(install_path)
