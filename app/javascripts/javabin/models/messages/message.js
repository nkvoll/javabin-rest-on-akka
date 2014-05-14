define(["backbone"], function(Backbone) {
    var Message = Backbone.Model.extend({
        url: function() {
            return this.options.endpoint + "/messages/message/" + this.get("id");
        },

        initialize: function(attributes, options) {
            this.options = options;
        }
    });

    return Message;
});