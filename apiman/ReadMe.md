##Apiman Gateway

Apiman is an ApiManager. This application deploy the Apiman REST API and console. Apiman requires the 
Elasticsearch service to be up and running. You can use this application to manage the apiman gateway. Note that the  gateway runs in a different container. The Fabric8 gateway uses the Apiman engine to enforce policies and plans that are published to the gateway using the Apiman console.

For more details please see the <a href="http://fabric8.io/guide/apiManagement.html">Fabric8 User Guide on API Management </a> and the <a href="http://www.apiman.io/">Apiman website</a> itself.

If you are running from vagrant then once apiman is running you can hit

http://apiman.vagrant.f8/apiman/

to see the apiman status page. All other endpoints in this service require and authenticated session using a valid bearer token. For the apiman console navigate to

http://apiman.vagrant.f8/apimanui/

Note that the host part of the urls may vary when not running on vagrant.