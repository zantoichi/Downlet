package downlet

internal fun terminateProcessTree(process: Process) {
    val descendants = process.descendants().toList()
    descendants.forEach(ProcessHandle::destroy)
    process.destroy()
    descendants.filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly)
    if (process.isAlive) process.destroyForcibly()
}
