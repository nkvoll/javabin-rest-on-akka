define(["backbone", "javabin/models/users/user"], function(Backbone, User) {
    var Users = Backbone.Collection.extend({
        model: User,

        url: function() {
            var query = (this.options.query || {}).q || "*"
            return this.options.endpoint + "/users/_search?query=" + query
        },

        initialize: function(attributes, options) {
            this.options = options;
        },

        parse: function(result) {
            var self = this;
            return _.map(result.users, function(username) { return new User({ username: username }, self.options) })
        }
    });

    return Users;
});