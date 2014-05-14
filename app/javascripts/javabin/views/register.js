define(function(require, exports, module) {
    var $ = require('jquery'),
        _ = require('underscore'),
        Backbone = require('backbone'),
        Marionette = require('backbone.marionette'),
        template = require('tpl!javabin/templates/register'),
        bv = require('bootstrap.validator'),
        deparam = require('deparam');

    var RegisterView = Marionette.Layout.extend({
        template: template,
        events: {
            "submit form": "register"
        },

        onDomRefresh: function() {
            this.$el.find('form').validator()
        },

        register: function(event) {
            event.preventDefault();
            var self = this
            var $target = $(event.target).closest("form");

            $.ajax({
                url: this.options.endpoint + "/users/register",
                data: $target.serialize(),
                method: "post"
            }).then(function() {
                window.location.hash = "login";
            }).fail(self.options.application.handleErrorWithModal)
        }
    });

    return RegisterView;
});