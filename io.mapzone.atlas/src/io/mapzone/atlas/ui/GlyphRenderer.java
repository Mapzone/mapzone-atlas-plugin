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
package io.mapzone.atlas.ui;

import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DirectColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.IOException;

import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.FeatureSource;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.filter.function.EnvFunction;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.FeatureLayer;
import org.geotools.map.MapContent;
import org.geotools.renderer.lite.NoThreadStreamingRenderer;
import org.geotools.renderer.lite.RendererUtilities;
import org.geotools.renderer.lite.StreamingRenderer;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.style.Style;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;

import org.eclipse.core.runtime.IProgressMonitor;

import org.polymap.core.runtime.UIJob;
import org.polymap.core.runtime.UIThreadExecutor;
import org.polymap.core.ui.UIUtils;

import org.polymap.model2.test.Timer;

/**
 * Asynchronously renders glyphes to be used for {@link SearchPanel} list icons.
 *
 * @author Falko Bräutigam
 */
public class GlyphRenderer
        extends UIJob {

    private static final Log log = LogFactory.getLog( GlyphRenderer.class );

    private Feature             feature;

    private Style               style;

    private int                 imageSize;

    private double              mapWidthPerPixel;

    private Consumer<Image>     consumer;
    

    /**
     * 
     * 
     * @param f The {@link Feature}/{@link Geometry} to be rendered.
     * @param style The {@link Style} to be used for rendering.
     * @param imageSize The width/heigh of the result image in pixels.
     * @param mapWidthPerPixel The map scale the glyph is to be rendered for. Usually this is
     *        the map scale of the main map so that the glyph reflects the rendering
     *        in the map.
     */
    public GlyphRenderer( Feature f, Style style, int imageSize, double mapWidthPerPixel ) {
        super( "GlyphRenderer" );
        this.feature = f;
        this.style = style;
        this.mapWidthPerPixel = mapWidthPerPixel;
        this.imageSize = imageSize;
    }

    
    /**
     * Start rendering. Result is handled by the given {@link Consumer}.
     * <p>
     * TODO Handle Exceptions while rendering ({@link CompletableFuture}?)
     *
     * @param consumer Called in display thread.
     * @throws E
     */
    public void start( @SuppressWarnings("hiding") Consumer<Image> consumer ) {
        this.consumer = consumer;
        schedule();
    }
    
    
    @Override
    protected void runWithException( IProgressMonitor monitor ) throws Exception {
        Timer t = Timer.startNow();
        
        // BufferedImage
        Feature f = normalizeGeometry( feature );
        FeatureSource fs = featureSource( f );

        // bbox of the rendered image
        Point centroid = ((Geometry)f.getDefaultGeometryProperty().getValue()).getCentroid();
        CoordinateReferenceSystem crs = f.getDefaultGeometryProperty().getType().getCoordinateReferenceSystem();
        double mapAreaWidth = mapWidthPerPixel * imageSize;
        ReferencedEnvelope mapArea = new ReferencedEnvelope( 
                centroid.getX() - (mapAreaWidth/2),
                centroid.getX() + (mapAreaWidth/2),
                centroid.getY() - (mapAreaWidth/2),
                centroid.getY() + (mapAreaWidth/2),
                crs );

        BufferedImage result = new BufferedImage( imageSize, imageSize, BufferedImage.TYPE_INT_RGB );
        Graphics2D g = result.createGraphics();
        
        // XXX our #convertToSWT() does not support alpha channel
        g.setBackground( Color.WHITE );
        g.clearRect( 0, 0, imageSize, imageSize );

        // renderer
        StreamingRenderer renderer = new NoThreadStreamingRenderer();

        MapContent mapContent = new MapContent();
        mapContent.getViewport().setCoordinateReferenceSystem( crs );
        mapContent.addLayer( new FeatureLayer( fs, (org.geotools.styling.Style)style ) );

        // geoserver compatibility to support *env* in SLD functions
        double scale = RendererUtilities.calculateOGCScale( mapArea, imageSize, Collections.EMPTY_MAP);
        EnvFunction.setLocalValue( "wms_scale_denominator", scale );

        RenderingHints hints = new RenderingHints( RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY );
        hints.add( new RenderingHints( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON ) );
        hints.add( new RenderingHints( RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON ) );
        hints.add( new RenderingHints( RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON ) );

        renderer.setJava2DHints( hints );
        renderer.setRendererHints( new HashMap() {{
            put( StreamingRenderer.TEXT_RENDERING_KEY, StreamingRenderer.TEXT_RENDERING_ADAPTIVE );
            put( StreamingRenderer.OPTIMIZE_FTS_RENDERING_KEY, Boolean.TRUE );
            put( "optimizedDataLoadingEnabled", Boolean.TRUE );
        }});

        // render
        renderer.setMapContent( mapContent );
        Rectangle paintArea = new Rectangle( imageSize, imageSize );
        renderer.paint( g, paintArea, mapArea );
        mapContent.dispose();
        
        // result
        ImageData imageData = convertToSWT( result );
        log.info( "Glyph renderer: " + t.elapsedTime() + "ms" );
        UIThreadExecutor.async( () -> {
            consumer.accept( new Image( UIUtils.sessionDisplay(), imageData ) );
            return null;
        });
    }
    
    
    /**
     * 
     *
     * @param f
     * @return
     */
    protected Feature normalizeGeometry( Feature f ) {
        Geometry geom = (Geometry)f.getDefaultGeometryProperty().getValue();
        if (geom instanceof Point) {
            return f;
        }
        else {
            System.err.println( "Machen wir später: " + geom );
            //throw new UnsupportedOperationException( "Geometry type is not supported yet: " + geom );
            return f;
        }
    }
    
    
    /**
     * Makes an in-memory {@link FeatureSource} for the given {@link Feature}.
     *
     * @return Newly created {@link FeatureSource}.
     */
    protected FeatureSource featureSource( Feature f ) {
        try {
            SimpleFeatureCollection fc = DataUtilities.collection( (SimpleFeature)f );
            DataStore ds = DataUtilities.dataStore( fc );
            return ds.getFeatureSource( fc.getSchema().getName() );
        }
        catch (IOException e) {
            throw new RuntimeException( "Should never happen.", e );
        }
    }
    

    /**
     * Converts a buffered image to SWT {@link ImageData}.
     *
     * @param image The buffered image (<code>null</code> not permitted).
     * @return The image data.
     */
    public static ImageData convertToSWT( BufferedImage image ) {
        if (image.getColorModel() instanceof DirectColorModel) {
            DirectColorModel colorModel = (DirectColorModel)image.getColorModel();
            PaletteData palette = new PaletteData( colorModel.getRedMask(), colorModel.getGreenMask(), colorModel.getBlueMask() );
            ImageData data = new ImageData( image.getWidth(), image.getHeight(), colorModel.getPixelSize(), palette );
            WritableRaster raster = image.getRaster();
            int[] pixelArray = new int[3];
            for (int y = 0; y < data.height; y++) {
                for (int x = 0; x < data.width; x++) {
                    raster.getPixel( x, y, pixelArray );
                    int pixel = palette.getPixel( new RGB( pixelArray[0], pixelArray[1], pixelArray[2] ) );
                    data.setPixel( x, y, pixel );
                }
            }
            return data;
        }
        else if (image.getColorModel() instanceof IndexColorModel) {
            IndexColorModel colorModel = (IndexColorModel)image.getColorModel();
            int size = colorModel.getMapSize();
            byte[] reds = new byte[size];
            byte[] greens = new byte[size];
            byte[] blues = new byte[size];
            colorModel.getReds( reds );
            colorModel.getGreens( greens );
            colorModel.getBlues( blues );
            RGB[] rgbs = new RGB[size];
            for (int i = 0; i < rgbs.length; i++) {
                rgbs[i] = new RGB( reds[i] & 0xFF, greens[i] & 0xFF, blues[i] & 0xFF );
            }
            PaletteData palette = new PaletteData( rgbs );
            ImageData data = new ImageData( image.getWidth(), image.getHeight(), colorModel.getPixelSize(), palette );
            data.transparentPixel = colorModel.getTransparentPixel();
            WritableRaster raster = image.getRaster();
            int[] pixelArray = new int[1];
            for (int y = 0; y < data.height; y++) {
                for (int x = 0; x < data.width; x++) {
                    raster.getPixel( x, y, pixelArray );
                    data.setPixel( x, y, pixelArray[0] );
                }
            }
            return data;
        }
        else {
            throw new RuntimeException( "Unsupported image color model." );            
        }
    }
    
}
