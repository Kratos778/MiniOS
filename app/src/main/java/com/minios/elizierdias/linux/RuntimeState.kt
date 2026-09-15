package com.minios.elizierdias.linux

/** Estados explicitos do Runtime Core (plano §7). */
enum class RuntimeState {
    STOPPED,
    STARTING,
    RUNNING,
    DEGRADED,
    RESTARTING,
    STOPPING,
    ERROR,
}

enum class ProcessState {
    CREATED,
    STARTING,
    RUNNING,
    EXITED,
    KILLED,
    ERROR,
}

enum class VncState {
    VNC_STOPPED,
    VNC_STARTING,
    VNC_READY,
    VNC_RESTARTING,
    VNC_ERROR,
}
