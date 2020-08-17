/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.exceptionmappers;

import org.cobbzilla.wizard.exceptionmappers.OutOfMemoryErrorMapper;
import org.springframework.stereotype.Service;

import javax.ws.rs.ext.Provider;

@Provider @Service
public class BubbleOutOfMemoryProvider extends OutOfMemoryErrorMapper {}
