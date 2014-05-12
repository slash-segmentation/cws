/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.ucsd.crbs.cws.workflow;

import java.util.HashMap;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


/**
 *
 * @author Christopher Churas <churas@ncmir.ucsd.edu>
 */
@RunWith(JUnit4.class)
public class TestWorkflowParameter {

    public TestWorkflowParameter() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test public void testBasicGettersAndSetters(){
        WorkflowParameter wp = new WorkflowParameter();
        
        assertTrue(wp.getName() == null);
        assertTrue(wp.getDisplayName() == null);
        assertTrue(wp.getValue() == null);
        assertTrue(wp.getType() == null);
        assertTrue(wp.getHelp() == null);
        assertTrue(wp.getIsAdvanced() == false);
        assertTrue(wp.getIsRequired() == false);
        assertTrue(wp.getDelimiterValue() == null);
        assertTrue(wp.getValidationType() == null);
        assertTrue(wp.getValidationHelp() == null);
        assertTrue(wp.getMaxValue() == 0.0);
        assertTrue(wp.getMinValue() == 0.0);
        assertTrue(wp.getMaxLength() == 0L);
        assertTrue(wp.getValidationRegex() == null);
        assertTrue(wp.getMaxFileSize() == 0L);
        assertTrue(wp.getValueMap() == null);
        
        wp.setName("name");
        wp.setDisplayName("display");
        wp.setValue("value");
        wp.setType("type");
        wp.setHelp("help");
        wp.setIsAdvanced(true);
        assertTrue(wp.getIsRequired() == false);
        assertTrue(wp.getIsAdvanced() == true);
        wp.setIsRequired(true);
        assertTrue(wp.getIsRequired() == true);
        assertTrue(wp.getIsAdvanced() == true);
        wp.setIsAdvanced(false);
        assertTrue(wp.getIsRequired() == true);
        assertTrue(wp.getIsAdvanced() == false);
        wp.setDelimiterValue("delim");
        wp.setValidationType("validationtype");
        wp.setValidationHelp("validationhelp");
        double maxVal = 2.0;
        wp.setMaxValue(maxVal);
        double minVal = 3.0;
        wp.setMinValue(minVal);
        wp.setMaxLength(1L);
        wp.setValidationRegex("regex");
        wp.setMaxFileSize(2L);
        wp.setValueMap(new HashMap<String,String>());
        
        assertTrue(wp.getName().equals("name"));
        assertTrue(wp.getDisplayName().equals("display"));
        assertTrue(wp.getValue().equals("value"));
        assertTrue(wp.getType().equals("type"));
        assertTrue(wp.getHelp().equals("help"));
        assertTrue(wp.getDelimiterValue().equals("delim"));
        assertTrue(wp.getValidationType().equals("validationtype"));
        assertTrue(wp.getValidationHelp().equals("validationhelp"));
        assertTrue(wp.getMaxValue() == maxVal);
        assertTrue(wp.getMinValue() == minVal);
        assertTrue(wp.getMaxLength() == 1L);
        assertTrue(wp.getValidationRegex().equals("regex"));
        assertTrue(wp.getMaxFileSize() == 2L);
        assertTrue(wp.getValueMap().isEmpty());
        
        
    }
    
}