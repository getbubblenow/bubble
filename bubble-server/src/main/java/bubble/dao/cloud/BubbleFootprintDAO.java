/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.dao.cloud;

import bubble.dao.account.AccountOwnedTemplateDAO;
import bubble.model.cloud.BubbleFootprint;
import bubble.server.BubbleConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Arrays;

@Repository
public class BubbleFootprintDAO extends AccountOwnedTemplateDAO<BubbleFootprint> {

    @Autowired private BubbleConfiguration configuration;

    @Override public Object preCreate(BubbleFootprint footprint) {
        if (footprint.hasAllowedCountries()) {
            footprint.setAllowedCountries(Arrays.stream(footprint.getAllowedCountries()).map(String::toUpperCase).toArray(String[]::new));
        }
        if (footprint.hasDisallowedCountries()) {
            footprint.setDisallowedCountries(Arrays.stream(footprint.getDisallowedCountries()).map(BubbleFootprint.NORMALIZE_COUNTRY).toArray(String[]::new));
        }
        if (configuration.hasDisallowedCountries()) {
            footprint.addDisallowedCountries(configuration.getDisallowedCountries());
        }
        return super.preCreate(footprint);
    }
}
