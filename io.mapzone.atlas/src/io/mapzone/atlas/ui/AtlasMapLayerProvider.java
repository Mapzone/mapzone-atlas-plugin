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
package io.mapzone.atlas.ui;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.polymap.p4.map.ProjectLayerProvider;

/**
 * Builds layers of {@link AtlasMapPanel#mapViewer}.
 *
 * @author Falko Bräutigam
 */
public class AtlasMapLayerProvider
        extends ProjectLayerProvider {

    private static final Log log = LogFactory.getLog( AtlasMapLayerProvider.class );

//    @Override
//    protected Pipeline createPipeline( String layerName ) {
//        try {
//            ILayer layer = layers.get( layerName );
//            Pipeline pipeline = super.createPipeline( layerName );
//
//            // AtlasFeatureLayer?
//            Optional<AtlasFeatureLayer> afl = AtlasFeatureLayer.of( layer ).get();
//            if (afl.isPresent()) {
//                // -> add filter processor
//                FeatureRenderProcessor2 renderProc = (FeatureRenderProcessor2)pipeline.getLast().processor();
//                Pipeline featurePipeline = renderProc.pipeline();
//
//                Map<String,Object> props = Collections.singletonMap( "layer", layer );
//                ProcessorDescriptor filterProc = new ProcessorDescriptor( FulltextFilterProcessor.class, props );
//                filterProc.processor().init( new PipelineProcessorSite( props ) );
//
//                featurePipeline.add( featurePipeline.length()-1, filterProc );
//                log.info( "Pipeline: " + featurePipeline );
//            }
//            return pipeline;
//        }
//        catch (Exception e) {
//            throw new RuntimeException( e );
//        }
//    }
    
}
