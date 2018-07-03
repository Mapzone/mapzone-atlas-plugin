/* 
 * polymap.org
 * Copyright (C) 2016, Falko Bräutigam. All rights reserved.
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

import static org.polymap.core.runtime.UIThreadExecutor.logErrorMsg;
import static org.polymap.core.runtime.event.TypeEventFilter.ifType;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.opengis.feature.Feature;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.base.Throwables;

import org.eclipse.jface.viewers.ILazyTreeContentProvider;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;

import org.polymap.core.data.PipelineFeatureSource;
import org.polymap.core.project.ILayer;
import org.polymap.core.project.IMap;
import org.polymap.core.runtime.UIJob;
import org.polymap.core.runtime.UIThreadExecutor;
import org.polymap.core.runtime.config.Config;
import org.polymap.core.runtime.config.Configurable;
import org.polymap.core.runtime.config.DefaultInt;
import org.polymap.core.runtime.config.Mandatory;
import org.polymap.core.runtime.event.EventHandler;
import org.polymap.core.runtime.event.EventManager;

import org.polymap.p4.layer.FeatureLayer;

import io.mapzone.atlas.AtlasFeatureLayer;
import io.mapzone.atlas.AtlasQuery;
import io.mapzone.atlas.PropertyChangeEvent;

/**
 * Provides {@link ILayer}s of an {@link IMap} and the features thereof. 
 * Listens to {@link PropertyChangeEvent}s fired when {@link AtlasFeatureLayer#sessionQuery()}
 * has been changed.
 *
 * @author Falko Bräutigam
 */
public class SearchContentProvider
        extends Configurable
        implements /*ITreeContentProvider,*/ ILazyTreeContentProvider {

    private static Log log = LogFactory.getLog( SearchContentProvider.class );
    
    public static final Object          LOADING = new Object();
    
    public static final Object[]        CACHE_LOADING = {LOADING};
    
    public static final Object          NO_CHILDREN = new Object();
    
    public static final Object[]        CACHE_NO_CHILDREN = {NO_CHILDREN};
    
    @Mandatory
    @DefaultInt( 25 )
    public Config<Integer>              maxResults;
    
    private TreeViewer                  viewer;
    
    private IMap                        input;

    private ConcurrentMap<Object,Object[]> cache = new ConcurrentHashMap( 32 );
    
    
    public SearchContentProvider() {
        EventManager.instance().subscribe( this, ifType( PropertyChangeEvent.class, ev -> 
                ev.getSource() instanceof AtlasQuery ) );
    }
    
    @Override
    public void dispose() {
        EventManager.instance().unsubscribe( this );
    }

    @Override
    public void inputChanged( @SuppressWarnings("hiding") Viewer viewer, Object oldInput, Object newInput ) {
        this.viewer = (TreeViewer)viewer;
        this.input = (IMap)newInput;
        flush();
    }

    @EventHandler( display=true, delay=100 )
    public void onLayerQueryChange( List<PropertyChangeEvent> evs ) {
        // just refresh() does not always properly reflect structural changes
        viewer.setInput( input );  //refresh();
    }
    
    /**
     * Flush internal cache.
     */
    public void flush() {
        cache.clear();
    }

    
    // ILazyTreeContentProvider ***************************

    @Override
    public void updateChildCount( Object elm, int currentChildCount ) {
        // check cache
        if (elm == LOADING) {
            return;
        }
        Object[] cached = cache.get( elm );
        if (cached != null && (cached.length == currentChildCount || cached == CACHE_LOADING)) {
            return;    
        }
        
        // IMap
        if (elm instanceof IMap) {
            updateMap( (IMap)elm, currentChildCount );
        }
        // ILayer
        else if (elm instanceof ILayer) {
            updateLayer( (ILayer)elm, currentChildCount );
        }
        // Feature
        else if (elm instanceof Feature) {
            if (currentChildCount == 0) {
                viewer.setChildCount( elm, 0 );
            }
        }
        else {
            throw new RuntimeException( "Unknown element type: " + elm );
        }
    }


    /**
    *
    */
   protected void updateMap( IMap elm, int currentChildCount ) {
       updateChildrenLoading( elm );
       
       ConcurrentMap<String,ILayer> children = new ConcurrentSkipListMap();
       List<CompletableFuture> tasks = new ArrayList();
       for (ILayer layer : elm.layers) {
           AtlasFeatureLayer afl = AtlasFeatureLayer.of( layer );
           tasks.add( afl.featureLayer().thenAccept( fl -> {
               if (fl.isPresent()) {
                   children.put( layer.label.get(), layer );
               }
           }));
       };
       tasks.forEach( task -> {
           try { task.get(); }
           catch (Exception e) { Throwables.propagate( e ); }
       });
       
       updateChildren( elm, children.values().toArray(), currentChildCount );
   }


   /**
    *
    */
   protected void updateLayer( ILayer elm, int currentChildCount ) {
       updateChildrenLoading( elm );

       UIJob.schedule( elm.label.get(), monitor -> {
//           Query query = AtlasFeatureLayer.sessionQuery().build( elm );
           
           FeatureLayer fl = FeatureLayer.of( elm ).get().get();
           PipelineFeatureSource fs = fl.featureSource();
           Object[] children = fs.getFeatures( /*query*/ ).toArray();
           updateChildren( elm, children, currentChildCount );               
       });
   }


    /**
     * Updates the {@link #cache} and the child count for this elm in the viewer/tree.
     */
    protected void updateChildren( Object elm, Object[] children, int currentChildCount  ) {
        cache.put( elm, children );

//        if (children.length != currentChildCount) {
        UIThreadExecutor.async( () -> { 
            viewer.setChildCount( elm, children.length );
            viewer.replace( elm, 0, children.length > 0 ? children[0] : null );  // replace the LOADING elm
        }, logErrorMsg( "" ) );
//        }
    }


    /**
     * Marks the given elm as {@link #CACHE_LOADING}. 
     */
    protected void updateChildrenLoading( Object elm ) {
        try {
            cache.put( elm, CACHE_LOADING );
            viewer.setChildCount( elm, 1 );
        }
        catch (Exception e) {
            // ColumnViewer.checkBusy()
            log.warn( e.getLocalizedMessage() );
        }
    }


    @Override
    public void updateElement( Object parent, int index ) {
        Object[] children = cache.get( parent );
        if (children == null) {
            return;
//            updateChildCount( parent, -1 );
//            children = cache.get( parent );
        }
        if (index < children.length) {
            Object child = children[index];
            viewer.replace( parent, index, child );
            boolean hasChildren = !(child instanceof Feature);
            viewer.setHasChildren( child, hasChildren );
        }
        else {
            viewer.replace( parent, index, NO_CHILDREN );
        }
        
//        updateChildCount( children[index], -1 );
    }

    
    public Optional<Integer> cachedChildCount( Object elm ) {
        Object[] result = cache.get( elm );
        return result != null && (result.length > 1 || result != CACHE_LOADING) 
                ? Optional.of( result.length ) 
                : Optional.empty();
    }

    
    @Override
    public Object getParent( Object elm ) {
        log.debug( "getParent( " + elm.getClass().getSimpleName() + "): ..." );
        if (elm == input) {
            return null;
        }
        else if (elm instanceof IMap) {
            return input;
        }
        else if (elm instanceof ILayer) {
            return ((ILayer)elm).parentMap.get();
        }
        else if (elm instanceof Feature) {
            for (Map.Entry<Object,Object[]> entry : cache.entrySet()) {
                if (ArrayUtils.contains( entry.getValue(), elm )) {
                    return entry.getKey();
                }
            }
            throw new IllegalArgumentException( "No parent for feature: " + elm );
        }
        else {
            throw new IllegalArgumentException( "Unknown element type: " + elm );
        }
    }

    
    public TreePath treePathOf( Object elm ) {
        LinkedList result = new LinkedList();
        for (Object current = elm; current != null; current = getParent( current )) {
            if (current != input) {
                result.addFirst( current );
            }
        }
        return new TreePath( result.toArray() );
    }
    
    
//    // ITreeContentProvider *******************************
//    
//    @Override
//    public boolean hasChildren( Object elm ) {
//        if (elm instanceof IMetadataCatalog) {
//            return true;
//        }
//        else if (elm instanceof IMetadata) {
//            return true;
//        }
//        else if (elm instanceof IServiceInfo) {
//            return true;
//        }
//        else if (elm instanceof IResourceInfo) {
//            return false;
//        }
//        return false;
//    }
//
//    
//    @Override
//    public Object[] getChildren( Object elm ) {
//        if (elm instanceof IMetadataCatalog) {
//            return ((IMetadataCatalog)elm).query( catalogQuery ).execute().stream().toArray();
//        }
//        else if (elm instanceof IMetadata) {
//            Map<String,String> connectionParams = ((IMetadata)elm).getConnectionParams();
//            if (resolver.canResolve( connectionParams )) {
//                resolver.resolve( )
//            }
//            return .
//        }
//        else if (elm instanceof IServiceInfo) {
//            return true;
//        }
//        else if (elm instanceof IResourceInfo) {
//            return false;
//        }
//        return false;
//    }
//
//
//    @Override
//    public Object[] getElements( Object elm ) {
//        return getChildren( elm );
//    }
//
//    
//    @Override
//    public Object getParent( Object elm ) {
//        // XXX Auto-generated method stub
//        throw new RuntimeException( "not yet implemented." );
//    }
    
}
