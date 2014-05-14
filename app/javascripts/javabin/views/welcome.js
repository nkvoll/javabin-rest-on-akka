define(function(require, exports, module) {
    var $ = require('jquery'),
        _ = require('underscore'),
        Backbone = require('backbone'),
        Marionette = require('backbone.marionette'),
        template = require('tpl!javabin/templates/welcome'),
        deparam = require('deparam');

    var WelcomeView = Marionette.Layout.extend({
        template: template,
        templateHelpers: function() {
            var self = this;
            return {
                application: function() {
                    return self.options.application
                }
            }
        }
    });

    return WelcomeView;
});