package kxcli.api

public interface Command {
    public val name: String
    public val description: String

    // null -> success
    // string -> error
    public fun execute(args: List<String>): String?
}
