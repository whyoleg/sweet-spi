

import { instantiate } from './sweet-spi-kxcli-command-hello.uninstantiated.mjs';
import "./custom-formatters.js"

const exports = (await instantiate({

})).exports;

export const {
command,
memory,
_initialize
} = exports


