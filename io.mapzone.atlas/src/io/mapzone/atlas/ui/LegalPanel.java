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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.swt.widgets.Composite;
import org.polymap.core.runtime.i18n.IMessages;
import org.polymap.rhei.batik.PanelIdentifier;
import org.polymap.p4.P4Panel;
import org.polymap.p4.P4Plugin;

import io.mapzone.atlas.AtlasPlugin;
import io.mapzone.atlas.Messages;

/**
 * 
 *
 * @author Falko Bräutigam
 */
public class LegalPanel
        extends P4Panel {

    private static final Log log = LogFactory.getLog( LegalPanel.class );

    public static final PanelIdentifier ID = PanelIdentifier.parse( "legal" );
    
    protected static final IMessages    i18n = Messages.forPrefix( "LegalPanel" );


    @Override
    public boolean beforeInit() {
        if (parentPanel().isPresent() && parentPanel().get() instanceof AtlasMapPanel) {
            site().title.set( "" );
            site().icon.set( AtlasPlugin.images().svgImage( "security-account.svg", P4Plugin.HEADER_ICON_CONFIG ) );
            site().tooltip.set( "Impressum, Kontakt, Datenschutz" );
            return true;            
        }
        return false;
    }


    @Override
    public void createContents( Composite parent ) {
        site().title.set( "Rechtliches" );
        //parent.setLayout( FormLayoutFactory.defaults().margins( 3, 3, 17, 3 ).spacing( 8 ).create() );
    }

}
