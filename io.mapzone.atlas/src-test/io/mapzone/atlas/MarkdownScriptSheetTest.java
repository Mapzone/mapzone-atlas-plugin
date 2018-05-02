/* 
 * polymap.org
 * Copyright (C) 2018, the @authors. All rights reserved.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 3.0 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 */
package io.mapzone.atlas;

import static org.junit.Assert.assertEquals;

import java.util.Calendar;
import java.util.GregorianCalendar;

import org.junit.Before;
import org.junit.Test;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import io.mapzone.atlas.sheet.MarkdownScriptSheet;

/**
 * 
 *
 * @author Falko Bräutigam
 */
public class MarkdownScriptSheetTest {

    private static final Log log = LogFactory.getLog( MarkdownScriptSheetTest.class );
    
    private MarkdownScriptSheet     sheet;
    
    private IProgressMonitor        monitor;

    @Before
    public void setup() {
        sheet = new MarkdownScriptSheet( "" );
        monitor = new NullProgressMonitor();
    }
    
    @Test
    public void plainText() throws Exception {
        assertEquals( "plain text", sheet.check( "plain text", monitor ) );
        assertEquals( "", sheet.check( "", monitor ) );
    }


    @Test
    public void simpleVariables() throws Exception {
        sheet.addVariable( "test", "Test" );
        assertEquals( "pre Test suffix", sheet.check( "pre $test suffix", monitor ) );
        assertEquals( "pre Test", sheet.check( "pre $test", monitor ) );
        assertEquals( "Test", sheet.check( "$test", monitor ) );
        assertEquals( "Test Test", sheet.check( "$test $test", monitor ) );
        assertEquals( "TestTest", sheet.check( "$test$test", monitor ) );

        sheet.addVariable( "._-:", "Test" );
        assertEquals( "Test", sheet.check( "$._-:", monitor ) );
    }
    
    
    @Test
    public void simpleScripts() throws Exception {
        sheet.addVariable( "test", "Test" );
        assertEquals( "pre Test suffix", sheet.check( "pre ${test} suffix", monitor ) );
        assertEquals( "pre Test", sheet.check( "pre ${test}", monitor ) );
        assertEquals( "Test", sheet.check( "${test}", monitor ) );
        assertEquals( "TestTest", sheet.check( "${test}${test}", monitor ) );
        //assertEquals( "Test", sheet.check( "$test$", monitor ) );
        
        assertEquals( "Test!", sheet.check( "${test + '!'}", monitor ) );
    }

    @Test
    public void dateScriptUtils() throws Exception {
        Calendar now = GregorianCalendar.getInstance();
        now.set( 2018, 03, 24, 17, 10, 52 );
        sheet.addVariable( "now", now.getTime() );
        assertEquals( "24.04.2018 17:10:52", sheet.check( "${formatDateTime(now)}", monitor ) );
        assertEquals( "24.04.2018", sheet.check( "${formatDate(now)}", monitor ) );
        assertEquals( "24.04.2018", sheet.check( "${format(now)}", monitor ) );
    }
    
    @Test
    public void numberScriptUtils() throws Exception {
        sheet.addVariable( "i", new Integer( 101 ) );
        //assertEquals( "0", sheet.check( "${formatNumber(i)}", monitor ) );
        assertEquals( "101", sheet.check( "${format(i)}", monitor ) );

        sheet.addVariable( "f", new Float( 101.0f ) );
        assertEquals( "101,0", sheet.check( "${format(f)}", monitor ) );
        assertEquals( "101,00", sheet.check( "${formatNumber(f,2)}", monitor ) );
        assertEquals( "0.101,00", sheet.check( "${formatNumber(f,4,2)}", monitor ) );

        sheet.addVariable( "d", new Double( 101.0 ) );
        assertEquals( "101,0", sheet.check( "${format(d)}", monitor ) );
        assertEquals( "101,00", sheet.check( "${formatNumber(d,2)}", monitor ) );
        assertEquals( "0.101,00", sheet.check( "${formatNumber(d,4,2)}", monitor ) );
    }

    @Test
    public void defaultsScriptUtils() throws Exception {
        sheet.addVariable( "test", null );
        assertEquals( "Test", sheet.check( "${defaults(test,'Test')}", monitor ) );        

        sheet.addVariable( "empty", "" );
        assertEquals( "Test", sheet.check( "${defaultString(empty,'Test')}", monitor ) );        
    }
    
}
