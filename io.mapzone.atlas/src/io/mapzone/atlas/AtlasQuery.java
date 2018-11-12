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

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.filter.Filter;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.polymap.core.project.ILayer;
import org.polymap.core.runtime.config.Concern;
import org.polymap.core.runtime.config.Config;
import org.polymap.core.runtime.config.Configurable;

import org.polymap.p4.layer.FeatureLayer;

import io.mapzone.atlas.index.AtlasIndex;

/**
 * The current query of an Atlas instance, consisting of a spatial query
 * ({@link #mapExtent}) and a fulltext query ({@link #queryText}).
 * <p/>
 * There is just one instance per session, returned by
 * {@link AtlasFeatureLayer#sessionQuery()}.
 *
 * @author Falko Bräutigam
 */
public class AtlasQuery
        extends Configurable {

    private static final Log log = LogFactory.getLog( AtlasQuery.class );
    
    public static final AtlasQuery      TYPE = new AtlasQuery();

    @Concern( AtlasPropertyChangeEvent.Fire.class )
    public Config<String>               queryText;

    @Concern( AtlasPropertyChangeEvent.Fire.class )
    public Config<ReferencedEnvelope>   mapExtent;


    /**
     * Builds the {@link Filter} for the {@link AtlasQueryFilterProcessor}. 
     */
    public Filter build( ILayer layer, CoordinateReferenceSystem crs ) throws Exception {
        Filter extentFilter = extentFilterOf( crs );
        Filter textFilter = fulltextFilterOf( layer );
        return ff.and( extentFilter, textFilter );
    }


    /**
     *
     */
    protected Filter extentFilterOf( CoordinateReferenceSystem crs ) throws Exception {
        Filter extentFilter = Filter.INCLUDE;
        if (mapExtent.isPresent()) {
            try {
                ReferencedEnvelope transformed = mapExtent.get().transform( crs, true );
                extentFilter = ff.bbox( ff.property( "" ), transformed );
            }
            catch (Exception e) {
                // mapExtent is not yet properly set
                log.warn( "CRS: " + CRS.toSRS( crs ) + " -- " + e.getLocalizedMessage() );
                log.warn( "   mapExtent: " + mapExtent.get() );
                log.warn( "       valid: " + crs.getDomainOfValidity() );
            }
        }
        return extentFilter;
    }


    /**
     *
     */
    protected Filter fulltextFilterOf( ILayer layer ) throws Exception {
        Filter textFilter = Filter.INCLUDE;
        if (queryText.isPresent() /*&& mapExtent.isPresent()*/) {
            AtlasIndex index = AtlasIndex.instance();
            textFilter = index.query( queryText.get(), layer );
        }
        return textFilter;
    }

   
    /**
     * The simple *isLike* over all String attributes. For testing. 
     */
    protected Filter isLikeFilterOf( ILayer layer ) throws Exception {
        Filter textFilter = Filter.INCLUDE;
        if (queryText.isPresent() /*&& mapExtent.isPresent()*/) {

            // simple all-string-properties search
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
