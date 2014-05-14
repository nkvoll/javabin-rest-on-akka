define(function(require, exports, module) {
    var $ = require('jquery'),
        _ = require('underscore'),
        Backbone = require('backbone'),
        Marionette = require('backbone.marionette'),
        template = require('tpl!javabin/templates/users/user.detail'),
        CodeMirror = require('codemirror'),
        CodeMirrorJS = require('codemirror.mode.javascript'),
        md5 = require('md5'),
        identicon = require('identicon'),
        deparam = require('deparam');

    var UserView = Marionette.ItemView.extend({
        template: template,

        templateHelpers: function() {
            var self = this;
            return {
                hasPermission: function(permission) {
                    var permissions = (self.options.application.currentUser.get("attributes") || {}).permissions || [];

                    var matchingPermissions = _.filter(permissions, function(currentPermission) {
                        return permission.match(new RegExp(currentPermission))
                    });
                    return _.size(matchingPermissions) > 0
                },

                hasEditPermission: function() {
                    return self.templateHelpers().hasPermission("users.user.attributes.edit")
                },

                identicon: function(value, size) {
                    var hash = md5(value);
                    var data = new Identicon(hash, size);

                    return '<img width='+size+' height='+size+' src="data:image/png;base64,' + data + '">'
                }
            }
        },

        onDomRefresh: function() {
            var self = this;
            this.$el.find(".codemirror-editor").each(function(index, item) {
                var $item = $(item);

                var editor = CodeMirror.fromTextArea(item, {
                    mode: "application/json",
                    lineNumbers: true,
                    electricChars: true,
                    readOnly: $item.data("read-only") !== false
                });

                var editorName = $item.data("editor-name");
                if (editorName !== undefined) {
                    self.editors[editorName] = editor;
                }
            });
        },

        events: {
            "submit form": "updateAttributes",
            "click .add-permission": "addPermission"
        },

        updateAttributes: function(event) {
            event.preventDefault();
            var self = this;

            var $form = $(event.target).closest('form');
            var data = _.object(_.map($form.serializeArray(), _.values))

            data.attributes = JSON.parse(data.attributes);

            this.model.set(data);

            $form.find(':submit').prop('disabled', true);
            this.model.save().then(function() { self.render() })
        },

        addPermission: function(evt) {
            evt.preventDefault();
            var $form = this.$el.find('form')
            var data = _.object(_.map($form.serializeArray(), _.values))

            var attributes = JSON.parse(data.attributes);

            var newPermission = prompt('Enter new permission. (e.g "messages")')
            if(newPermission) {
                attributes.permissions = attributes.permissions || []
                attributes.permissions.push(newPermission)

                this.model.set('attributes', attributes);
                this.render()
            }
            return false;
        }
    });

    return UserView;
});