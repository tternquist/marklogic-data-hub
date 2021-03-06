package com.marklogic.gradle.task

import com.marklogic.gradle.exception.EntityNameRequiredException
import com.marklogic.gradle.exception.FlowNameRequiredException
import com.marklogic.hub.flow.CodeFormat
import com.marklogic.hub.flow.DataFormat
import com.marklogic.hub.flow.FlowType
import com.marklogic.hub.scaffold.Scaffolding

abstract class CreateFlowTask extends HubTask {

    void createFlow(FlowType flowType) {
        def entityName = project.hasProperty("entityName") ? project.property("entityName") : null
        if (entityName == null) {
            throw new EntityNameRequiredException()
        }
        def flowName = project.hasProperty("flowName") ? project.property("flowName") : null
        if (flowName == null) {
            throw new FlowNameRequiredException()
        }

        def pluginFormat = project.hasProperty("pluginFormat") ?
            CodeFormat.getCodeFormat(project.property("pluginFormat")) : CodeFormat.JAVASCRIPT

        def dataFormatStr = project.hasProperty("dataFormat") ?
            project.property("dataFormat") : "json"

        def dataFormat = null
        switch(dataFormatStr) {
            case "json":
                dataFormat = DataFormat.JSON
            break
            case "xml":
                dataFormat = DataFormat.XML
            break
            default:
                println "invalid dataFormat: " + dataFormatStr
                return
        }

        def projectDir = getHubConfig().projectDir
        Scaffolding scaffolding = new Scaffolding(projectDir, getFinalClient())
        println "Creating an " + pluginFormat + " " + flowType + " flow named " + flowName + " for entity " + entityName
        scaffolding.createFlow(entityName, flowName, flowType, pluginFormat, dataFormat)
    }
}
