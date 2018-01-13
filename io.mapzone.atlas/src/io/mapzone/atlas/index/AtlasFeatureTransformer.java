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

import org.json.JSONObject;
import org.opengis.feature.Feature;

import com.vividsolutions.jts.geom.Geometry;

import org.polymap.rhei.fulltext.indexing.Feature2JsonTransformer;

/**
 * 
 *
 * @author Falko Bräutigam
 */
class AtlasFeatureTransformer
        extends Feature2JsonTransformer {
    
    @Override
    protected void init( JSONObject result, Feature feature ) {
        super.init( result, feature );
        result.putOnce( "_featureType_", feature.getType().getName().getLocalPart() );
    }

    @Override
    protected void addValue( String name, Object value, JSONObject result, Feature feature ) {
        if (!(value instanceof Geometry)) {
            super.addValue( name, value, result, feature );
        }
    }

}
