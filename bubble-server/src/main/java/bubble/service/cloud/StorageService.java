package bubble.service.cloud;

import java.io.IOException;

public interface StorageService {

    boolean exists(String account, String tgzB64);

    void delete(String account, String path) throws IOException;
}
