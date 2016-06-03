Elasticsearch is a search server based on Lucene. It provides a distributed, multitenant-capable full-text search engine with a RESTful web interface and schema-free JSON documents. Elasticsearch is developed in Java and is released as open source under the terms of the Apache License.

More details on the <a href="http://wikipedia.org/wiki/Elasticsearch" target="wikipedia">Elasticsearch wikipedia page</a> or the <a href="http://elasticsearch.com/" target="elasticsearch">Elasticsearch home page</a>.

##Running Elasticsearch on SSL

By default Elasticsearch can be used without authentication. When the 'ssl' profile
is used the maven will build creates a selfsigned certificate which is added to
a keystore, and Searchguard is installed by the `docker-entrypoint.sh` start script to provide a way to either verify a client certificate (turned off: `enforce_clientauth: false`), or to use Basic Authentication. Run

mvn -Pssl -Pf8-local-deploy

to run Elasticsearch with Basic Auth over HTTPS. The build will create a `elasticsearch-v1-keystore` secret which contains the keystore with the selfsigned certificate. The credentials used by Basic Auth are stored in the `src/main/resources/elasticsearch.yml` file, and can
be update in the `docker-entrypoint.sh`.

searchguard.authentication.settingsdb.user.admin: supersecret
searchguard.authentication.authorization.settingsdb.roles.admin: ["admin"]


