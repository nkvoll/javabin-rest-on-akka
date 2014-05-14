({
    appDir: "./javascripts",
    baseUrl: "./",
    dir: "./_build",
    mainConfigFile: 'javascripts/main.js',

    optimizeCss: "standard",
    optimize: "uglify",
    skipDirOptimize: false,
    inlineText: true,

    pragmas: {
        buildExclude: true
    },

    modules: [
        {
            name: 'main'
        }
    ]
})
