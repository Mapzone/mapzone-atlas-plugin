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
package io.mapzone.atlas.index;

import org.polymap.rhei.fulltext.indexing.FulltextTokenizer;

/**
 * Normale whitespace und special chars (.,;:-\\@"'()[]{}) als Token-Delimiter
 * aber '/' als Teil von Zähler/Nenner lassen.
 * 
 * @see UpdateableFullTextIndex
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
class AtlasTokenizer
        implements FulltextTokenizer {

    @Override
    public boolean isTokenChar( int c ) {
        switch (c) {
            case ' ': return false;
            case '\t': return false;
            case '\r': return false;
            case '\n': return false;
            case '.': return false;
            case ',': return false;
            case ';': return false;
            case ':': return false;
            case '-': return false;
            case '\\': return false;
            case '@': return false;
            case '"': return false;
            case '\'': return false;
            case '{': return false;
            case '}': return false;
            case '[': return false;
            case ']': return false;
            // http://polymap.org/atlas/ticket/77
            case '(': return false;
            case ')': return false;
            default: return true;
        }
    }
    
}
