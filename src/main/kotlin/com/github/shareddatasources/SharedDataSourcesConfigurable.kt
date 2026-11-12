package com.github.shareddatasources

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class SharedDataSourcesConfigurable : Configurable {

    private var panel: JPanel? = null
    private var directoryField: TextFieldWithBrowseButton? = null
    private var enabledCheckBox: JBCheckBox? = null
    private var autoBackupCheckBox: JBCheckBox? = null

    override fun getDisplayName(): String = "Shared DataSources"

    override fun createComponent(): JComponent {
        val settings = SharedDataSourcesSettings.getInstance().state

        directoryField = TextFieldWithBrowseButton().apply {
            text = settings.sharedDirectory
            addBrowseFolderListener(
                "Select Shared DataSources Directory",
                "Choose the directory containing shared dataSources.xml and dataSources.local.xml",
                null,
                FileChooserDescriptorFactory.createSingleFolderDescriptor()
            )
        }

        enabledCheckBox = JBCheckBox("Enable automatic sync on project open", settings.enabled)
        autoBackupCheckBox = JBCheckBox("Auto backup existing files before replacing", settings.autoBackup)

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Shared directory:", directoryField!!)
            .addComponent(enabledCheckBox!!)
            .addComponent(autoBackupCheckBox!!)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        return panel!!
    }

    override fun isModified(): Boolean {
        val settings = SharedDataSourcesSettings.getInstance().state
        return directoryField?.text != settings.sharedDirectory ||
                enabledCheckBox?.isSelected != settings.enabled ||
                autoBackupCheckBox?.isSelected != settings.autoBackup
    }

    override fun apply() {
        val settings = SharedDataSourcesSettings.getInstance()
        settings.loadState(
            SharedDataSourcesSettings.State(
                sharedDirectory = directoryField?.text ?: settings.state.sharedDirectory,
                enabled = enabledCheckBox?.isSelected ?: true,
                autoBackup = autoBackupCheckBox?.isSelected ?: true
            )
        )
    }

    override fun reset() {
        val settings = SharedDataSourcesSettings.getInstance().state
        directoryField?.text = settings.sharedDirectory
        enabledCheckBox?.isSelected = settings.enabled
        autoBackupCheckBox?.isSelected = settings.autoBackup
    }

    override fun disposeUIResources() {
        panel = null
        directoryField = null
        enabledCheckBox = null
        autoBackupCheckBox = null
    }
}
