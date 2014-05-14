define(function(require, exports, module) {
    var $ = require('jquery'),
        _ = require('underscore'),
        Backbone = require('backbone'),
        Marionette = require('backbone.marionette'),
        template = require('tpl!javabin/templates/login'),
        bv = require('bootstrap.validator'),
        deparam = require('deparam');

    var LoginView = Marionette.Layout.extend({
        template: template,
        events: {
            "submit form": "login"
        },

        onDomRefresh: function() {
            this.$el.find('form').validator()
        },

        login: function(event) {
            event.preventDefault();
            var self = this;

            var $target = $(event.target).closest("form");

            var username = $target.find('#usernameInput').val();
            var password = $target.find('#passwordInput').val();

            $.ajax({
                url: this.options.endpoint + "/users/current?ignoreCookies=true",
                username: username,
                password: password,
                headers: {
                    "Authorization": "Basic " + btoa(username + ":" + password)
                }
            }).then(function() {
                window.location.replace("/app");
            }).fail(self.options.application.handleErrorWithModal)
        }
    });

    return LoginView;
});