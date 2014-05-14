define(["backbone"], function(Backbone) {
    var Messages = Backbone.Collection.extend({
        url: function() {
            return this.options.endpoint + "/messages/receive"
        },

        initialize: function(attributes, options) {
            this.options = options;
        },

        comparator: function(message) {
            return -new Date(message.get("created")).getTime();
        }
    });

    return Messages;
});