define(function(require, exports, module) {
    var $ = require('jquery'),
        _ = require('underscore'),
        Backbone = require('backbone'),
        Marionette = require('backbone.marionette'),
        template = require('tpl!javabin/templates/topbar'),
        updateModifierLinks = require('jquery.updateModifierLinks'),
        deparam = require('deparam');

    var TopbarView = Marionette.Layout.extend({
        template: template,
        className: "container",

        initialize: function(options) {
            var self = this;
            this.listenTo(options.application.vent, 'viewChanged', function(options) {
                self.render();
            });

            this.options = options;
        },

        templateHelpers: function() {
            var self = this
            return {
                hasPermission: function(permission) {
                    var permissions = self.model.get("attributes").permissions || [];

                    var matchingPermissions = _.filter(permissions, function(currentPermission) {
                        return permission.match(new RegExp(currentPermission))
                    });
                    return _.size(matchingPermissions) > 0
                }
            }
        },

        onDomRefresh: function() {
            this.$el.updateModifierLinks()
        }
    });

    return TopbarView;
});