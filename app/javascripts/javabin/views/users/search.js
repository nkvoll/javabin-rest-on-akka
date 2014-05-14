define(function(require, exports, module) {
    var $ = require('jquery'),
        _ = require('underscore'),
        Backbone = require('backbone'),
        Marionette = require('backbone.marionette'),
        template = require('tpl!javabin/templates/users/search'),

        bv = require('bootstrap.validator'),
        deparam = require('deparam');

    var SearchUsersView = Marionette.Layout.extend({
        template: template,

        initialize: function(options) {
            var self = this;
            this.options = options;
        },

        events: {
            "submit form": "search"
        },

        onRender: function() {
            var $form = this.$el.find('form');
            $form.validator();
        },

        search: function(event) {
            event.preventDefault()

            var self = this

            var $target = $(event.target).closest("form");
            var data = _.object(_.map($target.serializeArray(), _.values))

            window.location.hash = "#users/search?q=" + data.username
        }
    });

    return SearchUsersView;
});