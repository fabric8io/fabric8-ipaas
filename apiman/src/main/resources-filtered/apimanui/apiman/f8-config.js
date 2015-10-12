var APIMAN_CONFIG_DATA = {
    "platform" : "standalone",
    "apiman" : {
        "version" : "f8-${project.version}",
        "builtOn" : "${timestamp}",
        "logoutUrl" : null
    },
    "user" : {
        "username" : null
    },
    "ui" : {
        "header" : "apiman",
        "metrics" : true
    },
    "api" : {
        "endpoint" : "/apiman",
        "auth" : {
            "type" : "bearerTokenFromHash"
        }
    }
};
