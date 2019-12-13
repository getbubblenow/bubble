package bubble.model.cloud;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;

@Embeddable @NoArgsConstructor @Accessors(chain=true)
public class BubbleDomainNameServer implements Serializable {

    @Column(nullable=false, length=500)
    @Getter @Setter private String fqdn;

    @Column(nullable=false, length=20)
    @Getter @Setter private String ip4;

    @Column(nullable=false, length=100)
    @Getter @Setter private String ip6;

}
