package bubble.resources.cloud;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.ws.rs.Path;

import static bubble.ApiConstants.DOMAINS_ENDPOINT;

@Path(DOMAINS_ENDPOINT)
@Service @Slf4j
public class PublicDomainsResource extends DomainsResourceBase {

    public PublicDomainsResource() { super(null); }

}
