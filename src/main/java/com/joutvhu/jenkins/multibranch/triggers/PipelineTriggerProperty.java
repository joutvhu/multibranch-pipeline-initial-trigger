package com.joutvhu.jenkins.multibranch.triggers;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import hudson.Extension;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import hudson.util.DescribableList;
import jenkins.branch.Branch;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.OrganizationFolder;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.mixin.ChangeRequestSCMHead2;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.multibranch.BranchJobProperty;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Job property to enable setting jobs to trigger when a pipeline is created or deleted.
 * In details by this, multi branch pipeline will trigger other job/jobs depending on the configuration.
 * Jobs defined in Pipeline Pre Create Jobs Trigger Field, will be triggered when a new pipeline created by branch indexing.
 * Jobs defined in Pipeline Post Create Jobs Trigger Field, will be triggered when a pipeline is deleted by branch indexing.
 * Jobs defined in the Pipeline Run Delete Jobs Trigger Field will be triggered when a Pipeline run is deleted
 * (either by explicitly deleting the run or the branch in the run.
 */
public class PipelineTriggerProperty extends AbstractFolderProperty<MultiBranchProject<?, ?>> {
    private static final Logger LOGGER = Logger.getLogger(PipelineTriggerProperty.class.getName());

    private static final String sourceBranchName = "SOURCE_BRANCH_NAME";
    private static final String targetBranchName = "TARGET_BRANCH_NAME";

    private static final int quitePeriod = 0;

    private String jobFilter = "*";
    private List<AdditionalParameter> additionalParameters = new ArrayList<>();

    /**
     * @see DataBoundConstructor
     */
    @DataBoundConstructor
    public PipelineTriggerProperty(
        String jobFilter,
        List<AdditionalParameter> additionalParameters
    ) {
        this.setJobFilter(jobFilter);
        this.setAdditionalParameters(additionalParameters);
    }

    /**
     * @see AbstractFolderPropertyDescriptor
     */
    @Extension
    public static class DescriptorImpl extends AbstractFolderPropertyDescriptor {
        /**
         * @return Property Name
         * @see AbstractFolderPropertyDescriptor
         */
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Pipeline Trigger";
        }

        /**
         * Return true if calling class is MultiBranchProject
         *
         * @param containerType See AbstractFolder
         * @return boolean
         * @see AbstractFolderPropertyDescriptor
         */
        @Override
        public boolean isApplicable(Class<? extends AbstractFolder> containerType) {
            if (WorkflowMultiBranchProject.class.isAssignableFrom(containerType))
                return true;
            else if (OrganizationFolder.class.isAssignableFrom(containerType))
                return true;
            else
                return false;
        }

        /**
         * Auto complete methods @createActionJobsToTrigger field.
         *
         * @param value Value to search in Job Full Names
         * @return AutoCompletionCandidates
         */
        public AutoCompletionCandidates doAutoCompleteCreateActionJobsToTrigger(@QueryParameter String value) {
            return this.autoCompleteCandidates(value);
        }

        /**
         * Auto complete methods @deleteActionJobsToTrigger field.
         *
         * @param value Value to search in Job Full Namesif
         * @return AutoCompletionCandidates
         */
        public AutoCompletionCandidates doAutoCompleteDeleteActionJobsToTrigger(@QueryParameter String value) {
            return this.autoCompleteCandidates(value);
        }

        /**
         * Auto complete methods @deleteActionJobsToTrigger field.
         *
         * @param value Value to search in Job Full Namesif
         * @return AutoCompletionCandidates
         */
        public AutoCompletionCandidates doAutoCompleteActionJobsToTriggerOnRunDelete(@QueryParameter String value) {
            return this.autoCompleteCandidates(value);
        }

        /**
         * Get all Job items in Jenkins. Filter them if they contain @value in Job Full names.
         * Also filter Jobs which have @Item.BUILD and @Item.READ permissions.
         *
         * @param value Value to search in Job Full Names
         * @return AutoCompletionCandidates
         */
        private AutoCompletionCandidates autoCompleteCandidates(String value) {
            AutoCompletionCandidates candidates = new AutoCompletionCandidates();
            List<Job> jobs = Jenkins.getInstance().getAllItems(Job.class);
            for (Job job : jobs) {
                String jobName = job.getFullName();
                if (jobName.contains(value.trim()) && job.hasPermission(Item.BUILD) && job.hasPermission(Item.READ))
                    candidates.add(jobName);
            }
            return candidates;
        }
    }

    /**
     * Build Jobs and pass parameter to Build
     */
    private void buildJobs(WorkflowJob workflowJob) {
        List<ParameterValue> parameterValues = new ArrayList<>();
        for (AdditionalParameter additionalParameter : this.getAdditionalParameters()) {
            parameterValues.add(new StringParameterValue(additionalParameter.getName(), additionalParameter.getValue(), "Set by MultiBranch Pipeline Plugin"));
        }
        ParametersAction parametersAction = new ParametersAction(parameterValues);
        workflowJob.scheduleBuild2(quitePeriod, parametersAction);
    }

    public void triggerActionJobs(WorkflowJob workflowJob) {
        if (!(workflowJob.getParent() instanceof WorkflowMultiBranchProject)) {
            LOGGER.log(Level.FINE, "[Multibranch Pipeline Initial Trigger] Caller Job is not child of WorkflowMultiBranchProject. Skipping.");
            return;
        }
        WorkflowMultiBranchProject workflowMultiBranchProject = (WorkflowMultiBranchProject) workflowJob.getParent();
        PipelineTriggerProperty pipelineTriggerProperty = workflowMultiBranchProject.getProperties().get(PipelineTriggerProperty.class);
        if (pipelineTriggerProperty != null) {
            if (checkJobFilter(workflowJob.getName(), pipelineTriggerProperty)) {
                pipelineTriggerProperty.buildJobs(workflowJob);
            } else {
                LOGGER.log(Level.INFO, "[Multibranch Pipeline Initial Trigger] {0} not included by the Include Filter", workflowJob.getName());
            }
        }
    }

    public String getJobFilter() {
        return jobFilter;
    }

    @DataBoundSetter
    public void setJobFilter(String jobFilter) {
        this.jobFilter = jobFilter;
    }

    private boolean checkJobFilter(String projectName, PipelineTriggerProperty pipelineTriggerProperty) {
        String wildcardDefinitions = pipelineTriggerProperty.getJobFilter();
        return Pattern.matches(convertToPattern(wildcardDefinitions), projectName);
    }

    public static String convertToPattern(String wildcardDefinitions) {
        StringBuilder quotedBranches = new StringBuilder();
        for (String wildcard : wildcardDefinitions.split(" ")) {
            StringBuilder quotedBranch = new StringBuilder();
            for (String branch : wildcard.split("(?=[*])|(?<=[*])")) {
                if (branch.equals("*")) {
                    quotedBranch.append(".*");
                } else if (!branch.isEmpty()) {
                    quotedBranch.append(Pattern.quote(branch));
                }
            }
            if (quotedBranches.length() > 0) {
                quotedBranches.append("|");
            }
            quotedBranches.append(quotedBranch);
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
