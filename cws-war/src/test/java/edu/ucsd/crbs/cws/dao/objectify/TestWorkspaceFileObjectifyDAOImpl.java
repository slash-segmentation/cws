/*
 * COPYRIGHT AND LICENSE
 * 
 * Copyright 2015 The Regents of the University of California All Rights Reserved
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

import com.google.appengine.tools.development.testing.LocalBlobstoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import static edu.ucsd.crbs.cws.dao.objectify.OfyService.ofy;
import edu.ucsd.crbs.cws.workflow.InputWorkspaceFileLink;
import edu.ucsd.crbs.cws.workflow.Job;
import edu.ucsd.crbs.cws.workflow.WorkspaceFile;
import edu.ucsd.crbs.cws.workflow.report.DeleteReport;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;



/**
 *
 * @author Christopher Churas <churas@ncmir.ucsd.edu>
 */
@RunWith(JUnit4.class)
public class TestWorkspaceFileObjectifyDAOImpl {

    
      private final LocalServiceTestHelper _helper =
        new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig(),
        new LocalBlobstoreServiceTestConfig());
    
    public TestWorkspaceFileObjectifyDAOImpl() {
    }

    @BeforeClass
    public static void setUpClass() {
        Logger.getLogger(WorkspaceFileObjectifyDAOImpl.class.getName()).setLevel(Level.OFF);
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
        _helper.setUp();
        ofy().clear();
    }

    @After
    public void tearDown() {
        _helper.tearDown();
    }
    
    @Test
    public void testDeleteWhereWorkspaceFileNotFound() throws Exception {
        WorkspaceFileObjectifyDAOImpl workspaceFileDAO = new WorkspaceFileObjectifyDAOImpl(null,null);
        
        DeleteReport dwr = workspaceFileDAO.delete(1L, null, false);
        assertTrue(dwr != null);
        assertTrue(dwr.isSuccessful() == false);
        assertTrue(dwr.getReason().equals("WorkspaceFile not found"));
    }
    
    @Test
    public void testDeleteWhereParentJobExistsAndIgnoreParentIsFalse() throws Exception {
        JobObjectifyDAOImpl jobDAO = new JobObjectifyDAOImpl(null);
        
        WorkspaceFileObjectifyDAOImpl workspaceFileDAO = new WorkspaceFileObjectifyDAOImpl(jobDAO,null);
        WorkspaceFile wsf = new WorkspaceFile();
        Job j = new Job();
        j.setName("job");
        j = jobDAO.insert(j, true);
        
        wsf.setName("foo");
        wsf.setSourceJobId(j.getId());
        wsf = workspaceFileDAO.insert(wsf, false);
        DeleteReport dwr = workspaceFileDAO.delete(wsf.getId(), 
                null, false);
        assertTrue(dwr != null);
        assertTrue(dwr.isSuccessful() == false);
        assertTrue(dwr.getReason().equals("Cannot delete WorkspaceFile it is output of job ("+j.getId()+" "+j.getName()));
    }
    
    
    @Test
    public void testDeleteWhereWorkspaceFileIsInputForOtherJobsAndIgnoreParentIsTrue() throws Exception {
        JobObjectifyDAOImpl jobDAO = new JobObjectifyDAOImpl(null);
        InputWorkspaceFileLinkObjectifyDAOImpl inputWorkspaceDAO = new InputWorkspaceFileLinkObjectifyDAOImpl();
        
        WorkspaceFileObjectifyDAOImpl workspaceFileDAO = new WorkspaceFileObjectifyDAOImpl(jobDAO,inputWorkspaceDAO);
        WorkspaceFile wsf = new WorkspaceFile();
        Job j = new Job();
        j.setName("job");
        j = jobDAO.insert(j, true);
        
        wsf.setName("foo");
        wsf.setSourceJobId(j.getId());
        wsf = workspaceFileDAO.insert(wsf, true);
        
        Job linkedJob = new Job();
        linkedJob.setName("ha");
        linkedJob = jobDAO.insert(linkedJob, true);
        
        InputWorkspaceFileLink iwfl = new InputWorkspaceFileLink();
        iwfl.setJob(linkedJob);
        iwfl.setWorkspaceFile(wsf);
        iwfl = inputWorkspaceDAO.insert(iwfl);
        
        assertTrue("grrr",inputWorkspaceDAO.getByWorkspaceFileIdCount(wsf.getId(),null) == 1);
        
        DeleteReport dwr = workspaceFileDAO.delete(wsf.getId(), 
                null, true);
        
        assertTrue(dwr != null);
        assertTrue(dwr.isSuccessful() == false);
        assertTrue(dwr.getReason().equals("Found WorkspaceFile is linked to 1 Job(s)"));
    }
    
    @Test
    public void testDelete() throws Exception {
        JobObjectifyDAOImpl jobDAO = new JobObjectifyDAOImpl(null);
        InputWorkspaceFileLinkObjectifyDAOImpl inputWorkspaceDAO = new InputWorkspaceFileLinkObjectifyDAOImpl();
        
        WorkspaceFileObjectifyDAOImpl workspaceFileDAO = new WorkspaceFileObjectifyDAOImpl(jobDAO,
                inputWorkspaceDAO);
        WorkspaceFile wsf = new WorkspaceFile();
        
        wsf.setName("foo");
        wsf = workspaceFileDAO.insert(wsf, true);
        DeleteReport dwr = workspaceFileDAO.delete(wsf.getId(), 
                null, false);
        assertTrue(dwr != null);
        assertTrue(dwr.getReason(),dwr.isSuccessful() == true);
        assertTrue(dwr.getReason() == null);
        
        wsf = workspaceFileDAO.getWorkspaceFileById(wsf.getId().toString(), 
                null);
        assertTrue(wsf != null);
        assertTrue(wsf.getDeleted() == true);
        
        dwr = workspaceFileDAO.delete(wsf.getId(), 
                Boolean.FALSE, false);
        assertTrue(dwr != null);
        assertTrue(dwr.getReason(),dwr.isSuccessful() == true);
        assertTrue(dwr.getReason() == null);

                wsf = workspaceFileDAO.getWorkspaceFileById(wsf.getId().toString(), 
                null);
        assertTrue(wsf != null);
        assertTrue(wsf.getDeleted() == true);

        dwr = workspaceFileDAO.delete(wsf.getId(), 
                Boolean.TRUE, false);
        assertTrue(dwr != null);
        assertTrue(dwr.getReason(),dwr.isSuccessful() == true);
        assertTrue(dwr.getReason() == null);

        wsf = workspaceFileDAO.getWorkspaceFileById(wsf.getId().toString(), 
                null);
        assertTrue(wsf == null);
    }
    

}