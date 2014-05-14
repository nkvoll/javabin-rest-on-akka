define(function(require) {
    var $ = require('jquery'),
        _ = require('underscore'),
        deparam = require('deparam');

    return $.fn.updateModifierLinks = function(currentValues, emptyPrefix) {
        if(! currentValues) {
            var slashLocation = window.location.hash.lastIndexOf('?');
            if(slashLocation >= 0) {
                currentValues = deparam(window.location.hash.substring(slashLocation + 1));
            } else {
                currentValues = {};
            }
        }

        $(this).find('a[data-modifier]').each(function() {
            var $this = $(this),
                prefix = window.location.hash.substring(0, window.location.hash.lastIndexOf('?')) || window.location.hash,
                params = _.extend({}, currentValues, deparam($this.data('modifier')));

            _(params).each(function(value, key) {
                if(value === null) {
                    delete params[key];
                }
            });

            if(! prefix)
                prefix = emptyPrefix || '';

            // Sort to get a deterministic order. We want parameters links to result in *exactly* the same link.
            var hrefWithoutParams = $this.attr('href');
            if($this.attr('href').indexOf("#") != -1) {
                hrefWithoutParams = $this.attr('href').substring($this.attr('href').indexOf("#"));
            }

            if(_.size(params) > 0) {
                $this.attr('href', [hrefWithoutParams || prefix, $.param(_(params).sort())].join('?'));
            }

            var isActive = _.str.startsWith(prefix, hrefWithoutParams)

            _(params).each(function(value, key) {
                if(! _(value).isEqual(currentValues[key])) {
                    isActive = false;
                }
            });

            if(isActive) {
                $this.addClass('active');
                $this.parents('li').first().addClass('active');
            } else {
                $this.removeClass('active');
                $this.parents('li').first().removeClass('active');
            }

        })
    };
});
