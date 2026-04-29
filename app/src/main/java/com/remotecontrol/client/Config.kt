private fun lockDevice(commandId: Int) {
    socket.emit("command_result", JSONObject().apply {
        put("command_id", commandId)
        put("result", JSONObject().apply { put("status", "lock_not_supported") })
    })
}
