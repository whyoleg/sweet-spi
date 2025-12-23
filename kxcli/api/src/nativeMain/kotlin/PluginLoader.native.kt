package kxcli.api

public actual class PluginLoader<T : Any> {
    public actual fun load(): Sequence<T> {
        TODO("Not yet implemented")
    }

    public actual fun loadFromFiles(paths: List<String>): Sequence<T> {
        TODO("Not yet implemented")
    }

    public actual companion object {
        public actual inline fun <reified T : Any> of(): PluginLoader<T> {
            TODO("Not yet implemented")
        }
    }
}
