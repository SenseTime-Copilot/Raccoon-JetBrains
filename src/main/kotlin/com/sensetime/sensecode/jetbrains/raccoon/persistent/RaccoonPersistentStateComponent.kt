package com.sensetime.sensecode.jetbrains.raccoon.persistent

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonPlugin


internal abstract class RaccoonPersistentStateComponent<T : RaccoonPersistentStateComponent.State>(
    private val initialState: T
) : PersistentStateComponentWithModificationTracker<T> {
    abstract class State : BaseState() {
        var version by string("")
    }

    @Volatile
    private var _state: T = initialState
        set(value) {
            field = value
            field.version = RaccoonPlugin.getVersion()
        }

    final override fun getState() = _state.also {
        LOG.trace { "getState: $it" }
    }

    final override fun getStateModificationCount() = _state.modificationCount

    override fun loadState(state: T) {
        LOG.trace { "loadState[before]: ${this._state}" }
        this._state = state
        LOG.trace { "loadState[after]: ${this._state}" }
    }

    fun restore() {
        loadState(initialState)
    }

    companion object {
        private val LOG = logger<RaccoonPersistentStateComponent<*>>()
    }
}
