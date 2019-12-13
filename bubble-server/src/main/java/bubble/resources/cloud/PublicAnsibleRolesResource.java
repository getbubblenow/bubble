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
