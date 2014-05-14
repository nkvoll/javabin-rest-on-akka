define(["backbone"], function(Backbone) {
    var Welcome = Backbone.Model.extend({
        url: function() {
            return this.options.endpoint + "/";
        },

        initialize: function(attributes, options) {
            this.options = options;
        }
    });

    return Welcome;
});