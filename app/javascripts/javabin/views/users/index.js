define(function(require, exports, module) {
    var $ = require('jquery'),
        _ = require('underscore'),
        Backbone = require('backbone'),
        Marionette = require('backbone.marionette'),
        template = require('tpl!javabin/templates/users/index'),

        UsersCollection = require('javabin/models/users/search'),

        SearchUsersView = require('javabin/views/users/search'),
        UsersListView = require('javabin/views/users/list'),

        deparam = require('deparam');

    var UsersIndexView = Marionette.Layout.extend({
        template: template,

        initialize: function(options) {
            this.options = options;

            this.usersCollection = new UsersCollection([], this.options);
        },

        onRender: function() {
            this.topRegion.show(new SearchUsersView({collection: this.usersCollection}, this.options));
            this.centerRegion.show(new UsersListView({collection: this.usersCollection}, this.options));
        },

        regions: {
            topRegion: ".top-region",
            centerRegion: ".center-region"
        }
    });

    return UsersIndexView;
});