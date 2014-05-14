define(function(require, exports, module) {
    var $ = require('jquery'),
        _ = require('underscore'),
        Backbone = require('backbone'),
        Marionette = require('backbone.marionette'),
        template = require('tpl!javabin/templates/messages/poll'),
        messageTemplate = require('tpl!javabin/templates/messages/message'),

        Message = require('javabin/models/messages/message'),
        moment = require('moment'),

        identicon = require('identicon'),
        md5 = require('md5'),

        _markdown = require('markdown'),

        bv = require('bootstrap.validator'),
        deparam = require('deparam');

    var hashCode = function(str) {
        for(var ret = 0, i = 0, len = str.length; i < len; i++) {
            ret = (31 * ret + str.charCodeAt(i)) << 0;
        }
        return ret;
    }

    var intToHSL = function(value, saturation, lightness) {
        var shortened = value % 360;
        return "hsl(" + shortened + ", "+ (saturation||100) +"%, "+ (lightness||30) + "%)";
    };

    var colorCache = {}

    var PollMessagesView = Marionette.CompositeView.extend({
        template: template,
        itemView: Marionette.ItemView.extend({
            template: messageTemplate,
            events: {
                "click .panel-heading": "clicked"
            },
            templateHelpers: function() {
                var self = this;
                var identicons = {}
                return {
                    moment: moment,
                    sourceIsSelf: function() {
                        return self.model.get('source') == self.model.options.application.currentUser.get('username');
                    },
                    markdown: function(content) {
                        var html = _markdown.toHTML(content);
                        return html;
                    },
                    identicon: function(value, size) {
                        if(!identicons[value]) {
                            var hash = md5(value);
                            var data = new Identicon(hash, size);
                            identicons[value] = data
                        }
                        return '<img width='+size+' height='+size+' src="data:image/png;base64,' + identicons[value] + '">'
                    }
                }
            },
            onRender: function() {
                var source = this.model.get('source')

                if(!colorCache[source]) {
                    var value = hashCode(md5(this.model.get('source')))

                    colorCache[source] = {
                        color: intToHSL(value, 70, 30),
                        colorGradientTop: intToHSL(value, 60, 90),
                        colorGradientBottom: intToHSL(value, 40, 75),
                        colorBorder: intToHSL(value, 60, 70)
                    }
                }

                var cachedColor = colorCache[source]

                this.$el.find('.panel-heading').css({
                    'color': cachedColor.color,
                    'background-color': cachedColor.colorGradientTop,
                    'border-color': cachedColor.colorBorder,
                    'background-image': "linear-gradient(to bottom, "+cachedColor.colorGradientTop+" 0%, "+cachedColor.colorGradientBottom+" 80%)"
                })

                this.$el.find('.panel').css({
                    'border-color': cachedColor.colorBorder
                })
            },
            clicked: function(evt) {
                evt.preventDefault();

                if(!$(evt.target).hasClass("btn") && !$(evt.target).parent().hasClass("btn")) {
                    this.model.options.application.vent.trigger("message:clicked", { message: this.model });
                } else {
                    this.model.destroy();
                }
                return false;
            },
            updateMoment: function() {
                this.$el.find('.moment').each(function(id, el) {
                    var $el = $(el);
                    var m = moment($el.data('moment'));
                    $el.html(m.fromNow());
                })
            }
        }),
        itemViewContainer: '#messages',

        initialize: function(mods, options) {
            this.options = options;
            var now = new Date();
            var yesterday = new Date(now.getTime() - 86400 * 1000);
            this.filterDelivered = false;
            this.options.lastSince = this.options.lastSince || yesterday.toISOString() || "2014-01-01T00:00:00.000Z";

            this.polling = null
            this.poll_timeout = -1;
            this.updatingChildren = null
        },

        onRender: function() {
            this.$messageCountEl = this.$el.find('#messageCount')

            this.$pollError = this.$el.find("#pollError")

            if(!this.polling) {
                this.poll();
            }

            if(!this.updatingChildren) {
                this.updateChildren()
            }
        },

        onBeforeClose: function() {
            if(this.polling) {
                this.polling.abort();
            }
            if(this.poll_timeout != -1) {
                window.clearTimeout(this.poll_timeout);
            }
        },

        poll: function() {
            var self = this;

            this.polling = $.ajax({
                url: this.options.endpoint + "/messages/receive/poll?updateDelivered=true&filterDelivered="+this.filterDelivered+"&since=" + this.options.lastSince.replace("+", "%2B")
            });

            self.$pollError.addClass("hidden")

            this.polling.success(function(data, status, xhr) {
                var strData = _.filter(data.split("\r\n"), function(it) { return !_.isEmpty(it) });
                var jsonMessages = _.map(strData, JSON.parse);
                var messages = _.map(jsonMessages, function(msgData) { return new Message(msgData, _.clone(self.options)) });

                self.collection.add(messages);

                if(_.size(messages)) {
                    self.updateMessageCount()
                }

                var now = new Date();
                var yesterday = new Date(now.getTime() - 86400 * 1000);
                self.options.lastSince = yesterday.toISOString();
                self.filterDelivered = true;

                self.poll()
            });

            this.polling.fail(function() {
                self.$pollError.removeClass("hidden")
                self.filterDelivered = false;
                self.poll_timeout = setTimeout(function() { self.poll_timeout = -1; self.poll(); }, 5000)
            })
        },

        updateChildren: function() {
            var self = this;

            self.children.each(function(child) {
                child.updateMoment();
            })

            this.updatingChildren = setTimeout(function() {
                self.updateChildren()
            }, 5000)
        },

        collectionEvents: {
            "remove": "updateMessageCount"
        },

        updateMessageCount: function() {
            var size = this.collection.size()
            var message = "showing " + size + " messages"
            if(size == 1) {
                message = message.substring(0, message.length-1)
            }

            this.$messageCountEl.html(message)
        },

        appendHtml: function(collectionView, itemView, index){
            var childrenContainer = collectionView.itemViewContainer ? collectionView.$(collectionView.itemViewContainer) : collectionView.$el;
            var children = childrenContainer.children();

            //console.log(index, children.size(), itemView.model.get("contents"))

            if (children.size() == 0) {
                childrenContainer.append(itemView.el);
            } else if(children.size() == index) {
                childrenContainer.append(itemView.el);
            } else {
                children.eq(0).before(itemView.el);
            }
        }
    });

    return PollMessagesView;
});