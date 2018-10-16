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

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import org.polymap.core.ui.FormDataFactory;
import org.polymap.core.ui.FormLayoutFactory;
import org.polymap.core.ui.UIUtils;

import org.polymap.rhei.batik.BatikApplication;
import org.polymap.rhei.batik.app.IAppDesign;
import org.polymap.rhei.batik.toolkit.DefaultToolkit;
import org.polymap.rhei.batik.toolkit.md.MdAppDesign;

import org.polymap.cms.ContentProvider;
import org.polymap.cms.ContentProvider.ContentObject;

/**
 * 
 *
 * @author Falko Bräutigam
 */
public class AtlasAppDesign
        extends MdAppDesign
        implements IAppDesign {

    private static final DefaultToolkit tk = new DefaultToolkit( null, null );
    
    public static final int             PHONE_WIDTH = 800;

    public static boolean isPhone() {
        return UIUtils.sessionDisplay().getClientArea().width < PHONE_WIDTH; 
    }

    
    // instance *******************************************
    @Override
    protected Composite fillHeaderArea( Composite parent ) {
        Composite contents = null;
        
        String northParam = BatikApplication.instance().getInitRequestParameter( "north" ).orElse( "on" );
        if (northParam.equals( "on" ) && !isPhone()) {
            contents = new Composite( parent, SWT.NO_FOCUS );
            //contents.setBackground( UIUtils.getColor( 0xff, 0xff, 0xff ) );
            contents.setLayout( FormLayoutFactory.defaults().margins( 3, 0 ).create() );

            ContentObject co = ContentProvider.instance().findContent( "/atlas/header.md" );
            FormDataFactory.on( tk.createFlowText( contents, co.content() ) )
                    .fill().height( 100 );
        }
        return contents;
    }

}
