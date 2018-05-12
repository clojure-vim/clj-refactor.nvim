try {
  require('source-map-support').install();
} catch (e) {
  // Do nothing, this is quite normal as this probably isn't installed.
}

var clj_refactor = require('./compiled.js')

module.exports = clj_refactor.clj_refactor.main._main
