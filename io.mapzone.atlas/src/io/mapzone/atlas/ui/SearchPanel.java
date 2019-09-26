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

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.geotools.styling.Style;
import org.opengis.feature.Feature;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.ViewerCell;

import org.eclipse.core.runtime.IProgressMonitor;

import org.polymap.core.data.feature.DefaultStyles;
import org.polymap.core.mapeditor.MapViewer;
import org.polymap.core.project.ILayer;
import org.polymap.core.project.IMap;
import org.polymap.core.runtime.UIJob;
import org.polymap.core.runtime.UIThreadExecutor;
import org.polymap.core.runtime.event.EventHandler;
import org.polymap.core.runtime.event.EventManager;
import org.polymap.core.ui.FormDataFactory;
import org.polymap.core.ui.FormLayoutFactory;
import org.polymap.core.ui.SelectionAdapter;
import org.polymap.core.ui.UIUtils;

import org.polymap.rhei.batik.Context;
import org.polymap.rhei.batik.Mandatory;
import org.polymap.rhei.batik.PanelIdentifier;
import org.polymap.rhei.batik.Scope;
import org.polymap.rhei.batik.app.SvgImageRegistryHelper;
import org.polymap.rhei.batik.toolkit.ActionItem;
import org.polymap.rhei.batik.toolkit.ActionText;
import org.polymap.rhei.batik.toolkit.ClearTextAction;
import org.polymap.rhei.batik.toolkit.SimpleDialog;
import org.polymap.rhei.batik.toolkit.TextActionItem;
import org.polymap.rhei.batik.toolkit.TextActionItem.Type;
import org.polymap.rhei.batik.toolkit.md.MdListViewer;
import org.polymap.rhei.batik.toolkit.md.TreeExpandStateDecorator;
import org.polymap.rhei.fulltext.ui.FulltextProposal;

import org.polymap.model2.test.Timer;
import org.polymap.p4.P4Panel;
import org.polymap.p4.P4Plugin;
import org.polymap.p4.layer.FeatureClickEvent;
import org.polymap.p4.layer.FeatureLayer;
import org.polymap.rap.openlayers.base.OlEvent;
import org.polymap.rap.openlayers.view.View;
import org.polymap.rap.openlayers.view.View.ExtentEventPayload;

import io.mapzone.atlas.AtlasFeatureLayer;
import io.mapzone.atlas.AtlasPlugin;
import io.mapzone.atlas.index.AtlasIndex;
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

    /** Inbound: access to current map scale. */
    @Scope( AtlasPlugin.Scope )
    protected Context<MapViewer<ILayer>> atlasMapViewer;
    
    private ActionText              searchText;

    private MdListViewer            list;
    
    private SearchContentProvider   contentProvider;

    private Button                  clearBtn;

    
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
    public void init() {
        super.init();
        //site().setSize( 250, 300, Integer.MAX_VALUE );
        EventManager.instance().subscribe( this, ev -> ev instanceof FeatureClickEvent ); 
    }

    @Override
    public void dispose() {
        super.dispose();
        EventManager.instance().unsubscribe( this );
    }


    @Override
    public void createContents( Composite parent ) {
        site().title.set( "Suchen" );
        parent.setLayout( FormLayoutFactory.defaults().margins( 0, 10 ).spacing( 6 ).create() );
        
        // searchText
        searchText = tk().createActionText( parent, "" )
                .performOnEnter.put( true )
                .performDelayMillis.put( 1000 )
                .textHint.put( "Suchen...");
        ActionItem clearAction = new ClearTextAction( searchText )
                .tooltip.put( "Suche zurücksetzen. Alle Objekte anzeigen." )
                .icon.put( AtlasPlugin.images().svgImage( "close-box.svg", SvgImageRegistryHelper.NORMAL24 ) );
        new TextActionItem( searchText, Type.DEFAULT )
                .action.put( ev -> doSearch() )
                .tooltip.put( "Suchen in allen Einträgen." )
                .icon.put( AtlasPlugin.images().svgImage( "magnify.svg", SvgImageRegistryHelper.NORMAL12 ) );
        new FulltextProposal( AtlasIndex.instance().queryDecoratedIndex(), searchText.getText() )
                .eventOnAccept.put( true );
        searchText.getText().setFont( UIUtils.bold( searchText.getText().getFont() ) );
        searchText.getText().addModifyListener( ev -> updateEnabled() );

        // clear button
        clearBtn = tk().createButton( parent, null, SWT.PUSH );
        clearBtn.setToolTipText( "Suche zurücksetzen. Alle Objekte anzeigen." );
        clearBtn.setImage( AtlasPlugin.images().svgImage( "refresh.svg", SvgImageRegistryHelper.WHITE24 ) );
        clearBtn.addSelectionListener( UIUtils.selectionListener( ev -> clearAction.action.get().accept( ev ) ) );
                
        // list
        list = tk().createListViewer( parent, SWT.VIRTUAL, SWT.SINGLE, SWT.FULL_SELECTION );
        list.setContentProvider( contentProvider = new SearchContentProvider() );
        list.firstLineLabelProvider.set( new TreeExpandStateDecorator( list, new SearchLabelProvider() )
                .luminanceDelta.put( 5f ).saturationDelta.put( 0f ) );
        list.secondLineLabelProvider.set( new SearchDescriptionProvider() );
        list.iconProvider.set( new LayersIconProvider() );
        list.addOpenListener( ev -> { 
            SelectionAdapter sel = UIUtils.selection( ev.getSelection() );
            sel.first( ILayer.class ).ifPresent( l -> {
                doToggleLayer( l );
            });
            sel.first( Feature.class ).ifPresent( f -> {
                doClickFeature( f );
                //doOpenDialog( f );
            });
        });
        list.setInput( map.get() );

        // map events
        View view = atlasMapViewer.get().map().view.get();
        view.addEventListener( View.Event.RESOLUTION, this, new View.ExtentEventPayload() );

        // layout
        FormDataFactory.on( clearBtn ).top( 0 ).left( 100, -40 ).right( 100, -2 ).height( 34 );
        FormDataFactory.on( searchText.getControl() ).fill().right( clearBtn ).height( 35 ).noBottom();
        FormDataFactory.on( list.getTree() ).fill().top( searchText.getControl() );
    }


    protected void updateEnabled() {
        // defer until doSearch() has completed
        UIThreadExecutor.async( () ->
                clearBtn.setEnabled( !StringUtils.isBlank( AtlasFeatureLayer.sessionQuery().queryText.get() ) ) );
    }
    

    @EventHandler( display=true, delay=2500 )
    protected void onOlEvent( List<OlEvent> evs ) {
        if (list.getControl().isDisposed()) {
            log.warn( "Removing handler for OL events." );
            View view = atlasMapViewer.get().map().view.get();
            view.removeEventListener( View.Event.RESOLUTION, this );
        }
        else {
            // map extent
            for (OlEvent ev : Lists.reverse( evs )) {
                ExtentEventPayload payload = View.ExtentEventPayload.findIn( ev ).orElse( null );
                if (payload != null) {
                    list.refresh( true );
                    break;
                }
            }
        }
    }

    
    protected void doSearch() {
        AtlasFeatureLayer.sessionQuery().queryText.set( searchText.getTextText() );

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
        // do this before list expand, so that map refresh does not wait
        // for list content provider
        boolean expanded = list.getExpandedState( layer );  // do this outside the thenAccept() job         
        AtlasFeatureLayer.of( layer ).visible.set( !expanded );  // intentionally fails if not present    
        
        // triggers list refresh and content provider voodoo
        list.toggleItemExpand( layer );
    }
    
    
    /**
     * 
     */
    protected void doOpenDialog( Feature f ) {
        SimpleDialog dialog = tk().createSimpleDialog( "..." )
            .setContents( parent -> {
                parent.setLayout( FormLayoutFactory.defaults().margins( 8, 0 ).create() );
                Label text = tk().createFlowText( parent, "...", SWT.WRAP );
                int textWidth = P4Panel.SIDE_PANEL_WIDTH-50;
                FormDataFactory.on( text ).fill().width( textWidth );
                
                new ScriptJob( f, LayerSheet.DETAIL, t -> {
                    text.setText( t );

                    Shell shell = parent.getShell();
                    shell.layout( true, true );
                    parent.getDisplay().timerExec( 500, () -> {
                        shell.layout( true, true );
                        // XXX last resort if font/text size recognition did not work
                        log.debug( "Text size: " + text.getSize() );
                        log.debug( "Shell size: " + parent.getShell().getSize() );
                        if (shell.getSize().y < 200) {
                            Point size = text.computeSize( textWidth, SWT.DEFAULT );
                            text.setLayoutData( FormDataFactory.filled().height( size.y ).width( textWidth ).create() );
                            shell.setSize( shell.getSize().x, 125 + size.y );
                            shell.setLocation( shell.getLocation().x, shell.getLocation().y-125 );
                        }
                    });
                });
            })
            .addOkAction( "Schließen", () -> {
                return true;
            });
        
        new ScriptJob( f, LayerSheet.TITLE, t -> 
                dialog.getShell().setText( StringUtils.abbreviate( t, 32 ) ) );
        dialog.setBlockOnOpen( false );
        dialog.open();
    }
    
    
    /** 
     * Fires {@link FeatureClickEvent}. 
     */
    protected void doClickFeature( Feature f ) {
        ILayer layer = (ILayer)contentProvider.getParent( f );
        FeatureLayer.of( layer ).thenAccept( fl -> {
            fl.get().setClicked( f );
        });
    }

    
    /**
     * Select and reveal item in {@link #list} when feature was clicked on the
     * {@link AtlasMapPanel} or in {@link #doClickFeature(Feature)}.
     */
    @EventHandler( display=true )
    protected void onFeatureClick( FeatureClickEvent ev ) throws Exception {
        if (list != null && !list.getControl().isDisposed()) {
            list.setSelection( new StructuredSelection( ev.clicked.get() ), true );
            doOpenDialog( ev.clicked.get() );
        }
        else {
            EventManager.instance().unsubscribe( this );
        }
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
                                    try {
                                        cell.setText( layerLabel + " (" + polled.get() + ")" );
                                        if (AtlasFeatureLayer.of( layer ).visible.get()) {
                                            log.debug( "expand: " + layer.label.get() );
                                            list.expandToLevel( layer, 1 );
                                        }
                                    }
                                    catch (SWTException e) {
                                        log.warn( e.getLocalizedMessage() );
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
                new ScriptJob( (Feature)elm, LayerSheet.TITLE, text -> { 
                    // widget is disposed because of async job
                    try { cell.setText( text ); }
                    catch (SWTException e) { log.warn( e.getLocalizedMessage() ); }
                });
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
                new ScriptJob( (Feature)elm, LayerSheet.DESCRIPTION, text -> {
                    // widget is disposed because of async job
                    try { cell.setText( text ); }
                    catch (SWTException e) { log.warn( e.getLocalizedMessage() ); }
                });
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
            // ILayer
            if (elm instanceof ILayer) {
                cell.setImage( AtlasPlugin.images().svgImage( "folder-outline.svg", SvgImageRegistryHelper.NORMAL24 ) );
            }
            // Feature
            else if (elm instanceof Feature) {
                ILayer layer = (ILayer)contentProvider.getParent( elm );
                String styleId = layer.styleIdentifier.get();
                Style style = P4Plugin.styleRepo().serializedFeatureStyle( styleId, Style.class )
                        .orElse( DefaultStyles.createAllStyle() );
                log.debug( "Extent: " + atlasMapViewer.get().mapExtent.get().getWidth() );
                double mapWidth = atlasMapViewer.get().mapExtent.get().getWidth();
                double imageWidth = atlasMapViewer.get().getControl().getSize().x;
                new GlyphRenderer( (Feature)elm, style, 28, mapWidth / imageWidth ).start( image -> {
                    try {
                        cell.setImage( (Image)image );
                    }
                    catch (SWTException e) {
                        log.warn( e.getLocalizedMessage() );
                    }
                });
            }
            else {
                log.warn( "Unsupported element type: " + elm.getClass().getSimpleName() );
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
                sheet.setStandardVariables( layer, feature );
                String text = sheet.build( monitor );
                log.debug( "Script " + layerSheet + ": " + timer.elapsedTime() + "ms" );
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
