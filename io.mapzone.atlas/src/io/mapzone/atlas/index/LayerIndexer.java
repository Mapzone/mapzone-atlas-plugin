/* 
 * polymap.org
 * Copyright (C) 2017-2018, the @authors. All rights reserved.
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
package io.mapzone.atlas.index;

import java.util.Optional;

import org.geotools.data.FeatureSource;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.json.JSONObject;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.core.runtime.IProgressMonitor;

import org.polymap.core.project.ILayer;
import org.polymap.core.runtime.UIJob;

import org.polymap.rhei.fulltext.update.UpdateableFulltextIndex.Updater;

import org.polymap.p4.layer.FeatureLayer;

/**
 * 
 *
 * @author Falko Bräutigam
 */
class LayerIndexer
        extends UIJob {

    private static final Log log = LogFactory.getLog( LayerIndexer.class );

    private ILayer              layer;
    
    private Updater             updater;

    private AtlasIndex          atlasIndex;
    
    
    public LayerIndexer( ILayer layer, Updater updater, AtlasIndex atlasIndex ) {
        super( LayerIndexer.class.getSimpleName() + ": " + layer.label.get() );
        this.updater = updater;
        this.layer = layer;
        this.atlasIndex = atlasIndex;
    }


//    @Override
//    protected IStatus run( IProgressMonitor monitor ) {
//        try {
//            runWithException( monitor );
//            return monitor.isCanceled() ? Status.CANCEL_STATUS : Status.OK_STATUS;
//        }
//        catch (Exception e) {
//            throw new RuntimeException( e );
//        }
//    }

    
    @Override
    protected void runWithException( IProgressMonitor monitor ) throws Exception {
        Optional<FeatureLayer> fl = FeatureLayer.of( layer ).get();
        if (fl.isPresent()) {
            FeatureSource fs = fl.get().featureSource();
            FeatureCollection features = fs.getFeatures();
            monitor.beginTask( layer.label.get(), IProgressMonitor.UNKNOWN /*features.size()*/ );
            try (
                FeatureIterator it = features.features();
            ){
                int count = 0;
                for (;it.hasNext(); count++) {
                    JSONObject json = atlasIndex.transform( it.next() );
                    updater.store( json, true );
                    monitor.worked( 1 );
                }
                log.info( "indexed: " + count );
            }
            monitor.done();
        }
    }
    
}
