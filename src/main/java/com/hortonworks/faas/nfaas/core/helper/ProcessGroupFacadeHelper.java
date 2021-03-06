package com.hortonworks.faas.nfaas.core.helper;

import com.hortonworks.faas.nfaas.config.EntityState;
import org.apache.nifi.web.api.dto.ProcessGroupDTO;
import org.apache.nifi.web.api.dto.VariableDTO;
import org.apache.nifi.web.api.entity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Configuration
public class ProcessGroupFacadeHelper extends BaseFacadeHelper {

    private static final Logger logger = LoggerFactory.getLogger(ProcessGroupFacadeHelper.class);

    @Autowired
    RemoteProcessGroupFacadeHelper remoteProcessGroupFacadeHelper;


    /**
     * This is the method to stop and un-deploy the process group.
     *
     * @param processGroupFlowEntity
     */
    public void stopAndUnDeployProcessGroup(ProcessGroupFlowEntity processGroupFlowEntity, String pgId) {
        logger.info("stopAndUnDeployProcessGroup Starts for --> " + pgId);
        ProcessGroupFlowEntity pgfe = processorGroupFlowFacadeHelper.stopProcessGroupComponents(processGroupFlowEntity, null, pgId);
        logger.info(pgfe.toString());
        remoteProcessGroupFacadeHelper.disableRemoteProcessGroup(pgId);
        ProcessGroupEntity pge = processGroup.getLatestProcessGroupEntity(pgId);
        pge = this.deleteProcessGroup(pge);
        logger.info("stopAndUnDeployProcessGroup Ends for --> " + pgId);
    }

    /**
     * This is the method to stop the process group.
     *
     * @param processGroupFlowEntity
     */
    public void stopProcessGroup(ProcessGroupFlowEntity processGroupFlowEntity, String pgId) {
        logger.info("stopProcessGroup Starts for --> " + pgId);
        ProcessGroupFlowEntity pgfe = processorGroupFlowFacadeHelper.stopProcessGroupComponents(processGroupFlowEntity, null, pgId);
        logger.info(pgfe.toString());
        ProcessGroupEntity pge = processGroup.getLatestProcessGroupEntity(pgId);
        logger.info("stopProcessGroup Ends for --> " + pge.getComponent().getName());
    }


    public void checkParentProcessGroupStatus(ProcessGroupEntity pge, String state) {
        int count = 0;
        int innerCount = 0;

        while (true && count < WAIT_IN_SEC) {

            Set<ProcessGroupEntity> processGroups = new LinkedHashSet<>();
            processGroups.add(pge);

            int queuedCount = 0;
            for (ProcessGroupEntity processGroupEntity : processGroups) {
                if (state.equalsIgnoreCase(EntityState.STOPPED.getState())) {
                    queuedCount = Integer
                            .parseInt(processGroupEntity.getStatus().getAggregateSnapshot().getQueuedCount().replaceAll(",", ""));
                    // Check for the Runing count
                    if (processGroupEntity.getRunningCount() > 0) {
                        break;
                    }
                    // Check for the queue content
                    if (queuedCount > 0) {
                        flowFileQueues.deleteTheQueueContent(processGroupEntity);
                        break;
                    }

                }

                if (state.equalsIgnoreCase(EntityState.RUNNING.getState())
                        && processGroupEntity.getStoppedCount() > 0) {
                    break;
                }

                innerCount++;
            }

            if (processGroups.size() == innerCount) {
                break;
            }

            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {

            }
            pge = processGroup.getLatestProcessGroupEntity(pge.getId());
            count++;
            innerCount = 0;
        }

    }

    /**
     * Method is used to get the template ID
     *
     * @param processGroupEntity
     * @return
     */
    public String getTemplateId(ProcessGroupEntity processGroupEntity) {
        try {

            String templateId = templateFacadeHelper.checkTemplateExist();
            if (templateId != null)
                template.deleteTemplate(templateId);

            TemplateEntity templateEntity = template.uploadTemplate(processGroupEntity);

            return templateEntity.getTemplate().getId();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return "";
    }


    /**
     * Delete the Process group
     *
     * @param pge
     * @return
     */
    public ProcessGroupEntity deleteProcessGroup(ProcessGroupEntity pge) {
        return processGroup.deleteProcessGroup(pge, EntityState.DELETE.getState());
    }

    /**
     * This is the method to stop and un-deploy the process group.
     *
     * @param processGroupFlowEntity
     */
    public void deleteProcessGroup(ProcessGroupFlowEntity processGroupFlowEntity, String pgId) {
        logger.info("deleteProcessGroup Starts for --> " + pgId);
        ProcessGroupEntity pge = processGroup.getLatestProcessGroupEntity(pgId);
        pge = deleteProcessGroup(pge);
        logger.info("deleteProcessGroup Ends for --> " + pgId);
    }

    /**
     * This is the method to get all the processGroupEntity from the pgfe
     *
     * @param pgfe
     * @param processGroupsFromTemplate
     * @return
     */
    public Set<ProcessGroupEntity> getProcessGroupEntityForUndeploy(ProcessGroupFlowEntity pgfe,
                                                                    Set<ProcessGroupDTO> processGroupsFromTemplate) {

        Set<ProcessGroupEntity> resultProcessGroups = new LinkedHashSet<>();
        Set<ProcessGroupEntity> allProcessGroups = pgfe.getProcessGroupFlow().getFlow().getProcessGroups();

        Set<String> processGroupNameFromTemplate = getAllProcessGroupNameFromTemplate(processGroupsFromTemplate);

        for (ProcessGroupEntity pge : allProcessGroups) {
            if (processGroupNameFromTemplate.contains(pge.getComponent().getName())) {
                resultProcessGroups.add(pge);
            }

        }
        return resultProcessGroups;
    }


    public Set<String> getAllProcessGroupNameFromTemplate(Set<ProcessGroupDTO> processGroupsFromTemplate) {
        Set<String> processGroupNameFromTemplate = new LinkedHashSet<>();

        for (ProcessGroupDTO processGroupDTO : processGroupsFromTemplate) {
            processGroupNameFromTemplate.add(processGroupDTO.getName());
        }

        return processGroupNameFromTemplate;
    }

    public ProcessGroupEntity getProcessGroupEntityByName(ProcessGroupFlowEntity pgfe,
                                                          String pgName) {

        ProcessGroupEntity resultPge = null;
        Set<ProcessGroupEntity> allProcessGroups = pgfe.getProcessGroupFlow().getFlow().getProcessGroups();

        for (ProcessGroupEntity pge : allProcessGroups) {
            if (pgName.equalsIgnoreCase(pge.getComponent().getName())) {
                resultPge = pge;
                break;
            }

        }

        return resultPge;

    }

    public VariableRegistryEntity updadeVariableRegistry(ProcessGroupEntity pge, Map<String,String> sqlMap) {
        VariableRegistryEntity vre  = processGroup.getVariableRegistry(pge);
        Set<VariableEntity> variables = vre.getVariableRegistry().getVariables();

        String value = "";
        VariableDTO varDto = null;

        for(VariableEntity variable : variables){
            varDto = variable.getVariable();
            value = sqlMap.get(varDto.getName());

            if(value == null || value.trim() == "")
                continue;

            varDto.setValue(value);
        }

        return  processGroup.updateVariableRegistry(pge.getId(), vre);
    }

    public VariableRegistryEntity createOrUpdadeVariableRegistry(ProcessGroupEntity pge, Map<String,String> paramMap) {
        VariableRegistryEntity vre  = processGroup.getVariableRegistry(pge);
        Set<VariableEntity> variables = vre.getVariableRegistry().getVariables();

        VariableDTO varDto = null;
        String value = "";

        boolean found = false;

        for(String key : paramMap.keySet()){
            found = false;
            for(VariableEntity variable : variables){
                varDto = variable.getVariable();
                if(key.equalsIgnoreCase(varDto.getName())){
                    found = true;
                    varDto = variable.getVariable();
                    value = paramMap.get(varDto.getName());

                    if(value != null) {
                        varDto.setValue(value);
                    }

                    break;
                }
            }

            if(!found){
                VariableEntity entity = new VariableEntity();
                VariableDTO variable = new VariableDTO();

                variable.setName(key);
                variable.setValue(paramMap.get(key));
                variable.setProcessGroupId(pge.getId());

                entity.setVariable(variable);
                variables.add(entity);
            }

        }

        vre.getVariableRegistry().setVariables(variables);
        return  processGroup.updateVariableRegistry(pge.getId(), vre);
    }
}
