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

import static org.polymap.core.data.DataPlugin.ff;

import org.geotools.data.Query;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.filter.Filter;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import org.polymap.core.project.ILayer;
import org.polymap.core.runtime.config.Concern;
import org.polymap.core.runtime.config.Config;
import org.polymap.core.runtime.config.ConfigurationFactory;

import org.polymap.p4.layer.FeatureLayer;

/**
 * There is just one instance per session, returned by {@link AtlasFeatureLayer#sessionQuery()}.
 *
 * @author Falko Bräutigam
 */
public class AtlasQuery {

    public static final AtlasQuery   TYPE = new AtlasQuery();

    @Concern( PropertyChangeEvent.Fire.class )
    public Config<String>               queryText;

    @Concern( PropertyChangeEvent.Fire.class )
    public Config<ReferencedEnvelope>   mapExtent;


    /** Constructs a new instance with no restrictions. */
    protected AtlasQuery() {
        ConfigurationFactory.inject( this );
    }

    
    public Query build( ILayer layer ) throws Exception {
        Filter extentFilter = extentFilterOf( layer );
        Filter textFilter = fulltextFilterOf( layer );
        return new Query( "", ff.and( extentFilter, textFilter ) );
    }


    /**
     *
     */
    protected Filter extentFilterOf( ILayer layer ) throws Exception {
        Filter extentFilter = Filter.INCLUDE;
        if (mapExtent.isPresent()) {
            CoordinateReferenceSystem layerCrs = FeatureLayer.of( layer ).get().get().featureSource().getSchema().getCoordinateReferenceSystem();
            ReferencedEnvelope transformed = mapExtent.get().transform( layerCrs, true );
            extentFilter = ff.bbox( ff.property( "" ), transformed );
        }
        return extentFilter;
    }


    /**
     *
     */
    public Filter fulltextFilterOf( ILayer layer ) throws Exception {
        Filter textFilter = Filter.INCLUDE;
        if (queryText.isPresent() /*&& mapExtent.isPresent()*/) {
//            AtlasIndex index = AtlasIndex.instance();
//            textFilter = index.query( queryText.get(), layer );
            
            textFilter = Filter.EXCLUDE;
            SimpleFeatureType schema = FeatureLayer.of( layer ).get().get().featureSource().getSchema();
            for (AttributeDescriptor attr : schema.getAttributeDescriptors()) {
                if (String.class.isAssignableFrom( attr.getType().getBinding() )) {
                    textFilter = ff.or( textFilter, ff.like( ff.property( attr.getName() ), queryText.get() + "*", "*", "?", "\\" ) );
                }
            }
        }
        return textFilter;
    }
    
}
