/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.resources;

import org.cobbzilla.wizard.resources.AbstractTimezonesResource;
import org.springframework.stereotype.Service;

import javax.ws.rs.Path;

import static bubble.ApiConstants.TIMEZONES_ENDPOINT;

@Path(TIMEZONES_ENDPOINT)
@Service
public class TimezonesResource extends AbstractTimezonesResource {}
