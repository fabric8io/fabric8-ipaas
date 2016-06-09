var APIMAN_CONFIG_DATA = {
    "apiman" : {
        "version" : "f8-${project.version}",
        "builtOn" : "${timestamp}",
        "logoutUrl" : null
    },
    "user" : {
        "username" : null
    },
    "ui" : {
        "header" : "ose",
        "platform" : "f8",
        "metrics" : false
    },
    "api" : {
        "endpoint" : "/apiman",
        "auth" : {
            "type" : "bearerToken",
            "bearerToken" : {
                "token" : "@token@",
                "refreshPeriod" : 288
            }
            
        }
    }
};
