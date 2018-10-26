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

import static org.polymap.rhei.batik.app.SvgImageRegistryHelper.NORMAL24;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Slider;

import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.IOpenListener;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.ViewerCell;

import org.polymap.core.mapeditor.MapViewer;
import org.polymap.core.project.ILayer;
import org.polymap.core.project.IMap;
import org.polymap.core.project.ui.ProjectNodeContentProvider;
import org.polymap.core.project.ui.ProjectNodeLabelProvider;
import org.polymap.core.project.ui.ProjectNodeLabelProvider.PropType;
import org.polymap.core.runtime.i18n.IMessages;
import org.polymap.core.ui.FormDataFactory;
import org.polymap.core.ui.FormLayoutFactory;
import org.polymap.core.ui.SelectionAdapter;
import org.polymap.core.ui.UIUtils;

import org.polymap.rhei.batik.BatikPlugin;
import org.polymap.rhei.batik.Context;
import org.polymap.rhei.batik.Mandatory;
import org.polymap.rhei.batik.PanelIdentifier;
import org.polymap.rhei.batik.Scope;
import org.polymap.rhei.batik.toolkit.IPanelSection;
import org.polymap.rhei.batik.toolkit.md.CheckboxActionProvider;
import org.polymap.rhei.batik.toolkit.md.MdListViewer;

import org.polymap.p4.P4Panel;
import org.polymap.p4.P4Plugin;
import org.polymap.p4.layer.FeatureLayer;
import org.polymap.rap.openlayers.layer.Layer;

import io.mapzone.atlas.AtlasFeatureLayer;
import io.mapzone.atlas.AtlasPlugin;
import io.mapzone.atlas.Messages;

/**
 * 
 *
 * @author Falko Bräutigam
 */
public class AtlasLayersPanel
        extends P4Panel {

    private static final Log log = LogFactory.getLog( AtlasLayersPanel.class );

    public static final PanelIdentifier ID = PanelIdentifier.parse( "atlas-layers" );
    
    protected static final IMessages    i18n = Messages.forPrefix( "AtlasLayersPanel" );

    @Mandatory
    @Scope( AtlasPlugin.Scope )
    protected Context<IMap>             map;

    @Scope( AtlasPlugin.Scope )
    protected Context<MapViewer<ILayer>> atlasMapViewer;

    protected ILayer                    selected;

    private MdListViewer                list;

    /** The transparency section. */
    private IPanelSection               section;

    
    @Override
    public boolean beforeInit() {
        if (parentPanel().isPresent() && parentPanel().get() instanceof AtlasMapPanel) {
            site().title.set( "" );
            site().icon.set( P4Plugin.images().svgImage( "layers.svg", P4Plugin.HEADER_ICON_CONFIG ) );
            site().tooltip.set( "Ebenen im Hintergrund einblenden" );
            return true;            
        }
        return false;
    }


    @Override
    public void createContents( Composite parent ) {
        site().title.set( "Hintergrund" );
        parent.setLayout( FormLayoutFactory.defaults().margins( 3, 3, 17, 3 ).spacing( 8 ).create() );

        // list
        list = tk().createListViewer( parent, SWT.SINGLE, SWT.FULL_SELECTION );
        list.setContentProvider( new ImageLayersContentProvider() );

        list.firstLineLabelProvider.set( new ProjectNodeLabelProvider( PropType.Label ).abbreviate.put( 35 ) );
        list.secondLineLabelProvider.set( new ProjectNodeLabelProvider( PropType.Description ).abbreviate.put( 45 ) );
        list.iconProvider.set( new LayerIconProvider() );
        list.firstSecondaryActionProvider.set( new LayerVisibleAction());
        
        list.addOpenListener( new IOpenListener() {
            @Override public void open( OpenEvent ev ) {
                SelectionAdapter.on( ev.getSelection() ).forEach( elm -> {
                    selected = (ILayer)elm;
                    section.getControl().setEnabled( true );
                });
            }
        });
        list.setInput( map.get() );

        // layer settings
        section = tk().createPanelSection( parent, "Transparenz", SWT.BORDER );
        section.getControl().setEnabled( false );
        section.getBody().setLayout( FormLayoutFactory.defaults().margins( 0, 8 ).create() );
        Slider slider = new Slider( section.getBody(), SWT.NONE );
        FormDataFactory.on( slider ).fill().noBottom();
        slider.setMinimum( 10 );
        slider.setMaximum( 110 );
        slider.setSelection( 100 );
        slider.setIncrement( 10 );
        slider.addSelectionListener( UIUtils.selectionListener( ev -> {
            log.info( "Slider: " + slider.getSelection() );
            adjustLayerOpacity( selected, slider.getSelection() );
        }));
        
        // noBottom: avoid empty rows and lines
        FormDataFactory.on( list.getControl() ).fill().bottom( 50 );
        FormDataFactory.on( section.getControl() ).fill().top( list.getTree() );
    }

    
    protected void adjustLayerOpacity( ILayer layer, int opacity ) {
        AtlasMapLayerProvider layerProvider = (AtlasMapLayerProvider)atlasMapViewer.get().layerProvider.get();
        Layer olayer = layerProvider.findCreatedLayer( layer );
        olayer.opacity.set( 0.01f * opacity );
    }
    
    
    /**
     * 
     */
    protected final class LayerVisibleAction
            extends CheckboxActionProvider {
    
        public LayerVisibleAction() {
            super( P4Plugin.images().svgImage( "eye.svg", NORMAL24 ),
                    BatikPlugin.images().svgImage( "checkbox-blank-outline.svg", NORMAL24 ) );
        }

        @Override
        protected boolean initSelection( MdListViewer _viewer, Object elm ) {
            return ((ILayer)elm).userSettings.get().visible.get();
        }

        
        @Override
        public void perform( MdListViewer viewer, Object elm ) {
            boolean toBeUnselected = isSelected( elm );
            if (toBeUnselected) {
                boolean anyOtherVisible = map.get().layers.stream()
                        .filter( l -> l != elm )
                        .anyMatch( l -> AtlasFeatureLayer.of( l ).visible.get() );

                if (!anyOtherVisible) {
                    tk().createSimpleDialog( "Achtung" )
                            .addOkAction( () -> true )
                            .setContents( parent -> {
                                parent.setLayout( FormLayoutFactory.defaults().create() );
                                Label text = tk().createFlowText( parent, "Eine Ebene muss immer aktiv bleiben.\n\n"
                                        + "Aktivieren Sie zuerst eine andere Ebene,\n"
                                        + "um diese Ebene deaktivieren zu können." );
                                FormDataFactory.on( text ).fill().width( 300 );
                            })
                            .open();
                    return;
                }
            }
            super.perform( viewer, elm );
        }

        
        @Override
        protected void onSelection( MdListViewer _viewer, Object elm, @SuppressWarnings( "hiding" ) boolean selected ) {
            ((ILayer)elm).userSettings.get().visible.set( selected );
        }
    }

    
    /**
     * 
     */
    protected final class LayerIconProvider
            extends CellLabelProvider {

        private Map<Object,Image> legendGraphics = new HashMap();

        @Override
        public void update( ViewerCell cell ) {
            ILayer layer = (ILayer)cell.getElement();
            cell.setImage( legendGraphics.containsKey( layer.id() )
                    ? legendGraphics.get( layer.id() )
                    : P4Plugin.images().svgImage( "layers.svg", NORMAL24 ) );
        }
    }

    
    /**
     * 
     */
    protected class ImageLayersContentProvider
            extends ProjectNodeContentProvider {
    
        @Override
        public Object[] getChildren( Object elm ) {
            if (elm instanceof IMap) {
                return ((IMap)elm).layers.stream()
                        .filter( l -> {
                            try {
                                return !FeatureLayer.of( l ).get().isPresent();
                            }
                            catch (Exception e) {
                                throw new RuntimeException( e );
                            }
                        })
                        .sorted( ILayer.ORDER_KEY_ORDERING.reversed() )
                        .collect( Collectors.toList() ).toArray();
            }
            return null;
        }
    }
    
}
