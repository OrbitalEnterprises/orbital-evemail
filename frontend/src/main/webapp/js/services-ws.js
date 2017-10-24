/* Proxy Services */
(function () {
    var servicesWS = angular.module('evemailServicesWS', ['evemailRemoteServices']);

    /**
     * Service for retrieving build and version info.
     */
    servicesWS.factory('ReleaseService', ['SwaggerService',
        function (SwaggerService) {
            return {
                'buildDate': function () {
                    return SwaggerService.getSwagger()
                        .then(function (swg) {
                            return swg.Release.buildDate({}, {})
                                .then(function (result) {
                                    return result.status == 200 ? result.obj['buildDate'] : "";
                                })
                                .catch(function (error) {
                                    console.log(error);
                                    return "";
                                });
                        });
                },
                'version': function () {
                    return SwaggerService.getSwagger()
                        .then(function (swg) {
                            return swg.Release.version({}, {})
                                .then(function (result) {
                                    return result.status == 200 ? result.obj['version'] : "";
                                })
                                .catch(function (error) {
                                    console.log(error);
                                    return "";
                                });
                        });
                }
            };
        }]);


    /**
     * Service for sharing authentication state among all controllers.
     */
    servicesWS.factory('AccountWSService', ['SwaggerService',
        function (SwaggerService) {
            return {
                'getUser': function () {
                    return SwaggerService.getSwagger()
                        .then(function (swg) {
                            return swg.Account.getUser({}, {})
                                .then(function (result) {
                                    return result.obj;
                                }).catch(handleRemoteResponse);
                        });
                },
                'getUserPassword': function() {
                    return SwaggerService.getSwagger()
                        .then(function (swg) {
                            return swg.Account.getUserPassword({}, {})
                                .then(function (result) {
                                    return result.obj;
                                }).catch(handleRemoteResponse);
                        });
                },
                'changeUserPassword': function(pw) {
                    return SwaggerService.getSwagger()
                        .then(function (swg) {
                            return swg.Account.changeUserPassword({password: pw}, {})
                                .then(function (result) {
                                    return result;
                                }).catch(handleRemoteResponse);
                        });
                }
            };
        }]);

    /**
     * Service to collect and periodically update user credentials.  Changes in credentials are broadcast as an event.
     */
    servicesWS.factory('UserCredentialsService', ['$rootScope', '$timeout', 'AccountWSService',
        function ($rootScope, $timeout, AccountWSService) {
            var userInfo = null;
            var update = function () {
                $rootScope.$apply(function () {
                    $rootScope.$broadcast('UserInfoChange', userInfo);
                });
            };
            var updateUserCredentials = function () {
                AccountWSService.getUser().then(function (val) {
                    if (val != null) {
                        userInfo = val;
                        update();
                    }
                }).catch(function () {
                    // Reset user on any error
                    userInfo = null;
                    update();
                });
                $timeout(updateUserCredentials, 1000 * 60 * 3);
            };
            updateUserCredentials();
            return {
                'getUser': function () {
                    return userInfo;
                }
            };
        }]);

})();
