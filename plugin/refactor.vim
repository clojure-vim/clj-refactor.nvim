autocmd FileType clojure noremap <buffer> crad :CAddDeclaration<CR>
autocmd FileType clojure noremap <buffer> cram :CAddMissingLibSpec<CR>
autocmd FileType clojure noremap <buffer> crcc :CCycleColl<CR>
autocmd FileType clojure noremap <buffer> crci :CCycleIf<CR>
autocmd FileType clojure noremap <buffer> crcp :CCyclePrivacy<CR>
autocmd FileType clojure noremap <buffer> crct :CCycleThread<CR>
autocmd FileType clojure noremap <buffer> crel :CExpandLet<CR>
autocmd FileType clojure noremap <buffer> cref :CExtractFunction 
autocmd FileType clojure noremap <buffer> crfe :CFunctionFromExample<CR>
autocmd FileType clojure noremap <buffer> cril :CIntroduceLet 
autocmd FileType clojure noremap <buffer> crml :CMoveToLet 
autocmd FileType clojure noremap <buffer> crtf :CThreadFirstAll<CR>
autocmd FileType clojure noremap <buffer> crth :CThread<CR>
autocmd FileType clojure noremap <buffer> crtl :CThreadLastAll<CR>
autocmd FileType clojure noremap <buffer> crtt :CThreadLast<CR>
autocmd FileType clojure noremap <buffer> crua :CUnwindAll<CR>
autocmd FileType clojure noremap <buffer> cruw :CUnwindThread<CR>

autocmd FileType clojure noremap <buffer> crcn :CCleanNS<CR>
autocmd FileType clojure noremap <buffer> crrd :CRenameDir 
autocmd FileType clojure noremap <buffer> crrf :CRenameFile 
autocmd FileType clojure noremap <buffer> crrs :CRenameSymbol 

autocmd FileType clojure inoremap <buffer> / /<ESC>:silent! CMagicRequires<CR>a
