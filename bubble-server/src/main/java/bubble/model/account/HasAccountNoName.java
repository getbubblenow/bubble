package bubble.model.account;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.Transient;

public interface HasAccountNoName extends HasAccount {

    @Override @JsonIgnore @Transient default String getName() { return getUuid(); }

}
