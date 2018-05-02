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

import java.util.Date;
import java.util.Locale;

import java.text.DateFormat;
import java.text.NumberFormat;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.rap.rwt.RWT;

/**
 * Static helper methods to be used from scripts.
 *
 * @author Falko Bräutigam
 */
public class ScriptUtils {

    private static final Log log = LogFactory.getLog( ScriptUtils.class );
    
    public static Locale locale() {
        try {
            return RWT.getLocale();
        }
        catch (Exception e) {
            return Locale.GERMAN;
        }
    }
    
    // default ********************************************
    
    public static Object defaults( Object o, Object d ) {
        return o != null ? o : d;
    }
    
    public static Object defaultString( String s, String d ) {
        return StringUtils.defaultIfBlank( s, d );
    }
    
    // Date ***********************************************
    
    public static String format( Date date ) {
        return formatDateMedium( date );
    }
    
    public static String formatDate( Date date ) {
        return formatDateMedium( date );
    }
    
    public static String formatDateMedium( Date date ) {
        DateFormat df = DateFormat.getDateInstance( DateFormat.MEDIUM, locale() );
        return df.format( date );
    }
    
    public static String formatDateLong( Date date ) {
        DateFormat df = DateFormat.getDateInstance( DateFormat.LONG, locale() );
        return df.format( date );
    }
    
    // DateTime *******************************************
    
    public static String formatDateTime( Date date ) {
        return formatDateTimeMedium( date );
    }
    
    public static String formatDateTimeMedium( Date date ) {
        DateFormat df = DateFormat.getDateTimeInstance( DateFormat.MEDIUM, DateFormat.MEDIUM, locale() );
        return df.format( date );
    }
    
    public static String formatDateTimeLong( Date date ) {
        DateFormat df = DateFormat.getDateTimeInstance( DateFormat.LONG, DateFormat.LONG, locale() );
        return df.format( date );
    }
    
    // Number *********************************************
    
    public static String format( Integer n ) {
        return formatNumber( n, 1, 0, Integer.MAX_VALUE, 0 );
    }
    
    public static String format( Float n ) {
        return formatNumber( n, 1, 1, Integer.MAX_VALUE, Integer.MAX_VALUE );
    }
    
    public static String format( Double n ) {
        return formatNumber( n, 1, 1, Integer.MAX_VALUE, Integer.MAX_VALUE );
    }
    
    public static String formatNumber( Number n, int minFractionDigits ) {
        return formatNumber( n, 0, minFractionDigits, Integer.MAX_VALUE, Integer.MAX_VALUE );
    }
    
    public static String formatNumber( Number n, int minIntegerDigits, int minFractionDigits ) {
        return formatNumber( n, minIntegerDigits, minFractionDigits, Integer.MAX_VALUE, Integer.MAX_VALUE );
    }
    
    public static String formatNumber( Number n, int minIntegerDigits, int minFractionDigits, int maxIntegerDigits, int maxFractionDigits ) {
        NumberFormat nf = NumberFormat.getNumberInstance( locale() );
        nf.setMinimumFractionDigits( minFractionDigits );
        nf.setMinimumIntegerDigits( minIntegerDigits );
        nf.setMaximumFractionDigits( maxFractionDigits );
        nf.setMaximumIntegerDigits( maxIntegerDigits );
        return nf.format( n.doubleValue() );
    }
    
}
