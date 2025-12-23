import {
  protoOf180f3jzyo7rfj as protoOf,
  initMetadataForCompanion1wyw17z38v6ac as initMetadataForCompanion,
  initMetadataForInterface1egvbzx539z91 as initMetadataForInterface,
  Unit_getInstanced3nlqte25ayu as Unit_getInstance,
} from './kotlin-kotlin-stdlib.mjs';
//region block: imports
//endregion
//region block: pre-declaration
initMetadataForCompanion(Companion);
initMetadataForInterface(Command, 'Command');
//endregion
function Companion() {
  Companion_instance = this;
}
var Companion_instance;
function Companion_getInstance() {
  if (Companion_instance == null)
    new Companion();
  return Companion_instance;
}
function Command() {
}
function convertToJs(_this__u8e3s4, command) {
  // Inline function 'kotlin.apply' call
  return jsCommand(convertToJs$lambda(command));
}
function jsCommand(getName) {
  return {getName: getName};
}
function convertToJs$lambda($command) {
  return function () {
    // Inline function 'kotlin.js.toJsString' call
    return $command.get_name_woqyms_k$();
  };
}
//region block: exports
export {
  Companion_getInstance as Companion_getInstance1s21qgfzpwsoa,
  Command as Command222l43jbm8o9s,
  convertToJs as convertToJs29f13vbumndr0,
};
//endregion

//# sourceMappingURL=sweet-spi-kxcli-api.mjs.map
