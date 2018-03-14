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
package io.mapzone.atlas;

import static org.apache.commons.io.FileUtils.byteCountToDisplaySize;

import org.osgi.framework.BundleContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.ui.plugin.AbstractUIPlugin;

import org.polymap.rhei.batik.Context;
import org.polymap.rhei.batik.app.SvgImageRegistryHelper;

import io.mapzone.atlas.index.AtlasIndex;

/**
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public class AtlasPlugin
        extends AbstractUIPlugin {

    private static final Log log = LogFactory.getLog( AtlasPlugin.class );

    public static final String      ID = "io.mapzone.atlas";

    /** The globale {@link Context} scope for the {@link AtlasPlugin}. */
    public static final String      Scope = "io.mapzone.atlas";

    public static final String      HEADER_ICON_CONFIG = SvgImageRegistryHelper.WHITE24;
    public static final String      TOOLBAR_ICON_CONFIG = SvgImageRegistryHelper.NORMAL24;

    private static AtlasPlugin      instance;


    public static AtlasPlugin instance() {
        return instance;
    }

    /**
     * Shortcut to <code>instance().images</code>.
     */
    public static SvgImageRegistryHelper images() {
        return instance().images;
    }
    
    
    // instance *******************************************

    public SvgImageRegistryHelper   images = new SvgImageRegistryHelper( this );


    public void start( BundleContext context ) throws Exception {
        super.start( context );
        instance = this;
        
        AtlasIndex atlasIndex = AtlasIndex.instance();
        log.info( "Index size: " + byteCountToDisplaySize( atlasIndex.sizeInByte() ) );
        if (atlasIndex.index().isEmpty()) {
            log.warn( "Index update commented out!" );
            //atlasIndex.update();
        }
        
//        // JAAS config: no dialog; let LoginPanel create UI
//        SecurityContext.registerConfiguration( () -> new StandardConfiguration() {
//            @Override
//            public String getConfigName() {
//                return SecurityContext.SERVICES_CONFIG_NAME;
//            }
//        });
    }


    public void stop( BundleContext context ) throws Exception {
        instance = null;
        super.stop( context );
    }

}
