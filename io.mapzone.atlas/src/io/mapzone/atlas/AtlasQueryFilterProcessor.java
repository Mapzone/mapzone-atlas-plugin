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
package io.mapzone.atlas;

import java.util.List;

import org.geotools.data.DataAccess;
import org.geotools.data.Query;
import org.opengis.filter.Filter;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.polymap.core.data.DataPlugin;
import org.polymap.core.data.feature.DefaultFeaturesProcessor;
import org.polymap.core.data.feature.FeaturesProducer;
import org.polymap.core.data.feature.GetBoundsRequest;
import org.polymap.core.data.feature.GetBoundsResponse;
import org.polymap.core.data.feature.GetFeatureTypeResponse;
import org.polymap.core.data.feature.GetFeaturesRequest;
import org.polymap.core.data.feature.GetFeaturesResponse;
import org.polymap.core.data.feature.GetFeaturesSizeRequest;
import org.polymap.core.data.feature.GetFeaturesSizeResponse;
import org.polymap.core.data.feature.ModifyFeaturesResponse;
import org.polymap.core.data.feature.TransactionResponse;
import org.polymap.core.data.pipeline.Consumes;
import org.polymap.core.data.pipeline.DataSourceDescriptor;
import org.polymap.core.data.pipeline.EndOfProcessing;
import org.polymap.core.data.pipeline.Param;
import org.polymap.core.data.pipeline.PipelineBuilder;
import org.polymap.core.data.pipeline.PipelineBuilderConcernAdapter;
import org.polymap.core.data.pipeline.PipelineExecutor.ProcessorContext;
import org.polymap.core.data.pipeline.PipelineProcessor;
import org.polymap.core.data.pipeline.PipelineProcessorSite;
import org.polymap.core.data.pipeline.ProcessorDescriptor;
import org.polymap.core.data.pipeline.ProcessorResponse;
import org.polymap.core.data.pipeline.Produces;
import org.polymap.core.project.ILayer;

import org.polymap.p4.project.ProjectRepository;

/**
 * Add {@link AtlasFeatureLayer#fulltextFilter()} to the layer filter.
 *
 * @author Falko Bräutigam
 */
public class AtlasQueryFilterProcessor
        extends DefaultFeaturesProcessor {

    private static final Log log = LogFactory.getLog( AtlasQueryFilterProcessor.class );

    public static final Param<ILayer>       PARAM_LAYER = new Param( "layer", ILayer.class );
    
    
    /**
     * Adds a {@link AtlasQueryFilterProcessor} to each and every
     * {@link FeaturesProducer} pipeline.
     */
    public static class AtlasQueryFilterPipelineBuilderConcern
            extends PipelineBuilderConcernAdapter {

        private DataSourceDescriptor                dsd;

        private Class<? extends PipelineProcessor>  usecase;

        private String                              layerId;

        @Override
        @SuppressWarnings("hiding")
        public void startBuild( PipelineBuilder builder, String layerId, DataSourceDescriptor dsd,
                Class<? extends PipelineProcessor> usecase, ProcessorDescriptor start ) {
            this.dsd = dsd;
            this.usecase = usecase;
            this.layerId = layerId;
        }

        @Override
        public void additionals( PipelineBuilder builder, List<ProcessorDescriptor> chain ) {
            try {
                if (dsd.service.get() instanceof DataAccess 
                        && FeaturesProducer.class.isAssignableFrom( usecase )) {
//                    ILayer layer = ProjectRepository.unitOfWork().entity( ILayer.class, layerId );
//                    
//                    Optional<AtlasFeatureLayer> afl = AtlasFeatureLayer.of( layer ).get();
//                    if (afl.isPresent()) {
                        chain.add( 0, new ProcessorDescriptor( AtlasQueryFilterProcessor.class, null ) );
//                    }
                    
                }
            }
            catch (Exception e) {
                throw new RuntimeException( e );
            }
        }
    }
    
    
    // instance *******************************************
    
    private ILayer                      layer;

    private CoordinateReferenceSystem   layerCrs;


    @Override
    public void init( PipelineProcessorSite site ) throws Exception {
        layer = ProjectRepository.unitOfWork().entity( ILayer.class, site.layerId.get() );
//        layer = (ILayer)site.params().get( "layer" );
    }


    protected Query adapt( Query query ) throws Exception {
        Filter orig = query.getFilter();
        //Filter text = AtlasFeatureLayer.sessionQuery().fulltextFilterOf( layer );
        Filter atlas = AtlasFeatureLayer.sessionQuery().build( layer, layerCrs );

        Query adapted = new Query( query );
        adapted.setFilter( DataPlugin.ff.and( orig, atlas ) );
        log.debug( "FILTER:" + adapted.getFilter() );
        return adapted;
    }
    
    
    @Override
    public void getFeatureSizeRequest( GetFeaturesSizeRequest request, ProcessorContext context ) throws Exception {
        context.sendRequest( new GetFeaturesSizeRequest( adapt( request.getQuery() ) ) );
    }


    @Override
    public void getFeatureBoundsRequest( GetBoundsRequest request, ProcessorContext context ) throws Exception {
        context.sendRequest( new GetBoundsRequest( adapt( request.query.get() ) ) );
    }


    @Override
    public void getFeatureRequest( GetFeaturesRequest request, ProcessorContext context ) throws Exception {
        context.sendRequest( new GetFeaturesRequest( adapt( request.getQuery() ) ) );
    }


    @Produces( {TransactionResponse.class, ModifyFeaturesResponse.class, GetBoundsResponse.class, GetFeaturesSizeResponse.class, GetFeatureTypeResponse.class, GetFeaturesResponse.class, EndOfProcessing.class} )
    @Consumes( {TransactionResponse.class, ModifyFeaturesResponse.class, GetBoundsResponse.class, GetFeaturesSizeResponse.class, GetFeatureTypeResponse.class, GetFeaturesResponse.class, EndOfProcessing.class} )
    public void handleResponse( ProcessorResponse response, ProcessorContext context ) throws Exception {
        if (response instanceof GetFeatureTypeResponse) {
            layerCrs = ((GetFeatureTypeResponse)response).getFeatureType().getCoordinateReferenceSystem();
        }
        context.sendResponse( response );
    }
    
}
