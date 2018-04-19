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
import java.util.Optional;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.Viewer;

import org.polymap.core.mapeditor.MapViewer;
import org.polymap.core.project.ILayer;
import org.polymap.core.project.IMap;
import org.polymap.core.project.ProjectNode.ProjectNodeCommittedEvent;
import org.polymap.core.runtime.config.Config;
import org.polymap.core.runtime.event.EventHandler;
import org.polymap.core.runtime.event.EventManager;

import io.mapzone.atlas.AtlasFeatureLayer;
import io.mapzone.atlas.LayerQueryBuilder;
import io.mapzone.atlas.PropertyChangeEvent;

/**
 * Provides the content of {@link AtlasMapPanel#mapViewer}.
 * <p/>
 * This also tracks the state of the layers and triggers {@link MapViewer#refresh()}
 * on {@link ProjectNodeCommittedEvent} and {@link PropertyChangeEvent}. 
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public class AtlasMapContentProvider
        implements IStructuredContentProvider {

    private static final Log log = LogFactory.getLog( AtlasMapContentProvider.class );
    
    private IMap                map;

    private MapViewer           viewer;
    
    /** Last result of {@link #getElements(Object)}. */
    private List<ILayer>        elements = new ArrayList();


    @Override
    public void inputChanged( @SuppressWarnings("hiding") Viewer viewer, Object oldInput, Object newInput ) {
        if (map != null) {
            dispose();
        }
        
        this.map = (IMap)newInput;
        this.viewer = (MapViewer)viewer;
        
        // listen to AtlasFeatureLayer#visible
        EventManager.instance().subscribe( this, isType( PropertyChangeEvent.class, ev -> {
            Config prop = ev.prop.get();
            return prop.equals( AtlasFeatureLayer.TYPE.visible )
                    || prop.equals( LayerQueryBuilder.TYPE.queryText );
            
//            if (ev.getSource() instanceof AtlasFeatureLayer) {
//                String layerId = ((AtlasFeatureLayer)ev.getSource()).layer().id();
//                log.info( "Check (visible): " + ((AtlasFeatureLayer)ev.getSource()).layer().label.get() );
//                return map.layers.stream()
//                        .filter( l -> l.id().equals( layerId ) )
//                        .findAny().isPresent();
//            }
//            return false;
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
        elements.clear();
        for (ILayer layer : map.layers) {
            try {
                // wait for (check) feature layer
                Optional<AtlasFeatureLayer> featureLayer = AtlasFeatureLayer.of( layer ).get();
                if (featureLayer.isPresent()) {
                    if (featureLayer.get().visible.get()) {
                        elements.add( layer );
                    }
                }
                // background layer
                else {
                    if (layer.userSettings.get().visible.get()) {
                        elements.add( layer );                        
                    }
                };
            }
            catch (Exception e) { 
                log.warn( "", e ); 
            }
        }
        return elements.toArray();
    }


    @EventHandler( display=true, delay=750 )
    protected void onAtlasPropertyChange( List<PropertyChangeEvent> evs ) {
        for (PropertyChangeEvent ev : evs) {
            Config prop = ev.prop.get();
            if (prop.equals( AtlasFeatureLayer.TYPE.visible )) {
                viewer.refresh();
            }
            else if (prop.equals( LayerQueryBuilder.TYPE.queryText )) {
                elements.stream().forEach( l -> viewer.refresh( l, true ) );
            }
            else {
                throw new RuntimeException( "Unhandled event property type: " + prop );
            }
        }
    }
    
}
