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

import org.geotools.data.Query;
import org.opengis.filter.Filter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.polymap.core.data.DataPlugin;
import org.polymap.core.data.feature.DefaultFeaturesProcessor;
import org.polymap.core.data.feature.GetBoundsRequest;
import org.polymap.core.data.feature.GetFeatureTypeResponse;
import org.polymap.core.data.feature.GetFeaturesRequest;
import org.polymap.core.data.feature.GetFeaturesResponse;
import org.polymap.core.data.feature.GetFeaturesSizeRequest;
import org.polymap.core.data.feature.ModifyFeaturesResponse;
import org.polymap.core.data.feature.TransactionResponse;
import org.polymap.core.data.pipeline.Consumes;
import org.polymap.core.data.pipeline.EndOfProcessing;
import org.polymap.core.data.pipeline.PipelineExecutor.ProcessorContext;
import org.polymap.core.data.pipeline.PipelineProcessorSite;
import org.polymap.core.data.pipeline.ProcessorResponse;
import org.polymap.core.data.pipeline.Produces;
import org.polymap.core.project.ILayer;

import io.mapzone.atlas.AtlasFeatureLayer;

/**
 * Add {@link AtlasFeatureLayer#fulltextFilter()} the the layer filer.
 *
 * @author Falko Bräutigam
 */
public class FilterFeatureProcessor
        extends DefaultFeaturesProcessor {

    private static final Log log = LogFactory.getLog( FilterFeatureProcessor.class );

    private ILayer          layer;


    @Override
    public void init( PipelineProcessorSite site ) throws Exception {
        layer = site.getProperty( "layer" );
    }

    
    protected Query adapt( Query query ) throws Exception {
        Filter orig = query.getFilter();
        Filter fulltext = AtlasFeatureLayer.of( layer ).get().get().fulltextFilter();
        query.setFilter( DataPlugin.ff.and( orig, fulltext ) );
        return query;
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


    @Produces( {TransactionResponse.class, ModifyFeaturesResponse.class, GetFeatureTypeResponse.class, GetFeaturesResponse.class, EndOfProcessing.class} )
    @Consumes( {TransactionResponse.class, ModifyFeaturesResponse.class, GetFeatureTypeResponse.class, GetFeaturesResponse.class, EndOfProcessing.class} )
    public void handleResponse( ProcessorResponse response, ProcessorContext context ) throws Exception {
        context.sendResponse( response );
    }
    
}
