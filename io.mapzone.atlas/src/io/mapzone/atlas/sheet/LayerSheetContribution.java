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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.polymap.rhei.batik.contribution.IContributionProvider;
import org.polymap.rhei.batik.contribution.IContributionSite;
import org.polymap.rhei.batik.contribution.IDashboardContribution;
import org.polymap.rhei.batik.dashboard.Dashboard;
import org.polymap.rhei.batik.toolkit.PriorityConstraint;

import org.polymap.p4.layer.LayerInfoPanel;

/**
 * 
 *
 * @author Falko Bräutigam
 */
public class LayerSheetContribution
        implements IContributionProvider, IDashboardContribution {

    private static final Log log = LogFactory.getLog( LayerSheetContribution.class );

    @Override
    public void fillDashboard( IContributionSite site, Dashboard dashboard ) {
        if (site.tagsContain( LayerInfoPanel.DASHBOARD_ID )) {
            dashboard.addDashlet( new LayerSheetsDashlet()
                    .addConstraint( new PriorityConstraint( 25 ) )
                    .setExpanded( false ) );
        }
    }
    
}
