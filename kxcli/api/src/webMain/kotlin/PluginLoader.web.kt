package kxcli.api

public actual class PluginLoader<T : Any> {
    public actual fun load(): Sequence<T> {
        TODO("Not yet implemented")
    }

    public actual fun loadFromFiles(paths: List<String>): Sequence<T> {
        TODO("Not yet implemented")
    }

    // for workers, we can have only `suspend` (kotlinx.rpc like) interfaces
    // for js/wasmJs (browser + nodejs)
    // suspend? should return flow? Sequence<suspend () -> T>? SuspendSequence (a.k.a flow)
    public /*suspend*/ fun loadFromUrls(urls: List<String>, useWorkers: Boolean = false): Sequence<T> {
        TODO("Not yet implemented")
    }


    public actual companion object {
        public actual inline fun <reified T : Any> of(): PluginLoader<T> {
            TODO("Not yet implemented")
        }
    }
}
