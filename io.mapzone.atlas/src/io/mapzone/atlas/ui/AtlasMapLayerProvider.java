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
import org.polymap.rap.openlayers.layer.Layer;
import org.polymap.rap.openlayers.layer.TileLayer;
import org.polymap.rap.openlayers.source.TileWMSSource;
import org.polymap.rap.openlayers.source.WMSRequestParams;

import io.mapzone.atlas.AtlasFeatureLayer;
import io.mapzone.atlas.AtlasQuery;

/**
 * Builds layers of {@link AtlasMapPanel#mapViewer}. In addition to
 * {@link ProjectLayerProvider} this adds the TIME param which reflects the current
 * {@link AtlasQuery}, so that
 *
 * @author Falko Bräutigam
 */
public class AtlasMapLayerProvider
        extends ProjectLayerProvider {

    private static final Log log = LogFactory.getLog( AtlasMapLayerProvider.class );

    
    @Override
    protected Layer buildTiledLayer( String layerName, String styleHash ) {
        AtlasQuery atlasQuery = AtlasFeatureLayer.sessionQuery();
        String queriedHash = atlasQuery.queryText
                .map( text -> String.valueOf( text.hashCode() ) )
                .orElse( "default" );
        
        return new TileLayer()
                .source.put( new TileWMSSource()
                        //.tileGrid.put( new TileGrid( "ol.tilegrid.TileGrid" ) {}.tileSize.put( new Size( 1024, 1024 ) ) )
                        .url.put( "." + alias )
                        .params.put( new WMSRequestParams()
                                .version.put( "1.1.1" )  // send "SRS" param
                                .layers.put( layerName )
                                .styles.put( styleHash )
                                .time.put( queriedHash )
                                .format.put( "image/png" ) ) );
    }
    

}
