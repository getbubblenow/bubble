/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.model.app.config;

import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldConfig;
import org.cobbzilla.wizard.model.search.SearchBoundComparison;

public class AppDataParam extends EntityFieldConfig {

    @Getter @Setter private SearchBoundComparison operator = SearchBoundComparison.eq;
    @Getter @Setter private String when;

    @Override public Integer getIndex() { return super.getIndex() == null ? 0 : super.getIndex(); }

}
