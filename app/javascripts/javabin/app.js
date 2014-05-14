/*define(["jquery", "underscore", "backbone", "backbone.marionette",
    "javabin/models/user",
    "deparam"],
    function($, _, Backbone, Marionette,
        User,
        deparam) {
        */

define(function(require, exports, module) {
    var $ = require('jquery'),
        _ = require('underscore'),
        Backbone = require('backbone'),
        Marionette  = require('backbone.marionette'),
        CurrentUser = require('javabin/models/currentuser'),
        Welcome = require('javabin/models/welcome'),
        WelcomeView = require('javabin/views/welcome'),
        TopBarView = require('javabin/views/topbar'),
        LoginView = require('javabin/views/login'),
        RegisterView = require('javabin/views/register'),
        ErrorView = require('javabin/views/error'),

        UserIndexView = require('javabin/views/users/index'),
        UserView = require('javabin/views/users/user'),
        User = require('javabin/models/users/user'),

        MessagesView = require('javabin/views/messages/index'),
        deparam = require('deparam');

    var App = new Marionette.Application();

    var handleError = function(request, useModal) {
        var model = new Backbone.Model({request: request})
        var errorView = new ErrorView({ application: App, model: model });
        if(useModal) {
            App.showModal(errorView);
        } else {
            App.vent.trigger('viewChanged', {});
            App.centerRegion.show(errorView);
        }
    }
    App.handleError = handleError;
    App.handleErrorWithModal = function(request) { App.handleError(request, true); };

    App.currentUser = new CurrentUser({username: "anonymous"});

    App.addInitializer(function(options) {
        var AppRouter = Backbone.Router.extend({
            routes: {
                "": "welcome",
                "search": "search",
                "search?*params": "search",
                "users": "users",
                "users/user/:username": "usersUser",
                "users/search?*params": "usersSearch",
                "messages": "messages",
                "register": "register",
                "login": "login",
                "logout": "logout"
            },

            logout: function() {
                $.ajax({
                    url: "http://localhost:8080/api/v0/users/current?ignoreCookies=true",
                    headers: {
                        Authorization: "Basic YW5vbnltb3VzOmZvb2Jhcg=="
                    }
                }).then(function() {
                    window.location.replace("/app");
                });
            },

            welcome: function() {
                var welcome = new Welcome([], { endpoint: options.endpoint })
                var fetching = welcome.fetch();
                $.when(fetching).then(function() {
                    App.vent.trigger('viewChanged', {})
                    App.centerRegion.show(new WelcomeView({application: App, model: welcome}));
                    window.scrollTo(0, 0);
                }, function(request, status, error) {
                    handleError(request);
                });
            },

            register: function() {
                App.vent.trigger('viewChanged', {})
                App.centerRegion.show(new RegisterView({application: App, endpoint: options.endpoint}));
                window.scrollTo(0, 0);
            },

            login: function() {
                App.vent.trigger('viewChanged', {})
                App.centerRegion.show(new LoginView({application: App, endpoint: options.endpoint}));
                window.scrollTo(0, 0);
            },

            users: function() {
                App.vent.trigger('viewChanged', {})
                var view = new UserIndexView({application: App, endpoint: options.endpoint})
                App.centerRegion.show(view);
                view.usersCollection.fetch().then(function() {
                }, function(request, status, error) {
                    handleError(request);
                });
                window.scrollTo(0, 0);
            },

            usersUser: function(username) {
                var user = new User({ username: username }, { endpoint: options.endpoint })
                var fetching = user.fetch();
                $.when(fetching).then(function() {
                    var view = new UserView({application: App, model: user})
                    App.vent.trigger('viewChanged', {});
                    App.centerRegion.show(view);
                    window.scrollTo(0, 0);
                }, function(request, status, error) {
                    handleError(request);
                });
            },

            usersSearch: function(params) {
                if(!(App.centerRegion.currentView instanceof UserIndexView)) {
                    App.centerRegion.show(new UserIndexView({application: App, endpoint: options.endpoint}));
                    App.vent.trigger('viewChanged', {})
                }

                App.centerRegion.currentView.usersCollection.options.query = deparam(params || '')

                App.centerRegion.currentView.usersCollection.reset();
                App.centerRegion.currentView.usersCollection.fetch().then(function() {
                }, function(request, status, error) {
                    handleError(request);
                });
            },

            messages: function() {
                App.vent.trigger('viewChanged', {})
                App.centerRegion.show(new MessagesView({application: App, endpoint: options.endpoint}));
                window.scrollTo(0, 0);
            }
        });

        new AppRouter();

        App.on('all', function(event) {
            console.log('app.event:', event, arguments);
        });

        App.vent.on('all', function(event) {
            console.log('vent.event:', event, arguments);
        });

        Backbone.history.start();

        App.topRegion.show(new TopBarView({ application: App, model: App.currentUser }));
    });

    App.addRegions({
        topRegion: "#top-region",
        centerRegion: "#center-region",
        modalRegion: "#app-modal-region"
    });

    App.showModal = function(view) {
        App.modalRegion.show(view);
        $(App.modalRegion.el).closest(".javabin-app-modal").modal();
    };

    return App;
})