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

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import org.eclipse.jface.layout.RowLayoutFactory;

import org.polymap.core.runtime.i18n.IMessages;
import org.polymap.core.ui.FormDataFactory;
import org.polymap.core.ui.FormLayoutFactory;
import org.polymap.core.ui.UIUtils;

import org.polymap.rhei.batik.PanelIdentifier;
import org.polymap.rhei.batik.toolkit.IPanelSection;

import org.polymap.cms.ContentProvider;
import org.polymap.cms.ContentProvider.ContentObject;
import org.polymap.p4.P4Panel;
import org.polymap.p4.P4Plugin;

import io.mapzone.atlas.AtlasPlugin;
import io.mapzone.atlas.Messages;

/**
 * 
 *
 * @author Falko Bräutigam
 */
public class DocPanel
        extends P4Panel {

    private static final Log log = LogFactory.getLog( DocPanel.class );

    public static final PanelIdentifier ID = PanelIdentifier.parse( "doc" );
    
    protected static final IMessages    i18n = Messages.forPrefix( "DocPanel" );


    @Override
    public boolean beforeInit() {
        if (parentPanel().isPresent() && parentPanel().get() instanceof AtlasMapPanel) {
            site().title.set( "" );
            site().icon.set( AtlasPlugin.images().svgImage( "help-circle-outline.svg", P4Plugin.HEADER_ICON_CONFIG ) );
            site().tooltip.set( "Hilfe und Dokumentation" );
            return true;            
        }
        return false;
    }


    @Override
    public void createContents( Composite parent ) {
        site().title.set( "Dokumentation" );
        parent.setLayout( RowLayoutFactory.fillDefaults().margins( 0, 8 ).spacing( 8 ).create() );
        
        List<ContentObject> list = ContentProvider.instance().listContent( "/atlas/docs" );
        for (ContentObject co : list) {
            String title = StringUtils.capitalize( co.title() );
            IPanelSection section = tk().createPanelSection( parent, title, IPanelSection.EXPANDABLE, SWT.BORDER );
            section.setExpanded( false );

            if (list.size() == 1) {
                UIUtils.sessionDisplay().timerExec( 1000, () -> {
                    section.setExpanded( true );
                    site().layout( true );  // force update for scrollbars
                });
            }
            
            section.getBody().setLayout( FormLayoutFactory.defaults().create() );
            FormDataFactory.on( tk().createFlowText( section.getBody(), co.content() ) )
                    .fill().width( parent.getSize().x-30 );
        }
    }

}
