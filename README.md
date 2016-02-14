A neovim port of [clj-refactor.el](https://github.com/clojure-emacs/clj-refactor.el)

# Usage 

All commands are mapped under the `cr` prefix and use a two letter mnemonic shortcut. E.g. `crrs` for `Clojure Refactor Rename Symbol`.The full list is below.

# Installation

### Pre-requisites
[Install node.js](https://nodejs.org)

[Install latest neovim](https://github.com/neovim/neovim/wiki/Installing-Neovim)

[Install node-host](https://github.com/neovim/node-host)

[Install refactor-nrepl](https://github.com/clojure-emacs/refactor-nrepl)

[Install vim-fireplace](https://github.com/tpope/vim-fireplace)

### Vundle

Using Vundle, add this to your vundle .config/nvim/init.vim section:

`Plugin 'snoe/clj-refactor.nvim'`

### Inside nvim

- run `:PluginInstall`
- `:UpdateRemotePlugins` you should see `remote/host: node host registered plugins ['clj-refactor.nvim']` 
- *restart* nvim
- refactor

# Progress

## Options
You can set `g:clj_refactor_prune_ns_form` and `g:clj_refactor_prefix_rewriting` to `0` to affect the corresponding [middleware options](https://github.com/clojure-emacs/refactor-nrepl#configuration). Both default to `1`.

## Passive abilities

- [x] [Magic requires](https://github.com/clojure-emacs/clj-refactor.el/wiki#magic-require://github.com/clojure-emacs/clj-refactor.el/wiki#magic-requires) - experimental `autocmd FileType clojure inoremap <buffer> / /<ESC>:silent! CMagicRequires<CR>a`
- [ ] [Automatic insertion of namespace declaration](https://github.com/clojure-emacs/clj-refactor.el/wiki#automatic-insertion-of-namespace-declaration)

## Commands

- [x] `crad` [add-declaration](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/add-declaration.gif)
- [ ] [add-import](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/add-import.gif)
- [ ] [add-libspec](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/add-libspec.gif)
- [x] `cram` [add-missing-libspec](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/add-missing-libspec.gif)
- [ ] [add-project-dependency](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/add-project-dependency.gif)
- [ ] [add-stubs](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/add-stubs.gif)
- [x] `crcn` [clean-ns](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/clean-ns.gif)
- [x] `crfe` [create-fn-from-example](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/create-fn-from-example.gif)
- [x] `crcc` [cycle-coll](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/cycle-coll.gif)
- [x] `crci` [cycle-if](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/cycle-if.gif)
- [x] `crcp` [cycle-privacy](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/cycle-privacy.gif)
- [x] `crct` [cycle-thread](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/cycle-thread.gif)
- [ ] [describe-refactor](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/describe-refactor.gif)
- [ ] [destructure-keys](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/destructure-keys.gif)
- [x] `crel` [expand-let](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/expand-let.gif) * Doesn't yet replace other usages of bindings
- [x] `cred` [extract-def](https://github.com/clojure-emacs/clj-refactor.el/wiki/cljr-extract-def)
- [x] `cref` [extract-fn](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/extract-fn.gif)
- [ ] [find-usages](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/find-usages.gif)
- [ ] [hotload-dependency](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/hotload-dependency.gif)
- [ ] [inline-symbol](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/inline-symbol.gif)
- [x] `cril` [introduce-let](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/introduce-let.gif)
- [ ] [move-form](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/move-form.gif)
- [x] `crml` [move-to-let](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/move-to-let.gif)
- [ ] [promote-fn-literal](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/promote-fn-literal.gif)
- [ ] [promote-fn](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/promote-fn.gif)
- [ ] [remove-let](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/remove-let.gif)
- [ ] [remove-unused-requires](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/remove-unused-requires.gif)
- [x] `crrf` `crrd` [rename-file-or-dir](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/rename-file-or-dir.gif)
- [x] `crrs` [rename-symbol-global](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/rename-symbol-global.gif) * just rename symbol
- [x] `crrs` [rename-symbol-local](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/rename-symbol-local.gif) * just rename symbol
- [ ] [replace-use](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/replace-use.gif)
- [ ] [show-changelog](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/show-changelog.gif)
- [ ] [sort-project-dependencies](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/sort-project-dependencies.gif)
- [ ] [stop-referring](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/stop-referring.gif)
- [x] `crtf` [thread-first-all](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/thread-first-all.gif)
- [x] `crtl` [thread-last-all](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/thread-last-all.gif)
- [x] `crtt` [thread-last](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/thread-last.gif)
- [x] `crth` [thread](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/thread.gif)
- [x] `crua` [unwind-all](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/unwind-all.gif)
- [x] `cruw` [unwind-thread](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/unwind-thread.gif)

# Development / Testing

Run `lein npm install`

I generally have 4 terminals open:

- `$ rlwrap lein figwheel`
- `$ node target/out/tests.js`
- `$ lein cljsbuild auto plugin`
- `$ tail -f $NEOVIM_JS_DEBUG`

Somewhere in your environment do `export NEOVIM_JS_DEBUG=~/nvimdebug.log` and neovim will dump messages from the plugin there. If something goes wrong it will likely show up in `~/.nvimlog`

