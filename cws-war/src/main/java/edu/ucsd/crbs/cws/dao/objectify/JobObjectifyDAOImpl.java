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

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Work;
import com.googlecode.objectify.cmd.Query;
import edu.ucsd.crbs.cws.dao.InputWorkspaceFileLinkDAO;
import edu.ucsd.crbs.cws.dao.JobDAO;
import edu.ucsd.crbs.cws.dao.WorkspaceFileDAO;
import static edu.ucsd.crbs.cws.dao.objectify.OfyService.ofy;
import edu.ucsd.crbs.cws.workflow.InputWorkspaceFileLink;
import edu.ucsd.crbs.cws.workflow.Job;
import edu.ucsd.crbs.cws.workflow.Parameter;
import edu.ucsd.crbs.cws.workflow.Workflow;
import edu.ucsd.crbs.cws.workflow.WorkspaceFile;
import edu.ucsd.crbs.cws.workflow.report.DeleteReport;
import edu.ucsd.crbs.cws.workflow.report.DeleteReportImpl;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implements TaskDAO interface which provides means to load and save Task
 * objects to Google NoSQL data store via Objectify.
 *
 * @author Christopher Churas <churas@ncmir.ucsd.edu>
 */
public class JobObjectifyDAOImpl implements JobDAO {

    private static final Logger _log
            = Logger.getLogger(JobObjectifyDAOImpl.class.getName());
    private static final String COMMA = ",";

    private InputWorkspaceFileLinkDAO _inputWorkspaceFileLinkDAO;
    private WorkspaceFileDAO _workspaceFileDAO;
    
    
    public JobObjectifyDAOImpl(InputWorkspaceFileLinkDAO inputWorkspaceFileLinkDAO)
            {
        _inputWorkspaceFileLinkDAO = inputWorkspaceFileLinkDAO;
    }
    
    public void setWorkspaceFileDAO(WorkspaceFileDAO workspaceFileDAO){
        _workspaceFileDAO = workspaceFileDAO;
    }
    
    /**
     * In a transaction this method loads a {@link Job} with matching <b>jobId</b>
     * and resaves it to data store
     * @param jobId
     * @return
     * @throws Exception 
     */
    @Override
    public Job resave(final long jobId) throws Exception {

        Job resJob = ofy().transact(new Work<Job>() {
            @Override
            public Job run() {
                Job job;
                try {
                    job = getJobById(Long.toString(jobId));
                } catch (Exception ex) {
                    _log.log(Level.WARNING,
                            "Caught exception attempting to load job {0} : {1}",
                            new Object[]{jobId,ex.getMessage()});
                    return null;
                }
                if (job == null) {
                    return null;
                }

                Key<Job> tKey = ofy().save().entity(job).now();
                return job;
            }
        });

        if (resJob == null){
            throw new Exception("There was an error resaving job "+jobId);
        }
        return resJob;
    }
                
    @Override
    public Job getJobById(final String jobId) throws Exception {
        long jobIdAsLong;
        try {
            jobIdAsLong = Long.parseLong(jobId);
        } catch (NumberFormatException nfe) {
            throw new Exception(nfe);
        }
        return ofy().load().type(Job.class).id(jobIdAsLong).now();
    }

    /**
     * Gets {@link Job} matching <b>jobId</b> and <b>user</b>
     * @param jobId
     * @param user 
     * @return {@link Job} if the {@link Job#getOwner()} matches <b>user</b> 
     *         and neither value is null otherwise null is returned
     * @throws Exception if <b>jobId</b> is not parseable as a Long or there was 
     *         an issue with the data store
     */
    @Override
    public Job getJobByIdAndUser(String jobId, String user) throws Exception {

        if (user == null){
            return null;
        }
        
        Job job = this.getJobById(jobId);
        if (job == null){
            return null;
        }
        if (job.getOwner() == null){
            return null;
        }
        
        if (job.getOwner().equals(user)){
            return job;
        }
        return null;
    }

    private Query<Job> getJobsQuery(String owner, String status,
           Boolean notSubmittedToScheduler, boolean noParams, 
           boolean noWorkflowParams,final Boolean showDeleted) throws Exception {
         Query<Job> q = ofy().load().type(Job.class);

        if (status != null) {
            q = q.filter("_status in ", generateListFromCommaSeparatedString(status));
        }
        if (owner != null) {
            q = q.filter("_owner", owner);
        }
        if (notSubmittedToScheduler == true) {
            q = q.filter("_hasJobBeenSubmittedToScheduler", false);
        }

        if (showDeleted != null){
            q = q.filter("_deleted",showDeleted);
        }
        else {
            q = q.filter("_deleted",false);
        }
        return q;
    }
    
    @Override
    public List<Job> getJobs(String owner, String status,
           Boolean notSubmittedToScheduler, boolean noParams, 
           boolean noWorkflowParams,final Boolean showDeleted) throws Exception {
        
        Query<Job> q = getJobsQuery(owner,status,notSubmittedToScheduler,
                noParams,noWorkflowParams,showDeleted);
        
        if (noParams == false && noWorkflowParams == false) {
            return q.list();
        }

        List<Job> jobs = q.list();
        for (Job j : jobs) {
            if (noParams == true) {
                j.setParameters(null);
            }
            if (noWorkflowParams == true) {
                Workflow w = j.getWorkflow();
                if (w != null) {
                    w.setParameters(null);
                    w.setParentWorkflow(null);
                }
            }
        }
        return jobs;
    }

    @Override
    public int getJobsCount(String owner, String status, 
            Boolean notSubmittedToScheduler, boolean noParams, 
            boolean noWorkflowParams, Boolean showDeleted) throws Exception {
        Query<Job> q = getJobsQuery(owner,status,notSubmittedToScheduler,
                noParams,noWorkflowParams,showDeleted);
        return q.count();
    }
    
    

    /**
     * Creates a new Job in the data store
     *
     * @param job Job to insert
     * @param skipWorkflowCheck
     * @return Job object with id updated
     * @throws Exception
     */
    @Override
    public Job insert(Job job, boolean skipWorkflowCheck) throws Exception {
        if (job == null) {
            throw new NullPointerException("Job is null");
        }
        if (job.getCreateDate() == null) {
            job.setCreateDate(new Date());
        }

        if (skipWorkflowCheck == false) {

            if (job.getWorkflow() == null) {
                throw new Exception("Job Workflow cannot be null");
            }

            if (job.getWorkflow().getId() == null || job.getWorkflow().getId() <= 0) {
                throw new Exception("Job Workflow id is either null or 0 or less which is not valid");
            }
            //try to load the workflow and only if we get a workflow do we try to save
            //the job otherwise it is an error
            Workflow wf = ofy().load().type(Workflow.class).id(job.getWorkflow().getId()).now();
            if (wf == null) {
                throw new Exception("Unable to load Workflow for Job");
            }
        }
        
        
        Key<Job> jKey = ofy().save().entity(job).now();

        //iterate through parameters and insert
        //InputWorkspaceFileLink objects for WorkspaceFiles that are being
        //used
        if (job.getParameters() != null){
            for (Parameter p : job.getParameters()){
                if (p.isIsWorkspaceId()){
                    InputWorkspaceFileLink fileLink = new InputWorkspaceFileLink();
                    fileLink.setJob(job);
                    fileLink.setParameterName(p.getName());
                    WorkspaceFile wsf = new WorkspaceFile();
                    wsf.setId(Long.valueOf(p.getValue()));
                    fileLink.setWorkspaceFile(wsf);
                    _inputWorkspaceFileLinkDAO.insert(fileLink);
                }
            }
        }
        
        return job;
    }

    /**
     * Updates {@link Job} with id <b>jobId</b>
     * @param jobId
     * @param status
     * @param estCpu
     * @param estRunTime
     * @param estDisk
     * @param submitDate
     * @param startDate
     * @param finishDate
     * @param submittedToScheduler
     * @param schedulerJobId
     * @param deleted
     * @param error
     * @param detailedError
     * @return
     * @deprecated Please use {@link #update(edu.ucsd.crbs.cws.workflow.Job, 
     * java.lang.Boolean, java.lang.Boolean, java.lang.Long, java.lang.Long,
     * java.lang.Long) }
     * @throws Exception 
     */
    @Override
    public Job update(final long jobId, final String status, final Long estCpu,
            final Long estRunTime, final Long estDisk, final Long submitDate,
            final Long startDate, final Long finishDate,
            final Boolean submittedToScheduler,
            final String schedulerJobId,
            final Boolean deleted,
            final String error,
            final String detailedError) throws Exception {

        Job tempJob = new Job();
        tempJob.setId(jobId);
        tempJob.setStatus(status);
        if (submitDate != null){
            tempJob.setSubmitDate(new Date(submitDate));
        }
        if (startDate != null){
            tempJob.setStartDate(new Date(startDate));
        }
        if (finishDate != null){
            tempJob.setFinishDate(new Date(finishDate));
        }
        tempJob.setSchedulerJobId(schedulerJobId);
        tempJob.setError(error);
        tempJob.setDetailedError(detailedError);
        
        return update(tempJob);
    }

    @Override
    public Job update(final Job job) throws Exception {
        if (job == null){
            throw new IllegalArgumentException("Job cannot be null");
        }
        if (job.getId() == null){
            throw new Exception("Id must be set");
        }
        ofy().save().entity(job).now();
        return job;
    }
    
    /**
     * Gets {@link Job}s that were run from the <b>workflowId</b> passed in
     * @param workflowId id of {@link Workflow} that {@link Job} was run from
     * @return List of {@link Job}s that were run from <b>workflowId</b>
     * @throws Exception If there was an error querying the data store
     */
    @Override
    public List<Job> getJobsWithWorkflowId(long workflowId) throws Exception {
        Query<Job> q = ofy().load().type(Job.class);
        Workflow w = new Workflow();
        w.setId(workflowId);
        q = q.filter("_workflow", Key.create(w));
        return q.list();
    }

    /**
     * Gets number of {@link Job}s that were run from the <b>workflowId</b> passed in
     * @param workflowId id of {@link Workflow} that {@link Job} was run from
     * @return number of jobs run under the <b>workflowId</b>
     * @throws Exception If there was an error querying the data store
     */
    @Override
    public int getJobsWithWorkflowIdCount(long workflowId) throws Exception {
        Query<Job> q = ofy().load().type(Job.class);
        Workflow w = new Workflow();
        w.setId(workflowId);
        return q.filter("_workflow", Key.create(w)).count();
    }

    @Override
    public DeleteReport delete(long jobId, Boolean permanentlyDelete) throws Exception {
        DeleteReportImpl dwr = new DeleteReportImpl();
        dwr.setId(jobId);
        dwr.setSuccessful(false);
        dwr.setReason("Unknown");
        
        Job job = this.getJobById(Long.toString(jobId));
        if (job == null){
            dwr.setReason("Job not found");
            return dwr;
        }
        _log.log(Level.INFO,"Checking if its possible to delete Job {0}",
                jobId);
        
        List<WorkspaceFile> wsfList = _workspaceFileDAO.getWorkspaceFilesBySourceJobId(jobId);
        if (wsfList != null && !wsfList.isEmpty()){
            if (wsfList.size() != 1){
                dwr.setReason("Found "+wsfList.size()+
                        " WorkspaceFiles as output for Job, but expected 1");
                return dwr;
            }
            
            DeleteReport dr = _workspaceFileDAO.delete(wsfList.get(0).getId(), 
                        permanentlyDelete, true);
            if (dr.isSuccessful() == false){
                dwr.setReason("Unable to delete Workspace File ("+dr.getId()+
                        ") : "+dr.getReason());
                return dwr;
            }
        }
        if (permanentlyDelete != null && permanentlyDelete == true){
            ofy().delete().type(Job.class).id(job.getId()).now();
        }
        else {
            job.setDeleted(true);
            update(job);
        }
        dwr.setSuccessful(true);
        dwr.setReason(null);
        
        return dwr;
    }
    
    
    
    
    private List<String> generateListFromCommaSeparatedString(final String val) {
        return Arrays.asList(val.split(COMMA));
    }
}
