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
package io.mapzone.atlas.ui;

import java.util.Collections;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.polymap.core.data.feature.FeatureRenderProcessor2;
import org.polymap.core.data.pipeline.Pipeline;
import org.polymap.core.data.pipeline.PipelineProcessorSite;
import org.polymap.core.data.pipeline.ProcessorDescription;
import org.polymap.core.project.ILayer;

import org.polymap.p4.map.ProjectLayerProvider;

/**
 * Builds layers of {@link AtlasMapPanel#mapViewer}.
 *
 * @author Falko Bräutigam
 */
public class AtlasMapLayerProvider
        extends ProjectLayerProvider {

    private static final Log log = LogFactory.getLog( AtlasMapLayerProvider.class );

    @Override
    protected Pipeline createPipeline( String layerName ) {
        Pipeline pipeline = super.createPipeline( layerName );
        
        // add filter processor
        try {
            FeatureRenderProcessor2 renderProc = (FeatureRenderProcessor2)pipeline.getLast().processor();
            Pipeline featurePipeline = renderProc.pipeline();
            
            ILayer layer = layers.get( layerName );
            Map<String,Object> props = Collections.singletonMap( "layer", layer );
            ProcessorDescription filterProc = new ProcessorDescription( FilterFeatureProcessor.class, props );
            filterProc.processor().init( new PipelineProcessorSite( props ) );

            featurePipeline.add( featurePipeline.length()-1, filterProc );
            log.info( "Pipeline: " + featurePipeline );
            
            return pipeline;
        }
        catch (Exception e) {
            throw new RuntimeException( e );
        }
    }
    
}
