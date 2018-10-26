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

import static org.polymap.core.runtime.event.TypeEventFilter.*;

import java.util.ArrayList;
import java.util.List;

import java.beans.PropertyChangeEvent;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.collect.FluentIterable;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.Viewer;

import org.polymap.core.mapeditor.MapViewer;
import org.polymap.core.project.ILayer;
import org.polymap.core.project.ILayer.LayerUserSettings;
import org.polymap.core.project.IMap;
import org.polymap.core.project.ProjectNode.ProjectNodeCommittedEvent;
import org.polymap.core.runtime.UIThreadExecutor;
import org.polymap.core.runtime.config.Config;
import org.polymap.core.runtime.event.EventHandler;
import org.polymap.core.runtime.event.EventManager;

import io.mapzone.atlas.AtlasFeatureLayer;
import io.mapzone.atlas.AtlasQuery;
import io.mapzone.atlas.AtlasPropertyChangeEvent;

/**
 * Provides the content of {@link AtlasMapPanel#mapViewer}.
 * <p/>
 * This also tracks the state of the layers and triggers {@link MapViewer#refresh()}
 * on {@link ProjectNodeCommittedEvent} and {@link AtlasPropertyChangeEvent}. 
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public class AtlasMapContentProvider
        implements IStructuredContentProvider {

    private static final Log log = LogFactory.getLog( AtlasMapContentProvider.class );
    
    private IMap                map;

    private MapViewer           viewer;
    
    /** Last result of {@link #getElements(Object)}. */
    private List<ILayer>        featureLayers = new ArrayList();

    /** Last result of {@link #getElements(Object)}. */
    private List<ILayer>        backgroundLayers = new ArrayList();


    @Override
    public void inputChanged( @SuppressWarnings("hiding") Viewer viewer, Object oldInput, Object newInput ) {
        if (map != null) {
            dispose();
        }
        
        this.map = (IMap)newInput;
        this.viewer = (MapViewer)viewer;
        
        // listen to AtlasFeatureLayer#visible
        EventManager.instance().subscribe( this, isType( AtlasPropertyChangeEvent.class, ev -> {
            Config prop = ev.prop.get();
            return prop.equals( AtlasFeatureLayer.TYPE.visible )
                    || prop.equals( AtlasQuery.TYPE.queryText );
        }));
        
        // listen to LayerUserSettings#visible
        EventManager.instance().subscribe( this, ifType( PropertyChangeEvent.class, ev -> {
            if (ev.getSource() instanceof LayerUserSettings) {
                String layerId = ((LayerUserSettings)ev.getSource()).layerId();
                return map.layers.stream()
                        .filter( l -> l.id().equals( layerId ) )
                        .findAny().isPresent();
            }
            return false;
        }));

    }

    
    @Override
    public void dispose() {
        log.info( "..." );
        this.map = null;
        EventManager.instance().unsubscribe( this );
    }


    @Override
    public Object[] getElements( Object inputElement ) {
        featureLayers.clear();
        backgroundLayers.clear();
        for (ILayer layer : map.layers) {
            try {
                AtlasFeatureLayer afl = AtlasFeatureLayer.of( layer );
                // atlas/feature layer
                if (afl.featureLayer().get().isPresent()) {
                    featureLayers.add( layer );
                }
                // background layer
                else {
                    backgroundLayers.add( layer );
                    if (!layer.userSettings.get().visible.get()) {
                        UIThreadExecutor.async( () -> 
                                viewer.setVisible( layer, false ) );
                    }
                };
            }
            catch (Exception e) { 
                log.warn( "", e );
            }
        }
        return FluentIterable.from( featureLayers ).append( backgroundLayers ).toArray( ILayer.class );
    }


    @EventHandler( display=true, delay=500 )
    protected void onAtlasFeatureLayerChange( List<AtlasPropertyChangeEvent> evs ) {
        // process just last event; skip previous event
        AtlasPropertyChangeEvent ev = FluentIterable.from( evs ).last().get();
        Config<Boolean> prop = ev.prop.get();

        if (prop.equals( AtlasFeatureLayer.TYPE.visible )) {
            viewer.setVisible( ((AtlasFeatureLayer)ev.getSource()).layer(), prop.get() );
            //viewer.refresh();
        }

        else if (prop.equals( AtlasQuery.TYPE.queryText )) {
            for (ILayer l : featureLayers) {
                // refresh remove()/add() layer in order to avoid layer.refresh()
                // so after this the layer is visible by default
                viewer.refresh( l, true );
                viewer.setVisible( l, AtlasFeatureLayer.of( l ).visible.get() );
            }
        }

        else {
            throw new RuntimeException( "Unhandled event property type: " + prop );
        }
    }
    
    
    @EventHandler( display=true, delay=100 )
    protected void onBackgroundLayerChange( List<PropertyChangeEvent> evs ) {
        for (PropertyChangeEvent ev : evs) {
            LayerUserSettings settings = (LayerUserSettings)ev.getSource();
            ILayer layer = backgroundLayers.stream()
                    .filter( l -> l.id().equals( settings.layerId() ) ).findFirst()
                    .orElseThrow( () -> new IllegalStateException() );
            viewer.setVisible( layer, settings.visible.get() );
        }
    }
    
}
