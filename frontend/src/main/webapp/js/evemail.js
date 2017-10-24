/* EveKit Data Platform Module */
(function(){
    var evemail = angular.module('evemail', [
        'ngResource',
        'evemailDialog',
        'evemailRemoteServices',
        'evemailServicesWS'
    ]);

    // Capture any authorization errors before we process the rest of the window location
    var searchParams = window.location.search;
    var auth_error = null;
    if (searchParams && searchParams.length > 1) {
        var vals = searchParams.substr(1).split('&');
        for (var i = 0; i < vals.length; i++) {
            var next = vals[i].split('=');
            if (next[0] === 'auth_error') {
                auth_error = decodeURIComponent(next[1].replace(/\+/g,' '));
                break;
            }
        }
    }

    /* Add scrolling directive to handle hash scrolling. */
    /* nicked from here: http://stackoverflow.com/questions/14712223/how-to-handle-anchor-hash-linking-in-angularjs */
    evemail.directive('scrollTo', function ($location, $anchorScroll) {
        return function(scope, element, attrs) {

            element.bind('click', function(event) {
                event.stopPropagation();
                var off = scope.$on('$locationChangeStart', function(ev) {
                    off();
                    ev.preventDefault();
                });
                var location = attrs.scrollTo;
                $location.hash(location);
                $anchorScroll();
            });
        }});

    /* Inband controller for setting the version for the page */
    evemail.controller('VersionCtrl', ['$scope', 'ReleaseService',
        function($scope, ReleaseService) {
            $scope.evemailBuildDate = -1;
            $scope.evemailVersion = -1;
            ReleaseService.buildDate().then(function (val) {
                $scope.$apply(function() {
                    $scope.evemailBuildDate = val;
                });
            });
            ReleaseService.version().then(function (val) {
                $scope.$apply(function() {
                    $scope.evemailVersion = val;
                });
            });
        }]);

    /* Inband controller for setting authentication status and other container menu settings. */
    evemail.controller('EveMailCtrl', ['$scope', '$timeout', 'UserCredentialsService', 'AccountWSService', 'DialogService',
        function($scope, $timeout, UserCredentialsService, AccountWSService, DialogService) {
            // Scope setup
            $scope.showPassword = false;
            $scope.accountPassword = null;
            $scope.newPassword = '';
            $scope.renewPassword = '';
            // Set up user credential management
            $scope.userInfo = UserCredentialsService.getUser();
            $scope.$on('UserInfoChange', function(event, ui) {
                $scope.userInfo = ui;
            });
            // Check for authentication error and post an appropriate dialog
            if (auth_error !== null) {
                $timeout(function () { DialogService.simpleErrorMessage(auth_error, 20) }, 1);
            }
            // Retrieve current user password on demand
            $scope.retrievePassword = function() {
                if ($scope.accountPassword !== null) return;
                AccountWSService.getUserPassword().then(function (val) {
                    $scope.$apply(function() {
                        $scope.accountPassword = val.password;
                    });
                });
            };
            // Sanity check password change form
            $scope.isFormInvalid = function() {
                // Password must be between 4 and 12 characters in length
                if ($scope.newPassword.length < 4 || $scope.newPassword.length > 12)
                    return true;
                // New and retyped password must be identical
                if ($scope.newPassword !== $scope.renewPassword)
                    return true;
                // Otherwise, good password
                return false;
            }
            // Start password change dialog
            $scope.changePassword = function() {
                $scope.newPassword = '';
                $scope.renewPassword = '';
                $('#changePassword').modal({
                    backdrop: 'static',
                    keyboard: false
                });
            };
            // Commit password change
            $scope.updatePassword = function() {
                AccountWSService.changeUserPassword($scope.newPassword).then(function () {
                    $scope.$apply(function() {
                        $scope.showPassword = false;
                        $scope.accountPassword = null;
                        DialogService.simpleInfoMessage('Password changed successfully!', 5);
                    });
                }).catch(function(err) {
                    // Fail, show error message
                    $scope.$apply(function() {
                        DialogService.connectionErrorMessage('changing password: ' + err.errorMessage, 20);
                    });
                });
            };
        }]);

})();
