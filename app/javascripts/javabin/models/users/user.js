define(["backbone"], function(Backbone) {
    var User = Backbone.Model.extend({
        idAttribute: "username",

        url: function() {
            return this.options.endpoint + "/users/user/" + this.get("username");
        },

        initialize: function(attributes, options) {
            this.options = options;
        }
    });

    return User;
});