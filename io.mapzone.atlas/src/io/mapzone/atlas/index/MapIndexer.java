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
package io.mapzone.atlas.index;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.core.runtime.IProgressMonitor;

import org.polymap.core.project.ILayer;
import org.polymap.core.project.IMap;
import org.polymap.core.runtime.UIJob;
import org.polymap.core.runtime.config.ConfigurationFactory;

import org.polymap.rhei.fulltext.update.UpdateableFulltextIndex;
import org.polymap.rhei.fulltext.update.UpdateableFulltextIndex.Updater;

import org.polymap.model2.runtime.UnitOfWork;
import org.polymap.p4.project.ProjectRepository;

import io.mapzone.atlas.AtlasFeatureLayer;

/**
 * 
 *
 * @author Falko Bräutigam
 */
class MapIndexer
        extends UIJob {

    private static final Log log = LogFactory.getLog( MapIndexer.class );
    
    private AtlasIndex                  atlasIndex;

    
    public MapIndexer( AtlasIndex atlasIndex ) {
        super( MapIndexer.class.getSimpleName() );
        ConfigurationFactory.inject( this );
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
        try (
            UnitOfWork uow = ProjectRepository.newUnitOfWork();
            Updater updater = ((UpdateableFulltextIndex)atlasIndex.index()).prepareUpdate();
        ){
            // start layer jobs
            IMap map = uow.entity( IMap.class, ProjectRepository.ROOT_MAP_ID );
            log.info( "Starting: " + map.label.get() );
            List<LayerIndexer> layerIndexers = new ArrayList();
            for (ILayer layer : map.layers) {
                if (AtlasFeatureLayer.of( layer ).get().isPresent()) {
                    LayerIndexer indexer = new LayerIndexer( layer, updater, atlasIndex );
                    indexer.schedule();
                    layerIndexers.add( indexer );
                }
            }
            // wait for layer jobs
            UIJob.joinJobs( layerIndexers );
            
            updater.apply();
            log.info( "Done: " + map.label.get() );
        }
    }

}
