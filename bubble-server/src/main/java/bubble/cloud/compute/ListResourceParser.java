/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.compute;

import bubble.cloud.compute.ResourceParser;

import java.util.ArrayList;
import java.util.List;

public abstract class ListResourceParser<E> implements ResourceParser<E, List<E>> {

    @Override public List<E> newResults() { return new ArrayList<>(); }

}
