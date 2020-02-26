/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.resources.cloud;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.ws.rs.Path;

import static bubble.ApiConstants.ROLES_ENDPOINT;

@Path(ROLES_ENDPOINT)
@Service @Slf4j
public class PublicAnsibleRolesResource extends AnsibleRolesResourceBase {

    public PublicAnsibleRolesResource() { super(null); }

}
