A neovim port of [clj-refactor.el](https://github.com/clojure-emacs/clj-refactor.el)

# Installation

### Pre-requisites
[Install node.js](https://nodejs.org)

[Install latest neovim](https://github.com/neovim/neovim/wiki/Installing-Neovim)

[Install node-host](https://github.com/neovim/node-host)

### Vundle

Using Vundle, add this to your vundle .config/nvim/init.vim section:

```
Plugin 'snoe/nvim-refactor.js'
```

### Inside nvim
- `:PluginInstall`
- `:UpdateRemotePlugins` you should see `remote/host: node host registered plugins ['nvim-refactor.js']` 
- *restart* nvim
- refactor

### Progress

- [x] [add-declaration](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/add-declaration.gif)
- [ ] [add-import](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/add-import.gif)
- [ ] [add-libspec](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/add-libspec.gif)
- [x] [add-missing-libspec](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/add-missing-libspec.gif) * Combined with magic-requires
- [ ] [add-project-dependency](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/add-project-dependency.gif)
- [ ] [add-stubs](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/add-stubs.gif)
- [x] [clean-ns](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/clean-ns.gif)
- [ ] [cljr-code-hydra](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/cljr-code-hydra.gif)
- [ ] [cljr-hydras](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/cljr-hydras.gif)
- [ ] [cljr-ns-hydra](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/cljr-ns-hydra.gif)
- [ ] [cljr-project-hydra](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/cljr-project-hydra.gif)
- [ ] [cljr-self-hydra](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/cljr-self-hydra.gif)
- [ ] [cljr-toplevel-hydra](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/cljr-toplevel-hydra.gif)
- [ ] [create-fn-from-example](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/create-fn-from-example.gif)
- [x] [cycle-coll](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/cycle-coll.gif)
- [x] [cycle-if](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/cycle-if.gif)
- [ ] [cycle-privacy](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/cycle-privacy.gif)
- [ ] [cycle-thread](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/cycle-thread.gif)
- [ ] [describe-refactor](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/describe-refactor.gif)
- [ ] [destructure-keys](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/destructure-keys.gif)
- [x] [expand-let](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/expand-let.gif)
-- * Doesn't yet replace other usages of bindings
- [ ] [extract-fn](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/extract-fn.gif)
- [ ] [find-usages](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/find-usages.gif)
- [ ] [hotload-dependency](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/hotload-dependency.gif)
- [ ] [inline-symbol](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/inline-symbol.gif)
- [x] [introduce-let](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/introduce-let.gif)
- [x] [magic-requires](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/magic-requires.gif)
- [ ] [move-form](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/move-form.gif)
- [x] [move-to-let](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/move-to-let.gif)
- [ ] [promote-fn-literal](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/promote-fn-literal.gif)
- [ ] [promote-fn](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/promote-fn.gif)
- [ ] [remove-let](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/remove-let.gif)
- [ ] [remove-unused-requires](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/remove-unused-requires.gif)
- [x] [rename-file-or-dir](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/rename-file-or-dir.gif)
- [x] [rename-symbol-global](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/rename-symbol-global.gif) * just rename-symbol
- [x] [rename-symbol-local](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/rename-symbol-local.gif) * just rename-symbol
- [ ] [replace-use](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/replace-use.gif)
- [ ] [show-changelog](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/show-changelog.gif)
- [ ] [sort-ns](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/sort-ns.gif)
- [ ] [sort-project-dependencies](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/sort-project-dependencies.gif)
- [ ] [stop-referring](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/stop-referring.gif)
- [ ] [thread-first-all](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/thread-first-all.gif)
- [ ] [thread-last-all](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/thread-last-all.gif)
- [ ] [thread-last](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/thread-last.gif)
- [ ] [thread](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/thread.gif)
- [ ] [unwind-all](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/unwind-all.gif)
- [ ] [unwind-thread](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/unwind-thread.gif)
