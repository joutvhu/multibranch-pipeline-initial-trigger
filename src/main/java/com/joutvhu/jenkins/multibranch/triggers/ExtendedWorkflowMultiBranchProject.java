package com.joutvhu.jenkins.multibranch.triggers;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.listeners.ItemListener;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowBranchProjectFactory;

public class ExtendedWorkflowMultiBranchProject extends WorkflowBranchProjectFactory {
    @Extension
    public static class ItemListenerImpl extends ItemListener {
        @Override
        public void onCreated(Item item) {
            super.onCreated(item);
            PipelineTriggerProperty.triggerPipelineTriggerPropertyFromParentForOnCreate(item);
        }
    }
}
