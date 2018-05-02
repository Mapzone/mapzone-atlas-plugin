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
import org.eclipse.swt.widgets.Slider;

import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.IOpenListener;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.ViewerCell;

import org.polymap.core.project.ILayer;
import org.polymap.core.project.IMap;
import org.polymap.core.project.ui.ProjectNodeContentProvider;
import org.polymap.core.project.ui.ProjectNodeLabelProvider;
import org.polymap.core.project.ui.ProjectNodeLabelProvider.PropType;
import org.polymap.core.runtime.i18n.IMessages;
import org.polymap.core.ui.FormDataFactory;
import org.polymap.core.ui.FormLayoutFactory;
import org.polymap.core.ui.SelectionAdapter;

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
import org.polymap.p4.layer.LayerInfoPanel;

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

    /** Set before opening {@link LayerInfoPanel}. */
    @Scope( AtlasPlugin.Scope)
    protected Context<ILayer>           selected;

    private MdListViewer                list;

    
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
                    selected.set( (ILayer)elm );
                });
            }
        });
        list.setInput( map.get() );

        // layer settings
        IPanelSection section = tk().createPanelSection( parent, "Transparenz", SWT.BORDER );
        section.getBody().setLayout( FormLayoutFactory.defaults().margins( 0, 8 ).create() );
        Slider slider = new Slider( section.getBody(), SWT.NONE );
        slider.setMinimum( 0 );
        slider.setMaximum( 100 );
        slider.setSelection( 50 );
        FormDataFactory.on( slider ).fill().noBottom();
        
        // noBottom: avoid empty rows and lines
        FormDataFactory.on( list.getControl() ).fill().bottom( 50 );
        FormDataFactory.on( section.getControl() ).fill().top( list.getTree() );
    }

    
    /**
     * 
     */
    protected final class LayerVisibleAction
            extends CheckboxActionProvider {
    
        public LayerVisibleAction() {
            super( P4Plugin.images().svgImage( "eye.svg", NORMAL24 ),
                    BatikPlugin.images().svgImage( "checkbox-blank-circle-outline.svg", NORMAL24 ) );
        }

        @Override
        protected boolean initSelection( MdListViewer _viewer, Object elm ) {
            return ((ILayer)elm).userSettings.get().visible.get();
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
