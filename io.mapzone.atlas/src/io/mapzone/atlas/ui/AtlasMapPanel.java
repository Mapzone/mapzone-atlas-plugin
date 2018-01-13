/* 
 * polymap.org
 * Copyright (C) 2015-2016, Falko Bräutigam. All rights reserved.
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

import static org.polymap.core.runtime.event.TypeEventFilter.isType;
import static org.polymap.p4.layer.FeatureLayer.ff;

import org.geotools.data.FeatureStore;
import org.geotools.feature.FeatureCollection;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.json.JSONArray;
import org.json.JSONObject;
import org.opengis.feature.Feature;
import org.opengis.filter.Filter;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

import org.eclipse.swt.widgets.Composite;

import org.polymap.core.data.Features;
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
import org.polymap.core.ui.StatusDispatcher;
import org.polymap.core.ui.UIUtils;

import org.polymap.rhei.batik.Context;
import org.polymap.rhei.batik.PanelIdentifier;
import org.polymap.rhei.batik.PropertyAccessEvent;
import org.polymap.rhei.batik.Scope;
import org.polymap.rhei.batik.contribution.ContributionManager;
import org.polymap.rhei.batik.toolkit.ActionItem;
import org.polymap.rhei.batik.toolkit.Snackbar.Appearance;

import org.polymap.p4.P4Panel;
import org.polymap.p4.layer.LayersCatalogsPanel;
import org.polymap.p4.project.ProjectRepository;
import org.polymap.rap.openlayers.base.OlEvent;
import org.polymap.rap.openlayers.base.OlEventListener;
import org.polymap.rap.openlayers.base.OlMap.Event;
import org.polymap.rap.openlayers.control.MousePositionControl;
import org.polymap.rap.openlayers.control.ScaleLineControl;
import org.polymap.rap.openlayers.view.View;

import io.mapzone.atlas.AtlasFeatureLayer;
import io.mapzone.atlas.AtlasPlugin;
import io.mapzone.atlas.Messages;

/**
 * 
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public class AtlasMapPanel
        extends P4Panel
        implements OlEventListener {

    private static final Log log = LogFactory.getLog( AtlasMapPanel.class );

    public static final PanelIdentifier ID = PanelIdentifier.parse( "start" );
    
    private static final IMessages      i18n = Messages.forPrefix( "AtlasMapPanel" );

    /**
     * Outbound: The map of this P4 instance. This instance belongs to
     * {@link ProjectRepository#unitOfWork()}.
     */
    @Scope( AtlasPlugin.Scope )
    protected Context<IMap>             map;

    public MapViewer<ILayer>            mapViewer;

    private ReferencedEnvelope          currentExtent;

    
    @Override
    public void init() {
        super.init();
        
        // the 'start' panel initializes context
        map.compareAndSet( null, ProjectRepository.unitOfWork().entity( IMap.class, "root" ) );
        
        // XXX fake user login; used by ProjectNodeUser for example
        SecurityContext sc = SecurityContext.instance();
        if (!sc.isLoggedIn()) {
            if (!sc.login( "admin", "admin" )) {
                throw new RuntimeException( "Default/fake login did not succeed." );
            }
        }
        
        //
        featureLayer.addListener( this, ev -> ev.getType() == PropertyAccessEvent.TYPE.SET );
        
        // listen to maxExtent changes
        EventManager.instance().subscribe( this, isType( ProjectNodeCommittedEvent.class, ev -> 
                ev.getEntityId().equals( map.get().id() ) ) );
    }

    
    @Override
    public void dispose() {
        EventManager.instance().unsubscribe( this );
        
        mapViewer.getMap().removeEventListener( Event.click, this );
        View view = mapViewer.getMap().view.get();
        view.removeEventListener( View.Event.resolution, this );
        view.removeEventListener( View.Event.rotation, this );
        view.removeEventListener( View.Event.center, this );
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

    
    @Override
    public void createContents( Composite parent ) {
        // title and layout
        site().title.set( "Atlas" );
        site().setSize( SIDE_PANEL_WIDTH/2, Integer.MAX_VALUE, Integer.MAX_VALUE );
        
        //parent.setBackground( UIUtils.getColor( 0xff, 0xff, 0xff ) );
        parent.setLayout( FormLayoutFactory.defaults().margins( 0, 0, 5, 0 ).spacing( 0 ).create() );
        
        // mapViewer
        try {
            mapViewer = new MapViewer( parent );
            // triggers {@link MapViewer#refresh()} on {@link ProjectNodeCommittedEvent} 
            mapViewer.contentProvider.set( new AtlasMapContentProvider() );
            mapViewer.layerProvider.set( new AtlasMapLayerProvider() );
            
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

            //
            mapViewer.getMap().addEventListener( Event.click, this );

            // XXX rough way to get mapExtent changes
            View view = mapViewer.getMap().view.get();
            view.addEventListener( View.Event.resolution, this );
            view.addEventListener( View.Event.rotation, this );
            view.addEventListener( View.Event.center, this );
        }
        catch (Exception e) {
            throw new RuntimeException( e );
        }

        ContributionManager.instance().contributeTo( this, this );
    }

    
    @Override
    public void handleEvent( OlEvent ev ) {
        log.info( "event: " + ev.properties() );
        // feature click
        JSONObject clickedFeature = ev.properties().optJSONObject( "feature" );
        if (clickedFeature != null) {
            JSONArray coord = clickedFeature.getJSONArray( "coordinate" );
            double x = coord.getDouble( 0 );
            double y = coord.getDouble( 1 );

            if (featureLayer.isPresent()) {
                try {
                    clickFeature( featureLayer.get().featureSource(), new Coordinate( x, y ) );
                }
                catch (Exception e) {
                    StatusDispatcher.handleError( "Unable to select feature.", e );
                }
            }
            else {
                tk().createSnackbar( Appearance.FadeIn, "No layer choosen to be <em>selectable</em>", new ActionItem( null )
                        .action.put( ev2 -> {
                            getContext().openPanel( site().path(), LayersCatalogsPanel.ID );
                        })
                        .text.put( "Layers..." ) );
            }
        }
        // map extent
        JSONArray json = ev.properties().optJSONArray( "extent" );
        if (json != null) {
            Envelope extent = new Envelope( 
                    json.getDouble( 0 ), json.getDouble( 2 ), 
                    json.getDouble( 1 ), json.getDouble( 3 ) );

            CoordinateReferenceSystem crs = mapViewer.getMapCRS();
            ReferencedEnvelope newExtent = ReferencedEnvelope.create( extent, crs );
            if (!newExtent.equals( currentExtent )) {
                AtlasFeatureLayer.query().mapExtent.set( currentExtent = newExtent );
            }
        }
    }

    
    protected void clickFeature( FeatureStore fs, Coordinate clicked ) throws Exception {
        CoordinateReferenceSystem mapCrs = Geometries.crs( map.get().srsCode.get() );
        GeometryFactory gf = new GeometryFactory();

        Point point = gf.createPoint( clicked );

        // buffer: 50m
        double buffer = 50;
        Point norm = Geometries.transform( point, mapCrs, Geometries.crs( "EPSG:3857" ) );
        ReferencedEnvelope buffered = new ReferencedEnvelope(
                norm.getX()-buffer, norm.getX()+buffer, norm.getY()-buffer, norm.getY()+buffer,
                Geometries.crs( "EPSG:3857" ) );
        
        // transform -> dataCrs
        CoordinateReferenceSystem dataCrs = fs.getSchema().getCoordinateReferenceSystem();
        buffered = buffered.transform( dataCrs, true );

        // get feature
        Filter filter = ff.intersects( ff.property( "" ), ff.literal( JTS.toGeometry( (Envelope)buffered ) ) );
        FeatureCollection selected = fs.getFeatures( filter );
        if (selected.isEmpty()) {
            return; // nothing found
        }
        if (selected.size() > 1) {
            log.info( "Multiple features found: " + selected.size() );
        }
        Feature any = (Feature)Features.stream( selected ).findAny().get();
        featureLayer.get().setClicked( any );
        log.info( "clicked: " + any );
    }

}
