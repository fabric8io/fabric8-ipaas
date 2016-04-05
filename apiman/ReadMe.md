##Apiman Gateway

Apiman is an ApiManager. This application deploy the Apiman REST API and console. Apiman requires the 
Elasticsearch service to be up and running. You can use this application to manage the apiman gateway. Note that the  gateway runs in a different container. The Fabric8 gateway uses the Apiman engine to enforce policies and plans that are published to the gateway using the Apiman console.

For more details please see the <a href="http://fabric8.io/guide/apiManagement.html">Fabric8 User Guide on API Management </a> and the <a href="http://www.apiman.io/">Apiman website</a> itself.

If you are running from vagrant then once apiman is running you can hit

http://apiman.vagrant.f8/apiman/

to see the apiman status page. All other endpoints in this service require and authenticated session using a valid bearer token. For the apiman console navigate to

http://apiman-default.vagrant.f8/apiman/apimanui/

Note that the host part of the urls may vary when not running on vagrant, and that 
you need a Bearer OAuthToken set. Just use the link from the fabric8 console.


##Running Apiman on SSL

If you'd like to run apiman over SSL then first double check you have the following system parameters set correctly:

KUBERNETES_DOMAIN = vagrant.f8
KUBERNETES_NAMESPACE = default

Now you can run:

mvn -Pssl clean compile

to generate a selfsigned certificate for hostname 'apiman-default.vagrant.f8', in the target/secret directory, and it adds it as a secret called 'apiman-keystore' in the current 'default' namespace.

Now to deploy apiman with SSL support you execute

mvn -Pssl -Pf8-local-deploy

which mounts the secrets in the /secret directory in the pod and Jetty will start up
using the SslConnectionFactory, based to the system parameter 'APIMAN_SSL' being set 
to 'true'. See also the properties set in the maven 'ssl' profile

<docker.env.apiman.ssl>true</docker.env.apiman.ssl>

The fabric8-maven-plugin aready creates a route but you want a passthrough route so run

oc delete route apiman 
oc create route passthrough apiman --service apiman

Now the apiman should run at

https://apiman.vagrant.f8/apiman/apimanui



