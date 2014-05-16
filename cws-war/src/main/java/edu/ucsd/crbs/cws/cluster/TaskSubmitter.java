package edu.ucsd.crbs.cws.cluster;

import edu.ucsd.crbs.cws.workflow.Task;

/**
 * Submits Task objects to local cluster via SGE
 * @author Christopher Churas <churas@ncmir.ucsd.edu>
 */
public class TaskSubmitter {
    
    TaskDirectoryCreator _directoryCreator;
    TaskCmdScriptCreator _cmdScriptCreator;
    TaskCmdScriptSubmitter _cmdScriptSubmitter;
    
    
    
    public TaskSubmitter(){
        _directoryCreator = new TaskDirectoryCreatorImpl();
        _cmdScriptCreator = new TaskCmdScriptCreatorImpl();
        _cmdScriptSubmitter = new TaskCmdScriptSubmitterImpl();
    }
    
    /**
     * Submits task to local SGE cluster.  This method creates the necessary
     * files and directories.  This method will then update the jobId value in
     * the Task and set the status to correct state.
     * @param t Task to submit
     * @return SGE Job id
     * @throws Exception If there was a problem creating or submitting the Task
     */
    public String submitTask(Task t) throws Exception {
        String taskDir = _directoryCreator.create(t);
        String cmdScript = _cmdScriptCreator.create(taskDir, t);
        return _cmdScriptSubmitter.submit(cmdScript, t);
    }
}
