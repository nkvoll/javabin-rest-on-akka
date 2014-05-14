requirejs.config({
    baseUrl: "javascripts",
    paths: {
        jquery: "../bower_components/jquery/dist/jquery",
        backbone: "../bower_components/backbone/backbone",
        underscore: "../bower_components/underscore/underscore",
        "underscore.string": "../bower_components/underscore.string/lib/underscore.string",

        "backbone.marionette": "../bower_components/marionette/lib/core/amd/backbone.marionette",
        "backbone.wreqr": "../bower_components/backbone.wreqr/lib/backbone.wreqr",
        "backbone.babysitter": "../bower_components/backbone.babysitter/lib/amd/backbone.babysitter",

        "bootstrap": "../bower_components/bootstrap/dist/js/bootstrap",
        "bootstrap.validator": "../bower_components/bootstrap-validator/dist/validator",

        "deparam": "vendor/deparam",
        "markdown": "../bower_components/markdown/lib/markdown",

        "pace": "../bower_components/pace/pace",

        "jquery.updateModifierLinks": "vendor/jquery.updateModifierLinks",

        "typeahead": "../bower_components/typeahead.js/dist/typeahead.jquery",
        "bloodhound": "../bower_components/typeahead.js/dist/bloodhound",

        "modernizr": "../bower_components/modernizr/modernizr",

        text: '../bower_components/text/text',
        tpl: '../bower_components/requirejs-tpl-jfparadis/tpl',

        moment: "../bower_components/momentjs/moment",

        "codemirror": "../bower_components/codemirror/lib/codemirror",
        "codemirror.mode.javascript": "../bower_components/codemirror/mode/javascript/javascript",

        "pnglib": "vendor/pnglib",
        "identicon": "vendor/identicon",
        "md5": "../bower_components/blueimp-md5/js/md5"
    },
    map: {
        "*": {
            "../../lib/codemirror": "codemirror"
        }
    },
    shim: {
        jquery: {
            exports: "jQuery"
        },
        "underscore": {
            exports: "_"
        },
        "underscore.string": {
            deps: ['underscore']
        },
        bootstrap: {
            deps: ["jquery"],
            exports: "bootstrap"
        },
        "bootstrap.validator": {
            deps: ["bootstrap"]
        },
        backbone: {
            deps: ["jquery", "underscore"],
            exports: "Backbone"
        },
        "backbone.marionette": {
            deps: ["backbone", "backbone.babysitter"],
            exports: "Backbone.Marionette"
        },
        "backbone.babysitter": {
            deps: ["backbone", "underscore"],
            exports: "Backbone.ChildViewContainer"
        },
        "backbone.wreqr": {
            deps: ["backbone"]
        },
        "bloodhound": {
            exports: "Bloodhound"
        },
        "codemirror": {
            exports: "CodeMirror"
        },
        "codemirror.mode.javascript": {
            deps: ["codemirror"]
        },
        "pace": {
            exports: "Pace"
        },
        "markdown": {
            exports: "markdown"
        },
        "pnglib": {
            exports: "PNGlib"
        },
        "identicon": {
            deps: ["pnglib"],
            exports: "Identicon"
        }
    }
})

require(["jquery", "bootstrap", "backbone", "javabin/app", "underscore", "underscore.string", "pace", "modernizr"], function($, bootstrap, Backbone, javabinApp, _, underscoreString, pace) {
    var options = {
        endpoint: window.location.origin + "/api/v0"
    };

    $(document).on('ajaxSend', function(elm, xhr, s) {
        if(s.url.indexOf(options.endpoint) == 0 && s.type == 'POST' || s.type == 'PUT' || s.type == 'DELETE') {
            xhr.setRequestHeader('X-Xsrftoken', window._xsrf_token);
        }
    });

    _.str = underscoreString

    pace.start({
        ajax: {
            trackMethods: ["GET", "POST", "PUT", "DELETE"]
        }
    });

    javabinApp.currentUser.options = {endpoint: options.endpoint};
    javabinApp.currentUser.fetch().then(function() {
        javabinApp.start(options);
    })
});