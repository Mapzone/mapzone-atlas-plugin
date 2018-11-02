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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import com.google.common.collect.MapMaker;
import org.polymap.core.project.ILayer;

import org.polymap.p4.layer.FeatureLayer;
import org.polymap.p4.map.ProjectLayerProvider;
import org.polymap.rap.openlayers.layer.Layer;
import org.polymap.rap.openlayers.layer.TileLayer;
import org.polymap.rap.openlayers.source.TileWMSSource;
import org.polymap.rap.openlayers.source.WMSRequestParams;

import io.mapzone.atlas.AtlasFeatureLayer;
import io.mapzone.atlas.AtlasQuery;

/**
 * Builds layers of {@link AtlasMapPanel#mapViewer}. In addition to
 * {@link ProjectLayerProvider} this adds the TIME param which reflects the current
 * {@link AtlasQuery}, so that...
 *
 * @author Falko Bräutigam
 */
public class AtlasMapLayerProvider
        extends ProjectLayerProvider {

    private Map<String,Layer>   createdLayers = new MapMaker().concurrencyLevel( 2 ).initialCapacity( 32 ).weakValues().makeMap();
    
    
    public AtlasMapLayerProvider( String servletAlias ) {
        super( servletAlias );
    }


    public Layer findCreatedLayer( ILayer layer ) {
        return createdLayers.get( layer.id() );
    }
    
    
    @Override
    protected Layer buildTiledLayer( String layerName, String styleHash ) {
        try {
            ILayer layer = layers.get( layerName );
            Optional<FeatureLayer> featureLayer = FeatureLayer.of( layer ).get();
            AtlasQuery atlasQuery = AtlasFeatureLayer.sessionQuery();        
            String queriedHash = featureLayer.isPresent()
                    ? atlasQuery.queryText.map( text -> String.valueOf( text.hashCode() ) ).orElse( "default" )
                    : "default";
            
            TileLayer result = new TileLayer()
                    .source.put( new TileWMSSource()
                            .url.put( "." + alias )
                            .params.put( new WMSRequestParams()
                                    .version.put( "1.1.1" )  // send "SRS" param
                                    .layers.put( layerName )
                                    .styles.put( styleHash )
                                    .time.put( queriedHash )
                                    .format.put( "image/png" ) ) )
                    // FIXME  init visibility of feature layer should be set elsewhere
                    // see AtlasMapContentProvider event handling
                    .visible.put( !featureLayer.isPresent() );
            
            createdLayers.put( layer.id(), result );
            return result;
        }
        catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException( e );
        }
    }
    

}
