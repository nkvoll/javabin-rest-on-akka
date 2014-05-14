define(function(require, exports, module) {
    var $ = require('jquery'),
        _ = require('underscore'),
        Backbone = require('backbone'),
        Marionette = require('backbone.marionette'),
        template = require('tpl!javabin/templates/error'),
        deparam = require('deparam');

    var ErrorView = Marionette.Layout.extend({
        template: template
    });

    return ErrorView;
});