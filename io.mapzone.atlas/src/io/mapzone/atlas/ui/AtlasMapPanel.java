/* 
 * polymap.org
 * Copyright (C) 2015-2018, Falko Bräutigam. All rights reserved.
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

import static org.polymap.core.data.DataPlugin.ff;
import static org.polymap.core.runtime.event.TypeEventFilter.isType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.Cookie;
import org.geotools.feature.FeatureCollection;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.util.NullProgressListener;
import org.opengis.feature.Feature;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.AtomicDouble;
//import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

import org.eclipse.swt.widgets.Composite;

import org.eclipse.rap.rwt.RWT;

import org.polymap.core.data.PipelineFeatureSource;
import org.polymap.core.data.util.Geometries;
import org.polymap.core.mapeditor.MapViewer;
import org.polymap.core.project.ILayer;
import org.polymap.core.project.IMap;
import org.polymap.core.project.ProjectNode.ProjectNodeCommittedEvent;
import org.polymap.core.runtime.event.EventHandler;
import org.polymap.core.runtime.event.EventManager;
import org.polymap.core.runtime.i18n.IMessages;
import org.polymap.core.security.SecurityContext;
import org.polymap.core.ui.FormDataFactory;
import org.polymap.core.ui.FormLayoutFactory;
import org.polymap.core.ui.UIUtils;

import org.polymap.rhei.batik.Context;
import org.polymap.rhei.batik.PanelIdentifier;
import org.polymap.rhei.batik.Scope;
import org.polymap.rhei.batik.contribution.ContributionManager;

import org.polymap.model2.test.Timer;
import org.polymap.p4.P4Panel;
import org.polymap.p4.layer.FeatureClickEvent;
import org.polymap.p4.layer.FeatureLayer;
import org.polymap.p4.project.ProjectRepository;
import org.polymap.rap.openlayers.base.OlEvent;
import org.polymap.rap.openlayers.base.OlFeature;
import org.polymap.rap.openlayers.base.OlMap;
import org.polymap.rap.openlayers.base.OlMap.Event;
import org.polymap.rap.openlayers.control.MousePositionControl;
import org.polymap.rap.openlayers.control.ScaleLineControl;
import org.polymap.rap.openlayers.format.GeoJSONFormat;
import org.polymap.rap.openlayers.geom.PointGeometry;
import org.polymap.rap.openlayers.layer.Layer;
import org.polymap.rap.openlayers.layer.VectorLayer;
import org.polymap.rap.openlayers.source.VectorSource;
import org.polymap.rap.openlayers.style.CircleStyle;
import org.polymap.rap.openlayers.style.StrokeStyle;
import org.polymap.rap.openlayers.style.Style;
import org.polymap.rap.openlayers.style.TextStyle;
import org.polymap.rap.openlayers.types.Color;
import org.polymap.rap.openlayers.types.Coordinate;
import org.polymap.rap.openlayers.types.Extent;
import org.polymap.rap.openlayers.view.View;

import io.mapzone.atlas.AtlasFeatureLayer;
import io.mapzone.atlas.AtlasPlugin;
import io.mapzone.atlas.Messages;

/**
 * The main map panel of the Atlas app.
 * <p/>
 * Listens to:
 * <ul>
 * <li>{@link FeatureClickEvent} : center/hover clicked feature</li>
 * <li>{@link ProjectNodeCommittedEvent} : set {@link #mapViewer} mapExtent</li>
 * </ul>
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public class AtlasMapPanel
        extends P4Panel {

    private static final Log log = LogFactory.getLog( AtlasMapPanel.class );

    public static final PanelIdentifier ID = PanelIdentifier.parse( "start" );
    
    private static final IMessages      i18n = Messages.forPrefix( "AtlasMapPanel" );
    
    public static final String          COOKIE_NAME = "mapzone_atlas";
    public static final String          COOKIE_PATH = "/";
    public static final int             COOKIE_MAX_AGE = (int)TimeUnit.DAYS.toSeconds( 300 );

    private static final double         SEARCH_RADIUS = 250d;

    /**
     * Outbound: The map of this P4 instance. This instance belongs to
     * {@link ProjectRepository#unitOfWork()}.
     */
    @Scope( AtlasPlugin.Scope )
    protected Context<IMap>             map;

    /**
     * Outbound: access to current map scale and layers.
     */
    @Scope( AtlasPlugin.Scope )
    protected Context<MapViewer<ILayer>> atlasMapViewer;

    private MapViewer<ILayer>           mapViewer;

    private ReferencedEnvelope          currentExtent;

    private Layer<VectorSource>         hoverLayer;

    private Pair<Feature,ILayer>        hovered;
    
    
    @Override
    public void init() {
        super.init();
        
        // the 'start' panel initializes context
        map.compareAndSet( null, ProjectRepository.unitOfWork().entity( IMap.class, "root" ) );
        
        // Fake user login; used by ILayer#LayerUserSettings for example
        SecurityContext sc = SecurityContext.instance();
        if (!sc.isLoggedIn()) {
            Cookie usernameCookie = Arrays.stream( RWT.getRequest().getCookies() )
                    .filter( cookie -> cookie.getName().equals( COOKIE_NAME ) ).findAny()
                    .orElseGet( () -> {
                        // XXX this produces more and more ILayer#LayerUserSettings; one for
                        // each browser that accesses the Atlas; these db entries are never evicted
                        String username = RandomStringUtils.randomAlphanumeric( 16 );
                        Cookie cookie = new Cookie( COOKIE_NAME, username );
                        cookie.setHttpOnly( true );
                        cookie.setPath( COOKIE_PATH );
                        cookie.setSecure( false ); // XXX
                        cookie.setMaxAge( COOKIE_MAX_AGE );
                        RWT.getResponse().addCookie( cookie );
                        log.debug( "New cookie: " + cookie.getValue() + ", path=" + cookie.getPath() + ", maxAge=" + cookie.getMaxAge() );
                        return cookie;
                    });
            log.debug( "Login: " + usernameCookie.getValue() );
            sc.loginTrusted( usernameCookie.getValue() );
        }
        
        // listen to maxExtent changes
        EventManager.instance().subscribe( this, isType( ProjectNodeCommittedEvent.class, ev -> 
                ev.getEntityId().equals( map.get().id() ) ) );
        
        // listen to Feature click
        EventManager.instance().subscribe( this, ev -> ev instanceof FeatureClickEvent ); 
    }

    
    @Override
    public void dispose() {
        EventManager.instance().unsubscribe( this );
        
        mapViewer.map().removeEventListener( Event.CLICK, this );
        View view = mapViewer.map().view.get();
        view.removeEventListener( View.Event.RESOLUTION, this );
        view.removeEventListener( View.Event.ROTATION, this );
        view.removeEventListener( View.Event.CENTER, this );
        
        if (mapViewer != null) {
            ((AtlasMapLayerProvider)mapViewer.layerProvider.get()).dispose();
            ((AtlasMapContentProvider)mapViewer.layerProvider.get()).dispose();
            mapViewer.dispose();
        }
    }

    
    @EventHandler( display=true )
    protected void onMapChange( ProjectNodeCommittedEvent ev ) {
        ReferencedEnvelope mapMaxExtent = map.get().maxExtent();
        ReferencedEnvelope viewerMaxExtent = mapViewer.maxExtent.get();
        if (!mapMaxExtent.equals( viewerMaxExtent )) {
            mapViewer.maxExtent.set( mapMaxExtent );
            mapViewer.mapExtent.set( mapMaxExtent );
        }
    }


    /**
     * Center map when list item was clicked in {@link SearchPanel}. 
     */
    @EventHandler( display=true )
    protected void onFeatureClick( FeatureClickEvent ev ) throws Exception {
        if (mapViewer != null && !mapViewer.getControl().isDisposed()) {
            // check if event was send by us via onOlEvent()
            if (hovered == null || hovered.getLeft() != ev.clicked.get()) {
                Coordinate centroid = transformedFeatureCentroid( ev.clicked.get() );
                mapViewer.map().view.get().center.set( centroid );
                updateHoverLayer( centroid );
            }
        }
        else {
            EventManager.instance().unsubscribe( this );
        }
    }

    
    @Override
    public void createContents( Composite parent ) {
        // title and layout
        site().title.set( "Atlas Vorpommern-Greifswald" );
        site().setSize( SIDE_PANEL_WIDTH/2, Integer.MAX_VALUE, Integer.MAX_VALUE );
        
        //parent.setBackground( UIUtils.getColor( 0xff, 0xff, 0xff ) );
        parent.setLayout( FormLayoutFactory.defaults().margins( 0, 0, 5, 0 ).spacing( 0 ).create() );
        
        // mapViewer
        try {
            atlasMapViewer.set( mapViewer = new MapViewer( parent ) );
            // provider triggers {@link MapViewer#refresh()} on {@link ProjectNodeCommittedEvent} 
            mapViewer.contentProvider.set( new AtlasMapContentProvider() );
            // XXX provider registers an servlet for each and every instance/session
            mapViewer.layerProvider.set( new AtlasMapLayerProvider( "/atlasmap" ) );
            
            ReferencedEnvelope maxExtent = map.get().maxExtent();
            log.info( "maxExtent: " + maxExtent );
            mapViewer.maxExtent.set( maxExtent );
            
            mapViewer.addMapControl( new MousePositionControl() );
            mapViewer.addMapControl( new ScaleLineControl() );
            
            mapViewer.setInput( map.get() );
            FormDataFactory.on( mapViewer.getControl() ).fill();
            mapViewer.getControl().moveBelow( null );
            mapViewer.getControl().setBackground( UIUtils.getColor( 0xff, 0xff, 0xff ) );
            
            mapViewer.mapExtent.set( maxExtent );

            // hover layer
            hoverLayer = new VectorLayer()
                    .style.put( new Style()
                            // PointGeometry
                            .text.put( new TextStyle()
                                    .text.put( "title" ) )
                            .image.put( new CircleStyle( 16f )
                                    .stroke.put( new StrokeStyle()
                                            .color.put( new Color( 240, 0, 10 ) )
                                            .width.put( 4f ) ) ) )
                    .source.put( new VectorSource().format.put( new GeoJSONFormat() ) );
            mapViewer.map().addLayer( hoverLayer );
            mapViewer.map().addEventListener( OlMap.Event.POINTERMOVE, this, new OlMap.PointerEventPayload() );
            mapViewer.map().addEventListener( OlMap.Event.CLICK, this, new OlMap.ClickEventPayload() );

            // XXX rough way to get mapExtent changes
            View view = mapViewer.map().view.get();
            view.addEventListener( View.Event.RESOLUTION, this, new View.ExtentEventPayload() );
//            view.addEventListener( View.Event.ROTATION, this, new View.ExtentEventPayload() );
//            view.addEventListener( View.Event.CENTER, this, new View.ExtentEventPayload() );
        }
        catch (Exception e) {
            throw new RuntimeException( e );
        }

        ContributionManager.instance().contributeTo( this, this );
    }

    
    @EventHandler( display=true )
    protected void onOlEvent( OlEvent ev ) {
        // pointer move
        OlMap.PointerEventPayload.findIn( ev ).ifPresent( payload -> {
            updateHoverLayer( payload.coordinate() );
        });
        
        // click
        OlMap.ClickEventPayload.findIn( ev ).ifPresent( payload -> {
            updateHoverLayer( payload.coordinate() );
            if (hovered != null) {
                // XXX check if SearchPanel is open?
                FeatureLayer.of( hovered.getRight() ).thenAccept( fl -> fl.get().setClicked( hovered.getLeft() ) );
            }
        });

        // map extent
        View.ExtentEventPayload.findIn( ev ).ifPresent( payload -> {
            Extent extent = payload.extent();
            CoordinateReferenceSystem crs = mapViewer.getMapCRS();
            ReferencedEnvelope newExtent = ReferencedEnvelope.create( 
                    new Envelope( extent.minx, extent.maxx, extent.miny, extent.maxy ), crs );
            if (!newExtent.equals( currentExtent )) {
                AtlasFeatureLayer.sessionQuery().mapExtent.set( currentExtent = newExtent );
            }
        });
    }
    
    
    protected void updateHoverLayer( Coordinate coord ) {
        log.debug( "Coordinate: " + coord );
        hoverLayer.source.get().clear();
        
        // find nearest feature
        CoordinateReferenceSystem mapCrs = map.get().maxExtent().getCoordinateReferenceSystem();
        ReferencedEnvelope bbox = new ReferencedEnvelope( 
                coord.x-SEARCH_RADIUS, coord.x+SEARCH_RADIUS, coord.y-SEARCH_RADIUS, coord.y+SEARCH_RADIUS, mapCrs );
        // start tasks for all (visible) layers
        hovered = null;
        AtomicDouble distance = new AtomicDouble( Double.MAX_VALUE );
        List<Future> tasks = new ArrayList();
        for (ILayer layer : map.get().layers) {
            AtlasFeatureLayer afl = AtlasFeatureLayer.of( layer );
            if (afl.visible.get()) {
                tasks.add( afl.featureLayer().thenAccept( fl -> {
                    try {
                        Timer timer = Timer.startNow();
                        PipelineFeatureSource fs = fl.get().featureSource();
                        CoordinateReferenceSystem layerCrs = fs.getSchema().getCoordinateReferenceSystem();
                        ReferencedEnvelope transformed = bbox.transform( layerCrs, true );
                        FeatureCollection features = fs.getFeatures( ff.bbox( ff.property( "" ), transformed ) );
                        features.accepts( feature -> {
                            Coordinate featureCoord = transformedFeatureCentroid( feature );
                            double featureDistance = Math.sqrt( Math.pow( Math.abs( featureCoord.x - coord.x ), 2 ) + Math.pow( Math.abs( featureCoord.y - coord.y ), 2 ) );
                            log.info( "Feature distance: " + featureDistance );
                            if (featureDistance < distance.get()) {
                                distance.set( featureDistance );
                                hovered = Pair.of( feature, afl.layer() );
                            }
                        }, new NullProgressListener() );
                        log.info( "Task: " + layer.label.get() + " -> " + timer.elapsedTime() + "ms" );
                    }
                    catch (Exception e) {
                        throw new RuntimeException( e );
                    }
                }));
            }
        }
//        // wait for all tasks to complete
//        try {
//            Thread.sleep( 3000 );
//            log.info( "go on..." );
//        }
//        catch (InterruptedException e1) {
//        }
        tasks.forEach( task -> {
            try { task.get(); }
            catch (Exception e) { Throwables.propagate( e ); }
        });
        if (hovered != null) {
            hoverLayer.source.get().addFeature( new OlFeature()
                    .name.put( "HoverPoint" )
                    .geometry.put( new PointGeometry( transformedFeatureCentroid( hovered.getLeft() ) ) ) );
        }
    }


    /** 
     * The center of the given {@link Feature} in {@link #mapViewer} CRS. 
     */
    protected Coordinate transformedFeatureCentroid( Feature f ) {
        Geometry geom = (Geometry)f.getDefaultGeometryProperty().getValue();
        Point centroid = geom.getCentroid();
        try {
            centroid = Geometries.transform( centroid, 
                    f.getDefaultGeometryProperty().getDescriptor().getCoordinateReferenceSystem(),
                    mapViewer.getMapCRS() );
            return new Coordinate( centroid.getX(), centroid.getY() );
        }
        catch (Exception e) {
            throw new RuntimeException( e );
        }
    }

}
