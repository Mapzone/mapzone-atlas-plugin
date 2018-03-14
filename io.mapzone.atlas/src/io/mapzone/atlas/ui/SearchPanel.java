/* 
 * polymap.org
 * Copyright (C) 2016, the @authors. All rights reserved.
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

import java.util.Optional;

import org.opengis.feature.Feature;
import org.opengis.feature.Property;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.base.Joiner;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.ViewerCell;

import org.polymap.core.project.ILayer;
import org.polymap.core.project.IMap;
import org.polymap.core.runtime.UIJob;
import org.polymap.core.runtime.UIThreadExecutor;
import org.polymap.core.ui.FormDataFactory;
import org.polymap.core.ui.FormLayoutFactory;
import org.polymap.core.ui.SelectionAdapter;

import org.polymap.rhei.batik.Context;
import org.polymap.rhei.batik.Mandatory;
import org.polymap.rhei.batik.PanelIdentifier;
import org.polymap.rhei.batik.Scope;
import org.polymap.rhei.batik.app.SvgImageRegistryHelper;
import org.polymap.rhei.batik.toolkit.ActionText;
import org.polymap.rhei.batik.toolkit.ClearTextAction;
import org.polymap.rhei.batik.toolkit.TextActionItem;
import org.polymap.rhei.batik.toolkit.TextActionItem.Type;
import org.polymap.rhei.batik.toolkit.md.MdListViewer;
import org.polymap.p4.P4Panel;

import io.mapzone.atlas.AtlasFeatureLayer;
import io.mapzone.atlas.AtlasPlugin;

/**
 * 
 *
 * @author Falko Bräutigam
 */
public class SearchPanel
        extends P4Panel {

    private static final Log log = LogFactory.getLog( SearchPanel.class );
    
    public static final PanelIdentifier ID = PanelIdentifier.parse( "atlas-search" );
    
    /** Inbound: */
    @Mandatory
    @Scope( AtlasPlugin.Scope )
    protected Context<IMap>         map;

    private ActionText              searchText;

    private MdListViewer            viewer;
    
    private SearchContentProvider   contentProvider;

    
    @Override
    public boolean beforeInit() {
        if (parentPanel().isPresent() && parentPanel().get() instanceof AtlasMapPanel) {
            site().title.set( "" );
            site().tooltip.set( "Orte und Informationen suchen" );
            site().icon.set( AtlasPlugin.images().svgImage( "magnify.svg", AtlasPlugin.HEADER_ICON_CONFIG ) );
            return true;            
        }
        return false;
    }


    @Override
    public void dispose() {
        super.dispose();
    }


    @Override
    public void createContents( Composite parent ) {
        site().title.set( "Suchen" );
        parent.setLayout( FormLayoutFactory.defaults().margins( 0, 10 ).spacing( 6 ).create() );
        
        // searchText
        searchText = tk().createActionText( parent, "" );
        new TextActionItem( searchText, Type.DEFAULT )
                .action.put( ev -> doSearch() )
                .text.put( "Suchen..." )
                .tooltip.put( "Fulltext search. Use * as wildcard.<br/>&lt;ENTER&gt; starts the search." )
                .icon.put( AtlasPlugin.images().svgImage( "magnify.svg", SvgImageRegistryHelper.DISABLED12 ) );
        new ClearTextAction( searchText );
        //searchText.getText().forceFocus();

        // viewer
        viewer = tk().createListViewer( parent, SWT.VIRTUAL, SWT.SINGLE, SWT.FULL_SELECTION );
        viewer.setContentProvider( 
                contentProvider = new SearchContentProvider() );
        viewer.firstLineLabelProvider.set( 
                //new TreeExpandStateDecorator( viewer, 
                new SearchLabelProvider() );
        viewer.secondLineLabelProvider.set( 
                new SearchDescriptionProvider() );
        viewer.iconProvider.set( 
                new LayersIconProvider() );
        viewer.addOpenListener( ev -> 
                SelectionAdapter.on( ev.getSelection() ).first( ILayer.class ).ifPresent( l -> doToggleLayer( l ) ) );
        viewer.setInput( map.get() );

        // layout
        FormDataFactory.on( searchText.getControl() ).fill().height( 30 ).noBottom();
        FormDataFactory.on( viewer.getTree() ).fill().top( searchText.getControl() );
    }

    
    protected void doSearch() {
        AtlasFeatureLayer.query().queryText.set( searchText.getTextText() );

//        Envelope extent = mapExtent.get().mapExtent.get();
//        CoordinateReferenceSystem crs = mapExtent.get().getMapCRS();
//                
//        contentProvider.updateViewer( new LayerQueryBuilder()
//                .queryText.put( searchText.getTextText() )
//                .mapExtent.put( ReferencedEnvelope.create( extent, crs ) ) );
    }
    

    /**
     * 
     */
    protected void doToggleLayer( ILayer layer ) {
        viewer.toggleItemExpand( layer );
        
        AtlasFeatureLayer.of( layer ).thenAccept( o -> {
            boolean expanded = viewer.getExpandedState( layer );
            o.ifPresent( afl -> afl.visible.set( expanded ) );    
        });
    }
    
    
    /**
     * Labels for {@link SearchPanel#viewer}. 
     */
    protected class SearchLabelProvider
            extends CellLabelProvider {

        @Override
        public void update( ViewerCell cell ) {
            Object elm = cell.getElement();
            if (elm == SearchContentProvider.LOADING) {
                cell.setText( "Loading..." );
            }
            // IMap
            else if (elm instanceof IMap) {
                cell.setText( ((IMap)elm).label.get() );
            }
            // ILayer
            else if (elm instanceof ILayer) {
                ILayer layer = (ILayer)elm;
                String layerLabel = layer.label.get();
                
                Optional<Integer> childCount = contentProvider.cachedChildCount( layer );
                if (childCount.isPresent()) {
                    cell.setText( layerLabel + " (" + childCount.get() + ")" );
                }
                else {
                    cell.setText( layerLabel + " (..)" );
                    // poll contentProvider for child count
                    contentProvider.updateLayer( layer, -1 );
                    UIJob.schedule( "Layer child count: " + layer.label.get(), monitor -> {
                        for (int c=0; c<100; c++) {
                            Thread.sleep( 100 );
                            Optional<Integer> polled = contentProvider.cachedChildCount( layer );
                            if (polled.isPresent()) {
                                UIThreadExecutor.async( () -> { 
                                    cell.setText( layerLabel + " (" + polled.get() + ")" );
                                    if (AtlasFeatureLayer.of( layer ).get().get().visible.get()) {
                                        log.info( "expand: " + layer.label.get() );
                                        viewer.expandToLevel( layer, 1 );
                                    }
                                    return null;
                                });
                                break;
                            }
                        }
                    });
                }
            }
            // Feature
            else if (elm instanceof Feature) {
                Property prop = ((Feature)elm).getProperty( "Name" );
                if (prop != null) {
                    cell.setText( prop.getValue().toString() );
                    return;
                }
                prop = ((Feature)elm).getProperty( "NAME" );
                if (prop != null) {
                    cell.setText( prop.getValue().toString() );
                    return;
                }
                else {
                    cell.setText( ((Feature)elm).getIdentifier().getID() );
                }
            }
            else {
                throw new IllegalStateException( "Unknown element type: " + elm );
            }
        }
    }

    
    /**
     * Descriptions for {@link SearchPanel#viewer}. 
     */
    protected class SearchDescriptionProvider
            extends CellLabelProvider {

        @Override
        public void update( ViewerCell cell ) {
            Object elm = cell.getElement();
            if (elm == SearchContentProvider.LOADING) {
                cell.setText( "..." );
            }
            // ILayer
            else if (elm instanceof ILayer) {
                ILayer layer = (ILayer)elm;
                cell.setText( Joiner.on( ", " ).join( layer.keywords ) );
            }
            // Feature
            else if (elm instanceof Feature) {
                cell.setText( "Südstr. 6, 04425 Taucha" );
            }
            else {
                throw new IllegalStateException( "Unknown element type: " + elm );
            }
        }
    }

    
    /**
     * Icons for {@link SearchPanel#viewer}.
     */
    protected final class LayersIconProvider
            extends CellLabelProvider {

        @Override
        public void update( ViewerCell cell ) {
            Object elm = cell.getElement();
            if (elm instanceof ILayer) {
                cell.setImage( AtlasPlugin.images().svgImage( "map-marker-multiple.svg", SvgImageRegistryHelper.NORMAL24 ) );
            }
            else /*if (elm instanceof Feature)*/ {
                cell.setImage( AtlasPlugin.images().svgImage( "map-marker.svg", SvgImageRegistryHelper.NORMAL12 ) );
            }
        }
    }

}
