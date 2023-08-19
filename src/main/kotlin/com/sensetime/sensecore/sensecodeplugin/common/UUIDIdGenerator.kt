package com.sensetime.sensecore.sensecodeplugin.common

class UUIDIdGenerator : IdGenerator {
    override fun generateId(): String {
        return java.util.UUID.randomUUID().toString()
    }
}
