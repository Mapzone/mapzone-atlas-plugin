/* 
 * polymap.org
 * Copyright (C) 2018, the @authors. All rights reserved.
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
package io.mapzone.atlas.sheet;

import java.util.List;

import java.io.IOException;
import org.geotools.data.Query;
import org.geotools.util.NullProgressListener;
import org.opengis.feature.Feature;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.PropertyDescriptor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.base.Throwables;
import com.vividsolutions.jts.geom.Geometry;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import org.eclipse.ui.forms.events.ExpansionEvent;

import org.eclipse.core.runtime.NullProgressMonitor;

import org.polymap.core.data.PipelineFeatureSource;
import org.polymap.core.project.ILayer;
import org.polymap.core.runtime.BlockingReference2;
import org.polymap.core.runtime.event.EventHandler;
import org.polymap.core.runtime.event.EventManager;
import org.polymap.core.runtime.event.TypeEventFilter;
import org.polymap.core.runtime.i18n.IMessages;
import org.polymap.core.ui.ColumnLayoutFactory;
import org.polymap.core.ui.FormDataFactory;
import org.polymap.core.ui.FormLayoutFactory;
import org.polymap.core.ui.HSLColor;
import org.polymap.core.ui.StatusDispatcher;
import org.polymap.core.ui.UIUtils;

import org.polymap.rhei.batik.Context;
import org.polymap.rhei.batik.PanelIdentifier;
import org.polymap.rhei.batik.Scope;
import org.polymap.rhei.batik.contribution.ContributionManager;
import org.polymap.rhei.batik.dashboard.Dashboard;
import org.polymap.rhei.batik.dashboard.DashletSite;
import org.polymap.rhei.batik.dashboard.DefaultDashlet;
import org.polymap.rhei.batik.toolkit.MinWidthConstraint;
import org.polymap.rhei.batik.toolkit.PriorityConstraint;
import org.polymap.rhei.table.DefaultFeatureTableColumn;
import org.polymap.rhei.table.FeatureCollectionContentProvider;
import org.polymap.rhei.table.FeatureTableViewer;

import org.polymap.p4.P4Panel;
import org.polymap.p4.P4Plugin;
import org.polymap.p4.layer.FeatureLayer;
import org.polymap.p4.layer.LayerInfoPanel;

import io.mapzone.atlas.AtlasPlugin;
import io.mapzone.atlas.Messages;
import io.mapzone.atlas.sheet.MarkdownScriptSheet.LayerSheet;


/**
 * 
 *
 * @author Falko Bräutigam
 */
public class LayerSheetsHelpPanel
        extends P4Panel {

    private static final Log log = LogFactory.getLog( LayerSheetsHelpPanel.class );

    public static final PanelIdentifier ID = new PanelIdentifier( "atlas-sheetshelp" );

    public static final String          DASHBOARD_ID = AtlasPlugin.ID + ".LayerSheetsHelpPanelDashboard";
    
    protected static final IMessages    i18n = Messages.forPrefix( "LayerSheetsHelpPanel" );

    /** Inbound: */
    @Scope( P4Plugin.Scope )
    private Context<ILayer>             layer;
    
    /** The Feature to get example values (with proper type) from. */
    private BlockingReference2<Feature> feature = new BlockingReference2();
    
    private Dashboard                   dashboard;

    private Composite                   dashboardControl;


    @Override
    public boolean beforeInit() {
        return parentPanel()
                .filter( parent -> parent instanceof LayerInfoPanel )
                .map( parent -> {
                    site().title.set( "" );
                    site().tooltip.set( i18n.get( "tooltip" ) );
                    site().icon.set( AtlasPlugin.images().svgImage( "presentation-play.svg", P4Plugin.HEADER_ICON_CONFIG ) );
                    return true;
                })
                .orElse( false );
    }


    @Override
    public void init() {
        super.init();
        FeatureLayer.of( layer.get() ).thenAccept( fl -> {
            try {
                PipelineFeatureSource fs = fl.get().featureSource();
                Query query = new Query();
                query.setMaxFeatures( 1 );
                fs.getFeatures( query ).accepts( f -> this.feature.set( f ), new NullProgressListener() );
            }
            catch (IOException e) {
                StatusDispatcher.handleError( "Unable to get feature for this layer.", e );
            }
        });
    }


    @Override
    public void createContents( Composite parent ) {
        site().title.set( "Atlas Vorschau" );
        dashboard = new Dashboard( getSite(), DASHBOARD_ID ).defaultExpandable.put( true );
        dashboard.addDashlet( new PreviewDashlet()
                .addConstraint( new PriorityConstraint( 50 ) ) );
        dashboard.addDashlet( new CheatSheetDashlet()
                .addConstraint( new PriorityConstraint( 3 ) ).setExpanded( false ) );
        dashboard.addDashlet( new FeatureTypeDashlet()
                .addConstraint( new PriorityConstraint( 100 ) ).setExpanded( false ) );
        dashboard.addDashlet( new FeatureTableDashlet()
                .addConstraint( new PriorityConstraint( 1 ) ).setExpanded( false ) );
        ContributionManager.instance().contributeTo( dashboard, this, DASHBOARD_ID );
        
        dashboardControl = dashboard.createContents( parent );
        
        EventManager.instance().subscribe( this, TypeEventFilter.ifType( ExpansionEvent.class, ev -> 
                dashboard.dashlets().stream().anyMatch( d -> d.site().getPanelSection() == ev.getSource() ) ) );
    }

    
    @EventHandler( display=true )
    protected void onDashletExpansion( ExpansionEvent ev ) {
        if (!dashboard.isDisposed() && ev.getState()) {
//            for (IDashlet dashlet : dashboard.dashlets()) {
//                if (dashlet.site().isExpanded() && dashlet.site().getPanelSection() != ev.getSource()) {
//                    dashlet.site().setExpanded( false );
//                }
//            }
        }
    }
    

    /**
     * 
     */
    protected class PreviewDashlet
            extends DefaultDashlet {

        private Label       titleText;
        private Label       descriptionText;
        private Label       sheetText;

        @Override
        public void init( DashletSite site ) {
            super.init( site );
            site().title.set( i18n.get( "preview" ) );
            site().addConstraint( new MinWidthConstraint( P4Panel.SIDE_PANEL_WIDTH, 0 ) );
            //site().border.set( false );
        }

        @Override
        public void dispose() {
            super.dispose();
            EventManager.instance().unsubscribe( this );
        }

        @Override
        public void createContents( Composite parent ) {
            parent.setLayout( FormLayoutFactory.defaults().spacing( 8 ).create() );
            titleText = tk().createLabel( parent, "..." );
            descriptionText = tk().createLabel( parent, "..." );
            sheetText = tk().createFlowText( parent, "..." );
        
            FormDataFactory.on( titleText ).fill().noBottom().width( P4Panel.SIDE_PANEL_WIDTH );
            FormDataFactory.on( descriptionText ).fill().top( titleText ).noBottom().width( P4Panel.SIDE_PANEL_WIDTH );
            FormDataFactory.on( sheetText ).fill().top( descriptionText ).height( 350 ).width( P4Panel.SIDE_PANEL_WIDTH );
            
            updateSheets( null );
            
            EventManager.instance().subscribe( this, ev -> ev instanceof SheetUpdateEvent );
        }
        
        @EventHandler( delay=1000, display=true )
        protected void updateSheets( List<SheetUpdateEvent> evs ) {
            try {
                update( titleText, LayerSheet.TITLE );
                update( descriptionText, LayerSheet.DESCRIPTION );
                update( sheetText, LayerSheet.DETAIL );
            }
            catch (Exception e) {
                StatusDispatcher.handleError( Throwables.getRootCause( e ).getMessage(), e );
            }
        }
        
        protected void update( Label l, LayerSheet _sheet ) throws Exception {
            if (!l.isDisposed()) {
//                l.setBackground( UIUtils.getColor( 255, 252, 204 ) );
//                IPanelSection panelSection = site().getPanelSection();
//                Composite parent = panelSection.getControl();
//                parent.setBackground( new HSLColor( parent.getBackground() ).adjustShade( 2 ).toSWT() );
//                parent = panelSection.getBody();
//                parent.setBackground( new HSLColor( parent.getBackground() ).adjustShade( 2 ).toSWT() );
                l.setBackground( new HSLColor( l.getParent().getBackground() )
                        .adjustLuminance( -3 )
                        .adjustSaturation( 20 ).toSWT() );
                
                MarkdownScriptSheet sheet = MarkdownScriptSheet.of( layer.get(), _sheet );
                sheet.setVariable( "layer", layer.get() );
                sheet.setVariable( "layer_name", layer.get().label.get() );
                sheet.setVariable( "layer_title", layer.get().label.get() );
                sheet.setVariable( "layer_description", layer.get().description.get() );
                sheet.setVariable( "layer_keywords", layer.get().keywords );
                for (Property p : feature.waitAndGet().getProperties()) {
                    sheet.setVariable( p.getName().getLocalPart(), p.getValue() );
                }
                String content = sheet.build( new NullProgressMonitor() );
                l.setText( content );
            }
        }
    }
    

    /**
     * 
     */
    protected class CheatSheetDashlet
            extends DefaultDashlet {

        @Override
        public void init( DashletSite site ) {
            super.init( site );
            site().title.set( i18n.get( "documentation" ) );
            site().addConstraint( new MinWidthConstraint( P4Panel.SIDE_PANEL_WIDTH, 0 ) );
        }

        @Override
        public void createContents( Composite parent ) {
            parent.setLayout( FormLayoutFactory.defaults().margins( 0, -8 ).create() );
            ScrolledComposite scrolled = tk().createScrolledComposite( parent, SWT.V_SCROLL );
            FormDataFactory.on( scrolled ).fill().height( 420 );
            
            Composite content = (Composite)scrolled.getContent();
            content.setLayout( FormLayoutFactory.defaults().create() );
            Label text = tk().createFlowText( content, i18n.get( "cheatsheet" ) );
            FormDataFactory.on( text ).fill().height( 700 );

//            URL res = AtlasPlugin.instance().getBundle().getResource( "doc/io/mapzone/atlas/sheet/ScriptUtils.html" );
//            try (InputStream in = res.openStream()) {
//                Label l = tk().createFlowText( parent, IOUtils.toString( in, "UTF-8" ) );
//                FormDataFactory.on( l ).fill().height( 650 );
//            }
//            catch (IOException e) {
//                log.warn( "", e );
//            }
        }
    }
    

    /**
     * 
     */
    protected class FeatureTypeDashlet
            extends DefaultDashlet {

        private int         fieldCount;

        @Override
        public void init( DashletSite site ) {
            super.init( site );
            site().title.set( i18n.get( "datafields" ) );
            site().addConstraint( new MinWidthConstraint( P4Panel.SIDE_PANEL_WIDTH, 0 ) );
        }

        @Override
        public void createContents( Composite parent ) {
            parent.setLayout( ColumnLayoutFactory.defaults().columns( 1, 3 ).spacing( 3 ).margins( 3 ).create() );
            try {
                PipelineFeatureSource fs = FeatureLayer.of( layer.get() ).get().get().featureSource();
                SimpleFeatureType schema = fs.getSchema();
                
                createField( parent, "layer_name", "String" );
                createField( parent, "layer_description", "String" );
                createField( parent, "layer_keywords", "Collection" );
                
                for (PropertyDescriptor prop : schema.getDescriptors()) {
                    createField( parent, prop.getName().getLocalPart(), prop.getType().getBinding().getSimpleName() );
                }
            }
            catch (Exception e) {
                log.warn( "", e );
            }
        }

        protected void createField( Composite parent, String _name, String _type ) {
            Composite container = tk().createComposite( parent );
            if (fieldCount++ % 2 == 0) {
                container.setBackground( new HSLColor( container.getParent().getBackground() ).adjustShade( 4 ).toSWT() );
            }

            container.setLayout( FormLayoutFactory.defaults().create() );
            Label name = tk().createLabel( container, _name );
            name.setFont( UIUtils.bold( name.getFont() ) );
            
            Label type = tk().createLabel( container, _type );

            FormDataFactory.on( name ).fill().noRight().width( 110 );
            FormDataFactory.on( type ).fill().left( name );
        }
    }
    

    /**
     * 
     */
    protected class FeatureTableDashlet
            extends DefaultDashlet {

        @Override
        public void init( DashletSite site ) {
            super.init( site );
            site().title.set( i18n.get( "datatable" ) );
            site().addConstraint( new MinWidthConstraint( P4Panel.SIDE_PANEL_WIDTH, 0 ) );
            //site().addConstraint( new MinHeightConstraint( P4Panel.SIDE_PANEL_WIDTH-160, 1 ) );
        }

        @Override
        public void createContents( Composite parent ) {
            parent.setLayout( FormLayoutFactory.defaults().create() );
            try {
                PipelineFeatureSource fs = FeatureLayer.of( layer.get() ).get().get().featureSource();
                SimpleFeatureType schema = fs.getSchema();
                
                FeatureTableViewer table = new FeatureTableViewer( parent, SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION );
                FormDataFactory.on( table.getTable() ).fill().height( 250 );
                table.setContentProvider( new FeatureCollectionContentProvider() );

                for (PropertyDescriptor prop : schema.getDescriptors()) {
                    if (Geometry.class.isAssignableFrom( prop.getType().getBinding() )) {
                        // skip Geometry
                    }
                    else {
                        DefaultFeatureTableColumn column = new DefaultFeatureTableColumn( prop );
                        column.setWeight( 1, 65 );
                        table.addColumn( column );
                    }
                }

                Query query = new Query();
                query.setMaxFeatures( 25 );
                table.setInput( fs.getFeatures( query ) );
            }
            catch (Exception e) {
                StatusDispatcher.handleError( "Unable to display data table.", e );
            }
        }
    }
    
}
