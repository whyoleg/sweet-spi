1. support JVM + explicit registration - done
2. implement compiler plugin - done
3. check how we can support JS/Wasm-JS with JSExport
4. check how we can support Wasm via multi-module compilation

current TODO:

- [x] support top-level functions/properties
- [x] check classes with multiple constructors
    - choose `first` non-private with no arguments
    - choose `first` non-private with all default arguments
- [x] check classes with generics
- [ ] implement checkers
- [ ] support full java interop + checkers
- [ ] implement intrinsics:
    - jvm
    - klib
