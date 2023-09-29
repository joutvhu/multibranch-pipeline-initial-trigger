package com.joutvhu.jenkins.multibranch.triggers;

import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import hudson.model.Item;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import hudson.util.DescribableList;
import jenkins.branch.MultiBranchProject;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class PipelineTriggerProperty extends AbstractFolderProperty<MultiBranchProject<?, ?>> {
    private static final Logger LOGGER = Logger.getLogger(PipelineTriggerProperty.class.getName());

    private String jobIncludeFilter = "";
    private String jobExcludeFilter = "";
    private List<AdditionalParameter> additionalParameters = new ArrayList<>();

    @DataBoundConstructor
    public PipelineTriggerProperty(
        String jobIncludeFilter,
        String jobExcludeFilter,
        List<AdditionalParameter> additionalParameters
    ) {
        this.setJobIncludeFilter(jobIncludeFilter);
        this.setJobExcludeFilter(jobExcludeFilter);
        this.setAdditionalParameters(additionalParameters);
    }

    /**
     * Build Jobs and pass parameter to Build
     */
    private void buildJobs(WorkflowJob workflowJob) {
        List<ParameterValue> parameterValues = new ArrayList<>();
        parameterValues.add(new StringParameterValue("MULTIBRANCH_JOB_TRIGGER_EVENT", "CREATE", "Set by Multibranch Pipeline Initial Trigger"));
        for (AdditionalParameter additionalParameter : this.getAdditionalParameters()) {
            parameterValues.add(new StringParameterValue(additionalParameter.getName(), additionalParameter.getValue(), "Set by Multibranch Pipeline Initial Trigger"));
        }
        ParametersAction parametersAction = new ParametersAction(parameterValues);
        workflowJob.scheduleBuild2(0, parametersAction);
    }

    public void triggerActionJobs(WorkflowJob workflowJob) {
        if (!(workflowJob.getParent() instanceof WorkflowMultiBranchProject)) {
            LOGGER.log(Level.FINE, "[Multibranch Pipeline Initial Trigger] Caller Job is not child of WorkflowMultiBranchProject. Skipping.");
            return;
        }
        WorkflowMultiBranchProject workflowMultiBranchProject = (WorkflowMultiBranchProject) workflowJob.getParent();
        PipelineTriggerProperty pipelineTriggerProperty = workflowMultiBranchProject.getProperties().get(PipelineTriggerProperty.class);
        if (pipelineTriggerProperty != null) {
            if (checkJobExcludeFilter(workflowJob.getName(), pipelineTriggerProperty)) {
                LOGGER.log(Level.INFO, "[Multibranch Pipeline Initial Trigger] {0} excluded by the Job Exclude Filter", workflowJob.getName());
            } else if (checkJobIncludeFilter(workflowJob.getName(), pipelineTriggerProperty)) {
                pipelineTriggerProperty.buildJobs(workflowJob);
            } else {
                LOGGER.log(Level.INFO, "[Multibranch Pipeline Initial Trigger] {0} not included by the Job Include Filter", workflowJob.getName());
            }
        }
    }

    public String getJobIncludeFilter() {
        return jobIncludeFilter;
    }

    @DataBoundSetter
    public void setJobIncludeFilter(String jobIncludeFilter) {
        this.jobIncludeFilter = jobIncludeFilter;
    }

    public String getJobExcludeFilter() {
        return jobExcludeFilter;
    }

    @DataBoundSetter
    public void setJobExcludeFilter(String jobExcludeFilter) {
        this.jobExcludeFilter = jobExcludeFilter;
    }

    private boolean checkJobIncludeFilter(String projectName, PipelineTriggerProperty pipelineTriggerProperty) {
        String wildcardDefinitions = pipelineTriggerProperty.getJobIncludeFilter();
        return Pattern.matches(convertToPattern(wildcardDefinitions), projectName);
    }

    private boolean checkJobExcludeFilter(String projectName, PipelineTriggerProperty pipelineTriggerProperty) {
        String wildcardDefinitions = pipelineTriggerProperty.getJobExcludeFilter();
        return Pattern.matches(convertToPattern(wildcardDefinitions), projectName);
    }

    public static String convertToPattern(String wildcardDefinitions) {
        StringBuilder quotedBranches = new StringBuilder();
        for (String wildcard : wildcardDefinitions.split(" ")) {
            StringBuilder quotedJob = new StringBuilder();
            for (String job : wildcard.split("(?=[*])|(?<=[*])")) {
                if (job.equals("*")) {
                    quotedJob.append(".*");
                } else if (!job.isEmpty()) {
                    quotedJob.append(Pattern.quote(job));
                }
            }
            if (quotedBranches.length() > 0) {
                quotedBranches.append("|");
            }
            quotedBranches.append(quotedJob);
        }
        return quotedBranches.toString();
    }

    public List<AdditionalParameter> getAdditionalParameters() {
        return additionalParameters;
    }

    @DataBoundSetter
    public void setAdditionalParameters(List<AdditionalParameter> additionalParameters) {
        if (additionalParameters == null)
            this.additionalParameters = new ArrayList<>();
        else
            this.additionalParameters = additionalParameters;
    }

    public static PipelineTriggerProperty getPipelineTriggerPropertyFromItem(Item item) {
        WorkflowMultiBranchProject workflowMultiBranchProject = (WorkflowMultiBranchProject) item.getParent();
        DescribableList<AbstractFolderProperty<?>, AbstractFolderPropertyDescriptor> properties = workflowMultiBranchProject.getProperties();
        return properties.get(PipelineTriggerProperty.class);
    }

    public static void triggerPipelineTriggerPropertyFromParentForOnCreate(Item item) {
        if (item instanceof WorkflowJob && item.getParent() instanceof WorkflowMultiBranchProject) {
            PipelineTriggerProperty pipelineTriggerProperty = getPipelineTriggerPropertyFromItem(item);
            if (pipelineTriggerProperty != null)
                pipelineTriggerProperty.triggerActionJobs((WorkflowJob) item);
            else
                LOGGER.fine(String.format("PipelineTriggerProperty is null in Item:%s", item.getFullName()));
        } else {
            LOGGER.fine(String.format("Item:%s is not instance of WorkflowJob", item.getFullName()));
        }
    }
}
