define(function(require, exports, module) {
    var $ = require('jquery'),
        _ = require('underscore'),
        Backbone = require('backbone'),
        Marionette = require('backbone.marionette'),
        template = require('tpl!javabin/templates/messages/send'),

        Bloodhound = require('bloodhound'),
        typeahead = require('typeahead'),

        bv = require('bootstrap.validator'),
        deparam = require('deparam');

    var SendMessageView = Marionette.Layout.extend({
        template: template,

        initialize: function(options) {
            var self = this;

            this.options = options;

            var usersArray = [];

            this.users = new Bloodhound({
                datumTokenizer: Bloodhound.tokenizers.obj.whitespace('value'),
                queryTokenizer: Bloodhound.tokenizers.whitespace,

                local: $.map(usersArray, function(user) { return { value: user.username }; }),
                remote: {
                    url: this.options.endpoint + '/users/_search?query=%QUERY',
                    filter: function(parsedResponse) {
                        return _.map(parsedResponse.users, function(name) { return { username: name }});
                    }
                }
            });

            this.users.initialize();

            this.listenTo(options.application.vent, 'message:clicked', function(evt) {
                var currentUsername = options.application.currentUser.get("username");
                var usernames = [evt.message.get('source'), evt.message.get('destination')];

                var suggestedNames = _.filter(usernames, function(username) { return username != currentUsername });
                suggestedNames.push(currentUsername);

                // fall back to selecting our own name...
                var selectedUsername = suggestedNames[0];

                self.$el.find('input[name=destination]').typeahead('val', selectedUsername);
                self.$el.find('form').validator('validate')
                self.$el.find('input[name=contents]').focus()
            });
        },

        events: {
            "submit form": "send",
            "change input[name=destination]": "destChanged"
        },

        onRender: function() {
            var $form = this.$el.find('form')
            $form.validator()

            var dest = this.$el.find('input[name=destination]')

            dest.typeahead({
                hint: true,
                highlight: true,
                minLength: 1
            }, {
                name: 'users',
                displayKey: 'username',
                source: this.users.ttAdapter()
            });

            $form.find('input.tt-hint').removeAttr('required')
            $form.find('input.tt-hint').removeAttr('autofocus')
        },

        send: function(event) {
            event.preventDefault();
            var self = this

            var $target = $(event.target).closest("form");
            var data = _.object(_.map($target.serializeArray(), _.values))

            $target.find('input[name=contents]').val('');

            $.ajax({
                url: this.options.endpoint + "/messages/send/" + data.destination,
                data: { contents: data.contents },
                method: "post"
            }).then(function() {
                //console.log("successfully sent")
            }).fail(self.options.application.handleErrorWithModal)
        }
    });

    return SendMessageView;
});