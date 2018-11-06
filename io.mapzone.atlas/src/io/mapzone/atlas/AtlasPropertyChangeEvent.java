/* 
 * polymap.org
 * Copyright (C) 2017, the @authors. All rights reserved.
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

import java.util.EventObject;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.polymap.core.runtime.config.Config;
import org.polymap.core.runtime.config.Config2;
import org.polymap.core.runtime.config.ConfigurationFactory;
import org.polymap.core.runtime.config.DefaultPropertyConcern;
import org.polymap.core.runtime.config.Immutable;
import org.polymap.core.runtime.config.Mandatory;
import org.polymap.core.runtime.event.EventHandler;
import org.polymap.core.runtime.event.EventManager;

/**
 *
 * @author Falko Bräutigam
 */
public class AtlasPropertyChangeEvent
        extends EventObject {

    private static final Log log = LogFactory.getLog( AtlasPropertyChangeEvent.class );

    @Mandatory @Immutable
    public Config<Config>           prop;
    
    @Mandatory @Immutable
    public Config<Object>           newValue;


    public AtlasPropertyChangeEvent( Object source ) {
        super( source );
        assert source instanceof AtlasFeatureLayer || source instanceof AtlasQuery;
        ConfigurationFactory.inject( this );
    }

    /**
     * Result is {@link AtlasFeatureLayer} or {@link AtlasQuery}.
     */
    @Override
    public Object getSource() {
        return super.getSource();
    }

    /**
     * Fires a {@link AtlasPropertyChangeEvent} when config property is modified.
     */
    public static class Fire
            extends DefaultPropertyConcern {

        /**
         * This is called *before* the {@link Config2} property is set. However, there is no
         * race condition between event handler thread, that might access property value, and
         * the current thread, that sets the property value, because most {@link EventHandler}s
         * are done in display thread.
         */
        @Override
        public Object doSet( Object obj, Config prop, Object newValue ) {
            AtlasPropertyChangeEvent ev = new AtlasPropertyChangeEvent( prop.info().getHostObject() );
            ev.prop.set( prop );
            ev.newValue.set( newValue );
            log.debug( "Publishing: " + prop.info().getName() + " => " + newValue );
            EventManager.instance().publish( ev );
            log.debug( "Published: " + prop.info().getName() + " => " + newValue );
            
            return newValue;
        }
    }
    
}