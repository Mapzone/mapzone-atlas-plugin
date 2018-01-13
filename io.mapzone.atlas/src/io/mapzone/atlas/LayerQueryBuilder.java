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

import static org.polymap.core.data.DataPlugin.ff;

import org.geotools.data.Query;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.filter.Filter;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import org.polymap.core.project.ILayer;
import org.polymap.core.runtime.config.Concern;
import org.polymap.core.runtime.config.Config;
import org.polymap.core.runtime.config.ConfigurationFactory;

import org.polymap.p4.layer.FeatureLayer;

import io.mapzone.atlas.index.AtlasIndex;

/**
 * 
 *
 * @author Falko Bräutigam
 */
public class LayerQueryBuilder {

    public static final LayerQueryBuilder   TYPE = new LayerQueryBuilder();

    @Concern( PropertyChangeEvent.Fire.class )
    public Config<String>               queryText;

    @Concern( PropertyChangeEvent.Fire.class )
    public Config<ReferencedEnvelope>   mapExtent;


    /** Constructs a new instance with no restrictions. */
    protected LayerQueryBuilder() {
        ConfigurationFactory.inject( this );
    }

    
    public Query build( ILayer layer ) throws Exception {
        Filter extentFilter = extentFilter( layer );
        Filter textFilter = fulltextFilter( layer );
        return new Query( "", ff.and( extentFilter, textFilter ) );
    }


    /**
     *
     */
    protected Filter extentFilter( ILayer layer ) throws Exception {
        Filter extentFilter = Filter.INCLUDE;
        if (mapExtent.isPresent()) {
            CoordinateReferenceSystem layerCrs = FeatureLayer.of( layer ).get().get().featureSource().getSchema().getCoordinateReferenceSystem();
            ReferencedEnvelope transformedExtent = mapExtent.get().transform( layerCrs, true );
            extentFilter = ff.bbox( ff.property( "" ), transformedExtent );
        }
        return extentFilter;
    }


    /**
     *
     */
    protected Filter fulltextFilter( ILayer layer ) throws Exception {
        Filter textFilter = Filter.INCLUDE;
        if (queryText.isPresent() && mapExtent.isPresent()) {
            AtlasIndex index = AtlasIndex.instance();
            textFilter = index.query( queryText.get(), layer );
        }
        return textFilter;
    }
    
}
