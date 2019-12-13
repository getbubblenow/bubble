package bubble.cloud.auth;

import org.cobbzilla.util.collection.SingletonList;
import org.cobbzilla.wizard.validation.ConstraintViolationBean;

import java.util.List;

public interface AuthFieldHandler {

    List<ConstraintViolationBean> validate(String val);

    String mask(String val);

    default List<ConstraintViolationBean> singleError(String template, String message) {
        return singleError(template, message, null);
    }

    default List<ConstraintViolationBean> singleError(String template, String message, String invalidValue) {
        return new SingletonList<>(new ConstraintViolationBean(template, message, invalidValue));
    }

}
