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
package io.mapzone.atlas.sheet;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.opengis.feature.Feature;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.PropertyDescriptor;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

import org.polymap.core.CorePlugin;
import org.polymap.core.data.PipelineFeatureSource;
import org.polymap.core.project.ILayer;
import org.polymap.core.runtime.event.EventManager;

import org.polymap.p4.layer.FeatureLayer;

import io.mapzone.atlas.AtlasPlugin;

/**
 * 
 *
 * @author Falko Bräutigam
 */
public class MarkdownScriptSheet {

    private static final Log log = LogFactory.getLog( MarkdownScriptSheet.class );

    public static ScriptEngineManager scriptEngineManager = new ScriptEngineManager( MarkdownScriptSheet.class.getClassLoader() );
    
    // XXX does not support quoted parentheses
    public static final Pattern  EMBEDDED_SCRIPT_PATTERN = Pattern.compile( 
            "\\$(" +                // the magic start token 
            "([a-zA-Z_\\-\\.:]+)" + // a simple variable reference: $name
            "|" +                   // OR
            "\\{([^}]+)\\}" +       // a script: ${format(name)}
            "|" +                   // OR
            "([^$]+)\\$" +          // a script: $format(name)
            ")" );

    // Invalid Groovy variable characters
    public static final Pattern  INVALID_VARIABLE_CHAR_PATTERN = Pattern.compile( "[^a-zA-Z0-9_]" ); 

    public enum LayerSheet {
        TITLE, DESCRIPTION, DETAIL;
    }
    
    public static MarkdownScriptSheet of( ILayer layer, LayerSheet sheet ) throws IOException {
        File sheetsDir = new File( CorePlugin.getDataLocation( AtlasPlugin.instance() ), "sheets" );
        File f = new File( sheetsDir, "layer" + layer.id() + "." + sheet.toString().toLowerCase() );
        return new MarkdownScriptSheet( f );
        
    }
    
    // instance *******************************************
    
    private File                f;
    
    private String              markdown;
    
    private Map<String,Object>  variables = new HashMap();


    public MarkdownScriptSheet( String markdown ) {
        this.markdown = markdown;
    }


    public MarkdownScriptSheet( File f ) throws IOException {
        this.f = f;
        this.markdown = f.exists() ? FileUtils.readFileToString( f, "UTF-8" ) : "";
    }

    /** 
     * The markdown/script source of this sheet. 
     */
    public Optional<String> text() {
        return Optional.ofNullable( StringUtils.isBlank( markdown ) ? null : markdown );
    }

    /**
     * 
     * @return this
     */
    public MarkdownScriptSheet setVariable( String name, Object value ) {
        variables.put( name, value );
        return this;
    }

    /**
     * 
     * @return this
     * @throws IllegalStateException If the given name already exists.
     */
    public MarkdownScriptSheet addVariable( String name, Object value ) {
        if (variables.put( name, value ) != null) {
            throw new IllegalStateException( "Name already exists: " + name );
        }
        return this;
    }

    
    public static String normalizeVariable( String name ) {
        return INVALID_VARIABLE_CHAR_PATTERN.matcher( name ).replaceAll( "_" );
    }
    
    
    /**
     * Returns a list of standard variables of the given layer and feature.
     * 
     * @throws Exception If something goes wrong with {@link FeatureLayer}.
     */
    public static Map<String,Class<?>> getStandardVariables( ILayer layer ) throws Exception {
        Map<String,Class<?>> result = new TreeMap();
        result.put( "layer_name", String.class );
        result.put( "layer_title", String.class );
        result.put( "layer_description", String.class );
        result.put( "layer_keywords", Collection.class );

        PipelineFeatureSource fs = FeatureLayer.of( layer ).get().get().featureSource();
        SimpleFeatureType schema = fs.getSchema();

        for (PropertyDescriptor p : schema.getDescriptors()) {
            result.put( normalizeVariable( p.getName().getLocalPart() ), p.getType().getBinding() );
        }
        return result;
    }

        
    /**
     * Sets standard variables of the given layer and feature.
     * 
     * @see #getVariables(ILayer, Feature)
     * @return this
     */
    public MarkdownScriptSheet setStandardVariables( ILayer layer, Feature feature ) {
        setVariable( "layer", layer );
        setVariable( "layer_name", layer.label.get() );
        setVariable( "layer_title", layer.label.get() );
        setVariable( "layer_description", layer.description.get() );
        setVariable( "layer_keywords", layer.keywords );
        
        for (Property p : feature.getProperties()) {
            setVariable( normalizeVariable( p.getName().getLocalPart() ), p.getValue() );
        }
        return this;
    }


    /**
     * Checks the given text for correct syntax.
     *
     * @param text The text to check for correct syntax.
     * @return The result of executing the given text.
     * @throws ScriptException 
     * @throws ParseException 
     */
    public String check( String text, IProgressMonitor monitor ) 
            throws ParseException, OperationCanceledException, ScriptException {
        return substituteVariables( text, monitor );
    }
    
    
    /**
     * Builds the markdown of this sheet by substituting variable references and
     * scripts in the text.
     */
    public String build( IProgressMonitor monitor ) 
            throws ParseException, OperationCanceledException, ScriptException {
        return substituteVariables( markdown, monitor );
    }
    
    
    /**
     * Updates the markdown/script content of this sheet.
     */
    public void update( String text, IProgressMonitor monitor ) 
            throws ParseException, OperationCanceledException, ScriptException, IOException {
        //check( text, monitor );
        FileUtils.write( f, text, "UTF-8" );
        EventManager.instance().publish( new SheetUpdateEvent( this ) );
    }
    
    
    protected String substituteVariables( String input, IProgressMonitor monitor ) throws ParseException, ScriptException {
        Matcher matcher = EMBEDDED_SCRIPT_PATTERN.matcher( input );
        StringBuilder buf = new StringBuilder( 4096 );
        int pos = 0;
        while (matcher.find()) {
            buf.append( input.substring( pos, matcher.start() ) );
            pos = matcher.end();
            
//            for (int i=0; i<=matcher.groupCount(); i++ ) {
//                log.info( "(" + i + "): " + matcher.group( i ) );
//            }
            
            // variable reference
            String replacement = null;
            if (matcher.group( 2 ) != null) {
                String name = matcher.group( 2 );
                Object value = variables.get( name );
                if (value == null) {
                    throw new ParseException( "No such variable: '" + name + "'", matcher.start()+1 );
                }
                replacement = value.toString();
            }
            // script
            else if (matcher.group( 3 ) != null || matcher.group( 4 ) != null) {
                String script = matcher.group( 3 ) != null ? matcher.group( 3 ) : matcher.group( 4 );
                Object value = executeScript( script );
                if (value == null) {
                    throw new ParseException( "Script does not return a value: " + script, matcher.start()+1 );
                }
                replacement = value.toString();
            }
            else {
                throw new RuntimeException( "Not handled: " + matcher.group() );
            }
            buf.append( replacement );
            
            // check canceld
            if (monitor.isCanceled()) {
                throw new OperationCanceledException();
            }
        }
        buf.append( input.substring( pos ) );
        return buf.toString();
    }
    
    
    protected Object executeScript( String script ) throws ScriptException {
        ScriptEngine engine = scriptEngineManager.getEngineByName( "groovy" );
        SimpleBindings bindings = new SimpleBindings( variables );
        script = "import static io.mapzone.atlas.sheet.ScriptUtils.*;\n" + script;
        return engine.eval( script, bindings );
    }
    
}
