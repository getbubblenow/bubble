package bubble.resources;

import org.cobbzilla.wizard.resources.AbstractTimezonesResource;
import org.springframework.stereotype.Service;

import javax.ws.rs.Path;

import static bubble.ApiConstants.TIMEZONES_ENDPOINT;

@Path(TIMEZONES_ENDPOINT)
@Service
public class TimezonesResource extends AbstractTimezonesResource {}
