package io.jenkins.plugins.supabase;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Listener that converts multiple SupabaseEventChangeActions into a single visible action
 * when a build starts, so event data appears properly in the Changes section.
 */
@Extension
public class SupabaseEventChangeListener extends RunListener<Run<?, ?>> {
    
    private static final Logger LOGGER = Logger.getLogger(SupabaseEventChangeListener.class.getName());

    @Override
    public void onStarted(Run<?, ?> run, TaskListener listener) {
        // Find all SupabaseEventChangeActions in the build
        List<SupabaseEventTrigger.SupabaseEventChangeAction> changeActions = run.getActions(SupabaseEventTrigger.SupabaseEventChangeAction.class);
        
        if (changeActions != null && !changeActions.isEmpty()) {
            try {
                // Create a consolidated visible action from all change actions
                List<SupabaseEventTrigger.SupabaseEventChangeAction> allChanges = new ArrayList<>(changeActions);
                
                // Remove the invisible actions
                for (SupabaseEventTrigger.SupabaseEventChangeAction action : changeActions) {
                    run.removeAction(action);
                }
                
                // Add a single visible action that displays in Changes section
                SupabaseEventTrigger.SupabaseChangesDisplayAction displayAction = 
                    new SupabaseEventTrigger.SupabaseChangesDisplayAction(allChanges);
                run.addAction(displayAction);
                
                LOGGER.info("Added Supabase changes display action with " + allChanges.size() + " changes to build " + run.getNumber());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to add changes display action: " + e.getMessage(), e);
            }
        }
    }
}
