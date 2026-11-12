package com.github.shareddatasources

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import java.nio.file.Path

@Service
@State(
    name = "SharedDataSourcesSettings",
    storages = [Storage("sharedDataSources.xml")]
)
class SharedDataSourcesSettings : PersistentStateComponent<SharedDataSourcesSettings.State> {

    data class State(
        var sharedDirectory: String = Path.of(
            System.getProperty("java.io.tmpdir"),
            ".shared-datasources"
        ).toString(),
        var enabled: Boolean = true,
        var autoBackup: Boolean = true
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(): SharedDataSourcesSettings {
            return ApplicationManager.getApplication().getService(SharedDataSourcesSettings::class.java)
        }
    }
}
