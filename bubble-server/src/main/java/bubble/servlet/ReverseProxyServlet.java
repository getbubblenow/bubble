package bubble.servlet;

import bubble.dao.account.AccountDAO;
import bubble.dao.app.AppMatcherDAO;
import bubble.server.BubbleConfiguration;
import org.eclipse.jetty.proxy.ProxyServlet;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ReverseProxyServlet extends ProxyServlet.Transparent {

    @Autowired private BubbleConfiguration configuration;
    @Autowired private AppMatcherDAO matcherDAO;
    @Autowired private AccountDAO accountDAO;

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        final String host = req.getRemoteHost();
        final String xaub64 = req.getHeader("X-Authorized-User");
        if (xaub64 == null) {
            resp.sendError(403);
            return;
        }
//        final String xau = new String(Base64.decode(xaub64));
//        final Account account =
//        if (matcherDAO.findByAccountAndFqdnAndEnabled(account.getUuid(), host))
        // check SiteMatchers here...
        super.service(req, resp);
    }

}