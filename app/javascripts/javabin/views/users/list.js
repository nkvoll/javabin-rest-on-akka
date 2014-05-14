define(function(require, exports, module) {
    var $ = require('jquery'),
        _ = require('underscore'),
        Backbone = require('backbone'),
        Marionette = require('backbone.marionette'),
        template = require('tpl!javabin/templates/users/list'),
        userTemplate = require('tpl!javabin/templates/users/user'),
        md5 = require('md5'),
        identicon = require('identicon'),
        bv = require('bootstrap.validator'),
        deparam = require('deparam');

    var UserListView = Marionette.CompositeView.extend({
        template: template,
        itemView: Marionette.ItemView.extend({
            template: userTemplate,
            templateHelpers: {
                identicon: function(value, size) {
                    var hash = md5(value);
                    var data = new Identicon(hash, size);

                    return '<img width='+size+' height='+size+' src="data:image/png;base64,' + data + '">'
                }
            }
        }),
        itemViewContainer: '#users',

        initialize: function(mods, options) {
            this.options = options;
        }
    });

    return UserListView;
});