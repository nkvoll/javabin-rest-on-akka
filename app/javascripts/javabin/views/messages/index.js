define(function(require, exports, module) {
    var $ = require('jquery'),
        _ = require('underscore'),
        Backbone = require('backbone'),
        Marionette = require('backbone.marionette'),
        template = require('tpl!javabin/templates/messages/index'),

        SendMessageView = require('javabin/views/messages/send'),
        PollMessagesView = require('javabin/views/messages/poll'),

        Messages = require('javabin/models/messages/messages'),

        bv = require('bootstrap.validator'),
        deparam = require('deparam');

    var MessagesView = Marionette.Layout.extend({
        template: template,

        initialize: function(options) {
            this.options = options;
        },

        onRender: function() {
            this.sendRegion.show(new SendMessageView(this.options));

            var pollView = new PollMessagesView({ collection: new Messages() }, this.options)
            this.pollRegion.show(pollView);
        },

        onDomRefresh: function() {
            this.$el.find('form').validator()
        },

        regions: {
            sendRegion: "#send",
            pollRegion: "#poll"
        }
    });

    return MessagesView;
});