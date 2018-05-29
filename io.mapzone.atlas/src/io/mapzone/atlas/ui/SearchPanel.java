/* 
 * polymap.org
 * Copyright (C) 2016-2018, the @authors. All rights reserved.
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
import java.util.function.Consumer;

import org.opengis.feature.Feature;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.base.Joiner;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.ViewerCell;

import org.eclipse.core.runtime.IProgressMonitor;

import org.polymap.core.project.ILayer;
import org.polymap.core.project.IMap;
import org.polymap.core.runtime.UIJob;
import org.polymap.core.runtime.UIThreadExecutor;
import org.polymap.core.ui.FormDataFactory;
import org.polymap.core.ui.FormLayoutFactory;
import org.polymap.core.ui.SelectionAdapter;
import org.polymap.core.ui.UIUtils;

import org.polymap.rhei.batik.Context;
import org.polymap.rhei.batik.Mandatory;
import org.polymap.rhei.batik.PanelIdentifier;
import org.polymap.rhei.batik.Scope;
import org.polymap.rhei.batik.app.SvgImageRegistryHelper;
import org.polymap.rhei.batik.toolkit.ActionText;
import org.polymap.rhei.batik.toolkit.ClearTextAction;
import org.polymap.rhei.batik.toolkit.SimpleDialog;
import org.polymap.rhei.batik.toolkit.TextActionItem;
import org.polymap.rhei.batik.toolkit.TextActionItem.Type;
import org.polymap.rhei.batik.toolkit.md.MdListViewer;
import org.polymap.rhei.batik.toolkit.md.TreeExpandStateDecorator;

import org.polymap.model2.test.Timer;
import org.polymap.p4.P4Panel;

import io.mapzone.atlas.AtlasFeatureLayer;
import io.mapzone.atlas.AtlasPlugin;
import io.mapzone.atlas.sheet.MarkdownScriptSheet;
import io.mapzone.atlas.sheet.MarkdownScriptSheet.LayerSheet;

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

    private MdListViewer            list;
    
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

        // list
        list = tk().createListViewer( parent, SWT.VIRTUAL, SWT.SINGLE, SWT.FULL_SELECTION );
        list.setContentProvider( contentProvider = new SearchContentProvider() );
        list.firstLineLabelProvider.set( new TreeExpandStateDecorator( list, new SearchLabelProvider() )
                .luminanceDelta.put( 0f ).saturationDelta.put( 0f ) );
        list.secondLineLabelProvider.set( new SearchDescriptionProvider() );
        list.iconProvider.set( new LayersIconProvider() );
        list.addOpenListener( ev -> { 
            SelectionAdapter sel = UIUtils.selection( ev.getSelection() );
            sel.first( ILayer.class ).ifPresent( l -> {
                doToggleLayer( l );
            });
            sel.first( Feature.class ).ifPresent( f -> {
                doOpenDialog( f );
            });
        });
        list.setInput( map.get() );

        // layout
        FormDataFactory.on( searchText.getControl() ).fill().height( 30 ).noBottom();
        FormDataFactory.on( list.getTree() ).fill().top( searchText.getControl() );
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
        list.toggleItemExpand( layer );
        
        AtlasFeatureLayer.of( layer ).thenAccept( o -> {
            boolean expanded = list.getExpandedState( layer );
            o.ifPresent( afl -> afl.visible.set( expanded ) );    
        });
    }
    
    
    /**
     * 
     */
    protected void doOpenDialog( Feature f ) {
        SimpleDialog dialog = new SimpleDialog()
                .title.put( "..." )
                .setContents( parent -> {
                    parent.setLayout( FormLayoutFactory.defaults().margins( 8, 0 ).create() );
                    Label text = tk().createFlowText( parent, "...", SWT.WRAP );
                    FormDataFactory.on( text ).fill().width( P4Panel.SIDE_PANEL_WIDTH-50 );
                    new ScriptJob( f, LayerSheet.DETAIL, t -> {
                        text.setText( t );
                        parent.getParent().layout( true, true );
                        parent.getDisplay().timerExec( 500, () -> {
                            // last resort if font/text size recognition did not work
                            log.info( "Text: " + text.getSize() );
                            if (text.getSize().y < 250) {
                                //text.setText( text.getText() + " " );
                                FormDataFactory.on( text ).fill().height( 300 ).width( P4Panel.SIDE_PANEL_WIDTH-48 );
                                parent.getParent().layout( true, true );
                            }
                        });
                    });
                })
                .addOkAction( "Close", () -> {
                    return true;
                });
        new ScriptJob( f, LayerSheet.TITLE, t -> 
                dialog.getShell().setText( StringUtils.abbreviate( t, 35 ) ) );
        dialog.openAndBlock();
    }
    
    
    /**
     * Labels for {@link SearchPanel#list}. 
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
                    UIJob.schedule( layer.label.get(), monitor -> {
                        for (int c=0; c<100; c++) {
                            Thread.sleep( 100 );
                            Optional<Integer> polled = contentProvider.cachedChildCount( layer );
                            if (polled.isPresent()) {
                                UIThreadExecutor.async( () -> { 
                                    cell.setText( layerLabel + " (" + polled.get() + ")" );
                                    if (AtlasFeatureLayer.of( layer ).get().get().visible.get()) {
                                        log.info( "expand: " + layer.label.get() );
                                        list.expandToLevel( layer, 1 );
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
                new ScriptJob( (Feature)elm, LayerSheet.TITLE, text -> cell.setText( text ) );
            }
            else {
                throw new IllegalStateException( "Unknown element type: " + elm );
            }
        }
    }

    
    /**
     * Descriptions for {@link SearchPanel#list}. 
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
                new ScriptJob( (Feature)elm, LayerSheet.DESCRIPTION, text -> cell.setText( text ) );
            }
            else {
                throw new IllegalStateException( "Unknown element type: " + elm );
            }
        }
    }

    
    /**
     * Icons for {@link SearchPanel#list}.
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

    
    /**
     * 
     */
    protected class ScriptJob
            extends UIJob {

        private Feature             feature;
        
        private LayerSheet          layerSheet;

        private Consumer<String>    consumer;
        
        public ScriptJob( Feature feature, LayerSheet layerSheet, Consumer<String> consumer ) {
            super( "Script" );
            this.layerSheet = layerSheet;
            this.feature = feature;
            this.consumer = consumer;
            schedule();
        }

        @Override
        protected void runWithException( IProgressMonitor monitor ) throws Exception {
            try {
                Timer timer = Timer.startNow();
                ILayer layer = (ILayer)contentProvider.getParent( feature );
                MarkdownScriptSheet sheet = MarkdownScriptSheet.of( layer, layerSheet );
                sheet.setVariables( layer, feature );
                String text = sheet.build( monitor );
                log.info( "Script " + layerSheet + ": " + timer.elapsedTime() + "ms" );
                UIThreadExecutor.async( () -> {
                    consumer.accept( text );
                });
            }
            catch (Exception e) {
                // don't bother client UI
                log.warn( "", e );
            }
        }
    }
    
}
