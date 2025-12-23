typedef struct C_Command {
    // Function pointer to get the name
    const char *(*get_name)(struct C_Command *self);

    // Function pointer to get the description
    const char *(*get_description)(struct C_Command *self);

    // Function pointer for the execute method
    // Returns NULL on success, or an error string on failure
    const char *(*execute)(struct C_Command *self, const char **args, int args_count);

    // cleanup memory
    void (*cleanup)(struct C_Command *self);

    // pointer to holder
    void *user_data;
} C_Command;
