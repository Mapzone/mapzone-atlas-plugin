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

import java.io.IOException;
import org.geotools.data.Query;
import org.geotools.util.NullProgressListener;
import org.opengis.feature.Feature;
import org.opengis.feature.Property;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.base.Throwables;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

import org.polymap.core.data.PipelineFeatureSource;
import org.polymap.core.project.ILayer;
import org.polymap.core.runtime.BlockingReference2;
import org.polymap.core.runtime.UIJob;
import org.polymap.core.runtime.UIThreadExecutor;
import org.polymap.core.runtime.i18n.IMessages;
import org.polymap.core.ui.FormDataFactory;
import org.polymap.core.ui.FormLayoutFactory;
import org.polymap.core.ui.StatusDispatcher;
import org.polymap.core.ui.UIUtils;

import org.polymap.rhei.batik.BatikApplication;
import org.polymap.rhei.batik.Context;
import org.polymap.rhei.batik.Scope;
import org.polymap.rhei.batik.app.SvgImageRegistryHelper;
import org.polymap.rhei.batik.dashboard.DashletSite;
import org.polymap.rhei.batik.dashboard.DefaultDashlet;
import org.polymap.rhei.batik.dashboard.ISubmitableDashlet;
import org.polymap.rhei.batik.toolkit.md.MdToolkit;

import org.polymap.p4.P4Plugin;
import org.polymap.p4.layer.FeatureLayer;

import io.mapzone.atlas.Messages;
import io.mapzone.atlas.sheet.MarkdownScriptSheet.LayerSheet;

/**
 * 
 *
 * @author Falko Bräutigam
 */
public class LayerSheetsDashlet
        extends DefaultDashlet
        implements ISubmitableDashlet {

    private static final Log log = LogFactory.getLog( LayerSheetsDashlet.class );
    
    protected static final IMessages    i18n = Messages.forPrefix( "LayerSheetsDashlet" );
    
    /** Inbound: */
    @Scope( P4Plugin.Scope )
    private Context<ILayer>             layer;
    
    /** The Feature to get example values (with proper type) from. */
    private BlockingReference2<Feature> feature = new BlockingReference2();
    
    private MarkdownScriptSheetText     titleText;

    private MarkdownScriptSheetText     descriptionText;

    private MarkdownScriptSheetText     sheetText;
    
    
    @Override
    public void init( DashletSite site ) {
        super.init( site );
        site().title.set( i18n.get( "title" ) );
    }

    
    protected MdToolkit tk() {
        return (MdToolkit)site().toolkit();
    }

    
    protected void fetchFeature() {
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
        fetchFeature();
        try {
            parent.setLayout( FormLayoutFactory.defaults().spacing( 8 ).create() );
            
            Label titleLabel = tk().createLabel( parent, i18n.get( "titleTitle" ) );
            titleLabel.setFont( UIUtils.bold( titleLabel.getFont() ) );
            titleText = new MarkdownScriptSheetText( parent, LayerSheet.TITLE );
            
            Label descriptionLabel = tk().createLabel( parent, i18n.get( "descriptionTitle" ) );
            descriptionLabel.setFont( UIUtils.bold( descriptionLabel.getFont() ) );
            descriptionText = new MarkdownScriptSheetText( parent, LayerSheet.DESCRIPTION );
            
            Label sheetLabel = tk().createLabel( parent, i18n.get( "sheetTitle" ) );
            sheetLabel.setFont( UIUtils.bold( descriptionLabel.getFont() ) );
            sheetText = new MarkdownScriptSheetText( parent, LayerSheet.DETAIL, SWT.MULTI, SWT.WRAP );
            
            // layout
            FormDataFactory.on( titleLabel ).fill().left( 0, 3 ).noBottom();
            FormDataFactory.on( titleText.getControl() ).fill().top( titleLabel, -10 ).noBottom();
            FormDataFactory.on( descriptionLabel ).fill().top( titleText.getControl() ).left( 0, 3 ).noBottom();
            FormDataFactory.on( descriptionText.getControl() ).fill().top( descriptionLabel, -10 ).noBottom();
            FormDataFactory.on( sheetLabel ).fill().top( descriptionText.getControl() ).left( 0, 3 ).noBottom();
            FormDataFactory.on( sheetText.getControl() ).fill().top( sheetLabel, -10 ).noBottom().height( 350 );
            
            parent.getDisplay().timerExec( 1500, () -> {
                BatikApplication.instance().getContext().openPanel( site().panelSite().getPath(), LayerSheetsHelpPanel.ID );
            });
        }
        catch (IOException e) {
            StatusDispatcher.handleError( "Unable to read sheets.", e );
        }
    }

    
    @Override
    public boolean submit( IProgressMonitor monitor ) throws Exception {
        return titleText.submit() && descriptionText.submit() && sheetText.submit();
    }

    
    protected void updateEnabled() {
        site().enableSubmit( 
                titleText.isDirty || descriptionText.isDirty || sheetText.isDirty,
                titleText.isValid && descriptionText.isValid && sheetText.isValid );
    }


    /**
     * 
     */
    protected class MarkdownScriptSheetText {
        
        private Text                    text;
        
        private MarkdownScriptSheet     sheet;

        private volatile UIJob          syntaxChecker;

        private Color                   defaultFg;
        
        public boolean                  isDirty = false;
        
        public boolean                  isValid = true;

        
        public MarkdownScriptSheetText( Composite parent, LayerSheet sheet, int... styles ) throws IOException {
            this.sheet = MarkdownScriptSheet.of( layer.get(), sheet );

            String content = this.sheet.text().orElseGet( () -> {
                switch (sheet) {
                    case TITLE: return "$layer_name";
                    case DESCRIPTION: return "${layer_keywords.join(', ')}";
                    case DETAIL: return "$layer_description";
                    default: throw new RuntimeException( "Unhandled sheet type: " + sheet );
                }
            });
            text = site().toolkit().createText( parent, content, ArrayUtils.add( styles, SWT.BORDER ) );
            text.addModifyListener( ev -> {
                isDirty = true;
                checkSyntax();
            });
            defaultFg = text.getForeground();
        }
        
        
        public Text getControl() {
            return text;
        }

        
        public boolean submit() {
            try {
                sheet.update( text.getText(), new NullProgressMonitor() );
                return true;
            }
            catch (Exception e) {
                StatusDispatcher.handleError( "Unable to store sheet.", e );
                return false;
            }
        }
        
        
        protected void checkSyntax() {
            if (syntaxChecker != null) {
                syntaxChecker.cancel();
                //syntaxChecker.join();
            }
            String modified = text.getText();
            syntaxChecker = new UIJob( "Syntax check" ) {
                @Override protected void runWithException( IProgressMonitor monitor ) throws Exception {
                    try {
                        sheet.setVariable( "layer", layer.get() );
                        sheet.setVariable( "layer_name", layer.get().label.get() );
                        sheet.setVariable( "layer_title", layer.get().label.get() );
                        sheet.setVariable( "layer_description", layer.get().description.get() );
                        sheet.setVariable( "layer_keywords", layer.get().keywords );
                        for (Property p : feature.waitAndGet().getProperties()) {
                            sheet.setVariable( p.getName().getLocalPart(), p.getValue() );
                        }
                        String result = sheet.check( modified, monitor );
                        log.debug( result );
                        isValid = true;
                        
                        UIThreadExecutor.async( () -> {
                            text.setToolTipText( i18n.get( "syntaxOk" ) );
                            text.setForeground( defaultFg );
                            updateEnabled();
                        });
                    }
                    catch (OperationCanceledException e) {
                        // just exit without message
                    }
                    catch (Exception e) {
                        log.debug( "", e );
                        isValid = false;
                        UIThreadExecutor.async( () -> {
                            text.setToolTipText( Throwables.getRootCause( e ).getMessage() );
                            text.setForeground( UIUtils.getColor( SvgImageRegistryHelper.COLOR_DANGER ) );
                            updateEnabled();
                        });
                    }
                    finally {
                        syntaxChecker = null;
                    }
                }
            };
            syntaxChecker.schedule();
        }
    }
}
