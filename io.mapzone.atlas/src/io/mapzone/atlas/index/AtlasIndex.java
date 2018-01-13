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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import java.io.File;
import java.io.IOException;

import org.geotools.data.FeatureSource;
import org.json.JSONObject;
import org.opengis.feature.Feature;
import org.opengis.filter.Filter;
import org.opengis.filter.identity.FeatureId;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.collect.FluentIterable;

import org.polymap.core.CorePlugin;
import org.polymap.core.data.DataPlugin;
import org.polymap.core.project.ILayer;
import org.polymap.core.runtime.FutureJobAdapter;
import org.polymap.core.runtime.Lazy;
import org.polymap.core.runtime.LockedLazyInit;
import org.polymap.core.runtime.session.DefaultSessionContext;
import org.polymap.core.runtime.session.DefaultSessionContextProvider;
import org.polymap.core.runtime.session.SessionContext;

import org.polymap.rhei.fulltext.FulltextIndex;
import org.polymap.rhei.fulltext.indexing.FeatureTransformer;
import org.polymap.rhei.fulltext.indexing.LowerCaseTokenFilter;
import org.polymap.rhei.fulltext.indexing.ToStringTransformer;
import org.polymap.rhei.fulltext.store.lucene.LuceneFulltextIndex;

import io.mapzone.atlas.AtlasPlugin;

/**
 * Provides a {@link FulltextIndex} of the content of the features of all Atlas
 * {@link ILayer}s.
 *
 * @author Falko Bräutigam
 */
public class AtlasIndex {

    private static final Log log = LogFactory.getLog( AtlasIndex.class );
    
    private static final DefaultSessionContextProvider    sessionProvider = new DefaultSessionContextProvider();
    
    public static final Lazy<AtlasIndex> instance = new LockedLazyInit( () -> new AtlasIndex() );
    
    public static final AtlasIndex instance() {
        return instance.get();
    }
    
    
    // instance ******************************************
    
    private LuceneFulltextIndex         index;
    
    private List<FeatureTransformer>    transformers = new ArrayList();
    
    private AtomicReference<MapIndexer> mapIndexer = new AtomicReference();

    private DefaultSessionContext       updateContext;

    private DefaultSessionContextProvider contextProvider;
    
    
    protected AtlasIndex() {
        // Lucene index
        try {
            File indexDir = new File( CorePlugin.getDataLocation( AtlasPlugin.instance() ), "index" );
            index = new LuceneFulltextIndex( indexDir );
            index.setTokenizer( new AtlasTokenizer() );
            index.addTokenFilter( new LowerCaseTokenFilter() );
            
            transformers.add( new AtlasFeatureTransformer() );
            transformers.add( new ToStringTransformer() );            
        }
        catch (IOException e) {
            throw new RuntimeException( e );
        }
        
        // sessionContext
        assert updateContext == null && contextProvider == null;
        updateContext = new DefaultSessionContext( AtlasIndex.class.getSimpleName() + hashCode() );
        contextProvider = new DefaultSessionContextProvider() {
            protected DefaultSessionContext newContext( String sessionKey ) {
                assert sessionKey.equals( updateContext.getSessionKey() );
                return updateContext;
            }
        };
        SessionContext.addProvider( contextProvider );
    }
    
    
    public FulltextIndex index() {
        return index;
    }

    
    public long sizeInByte() {
        return index.store().storeSizeInByte();
    }
    
    
    /**
     * Query this index. 
     *
     * @param query The Lucene query string.
     * @param layer The layer to query.
     * @return The query/filter to apply to the {@link FeatureSource} of the layer.
     * @throws Exception 
     */
    public Filter query( String query, ILayer layer ) throws Exception {
        Filter filter = Filter.INCLUDE;
        if (!StringUtils.isBlank( query )) {
            Set<FeatureId> fids = FluentIterable.from( index.search( query, -1 ) )
                    .transform( json -> DataPlugin.ff.featureId( json.getString( FulltextIndex.FIELD_ID ) ) )
                    .toSet();
            filter = !fids.isEmpty() ? DataPlugin.ff.id( fids ) : Filter.EXCLUDE;
        }
        return filter;
    }
    
    
    /**
     * 
     */
    public Future update() {
        MapIndexer job = mapIndexer.updateAndGet( current -> {
            if (current == null) {
                try {
                    sessionProvider.mapContext( updateContext.getSessionKey(), true );
                    current = new MapIndexer( this );
                    current.schedule();
                }
                finally {
                    sessionProvider.unmapContext();
                }
            }
            return current;
        });
        return new FutureJobAdapter( job );
    }
    
    
    protected JSONObject transform( Feature feature ) {
        Object result = feature;
        for (FeatureTransformer transformer : transformers) {
            result = transformer.apply( result );
        }
        return (JSONObject)result;
    }
    
}
