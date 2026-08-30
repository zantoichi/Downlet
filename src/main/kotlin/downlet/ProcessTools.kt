package downlet

internal fun terminateProcessTree(process: Process): List<ProcessHandle> {
    val descendants = process.descendants().toList()
    descendants.forEach(ProcessHandle::destroy)
    process.destroy()
    descendants.filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly)
    if (process.isAlive) process.destroyForcibly()
    return descendants + process.toHandle()
}

internal fun awaitProcessTreeTermination(
    process: Process,
    handles: List<ProcessHandle>,
) {
    handles.filterNot { it.pid() == process.pid() }.forEach { handle ->
        if (handle.isAlive) handle.onExit().join()
    }
    process.waitFor()
}
