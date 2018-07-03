/* 
 * polymap.org
 * Copyright (C) 2017, the @authors. All rights reserved.
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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.geotools.data.Query;
import org.opengis.filter.Filter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.polymap.core.project.ILayer;
import org.polymap.core.runtime.config.Concern;
import org.polymap.core.runtime.config.Config;
import org.polymap.core.runtime.config.Configurable;
import org.polymap.core.runtime.config.DefaultBoolean;
import org.polymap.core.runtime.session.SessionContext;
import org.polymap.core.runtime.session.SessionSingleton;

import org.polymap.p4.layer.FeatureLayer;

/**
 * Carries ...
 *
 * @author Falko Bräutigam
 */
public class AtlasFeatureLayer
        extends Configurable {

    private static final Log log = LogFactory.getLog( AtlasFeatureLayer.class );
    
    /** A proto-type instance used to check equality of {@link Config properties}. */
    public static final AtlasFeatureLayer   TYPE = new AtlasFeatureLayer( null );
        
    /**
     * Returns the one and only {@link AtlasFeatureLayer} for the given
     * {@link ILayer} in the current {@link SessionContext}. Computes a new instance
     * if not yet present.
     *
     * @see FeatureLayer#of(ILayer)
     * @return Newly created or cached instance.
     */
    public static AtlasFeatureLayer of( ILayer layer ) {
        SessionHolder session = SessionHolder.instance( SessionHolder.class ); 
        return session.instances.computeIfAbsent( layer.id(), key -> {
            log.info( "Creating AtlasFeatureLayer for: " + layer.label.get() );
            return new AtlasFeatureLayer( layer );
        });
    }
    
    /**
     * The fulltext query and map extent commonly used to filter the features of all
     * layers of the current session.
     */
    public static AtlasQuery sessionQuery() {
        return SessionHolder.instance( SessionHolder.class ).atlasQuery;
    }

    protected static class SessionHolder
            extends SessionSingleton {
        
        ConcurrentMap<String,AtlasFeatureLayer> instances = new ConcurrentHashMap( 32 );
        
        AtlasQuery                  atlasQuery = new AtlasQuery();
    }

    
    // instance *******************************************
        
    private ILayer                  layer;

    /** Layer is visible in the map. */
    @DefaultBoolean( false )
    @Concern( PropertyChangeEvent.Fire.class )
    public Config<Boolean>          visible;
    
    
//    protected AtlasFeatureLayer() {
//    }
    
    protected AtlasFeatureLayer( ILayer layer ) {
        this.layer = layer;
    }
    
    public CompletableFuture<Optional<FeatureLayer>> featureLayer() {
        return FeatureLayer.of( layer );
    }

    /** 
     * Shortcut to {@link #featureLayer}.layer(). 
     */
    public ILayer layer() {
        return layer;
    }

    
    /**
     * Builds a {@link Filter} for this layer using the currently defined
     * {@link #atlasQuery}.
     *
     * @return Newly created {@link Query}.
     * @throws Exception
     */
    public Filter fulltextFilter() throws Exception {
        return sessionQuery().fulltextFilterOf( layer );
    }

}
