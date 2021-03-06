/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.main;

import org.cobbzilla.wizard.model.entityconfig.ModelSetupListener;

public class BubbleModelMain extends BubbleModelMainBase<BubbleModelOptions> {

    public static final void main (String[] args) { main(BubbleModelMain.class, args); }

    @Override protected ModelSetupListener getListener() {
        return new BubbleModelMainListener(System.getenv(), getApiClient());
    }

}
