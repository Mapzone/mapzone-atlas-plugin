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
package io.mapzone.atlas.index;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

import org.geotools.data.FeatureSource;
import org.json.JSONObject;
import org.opengis.feature.Feature;
import org.opengis.filter.Filter;
import org.opengis.filter.identity.FeatureId;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import org.polymap.core.CorePlugin;
import org.polymap.core.data.DataPlugin;
import org.polymap.core.data.feature.storecache.StoreCacheProcessor;
import org.polymap.core.project.ILayer;
import org.polymap.core.project.IMap;
import org.polymap.core.runtime.Lazy;
import org.polymap.core.runtime.LockedLazyInit;
import org.polymap.core.runtime.UIJob;
import org.polymap.core.runtime.cache.Cache;
import org.polymap.core.runtime.cache.CacheConfig;
import org.polymap.core.runtime.session.DefaultSessionContext;
import org.polymap.core.runtime.session.DefaultSessionContextProvider;
import org.polymap.core.runtime.session.SessionContext;

import org.polymap.rhei.fulltext.FulltextIndex;
import org.polymap.rhei.fulltext.indexing.FeatureTransformer;
import org.polymap.rhei.fulltext.indexing.LowerCaseTokenFilter;
import org.polymap.rhei.fulltext.indexing.ToStringTransformer;
import org.polymap.rhei.fulltext.store.lucene.LuceneFulltextIndex;
import org.polymap.rhei.fulltext.update.UpdateableFulltextIndex;
import org.polymap.rhei.fulltext.update.UpdateableFulltextIndex.Updater;

import org.polymap.model2.runtime.UnitOfWork;
import org.polymap.p4.project.ProjectRepository;

import io.mapzone.atlas.AtlasPlugin;

/**
 * Provides a {@link FulltextIndex} of the content of the features of all Atlas
 * {@link ILayer}s.
 * <p/>
 * The index is {@link #RECREATE_TIMEOUT periodically} re-created by the
 * {@link IndexerJob} which runs inside its own {@link #SESSION_PROVIDER session
 * context}.
 *
 * @author Falko Bräutigam
 */
public class AtlasIndex {

    private static final Log log = LogFactory.getLog( AtlasIndex.class );
    
    private static final Duration RECREATE_TIMEOUT = Duration.ofMinutes( 60 );
    
    private static final DefaultSessionContextProvider SESSION_PROVIDER = new DefaultSessionContextProvider();
    
    private static final Lazy<AtlasIndex> INSTANCE = new LockedLazyInit( () -> new AtlasIndex() );
    
    /**
     * The global instance.
     */
    public static final AtlasIndex instance() {
        return INSTANCE.get();
    }
    

    // instance ******************************************
    
    private LuceneFulltextIndex         index;
    
    private List<FeatureTransformer>    transformers = new ArrayList();
    
    private IndexerJob                  indexer = new IndexerJob();

    private DefaultSessionContext       updateContext;

    private DefaultSessionContextProvider contextProvider;
    
    private Cache<String,List<JSONObject>> cache = CacheConfig.defaults().initSize( 128 ).createCache();
    
    
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
        
        // start indexer
        indexer.schedule();
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
            Set<FeatureId> fids = FluentIterable.from( search( query, -1 ) )
                    .transform( json -> DataPlugin.ff.featureId( json.getString( FulltextIndex.FIELD_ID ) ) )
                    .toSet();
            filter = !fids.isEmpty() ? DataPlugin.ff.id( fids ) : Filter.EXCLUDE;
        }
        return filter;
    }
    
    
    protected List<JSONObject> search( String query, int maxResults ) {
        return cache.get( query, key -> {
            try {
                Iterable<JSONObject> rs = index.search( query, maxResults );
                return Lists.newArrayList( rs );
            }
            catch (Exception e) {
                log.warn( "", e );
                return Collections.EMPTY_LIST;
            }
        });
    }
    
    
    protected JSONObject transform( Feature feature ) {
        Object result = feature;
        for (FeatureTransformer transformer : transformers) {
            result = transformer.apply( result );
        }
        return (JSONObject)result;
    }
    
    
    /**
     * Re-creates the index. Re-schedules itself with
     * {@link AtlasIndex#RECREATE_TIMEOUT}.
     * <p/>
     * The periodic update also triggers {@link StoreCacheProcessor} to update its
     * cache. We currently do not have a async job to do this. This indexer triggers
     * the update to be done asynchronously.
     */
    protected class IndexerJob
            extends Job {
        
        public IndexerJob() {
            super( "Atlas Indexer" );
        }

        @Override
        protected IStatus run( IProgressMonitor monitor ) {
            try {
                SESSION_PROVIDER.mapContext( updateContext.getSessionKey(), true );
                doIndex( monitor );
                return monitor.isCanceled() ? Status.CANCEL_STATUS : Status.OK_STATUS;
            }
            catch (Exception e) {
                log.warn( "", e );
                throw Throwables.propagate( e );
            }
            finally {
                SESSION_PROVIDER.unmapContext();
                // re-schedule
                schedule( RECREATE_TIMEOUT.toMillis() );
            }
        }


        protected void doIndex( IProgressMonitor monitor ) throws Exception {
            try (
                UnitOfWork uow = ProjectRepository.newUnitOfWork();
                Updater updater = ((UpdateableFulltextIndex)index).prepareUpdate();
            ){
                // start layer jobs
                IMap map = uow.entity( IMap.class, ProjectRepository.ROOT_MAP_ID );
                log.info( "Map: " + map.label.get() );
                List<LayerIndexer> layerIndexers = new ArrayList();
                for (ILayer layer : map.layers) {
                    LayerIndexer layerIndexer = new LayerIndexer( layer, updater, AtlasIndex.this );
                    layerIndexer.schedule();
                    layerIndexers.add( layerIndexer );
                }
                // wait for layer jobs
                UIJob.joinJobs( layerIndexers );

                updater.apply();
                log.info( "Done: " + map.label.get() );
            }
        }
    }
    
}
