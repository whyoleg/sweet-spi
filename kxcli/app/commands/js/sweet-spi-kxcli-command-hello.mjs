import {
  protoOf180f3jzyo7rfj as protoOf,
  singleOrNull28acamgsnmzwe as singleOrNull,
  toString1pkumu07cwy4m as toString,
  IllegalStateException_init_$Create$2w9444nebyjns as IllegalStateException_init_$Create$,
  println2shhhgwwt4c61 as println,
  initMetadataForClassbxx6q50dy2s7 as initMetadataForClass,
  VOID3gxj6tk5isa35 as VOID,
} from './kotlin-kotlin-stdlib.mjs';
import {
  Command222l43jbm8o9s as Command,
  Companion_getInstance1s21qgfzpwsoa as Companion_getInstance,
  convertToJs29f13vbumndr0 as convertToJs,
} from './sweet-spi-kxcli-api.mjs';
//region block: imports
//endregion
//region block: pre-declaration
initMetadataForClass(HelloCommand, 'HelloCommand', HelloCommand, VOID, [Command]);
//endregion
function HelloCommand() {
}
protoOf(HelloCommand).get_name_woqyms_k$ = function () {
  return 'hello';
};
protoOf(HelloCommand).get_description_emjre5_k$ = function () {
  return 'prints `Hello, {arg}!';
};
protoOf(HelloCommand).execute_r55rj1_k$ = function (args) {
  var tmp;
  try {
    var tmp0_elvis_lhs = singleOrNull(args);
    var tmp_0;
    if (tmp0_elvis_lhs == null) {
      var message = 'Please provide a single argument';
      throw IllegalStateException_init_$Create$(toString(message));
    } else {
      tmp_0 = tmp0_elvis_lhs;
    }
    var arg = tmp_0;
    println('Hello, ' + arg + '!');
    tmp = null;
  } catch ($p) {
    var tmp_1;
    if ($p instanceof Error) {
      var cause = $p;
      tmp_1 = cause.toString();
    } else {
      throw $p;
    }
    tmp = tmp_1;
  }
  return tmp;
};
function command() {
  return convertToJs(Companion_getInstance(), new HelloCommand());
}
//region block: exports
export {
  command as command,
};
//endregion

//# sourceMappingURL=sweet-spi-kxcli-command-hello.mjs.map
