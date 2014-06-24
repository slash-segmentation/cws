/*
 * COPYRIGHT AND LICENSE
 * 
 * Copyright 2014 The Regents of the University of California All Rights Reserved
 * 
 * Permission to copy, modify and distribute any part of this CRBS Workflow 
 * Service for educational, research and non-profit purposes, without fee, and
 * without a written agreement is hereby granted, provided that the above 
 * copyright notice, this paragraph and the following three paragraphs appear
 * in all copies.
 * 
 * Those desiring to incorporate this CRBS Workflow Service into commercial 
 * products or use for commercial purposes should contact the Technology
 * Transfer Office, University of California, San Diego, 9500 Gilman Drive, 
 * Mail Code 0910, La Jolla, CA 92093-0910, Ph: (858) 534-5815, 
 * FAX: (858) 534-7345, E-MAIL:invent@ucsd.edu.
 * 
 * IN NO EVENT SHALL THE UNIVERSITY OF CALIFORNIA BE LIABLE TO ANY PARTY FOR 
 * DIRECT, INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING 
 * LOST PROFITS, ARISING OUT OF THE USE OF THIS CRBS Workflow Service, EVEN IF 
 * THE UNIVERSITY OF CALIFORNIA HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH
 * DAMAGE.
 * 
 * THE CRBS Workflow Service PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE
 * UNIVERSITY OF CALIFORNIA HAS NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, 
 * UPDATES, ENHANCEMENTS, OR MODIFICATIONS. THE UNIVERSITY OF CALIFORNIA MAKES
 * NO REPRESENTATIONS AND EXTENDS NO WARRANTIES OF ANY KIND, EITHER IMPLIED OR 
 * EXPRESS, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE, OR THAT THE USE OF 
 * THE CRBS Workflow Service WILL NOT INFRINGE ANY PATENT, TRADEMARK OR OTHER
 * RIGHTS. 
 */

package edu.ucsd.crbs.cws.workflow.validate;

import edu.ucsd.crbs.cws.auth.User;
import edu.ucsd.crbs.cws.dao.WorkflowDAO;
import edu.ucsd.crbs.cws.dao.objectify.WorkflowObjectifyDAOImpl;
import edu.ucsd.crbs.cws.workflow.Parameter;
import edu.ucsd.crbs.cws.workflow.ParameterWithError;
import edu.ucsd.crbs.cws.workflow.Task;
import edu.ucsd.crbs.cws.workflow.WorkflowParameter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Christopher Churas <churas@ncmir.ucsd.edu>
 */
public class TaskValidatorImpl implements TaskValidator {

    private static final Logger _log
            = Logger.getLogger(TaskValidatorImpl.class.getName());
    
    static WorkflowDAO _workflowDAO = new WorkflowObjectifyDAOImpl();
    
    static TaskParametersChecker _taskParamNullChecker = new TaskParametersNullChecker();
    static TaskParametersChecker _taskParamDuplicateChecker = new TaskParametersDuplicateChecker();
    
    
    /**
     * Performs validation of {@link Task} against the {@link Workflow} the task is supposed
     * to be running.  If problems are found they will be set in the <b>error</b> methods in {@link Task}
     * object.  Namely, {@link Task#getError()} and {@link Task#getParametersWithErrors()} 
     * @param task {@link Task} to validate
     * @param user User running the {@link Task}
     * @return <b>task</b> passed in with errors set as appropriate
     * @throws Exception If the <b>task</b> is null
     */
    @Override
    public Task validateParameters(Task task,User user) throws Exception {
        if (task == null){
            throw new Exception("Task cannot be null");
        }
        
        _taskParamNullChecker.check(task);

        //iterate through all the Parameters of the task and verify there are
        // no duplicates first if there are set error
        _taskParamDuplicateChecker.check(task);
        
        //load Workflow For Task and if this has problems bail cause we need the
        // Workflow object to do anything else
        if (_workflowDAO.loadWorkflow(task,user) == null){
            return task;
        }
        
        // iterate again through all parameters and find corresponding workflowparameter
        // if no match set error
        Map<WorkflowParameter,Parameter> paramToWorkflowParamMap = linkTaskParametersWithWorkflowParameters(task);
        
        if (paramToWorkflowParamMap == null){
            return task;
        }
        
        // for match verify value is within bounds of workflow parameter if not set error
        // keep track of workflow parameters already covered and if there are
        // any extra note those in the error
        validateParameterMap(task,paramToWorkflowParamMap);
        
        return task;
    }

    /**
     * Iterates through {@link Task#getParameters()} for <b>task</b> and links up {@link Parameter} objects
     * with {@link WorkflowParameter} objects into a map that is returned.<p/>
     * This method will fail if any of the following occurs:<p/>
     * If any <b>task</b> {@link Parameter} objects don't have a matching {@link WorkflowParameter}<br/>
     * If no <b>task</b> {@link Parameter} matches a {@link WorkflowParameter} that is <b>required</b> ie {@link WorkflowParameter#getIsRequired() } == true<br/>
     * 
     * <p/>Note it is possible for {@link WorkflowParameter} objects have no corresponding
     * {@link Parameter} and this is fine as long as those {@link WorkflowParameter} objects
     * are not <b>required</b>
     * @param task Task to examine
     * @return Map with {@link WorkflowParameter} as key and {@link Parameter} as value 
     */
    private Map<WorkflowParameter,Parameter> linkTaskParametersWithWorkflowParameters(Task task){
        
        HashMap<WorkflowParameter,Parameter> linkedMap = new HashMap<>();
        WorkflowParameter wParam;

        for (Parameter p : task.getParameters()){
            
            wParam = task.getWorkflow().removeWorkflowParameterMatchingName(p.getName());
            if (wParam == null){
                task.addParameterWithError(new ParameterWithError(p,"No matching WorkflowParameter"));
            }
            else {
                linkedMap.put(wParam,p);
            }
        }
        
        if (task.getWorkflow().getParameters().isEmpty() == true){
            return linkedMap;
        }
        
        for (WorkflowParameter wParameter : task.getWorkflow().getParameters()){
            if (wParameter.getIsAdvanced()){
                task.addParameterWithError(new ParameterWithError(wParameter.getName(),null,"Required parameter not found"));
            }
        }
        return linkedMap;
    }
    
    private void validateParameterMap(Task task, Map<WorkflowParameter,Parameter> paramMap){
        
        Parameter param;
        for (WorkflowParameter wParam : paramMap.keySet()){
            param = paramMap.get(wParam);
            String res = validateParameter(wParam,param.getValue());
            if (res != null){
                _log.log(Level.INFO,"Validation error {0} {1}",new Object[]{param.asString(),res});
                task.addParameterWithError(new ParameterWithError(param,res));
            }
        }
    }
    
    private String validateParameter(WorkflowParameter parameter,final String value){
        
        if (parameter.getValidationType() == null){
            return null;
        }
        
        if (parameter.getIsRequired() == true && value == null){
            return "Required Parameter cannot be null";
        }
        else if (parameter.getValidationType().equals(WorkflowParameter.ValidationType.STRING)){
            return validateStringParameter(parameter,value);
        }
        else if (parameter.getValidationType().equals(WorkflowParameter.ValidationType.DIGITS)){
            return validateDigitsParameter(parameter,value);
        }
        else if (parameter.getValidationType().equals(WorkflowParameter.ValidationType.NUMBER)){
            return validateNumberParameter(parameter,value);
        }
        return "Unknown type of validation";
    }
    
    /**
     * Checks regular expression in {@link WorkflowParameter#getValidationRegex()} and verify
     * <b>value</b> matches 
     * @param parameter
     * @param value 
     * @return null if <b>value</b> is fine with regular expression otherwise String describing the error
     */
    private String validateStringParameter(WorkflowParameter parameter,final String value){
        return null;
    }

    private String validateNumberParameter(WorkflowParameter parameter,final String value){
        return null;
    }
    
    private String validateDigitsParameter(WorkflowParameter parameter,final String value){
        return null;
    }
}
