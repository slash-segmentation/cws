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

package edu.ucsd.crbs.cws.dao.objectify;

import com.google.appengine.api.blobstore.BlobInfo;
import com.google.appengine.api.blobstore.BlobInfoFactory;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Work;
import com.googlecode.objectify.cmd.Query;
import edu.ucsd.crbs.cws.auth.User;
import edu.ucsd.crbs.cws.dao.JobDAO;
import edu.ucsd.crbs.cws.dao.WorkflowDAO;
import static edu.ucsd.crbs.cws.dao.objectify.OfyService.ofy;
import edu.ucsd.crbs.cws.gae.WorkflowParameterDataFetcher;
import edu.ucsd.crbs.cws.gae.URLFetcherImpl;
import edu.ucsd.crbs.cws.workflow.Job;
import edu.ucsd.crbs.cws.workflow.Workflow;
import edu.ucsd.crbs.cws.workflow.WorkflowParameter;
import edu.ucsd.crbs.cws.workflow.report.DeleteWorkflowReport;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides access methods to load, modify, and save Workflow objects to Google App
 * Engine Data store using the Objectify library
 * 
 * @author Christopher Churas <churas@ncmir.ucsd.edu>
 */
public class WorkflowObjectifyDAOImpl implements WorkflowDAO {

    private static final Logger _log
            = Logger.getLogger(WorkflowObjectifyDAOImpl.class.getName());
    
    
    WorkflowParameterDataFetcher _dropDownFetcher = new URLFetcherImpl();

    private JobDAO _jobDAO = null;
    
    public WorkflowObjectifyDAOImpl(JobDAO jobDAO){
        _jobDAO = jobDAO;
    }
    
    /**
     * Via a transaction load {@link Workflow} by <b>workflowId</b> and then 
     * resave it
     * @param workflowId
     * @return Saved {@link Workflow}
     * @throws Exception 
     */
    @Override
    public Workflow resave(final long workflowId) throws Exception {
         Workflow res = ofy().transact(new Work<Workflow>() {
            @Override
            public Workflow run() {
                Workflow workflow;
                try {
                    workflow = getWorkflowById(Long.toString(workflowId),null);
                } catch (Exception ex) {
                    return null;
                }
                if (workflow == null) {
                    return null;
                }

                Key<Workflow> tKey = ofy().save().entity(workflow).now();
                return workflow;
            }
        });

        return res;
    }
    
    
    
    
    /**
     * Adds a new workflow to the data store.  If the Id of the Workflow is set then the
     * parent field of this Workflow object is set to that Id and this Workflow is given
     * a new Id.
     * 
     * @param w Workflow to add to data store
     * @return Workflow object with new id added
     * @throws Exception If there was an error during persistence
     */
    @Override
    public Workflow insert(Workflow w) throws Exception {
        if (w == null){
          throw new Exception("Workflow object passed in is null");
          }
     
        //set date if it is null
      if (w.getCreateDate() == null){
          w.setCreateDate(new Date());
      }
      
      //if version is 0 or less set it to 1
      if (w.getVersion() <= 0){
          w.setVersion(1);
      }
      
      // seems a user is uploading the same workflow twice. 
      // lets create a new workflow using the id to load
      // the old workflow and set it as the parent for this
      // new workflow
      if (w.getId() != null && w.getId().longValue() > 0){
          Workflow parentWf = ofy().load().type(Workflow.class).id(w.getId()).now();
          if (parentWf == null){
              throw new Exception("Unable to load parent Workflow with id: "+w.getId().toString());
          }
          w.setId(null);
          w.setParentWorkflow(parentWf);
          int newVersion = parentWf.getVersion()+1;
          w.setVersion(newVersion);
      }
      
      Key<Workflow> wfKey = ofy().save().entity(w).now();
      return w;
    
    }

    /**
     * Obtains all Workflows from data store, optionally removing the WorkflowParameters
     * if omitWorkflowParams is set to true
     * @param omitWorkflowParams If set to true then WorkflowParameters is set to null
     * @param showDeleted
     * @return List of Workflow objects or empty list or null
     */
    @Override
    public List<Workflow> getAllWorkflows(boolean omitWorkflowParams,
            final Boolean showDeleted) {
        /* @TODO figure out way to make objectify optionally retreive workflow parameters instead of removing them here */
        Query<Workflow> q = ofy().load().type(Workflow.class);

        if (showDeleted != null) {
            q = q.filter("_deleted", showDeleted);
        } else {
            q = q.filter("_deleted", false);
        }

        List<Workflow> workflows = q.list();

        if (omitWorkflowParams == false) {
            return workflows;
        }

        for (Workflow w : workflows) {
            w.setParameters(null);
        }
        return workflows;
    }

    /**
     * Queries Objectify to get Workflow matching id passed in.  
     * @param workflowId String containing workflow id.  Id must be greater then 0
     * @return Workflow object or null if none is found
     * @throws Exception If workflowId is null or there is an error parsing the numerical id or if there was an error retrieving from Objectify
     */
    @Override
    public Workflow getWorkflowById(final String workflowId,User user) throws Exception {
        long wfId;
        if (workflowId == null){
            throw new IllegalArgumentException("workflow id cannot be null");
        }
        try {
            wfId = Long.parseLong(workflowId);
        } catch (NumberFormatException nfe) {
            throw new NumberFormatException("Unable to parse workflow id from: " + workflowId + " : " + nfe.getMessage());
        }
        
        if (wfId <= 0){
            throw new Exception(Long.toString(wfId)+" is not a valid workflow id");
        }
        Workflow w = ofy().load().type(Workflow.class).id(wfId).now();
        
        if (w == null){
            return null;
        }
        
        for (WorkflowParameter param : w.getParameters()){
            _dropDownFetcher.fetchAndUpdate(param,user);
        }
        return w;
    }

    
    @Override
    public Workflow updateBlobKey(final long workflowId,final String key) throws Exception {
        Workflow resWorkflow;
        resWorkflow = ofy().transact(new Work<Workflow>() {
            @Override
            public Workflow run() {
                Workflow w = ofy().load().type(Workflow.class).id(workflowId).now();

                if (w == null) {
                    return null;
                }
                
                if (w.getBlobKey() == null && key == null){
                   return w;
                }
                
                if (w.getBlobKey() != null && key != null && 
                        w.getBlobKey().equals(key)){
                    return w;
                }
                w.setBlobKey(key);
                Key<Workflow> wKey = ofy().save().entity(w).now();
                return w;
            }
        });
        if (resWorkflow == null){
            throw new Exception("There was a problem updating the workflow");
        }
        return resWorkflow;
    }
    
    @Override
    public Workflow updateDeleted(final long workflowId,final boolean isDeleted) throws Exception {
        Workflow resWorkflow;
        resWorkflow = ofy().transact(new Work<Workflow>() {
            @Override
            public Workflow run() {
                Workflow w = ofy().load().type(Workflow.class).id(workflowId).now();

                if (w == null) {
                    return null;
                }
                
                if (w.isDeleted() == isDeleted){
                   return w;
                }
                
                
                w.setDeleted(isDeleted);
                Key<Workflow> wKey = ofy().save().entity(w).now();
                return w;
            }
        });
        if (resWorkflow == null){
            throw new Exception("There was a problem updating the workflow");
        }
        return resWorkflow;
    }
    

    @Override
    public Workflow getWorkflowForJob(Job job, User user) throws Exception {
        if (job == null){
            throw new IllegalArgumentException("Job cannot be null");
        }
        if (job.getWorkflow() == null){
            throw new IllegalArgumentException("No Workflow found");
        }
        if (job.getWorkflow().getId() == null){
            throw new IllegalArgumentException("Workflow id not found");
        }
        return getWorkflowById(job.getWorkflow().getId().toString(), user);
    }
    
    /**
     * Deletes {@link Workflow} identified by <b>workflowId</b> logically or
     * for real depending on value of <b>permanentlyDelete</b> parameter.<p/>
     * 
     * This method first sees if any {@link Job}s have been run on {@link Workflow}
     * if yes then {@link DeleteWorkflowReport}'s {@link DeleteWorkflowReport#isSuccessful()}
     * will be set to <b><code>false</b></code> and {@link DeleteWorkflowReport#getReason()}
     * will be set to the following:<p/>
     * 
     * <code>Cannot delete (NUMBER) jobs have been run under workflow</code>
     * 
     * @param workflowId
     * @param permanentlyDelete
     * @return {@link DeleteWorkflowReport} denoting success or failure
     * @throws Exception If there was an error querying the datastore
     */
    @Override
    public DeleteWorkflowReport delete(long workflowId, Boolean permanentlyDelete) throws Exception {

        DeleteWorkflowReport dwr = new DeleteWorkflowReport();
        dwr.setId(workflowId);
        dwr.setSuccessful(false);
        dwr.setReason("Unknown");
        
        _log.log(Level.INFO,"Checking if its possible to delete workflow {0} ",workflowId);
        
     //look for any jobs associated with workflow
     int numAssociatedJobs = _jobDAO.getJobsWithWorkflowIdCount(workflowId);
     
     //if found add to DeleteWorkflowReport and return
     if (numAssociatedJobs > 0){
        dwr.setReason("Cannot delete "+numAssociatedJobs+
                " job(s) have been run under workflow");
        return dwr;
     }

      //if permanentlyDelete is not null and true then run real delete
     if (permanentlyDelete != null && permanentlyDelete == true){
         //need to load workflow and get its blobkey if any
         Workflow w = getWorkflowById(Long.toString(workflowId), null);
         if (w.getBlobKey() != null){
             _log.log(Level.INFO,"Blob key found {0}  Deleting from blobstore",
                     w.getBlobKey());
             BlobKey bk = new BlobKey(w.getBlobKey());
             BlobInfo bInfo = new BlobInfoFactory().loadBlobInfo(bk);
             if (bInfo == null){
                 _log.log(Level.WARNING,"No BlobInfo found");
             }
             else {
                  
                 _log.log(Level.INFO,"Found file {0}",bInfo.getFilename());
             }
             BlobstoreServiceFactory.getBlobstoreService().delete(bk);
         }
         ofy().delete().type(Workflow.class).id(workflowId).now();
     }
     else {
        //else just set _deleted to true 
        updateDeleted(workflowId, true);
     }
     dwr.setSuccessful(true);
     dwr.setReason(null);
     return dwr;
    }
}

