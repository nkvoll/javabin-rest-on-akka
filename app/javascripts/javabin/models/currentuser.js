define(["backbone"], function(Backbone) {
    var User = Backbone.Model.extend({
        url: function() {
            return this.options.endpoint + "/users/current";
        },

        initialize: function(attributes, options) {
            this.options = options;
        }
    });

    return User;
});